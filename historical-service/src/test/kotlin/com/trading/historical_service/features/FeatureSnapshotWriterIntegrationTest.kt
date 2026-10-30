package com.trading.historical_service.features

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration

@Testcontainers
class FeatureSnapshotWriterIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("features")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun snapshot(version: String = FeatureSnapshots.VERSION, availableAt: Long = 144_000_000L) =
        FeatureSnapshot(
            symbol = "BTCUSDT", interval = "4h", horizonHours = 4,
            featureVersion = version, candleCloseTimeMs = 129_600_000L,
            availableAtMs = availableAt, ema12 = 101.0, ema26 = 99.0,
            rsi14 = 55.0, macdLine = 2.0, macdSignal = 1.0,
            macdHistogram = 1.0, volatility20 = 0.015, volumeRatio20 = 1.2
        )

    @Test
    fun `same observation is idempotent but later observation and new version are preserved`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { statement ->
                // Exercise the real Flyway DDL rather than a simplified test schema.
                val ddl = javaClass.getResourceAsStream(
                    "/db/migration/V5__create_technical_feature_snapshots.sql"
                )!!.bufferedReader().readText()
                statement.execute(ddl)
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName)
                .username(postgres.username).password(postgres.password).build()
        )
        val client = DatabaseClient.create(factory)
        val writer = FeatureSnapshotWriter(client)
        fun count() = client.sql("SELECT COUNT(*) AS n FROM technical_feature_snapshots")
            .map { row, _ -> (row.get("n") as Number).toLong() }
            .one().block(Duration.ofSeconds(5))

        assertEquals(1L, writer.save(snapshot()))
        assertEquals(0L, writer.save(snapshot()))
        assertEquals(1L, writer.save(snapshot(availableAt = 144_000_001L)))
        assertEquals(1L, writer.save(snapshot(version = "technical-4h-v2")))
        assertEquals(3L, count())

        assertThrows(IllegalArgumentException::class.java) {
            writer.save(snapshot(availableAt = 129_599_999L))
        }
        assertEquals(3L, count())
    }
}
