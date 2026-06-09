"use client";

import { useState, useCallback } from "react";
import { useAccount, useSignTypedData } from "wagmi";
import {
  getPaymentOrder,
  getPermitTypedData,
  executePermit,
  type PaymentOrder,
  type PermitTxResponse,
} from "@/lib/api";

export function usePayment(jwt: string | null) {
  const { address } = useAccount();
  const { signTypedDataAsync } = useSignTypedData();

  const [order, setOrder] = useState<PaymentOrder | null>(null);
  const [txResult, setTxResult] = useState<PermitTxResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchOrder = useCallback(
    async (orderId: string) => {
      if (!jwt) return;
      setLoading(true);
      setError(null);
      try {
        const o = await getPaymentOrder(jwt, orderId);
        setOrder(o);
      } catch (e) {
        setError(e instanceof Error ? e.message : "取得失敗");
      } finally {
        setLoading(false);
      }
    },
    [jwt]
  );

  /**
   * EIP-2612 Permit フロー
   * 1. バックエンドから EIP-712 typed data を取得
   * 2. eth_signTypedData_v4 でウォレット署名
   * 3. バックエンドに署名を送信 → permit + transferFrom を実行
   */
  const payWithPermit = useCallback(
    async (orderId: string) => {
      if (!jwt || !address) return;
      setLoading(true);
      setError(null);
      try {
        // Step 1: typed data 取得
        const typedData = await getPermitTypedData(jwt, orderId);

        // deadline は message に文字列で入っているので数値に変換
        const deadline = parseInt(typedData.message.deadline, 10);

        // Step 2: ウォレット署名（Wagmi v2 の signTypedData）
        const signature = await signTypedDataAsync({
          domain: {
            ...typedData.domain,
            chainId: BigInt(typedData.domain.chainId),
            verifyingContract: typedData.domain.verifyingContract as `0x${string}`,
          },
          types: typedData.types as Record<string, { name: string; type: string }[]>,
          primaryType: typedData.primaryType,
          message: {
            owner: typedData.message.owner as `0x${string}`,
            spender: typedData.message.spender as `0x${string}`,
            value: BigInt(typedData.message.value),
            nonce: BigInt(typedData.message.nonce),
            deadline: BigInt(deadline),
          },
        });

        // Step 3: バックエンド実行
        const result = await executePermit(
          jwt,
          orderId,
          typedData.message.nonce,
          deadline,
          signature
        );
        setTxResult(result);
        // ステータスを更新
        const updated = await getPaymentOrder(jwt, orderId);
        setOrder(updated);
        return result;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Permit 実行失敗");
        throw e;
      } finally {
        setLoading(false);
      }
    },
    [jwt, address, signTypedDataAsync]
  );

  return { order, txResult, loading, error, fetchOrder, payWithPermit };
}
