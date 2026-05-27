package com.web3pay.token;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StablecoinType {
    JPYC("0xE7C3D8C9a439feDe00D2600032D5dB0Be71C3c29", 18, 137),  // Polygon Mainnet / 新JPYC（JPYC EX対応・電子決済手段版）/ ガス代: POL（旧MATIC）
    USDC("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", 6,   1),   // Ethereum Mainnet
    USDT("0xdAC17F958D2ee523a2206206994597C13D831ec7", 6,   1),   // Ethereum Mainnet
    DAI( "0x6B175474E89094C44Da98b954EedeAC495271d0F", 18,  1);   // Ethereum Mainnet

    private final String contractAddress;
    private final int decimals;
    private final int chainId;
}
