package com.example.api.controller;

import java.io.IOException;
import java.util.Optional;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.api.configuration.UploadConfiguration;
import com.example.api.service.FileStorageService;
import com.example.api.utils.ImageFormat;
import com.example.api.utils.ResultTemplate;

// 文件上传（需登录；商品图片只有管理员能传，见 SecurityConfiguration）：
// 返回访问路径，前端把它填进头像、商品主图等字段，随表单一起保存
@RestController
@RequestMapping("/upload")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    // type 为上传用途：avatars 头像 / images 商品图片
    @PostMapping("/{type}")
    public ResultTemplate upload(
        @PathVariable String type,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        ResultTemplate result = new ResultTemplate();

        // type 会成为存放目录名，只认白名单：.. 之类的值进不来，文件写不到上传目录之外
        if (!UploadConfiguration.DIRECTORIES.contains(type)) {
            result.setStatus(false);
            result.putPayload("message", "不支持的上传类型");
            return result;
        }

        if (file.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "请选择要上传的图片");
            return result;
        }

        Optional<String> extension = ImageFormat.detect(file);
        if (extension.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "只支持 JPG、PNG、GIF、WEBP 格式的图片");
            return result;
        }

        result.putPayload("url", this.fileStorageService.store(type, file, extension.get()));
        return result;
    }

}
