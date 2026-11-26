package com.example.api.websocket;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_ID = "notificationUserId";
    public static final String TOKEN_EXPIRES_AT = "notificationTokenExpiresAt";
    public static final String AUTH_VERSION = "notificationAuthVersion";

    private final NotificationSocketTickets tickets;

    public NotificationHandshakeInterceptor(NotificationSocketTickets tickets) { this.tickets = tickets; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler handler, Map<String, Object> attributes) {
        String value = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
        Optional<NotificationSocketTickets.Ticket> ticket = this.tickets.consume(value);
        if (ticket.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID, ticket.get().userId());
        attributes.put(TOKEN_EXPIRES_AT, ticket.get().tokenExpiresAt());
        attributes.put(AUTH_VERSION, ticket.get().authVersion());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler handler, Exception exception) { }
}
