package com.example.api.exception;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.api.controller.FileController;
import com.example.api.controller.UploadController;
import com.example.api.utils.ResultTemplate;

@RestControllerAdvice(assignableTypes = { UploadController.class, FileController.class })
public class FileStorageExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileStorageExceptionHandler.class);

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResultTemplate handle(IOException exception) {
        LOGGER.error("文件存储请求失败", exception);
        ResultTemplate result = new ResultTemplate();
        result.setStatus(false);
        return result.putPayload("message", "文件服务暂不可用，请稍后重试");
    }

}
