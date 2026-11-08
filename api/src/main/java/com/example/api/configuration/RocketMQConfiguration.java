package com.example.api.configuration;

import java.util.Map;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.api.mq.OrderPayTimeoutListener;

// RocketMQ 客户端：走 gRPC 连 Proxy（docker-compose 里的 127.0.0.1:8081）。
// 启动时就连上 Proxy，RocketMQ 没开的话后端会直接启动失败（和 MySQL 一样）。
// 客户端不会自动建 Topic 和消费组，首次使用前先建好，命令见 docker-compose.yml 顶部注释
@Configuration
public class RocketMQConfiguration {

    // 订单支付超时：下单时发一条延时消息，到期后消费者取消仍未支付的订单。Topic 的消息类型必须是 DELAY
    public static final String ORDER_PAY_TIMEOUT_TOPIC = "order-pay-timeout";

    public static final String ORDER_PAY_TIMEOUT_CONSUMER_GROUP = "order-pay-timeout-consumer";

    @Bean
    public ClientConfiguration rocketMQClientConfiguration(@Value("${rocketmq.endpoints}") String endpoints) {
        return ClientConfiguration.newBuilder().setEndpoints(endpoints).build();
    }

    // 生产者是线程安全的，全应用共用一个；setTopics 让启动时就预取路由，Topic 不存在时启动即报错
    @Bean(destroyMethod = "close")
    public Producer rocketMQProducer(ClientConfiguration configuration) throws ClientException {
        return ClientServiceProvider.loadService().newProducerBuilder()
            .setClientConfiguration(configuration)
            .setTopics(ORDER_PAY_TIMEOUT_TOPIC)
            .build();
    }

    // build() 之后就开始拉消息、交给监听器处理；监听器返回 FAILURE 或抛异常时，消息稍后重投（消费组默认最多重试 16 次）
    @Bean(destroyMethod = "close")
    public PushConsumer orderPayTimeoutConsumer(
        ClientConfiguration configuration,
        OrderPayTimeoutListener listener
    ) throws ClientException {
        return ClientServiceProvider.loadService().newPushConsumerBuilder()
            .setClientConfiguration(configuration)
            .setConsumerGroup(ORDER_PAY_TIMEOUT_CONSUMER_GROUP)
            .setSubscriptionExpressions(Map.of(ORDER_PAY_TIMEOUT_TOPIC, FilterExpression.SUB_ALL))
            .setMessageListener(listener)
            .build();
    }

}
