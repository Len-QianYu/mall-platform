package com.example.api.controller;

import java.util.Optional;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.enums.PaymentStatus;
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

    @GetMapping("/methods")
    public ResultTemplate methods() {
        return new ResultTemplate()
            .putPayload("methods", this.paymentService.availableMethods())
            .putPayload("alipaySandbox", this.paymentService.isAlipaySandbox());
    }

    @GetMapping("/query")
    public ResultTemplate query(@RequestParam Long orderId, @AuthenticationPrincipal Jwt jwt) {
        PaymentService.PaymentQuery payment = this.paymentService.query(orderId, CurrentUser.id(jwt));
        return new ResultTemplate()
            .putPayload("order", payment.order())
            .putPayload("payment", payment.payment())
            .putPayload("paid", payment.paid());
    }

    // 支付宝不带 JWT；仅此 POST 接口放行，成功必须返回纯文本 success。
    @PostMapping(value = "/alipay/notify", produces = "text/plain;charset=UTF-8")
    public String notify(@RequestParam Map<String, String> params) {
        try {
            return this.paymentService.handleAlipayNotify(params) ? "success" : "failure";
        } catch (RuntimeException error) {
            // 事务回滚后返回 failure，支付宝会重试；不输出含签名的完整请求。
            return "failure";
        }
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

        Optional<OrderEntity> order = this.orderService.fetchByIdForUser(fields.orderId(), CurrentUser.id(jwt));
        if (order.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "订单不存在");
            return result;
        }

        try {
            PaymentService.PaymentStart payment = this.paymentService.pay(order.get(), fields.method());
            result.putPayload("payment", payment.payment());
            result.putPayload("payUrl", payment.payUrl());
            result.putPayload("message", payment.payment().getStatus() == PaymentStatus.SUCCESS
                ? "支付成功" : "请前往支付宝完成支付");
        } catch (TradeFailedException exception) {
            result.setStatus(false);
            result.putPayload("message", exception.getMessage());
            return result;
        }

        return result;
    }

}
