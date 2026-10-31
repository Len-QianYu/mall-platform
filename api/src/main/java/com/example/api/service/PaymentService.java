package com.example.api.service;

import java.util.List;

import com.example.api.entity.PaymentEntity;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.utils.Pagination;

public interface PaymentService {

    // 按条件统计支付流水数（条件为 null 时不筛选）
    Integer count(String keyword, PaymentMethod method, PaymentStatus status);

    // 按条件查询一页支付流水，新的在前
    List<PaymentEntity> fetch(String keyword, PaymentMethod method, PaymentStatus status, Pagination pagination);

}
