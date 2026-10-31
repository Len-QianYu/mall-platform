package com.example.api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

// 订单明细：商品名、主图、单价是下单时的快照，不随商品修改变化
@Data
@TableName("order_item")
public class OrderItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Integer productId;

    private String productName;

    private String productImage;

    // 成交单价（元）
    private BigDecimal unitPrice;

    private Integer quantity;

    // created_at 由数据库维护，这里只读不写
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

}
