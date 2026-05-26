package com.web3pay.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

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

    private static String generateConsumerNonce() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
