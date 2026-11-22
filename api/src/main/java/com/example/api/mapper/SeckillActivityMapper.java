package com.example.api.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.dto.SeckillActivityView;
import com.example.api.entity.SeckillActivityEntity;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivityEntity> {
    String VIEW_COLUMNS = "a.*, p.name AS product_name, p.subtitle AS product_subtitle, "
        + "p.main_image AS product_image, p.status AS product_status, "
        + "UNIX_TIMESTAMP(a.starts_at) * 1000 AS starts_at_epoch_ms, "
        + "UNIX_TIMESTAMP(a.ends_at) * 1000 AS ends_at_epoch_ms, "
        + "CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED) AS server_time";

    String VIEW_FILTER = """
        WHERE 1 = 1
        <if test="!admin">AND a.status != 0 AND p.status = 1 AND a.ends_at > NOW() - INTERVAL 1 DAY</if>
        <if test="phase == 'LIVE'">AND a.status = 1 AND a.starts_at &lt;= NOW() AND a.ends_at > NOW() AND a.stock > 0</if>
        <if test="phase == 'UPCOMING'">AND a.status = 1 AND a.starts_at > NOW()</if>
        <if test="phase == 'SOLD_OUT'">AND a.status = 1 AND a.starts_at &lt;= NOW() AND a.ends_at > NOW() AND a.stock = 0</if>
        <if test="phase == 'ENDED'">AND a.ends_at &lt;= NOW()</if>
        """;

    @Select("SELECT " + VIEW_COLUMNS + " FROM seckill_activity a JOIN product p ON p.id = a.product_id "
        + "WHERE a.id = #{id}")
    SeckillActivityView selectView(@Param("id") Long id);

    @Select("<script>SELECT " + VIEW_COLUMNS
        + " FROM seckill_activity a JOIN product p ON p.id = a.product_id "
        + VIEW_FILTER
        + "ORDER BY CASE WHEN a.status = 1 AND a.starts_at &lt;= NOW() AND a.ends_at > NOW() THEN 0 "
        + "WHEN a.status = 1 AND a.starts_at > NOW() THEN 1 ELSE 2 END, a.starts_at ASC, a.id DESC "
        + "LIMIT #{limit} OFFSET #{offset}</script>")
    List<SeckillActivityView> selectViews(@Param("admin") boolean admin, @Param("phase") String phase,
        @Param("offset") Integer offset, @Param("limit") Integer limit);

    @Select("<script>SELECT COUNT(*) FROM seckill_activity a JOIN product p ON p.id = a.product_id "
        + VIEW_FILTER + "</script>")
    int countViews(@Param("admin") boolean admin, @Param("phase") String phase);

    @Select("SELECT * FROM seckill_activity WHERE id = #{id} FOR UPDATE")
    SeckillActivityEntity selectForUpdate(@Param("id") Long id);

    @Select("SELECT NOW()")
    LocalDateTime databaseNow();

    // 最终库存以数据库为准；即使 Redis 丢失或重建，条件更新也不会卖出超过活动配额。
    @Update("UPDATE seckill_activity SET stock = stock - #{quantity} WHERE id = #{id} AND status = 1 "
        + "AND starts_at <= NOW() AND ends_at > NOW() AND stock >= #{quantity}")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("UPDATE seckill_activity SET stock = stock + #{quantity} WHERE id = #{id} AND status = 1 "
        + "AND stock + returned_stock + #{quantity} <= total_stock")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("UPDATE seckill_activity SET returned_stock = returned_stock + #{quantity} WHERE id = #{id} "
        + "AND status != 1 AND stock + returned_stock + #{quantity} <= total_stock")
    int returnStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("UPDATE seckill_activity SET status = #{status}, returned_stock = returned_stock + stock, stock = 0 "
        + "WHERE id = #{id} AND status = 1")
    int close(@Param("id") Long id, @Param("status") Integer status);

    @Select("SELECT id FROM seckill_activity WHERE status = 1 AND ends_at <= NOW() ORDER BY id LIMIT #{limit}")
    List<Long> selectExpiredIds(@Param("limit") Integer limit);
}
