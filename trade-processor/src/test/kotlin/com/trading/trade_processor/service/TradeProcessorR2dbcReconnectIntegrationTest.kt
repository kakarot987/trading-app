package com.trading.trade_processor.service

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Mono
import java.sql.DriverManager
import java.time.Duration

/**
 * Tests a dead PostgreSQL session inside a disposable Testcontainers database.
 * This is not a live database restart or Kafka broker recovery test.
 */
@Testcontainers
class TradeProcessorR2dbcReconnectIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("processor_reconnect")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun pid(pool: ConnectionPool): Int =
        Mono.usingWhen(
            Mono.from(pool.create()),
            { connection ->
                Mono.from(connection.createStatement("SELECT pg_backend_pid() AS pid").execute())
                    .flatMap { result ->
                        Mono.from(result.map { row, _ ->
                            requireNotNull(row.get("pid", java.lang.Integer::class.java)).toInt()
                        })
                    }
            },
            { connection -> Mono.from(connection.close()) }
        ).block(Duration.ofSeconds(15))!!

    @Test
    fun `dead pooled database session is replaced on next acquire`() {
        val connectionFactory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host)
                .port(postgres.getMappedPort(5432))
                .database(postgres.databaseName)
                .username(postgres.username)
                .password(postgres.password)
                .build()
        )
        val pool = ConnectionPool(
            ConnectionPoolConfiguration.builder(connectionFactory)
                .initialSize(0)
                .maxSize(1)
                .validationQuery("SELECT 1")
                .maxIdleTime(Duration.ofSeconds(30))
                .maxAcquireTime(Duration.ofSeconds(10))
                .build()
        )
        try {
            val initial = pid(pool)
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { jdbc ->
                jdbc.prepareStatement("SELECT pg_terminate_backend(?)").use { statement ->
                    statement.setInt(1, initial)
                    statement.executeQuery().use { rs ->
                        assertTrue(rs.next())
                        assertTrue(rs.getBoolean(1), "Expected test backend session to be terminated")
                    }
                }
            }
            val recovered = pid(pool)
            assertTrue(recovered > 0)
            assertNotEquals(initial, recovered, "Returned terminated session instead of reconnecting")
        } finally {
            pool.dispose()
        }
    }
}
