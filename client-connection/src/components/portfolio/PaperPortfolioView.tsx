import { useCallback, useEffect, useState } from 'react';

type Balance = { asset: string; available: string };
type Order = {
  orderId: string; clientOrderId: string; symbol: string; side: string;
  quantity: string; submittedAtMs: number; status: string; rejectionReason: string | null;
};

type RealizedInventory = {
  asset: string;
  fillCount: number;
  remainingQuantity: string;
  remainingCostBasisUsdt: string;
  realizedPnlUsdt: string;
  netQuoteMovementUsdt: string;
};

type Evaluation = {
  activity: {
    fillCount: number;
    tradedNotionalUsdt: string;
    paidFeesUsdt: string;
    quoteLedgerNetUsdt: string;
  };
  evidence: string[];
  realizedInventory?: RealizedInventory[] | null;
  limitations: string[];
  inventory: unknown[] | null;
  equity: unknown | null;
  intervalRisk: unknown | null;
  benchmark: unknown | null;
};

const WALLET_KEY = 'paper-wallet-id';

export function PaperPortfolioView() {
  const [walletInput, setWalletInput] = useState(() => localStorage.getItem(WALLET_KEY) ?? '');
  const [walletId, setWalletId] = useState(() => localStorage.getItem(WALLET_KEY) ?? '');
  // A scoped bearer capability is a secret: never put it in localStorage,
  // query strings, logs or a persisted state store. Reload clears it.
  const [credentialInput, setCredentialInput] = useState('');
  const [walletCredential, setWalletCredential] = useState('');
  const [balances, setBalances] = useState<Balance[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [evaluation, setEvaluation] = useState<Evaluation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    if (!walletId) return;
    setLoading(true);
    setError('');
    try {
      const id = encodeURIComponent(walletId);
      const options: RequestInit = {
        cache: 'no-store',
        ...(walletCredential
          ? { headers: { Authorization: `Bearer ${walletCredential}` } }
          : {}),
      };
      const [balanceResponse, orderResponse, evaluationResponse] = await Promise.all([
        fetch(`/api/v1/paper/wallets/${id}/balances`, options),
        fetch(`/api/v1/paper/wallets/${id}/orders?limit=50`, options),
        fetch(`/api/v1/paper/wallets/${id}/evaluation`, options)
      ]);
      if (!balanceResponse.ok || !orderResponse.ok || !evaluationResponse.ok) {
        throw new Error('Portfolio API unavailable or wallet request rejected');
      }
      const nextBalances: Balance[] = await balanceResponse.json();
      const nextOrders: Order[] = await orderResponse.json();
      const nextEvaluation: Evaluation = await evaluationResponse.json();
      setBalances(nextBalances);
      setOrders(nextOrders);
      setEvaluation(nextEvaluation);
    } catch (cause) {
      setBalances([]);
      setOrders([]);
      setEvaluation(null);
      setError(cause instanceof Error ? cause.message : 'Unable to load paper portfolio');
    } finally {
      setLoading(false);
    }
  }, [walletId, walletCredential]);

  useEffect(() => { void refresh(); }, [refresh]);

  function selectWallet() {
    const id = walletInput.trim();
    if (!/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(id)) {
      setError('Enter a valid paper wallet UUID');
      return;
    }
    const secret = credentialInput.trim();
    if (secret && !/^pw_[A-Za-z0-9_-]{43}$/.test(secret)) {
      setError('Enter a valid wallet-specific access token');
      return;
    }
    localStorage.setItem(WALLET_KEY, id);
    setWalletCredential(secret);
    setWalletId(id);
    setBalances([]);
    setOrders([]);
    setEvaluation(null);
    setError('');
  }

  return (
    <section className="space-y-5">
      <header>
        <h1 className="text-2xl font-semibold">Paper portfolio</h1>
        <p className="text-sm text-slate-400">Simulated spot trading only. Read-only balances and order history; no real funds or live orders.</p>
      </header>
      <div className="rounded-lg border border-slate-700 p-4 space-y-3">
        <label className="block text-sm" htmlFor="paper-wallet-id">Paper wallet ID (UUID)</label>
        <div className="flex flex-wrap gap-2">
          <input id="paper-wallet-id" className="min-w-64 flex-1 rounded border border-slate-600 bg-slate-900 p-2 text-sm"
            value={walletInput} onChange={(event) => setWalletInput(event.target.value)}
            placeholder="Paste an existing paper wallet UUID" />
          <button type="button" onClick={selectWallet} className="rounded bg-sky-700 px-4 py-2 text-sm">Load wallet</button>
          <button type="button" onClick={() => void refresh()} disabled={!walletId || loading}
            className="rounded border border-slate-600 px-4 py-2 text-sm disabled:opacity-50">Refresh</button>
        </div>
        <label className="block text-sm" htmlFor="paper-wallet-credential">
          Wallet access token (private demo only)
        </label>
        <input id="paper-wallet-credential" type="password" autoComplete="off"
          className="w-full rounded border border-slate-600 bg-slate-900 p-2 text-sm"
          value={credentialInput} onChange={(event) => setCredentialInput(event.target.value)}
          placeholder="pw_... — issued once when the wallet is created" />
        <p className="text-xs text-slate-500">
          Wallet UUIDs are not credentials. Access tokens stay only in memory,
          are cleared on page reload and are not saved to browser storage.
          This is a private paper demo, not multi-user identity authentication.
        </p>
        {error && <p role="alert" className="text-sm text-red-400">{error}</p>}
      </div>
      <div className="rounded-lg border border-slate-700 p-4">
        <h2 className="mb-3 font-semibold">Available balances</h2>
        {balances.length === 0 ? <p className="text-sm text-slate-400">No balances loaded.</p> :
          <div className="grid gap-3 sm:grid-cols-3">{balances.map((item) =>
            <div className="rounded bg-slate-800 p-3" key={item.asset}>
              <p className="text-xs text-slate-400">{item.asset}</p>
              <p className="font-mono text-lg">{item.available}</p>
            </div>)}</div>}
      </div>
      <div className="rounded-lg border border-slate-700 p-4 space-y-3">
        <h2 className="font-semibold">Paper evaluation</h2>
        {!evaluation ? <p className="text-sm text-slate-400">No evaluation loaded.</p> : (
          <>
            <p className="text-xs text-slate-400">Persisted simulated activity only; cash movement is not profit.</p>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded bg-slate-800 p-3"><p className="text-xs text-slate-400">Recorded fills</p><p className="font-mono">{evaluation.activity.fillCount}</p></div>
              <div className="rounded bg-slate-800 p-3"><p className="text-xs text-slate-400">Trade turnover (USDT)</p><p className="font-mono">{evaluation.activity.tradedNotionalUsdt}</p></div>
              <div className="rounded bg-slate-800 p-3"><p className="text-xs text-slate-400">Fees paid (USDT)</p><p className="font-mono">{evaluation.activity.paidFeesUsdt}</p></div>
            </div>

            {evaluation.realizedInventory && evaluation.realizedInventory.length > 0 && (
              <div className="space-y-2">
                <h3 className="text-sm font-medium">Realized paper inventory (USDT)</h3>
                <p className="text-xs text-slate-400">
                  From recorded paper fills only. Realized P&amp;L is not total portfolio
                  return; unmarked holdings have no unrealized P&amp;L estimate.
                </p>
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm">
                    <thead><tr className="border-b border-slate-700">
                      <th className="p-2">Asset</th><th className="p-2">Fills</th>
                      <th className="p-2">Remaining quantity</th>
                      <th className="p-2">Remaining cost basis</th>
                      <th className="p-2">Realized P&amp;L</th>
                    </tr></thead>
                    <tbody>{evaluation.realizedInventory.map((position) =>
                      <tr key={position.asset} className="border-b border-slate-800">
                        <td className="p-2">{position.asset}</td>
                        <td className="p-2">{position.fillCount}</td>
                        <td className="p-2 font-mono">{position.remainingQuantity}</td>
                        <td className="p-2 font-mono">{position.remainingCostBasisUsdt}</td>
                        <td className="p-2 font-mono">{position.realizedPnlUsdt}</td>
                      </tr>
                    )}</tbody>
                  </table>
                </div>
              </div>
            )}
            <h3 className="text-sm font-medium">Unavailable evaluations and limitations</h3>
            <ul className="list-disc space-y-1 pl-5 text-xs text-slate-400">
              {evaluation.limitations.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </>
        )}
      </div>
      <div className="overflow-x-auto rounded-lg border border-slate-700 p-4">
        <h2 className="mb-3 font-semibold">Paper order history</h2>
        {orders.length === 0 ? <p className="text-sm text-slate-400">No orders loaded.</p> :
          <table className="w-full text-left text-sm">
            <thead><tr className="border-b border-slate-700">
              <th className="p-2">Submitted</th><th className="p-2">Symbol</th>
              <th className="p-2">Side</th><th className="p-2">Quantity</th><th className="p-2">Status</th>
            </tr></thead>
            <tbody>{orders.map((order) => <tr key={order.orderId} className="border-b border-slate-800">
              <td className="p-2">{new Date(order.submittedAtMs).toLocaleString()}</td>
              <td className="p-2">{order.symbol}</td><td className="p-2">{order.side}</td>
              <td className="p-2 font-mono">{order.quantity}</td><td className="p-2">{order.status}</td>
            </tr>)}</tbody>
          </table>}
      </div>
    </section>
  );
}
