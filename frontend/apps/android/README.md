# Pantopus Android

Native Android app for Pantopus, built with Kotlin 2.0, Jetpack Compose, and Material 3.

## Stack

| Layer          | Choice                                                                    |
|----------------|---------------------------------------------------------------------------|
| Language       | Kotlin 2.0 (K2 compiler)                                                  |
| Min / Target   | minSdk 26 (Android 8.0), compileSdk 34, targetSdk 34                      |
| UI             | Jetpack Compose + Material 3                                              |
| Navigation     | Navigation Compose                                                        |
| DI             | Hilt (Dagger 2)                                                           |
| Networking     | Retrofit + OkHttp + Moshi                                                 |
| Async          | Kotlinx Coroutines + Flow                                                 |
| Persistence    | DataStore (Preferences) for tokens                                        |
| Realtime       | [socket.io-client](https://github.com/socketio/socket.io-client-java)     |
| Payments       | [Stripe Android SDK](https://github.com/stripe/stripe-android)            |
| Maps           | Google Maps Compose                                                       |
| Image loading  | Coil                                                                      |
| Logging        | Timber                                                                    |
| Build          | Gradle 8.9 + Kotlin DSL + version catalog (`libs.versions.toml`)          |
| Lint / format  | detekt, ktlint                                                            |
| Testing        | JUnit4, MockK, Turbine, Compose UI Test, Espresso                         |

## Layout

```
frontend/apps/android/
├── settings.gradle.kts           # Multi-module declaration, repo config
├── build.gradle.kts              # Top-level plugins, detekt + ktlint
├── gradle.properties             # JVM args, AndroidX flags, Kotlin style
├── gradle/
│   ├── libs.versions.toml        # Version catalog — single source of truth
│   └── wrapper/                  # Gradle wrapper (committed)
├── gradlew, gradlew.bat          # Wrapper launchers
├── config/
│   └── detekt.yml                # Detekt rules
├── .env.example                  # Local env template (copy to .env)
├── app/
│   ├── build.gradle.kts          # App module build script
│   ├── proguard-rules.pro        # R8 keep rules for release builds
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/app/pantopus/android/
│       │   │   ├── PantopusApplication.kt   # Hilt entry
│       │   │   ├── MainActivity.kt          # Single-activity Compose host
│       │   │   ├── ui/                      # Compose screens, theme, nav
│       │   │   ├── data/                    # Retrofit API, auth, realtime
│       │   │   ├── domain/                  # Domain models (add as needed)
│       │   │   └── di/                      # Hilt modules
│       │   └── res/                         # Resources, themes, icons
│       ├── test/                            # Unit tests (JVM)
│       └── androidTest/                     # Instrumented tests (device/emulator)
└── README.md
```

## Prerequisites

- **JDK 17** (Temurin recommended)
- **Android Studio** Hedgehog (2023.1.1) or later
- **Android SDK 34** (installed via Android Studio's SDK Manager)

On macOS:
```bash
brew install --cask temurin@17 android-studio
```

## First-time setup

```bash
cd frontend/apps/android
cp .env.example .env                # Edit with your backend URL + Stripe key
./gradlew --version                 # Downloads Gradle 8.9 wrapper
./gradlew assembleDebug             # First build (~3-5 min on a clean machine)
```

Or open `frontend/apps/android/` in Android Studio and let it sync.

## Common tasks

| Task                              | Command                                                      |
|-----------------------------------|--------------------------------------------------------------|
| Build debug APK                   | `./gradlew assembleDebug`                                    |
| Install on running emulator/device| `./gradlew installDebug`                                     |
| Build release bundle (AAB)        | `./gradlew bundleRelease`                                    |
| Run unit tests                    | `./gradlew test`                                             |
| Run instrumented tests            | `./gradlew connectedAndroidTest`                             |
| Lint (detekt)                     | `./gradlew detekt`                                           |
| Lint (ktlint)                     | `./gradlew ktlintCheck`                                      |
| Format (ktlint)                   | `./gradlew ktlintFormat`                                     |
| Clean                             | `./gradlew clean`                                            |

## Architecture

The app follows a pragmatic 3-layer MVVM split:

- **`ui/`** — Composable screens and Hilt-injected ViewModels. Each screen owns a `UiState` data class exposed via `StateFlow`. ViewModels never import Retrofit or DataStore directly — they go through the data layer.
- **`data/`** — `ApiService` (Retrofit interface), `AuthRepository` (session orchestration), `TokenStorage` (DataStore-backed token persistence), `AuthInterceptor` (OkHttp interceptor that attaches the bearer token and handles 401s), `SocketManager` (Socket.IO wrapper).
- **`di/`** — Hilt modules. Currently just `NetworkModule` wiring Moshi / OkHttp / Retrofit / ApiService. Add module-scoped bindings as the app grows.

### BuildConfig fields

`app/build.gradle.kts` reads `.env` at configure time and injects these into `BuildConfig`:

- `BuildConfig.PANTOPUS_API_BASE_URL`
- `BuildConfig.PANTOPUS_SOCKET_URL`
- `BuildConfig.STRIPE_PUBLISHABLE_KEY`

The Maps API key goes into the manifest via `${MAPS_API_KEY}` placeholder — it never hits BuildConfig because the Maps SDK reads it from `AndroidManifest.xml`.

### Networking to localhost from the emulator

The Android emulator cannot reach `localhost` — that points at the emulator itself. Use `http://10.0.2.2:8000` to hit the host machine. The `.env.example` already sets this. For a physical device on the same LAN, use your Mac's LAN IP. For the default debug config we enable cleartext HTTP (`usesCleartextTraffic="true"` in the manifest) — turn this off for release builds, which should only hit HTTPS anyway.

## Adding a new feature

1. Create `ui/screens/your_feature/` with `YourFeatureScreen.kt` and `YourFeatureViewModel.kt`.
2. Add the Retrofit endpoint to `data/api/ApiService.kt` and any DTOs to `data/api/models/Models.kt`.
3. Add a route to `ui/navigation/PantopusNavHost.kt`.
4. If a new library is needed, add the version to `gradle/libs.versions.toml` under `[versions]`, the library under `[libraries]`, and reference it in `app/build.gradle.kts`.

## Push notifications

The `POST /api/notifications/register` endpoint takes `{token, platform}` — the app should register with `platform: "android"` and an FCM token. FCM wiring (Firebase `google-services.json` + the `com.google.gms.google-services` plugin) is **not** included in this scaffold yet; add it when ready. Backend-side, see [`docs/push-native-migration.md`](../../../docs/push-native-migration.md) for the plan to migrate from Expo's push infrastructure to direct FCM + APNs.

## Release signing

Don't commit your keystore. The recommended flow:

1. Generate a keystore: `keytool -genkey -v -keystore pantopus.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pantopus`.
2. Put the path + passwords in `~/.gradle/gradle.properties`:
   ```
   PANTOPUS_KEYSTORE_FILE=/absolute/path/to/pantopus.jks
   PANTOPUS_KEYSTORE_PASSWORD=...
   PANTOPUS_KEY_ALIAS=pantopus
   PANTOPUS_KEY_PASSWORD=...
   ```
3. Wire a `signingConfigs { release { … } }` block into `app/build.gradle.kts` that reads those properties and attach it to `buildTypes.release`.
4. `./gradlew bundleRelease` produces a signed AAB.

For automated Play Store uploads, add the [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) plugin and a service account JSON (also not committed).

## Troubleshooting

| Symptom                                            | Fix                                                                   |
|----------------------------------------------------|-----------------------------------------------------------------------|
| `SDK location not found`                           | Create `local.properties` with `sdk.dir=/absolute/path/to/Android/sdk`|
| `Unable to load class 'io.socket.client.IO'`       | Confirm JitPack is in `settings.gradle.kts` (it is)                   |
| Emulator can't reach backend at `localhost:8000`   | Use `http://10.0.2.2:8000` in `.env`                                  |
| `Cleartext HTTP ... not permitted`                 | Keep `usesCleartextTraffic="true"` for debug, hit HTTPS in release    |
| First build is very slow                           | Expected — BOM + Kotlin + KSP first-time compile. Second build ~30s.  |
