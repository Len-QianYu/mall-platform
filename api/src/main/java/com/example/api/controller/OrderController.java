package com.example.api.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.OrderWithItemsView;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.exception.TradeFailedException;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;
import com.example.api.validator.OrderValidateGroup;

// 商城前台订单（需登录）：用户 ID 取自 JWT，只能查看和操作自己的订单
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    // 我的订单：可按状态筛选，每个订单带上商品明细
    @GetMapping
    public ResultTemplate index(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) OrderStatus status,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();
        Integer userId = CurrentUser.id(jwt);

        // 先计数再分页：页码超出总页数时 Pagination 会收回到最后一页
        Integer total = this.orderService.countForUser(userId, status);
        Pagination pagination = Pagination.paginate(total, pageSize, page);
        List<OrderWithItemsView> orders = this.orderService.fetchForUser(userId, status, pagination);

        result.putPayload("orders", orders);
        result.putPayload("pagination", pagination);
        return result;
    }

    // 订单详情：订单主表 + 商品明细
    @GetMapping("/{id}")
    public ResultTemplate show(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        ResultTemplate result = new ResultTemplate();

        Optional<OrderEntity> order = this.orderService.fetchByIdForUser(id, CurrentUser.id(jwt));
        if (order.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "订单不存在");
            return result;
        }

        result.putPayload("order", order.get());
        result.putPayload("items", this.orderService.fetchItems(id));
        return result;
    }

    // 下单：请求体只有商品 ID 和数量，价格以服务端查库为准
    @PostMapping("/create")
    public ResultTemplate create(
        @RequestBody @Validated TradeRequests.OrderCreate fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("订单信息不符合要求");
        }

        try {
            result.putPayload("order", this.orderService.create(CurrentUser.id(jwt), fields));
        } catch (TradeFailedException exception) {
            // 商品下架、库存不足：已扣的库存已随事务回滚，把原因返回给前端
            result.setStatus(false);
            result.putPayload("message", exception.getMessage());
            return result;
        }

        result.putPayload("message", "下单成功");
        return result;
    }

    // 取消：待支付 → 已取消，回补库存
    @PostMapping("/cancel")
    public ResultTemplate cancel(
        @RequestBody @Validated({ OrderValidateGroup.UpdateStatus.class }) OrderEntity fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少订单 ID");
        }

        if (!this.isOwnOrder(fields.getId(), jwt)) {
            result.setStatus(false);
            result.putPayload("message", "订单不存在");
            return result;
        }

        if (!this.orderService.cancel(fields.getId())) {
            result.setStatus(false);
            result.putPayload("message", "只有待支付的订单才能取消，请刷新后重试");
            return result;
        }

        result.putPayload("message", "订单已取消");
        return result;
    }

    // 确认收货：已发货 → 已完成
    @PostMapping("/confirm")
    public ResultTemplate confirm(
        @RequestBody @Validated({ OrderValidateGroup.UpdateStatus.class }) OrderEntity fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少订单 ID");
        }

        if (!this.isOwnOrder(fields.getId(), jwt)) {
            result.setStatus(false);
            result.putPayload("message", "订单不存在");
            return result;
        }

        if (!this.orderService.confirm(fields.getId())) {
            result.setStatus(false);
            result.putPayload("message", "只有已发货的订单才能确认收货，请刷新后重试");
            return result;
        }

        result.putPayload("message", "已确认收货");
        return result;
    }

    // 退款：已支付（未发货）→ 已退款，回补库存、扣回销量
    @PostMapping("/refund")
    public ResultTemplate refund(
        @RequestBody @Validated({ OrderValidateGroup.UpdateStatus.class }) OrderEntity fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少订单 ID");
        }

        if (!this.isOwnOrder(fields.getId(), jwt)) {
            result.setStatus(false);
            result.putPayload("message", "订单不存在");
            return result;
        }

        if (!this.paymentService.refund(fields.getId())) {
            result.setStatus(false);
            result.putPayload("message", "只有已支付、未发货的订单才能退款，请刷新后重试");
            return result;
        }

        result.putPayload("message", "退款成功，款项已原路退回");
        return result;
    }

    // 改状态前先确认是自己的订单：订单的下单人不会变，确认之后再按 ID 条件更新，两步之间没有竞态
    private boolean isOwnOrder(Long id, Jwt jwt) {
        return this.orderService.fetchByIdForUser(id, CurrentUser.id(jwt)).isPresent();
    }

}
