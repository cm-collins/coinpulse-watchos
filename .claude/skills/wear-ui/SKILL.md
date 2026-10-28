---
name: wear-ui
description: Build or review CoinPulse Compose UI for round Wear OS screens and the Android phone companion (price lists, coin detail, loading/stale/error/empty states, navigation, accessibility, previews). Use when creating or changing any screen, composable, theme, or UI test.
---

# CoinPulse UI (Wear OS first, phone second)

This skill covers *presentation only*. For general Wear Compose Material 3 API usage (AppScaffold, ScreenScaffold, TransformingLazyColumn, ambient, migration) defer to the installed `android-skills:wear-compose-m3` skill; for phone adaptive layout use `android-skills:adaptive`. Do not restate them here.

## Before writing UI
1. Read the existing screens and `presentation/theme/Theme.kt`; extend them instead of introducing a parallel style. Check `gradle/libs.versions.toml` for the Wear Compose versions actually in use (do not assume APIs from a newer release).
2. Check which modules exist. At the time this skill was written only the watch module `:app` (package `com.example.coinpulse.presentation`) existed; there was no phone module. If a phone UI is requested and no phone module exists, stop and ask before scaffolding one.
3. Look up any version-sensitive component in official docs (developer.android.com/design/ui/wear, developer.android.com/training/wearables) rather than recalling from memory.

## Visual language
Follow `docs/DESIGN.md` ("Obsidian": OLED black, restrained palette, Roboto Flex, Material Symbols, gain/loss always paired with sign + arrow). It is a proposal until implemented; update it when the theme code changes and re-check contrast for any new color.

## Design rules
- **Glanceable**: one primary task per screen (see current price movement). A reading should take seconds. Push configuration, long lists of settings, and anything typing-heavy to the phone.
- **Round-screen safety**: use `ScreenScaffold` + `TransformingLazyColumn` with the `contentPadding` the scaffold provides; do not hand-pad to corners. Never place critical text in the extreme corners. Test on a small round and a large round preview.
- **Touch targets and type**: prefer stock Material 3 Wear components (their defaults meet size guidance). For custom clickable elements, make them at least 48dp and keep them separated. Use theme typography, no hard-coded `sp`; support large font scale by letting text wrap/ellipsize and by previewing with `@WearPreviewFontScales`.
- **Color is not the only signal**: price up/down must also be conveyed by a sign (`+`/`−`), arrow icon, or text, and have a content description ("Up 2.31 percent"). Use theme color roles for contrast; no raw hex in screens.
- **Ambient / battery**: avoid continuous animations; handle ambient mode per the wear-compose-m3 skill.

## Presenting market data
- Show: base symbol (`BTC`) prominently, full pair only in detail; last price; 24h percent change with sign; optional "updated hh:mm" when stale.
- Format with `BigDecimal`/`NumberFormat` (locale-aware), not `Double` arithmetic. Formatting lives in a small, unit-testable formatter, not inside composables. Choose decimal places by price magnitude (a sub-cent coin needs more digits than BTC); never round away significant digits silently.
- Model every screen with an explicit, sealed UI state: `Loading`, `Content(items, lastUpdated, isStale)`, `Empty`, `Error(kind, hasCachedContent)`. Rules:
  - Loading: skeleton/indeterminate indicator, no fake prices.
  - Stale: keep showing last data, mark it ("Updated 5 min ago"), dim lightly; do not blank the screen.
  - Error with cache: non-blocking message + retry action. Error without cache: full message + single retry button.
  - Empty (no watchlist / no results): explain and offer the one next action.
  - Messages come from `strings.xml` (no hard-coded user text) and distinguish offline vs. server problem vs. rate limited, using the error kinds the data layer exposes.

## Architecture boundaries
- **MVVM is mandatory** (see root `CLAUDE.md`). A screen has a stateful entry composable that obtains its `ViewModel`, collects `StateFlow<UiState>` (`collectAsStateWithLifecycle` once the lifecycle-runtime-compose dependency is added via the catalog), and passes plain state + lambdas to a stateless content composable (the one previewed and UI-tested).
- Composables never touch HTTP clients, Binance DTOs, Data Layer clients, or repositories, and never launch network work. User actions go to the ViewModel as functions/events.
- ViewModels expose immutable `StateFlow<UiState>`, hold no Android view or Context references, and receive dependencies by constructor (factory-created; no DI framework or generic base ViewModel).
- Business logic (change calculation, staleness thresholds, sorting) belongs in domain/ViewModel code with unit tests, not in composables.
- Watch and phone each get their own presentation layer; don't move composables or ViewModels into a shared module just to avoid duplication.
- Navigation: keep it shallow (list → detail, maybe one settings screen). Use the navigation approach already in the repo; if none, consult `android-skills:navigation-3` and the Wear navigation docs before adding a dependency. Swipe-to-dismiss must keep working; do not override back handling without reason.

## Previews and tests
- Every screen composable gets a `@WearPreviewDevices` and `@WearPreviewFontScales` preview for each state (loading, content, stale, empty, error), driven by fake state objects.
- Compose UI tests (`ui-test-junit4`, already in the version catalog) assert visible text/semantics per state and that retry callbacks fire. Run them via the `verify-android` skill; do not claim they pass without running them.

## Checklist
- [ ] No new dependency unless existing ones cannot do it; if added, it goes in `libs.versions.toml`.
- [ ] All five states implemented and previewed.
- [ ] No `Double` for money; sign/icon plus color for direction.
- [ ] Strings externalized; content descriptions present.
- [ ] Composables have no knowledge of transport or API.

## Common failure modes
- Using Material 2 `androidx.wear.compose.material` components in a Material 3 project.
- Calling APIs that exist only in a newer Wear Compose release than the catalog pins.
- Text clipped on small round screens or at large font scale.
- Blank screen on refresh failure instead of stale-with-warning.
- Formatting prices inside composables, leading to untested rounding.
