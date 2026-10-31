package com.example.api.security;

import org.springframework.security.core.AuthenticationException;

public class TokenAuthenticationException extends AuthenticationException {

    public TokenAuthenticationException() {
        super("Illegal Token!");
    }

}
