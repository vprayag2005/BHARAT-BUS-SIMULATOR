# Bharat Bus Simulator (Android 3D)

A realistic 3D Android bus simulator inspired by BUSSID, featuring real-world roads and geography. Development follows a strict district-by-district model: starting in Kerala (pilot district: Thrissur), expanding across all 14 Kerala districts, progressing into Tamil Nadu and Karnataka, and ultimately covering India.

## Development Principles & Rules
1. **Plan Before Code:** Every phase, feature, or bug fix requires an inspected plan and explicit user approval before execution.
2. **Real Geography First:** Road alignments, junctions, and terrain are derived from verified OpenStreetMap data and digital elevation models (Copernicus DEM). No invented real-world geography.
3. **District Delivery Model:** Each district must pass quality gates (G0: Data Ready → G1: Validated → G2: Driveable → G3: Scenery → G4: Gameplay → G5: Android Verified → G6: Accepted).
4. **Shared Architecture:** Road generation, bus physics, traffic AI, navigation, and streaming systems are shared core systems—never duplicated per district.
5. **Android-First & Hardware Verified:** Tested and profiled on Android hardware. Never claim a test succeeded unless it actually passed.

---

## Toolchain & Versions (Phase 0 Baseline)
* **Game Engine:** Unity 6 LTS (`6000.3.27f1`)
* **Render Pipeline:** Universal Render Pipeline (URP 17.3.0)
* **Target Platform:** Android (ARM64 `arm64-v8a`, IL2CPP)
* **Graphics API:** Vulkan (primary), OpenGL ES 3.2 (fallback)
* **Target Android SDK:** API Level 34 / 35+ (Google Play compliant)
* **Bundled OpenJDK:** Temurin-17.0.18+8 (OpenJDK 17 64-bit)
* **Bundled Android NDK:** NDK Revision 27.2.12479018 (r27b)
* **Bundled Android SDK:** Build-Tools 36.0.0, API platforms 34–37
* **Geospatial Preprocessing:** Python 3.14 (`geopandas`, `shapely`, `pyproj`, `networkx`, `pandas`)
* **Application Identifier:** `com.bharatbussim.game`

---

## Repository Structure
```
BHARAT BUS SIMULATOR/
├── .gitignore                      # Safe ignore rules for Unity, Python & raw GIS
├── .gitattributes                  # LFS candidacy & line ending normalization
├── README.md                       # Project overview and build guide
├── docs/
│   ├── ARCHITECTURE.md             # Coordinate policy, quality gates, tile streaming
│   ├── DATA_PROVENANCE.md          # GIS source/license register (ODbL, DEM terms)
│   └── PHASES.md                   # PRD phase tracking and quality gates
├── tools/                          # Python GIS tooling & data preprocessing
│   ├── requirements.txt            # Documented Python dependencies
│   └── osm_processor/              # Phase 1 & 2 boundary/road graph extraction
├── data/                           # Manifests & GIS data schemas
│   ├── raw/                        # Excluded from git (raw OSM/DEM downloads)
│   ├── processed/                  # Normalized datasets
│   └── manifests/
│       └── kerala/                 # District manifests (pilot: Thrissur)
└── unity/                          # Unity 6.3 LTS Client Project
    ├── Assets/
    │   ├── _Project/               # Custom project code, scenes, and assets
    │   │   ├── Core/               # Foundation managers
    │   │   ├── Diagnostics/        # DeviceDiagnosticsOverlay (FPS, memory, device info)
    │   │   ├── Editor/             # BuildScript & Editor tooling
    │   │   └── Scenes/
    │   │       └── 01_Phase0_SmokeTest.unity
    │   └── Settings/               # Official Mobile & PC URP renderers
    ├── Packages/
    │   └── manifest.json           # URP, Input System, Test Framework
    └── ProjectSettings/            # ProjectSettings.asset, IL2CPP, ARM64
```

---

## How to Build the Android Smoke Test APK
1. Open the project in Unity 6.3 LTS (`6000.3.27f1`).
2. Run via Unity Editor menu: **BharatBusSim > Build > Build Android Smoke Test APK**.
3. Or build via command line:
   ```powershell
   & "C:\Program Files\Unity\Hub\Editor\6000.3.27f1\Editor\Unity.exe" -batchmode -quit -projectPath "C:\Project\BHARAT BUS SIMULATOR\unity" -executeMethod BuildScript.BuildAndroidSmokeTest -logFile "builds/Android/build.log"
   ```
4. Output APK location: `builds/Android/Phase0_SmokeTest.apk`.
5. Deploy to connected phone via ADB:
   ```powershell
   adb install -r builds/Android/Phase0_SmokeTest.apk
   adb shell am start -n com.bharatbussim.game/com.unity3d.player.UnityPlayerGameActivity
   ```
