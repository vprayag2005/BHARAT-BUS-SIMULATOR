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

/**
 * 5-State Interactive Sequence for Muthuvara Animated Railway Level Crossing (LC-14 on SH 69).
 */
enum class MuthuvaraLcPhase {
    STATE_OPEN,
    STATE_CLOSING,
    STATE_LOCKED,
    STATE_TRAIN_PASSING,
    STATE_OPENING
}

/**
 * Zone 4: North-West, Thrissur Kole Wetlands, Enamavu Sluice Regulator Bridge,
 * Muthuvara Animated Railway Level Crossing (LC-14), Guruvayur Temple Square & Sawtooth Bus Terminal,
 * and Bharathappuzha (Nila River) Dual Bridges (Historic 1902 Cochin Truss + Modern SH 22 Bridge).
 *
 * Road Design Rule Guarantee:
 * - Every drivable carriageway (SH 75, SH 69, SH 22, Guruvayur Avenue & Stand Driveways) stays 100% clear.
 * - All sluice winch towers, LC stanchions, gatekeeper cabin, temple plazas, bridge parapets, and heritage piers
 *   sit strictly outside the drivable lanes.
 */
internal object Zone4KoleRailHeritageRenderer {

    // =========================================================================
    // SINGLE SOURCE OF TRUTH: ZONE 4 GEOMETRIC & ELEVATION CONSTANTS
    // =========================================================================

    // 1. SH 75 Thrissur Kole Wetlands Raised 1.5m Laterite Embankment (X = -1100 .. -1845)
    const val KOLE_EAST_RAMP_START_X = -1100.0f
    const val KOLE_EAST_FLAT_X = -1155.0f
    const val KOLE_WEST_FLAT_X = -1790.0f
    const val KOLE_WEST_RAMP_END_X = -1845.0f
    const val KOLE_EMBANKMENT_Y = 1.56f // Exact 1.50m above 0.06m base datum

    // 2. Enamavu Sluice Regulator Bridge (Major Water Bridge 3 — 18 Sluice Gates × 5.0m = 90.0m)
    const val ENAMAVU_CENTER_Z = 113.0f
    const val ENAMAVU_EAST_RAMP_START_X = -1555.0f
    const val ENAMAVU_DECK_EAST_X = -1600.0f
    const val ENAMAVU_DECK_WEST_X = -1690.0f
    const val ENAMAVU_WEST_RAMP_END_X = -1735.0f
    const val ENAMAVU_DECK_Y = 3.45f
    const val ENAMAVU_DECK_HALF_W = 5.25f
    const val ENAMAVU_GATE_COUNT = 18

    // 3. Muthuvara Animated Railway Level Crossing (LC-14 on SH 69 — Thrissur–Guruvayur Line)
    const val MUTHUVARA_LC_X = -560.0f
    const val MUTHUVARA_LC_Z = 610.0f
    const val MUTHUVARA_ROAD_HALF_W = 5.30f
    const val MUTHUVARA_TRACK_WEST_X = -960.0f
    const val MUTHUVARA_TRACK_EAST_X = -160.0f
    const val MUTHUVARA_STOP_LINE_OFFSET_Z = 8.8f
    const val MUTHUVARA_GATE_Z_OFFSET = 6.2f

    // 4. Guruvayur Temple East Nada Square & Sawtooth Bus Terminal
    const val GURUVAYUR_TEMPLE_CENTER_X = -2590.0f
    const val GURUVAYUR_TEMPLE_CENTER_Z = 1260.0f
    const val GURUVAYUR_STAND_CENTER_X = -2735.0f
    const val GURUVAYUR_STAND_CENTER_Z = 1296.0f

    // 5. Bharathappuzha (Nila River) Dual Bridges (Northern Boundary Bridge 4 — Cheruthuruthy to Shoranur)
    const val BHARATHAPPUZHA_CENTER_X = 100.0f
    const val COCHIN_1902_CENTER_X = 123.5f // Parallel 23.5m East of Modern Roadway Bridge
    const val BHARATHAPPUZHA_RAMP_SOUTH_Z = 2630.0f
    const val BHARATHAPPUZHA_DECK_SOUTH_Z = 2690.0f
    const val BHARATHAPPUZHA_DECK_NORTH_Z = 2890.0f
    const val BHARATHAPPUZHA_RAMP_NORTH_Z = 2950.0f
    const val BHARATHAPPUZHA_DECK_Y = 6.20f
    const val BHARATHAPPUZHA_DECK_HALF_W = 5.60f

    // =========================================================================
    // ZONE 4 AUTHENTIC MATERIAL PALETTE
    // =========================================================================
    private val MatLateriteRich = Color(0xFF8B3A22)
    private val MatLateriteDark = Color(0xFF6B2A18)
    private val MatLateriteLight = Color(0xFFA84C30)
    private val MatKolePaddyLush = Color(0xFF3F7D20)
    private val MatKolePaddyBright = Color(0xFF569E2B)
    private val MatKoleMudBund = Color(0xFF5D4632)
    private val MatWaterHyacinth = Color(0xFF2D6A30)
    private val MatWaterHyacinthLight = Color(0xFF438A3E)
    private val MatDrainageCanalDark = Color(0xFF144B57)

    private val MatBarrageConcrete = Color(0xFF9CA3AF)
    private val MatBarrageConcreteDark = Color(0xFF6B7280)
    private val MatBarrageConcreteLight = Color(0xFFCBD5E1)
    private val MatIndustrialGrey = Color(0xFF475569)
    private val MatIndustrialGreyDark = Color(0xFF334155)
    private val MatIndustrialSteel = Color(0xFF64748B)
    private val MatRustyIron = Color(0xFF7C3A21)

    private val MatLcRed = Color(0xFFDC2626)
    private val MatLcWhite = Color(0xFFF8FAFC)
    private val MatLcYellowOchre = Color(0xFFEAB308)
    private val MatLcYellowCabin = Color(0xFFF5C842)
    private val MatLcYellowCabinShade = Color(0xFFD9A626)
    private val MatLcBrickBase = Color(0xFF8F3828)
    private val MatLcCastIron = Color(0xFF1E293B)
    private val MatLcBlack = Color(0xFF111827)
    private val MatLcLedOn = Color(0xFFFF2424)
    private val MatLcLedOff = Color(0xFF4A1515)
    private val MatBallastCrushed = Color(0xFF4B4742)
    private val MatRailSteel = Color(0xFF8E969E)

    private val MatGranitePlaza = Color(0xFF9E9B94)
    private val MatGraniteBorder = Color(0xFF75726B)
    private val MatKeralaWhitewash = Color(0xFFF4EFE6)
    private val MatKeralaCream = Color(0xFFEAE2D3)
    private val MatTeakDark = Color(0xFF4A2C18)
    private val MatTeakMid = Color(0xFF6B3E22)
    private val MatTerracottaTemple = Color(0xFFB43E24)
    private val MatTerracottaDark = Color(0xFF8A2B18)
    private val MatCopperRoof = Color(0xFF9A4820)
    private val MatTempleGold = Color(0xFFEAB308)
    private val MatTempleGoldBright = Color(0xFFFACC15)
    private val MatTempleGoldDeep = Color(0xFFB47808)
    private val MatFlameWarm = Color(0xFFFFB703)
    private val MatElephantGrey = Color(0xFF44403C)
    private val MatElephantDark = Color(0xFF292524)
    private val MatIvory = Color(0xFFFEFCE8)

    private val MatTerminalApron = Color(0xFF3E444D)
    private val MatTerminalPlatform = Color(0xFFC8CCD2)
    private val MatTerminalBlue = Color(0xFF1E3A8A)
    private val MatTerminalRoofBlue = Color(0xFF1D4ED8)
    private val MatGlassTinted = Color(0xFF1E3A5F)

    private val MatCochinRedOxide = Color(0xFF7F2A22)
    private val MatCochinRedOxideDark = Color(0xFF5A1D17)
    private val MatCochinBlackSteel = Color(0xFF27272A)
    private val MatCutStoneGranite = Color(0xFF78716C)
    private val MatCutStoneDark = Color(0xFF57534E)
    private val MatNilaSandGolden = Color(0xFFD9B675)
    private val MatNilaSandWarm = Color(0xFFC9A25E)
    private val MatElephantGrass = Color(0xFF4D7C0F)
    private val MatElephantGrassTip = Color(0xFF84CC16)
    private val MatEgretWhite = Color(0xFFFAFAFA)
    private val MatKingfisherBlue = Color(0xFF0284C7)

    // =========================================================================
    // ELEVATION PROFILES (O(1) SMOOTH C1 COSINE RAMPS FOR ROADS & BUS PHYSICS)
    // =========================================================================

    private fun cosinePlateauX(
        x: Float,
        eastRampStart: Float,
        eastDeckStart: Float,
        westDeckEnd: Float,
        westRampEnd: Float
    ): Float {
        // Note: Moving West means X becomes MORE NEGATIVE (eastRampStart > eastDeckStart > westDeckEnd > westRampEnd)
        return when {
            x >= eastRampStart -> 0f
            x <= westRampEnd -> 0f
            x in westDeckEnd..eastDeckStart -> 1f
            x > eastDeckStart -> {
                val u = ((eastRampStart - x) / (eastRampStart - eastDeckStart)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
            else -> {
                val u = ((x - westRampEnd) / (westDeckEnd - westRampEnd)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
        }
    }

    private fun cosinePlateauZ(
        z: Float,
        southRampStart: Float,
        southDeckStart: Float,
        northDeckEnd: Float,
        northRampEnd: Float
    ): Float {
        return when {
            z <= southRampStart -> 0f
            z >= northRampEnd -> 0f
            z in southDeckStart..northDeckEnd -> 1f
            z < southDeckStart -> {
                val u = ((z - southRampStart) / (southDeckStart - southRampStart)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
            else -> {
                val u = ((northRampEnd - z) / (northRampEnd - northDeckEnd)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
        }
    }

    /**
     * Exact O(1) road surface Y for all Zone 4 elevated corridors:
     * - SH 75 Kole Wetlands 1.5m raised embankment (Y = 1.56m)
     * - Enamavu Sluice Regulator Bridge deck (Y = 3.45m)
     * - Bharathappuzha Modern Roadway Bridge deck on SH 22 (Y = 6.20m)
     */
    fun computeZone4RoadSurfaceY(x: Float, z: Float): Float {
        var y = 0.06f

        // A. SH 75 Kole Wetlands 1.5m Raised Embankment & Enamavu Sluice Regulator Bridge
        if (x in KOLE_WEST_RAMP_END_X..KOLE_EAST_RAMP_START_X && z in 42.0f..178.0f) {
            val koleP = cosinePlateauX(
                x, KOLE_EAST_RAMP_START_X, KOLE_EAST_FLAT_X, KOLE_WEST_FLAT_X, KOLE_WEST_RAMP_END_X
            )
            var sh75Y = 0.06f + koleP * (KOLE_EMBANKMENT_Y - 0.06f)

            if (x in ENAMAVU_WEST_RAMP_END_X..ENAMAVU_EAST_RAMP_START_X && abs(z - ENAMAVU_CENTER_Z) <= 16.0f) {
                val enamavuP = cosinePlateauX(
                    x, ENAMAVU_EAST_RAMP_START_X, ENAMAVU_DECK_EAST_X, ENAMAVU_DECK_WEST_X, ENAMAVU_WEST_RAMP_END_X
                )
                sh75Y = max(sh75Y, KOLE_EMBANKMENT_Y + enamavuP * (ENAMAVU_DECK_Y - KOLE_EMBANKMENT_Y))
            }
            y = max(y, sh75Y)
        }

        // B. Bharathappuzha (Nila River) Modern Roadway Bridge on SH 22 (X = 100.0, Z = 2630..2950)
        if (z in BHARATHAPPUZHA_RAMP_SOUTH_Z..BHARATHAPPUZHA_RAMP_NORTH_Z &&
            abs(x - BHARATHAPPUZHA_CENTER_X) <= 13.5f
        ) {
            val bp = cosinePlateauZ(
                z, BHARATHAPPUZHA_RAMP_SOUTH_Z, BHARATHAPPUZHA_DECK_SOUTH_Z,
                BHARATHAPPUZHA_DECK_NORTH_Z, BHARATHAPPUZHA_RAMP_NORTH_Z
            )
            y = max(y, 0.06f + bp * (BHARATHAPPUZHA_DECK_Y - 0.06f))
        }

        return y
    }

    /**
     * Scenery / tree / building clearance test guaranteeing ZERO props ever spawn inside:
     * - Enamavu Regulator Bridge & backwater channels
     * - SH 75 Kole Wetlands flooded paddy/canal expanse near the embankment
     * - Muthuvara Level Crossing railway track corridor & gatekeeper yard
     * - Guruvayur Sree Krishna Temple Square, Rudratheertham & Sawtooth Bus Terminal
     * - Bharathappuzha sandy riverbed & Dual Bridges (Modern + 1902 Cochin Bridge)
     */
    fun isPointClearOfZone4Structures(px: Float, pz: Float): Boolean {
        // 1. Enamavu Sluice Regulator Bridge + Lake/Sea backwater channels
        if (px in -1755f..-1535f && pz in -210f..360f) return false
        // 2. SH 75 Kole Wetlands flanking canals & flooded paddy zone immediately beside embankment
        if (px in -1840f..-1110f && pz in 55f..195f) return false
        // 3. Muthuvara Level Crossing track corridor (Z = 610 ± 18m) & SH 69 crossing zone
        if (px in -970f..-150f && pz in 590f..630f) return false
        if (px in -590f..-530f && pz in 565f..655f) return false
        // 4. Guruvayur Temple East Nada Square, Rudratheertham Pond & Sawtooth Bus Terminal
        if (px in -2795f..-2535f && pz in 1222f..1342f) return false
        // 5. Bharathappuzha Dual Bridges & Broad Sandy Nila Riverbed (Z = 2620..2960)
        if (px in 44f..340f && pz in 2620f..2960f) return false
        return true
    }

    // =========================================================================
    // GROUND PLAZAS: KOLE PADDY FIELDS, ENAMAVU CHANNEL, MUTHUVARA BALLAST,
    //                GURUVAYUR EAST NADA PLAZA/TERMINAL & BHARATHAPPUZHA SANDS
    // =========================================================================
    fun DrawScope.drawZone4GroundPlazas(
        rig: CameraRig,
        path: Path,
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>
    ) {
        val drawAll = rig.recorder != null

        // 1. THRISSUR KOLE WETLANDS FLOODED PADDY FIELDS (Padashekharam), MUD BUNDS & CANALS
        if (drawAll || (camEye.x in -2150f..-850f && camEye.z in -300f..550f)) {
            drawKoleWetlandsGround(rig, path)
        }

        // 2. ENAMAVU REGULATOR BACKWATER CHANNEL BED & FLOATING WATER HYACINTH (African Payal) MATS
        if (drawAll || hypot(camEye.x - (-1645f), camEye.z - ENAMAVU_CENTER_Z) < 620f) {
            drawEnamavuBackwaterGround(rig, path)
        }

        // 3. MUTHUVARA RAILWAY LEVEL CROSSING BALLAST CORRIDOR & ROAD STOP-LINE MARKINGS
        if (drawAll || hypot(camEye.x - MUTHUVARA_LC_X, camEye.z - MUTHUVARA_LC_Z) < 520f) {
            drawMuthuvaraLcGround(rig, path)
        }

        // 4. GURUVAYUR SREE KRISHNA TEMPLE EAST NADA PLAZA, RUDRATHEERTHAM & SAWTOOTH BUS TERMINAL
        if (drawAll || hypot(camEye.x - (-2680f), camEye.z - 1275f) < 560f) {
            drawGuruvayurTempleAndTerminalGround(rig, path)
        }

        // 5. BHARATHAPPUZHA (NILA RIVER) VAST GOLDEN SANDBARS & RIVERBANK LEVEES
        if (drawAll || hypot(camEye.x - BHARATHAPPUZHA_CENTER_X, camEye.z - 2790f) < 760f) {
            drawBharathappuzhaRiverbedGround(rig, path)
        }
    }

    private fun DrawScope.drawKoleWetlandsGround(rig: CameraRig, path: Path) {
        // Expansive flooded Kole paddy tracts (Padashekharam) on BOTH sides of SH 75 (X = -1140..-1550 & -1735..-1820)
        val tracts = arrayOf(
            // East of Enamavu Regulator
            floatArrayOf(-1550f, -1140f, 162f, 395f),
            floatArrayOf(-1550f, -1140f, -115f, 88f),
            // West of Enamavu Regulator toward Kanjani
            floatArrayOf(-1820f, -1735f, 130f, 340f),
            floatArrayOf(-1820f, -1735f, -95f, 92f)
        )
        for ((idx, t) in tracts.withIndex()) {
            val quad = listOf(
                Vec3(t[0], 0.036f, t[2]),
                Vec3(t[1], 0.036f, t[2]),
                Vec3(t[1], 0.036f, t[3]),
                Vec3(t[0], 0.036f, t[3])
            )
            if (rig.buildWorldPolygonPath(path, quad)) {
                drawPath(path, if (idx % 2 == 0) MatKolePaddyLush else MatKolePaddyBright)
            }
            // Checkerboard earthen mud bunds (Varambu) subdividing flooded Kole Padashekharam plots
            var bx = t[0] + 55f
            while (bx < t[1] - 25f) {
                val bund = listOf(
                    Vec3(bx - 1.4f, 0.046f, t[2]),
                    Vec3(bx + 1.4f, 0.046f, t[2]),
                    Vec3(bx + 1.4f, 0.046f, t[3]),
                    Vec3(bx - 1.4f, 0.046f, t[3])
                )
                if (rig.buildWorldPolygonPath(path, bund)) drawPath(path, MatKoleMudBund)
                bx += 68f
            }
            var bz = t[2] + 52f
            while (bz < t[3] - 25f) {
                val bundZ = listOf(
                    Vec3(t[0], 0.046f, bz - 1.3f),
                    Vec3(t[1], 0.046f, bz - 1.3f),
                    Vec3(t[1], 0.046f, bz + 1.3f),
                    Vec3(t[0], 0.046f, bz + 1.3f)
                )
                if (rig.buildWorldPolygonPath(path, bundZ)) drawPath(path, MatKoleMudBund)
                bz += 64f
            }
        }

        // Flanking deep Kole drainage canals directly alongside SH 75 embankment (East of Enamavu)
        for (canalZ in arrayOf(Pair(152f, 164f), Pair(86f, 99f))) {
            val canal = listOf(
                Vec3(-1560f, 0.030f, canalZ.first),
                Vec3(-1145f, 0.030f, canalZ.first),
                Vec3(-1145f, 0.030f, canalZ.second),
                Vec3(-1560f, 0.030f, canalZ.second)
            )
            if (rig.buildWorldPolygonPath(path, canal)) drawPath(path, MatDrainageCanalDark)
        }
    }

    private fun DrawScope.drawEnamavuBackwaterGround(rig: CameraRig, path: Path) {
        // Dark riverbed substrate underneath Enamavu Barrage (X = -1705 .. -1585, Z = -200 .. 340)
        val bed = listOf(
            Vec3(-1705f, 0.025f, -200f),
            Vec3(-1585f, 0.025f, -200f),
            Vec3(-1585f, 0.025f, 340f),
            Vec3(-1705f, 0.025f, 340f)
        )
        if (rig.buildWorldPolygonPath(path, bed)) drawPath(path, MatDrainageCanalDark)

        // Laterite abutment banks flanking East & West channel shores
        for (bankX in arrayOf(Pair(-1590f, -1566f), Pair(-1724f, -1700f))) {
            val bank = listOf(
                Vec3(bankX.first, 0.048f, -180f),
                Vec3(bankX.second, 0.048f, -180f),
                Vec3(bankX.second, 0.048f, 320f),
                Vec3(bankX.first, 0.048f, 320f)
            )
            if (rig.buildWorldPolygonPath(path, bank)) drawPath(path, MatLateriteDark)
        }

        // Lake Side (North / +Z, Z = 132..300): Floating green mats of Water Hyacinth (African Payal)
        val hyacinthMats = arrayOf(
            floatArrayOf(-1682f, -1650f, 138f, 162f),
            floatArrayOf(-1632f, -1604f, 148f, 174f),
            floatArrayOf(-1674f, -1638f, 188f, 214f),
            floatArrayOf(-1625f, -1594f, 210f, 238f),
            floatArrayOf(-1688f, -1654f, 245f, 276f)
        )
        for ((idx, hm) in hyacinthMats.withIndex()) {
            val matQuad = listOf(
                Vec3(hm[0], 0.145f, hm[2]),
                Vec3(hm[1], 0.145f, hm[2]),
                Vec3(hm[1], 0.145f, hm[3]),
                Vec3(hm[0], 0.145f, hm[3])
            )
            if (rig.buildWorldPolygonPath(path, matQuad)) {
                drawPath(path, if (idx % 2 == 0) MatWaterHyacinth else MatWaterHyacinthLight)
            }
        }
    }

    private fun DrawScope.drawMuthuvaraLcGround(rig: CameraRig, path: Path) {
        val cx = MUTHUVARA_LC_X
        val cz = MUTHUVARA_LC_Z

        // Crushed basalt railway ballast bed strictly OUTSIDE the SH 69 asphalt road (West & East wings)
        for (spanX in arrayOf(Pair(MUTHUVARA_TRACK_WEST_X, cx - MUTHUVARA_ROAD_HALF_W - 0.5f), Pair(cx + MUTHUVARA_ROAD_HALF_W + 0.5f, MUTHUVARA_TRACK_EAST_X))) {
            val ballast = listOf(
                Vec3(spanX.first, 0.035f, cz - 3.6f),
                Vec3(spanX.second, 0.035f, cz - 3.6f),
                Vec3(spanX.second, 0.035f, cz + 3.6f),
                Vec3(spanX.first, 0.035f, cz + 3.6f)
            )
            if (rig.buildWorldPolygonPath(path, ballast)) drawPath(path, MatBallastCrushed)
        }

        // Flush Reinforced Concrete Level-Crossing Deck Plate embedded across SH 69 (smooth ride over rails!)
        val lcPlate = listOf(
            Vec3(cx - MUTHUVARA_ROAD_HALF_W, 0.064f, cz - 2.35f),
            Vec3(cx + MUTHUVARA_ROAD_HALF_W, 0.064f, cz - 2.35f),
            Vec3(cx + MUTHUVARA_ROAD_HALF_W, 0.064f, cz + 2.35f),
            Vec3(cx - MUTHUVARA_ROAD_HALF_W, 0.064f, cz + 2.35f)
        )
        if (rig.buildWorldPolygonPath(path, lcPlate)) drawPath(path, MatBarrageConcrete)

        // Recessed broad-gauge steel rails (1.676m Indian Broad Gauge) cleanly across the level crossing apron
        for (rz in floatArrayOf(cz - 0.84f, cz + 0.84f)) {
            val rail = listOf(
                Vec3(cx - MUTHUVARA_ROAD_HALF_W - 0.3f, 0.069f, rz - 0.08f),
                Vec3(cx + MUTHUVARA_ROAD_HALF_W + 0.3f, 0.069f, rz - 0.08f),
                Vec3(cx + MUTHUVARA_ROAD_HALF_W + 0.3f, 0.069f, rz + 0.08f),
                Vec3(cx - MUTHUVARA_ROAD_HALF_W - 0.3f, 0.069f, rz + 0.08f)
            )
            if (rig.buildWorldPolygonPath(path, rail)) drawPath(path, MatRailSteel)
        }

        // Bold white thermoplastic STOP lines on both North & South SH 69 approaches (Z = 610 ± 8.8m)
        for (sz in floatArrayOf(cz - MUTHUVARA_STOP_LINE_OFFSET_Z, cz + MUTHUVARA_STOP_LINE_OFFSET_Z)) {
            val stopBar = listOf(
                Vec3(cx - MUTHUVARA_ROAD_HALF_W + 0.45f, 0.068f, sz - 0.32f),
                Vec3(cx + MUTHUVARA_ROAD_HALF_W - 0.45f, 0.068f, sz - 0.32f),
                Vec3(cx + MUTHUVARA_ROAD_HALF_W - 0.45f, 0.068f, sz + 0.32f),
                Vec3(cx - MUTHUVARA_ROAD_HALF_W + 0.45f, 0.068f, sz + 0.32f)
            )
            if (rig.buildWorldPolygonPath(path, stopBar)) drawPath(path, MatLcWhite)
        }

        // Yellow-and-black advance rumble warning bars at Z = 610 ± 18m & ± 20m
        for (rz in floatArrayOf(cz - 18.0f, cz - 19.6f, cz + 18.0f, cz + 19.6f)) {
            val rumble = listOf(
                Vec3(cx - MUTHUVARA_ROAD_HALF_W + 0.5f, 0.067f, rz - 0.22f),
                Vec3(cx + MUTHUVARA_ROAD_HALF_W - 0.5f, 0.067f, rz - 0.22f),
                Vec3(cx + MUTHUVARA_ROAD_HALF_W - 0.5f, 0.067f, rz + 0.22f),
                Vec3(cx - MUTHUVARA_ROAD_HALF_W + 0.5f, 0.067f, rz + 0.22f)
            )
            if (rig.buildWorldPolygonPath(path, rumble)) drawPath(path, MatLcYellowOchre)
        }

        // Concrete yard pad under Gatekeeper Cabin (East side, strictly outside road & track!)
        val cabinPad = listOf(
            Vec3(cx + 9.8f, 0.056f, cz + 6.5f),
            Vec3(cx + 19.5f, 0.056f, cz + 6.5f),
            Vec3(cx + 19.5f, 0.056f, cz + 14.5f),
            Vec3(cx + 9.8f, 0.056f, cz + 14.5f)
        )
        if (rig.buildWorldPolygonPath(path, cabinPad)) drawPath(path, MatGranitePlaza)
    }

    private fun DrawScope.drawGuruvayurTempleAndTerminalGround(rig: CameraRig, path: Path) {
        // A. Guruvayur East Nada Grand Grey Granite Pedestrian Plaza (X = -2631..-2548, Z = 1230..1290)
        //    Strictly 9m East of road center at X = -2640 -> ZERO road overlap!
        val nadaOuter = listOf(
            Vec3(-2631.0f, 0.056f, 1230.0f),
            Vec3(-2548.0f, 0.056f, 1230.0f),
            Vec3(-2548.0f, 0.056f, 1290.0f),
            Vec3(-2631.0f, 0.056f, 1290.0f)
        )
        if (rig.buildWorldPolygonPath(path, nadaOuter)) drawPath(path, MatGraniteBorder)

        val nadaInner = listOf(
            Vec3(-2629.5f, 0.058f, 1231.8f),
            Vec3(-2550.0f, 0.058f, 1231.8f),
            Vec3(-2550.0f, 0.058f, 1288.2f),
            Vec3(-2629.5f, 0.058f, 1288.2f)
        )
        if (rig.buildWorldPolygonPath(path, nadaInner)) drawPath(path, MatGranitePlaza)

        // Ceremonial granite walkway carpet leading straight into East Nada Gopuram (Z = 1255..1265)
        val nadaCarpet = listOf(
            Vec3(-2631.0f, 0.060f, 1254.5f),
            Vec3(-2614.0f, 0.060f, 1254.5f),
            Vec3(-2614.0f, 0.060f, 1265.5f),
            Vec3(-2631.0f, 0.060f, 1265.5f)
        )
        if (rig.buildWorldPolygonPath(path, nadaCarpet)) drawPath(path, MatKeralaCream)

        // Sacred Rudratheertham Temple Pond Laterite Stepped Basin (North of Temple: X = -2622..-2572, Z = 1291..1330)
        val pondSteps = listOf(
            Vec3(-2624.0f, 0.056f, 1290.0f),
            Vec3(-2570.0f, 0.056f, 1290.0f),
            Vec3(-2570.0f, 0.056f, 1331.0f),
            Vec3(-2624.0f, 0.056f, 1331.0f)
        )
        if (rig.buildWorldPolygonPath(path, pondSteps)) drawPath(path, MatLateriteRich)

        // B. Guruvayur Bus Terminal Concrete Maneuvering Apron (X = -2784..-2686, Z = 1266.2..1328.0)
        //    Flush joined to Z = 1260.0 road edge (road halfW ≈ 5.5m -> edge at Z = 1265.5m!)
        val apron = listOf(
            Vec3(-2784.0f, 0.056f, 1265.6f),
            Vec3(-2686.0f, 0.056f, 1265.6f),
            Vec3(-2686.0f, 0.056f, 1328.0f),
            Vec3(-2784.0f, 0.056f, 1328.0f)
        )
        if (rig.buildWorldPolygonPath(path, apron)) drawPath(path, MatTerminalApron)

        // Wide flared Entry Driveway (East: X = -2715..-2701) & Exit Driveway (West: X = -2769..-2755)
        for (driveX in floatArrayOf(-2715.0f, -2701.0f, -2769.0f, -2755.0f)) {
            val line = listOf(
                Vec3(driveX - 0.16f, 0.062f, 1265.6f),
                Vec3(driveX + 0.16f, 0.062f, 1265.6f),
                Vec3(driveX + 0.16f, 0.062f, 1283.0f),
                Vec3(driveX - 0.16f, 0.062f, 1283.0f)
            )
            if (rig.buildWorldPolygonPath(path, line)) drawPath(path, MatLcWhite)
        }

        // 8 Angled Sawtooth Bus Docking Bays marked in bold yellow thermoplastic lines (Z = 1284..1303)
        for (bIdx in 0 until 8) {
            val bx = -2770.0f + bIdx * 10.2f
            val slant = 2.8f
            val leftLine = listOf(
                Vec3(bx - 0.14f, 0.062f, 1284.0f),
                Vec3(bx + 0.14f, 0.062f, 1284.0f),
                Vec3(bx + slant + 0.14f, 0.062f, 1303.0f),
                Vec3(bx + slant - 0.14f, 0.062f, 1303.0f)
            )
            if (rig.buildWorldPolygonPath(path, leftLine)) drawPath(path, MatLcYellowOchre)
        }
    }

    private fun DrawScope.drawBharathappuzhaRiverbedGround(rig: CameraRig, path: Path) {
        // Vast sunlit golden sandy Bharathappuzha (Nila River) riverbed (X = 48..330, Z = 2672..2908)
        // Starts at X = 48f so Shoranur Railway tracks at X = 12..38 stay 100% untouched!
        val sandBed = listOf(
            Vec3(48.0f, 0.032f, 2672.0f),
            Vec3(330.0f, 0.032f, 2672.0f),
            Vec3(330.0f, 0.032f, 2908.0f),
            Vec3(48.0f, 0.032f, 2908.0f)
        )
        if (rig.buildWorldPolygonPath(path, sandBed)) drawPath(path, MatNilaSandGolden)

        // Sculpted warm golden mid-river sandbars protruding between the two meandering Nila channels
        val sandbars = arrayOf(
            floatArrayOf(58f, 155f, 2768f, 2816f),
            floatArrayOf(175f, 295f, 2755f, 2804f),
            floatArrayOf(115f, 225f, 2856f, 2890f),
            floatArrayOf(65f, 165f, 2692f, 2722f)
        )
        for (sb in sandbars) {
            val bar = listOf(
                Vec3(sb[0], 0.085f, sb[2]),
                Vec3(sb[1], 0.085f, sb[2]),
                Vec3(sb[1], 0.085f, sb[3]),
                Vec3(sb[0], 0.085f, sb[3])
            )
            if (rig.buildWorldPolygonPath(path, bar)) drawPath(path, MatNilaSandWarm)
        }
    }

    // =========================================================================
    // STATIC 3D GEOMETRY CACHE (COMPILED ONCE INTO HARDWARE OPENGL ES VBO)
    // =========================================================================
    @Volatile
    private var cachedZone4Faces: List<QuadFace3D>? = null
    @Volatile
    private var cachedZone4RoadsCount: Int = -1

    fun collectAllZone4StaticFaces(
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        val existing = cachedZone4Faces
        if (existing != null && cachedZone4RoadsCount == roads.size) {
            outFaces.addAll(existing)
            return
        }
        val built = ArrayList<QuadFace3D>(8500)
        buildKoleWetlandsEmbankmentAndPosts3D(roads, built)
        buildEnamavuSluiceRegulatorBridge3D(built)
        buildMuthuvaraLevelCrossingStatic3D(built)
        buildGuruvayurTempleAndSawtoothTerminal3D(built)
        buildBharathappuzhaDualBridges3D(built)

        cachedZone4Faces = built
        cachedZone4RoadsCount = roads.size
        outFaces.addAll(built)
    }

    fun collectZone4FacesNearCamera(
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        collectAllZone4StaticFaces(roads, outFaces)
    }

    // =========================================================================
    // 1. SH 75 KOLE WETLANDS 1.5m LATERITE EMBANKMENT REVETMENTS & KM POSTS
    // =========================================================================
    private fun buildKoleWetlandsEmbankmentAndPosts3D(
        roads: List<ThrissurOsmRoad>,
        out: MutableList<QuadFace3D>
    ) {
        val sh75 = roads.firstOrNull { it.id == "ROAD_WEST_FORT_TO_KANJANI" } ?: return
        val frames = sh75.frames
        if (frames.size < 20) return

        val kmPost1 = Zone4ArchitecturalTextures.createSh75KilometerPostSpec(
            cacheKey = "ZONE4_SH75_KM_POST_4_8_V1",
            dest1Ml = "കാഞ്ഞാണി 4",
            dest2Ml = "തൃശ്ശൂർ 8"
        )
        val kmPost2 = Zone4ArchitecturalTextures.createSh75KilometerPostSpec(
            cacheKey = "ZONE4_SH75_KM_POST_2_10_V1",
            dest1Ml = "കാഞ്ഞാണി 2",
            dest2Ml = "തൃശ്ശൂർ 10"
        )

        var kmPostCounter = 0
        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            val midX = (f0.pos.x + f1.pos.x) * 0.5f
            if (midX !in KOLE_WEST_RAMP_END_X..KOLE_EAST_RAMP_START_X) continue
            // Skip Enamavu Barrage open water channel (handled by Enamavu Sluice Bridge builder)
            if (midX in (ENAMAVU_DECK_WEST_X - 8f)..(ENAMAVU_DECK_EAST_X + 8f)) continue

            val y0 = f0.pos.y
            val y1 = f1.pos.y
            if (max(y0, y1) < 0.25f) continue

            val hw0 = f0.halfWidthM + 0.45f
            val hw1 = f1.halfWidthM + 0.45f
            val slopeOut0 = hw0 + max(0.8f, y0 * 1.9f)
            val slopeOut1 = hw1 + max(0.8f, y1 * 1.9f)

            for (side in floatArrayOf(-1f, 1f)) {
                val top0 = Vec3(f0.pos.x + f0.right.x * side * hw0, y0 - 0.04f, f0.pos.z + f0.right.z * side * hw0)
                val top1 = Vec3(f1.pos.x + f1.right.x * side * hw1, y1 - 0.04f, f1.pos.z + f1.right.z * side * hw1)
                val bot0 = Vec3(f0.pos.x + f0.right.x * side * slopeOut0, 0.03f, f0.pos.z + f0.right.z * side * slopeOut0)
                val bot1 = Vec3(f1.pos.x + f1.right.x * side * slopeOut1, 0.03f, f1.pos.z + f1.right.z * side * slopeOut1)

                // Sloped laterite stone revetment protecting the 1.5m Kole road embankment
                val revCol = if (i % 2 == 0) MatLateriteRich else MatLateriteLight
                out.add(QuadFace3D(top0, top1, bot1, bot0, revCol, null, 0f))

                // Concrete shoulder guard bollards every ~30m along the elevated Kole embankment
                if (i % 3 == 0 && max(y0, y1) > 1.0f) {
                    val bx = f0.pos.x + f0.right.x * side * (hw0 + 0.35f)
                    val bz = f0.pos.z + f0.right.z * side * (hw0 + 0.35f)
                    addAxisBox(out, bx - 0.14f, y0, bz - 0.14f, bx + 0.14f, y0 + 0.55f, bz + 0.14f, MatLcWhite, MatLcWhite, MatLcWhite)
                    addAxisBox(out, bx - 0.15f, y0 + 0.55f, bz - 0.15f, bx + 0.15f, y0 + 0.72f, bz + 0.15f, MatLcRed, MatLcRed, MatLcRed)
                }
            }

            // Authentic Kerala PWD White Concrete Kilometer Post with Green Curved Top every ~160m
            if (i % 16 == 6 && y0 > 1.25f) {
                val postSpec = if (kmPostCounter % 2 == 0) kmPost1 else kmPost2
                kmPostCounter++
                val px = f0.pos.x + f0.right.x * (hw0 + 1.15f)
                val pz = f0.pos.z + f0.right.z * (hw0 + 1.15f)
                addAxisBox(out, px - 0.38f, y0 - 0.08f, pz - 0.16f, px + 0.38f, y0 + 0.72f, pz + 0.16f, MatLcWhite, MatLcWhite, MatLcWhite)
                addAxisBox(out, px - 0.38f, y0 + 0.72f, pz - 0.16f, px + 0.38f, y0 + 1.04f, pz + 0.16f, Color(0xFF047857), Color(0xFF065F46), Color(0xFF047857))
                out.add(
                    QuadFace3D(
                        Vec3(px - 0.36f, y0 + 0.04f, pz - 0.18f),
                        Vec3(px + 0.36f, y0 + 0.04f, pz - 0.18f),
                        Vec3(px + 0.36f, y0 + 1.02f, pz - 0.18f),
                        Vec3(px - 0.36f, y0 + 1.02f, pz - 0.18f),
                        MatLcWhite, null, -0.06f, signboard = postSpec
                    )
                )
            }
        }
    }

    // =========================================================================
    // 2. ENAMAVU SLUICE REGULATOR BRIDGE (MAJOR WATER BRIDGE 3 — 18 SLUICE GATES)
    //    Built directly along SH 75 at Z = 113.0f, X = -1555..-1735 (Deck Y = 3.45m)
    // =========================================================================
    private fun buildEnamavuSluiceRegulatorBridge3D(out: MutableList<QuadFace3D>) {
        val cz = ENAMAVU_CENTER_Z
        val hw = ENAMAVU_DECK_HALF_W
        val deckY = ENAMAVU_DECK_Y
        val shutterTex = Zone4ArchitecturalTextures.createSluiceGateShutterSpec()
        val bridgeSign = Zone4ArchitecturalTextures.createZone4PwdBridgeSignSpec(
            cacheKey = "ZONE4_ENAMAVU_PWD_BRIDGE_SIGN_V1",
            titleText = "ഏനാമാവ് റെഗുലേറ്റർ പാലം • ENAMAVU REGULATOR BRIDGE",
            subText = "THRISSUR KOLE WETLANDS (RAMSAR SITE) • 18 SLUICE GATES • SH 75",
            bgColorInt = 0xFF0369A1.toInt()
        )

        // A. Approach Ramp Retaining Walls + Main Barrage Deck Concrete Sidebeams & Parapets (X = -1555..-1735)
        val stepX = 6.0f
        var x0 = ENAMAVU_EAST_RAMP_START_X
        while (x0 > ENAMAVU_WEST_RAMP_END_X + 0.1f) {
            val x1 = max(x0 - stepX, ENAMAVU_WEST_RAMP_END_X)
            val y0 = computeZone4RoadSurfaceY(x0, cz)
            val y1 = computeZone4RoadSurfaceY(x1, cz)
            val deckThick = 0.65f
            val paraH = 1.05f
            val isOpenBarrage = x0 <= ENAMAVU_DECK_EAST_X + 2f && x1 >= ENAMAVU_DECK_WEST_X - 2f

            for (side in floatArrayOf(-1f, 1f)) {
                val zIn = cz + side * (hw + 0.12f)
                val zOut = cz + side * (hw + 0.52f)
                val zMin = min(zIn, zOut)
                val zMax = max(zIn, zOut)

                // Concrete crash parapet + coping rail strictly outside the 10.5m SH 75 carriageway
                out.add(QuadFace3D(Vec3(x0, y0, zIn), Vec3(x1, y1, zIn), Vec3(x1, y1 + paraH, zIn), Vec3(x0, y0 + paraH, zIn), MatBarrageConcreteLight, null, 0f))
                out.add(QuadFace3D(Vec3(x1, y1 - deckThick, zOut), Vec3(x0, y0 - deckThick, zOut), Vec3(x0, y0 + paraH, zOut), Vec3(x1, y1 + paraH, zOut), MatBarrageConcrete, null, 0f))
                out.add(QuadFace3D(Vec3(x0, y0 + paraH, zMin), Vec3(x1, y1 + paraH, zMin), Vec3(x1, y1 + paraH, zMax), Vec3(x0, y0 + paraH, zMax), MatLcWhite, null, 0f))

                // Solid weathered concrete abutment retaining walls on East/West approach ramps
                if (!isOpenBarrage) {
                    out.add(QuadFace3D(Vec3(x1, 0.04f, zOut), Vec3(x0, 0.04f, zOut), Vec3(x0, max(0.05f, y0 - deckThick), zOut), Vec3(x1, max(0.05f, y1 - deckThick), zOut), MatLateriteDark, null, 0f))
                }
            }

            // Deck soffit over the 90m barrage span
            if (isOpenBarrage) {
                out.add(
                    QuadFace3D(
                        Vec3(x0, y0 - deckThick, cz - hw - 0.5f),
                        Vec3(x0, y0 - deckThick, cz + hw + 0.5f),
                        Vec3(x1, y1 - deckThick, cz + hw + 0.5f),
                        Vec3(x1, y1 - deckThick, cz - hw - 0.5f),
                        MatBarrageConcreteDark, null, 0f
                    )
                )
            }
            x0 = x1
        }

        // B. 19 MASSIVE BARRAGE CONCRETE PIERS & 18 VERTICAL STEEL SLUICE GATES + OVERHEAD WINCH TOWERS!
        //    Span: X = -1600.0f .. -1690.0f (18 bays × 5.0m = 90.0m)
        val baySpan = (ENAMAVU_DECK_EAST_X - ENAMAVU_DECK_WEST_X) / ENAMAVU_GATE_COUNT.toFloat() // 5.0m
        val lakeSideZ = cz + hw + 2.35f // North / Lake side (strictly outside road!)
        val seaSideZ = cz - hw - 1.65f  // South / Sea side

        // Elevated Irrigation Engineers' Walkway running along the Lake Side overlooking all 18 sluice gates
        addAxisBox(
            out,
            ENAMAVU_DECK_WEST_X - 1.0f, deckY - 0.22f, cz + hw + 0.52f,
            ENAMAVU_DECK_EAST_X + 1.0f, deckY + 0.08f, cz + hw + 2.85f,
            MatBarrageConcreteLight, MatBarrageConcreteDark, MatBarrageConcrete
        )
        // Tubular industrial grey safety handrail along outer edge of engineers' walkway
        val walkOuterZ = cz + hw + 2.75f
        addAxisBox(out, ENAMAVU_DECK_WEST_X, deckY + 1.02f, walkOuterZ - 0.05f, ENAMAVU_DECK_EAST_X, deckY + 1.10f, walkOuterZ + 0.05f, MatIndustrialGrey, MatIndustrialGreyDark, MatIndustrialGrey)
        addAxisBox(out, ENAMAVU_DECK_WEST_X, deckY + 0.56f, walkOuterZ - 0.04f, ENAMAVU_DECK_EAST_X, deckY + 0.62f, walkOuterZ + 0.04f, MatIndustrialGrey, MatIndustrialGreyDark, MatIndustrialGrey)

        // Overhead continuous steel winch hoist gantry deck spanning above all 18 sluice gates (Y = deckY + 4.35m)
        addAxisBox(
            out,
            ENAMAVU_DECK_WEST_X - 0.6f, deckY + 4.25f, lakeSideZ - 0.95f,
            ENAMAVU_DECK_EAST_X + 0.6f, deckY + 4.60f, lakeSideZ + 0.95f,
            MatIndustrialGrey, MatIndustrialGreyDark, MatIndustrialSteel
        )

        for (pIdx in 0..ENAMAVU_GATE_COUNT) {
            val px = ENAMAVU_DECK_EAST_X - pIdx * baySpan
            // Thick concrete barrage pier running under roadway from Sea Side to Lake Side with cutwater noses
            addAxisBox(out, px - 0.52f, 0.02f, seaSideZ, px + 0.52f, deckY - 0.55f, lakeSideZ + 1.35f, MatBarrageConcrete, MatBarrageConcreteDark, MatBarrageConcrete)
            // Industrial-grey steel winch tower column rising from Lake-Side pier up to overhead hoist deck!
            addAxisBox(out, px - 0.28f, deckY, lakeSideZ - 0.45f, px + 0.28f, deckY + 4.95f, lakeSideZ + 0.45f, MatIndustrialGrey, MatIndustrialGreyDark, MatIndustrialSteel)
            // Walkway handrail stanchion
            addAxisBox(out, px - 0.05f, deckY + 0.08f, walkOuterZ - 0.05f, px + 0.05f, deckY + 1.12f, walkOuterZ + 0.05f, MatIndustrialGreyDark, MatIndustrialGreyDark, MatIndustrialGreyDark)

            // Build each of the 18 massive vertical steel sluice gates & mechanical winch assemblies between piers
            if (pIdx < ENAMAVU_GATE_COUNT) {
                val gx0 = px - baySpan + 0.56f
                val gx1 = px - 0.56f
                val gMidX = (gx0 + gx1) * 0.5f
                // Alternate slightly raised shutters so foaming Kole water visibly discharges beneath!
                val gateBottomY = if (pIdx % 3 == 0) 0.72f else 0.34f
                val gateTopY = gateBottomY + 2.55f

                // Heavy riveted vertical steel sluice shutter plate facing both Lake & Sea sides
                out.add(
                    QuadFace3D(
                        Vec3(gx0, gateBottomY, lakeSideZ + 0.14f),
                        Vec3(gx1, gateBottomY, lakeSideZ + 0.14f),
                        Vec3(gx1, gateTopY, lakeSideZ + 0.14f),
                        Vec3(gx0, gateTopY, lakeSideZ + 0.14f),
                        MatIndustrialGreyDark, null, -0.04f, signboard = shutterTex
                    )
                )
                out.add(
                    QuadFace3D(
                        Vec3(gx1, gateBottomY, lakeSideZ - 0.14f),
                        Vec3(gx0, gateBottomY, lakeSideZ - 0.14f),
                        Vec3(gx0, gateTopY, lakeSideZ - 0.14f),
                        Vec3(gx1, gateTopY, lakeSideZ - 0.14f),
                        MatIndustrialGreyDark, null, -0.04f, signboard = shutterTex
                    )
                )
                // Top & bottom shutter frame flange
                addAxisBox(out, gx0, gateTopY - 0.14f, lakeSideZ - 0.18f, gx1, gateTopY + 0.06f, lakeSideZ + 0.18f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)

                // Twin vertical steel lifting screw-rods rising from shutter top into the overhead mechanical winch drum!
                for (rodOff in floatArrayOf(-1.15f, 1.15f)) {
                    val rx = gMidX + rodOff
                    addAxisBox(out, rx - 0.06f, gateTopY, lakeSideZ - 0.06f, rx + 0.06f, deckY + 5.15f, lakeSideZ + 0.06f, MatRailSteel, MatIndustrialGreyDark, MatRailSteel)
                    // Counterweight pulley housing + cast-iron gear winch drum on overhead gantry
                    addAxisBox(out, rx - 0.28f, deckY + 4.55f, lakeSideZ - 0.34f, rx + 0.28f, deckY + 5.12f, lakeSideZ + 0.34f, MatIndustrialGreyDark, MatRustyIron, MatIndustrialGrey)
                }
                // Central motorized hoist gearbox & drive shaft linking both screw-rods
                addAxisBox(out, gMidX - 1.20f, deckY + 4.78f, lakeSideZ - 0.10f, gMidX + 1.20f, deckY + 4.94f, lakeSideZ + 0.10f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
                addAxisBox(out, gMidX - 0.42f, deckY + 4.60f, lakeSideZ - 0.40f, gMidX + 0.42f, deckY + 5.26f, lakeSideZ + 0.40f, MatIndustrialGrey, MatLcYellowOchre, MatIndustrialGreyDark)
            }
        }

        // C. Overhead Kerala PWD Enamavu Regulator Bridge Signboards on East & West approaches
        for (signX in floatArrayOf(ENAMAVU_EAST_RAMP_START_X + 6f, ENAMAVU_WEST_RAMP_END_X - 6f)) {
            val sy = computeZone4RoadSurfaceY(signX, cz)
            // Twin portal columns strictly outside SH 75 road (Z = cz ± 6.1m)
            for (side in floatArrayOf(-1f, 1f)) {
                val colZ = cz + side * (hw + 0.90f)
                addAxisBox(out, signX - 0.18f, sy - 0.1f, colZ - 0.18f, signX + 0.18f, sy + 6.75f, colZ + 0.18f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
            }
            addAxisBox(out, signX - 0.14f, sy + 6.35f, cz - hw - 0.90f, signX + 0.14f, sy + 6.75f, cz + hw + 0.90f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
            out.add(
                QuadFace3D(
                    Vec3(signX + 0.16f, sy + 5.55f, cz - 5.2f),
                    Vec3(signX + 0.16f, sy + 5.55f, cz + 5.2f),
                    Vec3(signX + 0.16f, sy + 7.15f, cz + 5.2f),
                    Vec3(signX + 0.16f, sy + 7.15f, cz - 5.2f),
                    Color(0xFF0369A1), null, -0.22f, signboard = bridgeSign
                )
            )
        }

        // D. Sculpted Kerala Wetland Water Birds (White Egrets & Turquoise Kingfishers perched on wooden stakes on Lake Side!)
        val birdPosts = arrayOf(
            Triple(-1612f, 128.5f, true),
            Triple(-1634f, 134.0f, false),
            Triple(-1658f, 130.5f, true),
            Triple(-1678f, 136.0f, true),
            Triple(-1622f, 146.0f, false)
        )
        for ((bx, bz, isEgret) in birdPosts) {
            // Wooden bamboo fishing stake rising out of freshwater lake
            addAxisBox(out, bx - 0.08f, 0.02f, bz - 0.08f, bx + 0.08f, 1.45f, bz + 0.08f, MatKoleMudBund, MatTeakDark, MatKoleMudBund)
            if (isEgret) {
                // White Egret silhouette (legs + oval body + graceful neck + orange beak)
                addAxisBox(out, bx - 0.16f, 1.68f, bz - 0.24f, bx + 0.16f, 1.98f, bz + 0.24f, MatEgretWhite, MatEgretWhite, MatEgretWhite)
                addAxisBox(out, bx - 0.05f, 1.96f, bz + 0.12f, bx + 0.05f, 2.34f, bz + 0.22f, MatEgretWhite, MatEgretWhite, MatEgretWhite)
                addAxisBox(out, bx - 0.04f, 2.26f, bz + 0.22f, bx + 0.04f, 2.33f, bz + 0.42f, MatLcYellowOchre, MatLcYellowOchre, MatLcYellowOchre)
            } else {
                // Bright Turquoise & Orange Kerala Kingfisher perched on stake
                addAxisBox(out, bx - 0.11f, 1.48f, bz - 0.15f, bx + 0.11f, 1.72f, bz + 0.15f, MatKingfisherBlue, MatTerracottaTemple, MatKingfisherBlue)
                addAxisBox(out, bx - 0.03f, 1.64f, bz + 0.15f, bx + 0.03f, 1.69f, bz + 0.34f, MatLcBlack, MatLcBlack, MatLcBlack)
            }
        }
    }

    // =========================================================================
    // 3. MUTHUVARA ANIMATED RAILWAY LEVEL CROSSING (LC-14 ON SH 69)
    //    Static Stanchions, Crossbucks, Yellow-Ochre Gatekeeper Cabin & 25kV OHE Masts
    // =========================================================================
    private fun buildMuthuvaraLevelCrossingStatic3D(out: MutableList<QuadFace3D>) {
        val cx = MUTHUVARA_LC_X
        val cz = MUTHUVARA_LC_Z
        val hw = MUTHUVARA_ROAD_HALF_W
        val cabinBoard = Zone4ArchitecturalTextures.createMuthuvaraCabinBoardSpec()

        // A. 4 Heavy Cast-Iron Pivot Stanchions with Counterweight Housings (strictly OUTSIDE SH 69 ±5.95m!)
        for (gz in floatArrayOf(cz - MUTHUVARA_GATE_Z_OFFSET, cz + MUTHUVARA_GATE_Z_OFFSET)) {
            for (side in floatArrayOf(-1f, 1f)) {
                val sx = cx + side * (hw + 0.65f)
                // Concrete foundation plinth with yellow/black hazard base
                addAxisBox(out, sx - 0.42f, 0.05f, gz - 0.42f, sx + 0.42f, 0.45f, gz + 0.42f, MatLcYellowOchre, MatLcBlack, MatLcYellowOchre)
                // Heavy cast-iron A-frame pivot pedestal (Y = 0.45..1.42m)
                addAxisBox(out, sx - 0.24f, 0.45f, gz - 0.28f, sx + 0.24f, 1.42f, gz + 0.28f, MatLcCastIron, MatIndustrialGreyDark, MatLcCastIron)
                // Pivot axle hub + red-painted motor/crank gearbox cap
                addAxisBox(out, sx - 0.28f, 1.05f, gz - 0.32f, sx + 0.28f, 1.32f, gz + 0.32f, MatLcRed, MatLcCastIron, MatLcRed)
                // Electric clanging warning bell dome mounted atop stanchion!
                addAxisBox(out, sx - 0.05f, 1.42f, gz - 0.05f, sx + 0.05f, 2.25f, gz + 0.05f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
                addAxisBox(out, sx - 0.18f, 2.20f, gz - 0.18f, sx + 0.18f, 2.42f, gz + 0.18f, MatTempleGold, MatTempleGoldDeep, MatTempleGold)
            }
        }

        // B. Twin St. Andrew's Cross Crossbuck Warning Posts with Dual Black Signal Visors
        //    (Positioned cleanly on left-hand approach verges for both Northbound & Southbound drivers!)
        val crossbuckPosts = arrayOf(
            Pair(cx - hw - 1.05f, cz - 7.8f), // Facing Northbound drivers
            Pair(cx + hw + 1.05f, cz + 7.8f)  // Facing Southbound drivers
        )
        for ((px, pz) in crossbuckPosts) {
            // Alternating black-and-white striped steel warning post (Y = 0.05..3.95m)
            for (seg in 0 until 8) {
                val y0 = 0.05f + seg * 0.48f
                val col = if (seg % 2 == 0) MatLcWhite else MatLcBlack
                addAxisBox(out, px - 0.09f, y0, pz - 0.09f, px + 0.09f, y0 + 0.48f, pz + 0.09f, col, col, col)
            }
            // St. Andrew's Cross ("X") diagonal blades at Y = 3.15..3.85m
            addDiagonalBeam(out, Vec3(px - 0.72f, 3.15f, pz), Vec3(px + 0.72f, 3.85f, pz), 0.10f, MatLcWhite, MatLcRed)
            addDiagonalBeam(out, Vec3(px - 0.72f, 3.85f, pz), Vec3(px + 0.72f, 3.15f, pz), 0.10f, MatLcWhite, MatLcRed)

            // Twin circular signal backing board + black sun visors at Y = 2.48m (Live flashing LEDs drawn dynamically!)
            addAxisBox(out, px - 0.62f, 2.22f, pz - 0.08f, px + 0.62f, 2.74f, pz + 0.08f, MatLcBlack, MatLcBlack, MatLcBlack)
            for (lx in floatArrayOf(px - 0.34f, px + 0.34f)) {
                addAxisBox(out, lx - 0.16f, 2.66f, pz - 0.24f, lx + 0.16f, 2.72f, pz + 0.24f, MatLcBlack, MatLcBlack, MatLcBlack)
            }
        }

        // C. Indian Railways Standard Single-Story Yellow-Ochre Gatekeeper Cabin (East side: X = cx+10.8..17.6, Z = cz+7.4..12.8)
        val cabX0 = cx + 10.8f
        val cabX1 = cx + 17.6f
        val cabZ0 = cz + 7.4f
        val cabZ1 = cz + 12.8f
        // Red brick lower plinth band (Y = 0.06..1.15m)
        addAxisBox(out, cabX0, 0.06f, cabZ0, cabX1, 1.15f, cabZ1, MatLcBrickBase, MatLcBrickBase, MatLateriteDark)
        // Classic Indian Railways yellow-ochre plastered cabin walls (Y = 1.15..3.75m)
        addAxisBox(out, cabX0, 1.15f, cabZ0, cabX1, 3.75f, cabZ1, MatLcYellowCabin, MatLcYellowCabinShade, MatLcYellowCabin)
        // Concrete overhanging flat roof slab + red fascia trim
        addAxisBox(out, cabX0 - 0.65f, 3.75f, cabZ0 - 0.75f, cabX1 + 0.65f, 4.10f, cabZ1 + 0.65f, MatBarrageConcreteLight, MatLcBrickBase, MatBarrageConcrete)
        // Recessed observation window facing the road & track + interior telephone console desk
        addAxisBox(out, cabX0 - 0.04f, 1.55f, cabZ0 + 0.9f, cabX0 + 0.04f, 2.75f, cabZ1 - 0.9f, MatGlassTinted, MatGlassTinted, MatGlassTinted)
        addAxisBox(out, cabX0 + 0.8f, 1.55f, cabZ0 - 0.04f, cabX1 - 0.8f, 2.75f, cabZ0 + 0.04f, MatGlassTinted, MatGlassTinted, MatGlassTinted)
        // Authentic Southern Railway LC-14 Muthuvara cabin nameplate on roof parapet facing SH 69
        out.add(
            QuadFace3D(
                Vec3(cabX0 - 0.68f, 3.85f, cabZ1 - 0.3f),
                Vec3(cabX0 - 0.68f, 3.85f, cabZ0 + 0.3f),
                Vec3(cabX0 - 0.68f, 4.88f, cabZ0 + 0.3f),
                Vec3(cabX0 - 0.68f, 4.88f, cabZ1 - 0.3f),
                MatLcYellowOchre, null, -0.10f, signboard = cabinBoard
            )
        )
        // Outdoor manual iron lifting crank wheel & mechanical winch pedestal right outside the cabin door!
        val crankX = cabX0 - 1.05f
        val crankZ = cabZ0 + 1.4f
        addAxisBox(out, crankX - 0.22f, 0.06f, crankZ - 0.22f, crankX + 0.22f, 1.25f, crankZ + 0.22f, MatLcCastIron, MatIndustrialGreyDark, MatLcCastIron)
        addAxisBox(out, crankX - 0.45f, 0.85f, crankZ - 0.08f, crankX + 0.45f, 1.55f, crankZ + 0.08f, MatLcRed, MatLcCastIron, MatLcRed)

        // D. 25kV AC Electrified Single-Track Galvanized Steel OHE Traction Masts along the Guruvayur Rail Line
        var mx = MUTHUVARA_TRACK_WEST_X + 20f
        while (mx <= MUTHUVARA_TRACK_EAST_X - 20f) {
            // Strictly skip road width (SH 69 is at cx ± 5.3m)
            if (abs(mx - cx) > 12.0f) {
                val mz = cz - 3.05f
                // Vertical galvanized H-beam mast
                addAxisBox(out, mx - 0.14f, 0.05f, mz - 0.14f, mx + 0.14f, 7.60f, mz + 0.14f, MatIndustrialSteel, MatIndustrialGrey, MatIndustrialSteel)
                // Cantilever arm extending over single broad-gauge track centerline (Z = cz)
                addAxisBox(out, mx - 0.07f, 6.25f, mz, mx + 0.07f, 6.40f, cz + 0.65f, MatIndustrialSteel, MatIndustrialGrey, MatIndustrialSteel)
                addDiagonalBeam(out, Vec3(mx, 7.25f, mz + 0.1f), Vec3(mx, 6.32f, cz + 0.45f), 0.05f, MatIndustrialSteel, MatIndustrialGrey)
                // Porcelain insulator drop
                addAxisBox(out, mx - 0.09f, 5.75f, cz - 0.09f, mx + 0.09f, 6.25f, cz + 0.09f, MatLateriteLight, MatLateriteDark, MatLateriteLight)
            }
            mx += 38.0f
        }
        // Continuous 25kV copper contact wire & catenary messenger wire above single track (Y = 5.72m & 6.80m)
        addAxisBox(out, MUTHUVARA_TRACK_WEST_X, 5.70f, cz - 0.03f, MUTHUVARA_TRACK_EAST_X, 5.75f, cz + 0.03f, MatCopperRoof, MatCopperRoof, MatCopperRoof)
        addAxisBox(out, MUTHUVARA_TRACK_WEST_X, 6.76f, cz - 0.03f, MUTHUVARA_TRACK_EAST_X, 6.81f, cz + 0.03f, MatIndustrialSteel, MatIndustrialSteel, MatIndustrialSteel)
    }

    /**
     * Dynamic 60 Hz 3D rendering for Muthuvara Animated Level Crossing (LC-14):
     * - 4 Twin cylindrical steel boom barriers (300mm red/white stripes) rotating 85° <-> 0°
     * - Suspended 450mm red metal STOP discs hanging beneath each arm
     * - Twin alternating 1 Hz bright red flashing LED warning heads on both St. Andrew's Cross posts
     */
    fun collectZone4DynamicFaces(
        simTimeSec: Float,
        lcPhase: MuthuvaraLcPhase,
        lcBarrierClosedFrac: Float, // 0f = OPEN (85° vertical), 1f = CLOSED (0° horizontal across SH 69)
        camEye: Vec3,
        outFaces: MutableList<QuadFace3D>
    ) {
        if (hypot(camEye.x - MUTHUVARA_LC_X, camEye.z - MUTHUVARA_LC_Z) > 340f) return

        val cx = MUTHUVARA_LC_X
        val cz = MUTHUVARA_LC_Z
        val hw = MUTHUVARA_ROAD_HALF_W

        // Smooth boom angle: 85° when fully open (0f) -> 0° horizontal when closed (1f)
        val clamped = lcBarrierClosedFrac.coerceIn(0f, 1f)
        val angleRad = ((1f - clamped) * 85.0f) * (PI.toFloat() / 180.0f)
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val armLen = hw + 0.50f // Meets cleanly at road center X = cx!
        val segCount = 10       // Bold alternating ~0.58m / 300mm-scale retroreflective red & white bands

        for (gz in floatArrayOf(cz - MUTHUVARA_GATE_Z_OFFSET, cz + MUTHUVARA_GATE_Z_OFFSET)) {
            for (side in floatArrayOf(-1f, 1f)) {
                val pivotX = cx + side * (hw + 0.65f)
                val pivotY = 1.18f
                val inwardDir = -side // Points toward road center X = cx

                // 1. Segmented Red-and-White Retroreflective Steel Boom Arm
                for (seg in 0 until segCount) {
                    val d0 = (seg.toFloat() / segCount) * armLen
                    val d1 = ((seg + 1).toFloat() / segCount) * armLen
                    val x0 = pivotX + inwardDir * d0 * cosA
                    val y0 = pivotY + d0 * sinA
                    val x1 = pivotX + inwardDir * d1 * cosA
                    val y1 = pivotY + d1 * sinA
                    val col = if (seg % 2 == 0) MatLcRed else MatLcWhite
                    addDiagonalBeam(outFaces, Vec3(x0, y0, gz), Vec3(x1, y1, gz), 0.085f, col, col)
                }

                // 2. Counterweight block extending outward opposite the boom arm
                val cwLen = 1.15f
                val cwX = pivotX - inwardDir * cwLen * cosA
                val cwY = pivotY - cwLen * sinA
                addDiagonalBeam(outFaces, Vec3(pivotX, pivotY, gz), Vec3(cwX, cwY, gz), 0.14f, MatLcCastIron, MatLcYellowOchre)

                // 3. Suspended 450mm Red Metal STOP Disc hanging cleanly at center of each driving lane!
                //    Rendered with layered crisp white outer rim + crimson face + white horizontal STOP bar (visible both sides!)
                val discDist = armLen * 0.56f
                val dx = pivotX + inwardDir * discDist * cosA
                val dy = pivotY + discDist * sinA - 0.34f
                val rOuter = 0.28f
                val rInner = 0.23f
                // Hanging steel link chain from boom arm
                addAxisBox(outFaces, dx - 0.03f, dy + rOuter - 0.02f, gz - 0.03f, dx + 0.03f, pivotY + discDist * sinA, gz + 0.03f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
                // White retroreflective outer rim plate
                addAxisBox(outFaces, dx - rOuter, dy - rOuter, gz - 0.035f, dx + rOuter, dy + rOuter, gz + 0.035f, MatLcWhite, MatLcWhite, MatLcWhite)
                // Bold Indian Railways crimson-red inner face on both North & South sides
                addAxisBox(outFaces, dx - rInner, dy - rInner, gz - 0.048f, dx + rInner, dy + rInner, gz + 0.048f, MatLcRed, MatLcRed, MatLcRed)
                // Crisp white horizontal STOP band across center of disc
                addAxisBox(outFaces, dx - 0.17f, dy - 0.045f, gz - 0.056f, dx + 0.17f, dy + 0.045f, gz + 0.056f, MatLcWhite, MatLcWhite, MatLcWhite)
            }
        }

        // 4. Twin Alternating 1 Hz Flashing Red LED Warning Signals on both St. Andrew's Cross posts!
        val warningActive = lcPhase != MuthuvaraLcPhase.STATE_OPEN
        val flashParity = ((simTimeSec * 2.0f).toInt() and 1) == 0 // 1 Hz full left-right alternation!
        val leftCol = if (warningActive && flashParity) MatLcLedOn else MatLcLedOff
        val rightCol = if (warningActive && !flashParity) MatLcLedOn else MatLcLedOff

        val crossbuckPosts = arrayOf(
            Pair(cx - hw - 1.05f, cz - 7.8f),
            Pair(cx + hw + 1.05f, cz + 7.8f)
        )
        for ((px, pz) in crossbuckPosts) {
            addAxisBox(outFaces, px - 0.48f, 2.34f, pz - 0.12f, px - 0.20f, 2.62f, pz + 0.12f, leftCol, leftCol, leftCol)
            addAxisBox(outFaces, px + 0.20f, 2.34f, pz - 0.12f, px + 0.48f, 2.62f, pz + 0.12f, rightCol, rightCol, rightCol)
        }
    }

    // =========================================================================
    // 4. GURUVAYUR SREE KRISHNA TEMPLE EAST NADA & SAWTOOTH BUS TERMINAL
    //    Replaces old boxy placeholder with ultra-realistic Kerala temple & terminal!
    // =========================================================================
    private fun buildGuruvayurTempleAndSawtoothTerminal3D(out: MutableList<QuadFace3D>) {
        val copperRoofTex = Zone4ArchitecturalTextures.createGuruvayurCopperRoofSpec()
        val nadaPlaqueTex = Zone4ArchitecturalTextures.createGuruvayurEastNadaPlaqueSpec()
        val terminalFasciaTex = Zone4ArchitecturalTextures.createGuruvayurTerminalFasciaSpec()
        val terracottaTex = Zone3ArchitecturalTextures.createTerracottaTileRoofSpec()

        // ---- A. GURUVAYUR SREE KRISHNA TEMPLE (EAST NADA — X = -2628..-2554, Z = 1234..1286) ----
        // Traditional Kerala Laterite & Whitewashed Chuttumathil Compound Wall with Sloped Terracotta Coping
        addAxisBox(out, -2620f, 0.06f, 1234.5f, -2554f, 3.85f, 1236.2f, MatTerracottaTemple, MatKeralaWhitewash, MatKeralaWhitewash)
        addAxisBox(out, -2620f, 0.06f, 1283.8f, -2554f, 3.85f, 1285.5f, MatTerracottaTemple, MatKeralaWhitewash, MatKeralaWhitewash)
        addAxisBox(out, -2555.8f, 0.06f, 1234.5f, -2554f, 3.85f, 1285.5f, MatTerracottaTemple, MatKeralaWhitewash, MatKeralaWhitewash)
        // West-facing East Nada front compound walls flanking the grand Gopuram gateway
        addAxisBox(out, -2620f, 0.06f, 1234.5f, -2618.2f, 3.85f, 1250.5f, MatTerracottaTemple, MatKeralaWhitewash, MatKeralaWhitewash)
        addAxisBox(out, -2620f, 0.06f, 1269.5f, -2618.2f, 3.85f, 1285.5f, MatTerracottaTemple, MatKeralaWhitewash, MatKeralaWhitewash)
        // Brass Chuttuvilakku oil-lamp timber frames glowing along front wall
        addAxisBox(out, -2620.3f, 1.55f, 1235.5f, -2619.9f, 2.55f, 1250.0f, MatTempleGold, MatTempleGoldDeep, MatTempleGold)
        addAxisBox(out, -2620.3f, 1.55f, 1270.0f, -2619.9f, 2.55f, 1284.5f, MatTempleGold, MatTempleGoldDeep, MatTempleGold)

        // Iconic Multi-Tier Kerala East Nada Gopuram (X = -2621..-2610, Z = 1249.5..1270.5)
        val gx = -2615.5f
        val gz = 1260.0f
        // Ground-floor twin laterite/whitewash carved gateway piers leaving wide ceremonial portal
        addAxisBox(out, gx - 5.2f, 0.06f, gz - 10.2f, gx + 5.2f, 5.4f, gz - 4.2f, MatTeakDark, MatKeralaWhitewash, MatKeralaCream)
        addAxisBox(out, gx - 5.2f, 0.06f, gz + 4.2f, gx + 5.2f, 5.4f, gz + 10.2f, MatTeakDark, MatKeralaWhitewash, MatKeralaCream)
        addAxisBox(out, gx - 5.2f, 4.4f, gz - 4.2f, gx + 5.2f, 5.6f, gz + 4.2f, MatTeakDark, MatTeakMid, MatTeakDark)
        // Ceremonial Maroon-and-Gold Guruvayur East Nada Arch Plaque above gateway
        out.add(
            QuadFace3D(
                Vec3(gx - 5.32f, 4.45f, gz + 6.8f),
                Vec3(gx - 5.32f, 4.45f, gz - 6.8f),
                Vec3(gx - 5.32f, 5.85f, gz - 6.8f),
                Vec3(gx - 5.32f, 5.85f, gz + 6.8f),
                Color(0xFF7F1D1D), null, -0.16f, signboard = nadaPlaqueTex
            )
        )
        // Tier 1 Flared Kerala Copper-and-Terracotta Hipped Gopuram Roof (Y = 5.4..9.0m)
        addFourSidedTempleRoof(out, gx, gz, 6.8f, 12.2f, 5.4f, 4.4f, 8.8f, 9.0f, copperRoofTex)
        // Tier 2 Carved Teakwood Upper Gallery Storey (Y = 9.0..11.8m)
        addAxisBox(out, gx - 4.1f, 9.0f, gz - 8.2f, gx + 4.1f, 11.8f, gz + 8.2f, MatTeakDark, MatTeakMid, MatTeakDark)
        // Tier 2 Steep Upper Copper-Clad Gopuram Roof (Y = 11.7..16.4m)
        addFourSidedTempleRoof(out, gx, gz, 5.8f, 10.2f, 11.7f, 0.55f, 5.6f, 16.4f, copperRoofTex)
        // Gleaming Golden Brass Ridge + 3 Golden Stupikas (Thazhikakudams) along Gopuram crest!
        addAxisBox(out, gx - 0.45f, 16.3f, gz - 5.8f, gx + 0.45f, 16.75f, gz + 5.8f, MatTempleGoldBright, MatTempleGold, MatTempleGoldDeep)
        for (sz in floatArrayOf(gz - 3.8f, gz, gz + 3.8f)) {
            addOctagonalColumn(out, gx, sz, 16.75f, 18.65f, 0.36f, 0.08f, MatTempleGoldBright, MatTempleGoldDeep)
        }

        // 33-Meter Tall Gold-Plated Dwajasthambham (Flagstaff) in front of East Nada (X = -2625.5, Z = 1260.0)
        val flagX = -2625.5f
        addAxisBox(out, flagX - 1.45f, 0.06f, gz - 1.45f, flagX + 1.45f, 1.35f, gz + 1.45f, MatGraniteBorder, MatKeralaWhitewash, MatGranitePlaza)
        addAxisBox(out, flagX - 1.05f, 1.35f, gz - 1.05f, flagX + 1.05f, 2.25f, gz + 1.05f, MatTempleGoldDeep, MatTempleGold, MatTempleGoldDeep)
        addOctagonalColumn(out, flagX, gz, 2.25f, 33.0f, 0.46f, 0.22f, MatTempleGoldBright, MatTempleGold)
        // Ornate Golden Garuda Finial Cap at Y = 33.0m
        addAxisBox(out, flagX - 0.65f, 32.8f, gz - 0.65f, flagX + 0.65f, 33.45f, gz + 0.65f, MatTempleGoldBright, MatTempleGoldDeep, MatTempleGoldBright)

        // Twin Iconic Multi-Tiered Blazing Brass Deepastambhams (13 Concentric Lamp Rings with Warm Flames!)
        for (dz in floatArrayOf(gz - 5.4f, gz + 5.4f)) {
            val dx = -2627.8f
            addAxisBox(out, dx - 1.15f, 0.06f, dz - 1.15f, dx + 1.15f, 0.95f, dz + 1.15f, MatGraniteBorder, MatGranitePlaza, MatGraniteBorder)
            addOctagonalColumn(out, dx, dz, 0.95f, 10.6f, 0.34f, 0.14f, MatTempleGold, MatTempleGoldDeep)
            // 13 circular projecting brass oil-lamp receptacle tiers tapering upward!
            for (tier in 0 until 13) {
                val ty = 1.65f + tier * 0.68f
                val tr = 1.18f - tier * 0.062f
                addOctagonalColumn(out, dx, dz, ty, ty + 0.16f, tr, tr * 0.92f, MatTempleGoldBright, MatTempleGoldDeep)
                // Glowing golden cotton-wick oil flames ringing the outer lip!
                addOctagonalColumn(out, dx, dz, ty + 0.16f, ty + 0.26f, tr * 0.94f, tr * 0.85f, MatFlameWarm, MatFlameWarm)
            }
        }

        // Twin Ceremonial Life-Size Sculpted Kerala Elephant Statues (Guruvayur Kesavan tribute with Golden Nettipattam!)
        for (ez in floatArrayOf(gz - 12.2f, gz + 12.2f)) {
            addSculptedKeralaElephantStatue(out, -2625.5f, ez)
        }

        // Inner Circular Sanctum Sanctorum (Vatta Sreekovil) with Gleaming Copper & Gold Conical Roof
        val sx = -2586.0f
        addOctagonalColumn(out, sx, gz, 0.06f, 5.2f, 9.2f, 9.2f, MatKeralaWhitewash, MatKeralaCream)
        addOctagonalColumn(out, sx, gz, 5.1f, 11.8f, 10.6f, 1.1f, MatTempleGold, MatCopperRoof)
        addOctagonalColumn(out, sx, gz, 11.8f, 14.4f, 0.55f, 0.10f, MatTempleGoldBright, MatTempleGoldDeep)

        // ---- B. GURUVAYUR BUS TERMINAL (SAWTOOTH PILGRIMAGE HUB: X = -2784..-2686, Z = 1266..1328) ----
        // Perimeter boundary wall strictly OUTSIDE wide Left Entry (-2715..-2701) & Right Exit (-2769..-2755)
        addAxisBox(out, -2784.0f, 0.06f, 1266.0f, -2769.5f, 1.45f, 1267.0f, MatKeralaWhitewash, MatLateriteRich, MatKeralaWhitewash)
        addAxisBox(out, -2754.5f, 0.06f, 1266.0f, -2715.5f, 1.45f, 1267.0f, MatKeralaWhitewash, MatLateriteRich, MatKeralaWhitewash)
        addAxisBox(out, -2700.5f, 0.06f, 1266.0f, -2686.0f, 1.45f, 1267.0f, MatKeralaWhitewash, MatLateriteRich, MatKeralaWhitewash)

        // Entry & Exit Portal Gate Pillars (terracotta & cream architectural gateposts)
        for (px in floatArrayOf(-2769.5f, -2754.5f, -2715.5f, -2700.5f)) {
            addAxisBox(out, px - 0.45f, 0.06f, 1265.7f, px + 0.45f, 5.4f, 1267.3f, MatTerracottaTemple, MatKeralaCream, MatTerracottaDark)
            addAxisBox(out, px - 0.65f, 5.4f, 1265.5f, px + 0.65f, 5.9f, 1267.5f, MatTerracottaDark, MatTerracottaTemple, MatTerracottaDark)
        }
        // Main Overhead Terminal Fascia Archway spanning the front plaza
        addAxisBox(out, -2770.0f, 5.35f, 1265.8f, -2700.0f, 6.25f, 1267.2f, MatTerminalBlue, MatTerminalRoofBlue, MatTerminalBlue)
        out.add(
            QuadFace3D(
                Vec3(-2760.0f, 5.38f, 1265.65f),
                Vec3(-2710.0f, 5.38f, 1265.65f),
                Vec3(-2710.0f, 7.18f, 1265.65f),
                Vec3(-2760.0f, 7.18f, 1265.65f),
                MatTerminalBlue, null, -0.18f, signboard = terminalFasciaTex
            )
        )

        // Raised Sawtooth Passenger Boarding Platform (Z = 1303.5..1314.0) with Angled Sawtooth Concrete Noses!
        addAxisBox(out, -2778.0f, 0.06f, 1303.5f, -2692.0f, 0.36f, 1314.0f, MatTerminalPlatform, MatGraniteBorder, MatTerminalPlatform)
        addAxisBox(out, -2778.0f, 0.30f, 1303.4f, -2692.0f, 0.38f, 1303.9f, MatLcYellowOchre, MatLcYellowOchre, MatLcYellowOchre)

        // Heavy Terracotta-Red Tubular Steel Pillars + Pitched Blue & Terracotta Passenger Waiting Shed Canopy!
        val baySpecs = arrayOf(
            Zone4ArchitecturalTextures.createBusBaySignSpec("ZONE4_GVY_BAY1", "BAY 1", "THRISSUR / ERNAKULAM"),
            Zone4ArchitecturalTextures.createBusBaySignSpec("ZONE4_GVY_BAY3", "BAY 3", "KOZHIKODE / KANNUR"),
            Zone4ArchitecturalTextures.createBusBaySignSpec("ZONE4_GVY_BAY5", "BAY 5", "PALAKKAD / COIMBATORE"),
            Zone4ArchitecturalTextures.createBusBaySignSpec("ZONE4_GVY_BAY7", "BAY 7", "CHAVAKKAD / KODUNGALLUR")
        )
        for (cIdx in 0..7) {
            val cxCol = -2774.0f + cIdx * 11.4f
            // Terracotta-red structural columns
            addAxisBox(out, cxCol - 0.22f, 0.36f, 1304.8f, cxCol + 0.22f, 5.65f, 1305.24f, MatTerracottaTemple, MatTerracottaDark, MatTerracottaTemple)
            addAxisBox(out, cxCol - 0.22f, 0.36f, 1312.8f, cxCol + 0.22f, 5.65f, 1313.24f, MatTerracottaTemple, MatTerracottaDark, MatTerracottaTemple)
            // Concrete passenger seating benches between columns
            if (cIdx < 7) {
                addAxisBox(out, cxCol + 2.2f, 0.36f, 1308.5f, cxCol + 8.8f, 0.88f, 1309.6f, MatTeakMid, MatGraniteBorder, MatTeakDark)
            }
            // Hanging enamel destination bay signboards beneath canopy truss
            if (cIdx % 2 == 0 && cIdx / 2 < baySpecs.size) {
                val signX = cxCol + 5.7f
                out.add(
                    QuadFace3D(
                        Vec3(signX - 2.6f, 4.35f, 1304.2f),
                        Vec3(signX + 2.6f, 4.35f, 1304.2f),
                        Vec3(signX + 2.6f, 5.25f, 1304.2f),
                        Vec3(signX - 2.6f, 5.25f, 1304.2f),
                        MatTerminalRoofBlue, null, -0.14f, signboard = baySpecs[cIdx / 2]
                    )
                )
            }
        }
        // Long Pitched Terminal Shed Roof (Royal Blue corrugated steel deck + translucent skylight strips + terracotta trim)
        addAxisBox(out, -2777.0f, 5.55f, 1296.0f, -2693.0f, 6.05f, 1314.5f, MatTerminalRoofBlue, MatTerminalBlue, MatTerminalBlue)
        addFourSidedTempleRoof(out, -2735.0f, 1305.2f, 42.5f, 9.6f, 6.00f, 39.5f, 0.8f, 8.25f, terracottaTex)

        // Terminal Concourse, KSRTC Enquiry & Pilgrims Luggage Counter Building behind platform (Z = 1314.5..1326.5)
        addAxisBox(out, -2778.0f, 0.06f, 1314.5f, -2692.0f, 7.60f, 1326.5f, MatKeralaCream, MatKeralaWhitewash, MatKeralaCream)
        addAxisBox(out, -2779.0f, 7.60f, 1313.8f, -2691.0f, 8.35f, 1327.2f, MatTerracottaTemple, MatTerracottaDark, MatTerracottaTemple)
        // Panoramic tinted glass concourse windows & luggage counter grilles
        for (wx in floatArrayOf(-2768f, -2750f, -2732f, -2714f)) {
            addAxisBox(out, wx, 1.35f, 1314.38f, wx + 12.5f, 3.35f, 1314.55f, MatGlassTinted, MatGlassTinted, MatGlassTinted)
            addAxisBox(out, wx, 4.55f, 1314.38f, wx + 12.5f, 6.45f, 1314.55f, MatGlassTinted, MatGlassTinted, MatGlassTinted)
        }
    }

    /**
     * Sculpted life-size Kerala temple ceremonial elephant statue (Guruvayur Kesavan style)
     * with raised trunk, ivory tusks, and glittering golden Nettipattam forehead caparison!
     */
    private fun addSculptedKeralaElephantStatue(out: MutableList<QuadFace3D>, cx: Float, cz: Float) {
        // Granite display plinth
        addAxisBox(out, cx - 2.1f, 0.06f, cz - 1.35f, cx + 2.1f, 0.72f, cz + 1.35f, MatGraniteBorder, MatGranitePlaza, MatGraniteBorder)
        // 4 sturdy elephant legs
        for (lx in floatArrayOf(cx - 1.05f, cx + 0.95f)) {
            for (lz in floatArrayOf(cz - 0.55f, cz + 0.55f)) {
                addAxisBox(out, lx - 0.24f, 0.72f, lz - 0.24f, lx + 0.24f, 1.95f, lz + 0.24f, MatElephantDark, MatElephantGrey, MatElephantDark)
            }
        }
        // Sculpted elephant barrel body + arched back
        addAxisBox(out, cx - 1.35f, 1.85f, cz - 0.85f, cx + 1.35f, 3.25f, cz + 0.85f, MatElephantGrey, MatElephantDark, MatElephantGrey)
        // Broad domed head facing West (-X toward East Nada approach)
        addAxisBox(out, cx - 2.05f, 2.25f, cz - 0.72f, cx - 1.10f, 3.52f, cz + 0.72f, MatElephantGrey, MatElephantDark, MatElephantGrey)
        // Flared ears
        addAxisBox(out, cx - 1.55f, 2.25f, cz - 1.08f, cx - 1.25f, 3.35f, cz - 0.70f, MatElephantDark, MatElephantGrey, MatElephantDark)
        addAxisBox(out, cx - 1.55f, 2.25f, cz + 0.70f, cx - 1.25f, 3.35f, cz + 1.08f, MatElephantDark, MatElephantGrey, MatElephantDark)
        // Glittering Golden Nettipattam forehead caparison drape!
        addAxisBox(out, cx - 2.12f, 2.18f, cz - 0.58f, cx - 2.04f, 3.48f, cz + 0.58f, MatTempleGoldBright, MatTempleGold, MatTempleGoldDeep)
        // Curved ivory tusks extending forward (-X)
        addDiagonalBeam(out, Vec3(cx - 1.95f, 2.35f, cz - 0.34f), Vec3(cx - 2.65f, 2.75f, cz - 0.42f), 0.08f, MatIvory, MatIvory)
        addDiagonalBeam(out, Vec3(cx - 1.95f, 2.35f, cz + 0.34f), Vec3(cx - 2.65f, 2.75f, cz + 0.42f), 0.08f, MatIvory, MatIvory)
        // Majestic curved trunk reaching forward and slightly upward
        addDiagonalBeam(out, Vec3(cx - 2.05f, 2.48f, cz), Vec3(cx - 2.45f, 1.55f, cz), 0.16f, MatElephantGrey, MatElephantDark)
        addDiagonalBeam(out, Vec3(cx - 2.45f, 1.55f, cz), Vec3(cx - 2.68f, 1.88f, cz), 0.12f, MatElephantGrey, MatElephantDark)
    }

    // =========================================================================
    // 5. BHARATHAPPUZHA (NILA RIVER) DUAL BRIDGES (NORTHERN BOUNDARY BRIDGE 4)
    //    - Modern 2-Lane Prestressed Concrete Roadway Bridge on SH 22 (X = 100.0)
    //    - Parallel Historic Cochin Bridge (1902) Multi-Span Steel Lattice Truss (X = 123.5)
    //    - Vast Sandy Riverbed & Tall Elephant Grass Clusters
    // =========================================================================
    private fun buildBharathappuzhaDualBridges3D(out: MutableList<QuadFace3D>) {
        val cx = BHARATHAPPUZHA_CENTER_X
        val hw = BHARATHAPPUZHA_DECK_HALF_W
        val deckY = BHARATHAPPUZHA_DECK_Y
        val riverSign = Zone4ArchitecturalTextures.createZone4PwdBridgeSignSpec(
            cacheKey = "ZONE4_BHARATHAPPUZHA_PWD_SIGN_V1",
            titleText = "ഭാരതപ്പുഴ (നിള) • BHARATHAPPUZHA RIVER",
            subText = "CHERUTHURUTHY – SHORANUR DUAL BRIDGES • SH 22 NORTHERN BOUNDARY",
            bgColorInt = 0xFF0369A1.toInt()
        )
        val cochin1902Plaque = Zone4ArchitecturalTextures.createCochinBridge1902PlaqueSpec()

        // ---- A. MODERN 2-LANE PRESTRESSED CONCRETE ROADWAY BRIDGE (X = 100.0, Z = 2630..2950) ----
        val stepZ = 8.0f
        var z0 = BHARATHAPPUZHA_RAMP_SOUTH_Z
        while (z0 < BHARATHAPPUZHA_RAMP_NORTH_Z - 0.1f) {
            val z1 = min(z0 + stepZ, BHARATHAPPUZHA_RAMP_NORTH_Z)
            val y0 = computeZone4RoadSurfaceY(cx, z0)
            val y1 = computeZone4RoadSurfaceY(cx, z1)
            val girderDepth = 1.25f
            val paraH = 1.12f
            val isOpenRiver = z0 >= BHARATHAPPUZHA_DECK_SOUTH_Z - 4f && z1 <= BHARATHAPPUZHA_DECK_NORTH_Z + 4f

            for (side in floatArrayOf(-1f, 1f)) {
                val walkIn = cx + side * (hw + 0.08f)
                val walkOut = cx + side * (hw + 1.35f)
                val paraOut = cx + side * (hw + 1.68f)
                val xMinW = min(walkIn, walkOut)
                val xMaxW = max(walkIn, walkOut)
                val xMinP = min(walkOut, paraOut)
                val xMaxP = max(walkOut, paraOut)

                // Raised concrete pedestrian walkway flanking both sides of the 2-lane carriageway
                out.add(QuadFace3D(Vec3(xMinW, y0 + 0.18f, z0), Vec3(xMaxW, y0 + 0.18f, z0), Vec3(xMaxW, y1 + 0.18f, z1), Vec3(xMinW, y1 + 0.18f, z1), MatBarrageConcreteLight, null, 0f))
                out.add(QuadFace3D(Vec3(walkIn, y0, z0), Vec3(walkIn, y1, z1), Vec3(walkIn, y1 + 0.18f, z1), Vec3(walkIn, y0 + 0.18f, z0), MatLcYellowOchre, null, 0f))

                // White concrete bridge crash parapet + galvanized steel top handrail
                out.add(QuadFace3D(Vec3(walkOut, y0 + 0.18f, z0), Vec3(walkOut, y1 + 0.18f, z1), Vec3(walkOut, y1 + paraH, z1), Vec3(walkOut, y0 + paraH, z0), MatLcWhite, null, 0f))
                out.add(QuadFace3D(Vec3(paraOut, y0 - girderDepth, z0), Vec3(paraOut, y1 - girderDepth, z1), Vec3(paraOut, y1 + paraH, z1), Vec3(paraOut, y0 + paraH, z0), MatBarrageConcrete, null, 0f))
                out.add(QuadFace3D(Vec3(xMinP, y0 + paraH, z0), Vec3(xMaxP, y0 + paraH, z0), Vec3(xMaxP, y1 + paraH, z1), Vec3(xMinP, y1 + paraH, z1), MatLcWhite, null, 0f))

                // Solid concrete approach embankment walls on South/North ramps
                if (!isOpenRiver && max(y0, y1) > 0.40f) {
                    out.add(QuadFace3D(Vec3(paraOut, 0.04f, z0), Vec3(paraOut, 0.04f, z1), Vec3(paraOut, max(0.05f, y1 - girderDepth), z1), Vec3(paraOut, max(0.05f, y0 - girderDepth), z0), MatBarrageConcreteDark, null, 0f))
                }
            }

            // Prestressed concrete I-girder soffit over the 200m river span
            if (isOpenRiver) {
                out.add(
                    QuadFace3D(
                        Vec3(cx - hw - 1.6f, y0 - girderDepth, z0),
                        Vec3(cx + hw + 1.6f, y0 - girderDepth, z0),
                        Vec3(cx + hw + 1.6f, y1 - girderDepth, z1),
                        Vec3(cx - hw - 1.6f, y1 - girderDepth, z1),
                        MatBarrageConcreteDark, null, 0f
                    )
                )
            }
            z0 = z1
        }

        // 7 Pairs of Solid Elliptical Concrete Piers supporting the Modern Roadway Bridge across the sandy Nila riverbed
        val pierCount = 7
        for (p in 0 until pierCount) {
            val pz = BHARATHAPPUZHA_DECK_SOUTH_Z + p * (200.0f / (pierCount - 1))
            // Pier cap hammerhead beam
            addAxisBox(out, cx - hw - 1.2f, deckY - 2.05f, pz - 1.10f, cx + hw + 1.2f, deckY - 1.20f, pz + 1.10f, MatBarrageConcrete, MatBarrageConcreteDark, MatBarrageConcrete)
            // Twin solid cylindrical piers founded on deep caissons in the golden sand
            for (px in floatArrayOf(cx - 3.6f, cx + 3.6f)) {
                addOctagonalColumn(out, px, pz, 0.02f, deckY - 2.05f, 1.05f, 0.90f, MatBarrageConcrete, MatBarrageConcreteDark)
                addOctagonalColumn(out, px, pz, 0.02f, 0.85f, 1.38f, 1.22f, MatBarrageConcreteDark, MatBarrageConcreteDark)
            }
        }

        // Bridge Streetlights along Modern Bridge Parapets + Blue PWD River Portals on Cheruthuruthy & Shoranur Ends
        for (lz in floatArrayOf(2665f, 2705f, 2745f, 2785f, 2825f, 2865f, 2915f)) {
            val ly = computeZone4RoadSurfaceY(cx, lz) + 1.12f
            for (side in floatArrayOf(-1f, 1f)) {
                val px = cx + side * (hw + 1.50f)
                addAxisBox(out, px - 0.07f, ly, lz - 0.07f, px + 0.07f, ly + 4.8f, lz + 0.07f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
                val armEnd = px - side * 1.45f
                addAxisBox(out, min(px, armEnd), ly + 4.68f, lz - 0.06f, max(px, armEnd), ly + 4.80f, lz + 0.06f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
                addAxisBox(out, armEnd - 0.22f, ly + 4.56f, lz - 0.14f, armEnd + 0.22f, ly + 4.68f, lz + 0.14f, MatFlameWarm, MatFlameWarm, MatFlameWarm)
            }
        }
        for (portalZ in floatArrayOf(BHARATHAPPUZHA_RAMP_SOUTH_Z - 6f, BHARATHAPPUZHA_RAMP_NORTH_Z + 6f)) {
            for (side in floatArrayOf(-1f, 1f)) {
                val colX = cx + side * (hw + 1.25f)
                addAxisBox(out, colX - 0.18f, 0.05f, portalZ - 0.18f, colX + 0.18f, 6.95f, portalZ + 0.18f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
            }
            addAxisBox(out, cx - hw - 1.25f, 6.45f, portalZ - 0.16f, cx + hw + 1.25f, 6.95f, portalZ + 0.16f, MatIndustrialSteel, MatIndustrialGreyDark, MatIndustrialSteel)
            out.add(
                QuadFace3D(
                    Vec3(cx - 5.4f, 5.65f, portalZ - 0.18f),
                    Vec3(cx + 5.4f, 5.65f, portalZ - 0.18f),
                    Vec3(cx + 5.4f, 7.25f, portalZ - 0.18f),
                    Vec3(cx - 5.4f, 7.25f, portalZ - 0.18f),
                    Color(0xFF0369A1), null, -0.22f, signboard = riverSign
                )
            )
        }

        // ---- B. THE HISTORIC COCHIN BRIDGE (1902) — PARALLEL AT X = 123.5f (23.5m EAST!) ----
        //         Classic British-Era 6-Span Red-Oxide & Black Steel Lattice Truss on Heavy Cut-Stone Masonry Piers!
        val hx = COCHIN_1902_CENTER_X
        val hHalfW = 3.55f
        val hDeckY = 5.40f
        val trussH = 4.95f
        val numSpans = 6
        val spanLen = 200.0f / numSpans.toFloat() // 33.33m per historic riveted truss span!

        // South & North historic stone masonry approach abutments
        addAxisBox(out, hx - hHalfW - 0.8f, 0.02f, 2662.0f, hx + hHalfW + 0.8f, hDeckY, 2690.5f, MatCutStoneGranite, MatCutStoneDark, MatCutStoneGranite)
        addAxisBox(out, hx - hHalfW - 0.8f, 0.02f, 2889.5f, hx + hHalfW + 0.8f, hDeckY, 2918.0f, MatCutStoneGranite, MatCutStoneDark, MatCutStoneGranite)

        // 7 Heavy Cut-Stone Granite Masonry Piers with Pointed Upstream/Downstream Cutwater Noses!
        for (p in 0..numSpans) {
            val pz = BHARATHAPPUZHA_DECK_SOUTH_Z + p * spanLen
            // Heavy granite masonry pier body (X = hx ± 4.6m, Z = pz ± 1.45m)
            addAxisBox(out, hx - 4.6f, 0.02f, pz - 1.45f, hx + 4.6f, hDeckY - 0.35f, pz + 1.45f, MatCutStoneGranite, MatCutStoneDark, MatCutStoneGranite)
            // Stepped granite pedestal base collar & stone coping cap
            addAxisBox(out, hx - 5.2f, 0.02f, pz - 1.85f, hx + 5.2f, 0.95f, pz + 1.85f, MatCutStoneDark, MatCutStoneDark, MatCutStoneGranite)
            addAxisBox(out, hx - 4.85f, hDeckY - 0.42f, pz - 1.62f, hx + 4.85f, hDeckY - 0.08f, pz + 1.62f, MatBarrageConcrete, MatCutStoneDark, MatCutStoneGranite)
        }

        // 6 Multi-Span British-Era Red-Oxide & Black Steel Lattice Truss Superstructures!
        for (s in 0 until numSpans) {
            val sz0 = BHARATHAPPUZHA_DECK_SOUTH_Z + s * spanLen + 0.6f
            val sz1 = BHARATHAPPUZHA_DECK_SOUTH_Z + (s + 1) * spanLen - 0.6f
            val panelCount = 6
            val panelLen = (sz1 - sz0) / panelCount.toFloat()

            // Weathered heritage timber-and-steel bridge deck floor stringers
            addAxisBox(out, hx - hHalfW, hDeckY - 0.32f, sz0, hx + hHalfW, hDeckY + 0.04f, sz1, MatCochinBlackSteel, MatCochinRedOxideDark, MatCochinBlackSteel)

            for (side in floatArrayOf(-1f, 1f)) {
                val tx = hx + side * hHalfW
                // Heavy riveted bottom chord & top chord box beams (red-oxide & black steel!)
                addAxisBox(out, tx - 0.24f, hDeckY - 0.10f, sz0, tx + 0.24f, hDeckY + 0.38f, sz1, MatCochinRedOxide, MatCochinBlackSteel, MatCochinRedOxideDark)
                addAxisBox(out, tx - 0.24f, hDeckY + trussH - 0.36f, sz0, tx + 0.24f, hDeckY + trussH + 0.14f, sz1, MatCochinRedOxide, MatCochinBlackSteel, MatCochinRedOxideDark)

                // Lattice vertical posts & crisp Warren/Pratt X-braced diagonal steel struts!
                for (k in 0..panelCount) {
                    val kz = sz0 + k * panelLen
                    addAxisBox(out, tx - 0.16f, hDeckY + 0.25f, kz - 0.16f, tx + 0.16f, hDeckY + trussH - 0.25f, kz + 0.16f, MatCochinBlackSteel, MatCochinRedOxideDark, MatCochinBlackSteel)
                    if (k < panelCount) {
                        val kzNext = kz + panelLen
                        // Rising + falling steel lattice diagonals forming unmistakable 1902 British ironwork X-panels!
                        addDiagonalBeam(out, Vec3(tx, hDeckY + 0.30f, kz), Vec3(tx, hDeckY + trussH - 0.30f, kzNext), 0.11f, MatCochinRedOxide, MatCochinRedOxideDark)
                        addDiagonalBeam(out, Vec3(tx, hDeckY + trussH - 0.30f, kz), Vec3(tx, hDeckY + 0.30f, kzNext), 0.10f, MatCochinBlackSteel, MatCochinRedOxideDark)
                    }
                }
            }

            // Overhead transverse steel portal cross-bracing connecting West & East trusses
            for (k in 0..panelCount step 2) {
                val kz = sz0 + k * panelLen
                addAxisBox(out, hx - hHalfW, hDeckY + trussH - 0.26f, kz - 0.16f, hx + hHalfW, hDeckY + trussH + 0.10f, kz + 0.16f, MatCochinBlackSteel, MatCochinRedOxide, MatCochinBlackSteel)
            }
        }

        // Heritage Cast-Iron Plaque on South Portal of the 1902 Cochin Bridge facing Cheruthuruthy/SH 22 drivers!
        out.add(
            QuadFace3D(
                Vec3(hx - 3.3f, hDeckY + trussH - 1.25f, BHARATHAPPUZHA_DECK_SOUTH_Z + 0.4f),
                Vec3(hx + 3.3f, hDeckY + trussH - 1.25f, BHARATHAPPUZHA_DECK_SOUTH_Z + 0.4f),
                Vec3(hx + 3.3f, hDeckY + trussH + 0.35f, BHARATHAPPUZHA_DECK_SOUTH_Z + 0.4f),
                Vec3(hx - 3.3f, hDeckY + trussH + 0.35f, BHARATHAPPUZHA_DECK_SOUTH_Z + 0.4f),
                MatCochinBlackSteel, null, -0.18f, signboard = cochin1902Plaque
            )
        )

        // ---- C. TALL ELEPHANT GRASS CLUSTERS ON GOLDEN SANDBARS & RIVERBANKS ----
        val grassClumps = arrayOf(
            Pair(72f, 2705f), Pair(84f, 2786f), Pair(142f, 2792f), Pair(164f, 2776f),
            Pair(192f, 2768f), Pair(224f, 2784f), Pair(148f, 2872f), Pair(184f, 2868f),
            Pair(68f, 2882f), Pair(158f, 2702f), Pair(210f, 2708f), Pair(255f, 2865f)
        )
        for ((gx, gz) in grassClumps) {
            for (b in 0 until 5) {
                val ang = b * 1.25f
                val bx = gx + cos(ang) * 2.4f
                val bz = gz + sin(ang) * 2.4f
                val h = 2.3f + (b % 3) * 0.65f
                // Crisscrossing tall elephant grass blades (Kora / Eeta reed clumps)
                out.add(QuadFace3D(Vec3(bx - 0.9f, 0.08f, bz), Vec3(bx + 0.9f, 0.08f, bz), Vec3(bx + 0.35f, h, bz), Vec3(bx - 0.35f, h, bz), if (b % 2 == 0) MatElephantGrass else MatElephantGrassTip, null, 0f))
                out.add(QuadFace3D(Vec3(bx, 0.08f, bz - 0.9f), Vec3(bx, 0.08f, bz + 0.9f), Vec3(bx, h, bz + 0.35f), Vec3(bx, h, bz - 0.35f), if (b % 2 == 0) MatElephantGrassTip else MatElephantGrass, null, 0f))
            }
        }
    }

    // =========================================================================
    // 3D GEOMETRY PRIMITIVES
    // =========================================================================

    private fun addAxisBox(
        out: MutableList<QuadFace3D>,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        topCol: Color, sideCol: Color, frontBackCol: Color
    ) {
        out.add(QuadFace3D(Vec3(x0, y1, z0), Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), topCol, null, 0f))
        out.add(QuadFace3D(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x0, y1, z0), frontBackCol, null, 0f))
        out.add(QuadFace3D(Vec3(x1, y0, z1), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x1, y1, z1), frontBackCol, null, 0f))
        out.add(QuadFace3D(Vec3(x0, y0, z1), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x0, y1, z1), sideCol, null, 0f))
        out.add(QuadFace3D(Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), sideCol, null, 0f))
    }

    private fun addOctagonalColumn(
        out: MutableList<QuadFace3D>,
        cx: Float, cz: Float,
        y0: Float, y1: Float,
        rBot: Float, rTop: Float,
        colA: Color, colB: Color
    ) {
        val sides = 8
        for (i in 0 until sides) {
            val a0 = (i.toFloat() / sides) * 2f * PI.toFloat()
            val a1 = ((i + 1).toFloat() / sides) * 2f * PI.toFloat()
            val p0 = Vec3(cx + cos(a0) * rBot, y0, cz + sin(a0) * rBot)
            val p1 = Vec3(cx + cos(a1) * rBot, y0, cz + sin(a1) * rBot)
            val p2 = Vec3(cx + cos(a1) * rTop, y1, cz + sin(a1) * rTop)
            val p3 = Vec3(cx + cos(a0) * rTop, y1, cz + sin(a0) * rTop)
            out.add(QuadFace3D(p0, p1, p2, p3, if (i % 2 == 0) colA else colB, null, 0f))
        }
    }

    private fun addFourSidedTempleRoof(
        out: MutableList<QuadFace3D>,
        cx: Float, cz: Float,
        botHW: Float, botHZ: Float, botY: Float,
        topHW: Float, topHZ: Float, topY: Float,
        roofTex: SignboardQuadSpec
    ) {
        // West sloped face (-X)
        out.add(QuadFace3D(Vec3(cx - botHW, botY, cz - botHZ), Vec3(cx - botHW, botY, cz + botHZ), Vec3(cx - topHW, topY, cz + topHZ), Vec3(cx - topHW, topY, cz - topHZ), MatCopperRoof, null, -0.04f, signboard = roofTex))
        // East sloped face (+X)
        out.add(QuadFace3D(Vec3(cx + botHW, botY, cz + botHZ), Vec3(cx + botHW, botY, cz - botHZ), Vec3(cx + topHW, topY, cz - topHZ), Vec3(cx + topHW, topY, cz + topHZ), MatCopperRoof, null, -0.04f, signboard = roofTex))
        // North sloped hip face (+Z)
        out.add(QuadFace3D(Vec3(cx + botHW, botY, cz + botHZ), Vec3(cx - botHW, botY, cz + botHZ), Vec3(cx - topHW, topY, cz + topHZ), Vec3(cx + topHW, topY, cz + topHZ), MatTerracottaDark, null, -0.04f, signboard = roofTex))
        // South sloped hip face (-Z)
        out.add(QuadFace3D(Vec3(cx - botHW, botY, cz - botHZ), Vec3(cx + botHW, botY, cz - botHZ), Vec3(cx + topHW, topY, cz - topHZ), Vec3(cx - topHW, topY, cz - topHZ), MatTerracottaDark, null, -0.04f, signboard = roofTex))
    }

    private fun addDiagonalBeam(
        out: MutableList<QuadFace3D>,
        a: Vec3,
        b: Vec3,
        halfThick: Float,
        colA: Color,
        colB: Color
    ) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        val ux = dx / len
        val uy = dy / len
        val uz = dz / len
        // Two perpendicular offsets to give solid 3D thickness from all angles
        var px = -uz
        var py = 0f
        var pz = ux
        val pLen = hypot(px, pz)
        if (pLen < 0.05f) {
            px = 1f; py = 0f; pz = 0f
        } else {
            px /= pLen; pz /= pLen
        }
        val qx = uy * pz - uz * py
        val qy = uz * px - ux * pz
        val qz = ux * py - uy * px

        val pOff = Vec3(px * halfThick, py * halfThick, pz * halfThick)
        val qOff = Vec3(qx * halfThick, qy * halfThick, qz * halfThick)

        out.add(QuadFace3D(a - pOff, b - pOff, b + pOff, a + pOff, colA, null, 0f))
        out.add(QuadFace3D(a - qOff, b - qOff, b + qOff, a + qOff, colB, null, 0f))
    }
}
