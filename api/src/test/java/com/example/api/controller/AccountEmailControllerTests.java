package com.example.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api.entity.UserEntity;
import com.example.api.exception.AccountFlowException;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.AccountEmailService;
import com.example.api.service.UserService;
import com.example.api.utils.JwtUtils;

@WebMvcTest({ AccountEmailController.class, SecurityController.class })
@Import(SecurityConfiguration.class)
class AccountEmailControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private AccountEmailService emails;
    @MockitoBean private UserService users;
    @MockitoBean private PasswordEncoder passwords;
    @MockitoBean private JwtUtils tokens;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void anonymousUsersCannotIssueOrConfirmEmailBindings() throws Exception {
        this.mvc.perform(post("/account/email/code").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"owner@example.com\",\"password\":\"123456\"}")).andExpect(status().isUnauthorized());
        this.mvc.perform(post("/account/email/bind").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"owner@example.com\",\"password\":\"123456\",\"code\":\"123456\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(this.emails);
    }

    @ParameterizedTest
    @ValueSource(strings = { "user", "admin", "super_admin" })
    void bindingUsesTheJwtOwnerAndDoesNotExposeTheCode(String role) throws Exception {
        authenticate(role);
        this.mvc.perform(post("/account/email/code").header(HttpHeaders.AUTHORIZATION, "Bearer account-token")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"id":999,"email":"OWNER@EXAMPLE.COM","password":"123456"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.expiresInSeconds").value(600))
            .andExpect(jsonPath("$.payload.resendAfterSeconds").value(60))
            .andExpect(jsonPath("$.payload.code").doesNotExist());
        verify(this.emails).sendBindingCode(7, "owner@example.com", "123456", "127.0.0.1");
    }

    @Test
    void bindingReturnsAFreshLoginWhileHidingPasswordAndInternalVersion() throws Exception {
        authenticate("super_admin");
        UserEntity user = account("super_admin");
        user.setAuthVersion(1L);
        when(this.emails.bind(7, "owner@example.com", "123456", "012345")).thenReturn(user);
        when(this.tokens.generateToken(user)).thenReturn("fresh-token");
        this.mvc.perform(post("/account/email/bind").header(HttpHeaders.AUTHORIZATION, "Bearer account-token")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"owner@example.com","password":"123456","code":"012345"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.token").value("fresh-token"))
            .andExpect(jsonPath("$.payload.user.role").value("super_admin"))
            .andExpect(jsonPath("$.payload.user.emailVerified").value(true))
            .andExpect(jsonPath("$.payload.user.password").doesNotExist())
            .andExpect(jsonPath("$.payload.user.authVersion").doesNotExist());
    }

    @Test
    void passwordRecoveryIsPublicAndOnlyReturnsGenericDeliveryInformation() throws Exception {
        this.mvc.perform(post("/account/password-reset/code").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"OWNER@EXAMPLE.COM\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.message").value("如果该邮箱已绑定有效账号，验证码将发送至邮箱"))
            .andExpect(jsonPath("$.payload.code").doesNotExist()).andExpect(jsonPath("$.payload.userId").doesNotExist());
        verify(this.emails).sendResetCode("owner@example.com", "127.0.0.1");
    }

    @Test
    void resetRequiresReloginAndIgnoresClientIdentityOverrides() throws Exception {
        this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON).content("""
            {"id":999,"role":"super_admin","email":"OWNER@EXAMPLE.COM","code":"123456","newPassword":"new-password"}
            """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.token").doesNotExist());
        verify(this.emails).resetPassword("owner@example.com", "123456", "new-password");
        verifyNoInteractions(this.tokens);
    }

    @Test
    void rateLimitsExposeRetryAfterWithoutSendingACode() throws Exception {
        doThrow(new AccountFlowException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试", 60))
            .when(this.emails).sendResetCode("owner@example.com", "127.0.0.1");
        this.mvc.perform(post("/account/password-reset/code").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"owner@example.com\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.payload.retryAfterSeconds").value(60));
    }

    @Test
    void rejectsMalformedRecoveryFieldsBeforeCallingTheService() throws Exception {
        this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"not-email\",\"code\":\"12\",\"newPassword\":\"123\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.emails);
    }

    @ParameterizedTest
    @ValueSource(strings = { "user", "admin", "super_admin" })
    void emailLoginRetainsTheStoredRole(String role) throws Exception {
        UserEntity user = account(role);
        when(this.users.fetchByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(this.passwords.matches("123456", "stored-hash")).thenReturn(true);
        when(this.tokens.generateToken(user)).thenReturn("login-token");
        String requestedRole = role.equals("user") ? "user" : "admin";
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"email":"OWNER@EXAMPLE.COM","password":"123456","role":"%s"}
            """.formatted(requestedRole)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.user.role").value(role)).andExpect(jsonPath("$.payload.user.password").doesNotExist());
        verify(this.users).fetchByEmail("owner@example.com");
        verify(this.users, never()).fetchByUsername(anyString());
    }

    @Test
    void rejectsRequestsContainingBothUsernameAndEmail() throws Exception {
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"username":"owner","email":"owner@example.com","password":"123456","role":"user"}
            """))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.users, this.passwords, this.tokens);
    }

    private UserEntity account(String role) {
        UserEntity user = new UserEntity();
        user.setId(7); user.setUsername("owner"); user.setRole(role);
        user.setEmail("owner@example.com"); user.setEmailVerified(true); user.setPassword("stored-hash");
        return user;
    }

    private void authenticate(String role) {
        when(this.decoder.decode("account-token")).thenReturn(Jwt.withTokenValue("account-token")
            .header("alg", "RS256").subject("owner").claim("userId", 7L).claim("role", role).build());
    }
}
