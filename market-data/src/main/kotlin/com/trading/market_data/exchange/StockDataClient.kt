package com.trading.market_data.exchange

import com.trading.market_data.kafka.KafkaPublisher
import com.trading.common.model.TradeEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant
import jakarta.annotation.PostConstruct

/**
 * Stock data client using Alpha Vantage + Mock fallback
 * NO Yahoo Finance (they block automated requests)
 */
@Service
class StockDataClient(
    private val kafkaPublisher: KafkaPublisher,
    private val restTemplate: RestTemplate,
    @Value("\${stock.api.alpha-vantage.key:}") private val alphaVantageKey: String,
    @Value("\${stock.api.enabled:true}") private val enabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Limit to 4 symbols to stay under Alpha Vantage rate limit (5 calls/min)
    private val symbols = listOf("AAPL", "GOOGL", "MSFT", "TSLA")

    @PostConstruct
    fun init() {
        logger.info("=" .repeat(60))
        logger.info("📈 StockDataClient initialized!")
        logger.info("🔑 Alpha Vantage key: ${if (alphaVantageKey.isNotEmpty()) "✅ Configured" else "❌ Missing"}")
        logger.info("📊 Tracking: $symbols")
        logger.info("⏰ Polling interval: 60 seconds")
        logger.info("🔄 Status: ${if (enabled) "ENABLED ✅" else "DISABLED ❌"}")
        logger.info("=" .repeat(60))
    }

    @Scheduled(fixedRate = 60000, initialDelay = 5000)
    fun fetchQuotes() {
        if (!enabled) {
            logger.debug("Stock data client disabled")
            return
        }

        logger.info("🔄 Fetching stock quotes...")
        var successCount = 0
        var failCount = 0

        symbols.forEach { symbol ->
            try {
                val quote = if (alphaVantageKey.isNotEmpty()) {
                    tryAlphaVantage(symbol) ?: generateMockData(symbol)
                } else {
                    logger.warn("⚠️ No Alpha Vantage key, using mock data")
                    generateMockData(symbol)
                }

                kafkaPublisher.publishTradeEvent(quote)
                successCount++
                logger.info("✅ $symbol: ${quote.price} (${quote.exchange})")

            } catch (e: Exception) {
                failCount++
                logger.error("❌ $symbol failed: ${e.message}")
            }
        }

        logger.info("📊 Completed: $successCount success, $failCount failed")
    }

    /**
     * Fetch from Alpha Vantage (5 calls/min limit on free tier)
     */
    private fun tryAlphaVantage(symbol: String): TradeEvent? {
        return try {
            val url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=$symbol&apikey=$alphaVantageKey"

            val response = restTemplate.getForObject(url, Map::class.java) as? Map<String, Any>

            // Check for API limit message
            if (response?.containsKey("Note") == true) {
                logger.warn("⚠️ Alpha Vantage rate limit reached for $symbol")
                return null
            }

            val quote = response?.get("Global Quote") as? Map<String, Any>

            val price = (quote?.get("05. price") as? String)?.toDoubleOrNull()
            val volume = (quote?.get("06. volume") as? String)?.toDoubleOrNull() ?: 0.0

            if (price == null) {
                logger.debug("❌ Alpha Vantage: No price for $symbol")
                return null
            }

            TradeEvent(
                symbol = symbol,
                exchange = "ALPHA_VANTAGE",
                price = price,
                volume = volume,
                tradeTimestamp = Instant.now().toEpochMilli()
            )
        } catch (e: Exception) {
            logger.debug("❌ Alpha Vantage error for $symbol: ${e.message}")
            null
        }
    }

    /**
     * Generate realistic mock data as fallback
     */
    private fun generateMockData(symbol: String): TradeEvent {
        val basePrices = mapOf(
            "AAPL" to 180.0,
            "GOOGL" to 140.0,
            "MSFT" to 380.0,
            "TSLA" to 240.0
        )

        val basePrice = basePrices[symbol] ?: 100.0
        val variation = (Math.random() - 0.5) * 2.0 // Random walk ±1%
        val price = basePrice * (1 + variation / 100)

        return TradeEvent(
            symbol = symbol,
            exchange = "MOCK",
            price = String.format("%.2f", price).toDouble(),
            volume = (Math.random() * 1000000).toLong().toDouble(),
            tradeTimestamp = Instant.now().toEpochMilli()
        )
    }
}