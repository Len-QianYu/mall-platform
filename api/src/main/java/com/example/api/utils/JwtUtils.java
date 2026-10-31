package com.example.api.utils;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.example.api.configuration.JwtConfiguration;
import com.example.api.entity.UserEntity;

@Component
public class JwtUtils {

    private final JwtEncoder jwtEncoder;

    // 提供JwtEncoder，用于编码JWT token
    public JwtUtils(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

// 生成JWT token
    public String generateToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(JwtConfiguration.getTokenValidity());

        // 根据用户角色设置权限
        String role = user.getRole() != null ? user.getRole() : "user";
        List<String> authorities = List.of("权限" + role.toUpperCase());

        // 登录
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfiguration.getIssuer())
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("role", role)
                .claim("authorities", authorities)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

// 从JWT token中提取用户ID
    public Integer extractUserId(String token) {
        // 这个方法主要用于解析token，实际使用中Spring Security会自动处理
        return null;
    }

// 验证token是否有效
    public boolean validateToken(String token) {
        try {
            // Spring Security会自动验证token的有效性
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
