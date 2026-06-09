package com.web3pay.chain;

import com.web3pay.payment.PaymentService;
import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferEventPollerTest {

    @Mock ChainRegistry chainRegistry;
    @Mock PollerStateRepository pollerStateRepository;
    @Mock PaymentService paymentService;
    @Mock Web3j web3j;

    @InjectMocks TransferEventPoller poller;

    // ERC-20 Transfer(address indexed from, address indexed to, uint256 value)
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final String RECEIVER = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final String SENDER   = "0x" + "a".repeat(40);
    private static final String TX_HASH  = "0x" + "b".repeat(64);
    private static final long BLOCK = 10_000L;
    private static final StablecoinType TOKEN = StablecoinType.JPYC;

    @BeforeEach
    void setUp() {
        lenient().when(chainRegistry.resolve(TOKEN.getChainId())).thenReturn(web3j);
        ReflectionTestUtils.setField(poller, "pollerEnabled", true);
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private void mockBlockNumber(long block) throws IOException {
        EthBlockNumber result = new EthBlockNumber();
        result.setResult("0x" + Long.toHexString(block));
        Request<?, EthBlockNumber> req = mock(Request.class);
        when(req.send()).thenReturn(result);
        when(web3j.ethBlockNumber()).thenReturn((Request) req);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockGetLogs(List<EthLog.LogObject> logObjects) throws IOException {
        EthLog ethLog = new EthLog();
        ethLog.setResult((List) logObjects);
        Request<?, EthLog> req = mock(Request.class);
        when(req.send()).thenReturn(ethLog);
        when(web3j.ethGetLogs(any())).thenReturn((Request) req);
    }

    @SuppressWarnings("unchecked")
    private void mockGetLogsThrows() throws IOException {
        Request<?, EthLog> req = mock(Request.class);
        when(req.send()).thenThrow(new IOException("node unavailable"));
        when(web3j.ethGetLogs(any())).thenReturn((Request) req);
    }

    private EthLog.LogObject buildLog(String toAddress, BigInteger amount, String txHash) {
        EthLog.LogObject log = new EthLog.LogObject();
        String fromPadded = "0x" + "0".repeat(24) + SENDER.substring(2).toLowerCase();
        String toPadded   = "0x" + "0".repeat(24) + toAddress.substring(2).toLowerCase();
        log.setTopics(List.of(TRANSFER_TOPIC, fromPadded, toPadded));
        log.setData(Numeric.toHexStringWithPrefixZeroPadded(amount, 64));
        log.setTransactionHash(txHash);
        return log;
    }

    private PollerState stateAt(long block) {
        return PollerState.builder()
                .token(TOKEN.name())
                .lastProcessedBlock(block)
                .build();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void poll_disabled_doesNotInteractWithChain() {
        ReflectionTestUtils.setField(poller, "pollerEnabled", false);
        poller.poll();
        verifyNoInteractions(web3j, pollerStateRepository, paymentService);
    }

    @Test
    void poll_withTransferLog_callsConfirmPayment() throws IOException {
        mockBlockNumber(BLOCK);
        when(pollerStateRepository.findById(TOKEN.name()))
                .thenReturn(Optional.of(stateAt(BLOCK - 100)));
        when(pollerStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BigInteger rawAmount = BigInteger.TEN.pow(18); // 1 JPYC
        mockGetLogs(List.of(buildLog(RECEIVER, rawAmount, TX_HASH)));

        poller.poll();

        verify(paymentService).confirmPayment(
                eq(RECEIVER.toLowerCase()), eq(TOKEN), eq(rawAmount), eq(TX_HASH));
    }

    @Test
    void poll_firstRun_usesInitialLookbackAndAdvancesState() throws IOException {
        // No existing state → initial lookback of 200 blocks
        long safeBlock = BLOCK - 20; // REQUIRED_CONFIRMATIONS = 20
        mockBlockNumber(BLOCK);
        when(pollerStateRepository.findById(TOKEN.name())).thenReturn(Optional.empty());
        mockGetLogs(List.of());
        when(pollerStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        poller.poll();

        ArgumentCaptor<PollerState> captor = ArgumentCaptor.forClass(PollerState.class);
        verify(pollerStateRepository).save(captor.capture());
        assertThat(captor.getValue().getLastProcessedBlock()).isEqualTo(safeBlock);
    }

    @Test
    void poll_noNewBlocks_skipsEthGetLogs() throws IOException {
        long safeBlock = BLOCK - 20;
        mockBlockNumber(BLOCK);
        // lastProcessed already at safeBlock → fromBlock > toBlock
        when(pollerStateRepository.findById(TOKEN.name()))
                .thenReturn(Optional.of(stateAt(safeBlock)));

        poller.poll();

        verify(web3j, never()).ethGetLogs(any());
        verify(paymentService, never()).confirmPayment(any(), any(), any(), any());
        verify(pollerStateRepository, never()).save(any());
    }

    @Test
    void poll_networkError_doesNotAdvanceState() throws IOException {
        mockBlockNumber(BLOCK);
        when(pollerStateRepository.findById(TOKEN.name()))
                .thenReturn(Optional.of(stateAt(BLOCK - 100)));
        mockGetLogsThrows();

        poller.poll();

        verify(pollerStateRepository, never()).save(any());
        verify(paymentService, never()).confirmPayment(any(), any(), any(), any());
    }

    @Test
    void poll_invalidTxHash_skipsLogButAdvancesState() throws IOException {
        mockBlockNumber(BLOCK);
        when(pollerStateRepository.findById(TOKEN.name()))
                .thenReturn(Optional.of(stateAt(BLOCK - 100)));
        when(pollerStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EthLog.LogObject badLog = buildLog(RECEIVER, BigInteger.TEN, "not-a-valid-hash");
        mockGetLogs(List.of(badLog));

        poller.poll();

        verify(paymentService, never()).confirmPayment(any(), any(), any(), any());
        verify(pollerStateRepository).save(any(PollerState.class));
    }

    @Test
    void poll_multipleTransferLogs_callsConfirmPaymentForEach() throws IOException {
        mockBlockNumber(BLOCK);
        when(pollerStateRepository.findById(TOKEN.name()))
                .thenReturn(Optional.of(stateAt(BLOCK - 100)));
        when(pollerStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String other = "0x" + "c".repeat(40);
        mockGetLogs(List.of(
                buildLog(RECEIVER, BigInteger.valueOf(100L), TX_HASH),
                buildLog(other,    BigInteger.valueOf(200L), "0x" + "d".repeat(64))
        ));

        poller.poll();

        verify(paymentService, times(2)).confirmPayment(any(), eq(TOKEN), any(), any());
    }
}
