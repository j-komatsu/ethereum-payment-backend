# PoSとファイナリティ — バリデーターの技術詳細

> 🌿 中級者向け | 読了時間: 約15分  
> 前のステップ: [RPCプロバイダー比較](02-rpc-provider-comparison.md)  
> 関連: [report-20 Ethereumロードマップ](../../report-20-ethereum-roadmap.md)

---

## The Merge（2022年9月15日）

Ethereum は 2022年9月15日に「**The Merge**」を完了し、  
Proof of Work (PoW) から Proof of Stake (PoS) に移行しました。

```
The Merge 前（PoW）:
  - マイナーが SHA-256 計算競争でブロックを作る
  - 大量の電力消費（スウェーデン1国と同等）
  - ASIC マシン＋大量電力が必要 → 資本集中

The Merge 後（PoS）:
  - バリデーターが ETH を担保（Stake）に参加
  - 電力消費 約99.95% 削減
  - 32 ETH があれば原理上誰でも参加可能
```

---

## スロット・エポックの概念

PoS の時間単位:

```
スロット（Slot）: 12秒 × 32スロット = 1エポック（384秒 ≒ 6.4分）

[Slot 0][Slot 1][Slot 2]...[Slot 31] = 1 Epoch（エポック）
  12秒    12秒    12秒  ...   12秒
```

- 各スロットで1バリデーターが「ブロック提案者（Proposer）」として選ばれる
- 選ばれたバリデーターがブロックを提案
- 他の全バリデーターがそのブロックを検証して署名（**Attestation**）

---

## バリデーターの選出メカニズム

バリデーターはランダムに選ばれますが、そのランダム性は **RANDAO** という仕組みで実現されます。

```
RANDAO の仕組み（簡略化）:
  全バリデーターが「シークレット値」を事前コミット
  → コミット値を集めてXORとハッシュで混ぜる
  → その結果を「乱数」として使う
  → 次のエポックのブロック提案者を決定

特性:
  - 事前に予測不可能（全員の値が混ざるため）
  - 操作困難（自分の値だけ変えても影響が小さい）
```

---

## Attestation（証明・検証）の仕組み

各スロットで:

```
1. バリデーターA（提案者）がブロックを作成・提案
2. バリデーターB, C, D, ... が独立に検証して署名
3. 2/3 以上が署名 → ブロックが「Justified（正当化）」される
```

Attestation は毎スロットで行われますが、  
**最終的な確定（ファイナリティ）には2エポック必要**です。

---

## ファイナリティの詳細

### Ethereum PoS のファイナリティ

```
Timeline:
  t=0s    : ブロックがネットワークに伝播
  t=12s   : 次のスロット開始（このブロックへの Attestation 収集）
  t=6.4min: 1エポック経過 → Justified
  t=12.8min: 2エポック経過 → **Finalized（ファイナリティ確定）**
```

「Finalized」になると、そのブロックの内容を変更するには  
バリデーター全体の **1/3以上が共謀して担保を失う** 必要があります（事実上不可能）。

### Casper FFG（Finality Gadget）

Ethereum PoS のファイナリティは **Casper FFG** というプロトコルで実現されます:

```
Checkpoint: 各エポックの最初のブロック

エポック N の Checkpoint が Justified される条件:
  - 前の Checkpoint が Justified であること
  - 全バリデーターの 2/3 以上が署名すること

エポック N が Finalized される条件:
  - エポック N が Justified であること
  - エポック N+1 も Justified であること（隣接 Checkpoint の連続 Justification）
```

---

## 実際に何ブロック待つべきか

### アプリ開発での判断基準

| 待機 | 状態 | 適用例 |
|---|---|---|
| 0 blocks | ブロックに含まれた | 超低額の高速決済（非推奨）|
| 1〜5 blocks | ほぼ確定 | 少額決済、通常ユースケース |
| 12 blocks（≈2.4分）| PoW時代の慣習 | 慣例的に「安全」とされる水準 |
| 2 Epochs（≈12.8分）| Finalized | 高額決済・厳密なファイナリティが必要な場合 |

**blockTag "finalized" を使う方法:**
```json
{
  "method": "eth_getBalance",
  "params": ["0x...", "finalized"]
}
```
これで自動的にファイナリティ確定済みの値を返します。

### このプロジェクトの判断

```java
// TransferEventPoller での現在の判断
// Polygon は2秒でブロック → 5ブロック（≈10秒）で実用上十分
private static final int CONFIRMATION_BLOCKS = 5;
```

Polygon の確認が速い理由はバリデーター数が少ないことと  
ブロック時間が短いことによります（詳細は下記）。

---

## Polygon PoS（Ethereum PoSとの違い）

このプロジェクトが使う Polygon は独自のPoSです。

| 比較項目 | Ethereum PoS | Polygon PoS |
|---|---|---|
| ブロック時間 | 12秒 | 約2秒 |
| バリデーター数 | 100万台超 | 100名程度（委任制）|
| ファイナリティ | 約12.8分（Casper FFG）| ブロック確認2秒、チェックポイント約30分 |
| セキュリティ | Ethereumネットワーク全体 | Polygonバリデーター群 |
| チェックポイント | なし | Ethereum Mainnetに定期記録 |

### Polygon のチェックポイント（Checkpoint）

```
Polygon ブロック（2秒ごと）:
  [Block 1] → [Block 2] → [Block 3] → ... → [Block N]
                                               ↓ 約30分ごと
                                  Ethereum Mainnet にマークルルートとして記録
```

チェックポイントにより:
- Polygonの取引が Ethereum のセキュリティに「裏付け」される
- チェックポイント後は Ethereum 本体でも証明可能

**トレードオフ:**  
速い（2秒/block）→ バリデーター数が少ない → Ethereumより分散度は低い

---

## Slashing の詳細

### 違反の種類

| 違反 | 内容 | ペナルティ |
|---|---|---|
| Double Proposal（二重提案）| 同じスロットで2つの異なるブロックを提案 | 担保の一部没収 + 強制退場 |
| Double Vote（二重投票）| 同じエポックの2つの異なる Checkpoint に Attestation | 同上 |
| Surround Vote（包囲投票）| 以前の Attestation を「包囲」するような投票 | 同上 |

### Inactivity Leak（非活動リーク）

ネットワーク全体でファイナリティが止まった場合、  
非参加のバリデーターに対してスタックされた ETH が徐々に削減されます。

```
ファイナリティ停止が続くと:
  オフラインのバリデーター → ETH が徐々に減る（リーク）
  → 最終的にオンラインのバリデーターが 2/3 超を占める
  → ファイナリティが再開できる
```

これにより長期障害時でも最終的に多数派がコントロールを取り戻せます。

---

## まとめ: アプリ開発者が知るべきポイント

```
1. 送金確認には「何ブロック待つか」を決める
   - Ethereum: 厳密には 2エポック（12.8分）、実用的には 12 blocks（2.4分）
   - Polygon: 5〜10 blocks（10〜20秒）で十分

2. "finalized" blockTag を使うと自動的に安全な値が返る

3. バリデーターを自前で運用する必要はない
   → 確定したブロックを Alchemy 経由で読むだけ

4. Polygon は速い代わりにバリデーター数が少ない（セキュリティのトレードオフ）
```

---

## 次に読むもの

- [advanced/README.md](../advanced/README.md) — 技術レポート群（実装レベル）
- [report-20 Ethereumロードマップ](../../report-20-ethereum-roadmap.md) — PoS移行・Danksharding・将来展望
- [use-cases/personal-dev.md](../use-cases/personal-dev.md) — 実際の開発環境セットアップ
