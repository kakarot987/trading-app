import { create } from 'zustand';
import type { AlertEvent } from '@/types';

/**
 * Alert Store - manages all incoming alerts
 * Will be fully implemented in Part 3
 */

interface AlertStore {
  alerts: AlertEvent[];
  
  addAlert: (alert: AlertEvent) => void;
  removeAlert: (alertId: string) => void;
  clearAlerts: () => void;
  getAlertsBySymbol: (symbol: string) => AlertEvent[];
}

export const useAlertStore = create<AlertStore>((set, get) => ({
  alerts: [],

  addAlert: (alert) => {
    // Placeholder - full implementation in Part 3
    set((state) => ({
      alerts: [alert, ...state.alerts].slice(0, 50), // Keep last 50 alerts
    }));
  },

  removeAlert: (alertId) => {
    set((state) => ({
      alerts: state.alerts.filter((a) => a.alertId !== alertId),
    }));
  },

  clearAlerts: () => {
    set({ alerts: [] });
  },

  getAlertsBySymbol: (symbol) => {
    return get().alerts.filter((a) => a.symbol === symbol);
  },
}));