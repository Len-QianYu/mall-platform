package com.example.api.service.implementation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.api.configuration.UploadConfiguration;
import com.example.api.service.FileStorageService;
import com.example.api.utils.ImageFormat;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

// RustFS 实现：对象键为 <directory>/<UUID>.<extension>，桶保持私有，图片由 FileController 读取。
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final S3Client client;

    private final String bucket;

    private volatile boolean bucketReady;

    public FileStorageServiceImpl(S3Client client, @Value("${rustfs.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String store(String directory, MultipartFile file, String extension) throws IOException {
        if (!UploadConfiguration.DIRECTORIES.contains(directory)) {
            throw new IllegalArgumentException("不支持的上传类型");
        }
        String contentType = ImageFormat.contentType(extension);

        // 不沿用客户端文件名；扩展名和 Content-Type 来自服务端的图片格式识别。
        String filename = UUID.randomUUID() + "." + extension;
        try (InputStream input = file.getInputStream()) {
            ensureBucket();
            // 图片由 multipart 限制为 5MB；使用可重放的内容，供 SDK 签名和重试时重复读取。
            this.client.putObject(request -> request
                .bucket(this.bucket)
                .key(directory + "/" + filename)
                .contentType(contentType), RequestBody.fromBytes(input.readAllBytes()));
        } catch (SdkException exception) {
            throw new IOException("上传到 RustFS 失败", exception);
        }

        return "/" + directory + "/" + filename;
    }

    @Override
    public Optional<StoredFile> load(String directory, String filename) throws IOException {
        if (!UploadConfiguration.DIRECTORIES.contains(directory)
            || !filename.matches("[A-Za-z0-9_-]+\\.(jpg|png|gif|webp)")) {
            return Optional.empty();
        }

        try {
            ResponseInputStream<GetObjectResponse> input = this.client.getObject(request -> request
                .bucket(this.bucket)
                .key(directory + "/" + filename));
            GetObjectResponse metadata = input.response();
            return Optional.of(new StoredFile(input, metadata.contentType(), metadata.contentLength(),
                metadata.eTag(), metadata.lastModified()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw new IOException("读取 RustFS 文件失败", exception);
        } catch (SdkException exception) {
            throw new IOException("读取 RustFS 文件失败", exception);
        }
    }

    @Override
    public void delete(String url) throws IOException {
        if (url == null) return;
        String[] path = url.split("/", -1);
        if (path.length != 3 || !path[0].isEmpty()
            || !UploadConfiguration.DIRECTORIES.contains(path[1])
            || !path[2].matches("[A-Za-z0-9_-]+\\.(jpg|png|gif|webp)")) {
            return;
        }

        try {
            this.client.deleteObject(request -> request.bucket(this.bucket).key(path[1] + "/" + path[2]));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return;
            throw new IOException("删除 RustFS 文件失败", exception);
        } catch (SdkException exception) {
            throw new IOException("删除 RustFS 文件失败", exception);
        }
    }

    // 首次上传时初始化桶；RustFS 暂未启动不影响后端启动，失败后下次上传会再次尝试。
    private synchronized void ensureBucket() {
        if (this.bucketReady) {
            return;
        }
        try {
            this.client.headBucket(request -> request.bucket(this.bucket));
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            try {
                this.client.createBucket(request -> request.bucket(this.bucket));
            } catch (S3Exception creationException) {
                // 多个后端实例可能同时建桶，409 后确认自己能访问这个桶再继续。
                if (creationException.statusCode() != 409) {
                    throw creationException;
                }
                this.client.headBucket(request -> request.bucket(this.bucket));
            }
        }
        this.bucketReady = true;
    }

}
