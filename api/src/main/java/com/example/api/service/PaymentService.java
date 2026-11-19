package com.example.api.service;

import java.util.List;
import java.util.Map;

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

    List<PaymentMethod> availableMethods();

    boolean isAlipaySandbox();

    // 支付宝返回收银台链接和待支付流水；模拟支付直接完成。
    PaymentStart pay(OrderEntity order, PaymentMethod method);

    // 只查询当前用户的订单；支付宝成功后同步订单、流水、销量。
    PaymentQuery query(Long orderId, Integer userId);

    boolean handleAlipayNotify(Map<String, String> params);

    // 由取消订单的事务调用，调用方已锁住订单；已收到付款时同步支付并返回 false。
    boolean closePending(OrderEntity order);

    // 按流水 ID 分页轮询，避免前面的待支付流水一直占住对账批次。
    List<PaymentEntity> fetchPendingAlipay(long afterId, int limit);

    // 定时任务按具体流水对账；同一订单的旧流水收到付款时也必须处理。
    void reconcile(Long paymentId);

    // 退款：只有已支付、未发货的订单能退。订单改为已退款、成功的流水改为已退款、回补库存、扣回销量；
    // 返回是否成功；支付宝确认原路退款后才更新本地状态，失败时抛 TradeFailedException。
    boolean refund(Long orderId);

    record PaymentStart(PaymentEntity payment, String payUrl) {}

    record PaymentQuery(OrderEntity order, PaymentEntity payment, boolean paid) {}

}
