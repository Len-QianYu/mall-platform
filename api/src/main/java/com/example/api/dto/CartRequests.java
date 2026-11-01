package com.example.api.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

// 购物车接口的请求体：用户 ID 取自 JWT，请求体里不接收
public final class CartRequests {

    private CartRequests() {
    }

    // 加购：已在购物车则累加数量
    public record Create(
        @NotNull Integer productId,
        @NotNull @Min(1) @Max(999) Integer quantity
    ) {}

    // 改数量或勾选状态，两者至少给一个
    public record Update(
        @NotNull Long id,
        @Min(1) @Max(999) Integer quantity,
        @Min(0) @Max(1) Integer checked
    ) {}

    public record CheckAll(
        @NotNull @Min(0) @Max(1) Integer checked
    ) {}

    public record Delete(
        @NotEmpty List<@NotNull Long> ids
    ) {}

}
