package com.example.api.utils;

import org.springframework.security.oauth2.jwt.Jwt;

// 当前登录用户：用户 ID 只从 JWT 取，不信任请求体里的 userId
public final class CurrentUser {

    private CurrentUser() {
    }

    // 登录时写入的 userId 声明；JSON 数字解码回来可能是 Long，统一转成 Integer
    public static Integer id(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.intValue();
    }

}
