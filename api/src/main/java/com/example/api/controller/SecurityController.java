package com.example.api.controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.entity.UserEntity;
import com.example.api.exception.LoginFailedException;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.UserService;
import com.example.api.utils.JwtUtils;
import com.example.api.utils.ResultTemplate;
import com.example.api.validator.UserValidateGroup;

@RestController
public class SecurityController {

    private final UserService userService;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtils jwtUtils;

    public SecurityController(
        UserService userService,
        PasswordEncoder passwordEncoder,
        JwtUtils jwtUtils
    ) {
        this.userService        = userService;
        this.jwtUtils           = jwtUtils;
        this.passwordEncoder    = passwordEncoder;
    }

    // 使用JWT进行登录认证
    @PostMapping("/login")
    public ResultTemplate login(
        @RequestBody @Validated({ UserValidateGroup.Login.class }) UserEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("用户名或密码格式不正确");
        }

        // 验证用户名和密码
        UserEntity user = this.userService.fetchByUsername(fields.getUsername()).orElseThrow(
            () -> new LoginFailedException()
        );

        if (!this.passwordEncoder.matches(fields.getPassword(), user.getPassword())) {
            throw new LoginFailedException();
        }

        // 检查账户状态（1 启用 / 0 禁用）
        if (user.getStatus() != null && user.getStatus() == 0) {
            result.setStatus(false);
            result.putPayload("message", "账户已被封禁,无法登录");
            return result;
        }

        // 检查角色是否匹配（如果前端提供了角色）
        String requestedRole = fields.getRole(); // 前端获取
        String actualRole = user.getRole();      // 数据库获取
        if (requestedRole != null && !requestedRole.isEmpty() && !requestedRole.equals(actualRole)) {
            result.setStatus(false);
            if ("admin".equals(requestedRole)) {
                result.putPayload("message", "这是普通用户");
            } else {
                result.putPayload("message", "这是管理员");
            }
            return result;
        }

        // 生成JWT token
        String jwtToken = this.jwtUtils.generateToken(user);

        // Never expose the stored password hash in the response.
        user.setPassword(null);
        result.putPayload("user", user);
        result.putPayload("token", jwtToken);

        return result;
    }

    @PostMapping("/logout")
    public ResultTemplate logout() {
        ResultTemplate result = new ResultTemplate();

        // 退出登录的逻辑可以在这里处理
        // 比如清除服务端的session、记录日志等       
        // 使用 token 认证，前端清除本地存储的 token 即可
        result.putPayload("message", "退出登录成功");

        return result;
    }

}
