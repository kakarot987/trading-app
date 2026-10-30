package com.trading.historical_service.features

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TechnicalIndicatorsTest {
    private fun near(expected: Double, actual: Double?, tolerance: Double = 1e-8) {
        assertNotNull(actual)
        assertTrue(abs(expected - actual!!) < tolerance, "expected=$expected actual=$actual")
    }

    @Test
    fun `ema seeds with simple average and follows recursive formula`() {
        val result = TechnicalIndicators.ema(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(result[0])
        assertNull(result[1])
        near(2.0, result[2])
        near(3.0, result[3])
        near(4.0, result[4])
    }

    @Test
    fun `RSI handles flat rising and falling fixtures`() {
        near(50.0, TechnicalIndicators.rsi(List(16) { 100.0 })[14])
        near(100.0, TechnicalIndicators.rsi((1..16).map(Int::toDouble))[14])
        near(0.0, TechnicalIndicators.rsi((1..16).map { (17 - it).toDouble() })[14])
        assertTrue(TechnicalIndicators.rsi(List(14) { 100.0 }).all { it == null })
    }

    @Test
    fun `MACD has expected warmup and zero result for constant price`() {
        val result = TechnicalIndicators.macd(List(40) { 100.0 })
        assertTrue(result.take(25).all { it.line == null })
        assertTrue(result.take(33).all { it.signal == null })
        near(0.0, result[33].line)
        near(0.0, result[33].signal)
        near(0.0, result[33].histogram)
    }

    @Test
    fun `volatility and volume ratio require complete history`() {
        val volatility = TechnicalIndicators.logReturnVolatility(List(5) { 100.0 }, 3)
        assertTrue(volatility.take(3).all { it == null })
        near(0.0, volatility[3])

        val ratio = TechnicalIndicators.volumeRatio(listOf(10.0, 20.0, 30.0), 3)
        assertNull(ratio[0])
        assertNull(ratio[1])
        near(1.5, ratio[2])
        assertNull(TechnicalIndicators.volumeRatio(List(3) { 0.0 }, 3)[2])
    }

    @Test
    fun `rejects invalid input and indicator periods`() {
        assertThrows(IllegalArgumentException::class.java) { TechnicalIndicators.ema(listOf(1.0), 0) }
        assertThrows(IllegalArgumentException::class.java) { TechnicalIndicators.rsi(listOf(-1.0)) }
        assertThrows(IllegalArgumentException::class.java) { TechnicalIndicators.macd(listOf(1.0), 26, 12) }
        assertThrows(IllegalArgumentException::class.java) { TechnicalIndicators.logReturnVolatility(listOf(0.0, 1.0)) }
    }
}
