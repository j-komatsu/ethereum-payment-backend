package com.web3pay.chain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "poller_states")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PollerState {

    @Id
    @Column(length = 10)
    private String token;

    @Column(nullable = false)
    private Long lastProcessedBlock;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PollerState)) return false;
        PollerState that = (PollerState) o;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(token);
    }
}
