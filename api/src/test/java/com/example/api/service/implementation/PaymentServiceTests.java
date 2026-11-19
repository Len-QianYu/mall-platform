package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.PaymentEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.enums.PaymentMethod;
import com.example.api.enums.PaymentStatus;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.PaymentMapper;
import com.example.api.service.AlipayGateway;
import com.example.api.service.AlipayGateway.Trade;
import com.example.api.service.PaymentService.PaymentQuery;
import com.example.api.service.PaymentService.PaymentStart;
import com.example.api.service.ProductService;

class PaymentServiceTests {

    @BeforeAll
    static void initializeMappings() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "payment-tests");
        TableInfoHelper.initTableInfo(assistant, OrderEntity.class);
        TableInfoHelper.initTableInfo(assistant, PaymentEntity.class);
        TableInfoHelper.initTableInfo(assistant, OrderItemEntity.class);
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 5);
    private static final String PAY_URL = "https://openapi-sandbox.dl.alipaydev.com/gateway.do?test=payment";
    private PaymentMapper payments;
    private OrderMapper orders;
    private ProductService products;
    private AlipayGateway alipay;
    private PaymentServiceImpl service;
    private OrderEntity order;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        this.payments = mock(PaymentMapper.class);
        this.orders = mock(OrderMapper.class);
        this.products = mock(ProductService.class);
        this.alipay = mock(AlipayGateway.class);
        OrderItemMapper items = mock(OrderItemMapper.class);
        this.service = new PaymentServiceImpl(this.payments, this.orders, items, this.products,
            this.alipay, Duration.ofMinutes(15), true);
        this.order = new OrderEntity();
        this.order.setId(42L);
        this.order.setOrderNo("TEST-ORDER-42");
        this.order.setUserId(7);
        this.order.setTotalAmount(new BigDecimal("33.60"));
        this.order.setStatus(OrderStatus.PENDING_PAYMENT);
        this.order.setCreatedAt(NOW.minusMinutes(5));
        this.payment = new PaymentEntity();
        this.payment.setId(99L);
        this.payment.setPaymentNo("TEST-PAYMENT-99");
        this.payment.setOrderId(42L);
        this.payment.setUserId(7);
        this.payment.setAmount(new BigDecimal("33.60"));
        this.payment.setMethod(PaymentMethod.ALIPAY);
        this.payment.setStatus(PaymentStatus.PENDING);
        OrderItemEntity item = new OrderItemEntity();
        item.setProductId(11);
        item.setQuantity(2);
        when(items.selectList(any())).thenReturn(List.of(item));
        when(this.orders.selectByIdForUpdate(42L)).thenReturn(this.order);
        when(this.orders.databaseNow()).thenReturn(NOW);
        when(this.orders.update(any())).thenReturn(1);
        when(this.payments.selectLatestForUpdate(42L)).thenAnswer(call -> this.payment);
        when(this.payments.selectById(any())).thenAnswer(call -> this.payment);
        when(this.payments.selectOne(any())).thenAnswer(call -> this.payment);
        when(this.payments.selectByNoForUpdate(any())).thenAnswer(call -> this.payment);
        when(this.payments.selectSuccessForUpdate(42L)).thenAnswer(call -> this.payment);
        when(this.payments.insert(any(PaymentEntity.class))).thenAnswer(call -> {
            this.payment = call.getArgument(0);
            this.payment.setId(99L);
            return 1;
        });
        when(this.alipay.isEnabled()).thenReturn(true);
        when(this.alipay.createPayUrl(any(), any(), any())).thenReturn(PAY_URL);
        when(this.alipay.query(any())).thenReturn(Optional.of(this.trade("WAIT_BUYER_PAY")));
        when(this.alipay.verifyNotification(any())).thenReturn(true);
    }

    @Test
    void creatingAlipayPaymentUsesOrderAmountAndDoesNotMarkTheOrderPaid() {
        this.payment = null;
        PaymentStart started = this.service.pay(this.order, PaymentMethod.ALIPAY);

        assertEquals(PAY_URL, started.payUrl());
        assertEquals(PaymentStatus.PENDING, started.payment().getStatus());
        assertEquals(new BigDecimal("33.60"), started.payment().getAmount());
        assertEquals(7, started.payment().getUserId());
        assertEquals(OrderStatus.PENDING_PAYMENT, this.order.getStatus());
        verify(this.orders, never()).update(any());
        verifyNoInteractions(this.products);
        verify(this.alipay).createPayUrl(eq(this.payment), eq(this.order), eq(NOW.plusMinutes(10)));
    }

    @Test
    void reopeningCheckoutReusesTheSameMerchantPaymentNumber() {
        String paymentNo = this.payment.getPaymentNo();
        this.service.pay(this.order, PaymentMethod.ALIPAY);
        this.service.pay(this.order, PaymentMethod.ALIPAY);
        assertEquals(paymentNo, this.payment.getPaymentNo());
        verify(this.payments, never()).insert(any(PaymentEntity.class));
        verifyNoInteractions(this.products);
    }

    @Test
    void mockPaymentStillCompletesAndIncreasesSales() {
        this.payment = null;
        PaymentStart started = this.service.pay(this.order, PaymentMethod.MOCK);
        assertNull(started.payUrl());
        assertEquals(PaymentStatus.SUCCESS, started.payment().getStatus());
        assertEquals(OrderStatus.PAID, this.order.getStatus());
        verify(this.products).addSales(11, 2);
        verify(this.alipay, never()).createPayUrl(any(), any(), any());
    }

    @Test
    void aPendingAlipayPaymentCannotBeReplacedByMockPayment() {
        assertThrows(TradeFailedException.class, () -> this.service.pay(this.order, PaymentMethod.MOCK));
        verify(this.orders, never()).update(any());
        verify(this.payments, never()).insert(any(PaymentEntity.class));
    }

    @Test
    void unsupportedAndDisabledMethodsCannotCreateSuccessfulPayments() {
        assertThrows(TradeFailedException.class, () -> this.service.pay(this.order, PaymentMethod.WECHAT));
        when(this.alipay.isEnabled()).thenReturn(false);
        assertThrows(TradeFailedException.class, () -> this.service.pay(this.order, PaymentMethod.ALIPAY));
        verifyNoInteractions(this.orders, this.payments, this.products);
    }

    @Test
    void exactExpiryBoundaryRejectsPaymentEvenBeforeTheTimeoutTaskRuns() {
        this.order.setCreatedAt(NOW.minusMinutes(15));
        assertThrows(TradeFailedException.class, () -> this.service.pay(this.order, PaymentMethod.ALIPAY));
        verify(this.payments, never()).insert(any(PaymentEntity.class));
        verify(this.alipay, never()).createPayUrl(any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = { "PAID", "CANCELLED", "REFUNDED", "SHIPPED", "COMPLETED" })
    void terminalAndPaidOrdersCannotBePaidAgain(OrderStatus status) {
        this.order.setStatus(status);
        assertThrows(TradeFailedException.class, () -> this.service.pay(this.order, PaymentMethod.ALIPAY));
        verify(this.payments, never()).insert(any(PaymentEntity.class));
        verifyNoInteractions(this.products);
    }

    @Test
    void queryingAnotherUsersOrderFailsBeforeCallingAlipay() {
        assertThrows(TradeFailedException.class, () -> this.service.query(42L, 8));
        verifyNoInteractions(this.payments, this.alipay, this.products);
    }

    @Test
    void queryFollowedByRepeatedCallbacksCountsTheSaleOnce() {
        when(this.alipay.query(any())).thenReturn(Optional.of(this.trade("TRADE_SUCCESS")));
        PaymentQuery result = this.service.query(42L, 7);
        assertTrue(result.paid());
        assertEquals("ALIPAY-TRADE-99", result.payment().getTradeNo());
        assertTrue(this.service.handleAlipayNotify(this.notification("33.60", "TRADE_SUCCESS")));
        assertTrue(this.service.handleAlipayNotify(this.notification("33.600", "TRADE_FINISHED")));
        assertTrue(this.service.query(42L, 7).paid());
        verify(this.products, times(1)).addSales(11, 2);
        verify(this.orders, times(1)).update(any());
        verify(this.alipay, times(1)).query(any());
    }

    @Test
    void unverifiedNotificationDoesNotAccessTheDatabase() {
        when(this.alipay.verifyNotification(any())).thenReturn(false);
        assertFalse(this.service.handleAlipayNotify(this.notification("33.60", "TRADE_SUCCESS")));
        verifyNoInteractions(this.orders, this.payments, this.products);
    }

    @Test
    void signedNotificationWithWrongAmountCannotMarkTheOrderPaid() {
        assertThrows(TradeFailedException.class,
            () -> this.service.handleAlipayNotify(this.notification("0.01", "TRADE_SUCCESS")));
        assertEquals(PaymentStatus.PENDING, this.payment.getStatus());
        verify(this.orders, never()).update(any());
        verify(this.payments, never()).updateById(any(PaymentEntity.class));
        verifyNoInteractions(this.products);
    }

    @Test
    void aQueryResultForAnotherMerchantPaymentIsRejected() {
        when(this.alipay.query(any())).thenReturn(Optional.of(new Trade(
            "ANOTHER-PAYMENT", "ALIPAY-TRADE-99", new BigDecimal("33.60"), "TRADE_SUCCESS")));
        assertThrows(TradeFailedException.class, () -> this.service.query(42L, 7));
        verify(this.orders, never()).update(any());
        verifyNoInteractions(this.products);
    }

    @Test
    void unsuccessfulTradeNeverChangesOrderStatusOrSales() {
        PaymentQuery result = this.service.query(42L, 7);
        assertFalse(result.paid());
        assertEquals(PaymentStatus.PENDING, result.payment().getStatus());
        verify(this.orders, never()).update(any());
        verifyNoInteractions(this.products);
    }

    @Test
    void closedTradeMarksThePendingPaymentFailedWithoutChangingInventory() {
        when(this.alipay.query(any())).thenReturn(Optional.of(this.trade("TRADE_CLOSED")));
        assertFalse(this.service.query(42L, 7).paid());
        assertEquals(PaymentStatus.FAILED, this.payment.getStatus());
        verify(this.orders, never()).update(any());
        verifyNoInteractions(this.products);
    }

    @Test
    void refundUpdatesLocalStateOnlyAfterAlipayConfirmsAndIsNotRepeated() {
        this.order.setStatus(OrderStatus.PAID);
        this.payment.setStatus(PaymentStatus.SUCCESS);
        this.payment.setTradeNo("ALIPAY-TRADE-99");
        assertTrue(this.service.refund(42L));
        assertFalse(this.service.refund(42L));
        assertEquals(PaymentStatus.REFUNDED, this.payment.getStatus());
        InOrder sequence = inOrder(this.alipay, this.orders, this.payments, this.products);
        sequence.verify(this.alipay).refund(this.payment);
        sequence.verify(this.orders).update(any());
        sequence.verify(this.payments).updateById(this.payment);
        sequence.verify(this.products).restoreStock(11, 2);
        sequence.verify(this.products).subtractSales(11, 2);
        verify(this.alipay, times(1)).refund(any());
    }

    @Test
    void failedAlipayRefundLeavesOrderPaymentInventoryAndSalesUnchanged() {
        this.order.setStatus(OrderStatus.PAID);
        this.payment.setStatus(PaymentStatus.SUCCESS);
        doThrow(new TradeFailedException("支付宝退款失败")).when(this.alipay).refund(any());
        assertThrows(TradeFailedException.class, () -> this.service.refund(42L));
        assertEquals(OrderStatus.PAID, this.order.getStatus());
        assertEquals(PaymentStatus.SUCCESS, this.payment.getStatus());
        verify(this.orders, never()).update(any());
        verify(this.payments, never()).updateById(any(PaymentEntity.class));
        verifyNoInteractions(this.products);
    }

    @Test
    void cancellationSynchronizesAnAlreadyPaidTradeAndDoesNotCloseIt() {
        when(this.alipay.query(any())).thenReturn(Optional.of(this.trade("TRADE_SUCCESS")));
        assertFalse(this.service.closePending(this.order));
        assertEquals(OrderStatus.PAID, this.order.getStatus());
        verify(this.alipay, never()).close(any());
        verify(this.products, never()).restoreStock(any(), any());
    }

    @Test
    void cancellationClosesAnUnpaidTradeBeforeAllowingInventoryToBeRestored() {
        when(this.alipay.close(any())).thenReturn(true);
        assertTrue(this.service.closePending(this.order));
        assertEquals(PaymentStatus.FAILED, this.payment.getStatus());
        InOrder sequence = inOrder(this.alipay, this.payments);
        sequence.verify(this.alipay).close(this.payment);
        sequence.verify(this.payments).updateById(this.payment);
    }

    @Test
    void paymentWinningTheRaceAgainstTradeCloseIsSynchronizedInsteadOfCancelled() {
        when(this.alipay.query(any())).thenReturn(
            Optional.of(this.trade("WAIT_BUYER_PAY")), Optional.of(this.trade("TRADE_SUCCESS")));
        when(this.alipay.close(any())).thenReturn(false);
        assertFalse(this.service.closePending(this.order));
        assertEquals(OrderStatus.PAID, this.order.getStatus());
        verify(this.products).addSales(11, 2);
        verify(this.products, never()).restoreStock(any(), any());
    }

    @Test
    void anUnopenedPaymentLinkRemainsPendingAfterCancellationForLatePaymentReconciliation() {
        when(this.alipay.query(any())).thenReturn(Optional.empty());
        assertTrue(this.service.closePending(this.order));
        assertEquals(PaymentStatus.PENDING, this.payment.getStatus());
        this.order.setStatus(OrderStatus.CANCELLED);
        when(this.alipay.query(any())).thenReturn(Optional.of(this.trade("TRADE_SUCCESS")));
        PaymentQuery result = this.service.query(42L, 7);
        assertFalse(result.paid());
        assertEquals(OrderStatus.CANCELLED, result.order().getStatus());
        assertEquals(PaymentStatus.REFUNDED, result.payment().getStatus());
        verify(this.alipay).refund(this.payment);
        verifyNoInteractions(this.products);
    }

    @Test
    void repeatedLatePaymentNotificationsRefundOnceWithoutReopeningTheCancelledOrder() {
        this.order.setStatus(OrderStatus.CANCELLED);
        assertTrue(this.service.handleAlipayNotify(this.notification("33.60", "TRADE_SUCCESS")));
        assertTrue(this.service.handleAlipayNotify(this.notification("33.60", "TRADE_SUCCESS")));
        assertEquals(OrderStatus.CANCELLED, this.order.getStatus());
        assertEquals(PaymentStatus.REFUNDED, this.payment.getStatus());
        verify(this.alipay, times(1)).refund(this.payment);
        verify(this.orders, never()).update(any());
        verifyNoInteractions(this.products);
    }

    @Test
    void reconciliationRefundsAnOlderPendingLedgerWhenTheOrderIsAlreadyPaid() {
        this.order.setStatus(OrderStatus.PAID);
        when(this.alipay.query(this.payment)).thenReturn(Optional.of(this.trade("TRADE_SUCCESS")));

        this.service.reconcile(99L);
        this.service.reconcile(99L);

        assertEquals(OrderStatus.PAID, this.order.getStatus());
        assertEquals(PaymentStatus.REFUNDED, this.payment.getStatus());
        verify(this.alipay, times(1)).query(this.payment);
        verify(this.alipay, times(1)).refund(this.payment);
        verify(this.payments, never()).selectLatestForUpdate(any());
        verify(this.orders, never()).update(any());
        verifyNoInteractions(this.products);
    }

    @Test
    void reconciliationRechecksTheLockedLedgerBeforeCallingAlipay() {
        PaymentEntity scanned = new PaymentEntity();
        scanned.setPaymentNo(this.payment.getPaymentNo());
        scanned.setOrderId(42L);
        scanned.setMethod(PaymentMethod.ALIPAY);
        scanned.setStatus(PaymentStatus.PENDING);
        when(this.payments.selectById(99L)).thenReturn(scanned);
        this.payment.setStatus(PaymentStatus.SUCCESS);

        this.service.reconcile(99L);

        InOrder sequence = inOrder(this.orders, this.payments);
        sequence.verify(this.payments).selectById(99L);
        sequence.verify(this.orders).selectByIdForUpdate(42L);
        sequence.verify(this.payments).selectByNoForUpdate(this.payment.getPaymentNo());
        verify(this.alipay, never()).query(any());
        verify(this.alipay, never()).refund(any());
        verifyNoInteractions(this.products);
    }

    @Test
    void expiredLinksWithNoAlipayTradeEventuallyStopBeingReconciled() {
        when(this.alipay.query(any())).thenReturn(Optional.empty());
        assertFalse(this.service.query(42L, 7).paid());
        assertEquals(PaymentStatus.PENDING, this.payment.getStatus());
        this.order.setCreatedAt(NOW.minusMinutes(16));
        assertFalse(this.service.query(42L, 7).paid());
        assertEquals(PaymentStatus.FAILED, this.payment.getStatus());
        verifyNoInteractions(this.products);
    }

    private Trade trade(String status) {
        return new Trade("TEST-PAYMENT-99", "ALIPAY-TRADE-99", new BigDecimal("33.60"), status);
    }

    private Map<String, String> notification(String amount, String status) {
        return Map.of("out_trade_no", "TEST-PAYMENT-99", "trade_no", "ALIPAY-TRADE-99",
            "total_amount", amount, "trade_status", status);
    }
}
