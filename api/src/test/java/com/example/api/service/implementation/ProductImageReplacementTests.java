package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.FileStorageService;
import com.example.api.service.ProductService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false",
    "seckill.expiry-enabled=false" })
class ProductImageReplacementTests {

    @Autowired private ProductMapper products;
    @Autowired private OrderItemMapper orderItems;
    @Autowired private ProductService service;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private FileStorageService files;
    @MockitoBean private CacheManager caches;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;

    private final List<Integer> createdProducts = new ArrayList<>();
    private final List<Long> createdOrderItems = new ArrayList<>();

    @BeforeEach
    void cache() {
        when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class));
    }

    @AfterEach
    void cleanUp() {
        this.createdOrderItems.forEach(this.orderItems::deleteById);
        this.createdProducts.forEach(this.products::deleteById);
    }

    @Test
    void deletesTheOldImageOnlyAfterTheNewImageHasCommitted() throws Exception {
        ProductEntity product = this.product(image());
        String oldImage = product.getMainImage();
        String newImage = image();

        new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
            assertEquals(1, this.replace(product, newImage));
            assertEquals(newImage, this.products.selectById(product.getId()).getMainImage());
            verifyNoInteractions(this.files);
        });

        assertEquals(newImage, this.products.selectById(product.getId()).getMainImage());
        verify(this.files).delete(oldImage);
    }

    @Test
    void rollbackKeepsTheOriginalDatabaseImageAndDoesNotDeleteAnyObject() {
        ProductEntity product = this.product(image());
        String oldImage = product.getMainImage();

        new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
            assertEquals(1, this.replace(product, image()));
            verifyNoInteractions(this.files);
            transaction.setRollbackOnly();
        });

        assertEquals(oldImage, this.products.selectById(product.getId()).getMainImage());
        verifyNoInteractions(this.files);
    }

    @Test
    void failedOldImageDeletionDoesNotFailOrUndoTheProductUpdate() throws Exception {
        ProductEntity product = this.product(image());
        String oldImage = product.getMainImage();
        String newImage = image();
        doThrow(new IOException("RustFS offline")).when(this.files).delete(oldImage);

        assertEquals(1, assertDoesNotThrow(() -> this.replace(product, newImage)));

        assertEquals(newImage, this.products.selectById(product.getId()).getMainImage());
        verify(this.files).delete(oldImage);
    }

    @Test
    void runtimeCleanupFailuresAlsoLeaveTheCommittedNewImageIntact() throws Exception {
        ProductEntity product = this.product(image());
        String oldImage = product.getMainImage();
        String newImage = image();
        doThrow(new IllegalStateException("storage unavailable")).when(this.files).delete(oldImage);

        assertEquals(1, assertDoesNotThrow(() -> this.replace(product, newImage)));

        assertEquals(newImage, this.products.selectById(product.getId()).getMainImage());
    }

    @Test
    void unchangedImagesAndMissingProductsDoNotTriggerObjectDeletion() {
        ProductEntity product = this.product(image());
        assertEquals(1, this.replace(product, product.getMainImage()));
        this.products.deleteById(product.getId());

        assertEquals(0, this.replace(product, image()));

        verifyNoInteractions(this.files);
    }

    @Test
    void removingTheImageDeletesTheUnusedOldObjectAfterCommit() throws Exception {
        ProductEntity product = this.product(image());
        String oldImage = product.getMainImage();

        assertEquals(1, this.replace(product, null));

        assertNull(this.products.selectById(product.getId()).getMainImage());
        verify(this.files).delete(oldImage);
    }

    @Test
    void sharedImagesAreDeletedOnlyAfterTheLastProductReleasesThem() throws Exception {
        String oldImage = image();
        ProductEntity first = this.product(oldImage);
        ProductEntity second = this.product(oldImage);

        assertEquals(1, this.replace(first, image()));
        verifyNoInteractions(this.files);
        assertEquals(oldImage, this.products.selectById(second.getId()).getMainImage());

        assertEquals(1, this.replace(second, image()));

        verify(this.files).delete(oldImage);
    }

    @Test
    void historyOrderSnapshotsKeepTheirReferencedImages() {
        ProductEntity product = this.product(image());
        String oldImage = product.getMainImage();
        OrderItemEntity snapshot = new OrderItemEntity();
        snapshot.setOrderId(0L);
        snapshot.setProductId(product.getId());
        snapshot.setProductName(product.getName());
        snapshot.setProductImage(oldImage);
        snapshot.setUnitPrice(product.getPrice());
        snapshot.setQuantity(1);
        this.orderItems.insert(snapshot);
        this.createdOrderItems.add(snapshot.getId());

        assertEquals(1, this.replace(product, image()));

        assertEquals(oldImage, this.orderItems.selectById(snapshot.getId()).getProductImage());
        verifyNoInteractions(this.files);
    }

    private int replace(ProductEntity product, String image) {
        product.setMainImage(image);
        return this.service.update(product);
    }

    private ProductEntity product(String image) {
        ProductEntity product = new ProductEntity();
        product.setName("image-replacement-" + UUID.randomUUID());
        product.setCategory("测试");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(10);
        product.setSales(0);
        product.setStatus(1);
        product.setMainImage(image);
        this.products.insert(product);
        this.createdProducts.add(product.getId());
        return product;
    }

    private static String image() {
        return "/images/image-replacement-" + UUID.randomUUID() + ".png";
    }
}
