package com.web3pay.chain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChainService {

    private final Web3j web3j;

    public EthBalanceResponse getEthBalance(String address) {
        try {
            BigInteger wei = web3j
                    .ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();

            BigDecimal eth = Convert.fromWei(wei.toString(), Convert.Unit.ETHER);
            log.debug("ETH balance query: address={} wei={}", address, wei);

            return new EthBalanceResponse(address, eth.toPlainString(), wei.toString());
        } catch (IOException e) {
            throw new ChainCommunicationException("Ethereum ノードとの通信に失敗しました", e);
        }
    }
}
