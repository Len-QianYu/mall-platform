package com.example.api.dto;

import java.time.LocalDate;
import java.util.List;

// category 为 null 时表示商品缺失或没有分类；每日数据包含区间内的零销量日期。
public record CategorySalesView(String category, long soldQuantity, List<DailySales> trend) {
    public record DailySales(LocalDate date, long soldQuantity) {
    }
}
