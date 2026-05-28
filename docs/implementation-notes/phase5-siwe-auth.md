# Phase 5 実装ノート — SIWE 認証・JWT 発行・Spring Security

**対象 Day:** 21  
**実装ブランチ:** `feat/siwe-auth`  
**PR:** #19

---

## 実装した機能

### Day 21: SIWE 認証基盤

- `SiweNonce` — ワンタイムノンスの JPA エンティティ（`used` フラグで二重利用防止）
- `SiweNonceRepository` — JPA リポジトリ（`deleteExpiredBefore` で期限切れノンスを削除）
- `SiweService` — EIP-4361 メッセージ解析・EIP-191 署名検証・JWT 発行
- `JwtService` — jjwt 0.12.x による HS256 JWT 生成・検証（`SecretKey` をコンストラクタでキャッシュ）
- `JwtAuthFilter` — `OncePerRequestFilter` で `Authorization: Bearer` を検証
- `SecurityConfig` — CSRF 無効・ステートレスセッション・エンドポイント別認証ポリシー
- `SiweController` — `POST /api/v1/auth/nonce`, `POST /api/v1/auth/verify`
- `NonceCleanupJob` — 60秒ごとに期限切れノンスを削除
- `V3__siwe_nonces.sql` — Flyway マイグレーション

---

## 3エージェントレビューで発覚した問題と対応

### 初回レビュー（🔴 6件、全修正済み）

| エージェント | 🔴 指摘 | 修正内容 |
|---|---|---|
| Blockchain | メッセージ本文2行目のアドレスが未検証 | `extractMessageAddress()` 追加、`recoveredAddress` から JWT を発行 |
| Blockchain | `v` の signed byte バグ（`& 0xFF` が必要） | `int vInt = sigBytes[64] & 0xFF` に変更、27/28 以外を拒否 |
| Spring | `catch (Exception ignored)` でスタックトレース消失 | `ExpiredJwtException` / `JwtException` / `Exception` を分けてログ出力 |
| Spring | `@Modifying deleteExpiredBefore` に `@Transactional` なし | `@Transactional` を追加 |
| Security | メッセージ長チェックなし | `MAX_MESSAGE_LENGTH = 4096` を `verify()` 冒頭でチェック |
| Security | `NonceCleanupJob` がなく DB 膨張リスク | `NonceCleanupJob` を新規作成 |

### 再レビュー（全 🔴 → 🟢 を確認後、追加で🟡を対応）

| エージェント | 🟡 指摘 | 対応 |
|---|---|---|
| Blockchain | `extractField` の first-match で重複フィールドを許可 | 重複フィールド検出を追加（`Duplicate field` 例外） |
| Spring | `UserDetailsServiceAutoConfiguration` が不要に起動 | `@SpringBootApplication(exclude = ...)` で除外 |
| Spring | `SecretKey` を毎回生成 | コンストラクタで一度だけ生成しキャッシュ |
| Security | エラーメッセージ詳細がログインジェクションの温床 | `GlobalExceptionHandler` で統一（`"Authentication failed"`）、ログは WARN |
| Security | CRLF 改行への非対応 | `verify()` 冒頭で `\r\n` → `\n` に正規化 |

---

## 当初設計から変更した点

### JWT の subject を `address` → `recoveredAddress` に変更

**当初:** `jwtService.generate(address.toLowerCase())` — クライアント申告アドレス  
**変更後:** `jwtService.generate(recoveredAddress.toLowerCase())` — 署名検証済みアドレス

**理由:** Blockchain レビュー🔴。クライアントがメッセージ本文に任意のアドレスを書いても、JWT subject は暗号学的に検証されたアドレスであることを保証する必要がある。

### `@Transactional` のインポートを jakarta → Spring に変更（推奨事項）

`jakarta.transaction.Transactional` でも動作するが、Spring の拡張属性（`propagation`, `readOnly`）が使えないため、`org.springframework.transaction.annotation.Transactional` を使うことが Spring Boot の標準。今後新規作成するクラスは Spring 版を使う。

---

## 妥協点・既知の制限事項

- **ノンス競合状態:** `findById` → `isUsed` チェック → `save` の間に並列リクエストが来た場合、デフォルトの `READ_COMMITTED` 分離レベルでは二重認証の可能性がある。本番化時は `@Lock(LockModeType.PESSIMISTIC_WRITE)` または `@Version`（楽観ロック）を追加する。
- **`POST /nonce` にレート制限なし:** 認証不要エンドポイントのため大量リクエストで DB にノンスを積み上げられる。本番化時は Nginx の `limit_req_zone` または Bucket4j で対応。
- **ログインジェクションの可能性:** `SiweException` メッセージに攻撃者制御の値（version, v 値）が含まれ、WARN ログに出力される。本番化前に静的メッセージに変更する。
- **削除件数の未ログ:** `NonceCleanupJob` の削除件数がログ出力されない。`deleteExpiredBefore` の戻り値を `int` に変えて記録するとオペレーション性が上がる。
- **`Expiration Time` フィールドの未検証:** EIP-4361 の任意フィールド `Expiration Time` をメッセージに埋め込んだ場合、サーバーは無視する。ノンスの TTL で等価な保護はできているが、厳密な EIP-4361 準拠には `Expiration Time` の解析と期限チェックが必要。

---

## セキュリティ設計のメモ

### SIWE 署名検証フロー

```
1. メッセージ長チェック（4096バイト上限）
2. CRLF 正規化（\r\n → \n）
3. ETH_ADDRESS_PATTERN でリクエストアドレス形式検証
4. メッセージ本文からフィールド抽出（重複フィールドを拒否）
5. ドメイン・ChainID・Version 検証
6. メッセージ本文2行目のアドレスとリクエストアドレスを照合
7. ノンスの有効性・使用済みチェック
8. Sign.signedPrefixedMessageToKey() で署名から公開鍵を復元
9. 復元されたアドレスとリクエストアドレスを照合
10. ノンスを used=true に更新
11. recoveredAddress.toLowerCase() を subject に JWT 発行
```

### エンドポイント認証ポリシー

| エンドポイント | ポリシー |
|---|---|
| `/api/v1/auth/**` | permitAll |
| `/api/v1/chain/**` | permitAll（学習用） |
| `/actuator/health`, `/actuator/info` | permitAll |
| `/swagger-ui/**`, `/v3/api-docs/**` | permitAll |
| `/api/v1/payments/**` | authenticated（JWT 必須） |
