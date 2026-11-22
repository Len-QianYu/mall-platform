package com.example.api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.api.enums.ProductNotificationType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
@TableName("product_notification")
public class ProductNotificationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @JsonIgnore
    private Integer userId;
    @JsonIgnore
    private Long eventId;
    private Integer productId;
    private ProductNotificationType type;
    private String productName;
    private String productImage;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
