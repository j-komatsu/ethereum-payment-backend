-- poller_states: tracks the last processed block per token for resumable event polling
CREATE TABLE poller_states
(
    token                VARCHAR(10) NOT NULL PRIMARY KEY,
    last_processed_block BIGINT      NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

-- Enforce idempotency: each on-chain transaction can confirm at most one payment order
ALTER TABLE payment_orders
    ADD CONSTRAINT uq_payment_orders_tx_hash UNIQUE (tx_hash);
