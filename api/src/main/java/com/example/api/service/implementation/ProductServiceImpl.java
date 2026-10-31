package com.example.api.service.implementation;

import java.util.List;

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
    public List<String> fetchCategories() {
        return this.productMapper.selectCategories();
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
