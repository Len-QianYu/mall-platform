package com.example.api.mq;

import java.nio.charset.StandardCharsets;

import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.api.service.OrderService;

// 消费订单支付超时消息：到期时订单还是待支付就取消并回补库存。
// cancel 是条件更新，订单已支付、已被取消，或消息重复投递时都只是不生效，所以不用先查状态，重复消费也安全
@Component
public class OrderPayTimeoutListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPayTimeoutListener.class);

    private final OrderService orderService;

    public OrderPayTimeoutListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public ConsumeResult consume(MessageView message) {
        String body = StandardCharsets.UTF_8.decode(message.getBody()).toString();
        long orderId;
        try {
            orderId = Long.parseLong(body);
        } catch (NumberFormatException error) {
            // 消息体不对，重投也一样解析不了，直接确认掉
            log.warn("支付超时消息 {} 的消息体不是订单 ID：{}", message.getMessageId(), body);
            return ConsumeResult.SUCCESS;
        }

        try {
            if (this.orderService.cancel(orderId)) {
                log.info("订单 {} 超时未支付，已自动取消", orderId);
            }
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException error) {
            // 数据库暂时不可用等：返回 FAILURE 让消息稍后重投
            log.error("订单 {} 超时取消失败，稍后重试", orderId, error);
            return ConsumeResult.FAILURE;
        }
    }

}
