import { useEffect, useRef, useState, useCallback } from 'react';
import { config } from '@/config';
import type { WebSocketStatus, SubscriptionMessage } from '@/types';

/**
 * Custom WebSocket hook with reconnection logic
 * Handles connection, reconnection, message parsing, and subscription management
 */
export function useWebSocket<T>(
  url: string,
  onMessage: (data: T) => void,
  enabled = true
) {
  const [status, setStatus] = useState<WebSocketStatus>('disconnected');
  const [error, setError] = useState<string | null>(null);
  const [lastConnected, setLastConnected] = useState<number | null>(null);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);
  
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const heartbeatIntervalRef = useRef<ReturnType<typeof setInterval> | undefined>(undefined);
  const reconnectAttemptsRef = useRef(0);
  const isManualDisconnectRef = useRef(false);
  const isMountedRef = useRef(true);
  const onMessageRef = useRef(onMessage);
  const connectRef = useRef<() => void>(() => {});

  // Update onMessage ref when it changes
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  /**
   * Start heartbeat to keep connection alive
   */
  const startHeartbeat = useCallback(() => {
    if (heartbeatIntervalRef.current) {
      clearInterval(heartbeatIntervalRef.current);
    }
    
    heartbeatIntervalRef.current = setInterval(() => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        try {
          wsRef.current.send(JSON.stringify({ type: 'ping' }));
        } catch (err) {
          console.error('[WebSocket] Heartbeat failed:', err);
        }
      }
    }, config.ws.heartbeatInterval);
  }, []);

  /**
   * Stop heartbeat
   */
  const stopHeartbeat = useCallback(() => {
    if (heartbeatIntervalRef.current) {
      clearInterval(heartbeatIntervalRef.current);
      heartbeatIntervalRef.current = undefined;
    }
  }, []);

  /**
   * Attempt to reconnect with exponential backoff
   */
  const attemptReconnect = useCallback(() => {
    if (!isMountedRef.current) return;
    
    if (reconnectAttemptsRef.current >= config.ws.maxReconnectAttempts) {
      console.error('[WebSocket] Max reconnect attempts reached');
      if (isMountedRef.current) {
        setStatus('error');
        setError('Failed to reconnect after maximum attempts');
      }
      return;
    }

    reconnectAttemptsRef.current += 1;
    setReconnectAttempts(reconnectAttemptsRef.current);
    const backoffDelay = Math.min(
      config.ws.reconnectInterval * Math.pow(1.5, reconnectAttemptsRef.current - 1),
      30000
    );

    console.log(
      `[WebSocket] Reconnecting in ${backoffDelay}ms (attempt ${reconnectAttemptsRef.current}/${config.ws.maxReconnectAttempts})`
    );
    
    if (isMountedRef.current) {
      setStatus('reconnecting');
    }

    reconnectTimeoutRef.current = setTimeout(() => {
      if (isMountedRef.current && enabled) {
        connectRef.current();
      }
    }, backoffDelay);
  }, [enabled]);

  /**
   * Connect to WebSocket server
   */
  const connect = useCallback(() => {
    if (!isMountedRef.current) return;
    
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      console.log(`[WebSocket] Already connected to ${url}`);
      return;
    }

    console.log(`[WebSocket] Connecting to ${url}...`);
    if (isMountedRef.current) {
      setStatus('connecting');
      setError(null);
    }

    try {
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = () => {
        if (!isMountedRef.current) return;
        
        console.log(`[WebSocket] Connected to ${url}`);
        setStatus('connected');
        setLastConnected(Date.now());
        setError(null);
        reconnectAttemptsRef.current = 0;
        setReconnectAttempts(0);
        startHeartbeat();
      };

      ws.onmessage = (event) => {
        if (!isMountedRef.current) return;
        
        try {
          const data = JSON.parse(event.data) as T;
          onMessageRef.current(data);
        } catch (err) {
          console.error('[WebSocket] Failed to parse message:', err);
        }
      };

      ws.onerror = (event) => {
        if (!isMountedRef.current) return;
        
        console.error('[WebSocket] Error:', event);
        setError('WebSocket connection error');
        setStatus('error');
      };

      ws.onclose = (event) => {
        if (!isMountedRef.current) return;
        
        console.log(`[WebSocket] Disconnected from ${url}`, event.code, event.reason);
        stopHeartbeat();
        
        if (!isManualDisconnectRef.current && enabled && isMountedRef.current) {
          attemptReconnect();
        } else if (isMountedRef.current) {
          setStatus('disconnected');
        }
      };
    } catch (err) {
      if (!isMountedRef.current) return;
      
      console.error('[WebSocket] Connection failed:', err);
      setError(err instanceof Error ? err.message : 'Connection failed');
      setStatus('error');
      
      if (enabled && isMountedRef.current) {
        attemptReconnect();
      }
    }
  }, [url, enabled, startHeartbeat, stopHeartbeat, attemptReconnect]);

  useEffect(() => {
    connectRef.current = connect;
  }, [connect]);

  /**
   * Disconnect from WebSocket
   */
  const disconnect = useCallback(() => {
    console.log(`[WebSocket] Manually disconnecting from ${url}`);
    isManualDisconnectRef.current = true;
    
    stopHeartbeat();
    
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
    }
    
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    
    if (isMountedRef.current) {
      setStatus('disconnected');
    }
  }, [url, stopHeartbeat]);

  /**
   * Subscribe to specific symbols
   */
  const subscribe = useCallback((symbols: string[]) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      const message: SubscriptionMessage = { symbols };
      console.log('[WebSocket] Subscribing to:', symbols);
      
      try {
        wsRef.current.send(JSON.stringify(message));
      } catch (err) {
        console.error('[WebSocket] Failed to send subscription:', err);
      }
    } else {
      console.warn('[WebSocket] Cannot subscribe - connection not open');
    }
  }, []);

  /**
   * Manual reconnect trigger
   */
  const reconnect = useCallback(() => {
    console.log('[WebSocket] Manual reconnect triggered');
    disconnect();
    setTimeout(() => {
      isManualDisconnectRef.current = false;
      reconnectAttemptsRef.current = 0;
      setReconnectAttempts(0);
      if (isMountedRef.current) {
        connect();
      }
    }, 100);
  }, [connect, disconnect]);

  // Connect/disconnect based on enabled flag - ONLY runs when enabled or url changes
  useEffect(() => {
    isMountedRef.current = true;
    
    if (enabled) {
      isManualDisconnectRef.current = false;
      connect();
    }

    return () => {
      isMountedRef.current = false;
      isManualDisconnectRef.current = true;
      
      stopHeartbeat();
      
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [url, enabled, connect, stopHeartbeat]);

  return {
    status,
    error,
    lastConnected,
    reconnectAttempts,
    subscribe,
    reconnect,
    isConnected: status === 'connected',
  };
}