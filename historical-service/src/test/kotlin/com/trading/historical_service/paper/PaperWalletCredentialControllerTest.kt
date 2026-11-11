package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** Validation happens before any SQL write or secret lifecycle side effect. */
class PaperWalletCredentialControllerTest {
    private val wallet = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val db = Mockito.mock(DatabaseClient::class.java)
    private val controller = PaperWalletCredentialController(PaperWalletCredentialLifecycle(db))

    @Test fun `invalid credentials are rejected before rotation or revocation`() {
        for (bad in listOf("", "Bearer bad", "Basic " + "a".repeat(50),
                "bearer " + PaperWalletTokens.generate())) {
            val rotateError = assertThrows(ResponseStatusException::class.java) {
                controller.rotate(wallet, bad)
            }
            assertEquals(HttpStatus.UNAUTHORIZED, rotateError.statusCode)
            val revokeError = assertThrows(ResponseStatusException::class.java) {
                controller.revoke(wallet, bad)
            }
            assertEquals(HttpStatus.UNAUTHORIZED, revokeError.statusCode)
        }
        Mockito.verifyNoInteractions(db)
    }
}
