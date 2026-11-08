package com.example.api.task;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.api.service.OrderService;

// 支付超时的兜底：正常由 RocketMQ 延时消息准时取消，这里每 5 分钟扫一次漏掉的订单
// （下单后消息没发出去、消息重试耗尽进了死信队列等）。只扫超时 1 分钟以上的，不和延时消息抢正常到期的订单；
// 真撞上了也没关系，cancel 是条件更新，只有一方能取消成功
@Component
public class OrderPayTimeoutScanTask {

    private static final Logger log = LoggerFactory.getLogger(OrderPayTimeoutScanTask.class);

    // 每轮最多处理的订单数；积压更多时下一轮接着处理
    private static final int BATCH_SIZE = 500;

    private final OrderService orderService;

    private final Duration expireAfter;

    public OrderPayTimeoutScanTask(OrderService orderService, @Value("${order.pay-timeout}") Duration payTimeout) {
        this.orderService = orderService;
        this.expireAfter = payTimeout.plusMinutes(1);
    }

    @Scheduled(initialDelay = 1, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void cancelExpiredOrders() {
        List<Long> ids = this.orderService.fetchExpiredIds(this.expireAfter, BATCH_SIZE);
        int cancelled = 0;
        for (Long id : ids) {
            // 一笔取消失败不影响其他订单，下一轮还会再扫到它
            try {
                if (this.orderService.cancel(id)) {
                    cancelled++;
                }
            } catch (RuntimeException error) {
                log.error("订单 {} 超时取消失败，下一轮重试", id, error);
            }
        }
        if (cancelled > 0) {
            log.info("定时扫描取消了 {} 笔超时未支付的订单", cancelled);
        }
    }

}
