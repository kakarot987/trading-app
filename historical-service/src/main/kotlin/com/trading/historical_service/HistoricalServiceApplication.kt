package com.trading.historical_service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class HistoricalServiceApplication

fun main(args: Array<String>) {
	runApplication<HistoricalServiceApplication>(*args)
}
