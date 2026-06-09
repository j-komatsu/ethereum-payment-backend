package com.web3pay.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.web3pay.token.StablecoinType.JPYC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentExpiryJobTest {

    @Mock
    PaymentOrderRepository repository;

    @InjectMocks
    PaymentExpiryJob expiryJob;

    @Test
    void expireOrders_withExpiredPendingOrders_updatesStatusToExpired() {
        PaymentOrder order1 = orderWithStatus("order-1", PaymentStatus.PENDING, Instant.now().minusSeconds(10));
        PaymentOrder order2 = orderWithStatus("order-2", PaymentStatus.PENDING, Instant.now().minusSeconds(60));

        when(repository.findByStatusInAndExpiresAtBefore(
                eq(List.of(PaymentStatus.PENDING, PaymentStatus.AWAITING_CONSUMER)), any(Instant.class)))
                .thenReturn(List.of(order1, order2));

        expiryJob.expireOrders();

        assertThat(order1.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(order2.getStatus()).isEqualTo(PaymentStatus.EXPIRED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PaymentOrder>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void expireOrders_withExpiredAwaitingConsumerOrder_updatesStatusToExpired() {
        PaymentOrder cpmOrder = orderWithStatus("cpm-1", PaymentStatus.AWAITING_CONSUMER, Instant.now().minusSeconds(30));

        when(repository.findByStatusInAndExpiresAtBefore(
                eq(List.of(PaymentStatus.PENDING, PaymentStatus.AWAITING_CONSUMER)), any(Instant.class)))
                .thenReturn(List.of(cpmOrder));

        expiryJob.expireOrders();

        assertThat(cpmOrder.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(repository).saveAll(List.of(cpmOrder));
    }

    @Test
    void expireOrders_withNoExpiredOrders_doesNotSave() {
        when(repository.findByStatusInAndExpiresAtBefore(any(), any()))
                .thenReturn(List.of());

        expiryJob.expireOrders();

        verify(repository, never()).saveAll(any());
    }

    @Test
    void expireOrders_queriesPendingAndAwaitingConsumer() {
        when(repository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of());

        expiryJob.expireOrders();

        verify(repository).findByStatusInAndExpiresAtBefore(
                eq(List.of(PaymentStatus.PENDING, PaymentStatus.AWAITING_CONSUMER)),
                any(Instant.class));
    }

    private PaymentOrder orderWithStatus(String id, PaymentStatus status, Instant expiresAt) {
        return PaymentOrder.builder()
                .id(id)
                .receiverAddress("0x" + "a".repeat(40))
                .expectedAmount(new BigDecimal("100"))
                .token(JPYC)
                .paymentMode(PaymentMode.MPM)
                .status(status)
                .createdAt(Instant.now().minusSeconds(120))
                .expiresAt(expiresAt)
                .build();
    }
}
