# レポート21 — リッチなドキュメントサイト：Docusaurus 導入ガイド

> 現在の `.md` ファイルを、検索・ダークモード・バージョニング付きのドキュメントサイトに変換する

---

## 1. なぜ Docusaurus か

```
現状: docs/*.md → GitHub で読む（シンプル）

目標: 
  ✅ 検索機能
  ✅ ダークモード
  ✅ ナビゲーション（サイドバー）
  ✅ Mermaid 図のレンダリング
  ✅ MDX（Markdown の中に React コンポーネント）
  ✅ バージョニング（v1 / v2）
  ✅ GitHub Pages / Vercel へのデプロイ（無料）
  ✅ 既存の .md ファイルをそのまま使える
```

**競合との比較:**

| ツール | 特徴 | 向いている用途 |
|---|---|---|
| **Docusaurus 3** | React ベース、MDX、meta社製 | プロジェクトドキュメント |
| VitePress | Vue ベース、超高速 | 技術リファレンス |
| MkDocs Material | Python ベース、シンプル | 技術ドキュメント全般 |
| Mintlify | SaaS、ゼロ設定 | API ドキュメント |
| Nextra | Next.js ベース | ブログ + ドキュメント |

**このプロジェクトの推奨:** Docusaurus 3（Meta 製、実績豊富、MDX でインタラクティブな例が書ける）

---

## 2. セットアップ手順

### 2-1. インストール

```bash
# プロジェクトルートに docs-site ディレクトリを作成
npx create-docusaurus@latest docs-site classic --typescript
cd docs-site
npm install
```

### 2-2. ディレクトリ構成

```
docs-site/
├── docusaurus.config.ts    ← サイト設定
├── sidebars.ts             ← サイドバー構成
├── src/
│   ├── components/         ← カスタム React コンポーネント
│   └── pages/              ← トップページ等
└── docs/                   ← Markdown ファイルを配置（既存 docs/ から移植）
    ├── intro.md
    ├── reports/
    │   ├── report-01.md
    │   └── ...
    └── implementation-notes/
        └── ...
```

### 2-3. 既存 docs/ をそのまま活用する設定

```typescript
// docusaurus.config.ts
const config: Config = {
  title: 'Web3Pay — Ethereum 決済バックエンド',
  tagline: 'JPYC ステーブルコイン決済の学習プロジェクト',
  url: 'https://j-komatsu.github.io',
  baseUrl: '/ethereum-payment-backend/',
  
  themeConfig: {
    navbar: {
      title: '⛓ Web3Pay Docs',
      items: [
        { to: '/docs/intro', label: 'ドキュメント', position: 'left' },
        { to: '/docs/reports/report-01', label: 'レポート', position: 'left' },
        { href: 'https://github.com/j-komatsu/ethereum-payment-backend', label: 'GitHub', position: 'right' },
      ],
    },
    // Mermaid を有効化
    mermaid: { theme: { light: 'neutral', dark: 'forest' } },
  } satisfies Preset.ThemeConfig,
  
  markdown: {
    mermaid: true,  // ← Mermaid 図を有効化
  },
  themes: ['@docusaurus/theme-mermaid'],  // ← 追加
};
```

### 2-4. サイドバーの設定

```typescript
// sidebars.ts
const sidebars: SidebarsConfig = {
  docs: [
    'intro',
    {
      type: 'category',
      label: '📚 学習レポート',
      items: [
        'reports/report-01-ethereum-basics',
        'reports/report-02-accounts-and-web3j',
        // ... 省略
        'reports/report-21-docusaurus-rich-docs',
      ],
    },
    {
      type: 'category',
      label: '🔧 実装ノート',
      items: ['implementation-notes/phase0', 'implementation-notes/phase1'],
    },
    {
      type: 'category',
      label: '💡 ナレッジ',
      items: ['knowledge/day-01', 'knowledge/day-02'],
    },
  ],
};
```

---

## 3. MDX でインタラクティブなドキュメント

Docusaurus では `.mdx` ファイルを使うと Markdown の中に React コンポーネントを埋め込めます。

### コード例: 比較タブ

```mdx
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs>
  <TabItem value="permit" label="EIP-2612 Permit">

  ```java
  // 概念コード（実際の実装は PermitService#execute() を参照）
  PermitTxResponse execute(String paymentOrderId, String ownerAddress, ...) {
      verifyPermitSignature(token, ownerAddress, ...);
      sendPermit(web3j, token, ownerAddress, ...);
      sendTransferFrom(web3j, token, ownerAddress, receiverAddress, value);
  }
  ```

  </TabItem>
  <TabItem value="approve" label="従来の approve">

  ```javascript
  // 2ステップ必要（ガス代2回）
  await token.approve(spender, amount);
  await token.transferFrom(owner, recipient, amount);
  ```

  </TabItem>
</Tabs>
```

### コード例: 注意書きコンポーネント

```mdx
:::danger セキュリティ注意
秘密鍵をコードにハードコードしてはいけません。
必ず環境変数 `${PERMIT_SPENDER_PRIVATE_KEY}` を使用してください。
:::

:::tip Polygon PoS を使う理由
JPYC は Polygon PoS にデプロイされており、
ガス代が Ethereum の 1/1000 以下で済みます。
:::
```

---

## 4. GitHub Pages へのデプロイ（無料）

```yaml
# .github/workflows/deploy-docs.yml
name: Deploy Docs

on:
  push:
    branches: [main]
    paths: ['docs-site/**']

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: cd docs-site && npm ci && npm run build
      - uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./docs-site/build
```

デプロイ後 URL: `https://j-komatsu.github.io/ethereum-payment-backend/`

---

## 5. 既存 Markdown から Docusaurus への移行チェックリスト

```
□ frontmatter の追加（タイトル・説明）
  ---
  sidebar_position: 1
  title: Ethereum 基礎
  description: Ethereum の仕組みを初心者向けに解説
  ---

□ Mermaid 図はそのまま使える（```mermaid ブロック）

□ 相対リンクの修正
  旧: [report-02](report-02-accounts-and-web3j.md)
  新: [report-02](./report-02-accounts-and-web3j)

□ 画像パスの修正（必要であれば）

□ admonition（注意書き）への変換
  旧: > ⚠️ 注意: ...
  新: :::caution\n...\n:::
```

---

## 6. 今すぐ始める最小構成

Docusaurus のセットアップが大掛かりに感じる場合、**GitHub の Mermaid レンダリングだけで今すぐリッチにできます**。

GitHub は `.md` ファイル内の \`\`\`mermaid ブロックをそのままレンダリングします。本レポート集（report-16〜20）にはすでに Mermaid 図が入っています。

```
今日できること（5分）:
  1. GitHub で docs/ フォルダを開く
  2. report-16-layer2-complete-guide.md を開く
  3. Mermaid の図が自動でレンダリングされているのを確認 ✅

将来できること（1〜2日）:
  1. npx create-docusaurus@latest docs-site classic --typescript
  2. 既存 .md を docs-site/docs/ に移動
  3. npm run build && npm run serve で確認
  4. GitHub Actions でデプロイ
```

---

## まとめ

```
ステップ1（今すぐ）: Mermaid 図付き .md で GitHub レンダリング活用
ステップ2（近い将来）: Docusaurus でドキュメントサイト化
ステップ3（発展）: MDX でインタラクティブなコンポーネント追加
ステップ4（本格的）: Algolia DocSearch で全文検索
```

現在の `docs/` ディレクトリ構成はすでに Docusaurus と互換性があります。  
`docs-site/` ディレクトリを追加するだけで始められます。
