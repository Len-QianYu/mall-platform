package com.example.api.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.api.controller.admin.AdminDashboardController;
import com.example.api.dto.DashboardTrendPoint;
import com.example.api.dto.OrderStatusCount;
import com.example.api.enums.OrderStatus;
import com.example.api.service.OrderService;
import com.example.api.service.ProductService;
import com.example.api.service.UserService;

class AdminDashboardTests {

    private UserService users;
    private ProductService products;
    private OrderService orders;
    private AdminDashboardController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.users = mock(UserService.class);
        this.products = mock(ProductService.class);
        this.orders = mock(OrderService.class);
        this.controller = new AdminDashboardController(this.users, this.products, this.orders);
        this.mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
        when(this.users.count(null, null, null)).thenReturn(2);
        when(this.products.count(null, null, null)).thenReturn(3);
        when(this.orders.count(null, null)).thenReturn(4);
        when(this.orders.sumSalesAmount()).thenReturn(new BigDecimal("1234.56"));
        when(this.orders.fetchRecent(5)).thenReturn(List.of());
        when(this.orders.fetchTrend(any(), any())).thenReturn(List.of(
            new DashboardTrendPoint(LocalDate.of(2026, 11, 17), 1L, new BigDecimal("12.34"))
        ));
        when(this.orders.fetchStatusCounts()).thenReturn(List.of(new OrderStatusCount(OrderStatus.PAID, 4L)));
    }

    @Test
    void defaultsToThirtyDaysAndSerializesChartDatesCountsAndAmounts() throws Exception {
        LocalDate today = LocalDate.now();
        this.mvc.perform(get("/admin/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.stats.salesAmount").value(1234.56))
            .andExpect(jsonPath("$.payload.trend[0].date").value("2026-11-17"))
            .andExpect(jsonPath("$.payload.trend[0].salesAmount").value(12.34))
            .andExpect(jsonPath("$.payload.orderStatuses[0].status").value("PAID"));

        verify(this.orders).fetchTrend(today.minusDays(29), today.plusDays(1));
    }

    @Test
    void acceptsSevenDaysIncludingToday() throws Exception {
        LocalDate today = LocalDate.now();
        this.mvc.perform(get("/admin/dashboard").param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));

        verify(this.orders).fetchTrend(today.minusDays(6), today.plusDays(1));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0, 91, Integer.MAX_VALUE })
    void rejectsUnboundedRangesBeforeQueryingAnyData(int days) {
        assertFalse(this.controller.index(days).isStatus());
        verifyNoInteractions(this.users, this.products, this.orders);
    }
}
