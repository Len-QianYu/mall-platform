package com.example.api.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

// 订单号、支付流水号：时间到秒 + 6 位随机数。
// 唯一性由 uk_order_no / uk_payment_no 兜底：同一秒的两笔交易撞号的概率是百万分之一，
// 撞上时插入报错、整笔交易回滚，用户重试即可
public final class TradeNo {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private TradeNo() {
    }

    public static String order() {
        return generate("");
    }

    // 支付流水号带 P 前缀，和订单号区分开（与 data.sql 一致）
    public static String payment() {
        return generate("P");
    }

    private static String generate(String prefix) {
        return prefix
            + LocalDateTime.now().format(TIME_FORMAT)
            + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

}
