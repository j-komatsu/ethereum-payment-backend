package com.web3pay.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoPurchaseExecutionRepository extends JpaRepository<AutoPurchaseExecution, String> {

    List<AutoPurchaseExecution> findByWalletAddress(String walletAddress);

    List<AutoPurchaseExecution> findByStatus(ExecutionStatus status);
}
