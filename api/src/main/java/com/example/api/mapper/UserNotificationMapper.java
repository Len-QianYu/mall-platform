package com.example.api.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.example.api.dto.UserNotificationView;

@Mapper
public interface UserNotificationMapper {
    @Select("""
        <script>
        SELECT
            (SELECT COUNT(*) FROM product_notification WHERE user_id = #{userId}
                <if test="unreadOnly">AND read_at IS NULL</if>)
            + (SELECT COUNT(*) FROM order_shipment_notification WHERE user_id = #{userId}
                <if test="unreadOnly">AND read_at IS NULL</if>)
        </script>
        """)
    int count(@Param("userId") Integer userId, @Param("unreadOnly") boolean unreadOnly);

    // 合并后再排序、分页，避免两种通知各自分页导致遗漏或重复。
    @Select("""
        <script>
        SELECT id, type, product_id, product_name, product_image, old_price, new_price,
               NULL AS order_id, NULL AS order_no, NULL AS total_amount, NULL AS shipped_at,
               read_at, created_at
        FROM product_notification WHERE user_id = #{userId}
            <if test="unreadOnly">AND read_at IS NULL</if>
        UNION ALL
        SELECT id, 'ORDER_SHIPPED' AS type, NULL, NULL, NULL, NULL, NULL,
               order_id, order_no, total_amount, shipped_at, read_at, created_at
        FROM order_shipment_notification WHERE user_id = #{userId}
            <if test="unreadOnly">AND read_at IS NULL</if>
        ORDER BY created_at DESC, type ASC, id DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<UserNotificationView> fetch(@Param("userId") Integer userId, @Param("unreadOnly") boolean unreadOnly,
        @Param("limit") Integer limit, @Param("offset") Integer offset);
}
