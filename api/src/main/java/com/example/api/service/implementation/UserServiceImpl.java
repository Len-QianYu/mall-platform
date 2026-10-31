package com.example.api.service.implementation;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.entity.UserEntity;
import com.example.api.mapper.UserMapper;
import com.example.api.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
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

}
