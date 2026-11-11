package com.trading.trade_processor.service

import com.trading.common.model.TradeEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
        Mockito.`when`(persistence.persistWithOutbox(
            Mockito.any(), Mockito.any()
        )).thenReturn(0)
        consumer.consume(event())
        Mockito.verify(persistence).persistWithOutbox(Mockito.any(), Mockito.any())
        Mockito.verifyNoInteractions(redis)
    }

    @Test fun `failed durable persistence propagates so Kafka cannot acknowledge`() {
        Mockito.`when`(persistence.persistWithOutbox(
            Mockito.any(), Mockito.any()
        )).thenThrow(IllegalStateException("database unavailable"))
        assertThrows(IllegalStateException::class.java) {
            consumer.consume(event())
        }
        Mockito.verifyNoInteractions(redis)
    }
}
