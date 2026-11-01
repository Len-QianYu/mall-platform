package com.example.api.service.implementation;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.dto.CartItemView;
import com.example.api.entity.CartEntity;
import com.example.api.mapper.CartMapper;
import com.example.api.service.CartService;

@Service
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;

    public CartServiceImpl(CartMapper cartMapper) {
        this.cartMapper = cartMapper;
    }

    @Override
    public List<CartItemView> fetchItems(Integer userId) {
        return this.cartMapper.selectItems(userId);
    }

    @Override
    public void create(Integer userId, Integer productId, Integer quantity) {
        this.cartMapper.upsert(userId, productId, quantity);
    }

    // 以下改、删都在 WHERE 里带上 user_id：传入别人的条目 ID 也改不到、删不到

    @Override
    public Integer update(Integer userId, Long id, Integer quantity, Integer checked) {
        return this.cartMapper.update(
            Wrappers.<CartEntity>lambdaUpdate()
                .set(quantity != null, CartEntity::getQuantity, quantity)
                .set(checked != null, CartEntity::getChecked, checked)
                .eq(CartEntity::getId, id)
                .eq(CartEntity::getUserId, userId)
        );
    }

    @Override
    public void checkAll(Integer userId, Integer checked) {
        // 下架、售罄的条目也一起改：勾选只是标记，能否结算由前端规则和下单时的库存校验决定
        this.cartMapper.update(
            Wrappers.<CartEntity>lambdaUpdate()
                .set(CartEntity::getChecked, checked)
                .eq(CartEntity::getUserId, userId)
        );
    }

    @Override
    public void delete(Integer userId, List<Long> ids) {
        this.cartMapper.delete(
            Wrappers.<CartEntity>lambdaQuery()
                .eq(CartEntity::getUserId, userId)
                .in(CartEntity::getId, ids)
        );
    }

}
