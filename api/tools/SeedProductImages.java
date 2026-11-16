import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import com.example.api.configuration.UploadConfiguration;
import com.example.api.demo.DemoImageCatalog;
import com.example.api.demo.DemoImageImporter;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;

// 在 api/ 下运行；不启动第二个后端实例，也不创建 RocketMQ 消费者。
public class SeedProductImages {

    public static void main(String[] args) throws Exception {
        boolean dryRun = Arrays.asList(args).contains("--dry-run");
        if (args.length > (dryRun ? 1 : 0)) {
            throw new IllegalArgumentException("用法：SeedProductImages.java [--dry-run]");
        }
        Properties settings = new Properties();
        Path env = Path.of(".env");
        if (Files.isRegularFile(env)) {
            try (Reader reader = Files.newBufferedReader(env, StandardCharsets.UTF_8)) {
                settings.load(reader);
            }
        }
        DemoImageCatalog catalog = DemoImageCatalog.load(Path.of("demo/product-images"));
        catalog.verifyAssets();
        System.out.printf("素材校验通过：%d 个商品类型、%d 张图片（含原始图书的专用封面），商品名称、审核来源与 SHA-256 一致。%n",
            catalog.entries().size(), catalog.allPhotos().size());
        String database = setting(settings, "DB_NAME", "mall_platform");
        String jdbc = "jdbc:mysql://" + setting(settings, "DB_HOST", "localhost") + ":" + setting(settings, "DB_PORT", "3306")
            + "/" + database + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        try (var connection = DriverManager.getConnection(jdbc, setting(settings, "DB_USERNAME", "root"), setting(settings, "DB_PASSWORD", ""))) {
            var plan = DemoImageImporter.plan(connection, catalog);
            System.out.printf("数据库 %s：待新增演示商品 %d 件，待补齐或修正商品图 %d 件，待补齐或修正原始演示订单图 %d 条。%n",
                database, plan.missingDemoProducts(), plan.productImagesToUpdate(), plan.orderImagesToUpdate());
            if (dryRun) {
                System.out.println("预览完成；RustFS、数据库和 Redis 均未修改。");
                return;
            }
            try (var client = new UploadConfiguration().rustfsClient(
                URI.create(setting(settings, "RUSTFS_ENDPOINT", "http://127.0.0.1:9000")),
                required(settings, "RUSTFS_ACCESS_KEY"), required(settings, "RUSTFS_SECRET_KEY"), setting(settings, "RUSTFS_REGION", "us-east-1"))) {
                var uploaded = DemoImageImporter.upload(client, setting(settings, "RUSTFS_BUCKET", "mall-uploads"), catalog);
                System.out.printf("RustFS：新上传 %d 张，已有且校验通过 %d 张。%n", uploaded.uploaded(), uploaded.existing());
            }
            var applied = DemoImageImporter.apply(connection, catalog);
            System.out.printf("数据库事务已提交：新增演示商品 %d 件，补齐或修正商品图 %d 件，补齐或修正演示订单图 %d 条。%n",
                applied.newProducts(), applied.productImages(), applied.orderImages());
        }
        clearProductCaches(settings);
        System.out.println("商品演示图片导入与映射修复完成。");
    }

    private static void clearProductCaches(Properties settings) {
        RedisClient client = RedisClient.create(RedisURI.builder()
            .withHost(setting(settings, "REDIS_HOST", "127.0.0.1"))
            .withPort(Integer.parseInt(setting(settings, "REDIS_PORT", "6379")))
            .withTimeout(Duration.ofSeconds(3)).build());
        try (var connection = client.connect()) {
            var commands = connection.sync();
            long removed = 0;
            for (String cache : new String[] { "product:detail", "product:list", "product:count", "product:categories" }) {
                ScanCursor cursor = ScanCursor.INITIAL;
                do {
                    var scan = commands.scan(cursor, ScanArgs.Builder.matches(cache + "::*").limit(1000));
                    if (!scan.getKeys().isEmpty()) {
                        removed += commands.del(scan.getKeys().toArray(String[]::new));
                    }
                    cursor = scan;
                } while (!cursor.isFinished());
            }
            System.out.println("已清理商品相关缓存 " + removed + " 项。");
        } catch (RuntimeException exception) {
            System.err.println("图片和数据库已完成，但 Redis 缓存清理失败；Redis 恢复后可再次运行本命令。原因：" + exception.getMessage());
        } finally {
            client.shutdown();
        }
    }

    private static String required(Properties settings, String name) {
        String value = setting(settings, name, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("请先在 api/.env 中填写 " + name);
        }
        return value;
    }

    private static String setting(Properties settings, String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null ? settings.getProperty(name, defaultValue) : value;
    }

}
