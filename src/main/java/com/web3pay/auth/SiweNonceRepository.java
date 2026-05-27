package com.web3pay.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface SiweNonceRepository extends JpaRepository<SiweNonce, String> {

    @Modifying
    @Query("DELETE FROM SiweNonce n WHERE n.expiresAt < :before")
    void deleteExpiredBefore(Instant before);
}
