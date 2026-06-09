package com.web3pay.token;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StablecoinTypeTest {

    @Test
    void allTokens_contractAddressMatchesEthereumFormat() {
        for (StablecoinType token : StablecoinType.values()) {
            assertThat(token.getContractAddress())
                    .as("contractAddress of %s", token)
                    .matches("^0x[0-9a-fA-F]{40}$");
        }
    }

    @Test
    void allTokens_decimalsArePositive() {
        for (StablecoinType token : StablecoinType.values()) {
            assertThat(token.getDecimals())
                    .as("decimals of %s", token)
                    .isPositive();
        }
    }

    @Test
    void allTokens_chainIdIsPositive() {
        for (StablecoinType token : StablecoinType.values()) {
            assertThat(token.getChainId())
                    .as("chainId of %s", token)
                    .isPositive();
        }
    }

    @Test
    void allTokens_permitNameAndVersionAreNotBlank() {
        for (StablecoinType token : StablecoinType.values()) {
            assertThat(token.getPermitName())
                    .as("permitName of %s", token)
                    .isNotBlank();
            assertThat(token.getPermitVersion())
                    .as("permitVersion of %s", token)
                    .isNotBlank();
        }
    }

    @Test
    void usdt_isNotPermitSupported() {
        assertThat(StablecoinType.USDT.isPermitSupported()).isFalse();
    }

    @Test
    void jpycUsdcDai_arePermitSupported() {
        List<StablecoinType> supported = List.of(StablecoinType.JPYC, StablecoinType.USDC, StablecoinType.DAI);
        for (StablecoinType token : supported) {
            assertThat(token.isPermitSupported())
                    .as("%s should be permit-supported", token)
                    .isTrue();
        }
    }

    @Test
    void jpycAndUsdcAndUsdt_areOnPolygon() {
        for (StablecoinType token : List.of(StablecoinType.JPYC, StablecoinType.USDC, StablecoinType.USDT)) {
            assertThat(token.getChainId())
                    .as("chainId of %s", token)
                    .isEqualTo(137);
        }
    }

    @Test
    void dai_isOnEthereumMainnet() {
        assertThat(StablecoinType.DAI.getChainId()).isEqualTo(1);
    }

    @Test
    void jpycAndDai_have18Decimals() {
        assertThat(StablecoinType.JPYC.getDecimals()).isEqualTo(18);
        assertThat(StablecoinType.DAI.getDecimals()).isEqualTo(18);
    }

    @Test
    void usdcAndUsdt_have6Decimals() {
        assertThat(StablecoinType.USDC.getDecimals()).isEqualTo(6);
        assertThat(StablecoinType.USDT.getDecimals()).isEqualTo(6);
    }
}
