package com.web3pay.streaming;

import com.web3pay.token.StablecoinType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateStreamRequest(

        @NotNull
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "有効なEthereumアドレス形式を入力してください")
        String receiverAddress,

        @NotNull
        StablecoinType token,

        @NotNull
        @DecimalMin(value = "0.000000001", message = "ratePerSecond は 0 より大きい値を指定してください")
        @DecimalMax(value = "1000000", message = "ratePerSecond は 1,000,000 以下で指定してください")
        BigDecimal ratePerSecond
) {
}
