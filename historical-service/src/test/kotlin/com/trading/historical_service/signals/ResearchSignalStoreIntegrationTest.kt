package com.trading.historical_service.signals

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration

/** Disposable PostgreSQL acceptance for idempotence, as-of and expiry. */
@Testcontainers
class ResearchSignalStoreIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("research_signals")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test fun `signals remain immutable and expired records stay auditable`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { statement ->
                val ddl = requireNotNull(javaClass.getResourceAsStream(
                    "/db/migration/V8__create_research_signals.sql"
                )).bufferedReader().use { it.readText() }
                statement.execute(ddl)
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        )
        val db = DatabaseClient.create(factory)
        val store = ResearchSignalStore(db)
        val close = 1_793_347_200_000L
        val signal = AbstentionBaseline.predict(
            "BTCUSDT", "technical-4h-v1", close, close, close + 1_000
        )
        assertEquals(1L, store.save(signal).block(Duration.ofSeconds(5)))
        assertEquals(0L, store.save(signal.copy(rationale = "attempted mutation"))
            .block(Duration.ofSeconds(5)))
        assertEquals(0, store.list("BTCUSDT", signal.generatedAtMs - 1, 20).collectList()
            .block(Duration.ofSeconds(5))!!.size)
        assertEquals(1, store.list("BTCUSDT", signal.generatedAtMs, 20, true).collectList()
            .block(Duration.ofSeconds(5))!!.size)
        assertEquals(0, store.list("BTCUSDT", signal.expiresAtMs, 20, true).collectList()
            .block(Duration.ofSeconds(5))!!.size)
        val archived = store.list("BTCUSDT", signal.expiresAtMs, 20).collectList()
            .block(Duration.ofSeconds(5))!!
        assertEquals(1, archived.size)
        assertEquals(signal, archived.single())
    }
}
