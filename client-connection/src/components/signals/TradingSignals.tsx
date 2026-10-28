// src/components/signals/TradingSignals.tsx
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { useClock } from '@/hooks/useClock';
import { Badge } from '@/components/ui/Badge';
import { Zap, Clock } from 'lucide-react';

interface TradingSignal {
  id: string;
  symbol: string;
  type: 'BUY' | 'SELL' | 'HOLD';
  strength: 'STRONG' | 'MODERATE' | 'WEAK';
  indicator: string;
  price: number;
  target?: number;
  stopLoss?: number;
  timestamp: number;
  confidence: number;
}

interface TradingSignalsProps {
  maxSignals?: number;
}

// Mock signals until the Signal Service is connected.
const mockSignals: TradingSignal[] = [
      {
        id: '1',
        symbol: 'BTCUSDT',
        type: 'BUY',
        strength: 'STRONG',
        indicator: 'RSI Oversold + MACD Bullish Cross',
        price: 42500,
        target: 45000,
        stopLoss: 41000,
        timestamp: Date.now() - 2 * 60 * 1000,
        confidence: 85,
      },
      {
        id: '2',
        symbol: 'AAPL',
        type: 'SELL',
        strength: 'MODERATE',
        indicator: 'Moving Average Crossover',
        price: 178.5,
        target: 175.0,
        stopLoss: 180.5,
        timestamp: Date.now() - 5 * 60 * 1000,
        confidence: 72,
      },
      {
        id: '3',
        symbol: 'ETHUSDT',
        type: 'BUY',
        strength: 'STRONG',
        indicator: 'Volume Breakout',
        price: 2250,
        target: 2400,
        stopLoss: 2150,
        timestamp: Date.now() - 10 * 60 * 1000,
        confidence: 88,
      },
      {
        id: '4',
        symbol: 'TSLA',
        type: 'HOLD',
        strength: 'WEAK',
        indicator: 'Consolidation Pattern',
        price: 245.0,
        timestamp: Date.now() - 15 * 60 * 1000,
        confidence: 55,
      },
      {
        id: '5',
        symbol: 'XAUUSD',
        type: 'BUY',
        strength: 'MODERATE',
        indicator: 'Support Level Bounce',
        price: 2050,
        target: 2080,
        stopLoss: 2040,
        timestamp: Date.now() - 20 * 60 * 1000,
        confidence: 68,
      },
    ];

export function TradingSignals({ maxSignals = 10 }: TradingSignalsProps) {
  const now = useClock();
  const formatTimeAgo = (timestamp: number) => {
    const seconds = Math.floor((now - timestamp) / 1000);
    if (seconds < 60) return `${seconds}s ago`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    return `${Math.floor(seconds / 3600)}h ago`;
  };

  const getSignalColor = (type: string) => {
    switch (type) {
      case 'BUY':
        return 'bg-green-500/20 text-green-400 border-green-500/30';
      case 'SELL':
        return 'bg-red-500/20 text-red-400 border-red-500/30';
      default:
        return 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30';
    }
  };

  const getStrengthColor = (strength: string) => {
    switch (strength) {
      case 'STRONG':
        return 'text-green-400';
      case 'MODERATE':
        return 'text-yellow-400';
      default:
        return 'text-slate-400';
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Zap className="w-5 h-5 text-yellow-500" />
          Trading Signals
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {mockSignals.slice(0, maxSignals).map((signal) => (
            <div
              key={signal.id}
              className="p-3 rounded-lg border border-slate-700 hover:border-slate-600 hover:bg-slate-800 transition-colors"
            >
              {/* Header */}
              <div className="flex items-start justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="font-bold text-white">{signal.symbol}</span>
                  <Badge className={getSignalColor(signal.type)}>
                    {signal.type === 'BUY' && '🟢'}
                    {signal.type === 'SELL' && '🔴'}
                    {signal.type === 'HOLD' && '🟡'}
                    {signal.type}
                  </Badge>
                  <Badge
                    variant="outline"
                    className={getStrengthColor(signal.strength)}
                  >
                    {signal.strength}
                  </Badge>
                </div>
                <div className="flex items-center gap-1 text-xs text-slate-400">
                  <Clock className="w-3 h-3" />
                  {formatTimeAgo(signal.timestamp)}
                </div>
              </div>

              {/* Indicator */}
              <div className="text-sm text-slate-300 mb-2">
                {signal.indicator}
              </div>

              {/* Prices */}
              <div className="grid grid-cols-3 gap-2 text-xs">
                <div>
                  <div className="text-slate-400">Entry</div>
                  <div className="font-medium text-white">
                    ${signal.price.toFixed(2)}
                  </div>
                </div>
                {signal.target && (
                  <div>
                    <div className="text-slate-400">Target</div>
                    <div className="font-medium text-green-400">
                      ${signal.target.toFixed(2)}
                    </div>
                  </div>
                )}
                {signal.stopLoss && (
                  <div>
                    <div className="text-slate-400">Stop Loss</div>
                    <div className="font-medium text-red-400">
                      ${signal.stopLoss.toFixed(2)}
                    </div>
                  </div>
                )}
              </div>

              {/* Confidence */}
              <div className="mt-2 flex items-center gap-2">
                <div className="flex-1 bg-slate-700 rounded-full h-1.5">
                  <div
                    className={`h-1.5 rounded-full ${
                      signal.confidence > 75
                        ? 'bg-green-500'
                        : signal.confidence > 50
                        ? 'bg-yellow-500'
                        : 'bg-red-500'
                    }`}
                    style={{ width: `${signal.confidence}%` }}
                  />
                </div>
                <span className="text-xs text-slate-400">
                  {signal.confidence}%
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* Service Integration Note */}
        <div className="mt-4 p-3 bg-slate-800 rounded-lg text-xs text-slate-400">
          <div className="font-medium mb-1">🔌 Signal Service</div>
          <div>
            Connect to your Signal Generation Service to get AI-powered trading
            signals. Replace mock data with real-time analysis.
          </div>
        </div>
      </CardContent>
    </Card>
  );
}