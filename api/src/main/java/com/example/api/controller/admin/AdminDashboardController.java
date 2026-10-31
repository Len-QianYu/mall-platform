package com.example.api.controller.admin;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.service.OrderService;
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
    public ResultTemplate index() {
        ResultTemplate result = new ResultTemplate();

        // 统计卡片：不带筛选条件的 count 就是总数，和各列表页不筛选时的总条数一致
        result.putPayload("stats", Map.of(
            "userCount", this.userService.count(null, null, null),
            "productCount", this.productService.count(null, null, null),
            "orderCount", this.orderService.count(null, null),
            "salesAmount", this.orderService.sumSalesAmount()
        ));
        result.putPayload("recentOrders", this.orderService.fetchRecent(5));
        return result;
    }

}
