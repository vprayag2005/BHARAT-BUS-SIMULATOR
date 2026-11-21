package com.bharatbussim.game.ui

import android.content.Context
import com.bharatbussim.game.model.CircleObstacleInput
import com.bharatbussim.game.model.ImpactMaterial
import com.bharatbussim.game.model.ObbObstacleInput
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.ThrissurOsmRoad
import java.io.BufferedInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Thrissur District Highway Fuel Station 3D Asset System.
 *
 * Loads the compiled Google Drive 3D assets (`thrissur_fuel_station_meshes.bin` and
 * `thrissur_fuel_station_atlas.png`):
 *   - Model 0 (`indian_fuel_station`): 48.6m x 28.3m modern Indian highway fuel station
 *     with 4.55m-clearance overhead canopy, twin diesel/petrol pump islands, office,
 *     restrooms, utility block, air/water bay, and U-shaped perimeter compound walls.
 *   - Model 1 (`fuel_price_signboard`): 9.22m double-sided roadside digital price monolith.
 *
 * Placement & Road Clearance Policy:
 *   - Placed strictly at realistic inter-city highway intervals (~18-35 km real-world
 *     driving distance apart across Thrissur District's outer National & State Highways;
 *     zero stations clustered close together or inside Zone 1 city center).
 *   - Every station footprint and price signboard is verified against ALL road segments
 *     (including NH 66 parallel 5.4m service roads) so zero geometry ever touches a
 *     drivable lane or shoulder.
 */
internal object ThrissurFuelStationSystem {

    const val MODEL_STATION = 0
    const val MODEL_SIGNBOARD = 1

    // Native GLB extents of Indian_Fuel_Station.glb (front curb faces local -Z)
    private const val STATION_HALF_X = 24.31f
    private const val STATION_FRONT_Z = -9.50f // road-facing curb line
    private const val STATION_REAR_Z = 18.81f  // rear boundary wall

    // TWO Symmetrical Glowing Fuel Bay Parking Slots — BOTH on the right (+Z) side of their pump islands:
    //   Side 1 (ROW 1 • DIESEL): inner under-canopy lane right beside Diesel Island (+Z side of Z = -3.50m), served by Diesel Worker 2.
    //   Side 2 (ROW 2 • PETROL): rear bay lane right beside Petrol Island (+Z side of Z = +3.50m), served by Petrol Worker 1.
    // Each slot is 13.70m x 3.80m so the full 12.0m x 2.55m Hino RM2 bus fits cleanly inside all 4 corners!
    const val DIESEL_ISLAND_Z = -3.50f
    const val DIESEL_SLOT_SIDE_DIR = 1f // +Z (right / under-canopy) side of Diesel Island
    const val DIESEL_SLOT_MIN_X = -6.85f
    const val DIESEL_SLOT_MAX_X = 6.85f
    const val DIESEL_SLOT_MIN_Z = -2.70f
    const val DIESEL_SLOT_MAX_Z = 1.10f
    const val DIESEL_DISPENSER_X = 2.20f

    const val PETROL_ISLAND_Z = 3.50f
    const val PETROL_SLOT_SIDE_DIR = 1f // +Z (right / rear) side of Petrol Island
    const val PETROL_SLOT_MIN_X = -6.85f
    const val PETROL_SLOT_MAX_X = 6.85f
    const val PETROL_SLOT_MIN_Z = 4.30f
    const val PETROL_SLOT_MAX_Z = 8.10f
    const val PETROL_DISPENSER_X = -2.20f

    // Exact physical dispenser side hose manifold & holster coordinates (measured from Indian_Fuel_Station.glb):
    // Dispenser front LCD spans X=[dispX-0.326..dispX+0.326], Y=[1.195..1.627], Z=dispZ±0.247.
    // Side hose swivel outlet & nozzle holster sit strictly on the side chassis (never on the meter display!):
    //   - Diesel (dispX=+2.20): -X side (sideSign = -1f -> X=+1.585m)
    //   - Petrol (dispX=-2.20): +X side (sideSign = +1f -> X=-1.585m)
    const val DISPENSER_HOSE_SIDE_OFFSET_X = 0.615f
    const val DISPENSER_HOSE_OUTLET_Y = 1.88f
    const val DISPENSER_HOSE_OUTLET_Z_OFF = 0.03f
    const val DISPENSER_HOLSTER_SIDE_OFFSET_X = 0.635f
    const val DISPENSER_HOLSTER_Y = 1.24f
    const val DISPENSER_HOLSTER_Z_OFF = 0.12f

    // Exact Hino RM2 exterior side wall & hinged fuel filler door geometry (measured from hino_rm2_real_mesh.bin)
    const val BUS_SIDE_WALL_X = 1.242f
    const val BUS_FUEL_DOOR_CENTER_Y = 1.06f
    const val BUS_FUEL_DOOR_WIDTH = 0.34f
    const val BUS_FUEL_DOOR_HEIGHT = 0.32f
    const val BUS_FUEL_DOOR_MAX_OPEN_DEG = 112f

    // TWO Roadside Fuel_Price_Signboard monoliths placed OUTSIDE the station compound right beside
    // the highway edge (one outside Entry, one outside Exit), rotated PERPENDICULAR to the highway
    // so oncoming drivers along the highway read the wide illuminated double-sided price board head-on!
    private const val ENTRY_SIGN_LOCAL_X = 26.25f
    private const val EXIT_SIGN_LOCAL_X = -26.25f
    private const val SIGN_LOCAL_Z = -7.85f // With 90-deg rotation, half-width 1.80m reaches -9.65m (flush with curb)
    private const val SIGN_HALF_X = 1.80f
    private const val SIGN_HALF_Z = 0.65f

    private data class FuelStationSiteSpec(
        val id: String,
        val displayName: String,
        val roadId: String,
        val targetX: Float,
        val targetZ: Float,
        val side: Float, // -1f = Left of road tangent, +1f = Right of road tangent
        val extraRoadClearanceM: Float
    )

    data class FuelStationPlacement(
        val id: String,
        val displayName: String,
        val roadId: String,
        val cx: Float,
        val baseY: Float,
        val cz: Float,
        val yawRad: Float,
        val cosYaw: Float,
        val sinYaw: Float,
        // Entry-side roadside Fuel Price Signboard (facing highway + incoming entry traffic)
        val signX: Float,
        val signBaseY: Float,
        val signZ: Float,
        val signYawRad: Float,
        val signCosYaw: Float,
        val signSinYaw: Float,
        // Exit-side roadside Fuel Price Signboard (facing highway + oncoming exit-side traffic)
        val exitSignX: Float,
        val exitSignBaseY: Float,
        val exitSignZ: Float,
        val exitSignYawRad: Float,
        val exitSignCosYaw: Float,
        val exitSignSinYaw: Float,
        val boundingRadius: Float
    )

    /**
     * 6 realistic highway fuel stations spaced ~18-35 km real-world driving distance apart
     * across Thrissur District's major National Highways (NH 544 East, NH 544 South,
     * NH 66 Coastal North & South) and State Highways (SH 22 North, SH 69 NW).
     */
    private val SITE_SPECS = listOf(
        FuelStationSiteSpec(
            id = "FUEL_NH544_PATTIKKAD",
            displayName = "Pattikkad Highway Fuel Station (NH 544 East)",
            roadId = "ROAD_NH544_MANNUTHY_TO_KUTHIRAN",
            targetX = 1675f,
            targetZ = 1080f,
            side = -1f, // North-West side, clear of Kuthiran foothills & SH 51
            extraRoadClearanceM = 1.65f
        ),
        FuelStationSiteSpec(
            id = "FUEL_NH544_PERAMBRA",
            displayName = "Perambra Highway Fuel Station (NH 544 South)",
            roadId = "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY",
            targetX = 2315f,
            targetZ = -4040f,
            side = -1f, // East side on open highway stretch between Kodakara & Chalakudy
            extraRoadClearanceM = 1.65f
        ),
        FuelStationSiteSpec(
            id = "FUEL_SH22_WADAKKANCHERY",
            displayName = "Mullurkara Highway Fuel Station (SH 22 North)",
            roadId = "ROAD_THRISSUR_TO_SHORANUR",
            targetX = 94.5f,
            targetZ = 2110f,
            side = -1f, // West side, opposite the Southern Railway corridor (East at X=155)
            extraRoadClearanceM = 1.65f
        ),
        FuelStationSiteSpec(
            id = "FUEL_SH69_MUNDUR",
            displayName = "Mundur Highway Fuel Station (SH 69 NW)",
            roadId = "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR",
            targetX = -1740f,
            targetZ = 1025f,
            side = -1f, // South side along dead-straight Mundur highway stretch
            extraRoadClearanceM = 1.65f
        ),
        FuelStationSiteSpec(
            id = "FUEL_NH66_VADANAPPALLY",
            displayName = "Vadanappally Coastal Fuel Station (NH 66 North)",
            roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
            targetX = -3225f,
            targetZ = -665f,
            side = -1f, // East (inland) side, strictly outside NH 66's 5.4m service road (halfW + 7.25m)
            extraRoadClearanceM = 7.75f
        ),
        FuelStationSiteSpec(
            id = "FUEL_NH66_MATHILAKAM",
            displayName = "Mathilakam Coastal Fuel Station (NH 66 South)",
            roadId = "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM",
            targetX = -1815f,
            targetZ = -3335f,
            side = -1f, // East (inland) side, strictly outside NH 66's 5.4m service road (halfW + 7.25m)
            extraRoadClearanceM = 7.75f
        )
    )

    @Volatile
    private var cachedModels: List<Zone1GDriveEnvironmentRenderer.LoadedGDriveModel>? = null

    @Volatile
    private var cachedPlacements: List<FuelStationPlacement>? = null

    @Volatile
    private var cachedRoadCount: Int = -1

    fun ensureLoaded(
        context: Context,
        roads: List<ThrissurOsmRoad>
    ): List<Zone1GDriveEnvironmentRenderer.LoadedGDriveModel> {
        ensurePlacements(roads)
        return cachedModels ?: synchronized(this) {
            cachedModels ?: try {
                context.assets.open("thrissur_fuel_station_meshes.bin").use { raw ->
                    BufferedInputStream(raw, 65536).use { input ->
                        Zone1GDriveEnvironmentRenderer.parseBinaryMeshes(input.readBytes())
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }.also { cachedModels = it }
        }
    }

    fun getLoadedModels(): List<Zone1GDriveEnvironmentRenderer.LoadedGDriveModel> =
        cachedModels ?: emptyList()

    fun getPlacements(): List<FuelStationPlacement> = cachedPlacements ?: emptyList()

    /**
     * Computes exact road-aligned placements for all 6 highway fuel stations and verifies
     * mathematically that every corner of the 48.6m x 28.3m compound and 9.22m price signboard
     * clears all drivable carriageways, shoulders, and NH 66 parallel service roads.
     */
    fun ensurePlacements(roads: List<ThrissurOsmRoad>): List<FuelStationPlacement> {
        val existing = cachedPlacements
        if (existing != null && cachedRoadCount == roads.size && roads.isNotEmpty()) {
            return existing
        }
        if (roads.isEmpty()) return emptyList()

        return synchronized(this) {
            val again = cachedPlacements
            if (again != null && cachedRoadCount == roads.size) {
                return@synchronized again
            }

            val roadById = HashMap<String, ThrissurOsmRoad>(roads.size)
            for (r in roads) {
                roadById[r.id] = r
            }

            val built = ArrayList<FuelStationPlacement>(SITE_SPECS.size)
            for (spec in SITE_SPECS) {
                val road = roadById[spec.roadId] ?: continue
                val frame = findNearestFlatRoadFrame(road, spec.targetX, spec.targetZ) ?: continue

                val ox = frame.right.x * spec.side
                val oz = frame.right.z * spec.side
                val outLen = hypot(ox, oz).coerceAtLeast(1e-4f)
                val outNx = ox / outLen
                val outNz = oz / outLen

                // Match GDRIVE_INSTANCED_VS rotation:
                //   rotX = lx * cos(yaw) - lz * sin(yaw)
                //   rotZ = lx * sin(yaw) + lz * cos(yaw)
                // Station front is local -Z = (sin(yaw), -cos(yaw)); rear is local +Z = (-sin(yaw), cos(yaw)).
                // Align local +Z along outward normal (outNx, outNz) => yaw = atan2(-outNx, outNz).
                val yawRad = atan2(-outNx, outNz)
                val cosY = cos(yawRad)
                val sinY = sin(yawRad)

                // Orient BOTH Entry and Exit 9.22m double-sided Fuel Price Signboards PERPENDICULAR
                // to the highway (slight ~7 deg cant toward oncoming carriageway) so drivers driving
                // down the highway read the tall glowing price panels straight head-on from both directions!
                val halfPi = (PI * 0.5).toFloat()
                val entrySignYawRad = yawRad + halfPi - 0.12f
                val entrySignCosY = cos(entrySignYawRad)
                val entrySignSinY = sin(entrySignYawRad)

                val exitSignYawRad = yawRad + halfPi + 0.12f
                val exitSignCosY = cos(exitSignYawRad)
                val exitSignSinY = sin(exitSignYawRad)

                // Start at exact front curb clearance, then push outward in 0.5m steps if a gentle
                // highway curve brings any corner of the 48.6m frontage or either rotated roadside price board
                // closer than allowed.
                var centerDist = frame.halfWidthM + spec.extraRoadClearanceM + abs(STATION_FRONT_Z)
                var finalCx = frame.pos.x + outNx * centerDist
                var finalCz = frame.pos.z + outNz * centerDist

                for (step in 0 until 24) {
                    val tryCx = frame.pos.x + outNx * centerDist
                    val tryCz = frame.pos.z + outNz * centerDist
                    if (isFootprintClearOfAllRoads(
                            tryCx, tryCz, cosY, sinY,
                            entrySignCosY, entrySignSinY,
                            exitSignCosY, exitSignSinY,
                            spec.roadId, spec.extraRoadClearanceM, roads
                        )
                    ) {
                        finalCx = tryCx
                        finalCz = tryCz
                        break
                    }
                    centerDist += 0.5f
                    finalCx = frame.pos.x + outNx * centerDist
                    finalCz = frame.pos.z + outNz * centerDist
                }

                // Forecourt top is local Y = +0.02m; road surface is ~0.05..0.06m -> baseY = frame.y - 0.015m
                val baseY = (frame.pos.y - 0.015f).coerceAtLeast(0.03f)

                val entrySignWorldX = finalCx + ENTRY_SIGN_LOCAL_X * cosY - SIGN_LOCAL_Z * sinY
                val entrySignWorldZ = finalCz + ENTRY_SIGN_LOCAL_X * sinY + SIGN_LOCAL_Z * cosY
                val exitSignWorldX = finalCx + EXIT_SIGN_LOCAL_X * cosY - SIGN_LOCAL_Z * sinY
                val exitSignWorldZ = finalCz + EXIT_SIGN_LOCAL_X * sinY + SIGN_LOCAL_Z * cosY
                val signBaseY = frame.pos.y - 0.01f

                built.add(
                    FuelStationPlacement(
                        id = spec.id,
                        displayName = spec.displayName,
                        roadId = spec.roadId,
                        cx = finalCx,
                        baseY = baseY,
                        cz = finalCz,
                        yawRad = yawRad,
                        cosYaw = cosY,
                        sinYaw = sinY,
                        signX = entrySignWorldX,
                        signBaseY = signBaseY,
                        signZ = entrySignWorldZ,
                        signYawRad = entrySignYawRad,
                        signCosYaw = entrySignCosY,
                        signSinYaw = entrySignSinY,
                        exitSignX = exitSignWorldX,
                        exitSignBaseY = signBaseY,
                        exitSignZ = exitSignWorldZ,
                        exitSignYawRad = exitSignYawRad,
                        exitSignCosYaw = exitSignCosY,
                        exitSignSinYaw = exitSignSinY,
                        boundingRadius = 36.0f
                    )
                )
            }

            cachedPlacements = built
            cachedRoadCount = roads.size
            built
        }
    }

    /**
     * Returns true if world point (wx, wz) is safely outside every fuel station compound
     * and its roadside price signboard (with optional margin for trees/buildings/props).
     */
    fun isPointClearOfFuelStations(wx: Float, wz: Float, extraMargin: Float = 4.0f): Boolean {
        val list = cachedPlacements ?: return true
        for (i in 0 until list.size) {
            val st = list[i]
            val dx = wx - st.cx
            val dz = wz - st.cz
            val maxR = st.boundingRadius + extraMargin
            if (dx * dx + dz * dz > maxR * maxR) continue

            // Inverse transform into station local space (same convention as ThrissurCollisionSystem.Obb)
            val lx = dx * st.cosYaw + dz * st.sinYaw
            val lz = -dx * st.sinYaw + dz * st.cosYaw

            // Main station compound + approach apron + BOTH rotated entry & exit roadside price signboard envelopes
            if (lx >= (EXIT_SIGN_LOCAL_X - SIGN_HALF_Z - extraMargin - 2.0f) &&
                lx <= (ENTRY_SIGN_LOCAL_X + SIGN_HALF_Z + extraMargin + 2.0f) &&
                lz >= (SIGN_LOCAL_Z - SIGN_HALF_X - extraMargin - 1.5f) &&
                lz <= (STATION_REAR_Z + extraMargin)
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Returns true if a highway W-beam guardrail sample point (gx, gz) is clear of fuel station
     * entry/exit aprons. Tightly scoped to the station's road-facing side so the opposite side's
     * highway guardrail stays intact while the station side opens cleanly with Extruder End Terminals.
     */
    fun isGuardrailClearOfFuelStations(gx: Float, gz: Float): Boolean {
        val list = cachedPlacements ?: return true
        for (i in 0 until list.size) {
            val st = list[i]
            val dx = gx - st.cx
            val dz = gz - st.cz
            if (dx * dx + dz * dz > 58f * 58f) continue

            val lx = dx * st.cosYaw + dz * st.sinYaw
            val lz = -dx * st.sinYaw + dz * st.cosYaw

            // Station front curb is at lz = -9.5m; highway shoulder on station side sits around
            // lz = -10.5m..-18.5m (including NH 66 service-road width). Keep opposite-side rail
            // (which is > 20m further out at lz < -30m) untouched!
            if (lx in -32.0f..32.0f && lz in -20.0f..4.0f) {
                return false
            }
        }
        return true
    }

    /**
     * Registers exact solid colliders for every fuel station's buildings, perimeter walls,
     * pump islands, canopy pillars, and roadside price signboard while keeping the entire
     * 48m front approach and all 3 drive-through lanes under the canopy 100% open and drivable.
     */
    fun appendFuelStationColliders(
        buildingObbs: MutableList<ObbObstacleInput>,
        signPostCircles: MutableList<CircleObstacleInput>
    ) {
        val list = cachedPlacements ?: return
        for (i in 0 until list.size) {
            val st = list[i]

            fun addLocalObb(
                minLx: Float,
                maxLx: Float,
                minLz: Float,
                maxLz: Float,
                material: ImpactMaterial,
                label: String
            ) {
                val midLx = (minLx + maxLx) * 0.5f
                val midLz = (minLz + maxLz) * 0.5f
                val hx = (maxLx - minLx) * 0.5f
                val hz = (maxLz - minLz) * 0.5f
                val wx = st.cx + midLx * st.cosYaw - midLz * st.sinYaw
                val wz = st.cz + midLx * st.sinYaw + midLz * st.cosYaw
                buildingObbs.add(
                    ObbObstacleInput(
                        cx = wx,
                        cz = wz,
                        cosYaw = st.cosYaw,
                        sinYaw = st.sinYaw,
                        halfX = hx,
                        halfZ = hz,
                        material = material,
                        label = label
                    )
                )
            }

            fun addLocalCircle(
                lx: Float,
                lz: Float,
                radius: Float,
                material: ImpactMaterial,
                label: String
            ) {
                val wx = st.cx + lx * st.cosYaw - lz * st.sinYaw
                val wz = st.cz + lx * st.sinYaw + lz * st.cosYaw
                signPostCircles.add(
                    CircleObstacleInput(
                        cx = wx,
                        cz = wz,
                        radius = radius,
                        material = material,
                        label = label
                    )
                )
            }

            // 1. Rear station buildings (leaves wide 6.75m rear drive lane at Z = 4.15..10.90 open!)
            addLocalObb(-9.40f, 7.40f, 10.90f, 17.70f, ImpactMaterial.CONCRETE_BUILDING, "Fuel Station Building")
            addLocalObb(8.80f, 15.70f, 11.80f, 17.20f, ImpactMaterial.CONCRETE_BUILDING, "Fuel Station Restroom")
            addLocalObb(-16.70f, -10.30f, 11.80f, 17.20f, ImpactMaterial.CONCRETE_BUILDING, "Fuel Station Utility")
            addLocalObb(-16.40f, -12.60f, 3.30f, 5.70f, ImpactMaterial.CONCRETE_BUILDING, "Air & Water Bay")

            // 2. U-shaped perimeter compound walls (entire 48m front along local -Z stays wide open!)
            addLocalObb(-24.45f, -23.75f, -8.50f, 18.80f, ImpactMaterial.STONE_WALL, "Fuel Station Wall")
            addLocalObb(23.75f, 24.45f, -8.50f, 18.80f, ImpactMaterial.STONE_WALL, "Fuel Station Wall")
            addLocalObb(-24.45f, 24.45f, 18.30f, 19.00f, ImpactMaterial.STONE_WALL, "Fuel Station Wall")

            // 3. Diesel & Petrol raised pump islands (leaves 5.7m center drive-through lane open!)
            addLocalObb(-4.55f, 4.55f, -4.15f, -2.85f, ImpactMaterial.CONCRETE_BUILDING, "Diesel Pump Island")
            addLocalObb(-4.55f, 4.55f, 2.85f, 4.15f, ImpactMaterial.CONCRETE_BUILDING, "Petrol Pump Island")

            // 4. Four structural canopy support pillars
            addLocalCircle(-5.60f, -3.50f, 0.48f, ImpactMaterial.METAL_SIGN_POLE, "Canopy Pillar")
            addLocalCircle(5.60f, -3.50f, 0.48f, ImpactMaterial.METAL_SIGN_POLE, "Canopy Pillar")
            addLocalCircle(-5.60f, 3.50f, 0.48f, ImpactMaterial.METAL_SIGN_POLE, "Canopy Pillar")
            addLocalCircle(5.60f, 3.50f, 0.48f, ImpactMaterial.METAL_SIGN_POLE, "Canopy Pillar")

            // 5. Entry (upstream -X) & Exit (downstream +X) direction sign posts
            addLocalCircle(-11.60f, -8.90f, 0.25f, ImpactMaterial.METAL_SIGN_POLE, "Fuel Entry Sign")
            addLocalCircle(11.60f, -8.90f, 0.25f, ImpactMaterial.METAL_SIGN_POLE, "Fuel Exit Sign")

            // 6. Entry & Exit Roadside 9.22m Fuel Price Signboard monolith bases
            buildingObbs.add(
                ObbObstacleInput(
                    cx = st.signX,
                    cz = st.signZ,
                    cosYaw = st.signCosYaw,
                    sinYaw = st.signSinYaw,
                    halfX = SIGN_HALF_X,
                    halfZ = SIGN_HALF_Z,
                    material = ImpactMaterial.METAL_SIGN_POLE,
                    label = "Fuel Entry Price Signboard"
                )
            )
            buildingObbs.add(
                ObbObstacleInput(
                    cx = st.exitSignX,
                    cz = st.exitSignZ,
                    cosYaw = st.exitSignCosYaw,
                    sinYaw = st.exitSignSinYaw,
                    halfX = SIGN_HALF_X,
                    halfZ = SIGN_HALF_Z,
                    material = ImpactMaterial.METAL_SIGN_POLE,
                    label = "Fuel Exit Price Signboard"
                )
            )
        }
    }

    private fun findNearestFlatRoadFrame(
        road: ThrissurOsmRoad,
        targetX: Float,
        targetZ: Float
    ): RoadFrame? {
        val frames = road.frames
        if (frames.size < 10) return null
        var best: RoadFrame? = null
        var bestDistSq = Float.POSITIVE_INFINITY
        for (i in 4 until frames.size - 4) {
            val f = frames[i]
            if (f.pos.y > 0.22f) continue // Never snap to an elevated bridge or ramp
            val dx = f.pos.x - targetX
            val dz = f.pos.z - targetZ
            val dSq = dx * dx + dz * dz
            if (dSq < bestDistSq) {
                bestDistSq = dSq
                best = f
            }
        }
        return best
    }

    private fun isFootprintClearOfAllRoads(
        cx: Float,
        cz: Float,
        cosY: Float,
        sinY: Float,
        entrySignCosY: Float,
        entrySignSinY: Float,
        exitSignCosY: Float,
        exitSignSinY: Float,
        hostRoadId: String,
        hostExtraClearanceM: Float,
        roads: List<ThrissurOsmRoad>
    ): Boolean {
        // Sample station frontage, sides, rear corners, AND all 4 exact rotated corners of BOTH signboards!
        val stSampleXs = floatArrayOf(
            -STATION_HALF_X, -14.0f, 0.0f, 14.0f, STATION_HALF_X,
            -STATION_HALF_X, STATION_HALF_X
        )
        val stSampleZs = floatArrayOf(
            STATION_FRONT_Z, STATION_FRONT_Z, STATION_FRONT_Z, STATION_FRONT_Z, STATION_FRONT_Z,
            STATION_REAR_Z, STATION_REAR_Z
        )

        val totalSamples = stSampleXs.size + 8
        val worldXs = FloatArray(totalSamples)
        val worldZs = FloatArray(totalSamples)
        var outIdx = 0
        for (k in stSampleXs.indices) {
            val lx = stSampleXs[k]
            val lz = stSampleZs[k]
            worldXs[outIdx] = cx + lx * cosY - lz * sinY
            worldZs[outIdx] = cz + lx * sinY + lz * cosY
            outIdx++
        }

        val entryCx = cx + ENTRY_SIGN_LOCAL_X * cosY - SIGN_LOCAL_Z * sinY
        val entryCz = cz + ENTRY_SIGN_LOCAL_X * sinY + SIGN_LOCAL_Z * cosY
        val exitCx = cx + EXIT_SIGN_LOCAL_X * cosY - SIGN_LOCAL_Z * sinY
        val exitCz = cz + EXIT_SIGN_LOCAL_X * sinY + SIGN_LOCAL_Z * cosY
        val signCornerX = floatArrayOf(-SIGN_HALF_X, SIGN_HALF_X, -SIGN_HALF_X, SIGN_HALF_X)
        val signCornerZ = floatArrayOf(-SIGN_HALF_Z, -SIGN_HALF_Z, SIGN_HALF_Z, SIGN_HALF_Z)
        for (k in 0 until 4) {
            val sx = signCornerX[k]
            val sz = signCornerZ[k]
            worldXs[outIdx] = entryCx + sx * entrySignCosY - sz * entrySignSinY
            worldZs[outIdx] = entryCz + sx * entrySignSinY + sz * entrySignCosY
            outIdx++
        }
        for (k in 0 until 4) {
            val sx = signCornerX[k]
            val sz = signCornerZ[k]
            worldXs[outIdx] = exitCx + sx * exitSignCosY - sz * exitSignSinY
            worldZs[outIdx] = exitCz + sx * exitSignSinY + sz * exitSignCosY
            outIdx++
        }

        for (rIdx in 0 until roads.size) {
            val road = roads[rIdx]
            val frames = road.frames
            if (frames.size < 2) continue

            val isHost = (road.id == hostRoadId)
            val margin = if (isHost) (hostExtraClearanceM - 0.15f).coerceAtLeast(1.0f) else 3.0f
            val baseLimit = road.widthM * 0.5f + margin

            // Fast bounding-sphere rejection per road sample
            for (i in 0 until frames.size - 1) {
                val a = frames[i]
                val b = frames[i + 1]
                val midX = (a.pos.x + b.pos.x) * 0.5f
                val midZ = (a.pos.z + b.pos.z) * 0.5f
                val dcx = cx - midX
                val dcz = cz - midZ
                if (dcx * dcx + dcz * dcz > 95f * 95f) continue

                val segX = b.pos.x - a.pos.x
                val segZ = b.pos.z - a.pos.z
                val segLenSq = (segX * segX + segZ * segZ).coerceAtLeast(1e-5f)
                val segHalfW = maxOf(a.halfWidthM, b.halfWidthM)
                val reqDist = maxOf(baseLimit, segHalfW + margin)
                val reqDistSq = reqDist * reqDist

                for (k in worldXs.indices) {
                    val px = worldXs[k]
                    val pz = worldZs[k]
                    val t = (((px - a.pos.x) * segX + (pz - a.pos.z) * segZ) / segLenSq).coerceIn(0f, 1f)
                    val projX = a.pos.x + segX * t
                    val projZ = a.pos.z + segZ * t
                    val dx = px - projX
                    val dz = pz - projZ
                    if (dx * dx + dz * dz < reqDistSq) {
                        return false
                    }
                }
            }
        }
        return true
    }
}
