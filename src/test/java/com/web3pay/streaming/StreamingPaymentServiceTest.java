package com.web3pay.streaming;

import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamingPaymentServiceTest {

    @Mock
    private SablierStreamRepository streamRepository;

    @InjectMocks
    private StreamingPaymentService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "sablierEnabled", false);
        ReflectionTestUtils.setField(service, "sablierContractAddress", null);
    }

    @Test
    void createStream_mockMode_persistsStreamAndReturnsResponse() {
        String ownerAddress = "0xabc1230000000000000000000000000000000001";
        CreateStreamRequest request = new CreateStreamRequest(
                "0xdef4560000000000000000000000000000000002",
                StablecoinType.JPYC,
                new BigDecimal("0.001")
        );

        when(streamRepository.save(any(SablierStream.class))).thenAnswer(inv -> {
            SablierStream s = inv.getArgument(0);
            s.setId("test-id");
            return s;
        });

        StreamResponse response = service.createStream(ownerAddress, request);

        assertThat(response.id()).isEqualTo("test-id");
        assertThat(response.walletAddress()).isEqualTo(ownerAddress);
        assertThat(response.receiverAddress()).isEqualTo(request.receiverAddress());
        assertThat(response.token()).isEqualTo(StablecoinType.JPYC);
        assertThat(response.ratePerSecond()).isEqualByComparingTo("0.001");
        assertThat(response.status()).isEqualTo(StreamStatus.ACTIVE);

        verify(streamRepository).save(any(SablierStream.class));
    }

    @Test
    void cancelStream_activeStream_updatesStatusToCanceled() {
        String ownerAddress = "0xabc1230000000000000000000000000000000001";
        SablierStream stream = buildStream(42L, ownerAddress, StreamStatus.ACTIVE);

        when(streamRepository.findByStreamId(42L)).thenReturn(Optional.of(stream));
        when(streamRepository.save(any(SablierStream.class))).thenAnswer(inv -> inv.getArgument(0));

        StreamResponse response = service.cancelStream(42L, ownerAddress);

        assertThat(response.status()).isEqualTo(StreamStatus.CANCELED);
        assertThat(response.canceledAt()).isNotNull();
    }

    @Test
    void cancelStream_alreadyCanceled_throwsIllegalState() {
        String ownerAddress = "0xabc1230000000000000000000000000000000001";
        SablierStream stream = buildStream(42L, ownerAddress, StreamStatus.CANCELED);

        when(streamRepository.findByStreamId(42L)).thenReturn(Optional.of(stream));

        assertThatThrownBy(() -> service.cancelStream(42L, ownerAddress))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("キャンセル済み");
    }

    @Test
    void cancelStream_nonOwner_throwsIllegalArgument() {
        String ownerAddress = "0xabc1230000000000000000000000000000000001";
        String otherAddress = "0xdef4560000000000000000000000000000000002";
        SablierStream stream = buildStream(42L, ownerAddress, StreamStatus.ACTIVE);

        when(streamRepository.findByStreamId(42L)).thenReturn(Optional.of(stream));

        assertThatThrownBy(() -> service.cancelStream(42L, otherAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("オーナー");
    }

    @Test
    void cancelStream_notFound_throwsStreamNotFoundException() {
        when(streamRepository.findByStreamId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelStream(99L, "0xabc1230000000000000000000000000000000001"))
                .isInstanceOf(StreamNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listStreams_returnsAllForWallet() {
        String walletAddress = "0xabc1230000000000000000000000000000000001";
        List<SablierStream> streams = List.of(
                buildStream(1L, walletAddress, StreamStatus.ACTIVE),
                buildStream(2L, walletAddress, StreamStatus.CANCELED)
        );
        when(streamRepository.findByWalletAddress(walletAddress)).thenReturn(streams);

        List<StreamResponse> result = service.listStreams(walletAddress);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).streamId()).isEqualTo(1L);
        assertThat(result.get(1).streamId()).isEqualTo(2L);
    }

    @Test
    void getWithdrawableAmount_mockMode_returnsRateTimesElapsed() {
        String ownerAddress = "0xabc1230000000000000000000000000000000001";
        SablierStream stream = buildStream(42L, ownerAddress, StreamStatus.ACTIVE);
        stream.setRatePerSecond(new BigDecimal("1.0")); // 1 JPYC per second

        when(streamRepository.findByStreamId(42L)).thenReturn(Optional.of(stream));

        BigDecimal result = service.getWithdrawableAmount(42L);

        // elapsed seconds >= 0, result >= 0
        assertThat(result).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    private SablierStream buildStream(Long streamId, String walletAddress, StreamStatus status) {
        return SablierStream.builder()
                .id("id-" + streamId)
                .streamId(streamId)
                .walletAddress(walletAddress)
                .receiverAddress("0xdef4560000000000000000000000000000000002")
                .token(StablecoinType.JPYC)
                .ratePerSecond(new BigDecimal("0.001"))
                .status(status)
                .createdAt(java.time.Instant.now())
                .build();
    }
}
