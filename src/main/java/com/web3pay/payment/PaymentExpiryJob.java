package com.web3pay.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryJob {

    private final PaymentOrderRepository repository;

    @Scheduled(fixedDelayString = "${payment.expiry-check-interval-ms:60000}")
    @Transactional
    public void expireOrders() {
        List<PaymentOrder> expired = repository.findByStatusInAndExpiresAtBefore(
                List.of(PaymentStatus.PENDING, PaymentStatus.AWAITING_CONSUMER), Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(order -> {
            order.setStatus(PaymentStatus.EXPIRED);
            log.info("Expired payment order id={} expiresAt={}", order.getId(), order.getExpiresAt());
        });

        repository.saveAll(expired);
        log.info("Expired {} payment order(s)", expired.size());
    }
}
