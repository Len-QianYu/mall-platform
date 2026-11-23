package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import com.example.api.configuration.UploadConfiguration;
import com.example.api.service.FileStorageService.StoredFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

// 显式开启后使用独立的临时桶测试真实 RustFS；不连接数据库和 RocketMQ。
@EnabledIfEnvironmentVariable(named = "RUSTFS_INTEGRATION_TESTS", matches = "true")
class RustfsStorageIntegrationTests {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uploadsAndReadsImagesFromPrivateBucket(boolean diskBacked, @TempDir Path temporaryDirectory) throws Exception {
        String bucket = "mall-upload-test-" + UUID.randomUUID();
        byte[] image = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a/p8AAAAASUVORK5CYII=");
        if (diskBacked) {
            image = Arrays.copyOf(image, 2 * 1024 * 1024);
        }
        Path imageFile = Files.write(temporaryDirectory.resolve("image.png"), image);
        MockMultipartFile file = new MockMultipartFile("file", "fake.html", "text/html", image) {
            @Override
            public InputStream getInputStream() throws IOException {
                // 大图片落盘后，Servlet 提供的文件流不支持 mark/reset。
                return diskBacked ? Files.newInputStream(imageFile) : super.getInputStream();
            }
        };

        try (S3Client client = new UploadConfiguration().rustfsClient(
            URI.create(setting("RUSTFS_ENDPOINT", "http://127.0.0.1:9000")),
            setting("RUSTFS_ACCESS_KEY", "mallplatform"),
            setting("RUSTFS_SECRET_KEY", "mallplatform123"),
            setting("RUSTFS_REGION", "us-east-1"))) {
            FileStorageServiceImpl storage = new FileStorageServiceImpl(client, bucket);
            try {
                for (String directory : UploadConfiguration.DIRECTORIES) {
                    String url = storage.store(directory, file, "png");
                    assertTrue(url.matches("/" + directory + "/[0-9a-f-]{36}\\.png"));
                    String filename = url.substring(url.lastIndexOf('/') + 1);

                    try (StoredFile stored = storage.load(directory, filename).orElseThrow()) {
                        assertEquals("image/png", stored.contentType());
                        assertEquals(image.length, stored.contentLength());
                        assertArrayEquals(image, stored.content().readAllBytes());
                    }

                    // 迁移工具使用同样的条件写入，已有对象必须保持原内容。
                    S3Exception conflict = assertThrows(S3Exception.class, () -> client.putObject(request ->
                        request.bucket(bucket).key(directory + "/" + filename).ifNoneMatch("*"),
                        RequestBody.fromBytes(new byte[] { 1, 2, 3 })));
                    assertEquals(412, conflict.statusCode());
                    try (StoredFile stored = storage.load(directory, filename).orElseThrow()) {
                        assertArrayEquals(image, stored.content().readAllBytes());
                    }

                    // 没有 S3 凭证的请求不能直接读私有桶中的对象。
                    java.net.HttpURLConnection anonymous = (java.net.HttpURLConnection) URI.create(
                        setting("RUSTFS_ENDPOINT", "http://127.0.0.1:9000") + "/" + bucket + url).toURL().openConnection();
                    anonymous.setConnectTimeout(3000);
                    anonymous.setReadTimeout(3000);
                    try {
                        assertEquals(403, anonymous.getResponseCode());
                    } finally {
                        anonymous.disconnect();
                    }
                }
                assertTrue(storage.load("images", UUID.randomUUID() + ".png").isEmpty());
                assertTrue(storage.load("images", "../private.png").isEmpty());
            } finally {
                // 只清理本测试创建的随机桶和对象。
                try {
                    client.listObjectsV2(request -> request.bucket(bucket)).contents().forEach(object ->
                        client.deleteObject(request -> request.bucket(bucket).key(object.key())));
                    client.deleteBucket(request -> request.bucket(bucket));
                } catch (S3Exception exception) {
                    if (exception.statusCode() != 404) {
                        throw exception;
                    }
                }
            }
        }
    }

    private static String setting(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

}
