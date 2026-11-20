package com.example.api.configuration;

import java.time.Duration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

// 商品缓存使用 Lettuce，下单锁使用 Redisson；两个客户端连接同一个 Redis。
@Configuration
@EnableConfigurationProperties(DataRedisProperties.class)
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(DataRedisProperties redis) {
        Config config = new Config();
        // Redis 不可用时仍允许应用启动；下单获取锁失败会返回可重试的业务错误。
        config.setLazyInitialization(true);
        config.setNettyThreads(4);
        config.setThreads(2);
        SingleServerConfig server = config.useSingleServer()
            .setAddress((redis.getSsl().isEnabled() ? "rediss://" : "redis://")
                + redis.getHost() + ":" + redis.getPort())
            .setDatabase(redis.getDatabase())
            .setConnectTimeout(milliseconds(redis.getConnectTimeout(), Duration.ofSeconds(1)))
            .setTimeout(milliseconds(redis.getTimeout(), Duration.ofSeconds(1)))
            .setRetryAttempts(0)
            .setConnectionMinimumIdleSize(1)
            .setConnectionPoolSize(8)
            .setSubscriptionConnectionMinimumIdleSize(1)
            .setSubscriptionConnectionPoolSize(2);
        if (StringUtils.hasText(redis.getUsername())) server.setUsername(redis.getUsername());
        if (StringUtils.hasText(redis.getPassword())) server.setPassword(redis.getPassword());
        return Redisson.create(config);
    }

    private static int milliseconds(Duration value, Duration fallback) {
        return Math.toIntExact((value == null ? fallback : value).toMillis());
    }
}
