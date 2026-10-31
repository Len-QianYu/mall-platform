package com.example.api.controller;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.entity.UserEntity;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.UserService;
import com.example.api.utils.ResultTemplate;
import com.example.api.validator.UserValidateGroup;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

}
