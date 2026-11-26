package com.example.api.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.dto.CategorySalesPoint;
import com.example.api.entity.OrderItemEntity;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {

    // 按支付时间统计实际件数，不使用商品累计销量或订单笔数；下架商品仍计入。
    // 不关联支付流水，避免同一订单的多条流水重复累计；缺失商品归入未分类。
    @Select("""
        SELECT NULLIF(TRIM(p.category), '') AS category, DATE(o.paid_at) AS date,
               SUM(i.quantity) AS soldQuantity
        FROM orders o
        JOIN order_item i ON i.order_id = o.id
        LEFT JOIN product p ON p.id = i.product_id
        WHERE o.paid_at >= #{start} AND o.paid_at < #{end}
          AND o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
        GROUP BY NULLIF(TRIM(p.category), ''), DATE(o.paid_at)
        ORDER BY category, date
        """)
    List<CategorySalesPoint> fetchDailyCategorySales(
        @Param("start") LocalDateTime start, @Param("end") LocalDateTime end
    );
}
