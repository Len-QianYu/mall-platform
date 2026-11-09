package com.example.api.controller.admin;

import java.util.List;
import java.util.Optional;

import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.entity.OrderEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;
import com.example.api.validator.OrderValidateGroup;

// 管理端订单管理（/admin/** 只允许管理员访问，见 SecurityConfiguration）
@RestController
@RequestMapping("/admin/order")
public class AdminOrderController {

    private final OrderService orderService;

    private final PaymentService paymentService;

    public AdminOrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    // 订单列表：关键字匹配订单号 / 收货人 / 收货电话，可按状态筛选
    @GetMapping
    public ResultTemplate index(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) OrderStatus status
    ) {
        ResultTemplate result = new ResultTemplate();

        // 先计数再分页：页码超出总页数时 Pagination 会收回到最后一页
        Integer total = this.orderService.count(keyword, status);
        Pagination pagination = Pagination.paginate(total, pageSize, page);
        List<OrderEntity> orders = this.orderService.fetch(keyword, status, pagination);

        result.putPayload("orders", orders);
        result.putPayload("pagination", pagination);
        return result;
    }

    // 订单详情：订单主表 + 商品明细
    @GetMapping("/{id}")
    public ResultTemplate show(@PathVariable Long id) {
        ResultTemplate result = new ResultTemplate();

        Optional<OrderEntity> order = this.orderService.fetchById(id);
        if (order.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "订单不存在");
            return result;
        }

        result.putPayload("order", order.get());
        result.putPayload("items", this.orderService.fetchItems(id));
        return result;
    }

    // 发货：已支付 → 已发货
    @PostMapping("/ship")
    public ResultTemplate ship(
        @RequestBody @Validated({ OrderValidateGroup.UpdateStatus.class }) OrderEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少订单 ID");
        }

        if (!this.orderService.ship(fields.getId())) {
            result.setStatus(false);
            result.putPayload("message", "只有已支付的订单才能发货，请刷新后重试");
            return result;
        }

        result.putPayload("message", "已发货");
        return result;
    }

    // 取消：待支付 → 已取消（已支付的订单走下面的退款）
    @PostMapping("/cancel")
    public ResultTemplate cancel(
        @RequestBody @Validated({ OrderValidateGroup.UpdateStatus.class }) OrderEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少订单 ID");
        }

        if (!this.orderService.cancel(fields.getId())) {
            result.setStatus(false);
            result.putPayload("message", "只有待支付的订单才能取消，请刷新后重试");
            return result;
        }

        result.putPayload("message", "订单已取消");
        return result;
    }

    // 退款：已支付（未发货）→ 已退款，回补库存、扣回销量。已发货的要先退货，暂不支持
    @PostMapping("/refund")
    public ResultTemplate refund(
        @RequestBody @Validated({ OrderValidateGroup.UpdateStatus.class }) OrderEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少订单 ID");
        }

        if (!this.paymentService.refund(fields.getId())) {
            result.setStatus(false);
            result.putPayload("message", "只有已支付、未发货的订单才能退款，请刷新后重试");
            return result;
        }

        result.putPayload("message", "已退款");
        return result;
    }

}
