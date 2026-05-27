CREATE TABLE siwe_nonces (
    nonce     VARCHAR(32)  NOT NULL PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    used      BOOLEAN      NOT NULL DEFAULT false
);
