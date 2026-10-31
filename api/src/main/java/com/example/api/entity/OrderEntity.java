package com.example.api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.api.enums.OrderStatus;
import com.example.api.validator.OrderValidateGroup;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 订单主表（表名 orders：ORDER 是 SQL 关键字）
@Data
@TableName("orders")
public class OrderEntity {

    @TableId(type = IdType.AUTO)
    @NotNull(groups = { OrderValidateGroup.UpdateStatus.class })
    private Long id;

    // 订单号（对外展示）
    private String orderNo;

    private Integer userId;

    // 订单总额（元）
    private BigDecimal totalAmount;

    private OrderStatus status;

    // 收货信息：下单时的快照，用户后来改地址不影响历史订单
    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    // 买家备注
    private String remark;

    private LocalDateTime paidAt;

    private LocalDateTime shippedAt;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    // created_at / updated_at 由数据库维护，这里只读不写
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

}
