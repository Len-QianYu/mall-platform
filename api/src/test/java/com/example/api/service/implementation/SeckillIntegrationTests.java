package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.dto.SeckillRequests;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.entity.SeckillActivityEntity;
import com.example.api.enums.PaymentMethod;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.PaymentMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mapper.SeckillActivityMapper;
import com.example.api.mapper.SeckillPurchaseMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;
import com.example.api.service.SeckillService;
import com.example.api.service.SeckillStockGate;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.Pagination;

// 使用真实 Redis / MySQL；只创建、删除本测试的随机商品、活动和请求。
@EnabledIfEnvironmentVariable(named = "SECKILL_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false",
    "payment.mock-enabled=true", "seckill.expiry-enabled=false", "order.create-lock-wait=10s" })
class SeckillIntegrationTests {
    @Autowired private SeckillService seckill;
    @Autowired private OrderService orders;
    @Autowired private PaymentService payments;
    @Autowired private ProductMapper products;
    @Autowired private SeckillActivityMapper activities;
    @Autowired private SeckillPurchaseMapper purchases;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderItemMapper items;
    @Autowired private PaymentMapper paymentMapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private SeckillStockGate gate;
    @MockitoBean private CacheManager caches;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutProducer timeoutMessages;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;

    private final List<Integer> productIds = new ArrayList<>();
    private final List<Long> activityIds = new ArrayList<>();
    private final List<String> requestIds = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void cache() { when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class)); }

    @Test
    void campaignAllocatesStockAndUsesItsOwnPriceWhileRetriesCreateOneOrder() {
        SeckillActivityEntity activity = this.activity(5, 2);
        int liveCount = this.seckill.count("LIVE");
        assertTrue(liveCount > 0);
        var live = this.seckill.fetch("LIVE", Pagination.paginate(liveCount, 100, 1));
        assertFalse(live.isEmpty());
        assertTrue(live.stream().allMatch(view -> "LIVE".equals(view.getPhase())));
        TradeRequests.OrderCreate request = this.request(activity, 2);
        OrderEntity order = this.orders.create(7, request);
        assertEquals(new BigDecimal("60.00"), order.getTotalAmount());
        assertEquals(15, this.products.selectById(activity.getProductId()).getStock());
        assertEquals(3, this.activities.selectById(activity.getId()).getStock());
        OrderItemEntity item = this.items.selectList(Wrappers.<OrderItemEntity>lambdaQuery()
            .eq(OrderItemEntity::getOrderId, order.getId())).getFirst();
        assertEquals(activity.getId(), item.getSeckillActivityId());
        assertEquals(new BigDecimal("30.00"), item.getUnitPrice());
        assertEquals(order.getId(), this.orders.create(7, request).getId());
        assertEquals(3, this.activities.selectById(activity.getId()).getStock());
        assertThrows(TradeFailedException.class, () -> this.orders.create(7, new TradeRequests.OrderCreate(
            request.requestId(), request.items(), false, request.receiverName(), request.receiverPhone(),
            request.receiverAddress(), request.remark())));
    }

    @Test
    void differentUsersCompetingForThreeUnitsCreateExactlyThreeOrders() throws Exception {
        SeckillActivityEntity activity = this.activity(3, 1);
        assertEquals(3, this.compete(activity, false).size());
        assertEquals(0, this.activities.selectById(activity.getId()).getStock());
        assertEquals(17, this.products.selectById(activity.getProductId()).getStock());
    }

    @Test
    void databaseStillPreventsOversellingWhenRedisAdmitsEveryRequest() throws Exception {
        doNothing().when(this.gate).reserve(anyLong(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
        SeckillActivityEntity activity = this.activity(3, 1);
        assertEquals(3, this.compete(activity, false).size());
        assertEquals(0, this.activities.selectById(activity.getId()).getStock());
    }

    @Test
    void perUserLimitSurvivesDifferentRequestIdsAndRedisCacheRebuilds() throws Exception {
        SeckillActivityEntity activity = this.activity(10, 2);
        assertEquals(2, this.compete(activity, true).size());
        this.gate.invalidate(activity.getId());
        assertThrows(TradeFailedException.class, () -> this.orders.create(7, this.request(activity, 1)));
        assertEquals(2, this.purchases.quantity(activity.getId(), 7));
        assertEquals(8, this.activities.selectById(activity.getId()).getStock());
        // 数据库限购也须独立成立。
        doNothing().when(this.gate).reserve(anyLong(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
        assertThrows(TradeFailedException.class, () -> this.orders.create(7, this.request(activity, 1)));
        assertEquals(8, this.activities.selectById(activity.getId()).getStock());
    }

    @Test
    void failureAfterReservationRollsBackDatabaseAndCompensatesRedis() {
        SeckillActivityEntity activity = this.activity(3, 1);
        TradeRequests.OrderCreate valid = this.request(activity, 1);
        TradeRequests.OrderCreate broken = new TradeRequests.OrderCreate(valid.requestId(), valid.items(), false,
            valid.receiverName(), null, valid.receiverAddress(), null, activity.getId());
        assertThrows(DataIntegrityViolationException.class, () -> this.orders.create(7, broken));
        assertEquals(3, this.activities.selectById(activity.getId()).getStock());
        assertNull(this.orderMapper.selectByRequest(7, valid.requestId()));
        assertNull(this.purchases.quantity(activity.getId(), 7));
        assertEquals("3", this.redis.opsForHash().get(SeckillStockGate.key(activity.getId()), "stock"));
        assertNotNull(this.orders.create(7, valid));
        assertEquals(2, this.activities.selectById(activity.getId()).getStock());
    }

    @Test
    void cancellationRefundAndClosingRestoreTheExactStockToTheRightPool() {
        SeckillActivityEntity activity = this.activity(5, 3);
        OrderEntity cancelled = this.orders.create(7, this.request(activity, 2));
        assertTrue(this.orders.cancel(cancelled.getId()));
        assertFalse(this.orders.cancel(cancelled.getId()));
        assertEquals(5, this.activities.selectById(activity.getId()).getStock());
        assertEquals(15, this.products.selectById(activity.getProductId()).getStock());

        OrderEntity paid = this.orders.create(7, this.request(activity, 2));
        this.payments.pay(paid, PaymentMethod.MOCK);
        assertTrue(this.payments.refund(paid.getId()));
        assertFalse(this.payments.refund(paid.getId()));
        assertEquals(5, this.activities.selectById(activity.getId()).getStock());
        assertEquals(0, this.products.selectById(activity.getProductId()).getSales());

        OrderEntity pending = this.orders.create(7, this.request(activity, 2));
        assertTrue(this.seckill.close(activity.getId()));
        assertEquals(18, this.products.selectById(activity.getProductId()).getStock());
        assertEquals(3, this.activities.selectById(activity.getId()).getReturnedStock());
        assertTrue(this.orders.cancel(pending.getId()));
        assertFalse(this.orders.cancel(pending.getId()));
        assertFalse(this.seckill.close(activity.getId()));
        assertEquals(20, this.products.selectById(activity.getProductId()).getStock());
        assertEquals(5, this.activities.selectById(activity.getId()).getReturnedStock());
        assertEquals(0, this.purchases.quantity(activity.getId(), 7));
    }

    @Test
    void expiryReturnsUnusedQuotaAndLaterCancellationReturnsHeldStock() {
        SeckillActivityEntity activity = this.activity(5, 1);
        OrderEntity pending = this.orders.create(7, this.request(activity, 1));
        this.activities.update(Wrappers.<SeckillActivityEntity>lambdaUpdate().eq(SeckillActivityEntity::getId, activity.getId())
            .set(SeckillActivityEntity::getEndsAt, this.activities.databaseNow().minusSeconds(1)));
        this.seckill.closeExpired(activity.getId());
        assertEquals(2, this.activities.selectById(activity.getId()).getStatus());
        assertEquals(19, this.products.selectById(activity.getProductId()).getStock());
        assertEquals("ENDED", this.seckill.fetchById(activity.getId()).orElseThrow().getPhase());
        assertTrue(this.orders.cancel(pending.getId()));
        assertEquals(20, this.products.selectById(activity.getProductId()).getStock());
    }

    @Test
    void upcomingCampaignAndMixedCartRequestsCannotDeductStock() {
        SeckillActivityEntity activity = this.activity(3, 1);
        this.activities.update(Wrappers.<SeckillActivityEntity>lambdaUpdate().eq(SeckillActivityEntity::getId, activity.getId())
            .set(SeckillActivityEntity::getStartsAt, this.activities.databaseNow().plusMinutes(10)));
        assertThrows(TradeFailedException.class, () -> this.orders.create(7, this.request(activity, 1)));
        TradeRequests.OrderCreate request = this.request(activity, 1);
        assertThrows(TradeFailedException.class, () -> this.orders.create(7, new TradeRequests.OrderCreate(
            request.requestId(), request.items(), true, request.receiverName(), request.receiverPhone(),
            request.receiverAddress(), null, activity.getId())));
        assertEquals(3, this.activities.selectById(activity.getId()).getStock());
    }

    private SeckillActivityEntity activity(int stock, int limit) {
        ProductEntity product = new ProductEntity();
        product.setName("seckill-test-" + UUID.randomUUID());
        product.setCategory("测试");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(20);
        product.setSales(0);
        product.setStatus(1);
        this.products.insert(product);
        this.productIds.add(product.getId());
        LocalDateTime now = this.activities.databaseNow();
        SeckillActivityEntity activity = this.seckill.create(new SeckillRequests.Create(product.getId(),
            "seckill-test-" + UUID.randomUUID(), new BigDecimal("30.00"), stock, limit, now.minusMinutes(1), now.plusHours(1)));
        this.activityIds.add(activity.getId());
        return activity;
    }

    private TradeRequests.OrderCreate request(SeckillActivityEntity activity, int quantity) {
        String id = UUID.randomUUID().toString();
        this.requestIds.add(id);
        return new TradeRequests.OrderCreate(id, List.of(new TradeRequests.OrderCreateItem(activity.getProductId(), quantity)),
            false, "测试收货人", "13900139000", "测试收货地址", null, activity.getId());
    }

    private List<OrderEntity> compete(SeckillActivityEntity activity, boolean sameUser) throws Exception {
        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<OrderEntity> successful = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(callers)) {
            List<java.util.concurrent.Future<OrderEntity>> results = new ArrayList<>();
            for (int index = 0; index < callers; index++) {
                int userId = sameUser ? 7 : 10000 + index;
                TradeRequests.OrderCreate request = this.request(activity, 1);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    try { return this.orders.create(userId, request); }
                    catch (TradeFailedException rejected) { return null; }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (var result : results) {
                OrderEntity order = result.get(20, TimeUnit.SECONDS);
                if (order != null) successful.add(order);
            }
        }
        return successful;
    }

    @AfterEach
    void cleanup() {
        new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
            if (!this.requestIds.isEmpty()) {
                List<OrderEntity> orders = this.orderMapper.selectList(Wrappers.<OrderEntity>lambdaQuery()
                    .in(OrderEntity::getRequestId, this.requestIds));
                for (OrderEntity order : orders) {
                    this.jdbc.update("DELETE FROM order_notification WHERE order_id = ?", order.getId());
                    this.paymentMapper.delete(Wrappers.<PaymentEntity>lambdaQuery().eq(PaymentEntity::getOrderId, order.getId()));
                    this.items.delete(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, order.getId()));
                    this.orderMapper.deleteById(order.getId());
                }
            }
            for (Long id : this.activityIds) {
                this.jdbc.update("DELETE FROM seckill_purchase WHERE activity_id = ?", id);
                this.activities.deleteById(id);
            }
            for (Integer id : this.productIds) this.products.deleteById(id);
        });
        for (Long id : this.activityIds) this.gate.invalidate(id);
    }
}
