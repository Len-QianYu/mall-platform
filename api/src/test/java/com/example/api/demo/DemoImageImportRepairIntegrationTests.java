package com.example.api.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// 使用独立随机数据库验证真实 MySQL 的更新和回滚，不启动 Spring 或 RocketMQ。
@EnabledIfEnvironmentVariable(named = "DEMO_IMAGE_INTEGRATION_TESTS", matches = "true")
class DemoImageImportRepairIntegrationTests {

    @Test
    void repairsExistingDemoImagesPreservesManualImagesAndIsIdempotent() throws Exception {
        DemoImageCatalog catalog = DemoImageCatalog.load(Path.of("demo/product-images"));
        var toner = catalog.entries().stream().filter(entry -> entry.slug().equals("toner")).findFirst().orElseThrow();
        var book = catalog.entryFor(15);
        var aiBook = catalog.entryFor(16);
        String oldToner = catalog.retiredImagePaths().stream().filter(path -> path.contains("demo-toner-2-")).findFirst().orElseThrow();
        String oldBook = catalog.retiredImagePaths().stream().filter(path -> path.contains("demo-java-book-")).findFirst().orElseThrow();
        var missingDemo = catalog.entries().stream().filter(entry -> entry.slug().equals("candle")).findFirst().orElseThrow();
        List<Integer> ids = benchmarkIds(catalog.entries().indexOf(toner), 5);

        withIsolatedDatabase(connection -> {
            seedDemos(connection, catalog, Set.of(missingDemo.demoId()));
            putProduct(connection, ids.get(0), benchmarkName(ids.get(0), toner.item()), toner.category(), oldToner);
            putProduct(connection, ids.get(1), benchmarkName(ids.get(1), toner.item()), toner.category(), "/images/manual-toner.webp");
            putProduct(connection, ids.get(2), benchmarkName(ids.get(2), toner.item() + "保护套"), toner.category(), oldToner);
            putProduct(connection, ids.get(3), benchmarkName(ids.get(3), toner.item()), "数码", oldToner);
            putProduct(connection, ids.get(4), benchmarkName(ids.get(4), toner.item()), toner.category(), toner.imagePath(ids.get(4)));
            putProduct(connection, 200000, benchmarkName(200000, toner.item()), toner.category(), oldToner);
            putProduct(connection, 15, legacyName(book, 15), book.category(), oldBook);
            putProduct(connection, 16, legacyName(aiBook, 16), aiBook.category(), "/images/manual-ai-book.webp");

            var snapshot6 = catalog.snapshots().stream().filter(snapshot -> snapshot.id() == 6).findFirst().orElseThrow();
            var snapshot7 = catalog.snapshots().stream().filter(snapshot -> snapshot.id() == 7).findFirst().orElseThrow();
            putOrderItem(connection, snapshot6.id(), snapshot6.productId(), snapshot6.name(), snapshot6.createdAt(), oldBook);
            putOrderItem(connection, snapshot7.id(), snapshot7.productId(), snapshot7.name(), snapshot7.createdAt(), oldBook);
            putOrderItem(connection, 9001, snapshot6.productId(), snapshot6.name(), snapshot6.createdAt(), oldBook);

            var snapshot1 = catalog.snapshots().stream().filter(snapshot -> snapshot.id() == 1).findFirst().orElseThrow();
            var headphones = catalog.entryFor(1);
            putProduct(connection, 1, snapshot1.name(), headphones.category(), headphones.imagePath(1));
            putOrderItem(connection, 1, 1, snapshot1.name(), snapshot1.createdAt(), "/images/manual-order-snapshot.webp");
            var snapshot2 = catalog.snapshots().stream().filter(snapshot -> snapshot.id() == 2).findFirst().orElseThrow();
            var charger = catalog.entryFor(4);
            putProduct(connection, 4, snapshot2.name(), charger.category(), charger.imagePath(4));
            String otherTime = LocalDateTime.parse(snapshot2.createdAt().replace(' ', 'T')).plusSeconds(1).toString().replace('T', ' ');
            putOrderItem(connection, 2, 4, snapshot2.name(), otherTime, oldToner);

            var plan = DemoImageImporter.plan(connection, catalog);
            assertEquals(1, plan.missingDemoProducts());
            assertEquals(2, plan.productImagesToUpdate());
            assertEquals(2, plan.orderImagesToUpdate());
            var applied = DemoImageImporter.apply(connection, catalog);
            assertEquals(1, applied.newProducts());
            assertEquals(2, applied.productImages());
            assertEquals(2, applied.orderImages());
            assertEquals(toner.imagePath(ids.get(0)), scalar(connection, "SELECT main_image FROM product WHERE id = " + ids.get(0)));
            assertEquals("/images/manual-toner.webp", scalar(connection, "SELECT main_image FROM product WHERE id = " + ids.get(1)));
            assertEquals(oldToner, scalar(connection, "SELECT main_image FROM product WHERE id = " + ids.get(2)));
            assertEquals(oldToner, scalar(connection, "SELECT main_image FROM product WHERE id = " + ids.get(3)));
            assertEquals(oldToner, scalar(connection, "SELECT main_image FROM product WHERE id = 200000"));
            assertEquals(book.imagePath(15), scalar(connection, "SELECT main_image FROM product WHERE id = 15"));
            assertEquals(book.imagePath(15), scalar(connection, "SELECT product_image FROM order_item WHERE id = 6"));
            assertEquals("/images/manual-ai-book.webp", scalar(connection, "SELECT main_image FROM product WHERE id = 16"));
            assertEquals("/images/manual-ai-book.webp", scalar(connection, "SELECT product_image FROM order_item WHERE id = 7"));
            assertEquals("/images/manual-order-snapshot.webp", scalar(connection, "SELECT product_image FROM order_item WHERE id = 1"));
            assertEquals(oldToner, scalar(connection, "SELECT product_image FROM order_item WHERE id = 2"));
            assertEquals(oldBook, scalar(connection, "SELECT product_image FROM order_item WHERE id = 9001"));
            assertEquals("123.45/17/0", scalar(connection, "SELECT CONCAT(price, '/', stock, '/', status) FROM product WHERE id = " + ids.get(0)));
            assertEquals(missingDemo.imagePath(missingDemo.demoId()), scalar(connection, "SELECT main_image FROM product WHERE id = " + missingDemo.demoId()));

            var repeatedPlan = DemoImageImporter.plan(connection, catalog);
            assertEquals(new DemoImageImporter.Plan(0, 0, 0), repeatedPlan);
            assertEquals(new DemoImageImporter.DatabaseResult(0, 0, 0), DemoImageImporter.apply(connection, catalog));
        });
    }

    @Test
    void rollsBackImageRepairsAndNewProductsIfAnOrderUpdateFails() throws Exception {
        DemoImageCatalog catalog = DemoImageCatalog.load(Path.of("demo/product-images"));
        var toner = catalog.entries().stream().filter(entry -> entry.slug().equals("toner")).findFirst().orElseThrow();
        String oldImage = catalog.retiredImagePaths().stream().filter(path -> path.contains("demo-toner-2-")).findFirst().orElseThrow();
        int id = benchmarkIds(catalog.entries().indexOf(toner), 1).getFirst();
        int missing = catalog.entries().getLast().demoId();
        withIsolatedDatabase(connection -> {
            seedDemos(connection, catalog, Set.of(missing));
            putProduct(connection, id, benchmarkName(id, toner.item()), toner.category(), oldImage);
            execute(connection, "ALTER TABLE order_item DROP COLUMN created_at");
            assertThrows(SQLException.class, () -> DemoImageImporter.apply(connection, catalog));
            assertTrue(connection.getAutoCommit());
            assertEquals(oldImage, scalar(connection, "SELECT main_image FROM product WHERE id = " + id));
            assertEquals("0", scalar(connection, "SELECT COUNT(*) FROM product WHERE id = " + missing));
        });
    }

    private static void seedDemos(Connection connection, DemoImageCatalog catalog, Set<Integer> missing) throws SQLException {
        for (var entry : catalog.entries()) {
            if (!missing.contains(entry.demoId())) {
                putProduct(connection, entry.demoId(), entry.item(), entry.category(), entry.imagePath(entry.demoId()));
            }
        }
    }

    private static String legacyName(DemoImageCatalog.Entry entry, int id) {
        return entry.legacy().stream().filter(product -> product.id() == id).findFirst().orElseThrow().name();
    }

    private static List<Integer> benchmarkIds(int kind, int count) {
        List<Integer> ids = new ArrayList<>();
        for (int id = 1001; id <= 101000 && ids.size() < count; id++) {
            long hash = hash(id);
            if ((hash % 6) * 8 + (hash / 6) % 8 == kind) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String benchmarkName(int id, String item) {
        long hash = hash(id);
        String[] prefixes = { "新款", "经典", "轻奢", "简约", "加厚", "便携", "升级版", "国潮", "旗舰", "入门" };
        return prefixes[(int) ((hash / 48) % 10)] + item + " "
            + "ABCDEFGHJKLMNPQRSTUVWXYZ".charAt((int) ((hash / 480) % 24)) + (100 + (hash / 11520) % 900);
    }

    private static long hash(int id) {
        CRC32 hash = new CRC32();
        hash.update(Integer.toString(id).getBytes(StandardCharsets.UTF_8));
        return hash.getValue();
    }

    private static void putProduct(Connection connection, int id, String name, String category, String image) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO product (id,name,category,price,stock,sales,main_image,status,created_at)
            VALUES (?,?,?,123.45,17,9,?,0,'2026-10-01 10:00:00')
            """)) {
            statement.setInt(1, id);
            statement.setString(2, name);
            statement.setString(3, category);
            statement.setString(4, image);
            statement.executeUpdate();
        }
    }

    private static void putOrderItem(Connection connection, int id, int productId, String name, String createdAt, String image) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO order_item (id,product_id,product_name,created_at,product_image) VALUES (?,?,?,?,?)")) {
            statement.setInt(1, id);
            statement.setInt(2, productId);
            statement.setString(3, name);
            statement.setTimestamp(4, Timestamp.valueOf(createdAt));
            statement.setString(5, image);
            statement.executeUpdate();
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void withIsolatedDatabase(DatabaseCheck check) throws Exception {
        Properties settings = new Properties();
        if (Files.isRegularFile(Path.of(".env"))) {
            try (Reader reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) {
                settings.load(reader);
            }
        }
        String host = setting(settings, "DB_HOST", "localhost");
        if (!Set.of("localhost", "127.0.0.1", "::1").contains(host)) {
            throw new IllegalStateException("The demo image integration test requires a local MySQL instance");
        }
        String schema = "mall_demo_images_test_" + UUID.randomUUID().toString().replace("-", "");
        String jdbc = "jdbc:mysql://" + host + ":" + setting(settings, "DB_PORT", "3306")
            + "/?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        try (Connection connection = DriverManager.getConnection(jdbc, setting(settings, "DB_USERNAME", "root"), setting(settings, "DB_PASSWORD", ""))) {
            execute(connection, "CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            try {
                execute(connection, "USE `" + schema + "`");
                execute(connection, """
                    CREATE TABLE product (
                      id INT PRIMARY KEY, name VARCHAR(256) NOT NULL, subtitle VARCHAR(512), description TEXT,
                      category VARCHAR(32) NOT NULL, price DECIMAL(10,2) NOT NULL, stock INT NOT NULL,
                      sales INT NOT NULL, main_image VARCHAR(512), status INT NOT NULL, created_at DATETIME NOT NULL
                    ) ENGINE=InnoDB
                    """);
                execute(connection, """
                    CREATE TABLE order_item (
                      id INT PRIMARY KEY, product_id INT NOT NULL, product_name VARCHAR(256) NOT NULL,
                      product_image VARCHAR(512), created_at DATETIME NOT NULL
                    ) ENGINE=InnoDB
                    """);
                check.run(connection);
            } finally {
                connection.setAutoCommit(true);
                execute(connection, "DROP DATABASE `" + schema + "`");
            }
        }
    }

    private static String setting(Properties settings, String key, String defaultValue) {
        String environment = System.getenv(key);
        return environment == null ? settings.getProperty(key, defaultValue) : environment;
    }

    @FunctionalInterface
    private interface DatabaseCheck {
        void run(Connection connection) throws Exception;
    }
}
