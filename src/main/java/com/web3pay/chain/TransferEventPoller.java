package com.web3pay.chain;

import com.web3pay.payment.PaymentService;
import com.web3pay.token.StablecoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventPoller {

    private static final Event TRANSFER_EVENT = new Event("Transfer", List.of(
            new TypeReference<Address>(true) {},
            new TypeReference<Address>(true) {},
            new TypeReference<Uint256>() {}
    ));
    private static final String TRANSFER_TOPIC = EventEncoder.encode(TRANSFER_EVENT);

    // Polygon PoS: 20 blocks ≈ 40 seconds after confirmation
    private static final int REQUIRED_CONFIRMATIONS = 20;
    // Max block range per poll to stay within Infura/Alchemy free-tier limits
    private static final long MAX_BLOCK_RANGE = 2_000;
    // Initial lookback on first run (avoids scanning the entire chain)
    private static final long INITIAL_LOOKBACK_BLOCKS = 200;

    private final ChainRegistry chainRegistry;
    private final PollerStateRepository pollerStateRepository;
    private final PaymentService paymentService;

    @Value("${poller.enabled:true}")
    private boolean pollerEnabled;

    @Scheduled(fixedDelayString = "${poller.interval-ms:15000}")
    void poll() {
        if (!pollerEnabled) {
            return;
        }
        // Phase 4: JPYC on Polygon only
        pollToken(StablecoinType.JPYC);
    }

    private void pollToken(StablecoinType token) {
        Web3j web3j = chainRegistry.resolve(token.getChainId());
        try {
            long currentBlock = web3j.ethBlockNumber().send().getBlockNumber().longValue();
            long safeBlock = currentBlock - REQUIRED_CONFIRMATIONS;
            if (safeBlock < 0) {
                return;
            }

            PollerState state = pollerStateRepository.findById(token.name())
                    .orElseGet(() -> PollerState.builder()
                            .token(token.name())
                            .lastProcessedBlock(safeBlock - INITIAL_LOOKBACK_BLOCKS)
                            .build());

            long fromBlock = state.getLastProcessedBlock() + 1;
            long toBlock = Math.min(safeBlock, fromBlock + MAX_BLOCK_RANGE - 1);

            if (fromBlock > toBlock) {
                log.debug("No new confirmed blocks to process for {}. fromBlock={} toBlock={}", token.name(), fromBlock, toBlock);
                return;
            }

            log.debug("Polling {} Transfer events: blocks {}-{}", token.name(), fromBlock, toBlock);
            processTransfers(web3j, token, fromBlock, toBlock);

            state.setLastProcessedBlock(toBlock);
            pollerStateRepository.save(state);

        } catch (Exception e) {
            // Do not advance lastProcessedBlock on error — retry next interval
            log.error("Failed to poll {} Transfer events: {}", token.name(), e.getMessage(), e);
        }
    }

    private void processTransfers(Web3j web3j, StablecoinType token, long fromBlock, long toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                token.getContractAddress()
        );
        filter.addSingleTopic(TRANSFER_TOPIC);

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        if (ethLog.hasError()) {
            log.error("eth_getLogs error: {}", ethLog.getError().getMessage());
            return;
        }

        var logs = ethLog.getLogs();
        if (logs.isEmpty()) {
            return;
        }

        log.info("Found {} Transfer event(s) for {} in blocks {}-{}", logs.size(), token.name(), fromBlock, toBlock);

        for (var result : logs) {
            EthLog.LogObject logObj = (EthLog.LogObject) result.get();
            processLog(logObj, token);
        }
    }

    private void processLog(EthLog.LogObject logObj, StablecoinType token) {
        try {
            List<String> topics = logObj.getTopics();
            if (topics.size() < 3) {
                return;
            }
            // topics[1] = from (indexed, padded to 32 bytes)
            // topics[2] = to   (indexed, padded to 32 bytes)
            String to = decodeAddress(topics.get(2));
            BigInteger rawAmount = Numeric.decodeQuantity(logObj.getData());
            String txHash = logObj.getTransactionHash();

            log.debug("Transfer event: to={} amount={} txHash={}", to, rawAmount, txHash);
            paymentService.confirmPayment(to, token, rawAmount, txHash);

        } catch (Exception e) {
            log.error("Failed to process Transfer log txHash={}: {}", logObj.getTransactionHash(), e.getMessage(), e);
        }
    }

    private static String decodeAddress(String paddedHex) {
        // Remove 0x prefix, take last 40 chars (20 bytes = address)
        String hex = Numeric.cleanHexPrefix(paddedHex);
        return "0x" + hex.substring(hex.length() - 40);
    }
}
