package com.web3pay.streaming;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SablierStreamRepository extends JpaRepository<SablierStream, String> {

    Optional<SablierStream> findByStreamId(Long streamId);

    List<SablierStream> findByWalletAddress(String walletAddress);

    List<SablierStream> findByStatus(StreamStatus status);
}
