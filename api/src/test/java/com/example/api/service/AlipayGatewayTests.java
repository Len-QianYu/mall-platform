package com.example.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.example.api.configuration.AlipayProperties;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.exception.TradeFailedException;

class AlipayGatewayTests {

    private static String privateKey;
    private static String publicKey;
    private AlipayProperties properties;
    private AlipayClient client;
    private AlipayGateway gateway;
    private PaymentEntity payment;

    @BeforeAll
    static void generateTestKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    @BeforeEach
    void setUp() {
        this.properties = new AlipayProperties();
        this.properties.setEnabled(true);
        this.properties.setAppId("test-app-id");
        this.properties.setAlipayPublicKey(publicKey);
        this.properties.setSellerId("test-seller-id");
        this.client = mock(AlipayClient.class);
        this.gateway = new AlipayGateway(this.properties, Optional.of(this.client));
        this.payment = new PaymentEntity();
        this.payment.setPaymentNo("TEST-PAYMENT-99");
        this.payment.setTradeNo("ALIPAY-TRADE-99");
        this.payment.setAmount(new BigDecimal("33.60"));
    }

    @Test
    void verifiesRealRsa2SignaturesAndRejectsTampering() throws Exception {
        Map<String, String> params = this.signedNotification();
        assertTrue(this.gateway.verifyNotification(params));
        params.put("total_amount", "0.01");
        assertFalse(this.gateway.verifyNotification(params));
    }

    @Test
    void wrongApplicationOrSellerIsRejectedEvenWithAValidSignature() throws Exception {
        Map<String, String> params = this.signedNotification();
        this.properties.setAppId("another-app");
        assertFalse(this.gateway.verifyNotification(params));
        this.properties.setAppId("test-app-id");
        this.properties.setSellerId("another-seller");
        assertFalse(this.gateway.verifyNotification(params));
    }

    @Test
    void missingSignatureAndDisabledGatewayAreRejected() throws Exception {
        Map<String, String> params = this.signedNotification();
        params.remove("sign");
        assertFalse(this.gateway.verifyNotification(params));
        this.properties.setEnabled(false);
        assertFalse(this.gateway.verifyNotification(this.signedNotification()));
    }

    @Test
    void checkoutRequestUsesServerAmountRsaPagePayAndAbsoluteOrderDeadline() throws Exception {
        AlipayTradePagePayResponse response = new AlipayTradePagePayResponse();
        response.setBody("https://openapi-sandbox.dl.alipaydev.com/gateway.do?test=payment");
        when(this.client.pageExecute(any(AlipayTradePagePayRequest.class), eq("GET"))).thenReturn(response);
        OrderEntity order = new OrderEntity();
        order.setId(42L);
        order.setOrderNo("TEST-ORDER-42");
        this.gateway.createPayUrl(this.payment, order, LocalDateTime.of(2026, 11, 19, 10, 15));

        ArgumentCaptor<AlipayTradePagePayRequest> captured = ArgumentCaptor.forClass(AlipayTradePagePayRequest.class);
        verify(this.client).pageExecute(captured.capture(), eq("GET"));
        AlipayTradePagePayModel model = (AlipayTradePagePayModel) captured.getValue().getBizModel();
        assertEquals("TEST-PAYMENT-99", model.getOutTradeNo());
        assertEquals("33.60", model.getTotalAmount());
        assertEquals("FAST_INSTANT_TRADE_PAY", model.getProductCode());
        assertEquals("2026-11-19 10:15:00", model.getTimeExpire());
        assertEquals("http://localhost:8080/payment/alipay/notify", captured.getValue().getNotifyUrl());
        assertEquals("http://localhost:5173/payment/return?orderId=42", captured.getValue().getReturnUrl());
    }

    @Test
    void theOfficialSdkCanGenerateASignedCheckoutUrlWithoutNetworkAccess() throws Exception {
        AlipayClient sdk = new DefaultAlipayClient(this.properties.getGatewayUrl(), "test-app-id",
            privateKey, "json", "UTF-8", publicKey, "RSA2");
        AlipayGateway local = new AlipayGateway(this.properties, Optional.of(sdk));
        OrderEntity order = new OrderEntity();
        order.setId(42L);
        order.setOrderNo("TEST-ORDER-42");
        String url = local.createPayUrl(this.payment, order, LocalDateTime.of(2026, 11, 19, 10, 15));
        assertTrue(url.startsWith(this.properties.getGatewayUrl()));
        assertTrue(url.contains("method=alipay.trade.page.pay"));
        assertTrue(url.contains("sign="));
        assertTrue(url.contains("sign_type=RSA2"));
    }

    @Test
    void aTradeThatHasNotBeenOpenedIsDifferentFromAnApiFailure() throws Exception {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_NOT_EXIST");
        when(this.client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        assertTrue(this.gateway.query(this.payment).isEmpty());
        response.setSubCode("ACQ.ACCESS_FORBIDDEN");
        assertThrows(TradeFailedException.class, () -> this.gateway.query(this.payment));
    }

    @Test
    void sdkFailuresBecomeRecoverableBusinessErrors() throws Exception {
        when(this.client.execute(any(AlipayTradeQueryRequest.class))).thenThrow(new AlipayApiException("test failure"));
        assertThrows(TradeFailedException.class, () -> this.gateway.query(this.payment));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "invalid-amount" })
    void incompleteSuccessfulQueryResponsesCannotConfirmAPayment(String amount) throws Exception {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setOutTradeNo(this.payment.getPaymentNo());
        response.setTradeNo(this.payment.getTradeNo());
        response.setTradeStatus("TRADE_SUCCESS");
        response.setTotalAmount(amount);
        when(this.client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);

        assertThrows(TradeFailedException.class, () -> this.gateway.query(this.payment));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "invalid-amount" })
    void incompleteSuccessfulRefundResponsesCannotConfirmARefund(String amount) throws Exception {
        AlipayTradeRefundResponse response = new AlipayTradeRefundResponse();
        response.setCode("10000");
        response.setOutTradeNo(this.payment.getPaymentNo());
        response.setTradeNo(this.payment.getTradeNo());
        response.setRefundFee(amount);
        when(this.client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);

        assertThrows(TradeFailedException.class, () -> this.gateway.refund(this.payment));
    }

    @Test
    void aMissingAlipayTradeNumberCannotStartARefund() {
        this.payment.setTradeNo(null);

        assertThrows(TradeFailedException.class, () -> this.gateway.refund(this.payment));
        verifyNoInteractions(this.client);
    }

    @Test
    void repeatedRefundRequestsUseOneStableRequestNumberAndTheFullOriginalAmount() throws Exception {
        AlipayTradeRefundResponse response = new AlipayTradeRefundResponse();
        response.setCode("10000");
        response.setOutTradeNo("TEST-PAYMENT-99");
        response.setTradeNo("ALIPAY-TRADE-99");
        response.setRefundFee("33.60");
        when(this.client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);
        this.gateway.refund(this.payment);
        this.gateway.refund(this.payment);
        ArgumentCaptor<AlipayTradeRefundRequest> captured = ArgumentCaptor.forClass(AlipayTradeRefundRequest.class);
        verify(this.client, org.mockito.Mockito.times(2)).execute(captured.capture());
        for (AlipayTradeRefundRequest request : captured.getAllValues()) {
            AlipayTradeRefundModel model = (AlipayTradeRefundModel) request.getBizModel();
            assertEquals("TEST-PAYMENT-99-REFUND", model.getOutRequestNo());
            assertEquals("33.60", model.getRefundAmount());
        }
        response.setRefundFee("0.01");
        assertThrows(TradeFailedException.class, () -> this.gateway.refund(this.payment));
    }

    private Map<String, String> signedNotification() throws Exception {
        Map<String, String> params = new HashMap<>(Map.of(
            "app_id", "test-app-id", "seller_id", "test-seller-id", "out_trade_no", "TEST-PAYMENT-99",
            "trade_no", "ALIPAY-TRADE-99", "total_amount", "33.60", "trade_status", "TRADE_SUCCESS"));
        String sign = AlipaySignature.rsaSign(AlipaySignature.getSignContent(params), privateKey, "UTF-8", "RSA2");
        params.put("sign_type", "RSA2");
        params.put("sign", sign);
        return params;
    }
}
