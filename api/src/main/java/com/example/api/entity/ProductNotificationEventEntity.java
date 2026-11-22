package com.example.api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.api.enums.ProductNotificationType;

import lombok.Data;

@Data
@TableName("product_notification_event")
public class ProductNotificationEventEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer productId;
    private ProductNotificationType type;
    private String productName;
    private String productImage;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private LocalDateTime consumedAt;
}
