package com.web3pay.token;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StablecoinType {
    // TODO: Verify contract address and permitName against on-chain name() before production use
    JPYC("0x431D5dfF03120AFA4bDf332c61A6e1766eF37BF6", 18, 137, "JPY Coin",       "1", true),
    // Polygon native USDC (Circle 公式発行, chainId=137)
    USDC("0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359", 6,  137, "USD Coin",       "2", true),
    // Polygon USDT (Tether 公式, chainId=137) — EIP-2612 非対応
    USDT("0xc2132D05D31c914a87C6611C10748AEb04B58e8F", 6,  137, "Tether USD",     "1", false),
    // DAI は学習用に Ethereum Mainnet (chainId=1) を維持
    DAI( "0x6B175474E89094C44Da98b954EedeAC495271d0F", 18,  1,  "Dai Stablecoin", "1", true);

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
