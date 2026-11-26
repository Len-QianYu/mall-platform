package com.example.api.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.api.controller.AccountEmailController;
import com.example.api.controller.SecurityController;
import com.example.api.utils.ResultTemplate;

@RestControllerAdvice(assignableTypes = { AccountEmailController.class, SecurityController.class })
public class AccountExceptionHandler {
    @ExceptionHandler(AccountFlowException.class)
    public ResponseEntity<ResultTemplate> flow(AccountFlowException error) {
        var result = failure(error.getMessage());
        var response = ResponseEntity.status(error.status());
        if (error.retryAfterSeconds() > 0) {
            result.putPayload("retryAfterSeconds", error.retryAfterSeconds());
            response.header("Retry-After", Integer.toString(error.retryAfterSeconds()));
        }
        return response.body(result);
    }

    @ExceptionHandler(ValidateFailedException.class)
    public ResponseEntity<ResultTemplate> invalid(ValidateFailedException error) {
        return ResponseEntity.badRequest().body(failure(error.getMessage()));
    }

    @ExceptionHandler(LoginFailedException.class)
    public ResultTemplate invalidLogin(LoginFailedException error) {
        return failure("账号或密码错误");
    }

    private static ResultTemplate failure(String message) {
        var result = new ResultTemplate();
        result.setStatus(false);
        return result.putPayload("message", message);
    }
}
