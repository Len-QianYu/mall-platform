package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.entity.OrderShipmentNotificationEntity;
import com.example.api.entity.ProductNotificationEntity;
import com.example.api.entity.UserEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.enums.ProductNotificationType;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.OrderNotificationMapper;
import com.example.api.mapper.OrderShipmentNotificationMapper;
import com.example.api.mapper.ProductNotificationMapper;
import com.example.api.mapper.UserMapper;
import com.example.api.service.OrderNotificationService;
import com.example.api.service.OrderService;
import com.example.api.service.UserNotificationService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.Pagination;
import com.example.api.websocket.ProductNotificationWebSocketHandler;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false" })
@Transactional
@Sql(scripts = "/db/upgrade-order-shipment-notifications.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderNotificationPersistenceTests {
    @Autowired private OrderMapper orders;
    @Autowired private OrderNotificationMapper rows;
    @Autowired private UserMapper users;
    @MockitoSpyBean private OrderNotificationService notifications;
    @Autowired private OrderShipmentNotificationMapper shipmentRows;
    @Autowired private ProductNotificationMapper productRows;
    @Autowired private OrderService shipping;
    @Autowired private UserNotificationService userNotifications;
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

    @Test
    void successfulShippingSavesTheBuyerNotificationWithTheShippedOrderSnapshot() {
        Fixture fixture = this.fixture();
        this.setStatus(fixture, OrderStatus.PAID);
        assertTrue(this.shipping.ship(fixture.order().getId()));
        OrderEntity order = this.orders.selectById(fixture.order().getId());
        OrderShipmentNotificationEntity notification = this.shipment(fixture);
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertEquals(fixture.buyer().getId(), notification.getUserId());
        assertEquals(order.getId(), notification.getOrderId());
        assertEquals(order.getOrderNo(), notification.getOrderNo());
        assertEquals(order.getTotalAmount(), notification.getTotalAmount());
        assertEquals(order.getShippedAt(), notification.getShippedAt());
        assertEquals(order.getShippedAt(), notification.getCreatedAt());
        assertEquals(1, this.userNotifications.count(fixture.buyer().getId(), true));
        assertEquals(0, this.userNotifications.count(fixture.admin().getId(), false));
        assertEquals(0, this.userNotifications.count(fixture.superAdmin().getId(), false));
        verifyNoInteractions(this.sockets);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void ordersInOtherStatesCannotShipOrCreateBuyerNotifications(OrderStatus status) {
        Fixture fixture = this.fixture();
        this.setStatus(fixture, status);
        assertFalse(this.shipping.ship(fixture.order().getId()));
        assertEquals(status, this.orders.selectById(fixture.order().getId()).getStatus());
        assertEquals(0, this.userNotifications.count(fixture.buyer().getId(), false));
        verifyNoInteractions(this.sockets);
    }

    @Test
    void repeatedShippingDoesNotDuplicateTheNotificationOrResetItsReadState() {
        Fixture fixture = this.fixture();
        this.setStatus(fixture, OrderStatus.PAID);
        assertTrue(this.shipping.ship(fixture.order().getId()));
        OrderShipmentNotificationEntity notification = this.shipment(fixture);
        assertFalse(this.userNotifications.markRead(fixture.admin().getId(), notification.getId(), "ORDER_SHIPPED"));
        assertTrue(this.userNotifications.markRead(fixture.buyer().getId(), notification.getId(), "ORDER_SHIPPED"));
        assertFalse(this.shipping.ship(fixture.order().getId()));
        assertEquals(1, this.userNotifications.count(fixture.buyer().getId(), false));
        assertEquals(0, this.userNotifications.count(fixture.buyer().getId(), true));
        assertNotNull(this.shipment(fixture).getReadAt());
        verifyNoInteractions(this.sockets);
    }

    @Test
    void shipmentAndProductNotificationsShareOrderedPaginationAndOwnerScopedReadAll() {
        Fixture fixture = this.fixture();
        this.setStatus(fixture, OrderStatus.PAID);
        assertTrue(this.shipping.ship(fixture.order().getId()));
        LocalDateTime shippedAt = this.shipment(fixture).getShippedAt();
        this.productNotification(fixture.buyer(), 1L, shippedAt.plusSeconds(10));
        this.productNotification(fixture.buyer(), 2L, shippedAt.minusSeconds(10));
        this.productNotification(fixture.buyer(), 3L, shippedAt.minusSeconds(20));
        this.productNotification(fixture.admin(), 1L, shippedAt.plusSeconds(20));
        Integer buyer = fixture.buyer().getId();
        assertEquals(4, this.userNotifications.count(buyer, true));
        var first = this.userNotifications.fetch(buyer, false, Pagination.paginate(4, 2, 1));
        var second = this.userNotifications.fetch(buyer, false, Pagination.paginate(4, 2, 2));
        assertEquals(List.of("PRICE_DROP", "ORDER_SHIPPED"), first.stream().map(item -> item.getType()).toList());
        assertEquals(List.of(shippedAt.minusSeconds(10), shippedAt.minusSeconds(20)),
            second.stream().map(item -> item.getCreatedAt()).toList());
        assertEquals(fixture.order().getId(), first.get(1).getOrderId());
        assertEquals(fixture.order().getOrderNo(), first.get(1).getOrderNo());
        assertEquals(shippedAt, first.get(1).getShippedAt());
        this.userNotifications.markAllRead(buyer);
        assertEquals(0, this.userNotifications.count(buyer, true));
        assertEquals(4, this.userNotifications.count(buyer, false));
        assertEquals(1, this.userNotifications.count(fixture.admin().getId(), true));
        assertTrue(this.userNotifications.fetch(buyer, false, Pagination.paginate(4, 20, 1))
            .stream().allMatch(item -> item.getReadAt() != null));
        verifyNoInteractions(this.sockets);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollingBackShippingRestoresThePaidStateAndLeavesNoNotificationOrPush() {
        Fixture fixture = new TransactionTemplate(this.transactions).execute(transaction -> this.fixture());
        try {
            this.setStatus(fixture, OrderStatus.PAID);
            new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
                assertTrue(this.shipping.ship(fixture.order().getId()));
                assertEquals(1, this.userNotifications.count(fixture.buyer().getId(), true));
                verifyNoInteractions(this.sockets);
                transaction.setRollbackOnly();
            });
            assertEquals(OrderStatus.PAID, this.orders.selectById(fixture.order().getId()).getStatus());
            assertEquals(0, this.userNotifications.count(fixture.buyer().getId(), false));
            verifyNoInteractions(this.sockets);
        } finally { this.deleteShippingFixture(fixture); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void notificationSaveFailureRollsBackShipping() {
        Fixture fixture = new TransactionTemplate(this.transactions).execute(transaction -> this.fixture());
        try {
            this.setStatus(fixture, OrderStatus.PAID);
            OrderNotificationService recorder = AopTestUtils.getUltimateTargetObject(this.notifications);
            doThrow(new IllegalStateException("notification database unavailable"))
                .when(recorder).recordShippedOrder(fixture.order().getId());
            assertThrows(IllegalStateException.class, () -> this.shipping.ship(fixture.order().getId()));
            assertEquals(OrderStatus.PAID, this.orders.selectById(fixture.order().getId()).getStatus());
            assertEquals(0, this.userNotifications.count(fixture.buyer().getId(), false));
            verifyNoInteractions(this.sockets);
        } finally { this.deleteShippingFixture(fixture); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shippingCommitKeepsTheOfflineNotificationEvenIfItsRealtimePushFails() {
        Fixture fixture = new TransactionTemplate(this.transactions).execute(transaction -> this.fixture());
        try {
            this.setStatus(fixture, OrderStatus.PAID);
            doThrow(new IllegalStateException("socket closed")).when(this.sockets).sendShipmentNotification(any());
            assertTrue(assertDoesNotThrow(() -> this.shipping.ship(fixture.order().getId())));
            assertEquals(OrderStatus.SHIPPED, this.orders.selectById(fixture.order().getId()).getStatus());
            assertEquals(1, this.userNotifications.count(fixture.buyer().getId(), true));
            verify(this.sockets).sendShipmentNotification(this.shipment(fixture));
        } finally { this.deleteShippingFixture(fixture); }
    }

    private void setStatus(Fixture fixture, OrderStatus status) {
        fixture.order().setStatus(status);
        this.orders.updateById(fixture.order());
    }

    private OrderShipmentNotificationEntity shipment(Fixture fixture) {
        return this.shipmentRows.selectOne(Wrappers.<OrderShipmentNotificationEntity>lambdaQuery()
            .eq(OrderShipmentNotificationEntity::getOrderId, fixture.order().getId()));
    }

    private void productNotification(UserEntity user, Long eventId, LocalDateTime createdAt) {
        ProductNotificationEntity notification = new ProductNotificationEntity();
        notification.setUserId(user.getId());
        notification.setEventId(eventId);
        notification.setProductId(11);
        notification.setType(ProductNotificationType.PRICE_DROP);
        notification.setProductName("通知排序测试商品");
        notification.setOldPrice(new BigDecimal("100.00"));
        notification.setNewPrice(new BigDecimal("80.00"));
        notification.setCreatedAt(createdAt);
        this.productRows.insert(notification);
    }

    private void deleteShippingFixture(Fixture fixture) {
        new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
            this.shipmentRows.delete(Wrappers.<OrderShipmentNotificationEntity>lambdaQuery()
                .eq(OrderShipmentNotificationEntity::getOrderId, fixture.order().getId()));
            this.orders.deleteById(fixture.order().getId());
            fixture.users().forEach(user -> this.users.deleteById(user.getId()));
        });
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
