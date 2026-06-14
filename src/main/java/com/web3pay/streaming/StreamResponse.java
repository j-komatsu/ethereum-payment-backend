package com.web3pay.streaming;

import com.web3pay.token.StablecoinType;

import java.math.BigDecimal;
import java.time.Instant;

public record StreamResponse(
        String id,
        Long streamId,
        String walletAddress,
        String receiverAddress,
        StablecoinType token,
        BigDecimal ratePerSecond,
        StreamStatus status,
        Instant createdAt,
        Instant canceledAt
) {
    static StreamResponse from(SablierStream stream) {
        return new StreamResponse(
                stream.getId(),
                stream.getStreamId(),
                stream.getWalletAddress(),
                stream.getReceiverAddress(),
                stream.getToken(),
                stream.getRatePerSecond(),
                stream.getStatus(),
                stream.getCreatedAt(),
                stream.getCanceledAt()
        );
    }
}
