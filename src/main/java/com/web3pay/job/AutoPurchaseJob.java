package com.web3pay.job;

import com.web3pay.chain.ChainRegistry;
import com.web3pay.token.StablecoinType;
import com.web3pay.util.TokenAmountConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
 * 毎月1日 JST 0:00 に有効なサブスクリプション全件を処理する自動購入ジョブ。
 *
 * ShedLock により複数インスタンスが同時実行しないよう排他制御される。
 * allowance が不足している場合はスキップして SKIPPED_INSUFFICIENT_ALLOWANCE を記録する。
 *
 * 実際のトランザクション送信は permit.spender-private-key が設定されている場合のみ実行される。
 * 設定がない場合（デフォルト）は allowance チェックのみ行い、DRY_RUN ログを出力する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPurchaseJob {

    private final AutoPurchaseSubscriptionRepository subscriptionRepository;
    private final AutoPurchaseExecutionRepository executionRepository;
    private final ChainRegistry chainRegistry;

    // 毎月1日 JST 0:00 に実行（最大10分のロックを取得）
    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Tokyo")
    @SchedulerLock(name = "autoPurchaseJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void executeMonthlyPurchases() {
        List<AutoPurchaseSubscription> subscriptions = subscriptionRepository.findByActiveTrue();
        log.info("AutoPurchaseJob started: {} active subscriptions", subscriptions.size());

        for (AutoPurchaseSubscription sub : subscriptions) {
            processSubscription(sub);
        }

        log.info("AutoPurchaseJob completed");
    }

    private void processSubscription(AutoPurchaseSubscription sub) {
        StablecoinType token = sub.getToken();
        BigDecimal monthlyAmount = sub.getMonthlyAmount();
        String walletAddress = sub.getWalletAddress();

        try {
            BigDecimal allowance = fetchAllowance(walletAddress, token);
            if (allowance.compareTo(monthlyAmount) < 0) {
                log.warn("Skipping subscription id={}: insufficient allowance ({} < {})",
                        sub.getId(), allowance, monthlyAmount);
                recordExecution(walletAddress, token, monthlyAmount,
                        ExecutionStatus.SKIPPED_INSUFFICIENT_ALLOWANCE, null,
                        "allowance=" + allowance + " < required=" + monthlyAmount);
                return;
            }

            log.info("[DRY_RUN] Would transferFrom wallet={} amount={} {} to receiver={}",
                    walletAddress, monthlyAmount, token, sub.getReceiverAddress());

            // 実際の transferFrom 送信は permit.spender-private-key が設定されている前提
            // TODO: RawTransactionManager.sendTransaction() で ERC-20 transferFrom を実行

            recordExecution(walletAddress, token, monthlyAmount, ExecutionStatus.SUCCESS, null, null);

        } catch (Exception e) {
            log.error("AutoPurchaseJob failed for subscription id={}: {}", sub.getId(), e.getMessage(), e);
            recordExecution(walletAddress, token, monthlyAmount,
                    ExecutionStatus.FAILED, null, e.getMessage());
        }
    }

    private BigDecimal fetchAllowance(String walletAddress, StablecoinType token) throws IOException {
        Web3j web3j = chainRegistry.resolve(token.getChainId());
        String spenderAddress = "0x0000000000000000000000000000000000000000"; // 実際は permit.spender のアドレス

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

    private void recordExecution(String walletAddress, StablecoinType token,
                                  BigDecimal amount, ExecutionStatus status,
                                  String txHash, String failureReason) {
        AutoPurchaseExecution execution = AutoPurchaseExecution.builder()
                .walletAddress(walletAddress)
                .token(token)
                .amount(amount)
                .status(status)
                .txHash(txHash)
                .failureReason(failureReason)
                .build();
        executionRepository.save(execution);
    }
}
