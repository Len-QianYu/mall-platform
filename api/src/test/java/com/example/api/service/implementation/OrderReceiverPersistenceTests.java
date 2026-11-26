package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.entity.OrderEntity;
import com.example.api.entity.UserEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.UserMapper;
import com.example.api.service.OrderService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.Pagination;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false",
    "seckill.expiry-enabled=false" })
@Transactional
class OrderReceiverPersistenceTests {
    private static final LocalDateTime TEST_TIME = LocalDateTime.of(2026, 11, 25, 12, 0);
    @Autowired private OrderService service;
    @Autowired private OrderMapper orders;
    @Autowired private UserMapper users;
    @MockitoBean private Producer rocketMQProducer;
    @MockitoBean private PushConsumer orderPayTimeoutConsumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private RedissonClient redisson;

    @Test
    void showsEachBuyersUsernameOnHistoricalOrdersAndAllowsSearchingThatUsername() {
        UserEntity buyer = this.buyer();
        UserEntity other = this.buyer();
        OrderEntity order = this.order(buyer.getId());
        OrderEntity otherOrder = this.order(other.getId());

        assertEquals(buyer.getUsername(), this.service.fetchById(order.getId()).orElseThrow().getReceiverName());
        assertEquals(other.getUsername(), this.service.fetchById(otherOrder.getId()).orElseThrow().getReceiverName());
        assertEquals(buyer.getUsername(), this.service.fetchByIdForUser(order.getId(), buyer.getId())
            .orElseThrow().getReceiverName());
        assertTrue(this.service.fetchByIdForUser(order.getId(), other.getId()).isEmpty());
        assertEquals(1, this.service.count(buyer.getUsername(), null));
        assertEquals(buyer.getUsername(), this.service.fetch(buyer.getUsername(), null,
            Pagination.paginate(1, 10, 1)).getFirst().getReceiverName());
        assertEquals(buyer.getUsername(), this.service.fetchForUser(buyer.getId(), null,
            Pagination.paginate(1, 10, 1)).getFirst().getReceiverName());
        assertEquals(buyer.getUsername(), this.service.fetchRecent(5).stream()
            .filter(item -> item.getId().equals(order.getId())).findFirst().orElseThrow().getReceiverName());
    }

    private UserEntity buyer() {
        UserEntity user = new UserEntity();
        user.setUsername("recv" + UUID.randomUUID().toString().substring(0, 8));
        user.setName("客户一");
        user.setPassword("unused-test-password");
        user.setRole("user");
        user.setStatus(1);
        user.setRegisteredAt(TEST_TIME);
        user.setUpdatedAt(TEST_TIME);
        this.users.insert(user);
        return user;
    }

    private OrderEntity order(Integer userId) {
        OrderEntity order = new OrderEntity();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setReceiverName("客户一");
        order.setReceiverPhone("13900139000");
        order.setReceiverAddress("测试收货地址");
        order.setCreatedAt(TEST_TIME);
        order.setUpdatedAt(TEST_TIME);
        this.orders.insert(order);
        return order;
    }
}
