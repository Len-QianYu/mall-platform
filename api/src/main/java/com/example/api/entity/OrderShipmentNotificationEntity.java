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
@TableName("order_shipment_notification")
public class OrderShipmentNotificationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @JsonIgnore
    private Integer userId;
    @TableField(exist = false)
    private final String type = "ORDER_SHIPPED";
    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private LocalDateTime shippedAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
