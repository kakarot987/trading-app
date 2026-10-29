package com.trading.trade_processor.config

import com.trading.trade_processor.service.TradeQuarantineService
import org.mockito.Mockito
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.kafka.listener.DefaultErrorHandler

/**
 * Guards against accidentally dropping the listener error handler from the
 * factory. That regression allowed failed DB writes to be skipped after the
 * framework's default retry attempts were exhausted.
 */
class KafkaConfigTest {

    private val config = KafkaConfig(KafkaProperties(), Mockito.mock(TradeQuarantineService::class.java))

    @Test
    fun `consumer disables Kafka automatic offset commits`() {
        val properties = config.consumerFactory().configurationProperties

        assertEquals(false, properties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG])
    }

    @Test
    fun `listener container uses the non-skipping retry handler`() {
        val container = config.kafkaListenerContainerFactory()
            .createContainer("trade-events")

        assertTrue(container.commonErrorHandler is DefaultErrorHandler)
    }
}
