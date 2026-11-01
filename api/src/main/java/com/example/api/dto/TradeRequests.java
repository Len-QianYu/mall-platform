package com.example.api.dto;

import java.util.List;

import com.example.api.enums.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 下单、支付的请求体：用户 ID 取自 JWT，请求体里不接收
public final class TradeRequests {

    private TradeRequests() {
    }

    // 下单：只收商品 ID 和数量，价格以服务端查库为准
    public record OrderCreate(
        @NotEmpty @Size(max = 50) List<@NotNull @Valid OrderCreateItem> items,
        // true 表示从购物车结算，下单成功后从购物车移除这些商品；不传按 false 处理
        // （用包装类型：Jackson 3 默认不允许基本类型缺省，缺了这个字段会直接 400）
        Boolean fromCart,
        @NotBlank @Size(max = 32) String receiverName,
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String receiverPhone,
        @NotBlank @Size(max = 255) String receiverAddress,
        @Size(max = 255) String remark
    ) {}

    public record OrderCreateItem(
        @NotNull Integer productId,
        @NotNull @Min(1) @Max(999) Integer quantity
    ) {}

    public record Pay(
        @NotNull Long orderId,
        @NotNull PaymentMethod method
    ) {}

}
