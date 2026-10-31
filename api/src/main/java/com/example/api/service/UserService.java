package com.example.api.service;

import java.util.List;
import java.util.Optional;

import com.example.api.entity.UserEntity;
import com.example.api.utils.Pagination;

public interface UserService {

    // 按用户名查询（登录、注册查重用）
    Optional<UserEntity> fetchByUsername(String username);

    // 创建用户，返回受影响行数
    Integer create(UserEntity user);

    // 按条件统计用户数（条件为 null 时不筛选）
    Integer count(String keyword, String role, Integer status);

    // 按条件查询一页用户，新注册的在前
    List<UserEntity> fetch(String keyword, String role, Integer status, Pagination pagination);

    // 管理端编辑资料和角色，返回受影响行数（0 表示用户不存在）
    Integer update(UserEntity user);

    // 启用 / 禁用，返回受影响行数（0 表示用户不存在）
    Integer updateStatus(Integer id, Integer status);

}
