package com.trading.historical_service.signals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class ResearchSignalControllerTest {
    private val controller = ResearchSignalController(mock(ResearchSignalStore::class.java))
    private val now = Instant.parse("2026-10-30T12:00:00Z")

    @Test fun `rejects unsupported symbols`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            controller.list("DOGEUSDT", now, 20)
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }

    @Test fun `rejects invalid limits`() {
        for (limit in listOf(-1, 0, 101)) {
            val error = assertThrows(ResponseStatusException::class.java) {
                controller.list("BTCUSDT", now, limit)
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
    }

    @Test fun `rejects future cutoffs`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            controller.list("ETHUSDT", Instant.now().plusSeconds(3600), 20)
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }
}
