import { SiweMessage } from "siwe";
import { fetchNonce, verifySiwe } from "./api";

/**
 * SIWE (Sign-In With Ethereum) フロー
 *
 * 1. バックエンドからワンタイム nonce を取得
 * 2. EIP-4361 形式のメッセージを組み立て
 * 3. ウォレット（MetaMask 等）で署名
 * 4. バックエンドで署名を検証 → JWT 取得
 */
export async function signInWithEthereum(
  address: string,
  chainId: number,
  signMessageFn: (message: string) => Promise<string>
): Promise<string> {
  // Step 1: nonce 取得
  const nonce = await fetchNonce();

  // Step 2: SIWE メッセージ組み立て
  const message = new SiweMessage({
    domain: window.location.host,
    address,
    statement: "Web3Pay にサインインします",
    uri: window.location.origin,
    version: "1",
    chainId,
    nonce,
  });
  const messageStr = message.prepareMessage();

  // Step 3: ウォレット署名
  const signature = await signMessageFn(messageStr);

  // Step 4: バックエンド検証 → JWT
  const jwt = await verifySiwe(messageStr, signature, address);
  return jwt;
}
