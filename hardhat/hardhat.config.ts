import { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";

const ALCHEMY_KEY = process.env.ALCHEMY_API_KEY ?? "";

const config: HardhatUserConfig = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: { enabled: true, runs: 200 },
    },
  },
  networks: {
    // ── ローカルノード（API キー不要） ──────────────────────────────────
    // 起動: npx hardhat node
    // Spring Boot 接続先: http://localhost:8545
    hardhat: {
      chainId: 31337,
      accounts: {
        count: 5,
        accountsBalance: "10000000000000000000000", // 10,000 ETH each
      },
    },
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 31337,
    },

    // ── Polygon フォーク（要 Alchemy API キー） ────────────────────────
    // 起動: ALCHEMY_API_KEY=xxx npx hardhat node --fork <polygon-url>
    // Spring Boot 接続先: http://localhost:8545（フォーク後も同じ）
    ...(ALCHEMY_KEY
      ? {
          polygonFork: {
            url: "http://127.0.0.1:8545",
            chainId: 31337,
            forking: {
              url: `https://polygon-mainnet.g.alchemy.com/v2/${ALCHEMY_KEY}`,
            },
          },
        }
      : {}),
  },
};

export default config;
