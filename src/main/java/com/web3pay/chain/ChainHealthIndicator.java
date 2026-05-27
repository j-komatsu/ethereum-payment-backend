package com.web3pay.chain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChainHealthIndicator implements HealthIndicator {

    private final Web3j web3j;

    @Override
    public Health health() {
        try {
            long blockNumber = web3j.ethBlockNumber().send().getBlockNumber().longValueExact();
            return Health.up()
                    .withDetail("blockNumber", blockNumber)
                    .build();
        } catch (Exception e) {
            log.warn("Ethereum node health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
