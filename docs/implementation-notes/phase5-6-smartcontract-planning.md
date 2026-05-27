# 実装ノート: Phase 5・6 スマコン計画更新

> 作業日: 2026-05-24
> ブランチ: docs/plan-update-phase5-6-smartcontract

---

## 背景

ユーザーより「JPYCを優先したい、HashPort Walletを使いたい、認証→事前承認→自動購入の流れを実装したい」という要望があった。
また「スマコンのルールを自由に追加できるか、既存の仕組みはどんなものがあるか」という質問があり、調査した。

---

## 判断・変更

### Phase 5 を全面刷新した

**変更前:** Webhook・サマリーAPI・メトリクスが中心  
**変更後:** SIWE認証 → EIP-2612 Permit → approve/transferFrom 即時決済

**理由:** ユーザーの要望（ウォレット認証 + スマコン事前承認決済）を反映するため。
Webhook と サマリーAPIは Day 24 に残した（引き続き必要な機能のため）。

### Phase 6 を全面刷新した

**変更前:** テスト基盤整備のみ  
**変更後:** Sablier Flow ストリーミング決済 → カスタム自動購入コントラクト → テスト → OpenAPI

**理由:** 「自動購入・サブスク」の要望に対応するため。

---

## 技術選択

### SIWE（EIP-4361）を採用

**検討した選択肢:**
- OAuth/JWT（従来型）→ パスワード管理が必要、Web3との親和性が低い
- SIWE → パスワード不要、ウォレット所有者であることを証明できる

**採用理由:** HashPort Wallet が EIP-4361 に準拠しているため即利用可能。

### EIP-2612 Permit を採用

**理由:** JPYC（JPYCv2）がすでに EIP-2612 対応済みのため、追加コントラクトなしで即利用可能。
ユーザーが ETH を持っていなくても承認できる点も重要。

### Sablier Flow を採用

**検討した選択肢:**
- カスタムコントラクト作成 → 開発・監査コストが高い
- Sablier Flow → 実績のあるプロトコル、JPYC（ERC-20）に対応済み

**採用理由:** 運用実績があり、セキュリティリスクが低い。ERC-20準拠なのでJPYCと相性が良い。

---

## 発見した制約・注意事項

### サーバー秘密鍵の管理が必要になる

Phase 5 Day 23（approve/transferFrom 即時決済）では、バックエンドが `transferFrom` を呼ぶために
**サーバー側で秘密鍵を保持する必要がある**。

これは重大なセキュリティ責任を伴う。対策:
- 環境変数で管理（`.env` は Git に上げない）
- 本番では HashiCorp Vault や クラウドの Secret Manager を推奨
- Phase 5 実装時に必ずセキュリティエージェントレビューを受ける

### EIP-4337 Paymaster は将来対応

EIP-4337（Account Abstraction / Paymaster）の完全実装は Phase 7 以降に延期。
理由:
- Bundler の運用が必要（インフラコストが発生する）
- HashPort の EIP-7702 実装で部分的なガスレスは実現できる
- Phase 5-6 の範囲では複雑すぎる

### Spring Security は Phase 5 で追加必須

SIWE 認証を実装する Day 21 で Spring Security の導入が必要になる。
Phase 0 では「後回し」にしていたが、Phase 5 では必須。

---

## 却下した選択肢

### WalletConnect を採用しない（当面）

WalletConnect は海外企業の製品。日本市場への訴求として HashPort を優先する。
将来的に必要なら追加できる設計にしておく。

### カスタム ERC-20 コントラクトは作成しない

`approve → transferFrom` の既存パターンと Sablier で十分。
独自コントラクトは監査コストが高く、Phase 5-6 の範囲では不要。

---

## PLAN.md 以外の変更点

- `docs/knowledge/smartcontract-standards.md` 新規作成（スマコン規格ナレッジ）
- PLAN.md のタイムライン表を修正（Phase 5-6 の説明が古いままだったため）
- PLAN.md の Phase 0 成果物表で `pom.xml` → `build.gradle.kts` に修正
- PLAN.md の Day 9 で `pom.xml` への依存追記例 → `build.gradle.kts` に修正
- PLAN.md のファイル構成で `pom.xml` → `build.gradle.kts` / `settings.gradle.kts` に修正
- PLAN.md のレポートファイル名を実際のファイル名に合わせて修正
