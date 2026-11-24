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

import com.example.api.controller.admin.AdminNotificationController;
import com.example.api.entity.OrderNotificationEntity;
import com.example.api.exception.TradeExceptionHandler;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.OrderNotificationService;

@WebMvcTest(AdminNotificationController.class)
@Import({ SecurityConfiguration.class, TradeExceptionHandler.class })
class AdminNotificationControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrderNotificationService notifications;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void anonymousAndOrdinaryBuyersCannotAccessAnyAdminNotificationEndpoint() throws Exception {
        this.mvc.perform(get("/admin/notification")).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/admin/notification/unread-count")).andExpect(status().isUnauthorized());
        this.mvc.perform(post("/admin/notification/read-all")).andExpect(status().isUnauthorized());
        this.authenticate("user");
        this.mvc.perform(get("/admin/notification").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token"))
            .andExpect(status().isForbidden());
        this.mvc.perform(get("/admin/notification/unread-count").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token"))
            .andExpect(status().isForbidden());
        this.mvc.perform(post("/admin/notification/read-all").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token"))
            .andExpect(status().isForbidden());
        this.mvc.perform(post("/admin/notification/read").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":42}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(this.notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = { "admin", "super_admin" })
    void bothAdminRolesSeeTheirOrderSnapshotsAndUnreadCounts(String role) throws Exception {
        this.authenticate(role);
        OrderNotificationEntity notification = new OrderNotificationEntity();
        notification.setId(42L);
        notification.setUserId(7);
        notification.setOrderId(99L);
        notification.setOrderNo("ORDER-99");
        notification.setBuyerId(8);
        notification.setBuyerUsername("buyer");
        notification.setBuyerName("买家名字");
        notification.setTotalAmount(new BigDecimal("168.80"));
        when(this.notifications.count(7, true)).thenReturn(1);
        when(this.notifications.fetch(eq(7), eq(true), any())).thenReturn(List.of(notification));
        this.mvc.perform(get("/admin/notification").param("unreadOnly", "true").param("userId", "999")
            .header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.notifications[0].type").value("NEW_ORDER"))
            .andExpect(jsonPath("$.payload.notifications[0].orderId").value(99))
            .andExpect(jsonPath("$.payload.notifications[0].orderNo").value("ORDER-99"))
            .andExpect(jsonPath("$.payload.notifications[0].buyerUsername").value("buyer"))
            .andExpect(jsonPath("$.payload.notifications[0].buyerName").value("买家名字"))
            .andExpect(jsonPath("$.payload.notifications[0].totalAmount").value(168.80))
            .andExpect(jsonPath("$.payload.notifications[0].userId").doesNotExist())
            .andExpect(jsonPath("$.payload.unreadCount").value(1));
    }

    @Test
    void readAndReadAllUseTheJwtOwnerAndDoNotAcceptForgedRecipientIds() throws Exception {
        this.authenticate("admin");
        when(this.notifications.markRead(7, 42L)).thenReturn(true);
        this.mvc.perform(post("/admin/notification/read").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":42,\"userId\":999}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
        verify(this.notifications).markRead(7, 42L);
        this.mvc.perform(post("/admin/notification/read-all").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999}"))
            .andExpect(status().isOk());
        verify(this.notifications).markAllRead(7);
    }

    @Test
    void readingSomeoneElsesNotificationReturnsAVisibleBusinessError() throws Exception {
        this.authenticate("super_admin");
        this.mvc.perform(post("/admin/notification/read").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":999}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.payload.message").value("通知不存在"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"id\":0}", "{\"id\":-1}", "{\"id\":null}" })
    void invalidReadRequestsAreRejectedBeforeWriting(String body) throws Exception {
        this.authenticate("admin");
        this.mvc.perform(post("/admin/notification/read").header(HttpHeaders.AUTHORIZATION, "Bearer admin-notification-token")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(this.notifications);
    }

    private void authenticate(String role) {
        Jwt jwt = Jwt.withTokenValue("admin-notification-token").header("alg", "RS256").subject("admin-notification-user")
            .claim("role", role).claim("userId", 7L).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(this.jwtDecoder.decode("admin-notification-token")).thenReturn(jwt);
    }
}
