package com.example.api.controller.admin;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.entity.UserEntity;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.UserService;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;
import com.example.api.validator.UserValidateGroup;

// 管理端用户管理（/admin/** 只允许管理员访问，见 SecurityConfiguration）
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    // 用户列表：关键字匹配用户名 / 名字 / 手机号，可按角色、状态筛选
    @GetMapping
    public ResultTemplate index(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) Integer status
    ) {
        ResultTemplate result = new ResultTemplate();

        // 先计数再分页：页码超出总页数时 Pagination 会收回到最后一页
        Integer total = this.userService.count(keyword, role, status);
        Pagination pagination = Pagination.paginate(total, pageSize, page);
        List<UserEntity> users = this.userService.fetch(keyword, role, status, pagination);

        result.putPayload("users", users);
        result.putPayload("pagination", pagination);
        return result;
    }

    // 启用 / 禁用
    @PostMapping("/status")
    public ResultTemplate status(
        @RequestBody @Validated({ UserValidateGroup.UpdateStatus.class }) UserEntity fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("用户 ID 或状态不正确");
        }

        // 不能禁用自己，否则当前管理员会被锁在系统外
        if (fields.getStatus() == 0 && fields.getId().equals(this.currentUserId(jwt))) {
            result.setStatus(false);
            result.putPayload("message", "不能禁用自己");
            return result;
        }

        if (this.userService.updateStatus(fields.getId(), fields.getStatus()) == 0) {
            result.setStatus(false);
            result.putPayload("message", "用户不存在");
            return result;
        }

        result.putPayload("message", fields.getStatus() == 1 ? "已启用" : "已禁用");
        return result;
    }

    // 编辑资料和角色（用户名、密码不在这里改）
    @PostMapping("/update")
    public ResultTemplate update(
        @RequestBody @Validated({ UserValidateGroup.Update.class }) UserEntity fields,
        BindingResult bindingResult,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("用户信息不符合要求");
        }

        // 不能改自己的角色：管理员把自己降成普通用户就失去了管理权限
        if (fields.getId().equals(this.currentUserId(jwt)) && !"admin".equals(fields.getRole())) {
            result.setStatus(false);
            result.putPayload("message", "不能修改自己的角色");
            return result;
        }

        if (this.userService.update(fields) == 0) {
            result.setStatus(false);
            result.putPayload("message", "用户不存在");
            return result;
        }

        result.putPayload("message", "保存成功");
        return result;
    }

    // 当前登录管理员的 ID：登录时写进了 JWT 的 userId 声明，解析出来是 Long
    private Integer currentUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        return userId.intValue();
    }

}
