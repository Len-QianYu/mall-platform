package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.example.api.dto.TradeRequests;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.api.entity.CartEntity;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.entity.UserEntity;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.CartMapper;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.PaymentService;
import com.example.api.service.OrderNotificationService;
import com.example.api.service.ProductService;
import com.example.api.service.SeckillService;

class OrderIdempotencyTests {

    @BeforeAll
    static void initializeMappings() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),
            "order-idempotency-tests"), CartEntity.class);
    }

    private OrderMapper orders;
    private OrderItemMapper items;
    private ProductMapper products;
    private ProductService stock;
    private CartMapper cart;
    private OrderPayTimeoutProducer messages;
    private OrderNotificationService notifications;
    private RedissonClient redisson;
    private RLock lock;
    private PlatformTransactionManager transactions;
    private OrderServiceImpl service;
    private OrderEntity staged;
    private OrderEntity committed;
    private boolean locked;
    private final List<String> events = new ArrayList<>();
    private final String requestId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() throws Exception {
        this.orders = mock(OrderMapper.class);
        when(this.orders.selectReceiverUsernames(anyList())).thenAnswer(call -> {
            List<Integer> userIds = call.getArgument(0);
            return userIds.stream().map(id -> {
                UserEntity user = new UserEntity();
                user.setId(id);
                user.setUsername("buyer-" + id);
                user.setName("客户一");
                return user;
            }).toList();
        });
        this.items = mock(OrderItemMapper.class);
        this.products = mock(ProductMapper.class);
        this.stock = mock(ProductService.class);
        this.cart = mock(CartMapper.class);
        this.messages = mock(OrderPayTimeoutProducer.class);
        this.notifications = mock(OrderNotificationService.class);
        this.redisson = mock(RedissonClient.class);
        this.lock = mock(RLock.class);
        this.transactions = mock(PlatformTransactionManager.class);
        this.service = new OrderServiceImpl(this.orders, this.items, this.products, this.stock,
            mock(SeckillService.class), this.cart, this.messages, mock(PaymentService.class), this.notifications, this.redisson,
            this.transactions, Duration.ofSeconds(3));

        when(this.redisson.getLock(anyString())).thenReturn(this.lock);
        when(this.lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(call -> {
            this.locked = true;
            return true;
        });
        when(this.lock.isHeldByCurrentThread()).thenAnswer(call -> this.locked);
        doAnswer(call -> {
            this.events.add("unlock");
            this.locked = false;
            return null;
        }).when(this.lock).unlock();
        when(this.transactions.getTransaction(any(TransactionDefinition.class))).thenAnswer(call -> {
            assertTrue(this.locked);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                ((TransactionDefinition) call.getArgument(0)).getPropagationBehavior());
            this.events.add("begin");
            return new SimpleTransactionStatus();
        });
        doAnswer(call -> {
            assertTrue(this.locked, "lock must cover the commit");
            this.events.add("commit");
            if (this.staged != null) this.committed = this.staged;
            this.staged = null;
            return null;
        }).when(this.transactions).commit(any());
        doAnswer(call -> {
            assertTrue(this.locked, "lock must cover the rollback");
            this.events.add("rollback");
            this.staged = null;
            return null;
        }).when(this.transactions).rollback(any());
        when(this.orders.selectByRequest(anyInt(), anyString())).thenAnswer(call ->
            this.committed != null && this.committed.getUserId().equals(call.getArgument(0))
                && this.committed.getRequestId().equals(call.getArgument(1)) ? this.committed : null);
        when(this.orders.insert(any(OrderEntity.class))).thenAnswer(call -> {
            this.staged = call.getArgument(0);
            this.staged.setId(42L);
            this.events.add("insert");
            return 1;
        });
        when(this.orders.selectById(anyLong())).thenAnswer(call -> this.staged);
        when(this.stock.deductStock(anyInt(), anyInt())).thenReturn(true);
        for (int id : List.of(11, 12)) {
            ProductEntity product = new ProductEntity();
            product.setId(id);
            product.setName("product-" + id);
            product.setPrice(new BigDecimal("16.80"));
            product.setStatus(1);
            product.setStock(7);
            when(this.products.selectById(id)).thenReturn(product);
        }
    }

    @Test
    void savesTheAuthenticatedBuyersUsernameInsteadOfTheSubmittedRecipientName() {
        TradeRequests.OrderCreate request = new TradeRequests.OrderCreate(this.requestId,
            List.of(new TradeRequests.OrderCreateItem(11, 2)), false,
            "客户一", "13900139000", "address", null);

        OrderEntity order = this.service.create(7, request);

        assertEquals("buyer-7", order.getReceiverName());
        assertEquals("13900139000", order.getReceiverPhone());
        assertEquals("address", order.getReceiverAddress());
        assertSame(order, this.service.create(7, request));
        verify(this.orders, times(1)).insert(any(OrderEntity.class));
    }

    @Test
    void repeatedRequestsReturnTheOriginalOrderWithoutRepeatingAnyBusinessWrite() {
        TradeRequests.OrderCreate request = this.request(2, true, "address", null);
        OrderEntity first = this.service.create(7, request);
        OrderEntity retry = this.service.create(7, request);

        assertSame(first, retry);
        assertEquals(new BigDecimal("33.60"), first.getTotalAmount());
        verify(this.stock, times(1)).deductStock(11, 2);
        verify(this.orders, times(1)).insert(any(OrderEntity.class));
        verify(this.items, times(1)).insert(any(OrderItemEntity.class));
        verify(this.cart, times(1)).delete(any());
        verify(this.messages, times(1)).sendAfterCommit(first);
        verify(this.redisson, times(1)).getLock("mall:order:create:7:" + this.requestId);
        assertFalse(this.locked);
        assertTrue(this.events.indexOf("commit") < this.events.indexOf("unlock"));
    }

    @Test
    void theSecondCheckReusesAnOrderCommittedWhileTheRequestWasWaitingForTheLock() {
        TradeRequests.OrderCreate request = this.request(2, false, "address", null);
        OrderEntity first = this.service.create(7, request);
        clearInvocations(this.orders, this.items, this.stock, this.cart, this.messages, this.redisson);
        when(this.orders.selectByRequest(7, this.requestId)).thenReturn(null, first);

        assertSame(first, this.service.create(7, request));

        verifyNoInteractions(this.items, this.stock, this.cart, this.messages);
        verify(this.orders, never()).insert(any(OrderEntity.class));
        verify(this.redisson).getLock("mall:order:create:7:" + this.requestId);
    }

    @Test
    void aRequestIdCannotBeReusedWithDifferentOrderContent() {
        this.service.create(7, this.request(2, false, "address", null));
        for (TradeRequests.OrderCreate changed : List.of(
            this.request(3, false, "address", null), this.request(2, true, "address", null),
            this.request(2, false, "another address", null), this.request(2, false, "address", "remark"))) {
            assertThrows(TradeFailedException.class, () -> this.service.create(7, changed));
        }
        verify(this.stock, times(1)).deductStock(11, 2);
        verify(this.orders, times(1)).insert(any(OrderEntity.class));
    }

    @Test
    void itemOrderDuplicateLinesAndOptionalDefaultsDoNotChangeTheLogicalRequest() {
        TradeRequests.OrderCreate first = new TradeRequests.OrderCreate(this.requestId,
            List.of(new TradeRequests.OrderCreateItem(11, 1), new TradeRequests.OrderCreateItem(12, 1),
                new TradeRequests.OrderCreateItem(11, 1)), null, "receiver", "13900139000", "address", null);
        TradeRequests.OrderCreate equivalent = new TradeRequests.OrderCreate(this.requestId,
            List.of(new TradeRequests.OrderCreateItem(12, 1), new TradeRequests.OrderCreateItem(11, 2)),
            false, "receiver", "13900139000", "address", "");

        assertSame(this.service.create(7, first), this.service.create(7, equivalent));
        verify(this.stock, times(1)).deductStock(11, 2);
        verify(this.stock, times(1)).deductStock(12, 1);
    }

    @Test
    void failedStockDeductionRollsBackBeforeUnlockAndTheSameRequestCanBeRetried() {
        TradeRequests.OrderCreate request = this.request(2, false, "address", null);
        when(this.stock.deductStock(11, 2)).thenReturn(false, true);
        assertThrows(TradeFailedException.class, () -> this.service.create(7, request));
        assertTrue(this.events.indexOf("rollback") < this.events.indexOf("unlock"));
        assertFalse(this.locked);
        verify(this.orders, never()).insert(any(OrderEntity.class));

        assertEquals(42L, this.service.create(7, request).getId());
        verify(this.transactions, times(1)).commit(any());
        verify(this.transactions, times(1)).rollback(any());
    }

    @Test
    void lockContentionDoesNotStartATransactionOrTouchStockAndDoesNotUnlockSomeoneElsesLock() throws Exception {
        when(this.lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);
        assertThrows(TradeFailedException.class,
            () -> this.service.create(7, this.request(2, false, "address", null)));
        verifyNoInteractions(this.transactions, this.stock, this.items, this.cart, this.messages);
        verify(this.lock, never()).unlock();
    }

    @Test
    void interruptedLockAcquisitionRestoresTheInterruptFlagWithoutBusinessWrites() throws Exception {
        when(this.lock.tryLock(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new InterruptedException());
        try {
            assertThrows(TradeFailedException.class,
                () -> this.service.create(7, this.request(2, false, "address", null)));
            assertTrue(Thread.currentThread().isInterrupted());
            verifyNoInteractions(this.transactions, this.stock, this.items, this.cart, this.messages);
            verify(this.lock, never()).unlock();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void redisFailureRejectsNewOrdersButAnAlreadyCommittedRequestStillSucceeds() {
        TradeRequests.OrderCreate request = this.request(2, false, "address", null);
        OrderEntity first = this.service.create(7, request);
        when(this.redisson.getLock(anyString())).thenThrow(new RedisException("offline"));
        assertSame(first, this.service.create(7, request));
        assertThrows(TradeFailedException.class, () -> this.service.create(8, request));
        verify(this.stock, times(1)).deductStock(11, 2);
        verify(this.orders, times(1)).insert(any(OrderEntity.class));
    }

    @Test
    void aFailedUnlockDoesNotTurnACommittedOrderIntoAFailure() {
        doThrow(new RedisException("offline")).when(this.lock).unlock();
        OrderEntity created = this.service.create(7, this.request(2, false, "address", null));
        assertEquals(42L, created.getId());
        verify(this.transactions).commit(any());
        verify(this.transactions, never()).rollback(any());
    }

    @Test
    void duplicateKeyRecoveryRunsAfterRollbackAndReturnsTheWinningOrder() {
        TradeRequests.OrderCreate request = this.request(2, false, "address", null);
        OrderEntity winner = this.service.create(7, request);
        this.committed = null;
        this.events.clear();
        clearInvocations(this.messages);
        when(this.orders.insert(any(OrderEntity.class))).thenAnswer(call -> {
            this.committed = winner;
            throw new DuplicateKeyException("uk_user_request");
        });

        assertSame(winner, this.service.create(7, request));
        assertTrue(this.events.indexOf("rollback") < this.events.indexOf("unlock"));
        verifyNoInteractions(this.messages);
    }

    @Test
    void unrelatedUniqueConstraintFailuresAreNotMistakenForSuccessfulRetries() {
        when(this.orders.insert(any(OrderEntity.class))).thenThrow(new DuplicateKeyException("uk_order_no"));
        assertThrows(DuplicateKeyException.class,
            () -> this.service.create(7, this.request(2, false, "address", null)));
        verify(this.transactions).rollback(any());
        verify(this.lock).unlock();
    }

    private TradeRequests.OrderCreate request(int quantity, Boolean fromCart, String address, String remark) {
        return new TradeRequests.OrderCreate(this.requestId,
            List.of(new TradeRequests.OrderCreateItem(11, quantity)), fromCart,
            "receiver", "13900139000", address, remark);
    }

    @Test
    void successfulSubmissionRecordsOneNotificationDespiteRetriesAndFailuresRecordNone() {
        TradeRequests.OrderCreate request = this.request(2, false, "address", null);
        when(this.stock.deductStock(11, 2)).thenReturn(false, true);
        assertThrows(TradeFailedException.class, () -> this.service.create(7, request));
        verifyNoInteractions(this.notifications);
        OrderEntity first = this.service.create(7, request);
        assertSame(first, this.service.create(7, request));
        assertSame(first, this.service.create(7, request));
        verify(this.notifications, times(1)).recordCreatedOrder(first.getId());
    }
}
