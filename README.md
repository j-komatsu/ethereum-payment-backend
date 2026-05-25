# ethereum-payment-backend

Java + Spring Boot + Web3j による JPYC（日本円ステーブルコイン）決済バックエンド。

JPYC / USDC / USDT / DAI の受取確認・注文管理 REST API を提供します。
HashPort Wallet との連携を前提とした日本円決済システムです。

## 技術スタック

| 層 | 技術 |
|---|---|
| Runtime | Java 21（仮想スレッド有効）|
| Framework | Spring Boot 3.2 |
| Ethereum | Web3j 4.10 |
| DB (dev) | H2 ファイルモード（`data/paymentdb-dev`）|
| DB (prod) | PostgreSQL |
| Build | Gradle Kotlin DSL |

## パッケージ構成

```
src/main/java/com/example/payment/
├── EthereumPaymentApplication.java   # エントリポイント
├── config/
│   └── Web3jConfig.java              # Web3j Bean 定義（Infura URL ロギング防止）
├── controller/
│   ├── PaymentController.java        # REST エンドポイント
│   └── CreatePaymentRequest.java     # リクエスト DTO（アドレス形式バリデーション）
├── service/
│   └── PaymentService.java           # ビジネスロジック
├── model/
│   ├── PaymentOrder.java             # JPA エンティティ
│   ├── PaymentStatus.java            # ステータス列挙
│   └── StablecoinType.java           # トークン列挙（JPYC 優先・コントラクトアドレス付き）
├── repository/
│   └── PaymentOrderRepository.java   # Spring Data JPA
└── exception/
    ├── PaymentOrderNotFoundException.java
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
2. 無料アカウント作成
3. 新規プロジェクト作成 → API Key 取得
4. `.env` に設定: `WEB3J_CLIENT_ADDRESS=https://sepolia.infura.io/v3/YOUR_KEY`

### 3. 接続確認

```bash
source .env
./scripts/check-connection.sh
# ✅ 最新ブロック番号: 12345678 (0xbc614e)
# ✅ ネットワーク: Sepolia Testnet
```

### 4. アプリ起動

```bash
# 開発モード（H2 ファイルモード・デバッグログ）
./gradlew bootRun --args='--spring.profiles.active=dev'
```

起動後:
- API: `http://localhost:8080/api/v1/payments`
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

### 決済注文の作成

```http
POST /api/v1/payments
Content-Type: application/json

{
  "receiverAddress": "0xYourAddress",
  "amount": "100.00",
  "token": "JPYC",
  "ttlSeconds": 3600
}
```

### 注文の取得

```http
GET /api/v1/payments/{id}
```

### 注文一覧

```http
GET /api/v1/payments?status=PENDING
```

## 対応トークン

> ⚠️ チェーンに注意: JPYC は **Polygon** で動作します。USDC/USDT/DAI は Ethereum Mainnet のアドレスです。

| Token | Contract Address | Chain | Decimals | 備考 |
|---|---|---|---|---|
| **JPYC** | `0x431D5dfF03120AFA4bDf332c61A6e1766eF37BDB` | Polygon Mainnet | 18 | 優先・EIP-2612対応 |
| USDC | `0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48` | Ethereum Mainnet | 6 | — |
| USDT | `0xdAC17F958D2ee523a2206206994597C13D831ec7` | Ethereum Mainnet | 6 | — |
| DAI  | `0x6B175474E89094C44Da98b954EedeAC495271d0F` | Ethereum Mainnet | 18 | — |

## セキュリティ

- 秘密鍵・API キー・パスワードは絶対にリポジトリにコミットしない
- PR 作成前に必ず `./scripts/security-check.sh` を実行
- 本番設定（`application.yml`）に H2 コンソールや DEBUG ログを含めない

## 開発計画

詳細な実装計画・学習レポートの構成は [`docs/PLAN.md`](docs/PLAN.md) を参照。

7フェーズ・35日分の具体的なタスクとレポートテーマを記載しています。
