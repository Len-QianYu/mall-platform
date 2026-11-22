package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
import com.example.api.entity.ProductEntity;
import com.example.api.entity.ProductFavoriteEntity;
import com.example.api.entity.ProductNotificationEntity;
import com.example.api.entity.ProductNotificationEventEntity;
import com.example.api.enums.ProductNotificationType;
import com.example.api.mapper.ProductFavoriteMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mapper.ProductNotificationEventMapper;
import com.example.api.mapper.ProductNotificationMapper;
import com.example.api.service.ProductFavoriteService;
import com.example.api.service.ProductNotificationService;
import com.example.api.service.ProductService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.websocket.ProductNotificationWebSocketHandler;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false" })
@Transactional
class ProductNotificationPersistenceTests {
    @Autowired private ProductMapper products;
    @Autowired private ProductFavoriteMapper favoriteRows;
    @Autowired private ProductNotificationMapper notificationRows;
    @Autowired private ProductNotificationEventMapper events;
    @Autowired private ProductService productService;
    @Autowired private ProductFavoriteService favorites;
    @Autowired private ProductNotificationService notifications;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private CacheManager caches;
    @MockitoBean private ProductNotificationWebSocketHandler sockets;
    private int owner;

    @BeforeEach
    void setUp() {
        this.owner = ThreadLocalRandom.current().nextInt(100000000, 1000000000);
        when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class));
    }

    @Test
    void favoritesAreIdempotentAndKeepDelistedProductsForRelistingAlerts() {
        ProductEntity product = this.product(0);
        this.favorites.setFavorite(this.owner, product.getId(), true);
        ProductFavoriteEntity original = this.favoriteRows.selectOne(Wrappers.<ProductFavoriteEntity>lambdaQuery()
            .eq(ProductFavoriteEntity::getUserId, this.owner));
        this.favorites.setFavorite(this.owner, product.getId(), true);
        ProductFavoriteEntity repeated = this.favoriteRows.selectById(original.getId());
        assertEquals(original.getCreatedAt(), repeated.getCreatedAt());
        assertEquals(List.of(product.getId()), this.favorites.fetchIds(this.owner));
        assertEquals(1, this.favorites.count(this.owner));
        this.favorites.setFavorite(this.owner + 1, product.getId(), false);
        assertEquals(1, this.favorites.count(this.owner));
    }

    @Test
    void relistingPriceDropsAndPriceRisesCreatePersistentSnapshotNotifications() {
        ProductEntity product = this.product(0);
        this.favorites.setFavorite(this.owner, product.getId(), true);
        this.productService.updateStatus(product.getId(), 1);
        product.setStatus(1);
        this.price(product, "80.00");
        this.price(product, "120.00");
        List<ProductNotificationEventEntity> changes = this.changes(product);
        assertEquals(List.of(ProductNotificationType.LISTED, ProductNotificationType.PRICE_DROP,
            ProductNotificationType.PRICE_RISE), changes.stream().map(ProductNotificationEventEntity::getType).toList());
        changes.forEach(change -> this.notifications.consumeEvent(change.getId()));
        List<ProductNotificationEntity> received = this.received();
        assertEquals(3, received.size());
        assertEquals(new BigDecimal("100.00"), received.get(1).getOldPrice());
        assertEquals(new BigDecimal("80.00"), received.get(1).getNewPrice());
        assertEquals(new BigDecimal("120.00"), received.get(2).getNewPrice());
        assertEquals(3, this.notifications.count(this.owner, true));
        verifyNoInteractions(this.sockets); // 外层事务尚未提交，不能提前推送。
    }

    @Test
    void unchangedPricesRepeatedStatusAndOtherEditsDoNotNotify() {
        ProductEntity product = this.product(1);
        this.productService.updateStatus(product.getId(), 1);
        product.setName("renamed-" + UUID.randomUUID());
        product.setPrice(new BigDecimal("100.0"));
        this.productService.update(product);
        this.productService.updateStatus(product.getId(), 0);
        assertTrue(this.changes(product).isEmpty());
    }

    @Test
    void simultaneousRelistingAndPriceChangeProduceBothAlerts() {
        ProductEntity product = this.product(0);
        product.setStatus(1);
        this.price(product, "70.00");
        assertEquals(List.of(ProductNotificationType.LISTED, ProductNotificationType.PRICE_DROP),
            this.changes(product).stream().map(ProductNotificationEventEntity::getType).toList());
    }

    @Test
    void canceledAndNewSubscriptionsDoNotReceiveAnOlderQueuedEvent() {
        ProductEntity product = this.product(1);
        this.favorites.setFavorite(this.owner, product.getId(), true);
        this.price(product, "80.00");
        this.favorites.setFavorite(this.owner, product.getId(), false);
        this.favorites.setFavorite(this.owner + 1, product.getId(), true);
        this.favorites.setFavorite(this.owner, product.getId(), true);
        this.notifications.consumeEvent(this.changes(product).getFirst().getId());
        assertEquals(0, this.notifications.count(this.owner, false));
        assertEquals(0, this.notifications.count(this.owner + 1, false));
    }

    @Test
    void duplicateDeliveryPreservesReadStateAndCannotMarkAnotherOwnersNotifications() {
        ProductEntity product = this.product(1);
        this.favorites.setFavorite(this.owner, product.getId(), true);
        this.price(product, "80.00");
        Long eventId = this.changes(product).getFirst().getId();
        this.notifications.consumeEvent(eventId);
        ProductNotificationEntity notification = this.received().getFirst();
        assertFalse(this.notifications.markRead(this.owner + 1, notification.getId()));
        this.notifications.markAllRead(this.owner + 1);
        assertEquals(1, this.notifications.count(this.owner, true));
        assertTrue(this.notifications.markRead(this.owner, notification.getId()));
        assertNotNull(this.notificationRows.selectById(notification.getId()).getReadAt());
        this.notifications.consumeEvent(eventId);
        assertEquals(1, this.notifications.count(this.owner, false));
        assertEquals(0, this.notifications.count(this.owner, true));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollingBackProductEditAlsoRollsBackItsNotificationEvent() {
        ProductEntity product = this.product(1);
        try {
            new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
                this.price(product, "80.00");
                assertEquals(1, this.changes(product).size());
                transaction.setRollbackOnly();
            });
            assertEquals(new BigDecimal("100.00"), this.products.selectById(product.getId()).getPrice());
            assertTrue(this.changes(product).isEmpty());
        } finally {
            this.events.delete(Wrappers.<ProductNotificationEventEntity>lambdaQuery()
                .eq(ProductNotificationEventEntity::getProductId, product.getId()));
            this.products.deleteById(product.getId());
        }
    }

    private ProductEntity product(int status) {
        ProductEntity product = new ProductEntity();
        product.setName("notification-" + UUID.randomUUID());
        product.setCategory("通知测试");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(10);
        product.setSales(0);
        product.setStatus(status);
        this.products.insert(product);
        return product;
    }

    private void price(ProductEntity product, String price) {
        product.setPrice(new BigDecimal(price));
        assertEquals(1, this.productService.update(product));
    }

    private List<ProductNotificationEventEntity> changes(ProductEntity product) {
        return this.events.selectList(Wrappers.<ProductNotificationEventEntity>lambdaQuery()
            .eq(ProductNotificationEventEntity::getProductId, product.getId()).orderByAsc(ProductNotificationEventEntity::getId));
    }

    private List<ProductNotificationEntity> received() {
        return this.notificationRows.selectList(Wrappers.<ProductNotificationEntity>lambdaQuery()
            .eq(ProductNotificationEntity::getUserId, this.owner).orderByAsc(ProductNotificationEntity::getId));
    }
}
