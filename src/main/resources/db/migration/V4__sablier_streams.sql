-- sablier_streams: Sablier Flow ストリーミング決済の管理テーブル
CREATE TABLE sablier_streams
(
    id               VARCHAR(36)     NOT NULL PRIMARY KEY,
    stream_id        BIGINT          NOT NULL UNIQUE,
    wallet_address   VARCHAR(42)     NOT NULL,
    receiver_address VARCHAR(42)     NOT NULL,
    token            VARCHAR(10)     NOT NULL,
    rate_per_second  NUMERIC(36, 18) NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL,
    canceled_at      TIMESTAMPTZ
);

CREATE INDEX idx_sablier_streams_wallet ON sablier_streams (wallet_address);
CREATE INDEX idx_sablier_streams_status ON sablier_streams (status);
