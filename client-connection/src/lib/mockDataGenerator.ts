import type { ProcessedTradeEvent, AlertEvent, AlertSeverity, AlertType } from '@/types';
import { generateId } from './utils';

// ============================================
// Mock Data Generator for Testing UI
// ============================================

const SYMBOLS = ['BTCUSD', 'ETHUSD', 'AAPL', 'GOOGL', 'MSFT'];

const BASE_PRICES: Record<string, number> = {
  'BTCUSD': 95000,
  'ETHUSD': 3500,
  'AAPL': 185,
  'GOOGL': 142,
  'MSFT': 378,
};

const ALERT_TYPES: AlertType[] = ['PRICE_SPIKE', 'VOLUME_ANOMALY', 'PATTERN_DETECTED', 'THRESHOLD_BREACH'];
const ALERT_SEVERITIES: AlertSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

const ALERT_MESSAGES: Record<AlertType, string[]> = {
  PRICE_SPIKE: [
    'Significant price movement detected',
    'Unusual price volatility observed',
    'Sharp price increase detected',
  ],
  VOLUME_ANOMALY: [
    'Trading volume exceeded normal levels',
    'Unusual trading activity detected',
    'Volume spike detected',
  ],
  PATTERN_DETECTED: [
    'Bullish pattern formation detected',
    'Technical pattern identified',
    'Price pattern breakout detected',
  ],
  THRESHOLD_BREACH: [
    'Price crossed resistance level',
    'Support level breached',
    'Key threshold exceeded',
  ],
};

/**
 * Generate a random trade event
 */
export function generateMockTrade(symbol?: string): ProcessedTradeEvent {
  const selectedSymbol = symbol || SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)];
  const basePrice = BASE_PRICES[selectedSymbol] || 100;
  
  // Price varies ±2% from base
  const priceVariation = (Math.random() - 0.5) * 0.04;
  const price = basePrice * (1 + priceVariation);
  
  const volume = Math.random() * 10 + 0.1;
  const isBuy = Math.random() > 0.5;
  const notionalValue = price * volume;
  const latencyMs = Math.random() * 50 + 5;
  
  const timestamp = Date.now();
  
  return {
    tradeId: generateId(),
    symbol: selectedSymbol,
    price: Number(price.toFixed(2)),
    volume: Number(volume.toFixed(4)),
    timestamp,
    processedAt: timestamp + Math.floor(latencyMs),
    latencyMs: Number(latencyMs.toFixed(2)),
    notionalValue: Number(notionalValue.toFixed(2)),
    isBuy,
  };
}

/**
 * Generate a random alert event
 */
export function generateMockAlert(symbol?: string): AlertEvent {
  const selectedSymbol = symbol || SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)];
  const alertType = ALERT_TYPES[Math.floor(Math.random() * ALERT_TYPES.length)];
  const severity = ALERT_SEVERITIES[Math.floor(Math.random() * ALERT_SEVERITIES.length)];
  
  const messages = ALERT_MESSAGES[alertType];
  const message = messages[Math.floor(Math.random() * messages.length)];
  
  const basePrice = BASE_PRICES[selectedSymbol] || 100;
  const triggerValue = Math.random() > 0.5 ? basePrice * (1 + (Math.random() - 0.5) * 0.1) : null;
  const threshold = triggerValue ? triggerValue * 0.95 : null;
  
  return {
    alertId: generateId(),
    symbol: selectedSymbol,
    severity,
    type: alertType,
    message,
    triggerValue: triggerValue ? Number(triggerValue.toFixed(2)) : null,
    threshold: threshold ? Number(threshold.toFixed(2)) : null,
    timestamp: Date.now(),
  };
}

/**
 * Start generating mock trades at a specified interval
 */
export function startMockTradeGenerator(
  onTrade: (trade: ProcessedTradeEvent) => void,
  symbols: string[],
  intervalMs = 1000
): () => void {
  const interval = setInterval(() => {
    // Generate 1-3 trades per interval
    const tradeCount = Math.floor(Math.random() * 3) + 1;
    
    for (let i = 0; i < tradeCount; i++) {
      const symbol = symbols.length > 0 
        ? symbols[Math.floor(Math.random() * symbols.length)]
        : undefined;
      
      const trade = generateMockTrade(symbol);
      onTrade(trade);
    }
  }, intervalMs);
  
  // Return cleanup function
  return () => clearInterval(interval);
}

/**
 * Start generating mock alerts at a specified interval
 */
export function startMockAlertGenerator(
  onAlert: (alert: AlertEvent) => void,
  symbols: string[],
  intervalMs = 5000
): () => void {
  const interval = setInterval(() => {
    const symbol = symbols.length > 0 
      ? symbols[Math.floor(Math.random() * symbols.length)]
      : undefined;
    
    const alert = generateMockAlert(symbol);
    onAlert(alert);
  }, intervalMs);
  
  // Return cleanup function
  return () => clearInterval(interval);
}