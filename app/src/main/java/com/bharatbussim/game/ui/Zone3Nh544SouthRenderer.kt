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
 * Zone 3: NH 544 South — Real-World Reference Architecture & Engineering:
 *  - Ollur Railway Overbridge (ROB): Steel-concrete composite plate-girder span with
 *    alpha-cutout galvanized 25kV wire-mesh safety screens & Southern Railway 25kV OHE
 *    lattice portals above real 3D Google Drive tracks and live 8-unit WAG-9 express trains.
 *  - Paliyekkara Toll Plaza (NH 544 Km 278.000, GIPL / NHAI): Iconic pitched Royal Blue
 *    corrugated steel canopy over open 3D tubular steel Warren space-frame truss, GIPL/NHAI
 *    branded fascia, chamfered glass-and-aluminum toll booths, 12 live pneumatic LED boom
 *    barriers, 2 free outer bypass lanes, and 2-storey GIPL Toll Control Building.
 *  - Kurumali River Bridge (140m Puthukkad) & Chalakudy River Bridge (280m Chalakudy Puzhapalam):
 *    6-girder PSC I-girder superstructures, hydrodynamic cutwater elliptical twin piers with
 *    monsoon HFL waterline weathering & flared hammerhead caps, alpha-cutout open slotted
 *    Kerala PWD balustrades with royal-blue handrails, sculpted organic sandbars & moored Vallams.
 *  - NH 544 Landscaped Bougainvillea Median, Kodakara Junction Gantry & Chalakudy South Bus Bay
 *    with pitched Mangalore terracotta clay-tile roof.
 */
internal object Zone3Nh544SouthRenderer {

    // =========================================================================
    // REAL-WORLD GEOMETRIC ANCHORS (100% CLEAR OF ALL EXISTING ROADS)
    // =========================================================================
    const val OLLUR_ROB_RAMP_NORTH_Z = -1330.0f
    const val OLLUR_ROB_DECK_NORTH_Z = -1385.0f
    const val OLLUR_ROB_CENTER_Z = -1430.0f
    const val OLLUR_ROB_DECK_SOUTH_Z = -1475.0f
    const val OLLUR_ROB_RAMP_SOUTH_Z = -1530.0f
    const val OLLUR_ROB_DECK_Y = 7.40f

    const val TOLL_CENTER_X = 999.5f
    const val TOLL_CENTER_Z = -1806.0f
    const val TOLL_FLARE_NORTH_START_Z = -1686.0f // 120m North of toll center (smooth 92m Cosine S-curve flare)
    const val TOLL_PQC_NORTH_Z = -1778.0f         // Full 80.4m plaza width begins (28m North of booths)
    const val TOLL_PQC_SOUTH_Z = -1834.0f         // Full 80.4m plaza width ends (28m South of booths)
    const val TOLL_FLARE_SOUTH_END_Z = -1926.0f   // 120m South of toll center (smooth 92m Cosine S-curve taper)
    const val TOLL_OUTER_HALF_W = 40.2f
    const val TOLL_LANE_COUNT = 12
    const val TOLL_LANE_PITCH = 5.40f          // 1.36m island + 4.04m clear unobstructed driving lane
    const val TOLL_ISLAND_HALF_W = 0.68f
    const val TOLL_CANOPY_HALF_W = 34.2f       // Spans full 68.4m across 12 lanes + structural overhang
    const val TOLL_CANOPY_CLEAR_Y = 5.75f

    const val KURUMALI_CENTER_X = 1366.0f
    const val KURUMALI_RAMP_NORTH_Z = -2120.0f
    const val KURUMALI_DECK_NORTH_Z = -2180.0f
    const val KURUMALI_CENTER_Z = -2250.0f
    const val KURUMALI_DECK_SOUTH_Z = -2320.0f
    const val KURUMALI_RAMP_SOUTH_Z = -2380.0f
    const val KURUMALI_DECK_Y = 6.20f

    const val KODAKARA_X = 2016.0f
    const val KODAKARA_Z = -3437.0f

    const val CHALAKUDY_CENTER_X = 2596.0f
    const val CHALAKUDY_RAMP_NORTH_Z = -4840.0f
    const val CHALAKUDY_DECK_NORTH_Z = -4900.0f
    const val CHALAKUDY_CENTER_Z = -5040.0f
    const val CHALAKUDY_DECK_SOUTH_Z = -5180.0f
    const val CHALAKUDY_RAMP_SOUTH_Z = -5240.0f
    const val CHALAKUDY_DECK_Y = 8.20f

    const val CHALAKUDY_TERM_X = 2596.0f
    const val CHALAKUDY_TERM_Z = -5365.0f

    // =========================================================================
    // AUTHENTIC KERALA NH 544 / GIPL / PWD MATERIAL PALETTE
    // =========================================================================
    private val MatPaliyekkaraTerracotta = Color(0xFFD95B34)
    private val MatPaliyekkaraTerracottaDark = Color(0xFFB5411F)
    private val MatKeralaGableDark = Color(0xFF1A222D)
    private val MatNhaiTeal = Color(0xFF007C7E)
    private val MatGiplRoyalBlue = Color(0xFF0052A5)
    private val MatGiplRoyalBlueDark = Color(0xFF003875)
    private val MatAcpWhite = Color(0xFFF1F5F9)
    private val MatAcpSilver = Color(0xFFCBD5E1)
    private val MatAcpSilverDark = Color(0xFF94A3B8)
    private val MatTrussSteel = Color(0xFF334155)
    private val MatTrussSteelDark = Color(0xFF1E293B)
    private val MatPlateGirderWeb = Color(0xFF3B4859)
    private val MatPlateGirderFlange = Color(0xFF232D3B)
    private val MatConcreteDeck = Color(0xFFD5DAE1)
    private val MatConcreteDeckShadow = Color(0xFF9AA3B0)
    private val MatConcretePier = Color(0xFFB5BDC8)
    private val MatConcretePierDark = Color(0xFF7A8494)
    private val MatConcreteMossWet = Color(0xFF4B5546)     // Monsoon HFL algae/silt waterline stain
    private val MatConcreteCaisson = Color(0xFF3F473E)     // Deep submerged well-foundation cap
    private val MatNeopreneBearing = Color(0xFF18181B)
    private val MatConcreteApron = Color(0xFF9DA5B0)       // Sunlit light-grey concrete plaza apron (matches photo!)
    private val MatTollLaneDark = Color(0xFF6B7280)        // Tire-wear dark concrete lane strip
    private val MatServiceRoadAsphalt = Color(0xFF2D333D)
    private val MatHazardYellow = Color(0xFFFACC15)
    private val MatHazardOrange = Color(0xFFEA580C)
    private val MatHazardBlack = Color(0xFF18181B)
    private val MatTintedGlass = Color(0xFF0F2942)
    private val MatLedGreen = Color(0xFF22C55E)
    private val MatLedRed = Color(0xFFEF4444)
    private val MatLedWhite = Color(0xFFF8FAFC)
    private val MatLedWarm = Color(0xFFFEF08A)
    private val MatGalvanized = Color(0xFFCBD5E1)
    private val MatGalvanizedDark = Color(0xFF64748B)
    private val MatMedianSoil = Color(0xFF4A3728)
    private val MatMedianGrass = Color(0xFF2F5E24)
    private val MatMedianGrassLight = Color(0xFF3F7A30)
    private val MatBougainvilleaMagenta = Color(0xFFDB2777)
    private val MatBougainvilleaCrimson = Color(0xFFE11D48)
    private val MatBougainvilleaPurple = Color(0xFFA21CAF)
    private val MatBougainvilleaLight = Color(0xFFF472B6)
    private val MatRiverSandbank = Color(0xFFCBB27E)
    private val MatRiverSandCrest = Color(0xFFDCC694)
    private val MatRiverSandWet = Color(0xFF8C7752)
    private val MatRiverbedDark = Color(0xFF12323D)
    private val MatRiverRock = Color(0xFF575E66)
    private val MatRiverRockDark = Color(0xFF393F46)
    private val MatVallamWood = Color(0xFF5C3419)
    private val MatVallamWoodLight = Color(0xFF854D27)
    private val MatBambooPole = Color(0xFF9E8C4A)
    private val MatBallastDark = Color(0xFF3B3F46)
    private val MatCopperWire = Color(0xFFB45309)
    private val MatInsulatorBrown = Color(0xFF6D2E1D)
    private val MatTimberBrown = Color(0xFF5B341B)
    private val MatTerracottaTile = Color(0xFF9A3412)

    // =========================================================================
    // PUBLIC COORDINATE & SURFACE QUERIES (PRESERVED API)
    // =========================================================================
    fun ollurRobCenterXAt(z: Float): Float {
        return 958.85f + (-1430.0f - z) * 0.032f
    }

    fun tollLaneCenterX(laneIdx: Int): Float {
        return TOLL_CENTER_X + (laneIdx - 5.5f) * TOLL_LANE_PITCH
    }

    fun tollIslandX(islandIdx: Int): Float {
        return TOLL_CENTER_X + (islandIdx - 6) * TOLL_LANE_PITCH
    }

    fun isTollLaneActiveFastag(laneIdx: Int): Boolean = laneIdx in 1..10

    fun nearestTollLaneIndex(wx: Float): Int {
        val raw = ((wx - TOLL_CENTER_X) / TOLL_LANE_PITCH) + 5.5f
        val idx = kotlin.math.round(raw).toInt()
        return if (idx in 0 until TOLL_LANE_COUNT) idx else -1
    }

    /**
     * Single source of truth for Paliyekkara Toll Plaza's smooth Cosine S-curve highway half-width.
     * Shared by RoadFrame generation (shoulders, bevel, asphalt ribbon, outer white edge lines) AND
     * the PQC concrete toll pavement overlay so the highway joins the toll plaza with 0.000m seams!
     */
    fun paliyekkaraFlareHalfWidthAtZ(z: Float): Float {
        return when {
            z >= TOLL_FLARE_NORTH_START_Z -> 12.0f
            z > TOLL_PQC_NORTH_Z -> {
                val u = ((TOLL_FLARE_NORTH_START_Z - z) / (TOLL_FLARE_NORTH_START_Z - TOLL_PQC_NORTH_Z)).coerceIn(0f, 1f)
                val s = 0.5f - 0.5f * cos(u * PI.toFloat())
                12.0f + (TOLL_OUTER_HALF_W - 12.0f) * s
            }
            z >= TOLL_PQC_SOUTH_Z -> TOLL_OUTER_HALF_W
            z > TOLL_FLARE_SOUTH_END_Z -> {
                val u = ((TOLL_PQC_SOUTH_Z - z) / (TOLL_PQC_SOUTH_Z - TOLL_FLARE_SOUTH_END_Z)).coerceIn(0f, 1f)
                val s = 0.5f - 0.5f * cos(u * PI.toFloat())
                TOLL_OUTER_HALF_W + (10.0f - TOLL_OUTER_HALF_W) * s
            }
            else -> 10.0f
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

    fun computeZone3BridgeSurfaceY(x: Float, z: Float): Float {
        var y = 0.06f
        if (z in OLLUR_ROB_RAMP_SOUTH_Z..OLLUR_ROB_RAMP_NORTH_Z) {
            val robX = ollurRobCenterXAt(z)
            if (abs(x - robX) <= 15.5f) {
                val p = cosineBridgeProfileSouthbound(
                    z, OLLUR_ROB_RAMP_NORTH_Z, OLLUR_ROB_DECK_NORTH_Z,
                    OLLUR_ROB_DECK_SOUTH_Z, OLLUR_ROB_RAMP_SOUTH_Z
                )
                y = max(y, 0.06f + p * (OLLUR_ROB_DECK_Y - 0.06f))
            }
        }
        if (z in KURUMALI_RAMP_SOUTH_Z..KURUMALI_RAMP_NORTH_Z && abs(x - KURUMALI_CENTER_X) <= 15.5f) {
            val p = cosineBridgeProfileSouthbound(
                z, KURUMALI_RAMP_NORTH_Z, KURUMALI_DECK_NORTH_Z,
                KURUMALI_DECK_SOUTH_Z, KURUMALI_RAMP_SOUTH_Z
            )
            y = max(y, 0.06f + p * (KURUMALI_DECK_Y - 0.06f))
        }
        if (z in CHALAKUDY_RAMP_SOUTH_Z..CHALAKUDY_RAMP_NORTH_Z && abs(x - CHALAKUDY_CENTER_X) <= 15.5f) {
            val p = cosineBridgeProfileSouthbound(
                z, CHALAKUDY_RAMP_NORTH_Z, CHALAKUDY_DECK_NORTH_Z,
                CHALAKUDY_DECK_SOUTH_Z, CHALAKUDY_RAMP_SOUTH_Z
            )
            y = max(y, 0.06f + p * (CHALAKUDY_DECK_Y - 0.06f))
        }
        return y
    }

    fun computeZone3OverheadCeilingY(x: Float, z: Float): Float {
        if (x in (TOLL_CENTER_X - TOLL_CANOPY_HALF_W - 2f)..(TOLL_CENTER_X + TOLL_CANOPY_HALF_W + 2f) &&
            z in (TOLL_CENTER_Z - 9.5f)..(TOLL_CENTER_Z + 9.5f)
        ) {
            return 5.35f
        }
        return 1000f
    }

    fun isPointClearOfZone3Structures(px: Float, pz: Float): Boolean {
        if (px in 945f..1068f && pz in -1872f..-1740f) return false
        if (pz in (TOLL_FLARE_SOUTH_END_Z - 16f)..(TOLL_FLARE_NORTH_START_Z + 16f)) {
            val clearanceHalfW = paliyekkaraFlareHalfWidthAtZ(pz) + 12.0f
            if (abs(px - TOLL_CENTER_X) <= clearanceHalfW) return false
        }
        if (px in 760f..1160f && pz in -1452f..-1408f) return false
        if (px in 1150f..1585f && pz in -2325f..-2175f) return false
        if (px in 2345f..2850f && pz in -5188f..-4892f) return false
        if (px in 2566f..2644f && pz in -5425f..-5325f) return false
        return true
    }

    // =========================================================================
    // GROUND PLAZAS: 12-LANE TOLL APRON, SERVICE ROADS, RIVERBEDS & TERMINUS
    // =========================================================================
    fun DrawScope.drawZone3GroundPlazas(
        rig: CameraRig,
        path: Path,
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>
    ) {
        val drawAll = rig.recorder != null

        // 1. PALIYEKKARA TOLL PLAZA PAVEMENT IS RENDERED IN PASS 2C (drawPaliyekkaraTollPavementOverlay)
        //    DIRECTLY ON TOP OF THE CONTINUOUS S-CURVE FLARED NH 544 ASPHALT RIBBON FOR ZERO SEAMS!

        // 2. OLLUR ROB RAILWAY BALLAST BED UNDERNEATH BRIDGE (Z = -1440..-1420, X = 770..1150)
        if (drawAll || hypot(camEye.x - 959f, camEye.z - OLLUR_ROB_CENTER_Z) < 500f) {
            val ballastPts = listOf(
                Vec3(770f, 0.042f, OLLUR_ROB_CENTER_Z - 7.5f),
                Vec3(1150f, 0.042f, OLLUR_ROB_CENTER_Z - 7.5f),
                Vec3(1150f, 0.042f, OLLUR_ROB_CENTER_Z + 7.5f),
                Vec3(770f, 0.042f, OLLUR_ROB_CENTER_Z + 7.5f)
            )
            if (rig.buildWorldPolygonPath(path, ballastPts)) {
                drawPath(path, MatBallastDark)
            }
        }

        // 3. KURUMALI RIVERBED & SANDY RIVERBANKS
        if (drawAll || hypot(camEye.x - KURUMALI_CENTER_X, camEye.z - KURUMALI_CENTER_Z) < 620f) {
            for (bankZ in arrayOf(Pair(-2185f, -2166f), Pair(-2334f, -2315f))) {
                val bankPts = listOf(
                    Vec3(1155f, 0.044f, bankZ.first),
                    Vec3(1580f, 0.044f, bankZ.first),
                    Vec3(1580f, 0.044f, bankZ.second),
                    Vec3(1155f, 0.044f, bankZ.second)
                )
                if (rig.buildWorldPolygonPath(path, bankPts)) {
                    drawPath(path, MatRiverSandbank)
                }
            }
            val riverBedPts = listOf(
                Vec3(1155f, 0.032f, -2316f),
                Vec3(1580f, 0.032f, -2316f),
                Vec3(1580f, 0.032f, -2184f),
                Vec3(1155f, 0.032f, -2184f)
            )
            if (rig.buildWorldPolygonPath(path, riverBedPts)) {
                drawPath(path, MatRiverbedDark)
            }
        }

        // 4. CHALAKUDY RIVERBED & SANDBANKS (280m Major Water Bridge 1)
        if (drawAll || hypot(camEye.x - CHALAKUDY_CENTER_X, camEye.z - CHALAKUDY_CENTER_Z) < 760f) {
            for (bankZ in arrayOf(Pair(-4906f, -4882f), Pair(-5198f, -5174f))) {
                val bankPts = listOf(
                    Vec3(2350f, 0.044f, bankZ.first),
                    Vec3(2845f, 0.044f, bankZ.first),
                    Vec3(2845f, 0.044f, bankZ.second),
                    Vec3(2350f, 0.044f, bankZ.second)
                )
                if (rig.buildWorldPolygonPath(path, bankPts)) {
                    drawPath(path, MatRiverSandbank)
                }
            }
            val riverBedPts = listOf(
                Vec3(2350f, 0.032f, -5175f),
                Vec3(2845f, 0.032f, -5175f),
                Vec3(2845f, 0.032f, -4905f),
                Vec3(2350f, 0.032f, -4905f)
            )
            if (rig.buildWorldPolygonPath(path, riverBedPts)) {
                drawPath(path, MatRiverbedDark)
            }
        }

        // 5. CHALAKUDY SOUTH NH 544 BUS BAY & TURNAROUND APRON
        if (drawAll || hypot(camEye.x - CHALAKUDY_TERM_X, camEye.z - CHALAKUDY_TERM_Z) < 420f) {
            val termPad = listOf(
                Vec3(CHALAKUDY_TERM_X - 20f, 0.056f, -5330f),
                Vec3(CHALAKUDY_TERM_X + 36f, 0.056f, -5330f),
                Vec3(CHALAKUDY_TERM_X + 36f, 0.056f, -5412f),
                Vec3(CHALAKUDY_TERM_X - 20f, 0.056f, -5412f)
            )
            if (rig.buildWorldPolygonPath(path, termPad)) {
                drawPath(path, MatConcreteApron)
            }
            for (b in 0..4) {
                val bz = -5348f - b * 11.0f
                val stripe = listOf(
                    Vec3(2610f, 0.064f, bz - 0.16f),
                    Vec3(2630f, 0.064f, bz - 0.16f),
                    Vec3(2630f, 0.064f, bz + 0.16f),
                    Vec3(2610f, 0.064f, bz + 0.16f)
                )
                if (rig.buildWorldPolygonPath(path, stripe)) {
                    drawPath(path, MatHazardYellow)
                }
            }
        }

        // 6. DEDICATED 5.5m ONE-WAY SERVICE ROADS ON BOTH SIDES OF NH 544 SOUTH
        drawNh544SouthServiceRoads(rig, path, camEye, roads, drawAll)
    }

    /**
     * PASS 2C: Draws Paliyekkara Toll Plaza's Rigid PQC Concrete Pavement Slab, Asphalt-to-Concrete
     * Expansion Joints, Fan-Out Highway-to-Toll Lane Guides, Yellow Rumble Strips, 12 Toll Lanes,
     * Green/Red FASTag RFID Stop Strips, and 2 Outer Bypass Lanes cleanly ON TOP OF the continuous
     * S-curve flared NH 544 asphalt ribbon (so shoulders, edge lips, and outer white lines wrap
     * unbroken around the entire 240m toll plaza approach!).
     */
    fun DrawScope.drawPaliyekkaraTollPavementOverlay(
        rig: CameraRig,
        path: Path,
        camEye: Vec3
    ) {
        val drawAll = rig.recorder != null
        if (!drawAll && hypot(camEye.x - TOLL_CENTER_X, camEye.z - TOLL_CENTER_Z) > 560f) return

        val tx = TOLL_CENTER_X
        val tz = TOLL_CENTER_Z
        val pqcStartNorthZ = -1728.0f // Rigid PQC concrete slab begins 78m North of toll booths
        val pqcEndSouthZ = -1884.0f   // Rigid PQC concrete slab ends 78m South of toll booths

        // 1. Smooth 28-segment Sunlit PQC Concrete Toll Plaza Pavement matching exact S-curve road width!
        val slabSteps = 28
        for (k in 0 until slabSteps) {
            val z0 = pqcStartNorthZ - (k.toFloat() / slabSteps) * (pqcStartNorthZ - pqcEndSouthZ)
            val z1 = pqcStartNorthZ - ((k + 1).toFloat() / slabSteps) * (pqcStartNorthZ - pqcEndSouthZ)
            val hw0 = paliyekkaraFlareHalfWidthAtZ(z0) - 0.04f
            val hw1 = paliyekkaraFlareHalfWidthAtZ(z1) - 0.04f
            val segPoly = listOf(
                Vec3(tx - hw0, 0.060f, z0),
                Vec3(tx + hw0, 0.060f, z0),
                Vec3(tx + hw1, 0.060f, z1),
                Vec3(tx - hw1, 0.060f, z1)
            )
            if (rig.buildWorldPolygonPath(path, segPoly)) drawPath(path, MatConcreteApron)
        }

        // Crisp Transverse Dark Bituminous Expansion-Joint Header Strips at Asphalt <-> PQC Concrete transitions
        for (jointZ in floatArrayOf(pqcStartNorthZ, pqcEndSouthZ)) {
            val jHW = paliyekkaraFlareHalfWidthAtZ(jointZ) - 0.05f
            val jointBar = listOf(
                Vec3(tx - jHW, 0.0615f, jointZ - 0.24f),
                Vec3(tx + jHW, 0.0615f, jointZ - 0.24f),
                Vec3(tx + jHW, 0.0615f, jointZ + 0.24f),
                Vec3(tx - jHW, 0.0615f, jointZ + 0.24f)
            )
            if (rig.buildWorldPolygonPath(path, jointBar)) drawPath(path, MatHazardBlack)
        }

        // 2. Continuous Solid Yellow Center Median Lines seamlessly linking North & South NH 544 medians
        //    to the modular concrete approach divider blocks (0.000m gap at Z = -1686 & -1926!)
        for (side in floatArrayOf(-1f, 1f)) {
            val nMedLine = listOf(
                Vec3(tx + side * 0.74f, 0.063f, TOLL_FLARE_NORTH_START_Z + 1.5f),
                Vec3(tx + side * 0.90f, 0.063f, TOLL_FLARE_NORTH_START_Z + 1.5f),
                Vec3(tx + side * 0.90f, 0.063f, tz + 12.0f),
                Vec3(tx + side * 0.74f, 0.063f, tz + 12.0f)
            )
            if (rig.buildWorldPolygonPath(path, nMedLine)) drawPath(path, MatHazardYellow)

            val sMedLine = listOf(
                Vec3(tx + side * 0.74f, 0.063f, tz - 12.0f),
                Vec3(tx + side * 0.90f, 0.063f, tz - 12.0f),
                Vec3(tx + side * 0.90f, 0.063f, TOLL_FLARE_SOUTH_END_Z - 1.5f),
                Vec3(tx + side * 0.74f, 0.063f, TOLL_FLARE_SOUTH_END_Z - 1.5f)
            )
            if (rig.buildWorldPolygonPath(path, sMedLine)) drawPath(path, MatHazardYellow)
        }

        // 3. Graceful Dashed White Fan-Out Approach Lane Guide Lines leading traffic smoothly from
        //    the 2+2 highway carriageways into the 12 toll lanes on both North & South S-curve flares!
        val fanTargets = floatArrayOf(-27.0f, -21.6f, -16.2f, -10.8f, -5.4f, 5.4f, 10.8f, 16.2f, 21.6f, 27.0f)
        for (targetOff in fanTargets) {
            val sign = if (targetOff < 0f) -1f else 1f
            val frac = abs(targetOff) / 32.4f
            val northStartX = tx + sign * (1.2f + frac * 10.2f)
            val southEndX = tx + sign * (1.2f + frac * 8.2f)
            val boothApproachX = tx + targetOff
            for (dash in 0 until 7) {
                val u0 = (dash * 2.0f) / 14.0f
                val u1 = (dash * 2.0f + 1.0f) / 14.0f
                val s0 = 0.5f - 0.5f * cos(u0 * PI.toFloat())
                val s1 = 0.5f - 0.5f * cos(u1 * PI.toFloat())

                // North approach fan dash
                val nz0 = TOLL_FLARE_NORTH_START_Z + (TOLL_PQC_NORTH_Z - TOLL_FLARE_NORTH_START_Z) * u0
                val nz1 = TOLL_FLARE_NORTH_START_Z + (TOLL_PQC_NORTH_Z - TOLL_FLARE_NORTH_START_Z) * u1
                val nx0 = northStartX + (boothApproachX - northStartX) * s0
                val nx1 = northStartX + (boothApproachX - northStartX) * s1
                val nDash = listOf(
                    Vec3(nx0 - 0.09f, 0.063f, nz0),
                    Vec3(nx0 + 0.09f, 0.063f, nz0),
                    Vec3(nx1 + 0.09f, 0.063f, nz1),
                    Vec3(nx1 - 0.09f, 0.063f, nz1)
                )
                if (rig.buildWorldPolygonPath(path, nDash)) drawPath(path, MatLedWhite)

                // South exit merge dash
                val sz0 = TOLL_PQC_SOUTH_Z + (TOLL_FLARE_SOUTH_END_Z - TOLL_PQC_SOUTH_Z) * u0
                val sz1 = TOLL_PQC_SOUTH_Z + (TOLL_FLARE_SOUTH_END_Z - TOLL_PQC_SOUTH_Z) * u1
                val sx0 = boothApproachX + (southEndX - boothApproachX) * s0
                val sx1 = boothApproachX + (southEndX - boothApproachX) * s1
                val sDash = listOf(
                    Vec3(sx0 - 0.09f, 0.063f, sz0),
                    Vec3(sx0 + 0.09f, 0.063f, sz0),
                    Vec3(sx1 + 0.09f, 0.063f, sz1),
                    Vec3(sx1 - 0.09f, 0.063f, sz1)
                )
                if (rig.buildWorldPolygonPath(path, sDash)) drawPath(path, MatLedWhite)
            }
        }

        // 4. Multi-bar Yellow Thermoplastic Speed-Calming Rumble Strips cleanly fitted inside flare width
        for (dirZ in floatArrayOf(1f, -1f)) {
            for (bar in 0..2) {
                val rz = tz + dirZ * (44.0f + bar * 2.4f)
                val barHW = (paliyekkaraFlareHalfWidthAtZ(rz) - 2.2f).coerceAtLeast(8.0f)
                // Split left & right of central median so strips never cross concrete divider blocks
                for (side in floatArrayOf(-1f, 1f)) {
                    val xA = tx + side * 1.15f
                    val xB = tx + side * barHW
                    val strip = listOf(
                        Vec3(min(xA, xB), 0.063f, rz - 0.28f),
                        Vec3(max(xA, xB), 0.063f, rz - 0.28f),
                        Vec3(max(xA, xB), 0.063f, rz + 0.28f),
                        Vec3(min(xA, xB), 0.063f, rz + 0.28f)
                    )
                    if (rig.buildWorldPolygonPath(path, strip)) drawPath(path, MatHazardYellow)
                }
            }
        }

        // 5. 12 Individual Toll Lanes (Dark Tire-Wear Strips, Yellow Rumble Strips & FASTag RFID Stop Lines)
        for (laneIdx in 0 until TOLL_LANE_COUNT) {
            val lx = tollLaneCenterX(laneIdx)
            val laneQuad = listOf(
                Vec3(lx - 1.85f, 0.0615f, tz + 26.0f),
                Vec3(lx + 1.85f, 0.0615f, tz + 26.0f),
                Vec3(lx + 1.85f, 0.0615f, tz - 26.0f),
                Vec3(lx - 1.85f, 0.0615f, tz - 26.0f)
            )
            if (rig.buildWorldPolygonPath(path, laneQuad)) drawPath(path, MatTollLaneDark)

            // Double yellow approach rumble bars ahead of booth
            for (rz in floatArrayOf(tz + 13.5f, tz + 12.2f, tz - 12.2f, tz - 13.5f)) {
                val rumble = listOf(
                    Vec3(lx - 1.80f, 0.0635f, rz - 0.24f),
                    Vec3(lx + 1.80f, 0.0635f, rz - 0.24f),
                    Vec3(lx + 1.80f, 0.0635f, rz + 0.24f),
                    Vec3(lx - 1.80f, 0.0635f, rz + 0.24f)
                )
                if (rig.buildWorldPolygonPath(path, rumble)) drawPath(path, MatHazardYellow)
            }

            // Crisp green/red FASTag RFID scanning stop-line zone on BOTH North & South approaches!
            val rfidCol = if (isTollLaneActiveFastag(laneIdx)) MatLedGreen else MatLedRed
            for (rfidZ in floatArrayOf(tz + 7.5f, tz - 8.3f)) {
                val rfidStrip = listOf(
                    Vec3(lx - 1.75f, 0.0635f, rfidZ),
                    Vec3(lx + 1.75f, 0.0635f, rfidZ),
                    Vec3(lx + 1.75f, 0.0635f, rfidZ + 0.80f),
                    Vec3(lx - 1.75f, 0.0635f, rfidZ + 0.80f)
                )
                if (rig.buildWorldPolygonPath(path, rfidStrip)) drawPath(path, rfidCol)
            }
        }

        // 6. 2 Dedicated Free Outer Bypass Lanes (Two-Wheelers, Auto-Rickshaws & Emergency Ambulances)
        for (side in floatArrayOf(-1f, 1f)) {
            val bx = tx + side * 36.4f
            val bypassAsphalt = listOf(
                Vec3(bx - 2.65f, 0.062f, tz + 28.0f),
                Vec3(bx + 2.65f, 0.062f, tz + 28.0f),
                Vec3(bx + 2.65f, 0.062f, tz - 28.0f),
                Vec3(bx - 2.65f, 0.062f, tz - 28.0f)
            )
            if (rig.buildWorldPolygonPath(path, bypassAsphalt)) drawPath(path, MatServiceRoadAsphalt)

            val greenLine = listOf(
                Vec3(bx - 0.22f, 0.064f, tz + 24.0f),
                Vec3(bx + 0.22f, 0.064f, tz + 24.0f),
                Vec3(bx + 0.22f, 0.064f, tz - 24.0f),
                Vec3(bx - 0.22f, 0.064f, tz - 24.0f)
            )
            if (rig.buildWorldPolygonPath(path, greenLine)) drawPath(path, MatLedGreen)
        }
    }

    private fun DrawScope.drawNh544SouthServiceRoads(
        rig: CameraRig,
        path: Path,
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>,
        drawAll: Boolean
    ) {
        val targetIds = setOf("ROAD_NH544_MANNUTHY_TO_PALIYEKKARA", "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY")
        for (road in roads) {
            if (road.id !in targetIds) continue
            val frames = road.frames
            if (frames.size < 12) continue

            fun inServiceRoadZone(z: Float, y: Float): Boolean {
                if (y > 0.30f) return false
                return (z in -1280f..-660f && abs(z - (-927f)) > 36f) ||
                    (z in -2095f..-1945f) ||
                    (z in -2880f..-2405f) ||
                    (z in -3820f..-3120f) ||
                    (z in -4805f..-4260f)
            }

            var i = 0
            while (i < frames.size - 2) {
                val f0 = frames[i]
                val f1 = frames[min(frames.size - 1, i + 2)]
                if (!inServiceRoadZone(f0.pos.z, f0.pos.y) || !inServiceRoadZone(f1.pos.z, f1.pos.y)) {
                    i += 2
                    continue
                }
                if (!drawAll && hypot(camEye.x - f0.pos.x, camEye.z - f0.pos.z) > 480f) {
                    i += 2
                    continue
                }
                val vergeInner = f0.halfWidthM + 1.8f
                val svcInner = f0.halfWidthM + 4.0f
                val svcOuter = svcInner + 5.5f

                for (side in floatArrayOf(-1f, 1f)) {
                    val vPts = listOf(
                        Vec3(f0.pos.x + f0.right.x * side * vergeInner, 0.056f, f0.pos.z + f0.right.z * side * vergeInner),
                        Vec3(f0.pos.x + f0.right.x * side * svcInner, 0.056f, f0.pos.z + f0.right.z * side * svcInner),
                        Vec3(f1.pos.x + f1.right.x * side * svcInner, 0.056f, f1.pos.z + f1.right.z * side * svcInner),
                        Vec3(f1.pos.x + f1.right.x * side * vergeInner, 0.056f, f1.pos.z + f1.right.z * side * vergeInner)
                    )
                    if (rig.buildWorldPolygonPath(path, vPts)) drawPath(path, MatMedianGrass)

                    val sPts = listOf(
                        Vec3(f0.pos.x + f0.right.x * side * svcInner, 0.058f, f0.pos.z + f0.right.z * side * svcInner),
                        Vec3(f0.pos.x + f0.right.x * side * svcOuter, 0.058f, f0.pos.z + f0.right.z * side * svcOuter),
                        Vec3(f1.pos.x + f1.right.x * side * svcOuter, 0.058f, f1.pos.z + f1.right.z * side * svcOuter),
                        Vec3(f1.pos.x + f1.right.x * side * svcInner, 0.058f, f1.pos.z + f1.right.z * side * svcInner)
                    )
                    if (rig.buildWorldPolygonPath(path, sPts)) drawPath(path, MatServiceRoadAsphalt)
                }
                i += 2
            }
        }
    }

    // =========================================================================
    // STATIC 3D GEOMETRY CACHE (COMPILED ONCE INTO HARDWARE OPENGL ES VBO)
    // =========================================================================
    @Volatile
    private var cachedZone3Faces: List<QuadFace3D>? = null
    @Volatile
    private var cachedRoadsCount: Int = -1

    fun collectAllZone3StaticFaces(
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        val existing = cachedZone3Faces
        if (existing != null && cachedRoadsCount == roads.size) {
            outFaces.addAll(existing)
            return
        }
        val built = ArrayList<QuadFace3D>(9200)
        buildPaliakkaraTollPlaza3D(built)
        buildOllurRailwayOverbridge3D(roads, built)
        buildKurumaliRiverBridge3D(built)
        buildChalakudyRiverBridge3D(built)
        buildNh544SouthMedianBougainvilleaAndGantries(roads, built)
        buildChalakudySouthTerminus3D(built)

        cachedZone3Faces = built
        cachedRoadsCount = roads.size
        outFaces.addAll(built)
    }

    fun collectZone3FacesNearCamera(
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        if (camEye.z > -400f && camEye.x < 450f) return
        collectAllZone3StaticFaces(roads, outFaces)
    }

    // =========================================================================
    // 1. REAL-WORLD PALIYEKKARA TOLL PLAZA (NH 544 KM 278.000 — GIPL / NHAI)
    //    Faithfully modeled after the real-world Paliyekkara Toll Plaza photograph:
    //    - Iconic 3-Pavilion Traditional Kerala Terracotta Roof Silhouette:
    //       1) Central Two-Tier Kerala Pagoda Pavilion with panoramic white-mullioned
    //          clerestory window band & upper dark Mukhappu gable dormer
    //       2) Long Horizontal Terracotta Pitched Main Canopy spanning all 12 lanes
    //          with 5 rhythmic dark triangular Kerala dormers & white steel columns
    //       3) Flanking Tall West & East Kerala Gable Pavilions over the Free Outer
    //          Bypass Lanes featuring massive dark triangular Mukhappu gables with
    //          white ornamental lattice filigree!
    //    - Tall vertical Teal-Green (#007C7E) & Saffron-Orange (#EA681E) NHAI lane signs
    //    - Modular grey concrete approach median divider blocks with steel U-loop handles
    //    - 3 slender white high-mast floodlight towers rising behind the terracotta roofs
    // =========================================================================
    private fun buildPaliakkaraTollPlaza3D(out: MutableList<QuadFace3D>) {
        val tx = TOLL_CENTER_X
        val tz = TOLL_CENTER_Z
        val mainHalfW = 32.8f // Main horizontal canopy spans all 12 lanes (X = tx ± 32.8m)

        val terracottaRoofTex = Zone3ArchitecturalTextures.createPaliyekkaraTerracottaRoofSpec()
        val gableMukhappuTex = Zone3ArchitecturalTextures.createPaliyekkaraGableMukhappuSpec()
        val clerestoryTex = Zone3ArchitecturalTextures.createPaliyekkaraClerestorySpec()
        val centerPlaqueTex = Zone3ArchitecturalTextures.createPaliyekkaraCenterPlaqueSpec()
        val boothFacadeTex = Zone3ArchitecturalTextures.createTollBoothFacadeSpec()
        val adminFacadeTex = Zone3ArchitecturalTextures.createGiplAdminFacadeSpec()

        // A. 13 TAPERED CONCRETE SAFETY ISLANDS, YELLOW-BLACK BOLLARDS/DRUMS,
        //    CRISP WHITE STEEL SUPPORT COLUMNS, CHAMFERED GLASS TOLL BOOTHS & SIGNALS
        //    (Strictly within ±0.68m of island center -> 4.04m clear width in all 12 lanes!)
        for (iIdx in 0..TOLL_LANE_COUNT) {
            val ix = tollIslandX(iIdx)
            val hwBase = TOLL_ISLAND_HALF_W // 0.68m
            val hwTop = 0.56f

            // 1. Sloped Trapezoidal Concrete Island with Yellow-Black End Chevron Curbs
            val segs = 12
            val z0Base = tz - 9.0f
            val segLen = 18.0f / segs
            for (s in 0 until segs) {
                val sz0 = z0Base + s * segLen
                val sz1 = sz0 + segLen
                val isEndStripe = s < 3 || s >= segs - 3
                val curbCol = when {
                    isEndStripe && s % 2 == 0 -> MatHazardYellow
                    isEndStripe -> MatHazardBlack
                    else -> MatConcreteDeck
                }
                addTrapezoidIslandSegment(out, ix, sz0, sz1, 0.06f, 0.40f, hwBase, hwTop, curbCol, MatConcreteDeckShadow)
            }

            // 2. Stepped Concrete Bullnose + Yellow-Black Striped Crash Barrels / Safety Drums
            for (dirZ in floatArrayOf(-1f, 1f)) {
                val noseZ = tz + dirZ * 9.55f
                addTrapezoidIslandSegment(
                    out, ix,
                    min(noseZ, noseZ + dirZ * 1.1f), max(noseZ, noseZ + dirZ * 1.1f),
                    0.06f, 0.50f, 0.62f, 0.42f, MatHazardYellow, MatHazardBlack
                )
                val b1Z = tz + dirZ * 10.30f
                addOctagonalPillar(out, ix, b1Z, 0.06f, 1.10f, 0.48f, MatHazardYellow, Color(0xFFEAB308))
                addOctagonalPillar(out, ix, b1Z, 0.64f, 0.80f, 0.50f, MatHazardBlack, MatHazardBlack)
                addOctagonalPillar(out, ix, b1Z, 0.28f, 0.42f, 0.50f, MatHazardBlack, MatHazardBlack)
                val b2Z = tz + dirZ * 11.20f
                addOctagonalPillar(out, ix, b2Z, 0.06f, 0.92f, 0.38f, MatHazardYellow, Color(0xFFEAB308))
                addOctagonalPillar(out, ix, b2Z, 0.52f, 0.66f, 0.40f, MatHazardBlack, MatHazardBlack)
            }

            // 3. Clean White Rectangular Steel Columns (exact match to photo's bright white pillars!)
            if (iIdx % 2 == 0) {
                // Yellow-black base bumper collar + tall crisp white column rising to dark truss soffit
                addAxisBox(out, ix - 0.34f, 0.40f, tz - 0.34f, ix + 0.34f, 1.05f, tz + 0.34f, MatHazardYellow, MatHazardBlack, MatHazardYellow)
                addAxisBox(out, ix - 0.28f, 1.05f, tz - 0.30f, ix + 0.28f, 6.18f, tz + 0.30f, MatAcpWhite, MatAcpSilver, MatAcpWhite)
                // Subtle dark structural steel haunch brackets supporting main truss
                addDiagonalStrut(out, Vec3(ix, 5.10f, tz), Vec3(ix, 6.12f, tz + 2.1f), 0.11f, MatTrussSteel, MatTrussSteelDark)
                addDiagonalStrut(out, Vec3(ix, 5.10f, tz), Vec3(ix, 6.12f, tz - 2.1f), 0.11f, MatTrussSteel, MatTrussSteelDark)
            }

            // 4. Modular Glass-and-White Paliyekkara Toll Operator Booths (iIdx = 1..11, width ±0.56m!)
            if (iIdx in 1..11) {
                val bw = 0.54f
                val bz0 = tz - 2.35f
                val bz1 = tz + 2.35f
                addAxisBox(out, ix - bw + 0.02f, 0.40f, bz0, ix + bw - 0.02f, 2.74f, bz1, MatAcpWhite, MatAcpSilverDark, MatNhaiTeal)
                out.add(
                    QuadFace3D(
                        Vec3(ix - bw - 0.01f, 0.40f, bz0),
                        Vec3(ix - bw - 0.01f, 0.40f, bz1),
                        Vec3(ix - bw - 0.01f, 2.74f, bz1),
                        Vec3(ix - bw - 0.01f, 2.74f, bz0),
                        MatAcpWhite, null, -0.05f, signboard = boothFacadeTex
                    )
                )
                out.add(
                    QuadFace3D(
                        Vec3(ix + bw + 0.01f, 0.40f, bz1),
                        Vec3(ix + bw + 0.01f, 0.40f, bz0),
                        Vec3(ix + bw + 0.01f, 2.74f, bz0),
                        Vec3(ix + bw + 0.01f, 2.74f, bz1),
                        MatAcpWhite, null, -0.05f, signboard = boothFacadeTex
                    )
                )
                for (endDir in floatArrayOf(-1f, 1f)) {
                    val ez = tz + endDir * 2.37f
                    addAxisBox(out, ix - 0.42f, 1.38f, ez - 0.02f, ix + 0.42f, 2.52f, ez + 0.02f, MatTintedGlass, MatTintedGlass, MatTintedGlass)
                }
                addAxisBox(out, ix - 0.64f, 1.28f, tz - 0.55f, ix + 0.64f, 1.36f, tz + 0.55f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
                addAxisBox(out, ix - 0.62f, 2.74f, bz0 - 0.24f, ix + 0.62f, 2.94f, bz1 + 0.24f, MatPaliyekkaraTerracotta, MatPaliyekkaraTerracotta, MatPaliyekkaraTerracottaDark)
                addAxisBox(out, ix - 0.34f, 2.94f, tz - 1.55f, ix + 0.34f, 3.44f, tz - 0.72f, MatAcpWhite, MatAcpSilverDark, MatAcpWhite)

                // 2-Aspect Red/Green Lane Signal Housing beside booth (Live RED/GREEN lenses rendered dynamically!)
                val laneIdx = iIdx - 1
                val sigZ = if (laneIdx < 6) tz + 2.75f else tz - 2.75f
                addAxisBox(out, ix - 0.05f, 0.40f, sigZ - 0.05f, ix + 0.05f, 2.75f, sigZ + 0.05f, MatGalvanizedDark, MatGalvanizedDark, MatGalvanizedDark)
                addAxisBox(out, ix - 0.20f, 2.12f, sigZ - 0.14f, ix + 0.20f, 2.86f, sigZ + 0.14f, MatHazardBlack, MatHazardBlack, MatHazardBlack)
            }

            // 5. Pneumatic Boom Barrier Actuator Pedestal Housing (on both approaches cleanly aligned with boom pivot)
            if (iIdx in 1..12) {
                val laneIdx = iIdx - 1
                val pedZ = if (laneIdx < 6) tz - 3.6f else tz + 3.6f
                addAxisBox(out, ix - 0.22f, 0.40f, pedZ - 0.24f, ix + 0.22f, 1.24f, pedZ + 0.24f, MatHazardOrange, MatHazardOrange, MatHazardYellow)
                addAxisBox(out, ix - 0.24f, 1.24f, pedZ - 0.26f, ix + 0.24f, 1.32f, pedZ + 0.26f, MatHazardBlack, MatHazardBlack, MatHazardBlack)
            }

            // 6. CCTV & ANPR Camera Masts on Islands 2, 6, 10
            if (iIdx == 2 || iIdx == 6 || iIdx == 10) {
                for (camDir in floatArrayOf(-1f, 1f)) {
                    val cz = tz + camDir * 6.8f
                    addAxisBox(out, ix - 0.06f, 0.40f, cz - 0.06f, ix + 0.06f, 4.45f, cz + 0.06f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
                    addAxisBox(out, ix - 0.14f, 4.25f, cz + camDir * 0.05f, ix + 0.14f, 4.48f, cz + camDir * 0.45f, MatAcpWhite, MatAcpSilverDark, MatHazardBlack)
                }
            }
        }

        // B. APPROACH MODULAR CONCRETE MEDIAN DIVIDER BLOCKS WITH STEEL U-LOOP TOP HANDLES!
        //    (Exact match to the foreground right of the real Paliyekkara photo — strictly centered
        //     on X = tx ± 0.40m between Southbound and Northbound lanes, running seamlessly along
        //     both 120m North & South toll flare approaches directly into the NH 544 highway medians!)
        for (dirZ in floatArrayOf(1f, -1f)) {
            var mz = 13.0f
            while (mz <= 114.0f) {
                val cz = tz + dirZ * mz
                // Beveled modular grey concrete divider block (0.80m W x 1.55m L x 0.72m H)
                addTrapezoidIslandSegment(out, tx, cz - 0.78f, cz + 0.78f, 0.06f, 0.74f, 0.44f, 0.34f, MatConcretePier, MatConcreteDeck)
                // Twin upright steel U-loop guard / lifting handles on top of each concrete block!
                for (uOff in floatArrayOf(-0.34f, 0.34f)) {
                    val uz = cz + uOff
                    addAxisBox(out, tx - 0.04f, 0.74f, uz - 0.16f, tx + 0.04f, 0.98f, uz - 0.10f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
                    addAxisBox(out, tx - 0.04f, 0.74f, uz + 0.10f, tx + 0.04f, 0.98f, uz + 0.16f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
                    addAxisBox(out, tx - 0.04f, 0.94f, uz - 0.16f, tx + 0.04f, 1.00f, uz + 0.16f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
                }
                mz += 2.35f
            }
        }

        // C. OPEN 3D DARK STEEL UNDERSIDE TRUSS & BRIGHT LED CEILING BAYS (Y = 5.85m .. 6.45m)
        val trussYBot = 5.85f
        val trussYTop = 6.45f
        for (beamZ in floatArrayOf(tz - 6.4f, tz - 2.1f, tz + 2.1f, tz + 6.4f)) {
            addAxisBox(out, tx - mainHalfW, trussYBot, beamZ - 0.13f, tx + mainHalfW, trussYBot + 0.18f, beamZ + 0.13f, MatTrussSteel, MatTrussSteelDark, MatTrussSteel)
            addAxisBox(out, tx - mainHalfW, trussYTop - 0.16f, beamZ - 0.13f, tx + mainHalfW, trussYTop, beamZ + 0.13f, MatTrussSteel, MatTrussSteelDark, MatTrussSteel)
        }
        for (bay in -6..6) {
            val bx = tx + bay * TOLL_LANE_PITCH
            addAxisBox(out, bx - 0.10f, trussYBot, tz - 6.4f, bx + 0.10f, trussYBot + 0.16f, tz + 6.4f, MatTrussSteel, MatTrussSteelDark, MatTrussSteel)
            for (zStep in arrayOf(-6.4f to -2.1f, -2.1f to 2.1f, 2.1f to 6.4f)) {
                val zMid = (zStep.first + zStep.second) * 0.5f
                addDiagonalStrut(out, Vec3(bx, trussYBot + 0.08f, tz + zStep.first), Vec3(bx, trussYTop - 0.06f, tz + zMid), 0.07f, MatTrussSteel, MatTrussSteelDark)
                addDiagonalStrut(out, Vec3(bx, trussYTop - 0.06f, tz + zMid), Vec3(bx, trussYBot + 0.08f, tz + zStep.second), 0.07f, MatTrussSteel, MatTrussSteelDark)
            }
        }
        // Bright soffit ceiling + recessed LED bay fixtures above all 12 lanes
        out.add(
            QuadFace3D(
                Vec3(tx - mainHalfW, trussYTop, tz - 6.8f),
                Vec3(tx + mainHalfW, trussYTop, tz - 6.8f),
                Vec3(tx + mainHalfW, trussYTop, tz + 6.8f),
                Vec3(tx - mainHalfW, trussYTop, tz + 6.8f),
                MatAcpWhite, null, 0f
            )
        )
        for (laneIdx in 0 until TOLL_LANE_COUNT) {
            val lx = tollLaneCenterX(laneIdx)
            for (lz in floatArrayOf(tz - 3.6f, tz, tz + 3.6f)) {
                addAxisBox(out, lx - 0.65f, trussYTop - 0.08f, lz - 0.35f, lx + 0.65f, trussYTop + 0.01f, lz + 0.35f, MatLedWhite, MatLedWarm, MatLedWhite)
            }
        }

        // D. LONG HORIZONTAL KERALA TERRACOTTA MAIN CANOPY ROOF (X = tx ± 32.8m, Y = 6.38m -> 8.35m)
        //    Dark charcoal structural eave trim + bright pitched terracotta ribbed slopes + 5 dark dormers!
        val mainEaveY = 6.38f
        val mainRidgeY = 8.35f
        val mainEaveZ = 7.05f
        val mainRidgeZ = 1.20f
        // Dark steel fascia edging bar on North & South eaves
        for (faceDir in floatArrayOf(1f, -1f)) {
            val fz = tz + faceDir * mainEaveZ
            addAxisBox(out, tx - mainHalfW, mainEaveY - 0.36f, fz - 0.16f, tx + mainHalfW, mainEaveY + 0.06f, fz + 0.16f, MatKeralaGableDark, MatTrussSteelDark, MatKeralaGableDark)
        }
        // Main pitched terracotta roof slopes split into 3 clean panels across the 65.6m width for crisp rib scale
        val mainPanels = arrayOf(Pair(-mainHalfW, -10.5f), Pair(-10.5f, 10.5f), Pair(10.5f, mainHalfW))
        for ((px0, px1) in mainPanels) {
            val rx0 = tx + px0
            val rx1 = tx + px1
            // North facing terracotta slope (+Z)
            out.add(
                QuadFace3D(
                    Vec3(rx1, mainEaveY, tz + mainEaveZ),
                    Vec3(rx0, mainEaveY, tz + mainEaveZ),
                    Vec3(rx0, mainRidgeY, tz + mainRidgeZ),
                    Vec3(rx1, mainRidgeY, tz + mainRidgeZ),
                    MatPaliyekkaraTerracotta, null, -0.05f, signboard = terracottaRoofTex
                )
            )
            // South facing terracotta slope (-Z)
            out.add(
                QuadFace3D(
                    Vec3(rx0, mainEaveY, tz - mainEaveZ),
                    Vec3(rx1, mainEaveY, tz - mainEaveZ),
                    Vec3(rx1, mainRidgeY, tz - mainRidgeZ),
                    Vec3(rx0, mainRidgeY, tz - mainRidgeZ),
                    MatPaliyekkaraTerracotta, null, -0.05f, signboard = terracottaRoofTex
                )
            )
        }
        // Top flat terracotta deck crown bridging ridge Z (-1.2 .. +1.2)
        addAxisBox(out, tx - mainHalfW, mainRidgeY - 0.10f, tz - mainRidgeZ, tx + mainHalfW, mainRidgeY + 0.06f, tz + mainRidgeZ, MatPaliyekkaraTerracottaDark, MatPaliyekkaraTerracottaDark, MatPaliyekkaraTerracottaDark)

        // 5 Iconic Small Dark Triangular Kerala Roof Dormers (Mukhappu) along the Main Terracotta Canopy!
        // (Left-outer, Left-inner, Center-under-tower, Right-inner, Right-outer — exact match to photo!)
        for (dormXOff in floatArrayOf(-24.8f, -14.2f, 0.0f, 14.2f, 24.8f)) {
            val dx = tx + dormXOff
            val dHalfW = if (dormXOff == 0.0f) 2.55f else 2.05f
            val dPeakH = if (dormXOff == 0.0f) 1.55f else 1.25f
            for (faceDir in floatArrayOf(1f, -1f)) {
                val baseZ = tz + faceDir * (mainEaveZ - 0.15f)
                val peakZ = tz + faceDir * (mainEaveZ - 2.30f)
                val baseY = mainEaveY + 0.08f
                val peakY = baseY + dPeakH
                // Dark triangular front gable opening
                out.add(
                    QuadFace3D(
                        Vec3(dx - dHalfW, baseY, baseZ),
                        Vec3(dx + dHalfW, baseY, baseZ),
                        Vec3(dx, peakY, baseZ),
                        Vec3(dx, peakY, baseZ),
                        MatKeralaGableDark, null, -0.08f
                    )
                )
                // Left & right sloped terracotta cheek eaves of the small dormer
                out.add(QuadFace3D(Vec3(dx - dHalfW - 0.18f, baseY - 0.05f, baseZ + faceDir * 0.15f), Vec3(dx, peakY + 0.08f, baseZ + faceDir * 0.15f), Vec3(dx, peakY + 0.08f, peakZ), Vec3(dx - dHalfW, baseY, peakZ), MatPaliyekkaraTerracotta, null, 0f))
                out.add(QuadFace3D(Vec3(dx + dHalfW + 0.18f, baseY - 0.05f, baseZ + faceDir * 0.15f), Vec3(dx, peakY + 0.08f, baseZ + faceDir * 0.15f), Vec3(dx, peakY + 0.08f, peakZ), Vec3(dx + dHalfW, baseY, peakZ), MatPaliyekkaraTerracottaDark, null, 0f))
            }
        }

        // E. CENTRAL TWO-TIER KERALA GOPURAM / NALUKETTU PAGODA PAVILION (Exact centerpiece of the photo!)
        //    1) Dark recessed plinth rising from main canopy (Y = 8.15 .. 8.55m)
        addAxisBox(out, tx - 8.6f, 8.10f, tz - 5.2f, tx + 8.6f, 8.55f, tz + 5.2f, MatKeralaGableDark, MatKeralaGableDark, MatKeralaGableDark)

        //    2) Tier 1: Wide Flared Lower Terracotta Skirt Roof (Y = 8.45m -> 10.85m)
        val t1BotX = 10.6f; val t1BotZ = 6.2f; val t1BotY = 8.45f
        val t1TopX = 7.8f;  val t1TopZ = 4.2f; val t1TopY = 10.85f
        addFourSidedPyramidalTerracottaRoof(out, tx, tz, t1BotX, t1BotZ, t1BotY, t1TopX, t1TopZ, t1TopY, terracottaRoofTex)

        //    3) Middle Clerestory Panoramic White-Mullioned Glass Window Band (Y = 10.85m -> 12.05m)
        val clHW = 7.65f
        val clHZ = 4.05f
        val clY0 = 10.85f
        val clY1 = 12.05f
        addAxisBox(out, tx - clHW + 0.08f, clY0, tz - clHZ + 0.08f, tx + clHW - 0.08f, clY1, tz + clHZ - 0.08f, MatKeralaGableDark, MatAcpWhite, MatAcpWhite)
        // Clerestory glass ribbon on all 4 sides (North, South, West, East)
        out.add(QuadFace3D(Vec3(tx + clHW, clY0, tz + clHZ + 0.04f), Vec3(tx - clHW, clY0, tz + clHZ + 0.04f), Vec3(tx - clHW, clY1, tz + clHZ + 0.04f), Vec3(tx + clHW, clY1, tz + clHZ + 0.04f), MatAcpWhite, null, -0.06f, signboard = clerestoryTex))
        out.add(QuadFace3D(Vec3(tx - clHW, clY0, tz - clHZ - 0.04f), Vec3(tx + clHW, clY0, tz - clHZ - 0.04f), Vec3(tx + clHW, clY1, tz - clHZ - 0.04f), Vec3(tx - clHW, clY1, tz - clHZ - 0.04f), MatAcpWhite, null, -0.06f, signboard = clerestoryTex))
        out.add(QuadFace3D(Vec3(tx - clHW - 0.04f, clY0, tz - clHZ), Vec3(tx - clHW - 0.04f, clY0, tz + clHZ), Vec3(tx - clHW - 0.04f, clY1, tz + clHZ), Vec3(tx - clHW - 0.04f, clY1, tz - clHZ), MatAcpWhite, null, -0.06f, signboard = clerestoryTex))
        out.add(QuadFace3D(Vec3(tx + clHW + 0.04f, clY0, tz + clHZ), Vec3(tx + clHW + 0.04f, clY0, tz - clHZ), Vec3(tx + clHW + 0.04f, clY1, tz - clHZ), Vec3(tx + clHW + 0.04f, clY1, tz + clHZ), MatAcpWhite, null, -0.06f, signboard = clerestoryTex))

        //    4) Tier 2: Steep Upper Terracotta Hipped Roof (Y = 12.00m -> 15.25m) + Central Dark Mukhappu Gable!
        val t2BotX = 8.65f; val t2BotZ = 4.95f; val t2BotY = 12.00f
        val t2TopX = 6.90f; val t2TopZ = 0.22f; val t2TopY = 15.25f
        addFourSidedPyramidalTerracottaRoof(out, tx, tz, t2BotX, t2BotZ, t2BotY, t2TopX, t2TopZ, t2TopY, terracottaRoofTex)
        // Terracotta ridge beam along Y = 15.25m
        addAxisBox(out, tx - t2TopX - 0.15f, t2TopY - 0.08f, tz - 0.26f, tx + t2TopX + 0.15f, t2TopY + 0.16f, tz + 0.26f, MatPaliyekkaraTerracottaDark, MatKeralaGableDark, MatPaliyekkaraTerracottaDark)
        // Prominent dark triangular center Mukhappu dormer on upper Tier 2 roof (North & South faces!)
        for (faceDir in floatArrayOf(1f, -1f)) {
            val gBaseY = 13.05f
            val gPeakY = 15.20f
            val gBaseZ = tz + faceDir * 3.45f
            val gPeakZ = tz + faceDir * 0.28f
            val gHW = 2.65f
            out.add(QuadFace3D(Vec3(tx - gHW, gBaseY, gBaseZ), Vec3(tx + gHW, gBaseY, gBaseZ), Vec3(tx, gPeakY, gBaseZ), Vec3(tx, gPeakY, gBaseZ), MatKeralaGableDark, null, -0.08f))
            // Overhanging terracotta dormer eaves
            out.add(QuadFace3D(Vec3(tx - gHW - 0.22f, gBaseY - 0.06f, gBaseZ + faceDir * 0.18f), Vec3(tx, gPeakY + 0.12f, gBaseZ + faceDir * 0.18f), Vec3(tx, gPeakY + 0.12f, gPeakZ), Vec3(tx - gHW, gBaseY, gPeakZ), MatPaliyekkaraTerracotta, null, 0f))
            out.add(QuadFace3D(Vec3(tx + gHW + 0.22f, gBaseY - 0.06f, gBaseZ + faceDir * 0.18f), Vec3(tx, gPeakY + 0.12f, gBaseZ + faceDir * 0.18f), Vec3(tx, gPeakY + 0.12f, gPeakZ), Vec3(tx + gHW, gBaseY, gPeakZ), MatPaliyekkaraTerracottaDark, null, 0f))
        }

        //    5) Clean Bilingual Paliyekkara Toll Plaza Name Plaque centered cleanly above lanes 5 & 6
        for (faceDir in floatArrayOf(1f, -1f)) {
            val pz = tz + faceDir * (mainEaveZ + 0.24f)
            if (faceDir > 0f) {
                out.add(QuadFace3D(Vec3(tx + 5.6f, 6.50f, pz), Vec3(tx - 5.6f, 6.50f, pz), Vec3(tx - 5.6f, 7.50f, pz), Vec3(tx + 5.6f, 7.50f, pz), MatKeralaGableDark, null, -0.24f, signboard = centerPlaqueTex))
            } else {
                out.add(QuadFace3D(Vec3(tx - 5.6f, 6.50f, pz), Vec3(tx + 5.6f, 6.50f, pz), Vec3(tx + 5.6f, 7.50f, pz), Vec3(tx - 5.6f, 7.50f, pz), MatKeralaGableDark, null, -0.24f, signboard = centerPlaqueTex))
            }
        }

        // F. LEFT & RIGHT TALL FLANKING KERALA GABLE PAVILIONS OVER FREE OUTER BYPASS LANES!
        //    (Exact match to the far-left & far-right tall peaked roofs with massive dark triangular
        //     Mukhappu gables & white lattice ornament in the real Paliyekkara photo!)
        val freeBypassSign = Zone3ArchitecturalTextures.createPaliyekkaraVerticalLaneSignSpec(
            cacheKey = "ZONE3_PALIYEKKARA_FREE_LANE_VERT_V3",
            isOrangeHighlight = false,
            laneLabel = "FREE LANE",
            line1 = "2W / AUTO",
            line2 = "AMBULANCE"
        )
        for (side in floatArrayOf(-1f, 1f)) {
            val pcx = tx + side * 37.0f // Centered over the outer free bypass lane (tx ± 36.4m)
            val pHW = 7.45f             // 14.9m wide flaring eaves
            val pHZ = 6.95f
            val eaveY = 7.40f
            val ridgeY = 13.10f
            val gableBaseY = 9.05f

            // Outer white steel support columns strictly OUTSIDE the bypass lane (X = tx ± 41.8m, clear of ±39.05m!)
            val outerColX = tx + side * 41.8f
            for (czOff in floatArrayOf(-3.2f, 3.2f)) {
                addAxisBox(out, outerColX - 0.30f, 0.06f, tz + czOff - 0.30f, outerColX + 0.30f, 0.95f, tz + czOff + 0.30f, MatHazardYellow, MatHazardBlack, MatHazardYellow)
                addAxisBox(out, outerColX - 0.25f, 0.95f, tz + czOff - 0.25f, outerColX + 0.25f, eaveY - 0.25f, tz + czOff + 0.25f, MatAcpWhite, MatAcpSilver, MatAcpWhite)
            }
            // Inner dark steel cross-beam & truss spanning across the outer bypass lane (Y = 6.75..7.40m)
            addAxisBox(out, pcx - pHW + 1.2f, 6.75f, tz - 5.8f, pcx + pHW - 1.2f, 7.35f, tz + 5.8f, MatKeralaGableDark, MatTrussSteelDark, MatKeralaGableDark)

            // 1) Lower Flared Terracotta Skirt Band on North & South faces (Y = 7.40m -> 9.05m)
            val skTopHW = 5.45f
            val skTopHZ = 4.95f
            addFourSidedPyramidalTerracottaRoof(out, pcx, tz, pHW, pHZ, eaveY, skTopHW, skTopHZ, gableBaseY, terracottaRoofTex)

            // 2) Steep Upper Peaked Terracotta Roof Slopes (West & East slopes meeting at central ridge X = pcx, Y = 13.10m!)
            out.add(
                QuadFace3D(
                    Vec3(pcx - skTopHW - 0.35f, gableBaseY - 0.15f, tz - skTopHZ - 0.35f),
                    Vec3(pcx - skTopHW - 0.35f, gableBaseY - 0.15f, tz + skTopHZ + 0.35f),
                    Vec3(pcx, ridgeY, tz + skTopHZ * 0.72f),
                    Vec3(pcx, ridgeY, tz - skTopHZ * 0.72f),
                    MatPaliyekkaraTerracotta, null, -0.05f, signboard = terracottaRoofTex
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(pcx + skTopHW + 0.35f, gableBaseY - 0.15f, tz + skTopHZ + 0.35f),
                    Vec3(pcx + skTopHW + 0.35f, gableBaseY - 0.15f, tz - skTopHZ - 0.35f),
                    Vec3(pcx, ridgeY, tz - skTopHZ * 0.72f),
                    Vec3(pcx, ridgeY, tz + skTopHZ * 0.72f),
                    MatPaliyekkaraTerracotta, null, -0.05f, signboard = terracottaRoofTex
                )
            )
            addAxisBox(out, pcx - 0.22f, ridgeY - 0.06f, tz - skTopHZ * 0.74f, pcx + 0.22f, ridgeY + 0.18f, tz + skTopHZ * 0.74f, MatKeralaGableDark, MatPaliyekkaraTerracottaDark, MatKeralaGableDark)

            // 3) LARGE ICONIC DARK TRIANGULAR KERALA MUKHAPPU GABLE WITH WHITE ORNAMENTAL FILIGREE
            for (faceDir in floatArrayOf(1f, -1f)) {
                val gz = tz + faceDir * (skTopHZ + 0.08f)
                if (faceDir > 0f) {
                    out.add(
                        QuadFace3D(
                            Vec3(pcx + skTopHW, gableBaseY, gz),
                            Vec3(pcx - skTopHW, gableBaseY, gz),
                            Vec3(pcx - skTopHW, ridgeY + 0.08f, gz),
                            Vec3(pcx + skTopHW, ridgeY + 0.08f, gz),
                            MatKeralaGableDark, null, -0.10f, signboard = gableMukhappuTex
                        )
                    )
                } else {
                    out.add(
                        QuadFace3D(
                            Vec3(pcx - skTopHW, gableBaseY, gz),
                            Vec3(pcx + skTopHW, gableBaseY, gz),
                            Vec3(pcx + skTopHW, ridgeY + 0.08f, gz),
                            Vec3(pcx - skTopHW, ridgeY + 0.08f, gz),
                            MatKeralaGableDark, null, -0.10f, signboard = gableMukhappuTex
                        )
                    )
                }

                val signZ = tz + faceDir * (pHZ - 0.2f)
                if (faceDir > 0f) {
                    out.add(QuadFace3D(Vec3(pcx + 1.45f, 5.15f, signZ), Vec3(pcx - 1.45f, 5.15f, signZ), Vec3(pcx - 1.45f, 7.10f, signZ), Vec3(pcx + 1.45f, 7.10f, signZ), MatNhaiTeal, null, -0.22f, signboard = freeBypassSign))
                } else {
                    out.add(QuadFace3D(Vec3(pcx - 1.45f, 5.15f, signZ), Vec3(pcx + 1.45f, 5.15f, signZ), Vec3(pcx + 1.45f, 7.10f, signZ), Vec3(pcx - 1.45f, 7.10f, signZ), MatNhaiTeal, null, -0.22f, signboard = freeBypassSign))
                }
            }
        }

        // G. TALL VERTICAL TEAL-GREEN & SAFFRON-ORANGE NHAI OVERHEAD LANE SIGNBOARDS + RFID + VMS
        val tealBusSign = Zone3ArchitecturalTextures.createPaliyekkaraVerticalLaneSignSpec(
            cacheKey = "ZONE3_PALIYEKKARA_TEAL_BUS_V3",
            isOrangeHighlight = false,
            laneLabel = "FASTag",
            line1 = "BUS / MAV",
            line2 = "HEAVY VEH"
        )
        val tealCarSign = Zone3ArchitecturalTextures.createPaliyekkaraVerticalLaneSignSpec(
            cacheKey = "ZONE3_PALIYEKKARA_TEAL_CAR_V3",
            isOrangeHighlight = false,
            laneLabel = "FASTag",
            line1 = "CAR / LCV",
            line2 = "AUTO SCAN"
        )
        val orangeFastagSign = Zone3ArchitecturalTextures.createPaliyekkaraVerticalLaneSignSpec(
            cacheKey = "ZONE3_PALIYEKKARA_ORANGE_FASTAG_V3",
            isOrangeHighlight = true,
            laneLabel = "FASTag",
            line1 = "EXPRESS",
            line2 = "NON-STOP"
        )
        val closedCrossSign = createBannerSignSpec(
            cacheKey = "ZONE3_TOLL_LANE_RED_CROSS_V3",
            bgColorInt = 0xFF7F1D1D.toInt(),
            borderColorInt = 0xFFEF4444.toInt(),
            titleText = "× CLOSED ×",
            subText = "USE NEXT LANE",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFECACA.toInt(),
            bmpWidth = 192,
            bmpHeight = 256
        )
        val vmsFareSign = createBannerSignSpec(
            cacheKey = "ZONE3_TOLL_VMS_FARE_V3",
            bgColorInt = 0xFF020617.toInt(),
            borderColorInt = 0xFFFACC15.toInt(),
            titleText = "FASTag OK • FARE ₹50",
            subText = "BUS / COACH • AUTO RFID",
            titleColorInt = 0xFF4ADE80.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 384,
            bmpHeight = 128
        )

        for (laneIdx in 0 until TOLL_LANE_COUNT) {
            val lx = tollLaneCenterX(laneIdx)
            val isOpen = isTollLaneActiveFastag(laneIdx)
            val isOrangeLane = (laneIdx == 3 || laneIdx == 8)
            val signSpec = when {
                !isOpen -> closedCrossSign
                isOrangeLane -> orangeFastagSign
                laneIdx % 2 == 0 -> tealBusSign
                else -> tealCarSign
            }
            val ledCol = if (isOpen) MatLedGreen else MatLedRed

            for (faceDir in floatArrayOf(1f, -1f)) {
                val sz = tz + faceDir * (mainEaveZ + 0.25f)
                val sY0 = 5.65f
                val sY1 = 7.82f
                val sHW = 1.25f
                if (laneIdx != 5 && laneIdx != 6) {
                    if (faceDir > 0f) {
                        out.add(QuadFace3D(Vec3(lx + sHW, sY0, sz), Vec3(lx - sHW, sY0, sz), Vec3(lx - sHW, sY1, sz), Vec3(lx + sHW, sY1, sz), MatNhaiTeal, null, -0.24f, signboard = signSpec))
                    } else {
                        out.add(QuadFace3D(Vec3(lx - sHW, sY0, sz), Vec3(lx + sHW, sY0, sz), Vec3(lx + sHW, sY1, sz), Vec3(lx - sHW, sY1, sz), MatNhaiTeal, null, -0.24f, signboard = signSpec))
                    }
                } else {
                    // Cleanly below the central title plaque (Y = 5.38..6.36m, zero overlap!)
                    val cY0 = 5.38f; val cY1 = 6.36f
                    if (faceDir > 0f) {
                        out.add(QuadFace3D(Vec3(lx + 1.05f, cY0, sz + 0.04f), Vec3(lx - 1.05f, cY0, sz + 0.04f), Vec3(lx - 1.05f, cY1, sz + 0.04f), Vec3(lx + 1.05f, cY1, sz + 0.04f), MatNhaiTeal, null, -0.26f, signboard = signSpec))
                    } else {
                        out.add(QuadFace3D(Vec3(lx - 1.05f, cY0, sz - 0.04f), Vec3(lx + 1.05f, cY0, sz - 0.04f), Vec3(lx + 1.05f, cY1, sz - 0.04f), Vec3(lx - 1.05f, cY1, sz - 0.04f), MatNhaiTeal, null, -0.26f, signboard = signSpec))
                    }
                }
            }

            // Overhead High-Speed FASTag RFID Transceiver Antenna mounted 8.2m ahead of booth
            val rfidZ = if (laneIdx < 6) tz + 8.2f else tz - 8.2f
            addAxisBox(out, lx - 0.55f, 5.42f, rfidZ - 0.32f, lx + 0.55f, 5.72f, rfidZ + 0.32f, MatAcpWhite, MatTrussSteel, MatAcpWhite)
            addAxisBox(out, lx - 0.42f, 5.34f, rfidZ - 0.20f, lx + 0.42f, 5.42f, rfidZ + 0.20f, ledCol, ledCol, ledCol)

            // Electronic Variable Message Fare Board mounted beside active booths
            if (isOpen && laneIdx in 2..9) {
                val boardIx = tollIslandX(laneIdx + 1)
                val bz = if (laneIdx < 6) tz + 3.25f else tz - 3.25f
                addAxisBox(out, boardIx - 0.48f, 2.95f, bz - 0.12f, boardIx + 0.48f, 3.68f, bz + 0.12f, Color(0xFF020617), Color(0xFF020617), Color(0xFF020617))
                if (laneIdx < 6) {
                    out.add(QuadFace3D(Vec3(boardIx + 0.46f, 2.98f, bz + 0.14f), Vec3(boardIx - 0.46f, 2.98f, bz + 0.14f), Vec3(boardIx - 0.46f, 3.65f, bz + 0.14f), Vec3(boardIx + 0.46f, 3.65f, bz + 0.14f), Color(0xFF020617), null, -0.22f, signboard = vmsFareSign))
                } else {
                    out.add(QuadFace3D(Vec3(boardIx - 0.46f, 2.98f, bz - 0.14f), Vec3(boardIx + 0.46f, 2.98f, bz - 0.14f), Vec3(boardIx + 0.46f, 3.65f, bz - 0.14f), Vec3(boardIx - 0.46f, 3.65f, bz - 0.14f), Color(0xFF020617), null, -0.22f, signboard = vmsFareSign))
                }
            }
        }

        // Slender Galvanized RFID Antenna Cross-Beam at Z = tz ± 8.2m (legs strictly outside bypass lanes!)
        for (rfidZ in floatArrayOf(tz + 8.2f, tz - 8.2f)) {
            addAxisBox(out, tx - mainHalfW, 5.72f, rfidZ - 0.14f, tx + mainHalfW, 5.92f, rfidZ + 0.14f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            for (side in floatArrayOf(-1f, 1f)) {
                val legX = tx + side * 41.8f
                addOctagonalPillar(out, legX, rfidZ, 0.06f, 5.95f, 0.20f, MatGalvanized, MatGalvanizedDark)
            }
        }

        // H. FOUR SLENDER TALL WHITE HIGH-MAST FLOODLIGHT TOWERS STRICTLY OUTSIDE ALL DRIVABLE APRONS!
        //    (Positioned safely beyond the ±40.2m bypass lanes & tapered South flare while framing
        //     the exact skyline silhouette behind the terracotta roofs in the photo!)
        val highMasts = arrayOf(
            Triple(tx - 45.5f, tz - 44.5f, 27.5f), // 7.6m outside West South S-curve flare edge
            Triple(tx + 45.5f, tz - 44.5f, 26.0f), // 7.6m outside East South S-curve flare edge
            Triple(tx + 46.5f, tz - 16.5f, 28.5f), // 6.3m outside East outer bypass lane
            Triple(tx - 46.5f, tz + 16.5f, 27.0f)  // 6.3m outside West outer bypass lane
        )
        for ((mx, mz, mh) in highMasts) {
            addTaperedOctagonalPillar(out, mx, mz, 0.06f, mh, 0.36f, 0.13f, MatAcpWhite, MatAcpSilver)
            // Slender high-mast luminaire ring + glowing LED floodlight cluster
            addOctagonalPillar(out, mx, mz, mh - 0.35f, mh + 0.15f, 1.45f, MatTrussSteel, MatTrussSteelDark)
            addOctagonalPillar(out, mx, mz, mh - 0.55f, mh - 0.32f, 1.38f, MatLedWhite, MatLedWarm)
        }

        // I. 2-STOREY GIPL TOLL CONTROL & ADMIN BUILDING (East strictly outside bypass lane: X = tx + 48.0..62.0)
        val ax0 = tx + 48.0f
        val ax1 = tx + 62.0f
        val az0 = tz - 16.5f
        val az1 = tz + 14.5f
        addAxisBox(out, ax0, 0.06f, az0, ax1, 8.20f, az1, MatAcpWhite, MatAcpSilver, MatAcpWhite)
        addAxisBox(out, ax0 - 0.4f, 8.20f, az0 - 0.4f, ax1 + 0.4f, 8.95f, az1 + 0.4f, MatPaliyekkaraTerracotta, MatPaliyekkaraTerracottaDark, MatPaliyekkaraTerracotta)
        out.add(QuadFace3D(Vec3(ax0 - 0.06f, 0.35f, az0), Vec3(ax0 - 0.06f, 0.35f, az1), Vec3(ax0 - 0.06f, 8.35f, az1), Vec3(ax0 - 0.06f, 8.35f, az0), MatAcpWhite, null, -0.06f, signboard = adminFacadeTex))
        out.add(QuadFace3D(Vec3(ax1, 0.35f, az1 + 0.06f), Vec3(ax0, 0.35f, az1 + 0.06f), Vec3(ax0, 8.35f, az1 + 0.06f), Vec3(ax1, 8.35f, az1 + 0.06f), MatAcpWhite, null, -0.06f, signboard = adminFacadeTex))
        addAxisBox(out, ax0 - 3.2f, 3.85f, tz - 4.2f, ax0, 4.30f, tz + 2.2f, MatPaliyekkaraTerracotta, MatAcpWhite, MatPaliyekkaraTerracotta)
        addOctagonalPillar(out, ax0 - 2.8f, tz - 3.6f, 0.06f, 3.85f, 0.24f, MatAcpWhite, MatAcpSilver)
        addOctagonalPillar(out, ax0 - 2.8f, tz + 1.6f, 0.06f, 3.85f, 0.24f, MatAcpWhite, MatAcpSilver)
        val towerX = (ax0 + ax1) * 0.5f
        val towerZ = tz - 6.0f
        addTaperedOctagonalPillar(out, towerX, towerZ, 8.95f, 18.2f, 0.65f, 0.14f, MatHazardRedWhite(0), MatHazardRedWhite(1))
        addAxisBox(out, towerX - 0.85f, 15.8f, towerZ - 0.18f, towerX + 0.85f, 16.4f, towerZ + 0.18f, MatAcpWhite, MatAcpSilver, MatAcpWhite)
    }

    /**
     * Four-sided flared trapezoidal Kerala hipped terracotta roof tier (used for the central
     * two-tier pagoda pavilion lower/upper roofs and flanking pavilion skirt roofs).
     */
    private fun addFourSidedPyramidalTerracottaRoof(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        botHW: Float,
        botHZ: Float,
        botY: Float,
        topHW: Float,
        topHZ: Float,
        topY: Float,
        roofTex: SignboardQuadSpec
    ) {
        // North sloped face (+Z)
        out.add(
            QuadFace3D(
                Vec3(cx + botHW, botY, cz + botHZ),
                Vec3(cx - botHW, botY, cz + botHZ),
                Vec3(cx - topHW, topY, cz + topHZ),
                Vec3(cx + topHW, topY, cz + topHZ),
                MatPaliyekkaraTerracotta, null, -0.05f, signboard = roofTex
            )
        )
        // South sloped face (-Z)
        out.add(
            QuadFace3D(
                Vec3(cx - botHW, botY, cz - botHZ),
                Vec3(cx + botHW, botY, cz - botHZ),
                Vec3(cx + topHW, topY, cz - topHZ),
                Vec3(cx - topHW, topY, cz - topHZ),
                MatPaliyekkaraTerracotta, null, -0.05f, signboard = roofTex
            )
        )
        // West sloped hip face (-X)
        out.add(
            QuadFace3D(
                Vec3(cx - botHW, botY, cz - botHZ),
                Vec3(cx - botHW, botY, cz + botHZ),
                Vec3(cx - topHW, topY, cz + topHZ),
                Vec3(cx - topHW, topY, cz - topHZ),
                MatPaliyekkaraTerracottaDark, null, -0.05f, signboard = roofTex
            )
        )
        // East sloped hip face (+X)
        out.add(
            QuadFace3D(
                Vec3(cx + botHW, botY, cz + botHZ),
                Vec3(cx + botHW, botY, cz - botHZ),
                Vec3(cx + topHW, topY, cz - topHZ),
                Vec3(cx + topHW, topY, cz + topHZ),
                MatPaliyekkaraTerracottaDark, null, -0.05f, signboard = roofTex
            )
        )
    }

    private fun MatHazardRedWhite(idx: Int): Color = if (idx % 2 == 0) MatLedRed else MatAcpWhite

    // =========================================================================
    // 2. REAL-WORLD OLLUR RAILWAY OVERBRIDGE (ROB) OVER 25kV SOUTHERN RAILWAY
    //    - Steel-Concrete Composite Plate-Girder Main Span with vertical steel web stiffeners
    //    - Sloped Trapezoidal New Jersey Concrete Crash Barriers
    //    - Alpha-Cutout Galvanized Diamond Wire-Mesh 25kV Anti-Contact Safety Screens
    //      (revealing the REAL 3D Google Drive Indian Railways tracks & 2 live 8-unit trains!)
    //    - Authentic Southern Railway 25kV AC Double-Track Lattice OHE Portal Masts
    // =========================================================================
    private fun buildOllurRailwayOverbridge3D(
        roads: List<ThrissurOsmRoad>,
        out: MutableList<QuadFace3D>
    ) {
        val nhRoad = roads.firstOrNull { it.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" } ?: return
        val frames = nhRoad.frames.filter { it.pos.z in (OLLUR_ROB_RAMP_SOUTH_Z - 4f)..(OLLUR_ROB_RAMP_NORTH_Z + 4f) }
        if (frames.size < 6) return

        val robMeshTex = Zone3ArchitecturalTextures.createRobSafetyMeshSpec()

        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            val hw0 = f0.halfWidthM
            val hw1 = f1.halfWidthM
            val y0 = f0.pos.y
            val y1 = f1.pos.y
            val midZ = (f0.pos.z + f1.pos.z) * 0.5f
            val inRailSpan = midZ in (OLLUR_ROB_DECK_SOUTH_Z - 6f)..(OLLUR_ROB_DECK_NORTH_Z + 6f)
            val inMainSteelSpan = midZ in -1466f..-1394f
            val girderDepth = 1.48f

            for (side in floatArrayOf(-1f, 1f)) {
                val p0Out = Vec3(f0.pos.x + f0.right.x * side * (hw0 + 0.62f), y0 + 0.02f, f0.pos.z + f0.right.z * side * (hw0 + 0.62f))
                val p1Out = Vec3(f1.pos.x + f1.right.x * side * (hw1 + 0.62f), y1 + 0.02f, f1.pos.z + f1.right.z * side * (hw1 + 0.62f))
                val botY0 = if (inRailSpan) (y0 - girderDepth).coerceAtLeast(0.05f) else 0.04f
                val botY1 = if (inRailSpan) (y1 - girderDepth).coerceAtLeast(0.05f) else 0.04f

                // Outer Steel Plate-Girder Web (over tracks) or Reinforced Concrete Retaining Wall (ramps)
                val fasciaCol = when {
                    inMainSteelSpan -> MatPlateGirderWeb
                    inRailSpan -> MatConcreteDeck
                    else -> MatConcretePier
                }
                out.add(
                    QuadFace3D(
                        Vec3(p0Out.x, botY0, p0Out.z),
                        Vec3(p1Out.x, botY1, p1Out.z),
                        p1Out, p0Out,
                        fasciaCol, null, 0f
                    )
                )

                // Steel Plate-Girder Top/Bottom Flanges & Vertical Stiffener Ribs along main railway span!
                if (inMainSteelSpan) {
                    val fEdge0 = Vec3(f0.pos.x + f0.right.x * side * (hw0 + 0.78f), y0, f0.pos.z + f0.right.z * side * (hw0 + 0.78f))
                    val fEdge1 = Vec3(f1.pos.x + f1.right.x * side * (hw1 + 0.78f), y1, f1.pos.z + f1.right.z * side * (hw1 + 0.78f))
                    // Upper & lower steel flange bands
                    out.add(QuadFace3D(Vec3(fEdge0.x, y0 - 0.22f, fEdge0.z), Vec3(fEdge1.x, y1 - 0.22f, fEdge1.z), Vec3(fEdge1.x, y1 + 0.06f, fEdge1.z), Vec3(fEdge0.x, y0 + 0.06f, fEdge0.z), MatPlateGirderFlange, null, 0f))
                    out.add(QuadFace3D(Vec3(fEdge0.x, botY0 - 0.08f, fEdge0.z), Vec3(fEdge1.x, botY1 - 0.08f, fEdge1.z), Vec3(fEdge1.x, botY1 + 0.18f, fEdge1.z), Vec3(fEdge0.x, botY0 + 0.18f, fEdge0.z), MatPlateGirderFlange, null, 0f))
                    // Vertical steel plate-girder web stiffener rib at frame i
                    addAxisBox(
                        out,
                        p0Out.x - 0.12f, botY0, p0Out.z - 0.10f,
                        p0Out.x + 0.12f, y0 + 0.04f, p0Out.z + 0.10f,
                        MatPlateGirderFlange, MatPlateGirderFlange, MatPlateGirderFlange
                    )
                }

                // Sloped Trapezoidal New Jersey Concrete Crash Parapet Wall (strictly OUTSIDE road halfWidth!)
                val paraInBot = hw0 + 0.14f
                val paraInTop = hw0 + 0.34f
                val paraOut = hw0 + 0.68f
                val q0InB = Vec3(f0.pos.x + f0.right.x * side * paraInBot, y0, f0.pos.z + f0.right.z * side * paraInBot)
                val q1InB = Vec3(f1.pos.x + f1.right.x * side * paraInBot, y1, f1.pos.z + f1.right.z * side * paraInBot)
                val q0InT = Vec3(f0.pos.x + f0.right.x * side * paraInTop, y0 + 1.04f, f0.pos.z + f0.right.z * side * paraInTop)
                val q1InT = Vec3(f1.pos.x + f1.right.x * side * paraInTop, y1 + 1.04f, f1.pos.z + f1.right.z * side * paraInTop)
                val q0OutB = Vec3(f0.pos.x + f0.right.x * side * paraOut, y0, f0.pos.z + f0.right.z * side * paraOut)
                val q1OutB = Vec3(f1.pos.x + f1.right.x * side * paraOut, y1, f1.pos.z + f1.right.z * side * paraOut)
                val q0OutT = Vec3(f0.pos.x + f0.right.x * side * paraOut, y0 + 1.04f, f0.pos.z + f0.right.z * side * paraOut)
                val q1OutT = Vec3(f1.pos.x + f1.right.x * side * paraOut, y1 + 1.04f, f1.pos.z + f1.right.z * side * paraOut)
                out.add(QuadFace3D(q0InB, q1InB, q1InT, q0InT, MatConcreteDeck, null, 0f))
                out.add(QuadFace3D(q0InT, q1InT, q1OutT, q0OutT, MatAcpWhite, null, 0f))
                out.add(QuadFace3D(q0OutB, q1OutB, q1OutT, q0OutT, MatConcretePier, null, 0f))

                // ALPHA-CUTOUT Galvanized Diamond Wire-Mesh 25kV Anti-Contact Safety Screen over Railway Span!
                if (inRailSpan) {
                    val meshH = 2.45f
                    val mX0 = f0.pos.x + f0.right.x * side * (hw0 + 0.50f)
                    val mZ0 = f0.pos.z + f0.right.z * side * (hw0 + 0.50f)
                    val mX1 = f1.pos.x + f1.right.x * side * (hw1 + 0.50f)
                    val mZ1 = f1.pos.z + f1.right.z * side * (hw1 + 0.50f)
                    out.add(
                        QuadFace3D(
                            Vec3(mX0, y0 + 1.04f, mZ0),
                            Vec3(mX1, y1 + 1.04f, mZ1),
                            Vec3(mX1, y1 + 1.04f + meshH, mZ1),
                            Vec3(mX0, y0 + 1.04f + meshH, mZ0),
                            MatGalvanized, null, -0.04f, signboard = robMeshTex
                        )
                    )
                    // Heavy galvanized vertical H-post anchoring safety screen
                    if (i % 2 == 0) {
                        addAxisBox(
                            out,
                            mX0 - 0.07f, y0 + 1.04f, mZ0 - 0.07f,
                            mX0 + 0.07f, y0 + 1.04f + meshH + 0.06f, mZ0 + 0.07f,
                            MatGalvanized, MatGalvanizedDark, MatGalvanized
                        )
                    }
                }
            }

            // Visible 4-Girder Composite Underside Superstructure over railway tracks
            if (inRailSpan) {
                // Concrete deck soffit ceiling
                out.add(
                    QuadFace3D(
                        Vec3(f0.pos.x - f0.right.x * (hw0 + 0.62f), y0 - 0.35f, f0.pos.z - f0.right.z * (hw0 + 0.62f)),
                        Vec3(f0.pos.x + f0.right.x * (hw0 + 0.62f), y0 - 0.35f, f0.pos.z + f0.right.z * (hw0 + 0.62f)),
                        Vec3(f1.pos.x + f1.right.x * (hw1 + 0.62f), y1 - 0.35f, f1.pos.z + f1.right.z * (hw1 + 0.62f)),
                        Vec3(f1.pos.x - f1.right.x * (hw1 + 0.62f), y1 - 0.35f, f1.pos.z - f1.right.z * (hw1 + 0.62f)),
                        MatConcretePierDark, null, 0f
                    )
                )
                // 4 longitudinal underside steel/PSC I-girders cleanly visible from railway track level
                val gCol = if (inMainSteelSpan) MatPlateGirderWeb else MatConcretePier
                for (gOff in floatArrayOf(-7.2f, -2.4f, 2.4f, 7.2f)) {
                    val g0 = Vec3(f0.pos.x + f0.right.x * gOff, y0, f0.pos.z + f0.right.z * gOff)
                    val g1 = Vec3(f1.pos.x + f1.right.x * gOff, y1, f1.pos.z + f1.right.z * gOff)
                    out.add(QuadFace3D(Vec3(g0.x, y0 - girderDepth, g0.z), Vec3(g1.x, y1 - girderDepth, g1.z), Vec3(g1.x, y1 - 0.35f, g1.z), Vec3(g0.x, y0 - 0.35f, g0.z), gCol, null, 0f))
                }
            }
        }

        // Tapered Concrete Hammerhead Piers flanking the twin electrified tracks (Z = -1392, -1412, -1448, -1468)
        for (pz in floatArrayOf(-1392f, -1412f, -1448f, -1468f)) {
            val px = ollurRobCenterXAt(pz)
            for (lat in floatArrayOf(-7.8f, 0.0f, 7.8f)) {
                addTaperedOctagonalPillar(out, px + lat, pz, 0.04f, OLLUR_ROB_DECK_Y - 2.15f, 1.05f, 0.88f, MatConcretePier, MatConcretePierDark)
            }
            addTrapezoidalPierCap(out, px, pz, 11.4f, 12.4f, OLLUR_ROB_DECK_Y - 2.20f, OLLUR_ROB_DECK_Y - 1.48f, 1.35f)
            // Neoprene elastomeric bearing pads under each longitudinal girder
            for (gOff in floatArrayOf(-7.2f, -2.4f, 2.4f, 7.2f)) {
                addAxisBox(out, px + gOff - 0.42f, OLLUR_ROB_DECK_Y - 1.48f, pz - 0.35f, px + gOff + 0.42f, OLLUR_ROB_DECK_Y - 1.32f, pz + 0.35f, MatNeopreneBearing, MatNeopreneBearing, MatNeopreneBearing)
            }
        }

        // Overhead NHAI ROB Signboard Gantries at North & South approaches + 25kV Danger Warning Plaque
        val robSign = createBannerSignSpec(
            cacheKey = "ZONE3_OLLUR_ROB_SIGN_V2",
            bgColorInt = 0xFF057A55.toInt(),
            borderColorInt = 0xFFFFFFFF.toInt(),
            titleText = "ഒല്ലൂർ റെയിൽവേ മേൽപ്പാലം • OLLUR ROB",
            subText = "SOUTHERN RAILWAY 25kV MAINLINE • NH 544 • PALIYEKKARA TOLL 1.8 KM ↑",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 960,
            bmpHeight = 160
        )
        addOverheadHighwayGantry(out, ollurRobCenterXAt(-1324f), -1324f, 0.06f, 13.8f, robSign, facingNorth = true)
        addOverheadHighwayGantry(out, ollurRobCenterXAt(-1536f), -1536f, 0.06f, 13.8f, robSign, facingNorth = false)

        // B. AUTHENTIC SOUTHERN RAILWAY 25kV AC DOUBLE-TRACK LATTICE OHE PORTAL MASTS & CATENARY WIRES
        //    (Real 3D textured broad-gauge tracks & 2 live 8-unit WAG-9 trains are rendered underneath
        //     directly by ShoranurRailwaySystem.kt — zero boxy fake rails or box trains!)
        val railZTracks = floatArrayOf(OLLUR_ROB_CENTER_Z - 2.65f, OLLUR_ROB_CENTER_Z + 2.65f)
        for (rz in railZTracks) {
            // Contact wire (Y = 5.30m) + Upper Catenary Messenger Wire (Y = 5.95m, safely below 6.0m soffit!)
            addAxisBox(out, 770f, 5.26f, rz - 0.03f, 1150f, 5.31f, rz + 0.03f, MatCopperWire, MatCopperWire, MatCopperWire)
            addAxisBox(out, 770f, 5.86f, rz - 0.025f, 1150f, 5.91f, rz + 0.025f, MatGalvanizedDark, MatGalvanizedDark, MatGalvanizedDark)
        }
        // Double-Track Galvanized 25kV OHE Lattice Portal Frames every 40m (strictly outside ROB piers!)
        for (mx in floatArrayOf(790f, 830f, 870f, 910f, 998f, 1038f, 1078f, 1118f)) {
            val zN = OLLUR_ROB_CENTER_Z + 6.45f
            val zS = OLLUR_ROB_CENTER_Z - 6.45f
            // Twin galvanized H-column portal masts with concrete plinth bases
            for (mz in floatArrayOf(zS, zN)) {
                addAxisBox(out, mx - 0.30f, 0.04f, mz - 0.30f, mx + 0.30f, 0.65f, mz + 0.30f, MatConcretePier, MatConcretePierDark, MatConcretePier)
                addAxisBox(out, mx - 0.14f, 0.65f, mz - 0.14f, mx + 0.14f, 7.15f, mz + 0.14f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            }
            // Overhead Lattice Portal Cross-Boom spanning across both broad-gauge tracks (Y = 6.55..7.05m)
            addAxisBox(out, mx - 0.10f, 6.55f, zS, mx + 0.10f, 6.68f, zN, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            addAxisBox(out, mx - 0.10f, 6.95f, zS, mx + 0.10f, 7.08f, zN, MatGalvanized, MatGalvanizedDark, MatGalvanized)
            // Diagonal lattice portal struts & brown ceramic 25kV disc insulators above each track
            for (k in 0 until 6) {
                val za = zS + k * (12.9f / 6f)
                val zb = za + (12.9f / 6f)
                addDiagonalStrut(out, Vec3(mx, 6.60f, za), Vec3(mx, 7.00f, zb), 0.045f, MatGalvanizedDark, MatGalvanized)
            }
            for (rz in railZTracks) {
                // Vertical drop-tube + brown ceramic 25kV insulator + contact steady-arm
                addAxisBox(out, mx - 0.05f, 5.90f, rz - 0.05f, mx + 0.05f, 6.60f, rz + 0.05f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
                addOctagonalPillar(out, mx, rz, 6.12f, 6.46f, 0.14f, MatInsulatorBrown, MatInsulatorBrown)
                addAxisBox(out, mx - 0.03f, 5.30f, rz - 0.03f, mx + 0.03f, 5.90f, rz + 0.03f, MatCopperWire, MatCopperWire, MatCopperWire)
            }
        }
    }

    // =========================================================================
    // 3. KURUMALI RIVER BRIDGE (MAJOR WATER BRIDGE 2 — PUTHUKKAD, 140m SPAN)
    // =========================================================================
    private fun buildKurumaliRiverBridge3D(out: MutableList<QuadFace3D>) {
        buildMajorRiverBridgeStructure(
            out = out,
            centerX = KURUMALI_CENTER_X,
            rampNorthZ = KURUMALI_RAMP_NORTH_Z,
            deckNorthZ = KURUMALI_DECK_NORTH_Z,
            deckSouthZ = KURUMALI_DECK_SOUTH_Z,
            rampSouthZ = KURUMALI_RAMP_SOUTH_Z,
            deckY = KURUMALI_DECK_Y,
            halfRoadW = 10.0f,
            pierPairs = 5,
            pierRadiusZ = 1.45f,
            signKey = "ZONE3_KURUMALI_RIVER_PWD_V2",
            malayalamTitle = "കുറുമാലിപ്പുഴ • KURUMALI RIVER",
            englishSub = "PUTHUKKAD BRIDGE (140 M) • NH 544 • KERALA PWD"
        )
        // Sculpted natural river sandbars + boulders along Kurumali banks
        addOrganicRiverSandbar(out, sx = 1288f, sz = -2228f, rx = 18f, rz = 7.5f, crestY = 0.54f)
        addOrganicRiverSandbar(out, sx = 1445f, sz = -2278f, rx = 21f, rz = 8.5f, crestY = 0.58f)
        // Moored Kerala wooden Vallam canoes
        addMooredVallamCanoe(out, cx = 1322f, cz = -2194f, yawRad = 0.25f)
        addMooredVallamCanoe(out, cx = 1418f, cz = -2306f, yawRad = -0.35f)
    }

    // =========================================================================
    // 4. CHALAKUDY RIVER BRIDGE (MAJOR WATER BRIDGE 1 — 280m CHALAKUDY PUZHAPALAM!)
    //    Lat 10.2970° N, Lon 76.3330° E -> (X = 2596.0, Z = -4900..-5180, Y = 8.20m)
    // =========================================================================
    private fun buildChalakudyRiverBridge3D(out: MutableList<QuadFace3D>) {
        buildMajorRiverBridgeStructure(
            out = out,
            centerX = CHALAKUDY_CENTER_X,
            rampNorthZ = CHALAKUDY_RAMP_NORTH_Z,
            deckNorthZ = CHALAKUDY_DECK_NORTH_Z,
            deckSouthZ = CHALAKUDY_DECK_SOUTH_Z,
            rampSouthZ = CHALAKUDY_RAMP_SOUTH_Z,
            deckY = CHALAKUDY_DECK_Y,
            halfRoadW = 10.0f,
            pierPairs = 8, // 8 pairs of hydrodynamic cutwater concrete piers across the 280m river!
            pierRadiusZ = 1.65f,
            signKey = "ZONE3_CHALAKUDY_RIVER_PWD_V2",
            malayalamTitle = "ചാലക്കുടിപ്പുഴ • CHALAKUDY RIVER",
            englishSub = "MAJOR BRIDGE (280 M) • NH 544 • KERALA PWD"
        )

        // Organic Sculpted Multi-Ring Elliptical Sandbars & Faceted Boulders inside Chalakudy River!
        addOrganicRiverSandbar(out, sx = 2516f, sz = -4976f, rx = 25f, rz = 10.5f, crestY = 0.68f)
        addOrganicRiverSandbar(out, sx = 2682f, sz = -5096f, rx = 29f, rz = 11.5f, crestY = 0.72f)
        addOrganicRiverSandbar(out, sx = 2462f, sz = -5122f, rx = 21f, rz = 8.5f, crestY = 0.62f)
        addOrganicRiverSandbar(out, sx = 2734f, sz = -4964f, rx = 23f, rz = 9.0f, crestY = 0.64f)

        // Traditional Kerala Country Canoes (Vallam) moored to bamboo poles
        addMooredVallamCanoe(out, cx = 2545f, cz = -4916f, yawRad = 0.18f)
        addMooredVallamCanoe(out, cx = 2528f, cz = -4922f, yawRad = -0.14f)
        addMooredVallamCanoe(out, cx = 2652f, cz = -5164f, yawRad = 0.30f)
        addMooredVallamCanoe(out, cx = 2684f, cz = -5158f, yawRad = -0.22f)
    }

    /**
     * Real-world Kerala NH 544 / PWD Multi-Girder Major River Bridge:
     * - 6 visible parallel Prestressed Concrete (PSC) I-Girders + transverse cross-diaphragms
     * - Hydrodynamic cutwater elliptical twin piers with mossy monsoon HFL waterline weathering
     * - Flared trapezoidal hammerhead pier caps + black neoprene elastomeric bearing pads
     * - Tapered concrete riverbank abutments & wing-walls
     * - Raised pedestrian walkways + ALPHA-CUTOUT open slotted concrete balustrade & royal-blue handrails
     * - Sloped New Jersey concrete central divider + sweeping cobra-head twin-arm LED streetlights
     */
    private fun buildMajorRiverBridgeStructure(
        out: MutableList<QuadFace3D>,
        centerX: Float,
        rampNorthZ: Float,
        deckNorthZ: Float,
        deckSouthZ: Float,
        rampSouthZ: Float,
        deckY: Float,
        halfRoadW: Float,
        pierPairs: Int,
        pierRadiusZ: Float,
        signKey: String,
        malayalamTitle: String,
        englishSub: String
    ) {
        val walkwayW = 1.60f
        val outerEdge = halfRoadW + walkwayW // 11.60m each side
        val segStep = 8.0f // Matches dense 8m road spline vertices on cosine ramps for 0.000m vertical gap!
        val railingTex = Zone3ArchitecturalTextures.createBridgeSlottedRailingSpec()

        var z = rampNorthZ
        var segIdx = 0
        while (z > rampSouthZ + 0.5f) {
            val zNext = max(rampSouthZ, z - segStep)
            val y0 = computeZone3BridgeSurfaceY(centerX, z)
            val y1 = computeZone3BridgeSurfaceY(centerX, zNext)
            val midZ = (z + zNext) * 0.5f
            val onWaterSpan = midZ in deckSouthZ..deckNorthZ
            val girderDepth = 1.65f

            for (side in floatArrayOf(-1f, 1f)) {
                val xIn = centerX + side * (halfRoadW + 0.04f)
                val xOut = centerX + side * outerEdge
                val railX = centerX + side * (outerEdge - 0.14f)

                // 1. Raised Pedestrian Walkway Deck + Yellow-Black Safety Kerb Riser
                out.add(
                    QuadFace3D(
                        Vec3(xIn, y0 + 0.24f, z),
                        Vec3(xOut, y0 + 0.24f, z),
                        Vec3(xOut, y1 + 0.24f, zNext),
                        Vec3(xIn, y1 + 0.24f, zNext),
                        MatConcreteDeck, null, 0f
                    )
                )
                val kerbCol = if (segIdx % 2 == 0) MatHazardYellow else MatHazardBlack
                out.add(
                    QuadFace3D(
                        Vec3(xIn, y0 - 0.02f, z),
                        Vec3(xIn, y1 - 0.02f, zNext),
                        Vec3(xIn, y1 + 0.24f, zNext),
                        Vec3(xIn, y0 + 0.24f, z),
                        kerbCol, null, 0f
                    )
                )

                // 2. ALPHA-CUTOUT Open Slotted Kerala PWD Balustrade & Royal-Blue Steel Handrail!
                //    (Lets drivers see the sparkling river, sandbars & canoes cleanly through the railing!)
                val railH = 1.32f
                out.add(
                    QuadFace3D(
                        Vec3(railX, y0 + 0.24f, z),
                        Vec3(railX, y1 + 0.24f, zNext),
                        Vec3(railX, y1 + 0.24f + railH, zNext),
                        Vec3(railX, y0 + 0.24f + railH, z),
                        MatConcreteDeck, null, -0.04f, signboard = railingTex
                    )
                )

                // 3. Cantilevered Deck Fascia Slab Edge (top 0.42m overhang) + Ramp Retaining Wall
                val slabBot0 = y0 - 0.36f
                val slabBot1 = y1 - 0.36f
                if (onWaterSpan) {
                    // Crisp overhanging bridge deck fascia edge with drip-lip shadow underneath
                    out.add(
                        QuadFace3D(
                            Vec3(xOut, slabBot0, z),
                            Vec3(xOut, slabBot1, zNext),
                            Vec3(xOut, y1 + 0.24f, zNext),
                            Vec3(xOut, y0 + 0.24f, z),
                            MatConcreteDeck, null, 0f
                        )
                    )
                    // Recessed outer PSC I-girder web face (set 0.75m inward under cantilevered deck!)
                    val xGirder = centerX + side * (outerEdge - 0.78f)
                    out.add(
                        QuadFace3D(
                            Vec3(xGirder, y0 - girderDepth, z),
                            Vec3(xGirder, y1 - girderDepth, zNext),
                            Vec3(xGirder, slabBot1, zNext),
                            Vec3(xGirder, slabBot0, z),
                            MatConcretePier, null, 0f
                        )
                    )
                } else {
                    // Solid tapered approach embankment retaining wall on North & South ramps
                    out.add(
                        QuadFace3D(
                            Vec3(xOut, 0.04f, z),
                            Vec3(xOut, 0.04f, zNext),
                            Vec3(xOut, y1 + 0.24f, zNext),
                            Vec3(xOut, y0 + 0.24f, z),
                            MatConcretePier, null, 0f
                        )
                    )
                }
            }

            // 4. Dead-Centered Sloped Trapezoidal Central Concrete New Jersey Divider Barrier
            //    (Sits squarely inside the road's ±0.62m concrete median & ±0.82m yellow inner lines,
            //     with smooth tapered bullnose ramps at North & South bridge portals!)
            val isFirstSeg = segIdx == 0
            val isLastSeg = zNext <= rampSouthZ + 0.5f
            val h0 = if (isFirstSeg) 0.16f else 0.88f
            val h1 = if (isLastSeg) 0.16f else 0.88f
            val toeCol = if (segIdx % 2 == 0) MatHazardYellow else MatHazardBlack
            addSlopedMedianSegment(
                out = out,
                cx = centerX,
                z0 = z,
                z1 = zNext,
                y0 = y0 + 0.01f,
                y1 = y1 + 0.01f,
                hwBot = 0.46f,
                hwTop = 0.18f,
                height0 = h0,
                height1 = h1,
                sideCol = MatConcreteDeck,
                topCol = MatAcpWhite,
                toeCol = toeCol
            )

            // 5. Visible Multi-Girder (6-Beam) Prestressed Concrete Superstructure & Curved Cobra-Head Streetlights!
            if (onWaterSpan) {
                // Underside deck slab soffit (Y - 0.36m)
                out.add(
                    QuadFace3D(
                        Vec3(centerX - outerEdge, y0 - 0.36f, z),
                        Vec3(centerX + outerEdge, y0 - 0.36f, z),
                        Vec3(centerX + outerEdge, y1 - 0.36f, zNext),
                        Vec3(centerX - outerEdge, y1 - 0.36f, zNext),
                        MatConcreteDeckShadow, null, 0f
                    )
                )
                // 4 interior visible PSC I-Girder ribs (plus the 2 outer girders = 6 parallel I-girders!)
                for (gxOff in floatArrayOf(-6.8f, -2.2f, 2.2f, 6.8f)) {
                    val gx = centerX + gxOff
                    // Bottom flange bulb & vertical web sides
                    out.add(QuadFace3D(Vec3(gx - 0.24f, y0 - girderDepth, z), Vec3(gx + 0.24f, y0 - girderDepth, z), Vec3(gx + 0.24f, y1 - girderDepth, zNext), Vec3(gx - 0.24f, y1 - girderDepth, zNext), MatConcretePierDark, null, 0f))
                    out.add(QuadFace3D(Vec3(gx - 0.16f, y0 - girderDepth, z), Vec3(gx - 0.16f, y1 - girderDepth, zNext), Vec3(gx - 0.16f, y1 - 0.36f, zNext), Vec3(gx - 0.16f, y0 - 0.36f, z), MatConcretePier, null, 0f))
                    out.add(QuadFace3D(Vec3(gx + 0.16f, y0 - girderDepth, z), Vec3(gx + 0.16f, y1 - girderDepth, zNext), Vec3(gx + 0.16f, y1 - 0.36f, zNext), Vec3(gx + 0.16f, y0 - 0.36f, z), MatConcretePierDark, null, 0f))
                }

                // Graceful Curved Twin-Arm Cobra-Head Bridge Streetlights every ~32m on central median
                if (segIdx % 4 == 0) {
                    addCurvedTwinArmStreetlight(out, centerX, y0 + 0.89f, z, 1f, 0f, poleHeight = 6.8f, armReach = 2.45f)
                }
            }
            z = zNext
            segIdx++
        }

        // 6. Tapered Reinforced Concrete Riverbank Abutments & Wing-Walls at North & South Bank Portals!
        for (abutZ in arrayOf(Pair(deckNorthZ, 1f), Pair(deckSouthZ, -1f))) {
            val az = abutZ.first
            val dir = abutZ.second
            // Massive riverbank abutment breast-wall supporting bridge deck ends
            addAxisBox(out, centerX - outerEdge - 0.4f, 0.0f, az - 1.6f, centerX + outerEdge + 0.4f, deckY - 1.50f, az + 1.6f, MatConcretePier, MatConcretePierDark, MatConcretePier)
            // Monsoon mossy base apron at waterline
            addAxisBox(out, centerX - outerEdge - 0.9f, 0.0f, az - 2.1f, centerX + outerEdge + 0.9f, 1.45f, az + 2.1f, MatConcreteMossWet, MatConcreteCaisson, MatConcreteMossWet)
            // Flared trapezoidal concrete wing-walls retaining the approach embankments
            for (side in floatArrayOf(-1f, 1f)) {
                val wx0 = centerX + side * (outerEdge + 0.2f)
                val wx1 = centerX + side * (outerEdge + 4.8f)
                val wz0 = az
                val wz1 = az + dir * 10.5f
                out.add(QuadFace3D(Vec3(wx0, 0.04f, wz0), Vec3(wx1, 0.04f, wz1), Vec3(wx1, 1.80f, wz1), Vec3(wx0, deckY + 0.20f, wz0), MatConcretePier, null, 0f))
                out.add(QuadFace3D(Vec3(wx1, 0.04f, wz1), Vec3(wx0, 0.04f, wz0), Vec3(wx0, deckY + 0.20f, wz0), Vec3(wx1, 1.80f, wz1), MatConcretePierDark, null, 0f))
            }
        }

        // 7. Finger-Type Steel Expansion Joints every 35m across the main bridge span!
        val spanLen = deckNorthZ - deckSouthZ
        val jointCount = (spanLen / 35.0f).toInt().coerceAtLeast(2)
        for (j in 0..jointCount) {
            val jz = deckNorthZ - j * (spanLen / jointCount)
            out.add(
                QuadFace3D(
                    Vec3(centerX - halfRoadW, deckY + 0.025f, jz - 0.24f),
                    Vec3(centerX + halfRoadW, deckY + 0.025f, jz - 0.24f),
                    Vec3(centerX + halfRoadW, deckY + 0.025f, jz + 0.24f),
                    Vec3(centerX - halfRoadW, deckY + 0.025f, jz + 0.24f),
                    MatTrussSteelDark, null, -0.05f
                )
            )
        }

        // 8. Hydrodynamic Cutwater Elliptical Twin Piers, Monsoon Waterline Weathering & Hammerhead Pier Caps!
        val pierSpacing = spanLen / (pierPairs + 1)
        for (p in 1..pierPairs) {
            val pz = deckNorthZ - p * pierSpacing
            for (pierOffX in floatArrayOf(-5.6f, 5.6f)) {
                val px = centerX + pierOffX
                // Wide well-foundation caisson cap emerging from riverbed (Y = 0.0..0.95m)
                addCutwaterRiverPier(out, px, pz, 0.0f, 0.95f, rx = pierRadiusZ * 1.95f, rz = pierRadiusZ * 1.28f, MatConcreteCaisson, MatConcreteCaisson)
                // Dark green-brown monsoon HFL algae/silt waterline weathering band (Y = 0.95..2.10m)
                addCutwaterRiverPier(out, px, pz, 0.95f, 2.10f, rx = pierRadiusZ * 1.65f, rz = pierRadiusZ * 0.98f, MatConcreteMossWet, MatConcretePierDark)
                // Sun-bleached weathered concrete upper cutwater pier shaft (Y = 2.10..deckY - 2.55m)
                addCutwaterRiverPier(out, px, pz, 2.10f, deckY - 2.55f, rx = pierRadiusZ * 1.55f, rz = pierRadiusZ * 0.92f, MatConcretePier, MatConcretePierDark)
            }
            // Flared Trapezoidal Concrete Hammerhead Pier Cap Beam spanning cleanly under all 6 PSC I-Girders!
            addTrapezoidalPierCap(
                out = out,
                cx = centerX,
                cz = pz,
                halfBotW = outerEdge - 2.4f,
                halfTopW = outerEdge - 0.35f,
                yBot = deckY - 2.60f,
                yTop = deckY - 1.80f,
                halfThickZ = pierRadiusZ * 0.85f
            )
            // Neoprene elastomeric bearing pads & transverse concrete cross-diaphragm above pier cap
            for (gxOff in floatArrayOf(-10.8f, -6.8f, -2.2f, 2.2f, 6.8f, 10.8f)) {
                addAxisBox(out, centerX + gxOff - 0.36f, deckY - 1.80f, pz - 0.32f, centerX + gxOff + 0.36f, deckY - 1.64f, pz + 0.32f, MatNeopreneBearing, MatNeopreneBearing, MatNeopreneBearing)
            }
            addAxisBox(out, centerX - outerEdge + 0.9f, deckY - 1.64f, pz - 0.28f, centerX + outerEdge - 0.9f, deckY - 0.36f, pz + 0.28f, MatConcretePierDark, MatConcretePierDark, MatConcretePierDark)
        }

        // 9. Blue Kerala PWD River Name Signboards at Both North & South Bridge Approaches!
        val riverSign = createBannerSignSpec(
            cacheKey = signKey,
            bgColorInt = 0xFF1D4ED8.toInt(),
            borderColorInt = 0xFFFFFFFF.toInt(),
            titleText = malayalamTitle,
            subText = englishSub,
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 768,
            bmpHeight = 160
        )
        addOverheadHighwayGantry(out, centerX, rampNorthZ + 6f, 0.06f, 12.6f, riverSign, facingNorth = true)
        addOverheadHighwayGantry(out, centerX, rampSouthZ - 6f, 0.06f, 12.6f, riverSign, facingNorth = false)
    }

    /**
     * Sculpted Organic Multi-Ring 12-Sided River Sandbar Mound + Faceted River Boulders (NO boxes!).
     */
    private fun addOrganicRiverSandbar(
        out: MutableList<QuadFace3D>,
        sx: Float,
        sz: Float,
        rx: Float,
        rz: Float,
        crestY: Float
    ) {
        val segs = 12
        val rOuterX = rx
        val rOuterZ = rz
        val rMidX = rx * 0.72f
        val rMidZ = rz * 0.68f
        val rTopX = rx * 0.36f
        val rTopZ = rz * 0.32f

        fun ringPt(k: Int, radX: Float, radZ: Float, y: Float): Vec3 {
            val a = (k.toFloat() / segs) * 2f * PI.toFloat()
            // Subtle organic sinusoidal coastline irregularity
            val wobble = 1.0f + 0.11f * sin(a * 3f + sx * 0.05f) + 0.07f * cos(a * 5f + sz * 0.04f)
            return Vec3(sx + cos(a) * radX * wobble, y, sz + sin(a) * radZ * wobble)
        }

        for (k in 0 until segs) {
            val kNext = (k + 1) % segs
            // Wet sloping outer sandbank skirt (Y = 0.02 -> 0.28m)
            val o0 = ringPt(k, rOuterX, rOuterZ, 0.02f)
            val o1 = ringPt(kNext, rOuterX, rOuterZ, 0.02f)
            val m0 = ringPt(k, rMidX, rMidZ, crestY * 0.56f)
            val m1 = ringPt(kNext, rMidX, rMidZ, crestY * 0.56f)
            out.add(QuadFace3D(o0, o1, m1, m0, MatRiverSandWet, null, 0f))

            // Golden dry sandbar upper slope (Y = mid -> crest)
            val t0 = ringPt(k, rTopX, rTopZ, crestY)
            val t1 = ringPt(kNext, rTopX, rTopZ, crestY)
            out.add(QuadFace3D(m0, m1, t1, t0, MatRiverSandbank, null, 0f))

            // Gentle sunlit crest cap fan
            out.add(QuadFace3D(t0, t1, Vec3(sx, crestY + 0.08f, sz), Vec3(sx, crestY + 0.08f, sz), MatRiverSandCrest, null, 0f))
        }

        // Faceted irregular natural river boulders resting on sandbar
        addFacetedBoulder(out, sx - rx * 0.22f, crestY * 0.72f, sz - rz * 0.14f, 1.45f, 0.72f, 1.15f)
        addFacetedBoulder(out, sx + rx * 0.26f, crestY * 0.65f, sz + rz * 0.18f, 1.15f, 0.56f, 0.92f)
        addFacetedBoulder(out, sx - rx * 0.05f, crestY * 0.80f, sz + rz * 0.25f, 0.85f, 0.44f, 0.75f)
    }

    private fun addFacetedBoulder(
        out: MutableList<QuadFace3D>,
        cx: Float,
        baseY: Float,
        cz: Float,
        rx: Float,
        ry: Float,
        rz: Float
    ) {
        val sides = 6
        val apex = Vec3(cx + rx * 0.12f, baseY + ry, cz - rz * 0.08f)
        for (k in 0 until sides) {
            val a0 = (k.toFloat() / sides) * 2f * PI.toFloat()
            val a1 = ((k + 1).toFloat() / sides) * 2f * PI.toFloat()
            val p0 = Vec3(cx + cos(a0) * rx, baseY, cz + sin(a0) * rz)
            val p1 = Vec3(cx + cos(a1) * rx, baseY, cz + sin(a1) * rz)
            val m0 = Vec3(cx + cos(a0) * rx * 0.72f, baseY + ry * 0.62f, cz + sin(a0) * rz * 0.72f)
            val m1 = Vec3(cx + cos(a1) * rx * 0.72f, baseY + ry * 0.62f, cz + sin(a1) * rz * 0.72f)
            val col = if (k % 2 == 0) MatRiverRock else MatRiverRockDark
            out.add(QuadFace3D(p0, p1, m1, m0, col, null, 0f))
            out.add(QuadFace3D(m0, m1, apex, apex, if (k % 2 == 0) MatConcretePierDark else MatRiverRock, null, 0f))
        }
    }

    private fun addMooredVallamCanoe(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        yawRad: Float
    ) {
        val c = cos(yawRad)
        val s = sin(yawRad)
        fun pt(lx: Float, ly: Float, lz: Float) = Vec3(cx + lx * c - lz * s, ly, cz + lx * s + lz * c)

        val hullSegs = arrayOf(
            Triple(-3.8f, 0.10f, 0.88f),
            Triple(-2.6f, 0.54f, 0.52f),
            Triple(0.0f, 0.72f, 0.44f),
            Triple(2.6f, 0.54f, 0.52f),
            Triple(3.8f, 0.10f, 0.88f)
        )
        for (i in 0 until hullSegs.size - 1) {
            val (x0, w0, h0) = hullSegs[i]
            val (x1, w1, h1) = hullSegs[i + 1]
            out.add(QuadFace3D(pt(x0, 0.14f, -w0 * 0.68f), pt(x1, 0.14f, -w1 * 0.68f), pt(x1, h1, -w1), pt(x0, h0, -w0), MatVallamWood, null, 0f))
            out.add(QuadFace3D(pt(x0, 0.14f, w0 * 0.68f), pt(x1, 0.14f, w1 * 0.68f), pt(x1, h1, w1), pt(x0, h0, w0), MatVallamWood, null, 0f))
            out.add(QuadFace3D(pt(x0, 0.24f, -w0 * 0.62f), pt(x1, 0.24f, -w1 * 0.62f), pt(x1, 0.24f, w1 * 0.62f), pt(x0, 0.24f, w0 * 0.62f), MatVallamWoodLight, null, 0f))
        }
        for (tx in floatArrayOf(-1.45f, 0.0f, 1.45f)) {
            out.add(QuadFace3D(pt(tx - 0.14f, 0.48f, -0.58f), pt(tx + 0.14f, 0.48f, -0.58f), pt(tx + 0.14f, 0.48f, 0.58f), pt(tx - 0.14f, 0.48f, 0.58f), MatVallamWoodLight, null, 0f))
        }
        // Bamboo mooring pole stuck in riverbed beside prow + slack mooring rope!
        val polePos = pt(4.7f, 0f, 0.85f)
        addTaperedOctagonalPillar(out, polePos.x, polePos.z, 0.04f, 2.45f, 0.07f, 0.045f, MatBambooPole, MatTimberBrown)
        val prowPos = pt(3.65f, 0.78f, 0f)
        out.add(
            QuadFace3D(
                prowPos, polePos.copy(y = 1.18f),
                polePos.copy(y = 1.24f), prowPos.copy(y = 0.83f),
                MatRiverSandCrest, null, 0f
            )
        )
    }

    // =========================================================================
    // 5. NH 544 SOUTH LANDSCAPED MEDIAN (SCULPTED ORGANIC BOUGAINVILLEA + COBRA LIGHTS)
    //    & KODAKARA JUNCTION OVERHEAD DIRECTIONAL GANTRY
    // =========================================================================
    private fun buildNh544SouthMedianBougainvilleaAndGantries(
        roads: List<ThrissurOsmRoad>,
        out: MutableList<QuadFace3D>
    ) {
        val targetIds = setOf("ROAD_NH544_MANNUTHY_TO_PALIYEKKARA", "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY")
        val flowerPalette = arrayOf(
            MatBougainvilleaMagenta,
            MatBougainvilleaCrimson,
            MatBougainvilleaPurple,
            MatBougainvilleaLight
        )

        for (road in roads) {
            if (road.id !in targetIds) continue
            val frames = road.frames
            if (frames.size < 20) continue

            var fIdx = 14
            while (fIdx < frames.size - 10) {
                val f = frames[fIdx]
                val z = f.pos.z
                val clearOfSpecials = f.pos.y < 0.18f &&
                    z < -40f &&
                    z !in -960f..-890f &&
                    z !in (OLLUR_ROB_RAMP_SOUTH_Z - 12f)..(OLLUR_ROB_RAMP_NORTH_Z + 12f) &&
                    z !in (TOLL_FLARE_SOUTH_END_Z - 8f)..(TOLL_FLARE_NORTH_START_Z + 8f) &&
                    z !in (KURUMALI_RAMP_SOUTH_Z - 14f)..(KURUMALI_RAMP_NORTH_Z + 14f) &&
                    z !in (CHALAKUDY_RAMP_SOUTH_Z - 14f)..(CHALAKUDY_RAMP_NORTH_Z + 14f) &&
                    z > -5320f
                if (clearOfSpecials) {
                    val fNext = frames[min(frames.size - 1, fIdx + 2)]
                    val medW = 1.15f
                    // Sloped concrete kerb edges + dark planter soil bed along highway median
                    val p0L = Vec3(f.pos.x - f.right.x * medW, 0.06f, f.pos.z - f.right.z * medW)
                    val p0R = Vec3(f.pos.x + f.right.x * medW, 0.06f, f.pos.z + f.right.z * medW)
                    val p1L = Vec3(fNext.pos.x - fNext.right.x * medW, 0.06f, fNext.pos.z - fNext.right.z * medW)
                    val p1R = Vec3(fNext.pos.x + fNext.right.x * medW, 0.06f, fNext.pos.z + fNext.right.z * medW)
                    val kerbCol = if ((fIdx / 3) % 2 == 0) MatAcpWhite else MatHazardBlack
                    out.add(QuadFace3D(p0L, p1L, p1L.copy(y = 0.28f), p0L.copy(y = 0.28f), kerbCol, null, 0f))
                    out.add(QuadFace3D(p0R, p1R, p1R.copy(y = 0.28f), p0R.copy(y = 0.28f), kerbCol, null, 0f))
                    out.add(QuadFace3D(p0L.copy(y = 0.28f), p0R.copy(y = 0.28f), p1R.copy(y = 0.28f), p1L.copy(y = 0.28f), MatMedianSoil, null, 0f))

                    // Sculpted Organic Multi-Faceted Bougainvillea Shrub Crown (NO blocky cubes!)
                    val flowerCol = flowerPalette[(fIdx / 4) % flowerPalette.size]
                    val flowerAlt = flowerPalette[((fIdx / 4) + 1) % flowerPalette.size]
                    addOrganicBougainvilleaShrub(out, f.pos.x, 0.28f, f.pos.z, flowerCol, flowerAlt)

                    // Graceful Curved Twin-Arm Cobra-Head 5000K LED Streetlight every ~36m on median
                    if (fIdx % 6 == 0) {
                        addCurvedTwinArmStreetlight(
                            out = out,
                            cx = f.pos.x,
                            baseY = 0.28f,
                            cz = f.pos.z,
                            rx = f.right.x,
                            rz = f.right.z,
                            poleHeight = 8.0f,
                            armReach = 2.65f
                        )
                    }
                }
                fIdx += 3
            }
        }

        // Kodakara Junction NHAI Overhead Directional Gantry (X = 2016, Z = -3437)
        val kodakaraSign = createBannerSignSpec(
            cacheKey = "ZONE3_KODAKARA_NH544_GANTRY_V2",
            bgColorInt = 0xFF057A55.toInt(),
            borderColorInt = 0xFFFFFFFF.toInt(),
            titleText = "കൊടകര • KODAKARA JUNCTION • NH 544",
            subText = "ചാലക്കുടി / CHALAKUDY 11 KM ↑  •  ഇരിങ്ങാലക്കുട / IRINJALAKUDA 12 KM ↘",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 960,
            bmpHeight = 160
        )
        addOverheadHighwayGantry(out, KODAKARA_X, KODAKARA_Z, 0.06f, 12.2f, kodakaraSign, facingNorth = true)
    }

    /**
     * Sculpted multi-faceted organic Bougainvillea flowering shrub mound (tapered stem +
     * 6-sided faceted evergreen bush + staggered multi-tone pink/magenta/crimson blossom dome).
     */
    private fun addOrganicBougainvilleaShrub(
        out: MutableList<QuadFace3D>,
        cx: Float,
        baseY: Float,
        cz: Float,
        primaryFlower: Color,
        accentFlower: Color
    ) {
        val sides = 6
        val rBase = 0.36f
        val rMid = 0.78f
        val rTop = 0.42f
        val yMid = baseY + 0.56f
        val yTop = baseY + 1.04f
        val apex = Vec3(cx, baseY + 1.32f, cz)

        for (k in 0 until sides) {
            val a0 = (k.toFloat() / sides) * 2f * PI.toFloat()
            val a1 = ((k + 1).toFloat() / sides) * 2f * PI.toFloat()
            val p0 = Vec3(cx + cos(a0) * rBase, baseY, cz + sin(a0) * rBase)
            val p1 = Vec3(cx + cos(a1) * rBase, baseY, cz + sin(a1) * rBase)
            val m0 = Vec3(cx + cos(a0) * rMid, yMid, cz + sin(a0) * rMid)
            val m1 = Vec3(cx + cos(a1) * rMid, yMid, cz + sin(a1) * rMid)
            val t0 = Vec3(cx + cos(a0 + 0.25f) * rTop, yTop, cz + sin(a0 + 0.25f) * rTop)
            val t1 = Vec3(cx + cos(a1 + 0.25f) * rTop, yTop, cz + sin(a1 + 0.25f) * rTop)
            val greenCol = if (k % 2 == 0) MatMedianGrass else MatMedianGrassLight
            val bloomCol = if (k % 2 == 0) primaryFlower else accentFlower
            out.add(QuadFace3D(p0, p1, m1, m0, greenCol, null, 0f))
            out.add(QuadFace3D(m0, m1, t1, t0, if (k % 3 == 0) bloomCol else greenCol, null, 0f))
            out.add(QuadFace3D(t0, t1, apex, apex, bloomCol, null, 0f))
        }
    }

    // =========================================================================
    // 6. CHALAKUDY SOUTH NH 544 BUS BAY PAVILION (PITCHED MANGALORE TERRACOTTA ROOF!)
    // =========================================================================
    private fun buildChalakudySouthTerminus3D(out: MutableList<QuadFace3D>) {
        val bx = CHALAKUDY_TERM_X + 30.5f // Strictly East of highway & turnaround bay (X = 2626.5..2635.5)
        val bz0 = -5398f
        val bz1 = -5346f
        val tileRoofTex = Zone3ArchitecturalTextures.createTerracottaTileRoofSpec()

        // Raised concrete passenger boarding platform with yellow safety tactile edge
        addAxisBox(out, bx - 3.6f, 0.06f, bz0, bx + 4.6f, 0.46f, bz1, MatConcreteDeck, MatConcretePier, MatConcretePier)
        addAxisBox(out, bx - 3.65f, 0.40f, bz0, bx - 3.30f, 0.48f, bz1, MatHazardYellow, MatHazardYellow, MatHazardYellow)

        // Cream plastered Kerala waiting hall with recessed windows
        addAxisBox(out, bx - 0.3f, 0.46f, bz0 + 6f, bx + 4.2f, 4.25f, bz1 - 6f, Color(0xFFFDF8EE), Color(0xFFF5ECD8), Color(0xFFE7D9BE))
        addAxisBox(out, bx - 0.36f, 1.85f, bz0 + 10f, bx - 0.28f, 3.35f, bz1 - 10f, MatTintedGlass, MatTintedGlass, MatTintedGlass)

        // Tapered verandah columns on concrete base plinths + passenger waiting benches
        for (pz in floatArrayOf(bz0 + 2f, bz0 + 14.5f, bz0 + 26f, bz0 + 37.5f, bz1 - 2f)) {
            addAxisBox(out, bx - 3.25f, 0.46f, pz - 0.34f, bx - 2.55f, 1.05f, pz + 0.34f, MatConcretePier, MatConcretePierDark, MatConcretePier)
            addTaperedOctagonalPillar(out, bx - 2.90f, pz, 1.05f, 4.25f, 0.22f, 0.17f, MatAcpWhite, MatAcpSilver)
        }
        for (benchZ in floatArrayOf(bz0 + 8f, bz0 + 20f, bz0 + 32f, bz0 + 44f)) {
            addAxisBox(out, bx - 1.35f, 0.46f, benchZ - 2.2f, bx - 0.65f, 0.96f, benchZ + 2.2f, MatTimberBrown, MatConcretePier, MatTimberBrown)
        }

        // Authentic Pitched Kerala Mangalore Clay-Tile Gable Roof (Y = 4.15m eaves -> Y = 6.35m ridge!)
        val rEaveY = 4.15f
        val rRidgeY = 6.35f
        val rCenterX = bx + 0.50f
        val rWestX = bx - 4.35f
        val rEastX = bx + 5.35f
        val rZ0 = bz0 - 1.6f
        val rZ1 = bz1 + 1.6f

        // West pitched Mangalore tile roof slope (facing the NH 544 highway!)
        out.add(
            QuadFace3D(
                Vec3(rWestX, rEaveY, rZ1),
                Vec3(rWestX, rEaveY, rZ0),
                Vec3(rCenterX, rRidgeY, rZ0),
                Vec3(rCenterX, rRidgeY, rZ1),
                MatTerracottaTile, null, -0.05f, signboard = tileRoofTex
            )
        )
        // East pitched Mangalore tile roof slope
        out.add(
            QuadFace3D(
                Vec3(rEastX, rEaveY, rZ0),
                Vec3(rEastX, rEaveY, rZ1),
                Vec3(rCenterX, rRidgeY, rZ1),
                Vec3(rCenterX, rRidgeY, rZ0),
                MatTerracottaTile, null, -0.05f, signboard = tileRoofTex
            )
        )
        // Terracotta ridge cap tiles & timber gable fascia boards
        addAxisBox(out, rCenterX - 0.20f, rRidgeY - 0.05f, rZ0, rCenterX + 0.20f, rRidgeY + 0.18f, rZ1, MatTerracottaTile, MatTimberBrown, MatTerracottaTile)
        for (gz in floatArrayOf(rZ0, rZ1)) {
            out.add(QuadFace3D(Vec3(rWestX, rEaveY, gz), Vec3(rCenterX, rEaveY, gz), Vec3(rCenterX, rRidgeY, gz), Vec3(rWestX, rEaveY, gz), MatTimberBrown, null, 0f))
            out.add(QuadFace3D(Vec3(rCenterX, rEaveY, gz), Vec3(rEastX, rEaveY, gz), Vec3(rCenterX, rRidgeY, gz), Vec3(rCenterX, rRidgeY, gz), MatTimberBrown, null, 0f))
        }

        // Crisp Bilingual Chalakudy NH 544 Bus Bay Signboard on West Fascia
        val chalakudySign = createBannerSignSpec(
            cacheKey = "ZONE3_CHALAKUDY_TERMINUS_V2",
            bgColorInt = 0xFF1E3A8A.toInt(),
            borderColorInt = 0xFFFACC15.toInt(),
            titleText = "ചാലക്കുടി • CHALAKUDY NH 544 BUS BAY",
            subText = "THRISSUR • PALAKKAD • ATHIRAPPALLY • ERNAKULAM EXPRESS",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 896,
            bmpHeight = 160
        )
        out.add(
            QuadFace3D(
                Vec3(rWestX - 0.08f, 3.65f, bz1 - 6f),
                Vec3(rWestX - 0.08f, 3.65f, bz0 + 6f),
                Vec3(rWestX - 0.08f, 4.65f, bz0 + 6f),
                Vec3(rWestX - 0.08f, 4.65f, bz1 - 6f),
                Color(0xFF1E3A8A), null, -0.22f, signboard = chalakudySign
            )
        )
    }

    // =========================================================================
    // LIVE DYNAMIC WORKING ELEMENTS (DRAWN EVERY FRAME AT 60 FPS!)
    //   - Paliyekkara Toll Plaza 12 Pneumatic Boom Barrier Arms (0° -> 85° in 1.2s!)
    //     with red-white reflective stripes & glowing RED/GREEN LED strips!
    //   (Live Southern Railway 8-unit WAG-9 trains under Ollur ROB now use REAL 3D
    //    textured models in ShoranurRailwaySystem.kt — no blocky box train here!)
    // =========================================================================
    fun collectZone3DynamicFaces(
        simTimeSec: Float,
        activeTollLaneIdx: Int,
        tollBarrierOpenFrac: Float,
        camEye: Vec3,
        outFaces: MutableList<QuadFace3D>,
        laneOpenFracs: FloatArray? = null
    ) {
        // PALIYEKKARA TOLL PLAZA 12 PNEUMATIC LED BOOM BARRIER ARMS + LIVE RED/GREEN BOOTH SIGNALS
        if (hypot(camEye.x - TOLL_CENTER_X, camEye.z - TOLL_CENTER_Z) < 360f) {
            for (laneIdx in 0 until TOLL_LANE_COUNT) {
                val isOpenLane = isTollLaneActiveFastag(laneIdx)
                val playerFrac = if (isOpenLane && laneIdx == activeTollLaneIdx) tollBarrierOpenFrac.coerceIn(0f, 1f) else 0f
                val trafficFrac = if (isOpenLane && laneOpenFracs != null && laneIdx < laneOpenFracs.size) {
                    laneOpenFracs[laneIdx].coerceIn(0f, 1f)
                } else 0f
                val rawOpen = max(playerFrac, trafficFrac)
                val openFrac = if (rawOpen > 0.85f && rawOpen < 0.999f) {
                    rawOpen + sin((rawOpen - 0.85f) * 24f) * 0.025f
                } else rawOpen

                val angleRad = (openFrac * 85.0f * (PI.toFloat() / 180.0f)).coerceIn(0f, 1.52f)
                val isGreenLed = openFrac > 0.18f
                val ledColor = if (isGreenLed) MatLedGreen else MatLedRed

                val pivotX = tollIslandX(laneIdx + 1) - 0.18f
                val pivotY = 1.14f
                val pivotZ = if (laneIdx < 6) TOLL_CENTER_Z - 3.6f else TOLL_CENTER_Z + 3.6f

                val armLen = 3.72f
                val segs = 7
                val segL = armLen / segs
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)

                // Thicker, crisp red-and-white reflective pneumatic boom arm clearly visible from Cockpit & Chase views!
                val halfH = 0.105f
                val halfT = 0.075f
                for (s in 0 until segs) {
                    val d0 = s * segL
                    val d1 = (s + 1) * segL
                    val x0 = pivotX - d0 * cosA
                    val y0 = pivotY + d0 * sinA
                    val x1 = pivotX - d1 * cosA
                    val y1 = pivotY + d1 * sinA
                    val stripeCol = if (s % 2 == 0) MatLedRed else MatLedWhite

                    val nx = sinA * halfH
                    val ny = cosA * halfH
                    // North face (+Z)
                    outFaces.add(
                        QuadFace3D(
                            Vec3(x0 - nx, y0 - ny, pivotZ + halfT),
                            Vec3(x1 - nx, y1 - ny, pivotZ + halfT),
                            Vec3(x1 + nx, y1 + ny, pivotZ + halfT),
                            Vec3(x0 + nx, y0 + ny, pivotZ + halfT),
                            stripeCol, null, -0.04f
                        )
                    )
                    // South face (-Z)
                    outFaces.add(
                        QuadFace3D(
                            Vec3(x1 - nx, y1 - ny, pivotZ - halfT),
                            Vec3(x0 - nx, y0 - ny, pivotZ - halfT),
                            Vec3(x0 + nx, y0 + ny, pivotZ - halfT),
                            Vec3(x1 + nx, y1 + ny, pivotZ - halfT),
                            stripeCol, null, -0.04f
                        )
                    )
                }

                // Bright Glowing RED/GREEN LED Strip running full length beneath boom arm!
                val tipX = pivotX - armLen * cosA
                val tipY = pivotY + armLen * sinA
                val ledOffX = -sinA * (halfH + 0.055f)
                val ledOffY = -cosA * (halfH + 0.055f)
                outFaces.add(
                    QuadFace3D(
                        Vec3(pivotX + ledOffX, pivotY + ledOffY, pivotZ + halfT + 0.015f),
                        Vec3(tipX + ledOffX, tipY + ledOffY, pivotZ + halfT + 0.015f),
                        Vec3(tipX, tipY, pivotZ + halfT + 0.015f),
                        Vec3(pivotX, pivotY, pivotZ + halfT + 0.015f),
                        ledColor, null, -0.08f
                    )
                )
                outFaces.add(
                    QuadFace3D(
                        Vec3(tipX + ledOffX, tipY + ledOffY, pivotZ - halfT - 0.015f),
                        Vec3(pivotX + ledOffX, pivotY + ledOffY, pivotZ - halfT - 0.015f),
                        Vec3(pivotX, pivotY, pivotZ - halfT - 0.015f),
                        Vec3(tipX, tipY, pivotZ - halfT - 0.015f),
                        ledColor, null, -0.08f
                    )
                )

                // Live 2-Aspect Booth Traffic Signal Lenses (glows RED when gate closed, switches GREEN when gate opens!)
                val iIdx = laneIdx + 1
                if (iIdx in 1..11) {
                    val ix = tollIslandX(iIdx)
                    val sigZ = if (laneIdx < 6) TOLL_CENTER_Z + 2.75f else TOLL_CENTER_Z - 2.75f
                    val faceZ = if (laneIdx < 6) sigZ + 0.16f else sigZ - 0.16f
                    val redCol = if (isGreenLed) Color(0xFF450A0A) else MatLedRed
                    val grnCol = if (isGreenLed) MatLedGreen else Color(0xFF052E16)
                    // Upper Red lens + Lower Green lens (visible from both directions!)
                    outFaces.add(
                        QuadFace3D(
                            Vec3(ix - 0.12f, 2.52f, faceZ),
                            Vec3(ix + 0.12f, 2.52f, faceZ),
                            Vec3(ix + 0.12f, 2.76f, faceZ),
                            Vec3(ix - 0.12f, 2.76f, faceZ),
                            redCol, null, -0.10f
                        )
                    )
                    outFaces.add(
                        QuadFace3D(
                            Vec3(ix - 0.12f, 2.20f, faceZ),
                            Vec3(ix + 0.12f, 2.20f, faceZ),
                            Vec3(ix + 0.12f, 2.44f, faceZ),
                            Vec3(ix - 0.12f, 2.44f, faceZ),
                            grnCol, null, -0.10f
                        )
                    )
                }
            }
        }
    }

    // =========================================================================
    // ARCHITECTURAL 3D GEOMETRY PRIMITIVES (TRUSSES, CUTWATER PIERS, COBRA LIGHTS)
    // =========================================================================

    /**
     * Hydrodynamic 10-sided Cutwater Elliptical River Bridge Pier (pointed East-West V-noses
     * slicing the river current cleanly, modeled after real Kerala PWD / NHAI river bridges).
     */
    private fun addCutwaterRiverPier(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        y0: Float,
        y1: Float,
        rx: Float,
        rz: Float,
        lightCol: Color,
        darkCol: Color
    ) {
        // 10-vertex hydrodynamic boat-hull / cutwater profile elongated along river flow (East-West X)
        val profile = arrayOf(
            Pair(rx, 0f),
            Pair(rx * 0.58f, rz * 0.76f),
            Pair(0f, rz),
            Pair(-rx * 0.58f, rz * 0.76f),
            Pair(-rx, 0f),
            Pair(-rx * 0.58f, -rz * 0.76f),
            Pair(0f, -rz),
            Pair(rx * 0.58f, -rz * 0.76f)
        )
        for (k in profile.indices) {
            val p0 = profile[k]
            val p1 = profile[(k + 1) % profile.size]
            out.add(
                QuadFace3D(
                    Vec3(cx + p0.first, y0, cz + p0.second),
                    Vec3(cx + p1.first, y0, cz + p1.second),
                    Vec3(cx + p1.first, y1, cz + p1.second),
                    Vec3(cx + p0.first, y1, cz + p0.second),
                    if (k % 2 == 0) lightCol else darkCol, null, 0f
                )
            )
        }
        // Top cap for stepped caisson ledge
        out.add(
            QuadFace3D(
                Vec3(cx - rx * 0.65f, y1, cz - rz * 0.85f),
                Vec3(cx + rx * 0.65f, y1, cz - rz * 0.85f),
                Vec3(cx + rx * 0.65f, y1, cz + rz * 0.85f),
                Vec3(cx - rx * 0.65f, y1, cz + rz * 0.85f),
                lightCol, null, 0f
            )
        )
    }

    /**
     * Flared Trapezoidal Concrete Hammerhead Pier Cap Beam (wider at top under I-girders,
     * cantilevered tapered undersides toward pier shafts).
     */
    private fun addTrapezoidalPierCap(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        halfBotW: Float,
        halfTopW: Float,
        yBot: Float,
        yTop: Float,
        halfThickZ: Float
    ) {
        val z0 = cz - halfThickZ
        val z1 = cz + halfThickZ
        // North & South trapezoidal faces
        out.add(QuadFace3D(Vec3(cx - halfBotW, yBot, z1), Vec3(cx + halfBotW, yBot, z1), Vec3(cx + halfTopW, yTop, z1), Vec3(cx - halfTopW, yTop, z1), MatConcreteDeck, null, 0f))
        out.add(QuadFace3D(Vec3(cx + halfBotW, yBot, z0), Vec3(cx - halfBotW, yBot, z0), Vec3(cx - halfTopW, yTop, z0), Vec3(cx + halfTopW, yTop, z0), MatConcretePier, null, 0f))
        // Sloped West & East cantilevered wing undersides
        out.add(QuadFace3D(Vec3(cx - halfBotW, yBot, z0), Vec3(cx - halfBotW, yBot, z1), Vec3(cx - halfTopW, yTop, z1), Vec3(cx - halfTopW, yTop, z0), MatConcretePierDark, null, 0f))
        out.add(QuadFace3D(Vec3(cx + halfBotW, yBot, z1), Vec3(cx + halfBotW, yBot, z0), Vec3(cx + halfTopW, yTop, z0), Vec3(cx + halfTopW, yTop, z1), MatConcretePierDark, null, 0f))
        // Top bearing seat & bottom soffit
        out.add(QuadFace3D(Vec3(cx - halfTopW, yTop, z0), Vec3(cx + halfTopW, yTop, z0), Vec3(cx + halfTopW, yTop, z1), Vec3(cx - halfTopW, yTop, z1), MatConcreteDeck, null, 0f))
        out.add(QuadFace3D(Vec3(cx - halfBotW, yBot, z1), Vec3(cx + halfBotW, yBot, z1), Vec3(cx + halfBotW, yBot, z0), Vec3(cx - halfBotW, yBot, z0), MatConcretePierDark, null, 0f))
    }

    /**
     * Sloped Trapezoidal New Jersey Concrete Toll Island Segment.
     */
    private fun addTrapezoidIslandSegment(
        out: MutableList<QuadFace3D>,
        cx: Float,
        z0: Float,
        z1: Float,
        yBot: Float,
        yTop: Float,
        hwBot: Float,
        hwTop: Float,
        sideCol: Color,
        topCol: Color
    ) {
        out.add(QuadFace3D(Vec3(cx - hwBot, yBot, z0), Vec3(cx - hwBot, yBot, z1), Vec3(cx - hwTop, yTop, z1), Vec3(cx - hwTop, yTop, z0), sideCol, null, 0f))
        out.add(QuadFace3D(Vec3(cx + hwBot, yBot, z1), Vec3(cx + hwBot, yBot, z0), Vec3(cx + hwTop, yTop, z0), Vec3(cx + hwTop, yTop, z1), sideCol, null, 0f))
        out.add(QuadFace3D(Vec3(cx - hwTop, yTop, z0), Vec3(cx + hwTop, yTop, z0), Vec3(cx + hwTop, yTop, z1), Vec3(cx - hwTop, yTop, z1), topCol, null, 0f))
    }

    /**
     * Sloped Trapezoidal New Jersey Central Bridge Divider Segment with proper outward-facing
     * normals, yellow-black reflective base safety toe strip, and tapered portal end heights.
     */
    private fun addSlopedMedianSegment(
        out: MutableList<QuadFace3D>,
        cx: Float,
        z0: Float,
        z1: Float,
        y0: Float,
        y1: Float,
        hwBot: Float,
        hwTop: Float,
        height0: Float,
        height1: Float,
        sideCol: Color,
        topCol: Color,
        toeCol: Color
    ) {
        val toeH0 = min(0.22f, height0 * 0.45f)
        val toeH1 = min(0.22f, height1 * 0.45f)
        val hwMid = (hwBot * 0.78f + hwTop * 0.22f)

        // West sloping face (-X normal: z1 -> z0 along bottom -> up to top)
        out.add(QuadFace3D(Vec3(cx - hwBot, y0, z0), Vec3(cx - hwBot, y1, z1), Vec3(cx - hwMid, y1 + toeH1, z1), Vec3(cx - hwMid, y0 + toeH0, z0), toeCol, null, 0f))
        out.add(QuadFace3D(Vec3(cx - hwMid, y0 + toeH0, z0), Vec3(cx - hwMid, y1 + toeH1, z1), Vec3(cx - hwTop, y1 + height1, z1), Vec3(cx - hwTop, y0 + height0, z0), sideCol, null, 0f))

        // East sloping face (+X normal: z0 -> z1 reversed so outward normal faces +X cleanly!)
        out.add(QuadFace3D(Vec3(cx + hwBot, y1, z1), Vec3(cx + hwBot, y0, z0), Vec3(cx + hwMid, y0 + toeH0, z0), Vec3(cx + hwMid, y1 + toeH1, z1), toeCol, null, 0f))
        out.add(QuadFace3D(Vec3(cx + hwMid, y1 + toeH1, z1), Vec3(cx + hwMid, y0 + toeH0, z0), Vec3(cx + hwTop, y0 + height0, z0), Vec3(cx + hwTop, y1 + height1, z1), sideCol, null, 0f))

        // Top concrete cap (+Y upward normal!)
        out.add(QuadFace3D(Vec3(cx - hwTop, y1 + height1, z1), Vec3(cx + hwTop, y1 + height1, z1), Vec3(cx + hwTop, y0 + height0, z0), Vec3(cx - hwTop, y0 + height0, z0), topCol, null, 0f))
    }

    /**
     * 3D Diagonal Tubular Steel Truss Strut / V-Brace connecting world point [a] to [b].
     */
    private fun addDiagonalStrut(
        out: MutableList<QuadFace3D>,
        a: Vec3,
        b: Vec3,
        halfThickness: Float,
        lightCol: Color,
        darkCol: Color
    ) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.01f)
        // Compute two perpendicular side offsets
        var px = -dz
        var py = 0f
        var pz = dx
        val pLen = hypot(px, pz)
        if (pLen < 0.01f) {
            px = 1f; py = 0f; pz = 0f
        } else {
            px /= pLen; pz /= pLen
        }
        val ux = (dy * pz - dz * py) / len
        val uy = (dz * px - dx * pz) / len
        val uz = (dx * py - dy * px) / len

        val r = halfThickness
        val aP = Vec3(a.x + px * r, a.y + py * r, a.z + pz * r)
        val aN = Vec3(a.x - px * r, a.y - py * r, a.z - pz * r)
        val bP = Vec3(b.x + px * r, b.y + py * r, b.z + pz * r)
        val bN = Vec3(b.x - px * r, b.y - py * r, b.z - pz * r)

        val aU = Vec3(a.x + ux * r, a.y + uy * r, a.z + uz * r)
        val aD = Vec3(a.x - ux * r, a.y - uy * r, a.z - uz * r)
        val bU = Vec3(b.x + ux * r, b.y + uy * r, b.z + uz * r)
        val bD = Vec3(b.x - ux * r, b.y - uy * r, b.z - uz * r)

        out.add(QuadFace3D(aN, bN, bP, aP, lightCol, null, 0f))
        out.add(QuadFace3D(aD, bD, bU, aU, darkCol, null, 0f))
    }

    /**
     * Graceful Curved Sweeping Twin-Arm Cobra-Head NHAI Highway / Bridge LED Streetlight.
     */
    private fun addCurvedTwinArmStreetlight(
        out: MutableList<QuadFace3D>,
        cx: Float,
        baseY: Float,
        cz: Float,
        rx: Float,
        rz: Float,
        poleHeight: Float,
        armReach: Float
    ) {
        val topY = baseY + poleHeight
        // Tapered octagonal galvanized steel pole
        addTaperedOctagonalPillar(out, cx, cz, baseY, topY, 0.14f, 0.075f, MatGalvanized, MatGalvanizedDark)

        // Sweeping curved dual luminaire arms extending left & right over both carriageways
        for (side in floatArrayOf(-1f, 1f)) {
            val elbow = Vec3(cx + rx * side * (armReach * 0.48f), topY + 0.58f, cz + rz * side * (armReach * 0.48f))
            val tip = Vec3(cx + rx * side * armReach, topY + 0.78f, cz + rz * side * armReach)
            addDiagonalStrut(out, Vec3(cx, topY - 0.12f, cz), elbow, 0.05f, MatGalvanized, MatGalvanizedDark)
            addDiagonalStrut(out, elbow, tip, 0.045f, MatGalvanized, MatGalvanizedDark)
            // Sleek tapered LED cobra-head fixture housing + glowing white underside lens
            addAxisBox(out, tip.x - 0.34f, tip.y - 0.06f, tip.z - 0.16f, tip.x + 0.34f, tip.y + 0.10f, tip.z + 0.16f, MatGalvanizedDark, MatGalvanized, MatGalvanizedDark)
            addAxisBox(out, tip.x - 0.28f, tip.y - 0.11f, tip.z - 0.12f, tip.x + 0.28f, tip.y - 0.05f, tip.z + 0.12f, MatLedWhite, MatLedWarm, MatLedWhite)
        }
    }

    private fun addOverheadHighwayGantry(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        baseY: Float,
        halfSpanX: Float,
        signSpec: SignboardQuadSpec,
        facingNorth: Boolean
    ) {
        // Tapered steel gantry columns with concrete plinth bases
        for (side in floatArrayOf(-1f, 1f)) {
            val lx = cx + side * halfSpanX
            addAxisBox(out, lx - 0.42f, baseY, cz - 0.42f, lx + 0.42f, baseY + 0.65f, cz + 0.42f, MatHazardYellow, MatHazardBlack, MatHazardYellow)
            addTaperedOctagonalPillar(out, lx, cz, baseY + 0.65f, baseY + 7.5f, 0.28f, 0.20f, MatGalvanized, MatGalvanizedDark)
        }
        // Open double-chord steel overhead truss girder
        addAxisBox(out, cx - halfSpanX - 0.3f, baseY + 5.85f, cz - 0.20f, cx + halfSpanX + 0.3f, baseY + 6.08f, cz + 0.20f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
        addAxisBox(out, cx - halfSpanX - 0.3f, baseY + 6.75f, cz - 0.20f, cx + halfSpanX + 0.3f, baseY + 6.98f, cz + 0.20f, MatGalvanized, MatGalvanizedDark, MatGalvanized)
        val boardW = min(9.8f, halfSpanX - 1.5f)
        val sz = if (facingNorth) cz + 0.28f else cz - 0.28f
        if (facingNorth) {
            out.add(
                QuadFace3D(
                    Vec3(cx + boardW, baseY + 5.75f, sz),
                    Vec3(cx - boardW, baseY + 5.75f, sz),
                    Vec3(cx - boardW, baseY + 7.55f, sz),
                    Vec3(cx + boardW, baseY + 7.55f, sz),
                    Color(0xFF057A55), null, -0.22f, signboard = signSpec
                )
            )
        } else {
            out.add(
                QuadFace3D(
                    Vec3(cx - boardW, baseY + 5.75f, sz),
                    Vec3(cx + boardW, baseY + 5.75f, sz),
                    Vec3(cx + boardW, baseY + 7.55f, sz),
                    Vec3(cx - boardW, baseY + 7.55f, sz),
                    Color(0xFF057A55), null, -0.22f, signboard = signSpec
                )
            )
        }
    }

    private fun addOctagonalPillar(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        y0: Float,
        y1: Float,
        radius: Float,
        lightCol: Color,
        darkCol: Color
    ) {
        addTaperedOctagonalPillar(out, cx, cz, y0, y1, radius, radius, lightCol, darkCol)
    }

    private fun addTaperedOctagonalPillar(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        y0: Float,
        y1: Float,
        rBot: Float,
        rTop: Float,
        lightCol: Color,
        darkCol: Color
    ) {
        val sides = 8
        for (k in 0 until sides) {
            val a0 = (k.toFloat() / sides) * 2f * PI.toFloat()
            val a1 = ((k + 1).toFloat() / sides) * 2f * PI.toFloat()
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            out.add(
                QuadFace3D(
                    Vec3(cx + c0 * rBot, y0, cz + s0 * rBot),
                    Vec3(cx + c1 * rBot, y0, cz + s1 * rBot),
                    Vec3(cx + c1 * rTop, y1, cz + s1 * rTop),
                    Vec3(cx + c0 * rTop, y1, cz + s0 * rTop),
                    if (k % 2 == 0) lightCol else darkCol,
                    null, 0f
                )
            )
        }
    }

    private fun addAxisBox(
        out: MutableList<QuadFace3D>,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        topCol: Color, sideCol: Color, frontBackCol: Color
    ) {
        out.add(QuadFace3D(Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x0, y1, z1), topCol, null, 0f))
        out.add(QuadFace3D(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1), frontBackCol, null, 0f))
        out.add(QuadFace3D(Vec3(x1, y0, z0), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0), frontBackCol, null, 0f))
        out.add(QuadFace3D(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0), sideCol, null, 0f))
        out.add(QuadFace3D(Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), sideCol, null, 0f))
    }
}
