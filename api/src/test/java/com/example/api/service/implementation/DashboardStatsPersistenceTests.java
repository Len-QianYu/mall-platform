package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
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
import com.example.api.entity.ProductEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.OrderService;
import com.example.api.service.ProductService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false",
    "seckill.expiry-enabled=false" })
@Transactional
class DashboardStatsPersistenceTests {
    @Autowired private OrderService orders;
    @Autowired private ProductService products;
    @Autowired private OrderMapper orderMapper;
    @Autowired private ProductMapper productMapper;
    @MockitoBean private Producer rocketMQProducer;
    @MockitoBean private PushConsumer orderPayTimeoutConsumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private RedissonClient redisson;

    @Test
    void splitsSalesAmountsUsingTheSameStatusScopeAsTheTotal() {
        Map<OrderStatus, BigDecimal> before = this.orders.fetchSalesAmountsByStatus();
        BigDecimal totalBefore = this.orders.sumSalesAmount();
        this.order(OrderStatus.PAID, "10.11");
        this.order(OrderStatus.SHIPPED, "20.22");
        this.order(OrderStatus.COMPLETED, "30.33");
        this.order(OrderStatus.PENDING_PAYMENT, "9999.99");
        this.order(OrderStatus.CANCELLED, "9999.99");
        this.order(OrderStatus.REFUNDED, "9999.99");

        Map<OrderStatus, BigDecimal> after = this.orders.fetchSalesAmountsByStatus();
        assertEquals(3, after.size());
        assertEquals(0, after.get(OrderStatus.PAID).subtract(before.get(OrderStatus.PAID)).compareTo(new BigDecimal("10.11")));
        assertEquals(0, after.get(OrderStatus.SHIPPED).subtract(before.get(OrderStatus.SHIPPED)).compareTo(new BigDecimal("20.22")));
        assertEquals(0, after.get(OrderStatus.COMPLETED).subtract(before.get(OrderStatus.COMPLETED)).compareTo(new BigDecimal("30.33")));
        assertEquals(0, this.orders.sumSalesAmount().subtract(totalBefore).compareTo(new BigDecimal("60.66")));
        assertEquals(0, after.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(this.orders.sumSalesAmount()));
    }

    @Test
    void shippingMovesRevenueBetweenStatusesAndRefundingRemovesIt() {
        Map<OrderStatus, BigDecimal> before = this.orders.fetchSalesAmountsByStatus();
        BigDecimal totalBefore = this.orders.sumSalesAmount();
        OrderEntity order = this.order(OrderStatus.PAID, "123.45");
        order.setStatus(OrderStatus.SHIPPED);
        assertEquals(1, this.orderMapper.updateById(order));

        Map<OrderStatus, BigDecimal> shipped = this.orders.fetchSalesAmountsByStatus();
        assertEquals(0, shipped.get(OrderStatus.PAID).compareTo(before.get(OrderStatus.PAID)));
        assertEquals(0, shipped.get(OrderStatus.SHIPPED).subtract(before.get(OrderStatus.SHIPPED)).compareTo(new BigDecimal("123.45")));
        assertEquals(0, this.orders.sumSalesAmount().subtract(totalBefore).compareTo(new BigDecimal("123.45")));

        order.setStatus(OrderStatus.REFUNDED);
        assertEquals(1, this.orderMapper.updateById(order));
        assertEquals(before, this.orders.fetchSalesAmountsByStatus());
        assertEquals(0, this.orders.sumSalesAmount().compareTo(totalBefore));
    }

    @Test
    void productStatusCountsIncludeBothListedAndUnlistedProducts() {
        int totalBefore = this.products.count(null, null, null);
        int onSaleBefore = this.products.count(null, null, 1);
        int offSaleBefore = this.products.count(null, null, 0);
        this.product(1);
        this.product(0);

        assertEquals(totalBefore + 2, this.products.count(null, null, null));
        assertEquals(onSaleBefore + 1, this.products.count(null, null, 1));
        assertEquals(offSaleBefore + 1, this.products.count(null, null, 0));
    }

    private OrderEntity order(OrderStatus status, String amount) {
        OrderEntity order = new OrderEntity();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(7);
        order.setTotalAmount(new BigDecimal(amount));
        order.setStatus(status);
        order.setReceiverName("统计测试");
        order.setReceiverPhone("13900139000");
        order.setReceiverAddress("测试收货地址");
        order.setPaidAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        assertEquals(1, this.orderMapper.insert(order));
        return order;
    }

    private void product(int status) {
        ProductEntity product = new ProductEntity();
        product.setName("dashboard-stats-" + UUID.randomUUID());
        product.setCategory("统计测试");
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(1);
        product.setSales(0);
        product.setStatus(status);
        assertEquals(1, this.productMapper.insert(product));
    }
}
