package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    Page<PaymentOrder> findByStatus(PaymentStatus status, Pageable pageable);

    List<PaymentOrder> findByStatusAndExpiresAtBefore(PaymentStatus status, Instant now);

    List<PaymentOrder> findByStatusInAndExpiresAtBefore(List<PaymentStatus> statuses, Instant now);

    // OrderByCreatedAtAsc ensures deterministic match when multiple PENDING orders share the same receiver+token
    Optional<PaymentOrder> findFirstByStatusAndReceiverAddressIgnoreCaseAndTokenOrderByCreatedAtAsc(
            PaymentStatus status, String receiverAddress, StablecoinType token);

    boolean existsByTxHash(String txHash);

    /** Atomically transitions an order from expectedStatus to newStatus.
     * Returns 1 if the update succeeded, 0 if the order was not in expectedStatus. */
    @Modifying
    @Transactional
    @Query("UPDATE PaymentOrder o SET o.status = :newStatus WHERE o.id = :id AND o.status = :expectedStatus")
    int updateStatusConditionally(
            @Param("id") String id,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("newStatus") PaymentStatus newStatus);
}
