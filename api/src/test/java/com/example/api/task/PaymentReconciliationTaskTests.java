package com.example.api.task;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.api.entity.PaymentEntity;
import com.example.api.exception.TradeFailedException;
import com.example.api.service.PaymentService;

class PaymentReconciliationTaskTests {

    private PaymentService payments;
    private PaymentReconciliationTask task;

    @BeforeEach
    void setUp() {
        this.payments = mock(PaymentService.class);
        this.task = new PaymentReconciliationTask(this.payments);
    }

    @Test
    void laterPaymentsAreReconciledWhileTheFirstHundredRemainPending() {
        when(this.payments.fetchPendingAlipay(0, 100)).thenReturn(this.batch(1, 100));
        when(this.payments.fetchPendingAlipay(100, 100)).thenReturn(this.batch(101, 101));

        this.task.reconcile();
        this.task.reconcile();

        verify(this.payments, times(1)).reconcile(1L);
        verify(this.payments, times(1)).reconcile(100L);
        verify(this.payments, times(1)).reconcile(101L);
        verify(this.payments, never()).query(anyLong(), eq(7));
    }

    @Test
    void failedPaymentsDoNotBlockLaterBatchesAndAreRetriedAfterWrapping() {
        when(this.payments.fetchPendingAlipay(0, 100)).thenReturn(this.batch(1, 100));
        when(this.payments.fetchPendingAlipay(100, 100)).thenReturn(this.batch(101, 101));
        when(this.payments.fetchPendingAlipay(101, 100)).thenReturn(List.of());
        doThrow(new TradeFailedException("test query failure")).when(this.payments).reconcile(1L);

        this.task.reconcile();
        this.task.reconcile();
        this.task.reconcile();

        verify(this.payments, times(2)).reconcile(1L);
        verify(this.payments, times(2)).reconcile(100L);
        verify(this.payments, times(1)).reconcile(101L);
    }

    @Test
    void anEmptyQueueDoesNotQueryAnyAlipayTrade() {
        when(this.payments.fetchPendingAlipay(0, 100)).thenReturn(List.of());

        this.task.reconcile();

        verify(this.payments, never()).reconcile(anyLong());
    }

    private List<PaymentEntity> batch(long firstId, long lastId) {
        return LongStream.rangeClosed(firstId, lastId).mapToObj(id -> {
            PaymentEntity payment = new PaymentEntity();
            payment.setId(id);
            payment.setOrderId(42L);
            payment.setUserId(7);
            payment.setPaymentNo("TEST-PAYMENT-" + id);
            return payment;
        }).toList();
    }
}
