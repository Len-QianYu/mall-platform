package com.example.api.validator;

public interface OrderValidateGroup {

    // 发货、取消、确认收货：目标状态由接口决定，请求体里只有订单 ID
    public interface UpdateStatus {}

}
