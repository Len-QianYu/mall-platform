package com.example.api.controller.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.entity.PaymentEntity;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.service.PaymentService;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;

// 管理端支付流水（/admin/** 只允许管理员访问，见 SecurityConfiguration）。
// 流水只读：由支付流程写入，管理端只查不改
@RestController
@RequestMapping("/admin/payment")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // 流水列表：关键字匹配流水号，可按支付方式、状态筛选
    @GetMapping
    public ResultTemplate index(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) PaymentMethod method,
        @RequestParam(required = false) PaymentStatus status
    ) {
        ResultTemplate result = new ResultTemplate();

        // 先计数再分页：页码超出总页数时 Pagination 会收回到最后一页
        Integer total = this.paymentService.count(keyword, method, status);
        Pagination pagination = Pagination.paginate(total, pageSize, page);
        List<PaymentEntity> payments = this.paymentService.fetch(keyword, method, status, pagination);

        result.putPayload("payments", payments);
        result.putPayload("pagination", pagination);
        return result;
    }

}
