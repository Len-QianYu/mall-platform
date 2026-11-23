package com.example.api.configuration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

class UploadConfigurationTests {

    @Test
    void sendsLargeObjectsWithoutExpectContinueOrChunkedEncoding() throws Exception {
        byte[] image = new byte[2 * 1024 * 1024];
        AtomicReference<String> expect = new AtomicReference<>();
        AtomicReference<String> encoding = new AtomicReference<>();
        AtomicReference<String> length = new AtomicReference<>();
        AtomicReference<byte[]> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            expect.set(exchange.getRequestHeaders().getFirst("Expect"));
            encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            length.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            try {
                if (expect.get() != null) {
                    // S3 兼容服务不能完成 100-continue 握手时，客户端应直接发送已知长度的内容。
                    byte[] error = "<Error><Code>UnexpectedExpectHeader</Code></Error>"
                        .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/xml");
                    exchange.sendResponseHeaders(417, error.length);
                    exchange.getResponseBody().write(error);
                    return;
                }
                received.set(exchange.getRequestBody().readAllBytes());
                exchange.getResponseHeaders().set("ETag", "\"test-etag\"");
                exchange.sendResponseHeaders(200, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (S3Client client = new UploadConfiguration().rustfsClient(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "test-access-key", "test-secret-key", "us-east-1")) {
            client.putObject(request -> request.bucket("upload-protocol-test")
                .key("images/large.png").contentType("image/png"), RequestBody.fromBytes(image));
            assertNull(expect.get());
            assertNull(encoding.get());
            assertEquals(Integer.toString(image.length), length.get());
            assertArrayEquals(image, received.get());
        } finally {
            server.stop(0);
        }
    }
}
