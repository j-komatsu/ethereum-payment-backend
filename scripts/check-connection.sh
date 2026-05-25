#!/bin/bash
# Ethereum ノードへの接続確認スクリプト
# 使い方: WEB3J_CLIENT_ADDRESS を環境変数にセットしてから実行する
#   export WEB3J_CLIENT_ADDRESS=https://sepolia.infura.io/v3/YOUR_KEY
#   ./scripts/check-connection.sh

set -euo pipefail

if [ -z "${WEB3J_CLIENT_ADDRESS:-}" ]; then
  echo "❌ エラー: WEB3J_CLIENT_ADDRESS が設定されていません"
  echo "   export WEB3J_CLIENT_ADDRESS=https://sepolia.infura.io/v3/YOUR_KEY"
  exit 1
fi

echo "🔍 接続先: [REDACTED] (APIキー保護のためURLは表示しません)"
echo ""

# eth_blockNumber: 現在のブロック番号を取得
echo "--- eth_blockNumber ---"
RESPONSE=$(curl -s -X POST "${WEB3J_CLIENT_ADDRESS}" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}')

if echo "${RESPONSE}" | grep -q '"error"'; then
  echo "❌ エラーレスポンス: ${RESPONSE}"
  exit 1
fi

HEX_BLOCK=$(echo "${RESPONSE}" | grep -o '"result":"[^"]*"' | sed 's/"result":"//;s/"//')
if [ -z "${HEX_BLOCK}" ]; then
  echo "❌ ブロック番号を取得できませんでした: ${RESPONSE}"
  exit 1
fi

# 16進数 → 10進数に変換
DEC_BLOCK=$((${HEX_BLOCK}))
echo "✅ 最新ブロック番号: ${DEC_BLOCK} (${HEX_BLOCK})"

# eth_chainId: チェーンIDを確認
echo ""
echo "--- eth_chainId ---"
CHAIN_RESPONSE=$(curl -s -X POST "${WEB3J_CLIENT_ADDRESS}" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":2}')

HEX_CHAIN=$(echo "${CHAIN_RESPONSE}" | grep -o '"result":"[^"]*"' | sed 's/"result":"//;s/"//')
DEC_CHAIN=$((${HEX_CHAIN}))

case ${DEC_CHAIN} in
  1)   NETWORK="Ethereum Mainnet" ;;
  11155111) NETWORK="Sepolia Testnet" ;;
  137) NETWORK="Polygon Mainnet" ;;
  80002) NETWORK="Polygon Amoy Testnet" ;;
  *)   NETWORK="Unknown (chainId=${DEC_CHAIN})" ;;
esac

echo "✅ ネットワーク: ${NETWORK}"
echo ""
echo "🎉 接続確認完了！"
