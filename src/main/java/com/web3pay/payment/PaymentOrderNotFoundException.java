package com.web3pay.payment;

public class PaymentOrderNotFoundException extends RuntimeException {

    public PaymentOrderNotFoundException(String id) {
        super("Payment order not found: " + id);
    }
}
