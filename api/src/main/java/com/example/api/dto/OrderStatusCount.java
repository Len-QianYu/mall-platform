package com.example.api.dto;

import com.example.api.enums.OrderStatus;

public record OrderStatusCount(OrderStatus status, long count) {
}
