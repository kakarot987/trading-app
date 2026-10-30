import { useEffect, useRef } from 'react';
import {
  createChart,
  type IChartApi,
  type ISeriesApi,
  type Time,
  LineSeries,
} from 'lightweight-charts';
import { LineChart, Maximize2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent, Button } from '@/components/ui';
import { TIME_INTERVALS } from '@/config';
import type { ProcessedTradeEvent, TimeInterval } from '@/types';

interface TradeChartProps {
  symbol: string;
  trades: ProcessedTradeEvent[];
  interval?: TimeInterval;
  onIntervalChange?: (interval: TimeInterval) => void;
}

export function TradeChart({
  symbol,
  trades,
  interval = '1m',
  onIntervalChange,
}: TradeChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ISeriesApi<'Line'> | null>(null);

  // Initialize chart
  useEffect(() => {
    if (!chartContainerRef.current) return;

    const chart = createChart(chartContainerRef.current, {
      width: chartContainerRef.current.clientWidth,
      height: 400,
      layout: {
        background: { color: 'transparent' },
        textColor: 'rgb(156, 163, 175)',
      },
      grid: {
        vertLines: { color: 'rgb(55, 65, 81)' },
        horzLines: { color: 'rgb(55, 65, 81)' },
      },
      rightPriceScale: {
        borderColor: 'rgb(55, 65, 81)',
      },
      timeScale: {
        borderColor: 'rgb(55, 65, 81)',
        timeVisible: true,
        secondsVisible: false,
      },
    });

    // Use the built-in LineSeries definition
    const lineSeries = chart.addSeries(LineSeries, {
      color: 'rgb(59, 130, 246)',
      lineWidth: 2,
    });

    chartRef.current = chart;
    seriesRef.current = lineSeries;

    // Handle resize
    const handleResize = () => {
      if (chartContainerRef.current && chartRef.current) {
        chartRef.current.applyOptions({
          width: chartContainerRef.current.clientWidth,
        });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
    };
  }, []);

  // Update chart data when trades change
  useEffect(() => {
    if (!seriesRef.current || trades.length === 0) return;

    // Convert trades to line data and handle duplicate timestamps
    const lineDataMap = new Map<number, number>();
    
    trades.forEach((trade) => {
      const time = Math.floor(trade.timestamp / 1000);
      // Keep only the latest price for each timestamp
      if (!lineDataMap.has(time) || trade.timestamp > (lineDataMap.get(time) || 0)) {
        lineDataMap.set(time, trade.price);
      }
    });

    // Convert map to sorted array
    const lineData = Array.from(lineDataMap.entries())
      .map(([time, value]) => ({
        time: time as Time,
        value,
      }))
      .sort((a, b) => (a.time as number) - (b.time as number));

    seriesRef.current.setData(lineData);

    // Auto-fit content
    if (chartRef.current) {
      chartRef.current.timeScale().fitContent();
    }
  }, [trades]);

  const toggleFullscreen = () => {
    if (!chartContainerRef.current) return;

    if (!document.fullscreenElement) {
      chartContainerRef.current.requestFullscreen();
    } else {
      document.exitFullscreen();
    }
  };

  return (
    <Card className="flex flex-col">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <LineChart className="h-4 w-4 text-[hsl(var(--color-primary))]" />
            <CardTitle>{symbol} Price Chart</CardTitle>
          </div>

          <div className="flex items-center gap-2">
            {/* Interval Selector */}
            {onIntervalChange && (
              <div className="flex items-center gap-1 rounded-lg border border-[hsl(var(--color-border))] p-1">
                {TIME_INTERVALS.map((int) => (
                  <Button
                    key={int.value}
                    size="sm"
                    variant={interval === int.value ? 'default' : 'ghost'}
                    onClick={() => onIntervalChange(int.value)}
                    className="h-6 px-2 text-xs"
                  >
                    {int.label}
                  </Button>
                ))}
              </div>
            )}

            <Button size="icon" variant="ghost" onClick={toggleFullscreen}>
              <Maximize2 className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </CardHeader>

      <CardContent>
        {trades.length === 0 ? (
          <div className="flex h-[400px] items-center justify-center rounded-lg border border-dashed border-[hsl(var(--color-border))]">
            <p className="text-sm text-[hsl(var(--color-muted-foreground))]">
              Waiting for price data...
            </p>
          </div>
        ) : (
          <div ref={chartContainerRef} className="rounded-lg" />
        )}
      </CardContent>
    </Card>
  );
}