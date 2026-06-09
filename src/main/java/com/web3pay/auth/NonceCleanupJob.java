package com.web3pay.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NonceCleanupJob {

    private final SiweNonceRepository nonceRepository;

    @Scheduled(fixedDelayString = "${auth.siwe.cleanup-interval-ms:60000}")
    public void deleteExpiredNonces() {
        nonceRepository.deleteExpiredBefore(Instant.now());
        log.debug("Expired SIWE nonces cleaned up");
    }
}
