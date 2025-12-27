import type { SymbolConfig, TimeInterval } from '@/types';

// ============================================
// Environment Configuration
// ============================================

export const config = {
  // WebSocket endpoints (Service 3)
  ws: {
    tradesUrl: import.meta.env.VITE_WS_TRADES_URL || 'ws://localhost:8082/ws/trades',
    alertsUrl: import.meta.env.VITE_WS_ALERTS_URL || 'ws://localhost:8082/ws/alerts',
    reconnectInterval: 3000,
    maxReconnectAttempts: 10,
    heartbeatInterval: 30000,
  },

  // REST API endpoints (Service 4)
  api: {
    baseUrl: import.meta.env.VITE_HISTORICAL_DATA_URL || 'http://localhost:8084',
    timeout: 10000,
  },

  // UI Configuration
  ui: {
    maxTradesInFeed: 100,        // Max trades to keep in memory per symbol
    maxAlertsInFeed: 50,         // Max alerts to keep in memory
    chartUpdateThrottle: 100,    // ms between chart updates
    tradeAnimationDuration: 300, // ms for trade highlight animation
  },
} as const;

// ============================================
// Legacy API_CONFIG export (for backward compatibility)
// ============================================
export const API_CONFIG = {
  historicalDataUrl: config.api.baseUrl,
  requestTimeout: config.api.timeout,
} as const;

// ============================================
// Available Symbols
// ============================================

export const AVAILABLE_SYMBOLS: SymbolConfig[] = [
  { symbol: 'BTCUSD', displayName: 'Bitcoin', baseAsset: 'BTC', quoteAsset: 'USD' },
  { symbol: 'ETH-USD', displayName: 'Ethereum', baseAsset: 'ETH', quoteAsset: 'USD' },
  { symbol: 'AAPL', displayName: 'Apple Inc.', baseAsset: 'AAPL', quoteAsset: 'USD' },
  { symbol: 'GOOGL', displayName: 'Alphabet Inc.', baseAsset: 'GOOGL', quoteAsset: 'USD' },
  { symbol: 'MSFT', displayName: 'Microsoft', baseAsset: 'MSFT', quoteAsset: 'USD' },
];

// ============================================
// Time Intervals
// ============================================

export const TIME_INTERVALS: { value: TimeInterval; label: string }[] = [
  { value: '1m', label: '1 Min' },
  { value: '5m', label: '5 Min' },
  { value: '15m', label: '15 Min' },
  { value: '1h', label: '1 Hour' },
  { value: '4h', label: '4 Hour' },
  { value: '1d', label: '1 Day' },
];

// ============================================
// Alert Severity Config
// ============================================

export const ALERT_SEVERITY_CONFIG = {
  LOW: { color: 'text-blue-400', bg: 'bg-blue-400/10', border: 'border-blue-400/20' },
  MEDIUM: { color: 'text-yellow-400', bg: 'bg-yellow-400/10', border: 'border-yellow-400/20' },
  HIGH: { color: 'text-orange-400', bg: 'bg-orange-400/10', border: 'border-orange-400/20' },
  CRITICAL: { color: 'text-red-400', bg: 'bg-red-400/10', border: 'border-red-400/20' },
} as const;