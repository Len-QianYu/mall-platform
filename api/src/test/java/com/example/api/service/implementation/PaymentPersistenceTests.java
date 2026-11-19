package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.PaymentMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.AlipayGateway;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

// 使用项目已有的本机测试环境，所有业务写入随测试事务回滚；不请求支付宝或消费真实消息。
@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false" })
@Transactional
class PaymentPersistenceTests {

    @Autowired private OrderService orders;
    @Autowired private PaymentService payments;
    @Autowired private ProductMapper productMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private PaymentMapper paymentMapper;

    @MockitoBean private AlipayGateway alipay;
    @MockitoBean private Producer rocketMQProducer;
    @MockitoBean private PushConsumer orderPayTimeoutConsumer;
    @MockitoBean private OrderPayTimeoutScanTask orderPayTimeoutScanTask;
    @MockitoBean private PaymentReconciliationTask paymentReconciliationTask;

    @Test
    void paymentQueryRepeatedNotificationAndRefundPersistOneLedgerAndRestoreTheExactStock() {
        when(this.alipay.isEnabled()).thenReturn(true);
        when(this.alipay.createPayUrl(any(), any(), any())).thenReturn("https://example.test/checkout");
        when(this.alipay.verifyNotification(any())).thenReturn(true);
        ProductEntity product = new ProductEntity();
        product.setName("payment-test-" + UUID.randomUUID());
        product.setCategory("测试");
        product.setPrice(new BigDecimal("16.80"));
        product.setStock(7);
        product.setSales(0);
        product.setStatus(1);
        this.productMapper.insert(product);
        OrderEntity order = this.orders.create(7, new TradeRequests.OrderCreate(
            List.of(new TradeRequests.OrderCreateItem(product.getId(), 2)), false,
            "测试收货人", "13900139000", "测试收货地址", null));

        PaymentEntity started = this.payments.pay(order, PaymentMethod.ALIPAY).payment();
        assertEquals(PaymentStatus.PENDING, started.getStatus());
        assertNull(started.getTradeNo());
        String tradeNo = "test-trade-" + UUID.randomUUID();
        when(this.alipay.query(any())).thenReturn(Optional.of(new AlipayGateway.Trade(
            started.getPaymentNo(), tradeNo, new BigDecimal("33.60"), "TRADE_SUCCESS")));
        assertTrue(this.payments.query(order.getId(), 7).paid());
        Map<String, String> notification = Map.of("out_trade_no", started.getPaymentNo(), "trade_no", tradeNo,
            "total_amount", "33.60", "trade_status", "TRADE_SUCCESS");
        assertTrue(this.payments.handleAlipayNotify(notification));
        assertTrue(this.payments.handleAlipayNotify(notification));

        assertEquals(OrderStatus.PAID, this.orderMapper.selectById(order.getId()).getStatus());
        assertEquals(1L, this.paymentMapper.selectCount(Wrappers.<PaymentEntity>lambdaQuery()
            .eq(PaymentEntity::getOrderId, order.getId())));
        assertEquals(tradeNo, this.paymentMapper.selectById(started.getId()).getTradeNo());
        assertEquals(5, this.productMapper.selectById(product.getId()).getStock());
        assertEquals(2, this.productMapper.selectById(product.getId()).getSales());
        assertTrue(this.payments.refund(order.getId()));
        assertEquals(OrderStatus.REFUNDED, this.orderMapper.selectById(order.getId()).getStatus());
        assertEquals(PaymentStatus.REFUNDED, this.paymentMapper.selectById(started.getId()).getStatus());
        assertEquals(7, this.productMapper.selectById(product.getId()).getStock());
        assertEquals(0, this.productMapper.selectById(product.getId()).getSales());
        verify(this.alipay, times(1)).refund(any());
    }
}
