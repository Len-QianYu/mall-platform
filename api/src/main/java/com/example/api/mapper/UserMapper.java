package com.example.api.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.UserEntity;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
