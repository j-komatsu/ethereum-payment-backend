-- payment_orders: core table for payment request tracking
CREATE TABLE payment_orders
(
    id               VARCHAR(36)     NOT NULL PRIMARY KEY,
    payment_mode     VARCHAR(255)    NOT NULL,
    receiver_address VARCHAR(255)    NOT NULL,
    sender_address   VARCHAR(255),
    consumer_nonce   VARCHAR(64),
    expected_amount  NUMERIC(36, 18) NOT NULL,
    token            VARCHAR(255)    NOT NULL,
    status           VARCHAR(255)    NOT NULL,
    tx_hash          VARCHAR(66),
    created_at       TIMESTAMPTZ     NOT NULL,
    confirmed_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ
);
