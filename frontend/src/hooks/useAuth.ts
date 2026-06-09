"use client";

import { useState, useCallback } from "react";
import { useAccount, useSignMessage, useChainId } from "wagmi";
import { signInWithEthereum } from "@/lib/siwe";

const JWT_KEY = "web3pay_jwt";

export function useAuth() {
  const { address } = useAccount();
  const chainId = useChainId();
  const { signMessageAsync } = useSignMessage();

  const [jwt, setJwt] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(JWT_KEY);
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const login = useCallback(async () => {
    if (!address) throw new Error("ウォレットが接続されていません");
    setLoading(true);
    setError(null);
    try {
      const token = await signInWithEthereum(
        address,
        chainId,
        (msg) => signMessageAsync({ message: msg })
      );
      setJwt(token);
      localStorage.setItem(JWT_KEY, token);
      return token;
    } catch (e) {
      const msg = e instanceof Error ? e.message : "認証に失敗しました";
      setError(msg);
      throw e;
    } finally {
      setLoading(false);
    }
  }, [address, chainId, signMessageAsync]);

  const logout = useCallback(() => {
    setJwt(null);
    localStorage.removeItem(JWT_KEY);
  }, []);

  return { jwt, isLoggedIn: !!jwt, loading, error, login, logout };
}
