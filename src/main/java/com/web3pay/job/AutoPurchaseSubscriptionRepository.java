package com.web3pay.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoPurchaseSubscriptionRepository extends JpaRepository<AutoPurchaseSubscription, String> {

    List<AutoPurchaseSubscription> findByActiveTrue();

    List<AutoPurchaseSubscription> findByWalletAddress(String walletAddress);
}
