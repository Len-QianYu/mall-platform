package com.example.api.service;

import java.util.List;
import java.util.Optional;

import com.example.api.dto.ProfileRequests;
import com.example.api.entity.UserEntity;
import com.example.api.utils.Pagination;

public interface UserService {

    Optional<UserEntity> fetchById(Integer id);

    // 按用户名查询（登录、注册查重用）
    Optional<UserEntity> fetchByUsername(String username);

    // 只查询已验证的唯一登录邮箱。
    Optional<UserEntity> fetchByEmail(String email);

    // 创建用户，返回受影响行数
    Integer create(UserEntity user);

    // 按条件统计用户数（条件为 null 时不筛选）
    Integer count(String keyword, String role, Integer status);

    // 按条件查询一页用户，新注册的在前
    List<UserEntity> fetch(String keyword, String role, Integer status, Pagination pagination);

    // 管理端编辑资料和角色：超级管理员不可降级，提升为超级管理员时自动启用
    // 返回受影响行数（0 表示用户不存在或角色受保护）
    Integer update(UserEntity user);

    // 启用 / 禁用，超级管理员不可禁用（0 表示用户不存在或角色受保护）
    Integer updateStatus(Integer id, Integer status);

    // 用户改自己的资料，返回受影响行数（0 表示用户不存在）
    Integer updateProfile(Integer id, ProfileRequests.Update profile);

    // 上传后单独保存头像，返回受影响行数（0 表示用户不存在）
    Integer updateAvatar(Integer id, String avatarUrl);

    // 改密码：传入明文，在此加密，返回受影响行数（0 表示用户不存在）
    Integer updatePassword(Integer id, String password);

    Integer bindEmail(Integer id, String email, long expectedVersion);

    Integer resetPassword(Integer id, String email, long expectedVersion, String password);

}
