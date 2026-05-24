package com.example.payment.controller;

import com.example.payment.model.StablecoinType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {

    @NotBlank
    private String receiverAddress;

    @NotNull
    @DecimalMin("0.000001")
    private BigDecimal amount;

    @NotNull
    private StablecoinType token;

    private Long ttlSeconds;
}
