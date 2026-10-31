package com.example.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity httpSecurity,
        JwtDecoder jwtDecoder,
        JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        httpSecurity
            .sessionManagement(session -> {
                // 无状态：JWT 认证不需要 session
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
            })
            .cors(cors -> {
                CorsConfiguration config = new CorsConfiguration();
                config.applyPermitDefaultValues();

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);

                cors.configurationSource(source);
            })
            .csrf(csrf -> {
                csrf.disable();
            })
            .logout(logout -> {
                logout.disable(); // 禁用默认的logout配置
            })
            .authorizeHttpRequests(authorize -> {
                // 允许登录和注册接口无需认证（放行）
                authorize.requestMatchers("/login", "/register","/logout").permitAll();
                // 放行错误页：否则匿名请求出错（如 500）转发到 /error 时会被拦成 401，掩盖真实错误
                authorize.requestMatchers("/error").permitAll();
                // 放行静态与上传的公开资源（<img> 标签不会带 Authorization）
                authorize.requestMatchers(
                    "/assets/**",
                    "/favicon.ico",
                    "/images/**",
                    "/avatars/**",
                    "/themes/**",
                    "/ai-images/**",
                    "/ai-videos/**",
                    "/covers/**",
                    "/videos/**"
                    // "/public/**",
                    // "/static/**"
                ).permitAll();
                // 放行WebSocket端点
                authorize.requestMatchers("/ws/**").permitAll();
                // 放行用户匿名注册接口
				authorize.requestMatchers("/user/create").permitAll();
                // 管理端接口只允许管理员访问：前端路由守卫只是界面拦截，真正的权限在这里收口
                authorize.requestMatchers("/admin/**").hasRole("admin");
                // 其他所有请求都需要JWT认证
                authorize.anyRequest().authenticated();
            })
            // JwtDecoder，用于解码JWT token
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            );

        return httpSecurity.build();
    }

    // 把 JWT 的 role 声明转成角色权限：role=admin → ROLE_admin，供 hasRole("admin") 判断。
    // 默认转换器只读 scope 声明，不配置的话 JWT 里的角色不会生效，任何登录用户都能访问管理端
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    // 库里存的是不带 {bcrypt} 前缀的 BCrypt 哈希（见 db/data.sql），
    // DelegatingPasswordEncoder 依赖前缀识别算法，匹配这类哈希会直接抛异常，所以直接用 BCrypt
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}


// 特性	        第一个配置 (自定义Token认证)	第二个配置 (OAuth2资源服务器)
// 认证方式	    自定义Token认证	标准OAuth2      JWT认证
// 认证管理器	自定义 ProviderManager + TokenAuthenticationProvider	Spring Security 自动配置
// 过滤器	    自定义 TokenAuthenticationFilter	内置 BearerTokenAuthenticationFilter
// 架构层次	    较低层，手动实现	较高层，使用Spring Security抽象
// 配置复杂度	高，需要手动实现很多组件	     低，声明式配置
// 标准化	    非标准，自定义协议	            符合OAuth2标准

//所以认证管理器和过滤器改成只需要配置JWT解码器