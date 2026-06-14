package com.web3pay.job;

import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoPurchaseJobTest {

    @Mock
    private AutoPurchaseSubscriptionRepository subscriptionRepository;

    @Mock
    private AutoPurchaseProcessor processor;

    @InjectMocks
    private AutoPurchaseJob job;

    @Test
    void executeMonthlyPurchases_noSubscriptions_doesNothing() {
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of());

        job.executeMonthlyPurchases();

        verify(processor, never()).process(any());
    }

    @Test
    void executeMonthlyPurchases_withSubscription_delegatesToProcessor() {
        AutoPurchaseSubscription sub = buildSubscription("0xabc1230000000000000000000000000000000001",
                new BigDecimal("1000"));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        job.executeMonthlyPurchases();

        verify(processor).process(sub);
    }

    @Test
    void executeMonthlyPurchases_multipleSubscriptions_processesAll() {
        AutoPurchaseSubscription sub1 = buildSubscription("0xabc1230000000000000000000000000000000001",
                new BigDecimal("1000"));
        AutoPurchaseSubscription sub2 = buildSubscription("0xdef4560000000000000000000000000000000002",
                new BigDecimal("500"));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub1, sub2));

        job.executeMonthlyPurchases();

        verify(processor).process(sub1);
        verify(processor).process(sub2);
    }

    private AutoPurchaseSubscription buildSubscription(String walletAddress, BigDecimal monthlyAmount) {
        AutoPurchaseSubscription sub = new AutoPurchaseSubscription();
        sub.setId("sub-" + walletAddress.substring(2, 6));
        sub.setWalletAddress(walletAddress);
        sub.setReceiverAddress("0x0000000000000000000000000000000000000099");
        sub.setToken(StablecoinType.JPYC);
        sub.setMonthlyAmount(monthlyAmount);
        sub.setActive(true);
        return sub;
    }
}
