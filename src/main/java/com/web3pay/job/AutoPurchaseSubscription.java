package com.web3pay.job;

import com.web3pay.token.StablecoinType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * ユーザーが事前承認した自動引き落とし設定。
 * allowance を持つウォレットが毎月 amount を自動的に支払う。
 */
@Entity
@Table(name = "auto_purchase_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoPurchaseSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 42)
    private String walletAddress;

    @Column(nullable = false, length = 42)
    private String receiverAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StablecoinType token;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal monthlyAmount;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant canceledAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        active = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AutoPurchaseSubscription)) return false;
        AutoPurchaseSubscription that = (AutoPurchaseSubscription) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
