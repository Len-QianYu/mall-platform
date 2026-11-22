package com.example.api.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.Test;

import com.example.api.mapper.ProductNotificationEventMapper;
import com.example.api.service.ProductNotificationService;

class ProductNotificationMqTests {
    @Test
    void consumerRetriesTransientFailuresAndAcknowledgesSuccessfulProcessing() {
        ProductNotificationService notifications = mock(ProductNotificationService.class);
        ProductNotificationListener listener = new ProductNotificationListener(notifications);
        doThrow(new IllegalStateException("database unavailable")).doNothing().when(notifications).consumeEvent(42L);
        assertEquals(ConsumeResult.FAILURE, listener.consume(this.message("42")));
        assertEquals(ConsumeResult.SUCCESS, listener.consume(this.message("42")));
        verify(notifications, times(2)).consumeEvent(42L);
    }

    @Test
    void malformedEventsAreAcknowledgedWithoutBusinessWrites() {
        ProductNotificationService notifications = mock(ProductNotificationService.class);
        ProductNotificationListener listener = new ProductNotificationListener(notifications);
        for (String body : List.of("invalid", "0", "-1", "999999999999999999999999"))
            assertEquals(ConsumeResult.SUCCESS, listener.consume(this.message(body)));
        verifyNoInteractions(notifications);
    }

    @Test
    void failedPublishStaysPendingAndInflightMessagesAreNotSentAgain() {
        ProductNotificationEventMapper events = mock(ProductNotificationEventMapper.class);
        Producer producer = mock(Producer.class);
        when(events.selectPendingIds()).thenReturn(List.of(42L));
        CompletableFuture<SendReceipt> first = new CompletableFuture<>();
        when(producer.sendAsync(any())).thenReturn(first,
            CompletableFuture.completedFuture(mock(SendReceipt.class)));
        ProductNotificationProducer publisher = new ProductNotificationProducer(events, producer);
        publisher.publishPending();
        publisher.publishPending();
        verify(producer).sendAsync(any());
        verify(events, never()).markPublished(42L);
        first.completeExceptionally(new IllegalStateException("broker unavailable"));
        publisher.publishPending();
        verify(producer, times(2)).sendAsync(any());
        verify(events).markPublished(42L);
    }

    private MessageView message(String body) {
        MessageView message = mock(MessageView.class);
        when(message.getBody()).thenAnswer(invocation -> ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        return message;
    }
}
