package com.example.api.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

// 按文件头（魔数）识别图片格式：文件名和 Content-Type 都由客户端决定、可以伪造，只有文件内容可信。
// 只收这几种位图；SVG 不收，它本质是 XML，可以内嵌脚本，从后端地址打开时会在本站执行
public final class ImageFormat {

    private ImageFormat() {
    }

    // 返回格式对应的扩展名（存盘时用它，不用原文件名的扩展名）；不是支持的图片时返回空
    public static Optional<String> detect(MultipartFile file) throws IOException {
        byte[] head;
        try (InputStream input = file.getInputStream()) {
            head = input.readNBytes(12);
        }

        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("jpg");
        }
        if (startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("png");
        }
        if (startsWith(head, 0, 'G', 'I', 'F', '8')) {
            return Optional.of("gif");
        }
        // WEBP：RIFF 容器，第 8 个字节起是 WEBP
        if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of("webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

}
