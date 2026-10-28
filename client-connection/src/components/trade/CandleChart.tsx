// src/components/charts/CandleChart.tsx
import { useEffect, useRef, useState } from 'react';
import {
  createChart,
  type IChartApi,
  type ISeriesApi,
  type CandlestickData,
  type Time,
  CandlestickSeries,
  HistogramSeries,
} from 'lightweight-charts';
import type { Candle } from '@/types/historical';
import { formatPrice } from '@/lib/utils';

interface CandleChartProps {
  symbol: string;
  candles: Candle[];
  height?: number;
  showVolume?: boolean;
}

export function CandleChart({
  symbol,
  candles,
  height = 400,
  showVolume = true,
}: CandleChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleSeriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null);
  const volumeSeriesRef = useRef<ISeriesApi<'Histogram'> | null>(null);
  const [isLoading, setIsLoading] = useState(true);

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
        mode: 1, // Normal crosshair
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

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#10b981',
      downColor: '#ef4444',
      borderUpColor: '#10b981',
      borderDownColor: '#ef4444',
      wickUpColor: '#10b981',
      wickDownColor: '#ef4444',
    });

    let volumeSeries: ISeriesApi<'Histogram'> | null = null;
    if (showVolume) {
      volumeSeries = chart.addSeries(HistogramSeries, {
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
    }

    chartRef.current = chart;
    candleSeriesRef.current = candleSeries;
    volumeSeriesRef.current = volumeSeries;

    // Handle resize
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
  }, [height, showVolume]);

  // Update data
  useEffect(() => {
    if (!candleSeriesRef.current || !candles.length) return;

    setIsLoading(true);

    try {
      // Convert candles to Lightweight Charts format
      const candleData: CandlestickData[] = candles.map((candle) => ({
        time: (candle.openTime / 1000) as Time,
        open: candle.open,
        high: candle.high,
        low: candle.low,
        close: candle.close,
      }));

      candleSeriesRef.current.setData(candleData);

      // Add volume data if enabled
      if (volumeSeriesRef.current && showVolume) {
        const volumeData = candles.map((candle) => ({
          time: (candle.openTime / 1000) as Time,
          value: candle.volume,
          color: candle.close >= candle.open ? '#10b98166' : '#ef444466',
        }));

        volumeSeriesRef.current.setData(volumeData);
      }

      // Fit content to visible range
      chartRef.current?.timeScale().fitContent();

      setIsLoading(false);
    } catch (error) {
      console.error('Error updating chart data:', error);
      setIsLoading(false);
    }
  }, [candles, showVolume]);

  const latestCandle = candles[candles.length - 1];
  const priceChange = latestCandle
    ? latestCandle.close - latestCandle.open
    : 0;
  const priceChangePercent = latestCandle
    ? (priceChange / latestCandle.open) * 100
    : 0;

  return (
    <div className="relative">
      {/* Chart Header */}
      <div className="absolute top-4 left-4 z-10 bg-slate-900/80 backdrop-blur-sm px-4 py-2 rounded-lg">
        <div className="flex items-center gap-4">
          <div>
            <div className="text-sm text-slate-400">Symbol</div>
            <div className="text-lg font-bold text-white">{symbol}</div>
          </div>
          {latestCandle && (
            <>
              <div>
                <div className="text-sm text-slate-400">Price</div>
                <div className="text-lg font-bold text-white">
                  {formatPrice(latestCandle.close)}
                </div>
              </div>
              <div>
                <div className="text-sm text-slate-400">Change</div>
                <div
                  className={`text-lg font-bold ${
                    priceChange >= 0 ? 'text-green-500' : 'text-red-500'
                  }`}
                >
                  {priceChange >= 0 ? '+' : ''}
                  {priceChangePercent.toFixed(2)}%
                </div>
              </div>
              <div>
                <div className="text-sm text-slate-400">Volume</div>
                <div className="text-lg font-bold text-white">
                  {latestCandle.volume.toFixed(2)}
                </div>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Loading overlay */}
      {isLoading && (
        <div className="absolute inset-0 bg-slate-900/50 flex items-center justify-center z-20 rounded-lg">
          <div className="text-white">Loading chart...</div>
        </div>
      )}

      {/* Chart container */}
      <div ref={chartContainerRef} className="rounded-lg overflow-hidden" />

      {/* No data message */}
      {!isLoading && !candles.length && (
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="text-slate-400 text-center">
            <div className="text-2xl mb-2">📊</div>
            <div>No chart data available for {symbol}</div>
            <div className="text-sm mt-1">
              Try selecting a different time range or symbol
            </div>
          </div>
        </div>
      )}
    </div>
  );
}