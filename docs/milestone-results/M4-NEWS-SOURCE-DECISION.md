# M4 — News provider decision and limits

**Candidate:** CryptoPanic v2 posts API, metadata-only. This is a candidate integration, not confirmation of a paid subscription or reuse rights.

- Endpoint: `https://cryptopanic.com/api/{developer|growth|enterprise}/v2/posts/`.
- Filters: `currencies=BTC,ETH`, `public=true`, `kind=news`.
- Returns `results` with `published_at`, `instruments`, `source`, `original_url` and `url`.
- Access requires an authenticated account and provider-specific tier. **Do not enable** until the operator has confirmed the plan, API limits and permission to store article titles, URLs and metadata for the intended use. Do not ingest article body or copyrighted full text.
- Application is **disabled by default** (`M4_NEWS_ENABLED=false` or property `m4.news.enabled`).
- Default cadence: 900,000 ms (every 15 minutes); one request per poll, 20 articles maximum processed. At uninterrupted default cadence this could mean approximately **2,880 requests in a 30-day month**; check the actual monthly allowance before enabling. If monthly quota is lower, increase cadence or disable.
- Provider 403/429 errors must be treated as capacity limits. No client-side retry storm.
- Provider credentials must remain in local environment/secret management, never committed or displayed in logs.
- Store separate `published_at` and first `observed_at`. An as-of backtest can only use events with `observed_at <= prediction_time`.
- No sentiment inferred from an article title. Benchmark and label sentiment before connecting to signals.

Reference: https://cryptopanic.com/developers/api/
