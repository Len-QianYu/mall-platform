package com.example.api.service.implementation;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.entity.ProductNotificationEntity;
import com.example.api.entity.ProductNotificationEventEntity;
import com.example.api.mapper.ProductNotificationEventMapper;
import com.example.api.mapper.ProductNotificationMapper;
import com.example.api.service.ProductNotificationService;
import com.example.api.utils.Pagination;
import com.example.api.websocket.ProductNotificationWebSocketHandler;

@Service
public class ProductNotificationServiceImpl implements ProductNotificationService {
    private final ProductNotificationMapper notifications;
    private final ProductNotificationEventMapper events;
    private final ProductNotificationWebSocketHandler sockets;

    public ProductNotificationServiceImpl(ProductNotificationMapper notifications,
        ProductNotificationEventMapper events, ProductNotificationWebSocketHandler sockets) {
        this.notifications = notifications;
        this.events = events;
        this.sockets = sockets;
    }

    @Override
    public int count(Integer userId, boolean unreadOnly) {
        return this.notifications.selectCount(this.filter(userId, unreadOnly)).intValue();
    }

    @Override
    public List<ProductNotificationEntity> fetch(Integer userId, boolean unreadOnly, Pagination pagination) {
        return this.notifications.selectList(new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(userId, unreadOnly).orderByDesc(ProductNotificationEntity::getId));
    }

    @Override
    @Transactional
    public boolean markRead(Integer userId, Long id) {
        boolean found = this.notifications.markRead(userId, id) > 0;
        if (found) this.refreshAfterCommit(userId);
        return found;
    }

    @Override
    @Transactional
    public void markAllRead(Integer userId) {
        this.notifications.markAllRead(userId);
        this.refreshAfterCommit(userId);
    }

    @Override
    @Transactional
    public void consumeEvent(Long eventId) {
        ProductNotificationEventEntity event = this.events.selectForUpdate(eventId);
        if (event == null || event.getConsumedAt() != null) return;
        this.notifications.insertForEvent(eventId);
        List<ProductNotificationEntity> created = this.notifications.selectByEvent(eventId);
        this.events.markConsumed(eventId);
        // 在线推送必须在通知提交后，浏览器收到后才能立即查询；离线通知保留在数据库。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { created.forEach(sockets::sendNotification); }
        });
    }

    private LambdaQueryWrapper<ProductNotificationEntity> filter(Integer userId, boolean unreadOnly) {
        return Wrappers.<ProductNotificationEntity>lambdaQuery()
            .eq(ProductNotificationEntity::getUserId, userId)
            .isNull(unreadOnly, ProductNotificationEntity::getReadAt);
    }

    private void refreshAfterCommit(Integer userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { sockets.sendRefresh(userId); }
        });
    }
}
