package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class PaperCommandControllerTest {
    private val db = mock(DatabaseClient::class.java)
    private val wallet = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val request = SubmitPaperOrderRequest(
        "order-fixture", "BTCUSDT", PaperOrderSide.BUY, "0.01000000", 1000L
    )

    @Test fun `paper commands are disabled by default`() {
        val controller = PaperCommandController(db, false)
        val walletError = assertThrows(ResponseStatusException::class.java) {
            controller.createWallet(CreatePaperWalletRequest())
        }
        assertEquals(HttpStatus.FORBIDDEN, walletError.statusCode)
        val orderError = assertThrows(ResponseStatusException::class.java) {
            controller.submit(wallet, request)
        }
        assertEquals(HttpStatus.FORBIDDEN, orderError.statusCode)
    }

    @Test fun `enabled commands reject invalid amounts before DB access`() {
        val controller = PaperCommandController(db, true)
        val walletError = assertThrows(ResponseStatusException::class.java) {
            controller.createWallet(CreatePaperWalletRequest("-1"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, walletError.statusCode)
        val orderError = assertThrows(ResponseStatusException::class.java) {
            controller.submit(wallet, request.copy(quantity = "0.000000001"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, orderError.statusCode)
    }

    @Test fun `enabled commands reject future submission timestamp`() {
        val controller = PaperCommandController(db, true)
        val error = assertThrows(ResponseStatusException::class.java) {
            controller.submit(wallet, request.copy(submittedAtMs = Long.MAX_VALUE))
        }
        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }
}
