package com.trading.historical_service.config

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Mono
import java.sql.DriverManager
import java.time.Duration

/**
 * A disposable PostgreSQL container forcibly closes an idle pooled connection.
 * The pool must validate it before reuse and reconnect. This is not a full
 * Docker/Redis outage acceptance test against the running application.
 */
@Testcontainers
class R2dbcReconnectIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("reconnect_test")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun backendPid(pool: ConnectionPool): Int =
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
    fun `idle pooled connection is replaced after postgres terminates session`() {
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host)
                .port(postgres.getMappedPort(5432))
                .database(postgres.databaseName)
                .username(postgres.username)
                .password(postgres.password)
                .build()
        )
        val config = ConnectionPoolConfiguration.builder(factory)
            .initialSize(0)
            .maxSize(1)
            .validationQuery("SELECT 1")
            .maxIdleTime(Duration.ofSeconds(30))
            .maxAcquireTime(Duration.ofSeconds(10))
            .build()
        val pool = ConnectionPool(config)
        try {
            val first = backendPid(pool)
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
                .use { jdbc ->
                    jdbc.prepareStatement("SELECT pg_terminate_backend(?)").use { statement ->
                        statement.setInt(1, first)
                        statement.executeQuery().use { result ->
                            assertTrue(result.next())
                            assertTrue(result.getBoolean(1))
                        }
                    }
                }
            val recovered = backendPid(pool)
            assertTrue(recovered > 0)
            assertTrue(recovered != first, "Pool returned the dead PostgreSQL session")
            assertEquals(recovered, backendPid(pool), "Healthy session should be reusable")
        } finally {
            pool.dispose()
        }
    }
}
