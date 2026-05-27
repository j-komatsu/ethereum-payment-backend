# レポート02 — Ethereum アカウントと Web3j 接続

## はじめに

Phase 2 では、Ethereum ノードへの接続と ETH 残高照会 API を実装した。
本レポートでは、実装を通じて学んだ「Ethereum アカウントの仕組み」と「Web3j を通じたノード通信」の技術的背景をまとめる。

---

## 1. Ethereum アカウントの種類

Ethereum には 2 種類のアカウントが存在する。

| 種別 | 正式名称 | 説明 |
|---|---|---|
| EOA | Externally Owned Account | 秘密鍵で管理される一般ユーザーのウォレット |
| CA | Contract Account | デプロイ済みスマートコントラクトのアドレス |

どちらも `0x` + 40文字の16進数（20バイト = 160ビット）で表されるアドレスを持ち、ETH 残高を保有できる。  
見た目は同一だが、CA には秘密鍵が存在しない。

### アドレスの導出プロセス（EOA）

```
秘密鍵（256 bit ランダム） 
  → secp256k1 楕円曲線乗算 
  → 公開鍵（512 bit）
  → Keccak-256 ハッシュ（256 bit）
  → 末尾 160 bit（= 20 バイト）がアドレス
```

アドレスは公開情報だが、秘密鍵はアドレスから逆算できない（楕円曲線離散対数問題の困難性）。

---

## 2. Wei とイーサ単位の変換

ETH の最小単位は **Wei** であり、スマートコントラクトや JSON-RPC は常に Wei 単位で値をやり取りする。

| 単位 | Wei 換算 | 用途 |
|---|---|---|
| Wei | 1 | EVM内部、JSON-RPC |
| Gwei | 10^9 | ガス代の表示 |
| Ether | 10^18 | ユーザー向け表示 |

```
1 ETH = 1,000,000,000,000,000,000 Wei（10^18）
```

Web3j では `Convert.fromWei(wei, Convert.Unit.ETHER)` で変換できる。  
Wei は最大 2^256 − 1 の整数になりうるため、Java では `BigInteger`、小数変換後は `BigDecimal` を使う。  
JSON に渡す際は `.toPlainString()` で科学的記数法（`1.5E+18` など）を防ぐ。

---

## 3. JSON-RPC と eth_getBalance

Ethereum ノードは **JSON-RPC 2.0** プロトコルで外部からの問い合わせを受け付ける。  
残高照会は `eth_getBalance` メソッドを呼ぶ。

```json
// リクエスト
{
  "jsonrpc": "2.0",
  "method": "eth_getBalance",
  "params": ["0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045", "latest"],
  "id": 1
}

// レスポンス
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": "0x56bc75e2d63100000"  // 100 ETH を Wei で表した16進数
}
```

第2パラメータはブロック指定で、`"latest"`（最新確定ブロック）を使うのが通常。  
`"pending"` を指定するとメモリプール内のトランザクションも反映された仮の残高を返す。

---

## 4. Web3j によるノード通信

Web3j は Ethereum ノードへの接続を抽象化する Java ライブラリ。  
通信プロトコルとして HTTP(S)、WebSocket、IPC をサポートする。

### 接続設定（Spring Boot）

```java
@Bean
public Web3j web3j(@Value("${web3j.client-address}") String clientAddress) {
    return Web3j.build(new HttpService(clientAddress));
}
```

設定ファイルから接続先 URL を注入することで、本番（Alchemy/Infura）と開発（ローカルノード）を切り替えられる。  
URL 自体が API キーを含む機密情報のため、`.env` や環境変数で管理しリポジトリにコミットしない。

### 残高取得のコード例

```java
BigInteger wei = web3j
    .ethGetBalance(address, DefaultBlockParameterName.LATEST)
    .send()
    .getBalance();
```

`send()` は同期呼び出し（ブロッキング）。I/O 例外は `IOException` としてスローされるため、  
`ChainCommunicationException`（unchecked）にラップして呼び出し元に伝播させる。

---

## 5. ヘルスチェックの実装

Spring Actuator の `HealthIndicator` インターフェースを実装し、`eth_blockNumber` を定期呼び出しすることで  
Ethereum ノードの疎通確認を行う。

```
GET /actuator/health
{
  "status": "UP",
  "components": {
    "chain": {
      "status": "UP",
      "details": { "blockNumber": 19876543 }
    }
  }
}
```

ノードが停止・タイムアウトした場合は `"DOWN"` になり、監視システムへのアラートトリガーとして機能する。

---

## 6. アドレスバリデーションの重要性

Ethereum アドレスは EIP-55 チェックサム付きの大文字小文字混在形式と、全小文字の形式がある。  
Web3j の `ethGetBalance` は両方を受け付けるが、**形式検証なしに受け取ると誤ったアドレスに送金するリスク**がある。

本実装では正規表現 `^0x[0-9a-fA-F]{40}$` で形式を検証し、不正なアドレスは 400 Bad Request を返す。

EIP-55 チェックサム（大文字小文字による誤字検出）の検証は Phase 3 以降のタスクとして別途検討する。

---

## まとめ

| 項目 | 実装内容 |
|---|---|
| 接続設定 | `Web3jConfig` で Bean 生成、URL は環境変数から注入 |
| 残高照会 API | `GET /api/v1/chain/eth-balance/{address}` → Wei + ETH 単位で返す |
| ヘルスチェック | `ChainHealthIndicator` → `/actuator/health` に自動登録 |
| エラーハンドリング | `ChainCommunicationException → 502 Bad Gateway` |
| バリデーション | `@Pattern(regexp = "^0x[0-9a-fA-F]{40}$")` でアドレス形式を強制 |
| API ドキュメント | SpringDoc OpenAPI → `/swagger-ui.html` で Swagger UI |
