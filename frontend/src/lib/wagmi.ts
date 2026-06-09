import { getDefaultConfig } from "@rainbow-me/rainbowkit";
import { polygon } from "wagmi/chains";

export const wagmiConfig = getDefaultConfig({
  appName: "Web3Pay",
  projectId: process.env.NEXT_PUBLIC_WALLETCONNECT_PROJECT_ID ?? "demo",
  chains: [polygon],
  ssr: true,
});
