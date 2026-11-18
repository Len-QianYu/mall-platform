package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.api.dto.DashboardTrendPoint;
import com.example.api.dto.OrderStatusCount;
import com.example.api.enums.OrderStatus;
import com.example.api.mapper.CartMapper;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.ProductService;

class OrderDashboardTests {

    private OrderMapper mapper;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        this.mapper = mock(OrderMapper.class);
        this.service = new OrderServiceImpl(
            this.mapper, mock(OrderItemMapper.class), mock(ProductMapper.class),
            mock(ProductService.class), mock(CartMapper.class), mock(OrderPayTimeoutProducer.class)
        );
        when(this.mapper.fetchDailyOrders(any(), any())).thenReturn(List.of());
        when(this.mapper.fetchDailySales(any(), any())).thenReturn(List.of());
        when(this.mapper.fetchStatusCounts()).thenReturn(List.of());
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
}
