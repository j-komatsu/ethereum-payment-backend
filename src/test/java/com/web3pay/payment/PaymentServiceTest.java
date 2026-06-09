package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentOrderRepository repository;

    @InjectMocks
    private PaymentService paymentService;

    private static final String TX_HASH = "0x" + "a".repeat(64);
    private static final String RECEIVER = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final StablecoinType TOKEN = StablecoinType.JPYC;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(paymentService, "defaultTtlSeconds", 3600L);
    }

    // ------------------------------------------------------------------ helpers

    private PaymentOrder pendingOrder(BigDecimal expectedAmount) {
        return PaymentOrder.builder()
                .id("order-1")
                .receiverAddress(RECEIVER)
                .token(TOKEN)
                .expectedAmount(expectedAmount)
                .paymentMode(PaymentMode.MPM)
                .senderAddress("0x" + "b".repeat(40))
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private BigInteger toRaw(BigDecimal human) {
        return human.movePointRight(TOKEN.getDecimals()).toBigIntegerExact();
    }

    // ------------------------------------------------------------------ confirmPayment tests

    @Test
    void confirmPayment_exactAmount_setsConfirmed() {
        BigDecimal expected = new BigDecimal("100");
        PaymentOrder order = pendingOrder(expected);

        when(repository.existsByTxHash(TX_HASH)).thenReturn(false);
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(
                PaymentStatus.PENDING, RECEIVER, TOKEN))
                .thenReturn(Optional.of(order));

        paymentService.confirmPayment(RECEIVER, TOKEN, toRaw(expected), TX_HASH);

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(repository).save(captor.capture());
        PaymentOrder saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(saved.getTxHash()).isEqualTo(TX_HASH);
        assertThat(saved.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirmPayment_excessAmount_setsOverpaid() {
        BigDecimal expected = new BigDecimal("100");
        PaymentOrder order = pendingOrder(expected);

        when(repository.existsByTxHash(TX_HASH)).thenReturn(false);
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(
                PaymentStatus.PENDING, RECEIVER, TOKEN))
                .thenReturn(Optional.of(order));

        paymentService.confirmPayment(RECEIVER, TOKEN, toRaw(new BigDecimal("150")), TX_HASH);

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.OVERPAID);
    }

    @Test
    void confirmPayment_shortAmount_setsUnderpaid() {
        BigDecimal expected = new BigDecimal("100");
        PaymentOrder order = pendingOrder(expected);

        when(repository.existsByTxHash(TX_HASH)).thenReturn(false);
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(
                PaymentStatus.PENDING, RECEIVER, TOKEN))
                .thenReturn(Optional.of(order));

        paymentService.confirmPayment(RECEIVER, TOKEN, toRaw(new BigDecimal("50")), TX_HASH);

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.UNDERPAID);
    }

    @Test
    void confirmPayment_duplicateTxHash_skipsProcessing() {
        when(repository.existsByTxHash(TX_HASH)).thenReturn(true);

        paymentService.confirmPayment(RECEIVER, TOKEN, toRaw(new BigDecimal("100")), TX_HASH);

        verify(repository, never()).findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void confirmPayment_noMatchingOrder_skipsProcessing() {
        when(repository.existsByTxHash(TX_HASH)).thenReturn(false);
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(
                PaymentStatus.PENDING, RECEIVER, TOKEN))
                .thenReturn(Optional.empty());

        paymentService.confirmPayment(RECEIVER, TOKEN, toRaw(new BigDecimal("100")), TX_HASH);

        verify(repository, never()).save(any());
    }

    @Test
    void confirmPayment_addressCaseInsensitive_matchesOrder() {
        BigDecimal expected = new BigDecimal("100");
        PaymentOrder order = pendingOrder(expected);
        String lowerReceiver = RECEIVER.toLowerCase();

        when(repository.existsByTxHash(TX_HASH)).thenReturn(false);
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(
                PaymentStatus.PENDING, lowerReceiver, TOKEN))
                .thenReturn(Optional.of(order));

        paymentService.confirmPayment(lowerReceiver, TOKEN, toRaw(expected), TX_HASH);

        verify(repository).save(any(PaymentOrder.class));
    }

    // ------------------------------------------------------------------ claimOrder tests

    private static final String CONSUMER = "0x" + "c".repeat(40);
    private static final String NONCE = "a".repeat(64);

    private PaymentOrder awaitingConsumerOrder() {
        return PaymentOrder.builder()
                .id("cpm-order-1")
                .receiverAddress(RECEIVER)
                .token(TOKEN)
                .expectedAmount(new BigDecimal("500"))
                .paymentMode(PaymentMode.CPM)
                .consumerNonce(NONCE)
                .status(PaymentStatus.AWAITING_CONSUMER)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    void claimOrder_validNonce_setsPendingAndSenderAddress() {
        PaymentOrder order = awaitingConsumerOrder();
        when(repository.findById("cpm-order-1")).thenReturn(Optional.of(order));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentOrder result = paymentService.claimOrder("cpm-order-1", CONSUMER, NONCE);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getSenderAddress()).isEqualTo(CONSUMER);
    }

    @Test
    void claimOrder_wrongNonce_throwsIllegalArgument() {
        PaymentOrder order = awaitingConsumerOrder();
        when(repository.findById("cpm-order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.claimOrder("cpm-order-1", CONSUMER, "b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumerNonce");
    }

    @Test
    void claimOrder_notCpmMode_throwsIllegalArgument() {
        PaymentOrder mpmOrder = pendingOrder(new BigDecimal("100"));
        when(repository.findById("order-1")).thenReturn(Optional.of(mpmOrder));

        assertThatThrownBy(() -> paymentService.claimOrder("order-1", CONSUMER, NONCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CPM");
    }

    @Test
    void claimOrder_alreadyClaimed_throwsIllegalArgument() {
        PaymentOrder order = awaitingConsumerOrder();
        order.setStatus(PaymentStatus.PENDING);
        when(repository.findById("cpm-order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.claimOrder("cpm-order-1", CONSUMER, NONCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("確定済み");
    }
}
