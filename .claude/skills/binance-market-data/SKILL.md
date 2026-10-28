---
name: binance-market-data
description: Implement or modify CoinPulse's Binance public market-data client (REST polling via data-api.binance.vision): DTOs, BigDecimal parsing, repository, error/rate-limit/retry handling, freshness, and fixture-based tests. Use for any price, ticker, or kline data work. Never for accounts, trading, or API keys.
---

# Binance public market data

Source of truth: https://developers.binance.com/en/docs/products/spot/rest-api and the market-data-only page (https://developers.binance.com/docs/binance-spot-api-docs/faqs/market_data_only). Re-check the docs before relying on any endpoint, parameter, weight, or field not listed below; this skill was written from those pages and may drift.

## Scope and hard limits
- **Public market data only.** No API keys, signatures, `X-MBX-APIKEY` header, account/user-data endpoints, order endpoints, or trading permissions. No secrets in code, `local.properties`, or Git.
- **REST polling for the MVP.** WebSocket streams (`data-stream.binance.vision`) are a separate, later enhancement; do not mix them into the first implementation.
- Base URL: `https://data-api.binance.vision` (documented market-data-only host). It does not serve user data streams. Keep the base URL injectable so tests can point at a fake server.

## Endpoints (documented as available on the market-data-only host)
| Need | Endpoint | Notes (from docs) |
|---|---|---|
| Connectivity check | `GET /api/v3/ping`, `/api/v3/time` | `time` gives server clock; useful for skew checks |
| Watchlist prices | `GET /api/v3/ticker/price?symbols=[...]` | weight 2 for one symbol, 4 for `symbols`/omitted |
| Price + 24h change | `GET /api/v3/ticker/24hr?symbols=[...]&type=MINI` | weight 2 for 1–20 symbols, 40 for 21–100, 80 for 101+/omitted. Prefer `type=MINI` when only price/change is shown (verify MINI field set in docs) |
| Charts | `GET /api/v3/klines?symbol&interval&limit` | weight 2, `limit` ≤ 1000; rows are 12-element arrays |
| Symbol validation | `GET /api/v3/exchangeInfo` | heavy; fetch rarely and cache; confirm weight/shape in docs first |

Always pass explicit `symbols` (never omit — omitting returns every market and costs the maximum weight). All price/quantity/volume fields are **JSON strings**; timestamps and counts are integers (ms since epoch).

## Layering (keep these separate)
1. **Remote/DTO** (`...data.remote`): `@Serializable`-style classes mirroring JSON exactly; prices as `String`. Kline rows need a custom decoder for heterogeneous arrays.
2. **Mapper**: DTO → domain; the *only* place strings become numbers. Throws/returns a typed parse failure on bad data.
3. **Domain** (`...domain`): `Coin`/`Ticker(symbol, lastPrice: BigDecimal, changePercent: BigDecimal, ...)`, `MarketSnapshot(items, fetchedAt: Instant)`. No Android or HTTP types.
4. **Repository interface** in domain; implementation in data. Exposes `suspend fun`/`Flow` returning a result type (e.g. `MarketResult.Success | Failure(kind)`), not raw exceptions.
5. **UI state** is built by ViewModels (MVVM, see root `CLAUDE.md`) from domain models (see `wear-ui`). ViewModels receive the repository interface by constructor and contain no HTTP, JSON, or persistence code. Add use cases only for reusable business rules, not as pass-through wrappers.
6. **Wiring**: construct the client and repository manually (e.g. in the `Application` or a ViewModel factory); no DI framework or service locator. A repository exposing polling should be cancellation-safe so the ViewModel can scope it to `viewModelScope`.

**Decided: OkHttp for HTTP** (no Ktor, no Retrofit) **and kotlinx.serialization for JSON**; use `MockWebServer` in tests. None are in `libs.versions.toml` yet: when first needed, confirm versions are compatible with the pinned Kotlin/AGP against official docs and add them via the version catalog only (the serialization Gradle plugin goes through the catalog too). Keep OkHttp types inside the data layer.

## Decimal correctness
- Parse with `BigDecimal(string)` (not `toBigDecimal()` on a `Double`, not `Double.parseDouble`). Reject blank/`NaN`/non-numeric strings with a parse error.
- Do arithmetic (percent change, conversions) in `BigDecimal` with an explicit `MathContext`/scale and `RoundingMode`. Compare with `compareTo`, never `equals` (scale differs).
- Convert to `Double` only at the very edge for chart plotting, and document it.
- Prefer Binance's own `priceChangePercent` over recomputing; if recomputing, test it.

## Time and freshness
- Store `fetchedAt: Instant` (from an injectable `Clock`) on every snapshot; keep server `closeTime`/`openTime` as separate `Instant`s when used.
- Define staleness in one place (e.g. `isStale(now, fetchedAt, maxAge)`), configurable and unit tested. UI decides how to display it; the repository only reports it.
- Never present cached data as fresh; keep the last good snapshot and mark it stale on refresh failure.

## Errors, retries, rate limits
Map to a closed set of kinds (sealed type): `Offline`, `Timeout`, `RateLimited(retryAfter)`, `Banned(retryAfter)`, `ClientError`(4xx, non-retryable), `ServerError`(5xx), `Malformed`(parse/shape), `Unknown`.
- Binance error body: `{"code": -1121, "msg": "Invalid symbol."}`; parse it, surface the code in logs, show friendly text in UI.
- **429**: back off and stop; honour `Retry-After` (seconds). **418**: IP auto-ban; honour `Retry-After`, do not retry before it elapses. **403**: may be a WAF block; treat as non-retryable. Watch `X-MBX-USED-WEIGHT-*` headers and throttle proactively.
- Retry only retryable failures (I/O errors, timeouts, 5xx, 429 after `Retry-After`): at most 3 attempts, exponential backoff with jitter, a cap on total delay. Never retry 4xx (other than 429), malformed JSON, or cancellation.
- Always rethrow `CancellationException`; don't swallow it in a generic `catch`.
- Set explicit connect/read/call timeouts. Polling interval must respect weight budget (state the arithmetic in a comment); the poller is lifecycle-scoped and stops when the UI is not visible.
- Offline detection comes from the actual failure; don't rely only on a connectivity pre-check.

## Tests (deterministic, no live network in unit tests)
- JSON fixtures under `src/test/resources/binance/` copied from documented example shapes: ticker price, 24hr (FULL and MINI), klines, error body, malformed/truncated, empty array.
- Use `MockWebServer` or a fake engine with a fixed `Clock`; no `Thread.sleep` — use coroutine test dispatchers/virtual time for backoff.
- Also unit-test the consuming ViewModel against a fake repository (loading → success, empty, stale, error mapping).
- Assert: exact `BigDecimal` values for high-precision strings like `"0.00000012"` and `"67123.45000000"`; unknown extra fields tolerated; missing required fields → `Malformed`; each HTTP status → expected kind; `Retry-After` honoured; retry count bounded; cancellation propagates; stale flag logic.
- Optional manual smoke check against the live API (curl `https://data-api.binance.vision/api/v3/ticker/price?symbol=BTCUSDT`) is allowed but must be reported as manual, not as a test.

## Checklist
- [ ] Only market-data host/endpoints; no credentials anywhere.
- [ ] DTO / domain / repository / UI state separated.
- [ ] No `Double` in money paths.
- [ ] Bounded retries, `Retry-After` honoured, cancellation safe.
- [ ] Fixtures + tests for parsing, precision, errors, repository.
- [ ] Run via `verify-android`.

## Common failure modes
- Omitting `symbols` (max weight) or polling too fast → 429/418.
- Treating string `"1.10"` and `"1.1"` as unequal (`equals`) or converting through `Double`.
- Retrying 4xx or looping on a 418.
- Swallowing `CancellationException`, leaking polling after the screen closes.
- Hard-coding the base URL so tests need the real network.
