package com.trading.trade_processor.config

import com.trading.common.model.ProcessedTradeEvent
import com.trading.common.model.TradeEvent
import com.trading.trade_processor.service.TradeQuarantineService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
@EnableKafka
class KafkaConfig(
    private val kafkaProperties: KafkaProperties,
    private val quarantineService: TradeQuarantineService
) {

    @Bean
    fun consumerFactory(): ConsumerFactory<String, TradeEvent> {
        val props = kafkaProperties.buildConsumerProperties().toMutableMap()

        // Spring Kafka commits only after successful listener execution.
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false

        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] =
            StringDeserializer::class.java

        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            ErrorHandlingDeserializer::class.java
        props[ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS] =
            JsonDeserializer::class.java

        props[JsonDeserializer.TRUSTED_PACKAGES] = "*"

        return DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            ErrorHandlingDeserializer(JsonDeserializer(TradeEvent::class.java))
        )
    }

    @Bean
    fun kafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, TradeEvent> {

        val factory =
            ConcurrentKafkaListenerContainerFactory<String, TradeEvent>()

        factory.consumerFactory = consumerFactory()
        factory.setCommonErrorHandler(kafkaErrorHandler())
        return factory
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, ProcessedTradeEvent> {
        val props = kafkaProperties.buildProducerProperties().toMutableMap()

        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] =
            StringSerializer::class.java

        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] =
            JsonSerializer::class.java

        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, ProcessedTradeEvent> {
        return KafkaTemplate(producerFactory())
    }

    @Bean
    fun kafkaErrorHandler(): DefaultErrorHandler {
        // Never treat a temporary database outage as a successfully handled trade.
        // Infinite retries can block the partition on a permanently failing record;
        // introduce an explicit failure classification and durable DLT before production.
        return DefaultErrorHandler(
            { record, exception ->
                // A record is recovered only after the quarantine write succeeds.
                // Database failures throw and keep the source offset unacknowledged.
                quarantineService.quarantine(record, exception)
            },
            FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS)
        ).apply {
            // Validation errors are permanent; infrastructure failures keep retrying.
            addNotRetryableExceptions(IllegalArgumentException::class.java)
        }
    }
}
