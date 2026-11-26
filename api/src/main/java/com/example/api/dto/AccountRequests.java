package com.example.api.dto;

import com.example.api.utils.EmailAddresses;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountRequests {
    private AccountRequests() { }

    public record Login(
        @Size(min = 2, max = 16) String username,
        @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 6, max = 32) String password,
        @NotBlank @Pattern(regexp = "user|admin|super_admin") String role
    ) {
        public Login {
            email = EmailAddresses.normalize(email);
            role = role == null ? "user" : role;
        }

        @AssertTrue
        public boolean isIdentityValid() {
            return (username != null && !username.isBlank()) != (email != null);
        }
    }

    public record BindCode(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 6, max = 32) String password
    ) {
        public BindCode { email = EmailAddresses.normalize(email); }
    }

    public record BindEmail(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Size(min = 6, max = 32) String password,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
    ) {
        public BindEmail { email = EmailAddresses.normalize(email); }
    }

    public record ResetCode(@NotBlank @Email @Size(max = 128) String email) {
        public ResetCode { email = EmailAddresses.normalize(email); }
    }

    public record ResetPassword(
        @NotBlank @Email @Size(max = 128) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
        @NotBlank @Size(min = 6, max = 32) String newPassword
    ) {
        public ResetPassword { email = EmailAddresses.normalize(email); }
    }
}
