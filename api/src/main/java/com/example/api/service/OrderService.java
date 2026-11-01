package com.example.api.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.example.api.dto.OrderWithItemsView;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.utils.Pagination;

public interface OrderService {

    // 按条件统计订单数（条件为 null 时不筛选）
    Integer count(String keyword, OrderStatus status);

    // 按条件查询一页订单，新下的在前
    List<OrderEntity> fetch(String keyword, OrderStatus status, Pagination pagination);

    // 某用户的订单数（status 为 null 时不筛选）
    Integer countForUser(Integer userId, OrderStatus status);

    // 某用户的一页订单，每个订单带上商品明细，新下的在前
    List<OrderWithItemsView> fetchForUser(Integer userId, OrderStatus status, Pagination pagination);

    // 最近下的若干笔订单（仪表盘用）
    List<OrderEntity> fetchRecent(Integer limit);

    Optional<OrderEntity> fetchById(Long id);

    // 只查该用户自己的订单：别人的订单和不存在一样返回空，不暴露订单是否存在
    Optional<OrderEntity> fetchByIdForUser(Long id, Integer userId);

    // 订单的商品明细
    List<OrderItemEntity> fetchItems(Long orderId);

    // 已支付、已发货、已完成订单的金额合计
    BigDecimal sumSalesAmount();

    // 下单：扣库存，按商品当前售价生成订单和明细，fromCart 时把这些商品移出购物车，返回新订单。
    // 商品下架或库存不足时抛 TradeFailedException，已扣的库存随事务回滚
    OrderEntity create(Integer userId, TradeRequests.OrderCreate request);

    // 发货：只有已支付的订单能发货，返回是否成功（订单不存在或状态不对时为 false）
    boolean ship(Long id);

    // 取消：只有待支付的订单能取消，取消后回补库存；返回是否成功（订单不存在或状态不对时为 false）
    boolean cancel(Long id);

    // 确认收货：只有已发货的订单能确认，返回是否成功（订单不存在或状态不对时为 false）
    boolean confirm(Long id);

}
