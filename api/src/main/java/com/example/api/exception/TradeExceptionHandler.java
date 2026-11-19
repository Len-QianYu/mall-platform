package com.example.api.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import com.example.api.controller.OrderController;
import com.example.api.controller.PaymentController;
import com.example.api.controller.admin.AdminOrderController;
import com.example.api.utils.ResultTemplate;

@RestControllerAdvice(assignableTypes = { OrderController.class, PaymentController.class, AdminOrderController.class })
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
}
