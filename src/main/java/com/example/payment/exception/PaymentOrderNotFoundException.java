package com.example.payment.exception;

public class PaymentOrderNotFoundException extends RuntimeException {

    public PaymentOrderNotFoundException(String id) {
        super("Payment order not found: " + id);
    }
}
