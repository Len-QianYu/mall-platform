package com.example.api.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.example.api.configuration.AlipayProperties;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.exception.TradeFailedException;

// 仅负责支付宝协议，订单状态和流水由 PaymentService 在事务中更新。
@Component
public class AlipayGateway {

    private static final Logger log = LoggerFactory.getLogger(AlipayGateway.class);
    private static final String NOT_EXIST = "ACQ.TRADE_NOT_EXIST";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayProperties properties;
    private final Optional<AlipayClient> client;

    public AlipayGateway(AlipayProperties properties, Optional<AlipayClient> client) {
        this.properties = properties;
        this.client = client;
    }

    public boolean isEnabled() {
        return this.properties.isEnabled() && this.client.isPresent();
    }

    public boolean isSandbox() {
        return this.properties.getGatewayUrl().toLowerCase(Locale.ROOT).contains("sandbox");
    }

    public String createPayUrl(PaymentEntity payment, OrderEntity order, LocalDateTime expiresAt) {
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        // 每笔支付流水作为商户交易号，重复打开收银台复用同一笔待支付流水。
        model.setOutTradeNo(payment.getPaymentNo());
        model.setTotalAmount(payment.getAmount().toPlainString());
        model.setSubject("商城订单-" + order.getOrderNo());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeExpire(expiresAt.format(TIME_FORMAT));

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setBizModel(model);
        request.setNotifyUrl(this.properties.getNotifyUrl());
        request.setReturnUrl(UriComponentsBuilder.fromUriString(this.properties.getReturnUrl())
            .queryParam("orderId", order.getId()).build().toUriString());
        try {
            // GET 模式在本地签名，直接得到收银台 URL，无需返回或执行第三方 HTML。
            String url = this.requiredClient().pageExecute(request, "GET").getBody();
            if (!StringUtils.hasText(url)) {
                throw new TradeFailedException("获取支付宝收银台失败，请稍后重试");
            }
            return url;
        } catch (AlipayApiException error) {
            throw this.requestFailed("发起支付");
        }
    }

    public Optional<Trade> query(PaymentEntity payment) {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(payment.getPaymentNo());
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = this.requiredClient().execute(request);
            if (!response.isSuccess()) {
                if (NOT_EXIST.equals(response.getSubCode())) {
                    return Optional.empty();
                }
                throw this.responseFailed("查询支付结果", response);
            }
            return Optional.of(new Trade(
                response.getOutTradeNo(), response.getTradeNo(),
                this.responseAmount(response.getTotalAmount(), "查询支付结果"), response.getTradeStatus()
            ));
        } catch (AlipayApiException error) {
            throw this.requestFailed("查询支付结果");
        }
    }

    public boolean verifyNotification(Map<String, String> params) {
        if (!this.isEnabled() || !this.properties.getAppId().equals(params.get("app_id"))) {
            return false;
        }
        if (StringUtils.hasText(this.properties.getSellerId())
            && !this.properties.getSellerId().equals(params.get("seller_id"))) {
            return false;
        }
        try {
            return AlipaySignature.rsaCheckV1(
                new HashMap<>(params), this.properties.getAlipayPublicKey(), "UTF-8", "RSA2"
            );
        } catch (AlipayApiException | RuntimeException error) {
            return false;
        }
    }

    // false 表示支付可能刚刚成功，调用方必须再查询，不能直接取消订单。
    public boolean close(PaymentEntity payment) {
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(payment.getPaymentNo());
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = this.requiredClient().execute(request);
            if (response.isSuccess()) return true;
            if (NOT_EXIST.equals(response.getSubCode())
                || "ACQ.TRADE_HAS_SUCCESS".equals(response.getSubCode())
                || "ACQ.TRADE_STATUS_ERROR".equals(response.getSubCode())) {
                return false;
            }
            throw this.responseFailed("关闭交易", response);
        } catch (AlipayApiException error) {
            throw this.requestFailed("关闭交易");
        }
    }

    public void refund(PaymentEntity payment) {
        if (!StringUtils.hasText(payment.getTradeNo())) {
            throw new TradeFailedException("支付流水缺少支付宝交易号，无法退款");
        }
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(payment.getPaymentNo());
        model.setTradeNo(payment.getTradeNo());
        model.setRefundAmount(payment.getAmount().toPlainString());
        // 回调重试、数据库回滚后的重试都使用同一个退款请求号，避免重复退款。
        model.setOutRequestNo(payment.getPaymentNo() + "-REFUND");
        model.setRefundReason("商城订单退款");
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = this.requiredClient().execute(request);
            if (!response.isSuccess()) throw this.responseFailed("退款", response);
            if (!payment.getPaymentNo().equals(response.getOutTradeNo())
                || !payment.getTradeNo().equals(response.getTradeNo())
                || payment.getAmount().compareTo(this.responseAmount(response.getRefundFee(), "核对退款结果")) != 0) {
                throw this.requestFailed("核对退款结果");
            }
        } catch (AlipayApiException error) {
            throw this.requestFailed("退款");
        }
    }

    private BigDecimal responseAmount(String value, String operation) {
        if (!StringUtils.hasText(value)) throw this.requestFailed(operation);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException error) {
            throw this.requestFailed(operation);
        }
    }

    private AlipayClient requiredClient() {
        if (!this.isEnabled()) {
            throw new TradeFailedException("支付宝支付未启用，请选择其他支付方式");
        }
        return this.client.orElseThrow();
    }

    private TradeFailedException responseFailed(String operation, AlipayResponse response) {
        log.warn("支付宝{}失败：code={}, subCode={}", operation, response.getCode(), response.getSubCode());
        return new TradeFailedException("支付宝" + operation + "失败，请稍后重试");
    }

    private TradeFailedException requestFailed(String operation) {
        log.warn("支付宝{}请求失败", operation);
        return new TradeFailedException("支付宝" + operation + "失败，请稍后重试");
    }

    public record Trade(String paymentNo, String tradeNo, BigDecimal amount, String status) {
        public boolean paid() {
            return "TRADE_SUCCESS".equals(this.status) || "TRADE_FINISHED".equals(this.status);
        }

        public boolean closed() {
            return "TRADE_CLOSED".equals(this.status);
        }
    }
}
