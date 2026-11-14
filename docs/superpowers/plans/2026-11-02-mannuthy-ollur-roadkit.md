# Mannuthy-Ollur RoadKit Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and align a realistic Mannuthy-Ollur road corridor in Unity using the newly added modular RoadKits (`Bharat_Road_Kit_CityV2_Colors.fbx` and supporting kits), and rigorously verify drivability with the 12m Kerala bus prototype.

**Architecture:** An Editor builder (`MannuthyOllurRoadKitBuilder.cs`) extracts modular road sub-meshes from `Bharat_Road_Kit_CityV2_Colors.fbx`, assigns authentic URP PBR materials, snaps and chains road sections (4-lane divided NH 544 carriageway, 4-way cross junction at Mannuthy, 4-to-2 transitions and terminus loops at Ollur) along the authentic UTM route alignment, attaches physics-tuned MeshColliders, and an automated driving test (`MannuthyOllurDriveTest.cs`) executes headless simulation (`Physics.Simulate`) with the 6-wheel Kerala bus prototype to verify zero snagging, proper suspension settling, and smooth drive.

**Tech Stack:** Unity 6000.3.27f1 (Universal Render Pipeline Lit), C# (.NET Standard), Unity WheelCollider physics, Unity Editor batchmode.

## Global Constraints
- Target Scene: `Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity`.
- Do not break existing preserved scenes (`01_Phase0_SmokeTest.unity`, `02_Phase4_BusPrototype.unity`).
- Maintain zero compiler errors and zero unhandled exceptions in Unity batchmode.
- Road tiles must match exact dimensional bounds and snap with zero vertical cliffs (< 0.005m discrepancy) to ensure continuous WheelCollider roll.

## Review Focus
1. Mesh Collider snags at tile joints causing sudden bus stall (speed dropping to 0 km/h).
2. Material shader mismatch or pink textures under Universal Render Pipeline.
3. Inverted or rotated road piece alignment causing traffic direction conflicts.
4. Elevation discontinuity between Mannuthy junction (Y=1.3m) and Ollur node (Y=1.1m).
5. Wheel contact loss or physics instability during high-speed bus traversal.

---

### Task 1: Material & Asset Importer Setup
**Files:**
- Create: `Assets/_Project/Models/RoadKits/BharatRoadKit/Materials/` (URP Lit materials for asphalt, curbs, markings)
- Modify: `Assets/_Project/Models/RoadKits/BharatRoadKit/Bharat_Road_Kit_CityV2_Colors.fbx.meta`

- [ ] Step 1.1: Ensure `isReadable: 1` in `Bharat_Road_Kit_CityV2_Colors.fbx.meta` so mesh data is accessible to MeshColliders and builders.
- [ ] Step 1.2: Create URP Lit materials for `M_CityKit_Asphalt`, `M_CityKit_Curb`, `M_CityKit_YellowLine`, `M_CityKit_WhiteLine`, `M_CityKit_Sidewalk`, `M_CityKit_Verge`, and `M_CityKit_SlabBase` using HighwayKit textures and authentic IRC colors.
- [ ] Step 1.3: Run Unity batchmode to verify all materials compile and serialize without warnings.

### Task 2: Corridor Assembly Builder (`MannuthyOllurRoadKitBuilder.cs`)
**Files:**
- Create: `Assets/_Project/Editor/MannuthyOllurRoadKitBuilder.cs`
- Modify: `Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity`

- [ ] Step 2.1: Write `MannuthyOllurRoadKitBuilder.cs` to locate `Bharat_Road_Kit_CityV2_Colors.fbx` sub-meshes (`Junction_4Way_Cross`, `Road_4Lane_Straight_22m`, `Road_4Lane_Straight_11m`, `Road_4Lane_Curve_90Deg`, `Road_Transition_4to2_Straight`, etc.).
- [ ] Step 2.2: Implement procedural tile snapping and alignment algorithm from Mannuthy junction `(1352.0, 1.30, -472.0)` through NH 544 to Ollur `(2178.0, 1.10, -1481.0)`.
- [ ] Step 2.3: Attach `MeshCollider` components with a low-bounce, high-friction `PhysicMaterial` to every road segment.
- [ ] Step 2.4: Integrate street lights from `Road+Junction.fbx` along the central divider/sidewalks.
- [ ] Step 2.5: Execute the builder via Unity batchmode and verify the scene hierarchy and saved assets.

### Task 3: Automated Bus Drivability Test Runner (`MannuthyOllurDriveTest.cs`)
**Files:**
- Create: `Assets/_Project/Editor/MannuthyOllurDriveTest.cs`

- [ ] Step 3.1: Write `MannuthyOllurDriveTest.cs` using headless physics simulation (`Physics.Simulate`).
- [ ] Step 3.2: Position the 12m Kerala bus prototype at the Mannuthy starting arm, engage Drive, release parking brake, and apply throttle.
- [ ] Step 3.3: Steer the bus through the corridor waypoints, logging velocity, wheel contact, and distance every physics frame.
- [ ] Step 3.4: Assert:
  - Bus reaches at least 40 km/h on straight sections.
  - Zero abrupt stalls (acceleration drop > 30 m/s² due to collider snag).
  - All 6 wheels maintain suspension contact with road colliders.
- [ ] Step 3.5: Run test from command line and verify it exits with code 0 and logs pass.

### Task 4: Verification Visuals & Reporting
**Files:**
- Create / Update: `artifacts/roadkit_shots/` (screenshots of junction, highway stretch, transition, cockpit)
- Modify: `Assets/_Project/Editor/MannuthyOllurDriveTest.cs` (camera rendering pass)

- [ ] Step 4.1: Add camera render passes to take top-down and driver-eye screenshots of the new road kit route.
- [ ] Step 4.2: Execute camera renders via Unity batchmode and confirm PNG artifacts are generated.
- [ ] Step 4.3: Review final screenshots and test logs to verify visual quality and road alignment.
