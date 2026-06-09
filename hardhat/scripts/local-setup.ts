/**
 * ローカル Hardhat ノード用セットアップスクリプト
 *
 * 使い方:
 *   1. npx hardhat node          （別ターミナルで）
 *   2. npx hardhat run scripts/local-setup.ts --network localhost
 *
 * 実行後に出力されるアドレスを Spring Boot の設定に使う。
 * API キーは一切不要。
 */
import { ethers } from "hardhat";

async function main() {
  const [deployer, receiver, sender] = await ethers.getSigners();

  console.log("=".repeat(60));
  console.log("Hardhat ローカルノード セットアップ");
  console.log("=".repeat(60));
  console.log(`Deployer  : ${deployer.address}`);
  console.log(`Receiver  : ${receiver.address}`);
  console.log(`Sender    : ${sender.address}`);

  // ── テスト用 ERC-20（MockJPYC）をデプロイ ─────────────────────────
  // OpenZeppelin の ERC20 を継承した最小実装を使用
  const MockERC20 = await ethers.getContractFactory("MockERC20");
  const jpyc = await MockERC20.deploy(
    "JPY Coin",     // name
    "JPYC",         // symbol
    18,             // decimals
    deployer.address
  );
  await jpyc.waitForDeployment();
  const jpycAddress = await jpyc.getAddress();

  console.log("\n── デプロイ済みコントラクト ──");
  console.log(`MockJPYC  : ${jpycAddress}`);

  // ── Sender に JPYC を発行（1,000 JPYC）────────────────────────────
  const mintAmount = ethers.parseUnits("1000", 18);
  await jpyc.mint(sender.address, mintAmount);
  console.log(`\n── ${sender.address} に ${ethers.formatUnits(mintAmount, 18)} JPYC をミント`);

  // ── Spring Boot dev profile 用の設定値を出力 ──────────────────────
  console.log("\n" + "=".repeat(60));
  console.log("Spring Boot (application-dev.yml / .env) に設定する値:");
  console.log("=".repeat(60));
  console.log(`WEB3J_CLIENT_ADDRESS=http://localhost:8545`);
  console.log(`WEB3J_POLYGON_ENDPOINT=http://localhost:8545`);
  console.log(`WEB3J_ETHEREUM_ENDPOINT=http://localhost:8545`);
  console.log("");
  console.log("JPYC コントラクトアドレス（StablecoinType.JPYC を置き換える場合）:");
  console.log(`  ${jpycAddress}`);
  console.log("");
  console.log("テスト用ウォレット（Hardhat デフォルト秘密鍵）:");
  console.log(`  Receiver  : ${receiver.address}`);
  console.log(`  Sender    : ${sender.address}`);
  console.log("=".repeat(60));
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
