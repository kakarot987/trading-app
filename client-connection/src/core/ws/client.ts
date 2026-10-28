type SubscriptionPayload = {
type: "subscribe";
channel: "trades" | "alerts";
symbols: string[];
};

export class WSClient {
private ws: WebSocket | null = null;
private url: string;
private reconnectAttempts = 0;

constructor(url: string) {
    this.url = url;
  }

  connect(onMessage: (data: { type?: string; payload?: { timestamp: number; symbol: string; price: number; volume: number; isBuy: boolean } }) => void) {
    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log("Connected:", this.url);
      this.reconnectAttempts = 0;
    };

    this.ws.onmessage = (ev) => {
      try {
        const parsed = JSON.parse(ev.data);
        onMessage(parsed);
      } catch {
        console.warn("Invalid WS message", ev.data);
      }
    };

    this.ws.onclose = () => {
      this.reconnect();
    };
  }

  subscribe(payload: SubscriptionPayload) {
    this.ws?.send(JSON.stringify(payload));
  }

  private reconnect() {
    const timeout = Math.min(1000 * 2 ** this.reconnectAttempts, 30000);
    setTimeout(() => {
      this.reconnectAttempts++;
      this.connect(() => {});
    }, timeout);
  }
}
