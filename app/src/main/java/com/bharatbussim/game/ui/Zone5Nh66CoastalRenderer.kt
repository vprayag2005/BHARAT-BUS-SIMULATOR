package com.bharatbussim.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zone 5: NH 66 Coastal Corridor, Nattika Toll Plaza, Chettuva Backwaters Bridge,
 * Triprayar Canoli Canal Bridge & Sree Rama Temple Water Ghat (Meenootu),
 * Kottapuram / Kodungallur NW-3 Bridge, and Chavakkad Beach Granite Seawall (Kallidum).
 *
 * Road Design Rule Guarantee:
 * - Every drivable NH 66 carriageway, bridge deck, and 10-lane + 2-bypass Nattika Toll Plaza lane
 *   stays 100% unobstructed and joined with 0.000m seams via shared Cosine S-curve flare & elevation math.
 * - Nattika Toll Plaza matches the real-world reference photo: crisp two-story bright white concrete
 *   overhead admin block directly on top of the canopy with signature royal-blue sloped window awnings,
 *   full-width deep-green NHAI trilingual fascia (Malayalam / Hindi / English + NH 66 shield), tall
 *   square white concrete columns, exterior steel staircase, red-framed outer portals, and 10 live
 *   pneumatic FASTag boom barriers.
 */
internal object Zone5Nh66CoastalRenderer {

    // =========================================================================
    // SINGLE SOURCE OF TRUTH: ZONE 5 GEOMETRIC & ELEVATION ANCHORS
    // =========================================================================

    // 1. Chettuva Backwaters Bridge (Major Water Bridge 5 — 360m total, 240m main estuary deck)
    const val CHETTUVA_CENTER_X = -3664.0f
    const val CHETTUVA_RAMP_NORTH_Z = 395.0f
    const val CHETTUVA_DECK_NORTH_Z = 335.0f
    const val CHETTUVA_CENTER_Z = 215.0f
    const val CHETTUVA_DECK_SOUTH_Z = 95.0f
    const val CHETTUVA_RAMP_SOUTH_Z = 35.0f
    const val CHETTUVA_DECK_Y = 7.20f
    const val CHETTUVA_DECK_HALF_W = 11.00f

    // 2. Nattika Toll Plaza (NH 66 between Thalikulam & Triprayar — 10-lane + 2 Free Outer Bypass Lanes)
    const val NATTIKA_CENTER_X = -2536.0f
    const val NATTIKA_CENTER_Z = -1850.0f
    const val NATTIKA_FLARE_NORTH_START_Z = -1730.0f
    const val NATTIKA_PQC_NORTH_Z = -1822.0f
    const val NATTIKA_PQC_SOUTH_Z = -1878.0f
    const val NATTIKA_FLARE_SOUTH_END_Z = -1970.0f
    const val NATTIKA_OUTER_HALF_W = 34.8f          // 10 lanes (54.0m) + 2 flanking 7.8m bypass lanes
    const val NATTIKA_LANE_COUNT = 10               // 5 Southbound (0..4) + 5 Northbound (5..9)
    const val NATTIKA_LANE_PITCH = 5.40f            // 1.28m island + 4.12m crystal-clear driving lane
    const val NATTIKA_ISLAND_HALF_W = 0.64f
    const val NATTIKA_CANOPY_HALF_W = 28.6f         // Spans all 10 toll lanes cleanly
    const val NATTIKA_CANOPY_CLEAR_Y = 5.65f

    // 3. Triprayar Canoli Canal Bridge & Sree Rama Temple Water Ghat (Meenootu)
    const val CANOLI_CENTER_X = -2220.0f
    const val CANOLI_RAMP_NORTH_Z = -2555.0f
    const val CANOLI_DECK_NORTH_Z = -2605.0f
    const val CANOLI_CENTER_Z = -2660.0f
    const val CANOLI_DECK_SOUTH_Z = -2715.0f
    const val CANOLI_RAMP_SOUTH_Z = -2765.0f
    const val CANOLI_DECK_Y = 4.65f
    const val CANOLI_DECK_HALF_W = 11.00f

    // 4. Kottapuram / Kodungallur NW-3 Bridge (Major Water Bridge 6 — High Navigational Clearance)
    const val KOTTAPURAM_CENTER_X = -1520.0f
    const val KOTTAPURAM_RAMP_NORTH_Z = -3780.0f
    const val KOTTAPURAM_DECK_NORTH_Z = -3880.0f
    const val KOTTAPURAM_CENTER_Z = -4050.0f
    const val KOTTAPURAM_DECK_SOUTH_Z = -4220.0f
    const val KOTTAPURAM_RAMP_SOUTH_Z = -4320.0f
    const val KOTTAPURAM_DECK_Y = 12.60f            // ~10.8m clear vertical navigation height over NW-3!
    const val KOTTAPURAM_DECK_HALF_W = 11.00f

    const val MOOTHAKUNNAM_TERM_X = -1520.0f
    const val MOOTHAKUNNAM_TERM_Z = -4425.0f

    // =========================================================================
    // AUTHENTIC NH 66 COASTAL & NATTIKA TOLL MATERIAL PALETTE
    // =========================================================================
    private val MatNattikaWhite = Color(0xFFF3F5F8)
    private val MatNattikaWhiteShade = Color(0xFFD5DCE4)
    private val MatNattikaRoofSlab = Color(0xFFE2E8F0)
    private val MatNattikaBlueAwning = Color(0xFF1543A8)       // Signature royal-blue window sunshades in photo!
    private val MatNattikaBlueAwningDark = Color(0xFF0E2F7A)
    private val MatNattikaNhaiGreen = Color(0xFF006B4F)        // Deep NHAI highway fascia green
    private val MatNattikaRedFrame = Color(0xFFC81E1E)         // Red steel safety portal frame in photo!
    private val MatNattikaStairSteel = Color(0xFF475569)
    private val MatNattikaStairDark = Color(0xFF1E293B)
    private val MatConcreteApron = Color(0xFF9EA6B2)
    private val MatTollLaneDark = Color(0xFF6B7280)
    private val MatConcreteDeck = Color(0xFFD5DAE1)
    private val MatConcreteDeckShadow = Color(0xFF9AA3B0)
    private val MatConcretePier = Color(0xFFB5BDC8)
    private val MatConcretePierDark = Color(0xFF7A8494)
    private val MatConcreteMossWet = Color(0xFF475542)
    private val MatGalvanized = Color(0xFFCBD5E1)
    private val MatGalvanizedDark = Color(0xFF64748B)
    private val MatHazardYellow = Color(0xFFFACC15)
    private val MatHazardBlack = Color(0xFF18181B)
    private val MatTintedGlass = Color(0xFF0F2942)
    private val MatLedGreen = Color(0xFF22C55E)
    private val MatLedRed = Color(0xFFEF4444)
    private val MatLedWhite = Color(0xFFF8FAFC)
    private val MatLedWarm = Color(0xFFFEF08A)

    private val MatMangroveMud = Color(0xFF4E4032)
    private val MatMangroveRoot = Color(0xFF5D3A24)
    private val MatMangroveLeafDark = Color(0xFF1E5629)
    private val MatMangroveLeafBright = Color(0xFF2F7A38)
    private val MatTeakDark = Color(0xFF4A2B16)
    private val MatTeakMid = Color(0xFF6E4121)
    private val MatBamboo = Color(0xFFC4A85B)
    private val MatBambooDark = Color(0xFF8C7336)
    private val MatBoulderGrey = Color(0xFF5A616B)
    private val MatBoulderDark = Color(0xFF3A4048)
    private val MatBoulderWarm = Color(0xFF6E675E)
    private val MatLaterite = Color(0xFF8F3C24)
    private val MatLateriteDark = Color(0xFF6B2A18)
    private val MatTerracotta = Color(0xFFB43E24)
    private val MatTempleGold = Color(0xFFEAB308)
    private val MatTempleCream = Color(0xFFF5EFE6)
    private val MatIceBoxBlue = Color(0xFF1D4ED8)
    private val MatIceBoxBlueLight = Color(0xFF3B82F6)
    private val MatWaterFoam = Color(0xFFEFF8FF)
    private val MatBargeRust = Color(0xFF7F2A22)
    private val MatBargeSteel = Color(0xFF334155)
    private val MatFerryBlue = Color(0xFF0369A1)
    private val MatFerryCream = Color(0xFFF8FAFC)

    // =========================================================================
    // PUBLIC QUERIES: TOLL LANES, COSINE FLARE, BRIDGE ELEVATIONS, CLEARANCE
    // =========================================================================

    fun nattikaLaneCenterX(laneIdx: Int): Float =
        NATTIKA_CENTER_X + (laneIdx - 4.5f) * NATTIKA_LANE_PITCH

    fun nattikaIslandX(islandIdx: Int): Float =
        NATTIKA_CENTER_X + (islandIdx - 5) * NATTIKA_LANE_PITCH

    fun isNattikaLaneActiveFastag(laneIdx: Int): Boolean = laneIdx in 1..8

    fun nearestNattikaTollLaneIndex(wx: Float): Int {
        val raw = ((wx - NATTIKA_CENTER_X) / NATTIKA_LANE_PITCH) + 4.5f
        val idx = kotlin.math.round(raw).toInt()
        return if (idx in 0 until NATTIKA_LANE_COUNT) idx else -1
    }

    /**
     * Smooth 92m Cosine S-curve flare for Nattika Toll Plaza on NH 66 (11.0m -> 34.8m -> 11.0m half-width),
     * shared identically by RoadFrame generation and PQC concrete plaza rendering for 0.000m seams.
     */
    fun nattikaFlareHalfWidthAtZ(z: Float): Float {
        return when {
            z >= NATTIKA_FLARE_NORTH_START_Z -> 11.0f
            z > NATTIKA_PQC_NORTH_Z -> {
                val u = ((NATTIKA_FLARE_NORTH_START_Z - z) / (NATTIKA_FLARE_NORTH_START_Z - NATTIKA_PQC_NORTH_Z)).coerceIn(0f, 1f)
                val s = 0.5f - 0.5f * cos(u * PI.toFloat())
                11.0f + (NATTIKA_OUTER_HALF_W - 11.0f) * s
            }
            z >= NATTIKA_PQC_SOUTH_Z -> NATTIKA_OUTER_HALF_W
            z > NATTIKA_FLARE_SOUTH_END_Z -> {
                val u = ((NATTIKA_PQC_SOUTH_Z - z) / (NATTIKA_PQC_SOUTH_Z - NATTIKA_FLARE_SOUTH_END_Z)).coerceIn(0f, 1f)
                val s = 0.5f - 0.5f * cos(u * PI.toFloat())
                NATTIKA_OUTER_HALF_W + (11.0f - NATTIKA_OUTER_HALF_W) * s
            }
            else -> 11.0f
        }
    }

    private fun cosineBridgeProfileSouthbound(
        z: Float,
        rampNorthZ: Float,
        deckNorthZ: Float,
        deckSouthZ: Float,
        rampSouthZ: Float
    ): Float {
        return when {
            z >= rampNorthZ -> 0f
            z <= rampSouthZ -> 0f
            z in deckSouthZ..deckNorthZ -> 1f
            z > deckNorthZ -> {
                val u = ((rampNorthZ - z) / (rampNorthZ - deckNorthZ)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
            else -> {
                val u = ((z - rampSouthZ) / (deckSouthZ - rampSouthZ)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
        }
    }

    /**
     * Exact O(1) road surface Y for all three Zone 5 water bridges:
     * - Chettuva Backwaters Bridge (Y = 7.20m)
     * - Triprayar Canoli Canal Bridge (Y = 4.65m)
     * - Kottapuram / Kodungallur NW-3 High-Clearance Bridge (Y = 12.60m)
     */
    fun computeZone5RoadSurfaceY(x: Float, z: Float): Float {
        var y = 0.06f
        if (z in CHETTUVA_RAMP_SOUTH_Z..CHETTUVA_RAMP_NORTH_Z && abs(x - CHETTUVA_CENTER_X) <= 16.5f) {
            val p = cosineBridgeProfileSouthbound(
                z, CHETTUVA_RAMP_NORTH_Z, CHETTUVA_DECK_NORTH_Z,
                CHETTUVA_DECK_SOUTH_Z, CHETTUVA_RAMP_SOUTH_Z
            )
            y = max(y, 0.06f + p * (CHETTUVA_DECK_Y - 0.06f))
        }
        if (z in CANOLI_RAMP_SOUTH_Z..CANOLI_RAMP_NORTH_Z && abs(x - CANOLI_CENTER_X) <= 16.5f) {
            val p = cosineBridgeProfileSouthbound(
                z, CANOLI_RAMP_NORTH_Z, CANOLI_DECK_NORTH_Z,
                CANOLI_DECK_SOUTH_Z, CANOLI_RAMP_SOUTH_Z
            )
            y = max(y, 0.06f + p * (CANOLI_DECK_Y - 0.06f))
        }
        if (z in KOTTAPURAM_RAMP_SOUTH_Z..KOTTAPURAM_RAMP_NORTH_Z && abs(x - KOTTAPURAM_CENTER_X) <= 16.5f) {
            val p = cosineBridgeProfileSouthbound(
                z, KOTTAPURAM_RAMP_NORTH_Z, KOTTAPURAM_DECK_NORTH_Z,
                KOTTAPURAM_DECK_SOUTH_Z, KOTTAPURAM_RAMP_SOUTH_Z
            )
            y = max(y, 0.06f + p * (KOTTAPURAM_DECK_Y - 0.06f))
        }
        return y
    }

    fun computeZone5OverheadCeilingY(x: Float, z: Float): Float {
        if (x in (NATTIKA_CENTER_X - NATTIKA_CANOPY_HALF_W - 2f)..(NATTIKA_CENTER_X + NATTIKA_CANOPY_HALF_W + 2f) &&
            z in (NATTIKA_CENTER_Z - 9.0f)..(NATTIKA_CENTER_Z + 9.0f)
        ) {
            return 5.25f
        }
        return 1000f
    }

    /**
     * Scenery clearance mask ensuring ZERO random trees or buildings ever spawn inside
     * Chettuva Estuary, Nattika Toll Plaza flare, Triprayar Canoli Canal & Temple Ghat,
     * Kottapuram NW-3 Periyar Lagoon, Moothakunnam turnaround bay, or Chavakkad Seawall.
     */
    fun isPointClearOfZone5Structures(px: Float, pz: Float): Boolean {
        // Chettuva Backwaters Estuary & Mangrove/Chinese-Net Basin
        if (px in -3990f..-3330f && pz in 45f..390f) return false
        // Nattika Toll Plaza & 92m Cosine Flares
        if (pz in (NATTIKA_FLARE_SOUTH_END_Z - 18f)..(NATTIKA_FLARE_NORTH_START_Z + 18f)) {
            val hw = nattikaFlareHalfWidthAtZ(pz) + 14.0f
            if (abs(px - NATTIKA_CENTER_X) <= hw) return false
        }
        // Triprayar Canoli Canal & Sree Rama Temple Water Ghat
        if (px in -2500f..-1940f && pz in -2755f..-2565f) return false
        // Kottapuram / Kodungallur NW-3 Periyar Lagoon & High Bridge
        if (px in -1970f..-1080f && pz in -4310f..-3790f) return false
        // Moothakunnam South Terminus & Turnaround Apron
        if (px in -1590f..-1450f && pz in -4490f..-4340f) return false
        // Chavakkad Granite Seawall & Beach Boats strip
        if (px in -4565f..-4509f && pz in 985f..1170f) return false
        return true
    }

    // =========================================================================
    // GROUND PLAZAS, PQC TOLL APRON, RIVERBEDS & TURNAROUND BAY
    // =========================================================================

    fun DrawScope.drawZone5GroundPlazas(
        rig: CameraRig,
        path: Path,
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>
    ) {
        drawNh66ServiceRoadsGround(rig, path, camEye, roads, drawAll = false)
        drawNattikaTollApronGround(rig, path, camEye)
        drawChettuvaEstuaryBedGround(rig, path, camEye)
        drawCanoliCanalAndGhatGround(rig, path, camEye)
        drawKottapuramLagoonAndTerminusGround(rig, path, camEye)
    }

    fun DrawScope.drawAllZone5GroundPlazasUnconditional(
        rig: CameraRig,
        path: Path,
        roads: List<ThrissurOsmRoad>
    ) {
        drawNh66ServiceRoadsGround(rig, path, Vec3(NATTIKA_CENTER_X, 2f, NATTIKA_CENTER_Z), roads, drawAll = true)
        drawNattikaTollApronGround(rig, path, Vec3(NATTIKA_CENTER_X, 2f, NATTIKA_CENTER_Z))
        drawChettuvaEstuaryBedGround(rig, path, Vec3(CHETTUVA_CENTER_X, 2f, CHETTUVA_CENTER_Z))
        drawCanoliCanalAndGhatGround(rig, path, Vec3(CANOLI_CENTER_X, 2f, CANOLI_CENTER_Z))
        drawKottapuramLagoonAndTerminusGround(rig, path, Vec3(KOTTAPURAM_CENTER_X, 2f, KOTTAPURAM_CENTER_Z))
    }

    /**
     * Renders parallel 5.5m paved asphalt NH 66 Service Roads + concrete shoulder drainage strips
     * flanking both sides of the brand-new 22.0m 6-lane divided NH 66 Coastal Expressway along
     * all open at-grade highway stretches (suppressed on bridges, toll flares, beach promenades, and SH 61 junction).
     */
    private fun DrawScope.drawNh66ServiceRoadsGround(
        rig: CameraRig,
        path: Path,
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>,
        drawAll: Boolean
    ) {
        val matServiceAsphalt = Color(0xFF2D333D)
        val matVergeConcrete = Color(0xFF8C949E)
        val targetIds = setOf("ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR", "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM")

        fun inServiceRoadStretch(z: Float, y: Float): Boolean {
            if (y > 0.25f) return false
            if (z > 900f) return false                                      // Chavakkad Beach start
            if (z in (CHETTUVA_RAMP_SOUTH_Z - 35f)..(CHETTUVA_RAMP_NORTH_Z + 35f)) return false
            if (z in -1265f..-1175f) return false                           // SH 61 Triprayar/Kanjani Junction
            if (z in (NATTIKA_FLARE_SOUTH_END_Z - 25f)..(NATTIKA_FLARE_NORTH_START_Z + 25f)) return false
            if (z in -2505f..-2325f) return false                           // Triprayar Beach & Bus Stand
            if (z in (CANOLI_RAMP_SOUTH_Z - 35f)..(CANOLI_RAMP_NORTH_Z + 35f)) return false
            if (z in (KOTTAPURAM_RAMP_SOUTH_Z - 35f)..(KOTTAPURAM_RAMP_NORTH_Z + 35f)) return false
            if (z < -4360f) return false                                    // Moothakunnam turnaround bay
            return true
        }

        for (road in roads) {
            if (road.id !in targetIds) continue
            val frames = road.frames
            if (frames.size < 6) continue
            for (i in 0 until frames.size - 1 step 2) {
                val f0 = frames[i]
                val f1 = frames[min(frames.size - 1, i + 2)]
                if (!inServiceRoadStretch(f0.pos.z, f0.pos.y) || !inServiceRoadStretch(f1.pos.z, f1.pos.y)) continue
                if (!drawAll && hypot(f0.pos.x - camEye.x, f0.pos.z - camEye.z) > 460f) continue
                for (side in floatArrayOf(-1f, 1f)) {
                    val innerVerge0 = f0.halfWidthM + 0.15f
                    val outerVerge0 = f0.halfWidthM + 1.85f
                    val innerVerge1 = f1.halfWidthM + 0.15f
                    val outerVerge1 = f1.halfWidthM + 1.85f
                    val srvInner0 = f0.halfWidthM + 1.85f
                    val srvOuter0 = f0.halfWidthM + 7.25f
                    val srvInner1 = f1.halfWidthM + 1.85f
                    val srvOuter1 = f1.halfWidthM + 7.25f

                    // Concrete shoulder separator verge between main 6-lane carriageway and service road
                    if (rig.buildClippedQuadPath(
                            path,
                            Vec3(f0.pos.x + f0.right.x * side * innerVerge0, 0.055f, f0.pos.z + f0.right.z * side * innerVerge0),
                            Vec3(f0.pos.x + f0.right.x * side * outerVerge0, 0.055f, f0.pos.z + f0.right.z * side * outerVerge0),
                            Vec3(f1.pos.x + f1.right.x * side * outerVerge1, 0.055f, f1.pos.z + f1.right.z * side * outerVerge1),
                            Vec3(f1.pos.x + f1.right.x * side * innerVerge1, 0.055f, f1.pos.z + f1.right.z * side * innerVerge1)
                        )
                    ) {
                        drawPath(path, matVergeConcrete)
                    }
                    // 5.4m Paved Asphalt Service Road
                    if (rig.buildClippedQuadPath(
                            path,
                            Vec3(f0.pos.x + f0.right.x * side * srvInner0, 0.0545f, f0.pos.z + f0.right.z * side * srvInner0),
                            Vec3(f0.pos.x + f0.right.x * side * srvOuter0, 0.0545f, f0.pos.z + f0.right.z * side * srvOuter0),
                            Vec3(f1.pos.x + f1.right.x * side * srvOuter1, 0.0545f, f1.pos.z + f1.right.z * side * srvOuter1),
                            Vec3(f1.pos.x + f1.right.x * side * srvInner1, 0.0545f, f1.pos.z + f1.right.z * side * srvInner1)
                        )
                    ) {
                        drawPath(path, matServiceAsphalt)
                    }
                }
            }
        }
    }

    private fun DrawScope.drawNattikaTollApronGround(rig: CameraRig, path: Path, camEye: Vec3) {
        if (hypot(camEye.x - NATTIKA_CENTER_X, camEye.z - NATTIKA_CENTER_Z) > 520f) return
        val tx = NATTIKA_CENTER_X
        val tz = NATTIKA_CENTER_Z

        // Continuous Cosine S-curve PQC concrete pavement matching RoadFrames to < 2cm!
        val zStart = NATTIKA_FLARE_NORTH_START_Z
        val zEnd = NATTIKA_FLARE_SOUTH_END_Z
        val slices = 28
        for (i in 0 until slices) {
            val z0 = zStart + (zEnd - zStart) * (i.toFloat() / slices)
            val z1 = zStart + (zEnd - zStart) * ((i + 1).toFloat() / slices)
            val hw0 = nattikaFlareHalfWidthAtZ(z0) - 0.04f
            val hw1 = nattikaFlareHalfWidthAtZ(z1) - 0.04f
            if (rig.buildClippedQuadPath(
                    path,
                    Vec3(tx - hw0, 0.0565f, z0),
                    Vec3(tx + hw0, 0.0565f, z0),
                    Vec3(tx + hw1, 0.0565f, z1),
                    Vec3(tx - hw1, 0.0565f, z1)
                )
            ) {
                drawPath(path, MatConcreteApron)
            }
        }

        // Subtle concrete expansion joint lines & dark tire-track ribbons down each of the 10 lanes
        for (laneIdx in 0 until NATTIKA_LANE_COUNT) {
            val lx = nattikaLaneCenterX(laneIdx)
            if (rig.buildClippedQuadPath(
                    path,
                    Vec3(lx - 1.45f, 0.059f, tz - 26f),
                    Vec3(lx + 1.45f, 0.059f, tz - 26f),
                    Vec3(lx + 1.45f, 0.059f, tz + 26f),
                    Vec3(lx - 1.45f, 0.059f, tz + 26f)
                )
            ) {
                drawPath(path, MatTollLaneDark.copy(alpha = 0.34f))
            }
            // Crisp white FASTag stop bar & chevron guide ticks
            for (stopDir in floatArrayOf(-1f, 1f)) {
                val sbZ = tz + stopDir * 5.8f
                if (rig.buildClippedQuadPath(
                        path,
                        Vec3(lx - 1.75f, 0.062f, sbZ - 0.22f),
                        Vec3(lx + 1.75f, 0.062f, sbZ - 0.22f),
                        Vec3(lx + 1.75f, 0.062f, sbZ + 0.22f),
                        Vec3(lx - 1.75f, 0.062f, sbZ + 0.22f)
                    )
                ) {
                    drawPath(path, MatLedWhite)
                }
            }
        }

        // Yellow rumble warning bars on North & South approaches
        for (rz in floatArrayOf(tz + 54f, tz + 48f, tz + 42f, tz - 42f, tz - 48f, tz - 54f)) {
            val hw = (nattikaFlareHalfWidthAtZ(rz) - 2.4f).coerceAtLeast(7.5f)
            if (rig.buildClippedQuadPath(
                    path,
                    Vec3(tx - hw, 0.061f, rz - 0.26f),
                    Vec3(tx + hw, 0.061f, rz - 0.26f),
                    Vec3(tx + hw, 0.061f, rz + 0.26f),
                    Vec3(tx - hw, 0.061f, rz + 0.26f)
                )
            ) {
                drawPath(path, MatHazardYellow)
            }
        }

        // Outer service bypass lane dashed white guide lines (West & East bypass lanes)
        for (side in floatArrayOf(-1f, 1f)) {
            val bx = tx + side * 31.1f
            if (rig.buildClippedQuadPath(
                    path,
                    Vec3(bx - 0.14f, 0.061f, tz - 26f),
                    Vec3(bx + 0.14f, 0.061f, tz - 26f),
                    Vec3(bx + 0.14f, 0.061f, tz + 26f),
                    Vec3(bx - 0.14f, 0.061f, tz + 26f)
                )
            ) {
                drawPath(path, MatLedWhite)
            }
        }
    }

    private fun DrawScope.drawChettuvaEstuaryBedGround(rig: CameraRig, path: Path, camEye: Vec3) {
        if (hypot(camEye.x - CHETTUVA_CENTER_X, camEye.z - CHETTUVA_CENTER_Z) > 560f) return
        // Dark estuarine riverbed under animated GPU saltwater layer
        if (rig.buildClippedQuadPath(
                path,
                Vec3(-3965f, 0.040f, 95f),
                Vec3(-3355f, 0.040f, 95f),
                Vec3(-3355f, 0.040f, 335f),
                Vec3(-3965f, 0.040f, 335f)
            )
        ) {
            drawPath(path, Color(0xFF0E333E))
        }
    }

    private fun DrawScope.drawCanoliCanalAndGhatGround(rig: CameraRig, path: Path, camEye: Vec3) {
        if (hypot(camEye.x - CANOLI_CENTER_X, camEye.z - CANOLI_CENTER_Z) > 480f) return
        if (rig.buildClippedQuadPath(
                path,
                Vec3(-2485f, 0.040f, -2705f),
                Vec3(-1955f, 0.040f, -2705f),
                Vec3(-1955f, 0.040f, -2615f),
                Vec3(-2485f, 0.040f, -2615f)
            )
        ) {
            drawPath(path, Color(0xFF103B44))
        }
        // Granite courtyard around Triprayar Sree Rama Temple Water Ghat (East bank, North of Canal)
        if (rig.buildClippedQuadPath(
                path,
                Vec3(-2198f, 0.058f, -2620f),
                Vec3(-2120f, 0.058f, -2620f),
                Vec3(-2120f, 0.058f, -2565f),
                Vec3(-2198f, 0.058f, -2565f)
            )
        ) {
            drawPath(path, Color(0xFFA7A299))
        }
    }

    private fun DrawScope.drawKottapuramLagoonAndTerminusGround(rig: CameraRig, path: Path, camEye: Vec3) {
        if (hypot(camEye.x - KOTTAPURAM_CENTER_X, camEye.z - KOTTAPURAM_CENTER_Z) > 640f) return
        // Broad Periyar Lagoon / NW-3 channel bed under animated GPU water
        if (rig.buildClippedQuadPath(
                path,
                Vec3(-1945f, 0.040f, -4215f),
                Vec3(-1095f, 0.040f, -4215f),
                Vec3(-1095f, 0.040f, -3885f),
                Vec3(-1945f, 0.040f, -3885f)
            )
        ) {
            drawPath(path, Color(0xFF0C2E3C))
        }
        // Moothakunnam / Ernakulam Border South Turnaround Bus Bay Apron (West & East flare at Z = -4380..-4470)
        if (rig.buildClippedQuadPath(
                path,
                Vec3(MOOTHAKUNNAM_TERM_X - 30f, 0.056f, -4470f),
                Vec3(MOOTHAKUNNAM_TERM_X + 30f, 0.056f, -4470f),
                Vec3(MOOTHAKUNNAM_TERM_X + 30f, 0.056f, -4380f),
                Vec3(MOOTHAKUNNAM_TERM_X - 30f, 0.056f, -4380f)
            )
        ) {
            drawPath(path, Color(0xFF3E444D))
        }
    }

    // =========================================================================
    // STATIC 3D GEOMETRY CACHE (BAKED INTO SPATIAL-CHUNKED GPU LANDMARK VBO)
    // =========================================================================

    @Volatile
    private var cachedZone5StaticFaces: List<QuadFace3D>? = null

    fun collectAllZone5StaticFaces(
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        val cached = cachedZone5StaticFaces ?: buildAllZone5StaticFaces(roads).also { cachedZone5StaticFaces = it }
        outFaces.addAll(cached)
    }

    fun collectZone5FacesNearCamera(
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        if (camEye.x > -900f) return
        collectAllZone5StaticFaces(roads, outFaces)
    }

    private fun buildAllZone5StaticFaces(roads: List<ThrissurOsmRoad>): List<QuadFace3D> {
        val out = ArrayList<QuadFace3D>(6400)
        buildChavakkadSeawallAndBoats3D(out)
        buildChettuvaBackwatersBridgeAndEstuary3D(out)
        buildNattikaTollPlaza3D(out)
        buildTriprayarCanoliCanalBridgeAndTempleGhat3D(out)
        buildKottapuramNw3BridgeAndTerminus3D(out)
        buildNh66HighwayFurniture3D(out, roads)
        buildCoastalCorridorScenery3D(out, roads)
        return out
    }

    // =========================================================================
    // 1. NATTIKA TOLL PLAZA (1:1 ARCHITECTURAL MATCH TO REAL REFERENCE PHOTO!)
    //    - 10 lanes (5 Southbound + 5 Northbound) + 2 Free Outer Service Bypass Lanes
    //    - Crisp two-story bright white concrete overhead admin building ON TOP of canopy
    //    - Signature bright Royal Blue sloped window awnings with dark steel side brackets
    //    - Full-width deep-green NHAI trilingual horizontal fascia signboard
    //    - Tall slender square white concrete columns + dark overhead RFID/CCTV boxes
    //    - Exterior industrial grey open-riser steel staircase ascending to upper control floor
    //    - Red-painted steel outer lane safety portal frames & white operator booths
    // =========================================================================
    private fun buildNattikaTollPlaza3D(out: MutableList<QuadFace3D>) {
        val tx = NATTIKA_CENTER_X
        val tz = NATTIKA_CENTER_Z
        val canopyHW = NATTIKA_CANOPY_HALF_W  // 28.6m
        val canopyD = 5.40f                   // Canopy depth Z = tz ± 5.4m (10.8m deep)
        val soffitY = NATTIKA_CANOPY_CLEAR_Y  // 5.65m clear overhead!

        val nhaiFasciaTex = Zone5ArchitecturalTextures.createNattikaNhaiFasciaSpec()
        val mainUpperFacadeTex = Zone5ArchitecturalTextures.createNattikaUpperFacadeSpec(
            cacheKey = "ZONE5_NATTIKA_UPPER_MAIN_V1",
            windowColumns = 6,
            twoStorey = false
        )
        val eastWingFacadeTex = Zone5ArchitecturalTextures.createNattikaUpperFacadeSpec(
            cacheKey = "ZONE5_NATTIKA_UPPER_EAST_V1",
            windowColumns = 2,
            twoStorey = true
        )
        val boothFacadeTex = Zone3ArchitecturalTextures.createTollBoothFacadeSpec()

        // A. 11 CONCRETE TOLL ISLANDS, YELLOW-BLACK CRASH BARRELS, TALL SQUARE WHITE COLUMNS & BOOTHS
        //    (Strictly within ±0.64m of island center -> 4.12m crystal-clear unobstructed driving width!)
        for (iIdx in 0..NATTIKA_LANE_COUNT) {
            val ix = nattikaIslandX(iIdx)
            val hwBase = NATTIKA_ISLAND_HALF_W
            val hwTop = 0.52f

            // Concrete barrier island with yellow-black chevron end curbs
            val segs = 10
            val z0Base = tz - 8.5f
            val segLen = 17.0f / segs
            for (s in 0 until segs) {
                val sz0 = z0Base + s * segLen
                val sz1 = sz0 + segLen
                val isEnd = s < 2 || s >= segs - 2
                val col = when {
                    isEnd && s % 2 == 0 -> MatHazardYellow
                    isEnd -> MatHazardBlack
                    else -> MatConcreteDeck
                }
                addAxisBox(out, ix - hwBase, 0.06f, sz0, ix + hwBase, 0.40f, sz1, col, MatConcreteDeckShadow, col)
            }

            // Bullnose & Yellow-Black Crash Attenuator Barrels at North & South ends of each island
            for (dirZ in floatArrayOf(-1f, 1f)) {
                val b1Z = tz + dirZ * 9.55f
                addAxisBox(out, ix - 0.42f, 0.06f, b1Z - 0.42f, ix + 0.42f, 1.05f, b1Z + 0.42f, MatHazardYellow, MatHazardYellow, MatHazardYellow)
                addAxisBox(out, ix - 0.44f, 0.56f, b1Z - 0.44f, ix + 0.44f, 0.72f, b1Z + 0.44f, MatHazardBlack, MatHazardBlack, MatHazardBlack)
                val b2Z = tz + dirZ * 10.45f
                addAxisBox(out, ix - 0.34f, 0.06f, b2Z - 0.34f, ix + 0.34f, 0.88f, b2Z + 0.34f, MatHazardYellow, MatHazardYellow, MatHazardYellow)
                addAxisBox(out, ix - 0.36f, 0.44f, b2Z - 0.36f, ix + 0.36f, 0.58f, b2Z + 0.36f, MatHazardBlack, MatHazardBlack, MatHazardBlack)
            }

            // Tall Slender Square White Concrete Columns (EXACT match to reference photo's rhythmic white pillars!)
            // Placed in pairs (front & back row at Z = tz ± 3.65m) cleanly on the concrete islands!
            for (colDirZ in floatArrayOf(-3.65f, 3.65f)) {
                val cz = tz + colDirZ
                // Yellow-black protective base collar
                addAxisBox(out, ix - 0.30f, 0.40f, cz - 0.30f, ix + 0.30f, 0.92f, cz + 0.30f, MatHazardYellow, MatHazardBlack, MatHazardYellow)
                // Crisp bright-white square concrete column shaft rising straight to canopy soffit
                addAxisBox(out, ix - 0.25f, 0.92f, cz - 0.26f, ix + 0.25f, soffitY + 0.04f, cz + 0.26f, MatNattikaWhite, MatNattikaWhiteShade, MatNattikaWhite)
            }

            // Operator Toll Cabins on inner islands (iIdx = 1..9, width ±0.52m, nestled cleanly between columns)
            if (iIdx in 1 until NATTIKA_LANE_COUNT) {
                val bw = 0.52f
                val bz0 = tz - 2.15f
                val bz1 = tz + 2.15f
                addAxisBox(out, ix - bw + 0.02f, 0.40f, bz0, ix + bw - 0.02f, 2.75f, bz1, MatNattikaWhite, MatNattikaWhiteShade, MatNattikaNhaiGreen)
                // Panoramic tinted operator booth windows facing both lanes
                out.add(
                    QuadFace3D(
                        Vec3(ix - bw - 0.01f, 0.40f, bz0),
                        Vec3(ix - bw - 0.01f, 0.40f, bz1),
                        Vec3(ix - bw - 0.01f, 2.75f, bz1),
                        Vec3(ix - bw - 0.01f, 2.75f, bz0),
                        MatNattikaWhite,
                        signboard = boothFacadeTex
                    )
                )
                out.add(
                    QuadFace3D(
                        Vec3(ix + bw + 0.01f, 0.40f, bz1),
                        Vec3(ix + bw + 0.01f, 0.40f, bz0),
                        Vec3(ix + bw + 0.01f, 2.75f, bz0),
                        Vec3(ix + bw + 0.01f, 2.75f, bz1),
                        MatNattikaWhite,
                        signboard = boothFacadeTex
                    )
                )
                // Split-AC outdoor unit box mounted neatly on top of booth roof
                addAxisBox(out, ix - 0.30f, 2.75f, tz - 0.55f, ix + 0.30f, 3.22f, tz + 0.25f, MatNattikaRoofSlab, MatGalvanizedDark, MatNattikaWhite)
            }
        }

        // B. RED-PAINTED STEEL OUTER LANE SAFETY PORTAL FRAMES (matches bottom-right of user's photo!)
        for (side in floatArrayOf(-1f, 1f)) {
            val rx = tx + side * (canopyHW + 1.4f)
            val rz0 = tz - 3.8f
            val rz1 = tz + 3.8f
            addAxisBox(out, rx - 0.14f, 0.06f, rz0 - 0.14f, rx + 0.14f, 3.45f, rz0 + 0.14f, MatNattikaRedFrame, MatNattikaRedFrame, MatNattikaRedFrame)
            addAxisBox(out, rx - 0.14f, 0.06f, rz1 - 0.14f, rx + 0.14f, 3.45f, rz1 + 0.14f, MatNattikaRedFrame, MatNattikaRedFrame, MatNattikaRedFrame)
            addAxisBox(out, rx - 0.14f, 3.25f, rz0 - 0.14f, rx + 0.14f, 3.55f, rz1 + 0.14f, MatNattikaRedFrame, MatNattikaRedFrame, MatNattikaRedFrame)
            // Diagonal red bracing struts
            addDiagonalBeam(out, Vec3(rx, 0.20f, rz0), Vec3(rx, 3.35f, rz0 + 1.8f), 0.10f, MatNattikaRedFrame, MatNattikaRedFrame)
            addDiagonalBeam(out, Vec3(rx, 0.20f, rz1), Vec3(rx, 3.35f, rz1 - 1.8f), 0.10f, MatNattikaRedFrame, MatNattikaRedFrame)
        }

        // C. CRISP WHITE FLAT CANOPY SOFFIT SLAB + DEEP-GREEN TRILINGUAL NHAI FASCIA BAND (EXACT PHOTO MATCH!)
        val fasciaBotY = soffitY
        val fasciaTopY = soffitY + 1.50f // 5.65m .. 7.15m
        addAxisBox(
            out,
            tx - canopyHW, fasciaBotY, tz - canopyD,
            tx + canopyHW, fasciaTopY, tz + canopyD,
            MatNattikaWhite, MatNattikaNhaiGreen, MatNattikaNhaiGreen
        )

        // Full-width Textured Deep-Green Trilingual NHAI Fascia on BOTH North (+Z) and South (-Z) faces!
        // North face (seen by Southbound traffic approaching from Chavakkad/Thalikulam)
        out.add(
            QuadFace3D(
                Vec3(tx + canopyHW, fasciaBotY, tz + canopyD + 0.04f),
                Vec3(tx - canopyHW, fasciaBotY, tz + canopyD + 0.04f),
                Vec3(tx - canopyHW, fasciaTopY, tz + canopyD + 0.04f),
                Vec3(tx + canopyHW, fasciaTopY, tz + canopyD + 0.04f),
                MatNattikaNhaiGreen,
                signboard = nhaiFasciaTex
            )
        )
        // South face (seen by Northbound traffic approaching from Triprayar/Kodungallur)
        out.add(
            QuadFace3D(
                Vec3(tx - canopyHW, fasciaBotY, tz - canopyD - 0.04f),
                Vec3(tx + canopyHW, fasciaBotY, tz - canopyD - 0.04f),
                Vec3(tx + canopyHW, fasciaTopY, tz - canopyD - 0.04f),
                Vec3(tx - canopyHW, fasciaTopY, tz - canopyD - 0.04f),
                MatNattikaNhaiGreen,
                signboard = nhaiFasciaTex
            )
        )

        // D. TWO-STORY BRIGHT WHITE CONCRETE OVERHEAD CONTROL BUILDING DIRECTLY ON TOP OF CANOPY!
        //    Exact massing from reference photo:
        //    - Main long bright-white block spanning West/Center (X = tx - 27.6 .. tx + 12.4, Y = 7.15 .. 11.45)
        //    - Stepped taller right/East two-story wing (X = tx + 12.4 .. tx + 27.6, Y = 7.15 .. 13.75)
        //    - Tall white rooftop stairwell / water-tank penthouse tower rising behind the transition (Y = 11.45 .. 16.05)
        val mainX0 = tx - 27.6f
        val mainX1 = tx + 12.4f
        val wingX0 = tx + 12.4f
        val wingX1 = tx + 27.6f
        val bldD = 4.65f
        val mainTopY = 11.45f
        val wingTopY = 13.75f

        // 1. Main white concrete overhead wing
        addAxisBox(out, mainX0, fasciaTopY, tz - bldD, mainX1, mainTopY, tz + bldD, MatNattikaRoofSlab, MatNattikaWhite, MatNattikaWhiteShade)
        // Parapet coping trim on top of main wing
        addAxisBox(out, mainX0 - 0.12f, mainTopY, tz - bldD - 0.12f, mainX1 + 0.12f, mainTopY + 0.28f, tz + bldD + 0.12f, MatNattikaWhite, MatNattikaWhiteShade, MatNattikaWhiteShade)
        // Textured barred-window facades on North & South faces of main wing
        out.add(
            QuadFace3D(
                Vec3(mainX1, fasciaTopY, tz + bldD + 0.03f),
                Vec3(mainX0, fasciaTopY, tz + bldD + 0.03f),
                Vec3(mainX0, mainTopY, tz + bldD + 0.03f),
                Vec3(mainX1, mainTopY, tz + bldD + 0.03f),
                MatNattikaWhite,
                signboard = mainUpperFacadeTex
            )
        )
        out.add(
            QuadFace3D(
                Vec3(mainX0, fasciaTopY, tz - bldD - 0.03f),
                Vec3(mainX1, fasciaTopY, tz - bldD - 0.03f),
                Vec3(mainX1, mainTopY, tz - bldD - 0.03f),
                Vec3(mainX0, mainTopY, tz - bldD - 0.03f),
                MatNattikaWhite,
                signboard = mainUpperFacadeTex
            )
        )

        // 2. Stepped taller East/Right white concrete wing (EXACT match to taller right half in photo!)
        addAxisBox(out, wingX0, fasciaTopY, tz - bldD, wingX1, wingTopY, tz + bldD, MatNattikaRoofSlab, MatNattikaWhite, MatNattikaWhiteShade)
        addAxisBox(out, wingX0 - 0.12f, wingTopY, tz - bldD - 0.12f, wingX1 + 0.12f, wingTopY + 0.30f, tz + bldD + 0.12f, MatNattikaWhite, MatNattikaWhiteShade, MatNattikaWhiteShade)
        out.add(
            QuadFace3D(
                Vec3(wingX1, fasciaTopY, tz + bldD + 0.03f),
                Vec3(wingX0, fasciaTopY, tz + bldD + 0.03f),
                Vec3(wingX0, wingTopY, tz + bldD + 0.03f),
                Vec3(wingX1, wingTopY, tz + bldD + 0.03f),
                MatNattikaWhite,
                signboard = eastWingFacadeTex
            )
        )
        out.add(
            QuadFace3D(
                Vec3(wingX0, fasciaTopY, tz - bldD - 0.03f),
                Vec3(wingX1, fasciaTopY, tz - bldD - 0.03f),
                Vec3(wingX1, wingTopY, tz - bldD - 0.03f),
                Vec3(wingX0, wingTopY, tz - bldD - 0.03f),
                MatNattikaWhite,
                signboard = eastWingFacadeTex
            )
        )

        // 3. Rooftop White Concrete Stairwell / Water-Tank Penthouse Tower (visible at top center-right of photo!)
        addAxisBox(out, tx + 6.8f, mainTopY, tz - 3.6f, tx + 12.6f, 16.05f, tz + 3.2f, MatNattikaRoofSlab, MatNattikaWhite, MatNattikaWhiteShade)

        // E. SIGNATURE 3D ROYAL-BLUE SLOPED WINDOW SUNSHADE AWNINGS WITH DARK SIDE BRACKETS!
        //    Projecting cleanly off BOTH North (+Z) and South (-Z) upper facades right above every window!
        fun addBlueWindowAwning(cx: Float, topY: Float, halfW: Float, zFace: Float, outDirZ: Float) {
            val zOut = zFace + outDirZ * 0.72f
            val yFront = topY - 0.34f
            // Sloped royal-blue corrugated metal awning sheet
            out.add(
                QuadFace3D(
                    Vec3(cx - halfW, topY, zFace),
                    Vec3(cx + halfW, topY, zFace),
                    Vec3(cx + halfW, yFront, zOut),
                    Vec3(cx - halfW, yFront, zOut),
                    MatNattikaBlueAwning
                )
            )
            // Underside shadow trim + front lip fascia
            addAxisBox(
                out,
                cx - halfW, yFront - 0.08f, min(zOut, zOut - outDirZ * 0.08f),
                cx + halfW, yFront + 0.04f, max(zOut, zOut - outDirZ * 0.08f),
                MatNattikaBlueAwningDark, MatNattikaBlueAwningDark, MatNattikaBlueAwningDark
            )
            // Twin dark steel diagonal side brackets holding the awning to the white wall
            for (sx in floatArrayOf(cx - halfW + 0.08f, cx + halfW - 0.08f)) {
                addDiagonalBeam(
                    out,
                    Vec3(sx, topY - 0.56f, zFace),
                    Vec3(sx, yFront, zOut),
                    0.05f,
                    MatNattikaStairDark,
                    MatNattikaStairDark
                )
            }
        }

        // Main wing staggered blue awnings (6 bays, matching photo rhythm!)
        val mainColW = (mainX1 - mainX0) / 6f
        for (i in 0 until 6) {
            val cx = mainX0 + (i + 0.5f) * mainColW
            val topY = if (i % 2 == 0) fasciaTopY + (mainTopY - fasciaTopY) * 0.54f else fasciaTopY + (mainTopY - fasciaTopY) * 0.84f
            val awHW = mainColW * 0.26f
            addBlueWindowAwning(cx, topY, awHW, tz + bldD + 0.04f, +1f)
            addBlueWindowAwning(cx, topY, awHW, tz - bldD - 0.04f, -1f)
        }
        // Taller East wing two-level blue awnings (2 wide bays × 2 storeys!)
        val wingColW = (wingX1 - wingX0) / 2f
        for (i in 0 until 2) {
            val cx = wingX0 + (i + 0.5f) * wingColW
            val awHW = wingColW * 0.27f
            val yHi = fasciaTopY + (wingTopY - fasciaTopY) * 0.88f
            val yLo = fasciaTopY + (wingTopY - fasciaTopY) * 0.44f
            addBlueWindowAwning(cx, yHi, awHW, tz + bldD + 0.04f, +1f)
            addBlueWindowAwning(cx, yHi, awHW, tz - bldD - 0.04f, -1f)
            addBlueWindowAwning(cx, yLo, awHW, tz + bldD + 0.04f, +1f)
            addBlueWindowAwning(cx, yLo, awHW, tz - bldD - 0.04f, -1f)
        }

        // F. EXTERIOR INDUSTRIAL GREY OPEN-RISER STEEL STAIRCASE (EXACT match to photo!)
        //    Rises outside East outer column bay (X = tx + 27.9 .. tx + 30.2, safely clear of East bypass lane at +31.1!)
        val stairX0 = tx + canopyHW - 1.2f
        val stairX1 = tx + canopyHW + 0.4f
        val stairBotZ = tz + 4.8f
        val stairTopZ = tz - 2.6f
        val stairSteps = 14
        // Twin inclined steel stringers & top handrails
        for (sx in floatArrayOf(stairX0, stairX1)) {
            addDiagonalBeam(out, Vec3(sx, 0.25f, stairBotZ), Vec3(sx, fasciaTopY + 0.15f, stairTopZ), 0.14f, MatNattikaStairSteel, MatNattikaStairDark)
            addDiagonalBeam(out, Vec3(sx, 1.35f, stairBotZ), Vec3(sx, fasciaTopY + 1.25f, stairTopZ), 0.07f, MatGalvanizedDark, MatNattikaStairDark)
        }
        // Horizontal steel stair treads
        for (st in 1 until stairSteps) {
            val f = st.toFloat() / stairSteps
            val sy = 0.25f + (fasciaTopY - 0.10f) * f
            val sz = stairBotZ + (stairTopZ - stairBotZ) * f
            addAxisBox(out, stairX0, sy - 0.05f, sz - 0.18f, stairX1, sy + 0.05f, sz + 0.18f, MatNattikaStairSteel, MatNattikaStairDark, MatNattikaStairDark)
        }

        // G. OVERHEAD CCTV / RFID TRANSCEIVER BOXES & ELECTRONIC LANE STATUS MATRICES (10 LANES)
        for (laneIdx in 0 until NATTIKA_LANE_COUNT) {
            val lx = nattikaLaneCenterX(laneIdx)
            val active = isNattikaLaneActiveFastag(laneIdx)
            val matrixTex = Zone5ArchitecturalTextures.createNattikaLaneMatrixSpec(
                cacheKey = "ZONE5_NATTIKA_LANE_${laneIdx + 1}",
                laneNumber = laneIdx + 1,
                activeFastag = active
            )
            // Dark hanging RFID / CCTV transceiver pods under canopy soffit (matching photo!)
            for (podZ in floatArrayOf(tz + 4.1f, tz - 4.1f)) {
                addAxisBox(out, lx - 0.26f, soffitY - 0.44f, podZ - 0.18f, lx + 0.26f, soffitY, podZ + 0.18f, MatNattikaStairDark, MatNattikaStairDark, MatNattikaStairDark)
            }
            // Electronic overhead lane status LED board on North & South canopy edges
            for (dirZ in floatArrayOf(-1f, 1f)) {
                val sz = tz + dirZ * (canopyD + 0.18f)
                out.add(
                    QuadFace3D(
                        Vec3(lx - 1.15f, soffitY - 0.92f, sz),
                        Vec3(lx + 1.15f, soffitY - 0.92f, sz),
                        Vec3(lx + 1.15f, soffitY - 0.06f, sz),
                        Vec3(lx - 1.15f, soffitY - 0.06f, sz),
                        if (active) MatLedGreen else MatLedRed,
                        signboard = matrixTex
                    )
                )
            }
        }

        // H. TWIN 22m HIGH-MAST FLOODLIGHT TOWERS (strictly outside West & East outer service bypass lanes!)
        for (mx in floatArrayOf(tx - 39.2f, tx + 39.2f)) {
            val mz = tz + 15.0f
            addAxisBox(out, mx - 0.32f, 0.06f, mz - 0.32f, mx + 0.32f, 22.0f, mz + 0.32f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            addAxisBox(out, mx - 1.45f, 21.8f, mz - 1.45f, mx + 1.45f, 22.35f, mz + 1.45f, MatGalvanizedDark, MatLedWarm, MatLedWarm)
        }
    }

    // =========================================================================
    // 2. CHETTUVA BACKWATERS BRIDGE (360m SPAN), MANGROVE ISLANDS,
    //    4 TRADITIONAL CHINESE FISHING NETS (CHEENA VALA) & KETTUVALLAMS
    // =========================================================================
    private fun buildChettuvaBackwatersBridgeAndEstuary3D(out: MutableList<QuadFace3D>) {
        val cx = CHETTUVA_CENTER_X
        val hw = CHETTUVA_DECK_HALF_W // 9.00m
        val railingTex = Zone3ArchitecturalTextures.createBridgeSlottedRailingSpec()
        val pwdSign = Zone5ArchitecturalTextures.createZone5PwdBridgeSignSpec(
            cacheKey = "ZONE5_CHETTUVA_BRIDGE_SIGN",
            malayalamTitle = "ചേറ്റുവ പാലം • NH 66",
            englishTitle = "CHETTUVA BACKWATERS BRIDGE",
            subtitle = "350m ESTUARY SPAN • KERALA PWD / NHAI"
        )
        val netMeshTex = Zone5ArchitecturalTextures.createChineseNetMeshSpec()
        val houseboatRoofTex = Zone5ArchitecturalTextures.createKettuvallamRoofSpec()

        // A. Prestressed Concrete Box-Girder Bridge Superstructure, Parapets & Slotted Handrails
        val segs = 28
        val z0All = CHETTUVA_RAMP_SOUTH_Z
        val z1All = CHETTUVA_RAMP_NORTH_Z
        for (i in 0 until segs) {
            val za = z0All + (z1All - z0All) * (i.toFloat() / segs)
            val zb = z0All + (z1All - z0All) * ((i + 1).toFloat() / segs)
            val ya = computeZone5RoadSurfaceY(cx, za)
            val yb = computeZone5RoadSurfaceY(cx, zb)
            val onWater = (za + zb) * 0.5f in (CHETTUVA_DECK_SOUTH_Z - 4f)..(CHETTUVA_DECK_NORTH_Z + 4f)
            val girderDepth = if (onWater) 1.55f else 0.72f

            // Concrete deck girder fascia & soffit along West & East outer edges
            for (side in floatArrayOf(-1f, 1f)) {
                val gx = cx + side * (hw + 0.15f)
                out.add(
                    QuadFace3D(
                        Vec3(gx, ya - girderDepth, za),
                        Vec3(gx, yb - girderDepth, zb),
                        Vec3(gx, yb + 0.04f, zb),
                        Vec3(gx, ya + 0.04f, za),
                        MatConcreteDeckShadow
                    )
                )
                // Raised pedestrian walkway curb & white concrete parapet wall (strictly outside ±9.00m!)
                val pxIn = cx + side * (hw + 0.10f)
                val pxOut = cx + side * (hw + 1.25f)
                out.add(
                    QuadFace3D(
                        Vec3(pxIn, ya + 0.22f, za),
                        Vec3(pxOut, ya + 0.22f, za),
                        Vec3(pxOut, yb + 0.22f, zb),
                        Vec3(pxIn, yb + 0.22f, zb),
                        MatConcreteDeck
                    )
                )
                // White concrete parapet low plinth + slotted railing + galvanized top tubular handrail
                val railX = cx + side * (hw + 1.18f)
                out.add(
                    QuadFace3D(
                        Vec3(railX, ya + 0.22f, za),
                        Vec3(railX, yb + 0.22f, zb),
                        Vec3(railX, yb + 1.28f, zb),
                        Vec3(railX, ya + 1.28f, za),
                        MatNattikaWhite,
                        signboard = railingTex
                    )
                )
                // Galvanized top steel handrail bar
                out.add(
                    QuadFace3D(
                        Vec3(railX - 0.08f, ya + 1.28f, za),
                        Vec3(railX + 0.08f, ya + 1.28f, za),
                        Vec3(railX + 0.08f, yb + 1.28f, zb),
                        Vec3(railX - 0.08f, yb + 1.28f, zb),
                        MatGalvanized
                    )
                )
            }
            // Bridge soffit underside (visible when looking from water/approaches)
            if (onWater) {
                out.add(
                    QuadFace3D(
                        Vec3(cx - hw - 0.2f, ya - girderDepth, za),
                        Vec3(cx + hw + 0.2f, ya - girderDepth, za),
                        Vec3(cx + hw + 0.2f, yb - girderDepth, zb),
                        Vec3(cx - hw - 0.2f, yb - girderDepth, zb),
                        MatConcretePierDark
                    )
                )
            }
            // Central low concrete median divider along bridge deck (width ±0.26m -> leaves two 8.74m carriageways!)
            out.add(
                QuadFace3D(
                    Vec3(cx - 0.24f, ya + 0.62f, za),
                    Vec3(cx + 0.24f, ya + 0.62f, za),
                    Vec3(cx + 0.24f, yb + 0.62f, zb),
                    Vec3(cx - 0.24f, yb + 0.62f, zb),
                    MatConcreteDeck
                )
            )
            for (ms in floatArrayOf(-0.24f, 0.24f)) {
                out.add(
                    QuadFace3D(
                        Vec3(cx + ms, ya, za),
                        Vec3(cx + ms, yb, zb),
                        Vec3(cx + ms, yb + 0.62f, zb),
                        Vec3(cx + ms, ya + 0.62f, za),
                        MatConcreteDeckShadow
                    )
                )
            }
        }

        // B. 7 Pairs of Massive Hydrodynamic Concrete Bridge Piers with Monsoon Waterline Weathering
        for (pIdx in 0..6) {
            val pz = CHETTUVA_DECK_SOUTH_Z + 12f + pIdx * 36.0f
            val capY = CHETTUVA_DECK_Y - 1.50f
            for (pxOff in floatArrayOf(-5.6f, 5.6f)) {
                val px = cx + pxOff
                // Wet moss-stained tidal base
                addAxisBox(out, px - 1.45f, 0.02f, pz - 2.25f, px + 1.45f, 1.45f, pz + 2.25f, MatConcreteMossWet, MatConcreteMossWet, MatConcreteMossWet)
                // Main concrete pier shaft
                addAxisBox(out, px - 1.15f, 1.45f, pz - 1.75f, px + 1.15f, capY - 0.85f, pz + 1.75f, MatConcretePier, MatConcretePier, MatConcretePierDark)
            }
            // Cross hammerhead pier cap beam uniting twin piers
            addAxisBox(out, cx - hw - 0.2f, capY - 0.85f, pz - 1.85f, cx + hw + 0.2f, capY, pz + 1.85f, MatConcreteDeck, MatConcretePier, MatConcretePierDark)
        }

        // Twin-arm marine galvanized highway streetlamps along Chettuva Bridge center median
        var lampZ = CHETTUVA_DECK_SOUTH_Z + 20f
        while (lampZ <= CHETTUVA_DECK_NORTH_Z - 15f) {
            val ly = computeZone5RoadSurfaceY(cx, lampZ)
            addAxisBox(out, cx - 0.10f, ly + 0.60f, lampZ - 0.10f, cx + 0.10f, ly + 7.20f, lampZ + 0.10f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            addAxisBox(out, cx - 1.95f, ly + 7.10f, lampZ - 0.12f, cx + 1.95f, ly + 7.28f, lampZ + 0.12f, MatGalvanized, MatLedWarm, MatLedWarm)
            lampZ += 40.0f
        }

        // Blue Kerala PWD Bridge Signboards at North & South approaches (strictly outside ±10.8m!)
        for (sz in floatArrayOf(CHETTUVA_RAMP_NORTH_Z + 6f, CHETTUVA_RAMP_SOUTH_Z - 6f)) {
            val sx = cx - hw - 2.6f
            addAxisBox(out, sx - 0.10f, 0.06f, sz - 0.10f, sx + 0.10f, 3.85f, sz + 0.10f, MatGalvanizedDark, MatGalvanized, MatGalvanized)
            out.add(
                QuadFace3D(
                    Vec3(sx - 2.1f, 2.35f, sz),
                    Vec3(sx + 2.1f, 2.35f, sz),
                    Vec3(sx + 2.1f, 3.75f, sz),
                    Vec3(sx - 2.1f, 3.75f, sz),
                    Color(0xFF0A4E94),
                    signboard = pwdSign
                )
            )
        }

        // C. SPRAWLING TIDAL MANGROVE ISLANDS (KANDAL KAADUKAL) WITH TANGLED AERIAL ROOTS
        val mangroveIslands = arrayOf(
            floatArrayOf(-3748f, 275f, 24f, 16f),
            floatArrayOf(-3785f, 152f, 28f, 18f),
            floatArrayOf(-3582f, 295f, 26f, 17f),
            floatArrayOf(-3545f, 175f, 22f, 15f),
            floatArrayOf(-3842f, 228f, 25f, 19f),
            floatArrayOf(-3490f, 248f, 30f, 20f)
        )
        for ((idx, isl) in mangroveIslands.withIndex()) {
            val ix = isl[0]; val iz = isl[1]; val rx = isl[2]; val rz = isl[3]
            // Mud/silt tidal bank emerging above saltwater
            addAxisBox(out, ix - rx, 0.06f, iz - rz, ix + rx, 0.52f, iz + rz, MatMangroveMud, MatMangroveMud, MatMangroveMud)
            // Tangled arching aerial stilt roots around island perimeter
            for (rIdx in 0 until 8) {
                val ang = (rIdx.toFloat() / 8f) * (2f * PI.toFloat()) + idx * 0.4f
                val rBaseX = ix + cos(ang) * (rx * 0.85f)
                val rBaseZ = iz + sin(ang) * (rz * 0.85f)
                val rOutX = ix + cos(ang) * (rx * 1.12f)
                val rOutZ = iz + sin(ang) * (rz * 1.12f)
                addDiagonalBeam(out, Vec3(rBaseX, 1.35f, rBaseZ), Vec3(rOutX, 0.06f, rOutZ), 0.14f, MatMangroveRoot, MatMangroveRoot)
            }
            // Layered glossy emerald mangrove canopy domes
            addAxisBox(out, ix - rx * 0.88f, 0.52f, iz - rz * 0.84f, ix + rx * 0.88f, 3.15f, iz + rz * 0.84f, MatMangroveLeafDark, MatMangroveLeafDark, MatMangroveLeafBright)
            addAxisBox(out, ix - rx * 0.56f, 3.15f, iz - rz * 0.54f, ix + rx * 0.56f, 5.10f, iz + rz * 0.54f, MatMangroveLeafBright, MatMangroveLeafBright, MatMangroveLeafDark)
        }

        // D. 4 GIANT TRADITIONAL CHINESE FISHING NETS (CHEENA VALA) WITH TEAK FULCRUMS & STONE COUNTERWEIGHTS!
        val fishingNets = arrayOf(
            floatArrayOf(-3704f, 316f, -1f), // West-North bank cantilevered South-West over water
            floatArrayOf(-3714f, 118f, +1f), // West-South bank cantilevered North-West over water
            floatArrayOf(-3620f, 314f, -1f), // East-North bank cantilevered South-East over water
            floatArrayOf(-3616f, 120f, +1f)  // East-South bank cantilevered North-East over water
        )
        for (net in fishingNets) {
            val nx = net[0]
            val nz = net[1]
            val dirZ = net[2] // Direction net booms reach over open water
            // Wooden timber pile jetty platform
            addAxisBox(out, nx - 2.8f, 0.08f, nz - 2.6f, nx + 2.8f, 1.15f, nz + 2.6f, MatTeakMid, MatTeakDark, MatTeakDark)
            // Teak A-frame fulcrum posts
            addDiagonalBeam(out, Vec3(nx - 2.0f, 1.10f, nz), Vec3(nx - 0.8f, 5.4f, nz + dirZ * 1.4f), 0.22f, MatTeakDark, MatTeakMid)
            addDiagonalBeam(out, Vec3(nx + 2.0f, 1.10f, nz), Vec3(nx + 0.8f, 5.4f, nz + dirZ * 1.4f), 0.22f, MatTeakDark, MatTeakMid)
            // Twin sweeping cantilevered bamboo main arms arching out 12.5m over the estuary!
            val tipZ = nz + dirZ * 13.0f
            val tailZ = nz - dirZ * 4.5f
            addDiagonalBeam(out, Vec3(nx - 1.2f, 2.2f, tailZ), Vec3(nx - 4.2f, 8.2f, tipZ), 0.16f, MatBamboo, MatBambooDark)
            addDiagonalBeam(out, Vec3(nx + 1.2f, 2.2f, tailZ), Vec3(nx + 4.2f, 8.2f, tipZ), 0.16f, MatBamboo, MatBambooDark)
            // Cross bamboo spreader beam & 4 curved drooping ribs holding the square net
            addDiagonalBeam(out, Vec3(nx - 4.2f, 8.2f, tipZ), Vec3(nx + 4.2f, 8.2f, tipZ), 0.14f, MatBamboo, MatBambooDark)
            val netCenterZ = nz + dirZ * 12.4f
            val netHalf = 4.6f
            // 4 suspension ropes from boom tips down to the dipping square nylon fishing net
            for (cxOff in floatArrayOf(-netHalf, netHalf)) {
                for (czOff in floatArrayOf(-netHalf * 0.7f, netHalf * 0.7f)) {
                    addDiagonalBeam(
                        out,
                        Vec3(nx + (if (cxOff < 0f) -4.0f else 4.0f), 8.1f, tipZ),
                        Vec3(nx + cxOff, 0.75f, netCenterZ + czOff),
                        0.06f,
                        MatBambooDark,
                        MatTeakDark
                    )
                }
            }
            // Alpha-cutout square dark nylon diamond fishing net dipped cleanly over the estuary water!
            out.add(
                QuadFace3D(
                    Vec3(nx - netHalf, 0.72f, netCenterZ - netHalf * 0.7f),
                    Vec3(nx + netHalf, 0.72f, netCenterZ - netHalf * 0.7f),
                    Vec3(nx + netHalf, 0.72f, netCenterZ + netHalf * 0.7f),
                    Vec3(nx - netHalf, 0.72f, netCenterZ + netHalf * 0.7f),
                    Color(0xFF1C252E),
                    signboard = netMeshTex
                )
            )
            // Suspended granite boulder counterweights hanging from the shoreward tail boom
            for (cwOff in floatArrayOf(-0.9f, 0.0f, 0.9f)) {
                addDiagonalBeam(out, Vec3(nx + cwOff, 2.3f, tailZ), Vec3(nx + cwOff, 0.85f, tailZ), 0.05f, MatTeakDark, MatTeakDark)
                addAxisBox(out, nx + cwOff - 0.34f, 0.38f, tailZ - 0.34f, nx + cwOff + 0.34f, 0.92f, tailZ + 0.34f, MatBoulderGrey, MatBoulderDark, MatBoulderWarm)
            }
        }

        // E. 3 MOORED LUXURY KERALA HOUSEBOATS (KETTUVALLAM) WITH ARCHED WOVEN BAMBOO ROOFS
        val houseboats = arrayOf(
            floatArrayOf(-3628f, 224f),
            floatArrayOf(-3604f, 188f),
            floatArrayOf(-3632f, 162f)
        )
        for (hb in houseboats) {
            val hx = hb[0]; val hz = hb[1]
            // Curved dark-teak wooden Kettuvallam hull
            addAxisBox(out, hx - 7.8f, 0.14f, hz - 2.15f, hx + 7.8f, 1.32f, hz + 2.15f, MatTeakDark, MatTeakMid, MatTeakDark)
            // Tapered prow & stern peaks
            addAxisBox(out, hx - 9.4f, 0.45f, hz - 1.25f, hx - 7.8f, 1.65f, hz + 1.25f, MatTeakMid, MatTeakDark, MatTeakDark)
            addAxisBox(out, hx + 7.8f, 0.45f, hz - 1.25f, hx + 9.4f, 1.65f, hz + 1.25f, MatTeakMid, MatTeakDark, MatTeakDark)
            // Open front viewing deck railing
            addAxisBox(out, hx - 7.6f, 1.32f, hz - 2.0f, hx - 4.6f, 1.98f, hz + 2.0f, MatBamboo, MatTeakMid, MatTeakDark)
            // Iconic arched woven bamboo & coir thatched roof cabin (Valavara)
            addAxisBox(out, hx - 4.6f, 1.32f, hz - 1.95f, hx + 6.4f, 3.05f, hz + 1.95f, MatBamboo, MatTeakMid, MatTintedGlass)
            // Curved bamboo thatched roof pitched crown (textured with woven bamboo!)
            val roofRidgeY = 4.05f
            out.add(
                QuadFace3D(
                    Vec3(hx - 5.0f, 2.95f, hz - 2.25f),
                    Vec3(hx + 6.8f, 2.95f, hz - 2.25f),
                    Vec3(hx + 6.8f, roofRidgeY, hz),
                    Vec3(hx - 5.0f, roofRidgeY, hz),
                    MatBamboo,
                    signboard = houseboatRoofTex
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(hx + 6.8f, 2.95f, hz + 2.25f),
                    Vec3(hx - 5.0f, 2.95f, hz + 2.25f),
                    Vec3(hx - 5.0f, roofRidgeY, hz),
                    Vec3(hx + 6.8f, roofRidgeY, hz),
                    MatBamboo,
                    signboard = houseboatRoofTex
                )
            )
        }
    }

    // =========================================================================
    // 3. TRIPRAYAR CANOLI CANAL BRIDGE & SREE RAMA TEMPLE WATER GHAT (MEENOOTU)
    // =========================================================================
    private fun buildTriprayarCanoliCanalBridgeAndTempleGhat3D(out: MutableList<QuadFace3D>) {
        val cx = CANOLI_CENTER_X
        val hw = CANOLI_DECK_HALF_W
        val railingTex = Zone3ArchitecturalTextures.createBridgeSlottedRailingSpec()
        val canalSign = Zone5ArchitecturalTextures.createZone5PwdBridgeSignSpec(
            cacheKey = "ZONE5_CANOLI_BRIDGE_SIGN",
            malayalamTitle = "തൃപ്രയാർ കനോലി കനാൽ പാലം",
            englishTitle = "TRIPRAYAR CANOLI CANAL BRIDGE",
            subtitle = "NH 66 • SREE RAMA TEMPLE GHAT"
        )
        val templePlaque = Zone5ArchitecturalTextures.createTriprayarTemplePlaqueSpec()

        // A. Concrete Bridge Deck, Slotted Parapets & Piers over Canoli Canal
        val segs = 16
        for (i in 0 until segs) {
            val za = CANOLI_RAMP_SOUTH_Z + (CANOLI_RAMP_NORTH_Z - CANOLI_RAMP_SOUTH_Z) * (i.toFloat() / segs)
            val zb = CANOLI_RAMP_SOUTH_Z + (CANOLI_RAMP_NORTH_Z - CANOLI_RAMP_SOUTH_Z) * ((i + 1).toFloat() / segs)
            val ya = computeZone5RoadSurfaceY(cx, za)
            val yb = computeZone5RoadSurfaceY(cx, zb)
            val onCanal = (za + zb) * 0.5f in (CANOLI_DECK_SOUTH_Z - 2f)..(CANOLI_DECK_NORTH_Z + 2f)
            val gDepth = if (onCanal) 1.15f else 0.55f

            for (side in floatArrayOf(-1f, 1f)) {
                val gx = cx + side * (hw + 0.14f)
                out.add(
                    QuadFace3D(
                        Vec3(gx, ya - gDepth, za),
                        Vec3(gx, yb - gDepth, zb),
                        Vec3(gx, yb + 0.04f, zb),
                        Vec3(gx, ya + 0.04f, za),
                        MatConcreteDeckShadow
                    )
                )
                val railX = cx + side * (hw + 0.95f)
                out.add(
                    QuadFace3D(
                        Vec3(railX, ya + 0.18f, za),
                        Vec3(railX, yb + 0.18f, zb),
                        Vec3(railX, yb + 1.22f, zb),
                        Vec3(railX, ya + 1.22f, za),
                        MatNattikaWhite,
                        signboard = railingTex
                    )
                )
            }
        }
        // 3 Twin Pier Bents in Canoli Canal
        for (pz in floatArrayOf(-2692f, -2660f, -2628f)) {
            for (pxOff in floatArrayOf(-5.5f, 5.5f)) {
                addAxisBox(out, cx + pxOff - 0.95f, 0.02f, pz - 1.35f, cx + pxOff + 0.95f, CANOLI_DECK_Y - 1.15f, pz + 1.35f, MatConcretePier, MatConcretePierDark, MatConcreteMossWet)
            }
            addAxisBox(out, cx - hw, CANOLI_DECK_Y - 1.70f, pz - 1.45f, cx + hw, CANOLI_DECK_Y - 1.12f, pz + 1.45f, MatConcreteDeck, MatConcretePier, MatConcretePierDark)
        }

        // PWD Bridge Signboard at North approach
        val sx = cx - hw - 2.6f
        val sz = CANOLI_RAMP_NORTH_Z + 5f
        addAxisBox(out, sx - 0.10f, 0.06f, sz - 0.10f, sx + 0.10f, 3.85f, sz + 0.10f, MatGalvanizedDark, MatGalvanized, MatGalvanized)
        out.add(
            QuadFace3D(
                Vec3(sx - 2.1f, 2.35f, sz),
                Vec3(sx + 2.1f, 2.35f, sz),
                Vec3(sx + 2.1f, 3.75f, sz),
                Vec3(sx - 2.1f, 3.75f, sz),
                Color(0xFF0A4E94),
                signboard = canalSign
            )
        )

        // B. TRIPRAYAR SREE RAMA TEMPLE WATER GHAT (MEENOOTU KADAVU) — East Bank right beside Canoli Canal!
        //    Positioned strictly East of the bridge (X = -2192 .. -2125, Z = -2632 .. -2570)
        val gx0 = -2192.0f
        val gx1 = -2134.0f
        // 6 Broad Stepped Granite & Laterite Ghat Steps descending right down into the Canoli Canal!
        for (step in 0 until 6) {
            val stepY = 0.20f + step * 0.24f
            val stepZ0 = -2631.0f + step * 1.45f
            val stepZ1 = stepZ0 + 1.65f
            addAxisBox(out, gx0, 0.04f, stepZ0, gx1, stepY, stepZ1, Color(0xFFB5B0A6), MatLaterite, MatLateriteDark)
        }
        // Ornamental laterite & whitewashed ghat pavilion piers flanking the steps
        for (px in floatArrayOf(gx0 + 1.2f, gx0 + 18f, gx0 + 38f, gx1 - 1.2f)) {
            addAxisBox(out, px - 0.85f, 0.06f, -2622f, px + 0.85f, 2.35f, -2620.4f, MatTempleCream, MatLaterite, MatLateriteDark)
        }
        // Iconic Kerala Temple Gopuram & Nalambalam Facade overlooking the Meenootu Water Ghat!
        val tcx = -2163.0f
        val tcz = -2594.0f
        // Whitewashed granite plinth + Nalambalam outer wall with terracotta roof eaves
        addAxisBox(out, tcx - 24f, 0.06f, tcz - 14f, tcx + 24f, 3.8f, tcz + 14f, MatTempleCream, MatTempleCream, MatLaterite)
        // Central Two-Tier Kerala Temple Gopuram Entrance Tower facing the canal!
        addAxisBox(out, tcx - 8.5f, 0.06f, tcz - 16.5f, tcx + 8.5f, 6.2f, tcz - 10.0f, MatTempleCream, MatTempleCream, MatTeakDark)
        addSimplePitchedRoof(out, tcx - 10.2f, tcx + 10.2f, tcz - 18.2f, tcz - 8.4f, 6.1f, 9.6f, MatTerracotta)
        addSimplePitchedRoof(out, tcx - 7.2f, tcx + 7.2f, tcz - 16.4f, tcz - 10.2f, 9.4f, 12.6f, MatTerracotta)
        // Golden Stupikas on Gopuram ridge
        for (stX in floatArrayOf(tcx - 2.6f, tcx, tcx + 2.6f)) {
            addAxisBox(out, stX - 0.22f, 12.55f, tcz - 13.5f, stX + 0.22f, 13.75f, tcz - 13.1f, MatTempleGold, MatTempleGold, MatTempleGold)
        }
        // Tall Golden Flagstaff (Kodimaram) & Multi-Tier Brass Deepastambham Oil Lamp in front of Gopuram
        addAxisBox(out, tcx - 0.34f, 0.06f, tcz - 21.5f, tcx + 0.34f, 16.8f, tcz - 20.8f, MatTempleGold, MatTempleGold, MatTempleGold)
        addAxisBox(out, tcx + 5.2f, 0.06f, tcz - 21.6f, tcx + 6.4f, 6.4f, tcz - 20.4f, MatTempleGold, Color(0xFFB47808), MatTempleGold)
        // Sacred Triprayar Sree Rama Temple Meenootu Ghat Signboard facing the NH 66 bridge!
        out.add(
            QuadFace3D(
                Vec3(tcx - 5.2f, 3.8f, tcz - 16.7f),
                Vec3(tcx + 5.2f, 3.8f, tcz - 16.7f),
                Vec3(tcx + 5.2f, 5.45f, tcz - 16.7f),
                Vec3(tcx - 5.2f, 5.45f, tcz - 16.7f),
                Color(0xFF7F1D1D),
                signboard = templePlaque
            )
        )
    }

    // =========================================================================
    // 4. KOTTAPURAM / KODUNGALLUR BRIDGE (MAJOR WATER BRIDGE 6 — NW-3 / PERIYAR LAGOON)
    //    Monumental 540m Multi-Span Elevated Viaduct (Y = 12.60m High Navigational Clearance!)
    //    + Moothakunnam / Ernakulam Border Turnaround Bus Bay & Welcome Gantry
    // =========================================================================
    private fun buildKottapuramNw3BridgeAndTerminus3D(out: MutableList<QuadFace3D>) {
        val cx = KOTTAPURAM_CENTER_X
        val hw = KOTTAPURAM_DECK_HALF_W
        val railingTex = Zone3ArchitecturalTextures.createBridgeSlottedRailingSpec()
        val kottapuramSign = Zone5ArchitecturalTextures.createZone5PwdBridgeSignSpec(
            cacheKey = "ZONE5_KOTTAPURAM_BRIDGE_SIGN",
            malayalamTitle = "കൊടുങ്ങല്ലൂർ - കോട്ടപ്പുറം പാലം",
            englishTitle = "KOTTAPURAM BRIDGE • NW-3",
            subtitle = "PERIYAR LAGOON • THRISSUR–ERNAKULAM BORDER"
        )
        val moothaGantryTex = Zone5ArchitecturalTextures.createMoothakunnamGantrySpec()

        // A. Monumental Elevated Haunched Concrete Box-Girder Superstructure (540m total)
        val segs = 36
        for (i in 0 until segs) {
            val za = KOTTAPURAM_RAMP_SOUTH_Z + (KOTTAPURAM_RAMP_NORTH_Z - KOTTAPURAM_RAMP_SOUTH_Z) * (i.toFloat() / segs)
            val zb = KOTTAPURAM_RAMP_SOUTH_Z + (KOTTAPURAM_RAMP_NORTH_Z - KOTTAPURAM_RAMP_SOUTH_Z) * ((i + 1).toFloat() / segs)
            val ya = computeZone5RoadSurfaceY(cx, za)
            val yb = computeZone5RoadSurfaceY(cx, zb)
            val midZ = (za + zb) * 0.5f
            val onLagoon = midZ in (KOTTAPURAM_DECK_SOUTH_Z - 4f)..(KOTTAPURAM_DECK_NORTH_Z + 4f)
            // Variable-depth haunched box girder (deeper over piers, sleeker mid-span for ~10.8m barge clearance!)
            val gDepth = if (onLagoon) 1.75f else 0.90f

            for (side in floatArrayOf(-1f, 1f)) {
                val gx = cx + side * (hw + 0.18f)
                out.add(
                    QuadFace3D(
                        Vec3(gx, ya - gDepth, za),
                        Vec3(gx, yb - gDepth, zb),
                        Vec3(gx, yb + 0.05f, zb),
                        Vec3(gx, ya + 0.05f, za),
                        MatConcreteDeckShadow
                    )
                )
                // White New-Jersey outer crash parapet + slotted marine railing
                val railX = cx + side * (hw + 1.18f)
                out.add(
                    QuadFace3D(
                        Vec3(railX, ya + 0.22f, za),
                        Vec3(railX, yb + 0.22f, zb),
                        Vec3(railX, yb + 1.38f, zb),
                        Vec3(railX, ya + 1.38f, za),
                        MatNattikaWhite,
                        signboard = railingTex
                    )
                )
            }
            if (onLagoon) {
                out.add(
                    QuadFace3D(
                        Vec3(cx - hw - 0.2f, ya - gDepth, za),
                        Vec3(cx + hw + 0.2f, ya - gDepth, za),
                        Vec3(cx + hw + 0.2f, yb - gDepth, zb),
                        Vec3(cx - hw - 0.2f, yb - gDepth, zb),
                        MatConcretePierDark
                    )
                )
            }
            // Central highway median crash barrier
            out.add(
                QuadFace3D(
                    Vec3(cx - 0.26f, ya + 0.72f, za),
                    Vec3(cx + 0.26f, ya + 0.72f, za),
                    Vec3(cx + 0.26f, yb + 0.72f, zb),
                    Vec3(cx - 0.26f, yb + 0.72f, zb),
                    MatConcreteDeck
                )
            )
            for (ms in floatArrayOf(-0.26f, 0.26f)) {
                out.add(
                    QuadFace3D(
                        Vec3(cx + ms, ya, za),
                        Vec3(cx + ms, yb, zb),
                        Vec3(cx + ms, yb + 0.72f, zb),
                        Vec3(cx + ms, ya + 0.72f, za),
                        MatConcreteDeckShadow
                    )
                )
            }
        }

        // B. Tall Twin Portal Piers leaving a WIDE 85m Central NW-3 Shipping Channel (Z = -4092..-4008)!
        val pierZs = floatArrayOf(-4212f, -4152f, -4092f, -4008f, -3948f, -3888f)
        for (pz in pierZs) {
            val isNavChannelPier = (pz == -4092f || pz == -4008f)
            val capY = KOTTAPURAM_DECK_Y - 1.72f
            for (pxOff in floatArrayOf(-5.8f, 5.8f)) {
                val px = cx + pxOff
                // Heavy marine caisson dolphin bumper + yellow-black fender stripe around shipping channel piers!
                addAxisBox(
                    out,
                    px - 1.95f, 0.02f, pz - 2.75f,
                    px + 1.95f, 2.05f, pz + 2.75f,
                    if (isNavChannelPier) MatHazardYellow else MatConcreteMossWet,
                    MatConcreteMossWet,
                    if (isNavChannelPier) MatHazardBlack else MatConcretePierDark
                )
                // Tall monumental elliptical concrete column rising 10.8m above the lagoon!
                addAxisBox(out, px - 1.35f, 2.05f, pz - 1.95f, px + 1.35f, capY - 1.05f, pz + 1.95f, MatConcretePier, MatConcretePier, MatConcretePierDark)
            }
            // Cross portal cap beam
            addAxisBox(out, cx - hw - 0.3f, capY - 1.05f, pz - 2.05f, cx + hw + 0.3f, capY, pz + 2.05f, MatConcreteDeck, MatConcretePier, MatConcretePierDark)
        }

        // Marine-grade twin-arm streetlamps along the elevated Kottapuram Viaduct
        var lampZ = KOTTAPURAM_DECK_SOUTH_Z + 18f
        while (lampZ <= KOTTAPURAM_DECK_NORTH_Z - 12f) {
            val ly = computeZone5RoadSurfaceY(cx, lampZ)
            addAxisBox(out, cx - 0.11f, ly + 0.70f, lampZ - 0.11f, cx + 0.11f, ly + 7.50f, lampZ + 0.11f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            addAxisBox(out, cx - 2.05f, ly + 7.40f, lampZ - 0.14f, cx + 2.05f, ly + 7.58f, lampZ + 0.14f, MatGalvanized, MatLedWarm, MatLedWarm)
            lampZ += 42.0f
        }

        // Blue PWD / NHAI Bridge Signboard at North Approach (Kodungallur side)
        val sx = cx - hw - 2.8f
        val sz = KOTTAPURAM_RAMP_NORTH_Z + 6f
        addAxisBox(out, sx - 0.12f, 0.06f, sz - 0.12f, sx + 0.12f, 4.10f, sz + 0.12f, MatGalvanizedDark, MatGalvanized, MatGalvanized)
        out.add(
            QuadFace3D(
                Vec3(sx - 2.3f, 2.45f, sz),
                Vec3(sx + 2.3f, 2.45f, sz),
                Vec3(sx + 2.3f, 3.95f, sz),
                Vec3(sx - 2.3f, 3.95f, sz),
                Color(0xFF0A4E94),
                signboard = kottapuramSign
            )
        )

        // C. MOOTHAKUNNAM / ERNAKULAM BORDER OVERHEAD GREEN NHAI GANTRY & COASTAL BUS SHELTER
        val gz = -4365.0f
        for (gxSide in floatArrayOf(cx - hw - 2.2f, cx + hw + 2.2f)) {
            addAxisBox(out, gxSide - 0.35f, 0.06f, gz - 0.35f, gxSide + 0.35f, 7.60f, gz + 0.35f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
        }
        addAxisBox(out, cx - hw - 2.2f, 6.20f, gz - 0.32f, cx + hw + 2.2f, 7.55f, gz + 0.32f, MatGalvanized, MatNattikaNhaiGreen, MatNattikaNhaiGreen)
        out.add(
            QuadFace3D(
                Vec3(cx + 8.5f, 6.10f, gz + 0.36f),
                Vec3(cx - 8.5f, 6.10f, gz + 0.36f),
                Vec3(cx - 8.5f, 7.85f, gz + 0.36f),
                Vec3(cx + 8.5f, 7.85f, gz + 0.36f),
                MatNattikaNhaiGreen,
                signboard = moothaGantryTex
            )
        )
        // Moothakunnam Coastal Bus Waiting Pavilion (strictly East of turnaround bay: X = cx + 29..36!)
        addAxisBox(out, cx + 29.0f, 0.06f, -4450f, cx + 36.0f, 4.10f, -4400f, MatTempleCream, MatTempleCream, MatLaterite)
        addSimplePitchedRoof(out, cx + 27.8f, cx + 37.2f, -4451.5f, -4398.5f, 4.05f, 6.30f, MatTerracotta)
    }

    // =========================================================================
    // 5. CHAVAKKAD BEACH GRANITE BOULDER SEAWALL (KALLIDUM), BEACHED VALLAMS
    //    & COLORFUL DRYING FISHING NETS (Strictly WEST of X = -4509m!)
    // =========================================================================
    private fun buildChavakkadSeawallAndBoats3D(out: MutableList<QuadFace3D>) {
        val chavakkadSign = Zone5ArchitecturalTextures.createZone5PwdBridgeSignSpec(
            cacheKey = "ZONE5_CHAVAKKAD_BEACH_SIGN",
            malayalamTitle = "ചാവക്കാട് ബീച്ച് • കടൽഭിത്തി",
            englishTitle = "CHAVAKKAD BEACH & SEAWALL",
            subtitle = "NH 66 COASTAL DRIVE • ARABIAN SEA"
        )
        // Beach welcome signboard along West promenade edge (X = -4511.5, Z = 1045)
        addAxisBox(out, -4511.6f, 0.06f, 1044.9f, -4511.4f, 3.60f, 1045.1f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
        out.add(
            QuadFace3D(
                Vec3(-4511.5f, 2.25f, 1043.0f),
                Vec3(-4511.5f, 2.25f, 1047.0f),
                Vec3(-4511.5f, 3.55f, 1047.0f),
                Vec3(-4511.5f, 3.55f, 1043.0f),
                Color(0xFF0A4E94),
                signboard = chavakkadSign
            )
        )

        // Granite Boulder Seawall (Kallidum) stacked in 2 organic rough-hewn tiers along X = -4518.5 .. -4525.0
        var bz = 996.0f
        var idx = 0
        while (bz <= 1158.0f) {
            if (bz !in 1072.0f..1088.0f) { // Leave wide promenade pier access opening at Z = 1072..1088!
                val jitterX = ((idx * 37) % 10) * 0.14f
                val hTop = 1.25f + ((idx * 19) % 7) * 0.12f
                val wZ = 2.6f + ((idx * 13) % 5) * 0.25f
                val bCol = when (idx % 3) {
                    0 -> MatBoulderGrey
                    1 -> MatBoulderDark
                    else -> MatBoulderWarm
                }
                addAxisBox(out, -4523.5f - jitterX, 0.06f, bz, -4518.8f, hTop, bz + wZ, bCol, MatBoulderDark, bCol)
                // Outer lower wave-breaker armor rock
                addAxisBox(out, -4526.2f - jitterX * 0.7f, 0.06f, bz + 0.4f, -4522.8f, hTop * 0.65f, bz + wZ - 0.2f, MatBoulderDark, MatBoulderGrey, MatBoulderDark)
            }
            bz += 3.15f
            idx++
        }

        // 4 Colorful Wooden Fishing Boats (Vallam) pulled up onto the golden beach sand (X = -4531..-4546)
        val boatSpecs = arrayOf(
            Pair(1018.0f, Color(0xFF0284C7)), // Ocean Blue Vallam
            Pair(1046.0f, Color(0xFFDC2626)), // Vermilion Red Vallam
            Pair(1106.0f, Color(0xFF059669)), // Emerald Green Vallam
            Pair(1140.0f, Color(0xFFD97706))  // Golden Saffron Vallam
        )
        for ((vz, hullCol) in boatSpecs) {
            // Hull resting at gentle beach angle
            addAxisBox(out, -4544.5f, 0.08f, vz - 1.15f, -4532.0f, 1.10f, vz + 1.15f, MatTeakMid, hullCol, MatTeakDark)
            // Tapered prow & stern peaks
            addAxisBox(out, -4546.2f, 0.35f, vz - 0.65f, -4544.5f, 1.38f, vz + 0.65f, hullCol, MatTeakDark, MatTeakMid)
            addAxisBox(out, -4532.0f, 0.35f, vz - 0.65f, -4530.4f, 1.38f, vz + 0.65f, hullCol, MatTeakDark, MatTeakMid)
            // Bright colorful nylon fishing nets drying on bamboo racks right next to each boat!
            val netCol = when (hullCol) {
                Color(0xFF0284C7) -> Color(0xFFEA580C)
                Color(0xFFDC2626) -> Color(0xFF2563EB)
                Color(0xFF059669) -> Color(0xFFDB2777)
                else -> Color(0xFF16A34A)
            }
            val nZ = vz + 4.8f
            addAxisBox(out, -4538.5f, 0.06f, nZ - 0.12f, -4538.3f, 1.65f, nZ + 0.12f, MatBamboo, MatBambooDark, MatBamboo)
            addAxisBox(out, -4529.7f, 0.06f, nZ - 0.12f, -4529.5f, 1.65f, nZ + 0.12f, MatBamboo, MatBambooDark, MatBamboo)
            out.add(
                QuadFace3D(
                    Vec3(-4538.4f, 0.22f, nZ),
                    Vec3(-4529.6f, 0.22f, nZ),
                    Vec3(-4529.6f, 1.58f, nZ),
                    Vec3(-4538.4f, 1.58f, nZ),
                    netCol
                )
            )
        }
    }

    // =========================================================================
    // 6. AUTHENTIC NH 66 COASTAL SCENERY: LATERITE COMPOUND WALLS, ROADSIDE
    //    FRESH FISH STALLS WITH BLUE ICE BOXES & BOAT-BUILDING YARDS
    //    Strictly placed ≥17m outside the NH 66 carriageway!
    // =========================================================================
    private fun buildCoastalCorridorScenery3D(out: MutableList<QuadFace3D>, roads: List<ThrissurOsmRoad>) {
        val coastalRoads = roads.filter {
            it.id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" || it.id == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM"
        }
        var propIdx = 0
        for (road in coastalRoads) {
            val frames = road.frames
            if (frames.size < 10) continue
            var nextDist = 110f
            for (i in 4 until frames.size - 4) {
                val f = frames[i]
                if (f.cumulativeDist < nextDist) continue
                // Skip bridges, toll flares, and water basins cleanly
                if (!isPointClearOfZone5Structures(f.pos.x, f.pos.z)) {
                    nextDist = f.cumulativeDist + 55f
                    continue
                }
                if (f.pos.y > 0.35f) {
                    nextDist = f.cumulativeDist + 55f
                    continue
                }
                val side = if (propIdx % 2 == 0) 1f else -1f
                val offset = f.halfWidthM + 12.2f // Cleanly outside the 5.5m parallel NH 66 service roads!
                val wx = f.pos.x + f.right.x * side * offset
                val wz = f.pos.z + f.right.z * side * offset
                if (!isPointClearOfZone5Structures(wx, wz) ||
                    !ThrissurFuelStationSystem.isPointClearOfFuelStations(wx, wz, extraMargin = 12.0f)
                ) {
                    nextDist = f.cumulativeDist + 55f
                    continue
                }

                when (propIdx % 3) {
                    0 -> {
                        // Authentic Kerala Laterite Residential Compound Wall + Terracotta Gate Pillars
                        val tx = f.tangent.x; val tz = f.tangent.z
                        val len = 11.0f
                        val p0x = wx - tx * len * 0.5f; val p0z = wz - tz * len * 0.5f
                        val p1x = wx + tx * len * 0.5f; val p1z = wz + tz * len * 0.5f
                        addOrientedWall(out, p0x, p0z, p1x, p1z, 0.06f, 1.55f, 0.32f, MatLaterite, MatLateriteDark)
                        addOrientedWall(out, p0x, p0z, p1x, p1z, 1.55f, 1.72f, 0.42f, MatTempleCream, MatConcreteDeck)
                        // Flanking gate pillars
                        addAxisBox(out, p0x - 0.36f, 0.06f, p0z - 0.36f, p0x + 0.36f, 2.25f, p0z + 0.36f, MatTempleCream, MatLaterite, MatLateriteDark)
                        addAxisBox(out, p1x - 0.36f, 0.06f, p1z - 0.36f, p1x + 0.36f, 2.25f, p1z + 0.36f, MatTempleCream, MatLaterite, MatLateriteDark)
                    }
                    1 -> {
                        // Kerala Roadside Fresh Fish Stall with Blue Tarpaulin & Cobalt-Blue Plastic Ice Boxes!
                        addAxisBox(out, wx - 2.6f, 0.06f, wz - 1.6f, wx + 2.6f, 0.95f, wz + 1.6f, MatTeakMid, MatTeakDark, MatTeakDark)
                        // Canopy poles & vibrant blue plastic tarpaulin roof
                        for (dx in floatArrayOf(-2.4f, 2.4f)) {
                            for (dz in floatArrayOf(-1.4f, 1.4f)) {
                                addAxisBox(out, wx + dx - 0.07f, 0.06f, wz + dz - 0.07f, wx + dx + 0.07f, 2.75f, wz + dz + 0.07f, MatBamboo, MatBambooDark, MatBamboo)
                            }
                        }
                        addAxisBox(out, wx - 2.8f, 2.68f, wz - 1.85f, wx + 2.8f, 2.84f, wz + 1.85f, MatIceBoxBlueLight, MatIceBoxBlue, MatIceBoxBlue)
                        // Iconic bright cobalt-blue plastic insulated fish ice boxes stacked beside stall!
                        addAxisBox(out, wx - 1.9f, 0.95f, wz - 0.55f, wx - 0.85f, 1.62f, wz + 0.55f, MatIceBoxBlue, MatIceBoxBlueLight, MatIceBoxBlue)
                        addAxisBox(out, wx + 0.6f, 0.95f, wz - 0.55f, wx + 1.75f, 1.62f, wz + 0.55f, MatIceBoxBlue, MatIceBoxBlueLight, MatIceBoxBlue)
                        addAxisBox(out, wx - 3.6f, 0.06f, wz - 0.6f, wx - 2.7f, 0.78f, wz + 0.6f, MatIceBoxBlueLight, MatIceBoxBlue, MatIceBoxBlue)
                    }
                    else -> {
                        // Coastal Wooden Boat-Building Yard Shed with Half-Built Vallam Hull on Keel Blocks!
                        for (dx in floatArrayOf(-5.5f, 0f, 5.5f)) {
                            for (dz in floatArrayOf(-3.2f, 3.2f)) {
                                addAxisBox(out, wx + dx - 0.14f, 0.06f, wz + dz - 0.14f, wx + dx + 0.14f, 3.85f, wz + dz + 0.14f, MatTeakDark, MatTeakMid, MatTeakDark)
                            }
                        }
                        addSimplePitchedRoof(out, wx - 6.0f, wx + 6.0f, wz - 3.8f, wz + 3.8f, 3.80f, 5.35f, MatGalvanizedDark)
                        // Curved wooden boat hull resting on timber keel blocks inside the open shed
                        addAxisBox(out, wx - 4.4f, 0.65f, wz - 1.15f, wx + 4.4f, 1.75f, wz + 1.15f, MatTeakMid, MatTeakDark, MatTeakMid)
                        addAxisBox(out, wx - 2.4f, 0.06f, wz - 0.6f, wx - 1.8f, 0.65f, wz + 0.6f, MatLaterite, MatLateriteDark, MatLaterite)
                        addAxisBox(out, wx + 1.8f, 0.06f, wz - 0.6f, wx + 2.4f, 0.65f, wz + 0.6f, MatLaterite, MatLateriteDark, MatLaterite)
                    }
                }
                propIdx++
                nextDist = f.cumulativeDist + 195f
            }
        }
    }

    // =========================================================================
    // LIVE DYNAMIC 3D ELEMENTS (60 FPS GPU STREAM PASS):
    //  1. Nattika Toll Plaza: 10 Live Pneumatic LED Boom Barrier Arms (Open to 85°!)
    //  2. Chettuva Backwaters: 2 Animated Tourist Speedboats with Foaming Wake Trails
    //  3. Triprayar Temple Water Ghat: Shimmering Schools of Sacred Meenootu Fish!
    //  4. Kottapuram NW-3 Bridge: Animated Cargo Barge + Kerala SWTD Passenger Ferry!
    // =========================================================================

    fun collectZone5DynamicFaces(
        simTimeSec: Float,
        activeNattikaLaneIdx: Int,
        nattikaBarrierOpenFrac: Float,
        camEye: Vec3,
        outFaces: MutableList<QuadFace3D>,
        laneOpenFracs: FloatArray? = null
    ) {
        // 1. Nattika Toll Plaza 10 Interactive Pneumatic FASTag Boom Barriers
        if (hypot(camEye.x - NATTIKA_CENTER_X, camEye.z - NATTIKA_CENTER_Z) < 280f) {
            val tz = NATTIKA_CENTER_Z
            for (laneIdx in 0 until NATTIKA_LANE_COUNT) {
                val active = isNattikaLaneActiveFastag(laneIdx)
                val playerFrac = if (active && laneIdx == activeNattikaLaneIdx) nattikaBarrierOpenFrac.coerceIn(0f, 1f) else 0f
                val trafficFrac = if (active && laneOpenFracs != null && laneIdx < laneOpenFracs.size) {
                    laneOpenFracs[laneIdx].coerceIn(0f, 1f)
                } else 0f
                val openFrac = max(playerFrac, trafficFrac)
                val angleRad = openFrac * (85f * PI.toFloat() / 180f)
                val isSouthbound = laneIdx < 5
                val pivotZ = if (isSouthbound) tz - 3.2f else tz + 3.2f
                val pivotX = nattikaIslandX(laneIdx) + NATTIKA_ISLAND_HALF_W + 0.05f
                val pivotY = 1.08f
                val armLen = 3.65f

                // Yellow-red hydraulic boom cabinet box on the concrete island edge
                addAxisBox(outFaces, pivotX - 0.34f, 0.40f, pivotZ - 0.22f, pivotX + 0.04f, 1.32f, pivotZ + 0.22f, MatHazardYellow, MatNattikaRedFrame, MatHazardYellow)

                // Articulated 6-segment striped aluminum boom arm with glowing green/red LED strip!
                val segs = 6
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)
                val ledCol = if (openFrac > 0.35f) MatLedGreen else MatLedRed
                for (s in 0 until segs) {
                    val d0 = armLen * (s.toFloat() / segs)
                    val d1 = armLen * ((s + 1).toFloat() / segs)
                    val x0 = pivotX + d0 * cosA
                    val y0 = pivotY + d0 * sinA
                    val x1 = pivotX + d1 * cosA
                    val y1 = pivotY + d1 * sinA
                    val stripeCol = if (s % 2 == 0) MatNattikaRedFrame else MatLedWhite
                    addDiagonalBeam(outFaces, Vec3(x0, y0, pivotZ), Vec3(x1, y1, pivotZ), 0.11f, stripeCol, ledCol)
                }
            }
        }

        // 2. Chettuva Backwaters: 2 Animated Tourist Speedboats Producing White Foaming Wake Trails
        if (hypot(camEye.x - CHETTUVA_CENTER_X, camEye.z - CHETTUVA_CENTER_Z) < 420f) {
            val boatCycle = (simTimeSec * 14.0f) % 440f
            // Boat 1: Cruising Westward beneath Chettuva Bridge at Z = 232m
            val b1x = -3450f - boatCycle
            drawSpeedboatWithWake(outFaces, b1x, 232f, dirX = -1f, hullCol = Color(0xFF0284C7), simTimeSec = simTimeSec)
            // Boat 2: Cruising Eastward beneath Chettuva Bridge at Z = 172m
            val b2x = -3890f + ((simTimeSec * 11.5f + 160f) % 440f)
            drawSpeedboatWithWake(outFaces, b2x, 172f, dirX = +1f, hullCol = Color(0xFFDC2626), simTimeSec = simTimeSec)
        }

        // 3. Triprayar Sree Rama Temple Water Ghat: Shimmering Sacred Meenootu Fish & Ripples!
        if (hypot(camEye.x - CANOLI_CENTER_X, camEye.z - CANOLI_CENTER_Z) < 260f) {
            for (fIdx in 0 until 10) {
                val phase = simTimeSec * (1.8f + (fIdx % 3) * 0.45f) + fIdx * 1.15f
                val fx = -2184f + (fIdx * 4.8f) + sin(phase) * 2.4f
                val fz = -2636.5f - (fIdx % 3) * 2.2f + cos(phase * 0.8f) * 1.1f
                val shimmer = 0.55f + 0.45f * sin(phase * 2.2f)
                val fishCol = if (fIdx % 2 == 0) Color(0xFFD1D5DB) else Color(0xFFFDE68A)
                // Fish back + expanding water feeding ripple square right on the canal surface
                addAxisBox(outFaces, fx - 0.35f, 0.14f, fz - 0.12f, fx + 0.35f, 0.24f, fz + 0.12f, fishCol, fishCol, fishCol)
                val rHalf = 0.45f + shimmer * 0.48f
                outFaces.add(
                    QuadFace3D(
                        Vec3(fx - rHalf, 0.155f, fz - rHalf),
                        Vec3(fx + rHalf, 0.155f, fz - rHalf),
                        Vec3(fx + rHalf, 0.155f, fz + rHalf),
                        Vec3(fx - rHalf, 0.155f, fz + rHalf),
                        MatWaterFoam.copy(alpha = 0.42f * (1.1f - shimmer * 0.5f))
                    )
                )
            }
        }

        // 4. Kottapuram / Kodungallur NW-3 Bridge: Animated Cargo Barge & Kerala SWTD Passenger Ferry!
        if (hypot(camEye.x - KOTTAPURAM_CENTER_X, camEye.z - KOTTAPURAM_CENTER_Z) < 480f) {
            // A. Heavy NW-3 Inland Cargo Barge gliding East-West cleanly underneath central 85m span (Z = -4038m)
            val bargeX = -1140f - ((simTimeSec * 8.2f) % 760f)
            val bz = -4038f
            // Steel cargo hull (40m long × 9.6m wide, height ~6.4m -> ~4.4m clear under the 12.6m bridge!)
            addAxisBox(outFaces, bargeX - 20f, 0.14f, bz - 4.8f, bargeX + 20f, 2.35f, bz + 4.8f, MatBargeSteel, MatBargeRust, MatBargeRust)
            // Stacked colorful shipping containers on deck
            addAxisBox(outFaces, bargeX - 14f, 2.35f, bz - 3.6f, bargeX - 4f, 4.95f, bz - 0.3f, Color(0xFF1D4ED8), Color(0xFF1E3A8A), Color(0xFF1E3A8A))
            addAxisBox(outFaces, bargeX - 14f, 2.35f, bz + 0.3f, bargeX - 4f, 4.95f, bz + 3.6f, Color(0xFFEA580C), Color(0xFFC2410C), Color(0xFFC2410C))
            addAxisBox(outFaces, bargeX - 3f, 2.35f, bz - 3.6f, bargeX + 7f, 4.95f, bz + 3.6f, Color(0xFF15803D), Color(0xFF166534), Color(0xFF166534))
            // White aft navigation wheelhouse bridge cabin
            addAxisBox(outFaces, bargeX + 11.5f, 2.35f, bz - 3.4f, bargeX + 18.0f, 6.25f, bz + 3.4f, MatFerryCream, MatFerryCream, MatTintedGlass)
            // Trailing foaming stern wake behind cargo barge
            outFaces.add(
                QuadFace3D(
                    Vec3(bargeX + 20f, 0.16f, bz - 4.2f),
                    Vec3(bargeX + 46f, 0.16f, bz - 6.8f),
                    Vec3(bargeX + 46f, 0.16f, bz + 6.8f),
                    Vec3(bargeX + 20f, 0.16f, bz + 4.2f),
                    MatWaterFoam.copy(alpha = 0.55f)
                )
            )

            // B. Kerala SWTD Double-Deck Lagoon Passenger Ferry gliding West-East (Z = -4066m)
            val ferryX = -1900f + ((simTimeSec * 10.5f + 220f) % 760f)
            val fz = -4066f
            addAxisBox(outFaces, ferryX - 11.5f, 0.14f, fz - 3.1f, ferryX + 11.5f, 1.55f, fz + 3.1f, MatFerryCream, MatFerryBlue, MatFerryBlue)
            addAxisBox(outFaces, ferryX - 8.5f, 1.55f, fz - 2.75f, ferryX + 8.5f, 3.65f, fz + 2.75f, MatFerryCream, MatTintedGlass, MatFerryCream)
            addAxisBox(outFaces, ferryX - 6.0f, 3.65f, fz - 2.35f, ferryX + 6.5f, 5.15f, fz + 2.35f, MatFerryBlue, MatFerryCream, MatTintedGlass)
            // Trailing white foaming wake behind passenger ferry
            outFaces.add(
                QuadFace3D(
                    Vec3(ferryX - 11.5f, 0.16f, fz - 2.6f),
                    Vec3(ferryX - 30.0f, 0.16f, fz - 4.8f),
                    Vec3(ferryX - 30.0f, 0.16f, fz + 4.8f),
                    Vec3(ferryX - 11.5f, 0.16f, fz + 2.6f),
                    MatWaterFoam.copy(alpha = 0.62f)
                )
            )
        }
    }

    private fun drawSpeedboatWithWake(
        out: MutableList<QuadFace3D>,
        bx: Float,
        bz: Float,
        dirX: Float,
        hullCol: Color,
        simTimeSec: Float
    ) {
        val bobY = 0.05f * sin(simTimeSec * 4.5f + bx * 0.08f)
        // Sleek speedboat hull & windscreen
        addAxisBox(out, bx - 3.6f, 0.14f + bobY, bz - 1.25f, bx + 3.6f, 1.05f + bobY, bz + 1.25f, MatLedWhite, hullCol, MatLedWhite)
        addAxisBox(out, bx - 1.2f, 1.05f + bobY, bz - 1.0f, bx + 1.2f, 1.65f + bobY, bz + 1.0f, MatTintedGlass, MatTintedGlass, MatLedWhite)
        // V-shaped white foaming wake trail behind the speedboat!
        val sternX = bx - dirX * 3.6f
        val wakeEndX = bx - dirX * 19.5f
        out.add(
            QuadFace3D(
                Vec3(sternX, 0.165f, bz - 0.95f),
                Vec3(wakeEndX, 0.165f, bz - 3.4f),
                Vec3(wakeEndX, 0.165f, bz + 3.4f),
                Vec3(sternX, 0.165f, bz + 0.95f),
                MatWaterFoam.copy(alpha = 0.68f)
            )
        )
    }

    // =========================================================================
    // COMPACT 3D GEOMETRY PRIMITIVE HELPERS
    // =========================================================================

    private fun addAxisBox(
        out: MutableList<QuadFace3D>,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        topCol: Color,
        sideXCol: Color,
        sideZCol: Color
    ) {
        val lx = min(x0, x1); val hx = max(x0, x1)
        val ly = min(y0, y1); val hy = max(y0, y1)
        val lz = min(z0, z1); val hz = max(z0, z1)
        // Top
        out.add(QuadFace3D(Vec3(lx, hy, lz), Vec3(hx, hy, lz), Vec3(hx, hy, hz), Vec3(lx, hy, hz), topCol))
        // Front (+Z) & Back (-Z)
        out.add(QuadFace3D(Vec3(lx, ly, hz), Vec3(hx, ly, hz), Vec3(hx, hy, hz), Vec3(lx, hy, hz), sideZCol))
        out.add(QuadFace3D(Vec3(hx, ly, lz), Vec3(lx, ly, lz), Vec3(lx, hy, lz), Vec3(hx, hy, lz), sideZCol))
        // Left (-X) & Right (+X)
        out.add(QuadFace3D(Vec3(lx, ly, lz), Vec3(lx, ly, hz), Vec3(lx, hy, hz), Vec3(lx, hy, lz), sideXCol))
        out.add(QuadFace3D(Vec3(hx, ly, hz), Vec3(hx, ly, lz), Vec3(hx, hy, lz), Vec3(hx, hy, hz), sideXCol))
    }

    private fun addSimplePitchedRoof(
        out: MutableList<QuadFace3D>,
        x0: Float, x1: Float,
        z0: Float, z1: Float,
        eaveY: Float, ridgeY: Float,
        roofCol: Color
    ) {
        val cz = (z0 + z1) * 0.5f
        val shade = Color(roofCol.red * 0.84f, roofCol.green * 0.84f, roofCol.blue * 0.84f, 1f)
        out.add(QuadFace3D(Vec3(x0, eaveY, z0), Vec3(x1, eaveY, z0), Vec3(x1, ridgeY, cz), Vec3(x0, ridgeY, cz), roofCol))
        out.add(QuadFace3D(Vec3(x1, eaveY, z1), Vec3(x0, eaveY, z1), Vec3(x0, ridgeY, cz), Vec3(x1, ridgeY, cz), shade))
        // Gable end triangles (rendered as degenerate-top quads)
        out.add(QuadFace3D(Vec3(x0, eaveY, z0), Vec3(x0, eaveY, z1), Vec3(x0, ridgeY, cz), Vec3(x0, ridgeY, cz), shade))
        out.add(QuadFace3D(Vec3(x1, eaveY, z1), Vec3(x1, eaveY, z0), Vec3(x1, ridgeY, cz), Vec3(x1, ridgeY, cz), shade))
    }

    private fun addDiagonalBeam(
        out: MutableList<QuadFace3D>,
        a: Vec3,
        b: Vec3,
        thickness: Float,
        topCol: Color,
        sideCol: Color
    ) {
        val dx = b.x - a.x; val dy = b.y - a.y; val dz = b.z - a.z
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        val ht = thickness * 0.5f
        val ux = dx / len; val uy = dy / len; val uz = dz / len
        // Cross with world up or world X to get two orthogonal offsets
        var px = -uz; var py = 0f; var pz = ux
        val plen = hypot(px, pz)
        if (plen < 0.05f) {
            px = 1f; py = 0f; pz = 0f
        } else {
            px /= plen; pz /= plen
        }
        val qx = uy * pz - uz * py
        val qy = uz * px - ux * pz
        val qz = ux * py - uy * px

        val a1 = Vec3(a.x - px * ht, a.y - py * ht, a.z - pz * ht)
        val a2 = Vec3(a.x + px * ht, a.y + py * ht, a.z + pz * ht)
        val b2 = Vec3(b.x + px * ht, b.y + py * ht, b.z + pz * ht)
        val b1 = Vec3(b.x - px * ht, b.y - py * ht, b.z - pz * ht)
        out.add(QuadFace3D(a1, a2, b2, b1, topCol))

        val c1 = Vec3(a.x - qx * ht, a.y - qy * ht, a.z - qz * ht)
        val c2 = Vec3(a.x + qx * ht, a.y + qy * ht, a.z + qz * ht)
        val d2 = Vec3(b.x + qx * ht, b.y + qy * ht, b.z + qz * ht)
        val d1 = Vec3(b.x - qx * ht, b.y - qy * ht, b.z - qz * ht)
        out.add(QuadFace3D(c1, c2, d2, d1, sideCol))
    }

    private fun addOrientedWall(
        out: MutableList<QuadFace3D>,
        x0: Float, z0: Float,
        x1: Float, z1: Float,
        y0: Float, y1: Float,
        thickness: Float,
        topCol: Color,
        sideCol: Color
    ) {
        val dx = x1 - x0; val dz = z1 - z0
        val len = hypot(dx, dz).coerceAtLeast(1e-3f)
        val nx = -dz / len * (thickness * 0.5f)
        val nz = dx / len * (thickness * 0.5f)
        val p0L = Vec3(x0 - nx, y0, z0 - nz); val p0R = Vec3(x0 + nx, y0, z0 + nz)
        val p1L = Vec3(x1 - nx, y0, z1 - nz); val p1R = Vec3(x1 + nx, y0, z1 + nz)
        val q0L = Vec3(x0 - nx, y1, z0 - nz); val q0R = Vec3(x0 + nx, y1, z0 + nz)
        val q1L = Vec3(x1 - nx, y1, z1 - nz); val q1R = Vec3(x1 + nx, y1, z1 + nz)
        out.add(QuadFace3D(q0L, q1L, q1R, q0R, topCol))
        out.add(QuadFace3D(p0L, p1L, q1L, q0L, sideCol))
        out.add(QuadFace3D(p1R, p0R, q0R, q1R, sideCol))
    }

    // =========================================================================
    // 7. FULL 6-LANE NH 66 NATIONAL HIGHWAY INFRASTRUCTURE:
    //    - 3D 0.90m Sloped Reinforced-Concrete New Jersey Central Median Barrier
    //    - Black & White Reflective Kerbs + Flowering Coastal Bougainvillea Planters
    //    - Continuous Galvanized Steel W-Beam Highway Crash Guardrails on Both Shoulders
    //    - Twin-Arm Curved Cobra-Head 5000K LED Highway Streetlights every ~38m
    //    - 3 Full-Span NHAI Green Overhead Highway Gantries + Blue-White NH 66 Km Stones
    // =========================================================================
    private fun buildNh66HighwayFurniture3D(
        out: MutableList<QuadFace3D>,
        roads: List<ThrissurOsmRoad>
    ) {
        val targetIds = setOf("ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR", "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM")
        val matNewJersey = Color(0xFFC8CDD4)
        val matNewJerseyShade = Color(0xFF9AA3B0)
        val matGuardrailSilver = Color(0xFFDCE3EC)
        val matBougainvilleaA = Color(0xFFD946EF)
        val matBougainvilleaB = Color(0xFFE11D48)
        val matShrubGreen = Color(0xFF226630)
        val matSoilDark = Color(0xFF3F3124)

        fun isOpenHighwayStretch(z: Float, y: Float): Boolean {
            if (y > 0.25f) return false
            if (z > 930f) return false                                      // Chavakkad Beach joint
            if (z in (CHETTUVA_RAMP_SOUTH_Z - 14f)..(CHETTUVA_RAMP_NORTH_Z + 14f)) return false
            if (z in -1260f..-1180f) return false                           // SH 61 Junction
            if (z in (NATTIKA_FLARE_SOUTH_END_Z - 12f)..(NATTIKA_FLARE_NORTH_START_Z + 12f)) return false
            if (z in -2485f..-2340f) return false                           // Triprayar Stand Entry/Exit
            if (z in (CANOLI_RAMP_SOUTH_Z - 14f)..(CANOLI_RAMP_NORTH_Z + 14f)) return false
            if (z in (KOTTAPURAM_RAMP_SOUTH_Z - 14f)..(KOTTAPURAM_RAMP_NORTH_Z + 14f)) return false
            if (z < -4355f) return false                                    // Moothakunnam turnaround bay
            return true
        }

        for (road in roads) {
            if (road.id !in targetIds) continue
            val frames = road.frames
            if (frames.size < 12) continue

            var nextLampDist = 38f
            var nextKmStoneDist = 180f
            var i = 4
            while (i < frames.size - 5) {
                val f0 = frames[i]
                val f1 = frames[min(frames.size - 1, i + 2)]
                if (isOpenHighwayStretch(f0.pos.z, f0.pos.y) && isOpenHighwayStretch(f1.pos.z, f1.pos.y)) {
                    // 1. Sloped 0.90m New Jersey Reinforced-Concrete Central Median Barrier + Chevron Kerbs
                    val kerbCol = if ((i / 2) % 2 == 0) MatNattikaWhite else MatHazardBlack
                    addOrientedWall(out, f0.pos.x, f0.pos.z, f1.pos.x, f1.pos.z, 0.06f, 0.24f, 1.24f, kerbCol, kerbCol)
                    addOrientedWall(out, f0.pos.x, f0.pos.z, f1.pos.x, f1.pos.z, 0.24f, 0.88f, 0.56f, matNewJersey, matNewJerseyShade)

                    // 2. Flowering Coastal Bougainvillea / Oleander Median Planter every ~18m
                    if (i % 4 == 0) {
                        val flowerCol = if ((i / 4) % 2 == 0) matBougainvilleaA else matBougainvilleaB
                        addAxisBox(out, f0.pos.x - 0.42f, 0.86f, f0.pos.z - 0.42f, f0.pos.x + 0.42f, 1.05f, f0.pos.z + 0.42f, matSoilDark, matSoilDark, matSoilDark)
                        addAxisBox(out, f0.pos.x - 0.62f, 1.02f, f0.pos.z - 0.62f, f0.pos.x + 0.62f, 1.62f, f0.pos.z + 0.62f, matShrubGreen, matShrubGreen, matShrubGreen)
                        addAxisBox(out, f0.pos.x - 0.46f, 1.48f, f0.pos.z - 0.46f, f0.pos.x + 0.46f, 1.92f, f0.pos.z + 0.46f, flowerCol, flowerCol, flowerCol)
                    }

                    // 3. Outer Shoulder W-Beam Crash Guardrails + Yellow/Black Chevron Extruder End Terminals
                    //    are rendered uniformly across BOTH NH 544 and NH 66 by HighwayWBeamGuardrailRenderer.

                    // 4. Twin-Arm Curved Cobra-Head 5000K LED Highway Streetlight on Central Median every ~38m
                    if (f0.cumulativeDist >= nextLampDist) {
                        addNh66TwinArmStreetlight(out, f0.pos.x, 0.88f, f0.pos.z, f0.right.x, f0.right.z)
                        nextLampDist = f0.cumulativeDist + 38f
                    }

                    // 5. Authentic Blue-and-White NH 66 Kilometer Stone along East Shoulder every ~180m
                    if (f0.cumulativeDist >= nextKmStoneDist) {
                        val kx = f0.pos.x + f0.right.x * (f0.halfWidthM + 1.45f)
                        val kz = f0.pos.z + f0.right.z * (f0.halfWidthM + 1.45f)
                        addAxisBox(out, kx - 0.24f, 0.06f, kz - 0.14f, kx + 0.24f, 0.62f, kz + 0.14f, MatNattikaWhite, MatNattikaWhite, MatNattikaWhiteShade)
                        addAxisBox(out, kx - 0.25f, 0.62f, kz - 0.15f, kx + 0.25f, 0.96f, kz + 0.15f, MatHazardYellow, MatHazardYellow, MatHazardYellow)
                        nextKmStoneDist = f0.cumulativeDist + 180f
                    }
                }
                i += 2
            }
        }

        // 6. THREE FULL-SPAN NHAI GREEN OVERHEAD HIGHWAY GANTRIES ALONG NH 66 (Strictly clear of 22m deck!)
        val gantrySpecs = arrayOf(
            Triple(
                "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
                0.22f,
                createNh66OverheadGantrySignSpec(
                    cacheKey = "ZONE5_NH66_GANTRY_CHETTUVA_V1",
                    malayalamLine = "ചേറ്റുവ  •  നാട്ടിക  •  തൃപ്രയാർ",
                    englishLine = "NH 66  •  CHETTUVA  |  NATTIKA TOLL  |  TRIPRAYAR"
                )
            ),
            Triple(
                "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
                0.62f,
                createNh66OverheadGantrySignSpec(
                    cacheKey = "ZONE5_NH66_GANTRY_THALIKULAM_V1",
                    malayalamLine = "കാഞ്ഞാണി SH 61  ←   •   ↑  നാട്ടിക ടോൾ പ്ലാസ (NH 66)",
                    englishLine = "KANJANI SH 61  ←   •   ↑  NATTIKA TOLL PLAZA  •  KODUNGALLUR"
                )
            ),
            Triple(
                "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM",
                0.46f,
                createNh66OverheadGantrySignSpec(
                    cacheKey = "ZONE5_NH66_GANTRY_KODUNGALLUR_V1",
                    malayalamLine = "കൊടുങ്ങല്ലൂർ  •  കോട്ടപ്പുറം പാലം  •  എറണാകുളം",
                    englishLine = "NH 66  •  KODUNGALLUR  |  KOTTAPURAM NW-3 BRIDGE  |  KOCHI"
                )
            )
        )
        for ((roadId, frac, signSpec) in gantrySpecs) {
            val road = roads.firstOrNull { it.id == roadId } ?: continue
            val frames = road.frames
            if (frames.size < 10) continue
            val f = frames[(frac * (frames.size - 1)).toInt().coerceIn(4, frames.size - 5)]
            if (f.pos.y > 0.25f) continue
            val colSpan = f.halfWidthM + 1.55f
            val lx = f.pos.x - f.right.x * colSpan; val lz = f.pos.z - f.right.z * colSpan
            val rx = f.pos.x + f.right.x * colSpan; val rz = f.pos.z + f.right.z * colSpan
            // Twin galvanized steel vertical gantry towers (Y = 0..8.6m)
            addAxisBox(out, lx - 0.34f, 0.06f, lz - 0.34f, lx + 0.34f, 8.6f, lz + 0.34f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            addAxisBox(out, rx - 0.34f, 0.06f, rz - 0.34f, rx + 0.34f, 8.6f, rz + 0.34f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            // Horizontal overhead steel truss girder at Y = 6.2..7.8m (clear > 5.8m above 6-lane deck!)
            addOrientedWall(out, lx, lz, rx, rz, 6.35f, 7.95f, 0.38f, MatGalvanized, MatGalvanizedDark)
            // Illuminated green NHAI directional board facing approaching traffic
            val sx0 = f.pos.x - f.right.x * 8.6f; val sz0 = f.pos.z - f.right.z * 8.6f
            val sx1 = f.pos.x + f.right.x * 8.6f; val sz1 = f.pos.z + f.right.z * 8.6f
            val fxOff = -f.tangent.x * 0.28f; val fzOff = -f.tangent.z * 0.28f
            out.add(
                QuadFace3D(
                    Vec3(sx1 + fxOff, 6.15f, sz1 + fzOff),
                    Vec3(sx0 + fxOff, 6.15f, sz0 + fzOff),
                    Vec3(sx0 + fxOff, 8.25f, sz0 + fzOff),
                    Vec3(sx1 + fxOff, 8.25f, sz1 + fzOff),
                    MatNattikaNhaiGreen,
                    signboard = signSpec
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(sx0 - fxOff, 6.15f, sz0 - fzOff),
                    Vec3(sx1 - fxOff, 6.15f, sz1 - fzOff),
                    Vec3(sx1 - fxOff, 8.25f, sz1 - fzOff),
                    Vec3(sx0 - fxOff, 8.25f, sz0 - fzOff),
                    MatNattikaNhaiGreen,
                    signboard = signSpec
                )
            )
        }
    }

    private fun addNh66TwinArmStreetlight(
        out: MutableList<QuadFace3D>,
        cx: Float,
        baseY: Float,
        cz: Float,
        rx: Float,
        rz: Float
    ) {
        val poleTopY = baseY + 8.1f
        // Galvanized octagonal pole mast
        addAxisBox(out, cx - 0.11f, baseY, cz - 0.11f, cx + 0.11f, poleTopY, cz + 0.11f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
        // Twin swept cobra-head arms reaching 2.75m over both Southbound & Northbound carriageways
        val tx = -rz
        val tz = rx
        val halfThick = 0.055f
        for (side in floatArrayOf(-1f, 1f)) {
            val midX = cx + rx * side * 1.45f
            val midZ = cz + rz * side * 1.45f
            val tipX = cx + rx * side * 2.75f
            val tipZ = cz + rz * side * 2.75f
            // Swept inner arm segment (cx, poleTopY-0.25) -> (midX, poleTopY+0.45)
            val y0 = poleTopY - 0.25f
            val y1 = poleTopY + 0.45f
            val y2 = poleTopY + 0.62f
            out.add(QuadFace3D(Vec3(cx - tx * halfThick, y0 - 0.05f, cz - tz * halfThick), Vec3(midX - tx * halfThick, y1 - 0.05f, midZ - tz * halfThick), Vec3(midX - tx * halfThick, y1 + 0.05f, midZ - tz * halfThick), Vec3(cx - tx * halfThick, y0 + 0.05f, cz - tz * halfThick), MatGalvanized))
            out.add(QuadFace3D(Vec3(midX + tx * halfThick, y1 - 0.05f, midZ + tz * halfThick), Vec3(cx + tx * halfThick, y0 - 0.05f, cz + tz * halfThick), Vec3(cx + tx * halfThick, y0 + 0.05f, cz + tz * halfThick), Vec3(midX + tx * halfThick, y1 + 0.05f, midZ + tz * halfThick), MatGalvanizedDark))
            out.add(QuadFace3D(Vec3(cx - tx * halfThick, y0 + 0.05f, cz - tz * halfThick), Vec3(midX - tx * halfThick, y1 + 0.05f, midZ - tz * halfThick), Vec3(midX + tx * halfThick, y1 + 0.05f, midZ + tz * halfThick), Vec3(cx + tx * halfThick, y0 + 0.05f, cz + tz * halfThick), MatGalvanized))
            // Swept outer arm segment (midX, poleTopY+0.45) -> (tipX, poleTopY+0.62)
            out.add(QuadFace3D(Vec3(midX - tx * halfThick, y1 - 0.05f, midZ - tz * halfThick), Vec3(tipX - tx * halfThick, y2 - 0.05f, tipZ - tz * halfThick), Vec3(tipX - tx * halfThick, y2 + 0.05f, tipZ - tz * halfThick), Vec3(midX - tx * halfThick, y1 + 0.05f, midZ - tz * halfThick), MatGalvanized))
            out.add(QuadFace3D(Vec3(tipX + tx * halfThick, y2 - 0.05f, tipZ + tz * halfThick), Vec3(midX + tx * halfThick, y1 - 0.05f, midZ + tz * halfThick), Vec3(midX + tx * halfThick, y1 + 0.05f, midZ + tz * halfThick), Vec3(tipX + tx * halfThick, y2 + 0.05f, tipZ + tz * halfThick), MatGalvanizedDark))
            out.add(QuadFace3D(Vec3(midX - tx * halfThick, y1 + 0.05f, midZ - tz * halfThick), Vec3(tipX - tx * halfThick, y2 + 0.05f, tipZ - tz * halfThick), Vec3(tipX + tx * halfThick, y2 + 0.05f, tipZ + tz * halfThick), Vec3(midX + tx * halfThick, y1 + 0.05f, midZ + tz * halfThick), MatGalvanized))
            // Warm-white 5000K LED cobra-head luminaire fixture
            addAxisBox(out, tipX - 0.28f, poleTopY + 0.50f, tipZ - 0.22f, tipX + 0.28f, poleTopY + 0.72f, tipZ + 0.22f, MatGalvanizedDark, MatLedWhite, MatLedWarm)
        }
    }

    private fun createNh66OverheadGantrySignSpec(
        cacheKey: String,
        malayalamLine: String,
        englishLine: String
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 1024,
        bmpHeight = 160,
        isDoubleSided = false
    ) {
        val bmp = android.graphics.Bitmap.createBitmap(1024, 160, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF006B4F.toInt()
        c.drawRect(0f, 0f, 1024f, 160f, p)
        p.style = android.graphics.Paint.Style.STROKE
        p.color = 0xFFFFFFFF.toInt()
        p.strokeWidth = 8f
        c.drawRect(8f, 8f, 1016f, 152f, p)
        p.style = android.graphics.Paint.Style.FILL
        // Yellow NH 66 route shield on left
        p.color = 0xFFFACC15.toInt()
        c.drawRoundRect(android.graphics.RectF(28f, 28f, 136f, 132f), 14f, 14f, p)
        p.color = 0xFF111827.toInt()
        p.textAlign = android.graphics.Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        p.textSize = 26f
        c.drawText("NH", 82f, 68f, p)
        p.textSize = 38f
        c.drawText("66", 82f, 114f, p)
        // Trilingual crisp white highway sign text
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 34f
        c.drawText(malayalamLine, 575f, 70f, p)
        p.textSize = 28f
        p.color = 0xFFE2F5EE.toInt()
        c.drawText(englishLine, 575f, 122f, p)
        bmp
    }
}
