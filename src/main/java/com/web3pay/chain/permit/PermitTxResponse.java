package com.web3pay.chain.permit;

public record PermitTxResponse(
        String paymentOrderId,
        String permitTxHash,
        String transferTxHash,
        String status
) {}
