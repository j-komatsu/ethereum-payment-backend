# レポート04：Ethereum イベント監視 — Logs・Transfer・ポーリング・ファイナリティ

**対象フェーズ:** Phase 4（Day 16-20）  
**作成日:** 2026-05-27

---

## 1. Ethereum のイベント（Log）とは何か

### なぜコントラクトはイベントを発火するのか

Ethereum のスマートコントラクトは、状態変化を外部に通知するために **イベント（Event）** を使う。イベントはブロックチェーン上のトランザクションのレシート（receipt）に **Log** として記録される。

コントラクトがイベントを使う主な理由は2つある。

| 理由 | 説明 |
|---|---|
| **ストレージコスト回避** | コントラクトのストレージ（storage）に書き込むと多くのガスを消費するが、Log への書き込みはストレージの数分の1〜数十分の1のコストで済む（Transfer イベントは SSTORE の約 1/10〜1/11 程度） |
| **外部監視の効率化** | DApp のフロントエンドやバックエンドは、コントラクトの全トランザクションを個別に取得するのではなく、必要なイベントだけを `eth_getLogs` でフィルタリングして取得できる |

ただしイベントはコントラクト自身からは読み取れない。「発信専用の通知」として設計されており、コントラクト外部（フロントエンド・バックエンド）が受け取るための仕組みである。

### Log の構造

各 Log（イベントの記録）は以下のフィールドで構成される。

```
Log {
  address  : コントラクトアドレス（どのコントラクトが発火したか）
  topics   : [bytes32; 最大4件]（indexed パラメーターのハッシュ値）
  data     : bytes（non-indexed パラメーターのABIエンコード値）
  blockNumber, transactionHash, logIndex, ... （メタ情報）
}
```

### topics[0] がイベントシグネチャのハッシュである意味

`topics[0]` には常に **イベントシグネチャの keccak256 ハッシュ** が格納される。

```
keccak256("Transfer(address,address,uint256)")
= 0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
```

このハッシュをトピックフィルターに指定することで、「このコントラクトの Transfer イベントだけ」という絞り込みが高速に実現できる。コントラクトアドレスをフィルターに追加することで、「JPYC コントラクトの Transfer イベントだけ」を効率的に取得できる。

Web3j での計算方法：

```java
Event TRANSFER_EVENT = new Event("Transfer", List.of(
    new TypeReference<Address>(true) {},   // from (indexed)
    new TypeReference<Address>(true) {},   // to   (indexed)
    new TypeReference<Uint256>() {}        // value (non-indexed)
));
String topic0 = EventEncoder.encode(TRANSFER_EVENT);
// → "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
```

---

## 2. Transfer イベントの読み方

### Transfer イベントの Solidity 定義

ERC-20 標準（EIP-20）で定義された Transfer イベントのシグネチャ：

```solidity
event Transfer(address indexed from, address indexed to, uint256 value);
```

### indexed パラメーターとは何か

`indexed` キーワードを付けたパラメーターは `topics` 配列に格納される（最大3つまで）。`indexed` を付けないパラメーターは `data` フィールドにまとめて格納される。

| フィールド | 内容 |
|---|---|
| `topics[0]` | `keccak256("Transfer(address,address,uint256)")` = イベント識別子 |
| `topics[1]` | `from` アドレス（32バイトに0パディング） |
| `topics[2]` | `to` アドレス（32バイトに0パディング） |
| `data` | `value`（uint256 の 32バイト ABI エンコード） |

実際のログデータ例（JPYC の Transfer ログ）：

```
topics[0]: 0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
topics[1]: 0x000000000000000000000000abcd...1234  ← from (前24バイトがゼロパディング)
topics[2]: 0x000000000000000000000000efgh...5678  ← to   (同上)
data:      0x000000000000000000000000000000000000000000000de0b6b3a7640000  ← 1.0 JPYC (1e18 = 10^18)
```

`indexed` にすることの効果：
- **フィルタリングが高速になる** — ノードはフルログをスキャンせず、topics のインデックスだけを参照して検索できる
- **ただし完全なデータはtopicsにある** — アドレスを `topics` で絞り込めば「特定アドレスへの送金だけ」を取得できる

### ABI デコードとは何か

ABI（Application Binary Interface）デコードとは、Ethereum の標準エンコーディング形式で格納されたバイト列を元の型に変換する処理のこと。

`data` フィールドの `uint256` をデコードする例：

```java
// Web3j での data デコード
BigInteger rawAmount = Numeric.decodeQuantity(logObj.getData());
// 1 JPYC = 1_000_000_000_000_000_000 (10^18 の最小単位)

// 人間が読める単位に変換
BigDecimal humanAmount = new BigDecimal(rawAmount).movePointLeft(18); // → 1.0
```

`topics` の indexed アドレスは 32バイトに左ゼロパディングされているため、末尾40文字（20バイト）がアドレス本体になる：

```java
private static String decodeAddress(String paddedHex) {
    String hex = Numeric.cleanHexPrefix(paddedHex); // "0x" を除去
    // 64文字（32バイト）の16進数から末尾40文字（20バイト）を取り出す
    return "0x" + hex.substring(24); // 先頭24文字はゼロパディング
}
```

---

## 3. eth_getLogs の仕組み

### fromBlock / toBlock の指定方法

`eth_getLogs` は指定ブロック範囲内のログをまとめて返す JSON-RPC メソッドである。

```json
{
  "method": "eth_getLogs",
  "params": [{
    "fromBlock": "0x1234abc",
    "toBlock": "0x1234abd",
    "address": "0xE7C3D8C9...",
    "topics": ["0xddf252ad..."]
  }]
}
```

Web3j での実装：

```java
EthFilter filter = new EthFilter(
    DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
    DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
    token.getContractAddress()  // address フィルター
);
filter.addSingleTopic(TRANSFER_TOPIC);  // topics[0] フィルター
EthLog ethLog = web3j.ethGetLogs(filter).send();
```

### address フィルターと topics フィルターの組み合わせ

フィルターの組み合わせで「必要なイベントだけ」を効率よく取得できる：

| フィルター種別 | 役割 |
|---|---|
| `address` | 特定コントラクトのログのみに絞り込む |
| `topics[0]` | 特定イベント種別（Transfer, Approval 等）に絞り込む |
| `topics[1]` | from アドレスで絞り込む（任意） |
| `topics[2]` | to アドレスで絞り込む（任意） |

今回の実装では `address`（JPYC コントラクト）+ `topics[0]`（Transfer シグネチャ）の組み合わせで全 Transfer を取得し、バックエンド側で受取アドレスとのマッチングを行っている。

### 大量ブロックを一度に取得できない理由

`eth_getLogs` のレスポンスサイズや処理時間は、指定ブロック範囲とログ件数に比例して増加する。そのため Infura・Alchemy 等のノードプロバイダーはレート制限を設けている：

| プロバイダー | ブロック範囲上限 | レスポンスサイズ上限 |
|---|---|---|
| Infura（Free Tier） | 10,000 ブロック | 10,000件 / 2MB |
| Alchemy（Free Tier） | **2,000 ブロック** | 10,000件 |

Polygon のブロック時間は約2秒なので 2,000ブロック ≈ 約67分分に相当する。本実装では `MAX_BLOCK_RANGE = 2_000` を上限として、一回のポーリングで最大2,000ブロック分を処理している。

---

## 4. ポーリング vs WebSocket サブスクリプション

### ポーリング（HTTP）の仕組み

一定間隔で `eth_getLogs` を呼び出し、前回処理済みのブロック番号から現在の安全なブロック番号（`currentBlock - REQUIRED_CONFIRMATIONS`）までのイベントを取得する。

```
[バックエンド] → (15秒ごと) → [ノード] eth_getLogs → [バックエンド]
```

**メリット：**
- HTTP/HTTPS で実装可能（ファイアウォール・プロキシに強い）
- ステートレスで再起動後も `lastProcessedBlock` から再開できる
- Infura/Alchemy の HTTP エンドポイントがそのまま使える

**デメリット：**
- リアルタイム性に欠ける（最大ポーリング間隔 + ブロック確認待ちの遅延）
- ノードへのリクエスト数が多い

### WebSocket サブスクリプション（eth_subscribe）

WebSocket 接続を確立し、イベントをプッシュで受け取る：

```json
// サブスクリプション開始
{ "method": "eth_subscribe", "params": ["logs", {
  "address": "0xE7C3D8C9...",
  "topics": ["0xddf252ad..."]
}]}

// ノードからのプッシュ通知
{ "method": "eth_subscription", "params": {
  "subscription": "0x1234...",
  "result": { /* ログデータ */ }
}}
```

**メリット：**
- リアルタイム（新しいブロックが確認されると即座に通知）
- ノードへのリクエスト数が少ない

**デメリット：**
- WebSocket 接続を常時維持する必要がある（接続断時の再接続処理が必要）
- Infura の WebSocket は無料枠でも使えるが、**接続数・メッセージ数の制限が厳しい**
- reorg（チェーン再編成）時に「削除されたログ」の通知も届くため、削除通知の処理が必要

### Infura 無料枠での制約

| 方式 | 制限事項 |
|---|---|
| HTTP ポーリング | 1日あたり 100,000 リクエストまで（2023年以降クレジット制に変更済み。要確認） |
| WebSocket | 接続数が少なく、長時間接続では切断が頻発する |

学習・PoC 用途では HTTP ポーリングが安定して扱いやすい。本番スケールでは専用ノード（Alchemy Growth Plan 等）または自前 Polygon ノードを検討する。

---

## 5. ブロック確認数（Confirmations）とファイナリティ

### なぜ1ブロックだけでは信頼できないのか

マイナー（またはバリデーター）がブロックを生成すると、そのブロックがチェーンの一部として確定するまでに短い競合期間がある。別のノードが同じブロック高で異なるブロックを生成した場合、どちらかが **reorg（チェーン再編成）** によって棄却される。

```
Block N (A)  ← 後でこちらが採用される
         ↘
          Block N+1 ← こちらが孤立ブロック（orphan）になる
Block N (B)
```

入金を検知した直後のブロックが reorg で棄却されると、そのトランザクションは「なかったこと」になる可能性がある。

### 一般的な確認数の目安

| ネットワーク | 取引所の典型的な確認数 | ブロック時間 | 合計待機時間 |
|---|---|---|---|
| Ethereum (PoS) | 12〜35 ブロック | 12 秒 | 2.5〜7 分 |
| Polygon PoS | 128〜256 ブロック（取引所基準） | 2 秒 | 4〜8 分 |
| Polygon PoS | **20 ブロック**（本 PoC での設定） | 2 秒 | 約40秒 |
| Bitcoin | 6 ブロック | 10 分 | 約60分 |

取引所がPolygonで256ブロック確認を要求するのは、過去に数十〜百数十ブロックに及ぶ大規模 reorg が発生した事例があったためである。本 PoC では学習・開発用途として20ブロック（約40秒）を採用している。

### Ethereum の PoS 移行後のファイナリティの変化

Ethereum は2022年9月の「The Merge」で Proof of Work (PoW) から **Proof of Stake (PoS)** に移行した。この移行により、ファイナリティの概念が変わった。

**PoW 時代（Merge 前）：**
- ファイナリティは「統計的確率」に基づく（ブロック数が増えるほど reorg の確率が指数的に減少）
- 数学的な「絶対的なファイナリティ」は存在しない

**PoS 移行後（Merge 後）：**
- 約6.4分（1エポック = 32スロット）ごとに **チェックポイント** が設けられる
- チェックポイントが2つ連続してバリデーターの 2/3 以上の票を集めると **Finalized（確定）** になる
- Finalized になったブロックは理論上 reorg されることがない（バリデーターのスラッシュなしには不可能）

```
Slot  1-32:  Epoch N
Slot 33-64:  Epoch N+1  ← 2エポック後、Epoch N が Justified
            (さらに次のエポックで Finalized)
```

**Polygon PoS との違い：**

Polygon PoS は独自の Bor + Heimdall アーキテクチャを採用しており、Ethereum PoS の Finality とは仕組みが異なる。

| 特徴 | Ethereum PoS | Polygon PoS |
|---|---|---|
| ファイナリティ | 約12.8分でFinalized | チェックポイントは ~30分ごとにEthに刻まれる |
| reorg リスク | Finalized後はゼロ | チェックポイント前は軽微なreorgリスク残存 |
| ブロック時間 | 12秒 | 約2秒 |
| 20確認での安全性 | 約4分（PoC用途では十分） | 約40秒（実用的な安全水準） |

---

## 6. 本実装での設計決定まとめ

### TransferEventPoller の設計パラメーター

| パラメーター | 値 | 根拠 |
|---|---|---|
| `REQUIRED_CONFIRMATIONS` | 20 | Polygon PoS の軽微なreorgリスク対策（≈40秒）。PoC向け許容値 |
| `MAX_BLOCK_RANGE` | 2,000 | Alchemy Free Tier の `eth_getLogs` 上限（Polygon ≈67分分） |
| `INITIAL_LOOKBACK_BLOCKS` | 200 | 初回起動時の遡及範囲（≈7分）。全チェーンスキャンを回避 |
| `interval-ms` | 15,000 ms | Polygon のブロック時間（2秒）に対して適切なポーリング間隔 |

### エラー耐性の設計

`eth_getLogs` がエラーを返した場合、例外をスローして `lastProcessedBlock` を進めないことで、次回のポーリングで同じブロック範囲を再試行できるようにしている。

```
eth_getLogs エラー
    ↓ throw ChainCommunicationException
pollToken の catch ブロック
    ↓ lastProcessedBlock は更新しない
次回のポーリング（15秒後）で同じ fromBlock から再試行
```

### 冪等性の保証

同じ Transfer イベントが複数回処理されても一度だけ入金確認されるよう、2つの仕組みを組み合わせている：

1. **ソフトチェック** — `repository.existsByTxHash(txHash)` で既処理かどうかを確認
2. **ハードチェック** — `PaymentOrder.txHash` の DB UNIQUE 制約により、二重保存を防止

> **注意（スケールアウト時）:** 上記の冪等性は単一インスタンス稼働を前提とする。複数インスタンスに水平スケールした場合、異なるインスタンスが同じブロック範囲を重複処理する可能性があり、UNIQUE 制約による `DataIntegrityViolationException` が発生しうる。本番スケールでは ShedLock 等の分散ロックが別途必要。

---

## まとめ

Ethereum のイベント（Log）は、コントラクトの状態変化を低コストで外部に通知するための仕組みである。ERC-20 の Transfer イベントは `topics[1]=from`, `topics[2]=to`, `data=value` という構造を持ち、`eth_getLogs` でフィルタリングすることで特定コントラクトへの送金をリアルタイムに検知できる。

HTTP ポーリングは実装がシンプルで再起動耐性があり、学習・PoC 用途には適した方式である。Polygon PoS では20ブロック確認（約40秒）を設けることで、軽微なチェーン再編成に対応している。Ethereum は PoS 移行により強力な「数学的ファイナリティ」を獲得したが、Polygon は独自アーキテクチャを持ち、確認数によるリスク管理が引き続き重要となる。
