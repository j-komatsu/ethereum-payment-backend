package com.web3pay.payment;

public enum PaymentStatus {
    PENDING,
    AWAITING_CONSUMER,
    CONFIRMED,
    EXPIRED,
    OVERPAID,
    UNDERPAID
}
