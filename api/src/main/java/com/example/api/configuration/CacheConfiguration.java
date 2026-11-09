package com.example.api.configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.example.api.entity.ProductEntity;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

// 商品缓存：Spring Cache 注解 + Redis（Cache Aside）。读：@Cacheable 先查 Redis，没有再查库并写回；
// 写：改完数据库再删缓存（ProductServiceImpl）。key 形如「缓存名::参数」，如 product:detail::1001。
// 过期时间按能容忍多旧来定：列表、计数 1 分钟（下单改了库存、销量不主动删）；详情、分类 30 分钟（变化时都会主动删）
@Configuration
@EnableCaching
public class CacheConfiguration implements CachingConfigurer {

    // 前台商品详情（只缓存上架商品，不存在或已下架的缓存空值）
    public static final String PRODUCT_DETAIL = "product:detail";

    // 前台商品列表的一页
    public static final String PRODUCT_LIST = "product:list";

    // 前台列表的总数（按关键字 + 分类）
    public static final String PRODUCT_COUNT = "product:count";

    // 有上架商品的分类
    public static final String PRODUCT_CATEGORIES = "product:categories";

    // 空值只存 1 分钟：反复查不存在的商品 ID 时不用每次都查库（缓存穿透），又不会长期占着内存
    private static final Duration NULL_VALUE_TTL = Duration.ofMinutes(1);

    @Bean
    public RedisCacheManagerBuilderCustomizer productCacheCustomizer(
        RedisConnectionFactory connectionFactory,
        JsonMapper jsonMapper
    ) {
        return builder -> builder
            .cacheWriter(RedisCacheWriter.create(connectionFactory, writer -> writer
                // 写缓存（写入、删除）同步执行：用 Lettuce 时默认是异步的，发出命令就返回、失败了也没有任何提示；
                // 改成同步后，删缓存在接口返回前就完成，Redis 出错时也能记到日志里
                .immediateWrites()
                // 清空整个缓存（后台改商品后清列表）时用 SCAN 分批找 key；默认的 KEYS 会在找完之前阻塞整个 Redis
                .batchStrategy(BatchStrategies.scan(1000))))
            .withCacheConfiguration(PRODUCT_DETAIL,
                config(jsonMapper, jsonMapper.constructType(ProductEntity.class), Duration.ofMinutes(30)))
            .withCacheConfiguration(PRODUCT_LIST,
                config(jsonMapper, jsonMapper.getTypeFactory().constructCollectionType(List.class, ProductEntity.class), Duration.ofMinutes(1)))
            .withCacheConfiguration(PRODUCT_COUNT,
                config(jsonMapper, jsonMapper.constructType(Integer.class), Duration.ofMinutes(1)))
            .withCacheConfiguration(PRODUCT_CATEGORIES,
                config(jsonMapper, jsonMapper.getTypeFactory().constructCollectionType(List.class, String.class), Duration.ofMinutes(30)));
    }

    // Redis 出错（连不上、超时）时只记一行日志、当作没命中，接口照常查库返回：缓存挂了只是变慢，不报错
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    // 值存 JSON，按每个缓存固定的类型读写，JSON 里不夹带类名
    private static RedisCacheConfiguration config(JsonMapper jsonMapper, JavaType type, Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, type)))
            .entryTtl((key, value) -> value == null ? NULL_VALUE_TTL : withJitter(ttl));
    }

    // 过期时间随机多加 0 ~ 10%：同一时刻写进去的一批缓存不会在同一时刻一起过期、把请求同时压到数据库（缓存雪崩）
    private static Duration withJitter(Duration ttl) {
        return ttl.plusMillis(ThreadLocalRandom.current().nextLong(ttl.toMillis() / 10 + 1));
    }

}
