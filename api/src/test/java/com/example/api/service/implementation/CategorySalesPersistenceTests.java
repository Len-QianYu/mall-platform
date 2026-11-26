package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.dto.CategorySalesView;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.OrderService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

// 使用随机商品分类隔离开发库数据；全部写入随测试事务回滚，不消费消息或执行订单任务。
@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false",
    "seckill.expiry-enabled=false" })
@Transactional
class CategorySalesPersistenceTests {
    @Autowired private OrderService service;
    @Autowired private ProductMapper products;
    @Autowired private OrderMapper orders;
    @Autowired private OrderItemMapper items;
    @MockitoBean private Producer rocketMQProducer;
    @MockitoBean private PushConsumer orderPayTimeoutConsumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private RedissonClient redisson;

    @Test
    void sumsActualItemQuantitiesByPaymentDayAndIncludesOnlyPaidShippedAndCompletedOrders() {
        LocalDate start = LocalDate.of(2026, 11, 11);
        LocalDateTime paidAt = start.plusDays(2).atTime(13, 0);
        String firstCategory = this.categoryName();
        String secondCategory = this.categoryName();
        String unsoldCategory = this.categoryName();
        ProductEntity first = this.product(firstCategory, 1);
        ProductEntity offSale = this.product(firstCategory, 0);
        ProductEntity second = this.product(secondCategory, 1);
        this.product(unsoldCategory, 0);

        OrderEntity paid = this.order(OrderStatus.PAID, paidAt);
        this.item(paid, first, 2);
        this.item(paid, offSale, 3);
        this.item(paid, second, 4);
        this.item(this.order(OrderStatus.SHIPPED, paidAt), first, 5);
        this.item(this.order(OrderStatus.COMPLETED, paidAt), first, 7);
        for (OrderStatus excluded : List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, OrderStatus.REFUNDED)) {
            this.item(this.order(excluded, paidAt), first, 100);
        }

        List<CategorySalesView> sales = this.service.fetchCategorySales(start, start.plusDays(7));
        CategorySalesView firstSales = this.find(sales, firstCategory);
        assertEquals(17L, firstSales.soldQuantity());
        assertEquals(17L, firstSales.trend().get(2).soldQuantity());
        assertEquals(0L, firstSales.trend().getFirst().soldQuantity());
        assertEquals(4L, this.find(sales, secondCategory).soldQuantity());
        assertEquals(0L, this.find(sales, unsoldCategory).soldQuantity());
        assertEquals(7, this.find(sales, unsoldCategory).trend().size());
    }

    @ParameterizedTest
    @ValueSource(ints = { 7, 30 })
    void usesAnInclusiveStartAndExclusiveEndAndExcludesOrdersWithoutAPaymentTime(int days) {
        LocalDate start = LocalDate.of(2026, 11, 11);
        LocalDate end = start.plusDays(days);
        String category = this.categoryName();
        ProductEntity product = this.product(category, 1);
        this.item(this.order(OrderStatus.PAID, start.atStartOfDay().minusSeconds(1)), product, 100);
        this.item(this.order(OrderStatus.PAID, start.atStartOfDay()), product, 2);
        this.item(this.order(OrderStatus.COMPLETED, end.atStartOfDay().minusSeconds(1)), product, 3);
        this.item(this.order(OrderStatus.PAID, end.atStartOfDay()), product, 100);
        this.item(this.order(OrderStatus.PAID, null), product, 100);

        CategorySalesView sales = this.find(this.service.fetchCategorySales(start, end), category);

        assertEquals(5L, sales.soldQuantity());
        assertEquals(days, sales.trend().size());
        assertEquals(start, sales.trend().getFirst().date());
        assertEquals(end.minusDays(1), sales.trend().getLast().date());
        assertEquals(2L, sales.trend().getFirst().soldQuantity());
        assertEquals(3L, sales.trend().getLast().soldQuantity());
    }

    @Test
    void removesRefundedUnitsFromTheOriginalPaymentDayButKeepsTheCategory() {
        LocalDate start = LocalDate.of(2026, 11, 11);
        String category = this.categoryName();
        ProductEntity product = this.product(category, 1);
        OrderEntity order = this.order(OrderStatus.PAID, start.plusDays(1).atTime(10, 0));
        this.item(order, product, 6);
        assertEquals(6L, this.find(this.service.fetchCategorySales(start, start.plusDays(7)), category).soldQuantity());

        order.setStatus(OrderStatus.REFUNDED);
        assertEquals(1, this.orders.updateById(order));

        CategorySalesView refunded = this.find(this.service.fetchCategorySales(start, start.plusDays(7)), category);
        assertEquals(0L, refunded.soldQuantity());
        assertEquals(0L, refunded.trend().get(1).soldQuantity());
        assertEquals(7, refunded.trend().size());
    }

    private CategorySalesView find(List<CategorySalesView> sales, String category) {
        return sales.stream().filter(item -> category.equals(item.category())).findFirst().orElseThrow();
    }

    private String categoryName() {
        return "sales-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ProductEntity product(String category, int status) {
        ProductEntity product = new ProductEntity();
        product.setName("category-sales-" + UUID.randomUUID());
        product.setCategory(category);
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(10);
        product.setSales(12345);
        product.setStatus(status);
        this.products.insert(product);
        return product;
    }

    private OrderEntity order(OrderStatus status, LocalDateTime paidAt) {
        OrderEntity order = new OrderEntity();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(7);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setReceiverName("销量测试");
        order.setReceiverPhone("13900139000");
        order.setReceiverAddress("测试收货地址");
        order.setPaidAt(paidAt);
        // 创建时间在统计区间之外，确保销量以支付时间归属。
        order.setCreatedAt(LocalDate.of(2026, 9, 1).atStartOfDay());
        this.orders.insert(order);
        return order;
    }

    private void item(OrderEntity order, ProductEntity product, int quantity) {
        OrderItemEntity item = new OrderItemEntity();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(quantity);
        this.items.insert(item);
    }
}
