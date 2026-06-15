# 法人ガイド — Alchemy・自前ノード・導入判断

> 🏢 法人・エンタープライズ向け | 読了時間: 約15分

---

## 法人でもAlchemyは使えるか

**結論: はい、法人でも広く採用されています。**

「法人 = 自前ノード」は誤解です。実態は:

| 規模 | よくある選択 |
|---|---|
| スタートアップ〜中小 | Alchemy / Infura（有料プラン）|
| 中堅〜大手 | Alchemy + Infura デュアルプロバイダー |
| 大規模（月数十億リクエスト超）| 自前ノード + Alchemy フォールバック |
| 超大規模（Coinbase・Binance級）| 完全自前 |

**採用している企業（一例）:**
- OpenSea（NFTマーケット大手）
- Adobe（NFT関連プロダクト）
- 多数のDeFiプロトコル・NFTプラットフォーム

---

## Alchemy エンタープライズプランの特徴

| 機能 | Free | Growth | Enterprise |
|---|---|---|---|
| 料金 | $0/月 | $49〜/月 | 要相談 |
| CU/月 | 3億 | 4億〜 | カスタム |
| SLA | なし | 99.9% | 99.99% |
| 専用エンドポイント | ❌ | ❌ | ✅ |
| DDoS 保護 | 標準 | 強化 | カスタム |
| 監査ログ | ❌ | ❌ | ✅ |
| カスタマーサポート | コミュニティ | メール | 専任CSM |
| DPA（データ処理委託契約）| ❌ | ❌ | ✅ |
| SOC 2 Type II 準拠 | - | - | ✅ |

---

## 自前ノードが必要になる条件

以下の**いずれか**に当てはまる場合のみ検討する:

| 条件 | 詳細 |
|---|---|
| **データを社外に出せない** | 金融規制・社内コンプライアンスで外部サービス禁止 |
| **月数十億リクエスト超** | Alchemy Enterprise の費用対効果が逆転する水準 |
| **`trace_*` APIを大量に使う** | 高度なデバッグ・DeFi分析（有料プランでも制限あり）|
| **過去全データが常時必要** | アーカイブノード（数TB ストレージ必要）|
| **レイテンシ 1ms 以下が必要** | 同一DC配置が必要なHFT的ユースケース |

**ほとんどの法人はこれらに該当しません。**

---

## コスト試算（法人）

### Alchemy 利用の場合

```
月1,000万リクエスト（eth_getLogs換算: 月750億CU想定、実際はより少ない）:

現実的な試算:
  eth_getLogs を15秒ごとにポーリング（24時間365日）:
  → 月 172,800 リクエスト × 75 CU = 約1,296万 CU / 月

  Alchemy Free プランの上限: 3億 CU
  → 個人・スモールチームなら 0円で十分
```

| プラン | 料金/月 | 適している規模 |
|---|---|---|
| Free | $0 | 個人・スタートアップ初期 |
| Growth | $49〜 | スタートアップ〜中小 |
| Scale | $499〜 | 中規模サービス |
| Enterprise | 要相談 | 大規模・SLA必須 |

### 自前フルノード の場合

| 項目 | 月額コスト |
|---|---|
| サーバー（AWS m5.2xlarge 相当）| $300〜$400 |
| SSD 2TB（EBS等）| $100〜$200 |
| 帯域コスト | $50〜$200 |
| 運用人件費（月10h × シニアエンジニア）| $1,000〜$4,000 |
| **合計** | **$1,450〜$4,800（≈ 22〜72万円）** |

→ Alchemy Enterprise が $10,000/月 を超えるまでは、コスト優位性は自前に出にくい

---

## セキュリティ・APIキー管理

### 本番環境での必須要件

```bash
# ❌ 絶対にやってはいけない
WEB3J_POLYGON_ENDPOINT=https://polygon-mainnet.g.alchemy.com/v2/hardcoded-key

# ✅ 推奨: クラウドシークレット管理サービスを使う
# AWS Secrets Manager
aws secretsmanager get-secret-value --secret-id prod/alchemy-api-key

# GCP Secret Manager
gcloud secrets versions access latest --secret="alchemy-api-key"

# Kubernetes Secret
kubectl create secret generic alchemy \
  --from-literal=endpoint=https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY
```

### Alchemy のセキュリティ機能（法人向け）

| 機能 | 説明 |
|---|---|
| IP 許可リスト | 特定IPからのリクエストのみ許可 |
| Domain 制限 | 特定ドメインからのみ許可（フロントエンド向け）|
| JWT 認証 | APIキーの代わりにJWT使用 |
| 監査ログ | Enterpriseプランで全リクエスト記録 |
| キーローテーション | ダッシュボードで即時Revoke・再発行 |

### セキュリティチェックリスト

```
□ APIキーをコードにハードコードしていない
□ .env が .gitignore に含まれている
□ 本番環境でシークレット管理サービスを使用している
□ APIキーのローテーション手順を策定した（推奨: 3ヶ月ごと）
□ IP許可リストを設定した（可能であれば）
□ 監査ログの保存期間を確認した（Enterprise必須）
□ SLAがビジネス要件を満たすことを確認した
□ DPA（データ処理委託契約）の要否を確認した
□ SOC 2 Type II 取得状況を確認した（Alchemy: ✅取得済み）
```

---

## 冗長化・フォールバック戦略

### デュアルプロバイダー構成（推奨）

```yaml
# application.yml
web3j:
  polygon-primary: ${WEB3J_POLYGON_PRIMARY}    # Alchemy
  polygon-fallback: ${WEB3J_POLYGON_FALLBACK}  # Infura
```

```java
@Service
public class ResilientChainClient {
    private final Web3j primary;
    private final Web3j fallback;
    private final MeterRegistry meterRegistry;

    public <T> T call(Function<Web3j, T> operation) {
        try {
            T result = operation.apply(primary);
            meterRegistry.counter("rpc.request", "provider", "primary", "status", "success").increment();
            return result;
        } catch (Exception e) {
            log.warn("Primary RPC failed: {}", e.getMessage());
            meterRegistry.counter("rpc.request", "provider", "primary", "status", "failure").increment();
            T result = operation.apply(fallback);
            meterRegistry.counter("rpc.request", "provider", "fallback", "status", "success").increment();
            return result;
        }
    }
}
```

### 推奨構成早見表

| 規模・要件 | 構成 | 月額目安 |
|---|---|---|
| スタートアップ初期 | Alchemy Free | $0 |
| 成長期・法人 | Alchemy Growth | $49〜 |
| 高可用性が必要 | Alchemy + Infura デュアル | $100〜 |
| 大規模 | 自前ノード + Alchemy フォールバック | $2,000〜 |

---

## 監視・アラート（本番必須）

```yaml
# Prometheus メトリクス例
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

監視すべき指標:

| 指標 | 閾値の目安 | アラート条件 |
|---|---|---|
| RPCエラー率 | < 0.1% | > 1% で警告 |
| RPCレイテンシ P99 | < 500ms | > 2000ms で警告 |
| ブロック遅延 | < 30s | > 2分で警告 |
| Alchemy CU消費 | 月上限の80% | 上限の90% でアラート |

---

## コンプライアンス対応

### 金融庁・規制対応のポイント

```
ブロックチェーンアプリを金融サービスに使う場合:

1. 仮想通貨交換業登録（必要な場合）
   → 暗号資産の売買・交換を行う場合は登録が必要

2. ステーブルコイン規制（2024年以降）
   → 電子決済手段に該当する場合は金融機関との提携が必要

3. データ保管場所
   → Alchemy はUS・EU拠点あり
   → 国内データ保持が必要な場合は自前ノード検討
```

---

## 意思決定フロー（法人向け）

```
Step 1: データを社外に出せない規制がある？
  → YES: 自前ノード一択
  → NO: Step 2 へ

Step 2: 月のリクエスト数は？
  → < 3億 CU: Alchemy Free
  → 3〜10億 CU: Alchemy Growth ($49〜)
  → > 10億 CU: Step 3 へ

Step 3: Alchemy Enterprise vs 自前ノード
  → コスト比較 + 運用リソースの有無で判断

Step 4: 単一プロバイダーへの依存は許容できるか？
  → NO: Alchemy + Infura デュアル構成
  → YES: Alchemy シングル + 監視強化

Step 5: SLAは十分か？
  → 99.9% で十分 → Growth プラン
  → 99.99% 必要 → Enterprise プラン
```

---

## 次のステップ

- [advanced/README.md](../advanced/README.md) — 技術レポートで実装を深く理解する
- [intermediate/02-rpc-provider-comparison.md](../intermediate/02-rpc-provider-comparison.md) — 技術的な詳細比較
