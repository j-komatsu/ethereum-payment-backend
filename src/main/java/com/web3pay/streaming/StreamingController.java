package com.web3pay.streaming;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Streaming", description = "Sablier Flow ストリーミング決済 API（サブスク・秒単位課金）")
@RestController
@RequestMapping("/api/v1/streams")
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingPaymentService streamingService;

    @Operation(
            summary = "ストリーム作成",
            description = "Sablier Flow ストリームを作成します。毎秒 ratePerSecond トークンが receiverAddress に流れます。")
    @PostMapping
    public ResponseEntity<StreamResponse> createStream(
            @Valid @RequestBody CreateStreamRequest request,
            Authentication authentication) {
        String ownerAddress = (String) authentication.getPrincipal();
        StreamResponse response = streamingService.createStream(ownerAddress, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "ストリーム取得", description = "Sablier streamId でストリームを取得します")
    @GetMapping("/{streamId}")
    public ResponseEntity<StreamResponse> getStream(
            @Parameter(description = "Sablier コントラクトが発行した streamId")
            @PathVariable Long streamId) {
        return ResponseEntity.ok(streamingService.getStream(streamId));
    }

    @Operation(summary = "ストリーム一覧", description = "認証ユーザーのウォレットに紐づくストリームを一覧します")
    @GetMapping
    public ResponseEntity<List<StreamResponse>> listStreams(Authentication authentication) {
        String ownerAddress = (String) authentication.getPrincipal();
        return ResponseEntity.ok(streamingService.listStreams(ownerAddress));
    }

    @Operation(
            summary = "引き出し可能残高照会",
            description = "指定ストリームの引き出し可能な JPYC 残高を返します（Sablier withdrawableAmountOf）")
    @GetMapping("/{streamId}/withdrawable")
    public ResponseEntity<Map<String, BigDecimal>> getWithdrawable(
            @PathVariable Long streamId) throws IOException {
        BigDecimal amount = streamingService.getWithdrawableAmount(streamId);
        return ResponseEntity.ok(Map.of("withdrawableAmount", amount));
    }

    @Operation(
            summary = "ストリームキャンセル",
            description = "ストリームをキャンセルします。オーナーのみ操作できます。残高はオーナーに返却されます。")
    @DeleteMapping("/{streamId}")
    public ResponseEntity<StreamResponse> cancelStream(
            @PathVariable Long streamId,
            Authentication authentication) {
        String callerAddress = (String) authentication.getPrincipal();
        return ResponseEntity.ok(streamingService.cancelStream(streamId, callerAddress));
    }
}
