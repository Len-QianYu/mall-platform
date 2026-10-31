package com.example.api.service.implementation;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.entity.PaymentEntity;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.mapper.PaymentMapper;
import com.example.api.service.PaymentService;
import com.example.api.utils.Pagination;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;

    public PaymentServiceImpl(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
    }

    @Override
    public Integer count(String keyword, PaymentMethod method, PaymentStatus status) {
        return this.paymentMapper.selectCount(this.filter(keyword, method, status)).intValue();
    }

    @Override
    public List<PaymentEntity> fetch(String keyword, PaymentMethod method, PaymentStatus status, Pagination pagination) {
        // 总数已由 count 查过，分页插件只负责拼 LIMIT（searchCount = false，不重复计数）
        return this.paymentMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(keyword, method, status).orderByDesc(PaymentEntity::getId)
        );
    }

    // 列表筛选条件：count 和 fetch 共用，保证总数和当前页按同一条件查询
    private LambdaQueryWrapper<PaymentEntity> filter(String keyword, PaymentMethod method, PaymentStatus status) {
        return Wrappers.<PaymentEntity>lambdaQuery()
            .like(StringUtils.hasText(keyword), PaymentEntity::getPaymentNo, keyword)
            .eq(method != null, PaymentEntity::getMethod, method)
            .eq(status != null, PaymentEntity::getStatus, status);
    }

}
