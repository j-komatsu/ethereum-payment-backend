# 学習ガイド — あなたのレベルはどれ？

ブロックチェーン・Ethereum・RPC・バリデーターを段階的に学べるガイドです。
自分のレベルを選んで、対応するフォルダから読み始めてください。

---

## 🌱 初心者（ブロックチェーンが初めて）

「ブロックチェーンって聞いたことあるけどよく分からない」  
「RPCって何？Alchemyって何？バリデーターって何？」という方はここから。

**読む順番（この順番で読んでください）:**

| Step | ファイル | 内容 | 読了時間 |
|------|----------|------|---------|
| ① | [beginner/01-blockchain-world.md](beginner/01-blockchain-world.md) | ブロックチェーン・Ethereum・ウォレットを例え話で理解 | 約10分 |
| ② | [beginner/02-rpc-and-alchemy.md](beginner/02-rpc-and-alchemy.md) | RPCサーバーとAlchemyが何か分かる | 約10分 |
| ③ | [beginner/03-validators.md](beginner/03-validators.md) | バリデーターの役割が分かる | 約8分 |

初心者ガイドを読み終えたら → [なぜブロックチェーンが必要か](../report-12-why-blockchain.md) へ、または中級者ガイドへ進む

---

## 🌿 中級者（仕組みは分かるが詳細が知りたい）

「技術的にどう動いているか知りたい」「Alchemyと自前ノードの違いは？」という方。

| Step | ファイル | 内容 | 読了時間 |
|------|----------|------|---------|
| ① | [intermediate/01-json-rpc-mechanics.md](intermediate/01-json-rpc-mechanics.md) | JSON-RPCプロトコルの詳細・メソッド一覧 | 約15分 |
| ② | [intermediate/02-rpc-provider-comparison.md](intermediate/02-rpc-provider-comparison.md) | Alchemy vs Infura vs 自前ノード徹底比較 | 約12分 |
| ③ | [intermediate/03-pos-and-finality.md](intermediate/03-pos-and-finality.md) | PoS・バリデーター選出・ファイナリティの技術詳細 | 約15分 |

中級者ガイドを読み終えたら → [advanced/README.md](advanced/README.md)（技術レポート群のナビ）へ

---

## 🔧 技術者・実装者（実装・設計レベルで理解したい）

既存の技術レポートが対応します。

→ **[advanced/README.md](advanced/README.md)** — 全技術レポートへのナビゲーション

---

## 🏠 個人開発者（0円で開発を始めたい）

「無料枠でどこまでできる？」「テストネットの使い方は？」という方。

→ **[use-cases/personal-dev.md](use-cases/personal-dev.md)**

---

## 🏢 法人・エンタープライズ（ビジネス導入を検討）

「法人でAlchemyを使っていいの？」「SLAは？コストは？」という方。

→ **[use-cases/enterprise.md](use-cases/enterprise.md)**

---

## 全体のテクノロジーマップ

```
インターネット
    ↓
あなたのアプリ (Spring Boot)
    ↓ HTTP / JSON-RPC          ← beginner-02 / intermediate-01
    ↓
RPCプロバイダー                ← intermediate-02 / use-cases/
 Alchemy / Infura / 自前ノード
    ↓
Ethereumネットワーク           ← beginner-01
    │
    ├── バリデーター群          ← beginner-03 / intermediate-03
    │    （PoSでブロックを承認・記録）
    │
    ├── スマートコントラクト    ← report-05
    │    JPYC (ERC-20) / Sablier Flow
    │
    └── ブロック・イベントログ  ← report-04
         Transfer Event → 入金検知
```
