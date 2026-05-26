package com.web3pay.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderRepository repository;

    @Value("${payment.default-ttl-seconds:3600}")
    private long defaultTtlSeconds;

    @Transactional
    public PaymentOrder createOrder(CreatePaymentRequest request) {
        long ttl = request.ttlSeconds() != null ? request.ttlSeconds() : defaultTtlSeconds;

        PaymentOrder order = PaymentOrder.builder()
                .receiverAddress(request.receiverAddress())
                .senderAddress(request.senderAddress())
                .expectedAmount(request.amount())
                .token(request.token())
                .status(PaymentStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(ttl))
                .build();

        PaymentOrder saved = repository.save(order);
        log.info("Created payment order id={} token={} amount={}", saved.getId(), saved.getToken(), saved.getExpectedAmount());
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
}
