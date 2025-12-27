import { Activity, Wifi, WifiOff, AlertCircle } from 'lucide-react';
import { Badge } from '@/components/ui';
import { cn } from '@/lib/utils';
import type { WebSocketStatus } from '@/types';

interface HeaderProps {
  tradeWsStatus: WebSocketStatus;
  alertWsStatus: WebSocketStatus;
}

function ConnectionIndicator({ status, label }: { status: WebSocketStatus; label: string }) {
  const statusConfig = {
    connected: { icon: Wifi, color: 'text-green-400', bg: 'bg-green-400' },
    connecting: { icon: Wifi, color: 'text-yellow-400', bg: 'bg-yellow-400' },
    reconnecting: { icon: Wifi, color: 'text-yellow-400', bg: 'bg-yellow-400' },
    disconnected: { icon: WifiOff, color: 'text-red-400', bg: 'bg-red-400' },
    error: { icon: AlertCircle, color: 'text-red-400', bg: 'bg-red-400' },
  };

  const config = statusConfig[status];
  const Icon = config.icon;

  return (
    <div className="flex items-center gap-2">
      <div className="relative">
        <Icon className={cn('h-4 w-4', config.color)} />
        {status === 'connected' && (
          <span className={cn('absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full', config.bg, 'animate-pulse')} />
        )}
      </div>
      <span className="text-xs text-[hsl(var(--muted-foreground))]">{label}</span>
    </div>
  );
}

export function Header({ tradeWsStatus, alertWsStatus }: HeaderProps) {
  return (
    <header className="sticky top-0 z-50 flex h-14 items-center justify-between border-b border-[hsl(var(--border))] bg-[hsl(var(--background))]/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-[hsl(var(--background))]/60">
      {/* Logo & Title */}
      <div className="flex items-center gap-3">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[hsl(var(--primary))]">
          <Activity className="h-5 w-5 text-white" />
        </div>
        <div>
          <h1 className="text-sm font-semibold">TradeStream</h1>
          <p className="text-xs text-[hsl(var(--muted-foreground))]">Real-time Trading Dashboard</p>
        </div>
      </div>

      {/* Connection Status */}
      <div className="flex items-center gap-6">
        <ConnectionIndicator status={tradeWsStatus} label="Trades" />
        <ConnectionIndicator status={alertWsStatus} label="Alerts" />

        <Badge variant={tradeWsStatus === 'connected' ? 'success' : 'warning'}>
          {tradeWsStatus === 'connected' ? 'Live' : 'Connecting...'}
        </Badge>
      </div>
    </header>
  );
}