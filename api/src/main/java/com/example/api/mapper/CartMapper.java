package com.example.api.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.dto.CartItemView;
import com.example.api.entity.CartEntity;

@Mapper
public interface CartMapper extends BaseMapper<CartEntity> {

    // 购物车条目带上商品当前信息，后加的在前；商品被物理删除的条目随 JOIN 一起过滤掉
    @Select("""
        SELECT c.id, c.product_id, c.quantity, c.checked,
               p.name AS product_name, p.main_image AS product_image,
               p.price, p.stock, p.status AS product_status
        FROM cart c
        JOIN product p ON p.id = c.product_id
        WHERE c.user_id = #{userId}
        ORDER BY c.id DESC
        """)
    List<CartItemView> selectItems(@Param("userId") Integer userId);

    // 加购：借唯一索引 uk_user_product 在一条 SQL 里完成“没有就插入、有就累加”，并发加购不会插出重复行
    @Insert("""
        INSERT INTO cart (user_id, product_id, quantity, checked)
        VALUES (#{userId}, #{productId}, #{quantity}, 1)
        ON DUPLICATE KEY UPDATE quantity = LEAST(quantity + VALUES(quantity), 999), checked = 1
        """)
    int upsert(@Param("userId") Integer userId, @Param("productId") Integer productId, @Param("quantity") Integer quantity);

}
