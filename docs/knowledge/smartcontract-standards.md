# スマートコントラクト規格ナレッジ

> 初回蓄積日: 2026-05-24 / 最終更新: 2026-05-26
> 関連フェーズ: Phase 5（ウォレット認証・事前承認決済）・Phase 6（自動購入・ストリーミング）

---

## 1. SIWE — Sign-In With Ethereum（EIP-4361）

### 概要

パスワード不要のウォレット認証規格。ユーザーが秘密鍵で署名するだけでログインできる。

### 仕組み

```
1. バックエンドがワンタイム nonce を生成
2. フロントがウォレットに EIP-4361 形式のメッセージを表示
3. ユーザーがウォレット（HashPort等）で署名（秘密鍵不要・ウォレット内部処理）
4. バックエンドが署名からアドレスを復元 → 一致すれば認証OK
5. JWT またはセッションを発行
```

### メッセージフォーマット（EIP-4361）

```
example.com wants you to sign in with your Ethereum account:
0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2

I accept the Terms of Service.

URI: https://example.com
Version: 1
Chain ID: 1
Nonce: 32891756
Issued At: 2021-09-30T16:25:24Z
```

### セキュリティポイント

| 脅威 | 対策 |
|---|---|
| リプレイ攻撃 | nonce の使い捨て（DB で検証済みフラグ）|
| フィッシング | domain と URI の検証（ウォレットが表示する domain と一致確認）|
| 有効期限切れ | `Expiration Time` フィールドで期限管理 |

### 従来認証との違い

| 項目 | パスワード認証 | SIWE |
|---|---|---|
| 認証器 | パスワード（サーバー管理） | 秘密鍵（ユーザー管理）|
| 漏洩リスク | DB流出でパスワード漏洩 | 秘密鍵はユーザー側のみ |
| フィッシング耐性 | 低 | 高（domain 検証）|
| ガス代 | 不要 | 不要（オフチェーン署名）|

### HashPort Wallet との相性

HashPort は EIP-4361 に準拠しているため、SIWE 認証をそのまま利用可能。

---

## 2. EIP-712 — 構造化データ署名

### 概要

単純な文字列ではなく、型付き構造化データを署名する規格。
EIP-2612 Permit などのベースとなる重要な規格。

### なぜ必要か

単純な `eth_sign` では何を署名しているか分かりにくく、フィッシング攻撃に脆弱。
EIP-712 では署名対象が「どのアプリの・どのコントラクトの・どのデータか」まで明示される。

### 構造

```
署名対象 = hash(0x1901 || domain_separator || hash(typed_data))
              ↑ EIP-712 固定プレフィックス（Web3j で手動実装する際は必須）

domain_separator:
  - name: "MyApp"
  - version: "1"
  - chainId: 1  ← チェーン偽装防止
  - verifyingContract: "0x..."  ← コントラクト偽装防止
```

### フィッシング耐性

ウォレットが `domain_separator` を画面表示するため、ユーザーが「どのサイトへの署名か」を確認できる。

---

## 3. EIP-2612 — Permit（ガスレス ERC-20 承認）

### 概要

ERC-20 の `approve()` をオフチェーン署名で代替できる規格。
ユーザーが ETH を持っていなくても承認ができる（ガスレス）。

### 従来の approve との比較

```
従来 approve:
  ユーザー → approve(spender, amount) TX送信 → ガス代が必要

EIP-2612 Permit:
  ユーザー → オフチェーン署名（ガス不要）
  バックエンド → permit(owner, spender, value, deadline, v, r, s) TX送信
  ※バックエンドがガスを負担（Paymaster でガスレスにも可能）
```

### 署名の構造

```
permit(
  address owner,    // 承認者（ユーザー）
  address spender,  // 引き落とし先（バックエンドのアドレス）
  uint256 value,    // 承認金額
  uint256 deadline, // 有効期限（UNIXタイムスタンプ）
  uint8 v, bytes32 r, bytes32 s  // EIP-712 署名
)
```

### JPYC との関係

**JPYC（JPYCv2）は EIP-2612 対応済み**。
コントラクトアドレス: `0xE7C3D8C9a439feDe00D2600032D5dB0Be71C3c29`（**Polygon mainnet**）
→ Phase 5 で即座に利用可能。

### リプレイ攻撃防止

| パラメーター | 役割 |
|---|---|
| `nonce` | コントラクトが管理するカウンター。使用済み署名の再利用を防止 |
| `deadline` | 期限切れ署名の悪用を防止。推奨: 現在時刻 + 20〜30分（短すぎると遅延で失効、長すぎると盗用リスク）|
| `chainId` (domain) | 他チェーンでの署名流用を防止 |

### 署名検証の注意点

`ecrecover`（`permit` 内部で使用）が `address(0)` を返した場合は**無効な署名**として必ず拒否すること。
Web3j での検証時も同様に address(0) チェックを実装する。

---

## 4. ERC-20 approve/transferFrom パターン

### 概要

ERC-20 の標準機能を使った「事前承認→即時引き落とし」パターン。

### フロー

```
Phase 1（承認）:
  ユーザー → approve(バックエンドアドレス, 10000 JPYC)
  ※ Permit（EIP-2612）を使えばガスレスで同等の承認が可能

Phase 2（決済時）:
  バックエンド → transferFrom(ユーザー, 受取アドレス, 1000 JPYC)
  ※ ユーザーの追加署名不要で即時決済
```

### allowance の仕組み

```
allowance[ユーザー][バックエンド] = 承認残高
transferFrom 実行時: allowance を減算
→ allowance が不足すると TX 失敗（revert）
```

### セキュリティ考慮点

- バックエンドが `transferFrom` を呼ぶため、**サーバー秘密鍵の管理が必須**
- 秘密鍵は環境変数で管理（`.env`・クラウドの Secret Manager）
- **絶対にコードにハードコードしない**
- 毎回 `allowance` 残高を確認してから実行

---

## 5. EIP-7702 — EOA スマートウォレット化

### 概要

通常の EOA（外部所有アカウント）に、一時的にスマートコントラクトのコードを付与できる規格。
Ethereum Pectra アップグレード（2025年）で導入。

### 何ができるか

- バッチトランザクション（複数操作を1TX で実行）
- スポンサー付きガス（Paymaster による代理ガス支払い）
- セッションキー（特定操作のみ許可する一時キー）
- ソーシャルリカバリー（秘密鍵を失っても復元可能）

### HashPort Wallet との関係

**HashPort は EIP-7702 を実装済み**（日本で先進的なウォレット）。
※ ユーザー数・セキュリティ実績は HashPort 公式情報に基づく。最新情報は公式サイトを確認すること。

→ HashPort ユーザーは EIP-7702 の恩恵を受けながら JPYC 決済が可能。

### EIP-4337（Account Abstraction）との違い

| 項目 | EIP-4337 | EIP-7702 |
|---|---|---|
| 対象 | スマートコントラクトウォレット専用 | EOA にも適用可能 |
| 既存ウォレットとの互換性 | 低（全面移行が必要）| 高（既存 EOA のまま使える）|
| 採用状況 | Biconomy・Safe 等が対応 | HashPort が対応済み |

---

## 6. EIP-4337 — Account Abstraction

### 概要

ガス代をユーザーの代わりにサービス側が支払う（Paymaster）仕組みを標準化。
スマートコントラクトウォレット向け。

### 主要コンポーネント

```
UserOperation（ユーザーの意図）
  → Bundler（TX をまとめてチェーンに送信）
  → EntryPoint コントラクト（処理の中心）
  → Paymaster（ガス代を代理支払い）
```

### Paymaster の役割

ガスを USDC/JPYC で支払う・または完全無料にするための仕組み。
→ ユーザーが ETH を持っていなくても操作できる。

### Phase 5-6 での活用

EIP-4337 の完全実装（Bundler 運用等）は Phase 7 以降に延期。
Phase 5-6 では HashPort の **EIP-7702 実装**によりガスレスに近いユーザー体験を実現する
（EIP-7702 と EIP-4337 は設計思想が異なり、HashPort は EIP-4337 Bundler とは独立して動作する）。

---

## 7. Sablier — ストリーミング決済プロトコル

### 概要

ERC-20 トークンを「毎秒単位」で送金し続けるプロトコル。
月額サブスクリプションや時間単位課金に最適。

### 主要コントラクト

| 種別 | 特徴 |
|---|---|
| **Sablier Lockup Linear** | 期間固定の線形ストリーム（入門に最適）|
| **Sablier Lockup Dynamic** | 非線形カーブ（段階的増加など）|
| **Sablier Flow** | オープンエンド（いつでも停止・変更可能）|

### Sablier Flow の仕組み

```
1. 送金者（ユーザー）が JPYC を Sablier コントラクトに預ける
2. 受取人（マーチャント）は毎秒 JPYC を受け取れる状態になる
3. 受取人はいつでも引き出し（withdraw）可能
4. 送金者はいつでも一時停止・追加入金・停止が可能
```

### ユースケース

| ユースケース | レート例 |
|---|---|
| 月額サブスク（1万円/月）| 10,000 JPYC ÷ 30日 ÷ 86,400秒 ≈ 0.00000386 JPYC/秒 |
| 時間課金（1000円/時）| 1,000 JPYC ÷ 3,600秒 ≈ 0.000278 JPYC/秒 |

### JPYC との相性

JPYC は ERC-20 準拠のため、Sablier はそのまま利用可能。
EIP-2612 Permit で事前承認 → Sablier へのデポジットをガスレスで実行できる。

### バックエンド実装のポイント

- Sablier コントラクト ABI を Web3j でラップ
- ストリーム ID を DB で管理（`stream_id` カラム追加）
- ストリーム残高は `withdrawable_amount(stream_id)` で取得
- 定期的にストリーム状態をポーリング（`@Scheduled`）

---

## 8. OpenZeppelin — 標準コントラクトライブラリ

### 概要

セキュリティ監査済みのスマートコントラクト実装集。
ERC-20・ERC-721・Ownable・AccessControl などを提供。

### 決済システムで関連する主要コントラクト

| コントラクト | 用途 |
|---|---|
| `ERC20` | 標準トークン実装 |
| `ERC20Permit` | EIP-2612 Permit 機能追加（JPYC が採用）|
| `Ownable` | オーナー管理（管理者機能）|
| `AccessControl` | ロールベースアクセス制御 |
| `ReentrancyGuard` | 再入攻撃防止 |
| `Pausable` | 緊急停止機能 |

### JPYCv2 との関係

JPYCv2 は `ERC20Permit`（EIP-2612）を継承しており、
OpenZeppelin の実装をベースにしていると推定される。

---

## 9. JPYC 対応規格マトリクス

> JPYC コントラクトアドレス: `0xE7C3D8C9a439feDe00D2600032D5dB0Be71C3c29`（**Polygon mainnet**、decimals=18）

| 規格 | 対応状況 | 活用フェーズ |
|---|---|---|
| ERC-20 | ✅ 対応 | Phase 3〜 |
| EIP-2612 Permit | ✅ 対応済み | Phase 5 |
| SIWE (EIP-4361) | 🔧 フロント連携が必要 | Phase 5 |
| EIP-712 | ✅ Permit のベース | Phase 5 |
| Sablier Flow | ✅ ERC-20 なので利用可能 | Phase 6 |
| EIP-4337 | ⚠️ ウォレット側が対応必要 | 将来 |
| EIP-7702 | ✅ HashPort 対応済み | Phase 5〜 |

---

## 10. 実装優先順位

```
1. ERC-20 基礎 → Phase 3 で実装 ✅
2. Transfer イベント監視 → Phase 4 で実装
3. SIWE 認証 → Phase 5 Day 21
4. EIP-2612 Permit → Phase 5 Day 22（JPYC 対応済みなので直結）
5. approve/transferFrom 決済 → Phase 5 Day 23
6. Sablier ストリーミング → Phase 6 Day 26
7. EIP-4337 Paymaster → 将来（Phase 7 以降）
```

---

## 参考リンク

- [EIP-4361 (SIWE)](https://eips.ethereum.org/EIPS/eip-4361)
- [EIP-712 (構造化データ署名)](https://eips.ethereum.org/EIPS/eip-712)
- [EIP-2612 (Permit)](https://eips.ethereum.org/EIPS/eip-2612)
- [EIP-7702 (EOA スマートウォレット化)](https://eips.ethereum.org/EIPS/eip-7702)
- [EIP-4337 (Account Abstraction)](https://eips.ethereum.org/EIPS/eip-4337)
- [Sablier ドキュメント](https://docs.sablier.com/)
- [JPYC 公式サイト](https://jpyc.jp/)
- [HashPort Wallet](https://hashport.io/)
