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
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.api.controller.admin.AdminDashboardController;
import com.example.api.dto.CategorySalesView;
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
        when(this.users.count(null, null, null)).thenReturn(8);
        when(this.users.count(null, "admin", null)).thenReturn(2);
        when(this.users.count(null, "user", null)).thenReturn(5);
        when(this.users.count(null, "super_admin", null)).thenReturn(1);
        when(this.products.count(null, null, null)).thenReturn(3);
        when(this.products.count(null, null, 1)).thenReturn(2);
        when(this.products.count(null, null, 0)).thenReturn(1);
        when(this.orders.count(null, null)).thenReturn(4);
        when(this.orders.fetchSalesAmountsByStatus()).thenReturn(Map.of(
            OrderStatus.PAID, new BigDecimal("200.10"),
            OrderStatus.SHIPPED, new BigDecimal("300.20"),
            OrderStatus.COMPLETED, new BigDecimal("734.26")
        ));
        when(this.orders.fetchRecent(5)).thenReturn(List.of());
        when(this.orders.fetchTrend(any(), any())).thenReturn(List.of(
            new DashboardTrendPoint(LocalDate.of(2026, 11, 17), 1L, new BigDecimal("12.34"))
        ));
        when(this.orders.fetchStatusCounts()).thenReturn(List.of(new OrderStatusCount(OrderStatus.PAID, 4L)));
        when(this.orders.fetchCategorySales(any(), any())).thenReturn(List.of(
            new CategorySalesView("claude玩偶", 6L,
                List.of(new CategorySalesView.DailySales(LocalDate.of(2026, 11, 17), 6L)))
        ));
    }

    @Test
    void defaultsToThirtyDaysAndSerializesChartDatesCountsAndAmounts() throws Exception {
        LocalDate today = LocalDate.now();
        this.mvc.perform(get("/admin/dashboard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.stats.userCount").value(8))
            .andExpect(jsonPath("$.payload.stats.userRoleCounts.admin").value(2))
            .andExpect(jsonPath("$.payload.stats.userRoleCounts.user").value(5))
            .andExpect(jsonPath("$.payload.stats.userRoleCounts.super_admin").value(1))
            .andExpect(jsonPath("$.payload.stats.productCount").value(3))
            .andExpect(jsonPath("$.payload.stats.productStatusCounts.onSale").value(2))
            .andExpect(jsonPath("$.payload.stats.productStatusCounts.offSale").value(1))
            .andExpect(jsonPath("$.payload.stats.salesAmount").value(1234.56))
            .andExpect(jsonPath("$.payload.stats.salesAmountsByStatus.PAID").value(200.10))
            .andExpect(jsonPath("$.payload.stats.salesAmountsByStatus.SHIPPED").value(300.20))
            .andExpect(jsonPath("$.payload.stats.salesAmountsByStatus.COMPLETED").value(734.26))
            .andExpect(jsonPath("$.payload.trend[0].date").value("2026-11-17"))
            .andExpect(jsonPath("$.payload.trend[0].salesAmount").value(12.34))
            .andExpect(jsonPath("$.payload.orderStatuses[0].status").value("PAID"))
            .andExpect(jsonPath("$.payload.categorySales[0].category").value("claude玩偶"))
            .andExpect(jsonPath("$.payload.categorySales[0].soldQuantity").value(6))
            .andExpect(jsonPath("$.payload.categorySales[0].trend[0].date").value("2026-11-17"))
            .andExpect(jsonPath("$.payload.categorySales[0].trend[0].soldQuantity").value(6));

        verify(this.orders).fetchTrend(today.minusDays(29), today.plusDays(1));
        verify(this.orders).fetchCategorySales(today.minusDays(29), today.plusDays(1));
        verify(this.orders).fetchSalesAmountsByStatus();
    }

    @Test
    void acceptsSevenDaysIncludingToday() throws Exception {
        LocalDate today = LocalDate.now();
        this.mvc.perform(get("/admin/dashboard").param("days", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));

        verify(this.orders).fetchTrend(today.minusDays(6), today.plusDays(1));
        verify(this.orders).fetchCategorySales(today.minusDays(6), today.plusDays(1));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0, 91, Integer.MAX_VALUE })
    void rejectsUnboundedRangesBeforeQueryingAnyData(int days) {
        assertFalse(this.controller.index(days).isStatus());
        verifyNoInteractions(this.users, this.products, this.orders);
    }
}
