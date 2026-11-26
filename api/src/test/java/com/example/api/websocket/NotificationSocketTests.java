package com.example.api.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.api.entity.ProductNotificationEntity;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.entity.OrderShipmentNotificationEntity;
import com.example.api.enums.ProductNotificationType;
import com.example.api.entity.UserEntity;
import com.example.api.mapper.UserMapper;

import tools.jackson.databind.json.JsonMapper;

class NotificationSocketTests {
    private final Instant now = Instant.parse("2026-11-22T04:00:00Z");
    private final Clock clock = mock(Clock.class);

    @Test
    void shortTicketIsSingleUseAndUsesServerAssignedIdentity() {
        when(this.clock.instant()).thenReturn(this.now);
        NotificationSocketTickets tickets = new NotificationSocketTickets(this.clock);
        String value = tickets.issue(7, this.now.plusSeconds(3600));
        assertEquals(7, tickets.consume(value).orElseThrow().userId());
        assertTrue(tickets.consume(value).isEmpty());
        assertTrue(tickets.consume("forged-ticket").isEmpty());
    }

    @Test
    void expiredTicketsAndExpiredJwtCannotEstablishAConnection() {
        when(this.clock.instant()).thenReturn(this.now);
        NotificationSocketTickets tickets = new NotificationSocketTickets(this.clock);
        String shortTicket = tickets.issue(7, this.now.plusSeconds(3600));
        String expiresWithJwt = tickets.issue(7, this.now.plusSeconds(5));
        when(this.clock.instant()).thenReturn(this.now.plusSeconds(6));
        assertTrue(tickets.consume(expiresWithJwt).isEmpty());
        when(this.clock.instant()).thenReturn(this.now.plusSeconds(31));
        assertTrue(tickets.consume(shortTicket).isEmpty());
    }

    @Test
    void handshakeRejectsMissingTicketAndIgnoresForgedUserId() {
        when(this.clock.instant()).thenReturn(this.now);
        NotificationSocketTickets tickets = new NotificationSocketTickets(this.clock);
        NotificationHandshakeInterceptor handshake = new NotificationHandshakeInterceptor(tickets);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/notifications?userId=999"));
        assertFalse(handshake.beforeHandshake(request, response, null, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        String value = tickets.issue(7, this.now.plusSeconds(3600));
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/notifications?ticket=" + value + "&userId=999"));
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(handshake.beforeHandshake(request, response, null, attributes));
        assertEquals(7, attributes.get(NotificationHandshakeInterceptor.USER_ID));
        assertFalse(handshake.beforeHandshake(request, response, null, new HashMap<>()));
    }

    @Test
    void notificationsOnlyReachTheOwnersSessionsAndOmitInternalIds() throws Exception {
        ProductNotificationWebSocketHandler handler = new ProductNotificationWebSocketHandler(JsonMapper.builder().build());
        WebSocketSession owner = this.session("owner", 7);
        WebSocketSession other = this.session("other", 8);
        handler.afterConnectionEstablished(owner);
        handler.afterConnectionEstablished(other);
        clearInvocations(owner, other);
        ProductNotificationEntity notification = new ProductNotificationEntity();
        notification.setId(42L);
        notification.setUserId(7);
        notification.setEventId(100L);
        notification.setType(ProductNotificationType.PRICE_RISE);
        handler.sendNotification(notification);
        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(owner).sendMessage(message.capture());
        verify(other, never()).sendMessage(any());
        assertTrue(message.getValue().getPayload().contains("PRICE_RISE"));
        assertFalse(message.getValue().getPayload().contains("userId"));
        assertFalse(message.getValue().getPayload().contains("eventId"));
    }

    @Test
    void socketMessagesCannotSwitchToAnotherUsersStream() throws Exception {
        ProductNotificationWebSocketHandler handler = new ProductNotificationWebSocketHandler(JsonMapper.builder().build());
        WebSocketSession session = this.session("owner", 7);
        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"userId\":8}"));
        verify(session).close(CloseStatus.BAD_DATA);
    }

    @Test
    void expiredAuthenticationIsClosedBeforeRegisteringTheSession() throws Exception {
        ProductNotificationWebSocketHandler handler = new ProductNotificationWebSocketHandler(JsonMapper.builder().build());
        WebSocketSession session = this.session("expired", 7);
        session.getAttributes().put(NotificationHandshakeInterceptor.TOKEN_EXPIRES_AT, Instant.now().minusSeconds(1));
        handler.afterConnectionEstablished(session);
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void shipmentNotificationsOnlyReachTheBuyerAcrossTabs() throws Exception {
        ProductNotificationWebSocketHandler handler = new ProductNotificationWebSocketHandler(JsonMapper.builder().build());
        WebSocketSession buyer = this.session("buyer", 7);
        WebSocketSession secondTab = this.session("buyer-tab", 7);
        WebSocketSession otherUser = this.session("other", 8);
        handler.afterConnectionEstablished(buyer);
        handler.afterConnectionEstablished(secondTab);
        handler.afterConnectionEstablished(otherUser);
        clearInvocations(buyer, secondTab, otherUser);
        OrderShipmentNotificationEntity notification = new OrderShipmentNotificationEntity();
        notification.setId(42L);
        notification.setUserId(7);
        notification.setOrderId(99L);
        notification.setOrderNo("ORDER-99");
        handler.sendShipmentNotification(notification);
        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(buyer).sendMessage(message.capture());
        verify(secondTab).sendMessage(any());
        verify(otherUser, never()).sendMessage(any());
        assertTrue(message.getValue().getPayload().contains("ORDER_SHIPPED"));
        assertTrue(message.getValue().getPayload().contains("ORDER-99"));
        assertFalse(message.getValue().getPayload().contains("userId"));
        assertFalse(message.getValue().getPayload().contains("order_notification"));
    }

    private WebSocketSession session(String id, int userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(NotificationHandshakeInterceptor.USER_ID, userId);
        attributes.put(NotificationHandshakeInterceptor.TOKEN_EXPIRES_AT, Instant.now().plusSeconds(3600));
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    @Test
    void passwordChangesStopPreviouslyAuthenticatedNotificationConnections() throws Exception {
        UserMapper users = mock(UserMapper.class);
        UserEntity user = new UserEntity(); user.setId(7); user.setAuthVersion(0L);
        when(users.selectById(7)).thenReturn(user);
        ProductNotificationWebSocketHandler handler = new ProductNotificationWebSocketHandler(JsonMapper.builder().build(), users);
        WebSocketSession session = this.session("versioned", 7);
        session.getAttributes().put(NotificationHandshakeInterceptor.AUTH_VERSION, 0L);
        handler.afterConnectionEstablished(session);
        clearInvocations(session);
        user.setAuthVersion(1L);
        handler.sendRefresh(7);
        verify(session, never()).sendMessage(any());
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void orderNotificationsAndReadRefreshOnlyReachTheirRecipientsAcrossTabs() throws Exception {
        ProductNotificationWebSocketHandler handler = new ProductNotificationWebSocketHandler(JsonMapper.builder().build());
        WebSocketSession owner = this.session("admin", 7);
        WebSocketSession secondTab = this.session("admin-tab", 7);
        WebSocketSession buyer = this.session("buyer", 8);
        handler.afterConnectionEstablished(owner);
        handler.afterConnectionEstablished(secondTab);
        handler.afterConnectionEstablished(buyer);
        clearInvocations(owner, secondTab, buyer);
        OrderNotificationEntity notification = new OrderNotificationEntity();
        notification.setId(42L);
        notification.setUserId(7);
        notification.setOrderId(99L);
        notification.setOrderNo("ORDER-99");
        handler.sendOrderNotification(notification);
        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(owner).sendMessage(message.capture());
        verify(secondTab).sendMessage(any());
        verify(buyer, never()).sendMessage(any());
        assertTrue(message.getValue().getPayload().contains("order_notification"));
        assertTrue(message.getValue().getPayload().contains("NEW_ORDER"));
        assertTrue(message.getValue().getPayload().contains("ORDER-99"));
        assertFalse(message.getValue().getPayload().contains("userId"));
        clearInvocations(owner, secondTab, buyer);
        handler.sendOrderRefresh(7);
        verify(owner).sendMessage(message.capture());
        verify(secondTab).sendMessage(any());
        verify(buyer, never()).sendMessage(any());
        assertTrue(message.getValue().getPayload().contains("order_refresh"));
    }
}
