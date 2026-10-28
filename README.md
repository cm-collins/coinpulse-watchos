# CoinPulse

A crypto market tracker for **Wear OS**, with a planned Android phone companion. It shows public cryptocurrency prices and 24h movements from Binance's public market-data API.

> **Status: early scaffold.** The repository currently contains the Android Studio Wear OS Compose starter (a placeholder screen). No Binance integration, data layer, phone app, or tests exist yet.

## Scope

- Public market data only. No Binance API keys, accounts, trading, or orders.
- REST polling via `https://data-api.binance.vision` for the MVP. WebSocket streaming is a possible later enhancement.
- Prices are handled as decimal-safe values (`BigDecimal`), never floating-point arithmetic.

## Tech stack

Versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml), which is the source of truth.

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Wear Compose Material 3 (watch) |
| Async | Kotlin coroutines and Flow |
| Build | Gradle (Kotlin DSL, wrapper), Android Gradle Plugin, version catalog |
| Phone ↔ watch | Wear OS Data Layer (`play-services-wearable`) |
| SDK levels | minSdk 30, compileSdk/targetSdk 37 |

## Architecture: MVVM

MVVM applies to both the watch and phone apps, each with its own presentation layer.

- **View**: Compose screens observe ViewModel state and send user actions back. No network calls or business logic in composables.
- **ViewModel**: AndroidX `ViewModel` exposing immutable `StateFlow<UiState>`. No HTTP, JSON, persistence, or view references.
- **Domain**: models, plus use cases only where they hold reusable business rules.
- **Data**: repository interfaces and implementations; Binance client, DTOs, and mappers live here.
- **Dependency injection**: manual constructor injection. No DI framework for now.
- **UI state**: loading, success, empty, stale-data, and error are explicit.
- **Phone ↔ watch**: the Data Layer sits behind an abstraction and carries only small durable state (watchlist, selected asset, alert configuration). Each app fetches market data itself.

## Modules

| Module | Role | State |
|---|---|---|
| `:app` | Wear OS app (`com.example.coinpulse`, UI in `presentation/`) | Starter template |
| phone companion | Android phone app | Not created yet |

## Development setup

Requirements:
- Android Studio with the Android SDK (the SDK path is stored in the git-ignored `local.properties`; Android Studio creates it).
- A JDK compatible with the Gradle wrapper and Android Gradle Plugin. Android Studio's bundled JBR works.
- A Wear OS emulator (API 30 or higher) or a watch, created via Android Studio's Device Manager.

Gradle commands (use `gradlew.bat` in PowerShell, `./gradlew` in Git Bash or macOS/Linux):

```
./gradlew :app:assembleDebug          # build
./gradlew :app:lintDebug              # lint
./gradlew :app:testDebugUnitTest      # unit tests (once tests exist)
./gradlew :app:connectedDebugAndroidTest   # UI tests (needs a running emulator/device)
```

These are the standard Android task names and have not yet been confirmed against this project. No code formatter is configured.

Run on an emulator by opening the project in Android Studio and launching the `app` configuration on a Wear OS virtual device. When a phone app is added, pair it with the watch emulator using Android Studio's Wear OS pairing assistant.

## Claude Code skills

Project skills live in [`.claude/skills/`](.claude/skills/):

- `wear-ui`: Wear/phone Compose UI and state presentation
- `binance-market-data`: Binance client, parsing, errors, retries
- `phone-watch-sync`: Data Layer contracts and reliability
- `verify-android`: build, test, lint, and emulator verification

Project conventions for contributors and AI assistants are in [`CLAUDE.md`](CLAUDE.md).

## Security

Do not commit secrets, signing keys, or `local.properties`.

## License

See [`LICENSE`](LICENSE).
