import { useMemo } from 'react';
import { useTradeStore } from '@/stores';

/**
 * Custom hook for accessing trade statistics with memoization
 * Prevents unnecessary re-renders when data hasn't changed
 */
export function useTradeStats(symbol: string) {
  // Get the raw data from store - use shallow equality check
  const trades = useTradeStore(
    (state) => state.getTradesBySymbol(symbol),
    (a, b) => {
      // Custom equality check - only re-render if array length or last item changed
      if (a.length !== b.length) return false;
      if (a.length === 0 && b.length === 0) return true;
      return a[a.length - 1]?.tradeId === b[b.length - 1]?.tradeId;
    }
  );

  const stats = useTradeStore(
    (state) => state.getStatisticsBySymbol(symbol),
    (a, b) => {
      // Deep equality check for stats object
      if (a === null && b === null) return true;
      if (a === null || b === null) return false;
      return (
        a.lastPrice === b.lastPrice &&
        a.priceChange === b.priceChange &&
        a.tradeCount === b.tradeCount &&
        a.totalVolume === b.totalVolume
      );
    }
  );

  // Memoize derived values to prevent recalculation on every render
  const memoizedStats = useMemo(() => {
    if (!stats) return null;

    return {
      ...stats,
      // Add additional computed values
      buyPressure: stats.totalVolume > 0 ? stats.buyVolume / stats.totalVolume : 0,
      sellPressure: stats.totalVolume > 0 ? stats.sellVolume / stats.totalVolume : 0,
      volatility: stats.avgPrice > 0 ? ((stats.maxPrice - stats.minPrice) / stats.avgPrice) * 100 : 0,
      momentum: stats.priceChangePercent > 0 ? 'bullish' as const : 'bearish' as const,
    };
  }, [stats]);

  return {
    trades,
    stats: memoizedStats,
    hasData: trades.length > 0,
  };
}

/**
 * Hook for getting aggregated trade statistics across all symbols
 */
export function useAggregatedStats() {
  const totalTrades = useTradeStore((state) => state.getTotalTrades());
  
  // Use a selector that returns stable references
  const allTrades = useTradeStore(
    (state) => state.trades,
    (a, b) => a.length === b.length && (a.length === 0 || a[a.length - 1]?.tradeId === b[b.length - 1]?.tradeId)
  );

  const aggregated = useMemo(() => {
    if (allTrades.length === 0) {
      return {
        totalTrades: 0,
        totalVolume: 0,
        uniqueSymbols: 0,
        avgLatency: 0,
      };
    }

    const uniqueSymbols = new Set(allTrades.map((t) => t.symbol)).size;
    const totalVolume = allTrades.reduce((sum, t) => sum + t.volume, 0);
    const avgLatency = allTrades.reduce((sum, t) => sum + t.latencyMs, 0) / allTrades.length;

    return {
      totalTrades,
      totalVolume,
      uniqueSymbols,
      avgLatency,
    };
  }, [allTrades, totalTrades]);

  return aggregated;
}

/**
 * Hook for getting top performing symbols
 */
export function useTopSymbols(limit = 5) {
  // Get stable references from the store
  const symbols = useTradeStore(
    (state) => Array.from(state.tradesBySymbol.keys()),
    (a, b) => a.length === b.length && a.every((sym, i) => sym === b[i])
  );

  const topSymbols = useMemo(() => {
    const { getStatisticsBySymbol } = useTradeStore.getState();
    
    const symbolStats = symbols
      .map((symbol) => ({
        symbol,
        stats: getStatisticsBySymbol(symbol),
      }))
      .filter((item) => item.stats !== null)
      .sort((a, b) => {
        // Sort by price change percentage (descending)
        return (b.stats?.priceChangePercent || 0) - (a.stats?.priceChangePercent || 0);
      })
      .slice(0, limit);

    return symbolStats;
  }, [symbols, limit]);

  return topSymbols;
}