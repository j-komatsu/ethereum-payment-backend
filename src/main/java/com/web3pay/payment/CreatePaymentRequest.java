package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "有効なEthereumアドレス形式（0x + 40文字の16進数）を入力してください")
        String receiverAddress,

        @NotBlank
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "有効なEthereumアドレス形式（0x + 40文字の16進数）を入力してください")
        String senderAddress,

        @NotNull
        @DecimalMin("0.000001")
        BigDecimal amount,

        @NotNull
        StablecoinType token,

        @Min(1)
        @Max(86400)
        Long ttlSeconds
) {}
