#!/bin/bash
# PRを出す前にローカルで実行するセキュリティチェック

set -e

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

FAILED=0

echo "=== ローカルセキュリティチェック ==="
echo ""

# 1. 秘密鍵パターン（64文字16進数）
echo "▶ 秘密鍵パターンをチェック中..."
if git diff origin/main...HEAD -- '*.java' '*.yml' '*.yaml' '*.properties' '*.env' | \
   grep -E '(private_?key|privateKey|PRIVATE_KEY)\s*[=:]\s*[0-9a-fA-F]{64}' > /dev/null 2>&1; then
  echo -e "${RED}[FAIL] 秘密鍵らしきパターンが検出されました${NC}"
  FAILED=1
else
  echo -e "${GREEN}[OK]${NC}"
fi

# 2. ニーモニックフレーズ（BIP39 英単語 12個以上）
echo "▶ ニーモニックフレーズをチェック中..."
if git diff origin/main...HEAD -- '*.java' '*.yml' '*.yaml' '*.properties' | \
   grep -E '(mnemonic|seed_?phrase|MNEMONIC)\s*[=:]' > /dev/null 2>&1; then
  echo -e "${RED}[FAIL] ニーモニックらしきパターンが検出されました${NC}"
  FAILED=1
else
  echo -e "${GREEN}[OK]${NC}"
fi

# 3. ハードコードされたパスワード
echo "▶ ハードコードパスワードをチェック中..."
if git diff origin/main...HEAD -- '*.java' '*.yml' '*.yaml' | \
   grep -E 'password\s*[=:]\s*["\x27][^"\x27$\{]{4,}["\x27]' > /dev/null 2>&1; then
  echo -e "${YELLOW}[WARN] パスワードらしき値が検出されました。環境変数を使用しているか確認してください${NC}"
else
  echo -e "${GREEN}[OK]${NC}"
fi

# 4. application.yml に危険な設定が混入していないか
echo "▶ application.yml（本番）の危険設定をチェック中..."
MAIN_YML="src/main/resources/application.yml"
if [ -f "$MAIN_YML" ]; then
  if grep -E 'h2.console.*enabled.*true|ddl-auto.*(update|create)|show-details.*always' "$MAIN_YML" > /dev/null 2>&1; then
    echo -e "${RED}[FAIL] application.yml に開発専用設定が含まれています${NC}"
    grep -n -E 'h2.console.*enabled.*true|ddl-auto.*(update|create)|show-details.*always' "$MAIN_YML"
    FAILED=1
  else
    echo -e "${GREEN}[OK]${NC}"
  fi
fi

# 5. .env ファイルが誤ってステージされていないか
echo "▶ .env ファイルのステージングをチェック中..."
if git diff --cached --name-only | grep -E '^\.env$|^\.env\.' | grep -v '.env.example' > /dev/null 2>&1; then
  echo -e "${RED}[FAIL] .env ファイルがステージされています！${NC}"
  FAILED=1
else
  echo -e "${GREEN}[OK]${NC}"
fi

# 6. application-secret / application-prod / application-local が誤ってコミットされていないか
echo "▶ 機密プロファイルファイルをチェック中..."
if git diff origin/main...HEAD --name-only | grep -E 'application-(secret|prod|local)\.yml' > /dev/null 2>&1; then
  echo -e "${RED}[FAIL] 機密プロファイルファイルがコミットに含まれています${NC}"
  FAILED=1
else
  echo -e "${GREEN}[OK]${NC}"
fi

echo ""
if [ $FAILED -eq 1 ]; then
  echo -e "${RED}=== チェック失敗：PRを出す前に上記の問題を修正してください ===${NC}"
  exit 1
else
  echo -e "${GREEN}=== すべてのチェックをパスしました。PRを作成できます ===${NC}"
fi
