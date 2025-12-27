import { apiClient } from './api';
import type { HistoricalTradeQuery, CandlestickData, ProcessedTradeEvent, ApiResponse } from '@/types';

// ============================================
// Historical Data Service (Service 4)
// ============================================

/**
 * Fetch historical trades for a given symbol and time range
 * Placeholder - will be connected to Service 4 in Part 4
 */
export async function fetchHistoricalTrades(
  query: HistoricalTradeQuery
): Promise<ProcessedTradeEvent[]> {
  try {
    const response = await apiClient.get<ApiResponse<ProcessedTradeEvent[]>>('/trades', {
      params: {
        symbol: query.symbol,
        interval: query.interval,
        from: query.from,
        to: query.to,
        limit: query.limit,
      },
    });
    return response.data.data;
  } catch (error) {
    console.error('Failed to fetch historical trades:', error);
    // Return empty array for now - mock data can be added here
    return [];
  }
}

/**
 * Fetch candlestick data for charting
 * Placeholder - will be connected to Service 4 in Part 4
 */
export async function fetchCandlestickData(
  query: HistoricalTradeQuery
): Promise<CandlestickData[]> {
  try {
    const response = await apiClient.get<ApiResponse<CandlestickData[]>>('/candles', {
      params: {
        symbol: query.symbol,
        interval: query.interval,
        from: query.from,
        to: query.to,
        limit: query.limit,
      },
    });
    return response.data.data;
  } catch (error) {
    console.error('Failed to fetch candlestick data:', error);
    // Return empty array for now - mock data can be added here
    return [];
  }
}

/**
 * Fetch recent trades (cached in Redis)
 * Placeholder - will be connected to Service 4 in Part 4
 */
export async function fetchRecentTrades(
  symbol: string,
  limit = 100
): Promise<ProcessedTradeEvent[]> {
  try {
    const response = await apiClient.get<ApiResponse<ProcessedTradeEvent[]>>('/trades/recent', {
      params: { symbol, limit },
    });
    return response.data.data;
  } catch (error) {
    console.error('Failed to fetch recent trades:', error);
    return [];
  }
}