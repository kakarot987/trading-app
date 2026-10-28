package com.trading.trade_processor.config

import com.trading.common.model.ProcessedTradeEvent
import com.trading.common.model.TradeEvent
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
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
@EnableKafka
class KafkaConfig(
    private val kafkaProperties: KafkaProperties
) {

    @Bean
    fun consumerFactory(): ConsumerFactory<String, TradeEvent> {
        val props = kafkaProperties.buildConsumerProperties().toMutableMap()

        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] =
            StringDeserializer::class.java

        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            JsonDeserializer::class.java

        props[JsonDeserializer.TRUSTED_PACKAGES] = "*"

        return DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            JsonDeserializer(TradeEvent::class.java)
        )
    }

    @Bean
    fun kafkaListenerContainerFactory():
            ConcurrentKafkaListenerContainerFactory<String, TradeEvent> {

        val factory =
            ConcurrentKafkaListenerContainerFactory<String, TradeEvent>()

        factory.consumerFactory = consumerFactory()
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
        return DefaultErrorHandler(
            { record, exception ->
                throw IllegalStateException(
                    "Kafka processing failed at " +
                            "${record.topic()}-${record.partition()} " +
                            "offset ${record.offset()}; refusing to skip record",
                    exception
                )
            },
            FixedBackOff(1_000L, 3L)
        )
    }
}