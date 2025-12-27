import { useEffect } from "react";
import { WSClient } from "./client";
import { useTradesStore } from "../state/tradesStore";

export function useTrades() {
  const addTrade = useTradesStore((s) => s.addTrade);

  useEffect(() => {
    const client = new WSClient(import.meta.env.VITE_WS_TRADES_URL);
    client.connect((msg) => {
      console.log("Inside useTrade", msg);
       if (msg.type === "trade" && msg.payload) {
    const p = msg.payload;

    const normalized = {
      ts: p.timestamp,
      symbol: p.symbol,
      price: p.price,
      size: p.volume,
      side: p.isBuy ? "BUY" : "SELL"
    };

    addTrade(normalized);
    console.log(normalized)
  }
});

    // ❌ REMOVE THIS — backend does not handle it
    // client.subscribe({
    //   type: "subscribe",
    //   channel: "trades",
    //   symbols: ["BTCUSD"],
    // });
  }, [addTrade]);
}
