package com.example.api.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.ProductEntity;

@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {

    // 已用过的全部分类（去重）
    @Select("SELECT DISTINCT category FROM product ORDER BY category")
    List<String> selectCategories();

    // 有上架商品的分类（前台筛选用，避免点进去是空列表）
    @Select("SELECT DISTINCT category FROM product WHERE status = 1 ORDER BY category")
    List<String> selectOnSaleCategories();

    // 下单扣库存：条件里带上“在售且库存够”，判断和扣减在同一条 SQL 里完成，并发下单不会超卖
    @Update("UPDATE product SET stock = stock - #{quantity} WHERE id = #{id} AND status = 1 AND stock >= #{quantity}")
    int deductStock(@Param("id") Integer id, @Param("quantity") Integer quantity);

    // 取消订单回补库存
    @Update("UPDATE product SET stock = stock + #{quantity} WHERE id = #{id}")
    int restoreStock(@Param("id") Integer id, @Param("quantity") Integer quantity);

    // 支付成功后累加销量：只算付了款的
    @Update("UPDATE product SET sales = sales + #{quantity} WHERE id = #{id}")
    int addSales(@Param("id") Integer id, @Param("quantity") Integer quantity);

    // 退款扣回销量。sales 是无符号列，减成负数会直接报错，所以最多减到 0
    @Update("UPDATE product SET sales = sales - LEAST(sales, #{quantity}) WHERE id = #{id}")
    int subtractSales(@Param("id") Integer id, @Param("quantity") Integer quantity);

}
