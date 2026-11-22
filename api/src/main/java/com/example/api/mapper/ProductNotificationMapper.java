package com.example.api.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.ProductNotificationEntity;

@Mapper
public interface ProductNotificationMapper extends BaseMapper<ProductNotificationEntity> {
    // 收藏晚于变动、已取消或取消后重新订阅的用户不接收历史事件。
    @Insert("""
        INSERT INTO product_notification
            (user_id, event_id, product_id, type, product_name, product_image, old_price, new_price, created_at)
        SELECT f.user_id, e.id, e.product_id, e.type, e.product_name, e.product_image,
               e.old_price, e.new_price, e.created_at
        FROM product_notification_event e JOIN product_favorite f ON f.product_id = e.product_id
        WHERE e.id = #{eventId} AND f.created_at <= e.created_at
        ON DUPLICATE KEY UPDATE read_at = product_notification.read_at
        """)
    int insertForEvent(@Param("eventId") Long eventId);

    @Select("SELECT * FROM product_notification WHERE event_id = #{eventId} ORDER BY id")
    List<ProductNotificationEntity> selectByEvent(@Param("eventId") Long eventId);

    @Update("""
        UPDATE product_notification SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP(6))
        WHERE id = #{id} AND user_id = #{userId}
        """)
    int markRead(@Param("userId") Integer userId, @Param("id") Long id);

    @Update("UPDATE product_notification SET read_at = CURRENT_TIMESTAMP(6) WHERE user_id = #{userId} AND read_at IS NULL")
    int markAllRead(@Param("userId") Integer userId);
}
