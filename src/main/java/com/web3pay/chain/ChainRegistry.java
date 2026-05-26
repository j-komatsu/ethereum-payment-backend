package com.web3pay.chain;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;

import java.util.Map;

@Component
public class ChainRegistry {

    private final Map<Integer, Web3j> nodes;

    public ChainRegistry(
            @Qualifier("polygonWeb3j") Web3j polygonWeb3j,
            @Qualifier("ethereumWeb3j") Web3j ethereumWeb3j) {
        this.nodes = Map.of(
                137, polygonWeb3j,
                1, ethereumWeb3j
        );
    }

    public Web3j resolve(int chainId) {
        Web3j web3j = nodes.get(chainId);
        if (web3j == null) {
            throw new UnsupportedOperationException("未対応のチェーンID: " + chainId);
        }
        return web3j;
    }
}
