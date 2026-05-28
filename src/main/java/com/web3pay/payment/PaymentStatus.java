package com.web3pay.payment;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    AWAITING_CONSUMER,
    CONFIRMED,
    EXPIRED,
    OVERPAID,
    UNDERPAID
}
