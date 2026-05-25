package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String receiverAddress;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StablecoinType token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String txHash;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant confirmedAt;

    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
    }
}
