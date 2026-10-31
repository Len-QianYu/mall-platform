package com.example.api.service.implementation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.service.OrderService;
import com.example.api.utils.Pagination;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    public OrderServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    @Override
    public Integer count(String keyword, OrderStatus status) {
        return this.orderMapper.selectCount(this.filter(keyword, status)).intValue();
    }

    @Override
    public List<OrderEntity> fetch(String keyword, OrderStatus status, Pagination pagination) {
        // 总数已由 count 查过，分页插件只负责拼 LIMIT（searchCount = false，不重复计数）
        return this.orderMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(keyword, status).orderByDesc(OrderEntity::getId)
        );
    }

    @Override
    public List<OrderEntity> fetchRecent(Integer limit) {
        return this.orderMapper.selectList(
            new Page<>(1, limit, false),
            Wrappers.<OrderEntity>lambdaQuery().orderByDesc(OrderEntity::getId)
        );
    }

    @Override
    public Optional<OrderEntity> fetchById(Long id) {
        return Optional.ofNullable(this.orderMapper.selectById(id));
    }

    @Override
    public List<OrderItemEntity> fetchItems(Long orderId) {
        return this.orderItemMapper.selectList(
            Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderId, orderId)
                .orderByAsc(OrderItemEntity::getId)
        );
    }

    @Override
    public BigDecimal sumSalesAmount() {
        return this.orderMapper.sumSalesAmount();
    }

    // 发货和取消都用条件更新：WHERE 里带上当前应有的状态，判断和修改在同一条 SQL 里完成，
    // 两个管理员同时操作、或取消时买家恰好付了款，都只有一方能改成功，不会出现状态被覆盖

    @Override
    public boolean ship(Long id) {
        return this.orderMapper.update(
            Wrappers.<OrderEntity>lambdaUpdate()
                .set(OrderEntity::getStatus, OrderStatus.SHIPPED)
                .set(OrderEntity::getShippedAt, LocalDateTime.now())
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getStatus, OrderStatus.PAID)
        ) > 0;
    }

    @Override
    public boolean cancel(Long id) {
        // 只改订单状态，不回补库存：下单流程还没实现，减库存的时机定下来后再补上对应的回补
        return this.orderMapper.update(
            Wrappers.<OrderEntity>lambdaUpdate()
                .set(OrderEntity::getStatus, OrderStatus.CANCELLED)
                .set(OrderEntity::getCancelledAt, LocalDateTime.now())
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getStatus, OrderStatus.PENDING_PAYMENT)
        ) > 0;
    }

    // 列表筛选条件：count 和 fetch 共用，保证总数和当前页按同一条件查询
    private LambdaQueryWrapper<OrderEntity> filter(String keyword, OrderStatus status) {
        return Wrappers.<OrderEntity>lambdaQuery()
            .and(StringUtils.hasText(keyword), wrapper -> wrapper
                .like(OrderEntity::getOrderNo, keyword)
                .or().like(OrderEntity::getReceiverName, keyword)
                .or().like(OrderEntity::getReceiverPhone, keyword)
            )
            .eq(status != null, OrderEntity::getStatus, status);
    }

}
