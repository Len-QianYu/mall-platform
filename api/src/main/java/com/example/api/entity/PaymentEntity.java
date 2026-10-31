package com.example.api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;

import lombok.Data;

// 支付流水：一单可有多条（支付失败后重试）
@Data
@TableName("payment")
public class PaymentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 支付流水号（对外展示）
    private String paymentNo;

    private Long orderId;

    private Integer userId;

    // 支付金额（元）
    private BigDecimal amount;

    private PaymentMethod method;

    private PaymentStatus status;

    // 支付成功时间
    private LocalDateTime paidAt;

    // created_at / updated_at 由数据库维护，这里只读不写
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

}
