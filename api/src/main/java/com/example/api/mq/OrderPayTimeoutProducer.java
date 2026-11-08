package com.example.api.mq;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.api.configuration.RocketMQConfiguration;
import com.example.api.entity.OrderEntity;

// 发送订单支付超时的延时消息：到期后 OrderPayTimeoutListener 收到，取消仍未支付的订单
@Component
public class OrderPayTimeoutProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderPayTimeoutProducer.class);

    private final Producer producer;

    private final Duration payTimeout;

    public OrderPayTimeoutProducer(Producer producer, @Value("${order.pay-timeout}") Duration payTimeout) {
        this.producer = producer;
        this.payTimeout = payTimeout;
    }

    // 在下单事务里调用，等事务提交后才发：事务回滚了不会留下一条指向不存在订单的消息，
    // 消费者收到消息时订单也一定已经能查到
    public void sendAfterCommit(OrderEntity order) {
        Long orderId = order.getId();
        String orderNo = order.getOrderNo();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(orderId, orderNo);
            }
        });
    }

    // 异步发送，不拖慢下单接口。发送失败只记日志、不影响下单结果（订单已经提交了），
    // 这类订单由 OrderPayTimeoutScanTask 定时扫描兜底取消
    private void send(Long orderId, String orderNo) {
        try {
            Message message = ClientServiceProvider.loadService().newMessageBuilder()
                .setTopic(RocketMQConfiguration.ORDER_PAY_TIMEOUT_TOPIC)
                // 消息 Key 用订单号，排查时可按订单号查消息
                .setKeys(orderNo)
                .setBody(String.valueOf(orderId).getBytes(StandardCharsets.UTF_8))
                .setDeliveryTimestamp(System.currentTimeMillis() + this.payTimeout.toMillis())
                .build();
            this.producer.sendAsync(message).whenComplete((receipt, error) -> {
                if (error != null) {
                    log.warn("订单 {} 的支付超时消息发送失败，将由定时扫描兜底取消", orderNo, error);
                }
            });
        } catch (RuntimeException error) {
            log.warn("订单 {} 的支付超时消息发送失败，将由定时扫描兜底取消", orderNo, error);
        }
    }

}
