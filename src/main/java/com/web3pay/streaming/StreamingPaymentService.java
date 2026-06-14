package com.web3pay.streaming;

import com.web3pay.chain.ChainRegistry;
import com.web3pay.token.StablecoinType;
import com.web3pay.util.TokenAmountConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * Sablier Flow v1 ストリーミング決済サービス。
 *
 * Sablier Flow は「毎秒 X トークンを受取人に送り続ける」プロトコル。
 * このサービスはオンチェーン操作（create/cancel）とオフチェーン状態管理（DB）を組み合わせて実装する。
 *
 * 実際のコントラクト呼び出しはガス代（POL）が必要なためバックエンドが負担する想定。
 * テスト環境では Hardhat fork + Sablier コントラクトアドレスが必要。
 *
 * Sablier Flow on Polygon Mainnet:
 * https://docs.sablier.com/contracts/flow/deployments
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingPaymentService {

    // Sablier Flow コントラクト（Polygon Mainnet）— 環境変数で上書き可能
    private static final String SABLIER_FLOW_POLYGON = "0x1a272b596b10f8a0Cbb3eb04Eb787Af40B2c0F1";

    private final SablierStreamRepository streamRepository;
    private final ChainRegistry chainRegistry;

    @Value("${sablier.contract-address:#{null}}")
    private String sablierContractAddress;

    @Value("${sablier.enabled:false}")
    private boolean sablierEnabled;

    /**
     * DB にストリーム記録を作成し、オンチェーン create を呼び出す。
     *
     * SABLIER_ENABLED=true の場合のみ on-chain 操作を行う。
     * false（デフォルト）ではモック streamId を割り当て、DB のみ更新する（ローカル開発用）。
     */
    @Transactional
    public StreamResponse createStream(String ownerAddress, CreateStreamRequest request) {
        StablecoinType token = request.token();
        BigDecimal ratePerSecond = request.ratePerSecond();

        long mockStreamId = System.currentTimeMillis(); // 実際は on-chain から取得

        if (sablierEnabled) {
            mockStreamId = createOnChain(ownerAddress, request.receiverAddress(), token, ratePerSecond);
            log.info("Sablier stream created on-chain: streamId={} owner={}", mockStreamId, ownerAddress);
        } else {
            log.info("Sablier is disabled (mock mode): assigned streamId={}", mockStreamId);
        }

        SablierStream stream = SablierStream.builder()
                .streamId(mockStreamId)
                .walletAddress(ownerAddress)
                .receiverAddress(request.receiverAddress())
                .token(token)
                .ratePerSecond(ratePerSecond)
                .status(StreamStatus.ACTIVE)
                .build();

        SablierStream saved = streamRepository.save(stream);
        log.info("Sablier stream persisted: id={} streamId={}", saved.getId(), saved.getStreamId());
        return StreamResponse.from(saved);
    }

    /**
     * ストリームをキャンセルし、DB の status を CANCELED に更新する。
     */
    @Transactional
    public StreamResponse cancelStream(Long streamId, String callerAddress) {
        SablierStream stream = streamRepository.findByStreamId(streamId)
                .orElseThrow(() -> new StreamNotFoundException(streamId));

        if (!stream.getWalletAddress().equalsIgnoreCase(callerAddress)) {
            throw new IllegalArgumentException("ストリームのオーナーのみがキャンセルできます");
        }
        if (stream.getStatus() == StreamStatus.CANCELED) {
            throw new IllegalStateException("すでにキャンセル済みのストリームです: streamId=" + streamId);
        }

        if (sablierEnabled) {
            cancelOnChain(streamId);
            log.info("Sablier stream cancelled on-chain: streamId={}", streamId);
        }

        stream.setStatus(StreamStatus.CANCELED);
        stream.setCanceledAt(Instant.now());
        return StreamResponse.from(streamRepository.save(stream));
    }

    /**
     * Sablier コントラクトに withdrawable_amount(streamId) を call し、引き出し可能残高を返す。
     */
    public BigDecimal getWithdrawableAmount(Long streamId) throws IOException {
        SablierStream stream = streamRepository.findByStreamId(streamId)
                .orElseThrow(() -> new StreamNotFoundException(streamId));

        if (!sablierEnabled) {
            // モード: ratePerSecond × ストリーム経過秒数（概算）
            long elapsedSeconds = Instant.now().getEpochSecond() - stream.getCreatedAt().getEpochSecond();
            return stream.getRatePerSecond().multiply(BigDecimal.valueOf(Math.max(0, elapsedSeconds)));
        }

        Web3j web3j = chainRegistry.resolve(stream.getToken().getChainId());
        String contractAddress = resolveContractAddress();

        Function function = new Function(
                "withdrawableAmountOf",
                List.of(new Uint256(BigInteger.valueOf(streamId))),
                List.of(new TypeReference<Uint128>() {})
        );

        String encodedFunction = FunctionEncoder.encode(function);
        String response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
        ).send().getValue();

        List<Type> decoded = FunctionReturnDecoder.decode(response, function.getOutputParameters());
        if (decoded.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigInteger rawAmount = ((Uint128) decoded.get(0)).getValue();
        return TokenAmountConverter.toHuman(rawAmount, stream.getToken().getDecimals());
    }

    public List<StreamResponse> listStreams(String walletAddress) {
        return streamRepository.findByWalletAddress(walletAddress)
                .stream().map(StreamResponse::from).toList();
    }

    public StreamResponse getStream(Long streamId) {
        return StreamResponse.from(
                streamRepository.findByStreamId(streamId)
                        .orElseThrow(() -> new StreamNotFoundException(streamId)));
    }

    // --- on-chain helpers ---

    private long createOnChain(String owner, String receiver, StablecoinType token, BigDecimal ratePerSecond) {
        // Sablier Flow の create(token, sender, recipient, ratePerSecond, transferable) を呼ぶ
        // 返り値の streamId を取得してDBに保存する
        // TODO: RawTransactionManager でトランザクション送信（PermitService と同パターン）
        //       EIP-2612 Permit で approve の代わりに permit → createAndDeposit を使う設計
        throw new UnsupportedOperationException("On-chain Sablier integration is not yet wired (requires private key config)");
    }

    private void cancelOnChain(Long streamId) {
        // Sablier Flow の cancel(streamId) を呼ぶ
        throw new UnsupportedOperationException("On-chain Sablier integration is not yet wired (requires private key config)");
    }

    private String resolveContractAddress() {
        return sablierContractAddress != null ? sablierContractAddress : SABLIER_FLOW_POLYGON;
    }
}
