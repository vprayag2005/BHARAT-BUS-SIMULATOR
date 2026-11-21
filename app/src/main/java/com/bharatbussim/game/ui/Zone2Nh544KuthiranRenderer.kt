package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.ui.theme.PremiumFonts
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zone 2: NH 544 East & Kuthiran 6-Lane Twin Tunnel + Mannuthy 4-Way Interchange
 * + Peechi Dam Branch Road (SH 51), Peechi Dam Walled Bus Stand Compound (Entry/Exit Gates)
 * & Monumental Masonry Gravity Dam Spillway.
 *
 * Strictly enforces Zero Road Intrusion:
 * - Every high-mast floodlight pylon, gantry leg, portal wall, rock cutting, check-post cabin,
 *   compound wall, gate pillar, and 3D tree sits cleanly OUTSIDE all drivable carriageways.
 * - Kuthiran Twin-Tube Tunnel provides authentic 1:1 cross-sections (14.0m total clear tube width:
 *   10.5m 3-lane asphalt + two 1.75m raised inspection walkways; 955m Left Tube & 944m Right Tube).
 */
internal object Zone2Nh544KuthiranRenderer {

    // =========================================================================
    // CANONICAL ZONE 2 GEOMETRIC CONSTANTS (1:1 METRIC CROSS-SECTIONS)
    // =========================================================================
    const val MANNUTHY_X = 1050.30f
    const val MANNUTHY_Z = 198.33f

    const val KUTHIRAN_WEST_PORTAL_X = 2160.0f
    const val KUTHIRAN_EAST_PORTAL_X = 3115.0f       // 955 m Left Tube
    const val KUTHIRAN_RIGHT_WEST_X = 2165.0f
    const val KUTHIRAN_RIGHT_EAST_X = 3109.0f        // 944 m Right Tube

    const val KUTHIRAN_LEFT_CENTER_Z = 1154.0f       // Eastbound 3-lane tube (Thrissur -> Palakkad)
    const val KUTHIRAN_RIGHT_CENTER_Z = 1126.0f      // Westbound 3-lane tube (Palakkad -> Thrissur)
    const val KUTHIRAN_CORRIDOR_CENTER_Z = 1140.0f

    const val KUTHIRAN_TUBE_HALF_WIDTH = 7.00f       // 14.0m clear width (10.5m asphalt + 2 x 1.75m walkways)
    const val KUTHIRAN_ASPHALT_HALF_W = 5.25f        // 10.5m (3 x 3.5m lanes)
    const val KUTHIRAN_WALKWAY_H = 0.42f
    const val KUTHIRAN_WALL_HAUNCH_Y = 3.50f
    const val KUTHIRAN_VAULT_CROWN_Y = 8.40f

    // Peechi-Vazhani Wildlife Sanctuary Check-Post anchor on ROAD_MANNUTHY_TO_PEECHI_DAM (idx 396)
    const val PEECHI_CHECKPOST_X = 3123.09f
    const val PEECHI_CHECKPOST_Z = 51.27f
    const val PEECHI_CHECKPOST_TX = 0.781f
    const val PEECHI_CHECKPOST_TZ = 0.625f
    const val PEECHI_CHECKPOST_RX = 0.625f
    const val PEECHI_CHECKPOST_RZ = -0.781f

    // Peechi Dam Walled Bus Stand & Viewpoint Compound (13.4m East of SH 51 end 3398.6, 125.0)
    const val PEECHI_COMPOUND_WEST_X = 3412.0f
    const val PEECHI_COMPOUND_EAST_X = 3454.0f
    const val PEECHI_COMPOUND_SOUTH_Z = 72.0f
    const val PEECHI_COMPOUND_NORTH_Z = 178.0f
    const val PEECHI_ENTRY_GATE_Z = 98.0f            // Clear 12m opening: Z = 92.0 .. 104.0
    const val PEECHI_EXIT_GATE_Z = 152.0f            // Clear 12m opening: Z = 146.0 .. 158.0
    const val PEECHI_DAM_PLAZA_X = 3432.0f
    const val PEECHI_DAM_PLAZA_Z = 125.0f
    const val PEECHI_DAM_WALL_WEST_X = 3460.0f
    const val PEECHI_DAM_WALL_EAST_X = 3484.0f

    // Architectural Materials (matching PDF hex palette)
    private val MatNhConcreteWhite = Color(0xFFE5E7EB)
    private val MatNhConcreteLight = Color(0xFFD1D5DB)
    private val MatNhConcreteMid = Color(0xFF9CA3AF)
    private val MatNhConcreteDark = Color(0xFF6B7280)
    private val MatNewJerseyBarrier = Color(0xFFC2C6C0)
    private val MatAntiGlareGreen = Color(0xFF15803D)
    private val MatGalvanizedSteel = Color(0xFFCBD5E1)
    private val MatGalvanizedDark = Color(0xFF64748B)
    private val MatShotcreteVault = Color(0xFFE2E8F0)
    private val MatShotcreteMid = Color(0xFFCBD5E1)
    private val MatShotcreteSplash = Color(0xFF94A3B8)
    private val MatTunnelPortalConcrete = Color(0xFFD6DAE0)
    private val MatTunnelPortalShadow = Color(0xFF8C96A5)
    private val MatLedDaylight5000K = Color(0xFFF8FAFC)
    private val MatLedAdapt3000Lux = Color(0xFFFFFFFF)
    private val MatLedAdaptGlow = Color(0xFFE0F2FE)
    private val MatChevronYellow = Color(0xFFFACC15)
    private val MatChevronBlack = Color(0xFF18181B)
    private val MatCatsEyeRed = Color(0xFFEF4444)
    private val MatEmergencyGreen = Color(0xFF16A34A)
    private val MatEmergencyGreenBright = Color(0xFF4ADE80)
    private val MatSosYellow = Color(0xFFEAB308)
    private val MatHydrantRed = Color(0xFFDC2626)
    private val MatJetFanSteel = Color(0xFF94A3B8)
    private val MatJetFanIntake = Color(0xFF1E293B)
    private val MatGraniteRock = Color(0xFF39414B)          // Deep blasted Western Ghats charnockite granite
    private val MatGraniteRockLight = Color(0xFF525C68)     // Sunlit fractured granite facet
    private val MatGraniteRockDark = Color(0xFF232930)      // Wet dark shadowed granite cliff face
    private val MatShotcreteSlope = Color(0xFF47505A)
    private val MatGhatsForestDark = Color(0xFF1A4726)      // Deep lush Western Ghats rainforest canopy
    private val MatGhatsForestMid = Color(0xFF2B6B2F)       // Mid emerald tropical foliage
    private val MatGhatsForestLight = Color(0xFF478E33)     // Sunlit bright emerald grass & canopy highlights
    private val MatCliffMossGreen = Color(0xFF4E8C31)       // Lush moss ledges clinging to dark granite rock
    private val MatLateriteRed = Color(0xFFB56632)          // Authentic Kerala reddish-ochre laterite earth cut
    private val MatLateriteDark = Color(0xFF8C4A22)
    private val MatPortalTerracotta = Color(0xFFA64B37)     // Iconic Kuthiran terracotta-red arched portal headwall
    private val MatPortalTerracottaDark = Color(0xFF7D3424) // Shaded terracotta portal pier & recess
    private val MatPortalRoofEave = Color(0xFFC26047)       // Sunlit pitched terracotta portal canopy roof
    private val MatPortalTrimWhite = Color(0xFFF3F6F9)      // Crisp white eaves fascia & triangular gable trim
    private val MatPortalThroatDark = Color(0xFF1A1E24)     // Deep dark shadowed tunnel mouth arch collar
    private val MatControlHouseCoral = Color(0xFFD97866)    // Iconic salmon-coral Tunnel Control House walls
    private val MatControlHouseCoralShade = Color(0xFFB85C4B)
    private val MatControlRoofSkyBlue = Color(0xFF47C1E8)   // Iconic bright sky-blue gabled metal roof
    private val MatControlRoofSkyBlueShade = Color(0xFF289FC7)
    private val MatMedianGrassBright = Color(0xFF44942E)    // Lush bright-green Kuthiran approach central median
    private val MatMedianGrassRich = Color(0xFF337822)
    private val MatTeakTrunk = Color(0xFF5C4033)
    private val MatBambooStalk = Color(0xFF65A30D)
    private val MatDamMasonry = Color(0xFF71717A)
    private val MatDamMasonryLight = Color(0xFFA1A1AA)
    private val MatDamMasonryDark = Color(0xFF52525B)
    private val MatSluiceGateSteel = Color(0xFF334155)
    private val MatReservoirWaterDeep = Color(0xFF0369A1)
    private val MatReservoirWaterMid = Color(0xFF0284C7)
    private val MatReservoirWaterShimmer = Color(0xFF38BDF8)
    private val MatCascadeFoam = Color(0xFFE0F2FE)
    private val MatLawnIsland = Color(0xFF3D8B2A)
    private val MatNhaiSignGreen = Color(0xFF057A55)

    // Sculpted Western Ghats Kuthiran Mala elevation profile (shared by 3D mountain mesh & real 3D instanced trees!)
    fun kuthiranMountainSurfaceY(wx: Float, wz: Float): Float {
        // 1. Main Kuthiran Mala Mountain Massif directly over tunnel (X = 2158 .. 3117, Z = 1025 .. 1255)
        if (wx in 2158f..3117f) {
            val dz = abs(wz - KUTHIRAN_CORRIDOR_CENTER_Z)
            if (dz > 115f) return 0f
            val u = ((wx - KUTHIRAN_WEST_PORTAL_X) / (KUTHIRAN_EAST_PORTAL_X - KUTHIRAN_WEST_PORTAL_X)).coerceIn(0f, 1f)
            // Natural undulating multi-peak Western Ghats ridge crest (24m above portals -> 66m central granite massif)
            val archEnv = sin(u * PI.toFloat())
            val multiPeak = 6.5f * sin(u * 5.0f * PI.toFloat()) + 3.8f * cos(u * 9.0f * PI.toFloat())
            val ridgePeakH = 24.0f + archEnv * 38.0f + archEnv * multiPeak
            val cragRipple = 2.4f * sin(wx * 0.032f + wz * 0.045f) * (1f - dz / 115f)

            return when {
                dz <= 22f -> {
                    val t = dz / 22f
                    (ridgePeakH * (1f - 0.14f * t) + cragRipple).coerceAtLeast(19.5f)
                }
                dz <= 54f -> {
                    val t = (dz - 22f) / 32f
                    val shoulderH = ridgePeakH * 0.86f
                    val midBenchH = (ridgePeakH * 0.50f).coerceAtLeast(15.5f)
                    (shoulderH * (1f - t) + midBenchH * t + cragRipple).coerceAtLeast(12.0f)
                }
                dz <= 86f -> {
                    val t = (dz - 54f) / 32f
                    val midBenchH = (ridgePeakH * 0.50f).coerceAtLeast(15.5f)
                    val lowerTalusH = (ridgePeakH * 0.18f).coerceAtLeast(5.5f)
                    (midBenchH * (1f - t) + lowerTalusH * t + cragRipple * 0.6f).coerceAtLeast(3.0f)
                }
                else -> {
                    val t = ((dz - 86f) / 29f).coerceIn(0f, 1f)
                    val lowerTalusH = (ridgePeakH * 0.18f).coerceAtLeast(5.5f)
                    (lowerTalusH * (1f - t)).coerceAtLeast(0f)
                }
            }
        }

        // 2. West & East Blasted-Granite Approach Rock-Cutting Terraces (X = 2005..2160 & 3115..3275)
        val isWest = wx in 2005f..2160f
        val isEast = wx in 3115f..3275f
        if (isWest || isEast) {
            val t = if (isWest) {
                ((wx - 2005f) / (2160f - 2005f)).coerceIn(0f, 1f)
            } else {
                (1f - (wx - 3115f) / (3275f - 3115f)).coerceIn(0f, 1f)
            }
            val dz = abs(wz - KUTHIRAN_CORRIDOR_CENTER_Z)
            val footOff = 19.5f + t * 9.0f
            if (dz <= footOff) return 0f
            val hMax = 5.5f + t * 21.0f
            val rel = dz - footOff
            return when {
                rel <= 9.0f -> (rel / 9.0f) * (hMax * 0.52f)
                rel <= 15.0f -> hMax * 0.52f + ((rel - 9.0f) / 6.0f) * (hMax * 0.08f)
                rel <= 27.0f -> hMax * 0.60f + ((rel - 15.0f) / 12.0f) * (hMax * 0.40f)
                rel <= 58.0f -> hMax * (1f - ((rel - 27f) / 31f) * 0.72f)
                else -> 0f
            }
        }
        return 0f
    }

    /**
     * Returns [0f..1f] acoustic reverberation envelope when the bus is inside either
     * Kuthiran Twin-Tube Tunnel (smoothly ramping over 18m at West and East portals).
     */
    fun computeKuthiranTunnelReverbEnvelope(x: Float, z: Float): Float {
        val inLeftZ = abs(z - KUTHIRAN_LEFT_CENTER_Z) <= KUTHIRAN_TUBE_HALF_WIDTH + 1.5f
        val inRightZ = abs(z - KUTHIRAN_RIGHT_CENTER_Z) <= KUTHIRAN_TUBE_HALF_WIDTH + 1.5f
        if (!inLeftZ && !inRightZ) return 0f

        val xMin = if (inLeftZ) KUTHIRAN_WEST_PORTAL_X else KUTHIRAN_RIGHT_WEST_X
        val xMax = if (inLeftZ) KUTHIRAN_EAST_PORTAL_X else KUTHIRAN_RIGHT_EAST_X
        val ramp = 18.0f
        if (x < xMin - ramp || x > xMax + ramp) return 0f
        val leftFade = ((x - (xMin - ramp)) / (ramp * 2f)).coerceIn(0f, 1f)
        val rightFade = (((xMax + ramp) - x) / (ramp * 2f)).coerceIn(0f, 1f)
        return min(leftFade, rightFade)
    }

    /**
     * Returns overhead ceiling height for chase-camera anti-clipping inside Kuthiran Twin Tunnel,
     * under Peechi Wildlife Check-Post arch, and under Peechi Dam Entry/Exit Gate arches.
     */
    fun computeZone2OverheadCeilingY(x: Float, z: Float): Float {
        // Kuthiran Left or Right Tunnel Vault (crown = 8.40m; keep chase camera cleanly below jet fans at 6.95m!)
        if (x in (KUTHIRAN_WEST_PORTAL_X - 9f)..(KUTHIRAN_EAST_PORTAL_X + 9f)) {
            if (abs(z - KUTHIRAN_LEFT_CENTER_Z) <= KUTHIRAN_TUBE_HALF_WIDTH + 2f ||
                abs(z - KUTHIRAN_RIGHT_CENTER_Z) <= KUTHIRAN_TUBE_HALF_WIDTH + 2f
            ) {
                return 6.35f
            }
        }
        // Peechi-Vazhani Wildlife Sanctuary Check-Post Overhead Arch
        if (hypot(x - PEECHI_CHECKPOST_X, z - PEECHI_CHECKPOST_Z) < 12.0f) {
            return 5.65f
        }
        // Peechi Dam Bus Stand South ENTRY & North EXIT Gate Portal Arches (X = 3412.0, beam Y = 5.80m)
        if (abs(x - PEECHI_COMPOUND_WEST_X) < 5.5f &&
            (abs(z - PEECHI_ENTRY_GATE_Z) < 7.0f || abs(z - PEECHI_EXIT_GATE_Z) < 7.0f)
        ) {
            return 5.30f
        }
        return 1000f
    }

    /**
     * Prevents generic roadside trees/buildings from spawning inside Zone 2 architectural zones
     * (Mannuthy Interchange, Kuthiran Mountain/Tunnel Corridor, Peechi Check-Post & Peechi Dam).
     */
    fun isPointClearOfZone2Structures(px: Float, pz: Float): Boolean {
        // Mannuthy 4-Way Interchange & Landscaped Quadrant Islands
        if (px in 985f..1130f && pz in 55f..355f) return false
        // Kuthiran Approach Cuttings, Twin Tunnel Mountain Massif & East Portal Approach
        if (px in 1970f..3310f && pz in 1010f..1270f) return false
        // Peechi Wildlife Check-Post
        if (hypot(px - PEECHI_CHECKPOST_X, pz - PEECHI_CHECKPOST_Z) < 28f) return false
        // Peechi Dam Walled Bus Terminal Compound, Masonry Dam Wall & Reservoir Lake
        if (px in 3365f..3980f && pz in -200f..460f) return false
        return true
    }

    // =========================================================================
    // GROUND PLAZAS, INTERCHANGE ISLANDS, WATER & APRONS (ROAD VBO PASS)
    // =========================================================================
    fun DrawScope.drawZone2GroundPlazas(
        rig: CameraRig,
        path: Path,
        camEye: Vec3
    ) {
        val drawAll = rig.recorder != null

        // 1. Mannuthy 4-Way Interchange Landscaped Green Quadrant Islands
        if (drawAll || hypot(camEye.x - MANNUTHY_X, camEye.z - MANNUTHY_Z) < 550f) {
            val islands = arrayOf(
                Triple(1012.0f, 238.0f, 14.5f), // NW High-Mast Floodlight Island
                Triple(1086.0f, 256.0f, 13.0f), // NE Landscaped Island
                Triple(1014.0f, 144.0f, 12.5f), // SW Landscaped Island
                Triple(1096.0f, 162.0f, 13.5f)  // SE Landscaped Island
            )
            for ((ix, iz, rad) in islands) {
                val curbPts = ArrayList<Vec3>(16)
                val grassPts = ArrayList<Vec3>(16)
                for (k in 0 until 16) {
                    val a = (k.toFloat() / 16f) * (2f * PI.toFloat())
                    curbPts.add(Vec3(ix + cos(a) * (rad + 0.65f), 0.055f, iz + sin(a) * (rad + 0.65f)))
                    grassPts.add(Vec3(ix + cos(a) * rad, 0.075f, iz + sin(a) * rad))
                }
                if (rig.buildWorldPolygonPath(path, curbPts)) {
                    drawPath(path, MatNhConcreteLight)
                }
                if (rig.buildWorldPolygonPath(path, grassPts)) {
                    drawPath(path, MatLawnIsland)
                }
            }
        }

        // 2. Kuthiran West & East Bifurcation Approach: Lush Emerald Grass Central Median Island,
        //    Diagonal Paved Emergency Crossover Slip-Road, Laterite Earth Cut & Concrete Drain!
        if (drawAll || hypot(camEye.x - 2640f, camEye.z - KUTHIRAN_CORRIDOR_CENTER_Z) < 1200f) {
            val matCrossoverAsphalt = Color(0xFF9AA3AD)
            // --- WEST APPROACH (X = 2018 .. 2160) ---
            // A. Wide lush bright-green grass central median island between Left & Right tubes
            val westGrassIsland = listOf(
                Vec3(2018.0f, 0.064f, 1140.0f),
                Vec3(2052.0f, 0.064f, 1136.4f),
                Vec3(2094.0f, 0.064f, 1134.2f),
                Vec3(2136.0f, 0.064f, 1131.8f),
                Vec3(2160.0f, 0.064f, 1131.6f),
                Vec3(2160.0f, 0.064f, 1148.4f),
                Vec3(2136.0f, 0.064f, 1148.2f),
                Vec3(2094.0f, 0.064f, 1145.8f),
                Vec3(2052.0f, 0.064f, 1143.6f)
            )
            if (rig.buildWorldPolygonPath(path, westGrassIsland)) {
                drawPath(path, MatMedianGrassBright)
            }
            // Sunlit emerald grass core accent
            val westGrassCore = listOf(
                Vec3(2030.0f, 0.067f, 1140.0f),
                Vec3(2086.0f, 0.067f, 1135.8f),
                Vec3(2086.0f, 0.067f, 1144.2f)
            )
            if (rig.buildWorldPolygonPath(path, westGrassCore)) {
                drawPath(path, MatMedianGrassRich)
            }
            // B. Diagonal Paved Concrete Emergency Crossover Slip-Road (exact real Kuthiran photo layout!)
            val westDiagSlip1 = listOf(
                Vec3(2086.0f, 0.070f, 1145.6f),
                Vec3(2098.0f, 0.070f, 1146.4f),
                Vec3(2138.0f, 0.070f, 1131.8f),
                Vec3(2124.0f, 0.070f, 1131.8f)
            )
            if (rig.buildWorldPolygonPath(path, westDiagSlip1)) {
                drawPath(path, matCrossoverAsphalt)
            }
            // Upper portal-side crossover spur linking back to Left Tube (forming the signature green triangle!)
            val westDiagSlip2 = listOf(
                Vec3(2127.0f, 0.070f, 1132.2f),
                Vec3(2137.5f, 0.070f, 1132.2f),
                Vec3(2143.5f, 0.070f, 1148.2f),
                Vec3(2134.5f, 0.070f, 1148.2f)
            )
            if (rig.buildWorldPolygonPath(path, westDiagSlip2)) {
                drawPath(path, matCrossoverAsphalt)
            }
            // Control House gravel/concrete forecourt pad right between the twin portals (X=2141..2158, Z=1133..1147)
            val westControlYard = listOf(
                Vec3(2140.0f, 0.068f, 1133.5f),
                Vec3(2159.0f, 0.068f, 1133.5f),
                Vec3(2159.0f, 0.068f, 1146.5f),
                Vec3(2140.0f, 0.068f, 1146.5f)
            )
            if (rig.buildWorldPolygonPath(path, westControlYard)) {
                drawPath(path, MatMedianGrassBright)
            }
            // C. Authentic Kerala Reddish-Ochre Laterite Earth Shoulder Embankment along South (Right) Approach Cut!
            val westLateriteStrip = listOf(
                Vec3(2005.0f, 0.058f, 1114.0f),
                Vec3(2155.0f, 0.058f, 1111.5f),
                Vec3(2155.0f, 0.058f, 1120.2f),
                Vec3(2005.0f, 0.058f, 1124.2f)
            )
            if (rig.buildWorldPolygonPath(path, westLateriteStrip)) {
                drawPath(path, MatLateriteRed)
            }
            // Concrete stormwater drain channel along foot of laterite cut
            val westDrainChannel = listOf(
                Vec3(2008.0f, 0.063f, 1117.2f),
                Vec3(2152.0f, 0.063f, 1114.6f),
                Vec3(2152.0f, 0.063f, 1116.2f),
                Vec3(2008.0f, 0.063f, 1118.8f)
            )
            if (rig.buildWorldPolygonPath(path, westDrainChannel)) {
                drawPath(path, MatNhConcreteLight)
            }

            // --- EAST APPROACH (Mirrored X = 3115 .. 3258) ---
            val eastGrassIsland = listOf(
                Vec3(3115.0f, 0.064f, 1131.6f),
                Vec3(3139.0f, 0.064f, 1131.8f),
                Vec3(3181.0f, 0.064f, 1134.2f),
                Vec3(3223.0f, 0.064f, 1136.4f),
                Vec3(3258.0f, 0.064f, 1140.0f),
                Vec3(3223.0f, 0.064f, 1143.6f),
                Vec3(3181.0f, 0.064f, 1145.8f),
                Vec3(3139.0f, 0.064f, 1148.2f),
                Vec3(3115.0f, 0.064f, 1148.4f)
            )
            if (rig.buildWorldPolygonPath(path, eastGrassIsland)) {
                drawPath(path, MatMedianGrassBright)
            }
            val eastDiagSlip1 = listOf(
                Vec3(3137.0f, 0.070f, 1131.8f),
                Vec3(3151.0f, 0.070f, 1131.8f),
                Vec3(3189.0f, 0.070f, 1145.6f),
                Vec3(3177.0f, 0.070f, 1146.4f)
            )
            if (rig.buildWorldPolygonPath(path, eastDiagSlip1)) {
                drawPath(path, matCrossoverAsphalt)
            }
            val eastLateriteStrip = listOf(
                Vec3(3120.0f, 0.058f, 1111.5f),
                Vec3(3270.0f, 0.058f, 1114.0f),
                Vec3(3270.0f, 0.058f, 1124.2f),
                Vec3(3120.0f, 0.058f, 1120.2f)
            )
            if (rig.buildWorldPolygonPath(path, eastLateriteStrip)) {
                drawPath(path, MatLateriteRed)
            }
        }

        // 3. Peechi Dam Walled Bus Terminal Courtyard, Botanical Gardens, Stilling Basin & Peechi Reservoir Lake
        if (drawAll || hypot(camEye.x - PEECHI_DAM_PLAZA_X, camEye.z - PEECHI_DAM_PLAZA_Z) < 980f) {
            // Outer Approach Forecourt Apron connecting SH 51 to Entry (Z=98) and Exit (Z=152) Gates
            val forecourtApron = listOf(
                Vec3(3384.0f, 0.056f, 92.0f),
                Vec3(PEECHI_COMPOUND_WEST_X + 0.5f, 0.056f, 91.0f),
                Vec3(PEECHI_COMPOUND_WEST_X + 0.5f, 0.056f, 159.0f),
                Vec3(3384.0f, 0.056f, 158.0f)
            )
            if (rig.buildWorldPolygonPath(path, forecourtApron)) {
                drawPath(path, Color(0xFF333942))
            }

            // Full Walled Compound Concrete Perimeter Pad (X = 3412 .. 3454, Z = 72 .. 178)
            val compoundBase = listOf(
                Vec3(PEECHI_COMPOUND_WEST_X, 0.058f, PEECHI_COMPOUND_SOUTH_Z),
                Vec3(PEECHI_COMPOUND_EAST_X, 0.058f, PEECHI_COMPOUND_SOUTH_Z),
                Vec3(PEECHI_COMPOUND_EAST_X, 0.058f, PEECHI_COMPOUND_NORTH_Z),
                Vec3(PEECHI_COMPOUND_WEST_X, 0.058f, PEECHI_COMPOUND_NORTH_Z)
            )
            if (rig.buildWorldPolygonPath(path, compoundBase)) {
                drawPath(path, MatNhConcreteLight)
            }

            // Inner Drivable Bus Courtyard Asphalt Surface (Z = 90 .. 160, X = 3412 .. 3443.5)
            val busCourtyard = listOf(
                Vec3(PEECHI_COMPOUND_WEST_X, 0.064f, 90.0f),
                Vec3(3443.5f, 0.064f, 90.0f),
                Vec3(3443.5f, 0.064f, 160.0f),
                Vec3(PEECHI_COMPOUND_WEST_X, 0.064f, 160.0f)
            )
            if (rig.buildWorldPolygonPath(path, busCourtyard)) {
                drawPath(path, Color(0xFF2F353F))
            }

            // Painted Bus Bay Lines inside Peechi Dam Courtyard (Bays 1..4 at Z = 109, 119, 129, 139)
            for (b in 0..4) {
                val lz = 104.5f + b * 10.0f
                val linePts = listOf(
                    Vec3(3426.0f, 0.070f, lz - 0.16f),
                    Vec3(3442.0f, 0.070f, lz - 0.16f),
                    Vec3(3442.0f, 0.070f, lz + 0.16f),
                    Vec3(3426.0f, 0.070f, lz + 0.16f)
                )
                if (rig.buildWorldPolygonPath(path, linePts)) {
                    drawPath(path, MatChevronYellow)
                }
            }

            // South & North Peechi Botanical Garden Landscaped Lawns inside Compound Walls
            val southGarden = listOf(
                Vec3(3414.0f, 0.068f, 73.5f),
                Vec3(3452.0f, 0.068f, 73.5f),
                Vec3(3452.0f, 0.068f, 88.5f),
                Vec3(3414.0f, 0.068f, 88.5f)
            )
            if (rig.buildWorldPolygonPath(path, southGarden)) {
                drawPath(path, MatLawnIsland)
            }
            val northGarden = listOf(
                Vec3(3414.0f, 0.068f, 161.5f),
                Vec3(3452.0f, 0.068f, 161.5f),
                Vec3(3452.0f, 0.068f, 176.5f),
                Vec3(3414.0f, 0.068f, 176.5f)
            )
            if (rig.buildWorldPolygonPath(path, northGarden)) {
                drawPath(path, MatLawnIsland)
            }

            // Elevated Dam-Viewpoint Pedestrian Promenade Deck (X = 3443.5 .. 3454.0, Z = 90 .. 160)
            val promenade = listOf(
                Vec3(3443.5f, 0.074f, 90.0f),
                Vec3(3454.0f, 0.074f, 90.0f),
                Vec3(3454.0f, 0.074f, 160.0f),
                Vec3(3443.5f, 0.074f, 160.0f)
            )
            if (rig.buildWorldPolygonPath(path, promenade)) {
                drawPath(path, Color(0xFFD6CEBE))
            }

            // Dark Wet Riverbed Stilling Basin & Tailrace Channel Bed (underneath animated flowing GPU water)
            val stillingBasinBed = listOf(
                Vec3(3454.0f, 0.036f, 180.0f),
                Vec3(PEECHI_DAM_WALL_WEST_X, 0.036f, 180.0f),
                Vec3(PEECHI_DAM_WALL_WEST_X, 0.036f, 285.0f),
                Vec3(3380.0f, 0.036f, 305.0f),
                Vec3(3325.0f, 0.036f, 265.0f),
                Vec3(3388.0f, 0.036f, 205.0f)
            )
            if (rig.buildWorldPolygonPath(path, stillingBasinBed)) {
                drawPath(path, Color(0xFF192B32))
            }
            // Dark Concrete Apron Floor directly below spillway gates (animated boiling plunge pool floats cleanly above at Y=0.135m)
            val dischargeBed = listOf(
                Vec3(3454.0f, 0.040f, 65.0f),
                Vec3(PEECHI_DAM_WALL_WEST_X, 0.040f, 65.0f),
                Vec3(PEECHI_DAM_WALL_WEST_X, 0.040f, 205.0f),
                Vec3(3454.0f, 0.040f, 205.0f)
            )
            if (rig.buildWorldPolygonPath(path, dischargeBed)) {
                drawPath(path, Color(0xFF152933))
            }

            // Submerged Dark Reservoir Basin Floor (animated sunlit Peechi Reservoir surface waves at Y = 19.22m above)
            val resBasinFloor = listOf(
                Vec3(PEECHI_DAM_WALL_EAST_X - 1f, 18.60f, -155.0f),
                Vec3(3945.0f, 18.60f, -155.0f),
                Vec3(3945.0f, 18.60f, 415.0f),
                Vec3(PEECHI_DAM_WALL_EAST_X - 1f, 18.60f, 415.0f)
            )
            if (rig.buildWorldPolygonPath(path, resBasinFloor)) {
                drawPath(path, Color(0xFF0A2E3F))
            }
        }
    }

    // =========================================================================
    // STATIC 3D GEOMETRY CACHE (COMPILED ONCE INTO HARDWARE OPENGL ES VBO)
    // =========================================================================
    @Volatile
    private var cachedZone2Faces: List<QuadFace3D>? = null
    @Volatile
    private var cachedRoadsCount: Int = -1

    fun collectAllZone2StaticFaces(
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        val existing = cachedZone2Faces
        if (existing != null && cachedRoadsCount == roads.size) {
            outFaces.addAll(existing)
            return
        }
        val built = ArrayList<QuadFace3D>(6400)
        buildMannuthyElevatedFlyoverAndInterchange(built)
        buildNh544MedianBarrierAndLighting(roads, built)
        buildKuthiranTwinTubeTunnelAndMountain(built)
        buildPeechiForestRoadAndDamSpillway(roads, built)
        cachedZone2Faces = built
        cachedRoadsCount = roads.size
        outFaces.addAll(built)
    }

    fun collectZone2FacesNearCamera(
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        // Software fallback path: only emit when camera is East of X = 700
        if (camEye.x < 700f) return
        collectAllZone2StaticFaces(roads, outFaces)
    }

    // =========================================================================
    // 1. MANNUTHY 4-WAY GROUND INTERCHANGE, 25m HIGH-MAST & NHAI OVERHEAD GANTRY
    //    (Clean, unobstructed 4-way highway junction — zero floating half-way bridge!)
    // =========================================================================
    private fun buildMannuthyElevatedFlyoverAndInterchange(out: MutableList<QuadFace3D>) {
        // 25-Meter High-Mast Floodlight Pylon in NW Landscaped Quadrant Island (X=1012, Z=238, +23m clear of roads!)
        val hmX = 1012.0f
        val hmZ = 238.0f
        // Sculpted 2-tier Kerala granite & masonry pedestal
        addAxisBox(out, hmX - 2.6f, 0.05f, hmZ - 2.6f, hmX + 2.6f, 0.75f, hmZ + 2.6f, MatDamMasonryLight, MatDamMasonry, MatDamMasonryDark)
        addAxisBox(out, hmX - 1.7f, 0.75f, hmZ - 1.7f, hmX + 1.7f, 1.65f, hmZ + 1.7f, MatNhConcreteWhite, MatNhConcreteLight, MatNhConcreteMid)
        // Tapered galvanized steel octagonal mast (3 segments rising to 25.0m)
        addTaperedPole(out, hmX, hmZ, 1.65f, 10.0f, 0.48f, 0.36f, MatGalvanizedSteel)
        addTaperedPole(out, hmX, hmZ, 10.0f, 18.5f, 0.36f, 0.25f, MatGalvanizedSteel)
        addTaperedPole(out, hmX, hmZ, 18.5f, 25.0f, 0.25f, 0.16f, MatGalvanizedSteel)
        // Crown 12-LED Luminaire Head Ring at Y = 24.75 .. 25.30m
        addAxisBox(out, hmX - 2.2f, 24.75f, hmZ - 2.2f, hmX + 2.2f, 25.30f, hmZ + 2.2f, MatGalvanizedDark, MatLedDaylight5000K, MatLedDaylight5000K)
        // Aviation red obstruction beacon on top
        addAxisBox(out, hmX - 0.22f, 25.30f, hmZ - 0.22f, hmX + 0.22f, 25.90f, hmZ + 0.22f, MatCatsEyeRed, MatCatsEyeRed, MatCatsEyeRed)

        // Decorative Kerala masonry planter plinths on NE, SW, SE islands (trees & grass are real 3D instanced models!)
        for ((px, pz) in arrayOf(Pair(1086.0f, 256.0f), Pair(1014.0f, 144.0f), Pair(1096.0f, 162.0f))) {
            addAxisBox(out, px - 2.4f, 0.06f, pz - 2.4f, px + 2.4f, 0.65f, pz + 2.4f, MatLawnIsland, MatNhConcreteWhite, MatNhConcreteLight)
        }

        // Official #057A55 Green Overhead Directional Gantry spanning NH 544 Northbound leaving Mannuthy toward Kuthiran
        // Columns at +-14.8m strictly outside 24m 6-lane carriageway!
        val gZ = 365.0f
        val gCx = 1041.2f
        val gColHalfSpan = 14.8f
        addAxisBox(out, gCx - gColHalfSpan - 0.45f, 0f, gZ - 0.45f, gCx - gColHalfSpan + 0.45f, 8.8f, gZ + 0.45f, MatGalvanizedSteel, MatGalvanizedSteel, MatGalvanizedDark)
        addAxisBox(out, gCx + gColHalfSpan - 0.45f, 0f, gZ - 0.45f, gCx + gColHalfSpan + 0.45f, 8.8f, gZ + 0.45f, MatGalvanizedSteel, MatGalvanizedSteel, MatGalvanizedDark)
        // Overhead steel truss beam
        addAxisBox(out, gCx - gColHalfSpan, 8.15f, gZ - 0.35f, gCx + gColHalfSpan, 8.65f, gZ + 0.35f, MatGalvanizedSteel, MatGalvanizedDark, MatGalvanizedSteel)
        addAxisBox(out, gCx - gColHalfSpan, 6.25f, gZ - 0.30f, gCx + gColHalfSpan, 6.55f, gZ + 0.30f, MatGalvanizedSteel, MatGalvanizedDark, MatGalvanizedSteel)

        val mannuthyNorthGantrySpec = createZone2NhaiGantrySpec(
            cacheKey = "ZONE2_MANNUTHY_NORTH_GANTRY_V1",
            header = "NH 544 • MANNUTHY 4-WAY INTERCHANGE (ZONE 2)",
            col1Mal = "കുതിരാൻ / പാലക്കാട്",
            col1Eng = "KUTHIRAN / PALAKKAD",
            col1Dist = "68 KM ↑",
            col2Mal = "പീച്ചി ഡാം",
            col2Eng = "PEECHI DAM (SH 51)",
            col2Dist = "15 KM ↗",
            col3Mal = "തൃശ്ശൂർ നഗരം",
            col3Eng = "THRISSUR CITY",
            col3Dist = "6 KM ↖"
        )
        // South-facing board (visible driving Northbound toward Kuthiran)
        out.add(
            QuadFace3D(
                Vec3(gCx - 11.5f, 6.35f, gZ - 0.40f),
                Vec3(gCx + 11.5f, 6.35f, gZ - 0.40f),
                Vec3(gCx + 11.5f, 8.55f, gZ - 0.40f),
                Vec3(gCx - 11.5f, 8.55f, gZ - 0.40f),
                MatNhaiSignGreen, null, -0.25f, signboard = mannuthyNorthGantrySpec
            )
        )
        // North-facing board (visible returning Southbound into Mannuthy)
        val mannuthyReturnGantrySpec = createZone2NhaiGantrySpec(
            cacheKey = "ZONE2_MANNUTHY_RETURN_GANTRY_V1",
            header = "NH 544 • MANNUTHY INTERCHANGE AHEAD",
            col1Mal = "പീച്ചി ഡാം",
            col1Eng = "PEECHI DAM",
            col1Dist = "15 KM ↖",
            col2Mal = "പാലിയേക്കര / കൊച്ചി",
            col2Eng = "KOCHI / TOLL NH 544",
            col2Dist = "16 KM ↑",
            col3Mal = "തൃശ്ശൂർ നഗരം (ഈസ്റ്റ് ഫോർട്ട്)",
            col3Eng = "THRISSUR CITY",
            col3Dist = "6 KM ↗"
        )
        out.add(
            QuadFace3D(
                Vec3(gCx + 11.5f, 6.35f, gZ + 0.40f),
                Vec3(gCx - 11.5f, 6.35f, gZ + 0.40f),
                Vec3(gCx - 11.5f, 8.55f, gZ + 0.40f),
                Vec3(gCx + 11.5f, 8.55f, gZ + 0.40f),
                MatNhaiSignGreen, null, -0.25f, signboard = mannuthyReturnGantrySpec
            )
        )
    }

    // =========================================================================
    // 2. NH 544 6-LANE DIVIDED EXPRESSWAY MEDIAN BARRIER, ANTI-GLARE VANES & LIGHTING
    // =========================================================================
    private fun buildNh544MedianBarrierAndLighting(
        roads: List<ThrissurOsmRoad>,
        out: MutableList<QuadFace3D>
    ) {
        val nhEastIds = setOf("ROAD_NH544_MANNUTHY_TO_KUTHIRAN", "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD")
        for (road in roads) {
            if (road.id !in nhEastIds) continue
            val frames = road.frames
            if (frames.size < 12) continue

            val totalLen = frames.last().cumulativeDist
            val startTrim = if (road.id == "ROAD_NH544_MANNUTHY_TO_KUTHIRAN") 48.0f else 28.0f
            val endTrim = if (road.id == "ROAD_NH544_MANNUTHY_TO_KUTHIRAN") 32.0f else 28.0f

            var lastLampDist = -100f
            var i = 0
            while (i < frames.size - 1) {
                val f0 = frames[i]
                // Step by 2 frames (~12-16m) for crisp smooth barrier curves with low quad count
                val nextIdx = min(i + 2, frames.size - 1)
                val f1 = frames[nextIdx]

                if (f0.cumulativeDist >= startTrim && f1.cumulativeDist <= totalLen - endTrim && !f0.centerCovered) {
                    val p0 = f0.pos
                    val p1 = f1.pos
                    val r0 = f0.right
                    val r1 = f1.right

                    // 900mm High Continuous New Jersey Reinforced Concrete Median Barrier (#C2C6C0)
                    val baseHw = 0.46f
                    val topHw = 0.22f
                    val barH = 0.90f
                    val y0 = 0.06f

                    // Left sloped concrete wall
                    out.add(
                        QuadFace3D(
                            Vec3(p0.x - r0.x * baseHw, y0, p0.z - r0.z * baseHw),
                            Vec3(p1.x - r1.x * baseHw, y0, p1.z - r1.z * baseHw),
                            Vec3(p1.x - r1.x * topHw, y0 + barH, p1.z - r1.z * topHw),
                            Vec3(p0.x - r0.x * topHw, y0 + barH, p0.z - r0.z * topHw),
                            MatNewJerseyBarrier, null, 0f
                        )
                    )
                    // Right sloped concrete wall
                    out.add(
                        QuadFace3D(
                            Vec3(p0.x + r0.x * baseHw, y0, p0.z + r0.z * baseHw),
                            Vec3(p0.x + r0.x * topHw, y0 + barH, p0.z + r0.z * topHw),
                            Vec3(p1.x + r1.x * topHw, y0 + barH, p1.z + r1.z * topHw),
                            Vec3(p1.x + r1.x * baseHw, y0, p1.z + r1.z * baseHw),
                            MatNewJerseyBarrier, null, 0f
                        )
                    )
                    // Top concrete cap
                    out.add(
                        QuadFace3D(
                            Vec3(p0.x - r0.x * topHw, y0 + barH, p0.z - r0.z * topHw),
                            Vec3(p0.x + r0.x * topHw, y0 + barH, p0.z + r0.z * topHw),
                            Vec3(p1.x + r1.x * topHw, y0 + barH, p1.z + r1.z * topHw),
                            Vec3(p1.x - r1.x * topHw, y0 + barH, p1.z - r1.z * topHw),
                            MatNhConcreteLight, null, 0f
                        )
                    )
                    // Green Plastic Anti-Glare Mesh Screen mounted on top of median (Y = 0.96 .. 1.56m)
                    out.add(
                        QuadFace3D(
                            Vec3(p0.x, y0 + barH, p0.z),
                            Vec3(p1.x, y0 + barH, p1.z),
                            Vec3(p1.x, y0 + barH + 0.62f, p1.z),
                            Vec3(p0.x, y0 + barH + 0.62f, p0.z),
                            MatAntiGlareGreen, null, 0f
                        )
                    )

                    // Twin-Arm Galvanized Steel Cobra-Head LED Streetlamp Poles every 35m
                    if (f0.cumulativeDist - lastLampDist >= 35.0f) {
                        lastLampDist = f0.cumulativeDist
                        val poleH = 10.2f
                        val armSpan = 3.1f
                        // Vertical galvanized mast mounted directly onto concrete median
                        addVerticalBeam(out, p0.x, p0.z, y0 + barH, y0 + poleH, 0.14f, MatGalvanizedSteel)
                        // Twin cobra-head arms extending out over both 3-lane carriageways
                        val lx = p0.x - r0.x * armSpan
                        val lz = p0.z - r0.z * armSpan
                        val rx = p0.x + r0.x * armSpan
                        val rz = p0.z + r0.z * armSpan
                        addHorizontalBeam(out, lx, lz, rx, rz, y0 + poleH - 0.18f, y0 + poleH + 0.08f, 0.10f, MatGalvanizedSteel)
                        // 200W 5000K Daylight White LED Luminaire Heads
                        addHorizontalBeam(
                            out,
                            p0.x - r0.x * (armSpan + 0.45f), p0.z - r0.z * (armSpan + 0.45f),
                            p0.x - r0.x * (armSpan - 0.55f), p0.z - r0.z * (armSpan - 0.55f),
                            y0 + poleH - 0.28f, y0 + poleH - 0.10f, 0.22f, MatLedDaylight5000K
                        )
                        addHorizontalBeam(
                            out,
                            p0.x + r0.x * (armSpan - 0.55f), p0.z + r0.z * (armSpan - 0.55f),
                            p0.x + r0.x * (armSpan + 0.45f), p0.z + r0.z * (armSpan + 0.45f),
                            y0 + poleH - 0.28f, y0 + poleH - 0.10f, 0.22f, MatLedDaylight5000K
                        )
                    }
                }
                i = nextIdx
            }
        }
    }

    // =========================================================================
    // 3. KUTHIRAN 6-LANE TWIN-TUBE TUNNEL (955m LEFT / 944m RIGHT) & MOUNTAIN
    //    Photo-Matched Real-World View:
    //      - Twin standalone Terracotta-Red Arched Portal Pavilions with
    //        overhanging pitched terracotta roof eaves & white triangular crests
    //      - Raw Dark Blasted-Granite Rock Cliff exposed directly BETWEEN and
    //        ABOVE the two portals with vibrant emerald moss shelves
    //      - Iconic Coral-Pink & Bright Sky-Blue Gabled Control House tucked
    //        between the twin tunnel mouths + white annex shed
    //      - Lush Green Approach Median with Black/White Painted Concrete Kerbs,
    //        Diagonal Crossover Slip-Road & Retaining Wall with Steel Pipe Rail
    //      - Red-Ochre Kerala Laterite Cut + Towering Forested Western Ghats Cliffs
    // =========================================================================
    private fun buildKuthiranTwinTubeTunnelAndMountain(out: MutableList<QuadFace3D>) {
        // A. Twin Standalone Terracotta Arched Portals + Central Blasted Granite Cliff + Coral/Sky-Blue Control House
        buildTunnelPortalFacade(out, portalX = KUTHIRAN_WEST_PORTAL_X, facingWest = true)
        buildTunnelPortalFacade(out, portalX = KUTHIRAN_EAST_PORTAL_X, facingWest = false)

        // B. Approach Central Median Black/White Painted Kerbs, Crossover Retaining Wall & Shrubs
        buildKuthiranApproachMedianDetails(out, isWestApproach = true)
        buildKuthiranApproachMedianDetails(out, isWestApproach = false)

        // C. Overhead VMS Gantries placed well before the bifurcation (2014 / 3266) so the
        //    panoramic approach view of both terracotta portals & control house stays 100% unobstructed!
        buildKuthiranVmsGantry(out, gantryX = 2014.0f, facingWest = true)
        buildKuthiranVmsGantry(out, gantryX = 3266.0f, facingWest = false)

        // D. Twin-Tube Interior Vaults (10-segment smooth horseshoe arch), Walkways, Chevrons,
        //    5000K LED Strips, Adaptation Banks, Jet Fans, Cross-Passage Doors & SOS/Hydrant Niches
        buildSingleTunnelTubeInterior(
            out = out,
            xStart = KUTHIRAN_WEST_PORTAL_X,
            xEnd = KUTHIRAN_EAST_PORTAL_X,
            centerZ = KUTHIRAN_LEFT_CENTER_Z,
            isLeftTube = true
        )
        buildSingleTunnelTubeInterior(
            out = out,
            xStart = KUTHIRAN_RIGHT_WEST_X,
            xEnd = KUTHIRAN_RIGHT_EAST_X,
            centerZ = KUTHIRAN_RIGHT_CENTER_Z,
            isLeftTube = false
        )

        // E. Dramatic Dark Blasted-Granite Approach Rock Cuttings + Lush Western Ghats Forest Trees
        //    Strictly >= 6.0m OUTSIDE both Left and Right tube outer asphalt edges!
        buildApproachRockCuttings(out, xStart = 2008.0f, xEnd = KUTHIRAN_WEST_PORTAL_X, isWestApproach = true)
        buildApproachRockCuttings(out, xStart = KUTHIRAN_EAST_PORTAL_X, xEnd = 3272.0f, isWestApproach = false)

        // F. Towering Kuthiran Mala Granite Mountain Massif & Dense Western Ghats Rainforest Ridge above Tunnel
        buildKuthiranMalaMountainMassif(out)
    }

    private fun buildTunnelPortalFacade(
        out: MutableList<QuadFace3D>,
        portalX: Float,
        facingWest: Boolean
    ) {
        val dir = if (facingWest) -1f else 1f
        val frontX = portalX
        val backX = portalX - dir * 6.8f
        val xMin = min(frontX, backX)
        val xMax = max(frontX, backX)

        val archCrownY = KUTHIRAN_VAULT_CROWN_Y         // 8.40m
        val portalLintelY = 9.70f                       // Clean terracotta spandrel above horseshoe crown
        val roofPeakY = 11.45f                          // Pitched overhanging terracotta roof peak
        val archSegs = 12                               // Smooth 12-segment horseshoe arch curvature

        // =====================================================================
        // 1. TWO STANDALONE TERRACOTTA / RUST-RED ARCHED PORTAL PAVILIONS
        //    (Exact photo match: separate terracotta arches with pitched eaves &
        //     signature white-trimmed central triangular Kerala gable crest!)
        // =====================================================================
        for (tubeCenterZ in floatArrayOf(KUTHIRAN_RIGHT_CENTER_Z, KUTHIRAN_LEFT_CENTER_Z)) {
            val innerLeftZ = tubeCenterZ - KUTHIRAN_TUBE_HALF_WIDTH    // -7.0m
            val innerRightZ = tubeCenterZ + KUTHIRAN_TUBE_HALF_WIDTH   // +7.0m
            val outerLeftZ = innerLeftZ - 1.35f                        // -8.35m (16.7m wide standalone pavilion)
            val outerRightZ = innerRightZ + 1.35f                      // +8.35m

            // A. Left & Right Terracotta Vertical Portal Piers (strictly outside 14.0m bore)
            addAxisBox(
                out, xMin, 0f, outerLeftZ, xMax, portalLintelY, innerLeftZ,
                MatPortalTerracotta, MatPortalTerracotta, MatPortalTerracottaDark
            )
            addAxisBox(
                out, xMin, 0f, innerRightZ, xMax, portalLintelY, outerRightZ,
                MatPortalTerracotta, MatPortalTerracotta, MatPortalTerracottaDark
            )
            // Upper Terracotta Spandrel Header above Arch Crown (Y = 8.40 .. 9.70m)
            addAxisBox(
                out, xMin, archCrownY, innerLeftZ, xMax, portalLintelY, innerRightZ,
                MatPortalTerracotta, MatPortalTerracotta, MatPortalTerracottaDark
            )

            // B. Smooth 12-Segment Horseshoe Arch Spandrel + Deep Shadowed Inner Arch Collar
            for (s in 0 until archSegs) {
                val a0 = (s.toFloat() / archSegs) * PI.toFloat()
                val a1 = ((s + 1).toFloat() / archSegs) * PI.toFloat()
                val z0 = tubeCenterZ - cos(a0) * KUTHIRAN_TUBE_HALF_WIDTH
                val y0 = KUTHIRAN_WALL_HAUNCH_Y + sin(a0) * (archCrownY - KUTHIRAN_WALL_HAUNCH_Y)
                val z1 = tubeCenterZ - cos(a1) * KUTHIRAN_TUBE_HALF_WIDTH
                val y1 = KUTHIRAN_WALL_HAUNCH_Y + sin(a1) * (archCrownY - KUTHIRAN_WALL_HAUNCH_Y)

                // Front terracotta curved arch spandrel (flush with frontX)
                val spandrelX = frontX + dir * 0.04f
                out.add(
                    QuadFace3D(
                        Vec3(spandrelX, y0, z0),
                        Vec3(spandrelX, y1, z1),
                        Vec3(spandrelX, portalLintelY, z1),
                        Vec3(spandrelX, portalLintelY, z0),
                        MatPortalTerracotta, null, -0.02f
                    )
                )
                // Dark shadowed concrete inner arch bevel lining the tunnel throat (photo-matched dark arch ring!)
                val collarInX = frontX - dir * 2.2f
                out.add(
                    QuadFace3D(
                        Vec3(frontX, y0, z0),
                        Vec3(collarInX, y0, z0),
                        Vec3(collarInX, y1, z1),
                        Vec3(frontX, y1, z1),
                        MatPortalThroatDark, null, -0.04f
                    )
                )
                // Subtle warm dark-terracotta recessed arch trim molding around the horseshoe rim
                val zOut0 = tubeCenterZ - cos(a0) * (KUTHIRAN_TUBE_HALF_WIDTH + 0.34f)
                val yOut0 = KUTHIRAN_WALL_HAUNCH_Y + sin(a0) * (archCrownY + 0.34f - KUTHIRAN_WALL_HAUNCH_Y)
                val zOut1 = tubeCenterZ - cos(a1) * (KUTHIRAN_TUBE_HALF_WIDTH + 0.34f)
                val yOut1 = KUTHIRAN_WALL_HAUNCH_Y + sin(a1) * (archCrownY + 0.34f - KUTHIRAN_WALL_HAUNCH_Y)
                out.add(
                    QuadFace3D(
                        Vec3(frontX + dir * 0.14f, y0, z0),
                        Vec3(frontX + dir * 0.14f, y1, z1),
                        Vec3(frontX + dir * 0.14f, yOut1, zOut1),
                        Vec3(frontX + dir * 0.14f, yOut0, zOut0),
                        MatPortalTerracottaDark, null, -0.06f
                    )
                )
            }

            // C. Overhanging Sloped Terracotta Canopy Roof & Crisp White Fascia Trim (Signature Kuthiran Silhouette!)
            val eaveFrontX = frontX + dir * 1.95f
            val eaveBackX = backX - dir * 0.6f
            val eaveMinX = min(eaveFrontX, eaveBackX)
            val eaveMaxX = max(eaveFrontX, eaveBackX)
            val eaveLeftZ = outerLeftZ - 0.85f
            val eaveRightZ = outerRightZ + 0.85f

            // Crisp white horizontal soffit fascia trim lip directly under the terracotta roof eaves
            addAxisBox(
                out, eaveMinX, portalLintelY - 0.18f, eaveLeftZ, eaveMaxX, portalLintelY + 0.12f, eaveRightZ,
                MatPortalTrimWhite, MatPortalTrimWhite, MatPortalTrimWhite
            )
            // Sloped pitched terracotta roof plane rising cleanly from front eaves (Y=9.82) up to rear crown (Y=11.45)
            val roofFrontY = portalLintelY + 0.12f
            val ridgeX = frontX - dir * 1.45f
            out.add(
                QuadFace3D(
                    Vec3(eaveFrontX, roofFrontY, eaveLeftZ),
                    Vec3(eaveFrontX, roofFrontY, eaveRightZ),
                    Vec3(ridgeX, roofPeakY, eaveRightZ - 0.45f),
                    Vec3(ridgeX, roofPeakY, eaveLeftZ + 0.45f),
                    MatPortalRoofEave, null, -0.03f
                )
            )
            // Flat/back roof deck tucking solidly back into the dark blasted granite rock cliff
            val backRoofMinX = min(ridgeX, eaveBackX)
            val backRoofMaxX = max(ridgeX, eaveBackX)
            addAxisBox(
                out, backRoofMinX, portalLintelY, eaveLeftZ + 0.35f, backRoofMaxX, roofPeakY, eaveRightZ - 0.35f,
                MatPortalTerracottaDark, MatPortalTerracottaDark, MatPortalTerracottaDark
            )
            // Left & right angled roof gable cheeks
            out.add(
                QuadFace3D(
                    Vec3(eaveFrontX, roofFrontY, eaveLeftZ),
                    Vec3(ridgeX, roofPeakY, eaveLeftZ + 0.45f),
                    Vec3(ridgeX, roofFrontY, eaveLeftZ + 0.45f),
                    Vec3(eaveFrontX, roofFrontY - 0.10f, eaveLeftZ),
                    MatPortalTerracottaDark, null, 0f
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(ridgeX, roofPeakY, eaveRightZ - 0.45f),
                    Vec3(eaveFrontX, roofFrontY, eaveRightZ),
                    Vec3(eaveFrontX, roofFrontY - 0.10f, eaveRightZ),
                    Vec3(ridgeX, roofFrontY, eaveRightZ - 0.45f),
                    MatPortalTerracottaDark, null, 0f
                )
            )

            // D. Signature Central White-Trimmed Triangular Kerala Gable Crest on the Terracotta Roof!
            val gBaseZ0 = tubeCenterZ - 1.95f
            val gBaseZ1 = tubeCenterZ + 1.95f
            val gFrontX = eaveFrontX + dir * 0.10f
            val gTopX = ridgeX + dir * 0.35f
            val gBaseY = roofFrontY + 0.08f
            val gApexY = roofPeakY + 0.18f
            // Crisp white triangular pediment frame (left + right bargeboards & base sill)
            addHorizontalBeam(out, gFrontX, gBaseZ0, gFrontX, gBaseZ1, gBaseY - 0.08f, gBaseY + 0.14f, 0.14f, MatPortalTrimWhite)
            out.add(
                QuadFace3D(
                    Vec3(gFrontX, gBaseY, gBaseZ0),
                    Vec3(gFrontX, gBaseY, gBaseZ0 + 0.34f),
                    Vec3(gTopX, gApexY - 0.14f, tubeCenterZ),
                    Vec3(gTopX, gApexY + 0.14f, tubeCenterZ),
                    MatPortalTrimWhite, null, -0.08f
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(gFrontX, gBaseY, gBaseZ1 - 0.34f),
                    Vec3(gFrontX, gBaseY, gBaseZ1),
                    Vec3(gTopX, gApexY + 0.14f, tubeCenterZ),
                    Vec3(gTopX, gApexY - 0.14f, tubeCenterZ),
                    MatPortalTrimWhite, null, -0.08f
                )
            )
            // Inner warm terracotta triangular gable medallion
            out.add(
                QuadFace3D(
                    Vec3(gFrontX - dir * 0.04f, gBaseY + 0.10f, gBaseZ0 + 0.34f),
                    Vec3(gFrontX - dir * 0.04f, gBaseY + 0.10f, gBaseZ1 - 0.34f),
                    Vec3(gTopX - dir * 0.04f, gApexY - 0.14f, tubeCenterZ + 0.06f),
                    Vec3(gTopX - dir * 0.04f, gApexY - 0.14f, tubeCenterZ - 0.06f),
                    MatPortalTerracottaDark, null, -0.05f
                )
            )
        }

        // =====================================================================
        // 2. EXPOSED DARK BLASTED CHARCOAL-SLATE GRANITE CLIFF FACE DIRECTLY
        //    BETWEEN & SOARING ABOVE BOTH TERRACOTTA PORTALS (WITH MOSS LEDGES!)
        // =====================================================================
        // Central rock buttress strictly between Right Portal (1134.35) & Left Portal (1145.65)
        val rockFrontX = frontX - dir * 0.8f
        val rockBackX = backX - dir * 2.5f
        val rMinX = min(rockFrontX, rockBackX)
        val rMaxX = max(rockFrontX, rockBackX)
        addAxisBox(out, rMinX, 0f, 1134.4f, rMaxX, 13.5f, 1145.6f, MatCliffMossGreen, MatGraniteRockDark, MatGraniteRock)
        // Stepped craggy granite rock face jutting slightly with lush moss shelf right between the two portals
        val cragX0 = min(frontX + dir * 0.9f, rockFrontX)
        val cragX1 = max(frontX + dir * 0.9f, rockFrontX)
        addAxisBox(out, cragX0, 0f, 1135.8f, cragX1, 8.6f, 1144.2f, MatCliffMossGreen, MatGraniteRock, MatGraniteRockDark)
        addAxisBox(out, rMinX, 13.4f, 1132.0f, rMaxX, 18.8f, 1148.0f, MatCliffMossGreen, MatGraniteRock, MatGraniteRockDark)

        // Rugged multi-tiered dark blasted granite cliff face rising cleanly ABOVE both terracotta portals (Y = 10.6..21.5m)
        val cliffBands = arrayOf(
            Triple(1105.0f, 1121.0f, 19.6f),
            Triple(1121.0f, 1135.0f, 18.8f),
            Triple(1135.0f, 1145.0f, 21.4f),
            Triple(1145.0f, 1159.0f, 19.2f),
            Triple(1159.0f, 1175.0f, 20.5f)
        )
        for ((idx, band) in cliffBands.withIndex()) {
            val (bz0, bz1, topY) = band
            val stepInX = frontX - dir * (1.2f + (idx % 2) * 1.4f)
            val bMinX = min(stepInX, rockBackX - dir * 2.0f)
            val bMaxX = max(stepInX, rockBackX - dir * 2.0f)
            val baseCliffY = if (bz0 >= 1117f && bz1 <= 1163f) 10.6f else 0f
            val rockCol = when (idx % 3) {
                0 -> MatGraniteRockDark
                1 -> MatGraniteRock
                else -> MatGraniteRockLight
            }
            addAxisBox(out, bMinX, baseCliffY, bz0, bMaxX, topY, bz1, MatCliffMossGreen, rockCol, MatGraniteRockDark)
            // Dense emerald rainforest trees crowning the upper blasted granite cliff right above the portals!
            val treeZ = (bz0 + bz1) * 0.5f
            val treeX = frontX - dir * (3.8f + (idx % 2) * 2.4f)
            addGhatsTeakTree(out, treeX, treeZ, heightM = 11.5f + (idx % 3) * 2.2f, crownR = 4.6f, baseY = topY - 0.6f)
        }
        // Extra lush mossy boulder shelves flanking outer North & South edges of the twin portals
        addAxisBox(out, rMinX, 0f, 1106.0f, rMaxX, 15.2f, 1117.5f, MatCliffMossGreen, MatGraniteRockDark, MatGraniteRock)
        addAxisBox(out, rMinX, 0f, 1162.5f, rMaxX, 15.8f, 1174.0f, MatCliffMossGreen, MatGraniteRock, MatGraniteRockDark)
        addGhatsTeakTree(out, frontX - dir * 1.8f, 1111.0f, heightM = 12.5f, crownR = 4.5f, baseY = 14.8f)
        addGhatsTeakTree(out, frontX - dir * 1.8f, 1169.0f, heightM = 13.0f, crownR = 4.8f, baseY = 15.2f)

        // =====================================================================
        // 3. ICONIC CORAL-TERRACOTTA & BRIGHT SKY-BLUE GABLED CONTROL HOUSE
        //    + WHITE UTILITY ANNEX SHED TUCKED RIGHT BETWEEN THE TWO PORTALS!
        //    (Strictly inside Z = 1134.8..1143.6 -> >5.1m CLEAR of both tubes!)
        // =====================================================================
        val houseCenterX = frontX + dir * 11.5f          // West: 2148.5, East: 3126.5
        val hxMin = houseCenterX - 4.4f
        val hxMax = houseCenterX + 4.4f
        val hzMin = 1137.4f
        val hzMax = 1143.4f
        val wallTopY = 3.55f
        val houseRidgeY = 5.25f

        // Concrete foundation plinth
        addAxisBox(out, hxMin - 0.35f, 0.06f, hzMin - 0.35f, hxMax + 0.35f, 0.38f, hzMax + 0.35f, MatNhConcreteLight, MatNhConcreteMid, MatNhConcreteMid)
        // Warm coral-terracotta / salmon-pink plastered walls (matching the iconic photo building!)
        addAxisBox(out, hxMin, 0.38f, hzMin, hxMax, wallTopY, hzMax, MatControlHouseCoral, MatControlHouseCoral, MatControlHouseCoralShade)
        // Front shuttered doorway & twin dark-framed control room windows facing the highway approach
        val doorFaceX = if (facingWest) hxMin - 0.05f else hxMax + 0.05f
        out.add(
            QuadFace3D(
                Vec3(doorFaceX, 0.38f, 1139.6f),
                Vec3(doorFaceX, 0.38f, 1141.2f),
                Vec3(doorFaceX, 2.48f, 1141.2f),
                Vec3(doorFaceX, 2.48f, 1139.6f),
                Color(0xFF3E2723), null, -0.06f
            )
        )
        for (wz in floatArrayOf(1138.3f, 1142.3f)) {
            out.add(
                QuadFace3D(
                    Vec3(doorFaceX, 1.45f, wz - 0.48f),
                    Vec3(doorFaceX, 1.45f, wz + 0.48f),
                    Vec3(doorFaceX, 2.42f, wz + 0.48f),
                    Vec3(doorFaceX, 2.42f, wz - 0.48f),
                    Color(0xFF1E293B), null, -0.06f
                )
            )
            // Crisp white window sunshade lintel
            addAxisBox(out, doorFaceX - 0.18f, 2.42f, wz - 0.56f, doorFaceX + 0.18f, 2.58f, wz + 0.56f, MatPortalTrimWhite, MatPortalTrimWhite, MatPortalTrimWhite)
        }
        // Bright Sky-Blue / Cyan Pitched Corrugated Metal Gable Roof (exact photo signature!)
        val rfMinX = hxMin - 0.55f
        val rfMaxX = hxMax + 0.55f
        val rfMinZ = hzMin - 0.65f
        val rfMaxZ = hzMax + 0.65f
        val midZ = (hzMin + hzMax) * 0.5f
        // South sloped sky-blue roof plane
        out.add(
            QuadFace3D(
                Vec3(rfMinX, wallTopY - 0.08f, rfMinZ),
                Vec3(rfMaxX, wallTopY - 0.08f, rfMinZ),
                Vec3(rfMaxX, houseRidgeY, midZ),
                Vec3(rfMinX, houseRidgeY, midZ),
                MatControlRoofSkyBlue, null, -0.02f
            )
        )
        // North sloped sky-blue roof plane
        out.add(
            QuadFace3D(
                Vec3(rfMaxX, wallTopY - 0.08f, rfMaxZ),
                Vec3(rfMinX, wallTopY - 0.08f, rfMaxZ),
                Vec3(rfMinX, houseRidgeY, midZ),
                Vec3(rfMaxX, houseRidgeY, midZ),
                MatControlRoofSkyBlueShade, null, -0.02f
            )
        )
        // Gable triangular end walls (West & East faces of pitched roof)
        for (gx in floatArrayOf(hxMin, hxMax)) {
            out.add(
                QuadFace3D(
                    Vec3(gx, wallTopY, hzMin),
                    Vec3(gx, wallTopY, hzMax),
                    Vec3(gx, houseRidgeY - 0.06f, midZ + 0.05f),
                    Vec3(gx, houseRidgeY - 0.06f, midZ - 0.05f),
                    MatControlHouseCoralShade, null, 0f
                )
            )
        }
        // Crisp bright ridge cap bar along sky-blue roof apex
        addAxisBox(out, rfMinX, houseRidgeY - 0.08f, midZ - 0.16f, rfMaxX, houseRidgeY + 0.12f, midZ + 0.16f, MatPortalTrimWhite, MatControlRoofSkyBlue, MatControlRoofSkyBlue)

        // Small white-roofed utility pump annex shed tucked on the South/Right side of the control house (just like photo!)
        val axMin = houseCenterX - 2.2f
        val axMax = houseCenterX + 1.8f
        val azMin = 1134.8f
        val azMax = 1137.2f
        addAxisBox(out, axMin, 0.06f, azMin, axMax, 2.25f, azMax, MatPortalTrimWhite, MatNhConcreteLight, MatNhConcreteMid)
        addAxisBox(out, axMin - 0.25f, 2.25f, azMin - 0.25f, axMax + 0.25f, 2.52f, azMax + 0.25f, MatPortalTrimWhite, MatControlRoofSkyBlue, MatPortalTrimWhite)
    }

    /**
     * Photo-matched Kuthiran Approach Central Median Details:
     *  - Alternating Crisp Black & White Painted Concrete Kerbs hugging both sides of the
     *    lush green central median island & the diagonal emergency crossover slip-road.
     *  - Low grey concrete retaining divider wall with galvanized pipe rail inside the
     *    triangular green crossover island (clearly visible in the aerial photo!).
     *  - Lush low roadside grass tufts & shrubs on the green median island.
     */
    private fun buildKuthiranApproachMedianDetails(
        out: MutableList<QuadFace3D>,
        isWestApproach: Boolean
    ) {
        fun addBlackWhiteKerbLine(x0: Float, z0: Float, x1: Float, z1: Float, segLen: Float = 2.4f) {
            val dx = x1 - x0
            val dz = z1 - z0
            val totalLen = hypot(dx, dz)
            if (totalLen < 0.8f) return
            val steps = max(1, (totalLen / segLen).toInt())
            for (k in 0 until steps) {
                val t0 = k.toFloat() / steps
                val t1 = (k + 1).toFloat() / steps
                val sx0 = x0 + dx * t0; val sz0 = z0 + dz * t0
                val sx1 = x0 + dx * t1; val sz1 = z0 + dz * t1
                val col = if (k % 2 == 0) MatPortalTrimWhite else MatChevronBlack
                addHorizontalBeam(out, sx0, sz0, sx1, sz1, 0.06f, 0.28f, 0.18f, col)
            }
        }

        if (isWestApproach) {
            // North edge of green median (hugging Left Tube inner asphalt edge)
            addBlackWhiteKerbLine(2020.0f, 1140.4f, 2085.0f, 1145.6f)
            addBlackWhiteKerbLine(2099.0f, 1146.6f, 2134.0f, 1148.1f)
            addBlackWhiteKerbLine(2144.0f, 1148.2f, 2158.0f, 1148.3f)
            // South edge of green median (hugging Right Tube inner asphalt edge)
            addBlackWhiteKerbLine(2020.0f, 1139.6f, 2123.0f, 1131.9f)
            addBlackWhiteKerbLine(2139.0f, 1131.7f, 2158.0f, 1131.6f)
            // Diagonal crossover slip-road inner kerbs framing the signature green triangle!
            addBlackWhiteKerbLine(2086.0f, 1145.4f, 2123.5f, 1132.1f)
            addBlackWhiteKerbLine(2099.0f, 1146.2f, 2134.0f, 1133.4f)
            addBlackWhiteKerbLine(2134.5f, 1147.8f, 2133.2f, 1134.0f)

            // Low concrete retaining parapet wall + galvanized pipe rail inside the triangular island (photo detail!)
            addHorizontalBeam(out, 2107.0f, 1144.2f, 2127.0f, 1138.8f, 0.08f, 0.92f, 0.26f, MatNhConcreteMid)
            addHorizontalBeam(out, 2107.0f, 1144.2f, 2127.0f, 1138.8f, 0.92f, 1.06f, 0.28f, MatNhConcreteWhite)
            addHorizontalBeam(out, 2107.2f, 1144.1f, 2126.8f, 1138.9f, 1.32f, 1.40f, 0.05f, MatGalvanizedSteel)

            // Low natural tropical grass/bush clusters on the green median & inside the crossover triangle
            val bushCoords = arrayOf(
                Pair(2052f, 1140.0f), Pair(2066f, 1139.2f), Pair(2074f, 1141.0f),
                Pair(2084f, 1137.8f), Pair(2096f, 1136.4f), Pair(2118f, 1143.6f),
                Pair(2124f, 1144.8f), Pair(2114f, 1145.0f)
            )
            for ((bx, bz) in bushCoords) {
                addAxisBox(out, bx - 1.4f, 0.08f, bz - 1.1f, bx + 1.4f, 0.72f, bz + 1.1f, MatGhatsForestLight, MatGhatsForestMid, MatGhatsForestDark)
            }
        } else {
            // Mirrored East Approach black & white kerbs + triangular crossover retaining wall
            addBlackWhiteKerbLine(3117.0f, 1148.3f, 3176.0f, 1146.2f)
            addBlackWhiteKerbLine(3190.0f, 1145.6f, 3255.0f, 1140.4f)
            addBlackWhiteKerbLine(3117.0f, 1131.6f, 3136.0f, 1131.7f)
            addBlackWhiteKerbLine(3152.0f, 1131.9f, 3255.0f, 1139.6f)
            addBlackWhiteKerbLine(3151.5f, 1132.1f, 3189.0f, 1145.4f)
            addHorizontalBeam(out, 3148.0f, 1138.8f, 3168.0f, 1144.2f, 0.08f, 0.92f, 0.26f, MatNhConcreteMid)
            addHorizontalBeam(out, 3148.0f, 1138.8f, 3168.0f, 1144.2f, 0.92f, 1.06f, 0.28f, MatNhConcreteWhite)
        }
    }

    private fun buildKuthiranVmsGantry(
        out: MutableList<QuadFace3D>,
        gantryX: Float,
        facingWest: Boolean
    ) {
        val dir = if (facingWest) -1f else 1f
        // Spans cleanly over BOTH Left & Right approach carriageways with 3 columns strictly outside asphalt:
        // South outer column (Z = 1117.0), Central divider column (Z = 1140.0), North outer column (Z = 1163.0)
        val colZs = floatArrayOf(1117.0f, KUTHIRAN_CORRIDOR_CENTER_Z, 1163.0f)
        for (cz in colZs) {
            addAxisBox(out, gantryX - 0.42f, 0f, cz - 0.42f, gantryX + 0.42f, 8.7f, cz + 0.42f, MatGalvanizedSteel, MatGalvanizedSteel, MatGalvanizedDark)
        }
        // Overhead galvanized truss span across both tubes
        addAxisBox(out, gantryX - 0.35f, 8.15f, 1116.8f, gantryX + 0.35f, 8.60f, 1163.2f, MatGalvanizedSteel, MatGalvanizedDark, MatGalvanizedSteel)
        addAxisBox(out, gantryX - 0.30f, 6.25f, 1116.8f, gantryX + 0.30f, 6.55f, 1163.2f, MatGalvanizedSteel, MatGalvanizedDark, MatGalvanizedSteel)

        val vmsSpec = createKuthiranVmsSignSpec("ZONE2_KUTHIRAN_VMS_LED_V1")
        val faceX = gantryX + dir * 0.42f
        for (tubeCenterZ in floatArrayOf(KUTHIRAN_LEFT_CENTER_Z, KUTHIRAN_RIGHT_CENTER_Z)) {
            val z0 = tubeCenterZ - 6.2f
            val z1 = tubeCenterZ + 6.2f
            if (facingWest) {
                out.add(
                    QuadFace3D(
                        Vec3(faceX, 6.35f, z1),
                        Vec3(faceX, 6.35f, z0),
                        Vec3(faceX, 8.50f, z0),
                        Vec3(faceX, 8.50f, z1),
                        Color(0xFF090D16), null, -0.30f, signboard = vmsSpec
                    )
                )
            } else {
                out.add(
                    QuadFace3D(
                        Vec3(faceX, 6.35f, z0),
                        Vec3(faceX, 6.35f, z1),
                        Vec3(faceX, 8.50f, z1),
                        Vec3(faceX, 8.50f, z0),
                        Color(0xFF090D16), null, -0.30f, signboard = vmsSpec
                    )
                )
            }
        }
    }

    private fun buildSingleTunnelTubeInterior(
        out: MutableList<QuadFace3D>,
        xStart: Float,
        xEnd: Float,
        centerZ: Float,
        isLeftTube: Boolean
    ) {
        val southWallZ = centerZ - KUTHIRAN_TUBE_HALF_WIDTH      // -7.00m
        val northWallZ = centerZ + KUTHIRAN_TUBE_HALF_WIDTH      // +7.00m
        val southCurbZ = centerZ - KUTHIRAN_ASPHALT_HALF_W       // -5.25m
        val northCurbZ = centerZ + KUTHIRAN_ASPHALT_HALF_W       // +5.25m

        val archSegs = 10
        val archZ = FloatArray(archSegs + 1)
        val archY = FloatArray(archSegs + 1)
        for (s in 0..archSegs) {
            val ang = (s.toFloat() / archSegs) * PI.toFloat()
            archZ[s] = centerZ - cos(ang) * KUTHIRAN_TUBE_HALF_WIDTH
            archY[s] = KUTHIRAN_WALL_HAUNCH_Y + sin(ang) * (KUTHIRAN_VAULT_CROWN_Y - KUTHIRAN_WALL_HAUNCH_Y)
        }

        // Segment the 955m / 944m tube into ~24m modular concrete shotcrete arch rings
        val segLen = 24.0f
        var x = xStart
        var ringIdx = 0
        while (x < xEnd - 0.1f) {
            val xNext = min(x + segLen, xEnd)
            val distFromWest = x - xStart
            val distFromEast = xEnd - xNext
            val inPortalThroat = distFromWest < 24.0f || distFromEast < 24.0f
            val inAdaptZone = !inPortalThroat && (distFromWest < 110.0f || distFromEast < 110.0f)

            // Realistic portal depth gradient: shadowed dark-slate entrance throat (matches the real photo!)
            // transitioning cleanly into bright, warmly lit concrete shotcrete lining inside the bore.
            val vaultCol = when {
                inPortalThroat -> Color(0xFF282E38)
                inAdaptZone -> Color(0xFFCFD8E3)
                ringIdx % 2 == 0 -> MatShotcreteVault
                else -> MatShotcreteMid
            }
            val sideWallCol = if (inPortalThroat) Color(0xFF343C47) else MatShotcreteSplash

            // 1. Lower Splash-Zone Concrete Sidewalls (Y = 0.42 .. 3.50m)
            out.add(
                QuadFace3D(
                    Vec3(x, KUTHIRAN_WALKWAY_H, southWallZ),
                    Vec3(xNext, KUTHIRAN_WALKWAY_H, southWallZ),
                    Vec3(xNext, KUTHIRAN_WALL_HAUNCH_Y, southWallZ),
                    Vec3(x, KUTHIRAN_WALL_HAUNCH_Y, southWallZ),
                    sideWallCol, null, 0f, 0f, 0f, 0f, 1f
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(xNext, KUTHIRAN_WALKWAY_H, northWallZ),
                    Vec3(x, KUTHIRAN_WALKWAY_H, northWallZ),
                    Vec3(x, KUTHIRAN_WALL_HAUNCH_Y, northWallZ),
                    Vec3(xNext, KUTHIRAN_WALL_HAUNCH_Y, northWallZ),
                    sideWallCol, null, 0f, 0f, 0f, 0f, -1f
                )
            )

            // 2. Modified Semi-Circular Horseshoe Arch Vault (10 smooth segments for true circular profile)
            for (s in 0 until archSegs) {
                val segCol = when {
                    inPortalThroat -> if (s in 3..6) Color(0xFF1F242C) else Color(0xFF303742)
                    s == 0 || s == archSegs - 1 -> MatShotcreteMid
                    else -> vaultCol
                }
                out.add(
                    QuadFace3D(
                        Vec3(x, archY[s], archZ[s]),
                        Vec3(xNext, archY[s], archZ[s]),
                        Vec3(xNext, archY[s + 1], archZ[s + 1]),
                        Vec3(x, archY[s + 1], archZ[s + 1]),
                        segCol, null, 0f
                    )
                )
            }

            // 3. Raised 1.75m Inspection Walkways on BOTH Sides of each Tube
            out.add(
                QuadFace3D(
                    Vec3(x, KUTHIRAN_WALKWAY_H, southWallZ),
                    Vec3(xNext, KUTHIRAN_WALKWAY_H, southWallZ),
                    Vec3(xNext, KUTHIRAN_WALKWAY_H, southCurbZ),
                    Vec3(x, KUTHIRAN_WALKWAY_H, southCurbZ),
                    MatNhConcreteLight, null, 0f, 0f, 0f, 1f, 0f
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(x, KUTHIRAN_WALKWAY_H, northCurbZ),
                    Vec3(xNext, KUTHIRAN_WALKWAY_H, northCurbZ),
                    Vec3(xNext, KUTHIRAN_WALKWAY_H, northWallZ),
                    Vec3(x, KUTHIRAN_WALKWAY_H, northWallZ),
                    MatNhConcreteLight, null, 0f, 0f, 0f, 1f, 0f
                )
            )

            // 4. Bold Alternating Yellow-and-Black Chevron Curbs (4m readable blocks along 24m ring) + Red Cat's Eyes
            val subCount = 6
            val subLen = (xNext - x) / subCount
            for (k in 0 until subCount) {
                val sx0 = x + k * subLen
                val sx1 = sx0 + subLen
                val curbCol = if ((ringIdx * subCount + k) % 2 == 0) MatChevronYellow else MatChevronBlack
                // South curb vertical face (+Z normal)
                out.add(
                    QuadFace3D(
                        Vec3(sx0, 0.06f, southCurbZ),
                        Vec3(sx1, 0.06f, southCurbZ),
                        Vec3(sx1, KUTHIRAN_WALKWAY_H, southCurbZ),
                        Vec3(sx0, KUTHIRAN_WALKWAY_H, southCurbZ),
                        curbCol, null, -0.04f
                    )
                )
                // North curb vertical face (-Z normal)
                out.add(
                    QuadFace3D(
                        Vec3(sx1, 0.06f, northCurbZ),
                        Vec3(sx0, 0.06f, northCurbZ),
                        Vec3(sx0, KUTHIRAN_WALKWAY_H, northCurbZ),
                        Vec3(sx1, KUTHIRAN_WALKWAY_H, northCurbZ),
                        curbCol, null, -0.04f
                    )
                )
                // Glowing Red Glass Cat's Eye Reflectors every 8m on curb lip
                if (k % 2 == 0) {
                    val mx = (sx0 + sx1) * 0.5f
                    out.add(
                        QuadFace3D(
                            Vec3(mx - 0.16f, 0.14f, southCurbZ + 0.04f),
                            Vec3(mx + 0.16f, 0.14f, southCurbZ + 0.04f),
                            Vec3(mx + 0.16f, 0.30f, southCurbZ + 0.04f),
                            Vec3(mx - 0.16f, 0.30f, southCurbZ + 0.04f),
                            MatCatsEyeRed, null, -0.10f
                        )
                    )
                    out.add(
                        QuadFace3D(
                            Vec3(mx + 0.16f, 0.14f, northCurbZ - 0.04f),
                            Vec3(mx - 0.16f, 0.14f, northCurbZ - 0.04f),
                            Vec3(mx - 0.16f, 0.30f, northCurbZ - 0.04f),
                            Vec3(mx + 0.16f, 0.30f, northCurbZ - 0.04f),
                            MatCatsEyeRed, null, -0.10f
                        )
                    )
                }
            }

            // 5. Dual Continuous Linear 5000K LED Lighting Strips along Upper Vault Haunches
            val ledSouthZ = centerZ - 5.35f
            val ledNorthZ = centerZ + 5.35f
            val ledY = 6.25f
            val ledCol = if (inAdaptZone) MatLedAdapt3000Lux else MatLedDaylight5000K
            out.add(
                QuadFace3D(
                    Vec3(x, ledY - 0.18f, ledSouthZ),
                    Vec3(xNext, ledY - 0.18f, ledSouthZ),
                    Vec3(xNext, ledY + 0.18f, ledSouthZ + 0.25f),
                    Vec3(x, ledY + 0.18f, ledSouthZ + 0.25f),
                    ledCol, null, -0.14f
                )
            )
            out.add(
                QuadFace3D(
                    Vec3(xNext, ledY - 0.18f, ledNorthZ),
                    Vec3(x, ledY - 0.18f, ledNorthZ),
                    Vec3(x, ledY + 0.18f, ledNorthZ - 0.25f),
                    Vec3(xNext, ledY + 0.18f, ledNorthZ - 0.25f),
                    ledCol, null, -0.14f
                )
            )

            // 6. Entrance "Adaptation Threshold" (first 100m inside West & East portals):
            //    Triple-row High-Intensity 3000-lux Daylight Overhead LED Arrays!
            if (inAdaptZone) {
                for (offZ in floatArrayOf(-2.4f, 0.0f, 2.4f)) {
                    val az = centerZ + offZ
                    val ay = KUTHIRAN_VAULT_CROWN_Y - 0.22f - abs(offZ) * 0.12f
                    out.add(
                        QuadFace3D(
                            Vec3(x + 1.0f, ay, az - 0.35f),
                            Vec3(xNext - 1.0f, ay, az - 0.35f),
                            Vec3(xNext - 1.0f, ay, az + 0.35f),
                            Vec3(x + 1.0f, ay, az + 0.35f),
                            MatLedAdapt3000Lux, null, -0.18f
                        )
                    )
                }
            }

            x = xNext
            ringIdx++
        }

        // 7. Paired Heavy Cylindrical Reversible Jet Fans (1.2m diameter) every 120m mounted to vault ceiling
        var fanX = xStart + 80.0f
        while (fanX < xEnd - 40.0f) {
            for (fanOffZ in floatArrayOf(-2.15f, 2.15f)) {
                val fz = centerZ + fanOffZ
                val fanTopY = KUTHIRAN_VAULT_CROWN_Y - 0.32f
                val fanBotY = fanTopY - 1.20f
                // Ceiling steel suspension brackets
                addAxisBox(out, fanX - 1.2f, fanTopY, fz - 0.22f, fanX + 1.2f, KUTHIRAN_VAULT_CROWN_Y + 0.05f, fz + 0.22f, MatGalvanizedDark, MatGalvanizedDark, MatGalvanizedDark)
                // Cylindrical stainless steel jet fan housing (3.4m long x 1.2m dia octagonal/box housing)
                addAxisBox(out, fanX - 1.70f, fanBotY, fz - 0.60f, fanX + 1.70f, fanTopY, fz + 0.60f, MatJetFanSteel, MatJetFanSteel, MatJetFanIntake)
                // Dark intake/exhaust safety grilles on West & East ends
                out.add(
                    QuadFace3D(
                        Vec3(fanX - 1.73f, fanBotY + 0.12f, fz - 0.48f),
                        Vec3(fanX - 1.73f, fanBotY + 0.12f, fz + 0.48f),
                        Vec3(fanX - 1.73f, fanTopY - 0.12f, fz + 0.48f),
                        Vec3(fanX - 1.73f, fanTopY - 0.12f, fz - 0.48f),
                        MatJetFanIntake, null, -0.12f
                    )
                )
                out.add(
                    QuadFace3D(
                        Vec3(fanX + 1.73f, fanBotY + 0.12f, fz + 0.48f),
                        Vec3(fanX + 1.73f, fanBotY + 0.12f, fz - 0.48f),
                        Vec3(fanX + 1.73f, fanTopY - 0.12f, fz - 0.48f),
                        Vec3(fanX + 1.73f, fanTopY - 0.12f, fz + 0.48f),
                        MatJetFanIntake, null, -0.12f
                    )
                )
            }
            fanX += 120.0f
        }

        // 8. 3 Emergency Cross-Passages every 300m (X = 2340, 2640, 2940) on the inner dividing-pillar wall:
        //    Bright Signal-Green Fire-Rated Steel Doors + Illuminated Running-Man Emergency Exit Signboards!
        val crossWallZ = if (isLeftTube) southWallZ + 0.08f else northWallZ - 0.08f
        val crossSignSpec = createEmergencyExitSignSpec("ZONE2_TUNNEL_EXIT_SIGN_V1")
        for (cx in floatArrayOf(2340.0f, 2640.0f, 2940.0f)) {
            // Concrete portal frame surround
            out.add(
                QuadFace3D(
                    Vec3(cx - 1.45f, KUTHIRAN_WALKWAY_H, crossWallZ),
                    Vec3(cx + 1.45f, KUTHIRAN_WALKWAY_H, crossWallZ),
                    Vec3(cx + 1.45f, 3.25f, crossWallZ),
                    Vec3(cx - 1.45f, 3.25f, crossWallZ),
                    MatEmergencyGreenBright, null, -0.06f
                )
            )
            // Bright signal-green fire-rated steel door
            val doorZ = if (isLeftTube) crossWallZ + 0.04f else crossWallZ - 0.04f
            out.add(
                QuadFace3D(
                    Vec3(cx - 1.10f, KUTHIRAN_WALKWAY_H, doorZ),
                    Vec3(cx + 1.10f, KUTHIRAN_WALKWAY_H, doorZ),
                    Vec3(cx + 1.10f, 2.95f, doorZ),
                    Vec3(cx - 1.10f, 2.95f, doorZ),
                    MatEmergencyGreen, null, -0.10f
                )
            )
            // Illuminated Emergency Exit Running-Man Signboard above door
            val signZ = if (isLeftTube) crossWallZ + 0.06f else crossWallZ - 0.06f
            if (isLeftTube) {
                out.add(
                    QuadFace3D(
                        Vec3(cx - 1.35f, 3.30f, signZ),
                        Vec3(cx + 1.35f, 3.30f, signZ),
                        Vec3(cx + 1.35f, 4.15f, signZ),
                        Vec3(cx - 1.35f, 4.15f, signZ),
                        MatEmergencyGreen, null, -0.15f, signboard = crossSignSpec
                    )
                )
            } else {
                out.add(
                    QuadFace3D(
                        Vec3(cx + 1.35f, 3.30f, signZ),
                        Vec3(cx - 1.35f, 3.30f, signZ),
                        Vec3(cx - 1.35f, 4.15f, signZ),
                        Vec3(cx + 1.35f, 4.15f, signZ),
                        MatEmergencyGreen, null, -0.15f, signboard = crossSignSpec
                    )
                )
            }
        }

        // 9. Recessed Emergency Niches every 100m along outer tunnel wall:
        //    Bright Yellow Emergency SOS Call Box + Red Fire Hydrant Hose Cabinet!
        val outerNicheZ = if (isLeftTube) northWallZ - 0.08f else southWallZ + 0.08f
        var nx = xStart + 80.0f
        while (nx < xEnd - 40.0f) {
            // Bright Yellow Emergency SOS Call Box
            out.add(
                QuadFace3D(
                    Vec3(nx - 1.15f, 1.05f, outerNicheZ),
                    Vec3(nx - 0.25f, 1.05f, outerNicheZ),
                    Vec3(nx - 0.25f, 2.35f, outerNicheZ),
                    Vec3(nx - 1.15f, 2.35f, outerNicheZ),
                    MatSosYellow, null, -0.10f
                )
            )
            // Bright Red Fire Hydrant Hose Cabinet
            out.add(
                QuadFace3D(
                    Vec3(nx + 0.15f, 0.90f, outerNicheZ),
                    Vec3(nx + 1.20f, 0.90f, outerNicheZ),
                    Vec3(nx + 1.20f, 2.40f, outerNicheZ),
                    Vec3(nx + 0.15f, 2.40f, outerNicheZ),
                    MatHydrantRed, null, -0.10f
                )
            )
            nx += 100.0f
        }
    }

    private fun buildApproachRockCuttings(
        out: MutableList<QuadFace3D>,
        xStart: Float,
        xEnd: Float,
        isWestApproach: Boolean
    ) {
        // Multi-tiered Blasted-Granite Western Ghats Approach Rock Cuttings stabilized with
        // shotcrete benches, rock-bolt ledges & natural granite outcrops — strictly >= 6.5m outside road edges!
        val steps = 10
        val dx = (xEnd - xStart) / steps
        for (i in 0 until steps) {
            val x0 = xStart + i * dx
            val x1 = x0 + dx
            val t0 = if (isWestApproach) i.toFloat() / steps else 1f - i.toFloat() / steps
            val t1 = if (isWestApproach) (i + 1).toFloat() / steps else 1f - (i + 1).toFloat() / steps

            val h0 = 5.5f + t0 * 21.0f
            val h1 = 5.5f + t1 * 21.0f
            val footOff0 = 19.5f + t0 * 9.0f
            val footOff1 = 19.5f + t1 * 9.0f

            for (side in floatArrayOf(-1f, 1f)) {
                val zFoot0 = KUTHIRAN_CORRIDOR_CENTER_Z + side * footOff0
                val zFoot1 = KUTHIRAN_CORRIDOR_CENTER_Z + side * footOff1
                val zBermIn0 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff0 + 9.0f)
                val zBermIn1 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff1 + 9.0f)
                val zBermOut0 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff0 + 15.0f)
                val zBermOut1 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff1 + 15.0f)
                val zCrest0 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff0 + 27.0f)
                val zCrest1 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff1 + 27.0f)
                val zBack0 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff0 + 58.0f)
                val zBack1 = KUTHIRAN_CORRIDOR_CENTER_Z + side * (footOff1 + 58.0f)

                val lowerCol = when (i % 3) {
                    0 -> MatGraniteRockDark
                    1 -> MatGraniteRock
                    else -> MatGraniteRockDark
                }
                val upperRockCol = if (i % 2 == 0) MatGraniteRockLight else MatGraniteRock
                val forestCol = if (i % 2 == 0) MatGhatsForestDark else MatGhatsForestMid

                // Tier 1: Steep dark blasted-granite Western Ghats cliff rising directly beside the approach
                out.add(
                    QuadFace3D(
                        Vec3(x0, 0.0f, zFoot0),
                        Vec3(x1, 0.0f, zFoot1),
                        Vec3(x1, h1 * 0.56f, zBermIn1),
                        Vec3(x0, h0 * 0.56f, zBermIn0),
                        lowerCol, null, 0f
                    )
                )
                // Tier 2: Lush mossy green ledge terrace (dense creeping vines & ferns!)
                out.add(
                    QuadFace3D(
                        Vec3(x0, h0 * 0.56f, zBermIn0),
                        Vec3(x1, h1 * 0.56f, zBermIn1),
                        Vec3(x1, h1 * 0.65f, zBermOut1),
                        Vec3(x0, h0 * 0.65f, zBermOut0),
                        MatCliffMossGreen, null, 0f
                    )
                )
                // Tier 3: Upper rugged dark granite crag crowned with dense tropical forest canopy
                out.add(
                    QuadFace3D(
                        Vec3(x0, h0 * 0.65f, zBermOut0),
                        Vec3(x1, h1 * 0.65f, zBermOut1),
                        Vec3(x1, h1, zCrest1),
                        Vec3(x0, h0, zCrest0),
                        upperRockCol, null, 0f
                    )
                )
                // Tier 4: Lush dark-emerald rainforest slope blanketing the Kuthiran valley flanks
                out.add(
                    QuadFace3D(
                        Vec3(x0, h0, zCrest0),
                        Vec3(x1, h1, zCrest1),
                        Vec3(x1, h1 * 0.30f, zBack1),
                        Vec3(x0, h0 * 0.30f, zBack0),
                        forestCol, null, 0f
                    )
                )
                // Dense 3D Western Ghats tropical rainforest trees perched along the rocky ridge & terrace!
                val midX = (x0 + x1) * 0.5f
                val midH = (h0 + h1) * 0.5f
                val midBermZ = (zBermIn0 + zBermOut0) * 0.5f
                val midCrestZ = (zCrest0 + zCrest1) * 0.5f
                if (i >= 1) {
                    addGhatsTeakTree(out, midX, midCrestZ, heightM = 11.0f + (i % 3) * 2.5f, crownR = 4.6f, baseY = midH - 0.5f)
                }
                if (i % 2 == 1) {
                    addGhatsTeakTree(out, midX, midBermZ, heightM = 8.5f + (i % 2) * 1.8f, crownR = 3.5f, baseY = midH * 0.56f)
                }
            }
        }

        // Dark Wet Granite Cliff Rock Chute behind the GPU Animated Monsoon Waterfall (West Portal South Cutting)
        if (isWestApproach) {
            val wfX0 = 2123.6f
            val wfX1 = 2132.0f
            out.add(
                QuadFace3D(
                    Vec3(wfX0, 0.1f, KUTHIRAN_CORRIDOR_CENTER_Z - 28.8f),
                    Vec3(wfX1, 0.1f, KUTHIRAN_CORRIDOR_CENTER_Z - 28.8f),
                    Vec3(wfX1 - 0.7f, 17.6f, KUTHIRAN_CORRIDOR_CENTER_Z - 37.8f),
                    Vec3(wfX0 + 0.7f, 17.6f, KUTHIRAN_CORRIDOR_CENTER_Z - 37.8f),
                    Color(0xFF283036), null, -0.02f
                )
            )
            // Natural granite rim boulders framing the rocky cliff plunge pool (strictly Z <= 1114.6, +5.4m clear of road!)
            addAxisBox(out, 2120.8f, 0.05f, 1109.2f, 2122.0f, 0.65f, 1114.4f, MatGraniteRockDark, MatGraniteRock, MatGraniteRockDark)
            addAxisBox(out, 2133.6f, 0.05f, 1109.2f, 2134.8f, 0.60f, 1114.4f, MatGraniteRockDark, MatGraniteRock, MatGraniteRockDark)
        }
    }

    private fun buildKuthiranMalaMountainMassif(out: MutableList<QuadFace3D>) {
        // Multi-peak, 16-segment, 4-contour-tier Sculpted 3D Western Ghats Granite Mountain Massif
        // rising organically up to Y = 68m over Kuthiran Twin-Tube Tunnel (X = 2160 .. 3115, Z = 1025 .. 1255).
        // Real 3D textured instanced trees & grass sit directly on kuthiranMountainSurfaceY(x, z)!
        val segCount = 16
        val dx = (KUTHIRAN_EAST_PORTAL_X - KUTHIRAN_WEST_PORTAL_X) / segCount
        val zOffsets = floatArrayOf(-115f, -86f, -54f, -22f, 0f, 22f, 54f, 86f, 115f)

        for (i in 0 until segCount) {
            val x0 = KUTHIRAN_WEST_PORTAL_X + i * dx
            val x1 = x0 + dx
            for (k in 0 until zOffsets.size - 1) {
                val z0 = KUTHIRAN_CORRIDOR_CENTER_Z + zOffsets[k]
                val z1 = KUTHIRAN_CORRIDOR_CENTER_Z + zOffsets[k + 1]
                val y00 = kuthiranMountainSurfaceY(x0, z0)
                val y10 = kuthiranMountainSurfaceY(x1, z0)
                val y11 = kuthiranMountainSurfaceY(x1, z1)
                val y01 = kuthiranMountainSurfaceY(x0, z1)

                val avgAbsZ = abs((zOffsets[k] + zOffsets[k + 1]) * 0.5f)
                val faceCol = when {
                    avgAbsZ >= 70f -> if ((i + k) % 2 == 0) MatGraniteRockDark else MatGraniteRock
                    avgAbsZ >= 38f -> if ((i + k) % 3 == 0) MatGraniteRockLight else MatGhatsForestDark
                    else -> if ((i + k) % 2 == 0) MatGhatsForestMid else MatGhatsForestDark
                }
                out.add(
                    QuadFace3D(
                        Vec3(x0, y00, z0),
                        Vec3(x1, y10, z0),
                        Vec3(x1, y11, z1),
                        Vec3(x0, y01, z1),
                        faceCol, null, 0f
                    )
                )
            }
        }

        // West & East Natural Faceted Mountain End-Slopes wrapping seamlessly around the concrete portals!
        for ((endX, innerX) in arrayOf(Pair(KUTHIRAN_WEST_PORTAL_X, KUTHIRAN_WEST_PORTAL_X + dx), Pair(KUTHIRAN_EAST_PORTAL_X, KUTHIRAN_EAST_PORTAL_X - dx))) {
            // Faceted green ridge slopes rising directly above the concrete portal crown (Y = 18.8m -> 38m+)
            val portalZs = floatArrayOf(1108f, 1126f, 1140f, 1154f, 1172f)
            for (p in 0 until portalZs.size - 1) {
                val za = portalZs[p]
                val zb = portalZs[p + 1]
                val yInA = kuthiranMountainSurfaceY(innerX, za)
                val yInB = kuthiranMountainSurfaceY(innerX, zb)
                out.add(
                    QuadFace3D(
                        Vec3(endX, 18.8f, za),
                        Vec3(endX, 18.8f, zb),
                        Vec3(innerX, yInB, zb),
                        Vec3(innerX, yInA, za),
                        if (p % 2 == 0) MatGhatsForestDark else MatGhatsForestMid, null, 0f
                    )
                )
            }
            // Stepped granite shoulder cheeks flanking South (1025..1108) & North (1172..1255) of portal headwalls
            val southCheekZs = floatArrayOf(1025f, 1054f, 1086f, 1108f)
            for (s in 0 until southCheekZs.size - 1) {
                val za = southCheekZs[s]
                val zb = southCheekZs[s + 1]
                val yEndA = if (s == 0) 0f else kuthiranMountainSurfaceY(endX, za).coerceAtMost(18.8f)
                val yEndB = if (s == southCheekZs.size - 2) 18.8f else kuthiranMountainSurfaceY(endX, zb).coerceAtMost(18.8f)
                out.add(
                    QuadFace3D(
                        Vec3(endX, yEndA, za),
                        Vec3(endX, yEndB, zb),
                        Vec3(innerX, kuthiranMountainSurfaceY(innerX, zb), zb),
                        Vec3(innerX, kuthiranMountainSurfaceY(innerX, za), za),
                        if (s % 2 == 0) MatGraniteRock else MatGraniteRockLight, null, 0f
                    )
                )
            }
            val northCheekZs = floatArrayOf(1172f, 1194f, 1226f, 1255f)
            for (n in 0 until northCheekZs.size - 1) {
                val za = northCheekZs[n]
                val zb = northCheekZs[n + 1]
                val yEndA = if (n == 0) 18.8f else kuthiranMountainSurfaceY(endX, za).coerceAtMost(18.8f)
                val yEndB = if (n == northCheekZs.size - 2) 0f else kuthiranMountainSurfaceY(endX, zb).coerceAtMost(18.8f)
                out.add(
                    QuadFace3D(
                        Vec3(endX, yEndA, za),
                        Vec3(endX, yEndB, zb),
                        Vec3(innerX, kuthiranMountainSurfaceY(innerX, zb), zb),
                        Vec3(innerX, kuthiranMountainSurfaceY(innerX, za), za),
                        if (n % 2 == 0) MatGraniteRockLight else MatGraniteRock, null, 0f
                    )
                )
            }
        }
    }

    // =========================================================================
    // 4. PEECHI DAM BRANCH ROAD (SH 51): WILDLIFE CHECK-POST, WALLED BUS TERMINAL
    //    (ENTRY/EXIT GATES, GUARD BOOTH, BAYS, PAVILION) & MONUMENTAL DAM SPILLWAY
    // =========================================================================
    private fun buildPeechiForestRoadAndDamSpillway(
        roads: List<ThrissurOsmRoad>,
        out: MutableList<QuadFace3D>
    ) {
        // A. Peechi-Vazhani Wildlife Sanctuary Concrete Check-Post Gate (on SH 51 at 3123.09, 51.27)
        //    Pillars & guard cabins sit at +-10.6m lateral (strictly +3.35m CLEAR outside 14.5m SH 51!)
        val cx = PEECHI_CHECKPOST_X
        val cz = PEECHI_CHECKPOST_Z
        val rx = PEECHI_CHECKPOST_RX
        val rz = PEECHI_CHECKPOST_RZ
        val tx = PEECHI_CHECKPOST_TX
        val tz = PEECHI_CHECKPOST_TZ

        val leftX = cx - rx * 10.6f
        val leftZ = cz - rz * 10.6f
        val rightX = cx + rx * 10.6f
        val rightZ = cz + rz * 10.6f

        // Left & Right Forest Check-Post Masonry Pillars + Forest Guard Booths
        addVerticalBeam(out, leftX, leftZ, 0f, 7.6f, 0.72f, MatNhConcreteWhite)
        addVerticalBeam(out, rightX, rightZ, 0f, 7.6f, 0.72f, MatNhConcreteWhite)
        val boothX = cx + rx * 13.4f
        val boothZ = cz + rz * 13.4f
        addAxisBox(out, boothX - 2.1f, 0f, boothZ - 2.1f, boothX + 2.1f, 3.2f, boothZ + 2.1f, MatNhConcreteWhite, MatNhConcreteLight, Color(0xFF14532D))
        addAxisBox(out, boothX - 2.5f, 3.2f, boothZ - 2.5f, boothX + 2.5f, 3.7f, boothZ + 2.5f, Color(0xFF991B1B), Color(0xFF7F1D1D), Color(0xFF7F1D1D))

        // Overhead Forest Arch Beam (clearance Y = 6.20m -> 7.80m, 100% open for tall buses!)
        addHorizontalBeam(out, leftX - rx * 0.8f, leftZ - rz * 0.8f, rightX + rx * 0.8f, rightZ + rz * 0.8f, 6.20f, 7.80f, 0.55f, Color(0xFF14532D))

        val checkPostSign = createBannerSignSpec(
            cacheKey = "ZONE2_PEECHI_CHECKPOST_ARCH_V1",
            bgColorInt = 0xFF14532D.toInt(),
            borderColorInt = 0xFFFACC15.toInt(),
            titleText = "പീച്ചി-വാഴാനി വന്യജീവി സങ്കേതം • PEECHI WILDLIFE CHECK POST",
            subText = "KERALA FOREST & WILDLIFE DEPT • DRIVE SLOW • NO PLASTIC • SH 51",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 960,
            bmpHeight = 160
        )
        val pL = Vec3(leftX + rx * 0.8f - tx * 0.62f, 6.28f, leftZ + rz * 0.8f - tz * 0.62f)
        val pR = Vec3(rightX - rx * 0.8f - tx * 0.62f, 6.28f, rightZ - rz * 0.8f - tz * 0.62f)
        out.add(
            QuadFace3D(
                pL, pR,
                Vec3(pR.x, 7.72f, pR.z),
                Vec3(pL.x, 7.72f, pL.z),
                Color(0xFF14532D), null, -0.25f, signboard = checkPostSign
            )
        )

        // B. Yellow & Black Chevron Curve Warning Posts along SH 51 Forest Bends
        //    (All forest teak trees, palms & undergrowth along SH 51 are real 3D instanced models!)
        val peechiRoad = roads.firstOrNull { it.id == "ROAD_MANNUTHY_TO_PEECHI_DAM" }
        if (peechiRoad != null && peechiRoad.frames.size > 30) {
            val frames = peechiRoad.frames
            var fIdx = 28
            while (fIdx < frames.size - 14) {
                val f = frames[fIdx]
                if (hypot(f.pos.x - PEECHI_CHECKPOST_X, f.pos.z - PEECHI_CHECKPOST_Z) > 26f) {
                    val prevF = frames[max(0, fIdx - 4)]
                    val nextF = frames[min(frames.size - 1, fIdx + 4)]
                    val turnDot = prevF.tangent.dotXZ(nextF.tangent)
                    if (turnDot < 0.94f) {
                        val outerSide = if ((prevF.tangent.x * nextF.tangent.z - prevF.tangent.z * nextF.tangent.x) >= 0f) 1f else -1f
                        val sx = f.pos.x + f.right.x * outerSide * 9.4f
                        val sz = f.pos.z + f.right.z * outerSide * 9.4f
                        addVerticalBeam(out, sx, sz, 0f, 2.4f, 0.08f, MatGalvanizedSteel)
                        addAxisBox(out, sx - 0.45f, 1.45f, sz - 0.45f, sx + 0.45f, 2.35f, sz + 0.45f, MatChevronYellow, MatChevronYellow, MatChevronBlack)
                    }
                }
                fIdx += 18
            }
        }

        // C. REALISTIC PEECHI DAM WALLED BUS STAND & VIEWPOINT COMPOUND (X = 3412 .. 3454, Z = 72 .. 178)
        //    Includes Laterite-Granite Perimeter Walls, Separate South ENTRY Gate (Z=92..104),
        //    Separate North EXIT Gate (Z=146..158), Guard Booth, Raised Boom Barriers, 4 Covered Bus Bays,
        //    Kerala Heritage DTPC Tourist Pavilion & Dam Viewpoint Stone Balustrade Promenade!
        buildPeechiDamWalledBusCompound(out)

        // D. MONUMENTAL PEECHI DAM COURSED-GRANITE GRAVITY WALL, 4 RADIAL SLUICE GATES,
        //    OVERHEAD HOIST BRIDGE, COLONIAL LAMP POSTS & 3D WESTERN GHATS RESERVOIR MOUNTAINS!
        val damWestX = PEECHI_DAM_WALL_WEST_X
        val damEastX = PEECHI_DAM_WALL_EAST_X
        val damH = 24.5f

        // South & North Flanking Masonry Dam Wing Walls with Heavy Stone Buttress Ribs every 22m
        addSlopedDamWallSection(out, damWestX, damEastX, z0 = -120f, z1 = 65f, heightM = damH)
        addSlopedDamWallSection(out, damWestX, damEastX, z0 = 205f, z1 = 365f, heightM = damH)
        for (bz in floatArrayOf(-98f, -72f, -46f, -20f, 6f, 32f, 54f, 224f, 250f, 276f, 302f, 328f, 352f)) {
            addAxisBox(out, damWestX - 7.6f, 0f, bz - 1.35f, damWestX - 1.0f, damH * 0.88f, bz + 1.35f, MatDamMasonryLight, MatDamMasonry, MatDamMasonryDark)
        }

        // Central Spillway Section (Z = 65..205): 5 Massive Concrete Spillway Piers + 4 Ribbed Radial Sluice Gates!
        val gateBayCount = 4
        val spillZ0 = 65.0f
        val spillZ1 = 205.0f
        val baySpanZ = (spillZ1 - spillZ0) / gateBayCount
        for (p in 0..gateBayCount) {
            val pz = spillZ0 + p * baySpanZ
            // Massive curved spillway buttress pier
            addAxisBox(out, damWestX - 4.2f, 0f, pz - 2.4f, damEastX + 1.2f, damH + 1.8f, pz + 2.4f, MatDamMasonryLight, MatDamMasonry, MatDamMasonryDark)
            // Upper Kerala-style hoist gantry motor house on top of each spillway pier
            addAxisBox(out, damWestX - 1.8f, damH + 1.8f, pz - 1.8f, damWestX + 5.2f, damH + 5.6f, pz + 1.8f, MatNhConcreteWhite, MatNhConcreteLight, Color(0xFF334155))
            // Terracotta sloped cap on each pier hoist cabin
            addAxisBox(out, damWestX - 2.2f, damH + 5.6f, pz - 2.1f, damWestX + 5.6f, damH + 6.3f, pz + 2.1f, Color(0xFF9A3412), Color(0xFF7C2D12), Color(0xFF7C2D12))
        }
        // Overhead Steel Hoist Bridge connecting all 5 spillway motor houses (Y = 29.0 .. 30.2m)
        addAxisBox(out, damWestX + 0.5f, damH + 4.4f, spillZ0, damWestX + 3.2f, damH + 5.1f, spillZ1, MatGalvanizedSteel, MatGalvanizedDark, MatGalvanizedSteel)

        // Overhead Dam Crest Roadway Bridge & White Masonry Balustrades spanning the entire dam (-120 .. 365)
        addAxisBox(out, damWestX - 1.4f, damH - 1.6f, -120f, damEastX + 0.8f, damH, 365f, MatNhConcreteLight, MatDamMasonryLight, MatDamMasonry)
        addAxisBox(out, damWestX - 1.5f, damH, -120f, damWestX - 0.7f, damH + 1.3f, 365f, MatNhConcreteWhite, MatNhConcreteWhite, MatNhConcreteLight)
        addAxisBox(out, damEastX + 0.1f, damH, -120f, damEastX + 0.8f, damH + 1.3f, 365f, MatNhConcreteWhite, MatNhConcreteWhite, MatNhConcreteLight)

        // Iconic Colonial Twin-Globe Streetlamp Poles lining the entire Peechi Dam Crest Promenade every 28m
        var lampZ = -100.0f
        while (lampZ <= 350.0f) {
            addVerticalBeam(out, damWestX - 0.9f, lampZ, damH + 1.3f, damH + 5.4f, 0.12f, MatGalvanizedDark)
            addAxisBox(out, damWestX - 1.6f, damH + 5.2f, lampZ - 0.15f, damWestX - 0.2f, damH + 5.7f, lampZ + 0.15f, MatLedDaylight5000K, MatLedDaylight5000K, Color(0xFFFEF08A))
            lampZ += 28.0f
        }

        // 4 Ribbed Steel Radial Tainter Sluice Gates + Multi-Layer Foaming Cascade Aprons!
        for (g in 0 until gateBayCount) {
            val gz0 = spillZ0 + g * baySpanZ + 2.4f
            val gz1 = spillZ0 + (g + 1) * baySpanZ - 2.4f
            // Heavy steel radial tainter gate leaf (Y = 14.2 .. 22.6m)
            addAxisBox(out, damWestX + 1.4f, 14.2f, gz0, damWestX + 5.4f, 22.6f, gz1, MatSluiceGateSteel, MatSluiceGateSteel, Color(0xFF1E293B))
            // Horizontal steel structural stiffening ribs across each sluice gate
            for (ribY in floatArrayOf(15.8f, 18.2f, 20.6f)) {
                addAxisBox(out, damWestX + 1.0f, ribY, gz0, damWestX + 1.5f, ribY + 0.35f, gz1, MatGalvanizedDark, MatGalvanizedSteel, MatSluiceGateSteel)
            }
            // Concrete ogee spillway masonry chute underneath the animated GPU cascading water sheet!
            val isActiveFlow = g == 1 || g == 2
            out.add(
                QuadFace3D(
                    Vec3(damWestX - 5.5f, 0.12f, gz0),
                    Vec3(damWestX - 5.5f, 0.12f, gz1),
                    Vec3(damWestX + 2.4f, 14.35f, gz1),
                    Vec3(damWestX + 2.4f, 14.35f, gz0),
                    if (isActiveFlow) Color(0xFF3E4B56) else MatDamMasonry,
                    null, 0f
                )
            )
        }

        // E. Multi-Peak Sculpted Western Ghats Forested Mountain Amphitheatre Framing Peechi Reservoir!
        //    Wraps around East shore (X = 3925), South shore (Z = -150) & North shore (Z = 405)
        val eastHills = arrayOf(
            Triple(3915f, -165f, 48f),
            Triple(3930f, -65f, 64f),
            Triple(3922f, 35f, 74f),
            Triple(3935f, 135f, 68f),
            Triple(3920f, 245f, 72f),
            Triple(3928f, 345f, 60f),
            Triple(3915f, 430f, 46f)
        )
        for (k in 0 until eastHills.size - 1) {
            val (hx0, hz0, hh0) = eastHills[k]
            val (hx1, hz1, hh1) = eastHills[k + 1]
            // Lower rocky forested shore slope rising out of the reservoir (Y = 18.8 .. midH)
            out.add(
                QuadFace3D(
                    Vec3(hx0 - 75f, 18.8f, hz0),
                    Vec3(hx1 - 75f, 18.8f, hz1),
                    Vec3(hx1 - 15f, hh1 * 0.65f, hz1),
                    Vec3(hx0 - 15f, hh0 * 0.65f, hz0),
                    if (k % 2 == 0) MatGhatsForestMid else MatGhatsForestDark, null, 0f
                )
            )
            // Upper misty Western Ghats summit crags
            out.add(
                QuadFace3D(
                    Vec3(hx0 - 15f, hh0 * 0.65f, hz0),
                    Vec3(hx1 - 15f, hh1 * 0.65f, hz1),
                    Vec3(hx1 + 48f, hh1, hz1),
                    Vec3(hx0 + 48f, hh0, hz0),
                    if (k % 2 == 0) MatGhatsForestDark else MatGraniteRockDark, null, 0f
                )
            )
        }
        // South & North forested hill flanks anchoring the masonry dam abutments into the mountains!
        for ((zShore, zPeak) in arrayOf(Pair(-120f, -215f), Pair(365f, 455f))) {
            val segs = 5
            for (s in 0 until segs) {
                val x0 = 3440f + s * 98f
                val x1 = x0 + 98f
                val h0 = 34f + (s % 2) * 14f + sin(s * 1.1f) * 8f
                val h1 = 34f + ((s + 1) % 2) * 14f + sin((s + 1) * 1.1f) * 8f
                out.add(
                    QuadFace3D(
                        Vec3(x0, 18.5f, zShore),
                        Vec3(x1, 18.5f, zShore),
                        Vec3(x1, h1, zPeak),
                        Vec3(x0, h0, zPeak),
                        if (s % 2 == 0) MatGhatsForestDark else MatGhatsForestMid, null, 0f
                    )
                )
            }
        }
    }

    /**
     * Builds the Complete Realistic Peechi Dam Bus Stand & Viewpoint Walled Compound:
     * - Stone-Laterite & Ivory Perimeter Compound Wall with Iron Grille Bays & Globe Pillar Lamps
     * - Separate South ENTRY Gate Portal (X=3412, Z=92..104) with Archway, Guard Booth & Raised Boom Barrier
     * - Separate North EXIT Gate Portal (X=3412, Z=146..158) with Archway & Raised Boom Barrier
     * - Kerala Heritage DTPC Tourist Reception & Bus Waiting Pavilion (X=3443.5..3452.5, Z=106..144)
     * - Elevated Dam-Viewpoint Stone Balustrade Promenade overlooking the 4-Gate Spillway
     */
    private fun buildPeechiDamWalledBusCompound(out: MutableList<QuadFace3D>) {
        val westX = PEECHI_COMPOUND_WEST_X      // 3412.0
        val eastX = PEECHI_COMPOUND_EAST_X      // 3454.0
        val southZ = PEECHI_COMPOUND_SOUTH_Z    // 72.0
        val northZ = PEECHI_COMPOUND_NORTH_Z    // 178.0

        val matLaterite = Color(0xFF9A3412)
        val matIvoryWall = Color(0xFFF3EFE6)
        val matWallCap = Color(0xFF57534E)
        val matTerracotta = Color(0xFFB45309)
        val matRoofDark = Color(0xFF7C2D12)
        val matGrilleIron = Color(0xFF1E293B)

        fun addCompoundWallSegmentZ(x: Float, z0: Float, z1: Float) {
            // Stone-laterite base plinth (Y = 0 .. 1.05m)
            addAxisBox(out, x - 0.34f, 0f, z0, x + 0.34f, 1.05f, z1, matLaterite, matLaterite, matWallCap)
            // Ivory plastered upper wall (Y = 1.05 .. 2.35m)
            addAxisBox(out, x - 0.26f, 1.05f, z0, x + 0.26f, 2.35f, z1, matIvoryWall, matIvoryWall, MatNhConcreteLight)
            // Ornamental dark iron top grille bar (Y = 2.35 .. 2.85m)
            addAxisBox(out, x - 0.08f, 2.68f, z0, x + 0.08f, 2.82f, z1, matGrilleIron, matGrilleIron, matGrilleIron)
            // Stone coping cap
            addAxisBox(out, x - 0.38f, 2.32f, z0, x + 0.38f, 2.46f, z1, matWallCap, matWallCap, matWallCap)
            // Decorative wall pilasters & globe lamps every 10m
            var pz = z0 + 5.0f
            while (pz < z1 - 2.0f) {
                addAxisBox(out, x - 0.44f, 0f, pz - 0.44f, x + 0.44f, 2.95f, pz + 0.44f, matIvoryWall, matIvoryWall, matLaterite)
                addAxisBox(out, x - 0.22f, 2.95f, pz - 0.22f, x + 0.22f, 3.35f, pz + 0.22f, Color(0xFFFEF08A), MatLedDaylight5000K, Color(0xFFFEF08A))
                pz += 10.0f
            }
        }

        fun addCompoundWallSegmentX(z: Float, x0: Float, x1: Float) {
            addAxisBox(out, x0, 0f, z - 0.34f, x1, 1.05f, z + 0.34f, matLaterite, matWallCap, matLaterite)
            addAxisBox(out, x0, 1.05f, z - 0.26f, x1, 2.35f, z + 0.26f, matIvoryWall, MatNhConcreteLight, matIvoryWall)
            addAxisBox(out, x0, 2.32f, z - 0.38f, x1, 2.46f, z + 0.38f, matWallCap, matWallCap, matWallCap)
            var px = x0 + 8.0f
            while (px < x1 - 2.0f) {
                addAxisBox(out, px - 0.44f, 0f, z - 0.44f, px + 0.44f, 2.85f, z + 0.44f, matIvoryWall, matLaterite, matIvoryWall)
                px += 10.0f
            }
        }

        // 1. Perimeter Compound Walls (West Wall strictly split cleanly around ENTRY Z=92..104 & EXIT Z=146..158!)
        addCompoundWallSegmentZ(westX, southZ, 91.5f)
        addCompoundWallSegmentZ(westX, 104.5f, 145.5f)
        addCompoundWallSegmentZ(westX, 158.5f, northZ)
        addCompoundWallSegmentX(southZ, westX, eastX)
        addCompoundWallSegmentX(northZ, westX, eastX)

        // 2. East Dam-Viewpoint Stone Balustrade Wall (low 1.25m panoramic wall with twin-globe colonial lamps)
        addAxisBox(out, eastX - 0.36f, 0.05f, southZ, eastX + 0.36f, 1.28f, northZ, matIvoryWall, matLaterite, matIvoryWall)
        addAxisBox(out, eastX - 0.44f, 1.28f, southZ, eastX + 0.44f, 1.44f, northZ, matWallCap, matWallCap, matWallCap)
        var vpZ = southZ + 6.0f
        while (vpZ <= northZ - 4.0f) {
            addVerticalBeam(out, eastX, vpZ, 1.44f, 4.4f, 0.12f, matGrilleIron)
            addAxisBox(out, eastX - 0.38f, 4.35f, vpZ - 0.16f, eastX + 0.38f, 4.78f, vpZ + 0.16f, MatLedDaylight5000K, Color(0xFFFEF08A), MatLedDaylight5000K)
            vpZ += 16.0f
        }

        // 3. Center Entrance Welcome Monument Plinth outside West Center Wall (X=3408.5..3411.5, Z=119..131, +10m clear of SH 51!)
        addAxisBox(out, 3408.6f, 0.05f, 119.5f, 3411.6f, 1.15f, 130.5f, MatLawnIsland, matLaterite, matLaterite)
        addAxisBox(out, 3409.5f, 1.15f, 121.0f, 3411.4f, 3.65f, 129.0f, matIvoryWall, matIvoryWall, MatNhConcreteLight)
        val monumentSign = createBannerSignSpec(
            cacheKey = "ZONE2_PEECHI_DAM_MONUMENT_V1",
            bgColorInt = 0xFF0369A1.toInt(),
            borderColorInt = 0xFFFACC15.toInt(),
            titleText = "പീച്ചി ഡാം • PEECHI DAM",
            subText = "ESTD. 1957 • ENTRY RIGHT ↘  |  EXIT LEFT ↖",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 768,
            bmpHeight = 192
        )
        out.add(
            QuadFace3D(
                Vec3(3409.42f, 1.55f, 128.6f),
                Vec3(3409.42f, 1.55f, 121.4f),
                Vec3(3409.42f, 3.45f, 121.4f),
                Vec3(3409.42f, 3.45f, 128.6f),
                MatReservoirWaterDeep, null, -0.22f, signboard = monumentSign
            )
        )

        // 4. GRAND SOUTH ENTRY GATE PORTAL (X = 3412.0, Z = 91.5 .. 104.5, clear 12.0m opening, Y = 5.80m clearance)
        buildPeechiGatePortal(
            out = out,
            gateX = westX,
            gateCenterZ = PEECHI_ENTRY_GATE_Z,
            isEntry = true,
            matLaterite = matLaterite,
            matIvoryWall = matIvoryWall,
            matTerracotta = matTerracotta,
            matRoofDark = matRoofDark
        )

        // 5. GRAND NORTH EXIT GATE PORTAL (X = 3412.0, Z = 145.5 .. 158.5, clear 12.0m opening, Y = 5.80m clearance)
        buildPeechiGatePortal(
            out = out,
            gateX = westX,
            gateCenterZ = PEECHI_EXIT_GATE_Z,
            isEntry = false,
            matLaterite = matLaterite,
            matIvoryWall = matIvoryWall,
            matTerracotta = matTerracotta,
            matRoofDark = matRoofDark
        )

        // 6. Security & Irrigation Ticket Check Booth tucked cleanly inside center wall beside ENTRY Gate
        //    (X = 3412.6 .. 3416.2, Z = 105.6 .. 110.2 — strictly clear of Entry lane Z=92..104!)
        addAxisBox(out, 3412.6f, 0.06f, 105.6f, 3416.2f, 3.25f, 110.2f, matIvoryWall, matIvoryWall, MatNhConcreteLight)
        addAxisBox(out, 3412.55f, 1.35f, 106.4f, 3416.25f, 2.55f, 109.4f, Color(0xFF0F172A), Color(0xFF1E3A5F), Color(0xFF1E3A5F))
        addAxisBox(out, 3412.1f, 3.25f, 105.1f, 3416.7f, 4.05f, 110.7f, matTerracotta, matRoofDark, matRoofDark)

        // 7. Kerala Heritage DTPC Tourist Reception & KSRTC Bus Waiting Pavilion (X = 3444.2 .. 3452.2, Z = 106 .. 144)
        //    Sits cleanly on East promenade leaving full 31.5m wide bus turning courtyard (X = 3412 .. 3443.5)!
        // Raised granite platform
        addAxisBox(out, 3443.8f, 0.06f, 105.5f, 3452.6f, 0.55f, 144.5f, MatNhConcreteLight, matLaterite, matLaterite)
        // Main ticket & tourist reception hall core
        addAxisBox(out, 3446.6f, 0.55f, 111.0f, 3452.0f, 4.45f, 139.0f, matIvoryWall, matIvoryWall, MatNhConcreteLight)
        // Panoramic tinted glass ticket & waiting windows facing the bus bays
        addAxisBox(out, 3446.50f, 1.45f, 113.0f, 3446.68f, 3.55f, 137.0f, Color(0xFF1E3A5F), Color(0xFF1E3A5F), Color(0xFF0F172A))
        // Front veranda granite columns along X = 3444.5 supporting cantilevered Kerala Mangalore-tiled canopy
        for (cz in floatArrayOf(107.0f, 114.5f, 122.0f, 128.0f, 135.5f, 143.0f)) {
            addAxisBox(out, 3444.15f, 0.55f, cz - 0.30f, 3444.85f, 4.65f, cz + 0.30f, matIvoryWall, matLaterite, matIvoryWall)
        }
        // Double-tier Sloped Kerala Terracotta Mangalore-Tile Roof over Tourist Pavilion & Bus Shelter
        addAxisBox(out, 3442.8f, 4.55f, 104.5f, 3453.2f, 5.25f, 145.5f, matTerracotta, matRoofDark, matRoofDark)
        addAxisBox(out, 3444.6f, 5.25f, 107.5f, 3451.8f, 6.25f, 142.5f, matTerracotta, matTerracotta, matRoofDark)
        addAxisBox(out, 3446.8f, 6.25f, 111.0f, 3449.8f, 7.05f, 139.0f, matRoofDark, matTerracotta, matRoofDark)

        // High-Res Bilingual Pavilion Fascia Signboard facing the Bus Courtyard!
        val pavilionSignSpec = createBannerSignSpec(
            cacheKey = "ZONE2_PEECHI_DAM_PAVILION_V2",
            bgColorInt = 0xFF0369A1.toInt(),
            borderColorInt = 0xFFFACC15.toInt(),
            titleText = "പീച്ചി ഡാം ടൂറിസ്റ്റ് ബസ് സ്റ്റാൻഡ് • PEECHI DAM BUS STAND",
            subText = "KERALA IRRIGATION DEPT & DTPC • BAYS 1–4 • DAM VIEWPOINT & GARDENS",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFE0F2FE.toInt(),
            bmpWidth = 1024,
            bmpHeight = 192
        )
        out.add(
            QuadFace3D(
                Vec3(3442.72f, 4.20f, 141.0f),
                Vec3(3442.72f, 4.20f, 109.0f),
                Vec3(3442.72f, 5.65f, 109.0f),
                Vec3(3442.72f, 5.65f, 141.0f),
                MatReservoirWaterDeep, null, -0.25f, signboard = pavilionSignSpec
            )
        )
    }

    private fun buildPeechiGatePortal(
        out: MutableList<QuadFace3D>,
        gateX: Float,
        gateCenterZ: Float,
        isEntry: Boolean,
        matLaterite: Color,
        matIvoryWall: Color,
        matTerracotta: Color,
        matRoofDark: Color
    ) {
        val halfClear = 6.0f // 12.0m clear drivable width!
        val zSouthPillar = gateCenterZ - halfClear - 0.65f
        val zNorthPillar = gateCenterZ + halfClear + 0.65f

        // Twin Monumental Kerala Granite & Ivory Gate Towers
        for (pz in floatArrayOf(zSouthPillar, zNorthPillar)) {
            addAxisBox(out, gateX - 0.95f, 0f, pz - 0.95f, gateX + 0.95f, 1.35f, pz + 0.95f, matLaterite, matLaterite, MatDamMasonryDark)
            addAxisBox(out, gateX - 0.78f, 1.35f, pz - 0.78f, gateX + 0.78f, 6.20f, pz + 0.78f, matIvoryWall, matIvoryWall, MatNhConcreteLight)
            // Decorative terracotta tower finial roof
            addAxisBox(out, gateX - 1.10f, 6.20f, pz - 1.10f, gateX + 1.10f, 6.95f, pz + 1.10f, matTerracotta, matRoofDark, matRoofDark)
            addAxisBox(out, gateX - 0.55f, 6.95f, pz - 0.55f, gateX + 0.55f, 7.55f, pz + 0.55f, matRoofDark, matTerracotta, matTerracotta)
            // Warm glowing gate lamp
            addAxisBox(out, gateX - 0.24f, 7.55f, pz - 0.24f, gateX + 0.24f, 8.05f, pz + 0.24f, Color(0xFFFEF08A), MatLedDaylight5000K, Color(0xFFFEF08A))
        }

        // Overhead Portal Arch Lintel Beam (Y = 5.80 .. 7.60m -> 5.80m clear height for tall double-deckers!)
        addAxisBox(out, gateX - 0.85f, 5.80f, zSouthPillar, gateX + 0.85f, 7.55f, zNorthPillar, matIvoryWall, matIvoryWall, MatNhConcreteLight)
        // Traditional Kerala Sloped Terracotta Archway Canopy Roof
        addAxisBox(out, gateX - 1.35f, 7.55f, zSouthPillar - 0.5f, gateX + 1.35f, 8.35f, zNorthPillar + 0.5f, matTerracotta, matRoofDark, matRoofDark)

        // Bilingual ENTRY / EXIT Archway Signboards (both West-facing & East-facing!)
        val signSpec = createBannerSignSpec(
            cacheKey = if (isEntry) "ZONE2_PEECHI_ENTRY_GATE_V1" else "ZONE2_PEECHI_EXIT_GATE_V1",
            bgColorInt = if (isEntry) 0xFF065F46.toInt() else 0xFF9A3412.toInt(),
            borderColorInt = 0xFFFACC15.toInt(),
            titleText = if (isEntry) "പീച്ചി ഡാം പ്രവേശനം • ENTRY" else "പീച്ചി ഡാം പുറത്തേക്ക് • EXIT",
            subText = if (isEntry) "PEECHI DAM BUS STAND & VIEWPOINT • WELCOME" else "TO SH 51 • MANNUTHY • NH 544 • THRISSUR",
            titleColorInt = 0xFFFFFFFF.toInt(),
            subColorInt = 0xFFFEF08A.toInt(),
            bmpWidth = 896,
            bmpHeight = 160
        )
        val zS = gateCenterZ - 5.6f
        val zN = gateCenterZ + 5.6f
        // West-facing (approaching from SH 51)
        out.add(
            QuadFace3D(
                Vec3(gateX - 0.90f, 5.90f, zN),
                Vec3(gateX - 0.90f, 5.90f, zS),
                Vec3(gateX - 0.90f, 7.45f, zS),
                Vec3(gateX - 0.90f, 7.45f, zN),
                MatNhaiSignGreen, null, -0.25f, signboard = signSpec
            )
        )
        // East-facing (from inside Peechi Dam courtyard)
        out.add(
            QuadFace3D(
                Vec3(gateX + 0.90f, 5.90f, zS),
                Vec3(gateX + 0.90f, 5.90f, zN),
                Vec3(gateX + 0.90f, 7.45f, zN),
                Vec3(gateX + 0.90f, 7.45f, zS),
                MatNhaiSignGreen, null, -0.25f, signboard = signSpec
            )
        )

        // Realistic Toll/Security Boom Barrier Pedestal & Raised Red-White Striped Arm (100% open & clear!)
        val barZ = if (isEntry) zNorthPillar - 0.95f else zSouthPillar + 0.95f
        val barDirZ = if (isEntry) -1f else 1f
        // Yellow-and-charcoal motor pedestal strictly tucked against gate pillar
        addAxisBox(out, gateX + 0.95f, 0.06f, barZ - 0.35f, gateX + 1.65f, 1.35f, barZ + 0.35f, MatChevronYellow, MatChevronYellow, MatChevronBlack)
        // Green "GATE OPEN" LED indicator on pedestal
        addAxisBox(out, gateX + 0.90f, 1.05f, barZ - 0.16f, gateX + 1.02f, 1.25f, barZ + 0.16f, MatEmergencyGreenBright, MatEmergencyGreenBright, MatEmergencyGreenBright)
        // Raised boom barrier arm (angled steeply upward at ~78° so tall buses pass unobstructed!)
        for (seg in 0..4) {
            val y0 = 1.25f + seg * 0.86f
            val y1 = y0 + 0.86f
            val zb0 = barZ + barDirZ * (seg * 0.16f)
            val zb1 = barZ + barDirZ * ((seg + 1) * 0.16f)
            val col = if (seg % 2 == 0) MatHydrantRed else MatNhConcreteWhite
            addHorizontalBeam(out, gateX + 1.28f, zb0, gateX + 1.28f, zb1, y0, y1, 0.08f, col)
        }
    }

    private fun addSlopedDamWallSection(
        out: MutableList<QuadFace3D>,
        westX: Float,
        eastX: Float,
        z0: Float,
        z1: Float,
        heightM: Float
    ) {
        // Stepped/battered coursed-granite downstream gravity dam wall (2 tiers for rich masonry relief)
        val midY = heightM * 0.48f
        out.add(
            QuadFace3D(
                Vec3(westX - 7.2f, 0f, z0),
                Vec3(westX - 7.2f, 0f, z1),
                Vec3(westX - 3.4f, midY, z1),
                Vec3(westX - 3.4f, midY, z0),
                MatDamMasonryDark, null, 0f
            )
        )
        out.add(
            QuadFace3D(
                Vec3(westX - 3.4f, midY, z0),
                Vec3(westX - 3.4f, midY, z1),
                Vec3(westX, heightM, z1),
                Vec3(westX, heightM, z0),
                MatDamMasonry, null, 0f
            )
        )
        // Upstream reservoir-facing wall
        out.add(
            QuadFace3D(
                Vec3(eastX, 0f, z1),
                Vec3(eastX, 0f, z0),
                Vec3(eastX, heightM, z0),
                Vec3(eastX, heightM, z1),
                MatDamMasonryDark, null, 0f
            )
        )
        // Crest top deck
        out.add(
            QuadFace3D(
                Vec3(westX, heightM, z0),
                Vec3(eastX, heightM, z0),
                Vec3(eastX, heightM, z1),
                Vec3(westX, heightM, z1),
                MatDamMasonryLight, null, 0f
            )
        )
    }

    // =========================================================================
    // REUSABLE 3D GEOMETRY PRIMITIVES & HIGH-RES CANVAS SIGNBOARD TEXTURES
    // =========================================================================
    private fun addAxisBox(
        out: MutableList<QuadFace3D>,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        topCol: Color, sideXCol: Color, sideZCol: Color
    ) {
        // Top (+Y)
        out.add(QuadFace3D(Vec3(minX, maxY, minZ), Vec3(maxX, maxY, minZ), Vec3(maxX, maxY, maxZ), Vec3(minX, maxY, maxZ), topCol, null, 0f))
        // Bottom (-Y) if elevated
        if (minY >= 2.5f) {
            out.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(maxX, minY, maxZ), Vec3(maxX, minY, minZ), Vec3(minX, minY, minZ), sideXCol, null, 0f))
        }
        // West (-X) & East (+X)
        out.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(minX, minY, minZ), Vec3(minX, maxY, minZ), Vec3(minX, maxY, maxZ), sideXCol, null, 0f))
        out.add(QuadFace3D(Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), sideXCol, null, 0f))
        // South (-Z) & North (+Z)
        out.add(QuadFace3D(Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, maxY, minZ), Vec3(minX, maxY, minZ), sideZCol, null, 0f))
        out.add(QuadFace3D(Vec3(maxX, minY, maxZ), Vec3(minX, minY, maxZ), Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), sideZCol, null, 0f))
    }

    private fun addVerticalBeam(
        out: MutableList<QuadFace3D>,
        cx: Float, cz: Float,
        y0: Float, y1: Float,
        halfW: Float,
        col: Color
    ) {
        addAxisBox(out, cx - halfW, y0, cz - halfW, cx + halfW, y1, cz + halfW, col, col, col)
    }

    private fun addTaperedPole(
        out: MutableList<QuadFace3D>,
        cx: Float, cz: Float,
        y0: Float, y1: Float,
        r0: Float, r1: Float,
        col: Color
    ) {
        val sides = 6
        for (k in 0 until sides) {
            val a0 = (k.toFloat() / sides) * (2f * PI.toFloat())
            val a1 = ((k + 1).toFloat() / sides) * (2f * PI.toFloat())
            out.add(
                QuadFace3D(
                    Vec3(cx + cos(a0) * r0, y0, cz + sin(a0) * r0),
                    Vec3(cx + cos(a1) * r0, y0, cz + sin(a1) * r0),
                    Vec3(cx + cos(a1) * r1, y1, cz + sin(a1) * r1),
                    Vec3(cx + cos(a0) * r1, y1, cz + sin(a0) * r1),
                    if (k % 2 == 0) col else MatGalvanizedDark, null, 0f
                )
            )
        }
    }

    private fun addHorizontalBeam(
        out: MutableList<QuadFace3D>,
        x0: Float, z0: Float,
        x1: Float, z1: Float,
        y0: Float, y1: Float,
        halfThick: Float,
        col: Color
    ) {
        val dx = x1 - x0
        val dz = z1 - z0
        val len = hypot(dx, dz).coerceAtLeast(0.01f)
        val nx = -dz / len * halfThick
        val nz = dx / len * halfThick
        out.add(QuadFace3D(Vec3(x0 + nx, y0, z0 + nz), Vec3(x1 + nx, y0, z1 + nz), Vec3(x1 + nx, y1, z1 + nz), Vec3(x0 + nx, y1, z0 + nz), col, null, 0f))
        out.add(QuadFace3D(Vec3(x1 - nx, y0, z1 - nz), Vec3(x0 - nx, y0, z0 - nz), Vec3(x0 - nx, y1, z0 - nz), Vec3(x1 - nx, y1, z1 - nz), col, null, 0f))
        out.add(QuadFace3D(Vec3(x0 - nx, y1, z0 - nz), Vec3(x1 - nx, y1, z1 - nz), Vec3(x1 + nx, y1, z1 + nz), Vec3(x0 + nx, y1, z0 + nz), col, null, 0f))
        out.add(QuadFace3D(Vec3(x0 + nx, y0, z0 + nz), Vec3(x1 + nx, y0, z1 + nz), Vec3(x1 - nx, y0, z1 - nz), Vec3(x0 - nx, y0, z0 - nz), col, null, 0f))
    }

    private fun addGhatsTeakTree(
        out: MutableList<QuadFace3D>,
        tx: Float, tz: Float,
        heightM: Float = 10.5f,
        crownR: Float = 3.8f,
        baseY: Float = 0.05f
    ) {
        val trunkH = heightM * 0.44f
        addVerticalBeam(out, tx, tz, baseY, baseY + trunkH, 0.34f, MatTeakTrunk)
        // Two-tier layered tropical teak canopy (lightweight 8 quads total)
        val c1Bot = baseY + trunkH * 0.85f
        val c1Top = baseY + heightM * 0.78f
        addAxisBox(out, tx - crownR, c1Bot, tz - crownR, tx + crownR, c1Top, tz + crownR, MatGhatsForestLight, MatGhatsForestMid, MatGhatsForestDark)
        val r2 = crownR * 0.65f
        addAxisBox(out, tx - r2, c1Top, tz - r2, tx + r2, baseY + heightM, tz + r2, MatGhatsForestLight, MatGhatsForestLight, MatGhatsForestMid)
    }

    private fun addBambooClump(out: MutableList<QuadFace3D>, bx: Float, bz: Float) {
        for (k in 0..2) {
            val ox = (k - 1) * 0.65f
            val oz = if (k == 1) 0.55f else -0.35f
            addVerticalBeam(out, bx + ox, bz + oz, 0.05f, 7.5f + k * 0.8f, 0.14f, MatBambooStalk)
        }
        addAxisBox(out, bx - 1.9f, 5.2f, bz - 1.9f, bx + 1.9f, 8.8f, bz + 1.9f, MatGhatsForestLight, MatBambooStalk, MatGhatsForestMid)
    }

    // =========================================================================
    // CUSTOM HIGH-RES ZONE 2 SIGNBOARD TEXTURES (NHAI #057A55, KUTHIRAN PORTAL, VMS, EXIT)
    // =========================================================================
    private fun createZone2NhaiGantrySpec(
        cacheKey: String,
        header: String,
        col1Mal: String, col1Eng: String, col1Dist: String,
        col2Mal: String, col2Eng: String, col2Dist: String,
        col3Mal: String, col3Eng: String, col3Dist: String
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 1024,
        bmpHeight = 256,
        isDoubleSided = false,
        backplateColor = Color(0xFF64748B),
        backplateBorderColor = Color(0xFF334155)
    ) {
        val bmp = Bitmap.createBitmap(1024, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = 1024f; val h = 256f

        canvas.drawColor(0xFF057A55.toInt())
        val hdrBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF034E37.toInt() }
        canvas.drawRect(10f, 10f, w - 10f, 62f, hdrBg)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }
        canvas.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 14f, 14f, border)
        border.strokeWidth = 4f
        canvas.drawLine(10f, 62f, w - 10f, 62f, border)

        val colW = (w - 20f) / 3f
        canvas.drawLine(10f + colW, 62f, 10f + colW, h - 10f, border)
        canvas.drawLine(10f + colW * 2f, 62f, 10f + colW * 2f, h - 10f, border)

        val hdrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFEF08A.toInt()
            typeface = PremiumFonts.displayTypefaceFor(header, bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 27f
        }
        canvas.drawText(header, w * 0.5f, 45f, hdrPaint)

        val cols = arrayOf(
            Triple(col1Mal, col1Eng, col1Dist),
            Triple(col2Mal, col2Eng, col2Dist),
            Triple(col3Mal, col3Eng, col3Dist)
        )
        for (i in 0..2) {
            val cxCol = 10f + (i + 0.5f) * colW
            val (mal, eng, dist) = cols[i]
            val malP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFEF08A.toInt()
                typeface = PremiumFonts.displayTypefaceFor(mal, bold = true)
                textAlign = Paint.Align.CENTER
                textSize = 28f
            }
            val engP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                typeface = PremiumFonts.displayTypefaceFor(eng, bold = true)
                textAlign = Paint.Align.CENTER
                textSize = 27f
            }
            val distP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                typeface = PremiumFonts.monoTypeface(bold = true)
                textAlign = Paint.Align.CENTER
                textSize = 31f
            }
            drawFitCentered(canvas, mal, cxCol, 110f, colW - 24f, malP)
            drawFitCentered(canvas, eng, cxCol, 162f, colW - 24f, engP)
            drawFitCentered(canvas, dist, cxCol, 220f, colW - 24f, distP)
        }
        bmp
    }

    private fun createKuthiranPortalHeaderSpec(
        cacheKey: String,
        portalSubtitle: String
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 1024,
        bmpHeight = 256,
        isDoubleSided = false
    ) {
        val bmp = Bitmap.createBitmap(1024, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = 1024f; val h = 256f

        canvas.drawColor(0xFF057A55.toInt())
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 9f
        }
        canvas.drawRoundRect(RectF(10f, 10f, w - 10f, h - 10f), 16f, 16f, border)

        // Left & Right Embossed NHAI Crest & Ashoka Emblem Roundels
        val crestFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEF08A.toInt() }
        val crestInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF024E3B.toInt() }
        val crestTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFEF08A.toInt()
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 28f
        }
        for ((emX, label) in arrayOf(Pair(95f, "NHAI"), Pair(w - 95f, "NH 544"))) {
            canvas.drawCircle(emX, h * 0.48f, 64f, crestFill)
            canvas.drawCircle(emX, h * 0.48f, 54f, crestInner)
            canvas.drawText(label, emX, h * 0.48f + 10f, crestTxt)
        }

        val malPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFEF08A.toInt()
            typeface = PremiumFonts.displayTypefaceFor("കുതിരാൻ തുരങ്കം", bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 52f
        }
        val engPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = PremiumFonts.displayTypefaceFor("KUTHIRAN TUNNEL", bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 48f
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE2E8F0.toInt()
            typeface = PremiumFonts.monoTypeface(bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 24f
        }
        drawFitCentered(canvas, "കുതിരാൻ തുരങ്കം • NH 544", w * 0.5f, 82f, 650f, malPaint)
        drawFitCentered(canvas, "KUTHIRAN TUNNEL (960 M)", w * 0.5f, 152f, 660f, engPaint)
        drawFitCentered(canvas, portalSubtitle, w * 0.5f, 214f, 680f, subPaint)
        bmp
    }

    private fun createKuthiranVmsSignSpec(cacheKey: String): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 768,
        bmpHeight = 160,
        isDoubleSided = false,
        backplateColor = Color(0xFF1E293B),
        backplateBorderColor = Color(0xFF475569)
    ) {
        val bmp = Bitmap.createBitmap(768, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF0A0E17.toInt())

        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF334155.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawRect(6f, 6f, 762f, 154f, frame)

        val amberLed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFBBF24.toInt()
            typeface = PremiumFonts.monoTypeface(bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 42f
        }
        val greenLed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4ADE80.toInt()
            typeface = PremiumFonts.monoTypeface(bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 36f
        }
        drawFitCentered(canvas, "SPEED LIMIT 60 KM/H • HEADLIGHTS ON", 384f, 68f, 710f, amberLed)
        drawFitCentered(canvas, "▼ LANE 1 OPEN   ▼ LANE 2 OPEN   ▼ LANE 3 OPEN", 384f, 128f, 710f, greenLed)
        bmp
    }

    private fun createEmergencyExitSignSpec(cacheKey: String): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 384,
        bmpHeight = 128,
        isDoubleSided = false
    ) {
        val bmp = Bitmap.createBitmap(384, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF15803D.toInt())
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }
        canvas.drawRoundRect(RectF(6f, 6f, 378f, 122f), 10f, 10f, border)
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = PremiumFonts.displayTypefaceFor("EMERGENCY EXIT", bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 34f
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFEF08A.toInt()
            typeface = PremiumFonts.monoTypeface(bold = true)
            textAlign = Paint.Align.CENTER
            textSize = 22f
        }
        canvas.drawText("EMERGENCY EXIT →", 192f, 58f, txt)
        canvas.drawText("CROSS-PASSAGE TO ADJACENT TUBE", 192f, 100f, sub)
        bmp
    }

    private fun drawFitCentered(
        canvas: Canvas,
        text: String,
        cx: Float,
        y: Float,
        maxW: Float,
        paint: Paint
    ) {
        val baseSz = paint.textSize
        val m = paint.measureText(text)
        if (m > maxW && m > 1f) {
            paint.textSize = (baseSz * (maxW / m)).coerceAtLeast(12f)
        }
        canvas.drawText(text, cx, y, paint)
        paint.textSize = baseSz
    }
}
