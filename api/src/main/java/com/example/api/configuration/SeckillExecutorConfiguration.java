package com.example.api.configuration;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class SeckillExecutorConfiguration {
    @Bean("seckillOrderExecutor")
    ThreadPoolTaskExecutor seckillOrderExecutor(
        @Value("${seckill.executor.workers:4}") int workers,
        @Value("${seckill.executor.queue-capacity:256}") int queueCapacity
    ) {
        if (workers < 1 || queueCapacity < 0) {
            throw new IllegalArgumentException("Seckill workers must be positive and queue capacity must be non-negative");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("seckill-order-");
        executor.setAllowCoreThreadTimeOut(true);
        // Reject before business work starts; running rejected tasks in the servlet thread
        // would defeat isolation and allow the database connection pool to be flooded.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
