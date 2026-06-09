"use client";

import type { PaymentOrder } from "@/lib/api";

const STATUS_COLOR: Record<string, string> = {
  AWAITING_CONSUMER: "#f59e0b",
  PENDING:           "#3b82f6",
  CONFIRMED:         "#16a34a",
  OVERPAID:          "#a855f7",
  UNDERPAID:         "#f97316",
  EXPIRED:           "#6b7280",
};

interface Props {
  order: PaymentOrder;
  onPay?: () => void;
  paying?: boolean;
}

export function PaymentCard({ order, onPay, paying }: Props) {
  const color = STATUS_COLOR[order.status] ?? "#374151";
  const canPay =
    onPay &&
    (order.status === "PENDING" || order.status === "AWAITING_CONSUMER") &&
    order.token !== "USDT"; // USDT は Permit 非対応

  return (
    <div
      style={{
        border: "1px solid #e5e7eb",
        borderRadius: 12,
        padding: 20,
        maxWidth: 480,
        fontFamily: "monospace",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
        <span style={{ fontSize: 13, color: "#6b7280" }}>Order ID</span>
        <span style={{ fontSize: 12 }}>{order.id}</span>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <span style={{ fontSize: 13, color: "#6b7280" }}>金額</span>
        <span style={{ fontWeight: "bold" }}>
          {order.expectedAmount} {order.token}
        </span>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <span style={{ fontSize: 13, color: "#6b7280" }}>モード</span>
        <span>{order.paymentMode}</span>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <span style={{ fontSize: 13, color: "#6b7280" }}>ステータス</span>
        <span style={{ color, fontWeight: "bold" }}>{order.status}</span>
      </div>

      {order.txHash && (
        <div style={{ marginTop: 8, fontSize: 11, color: "#6b7280", wordBreak: "break-all" }}>
          TxHash: {order.txHash}
        </div>
      )}

      {order.expiresAt && (
        <div style={{ marginTop: 4, fontSize: 11, color: "#9ca3af" }}>
          有効期限: {new Date(order.expiresAt).toLocaleString("ja-JP")}
        </div>
      )}

      {canPay && (
        <button
          onClick={onPay}
          disabled={paying}
          style={{
            marginTop: 16,
            width: "100%",
            padding: "12px 0",
            background: paying ? "#9ca3af" : "#7c3aed",
            color: "#fff",
            border: "none",
            borderRadius: 8,
            fontSize: 15,
            cursor: paying ? "not-allowed" : "pointer",
          }}
        >
          {paying ? "処理中..." : `Permit で ${order.expectedAmount} ${order.token} を支払う`}
        </button>
      )}
    </div>
  );
}
