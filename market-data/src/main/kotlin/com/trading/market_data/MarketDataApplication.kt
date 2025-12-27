package com.trading.market_data

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MarketDataApplication

private val logger = LoggerFactory.getLogger(MarketDataApplication::class.java)

fun main(args: Array<String>) {
	runApplication<MarketDataApplication>(*args)

	println("Starting the application")
	logger.info("Logger in application file")
}
