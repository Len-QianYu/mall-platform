package com.example.api.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.utils.Pagination;

public interface OrderService {

    // 按条件统计订单数（条件为 null 时不筛选）
    Integer count(String keyword, OrderStatus status);

    // 按条件查询一页订单，新下的在前
    List<OrderEntity> fetch(String keyword, OrderStatus status, Pagination pagination);

    // 最近下的若干笔订单（仪表盘用）
    List<OrderEntity> fetchRecent(Integer limit);

    Optional<OrderEntity> fetchById(Long id);

    // 订单的商品明细
    List<OrderItemEntity> fetchItems(Long orderId);

    // 已支付、已发货、已完成订单的金额合计
    BigDecimal sumSalesAmount();

    // 发货：只有已支付的订单能发货，返回是否成功（订单不存在或状态不对时为 false）
    boolean ship(Long id);

    // 取消：只有待支付的订单能取消，返回是否成功（订单不存在或状态不对时为 false）
    boolean cancel(Long id);

}
