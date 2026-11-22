package com.example.api.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.entity.ProductEntity;
import com.example.api.entity.ProductNotificationEventEntity;
import com.example.api.enums.ProductNotificationType;
import com.example.api.mapper.ProductNotificationEventMapper;

@Service
public class ProductNotificationEventRecorder {
    private final ProductNotificationEventMapper events;

    public ProductNotificationEventRecorder(ProductNotificationEventMapper events) { this.events = events; }

    // 必须与商品编辑在同一事务内；回滚不留消息，发送失败可从数据库重试。
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChanges(ProductEntity before, ProductEntity after) {
        if (!Objects.equals(before.getStatus(), 1) && Objects.equals(after.getStatus(), 1)) {
            this.record(before, after, ProductNotificationType.LISTED);
        }
        int priceChange = after.getPrice().compareTo(before.getPrice());
        if (priceChange != 0) {
            this.record(before, after, priceChange < 0
                ? ProductNotificationType.PRICE_DROP : ProductNotificationType.PRICE_RISE);
        }
    }

    private void record(ProductEntity before, ProductEntity after, ProductNotificationType type) {
        ProductNotificationEventEntity event = new ProductNotificationEventEntity();
        event.setProductId(after.getId());
        event.setType(type);
        event.setProductName(after.getName());
        event.setProductImage(after.getMainImage());
        event.setOldPrice(before.getPrice());
        event.setNewPrice(after.getPrice());
        this.events.insert(event);
    }
}
