package com.example.payment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StablecoinType {
    USDC("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", 6),
    USDT("0xdAC17F958D2ee523a2206206994597C13D831ec7", 6),
    DAI("0x6B175474E89094C44Da98b954EedeAC495271d0F", 18);

    private final String mainnetAddress;
    private final int decimals;
}
