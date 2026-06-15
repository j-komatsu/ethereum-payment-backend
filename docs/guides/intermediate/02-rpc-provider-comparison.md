# RPCプロバイダー比較 — Alchemy vs Infura vs 自前ノード

> 🌿 中級者向け | 読了時間: 約12分  
> 前のステップ: [JSON-RPC詳解](01-json-rpc-mechanics.md)  
> 次のステップ: [PoSとファイナリティ](03-pos-and-finality.md)

---

## 選択肢の全体像

| 選択肢 | 概要 |
|---|---|
| **Alchemy** | RPCプロバイダー最大手。Enhanced API・Webhookが充実 |
| **Infura** | ConsenSysが運営。業界標準的な位置づけ |
| **QuickNode** | 高速性に強み。マルチチェーン対応 |
| **dRPC** | 分散型RPC（複数ノードから集約）|
| **自前フルノード** | 完全コントロール。大トラフィック向け |
| **自前アーカイブノード** | 全歴史データが必要な場合 |

---

## 機能比較

| 機能 | Alchemy | Infura | 自前ノード |
|---|---|---|---|
| 標準JSON-RPC | ✅ | ✅ | ✅ |
| WebSocket | ✅ | ✅ | ✅ |
| Webhook（送金通知）| ✅（無料）| ✅（有料）| 自前実装必要 |
| NFT API | ✅ | ✅（有料）| ❌ |
| Token API | ✅ | ❌ | ❌ |
| trace_* / debug_* API | ✅（Growth以上）| ✅（有料）| ✅ |
| アーカイブデータ | ✅（有料）| ✅（有料）| アーカイブノード必要 |

---

## 料金比較

### Alchemy

| プラン | 料金 | 内容 |
|---|---|---|
| Free | $0/月 | 月3億 CU（Compute Units）|
| Growth | $49〜/月 | 月4億 CU + SLA 99.9% |
| Scale | $499〜/月 | 大容量 + 専用サポート |
| Enterprise | 要相談 | 専用エンドポイント + カスタムSLA |

**Compute Units（CU）とは:** Alchemy独自の単位。
- `eth_blockNumber` = 10 CU
- `eth_getBalance` = 19 CU
- `eth_call` = 26 CU
- `eth_getLogs` = 75 CU

月3億 CU = `eth_getLogs` を400万回呼べる量（個人には余りある）

### Infura

| プラン | 料金 | 内容 |
|---|---|---|
| Free | $0/月 | 月300万リクエスト |
| Developer | $50/月 | 月500万 + WebSocket |
| Team | $225/月 | 月2000万 |

### 自前ノード（参考コスト）

| 項目 | 月額コスト |
|---|---|
| サーバー（AWS m5.2xlarge 相当）| $300〜$400 |
| SSD 2TB | $100〜$200 |
| 帯域 | $50〜$200 |
| 運用人件費（月5h×エンジニア）| $500〜$2,000 |
| **合計** | **$950〜$2,800（≈ 14〜42万円）** |

---

## パフォーマンス特性

| | Alchemy | Infura | 自前ノード（同リージョン）|
|---|---|---|---|
| レイテンシ | 50〜200ms | 50〜300ms | 10〜50ms |
| 可用性 | 99.9%（有料）| 99.9%（有料）| 自己責任 |
| レート制限 | CUベース | リクエスト数ベース | なし |
| バースト耐性 | 高い | 中程度 | サーバースペック依存 |

---

## どれを選ぶか

### 判断フロー

```
学習・個人開発？
  → Alchemy Free 一択

法人・本番サービス？
  └ 月 < 3億CU ？
      → Alchemy Free
  └ 月 3〜10億CU ？
      → Alchemy Growth/Scale ($49〜499/月)
  └ 外部にデータを出せないコンプライアンス？
      → 自前ノード
  └ 月 数十億CU 超 ？
      → Alchemy Enterprise vs 自前ノード（コスト比較）

高い信頼性が必要（法人）？
  → Alchemy + Infura デュアルプロバイダー
```

### このプロジェクトの選択

**Alchemy Free プラン**で十分です。

理由:
- 学習・開発目的
- `eth_getLogs`（15秒ポーリング）+ `eth_call`（残高照会）が主な用途
- 月3億 CU は個人開発では使い切れない

---

## Alchemyの設定手順

1. [alchemy.com](https://alchemy.com) でアカウント作成（無料）
2. ダッシュボードで「Create App」
3. Chain: Polygon、Network: Mainnet（本番）または Amoy（テスト）を選択
4. 「View Key」から HTTPS URL をコピー
5. `.env` に設定:

```bash
# .env（コミットしない）
WEB3J_POLYGON_ENDPOINT=https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY
WEB3J_ETHEREUM_ENDPOINT=https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
```

> ⚠️ APIキーは絶対に `.gitignore` で除外すること

---

## フォールバック戦略（本番環境向け）

本番では単一プロバイダーへの依存を避けることが推奨されます:

```java
// プライマリ: Alchemy → フォールバック: Infura
@Service
public class ResilientWeb3j {
    private final Web3j primary;
    private final Web3j fallback;

    public BigInteger getBalance(String address) {
        try {
            return primary.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                          .send().getBalance();
        } catch (Exception e) {
            log.warn("Primary RPC failed, using fallback");
            return fallback.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                           .send().getBalance();
        }
    }
}
```

このプロジェクトは現在シングルプロバイダーですが、本番化する際はデュアル構成を検討してください。

---

## Alchemy Enhanced API（知っておくと便利）

標準 JSON-RPC にない Alchemy 独自の API:

| API | 説明 |
|---|---|
| `alchemy_getTokenBalances` | 1アドレスの全トークン残高を一括取得 |
| `alchemy_getAssetTransfers` | 指定アドレスの全送受金履歴 |
| `alchemy_getTokenMetadata` | トークンの名前・シンボル・decimals |
| `alchemy_getNFTs` | ウォレットが持つNFT一覧 |

標準の `eth_getLogs` で全 Transfer を自前で解析するより  
`alchemy_getAssetTransfers` の方が簡単なケースもあります。

---

## 次に読むもの

→ [03-pos-and-finality.md](03-pos-and-finality.md)  
「バリデーターとファイナリティの技術詳細」
