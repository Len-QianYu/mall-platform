package com.example.api.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.dto.ProductFavoriteView;
import com.example.api.entity.ProductFavoriteEntity;

@Mapper
public interface ProductFavoriteMapper extends BaseMapper<ProductFavoriteEntity> {
    @Select("SELECT product_id FROM product_favorite WHERE user_id = #{userId} ORDER BY id DESC")
    List<Integer> selectProductIds(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM product_favorite f JOIN product p ON p.id = f.product_id WHERE f.user_id = #{userId}")
    int countItems(@Param("userId") Integer userId);

    @Select("""
        SELECT f.product_id, p.name AS product_name, p.main_image AS product_image,
               p.category, p.price, p.stock, p.status AS product_status, f.created_at AS favorited_at
        FROM product_favorite f JOIN product p ON p.id = f.product_id
        WHERE f.user_id = #{userId} ORDER BY f.id DESC LIMIT #{limit} OFFSET #{offset}
        """)
    List<ProductFavoriteView> selectItems(@Param("userId") Integer userId,
        @Param("limit") int limit, @Param("offset") int offset);

    // 重复收藏保留原订阅时间；取消后再收藏则产生新的时间，不补发此前的变动。
    @Insert("""
        INSERT INTO product_favorite (user_id, product_id) VALUES (#{userId}, #{productId})
        ON DUPLICATE KEY UPDATE product_id = VALUES(product_id)
        """)
    int add(@Param("userId") Integer userId, @Param("productId") Integer productId);
}
