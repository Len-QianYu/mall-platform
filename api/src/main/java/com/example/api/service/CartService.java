package com.example.api.service;

import java.util.List;

import com.example.api.dto.CartItemView;

// 购物车：所有方法都按 userId 限定，只读写该用户自己的条目
public interface CartService {

    // 购物车条目，带上商品当前信息，后加的在前
    List<CartItemView> fetchItems(Integer userId);

    // 加购：已在购物车则累加数量（上限 999），并重新勾选
    void create(Integer userId, Integer productId, Integer quantity);

    // 改数量和 / 或勾选状态，为 null 的不改（调用方保证至少给一个）；返回受影响行数（0 表示条目不存在或不是该用户的）
    Integer update(Integer userId, Long id, Integer quantity, Integer checked);

    // 全选 / 全不选
    void checkAll(Integer userId, Integer checked);

    // 批量删除：不属于该用户的 ID 直接忽略
    void delete(Integer userId, List<Long> ids);

}
