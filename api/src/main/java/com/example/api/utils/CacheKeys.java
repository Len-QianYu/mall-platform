package com.example.api.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

// 拼缓存 key：各部分 URL 编码后用 ':' 连接，null 记为空串（和不传一样，表示不筛选）。
// 关键字、分类、排序都来自 URL，本身可能带 ':'；不编码的话「关键字 a:b」和「关键字 a + 分类 b」会拼出同一个 key，
// 两个查询互相串结果
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String join(Object... parts) {
        return Arrays.stream(parts)
            .map(part -> part == null ? "" : URLEncoder.encode(part.toString(), StandardCharsets.UTF_8))
            .collect(Collectors.joining(":"));
    }

}
