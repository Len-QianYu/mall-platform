package com.example.api.controller;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.enums.PaymentMethod;
import com.example.api.exception.TradeFailedException;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.ResultTemplate;

// 商城前台支付（需登录）：用户 ID 取自 JWT，只能支付自己的订单
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    private final OrderService orderService;

    public PaymentController(PaymentService paymentService, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderService   = orderService;
    }

    @PostMapping("/pay")
    public ResultTemplate pay(
        @RequestBody @Validated TradeRequests.Pay fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少订单 ID 或支付方式");
        }

        // 支付宝、微信还没接入，只接受模拟支付：否则会记下一条没真正收款的成功流水
        if (fields.method() != PaymentMethod.MOCK) {
            result.setStatus(false);
            result.putPayload("message", "暂不支持该支付方式");
            return result;
        }

        Optional<OrderEntity> order = this.orderService.fetchByIdForUser(fields.orderId(), CurrentUser.id(jwt));
        if (order.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "订单不存在");
            return result;
        }

        try {
            result.putPayload("payment", this.paymentService.pay(order.get(), fields.method()));
        } catch (TradeFailedException exception) {
            result.setStatus(false);
            result.putPayload("message", exception.getMessage());
            return result;
        }

        result.putPayload("message", "支付成功");
        return result;
    }

}
