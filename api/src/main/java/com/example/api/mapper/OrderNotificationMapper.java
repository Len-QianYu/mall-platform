package com.example.api.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.OrderNotificationEntity;

@Mapper
public interface OrderNotificationMapper extends BaseMapper<OrderNotificationEntity> {
    // 在下单事务中确定接收人，并保留下单时的买家资料与金额。
    @Insert("""
        INSERT INTO order_notification
            (user_id, order_id, order_no, buyer_id, buyer_username, buyer_name, total_amount, created_at)
        SELECT admin.id, o.id, o.order_no, o.user_id,
               COALESCE(buyer.username, CAST(o.user_id AS CHAR)), buyer.name, o.total_amount, o.created_at
        FROM orders o
        LEFT JOIN user buyer ON buyer.id = o.user_id
        JOIN user admin ON admin.role IN ('admin', 'super_admin') AND admin.status = 1
        WHERE o.id = #{orderId}
        ORDER BY admin.id
        ON DUPLICATE KEY UPDATE read_at = order_notification.read_at
        """)
    int insertForOrder(@Param("orderId") Long orderId);

    @Update("""
        UPDATE order_notification SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP(6))
        WHERE id = #{id} AND user_id = #{userId}
        """)
    int markRead(@Param("userId") Integer userId, @Param("id") Long id);

    @Update("UPDATE order_notification SET read_at = CURRENT_TIMESTAMP(6) WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") Integer userId);
}
