package com.example.api.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.api.service.UserService;

public class AccountTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final UserService users;

    public AccountTokenValidator(UserService users) { this.users = users; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object owner = token.getClaims().get("userId");
        Object version = token.getClaims().get("authVersion");
        if (!(owner instanceof Number number) || number.longValue() < 1 || number.longValue() > Integer.MAX_VALUE
            || (version != null && !(version instanceof Number))) return invalid();
        long issuedVersion = version == null ? 0L : ((Number) version).longValue();
        boolean current = this.users.fetchById(number.intValue()).filter(user ->
            user.getStatus() != null && user.getStatus() == 1
                && issuedVersion == (user.getAuthVersion() == null ? 0L : user.getAuthVersion())).isPresent();
        return current ? OAuth2TokenValidatorResult.success() : invalid();
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "登录已失效，请重新登录", null));
    }
}
