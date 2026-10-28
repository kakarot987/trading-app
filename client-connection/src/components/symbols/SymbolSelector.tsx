import { Check, X } from 'lucide-react';
import { Badge } from '@/components/ui';
import { AVAILABLE_SYMBOLS } from '@/config';
import { cn } from '@/lib/utils';

interface SymbolSelectorProps {
  selectedSymbols: string[];
  onSymbolToggle: (symbol: string) => void;
  onSymbolRemove?: (symbol: string) => void;
  maxSymbols?: number;
}

export function SymbolSelector({
  selectedSymbols,
  onSymbolToggle,
  onSymbolRemove,
  maxSymbols = 6,
}: SymbolSelectorProps) {
  const isSelected = (symbol: string) => selectedSymbols.includes(symbol);
  const canAddMore = selectedSymbols.length < maxSymbols;

  return (
    <div className="space-y-4">
      {/* Selected Symbols */}
      {selectedSymbols.length > 0 && (
        <div className="space-y-2">
          <p className="text-xs font-medium text-[hsl(var(--muted-foreground))]">
            Active Symbols ({selectedSymbols.length}/{maxSymbols})
          </p>
          <div className="flex flex-wrap gap-2">
            {selectedSymbols.map((symbol) => {
              return (
                <Badge
                  key={symbol}
                  variant="default"
                  className="group flex items-center gap-1.5 pl-2 pr-1 hover:bg-[hsl(var(--primary))]/20"
                >
                  <span className="font-mono text-xs">{symbol}</span>
                  {onSymbolRemove && (
                    <button
                      onClick={() => onSymbolRemove(symbol)}
                      className="rounded-full p-0.5 opacity-60 hover:bg-white/10 hover:opacity-100"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  )}
                </Badge>
              );
            })}
          </div>
        </div>
      )}

      {/* Available Symbols */}
      <div className="space-y-2">
        <p className="text-xs font-medium text-[hsl(var(--muted-foreground))]">
          Available Symbols
        </p>
        <div className="grid grid-cols-2 gap-2">
          {AVAILABLE_SYMBOLS.map((symbolConfig) => {
            const selected = isSelected(symbolConfig.symbol);
            const disabled = !selected && !canAddMore;

            return (
              <button
                key={symbolConfig.symbol}
                onClick={() => onSymbolToggle(symbolConfig.symbol)}
                disabled={disabled}
                className={cn(
                  'group relative flex items-center justify-between rounded-lg border p-3 text-left transition-colors',
                  selected
                    ? 'border-[hsl(var(--primary))] bg-[hsl(var(--primary))]/10'
                    : 'border-[hsl(var(--border))] hover:border-[hsl(var(--primary))]/50 hover:bg-[hsl(var(--accent))]',
                  disabled && 'cursor-not-allowed opacity-50'
                )}
              >
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm font-medium">
                      {symbolConfig.symbol}
                    </span>
                    {selected && (
                      <Check className="h-3.5 w-3.5 text-[hsl(var(--primary))]" />
                    )}
                  </div>
                  <p className="mt-0.5 text-xs text-[hsl(var(--muted-foreground))]">
                    {symbolConfig.displayName}
                  </p>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {!canAddMore && (
        <p className="text-xs text-yellow-400">
          Maximum {maxSymbols} symbols can be tracked simultaneously
        </p>
      )}
    </div>
  );
}