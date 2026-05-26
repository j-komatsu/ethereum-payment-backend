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
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndToken(
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
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndToken(
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
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndToken(
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

        verify(repository, never()).findFirstByStatusAndReceiverAddressIgnoreCaseAndToken(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void confirmPayment_noMatchingOrder_skipsProcessing() {
        when(repository.existsByTxHash(TX_HASH)).thenReturn(false);
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndToken(
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
        when(repository.findFirstByStatusAndReceiverAddressIgnoreCaseAndToken(
                PaymentStatus.PENDING, lowerReceiver, TOKEN))
                .thenReturn(Optional.of(order));

        paymentService.confirmPayment(lowerReceiver, TOKEN, toRaw(expected), TX_HASH);

        verify(repository).save(any(PaymentOrder.class));
    }
}
