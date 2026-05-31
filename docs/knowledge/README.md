# ナレッジベース：学びと気づきの記録

実装・レビュー・議論の中で得た「やってみてわかったこと」を記録する場所。

## 記録するタイミング

- **気づいた瞬間**：重要な発見・ミスの検出・判断のタイミングで随時記録
- **1日の終わり**：その日の振り返り（よかったこと・改善点・注意点）を追記

## ファイル構成

| ファイル | 内容 |
|---|---|
| `_template.md` | 新規作成時のテンプレート |
| `day-01-multi-agent-review.md` | マルチエージェントレビューの実際の価値（Phase 0）|
| `day-02-phase2-phase3-learnings.md` | マルチチェーンWeb3j・eth_call設計・JPYC新アドレス（Phase 2・3）|
| `day-03-frontend-payment-design.md` | DApp/WalletConnect/CPM/MPM・レビュー漏れ反省（Phase 3 完了後）|
| `day-04-pm-pmo-review.md` | PM/PMO レビュー導入・Phase 4 前の計画整理（テスト環境・依存順序）|
| `day-05-phase4-implementation.md` | Phase 4 実装の発見（Web3j raw型・eth_getLogsエラー設計・Flyway・JPA @Data）|
| `day-06-hardhat-fork-testing.md` | Hardhat fork テストの概念整理（Alchemy・ノードプロバイダー・フォーク動作原理）|
| `gas-cost-reference.md` | **ガス代リファレンス**：誰が・何に・いくら（フェーズ別・操作別）|
| `smartcontract-standards.md` | SIWE・EIP-712・Permit・Sablier・EIP-7702 規格リファレンス |

## 振り返りの3軸

毎日の終わりに以下の3軸で整理する：

| 軸 | 観点 |
|---|---|
| ✅ よかったこと | うまくいったこと・想定より良かったこと |
| 🔧 改善が必要なこと | 次回は違うやり方をすべきこと |
| ⚠️ 注意するべきこと | 見落としやすい・再発しやすいリスク |

## ドキュメント体系

```
docs/
├── PLAN.md              ← 開発計画（何をするか）
├── report-*.md          ← 技術レポート（Ethereumの仕組みなど）
│   ├── report-11-why-ethereum.md      ← なぜEthereumか：全チェーン徹底比較
│   ├── report-12-why-blockchain.md    ← なぜブロックチェーンが必要か・Web3とは
│   ├── report-13-stablecoin-future.md ← ステーブルコインの将来性・規制・CBDC
│   ├── report-14-crypto-regulations-timeline.md ← 仮想通貨規制の世界史（日本・米・EU・アジア）
│   └── report-15-meme-coins.md        ← ミームコインはなぜ流行ったのか（DOGE・SHIB・PEPE）
└── knowledge/
    ├── README.md        ← このファイル
    ├── _template.md     ← テンプレート
    └── day-XX-*.md      ← 日ごとの気づき・振り返り
```
