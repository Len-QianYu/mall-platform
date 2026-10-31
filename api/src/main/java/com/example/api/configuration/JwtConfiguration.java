package com.example.api.configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

// 编码只发生在登录/刷新时；
// 解码发生在每次受保护请求时；
@Configuration
public class JwtConfiguration {

    private static final String Key_Id = UUID.randomUUID().toString();
    private static final String IssUer = "spring-security-jwt";
    private static final Duration Token_Validity = Duration.ofHours(2);

    // 提供KeyPair，用于生成JWT token
    @Bean
    public KeyPair keyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            // 生成KeyPair失败，抛出异常
            throw new RuntimeException("未能生成密钥对", e);
        }
    }

    // 提供RSAKey，用于生成JWT token
    @Bean
    public RSAKey rsaKey(KeyPair keyPair) {
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(Key_Id)
                .build();
    }

    // 提供JWKSet，用于存储RSA公钥
    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        try {
            JWKSet jwkSet = new JWKSet(rsaKey);
            return new ImmutableJWKSet<>(jwkSet);
        } catch (Exception e) {
            // 生成JWKSet失败，抛出异常
            throw new RuntimeException("未能创建 JWK 源", e);
        }
    }

    // 提供JwtDecoder，用于解码JWT token
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) {
        try {
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
                    .build();
        } catch (Exception e) {
            // 创建JwtDecoder失败，抛出异常
            throw new RuntimeException("未能创建 JWT 解码器", e);
        }
    }

    // 提供JwtEncoder，用于编码JWT token
    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    // 提供issuer，用于标识JWT token的发行者
    public static String getIssuer() {
        return IssUer;
    }

    // 提供token有效期
    public static Duration getTokenValidity() {
        return Token_Validity;
    }
}
