package com.example.api.dto;

import java.util.List;

import org.springframework.beans.BeanUtils;

import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

// 我的订单列表：订单主表字段平铺，再带上商品明细，列表里直接展示商品
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderWithItemsView extends OrderEntity {

    private List<OrderItemEntity> items;

    public static OrderWithItemsView of(OrderEntity order, List<OrderItemEntity> items) {
        OrderWithItemsView view = new OrderWithItemsView();
        BeanUtils.copyProperties(order, view);
        view.setItems(items);
        return view;
    }

}
