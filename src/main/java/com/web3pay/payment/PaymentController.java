package com.web3pay.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentOrder> createPaymentOrder(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentOrder order = paymentService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentOrder> getPaymentOrder(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getOrder(id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentOrder>> listPaymentOrders(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(paymentService.listOrders(status));
    }
}
