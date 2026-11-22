package com.example.api.service.implementation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.PaymentMapper;
import com.example.api.service.AlipayGateway;
import com.example.api.service.AlipayGateway.Trade;
import com.example.api.service.PaymentService;
import com.example.api.service.ProductService;
import com.example.api.service.SeckillService;
import com.example.api.utils.Pagination;
import com.example.api.utils.TradeNo;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductService productService;
    private final SeckillService seckillService;
    private final AlipayGateway alipay;
    private final Duration payTimeout;
    private final boolean mockEnabled;

    public PaymentServiceImpl(
        PaymentMapper paymentMapper,
        OrderMapper orderMapper,
        OrderItemMapper orderItemMapper,
        ProductService productService,
        SeckillService seckillService,
        AlipayGateway alipay,
        @Value("${order.pay-timeout:15m}") Duration payTimeout,
        @Value("${payment.mock-enabled:true}") boolean mockEnabled
    ) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productService = productService;
        this.seckillService = seckillService;
        this.alipay = alipay;
        this.payTimeout = payTimeout;
        this.mockEnabled = mockEnabled;
    }

    @Override
    public Integer count(String keyword, PaymentMethod method, PaymentStatus status) {
        return this.paymentMapper.selectCount(this.filter(keyword, method, status)).intValue();
    }

    @Override
    public List<PaymentEntity> fetch(String keyword, PaymentMethod method, PaymentStatus status, Pagination pagination) {
        return this.paymentMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(keyword, method, status).orderByDesc(PaymentEntity::getId)
        );
    }

    @Override
    public List<PaymentMethod> availableMethods() {
        List<PaymentMethod> methods = new ArrayList<>();
        if (this.alipay.isEnabled()) methods.add(PaymentMethod.ALIPAY);
        if (this.mockEnabled) methods.add(PaymentMethod.MOCK);
        return methods;
    }

    @Override
    public boolean isAlipaySandbox() {
        return this.alipay.isSandbox();
    }

    @Override
    @Transactional
    public PaymentStart pay(OrderEntity requestedOrder, PaymentMethod method) {
        if (!this.availableMethods().contains(method)) {
            throw new TradeFailedException("暂不支持该支付方式");
        }
        OrderEntity order = this.ownedOrderForUpdate(requestedOrder.getId(), requestedOrder.getUserId());
        LocalDateTime now = this.orderMapper.databaseNow();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new TradeFailedException("订单不是待支付状态，请刷新后重试");
        }
        LocalDateTime expiresAt = order.getCreatedAt().plus(this.payTimeout);
        if (!now.isBefore(expiresAt)) {
            throw new TradeFailedException("订单支付已超时，请重新下单");
        }

        PaymentEntity payment = this.paymentMapper.selectLatestForUpdate(order.getId());
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            if (payment.getMethod() != method) {
                throw new TradeFailedException("订单已有待支付流水，请先查询支付结果或取消订单");
            }
        } else {
            payment = new PaymentEntity();
            payment.setPaymentNo(TradeNo.payment());
            payment.setOrderId(order.getId());
            payment.setUserId(order.getUserId());
            payment.setAmount(order.getTotalAmount());
            payment.setMethod(method);
            payment.setStatus(PaymentStatus.PENDING);
            this.paymentMapper.insert(payment);
        }

        String payUrl = null;
        if (method == PaymentMethod.ALIPAY) {
            payUrl = this.alipay.createPayUrl(payment, order, expiresAt);
        } else {
            this.markPaid(order, payment, now);
        }
        return new PaymentStart(this.paymentMapper.selectById(payment.getId()), payUrl);
    }

    @Override
    @Transactional
    public PaymentQuery query(Long orderId, Integer userId) {
        OrderEntity order = this.ownedOrderForUpdate(orderId, userId);
        PaymentEntity payment = this.paymentMapper.selectLatestForUpdate(orderId);
        this.syncAlipayTrade(order, payment);
        return new PaymentQuery(order, payment, isPaid(order));
    }

    @Override
    @Transactional
    public void reconcile(Long paymentId) {
        PaymentEntity found = this.paymentMapper.selectById(paymentId);
        if (found == null || found.getMethod() != PaymentMethod.ALIPAY) return;
        // 仍然先锁订单再锁流水，但按被扫描的流水查询，不能替换成订单的最新流水。
        OrderEntity order = this.orderMapper.selectByIdForUpdate(found.getOrderId());
        if (order == null) return;
        PaymentEntity payment = this.paymentMapper.selectByNoForUpdate(found.getPaymentNo());
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            this.syncAlipayTrade(order, payment);
        }
    }

    @Override
    @Transactional
    public boolean handleAlipayNotify(Map<String, String> params) {
        if (!this.alipay.verifyNotification(params)
            || !StringUtils.hasText(params.get("out_trade_no"))
            || !StringUtils.hasText(params.get("total_amount"))) {
            return false;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(params.get("total_amount"));
        } catch (NumberFormatException error) {
            return false;
        }
        // 先找到不可变的订单 ID，再按统一顺序锁订单、流水；锁定读取避免重复通知读到旧快照。
        PaymentEntity found = this.paymentMapper.selectOne(Wrappers.<PaymentEntity>lambdaQuery()
            .eq(PaymentEntity::getPaymentNo, params.get("out_trade_no")));
        if (found == null || found.getMethod() != PaymentMethod.ALIPAY) return false;
        OrderEntity order = this.orderMapper.selectByIdForUpdate(found.getOrderId());
        PaymentEntity payment = this.paymentMapper.selectByNoForUpdate(found.getPaymentNo());
        if (order == null || payment == null) return false;
        this.applyTrade(order, payment, new Trade(
            params.get("out_trade_no"), params.get("trade_no"), amount, params.get("trade_status")
        ));
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean closePending(OrderEntity order) {
        PaymentEntity payment = this.paymentMapper.selectLatestForUpdate(order.getId());
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) return true;
        if (payment.getMethod() != PaymentMethod.ALIPAY) {
            this.markFailed(payment);
            return true;
        }
        Optional<Trade> trade = this.alipay.query(payment);
        if (trade.isEmpty()) {
            // page.pay 是本地签名，买家可能还没打开链接。保留待支付流水，定时对账直到链接到期。
            // 取消后若收到晚到的付款，applyTrade 会原路退款，不会重新扣库存或复活订单。
            return true;
        }
        this.applyTrade(order, payment, trade.get());
        if (trade.get().paid()) return false;
        if (trade.get().closed() || this.alipay.close(payment)) {
            this.markFailed(payment);
            return true;
        }
        // 关闭交易与付款可能同时发生，再查询一次；不能确认未收款时保留订单等待重试。
        trade = this.alipay.query(payment);
        if (trade.isPresent()) {
            this.applyTrade(order, payment, trade.get());
            if (trade.get().paid()) return false;
            if (trade.get().closed()) return true;
        }
        throw new TradeFailedException("暂时无法确认支付宝交易状态，请稍后重试");
    }

    @Override
    public List<PaymentEntity> fetchPendingAlipay(long afterId, int limit) {
        if (!this.alipay.isEnabled()) return List.of();
        return this.paymentMapper.selectList(new Page<>(1, limit, false),
            Wrappers.<PaymentEntity>lambdaQuery()
                .eq(PaymentEntity::getMethod, PaymentMethod.ALIPAY)
                .eq(PaymentEntity::getStatus, PaymentStatus.PENDING)
                .gt(PaymentEntity::getId, afterId)
                .orderByAsc(PaymentEntity::getId));
    }

    @Override
    @Transactional
    public boolean refund(Long orderId) {
        OrderEntity order = this.orderMapper.selectByIdForUpdate(orderId);
        if (order == null || order.getStatus() != OrderStatus.PAID) return false;
        PaymentEntity payment = this.paymentMapper.selectSuccessForUpdate(orderId);
        if (payment == null) throw new TradeFailedException("未找到成功支付流水，无法退款");
        if (payment.getMethod() == PaymentMethod.ALIPAY) {
            this.alipay.refund(payment);
        } else if (payment.getMethod() != PaymentMethod.MOCK) {
            throw new TradeFailedException("暂不支持该支付方式退款");
        }
        // 持有订单行锁，管理员发货会等待；第三方退款失败时不会更新本地订单或库存。
        boolean updated = this.orderMapper.update(Wrappers.<OrderEntity>lambdaUpdate()
            .set(OrderEntity::getStatus, OrderStatus.REFUNDED)
            .eq(OrderEntity::getId, orderId).eq(OrderEntity::getStatus, OrderStatus.PAID)) > 0;
        if (!updated) throw new TradeFailedException("订单状态已变化，请刷新后重试");
        order.setStatus(OrderStatus.REFUNDED);
        payment.setStatus(PaymentStatus.REFUNDED);
        this.paymentMapper.updateById(payment);
        for (OrderItemEntity item : this.items(orderId)) {
            if (item.getSeckillActivityId() == null) {
                this.productService.restoreStock(item.getProductId(), item.getQuantity());
            } else {
                this.seckillService.restore(order, item);
            }
            this.productService.subtractSales(item.getProductId(), item.getQuantity());
        }
        return true;
    }

    private void syncAlipayTrade(OrderEntity order, PaymentEntity payment) {
        if (payment == null || payment.getMethod() != PaymentMethod.ALIPAY
            || (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED)) return;
        Optional<Trade> trade = this.alipay.query(payment);
        if (trade.isPresent()) {
            this.applyTrade(order, payment, trade.get());
        } else if (!this.orderMapper.databaseNow().isBefore(order.getCreatedAt().plus(this.payTimeout).plusMinutes(1))) {
            // 签名链接已过期且支付宝始终没有交易，停止定时查询。
            this.markFailed(payment);
        }
    }

    private void applyTrade(OrderEntity order, PaymentEntity payment, Trade trade) {
        if (!payment.getPaymentNo().equals(trade.paymentNo())
            || !Objects.equals(payment.getOrderId(), order.getId())
            || !Objects.equals(payment.getUserId(), order.getUserId())
            || trade.amount() == null || payment.getAmount().compareTo(trade.amount()) != 0
            || order.getTotalAmount().compareTo(trade.amount()) != 0) {
            throw new TradeFailedException("支付宝交易与订单金额不匹配");
        }
        if (!trade.paid()) {
            if (trade.closed()) this.markFailed(payment);
            return;
        }
        if (!StringUtils.hasText(trade.tradeNo()) || trade.tradeNo().length() > 64
            || (StringUtils.hasText(payment.getTradeNo()) && !payment.getTradeNo().equals(trade.tradeNo()))) {
            throw new TradeFailedException("支付宝交易号不匹配");
        }
        // 重复回调或查询不会再次累加销量，也不会覆盖已退款流水。
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) return;
        payment.setTradeNo(trade.tradeNo());
        LocalDateTime now = this.orderMapper.databaseNow();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            this.markPaid(order, payment, now);
        } else {
            // 取消后的晚到付款或另一笔流水重复收款：退款使用稳定请求号，订单状态不变。
            this.alipay.refund(payment);
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setPaidAt(now);
            this.paymentMapper.updateById(payment);
        }
    }

    private void markPaid(OrderEntity order, PaymentEntity payment, LocalDateTime now) {
        boolean updated = this.orderMapper.update(Wrappers.<OrderEntity>lambdaUpdate()
            .set(OrderEntity::getStatus, OrderStatus.PAID).set(OrderEntity::getPaidAt, now)
            .eq(OrderEntity::getId, order.getId()).eq(OrderEntity::getStatus, OrderStatus.PENDING_PAYMENT)) > 0;
        if (!updated) throw new TradeFailedException("订单不是待支付状态，请刷新后重试");
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(now);
        this.paymentMapper.updateById(payment);
        for (OrderItemEntity item : this.items(order.getId())) {
            this.productService.addSales(item.getProductId(), item.getQuantity());
        }
    }

    private void markFailed(PaymentEntity payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) return;
        payment.setStatus(PaymentStatus.FAILED);
        this.paymentMapper.updateById(payment);
    }

    private OrderEntity ownedOrderForUpdate(Long orderId, Integer userId) {
        OrderEntity order = this.orderMapper.selectByIdForUpdate(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new TradeFailedException("订单不存在");
        }
        return order;
    }

    private List<OrderItemEntity> items(Long orderId) {
        // 与下单/取消相同的商品加锁顺序，避免跨订单死锁。
        return this.orderItemMapper.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
            .eq(OrderItemEntity::getOrderId, orderId).orderByAsc(OrderItemEntity::getProductId));
    }

    private static boolean isPaid(OrderEntity order) {
        return order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.SHIPPED
            || order.getStatus() == OrderStatus.COMPLETED;
    }

    private LambdaQueryWrapper<PaymentEntity> filter(String keyword, PaymentMethod method, PaymentStatus status) {
        return Wrappers.<PaymentEntity>lambdaQuery()
            .and(StringUtils.hasText(keyword), query -> query.like(PaymentEntity::getPaymentNo, keyword)
                .or().like(PaymentEntity::getTradeNo, keyword))
            .eq(method != null, PaymentEntity::getMethod, method)
            .eq(status != null, PaymentEntity::getStatus, status);
    }
}
