package com.example.api.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import com.example.api.entity.UserEntity;

public class TokenAuthentication extends AbstractAuthenticationToken {

    private final Object principal;

    public TokenAuthentication(String principal) {
        super(List.of());
        this.principal	= principal;
        this.setAuthenticated(false);
    }

    public TokenAuthentication(UserEntity user, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal	= user;
        this.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

}
