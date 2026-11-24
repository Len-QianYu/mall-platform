package com.example.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class ProductNotificationRequests {
    private ProductNotificationRequests() { }

    public record Favorite(@NotNull @Min(1) Integer productId, @NotNull Boolean favorite) { }
    public record Read(@NotNull @Min(1) Long id,
        @Pattern(regexp = "LISTED|PRICE_DROP|PRICE_RISE|ORDER_SHIPPED") String type) { }
}
