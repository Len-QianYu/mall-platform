package com.example.api.service.implementation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.configuration.CacheConfiguration;
import com.example.api.entity.ProductEntity;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.ProductService;
import com.example.api.utils.Pagination;

// 前台读接口（countOnSale / fetchOnSale / fetchOnSaleById / fetchOnSaleCategories）走 Redis 缓存，
// 管理端和下单流程的读取都直接查库：要拿最新数据，不能用缓存里可能旧了的库存和价格
@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductMapper productMapper;

    private final CacheManager cacheManager;

    public ProductServiceImpl(ProductMapper productMapper, CacheManager cacheManager) {
        this.productMapper = productMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    public Integer count(String keyword, String category, Integer status) {
        return this.productMapper.selectCount(this.filter(keyword, category, status)).intValue();
    }

    @Override
    public List<ProductEntity> fetch(String keyword, String category, Integer status, Pagination pagination) {
        // 总数已由 count 查过，分页插件只负责拼 LIMIT（searchCount = false，不重复计数）
        return this.productMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(keyword, category, status).orderByDesc(ProductEntity::getId)
        );
    }

    @Override
    @Cacheable(cacheNames = CacheConfiguration.PRODUCT_COUNT, key = "T(com.example.api.utils.CacheKeys).join(#keyword, #category)")
    public Integer countOnSale(String keyword, String category) {
        return this.count(keyword, category, 1);
    }

    // 用页码校正后的 pagination 拼 key：超出总页数的页码和最后一页共用一条缓存
    @Override
    @Cacheable(
        cacheNames = CacheConfiguration.PRODUCT_LIST,
        key = "T(com.example.api.utils.CacheKeys).join(#keyword, #category, #sort, #pagination.currentPage, #pagination.pageSize)"
    )
    public List<ProductEntity> fetchOnSale(String keyword, String category, String sort, Pagination pagination) {
        LambdaQueryWrapper<ProductEntity> wrapper = this.filter(keyword, category, 1);

        // 排序值来自 URL，只认白名单内的几种，其余一律按综合（新建的在前）
        switch (sort == null ? "default" : sort) {
            case "sales" -> wrapper.orderByDesc(ProductEntity::getSales);
            case "newest" -> wrapper.orderByDesc(ProductEntity::getCreatedAt);
            case "price_asc" -> wrapper.orderByAsc(ProductEntity::getPrice);
            case "price_desc" -> wrapper.orderByDesc(ProductEntity::getPrice);
            default -> { }
        }
        // 主键兜底：排序列有并列时翻页结果仍然稳定，不会同一商品出现在两页
        wrapper.orderByDesc(ProductEntity::getId);

        return this.productMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            wrapper
        );
    }

    @Override
    public Optional<ProductEntity> fetchById(Integer id) {
        return Optional.ofNullable(this.productMapper.selectById(id));
    }

    // 不存在和已下架的也会缓存（空值，1 分钟），挡住反复查同一个无效 ID 的请求
    @Override
    @Cacheable(cacheNames = CacheConfiguration.PRODUCT_DETAIL, key = "#id")
    public Optional<ProductEntity> fetchOnSaleById(Integer id) {
        return this.fetchById(id).filter(product -> product.getStatus() == 1);
    }

    @Override
    public List<String> fetchCategories() {
        return this.productMapper.selectCategories();
    }

    @Override
    @Cacheable(cacheNames = CacheConfiguration.PRODUCT_CATEGORIES, key = "'on-sale'")
    public List<String> fetchOnSaleCategories() {
        return this.productMapper.selectOnSaleCategories();
    }

    @Override
    public Integer create(ProductEntity product) {
        Integer rows = this.productMapper.insert(product);
        this.evictCatalog(product.getId());
        return rows;
    }

    @Override
    public Integer update(ProductEntity product) {
        // 逐列 set 而不用 updateById：updateById 会跳过 null 字段，选填项清空后存不回 NULL；
        // 销量由下单流程累加，不在可改的列里
        Integer rows = this.productMapper.update(
            Wrappers.<ProductEntity>lambdaUpdate()
                .set(ProductEntity::getName, product.getName())
                .set(ProductEntity::getSubtitle, product.getSubtitle())
                .set(ProductEntity::getDescription, product.getDescription())
                .set(ProductEntity::getCategory, product.getCategory())
                .set(ProductEntity::getPrice, product.getPrice())
                .set(ProductEntity::getStock, product.getStock())
                .set(ProductEntity::getMainImage, product.getMainImage())
                .set(ProductEntity::getStatus, product.getStatus())
                .eq(ProductEntity::getId, product.getId())
        );
        this.evictCatalog(product.getId());
        return rows;
    }

    @Override
    public Integer updateStatus(Integer id, Integer status) {
        Integer rows = this.productMapper.update(
            Wrappers.<ProductEntity>lambdaUpdate()
                .set(ProductEntity::getStatus, status)
                .eq(ProductEntity::getId, id)
        );
        this.evictCatalog(id);
        return rows;
    }

    @Override
    public boolean deductStock(Integer id, Integer quantity) {
        boolean deducted = this.productMapper.deductStock(id, quantity) > 0;
        if (deducted) {
            this.evictDetail(id);
        }
        return deducted;
    }

    @Override
    public void restoreStock(Integer id, Integer quantity) {
        this.productMapper.restoreStock(id, quantity);
        this.evictDetail(id);
    }

    @Override
    public void addSales(Integer id, Integer quantity) {
        this.productMapper.addSales(id, quantity);
        this.evictDetail(id);
    }

    @Override
    public void subtractSales(Integer id, Integer quantity) {
        this.productMapper.subtractSales(id, quantity);
        this.evictDetail(id);
    }

    // 列表筛选条件：count 和 fetch 共用，保证总数和当前页按同一条件查询
    private LambdaQueryWrapper<ProductEntity> filter(String keyword, String category, Integer status) {
        return Wrappers.<ProductEntity>lambdaQuery()
            .like(StringUtils.hasText(keyword), ProductEntity::getName, keyword)
            .eq(StringUtils.hasText(category), ProductEntity::getCategory, category)
            .eq(status != null, ProductEntity::getStatus, status);
    }

    // 后台新增、修改、上下架：删这件商品的详情（新增时可能已缓存了空值），列表、计数、分类整个清空——
    // 改名、改价、上下架可能影响任意一页、任意关键字的结果，没法逐条找出来；后台改商品不频繁，清空的代价可以接受
    private void evictCatalog(Integer id) {
        this.evictAfterCommit(() -> {
            this.cache(CacheConfiguration.PRODUCT_DETAIL).evict(id);
            this.cache(CacheConfiguration.PRODUCT_LIST).clear();
            this.cache(CacheConfiguration.PRODUCT_COUNT).clear();
            this.cache(CacheConfiguration.PRODUCT_CATEGORIES).clear();
        });
    }

    // 下单、取消、支付、退款改了库存或销量：只删详情。列表里的库存、销量允许旧 1 分钟（列表缓存的过期时间），
    // 否则每下一单都要清空全部列表缓存，热门列表几乎总是查库
    private void evictDetail(Integer id) {
        this.evictAfterCommit(() -> this.cache(CacheConfiguration.PRODUCT_DETAIL).evict(id));
    }

    // 在事务里调用时，等事务提交后再删：提交前就删的话，别的请求可能在提交前又把旧数据读回缓存
    // （普通查询不受行锁影响，读到的是提交前的旧版本），之后一直读到旧数据直到过期。
    // 删除失败（Redis 连不上）只记日志：数据库已经改好了，不能因为缓存让下单、支付报错，旧缓存最晚到过期时自动更新
    private void evictAfterCommit(Runnable eviction) {
        Runnable quietly = () -> {
            try {
                eviction.run();
            } catch (RuntimeException error) {
                log.warn("删除商品缓存失败，缓存里的旧数据要等过期后才会更新", error);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            quietly.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                quietly.run();
            }
        });
    }

    private Cache cache(String name) {
        return Objects.requireNonNull(this.cacheManager.getCache(name));
    }

}
