# M8 browser smoke acceptance (manual, local-only)

Status: **NOT YET EXECUTED**. Record actual browser results before marking passed. This document is not a production security certification.

## Preconditions

- M8 historical service running on localhost:8084; verified API demo mode with `scripts/accept-m8-paper-api.ps1 -ExpectDemoBypass`.
- React frontend started from `client-connection` using `npm ci` then `npm run dev`.
- Test wallet ID: `8526fb2f-ccf1-487b-9f4e-0094b412b45e`. Synthetic local wallet only, no real money.

## Checklist

- [ ] Frontend loads without uncaught browser-console errors.
- [ ] Sidebar **Paper Portfolio** opens.
- [ ] Enter test wallet UUID and choose **Load wallet**.
- [ ] Available balances render (initial synthetic USDT: `100.00000000`; verify against current API state).
- [ ] One previously staged `NEW` BTCUSDT order appears; no fill is inferred.
- [ ] **Paper evaluation** displays persisted fill count, turnover and fees accurately.
- [ ] For zero fills, no realized P&L, equity returns, risk or benchmark performance is invented; limitations are visible.
- [ ] Refresh retains read-only behavior; no network request submits a paper order.
- [ ] Browser network requests to wallet endpoints are localhost-only.
- [ ] Record browser/device, commit SHA, date and screenshots (exclude secrets, tokens and personal data).

## Open blockers

The local demo bypass is not multi-user authentication. Do not publish the unauthenticated wallet API or claim production readiness. Browser smoke is not automated E2E testing. Mobile smoke, recoverability, resource benchmarks and independent performance evidence remain outstanding.

Record results in the M8 PR; keep `docs/MILESTONES.md` unchanged.
