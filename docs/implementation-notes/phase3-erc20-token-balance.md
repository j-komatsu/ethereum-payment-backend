# Phase 3 実装ノート — ERC-20 トークン残高 API

## 判断・変更・妥協点

### 1. Web3j CLI によるバインディング生成をしない判断

**判断:** ABI から Java クラスを自動生成する代わりに、`FunctionEncoder` / `FunctionReturnDecoder` を直接使った。

**理由:**
- Web3j CLI のインストールが必要で CI/CD 環境への組み込みが煩雑
- `balanceOf` 1 関数だけなら手書きの方がシンプル
- ABI エンコード/デコードの仕組みを理解する教育的価値がある

**将来:** Phase 4 以降で Transfer イベントのデコードなど複数の関数が必要になったら、`org.web3j:contracts` の既製バインディングか Gradle プラグインでの自動生成を検討する。

---

### 2. `eth_call` で `Credentials` を不要にした設計

**判断:** `ERC20.load()` の代わりに `Transaction.createEthCallTransaction()` を使い、Credentials を一切持たない設計にした。

**理由:**
- `ERC20.load()` は `Credentials` または `TransactionManager` が必要
- 読み取り専用の `balanceOf` にダミー秘密鍵を埋め込むのは CLAUDE.md の「秘密鍵をサーバーサイドに埋め込まない」ルールに抵触する可能性がある
- `eth_call` は状態変更しないため、送信者アドレスはコントラクトの処理結果に影響しない（`walletAddress` を `from` に指定しているが、これはガス計算用のヒントであり署名は不要）

---

### 3. `TokenAmountConverter` をユーティリティクラスとして設計

**判断:** `static` メソッドのみを持つ final クラス（インスタンス化不可）として設計した。

**理由:**
- 純粋な変換ロジックで副作用がなく、Spring Bean にする必要がない
- テストが簡単（モック不要）
- `toRaw()` で `toBigIntegerExact()` を使い、精度超過を例外で検知できる

**妥協点:** `toRaw()` で `ArithmeticException` を投げるが、呼び出し元でのハンドリング方針は未定。Phase 4 で決済金額の検証に使う際に再考する。

---

### 4. `StablecoinType` の invalid 値に対するエラーレスポンス

**発見:** `@RequestParam StablecoinType token` に無効な文字列（例: `UNKNOWN`）を渡すと、Spring MVC が `MethodArgumentTypeMismatchException` を投げる。

**現状:** `GlobalExceptionHandler` に専用ハンドラがなく、Spring のデフォルトで 400 Bad Request になる（動作としては正しい）。

**改善余地:** エラーメッセージが英語のデフォルト文字列になるため、`MethodArgumentTypeMismatchException` ハンドラを追加して日本語メッセージにできる。現フェーズでは省略。

---

### 5. 却下した選択肢

| 選択肢 | 却下理由 |
|---|---|
| `org.web3j:contracts` の `ERC20` クラスを使う | `Credentials` が必要でダミー秘密鍵の埋め込みを避けたかった |
| `balanceOf` の結果を `BigDecimal` で直接返す | 型が変わると呼び出し元の実装に影響が大きい。文字列で返すことで柔軟性を確保 |
| `TokenAmountConverter` を Spring Bean にする | 副作用がないため不要。テスト容易性も static の方が高い |
