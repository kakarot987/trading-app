package com.trading.trade_processor.service

import com.trading.common.model.TradeEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.springframework.data.redis.core.ReactiveRedisTemplate

/** Input errors and duplicate/retry boundaries must not quietly skip trades. */
class TradeConsumerBoundaryTest {
    @Suppress("UNCHECKED_CAST")
    private val redis = Mockito.mock(ReactiveRedisTemplate::class.java)
        as ReactiveRedisTemplate<String, TradeEvent>
    private val persistence = Mockito.mock(TradePersistenceService::class.java)
    private val consumer = TradeConsumer(persistence, redis)

    private fun event(id: String? = "stable-id") = TradeEvent(
        symbol = "BTCUSDT", exchange = "BINANCE", price = 70000.0,
        volume = 0.001, tradeTimestamp = 1700000000000L,
        side = "BUY", tradeId = id
    )

    @Test fun `missing trade IDs fail before persistence or cache`() {
        for (id in listOf(null, "", " ")) {
            assertThrows(IllegalArgumentException::class.java) {
                consumer.consume(event(id))
            }
        }
        Mockito.verifyNoInteractions(persistence, redis)
    }

    @Test fun `duplicate input does not touch best effort cache`() {
        // Mockito's unstubbed Int result is zero: an already-seen trade.
        // Avoid Mockito.any() here: it passes null to Kotlin non-null parameters.
        consumer.consume(event())
        assertEquals(1, Mockito.mockingDetails(persistence).invocations.count {
            it.method.name == "persistWithOutbox"
        })
        Mockito.verifyNoInteractions(redis)
    }

    @Test fun `failed durable persistence propagates so Kafka cannot acknowledge`() {
        // Use a default answer rather than null-producing argument matchers.
        // Any actual persistence invocation fails, regardless of generated timestamps.
        val failingPersistence = Mockito.mock(
            TradePersistenceService::class.java,
            Answer<Any?> { invocation ->
                if (invocation.method.name == "persistWithOutbox") {
                    throw IllegalStateException("database unavailable")
                }
                Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
        )
        assertThrows(IllegalStateException::class.java) {
            TradeConsumer(failingPersistence, redis).consume(event())
        }
        assertEquals(1, Mockito.mockingDetails(failingPersistence).invocations.count {
            it.method.name == "persistWithOutbox"
        })
        Mockito.verifyNoInteractions(redis)
    }
}
