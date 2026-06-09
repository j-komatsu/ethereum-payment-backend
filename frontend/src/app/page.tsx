"use client";

import { useState } from "react";
import { useAccount } from "wagmi";
import { ConnectAndLogin } from "@/components/ConnectAndLogin";
import { useAuth } from "@/hooks/useAuth";
import { createPaymentOrder, type PaymentOrder } from "@/lib/api";

export default function HomePage() {
  const { address } = useAccount();
  const { jwt, isLoggedIn } = useAuth();

  const [receiverAddress, setReceiverAddress] = useState("");
  const [amount, setAmount]                   = useState("1000");
  const [token, setToken]                     = useState("JPYC");
  const [mode, setMode]                       = useState<"MPM" | "CPM">("MPM");
  const [createdOrder, setCreatedOrder]       = useState<PaymentOrder | null>(null);
  const [creating, setCreating]               = useState(false);
  const [error, setError]                     = useState("");

  const handleCreate = async () => {
    if (!jwt) return;
    setCreating(true);
    setError("");
    try {
      const order = await createPaymentOrder(jwt, {
        receiverAddress,
        senderAddress: mode === "MPM" ? address : undefined,
        amount,
        token,
        paymentMode: mode,
      });
      setCreatedOrder(order);
    } catch (e) {
      setError(e instanceof Error ? e.message : "作成失敗");
    } finally {
      setCreating(false);
    }
  };

  return (
    <div style={{ maxWidth: 520, margin: "0 auto", display: "flex", flexDirection: "column", gap: 24 }}>
      <section>
        <h2 style={{ marginBottom: 12 }}>ウォレット接続 / ログイン</h2>
        <ConnectAndLogin />
      </section>

      {isLoggedIn && (
        <section>
          <h2 style={{ marginBottom: 12 }}>支払いオーダー作成</h2>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <label>
              受取アドレス
              <input
                value={receiverAddress}
                onChange={(e) => setReceiverAddress(e.target.value)}
                placeholder="0x..."
                style={{ display: "block", width: "100%", padding: 8, marginTop: 4, border: "1px solid #d1d5db", borderRadius: 6 }}
              />
            </label>

            <label>
              金額
              <input
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                style={{ display: "block", width: "100%", padding: 8, marginTop: 4, border: "1px solid #d1d5db", borderRadius: 6 }}
              />
            </label>

            <label>
              トークン
              <select
                value={token}
                onChange={(e) => setToken(e.target.value)}
                style={{ display: "block", padding: 8, marginTop: 4, border: "1px solid #d1d5db", borderRadius: 6 }}
              >
                <option value="JPYC">JPYC（Polygon）</option>
                <option value="USDC">USDC（Ethereum）</option>
                <option value="DAI">DAI（Ethereum）</option>
              </select>
            </label>

            <label>
              支払いモード
              <select
                value={mode}
                onChange={(e) => setMode(e.target.value as "MPM" | "CPM")}
                style={{ display: "block", padding: 8, marginTop: 4, border: "1px solid #d1d5db", borderRadius: 6 }}
              >
                <option value="MPM">MPM（マーチャントQR — 受取人が固定）</option>
                <option value="CPM">CPM（消費者QR — 支払い人がQRスキャン）</option>
              </select>
            </label>

            <button
              onClick={handleCreate}
              disabled={creating || !receiverAddress}
              style={{
                padding: "12px 0",
                background: creating ? "#9ca3af" : "#4f46e5",
                color: "#fff",
                border: "none",
                borderRadius: 8,
                cursor: creating ? "not-allowed" : "pointer",
                fontWeight: "bold",
              }}
            >
              {creating ? "作成中..." : "オーダー作成"}
            </button>
          </div>

          {error && <p style={{ color: "#dc2626", marginTop: 8 }}>{error}</p>}

          {createdOrder && (
            <div style={{ marginTop: 16, padding: 16, background: "#f0fdf4", borderRadius: 8, fontFamily: "monospace", fontSize: 13 }}>
              <p style={{ margin: 0, fontWeight: "bold", color: "#16a34a" }}>✅ オーダー作成完了</p>
              <p style={{ margin: "4px 0" }}>ID: {createdOrder.id}</p>
              <p style={{ margin: "4px 0" }}>Status: {createdOrder.status}</p>
              {createdOrder.consumerNonce && (
                <p style={{ margin: "4px 0", wordBreak: "break-all" }}>
                  QR Nonce: {createdOrder.consumerNonce}
                </p>
              )}
              <a
                href={`/pay/${createdOrder.id}`}
                style={{ color: "#7c3aed", display: "block", marginTop: 8 }}
              >
                → 支払いページを開く
              </a>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
