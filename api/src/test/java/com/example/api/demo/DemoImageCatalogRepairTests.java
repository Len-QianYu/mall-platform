package com.example.api.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class DemoImageCatalogRepairTests {

    private static final Path FIXTURES = Path.of("demo/product-images");

    @TempDir(factory = WorkspaceTempDirectory.class)
    Path temporary;

    @Test
    void validatesThePhotosAndUsesTheActualLegacyBookTitles() throws Exception {
        DemoImageCatalog catalog = DemoImageCatalog.load(FIXTURES);
        catalog.verifyAssets();
        assertEquals(48, catalog.entries().size());
        assertEquals(150, catalog.allPhotos().size());
        for (int id : new int[] { 15, 16 }) {
            var entry = catalog.entryFor(id);
            var legacy = entry.legacy().stream().filter(product -> product.id() == id).findFirst().orElseThrow();
            assertTrue(legacy.photos().stream().allMatch(photo -> legacy.name().equals(photo.productItem())));
            assertTrue(legacy.photos().stream().allMatch(photo -> "book-cover".equals(photo.sourceType())));
            assertNotEquals(entry.imagePath(entry.demoId()), entry.imagePath(id));
        }
        assertTrue(catalog.imageGroups().stream().filter(group -> group.source().type().equals("book-cover"))
            .allMatch(group -> !group.item().contains("?")));
    }

    @Test
    void rejectsAPhotoFromAnotherProductEvenWithAValidChecksum() throws Exception {
        Path root = copyDefinitions();
        JsonMapper mapper = JsonMapper.builder().build();
        var manifest = mapper.readTree(root.resolve("manifest.json").toFile());
        ObjectNode first = (ObjectNode) manifest.get("items").get(0).get("photos").get(0);
        first.put("productItem", "移动电源");
        mapper.writeValue(root.resolve("manifest.json").toFile(), manifest);
        assertThrows(IOException.class, () -> DemoImageCatalog.load(root));
    }

    @Test
    void rejectsAnUnreviewedSearchResult() throws Exception {
        Path root = copyDefinitions();
        JsonMapper mapper = JsonMapper.builder().build();
        var manifest = mapper.readTree(root.resolve("manifest.json").toFile());
        ObjectNode first = (ObjectNode) manifest.get("items").get(0).get("photos").get(0);
        first.put("title", "File:Hooded crow.jpg");
        mapper.writeValue(root.resolve("manifest.json").toFile(), manifest);
        assertThrows(IOException.class, () -> DemoImageCatalog.load(root));
    }

    @Test
    void rejectsALegacyBookMappedToAnUnrelatedTitle() throws Exception {
        Path root = copyDefinitions();
        JsonMapper mapper = JsonMapper.builder().build();
        var definitions = mapper.readTree(root.resolve("catalog.json").toFile());
        for (var kind : definitions.get("items")) {
            for (var legacy : kind.get("legacy")) {
                if (legacy.get("id").asInt() == 15) {
                    ((ObjectNode) legacy).put("imageSlug", "ai-modern-book");
                }
            }
        }
        mapper.writeValue(root.resolve("catalog.json").toFile(), definitions);
        assertThrows(IOException.class, () -> DemoImageCatalog.load(root));
    }

    @Test
    void detectsChangedBytesBeforeAnUpload() throws Exception {
        Path root = copyDefinitions();
        DemoImageCatalog catalog = DemoImageCatalog.load(root);
        var photo = catalog.allPhotos().getFirst();
        Files.createDirectories(root.resolve("assets"));
        byte[] bytes = Files.readAllBytes(FIXTURES.resolve("assets").resolve(photo.file()));
        bytes[bytes.length - 1] ^= 1;
        Files.write(root.resolve("assets").resolve(photo.file()), bytes);
        IOException failure = assertThrows(IOException.class, catalog::verifyAssets);
        assertTrue(failure.getMessage().contains("校验失败"));
    }

    private Path copyDefinitions() throws IOException {
        Path root = Files.createDirectory(temporary.resolve("fixtures"));
        for (String file : new String[] { "catalog.json", "manifest.json", "retired-manifest.json" }) {
            if (Files.isRegularFile(FIXTURES.resolve(file))) {
                Files.copy(FIXTURES.resolve(file), root.resolve(file));
            }
        }
        return root;
    }

    public static class WorkspaceTempDirectory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context) throws IOException {
            Path root = Path.of("target/demo-image-tests").toAbsolutePath();
            Files.createDirectories(root);
            return Files.createTempDirectory(root, "fixtures-");
        }
    }
}
