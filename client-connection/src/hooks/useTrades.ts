import { useQuery } from '@tanstack/react-query';
import { fetchRecentTrades, fetchHistoricalTrades } from '../services/historicalService';
import type { HistoricalTradeQuery } from '@/types';

/**
 * Hook to fetch recent trades for a symbol
 * Will be connected to real store in Part 3
 */
export function useRecentTrades(symbol: string, enabled = true) {
  return useQuery({
    queryKey: ['recent-trades', symbol],
    queryFn: () => fetchRecentTrades(symbol, 50),
    enabled,
    refetchInterval: false, // Real-time updates will come from WebSocket
  });
}

/**
 * Hook to fetch historical trades
 * Will be fully implemented in Part 4 when Service 4 is ready
 */
export function useHistoricalTrades(query: HistoricalTradeQuery, enabled = true) {
  return useQuery({
    queryKey: ['historical-trades', query],
    queryFn: () => fetchHistoricalTrades(query),
    enabled,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
}