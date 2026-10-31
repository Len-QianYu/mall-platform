package com.example.api.service;

import java.util.Optional;

import com.example.api.entity.UserEntity;

public interface UserService {

    // 按用户名查询（登录、注册查重用）
    Optional<UserEntity> fetchByUsername(String username);

    // 创建用户，返回受影响行数
    Integer create(UserEntity user);

}
