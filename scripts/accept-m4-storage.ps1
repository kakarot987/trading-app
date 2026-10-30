# M4 database smoke test: synthetic data is always rolled back.
$ErrorActionPreference = 'Stop'
$id = [guid]::NewGuid().ToString('N')
$url = "https://example.org/m4-test/$id"
$sql = @"
BEGIN;
CREATE TEMP TABLE acceptance_results (label text, passed boolean) ON COMMIT DROP;
WITH first_insert AS (
  INSERT INTO news_articles (
    deduplication_key, provider, provider_article_id, canonical_url,
    headline, source_name, event_type, published_at, observed_at
  ) VALUES (
    repeat(md5('$url'), 2), 'm4-acceptance', '$id', '$url',
    'Synthetic test headline', 'Acceptance Fixture', 'OTHER',
    '2026-10-30 08:00:00+00', '2026-10-30 08:05:00+00'
  ) RETURNING id
)
INSERT INTO news_article_symbols(article_id, symbol)
SELECT id, symbol FROM first_insert CROSS JOIN
  (VALUES ('BTCUSDT'), ('ETHUSDT')) AS s(symbol);
INSERT INTO acceptance_results
SELECT 'Article and two associations',
  (SELECT count(*) FROM news_articles WHERE provider_article_id='$id')=1
  AND (SELECT count(*) FROM news_article_symbols s
       JOIN news_articles a ON a.id=s.article_id
       WHERE a.provider_article_id='$id')=2;
WITH replay AS (
  INSERT INTO news_articles (
    deduplication_key, provider, provider_article_id, canonical_url,
    headline, source_name, event_type, published_at, observed_at
  ) VALUES (
    repeat(md5('$url'), 2), 'm4-acceptance', '$id', '$url',
    'Replayed headline', 'Acceptance Fixture', 'OTHER',
    '2026-10-30 08:00:00+00', '2026-10-30 08:06:00+00'
  ) ON CONFLICT (deduplication_key) DO NOTHING RETURNING id
)
INSERT INTO acceptance_results
SELECT 'Duplicate replay preserves first observed timestamp',
  (SELECT count(*) FROM replay)=0 AND
  (SELECT observed_at FROM news_articles WHERE provider_article_id='$id')
    = '2026-10-30 08:05:00+00'::timestamptz;
INSERT INTO acceptance_results
SELECT 'As-of query excludes future observation',
  (SELECT count(*) FROM news_articles a JOIN news_article_symbols s ON s.article_id=a.id
   WHERE a.provider_article_id='$id' AND s.symbol='BTCUSDT'
   AND a.observed_at <= '2026-10-30 08:04:59+00'::timestamptz)=0
  AND
  (SELECT count(*) FROM news_articles a JOIN news_article_symbols s ON s.article_id=a.id
   WHERE a.provider_article_id='$id' AND s.symbol='BTCUSDT'
   AND a.observed_at <= '2026-10-30 08:05:00+00'::timestamptz)=1;
SELECT (CASE WHEN passed THEN 'PASS' ELSE 'FAIL' END) || '|' || label
FROM acceptance_results ORDER BY label;
ROLLBACK;
"@
$output = $sql | & docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades
if ($LASTEXITCODE -ne 0) { throw 'M4 database smoke test failed; transaction not committed.' }
$results = @($output | Where-Object { $_ -match '^(PASS|FAIL)\|' })
$results | ForEach-Object { Write-Host $_ }
if ($results.Count -ne 3 -or @($results | Where-Object { $_ -notmatch '^PASS\|' }).Count -gt 0) {
    throw 'M4 storage acceptance checks failed'
}
Write-Host '[PASS] Storage checks succeeded; all synthetic rows rolled back.'
