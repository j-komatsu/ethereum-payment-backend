package com.web3pay.chain.permit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Permit", description = "EIP-2612 Permit を利用したガスレス支払い API")
@RestController
@RequestMapping("/api/v1/permit")
@RequiredArgsConstructor
public class PermitController {

    private final PermitService permitService;

    @Operation(
            summary = "EIP-712 typed data 取得",
            description = "フロントエンドが eth_signTypedData_v4 で署名するための EIP-712 typed data を返します")
    @GetMapping("/typed-data")
    public ResponseEntity<PermitTypedData> getTypedData(
            @RequestParam String paymentOrderId,
            Authentication authentication) {
        String ownerAddress = (String) authentication.getPrincipal();
        return ResponseEntity.ok(permitService.buildTypedData(paymentOrderId, ownerAddress));
    }

    @Operation(
            summary = "Permit + transferFrom 実行",
            description = "署名済み permit を検証し、permit() および transferFrom() を on-chain で実行します")
    @PostMapping("/execute")
    public ResponseEntity<PermitTxResponse> executePermit(
            @Valid @RequestBody ExecutePermitRequest request,
            Authentication authentication) {
        String ownerAddress = (String) authentication.getPrincipal();
        return ResponseEntity.ok(permitService.execute(
                request.paymentOrderId(), ownerAddress, request.deadline(),
                request.nonce(), request.signature()));
    }
}
