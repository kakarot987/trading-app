// src/hooks/useHistoricalData.ts
import { useQuery, useQueries } from '@tanstack/react-query';
import { useClock } from '@/hooks/useClock';
import { historicalApi } from '@/services/historical-api';
import type { CandleParams } from '@/types/historical';

// Query keys factory
export const historicalKeys = {
  all: ['historical'] as const,
  candles: () => [...historicalKeys.all, 'candles'] as const,
  candlesDetail: (params: CandleParams) =>
    [...historicalKeys.candles(), params] as const,
  trades: () => [...historicalKeys.all, 'trades'] as const,
  tradesRecent: (symbol: string, limit?: number) =>
    [...historicalKeys.trades(), 'recent', symbol, limit] as const,
  tradesRange: (symbol: string, startTime: number, endTime: number) =>
    [...historicalKeys.trades(), 'range', symbol, startTime, endTime] as const,
  batch: (symbols: string[], interval: string) =>
    [...historicalKeys.all, 'batch', symbols, interval] as const,
  health: () => [...historicalKeys.all, 'health'] as const,
};

/**
 * Fetch candles for a single symbol
 */
export function useCandles(
  params: CandleParams,
  options?: {
    enabled?: boolean;
    refetchInterval?: number;
  }
) {
  return useQuery({
    queryKey: historicalKeys.candlesDetail(params),
    queryFn: () => historicalApi.getCandles(params),
    staleTime: 30_000, // 30 seconds
    ...options,
  });
}

/**
 * Fetch last 24 hours of candles (convenience hook)
 */
export function useLast24HourCandles(
  symbol: string,
  interval: CandleParams['interval'] = '15m',
  enabled: boolean = true
) {
  const endTime = useClock(60_000);
  const startTime = endTime - 24 * 60 * 60 * 1000;

  return useCandles(
    { symbol, interval, startTime, endTime, limit: 96 },
    { enabled }
  );
}

/**
 * Fetch recent trades for a symbol
 */
export function useRecentTrades(
  symbol: string,
  limit: number = 100,
  options?: {
    enabled?: boolean;
    refetchInterval?: number;
  }
) {
  return useQuery({
    queryKey: historicalKeys.tradesRecent(symbol, limit),
    queryFn: () => historicalApi.getRecentTrades({ symbol, limit }),
    staleTime: 10_000, // 10 seconds
    ...options,
  });
}

/**
 * Fetch trades in a time range
 */
export function useTrades(
  symbol: string,
  startTime: number,
  endTime: number,
  enabled: boolean = true
) {
  return useQuery({
    queryKey: historicalKeys.tradesRange(symbol, startTime, endTime),
    queryFn: () => historicalApi.getTrades({ symbol, startTime, endTime }),
    staleTime: 60_000, // 1 minute
    enabled,
  });
}

/**
 * Fetch candles for multiple symbols in parallel
 */
export function useBatchCandles(
  symbols: string[],
  interval: CandleParams['interval'],
  hours: number = 24,
  enabled: boolean = true
) {
  const endTime = useClock(60_000);
  const startTime = endTime - hours * 60 * 60 * 1000;

  return useQuery({
    queryKey: historicalKeys.batch(symbols, interval),
    queryFn: () =>
      historicalApi.getBatchCandles({
        symbols,
        interval,
        startTime,
        endTime,
        limit: 500,
      }),
    staleTime: 30_000,
    enabled: enabled && symbols.length > 0,
  });
}

/**
 * Fetch candles for multiple symbols using parallel queries
 * This gives you individual loading states per symbol
 */
export function useMultiSymbolCandles(
  symbols: string[],
  interval: CandleParams['interval'],
  hours: number = 24
) {
  const endTime = useClock(60_000);
  const startTime = endTime - hours * 60 * 60 * 1000;

  return useQueries({
    queries: symbols.map((symbol) => ({
      queryKey: historicalKeys.candlesDetail({
        symbol,
        interval,
        startTime,
        endTime,
      }),
      queryFn: () =>
        historicalApi.getCandles({
          symbol,
          interval,
          startTime,
          endTime,
          limit: 500,
        }),
      staleTime: 30_000,
    })),
  });
}

/**
 * Health check query
 */
export function useHistoricalHealth(refetchInterval: number = 60_000) {
  return useQuery({
    queryKey: historicalKeys.health(),
    queryFn: () => historicalApi.getHealth(),
    refetchInterval,
    retry: 1,
  });
}

/**
 * Combined hook for dashboard - fetches both candles and recent trades
 */
export function useSymbolData(
  symbol: string,
  interval: CandleParams['interval'] = '15m',
  enabled: boolean = true
) {
  const candles = useLast24HourCandles(symbol, interval, enabled);
  const trades = useRecentTrades(symbol, 100, { enabled });

  return {
    candles: candles.data,
    trades: trades.data,
    isLoading: candles.isLoading || trades.isLoading,
    isError: candles.isError || trades.isError,
    error: candles.error || trades.error,
    refetch: () => {
      candles.refetch();
      trades.refetch();
    },
  };
}