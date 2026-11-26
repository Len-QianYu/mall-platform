package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.api.dto.CategorySalesPoint;
import com.example.api.dto.CategorySalesView;
import com.example.api.dto.DashboardTrendPoint;
import com.example.api.dto.OrderStatusCount;
import com.example.api.dto.OrderStatusSalesAmount;
import com.example.api.enums.OrderStatus;
import com.example.api.mapper.CartMapper;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.ProductService;
import com.example.api.service.SeckillService;
import com.example.api.service.PaymentService;
import com.example.api.service.OrderNotificationService;

class OrderDashboardTests {

    private OrderMapper mapper;
    private OrderItemMapper items;
    private ProductMapper products;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        this.mapper = mock(OrderMapper.class);
        this.items = mock(OrderItemMapper.class);
        this.products = mock(ProductMapper.class);
        this.service = new OrderServiceImpl(
            this.mapper, this.items, this.products,
            mock(ProductService.class), mock(SeckillService.class), mock(CartMapper.class), mock(OrderPayTimeoutProducer.class),
            mock(PaymentService.class), mock(OrderNotificationService.class), mock(RedissonClient.class),
            mock(PlatformTransactionManager.class), Duration.ofSeconds(3)
        );
        when(this.mapper.fetchDailyOrders(any(), any())).thenReturn(List.of());
        when(this.mapper.fetchDailySales(any(), any())).thenReturn(List.of());
        when(this.mapper.fetchStatusCounts()).thenReturn(List.of());
        when(this.mapper.fetchSalesAmountsByStatus()).thenReturn(List.of());
        when(this.products.selectCategories()).thenReturn(List.of());
        when(this.items.fetchDailyCategorySales(any(), any())).thenReturn(List.of());
    }

    @Test
    void fillsEveryDayIncludingLeapDayAndUsesAnExclusiveEndBoundary() {
        LocalDate start = LocalDate.of(2024, 2, 27);
        LocalDate end = LocalDate.of(2024, 3, 5);

        List<DashboardTrendPoint> trend = this.service.fetchTrend(start, end);

        assertEquals(7, trend.size());
        assertEquals(LocalDate.of(2024, 2, 29), trend.get(2).date());
        assertEquals(end.minusDays(1), trend.getLast().date());
        for (DashboardTrendPoint point : trend) {
            assertEquals(0L, point.orderCount());
            assertEquals(0, point.salesAmount().compareTo(BigDecimal.ZERO));
        }
        verify(this.mapper).fetchDailyOrders(start.atStartOfDay(), end.atStartOfDay());
        verify(this.mapper).fetchDailySales(start.atStartOfDay(), end.atStartOfDay());
    }

    @Test
    void keepsPaymentAmountsOnTheirPaymentDayAndOrderCountsOnTheirCreationDay() {
        LocalDate start = LocalDate.of(2026, 11, 11);
        LocalDate paidDate = start.plusDays(2);
        when(this.mapper.fetchDailyOrders(any(), any())).thenReturn(List.of(
            new DashboardTrendPoint(start, 3L, BigDecimal.ZERO)
        ));
        when(this.mapper.fetchDailySales(any(), any())).thenReturn(List.of(
            new DashboardTrendPoint(paidDate, 0L, new BigDecimal("1234.56"))
        ));

        List<DashboardTrendPoint> trend = this.service.fetchTrend(start, start.plusDays(7));

        assertEquals(3L, trend.getFirst().orderCount());
        assertEquals(0, trend.getFirst().salesAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0L, trend.get(2).orderCount());
        assertEquals(new BigDecimal("1234.56"), trend.get(2).salesAmount());
        assertEquals(0, trend.get(1).salesAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void fillsDailyCategorySalesWithZeroAndKeepsCategoriesWithoutAnySales() {
        LocalDate start = LocalDate.of(2026, 11, 11);
        when(this.products.selectCategories()).thenReturn(List.of("claude玩偶", "gpt玩偶"));
        when(this.items.fetchDailyCategorySales(any(), any())).thenReturn(List.of(
            new CategorySalesPoint("gpt玩偶", start, 2L),
            new CategorySalesPoint("gpt玩偶", start.plusDays(6), 5L)
        ));

        List<CategorySalesView> categories = this.service.fetchCategorySales(start, start.plusDays(7));

        assertEquals(List.of("gpt玩偶", "claude玩偶"), categories.stream().map(CategorySalesView::category).toList());
        assertEquals(7L, categories.getFirst().soldQuantity());
        assertEquals(List.of(2L, 0L, 0L, 0L, 0L, 0L, 5L),
            categories.getFirst().trend().stream().map(CategorySalesView.DailySales::soldQuantity).toList());
        assertEquals(start.datesUntil(start.plusDays(7)).toList(),
            categories.getFirst().trend().stream().map(CategorySalesView.DailySales::date).toList());
        assertEquals(0L, categories.getLast().soldQuantity());
        assertEquals(7, categories.getLast().trend().size());
        verify(this.items).fetchDailyCategorySales(start.atStartOfDay(), start.plusDays(7).atStartOfDay());
    }

    @Test
    void keepsUnclassifiedSalesAndUsesLongTotalsAcrossThirtyDays() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        when(this.items.fetchDailyCategorySales(any(), any())).thenReturn(List.of(
            new CategorySalesPoint(null, start, Integer.MAX_VALUE),
            new CategorySalesPoint(null, start.plusDays(29), 10L)
        ));

        CategorySalesView category = this.service.fetchCategorySales(start, start.plusDays(30)).getFirst();

        assertNull(category.category());
        assertEquals((long) Integer.MAX_VALUE + 10L, category.soldQuantity());
        assertEquals(30, category.trend().size());
        assertEquals(10L, category.trend().getLast().soldQuantity());
        verify(this.items).fetchDailyCategorySales(start.atStartOfDay(), start.plusDays(30).atStartOfDay());
    }

    @Test
    void returnsNoCategoryRowsWhenThereAreNoProductsOrSales() {
        LocalDate start = LocalDate.of(2026, 11, 11);
        assertEquals(List.of(), this.service.fetchCategorySales(start, start.plusDays(7)));
    }

    @Test
    void includesRefundedOrdersInStatusDistributionAndFillsMissingStatusesWithZero() {
        when(this.mapper.fetchStatusCounts()).thenReturn(List.of(
            new OrderStatusCount(OrderStatus.REFUNDED, 1L),
            new OrderStatusCount(OrderStatus.PAID, 2L)
        ));

        List<OrderStatusCount> counts = this.service.fetchStatusCounts();

        assertEquals(Arrays.asList(OrderStatus.values()), counts.stream().map(OrderStatusCount::status).toList());
        assertEquals(3L, counts.stream().mapToLong(OrderStatusCount::count).sum());
        assertEquals(0L, counts.getFirst().count());
        assertEquals(new OrderStatusCount(OrderStatus.REFUNDED, 1L), counts.getLast());
    }

    @Test
    void fillsSalesAmountsWithZeroAndKeepsOnlyRevenueStatusesWithoutLosingCents() {
        when(this.mapper.fetchSalesAmountsByStatus()).thenReturn(List.of(
            new OrderStatusSalesAmount(OrderStatus.PAID, new BigDecimal("12.34")),
            new OrderStatusSalesAmount(OrderStatus.COMPLETED, new BigDecimal("0.02")),
            new OrderStatusSalesAmount(OrderStatus.REFUNDED, new BigDecimal("999.99"))
        ));

        Map<OrderStatus, BigDecimal> amounts = this.service.fetchSalesAmountsByStatus();

        assertEquals(Map.of(
            OrderStatus.PAID, new BigDecimal("12.34"),
            OrderStatus.SHIPPED, BigDecimal.ZERO,
            OrderStatus.COMPLETED, new BigDecimal("0.02")
        ), amounts);
        assertEquals(new BigDecimal("12.36"), amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(this.mapper).fetchSalesAmountsByStatus();
    }

    @Test
    void returnsAllThreeSalesStatusesWithZeroWhenThereAreNoPaidOrders() {
        assertEquals(Map.of(
            OrderStatus.PAID, BigDecimal.ZERO,
            OrderStatus.SHIPPED, BigDecimal.ZERO,
            OrderStatus.COMPLETED, BigDecimal.ZERO
        ), this.service.fetchSalesAmountsByStatus());
    }
}
