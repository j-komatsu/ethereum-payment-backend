# 実装ノート: Phase 1 環境整備

> 作業日: 2026-05-25
> ブランチ: feat/phase1-ethereum-basics

---

## 背景

Phase 1 の計画は Day 1（H2ファイルモード）・Day 2（仮想スレッド+.env）・Day 3（接続確認スクリプト）・Day 4-5（レポート）。
しかし Day 1・Day 2 のタスクは Phase 0 のセキュリティ対応（PR #3）で先行実装済みだった。

---

## 判断

### Day 1・Day 2 は実装済みとして扱った

**理由:** Phase 0 のセキュリティレビューで「H2 ファイルモード（data/paymentdb-dev）」「仮想スレッド（spring.threads.virtual.enabled: true）」「.env.example」がすでに実装されていた。
重複実装は避け、Day 3 から着手した。

### 接続確認スクリプトにAPIキーをログ出力しない設計を採用

**理由:** `scripts/check-connection.sh` の接続確認では `WEB3J_CLIENT_ADDRESS`（Infura URL + APIキー）をそのままログに出力しない。
CLAUDE.md のセキュリティルール（「APIキーをリポジトリに上げない」）の精神を、スクリプトのログ出力にも適用した。

### README を全面リライト

**変更前の問題点:**
- Maven での起動手順が記載（Gradle 移行済みのため誤り）
- H2 in-memory と記載（ファイルモード移行済みのため誤り）
- JPYC が未記載（JPYC 優先方針のため追加必要）
- チェーン名が未記載（セキュリティレビューで指摘済み）

**変更後:** Gradle・H2ファイルモード・JPYC優先・Polygon mainnet 明記に統一。

### レポートの対象チェーンを Polygon として記述

JPYC は Polygon mainnet 上の ERC-20 コントラクト。
Ethereum（L1）の説明をしながら「JPYC は Polygon で動く」という点を明示した。

---

## 発見した事項

### テストネットの変遷

2023年以降、Ropsten・Rinkeby・Goerli は廃止または非推奨となり、**Sepolia が推奨テストネット**になっている。
PLAN.md の Day 3 手順（`https://sepolia.infura.io/v3/YOUR_KEY`）は正しい。

### eth_getLogs の制限

Phase 4 の Transfer イベント監視で `eth_getLogs` を使う予定だが、
Infura 無料枠では1回のリクエストで取得できるブロック範囲に制限がある（最大 10,000 ブロック程度）。
Phase 4 でポーリング設計を行う際に考慮が必要。

---

## 却下した選択肢

### check-connection.sh での jq 使用

`jq` コマンドを使えば JSON パースがより簡潔になるが、`jq` がインストールされていない環境での実行を考慮し、
`grep` + `sed` で実装した。ただし grep の正規表現が脆弱なため、本格的な JSON パースが必要な場合は jq 推奨。
