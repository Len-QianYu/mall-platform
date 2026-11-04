package com.example.api.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

// 文件存储：现在存本地磁盘；接入 RustFS 时换一个实现，上传接口和存路径的字段都不用改
public interface FileStorageService {

    // 把文件存到 directory 下，文件名由服务端生成；返回访问路径（如 /images/xxx.png），
    // 调用方把它写进头像、商品主图等字段
    String store(String directory, MultipartFile file, String extension) throws IOException;

}
