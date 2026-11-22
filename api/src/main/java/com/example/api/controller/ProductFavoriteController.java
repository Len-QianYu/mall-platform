package com.example.api.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.ProductNotificationRequests;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.ProductFavoriteService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;

@RestController
@RequestMapping("/favorite")
public class ProductFavoriteController {
    private final ProductFavoriteService favorites;

    public ProductFavoriteController(ProductFavoriteService favorites) { this.favorites = favorites; }

    @GetMapping("/ids")
    public ResultTemplate ids(@AuthenticationPrincipal Jwt jwt) {
        return new ResultTemplate().putPayload("productIds", this.favorites.fetchIds(CurrentUser.id(jwt)));
    }

    @GetMapping
    public ResultTemplate index(@RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "12") Integer pageSize, @AuthenticationPrincipal Jwt jwt) {
        Integer userId = CurrentUser.id(jwt);
        Pagination pagination = Pagination.paginate(this.favorites.count(userId), Math.min(pageSize, 100), page);
        ResultTemplate result = new ResultTemplate();
        result.putPayload("favorites", this.favorites.fetch(userId, pagination));
        return result.putPayload("pagination", pagination);
    }

    @PostMapping
    public ResultTemplate update(@RequestBody @Validated ProductNotificationRequests.Favorite fields,
        BindingResult bindingResult, @AuthenticationPrincipal Jwt jwt) {
        if (bindingResult.hasErrors()) throw new ValidateFailedException("商品 ID 或收藏状态不正确");
        this.favorites.setFavorite(CurrentUser.id(jwt), fields.productId(), fields.favorite());
        ResultTemplate result = new ResultTemplate();
        result.putPayload("favorite", fields.favorite());
        return result.putPayload("message", fields.favorite() ? "已收藏，将提醒上架和价格变动" : "已取消收藏");
    }
}
