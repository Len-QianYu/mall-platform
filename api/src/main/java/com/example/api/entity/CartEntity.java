package com.example.api.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

// 购物车：同一用户同一商品只有一行（唯一索引 uk_user_product），再次加购累加数量
@Data
@TableName("cart")
public class CartEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer userId;

    private Integer productId;

    private Integer quantity;

    // 是否勾选：1 勾选 / 0 未勾选（结算用）
    private Integer checked;

    // created_at / updated_at 由数据库维护，这里只读不写
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

}
