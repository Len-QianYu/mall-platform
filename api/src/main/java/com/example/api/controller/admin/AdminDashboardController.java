package com.example.api.controller.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.service.OrderService;
import com.example.api.enums.OrderStatus;
import com.example.api.service.ProductService;
import com.example.api.service.UserService;
import com.example.api.utils.ResultTemplate;

// 管理端仪表盘（/admin/** 只允许管理员访问，见 SecurityConfiguration）
@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final UserService userService;

    private final ProductService productService;

    private final OrderService orderService;

    public AdminDashboardController(
        UserService userService,
        ProductService productService,
        OrderService orderService
    ) {
        this.userService    = userService;
        this.productService = productService;
        this.orderService   = orderService;
    }

    @GetMapping
    public ResultTemplate index(@RequestParam(defaultValue = "30") int days) {
        ResultTemplate result = new ResultTemplate();
        if (days < 1 || days > 90) {
            result.setStatus(false);
            return result.putPayload("message", "统计天数需在 1–90 天之间");
        }

        // 统计卡片：不带筛选条件的 count 就是总数，和各列表页不筛选时的总条数一致
        Map<OrderStatus, BigDecimal> salesAmountsByStatus = this.orderService.fetchSalesAmountsByStatus();
        result.putPayload("stats", Map.of(
            "userCount", this.userService.count(null, null, null),
            "userRoleCounts", Map.of(
                "admin", this.userService.count(null, "admin", null),
                "user", this.userService.count(null, "user", null),
                "super_admin", this.userService.count(null, "super_admin", null)
            ),
            "productCount", this.productService.count(null, null, null),
            "productStatusCounts", Map.of(
                "onSale", this.productService.count(null, null, 1),
                "offSale", this.productService.count(null, null, 0)
            ),
            "orderCount", this.orderService.count(null, null),
            "salesAmount", salesAmountsByStatus.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
            "salesAmountsByStatus", salesAmountsByStatus
        ));
        result.putPayload("recentOrders", this.orderService.fetchRecent(5));
        LocalDate today = LocalDate.now();
        result.putPayload("trend", this.orderService.fetchTrend(today.minusDays(days - 1), today.plusDays(1)));
        result.putPayload("categorySales", this.orderService.fetchCategorySales(today.minusDays(days - 1), today.plusDays(1)));
        result.putPayload("orderStatuses", this.orderService.fetchStatusCounts());
        return result;
    }

}
