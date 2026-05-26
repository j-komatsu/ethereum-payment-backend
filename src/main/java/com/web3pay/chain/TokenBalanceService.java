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

    private final ChainRegistry chainRegistry;

    public TokenBalanceResponse getTokenBalance(String walletAddress, StablecoinType token) {
        String checksumAddress = Keys.toChecksumAddress(walletAddress);
        try {
            Web3j web3j = chainRegistry.resolve(token.getChainId());
            BigInteger rawBalance = callBalanceOf(web3j, checksumAddress, token.getContractAddress());
            BigDecimal humanBalance = TokenAmountConverter.toHuman(rawBalance, token.getDecimals());
            log.debug("Token balance query: address={} token={} raw={}", checksumAddress, token, rawBalance);
            return new TokenBalanceResponse(checksumAddress, token.name(), humanBalance.toPlainString(), rawBalance.toString());
        } catch (IOException e) {
            throw new ChainCommunicationException("ノードとの通信に失敗しました", e);
        }
    }

    @SuppressWarnings("rawtypes")
    private BigInteger callBalanceOf(Web3j web3j, String walletAddress, String contractAddress) throws IOException {
        Function function = new Function(
                "balanceOf",
                Collections.singletonList(new Address(walletAddress)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
        String encodedFunction = FunctionEncoder.encode(function);

        // from=null: eth_call は状態変更しないため送信者署名が不要
        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
        ).send();

        if (response.hasError()) {
            log.error("eth_call error: code={} message={}", response.getError().getCode(), response.getError().getMessage());
            throw new ChainCommunicationException("コントラクト呼び出しでエラーが発生しました", null);
        }

        if (response.isReverted()) {
            log.error("eth_call reverted: reason={}", response.getRevertReason());
            throw new ChainCommunicationException("コントラクト呼び出しが失敗しました", null);
        }

        List<Type> result = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (result.isEmpty()) {
            throw new ChainCommunicationException("コントラクトから空のレスポンスが返されました", null);
        }
        return ((Uint256) result.get(0)).getValue();
    }
}
