# docs — ドキュメントインデックス

> このプロジェクトのすべてのドキュメントを一覧します。
> 新規セッションで作業を再開する場合は **PLAN.md → implementation-notes → knowledge** の順に読むと文脈を素早く復元できます。

---

## 🗺️ 学習ガイド（guides）

ブロックチェーン・RPC・バリデーターを段階的に学べるガイドです。  
**初めての方はここから →** [guides/README.md](guides/README.md)

| レベル | フォルダ | 内容 |
|---|---|---|
| 🌱 初心者 | [guides/beginner/](guides/beginner/) | 例え話でブロックチェーン・RPC・バリデーターを理解 |
| 🌿 中級者 | [guides/intermediate/](guides/intermediate/) | JSON-RPC詳細・プロバイダー比較・PoS技術詳細 |
| 🔧 技術者 | [guides/advanced/](guides/advanced/) | 技術レポート群へのナビゲーション |
| 🏠 個人開発者 | [guides/use-cases/personal-dev.md](guides/use-cases/personal-dev.md) | Alchemy無料枠・テストネット・コスト0円開発 |
| 🏢 法人 | [guides/use-cases/enterprise.md](guides/use-cases/enterprise.md) | SLA・セキュリティ・コスト試算・意思決定フロー |

---

## 開発計画

| ファイル | 内容 |
|---|---|
| [PLAN.md](PLAN.md) | 全7フェーズの開発ロードマップ・タスク一覧・レポートテーマ |

---

## 学習レポート（reports）

フェーズ進行に合わせて書いた技術・概念の深掘りレポートです。

| # | ファイル | タイトル |
|---|---|---|
| 01 | [report-01-ethereum-basics.md](report-01-ethereum-basics.md) | Ethereum 基礎 |
| 02 | [report-02-accounts-and-web3j.md](report-02-accounts-and-web3j.md) | Ethereum アカウントと Web3j 接続 |
| 03 | [report-03-erc20-stablecoins.md](report-03-erc20-stablecoins.md) | ERC-20 規格とステーブルコイン |
| 04 | [report-04-events-and-monitoring.md](report-04-events-and-monitoring.md) | Ethereum イベント監視 — Logs・Transfer・ポーリング・ファイナリティ |
| 05 | [report-05-wallet-auth-and-smartcontract.md](report-05-wallet-auth-and-smartcontract.md) | ウォレット認証とスマートコントラクト決済 |
| 05F | [report-05F-frontend.md](report-05F-frontend.md) | フロントエンド・送金UI設計 |
| 06 | [report-06-stablecoins.md](report-06-stablecoins.md) | ステーブルコイン：設計思想・リスク・実装との関係 |
| 07 | [report-07-defi-mechanisms.md](report-07-defi-mechanisms.md) | DeFi の仕組み：AMM・Uniswap・流動性プール |
| 08 | [report-08-sablier-streaming.md](report-08-sablier-streaming.md) | Sablier ストリーミング決済：時間とともに流れるお金 |
| 09 | [report-09-testing-strategy.md](report-09-testing-strategy.md) | ブロックチェーンアプリのテスト戦略 |
| 10 | [report-10-erc20-and-gas.md](report-10-erc20-and-gas.md) | ERC-20 標準とガスコストの詳解 |
| 11 | [report-11-why-ethereum.md](report-11-why-ethereum.md) | なぜ Ethereum を選ぶのか：全チェーン徹底比較 |
| 12 | [report-12-why-blockchain.md](report-12-why-blockchain.md) | なぜブロックチェーンが必要か：既存システムとの本質的な違い |
| 13 | [report-13-stablecoin-future.md](report-13-stablecoin-future.md) | ステーブルコインの将来性：市場・規制・技術の交差点 |
| 14 | [report-14-crypto-regulations-timeline.md](report-14-crypto-regulations-timeline.md) | 仮想通貨規制の世界史：日本・米国・EU・アジアの時系列 |
| 15 | [report-15-meme-coins.md](report-15-meme-coins.md) | ミームコインはなぜ流行ったのか：Doge・SHIB・PEPE の社会心理学 |
| 16 | [report-16-layer2-complete-guide.md](report-16-layer2-complete-guide.md) | Layer 2 完全解説：なぜ Ethereum は遅いのか、Rollup・Polygon の全貌 |
| 17 | [report-17-web3-payment-vs-traditional.md](report-17-web3-payment-vs-traditional.md) | Web3 決済 vs 従来決済：Stripe・銀行振込との本質的な違い |
| 18 | [report-18-account-abstraction.md](report-18-account-abstraction.md) | アカウント抽象化と EIP-4337：Web3 UX 革命の全貌 |
| 19 | [report-19-smart-contract-security.md](report-19-smart-contract-security.md) | スマートコントラクトセキュリティ：The DAO ハックから学ぶ |
| 20 | [report-20-ethereum-roadmap.md](report-20-ethereum-roadmap.md) | Ethereum ロードマップ：PoS 移行から Danksharding まで |
| 21 | [report-21-docusaurus-rich-docs.md](report-21-docusaurus-rich-docs.md) | リッチなドキュメントサイト：Docusaurus 導入ガイド |

---

## 実装ノート（implementation-notes）

実装中の判断・設計変更・妥協点・却下した選択肢を記録しています。

| ファイル | 内容 |
|---|---|
| [phase0-initial-setup.md](implementation-notes/phase0-initial-setup.md) | 初期構成・セキュリティ強化 |
| [phase1-environment.md](implementation-notes/phase1-environment.md) | 環境整備（Java 21・Spring Boot・Gradle） |
| [phase2-web3j-chain-api.md](implementation-notes/phase2-web3j-chain-api.md) | Web3j 接続 & アカウント/残高 API |
| [phase3-erc20-token-balance.md](implementation-notes/phase3-erc20-token-balance.md) | ERC-20 トークン残高 API |
| [phase4-pre-cpm-design.md](implementation-notes/phase4-pre-cpm-design.md) | Phase 4 着手前 CPM 事前設計変更 |
| [phase4-transfer-event-poller.md](implementation-notes/phase4-transfer-event-poller.md) | Transfer イベント監視・入金検知・期限切れジョブ |
| [phase5-siwe-auth.md](implementation-notes/phase5-siwe-auth.md) | SIWE 認証・JWT 発行・Spring Security |
| [phase5-permit-payment.md](implementation-notes/phase5-permit-payment.md) | EIP-2612 Permit + transferFrom 決済 |
| [phase5-6-smartcontract-planning.md](implementation-notes/phase5-6-smartcontract-planning.md) | Phase 5・6 スマコン計画更新 |
| [_template.md](implementation-notes/_template.md) | 新規ノート作成用テンプレート |

---

## ナレッジ（knowledge）

セッションごとの気づき・発見・失敗・セキュリティ知見です。

| ファイル | 内容 |
|---|---|
| [day-01-multi-agent-review.md](knowledge/day-01-multi-agent-review.md) | マルチエージェントレビューの実際の価値 |
| [day-02-phase2-phase3-learnings.md](knowledge/day-02-phase2-phase3-learnings.md) | Phase 2・3 の実装から得た知見 |
| [day-03-frontend-payment-design.md](knowledge/day-03-frontend-payment-design.md) | フロントエンド設計・決済方式・レビュー漏れ |
| [day-04-pm-pmo-review.md](knowledge/day-04-pm-pmo-review.md) | PM/PMO レビュー導入・Phase 3 完了後の計画整理 |
| [day-05-phase4-implementation.md](knowledge/day-05-phase4-implementation.md) | Phase 4 実装で得た知識・判断・発見 |
| [day-06-hardhat-fork-testing.md](knowledge/day-06-hardhat-fork-testing.md) | Hardhat fork テストの概念整理 |
| [gas-cost-reference.md](knowledge/gas-cost-reference.md) | ガス代リファレンス：誰が・何に・いくら払うか |
| [smartcontract-standards.md](knowledge/smartcontract-standards.md) | スマートコントラクト規格ナレッジ（ERC-20・EIP-2612・EIP-712・SIWE） |
| [README.md](knowledge/README.md) | knowledge ディレクトリ内のファイル構成表 |
| [_template.md](knowledge/_template.md) | 新規ナレッジ作成用テンプレート |

---

## ディレクトリ構成

```
docs/
├── README.md                          ← このファイル（インデックス）
├── PLAN.md                            ← 開発ロードマップ
├── guides/                            ← 学習ガイド（レベル別）
│   ├── README.md                      ← ナビゲーション（どこから読むか）
│   ├── beginner/                      ← 🌱 初心者向け（例え話中心）
│   │   ├── 01-blockchain-world.md
│   │   ├── 02-rpc-and-alchemy.md
│   │   └── 03-validators.md
│   ├── intermediate/                  ← 🌿 中級者向け（技術詳細）
│   │   ├── 01-json-rpc-mechanics.md
│   │   ├── 02-rpc-provider-comparison.md
│   │   └── 03-pos-and-finality.md
│   ├── advanced/                      ← 🔧 技術者向け（実装レベル）
│   │   └── README.md
│   └── use-cases/                     ← 🏠🏢 用途別ガイド
│       ├── personal-dev.md
│       └── enterprise.md
├── report-01-ethereum-basics.md
├── report-02-accounts-and-web3j.md
├── report-03-erc20-stablecoins.md
├── report-04-events-and-monitoring.md
├── report-05-wallet-auth-and-smartcontract.md
├── report-05F-frontend.md
├── report-06-stablecoins.md
├── report-07-defi-mechanisms.md
├── report-08-sablier-streaming.md
├── report-09-testing-strategy.md
├── report-10-erc20-and-gas.md
├── report-11-why-ethereum.md
├── report-12-why-blockchain.md
├── report-13-stablecoin-future.md
├── report-14-crypto-regulations-timeline.md
├── report-15-meme-coins.md
├── report-16-layer2-complete-guide.md
├── report-17-web3-payment-vs-traditional.md
├── report-18-account-abstraction.md
├── report-19-smart-contract-security.md
├── report-20-ethereum-roadmap.md
├── report-21-docusaurus-rich-docs.md
├── implementation-notes/
│   ├── _template.md
│   ├── phase0-initial-setup.md
│   ├── phase1-environment.md
│   ├── phase2-web3j-chain-api.md
│   ├── phase3-erc20-token-balance.md
│   ├── phase4-pre-cpm-design.md
│   ├── phase4-transfer-event-poller.md
│   ├── phase5-6-smartcontract-planning.md
│   ├── phase5-permit-payment.md
│   └── phase5-siwe-auth.md
└── knowledge/
    ├── README.md
    ├── _template.md
    ├── day-01-multi-agent-review.md
    ├── day-02-phase2-phase3-learnings.md
    ├── day-03-frontend-payment-design.md
    ├── day-04-pm-pmo-review.md
    ├── day-05-phase4-implementation.md
    ├── day-06-hardhat-fork-testing.md
    ├── gas-cost-reference.md
    └── smartcontract-standards.md
```
