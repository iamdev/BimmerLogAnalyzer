# BimmerLog Analyzer

**Android app สำหรับวิเคราะห์ข้อมูล OBD จากรถยนต์ BMW (และรถยนต์ทั่วไป)**  
อ่านไฟล์ CSV จาก OBD Logger แล้วแสดงกราฟ Speed / Torque / Horsepower แบบ Interactive

[![Android CI](https://github.com/iamdev/BimmerLogAnalyzer/actions/workflows/android-ci.yml/badge.svg)](https://github.com/iamdev/BimmerLogAnalyzer/actions/workflows/android-ci.yml)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06-green)

---

## Screenshots

> _ภาพหน้าจอจะเพิ่มหลัง build ครั้งแรก_

| Home Screen | Chart — Speed | Chart — Dyno Curve |
|:-----------:|:-------------:|:------------------:|
| เลือกแหล่งข้อมูล | Speed vs Time | Torque + HP vs RPM |

---

## Features

- **📂 นำเข้าไฟล์ได้ 3 ช่องทาง**
  - Local storage (file picker)
  - **Microsoft OneDrive** (Microsoft/Azure account)
  - **Google Drive** (Google account)
- **📊 กราฟ 6 แบบ** (pinch-to-zoom, drag ได้)
  | Tab | แกน X | แกน Y |
  |-----|-------|-------|
  | Speed | Time (s) | Speed (km/h) |
  | Torque | Time (s) | Torque (Nm) + RPM overlay |
  | Power | Time (s) | PS + bhp |
  | **Dyno** | RPM | Torque (Nm) + Power (PS) — full throttle ≥95% |
  | Boost | Time (s) | Boost pressure + Exhaust pressure (bar) |
  | Temp | Time (s) | Engine / Transmission / Ambient (°C) |
- **⚡ Stats Summary** — Max Speed, Max Torque, Max Power, Max RPM
- **🌙 Dark theme** — อ่านได้ง่ายในรถ

---

## สูตรคำนวณ Horsepower

```
Power (PS)  = Torque (Nm) × RPM / 9,549.3
Power (bhp) = Torque (Nm) × RPM / 7,120.83
```

**Dyno Curve** คัดเฉพาะจุดที่ Throttle ≥ 95% เพื่อให้ได้กราฟ power band ที่แม่นยำ

---

## CSV Format ที่รองรับ

ไฟล์ CSV จาก OBD logger ที่มี header row — parser จะ **auto-detect columns** จากชื่อ column (case-insensitive)

ตัวอย่าง header ที่รองรับ:
```
Time,Gear,Vehicle speed km/h,Engine speed rpm,Current engine torque Nm,
Actual clutch torque layer Nm,Boost pressure bar,Accelerator pedal value %,
Vehicle acceleration m/s²,Exhaust pressure upstream of the turbine bar,
Current speed of the turbocharger rpm,Rail pressure - actual value bar,
Ambient temperature ° C,Engine temperature ° C,Transmission oil temperature ° C
```

ดูตัวอย่างข้อมูลจริงได้ที่ [`ExampleData/Log-2026-06-09--21-57-04.csv`](ExampleData/Log-2026-06-09--21-57-04.csv)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM + StateFlow |
| Charts | [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) v3.1.0 |
| OneDrive | [MSAL Android](https://github.com/AzureAD/microsoft-authentication-library-for-android) + Microsoft Graph API |
| Google Drive | Google Sign-In + [Google Drive API v3](https://developers.google.com/drive/api/v3/reference) |
| Async | Kotlin Coroutines |
| Build | Gradle 8.7 + AGP 8.5 + Kotlin 2.0 |
| Min SDK | 26 (Android 8.0) |

---

## Project Structure

```
app/src/main/java/com/bimmerloganalyzer/
├── MainActivity.kt                    # Entry point + Navigation
├── data/
│   ├── OBDDataPoint.kt                # Data model, HP calculation
│   ├── CsvParser.kt                   # CSV parser (auto column detection)
│   └── LogSession.kt                  # Session stats + downsampling
├── cloud/
│   ├── OneDriveHelper.kt              # MSAL + Microsoft Graph API
│   └── GoogleDriveHelper.kt           # Google Sign-In + Drive API
├── viewmodel/
│   └── MainViewModel.kt               # State management
└── ui/
    ├── theme/Theme.kt                 # Dark theme
    ├── components/OBDLineChart.kt     # MPAndroidChart Compose wrapper
    └── screens/
        ├── HomeScreen.kt              # Import source selection
        ├── ChartScreen.kt             # 6 chart types
        └── CloudFileBrowserDialog.kt  # Cloud file picker dialog
```

---

## Setup

### 1. Clone

```bash
git clone https://github.com/iamdev/BimmerLogAnalyzer.git
cd BimmerLogAnalyzer
```

### 2. OneDrive Integration (Optional)

1. ไปที่ [portal.azure.com](https://portal.azure.com) → **App registrations** → New registration
2. Platform: **Android** | Package: `com.bimmerloganalyzer`
3. คัดลอก **Client ID** และ **Signature Hash**
4. สร้างไฟล์จาก template:
   ```bash
   cp app/src/main/res/raw/msal_config.json.template \
      app/src/main/res/raw/msal_config.json
   ```
5. แทนค่า `YOUR_AZURE_CLIENT_ID` และ `YOUR_SIGNATURE_HASH` ในไฟล์

> **หา Signature Hash:**
> ```bash
> keytool -exportcert -alias androiddebugkey \
>   -keystore ~/.android/debug.keystore | \
>   openssl sha1 -binary | openssl base64
> ```

### 3. Google Drive Integration (Optional)

1. ไปที่ [console.cloud.google.com](https://console.cloud.google.com) → สร้าง project
2. Enable **Google Drive API**
3. OAuth 2.0 Client ID → Android | Package: `com.bimmerloganalyzer`
4. ดาวน์โหลด `google-services.json` วางไว้ที่ `app/google-services.json`
5. เพิ่ม plugin ใน `app/build.gradle.kts`:
   ```kotlin
   id("com.google.gms.google-services")
   ```

> ไฟล์ `msal_config.json` และ `google-services.json` อยู่ใน `.gitignore` — **ห้าม commit**

### 4. Build

```bash
./gradlew assembleDebug
```

APK จะอยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

---

## CI/CD

GitHub Actions ทำงานอัตโนมัติเมื่อ **สร้าง Pull Request** หรือ push เข้า `main`

```
Pull Request / push to main
        │
        ├── build
        │     ├── Inject secrets (MSAL_CONFIG_JSON, GOOGLE_SERVICES_JSON)
        │     ├── ./gradlew assembleDebug
        │     ├── ./gradlew testDebugUnitTest
        │     ├── ./gradlew lintDebug
        │     ├── Upload APK artifact (14 วัน)
        │     └── Comment APK download link ใน PR
        │
        └── validate-templates
              └── ตรวจว่า config templates ครบ
```

ตั้ง Secrets สำหรับ CI ที่ **Settings → Secrets → Actions**:

| Secret | ค่าที่ใส่ |
|--------|---------|
| `MSAL_CONFIG_JSON` | เนื้อหาทั้งหมดของ `msal_config.json` |
| `GOOGLE_SERVICES_JSON` | เนื้อหาทั้งหมดของ `google-services.json` |

---

## License

```
MIT License — © 2026
```
