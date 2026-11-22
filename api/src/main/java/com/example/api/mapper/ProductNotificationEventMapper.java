package com.example.api.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.ProductNotificationEventEntity;

@Mapper
public interface ProductNotificationEventMapper extends BaseMapper<ProductNotificationEventEntity> {
    @Select("SELECT * FROM product_notification_event WHERE id = #{id} FOR UPDATE")
    ProductNotificationEventEntity selectForUpdate(@Param("id") Long id);

    @Select("""
        SELECT id FROM product_notification_event
        WHERE published_at IS NULL AND consumed_at IS NULL ORDER BY id LIMIT 100
        """)
    List<Long> selectPendingIds();

    @Update("UPDATE product_notification_event SET published_at = CURRENT_TIMESTAMP(6) WHERE id = #{id} AND published_at IS NULL")
    int markPublished(@Param("id") Long id);

    @Update("UPDATE product_notification_event SET consumed_at = CURRENT_TIMESTAMP(6) WHERE id = #{id}")
    int markConsumed(@Param("id") Long id);
}
