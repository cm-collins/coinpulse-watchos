---
name: phone-watch-sync
description: Implement or review CoinPulse phone-to-watch communication with the Wear OS Data Layer (watchlist, selected assets, alert config, status): choosing MessageClient vs DataClient, typed versioned contracts, availability/reconnect handling, and tests. Use when touching play-services-wearable code or any phone/watch shared contract.
---

# Phone ↔ watch sync (Wear OS Data Layer)

Sources: https://developer.android.com/training/wearables/data/overview, `/data/messages`, `/data/data-items` (read when this skill was written), plus the Google Play services Wearable API reference for exact class semantics. Re-check the reference for anything below marked *verify*.

## Before any code
1. **Inspect actual modules and IDs.** Read `settings.gradle.kts`, each module's `build.gradle.kts`, and manifests. Record module names and `applicationId`s. Initial state when this skill was written: only `:app` (the watch app, `com.example.coinpulse`, `standalone=true`); no phone module existed. Do not invent a phone module name or ID; if absent, ask before creating one.
2. **Data Layer requires matching package name and signing signature** on both apps (per the overview). Phone and watch must use the same `applicationId` unless the docs say otherwise for the use case; debug builds must be signed with the same key. Confirm this before debugging "messages not arriving".
3. **Is the Data Layer even appropriate?** It only works between Wear OS and a paired *Android* phone (not iOS). The docs say it is for strictly watch↔phone interactions, and not a replacement for the app's own network access: the watch already polls Binance itself, so market data does not go through the Data Layer.
4. Add `play-services-wearable` only to modules that use it, via the version catalog (already declared as `playServicesWearable`; reuse that entry, don't add a second version).

## What goes over the wire (and what doesn't)
Sync: watchlist (ordered symbol list), selected/favourite asset, price-alert *configuration* (symbol, threshold as decimal string, direction, enabled), simple status (companion installed, last sync, schema version).
Do **not** sync: live prices, klines, or anything refreshed every few seconds; each side fetches those from Binance. Do not use it as a backend, and do not send secrets.

## Choosing the API
| Information | API | Why |
|---|---|---|
| Durable state both sides should converge on (watchlist, alert config, settings) | `DataClient` (`putDataItem`, `PutDataMapRequest`) | Persisted and synced; buffered and delivered after reconnect. Payload ≤ 100 KB; non-urgent items may be delayed (up to ~30 min per docs); call `setUrgent()` only when UX needs it |
| Transient command or request ("refresh now", "open alert screen", ping) | `MessageClient` | Best-effort, no retry, no persistence, needs a connected node (`TARGET_NODE_NOT_CONNECTED`); small payload |
| Large file/stream | `ChannelClient` (avoid unless truly needed) | For payloads too large for messages |
| Does the counterpart app exist? Which node? | `CapabilityClient` (declare a capability in `res/values/wear.xml`) and `NodeClient` | Find nodes by capability, not by assuming "the" phone |

Default for CoinPulse: **DataClient for watchlist/selection/alert config**, MessageClient only for transient nudges.

## Contract design
- One versioned schema per payload kind, owned by a shared place both modules can depend on (a small shared Kotlin module or duplicated tested codec if no shared module exists — ask first). Fields: `schemaVersion: Int`, `updatedAt` (epoch ms), a `revision`/`sequence` or `messageId`, payload fields. Decimals as strings.
- Stable path constants (`/coinpulse/v1/watchlist`, …) in one object. Use `DataMap`/serialization behind a codec class with `encode(domain) -> ByteArray/DataMap` and `decode(...) -> Result<Domain>` — decoding must never throw into a listener.
- Unknown newer `schemaVersion` → ignore safely and report a status; older/missing fields → defaults. Unknown extra fields are ignored.
- Conflict policy: define it explicitly (e.g. last-writer-wins by `updatedAt`, tie-break by node id). Dedupe by `revision`/`messageId` — Data Layer delivery can repeat events.

## Transport isolation
- MVVM is mandatory (root `CLAUDE.md`): the Data Layer is a data-layer detail. Define small domain-level interfaces (e.g. `WatchlistSync`, `CompanionStatus`) with `Flow`/`suspend` APIs, implemented in the data layer on top of `DataClient`/`MessageClient`/`CapabilityClient`. Composables and ViewModels never import `com.google.android.gms.wearable.*`; ViewModels get the interface by constructor injection and expose sync status as part of `UiState`. Provide fakes for previews and ViewModel tests.
- Each app (phone, watch) keeps its own ViewModels and presentation. Share only the contract/codec (non-UI) and only if a shared module is justified; ask before creating one.
- Wrap Play-services `Task`s with `await()` (kotlinx-coroutines-play-services) only if that dependency is added; otherwise use `suspendCancellableCoroutine`. Rethrow `CancellationException`.

## Reliability rules
- **Check availability first:** `GoogleApiAvailability.checkApiAvailability(client)`; catch `ApiException`/`AvailabilityException` and degrade gracefully (feature off, UI says "phone unavailable").
- Never assume the phone is connected, installed, or running; the watch app must work standalone with its last stored watchlist.
- **Delivery honesty:** a successful `Task` from `sendMessage`/`putDataItem` means the request was accepted locally (*verify exact semantics in the reference*), not that the peer received or applied it. Expose states like `Queued`/`SentLocally`; only report `Acknowledged` after an explicit application-level reply (`sendRequest` response or a status DataItem written by the peer). Never display "delivered" otherwise.
- Handle `TARGET_NODE_NOT_CONNECTED` and other failures with bounded retry/backoff, or by relying on DataItem buffering instead of retrying.
- Listeners: register in a lifecycle-aware scope (`onResume`/`onPause` for activities per docs, or a manifest-declared `WearableListenerService` for background events; services must be light and hand work to a scoped coroutine). Always unregister; no leaked listeners. Clients are cheap; don't cache them in static fields.
- On reconnect, re-read current DataItems (`getDataItems`) to reconcile instead of trusting that no events were missed.
- Permissions: Data Layer needs no runtime permission; add none. Don't add a backend or cloud service.

## Tests
- Pure unit tests (no Play services): codec round-trip, version negotiation, missing/extra/invalid fields, oversized payload rejection, dedupe/conflict resolution, "decode failure never throws".
- Fake `WatchlistSync` transport: disconnected → local state still works, status shows unavailable; reconnect → reconciliation; duplicate event applied once.
- Real two-device/emulator pairing checks are manual; report them as manual via `verify-android`, with exact steps and results.

## Checklist
- [ ] Module names/applicationIds read from the repo, not assumed.
- [ ] Same package + signing on both apps.
- [ ] Right client per payload; no market data over the wire.
- [ ] Versioned codec, safe decode, dedupe, defined conflict rule.
- [ ] Availability check, listener cleanup, standalone fallback.
- [ ] UI depends on interface only; no unnecessary permissions.

## Common failure modes
- Pair not recognised: mismatched `applicationId` or signing key, or emulator pair not set up.
- Using MessageClient for state, then losing it while disconnected.
- Reporting "synced" after only a local `Task` success.
- Throwing from a listener on a malformed payload; leaking listeners after `onPause`.
- Assuming the Data Layer works with an iOS-paired watch.
