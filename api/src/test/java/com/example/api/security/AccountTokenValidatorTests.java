package com.example.api.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.api.entity.UserEntity;
import com.example.api.service.UserService;

class AccountTokenValidatorTests {
    @Test
    void passwordChangesInvalidateBothOldAndLegacyTokens() {
        UserService users = mock(UserService.class);
        UserEntity user = new UserEntity(); user.setAuthVersion(0L);
        when(users.fetchById(7)).thenReturn(Optional.of(user));
        var validator = new AccountTokenValidator(users);
        var legacy = Jwt.withTokenValue("test").header("alg", "RS256").claim("userId", 7L).build();
        var versionZero = Jwt.withTokenValue("test").header("alg", "RS256").claim("userId", 7L).claim("authVersion", 0L).build();
        var versionOne = Jwt.withTokenValue("test").header("alg", "RS256").claim("userId", 7L).claim("authVersion", 1L).build();
        assertFalse(validator.validate(legacy).hasErrors());
        assertFalse(validator.validate(versionZero).hasErrors());
        user.setAuthVersion(1L);
        assertTrue(validator.validate(legacy).hasErrors());
        assertTrue(validator.validate(versionZero).hasErrors());
        assertFalse(validator.validate(versionOne).hasErrors());
        user.setStatus(0);
        assertTrue(validator.validate(versionOne).hasErrors());
    }
}
