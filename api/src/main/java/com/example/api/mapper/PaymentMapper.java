package com.example.api.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.PaymentEntity;

@Mapper
public interface PaymentMapper extends BaseMapper<PaymentEntity> {

    @Select("SELECT * FROM payment WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1 FOR UPDATE")
    PaymentEntity selectLatestForUpdate(@Param("orderId") Long orderId);

    @Select("SELECT * FROM payment WHERE payment_no = #{paymentNo} FOR UPDATE")
    PaymentEntity selectByNoForUpdate(@Param("paymentNo") String paymentNo);

    @Select("SELECT * FROM payment WHERE order_id = #{orderId} AND status = 'SUCCESS' ORDER BY id DESC LIMIT 1 FOR UPDATE")
    PaymentEntity selectSuccessForUpdate(@Param("orderId") Long orderId);
}
