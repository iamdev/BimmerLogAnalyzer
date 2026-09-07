# BimmerDyno

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
| เลือก folder ในเครื่อง | Speed vs Time | Torque + HP vs RPM |

---

## Features

- **📂 เปิดไฟล์จากเครื่อง** — เลือก folder ครั้งเดียว แอปจำไว้ให้ข้ามการเปิดแอปครั้งถัดไป
  (persisted URI permission) แล้วเรียกดูไฟล์ CSV ได้จากในแอปเลย
- **⚙️ หน้า Settings — Mapping คอลัมน์** — กำหนดเองได้ว่าแต่ละค่าอ่านจากคอลัมน์ไหน
  ของไฟล์ ตั้งเป็น *อัตโนมัติ* (ค่าเริ่มต้น) หรือ *ไม่ใช้* ก็ได้ — เปลี่ยนแล้วกราฟ
  อัปเดตทันที
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
ถ้า logger ตั้งชื่อคอลัมน์ไม่เหมือนใคร ตั้งค่าเองได้ที่หน้า **Settings → Mapping คอลัมน์**

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
| ไฟล์ | Storage Access Framework (DocumentFile) — ไม่ใช้อินเทอร์เน็ต |
| Async | Kotlin Coroutines |
| Build | Gradle 8.7 + AGP 8.5 + Kotlin 2.0 |
| Min SDK | 26 (Android 8.0) |

---

## Project Structure

```
app/src/main/java/com/bimmerdyno/
├── MainActivity.kt                    # Entry point + Navigation
├── data/
│   ├── OBDDataPoint.kt                # Data model, HP calculation
│   ├── CsvParser.kt                   # CSV parser (mapping + auto detection)
│   ├── LogField.kt                    # Field list + FieldMapping overrides
│   ├── SettingsStore.kt               # SharedPreferences (folder, mapping)
│   ├── FolderContents.kt              # LogFile / LogFolder / FolderContents
│   └── LogSession.kt                  # Session stats + downsampling
├── viewmodel/
│   └── MainViewModel.kt               # State management
└── ui/
    ├── theme/Theme.kt                 # Dark theme
    ├── components/OBDLineChart.kt     # MPAndroidChart Compose wrapper
    └── screens/
        ├── HomeScreen.kt              # เปิด folder ในเครื่อง
        ├── ChartScreen.kt             # 6 chart types
        ├── SettingsScreen.kt          # Mapping คอลัมน์
        └── LocalFolderBrowserDialog.kt # File picker dialog
```

---

## Setup

### 1. Clone

```bash
git clone https://github.com/iamdev/BimmerLogAnalyzer.git
cd BimmerLogAnalyzer
```

### 2. Build

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
        └── build
              ├── ./gradlew assembleDebug
              ├── ./gradlew testDebugUnitTest
              ├── ./gradlew lintDebug
              ├── Upload APK artifact (14 วัน)
              └── Comment APK download link ใน PR
```

ไม่ต้องตั้ง GitHub Secrets — แอปไม่ได้ใช้บริการภายนอกแล้ว

---

## License

```
MIT License — © 2026
```
