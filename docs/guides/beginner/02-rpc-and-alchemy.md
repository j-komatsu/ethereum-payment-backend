# RPCとAlchemy — アプリはどうやってブロックチェーンと話すのか

> 🌱 初心者向け | 読了時間: 約10分  
> 前のステップ: [ブロックチェーンの世界](01-blockchain-world.md)  
> 次のステップ: [バリデーターとは](03-validators.md)

---

## アプリはブロックチェーンに「問い合わせ」する

あなたが作るアプリ（Spring Boot など）は、ブロックチェーンの外に存在します。  
残高を調べたり、送金を確認したりするには、**問い合わせ** が必要です。  
この問い合わせの仕組みが **RPC（Remote Procedure Call）** です。

---

## RPCを例え話で理解する

### 「銀行の電話照会サービス」

```
昔の銀行（窓口しかない時代）:
  残高を知りたい → 本店まで直接行く → 係員に調べてもらう

今の銀行:
  残高を知りたい → 0120-xxx-xxx に電話
                 → 「残高照会は3番を押して口座番号を入力」
                 → 本店に行かずに答えがもらえる
```

**ブロックチェーンも同じです:**

```
あなたのアプリ
    ↓ 「このアドレスの残高は？」（電話照会）
Ethereumノード（銀行の本店）
    ↓ 「1000 JPYCです」
あなたのアプリ
```

この「電話照会」の仕組みが **RPC**、  
電話で使う言語の決まり（形式）が **JSON-RPC** です。

---

## Ethereumノードとは

**ノード** = 「Ethereumの全記録（台帳）を持つコンピューター」

ノードを自分で立てるには:
- **ストレージ**: 1〜2TB 以上（全ブロックを保存）
- **初期同期**: 最初のセットアップに数日かかる
- **維持コスト**: 24時間稼働サーバー + 電気代 + 管理コスト
- **技術的複雑さ**: Linuxサーバー管理・定期アップデート

---

## Alchemy とは

### 「コールセンターの代行サービス」

```
自前ノードを立てる場合:
  自分でコールセンター（本店）を作る
  → 建物を借りて、電話機を買って、スタッフを雇って...
  → 何百万円もかかる、毎日の管理が必要

Alchemy を使う場合:
  Alchemy が代わりにコールセンターを用意してくれる
  → あなたは API キー（電話番号）を受け取るだけ
  → 月3億回まで無料で使える
```

**Alchemy の役割:**

```
あなたのアプリ
    ↓ HTTP（電話）
Alchemy（コールセンター代行）
    ↓ 内部で管理している何台ものノードに転送
Ethereumネットワーク
```

あなたのアプリから見ると「Alchemy に問い合わせているだけ」ですが、  
裏では Alchemy が世界中のノードを管理して答えを返しています。

---

## JSON-RPC の実態（見てみましょう）

実際に送られるのは以下のような JSON データです:

```json
// あなたのアプリが送るリクエスト
POST https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY
{
  "jsonrpc": "2.0",
  "method": "eth_getBalance",
  "params": ["0x1234...5678", "latest"],
  "id": 1
}

// Ethereumノードからの返答
{
  "result": "0x56BC75E2D63100000"  // 100 MATIC（wei単位）
}
```

難しく見えますが要点は:
- `method`: 「何を聞くか」（残高・最新ブロック・取引内容など）
- `params`: 「誰の・いつの」という条件
- `result`: 答え

**Web3j** というライブラリを使うと、このJSON作成を自動化できます:
```java
// これだけ書けば内部でJSONを組み立ててAlchemyに送ってくれる
BigInteger balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                          .send().getBalance();
```

---

## 主なRPCプロバイダーの比較

| | Alchemy | Infura | 自前ノード |
|---|---|---|---|
| 無料枠 | 月3億CU | 月300万リクエスト | コスト0（設備費別）|
| 初期費用 | 0円 | 0円 | サーバー代 数万〜数十万円 |
| 月額費用 | 0円（無料枠内）| 0円（無料枠内）| 毎月 数万円〜 |
| 設定の手間 | APIキー発行だけ | 同左 | 数日かかる初期設定 |

**個人開発・学習目的なら Alchemy の無料枠で十分です。**

---

## 法人でもAlchemyを使っていいの？

**はい、法人でも広く採用されています。**

- Coinbase・OpenSea などの大手も利用
- エンタープライズプランで SLA・専用エンドポイント・監査ログを提供
- 自前ノードが必要になるのは月数十億リクエスト超か、外部にデータを出せないコンプライアンス要件がある場合だけ

詳しくは → [法人向けガイド](../use-cases/enterprise.md)

---

## このプロジェクトでの設定

```yaml
# application.yml（設定ファイル）
web3j:
  polygon-endpoint: ${WEB3J_POLYGON_ENDPOINT}   ← 環境変数から読む
  ethereum-endpoint: ${WEB3J_ETHEREUM_ENDPOINT}
```

```bash
# .env（絶対にGitにコミットしない！）
WEB3J_POLYGON_ENDPOINT=https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY
WEB3J_ETHEREUM_ENDPOINT=https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
```

Web3j がこのURLに向かってJSON-RPCリクエストを自動的に送ります。  
`web3j.ethGetBalance(...)` を呼ぶだけで、内部的には Alchemy への HTTP リクエストが飛びます。

---

## まとめ

| 用語 | 例え | 一言説明 |
|---|---|---|
| RPC | 電話での照会 | リモートで関数を呼び出す仕組み |
| JSON-RPC | 電話で使う言語（形式）| JSON形式でRPCするプロトコル |
| Ethereumノード | 銀行の本店 | 全記録を持つコンピューター |
| Alchemy / Infura | コールセンター代行業者 | ノードをクラウドで提供するサービス |
| Web3j | 電話の自動ダイヤル機能 | JSON-RPCをJavaメソッドとして使えるライブラリ |

---

## 次に読むもの

→ [03-validators.md](03-validators.md)  
「誰がブロックチェーンを承認・記録しているの？」
