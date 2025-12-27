import { AlertCircle, TrendingUp, Activity, AlertTriangle } from 'lucide-react';
import { formatDateTime, formatPrice, cn } from '@/lib/utils';
import { ALERT_SEVERITY_CONFIG } from '@/config';
import { Badge } from '@/components/ui';
import type { AlertEvent, AlertType } from '@/types';

interface AlertCardProps {
  alert: AlertEvent;
  isNew?: boolean;
}

const alertTypeIcons: Record<AlertType, typeof AlertCircle> = {
  PRICE_SPIKE: TrendingUp,
  VOLUME_ANOMALY: Activity,
  PATTERN_DETECTED: AlertTriangle,
  THRESHOLD_BREACH: AlertCircle,
};

export function AlertCard({ alert, isNew = false }: AlertCardProps) {
  const severityConfig = ALERT_SEVERITY_CONFIG[alert.severity];
  const Icon = alertTypeIcons[alert.type as AlertType] || AlertCircle;

  return (
    <div
      className={cn(
        'group rounded-lg border p-4 transition-all',
        severityConfig.bg,
        severityConfig.border,
        isNew && 'animate-pulse ring-2 ring-[hsl(var(--primary))]'
      )}
    >
      <div className="flex items-start gap-3">
        {/* Icon */}
        <div
          className={cn(
            'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
            severityConfig.bg,
            severityConfig.color
          )}
        >
          <Icon className="h-5 w-5" />
        </div>

        {/* Content */}
        <div className="flex-1 space-y-2">
          <div className="flex items-start justify-between gap-2">
            <div>
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm font-medium">{alert.symbol}</span>
                <Badge variant="outline" className={cn('text-xs', severityConfig.color)}>
                  {alert.severity}
                </Badge>
              </div>
              <p className={cn('mt-1 text-xs', severityConfig.color)}>
                {alert.type.replace(/_/g, ' ')}
              </p>
            </div>
            <span className="shrink-0 text-xs text-[hsl(var(--muted-foreground))]">
              {formatDateTime(alert.timestamp)}
            </span>
          </div>

          <p className="text-sm text-[hsl(var(--foreground))]">{alert.message}</p>

          {/* Trigger Values */}
          {(alert.triggerValue !== null || alert.threshold !== null) && (
            <div className="flex items-center gap-4 text-xs text-[hsl(var(--muted-foreground))]">
              {alert.triggerValue !== null && (
                <span>
                  Trigger: <span className={severityConfig.color}>${formatPrice(alert.triggerValue)}</span>
                </span>
              )}
              {alert.threshold !== null && (
                <span>
                  Threshold: <span className="text-[hsl(var(--foreground))]">${formatPrice(alert.threshold)}</span>
                </span>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}