import { create } from 'zustand';
import type { AlertEvent, AlertSeverity } from '@/types';

interface AlertStore {
  alerts: AlertEvent[];
  unreadCount: number;
  readAlertIds: Set<string>;
  mutedSymbols: Set<string>;
  addAlert: (alert: AlertEvent) => void;
  removeAlert: (alertId: string) => void;
  clearAlerts: () => void;
  markAllAsRead: () => void;
  getAlertsBySymbol: (symbol: string) => AlertEvent[];
  getAlertsBySeverity: (severity: AlertSeverity) => AlertEvent[];
  getAlertCountBySeverity: () => Record<AlertSeverity, number>;
  getCriticalAlerts: () => AlertEvent[];
  muteSymbol: (symbol: string) => void;
  unmuteSymbol: (symbol: string) => void;
  isSymbolMuted: (symbol: string) => boolean;
}

export const useAlertStore = create<AlertStore>((set, get) => ({
  alerts: [],
  unreadCount: 0,
  readAlertIds: new Set<string>(),
  mutedSymbols: new Set<string>(),

  addAlert: (alert) => set((state) => {
    if (state.mutedSymbols.has(alert.symbol)) return state;
    const alerts = [alert, ...state.alerts.filter((item) => item.alertId !== alert.alertId)].slice(0, 50);
    const readAlertIds = new Set([...state.readAlertIds].filter((id) => alerts.some((item) => item.alertId === id)));
    readAlertIds.delete(alert.alertId);
    return { alerts, readAlertIds, unreadCount: alerts.filter((item) => !readAlertIds.has(item.alertId)).length };
  }),

  removeAlert: (alertId) => set((state) => {
    const alerts = state.alerts.filter((alert) => alert.alertId !== alertId);
    const readAlertIds = new Set(state.readAlertIds);
    readAlertIds.delete(alertId);
    return { alerts, readAlertIds, unreadCount: alerts.filter((alert) => !readAlertIds.has(alert.alertId)).length };
  }),

  clearAlerts: () => set({ alerts: [], unreadCount: 0, readAlertIds: new Set<string>() }),

  markAllAsRead: () => set((state) => ({
    unreadCount: 0,
    readAlertIds: new Set(state.alerts.map((alert) => alert.alertId)),
  })),

  getAlertsBySymbol: (symbol) => get().alerts.filter((alert) => alert.symbol === symbol),
  getAlertsBySeverity: (severity) => get().alerts.filter((alert) => alert.severity === severity),
  getAlertCountBySeverity: () => {
    const counts: Record<AlertSeverity, number> = { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 };
    get().alerts.forEach((alert) => { counts[alert.severity] += 1; });
    return counts;
  },
  getCriticalAlerts: () => get().alerts.filter((alert) => alert.severity === 'CRITICAL'),

  muteSymbol: (symbol) => set((state) => ({
    mutedSymbols: new Set(state.mutedSymbols).add(symbol),
  })),
  unmuteSymbol: (symbol) => set((state) => {
    const mutedSymbols = new Set(state.mutedSymbols);
    mutedSymbols.delete(symbol);
    return { mutedSymbols };
  }),
  isSymbolMuted: (symbol) => get().mutedSymbols.has(symbol),
}));
