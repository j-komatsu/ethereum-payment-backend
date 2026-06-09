# レポート16 — Layer 2 完全解説：なぜ Ethereum は遅いのか、そして解決策

> 対象読者: Web3 初心者 / このプロジェクトで Polygon を使う理由を深く理解したい人

---

## 1. Ethereum のスケーラビリティ問題

### ブロックチェーン・トリレンマ

Vitalik Buterin が提唱した「ブロックチェーン・トリレンマ」は、以下の3つを同時に完全に実現することは難しいという概念です。

```
        分散性 (Decentralization)
             △
            / \
           /   \
          /     \
         /  選べるのは \
        /   2つだけ？  \
       ◯───────────────◯
  安全性           スケーラビリティ
 (Security)       (Scalability)
```

| チェーン | 分散性 | 安全性 | スケーラビリティ |
|---|---|---|---|
| Bitcoin | ◎ | ◎ | ✗（7 TPS） |
| Ethereum L1 | ◎ | ◎ | △（15-30 TPS） |
| Solana | △ | △ | ◎（65,000 TPS）|
| Polygon PoS | ○ | ○ | ◎（7,000 TPS）|
| Ethereum L2 | ◎（Lの安全性） | ◎ | ◎（目標） |

Ethereum は意図的に「分散性と安全性」を優先し、スケーラビリティを犠牲にした設計です。

### Ethereum L1 の実際のスループット

```
1ブロック ≈ 12秒
1ブロックのガス上限 ≈ 30,000,000 gas
ERC-20 Transfer ≈ 65,000 gas

→ 1秒あたりの Transfer 処理数 ≈ 30,000,000 ÷ 65,000 ÷ 12 ≈ 38 TPS
```

VisaのTPSが約24,000であることを考えると、Ethereum L1 だけでは到底グローバルな決済インフラになれません。

---

## 2. Layer 2 とは何か

Layer 2（L2）は「Ethereum L1 の上に乗っかった別の実行レイヤー」です。

```mermaid
graph TB
    subgraph L1["Ethereum L1（決済レイヤー）"]
        EC[Ethereum コンセンサス]
        ES[Ethereum セキュリティ]
    end
    subgraph L2["Layer 2（実行レイヤー）"]
        OP[Optimism]
        ARB[Arbitrum]
        ZKS[zkSync]
        BASE[Base]
    end
    subgraph SIDECHAIN["サイドチェーン（別チェーン）"]
        POL[Polygon PoS]
        BSC[BNB Chain]
    end
    L2 -->|状態をL1に記録| L1
    SIDECHAIN -.->|独立したセキュリティ| SIDECHAIN

    style L1 fill:#e8f4f8,stroke:#2196f3
    style L2 fill:#e8f5e9,stroke:#4caf50
    style SIDECHAIN fill:#fff3e0,stroke:#ff9800
```

**重要な違い:**
- **Layer 2**: Ethereum のセキュリティを **継承** する（状態をL1に記録）
- **サイドチェーン（Polygon PoS）**: 独自のバリデータセットを持つ **独立したチェーン**

> **このプロジェクトで使っている Polygon PoS は厳密には L2 ではなくサイドチェーンです。**
> セキュリティモデルが異なりますが、EVM 互換で安価なため JPYC の決済に適しています。

---

## 3. Optimistic Rollup の仕組み

Optimism・Arbitrum が採用する方式です。

```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Seq as Sequencer（L2）
    participant L1 as Ethereum L1

    User->>Seq: トランザクション送信
    Seq->>Seq: バッチ処理（数千TX）
    Seq->>L1: compressed batch を calldata に記録
    Note over L1: 7日間の挑戦期間
    L1->>L1: 不正証明なければ確定
```

**「楽観的」な理由**: 不正がないと楽観的に仮定してトランザクションを確定させ、不正があった場合にだけ証明で覆す設計。

| 項目 | 値 |
|---|---|
| ガス削減率 | 約10〜100倍安い |
| L1 最終確定まで | 7日間（挑戦期間） |
| EVM 互換性 | ほぼ完全互換 |
| 代表例 | Optimism, Arbitrum One |

**デメリット**: L2 → L1 への引き出し（ブリッジ）に7日かかる。高速ブリッジサービス（Hop, Across）で回避可能。

---

## 4. ZK Rollup の仕組み

zkSync・StarkNet・Polygon zkEVM が採用する方式です。

```mermaid
sequenceDiagram
    participant User as ユーザー
    participant Prover as Prover（ZK証明生成）
    participant L1 as Ethereum L1

    User->>Prover: トランザクション送信
    Prover->>Prover: バッチ処理
    Prover->>Prover: ZK証明（validity proof）生成
    Note over Prover: 数分〜数十分かかる
    Prover->>L1: batch + ZK証明を送信
    L1->>L1: 証明を検証（数秒）
    L1->>L1: 即座に最終確定 ✅
```

**「ゼロ知識」の意味**: 計算が正しいことを、計算内容を明かさずに証明できる暗号技術。

| 項目 | 値 |
|---|---|
| ガス削減率 | 約100〜1000倍安い（理論値） |
| L1 最終確定まで | 数分〜数時間（証明生成待ち） |
| EVM 互換性 | zkEVM で向上中（まだ制限あり） |
| 代表例 | zkSync Era, StarkNet, Polygon zkEVM |

**メリット**: 引き出しに7日待たなくていい。数学的な確実性がある。  
**デメリット**: ZK証明の生成が重い（計算コスト）。EVM 完全互換はまだ難しい。

---

## 5. Polygon PoS の特殊性

このプロジェクトで使っている Polygon PoS は、Rollup ではなく **プラズマ + PoS コンセンサスのサイドチェーン** です。

```mermaid
graph LR
    subgraph Ethereum["Ethereum L1"]
        RC[Root Contract]
        SB[State Checkpoints]
    end
    subgraph PolyPos["Polygon PoS"]
        V1[Validator 1]
        V2[Validator 2]
        V3[Validator ...]
        HB[Heimdall Layer\nPoS Consensus]
        BOR[Bor Layer\nBlock Production]
    end

    PolyPos -->|定期的にチェックポイント| Ethereum
    V1 & V2 & V3 --> HB
    HB --> BOR

    style Ethereum fill:#e8f4f8,stroke:#2196f3
    style PolyPos fill:#f3e5f5,stroke:#9c27b0
```

**Polygon PoS のセキュリティモデル:**
- 独自の ~100 バリデータセット（MATIC/POL をステーク）
- 定期的にチェックポイントを Ethereum に記録
- Ethereum のセキュリティを完全には継承しないが、現実的なトレードオフ

**数値で見る優位性:**

| 指標 | Ethereum L1 | Polygon PoS |
|---|---|---|
| ブロック時間 | ~12秒 | ~2秒 |
| ERC-20 Transfer ガス代 | $3〜50 | $0.001〜0.01 |
| TPS | ~30 | ~7,000 |
| ファイナリティ | 数分 | ~2分（チェックポイント） |
| JPYC コントラクト | なし | あり ✅ |

---

## 6. L2 vs サイドチェーン — どちらを選ぶべきか

```
目的別の選択ガイド:

高額な DeFi / 資産管理
  → Ethereum L1 または Arbitrum/Optimism（セキュリティ最優先）

日常的な決済（低額、頻度高い）
  → Polygon PoS または Base（速度・コスト優先）← このプロジェクト

NFT ミント（アート系）
  → Polygon PoS（安さ）または Ethereum L1（ブランド力）

企業向け / コンプライアンス重視
  → Optimism（Coinbase の Base も同系列）

ZK 最新技術を試したい
  → zkSync Era または Polygon zkEVM
```

---

## 7. このプロジェクトへの影響

```mermaid
graph TD
    JPYC["JPYC（決済トークン）\nPolygon PoS"] -->|Permit + transferFrom| BE["バックエンド\nSpring Boot"]
    BE -->|gas代 POL を支払い| POLY["Polygon PoS\nchainId=137"]
    
    DAI["DAI（学習用）\nEthereum Mainnet"] -->|Permit + transferFrom| BE2["バックエンド"]
    BE2 -->|gas代 ETH を支払い| ETH["Ethereum L1\nchainId=1"]

    POLY -.->|$0.001 のガス代| USER[ユーザー体験 ◎]
    ETH -.->|$3〜50 のガス代| USER2[ユーザー体験 ✗]

    style JPYC fill:#e8f5e9,stroke:#4caf50
    style DAI fill:#fff3e0,stroke:#ff9800
```

- **JPYC on Polygon**: 決済ガス代は数円以下 → 実用的
- **DAI on Ethereum**: ガス代が決済額を超える可能性 → 学習用参照のみ
- **将来的な移行先**: USDC on Arbitrum / Base も有力候補（Circle ネイティブ発行）

---

## まとめ

| 概念 | 一言で |
|---|---|
| Ethereum L1 | 最高の安全性・分散性、でも遅くて高い |
| Optimistic Rollup | L1 安全性を継承、7日引き出し問題あり |
| ZK Rollup | 数学的証明、速い確定、将来の主流 |
| Polygon PoS | サイドチェーン、速い・安い、JPYC はここ |
| ブロックチェーン・トリレンマ | 分散性・安全性・スケーラビリティの3つは同時に完璧にできない |

L2 の進化は止まらず、2024〜2025年の **EIP-4844（blob transactions）** により Rollup のコストがさらに1/10になりました（→ report-20参照）。
