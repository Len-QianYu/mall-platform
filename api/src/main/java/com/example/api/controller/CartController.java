package com.example.api.controller;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.CartRequests;
import com.example.api.entity.ProductEntity;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.CartService;
import com.example.api.service.ProductService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.ResultTemplate;

// 商城前台购物车（需登录）：用户 ID 取自 JWT，只能操作自己的购物车
@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    private final ProductService productService;

    public CartController(CartService cartService, ProductService productService) {
        this.cartService    = cartService;
        this.productService = productService;
    }

    @GetMapping
    public ResultTemplate index(@AuthenticationPrincipal Jwt jwt) {
        ResultTemplate result = new ResultTemplate();

        result.putPayload("items", this.cartService.fetchItems(CurrentUser.id(jwt)));
        return result;
    }

    // 加购：已在购物车则累加数量
    @PostMapping("/create")
    public ResultTemplate create(
        @RequestBody @Validated CartRequests.Create fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("商品或数量不正确");
        }

        Optional<ProductEntity> product = this.productService.fetchById(fields.productId())
            .filter(found -> found.getStatus() == 1);
        if (product.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "商品不存在或已下架");
            return result;
        }

        // 只按本次加购的数量校验库存（和详情页的数量上限一致）；
        // 累加后超出库存的，购物车页会提示库存不足，下单时再按实时库存扣减
        if (product.get().getStock() < fields.quantity()) {
            result.setStatus(false);
            result.putPayload("message", "库存不足");
            return result;
        }

        this.cartService.create(CurrentUser.id(jwt), fields.productId(), fields.quantity());

        result.putPayload("message", "已加入购物车");
        return result;
    }

    // 改数量或勾选状态
    @PostMapping("/update")
    public ResultTemplate update(
        @RequestBody @Validated CartRequests.Update fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors() || (fields.quantity() == null && fields.checked() == null)) {
            throw new ValidateFailedException("数量或勾选状态不正确");
        }

        if (this.cartService.update(CurrentUser.id(jwt), fields.id(), fields.quantity(), fields.checked()) == 0) {
            result.setStatus(false);
            result.putPayload("message", "购物车里没有这件商品，请刷新后重试");
            return result;
        }

        result.putPayload("message", "已更新");
        return result;
    }

    // 全选 / 全不选
    @PostMapping("/check-all")
    public ResultTemplate checkAll(
        @RequestBody @Validated CartRequests.CheckAll fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("勾选状态不正确");
        }

        this.cartService.checkAll(CurrentUser.id(jwt), fields.checked());

        result.putPayload("message", "已更新");
        return result;
    }

    // 删除：要删的条目已经不在了（如重复点击）也算成功，前端随后会重新拉取购物车
    @PostMapping("/delete")
    public ResultTemplate delete(
        @RequestBody @Validated CartRequests.Delete fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("缺少要删除的条目");
        }

        this.cartService.delete(CurrentUser.id(jwt), fields.ids());

        result.putPayload("message", "已删除");
        return result;
    }

}
