// src/components/dashboard/HistoricalDashboard.tsx
import { useState, useEffect } from 'react';
import { CandleChart } from '@/components/trade/CandleChart';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import {
  useHistoricalStore,
  useSelectedInterval,
  useSelectedSymbols,
} from '@/stores/useHistoricalStore';
import {
  useBatchCandles,
  useMultiSymbolCandles,
  useHistoricalHealth,
} from '@/hooks/useHistoricalData';
import type { CandleInterval } from '@/types/historical';
import { AVAILABLE_SYMBOLS } from '@/config';
import { formatTimestamp } from '@/lib/utils';
import { Activity, TrendingUp, TrendingDown, RefreshCw } from 'lucide-react';

// Extract just the symbol strings from AVAILABLE_SYMBOLS
const SYMBOL_LIST = AVAILABLE_SYMBOLS.map(s => s.symbol);

const INTERVALS: CandleInterval[] = ['1m', '5m', '15m', '1h', '1d'];
const TIME_RANGES = [
  { label: '1H', hours: 1 },
  { label: '4H', hours: 4 },
  { label: '24H', hours: 24 },
  { label: '7D', hours: 168 },
  { label: '30D', hours: 720 },
];

export function HistoricalDashboard() {
  const selectedInterval = useSelectedInterval();
  const selectedSymbols = useSelectedSymbols();
  const { setSelectedInterval, addSymbol, removeSymbol, setCandles } =
    useHistoricalStore();

  const [timeRangeHours, setTimeRangeHours] = useState(24);
  const [useBatch, setUseBatch] = useState(true);

  // Health check
  const { data: health } = useHistoricalHealth();

  // Fetch data based on mode (batch vs parallel)
  const batchQuery = useBatchCandles(
    selectedSymbols,
    selectedInterval,
    timeRangeHours,
    useBatch && selectedSymbols.length > 0
  );

  const parallelQueries = useMultiSymbolCandles(
    selectedSymbols,
    selectedInterval,
    timeRangeHours
  );

  // Update store when data arrives
  useEffect(() => {
    if (useBatch && batchQuery.data) {
      Object.entries(batchQuery.data).forEach(([symbol, response]) => {
        setCandles(symbol, response.candles);
      });
    }
  }, [batchQuery.data, useBatch, setCandles]);

  useEffect(() => {
    if (!useBatch) {
      parallelQueries.forEach((query, index) => {
        if (query.data) {
          setCandles(selectedSymbols[index], query.data.candles);
        }
      });
    }
  }, [parallelQueries, useBatch, selectedSymbols, setCandles]);

  const toggleSymbol = (symbol: string) => {
    if (selectedSymbols.includes(symbol)) {
      removeSymbol(symbol);
    } else {
      addSymbol(symbol);
    }
  };

  const handleRefresh = () => {
    if (useBatch) {
      batchQuery.refetch();
    } else {
      parallelQueries.forEach((q) => q.refetch());
    }
  };

  const isLoading = useBatch
    ? batchQuery.isLoading
    : parallelQueries.some((q) => q.isLoading);

  const hasError = useBatch
    ? batchQuery.isError
    : parallelQueries.some((q) => q.isError);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-white">Historical Data</h1>
          <p className="text-slate-400 mt-1">
            View and analyze historical candles and trades
          </p>
        </div>
        <div className="flex items-center gap-3">
          {health && (
            <Badge variant={health.status === 'UP' ? 'success' : 'destructive'}>
              <Activity className="w-3 h-3 mr-1" />
              Service {health.status}
            </Badge>
          )}
          <Button onClick={handleRefresh} disabled={isLoading}>
            <RefreshCw className={`w-4 h-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>
      </div>

      {/* Controls */}
      <Card className="p-6">
        <div className="space-y-4">
          {/* Symbol Selection */}
          <div>
            <label className="text-sm font-medium text-slate-300 mb-2 block">
              Select Symbols ({selectedSymbols.length} selected)
            </label>
            <div className="flex flex-wrap gap-2">
              {SYMBOL_LIST.map((symbol) => (
                <Button
                  key={symbol}
                  onClick={() => toggleSymbol(symbol)}
                  variant={
                    selectedSymbols.includes(symbol) ? 'default' : 'secondary'
                  }
                  size="sm"
                >
                  {symbol}
                </Button>
              ))}
            </div>
          </div>

          {/* Interval Selection */}
          <div>
            <label className="text-sm font-medium text-slate-300 mb-2 block">
              Candle Interval
            </label>
            <div className="flex gap-2">
              {INTERVALS.map((interval) => (
                <Button
                  key={interval}
                  onClick={() => setSelectedInterval(interval)}
                  variant={
                    selectedInterval === interval ? 'default' : 'secondary'
                  }
                  size="sm"
                >
                  {interval}
                </Button>
              ))}
            </div>
          </div>

          {/* Time Range */}
          <div>
            <label className="text-sm font-medium text-slate-300 mb-2 block">
              Time Range
            </label>
            <div className="flex gap-2">
              {TIME_RANGES.map(({ label, hours }) => (
                <Button
                  key={label}
                  onClick={() => setTimeRangeHours(hours)}
                  variant={timeRangeHours === hours ? 'default' : 'secondary'}
                  size="sm"
                >
                  {label}
                </Button>
              ))}
            </div>
          </div>

          {/* Fetch Mode Toggle */}
          <div className="flex items-center gap-2">
            <label className="text-sm font-medium text-slate-300">
              Fetch Mode:
            </label>
            <Button
              onClick={() => setUseBatch(!useBatch)}
              variant="secondary"
              size="sm"
            >
              {useBatch ? '📦 Batch API' : '⚡ Parallel Queries'}
            </Button>
            <span className="text-xs text-slate-400">
              {useBatch
                ? 'Single request for all symbols'
                : 'Individual loading states per symbol'}
            </span>
          </div>
        </div>
      </Card>

      {/* Error State */}
      {hasError && (
        <Card className="p-6 border-red-500/50 bg-red-500/10">
          <div className="flex items-center gap-3 text-red-400">
            <TrendingDown className="w-5 h-5" />
            <div>
              <div className="font-medium">Failed to load historical data</div>
              <div className="text-sm text-red-300">
                Check console for details or try refreshing
              </div>
            </div>
          </div>
        </Card>
      )}

      {/* Empty State */}
      {!selectedSymbols.length && (
        <Card className="p-12 text-center">
          <div className="text-slate-400">
            <TrendingUp className="w-16 h-16 mx-auto mb-4 opacity-50" />
            <h3 className="text-xl font-medium mb-2">No Symbols Selected</h3>
            <p>Select one or more symbols above to view historical charts</p>
          </div>
        </Card>
      )}

      {/* Charts Grid */}
      {selectedSymbols.length > 0 && (
        <div className="grid gap-6">
          {selectedSymbols.map((symbol, index) => {
            const queryData = useBatch
              ? batchQuery.data?.[symbol]
              : parallelQueries[index]?.data;

            const queryLoading = useBatch
              ? batchQuery.isLoading
              : parallelQueries[index]?.isLoading;

            const candles = queryData?.candles || [];

            return (
              <Card key={symbol} className="p-6">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <h3 className="text-xl font-bold text-white">{symbol}</h3>
                    {queryLoading && (
                      <Badge variant="outline">Loading...</Badge>
                    )}
                    {queryData && (
                      <>
                        <Badge
                          variant={
                            queryData.fromCache ? 'success' : 'outline'
                          }
                        >
                          {queryData.fromCache ? '⚡ Cached' : '🌐 API'}
                        </Badge>
                        <span className="text-xs text-slate-400">
                          {queryData.queryTimeMs}ms • {queryData.count} candles
                        </span>
                      </>
                    )}
                  </div>
                  <Button
                    onClick={() => removeSymbol(symbol)}
                    variant="ghost"
                    size="sm"
                  >
                    Remove
                  </Button>
                </div>

                <CandleChart
                  symbol={symbol}
                  candles={candles}
                  height={400}
                  showVolume={true}
                />

                {queryData && candles.length > 0 && (
                  <div className="mt-4 text-xs text-slate-400 text-center">
                    Updated {formatTimestamp(Date.now())}
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}