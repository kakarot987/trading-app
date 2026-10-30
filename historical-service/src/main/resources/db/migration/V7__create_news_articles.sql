-- M4: persist provenance and both publication/observation timestamps.
-- No sentiment scores are stored until a benchmarked classifier is introduced.
CREATE TABLE IF NOT EXISTS news_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deduplication_key CHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(128) NOT NULL,
    provider_article_id VARCHAR(255),
    canonical_url TEXT NOT NULL,
    headline VARCHAR(1000) NOT NULL,
    source_name VARCHAR(256) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT news_publication_before_observation CHECK (published_at <= observed_at)
);
CREATE TABLE IF NOT EXISTS news_article_symbols (
    article_id UUID NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    symbol VARCHAR(16) NOT NULL CHECK (symbol IN ('BTCUSDT','ETHUSDT')),
    PRIMARY KEY (article_id, symbol)
);
CREATE INDEX IF NOT EXISTS idx_news_articles_asof
    ON news_articles(observed_at DESC, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_news_article_symbols_symbol
    ON news_article_symbols(symbol, article_id);
