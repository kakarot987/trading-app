import { create } from "zustand";

type Trade = { ts: number; symbol: string; price: number; size: number; side: string };

type Store = {
trades: Trade[];
addTrade: (t: Trade) => void;
};

export const useTradesStore = create<Store>((set) => ({
  trades: [],
  addTrade: (t) =>
    set((s) => ({
      trades: [...s.trades.slice(-499), t], // keep last 500
    })),
}));

export default useTradesStore;