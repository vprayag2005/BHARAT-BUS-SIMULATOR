# Bharat Bus Simulator (Android 3D)

A realistic 3D Android bus simulator inspired by BUSSID, featuring real-world roads and geography. Development follows a strict district-by-district model: starting in Kerala (pilot district: Thrissur), expanding across all 14 Kerala districts, progressing into Tamil Nadu and Karnataka, and ultimately covering India.

## Development Principles & Rules
1. **Plan Before Code:** Every phase, feature, or bug fix requires an inspected plan and explicit user approval before execution.
2. **Real Geography First:** Road alignments, junctions, and terrain are derived from verified OpenStreetMap data and digital elevation models (Copernicus DEM). No invented real-world geography.
3. **District Delivery Model:** Each district must pass quality gates (G0: Data Ready → G1: Validated → G2: Driveable → G3: Scenery → G4: Gameplay → G5: Android Verified → G6: Accepted).
4. **Shared Architecture:** Road generation, bus physics, traffic AI, navigation, and streaming systems are shared core systems—never duplicated per district.
5. **Android-First & Hardware Verified:** Tested and profiled on Android hardware. Never claim a test succeeded unless it actually passed.

---

## Toolchain & Versions
* **Target Platform:** Android (minSdk 26, targetSdk 35, compileSdk 36)
* **Framework:** Native Android (Kotlin 2.0+, Jetpack Compose)
* **Graphics / 3D Pipeline:** Custom Low-Latency 3D Road Viewport & Projection Rasterizer
* **Build System:** Gradle 8.11+ / AGP 8.8+ / Java 21
* **Geospatial & Asset Tooling:** Python 3.12+ / Blender 4+
* **Application Identifier:** `com.bharatbussim.game`

---

## Repository Structure
```
BHARAT BUS SIMULATOR/
├── .gitignore                      # Safe ignore rules for Android, Python & raw GIS
├── .gitattributes                  # LFS candidacy & line ending normalization
├── README.md                       # Project overview and build guide
├── app/                            # Native Android Application (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/bharatbussim/game/
│   │   ├── MainActivity.kt         # Entry Activity & frame-synchronized physics loop
│   │   ├── model/                  # Data classes, OSM geometry & bus telemetry models
│   │   ├── ui/                     # 3D Viewport, HUD overlay, signboards, landmarks
│   │   └── viewmodel/              # ThrissurSimViewModel physics & simulation state
│   ├── src/main/assets/            # Compiled 3D binary mesh & OSM road JSON
│   └── src/main/res/               # SFX audio, strings, themes
├── art_src/                        # 3D source assets (Blender, OBJ, FBX, textures)
│   ├── vehicles/                   # Hino RM 2 & Kerala bus 3D models & textures
│   ├── roads/                      # Thrissur road network and junction models
│   └── audio/                      # Raw sound effects (engine, air brakes, wipers)
├── docs/                           # Architecture specifications & quality gates
├── data/                           # Manifests & GIS data schemas
└── tools/                          # Python GIS & 3D mesh compilation tooling
    └── compile_hino_rm2_android_mesh.py # Compiles raw 3D models into Android binary mesh
```

---

## How to Build the Android APK
1. Open the project in Android Studio or build via Gradle command line:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
2. Output APK location:
   `app/build/outputs/apk/debug/app-debug.apk`
3. Deploy to connected phone via ADB:
   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.bharatbussim.game/.MainActivity
   ```
