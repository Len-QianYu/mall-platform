package com.example.api.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipayLogger;

@Configuration
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "alipay", name = "enabled", havingValue = "true")
    AlipayClient alipayClient(AlipayProperties properties) throws AlipayApiException {
        if (!StringUtils.hasText(properties.getAppId())
            || !StringUtils.hasText(properties.getAppPrivateKey())
            || !StringUtils.hasText(properties.getAlipayPublicKey())) {
            throw new IllegalStateException("启用支付宝支付需要配置 ALIPAY_APP_ID、ALIPAY_APP_PRIVATE_KEY 和 ALIPAY_PUBLIC_KEY");
        }

        // SDK 的原始错误日志会记录完整签名报文；业务错误由 AlipayGateway 记录。
        AlipayLogger.setNeedEnableLogger(false);
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(properties.getAppId());
        config.setPrivateKey(properties.getAppPrivateKey());
        config.setAlipayPublicKey(properties.getAlipayPublicKey());
        config.setFormat("json");
        config.setCharset("UTF-8");
        config.setSignType("RSA2");
        config.setConnectTimeout(3000);
        config.setReadTimeout(5000);
        return new DefaultAlipayClient(config);
    }
}
