# Phase 2 実装ノート — Web3j接続 & アカウント/残高API

## 判断・変更・妥協点

### 1. `CreatePaymentRequest` を Java record に変換（Day 8）

**判断:** `@Data`（Lombok）から Java record に変換した。

**理由:**
- リクエストDTOは不変であるべき。`@Data` は setter を生成するため、バリデーション後に値が変更される余地がある
- Java record はコンストラクタ引数にバリデーションアノテーションを付与でき、Jackson 2.12+ で JSON デシリアライズが機能する
- Spring Boot 3.2 + Jackson 2.16 の組み合わせでは record への `@RequestBody` デシリアライズが正式サポートされている

**影響:** `PaymentService` のアクセサ呼び出しを `getX()` から `x()` に変更した（例: `request.getToken()` → `request.token()`）

---

### 2. `ChainCommunicationException` を `RuntimeException` のサブクラスとして設計

**判断:** checked exception（`IOException`）をラップして unchecked exception にする設計にした。

**理由:**
- `ChainService.getEthBalance` は `IOException` をキャッチして `ChainCommunicationException` に変換する
- Spring の `@ExceptionHandler` は unchecked exception のほうが扱いやすい
- 呼び出し元（Controller）に try-catch を強制しない

**グローバルハンドラ:** `GlobalExceptionHandler` に `ChainCommunicationException → 502 Bad Gateway` のハンドラを追加した。Ethereum ノードとの通信失敗はクライアントの誤りではなく上流障害なので 502 が適切。

---

### 3. `ChainController` のパスバリデーションに `@Validated` + `@Pattern` を使用

**判断:** `@PathVariable` に直接 `@Pattern` を付与し、クラスレベルで `@Validated` を付けた。

**理由:**
- `@Valid` は `@RequestBody` オブジェクトの内部フィールドを検証するが、プリミティブ型のパスバリデーションには対応していない
- `@Validated`（Spring）+ `@Pattern`（Bean Validation）の組み合わせが `@PathVariable` バリデーションの標準手法
- 不正アドレスは `ConstraintViolationException` として投げられるため、`GlobalExceptionHandler` に対応ハンドラを追加した

---

### 4. SpringDoc OpenAPI 2.5.0 を採用（Day 9）

**判断:** `springdoc-openapi-starter-webmvc-ui:2.5.0` を使用。

**理由:**
- Spring Boot 3.x 対応は `springdoc-openapi-starter-*` 系（2.x）である。旧 `springfox` や `springdoc 1.x` は Spring Boot 3 に非対応
- Swagger UI が `/swagger-ui.html` で自動提供され、`/v3/api-docs` で OpenAPI 3.0 JSON が取得できる

**アノテーション方針:** Controller に `@Tag` + `@Operation` のみ追加。`@ApiResponse` は現時点では省略（`GlobalExceptionHandler` がエラー形式を統一しているため、過剰な記述を避けた）

---

### 5. `EthBalanceResponse` を Java record として設計

**判断:** `address`・`balanceEth`・`balanceWei` の3フィールドを持つ record として設計。

**理由:**
- レスポンスDTOは生成後に変更されないため record が最適
- `balanceEth` は `BigDecimal.toPlainString()` で科学的記数法を避けた文字列で返す（例: `"1.5"` であり `"1.5E+0"` にならない）
- `balanceWei` も文字列で返す。Wei は最大 `2^256 - 1` と巨大な整数になる可能性があり、JSON の数値型（`long` や `double`）では精度が失われる

---

### 6. 却下した選択肢

| 選択肢 | 却下理由 |
|---|---|
| `getEthBalance` の戻り値に `BigDecimal` をそのまま使う | JSON シリアライズ時の精度・形式を制御するために文字列に変換 |
| `ChainCommunicationException` を checked exception にする | Controller 層で try-catch が必要になり記述量が増える。Spring の例外ハンドラ機構を活用すべき |
| `PaymentController` に `@Validated` を追加 | `@RequestBody` の場合は `@Valid` で十分。`@Validated` はパスバリデーション専用に限定して混在を避けた |
