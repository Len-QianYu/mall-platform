package com.example.api.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.dto.DashboardTrendPoint;
import com.example.api.dto.OrderStatusCount;
import com.example.api.dto.OrderStatusSalesAmount;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.UserEntity;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEntity> {

    // 两次幂等检查都实际查库，不复用 MyBatis 的空结果；只读查询不锁不存在记录的索引间隙。
    @Select("SELECT * FROM orders WHERE user_id = #{userId} AND request_id = #{requestId}")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    OrderEntity selectByRequest(@Param("userId") Integer userId, @Param("requestId") String requestId);

    // 支付、退款和取消统一先锁订单行，再锁流水和商品。
    @Select("SELECT * FROM orders WHERE id = #{id} FOR UPDATE")
    OrderEntity selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT NOW()")
    LocalDateTime databaseNow();

    // 订单收货人统一使用买家的用户名；一页订单批量查询，不逐单查用户。
    @Select("""
        <script>
        SELECT id, username FROM `user` WHERE id IN
        <foreach collection="userIds" item="userId" open="(" separator="," close=")">
            #{userId}
        </foreach>
        </script>
        """)
    List<UserEntity> selectReceiverUsernames(@Param("userIds") List<Integer> userIds);

    // 销售额：已支付、已发货、已完成订单的金额合计（待支付、已取消的不算）
    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')")
    BigDecimal sumSalesAmount();

    // 与累计销售额相同的状态范围，按当前订单状态拆分金额。
    @Select("""
        SELECT status, SUM(total_amount) AS amount
        FROM orders
        WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
        GROUP BY status
        """)
    List<OrderStatusSalesAmount> fetchSalesAmountsByStatus();

    @Select("""
        SELECT DATE(created_at) AS date, COUNT(*) AS orderCount, 0 AS salesAmount
        FROM orders
        WHERE created_at >= #{start} AND created_at < #{end}
        GROUP BY DATE(created_at)
        ORDER BY date
        """)
    List<DashboardTrendPoint> fetchDailyOrders(
        @Param("start") LocalDateTime start, @Param("end") LocalDateTime end
    );

    // 与累计销售额口径一致：退款后不再计入销售额，未支付订单也不计入。
    @Select("""
        SELECT DATE(paid_at) AS date, 0 AS orderCount, SUM(total_amount) AS salesAmount
        FROM orders
        WHERE paid_at >= #{start} AND paid_at < #{end}
          AND status IN ('PAID', 'SHIPPED', 'COMPLETED')
        GROUP BY DATE(paid_at)
        ORDER BY date
        """)
    List<DashboardTrendPoint> fetchDailySales(
        @Param("start") LocalDateTime start, @Param("end") LocalDateTime end
    );

    @Select("SELECT status, COUNT(*) AS count FROM orders GROUP BY status")
    List<OrderStatusCount> fetchStatusCounts();

}
