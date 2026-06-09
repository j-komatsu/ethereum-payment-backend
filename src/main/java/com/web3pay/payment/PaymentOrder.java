package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMode paymentMode;

    @Column(nullable = false)
    private String receiverAddress;

    @Column(nullable = true)
    private String senderAddress;

    @Column(nullable = true, length = 64)
    private String consumerNonce;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StablecoinType token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(unique = true, length = 66)
    private String txHash;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant confirmedAt;

    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (paymentMode == null) {
            paymentMode = PaymentMode.MPM;
        }
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentOrder)) return false;
        PaymentOrder that = (PaymentOrder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
