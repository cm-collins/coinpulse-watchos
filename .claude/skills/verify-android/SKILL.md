---
name: verify-android
description: Verify CoinPulse builds, unit tests, Compose UI tests, lint, and emulator runs, and report results truthfully with exact commands and exit codes. Use after any code change, before declaring work done, or when diagnosing a build/test/emulator failure.
---

# Verify CoinPulse (Android + Wear OS)

Core rule: **a check counts as passed only if you ran it and saw exit status 0.** If it was not run, say "not run" and why. Never summarize unrun checks as passing.

## 1. Inspect the environment first (read-only)
Run and record:
- `git status --short` (note pre-existing uncommitted changes — they may cause failures that are not yours).
- `cat settings.gradle.kts` — actual modules (initially only `:app`, the watch app; there may be no phone module).
- `gradle/gradle-wrapper.properties` (wrapper Gradle version), `gradle/libs.versions.toml` (AGP/Kotlin/Compose pins), `gradle/gradle-daemon-jvm.properties`.
- `java -version` and `echo $JAVA_HOME`: several JDKs may be on PATH (Android Studio JBR, Temurin 17, others). Gradle's required JDK is governed by the wrapper/AGP and daemon JVM settings; use the JDK Android Studio uses if the PATH default fails, and report which one.
- SDK: `local.properties` `sdk.dir` (machine-specific, git-ignored; never commit it). `ANDROID_HOME` may be unset; that is fine if `sdk.dir` exists. Check the directory exists and contains `platforms/`, `build-tools/`, `platform-tools/`, `emulator/`.
- Discover tasks, don't assume: `./gradlew projects` and `./gradlew :<module>:tasks --all` (use `gradlew.bat` in PowerShell).

## 2. Run checks, narrowest first
Use only tasks the listing shows. Typical order (each separately so failures are attributable):
1. Compile: `./gradlew :app:assembleDebug`
2. Unit tests: `./gradlew :app:testDebugUnitTest` (only if test sources exist)
3. Lint: `./gradlew :app:lintDebug`
4. Compose UI / instrumented tests: `./gradlew :app:connectedDebugAndroidTest` — requires a running device/emulator; skip honestly if none.
5. Optional release sanity: `./gradlew :app:assembleRelease` (R8 optimization is enabled in this project, which can expose keep-rule issues; no signing keys in Git).
Add `--stacktrace` only when diagnosing. Capture the exit code of each command and the key failure lines (first error, failing test names), not the whole log. No formatter task exists unless the build declares one (e.g. ktlint/spotless); do not claim formatting was checked otherwise.

Configuration cache is on (`org.gradle.configuration-cache=true`); if it reports a cache problem, report it as a build-config issue, don't disable it without saying so.

MVVM conformance review (read the diff, report findings; don't claim a tool enforced it): composables free of network/business logic and repository/Data Layer imports; ViewModels expose immutable `StateFlow<UiState>` with no Context/view or HTTP/JSON code; dependencies via constructors; cancellation not swallowed; ViewModel and repository tests use fakes and run as JVM unit tests (`testDebugUnitTest`), with `connectedDebugAndroidTest` only for Android-dependent behavior. Missing test dependencies (e.g. coroutines-test) are a catalog gap to report, not a reason to skip tests.

## 3. Emulator / device verification (when available)
- `adb` is not necessarily on PATH: use `<sdk.dir>/platform-tools/adb`. `adb devices -l` lists targets.
- AVDs: `<sdk.dir>/emulator/emulator -list-avds`. Wear OS needs a Wear system image (API ≥ minSdk 30). Phone checks need a separate phone AVD. Report which AVD/API/form factor (round/small round/large) was used.
- Install and launch: `./gradlew :app:installDebug`, then `adb shell am start -n com.example.coinpulse/.presentation.MainActivity` (confirm application ID and activity from the manifest/build file first). Check `adb logcat -d -s AndroidRuntime:E` for crashes; screenshots via `adb exec-out screencap -p > file.png` (save outside the repo).
- Pairing (phone↔watch Data Layer): must be done with Android Studio's Wear OS pairing assistant or documented AVD pairing; if no pair can be established, mark pairing checks "not verified: environment" rather than inferring.
- Installing via `android` CLI skill (`android-skills:android-cli`) is an acceptable alternative if installed; report the actual commands used.

## 4. Classify every failure
- **Environment**: no SDK/platform/build-tools, missing license acceptance, wrong JDK, no network for dependency download, no emulator/AVD, adb offline, virtualization unavailable. Report as *environment limitation*, give the fix, and do not edit app code to "fix" it.
- **Pre-existing code failure**: confirm the failing files are untouched by your changes (`git diff`), or reproduce on a separate `git worktree` of HEAD. Never `git stash`/`reset` the user's uncommitted work. Report, do not mask.
- **Regression from current changes**: fix at the root cause, rerun the failing task, then rerun the broader set.
- Never silence failures by disabling tests, loosening lint, adding `@Suppress`, or editing baselines unless the user asked, and say so if done.

## 5. Dependency compatibility
Do not bump versions to make a build pass. Read the error, then check official compatibility docs (AGP↔Gradle↔Kotlin↔JDK, Compose BOM / Wear Compose artifact versions). Note: the catalog pins Wear Compose artifacts individually while also applying a Compose BOM; mismatches between them are a known source of API errors — report them, propose a fix, and ask before upgrading.

## 6. Report format
Use a table:

| Check | Command | Exit | Result |
|---|---|---|---|
| Compile | `./gradlew :app:assembleDebug` | 0 | BUILD SUCCESSFUL |
| Unit tests | … | not run | no test sources exist |
| Emulator | … | not run | no Wear AVD installed (environment) |

Then list: failures with first error line, environment limitations, pre-existing issues, what you fixed, and remaining unverified items. State plainly what was not verified.

## Common failure modes
- `SDK location not found` → `local.properties` missing/incorrect (machine-specific; recreate locally, never commit).
- Unsupported Java version / toolchain not found → wrong JDK on PATH; Gradle toolchain resolution (foojay plugin) may need network.
- `connectedDebugAndroidTest` fails with "No connected devices" → environment, not code.
- Lint failures on `targetSdk`/new API warnings after adding a dependency → read `app/lint.xml` and report; don't blanket-disable.
- Windows paths: use `gradlew.bat` in PowerShell, `./gradlew` in Git Bash.
