# レポート08 — Sablier ストリーミング決済：時間とともに流れるお金

## はじめに

Phase 5 まで実装してきた決済の基本形は「一括送金」だった。  
ユーザーが署名 → バックエンドが `transferFrom` を呼ぶ → 全額が瞬時に移動する。

しかしサブスクリプション・給与・ベスティングのように「**時間に比例して支払いが発生する**」ユースケースは、  
一括送金では実現できない。毎月手動で送金するか、スケジューラーで `transferFrom` を繰り返すしかない。

Sablier はこの問題を解決した**ストリーミング決済プロトコル**だ。  
「お金が水道管を流れるように、毎秒単位でトークンが移動し続ける」という概念で設計されている。

本レポートでは、Sablier の仕組み・Spring Boot からの呼び出し方・JPYC との相性を整理する。

---

## 1. ストリーミング決済とは何か

### 従来の月次請求との比較

```
従来の月次請求:
  月初め        月末
  |              |
  受取人:  ○                    ○
  支払人:  ○ (全額引き落とし)   ○ (全額引き落とし)

  問題点:
  - 支払人は月初めに全額を渡してしまうリスク
  - 受取人は月末まで報酬を受け取れない
  - サービスが途中解約されても返金処理が複雑
```

```
ストリーミング決済（Sablier）:
  |------- 1ヶ月 -------|
  支払人:  ━━━━━━━━━━━━━  （毎秒少しずつ流れ出る）
  受取人:  ━━━━━━━━━━━━━  （毎秒少しずつ受け取れる）

  特徴:
  - 残高はリアルタイムで計算される（ブロックチェーン上のタイムスタンプを使用）
  - 解約したその秒まで分の報酬は受け取れる
  - 支払人はいつでも停止・解約できる
```

### 「毎秒」の意味

EVM の世界では「時間」は `block.timestamp` で表現される。  
Sablier は「ストリーム開始時刻からの経過秒数」に「毎秒レート」を掛けることで、引き出し可能な金額を計算する。

```
elapsed    = block.timestamp - stream.startTime
claimable  = elapsed × ratePerSecond

例:
  startTime     = 1,700,000,000（Unix タイムスタンプ）
  ratePerSecond = 0.000011574 JPYC/秒（= 1 JPYC/日）
  elapsed       = 86,400秒（= 1日）

  claimable = 86,400 × 0.000011574 = 0.999... ≈ 1 JPYC
```

実際の計算は `BigDecimal` ではなく 18 decimals の整数演算で行われるため、精度損失はない。

---

## 2. Sablier Flow vs Lockup の違い

Sablier は 2 種類のコントラクト群を提供している。目的が根本的に異なるため、選択ミスをしないよう理解しておく。

### 2-1. Lockup（期間固定ベスティング）

```
時間軸:
0%                           100%
|----------------------------|
開始                          終了（Cliff 解放）

主な用途:
  - 従業員ストックオプション（例: 4年ベスティング、1年クリフ）
  - ICO のトークンロックアップ
  - DAO の財務資金管理
```

特徴：

| 項目 | 内容 |
|---|---|
| 期間 | あらかじめ開始・終了を固定する |
| キャンセル | 発行者がキャンセルすると残りは返還 |
| バリアント | Linear / Dynamic / Tranched の 3 種 |
| ユースケース | 「X年後に全額解放」「線形に徐々に解放」 |

Lockup バリアントの詳細：

```
Lockup Linear（線形）:
  Amount
  1000 |          /
   500 |      /
     0 |-----------> 時間
         開始   終了

Lockup Dynamic（カスタム曲線）:
  Amount
  1000 |       ___/
   500 |    __/
     0 |___/-------> 時間

Lockup Tranched（段階解放）:
  Amount
  1000 |      |----
   500 |  |---
     0 |--|--------> 時間
         6M  12M
```

### 2-2. Flow（オープンエンド継続課金）

```
時間軸:
開始                     ?（いつでも停止可）
|========================...>

主な用途:
  - サブスクリプション課金（月額 X 円）
  - 給与ストリーム（毎日 Y 円）
  - DAO コントリビューター報酬
```

特徴：

| 項目 | 内容 |
|---|---|
| 期間 | 決まっていない（無期限） |
| 停止 | 支払人がいつでも停止・再開できる |
| 未払い | 残高不足になると自動で一時停止（PAUSED 状態） |
| ユースケース | 「月額 1,000 JPYC 課金を続ける」 |

### 選択基準

```
「期間と総額が最初から決まっている」→ Lockup
「いつ終わるかわからないが毎秒流したい」→ Flow
```

このプロジェクト（サブスク決済・サービス課金）では **Flow** が主な対象になる。

---

## 3. コントラクトの動作原理

### 3-1. ストリーム ID の発行

Sablier Flow コントラクトは NFT として各ストリームを管理する。  
ストリーム作成時に `uint256` 型のストリーム ID が発行される。

```
コントラクト内部:
  mapping(uint256 streamId => Stream) private _streams;

  struct Stream {
    address sender;        // 支払人
    address recipient;     // 受取人
    uint128 ratePerSecond; // 毎秒レート（token の decimals 単位）
    address token;         // ERC-20 トークンアドレス
    bool isTransferable;   // 受取人の NFT 移転可否
    uint40  snapshotTime;  // 最後に残高計算した時刻
    uint128 snapshotDebt;  // snapshotTime 時点の累積未払い額
    uint128 balance;       // 現在のコントラクト預かり残高
  }
```

```
ストリーム作成の流れ:
  1. sender が ERC-20 を approve（またはデポジット）
  2. flow.create(recipient, ratePerSecond, token, isTransferable) を呼ぶ
  3. コントラクトが新しい streamId を発行（uint256 のオートインクリメント）
  4. sender に NFT（ERC-721）を発行（所有証明）
  5. recipient も NFT を受け取る（引き出し権限の証明）
```

### 3-2. 残高計算（claimable amount）

Flow の残高計算は「スナップショット + 経過時間」のパターン。

```
withdrawableAmount(streamId) の計算:

  elapsed = block.timestamp - stream.snapshotTime
  accrued = elapsed × stream.ratePerSecond

  withdrawable = min(
    stream.snapshotDebt + accrued,  // 累積未払い
    stream.balance                   // 実際の預かり残高
  )
```

残高が枯渇すると `balance < debt` になり、`withdrawable = balance` となって  
以後は `PAUSED` 状態になる（支払人が追加デポジットするまで停止）。

```
状態遷移:

  create()
      |
      v
   STREAMING ──→ pause()  ──→ PAUSED
      |              ↑              |
      |          resume()           |
      |              └──────────────┘
      |
      v
   DEPLETED（balance が 0 になった）
```

### 3-3. 主要な関数シグネチャ

```solidity
// ストリーム作成（初期デポジットあり）
function createAndDeposit(
    address recipient,
    address token,
    uint128 ratePerSecond,
    bool    isTransferable,
    uint128 amount           // 初期に預け入れるトークン量
) external returns (uint256 streamId);

// 追加デポジット
function deposit(
    uint256 streamId,
    uint128 amount,
    address sender,
    address recipient
) external;

// 引き出し（recipient が呼ぶ）
function withdraw(
    uint256 streamId,
    address to,
    uint128 amount
) external;

// 引き出し可能残高の照会（view）
function withdrawableAmountOf(uint256 streamId)
    external view returns (uint128 withdrawableAmount);

// 一時停止（sender が呼ぶ）
function pause(uint256 streamId) external;

// 再開（sender が呼ぶ）
function restart(uint256 streamId, uint128 ratePerSecond) external;

// ボイド（強制終了・残高を両者に返還）
function void(uint256 streamId) external;
```

---

## 4. Spring Boot からの呼び出し方

### 4-1. ABI の取得と Web3j ラッパー生成

Sablier の ABI は GitHub で公開されている。  
`SablierFlow.json` を取得して Web3j CLI でラッパーを生成する。

```bash
# Sablier Flow ABI の取得（v2-core リポジトリから）
curl -o src/main/resources/abi/SablierFlow.json \
  https://raw.githubusercontent.com/sablier-labs/v2-core/main/out/SablierFlow.sol/SablierFlow.json

# Web3j CLI でラッパーを生成
web3j generate solidity \
  -a src/main/resources/abi/SablierFlow.json \
  -o src/main/java \
  -p com.web3pay.contract
```

生成されたラッパークラス `SablierFlow.java` を使って Spring Service から呼び出す。

### 4-2. コントラクトアドレス（Polygon Mainnet）

```
Sablier Flow v1.0（Polygon Mainnet）:
  アドレスは https://docs.sablier.com/contracts/flow/deployments で確認すること。

注意: コントラクトアドレスは必ずレビューエージェントに確認させる（CLAUDE.md セキュリティルール）
     変更時は必ず security/ ブランチを切ってレビューを通すこと。
```

### 4-3. StreamingPaymentService の実装イメージ

```java
package com.web3pay.chain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.ContractGasProvider;
import com.web3pay.contract.SablierFlow;

import java.math.BigInteger;

@Service
public class StreamingPaymentService {

    private final SablierFlow sablierFlow;

    // コントラクトアドレスは application.yml から注入（絶対にハードコードしない）
    public StreamingPaymentService(
            Web3j web3j,
            Credentials credentials,
            @Value("${sablier.flow.address}") String sablierFlowAddress,
            ContractGasProvider gasProvider) {
        this.sablierFlow = SablierFlow.load(
            sablierFlowAddress, web3j, credentials, gasProvider
        );
    }

    /**
     * ストリームを作成して初期デポジットを行う
     *
     * @param recipient     受取人アドレス（バリデーション済みであること）
     * @param tokenAddress  ERC-20 トークンアドレス（JPYC など）
     * @param ratePerSecond 毎秒レート（token の最小単位で指定）
     * @param initialAmount 初期デポジット量（最小単位）
     * @return Sablier が発行したストリーム ID
     */
    public BigInteger createStream(
            String recipient,
            String tokenAddress,
            BigInteger ratePerSecond,
            BigInteger initialAmount) throws Exception {

        // 先に ERC-20 の approve が必要（Permit で代替可能）
        var receipt = sablierFlow.createAndDeposit(
            recipient,
            tokenAddress,
            ratePerSecond,
            true,          // isTransferable: 受取人が NFT を転送できるか
            initialAmount
        ).send();

        // イベントログからストリーム ID を取り出す
        var events = SablierFlow.getCreateAndDepositEvents(receipt);
        if (events.isEmpty()) {
            throw new IllegalStateException(
                "ストリーム作成イベントが見つかりません: "
                + receipt.getTransactionHash());
        }
        return events.get(0).streamId;
    }

    /**
     * 引き出し可能残高を照会する（view 関数 = ガスなし）
     *
     * @param streamId Sablier のストリーム ID
     * @return 引き出し可能なトークン量（最小単位）
     */
    public BigInteger getWithdrawableAmount(BigInteger streamId) throws Exception {
        return sablierFlow.withdrawableAmountOf(streamId).send();
    }

    /**
     * 受取人が残高を引き出す
     *
     * @param streamId 引き出すストリームの ID
     * @param to       受け取るアドレス（通常は recipient）
     * @param amount   引き出す量（withdrawableAmountOf で取得した値以下）
     * @return トランザクションハッシュ
     */
    public String withdrawFromStream(
            BigInteger streamId,
            String to,
            BigInteger amount) throws Exception {

        var receipt = sablierFlow.withdraw(streamId, to, amount).send();
        return receipt.getTransactionHash();
    }

    /**
     * ストリームを一時停止する（支払人のみ可）
     */
    public String pauseStream(BigInteger streamId) throws Exception {
        var receipt = sablierFlow.pause(streamId).send();
        return receipt.getTransactionHash();
    }
}
```

### 4-4. レート計算ヘルパー

毎秒レート（ratePerSecond）は人間が直感的に扱いにくい。  
「月額 X JPYC」から逆算するユーティリティを実装しておく。

```java
package com.web3pay.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class StreamRateCalculator {

    private static final BigDecimal SECONDS_PER_DAY   = new BigDecimal("86400");
    private static final BigDecimal DAYS_PER_MONTH    = new BigDecimal("30");
    private static final BigDecimal SECONDS_PER_MONTH =
        SECONDS_PER_DAY.multiply(DAYS_PER_MONTH);

    /**
     * 月額金額（人間が読む形式）から毎秒レートを計算する
     *
     * @param monthlyAmount 月額（例: 1000.00 JPYC）
     * @param decimals      トークンの decimals（JPYC = 18）
     * @return 毎秒レート（最小単位の BigInteger）
     *
     * 例: 月額 1000 JPYC の場合
     *   ratePerSecond = 1000 × 10^18 / (30 × 86400)
     *                 = 1_000_000_000_000_000_000_000 / 2_592_000
     *                 ≒ 385_802_469_135 (wei/秒)
     */
    public static BigInteger fromMonthlyAmount(
            BigDecimal monthlyAmount, int decimals) {
        BigDecimal rawAmount = monthlyAmount
            .multiply(BigDecimal.TEN.pow(decimals));
        return rawAmount
            .divide(SECONDS_PER_MONTH, 0, RoundingMode.DOWN)
            .toBigInteger();
    }

    /**
     * ratePerSecond から月額表示に変換する（確認用）
     */
    public static BigDecimal toMonthlyAmount(
            BigInteger ratePerSecond, int decimals) {
        return new BigDecimal(ratePerSecond)
            .multiply(SECONDS_PER_MONTH)
            .divide(BigDecimal.TEN.pow(decimals), 6, RoundingMode.HALF_UP);
    }
}
```

```
使用例:
  月額 1,000 JPYC のサブスクを開始する場合:

  BigInteger rate = StreamRateCalculator.fromMonthlyAmount(
      new BigDecimal("1000"), 18  // JPYC は 18 decimals
  );
  // → 385_802_469_135 (wei/秒)

  逆変換で確認:
  BigDecimal monthly = StreamRateCalculator.toMonthlyAmount(rate, 18);
  // → 999.999... ≈ 1,000 JPYC/月（端数切り捨て分の誤差あり）
```

### 4-5. SablierStream エンティティ

DB 側でストリームの状態を管理するための JPA エンティティ。

```java
package com.web3pay.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sablier_streams")
public class SablierStream {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Sablier コントラクトが発行する uint256 の ID
    @Column(name = "stream_id", unique = true, nullable = false)
    private Long streamId;

    // 送金者アドレス（必ず ^0x[0-9a-fA-F]{40}$ 形式を検証すること）
    @Column(name = "wallet_address", length = 42, nullable = false)
    private String walletAddress;

    // 受取人アドレス
    @Column(name = "recipient_address", length = 42, nullable = false)
    private String recipientAddress;

    @Column(name = "token", length = 10, nullable = false)
    private String token;

    // 毎秒レート（最小単位を文字列で保持: double は精度損失の恐れがある）
    @Column(name = "rate_per_second", precision = 36, scale = 0, nullable = false)
    private BigDecimal ratePerSecond;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StreamStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    public enum StreamStatus {
        STREAMING,  // 流れている
        PAUSED,     // 一時停止（残高不足 or 手動）
        DEPLETED,   // 残高が 0 になった
        VOIDED      // 強制終了
    }
}
```

---

## 5. JPYC との相性

### ERC-20 であれば何でも使える設計

Sablier Flow は内部でトークン送受信に `IERC20.transferFrom` を使うため、  
**ERC-20 規格に準拠しているトークンであれば何でもストリーム化できる**。

```
Sablier Flow が実行する操作:
  デポジット時: token.transferFrom(sender, address(this), amount)
  引き出し時:   token.transfer(recipient, amount)

→ ERC-20 の transfer / transferFrom が正しく実装されていればOK
```

JPYC（JPY Coin）との相性確認：

| チェック項目 | JPYC の状況 | 判定 |
|---|---|---|
| ERC-20 準拠 | JPYCv2 は完全準拠 | 問題なし |
| 18 decimals | JPYC は 18 decimals | Sablier は全 decimals 対応 |
| EIP-2612 Permit | JPYCv2 は対応済み | デポジット時のガスレス化が可能 |
| ブロックリスト | JPYC にはアドレスブロック機能あり | ブロックされたアドレスは不可（想定内）|
| Polygon 対応 | JPYC は Polygon mainnet に存在 | Sablier も Polygon にデプロイ済み |

### EIP-2612 Permit との組み合わせ

デポジット時に `approve → deposit` の 2 トランザクションが必要になるが、  
JPYC は EIP-2612 対応済みなので Permit を使えばオフチェーン署名 1 回で済む。

```
通常フロー（2TX）:
  1. jpyc.approve(sablierFlowAddress, amount)  → TX1（ガス必要）
  2. sablierFlow.deposit(streamId, amount, ...) → TX2（ガス必要）

Permit フロー（1TX）:
  1. EIP-712 署名をオフチェーンで作成（ガスなし）
  2. sablierFlow.depositWithPermit(..., deadline, v, r, s) → TX1（ガス必要）
```

`depositWithPermit` 関数はデポジットと同時に `permit()` を呼ぶため、  
ユーザーは署名 1 回 + TX 1 回でストリームにトークンを追加できる。

---

## 6. ユースケース

### 6-1. サブスクリプション決済

```
シナリオ: 月額 1,000 JPYC のサービス契約

[ユーザー] ──createAndDeposit()──→ [Sablier Flow コントラクト]
                                       |
                                       | ratePerSecond = 385_802_469_135 wei/秒
                                       v
                                   毎秒 recipient に accumulated

マーチャント（recipient）が好きなタイミングで withdraw() を呼ぶ
  → 「使った分だけ」請求できる

利点:
  - 解約した瞬間まで分の JPYC は受け取れる（日割り計算が自動）
  - 前払いと後払いのリスクが均等になる
  - 残高が切れると自動で PAUSED → ユーザーへの自然な通知になる
```

### 6-2. 給与ストリーム（Payroll Streaming）

```
シナリオ: 日給 10,000 JPYC の雇用者が毎日受け取る

会社: 1ヶ月分の給与（300,000 JPYC）を事前デポジット
    → 従業員は毎秒 115_740_740 wei/秒 を受け取り始める

時間経過:
  1日後:  withdraw 可能 ≈ 10,000 JPYC
  15日後: withdraw 可能 ≈ 150,000 JPYC（累積）
  30日後: withdraw 可能 ≈ 300,000 JPYC（全額）

利点:
  - 従業員は給料日を待たずに出稼ぎ分をリアルタイムで引き出せる
  - 急な資金需要にも即時対応できる（マイクロファイナンス的な柔軟性）
```

### 6-3. ベスティングスケジュール（Lockup Linear）

```
シナリオ: プロジェクトトークン 1,000,000 枚を4年で線形解放

Lockup Linear:
  開始: 2026-01-01
  終了: 2030-01-01
  クリフ: 1年（最初の1年は引き出し不可）

  Time    | Unlocked Amount
  --------|------------------
  6ヶ月   | 0（クリフ期間中）
  12ヶ月  | 0 → 250,000（クリフ解放）
  24ヶ月  | 500,000（線形解放 1年分）
  36ヶ月  | 750,000（線形解放 2年分）
  48ヶ月  | 1,000,000（全解放）

利点:
  - 「チームが離脱したらトークンを返還する」仕組みが自動化される
  - スマートコントラクトが保証人 → 信頼不要
```

### 6-4. DAO 報酬・グラント

```
シナリオ: DAO がコントリビューターに毎月 500 JPYC の報酬を流す

DAO マルチシグウォレット
  → Sablier Flow にデポジット
    → コントリビューターへ毎秒流れ続ける

DAO が活動継続を評価している間は restart() / deposit() でリチャージ
活動が止まったら pause() / void() で停止

利点:
  - マイルストーン報酬と継続報酬を組み合わせられる
  - コントリビューターは「信用」ではなく「コード」を信頼できる
```

---

## 7. このプロジェクトとの関係

### Phase 6 での実装予定

PLAN.md の Day 26 に以下が記述されている：

```
feat: Sablier Flow連携（JPYCストリーミング・サブスク決済）

実装対象:
  - StreamingPaymentService.java
  - SablierStream.java（JPA エンティティ）

ユースケース:
  - 月額サブスクリプション（1秒単位で課金）
  - サービス利用時間に応じた従量課金
```

### 既存実装との統合イメージ

```
現在の実装:
  ユーザー → Permit 署名 → バックエンド → transferFrom → マーチャント
  （一括送金・一回限り）

Phase 6 追加後:
  ユーザー → Permit 署名 → バックエンド → Sablier.createAndDeposit
                                              |
                                              v 毎秒 flow
                                              |
                                              v
                                          マーチャント.withdraw()
  （継続課金・毎秒自動）
```

### SablierStream と PaymentOrder の設計分離

ストリーミング決済は PaymentOrder とはライフサイクルが根本的に異なるため、  
別エンティティとして管理する。

```
PaymentOrder（既存）:
  - 1注文 = 1回の送金（PENDING → CONFIRMED / EXPIRED）
  - txHash で確定を追跡
  - 期限（expiresAt）がある

SablierStream（Phase 6 新規）:
  - 1ストリーム = 継続的な流れ（STREAMING → PAUSED / DEPLETED / VOIDED）
  - streamId（uint256）でオンチェーンと紐づける
  - 終了期限はない（Flow の場合）
  - 引き出しトランザクションは受取人が任意のタイミングで実行
```

### バックエンドの監視責任

Sablier ストリームのバックエンド監視では、以下を `@Scheduled` ポーラーで監視する。

```
監視項目                  | 処理
--------------------------|----------------------------------------------
残高不足（DEPLETED）      | ユーザーに Webhook 通知・DB の status を更新
新規 withdraw イベント    | SablierStream.lastWithdrawAt を更新
ストリーム解約イベント    | SablierStream.status を VOIDED に更新
```

```java
// 監視ポーラーのイメージ（Phase 6 実装予定）
@Scheduled(fixedDelay = 15_000)
public void pollStreamStatuses() {
    List<SablierStream> activeStreams =
        sablierStreamRepository.findByStatus(StreamStatus.STREAMING);

    for (SablierStream stream : activeStreams) {
        BigInteger withdrawable = streamingPaymentService
            .getWithdrawableAmount(BigInteger.valueOf(stream.getStreamId()));

        if (withdrawable.equals(BigInteger.ZERO)) {
            stream.setStatus(StreamStatus.DEPLETED);
            webhookService.notify(stream, "DEPLETED");
        }
        sablierStreamRepository.save(stream);
    }
}
```

---

## まとめ

```
ストリーミング決済の本質:
  「お金を一括で渡す」→「お金を時間とともに流す」
  時間 × レート = 引き出し可能額（リアルタイムで計算）

Sablier の 2 種類:
  Flow    → オープンエンド。サブスク・給与・DAO報酬
  Lockup  → 期間固定。ベスティング・トークンロック

Spring Boot からの呼び出し:
  Web3j で SablierFlow.java ラッパーを生成
  createAndDeposit() でストリーム開始
  withdrawableAmountOf() で引き出し可能額を照会（ガスなし）
  withdraw() で実際に引き出し

JPYC との相性:
  ERC-20 であれば何でも動く設計 → JPYC もそのまま使える
  EIP-2612 Permit 対応 → デポジット時もガスレス化できる
  decimals = 18 → Sablier の内部計算と精度が合う

このプロジェクトでの位置づけ:
  Phase 6（Day 26）で SablierStream エンティティと
  StreamingPaymentService を実装予定
  PaymentOrder（一括送金）とは独立したライフサイクルで管理する
```

ストリーミング決済は「時間とお金の関係」を再定義する技術だ。  
「月末にまとめて払う」ではなく「使った分だけリアルタイムで流れる」──  
このモデルはサブスクリプション経済において、支払人と受取人の両方のリスクを低減する。  
JPYC × Sablier の組み合わせは「日本円建てのリアルタイム決済」として、  
このプロジェクトの最も重要な応用先になる。
