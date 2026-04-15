# GMediaHud

GMediaHud is an Android in-vehicle media HUD app built with Jetpack Compose. It synchronizes media metadata with the head unit, exposes car and media integration through vendor libraries, and uses system services (notification listener, accessibility) where needed.

## Support the project

If this app helps you, you can support further development, maintenance, and new features.

[![Donate](https://img.shields.io/badge/Donate-CloudTips-orange?style=for-the-badge)](https://pay.cloudtips.ru/p/19d38600)

### Crypto
- **BTC:** `bc1q37z3d7avhsq3ehpsjm2wldj86ajsnsd6gqnkzm`
- **ETH:** `0x69C73C422FEBBf12F47C29C51501Ad659fcdf74A`

Thanks for supporting the project.

## What This Project Does

- Presents the main HUD and settings UI in `MainActivity` using Compose (Material 3).
- Persists user preferences with AndroidX DataStore (`DataStoreManager`).
- Listens to media notifications via `MyNotificationListenerService` to drive on-screen track info and related behavior.
- Uses `BootAccessibilityService` for integration that depends on accessibility (user must enable it in system settings).
- Receives broadcast actions (`SHOW`, `HIDE`, `UPDATE_AUDIO_SOURCE`) through `BackgroundTaskReceiver` for external control flows.
- Integrates Firebase Analytics and Crashlytics for usage and crash reporting.
- Ships vendor-facing APIs as separate library modules; release builds can compile against them without embedding full implementations (see module notes below).

## Tech Stack

- Kotlin + Coroutines + Flow
- Jetpack Compose (Material 3, Navigation Compose)
- AndroidX DataStore (preferences)
- Coil (image loading)
- Kotlin Serialization (JSON)
- Timber (logging)
- Firebase (Analytics, Crashlytics)

## Module Structure

This is a multi-module Gradle project:

**Application**

- `app` — Application entry (`App`, `MainActivity`), Compose UI, services (`MyNotificationListenerService`, `BootAccessibilityService`), receivers, and app-level logic.

**Vendor / platform libraries**

These modules wrap OEM or platform APIs. The `app` module wires them so **release** builds use `releaseCompileOnly` (compile-time only, not packaged as full runtime deps in the same way as debug), while **debug** builds use `implementation` for local testing — see `app/build.gradle.kts` for the exact dependency split.

- `adaptapi` — EcarX AdaptAPI–style interfaces.
- `ecarx_car` — EcarX car / vehicle hardware signals.
- `ecarx_fw` — EcarX framework-facing pieces.
- `com_geely` — Geely OneOS / media-center related APIs.
- `tencent` — Tencent component service constants / integration surface.

## Requirements

- JDK 17 (as configured for the `app` module)
- Android SDK (compileSdk 35, targetSdk 35, minSdk 26)
- Gradle Wrapper (`gradle-8.10.2`)
- Android Studio / IntelliJ with Android support
- For Firebase: a valid `google-services.json` in the `app` module (as required by the Google Services plugin)

## Quick Start

1. Clone the repository.
2. Open the project in Android Studio.
3. Ensure Android SDK API 35 is installed and configured.
4. Build debug:

```bash
./gradlew :app:assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew :app:assembleDebug
```

## Useful Commands

- Build:
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:assembleRelease`
- Release pipeline (as defined in `app/build.gradle.kts`; assembles release):
  - `./gradlew prepareRelease`

## Release Signing

The project supports external signing configuration:

1. Create `secure.signing.gradle` (or point to a custom path via `secure.signing=...` in `gradle.properties`).
2. Add signing parameters in that file.
3. Run:

```bash
./gradlew prepareRelease
```

> If `secure.signing` is not set or the file is missing, signing behavior follows your default Android Gradle setup.

## UI Flow

- `MainActivity` hosts the Compose UI (edge-to-edge, custom theme under `ui/theme`).
- There is a single-activity structure; navigation and state are handled in Compose with lifecycle-aware collection of preferences and flows.

## Permissions and System Notes

The app uses (among others) the following capabilities:

- `INTERNET`, `ACCESS_NETWORK_STATE`
- **Notification listener service** — user must grant notification access in system settings for full behavior.
- **Accessibility service** — user must enable the app’s accessibility service when required by your deployment.

A `FileProvider` is declared for sharing files where needed. Some features assume an automotive / IVI environment; vendor modules target specific OEM stacks.

## Notes

- Vendor-specific code lives in `adaptapi`, `ecarx_car`, `ecarx_fw`, `com_geely`, and `tencent`; keep changes there isolated when porting to other platforms.
- The manifest includes queries such as `com.estrongs.android.pop` (ES File Explorer); the app may warn when that package is present.
