# JSON-RPC 詳解 — Ethereumとの通信プロトコル

> 🌿 中級者向け | 読了時間: 約15分  
> 前提: [beginner/02-rpc-and-alchemy.md](../beginner/02-rpc-and-alchemy.md) を読了済み  
> 次のステップ: [RPCプロバイダー比較](02-rpc-provider-comparison.md)

---

## JSON-RPC 2.0 仕様

Ethereum は **JSON-RPC 2.0** という規格でAPIを公開しています。

```json
// リクエスト
{
  "jsonrpc": "2.0",           // 必ず "2.0"
  "method": "eth_getBalance", // 呼び出すメソッド名
  "params": ["0x...", "latest"], // 引数
  "id": 1                     // レスポンスと対応させるID
}

// 成功レスポンス
{
  "jsonrpc": "2.0",
  "result": "0x1BC16D674EC80000",  // 成功時は result
  "id": 1
}

// エラーレスポンス
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32602,
    "message": "Invalid params"
  },
  "id": 1
}
```

---

## 主要な eth_* メソッド一覧

### 残高・アカウント系

| メソッド | 説明 | params |
|---|---|---|
| `eth_getBalance` | ETH残高（wei単位）| `[address, blockTag]` |
| `eth_getTransactionCount` | nonce（送信済みTX数）| `[address, blockTag]` |
| `eth_getCode` | コントラクトのバイトコード | `[address, blockTag]` |
| `eth_getStorageAt` | コントラクトのストレージ値 | `[address, position, blockTag]` |

### ブロック・TX系

| メソッド | 説明 |
|---|---|
| `eth_blockNumber` | 最新ブロック番号 |
| `eth_getBlockByNumber` | ブロック詳細（TX一覧含む）|
| `eth_getBlockByHash` | ハッシュでブロック取得 |
| `eth_getTransactionByHash` | TX詳細 |
| `eth_getTransactionReceipt` | TX実行結果（イベントログ・ガス使用量）|

### コントラクト呼び出し系

| メソッド | 説明 | ガス |
|---|---|---|
| `eth_call` | 読み取り専用（状態変更なし）| 不要 |
| `eth_sendRawTransaction` | 署名済みTXをブロードキャスト | 必要 |
| `eth_estimateGas` | ガス見積もり | - |

### イベント系（このプロジェクトで重要）

| メソッド | 説明 |
|---|---|
| `eth_getLogs` | 条件でイベントログを検索 |
| `eth_newFilter` | フィルターを作成（WebSocket向け）|
| `eth_getFilterChanges` | フィルターの新着ログを取得 |
| `eth_newBlockFilter` | 新しいブロックの通知フィルター |

---

## blockTag パラメータ

多くのメソッドで `blockTag` を指定します:

| 値 | 意味 |
|---|---|
| `"latest"` | 最新ブロック（通常はこれ）|
| `"earliest"` | ジェネシスブロック（ブロック#0）|
| `"pending"` | まだブロックに含まれていないTX |
| `"finalized"` | ファイナリティが確定したブロック（PoS以降）|
| `"safe"` | ほぼ安全なブロック（1エポック確定済み）|
| `"0xbc614e"` | 特定のブロック番号（16進数）|

---

## eth_call の実際（ERC-20残高取得）

ERC-20 の `balanceOf(address)` を読み取る例:

**送られるJSON:**
```json
{
  "method": "eth_call",
  "params": [
    {
      "to": "0x431D5dfF03120AFA4bDf0b...",
      "data": "0x70a08231"
              + "000000000000000000000000"
              + "1234567890abcdef..."
    },
    "latest"
  ]
}
```

`data` の構造:
```
0x70a08231                        ← balanceOf() の関数セレクタ（keccak256の先頭4バイト）
000000000000000000000000          ← 32バイトにパディング
1234567890abcdef...               ← アドレス（20バイト）
```

**Web3j での同等コード:**
```java
Function function = new Function(
    "balanceOf",
    List.of(new Address(walletAddress)),
    List.of(new TypeReference<Uint256>() {})
);
String encoded = FunctionEncoder.encode(function);
EthCall result = web3j.ethCall(
    Transaction.createEthCallTransaction(null, contractAddress, encoded),
    DefaultBlockParameterName.LATEST
).send();
BigInteger balance = (BigInteger) FunctionReturnDecoder
    .decode(result.getValue(), function.getOutputParameters())
    .get(0).getValue();
```

---

## eth_getLogs の使い方（Transfer イベント監視）

このプロジェクトの核心部分。ERC-20 Transfer イベントを検索する例:

```json
{
  "method": "eth_getLogs",
  "params": [{
    "fromBlock": "0xbc6000",
    "toBlock": "latest",
    "address": "0x431D5dfF...",
    "topics": [
      "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
      null,
      "0x000000000000000000000000受取人アドレス..."
    ]
  }]
}
```

topics の構造:
```
topics[0]: イベントシグネチャのハッシュ
           Transfer(address indexed from, address indexed to, uint256 value)
           → keccak256("Transfer(address,address,uint256)") = 0xddf252...

topics[1]: from アドレス（null = 誰でも OK）
topics[2]: to アドレス（受取人でフィルター）
```

---

## HTTP vs WebSocket

| 方式 | 特徴 | 向いている用途 |
|---|---|---|
| **HTTP** | 1リクエスト = 1レスポンス | 残高照会・TX送信・ポーリング |
| **WebSocket** | 常時接続・プッシュ通知 | リアルタイムイベント監視 |

**このプロジェクトが HTTP ポーリングを選んだ理由:**
- WebSocketは接続維持・再接続の実装が複雑
- 15秒ポーリングで十分なリアルタイム性
- シンプルな実装を優先（学習目的）

本番で高いリアルタイム性が必要なら WebSocket（または Alchemy Webhook）を検討。

---

## エラーコード一覧

| コード | 名前 | よくある原因 |
|---|---|---|
| `-32700` | Parse error | JSONが不正（構文エラー）|
| `-32600` | Invalid Request | `jsonrpc`や`method`が不正 |
| `-32601` | Method not found | メソッド名のタイポ |
| `-32602` | Invalid params | params の型・数が間違い |
| `-32603` | Internal error | ノード側の内部エラー |
| `-32000` | Server error | TX rejected, nonce incorrect など |

---

## バッチリクエスト

複数のリクエストを1回のHTTP通信でまとめて送れます:

```json
[
  {"jsonrpc": "2.0", "method": "eth_getBalance", "params": ["0xAddr1", "latest"], "id": 1},
  {"jsonrpc": "2.0", "method": "eth_getBalance", "params": ["0xAddr2", "latest"], "id": 2},
  {"jsonrpc": "2.0", "method": "eth_blockNumber", "params": [], "id": 3}
]
```

レスポンスも配列で返ります（順序は保証されないので `id` で対応させる）。  
大量のアドレスを一括照会する場合に有効ですが、Alchemy の無料枠ではバッチ上限があるため注意。

---

## 次に読むもの

→ [02-rpc-provider-comparison.md](02-rpc-provider-comparison.md)  
「Alchemy・Infura・自前ノード、どれを選ぶか」
