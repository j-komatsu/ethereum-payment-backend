-- auto_purchase_subscriptions: ユーザーの毎月自動引き落とし設定
CREATE TABLE auto_purchase_subscriptions
(
    id               VARCHAR(36)     NOT NULL PRIMARY KEY,
    wallet_address   VARCHAR(42)     NOT NULL,
    receiver_address VARCHAR(42)     NOT NULL,
    token            VARCHAR(10)     NOT NULL,
    monthly_amount   NUMERIC(36, 18) NOT NULL,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ     NOT NULL,
    canceled_at      TIMESTAMPTZ
);

CREATE INDEX idx_auto_subs_wallet  ON auto_purchase_subscriptions (wallet_address);
CREATE INDEX idx_auto_subs_active  ON auto_purchase_subscriptions (active);

-- auto_purchase_executions: 自動購入ジョブの実行履歴
CREATE TABLE auto_purchase_executions
(
    id               VARCHAR(36)     NOT NULL PRIMARY KEY,
    wallet_address   VARCHAR(42)     NOT NULL,
    token            VARCHAR(10)     NOT NULL,
    amount           NUMERIC(36, 18) NOT NULL,
    status           VARCHAR(40)     NOT NULL,
    tx_hash          VARCHAR(66),
    failure_reason   TEXT,
    executed_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_auto_exec_wallet ON auto_purchase_executions (wallet_address);
CREATE INDEX idx_auto_exec_status ON auto_purchase_executions (status);

-- shedlock: ShedLock 分散スケジューラーロック管理テーブル
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
