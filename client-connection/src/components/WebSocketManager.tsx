import { useEffect } from 'react';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useTradeStore, useAlertStore, useConnectionStore } from '@/stores';
import { config } from '@/config';
import type { ProcessedTradeEvent, AlertEvent } from '@/types';

/**
 * WebSocket message wrapper from Service 3
 */
interface WebSocketMessage<T> {
  type: string;
  payload: T;
}

/**
 * WebSocketManager - Manages WebSocket connections for trades and alerts
 * This component doesn't render anything, it just manages connections
 */
export function WebSocketManager() {
  const selectedSymbols = useConnectionStore((state) => state.selectedSymbols);
  const setTradeWsStatus = useConnectionStore((state) => state.setTradeWsStatus);
  const setAlertWsStatus = useConnectionStore((state) => state.setAlertWsStatus);
  const addTrade = useTradeStore((state) => state.addTrade);
  const addAlert = useAlertStore((state) => state.addAlert);

  // Trade WebSocket connection
  const tradeWs = useWebSocket<WebSocketMessage<ProcessedTradeEvent>>(
    config.ws.tradesUrl,
    (message) => {
      // Unwrap the payload from the message
      if (message.type === 'trade' && message.payload) {
        console.log('[Trade WebSocket] Received trade:', message.payload.symbol, message.payload.price);
        addTrade(message.payload);
      }
    },
    true // Always enabled
  );

  // Alert WebSocket connection
  const alertWs = useWebSocket<WebSocketMessage<AlertEvent>>(
    config.ws.alertsUrl,
    (message) => {
      // Unwrap the payload from the message
      if (message.type === 'alert' && message.payload) {
        console.log('[Alert WebSocket] Received alert:', message.payload.symbol, message.payload.severity);
        addAlert(message.payload);
      }
    },
    true // Always enabled
  );

  // Update connection status in store
  useEffect(() => {
    setTradeWsStatus(tradeWs.status);
  }, [tradeWs.status, setTradeWsStatus]);

  useEffect(() => {
    setAlertWsStatus(alertWs.status);
  }, [alertWs.status, setAlertWsStatus]);

  // Subscribe to selected symbols when they change or when connected
  useEffect(() => {
    if (tradeWs.isConnected && selectedSymbols.length > 0) {
      console.log('[Trade WebSocket] Subscribing to symbols:', selectedSymbols);
      tradeWs.subscribe(selectedSymbols);
    }
  }, [tradeWs.isConnected, selectedSymbols, tradeWs.subscribe]);

  useEffect(() => {
    if (alertWs.isConnected && selectedSymbols.length > 0) {
      console.log('[Alert WebSocket] Subscribing to symbols:', selectedSymbols);
      alertWs.subscribe(selectedSymbols);
    }
  }, [alertWs.isConnected, selectedSymbols, alertWs.subscribe]);

  // Log connection errors
  useEffect(() => {
    if (tradeWs.error) {
      console.error('[Trade WebSocket] Error:', tradeWs.error);
    }
  }, [tradeWs.error]);

  useEffect(() => {
    if (alertWs.error) {
      console.error('[Alert WebSocket] Error:', alertWs.error);
    }
  }, [alertWs.error]);

  // This component doesn't render anything
  return null;
}