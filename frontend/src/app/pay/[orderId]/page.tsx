"use client";

import { useEffect } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { ConnectAndLogin } from "@/components/ConnectAndLogin";
import { PaymentCard } from "@/components/PaymentCard";
import { useAuth } from "@/hooks/useAuth";
import { usePayment } from "@/hooks/usePayment";
import { claimPaymentOrder } from "@/lib/api";
import { useState } from "react";

/**
 * 支払いページ
 *
 * URL 形式:
 *   MPM: /pay/{orderId}
 *   CPM: /pay/{orderId}?nonce={consumerNonce}
 *
 * フロー:
 *   1. ウォレット接続
 *   2. SIWE ログイン → JWT 取得
 *   3. オーダー取得
 *   4. CPM かつ nonce パラメータあり → 消費者アドレス確定 (claim)
 *   5. Permit 署名 → バックエンド実行 → 完了
 */
export default function PayPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const orderId = params.orderId as string;
  const consumerNonce = searchParams.get("nonce");

  const { jwt, isLoggedIn } = useAuth();
  const { order, txResult, loading, error, fetchOrder, payWithPermit } = usePayment(jwt);
  const [claiming, setClaiming] = useState(false);
  const [claimError, setClaimError] = useState("");

  // ログイン後にオーダーを取得
  useEffect(() => {
    if (isLoggedIn && orderId) {
      fetchOrder(orderId);
    }
  }, [isLoggedIn, orderId, fetchOrder]);

  // CPM: ログイン後に自動 claim
  useEffect(() => {
    if (!isLoggedIn || !jwt || !order || !consumerNonce) return;
    if (order.status !== "AWAITING_CONSUMER") return;

    const claim = async () => {
      setClaiming(true);
      setClaimError("");
      try {
        await claimPaymentOrder(jwt, orderId, consumerNonce);
        fetchOrder(orderId); // ステータス更新
      } catch (e) {
        setClaimError(e instanceof Error ? e.message : "消費者確定失敗");
      } finally {
        setClaiming(false);
      }
    };
    claim();
  }, [isLoggedIn, jwt, order, consumerNonce, orderId, fetchOrder]);

  return (
    <div style={{ maxWidth: 520, margin: "0 auto", display: "flex", flexDirection: "column", gap: 24 }}>
      <h2>支払い</h2>

      <ConnectAndLogin />

      {!isLoggedIn && (
        <p style={{ color: "#6b7280" }}>
          ウォレットを接続してサインインすると支払いを開始できます。
        </p>
      )}

      {isLoggedIn && loading && !order && (
        <p style={{ color: "#6b7280" }}>オーダーを読み込み中...</p>
      )}

      {claiming && (
        <p style={{ color: "#f59e0b" }}>⏳ アドレスを確定中...</p>
      )}

      {claimError && (
        <p style={{ color: "#dc2626" }}>{claimError}</p>
      )}

      {order && (
        <PaymentCard
          order={order}
          onPay={() => payWithPermit(orderId)}
          paying={loading}
        />
      )}

      {error && !loading && (
        <p style={{ color: "#dc2626" }}>{error}</p>
      )}

      {txResult && (
        <div style={{ padding: 16, background: "#f0fdf4", borderRadius: 8, fontSize: 13, fontFamily: "monospace" }}>
          <p style={{ margin: 0, fontWeight: "bold", color: "#16a34a" }}>✅ 支払い完了</p>
          <p style={{ margin: "4px 0", wordBreak: "break-all" }}>Permit TX: {txResult.permitTxHash}</p>
          <p style={{ margin: "4px 0", wordBreak: "break-all" }}>Transfer TX: {txResult.transferTxHash}</p>
        </div>
      )}
    </div>
  );
}
