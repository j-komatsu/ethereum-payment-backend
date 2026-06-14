# Day 07 — Phase 6 実装・ドキュメントPR・レビュー多数の知見

## セッション概要

- docs PR #22（report-16〜21）を3エージェントレビュー後にマージ
- Phase 6（Sablier Flow ストリーミング・AutoPurchaseJob・OpenAPI）を実装
- PR #23 を3エージェントレビュー（計8件 🔴）後にマージ

---

## ✅ よかったこと

### Mermaid 図の Polygon PoS ファイナリティ表記の矛盾を自動検出

report-16 で「2分（チェックポイント）」と説明しているのに、report-17 の Mermaid 図では「2秒でファイナリティ」と書いていた矛盾を BC/Web3j エージェントが検出。ドキュメント間の整合性はエージェントレビューが有効。

### AutoPurchaseProcessor の別 Bean 分離

`@Transactional(propagation = REQUIRES_NEW)` は自クラス内の self-invocation では Spring AOP プロキシを経由しないため機能しない。Spring Boot エージェントが指摘するまで見落としていた。**トランザクション境界を自クラスで自己呼出しする設計は Spring では機能しない**。

### IDOR を Security エージェントが確実に検出

`GET /streams/{streamId}` や `GET /streams/{streamId}/withdrawable` に Authentication を渡していなかった。実装時に「一覧は自分のだけ」と認識していたが、ID 指定の直接アクセスへのオーナーチェックを忘れていた。IDOR は実装者が最も見落としやすいカテゴリ。

---

## 🔧 改善が必要なこと

### Sablier コントラクトアドレスの確認を先に行う

`0x1a272...` というアドレスを実装時にプレースホルダーとして入力したが、on-chain 実装前に必ず公式ドキュメントでアドレスを確認する習慣が必要。実際のコントラクトアドレスは必ず外部ドキュメントで確認してから記述する。

### `spenderAddress` のフェイル・ファスト設計を最初から考える

ゼロアドレスで全件スキップになる可能性をコーディング時に認識していたが「TODO」で先送りした。フェイル・ファスト（未設定なら即エラー）を最初から組み込む設計にすべきだった。

---

## ⚠️ 注意するべきこと

### Sablier Flow の関数名は `void(streamId)` で `cancel` ではない

Sablier Flow のキャンセル関数は `void(uint256 streamId)` という名前。`cancel` と誤記しやすいので注意。`void` は Solidity の予約語（返値なし）と同じ名前だが、Sablier は意図的にこの名前を使っている。

### REQUIRES_NEW と self-invocation

`@Transactional(propagation = Propagation.REQUIRES_NEW)` を同一クラスのメソッドに付けても、そのクラス内から呼び出す限り Spring AOP プロキシをバイパスするため機能しない。別クラス（別 `@Component` / `@Service`）に切り出す必要がある。

### OpenZeppelin v5 のインポートパス変更

`@openzeppelin/contracts/security/ReentrancyGuard.sol` は v4 以前のパス。v5 以降は `utils/ReentrancyGuard.sol` に変更されている。Solidity コードのサンプルを書く際は使用している OZ のバージョンを明記する。

### EIP-7702 の「導入予定」→「導入済み」

Pectra アップグレード（2025年5月）によって EIP-7702 は本番稼働済み。ドキュメント作成時点の「将来」が現在では「過去」になっていることがある。タイムライン表記は現在日付（2026年6月）で見直す必要があった。

---

## 技術メモ

### ShedLock + JdbcTemplate の設定

```java
JdbcTemplateLockProvider.Configuration.builder()
    .withJdbcTemplate(new JdbcTemplate(dataSource))
    .usingDbTime()  // DB サーバーのクロックを使う（クラスタ間のクロックずれ対策）
    .build()
```

- `usingDbTime()` は本番必須。アプリサーバーのクロックがずれている環境でも正確に動作する。
- H2（開発）と PostgreSQL（本番）の両方で動作する。

### Sablier withdrawableAmountOf の戻り値型

```java
// 正しい型: uint128（Uint128）
List.of(new TypeReference<Uint128>() {})
```

ABI に `withdrawableAmountOf(uint256) returns (uint128)` と記載されており、`Uint256` ではなく `Uint128` が正しい。

### イベント処理の独立トランザクション（再確認）

```
ループ内の各処理を独立トランザクションで → AutoPurchaseProcessor(@Transactional REQUIRES_NEW)
ループ全体を1トランザクションで → 1件失敗で全記録ロールバック（NG）
```

バッチ処理では「件ごとに独立したトランザクション + 例外を catch してスキップ記録」が基本パターン。

---

## 次セッションへの引き継ぎ

- Phase 6 ✅ 完了（Sablier・AutoPurchaseJob・OpenAPI）
- Phase 7 🔜 次: Docker 対応（マルチステージ Dockerfile + docker-compose.yml）
- TODO: `permit.spender-address` を `.env.example` に追加する
- TODO: Sablier Flow のコントラクトアドレスを公式ドキュメントで確認して `application.yml` に記載する
