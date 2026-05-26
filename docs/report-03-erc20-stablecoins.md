# レポート03 — ERC-20 規格とステーブルコイン

## はじめに

Phase 3 では JPYC をはじめとする ERC-20 トークンの残高照会 API を実装した。
本レポートでは「スマートコントラクト」「ERC-20 規格」「decimals の罠」について整理する。

---

## 1. スマートコントラクトとは何か

スマートコントラクトは Ethereum Virtual Machine（EVM）上で動くプログラムで、デプロイ後にブロックチェーン上に永続化される。

### コントラクトアドレスの決定方法

EOA（ユーザーウォレット）のアドレスは秘密鍵から導出されるが、コントラクトアドレスはデプロイ時に以下から決定される：

```
keccak256(RLP(deployerAddress, nonce)) の末尾 20 バイト
```

つまり同じアドレスから同じ nonce でデプロイすれば常に同じコントラクトアドレスになる。

### ABI（Application Binary Interface）とは

コントラクトは EVM バイトコードで動くため、外部から呼ぶには「どの関数に何を渡すか」をバイトコードに変換するルールが必要。
ABI がそのルールを JSON 形式で定義する。

```json
{
  "name": "balanceOf",
  "inputs":  [{ "name": "account", "type": "address" }],
  "outputs": [{ "name": "", "type": "uint256" }]
}
```

Web3j では ABI を読んで Java コードを自動生成（バインディング）できる。
本実装では ABI エンコード/デコードを手動で行うことで仕組みを理解した。

---

## 2. ERC-20 規格とは何か

ERC-20 は Ethereum の「トークン標準」で、互換性のあるトークンが同一のインターフェースで扱えるようになる。
USDC・USDT・DAI・JPYC はすべて ERC-20 に準拠しているため、同じコードで残高照会できる。

### 必須メソッド

| メソッド | 説明 |
|---|---|
| `totalSupply()` | 発行総量 |
| `balanceOf(address)` | 指定アドレスの残高 |
| `transfer(to, amount)` | 送金 |
| `transferFrom(from, to, amount)` | 承認済み送金（代理送金） |
| `approve(spender, amount)` | 引き落とし上限の承認 |
| `allowance(owner, spender)` | 残余引き落とし上限 |

### イベント

| イベント | 発火タイミング |
|---|---|
| `Transfer(from, to, value)` | 送金成功時（transfer / transferFrom）|
| `Approval(owner, spender, value)` | approve 成功時 |

Phase 4 の入金検知では `Transfer` イベントを監視する。

---

## 3. JPYC / USDC / USDT / DAI の違い

| トークン | 発行体 | 担保 | decimals | チェーン | 特徴 |
|---|---|---|---|---|---|
| JPYC | JPYC Inc. | 円建て前払式支払手段 / 電子決済手段 | 18 | Polygon | 日本円ペッグ・EIP-2612対応 |
| USDC | Circle | 法定通貨（USD）100% | 6 | Ethereum | 規制準拠・透明性高 |
| USDT | Tether | 法定通貨他 | 6 | Ethereum | 流動性最大・中央集権的 |
| DAI | MakerDAO | 暗号資産担保 | 18 | Ethereum | 分散型・過担保設計 |

### なぜ decimals が 6 と 18 で違うのか

USDC/USDT は設計上「精度は $0.000001 で十分」として 6 decimals を選んだ。
DAI/JPYC は ETH と同じ 18 decimals を採用し、コントラクト間の計算で単位を揃えやすくした。

---

## 4. decimals の罠

ERC-20 コントラクト内の残高は常に **整数（uint256）** で管理される。`1 USDC` は内部では `1_000_000`。

```
1 USDC = 1_000_000      (10^6)
1 JPYC = 1_000_000_000_000_000_000  (10^18)
```

### なぜ整数で管理するのか

EVM は浮動小数点演算をサポートしない。浮動小数点は丸め誤差が発生するため、金融計算には不適切。
整数に固定してすべての演算を整数で行うことで精度を保証する。

### Java での扱い方

| 型 | 用途 |
|---|---|
| `BigInteger` | オンチェーンの生値（rawAmount）|
| `BigDecimal` | ユーザー向け表示値（humanAmount）|

```java
// 変換例（TokenAmountConverter）
BigDecimal human = new BigDecimal(rawAmount).movePointLeft(decimals);
// 1_000_000 → 1.0（USDC, decimals=6）

BigInteger raw = humanAmount.movePointRight(decimals).toBigIntegerExact();
// 1.5 → 1_500_000（USDC, decimals=6）
```

`toBigIntegerExact()` は端数が残った場合に例外を投げるため、トークンの精度を超えた入力を防げる。

---

## 5. eth_call による残高照会の仕組み

残高照会（`balanceOf`）はチェーンの状態を変更しない「読み取り専用」の呼び出しのため、トランザクションではなく `eth_call` を使う。

```
1. ABI エンコード
   balanceOf(0xabc...)
   → 0x70a08231000000000000000000000000abc...

2. eth_call で JSON-RPC 送信
   { "method": "eth_call", "params": [{ "to": contractAddress, "data": encodedData }] }

3. レスポンスを ABI デコード
   0x000...00000000000000000DE0B6B3A7640000
   → BigInteger(10^18) → 1 JPYC
```

ガス代は不要で、ノードがローカルでシミュレーション実行して結果を返す。

---

## まとめ

| 項目 | 実装内容 |
|---|---|
| ERC-20 ABI | `src/main/resources/abi/erc20.json` |
| 残高照会 API | `GET /api/v1/chain/token-balance?address=0x...&token=JPYC` |
| decimals 変換 | `TokenAmountConverter.toHuman()` / `toRaw()` |
| アドレス正規化 | EIP-55 チェックサム（`Keys.toChecksumAddress()`）|
| エラー処理 | IOException → `ChainCommunicationException` → 502 |
