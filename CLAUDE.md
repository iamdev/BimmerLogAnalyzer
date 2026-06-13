# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

**BimmerLog Analyzer** — Android app that reads OBD logger CSV files (BMW and
generic) and renders interactive Speed / Torque / Horsepower charts. Files can
be opened from local storage, Microsoft OneDrive, or Google Drive.

## Build & Test

```bash
./gradlew assembleDebug        # build debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android lint
```

- **JDK 17**, Gradle 8.7, AGP 8.5, Kotlin 2.0, min SDK 26 / target SDK 34.
- `gradlew` / `gradlew.bat` are the **official** Gradle wrapper scripts — do not
  hand-edit them (a hand-written wrapper previously broke CI with a JVM-opts
  quoting bug).

## Architecture

MVVM + Jetpack Compose, single-activity, Navigation Compose.

```
data/        OBDDataPoint (model + HP math), CsvParser (auto column detect),
             LogSession (stats + downsampling), LogFileName (filename→datetime)
cloud/       OneDriveHelper (MSAL + Graph REST), GoogleDriveHelper (Drive API),
             CloudFile / CloudFolder / CloudFolderContents
viewmodel/   MainViewModel — UiState + FolderBrowseState (StateFlow)
ui/screens/  HomeScreen (import sources), ChartScreen (6 chart types),
             CloudFolderBrowserDialog (path input + folder navigation)
ui/components/ OBDLineChart (MPAndroidChart Compose wrapper)
ui/theme/    Theme.kt (dark theme)
```

Data flow: source picked in `HomeScreen` → `MainViewModel` parses CSV via
`CsvParser` → `UiState.Success(LogSession)` → navigation to `ChartScreen`.

## Domain knowledge

- **HP formula** (in `OBDDataPoint`): `PS = Torque(Nm) × RPM / 9549.3`,
  `bhp = Torque × RPM / 7120.83`. Power is 0 unless both torque and rpm > 0.
- **Dyno curve** uses only full-throttle points (`throttlePct >= 95`, `rpm > 500`),
  sorted by RPM.
- **Filename convention**: `Log-YYYY-MM-DD--HH-mm-ss.csv` encodes the session
  start datetime. `LogFileName` parses it; the CSV `Time` column is an offset in
  seconds from that start. When a filename matches, time-based charts show real
  HH:mm:ss clock labels instead of raw seconds.
- **CSV parsing** is column-name based (case-insensitive keyword match), not
  positional — new logger column orderings are tolerated. Header row is
  auto-located; non-numeric cells fall back to 0.
- **Row reconstruction**: the logger streams **one value per row**, carrying
  prior values forward, so many raw rows share the same `Time` and only the last
  row of each time-run is a complete sample. `CsvParser.compact()` keeps that
  last row per consecutive equal-`Time` run. If `Time` is absent/constant the
  raw rows are returned unchanged.
- **Dyno estimate** (`LogSession.dynoCurve`): full-throttle points are binned by
  RPM (max torque per bin = envelope); empty bins are linearly interpolated and
  flagged `estimated` so the UI draws them dashed vs solid circles for measured.

## Cloud integration

- Credentials are **not** committed. `msal_config.json` (→ `app/src/main/res/raw/`)
  and `google-services.json` (→ `app/`) are gitignored; `.template` files at the
  repo root / `app/` document the shape. Setup steps are in the README.
- `res/raw/` filenames must be `[a-z0-9_]` only — never put `*.template` (dots)
  there; keep templates outside `res/`.
- MSAL 5.3.0 pulls Surface-Duo `display-mask` (not on public Maven) and
  `androidx.credentials`; the MSAL dependency in `app/build.gradle.kts` excludes
  `com.microsoft.device.display` and Jetifier is enabled in `gradle.properties`.
- Google Drive `files()` returns **null** for empty folders — always
  `.orEmpty()` before mapping. OneDrive root folder id is normalized to the
  literal `"root"` so `folder.id == "root"` parent-detection guards work.

## CI

`.github/workflows/android-ci.yml` runs on every PR and push to `main`:
injects credential secrets (`MSAL_CONFIG_JSON`, `GOOGLE_SERVICES_JSON`), builds
the debug APK, runs unit tests + lint, uploads the APK artifact, and comments a
download link on the PR. A separate `validate-templates` job checks the
credential templates exist.

## Conventions

- Default branch is `main`; work on feature branches and open PRs.
- Helpers return `Result<T>`; the ViewModel maps failures to `UiState.Error` /
  `FolderBrowseState.Error`. Network/IO runs on `Dispatchers.IO`.
- UI strings are mixed Thai/English (Thai for user-facing labels). Match the
  surrounding file.
