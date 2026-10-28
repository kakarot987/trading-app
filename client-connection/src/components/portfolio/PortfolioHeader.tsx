// src/components/portfolio/PortfolioHeader.tsx
import { useState, useMemo } from 'react';
import { Card } from '@/components/ui/Card';
import { useTradeStore } from '@/stores';
import { formatPrice } from '@/lib/utils';
import { 
  TrendingUp, 
  TrendingDown, 
  Wallet, 
  Eye, 
  EyeOff,
  DollarSign 
} from 'lucide-react';

interface PortfolioHeaderProps {
  initialBalance?: number;
}

export function PortfolioHeader({ initialBalance = 100000 }: PortfolioHeaderProps) {
  const [showBalance, setShowBalance] = useState(true);
  const trades = useTradeStore((state) => state.trades);

  // Calculate portfolio metrics
  const metrics = useMemo(() => {
    // Mock calculations - replace with your actual portfolio logic
    const totalPnL = trades.reduce((sum, trade) => {
      // Simplified P&L calculation
      return sum + (trade.side === 'BUY' ? -trade.price * trade.volume : trade.price * trade.volume);
    }, 0);

    const currentBalance = initialBalance + totalPnL;
    const totalReturn = ((currentBalance - initialBalance) / initialBalance) * 100;
    const todayPnL = trades
      .filter((t) => t.timestamp > Date.now() - 24 * 60 * 60 * 1000)
      .reduce((sum, trade) => sum + (trade.side === 'BUY' ? -trade.price * trade.volume : trade.price * trade.volume), 0);
    
    const todayReturn = ((todayPnL / initialBalance) * 100);

    return {
      balance: currentBalance,
      totalReturn,
      todayPnL,
      todayReturn,
      initialBalance,
    };
  }, [trades, initialBalance]);

  const isPositiveTotal = metrics.totalReturn >= 0;
  const isPositiveToday = metrics.todayPnL >= 0;

  return (
    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
      {/* Total Balance */}
      <Card className="p-4 bg-gradient-to-br from-blue-500/10 to-purple-500/10 border-blue-500/30">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <Wallet className="w-4 h-4 text-blue-400" />
            <span className="text-sm text-slate-400">Total Balance</span>
          </div>
          <button
            onClick={() => setShowBalance(!showBalance)}
            className="text-slate-400 hover:text-white transition-colors"
          >
            {showBalance ? <Eye className="w-4 h-4" /> : <EyeOff className="w-4 h-4" />}
          </button>
        </div>
        <div className="text-3xl font-bold text-white mb-1">
          {showBalance ? formatPrice(metrics.balance) : '••••••'}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-400">
            Initial: {formatPrice(metrics.initialBalance)}
          </span>
        </div>
      </Card>

      {/* Total Return */}
      <Card className="p-4">
        <div className="flex items-center gap-2 mb-2">
          <DollarSign className="w-4 h-4 text-slate-400" />
          <span className="text-sm text-slate-400">Total Return</span>
        </div>
        <div className={`text-3xl font-bold mb-1 ${isPositiveTotal ? 'text-green-500' : 'text-red-500'}`}>
          {isPositiveTotal ? '+' : ''}{metrics.totalReturn.toFixed(2)}%
        </div>
        <div className="flex items-center gap-1 text-sm">
          {isPositiveTotal ? (
            <TrendingUp className="w-4 h-4 text-green-500" />
          ) : (
            <TrendingDown className="w-4 h-4 text-red-500" />
          )}
          <span className={isPositiveTotal ? 'text-green-500' : 'text-red-500'}>
            {formatPrice(metrics.balance - metrics.initialBalance)}
          </span>
        </div>
      </Card>

      {/* Today's P&L */}
      <Card className="p-4">
        <div className="flex items-center gap-2 mb-2">
          <TrendingUp className="w-4 h-4 text-slate-400" />
          <span className="text-sm text-slate-400">Today's P&L</span>
        </div>
        <div className={`text-3xl font-bold mb-1 ${isPositiveToday ? 'text-green-500' : 'text-red-500'}`}>
          {isPositiveToday ? '+' : ''}{formatPrice(metrics.todayPnL)}
        </div>
        <div className="flex items-center gap-1 text-sm">
          <span className={isPositiveToday ? 'text-green-500' : 'text-red-500'}>
            {isPositiveToday ? '+' : ''}{metrics.todayReturn.toFixed(2)}%
          </span>
        </div>
      </Card>

      {/* Quick Stats */}
      <Card className="p-4">
        <div className="flex items-center gap-2 mb-2">
          <Activity className="w-4 h-4 text-slate-400" />
          <span className="text-sm text-slate-400">Trading Activity</span>
        </div>
        <div className="text-3xl font-bold text-white mb-1">
          {trades.length}
        </div>
        <div className="text-xs text-slate-400">
          Total trades executed
        </div>
      </Card>
    </div>
  );
}

const Activity = TrendingUp;