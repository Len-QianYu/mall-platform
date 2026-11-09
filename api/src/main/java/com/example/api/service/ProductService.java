package com.example.api.service;

import java.util.List;
import java.util.Optional;

import com.example.api.entity.ProductEntity;
import com.example.api.utils.Pagination;

public interface ProductService {

    // 按条件统计商品数（条件为 null 时不筛选）
    Integer count(String keyword, String category, Integer status);

    // 按条件查询一页商品，新建的在前
    List<ProductEntity> fetch(String keyword, String category, Integer status, Pagination pagination);

    // 前台商品数：只统计上架商品，和 fetchOnSale 条件一致
    Integer countOnSale(String keyword, String category);

    // 前台商品列表：只查上架商品；sort 取值 default / sales / newest / price_asc / price_desc
    List<ProductEntity> fetchOnSale(String keyword, String category, String sort, Pagination pagination);

    Optional<ProductEntity> fetchById(Integer id);

    // 前台商品详情：只返回上架商品，已下架的和不存在一样返回空
    Optional<ProductEntity> fetchOnSaleById(Integer id);

    // 已用过的全部分类（去重）
    List<String> fetchCategories();

    // 有上架商品的分类（前台用）
    List<String> fetchOnSaleCategories();

    // 创建商品，返回受影响行数
    Integer create(ProductEntity product);

    // 修改商品，返回受影响行数（0 表示商品不存在）
    Integer update(ProductEntity product);

    // 上架 / 下架，返回受影响行数（0 表示商品不存在）
    Integer updateStatus(Integer id, Integer status);

    // 以下由订单、支付流程在各自的事务里调用：商品行一改，详情缓存在事务提交后删除

    // 下单扣库存：在售且库存够才扣，返回是否扣成功
    boolean deductStock(Integer id, Integer quantity);

    // 取消订单、退款时回补库存
    void restoreStock(Integer id, Integer quantity);

    // 支付成功后累加销量
    void addSales(Integer id, Integer quantity);

    // 退款扣回销量
    void subtractSales(Integer id, Integer quantity);

}
