package com.example.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class OrderNotificationRequests {
    private OrderNotificationRequests() { }

    public record Read(@NotNull @Min(1) Long id) { }
}
