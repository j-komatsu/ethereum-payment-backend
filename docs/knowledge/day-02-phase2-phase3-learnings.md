# Day 02：Phase2・Phase3 の実装から得た知見

**日付:** 2026-05-25（Phase 2・3 完了時点でまとめて記録）
**フェーズ:** Phase 2（Web3j接続・ETH残高API）/ Phase 3（ERC-20トークン残高API）

---

## Phase 2 で得た知見

### JPYC コントラクトアドレスの更新（重大）

Phase 2 終了後、JPYC のコントラクトアドレスが旧バージョン（Prepaid）になっていたことが発覚した。

| 種別 | アドレス | 状態 |
|---|---|---|
| 旧アドレス（JPYC Prepaid） | `0x431D5dfF03120AFA4bDf332c61A6e1766eF37BDB` | **廃止済み** |
| 新アドレス（JPYC EX） | `0xE7C3D8C9a439feDe00D2600032D5dB0Be71C3c29` | **現行・正規** |

- チェーン: Polygon Mainnet（chainId=137）
- decimals: 18
- ガス代通貨: POL（旧MATIC）
- EIP-2612 Permit: 対応済み

> ⚠️ コントラクトアドレスはGemini等の外部AIで確認する際、混同が起きやすい。
> 必ずPolygonscanで直接確認すること。

---

### マルチチェーン Web3j 設計（ChainRegistry）

単一の `Web3j` Bean で JPYC（Polygon）と USDC/USDT/DAI（Ethereum）を扱おうとすると、
ノードが混在して誤ったチェーンに問い合わせてしまう。

**解決策:** `ChainRegistry` でchainId → Web3j インスタンスをマッピング。

```java
// ChainRegistry: chainId で正しいノードを選択
public Web3j resolve(int chainId) {
    Web3j web3j = nodes.get(chainId);
    if (web3j == null) throw new UnsupportedOperationException("未対応のチェーンID: " + chainId);
    return web3j;
}
```

`StablecoinType` に `chainId` フィールドを持たせることで、
`token.getChainId()` → `chainRegistry.resolve()` の流れが自然になる。

---

### `eth_call` の from アドレスは null にする

読み取り専用の `balanceOf` 呼び出しで `from` にウォレットアドレスを入れていたが、
これは不要かつ誤解を招く。

```java
// 誤り: from にウォレットアドレスを指定
Transaction.createEthCallTransaction(walletAddress, contractAddress, encodedFunction)

// 正しい: from は null
Transaction.createEthCallTransaction(null, contractAddress, encodedFunction)
```

`eth_call` は状態変更しないため、from は署名不要。ガス計算ヒントとしても不要。

---

### `eth_call` のエラーハンドリング順序

```java
// 必ずこの順序でチェックする
if (response.hasError()) {         // 1. JSONRPCレベルのエラー
    throw new ChainCommunicationException(...);
}
if (response.isReverted()) {       // 2. コントラクトのrevert
    throw new ChainCommunicationException(...);
}
if (result.isEmpty()) {            // 3. デコード結果が空
    throw new ChainCommunicationException(...);
}
return ((Uint256) result.get(0)).getValue();  // 4. 正常値
```

revertReason はログにのみ出力し、レスポンスには含めない（内部情報漏洩防止）。

---

### `MethodArgumentTypeMismatchException` は別途ハンドリングが必要

`@RequestParam StablecoinType token` に `UNKNOWN` などの無効値を渡すと、
Spring MVC が `MethodArgumentTypeMismatchException` を投げる。
`GlobalExceptionHandler` に専用ハンドラがないとデフォルトの英語エラーになる。

また `ex.getValue()`（ユーザー入力値）をレスポンスに含めてはいけない（入力値リフレクション）。

```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    // ex.getValue() をレスポンスに含めない
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
        "パラメータ '" + ex.getName() + "' の値が不正です");
}
```

---

### `TokenAmountConverter.toHuman()` の null・負値ガード

オンチェーンの `balanceOf` は理論上負値を返さないが、
ユーティリティメソッドとして防御的にガードを入れておくべき。

```java
public static BigDecimal toHuman(BigInteger rawAmount, int decimals) {
    if (rawAmount == null) throw new IllegalArgumentException("rawAmount must not be null");
    if (rawAmount.signum() < 0) throw new IllegalArgumentException("rawAmount must not be negative");
    return new BigDecimal(rawAmount).movePointLeft(decimals).stripTrailingZeros();
}
```

---

## 📝 Phase 2・3 振り返り

### ✅ よかったこと

- ChainRegistry の導入でマルチチェーン対応が綺麗に設計できた
- `eth_call` のエラーハンドリングを3段階で実装し、堅牢になった
- `TokenAmountConverter` を static ユーティリティにしたことでテストが書きやすかった
- レビューで revertReason 漏洩・from アドレス問題など実装レベルの問題を全て検出できた

### 🔧 改善が必要なこと

- JPYC アドレスの確認を実装前にやるべきだった（後から発覚）
- `application.yml` に polygon-endpoint / ethereum-endpoint を最初から入れておくべきだった
- ドキュメントPR のレビューをスキップしてしまった（後述）

### ⚠️ 注意するべきこと

- **Polygon のガス代は POL（旧MATIC）**。ETH ではない。バックエンドウォレットが POL を保有している必要がある
- **ERC-20 の balanceOf は常に `uint256`**（負値はありえない）が、コード上のガードは書いておく
- **`application.yml` の `web3j.polygon-endpoint` を環境変数で明示する**。フォールバックに頼ると両チェーンが同じノードに向く設定ミスが起きる
