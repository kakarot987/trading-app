package com.trading.historical_service.news

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant

@Testcontainers
class NewsArticleRepositoryIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("news")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test
    fun `duplicate ingestion preserves original observation and atomic symbol associations`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { statement ->
                val ddl = javaClass.getResourceAsStream(
                    "/db/migration/V7__create_news_articles.sql"
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
        val repository = NewsArticleRepository(
            client, TransactionalOperator.create(R2dbcTransactionManager(factory))
        )
        val published = Instant.parse("2026-10-30T08:00:00Z")
        val observed = Instant.parse("2026-10-30T08:02:00Z")
        val article = NewsArticle(
            "example-feed", "item-7", "https://example.org/news/item-7?utm_medium=rss",
            "A sample security update", published, observed,
            setOf("BTCUSDT", "ETHUSDT"), NewsEventType.SECURITY, "Example Publisher"
        )
        assertEquals(true, repository.insert(article).block(Duration.ofSeconds(10)))
        assertEquals(false, repository.insert(article.copy(
            canonicalUrl = "https://example.org/news/item-7?utm_medium=other",
            observedAt = observed.plusSeconds(30)
        )).block(Duration.ofSeconds(10)))
        val count = client.sql("SELECT count(*) AS n FROM news_articles")
            .map { row, _ -> (row.get("n") as Number).toLong() }
            .one().block(Duration.ofSeconds(5))
        assertEquals(1L, count)
        assertEquals(1L, repository.countObservedBy("BTCUSDT", observed).block(Duration.ofSeconds(5)))
        assertEquals(1L, repository.countObservedBy("ETHUSDT", observed).block(Duration.ofSeconds(5)))
        assertEquals(0L, repository.countObservedBy("BTCUSDT", observed.minusSeconds(1)).block(Duration.ofSeconds(5)))
        val stored = client.sql("SELECT observed_at FROM news_articles")
            .map { row, _ -> row.get("observed_at", java.time.OffsetDateTime::class.java) }
            .one().block(Duration.ofSeconds(5))
        assertEquals(observed, stored!!.toInstant())
    }
}
