# Day 05 — Phase 4 実装で得た知識・判断・発見

**日付:** 2026-05-27  
**フェーズ:** Phase 4（Transfer イベント監視・入金検知・期限切れジョブ）

---

## 重要な発見

### 1. Web3j の `ethLog.getLogs()` は raw 型を返す

`EthLog.getLogs()` の戻り値は `List<LogResult>`（raw 型）であり、`List<LogResult<?>>` に代入しようとするとコンパイルエラーになる。

**解決策:** `var` を使って型推論させる。

```java
var logs = ethLog.getLogs();  // raw型のまま使う
for (var result : logs) {
    EthLog.LogObject logObj = (EthLog.LogObject) result.get();
}
```

### 2. `eth_getLogs` がエラーを返しても例外をスローしない設計は危険

最初の実装で `ethLog.hasError()` 時に `return` を使っていた。これにより `lastProcessedBlock` が正常に前進してしまい、エラー発生ブロック範囲のイベントが永久にスキップされるバグがあった。

**教訓:** ポーラーのエラーハンドリングは「例外を投げる → catch で最後の行のブロック前進をスキップ」という設計にすること。

### 3. JPA エンティティに `@Data` は使うな

Lombok の `@Data` は JPA エンティティと相性が悪い：
- `equals/hashCode` が全フィールドで生成されるため、Hibernate プロキシとの比較が壊れる
- 解決策は `@Getter` + `@Setter` に置き換え、`equals/hashCode` を `@Id` のみで**明示実装**すること（`@Getter/@Setter` だけでは `equals/hashCode` は生成されず Object のデフォルト実装になるため不十分）
- Phase 4 レビューで `PollerState` を修正、report-04 レビューで `PaymentOrder` も同様に修正済み

### 4. Flyway 導入のタイミングは「最初から」が理想

今回は Phase 4 で初めて Flyway を導入した。既存スキーマ（Phases 1-3 で Hibernate DDL auto が作ったもの）を V1 として表現する必要があり、バックフィルの作業が発生した。

**教訓:** 本番を見据えるならプロジェクト開始時から Flyway を入れておくべき。

### 5. `findFirst` に ORDER BY がないと非決定的な動作をする

Spring Data JPA の `findFirstByXxx` は ORDER BY なしでは返す行が不定。同一アドレス・トークンで複数の PENDING 注文が存在した場合、どの注文がマッチするか保証されない。

**解決策:** `findFirstByXxxOrderByCreatedAtAsc` とメソッド名に順序指定を含める。

---

## 判断の記録

### ポーリング間隔を 15 秒にした理由

Polygon ブロック時間（約2秒）に対して、15秒間隔では最大 7〜8 ブロック分溜まる。  
- 短すぎる（例：5秒）とノード API の呼び出し回数が増えレート制限に近づく  
- 長すぎる（例：60秒）と入金検知の遅延が大きくなる  
15秒は Infura/Alchemy Free Tier での日次リクエスト制限（3,000,000回）と入金検知の遅延のバランス点として選択。

### 20ブロック確認待ちの根拠

Polygon PoS で過去に数十ブロックの reorg が発生した事例がある。取引所は安全のため 128〜256 ブロックを要求するが、本 PoC（学習目的・少額決済）では 20ブロック（≈40秒）を採用。本番化時は金額に応じて増やす検討が必要。

### Flyway で `flyway-database-postgresql` を追加しなかった理由

Spring Boot 3.2.5 の dependency management が管理するバージョンでは `flyway-database-postgresql` が見つからずビルドエラーになった。`flyway-core` のみで PostgreSQL サポートが含まれていたため、その判断を維持した。

---

## 3エージェントレビューで発見された問題

今回の Phase 4 PR レビューで各エージェントが発見した主な指摘：

| エージェント | 🔴 指摘 | 結果 |
|---|---|---|
| Blockchain | `eth_getLogs` エラー時に `return` → `lastProcessedBlock` 誤前進 | 修正済み |
| Spring | `PollerState @Data` の JPA 非推奨設計 | 修正済み |
| Spring | `findFirstByStatus` の複数PENDING時の不定動作 | OrderByCreatedAtAsc を追加 |
| Spring | Flyway マイグレーション欠如 | V1/V2 スクリプト追加 |
| Security | `decodeAddress()` のアドレス形式検証なし（CLAUDE.md 絶対ルール違反） | Pattern 検証を追加 |

**学び:** セキュリティ絶対ルール（`^0x[0-9a-fA-F]{40}$` のアドレス検証）はブロックチェーンデータ（ノード応答）にも適用すること。「外部入力じゃないから安全」ではなく、防御的に検証を入れる。

---

## 次フェーズへの引き継ぎ事項

- Phase 5 では Spring Security を追加するため、既存エンドポイント（`/api/v1/payments`, `/api/v1/chain`）の公開/認証方針を決定してから着手すること（PLAN.md 参照）
- SIWE（Sign-In with Ethereum）認証の JWT シークレットは絶対にコードに含めないこと
