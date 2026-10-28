# CoinPulse

Crypto market tracker for Wear OS with a planned Android phone companion. Shows public Binance market data (prices, 24h change). Public data only: no API keys, accounts, trading, or orders.

## Current state
- Single module `:app` (the **watch** app), package `com.example.coinpulse`: `domain/`, `data/` (`remote/` OkHttp Binance client + DTOs, `BinanceWatchlistRepository`), `presentation/` (`theme/`, `watchlist/`), manual `AppContainer`. Watchlist dashboard (tap a coin for the detail screen with a 24h sparkline from klines; swipe right to go back, navigation via the official SwipeToDismissBox pattern, no nav library) shows live BTC, ETH, SOL, XRP, ADA (USDT) from `data-api.binance.vision` (auto-polls every 30s while the screen is visible, plus manual Refresh; pauses 5 min after a 429/418). OkHttp 5 + kotlinx.serialization are in the version catalog. No phone module or sync yet.
- Toolchain (see `gradle/libs.versions.toml`, the source of truth): AGP 9.4.1, Kotlin 2.2.10, Gradle 9.6.0 wrapper, Wear Compose Material 3 1.5.6, minSdk 30 / compileSdk and targetSdk 37.
- Update this section when modules or versions change.

## Architecture: MVVM (mandatory, watch and phone)
- Kotlin, Jetpack Compose (Wear Compose Material 3 on the watch), coroutines + Flow.
- **View**: Compose screens observe ViewModel state and send user actions to it. No network calls or business logic in composables.
- **ViewModel**: AndroidX `ViewModel` + coroutines. Exposes immutable `StateFlow<UiState>`; no Android views, HTTP, JSON, or persistence code inside. Handle coroutine cancellation correctly.
- **Domain**: models always; use cases only where they carry reusable business rules. Financial calculations and rules stay out of composables.
- **Data**: repository interfaces + implementations; Binance client, DTOs, and mappers live in data packages/modules.
- **DI**: manual constructor injection into ViewModels and repositories (via ViewModel factories). A plain `AppContainer` created in the `Application` class holds singletons (OkHttpClient, repository, Clock) and is passed explicitly, not accessed globally. No DI framework (Hilt/Koin/Dagger), service locator, or generic base ViewModel without a demonstrated need.
- **Networking**: OkHttp + kotlinx.serialization, confined to `data/remote`; retries bounded in `BinanceMarketApi`.
- **State**: loading, success, empty, stale-data, and error are explicit in `UiState`.
- **Modules**: phone and watch each apply MVVM with their own presentation layer. Share only non-UI code where responsibility and platform justify it; don't force composables/ViewModels into a shared module to avoid duplication. Add a phone module only after asking.
- **Phone ↔ watch**: Wear OS Data Layer only for watchlist/selection/alert config, behind an abstraction (never used directly by composables or ViewModels). Market data is fetched directly by each app.
- **Tests**: ViewModels and repositories are unit-tested with fakes; Compose UI/instrumented tests only for behavior needing Android.
- Before adding any new abstraction, state the problem it solves. Avoid speculative modularization and oversized classes.
- The `androidx.lifecycle` ViewModel/Compose and coroutines-test dependencies are not yet in the version catalog; add them via `libs.versions.toml` when first needed.

## Rules
- Money/prices are `BigDecimal` parsed from strings, never `Double` arithmetic.
- Binance base URL: `https://data-api.binance.vision` (REST polling for MVP; WebSockets later).
- Dependencies go through `gradle/libs.versions.toml`; keep the set minimal; don't bump versions to fix a build without asking.
- Never commit secrets, signing keys, or `local.properties`.
- Preserve uncommitted user changes; no destructive git operations.
- Never claim a build/test/emulator check passed unless it ran and exited 0.

## Skills (`.claude/skills/`)
- `wear-ui`: Compose UI for Wear/phone, states, accessibility.
- `binance-market-data`: API client, parsing, errors, retries.
- `phone-watch-sync`: Data Layer contracts and reliability.
- `verify-android`: build/test/lint/emulator verification and honest reporting.
Also use the installed `android-skills:*` (e.g. `wear-compose-m3`, `testing-setup`) rather than duplicating them.

## Commands (Windows: `gradlew.bat` in PowerShell, `./gradlew` in Git Bash)
- Build: `./gradlew :app:assembleDebug`
- Lint: `./gradlew :app:lintDebug`
- Unit tests: `./gradlew :app:testDebugUnitTest` (once tests exist)
- UI tests: `./gradlew :app:connectedDebugAndroidTest` (needs a Wear emulator/device)
- Not yet confirmed to work in this environment; run `verify-android` to establish a baseline. Android SDK path lives in git-ignored `local.properties`; `adb` is not on PATH (use `<sdk.dir>/platform-tools/adb`).
- No formatter is configured.
