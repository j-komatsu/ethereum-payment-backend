# 個人開発者ガイド — 0円でEthereumアプリを始める

> 🏠 個人開発者向け | 読了時間: 約12分

---

## 最速スタートガイド（費用: 0円）

### 必要なもの

| 必要なもの | 費用 | 入手方法 |
|---|---|---|
| Alchemy アカウント | 無料 | [alchemy.com](https://alchemy.com) でサインアップ |
| テスト用ウォレット | 無料 | MetaMask ブラウザ拡張機能 |
| テストネット MATIC | 無料（価値ゼロ）| Faucet から取得 |
| Java 21 + Docker | 無料 | 各公式サイト |

---

## ステップ1: Alchemy でAPIキーを取得

```
1. alchemy.com にサインアップ（メールのみ、クレカ不要）
2. ダッシュボード → 「Create App」ボタン
3. 設定:
   - Chain: Polygon
   - Network: Amoy（テスト用）  ← 開発中はここ
4. 作成後「View Key」→ HTTPS URL をコピー
   例: https://polygon-amoy.g.alchemy.com/v2/abc123xxx
```

---

## ステップ2: ローカル開発の設定

```bash
# プロジェクトルートに .env を作成（Gitには絶対コミットしない）
cat << 'EOF' > .env
WEB3J_POLYGON_ENDPOINT=https://polygon-amoy.g.alchemy.com/v2/YOUR_KEY
WEB3J_ETHEREUM_ENDPOINT=https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY
DB_URL=jdbc:h2:mem:testdb
DB_USERNAME=sa
DB_PASSWORD=
JWT_SECRET=local-dev-secret-must-be-at-least-32-characters-long
SIWE_DOMAIN=localhost
SIWE_CHAIN_ID=80002
EOF
```

> `.env` が `.gitignore` に含まれているか確認: `grep -n ".env" .gitignore`

---

## ステップ3: テストネット用トークンを取得

**Amoy（Polygon テストネット）の MATIC:**
- [faucet.polygon.technology](https://faucet.polygon.technology) にアクセス
- Network: Amoy を選択 → ウォレットアドレスを入力
- 1回で 0.5 MATIC 程度取得可能（無料・価値ゼロ）

**Sepolia（Ethereum テストネット）の ETH:**
- [sepoliafaucet.com](https://sepoliafaucet.com) → Alchemy アカウントでログイン
- 1回で 0.5 ETH 取得可能

---

## テストネット vs メインネット

| | テストネット | メインネット |
|---|---|---|
| ETH/MATIC | 価値ゼロ（Faucet で無料取得）| 本物（購入が必要）|
| ミスした場合 | 何度でもやり直せる | 取り返しがつかない |
| チェーン ID | Amoy: 80002, Sepolia: 11155111 | Polygon: 137, ETH: 1 |
| 使う局面 | 開発・テスト中は**常にここ** | 本番リリース時のみ |

**原則: 本番リリースまでテストネットを使い続ける**

---

## Alchemy 無料枠でどれだけ使えるか

月3億 CU（Compute Units）の消費量目安:

| 操作 | CU | 月3億CUでの上限 |
|---|---|---|
| `eth_blockNumber` | 10 CU | 3,000万回 |
| `eth_getBalance` | 19 CU | 約1,578万回 |
| `eth_call`（残高照会等）| 26 CU | 約1,153万回 |
| `eth_getLogs`（イベント監視）| 75 CU | 約400万回 |

**15秒ポーリングの場合の月間消費量:**
```
1日 = 24h × 60min × (60s / 15s) = 5,760 リクエスト
1ヶ月 = 5,760 × 30 = 172,800 リクエスト
→ 172,800 × 75 CU = 約1,296万 CU / 月

月3億 CUに対して約4% しか使わない → 余裕すぎる
```

---

## 開発フローの推奨パターン

```
Phase 1: ローカル完結（最初の数週間）
  ├── H2 インメモリDB（起動が速い・設定不要）
  ├── Alchemy Amoy（Polygon テストネット）
  └── モックモード（sablier.enabled=false）
  
Phase 2: ステージング相当（本番前確認）
  ├── PostgreSQL（Neon free tier: 0.5GB まで無料）
  ├── まだ Amoy テストネット
  └── Railway / Render の無料枠にデプロイ

Phase 3: 本番（リリース時のみ）
  ├── PostgreSQL（Neon pro: $19/月〜）
  ├── Alchemy Polygon Mainnet に切り替え
  └── 本物の MATIC・JPYC が動く
```

---

## よくある落とし穴

### 1. APIキーをGitにコミットしてしまう

```bash
# ❌ 絶対やってはいけない
git add .env
git commit -m "add env"  # ← キーが世界に公開される

# ✅ 正しい手順
echo ".env" >> .gitignore  # まず .gitignore に追加
# .env.example にプレースホルダーを書く（これはコミットOK）
```

もしコミットしてしまったら:
1. Alchemy ダッシュボードで **即座にAPIキーをRevoke**（無効化）
2. `git filter-branch` または BFG Repo Cleaner でコミット履歴から削除
3. 新しいAPIキーを発行して再設定

### 2. テストネットとメインネットのchainIdを混同する

```yaml
# application-dev.yml（開発用）
auth:
  siwe:
    chain-id: 80002   # ← Amoy テストネット

# application.yml（本番用）
auth:
  siwe:
    chain-id: ${SIWE_CHAIN_ID:137}  # ← Polygon Mainnet
```

### 3. wei と MATIC/ETH の変換を間違える

```java
// ❌ 間違い: wei をそのまま表示
System.out.println("残高: " + balance + " MATIC");  // 巨大な数になる

// ✅ 正しい: Convert ユーティリティを使う
BigDecimal maticValue = Convert.fromWei(balance.toString(), Convert.Unit.ETHER);
System.out.println("残高: " + maticValue + " MATIC");
// 1 MATIC = 10^18 wei（ETH と同じ単位系）
```

### 4. ローカルで動くのに本番で動かない

よくある原因:
- 環境変数の設定漏れ（`DB_URL` など）
- テストネット用アドレスを本番で使っている
- `application-dev.yml` の設定が本番で無効（H2がなくてFlywayが失敗）

---

## コスト試算（個人開発）

| 項目 | 費用 |
|---|---|
| Alchemy（無料枠）| **0円** |
| テストネット MATIC | **0円**（Faucet）|
| Neon PostgreSQL（無料枠）| **0円**（0.5GB まで）|
| Railway（無料枠）| **0円**（月5ドル超えると有料）|
| Polygon ガス代（本番・月）| **10〜100円**（MATIC）|
| **合計** | **ほぼ0円〜月数百円** |

---

## 便利な Alchemy 機能

### Alchemy Dashboard で確認できること

- リクエスト数のグラフ（日次・週次）
- エラー率とエラーの詳細
- レスポンスタイムの統計
- 残りCU（課金監視）

### Alchemy Notify（Webhook）

ポーリングの代わりに使えるプッシュ通知:

```
設定手順:
  1. ダッシュボード → Notify → Create Webhook
  2. 「Address Activity」を選択
  3. 監視したいアドレスを入力
  4. 送金があると POST で通知が来る（リアルタイム）
```

| 比較 | ポーリング（現在の実装）| Webhook |
|---|---|---|
| 実装の複雑さ | 低い | 中程度（エンドポイント公開が必要）|
| リアルタイム性 | ポーリング間隔分の遅延 | ほぼリアルタイム |
| CU消費 | あり | なし |
| ローカル開発 | 容易 | ngrok 等が必要 |

---

## 次のステップ

- [個人 → 法人検討](enterprise.md) — チームで使う・ビジネス化を検討する場合
- [advanced/README.md](../advanced/README.md) — 技術的にさらに深掘りしたい場合
