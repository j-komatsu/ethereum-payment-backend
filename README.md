# ethereum-payment-backend

Java + Spring Boot + Web3j によるステーブルコイン決済バックエンド。

USDC / USDT / DAI の受取確認・注文管理 REST API を提供します。

## 技術スタック

| 層 | 技術 |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.2 |
| Ethereum | Web3j 4.10 |
| DB (dev) | H2 (in-memory) |
| DB (prod) | PostgreSQL |
| Build | Maven |

## パッケージ構成

```
src/main/java/com/example/payment/
├── EthereumPaymentApplication.java   # エントリポイント
├── config/
│   └── Web3jConfig.java              # Web3j Bean 定義
├── controller/
│   ├── PaymentController.java        # REST エンドポイント
│   └── CreatePaymentRequest.java     # リクエスト DTO
├── service/
│   └── PaymentService.java           # ビジネスロジック
├── model/
│   ├── PaymentOrder.java             # JPA エンティティ
│   ├── PaymentStatus.java            # ステータス列挙
│   └── StablecoinType.java           # トークン列挙 (コントラクトアドレス付き)
├── repository/
│   └── PaymentOrderRepository.java   # Spring Data JPA
└── exception/
    ├── PaymentOrderNotFoundException.java
    └── GlobalExceptionHandler.java   # RFC 9457 ProblemDetail
```

## ローカル起動

```bash
# 依存関係のダウンロード & ビルド
mvn clean package -DskipTests

# 起動 (H2 in-memory DB 使用)
mvn spring-boot:run
```

起動後:
- API: `http://localhost:8080/api/v1/payments`
- H2 Console: `http://localhost:8080/h2-console`
- Actuator: `http://localhost:8080/actuator/health`

## 環境変数

| 変数 | デフォルト | 説明 |
|---|---|---|
| `WEB3J_CLIENT_ADDRESS` | `https://mainnet.infura.io/v3/YOUR_PROJECT_ID` | JSON-RPC エンドポイント |
| `DB_URL` | H2 in-memory | JDBC URL |
| `DB_USERNAME` | `sa` | DB ユーザー名 |
| `DB_PASSWORD` | (空) | DB パスワード |
| `DB_DRIVER` | `org.h2.Driver` | JDBC ドライバクラス |

## API 概要

### 決済注文の作成

```http
POST /api/v1/payments
Content-Type: application/json

{
  "receiverAddress": "0xYourAddress",
  "amount": "100.00",
  "token": "USDC",
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

## 対応ステーブルコイン (Mainnet)

| Token | Contract Address | Decimals |
|---|---|---|
| USDC | `0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48` | 6 |
| USDT | `0xdAC17F958D2ee523a2206206994597C13D831ec7` | 6 |
| DAI  | `0x6B175474E89094C44Da98b954EedeAC495271d0F` | 18 |

## 開発計画

詳細な実装計画・学習レポートの構成は [`docs/PLAN.md`](docs/PLAN.md) を参照。

7フェーズ・35日分の具体的なタスクとレポートテーマを記載しています。
