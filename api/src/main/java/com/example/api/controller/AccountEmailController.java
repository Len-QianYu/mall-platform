package com.example.api.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.AccountRequests;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.AccountEmailService;
import com.example.api.service.EmailVerificationCodes;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.JwtUtils;
import com.example.api.utils.ResultTemplate;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/account")
public class AccountEmailController {
    private final AccountEmailService emails;
    private final JwtUtils tokens;

    public AccountEmailController(AccountEmailService emails, JwtUtils tokens) {
        this.emails = emails;
        this.tokens = tokens;
    }

    @PostMapping("/email/code")
    public ResultTemplate bindingCode(@RequestBody @Validated AccountRequests.BindCode fields,
        BindingResult errors, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        validate(errors);
        this.emails.sendBindingCode(CurrentUser.id(jwt), fields.email(), fields.password(), request.getRemoteAddr());
        return sent("验证码已发送，请查收邮箱");
    }

    @PostMapping("/email/bind")
    public ResultTemplate bind(@RequestBody @Validated AccountRequests.BindEmail fields,
        BindingResult errors, @AuthenticationPrincipal Jwt jwt) {
        validate(errors);
        var user = this.emails.bind(CurrentUser.id(jwt), fields.email(), fields.password(), fields.code());
        var result = new ResultTemplate();
        result.putPayload("token", this.tokens.generateToken(user));
        result.putPayload("user", user);
        return result.putPayload("message", "邮箱绑定成功");
    }

    @PostMapping("/password-reset/code")
    public ResultTemplate resetCode(@RequestBody @Validated AccountRequests.ResetCode fields,
        BindingResult errors, HttpServletRequest request) {
        validate(errors);
        this.emails.sendResetCode(fields.email(), request.getRemoteAddr());
        return sent("如果该邮箱已绑定有效账号，验证码将发送至邮箱");
    }

    @PostMapping("/password-reset")
    public ResultTemplate reset(@RequestBody @Validated AccountRequests.ResetPassword fields, BindingResult errors) {
        validate(errors);
        this.emails.resetPassword(fields.email(), fields.code(), fields.newPassword());
        return new ResultTemplate().putPayload("message", "密码已重置，请使用新密码登录");
    }

    private static void validate(BindingResult errors) {
        if (errors.hasErrors()) throw new ValidateFailedException("邮箱、密码或验证码格式不正确");
    }

    private static ResultTemplate sent(String message) {
        var result = new ResultTemplate();
        result.putPayload("expiresInSeconds", EmailVerificationCodes.VALIDITY_SECONDS);
        result.putPayload("resendAfterSeconds", EmailVerificationCodes.RESEND_SECONDS);
        return result.putPayload("message", message);
    }
}
