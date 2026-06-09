# Phase 5 Day 22-23 実装ノート — EIP-2612 Permit + transferFrom

## 実装した機能

ユーザーが `eth_signTypedData_v4` で署名するだけで、ガスなしで ERC-20 支払いを完結させるフロー。

## 主要な設計判断

### 1. permitName / permitVersion を StablecoinType に持たせた

**判断:** コントラクトの `name()` / `version()` をリクエストのたびに eth_call するのではなく、`StablecoinType` に定数として保持。

**理由:** ドメインセパレータの計算は頻繁に行われるため、RPC 呼び出しを減らしたい。変更が必要な場合は enum を修正してデプロイするだけでよい。

**トレードオフ:** コントラクトを変更した場合（ほぼない）は enum も更新が必要。実際の値はデプロイ時に `name()` を呼び出して確認すること。

### 2. @Transactional を execute() から除去し PROCESSING ステータスを追加

**問題:** `@Transactional` の中で `waitForReceipt`（最大120秒 × 2 = 240秒）が走ると、DB コネクションを長時間保持しコネクションプールが枯渇する。また READ_COMMITTED では同一オーダーへの並行リクエストを防げない。

**解決策:**
1. `PaymentStatus.PROCESSING` を追加
2. `updateStatusConditionally(PENDING → PROCESSING)` で CAS 的なアトミック遷移
3. `execute()` 自体には `@Transactional` を付けず、各 Spring Data リポジトリメソッドが個別の短命トランザクションを使用

**注意点:** permit TX 成功 → transferFrom TX 失敗の場合、オーダーは PROCESSING のまま残る。現状リカバリーは手動対応（Phase 6 で対処を検討）。

### 3. クライアントから nonce を受け取る

**判断:** `ExecutePermitRequest` に `nonce` フィールドを追加し、サーバー側で現在のオンチェーン nonce と比較する。

**理由:** 署名後に nonce が変化していた場合、`verifyPermitSignature` は失敗するが「なぜ失敗したか」がクライアントに伝わらない。明示的な nonce ミスマッチエラーで "re-fetch and re-sign" を促せる。

### 4. EIP-1559 対応 (sendEIP1559Transaction)

**理由:** Polygon Mainnet (chainId=137) および Ethereum Mainnet (chainId=1) ともに EIP-1559 を採用済み。レガシーな `ethGasPrice()` は Polygon で過剰なガス代を払うリスクがある。

**実装:** `baseFee * 2 + maxPriorityFeePerGas` を `maxFeePerGas` として使用。

**TODO:** `baseFee` が null の場合のフォールバック（EIP-1559 未対応チェーンへの拡張時に対処）。

### 5. USDT は EIP-2612 非対応

Ethereum Mainnet の USDT は permit をサポートしない。`StablecoinType.permitSupported=false` で明示的に拒否。

## セキュリティ対応（CLAUDE.md 準拠）

- 秘密鍵: `${PERMIT_SPENDER_PRIVATE_KEY:}` 環境変数のみ、コードに埋め込みなし
- アドレス検証: `ETH_ADDRESS_PATTERN` で全入力を検証
- txHash 検証: `TX_HASH_PATTERN` で永続化前に検証
- エラーメッセージ: `PermitException` と `IllegalArgumentException` いずれも汎用メッセージを返却、詳細はログのみ
- オフチェーン署名検証: ガス消費前に EIP-712 署名を検証

## レビューで修正した主な点（PR#20）

| 分類 | 指摘 | 対処 |
|---|---|---|
| Blockchain | JPYC アドレス誤り | `0x431D5dfF...` に更新（TODO コメント付き） |
| Blockchain | USDT EIP-2612 非対応 | `permitSupported` フラグ追加 + 入口で拒否 |
| Blockchain | deadline 過去チェックなし | `execute()` 冒頭に追加 |
| Blockchain | Polygon で ethGasPrice() レガシー | EIP-1559 `sendEIP1559Transaction` に変更 |
| Blockchain | nonce ミスマッチ不明確 | `ExecutePermitRequest.nonce` フィールド追加 |
| Spring Boot | @Transactional が 240s I/O を包む | PROCESSING ステータス + CAS で解決 |
| Security | PermitException メッセージ露出 | 汎用メッセージに変更 |
| Security | IllegalArgumentException 露出 | 同様に汎用化 |

## 未解決の既知課題

- permit 成功 → transferFrom 失敗後の PROCESSING オーダーのリカバリー
- JPYC の `permitName` の本番前オンチェーン検証が必要（`name()` 呼び出し）
- `baseFee` null 時のフォールバック（EIP-1559 未対応チェーン対応時）
- レート制限（Phase 6 でのインフラ整備時に対処）
