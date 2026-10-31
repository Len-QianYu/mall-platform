package com.example.api.enums;

// 支付流水状态：payment.status 存枚举名（VARCHAR）
public enum PaymentStatus {
    PENDING,  // 待支付
    SUCCESS,  // 成功
    FAILED,   // 失败
    REFUNDED, // 已退款
}
