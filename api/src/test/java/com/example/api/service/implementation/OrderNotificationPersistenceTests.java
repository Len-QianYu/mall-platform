package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.entity.UserEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.OrderNotificationMapper;
import com.example.api.mapper.UserMapper;
import com.example.api.service.OrderNotificationService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.Pagination;
import com.example.api.websocket.ProductNotificationWebSocketHandler;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false" })
@Transactional
class OrderNotificationPersistenceTests {
    @Autowired private OrderMapper orders;
    @Autowired private OrderNotificationMapper rows;
    @Autowired private UserMapper users;
    @Autowired private OrderNotificationService notifications;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private CacheManager caches;
    @MockitoBean private ProductNotificationWebSocketHandler sockets;

    private record Fixture(OrderEntity order, UserEntity buyer, UserEntity admin, UserEntity superAdmin,
        UserEntity disabledAdmin, UserEntity disabledSuperAdmin) {
        List<UserEntity> users() { return List.of(buyer, admin, superAdmin, disabledAdmin, disabledSuperAdmin); }
    }

    @BeforeEach
    void caches() { when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class)); }

    @Test
    void newOrdersNotifyBothEnabledAdminRolesWithTheActualBuyerAndServerAmount() {
        Fixture fixture = this.fixture();
        this.notifications.recordCreatedOrder(fixture.order().getId());
        assertEquals(1, this.notifications.count(fixture.admin().getId(), true));
        assertEquals(1, this.notifications.count(fixture.superAdmin().getId(), true));
        assertEquals(0, this.notifications.count(fixture.buyer().getId(), false));
        assertEquals(0, this.notifications.count(fixture.disabledAdmin().getId(), false));
        assertEquals(0, this.notifications.count(fixture.disabledSuperAdmin().getId(), false));
        OrderNotificationEntity notification = this.forUser(fixture.admin());
        assertEquals(fixture.order().getOrderNo(), notification.getOrderNo());
        assertEquals(fixture.order().getId(), notification.getOrderId());
        assertEquals(fixture.buyer().getId(), notification.getBuyerId());
        assertEquals(fixture.buyer().getUsername(), notification.getBuyerUsername());
        assertEquals("买家名字", notification.getBuyerName());
        assertEquals(new BigDecimal("168.80"), notification.getTotalAmount());
        assertNotNull(notification.getCreatedAt());
        verifyNoInteractions(this.sockets);
    }

    @Test
    void buyerProfileChangesDoNotRewriteTheSubmittedOrderNotification() {
        Fixture fixture = this.fixture();
        this.notifications.recordCreatedOrder(fixture.order().getId());
        fixture.buyer().setName("改名后的买家");
        this.users.updateById(fixture.buyer());
        assertEquals("买家名字", this.forUser(fixture.superAdmin()).getBuyerName());
    }

    @Test
    void readStateIsPerAdminAndRepeatedRecordingCannotDuplicateOrResetIt() {
        Fixture fixture = this.fixture();
        this.notifications.recordCreatedOrder(fixture.order().getId());
        OrderNotificationEntity admin = this.forUser(fixture.admin());
        assertFalse(this.notifications.markRead(fixture.superAdmin().getId(), admin.getId()));
        this.notifications.markAllRead(fixture.buyer().getId());
        assertEquals(1, this.notifications.count(fixture.admin().getId(), true));
        assertTrue(this.notifications.markRead(fixture.admin().getId(), admin.getId()));
        this.notifications.recordCreatedOrder(fixture.order().getId());
        assertEquals(1, this.notifications.count(fixture.admin().getId(), false));
        assertEquals(0, this.notifications.count(fixture.admin().getId(), true));
        assertEquals(1, this.notifications.count(fixture.superAdmin().getId(), true));
        this.notifications.markAllRead(fixture.superAdmin().getId());
        assertEquals(0, this.notifications.count(fixture.superAdmin().getId(), true));
        assertNotNull(this.rows.selectById(admin.getId()).getReadAt());
        verifyNoInteractions(this.sockets);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aRolledBackOrderLeavesNoNotificationAndSendsNothing() {
        AtomicReference<Fixture> staged = new AtomicReference<>();
        new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
            Fixture fixture = this.fixture();
            staged.set(fixture);
            this.notifications.recordCreatedOrder(fixture.order().getId());
            assertEquals(1, this.notifications.count(fixture.admin().getId(), true));
            verifyNoInteractions(this.sockets);
            transaction.setRollbackOnly();
        });
        Fixture fixture = staged.get();
        assertEquals(0L, this.rows.selectCount(Wrappers.<OrderNotificationEntity>lambdaQuery()
            .eq(OrderNotificationEntity::getOrderId, fixture.order().getId())));
        assertEquals(null, this.orders.selectById(fixture.order().getId()));
        verifyNoInteractions(this.sockets);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void commitPushesToEveryRecipientAndOneBrokenSocketCannotFailTheOrder() {
        AtomicReference<Fixture> saved = new AtomicReference<>();
        try {
            assertDoesNotThrow(() -> new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
                Fixture fixture = this.fixture();
                saved.set(fixture);
                doAnswer(call -> {
                    OrderNotificationEntity notification = call.getArgument(0);
                    if (notification.getUserId().equals(fixture.admin().getId())) throw new IllegalStateException("socket closed");
                    return null;
                }).when(this.sockets).sendOrderNotification(any());
                this.notifications.recordCreatedOrder(fixture.order().getId());
                verifyNoInteractions(this.sockets);
            }));
            Fixture fixture = saved.get();
            List<OrderNotificationEntity> created = this.rows.selectList(Wrappers.<OrderNotificationEntity>lambdaQuery()
                .eq(OrderNotificationEntity::getOrderId, fixture.order().getId()));
            assertNotNull(this.orders.selectById(fixture.order().getId()));
            verify(this.sockets, times(created.size())).sendOrderNotification(any());
            verify(this.sockets).sendOrderNotification(this.forUser(fixture.superAdmin()));
        } finally {
            Fixture fixture = saved.get();
            if (fixture != null) new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
                this.rows.delete(Wrappers.<OrderNotificationEntity>lambdaQuery()
                    .eq(OrderNotificationEntity::getOrderId, fixture.order().getId()));
                this.orders.deleteById(fixture.order().getId());
                fixture.users().forEach(user -> this.users.deleteById(user.getId()));
            });
        }
    }

    private OrderNotificationEntity forUser(UserEntity user) {
        return this.notifications.fetch(user.getId(), false, Pagination.paginate(1, 20, 1)).getFirst();
    }

    private Fixture fixture() {
        UserEntity buyer = this.user("user", 1);
        buyer.setName("买家名字");
        this.users.updateById(buyer);
        OrderEntity order = new OrderEntity();
        order.setOrderNo("ON" + UUID.randomUUID().toString().replace("-", "").substring(0, 28));
        order.setUserId(buyer.getId());
        order.setTotalAmount(new BigDecimal("168.80"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setReceiverName("与买家不同的收货人");
        order.setReceiverPhone("13900139000");
        order.setReceiverAddress("测试收货地址");
        this.orders.insert(order);
        return new Fixture(order, buyer, this.user("admin", 1), this.user("super_admin", 1),
            this.user("admin", 0), this.user("super_admin", 0));
    }

    private UserEntity user(String role, int status) {
        UserEntity user = new UserEntity();
        user.setUsername("on" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        user.setPassword("test-password");
        user.setRole(role);
        user.setStatus(status);
        user.setRegisteredAt(LocalDateTime.now());
        this.users.insert(user);
        return user;
    }
}
