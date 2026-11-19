package com.example.api.task;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.api.entity.PaymentEntity;
import com.example.api.service.PaymentService;

// 补偿浏览器未返回、异步回调不可达及取消后才打开收银台的情况。
@Component
@ConditionalOnProperty(prefix = "payment", name = "reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationTask.class);
    private static final int BATCH_SIZE = 100;
    private final PaymentService payments;
    private long lastPaymentId;

    public PaymentReconciliationTask(PaymentService payments) {
        this.payments = payments;
    }

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void reconcile() {
        List<PaymentEntity> pending = this.payments.fetchPendingAlipay(this.lastPaymentId, BATCH_SIZE);
        if (pending.isEmpty() && this.lastPaymentId > 0) {
            this.lastPaymentId = 0;
            pending = this.payments.fetchPendingAlipay(this.lastPaymentId, BATCH_SIZE);
        }
        for (PaymentEntity payment : pending) {
            // 查询失败也继续推进游标，下一次循环再重试，避免阻塞后面的流水。
            this.lastPaymentId = payment.getId();
            try {
                this.payments.reconcile(payment.getId());
            } catch (RuntimeException error) {
                log.warn("支付流水 {} 对账失败，下轮重试", payment.getPaymentNo());
            }
        }
    }
}
