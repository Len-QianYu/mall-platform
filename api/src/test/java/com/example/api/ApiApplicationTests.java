package com.example.api;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

// RustFS 未启动时也能加载应用；测试不消费真实订单消息或执行超时取消。
@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "seckill.expiry-enabled=false" })
class ApiApplicationTests {

	@MockitoBean
	private Producer rocketMQProducer;

	@MockitoBean
	private PushConsumer orderPayTimeoutConsumer;

	@MockitoBean
	private OrderPayTimeoutScanTask orderPayTimeoutScanTask;

	@MockitoBean
	private PaymentReconciliationTask paymentReconciliationTask;

	@Test
	void contextLoads() {
	}

}
