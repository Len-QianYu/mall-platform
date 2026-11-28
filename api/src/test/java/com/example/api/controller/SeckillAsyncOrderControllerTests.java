package com.example.api.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
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

import com.example.api.configuration.SeckillExecutorConfiguration;
import com.example.api.entity.OrderEntity;
import com.example.api.exception.TradeExceptionHandler;
import com.example.api.exception.TradeFailedException;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.OrderService;
import com.example.api.service.PaymentService;
import com.example.api.service.SeckillOrderDispatcher;

@WebMvcTest(value = OrderController.class, properties = { "seckill.executor.enabled=true",
    "seckill.executor.workers=1", "seckill.executor.queue-capacity=0" })
@Import({ SecurityConfiguration.class, TradeExceptionHandler.class, SeckillExecutorConfiguration.class,
    SeckillOrderDispatcher.class })
class SeckillAsyncOrderControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private OrderService orders;
    @MockitoBean private PaymentService payments;
    @MockitoBean private JwtDecoder decoder;

    @BeforeEach
    void authenticate() {
        when(this.decoder.decode("seckill-token")).thenReturn(Jwt.withTokenValue("seckill-token")
            .header("alg", "RS256").subject("buyer").claim("role", "user").claim("userId", 7).build());
    }

    @Test
    void unauthenticatedRequestsNeverEnterTheBusinessExecutor() throws Exception {
        this.mvc.perform(post("/order/create").contentType(MediaType.APPLICATION_JSON).content(this.body("first")))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(this.orders);
    }

    @Test
    void waitsForTheCommittedOrderAndUsesTheAuthenticatedOwnerInTheWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        when(this.orders.create(eq(7), any())).thenAnswer(call -> {
            thread.set(Thread.currentThread().getName());
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return this.order();
        });
        var pending = this.mvc.perform(this.orderRequest("first")).andExpect(request().asyncStarted()).andReturn();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(thread.get().startsWith("seckill-order-"));
            assertEquals("", pending.getResponse().getContentAsString());
        } finally { release.countDown(); }
        pending.getAsyncResult(5000);
        this.mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true)).andExpect(jsonPath("$.payload.order.id").value(42))
            .andExpect(jsonPath("$.payload.order.userId").value(7))
            .andExpect(jsonPath("$.payload.order.requestHash").doesNotExist());
        verify(this.orders).create(eq(7), any());
    }

    @Test
    void fullExecutorRejectsBeforeOrderWritesAndStillCompletesTheAcceptedRequest() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(this.orders.create(eq(7), any())).thenAnswer(call -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return this.order();
        });
        var pending = this.mvc.perform(this.orderRequest("first")).andExpect(request().asyncStarted()).andReturn();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var rejected = this.mvc.perform(this.orderRequest("second")).andReturn();
            if (rejected.getRequest().isAsyncStarted()) {
                rejected.getAsyncResult(5000);
                rejected = this.mvc.perform(asyncDispatch(rejected)).andReturn();
            }
            status().isServiceUnavailable().match(rejected);
            jsonPath("$.status").value(false).match(rejected);
            jsonPath("$.payload.message").value("秒杀请求过多，请稍后重试").match(rejected);
            verify(this.orders, times(1)).create(eq(7), any());
        } finally { release.countDown(); }
        pending.getAsyncResult(5000);
        this.mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.order.id").value(42));
    }

    @Test
    void asynchronousStockFailuresKeepTheExistingBusinessResponse() throws Exception {
        when(this.orders.create(eq(7), any())).thenThrow(new TradeFailedException("秒杀商品已抢完"));
        var pending = this.mvc.perform(this.orderRequest("soldout")).andExpect(request().asyncStarted()).andReturn();
        pending.getAsyncResult(5000);
        this.mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.payload.message").value("秒杀商品已抢完"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder orderRequest(String suffix) {
        return post("/order/create").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body(suffix));
    }

    private String body(String suffix) {
        return """
            {"requestId":"seckill_request_%s","items":[{"productId":11,"quantity":1}],"fromCart":false,
             "receiverName":"buyer","receiverPhone":"13900139000","receiverAddress":"address",
             "seckillActivityId":3,"userId":999}
            """.formatted(suffix);
    }

    private OrderEntity order() {
        OrderEntity order = new OrderEntity();
        order.setId(42L);
        order.setUserId(7);
        order.setRequestHash("internal-hash");
        return order;
    }
}
