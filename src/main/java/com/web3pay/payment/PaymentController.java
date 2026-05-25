package com.web3pay.payment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "支払いオーダー一覧", description = "全オーダーを返します。status パラメータでフィルタリング可能")
    @GetMapping
    public ResponseEntity<List<PaymentOrder>> listPaymentOrders(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(paymentService.listOrders(status));
    }
}
