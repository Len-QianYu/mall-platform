package com.example.api.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.PaymentEntity;

@Mapper
public interface PaymentMapper extends BaseMapper<PaymentEntity> {
}
