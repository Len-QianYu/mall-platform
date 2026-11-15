import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Properties;

import com.example.api.configuration.UploadConfiguration;
import com.example.api.utils.ImageFormat;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

// 一次性迁移旧上传目录：保留文件名和访问路径，已有对象跳过，原文件保留。
public class MigrateUploads {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("用法：MigrateUploads.java <旧上传目录>");
        }
        Path root = Path.of(args[0]).toRealPath();
        Properties settings = new Properties();
        Path env = Path.of(".env");
        if (Files.isRegularFile(env)) {
            try (Reader reader = Files.newBufferedReader(env, StandardCharsets.UTF_8)) {
                settings.load(reader);
            }
        }
        String bucket = setting(settings, "RUSTFS_BUCKET", "mall-uploads");
        try (S3Client client = new UploadConfiguration().rustfsClient(
            URI.create(setting(settings, "RUSTFS_ENDPOINT", "http://127.0.0.1:9000")),
            setting(settings, "RUSTFS_ACCESS_KEY", "mallplatform"),
            setting(settings, "RUSTFS_SECRET_KEY", "mallplatform123"),
            setting(settings, "RUSTFS_REGION", "us-east-1"))) {
            try {
                client.headBucket(request -> request.bucket(bucket));
            } catch (S3Exception exception) {
                if (exception.statusCode() != 404) {
                    throw exception;
                }
                try {
                    client.createBucket(request -> request.bucket(bucket));
                } catch (S3Exception creationException) {
                    if (creationException.statusCode() != 409) {
                        throw creationException;
                    }
                    client.headBucket(request -> request.bucket(bucket));
                }
            }

            int migrated = 0;
            for (String directory : UploadConfiguration.DIRECTORIES) {
                Path folder = root.resolve(directory);
                if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try (var paths = Files.list(folder)) {
                    for (Path file : paths.toList()) {
                        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            continue;
                        }
                        String filename = file.getFileName().toString();
                        if (!filename.matches("[A-Za-z0-9_-]+\\.(jpg|png|gif|webp)")) {
                            throw new IllegalArgumentException("旧文件名不符合图片访问规则：" + file);
                        }
                        String key = directory + "/" + filename;
                        String extension = filename.substring(filename.lastIndexOf('.') + 1);
                        try {
                            client.putObject(request -> request.bucket(bucket).key(key)
                                .contentType(ImageFormat.contentType(extension))
                                .ifNoneMatch("*"), RequestBody.fromFile(file));
                            migrated++;
                            System.out.println("已迁移：/" + key);
                        } catch (S3Exception exception) {
                            // 条件写入保证重复运行不会覆盖桶中已经存在的对象。
                            if (exception.statusCode() != 412) {
                                throw exception;
                            }
                            System.out.println("已存在，跳过：/" + key);
                        }
                        byte[] localHash = checksum(Files.newInputStream(file));
                        byte[] remoteHash = checksum(client.getObject(request -> request.bucket(bucket).key(key)));
                        if (!MessageDigest.isEqual(localHash, remoteHash)) {
                            throw new IOException("迁移内容校验失败，RustFS 对象与本地文件不同：/" + key);
                        }
                        System.out.println("内容校验通过：/" + key);
                    }
                }
            }
            System.out.println("迁移完成，共上传 " + migrated + " 个文件；本地原文件保留。");
        }
    }

    private static byte[] checksum(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream stream = new DigestInputStream(input, digest)) {
            stream.transferTo(OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    private static String setting(Properties settings, String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null) {
            value = settings.getProperty(name, defaultValue);
        }
        return value;
    }

}
