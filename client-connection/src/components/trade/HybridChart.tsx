// src/components/charts/HybridChart.tsx
import { useEffect, useRef, useState, useMemo } from 'react';
import {
  createChart,
  type IChartApi,
  type CandlestickData,
  type Time,
  type UTCTimestamp,
  CandlestickSeries,
  HistogramSeries,
} from 'lightweight-charts';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { useLast24HourCandles } from '@/hooks/useHistoricalData';
import type { Trade } from '@/types';
import type { CandleInterval } from '@/types/historical';
import { formatPrice, formatTimestamp } from '@/lib/utils';
import { RefreshCw, TrendingUp, TrendingDown } from 'lucide-react';

interface HybridChartProps {
  symbol: string;
  liveTrades: Trade[]; // WebSocket trades
  interval?: CandleInterval;
  timeRangeHours?: number;
  height?: number;
}

export function HybridChart({
  symbol,
  liveTrades,
  interval = '15m',
  timeRangeHours = 24,
  height = 500,
}: HybridChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<any>(null);
  const volumeSeriesRef = useRef<any>(null);
  
  const [liveCandle, setLiveCandle] = useState<CandlestickData | null>(null);
  const [intervalMs, setIntervalMs] = useState(15 * 60 * 1000); // 15m default

  // Fetch historical data
  const { data: historicalData, isLoading, refetch } = useLast24HourCandles(
    symbol,
    interval,
    true
  );

  // Calculate interval in milliseconds
  useEffect(() => {
    const intervals: Record<CandleInterval, number> = {
      '1m': 60 * 1000,
      '5m': 5 * 60 * 1000,
      '15m': 15 * 60 * 1000,
      '1h': 60 * 60 * 1000,
      '1d': 24 * 60 * 60 * 1000,
    };
    setIntervalMs(intervals[interval]);
  }, [interval]);

  // Build current live candle from WebSocket trades
  useEffect(() => {
    if (!liveTrades.length) return;

    const now = Date.now();
    const currentCandleStartTime = Math.floor(now / intervalMs) * intervalMs;
    
    // Get trades within current candle period
    const currentPeriodTrades = liveTrades.filter(
      (trade) => trade.timestamp >= currentCandleStartTime
    );

    if (currentPeriodTrades.length === 0) return;

    // Build candle from trades
    const prices = currentPeriodTrades.map((t) => t.price);
    const volumes = currentPeriodTrades.map((t) => t.volume);

    const newCandle: CandlestickData = {
      time: (currentCandleStartTime / 1000) as UTCTimestamp,
      open: currentPeriodTrades[0].price,
      high: Math.max(...prices),
      low: Math.min(...prices),
      close: currentPeriodTrades[currentPeriodTrades.length - 1].price,
    };

    setLiveCandle(newCandle);
  }, [liveTrades, intervalMs]);

  // Initialize chart
  useEffect(() => {
    if (!chartContainerRef.current) return;

    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth,
      height,
      layout: {
        background: { color: '#0f172a' },
        textColor: '#94a3b8',
      },
      grid: {
        vertLines: { color: '#1e293b' },
        horzLines: { color: '#1e293b' },
      },
      crosshair: {
        mode: 1,
      },
      rightPriceScale: {
        borderColor: '#334155',
      },
      timeScale: {
        borderColor: '#334155',
        timeVisible: true,
        secondsVisible: false,
      },
    });

    // Use the new API with series types
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#10b981',
      downColor: '#ef4444',
      borderUpColor: '#10b981',
      borderDownColor: '#ef4444',
      wickUpColor: '#10b981',
      wickDownColor: '#ef4444',
    });

    const volumeSeries = chart.addSeries(HistogramSeries, {
      color: '#64748b',
      priceFormat: {
        type: 'volume',
      },
      priceScaleId: 'volume',
    });

    chart.priceScale('volume').applyOptions({
      scaleMargins: {
        top: 0.8,
        bottom: 0,
      },
    });

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;

    const handleResize = () => {
      if (chartContainerRef.current && chart) {
        chart.applyOptions({
          width: chartContainerRef.current.clientWidth,
        });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
    };
  }, [height]);

  // Update chart with historical + live data
  useEffect(() => {
    if (!candleSeriesRef.current || !volumeSeriesRef.current) return;

    try {
      // Prepare historical candles
      const historicalCandles: CandlestickData[] = historicalData?.candles.map(
        (candle) => ({
          time: (candle.openTime / 1000) as UTCTimestamp,
          open: candle.open,
          high: candle.high,
          low: candle.low,
          close: candle.close,
        })
      ) || [];

      // Combine historical + live candle
      let allCandles = [...historicalCandles];
      
      if (liveCandle) {
        // Check if live candle is newer than last historical candle
        const lastHistoricalTime = historicalCandles.length > 0
          ? historicalCandles[historicalCandles.length - 1].time
          : 0;
        
        if (liveCandle.time > lastHistoricalTime) {
          // Add as new candle
          allCandles.push(liveCandle);
        } else if (liveCandle.time === lastHistoricalTime) {
          // Update last candle
          allCandles[allCandles.length - 1] = liveCandle;
        }
      }

      // Set candle data
      candleSeriesRef.current.setData(allCandles);

      // Prepare volume data
      const volumeData = historicalData?.candles.map((candle) => ({
        time: (candle.openTime / 1000) as UTCTimestamp,
        value: candle.volume,
        color: candle.close >= candle.open ? '#10b98166' : '#ef444466',
      })) || [];

      volumeSeriesRef.current.setData(volumeData);

      // Auto-scroll to latest data
      chartRef.current?.timeScale().scrollToRealTime();
      
    } catch (error) {
      console.error('Error updating hybrid chart:', error);
    }
  }, [historicalData, liveCandle]);

  // Stats calculations
  const stats = useMemo(() => {
    const candles = historicalData?.candles || [];
    const latestCandle = liveCandle || (candles.length > 0 ? {
      open: candles[candles.length - 1].open,
      high: candles[candles.length - 1].high,
      low: candles[candles.length - 1].low,
      close: candles[candles.length - 1].close,
    } : null);

    if (!latestCandle) return null;

    const priceChange = latestCandle.close - latestCandle.open;
    const priceChangePercent = (priceChange / latestCandle.open) * 100;
    const isLive = liveCandle !== null;

    return {
      price: latestCandle.close,
      change: priceChange,
      changePercent: priceChangePercent,
      high: latestCandle.high,
      low: latestCandle.low,
      isLive,
    };
  }, [historicalData, liveCandle]);

  const liveTradeCount = liveTrades.filter(
    t => t.timestamp >= Date.now() - intervalMs
  ).length;

  return (
    <Card className="p-6">
      {/* Chart Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-4">
          <div>
            <h3 className="text-xl font-bold text-white">{symbol}</h3>
            <div className="text-xs text-slate-400">
              {interval} • {timeRangeHours}H History
            </div>
          </div>

          {stats && (
            <>
              <div className="border-l border-slate-700 pl-4">
                <div className="text-xs text-slate-400">Price</div>
                <div className="text-lg font-bold text-white">
                  {formatPrice(stats.price)}
                </div>
              </div>

              <div className="border-l border-slate-700 pl-4">
                <div className="text-xs text-slate-400">Change</div>
                <div
                  className={`text-lg font-bold flex items-center gap-1 ${
                    stats.change >= 0 ? 'text-green-500' : 'text-red-500'
                  }`}
                >
                  {stats.change >= 0 ? (
                    <TrendingUp className="w-4 h-4" />
                  ) : (
                    <TrendingDown className="w-4 h-4" />
                  )}
                  {stats.change >= 0 ? '+' : ''}
                  {stats.changePercent.toFixed(2)}%
                </div>
              </div>

              <div className="border-l border-slate-700 pl-4">
                <div className="text-xs text-slate-400">H / L</div>
                <div className="text-sm font-medium text-white">
                  {formatPrice(stats.high)} / {formatPrice(stats.low)}
                </div>
              </div>
            </>
          )}
        </div>

        <div className="flex items-center gap-3">
          {stats?.isLive && (
            <Badge variant="success" className="animate-pulse">
              🔴 LIVE • {liveTradeCount} trades
            </Badge>
          )}
          
          {historicalData && (
            <Badge variant={historicalData.fromCache ? 'success' : 'secondary'}>
              {historicalData.fromCache ? '⚡ Cached' : '🌐 API'} •{' '}
              {historicalData.queryTimeMs}ms
            </Badge>
          )}

          {isLoading && <Badge variant="secondary">Loading...</Badge>}

          <Button onClick={() => refetch()} variant="ghost" size="sm">
            <RefreshCw className="w-4 h-4" />
          </Button>
        </div>
      </div>

      {/* Chart Container */}
      <div ref={chartContainerRef} className="rounded-lg overflow-hidden" />

      {/* Footer Info */}
      <div className="mt-3 flex items-center justify-between text-xs text-slate-400">
        <div>
          Historical: {historicalData?.count || 0} candles
          {liveCandle && ' + 1 live candle'}
        </div>
        <div>
          Updated {formatTimestamp(Date.now())}
        </div>
      </div>

      {/* No Data State */}
      {!isLoading && !historicalData?.candles.length && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-900/50 rounded-lg">
          <div className="text-center text-slate-400">
            <div className="text-2xl mb-2">📊</div>
            <div>No historical data available</div>
            <div className="text-xs mt-1">
              Ensure Service 4 is running on port 8084
            </div>
          </div>
        </div>
      )}
    </Card>
  );
}