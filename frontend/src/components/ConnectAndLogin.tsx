"use client";

import { ConnectButton } from "@rainbow-me/rainbowkit";
import { useAccount } from "wagmi";
import { useAuth } from "@/hooks/useAuth";

export function ConnectAndLogin() {
  const { isConnected } = useAccount();
  const { isLoggedIn, loading, error, login, logout } = useAuth();

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      {/* RainbowKit のウォレット接続ボタン */}
      <ConnectButton />

      {/* ウォレット接続後に SIWE ログインボタンを表示 */}
      {isConnected && !isLoggedIn && (
        <button
          onClick={login}
          disabled={loading}
          style={{
            padding: "10px 20px",
            background: "#6b21a8",
            color: "#fff",
            border: "none",
            borderRadius: 8,
            cursor: loading ? "not-allowed" : "pointer",
          }}
        >
          {loading ? "署名中..." : "ウォレットでサインイン (SIWE)"}
        </button>
      )}

      {isLoggedIn && (
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ color: "#16a34a", fontSize: 14 }}>✅ ログイン済み</span>
          <button
            onClick={logout}
            style={{
              padding: "4px 12px",
              fontSize: 12,
              background: "#e5e7eb",
              border: "none",
              borderRadius: 4,
              cursor: "pointer",
            }}
          >
            ログアウト
          </button>
        </div>
      )}

      {error && <p style={{ color: "#dc2626", fontSize: 13 }}>{error}</p>}
    </div>
  );
}
