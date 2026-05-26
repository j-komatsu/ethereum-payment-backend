# ガス代リファレンス：誰が・何に・いくら払うか

> 初回作成: 2026-05-26
> 対象チェーン: **Polygon mainnet（chainId=137）/ Polygon Amoy testnet（chainId=80002）**
> ガス代通貨: **POL（旧MATIC）** ← ETH ではない

---

## 1. 大原則

| 原則 | 説明 |
|---|---|
| **読み取り操作はタダ** | `eth_call`・`eth_getLogs` などの読み取りは TX を発行しないためガス代ゼロ |
| **書き込み操作は POL が必要** | オンチェーンの状態を変える TX は発信者が POL でガスを払う |
| **署名はタダ** | SIWE/EIP-712 のオフチェーン署名はガスを消費しない |
| **バックエンドが TX を出す = バックエンドウォレットに POL が必要** | Phase 5 以降はバックエンドが TX を送信するため POL を保有すること |

---

## 2. 操作別ガス代一覧

### 読み取り操作（ガスなし）

| 操作 | 呼び出し | 発行者 | コスト |
|---|---|---|---|
| ETH 残高取得 | `eth_getBalance` | バックエンド | **無料** |
| ERC-20 残高取得 | `eth_call` → `balanceOf(address)` | バックエンド | **無料** |
| allowance 確認 | `eth_call` → `allowance(owner, spender)` | バックエンド | **無料** |
| nonces 取得（Permit 用） | `eth_call` → `nonces(owner)` | バックエンド | **無料** |
| Transfer イベント取得 | `eth_getLogs` | バックエンド | **無料** |
| ブロック番号取得 | `eth_blockNumber` | バックエンド | **無料** |
| ヘルスチェック | `eth_blockNumber` | バックエンド | **無料** |

### 書き込み操作（POL 必要）

| 操作 | TX 内容 | ガス支払者 | Polygon 目安コスト |
|---|---|---|---|
| JPYC 送金（Push型） | `transfer(to, amount)` | **ユーザー** | 〜0.005 POL |
| ERC-20 approve | `approve(spender, amount)` | **ユーザー** | 〜0.003 POL |
| Permit 提出 | `permit(owner, spender, value, deadline, v, r, s)` | **バックエンド** | 〜0.005 POL |
| transferFrom 実行 | `transferFrom(from, to, amount)` | **バックエンド** | 〜0.005 POL |
| Sablier 入金 | Sablier コントラクトへの JPYC 預け入れ | **ユーザー** | 〜0.01 POL |
| Sablier 引き出し | `withdraw(streamId, amount, recipient)` | **受取人（マーチャント）** | 〜0.005 POL |
| 自動購入ジョブ | `transferFrom(ユーザー, マーチャント, 月額分)` | **バックエンド** | 〜0.005 POL |

> **目安について:** Polygon の平均ガス価格は 30〜100 Gwei 程度。ERC-20 TX のガス上限は通常 65,000〜80,000 gas。
> `0.005 POL ≒ 65,000 gas × 70 Gwei`（2025年時点）。POL が 200円 程度なら 1 TX あたり 0.1〜0.5円 程度。

### オフチェーン操作（ガスゼロ・署名のみ）

| 操作 | 説明 | コスト |
|---|---|---|
| SIWE 署名（EIP-4361） | ウォレットでログイン用メッセージに署名 | **ゼロ** |
| Permit 署名（EIP-712） | JPYC の approve に相当するオフチェーン署名 | **ゼロ（ユーザー側）** |
| EIP-712 任意署名 | 任意の構造化データへの署名 | **ゼロ** |

---

## 3. フェーズ別ガス負担まとめ

### Phase 4：Transfer イベント監視（入金検知）

```
ユーザーが JPYC を送金する（Push型）:
  ユーザー → transfer(receiverAddress, amount) ← ユーザーが POL を支払う

バックエンドがイベントを監視する:
  バックエンド → eth_getLogs でポーリング ← ガスなし（読み取りのみ）

バックエンドが DB を更新する:
  バックエンド → PaymentOrder を CONFIRMED に変更 ← DB 操作のみ・ガスなし
```

**Phase 4 のポイント: バックエンドは POL を持つ必要がない**

---

### Phase 5：SIWE 認証 + Permit + transferFrom（Pull型）

```
ユーザーが SIWE でログイン:
  ユーザー → ウォレットでオフチェーン署名 ← ガスなし

バックエンドが nonce を発行:
  バックエンド → DB に nonce を保存 ← ガスなし（オフチェーン）

ユーザーが Permit 署名:
  ユーザー → EIP-712 署名（オフチェーン） ← ガスなし

バックエンドが permit() TX を送信:
  バックエンド → permit(owner, spender, value, deadline, v, r, s) ← バックエンドが POL を支払う

バックエンドが transferFrom() TX を送信:
  バックエンド → transferFrom(ユーザー, 受取アドレス, 金額) ← バックエンドが POL を支払う
```

**Phase 5 のポイント: ユーザーはガス不要。バックエンドウォレットに POL が必要**

---

### Phase 5-F：フロントエンド

```
ウォレット接続: ガスなし
残高照会: ガスなし（eth_call）
SIWE ログイン: ガスなし（オフチェーン署名）
Permit 署名（EIP-712）: ガスなし（オフチェーン署名）
↓
これらは Phase 5 バックエンドと同じ → バックエンドが POL 負担
```

---

### Phase 6：Sablier + 自動購入

```
Sablier へ JPYC を預ける（ユーザー）:
  ユーザー → depositToSablier(amount) ← ユーザーが POL を支払う
  ※ Permit 署名で JPYC の approve をガスレス化できる（ただし Sablier へのデポジット TX 自体は POL 必要）

Sablier から JPYC を引き出す（マーチャント）:
  マーチャント → withdraw(streamId, amount, recipient) ← マーチャントが POL を支払う

自動購入ジョブ（@Scheduled）:
  バックエンド → allowance 確認（eth_call, ガスなし）
  バックエンド → transferFrom(ユーザー, 受取, 月額分) ← バックエンドが POL を支払う
```

---

## 4. よくある誤解

### ❌「Permit はガスレス」

**正確には:** ユーザー側の署名はガスレス。  
`permit()` TX の送信はバックエンドが行うため、**バックエンドウォレットには POL が必要**。

```
✅ 正確な表現: 「ユーザーはガス不要。バックエンドが POL でガスを負担する」
❌ 誤解を招く表現: 「Permit はガスレス決済」
```

### ❌「JPYC でガスを払える」

**正確には:** Polygon のガス代通貨は **POL（旧MATIC）** のみ。  
JPYC は ERC-20 トークンであり、ガス代の支払いには使えない（Account Abstraction + Paymaster を使わない限り）。

### ❌「eth_call にもガスがかかる」

**正確には:** `eth_call` はノードでシミュレーション実行されるだけで、TX をブロックチェーンに記録しない。  
**ガスコストはゼロ。** ただし Infura/Alchemy の API 呼び出し回数は消費する。

### ❌「署名にガスがかかる」

**正確には:** SIWE・EIP-712 の署名はウォレット内部でのローカル計算。  
TX を送信していないためオンチェーンに記録されず、ガスは不要。

---

## 5. バックエンドウォレットの POL 管理

Phase 5 以降、バックエンドは `permit()` と `transferFrom()` の TX を送信するため、spender ウォレットに POL が必要。

| 環境 | POL の調達方法 |
|---|---|
| Hardhat fork（開発） | `hardhat_impersonateAccount` で大口ホルダーを偽装して残高操作（実コストなし）|
| Polygon Amoy（testnet） | Polygon Faucet（https://faucet.polygon.technology/）で無料取得 |
| Polygon mainnet（本番） | 取引所で POL を購入して送金 |

### 必要な POL の目安（mainnet）

1 TX ≒ 0.005 POL として試算（POL = 200円 換算）:

| 想定取引数/日 | 必要 POL/月 | 月額コスト目安 |
|---|---|---|
| 10件 | 3 POL | 約 600円 |
| 100件 | 30 POL | 約 6,000円 |
| 1,000件 | 300 POL | 約 60,000円 |

> ガス価格はネットワーク混雑度により変動。上記は参考値。

---

## 6. `.env.example` に必要な設定

```bash
# Phase 5 以降で必要（spender ウォレット）
SPENDER_WALLET_ADDRESS=0x...          # spender ウォレットのアドレス
SPENDER_PRIVATE_KEY=                  # ← 絶対に値をコミットしない
```

> SPENDER_PRIVATE_KEY は `.gitignore` 対象の `.env` にのみ記載する。
> `.env.example` にはキー名のみ記載し、値は空のままにすること。
