package com.web3pay.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class TokenAmountConverter {

    private TokenAmountConverter() {}

    /**
     * オンチェーンの生値（rawAmount）を人間可読の小数に変換する。
     * 例: JPYC(decimals=18) 1_000_000_000_000_000_000 → "1"
     *     USDC(decimals=6)  1_000_000 → "1"
     */
    public static BigDecimal toHuman(BigInteger rawAmount, int decimals) {
        return new BigDecimal(rawAmount).movePointLeft(decimals).stripTrailingZeros();
    }

    /**
     * 人間可読の金額をオンチェーンの生値に変換する。
     * 例: JPYC(decimals=18) "1.5" → 1_500_000_000_000_000_000
     * トークンの精度を超えた小数点以下は例外。
     */
    public static BigInteger toRaw(BigDecimal humanAmount, int decimals) {
        return humanAmount.movePointRight(decimals)
                .setScale(0, RoundingMode.UNNECESSARY)
                .toBigIntegerExact();
    }
}
