package com.web3pay.chain;

public record TokenBalanceResponse(
        String address,
        String token,
        String balance,
        String rawBalance
) {}
