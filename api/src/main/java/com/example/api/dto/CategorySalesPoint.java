package com.example.api.dto;

import java.time.LocalDate;

// SQL 按商品当前分类和支付日期汇总订单明细的售出件数。
public record CategorySalesPoint(String category, LocalDate date, long soldQuantity) {
}
