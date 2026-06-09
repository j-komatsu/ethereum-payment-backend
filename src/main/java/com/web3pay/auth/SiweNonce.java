package com.web3pay.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "siwe_nonces")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiweNonce {

    @Id
    @Column(length = 32)
    private String nonce;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SiweNonce)) return false;
        SiweNonce that = (SiweNonce) o;
        return Objects.equals(nonce, that.nonce);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nonce);
    }
}
