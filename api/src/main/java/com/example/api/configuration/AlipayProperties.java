package com.example.api.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

// 不生成 toString，避免配置中的密钥出现在日志中。
@Getter
@Setter
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    private boolean enabled;
    private String appId;
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String appPrivateKey;
    private String alipayPublicKey;
    private String sellerId;
    private String notifyUrl = "http://localhost:8080/payment/alipay/notify";
    private String returnUrl = "http://localhost:5173/payment/return";
}
