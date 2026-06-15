# 技術者向けガイド — 実装・設計レベルの参照資料

> 🔧 技術者向け  
> 前提: 初心者・中級者ガイドを読了済み、または Java/Spring Boot の開発経験あり

---

## このプロジェクトの技術レポート

実装・設計レベルで理解したい場合は以下の技術レポートを参照してください。

| # | レポート | 主なテーマ |
|---|---|---|
| 01 | [Ethereum基礎](../../report-01-ethereum-basics.md) | ブロック構造・EVM・ガス・ノード種別・JSON-RPC |
| 02 | [アカウントとWeb3j](../../report-02-accounts-and-web3j.md) | EOA/CA・Web3j接続・秘密鍵・署名 |
| 03 | [ERC-20とステーブルコイン](../../report-03-erc20-stablecoins.md) | ERC-20標準・JPYC・decimals・approve |
| 04 | [イベント監視](../../report-04-events-and-monitoring.md) | Transfer Event・eth_getLogs・ポーリング・ファイナリティ |
| 05 | [ウォレット認証とスマコン](../../report-05-wallet-auth-and-smartcontract.md) | SIWE・EIP-712・EIP-2612 Permit |
| 06 | [ステーブルコイン設計思想](../../report-06-stablecoins.md) | 法定担保型・アルゴ型・リスク比較 |
| 07 | [DeFiの仕組み](../../report-07-defi-mechanisms.md) | AMM・Uniswap・流動性プール |
| 08 | [Sablierストリーミング](../../report-08-sablier-streaming.md) | Sablier Flow・毎秒決済・ストリーム管理 |
| 09 | [テスト戦略](../../report-09-testing-strategy.md) | JUnit・Mockito・Hardhat fork テスト |
| 10 | [ERC-20とガスコスト](../../report-10-erc20-and-gas.md) | ガスコスト詳解・EIP-2612の経済的優位性 |
| 11 | [なぜEthereum？](../../report-11-why-ethereum.md) | 全チェーン比較・Solana・BNB Chain等 |
| 12 | [なぜブロックチェーン？](../../report-12-why-blockchain.md) | 従来システムとの本質的違い・ユースケース |
| 16 | [Layer 2完全解説](../../report-16-layer2-complete-guide.md) | Rollup・Optimism・zkSync・Polygon |
| 17 | [Web3決済 vs 従来決済](../../report-17-web3-payment-vs-traditional.md) | Stripe・銀行振込との比較・シーケンス図 |
| 18 | [アカウント抽象化](../../report-18-account-abstraction.md) | EIP-4337・EIP-7702・UX改善 |
| 19 | [スマコンセキュリティ](../../report-19-smart-contract-security.md) | Reentrancy・The DAO・OpenZeppelin |
| 20 | [Ethereumロードマップ](../../report-20-ethereum-roadmap.md) | PoS・EIP-4844・Danksharding・将来展望 |

---

## 実装ノート（設計判断の記録）

なぜこう実装したか、どこで迷ったかを記録した開発者向けドキュメント。

| フェーズ | ノート |
|---|---|
| Phase 0-1 | [初期設定・セキュリティ強化](../../implementation-notes/phase0-initial-setup.md) |
| Phase 2 | [Web3j接続・Chain API設計](../../implementation-notes/phase2-web3j-chain-api.md) |
| Phase 3 | [ERC-20残高API](../../implementation-notes/phase3-erc20-token-balance.md) |
| Phase 4 | [Transferイベントポーラー設計](../../implementation-notes/phase4-transfer-event-poller.md) |
| Phase 5 | [SIWE認証・EIP-2612 Permit実装](../../implementation-notes/phase5-siwe-auth.md) |
| Phase 6 | [Sablierストリーミング・ShedLock・OpenAPI](../../implementation-notes/phase6-streaming-autopurchase-openapi.md) |

---

## 外部リファレンス

### Web3j / Java

- [Web3j公式ドキュメント](https://docs.web3j.io/)
- [Web3j GitHub](https://github.com/hyperledger/web3j)
- [Spring Boot + Web3j サンプル](https://docs.web3j.io/4.8.7/quickstart/)

### JSON-RPC / Ethereum API

- [Ethereum JSON-RPC仕様](https://ethereum.org/en/developers/docs/apis/json-rpc/)
- [Alchemy API リファレンス](https://docs.alchemy.com/reference/ethereum-api-quickstart)
- [Alchemy Compute Units計算表](https://docs.alchemy.com/reference/compute-units)

### EIP（重要な標準）

| EIP | 内容 |
|---|---|
| [EIP-20](https://eips.ethereum.org/EIPS/eip-20) | ERC-20 トークン標準 |
| [EIP-712](https://eips.ethereum.org/EIPS/eip-712) | 構造化データへの署名 |
| [EIP-2612](https://eips.ethereum.org/EIPS/eip-2612) | Permit（ガスレスapprove）|
| [EIP-4337](https://eips.ethereum.org/EIPS/eip-4337) | Account Abstraction |
| [EIP-4844](https://eips.ethereum.org/EIPS/eip-4844) | Proto-Danksharding（blob）|
| [EIP-7702](https://eips.ethereum.org/EIPS/eip-7702) | Pectra: EOAのスマートコントラクト化 |

### ノード実装（自前ノード検討時）

| クライアント | 言語 | 特徴 |
|---|---|---|
| **Geth** | Go | 最も広く使われる実行レイヤー実装 |
| **Nethermind** | C# | エンタープライズ・アーカイブ対応 |
| **Besu** | Java | Hyperledger / エンタープライズ向け |
| **Reth** | Rust | 高性能・新世代実装 |
| **Prysm** | Go | コンセンサスレイヤー（バリデーター）|
| **Lighthouse** | Rust | コンセンサスレイヤー |

> The Merge 以降、フルノード = 実行レイヤー + コンセンサスレイヤーの2種類が必要

### Polygon / JPYC

- [Polygon Developer Docs](https://docs.polygon.technology/)
- [JPYC（日本円ステーブルコイン）](https://jpyc.jp/)
- [Sablier Flow ドキュメント](https://docs.sablier.com/guides/flow/)

---

## 推奨する学習順序（技術者向け）

```
① guides/intermediate/ を読了（JSON-RPC・プロバイダー・PoS）
② report-01（Ethereum基礎）で技術的な土台を固める
③ report-02（Web3j）でコード実装の全体像を掴む
④ report-04（イベント監視）でこのプロジェクトの核心を理解
⑤ report-05（SIWE・Permit）で認証・決済の実装を理解
⑥ implementation-notes で設計判断の経緯を確認
⑦ 気になるレポートを必要に応じて参照
```
