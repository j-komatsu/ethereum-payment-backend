package com.web3pay.job;

import com.web3pay.chain.ChainRegistry;
import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoPurchaseJobTest {

    @Mock
    private AutoPurchaseSubscriptionRepository subscriptionRepository;

    @Mock
    private AutoPurchaseExecutionRepository executionRepository;

    @Mock
    private ChainRegistry chainRegistry;

    @InjectMocks
    private AutoPurchaseJob job;

    @Test
    void executeMonthlyPurchases_noSubscriptions_doesNothing() {
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of());

        job.executeMonthlyPurchases();

        verify(executionRepository, never()).save(any());
    }

    @Test
    void executeMonthlyPurchases_withSubscription_recordsExecution() {
        AutoPurchaseSubscription sub = buildSubscription("0xabc1230000000000000000000000000000000001",
                new BigDecimal("1000"));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        // chainRegistry.resolve() will throw because no actual Web3j is wired
        // The job should catch this and record FAILED
        when(chainRegistry.resolve(137)).thenThrow(new RuntimeException("No node"));
        when(executionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.executeMonthlyPurchases();

        verify(executionRepository).save(argThat(exec ->
                exec.getStatus() == ExecutionStatus.FAILED &&
                exec.getFailureReason() != null
        ));
    }

    private AutoPurchaseSubscription buildSubscription(String walletAddress, BigDecimal monthlyAmount) {
        AutoPurchaseSubscription sub = new AutoPurchaseSubscription();
        sub.setId("sub-1");
        sub.setWalletAddress(walletAddress);
        sub.setReceiverAddress("0xdef4560000000000000000000000000000000002");
        sub.setToken(StablecoinType.JPYC);
        sub.setMonthlyAmount(monthlyAmount);
        sub.setActive(true);
        return sub;
    }
}
