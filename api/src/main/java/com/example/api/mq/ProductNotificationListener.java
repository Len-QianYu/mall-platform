package com.example.api.mq;

import java.nio.charset.StandardCharsets;

import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.api.service.ProductNotificationService;

@Component
public class ProductNotificationListener implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(ProductNotificationListener.class);
    private final ProductNotificationService notifications;

    public ProductNotificationListener(ProductNotificationService notifications) { this.notifications = notifications; }

    @Override
    public ConsumeResult consume(MessageView message) {
        String body = StandardCharsets.UTF_8.decode(message.getBody()).toString();
        long eventId;
        try {
            eventId = Long.parseLong(body);
            if (eventId < 1) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            log.warn("忽略无效的商品通知消息 {}", message.getMessageId());
            return ConsumeResult.SUCCESS;
        }
        try {
            this.notifications.consumeEvent(eventId);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException error) {
            log.error("商品通知事件 {} 消费失败，稍后重试", eventId, error);
            return ConsumeResult.FAILURE;
        }
    }
}
