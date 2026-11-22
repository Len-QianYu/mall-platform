package com.example.api.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.api.websocket.NotificationHandshakeInterceptor;
import com.example.api.websocket.ProductNotificationWebSocketHandler;

@Configuration
@EnableWebSocket
public class ProductNotificationWebSocketConfiguration implements WebSocketConfigurer {
    private final ProductNotificationWebSocketHandler handler;
    private final NotificationHandshakeInterceptor handshake;
    private final String[] allowedOrigins;

    public ProductNotificationWebSocketConfiguration(ProductNotificationWebSocketHandler handler,
        NotificationHandshakeInterceptor handshake, @Value("${product-notification.allowed-origins}") String[] allowedOrigins) {
        this.handler = handler;
        this.handshake = handshake;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(this.handler, "/ws/notifications")
            .addInterceptors(this.handshake).setAllowedOrigins(this.allowedOrigins);
    }
}
