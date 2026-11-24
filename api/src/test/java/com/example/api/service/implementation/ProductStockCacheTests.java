package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.configuration.CacheConfiguration;
import com.example.api.entity.ProductEntity;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.FileStorageService;
import com.example.api.service.ProductNotificationEventRecorder;
import com.example.api.service.ProductService;
import com.example.api.utils.Pagination;

@SpringJUnitConfig(ProductStockCacheTests.Config.class)
class ProductStockCacheTests {

    private static final int PRODUCT_ID = 123;

    @Autowired private ProductService service;
    @Autowired private CacheManager caches;
    @MockitoBean private ProductMapper mapper;
    @MockitoBean private ProductNotificationEventRecorder notificationEvents;
    @MockitoBean private FileStorageService fileStorage;

    private final AtomicInteger stock = new AtomicInteger();
    private TransactionTemplate transactions;

    @BeforeAll
    static void productMapping() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), "product-stock-cache-tests"),
            ProductEntity.class
        );
    }

    @BeforeEach
    void setUp() {
        this.caches.getCacheNames().forEach(name -> this.caches.getCache(name).clear());
        this.transactions = new TransactionTemplate(new StockTransactionManager(this.stock));
        // Each database read returns a separate snapshot, as Redis serialization does in production.
        when(this.mapper.selectList(any(Page.class), any())).thenAnswer(invocation -> List.of(this.product()));
        when(this.mapper.selectById(PRODUCT_ID)).thenAnswer(invocation -> this.product());
        when(this.mapper.adjustStock(eq(PRODUCT_ID), anyInt())).thenAnswer(invocation -> {
            long next = (long) this.stock.get() + invocation.<Integer>getArgument(1);
            if (next < 0 || next > Integer.MAX_VALUE) return 0;
            this.stock.set((int) next);
            return 1;
        });
    }

    @Test
    void restockingAndSellingOutRefreshBothHomeAndFilteredListings() {
        this.assertDisplayedStock(0);
        this.assertDisplayedStock(0);
        this.verifyCachedReads();

        assertTrue(this.service.adjustStock(PRODUCT_ID, 5));
        this.assertDisplayedStock(5);

        assertTrue(this.service.adjustStock(PRODUCT_ID, -5));
        this.assertDisplayedStock(0);
    }

    @Test
    void stockChangesBecomeVisibleInCachedProductsOnlyAfterCommit() {
        this.assertDisplayedStock(0);

        this.transactions.executeWithoutResult(status -> {
            assertTrue(this.service.adjustStock(PRODUCT_ID, 5));
            this.assertDisplayedStock(0);
        });

        this.assertDisplayedStock(5);
    }

    @Test
    void rollingBackAnAdjustmentPreservesTheCachedProducts() {
        this.assertDisplayedStock(0);

        this.transactions.executeWithoutResult(status -> {
            assertTrue(this.service.adjustStock(PRODUCT_ID, 5));
            status.setRollbackOnly();
        });

        assertEquals(0, this.stock.get());
        this.assertDisplayedStock(0);
        this.verifyCachedReads();
    }

    @Test
    void rejectedAdjustmentsKeepTheExistingCachedProducts() {
        this.assertDisplayedStock(0);

        assertFalse(this.service.adjustStock(PRODUCT_ID, -1));

        this.assertDisplayedStock(0);
        this.verifyCachedReads();
    }

    private void assertDisplayedStock(int expected) {
        List<ProductEntity> home = this.service.fetchOnSale(null, null, "sales", Pagination.paginate(1, 4, 1));
        List<ProductEntity> filtered = this.service.fetchOnSale("cache-test", "测试", null, Pagination.paginate(1, 20, 1));
        assertEquals(expected, home.getFirst().getStock());
        assertEquals(expected, filtered.getFirst().getStock());
        assertEquals(expected, this.service.fetchOnSaleById(PRODUCT_ID).orElseThrow().getStock());
    }

    private void verifyCachedReads() {
        verify(this.mapper, times(2)).selectList(any(Page.class), any());
        verify(this.mapper).selectById(PRODUCT_ID);
    }

    private ProductEntity product() {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setName("cache-test");
        product.setCategory("测试");
        product.setPrice(BigDecimal.ONE);
        product.setStock(this.stock.get());
        product.setSales(0);
        product.setStatus(1);
        return product;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @Import(ProductServiceImpl.class)
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                CacheConfiguration.PRODUCT_DETAIL, CacheConfiguration.PRODUCT_LIST,
                CacheConfiguration.PRODUCT_COUNT, CacheConfiguration.PRODUCT_CATEGORIES
            );
        }
    }

    // Run Spring's real commit/rollback callbacks without connecting to a business database.
    private static class StockTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicInteger stock;

        StockTransactionManager(AtomicInteger stock) {
            this.stock = stock;
        }

        @Override
        protected Object doGetTransaction() {
            return this.stock.get();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { }

        @Override
        protected void doCommit(DefaultTransactionStatus status) { }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            this.stock.set((Integer) status.getTransaction());
        }
    }
}
