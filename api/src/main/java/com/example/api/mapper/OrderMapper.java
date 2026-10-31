package com.example.api.mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.OrderEntity;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    // 销售额：已支付、已发货、已完成订单的金额合计（待支付、已取消的不算）
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')")
    BigDecimal sumSalesAmount();

}
