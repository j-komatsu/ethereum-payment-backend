const BASE = "/api/v1";

// ── 型定義 ────────────────────────────────────────────────────────────────

export type PaymentMode = "MPM" | "CPM";
export type PaymentStatus =
  | "AWAITING_CONSUMER"
  | "PENDING"
  | "CONFIRMED"
  | "OVERPAID"
  | "UNDERPAID"
  | "EXPIRED";

export interface PaymentOrder {
  id: string;
  paymentMode: PaymentMode;
  receiverAddress: string;
  senderAddress: string | null;
  consumerNonce: string | null;
  expectedAmount: string;
  token: string;
  status: PaymentStatus;
  txHash: string | null;
  createdAt: string;
  confirmedAt: string | null;
  expiresAt: string | null;
}

export interface PermitTypedData {
  primaryType: string;
  domain: {
    name: string;
    version: string;
    chainId: number;
    verifyingContract: string;
  };
  types: Record<string, { name: string; type: string }[]>;
  message: {
    owner: string;
    spender: string;
    value: string;
    nonce: string;
    deadline: string;
  };
}

export interface PermitTxResponse {
  paymentOrderId: string;
  permitTxHash: string;
  transferTxHash: string;
  status: string;
}

// ── ヘルパー ──────────────────────────────────────────────────────────────

async function request<T>(
  path: string,
  init?: RequestInit & { token?: string }
): Promise<T> {
  const { token, ...rest } = init ?? {};
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...rest.headers,
  };
  const res = await fetch(`${BASE}${path}`, { ...rest, headers });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// ── Auth ──────────────────────────────────────────────────────────────────

export async function fetchNonce(): Promise<string> {
  const data = await request<{ nonce: string }>("/auth/nonce", {
    method: "POST",
  });
  return data.nonce;
}

export async function verifySiwe(
  message: string,
  signature: string,
  address: string
): Promise<string> {
  const data = await request<{ token: string }>("/auth/verify", {
    method: "POST",
    body: JSON.stringify({ message, signature, address }),
  });
  return data.token;
}

// ── Payments ──────────────────────────────────────────────────────────────

export async function createPaymentOrder(
  token: string,
  body: {
    receiverAddress: string;
    senderAddress?: string;
    amount: string;
    token: string;
    paymentMode: PaymentMode;
    ttlSeconds?: number;
  }
): Promise<PaymentOrder> {
  return request<PaymentOrder>("/payments", {
    method: "POST",
    token,
    body: JSON.stringify(body),
  });
}

export async function getPaymentOrder(
  jwtToken: string,
  orderId: string
): Promise<PaymentOrder> {
  return request<PaymentOrder>(`/payments/${orderId}`, { token: jwtToken });
}

export async function claimPaymentOrder(
  jwtToken: string,
  orderId: string,
  consumerNonce: string
): Promise<PaymentOrder> {
  return request<PaymentOrder>(`/payments/${orderId}/claim`, {
    method: "POST",
    token: jwtToken,
    body: JSON.stringify({ consumerNonce }),
  });
}

// ── Permit ────────────────────────────────────────────────────────────────

export async function getPermitTypedData(
  jwtToken: string,
  paymentOrderId: string
): Promise<PermitTypedData> {
  return request<PermitTypedData>(
    `/permit/typed-data?paymentOrderId=${paymentOrderId}`,
    { token: jwtToken }
  );
}

export async function executePermit(
  jwtToken: string,
  paymentOrderId: string,
  nonce: string,
  deadline: number,
  signature: string
): Promise<PermitTxResponse> {
  return request<PermitTxResponse>("/permit/execute", {
    method: "POST",
    token: jwtToken,
    body: JSON.stringify({ paymentOrderId, nonce, deadline, signature }),
  });
}
