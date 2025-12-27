import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { formatPrice, formatVolume, formatTimestamp, formatLatency, formatNotional } from '@/lib/utils';
import { cn } from '@/lib/utils';
import type { ProcessedTradeEvent } from '@/types';

interface TradeCardProps {
  trade: ProcessedTradeEvent;
  isNew?: boolean;
}

export function TradeCard({ trade, isNew = false }: TradeCardProps) {
  return (
    <div
      className={cn(
        'group flex items-center gap-3 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--card))] p-3 transition-all',
        isNew && 'animate-pulse border-[hsl(var(--primary))] bg-[hsl(var(--primary))]/5',
        trade.isBuy ? 'hover:border-green-500/50' : 'hover:border-red-500/50'
      )}
    >
      {/* Buy/Sell Indicator */}
      <div
        className={cn(
          'flex h-10 w-10 items-center justify-center rounded-lg',
          trade.isBuy ? 'bg-green-500/10 text-green-400' : 'bg-red-500/10 text-red-400'
        )}
      >
        {trade.isBuy ? (
          <ArrowUpRight className="h-5 w-5" />
        ) : (
          <ArrowDownRight className="h-5 w-5" />
        )}
      </div>

      {/* Trade Details */}
      <div className="flex-1 space-y-1">
        <div className="flex items-center justify-between">
          <span className="font-mono text-xs font-medium text-[hsl(var(--muted-foreground))]">
            {trade.symbol}
          </span>
          <span className="text-xs text-[hsl(var(--muted-foreground))]">
            {formatTimestamp(trade.timestamp)}
          </span>
        </div>

        <div className="flex items-baseline gap-2">
          <span
            className={cn(
              'font-mono text-lg font-semibold',
              trade.isBuy ? 'text-green-400' : 'text-red-400'
            )}
          >
            ${formatPrice(trade.price)}
          </span>
          <span className="text-xs text-[hsl(var(--muted-foreground))]">
            × {formatVolume(trade.volume)}
          </span>
        </div>

        <div className="flex items-center justify-between text-xs text-[hsl(var(--muted-foreground))]">
          <span>Notional: {formatNotional(trade.notionalValue)}</span>
          <span>Latency: {formatLatency(trade.latencyMs)}</span>
        </div>
      </div>
    </div>
  );
}