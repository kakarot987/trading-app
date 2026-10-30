package com.trading.trade_processor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class TradeProcessorApplication

fun main(args: Array<String>) {
	runApplication<TradeProcessorApplication>(*args)
}
