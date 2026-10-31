package com.example.api.exception;

// 登录失败（用户名不存在或密码不匹配）
public class LoginFailedException extends RuntimeException {

    public LoginFailedException() {
        super("用户名或密码错误");
    }

    public LoginFailedException(String message) {
        super(message);
    }

}
