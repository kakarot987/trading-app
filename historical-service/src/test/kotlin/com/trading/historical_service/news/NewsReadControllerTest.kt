package com.trading.historical_service.news

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class NewsReadControllerTest {
    private val controller = NewsReadController(mock(DatabaseClient::class.java))
    private val cutoff = Instant.parse("2026-10-30T08:00:00Z")

    @Test
    fun `rejects unsupported symbols before database access`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            controller.list("DOGEUSDT", cutoff, 20)
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test
    fun `rejects invalid page sizes before database access`() {
        for (limit in listOf(0, -1, 101)) {
            val error = assertThrows(ResponseStatusException::class.java) {
                controller.list("BTCUSDT", cutoff, limit)
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
    }

    @Test
    fun `rejects future as-of timestamps before database access`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            controller.list("ETHUSDT", Instant.now().plusSeconds(3600), 20)
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }
}
