package com.example.api.configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 上传文件存本地磁盘（RustFS 接入前）：upload.root 下按用途分目录，每个目录映射成同名的访问路径，
// 如 <root>/avatars/a.png → /avatars/a.png（SecurityConfiguration 已放行这些路径，<img> 不带 Token 也能加载）
@Configuration
public class UploadConfiguration implements WebMvcConfigurer {

    // 允许的上传用途，同时是存放目录名和访问路径前缀：用户头像、商品图片
    public static final Set<String> DIRECTORIES = Set.of("avatars", "images");

    private final Path root;

    public UploadConfiguration(@Value("${upload.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 只映射白名单里的目录，不把整个 upload.root 暴露出去
        for (String directory : DIRECTORIES) {
            String location = this.root.resolve(directory).toUri().toString();
            registry.addResourceHandler("/" + directory + "/**")
                // 目录还没建时 toUri() 不带结尾的 /，少了它文件会按上一级目录解析
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                // 文件名是 UUID，同一个地址的内容不会变，浏览器可以长期缓存
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)));
        }
    }

}
