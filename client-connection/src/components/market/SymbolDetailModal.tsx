// src/components/market/SymbolDetailModal.tsx
import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { HybridChart } from '@/components/trade/HybridChart';
import { TradeFeed } from '@/components/trade/TradeFeed';
import { useTradeStore } from '@/stores';
import type { CandleInterval } from '@/types/historical';
import { X, BarChart3, Activity } from 'lucide-react';

interface SymbolDetailModalProps {
  symbol: string;
  onClose: () => void;
}

const INTERVALS: CandleInterval[] = ['1m', '5m', '15m', '1h', '1d'];
const TIME_RANGES = [
  { label: '1H', hours: 1 },
  { label: '4H', hours: 4 },
  { label: '24H', hours: 24 },
  { label: '7D', hours: 168 },
  { label: '30D', hours: 720 },
];

export function SymbolDetailModal({ symbol, onClose }: SymbolDetailModalProps) {
  const [interval, setInterval] = useState<CandleInterval>('15m');
  const [timeRange, setTimeRange] = useState(24);
  const [activeTab, setActiveTab] = useState<'chart' | 'trades'>('chart');

  const trades = useTradeStore((state) => state.trades);
  const symbolTrades = trades.filter((t) => t.symbol === symbol);

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-slate-900 rounded-lg shadow-2xl w-full max-w-7xl max-h-[90vh] overflow-hidden flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-slate-700">
          <div className="flex items-center gap-4">
            <h2 className="text-2xl font-bold text-white">{symbol}</h2>
            <Badge variant="success" className="animate-pulse">
              🔴 LIVE
            </Badge>
          </div>

          <div className="flex items-center gap-3">
            {/* Tab Switcher */}
            <div className="flex gap-1 bg-slate-800 rounded-lg p-1">
              <Button
                onClick={() => setActiveTab('chart')}
                variant={activeTab === 'chart' ? 'default' : 'ghost'}
                size="sm"
              >
                <BarChart3 className="w-4 h-4 mr-2" />
                Chart
              </Button>
              <Button
                onClick={() => setActiveTab('trades')}
                variant={activeTab === 'trades' ? 'default' : 'ghost'}
                size="sm"
              >
                <Activity className="w-4 h-4 mr-2" />
                Trades
              </Button>
            </div>

            <Button onClick={onClose} variant="ghost" size="sm">
              <X className="w-5 h-5" />
            </Button>
          </div>
        </div>

        {/* Controls */}
        <div className="p-4 border-b border-slate-700 flex items-center justify-between">
          {/* Interval */}
          <div className="flex items-center gap-2">
            <span className="text-sm text-slate-400">Interval:</span>
            <div className="flex gap-1">
              {INTERVALS.map((int) => (
                <Button
                  key={int}
                  onClick={() => setInterval(int)}
                  variant={interval === int ? 'default' : 'secondary'}
                  size="sm"
                >
                  {int}
                </Button>
              ))}
            </div>
          </div>

          {/* Time Range */}
          <div className="flex items-center gap-2">
            <span className="text-sm text-slate-400">Range:</span>
            <div className="flex gap-1">
              {TIME_RANGES.map(({ label, hours }) => (
                <Button
                  key={label}
                  onClick={() => setTimeRange(hours)}
                  variant={timeRange === hours ? 'default' : 'secondary'}
                  size="sm"
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {activeTab === 'chart' ? (
            <HybridChart
              symbol={symbol}
              liveTrades={symbolTrades}
              interval={interval}
              timeRangeHours={timeRange}
              height={600}
            />
          ) : (
            <Card className="h-full">
              <TradeFeed trades={symbolTrades} maxTrades={200} />
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}