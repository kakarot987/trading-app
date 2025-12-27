// src/types/historical.ts

export type CandleInterval = '1m' | '5m' | '15m' | '1h' | '1d';

export interface Candle {
  symbol: string;
  interval: CandleInterval;
  openTime: number;
  closeTime: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  trades: number;
  buyVolume: number;
  notionalValue: number;
}

export interface CandleResponse {
  symbol: string;
  interval: CandleInterval;
  candles: Candle[];
  count: number;
  fromCache: boolean;
  fromExternalApi: boolean;
  queryTimeMs: number;
}

export interface BatchCandleResponse {
  [symbol: string]: CandleResponse;
}

export interface CandleParams {
  symbol: string;
  interval: CandleInterval;
  startTime: number;
  endTime: number;
  limit?: number;
}

export interface CandleByDateParams {
  symbol: string;
  interval: CandleInterval;
  start: string; // ISO 8601
  end: string;   // ISO 8601
  limit?: number;
}

export interface BatchCandleParams {
  symbols: string[];
  interval: CandleInterval;
  startTime: number;
  endTime: number;
  limit?: number;
}

export interface RecentTradesParams {
  symbol: string;
  from?: number;
  limit?: number;
}

export interface TradesParams {
  symbol: string;
  startTime: number;
  endTime: number;
}

export interface HistoricalTrade {
  id: number;
  tradeId: string;
  symbol: string;
  exchange: string;
  price: number;
  volume: number;
  tradeTimestamp: number;
  side: 'BUY' | 'SELL';
  processedAt: number;
  latencyMs: number;
}

export interface HealthResponse {
  status: 'UP' | 'DOWN';
  timestamp: number;
  service: string;
}

export interface ApiErrorResponse {
  error: string;
  timestamp: number;
}