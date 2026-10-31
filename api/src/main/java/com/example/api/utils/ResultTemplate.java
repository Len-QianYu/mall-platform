package com.example.api.utils;

import java.util.HashMap;
import java.util.Map;

// 统一响应结构：status 表示业务是否成功，payload 承载数据或错误消息
public class ResultTemplate {

    private boolean status = true;

    private Map<String, Object> payload = new HashMap<>();

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    // 链式写入 payload
    public ResultTemplate putPayload(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }

}
