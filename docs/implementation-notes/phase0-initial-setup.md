# 実装ノート：Phase 0 — 初期構成・セキュリティ強化

**日付:** 2026-05-24
**対象ブランチ / PR:** PR #1, #2, #3, #4
**対応する計画:** `docs/PLAN.md` Phase 0

---

## 実装サマリー

Spring Boot 3.2 + Web3j 4.10 の初期構成を作成し、USDC/USDT/DAI/JPYC に対応した
`StablecoinType` と `PaymentOrder` エンティティ、REST API、セキュリティ設定を実装した。

---

## 意思決定ログ

### 判断：DBをH2ファイルモードにした

**状況:** 開発開始時にDBの選択が必要だった
**選択肢:**
- A案：PostgreSQL（本番想定）
- B案：H2 in-memory（簡単だが再起動でデータ消える）
- C案：H2 ファイルモード（無料・永続化・組み込み）

**決定:** C案（H2ファイルモード）
**理由:** 完全無料・Dockerなし・組み込みで起動が簡単。`./data/paymentdb.mv.db` として永続化できるため開発中に支障なし。将来は Neon（無料PostgreSQL）に移行予定。

---

### 判断：Java 21 仮想スレッドを最初から有効化した

**状況:** Web3j の I/O 待ちが多いため並行処理の設計が必要だった
**選択肢:**
- A案：Spring WebFlux（Reactive）
- B案：Java 21 仮想スレッド（`spring.threads.virtual.enabled: true`）

**決定:** B案（仮想スレッド）
**理由:** WebFlux は学習コストが高く、Web3j との統合も複雑になる。仮想スレッドは従来の命令型コードのまま I/O バウンドの性能を得られるため、このプロジェクトの規模に適している。

---

### 判断：エラーレスポンスに RFC 9457 ProblemDetail を採用した

**状況:** REST API のエラー形式を決める必要があった
**選択肢:**
- A案：独自エラーレスポンスクラスを作る
- B案：Spring Boot 3.x 標準の `ProblemDetail`（RFC 9457）

**決定:** B案（ProblemDetail）
**理由:** Spring Boot 3.x が標準サポート。追加実装不要で、国際標準に準拠したエラー形式になる。

---

### 変更：`application.yml` の本番設定を大幅に変えた

**当初の設計:** 単一の `application.yml` に全設定を記述
**変更後:** `application.yml`（本番）+ `application-dev.yml`（開発専用）に分離
**変更した理由:** セキュリティレビューで以下の危険設定が指摘された
- `h2.console.enabled: true`（認証なしDBアクセス）
- `ddl-auto: update`（本番スキーマ破壊リスク）
- `show-details: always`（DB情報・JVM状態の漏洩）
- `WEB3J_CLIENT_ADDRESS` にデフォルト値（本番でテストネット誤接続）

---

### 変更：`StablecoinType.JPYC` のアドレスを修正した

**当初の設計:** `0x431D5dfF03120AFA4bDf332c61A6e1766eF37BF9`
**変更後:** `0x431D5dfF03120AFA4bDf332c61A6e1766eF37BDB`
**変更した理由:** ブロックチェーン専門レビューエージェントが Etherscan 照合で末尾3文字の誤り（`BF9`→`BDB`）を検出。1文字でも違えば資金喪失につながる。

---

### 妥協点：Spring Security は今回導入しなかった

**本来やるべきこと:** API 認証（APIキー認証 or OAuth2）の実装
**今回の対応:** 未実装のまま
**妥協した理由:** 初期構成フェーズであり、認証の設計方針をまだ決めていない。`health show-details: when-authorized` を設定したが、Security 未導入のため実質 `never` と同等になることをレビューで指摘された。
**今後の対応時期:** Phase 5〜6

---

### 妥協点：EIP-55 チェックサム検証は @Pattern 止まりにした

**本来やるべきこと:** `WalletUtils.isValidAddress()` による EIP-55 チェックサム検証
**今回の対応:** `@Pattern(regexp = "^0x[0-9a-fA-F]{40}$")` による形式チェックのみ
**妥協した理由:** 形式チェックは即時追加できるが、チェックサム検証は Web3j の Bean 注入が必要でコントローラー層の設計を変える必要がある。
**今後の対応時期:** Phase 2（Web3j 接続実装時に合わせて追加）

---

### 発見：OkHttp が Infura URL をログに出力するリスク

**発見した内容:** `new HttpService(clientAddress)` のみだと OkHttp が起動時に接続先 URL をログに出力する可能性がある。URL に Infura API Key が含まれるため間接的な漏洩リスクがある
**対応:** `HttpLoggingInterceptor.Level.NONE` を明示設定し、`okhttp3: ERROR` をログレベルに追加

---

## 却下した選択肢

| 選択肢 | 却下した理由 |
|---|---|
| PostgreSQL（初期から） | 無料枠の制約・ローカルセットアップのコスト |
| WebFlux（Reactive） | 学習コストが高く Web3j との統合が複雑 |
| 独自エラーレスポンスクラス | Spring Boot 3.x 標準の ProblemDetail で十分 |
| 業務知識専門レビューエージェント | 現段階では業務ロジックが単純すぎて価値が薄い |
| ddl-auto: validate（最初から） | 初期構成フェーズでは update の方が開発しやすいため dev 限定で残した |

---

## 次のセッションへの引き継ぎ事項

- Phase 1 Day 1：`application-dev.yml` の動作確認（`./data/paymentdb-dev.mv.db` が生成されるか）
- Phase 1 Day 3：Infura アカウント取得・Sepolia 接続確認
- Phase 2 実装時：`receiverAddress` に EIP-55 チェックサム検証を追加する
- Phase 5〜6：Spring Security によるAPI認証の実装
- `ddl-auto: validate` で起動するためには Flyway / Liquibase の導入が前提（Phase 7）
