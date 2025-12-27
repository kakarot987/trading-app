package com.trading.websocker_broadcaster.config

import com.trading.common.model.ProcessedTradeEvent
import com.trading.websocker_broadcaster.model.AlertEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.support.serializer.JsonDeserializer

@Configuration
@EnableKafka
class KafkaConfig {

    private fun commonProps(groupId: String) = mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092"),
        ConsumerConfig.GROUP_ID_CONFIG to groupId,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
        JsonDeserializer.TRUSTED_PACKAGES to "*"
    )

    @Bean
    fun tradeConsumerFactory(): ConsumerFactory<String, ProcessedTradeEvent> =
        DefaultKafkaConsumerFactory(
            commonProps("websocket-broadcaster-trades"),
            StringDeserializer(),
            JsonDeserializer(ProcessedTradeEvent::class.java)
        )

    @Bean
    fun alertConsumerFactory(): ConsumerFactory<String, AlertEvent> =
        DefaultKafkaConsumerFactory(
            commonProps("websocket-broadcaster-alerts"),
            StringDeserializer(),
            JsonDeserializer(AlertEvent::class.java)
        )

    @Bean
    fun tradeKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, ProcessedTradeEvent> =
        ConcurrentKafkaListenerContainerFactory<String, ProcessedTradeEvent>().apply {
            consumerFactory = tradeConsumerFactory()
            setConcurrency(1)
        }

    @Bean
    fun alertKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, AlertEvent> =
        ConcurrentKafkaListenerContainerFactory<String, AlertEvent>().apply {
            consumerFactory = alertConsumerFactory()
            setConcurrency(1)
        }
}