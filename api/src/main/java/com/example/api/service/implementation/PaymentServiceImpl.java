package com.example.api.service.implementation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.PaymentMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.PaymentService;
import com.example.api.utils.Pagination;
import com.example.api.utils.TradeNo;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final ProductMapper productMapper;

    public PaymentServiceImpl(
        PaymentMapper paymentMapper,
        OrderMapper orderMapper,
        OrderItemMapper orderItemMapper,
        ProductMapper productMapper
    ) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
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

    @Override
    @Transactional
    public PaymentEntity pay(OrderEntity order, PaymentMethod method) {
        LocalDateTime now = LocalDateTime.now();

        // 条件更新：WHERE 里带上待支付状态，重复点击支付、或支付时订单恰好被取消，都只有一方能改成功
        boolean paid = this.orderMapper.update(
            Wrappers.<OrderEntity>lambdaUpdate()
                .set(OrderEntity::getStatus, OrderStatus.PAID)
                .set(OrderEntity::getPaidAt, now)
                .eq(OrderEntity::getId, order.getId())
                .eq(OrderEntity::getStatus, OrderStatus.PENDING_PAYMENT)
        ) > 0;
        if (!paid) {
            throw new TradeFailedException("订单不是待支付状态，请刷新后重试");
        }

        // 模拟支付不经过第三方，流水直接记为成功；金额取订单总额（下单后不会再变）
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentNo(TradeNo.payment());
        payment.setOrderId(order.getId());
        payment.setUserId(order.getUserId());
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(now);
        this.paymentMapper.insert(payment);

        // 销量只算付了款的；按商品 ID 从小到大更新，和下单扣库存的加锁顺序一致
        List<OrderItemEntity> items = this.orderItemMapper.selectList(
            Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderId, order.getId())
                .orderByAsc(OrderItemEntity::getProductId)
        );
        for (OrderItemEntity item : items) {
            this.productMapper.addSales(item.getProductId(), item.getQuantity());
        }

        // 重新查一次：created_at 等列由数据库填写
        return this.paymentMapper.selectById(payment.getId());
    }

    // 列表筛选条件：count 和 fetch 共用，保证总数和当前页按同一条件查询
    private LambdaQueryWrapper<PaymentEntity> filter(String keyword, PaymentMethod method, PaymentStatus status) {
        return Wrappers.<PaymentEntity>lambdaQuery()
            .like(StringUtils.hasText(keyword), PaymentEntity::getPaymentNo, keyword)
            .eq(method != null, PaymentEntity::getMethod, method)
            .eq(status != null, PaymentEntity::getStatus, status);
    }

}
