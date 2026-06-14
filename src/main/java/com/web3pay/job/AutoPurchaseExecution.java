package com.web3pay.job;

import com.web3pay.token.StablecoinType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "auto_purchase_executions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoPurchaseExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 42)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StablecoinType token;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    private String txHash;

    private String failureReason;

    @Column(nullable = false)
    private Instant executedAt;

    @PrePersist
    void prePersist() {
        executedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AutoPurchaseExecution)) return false;
        AutoPurchaseExecution that = (AutoPurchaseExecution) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
