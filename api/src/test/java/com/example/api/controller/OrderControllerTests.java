package com.example.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.example.api.entity.OrderEntity;
import com.example.api.exception.TradeExceptionHandler;
import com.example.api.exception.TradeFailedException;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;

@WebMvcTest(OrderController.class)
@Import({ SecurityConfiguration.class, TradeExceptionHandler.class })
class OrderControllerTests {

    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService orders;
    @MockitoBean private PaymentService payments;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void orderCreationRequiresAuthentication() throws Exception {
        this.mvc.perform(post("/order/create").contentType(MediaType.APPLICATION_JSON)
            .content(this.body("request-1234567890"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(this.orders, this.payments);
    }

    @Test
    void missingRequestIdIsRejectedBeforeCallingTheOrderService() throws Exception {
        this.authenticate();
        this.mvc.perform(post("/order/create").header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body("request-1234567890")
                .replace("\"requestId\":\"request-1234567890\",", "")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.orders);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "short", "invalid request id", "invalid/request/id",
        "12345678901234567890123456789012345678901234567890123456789012345" })
    void malformedRequestIdsAreRejectedBeforeAnyBusinessWrites(String requestId) throws Exception {
        this.authenticate();
        this.mvc.perform(post("/order/create").header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body(requestId)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.orders);
    }

    @Test
    void creationUsesTheJwtOwnerAndDoesNotExposeInternalIdempotencyFields() throws Exception {
        this.authenticate();
        OrderEntity order = new OrderEntity();
        order.setId(42L);
        order.setUserId(7);
        order.setRequestId("request-1234567890");
        order.setRequestHash("internal-hash");
        when(this.orders.create(eq(7), any())).thenReturn(order);

        this.mvc.perform(post("/order/create").header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body("request-1234567890")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.order.id").value(42))
            .andExpect(jsonPath("$.payload.order.userId").value(7))
            .andExpect(jsonPath("$.payload.order.requestId").doesNotExist())
            .andExpect(jsonPath("$.payload.order.requestHash").doesNotExist());
        verify(this.orders).create(eq(7), any());
    }

    @Test
    void lockOrContentConflictsUseTheExistingBusinessErrorResponse() throws Exception {
        this.authenticate();
        when(this.orders.create(eq(7), any())).thenThrow(new TradeFailedException("该订单正在提交，请稍后重试"));
        this.mvc.perform(post("/order/create").header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body("request-1234567890")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.payload.message").value("该订单正在提交，请稍后重试"));
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("order-token").header("alg", "RS256").subject("order-user")
            .claim("role", "user").claim("userId", 7L).build();
        when(this.jwtDecoder.decode("order-token")).thenReturn(jwt);
    }

    private String body(String requestId) {
        return """
            {"requestId":"%s","items":[{"productId":11,"quantity":2}],"fromCart":false,
             "receiverName":"receiver","receiverPhone":"13900139000","receiverAddress":"address",
             "remark":null,"userId":999,"totalAmount":0.01}
            """.formatted(requestId);
    }
}
