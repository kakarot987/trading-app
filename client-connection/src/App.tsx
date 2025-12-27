import { useState, useEffect } from 'react';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardHeader, CardTitle, CardContent, Button } from '@/components/ui';
import { PortfolioHeader } from '@/components/portfolio/PortfolioHeader';
import { MarketOverview } from '@/components/market/MarketOverview';
import { SymbolDetailModal } from '@/components/market/SymbolDetailModal';
import { TradingSignals } from '@/components/signals/TradingSignals';
import { NewsSection } from '@/components/news/NewsSection';
import { TradeFeed } from '@/components/trade/TradeFeed';
import { AlertFeed } from '@/components/alerts/AlertFeed';
import { SymbolSelector } from '@/components/symbols/SymbolSelector';
import { WebSocketManager } from '@/components/WebSocketManager';
import { HistoricalDashboard } from '@/components/statistics/HistoricalDashboard';
import { useTradeStore, useAlertStore, useConnectionStore } from '@/stores';
import { startMockTradeGenerator, startMockAlertGenerator } from '@/lib/mockDataGenerator';
import { RotateCw } from 'lucide-react';

// Toggle between mock data and real WebSocket
const USE_MOCK_DATA = false; // Set to false to use real backend

// Your actual trading symbols
const TRADING_SYMBOLS = [
  'BTCUSDT',
  'BNBUSDT',
  'AAPL',
  'GOOGL',
  'MSFT',
  'TSLA',
  'NFLX',
  'USOIL',
  'XAUUSD',
  'BTCUSD',
];

// Main Dashboard View - Professional trading layout with compact market overview
function MainDashboardView() {
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const alerts = useAlertStore((state) => state.alerts);
  const selectedSymbols = useConnectionStore((state) => state.selectedSymbols);

  // Auto-select all trading symbols on mount
  const { setSelectedSymbols } = useConnectionStore();
  useEffect(() => {
    if (selectedSymbols.length === 0) {
      setSelectedSymbols(TRADING_SYMBOLS);
    }
  }, []);

  return (
    <>
      {/* Portfolio Header with Balance */}
      <PortfolioHeader initialBalance={100000} />

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Left Column: Market Overview (Compact) + News */}
        <div className="space-y-4">
          {/* Compact Market Watchlist */}
          <MarketOverview
            symbols={selectedSymbols}
            onSymbolClick={(symbol) => setSelectedSymbol(symbol)}
            compact={true}
          />

          {/* Market News */}
          <NewsSection maxItems={3} />
        </div>

        {/* Middle Column: Trading Signals */}
        <div className="space-y-4">
          <TradingSignals maxSignals={8} />
        </div>

        {/* Right Column: Alerts */}
        <div className="space-y-4">
          <AlertFeed alerts={alerts} maxAlerts={15} />
        </div>
      </div>

      {/* Symbol Detail Modal */}
      {selectedSymbol && (
        <SymbolDetailModal
          symbol={selectedSymbol}
          onClose={() => setSelectedSymbol(null)}
        />
      )}
    </>
  );
}

// Historical Analysis View
function HistoricalView() {
  return <HistoricalDashboard />;
}

// All Trades View
function TradesView() {
  const trades = useTradeStore((state) => state.trades);
  return (
    <div className="h-[calc(100vh-7rem)]">
      <TradeFeed trades={trades} maxTrades={200} />
    </div>
  );
}

// All Alerts View
function AlertsView() {
  const alerts = useAlertStore((state) => state.alerts);
  return (
    <div className="h-[calc(100vh-7rem)]">
      <AlertFeed alerts={alerts} maxAlerts={100} />
    </div>
  );
}

// Settings View
function SettingsView() {
  const { selectedSymbols, addSymbol, removeSymbol, setSelectedSymbols } =
    useConnectionStore();
  const clearTrades = useTradeStore((state) => state.clearTrades);
  const clearAlerts = useAlertStore((state) => state.clearAlerts);

  const handleSymbolToggle = (symbol: string) => {
    if (selectedSymbols.includes(symbol)) {
      removeSymbol(symbol);
    } else {
      addSymbol(symbol);
    }
  };

  const handleSelectAll = () => {
    setSelectedSymbols(TRADING_SYMBOLS);
  };

  const handleClearAll = () => {
    if (confirm('Clear all trades and alerts?')) {
      clearTrades();
      clearAlerts();
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Symbol Configuration</CardTitle>
            <Button onClick={handleSelectAll} variant="outline" size="sm">
              Select All ({TRADING_SYMBOLS.length})
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <SymbolSelector
            selectedSymbols={selectedSymbols}
            onSymbolToggle={handleSymbolToggle}
            onSymbolRemove={removeSymbol}
            maxSymbols={10}
          />

          <div className="mt-4 p-3 bg-slate-800 rounded-lg text-xs">
            <div className="font-medium text-slate-300 mb-1">
              📊 Trading Symbols
            </div>
            <div className="text-slate-400">
              {selectedSymbols.length} of {TRADING_SYMBOLS.length} symbols
              selected for live tracking
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Data Management</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <Button
            variant="outline"
            onClick={handleClearAll}
            className="w-full"
          >
            <RotateCw className="mr-2 h-4 w-4" />
            Clear All Data
          </Button>

          <div className="rounded-lg bg-[hsl(var(--muted))] p-3 text-xs">
            <p className="font-medium">Connection Mode:</p>
            <p className="text-[hsl(var(--muted-foreground))]">
              {USE_MOCK_DATA
                ? '🎭 Mock Data (for testing)'
                : '🔌 Real WebSocket'}
            </p>
            <p className="mt-2 text-[hsl(var(--muted-foreground))]">
              To toggle, change USE_MOCK_DATA in App.tsx
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

export default function App() {
  const [activeView, setActiveView] = useState('dashboard');

  // Get WebSocket status from store
  const tradeWsStatus = useConnectionStore((state) => state.tradeWsStatus);
  const alertWsStatus = useConnectionStore((state) => state.alertWsStatus);
  const selectedSymbols = useConnectionStore((state) => state.selectedSymbols);
  const setTradeWsStatus = useConnectionStore(
    (state) => state.setTradeWsStatus
  );
  const setAlertWsStatus = useConnectionStore(
    (state) => state.setAlertWsStatus
  );

  // Get store actions
  const addTrade = useTradeStore((state) => state.addTrade);
  const addAlert = useAlertStore((state) => state.addAlert);

  // Mock data generators (only when USE_MOCK_DATA is true)
  useEffect(() => {
    if (!USE_MOCK_DATA) return;

    console.log('🎭 Starting mock data generators (for testing)...');

    // Simulate connection
    setTradeWsStatus('connected');
    setAlertWsStatus('connected');

    // Start generating trades every 1 second
    const stopTradeGenerator = startMockTradeGenerator(
      (trade) => {
        addTrade(trade);
      },
      selectedSymbols,
      1000
    );

    // Start generating alerts every 5 seconds
    const stopAlertGenerator = startMockAlertGenerator(
      (alert) => {
        addAlert(alert);
      },
      selectedSymbols,
      5000
    );

    // Cleanup on unmount
    return () => {
      console.log('🛑 Stopping mock data generators...');
      stopTradeGenerator();
      stopAlertGenerator();
      setTradeWsStatus('disconnected');
      setAlertWsStatus('disconnected');
    };
  }, [
    USE_MOCK_DATA,
    selectedSymbols,
    addTrade,
    addAlert,
    setTradeWsStatus,
    setAlertWsStatus,
  ]);

  const renderView = () => {
    switch (activeView) {
      case 'dashboard':
        return <MainDashboardView />;
      case 'historical':
        return <HistoricalView />;
      case 'trades':
        return <TradesView />;
      case 'alerts':
        return <AlertsView />;
      case 'settings':
        return <SettingsView />;
      default:
        return <MainDashboardView />;
    }
  };

  return (
    <>
      {/* WebSocket Manager - only active when not using mock data */}
      {!USE_MOCK_DATA && <WebSocketManager />}

      <MainLayout
        tradeWsStatus={tradeWsStatus}
        alertWsStatus={alertWsStatus}
        activeView={activeView}
        onViewChange={setActiveView}
      >
        {renderView()}
      </MainLayout>
    </>
  );
}