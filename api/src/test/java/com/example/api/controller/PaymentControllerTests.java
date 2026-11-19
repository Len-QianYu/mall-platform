package com.example.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
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
import com.example.api.entity.PaymentEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.exception.TradeExceptionHandler;
import com.example.api.exception.TradeFailedException;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;

@WebMvcTest(PaymentController.class)
@Import({ SecurityConfiguration.class, TradeExceptionHandler.class })
class PaymentControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentService payments;

    @MockitoBean
    private OrderService orders;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void paymentCreationQueriesAndMethodsRequireAuthentication() throws Exception {
        this.mvc.perform(post("/payment/pay").contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":42,\"method\":\"ALIPAY\"}")).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/payment/query").param("orderId", "42")).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/payment/methods")).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/payment/alipay/notify")).andExpect(status().isUnauthorized());
        verifyNoInteractions(this.payments, this.orders);
    }

    @Test
    void alipayNotificationIsPublicAndReturnsExactlyPlainTextSuccessOrFailure() throws Exception {
        when(this.payments.handleAlipayNotify(any())).thenReturn(true, false);
        this.mvc.perform(post("/payment/alipay/notify").param("sign", "test-signature"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string("success"));
        this.mvc.perform(post("/payment/alipay/notify").param("sign", "forged-signature"))
            .andExpect(status().isOk()).andExpect(content().string("failure"));
    }

    @Test
    void aNotificationTransactionFailureAsksAlipayToRetry() throws Exception {
        when(this.payments.handleAlipayNotify(any())).thenThrow(new TradeFailedException("test failure"));
        this.mvc.perform(post("/payment/alipay/notify").param("sign", "test-signature"))
            .andExpect(status().isOk()).andExpect(content().string("failure"));
    }

    @Test
    void creationUsesTheJwtOwnerAndIgnoresClientAmountAndIdentity() throws Exception {
        this.authenticate();
        OrderEntity order = this.order();
        PaymentEntity payment = new PaymentEntity();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("33.60"));
        when(this.orders.fetchByIdForUser(42L, 7)).thenReturn(Optional.of(order));
        when(this.payments.pay(order, PaymentMethod.ALIPAY)).thenReturn(new PaymentService.PaymentStart(
            payment, "https://openapi-sandbox.dl.alipaydev.com/gateway.do?test=payment"));
        this.mvc.perform(post("/payment/pay").header(HttpHeaders.AUTHORIZATION, "Bearer payment-token")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"orderId":42,"method":"ALIPAY","userId":8,"amount":0.01}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.payment.status").value("PENDING"))
            .andExpect(jsonPath("$.payload.payment.amount").value(33.6))
            .andExpect(jsonPath("$.payload.payUrl").isString());
        verify(this.orders).fetchByIdForUser(42L, 7);
        verify(this.payments).pay(order, PaymentMethod.ALIPAY);
    }

    @Test
    void anotherUsersOrderCannotReachThePaymentService() throws Exception {
        this.authenticate();
        when(this.orders.fetchByIdForUser(42L, 7)).thenReturn(Optional.empty());
        this.mvc.perform(post("/payment/pay").header(HttpHeaders.AUTHORIZATION, "Bearer payment-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":42,\"method\":\"ALIPAY\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.payments);
    }

    @Test
    void queryUsesTheJwtUserAndReturnsOnlyTheConfirmedState() throws Exception {
        this.authenticate();
        when(this.payments.query(42L, 7)).thenReturn(new PaymentService.PaymentQuery(this.order(), null, false));
        this.mvc.perform(get("/payment/query").param("orderId", "42").param("userId", "8")
            .header(HttpHeaders.AUTHORIZATION, "Bearer payment-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.paid").value(false))
            .andExpect(jsonPath("$.payload.order.status").value("PENDING_PAYMENT"));
        verify(this.payments).query(42L, 7);
    }

    @Test
    void businessFailuresReturnTheExistingApiErrorFormat() throws Exception {
        this.authenticate();
        when(this.payments.query(42L, 7)).thenThrow(new TradeFailedException("支付宝查询支付结果失败，请稍后重试"));
        this.mvc.perform(get("/payment/query").param("orderId", "42")
            .header(HttpHeaders.AUTHORIZATION, "Bearer payment-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.payload.message").value("支付宝查询支付结果失败，请稍后重试"));
    }

    @Test
    void configuredMethodsAreReturnedWithoutPaymentCredentials() throws Exception {
        this.authenticate();
        when(this.payments.availableMethods()).thenReturn(List.of(PaymentMethod.ALIPAY, PaymentMethod.MOCK));
        when(this.payments.isAlipaySandbox()).thenReturn(true);
        this.mvc.perform(get("/payment/methods").header(HttpHeaders.AUTHORIZATION, "Bearer payment-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.methods[0]").value("ALIPAY"))
            .andExpect(jsonPath("$.payload.alipaySandbox").value(true))
            .andExpect(jsonPath("$.payload.appPrivateKey").doesNotExist());
    }

    @Test
    void missingPaymentMethodIsABadRequestWithoutDatabaseAccess() throws Exception {
        this.authenticate();
        this.mvc.perform(post("/payment/pay").header(HttpHeaders.AUTHORIZATION, "Bearer payment-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":42}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.payments, this.orders);
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("payment-token").header("alg", "RS256").subject("payment-user")
            .claim("role", "user").claim("userId", 7L).build();
        when(this.jwtDecoder.decode("payment-token")).thenReturn(jwt);
    }

    private OrderEntity order() {
        OrderEntity order = new OrderEntity();
        order.setId(42L);
        order.setUserId(7);
        order.setTotalAmount(new BigDecimal("33.60"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        return order;
    }
}
