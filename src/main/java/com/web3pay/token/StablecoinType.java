package com.web3pay.token;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StablecoinType {
    // TODO: Verify contract address and permitName against on-chain name() before production use
    JPYC("0x431D5dfF03120AFA4bDf332c61A6e1766eF37BF6", 18, 137, "JPY Coin", "1", true),
    USDC("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", 6,   1,   "USD Coin", "2", true),
    USDT("0xdAC17F958D2ee523a2206206994597C13D831ec7", 6,   1,   "Tether USD", "1", false), // not EIP-2612
    DAI( "0x6B175474E89094C44Da98b954EedeAC495271d0F", 18,  1,   "Dai Stablecoin", "1", true);

    private final String contractAddress;
    private final int decimals;
    private final int chainId;
    /** EIP-712 domain name — must match the value returned by name() on the contract */
    private final String permitName;
    /** EIP-712 domain version — must match the version used when the contract was deployed */
    private final String permitVersion;
    /** Whether this token supports EIP-2612 permit */
    private final boolean permitSupported;
}
