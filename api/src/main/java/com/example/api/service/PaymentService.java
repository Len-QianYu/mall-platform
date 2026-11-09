package com.example.api.service;

import java.util.List;

import com.example.api.entity.OrderEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.utils.Pagination;

public interface PaymentService {

    // 按条件统计支付流水数（条件为 null 时不筛选）
    Integer count(String keyword, PaymentMethod method, PaymentStatus status);

    // 按条件查询一页支付流水，新的在前
    List<PaymentEntity> fetch(String keyword, PaymentMethod method, PaymentStatus status, Pagination pagination);

    // 支付订单：订单改为已支付、写一条成功的流水、累加商品销量，返回流水。
    // 订单已不是待支付（已付过、已取消）时抛 TradeFailedException
    PaymentEntity pay(OrderEntity order, PaymentMethod method);

    // 退款：只有已支付、未发货的订单能退。订单改为已退款、成功的流水改为已退款、回补库存、扣回销量；
    // 返回是否成功（订单不存在或状态不对时为 false）。目前只有模拟支付，不经过第三方，直接视为已退回
    boolean refund(Long orderId);

}
