# レポート05F — フロントエンド・送金UI設計

## はじめに

Phase 5-F では、実際に送金操作をブラウザ上で行えるフロントエンドを実装する。
本レポートでは実装前に把握すべき概念として「DApp」「WalletConnect」「CPM/MPM」を整理する。

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
          誰も止められない
          コードは全員が見える（オープンソース）
```

| 比較項目 | 普通のアプリ | DApp |
|---|---|---|
| ルールを決める | 会社 | コード（自動） |
| 止められるか | できる | できない |
| 残高を管理するのは | 会社のDB | ブロックチェーン |
| コードを見られるか | 見えない | 全員見える |

### このプロジェクトの位置づけ

```
このプロジェクトの構成:
  ├── Next.js フロント      ← "DApp" と呼ぶ（広い意味）
  ├── Spring Boot バックエンド  ← 中央サーバーあり
  └── Polygon ブロックチェーン  ← JPYC コントラクト
```

厳密には「完全な DApp」ではなく「Web3対応ウェブアプリ」だが、
バックエンドを持つ構成はほとんどの実サービスで採用されており、業界的には DApp と呼ばれる。

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
     │── セッション開始 ──────────>│                              │
     │<── QRコード表示 ────────────│                              │
     │                              │<──── QRスキャン ─────────── │
     │                              │                              │
     │<═══════ E2E暗号化チャネル確立（リレーは中身を読めない）══════>│
     │                              │                              │
     │── 署名リクエスト ──────────>│──────────────────────────> │
     │                              │       [承認 or 拒否]         │
     │<── 署名結果（v, r, s）──────│<──────────────────────────  │
```

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

**重要な原則：**
- コンビニ（レジ）はあなたの銀行口座に直接触れない → DApp は秘密鍵に触れない
- PayPay サーバーはお金を「中継」するだけ → リレーは暗号文を中継するだけ（中身は読めない）
- 「支払う」を押すのは常にあなた → 「署名する」のは常にあなたのウォレット

### WalletConnect・ウォレット・UIライブラリの関係

```
WalletConnect  → 通信プロトコル（仕様・OSS）    ← 「電話回線」
MetaMask/HashPort → ウォレット（鍵を管理）        ← 「電話機」
RainbowKit     → 接続UIライブラリ               ← 「電話帳アプリ」
```

RainbowKit を使う理由：WalletConnect・MetaMask・Coinbase などの接続方式を
1つのUIで統一してくれるため、自分で振り分けロジックを書かなくて済む。

---

## 3. CPM と MPM — 送金方式の分類

### 定義

QRコード決済の業界標準（EMVCo）で定義された2つの方式。

| | CPM（Consumer Presented Mode） | MPM（Merchant Presented Mode）|
|---|---|---|
| QRを出すのは | お客さん | お店 |
| スキャンするのは | お店 | お客さん |
| 主導権 | お客さん側 | お店側 |
| 身近な例 | PayPay でお客がQR提示 | 店頭の固定QRコードをスキャン |

### このプロジェクトは MPM

```java
// CreatePaymentRequest の構造
public record CreatePaymentRequest(
    String receiverAddress,  // お店側が決める
    String senderAddress,    // お店側が指定 ← ここがMPMの特徴
    BigDecimal amount,       // お店側が決める
    StablecoinType token
)
```

お店側（バックエンド）が支払い情報をすべて決めてから提示する → **MPM**。

```
MPMの流れ:
  バックエンドが PaymentOrder 作成（receiverAddress・amount・token が決まっている）
    ↓
  フロントが「1,000 JPYC を支払ってください」と表示
    ↓
  ユーザーが HashPort ウォレットで接続・承認
    ↓
  transferFrom で JPYC 移動
```

### CPM にするとどう変わるか

```
CPMの流れ:
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

### 今の設計で追加しておくフィールド

| フィールド | 型 | MPM | CPM |
|---|---|---|---|
| `paymentMode` | `MPM` / `CPM` | `MPM`（固定） | `CPM` |
| `senderAddress` | VARCHAR(42) | 必須（注文時確定） | null可（後で確定） |
| `consumerNonce` | VARCHAR(64) | null | QRから取得した値 |

### 後から「追加するだけ」で済むもの

```
CPM 対応時に新規追加するだけ（既存コードに変更なし）:
  GET  /api/v1/payments/pending?address=&nonce=  ← 消費者が請求確認
  POST /api/v1/payments/{id}/claim               ← 消費者が承認
  フロント: QRコード生成画面
  フロント: ポーリング（請求通知待ち）
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
// Wagmi でのトークン残高取得例
const { data: balance } = useReadContract({
  address: JPYC_CONTRACT_ADDRESS,
  abi: erc20Abi,
  functionName: 'balanceOf',
  args: [walletAddress],
})
```

---

## まとめ

| 概念 | 一言まとめ |
|---|---|
| DApp | ウォレットで操作するウェブアプリ（広義） |
| WalletConnect | ブラウザとスマホウォレットをQRでつなぐプロトコル |
| MPM | お店が支払い情報を提示してユーザーが承認する方式 |
| CPM | ユーザーがQRを提示してお店が請求する方式 |
| RainbowKit | 複数の接続方式を1つのUIに統一するライブラリ |

このプロジェクトは MPM からスタートし、フィールド設計を工夫することで
大きな作り直しなく CPM を追加できる構造を採用する。
