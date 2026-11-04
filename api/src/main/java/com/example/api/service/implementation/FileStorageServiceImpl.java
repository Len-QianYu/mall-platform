package com.example.api.service.implementation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.api.service.FileStorageService;

// 本地磁盘实现：存到 upload.root/<directory>/，由 UploadConfiguration 映射成同名的访问路径
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path root;

    public FileStorageServiceImpl(@Value("${upload.root}") String root) {
        // 转成绝对路径：transferTo 收到相对路径时会按 Tomcat 的临时目录解析，文件就存丢了
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String store(String directory, MultipartFile file, String extension) throws IOException {
        Path folder = Files.createDirectories(this.root.resolve(directory));

        // 文件名用 UUID 生成，不沿用原文件名：不会重名覆盖，原文件名里的 ../ 之类的字符也进不了路径
        String filename = UUID.randomUUID() + "." + extension;
        file.transferTo(folder.resolve(filename));

        return "/" + directory + "/" + filename;
    }

}
