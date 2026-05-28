package com.web3pay.chain.permit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ExecutePermitRequest(
        @NotBlank String paymentOrderId,
        @NotBlank String nonce,
        @Positive long deadline,
        @NotBlank String signature
) {}
