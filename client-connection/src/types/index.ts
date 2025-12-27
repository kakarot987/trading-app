// ============================================
// WebSocket Message Types (from Service 3)
// ============================================

export interface ProcessedTradeEvent {
  tradeId: string;
  symbol: string;
  price: number;
  volume: number;
  timestamp: number;
  processedAt: number;
  latencyMs: number;
  notionalValue: number;
  isBuy: boolean;
}

export interface AlertEvent {
  alertId: string;
  symbol: string;
  severity: AlertSeverity;
  type: AlertType;
  message: string;
  triggerValue: number | null;
  threshold: number | null;
  timestamp: number;
}

export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AlertType = 'PRICE_SPIKE' | 'VOLUME_ANOMALY' | 'PATTERN_DETECTED' | 'THRESHOLD_BREACH';

// ============================================
// WebSocket Subscription Types
// ============================================

export interface SubscriptionMessage {
  symbols: string[];
}

export type WebSocketStatus = 'connecting' | 'connected' | 'disconnected' | 'reconnecting' | 'error';

export interface WebSocketState {
  status: WebSocketStatus;
  error: string | null;
  lastConnected: number | null;
  reconnectAttempts: number;
}

// ============================================
// Historical Data Types (Service 4)
// ============================================

export interface HistoricalTradeQuery {
  symbol: string;
  interval?: TimeInterval;
  from?: number;
  to?: number;
  limit?: number;
}

export interface CandlestickData {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export type TimeInterval = '1m' | '5m' | '15m' | '1h' | '4h' | '1d';

// ============================================
// UI State Types
// ============================================

export interface SymbolConfig {
  symbol: string;
  displayName: string;
  baseAsset: string;
  quoteAsset: string;
  icon?: string;
}

export interface DashboardPanel {
  id: string;
  type: 'chart' | 'trades' | 'alerts' | 'orderbook';
  symbol?: string;
  settings?: Record<string, unknown>;
}

// ============================================
// API Response Types
// ============================================

export interface ApiResponse<T> {
  data: T;
  success: boolean;
  error?: string;
  timestamp: number;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
}