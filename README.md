# ethereum-payment-backend

Java + Spring Boot + Web3j による JPYC（日本円ステーブルコイン）決済バックエンド。

JPYC / USDC / USDT / DAI の受取確認・注文管理 REST API を提供します。
HashPort Wallet → MetaMask（Polygon Mainnet）間の JPYC 送金を想定した POC です。

## 技術スタック

| 層 | 技術 |
|---|---|
| Runtime | Java 21（仮想スレッド有効）|
| Framework | Spring Boot 3.2 |
| Ethereum | Web3j 4.10 |
| DB (dev) | H2 ファイルモード（`data/paymentdb-dev`）|
| DB (prod) | PostgreSQL |
| Build | Gradle Kotlin DSL |
| API ドキュメント | SpringDoc OpenAPI 2.5（Swagger UI）|

## パッケージ構成

```
src/main/java/com/web3pay/
├── Web3PayApplication.java
├── config/
│   └── Web3jConfig.java              # Web3j Bean 定義
├── chain/
│   ├── ChainController.java          # ETH残高照会 API
│   ├── ChainService.java             # Web3j 通信・EIP-55正規化
│   ├── ChainHealthIndicator.java     # Actuator ヘルスチェック
│   ├── ChainCommunicationException.java
│   └── EthBalanceResponse.java
├── payment/
│   ├── PaymentController.java        # 支払いオーダー CRUD
│   ├── CreatePaymentRequest.java     # リクエスト record
│   ├── PaymentService.java
│   ├── PaymentOrder.java             # JPA エンティティ
│   ├── PaymentOrderRepository.java
│   ├── PaymentStatus.java
│   └── PaymentOrderNotFoundException.java
├── token/
│   └── StablecoinType.java           # JPYC/USDC/USDT/DAI（アドレス・decimals・chainId）
└── exception/
    └── GlobalExceptionHandler.java   # RFC 9457 ProblemDetail
```

## ローカル起動手順

### 1. 環境変数の設定

```bash
cp .env.example .env
# .env を編集して WEB3J_CLIENT_ADDRESS に Infura/Alchemy の URL を設定
```

### 2. Infura アカウント取得（初回のみ）

1. [https://infura.io](https://infura.io) にアクセス
2. 無料アカウント作成 → API Key 取得
3. `.env` に設定:
   - Polygon: `WEB3J_CLIENT_ADDRESS=https://polygon-mainnet.infura.io/v3/YOUR_KEY`
   - テスト用: `WEB3J_CLIENT_ADDRESS=https://sepolia.infura.io/v3/YOUR_KEY`

### 3. 接続確認

```bash
source .env
./scripts/check-connection.sh
```

### 4. アプリ起動

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

起動後:
- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`
- Actuator: `http://localhost:8080/actuator/health`

## 環境変数

`.env.example` をコピーして `.env` を作成してください（`.env` は Git にコミットしない）。

| 変数 | 説明 |
|---|---|
| `WEB3J_CLIENT_ADDRESS` | JSON-RPC エンドポイント（Infura/Alchemy URL）|
| `DB_URL` | JDBC URL（本番: PostgreSQL 必須）|
| `DB_USERNAME` | DB ユーザー名 |
| `DB_PASSWORD` | DB パスワード |
| `DB_DRIVER` | JDBC ドライバクラス |

## API 概要

### 支払いオーダー作成

```http
POST /api/v1/payments
Content-Type: application/json

{
  "senderAddress":   "0xHashPortWalletのアドレス",
  "receiverAddress": "0xMetaMaskのアドレス",
  "amount": "1000",
  "token": "JPYC",
  "ttlSeconds": 3600
}
```

### 支払いオーダー取得

```http
GET /api/v1/payments/{id}
```

### 支払いオーダー一覧（ページネーション対応）

```http
GET /api/v1/payments?status=PENDING&page=0&size=20
```

### ETH 残高照会

```http
GET /api/v1/chain/eth-balance/{address}
→ { "address": "0x...", "balanceEth": "1.5", "balanceWei": "1500000000000000000" }
```

## 対応トークン

> JPYC は **Polygon Mainnet**、USDC/USDT/DAI は **Ethereum Mainnet** で動作します。ネットワークを混在させないよう注意してください。

| Token | Contract Address | Chain | Decimals | ガス代 |
|---|---|---|---|---|
| **JPYC** | `0xE7C3D8C9a439feDe00D2600032D5dB0Be71C3c29` | Polygon Mainnet (137) | 18 | POL |
| USDC | `0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48` | Ethereum Mainnet (1) | 6 | ETH |
| USDT | `0xdAC17F958D2ee523a2206206994597C13D831ec7` | Ethereum Mainnet (1) | 6 | ETH |
| DAI  | `0x6B175474E89094C44Da98b954EedeAC495271d0F` | Ethereum Mainnet (1) | 18 | ETH |

## セキュリティ

- 秘密鍵・API キー・ウォレットアドレスは `.env` で管理し Git にコミットしない
- Ethereum アドレスは `^0x[0-9a-fA-F]{40}$` 形式で必ずバリデーション
- 本番設定（`application.yml`）に H2 コンソール・DEBUG ログを含めない

## 開発進捗

| フェーズ | 内容 | 状態 |
|---|---|---|
| Phase 0 | 初期構成（Gradle・JPA・例外ハンドラ） | ✅ 完了 |
| Phase 1 | 環境整備・Ethereum 基礎 | ✅ 完了 |
| Phase 2 | Web3j 接続・ETH 残高 API・OpenAPI | ✅ 完了 |
| Phase 3 | ERC-20・JPYC 残高取得 | 🔜 次 |
| Phase 4 | Transfer イベント監視・入金検知 | — |
| Phase 5 | SIWE 認証・Permit 決済 | — |
| Phase 6 | Sablier 自動購入・テスト整備 | — |
| Phase 7 | Docker・デプロイ | — |

詳細は [`docs/PLAN.md`](docs/PLAN.md) を参照。
