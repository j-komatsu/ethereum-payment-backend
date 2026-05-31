# レポート09 — ブロックチェーンアプリのテスト戦略

## はじめに

Phase 4（Transfer イベント監視）と Phase 5（EIP-2612 Permit 実装）を通じて、
このプロジェクトのテストコードが積み重なってきた。

ブロックチェーンアプリのテストには、通常の Web アプリには存在しない固有の難しさがある。
本レポートでは「なぜ難しいのか」から始まり、テスト戦略の階層、Web3j のモック設計、
Hardhat fork テスト、そしてこのプロジェクトの実際の設計判断までを体系的に整理する。

---

## 1. なぜブロックチェーンアプリのテストが難しいのか

### 1-1. 本番チェーンの不可逆性

通常の Web アプリでは、テスト後にデータベースをロールバックして初期状態に戻せる。
ブロックチェーンには「ロールバック」が存在しない。

```
通常のWebアプリテスト:
  テスト実行 → DB にデータ挿入 → @Transactional でロールバック → 初期状態に戻る

ブロックチェーンテスト（本番チェーンを直接使った場合）:
  テスト実行 → TX を送信 → チェーンに刻まれる → 永久に残る
               ↑
               「テスト用のTXです」という概念がない
               撤回・修正・削除はできない
```

もし誤ったコントラクトアドレスにトークンを送ったテストTXが本番チェーンに乗れば、
そのトークンは永遠に取り出せなくなる。

### 1-2. ガス代のコスト

本番チェーン（Ethereum Mainnet、Polygon Mainnet 等）でのトランザクションには実費がかかる。

```
テストで100回 permit() + transferFrom() を実行した場合（Polygon Mainnet）:
  単純なTX1回 ≈ 50,000〜150,000 gas
  baseFee ≈ 30 gwei 時: 1回あたり ≈ 0.0015〜0.0045 MATIC
  100回: 0.15〜0.45 MATIC ≈ 数十円〜数百円

  CI/CD で毎ビルド実行すると月間コストが膨らむ
  テストが失敗しても費用は返ってこない
```

### 1-3. 遅い確認時間

`@Scheduled` ジョブや確認待ちのコードは、ブロック生成時間に縛られる。

```
Polygon PoS:   ブロック間隔 ≈ 2秒
               20確認待ち  ≈ 40秒
Ethereum:      ブロック間隔 ≈ 12秒
               20確認待ち  ≈ 240秒

JUnit テストで「240秒待ってから assert」は実用的でない。
```

このプロジェクトの `TransferEventPoller` は `REQUIRED_CONFIRMATIONS = 20` を設定している。
本番チェーンを使ったテストでは、このポーラーが実際に動くまで数分待ち続けることになる。

### 1-4. 外部状態への依存

ブロックチェーンの状態（残高・nonce・コントラクトの内部状態）は外部環境に依存する。

```
「JPYC を 1000 JPYC 持っているアカウント」を前提にテストを書いても:
  - テスト実行のたびに残高が変わっているかもしれない
  - nonce は過去のTXで進んでいるかもしれない
  - コントラクトが（ごくまれに）アップグレードされているかもしれない
```

これらの問題が、ブロックチェーンアプリのテストを難しくする根本的な理由である。

---

## 2. テスト戦略の階層

解決策は「チェーンに接続せずに何を検証し、どのレイヤーでチェーンを模倣するか」を
明確に分けることである。

```
テスト戦略のピラミッド

                    ┌─────────────────┐
                    │   Testnet       │  ← 本物に近いが遅い・コスト大
                    │（Polygon Amoy）  │
                    └────────┬────────┘
                  ┌──────────┴──────────┐
                  │   Fork テスト        │  ← 本番コントラクトをローカルで実行
                  │（Hardhat / Anvil）   │
                  └──────────┬──────────┘
              ┌──────────────┴──────────────┐
              │     統合テスト               │  ← Spring コンテキスト全体を起動
              │（@SpringBootTest + MockMvc）  │
              └──────────────┬──────────────┘
          ┌──────────────────┴──────────────────┐
          │          単体テスト                   │  ← 高速・外部依存なし
          │（@ExtendWith(MockitoExtension.class)） │
          └──────────────────────────────────────┘
```

| レイヤー | 速度 | 何を検証するか | このプロジェクトの実装 |
|---|---|---|---|
| 単体テスト | 数ミリ秒 | ロジック・計算・入力検証 | `PermitServiceTest`, `ChainServiceTest` |
| 統合テスト | 数秒 | HTTP層・Spring Bean間の配線 | `ChainControllerTest` |
| Fork テスト | 数十秒 | 本物のコントラクトとの互換性 | Day 24-25 で実装予定 |
| Testnet | 分〜時間 | E2E・本番前の最終確認 | PoC 段階では省略 |

**実用上の原則:** 単体テストで検証できるものは単体テストで検証する。
Fork テストはモックでは検証できないものだけに使う。

---

## 3. Web3j のモック戦略

Spring Boot + Web3j の構成では、どのレイヤーでモックするかを明確に決める必要がある。

### 3-1. モックの選択肢

```
Spring Boot アプリ
    ↓
Service クラス（PermitService, ChainService）
    ↓
Web3j インターフェース（org.web3j.protocol.Web3j）
    ↓
HttpService / WebSocketService
    ↓
実際の RPC エンドポイント（Alchemy, Infura, localhost）
```

**選択肢A: Web3j インターフェースをモック（推奨）**

```java
@ExtendWith(MockitoExtension.class)
class ChainServiceTest {

    @Mock
    private Web3j web3j;  // Web3j インターフェースを Mockito でモック

    @InjectMocks
    private ChainService chainService;

    @SuppressWarnings("unchecked")
    private void mockWeb3jBalance(String hexWei) throws IOException {
        EthGetBalance ethGetBalance = new EthGetBalance();
        ethGetBalance.setResult(hexWei);
        Request<?, EthGetBalance> request = mock(Request.class);
        when(request.send()).thenReturn(ethGetBalance);
        when(web3j.ethGetBalance(anyString(), any())).thenReturn((Request) request);
    }

    @Test
    void getEthBalance_returnsCorrectEthAndWei() throws IOException {
        // 1 ETH = 10^18 wei = 0xde0b6b3a7640000
        mockWeb3jBalance("0xde0b6b3a7640000");

        EthBalanceResponse response = chainService.getEthBalance(ADDRESS);

        assertThat(new BigDecimal(response.balanceEth())).isEqualByComparingTo("1");
    }
}
```

**選択肢B: RPC レイヤーをモック（Wire レベル）**

```java
// OkHttp を使ったワイヤーレベルのモック（MockWebServer 等を使用）
MockWebServer server = new MockWebServer();
server.enqueue(new MockResponse()
    .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0xde0b6b3a7640000\"}")
    .addHeader("Content-Type", "application/json"));

Web3j web3j = Web3j.build(new HttpService("http://localhost:" + server.getPort()));
```

### 3-2. どちらを選ぶか

| 観点 | Web3j インターフェースモック | RPC レイヤーモック |
|---|---|---|
| セットアップの簡単さ | 容易（Mockito のみ） | やや複雑（MockWebServer 等が必要） |
| テストの速度 | 最速 | 速いが若干遅い |
| 何を検証できるか | サービス層のロジック | JSON-RPC シリアライゼーション含む |
| Web3j のバグを踏むか | 踏まない | 踏む可能性がある |
| 推奨ケース | ユニットテスト全般 | Web3j の使い方が正しいか確認したい時 |

このプロジェクトでは **Web3j インターフェースをモック** する選択をしている。
理由は、検証したいのは「サービス層のロジックが正しいか」であり、
「Web3j が JSON を正しくパースするか」は Web3j ライブラリ側の責務だからである。

### 3-3. Web3j モックの落とし穴

Web3j の `Request` クラスは generic 型（`Request<O, T>`）を持つため、
Mockito でモックするとコンパイル警告が出る。

```java
// 正しい書き方: @SuppressWarnings("unchecked") を付けてキャスト
@SuppressWarnings("unchecked")
Request<?, EthGetBalance> request = mock(Request.class);
when(request.send()).thenReturn(ethGetBalance);
when(web3j.ethGetBalance(anyString(), any())).thenReturn((Request) request);
```

`@SuppressWarnings("unchecked")` を省略するとコンパイラ警告が大量に出るが、
動作には影響しない。メソッド単位で適用してスコープを絞るのが推奨。

---

## 4. Hardhat fork テスト

### 4-1. なぜ fork テストが必要か

Web3j のモックテストには根本的な限界がある。

```
モックテストで検証できないもの:

  permitName の正確な文字列
    ↓ "JPYCoin" vs "JPY Coin" の1文字の違いで
      ドメインセパレータが全く異なる → permit TX が必ずリバート

  コントラクトアドレスの正確性
    ↓ 見当違いのアドレスに TX を送っても
      Mockito はエラーを返さない

  ABI の引数順
    ↓ permit(owner, spender, value, deadline, v, r, s)
      vs permit(owner, spender, deadline, value, v, r, s)
      モックはどちらも受け付けてしまう

  ガス設定の妥当性
    ↓ maxFeePerGas が低すぎると TX がドロップ
      モックは常に成功を返す
```

これらは全て「本物のコントラクトバイトコードを実行して初めてわかる」種類のバグである。

### 4-2. Hardhat fork の仕組み

```
①  Alchemy 経由で Polygon Mainnet の状態をスナップショット取得
       - JPYC コントラクトのバイトコード
       - 各アドレスの残高・nonce
       - ブロック番号・baseFee など

②  ローカルマシンに偽のノード（Hardhat node）を起動
       - localhost:8545 で JSON-RPC を受け付ける

③  Spring Boot テストの Web3j を localhost:8545 に向ける

④  テスト実行
       - impersonateAccount で JPYC 保有者になりすます
       - setBalance でスペンダーウォレットにガス代を付与
       - permit() + transferFrom() を実際に実行
       - オンチェーンの状態変化（残高移動）を確認
```

### 4-3. hardhat.config.ts の設定例

```typescript
import { HardhatUserConfig } from "hardhat/config";

const config: HardhatUserConfig = {
  networks: {
    hardhat: {
      forking: {
        url: process.env.ALCHEMY_POLYGON_URL ?? "",
        // ブロック番号を固定してテストを再現可能にする
        blockNumber: 70000000,
      },
      chainId: 137,
    },
  },
};

export default config;
```

`blockNumber` を固定することが重要である。
固定しないと、実行のたびに最新ブロックからフォークされ、
コントラクトの状態が変わってテストが非決定的になる。

### 4-4. impersonateAccount と setBalance

```typescript
// Hardhat の特殊な JSON-RPC メソッド（本番チェーンには存在しない）

// JPYC を大量に持っているアドレスになりすます
await hre.network.provider.request({
  method: "hardhat_impersonateAccount",
  params: ["0xJPYCWhaleAddress"],
});

// スペンダーウォレットにガス代（MATIC）を付与
await hre.network.provider.send("hardhat_setBalance", [
  "0xSpenderAddress",
  "0x56BC75E2D63100000", // 100 MATIC in wei（hex）
]);
```

Java 側から呼び出す場合（Web3j の `Request` でラップする方法）:

```java
// Web3j のカスタムメソッド呼び出し
Request<?, org.web3j.protocol.core.methods.response.VoidType> request =
    new Request<>(
        "hardhat_impersonateAccount",
        List.of("0xJPYCWhaleAddress"),
        web3jService,
        org.web3j.protocol.core.methods.response.VoidType.class
    );
request.send();
```

---

## 5. Anvil（Foundry）vs Hardhat の比較

Hardhat fork の代替として、Foundry の Anvil がある。
両者は同じ用途（ローカルの EVM テストノード + フォーク）で使えるが、特性が異なる。

### 5-1. 起動速度と実行速度

```
Hardhat（TypeScript / Node.js）:
  起動: 3〜10秒（node_modules のロード含む）
  TX処理: ≈ 数十〜数百ミリ秒/TX

Anvil（Rust 実装）:
  起動: < 1秒
  TX処理: ≈ 数〜数十ミリ秒/TX（10〜100倍速い）
```

CI/CD で毎回フォークノードを起動する場合、Anvil の方が総テスト時間を短縮できる。

### 5-2. 機能の比較

| 機能 | Hardhat | Anvil |
|---|---|---|
| フォーク（mainnet fork） | あり | あり |
| impersonateAccount | `hardhat_impersonateAccount` | `anvil_impersonateAccount` |
| setBalance | `hardhat_setBalance` | `anvil_setBalance` |
| スナップショット | `evm_snapshot` / `evm_revert` | `evm_snapshot` / `evm_revert` |
| Solidity テスト（forge test） | なし | あり（Foundry エコシステム） |
| JavaScript/TypeScript 統合 | 豊富（ethers.js, viem） | プラグインで対応 |
| Java からの呼び出し | JSON-RPC 経由で可能 | 同上 |

### 5-3. どちらを使うか

**Java/Spring Boot プロジェクトでは、どちらも JSON-RPC 経由で操作するため実質的に等価である。**

```
選択の基準:
  - フロントエンドチームが Hardhat を使っている → Hardhat で統一
  - 速度を優先したい → Anvil
  - Solidity テスト（forge）も書きたい → Foundry/Anvil
  - Node.js のエコシステムに慣れている → Hardhat
```

このプロジェクトでは当初 Hardhat を想定しているが、
Java バックエンドのみで使うのであれば Anvil の方がシンプルかつ高速である。

### 5-4. Anvil のコマンドライン例

```bash
# Polygon Mainnet をブロック番号 70000000 でフォーク
anvil \
  --fork-url https://polygon-mainnet.g.alchemy.com/v2/YOUR_KEY \
  --fork-block-number 70000000 \
  --chain-id 137 \
  --port 8545

# Spring Boot テストから接続
# application-test.yml:
#   chain.137.rpc-url: http://localhost:8545
```

---

## 6. @Scheduled ジョブのテスト方法

`TransferEventPoller` のような `@Scheduled` ジョブには、
時刻に依存するロジックが含まれることがある（タイムアウト・TTL・有効期限など）。

### 6-1. 問題：`Instant.now()` への直接依存

```java
// テストしにくいコード例
public boolean isExpired(PaymentOrder order) {
    return Instant.now().isAfter(order.getExpiresAt());  // Instant.now() に直接依存
}
```

このコードのテストでは「現在時刻が注文の有効期限を過ぎている」状態を作るために、
`Thread.sleep()` で実際の時間が経過するのを待つか、有効期限を過去に設定するしかない。
前者はテストを遅くし、後者はテストデータの意図が不明確になる。

### 6-2. 解決策：Clock を注入する

Java 標準ライブラリの `java.time.Clock` を使ってテスト時に時刻を固定する。

```java
// 本番コード
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Clock clock;  // 注入された Clock を使う

    public boolean isExpired(PaymentOrder order) {
        return Instant.now(clock).isAfter(order.getExpiresAt());
    }
}
```

```java
// 本番用の Bean 設定
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();  // 本番環境では実際の時刻を返す Clock
    }
}
```

```java
// テスト用の設定
@TestConfiguration
public class TestClockConfig {

    // 固定された時刻の Clock を定義
    public static final Instant FIXED_TIME =
        Instant.parse("2026-05-31T12:00:00Z");

    @Bean
    @Primary  // 本番の Clock Bean を上書き
    public Clock clock() {
        return Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
    }
}
```

```java
// テストクラスでの使い方
@SpringBootTest
@Import(TestClockConfig.class)  // テスト用 Clock を注入
class PaymentExpiryTest {

    @Autowired
    private PaymentService paymentService;

    @Test
    void order_expiredBefore_fixedTime_isExpired() {
        PaymentOrder order = PaymentOrder.builder()
            // 固定時刻より1秒前に期限切れになるように設定
            .expiresAt(TestClockConfig.FIXED_TIME.minusSeconds(1))
            .build();

        assertThat(paymentService.isExpired(order)).isTrue();
    }

    @Test
    void order_expiredAfter_fixedTime_isNotExpired() {
        PaymentOrder order = PaymentOrder.builder()
            // 固定時刻より1秒後に期限切れになるように設定
            .expiresAt(TestClockConfig.FIXED_TIME.plusSeconds(1))
            .build();

        assertThat(paymentService.isExpired(order)).isFalse();
    }
}
```

### 6-3. @Scheduled ジョブ自体のテスト

`@Scheduled` アノテーションが付いたメソッドは、Spring コンテキストが管理するスケジューラーが呼び出す。
ユニットテストでは `@Scheduled` を無視して、メソッドを直接呼び出すだけでよい。

```java
@ExtendWith(MockitoExtension.class)
class TransferEventPollerTest {

    @Mock
    private ChainRegistry chainRegistry;

    @Mock
    private PollerStateRepository pollerStateRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private TransferEventPoller poller;

    @Test
    void poll_whenDisabled_doesNothing() {
        ReflectionTestUtils.setField(poller, "pollerEnabled", false);

        // @Scheduled は呼び出さず、メソッドを直接実行
        poller.poll();

        // Web3j が呼ばれていないことを確認
        verifyNoInteractions(chainRegistry);
    }
}
```

### 6-4. ポーリング間隔のテスト

スケジューリングが正しく設定されているかを確認したい場合は、
`@SpringBootTest` + `Awaitility` の組み合わせを使う。

```java
@SpringBootTest
class TransferEventPollerSchedulingTest {

    @MockBean
    private ChainRegistry chainRegistry;  // 外部依存をモックに置き換え

    @SpyBean
    private TransferEventPoller poller;   // 実際の Bean を Spy で監視

    @Test
    void poll_isInvokedByScheduler() {
        // Awaitility で非同期に「呼ばれるまで待つ」
        await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(poller, atLeastOnce()).poll()
            );
    }
}
```

ただし、このテストは Spring コンテキストを起動するため数秒かかる。
また `poller.interval-ms` を短くした `application-test.yml` が必要である。

---

## 7. このプロジェクトの実際のテスト構成

### 7-1. テストファイルの構成

```
src/test/java/com/web3pay/
├── Web3PayApplicationTests.java          // Spring コンテキスト起動確認
├── util/
│   └── TokenAmountConverterTest.java     // 純粋な計算ロジック
├── auth/
│   └── SiweServiceTest.java              // SIWE 署名検証（Web3j 暗号ライブラリを使用）
├── payment/
│   └── PaymentServiceTest.java           // 支払いステータス管理
└── chain/
    ├── ChainServiceTest.java             // Web3j モック（残高取得）
    ├── ChainControllerTest.java          // HTTP 層の統合テスト
    ├── TokenBalanceControllerTest.java   // 同上
    └── permit/
        └── PermitServiceTest.java        // EIP-712 暗号演算の検証
```

### 7-2. PermitServiceTest の設計判断

`PermitServiceTest` は、このプロジェクトで最も深く考えられたテストクラスである。

**検証対象の選択**

```java
// EIP-712 の核心的なセキュリティ不変条件を直接テスト:
// 「署名したアドレスが正しく復元できる」

@Test
void buildDigest_roundTrip_signatureVerifies() throws Exception {
    ECKeyPair ownerKeyPair = Keys.createEcKeyPair();
    String ownerAddress = "0x" + Keys.getAddress(ownerKeyPair);

    byte[] digest = permitService.buildDigest(
            domainSeparator, ownerAddress, spenderAddress, value, nonce, deadline);

    // ダイジェストに署名する（追加ハッシュなし — EIP-712 ダイジェストは既に keccak256）
    Sign.SignatureData sigData = Sign.signMessage(digest, ownerKeyPair, false);
    BigInteger recoveredKey = Sign.signedMessageHashToKey(digest, sigData);
    String recovered = "0x" + Keys.getAddress(recoveredKey);

    assertThat(recovered).isEqualToIgnoringCase(ownerAddress);
}
```

このテストは「EIP-712 の数学が正しく実装されているか」を、
実際の楕円曲線演算（`Keys.createEcKeyPair()`、`Sign.signMessage()`）を使って検証している。
Mockito は一切使わない。モックを使う必要がないからである。

**モックを最小限にした理由**

```java
@BeforeEach
void setUp() throws Exception {
    ECKeyPair spenderKeyPair = Keys.createEcKeyPair();
    String spenderPrivateKey = Numeric.toHexStringNoPrefix(spenderKeyPair.getPrivateKey());
    // PermitService をテスト用の秘密鍵で直接インスタンス化
    permitService = new PermitService(chainRegistry, orderRepository, spenderPrivateKey);
}
```

`ChainRegistry` と `PaymentOrderRepository` は `@Mock` だが、
EIP-712 の計算テスト（`computeDomainSeparator`, `buildDigest`）では
これらのモックには一切スタブが必要ない。呼ばれないからである。

**何をモックしていないか、その意味**

| テスト対象 | モックを使わない理由 |
|---|---|
| `computeDomainSeparator` | 純粋な ABI エンコード + keccak256。外部依存なし |
| `buildDigest` | 同上。EIP-712 の数学のみ |
| 楕円曲線署名の round-trip | Web3j の暗号演算が正しいかを確認したい。モックすると意味がなくなる |

**例外系のテスト設計**

```java
@Test
void buildTypedData_noSpenderConfigured_throwsPermitException() {
    // 空文字列の秘密鍵で PermitService を作る（設定ミスのシミュレーション）
    PermitService noSpender = new PermitService(chainRegistry, orderRepository, "");
    assertThatThrownBy(() -> noSpender.buildTypedData("order1", "0x" + "1".repeat(40)))
            .isInstanceOf(PermitException.class)
            .hasMessageContaining("Spender wallet not configured");
}

@Test
void execute_pastDeadline_throwsPermitException() {
    // 確実に過去の時刻を使う（Clock 注入不要）
    long pastDeadline = Instant.now().minusSeconds(60).getEpochSecond();
    assertThatThrownBy(() -> permitService.execute("order1", "0x" + "1".repeat(40),
                                                   pastDeadline, "0", "0x" + "0".repeat(130)))
            .isInstanceOf(PermitException.class)
            .hasMessageContaining("Deadline has already passed");
}
```

`execute_pastDeadline_throwsPermitException` は Clock を注入せずに
`Instant.now().minusSeconds(60)` を使っている。
これは「60秒前は確実に過去である」という事実に依存しており、
Clock を注入するほどの複雑さがないためこの選択は妥当である。

**SiweServiceTest との設計の共通点**

```java
// SiweServiceTest も同じ設計哲学:
// 実際の楕円曲線演算を使って「署名が正しく検証される」ことを確認する

@Test
void verify_validSignature_returnsJwt() throws Exception {
    ECKeyPair keyPair = Keys.createEcKeyPair();
    String address = "0x" + Keys.getAddress(keyPair);
    // ...
    String hexSig = signMessage(message, keyPair);  // 実際に署名する
    String jwt = siweService.verify(message, hexSig, address);
    assertThat(jwt).isNotBlank();
}

@Test
void verify_wrongSigner_throwsSiweException() throws Exception {
    ECKeyPair claimedKeyPair = Keys.createEcKeyPair();
    ECKeyPair signerKeyPair = Keys.createEcKeyPair();  // 意図的に異なるキーペア
    // claimedAddress の秘密鍵ではなく signerKeyPair で署名
    String hexSig = signMessage(message, signerKeyPair);
    assertThatThrownBy(() -> siweService.verify(message, hexSig, claimedAddress))
            .isInstanceOf(SiweException.class)
            .hasMessageContaining("Signature verification failed");
}
```

**共通する設計哲学:** 「暗号演算はモックしない。実際に鍵を生成し、実際に署名し、実際に検証する。」

### 7-3. モックの使い方の整理

このプロジェクトのテストを横断すると、モックの役割が明確に分かれている。

```
モックする（Mockito）:
  Web3j        → 実際のノードに繋がずにレスポンスを制御
  Repository   → DB 接続なしで永続化層の動作を定義
  ChainRegistry → 複数チェーンへの接続を切り離す

モックしない（実際のコードを実行）:
  楕円曲線暗号（Keys, Sign）   → これをモックすると「署名が正しいか」を検証できない
  JwtService                 → トークン生成・検証は実際のロジックをテスト
  BigInteger の計算            → 純粋な計算はモック不要
  keccak256 ハッシュ計算        → 同上
```

---

## まとめ

```
ブロックチェーンアプリのテストが難しい理由:
  不可逆性（ロールバック不可）
  ガス代コスト
  遅い確認時間（秒〜分）
  外部状態への依存

解決策はテスト戦略の階層化:
  単体テスト → ロジックの正しさ（Web3j モック）
  統合テスト → Spring Bean の配線
  Fork テスト → 本番コントラクトとの互換性（Hardhat/Anvil）
  Testnet → E2E 確認

Web3j のモック設計:
  Web3j インターフェースをモックする（RPC レイヤーより上位）
  暗号演算はモックしない — 実際に鍵生成・署名・検証を行う

Fork テスト（Hardhat/Anvil）:
  Alchemy で本番チェーンをスナップショット
  impersonateAccount で残高を持つアカウントになりすます
  setBalance でテスト用ウォレットにガスを付与
  ブロック番号を固定してテストを再現可能にする

@Scheduled ジョブ:
  Clock を注入してテスト時に時刻を固定する
  @TestConfiguration + @Primary で Clock Bean を上書き
  ジョブのロジックと、スケジューリング自体のテストは分離する

このプロジェクトの選択:
  「暗号演算はモックしない」が中心的な設計哲学
  PermitServiceTest と SiweServiceTest はどちらも実際の楕円曲線演算を実行する
  Fork テスト（Day 24-25）で「本物の JPYC コントラクトで動くか」を検証する
```

テスト戦略の核心は「何をテストしたいか」を明確にすることである。
モックは外部依存を切り離すためにあり、ロジック自体をモックすることに意味はない。
ブロックチェーンアプリでは特に「暗号演算の正確さ」が決定的に重要であり、
それは実際のライブラリを使って検証しなければならない。
