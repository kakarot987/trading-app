// src/components/market/MarketOverview.tsx
import { useMemo } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { Badge } from '@/components/ui/Badge';
import { useTradeStore } from '@/stores';
import { formatPrice, formatVolume } from '@/lib/utils';
import { TrendingUp, TrendingDown, ArrowRight } from 'lucide-react';

interface MarketOverviewProps {
  symbols: string[];
  onSymbolClick: (symbol: string) => void;
  compact?: boolean; // New prop for compact view
}

export function MarketOverview({ symbols, onSymbolClick, compact = false }: MarketOverviewProps) {
  const trades = useTradeStore((state) => state.trades);

  // Calculate stats for each symbol
  const symbolStats = useMemo(() => {
    return symbols.map((symbol) => {
      const symbolTrades = trades.filter((t) => t.symbol === symbol);
      
      if (symbolTrades.length === 0) {
        return {
          symbol,
          price: 0,
          change: 0,
          changePercent: 0,
          volume: 0,
          tradeCount: 0,
          lastUpdate: 0,
        };
      }

      // Sort by timestamp
      const sorted = [...symbolTrades].sort((a, b) => a.timestamp - b.timestamp);
      const latest = sorted[sorted.length - 1];
      const oldest = sorted[0];

      const priceChange = latest.price - oldest.price;
      const changePercent = (priceChange / oldest.price) * 100;
      const totalVolume = symbolTrades.reduce((sum, t) => sum + t.volume, 0);

      return {
        symbol,
        price: latest.price,
        change: priceChange,
        changePercent,
        volume: totalVolume,
        tradeCount: symbolTrades.length,
        lastUpdate: latest.timestamp,
      };
    });
  }, [symbols, trades]);

  // Sort by absolute change (most volatile first)
  const sortedStats = useMemo(() => {
    return [...symbolStats].sort((a, b) => 
      Math.abs(b.changePercent) - Math.abs(a.changePercent)
    );
  }, [symbolStats]);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <TrendingUp className="w-5 h-5 text-green-500" />
          Market Overview
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className={compact ? "space-y-1" : "space-y-2"}>
          {sortedStats.map((stat) => {
            const isPositive = stat.change >= 0;
            const hasData = stat.tradeCount > 0;

            return (
              <button
                key={stat.symbol}
                onClick={() => onSymbolClick(stat.symbol)}
                className={`w-full flex items-center justify-between ${
                  compact ? 'p-2' : 'p-3'
                } rounded-lg hover:bg-slate-800 transition-colors border border-slate-700 hover:border-slate-600 group`}
              >
                {/* Left: Symbol & Price */}
                <div className="flex-1 text-left">
                  <div className={`font-bold text-white ${compact ? 'text-xs' : 'mb-1'}`}>
                    {stat.symbol}
                  </div>
                  {hasData ? (
                    <div className={`font-bold text-white ${compact ? 'text-base' : 'text-2xl'}`}>
                      {formatPrice(stat.price)}
                    </div>
                  ) : (
                    <div className="text-xs text-slate-500">Waiting...</div>
                  )}
                </div>

                {/* Middle: Change */}
                {hasData && (
                  <div className="flex-1 text-center">
                    <div
                      className={`flex items-center justify-center gap-1 font-medium ${
                        isPositive ? 'text-green-500' : 'text-red-500'
                      } ${compact ? 'text-xs' : ''}`}
                    >
                      {isPositive ? (
                        <TrendingUp className={compact ? 'w-3 h-3' : 'w-4 h-4'} />
                      ) : (
                        <TrendingDown className={compact ? 'w-3 h-3' : 'w-4 h-4'} />
                      )}
                      <span>
                        {isPositive ? '+' : ''}
                        {stat.changePercent.toFixed(2)}%
                      </span>
                    </div>
                    {!compact && (
                      <div className="text-xs text-slate-400 mt-1">
                        {isPositive ? '+' : ''}
                        {formatPrice(stat.change)}
                      </div>
                    )}
                  </div>
                )}

                {/* Right: Volume & Arrow */}
                {!compact && (
                  <div className="flex-1 text-right flex items-center justify-end gap-3">
                    {hasData && (
                      <div>
                        <div className="text-xs text-slate-400">Volume</div>
                        <div className="text-sm font-medium text-white">
                          {formatVolume(stat.volume)}
                        </div>
                        <Badge variant="outline" className="text-xs mt-1">
                          {stat.tradeCount} trades
                        </Badge>
                      </div>
                    )}
                    <ArrowRight className="w-5 h-5 text-slate-500 group-hover:text-blue-500 transition-colors" />
                  </div>
                )}
                
                {compact && hasData && (
                  <ArrowRight className="w-4 h-4 text-slate-500 group-hover:text-blue-500 transition-colors ml-2" />
                )}
              </button>
            );
          })}
        </div>

        {sortedStats.every(s => s.tradeCount === 0) && (
          <div className="text-center py-8 text-slate-400">
            <div className="text-4xl mb-2">📊</div>
            <div>Waiting for market data...</div>
            <div className="text-xs mt-1">
              WebSocket will start streaming prices shortly
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}