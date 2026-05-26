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
Phase 0  ✅ 完了   初期構成（Gradle・パッケージ・README・セキュリティ基盤）
Phase 1  ✅ 完了   環境整備 + Ethereum 基礎理解
Phase 2  ✅ 完了   Web3j 接続 + アカウント・残高操作
Phase 3  ✅ 完了   ERC-20 規格 + JPYC 残高取得（JPYC優先）
Phase 4  🔜 次     Transfer イベント監視 + 入金検知
Phase 5            ウォレット認証 + スマコン事前承認決済（SIWE・Permit）
Phase 5-F          フロントエンド（Next.js 15 + Wagmi v2 + RainbowKit）送金UI
Phase 6            自動購入・サブスク + テスト + OpenAPI
Phase 7            デプロイ検討（Railway / Render 無料枠）
```

> **優先トークン：JPYC**（HashPort Wallet との組み合わせで日本円決済を実現）

---

## Phase 0 ✅ 完了

### 成果物

| ファイル | 内容 |
|---|---|
| `build.gradle.kts` | Spring Boot 3.2・Web3j 4.10・H2・Lombok（Gradle Kotlin DSL）|
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

**`build.gradle.kts` 追加:**
```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
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

## Phase 5：ウォレット認証 + スマコン事前承認決済

**目標：** HashPort Wallet でログイン → JPYC を事前承認 → 即時決済できる仕組みを実装する

### Day 21 — SIWE 認証基盤（Sign-In With Ethereum）

**前提作業（Day 21 着手前に実施）:**
- `build.gradle.kts` に `implementation("org.springframework.boot:spring-boot-starter-security")` を追加
- JWT を使う場合は `implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")` も追加

**新規ファイル:**
- `src/main/java/com/example/payment/auth/SiweController.java`
- `src/main/java/com/example/payment/auth/SiweService.java`
- `src/main/java/com/example/payment/auth/SiweNonce.java`（JPA エンティティ）
- `src/main/java/com/example/payment/config/SecurityConfig.java`（`@Configuration` + `SecurityFilterChain`）
- `src/main/java/com/example/payment/auth/JwtAuthFilter.java`（`OncePerRequestFilter`）

```
認証フロー:
1. GET /api/v1/auth/nonce → ランダムなワンタイムトークンを返す（DB の SiweNonce に保存）
2. フロント側：HashPort Wallet でメッセージに署名（EIP-4361形式）
3. POST /api/v1/auth/verify → 署名を検証・domain が自サービスと一致することをバックエンドで確認
                              → nonce を使用済みにして JWT を発行
4. 以降のリクエストは Authorization: Bearer <JWT> ヘッダーで認証
```

**SiweNonce テーブル設計（最小構成）:**
| カラム | 型 | 説明 |
|---|---|---|
| `nonce` | VARCHAR(64) PK | ランダム生成トークン |
| `used` | BOOLEAN | 使用済みフラグ（true で再利用不可）|
| `expires_at` | TIMESTAMP | 有効期限（5分程度を推奨）|
| `created_at` | TIMESTAMP | 生成日時 |

**SecurityConfig 設計方針:**
- `/api/v1/auth/**` は PERMIT_ALL（認証前なので公開）
- その他エンドポイントは AUTHENTICATED を要求
- CSRF は JWT 認証に切り替えるため無効化（`csrf.disable()`）
- SIWE 検証後は `UsernamePasswordAuthenticationToken` にウォレットアドレスを格納して `SecurityContextHolder` に設定

**学習ポイント:**
- 公開鍵暗号の署名検証（secp256k1・Web3j の Sign クラス）
- EIP-4361 メッセージフォーマット（domain / nonce / issuedAt）
- リプレイ攻撃防止（nonce の使い捨て・有効期限）
- JWT アルゴリズム（HS256 vs RS256）・有効期限・リフレッシュトークン設計

**コミットメッセージ例:**
```
feat: SIWE認証基盤（Sign-In With Ethereum / EIP-4361）
```

---

### Day 22 — EIP-2612 Permit ガスレス承認

**目標:** ユーザーが ETH を持っていなくても JPYC の支払い承認ができる仕組みを実装

**新規ファイル:** `src/main/java/com/example/payment/chain/PermitService.java`

```
処理フロー:
1. バックエンドが EIP-712 署名データを生成してフロントに渡す
2. HashPort Wallet でオフチェーン署名（ETH ガス不要）
3. バックエンドが permit(owner, spender, value, deadline, v, r, s) を呼び出す
4. approve と同等の状態がオンチェーンに記録される
```

**学習ポイント:**
- EIP-712（構造化データ署名）とは何か
- nonce と deadline でリプレイ攻撃を防ぐ仕組み
- JPYCv2 は EIP-2612 対応済みのため即利用可能

**コミットメッセージ例:**
```
feat: EIP-2612 Permit実装（JPYCガスレス承認）
```

---

### Day 23 — approve/transferFrom 即時決済

**目標:** 事前承認済みの JPYC を、ユーザーの追加署名なしでバックエンドから引き落とす

**新規ファイル:** `src/main/java/com/example/payment/chain/ApprovalPaymentService.java`

```
処理フロー:
1. ユーザーが Permit（Day 22）または通常の approve() で上限承認
2. 決済時：バックエンドが transferFrom(ユーザー, 受取アドレス, 金額) を呼び出す
3. ユーザーの署名なしで即時決済完了
4. PaymentOrder を CONFIRMED に更新
```

**設計判断のポイント:**
- バックエンドが送金操作をするため、サーバー秘密鍵（spender ウォレット）の管理が必要
- 秘密鍵は環境変数で管理・絶対にコードに書かない
- spender ウォレットは `transferFrom` 専用に分離・ETH 保有量はガス代のみに留める
- allowance は決済金額ぴったりを承認させる設計（無制限 approve を避ける）
- allowance の残高チェックを毎回行い不足なら即エラー
- `Credentials` オブジェクト（Web3j）は Spring @Bean として管理・ログ出力でアドレスが漏れないよう注意

**チェーン操作とDB更新のトランザクション整合性:**
```
設計パターン（2フェーズコミット問題への対処）:
1. @Transactional の外で transferFrom TX をチェーンに送信
2. TX 成功後に @Transactional 内で DB を CONFIRMED に更新
3. txHash を DB のユニークキーにして二重処理を防止
4. TX 成功 → DB 更新失敗 の場合：txHash でべき等チェック（次回ポーリングで復旧）
```

**コミットメッセージ例:**
```
feat: approve/transferFrom即時決済（事前承認JPYC引き落とし）
```

---

### Day 24 — Webhook 通知 + 決済サマリー API

**Webhook（新規ファイル）:** `src/main/java/com/example/payment/webhook/WebhookService.java`

```java
// Spring 6 の RestClient を使用
// PaymentOrder の status 変化時に外部 URL へ POST
// @TransactionalEventListener(phase = AFTER_COMMIT) + @Async で
// DB コミット後にトランザクション外で発火（HTTP 待ちでトランザクションを長保持しない）
// リトライは最大 3 回（指数バックオフ）
```

**サマリー API（エンドポイント追加）:**
```
GET /api/v1/payments/summary
→ {"pending": 3, "confirmed": 12, "expired": 1, "totalConfirmedJPYC": "15000"}
```

**コミットメッセージ例:**
```
feat: Webhook通知 + 決済サマリーAPI
```

---

### Day 25 — レポート執筆

**ファイル:** `docs/report-05-wallet-auth-and-smartcontract.md`

**必ず答える問い:**

```
1. SIWE（Sign-In With Ethereum）とは何か
   - なぜパスワードが不要なのか（秘密鍵が認証器）
   - EIP-4361 のメッセージフォーマット
   - nonce によるリプレイ攻撃防止の仕組み
   - 従来の OAuth / JWT 認証との違い

2. EIP-712 構造化データ署名とは何か
   - なぜ単純な文字列署名ではダメなのか
   - domain separator の役割
   - フィッシング耐性はなぜ高いのか

3. EIP-2612 Permit とは何か
   - approve() との違い
   - ガスレスの仕組み（オフチェーン署名 + オンチェーン実行）
   - deadline と nonce の意味

4. approve / transferFrom パターンとは何か
   - 通常の transfer との違い
   - allowance とは何か
   - バックエンドが transferFrom を呼ぶ際のセキュリティ考慮点
   - 秘密鍵をサーバーに持つことのリスクと対策

5. HashPort Wallet × JPYC × EIP-7702 の関係
   - EIP-7702 とは何か（EOA のスマートウォレット化）
   - HashPort が EIP-7702 を実装済みである意味
   - ガスレス決済の全体像
```

**コミットメッセージ例:**
```
docs: report-05 ウォレット認証・スマコン決済（SIWE・Permit・transferFrom）
```

---

## Phase 5-F：フロントエンド（送金UI）

**目標：** ウォレット接続 → 残高確認 → Permit 署名 → 送金確定 の一連の流れをブラウザで操作できる画面を実装する

### 技術スタック

| カテゴリ | 技術 | バージョン | 役割 |
|---|---|---|---|
| フレームワーク | Next.js (App Router) | 15.x | SSR + クライアント画面 |
| 言語 | TypeScript | 5.x | 型安全 |
| Web3 ライブラリ | Wagmi v2 + Viem | latest | ウォレット接続・署名・コントラクト呼び出し |
| ウォレットUI | RainbowKit | 2.x | MetaMask/HashPort 接続ダイアログ |
| UI コンポーネント | shadcn/ui + Tailwind CSS | latest | モダンな見た目 |
| HTTP クライアント | fetch（Next.js ビルトイン） | — | バックエンド API 呼び出し |

> **配置:** `ethereum-payment-frontend/`（バックエンドと別リポジトリ or monorepo サブディレクトリ）

---

### Day 21-F — プロジェクト初期構成 + ウォレット接続画面

**構成コマンド:**
```bash
npx create-next-app@latest ethereum-payment-frontend \
  --typescript --tailwind --app --src-dir
cd ethereum-payment-frontend
npx shadcn@latest init
npm install wagmi viem @rainbow-me/rainbowkit @tanstack/react-query
```

**実装内容:**
- Wagmi + RainbowKit のプロバイダーセットアップ（Polygon Mainnet / Ethereum Mainnet 設定）
- ウォレット接続ボタン（MetaMask / HashPort 対応）
- 接続中のアドレス・チェーン表示

**画面:**
```
┌─────────────────────────────────┐
│  💳 JPYC Payment Demo           │
│                                 │
│  [ウォレットを接続]              │
│                                 │
│  接続後: 0xd8dA...6045          │
│  チェーン: Polygon Mainnet       │
└─────────────────────────────────┘
```

---

### Day 22-F — トークン残高照会画面

**実装内容:**
- バックエンド `GET /api/v1/chain/token-balance` を呼び出して残高を表示
- JPYC / USDC / USDT / DAI のタブ切り替え
- ポーリング（10秒ごと自動更新）

**画面:**
```
┌─────────────────────────────────┐
│  残高照会                        │
│  アドレス: 0xd8dA...6045        │
│                                 │
│  [JPYC] [USDC] [USDT] [DAI]    │
│                                 │
│  JPYC 残高: 1,000.00 JPYC      │
│  （raw: 1000000000000000000000）│
└─────────────────────────────────┘
```

---

### Day 23-F — 送金フォーム + SIWE 認証

**実装内容:**
- SIWE ログイン（`GET /api/v1/auth/nonce` → ウォレット署名 → `POST /api/v1/auth/verify`）
- JWT をブラウザ（メモリ or httpOnly Cookie）に保持
- 送金フォーム: 受取アドレス・金額・トークン種別の入力

**画面:**
```
┌─────────────────────────────────┐
│  送金                            │
│                                 │
│  受取アドレス: [0x............] │
│  金額:         [    100] JPYC   │
│  トークン:     [JPYC ▼]        │
│                                 │
│  [確認画面へ]                    │
└─────────────────────────────────┘
```

---

### Day 24-F — Permit 署名 + 送金確定

**実装内容:**
- `wagmi` の `useSignTypedData`（EIP-712）で Permit 署名を実行
- バックエンド `POST /api/v1/payments` で決済注文を作成
- 決済ステータスのポーリング表示（PENDING → CONFIRMED / EXPIRED）

**送金フロー（画面遷移）:**
```
フォーム入力
  → 確認画面（金額・受取アドレス・手数料）
  → [Permit 署名] ← MetaMask/HashPort でポップアップ
  → バックエンドへ送信
  → ステータス待機画面（ポーリング）
  → 完了画面（txHash 表示）
```

---

### Day 25-F — レポート執筆

**ファイル:** `docs/report-05F-frontend.md`

**必ず答える問い:**
```
1. Wagmi / Viem とは何か（ethers.js との違い）
2. RainbowKit のウォレット対応範囲
3. useSignTypedData で EIP-712 署名をする仕組み
4. フロントとバックエンドの責任分界（署名はフロント・送金処理はバック）
5. SIWE の JWT をどこに保存するか（メモリ vs Cookie のトレードオフ）
```

---

## Phase 6：自動購入・サブスク + テスト + OpenAPI

**目標：** 定期課金・自動購入を実装し、テストと API ドキュメントを整備する

### Day 26 — Sablier Flow 連携（JPYC ストリーミング決済）

**目標:** 毎秒単位で JPYC が自動送金される「ストリーミング決済」を実装

**新規ファイル:**
- `src/main/java/com/example/payment/chain/StreamingPaymentService.java`
- `src/main/java/com/example/payment/model/SablierStream.java`（JPA エンティティ）

```
Sablier Flow の仕組み:
1. ユーザーが JPYC を Sablier コントラクトに預ける
2. 受取人（マーチャント）は毎秒 JPYC を受け取り続ける
3. バックエンドはストリームの状態を監視・DB に記録
4. ユーザーはいつでも停止・引き出しできる

ユースケース:
- 月額サブスクリプション（1秒単位で課金）
- サービス利用時間に応じた従量課金
```

**SablierStream エンティティ設計（PaymentOrder とは独立したライフサイクル）:**
| カラム | 型 | 説明 |
|---|---|---|
| `id` | UUID PK | 内部ID |
| `stream_id` | BIGINT UNIQUE | Sablier コントラクトが発行するストリームID |
| `wallet_address` | VARCHAR(42) | 送金者アドレス |
| `token` | VARCHAR(10) | トークン種別（JPYC 等）|
| `rate_per_second` | DECIMAL(36,18) | 毎秒送金レート |
| `status` | VARCHAR(20) | ACTIVE / PAUSED / DEPLETED / CANCELED |
| `created_at` | TIMESTAMP | 開始日時 |

**学習ポイント:**
- Sablier のコントラクト ABI を Web3j でラップ
- ストリーム開始・停止・残高確認の実装（`withdrawable_amount(stream_id)`）
- ストリーム ID を DB で管理（`SablierStream` エンティティ）

**コミットメッセージ例:**
```
feat: Sablier Flow連携（JPYCストリーミング・サブスク決済）
```

---

### Day 27 — カスタム自動購入コントラクト設計

**目標:** allowance + スケジューラーで定期自動引き落としを実装

**新規ファイル:** `src/main/java/com/example/payment/job/AutoPurchaseJob.java`

```
処理フロー:
1. ユーザーが大きめの allowance を事前承認（例：月10,000 JPYC分）
2. @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Tokyo") で毎月1日 JST 0時に実行
3. allowance が不足した場合は通知してスキップ
4. 実行履歴を DB に記録（auto_purchase_executions テーブル）
```

**重要な設計考慮点:**
- **排他制御（必須）:** クラスター環境で複数インスタンスが同時に実行されないよう ShedLock を使用
  - `build.gradle.kts` に `implementation("net.javacrumbs.shedlock:shedlock-spring:x.y.z")` を追加
  - `@SchedulerLock(name = "autoPurchaseJob", lockAtMostFor = "PT10M")` をジョブに付与
- **仮想スレッド化:** `@Scheduled` はデフォルトで仮想スレッドが適用されない
  - `@Bean ThreadPoolTaskScheduler` をオーバーライドして `setVirtualThreads(true)` を設定（Spring 6.1+）
  - または `spring.task.scheduling.virtual-threads.enabled=true`（Spring Boot 3.4+）
- **チェーン操作のトランザクション整合性:** Day 23 と同じパターン（txHash のユニーク制約でべき等保証）

**コミットメッセージ例:**
```
feat: JPYC自動購入ジョブ（allowance+Scheduled+ShedLock排他制御）
```

---

### Day 28 — テスト基盤整備

**新規ファイル:**
- `PaymentServiceTest.java` — Mockito 単体テスト
- `PaymentControllerTest.java` — MockMvc 統合テスト
- `ChainServiceTest.java` — Web3j モックテスト
- `SiweServiceTest.java` — 署名検証テスト

**コミットメッセージ例:**
```
test: 全サービスの単体・統合テスト追加
```

---

### Day 29 — SpringDoc OpenAPI 整備

**目標:** Swagger UI で全エンドポイントを日本語で説明

```java
// 各コントローラーに @Operation / @Tag を追加
// リクエスト・レスポンスの例を @Schema で定義
// http://localhost:8080/swagger-ui.html で確認
```

**コミットメッセージ例:**
```
feat: SpringDoc OpenAPI整備（Swagger UI・日本語説明）
```

---

### Day 30 — レポート執筆

**ファイル:** `docs/report-06-auto-payment-and-testing.md`

**必ず答える問い:**

```
1. Sablier とは何か
   - ストリーミング決済の仕組み
   - Lockup（期間固定）と Flow（オープンエンド）の違い
   - Spring Boot から Sablier コントラクトを呼ぶ方法
   - JPYC との相性（ERC-20 であれば何でも使える）

2. 自動購入の設計パターン
   - allowance + transferFrom パターンの限界（ユーザーの取り消しリスク）
   - EIP-4337 のセッションキーによる解決策
   - 失敗時のリトライ・通知設計

3. ブロックチェーンアプリのテスト戦略
   - なぜ実ネットワークでテストしにくいのか
   - ローカルチェーン（Hardhat / Anvil）の使い分け
   - Web3j のモック戦略（どのレイヤーでモックするか）
   - @Scheduled ジョブのテスト方法（Clock 差し込みパターン）
```

**コミットメッセージ例:**
```
docs: report-06 自動決済・テスト戦略（Sablier・allowance・Web3jモック）
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
Week 2    Phase 2  Day 6-10  Web3j接続・残高API
Week 3    Phase 3  Day 11-15 ERC-20・JPYC残高API
Week 4    Phase 4   Day 16-20 Transferイベント監視・入金検知
Week 5    Phase 5   Day 21-25 SIWE認証・EIP-2612 Permit・即時決済
Week 5-F  Phase 5-F Day 21F-25F フロントエンド（Next.js・Wagmi・送金UI）
Week 6    Phase 6   Day 26-30 Sablier自動購入・テスト・OpenAPI
Week 7    Phase 7   Day 31-35 Docker・デプロイ
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
│   ├── report-05-wallet-auth-and-smartcontract.md
│   ├── report-06-auto-payment-and-testing.md
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
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties
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
