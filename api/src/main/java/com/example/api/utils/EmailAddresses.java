package com.example.api.utils;

import java.util.Locale;

public final class EmailAddresses {
    private EmailAddresses() { }

    public static String normalize(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
