"use client";

import { WagmiProvider } from "wagmi";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RainbowKitProvider, darkTheme } from "@rainbow-me/rainbowkit";
import "@rainbow-me/rainbowkit/styles.css";
import { wagmiConfig } from "@/lib/wagmi";
import { useState } from "react";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  // QueryClient はコンポーネント内で生成してサーバー/クライアントの境界を安全に扱う
  const [queryClient] = useState(() => new QueryClient());

  return (
    <html lang="ja">
      <body style={{ margin: 0, fontFamily: "system-ui, sans-serif", background: "#f9fafb" }}>
        <WagmiProvider config={wagmiConfig}>
          <QueryClientProvider client={queryClient}>
            <RainbowKitProvider theme={darkTheme({ accentColor: "#7c3aed" })}>
              <header
                style={{
                  background: "#1e1b4b",
                  color: "#fff",
                  padding: "12px 24px",
                  display: "flex",
                  alignItems: "center",
                  gap: 12,
                }}
              >
                <span style={{ fontSize: 20, fontWeight: "bold" }}>⛓ Web3Pay</span>
                <span style={{ fontSize: 13, color: "#c4b5fd" }}>
                  JPYC ステーブルコイン決済
                </span>
              </header>
              <main style={{ padding: 24 }}>{children}</main>
            </RainbowKitProvider>
          </QueryClientProvider>
        </WagmiProvider>
      </body>
    </html>
  );
}
