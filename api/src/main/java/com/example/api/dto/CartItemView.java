package com.example.api.dto;

import java.math.BigDecimal;

import lombok.Data;

// 购物车条目（cart 关联 product）：商品名、主图、价格、库存、上下架都取商品当前值，不是快照
@Data
public class CartItemView {

    private Long id;

    private Integer productId;

    private Integer quantity;

    private Integer checked;

    private String productName;

    private String productImage;

    private BigDecimal price;

    private Integer stock;

    // 1 上架 / 0 下架：下架后仍留在购物车里，但不能结算
    private Integer productStatus;

}
