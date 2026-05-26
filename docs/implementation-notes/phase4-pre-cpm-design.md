# Phase 4 着手前 CPM 事前設計変更

**実施日:** 2026-05-26
**フェーズ:** Phase 4 着手前（Phase 3 完了後）

---

## 変更の背景

Phase 4（Transfer イベント監視）に入る前に、将来の CPM（Consumer Presented Mode）対応に備えた
最小限のスキーマ・ロジック変更を先行実施した。
大幅なリファクタリングを避けるため、**拡張ポイントの追加のみ**を行い、既存 MPM フローは一切変えない方針とした。

---

## 実装した変更

### 追加: `PaymentMode` enum

```java
public enum PaymentMode { MPM, CPM }
```

MPM（Merchant Presented Mode）= 加盟店がアドレスを提示するこのプロジェクトの現行方式。
CPM（Consumer Presented Mode）= 消費者がQRを提示する方式（将来対応）。

### 追加: `PaymentStatus.AWAITING_CONSUMER`

CPM 専用ステータス。注文作成直後・消費者QRスキャン前の状態を表す。
現時点では使用されないが、将来 CPM フローを追加する際に必要となる。

### 変更: `PaymentOrder`

| フィールド | 変更内容 |
|---|---|
| `paymentMode` | 追加（`@Enumerated(STRING)`, `nullable=false`）|
| `consumerNonce` | 追加（`VARCHAR(64)`, nullable, CPM専用）|
| `senderAddress` | `nullable=false` → `nullable=true`（CPM では注文時に未確定）|

### 変更: `CreatePaymentRequest`

`senderAddress` から `@NotBlank` を除去。フォーマット検証（`@Pattern`）は維持。
`paymentMode` フィールドを追加（省略時はサービス層で MPM に補完）。

### 変更: `PaymentService`

モードに応じた検証ロジックを追加:

```
MPM: senderAddress 必須（null/blank はエラー）
CPM: senderAddress 指定禁止（エラー）、consumerNonce を SecureRandom で生成
     初期ステータスを AWAITING_CONSUMER に設定
```

---

## 設計の判断

### consumerNonce の生成場所

consumerNonce はサーバー側で生成する（32バイトのランダム値を16進数エンコード）。
クライアントが指定することは許可しない（不正な nonce による攻撃を防ぐため）。

### CPM で senderAddress を禁止した理由

CPM では消費者のアドレスが QR スキャン後に判明するため、
注文作成時に senderAddress を指定することは概念的に矛盾する。
誤った使い方をエラーとして弾くことで、実装フェーズの混乱を防ぐ。

### SecureRandom の static フィールド管理

`SecureRandom` インスタンスはスレッドセーフなので static フィールドで共有可能。
毎回 `new SecureRandom()` すると OS エントロピーの無駄な消費が発生するため、static にした。

### @PrePersist での paymentMode デフォルト設定

サービス層で常に paymentMode を明示的に設定するが、
直接 `PaymentOrder.builder()` を使うテストや将来の操作で null になるリスクに備え、
`@PrePersist` でも MPM をデフォルトとして設定した。

---

## 却下した選択肢

### カスタムバリデーションアノテーション

クラスレベルの `@Constraint` で record に cross-field バリデーションを実装する案も検討したが、
- 実装の複雑さが増す
- 現時点では CPM フローが未実装で条件が流動的
- サービス層で検証する方が `IllegalArgumentException` → 400 レスポンスの流れが明確

→ サービス層での検証に統一した。

---

## 今後の CPM 実装で必要になること（Phase 4 以降）

- QR コード生成 API: `consumerNonce` を埋め込んだ QR を返す
- Consumer アドレス提出 API: スキャン後に `senderAddress` を設定し、ステータスを `PENDING` に遷移
- 重複 consumerNonce チェック（DB UNIQUE 制約追加）
