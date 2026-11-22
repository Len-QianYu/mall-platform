package com.example.api.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillPurchaseMapper {
    @Select("SELECT quantity FROM seckill_purchase WHERE activity_id = #{activityId} AND user_id = #{userId}")
    Integer quantity(@Param("activityId") Long activityId, @Param("userId") Integer userId);

    @Insert("INSERT INTO seckill_purchase (activity_id, user_id, quantity) VALUES (#{activityId}, #{userId}, 0) "
        + "ON DUPLICATE KEY UPDATE quantity = quantity")
    int ensure(@Param("activityId") Long activityId, @Param("userId") Integer userId);

    @Update("UPDATE seckill_purchase SET quantity = quantity + #{quantity} WHERE activity_id = #{activityId} "
        + "AND user_id = #{userId} AND quantity <= #{limit} - #{quantity}")
    int increase(@Param("activityId") Long activityId, @Param("userId") Integer userId,
        @Param("quantity") Integer quantity, @Param("limit") Integer limit);

    @Update("UPDATE seckill_purchase SET quantity = quantity - #{quantity} WHERE activity_id = #{activityId} "
        + "AND user_id = #{userId} AND quantity >= #{quantity}")
    int decrease(@Param("activityId") Long activityId, @Param("userId") Integer userId,
        @Param("quantity") Integer quantity);
}
