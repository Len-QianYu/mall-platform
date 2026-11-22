package com.example.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SeckillRequests {
    private SeckillRequests() {}

    public record Create(
        @NotNull @Min(1) Integer productId,
        @NotBlank @Size(max = 128) String title,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal seckillPrice,
        @NotNull @Min(1) @Max(1000000) Integer totalStock,
        @NotNull @Min(1) @Max(999) Integer limitPerUser,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt
    ) {}

    public record Close(@NotNull @Min(1) Long id) {}
}
