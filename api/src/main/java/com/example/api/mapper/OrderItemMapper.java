package com.example.api.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.OrderItemEntity;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemEntity> {
}
