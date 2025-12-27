import { useState, useEffect, useRef } from 'react';
import { TrendingUp, Filter } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge } from '../ui';
import { TradeCard } from './TradeCard';
import type { ProcessedTradeEvent } from '@/types';

interface TradeFeedProps {
  trades: ProcessedTradeEvent[];
  maxTrades?: number;
  selectedSymbol?: string | null;
}

export function TradeFeed({ trades, maxTrades = 50, selectedSymbol = null }: TradeFeedProps) {
  const [showOnlyBuys, setShowOnlyBuys] = useState(false);
  const [showOnlySells, setShowOnlySells] = useState(false);
  const [newTradeIds, setNewTradeIds] = useState<Set<string>>(new Set());
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const prevTradesLengthRef = useRef(trades.length);

  // Filter trades
  const filteredTrades = trades
    .filter((trade) => {
      if (selectedSymbol && trade.symbol !== selectedSymbol) return false;
      if (showOnlyBuys && !trade.isBuy) return false;
      if (showOnlySells && trade.isBuy) return false;
      return true;
    })
    .slice(0, maxTrades);

  // Track new trades for animation
  useEffect(() => {
    if (trades.length > prevTradesLengthRef.current) {
      const newTrades = trades.slice(0, trades.length - prevTradesLengthRef.current);
      const newIds = new Set(newTrades.map((t) => t.tradeId));
      setNewTradeIds(newIds);

      // Remove highlight after animation
      const timer = setTimeout(() => setNewTradeIds(new Set()), 1000);
      return () => clearTimeout(timer);
    }
    prevTradesLengthRef.current = trades.length;
  }, [trades]);

  // Auto-scroll to top on new trade
  useEffect(() => {
    if (scrollContainerRef.current && trades.length > prevTradesLengthRef.current) {
      scrollContainerRef.current.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [trades.length]);

  const buyCount = filteredTrades.filter((t) => t.isBuy).length;
  const sellCount = filteredTrades.length - buyCount;

  return (
    <Card className="flex h-full flex-col">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-[hsl(var(--primary))]" />
            <CardTitle>Live Trades</CardTitle>
            <Badge variant="outline">{filteredTrades.length}</Badge>
          </div>

          {/* Filters */}
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant={showOnlyBuys ? 'default' : 'ghost'}
              onClick={() => {
                setShowOnlyBuys(!showOnlyBuys);
                setShowOnlySells(false);
              }}
              className="h-7 gap-1"
            >
              <span className="h-2 w-2 rounded-full bg-green-400" />
              Buys ({buyCount})
            </Button>
            <Button
              size="sm"
              variant={showOnlySells ? 'default' : 'ghost'}
              onClick={() => {
                setShowOnlySells(!showOnlySells);
                setShowOnlyBuys(false);
              }}
              className="h-7 gap-1"
            >
              <span className="h-2 w-2 rounded-full bg-red-400" />
              Sells ({sellCount})
            </Button>
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex-1 overflow-hidden">
        {filteredTrades.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center space-y-2 text-center">
            <Filter className="h-8 w-8 text-[hsl(var(--muted-foreground))]" />
            <p className="text-sm text-[hsl(var(--muted-foreground))]">
              {trades.length === 0
                ? 'Waiting for trades...'
                : 'No trades match current filters'}
            </p>
          </div>
        ) : (
          <div
            ref={scrollContainerRef}
            className="h-full space-y-2 overflow-y-auto pr-2"
          >
            {filteredTrades.map((trade) => (
              <TradeCard
                key={trade.tradeId}
                trade={trade}
                isNew={newTradeIds.has(trade.tradeId)}
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}