package com.example.api.service.implementation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.dto.ProfileRequests;
import com.example.api.entity.UserEntity;
import com.example.api.mapper.UserMapper;
import com.example.api.service.UserService;
import com.example.api.utils.Pagination;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<UserEntity> fetchById(Integer id) {
        return Optional.ofNullable(this.userMapper.selectById(id));
    }

    @Override
    public Optional<UserEntity> fetchByUsername(String username) {
        // MyBatis-Plus 用 getter 引用解析列名，SFunction 的接收者缺少空值注解。
        @SuppressWarnings("null")
        UserEntity user = this.userMapper.selectOne(
            Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username)
        );
        return Optional.ofNullable(user);
    }

    @Override
    public Integer create(UserEntity user) {
        // 密码统一在此加密，数据库只存 BCrypt 哈希
        user.setPassword(this.passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("user");
        }
        user.setStatus(1);
        user.setRegisteredAt(LocalDateTime.now());
        return this.userMapper.insert(user);
    }

    @Override
    public Integer count(String keyword, String role, Integer status) {
        return this.userMapper.selectCount(this.filter(keyword, role, status)).intValue();
    }

    @Override
    public List<UserEntity> fetch(String keyword, String role, Integer status, Pagination pagination) {
        // 总数已由 count 查过，分页插件只负责拼 LIMIT（searchCount = false，不重复计数）
        return this.userMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(keyword, role, status).orderByDesc(UserEntity::getId)
        );
    }

    @Override
    public Integer update(UserEntity user) {
        // 逐列 set 而不用 updateById：updateById 会跳过 null 字段，选填项清空后存不回 NULL；
        // 只列出管理端能改的列，请求体夹带的用户名、密码、状态不会被写入
        return this.userMapper.update(
            Wrappers.<UserEntity>lambdaUpdate()
                .set(UserEntity::getName, user.getName())
                .set(UserEntity::getGender, user.getGender())
                .set(UserEntity::getPhone, user.getPhone())
                .set(UserEntity::getEmail, user.getEmail())
                .set(UserEntity::getRole, user.getRole())
                .eq(UserEntity::getId, user.getId())
        );
    }

    @Override
    public Integer updateStatus(Integer id, Integer status) {
        return this.userMapper.update(
            Wrappers.<UserEntity>lambdaUpdate()
                .set(UserEntity::getStatus, status)
                .eq(UserEntity::getId, id)
        );
    }

    @Override
    public Integer updateProfile(Integer id, ProfileRequests.Update profile) {
        // 和管理端编辑一样逐列 set：选填项清空后要能存回 NULL
        return this.userMapper.update(
            Wrappers.<UserEntity>lambdaUpdate()
                .set(UserEntity::getName, profile.name())
                .set(UserEntity::getGender, profile.gender())
                .set(UserEntity::getEmail, profile.email())
                .set(UserEntity::getPhone, profile.phone())
                .set(UserEntity::getAvatarUrl, profile.avatarUrl())
                .eq(UserEntity::getId, id)
        );
    }

    @Override
    public Integer updatePassword(Integer id, String password) {
        // 和注册一样，数据库只存 BCrypt 哈希
        return this.userMapper.update(
            Wrappers.<UserEntity>lambdaUpdate()
                .set(UserEntity::getPassword, this.passwordEncoder.encode(password))
                .eq(UserEntity::getId, id)
        );
    }

    // 列表筛选条件：count 和 fetch 共用，保证总数和当前页按同一条件查询
    private LambdaQueryWrapper<UserEntity> filter(String keyword, String role, Integer status) {
        return Wrappers.<UserEntity>lambdaQuery()
            .and(StringUtils.hasText(keyword), wrapper -> wrapper
                .like(UserEntity::getUsername, keyword)
                .or().like(UserEntity::getName, keyword)
                .or().like(UserEntity::getPhone, keyword)
            )
            .eq(StringUtils.hasText(role), UserEntity::getRole, role)
            .eq(status != null, UserEntity::getStatus, status);
    }

}
