package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.entity.ProductEntity;
import com.example.api.entity.ProductFavoriteEntity;
import com.example.api.entity.ProductNotificationEntity;
import com.example.api.entity.ProductNotificationEventEntity;
import com.example.api.entity.UserEntity;
import com.example.api.mapper.ProductFavoriteMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mapper.ProductNotificationEventMapper;
import com.example.api.mapper.ProductNotificationMapper;
import com.example.api.mq.ProductNotificationListener;
import com.example.api.service.ProductService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.JwtUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 使用专用 Topic/消费组，绝不消费开发环境的通知；退出前只清理本测试的随机商品数据。
@EnabledIfEnvironmentVariable(named = "PRODUCT_NOTIFICATION_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false",
    "product-notification.delivery-enabled=false", "debug=false", "logging.level.root=WARN"
})
class ProductNotificationMqIntegrationTests {
    private static final String TOPIC = "product-notification-test";
    private static final String GROUP = "product-notification-test-consumer";
    @LocalServerPort private int port;
    @Value("${rocketmq.endpoints}") private String endpoints;
    @Autowired private ProductMapper products;
    @Autowired private ProductFavoriteMapper favorites;
    @Autowired private ProductNotificationEventMapper events;
    @Autowired private ProductNotificationMapper notifications;
    @Autowired private ProductService productService;
    @Autowired private ProductNotificationListener listener;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JwtUtils jwt;
    @Autowired private JsonMapper json;
    @MockitoBean private Producer applicationProducer;
    @MockitoBean private PushConsumer applicationConsumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private CacheManager caches;

    @BeforeEach
    void caches() { when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class)); }

    @Test
    void rocketMqDeliversAllThreeChangesOverAuthenticatedWebSocketAndKeepsOfflineHistory() throws Exception {
        int owner = ThreadLocalRandom.current().nextInt(100000000, 1000000000);
        String token = this.token(owner);
        String offlineToken = this.token(owner + 1);
        ProductEntity product = new ProductEntity();
        product.setName("mq-notification-" + UUID.randomUUID());
        product.setCategory("通知链路测试");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(10);
        product.setSales(0);
        product.setStatus(0);
        this.products.insert(product);
        WebSocket socket = null;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        try {
            assertEquals(200, this.post(http, "/favorite", token,
                "{\"productId\":" + product.getId() + ",\"favorite\":true}").statusCode());
            assertEquals(200, this.post(http, "/favorite", offlineToken,
                "{\"productId\":" + product.getId() + ",\"favorite\":true}").statusCode());
            HttpResponse<String> ticketResponse = this.post(http, "/notification/socket-ticket", token, "{}");
            assertEquals(200, ticketResponse.statusCode());
            String ticket = this.json.readTree(ticketResponse.body()).path("payload").path("ticket").asText();
            URI socketUri = URI.create("ws://localhost:" + this.port + "/ws/notifications?ticket=" + ticket);
            SocketMessages messages = new SocketMessages();
            socket = http.newWebSocketBuilder().header("Origin", "http://localhost:5173")
                .connectTimeout(Duration.ofSeconds(5)).buildAsync(socketUri, messages).join();
            messages.await("ready");

            CompletionException replay = assertThrows(CompletionException.class, () -> http.newWebSocketBuilder()
                .header("Origin", "http://localhost:5173").buildAsync(socketUri, new SocketMessages()).join());
            assertEquals(401, assertInstanceOf(WebSocketHandshakeException.class, replay.getCause()).getResponse().statusCode());

            ClientConfiguration configuration = ClientConfiguration.newBuilder().setEndpoints(this.endpoints).build();
            ClientServiceProvider provider = ClientServiceProvider.loadService();
            try (Producer producer = provider.newProducerBuilder().setClientConfiguration(configuration).setTopics(TOPIC).build();
                PushConsumer consumer = provider.newPushConsumerBuilder().setClientConfiguration(configuration)
                    .setConsumerGroup(GROUP).setSubscriptionExpressions(Map.of(TOPIC, FilterExpression.SUB_ALL))
                    .setMessageListener(this.listener).build()) {
                Long listed = this.change(product, () -> this.productService.updateStatus(product.getId(), 1));
                this.publish(provider, producer, listed);
                assertEquals("LISTED", messages.await("notification").path("notification").path("type").asText());

                product.setStatus(1);
                product.setPrice(new BigDecimal("80.00"));
                Long dropped = this.change(product, () -> this.productService.update(product));
                this.publish(provider, producer, dropped);
                JsonNode drop = messages.await("notification").path("notification");
                assertEquals("PRICE_DROP", drop.path("type").asText());
                assertEquals(100, drop.path("oldPrice").asInt());
                assertEquals(80, drop.path("newPrice").asInt());

                product.setPrice(new BigDecimal("120.00"));
                Long risen = this.change(product, () -> this.productService.update(product));
                this.publish(provider, producer, risen);
                JsonNode rise = messages.await("notification").path("notification");
                assertEquals("PRICE_RISE", rise.path("type").asText());
                assertEquals(80, rise.path("oldPrice").asInt());
                assertEquals(120, rise.path("newPrice").asInt());

                assertEquals(3, this.get(http, "/notification/unread-count", token).path("payload").path("unreadCount").asInt());
                assertEquals(3, this.get(http, "/notification", offlineToken).path("payload").path("notifications").size());
                this.post(http, "/notification/read-all", token, "{}");
                messages.await("refresh");
                assertEquals(0, this.get(http, "/notification/unread-count", token).path("payload").path("unreadCount").asInt());
                assertEquals(3, this.get(http, "/notification/unread-count", offlineToken).path("payload").path("unreadCount").asInt());
            }
        } finally {
            if (socket != null) socket.abort();
            http.close();
            this.notifications.delete(Wrappers.<ProductNotificationEntity>lambdaQuery()
                .eq(ProductNotificationEntity::getProductId, product.getId()));
            this.events.delete(Wrappers.<ProductNotificationEventEntity>lambdaQuery()
                .eq(ProductNotificationEventEntity::getProductId, product.getId()));
            this.favorites.delete(Wrappers.<ProductFavoriteEntity>lambdaQuery()
                .eq(ProductFavoriteEntity::getProductId, product.getId()));
            this.products.deleteById(product.getId());
        }
    }

    private Long change(ProductEntity product, Runnable modification) {
        return new TransactionTemplate(this.transactions).execute(transaction -> {
            modification.run();
            ProductNotificationEventEntity event = this.events.selectList(Wrappers.<ProductNotificationEventEntity>lambdaQuery()
                .eq(ProductNotificationEventEntity::getProductId, product.getId())
                .orderByDesc(ProductNotificationEventEntity::getId)).getFirst();
            // 同事务预留给专用测试队列，避免已运行的开发实例把这个临时事件发送到正式 Topic。
            this.events.markPublished(event.getId());
            return event.getId();
        });
    }

    private void publish(ClientServiceProvider provider, Producer producer, Long eventId) throws Exception {
        producer.send(provider.newMessageBuilder().setTopic(TOPIC).setKeys("integration-" + eventId)
            .setBody(eventId.toString().getBytes(StandardCharsets.UTF_8)).build());
    }

    private String token(int userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("notify-test");
        user.setRole("user");
        return this.jwt.generateToken(user);
    }

    private HttpResponse<String> post(HttpClient http, String path, String token, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path))
            .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(HttpClient http, String path, String token) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path))
            .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return this.json.readTree(response.body());
    }

    private class SocketMessages implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder fragments = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) { webSocket.request(1); }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.fragments.append(data);
            if (last) {
                this.messages.add(this.fragments.toString());
                this.fragments.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        JsonNode await(String type) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                String message = this.messages.poll(1, TimeUnit.SECONDS);
                if (message == null) continue;
                JsonNode parsed = json.readTree(message);
                if (type.equals(parsed.path("type").asText())) return parsed;
            }
            throw new AssertionError("未收到预期的 WebSocket 消息：" + type);
        }
    }
}
