package com.example.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 个人中心的请求体：用户 ID 取自 JWT；也没有用户名、角色、状态字段，请求里夹带了也绑定不上
public final class ProfileRequests {

    private ProfileRequests() {
    }

    // 改资料：都是选填，传 null 表示清空；校验规则与 UserEntity 一致
    public record Update(
        @Size(max = 32) String name,
        @Pattern(regexp = "[FM]") String gender,
        @Email @Size(max = 128) String email,
        @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @Size(max = 512) String avatarUrl
    ) {}

    // 改密码：新密码长度规则与注册一致
    public record Password(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 6, max = 32) String newPassword
    ) {}

}
