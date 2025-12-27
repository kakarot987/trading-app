import { useTradesStore } from "../../core/state/tradesStore";

export function TradesTicker() {
  const trades = useTradesStore((s) => s.trades);

  return (
    <div className="p-4 bg-gray-900 text-white">
      <h2 className="font-bold mb-2">BTCUSD Trades</h2>
      <ul className="max-h-64 overflow-y-auto">
        {trades.map((t, i) => (
          <li key={i} className="text-sm">
            {new Date(t.ts).toLocaleTimeString()} — {t.symbol} {t.side} {t.size} @ {t.price}
          </li>
        ))}
      </ul>
    </div>
  );
}
