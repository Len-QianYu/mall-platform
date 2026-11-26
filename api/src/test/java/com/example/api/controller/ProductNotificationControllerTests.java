package com.example.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api.dto.UserNotificationView;
import com.example.api.exception.TradeExceptionHandler;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.ProductFavoriteService;
import com.example.api.service.UserNotificationService;
import com.example.api.websocket.NotificationSocketTickets;

@WebMvcTest({ ProductFavoriteController.class, ProductNotificationController.class })
@Import({ SecurityConfiguration.class, TradeExceptionHandler.class })
class ProductNotificationControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private ProductFavoriteService favorites;
    @MockitoBean private UserNotificationService notifications;
    @MockitoBean private NotificationSocketTickets tickets;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void favoritesNotificationsAndSocketTicketsRequireLogin() throws Exception {
        this.mvc.perform(get("/favorite/ids")).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/notification")).andExpect(status().isUnauthorized());
        this.mvc.perform(post("/notification/socket-ticket")).andExpect(status().isUnauthorized());
        this.mvc.perform(post("/favorite").contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":11,\"favorite\":true}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(this.favorites, this.notifications, this.tickets);
    }

    @Test
    void favoriteAndReadAllUseJwtOwnerDespiteForgedUserId() throws Exception {
        this.authenticate();
        this.mvc.perform(post("/favorite").header(HttpHeaders.AUTHORIZATION, "Bearer notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":11,\"favorite\":true,\"userId\":999}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.favorite").value(true));
        verify(this.favorites).setFavorite(7, 11, true);
        this.mvc.perform(post("/notification/read-all").header(HttpHeaders.AUTHORIZATION, "Bearer notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999}"))
            .andExpect(status().isOk());
        verify(this.notifications).markAllRead(7);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"productId\":0,\"favorite\":true}", "{\"productId\":11}", "{\"productId\":11,\"favorite\":null}" })
    void invalidFavoriteFieldsAreRejectedBeforeWrites(String body) throws Exception {
        this.authenticate();
        this.mvc.perform(post("/favorite").header(HttpHeaders.AUTHORIZATION, "Bearer notification-token")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.favorites);
    }

    @Test
    void unreadListIsOwnerScopedAndDoesNotExposeInternalOwnerOrEventIds() throws Exception {
        this.authenticate();
        UserNotificationView notification = new UserNotificationView();
        notification.setId(42L);
        notification.setProductId(11);
        notification.setType("PRICE_DROP");
        notification.setOldPrice(new BigDecimal("100.00"));
        notification.setNewPrice(new BigDecimal("80.00"));
        when(this.notifications.count(7, true)).thenReturn(1);
        when(this.notifications.fetch(eq(7), eq(true), any())).thenReturn(List.of(notification));
        this.mvc.perform(get("/notification").param("unreadOnly", "true").param("userId", "999")
            .header(HttpHeaders.AUTHORIZATION, "Bearer notification-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.notifications[0].id").value(42))
            .andExpect(jsonPath("$.payload.notifications[0].userId").doesNotExist())
            .andExpect(jsonPath("$.payload.notifications[0].eventId").doesNotExist())
            .andExpect(jsonPath("$.payload.notifications[0].type").value("PRICE_DROP"))
            .andExpect(jsonPath("$.payload.unreadCount").value(1));
    }

    @Test
    void readingAnotherUsersNotificationReturnsNotFound() throws Exception {
        this.authenticate();
        this.mvc.perform(post("/notification/read").header(HttpHeaders.AUTHORIZATION, "Bearer notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":999,\"userId\":8}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));
        verify(this.notifications).markRead(7, 999L, null);
    }

    @Test
    void shippedNotificationsAreReadByTypeAndJwtOwner() throws Exception {
        this.authenticate();
        when(this.notifications.markRead(7, 42L, "ORDER_SHIPPED")).thenReturn(true);
        this.mvc.perform(post("/notification/read").header(HttpHeaders.AUTHORIZATION, "Bearer notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":42,\"type\":\"ORDER_SHIPPED\",\"userId\":999}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
        verify(this.notifications).markRead(7, 42L, "ORDER_SHIPPED");
    }

    @ParameterizedTest
    @ValueSource(strings = { "NEW_ORDER", "UNKNOWN", "" })
    void unsupportedNotificationTypesAreRejectedBeforeWrites(String type) throws Exception {
        this.authenticate();
        this.mvc.perform(post("/notification/read").header(HttpHeaders.AUTHORIZATION, "Bearer notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":42,\"type\":\"" + type + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.notifications);
    }

    @Test
    void socketTicketIsIssuedForTheAuthenticatedUser() throws Exception {
        this.authenticate();
        when(this.tickets.issue(eq(7), any(), eq(0L))).thenReturn("one-time-ticket");
        this.mvc.perform(post("/notification/socket-ticket").header(HttpHeaders.AUTHORIZATION, "Bearer notification-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.ticket").value("one-time-ticket"));
        verify(this.tickets).issue(eq(7), any(), eq(0L));
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("notification-token").header("alg", "RS256").subject("notification-user")
            .claim("role", "user").claim("userId", 7L).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(this.jwtDecoder.decode("notification-token")).thenReturn(jwt);
    }
}
