package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.entity.UserEntity;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.OrderNotificationMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mapper.UserMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.OrderService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.JwtUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 随机端口和临时账号验证真实 HTTP / MySQL / Redis / WebSocket；不发送开发环境 MQ 消息。
@EnabledIfEnvironmentVariable(named = "ORDER_NOTIFICATION_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false",
    "product-notification.delivery-enabled=false", "product-notification.allowed-origins=http://localhost:5173",
    "debug=false", "logging.level.root=WARN"
})
class OrderNotificationIntegrationTests {
    @LocalServerPort private int port;
    @Autowired private UserMapper users;
    @Autowired private ProductMapper products;
    @Autowired private OrderMapper orders;
    @Autowired private OrderItemMapper items;
    @Autowired private OrderNotificationMapper notifications;
    @Autowired private OrderService orderService;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JwtUtils jwt;
    @Autowired private JsonMapper json;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutProducer timeoutMessages;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private CacheManager caches;

    private final List<UserEntity> testUsers = new ArrayList<>();
    private final List<WebSocket> sockets = new ArrayList<>();

    @Test
    void submittedOrderReachesBothAdminRolesOnceAndRestoresOfflineHistoryWithoutLeakingToBuyers() throws Exception {
        when(this.caches.getCache(anyString())).thenReturn(mock(Cache.class));
        ProductEntity product = null;
        UserEntity buyer = null;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        try {
            buyer = this.user("user", 1);
            UserEntity admin = this.user("admin", 1);
            UserEntity superAdmin = this.user("super_admin", 1);
            UserEntity offlineAdmin = this.user("admin", 1);
            UserEntity disabledAdmin = this.user("admin", 0);
            UserEntity disabledSuperAdmin = this.user("super_admin", 0);
            product = this.product();
            String buyerToken = this.jwt.generateToken(buyer);
            String adminToken = this.jwt.generateToken(admin);
            String superToken = this.jwt.generateToken(superAdmin);
            String offlineToken = this.jwt.generateToken(offlineAdmin);
            SocketMessages adminMessages = this.socket(http, adminToken);
            SocketMessages secondAdminTab = this.socket(http, adminToken);
            SocketMessages superMessages = this.socket(http, superToken);
            SocketMessages buyerMessages = this.socket(http, buyerToken);

            TradeRequests.OrderCreate request = this.request(product.getId(), 2);
            JsonNode result = this.post(http, "/order/create", buyerToken, this.json.writeValueAsString(request));
            assertTrue(result.path("status").asBoolean());
            JsonNode order = result.path("payload").path("order");
            long orderId = order.path("id").asLong();
            String orderNo = order.path("orderNo").asText();
            assertTrue(orderId > 0);
            JsonNode received = adminMessages.await("order_notification").path("notification");
            JsonNode superReceived = superMessages.await("order_notification").path("notification");
            assertEquals(orderId, secondAdminTab.await("order_notification").path("notification").path("orderId").asLong());
            for (JsonNode notification : List.of(received, superReceived)) {
                assertEquals("NEW_ORDER", notification.path("type").asText());
                assertEquals(orderNo, notification.path("orderNo").asText());
                assertEquals(orderId, notification.path("orderId").asLong());
                assertEquals(buyer.getId().intValue(), notification.path("buyerId").asInt());
                assertEquals(buyer.getUsername(), notification.path("buyerUsername").asText());
                assertEquals("通知验证买家", notification.path("buyerName").asText());
                assertEquals(0, new BigDecimal("33.60").compareTo(notification.path("totalAmount").decimalValue()));
                assertFalse(notification.has("userId"));
            }
            assertNull(buyerMessages.messages.poll(400, TimeUnit.MILLISECONDS));
            JsonNode offline = this.get(http, "/admin/notification", offlineToken);
            assertEquals(1, offline.path("payload").path("unreadCount").asInt());
            assertEquals(orderNo, offline.path("payload").path("notifications").get(0).path("orderNo").asText());
            assertEquals(0L, this.notifications.selectCount(Wrappers.<OrderNotificationEntity>lambdaQuery()
                .in(OrderNotificationEntity::getUserId, List.of(buyer.getId(), disabledAdmin.getId(), disabledSuperAdmin.getId()))));
            assertEquals(403, this.http(http, "/admin/notification", buyerToken, null).statusCode());
            JsonNode detail = this.get(http, "/admin/order/" + orderId, adminToken);
            assertEquals(orderNo, detail.path("payload").path("order").path("orderNo").asText());

            JsonNode repeated = this.post(http, "/order/create", buyerToken, this.json.writeValueAsString(request));
            assertEquals(orderId, repeated.path("payload").path("order").path("id").asLong());
            assertNull(adminMessages.messages.poll(400, TimeUnit.MILLISECONDS));
            assertNull(superMessages.messages.poll(400, TimeUnit.MILLISECONDS));
            assertEquals(1L, this.notifications.selectCount(Wrappers.<OrderNotificationEntity>lambdaQuery()
                .eq(OrderNotificationEntity::getOrderId, orderId).eq(OrderNotificationEntity::getUserId, admin.getId())));
            assertEquals(8, this.products.selectById(product.getId()).getStock());

            this.post(http, "/admin/notification/read", adminToken,
                this.json.writeValueAsString(Map.of("id", received.path("id").asLong(), "userId", superAdmin.getId())));
            adminMessages.await("order_refresh");
            secondAdminTab.await("order_refresh");
            assertEquals(0, this.get(http, "/admin/notification/unread-count", adminToken).path("payload").path("unreadCount").asInt());
            assertEquals(1, this.get(http, "/admin/notification/unread-count", superToken).path("payload").path("unreadCount").asInt());
            this.post(http, "/admin/notification/read-all", adminToken, "{}");
            adminMessages.await("order_refresh");
            secondAdminTab.await("order_refresh");
            assertEquals(1, this.get(http, "/admin/notification/unread-count", offlineToken).path("payload").path("unreadCount").asInt());

            JsonNode failed = this.post(http, "/order/create", buyerToken,
                this.json.writeValueAsString(this.request(product.getId(), 9)));
            assertFalse(failed.path("status").asBoolean());
            long before = this.notifications.selectCount(Wrappers.<OrderNotificationEntity>lambdaQuery()
                .eq(OrderNotificationEntity::getBuyerId, buyer.getId()));
            doThrow(new IllegalStateException("simulated failure after notification recording"))
                .when(this.timeoutMessages).sendAfterCommit(any());
            UserEntity savedBuyer = buyer;
            ProductEntity savedProduct = product;
            assertThrows(IllegalStateException.class,
                () -> this.orderService.create(savedBuyer.getId(), this.request(savedProduct.getId(), 1)));
            assertEquals(before, this.notifications.selectCount(Wrappers.<OrderNotificationEntity>lambdaQuery()
                .eq(OrderNotificationEntity::getBuyerId, buyer.getId())));
            assertEquals(1L, this.orders.selectCount(Wrappers.<OrderEntity>lambdaQuery().eq(OrderEntity::getUserId, buyer.getId())));
            assertEquals(8, this.products.selectById(product.getId()).getStock());
            assertNull(adminMessages.messages.poll(400, TimeUnit.MILLISECONDS));
            assertNull(buyerMessages.messages.poll(400, TimeUnit.MILLISECONDS));
        } finally {
            this.sockets.forEach(WebSocket::abort);
            http.close();
            UserEntity savedBuyer = buyer;
            ProductEntity savedProduct = product;
            new TransactionTemplate(this.transactions).executeWithoutResult(transaction -> {
                if (savedBuyer != null) {
                    for (OrderEntity order : this.orders.selectList(Wrappers.<OrderEntity>lambdaQuery()
                            .eq(OrderEntity::getUserId, savedBuyer.getId()))) {
                        this.notifications.delete(Wrappers.<OrderNotificationEntity>lambdaQuery()
                            .eq(OrderNotificationEntity::getOrderId, order.getId()));
                        this.items.delete(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, order.getId()));
                        this.orders.deleteById(order.getId());
                    }
                }
                if (savedProduct != null) this.products.deleteById(savedProduct.getId());
                this.testUsers.forEach(user -> this.users.deleteById(user.getId()));
            });
        }
    }

    private TradeRequests.OrderCreate request(int productId, int quantity) {
        return new TradeRequests.OrderCreate(UUID.randomUUID().toString(),
            List.of(new TradeRequests.OrderCreateItem(productId, quantity)), false,
            "与买家不同的收货人", "13900139000", "通知验证地址", null);
    }

    private ProductEntity product() {
        ProductEntity product = new ProductEntity();
        product.setName("order-notification-" + UUID.randomUUID());
        product.setCategory("通知测试");
        product.setPrice(new BigDecimal("16.80"));
        product.setStock(10);
        product.setSales(0);
        product.setStatus(1);
        this.products.insert(product);
        return product;
    }

    private UserEntity user(String role, int status) {
        UserEntity user = new UserEntity();
        user.setUsername("on" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        user.setName("通知验证买家");
        user.setPassword("test-password");
        user.setRole(role);
        user.setStatus(status);
        user.setRegisteredAt(LocalDateTime.now());
        this.users.insert(user);
        this.testUsers.add(user);
        return user;
    }

    private SocketMessages socket(HttpClient http, String token) throws Exception {
        String ticket = this.post(http, "/notification/socket-ticket", token, "{}").path("payload").path("ticket").asText();
        SocketMessages listener = new SocketMessages();
        WebSocket socket = http.newWebSocketBuilder().header("Origin", "http://localhost:5173")
            .connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI.create("ws://localhost:" + this.port + "/ws/notifications?ticket=" + ticket), listener).join();
        this.sockets.add(socket);
        listener.await("ready");
        return listener;
    }

    private JsonNode get(HttpClient http, String path, String token) throws Exception {
        HttpResponse<String> response = this.http(http, path, token, null);
        assertEquals(200, response.statusCode());
        return this.json.readTree(response.body());
    }

    private JsonNode post(HttpClient http, String path, String token, String body) throws Exception {
        HttpResponse<String> response = this.http(http, path, token, body);
        assertEquals(200, response.statusCode());
        return this.json.readTree(response.body());
    }

    private HttpResponse<String> http(HttpClient http, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path))
            .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token);
        if (body != null) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private class SocketMessages implements WebSocket.Listener {
        private final LinkedBlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.buffer.append(data);
            if (last) {
                this.messages.add(json.readTree(this.buffer.toString()));
                this.buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
        JsonNode await(String type) throws InterruptedException {
            JsonNode message = this.messages.poll(10, TimeUnit.SECONDS);
            assertNotNull(message, "missing socket message: " + type);
            assertEquals(type, message.path("type").asText());
            return message;
        }
    }
}
