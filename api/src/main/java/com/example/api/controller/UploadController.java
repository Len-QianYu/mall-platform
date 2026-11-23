package com.example.api.controller;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.api.configuration.UploadConfiguration;
import com.example.api.service.FileStorageService;
import com.example.api.service.UserService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.ImageFormat;
import com.example.api.utils.ResultTemplate;

// 文件上传（需登录；商品图片只有管理员能传，见 SecurityConfiguration）：
// 头像上传后直接保存到当前账号；商品图片返回访问路径，随商品表单保存
@RestController
@RequestMapping("/upload")
public class UploadController {

    private final FileStorageService fileStorageService;

    private final UserService userService;

    public UploadController(FileStorageService fileStorageService, UserService userService) {
        this.fileStorageService = fileStorageService;
        this.userService        = userService;
    }

    // type 为上传用途：avatars 头像 / images 商品图片
    @PostMapping("/{type}")
    public ResultTemplate upload(
        @PathVariable String type,
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal Jwt jwt
    ) throws IOException {
        ResultTemplate result = new ResultTemplate();

        // type 会成为 RustFS 对象键的前缀，只认白名单。
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

        String url = this.fileStorageService.store(type, file, extension.get());
        if ("avatars".equals(type) && this.userService.updateAvatar(CurrentUser.id(jwt), url) == 0) {
            result.setStatus(false);
            result.putPayload("message", "用户不存在，头像保存失败");
            return result;
        }

        result.putPayload("url", url);
        return result;
    }

}
