import { useState, useEffect, useRef } from 'react';
import { Bell, Filter } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent, Button, Badge } from '@/components/ui';
import { AlertCard } from './AlertCard';
import { ALERT_SEVERITY_CONFIG } from '@/config';
import type { AlertEvent, AlertSeverity } from '@/types';

interface AlertFeedProps {
  alerts: AlertEvent[];
  maxAlerts?: number;
  selectedSymbol?: string | null;
}

export function AlertFeed({ alerts, maxAlerts = 30, selectedSymbol = null }: AlertFeedProps) {
  const [selectedSeverity, setSelectedSeverity] = useState<AlertSeverity | null>(null);
  const [newAlertIds, setNewAlertIds] = useState<Set<string>>(new Set());
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const prevAlertsLengthRef = useRef(alerts.length);

  // Filter alerts
  const filteredAlerts = alerts
    .filter((alert) => {
      if (selectedSymbol && alert.symbol !== selectedSymbol) return false;
      if (selectedSeverity && alert.severity !== selectedSeverity) return false;
      return true;
    })
    .slice(0, maxAlerts);

  // Track new alerts for animation
  useEffect(() => {
    if (alerts.length > prevAlertsLengthRef.current) {
      const newAlerts = alerts.slice(0, alerts.length - prevAlertsLengthRef.current);
      const newIds = new Set(newAlerts.map((a) => a.alertId));
      setNewAlertIds(newIds);

      // Remove highlight after animation
      const timer = setTimeout(() => setNewAlertIds(new Set()), 1500);
      return () => clearTimeout(timer);
    }
    prevAlertsLengthRef.current = alerts.length;
  }, [alerts]);

  // Auto-scroll to top on new alert
  useEffect(() => {
    if (scrollContainerRef.current && alerts.length > prevAlertsLengthRef.current) {
      scrollContainerRef.current.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [alerts.length]);

  // Count by severity
  const severityCounts = {
    LOW: filteredAlerts.filter((a) => a.severity === 'LOW').length,
    MEDIUM: filteredAlerts.filter((a) => a.severity === 'MEDIUM').length,
    HIGH: filteredAlerts.filter((a) => a.severity === 'HIGH').length,
    CRITICAL: filteredAlerts.filter((a) => a.severity === 'CRITICAL').length,
  };

  return (
    <Card className="flex h-full flex-col">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Bell className="h-4 w-4 text-[hsl(var(--primary))]" />
            <CardTitle>Alerts</CardTitle>
            <Badge variant="outline">{filteredAlerts.length}</Badge>
          </div>

          {/* Severity Filters */}
          <div className="flex items-center gap-1">
            {(Object.keys(ALERT_SEVERITY_CONFIG) as AlertSeverity[]).map((severity) => {
              const config = ALERT_SEVERITY_CONFIG[severity];
              const count = severityCounts[severity];
              const isActive = selectedSeverity === severity;

              return (
                <Button
                  key={severity}
                  size="sm"
                  variant={isActive ? 'default' : 'ghost'}
                  onClick={() =>
                    setSelectedSeverity(isActive ? null : severity)
                  }
                  className="h-7 gap-1.5 px-2"
                >
                  <span className={`h-2 w-2 rounded-full ${config.bg}`} />
                  <span className="text-xs">{severity[0]}</span>
                  <span className="text-xs text-[hsl(var(--muted-foreground))]">
                    ({count})
                  </span>
                </Button>
              );
            })}
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex-1 overflow-hidden">
        {filteredAlerts.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center space-y-2 text-center">
            <Filter className="h-8 w-8 text-[hsl(var(--muted-foreground))]" />
            <p className="text-sm text-[hsl(var(--muted-foreground))]">
              {alerts.length === 0
                ? 'No alerts yet'
                : 'No alerts match current filters'}
            </p>
          </div>
        ) : (
          <div
            ref={scrollContainerRef}
            className="h-full space-y-3 overflow-y-auto pr-2"
          >
            {filteredAlerts.map((alert) => (
              <AlertCard
                key={alert.alertId}
                alert={alert}
                isNew={newAlertIds.has(alert.alertId)}
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}