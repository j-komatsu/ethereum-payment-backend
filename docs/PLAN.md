# 開発計画：Ethereum ステーブルコイン決済バックエンド

> **方針**
> - 1日1コミットのペースで無理なく継続
> - 実装とレポート（理解）をセットで進める
> - 完全無料構成・Java 21 + Spring Boot 3.2 のモダンスタック
> - ドキュメントはすべて日本語

---

## 技術スタック（確定版）

| カテゴリ | 技術 | バージョン | 無料か |
|---|---|---|---|
| 言語 | Java | 21 (LTS) | ✅ |
| フレームワーク | Spring Boot | 3.2.x | ✅ |
| 並行処理 | Java 21 仮想スレッド (Project Loom) | — | ✅ |
| Ethereum SDK | Web3j | 4.10.x | ✅ |
| DB (開発・本番) | H2 ファイルモード | 2.x | ✅ |
| ノード接続 | Infura / Alchemy 無料枠 | — | ✅ (月1M req) |
| ローカルチェーン | Hardhat ノード | — | ✅ |
| API ドキュメント | SpringDoc OpenAPI (Swagger UI) | 2.x | ✅ |
| テスト | JUnit 5 + Mockito + Spring Boot Test | — | ✅ |
| コンテナ | Docker Compose (ローカルのみ) | — | ✅ |

> PostgreSQL は将来クラウドに出す段階で Neon 無料枠を使用予定。今は H2 で十分。

---

## フェーズ一覧

```
Phase 0  ✅ 完了   初期構成（pom.xml・パッケージ・README）
Phase 1  🔜 次     環境整備 + Ethereum 基礎理解
Phase 2            Web3j 接続 + アカウント・残高操作
Phase 3            ERC-20 規格 + ステーブルコイン残高取得
Phase 4            Transfer イベント監視 + 入金検知
Phase 5            決済フロー完成（状態管理・Webhook）
Phase 6            テスト + OpenAPI ドキュメント
Phase 7            デプロイ検討（Railway / Render 無料枠）
```

---

## Phase 0 ✅ 完了

### 成果物

| ファイル | 内容 |
|---|---|
| `pom.xml` | Spring Boot 3.2・Web3j 4.10・H2・Lombok |
| `EthereumPaymentApplication.java` | エントリポイント |
| `PaymentOrder.java` | JPA エンティティ（UUID PK・ステータス管理） |
| `StablecoinType.java` | USDC / USDT / DAI（アドレス・decimals）|
| `PaymentController.java` | POST・GET /api/v1/payments |
| `PaymentService.java` | 注文作成・取得・一覧 |
| `GlobalExceptionHandler.java` | RFC 9457 ProblemDetail 形式エラー |
| `application.yml` | H2 in-memory・環境変数外部化 |

---

## Phase 1：環境整備 + Ethereum 基礎理解

**目標：** ローカル環境を本番に近い状態に整え、Ethereum の仕組みを一通り言語化できる

### Day 1 — H2 ファイルモードへ切り替え

**変更ファイル:** `src/main/resources/application.yml`

```yaml
# 変更前
url: jdbc:h2:mem:paymentdb

# 変更後
url: jdbc:h2:file:./data/paymentdb
```

**確認ポイント:**
- アプリ起動後に `data/paymentdb.mv.db` が生成されること
- 再起動後もデータが残ること
- `.gitignore` に `data/` を追加すること

**コミットメッセージ例:**
```
chore: H2をファイルモードに切り替え（data/paymentdb）
```

---

### Day 2 — Java 21 仮想スレッド有効化 + .env 管理

**変更ファイル:** `application.yml`、`.env.example`（新規作成）

```yaml
# application.yml に追加
spring:
  threads:
    virtual:
      enabled: true
```

```bash
# .env.example（新規作成）
WEB3J_CLIENT_ADDRESS=https://mainnet.infura.io/v3/YOUR_PROJECT_ID
DB_URL=jdbc:h2:file:./data/paymentdb
DB_USERNAME=sa
DB_PASSWORD=
```

**学習ポイント（コード内コメント不要・理解として持つ）:**
- 仮想スレッドは従来のプラットフォームスレッドと何が違うか
- なぜ Web3j の I/O 待ち処理と相性が良いか

**コミットメッセージ例:**
```
feat: Java 21 仮想スレッド有効化 + .env.example 追加
```

---

### Day 3 — Infura アカウント取得 + 接続確認スクリプト

**新規ファイル:** `scripts/check-connection.sh`

```bash
#!/bin/bash
# Sepolia テストネットへの接続確認（curl で JSON-RPC を叩く）
curl -X POST "${WEB3J_CLIENT_ADDRESS}" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'
```

**手順メモ（README に追記）:**
1. https://infura.io にアクセス
2. 無料アカウント作成
3. 新規プロジェクト作成 → API Key 取得
4. `WEB3J_CLIENT_ADDRESS=https://sepolia.infura.io/v3/YOUR_KEY` を `.env` に設定
5. スクリプト実行して現在のブロック番号が返ればOK

**コミットメッセージ例:**
```
chore: Infura接続確認スクリプト + README手順追記
```

---

### Day 4-5 — レポート執筆

**ファイル:** `docs/report-01-ethereum-basics.md`

**必ず答える問い（レポートの骨格）:**

```
1. ブロックチェーンとは何か
   - なぜ「チェーン」という名前か
   - ブロックの中身には何が入っているか
   - なぜデータを改ざんできないのか（ハッシュの仕組み）

2. Ethereum とは何か
   - Bitcoin との違いは何か
   - なぜスマートコントラクトが動くのか（EVM とは）
   - ガス（Gas）とは何か・なぜ必要か

3. ノードとは何か
   - フルノード・ライトノード・アーカイブノードの違い
   - Infura / Alchemy はどういうサービスか
   - JSON-RPC とは何か（eth_blockNumber などの意味）

4. メインネット vs テストネット
   - Sepolia とは何か
   - テストネットの ETH はどこで手に入るか（Faucet）
   - なぜテストネットで開発するのか

5. このプロジェクトとの関係
   - USDC の送金がブロックチェーン上でどう記録されるか（大まかな流れ）
```

**コミットメッセージ例:**
```
docs: report-01 Ethereum基礎（ブロックチェーン・EVM・ガス・ノード）
```

---

## Phase 2：Web3j 接続 + アカウント・残高操作

**目標：** Spring Boot から実際にチェーンと通信し、ETH 残高を返す API を動かす

### Day 6 — Web3j 接続ヘルスチェック

**新規ファイル:** `src/main/java/com/example/payment/chain/ChainHealthIndicator.java`

```java
// Spring Actuator のカスタムヘルスインジケーター
// /actuator/health に "ethereum": {"status": "UP", "blockNumber": 12345} を追加
```

**確認ポイント:**
- `http://localhost:8080/actuator/health` で Ethereum ノードへの疎通確認ができること
- ノードが落ちたら DOWN になること

**コミットメッセージ例:**
```
feat: Ethereum接続ヘルスインジケーター追加（Actuator連携）
```

---

### Day 7 — ETH 残高照会 API

**新規ファイル:**
- `src/main/java/com/example/payment/chain/ChainController.java`
- `src/main/java/com/example/payment/chain/ChainService.java`

**エンドポイント:**
```
GET /api/v1/chain/eth-balance/{address}
→ {"address": "0x...", "balanceEth": "1.234", "balanceWei": "1234000000000000000"}
```

**実装のポイント:**
- `web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)` を使う
- `Wei → Ether` の変換は `Convert.fromWei(balance.toString(), Convert.Unit.ETHER)` で行う
- アドレスの形式バリデーション（`WalletUtils.isValidAddress(address)`）

**コミットメッセージ例:**
```
feat: ETH残高照会API（GET /api/v1/chain/eth-balance/{address}）
```

---

### Day 8 — レスポンス DTO を Record に移行

**目標:** Lombok の `@Data` を Java 16+ の `record` に置き換えてよりモダンに

**変更対象:**
- `CreatePaymentRequest` → record に変換（バリデーションアノテーションの扱いに注意）
- ChainController のレスポンスは最初から record で作る

```java
// 例
public record EthBalanceResponse(
    String address,
    String balanceEth,
    String balanceWei
) {}
```

**コミットメッセージ例:**
```
refactor: レスポンスDTOをJava recordに移行
```

---

### Day 9 — SpringDoc OpenAPI 導入

**`pom.xml` 追加:**
```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.3.0</version>
</dependency>
```

**確認ポイント:**
- `http://localhost:8080/swagger-ui.html` で UI が見えること
- 各エンドポイントに `@Operation` / `@Tag` アノテーションで日本語説明を付ける

**コミットメッセージ例:**
```
feat: SpringDoc OpenAPI導入（Swagger UI）
```

---

### Day 10 — レポート執筆

**ファイル:** `docs/report-02-accounts-and-web3j.md`

**必ず答える問い:**

```
1. Ethereum のアカウントとは何か
   - EOA（外部所有アカウント）とは
   - コントラクトアカウントとは
   - 秘密鍵 → 公開鍵 → アドレスの生成フロー（楕円曲線暗号 secp256k1）
   - なぜアドレスは 0x から始まる 42 文字なのか

2. Wei・Gwei・Ether の関係
   - 1 Ether = 10^18 Wei の理由
   - ガス代計算で Gwei が使われる理由

3. Web3j の仕組み
   - JSON-RPC とは何か・どんなメソッドがあるか
   - Web3j はどうやって非同期処理を実現しているか（Flowable/RxJava）
   - Spring Bean として管理するメリット

4. 今回実装した API の解説
   - eth_getBalance の内部動作
   - DEFAULT_BLOCK_PARAMETER の意味（latest / pending / earliest）
```

**コミットメッセージ例:**
```
docs: report-02 アカウント・Web3j基礎（EOA・Wei・JSON-RPC）
```

---

## Phase 3：ERC-20 規格 + ステーブルコイン残高取得

**目標：** スマートコントラクトと通信し、USDC/USDT/DAI 残高を返す API を実装する

### Day 11 — ERC-20 ABI バインディング生成

**Web3j CLI のインストールと使い方:**
```bash
# Web3j CLI インストール
curl -L get.web3j.io | sh

# 標準ERC-20 ABIからJavaラッパーを生成
web3j generate solidity \
  -a src/main/resources/abi/erc20.json \
  -o src/main/java \
  -p com.example.payment.contract
```

**新規ファイル:**
- `src/main/resources/abi/erc20.json`（標準 ERC-20 ABI）
- `src/main/java/com/example/payment/contract/ERC20.java`（自動生成）

**コミットメッセージ例:**
```
feat: ERC-20 ABIバインディング生成（Web3j CLI）
```

---

### Day 12 — トークン残高照会 API

**エンドポイント:**
```
GET /api/v1/chain/token-balance?address=0x...&token=USDC
→ {"address": "0x...", "token": "USDC", "balance": "100.50", "rawBalance": "100500000"}
```

**実装のポイント:**
- `ERC20.load(contractAddress, web3j, credentials, gasProvider)` でコントラクトに接続
- `erc20.balanceOf(address).send()` で残高取得
- `StablecoinType` の `decimals` フィールドで正規化（USDC=6桁なので 10^6 で割る）

**コミットメッセージ例:**
```
feat: ERC-20トークン残高照会API（USDC/USDT/DAI対応）
```

---

### Day 13 — Token 変換ユーティリティ

**新規ファイル:** `src/main/java/com/example/payment/util/TokenAmountConverter.java`

```java
// BigInteger (rawAmount) ↔ BigDecimal (humanReadable) の変換
// decimals を考慮した正規化
// 例: USDC の 1000000 → 1.00
```

**なぜ必要か:**
- USDC/USDT は 6 decimals、DAI は 18 decimals
- `BigDecimal` で正確に扱わないと浮動小数点誤差が出る
- 決済金額の比較はすべてこのユーティリティを経由する

**コミットメッセージ例:**
```
feat: トークン金額変換ユーティリティ（decimals正規化）
```

---

### Day 14-15 — レポート執筆

**ファイル:** `docs/report-03-erc20-stablecoins.md`

**必ず答える問い:**

```
1. スマートコントラクトとは何か
   - コードがチェーン上に永続化されるとはどういうことか
   - コントラクトアドレスはどう決まるか
   - ABI（Application Binary Interface）とは何か

2. ERC-20 規格とは何か
   - なぜ規格が必要か（相互運用性）
   - 必須メソッド一覧と意味
     - totalSupply / balanceOf / transfer / transferFrom / approve / allowance
   - Transfer・Approval イベントとは何か

3. USDC / USDT / DAI の違い
   - USDC（Circle）：法定通貨担保・規制準拠型
   - USDT（Tether）：法定通貨担保・中央集権型
   - DAI（MakerDAO）：暗号資産担保・分散型
   - decimals が 6 と 18 で違う理由

4. decimals の罠
   - なぜ整数で扱うのか（浮動小数点の精度問題）
   - BigInteger / BigDecimal を使う理由
   - 1 USDC = 1000000 の意味
```

**コミットメッセージ例:**
```
docs: report-03 ERC-20・ステーブルコイン（ABI・decimals・USDC vs DAI）
```

---

## Phase 4：Transfer イベント監視 + 入金検知

**目標：** オンチェーンの送金を自動検知し、PaymentOrder を CONFIRMED に更新する

### Day 16 — イベントポーリング基盤

**新規ファイル:** `src/main/java/com/example/payment/chain/TransferEventPoller.java`

```java
// @Scheduled(fixedDelay = 15000) で 15 秒ごとにポーリング
// PENDING の PaymentOrder の receiverAddress 宛の Transfer イベントを検索
// Web3j の EthFilter + ethGetLogs を使用
```

**設計判断のポイント:**
- WebSocket サブスクリプション vs ポーリング
  - Infura 無料枠は WebSocket に制限あり → まずポーリングで安定実装
  - 将来 WebSocket に切り替えられる設計にしておく
- 最後に処理したブロック番号を DB に保存して再起動時にも継続できるようにする

**コミットメッセージ例:**
```
feat: ERC-20 Transferイベントポーリング基盤（15秒間隔）
```

---

### Day 17 — 入金検知 → PaymentOrder 更新

**変更ファイル:** `PaymentService.java`（`confirmPayment` メソッド追加）

```
処理フロー:
Transfer イベント検知
  → receiverAddress が PENDING の PaymentOrder に一致するか確認
  → 金額チェック（expectedAmount との比較）
    → 一致: CONFIRMED
    → 多い: OVERPAID
    → 少ない: UNDERPAID
  → txHash・confirmedAt を保存
```

**コミットメッセージ例:**
```
feat: Transfer入金検知→PaymentOrder自動確認（CONFIRMED/OVERPAID/UNDERPAID）
```

---

### Day 18 — 期限切れジョブ

**新規ファイル:** `src/main/java/com/example/payment/job/PaymentExpiryJob.java`

```java
// @Scheduled(fixedDelay = 60000) で 1 分ごとに実行
// expiresAt が過去かつ PENDING の注文を EXPIRED に更新
// ログに期限切れ件数を出力
```

**コミットメッセージ例:**
```
feat: 期限切れ決済注文の自動EXPIRED更新ジョブ
```

---

### Day 19-20 — レポート執筆

**ファイル:** `docs/report-04-events-and-monitoring.md`

**必ず答える問い:**

```
1. Ethereum のイベント（Log）とは何か
   - なぜコントラクトはイベントを発火するのか
   - Log の構造（address / topics / data）
   - topics[0] がイベントシグネチャのハッシュである意味

2. Transfer イベントの読み方
   - Transfer(address indexed from, address indexed to, uint256 value)
   - indexed パラメーターとは何か（topics に格納される）
   - ABI デコードとは何か

3. eth_getLogs の仕組み
   - fromBlock / toBlock の指定方法
   - address フィルター・topics フィルターの組み合わせ
   - 大量ブロックを一度に取得できない理由

4. ポーリング vs WebSocket サブスクリプション
   - それぞれのメリット・デメリット
   - Infura 無料枠での制約
   - eth_subscribe（WebSocket）の仕組み

5. ブロック確認数（Confirmations）とファイナリティ
   - なぜ 1 ブロックだけでは信頼できないのか
   - 一般的な確認数の目安（取引所は 12〜35 ブロック待つ）
   - Ethereum の PoS 移行後のファイナリティの変化
```

**コミットメッセージ例:**
```
docs: report-04 イベント監視（Logs・Transfer・ポーリング・ファイナリティ）
```

---

## Phase 5：決済フロー完成

**目標：** エンドツーエンドで動く決済システムにする

### Day 21 — Webhook 通知

**新規ファイル:** `src/main/java/com/example/payment/webhook/WebhookService.java`

```java
// Spring 6 の RestClient を使用（RestTemplate の後継・モダンなAPI）
// PaymentOrder の status 変化時に外部 URL へ POST
// リトライは最大 3 回（指数バックオフ）
```

**PaymentOrder エンティティに追加:**
```java
private String webhookUrl;  // 注文作成時にオプションで受け取る
```

**コミットメッセージ例:**
```
feat: Webhook通知機能（ステータス変化時にPOST・RestClient使用）
```

---

### Day 22 — 決済サマリー API

**エンドポイント追加:**
```
GET /api/v1/payments/summary
→ {
    "pending": 3,
    "confirmed": 12,
    "expired": 1,
    "totalConfirmedUSDC": "1250.00"
  }
```

**コミットメッセージ例:**
```
feat: 決済サマリーAPI（ステータス別件数・確認済み合計金額）
```

---

### Day 23 — ロガー・監視強化

**変更内容:**
- MDC（Mapped Diagnostic Context）で各ログに `orderId` を自動付与
- Actuator の `metrics` エンドポイントにカスタムメトリクス追加
  - `payment.orders.created`（カウンター）
  - `payment.orders.confirmed`（カウンター）
  - `payment.confirmation.time`（タイマー：作成〜確認の所要時間）

**コミットメッセージ例:**
```
feat: カスタムメトリクス追加（Micrometer）+ MDCログ改善
```

---

### Day 24-25 — レポート執筆

**ファイル:** `docs/report-05-payment-flow-design.md`

**必ず答える問い:**

```
1. オンチェーン決済の設計パターン
   - なぜ「受取アドレス監視」方式を採用したか
   - 代替案：送金前に approve → transferFrom を使う方式との比較
   - マーチャントごとにアドレスを変える設計 vs 共通アドレスにメモを付ける設計

2. 冪等性とは何か
   - 同じ Transfer イベントを 2 回処理したらどうなるか
   - txHash をユニークキーにする重要性
   - 再起動時のイベント再処理対策

3. 決済のセキュリティ考慮点
   - アドレスのチェックサム（EIP-55）とは何か
   - フロントランニングとは何か（今回の設計では影響するか）
   - 秘密鍵をサーバーに持たない設計の重要性

4. BigDecimal で金額を扱う重要性
   - なぜ double / float を使ってはいけないか
   - 金融計算での丸めルール（RoundingMode.HALF_UP）
   - 比較時の注意点（equals vs compareTo）
```

**コミットメッセージ例:**
```
docs: report-05 決済フロー設計（冪等性・セキュリティ・BigDecimal）
```

---

## Phase 6：テスト + OpenAPI ドキュメント

**目標：** 安心してリファクタできるテスト基盤を作る

### Day 26 — PaymentService 単体テスト

**新規ファイル:** `src/test/java/com/example/payment/service/PaymentServiceTest.java`

```java
// Mockito で PaymentOrderRepository をモック
// テストケース:
//   - 正常な注文作成
//   - TTL のデフォルト値適用
//   - 存在しない ID で getOrder → 例外スロー
//   - listOrders のステータスフィルター
```

**コミットメッセージ例:**
```
test: PaymentService単体テスト（Mockito）
```

---

### Day 27 — Controller 統合テスト

**新規ファイル:** `src/test/java/com/example/payment/controller/PaymentControllerTest.java`

```java
// @WebMvcTest + MockMvc を使用
// テストケース:
//   - POST /api/v1/payments 正常系（201 Created）
//   - POST バリデーションエラー（400 Bad Request・ProblemDetail形式）
//   - GET 存在しない ID（404 Not Found）
```

**コミットメッセージ例:**
```
test: PaymentController統合テスト（MockMvc・ProblemDetail検証）
```

---

### Day 28 — ChainService テスト（Web3j モック）

**新規ファイル:** `src/test/java/com/example/payment/chain/ChainServiceTest.java`

```java
// Web3j をモックして ChainService をテスト
// テストケース:
//   - 正常な ETH 残高取得・Wei → Ether 変換
//   - 無効なアドレス形式でのエラー
//   - ノード接続エラー時のハンドリング
```

**コミットメッセージ例:**
```
test: ChainService単体テスト（Web3jモック）
```

---

### Day 29-30 — レポート執筆

**ファイル:** `docs/report-06-testing-strategy.md`

**必ず答える問い:**

```
1. ブロックチェーンアプリのテスト難しさ
   - なぜ実ネットワークでテストしにくいのか（ガス代・速度・状態管理）
   - テスト戦略の層：ユニット → 統合 → E2E

2. Web3j のモック戦略
   - どのレイヤーでモックするか（Web3j オブジェクト vs JSON-RPC レベル）
   - WireMock を使ったJSON-RPCモックの方法

3. ローカルブロックチェーンの活用
   - Hardhat ノードとは何か
   - テストネット（Sepolia）との使い分け
   - Anvil（Foundry）との比較

4. @Scheduled のテスト方法
   - ジョブを手動トリガーするテスト設計
   - 時刻依存テストの Clock 差し込みパターン
```

**コミットメッセージ例:**
```
docs: report-06 テスト戦略（Web3jモック・ローカルチェーン・Scheduled）
```

---

## Phase 7：デプロイ検討

**目標：** 無料でインターネットからアクセスできる状態にする

### Day 31-32 — Docker 対応

**新規ファイル:** `Dockerfile`、`docker-compose.yml`

```dockerfile
# Dockerfile: マルチステージビルドで最小イメージ
FROM eclipse-temurin:21-jdk-alpine AS build
# ...
FROM eclipse-temurin:21-jre-alpine
# ...
```

```yaml
# docker-compose.yml: ローカル開発用
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - WEB3J_CLIENT_ADDRESS=${WEB3J_CLIENT_ADDRESS}
```

**コミットメッセージ例:**
```
feat: Dockerfile（マルチステージ）+ docker-compose.yml追加
```

---

### Day 33-35 — レポート執筆 + デプロイ手順

**ファイル:** `docs/report-07-deployment.md`

**必ず答える問い:**

```
1. 無料ホスティング比較
   | サービス | 無料枠 | スリープ | DB |
   |---|---|---|---|
   | Railway | 500時間/月 | なし | PostgreSQL |
   | Render | 750時間/月 | 15分で停止 | PostgreSQL |
   | Fly.io | 3 shared VM | なし | — |
   | Koyeb | 1 instance | なし | — |

2. 環境変数管理のベストプラクティス
   - 秘密情報を Git にコミットしてはいけない理由
   - .env / application-local.yml の使い分け
   - クラウドでの Secret 管理（Railway Variables など）

3. H2 → PostgreSQL 移行計画
   - Neon（サーバーレス PostgreSQL）無料枠の詳細
   - application-prod.yml の切り替え方法
   - Flyway / Liquibase によるスキーママイグレーション

4. Spring Boot の本番設定
   - actuator エンドポイントの公開範囲制限
   - ログレベルの本番向け調整
   - JVM ヒープサイズの適切な設定
```

**コミットメッセージ例:**
```
docs: report-07 デプロイ（Railway・Render・H2→PostgreSQL移行）
```

---

## 全体タイムライン

```
Week 1    Phase 1  Day 1-5   環境整備 + Ethereum基礎レポート
Week 2    Phase 2  Day 6-10  Web3j接続・残高API・OpenAPI
Week 3    Phase 3  Day 11-15 ERC-20・ステーブルコイン残高API
Week 4    Phase 4  Day 16-20 Transferイベント監視・入金検知
Week 5    Phase 5  Day 21-25 Webhook・サマリーAPI・メトリクス
Week 6    Phase 6  Day 26-30 テスト基盤整備
Week 7    Phase 7  Day 31-35 Docker・デプロイ
```

---

## ファイル構成（完成時）

```
ethereum-payment-backend/
├── docs/
│   ├── PLAN.md                          ← この計画書
│   ├── report-01-ethereum-basics.md
│   ├── report-02-accounts-and-web3j.md
│   ├── report-03-erc20-stablecoins.md
│   ├── report-04-events-and-monitoring.md
│   ├── report-05-payment-flow-design.md
│   ├── report-06-testing-strategy.md
│   └── report-07-deployment.md
├── scripts/
│   └── check-connection.sh
├── src/main/java/com/example/payment/
│   ├── EthereumPaymentApplication.java
│   ├── chain/
│   │   ├── ChainController.java         ← ETH残高・トークン残高API
│   │   ├── ChainService.java
│   │   ├── ChainHealthIndicator.java    ← Actuatorヘルスチェック
│   │   └── TransferEventPoller.java    ← イベント監視
│   ├── config/
│   │   └── Web3jConfig.java
│   ├── contract/
│   │   └── ERC20.java                  ← 自動生成バインディング
│   ├── controller/
│   │   ├── PaymentController.java
│   │   └── CreatePaymentRequest.java   ← record に移行
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   └── PaymentOrderNotFoundException.java
│   ├── job/
│   │   └── PaymentExpiryJob.java       ← 期限切れ自動更新
│   ├── model/
│   │   ├── PaymentOrder.java
│   │   ├── PaymentStatus.java
│   │   └── StablecoinType.java
│   ├── repository/
│   │   └── PaymentOrderRepository.java
│   ├── service/
│   │   └── PaymentService.java
│   ├── util/
│   │   └── TokenAmountConverter.java   ← decimals正規化
│   └── webhook/
│       └── WebhookService.java         ← RestClient で通知
├── src/main/resources/
│   ├── abi/
│   │   └── erc20.json
│   └── application.yml
├── src/test/
│   └── ...
├── .env.example
├── .gitignore
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## コミットルール

| プレフィックス | 用途 |
|---|---|
| `feat:` | 新機能追加 |
| `fix:` | バグ修正 |
| `refactor:` | 動作を変えないコード整理 |
| `test:` | テスト追加・修正 |
| `docs:` | レポート・README・コメント |
| `chore:` | 設定ファイル・依存関係・CI |
