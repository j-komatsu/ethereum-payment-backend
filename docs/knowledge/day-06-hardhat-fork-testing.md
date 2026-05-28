# Day 06 — Hardhat fork テストの概念整理

## 背景

Phase 5 Day 22-23 で EIP-2612 Permit の実装が完了した。
次のステップ（Day 24-25）で Hardhat fork を使った統合テストを書く前に、
「なぜ Hardhat fork が必要か」「Alchemy とは何か」を整理しておく。

---

## 1. Ethereum ノードとは何か

ブロックチェーンを操作（残高確認・TX送信・コントラクト呼び出し）するには、
チェーンの全データを持つ**フルノード**に接続する必要がある。

```
アプリ → ノード → ブロックチェーンの状態
```

フルノードを自前で運用するコスト:
- Polygon Mainnet: ストレージ 2TB+、同期に数日〜1週間
- Ethereum Mainnet: 2TB+、さらに重い
- 常時稼働のサーバーが必要

個人・スタートアップが自前で持つのは非現実的。

---

## 2. Alchemy とは（ノードプロバイダー）

Alchemy はノードをサービスとして提供する会社。競合に Infura、QuickNode がある。

```
アプリ → Alchemy API エンドポイント → Alchemy が管理するノード → チェーン
```

### API キーの役割

「どのユーザー/アプリのリクエストか」を識別するための認証情報。

```
https://polygon-mainnet.g.alchemy.com/v2/YOUR_API_KEY
```

### 料金

| プラン | 月間リクエスト数 | 費用 |
|---|---|---|
| 無料 | 3億 compute units | $0 |
| Growth | 制限なし | 従量課金 |

テスト・開発目的では無料プランで十分。

---

## 3. Hardhat fork とは何か

### 通常のモックテスト（限界あり）

```
テストコード → [Mockito でモックされた Web3j] → 実際のオンチェーン操作なし

✅ 速い
✅ 外部依存なし
❌ 実際の permit() が本物の JPYC コントラクトで動くかは不明
❌ permitName の誤り、アドレスの誤り、ABI の誤りを検出できない
```

### Hardhat fork（本番コントラクトをローカルで動かす）

```
①  Alchemy 経由で Polygon Mainnet の状態をスナップショット取得
       - JPYC コントラクトのバイトコード
       - 各アドレスの残高・nonce
       - ブロック番号・baseFee など

②  ローカルマシンに偽のノード（Hardhat node）を起動
       - localhost:8545 で RPC リクエストを受け付ける

③  Spring Boot テストの Web3j を localhost:8545 に向ける

④  テスト実行
       - impersonateAccount で JPYC 保有者になりすます（残高確保）
       - setBalance でスペンダーウォレットに POL（ガス代）を付与
       - permit() + transferFrom() を実際に実行
       - オンチェーンの状態変化（残高移動）を確認

→ 実際のコントラクトコードで動作確認
→ ガス代は偽トークン（無料）
→ 本番チェーンに影響なし
```

```
┌─────────────────────────────────────────────────────┐
│ ローカルマシン（テスト実行中）                        │
│                                                     │
│  JUnit テスト                                       │
│    └── PermitService.execute()                      │
│          └── Web3j → localhost:8545                │
│                                                     │
│  Hardhat node (localhost:8545)                      │
│    = Polygon Mainnet のある時点のスナップショット    │
│      ・JPYC コントラクト（本物のバイトコード）       │
│      ・テスト用ウォレットに残高を直接注入できる      │
│      ・TX を処理して状態変化を返す                  │
└─────────────────────────────────────────────────────┘
         ↑ スナップショット取得時のみ
    Alchemy API（Polygon Mainnet）にアクセス
```

---

## 4. Hardhat fork で確認したいこと

EIP-2612 の実装で「計算は合っているが実際には動かない」ケースの代表例:

| リスク | 内容 | Mock で検出できるか |
|---|---|---|
| `permitName` の誤り | `"JPYCoin"` vs `"JPY Coin"` — 1文字違うとドメインセパレータが全て異なる | ❌ |
| コントラクトアドレスの誤り | 見当違いのアドレスに TX を送る | ❌ |
| ABI 引数順の誤り | `permit(owner, spender, value, deadline, v, r, s)` の順 | ❌ |
| `transferFrom` の残高不足 | allowance / 残高が足りない | ❌ |
| EIP-1559 ガス設定 | `maxFeePerGas` が低すぎて TX がドロップ | ❌ |

これら全てが Hardhat fork テストで初めて検出できる。

---

## 5. Alchemy キーなしでの代替手段

| 方法 | 特徴 |
|---|---|
| Alchemy 無料プラン | 推奨。安定・高速 |
| Infura 無料プラン | 同等 |
| `https://polygon-rpc.com`（公開 RPC） | 無料だがレート制限あり・不安定 |
| Anvil（Foundry）+ Alchemy | Hardhat より高速なフォーク実装 |

---

## 6. Day 24-25 の実装計画

```
1. Node.js + Hardhat インストール（build.gradle で自動化可能）
2. hardhat.config.ts でフォーク設定
3. TestContainers または ProcessBuilder で Hardhat node を起動
4. Spring Boot テストで localhost:8545 を向く
5. テストシナリオ:
   - JPYC ホルダーが permit 署名
   - PermitService.execute() を呼び出し
   - 残高移動を on-chain で確認
```

**事前に必要なもの: Alchemy API キー（無料）のみ**

---

## 7. 学んだこと・判断の記録

- Hardhat fork は「本番コントラクトをローカルでシミュレートする」技術。CI/CD での自動テストにも使える
- Alchemy のような RPC プロバイダーはブロックチェーン開発の標準インフラ
- `permitName` のような「文字列の正確な一致」は EIP-712 で極めて重要。Mock では検証不可能
- テストネット（Polygon Amoy）でも同様のことができるが、テストトークンの入手・管理が必要なため Hardhat fork の方がシンプル
