package com.example.api.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.OrderShipmentNotificationEntity;

@Mapper
public interface OrderShipmentNotificationMapper extends BaseMapper<OrderShipmentNotificationEntity> {
    // 接收人取订单买家，订单号、金额、发货时间保存为快照。
    @Insert("""
        INSERT INTO order_shipment_notification
            (user_id, order_id, order_no, total_amount, shipped_at, created_at)
        SELECT user_id, id, order_no, total_amount, shipped_at, shipped_at
        FROM orders WHERE id = #{orderId} AND status = 'SHIPPED' AND shipped_at IS NOT NULL
        ON DUPLICATE KEY UPDATE read_at = order_shipment_notification.read_at
        """)
    int insertForOrder(@Param("orderId") Long orderId);

    @Update("""
        UPDATE order_shipment_notification SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP(6))
        WHERE id = #{id} AND user_id = #{userId}
        """)
    int markRead(@Param("userId") Integer userId, @Param("id") Long id);

    @Update("""
        UPDATE order_shipment_notification SET read_at = CURRENT_TIMESTAMP(6)
        WHERE user_id = #{userId} AND read_at IS NULL
        """)
    int markAllRead(@Param("userId") Integer userId);
}
