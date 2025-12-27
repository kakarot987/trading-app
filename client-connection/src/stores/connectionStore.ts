import { create } from 'zustand';
import type { WebSocketStatus } from '@/types';

/**
 * Connection Store - manages WebSocket connection states and subscriptions
 */

interface ConnectionState {
  tradeWsStatus: WebSocketStatus;
  alertWsStatus: WebSocketStatus;
  tradeWsError: string | null;
  alertWsError: string | null;
  lastTradeConnected: number | null;
  lastAlertConnected: number | null;
}

interface ConnectionStore extends ConnectionState {
  selectedSymbols: string[];
  
  // WebSocket status setters
  setTradeWsStatus: (status: WebSocketStatus) => void;
  setAlertWsStatus: (status: WebSocketStatus) => void;
  setTradeWsError: (error: string | null) => void;
  setAlertWsError: (error: string | null) => void;
  setLastTradeConnected: (timestamp: number | null) => void;
  setLastAlertConnected: (timestamp: number | null) => void;
  
  // Symbol management
  addSymbol: (symbol: string) => void;
  removeSymbol: (symbol: string) => void;
  setSymbols: (symbols: string[]) => void;
  
  // Helpers
  isFullyConnected: () => boolean;
  hasAnyConnection: () => boolean;
}

export const useConnectionStore = create<ConnectionStore>((set, get) => ({
  // Initial state
  tradeWsStatus: 'disconnected',
  alertWsStatus: 'disconnected',
  tradeWsError: null,
  alertWsError: null,
  lastTradeConnected: null,
  lastAlertConnected: null,
  selectedSymbols: ['BTCUSD', 'ETHUSD', 'AAPL', 'GOOGL', 'MSFT'], // Default symbols
  
  // Status setters
  setTradeWsStatus: (status) => {
    set({ tradeWsStatus: status });
    if (status === 'connected') {
      set({ lastTradeConnected: Date.now(), tradeWsError: null });
    }
  },
  
  setAlertWsStatus: (status) => {
    set({ alertWsStatus: status });
    if (status === 'connected') {
      set({ lastAlertConnected: Date.now(), alertWsError: null });
    }
  },
  
  setTradeWsError: (error) => set({ tradeWsError: error }),
  setAlertWsError: (error) => set({ alertWsError: error }),
  setLastTradeConnected: (timestamp) => set({ lastTradeConnected: timestamp }),
  setLastAlertConnected: (timestamp) => set({ lastAlertConnected: timestamp }),
  
  // Symbol management
  addSymbol: (symbol) =>
    set((state) => ({
      selectedSymbols: [...new Set([...state.selectedSymbols, symbol])],
    })),
  
  removeSymbol: (symbol) =>
    set((state) => ({
      selectedSymbols: state.selectedSymbols.filter((s) => s !== symbol),
    })),
  
  setSymbols: (symbols) => set({ selectedSymbols: symbols }),
  
  // Helper functions
  isFullyConnected: () => {
    const state = get();
    return (
      state.tradeWsStatus === 'connected' && 
      state.alertWsStatus === 'connected'
    );
  },
  
  hasAnyConnection: () => {
    const state = get();
    return (
      state.tradeWsStatus === 'connected' || 
      state.alertWsStatus === 'connected'
    );
  },
}));