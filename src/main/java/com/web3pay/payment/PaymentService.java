package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import com.web3pay.util.TokenAmountConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderRepository repository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${payment.default-ttl-seconds:3600}")
    private long defaultTtlSeconds;

    @Transactional
    public PaymentOrder createOrder(CreatePaymentRequest request) {
        PaymentMode mode = request.paymentMode() != null ? request.paymentMode() : PaymentMode.MPM;

        if (mode == PaymentMode.MPM) {
            if (request.senderAddress() == null || request.senderAddress().isBlank()) {
                throw new IllegalArgumentException("MPM モードでは senderAddress は必須です");
            }
        } else if (mode == PaymentMode.CPM) {
            if (request.senderAddress() != null && !request.senderAddress().isBlank()) {
                throw new IllegalArgumentException("CPM モードでは senderAddress は指定できません（消費者のアドレスはQRスキャン後に確定します）");
            }
        }

        String consumerNonce = (mode == PaymentMode.CPM) ? generateConsumerNonce() : null;
        PaymentStatus initialStatus = (mode == PaymentMode.CPM)
                ? PaymentStatus.AWAITING_CONSUMER
                : PaymentStatus.PENDING;

        long ttl = request.ttlSeconds() != null ? request.ttlSeconds() : defaultTtlSeconds;

        PaymentOrder order = PaymentOrder.builder()
                .receiverAddress(request.receiverAddress())
                .senderAddress(request.senderAddress())
                .expectedAmount(request.amount())
                .token(request.token())
                .paymentMode(mode)
                .consumerNonce(consumerNonce)
                .status(initialStatus)
                .expiresAt(Instant.now().plusSeconds(ttl))
                .build();

        PaymentOrder saved = repository.save(order);
        log.info("Created payment order id={} mode={} token={} amount={}", saved.getId(), mode, saved.getToken(), saved.getExpectedAmount());
        return saved;
    }

    @Transactional(readOnly = true)
    public PaymentOrder getOrder(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new PaymentOrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<PaymentOrder> listOrders(String statusParam, Pageable pageable) {
        if (statusParam == null) {
            return repository.findAll(pageable);
        }
        PaymentStatus status;
        try {
            status = PaymentStatus.valueOf(statusParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不正なステータス値です: " + statusParam);
        }
        return repository.findByStatus(status, pageable);
    }

    /**
     * Called when a CPM consumer scans the QR code and confirms their address.
     * Transitions the order from AWAITING_CONSUMER → PENDING.
     * The consumerNonce embedded in the QR code acts as a one-time claim token.
     */
    @Transactional
    public PaymentOrder claimOrder(String orderId, String consumerAddress, String consumerNonce) {
        PaymentOrder order = getOrder(orderId);

        if (order.getPaymentMode() != PaymentMode.CPM) {
            throw new IllegalArgumentException("このオーダーは CPM モードではありません");
        }
        if (order.getStatus() != PaymentStatus.AWAITING_CONSUMER) {
            throw new IllegalArgumentException("このオーダーはすでに確定済みです: " + order.getStatus());
        }
        if (!consumerNonce.equals(order.getConsumerNonce())) {
            throw new IllegalArgumentException("consumerNonce が一致しません");
        }

        order.setSenderAddress(consumerAddress);
        order.setStatus(PaymentStatus.PENDING);
        PaymentOrder saved = repository.save(order);
        log.info("CPM order claimed id={} consumer={}", orderId, consumerAddress);
        return saved;
    }

    /**
     * Called by TransferEventPoller when a Transfer event is detected on-chain.
     * Matches the incoming transfer to a PENDING PaymentOrder and updates its status.
     * Idempotent: if txHash already processed, silently skips.
     */
    @Transactional
    public void confirmPayment(String toAddress, StablecoinType token, BigInteger rawAmount, String txHash) {
        if (repository.existsByTxHash(txHash)) {
            log.debug("Already processed txHash={}, skipping", txHash);
            return;
        }

        Optional<PaymentOrder> optOrder = repository
                .findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(PaymentStatus.PENDING, toAddress, token);

        if (optOrder.isEmpty()) {
            log.debug("No PENDING order for receiver={} token={}", toAddress, token);
            return;
        }

        PaymentOrder order = optOrder.get();
        BigDecimal actual = TokenAmountConverter.toHuman(rawAmount, token.getDecimals());
        int cmp = actual.compareTo(order.getExpectedAmount());
        PaymentStatus newStatus = cmp == 0 ? PaymentStatus.CONFIRMED
                : cmp > 0 ? PaymentStatus.OVERPAID
                : PaymentStatus.UNDERPAID;

        order.setStatus(newStatus);
        order.setTxHash(txHash);
        order.setConfirmedAt(Instant.now());
        repository.save(order);

        log.info("Payment {} id={} txHash={} expected={} actual={}",
                newStatus, order.getId(), txHash, order.getExpectedAmount(), actual);
    }

    private static String generateConsumerNonce() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
