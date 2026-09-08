# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

**BimmerLog Analyzer** — Android app that reads OBD logger CSV files (BMW and
generic) and renders interactive Speed / Torque / Horsepower charts. Files can
be opened from local storage, Microsoft OneDrive, or Google Drive.

## Build & Test

```bash
./gradlew assembleDebug        # build debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests (no test sources currently exist)
./gradlew lintDebug            # Android lint
```

- **JDK 17**, Gradle 8.7, AGP 8.5.0, Kotlin 2.0.0, min SDK 26 / target SDK 34.
- `gradlew` / `gradlew.bat` are the **official** Gradle wrapper scripts — do not
  hand-edit them (a hand-written wrapper previously broke CI with a JVM-opts
  quoting bug).
- No unit test source files exist yet (`src/test/` is empty). CI runs the
  Gradle task anyway; it succeeds when there are no tests.

## Repository Layout

```
BimmerLogAnalyzer/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json.template      # shape doc for Firebase config
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/bimmerloganalyzer/
│       │   ├── MainActivity.kt            # NavHost, external CSV intent handling
│       │   ├── cloud/
│       │   │   ├── CloudFolder.kt         # CloudFile, CloudFolder, CloudFolderContents
│       │   │   ├── OneDriveHelper.kt      # MSAL + Microsoft Graph REST
│       │   │   └── GoogleDriveHelper.kt   # Google Sign-In + Drive API v3
│       │   ├── data/
│       │   │   ├── OBDDataPoint.kt        # core model + HP math
│       │   │   ├── CsvParser.kt           # auto-column-detect CSV parser
│       │   │   ├── LogFileName.kt         # filename ↔ datetime utilities
│       │   │   └── LogSession.kt          # stats + downsampling wrapper
│       │   ├── viewmodel/
│       │   │   └── MainViewModel.kt       # UiState + FolderBrowseState (StateFlow)
│       │   └── ui/
│       │       ├── components/
│       │       │   └── OBDLineChart.kt    # MPAndroidChart Compose wrapper
│       │       ├── screens/
│       │       │   ├── HomeScreen.kt      # import source selection
│       │       │   ├── ChartScreen.kt     # 6 chart types with tab selector
│       │       │   └── CloudFolderBrowserDialog.kt
│       │       └── theme/
│       │           └── Theme.kt           # dark Material3 theme
│       └── res/
│           └── values/{strings,themes}.xml
├── gradle/
│   ├── libs.versions.toml                 # centralized version catalog
│   └── wrapper/gradle-wrapper.properties
├── .github/workflows/android-ci.yml
├── ExampleData/Log-2026-06-09--21-57-04.csv
├── msal_config.json.template              # shape doc for Azure/OneDrive creds
├── build.gradle.kts                       # root project gradle
├── settings.gradle.kts
└── gradle.properties
```

## Architecture

MVVM + Jetpack Compose, single-activity, Navigation Compose.

Data flow: source picked in `HomeScreen` → `MainViewModel` parses CSV via
`CsvParser` → `UiState.Success(LogSession)` → navigate to `ChartScreen`.

### Key Dependencies (libs.versions.toml)

| Library | Version |
|---------|---------|
| Compose BOM | 2024.06.00 |
| Navigation Compose | 2.7.7 |
| Lifecycle / ViewModel | 2.8.3 |
| Activity Compose | 1.9.0 |
| MPAndroidChart | v3.1.0 (JitPack) |
| MSAL | 5.3.0 |
| Google Play Services Auth | 20.7.0 |
| Google API Client Android | 2.2.0 |
| Google Drive API | v3-rev20240123-2.0.0 |
| Kotlinx Coroutines Android | 1.8.1 |
| DataStore Preferences | 1.1.1 |

Maven repositories: `google()`, `mavenCentral()`, JitPack, Azure Surface Duo SDK Maven.

### State Management (MainViewModel.kt)

```kotlin
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val session: LogSession) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class FolderBrowseState {
    object Idle
    object Loading
    data class PathInput(val source: CloudSource, val currentPath: String = "/")
    data class Browsing(val contents: CloudFolderContents, val source: CloudSource)
    data class Error(val message: String, val source: CloudSource)
}

enum class CloudSource { ONEDRIVE, GOOGLE_DRIVE }
enum class ChartType { SPEED_TIME, TORQUE_TIME, POWER_TIME, DYNO_CURVE, BOOST_TIME, TEMP_TIME }
```

StateFlows: `uiState`, `folderBrowseState`, `selectedChartType`.

### Navigation (MainActivity.kt)

Two routes in `NavHost`:
- `"home"` → `HomeScreen` — also handles `ACTION_VIEW` intents for CSV files opened
  from a file manager (intent filters for `text/csv` and `text/comma-separated-values`).
- `"chart"` → `ChartScreen` — only reachable when `UiState.Success`.

MSAL OAuth redirect activity (`BrowserTabActivity`) is registered in the manifest
with scheme `msauth://com.bimmerloganalyzer/PLACEHOLDER_HASH`.

## Domain Knowledge

### OBDDataPoint Fields

| Field | Type | Description |
|-------|------|-------------|
| `time` | Float | Seconds offset from log start |
| `gear` | Float | |
| `speedKmh` | Float | |
| `rpm` | Float | |
| `torqueNm` | Float | |
| `clutchTorqueNm` | Float | |
| `boostBar` | Float | |
| `throttlePct` | Float | |
| `accelerationMs2` | Float | |
| `exhaustPressureBar` | Float | |
| `turboRpm` | Float | |
| `railPressureBar` | Float | |
| `ambientTempC` | Float | |
| `engineTempC` | Float | |
| `transmissionTempC` | Float | |

Calculated (not stored): `powerPs = torqueNm × rpm / 9549.3`,
`powerBhp = torqueNm × rpm / 7120.83`. Both are 0 when torque or rpm ≤ 0.

### LogSession

Wraps `List<OBDDataPoint>` with the source filename. Computed stats:
`maxSpeedKmh`, `maxTorqueNm`, `maxPowerPs`, `maxRpm`, `durationSec`,
`displayLabel` / `shortLabel` (formatted datetime from filename).

- `sampledPoints(maxPoints = 1000)` — downsamples for chart performance.
- `dynoCurve(binRpm = 250)` — RPM-binned max-torque envelope with interpolated
  gaps (`DynoPoint`, `estimated` flag) for the Dyno chart.

### CSV Parsing

`CsvParser` locates the header row by searching for a row containing `"Time"` or
`"speed"`. Column binding is case-insensitive keyword matching — column order
doesn't matter. Missing columns default to `0f`. Quoted fields and blank/numeric
leading rows are handled gracefully.

### Filename Convention

`Log-YYYY-MM-DD--HH-mm-ss.csv` encodes session start datetime. `LogFileName`
parses it; the CSV `Time` column is a float offset in seconds from that start.
When a filename matches, time-based charts render `HH:mm:ss` clock labels instead
of raw seconds on the X-axis.

## Cloud Integration

### Data Models (cloud/CloudFolder.kt)

```kotlin
data class CloudFile(val id: String, val name: String, val size: Long)
data class CloudFolder(val id: String, val name: String, val path: String)
data class CloudFolderContents(
    val currentFolder: CloudFolder,
    val parentFolder: CloudFolder?,
    val subFolders: List<CloudFolder>,
    val csvFiles: List<CloudFile>
)
```

### OneDrive (MSAL + Microsoft Graph v1.0)

- **Scopes:** `Files.Read`, `Files.Read.All`
- **Endpoints used:**
  - `GET /me/drive/root/children` — root listing
  - `GET /me/drive/items/{id}/children` — folder listing
  - `GET /me/drive/items/{id}/content` — file download
  - `GET /me/drive/root:/{path}` — navigate by path
- Root folder id is normalised to the literal `"root"` for parent-detection guards
  (`folder.id == "root"`).

### Google Drive (Drive API v3)

- **Scope:** `DriveScopes.DRIVE_READONLY`
- `files()` list calls return **null** for empty folders — always `.orEmpty()`
  before mapping.
- File list fields requested: `id`, `name`, `size`, `parents`.

### Credentials

Credentials are **not** committed. `msal_config.json` (→
`app/src/main/res/raw/`) and `google-services.json` (→ `app/`) are gitignored;
`.template` files document their shape. Setup steps are in `README.md`.

- `res/raw/` filenames must match `[a-z0-9_]` — never place `*.template` files
  (containing dots) inside `res/`.
- MSAL 5.3.0 pulls Surface-Duo `display-mask` (not on public Maven); the
  dependency excludes `com.microsoft.device.display`. Jetifier is enabled in
  `gradle.properties` to handle transitive MSAL deps.

## UI

### Theme (ui/theme/Theme.kt) — forced dark

| Token | Hex |
|-------|-----|
| `BluePrimary` | #1E90FF |
| `BlueContainer` | #003A70 |
| `GreenAccent` | #00E676 |
| `OrangeAccent` | #FF6D00 |
| `RedAccent` | #FF1744 |
| `SurfaceDark` | #121212 |
| `SurfaceVariant` | #1E1E1E |
| `OnSurface` | #E0E0E0 |

### OBDLineChart (MPAndroidChart Compose wrapper)

- Drag, independent X/Y pinch-to-zoom, double-tap zoom, and pan are enabled;
  value labels are off. A `resetZoomKey: Int` param calls `fitScreen()` when it
  changes (ChartScreen bumps it on tab switch / reset-zoom FAB).
- Tap a point → `ChartMarkerView` tooltip (`res/layout/chart_marker.xml`) shows
  the series label, X (clock time / RPM / seconds), and Y value.
- `ChartSeries` flags: `yAxisRight`, `dashed` (estimated data), `drawCircles`,
  `circlesOnly` (measured points, no line), `lineWidth`.
- Dark background `#1E1E1E`. Dual Y-axes supported. X-axis formatter: `startTime`
  → absolute `HH:mm:ss`; `xIsRpm` → RPM; otherwise relative `"Xs"` seconds.

### Chart Types (ChartScreen.kt)

| Tab | Series |
|-----|--------|
| Speed vs Time | Speed (km/h) |
| Torque vs Time | Torque (Nm) left + RPM/10 right |
| Power vs Time | Power in selected unit (PS/HP toggle) |
| Dyno Curve | RPM (X) vs Torque & Power — estimate-filled envelope; measured points get a solid line + circles, interpolated gaps drawn dashed |
| Boost vs Time | Boost (bar) + Exhaust pressure (bar) |
| Temp vs Time | Engine, Transmission, Ambient (°C) |

- **Power unit toggle** (`PowerUnit` PS/HP) appears on Power and Dyno tabs and
  drives both the chart series and the stats summary.
- **Row reconstruction** (`CsvParser.compact()`): the logger streams **one value
  per row**, carrying prior values forward, so many raw rows share the same
  `Time`; only the last row of each consecutive equal-`Time` run is a complete
  sample. If `Time` is absent/constant raw rows are returned unchanged.
- **Dyno estimate** (`LogSession.dynoCurve`): all points with `rpm > 500` and
  `torque > 0` are binned by RPM (max torque per bin = envelope); empty bins are
  linearly interpolated and flagged `estimated`. The single Dyno tab draws the
  measured points as a solid line + circles and the interpolated gaps dashed.

## CI (`.github/workflows/android-ci.yml`)

Triggers: push or PR to `main` / `master`.

**`build` job** (ubuntu-latest, 30 min timeout):
1. Set up JDK 17 (Temurin) + Gradle cache.
2. Inject `secrets.MSAL_CONFIG_JSON` → `app/src/main/res/raw/msal_config.json`.
3. Inject `secrets.GOOGLE_SERVICES_JSON` → `app/google-services.json`.
4. `./gradlew assembleDebug testDebugUnitTest lintDebug` (lint is
   `continue-on-error: true`).
5. Upload APK artifact (14-day retention) + lint report + test results.
6. Post APK download link comment on the PR.

**`validate-templates` job**: asserts that `msal_config.json.template` and
`app/google-services.json.template` both exist in the repo.

**Required GitHub Secrets:** `MSAL_CONFIG_JSON`, `GOOGLE_SERVICES_JSON`.

## Conventions

- Default branch is `main`; work on feature branches and open PRs.
- Helpers return `Result<T>`; the ViewModel maps failures to `UiState.Error` /
  `FolderBrowseState.Error`. Network/IO runs on `Dispatchers.IO`.
- UI strings are mixed Thai/English — Thai for user-facing labels. Match the
  surrounding file.
- Coroutine patterns: `viewModelScope.launch`, `withContext(Dispatchers.IO)` for
  blocking I/O, `suspendCancellableCoroutine` to wrap MSAL/Google Auth callbacks.
- Compose patterns: `collectAsState()` for StateFlow, `LaunchedEffect` for
  navigation side-effects, `rememberLauncherForActivityResult` for cross-activity
  results.
- ProGuard (`proguard-rules.pro`) keeps `com.microsoft.identity.**`,
  `com.google.api.**`, and `com.google.android.gms.**`; release minification is
  currently disabled.
