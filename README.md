# ethereum-payment-backend

Java 21 + Spring Boot 3.2 + Web3j による **JPYC（日本円ステーブルコイン）決済バックエンド**。

Polygon Mainnet 上の JPYC を用いた入金検知・EIP-2612 Permit 決済・SIWE 認証を提供する REST API サーバー。

## 技術スタック

| 層 | 技術 | バージョン |
|---|---|---|
| Runtime | Java 21（仮想スレッド有効） | 21 LTS |
| Framework | Spring Boot | 3.2.x |
| Ethereum SDK | Web3j | 4.10.x |
| DB (dev) | H2 ファイルモード | 2.x |
| DB (prod) | PostgreSQL | 15+ |
| Build | Gradle Kotlin DSL | 8.7 |
| API ドキュメント | SpringDoc OpenAPI (Swagger UI) | 2.5 |
| ローカルチェーン | Hardhat | 2.22+ |

## 機能一覧

| フェーズ | 機能 | 状態 |
|---|---|---|
| Phase 0 | 初期構成（Gradle・JPA・例外ハンドラ） | ✅ 完了 |
| Phase 1 | 環境整備・Ethereum 基礎 | ✅ 完了 |
| Phase 2 | Web3j 接続・ETH 残高 API | ✅ 完了 |
| Phase 3 | ERC-20・JPYC/USDC/USDT/DAI 残高取得 | ✅ 完了 |
| Phase 4 | Transfer イベント監視・入金検知（TransferEventPoller） | ✅ 完了 |
| Phase 5 | SIWE ウォレット認証・EIP-2612 Permit 決済 | ✅ 完了 |
| Phase 5-F | フロントエンド（Next.js 15 + Wagmi v2 + RainbowKit） | 🔜 進行中 |
| Phase 6 | Sablier ストリーミング・OpenAPI 充実 | — |
| Phase 7 | Docker・デプロイ（Railway / Render） | — |

## API エンドポイント

### 認証（SIWE）

```http
POST /api/v1/auth/nonce
→ { "nonce": "ランダム32文字" }

POST /api/v1/auth/verify
{ "message": "SIWE メッセージ本文", "signature": "0x...", "address": "0x..." }
→ { "token": "JWT" }
```

### 支払いオーダー（要 JWT）

```http
# MPM（Merchant Presented Mode）: マーチャントがアドレスを指定
POST /api/v1/payments
{ "receiverAddress":"0x...", "senderAddress":"0x...", "amount":"1000", "token":"JPYC", "paymentMode":"MPM" }
→ 201 Created: PaymentOrder

# CPM（Consumer Presented Mode）: QR コードを消費者がスキャン後にアドレス確定
POST /api/v1/payments
{ "receiverAddress":"0x...", "amount":"500", "token":"JPYC", "paymentMode":"CPM" }
→ 201 Created: PaymentOrder（status: AWAITING_CONSUMER）

# CPM 消費者確定（QR スキャン後、消費者の JWT + consumerNonce で呼ぶ）
POST /api/v1/payments/{id}/claim
{ "consumerNonce": "QRコードから取得した64文字のhex" }
→ 200 OK: PaymentOrder（status: PENDING）

GET  /api/v1/payments/{id}
GET  /api/v1/payments?status=PENDING&page=0&size=20
```

### EIP-2612 Permit 決済（要 JWT）

```http
# フロントエンドが eth_signTypedData_v4 で署名するための EIP-712 データを取得
GET /api/v1/permit/typed-data?paymentOrderId={id}
→ { "primaryType": "Permit", "domain": {...}, "types": {...}, "message": {...} }

# 署名済みデータを送信 → バックエンドが permit + transferFrom を実行
POST /api/v1/permit/execute
{ "paymentOrderId": "...", "nonce": "0", "deadline": 1234567890, "signature": "0x..." }
→ { "paymentOrderId": "...", "permitTxHash": "0x...", "transferTxHash": "0x...", "status": "CONFIRMED" }
```

### チェーン情報（認証不要）

```http
GET /api/v1/chain/eth-balance/{address}
→ { "address": "0x...", "balanceEth": "1.5", "balanceWei": "1500000000000000000" }

GET /api/v1/chain/token-balance/{address}?token=JPYC
→ { "address": "0x...", "token": "JPYC", "balance": "1000.0", "rawBalance": "1000000000000000000000" }
```

## ローカル起動手順

### パターン A: Hardhat ローカルノード（API キー不要）

```bash
# 1. Hardhat ノードを起動
cd hardhat
npm install
npx hardhat node     # localhost:8545 で JSON-RPC 起動

# 2. テスト用 ERC-20 をデプロイ（別ターミナル）
npx hardhat run scripts/local-setup.ts --network localhost

# 3. Spring Boot 起動（dev プロファイル）
cd ..
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### パターン B: Infura / Alchemy（API キーが必要）

```bash
# 1. 環境変数を設定
cp .env.example .env
# .env を編集して以下を設定:
# WEB3J_POLYGON_ENDPOINT=https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY
# WEB3J_ETHEREUM_ENDPOINT=https://mainnet.infura.io/v3/YOUR_KEY

# 2. 起動
source .env
./gradlew bootRun --args='--spring.profiles.active=dev'
```

起動後:
- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`（dev プロファイル時のみ）
- Actuator: `http://localhost:8080/actuator/health`

## 環境変数

`.env.example` をコピーして `.env` を作成してください（`.env` は Git に含めない）。

| 変数 | 必須 | 説明 |
|---|---|---|
| `WEB3J_CLIENT_ADDRESS` | ✅ | JSON-RPC エンドポイント（Polygon RPC URL または `http://localhost:8545`） |
| `WEB3J_POLYGON_ENDPOINT` | — | Polygon 専用エンドポイント（未設定時は `WEB3J_CLIENT_ADDRESS` が使われる） |
| `WEB3J_ETHEREUM_ENDPOINT` | — | Ethereum Mainnet 専用エンドポイント |
| `DB_URL` | ✅ | JDBC URL（dev では `application-dev.yml` で H2 を使用） |
| `DB_USERNAME` | ✅ | DB ユーザー名 |
| `DB_PASSWORD` | ✅ | DB パスワード |
| `JWT_SECRET` | ✅ | JWT 署名鍵（Base64 エンコードされた 32 バイト以上の文字列） |
| `SIWE_DOMAIN` | — | SIWE ドメイン（デフォルト: `localhost`） |
| `SIWE_CHAIN_ID` | — | SIWE チェーン ID（デフォルト: `137` = Polygon） |
| `PERMIT_SPENDER_PRIVATE_KEY` | — | Permit 実行者のウォレット秘密鍵（未設定時は Permit 実行不可） |

## 対応トークン

| Token | Contract Address | Chain | Decimals | Permit (EIP-2612) |
|---|---|---|---|---|
| **JPYC** | `0x431D5dfF03120AFA4bDf332c61A6e1766eF37BF6` | Polygon (137) | 18 | ✅ |
| USDC | `0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48` | Ethereum (1) | 6 | ✅ |
| USDT | `0xdAC17F958D2ee523a2206206994597C13D831ec7` | Ethereum (1) | 6 | ❌ |
| DAI | `0x6B175474E89094C44Da98b954EedeAC495271d0F` | Ethereum (1) | 18 | ✅ |

## テスト実行

```bash
./gradlew test
# 89 テスト（2026-05-31 時点）
```

## セキュリティ注意事項

- 秘密鍵・API キー・ウォレットアドレスは `.env` で管理し Git にコミットしない
- Ethereum アドレスは `^0x[0-9a-fA-F]{40}$` で必ずバリデーション
- `PERMIT_SPENDER_PRIVATE_KEY` は本番環境では HSM や Vault に移管する
- 本番設定に `h2-console.enabled: true` / `ddl-auto: update` / DEBUG ログを含めない

詳細な開発計画は [`docs/PLAN.md`](docs/PLAN.md) を参照。
