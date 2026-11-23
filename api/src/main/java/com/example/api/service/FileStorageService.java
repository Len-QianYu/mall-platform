package com.example.api.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

// 文件存储：通过 RustFS 存取，业务和前端继续使用稳定的相对访问路径。
public interface FileStorageService {

    // 把文件存到 directory 下，文件名由服务端生成；返回访问路径（如 /images/xxx.png），
    // 调用方把它写进头像、商品主图等字段
    String store(String directory, MultipartFile file, String extension) throws IOException;

    // 文件不存在时返回空；调用方负责关闭返回的流。
    Optional<StoredFile> load(String directory, String filename) throws IOException;

    // 删除本服务的相对访问路径对应的对象；外链和不合法路径忽略，已不存在的对象视为删除成功。
    void delete(String url) throws IOException;

    record StoredFile(
        InputStream content,
        String contentType,
        long contentLength,
        String eTag,
        Instant lastModified
    ) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            this.content.close();
        }

    }

}
