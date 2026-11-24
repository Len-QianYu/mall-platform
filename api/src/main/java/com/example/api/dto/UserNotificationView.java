package com.example.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

// 商城通知中心合并商品和发货通知；不暴露接收人及内部事件 ID。
@Data
public class UserNotificationView {
    private Long id;
    private String type;
    private Integer productId;
    private String productName;
    private String productImage;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private LocalDateTime shippedAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
