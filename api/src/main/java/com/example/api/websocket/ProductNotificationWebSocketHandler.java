package com.example.api.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.api.entity.ProductNotificationEntity;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ProductNotificationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ProductNotificationWebSocketHandler.class);
    private record Connection(Integer userId, Instant expiresAt, WebSocketSession session) { }
    private record NotificationMessage(String type, ProductNotificationEntity notification) { }

    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, Connection>> connections = new ConcurrentHashMap<>();
    private final JsonMapper json;

    public ProductNotificationWebSocketHandler(JsonMapper json) { this.json = json; }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Object userId = session.getAttributes().get(NotificationHandshakeInterceptor.USER_ID);
        Object expiry = session.getAttributes().get(NotificationHandshakeInterceptor.TOKEN_EXPIRES_AT);
        if (!(userId instanceof Integer owner) || !(expiry instanceof Instant expiresAt) || !expiresAt.isAfter(Instant.now())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.setTextMessageSizeLimit(512);
        Connection connection = new Connection(owner, expiresAt,
            new ConcurrentWebSocketSessionDecorator(session, 10000, 65536));
        this.connections.compute(owner, (id, existing) -> {
            ConcurrentHashMap<String, Connection> group = existing == null ? new ConcurrentHashMap<>() : existing;
            group.put(session.getId(), connection);
            return group;
        });
        this.send(connection, new TextMessage("{\"type\":\"ready\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Connection connection = this.find(session);
        if (connection == null || !connection.expiresAt().isAfter(Instant.now())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            this.remove(session);
        } else if ("ping".equals(message.getPayload())) {
            this.send(connection, new TextMessage("{\"type\":\"pong\"}"));
        } else {
            session.close(CloseStatus.BAD_DATA);
            this.remove(session);
        }
    }

    public void sendNotification(ProductNotificationEntity notification) {
        Map<String, Connection> recipients = this.connections.get(notification.getUserId());
        if (recipients == null) return;
        try {
            TextMessage message = new TextMessage(this.json.writeValueAsString(new NotificationMessage("notification", notification)));
            recipients.values().forEach(connection -> this.send(connection, message));
        } catch (RuntimeException error) {
            log.warn("通知 {} 已保存，但实时推送失败", notification.getId(), error);
        }
    }

    public void sendRefresh(Integer userId) {
        Map<String, Connection> recipients = this.connections.get(userId);
        if (recipients != null) recipients.values().forEach(connection -> this.send(connection, new TextMessage("{\"type\":\"refresh\"}")));
    }

    @Scheduled(fixedDelay = 30000)
    public void closeExpiredConnections() {
        this.connections.values().forEach(group -> group.values().forEach(connection -> {
            if (!connection.expiresAt().isAfter(Instant.now()) || !connection.session().isOpen()) this.close(connection);
        }));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { this.remove(session); }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) throws IOException {
        this.remove(session);
        session.close(CloseStatus.SERVER_ERROR);
    }

    private Connection find(WebSocketSession session) {
        Map<String, Connection> group = this.connections.get(session.getAttributes().get(NotificationHandshakeInterceptor.USER_ID));
        return group == null ? null : group.get(session.getId());
    }

    private void send(Connection connection, TextMessage message) {
        if (!connection.expiresAt().isAfter(Instant.now()) || !connection.session().isOpen()) {
            this.close(connection);
            return;
        }
        try {
            connection.session().sendMessage(message);
        } catch (IOException | RuntimeException error) {
            log.debug("用户 {} 的通知连接已断开", connection.userId(), error);
            this.close(connection);
        }
    }

    private void close(Connection connection) {
        this.remove(connection.session());
        try { connection.session().close(CloseStatus.POLICY_VIOLATION); }
        catch (IOException | RuntimeException error) { log.debug("关闭通知连接失败", error); }
    }

    private void remove(WebSocketSession session) {
        Object owner = session.getAttributes().get(NotificationHandshakeInterceptor.USER_ID);
        if (!(owner instanceof Integer userId)) return;
        this.connections.computeIfPresent(userId, (id, group) -> {
            group.remove(session.getId());
            return group.isEmpty() ? null : group;
        });
    }
}
