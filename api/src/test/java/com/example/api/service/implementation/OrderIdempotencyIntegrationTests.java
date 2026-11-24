package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.configuration.RedissonConfiguration;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.CartMapper;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.PaymentMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.OrderService;
import com.example.api.service.OrderNotificationService;
import com.example.api.mapper.OrderNotificationMapper;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.service.PaymentService;
import com.example.api.service.ProductService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

// 显式开启后验证真实 Redis / MySQL；只清理本测试创建的随机请求和商品。
@EnabledIfEnvironmentVariable(named = "ORDER_IDEMPOTENCY_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false",
    "order.create-lock-wait=10s", "seckill.expiry-enabled=false" })
class OrderIdempotencyIntegrationTests {

    @Autowired private OrderService orders;
    @Autowired private OrderNotificationService notifications;
    @Autowired private OrderNotificationMapper notificationMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderItemMapper itemMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private PaymentMapper paymentMapper;
    @Autowired private CartMapper cartMapper;
    @Autowired private ProductService productService;
    @Autowired private PaymentService paymentService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RedissonClient redisson;
    @Autowired private DataRedisProperties redisSettings;

    @MockitoBean private Producer rocketMQProducer;
    @MockitoBean private PushConsumer orderPayTimeoutConsumer;
    @MockitoBean private OrderPayTimeoutScanTask orderPayTimeoutScanTask;
    @MockitoBean private PaymentReconciliationTask paymentReconciliationTask;
    @MockitoBean private OrderPayTimeoutProducer timeoutMessages;

    private final List<Integer> productIds = new ArrayList<>();
    private final List<String> requestIds = new ArrayList<>();

    @Test
    void eightConcurrentSubmissionsAcrossTwoRedisClientsCreateOneOrderAndDeductStockOnce() throws Exception {
        ProductEntity product = this.product(7);
        TradeRequests.OrderCreate request = this.request(List.of(new TradeRequests.OrderCreateItem(product.getId(), 2)));
        RedissonClient secondClient = new RedissonConfiguration().redissonClient(this.redisSettings);
        try {
            OrderService secondService = this.service(this.orderMapper, secondClient);
            OrderService[] callers = new OrderService[8];
            for (int i = 0; i < callers.length; i++) callers[i] = i % 2 == 0 ? this.orders : secondService;
            List<OrderEntity> results = this.concurrently(request, callers);

            assertEquals(1, results.stream().map(OrderEntity::getId).distinct().count());
            assertEquals(1L, this.orderCount(request.requestId()));
            assertEquals(5, this.productMapper.selectById(product.getId()).getStock());
            assertEquals(0, this.productMapper.selectById(product.getId()).getSales());
            assertEquals(1L, this.itemMapper.selectCount(Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderId, results.getFirst().getId())));
            verify(this.timeoutMessages, times(1)).sendAfterCommit(any());
            assertFalse(this.redisson.getLock("mall:order:create:7:" + request.requestId()).isLocked());
        } finally {
            secondClient.shutdown();
        }
    }

    @Test
    void failureAfterDeductingOneProductRollsBackEverythingAndTheSameRequestCanSucceedLater() {
        ProductEntity first = this.product(7);
        ProductEntity second = this.product(0);
        TradeRequests.OrderCreate request = this.request(List.of(
            new TradeRequests.OrderCreateItem(first.getId(), 2), new TradeRequests.OrderCreateItem(second.getId(), 1)));

        assertThrows(TradeFailedException.class, () -> this.orders.create(7, request));
        assertEquals(7, this.productMapper.selectById(first.getId()).getStock());
        assertEquals(0L, this.orderCount(request.requestId()));
        verifyNoInteractions(this.timeoutMessages);

        this.productMapper.update(Wrappers.<ProductEntity>lambdaUpdate()
            .eq(ProductEntity::getId, second.getId()).set(ProductEntity::getStock, 5));
        OrderEntity order = this.orders.create(7, request);
        assertEquals(5, this.productMapper.selectById(first.getId()).getStock());
        assertEquals(4, this.productMapper.selectById(second.getId()).getStock());
        assertEquals(1L, this.orderCount(request.requestId()));
        verify(this.timeoutMessages, times(1)).sendAfterCommit(argThat(sent ->
            sent.getId().equals(order.getId()) && sent.getOrderNo().equals(order.getOrderNo())));
    }

    @Test
    void theDatabaseUniqueConstraintPreventsDuplicateStockDeductionEvenWhenTheLockFailsToExcludeRequests() throws Exception {
        ProductEntity product = this.product(7);
        TradeRequests.OrderCreate request = this.request(List.of(new TradeRequests.OrderCreateItem(product.getId(), 2)));
        RedissonClient ineffectiveClient = mock(RedissonClient.class);
        RLock ineffectiveLock = mock(RLock.class);
        when(ineffectiveClient.getLock(anyString())).thenReturn(ineffectiveLock);
        when(ineffectiveLock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(ineffectiveLock.isHeldByCurrentThread()).thenReturn(true);

        OrderMapper racingMapper = mock(OrderMapper.class, delegatesTo(this.orderMapper));
        CyclicBarrier bothSecondChecks = new CyclicBarrier(2);
        doAnswer(call -> {
            OrderEntity found = this.orderMapper.selectByRequest(7, request.requestId());
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                bothSecondChecks.await(10, TimeUnit.SECONDS);
            }
            return found;
        }).when(racingMapper).selectByRequest(7, request.requestId());
        OrderService unprotected = this.service(racingMapper, ineffectiveClient);

        List<OrderEntity> results = this.concurrently(request, unprotected, unprotected);
        assertEquals(results.getFirst().getId(), results.getLast().getId());
        assertEquals(1L, this.orderCount(request.requestId()));
        assertEquals(5, this.productMapper.selectById(product.getId()).getStock());
        verify(this.timeoutMessages, times(1)).sendAfterCommit(any());
    }

    private OrderService service(OrderMapper mapper, RedissonClient client) {
        return new OrderServiceImpl(mapper, this.itemMapper, this.productMapper, this.productService,
            mock(com.example.api.service.SeckillService.class), this.cartMapper, this.timeoutMessages, this.paymentService, this.notifications, client,
            this.transactionManager, Duration.ofSeconds(10));
    }

    private List<OrderEntity> concurrently(TradeRequests.OrderCreate request, OrderService... callers) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(callers.length);
        CountDownLatch ready = new CountDownLatch(callers.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<OrderEntity>> futures = new ArrayList<>();
            for (OrderService caller : callers) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    return caller.create(7, request);
                }));
            }
            boolean allReady = ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(allReady);
            List<OrderEntity> results = new ArrayList<>();
            for (Future<OrderEntity> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private ProductEntity product(int stock) {
        ProductEntity product = new ProductEntity();
        product.setName("order-idempotency-test-" + UUID.randomUUID());
        product.setCategory("测试");
        product.setPrice(new BigDecimal("16.80"));
        product.setStatus(1);
        product.setStock(stock);
        product.setSales(0);
        this.productMapper.insert(product);
        this.productIds.add(product.getId());
        return product;
    }

    private TradeRequests.OrderCreate request(List<TradeRequests.OrderCreateItem> items) {
        String id = UUID.randomUUID().toString();
        this.requestIds.add(id);
        return new TradeRequests.OrderCreate(id, items, false, "测试收货人", "13900139000", "测试收货地址", null);
    }

    private long orderCount(String requestId) {
        return this.orderMapper.selectCount(Wrappers.<OrderEntity>lambdaQuery()
            .eq(OrderEntity::getUserId, 7).eq(OrderEntity::getRequestId, requestId));
    }

    @AfterEach
    void cleanFixtures() {
        new TransactionTemplate(this.transactionManager).executeWithoutResult(transaction -> {
            for (String requestId : this.requestIds) {
                OrderEntity order = this.orderMapper.selectByRequest(7, requestId);
                if (order == null) continue;
                this.notificationMapper.delete(Wrappers.<OrderNotificationEntity>lambdaQuery()
                    .eq(OrderNotificationEntity::getOrderId, order.getId()));
                this.paymentMapper.delete(Wrappers.<PaymentEntity>lambdaQuery().eq(PaymentEntity::getOrderId, order.getId()));
                this.itemMapper.delete(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, order.getId()));
                this.orderMapper.deleteById(order.getId());
            }
            for (Integer id : this.productIds) this.productMapper.deleteById(id);
        });
    }
}
