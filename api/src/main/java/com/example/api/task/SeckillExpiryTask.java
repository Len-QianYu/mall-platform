package com.example.api.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.api.service.SeckillService;

@Component
@ConditionalOnProperty(name = "seckill.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class SeckillExpiryTask {
    private static final Logger log = LoggerFactory.getLogger(SeckillExpiryTask.class);
    private final SeckillService service;
    public SeckillExpiryTask(SeckillService service) { this.service = service; }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void closeExpired() {
        try {
            for (Long id : this.service.fetchExpiredIds(100)) {
                try { this.service.closeExpired(id); }
                catch (RuntimeException failure) { log.warn("自动结束秒杀活动 {} 失败，下轮重试", id, failure); }
            }
        } catch (RuntimeException failure) {
            log.warn("查询到期秒杀活动失败，下轮重试: {}", failure.getClass().getSimpleName());
        }
    }
}
