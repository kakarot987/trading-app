import { useCallback, useEffect, useState } from 'react';

type Balance = { asset: string; available: string };
type Order = {
  orderId: string; clientOrderId: string; symbol: string; side: string;
  quantity: string; submittedAtMs: number; status: string; rejectionReason: string | null;
};

const WALLET_KEY = 'paper-wallet-id';

export function PaperPortfolioView() {
  const [walletInput, setWalletInput] = useState(() => localStorage.getItem(WALLET_KEY) ?? '');
  const [walletId, setWalletId] = useState(() => localStorage.getItem(WALLET_KEY) ?? '');
  const [balances, setBalances] = useState<Balance[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    if (!walletId) return;
    setLoading(true);
    setError('');
    try {
      const id = encodeURIComponent(walletId);
      const [balanceResponse, orderResponse] = await Promise.all([
        fetch(`/api/v1/paper/wallets/${id}/balances`),
        fetch(`/api/v1/paper/wallets/${id}/orders?limit=50`)
      ]);
      if (!balanceResponse.ok || !orderResponse.ok) {
        throw new Error('Portfolio API unavailable or wallet request rejected');
      }
      const nextBalances: Balance[] = await balanceResponse.json();
      const nextOrders: Order[] = await orderResponse.json();
      setBalances(nextBalances);
      setOrders(nextOrders);
    } catch (cause) {
      setBalances([]);
      setOrders([]);
      setError(cause instanceof Error ? cause.message : 'Unable to load paper portfolio');
    } finally {
      setLoading(false);
    }
  }, [walletId]);

  useEffect(() => { void refresh(); }, [refresh]);

  function selectWallet() {
    const id = walletInput.trim();
    if (!/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(id)) {
      setError('Enter a valid paper wallet UUID');
      return;
    }
    localStorage.setItem(WALLET_KEY, id);
    setWalletId(id);
    setBalances([]);
    setOrders([]);
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
        <p className="text-xs text-slate-500">Local development only. A wallet ID is not authentication; do not expose this API publicly.</p>
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
