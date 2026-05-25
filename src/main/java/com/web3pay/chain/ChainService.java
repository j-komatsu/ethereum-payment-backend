package com.web3pay.chain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
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
        // EIP-55 チェックサム形式に正規化してログの追跡性を確保する
        String checksumAddress = Keys.toChecksumAddress(address);
        try {
            // LATEST を使用: リオーグで瞬間的に値が変わる可能性あり（残高照会用途のため許容）
            // 決済確定判定には FINALIZED/SAFE を使うこと（Phase 4 以降で実装）
            BigInteger wei = web3j
                    .ethGetBalance(checksumAddress, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();

            BigDecimal eth = Convert.fromWei(wei.toString(), Convert.Unit.ETHER);
            log.debug("ETH balance query: address={} wei={}", checksumAddress, wei);

            return new EthBalanceResponse(checksumAddress, eth.toPlainString(), wei.toString());
        } catch (IOException e) {
            throw new ChainCommunicationException("Ethereum ノードとの通信に失敗しました", e);
        }
    }
}
