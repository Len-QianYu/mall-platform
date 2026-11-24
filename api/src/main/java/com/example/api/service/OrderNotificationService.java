package com.example.api.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.mapper.OrderNotificationMapper;
import com.example.api.utils.Pagination;
import com.example.api.websocket.ProductNotificationWebSocketHandler;

@Service
public class OrderNotificationService {
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);
    private final OrderNotificationMapper notifications;
    private final ProductNotificationWebSocketHandler sockets;

    public OrderNotificationService(OrderNotificationMapper notifications, ProductNotificationWebSocketHandler sockets) {
        this.notifications = notifications;
        this.sockets = sockets;
    }

    // 与订单、明细和库存一起提交；幂等重试返回旧订单时不会调用此方法。
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreatedOrder(Long orderId) {
        this.notifications.insertForOrder(orderId);
        List<OrderNotificationEntity> created = this.notifications.selectList(
            Wrappers.<OrderNotificationEntity>lambdaQuery()
                .eq(OrderNotificationEntity::getOrderId, orderId).orderByAsc(OrderNotificationEntity::getId));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (OrderNotificationEntity notification : created) {
                    try { sockets.sendOrderNotification(notification); }
                    catch (RuntimeException error) {
                        // 通知已落库，推送失败不能把已提交的订单报成失败。
                        log.warn("新订单通知 {} 已保存，但实时推送失败", notification.getId(), error);
                    }
                }
            }
        });
    }

    public int count(Integer userId, boolean unreadOnly) {
        return this.notifications.selectCount(this.filter(userId, unreadOnly)).intValue();
    }

    public List<OrderNotificationEntity> fetch(Integer userId, boolean unreadOnly, Pagination pagination) {
        return this.notifications.selectList(new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(userId, unreadOnly).orderByDesc(OrderNotificationEntity::getId));
    }

    @Transactional
    public boolean markRead(Integer userId, Long id) {
        boolean found = this.notifications.markRead(userId, id) > 0;
        if (found) this.refreshAfterCommit(userId);
        return found;
    }

    @Transactional
    public void markAllRead(Integer userId) {
        this.notifications.markAllRead(userId);
        this.refreshAfterCommit(userId);
    }

    private LambdaQueryWrapper<OrderNotificationEntity> filter(Integer userId, boolean unreadOnly) {
        return Wrappers.<OrderNotificationEntity>lambdaQuery()
            .eq(OrderNotificationEntity::getUserId, userId)
            .isNull(unreadOnly, OrderNotificationEntity::getReadAt);
    }

    private void refreshAfterCommit(Integer userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { sockets.sendOrderRefresh(userId); }
        });
    }
}
