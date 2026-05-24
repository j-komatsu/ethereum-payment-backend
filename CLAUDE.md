# CLAUDE.md — プロジェクト規約・AI向け恒久指示

## セキュリティ絶対ルール

以下は例外なく守ること。違反しそうな場合は作業を止めてユーザーに確認する。

### リポジトリに絶対コミットしてはいけないもの

| 禁止対象 | 例 |
|---|---|
| 秘密鍵（Private Key） | `0xabc123...`（64文字の16進数） |
| ニーモニックフレーズ | `abandon ability able about...` |
| Infura / Alchemy API Key | `abc123def456...` |
| DBパスワード | `password=secret` |
| JWT シークレット | `jwt.secret=...` |
| 実際の `.env` ファイル | `.env`（`.env.example` はOK） |

**チェック方法:** コミット前に `git diff --staged` を確認し、上記パターンが含まれていないことを必ず確認する。

### 本番設定（application.yml）に含めてはいけないもの

- `spring.h2.console.enabled: true` → `application-dev.yml` にのみ許可
- `management.endpoint.health.show-details: always` → `when-authorized` を使用
- `spring.jpa.hibernate.ddl-auto: update` または `create` → `validate` または `none` を使用
- デバッグログレベル（`logging.level: DEBUG`）→ `application-dev.yml` にのみ許可

### Ethereum セキュリティルール

- 秘密鍵をサーバーサイドのコードに埋め込まない
- Ethereum アドレスは必ず形式検証（`^0x[0-9a-fA-F]{40}$`）を行う
- コントラクトアドレス変更時は必ずレビューエージェントに確認させる
- txHash は `^0x[0-9a-fA-F]{64}$` 形式のみ受け付ける

---

## PR レビュールール

PR 作成時は必ず以下の3エージェントを並列起動してレビューを実施する：

1. **ブロックチェーン/Web3j エージェント** — アドレス正確性・decimals・イベント・confirmations
2. **Spring Boot/Java エージェント** — JPA設計・トランザクション・仮想スレッド・パフォーマンス
3. **セキュリティエージェント** — 秘密情報漏洩・バリデーション・認証・本番設定

セキュリティエージェントの🔴指摘は**マージ前に必ず修正**する。

---

## 開発フロー

```
新しい作業 → 専用ブランチ作成 → コミット・プッシュ → PR作成
→ 3エージェント並列レビュー → 🔴修正 → ユーザー確認 → マージ
```

### ブランチ命名規則

| 種別 | 形式 | 例 |
|---|---|---|
| 機能追加 | `feat/<内容>` | `feat/transfer-event-poller` |
| バグ修正 | `fix/<内容>` | `fix/jpyc-address` |
| セキュリティ対応 | `security/<内容>` | `security/address-validation` |
| ドキュメント | `docs/<内容>` | `docs/report-01-ethereum` |
| 設定・環境 | `chore/<内容>` | `chore/h2-profile-separation` |

### コミットメッセージ規則

```
feat:     新機能追加
fix:      バグ修正
security: セキュリティ対応
refactor: 動作を変えないコード整理
test:     テスト追加・修正
docs:     レポート・README・ドキュメント
chore:    設定ファイル・依存関係
```

---

## 開発計画

詳細は [`docs/PLAN.md`](docs/PLAN.md) を参照。7フェーズ35日分のタスクとレポートテーマを記載。

## 言語ルール

- コード: 英語（クラス名・メソッド名・変数名）
- ドキュメント・レポート: 日本語
- コミットメッセージ: 日本語（プレフィックスは英語）
- PR タイトル・本文: 日本語
