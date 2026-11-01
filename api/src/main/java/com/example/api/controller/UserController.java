package com.example.api.controller;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.ProfileRequests;
import com.example.api.entity.UserEntity;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.UserService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.ResultTemplate;
import com.example.api.validator.UserValidateGroup;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    private final PasswordEncoder passwordEncoder;

    public UserController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService     = userService;
        this.passwordEncoder = passwordEncoder;
    }

    // 匿名注册：默认角色为 user
    @PostMapping("/create")
    public ResultTemplate create(
        @RequestBody @Validated({ UserValidateGroup.Create.class }) UserEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("用户名或密码不符合要求");
        }

        // 用户名查重
        if (this.userService.fetchByUsername(fields.getUsername()).isPresent()) {
            result.setStatus(false);
            result.putPayload("message", "用户名已存在");
            return result;
        }

        // 只取用户名和密码入库：请求体绑定的是整个实体，直接保存的话客户端可以夹带 id、status 等字段
        UserEntity user = new UserEntity();
        user.setUsername(fields.getUsername());
        user.setPassword(fields.getPassword());
        user.setRole("user");

        try {
            this.userService.create(user);
        } catch (DuplicateKeyException exception) {
            // 并发注册同名时两个请求都可能通过上面的查重，由唯一索引 uk_username 兜底
            result.setStatus(false);
            result.putPayload("message", "用户名已存在");
            return result;
        }

        result.putPayload("message", "注册成功");
        return result;
    }

    // 以下为个人中心（需登录）：用户 ID 取自 JWT，只能查看和修改自己的资料

    @GetMapping("/profile")
    public ResultTemplate profile(@AuthenticationPrincipal Jwt jwt) {
        ResultTemplate result = new ResultTemplate();

        Optional<UserEntity> user = this.userService.fetchById(CurrentUser.id(jwt));
        if (user.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "用户不存在");
            return result;
        }

        // 密码字段是 WRITE_ONLY，不会出现在响应里
        result.putPayload("user", user.get());
        return result;
    }

    // 改资料（用户名、角色、状态不在这里改）
    @PostMapping("/update")
    public ResultTemplate update(
        @RequestBody @Validated ProfileRequests.Update fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("资料不符合要求");
        }

        if (this.userService.updateProfile(CurrentUser.id(jwt), fields) == 0) {
            result.setStatus(false);
            result.putPayload("message", "用户不存在");
            return result;
        }

        result.putPayload("message", "保存成功");
        return result;
    }

    // 改密码：先核对当前密码。已签发的 JWT 在过期前仍然有效，前端改密成功后会主动退出登录
    @PostMapping("/password")
    public ResultTemplate password(
        @RequestBody @Validated ProfileRequests.Password fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("新密码需 6–32 个字符");
        }

        Optional<UserEntity> user = this.userService.fetchById(CurrentUser.id(jwt));
        if (user.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "用户不存在");
            return result;
        }

        if (!this.passwordEncoder.matches(fields.oldPassword(), user.get().getPassword())) {
            result.setStatus(false);
            result.putPayload("message", "当前密码不正确");
            return result;
        }

        this.userService.updatePassword(user.get().getId(), fields.newPassword());

        result.putPayload("message", "密码已修改");
        return result;
    }

}
