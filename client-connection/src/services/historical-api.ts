// src/services/historical-api.ts
import { apiClient } from '@/lib/api-client';
import type {
  CandleResponse,
  BatchCandleResponse,
  CandleParams,
  CandleByDateParams,
  BatchCandleParams,
  RecentTradesParams,
  TradesParams,
  HistoricalTrade,
  HealthResponse,
} from '@/types/historical';

const BASE_PATH = '/api/v1/historical';

export const historicalApi = {
  /**
   * Get historical OHLCV candles
   */
  async getCandles(params: CandleParams): Promise<CandleResponse> {
    const { data } = await apiClient.instance.get<CandleResponse>(
      `${BASE_PATH}/candles`,
      { params }
    );
    return data;
  },

  /**
   * Get candles using ISO date-time parameters
   */
  async getCandlesByDate(params: CandleByDateParams): Promise<CandleResponse> {
    const { data } = await apiClient.instance.get<CandleResponse>(
      `${BASE_PATH}/candles-by-date`,
      { params }
    );
    return data;
  },

  /**
   * Query multiple symbols in parallel
   */
  async getBatchCandles(
    params: BatchCandleParams
  ): Promise<BatchCandleResponse> {
    const { data } = await apiClient.instance.post<BatchCandleResponse>(
      `${BASE_PATH}/candles/batch`,
      params
    );
    return data;
  },

  /**
   * Get recent trades for a symbol
   */
  async getRecentTrades(params: RecentTradesParams): Promise<HistoricalTrade[]> {
    const { data } = await apiClient.instance.get<HistoricalTrade[]>(
      `${BASE_PATH}/trades/recent`,
      { params }
    );
    return data;
  },

  /**
   * Get trades in a specific time range
   */
  async getTrades(params: TradesParams): Promise<HistoricalTrade[]> {
    const { data } = await apiClient.instance.get<HistoricalTrade[]>(
      `${BASE_PATH}/trades`,
      { params }
    );
    return data;
  },

  /**
   * Health check
   */
  async getHealth(): Promise<HealthResponse> {
    const { data } = await apiClient.instance.get<HealthResponse>(
      `${BASE_PATH}/health`
    );
    return data;
  },
};

// Helper functions for common queries

/**
 * Get candles for the last N hours
 */
export function getCandlesLastHours(
  symbol: string,
  interval: CandleParams['interval'],
  hours: number,
  limit?: number
): Promise<CandleResponse> {
  const endTime = Date.now();
  const startTime = endTime - hours * 60 * 60 * 1000;
  
  return historicalApi.getCandles({
    symbol,
    interval,
    startTime,
    endTime,
    limit,
  });
}

/**
 * Get candles for the last 24 hours
 */
export function getCandlesLast24Hours(
  symbol: string,
  interval: CandleParams['interval'] = '15m'
): Promise<CandleResponse> {
  return getCandlesLastHours(symbol, interval, 24, 96);
}

/**
 * Get last 1000 trades
 */
export function getLastTrades(
  symbol: string,
  limit: number = 1000
): Promise<HistoricalTrade[]> {
  return historicalApi.getRecentTrades({ symbol, limit });
}

/**
 * Get multiple symbols' candles at once
 */
export function getMultiSymbolCandles(
  symbols: string[],
  interval: CandleParams['interval'],
  hours: number = 24
): Promise<BatchCandleResponse> {
  const endTime = Date.now();
  const startTime = endTime - hours * 60 * 60 * 1000;
  
  return historicalApi.getBatchCandles({
    symbols,
    interval,
    startTime,
    endTime,
    limit: 500,
  });
}