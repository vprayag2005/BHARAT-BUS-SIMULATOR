package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.ui.theme.PremiumFonts
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// =========================================================================
// SHORANUR JUNCTION RAILWAY STATION (SRR) — DISTINCT HERITAGE 3D ARCHITECTURE
// Foot-print: X = 16.0 .. 74.0m (58m E-W), Z = 3165.0 .. 3310.0m (145m N-S)
// Positioned West of SH 22 (X = 100.0m) and directly East of the 2-track
// Southern Railway double line (Track 1 @ X = 6.0m, Track 2 @ X = 11.2m).
//
// COMPLETELY DIFFERENT DESIGN & COLOR PALETTE FROM THRISSUR RAILWAY STATION:
// - Palakkad Division Heritage Terracotta-Ochre Masonry + Sandstone Colonnade
// - Deep Royal Maroon Pitched Mangalore-Tile Roofs & Triple-Dome Clock Tower
// - Brass-Gold Kalasham Spire & Illuminated 4-Face Heritage Clock
// - Covered West Platform 1 (X = 14.2 .. 25.5m) with Mustard-Yellow SRR Boards
// - 4 Forecourt Bus Bays + Wide 12m South ENTRY (Z=3198) & North EXIT (Z=3272)
// =========================================================================

internal const val SHORANUR_STATION_BAY_X = 56.5f
internal val SHORANUR_STATION_BAY_CENTERS_Z = floatArrayOf(3208.0f, 3224.0f, 3240.0f, 3256.0f)

private val SrrTerracottaMain = Color(0xFFC47A53)
private val SrrTerracottaDark = Color(0xFF9F5B39)
private val SrrSandstoneCream = Color(0xFFF0E4D2)
private val SrrSandstoneShade = Color(0xFFD9C5A9)
private val SrrMaroonRoof = Color(0xFF6E1E28)
private val SrrMaroonDeep = Color(0xFF4F131B)
private val SrrMaroonFascia = Color(0xFF5A1822)
private val SrrBrassGold = Color(0xFFD8A438)
private val SrrBrassDark = Color(0xFFA7781E)
private val SrrTealIron = Color(0xFF1E555C)
private val SrrTealIronDark = Color(0xFF143C42)
private val SrrGlassDark = Color(0xFF233240)
private val SrrGlassMullion = Color(0xFFE7D9C4)
private val SrrPlatformGrey = Color(0xFFBDB7AE)
private val SrrPlatformEdgeYellow = Color(0xFFFACC15)
private val SrrYardConcrete = Color(0xFF8C877E)
private val SrrYardLaneDark = Color(0xFF5F5B55)
private val SrrKerbYellow = Color(0xFFF59E0B)
private val SrrKerbBlack = Color(0xFF1E242B)
private val SrrBarrierRed = Color(0xFFDC2626)
private val SrrBarrierWhite = Color(0xFFF8FAFC)
private val SrrMustardBoard = Color(0xFFF5C428)
private val SrrShelterSilver = Color(0xFFC8CDD4)

/**
 * Ground pass for Shoranur Junction Railway Station (SRR):
 * - Concrete forecourt yard (X = 16.0 .. 74.0, Z = 3165.0 .. 3310.0)
 * - Circulation asphalt drive loop connecting South ENTRY (Z=3198) -> Bays -> North EXIT (Z=3272)
 * - Bus bay concrete pads, tactile yellow platform edge, and painted directional arrows
 */
internal fun DrawScope.drawShoranurStationYard(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (rig.recorder == null && hypot(45.0f - camEye.x, 3238.0f - camEye.z) > 620f) return
    val yYard = 0.064f
    val yLane = 0.067f
    val yMark = 0.070f

    fun drawFlatQuad(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, col: Color) {
        if (rig.buildClippedQuadPath(
                path,
                Vec3(x0, y, z0),
                Vec3(x1, y, z0),
                Vec3(x1, y, z1),
                Vec3(x0, y, z1)
            )
        ) {
            drawPath(path, col)
        }
    }

    // 1. Full Station Forecourt Concrete Apron
    drawFlatQuad(16.0f, 3165.0f, 74.0f, 3310.0f, yYard, SrrYardConcrete)

    // 2. Darker Asphalt Circulation Driveway inside Station Compound
    drawFlatQuad(48.0f, 3190.0f, 74.0f, 3284.0f, yLane, SrrYardLaneDark)
    // Entry & Exit concrete threshold aprons bridging X=74.0 wall cleanly to SH 22 access spurs
    drawFlatQuad(72.5f, 3192.2f, 76.5f, 3203.8f, yLane, SrrYardLaneDark)
    drawFlatQuad(72.5f, 3266.2f, 76.5f, 3277.8f, yLane, SrrYardLaneDark)

    // 3. Pedestrian Heritage Sandstone Concourse Forecourt Apron along East facade
    drawFlatQuad(38.0f, 3172.0f, 48.0f, 3304.0f, yLane, SrrSandstoneShade)

    // 4. 4 Forecourt Bus Bay Stalls with crisp white borders & yellow kerbs
    for (bz in SHORANUR_STATION_BAY_CENTERS_Z) {
        drawFlatQuad(51.8f, bz - 6.2f, 61.2f, bz + 6.2f, yMark, Color(0xFF474A50))
        // White stall guide stripes
        drawFlatQuad(51.6f, bz - 6.35f, 61.4f, bz - 6.05f, yMark + 0.002f, Color(0xFFF1F5F9))
        drawFlatQuad(51.6f, bz + 6.05f, 61.4f, bz + 6.35f, yMark + 0.002f, Color(0xFFF1F5F9))
        // Curb bump-stop on West side
        drawFlatQuad(51.1f, bz - 5.2f, 51.8f, bz + 5.2f, yMark + 0.002f, SrrKerbYellow)
    }

    // 5. Centerline lane divider & zebra pedestrian crossing from bus bays to station porch
    drawFlatQuad(64.8f, 3198.0f, 65.15f, 3272.0f, yMark, SrrKerbYellow)
    var zx = 48.6f
    while (zx <= 51.2f) {
        drawFlatQuad(zx, 3234.5f, zx + 0.45f, 3241.5f, yMark + 0.002f, Color(0xFFF8FAFC))
        zx += 0.90f
    }

    // 6. Gravel Ballast Bed under the 2 railway tracks right outside Platform 1 (X = 2.0 .. 14.5m)
    drawFlatQuad(2.0f, 3145.0f, 14.6f, 3330.0f, 0.056f, Color(0xFF5D574F))
}

private val cachedShoranurStationFaces: List<QuadFace3D> by lazy {
    buildShoranurStationStaticFaces()
}

private fun buildShoranurStationStaticFaces(): List<QuadFace3D> {
    val faces = ArrayList<QuadFace3D>(1400)

    fun addBox(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        topCol: Color,
        sideColX: Color = topCol,
        sideColZ: Color = sideColX,
        bottomCol: Color? = null,
        bias: Float = 0f
    ) {
        faces.add(QuadFace3D(Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x0, y1, z1), topCol, null, bias, 0f, 0f, 1f, 0f))
        if (bottomCol != null) {
            faces.add(QuadFace3D(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x0, y0, z0), bottomCol, null, bias, 0f, 0f, -1f, 0f))
        }
        faces.add(QuadFace3D(Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x0, y1, z0), sideColZ, null, bias, 0f, 0f, 0f, -1f))
        faces.add(QuadFace3D(Vec3(x1, y0, z1), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x1, y1, z1), sideColZ, null, bias, 0f, 0f, 0f, 1f))
        faces.add(QuadFace3D(Vec3(x0, y0, z1), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x0, y1, z1), sideColX, null, bias, 0f, -1f, 0f, 0f))
        faces.add(QuadFace3D(Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), sideColX, null, bias, 0f, 1f, 0f, 0f))
    }

    fun addPitchedRoofAlongZ(
        x0: Float, x1: Float,
        z0: Float, z1: Float,
        eaveY: Float, ridgeY: Float,
        roofCol: Color, gableCol: Color
    ) {
        val midX = (x0 + x1) * 0.5f
        // West slope
        faces.add(QuadFace3D(Vec3(x0, eaveY, z0), Vec3(midX, ridgeY, z0), Vec3(midX, ridgeY, z1), Vec3(x0, eaveY, z1), roofCol, null, 0f, 0f, -0.55f, 0.83f, 0f))
        // East slope
        faces.add(QuadFace3D(Vec3(midX, ridgeY, z0), Vec3(x1, eaveY, z0), Vec3(x1, eaveY, z1), Vec3(midX, ridgeY, z1), SrrMaroonDeep, null, 0f, 0f, 0.55f, 0.83f, 0f))
        // South gable triangle (degenerate quad)
        faces.add(QuadFace3D(Vec3(x0, eaveY, z0), Vec3(x1, eaveY, z0), Vec3(midX, ridgeY, z0), Vec3(midX, ridgeY, z0), gableCol, null, 0f, 0f, 0f, 0f, -1f))
        // North gable triangle
        faces.add(QuadFace3D(Vec3(x1, eaveY, z1), Vec3(x0, eaveY, z1), Vec3(midX, ridgeY, z1), Vec3(midX, ridgeY, z1), gableCol, null, 0f, 0f, 0f, 0f, 1f))
        // Ridge beam trim
        addBox(midX - 0.18f, ridgeY - 0.05f, z0 - 0.15f, midX + 0.18f, ridgeY + 0.22f, z1 + 0.15f, SrrBrassGold, SrrBrassDark, SrrBrassGold)
    }

    fun addPyramidRoof(
        x0: Float, x1: Float,
        z0: Float, z1: Float,
        baseY: Float, apexY: Float,
        colA: Color, colB: Color
    ) {
        val mx = (x0 + x1) * 0.5f
        val mz = (z0 + z1) * 0.5f
        val apex = Vec3(mx, apexY, mz)
        faces.add(QuadFace3D(Vec3(x0, baseY, z0), Vec3(x1, baseY, z0), apex, apex, colA, null, 0f, 0f, 0f, 0.6f, -0.8f))
        faces.add(QuadFace3D(Vec3(x1, baseY, z1), Vec3(x0, baseY, z1), apex, apex, colA, null, 0f, 0f, 0f, 0.6f, 0.8f))
        faces.add(QuadFace3D(Vec3(x0, baseY, z1), Vec3(x0, baseY, z0), apex, apex, colB, null, 0f, 0f, -0.8f, 0.6f, 0f))
        faces.add(QuadFace3D(Vec3(x1, baseY, z0), Vec3(x1, baseY, z1), apex, apex, colB, null, 0f, 0f, 0.8f, 0.6f, 0f))
    }

    fun addWallSegment(x0: Float, z0: Float, x1: Float, z1: Float) {
        val dx = x1 - x0
        val dz = z1 - z0
        val len = hypot(dx, dz).coerceAtLeast(0.01f)
        val halfT = 0.22f
        val nx = -dz / len * halfT
        val nz = dx / len * halfT
        val isHorizontal = kotlin.math.abs(dx) >= kotlin.math.abs(dz)
        if (isHorizontal) {
            val minX = minOf(x0, x1)
            val maxX = maxOf(x0, x1)
            val zMid = (z0 + z1) * 0.5f
            addBox(minX, 0.06f, zMid - halfT, maxX, 0.85f, zMid + halfT, SrrTerracottaDark, SrrTerracottaDark, SrrTerracottaDark)
            addBox(minX, 0.85f, zMid - halfT, maxX, 2.35f, zMid + halfT, SrrSandstoneCream, SrrSandstoneShade, SrrSandstoneCream)
            addBox(minX, 2.35f, zMid - halfT - 0.06f, maxX, 2.55f, zMid + halfT + 0.06f, SrrMaroonRoof, SrrMaroonDeep, SrrMaroonRoof)
        } else {
            val minZ = minOf(z0, z1)
            val maxZ = maxOf(z0, z1)
            val xMid = (x0 + x1) * 0.5f
            addBox(xMid - halfT, 0.06f, minZ, xMid + halfT, 0.85f, maxZ, SrrTerracottaDark, SrrTerracottaDark, SrrTerracottaDark)
            addBox(xMid - halfT, 0.85f, minZ, xMid + halfT, 2.35f, maxZ, SrrSandstoneCream, SrrSandstoneCream, SrrSandstoneShade)
            addBox(xMid - halfT - 0.06f, 2.35f, minZ, xMid + halfT + 0.06f, 2.55f, maxZ, SrrMaroonRoof, SrrMaroonRoof, SrrMaroonDeep)
        }
        // Decorative terracotta heritage piers every ~12m
        val pierCount = (len / 12.0f).toInt().coerceAtLeast(1)
        for (p in 0..pierCount) {
            val f = p.toFloat() / pierCount.toFloat()
            val px = x0 + dx * f
            val pz = z0 + dz * f
            addBox(px - 0.32f, 0.06f, pz - 0.32f, px + 0.32f, 2.82f, pz + 0.32f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
            addBox(px - 0.38f, 2.82f, pz - 0.38f, px + 0.38f, 3.00f, pz + 0.38f, SrrMaroonRoof, SrrMaroonDeep, SrrMaroonRoof)
        }
        // Silence unused nx/nz warning if needed
        if (nx + nz == Float.NaN) faces.clear()
    }

    // =========================================================================
    // 1. COVERED WEST RAILWAY PLATFORM 1 (X = 14.2 .. 25.5m, Z = 3168 .. 3306m)
    //    Right alongside Track 2 (X = 11.2m) & Track 1 (X = 6.0m)!
    // =========================================================================
    addBox(14.2f, 0.05f, 3168.0f, 25.5f, 1.08f, 3306.0f, SrrPlatformGrey, SrrTerracottaDark, SrrTerracottaDark)
    // Yellow safety tactile strip along Track 2 platform edge
    addBox(14.22f, 1.08f, 3168.0f, 14.72f, 1.12f, 3306.0f, SrrPlatformEdgeYellow)
    // Platform 1 Pitched Shelter Canopy supported on cast-iron teal heritage pillars
    addPitchedRoofAlongZ(15.0f, 25.0f, 3172.0f, 3302.0f, eaveY = 5.35f, ridgeY = 6.75f, roofCol = SrrShelterSilver, gableCol = SrrMaroonFascia)
    var pfZ = 3176.0f
    while (pfZ <= 3298.0f) {
        addBox(17.5f, 1.08f, pfZ - 0.16f, 17.86f, 5.45f, pfZ + 0.16f, SrrTealIron, SrrTealIronDark, SrrTealIron)
        addBox(23.1f, 1.08f, pfZ - 0.16f, 23.46f, 5.45f, pfZ + 0.16f, SrrTealIron, SrrTealIronDark, SrrTealIron)
        // Heritage cross truss beam
        addBox(17.4f, 5.15f, pfZ - 0.10f, 23.5f, 5.42f, pfZ + 0.10f, SrrTealIronDark)
        // Passenger benches every second bay
        if (((pfZ - 3176f) / 14f).toInt() % 2 == 0) {
            addBox(19.6f, 1.08f, pfZ - 1.3f, 20.6f, 1.58f, pfZ + 1.3f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
        }
        pfZ += 14.0f
    }

    // Iconic Indian Railways Mustard-Yellow Station Name Boards on Platform 1 (South & North ends)
    val platformBoardSpec = createShoranurStationBoardSpec(
        cacheKey = "SRR_PLATFORM_MUSTARD_BOARD_V1",
        lineTop = "SHORANUR JUNCTION",
        lineBottom = "ഷൊർണ്ണൂർ ജംഗ്ഷൻ • SRR"
    )
    for (boardZ in floatArrayOf(3184.0f, 3290.0f)) {
        // Concrete pedestals + yellow signboard visible from both trains and station
        addBox(16.2f, 1.08f, boardZ - 3.6f, 16.55f, 3.05f, boardZ - 3.25f, SrrSandstoneCream)
        addBox(16.2f, 1.08f, boardZ + 3.25f, 16.55f, 3.05f, boardZ + 3.6f, SrrSandstoneCream)
        addBox(16.15f, 1.90f, boardZ - 3.7f, 16.60f, 3.30f, boardZ + 3.7f, SrrMustardBoard, SrrKerbBlack, SrrMustardBoard)
        faces.add(
            QuadFace3D(
                Vec3(16.12f, 1.95f, boardZ + 3.6f),
                Vec3(16.12f, 1.95f, boardZ - 3.6f),
                Vec3(16.12f, 3.25f, boardZ - 3.6f),
                Vec3(16.12f, 3.25f, boardZ + 3.6f),
                baseColor = SrrMustardBoard,
                biasDist = -0.6f,
                signboard = platformBoardSpec
            )
        )
    }

    // =========================================================================
    // 2. MAIN HERITAGE STATION BUILDING — PALAKKAD TERRACOTTA + ROYAL MAROON
    //    Central Clock Tower Pavilion (Z = 3222 .. 3254) + Flanking Wings
    // =========================================================================
    // Raised sandstone plinth under entire terminal building
    addBox(25.2f, 0.06f, 3172.0f, 44.5f, 0.72f, 3304.0f, SrrSandstoneShade, SrrTerracottaDark, SrrTerracottaDark)

    // South Wing (Z = 3174 .. 3222, X = 25.5 .. 39.2)
    addBox(25.5f, 0.72f, 3174.0f, 39.2f, 2.30f, 3222.0f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
    addBox(25.5f, 2.30f, 3174.0f, 39.2f, 8.20f, 3222.0f, SrrSandstoneCream, SrrSandstoneShade, SrrSandstoneCream)
    addBox(25.3f, 7.80f, 3173.7f, 39.5f, 8.50f, 3222.3f, SrrMaroonFascia, SrrMaroonDeep, SrrMaroonFascia)
    addPitchedRoofAlongZ(24.6f, 40.2f, 3173.2f, 3222.8f, eaveY = 8.45f, ridgeY = 12.10f, roofCol = SrrMaroonRoof, gableCol = SrrTerracottaMain)

    // North Wing (Z = 3254 .. 3302, X = 25.5 .. 39.2)
    addBox(25.5f, 0.72f, 3254.0f, 39.2f, 2.30f, 3302.0f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
    addBox(25.5f, 2.30f, 3254.0f, 39.2f, 8.20f, 3302.0f, SrrSandstoneCream, SrrSandstoneShade, SrrSandstoneCream)
    addBox(25.3f, 7.80f, 3253.7f, 39.5f, 8.50f, 3302.3f, SrrMaroonFascia, SrrMaroonDeep, SrrMaroonFascia)
    addPitchedRoofAlongZ(24.6f, 40.2f, 3253.2f, 3302.8f, eaveY = 8.45f, ridgeY = 12.10f, roofCol = SrrMaroonRoof, gableCol = SrrTerracottaMain)

    // Grand Central Heritage Concourse Pavilion (Z = 3222 .. 3254, X = 25.0 .. 41.2)
    addBox(25.0f, 0.72f, 3222.0f, 41.2f, 2.60f, 3254.0f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
    addBox(25.0f, 2.60f, 3222.0f, 41.2f, 10.60f, 3254.0f, SrrSandstoneCream, SrrSandstoneShade, SrrSandstoneCream)
    addBox(24.7f, 10.20f, 3221.6f, 41.6f, 11.10f, 3254.4f, SrrMaroonFascia, SrrMaroonDeep, SrrMaroonFascia)
    addPitchedRoofAlongZ(24.2f, 42.2f, 3221.2f, 3254.8f, eaveY = 11.05f, ridgeY = 14.30f, roofCol = SrrMaroonRoof, gableCol = SrrTerracottaMain)

    // Triple-Dome Heritage Clock Tower rising over Center Pavilion (Z = 3232 .. 3244, X = 29.0 .. 40.0)
    addBox(29.0f, 11.00f, 3232.0f, 40.0f, 16.40f, 3244.0f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
    addBox(28.6f, 16.00f, 3231.6f, 40.4f, 16.75f, 3244.4f, SrrSandstoneCream, SrrSandstoneShade, SrrSandstoneCream)
    addPyramidRoof(28.2f, 40.8f, 3231.2f, 3244.8f, baseY = 16.75f, apexY = 21.40f, colA = SrrMaroonRoof, colB = SrrMaroonDeep)
    // Brass-Gold Kalasham Finial Spire atop Clock Tower
    addBox(34.15f, 21.20f, 3237.65f, 34.85f, 22.90f, 3238.35f, SrrBrassGold, SrrBrassDark, SrrBrassGold)
    addPyramidRoof(33.90f, 35.10f, 3237.40f, 3238.60f, baseY = 22.90f, apexY = 24.40f, colA = SrrBrassGold, colB = SrrBrassDark)

    // Flanking Twin Mini Heritage Cupolas (South & North shoulders of Central Pavilion)
    for (cupZ in floatArrayOf(3225.2f, 3250.8f)) {
        addBox(33.0f, 11.0f, cupZ - 2.3f, 38.6f, 13.8f, cupZ + 2.3f, SrrSandstoneCream, SrrTerracottaMain, SrrSandstoneShade)
        addPyramidRoof(32.5f, 39.1f, cupZ - 2.7f, cupZ + 2.7f, baseY = 13.8f, apexY = 16.6f, colA = SrrMaroonRoof, colB = SrrMaroonDeep)
        addBox(35.55f, 16.5f, cupZ - 0.25f, 36.05f, 17.6f, cupZ + 0.25f, SrrBrassGold)
    }

    // Illuminated Clock Face on East side of Clock Tower (facing SH 22)
    val clockSpec = createHeritageClockFaceSpec("SRR_HERITAGE_CLOCK_V1")
    faces.add(
        QuadFace3D(
            Vec3(40.15f, 12.1f, 3235.6f),
            Vec3(40.15f, 12.1f, 3240.4f),
            Vec3(40.15f, 15.8f, 3240.4f),
            Vec3(40.15f, 15.8f, 3235.6f),
            baseColor = SrrSandstoneCream,
            biasDist = -0.6f,
            signboard = clockSpec
        )
    )

    // Arched Sandstone Veranda Colonnade & Heritage Windows along East Facade (X = 39.2 .. 44.5)
    var bayZ = 3178.0f
    while (bayZ <= 3298.0f) {
        val isCentral = bayZ in 3223f..3253f
        val wallX = if (isCentral) 41.25f else 39.25f
        // Recessed tall arched dark glass windows + sandstone surround
        addBox(wallX, 2.50f, bayZ - 1.55f, wallX + 0.14f, 6.20f, bayZ + 1.55f, SrrGlassDark)
        addBox(wallX + 0.06f, 4.25f, bayZ - 1.55f, wallX + 0.18f, 4.42f, bayZ + 1.55f, SrrGlassMullion)
        addBox(wallX + 0.06f, 2.50f, bayZ - 0.09f, wallX + 0.18f, 6.20f, bayZ + 0.09f, SrrGlassMullion)
        // Veranda sandstone column at X = 44.1m
        addBox(43.75f, 0.72f, bayZ - 0.34f, 44.45f, 5.45f, bayZ + 0.34f, SrrSandstoneCream, SrrSandstoneShade, SrrSandstoneCream)
        addBox(43.65f, 0.72f, bayZ - 0.44f, 44.55f, 1.35f, bayZ + 0.44f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
        addBox(43.65f, 5.10f, bayZ - 0.44f, 44.55f, 5.50f, bayZ + 0.44f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
        bayZ += 8.0f
    }

    // Continuous Veranda Eave Cornice & Maroon Fascia along East Colonnade
    addBox(39.0f, 5.40f, 3173.5f, 45.2f, 6.05f, 3302.5f, SrrMaroonFascia, SrrMaroonDeep, SrrMaroonFascia, SrrSandstoneShade)

    // =========================================================================
    // 3. CANTILEVERED BUS PORTICO CANOPY & BAY NUMBER MARKINGS (X = 44.8 .. 56.2)
    // =========================================================================
    // Sloped Maroon Heritage Portico Roof shielding Buses & Passengers at Bays 1..4
    faces.add(
        QuadFace3D(
            Vec3(45.0f, 6.02f, 3199.0f),
            Vec3(56.2f, 5.38f, 3199.0f),
            Vec3(56.2f, 5.38f, 3277.0f),
            Vec3(45.0f, 6.02f, 3277.0f),
            SrrMaroonRoof, null, 0f, 0f, 0.08f, 0.99f, 0f
        )
    )
    // Portico East Terracotta & Gold Trim Fascia
    addBox(55.85f, 5.05f, 3198.8f, 56.35f, 5.58f, 3277.2f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
    addBox(56.20f, 5.55f, 3198.8f, 56.45f, 5.75f, 3277.2f, SrrBrassGold, SrrBrassDark, SrrBrassGold)
    // Cast-iron Teal Cantilever Support Columns & Trusses (stay clear of bus bay lane at X = 56.5!)
    var pZ = 3202.0f
    while (pZ <= 3274.0f) {
        addBox(49.2f, 0.06f, pZ - 0.22f, 49.68f, 5.72f, pZ + 0.22f, SrrTealIron, SrrTealIronDark, SrrTealIron)
        addBox(44.8f, 5.25f, pZ - 0.12f, 56.1f, 5.52f, pZ + 0.12f, SrrTealIronDark)
        pZ += 12.0f
    }

    // Tall Raised 3D Rooftop Signboard: "SHORANUR JUNCTION" facing East toward SH 22
    val stationRoofTitleSpec = createShoranurRoof3DTextSpec(
        cacheKey = "SHORANUR_STATION_ROOF_3D_TITLE_V1",
        text = "SHORANUR JUNCTION",
        bmpWidth = 1024,
        bmpHeight = 200,
        faceColorInt = 0xFFFFF8E7.toInt(),
        extrudeColorInt = 0xFF4F131B.toInt(),
        textSizePx = 112f
    )
    addBox(55.60f, 5.75f, 3212.0f, 56.30f, 6.20f, 3264.0f, SrrMaroonDeep, SrrMaroonFascia, SrrMaroonDeep)
    faces.add(
        QuadFace3D(
            Vec3(56.10f, 6.20f, 3213.0f),
            Vec3(56.10f, 6.20f, 3263.0f),
            Vec3(56.10f, 9.80f, 3263.0f),
            Vec3(56.10f, 9.80f, 3213.0f),
            baseColor = SrrMaroonDeep,
            biasDist = -1.8f,
            signboard = stationRoofTitleSpec
        )
    )

    // Crisp White Floor Bay Numbers "1".."4" painted on the 4 bus bays
    for (i in SHORANUR_STATION_BAY_CENTERS_Z.indices) {
        val bz = SHORANUR_STATION_BAY_CENTERS_Z[i]
        val numSpec = createShoranurStationBayNumSpec("SRR_BAY_NUM_${i + 1}", "${i + 1}")
        faces.add(
            QuadFace3D(
                Vec3(SHORANUR_STATION_BAY_X - 1.25f, 0.075f, bz - 1.25f),
                Vec3(SHORANUR_STATION_BAY_X + 1.25f, 0.075f, bz - 1.25f),
                Vec3(SHORANUR_STATION_BAY_X + 1.25f, 0.075f, bz + 1.25f),
                Vec3(SHORANUR_STATION_BAY_X - 1.25f, 0.075f, bz + 1.25f),
                baseColor = Color.White,
                biasDist = -0.8f,
                signboard = numSpec
            )
        )
    }

    // =========================================================================
    // 4. PERIMETER HERITAGE COMPOUND WALL (X = 16.0 .. 74.0, Z = 3165.0 .. 3310.0)
    //    East Wall along X = 74.0 has TWO wide 12m Portals facing SH 22:
    //    - South ENTRY Portal: Z = 3192.0 .. 3204.0m (center 3198.0m)
    //    - North EXIT Portal : Z = 3266.0 .. 3278.0m (center 3272.0m)
    // =========================================================================
    addWallSegment(16.0f, 3165.0f, 74.0f, 3165.0f) // South wall
    addWallSegment(16.0f, 3310.0f, 74.0f, 3310.0f) // North wall
    // East wall segments leaving South ENTRY (3192..3204) & North EXIT (3266..3278) cleanly open
    addWallSegment(74.0f, 3165.0f, 74.0f, 3191.5f)
    addWallSegment(74.0f, 3204.5f, 74.0f, 3265.5f)
    addWallSegment(74.0f, 3278.5f, 74.0f, 3310.0f)

    // =========================================================================
    // 5. TWIN HERITAGE EAST GATE PORTALS AT X = 74.0 — "ENTRY" & "EXIT"
    // =========================================================================
    val gateX = 74.0f

    fun addStationPortal(centerZ: Float, label: String, cacheKey: String, isEntry: Boolean) {
        val zMin = centerZ - 6.0f
        val zMax = centerZ + 6.0f
        // Twin sandstone & terracotta columns
        addBox(gateX - 0.55f, 0.06f, zMin - 0.95f, gateX + 0.55f, 6.25f, zMin - 0.05f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
        addBox(gateX - 0.55f, 0.06f, zMax + 0.05f, gateX + 0.55f, 6.25f, zMax + 0.95f, SrrTerracottaMain, SrrTerracottaDark, SrrTerracottaMain)
        // Heritage maroon lintel beam + pitched coping
        addBox(gateX - 0.65f, 5.55f, zMin - 1.15f, gateX + 0.65f, 6.45f, zMax + 1.15f, SrrMaroonFascia, SrrMaroonDeep, SrrMaroonFascia, SrrSandstoneShade)
        addBox(gateX - 0.72f, 6.45f, zMin - 1.25f, gateX + 0.72f, 6.68f, zMax + 1.25f, SrrBrassGold, SrrBrassDark, SrrBrassGold)

        val textSpec = createShoranurRoof3DTextSpec(
            cacheKey = cacheKey,
            text = label,
            bmpWidth = 512,
            bmpHeight = 160,
            faceColorInt = 0xFFFFF8E7.toInt(),
            extrudeColorInt = 0xFF4F131B.toInt(),
            textSizePx = 120f
        )
        faces.add(
            QuadFace3D(
                Vec3(gateX, 6.68f, centerZ - 3.6f),
                Vec3(gateX, 6.68f, centerZ + 3.6f),
                Vec3(gateX, 8.60f, centerZ + 3.6f),
                Vec3(gateX, 8.60f, centerZ - 3.6f),
                baseColor = SrrMaroonDeep,
                biasDist = -1.6f,
                signboard = textSpec
            )
        )

        // Boom barrier pedestal & rest post
        val pedX = if (isEntry) gateX + 0.68f else gateX - 0.68f
        addBox(pedX - 0.18f, 0.06f, zMin + 0.15f, pedX + 0.18f, 1.05f, zMin + 0.45f, SrrKerbYellow, SrrKerbBlack, SrrKerbYellow)
        addBox(pedX - 0.16f, 0.06f, zMax - 0.45f, pedX + 0.16f, 0.92f, zMax - 0.15f, SrrKerbBlack, SrrKerbYellow, SrrKerbBlack)
    }

    addStationPortal(3198.0f, "ENTRY", "SRR_PORTAL_ENTRY_3D_V1", isEntry = true)
    addStationPortal(3272.0f, "EXIT", "SRR_PORTAL_EXIT_3D_V1", isEntry = false)

    // Guard / Security Booth between Entry & Exit gates (X = 74.2 .. 77.4, Z = 3232.0 .. 3238.0)
    addBox(74.2f, 0.06f, 3232.0f, 77.4f, 2.85f, 3238.0f, SrrSandstoneCream, SrrTerracottaMain, SrrSandstoneShade)
    addBox(74.0f, 2.85f, 3231.7f, 77.6f, 3.20f, 3238.3f, SrrMaroonRoof, SrrMaroonDeep, SrrMaroonRoof)
    addBox(77.35f, 1.15f, 3233.0f, 77.48f, 2.35f, 3237.0f, SrrGlassDark)

    return faces
}

private fun appendSrrBoomArmAlongZ(
    hingeX: Float,
    hingeY: Float,
    hingeZ: Float,
    zDir: Float,
    armLength: Float,
    openFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    val angleRad = openFrac.coerceIn(0f, 1f) * (82.0 * PI / 180.0).toFloat()
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)

    fun pt(along: Float, upOff: Float, xOff: Float): Vec3 {
        val dz = (along * cosA - upOff * sinA) * zDir
        val dy = along * sinA + upOff * cosA
        return Vec3(hingeX + xOff, hingeY + dy, hingeZ + dz)
    }

    fun addSeg(a0: Float, a1: Float, halfR: Float, sideCol: Color, topCol: Color) {
        val c000 = pt(a0, -halfR, -halfR)
        val c100 = pt(a1, -halfR, -halfR)
        val c110 = pt(a1, halfR, -halfR)
        val c010 = pt(a0, halfR, -halfR)
        val c001 = pt(a0, -halfR, halfR)
        val c101 = pt(a1, -halfR, halfR)
        val c111 = pt(a1, halfR, halfR)
        val c011 = pt(a0, halfR, halfR)
        outFaces.add(QuadFace3D(c010, c110, c111, c011, topCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c000, c100, c101, c001, sideCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c001, c101, c111, c011, topCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c100, c000, c010, c110, sideCol, biasDist = -0.35f))
    }

    addSeg(-0.65f, 0f, 0.075f, SrrTealIron, SrrKerbBlack)
    val n = 14
    val segLen = armLength / n
    for (i in 0 until n) {
        val isRed = (i % 2 == 0)
        addSeg(
            i * segLen,
            (i + 1) * segLen,
            0.055f,
            if (isRed) SrrBarrierRed else SrrBarrierWhite,
            if (isRed) Color(0xFF991B1B) else Color(0xFFCBD5E1)
        )
    }
}

internal fun collectShoranurStationDynamicBarrierFaces(
    entryOpenFrac: Float,
    exitOpenFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    appendSrrBoomArmAlongZ(74.68f, 0.92f, 3192.30f, 1f, 11.45f, entryOpenFrac, outFaces)
    appendSrrBoomArmAlongZ(73.32f, 0.92f, 3266.30f, 1f, 11.45f, exitOpenFrac, outFaces)
}

internal fun collectAllShoranurStationStaticFaces(outFaces: MutableList<QuadFace3D>) {
    outFaces.addAll(cachedShoranurStationFaces)
}

internal fun collectShoranurStationSceneFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    if (hypot(45.0f - camEye.x, 3238.0f - camEye.z) > 650f) return
    outFaces.addAll(cachedShoranurStationFaces)
}

private fun createShoranurStationBoardSpec(
    cacheKey: String,
    lineTop: String,
    lineBottom: String
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = 768,
    bmpHeight = 192,
    isDoubleSided = true,
    renderBitmap = {
        val bmp = Bitmap.createBitmap(768, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF5C428.toInt())
        val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = 0xFF18181B.toInt()
        }
        c.drawRect(8f, 8f, 760f, 184f, borderP)
        val pTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF18181B.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 58f
            typeface = PremiumFonts.displayTypefaceFor(lineTop, bold = true)
            letterSpacing = 0.05f
        }
        c.drawText(lineTop, 384f, 86f, pTop)
        val pBot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF18181B.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 40f
            typeface = PremiumFonts.displayTypefaceFor(lineBottom, bold = true)
        }
        c.drawText(lineBottom, 384f, 148f, pBot)
        bmp
    }
)

private fun createHeritageClockFaceSpec(cacheKey: String): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = 256,
    bmpHeight = 256,
    isDoubleSided = true,
    renderBitmap = {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.TRANSPARENT)
        val cx = 128f
        val cy = 128f
        val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF8E7.toInt() }
        c.drawCircle(cx, cy, 114f, bgP)
        val rimP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = 0xFF5A1822.toInt()
        }
        c.drawCircle(cx, cy, 110f, rimP)
        val goldP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFFD8A438.toInt()
        }
        c.drawCircle(cx, cy, 98f, goldP)
        val tickP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1E293B.toInt()
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        for (h in 0 until 12) {
            val a = (h * 30.0 * PI / 180.0).toFloat()
            val r0 = if (h % 3 == 0) 72f else 82f
            val r1 = 94f
            c.drawLine(cx + sin(a) * r0, cy - cos(a) * r0, cx + sin(a) * r1, cy - cos(a) * r1, tickP)
        }
        // Classic 10:10 railway station clock hands
        tickP.strokeWidth = 9f
        c.drawLine(cx, cy, cx - 44f, cy - 28f, tickP)
        tickP.strokeWidth = 6f
        c.drawLine(cx, cy, cx + 56f, cy - 42f, tickP)
        val centerP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E1E28.toInt() }
        c.drawCircle(cx, cy, 10f, centerP)
        bmp
    }
)

private fun createShoranurStationBayNumSpec(cacheKey: String, numText: String): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = 160,
    bmpHeight = 160,
    isDoubleSided = true,
    renderBitmap = {
        val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.TRANSPARENT)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 108f
            typeface = PremiumFonts.displayTypefaceFor(numText, bold = true)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = 0x884F131B.toInt()
        c.drawText(numText, 80f, 120f, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFFF8E7.toInt()
        c.drawText(numText, 80f, 120f, p)
        bmp
    }
)
