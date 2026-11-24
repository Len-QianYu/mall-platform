package com.example.api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.api.dto.UserNotificationView;
import com.example.api.mapper.OrderShipmentNotificationMapper;
import com.example.api.mapper.UserNotificationMapper;
import com.example.api.utils.Pagination;
import com.example.api.websocket.ProductNotificationWebSocketHandler;

@Service
public class UserNotificationService {
    private final UserNotificationMapper notifications;
    private final ProductNotificationService products;
    private final OrderShipmentNotificationMapper shipments;
    private final ProductNotificationWebSocketHandler sockets;

    public UserNotificationService(UserNotificationMapper notifications, ProductNotificationService products,
        OrderShipmentNotificationMapper shipments, ProductNotificationWebSocketHandler sockets) {
        this.notifications = notifications;
        this.products = products;
        this.shipments = shipments;
        this.sockets = sockets;
    }

    public int count(Integer userId, boolean unreadOnly) {
        return this.notifications.count(userId, unreadOnly);
    }

    public List<UserNotificationView> fetch(Integer userId, boolean unreadOnly, Pagination pagination) {
        return this.notifications.fetch(userId, unreadOnly, pagination.getLimit(), pagination.getOffset());
    }

    @Transactional
    public boolean markRead(Integer userId, Long id, String type) {
        // 老客户端未传类型时仍按商品通知处理；发货通知必须指定类型，避免两张表 ID 相同误标已读。
        if (!"ORDER_SHIPPED".equals(type)) return this.products.markRead(userId, id);
        boolean found = this.shipments.markRead(userId, id) > 0;
        if (found) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { sockets.sendRefresh(userId); }
        });
        return found;
    }

    @Transactional
    public void markAllRead(Integer userId) {
        this.products.markAllRead(userId);
        this.shipments.markAllRead(userId);
    }
}
