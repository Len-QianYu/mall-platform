package com.example.api.controller;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import com.example.api.service.FileStorageService;
import com.example.api.service.FileStorageService.StoredFile;

// 保留图片的相对访问路径；从私有 RustFS 桶读取，浏览器无需 S3 凭证或会过期的签名 URL。
@RestController
public class FileController {

    private static final String CACHE_CONTROL = CacheControl.maxAge(Duration.ofDays(30)).cachePublic().getHeaderValue();

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{directory:avatars|images}/{filename:.+}")
    public void download(
        @PathVariable String directory,
        @PathVariable String filename,
        ServletWebRequest request,
        HttpServletResponse response
    ) throws IOException {
        try (StoredFile file = this.fileStorageService.load(directory, filename)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
            response.setHeader("X-Content-Type-Options", "nosniff");
            // 条件请求和 HEAD 也会关闭 RustFS 响应流，避免长期占用连接。
            if (request.checkNotModified(file.eTag(), file.lastModified().toEpochMilli())) {
                return;
            }
            response.setContentType(file.contentType());
            response.setContentLengthLong(file.contentLength());
            if (!HttpMethod.HEAD.matches(request.getRequest().getMethod())) {
                file.content().transferTo(response.getOutputStream());
            }
        }
    }

}
