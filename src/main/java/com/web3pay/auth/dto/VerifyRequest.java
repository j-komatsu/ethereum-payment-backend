package com.web3pay.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
        @NotBlank String message,
        @NotBlank String signature,
        @NotBlank String address
) {}
