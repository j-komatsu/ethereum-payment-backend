# レポート05F — フロントエンド・送金UI設計

## はじめに

Phase 5-F では、実際に送金操作をブラウザ上で行えるフロントエンドを実装する。
本レポートでは実装前に把握すべき概念として「DApp」「WalletConnect」「CPM/MPM」「決済方式」を整理する。

---

## 1. DApp（分散型アプリ）とは何か

### 定義

**Decentralized Application** の略。「サーバーの代わりにブロックチェーンで動くアプリ」が本来の定義だが、
現場では「ウォレットと通信するウェブアプリ全般」を指す広い意味でも使われる。

### 普通のアプリとの違い

```
【普通のアプリ（例：銀行アプリ）】
ユーザー → 中央サーバー → データベース
             ↑
          運営が口座凍結・サービス停止できる
          中身は外から見えない

【DApp（例：Uniswap）】
ユーザー → ブロックチェーン上のコントラクト
             ↑
          コントラクト自体は誰も書き換えられない
          コードは全員が見える（オープンソース）
```

| 比較項目 | 普通のアプリ | DApp（完全分散型） |
|---|---|---|
| ルールを決める | 会社 | コード（自動） |
| 止められるか | できる | コントラクト自体は不可※ |
| 残高を管理するのは | 会社のDB | ブロックチェーン |
| コードを見られるか | 見えない | 全員見える |

> ※ `Ownable`（管理者機能）を実装したコントラクトは管理者が一部操作を止められる。
> また管理者キーの漏洩リスクも存在する。「誰も止められない」は理想形の表現。

### このプロジェクトの位置づけ

```
このプロジェクトの構成:
  ├── Next.js フロント          ← "DApp" と呼ぶ（広い意味）
  ├── Spring Boot バックエンド  ← 中央サーバーあり ← ここが停止すれば決済も止まる
  └── Polygon ブロックチェーン  ← JPYC コントラクト
```

厳密には「完全な DApp」ではなく「Web3対応ウェブアプリ」。
バックエンドがある以上、サービス停止・注文拒否は運営側が行える。
コントラクトレイヤーのみが「誰も止められない」部分。

---

## 2. WalletConnect とは何か

### 一言で言うと

「**ブラウザ（DApp）とスマホのウォレットアプリを安全につなぐ通信プロトコル**」。

### なぜ必要か

ブラウザ拡張（MetaMask）が入っていれば `window.ethereum` で直接通信できる。
しかしスマホアプリ（HashPort）とPC画面は別デバイスのため直接つながらない。
WalletConnect はこの「別デバイス間通信」を解決する。

### 仕組み（QRコードで暗号化チャネルを確立）

```
DApp（ブラウザ）              WalletConnectリレー           ウォレット（スマホ）
     │                              │                              │
     │ QRコードをローカルで生成・表示（リレーサーバー経由ではない）│
     │── セッション登録 ──────────>│                              │
     │                              │<──── QRスキャン ─────────── │
     │                              │                              │
     │<═══════ E2E暗号化チャネル確立（リレーは中身を読めない）══════>│
     │                              │                              │
     │── 署名リクエスト ──────────>│──────────────────────────> │
     │                              │       [承認 or 拒否]         │
     │<── 署名結果（v, r, s）──────│<──────────────────────────  │
```

> **重要:** QRコードは DApp 自身がローカルで生成する（リレーサーバーから返ってくるのではない）。
> QRの中には `wc:` URI スキームのペアリング情報（セッション共有鍵）が含まれる。
> WalletConnect v2 では **ChaCha20-Poly1305** による対称暗号でE2E暗号化される。

**QRコードの役割：** スキャンした瞬間に「このセッション専用の暗号化鍵」を共有する合言葉。

### コンビニQR決済との対比

| コンビニ払い | WalletConnect |
|---|---|
| レジ画面 | DApp（フロントエンド）|
| PayPay サーバー | WalletConnect リレーサーバー |
| スマホの PayPay アプリ | ウォレットアプリ（HashPort）|
| 「支払う」タップ | 署名（秘密鍵で承認）|
| 支払い完了通知 | 署名結果がDAppに返る |

```
コンビニ払い:  レジがQR表示 → スマホでスキャン → PayPayサーバー経由 → 「支払う」タップ → 完了
WalletConnect: DAppがQR表示 → スマホでスキャン → リレー経由       → 「承認」タップ → 署名返却
```

**基本原則：**
- コンビニ（レジ）はあなたの銀行口座に直接触れない → DApp は秘密鍵に触れない
- PayPay サーバーはお金を「中継」するだけ → リレーは暗号文を中継するだけ（中身は読めない）
- 「支払う」を押すのは常にあなた → 「署名する」のは常にあなたのウォレット

> ⚠️ **注意：「秘密鍵に触れない」≠「何でも安全に署名してよい」**
> 悪意あるDAppは `approve(攻撃者アドレス, 全残高)` への署名を
> 「無害な操作」に見せかけて要求することができる。
> 接続するDAppのドメインとウォレット上の署名内容を必ず確認すること。

### WalletConnect・ウォレット・UIライブラリの関係

```
WalletConnect     → 通信プロトコル（仕様・OSS）← 「電話回線」
MetaMask/HashPort → ウォレット（鍵を管理）      ← 「電話機」
RainbowKit        → 接続UIライブラリ            ← 「電話帳アプリ」
```

RainbowKit を使う理由：WalletConnect・MetaMask・Coinbase などの接続方式を
1つのUIで統一してくれるため、自分で振り分けロジックを書かなくて済む。

---

## 3. CPM と MPM — 送金方式の分類

### 定義

QRコード決済の業界標準（EMVCo）で定義された分類を、Web3の文脈に応用したもの。
（EMVCoは伝統的なQR決済の仕様を定める機関であり、Ethereum送金プロトコルを直接定義するわけではない）

| | CPM（Consumer Presented Mode） | MPM（Merchant Presented Mode）|
|---|---|---|
| QRを出すのは | お客さん | お店 |
| スキャンするのは | お店 | お客さん |
| 主導権 | お客さん側 | お店側 |
| 身近な例 | PayPay でお客がQR提示 | 店頭の固定QRコードをスキャン |

### このプロジェクトは MPM

```java
// CreatePaymentRequest の構造（全フィールドにバリデーションあり）
public record CreatePaymentRequest(
    @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{40}$")
    String receiverAddress,  // お店側が決める

    @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{40}$")
    String senderAddress,    // お店側が指定 ← ここがMPMの特徴

    @NotNull @DecimalMin("0.000001")
    BigDecimal amount,

    @NotNull StablecoinType token
)
```

お店側（バックエンド）が支払い情報をすべて決めてから提示する → **MPM**。

```
MPMの流れ（Phase 5 以降の想定）:
  バックエンドが PaymentOrder 作成（receiverAddress・amount・token が決まっている）
    ↓
  フロントが「1,000 JPYC を支払ってください」と表示
    ↓
  ユーザーが HashPort ウォレットで接続・Permit 署名
    ↓
  バックエンドが transferFrom で JPYC 移動
```

### CPM にするとどう変わるか

```
CPMの流れ（将来対応）:
  ユーザーがスマホでQRコードを表示（address + nonce + expiry）
    ↓
  お店がスキャン → senderAddress をその場で取得
    ↓
  バックエンドが PaymentOrder 作成
    ↓
  ユーザーのスマホに「請求が届きました」と通知
    ↓
  ユーザーが承認 → 署名 → 送金
```

**最大の違い：** `senderAddress` が注文作成時点で判明しているか（MPM）、後から判明するか（CPM）。

### ユースケースによる使い分け

| ユースケース | 向いている方式 |
|---|---|
| EC サイトの決済（金額固定） | MPM |
| サービス料金の請求書払い | MPM |
| 店頭レジでの決済 | CPM |
| 割り勘・個人間送金 | CPM |

---

## 4. MPM/CPM 共通の設計方針

Phase 5-F では MPM で送金を実現しつつ、将来 CPM を追加できる設計にする。

### 設計の核心

「**CPM は MPM の後半フロー（PENDING 以降）を共有できる**」

```
MPM:  注文作成(senderAddress確定) → PENDING → 署名 → CONFIRMED
CPM:  注文作成(senderAddress未定) → AWAITING_CONSUMER → 承認 → PENDING → 署名 → CONFIRMED
                                                                            ↑
                                                              ここから先はMPMと同じ
```

> ⚠️ `AWAITING_CONSUMER` は**現時点の `PaymentStatus` enum には存在しない**（未実装）。
> CPM 対応時に `PaymentStatus` へ追加し、`@PrePersist` の初期ステータスロジックも
> MPM/CPM で分岐させる修正が必要になる。

### フィールド設計（CPM対応に向けた追加方針）

| フィールド | 型 | MPM（現在） | CPM（将来追加時） |
|---|---|---|---|
| `paymentMode` | `MPM` / `CPM` enum | `MPM`（固定） | `CPM` |
| `senderAddress` | VARCHAR(42) | 必須・`nullable=false`（現行） | **CPM追加時に `nullable=true` に変更が必要** |
| `consumerNonce` | VARCHAR(64) | null | QRから取得した64文字ランダム値（`SecureRandom` + hex）|

> ⚠️ 現行の `PaymentOrder.senderAddress` は `@Column(nullable=false)` で定義されており、
> `CreatePaymentRequest` も `@NotBlank` で必須になっている。
> これらは CPM 対応時に条件付きバリデーション（`paymentMode` による分岐）に変更する。
> 「今すぐ nullable にする」のではなく、**CPM 対応フェーズで変更する設計変更点**として記録する。

### `consumerNonce` のセキュリティ上の役割

CPM でQRコードに nonce を含める理由：

```
アドレスだけのQR → 写真を撮って後日再利用できる（リプレイ攻撃）

nonce + expiresAt のQR:
  ・nonce: 一度使ったらバックエンドで使用済みフラグを立てる（重複請求防止）
  ・expiresAt: 有効期限切れで失効（長時間の悪用防止）
  → この2つの組み合わせでリプレイ攻撃を防ぐ
```

### 後から「追加するだけ」で済むもの

```
CPM 対応時に新規追加するだけ（MPMの既存コードに変更なし）:
  GET  /api/v1/payments/pending?address=&nonce=  ← 消費者が請求確認
  POST /api/v1/payments/{id}/claim               ← 消費者が承認（動詞型エンドポイント採用）
  フロント: QRコード生成画面
  フロント: ポーリング（請求通知待ち）

既存コードへの変更（最小限）:
  PaymentStatus に AWAITING_CONSUMER を追加
  PaymentOrder.senderAddress を nullable=true に変更
  CreatePaymentRequest のバリデーションを paymentMode で分岐
```

---

## 5. フロントエンド技術スタック

| 技術 | バージョン | 役割 |
|---|---|---|
| Next.js (App Router) | 15.x | フレームワーク（SSR + クライアント） |
| TypeScript | 5.x | 型安全 |
| Wagmi v2 + Viem | latest | ウォレット接続・署名・コントラクト呼び出し |
| RainbowKit | 2.x | MetaMask/HashPort 接続ダイアログ |
| shadcn/ui + Tailwind CSS | latest | UIコンポーネント |

### Wagmi と ethers.js の違い

| | ethers.js | Wagmi v2 + Viem |
|---|---|---|
| スタイル | 命令型（クラスベース） | 宣言型（React Hooks） |
| 型安全 | 部分的 | 完全（Viem が担保） |
| React との相性 | 普通 | 最適化されている |
| バンドルサイズ | 大きめ | ツリーシェイキング対応 |

```typescript
// Wagmi でのトークン残高取得例（useReadContract は Wagmi v2 の API）
const { data: balance } = useReadContract({
  address: JPYC_CONTRACT_ADDRESS,
  abi: erc20Abi,
  functionName: 'balanceOf',
  args: [walletAddress],
})
```

### 認証トークン（JWT）の保管場所

SIWE 認証後に発行される JWT の保管方法は**セキュリティ上の重要な選択**。

| 保管場所 | XSS耐性 | CSRF耐性 | 備考 |
|---|---|---|---|
| `localStorage` | 🔴 脆弱（JS から読める） | 🟢 | **使用禁止** |
| メモリ（React state） | 🟢 | 🟢 | ページリロードで消える |
| `httpOnly Cookie` | 🟢（JS から読めない） | 🔴 要対策 | **推奨：SameSite=Strict + CSRF トークン必須** |

> このプロジェクトでは `httpOnly Cookie` + `SameSite=Strict` + CSRF トークンの組み合わせを採用する。
> `localStorage` への JWT 保管は XSS 攻撃で即座にトークン奪取されるため**禁止**。

---

## まとめ

| 概念 | 一言まとめ |
|---|---|
| DApp | ウォレットで操作するウェブアプリ（広義）。バックエンドがあれば止められる |
| WalletConnect | ブラウザとスマホウォレットをQRでつなぐプロトコル。QRはDApp自身が生成 |
| MPM | お店が支払い情報を提示してユーザーが承認する方式 |
| CPM | ユーザーがQRを提示してお店が請求する方式 |
| RainbowKit | 複数の接続方式を1つのUIに統一するライブラリ |

このプロジェクトは MPM からスタートし、フィールド設計を工夫することで
大きな作り直しなく CPM を追加できる構造を採用する。

---

## 6. CPM/MPM 以外の決済方式

CPM/MPM は「誰がQRを提示するか」という分類にすぎない。
より広い視点で決済方式を整理する。

---

### 軸① Push vs Pull（送金の主導権）

すべての決済の根本にある分類。

```
【Push（送る側が主導）】
送金者 ──(自分から送る)──> 受取人
例: 銀行振込・仮想通貨の直接送金

【Pull（受け取る側が主導）】
送金者 <──(引き落とす)── 受取人
例: クレジットカード・口座引き落とし
```

| | Push | Pull |
|---|---|---|
| 主導権 | 送る側 | 受け取る側 |
| 承認タイミング | 送金時 | 事前の引き落とし許可 |
| Web3での例 | `transfer()` | `approve()` + `transferFrom()` |
| リスク | 誤送金（取り消し不可） | 過剰引き落とし（approve 上限で制御） |

---

### 軸② Web3特有の決済方式

| 方式 | 仕組み | 特徴 |
|---|---|---|
| **Direct Transfer** | `transfer(to, amount)` を直接呼ぶ | 最もシンプル。送る側がガスを払う |
| **Approve + transferFrom** | 事前承認 → 受け取り側が引き落とし | Pull型。Phase 5 の本命 |
| **EIP-2612 Permit** | オフチェーン署名で承認 → **ユーザーのガス不要**（バックエンドが `permit()` TX のガスを負担） | Approve のユーザーガスレス版 |
| **Streaming（Sablier）** | 毎秒単位で自動送金 | サブスク・時間課金向け |
| **Meta-transaction** | リレーヤーがガス代を肩代わり | ユーザーが ETH/POL 不要 |
| **Account Abstraction** | スマートウォレットが UserOperation 経由でトランザクション送信 | EIP-4337。Paymaster によるガス代代替払いが可能 |

> **EIP-2612 Permit の注意点：**「ガス不要」は**ユーザー側のガスが不要**という意味。
> `permit()` や `transferFrom()` のトランザクション自体にはガスが必要であり、
> バックエンドのウォレットが Polygon の場合は **POL**（旧MATIC）を保有して支払う。
> JPYC（ERC-20）でガスを払うことはできない（Account Abstraction を使わない限り）。

> **EIP-2612 Permit のフィッシングリスク：**
> Permit 署名はオンチェーンに記録されないため、フィッシングサイトで騙し取られた場合に
> 通常の `approve()` より被害が発覚しにくい。署名を要求するサイトのドメインを必ず確認すること。

---

### 軸③ タイミングによる分類

```
【低遅延決済（数秒〜十数秒）】
 ユーザー操作 → オンチェーン TX 送信 → ブロック確認後に反映
 例: Direct Transfer・Permit + transferFrom（Phase 5）
 ※「即時」ではなくブロック確認（Polygon で約2〜5秒）が必要

【事後決済（入金検知型）】
 注文作成 → ユーザーが自分で送金 → バックエンドが検知 → 確認
 例: Transfer イベント監視（Phase 4）

【事前決済（サブスク型）】
 一度承認 → 定期的に自動引き落とし
 例: Sablier Streaming・AutoPurchaseJob（Phase 6）
```

---

### このプロジェクトが実装する方式の地図

```
Phase 3  ✅  残高照会のみ（決済なし）

Phase 4  🔜  【事後決済 / Push型】
              ユーザーが自分でJPYCを手動送金
              → バックエンドが Transfer イベントを検知して CONFIRMED に更新

Phase 5       【低遅延決済 / Pull型 + ユーザーガスレス】
              Permit（EIP-2612）でオフチェーン署名
              → バックエンドが permit() + transferFrom() を実行（ガス: POL）

Phase 5-F     【フロントでの送金UI】
              上記フローをブラウザで操作できる画面

Phase 6       【自動決済 / Pull型 + スケジューラ】
              Sablier Streaming または allowance + @Scheduled で定期引き落とし
```

---

### 方式ごとのメリット・デメリット比較

| 方式 | UX | ガス負担 | セキュリティ | 実装難度 |
|---|---|---|---|---|
| Direct Transfer（手動） | 悪い（毎回ウォレット操作） | ユーザー（ETH/POL） | 高※1 | 低 |
| 入金検知（Phase 4） | 普通（送金後に確認待ち） | ユーザー（POL） | 高※1 | 中 |
| Permit + transferFrom（Phase 5） | 良い（署名1回で完了） | バックエンド（POL） | 中※2 | 高 |
| Streaming（Phase 6） | 最良（自動） | バックエンド（POL） | 中※2 | 高 |
| Account Abstraction | 最良（ガス不要） | Paymaster | 高 | 非常に高 |

> ※1 **「高」の根拠と留意点：** バックエンドが秘密鍵を持たないため相対的に高評価。
> ただし誤送金（取り消し不可）・二重送金・Reorg（チェーン再編成）リスクは残る。
> 入金検知型では `txHash` のユニーク制約によるべき等性確保が必須。

> ※2 **「中」の根拠：** バックエンドのウォレット（spender）が侵害された場合、
> ユーザーが approve した範囲内で任意タイミングに引き落とし可能。
> Permit 署名の `deadline` を短く設定し、`spender` ウォレットの秘密鍵管理を厳重に行うことで
> リスクを低減できる。「バックエンドが秘密鍵を持つ」のではなく
> 「バックエンドのウォレットアドレスに transferFrom 権限が集中する」ことがリスクの本質。

---

### CPM/MPM と Push/Pull の関係

CPM/MPM は「誰が最初に提示するか」、Push/Pull は「誰が送金を主導するか」で別の軸。

```
              Push型                     Pull型
         ┌──────────────────────┬──────────────────────────┐
  MPM    │ お店が金額提示して   │ お店が承認をもらって     │
         │ ユーザーが自分で送金 │ お店が引き落とし         │
         │（Phase 4 の方式）    │（Phase 5 の方式 ← 本命） │
  ───────┼──────────────────────┼──────────────────────────┤
  CPM    │ ユーザーがQR提示して │ ユーザーがQR提示して     │
         │ 自分で送金           │ 承認 → お店が引き落とし  │
         └──────────────────────┴──────────────────────────┘
```

このプロジェクトの最終形は **MPM × Pull型（Permit + transferFrom）**。
