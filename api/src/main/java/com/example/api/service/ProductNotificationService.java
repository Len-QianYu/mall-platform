package com.example.api.service;

import java.util.List;

import com.example.api.entity.ProductNotificationEntity;
import com.example.api.utils.Pagination;

public interface ProductNotificationService {
    int count(Integer userId, boolean unreadOnly);
    List<ProductNotificationEntity> fetch(Integer userId, boolean unreadOnly, Pagination pagination);
    boolean markRead(Integer userId, Long id);
    void markAllRead(Integer userId);
    void consumeEvent(Long eventId);
}
