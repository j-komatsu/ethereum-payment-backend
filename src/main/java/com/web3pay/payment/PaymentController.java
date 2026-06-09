package com.web3pay.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments", description = "支払いオーダー管理 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "支払いオーダー作成", description = "新規支払いオーダーを作成し、PENDING 状態で返します")
    @PostMapping
    public ResponseEntity<PaymentOrder> createPaymentOrder(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentOrder order = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @Operation(summary = "支払いオーダー取得", description = "ID で支払いオーダーを取得します")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentOrder> getPaymentOrder(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getOrder(id));
    }

    @Operation(summary = "支払いオーダー一覧", description = "全オーダーを返します。status パラメータでフィルタリング可能（ページネーション対応）")
    @GetMapping
    public ResponseEntity<Page<PaymentOrder>> listPaymentOrders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(paymentService.listOrders(status, pageable));
    }

    @Operation(
            summary = "CPM 消費者アドレス確定",
            description = "CPM モードの注文に対し、消費者がQRスキャン後に自分のウォレットアドレスを確定します。" +
                    "consumerNonce が一致した場合、AWAITING_CONSUMER → PENDING に遷移します。")
    @PostMapping("/{id}/claim")
    public ResponseEntity<PaymentOrder> claimPaymentOrder(
            @PathVariable String id,
            @Valid @RequestBody ClaimPaymentRequest request,
            Authentication authentication) {
        String consumerAddress = (String) authentication.getPrincipal();
        PaymentOrder order = paymentService.claimOrder(id, consumerAddress, request.consumerNonce());
        return ResponseEntity.ok(order);
    }
}
