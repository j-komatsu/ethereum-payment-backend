package com.web3pay.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClaimPaymentRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9a-f]{64}$", message = "consumerNonce は64文字の16進数である必要があります")
        String consumerNonce
) {}
