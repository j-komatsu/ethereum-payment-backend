package com.example.payment.repository;

import com.example.payment.model.PaymentOrder;
import com.example.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    List<PaymentOrder> findByStatus(PaymentStatus status);

    List<PaymentOrder> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant now);
}
