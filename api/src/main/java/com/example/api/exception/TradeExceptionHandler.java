package com.example.api.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import com.example.api.controller.OrderController;
import com.example.api.controller.PaymentController;
import com.example.api.controller.ProductFavoriteController;
import com.example.api.controller.ProductNotificationController;
import com.example.api.controller.admin.AdminOrderController;
import com.example.api.controller.admin.AdminNotificationController;
import com.example.api.controller.admin.AdminProductController;
import com.example.api.controller.admin.AdminSeckillController;
import com.example.api.utils.ResultTemplate;

@RestControllerAdvice(assignableTypes = { OrderController.class, PaymentController.class,
    AdminOrderController.class, AdminProductController.class, AdminSeckillController.class,
    ProductFavoriteController.class, ProductNotificationController.class, AdminNotificationController.class })
public class TradeExceptionHandler {

    @ExceptionHandler(ValidateFailedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultTemplate invalid(ValidateFailedException error) {
        ResultTemplate result = new ResultTemplate();
        result.setStatus(false);
        return result.putPayload("message", error.getMessage());
    }

    @ExceptionHandler(TradeFailedException.class)
    public ResultTemplate handle(TradeFailedException error) {
        ResultTemplate result = new ResultTemplate();
        result.setStatus(false);
        return result.putPayload("message", error.getMessage());
    }

    @ExceptionHandler(TaskRejectedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResultTemplate busy(TaskRejectedException error) {
        ResultTemplate result = new ResultTemplate();
        result.setStatus(false);
        return result.putPayload("message", "秒杀请求过多，请稍后重试");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResultTemplate timeout(AsyncRequestTimeoutException error) {
        ResultTemplate result = new ResultTemplate();
        result.setStatus(false);
        return result.putPayload("message", "秒杀请求等待超时，请稍后重试");
    }
}
