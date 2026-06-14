package com.web3pay.streaming;

import com.web3pay.token.StablecoinType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sablier_streams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SablierStream {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private Long streamId;

    @Column(nullable = false, length = 42)
    private String walletAddress;

    @Column(nullable = false, length = 42)
    private String receiverAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StablecoinType token;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal ratePerSecond;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StreamStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant canceledAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        if (status == null) {
            status = StreamStatus.ACTIVE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SablierStream)) return false;
        SablierStream that = (SablierStream) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
