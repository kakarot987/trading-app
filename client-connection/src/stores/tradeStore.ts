import { createWithEqualityFn } from 'zustand/traditional';
import { persist } from 'zustand/middleware';
import { config } from '@/config';
import type { ProcessedTradeEvent } from '@/types';

/**
 * Trade Store - manages all incoming trades with symbol-based organization
 * Now with persistence, statistics, and performance optimizations
 */

interface TradeStatistics {
  totalVolume: number;
  avgPrice: number;
  minPrice: number;
  maxPrice: number;
  buyVolume: number;
  sellVolume: number;
  tradeCount: number;
  lastPrice: number;
  priceChange: number; // Change from first to last trade
  priceChangePercent: number;
}

interface TradeStore {
  trades: ProcessedTradeEvent[];
  tradesBySymbol: Map<string, ProcessedTradeEvent[]>;
  
  // Actions
  addTrade: (trade: ProcessedTradeEvent) => void;
  addTrades: (trades: ProcessedTradeEvent[]) => void;
  clearTrades: () => void;
  clearTradesBySymbol: (symbol: string) => void;
  
  // Selectors
  getTradesBySymbol: (symbol: string) => ProcessedTradeEvent[];
  getRecentTrades: (limit: number) => ProcessedTradeEvent[];
  getTradesByTimeRange: (startTime: number, endTime: number) => ProcessedTradeEvent[];
  
  // Statistics
  getTotalTrades: () => number;
  getTradeCountBySymbol: (symbol: string) => number;
  getStatisticsBySymbol: (symbol: string) => TradeStatistics | null;
}

export const useTradeStore = create<TradeStore>()(
  persist(
    (set, get) => ({
      trades: [],
      tradesBySymbol: new Map(),

      addTrade: (trade) => {
        set((state) => {
          // Add to global trades list (keep max trades)
          const newTrades = [trade, ...state.trades].slice(0, config.ui.maxTradesInFeed);
          
          // Update trades by symbol
          const symbolTrades = state.tradesBySymbol.get(trade.symbol) || [];
          const newSymbolTrades = [trade, ...symbolTrades].slice(0, config.ui.maxTradesInFeed);
          
          const newTradesBySymbol = new Map(state.tradesBySymbol);
          newTradesBySymbol.set(trade.symbol, newSymbolTrades);
          
          return {
            trades: newTrades,
            tradesBySymbol: newTradesBySymbol,
          };
        });
      },

      addTrades: (trades) => {
        if (trades.length === 0) return;
        
        set((state) => {
          // Merge and sort by timestamp (newest first)
          const allTrades = [...trades, ...state.trades]
            .sort((a, b) => b.timestamp - a.timestamp)
            .slice(0, config.ui.maxTradesInFeed);
          
          // Rebuild trades by symbol map
          const newTradesBySymbol = new Map<string, ProcessedTradeEvent[]>();
          
          allTrades.forEach((trade) => {
            const symbolTrades = newTradesBySymbol.get(trade.symbol) || [];
            if (symbolTrades.length < config.ui.maxTradesInFeed) {
              symbolTrades.push(trade);
              newTradesBySymbol.set(trade.symbol, symbolTrades);
            }
          });
          
          return {
            trades: allTrades,
            tradesBySymbol: newTradesBySymbol,
          };
        });
      },

      clearTrades: () => {
        set({ trades: [], tradesBySymbol: new Map() });
      },

      clearTradesBySymbol: (symbol) => {
        set((state) => {
          const newTradesBySymbol = new Map(state.tradesBySymbol);
          newTradesBySymbol.delete(symbol);
          
          // Also remove from global trades
          const newTrades = state.trades.filter((t) => t.symbol !== symbol);
          
          return {
            trades: newTrades,
            tradesBySymbol: newTradesBySymbol,
          };
        });
      },

      getTradesBySymbol: (symbol) => {
        const state = get();
        return state.tradesBySymbol.get(symbol) || [];
      },

      getRecentTrades: (limit) => {
        return get().trades.slice(0, limit);
      },

      getTradesByTimeRange: (startTime, endTime) => {
        return get().trades.filter(
          (trade) => trade.timestamp >= startTime && trade.timestamp <= endTime
        );
      },
      
      getTotalTrades: () => {
        return get().trades.length;
      },
      
      getTradeCountBySymbol: (symbol) => {
        const trades = get().tradesBySymbol.get(symbol);
        return trades ? trades.length : 0;
      },

      getStatisticsBySymbol: (symbol) => {
        const trades = get().tradesBySymbol.get(symbol);
        if (!trades || trades.length === 0) return null;

        const prices = trades.map((t) => t.price);
        const totalVolume = trades.reduce((sum, t) => sum + t.volume, 0);
        const buyVolume = trades.filter((t) => t.isBuy).reduce((sum, t) => sum + t.volume, 0);
        const sellVolume = totalVolume - buyVolume;
        
        const firstPrice = trades[trades.length - 1].price;
        const lastPrice = trades[0].price;
        const priceChange = lastPrice - firstPrice;
        const priceChangePercent = (priceChange / firstPrice) * 100;

        return {
          totalVolume,
          avgPrice: prices.reduce((sum, p) => sum + p, 0) / prices.length,
          minPrice: Math.min(...prices),
          maxPrice: Math.max(...prices),
          buyVolume,
          sellVolume,
          tradeCount: trades.length,
          lastPrice,
          priceChange,
          priceChangePercent,
        };
      },
    }),
    {
      name: 'trade-storage',
      // Only persist essential data, not the full trade history
      partialize: () => ({
        // Don't persist trades to avoid localStorage quota issues
        // Only persist the structure
      }),
    }
  )
);