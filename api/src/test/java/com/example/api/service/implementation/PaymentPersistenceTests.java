package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderNotificationMapper;
import com.example.api.mapper.PaymentMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.AlipayGateway;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

// 支付写入随测试事务回滚；下单使用独立事务，随机商品和订单在测试事务结束后清理。
// 不请求支付宝、Redis 或消费真实消息。
@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false" })
@Transactional
class PaymentPersistenceTests {

    @Autowired private OrderService orders;
    @Autowired private PaymentService payments;
    @Autowired private ProductMapper productMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private PaymentMapper paymentMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private OrderNotificationMapper notificationMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private AlipayGateway alipay;
    @MockitoBean private Producer rocketMQProducer;
    @MockitoBean private PushConsumer orderPayTimeoutConsumer;
    @MockitoBean private OrderPayTimeoutScanTask orderPayTimeoutScanTask;
    @MockitoBean private PaymentReconciliationTask paymentReconciliationTask;
    @MockitoBean private RedissonClient redisson;
    @MockitoBean private OrderPayTimeoutProducer payTimeoutProducer;

    private final String requestId = UUID.randomUUID().toString();
    private Integer productId;

    @Test
    void paymentQueryRepeatedNotificationAndRefundPersistOneLedgerAndRestoreTheExactStock() throws Exception {
        RLock lock = mock(RLock.class);
        when(this.redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
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
        TransactionTemplate fixture = new TransactionTemplate(this.transactionManager);
        fixture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        fixture.executeWithoutResult(transaction -> this.productMapper.insert(product));
        this.productId = product.getId();
        OrderEntity order = this.orders.create(7, new TradeRequests.OrderCreate(
            this.requestId,
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

    @AfterTransaction
    void cleanCommittedFixtures() {
        new TransactionTemplate(this.transactionManager).executeWithoutResult(transaction -> {
            OrderEntity order = this.orderMapper.selectByRequest(7, this.requestId);
            if (order != null) {
                this.notificationMapper.delete(Wrappers.<OrderNotificationEntity>lambdaQuery()
                    .eq(OrderNotificationEntity::getOrderId, order.getId()));
                this.paymentMapper.delete(Wrappers.<PaymentEntity>lambdaQuery().eq(PaymentEntity::getOrderId, order.getId()));
                this.orderItemMapper.delete(Wrappers.<OrderItemEntity>lambdaQuery()
                    .eq(OrderItemEntity::getOrderId, order.getId()));
                this.orderMapper.deleteById(order.getId());
            }
            if (this.productId != null) this.productMapper.deleteById(this.productId);
        });
    }
}
