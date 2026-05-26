package com.web3pay.chain;

import com.web3pay.token.StablecoinType;
import com.web3pay.util.TokenAmountConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBalanceService {

    private final Web3j web3j;

    public TokenBalanceResponse getTokenBalance(String walletAddress, StablecoinType token) {
        String checksumAddress = Keys.toChecksumAddress(walletAddress);
        try {
            BigInteger rawBalance = callBalanceOf(checksumAddress, token.getContractAddress());
            BigDecimal humanBalance = TokenAmountConverter.toHuman(rawBalance, token.getDecimals());
            log.debug("Token balance query: address={} token={} raw={}", checksumAddress, token, rawBalance);
            return new TokenBalanceResponse(checksumAddress, token.name(), humanBalance.toPlainString(), rawBalance.toString());
        } catch (IOException e) {
            throw new ChainCommunicationException("Ethereum ノードとの通信に失敗しました", e);
        }
    }

    @SuppressWarnings("rawtypes")
    private BigInteger callBalanceOf(String walletAddress, String contractAddress) throws IOException {
        Function function = new Function(
                "balanceOf",
                Collections.singletonList(new Address(walletAddress)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
        String encodedFunction = FunctionEncoder.encode(function);

        // LATEST を使用: 残高照会用途のため許容（決済確定には使用禁止）
        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(walletAddress, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
        ).send();

        if (response.isReverted()) {
            throw new ChainCommunicationException("コントラクト呼び出しが失敗しました: " + response.getRevertReason(), null);
        }

        List<Type> result = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return ((Uint256) result.get(0)).getValue();
    }
}
