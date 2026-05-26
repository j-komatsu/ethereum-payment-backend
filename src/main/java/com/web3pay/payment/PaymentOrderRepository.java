package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    Page<PaymentOrder> findByStatus(PaymentStatus status, Pageable pageable);

    List<PaymentOrder> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant now);

    // OrderByCreatedAtAsc ensures deterministic match when multiple PENDING orders share the same receiver+token
    Optional<PaymentOrder> findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(
            PaymentStatus status, String receiverAddress, StablecoinType token);

    boolean existsByTxHash(String txHash);
}
