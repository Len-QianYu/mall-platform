package com.example.api.dto;

import java.math.BigDecimal;

import com.example.api.enums.OrderStatus;

public record OrderStatusSalesAmount(OrderStatus status, BigDecimal amount) {
}
