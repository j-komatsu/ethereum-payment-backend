# Phase 6 実装ノート — Sablier Streaming・AutoPurchaseJob・OpenAPI

## 実装範囲

| Day | 機能 | パッケージ |
|---|---|---|
| 26 | Sablier Flow ストリーミング決済 | `com.web3pay.streaming` |
| 27 | AutoPurchaseJob + ShedLock 排他制御 | `com.web3pay.job` |
| 29 | SpringDoc OpenAPI 設定 + Swagger Bearer 認証 | `com.web3pay.config` |

---

## Day 26 — Sablier Flow 連携

### 判断: sablierEnabled フラグによる段階的実装

Sablier Flow コントラクト（Polygon Mainnet: `0x1a272...`）への実際の on-chain 操作には秘密鍵設定が必要。
テスト・開発環境で動作確認できるよう `sablier.enabled=false`（デフォルト）の mock モードを設けた。

- `enabled=false`: DB のみ更新、streamId はタイムスタンプで代替
- `enabled=true`: RawTransactionManager で on-chain create/cancel を実行（未実装・TODO）

### 設計決定: SablierStream は PaymentOrder と独立したライフサイクル

- PaymentOrder は「1回の決済」を表すが、SablierStream は「継続する資金フロー」を表す
- 両者を同じテーブルに混在させると status の意味が混乱するため、完全に別エンティティとした

### 却下した選択肢: Web3j コントラクト Wrapper 生成

Sablier Flow の ABI から `web3j generate` でラッパークラスを生成する選択肢を検討。
- メリット: 型安全、メソッド自動補完
- 却下理由: mock モードでは not-needed、on-chain モードは未実装なので今は Function/FunctionEncoder の手動エンコードで十分

---

## Day 27 — AutoPurchaseJob + ShedLock

### ShedLock 依存追加

```
shedlock-spring:5.13.0
shedlock-provider-jdbc-template:5.13.0
```

JdbcTemplateLockProvider を選択した理由: H2（開発）と PostgreSQL（本番）の両方で動作する。
Redis や ZooKeeper などの外部依存を増やさずに済む。

### 判断: spender アドレスは環境変数から取得する設計

`AutoPurchaseJob.fetchAllowance()` では spender アドレスが `0x000...0`（仮）になっている。
実際の運用では `permit.spender-private-key` から秘密鍵 → アドレスを導出するか、
専用の `permit.spender-address` 環境変数を追加する必要がある。

現時点では `0x000...0` で call しても `allowance` が 0 になるだけで例外にはならず、
SKIPPED_INSUFFICIENT_ALLOWANCE として記録されるため動作上の問題はない。

### 設計: AutoPurchaseSubscription エンティティを追加

PLAN.md には「auto_purchase_executions テーブル」のみ記載されていたが、
「誰が・何に・月いくら払うか」の設定を保持する AutoPurchaseSubscription エンティティが必要と判断して追加した。

---

## Day 29 — OpenAPI

### 既存実装との差分

PaymentController / ChainController / TokenBalanceController / SiweController / PermitController は
すでに `@Operation`, `@Tag` が実装済みだった。

Phase 6 で追加したもの:
- `OpenApiConfig.java`: API レベルのメタ情報（title, description, Bearer 認証スキーム）
- `StreamingController`: 新規 @Operation/@Tag

### Swagger UI でのベアラー認証

`SecurityScheme` を `OpenApiConfig` で定義することで、Swagger UI から JWT を入力して
保護されたエンドポイントを直接試せるようにした。

---

## Flyway マイグレーション

| バージョン | 内容 |
|---|---|
| V4 | `sablier_streams` テーブル + index |
| V5 | `auto_purchase_subscriptions` + `auto_purchase_executions` + `shedlock` テーブル |

---

## 残 TODO（Phase 6 以降）

- `AutoPurchaseJob.fetchAllowance()` のスペンダーアドレスを環境変数から取得
- Sablier on-chain `createAndDeposit()` / `cancel()` の実装（RawTransactionManager パターン）
- AutoPurchaseSubscription の CRUD API（登録・解約 UI から呼ぶため）
