package com.example.api.service;

import java.time.Duration;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.context.request.async.DeferredResult;

import com.example.api.utils.ResultTemplate;

@Component
public class SeckillOrderDispatcher {
    private final ThreadPoolTaskExecutor executor;
    private final boolean enabled;
    private final Duration timeout;

    public SeckillOrderDispatcher(@Qualifier("seckillOrderExecutor") ThreadPoolTaskExecutor executor,
        @Value("${seckill.executor.enabled:true}") boolean enabled,
        @Value("${seckill.executor.timeout:60s}") Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Seckill timeout must be positive");
        this.executor = executor;
        this.enabled = enabled;
        this.timeout = timeout;
    }

    public boolean isEnabled() { return this.enabled; }

    public DeferredResult<ResultTemplate> dispatch(Callable<ResultTemplate> work) {
        DeferredResult<ResultTemplate> response = new DeferredResult<>(this.timeout.toMillis());
        response.onTimeout(() -> response.setErrorResult(new AsyncRequestTimeoutException()));
        // Submit before MVC starts async processing, so a full queue returns a normal
        // 503 immediately. Accepted tasks carry the caller's security context and
        // produce their response only after the existing transaction has completed.
        this.executor.execute(new DelegatingSecurityContextRunnable(() -> {
            try { response.setResult(work.call()); }
            catch (Exception error) { response.setErrorResult(error); }
        }));
        return response;
    }
}
