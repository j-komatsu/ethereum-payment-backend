# レポート10 — ERC-20 標準とガスコストの詳解

## はじめに

ERC-20 はEthereum エコシステムの根幹をなすトークン標準であり、USDC・USDT・DAI・JPYC  
を含むほぼすべての主要トークンがこの規格に準拠している。  
本レポートでは ERC-20 の各インターフェースの意味、approve/transferFrom の設計思想、  
ガスの仕組みと EIP-1559 による変化、Polygon との比較、EIP-2612 Permit によるガスレス化、  
そして実践的なガス最適化テクニックを体系的に整理する。

---

## 1. ERC-20 標準インターフェース

ERC-20 は EIP-20 として仕様化されたインターフェース規約であり、  
準拠するコントラクトは以下のメソッドとイベントを実装する義務がある。

### 必須メソッド一覧

```
interface IERC20 {
    // ---- 残高・供給量 ----
    function totalSupply() external view returns (uint256);
    function balanceOf(address account) external view returns (uint256);

    // ---- 送金 ----
    function transfer(address to, uint256 amount) external returns (bool);
    function transferFrom(address from, address to, uint256 amount) external returns (bool);

    // ---- 承認 ----
    function approve(address spender, uint256 amount) external returns (bool);
    function allowance(address owner, address spender) external view returns (uint256);

    // ---- イベント ----
    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);
}
```

### オプションメソッド（事実上の必須）

```
function name()     external view returns (string);   // "USD Coin"
function symbol()   external view returns (string);   // "USDC"
function decimals() external view returns (uint8);    // 6
```

decimals は仕様上オプションだが、ERC-20 を扱うすべてのフロントエンドや  
DEX は実装を前提にしており、省略すると互換性が失われる。

### 各メソッドの詳細

#### balanceOf(address account)

ストレージから1スロットを読み出す最も単純な操作。  
`eth_call` でノードにシミュレーション実行させるだけで済むためガス代はかからない。

```
内部ストレージの概念図:
  mapping(address => uint256) _balances;

  balanceOf(0xAlice) = _balances[0xAlice]
                     = SLOAD(keccak256(0xAlice ++ slot0))
```

EVM はアドレスとスロット番号を連結してハッシュを計算し、その位置のストレージを読み出す。

#### transfer(address to, uint256 amount)

送信者（`msg.sender`）から `to` へ直接送金する。  
「from」の指定がなく、常にトランザクション送信者が支払い元になる。

```
transfer の動作:
  require(_balances[msg.sender] >= amount)
  _balances[msg.sender] -= amount;   // SSTORE（書き込み）
  _balances[to]         += amount;   // SSTORE（書き込み）
  emit Transfer(msg.sender, to, amount);
```

ガス消費の大部分は SSTORE 2回分であり、約 65,000 gas を要する。

#### approve(address spender, uint256 amount)

`msg.sender` が `spender` に対して「最大 amount まで引き落としてよい」と許可する。  
この承認はオンチェーンの mapping に記録される。

```
approve の動作:
  _allowances[msg.sender][spender] = amount;  // SSTORE
  emit Approval(msg.sender, spender, amount);
```

#### transferFrom(address from, address to, uint256 amount)

`spender`（`msg.sender`）が `from` の残高から `to` へ送金する。  
事前に `approve` で許可が与えられている必要がある。

```
transferFrom の動作:
  require(_allowances[from][msg.sender] >= amount)
  _allowances[from][msg.sender] -= amount;  // SSTORE
  _balances[from]               -= amount;  // SSTORE
  _balances[to]                 += amount;  // SSTORE
  emit Transfer(from, to, amount);
```

SSTORE が3回走るため transfer より約 20,000 gas 高い（計 ~85,000 gas）。

#### allowance(address owner, address spender)

現在の残余承認額を返す読み取り専用メソッド。  
ウォレットや DEX の UI が表示する「承認残高」はこれを参照している。

```
allowance(0xAlice, 0xUniswapRouter)
  -> _allowances[0xAlice][0xUniswapRouter]
  -> 例: 115792089237316195423570985008687907853269984665640564039457584007913129639935
         (type(uint256).max = 無限承認の場合)
```

#### decimals()

トークンの最小単位を定義する。10^decimals が人間が読む「1単位」に対応する。

```
decimals の意味:
  USDC:  decimals=6  -> 1 USDC = 1_000_000 (10^6) 内部単位
  JPYC:  decimals=18 -> 1 JPYC = 1_000_000_000_000_000_000 (10^18) 内部単位
  WBTC:  decimals=8  -> 1 WBTC = 100_000_000 (10^8) 内部単位

  ユーザーへの表示変換:
    rawAmount / 10^decimals = humanAmount

  例: JPYC
    raw = 500_000_000_000_000_000_000
    500_000_000_000_000_000_000 / 10^18 = 500 (JPYC)
```

EVM は浮動小数点を持たないため、すべての金額を整数として扱い、  
decimals によってスケールを外部で管理する設計になっている。

---

## 2. approve/transferFrom の設計思想

### なぜ2ステップか

ERC-20 の approve -> transferFrom という2ステップは、当初から不便だと批判されてきた。  
なぜこの設計が採用されたかを理解するには「プッシュ型 vs プル型」の違いを知る必要がある。

```
プッシュ型（transfer）:
  Alice がトランザクションを起動 -> コントラクトへ直接送金
  Alice が常にガスを払い、Alice が実行タイミングを制御する

プル型（approve + transferFrom）:
  1. Alice が Spender コントラクトに引き落とし許可を与える
  2. Spender コントラクトが好きなタイミングで Alice の残高から引き落とす
  サブスクリプション・DEX・レンディングなど「第三者が引き落とす」シナリオに対応
```

典型的な DEX のスワップフローを例に示す：

```
Step 1: ユーザーが approve を実行
  Alice -> USDC コントラクト: approve(UniswapRouter, 1000 USDC)
  ※ Alice がガスを払ってオンチェーンに記録

Step 2: ユーザーが DEX にスワップを依頼
  Alice -> UniswapRouter: swap(USDC->ETH, 1000 USDC)
  ※ Router が USDC.transferFrom(Alice, Pool, 1000) を内部で呼ぶ

承認がなければ Step 2 は revert する
```

このプル型設計により「コントラクトが自律的にユーザーの残高を操作できる」という  
DeFi の根幹機能が実現されている。

### 無限 allowance のリスク

多くの DeFi フロントエンドは初回承認時に `type(uint256).max`（無限承認）を提案する。

```
無限承認の値:
  type(uint256).max = 2^256 - 1
  = 115792089237316195423570985008687907853269984665640564039457584007913129639935

メリット: 2回目以降のインタラクションで approve トランザクションが不要
コスト削減: ~45,000 gas x approve 省略回数 分を節約できる
```

しかしこれには重大なリスクがある：

```
無限承認のリスクシナリオ:

  通常時:
    Alice -> approve(UniswapRouter, uint256.max)
    Alice -> UniswapRouter.swap(100 USDC)  <- 問題ない

  コントラクトがハッキングされた場合:
    攻撃者 -> UniswapRouter（乗っ取り）
    攻撃者 -> USDC.transferFrom(Alice, attacker, Alice の全残高)
    ↑ approve が残っているため成功してしまう

  Alice が対応できること:
    Alice -> USDC.approve(UniswapRouter, 0)  <- 手動でリセット
    ※ ただし被害後に気づくのは困難
```

代替アプローチとして「必要な額だけ承認する」パターンが推奨される：

```
安全な承認パターン:
  approve(spender, exactAmountNeeded)
  取引ごとに必要額を承認する

  例: 100 USDC のスワップなら:
    approve(UniswapRouter, 100_000_000)  // 100 USDC (decimals=6)

デメリット:
  毎回 approve トランザクションが必要 -> ~45,000 gas の追加コスト
  UX が悪化する（2つのトランザクションへの署名が必要）

-> この UX 問題を解決するのが EIP-2612 Permit（後述）
```

### approve の競合問題（Race Condition）

ERC-20 の approve には有名なセキュリティ上の競合問題がある：

```
問題のシナリオ:
  1. Alice が Bob に 100 トークンの approve を設定済み
  2. Alice が 50 に変更しようと approve(Bob, 50) を送信
  3. Bob が Alice の TX を mempool で発見
  4. Bob が高いガス代で transferFrom(Alice, Bob, 100) を先に実行
  5. Alice の approve(Bob, 50) が確定
  6. Bob がさらに transferFrom(Alice, Bob, 50) を実行

  -> Bob は合計 150 トークンを取得（期待値は最大 50）

対策: approve を変更する場合は一度 0 に設定してから再設定する
  approve(Bob, 0)   -> confirm
  approve(Bob, 50)  -> confirm

または OpenZeppelin の increaseAllowance / decreaseAllowance を使う
```

---

## 3. ガスの仕組み

### ガスとは何か

ガスは EVM での計算コストを測る単位であり、各オペコード（命令）に固定のガスコストが割り当てられている。

```
トランザクションのガスコスト計算:
  実際のコスト（ETH） = gasUsed x gasPrice

  gasLimit: 送信者が「最大いくらまで使っていいか」を指定する上限
  gasUsed:  実際に消費したガス量（gasLimit 以下）
  gasPrice: 1ガスあたりの ETH 価格（単位: wei/gas = gwei/gas）

  例:
    gasLimit = 100,000
    gasUsed  = 65,000
    gasPrice = 20 gwei = 20 x 10^-9 ETH

    コスト = 65,000 x 20 x 10^-9 ETH = 0.0013 ETH
    余剰   = (100,000 - 65,000) x 20 gwei 分は返還される
```

gasLimit を低く設定しすぎると "out of gas" エラーで TX は失敗し、  
消費したガス分は返還されない（マイナー/バリデータへの報酬として没収）。

### 主要オペコードのガスコスト

EVM の各命令には Yellow Paper で定義されたガスコストがある：

```
オペコード       ガス      説明
-----------     ------   ---------------------------------
ADD              3        加算
MUL              5        乗算
DIV              5        除算
KECCAK256       30+       ハッシュ（入力長に比例）
CALLDATALOAD     3        トランザクションの入力データ読み取り
MLOAD            3        メモリ読み取り
MSTORE           3        メモリ書き込み
SLOAD          2,100      ストレージ読み取り（コールド）/ 100（ウォーム）
SSTORE        20,000      ストレージへの新規書き込み（0 -> 非0）
               2,900      ストレージの既存値変更（非0 -> 非0）
CALL           2,600      外部コントラクト呼び出し
LOG3             375+     イベント発火（Transfer / Approval）
RETURN             0      戻り値返却
```

ベーストランザクションコスト: 21,000 gas（ETH 送金の最小コスト）

### SSTORE が高い理由

SSTORE（ストレージへの書き込み）が他の命令より桁違いに高い理由は、  
書き込みの影響が「そのトランザクション1回」ではなく「永続的にブロックチェーン全体に反映される」からだ。

```
ストレージの永続性コスト:
  全フルノードがこのデータを永遠に保持する必要がある
  -> ディスクコスト・メモリコスト・同期コストがすべての参加者に発生

  SLOAD/SSTORE のコスト変遷:
    EIP-2929（Berlin アップグレード, 2021年）:
      コールドアクセス（初回）: SLOAD=2,100 / SSTORE=20,000
      ウォームアクセス（同TX内2回目以降）: SLOAD=100 / SSTORE=100~2,900
      ↑ アクセスリストにより「同じスロットへの2回目以降」は割引

  比較:
    MSTORE（メモリ書き込み）: 3 gas   -> TX終了後に消える一時領域
    SSTORE（ストレージ書き込み）: 20,000 gas -> 永続的な状態変化
    -> 約6,600倍のコスト差がある
```

ERC-20 の transfer が ~65,000 gas かかる内訳：

```
transfer(to, amount) のガス内訳（概算）:
  ベーストランザクションコスト           21,000 gas
  呼び出しデータ（methodId + 2引数）      ~196 gas
  SLOAD（残高読み取り x 2）            4,200 gas
  残高チェック（比較・演算）               ~50 gas
  SSTORE（送信者残高の減算）            20,000 gas
  SSTORE（受信者残高の加算）
    - 受信者が初めて残高を持つ場合       20,000 gas（0->非0）
    - 既に残高がある場合                2,900 gas（非0->非0）
  LOG3（Transfer イベント）            ~1,875 gas
  その他（演算・JUMP等）                  残り

合計: 既存アドレスへ: ~51,000~65,000 gas
     新規アドレスへ: ~65,000~85,000 gas
```

---

## 4. EIP-1559 以前と以後

### レガシーガス（EIP-1559 以前）

EIP-1559 以前（London アップグレード 2021年8月以前）のガス価格モデル：

```
レガシーモデル（First Price Auction）:
  送信者が gasPrice を自由に設定する入札方式

  TX フィールド:
    gasPrice: 1 gwei 単位で指定（例: 50 gwei）

  ブロックへの採用ルール:
    マイナーは gasPrice が高い TX を優先してブロックに入れる
    -> 込み合っている時は gasPrice を上げないと何時間も待つ

  問題点:
    1. ガス価格の予測が困難（混雑度に合わせて手動調整が必要）
    2. 過剰支払いが常態化（競争に負けないように高めに設定）
    3. マイナーが mempool を操作してガス代を吊り上げる誘因
    4. 急騰時のガス代の不確実性が高い（10倍以上になることもあった）
```

### EIP-1559 以後

EIP-1559（London アップグレード、2021年8月）でガスモデルが根本的に変わった：

```
EIP-1559 モデル（2021年8月~現在）:
  TX フィールド（3つに変化）:
    maxFeePerGas:          1 gwei あたりの最大支払い意思額
    maxPriorityFeePerGas:  バリデータへのチップ（priority fee）
    gasLimit:              従来どおり

  プロトコルが自動計算する値:
    baseFee:  現在のブロックの基本料金（プロトコルが自動決定）
              -> 前ブロックが満杯に近いほど次の baseFee が上がる
              -> 前ブロックが空に近いほど次の baseFee が下がる
              -> ターゲット: ブロックの 50% 使用率で安定

  実際の支払い計算:
    effectiveGasPrice = min(maxFeePerGas, baseFee + maxPriorityFeePerGas)
    支払い = gasUsed x effectiveGasPrice

    うち:
      baseFee x gasUsed         -> バーン（焼却）される ← ETH デフレの仕組み
      priorityFee x gasUsed     -> バリデータへのチップ
```

具体的な数値例：

```
例: Ethereum メインネット（baseFee = 20 gwei, 標準的な混雑）

  設定値:
    maxFeePerGas         = 30 gwei（最悪でもこれ以上は払わない）
    maxPriorityFeePerGas = 2 gwei（バリデータへのチップ）

  実際の処理:
    effectiveGasPrice = min(30, 20 + 2) = 22 gwei
    gasUsed = 65,000（ERC-20 transfer）

    支払い = 65,000 x 22 gwei = 1,430,000 gwei = 0.00143 ETH
    バーン  = 65,000 x 20 gwei = 1,300,000 gwei = 0.0013 ETH
    チップ  = 65,000 x 2 gwei  =   130,000 gwei = 0.00013 ETH

  baseFee が急騰して 40 gwei になった場合:
    effectiveGasPrice = min(30, 40 + 2) = 30 gwei
    -> maxFeePerGas が上限として機能、超過分は払わない
    -> TX は baseFee > maxFeePerGas のためブロックに入れてもらえない
```

### レガシー vs EIP-1559 の比較図

```
レガシー（First Price Auction）:
  ユーザーA: gasPrice=50 gwei  ->  [採用]
  ユーザーB: gasPrice=45 gwei  ->  [採用]
  ユーザーC: gasPrice=30 gwei  ->  [待機]  <- 混雑時は後回し

  問題: 何 gwei にすれば通るか予測できない

EIP-1559:
  プロトコルが baseFee=20 gwei と公示
  ユーザーA: max=30, priority=2  ->  effectivePrice=22  [採用]
  ユーザーB: max=25, priority=3  ->  effectivePrice=23  [採用]
  ユーザーC: max=18, priority=1  ->  baseFee>max のため [拒否]

  改善: baseFee を見れば必要な設定がわかる
```

### EIP-1559 の改善点と残る課題

```
改善された点:
  1. ガス代予測の簡略化: baseFee を参照して +10~20% 程度で大体通る
  2. 過剰支払いの削減: 条件を満たせば refund が自動
  3. ETH のデフレ圧力: baseFee がバーンされるため供給量が減少
  4. ウォレット UX の向上: "suggested gas fee" の精度が上がった

残る課題:
  1. 混雑時は baseFee が急騰するため高額になることは変わらない
  2. priorityFee の競争は残る（緊急 TX はチップを高く設定する必要がある）
  3. EIP-4844（Proto-Danksharding, 2024）で L2 向けのデータコストは改善
```

---

## 5. Polygon vs Ethereum のガス代比較

### チェーン基本情報

```
                  Ethereum Mainnet      Polygon PoS
-----------       ----------------      -----------
コンセンサス      Proof of Stake        Proof of Stake
ブロック時間      ~12 秒                ~2 秒
ガストークン      ETH                   POL（旧 MATIC）
TPS（理論値）     ~15~30                ~7,000
baseFee 単位      gwei（10^-9 ETH）      gwei（10^-9 POL）
典型的 baseFee    5~100 gwei            30~300 gwei
トークン価格      ETH ~$3,000           POL ~$0.50（2024年末）
```

### 主要 ERC-20 操作のガス使用量

ガス使用量（gas units）自体はどの EVM 互換チェーンでも同じ。差は gasPrice とトークン価格にある。

```
操作                     gas 使用量（概算）
--------------------     ----------------
ETH 送金（最小）            21,000 gas
ERC-20 approve            ~45,000 gas
ERC-20 transfer           ~65,000 gas
ERC-20 transferFrom       ~85,000 gas
permit + transferFrom    ~110,000 gas    (EIP-2612)
Uniswap V3 スワップ      ~130,000~200,000 gas
```

### コスト比較表（具体的な金額換算）

```
操作: ERC-20 transfer（65,000 gas）

  Ethereum Mainnet（baseFee = 20 gwei, ETH = $3,000）:
    コスト = 65,000 x 20 x 10^-9 ETH = 0.0013 ETH
    USD    = 0.0013 x $3,000 = $3.90
    JPY    = 約 585 円（1 USD = 150 JPY 換算）

  Polygon PoS（baseFee = 100 gwei, POL = $0.50）:
    コスト = 65,000 x 100 x 10^-9 POL = 0.0065 POL
    USD    = 0.0065 x $0.50 = $0.00325
    JPY    = 約 0.5 円

  コスト比: Ethereum / Polygon = $3.90 / $0.00325 = 約 1,200 倍
```

```
操作別コスト比較（Ethereum: 20gwei, ETH=$3,000 / Polygon: 100gwei, POL=$0.50）:

操作                    ETH コスト    USD     Polygon コスト    USD
--------------------    ----------   ------   --------------   -------
approve                 0.0009 ETH   $2.70    0.0045 POL       $0.002
transfer                0.0013 ETH   $3.90    0.0065 POL       $0.003
transferFrom            0.0017 ETH   $5.10    0.0085 POL       $0.004
permit + transferFrom   0.0022 ETH   $6.60    0.011 POL        $0.006
Uniswap V3 swap         0.003 ETH    $9.00    0.015 POL        $0.008
```

### なぜ Polygon でも gwei 単位が同じなのに安いのか

```
コスト差の内訳:
  gasUnits: 同じ（65,000 は Polygon でも 65,000）
  gasPrice: Polygon の gwei は 10^-9 POL（ETH ではない）
  ネイティブトークン価格: POL ~$0.50 vs ETH ~$3,000

  価格比: $3,000 / $0.50 = 6,000 倍の価格差

  baseFee が同じ 20 gwei だと仮定した場合:
    Ethereum: 65,000 x 20 gwei x $3,000/ETH = $3.90
    Polygon:  65,000 x 20 gwei x $0.50/POL  = $0.00065

  実際は Polygon の baseFee が Ethereum より高め（100~300 gwei）だが、
  ネイティブトークン価格の差が圧倒的に大きいため 1,000~2,000 倍の差になる
```

### JPYC 決済における実際のコスト

このプロジェクトの主要ユースケース（Polygon 上での JPYC 決済）を考える：

```
ケース1: 従来の approve + transferFrom フロー（2 トランザクション）
  Step 1: approve(merchant, amount)     ~45,000 gas
  Step 2: transferFrom(user, merchant)  ~85,000 gas
  合計: ~130,000 gas

  Polygon でのコスト（baseFee=100 gwei, POL=$0.50）:
    130,000 x 100 x 10^-9 POL = 0.013 POL ≈ $0.0065 ≈ 約 1 円

ケース2: permit + transferFrom フロー（1 トランザクション）
  Step 1: オフチェーン署名（ガス不要）
  Step 2: permit() + transferFrom() を含む 1TX  ~110,000 gas

  Polygon でのコスト:
    110,000 x 100 x 10^-9 POL = 0.011 POL ≈ $0.0055 ≈ 約 0.8 円

ガス節約: ケース1比で約 20,000 gas 削減（approve の省略 + TX 基本料 21,000 gas）
```

---

## 6. EIP-2612 Permit がガスレスになる仕組み

### 従来の approve の問題点

EIP-2612 が解決しようとした課題は「approve がオンチェーントランザクションである」という事実だ。

```
従来のフロー（approve + transferFrom）:
  ユーザー -> [TX1] approve(spender, amount)   <- ガス代を払ってオンチェーン記録
  ユーザー -> [TX2] spender.doSomething()      <- ガス代を払って処理実行

  問題:
    1. 2つのトランザクションへの署名が必要（UX が悪い）
    2. approve 単体で ~45,000 gas のコストが常に発生
    3. ユーザーがガス代用のネイティブトークン（ETH/POL）を持つ必要がある
```

### EIP-2612 Permit の設計

Permit はオフチェーン署名（EIP-712 構造化データ署名）を使って、  
「approve の内容」をオンチェーンに記録するステップをスキップする。

```
Permit を使ったフロー:

  Step 1（オフチェーン・ガス不要）:
    ユーザーが署名するデータ構造（EIP-712）:
    {
      domain: { name, version, chainId, verifyingContract },
      types: {
        Permit: [
          { name: "owner",    type: "address" },
          { name: "spender",  type: "address" },
          { name: "value",    type: "uint256" },
          { name: "nonce",    type: "uint256" },
          { name: "deadline", type: "uint256" }
        ]
      },
      message: {
        owner:    0xAlice,
        spender:  0xServer,
        value:    1000_000_000_000_000_000_000,  // 1000 JPYC
        nonce:    3,                              // リプレイ防止
        deadline: 1735689600                      // 有効期限（Unix timestamp）
      }
    }
    -> Alice がウォレットで署名 -> 署名 (v, r, s) をサーバーに送信

  Step 2（オンチェーン・サーバーがガスを払う）:
    サーバー（または Relayer）が 1 TX で:
    jpycContract.permit(owner, spender, value, deadline, v, r, s)
    jpycContract.transferFrom(owner, merchant, value)
    ↑ この 2 呼び出しを 1 TX にまとめることも多い（コントラクト経由）
```

### コントラクト内部での permit() 処理

```
function permit(
    address owner,
    address spender,
    uint256 value,
    uint256 deadline,
    uint8 v, bytes32 r, bytes32 s
) external {
    require(deadline >= block.timestamp, "PERMIT_EXPIRED");

    // EIP-712 構造化データのハッシュを計算
    bytes32 structHash = keccak256(abi.encode(
        PERMIT_TYPEHASH,
        owner,
        spender,
        value,
        nonces[owner]++,   // <- nonce をインクリメントしてリプレイ防止
        deadline
    ));
    bytes32 hash = _hashTypedDataV4(structHash);

    // 署名からアドレスを復元（ecrecover）
    address signer = ECDSA.recover(hash, v, r, s);
    require(signer == owner, "INVALID_SIGNATURE");

    // approve と同等の処理を実行（msg.sender は owner ではない）
    _approve(owner, spender, value);
}
```

`ecrecover` がポイントで、署名 (v, r, s) と元のデータから「署名者のアドレス」を数学的に復元できる。  
これにより「オフチェーンで作成した署名」をオンチェーンで「本人確認の証拠」として扱える。

### ガスレスになるメカニズムの整理

```
「ガスレス」の意味:
  ユーザー視点: ガス代を支払うトランザクションに署名しない
              -> ETH/POL を持たなくても決済できる
  実際のコスト: サーバーまたは Relayer がガスを代わりに払っている

「ガスレス」は「ガス0円」ではなく「ユーザーがガスを払わない」の意味

Relayer モデル:
  ユーザー ---(署名のみ)----> Relayer（中間者）
  Relayer ---(TX 送信)----> Ethereum/Polygon
                             ↑ Relayer がガスを払う

費用回収モデル:
  - サービス手数料に含める
  - メタトランザクション（GSN: Gas Station Network）
  - プロトコル側が補助
```

### リプレイアタック防止

```
nonce の役割:
  nonces[owner] = 現在の nonce 値（初期値 0、permit() 呼び出しのたびに +1）

  攻撃シナリオ（nonce なし）:
    1. Alice が permit 署名を作成（100 JPYC を spender へ）
    2. spender が TX で permit() を実行して 100 JPYC を受け取る
    3. 悪意ある第三者が同じ署名を使って再度 permit() を実行
    -> Alice がもう 100 JPYC を失う

  nonce ありの防止:
    1. 署名時: nonce = 3 を含めて署名
    2. 初回 permit(): nonces[Alice] == 3 -> マッチ -> 処理実行 -> nonces[Alice] = 4
    3. 同じ署名の再利用試行: nonces[Alice] == 4 != 3 -> revert

deadline の役割:
  署名に「有効期限」を埋め込む
  deadline: 1735689600 (例: 2025-01-01 00:00:00 UTC)

  有効期限切れの署名で permit() を呼ぶと:
    require(deadline >= block.timestamp) -> revert
  -> 古い署名が後から悪用されない
```

### EIP-712 署名が approve を代替できる理由

```
通常の承認フロー:
  Alice が TX を送信 -> EVM が msg.sender == Alice を検証 -> approve を実行

Permit フロー:
  Alice がオフチェーンで署名 -> サーバーが TX を送信
  -> EVM が ecrecover(hash, v, r, s) == Alice を検証 -> approve を実行

  「msg.sender が Alice」の代わりに「署名が Alice のもの」であることを証明
  -> Alice が TX を送信しなくても、Alice の秘密鍵による承認が証明できる
```

---

## 7. ガス最適化のテクニック

ガス最適化はコントラクト開発において重要なエンジニアリング課題だ。  
以下に実践的なテクニックを整理する。

### 7.1 uint256 パッキング（Storage Slot Packing）

EVM のストレージスロットは 256 bit（32 bytes）単位。  
複数の小さな変数を1スロットに詰め込むことで SSTORE/SLOAD の回数を減らせる。

```
最適化前（非効率）:
  contract Token {
      uint256 public decimals;   // スロット 0（256bit 使用）
      uint256 public version;    // スロット 1（256bit 使用）
      address public owner;      // スロット 2（160bit, 残り 96bit 無駄）
      bool    public paused;     // スロット 3（8bit, 残り 248bit 無駄）
  }
  -> 読み取り: 4回の SLOAD = 4 x 2,100 = 8,400 gas

最適化後（パッキング）:
  contract Token {
      uint256 public decimals;   // スロット 0（256bit 使用）
      address public owner;      // スロット 1（160bit）
      bool    public paused;     //              8bit  ← owner と同スロット
      uint64  public version;    //             64bit  ← 同スロット
      // 160 + 8 + 64 = 232bit < 256bit -> 1スロットに収まる
  }
  -> 読み取り: 2回の SLOAD = 2 x 2,100 = 4,200 gas（50% 削減）
```

注意: Solidity は宣言順に低位アドレスから詰めるため、  
パッキングは「同じスロットに入る変数を連続して宣言する」必要がある。

### 7.2 events vs storage（イベントとストレージの使い分け）

イベント（LOG オペコード）はストレージより圧倒的に安い。  
「後で読む必要があるが、コントラクトのロジックには使わない」データはイベントに格納すべき。

```
コスト比較:
  イベント（LOG3）:  ~1,875 gas（32bytes のデータ1件 + topic 3本）
  SSTORE（新規）:   20,000 gas（32bytes のデータ1件）
  比率: 約 10 倍以上の差

使い分けの原則:
  ストレージに保存すべきもの:
    - コントラクトのロジックが参照する値
    - 例: _balances[address], _allowances[owner][spender]
    - 例: nonces[owner]（Permit のリプレイ防止に必要）

  イベントに任せるべきもの:
    - ログ・監査目的のデータ
    - 例: 送金履歴（Transfer イベント）
    - 例: 承認履歴（Approval イベント）
    - コントラクト内部の処理では参照しない情報

悪い例（ガス浪費）:
  mapping(uint256 => TransferRecord) public transferHistory;
  ↑ 全転送履歴をストレージに保存 -> 1件あたり 20,000+ gas

良い例:
  emit Transfer(from, to, amount);
  ↑ イベントに記録 -> ブロックチェーンのログとして格納（~1,875 gas）
  ↑ ただしコントラクト内部から参照できない（外部から eth_getLogs で取得）
```

### 7.3 バッチ転送（Batch Transfer）

複数の転送をまとめて1トランザクションで実行することで、  
1転送あたりのベースコスト 21,000 gas を節約できる。

```
個別転送 x 10回:
  21,000 gas x 10 (ベースコスト) + 65,000 gas x 10 (転送処理)
  = 210,000 + 650,000 = 860,000 gas

バッチ転送（1TX に 10件まとめる）:
  21,000 gas x 1 (ベースコスト) + 65,000 gas x 10 (転送処理)
  = 21,000 + 650,000 = 671,000 gas

節約: 860,000 - 671,000 = 189,000 gas（約 22% 削減）

バッチ転送の実装例（簡略）:
  function batchTransfer(
      address[] calldata recipients,
      uint256[] calldata amounts
  ) external returns (bool) {
      require(recipients.length == amounts.length);
      for (uint256 i = 0; i < recipients.length; i++) {
          _transfer(msg.sender, recipients[i], amounts[i]);
      }
      return true;
  }
```

### 7.4 calldata vs memory

関数の引数に `calldata` を使うと `memory` より安い（コピーが発生しない）。

```
function processArray(uint256[] calldata data)  // 推奨（readonly 引数）
function processArray(uint256[] memory data)    // コピーが発生

  calldata: 入力データをそのまま参照（読み取りのみ）
  memory:   入力データを一度メモリにコピーしてから参照

  1,000 要素の配列の場合:
    calldata: 追加コピーなし
    memory:   1,000 x MSTORE = 3,000+ gas の追加コスト
```

### 7.5 short-circuit と require の順序

`require` の条件チェックは安いものを先に置くことでガスを節約できる。

```
最適化前（高コストな条件を先に評価）:
  function transfer(address to, uint256 amount) external {
      require(_isComplexValidation(to));  // 1,000 gas の計算
      require(amount > 0);                // 3 gas
  }
  -> amount = 0 の場合も 1,000 gas の計算が先に走る

最適化後（安い条件を先に評価）:
  function transfer(address to, uint256 amount) external {
      require(amount > 0);                // 3 gas -> 0 なら即リジェクト
      require(_isComplexValidation(to));  // amount > 0 の場合のみ実行
  }
```

### 7.6 immutable と constant

デプロイ後に変わらない値は `immutable` や `constant` にするとストレージを使わない。

```
最適化前:
  uint256 public decimals = 18;    // SLOAD が必要（2,100 gas）

最適化後:
  uint8   public constant DECIMALS = 18;   // コンパイル時に埋め込まれる（3 gas）
  address public immutable OWNER;          // デプロイ時に設定、以降はコード内に展開

  コスト差: SLOAD 2,100 gas -> 3 gas（700 倍の差）
```

### 7.7 ガス最適化のトレードオフ

```
最適化の落とし穴:
  - 可読性の低下 -> セキュリティバグのリスクが上がる
  - 過剰なパッキング -> 変数アクセス時のシフト演算コストが増える場合がある
  - バッチ処理 -> 途中で 1 件失敗すると全件ロールバックする

原則:
  1. まず「正しく動く」コードを書く
  2. ガスプロファイルを計測して本当のボトルネックを特定する
  3. セキュリティを損なわない範囲で最適化する
  4. テストで意図しない動作変化がないことを確認する
```

---

## 8. このプロジェクトとの関係

### Phase 5 実装の振り返り

```
Phase 5 で実装した permit + transferFrom フロー:

  旧フロー（approve + transferFrom）:
    ユーザー -> [TX1] JPYC.approve(server, amount)   ~45,000 gas
    ユーザー -> [TX2] server.processPayment()         ~85,000 gas
    合計: ~130,000 gas / 2 TX

  新フロー（permit + transferFrom, Phase 5）:
    ユーザー -> [署名のみ] permit 署名データ -> サーバーへ送信  0 gas
    サーバー -> [TX1] permit() + transferFrom()       ~110,000 gas
    合計: ~110,000 gas / 1 TX

  改善点:
    ユーザー側のガス代: 0（ガス代を払う TX への署名が不要）
    TX 本数: 2 -> 1（UX 改善）
    サーバー側コスト: Polygon で約 0.8 円（商用で吸収可能）
```

### ガス費用の事業計算

```
Polygon での JPYC 決済コスト試算（2024年末想定）:
  1 決済あたりのガス: ~110,000 gas
  Polygon baseFee:   ~100 gwei
  POL 価格:          ~$0.50
  1 USD = 150 JPY

  1 決済のガス代:
    110,000 x 100 x 10^-9 POL x $0.50 x 150 JPY/USD
    = 0.011 POL x $0.50 x 150
    ≈ 0.83 円

  月 10,000 決済を処理する場合:
    月間ガス代 = 0.83 円 x 10,000 = 8,300 円 ≈ $55/月

  Ethereum Mainnet（比較）:
    同等の計算: 約 6,600 円/決済
    月 10,000 決済: 6,600 万円 ≈ $440,000/月 <- 事業として成立しない
```

これがこのプロジェクトで Polygon を選択した最大の理由のひとつだ。

---

## まとめ

```
ERC-20 インターフェース:
  6 つの必須メソッド（balanceOf, transfer, approve, transferFrom, allowance, decimals）
  内部表現は常に uint256 整数。decimals でスケールを管理

approve/transferFrom の設計:
  プル型アーキテクチャ -> 第三者コントラクトがユーザー残高を操作できる
  無限 allowance はリスクとトレードオフ
  approve 変更時の race condition に注意

ガスの仕組み:
  gasUsed x gasPrice = 実コスト
  SSTORE が高い（20,000 gas）のは永続的なブロックチェーン状態変化のため
  SLOAD（2,100 gas cold / 100 gas warm）も設計に影響する

EIP-1559（2021年8月~）:
  baseFee（自動決定・バーン） + priorityFee（チップ） + maxFeePerGas（上限）
  レガシーの入札方式から「予測可能なガス代」へ

Polygon vs Ethereum:
  同じ gas units でも POL の価格差（$0.50 vs $3,000）により
  ガス代は約 1,000~2,000 倍安い
  JPYC 決済は Polygon で 1 円未満、Ethereum では 6,000 円以上

EIP-2612 Permit:
  オフチェーン EIP-712 署名 -> ecrecover でオンチェーン検証
  nonce でリプレイ防止、deadline で有効期限管理
  ユーザーがガスを払わずに approve を委譲できる

ガス最適化:
  Storage Slot Packing: uint256 まとめで SSTORE 回数削減
  events vs storage:    ログはイベント（~10 倍安い）
  batch transfer:       ベースコスト 21,000 gas を複数件で分散
  immutable/constant:   SLOAD を回避（700 倍の差）
```

ガスは「Ethereum でプログラムを動かすための燃料」であり、その設計を理解することで  
なぜ DeFi が Ethereum ではなく Layer 2 やサイドチェーンに移行しているかが自然に理解できる。  
EIP-2612 Permit はその流れの中で「UX と効率」を両立させるための重要な設計パターンだ。
