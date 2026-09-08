# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

**BimmerDyno** — Android app that reads OBD logger CSV files (BMW and
generic) and renders interactive Speed / Torque / Horsepower charts. Files are
read from local storage only; the app has no network access and no accounts.
A Settings screen lets the user override which CSV column each field reads.

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
BimmerDyno/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/bimmerdyno/
│       │   ├── MainActivity.kt            # NavHost, external CSV intent handling
│       │   ├── data/
│       │   │   ├── OBDDataPoint.kt        # core model + HP math
│       │   │   ├── CsvParser.kt           # mapping-driven CSV parser
│       │   │   ├── LogField.kt            # LogField enum + FieldMapping
│       │   │   ├── SettingsStore.kt       # SharedPreferences persistence
│       │   │   ├── FolderContents.kt      # LogFile, LogFolder, FolderContents
│       │   │   ├── LogFileName.kt         # filename ↔ datetime utilities
│       │   │   └── LogSession.kt          # stats + downsampling wrapper
│       │   ├── viewmodel/
│       │   │   └── MainViewModel.kt       # UiState + FolderBrowseState (StateFlow)
│       │   └── ui/
│       │       ├── components/
│       │       │   └── OBDLineChart.kt    # MPAndroidChart Compose wrapper
│       │       ├── screens/
│       │       │   ├── HomeScreen.kt      # local folder entry point
│       │       │   ├── ChartScreen.kt     # 6 chart types with tab selector
│       │       │   ├── SettingsScreen.kt  # per-field column mapping
│       │       │   └── LocalFolderBrowserDialog.kt
│       │       └── theme/
│       │           └── Theme.kt           # dark Material3 theme
│       └── res/
│           └── values/{strings,themes}.xml
├── gradle/
│   ├── libs.versions.toml                 # centralized version catalog
│   └── wrapper/gradle-wrapper.properties
├── .github/workflows/android-ci.yml
├── ExampleData/Log-2026-06-09--21-57-04.csv
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
| Kotlinx Coroutines Android | 1.8.1 |
| DataStore Preferences | 1.1.1 |

Maven repositories: `google()`, `mavenCentral()`, JitPack.

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
    data class Browsing(val contents: FolderContents)
    data class Error(val message: String)
}

enum class ChartType { SPEED_TIME, TORQUE_TIME, POWER_TIME, DYNO_CURVE, BOOST_TIME, TEMP_TIME }
```

StateFlows: `uiState`, `folderBrowseState`, `selectedChartType`, `powerUnit`,
`fieldMapping`, `availableColumns`.

Changing the mapping saves it and re-parses the open log in place, so the charts
update without reopening the file. If that re-parse fails, `MainActivity`'s
`"chart"` route pops back home rather than showing an empty screen.

### Navigation (MainActivity.kt)

Three routes in `NavHost`:
- `"home"` → `HomeScreen` — also handles `ACTION_VIEW` intents for CSV files opened
  from a file manager (intent filters for `text/csv` and `text/comma-separated-values`).
- `"chart"` → `ChartScreen` — only reachable when `UiState.Success`.
- `"settings"` → `SettingsScreen` — reachable from the top bar of both screens.

The manifest declares no `INTERNET` permission: everything is read through the
Storage Access Framework.

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

`CsvParser.parse()` returns `Parsed(points, header)` and takes a `FieldMapping`
(see **Field Mapping** below); `FieldMapping.AUTO` is the default.

It locates the header row by searching for a row containing `"Time"` or
`"speed"`. Column binding is case-insensitive keyword matching unless the user
overrode it — column order doesn't matter. Missing columns default to `0f`.
Rows are split with `splitRow()`, which honours double-quoted fields containing
commas; blank/numeric leading rows are skipped.

`compact()` is skipped when the `Time` column is unmapped or disabled, since
without it there are no cycle boundaries to detect.

### Filename Convention

`Log-YYYY-MM-DD--HH-mm-ss.csv` encodes session start datetime. `LogFileName`
parses it; the CSV `Time` column is a float offset in seconds from that start.
When a filename matches, time-based charts render `HH:mm:ss` clock labels instead
of raw seconds on the X-axis.

## Local File Access

### Data Models (data/FolderContents.kt)

```kotlin
data class LogFile(val id: String, val name: String, val size: Long)
data class LogFolder(val id: String, val name: String, val path: String)
data class FolderContents(
    val currentFolder: LogFolder,
    val parentFolder: LogFolder?,
    val subFolders: List<LogFolder>,
    val csvFiles: List<LogFile>
)
```

`id` is always a document URI string — `DocumentFile.fromTreeUri` reopens it.

### Storage Access Framework

- `HomeScreen` launches `OpenDocumentTree`; `MainViewModel.openLocalFolder()`
  calls `takePersistableUriPermission` and stores the tree URI, so the folder
  survives reboots and the picker is never shown twice.
- `savedLocalFolderUri()` re-checks `persistedUriPermissions` before trusting the
  stored URI — a revoked grant falls back to re-prompting.
- Navigation is a `localNavStack` of URI strings; the bottom entry is the picked
  root, which is why "up" stops there rather than escaping the granted tree.

## Field Mapping (Settings)

`LogField` (data/LogField.kt) enumerates all 15 values with a stable `key` used
for persistence, a Thai display name, a unit, and `autoKeywords` for detection.

`FieldMapping.overrides` maps a field to a **column header name**:

| Override | Meaning |
|----------|---------|
| absent | auto-detect from `autoKeywords` (the default) |
| `FieldMapping.NONE` | field switched off — always reads `0f` |
| any other string | that column, matched case-insensitively |

`CsvParser.resolveIndices(headers, mapping)` binds every field to an index.
An override naming a column the file does not have **falls back to auto-detect**
rather than silently reading zero, so a mapping saved for one logger still works
on another file; `SettingsScreen` flags that case in red.

`SettingsStore` persists one key per field (`map_<key>`), never a serialised
blob, so adding a `LogField` later cannot invalidate what is already saved. It
also caches `lastKnownColumns` — the header of the last file opened — so the
Settings dropdown has real column names to offer at launch. Settings can also
read a header from any CSV via `CsvParser.readHeader()` without a full parse.

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
2. `./gradlew assembleDebug testDebugUnitTest lintDebug` (lint is
   `continue-on-error: true`).
3. Upload APK artifact (14-day retention) + lint report + test results.
4. Post APK download link comment on the PR.

No GitHub Secrets are required — the app has no external services.

## Conventions

- Default branch is `main`; work on feature branches and open PRs.
- The ViewModel maps failures to `UiState.Error` / `FolderBrowseState.Error`.
  File I/O runs on `Dispatchers.IO`.
- UI strings are mixed Thai/English — Thai for user-facing labels. Match the
  surrounding file.
- Coroutine patterns: `viewModelScope.launch` with `withContext(Dispatchers.IO)`
  for blocking I/O.
- Compose patterns: `collectAsState()` for StateFlow, `LaunchedEffect` for
  navigation side-effects, `rememberLauncherForActivityResult` for cross-activity
  results.
- ProGuard (`proguard-rules.pro`) is nearly empty; release minification is
  currently disabled.
