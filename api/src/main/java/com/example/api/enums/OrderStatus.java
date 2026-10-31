package com.example.api.enums;

// 订单状态：orders.status 存枚举名（VARCHAR），MyBatis 默认按名称读写
public enum OrderStatus {
    PENDING_PAYMENT, // 待支付
    PAID,            // 已支付，等待发货
    SHIPPED,         // 已发货
    COMPLETED,       // 已完成
    CANCELLED,       // 已取消
}
