package com.web3pay.job;

import com.web3pay.chain.ChainRegistry;
import com.web3pay.token.StablecoinType;
import com.web3pay.util.TokenAmountConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * 自動購入の1サブスクリプション処理。
 * REQUIRES_NEW により件ごとに独立したトランザクション。
 * 1件失敗しても他の実行記録はロールバックされない。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoPurchaseProcessor {

    private final AutoPurchaseExecutionRepository executionRepository;
    private final ChainRegistry chainRegistry;

    @Value("${permit.spender-address:}")
    private String spenderAddress;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(AutoPurchaseSubscription sub) {
        StablecoinType token = sub.getToken();
        BigDecimal monthlyAmount = sub.getMonthlyAmount();
        String walletAddress = sub.getWalletAddress();

        try {
            BigDecimal allowance = fetchAllowance(walletAddress, token);
            if (allowance.compareTo(monthlyAmount) < 0) {
                log.warn("Skipping subscription id={}: insufficient allowance ({} < {})",
                        sub.getId(), allowance, monthlyAmount);
                record(walletAddress, token, monthlyAmount,
                        ExecutionStatus.SKIPPED_INSUFFICIENT_ALLOWANCE, null,
                        "allowance=" + allowance + " < required=" + monthlyAmount);
                return;
            }

            log.info("[DRY_RUN] Would transferFrom wallet={} amount={} {} to receiver={}",
                    walletAddress, monthlyAmount, token, sub.getReceiverAddress());

            // TODO: RawTransactionManager.sendTransaction() で ERC-20 transferFrom を実行
            record(walletAddress, token, monthlyAmount, ExecutionStatus.SUCCESS, null, null);

        } catch (Exception e) {
            log.error("AutoPurchaseJob failed for subscription id={}: {}", sub.getId(), e.getMessage(), e);
            record(walletAddress, token, monthlyAmount, ExecutionStatus.FAILED, null, e.getMessage());
        }
    }

    private BigDecimal fetchAllowance(String walletAddress, StablecoinType token) throws IOException {
        if (spenderAddress == null || spenderAddress.isBlank()) {
            throw new IllegalStateException("permit.spender-address が未設定のため allowance チェックができません");
        }
        Web3j web3j = chainRegistry.resolve(token.getChainId());

        Function function = new Function(
                "allowance",
                List.of(
                        new org.web3j.abi.datatypes.Address(walletAddress),
                        new org.web3j.abi.datatypes.Address(spenderAddress)
                ),
                List.of(new TypeReference<Uint256>() {})
        );

        String encodedFunction = FunctionEncoder.encode(function);
        String response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, token.getContractAddress(), encodedFunction),
                DefaultBlockParameterName.LATEST
        ).send().getValue();

        List<Type> decoded = FunctionReturnDecoder.decode(response, function.getOutputParameters());
        if (decoded.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigInteger rawAllowance = ((Uint256) decoded.get(0)).getValue();
        return TokenAmountConverter.toHuman(rawAllowance, token.getDecimals());
    }

    private void record(String walletAddress, StablecoinType token, BigDecimal amount,
                        ExecutionStatus status, String txHash, String failureReason) {
        executionRepository.save(AutoPurchaseExecution.builder()
                .walletAddress(walletAddress)
                .token(token)
                .amount(amount)
                .status(status)
                .txHash(txHash)
                .failureReason(failureReason)
                .build());
    }
}
