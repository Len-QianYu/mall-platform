package com.example.api.exception;

// 下单、支付失败（商品下架、库存不足、订单状态不对等）：
// 在事务方法里抛出，让已扣的库存一并回滚；controller 捕获后把 message 返回给前端
public class TradeFailedException extends RuntimeException {

    public TradeFailedException(String message) {
        super(message);
    }

}
