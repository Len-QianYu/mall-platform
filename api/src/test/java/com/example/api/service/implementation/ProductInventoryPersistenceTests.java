package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.entity.ProductEntity;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.ProductService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false",
    "seckill.expiry-enabled=false" })
@Transactional
class ProductInventoryPersistenceTests {
    @Autowired private ProductMapper mapper;
    @Autowired private ProductService service;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private CacheManager caches;

    @BeforeEach
    void cache() { when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class)); }

    @Test
    void savingAnOldProductFormAfterAnOrderDoesNotRestoreDeductedInventory() {
        ProductEntity product = this.product();
        ProductEntity staleForm = this.mapper.selectById(product.getId());
        assertTrue(this.service.deductStock(product.getId(), 3));
        staleForm.setName("edited-" + UUID.randomUUID());
        assertEquals(1, this.service.update(staleForm));
        ProductEntity saved = this.mapper.selectById(product.getId());
        assertEquals(7, saved.getStock());
        assertEquals(staleForm.getName(), saved.getName());
    }

    @Test
    void stockAdjustmentsApplyToCurrentInventoryAndRejectUnderflowOverflowAndZero() {
        ProductEntity product = this.product();
        assertTrue(this.service.deductStock(product.getId(), 3));
        assertTrue(this.service.adjustStock(product.getId(), 5));
        assertEquals(12, this.mapper.selectById(product.getId()).getStock());
        assertFalse(this.service.adjustStock(product.getId(), -13));
        assertFalse(this.service.adjustStock(product.getId(), Integer.MAX_VALUE));
        assertFalse(this.service.adjustStock(product.getId(), 0));
        assertFalse(this.service.deductStock(product.getId(), 0));
        assertFalse(this.service.deductStock(product.getId(), -1));
        assertEquals(12, this.mapper.selectById(product.getId()).getStock());
        assertTrue(this.service.adjustStock(product.getId(), -12));
        assertEquals(0, this.mapper.selectById(product.getId()).getStock());
    }

    private ProductEntity product() {
        ProductEntity product = new ProductEntity();
        product.setName("inventory-" + UUID.randomUUID());
        product.setCategory("测试");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(10);
        product.setSales(0);
        product.setStatus(1);
        this.mapper.insert(product);
        return product;
    }
}
