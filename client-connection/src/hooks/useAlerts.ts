import { useMemo } from 'react';
import { useAlertStore } from '@/stores';
import type { AlertSeverity } from '@/types';

/**
 * Custom hooks for alert management with memoization
 */

/**
 * Get all alerts with computed values
 */
export function useAlerts() {
  const alerts = useAlertStore((state) => state.alerts);
  const unreadCount = useAlertStore((state) => state.unreadCount);
  const clearAlerts = useAlertStore((state) => state.clearAlerts);
  const removeAlert = useAlertStore((state) => state.removeAlert);
  const markAllAsRead = useAlertStore((state) => state.markAllAsRead);

  return {
    alerts,
    unreadCount,
    clearAlerts,
    removeAlert,
    markAllAsRead,
    hasUnread: unreadCount > 0,
  };
}

/**
 * Get alerts filtered by symbol
 */
export function useAlertsBySymbol(symbol: string) {
  const getAlertsBySymbol = useAlertStore((state) => state.getAlertsBySymbol);
  
  const alerts = useMemo(() => {
    return getAlertsBySymbol(symbol);
  }, [symbol, getAlertsBySymbol]);

  return {
    alerts,
    count: alerts.length,
  };
}

/**
 * Get alerts filtered by severity
 */
export function useAlertsBySeverity(severity: AlertSeverity) {
  const getAlertsBySeverity = useAlertStore((state) => state.getAlertsBySeverity);
  
  const alerts = useMemo(() => {
    return getAlertsBySeverity(severity);
  }, [severity, getAlertsBySeverity]);

  return {
    alerts,
    count: alerts.length,
  };
}

/**
 * Get alert statistics
 */
export function useAlertStats() {
  const getAlertCountBySeverity = useAlertStore((state) => state.getAlertCountBySeverity);
  const getCriticalAlerts = useAlertStore((state) => state.getCriticalAlerts);

  const stats = useMemo(() => {
    const counts = getAlertCountBySeverity();
    const critical = getCriticalAlerts();
    const total = counts.LOW + counts.MEDIUM + counts.HIGH + counts.CRITICAL;

    return {
      total,
      counts,
      critical: critical.length,
      hasCritical: critical.length > 0,
    };
  }, [getAlertCountBySeverity, getCriticalAlerts]);

  return stats;
}

/**
 * Hook for managing muted symbols
 */
export function useMutedSymbols() {
  const mutedSymbols = useAlertStore((state) => state.mutedSymbols);
  const muteSymbol = useAlertStore((state) => state.muteSymbol);
  const unmuteSymbol = useAlertStore((state) => state.unmuteSymbol);
  const isSymbolMuted = useAlertStore((state) => state.isSymbolMuted);

  return {
    mutedSymbols: Array.from(mutedSymbols),
    muteSymbol,
    unmuteSymbol,
    isSymbolMuted,
    hasMutedSymbols: mutedSymbols.size > 0,
  };
}