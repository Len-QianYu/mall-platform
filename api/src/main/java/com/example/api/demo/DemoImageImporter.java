package com.example.api.demo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.example.api.demo.DemoImageCatalog.Entry;
import com.example.api.demo.DemoImageCatalog.Legacy;
import com.example.api.demo.DemoImageCatalog.Photo;
import com.example.api.demo.DemoImageCatalog.Snapshot;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

// 仅由初始化 CLI 显式调用，使用 Java 统一处理对象存储与业务数据。
public final class DemoImageImporter {

    private DemoImageImporter() { }

    public record DatabaseResult(int newProducts, int productImages, int orderImages) { }

    public record Plan(int missingDemoProducts, int productImagesToUpdate, int orderImagesToUpdate) { }

    public record UploadResult(int uploaded, int existing) { }

    private record Sql(String text, List<Object> arguments) { }

    private static final String SNAPSHOT_JOIN = " FROM `order_item` AS `oi` JOIN `product` AS `p` ON `p`.`id` = `oi`.`product_id` AND `p`.`name` = `oi`.`product_name`";

    public static Plan plan(Connection connection, DemoImageCatalog catalog) throws SQLException {
        int missingProducts = 0;
        int missingImages = 0;
        for (Entry entry : catalog.entries()) {
            missingProducts += count(connection, "SELECT COUNT(*) FROM `product` WHERE `id` = ?", List.of(entry.demoId())) == 0 ? 1 : 0;
            Sql eligibility = eligible(entry, catalog);
            missingImages += count(connection, "SELECT COUNT(*) FROM `product` WHERE " + eligibility.text, eligibility.arguments);
        }
        int snapshots = 0;
        for (var snapshot : catalog.snapshots()) {
            Sql eligibility = snapshotEligible(snapshot, catalog, true);
            snapshots += count(connection, "SELECT COUNT(*)" + SNAPSHOT_JOIN + " WHERE " + eligibility.text, eligibility.arguments);
        }
        return new Plan(missingProducts, missingImages, snapshots);
    }

    public static UploadResult upload(S3Client client, String bucket, DemoImageCatalog catalog) throws IOException {
        catalog.verifyAssets();
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
        int uploaded = 0;
        int existing = 0;
        for (Photo photo : catalog.allPhotos()) {
                String key = "images/" + photo.file();
                boolean found;
                try {
                    HeadObjectResponse metadata = client.headObject(request -> request.bucket(bucket).key(key));
                    verifyObject(client, bucket, key, metadata, photo);
                    found = true;
                } catch (S3Exception exception) {
                    if (exception.statusCode() != 404) {
                        throw exception;
                    }
                    found = false;
                }
                if (found) {
                    existing++;
                    continue;
                }
                try {
                    client.putObject(request -> request.bucket(bucket).key(key).contentType("image/webp")
                        .metadata(Map.of("sha256", photo.sha256())).ifNoneMatch("*"), RequestBody.fromFile(catalog.assetFile(photo)));
                    uploaded++;
                } catch (S3Exception exception) {
                    if (exception.statusCode() != 409 && exception.statusCode() != 412) {
                        throw exception;
                    }
                    existing++;
                }
                verifyObject(client, bucket, key, client.headObject(request -> request.bucket(bucket).key(key)), photo);
        }
        return new UploadResult(uploaded, existing);
    }

    private static void verifyObject(S3Client client, String bucket, String key, HeadObjectResponse metadata, Photo photo) throws IOException {
        if (metadata.contentLength() != photo.bytes() || !"image/webp".equals(metadata.contentType())) {
            throw new IOException("RustFS 已有对象的大小或类型不同，保留原对象：" + key);
        }
        String digest = metadata.metadata().get("sha256");
        if (digest == null) {
            try (var input = client.getObject(request -> request.bucket(bucket).key(key))) {
                digest = DemoImageCatalog.checksum(input);
            }
        }
        if (!photo.sha256().equals(digest)) {
            throw new IOException("RustFS 已有对象校验失败，保留原对象：" + key);
        }
    }

    public static DatabaseResult apply(Connection connection, DemoImageCatalog catalog) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("初始化工具需要独立数据库连接");
        }
        connection.setAutoCommit(false);
        try {
            int newProducts = 0;
            int productImages = 0;
            for (Entry entry : catalog.entries()) {
                newProducts += execute(connection, """
                    INSERT INTO `product` (`id`,`name`,`subtitle`,`description`,`category`,`price`,`stock`,`sales`,`main_image`,`status`,`created_at`)
                    SELECT ?,?,?,?,?,?,100,?,?,1,'2026-11-02 10:00:00'
                    WHERE NOT EXISTS (SELECT 1 FROM `product` WHERE `id` = ?)
                    """, List.of(entry.demoId(), entry.item(), entry.subtitle(), entry.description(), entry.category(),
                    entry.price(), 20 + (entry.demoId() - 101) * 7, entry.imagePath(entry.demoId()), entry.demoId()));
                Sql selection = imageSelection(entry);
                Sql eligibility = eligible(entry, catalog);
                List<Object> arguments = new ArrayList<>(selection.arguments);
                arguments.addAll(eligibility.arguments);
                productImages += execute(connection,
                    "UPDATE `product` SET `main_image` = " + selection.text + " WHERE " + eligibility.text, arguments);
            }
            int snapshots = 0;
            for (var snapshot : catalog.snapshots()) {
                Sql eligibility = snapshotEligible(snapshot, catalog, false);
                snapshots += execute(connection,
                    "UPDATE `order_item` AS `oi` JOIN `product` AS `p` ON `p`.`id` = `oi`.`product_id` AND `p`.`name` = `oi`.`product_name`"
                    + " SET `oi`.`product_image` = `p`.`main_image` WHERE " + eligibility.text, eligibility.arguments);
            }
            connection.commit();
            return new DatabaseResult(newProducts, productImages, snapshots);
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static Sql imageSelection(Entry entry) {
        StringBuilder sql = new StringBuilder();
        List<Object> arguments = new ArrayList<>();
        for (Legacy legacy : entry.legacy()) {
            if (!legacy.imageSlug().equals(entry.slug())) {
                if (sql.isEmpty()) {
                    sql.append("CASE ");
                }
                sql.append("WHEN `id` = ? THEN ? ");
                arguments.add(legacy.id());
                arguments.add(entry.imagePath(legacy.id()));
            }
        }
        boolean override = !sql.isEmpty();
        if (override) {
            sql.append("ELSE ");
        }
        sql.append("CASE MOD(CRC32(CONCAT(`id`, '#image')), 3) WHEN 0 THEN ? WHEN 1 THEN ? ELSE ? END");
        entry.photos().forEach(photo -> arguments.add("/images/" + photo.file()));
        if (override) {
            sql.append(" END");
        }
        return new Sql(sql.toString(), arguments);
    }

    private static String replaceableImage(String column, DemoImageCatalog catalog) {
        return "(" + column + " IS NULL OR " + column + " = '' OR " + column + " IN ("
            + String.join(",", Collections.nCopies(catalog.managedImagePaths().size(), "?")) + "))";
    }

    private static Sql eligible(Entry entry, DemoImageCatalog catalog) {
        // 与 bench-product.sql 的名称生成规则一致；不能把范围内任意含关键字的商品当作演示商品。
        StringBuilder sql = new StringBuilder("""
            `category` = ? AND ((`id` BETWEEN 1001 AND 101000 AND `name` = CONCAT(
              ELT(MOD(CRC32(`id`) DIV 48, 10) + 1, '新款','经典','轻奢','简约','加厚','便携','升级版','国潮','旗舰','入门'),
              ?, ' ', SUBSTRING('ABCDEFGHJKLMNPQRSTUVWXYZ', MOD(CRC32(`id`) DIV 480, 24) + 1, 1),
              100 + MOD(CRC32(`id`) DIV 11520, 900))) OR (`id` = ? AND `name` = ?)
            """);
        List<Object> arguments = new ArrayList<>(List.of(entry.category(), entry.item(), entry.demoId(), entry.item()));
        for (Legacy ignored : entry.legacy()) {
            sql.append(" OR (`id` = ? AND `name` = ?)");
        }
        for (Legacy legacy : entry.legacy()) {
            arguments.add(legacy.id());
            arguments.add(legacy.name());
        }
        sql.append(") AND ").append(replaceableImage("`main_image`", catalog));
        arguments.addAll(catalog.managedImagePaths());
        Sql selection = imageSelection(entry);
        sql.append(" AND NOT (`main_image` <=> ").append(selection.text).append(')');
        arguments.addAll(selection.arguments);
        return new Sql(sql.toString(), arguments);
    }

    private static Sql snapshotEligible(Snapshot snapshot, DemoImageCatalog catalog, boolean planning) {
        Entry entry = catalog.entries().stream()
            .filter(candidate -> candidate.legacy().stream().anyMatch(legacy -> legacy.id() == snapshot.productId() && legacy.name().equals(snapshot.name())))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("演示订单快照没有对应的商品：" + snapshot.name()));
        StringBuilder sql = new StringBuilder("`oi`.`id` = ? AND `oi`.`product_id` = ? AND `oi`.`product_name` = ? AND `oi`.`created_at` = ? AND `p`.`category` = ? AND ");
        List<Object> arguments = new ArrayList<>(List.of(snapshot.id(), snapshot.productId(), snapshot.name(),
            Timestamp.valueOf(snapshot.createdAt()), entry.category()));
        sql.append(replaceableImage("`oi`.`product_image`", catalog)).append(" AND NOT (`oi`.`product_image` <=> ");
        arguments.addAll(catalog.managedImagePaths());
        if (planning) {
            sql.append("CASE WHEN ").append(replaceableImage("`p`.`main_image`", catalog))
                .append(" THEN ? ELSE `p`.`main_image` END");
            arguments.addAll(catalog.managedImagePaths());
            arguments.add(entry.imagePath(snapshot.productId()));
        } else {
            sql.append("`p`.`main_image`");
        }
        sql.append(')');
        if (!planning) {
            sql.append(" AND `p`.`main_image` IS NOT NULL AND `p`.`main_image` <> ''");
        }
        return new Sql(sql.toString(), arguments);
    }

    private static int count(Connection connection, String sql, List<?> arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int execute(Connection connection, String sql, List<?> arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            return statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, List<?> arguments) throws SQLException {
        for (int index = 0; index < arguments.size(); index++) {
            statement.setObject(index + 1, arguments.get(index));
        }
    }

}
