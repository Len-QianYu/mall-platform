package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.api.dto.SeckillRequests;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.entity.SeckillActivityEntity;
import com.example.api.entity.UserEntity;
import com.example.api.mapper.ProductMapper;
import com.example.api.mapper.SeckillActivityMapper;
import com.example.api.mapper.UserMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.OrderService;
import com.example.api.service.SeckillService;
import com.example.api.service.SeckillStockGate;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

// Real MySQL/Redis regression: more concurrent callers than physical connections.
@EnabledIfEnvironmentVariable(named = "TRADE_CONNECTION_POOL_TESTS", matches = "true")
@SpringBootTest(properties = { "spring.datasource.druid.initial-size=2", "spring.datasource.druid.min-idle=2",
    "spring.datasource.druid.max-active=2", "spring.datasource.druid.max-wait=2000",
    "seckill.expiry-enabled=false", "payment.reconcile-enabled=false", "alipay.enabled=false" })
class OrderConnectionPoolIntegrationTests {
    @Autowired private OrderService orders;
    @Autowired private SeckillService seckill;
    @Autowired private ProductMapper products;
    @Autowired private SeckillActivityMapper activities;
    @Autowired private UserMapper users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RedissonClient redisson;
    @Autowired private StringRedisTemplate redis;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutProducer timeoutMessages;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private CacheManager caches;

    private final List<Integer> userIds = new ArrayList<>();
    private ProductEntity product;
    private SeckillActivityEntity activity;

    @BeforeEach
    void prepare() {
        when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class));
        this.redisson.getKeys().count();
        this.redis.opsForValue().get("mall:connection-regression:readiness");
    }

    @Test
    void prechecksReleaseTheirConnectionsBeforeIndependentTransactionsStart() throws Exception {
        String prefix = "cp" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        for (int index = 0; index < 24; index++) {
            UserEntity user = new UserEntity();
            user.setUsername(prefix + index);
            user.setName(user.getUsername());
            user.setPassword("unused-test-password");
            user.setRole("user");
            user.setStatus(1);
            user.setRegisteredAt(LocalDateTime.now());
            this.users.insert(user);
            this.userIds.add(user.getId());
        }
        this.product = new ProductEntity();
        this.product.setName(prefix);
        this.product.setCategory("Connection regression");
        this.product.setPrice(new BigDecimal("99.00"));
        this.product.setStock(24);
        this.product.setSales(0);
        this.product.setStatus(1);
        this.products.insert(this.product);
        this.activity = this.seckill.create(new SeckillRequests.Create(this.product.getId(), prefix,
            new BigDecimal("9.90"), 24, 1, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1)));

        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        List<OrderEntity> committed = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(12)) {
            var futures = this.userIds.stream().map(userId -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return this.orders.create(userId, new TradeRequests.OrderCreate(prefix + "_request_" + userId,
                    List.of(new TradeRequests.OrderCreateItem(this.product.getId(), 1)), false, "buyer",
                    "13900139000", "Connection regression address", null, this.activity.getId()));
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) committed.add(future.get(10, TimeUnit.SECONDS));
        }
        assertEquals(24, committed.stream().map(OrderEntity::getId).distinct().count());
        assertEquals(24, committed.stream().map(OrderEntity::getUserId).distinct().count());
        assertEquals(0, this.activities.selectById(this.activity.getId()).getStock());
        assertEquals(24, this.jdbc.queryForObject("SELECT SUM(quantity) FROM seckill_purchase WHERE activity_id=?",
            Integer.class, this.activity.getId()));
    }

    @AfterEach
    void cleanup() {
        if (this.activity != null) {
            var orderIds = this.jdbc.queryForList("SELECT order_id FROM order_item WHERE seckill_activity_id=?",
                Long.class, this.activity.getId());
            for (Long id : orderIds) {
                this.jdbc.update("DELETE FROM order_notification WHERE order_id=?", id);
                this.jdbc.update("DELETE FROM order_item WHERE order_id=?", id);
                this.jdbc.update("DELETE FROM orders WHERE id=?", id);
            }
            this.jdbc.update("DELETE FROM seckill_purchase WHERE activity_id=?", this.activity.getId());
            this.jdbc.update("DELETE FROM seckill_activity WHERE id=?", this.activity.getId());
            this.redis.delete(SeckillStockGate.key(this.activity.getId()));
        }
        if (this.product != null) this.products.deleteById(this.product.getId());
        for (Integer id : this.userIds) this.users.deleteById(id);
    }
}
