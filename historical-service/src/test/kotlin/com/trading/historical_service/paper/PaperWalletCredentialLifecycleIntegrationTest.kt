package com.trading.historical_service.paper

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

/**
 * Disposable PostgreSQL test: atomic credential CAS and immutable audit.
 * The test never uses real wallet secrets or an exchange.
 */
@Testcontainers
class PaperWalletCredentialLifecycleIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_credential_lifecycle")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun bootstrapDb(): DatabaseClient {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { statement ->
                for (migration in listOf(
                    "V9__create_paper_trading_ledger.sql",
                    "V12__paper_wallet_scoped_credentials.sql",
                    "V13__paper_wallet_credential_audit.sql"
                )) {
                    statement.execute(requireNotNull(javaClass.getResourceAsStream(
                        "/db/migration/$migration"
                    )).bufferedReader().use { it.readText() })
                }
            }
        }
        return DatabaseClient.create(PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        ))
    }

    @Test fun `rotation and revocation are atomic and auditable`() {
        val db = bootstrapDb()
        val commands = PaperCommandController(db, true)
        val access = PaperWalletAccessStore(db)
        val lifecycle = PaperWalletCredentialLifecycle(db)
        val controller = PaperWalletCredentialController(lifecycle)
        val first = requireNotNull(commands.createWallet(CreatePaperWalletRequest())
            .block(Duration.ofSeconds(10)))
        val second = requireNotNull(commands.createWallet(CreatePaperWalletRequest())
            .block(Duration.ofSeconds(10)))

        fun authorized(wallet: UUID, token: String): Boolean =
            requireNotNull(access.authorized(wallet, token).block(Duration.ofSeconds(10)))
        fun events(wallet: UUID): List<Pair<String, Long>> =
            db.sql(
                """
                SELECT event_type, credential_version
                FROM paper_wallet_credential_events
                WHERE wallet_id=:wallet ORDER BY event_id
                """.trimIndent()
            ).bind("wallet", wallet)
                .map { row, _ ->
                    requireNotNull(row.get("event_type", String::class.java)) to
                        (requireNotNull(row.get("credential_version")) as Number).toLong()
                }.all().collectList().block(Duration.ofSeconds(10))!!

        assertTrue(authorized(first.walletId, first.walletToken))
        assertEquals(emptyList<Pair<String, Long>>(), events(first.walletId))

        val rotated = requireNotNull(controller.rotate(first.walletId,
            "Bearer ${first.walletToken}").block(Duration.ofSeconds(10))).walletToken
        assertTrue(PaperWalletTokens.isWellFormed(rotated))
        assertNotEquals(rotated, first.walletToken)
        assertFalse(authorized(first.walletId, first.walletToken))
        assertTrue(authorized(first.walletId, rotated))
        assertFalse(authorized(second.walletId, rotated))
        assertEquals(listOf("ROTATED" to 2L), events(first.walletId))

        // Stale credential is denied even if the HTTP filter has already
        // authorized an old request before another request rotated it.
        assertNull(lifecycle.rotate(first.walletId, first.walletToken)
            .block(Duration.ofSeconds(10)))
        assertFalse(requireNotNull(lifecycle.revoke(first.walletId, first.walletToken)
            .block(Duration.ofSeconds(10))))
        assertEquals(listOf("ROTATED" to 2L), events(first.walletId))

        val secondRotation = requireNotNull(lifecycle.rotate(first.walletId, rotated)
            .block(Duration.ofSeconds(10)))
        assertFalse(authorized(first.walletId, rotated))
        assertTrue(authorized(first.walletId, secondRotation))
        assertEquals(listOf("ROTATED" to 2L, "ROTATED" to 3L),
            events(first.walletId))

        controller.revoke(first.walletId, "Bearer $secondRotation")
            .block(Duration.ofSeconds(10))
        assertFalse(authorized(first.walletId, secondRotation))
        assertFalse(requireNotNull(lifecycle.revoke(first.walletId, secondRotation)
            .block(Duration.ofSeconds(10))))
        assertNull(lifecycle.rotate(first.walletId, secondRotation)
            .block(Duration.ofSeconds(10)))
        assertTrue(authorized(second.walletId, second.walletToken))
        assertEquals(listOf("ROTATED" to 2L, "ROTATED" to 3L, "REVOKED" to 4L),
            events(first.walletId))
        assertEquals(emptyList<Pair<String, Long>>(), events(second.walletId))

        // Even a previously valid token cannot restore a permanently revoked
        // wallet through this endpoint; no bootstrap-token recovery is added.
        val error = assertThrows(RuntimeException::class.java) {
            controller.rotate(first.walletId, "Bearer $secondRotation")
                .block(Duration.ofSeconds(10))
        }
        assertTrue(error is ResponseStatusException || error.cause is ResponseStatusException)
        val status = (error as? ResponseStatusException)
            ?: (error.cause as? ResponseStatusException)
        assertEquals(HttpStatus.UNAUTHORIZED, status?.statusCode)

        // Legacy wallets are not silently claimed or bootstrapped.
        val legacyWallet = requireNotNull(db.sql(
            "INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id"
        ).map { row, _ -> requireNotNull(row.get("wallet_id", UUID::class.java)) }
            .one().block(Duration.ofSeconds(10)))
        val unrelated = PaperWalletTokens.generate()
        assertNull(lifecycle.rotate(legacyWallet, unrelated).block(Duration.ofSeconds(10)))
        assertFalse(requireNotNull(lifecycle.revoke(legacyWallet, unrelated)
            .block(Duration.ofSeconds(10))))
        assertNull(lifecycle.rotate(UUID.randomUUID(), unrelated).block(Duration.ofSeconds(10)))

        // Immutable audit rows cannot be erased to hide lifecycle transitions.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            val sql = "DELETE FROM paper_wallet_credential_events WHERE wallet_id=?"
            assertThrows(java.sql.SQLException::class.java) {
                conn.prepareStatement(sql).use { statement ->
                    statement.setObject(1, first.walletId)
                    statement.executeUpdate()
                }
            }
        }
        assertEquals(3, events(first.walletId).size)
    }

}
