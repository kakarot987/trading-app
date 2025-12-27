// src/stores/useHistoricalStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { CandleInterval, Candle, HistoricalTrade } from '@/types/historical';

interface SymbolData {
  candles: Candle[];
  trades: HistoricalTrade[];
  lastUpdated: number;
}

interface HistoricalState {
  // Data storage by symbol
  symbolData: Record<string, SymbolData>;
  
  // UI state
  selectedInterval: CandleInterval;
  selectedSymbols: string[]; // For multi-symbol view
  timeRange: {
    start: number;
    end: number;
  };
  
  // Loading state
  loadingSymbols: Set<string>;
  
  // Actions
  setCandles: (symbol: string, candles: Candle[]) => void;
  setTrades: (symbol: string, trades: HistoricalTrade[]) => void;
  setSelectedInterval: (interval: CandleInterval) => void;
  setSelectedSymbols: (symbols: string[]) => void;
  addSymbol: (symbol: string) => void;
  removeSymbol: (symbol: string) => void;
  setTimeRange: (start: number, end: number) => void;
  setLoading: (symbol: string, loading: boolean) => void;
  clearSymbolData: (symbol: string) => void;
  clearAll: () => void;
  
  // Selectors
  getSymbolData: (symbol: string) => SymbolData | undefined;
  getLatestCandle: (symbol: string) => Candle | undefined;
  getLatestTrade: (symbol: string) => HistoricalTrade | undefined;
}

export const useHistoricalStore = create<HistoricalState>()(
  persist(
    (set, get) => ({
      symbolData: {},
      selectedInterval: '15m',
      selectedSymbols: [],
      timeRange: {
        start: Date.now() - 24 * 60 * 60 * 1000, // 24 hours ago
        end: Date.now(),
      },
      loadingSymbols: new Set(),

      setCandles: (symbol, candles) =>
        set((state) => ({
          symbolData: {
            ...state.symbolData,
            [symbol]: {
              ...state.symbolData[symbol],
              candles,
              trades: state.symbolData[symbol]?.trades || [],
              lastUpdated: Date.now(),
            },
          },
        })),

      setTrades: (symbol, trades) =>
        set((state) => ({
          symbolData: {
            ...state.symbolData,
            [symbol]: {
              ...state.symbolData[symbol],
              candles: state.symbolData[symbol]?.candles || [],
              trades,
              lastUpdated: Date.now(),
            },
          },
        })),

      setSelectedInterval: (interval) => set({ selectedInterval: interval }),

      setSelectedSymbols: (symbols) => set({ selectedSymbols: symbols }),

      addSymbol: (symbol) =>
        set((state) => ({
          selectedSymbols: state.selectedSymbols.includes(symbol)
            ? state.selectedSymbols
            : [...state.selectedSymbols, symbol],
        })),

      removeSymbol: (symbol) =>
        set((state) => ({
          selectedSymbols: state.selectedSymbols.filter((s) => s !== symbol),
        })),

      setTimeRange: (start, end) =>
        set({ timeRange: { start, end } }),

      setLoading: (symbol, loading) =>
        set((state) => {
          const newLoading = new Set(state.loadingSymbols);
          if (loading) {
            newLoading.add(symbol);
          } else {
            newLoading.delete(symbol);
          }
          return { loadingSymbols: newLoading };
        }),

      clearSymbolData: (symbol) =>
        set((state) => {
          const newData = { ...state.symbolData };
          delete newData[symbol];
          return {
            symbolData: newData,
            selectedSymbols: state.selectedSymbols.filter((s) => s !== symbol),
          };
        }),

      clearAll: () =>
        set({
          symbolData: {},
          selectedSymbols: [],
          loadingSymbols: new Set(),
        }),

      getSymbolData: (symbol) => get().symbolData[symbol],

      getLatestCandle: (symbol) => {
        const data = get().symbolData[symbol];
        if (!data?.candles.length) return undefined;
        return data.candles[data.candles.length - 1];
      },

      getLatestTrade: (symbol) => {
        const data = get().symbolData[symbol];
        if (!data?.trades.length) return undefined;
        return data.trades[data.trades.length - 1];
      },
    }),
    {
      name: 'historical-storage',
      partialize: (state) => ({
        selectedInterval: state.selectedInterval,
        selectedSymbols: state.selectedSymbols,
        timeRange: state.timeRange,
      }),
    }
  )
);

// Selectors with memoization
export const useSelectedInterval = () =>
  useHistoricalStore((state) => state.selectedInterval);

export const useSelectedSymbols = () =>
  useHistoricalStore((state) => state.selectedSymbols);

export const useTimeRange = () =>
  useHistoricalStore((state) => state.timeRange);

export const useSymbolCandles = (symbol: string) =>
  useHistoricalStore((state) => state.symbolData[symbol]?.candles || []);

export const useSymbolTrades = (symbol: string) =>
  useHistoricalStore((state) => state.symbolData[symbol]?.trades || []);

export const useIsLoading = (symbol: string) =>
  useHistoricalStore((state) => state.loadingSymbols.has(symbol));