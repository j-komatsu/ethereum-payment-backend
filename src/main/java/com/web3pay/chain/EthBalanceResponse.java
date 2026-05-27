package com.web3pay.chain;

public record EthBalanceResponse(
        String address,
        String balanceEth,
        String balanceWei
) {}
