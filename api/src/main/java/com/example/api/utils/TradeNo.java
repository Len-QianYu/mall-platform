package com.example.api.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

// 秒级时间 + 64 位随机后缀，订单号 30 字符、支付号 31 字符，均满足数据库的 32 字符限制。
// 大样本会放大原 6 位随机后缀的生日碰撞；数据库唯一索引继续作最终校验。
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
            + HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong());
    }

}
