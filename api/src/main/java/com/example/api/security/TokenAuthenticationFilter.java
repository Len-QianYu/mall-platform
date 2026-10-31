package com.example.api.security;

import java.io.IOException;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TokenAuthenticationFilter extends GenericFilterBean {

    private final RequestMatcher requestMatcher;

    private final AuthenticationManager authenticationManager;

    public TokenAuthenticationFilter(RequestMatcher requestMatcher, AuthenticationManager authenticationManager) {
        this.requestMatcher = requestMatcher;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void doFilter(
            ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        
        // 如果请求头中的Authorization字段匹配，则进行token认证
        if (this.requestMatcher.matches((HttpServletRequest) request)) {
            try {
                // 提取token
                String token = ((HttpServletRequest) request).getHeader("Authorization");
                // 创建Token认证对象
                TokenAuthentication tokenAuthentication = new TokenAuthentication(token);
                tokenAuthentication.setDetails(new WebAuthenticationDetails((HttpServletRequest) request));
                // 通过认证管理器验证Token
                Authentication authentication = this.authenticationManager.authenticate(tokenAuthentication);
                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(securityContext);
                TokenAuthenticationSuccessHandler successHandler = new TokenAuthenticationSuccessHandler();
                successHandler.onAuthenticationSuccess(
                        (HttpServletRequest) request, (HttpServletResponse) response, chain, authentication);
            } catch (AuthenticationException exception) {
                // 认证失败，清除安全上下文
                SecurityContextHolder.clearContext();
                TokenAuthenticationFailureHandler failureHandler = new TokenAuthenticationFailureHandler();
                failureHandler.onAuthenticationFailure(
                        (HttpServletRequest) request, (HttpServletResponse) response, exception);
            }
        } else {
            // 如果请求头中的Authorization字段不匹配，则继续执行下一个过滤器
            chain.doFilter(request, response);
        }
    }

}
