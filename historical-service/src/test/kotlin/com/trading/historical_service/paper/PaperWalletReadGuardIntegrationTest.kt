package com.trading.historical_service.paper

import com.trading.historical_service.evaluation.PaperEvaluationController
import com.trading.historical_service.evaluation.PersistedPaperActivityRepository
import com.trading.historical_service.evaluation.PersistedPaperInventoryRepository
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration

/** Real R2DBC transactional reads backed by disposable PostgreSQL. */
@Testcontainers
class PaperWalletReadGuardIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_read_guard")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test fun `wallet read routes deny stale revoked cross wallet credentials at query time`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                for (migration in listOf(
                    "V9__create_paper_trading_ledger.sql",
                    "V12__paper_wallet_scoped_credentials.sql",
                    "V13__paper_wallet_credential_audit.sql"
                )) {
                    stmt.execute(requireNotNull(javaClass.getResourceAsStream(
                        "/db/migration/$migration"
                    )).bufferedReader().use { it.readText() })
                }
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        )
        val db = DatabaseClient.create(factory)
        val guard = PaperWalletReadGuard(db, R2dbcTransactionManager(factory), false)
        val portfolio = PaperPortfolioController(db, guard)
        val evaluationController = PaperEvaluationController(
            PersistedPaperActivityRepository(db),
            PersistedPaperInventoryRepository(db), guard
        )
        val lifecycle = PaperWalletCredentialLifecycle(db)
        val commands = PaperCommandController(db, true)
        val first = requireNotNull(commands.createWallet(CreatePaperWalletRequest("100"))
            .block(Duration.ofSeconds(10)))
        val other = requireNotNull(commands.createWallet(CreatePaperWalletRequest("200"))
            .block(Duration.ofSeconds(10)))
        val wallet = first.walletId
        val old = "Bearer ${first.walletToken}"
        fun balances(header: String) = portfolio.balances(wallet, header)
            .collectList().block(Duration.ofSeconds(10))!!
        fun orders(header: String) = portfolio.orders(wallet, 20, header)
            .collectList().block(Duration.ofSeconds(10))!!
        fun evaluation(header: String) = evaluationController.evaluation(wallet, header)
            .block(Duration.ofSeconds(10))!!
        fun denied(block: () -> Any?) {
            val error = assertThrows(RuntimeException::class.java) { block() }
            val response = (error as? ResponseStatusException)
                ?: (error.cause as? ResponseStatusException)
            assertEquals(HttpStatus.UNAUTHORIZED, response?.statusCode)
        }

        assertEquals(0, balances(old).single().available.compareTo(java.math.BigDecimal("100")))
        assertEquals(emptyList<PaperOrderView>(), orders(old))
        assertEquals(0L, evaluation(old).activity.fillCount)
        denied { balances("") }
        denied { orders("Bearer ${other.walletToken}") }
        denied { evaluation("Bearer ${other.walletToken}") }

        // A request whose filter check happened before the rotation must
        // still fail at DB admission when the old token is supplied.
        val rotated = requireNotNull(lifecycle.rotate(wallet, first.walletToken)
            .block(Duration.ofSeconds(10)))
        denied { balances(old) }
        denied { orders(old) }
        denied { evaluation(old) }
        val current = "Bearer $rotated"
        assertEquals(1, balances(current).size)
        assertEquals(0L, evaluation(current).activity.fillCount)

        assertTrue(lifecycle.revoke(wallet, rotated).block(Duration.ofSeconds(10))!!)
        denied { balances(current) }
        denied { orders(current) }
        denied { evaluation(current) }
        assertEquals(0, portfolio.balances(other.walletId, "Bearer ${other.walletToken}")
            .collectList().block(Duration.ofSeconds(10))!!.single()
            .available.compareTo(java.math.BigDecimal("200")) )
    }
}
