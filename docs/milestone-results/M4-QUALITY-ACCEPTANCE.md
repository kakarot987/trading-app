# M4 news quality and provenance acceptance

This file covers reproducible **code-level** acceptance for the M4 phase PR.
It does not constitute provider authorization or human-labeled sentiment
validation, and it does not change `docs/MILESTONES.md`.

## Offline verification matrix

| Requirement | Evidence | Remaining limitation |
| --- | --- | --- |
| Article HTTPS URL / source provenance | `NewsArticleTest` | Publishers can revise or remove articles |
| Safe canonical links | `NewsArticleTest`, `CryptoPanicNewsPollerTest` reject credential-bearing URLs | Query-based tracking parameters remain permitted |
| Published before observed | `NewsArticleTest`, `CryptoPanicNewsPollerTest` | Provider timestamps need live verification |
| Symbol relevance | `CryptoPanicNewsPollerTest` maps explicit BTC/ETH instruments only | Publisher relevance and ambiguities need labeled review |
| Dedup and first-observed timestamp | `NewsArticleRepositoryIntegrationTest` | Cross-publisher syndication can require separate editorial matching |
| No future news in historical query | `NewsReadControllerTest` and repository tests | Historical provider snapshots may be incomplete |
| No default provider polling | `CryptoPanicNewsPollerTest` | Enable only after operator reviews plan and terms |
| Sentiment evaluation | `SentimentBenchmarkTest` and 24-case `SentimentEvaluationDatasetTest` | Fixtures are authored stress cases, not representative human-labeled news |
| Consistent confusion statistics | `SentimentBenchmarkTest` calls classifier exactly once per headline | Does not establish predictive value |
| Secret-safe polling errors | Poller logs exception class only; URL and exception message are omitted | Review other logging and external HTTP tracing configurations |

## Local acceptance commands

```powershell
$env:JAVA_TOOL_OPTIONS = "-Duser.timezone=UTC"
try {
    .\gradlew.bat :historical-service:test --tests "*NewsArticleTest" --tests "*CryptoPanicNewsPollerTest" --tests "*NewsArticleRepositoryIntegrationTest" --tests "*NewsReadControllerTest" --tests "*SentimentBenchmarkTest" --tests "*SentimentEvaluationDatasetTest"
}
finally {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

Testcontainers tests use disposable PostgreSQL; no live provider API request or
production wallet action is part of this matrix.

## Human/provider phase exit gates

1. Record written provider plan approval for intended title, link, metadata
   storage and redistribution; quota and attribution requirements must be
   reviewed before enabling `m4.news.enabled`.
2. Assemble a representative consented/permitted, time-bounded BTC/ETH
   labeled dataset with independent annotators. Record article timestamp,
   first observation, relevance label, sentiment label, annotator agreement
   and split boundaries. Do not repurpose the 24 toy stress fixtures as proof.
3. Measure macro-F1, per-class precision/recall, confusion matrix and
   abstention/uncertainty, including relevance errors, with honest sample sizes.
4. Inspect real provider quota errors and ensure credentials are redacted in
   application/proxy/observability logs.
5. Keep headline sentiment separate from forecast models and paper order
   actions until out-of-sample evidence justifies any linkage.

The integration remains metadata-only and disabled by default. This PR does
not claim commercial reuse rights, real-news classifier accuracy or any
trading profitability.
