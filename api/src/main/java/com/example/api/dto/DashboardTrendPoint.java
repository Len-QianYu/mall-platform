package com.example.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// 每日新订单数按下单时间统计，销售额按支付时间统计，二者可能出现在不同日期。
public record DashboardTrendPoint(LocalDate date, long orderCount, BigDecimal salesAmount) {
}
