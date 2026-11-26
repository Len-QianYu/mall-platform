package com.example.api.service;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.api.entity.UserEntity;
import com.example.api.exception.AccountFlowException;
import com.example.api.service.EmailVerificationCodes.Purpose;

@Service
public class AccountEmailService {
    private final UserService users;
    private final PasswordEncoder passwords;
    private final EmailVerificationCodes codes;
    private final VerificationMailSender mail;

    public AccountEmailService(UserService users, PasswordEncoder passwords,
        EmailVerificationCodes codes, VerificationMailSender mail) {
        this.users = users;
        this.passwords = passwords;
        this.codes = codes;
        this.mail = mail;
    }

    public void sendBindingCode(Integer owner, String email, String password, String requesterIp) {
        UserEntity user = this.requirePassword(owner, password);
        this.mail.requireConfigured();
        this.requireAvailableEmail(user.getId(), email);
        this.codes.reserveDelivery(email, requesterIp);
        this.deliver(Purpose.BIND, email, user);
    }

    public UserEntity bind(Integer owner, String email, String password, String code) {
        UserEntity user = this.requirePassword(owner, password);
        this.requireAvailableEmail(user.getId(), email);
        long version = version(user);
        if (!this.codes.consume(Purpose.BIND, email, owner, version, code)) throw invalidCode();
        try {
            if (this.users.bindEmail(owner, email, version) == 0) {
                throw new AccountFlowException("账号状态已变化，请重新登录后再试");
            }
        } catch (DuplicateKeyException error) {
            throw new AccountFlowException(HttpStatus.CONFLICT, "该邮箱已绑定其他账号");
        }
        UserEntity bound = this.users.fetchById(owner).orElseThrow(() -> new AccountFlowException("用户不存在"));
        if (version(bound) != version + 1) throw new AccountFlowException("账号状态已变化，请重新登录后再试");
        return bound;
    }

    public void sendResetCode(String email, String requesterIp) {
        this.mail.requireConfigured();
        // 未绑定或不存在的邮箱同样限流，响应不暴露账号是否存在。
        this.codes.reserveDelivery(email, requesterIp);
        this.users.fetchByEmail(email).filter(AccountEmailService::enabled)
            .ifPresent(user -> this.deliver(Purpose.RESET, email, user));
    }

    public void resetPassword(String email, String code, String newPassword) {
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AccountFlowException("密码过长，请缩短后重试");
        }
        UserEntity user = this.users.fetchByEmail(email).filter(AccountEmailService::enabled)
            .orElseThrow(AccountEmailService::invalidCode);
        long version = version(user);
        if (!this.codes.consume(Purpose.RESET, email, user.getId(), version, code)) throw invalidCode();
        if (this.users.resetPassword(user.getId(), email, version, newPassword) == 0) throw invalidCode();
    }

    private UserEntity requirePassword(Integer owner, String password) {
        UserEntity user = this.users.fetchById(owner).filter(AccountEmailService::enabled)
            .orElseThrow(() -> new AccountFlowException("用户不存在或账号已禁用"));
        if (!this.passwords.matches(password, user.getPassword())) throw new AccountFlowException("当前密码不正确");
        return user;
    }

    private void requireAvailableEmail(Integer owner, String email) {
        this.users.fetchByEmail(email).filter(user -> !owner.equals(user.getId())).ifPresent(user -> {
            throw new AccountFlowException(HttpStatus.CONFLICT, "该邮箱已绑定其他账号");
        });
    }

    private void deliver(Purpose purpose, String email, UserEntity user) {
        long version = version(user);
        String code = this.codes.issue(purpose, email, user.getId(), version);
        try {
            this.mail.send(email, code, purpose);
        } catch (AccountFlowException failure) {
            try { this.codes.discard(purpose, email, user.getId(), version, code); }
            catch (AccountFlowException cleanupFailure) { /* Redis TTL will expire this code. */ }
            throw failure;
        }
    }

    private static long version(UserEntity user) { return user.getAuthVersion() == null ? 0L : user.getAuthVersion(); }
    private static boolean enabled(UserEntity user) { return user.getStatus() != null && user.getStatus() == 1; }
    private static AccountFlowException invalidCode() { return new AccountFlowException("验证码无效或已过期，请重新获取"); }
}
