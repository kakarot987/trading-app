# Market Data Producer Service

## Overview
Microservice that connects to stock/crypto exchanges and streams live trade data to Kafka.

## Features
- **Binance WebSocket**: Real-time crypto trades (BTC, ETH, BNB)
- **Yahoo Finance REST**: Stock quotes (AAPL, GOOGL, MSFT, TSLA)
- **Kafka Integration**: Publishes normalized TradeEvent to `trade-events` topic
- **Docker Support**: Fully containerized with docker-compose

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development)
- Gradle 8+ (for local development)

### Run with Docker Compose
```bash
# Start all services (Zookeeper, Kafka, Producer, Kafka UI)
docker-compose up -d

# View logs
docker-compose logs -f market-data-producer

# Stop services
docker-compose down
```

### Local Development
```bash
# Start Kafka infrastructure
docker-compose up -d zookeeper kafka

# Run application locally
./gradlew bootRun

# Build JAR
./gradlew build
```

## Architecture

### TradeEvent Schema
```json
{
  "symbol": "BTCUSDT",
  "exchange": "BINANCE",
  "price": 43250.50,
  "volume": 0.125,
  "timestamp": 1703001234567,
  "side": "BUY",
  "trade_id": "12345",
  "processed_at": 1703001234890
}
```

### Components
- **BinanceWebSocketClient**: Maintains persistent WebSocket connections
- **YahooFinanceRestClient**: Polls REST API every 60 seconds
- **KafkaPublisher**: Publishes normalized events to Kafka
- **TradeEvent**: Canonical data model

## Monitoring
- **Kafka UI**: http://localhost:8090
- **Application**: http://localhost:8080

## Configuration
Edit `application.yml` or set environment variables:
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address
- `SPRING_PROFILES_ACTIVE`: Active profile (docker/local)

## Extending
Add new exchanges by implementing similar client classes:
1. Create client class (WebSocket or REST)
2. Normalize to TradeEvent schema
3. Publish via KafkaPublisher