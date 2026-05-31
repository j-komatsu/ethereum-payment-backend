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
        PaymentOrder order1 = pendingOrder("order-1", Instant.now().minusSeconds(10));
        PaymentOrder order2 = pendingOrder("order-2", Instant.now().minusSeconds(60));

        when(repository.findByStatusAndExpiresAtBefore(eq(PaymentStatus.PENDING), any(Instant.class)))
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
    void expireOrders_withNoExpiredOrders_doesNotSave() {
        when(repository.findByStatusAndExpiresAtBefore(eq(PaymentStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        expiryJob.expireOrders();

        verify(repository, never()).saveAll(any());
    }

    @Test
    void expireOrders_queriesOnlyPendingOrders() {
        when(repository.findByStatusAndExpiresAtBefore(any(), any())).thenReturn(List.of());

        expiryJob.expireOrders();

        verify(repository).findByStatusAndExpiresAtBefore(eq(PaymentStatus.PENDING), any(Instant.class));
    }

    private PaymentOrder pendingOrder(String id, Instant expiresAt) {
        return PaymentOrder.builder()
                .id(id)
                .receiverAddress("0x" + "a".repeat(40))
                .expectedAmount(new BigDecimal("100"))
                .token(JPYC)
                .paymentMode(PaymentMode.MPM)
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.now().minusSeconds(120))
                .expiresAt(expiresAt)
                .build();
    }
}
