package com.example.api.demo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.json.JsonMapper;

// 无 Spring Bean 或自动执行入口，仅由显式初始化工具使用。
public record DemoImageCatalog(Path assetRoot, List<Entry> entries, List<Snapshot> snapshots,
                               List<ImageGroup> imageGroups, List<String> retiredImagePaths) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Definitions(int version, int variantsPerItem, List<Definition> items,
                               List<Snapshot> legacyOrderItems, List<ImageDefinition> additionalImageItems) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Definition(String slug, String item, String category, BigDecimal price, String subtitle,
                              List<Legacy> legacy, ImageSource imageSource) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ImageDefinition(String slug, String item, String category, ImageSource imageSource) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageSource(String type, List<String> titles) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Manifest(int version, List<Images> items) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Images(String slug, List<Photo> photos) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Photo(String file, String sha256, long bytes, int width, int height,
                        String author, String license, String licenseUrl, String sourceUrl,
                        String title, String productItem, String sourceType) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Legacy(int id, String name, String imageSlug, List<Photo> photos) { }

    public record Snapshot(int id, int productId, String name, String createdAt) { }

    public record ImageGroup(String slug, String item, ImageSource source, List<Photo> photos) { }

    public record Entry(int demoId, String slug, String item, String category, BigDecimal price,
                        String subtitle, List<Legacy> legacy, List<Photo> photos) {

        public String description() {
            return this.item + "适合日常使用与选购。" + this.subtitle + "。请按个人需求选择，使用和保养请参考产品说明。";
        }

        public List<Photo> photosFor(int productId) {
            return this.legacy.stream().filter(product -> product.id == productId)
                .findFirst().map(Legacy::photos).orElse(this.photos);
        }

        public String imagePath(int productId) {
            return "/images/" + photosFor(productId).get(variant(productId)).file;
        }

    }

    public DemoImageCatalog {
        assetRoot = assetRoot.toAbsolutePath().normalize();
        entries = List.copyOf(entries);
        snapshots = List.copyOf(snapshots);
        imageGroups = List.copyOf(imageGroups);
        retiredImagePaths = List.copyOf(retiredImagePaths);
    }

    public static DemoImageCatalog load(Path root) throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        Definitions definitions = mapper.readValue(root.resolve("catalog.json").toFile(), Definitions.class);
        Manifest manifest = mapper.readValue(root.resolve("manifest.json").toFile(), Manifest.class);
        int additional = definitions.additionalImageItems == null ? 0 : definitions.additionalImageItems.size();
        if (definitions.version != 1 || definitions.variantsPerItem != 3 || manifest.version != 1
            || definitions.items == null || definitions.items.size() != 48
            || manifest.items == null || manifest.items.size() != 48 + additional
            || definitions.legacyOrderItems == null) {
            throw new IOException("演示图片清单不完整：应为 48 个品类及指定的独立商品图片组，每组 3 张图片");
        }
        Map<String, Images> images;
        try {
            images = manifest.items.stream().collect(Collectors.toMap(Images::slug, Function.identity()));
        } catch (IllegalStateException exception) {
            throw new IOException("图片清单中有重复品类", exception);
        }
        List<ImageGroup> groups = new ArrayList<>();
        var slugs = new HashSet<String>();
        for (Definition definition : definitions.items) {
            groups.add(group(definition.slug, definition.item, definition.imageSource, images, slugs));
        }
        if (definitions.additionalImageItems != null) {
            for (ImageDefinition definition : definitions.additionalImageItems) {
                groups.add(group(definition.slug, definition.item, definition.imageSource, images, slugs));
            }
        }
        Map<String, ImageGroup> groupsBySlug = groups.stream()
            .collect(Collectors.toMap(ImageGroup::slug, Function.identity()));
        List<Entry> entries = new ArrayList<>();
        var legacyIds = new HashSet<Integer>();
        for (int index = 0; index < definitions.items.size(); index++) {
            Definition definition = definitions.items.get(index);
            if (definition.category == null || definition.category.isBlank()
                || definition.price == null || definition.price.signum() <= 0
                || definition.subtitle == null || definition.legacy == null) {
                throw new IOException("商品类型无效：" + definition.slug);
            }
            List<Legacy> legacy = new ArrayList<>();
            for (Legacy product : definition.legacy) {
                ImageGroup selected = groupsBySlug.get(product.imageSlug == null ? definition.slug : product.imageSlug);
                if (product.id <= 0 || product.name == null || product.name.isBlank() || !legacyIds.add(product.id)
                    || selected == null || (product.imageSlug != null && !selected.item.equals(product.name))) {
                    throw new IOException("独立商品图片与商品名称不匹配：" + product.name);
                }
                legacy.add(new Legacy(product.id, product.name, selected.slug, selected.photos));
            }
            entries.add(new Entry(101 + index, definition.slug, definition.item, definition.category,
                definition.price, definition.subtitle, List.copyOf(legacy), groupsBySlug.get(definition.slug).photos));
        }
        List<String> retired = new ArrayList<>();
        Path retiredManifest = root.resolve("retired-manifest.json");
        if (Files.isRegularFile(retiredManifest)) {
            Manifest previous = mapper.readValue(retiredManifest.toFile(), Manifest.class);
            if (previous.version != 1 || previous.items == null) {
                throw new IOException("旧演示图片清单无效");
            }
            for (Images previousGroup : previous.items) {
                if (previousGroup.photos == null) {
                    throw new IOException("旧演示图片清单无效");
                }
                for (Photo photo : previousGroup.photos) {
                    verifyFilename(photo);
                    retired.add("/images/" + photo.file);
                }
            }
        }
        return new DemoImageCatalog(root.resolve("assets"), entries, definitions.legacyOrderItems, groups, retired);
    }

    private static ImageGroup group(String slug, String item, ImageSource source, Map<String, Images> images,
                                    Set<String> slugs) throws IOException {
        Images group = images.get(slug);
        if (slug == null || !slug.matches("[a-z0-9-]+") || !slugs.add(slug)
            || item == null || item.isBlank() || source == null || source.type == null
            || !Set.of("commons", "book-cover").contains(source.type)
            || ("commons".equals(source.type) && (source.titles == null || source.titles.size() != 3))
            || group == null || group.photos == null || group.photos.size() != 3) {
            throw new IOException("商品类型或固定素材映射无效：" + slug);
        }
        for (int index = 0; index < group.photos.size(); index++) {
            Photo photo = group.photos.get(index);
            verifyFilename(photo);
            String expectedTitle = "commons".equals(source.type) ? source.titles.get(index)
                : "Original demo cover: " + item + " / " + (index + 1);
            if (!photo.file.equals("demo-" + slug + "-" + (index + 1) + "-" + photo.sha256.substring(0, 12) + ".webp")
                || !item.equals(photo.productItem) || !source.type.equals(photo.sourceType)
                || expectedTitle == null || !expectedTitle.equals(photo.title)) {
                throw new IOException("图片与商品名称或核对过的来源不匹配：" + photo.file);
            }
            if ("book-cover".equals(source.type)
                && !("book-covers/" + slug + "-" + (index + 1) + ".svg").equals(photo.sourceUrl)) {
                throw new IOException("图书封面源文件映射无效：" + photo.file);
            }
        }
        return new ImageGroup(slug, item, source, List.copyOf(group.photos));
    }

    private static void verifyFilename(Photo photo) throws IOException {
        if (photo == null || photo.sha256 == null || !photo.sha256.matches("[0-9a-f]{64}") || photo.file == null
            || !photo.file.matches("demo-[a-z0-9-]+-[0-9a-f]{12}\\.webp")
            || !photo.file.endsWith("-" + photo.sha256.substring(0, 12) + ".webp")) {
            throw new IOException("演示图片文件名或校验码无效：" + (photo == null ? null : photo.file));
        }
    }

    public List<Photo> allPhotos() {
        return this.imageGroups.stream().flatMap(group -> group.photos.stream()).toList();
    }

    public List<String> managedImagePaths() {
        var paths = new HashSet<>(this.retiredImagePaths);
        allPhotos().forEach(photo -> paths.add("/images/" + photo.file));
        return paths.stream().sorted().toList();
    }

    public Entry entryFor(int productId) {
        return this.entries.stream().filter(entry -> entry.demoId == productId
            || entry.legacy.stream().anyMatch(product -> product.id == productId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未定义的演示商品：" + productId));
    }

    public void verifyAssets() throws IOException {
        var files = new HashSet<String>();
        for (Photo photo : allPhotos()) {
            if (!files.add(photo.file) || photo.bytes <= 0 || photo.bytes > 5 * 1024 * 1024
                || photo.width != 600 || photo.height != 600
                || photo.author == null || photo.author.isBlank() || photo.sourceUrl == null || photo.sourceUrl.isBlank()
                || photo.licenseUrl == null || photo.licenseUrl.isBlank() || photo.license == null
                || !photo.license.matches("(?i)CC BY(?:-SA)? [1-4]\\.0|CC0(?: 1\\.0)?|Public domain")) {
                throw new IOException("图片或许可元数据无效：" + photo.file);
            }
            if ("book-cover".equals(photo.sourceType)
                && !Files.isRegularFile(this.assetRoot.getParent().resolve(photo.sourceUrl), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("图书封面 SVG 源文件缺失：" + photo.file);
            }
            Path file = assetFile(photo);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != photo.bytes) {
                throw new IOException("图片缺失或大小不一致：" + photo.file);
            }
            try (InputStream input = Files.newInputStream(file)) {
                if (!checksum(input).equals(photo.sha256)) {
                    throw new IOException("图片内容校验失败：" + photo.file);
                }
            }
        }
    }

    public Path assetFile(Photo photo) throws IOException {
        Path file = this.assetRoot.resolve(photo.file).normalize();
        if (!file.getParent().equals(this.assetRoot)) {
            throw new IOException("图片路径越界");
        }
        return file;
    }

    public static int variant(int productId) {
        CRC32 crc = new CRC32();
        crc.update((productId + "#image").getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % 3);
    }

    public static String checksum(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
