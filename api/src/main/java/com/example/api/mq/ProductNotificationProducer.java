package com.example.api.mq;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.api.configuration.RocketMQConfiguration;
import com.example.api.mapper.ProductNotificationEventMapper;

@Component
@ConditionalOnProperty(prefix = "product-notification", name = "delivery-enabled", havingValue = "true", matchIfMissing = true)
public class ProductNotificationProducer {
    private static final Logger log = LoggerFactory.getLogger(ProductNotificationProducer.class);
    private final ProductNotificationEventMapper events;
    private final Producer producer;
    private final Set<Long> sending = ConcurrentHashMap.newKeySet();

    public ProductNotificationProducer(ProductNotificationEventMapper events, Producer producer) {
        this.events = events;
        this.producer = producer;
    }

    // 只读已提交的发件箱；发送或确认写库失败都保留待发状态，重启后也能继续发送。
    @Scheduled(fixedDelayString = "${product-notification.dispatch-interval:2000}", initialDelay = 2000)
    public void publishPending() {
        for (Long id : this.events.selectPendingIds()) {
            if (!this.sending.add(id)) continue;
            try {
                Message message = ClientServiceProvider.loadService().newMessageBuilder()
                    .setTopic(RocketMQConfiguration.PRODUCT_NOTIFICATION_TOPIC)
                    .setKeys("product-event-" + id)
                    .setBody(id.toString().getBytes(StandardCharsets.UTF_8)).build();
                this.producer.sendAsync(message).whenComplete((receipt, error) -> {
                    try {
                        if (error == null) this.events.markPublished(id);
                        else log.warn("商品变动事件 {} 发送失败，稍后重试", id, error);
                    } catch (RuntimeException failure) {
                        log.warn("商品变动事件 {} 的发送确认保存失败，稍后重试", id, failure);
                    } finally { this.sending.remove(id); }
                });
            } catch (RuntimeException error) {
                this.sending.remove(id);
                log.warn("商品变动事件 {} 发送失败，稍后重试", id, error);
            }
        }
    }
}
