package com.web3pay.payment;

import com.web3pay.token.StablecoinType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {

    @NotBlank
    @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "有効なEthereumアドレス形式（0x + 40文字の16進数）を入力してください")
    private String receiverAddress;

    @NotNull
    @DecimalMin("0.000001")
    private BigDecimal amount;

    @NotNull
    private StablecoinType token;

    @Max(86400)
    private Long ttlSeconds;
}
