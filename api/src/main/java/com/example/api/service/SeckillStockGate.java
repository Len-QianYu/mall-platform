package com.example.api.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.example.api.exception.TradeFailedException;

@Component
public class SeckillStockGate {
    private static final Logger log = LoggerFactory.getLogger(SeckillStockGate.class);
    // 崩溃时未回补的预扣最多影响 10 秒；重建和跨系统故障由数据库条件更新兜底。
    private static final String CACHE_TTL_MS = "10000";
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> reserveScript = script("lua/seckill-reserve.lua");
    private final DefaultRedisScript<Long> releaseScript = script("lua/seckill-release.lua");

    public SeckillStockGate(StringRedisTemplate redis) { this.redis = redis; }

    private static DefaultRedisScript<Long> script(String resource) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(resource));
        script.setResultType(Long.class);
        return script;
    }

    public static String key(Long activityId) { return "mall:seckill:{" + activityId + "}:inventory"; }

    public void reserve(Long activityId, Integer userId, String requestId, Integer quantity,
        Integer stock, Integer purchased, Integer limit) {
        Long result;
        try {
            result = this.redis.execute(this.reserveScript, List.of(key(activityId)), quantity.toString(),
                userId.toString(), requestId, stock.toString(), purchased.toString(), limit.toString(), CACHE_TTL_MS);
        } catch (RuntimeException unavailable) {
            log.warn("秒杀预扣不可用: {}", unavailable.getClass().getSimpleName());
            throw new TradeFailedException("秒杀服务暂时不可用，请稍后重试");
        }
        if (Long.valueOf(-1).equals(result)) throw new TradeFailedException("秒杀商品已抢完，请稍后刷新");
        if (Long.valueOf(-2).equals(result)) throw new TradeFailedException("已达到本场活动的限购数量");
        if (!Long.valueOf(1).equals(result)) throw new TradeFailedException("秒杀请求不正确，请重新结算");
    }

    public void release(Long activityId, Integer userId, String requestId) {
        try {
            this.redis.execute(this.releaseScript, List.of(key(activityId)), userId.toString(), requestId);
        } catch (RuntimeException unavailable) {
            log.warn("秒杀预扣回补失败，等待缓存过期: {}", unavailable.getClass().getSimpleName());
        }
    }

    public void invalidate(Long activityId) {
        try {
            this.redis.delete(key(activityId));
        } catch (RuntimeException unavailable) {
            log.warn("秒杀库存缓存删除失败，等待缓存过期: {}", unavailable.getClass().getSimpleName());
        }
    }
}
