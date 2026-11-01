package com.example.api.service.implementation;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.entity.ProductEntity;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.ProductService;
import com.example.api.utils.Pagination;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
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
    public Integer countOnSale(String keyword, String category) {
        return this.count(keyword, category, 1);
    }

    @Override
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

    @Override
    public List<String> fetchCategories() {
        return this.productMapper.selectCategories();
    }

    @Override
    public List<String> fetchOnSaleCategories() {
        return this.productMapper.selectOnSaleCategories();
    }

    @Override
    public Integer create(ProductEntity product) {
        return this.productMapper.insert(product);
    }

    @Override
    public Integer update(ProductEntity product) {
        // 逐列 set 而不用 updateById：updateById 会跳过 null 字段，选填项清空后存不回 NULL；
        // 销量由下单流程累加，不在可改的列里
        return this.productMapper.update(
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
    }

    @Override
    public Integer updateStatus(Integer id, Integer status) {
        return this.productMapper.update(
            Wrappers.<ProductEntity>lambdaUpdate()
                .set(ProductEntity::getStatus, status)
                .eq(ProductEntity::getId, id)
        );
    }

    // 列表筛选条件：count 和 fetch 共用，保证总数和当前页按同一条件查询
    private LambdaQueryWrapper<ProductEntity> filter(String keyword, String category, Integer status) {
        return Wrappers.<ProductEntity>lambdaQuery()
            .like(StringUtils.hasText(keyword), ProductEntity::getName, keyword)
            .eq(StringUtils.hasText(category), ProductEntity::getCategory, category)
            .eq(status != null, ProductEntity::getStatus, status);
    }

}
