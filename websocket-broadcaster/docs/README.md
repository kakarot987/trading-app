
trades-client.html

`<script>
  const ws = new WebSocket("ws://localhost:8082/ws/trades");
  ws.onopen = () => ws.send(JSON.stringify({ symbols: ["AAPL","TSLA"] }));
  ws.onmessage = (ev) => console.log("TRADE:", ev.data);
</script>
`

 alerts-client.html
`<script>
  const ws = new WebSocket("ws://localhost:8082/ws/alerts");
  ws.onopen = () => ws.send(JSON.stringify({ symbols: ["AAPL"] }));
  ws.onmessage = (ev) => console.log("ALERT:", ev.data);
</script>`