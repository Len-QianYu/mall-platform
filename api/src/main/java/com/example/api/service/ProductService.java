package com.example.api.service;

import java.util.List;

import com.example.api.entity.ProductEntity;
import com.example.api.utils.Pagination;

public interface ProductService {

    // 按条件统计商品数（条件为 null 时不筛选）
    Integer count(String keyword, String category, Integer status);

    // 按条件查询一页商品，新建的在前
    List<ProductEntity> fetch(String keyword, String category, Integer status, Pagination pagination);

    // 已用过的全部分类（去重）
    List<String> fetchCategories();

    // 创建商品，返回受影响行数
    Integer create(ProductEntity product);

    // 修改商品，返回受影响行数（0 表示商品不存在）
    Integer update(ProductEntity product);

    // 上架 / 下架，返回受影响行数（0 表示商品不存在）
    Integer updateStatus(Integer id, Integer status);

}
