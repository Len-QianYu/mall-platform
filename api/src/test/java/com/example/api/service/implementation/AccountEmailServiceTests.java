package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.api.entity.UserEntity;
import com.example.api.exception.AccountFlowException;
import com.example.api.service.AccountEmailService;
import com.example.api.service.EmailVerificationCodes;
import com.example.api.service.EmailVerificationCodes.Purpose;
import com.example.api.service.UserService;
import com.example.api.service.VerificationMailSender;

class AccountEmailServiceTests {
    private UserService users;
    private PasswordEncoder passwords;
    private EmailVerificationCodes codes;
    private VerificationMailSender mail;
    private AccountEmailService service;
    private UserEntity user;

    @BeforeEach
    void setup() {
        this.users = mock(UserService.class); this.passwords = mock(PasswordEncoder.class);
        this.codes = mock(EmailVerificationCodes.class); this.mail = mock(VerificationMailSender.class);
        this.service = new AccountEmailService(this.users, this.passwords, this.codes, this.mail);
        this.user = new UserEntity(); this.user.setId(10); this.user.setPassword("stored-hash"); this.user.setAuthVersion(3L);
        when(this.users.fetchById(10)).thenReturn(Optional.of(this.user));
        when(this.passwords.matches("current-password", "stored-hash")).thenReturn(true);
    }

    @Test
    void unknownOrUnverifiedAddressesAreRateLimitedWithoutReceivingMail() {
        when(this.users.fetchByEmail("unlinked@example.com")).thenReturn(Optional.empty());
        this.service.sendResetCode("unlinked@example.com", "test-ip");
        verify(this.codes).reserveDelivery("unlinked@example.com", "test-ip");
        verify(this.codes, never()).issue(any(), anyString(), anyInt(), anyLong());
        verify(this.mail, never()).send(anyString(), anyString(), any());
    }

    @Test
    void incorrectCurrentPasswordCannotStartBinding() {
        assertThrows(AccountFlowException.class, () -> this.service.sendBindingCode(10, "new@example.com", "wrong-password", "test-ip"));
        verifyNoInteractions(this.mail, this.codes);
    }

    @Test
    void failedMailDeliveryDiscardsOnlyTheIssuedChallenge() {
        when(this.users.fetchByEmail("new@example.com")).thenReturn(Optional.empty());
        when(this.codes.issue(Purpose.BIND, "new@example.com", 10, 3L)).thenReturn("123456");
        doThrow(new AccountFlowException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试"))
            .when(this.mail).send("new@example.com", "123456", Purpose.BIND);
        assertThrows(AccountFlowException.class, () -> this.service.sendBindingCode(10, "new@example.com", "current-password", "test-ip"));
        verify(this.codes).discard(Purpose.BIND, "new@example.com", 10, 3L, "123456");
    }

    @Test
    void correctBindingConsumesTheOwnerVersionAndReturnsTheNewVersion() {
        UserEntity bound = new UserEntity(); bound.setId(10); bound.setAuthVersion(4L); bound.setEmailVerified(true);
        when(this.users.fetchById(10)).thenReturn(Optional.of(this.user), Optional.of(bound));
        when(this.users.fetchByEmail("new@example.com")).thenReturn(Optional.empty());
        when(this.codes.consume(Purpose.BIND, "new@example.com", 10, 3L, "123456")).thenReturn(true);
        when(this.users.bindEmail(10, "new@example.com", 3L)).thenReturn(1);
        assertEquals(bound, this.service.bind(10, "new@example.com", "current-password", "123456"));
    }

    @Test
    void bindingCannotIssueALoginAfterAConcurrentPasswordChange() {
        UserEntity changed = new UserEntity(); changed.setId(10); changed.setAuthVersion(5L);
        when(this.users.fetchById(10)).thenReturn(Optional.of(this.user), Optional.of(changed));
        when(this.users.fetchByEmail("new@example.com")).thenReturn(Optional.empty());
        when(this.codes.consume(Purpose.BIND, "new@example.com", 10, 3L, "123456")).thenReturn(true);
        when(this.users.bindEmail(10, "new@example.com", 3L)).thenReturn(1);
        assertThrows(AccountFlowException.class, () -> this.service.bind(10, "new@example.com", "current-password", "123456"));
    }

    @Test
    void resetsOnlyTheVerifiedOwnerUsingTheExpectedVersion() {
        when(this.users.fetchByEmail("owner@example.com")).thenReturn(Optional.of(this.user));
        when(this.codes.consume(Purpose.RESET, "owner@example.com", 10, 3L, "123456")).thenReturn(true);
        when(this.users.resetPassword(10, "owner@example.com", 3L, "new-password")).thenReturn(1);
        this.service.resetPassword("owner@example.com", "123456", "new-password");
        verify(this.users).resetPassword(10, "owner@example.com", 3L, "new-password");
    }

    @Test
    void expiredOrWrongPurposeCodesNeverChangeThePassword() {
        when(this.users.fetchByEmail("owner@example.com")).thenReturn(Optional.of(this.user));
        assertThrows(AccountFlowException.class, () -> this.service.resetPassword("owner@example.com", "123456", "new-password"));
        verify(this.users, never()).resetPassword(anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    void disabledOwnersCannotRecoverOrReenableTheirAccount() {
        this.user.setStatus(0);
        when(this.users.fetchByEmail("owner@example.com")).thenReturn(Optional.of(this.user));
        assertThrows(AccountFlowException.class, () -> this.service.resetPassword("owner@example.com", "123456", "new-password"));
        verify(this.codes, never()).consume(any(), anyString(), anyInt(), anyLong(), anyString());
    }
}
