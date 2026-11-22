package com.example.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class ProductRequests {

    private ProductRequests() {}

    // 调整的是当前库存的增量，不能用管理员打开表单时的旧值覆盖库存。
    public record StockAdjust(@NotNull @Min(1) Integer id, @NotNull Integer delta) {}
}
