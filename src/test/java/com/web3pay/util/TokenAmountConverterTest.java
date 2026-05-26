package com.web3pay.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.*;

class TokenAmountConverterTest {

    @Test
    void toHuman_jpyc18decimals_convertsCorrectly() {
        // 1 JPYC = 10^18 raw
        BigInteger raw = BigInteger.TEN.pow(18);
        assertThat(toHuman(raw, 18)).isEqualByComparingTo("1");
    }

    @Test
    void toHuman_usdc6decimals_convertsCorrectly() {
        // 1.5 USDC = 1_500_000 raw
        BigInteger raw = BigInteger.valueOf(1_500_000L);
        assertThat(toHuman(raw, 6)).isEqualByComparingTo("1.5");
    }

    @Test
    void toHuman_zero_returnsZero() {
        assertThat(toHuman(BigInteger.ZERO, 18)).isEqualByComparingTo("0");
    }

    @Test
    void toHuman_negativeRawAmount_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TokenAmountConverter.toHuman(BigInteger.valueOf(-1), 18))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toHuman_nullRawAmount_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TokenAmountConverter.toHuman(null, 18))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toRaw_jpyc18decimals_convertsCorrectly() {
        BigDecimal human = new BigDecimal("1.5");
        BigInteger expected = new BigDecimal("1.5").movePointRight(18).toBigIntegerExact();
        assertThat(TokenAmountConverter.toRaw(human, 18)).isEqualTo(expected);
    }

    @Test
    void toRaw_usdc6decimals_convertsCorrectly() {
        BigDecimal human = new BigDecimal("100");
        assertThat(TokenAmountConverter.toRaw(human, 6)).isEqualTo(BigInteger.valueOf(100_000_000L));
    }

    @Test
    void toRaw_excessivePrecision_throwsArithmeticException() {
        // USDC は 6 decimals なので 7桁以上の小数は不正
        BigDecimal human = new BigDecimal("1.0000001");
        assertThatThrownBy(() -> TokenAmountConverter.toRaw(human, 6))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void roundTrip_humanToRawToHuman() {
        BigDecimal original = new BigDecimal("1234.56");
        BigInteger raw = TokenAmountConverter.toRaw(original, 6);
        BigDecimal result = TokenAmountConverter.toHuman(raw, 6);
        assertThat(result).isEqualByComparingTo(original);
    }

    private BigDecimal toHuman(BigInteger raw, int decimals) {
        return TokenAmountConverter.toHuman(raw, decimals);
    }
}
