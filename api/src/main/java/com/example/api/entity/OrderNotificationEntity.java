package com.example.api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
@TableName("order_notification")
public class OrderNotificationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @JsonIgnore
    private Integer userId;
    @TableField(exist = false)
    private final String type = "NEW_ORDER";
    private Long orderId;
    private String orderNo;
    private Integer buyerId;
    private String buyerUsername;
    private String buyerName;
    private BigDecimal totalAmount;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
