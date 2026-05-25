package com.web3pay.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderRepository repository;

    @Value("${payment.default-ttl-seconds:3600}")
    private long defaultTtlSeconds;

    @Transactional
    public PaymentOrder createOrder(CreatePaymentRequest request) {
        long ttl = request.getTtlSeconds() != null ? request.getTtlSeconds() : defaultTtlSeconds;

        PaymentOrder order = PaymentOrder.builder()
                .receiverAddress(request.getReceiverAddress())
                .expectedAmount(request.getAmount())
                .token(request.getToken())
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
    public List<PaymentOrder> listOrders(String statusParam) {
        if (statusParam == null) {
            return repository.findAll();
        }
        PaymentStatus status = PaymentStatus.valueOf(statusParam.toUpperCase());
        return repository.findByStatus(status);
    }
}
