package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class PaperPortfolioControllerTest {
    private val controller = PaperPortfolioController(mock(DatabaseClient::class.java))
    private val wallet = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test fun `rejects invalid order page limits before database access`() {
        for (limit in listOf(-1, 0, 101)) {
            val error = assertThrows(ResponseStatusException::class.java) {
                controller.orders(wallet, limit)
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
    }
}
