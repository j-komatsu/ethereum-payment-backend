# レポート05 — ウォレット認証とスマートコントラクト決済

## はじめに

Phase 5 では「ウォレット署名でログインする（SIWE）」と「ガスレスで ERC-20 を送金する（EIP-2612 Permit）」を実装した。  
本レポートでは、なぜこの設計になっているかを暗号理論から API 設計まで一貫して整理する。

---

## 1. SIWE（Sign-In With Ethereum）とは

### なぜウォレット署名でログインできるのか

Ethereum ウォレットは「秘密鍵」を持っている。秘密鍵の所有者だけが特定のデジタル署名を作れる。

```
秘密鍵 → 署名生成（Sign）
公開鍵 → 署名検証（Verify）→ このアドレスの持ち主が署名したと証明できる
```

これはパスワード認証の「パスワードを知っているか」と同じ構造だが、**サーバーに秘密を送らない**点が根本的に違う。

```
パスワード認証:
  ユーザー → パスワードを送信 → サーバーが検証
  （サーバーが漏洩するとパスワードが盗まれる）

SIWE:
  サーバー → ナンスを渡す → ユーザーがウォレットで署名 → サーバーが公開鍵で検証
  （秘密鍵はユーザーの端末から外に出ない）
```

### EIP-4361 の署名フォーマット

SIWE の署名対象メッセージには決まったフォーマットがある。

```
localhost wants you to sign in with your Ethereum account:
0xabc...123

Sign in to Web3Pay

URI: http://localhost/api/v1/auth
Version: 1
Chain ID: 137
Nonce: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
Issued At: 2026-05-28T00:00:00Z
```

各フィールドの意味:

| フィールド | 役割 |
|---|---|
| `localhost wants you to sign in...` | どのサービスがログインを要求しているか（フィッシング防止）|
| `Nonce` | ワンタイム使い捨て文字列（リプレイアタック防止）|
| `Chain ID` | どのチェーンでの署名か（Polygon=137, Ethereum=1）|
| `Issued At` | 署名の発行時刻（有効期限管理）|

### なぜ Nonce が必要か

Nonce がなければ「過去の署名」を再利用できてしまう。

```
悪意ある攻撃者が過去の署名を盗む
    ↓
同じ署名をサーバーに送って成り済ます（リプレイアタック）

Nonce があると：
    一度使った Nonce は DB に「使用済み」として記録
    → 同じ署名を再送しても「Nonce already used」で拒否
```

### 実装の流れ

```
① GET /api/v1/auth/nonce
   サーバーが 32文字のランダム Nonce を生成・DB 保存・返却

② フロント: eth_signTypedData or personal_sign でウォレットが署名

③ POST /api/v1/auth/verify
   { message, signature, address }
   → サーバーが署名を検証（ecrecover）→ アドレス一致 → JWT 発行

④ 以降のリクエストは JWT を Bearer トークンとして使用
```

---

## 2. EIP-191 と署名の仕組み

SIWE で使う署名は **EIP-191** という規格に準拠している。

### なぜプレフィックスが必要か

素のメッセージに署名すると、同じ秘密鍵で「Ethereum トランザクション」に見せかけたメッセージに誤って署名させられる危険がある。

```
悪意ある攻撃者:
  「このメッセージに署名して」→ 実は ETH 送金トランザクション
```

EIP-191 は署名対象に以下のプレフィックスを付ける：

```
"\x19Ethereum Signed Message:\n{メッセージ長}{メッセージ}"
```

`\x19` はどのトランザクション形式とも一致しない（RLP 形式は `0xc0〜` から始まる）ため、「これはトランザクションではなくメッセージの署名だ」と区別できる。

### 署名の数学

ECDSA（楕円曲線デジタル署名）の署名は 3 つの値から成る:

```
r: 32 バイト（署名の一部）
s: 32 バイト（署名の一部）
v: 1 バイト（27 または 28、リカバリID）
```

検証時：
```java
// Web3j の実装
BigInteger pubKey = Sign.signedPrefixedMessageToKey(message.getBytes(), sigData);
String recovered = "0x" + Keys.getAddress(pubKey);
// recovered == 署名したアドレス ならば本人確認完了
```

### v 値の罠（Java の signed byte）

Java の `byte` は -128〜127 の符号付き整数。MetaMask は v=27 or 28 を返すが、一部のウォレットは v=0 or 1 を返す。

```java
// 間違い（符号付きで読む）
byte v = sigBytes[64]; // 28 → 28、27 → 27（OK）、でも -29 になる場合がある

// 正しい（符号なしで読む）
int vInt = sigBytes[64] & 0xFF; // 常に 0〜255 の範囲
if (vInt < 27) vInt += 27;      // 0/1 → 27/28 に正規化
```

---

## 3. EIP-712 型付き署名とは

### なぜ EIP-191 だけでは不十分か

SIWE は EIP-191 で事足りる（テキストメッセージの署名）。  
しかし Permit のような「構造化データ（owner, spender, value, nonce, deadline）への署名」では、より厳格な仕組みが必要。

問題: ウォレットが素の hex を表示しても何に署名しているかわからない。

```
0x1234abcd5678ef90...（これが何か全くわからない）
```

EIP-712 を使うと MetaMask はこう表示する:

```
┌─────────────────────────────────┐
│ Permit                          │
│ owner:    0xabc...              │
│ spender:  0xdef...              │
│ value:    1000000               │
│ nonce:    0                     │
│ deadline: 1748400000            │
└─────────────────────────────────┘
```

ユーザーが「何に署名しているか」を確認できる。

### ドメインセパレータの役割

EIP-712 は署名に**ドメイン情報**を埋め込む。これにより「JPYC の permit 署名を USDC で使い回す」攻撃を防ぐ。

```
domainSeparator = keccak256(
  EIP712_DOMAIN_TYPEHASH,
  keccak256("JPY Coin"),  // name: コントラクト固有
  keccak256("1"),          // version: コントラクト固有
  137,                     // chainId: Polygon=137
  0x431D5d...              // verifyingContract: JPYCのアドレス
)
```

コントラクトが異なる → ドメインセパレータが異なる → 署名が無効になる。

### Digest の計算

```
digest = keccak256(
  "\x19\x01"          ← EIP-712 プレフィックス（EIP-191 との区別）
  + domainSeparator   ← どのコントラクトへの署名か
  + structHash        ← 何を承認するか（owner, spender, value, nonce, deadline）
)
```

サーバー側でも同じ digest を計算し、`ecrecover(digest, v, r, s) == ownerAddress` を確認する。

---

## 4. EIP-2612 Permit の仕組み

### 通常の approve + transferFrom との比較

**通常フロー（2 トランザクション）:**

```
① ユーザー → approve(spender, amount) TX → ガス必要（POL）
② スペンダー → transferFrom(user, receiver, amount) TX → ガス必要（POL）

問題: ユーザーが POL を持っていないと step ① ができない
```

**Permit フロー（1 署名 + 2 トランザクション）:**

```
① ユーザー → eth_signTypedData_v4（オフチェーン署名） → ガスゼロ
② スペンダー → permit(user, spender, amount, deadline, v, r, s) TX → スペンダーがガス負担
③ スペンダー → transferFrom(user, receiver, amount) TX → スペンダーがガス負担

メリット: ユーザーは POL を一切持たなくていい
```

### コントラクト側の permit() の動作

```solidity
function permit(
    address owner,
    address spender,
    uint256 value,
    uint256 deadline,
    uint8 v, bytes32 r, bytes32 s
) external {
    require(block.timestamp <= deadline); // 期限切れチェック
    
    // ユーザーが署名した digest を再計算
    bytes32 digest = keccak256(abi.encodePacked(
        "\x19\x01",
        DOMAIN_SEPARATOR,
        keccak256(abi.encode(PERMIT_TYPEHASH, owner, spender, value, nonces[owner]++, deadline))
    ));
    
    // 署名から owner アドレスを復元
    address recoveredOwner = ecrecover(digest, v, r, s);
    require(recoveredOwner == owner); // 本人確認
    
    // allowance を設定（approve と同じ効果）
    _allowances[owner][spender] = value;
}
```

サーバー（Java）側でも同じ計算を事前に行い、ガスを使う前に署名を検証する。

### Nonce がリプレイアタックを防ぐ仕組み

```
ユーザーの nonce = 5 で署名
    ↓
permit() 実行 → コントラクトが nonce を 5→6 にインクリメント
    ↓
同じ署名（nonce=5）を再送
    ↓
コントラクト: 現在の nonce は 6 → digest が一致しない → revert
```

---

## 5. 実装上の判断と学び

### @Transactional とブロッキング I/O の相性

`execute()` は DB 読み書きと RPC 呼び出し（最大 240 秒）が混在する。  
当初 `@Transactional` で全体を包んでいたが、これは誤り。

```
@Transactional execute()
  └── getActiveOrder() → DB コネクション取得
  └── waitForReceipt() → 120 秒ブロック（コネクション保持中）
  └── waitForReceipt() → 120 秒ブロック（コネクション保持中）
  └── save() → 書き込み
```

10 件の同時リクエストで最大 240 秒 × 10 = コネクションプールが枯渇。

**解決策:** `PROCESSING` ステータスで原子的状態遷移 + `@Transactional` を個別の短命操作に分割。

```java
// PENDING → PROCESSING を原子的に（updateStatusConditionally）
// RPC 呼び出しはトランザクション外
// PROCESSING → CONFIRMED を原子的に（save）
```

### EIP-1559 と Polygon

Polygon は EIP-1559 対応チェーン（London ハードフォーク適用済み）。  
レガシーな `eth_gasPrice` を使うと、RPC が高い値を返しすぎて過剰なガス代を払うリスクがある。

```java
// レガシー（非推奨）
BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

// EIP-1559（推奨）
BigInteger maxPriorityFee = web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas();
BigInteger baseFee = latestBlock.getBaseFeePerGas();
BigInteger maxFeePerGas = baseFee.multiply(BigInteger.TWO).add(maxPriorityFee);
```

---

## 6. セキュリティ設計のポイント

| リスク | 対策 |
|---|---|
| 秘密鍵のコミット | `${PERMIT_SPENDER_PRIVATE_KEY}` 環境変数のみ |
| 署名のリプレイ | SIWE: Nonce 使い捨て / Permit: on-chain nonce |
| 二重実行 | PROCESSING ステータスへの CAS（Compare-And-Swap）|
| 期限切れ署名受け入れ | `deadline <= Instant.now()` を RPC 前に検証 |
| エラー情報漏洩 | 汎用メッセージ返却・詳細はログのみ |
| アドレス検証 | `^0x[0-9a-fA-F]{40}$` パターン必須 |
| txHash 検証 | `^0x[0-9a-fA-F]{64}$` — DB 保存前に確認 |

---

## まとめ

```
SIWE（EIP-4361 + EIP-191）
  → ウォレット署名 = パスワードレス認証
  → 秘密鍵は端末の外に出ない

EIP-712（型付き署名）
  → 署名対象の構造を人間が読める形式で表示
  → ドメインセパレータでコントラクト間の誤用を防ぐ

EIP-2612 Permit（EIP-712 の応用）
  → approve をオフチェーン署名で代替
  → ユーザーのガス代ゼロを実現
  → スペンダーがガスを代理負担する設計
```

これらはいずれも「**秘密鍵を外に出さずに権限を委譲する**」という Ethereum の哲学の延長線上にある。
