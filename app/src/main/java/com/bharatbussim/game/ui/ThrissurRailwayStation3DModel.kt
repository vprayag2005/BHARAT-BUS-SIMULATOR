package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.ui.theme.PremiumFonts
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * High-detail OpenGL ES 3.0 3D Architectural Model of Thrissur Railway Station (TCR)
 * at its real-world South-West Thrissur location (X = -124.2 .. -64.0m, Z = -222.0 .. -62.0m),
 * accessed via Station Road & the OSM Station Entry/Exit spurs.
 *
 * Architectural Features:
 * - Unified Sakthan Bus Stand architectural color palette (cream masonry, grey columns,
 *   light fascia, charcoal roofs/canopies, cool-grey asphalt courtyard, warm stone plinth).
 * - East-facing Bus Forecourt with 4 Express Bays, South ENTRY (Z=-168) & North EXIT (Z=-108) gates,
 *   and tall Oswald Bold 3D rooftop lettering ("THRISSUR RAILWAY STATION") above the vaulted roofline & clock dial.
 * - Shoranur-grade open West-side Railway Boarding Platforms 1 & 2 directly alongside the 2-track
 *   Southern Railway mainline (X = -112.0m), complete with yellow tactile edge, pitched shelter canopies,
 *   passenger benches, mustard-yellow "THRISSUR / തൃശ്ശൂർ • TCR" boards, and covered Foot Overbridge (FOB).
 */

private val StationGroundEarth = Color(0xFF6A645B)
private val StationBasementStone = Color(0xFF938E85)
private val StationApronAsphalt = Color(0xFF474D56)
private val StationApronApproach = Color(0xFF393E46)
private val StationJointLine = Color(0x5523272D)
private val StationWhiteMark = Color(0xFFF1F5F9)

// Bus Stand Unified Architectural Palette (matching Sakthan Bus Terminal)
private val StationWallCream = Color(0xFFD6D2CA)
private val StationWallCoping = Color(0xFF9CA3AF)
private val StationColumnGrey = Color(0xFFC2BEB6)
private val StationFasciaLight = Color(0xFFE2E0DB)
private val StationFasciaShadow = Color(0xFFAEB2B8)
private val StationPlatformTile = Color(0xFFCBC6BC)
private val StationPlatformSoffit = Color(0xFF6B7280)
private val StationRoofCharcoal = Color(0xFF363B42)
private val StationRoofDeckDark = Color(0xFF2B3036)
private val StationSteelDark = Color(0xFF282C33)
private val StationGlassTint = Color(0xFF1F3448)
private val StationKerbYellow = Color(0xFFEAB308)
private val StationKerbBlack = Color(0xFF1E2228)
private val StationBarrierRed = Color(0xFFDC2626)
private val StationBarrierWhite = Color(0xFFF8FAFC)

private val StationPlatformEdgeYellow = Color(0xFFFACC15)
private val StationMustardBoard = Color(0xFFF5C428)
private val StationCanopySilver = Color(0xFFC8CDD4)
private val StationBallastGravel = Color(0xFF56514A)
private val StationBenchWood = Color(0xFF8B5A3C)

internal const val STATION_BUS_BAY_X = -78.2f
internal val STATION_BUS_BAY_CENTERS_Z = floatArrayOf(-158.0f, -144.0f, -130.0f, -116.0f)

internal fun DrawScope.drawThrissurModernStationYard(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (rig.recorder == null && hypot(camEye.x - (-92.0f), camEye.z - (-138.0f)) > 460f) return

    val yGroundOuter = 0.050f
    val yBasementPlinth = 0.052f
    val yApron = 0.058f

    // 1. Warm Stone-Earth Surround & Concrete Basement Plinth covering East Bus Forecourt + West Platforms 1 & 2
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-126.0f, yGroundOuter, -226.0f),
            Vec3(-60.2f, yGroundOuter, -226.0f),
            Vec3(-60.2f, yGroundOuter, -58.0f),
            Vec3(-126.0f, yGroundOuter, -58.0f)
        )
    ) {
        drawPath(path, StationGroundEarth)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-107.4f, yBasementPlinth, -222.5f),
            Vec3(-61.8f, yBasementPlinth, -222.5f),
            Vec3(-61.8f, yBasementPlinth, -60.5f),
            Vec3(-107.4f, yBasementPlinth, -60.5f)
        )
    ) {
        drawPath(path, StationBasementStone)
    }

    // Gravel Ballast Bed under the 2 broad-gauge Southern Railway tracks right beside Platform 1 & Platform 2
    // Track 1 (West / Northbound: Thrissur -> Shoranur) @ X = -114.6m, Track 2 (East / Southbound: Shoranur -> Thrissur) @ X = -109.4m
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-117.4f, 0.054f, -240.0f),
            Vec3(-106.8f, 0.054f, -240.0f),
            Vec3(-106.8f, 0.054f, -96.0f),
            Vec3(-117.4f, 0.054f, -96.0f)
        )
    ) {
        drawPath(path, StationBallastGravel)
    }

    // 2. Smooth Grey Asphalt Bus Forecourt & Drop-Off Yard (X = -82.2 .. -64.0, Z = -204.0 .. -62.0)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-82.2f, yApron, -204.0f),
            Vec3(-64.0f, yApron, -204.0f),
            Vec3(-64.0f, yApron, -62.0f),
            Vec3(-82.2f, yApron, -62.0f)
        )
    ) {
        drawPath(path, StationApronAsphalt)
    }

    // 3. Portal Threshold Apron Pads at South ENTRY (-174..-162) & North EXIT (-114..-102) across East Wall X = -64.0m
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-66.5f, 0.0588f, -174.0f),
            Vec3(-62.5f, 0.0588f, -174.0f),
            Vec3(-62.5f, 0.0588f, -162.0f),
            Vec3(-66.5f, 0.0588f, -162.0f)
        )
    ) {
        drawPath(path, StationApronApproach)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-66.5f, 0.0588f, -114.0f),
            Vec3(-62.5f, 0.0588f, -114.0f),
            Vec3(-62.5f, 0.0588f, -102.0f),
            Vec3(-66.5f, 0.0588f, -102.0f)
        )
    ) {
        drawPath(path, StationApronApproach)
    }

    // 4. Concrete Courtyard Expansion Joint Grid in Bus Forecourt
    if (hypot(camEye.x - (-73.0f), camEye.z - (-133.0f)) < 170f) {
        var gx = -81.0f
        while (gx <= -65.0f) {
            rig.projectSegmentClipped(Vec3(gx, 0.0592f, -198.0f), Vec3(gx, 0.0592f, -68.0f))?.let { seg ->
                drawLine(StationJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gx += 5.5f
        }
        var gz = -196.0f
        while (gz <= -72.0f) {
            rig.projectSegmentClipped(Vec3(-81.8f, 0.0592f, gz), Vec3(-64.4f, 0.0592f, gz))?.let { seg ->
                drawLine(StationJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gz += 7.0f
        }
    }

    // 5. Crisp White Bus Circulation Guide Curve: South ENTRY (Station Rd) -> Bays 1..4 -> North EXIT (Station Rd)
    val loopGuidePts = listOf(
        Vec3(-64.0f, 0.060f, -168.0f),
        Vec3(-70.5f, 0.060f, -168.0f),
        Vec3(-75.4f, 0.060f, -165.8f),
        Vec3(-78.2f, 0.060f, -161.0f),
        Vec3(-78.2f, 0.060f, -115.0f),
        Vec3(-75.4f, 0.060f, -110.2f),
        Vec3(-70.5f, 0.060f, -108.0f),
        Vec3(-64.0f, 0.060f, -108.0f)
    )
    for (i in 0 until loopGuidePts.size - 1) {
        rig.projectSegmentClipped(loopGuidePts[i], loopGuidePts[i + 1])?.let { seg ->
            drawLine(StationWhiteMark, seg.first, seg.second, strokeWidth = 1.8f)
        }
    }

    // 6. Bus Bay Painted Stalls & Tire Wear Tracks (Bays 1..4 along X = -78.2m)
    val tireDark = Color(0x441B1F26)
    val bayMinX = STATION_BUS_BAY_X - 1.95f
    val bayMaxX = STATION_BUS_BAY_X + 1.95f
    for (bz in STATION_BUS_BAY_CENTERS_Z) {
        val z0 = bz - 6.1f
        val z1 = bz + 6.1f
        for (tx in floatArrayOf(STATION_BUS_BAY_X - 0.92f, STATION_BUS_BAY_X + 0.92f)) {
            rig.projectSegmentClipped(Vec3(tx, 0.0595f, z0 + 0.8f), Vec3(tx, 0.0595f, z1 - 0.8f))?.let { seg ->
                drawLine(tireDark, seg.first, seg.second, strokeWidth = 2.6f)
            }
        }
        rig.projectSegmentClipped(Vec3(bayMinX, 0.0605f, z0), Vec3(bayMinX, 0.0605f, z1))?.let { seg ->
            drawLine(StationWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(Vec3(bayMaxX, 0.0605f, z0), Vec3(bayMaxX, 0.0605f, z1))?.let { seg ->
            drawLine(StationWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(Vec3(bayMinX, 0.0605f, z1), Vec3(bayMaxX, 0.0605f, z1))?.let { seg ->
            drawLine(StationWhiteMark, seg.first, seg.second, strokeWidth = 1.8f)
        }
        rig.projectSegmentClipped(Vec3(bayMinX, 0.0605f, z0), Vec3(bayMaxX, 0.0605f, z0))?.let { seg ->
            drawLine(StationWhiteMark, seg.first, seg.second, strokeWidth = 1.4f)
        }
    }
}

private val cachedThrissurStationFaces: List<QuadFace3D> by lazy {
    val faces = ArrayList<QuadFace3D>(1800)

    fun addBox(
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        topCol: Color,
        sideCol: Color = topCol,
        frontBackCol: Color = sideCol,
        bottomCol: Color? = null,
        bias: Float = 0f
    ) {
        faces.add(
            QuadFace3D(
                Vec3(minX, maxY, minZ),
                Vec3(minX, maxY, maxZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(maxX, maxY, minZ),
                topCol, null, bias, 0f, 0f, 1f, 0f
            )
        )
        if (bottomCol != null) {
            faces.add(
                QuadFace3D(
                    Vec3(minX, minY, maxZ),
                    Vec3(minX, minY, minZ),
                    Vec3(maxX, minY, minZ),
                    Vec3(maxX, minY, maxZ),
                    bottomCol, null, bias + 0.2f, 0f, 0f, -1f, 0f
                )
            )
        }
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, maxZ),
                Vec3(maxX, minY, maxZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(minX, maxY, maxZ),
                frontBackCol, null, bias, 0f, 0f, 0f, 1f
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(maxX, minY, minZ),
                Vec3(minX, minY, minZ),
                Vec3(minX, maxY, minZ),
                Vec3(maxX, maxY, minZ),
                frontBackCol, null, bias, 0f, 0f, 0f, -1f
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, minZ),
                Vec3(minX, minY, maxZ),
                Vec3(minX, maxY, maxZ),
                Vec3(minX, maxY, minZ),
                sideCol, null, bias, 0f, -1f, 0f, 0f
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(maxX, minY, maxZ),
                Vec3(maxX, minY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(maxX, maxY, maxZ),
                sideCol, null, bias, 0f, 1f, 0f, 0f
            )
        )
    }

    fun addBarrelVaultRoof(
        minX: Float,
        maxX: Float,
        minZ: Float,
        maxZ: Float,
        baseY: Float,
        archRise: Float,
        archAlongX: Boolean,
        topCol: Color,
        trimCol: Color
    ) {
        val segs = 6
        val mid = if (archAlongX) (minX + maxX) * 0.5f else (minZ + maxZ) * 0.5f
        val halfSpan = if (archAlongX) (maxX - minX) * 0.5f else (maxZ - minZ) * 0.5f
        for (i in 0 until segs) {
            val t0 = i.toFloat() / segs.toFloat()
            val t1 = (i + 1).toFloat() / segs.toFloat()
            val u0 = -1f + 2f * t0
            val u1 = -1f + 2f * t1
            val y0 = baseY + archRise * (1f - u0 * u0)
            val y1 = baseY + archRise * (1f - u1 * u1)
            if (archAlongX) {
                val x0 = mid + u0 * halfSpan
                val x1 = mid + u1 * halfSpan
                faces.add(QuadFace3D(Vec3(x0, y0, minZ), Vec3(x0, y0, maxZ), Vec3(x1, y1, maxZ), Vec3(x1, y1, minZ), topCol))
                faces.add(QuadFace3D(Vec3(x1, baseY, maxZ), Vec3(x0, baseY, maxZ), Vec3(x0, y0, maxZ), Vec3(x1, y1, maxZ), trimCol))
                faces.add(QuadFace3D(Vec3(x0, baseY, minZ), Vec3(x1, baseY, minZ), Vec3(x1, y1, minZ), Vec3(x0, y0, minZ), trimCol))
            } else {
                val z0 = mid + u0 * halfSpan
                val z1 = mid + u1 * halfSpan
                faces.add(QuadFace3D(Vec3(minX, y0, z0), Vec3(maxX, y0, z0), Vec3(maxX, y1, z1), Vec3(minX, y1, z1), topCol))
                faces.add(QuadFace3D(Vec3(minX, baseY, z1), Vec3(minX, baseY, z0), Vec3(minX, y0, z0), Vec3(minX, y1, z1), trimCol))
                faces.add(QuadFace3D(Vec3(maxX, baseY, z0), Vec3(maxX, baseY, z1), Vec3(maxX, y1, z1), Vec3(maxX, y0, z0), trimCol))
            }
        }
    }

    fun addPyramidRoof(
        minX: Float,
        minZ: Float,
        maxX: Float,
        maxZ: Float,
        eaveY: Float,
        peakY: Float,
        roofCol: Color,
        darkCol: Color
    ) {
        val cx = (minX + maxX) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val apex = Vec3(cx, peakY, cz)
        val pSW = Vec3(minX, eaveY, minZ)
        val pSE = Vec3(maxX, eaveY, minZ)
        val pNE = Vec3(maxX, eaveY, maxZ)
        val pNW = Vec3(minX, eaveY, maxZ)
        faces.add(QuadFace3D(pSW, pSE, apex, apex, roofCol))
        faces.add(QuadFace3D(pSE, pNE, apex, apex, darkCol))
        faces.add(QuadFace3D(pNE, pNW, apex, apex, roofCol))
        faces.add(QuadFace3D(pNW, pSW, apex, apex, darkCol))
    }

    fun addWallPolyline(pts: List<Pair<Float, Float>>) {
        for (i in 0 until pts.size - 1) {
            val (ax, az) = pts[i]
            val (bx, bz) = pts[i + 1]
            val minX = minOf(ax, bx) - 0.19f
            val maxX = maxOf(ax, bx) + 0.19f
            val minZ = minOf(az, bz) - 0.19f
            val maxZ = maxOf(az, bz) + 0.19f
            addBox(minX, 0.055f, minZ, maxX, 0.56f, maxZ, StationPlatformSoffit, StationPlatformSoffit)
            addBox(minX + 0.04f, 0.56f, minZ + 0.04f, maxX - 0.04f, 2.28f, maxZ - 0.04f, StationWallCream, StationColumnGrey)
            addBox(minX - 0.03f, 2.28f, minZ - 0.03f, maxX + 0.03f, 2.46f, maxZ + 0.03f, StationWallCoping, StationRoofDeckDark)
        }
    }

    fun addYellowBlackKerbLine(x0: Float, z0: Float, x1: Float, z1: Float, halfW: Float = 0.17f, h: Float = 0.28f) {
        val dx = x1 - x0
        val dz = z1 - z0
        val len = hypot(dx, dz).coerceAtLeast(0.1f)
        val count = (len / 0.85f).toInt().coerceAtLeast(1)
        val ux = dx / count.toFloat()
        val uz = dz / count.toFloat()
        val rx = -dz / len * halfW
        val rz = dx / len * halfW
        for (i in 0 until count) {
            val sx = x0 + ux * i
            val sz = z0 + uz * i
            val ex = sx + ux
            val ez = sz + uz
            val isY = (i % 2 == 0)
            val topCol = if (isY) StationKerbYellow else StationKerbBlack
            val sideCol = if (isY) Color(0xFFCA8A04) else Color(0xFF11151B)
            val p0 = Vec3(sx - rx, 0.06f, sz - rz)
            val p1 = Vec3(ex - rx, 0.06f, ez - rz)
            val p2 = Vec3(ex + rx, 0.06f, ez + rz)
            val p3 = Vec3(sx + rx, 0.06f, sz + rz)
            val p0t = Vec3(sx - rx, h, sz - rz)
            val p1t = Vec3(ex - rx, h, ez - rz)
            val p2t = Vec3(ex + rx, h, ez + rz)
            val p3t = Vec3(sx + rx, h, sz + rz)
            faces.add(QuadFace3D(p0t, p1t, p2t, p3t, topCol))
            faces.add(QuadFace3D(p0, p1, p1t, p0t, sideCol))
            faces.add(QuadFace3D(p2, p3, p3t, p2t, sideCol))
        }
    }

    fun addPitchedCanopyAlongZ(
        x0: Float,
        x1: Float,
        z0: Float,
        z1: Float,
        eaveY: Float,
        ridgeY: Float,
        roofCol: Color,
        darkSlopeCol: Color,
        gableCol: Color
    ) {
        val midX = (x0 + x1) * 0.5f
        faces.add(QuadFace3D(Vec3(x0, eaveY, z0), Vec3(midX, ridgeY, z0), Vec3(midX, ridgeY, z1), Vec3(x0, eaveY, z1), roofCol, null, 0f, 0f, -0.55f, 0.83f, 0f))
        faces.add(QuadFace3D(Vec3(midX, ridgeY, z0), Vec3(x1, eaveY, z0), Vec3(x1, eaveY, z1), Vec3(midX, ridgeY, z1), darkSlopeCol, null, 0f, 0f, 0.55f, 0.83f, 0f))
        faces.add(QuadFace3D(Vec3(x0, eaveY, z0), Vec3(x1, eaveY, z0), Vec3(midX, ridgeY, z0), Vec3(midX, ridgeY, z0), gableCol, null, 0f, 0f, 0f, 0f, -1f))
        faces.add(QuadFace3D(Vec3(x1, eaveY, z1), Vec3(x0, eaveY, z1), Vec3(midX, ridgeY, z1), Vec3(midX, ridgeY, z1), gableCol, null, 0f, 0f, 0f, 0f, 1f))
        addBox(midX - 0.16f, ridgeY - 0.04f, z0 - 0.12f, midX + 0.16f, ridgeY + 0.18f, z1 + 0.12f, StationRoofCharcoal, StationRoofDeckDark, StationRoofCharcoal)
    }

    // =========================================================================
    // 1. COVERED WEST RAILWAY BOARDING PLATFORMS 1 & 2 + FOOT OVERBRIDGE (FOB)
    //    Located directly on the West (back) side of Thrissur Station Hall (real-world SW Thrissur):
    //    - Track 2 (East / Southbound: Shoranur -> Thrissur) runs @ X = -109.4m
    //    - Track 1 (West / Northbound: Thrissur -> Shoranur) runs @ X = -114.6m
    //    - Platform 1 sits right beside Track 2 @ X = -107.20 .. -89.20m (Y = 1.08m, Z = -222 .. -98m)
    //    - Platform 2 sits right beside Track 1 @ X = -124.20 .. -116.80m (Y = 1.08m, Z = -218 .. -100m)
    // =========================================================================

    // --- 1A. PLATFORM 1 (EAST SIDE OF TRACK 2, DIRECTLY BEHIND MAIN STATION HALL) ---
    addBox(-107.20f, 0.05f, -222.0f, -89.20f, 1.08f, -98.0f, StationPlatformTile, StationPlatformSoffit, StationPlatformSoffit)
    // Bright yellow tactile safety strip along Track 2 boarding edge (X = -107.18 .. -106.68m)
    addBox(-107.18f, 1.08f, -222.0f, -106.68f, 1.12f, -98.0f, StationPlatformEdgeYellow)

    // Wide Open Concourse Boarding Passages & Stepped Ramps from East Bus Forecourt onto Platform 1
    addBox(-89.5f, 0.06f, -196.0f, -81.6f, 0.34f, -86.0f, StationPlatformTile, StationPlatformSoffit, StationPlatformSoffit)
    for (rampZ in arrayOf(Pair(-195.0f, -181.0f), Pair(-112.0f, -99.0f))) {
        val rz0 = rampZ.first
        val rz1 = rampZ.second
        // 4 broad concrete boarding steps rising West from concourse Y=0.34m to Platform 1 Y=1.08m
        for (s in 0 until 4) {
            val sx0 = -89.20f + s * 1.15f
            val sx1 = sx0 + 1.18f
            val sy1 = 1.08f - s * 0.185f
            addBox(sx0, 0.06f, rz0, sx1, sy1, rz1, StationPlatformTile, StationPlatformSoffit, StationPlatformSoffit)
        }
        // Stainless / charcoal safety handrails flanking the open boarding entry passages
        addBox(-89.2f, 1.08f, rz0 - 0.14f, -84.6f, 2.02f, rz0 + 0.08f, StationSteelDark)
        addBox(-89.2f, 1.08f, rz1 - 0.08f, -84.6f, 2.02f, rz1 + 0.14f, StationSteelDark)
    }

    // Platform 1 Full-Length Pitched Shelter Canopy (X = -106.2 .. -89.8m, Z = -216.0 .. -102.0m)
    addPitchedCanopyAlongZ(
        x0 = -106.2f, x1 = -89.8f,
        z0 = -216.0f, z1 = -102.0f,
        eaveY = 5.35f, ridgeY = 6.75f,
        roofCol = StationCanopySilver,
        darkSlopeCol = Color(0xFFB4BAC2),
        gableCol = StationRoofCharcoal
    )
    var pf1Z = -212.0f
    var pf1Idx = 0
    while (pf1Z <= -106.0f) {
        // Twin rows of sleek steel platform columns + overhead cross-trusses
        addBox(-103.60f, 1.08f, pf1Z - 0.18f, -103.24f, 5.48f, pf1Z + 0.18f, StationSteelDark, StationRoofDeckDark, StationSteelDark)
        addBox(-93.80f, 1.08f, pf1Z - 0.18f, -93.44f, 5.48f, pf1Z + 0.18f, StationSteelDark, StationRoofDeckDark, StationSteelDark)
        addBox(-103.6f, 5.15f, pf1Z - 0.10f, -93.4f, 5.42f, pf1Z + 0.10f, StationRoofDeckDark)
        // Passenger waiting benches along Platform 1
        if (pf1Idx % 2 == 0 && kotlin.math.abs(pf1Z - (-138.0f)) > 9.0f) {
            addBox(-99.2f, 1.08f, pf1Z - 1.45f, -98.0f, 1.60f, pf1Z + 1.45f, StationBenchWood, StationSteelDark, StationBenchWood)
            addBox(-98.2f, 1.60f, pf1Z - 1.45f, -98.0f, 2.08f, pf1Z + 1.45f, StationBenchWood, StationSteelDark, StationBenchWood)
        }
        pf1Z += 14.0f
        pf1Idx++
    }

    // Iconic Indian Railways Mustard-Yellow Station Name Boards on Platform 1 (South & North ends)
    val tcrPlatformBoardSpec = createThrissurStationMustardBoardSpec(
        cacheKey = "TCR_PLATFORM_MUSTARD_BOARD_V1",
        lineTop = "THRISSUR",
        lineBottom = "തൃശ്ശൂർ • TCR"
    )
    for (boardZ in floatArrayOf(-206.0f, -108.0f)) {
        addBox(-105.00f, 1.08f, boardZ - 3.6f, -104.65f, 3.05f, boardZ - 3.25f, StationWallCream)
        addBox(-105.00f, 1.08f, boardZ + 3.25f, -104.65f, 3.05f, boardZ + 3.6f, StationWallCream)
        addBox(-105.05f, 1.90f, boardZ - 3.7f, -104.60f, 3.30f, boardZ + 3.7f, StationMustardBoard, StationKerbBlack, StationMustardBoard)
        faces.add(
            QuadFace3D(
                Vec3(-105.08f, 1.95f, boardZ + 3.6f),
                Vec3(-105.08f, 1.95f, boardZ - 3.6f),
                Vec3(-105.08f, 3.25f, boardZ - 3.6f),
                Vec3(-105.08f, 3.25f, boardZ + 3.6f),
                baseColor = StationMustardBoard,
                biasDist = -0.6f,
                signboard = tcrPlatformBoardSpec
            )
        )
    }

    // --- 1B. PLATFORM 2 (WEST SIDE OF TRACK 1: X = -124.20 .. -116.80m, Z = -218.0 .. -100.0m) ---
    addBox(-124.20f, 0.05f, -218.0f, -116.80f, 1.08f, -100.0f, StationPlatformTile, StationPlatformSoffit, StationPlatformSoffit)
    // Bright yellow safety tactile strip facing Track 1 (X = -117.30 .. -116.82m)
    addBox(-117.30f, 1.08f, -218.0f, -116.82f, 1.12f, -100.0f, StationPlatformEdgeYellow)
    // Platform 2 Pitched Shelter Canopy
    addPitchedCanopyAlongZ(
        x0 = -123.6f, x1 = -117.5f,
        z0 = -212.0f, z1 = -104.0f,
        eaveY = 5.35f, ridgeY = 6.55f,
        roofCol = StationCanopySilver,
        darkSlopeCol = Color(0xFFB4BAC2),
        gableCol = StationRoofCharcoal
    )
    var pf2Z = -208.0f
    var pf2Idx = 0
    while (pf2Z <= -108.0f) {
        addBox(-120.75f, 1.08f, pf2Z - 0.18f, -120.38f, 5.55f, pf2Z + 0.18f, StationSteelDark, StationRoofDeckDark, StationSteelDark)
        addBox(-123.2f, 5.15f, pf2Z - 0.10f, -117.8f, 5.40f, pf2Z + 0.10f, StationRoofDeckDark)
        if (pf2Idx % 2 == 0 && kotlin.math.abs(pf2Z - (-138.0f)) > 9.0f) {
            addBox(-121.8f, 1.08f, pf2Z - 1.35f, -120.9f, 1.58f, pf2Z + 1.35f, StationBenchWood, StationSteelDark, StationBenchWood)
        }
        pf2Z += 15.0f
        pf2Idx++
    }
    // Mustard-yellow THRISSUR / TCR station name board on Platform 2
    for (boardZ in floatArrayOf(-204.0f, -110.0f)) {
        addBox(-118.85f, 1.08f, boardZ - 3.4f, -118.50f, 3.05f, boardZ - 3.05f, StationWallCream)
        addBox(-118.85f, 1.08f, boardZ + 3.05f, -118.50f, 3.05f, boardZ + 3.4f, StationWallCream)
        addBox(-118.90f, 1.90f, boardZ - 3.5f, -118.45f, 3.30f, boardZ + 3.5f, StationMustardBoard, StationKerbBlack, StationMustardBoard)
        faces.add(
            QuadFace3D(
                Vec3(-118.42f, 1.95f, boardZ - 3.4f),
                Vec3(-118.42f, 1.95f, boardZ + 3.4f),
                Vec3(-118.42f, 3.25f, boardZ + 3.4f),
                Vec3(-118.42f, 3.25f, boardZ - 3.4f),
                baseColor = StationMustardBoard,
                biasDist = -0.6f,
                signboard = tcrPlatformBoardSpec
            )
        )
    }

    // --- 1C. COVERED PEDESTRIAN FOOT OVERBRIDGE (FOB) SPANNING PLATFORM 1 <-> PLATFORM 2 ---
    // Aligned with central Clock Tower concourse @ Z = -139.8 .. -136.2m
    // Deck underside at Y = 6.85m (>0.70m clear headroom above WAG-9 @ 5.94m and Coach 2 @ 6.15m!)
    addBox(-122.4f, 6.85f, -139.8f, -101.6f, 7.18f, -136.2f, StationPlatformTile, StationRoofCharcoal, StationRoofCharcoal, StationRoofDeckDark)
    // FOB side steel safety balustrades (South & North sides)
    addBox(-122.4f, 7.18f, -139.85f, -101.6f, 8.38f, -139.65f, StationFasciaLight, StationRoofDeckDark, StationFasciaLight)
    addBox(-122.4f, 7.18f, -136.35f, -101.6f, 8.38f, -136.15f, StationFasciaLight, StationRoofDeckDark, StationFasciaLight)
    // FOB steel support towers standing strictly on Platform 1 (X=-104.2) & Platform 2 (X=-119.8) — 100% clear of tracks!
    for (towerX in floatArrayOf(-104.2f, -119.8f)) {
        addBox(towerX - 0.28f, 1.08f, -139.85f, towerX + 0.28f, 9.65f, -139.35f, StationSteelDark)
        addBox(towerX - 0.28f, 1.08f, -136.65f, towerX + 0.28f, 9.65f, -136.15f, StationSteelDark)
    }
    // FOB covered roof canopy over the overhead footbridge
    addBox(-122.8f, 9.55f, -140.2f, -101.2f, 9.95f, -135.8f, StationRoofCharcoal, StationRoofDeckDark, StationRoofCharcoal, StationFasciaShadow)
    // Stepped Passenger Staircases descending South onto Platform 1 (X=-103.0..-100.6) & Platform 2 (X=-122.4..-120.0)
    for (stairX0 in floatArrayOf(-103.0f, -122.4f)) {
        val sx1 = stairX0 + 2.4f
        for (st in 0 until 8) {
            val sz1 = -139.8f - st * 1.25f
            val sz0 = sz1 - 1.30f
            val syTop = 7.05f - st * 0.74f
            addBox(stairX0, syTop - 0.35f, sz0, sx1, syTop, sz1, StationPlatformTile, StationPlatformSoffit, StationPlatformSoffit)
        }
        // Stairwell outer safety walls
        addBox(stairX0 - 0.14f, 1.08f, -150.0f, stairX0 + 0.04f, 2.35f, -139.8f, StationWallCream, StationColumnGrey, StationWallCream)
        addBox(sx1 - 0.04f, 1.08f, -150.0f, sx1 + 0.14f, 2.35f, -139.8f, StationWallCream, StationColumnGrey, StationWallCream)
    }

    // Verandah kerbside safety stripe along East bus forecourt edge (X = -81.78m)
    addYellowBlackKerbLine(-81.78f, -182.0f, -81.78f, -94.0f)

    // South Wing & North Wing Twin Station Hall Pavilions (flanking the central Clock Tower)
    for ((wMinZ, wMaxZ) in arrayOf(Pair(-180.0f, -141.4f), Pair(-134.6f, -96.0f))) {
        addBox(-89.2f, 0.34f, wMinZ, -83.0f, 1.42f, wMaxZ, StationWallCream, StationColumnGrey, StationWallCream)
        addBox(-89.0f, 1.42f, wMinZ + 0.35f, -83.2f, 4.95f, wMaxZ - 0.35f, StationGlassTint, StationGlassTint, StationGlassTint)
        var pz = wMinZ + 0.4f
        while (pz <= wMaxZ - 0.3f) {
            addBox(-83.4f, 0.34f, pz - 0.32f, -82.4f, 5.45f, pz + 0.32f, StationColumnGrey, StationFasciaShadow, StationColumnGrey)
            addBox(-89.4f, 0.34f, pz - 0.32f, -88.6f, 5.45f, pz + 0.32f, StationColumnGrey, StationFasciaShadow, StationColumnGrey)
            pz += 7.2f
        }
        // Upper fascia cornice & Barrel-Vaulted Charcoal Roof (Y = 5.45m .. 8.15m)
        addBox(-89.5f, 5.20f, wMinZ - 0.35f, -82.2f, 5.82f, wMaxZ + 0.35f, StationFasciaLight, StationFasciaShadow, StationFasciaLight)
        addBarrelVaultRoof(-89.5f, -82.2f, wMinZ - 0.25f, wMaxZ + 0.25f, baseY = 5.82f, archRise = 2.25f, archAlongX = true, topCol = StationRoofCharcoal, trimCol = StationRoofDeckDark)
        // Charcoal ridge cap along vault crest
        addBox(-86.15f, 8.02f, wMinZ - 0.35f, -85.55f, 8.26f, wMaxZ + 0.35f, StationRoofDeckDark, StationSteelDark, StationRoofDeckDark)
    }

    // =========================================================================
    // 2. CENTRAL ARCHITECTURAL SPIRE CLOCK TOWER (X = -89.4 .. -82.2, Z = -141.4 .. -134.6)
    //    Dial positioned cleanly at Y = 6.30 .. 8.35m facing East toward Station Road!
    // =========================================================================
    addBox(-89.3f, 0.34f, -141.4f, -82.2f, 5.82f, -134.6f, StationWallCream, StationColumnGrey, StationWallCream)
    // Grand recessed glass main entrance portal facing East bus bay forecourt & Station Road
    addBox(-82.50f, 0.34f, -140.2f, -82.05f, 4.45f, -135.8f, StationGlassTint, StationGlassTint, StationGlassTint)
    addBox(-82.60f, 4.45f, -140.6f, -81.90f, 4.88f, -135.4f, StationRoofCharcoal, StationRoofDeckDark, StationRoofCharcoal)
    // Tower shaft (Y = 5.82 .. 11.45m) + Spire Pyramid (11.75 .. 14.60m)
    addBox(-88.6f, 5.82f, -140.6f, -82.8f, 11.45f, -135.4f, StationWallCream, StationColumnGrey, StationWallCream)
    addBox(-88.85f, 11.25f, -140.85f, -82.55f, 11.75f, -135.15f, StationFasciaLight, StationRoofDeckDark, StationFasciaLight)
    addPyramidRoof(-89.0f, -141.0f, -82.4f, -135.0f, eaveY = 11.75f, peakY = 14.60f, roofCol = StationRoofCharcoal, darkCol = StationRoofDeckDark)

    // Analog clock dial on East tower face (Y = 6.30 .. 8.35m, facing East toward Station Road)
    val stationClockSpec = createClockDialSignSpec("THRISSUR_STATION_CLOCK_DIAL_V2", wallBgColorInt = 0xFF363B42.toInt())
    faces.add(
        QuadFace3D(
            Vec3(-82.76f, 6.30f, -139.05f),
            Vec3(-82.76f, 6.30f, -136.95f),
            Vec3(-82.76f, 8.35f, -136.95f),
            Vec3(-82.76f, 8.35f, -139.05f),
            baseColor = StationRoofCharcoal,
            biasDist = -1.6f,
            signboard = stationClockSpec
        )
    )

    // =========================================================================
    // 3. STAND NAME "THRISSUR RAILWAY STATION" — TALL OSWALD BOLD FONT ABOVE ROOF!
    //    Elevated cleanly to Y = 9.15 .. 12.40m (3.25m tall) over Z = -157.0 .. -119.0m,
    //    facing East toward Station Road, above the 8.26m vault ridge & clock dial.
    // =========================================================================
    for (strutZ in floatArrayOf(-153.0f, -143.5f, -132.5f, -123.0f)) {
        addBox(-82.22f, 5.75f, strutZ - 0.14f, -81.98f, 9.35f, strutZ + 0.14f, StationSteelDark, StationRoofDeckDark)
    }
    addBox(-82.22f, 9.02f, -157.2f, -81.98f, 9.18f, -118.8f, StationRoofDeckDark, StationSteelDark)

    val stationRoofTitleSpec = createStation3DTextSpec(
        cacheKey = "THRISSUR_STATION_ROOF_TITLE_V2",
        text = "THRISSUR RAILWAY STATION",
        bmpWidth = 1720,
        bmpHeight = 196,
        faceColorInt = 0xFFF8FAFC.toInt(),
        extrudeColorInt = 0xFF2B3036.toInt(),
        textSizePx = 128f
    )
    faces.add(
        QuadFace3D(
            Vec3(-82.06f, 9.15f, -157.0f),
            Vec3(-82.06f, 9.15f, -119.0f),
            Vec3(-82.06f, 12.40f, -119.0f),
            Vec3(-82.06f, 12.40f, -157.0f),
            baseColor = Color.White,
            biasDist = -1.8f,
            signboard = stationRoofTitleSpec
        )
    )

    // =========================================================================
    // 4. CANTILEVERED PORTICO CANOPY OVER BUS BAYS 1..4 (X = -82.2 .. -75.0, Z = -167.0 .. -107.0)
    //    Unified Charcoal Canopy & Light Fascia (0% road/bay obstruction!)
    // =========================================================================
    faces.add(
        QuadFace3D(
            Vec3(-82.2f, 5.08f, -167.0f),
            Vec3(-75.0f, 5.78f, -167.0f),
            Vec3(-75.0f, 5.78f, -107.0f),
            Vec3(-82.2f, 5.08f, -107.0f),
            StationFasciaLight, null, 0f, 0f, 0f, -1f, 0f
        )
    )
    faces.add(
        QuadFace3D(
            Vec3(-82.2f, 5.22f, -167.0f),
            Vec3(-82.2f, 5.22f, -107.0f),
            Vec3(-75.0f, 5.92f, -107.0f),
            Vec3(-75.0f, 5.92f, -167.0f),
            StationRoofCharcoal, null, 0f, 0f, 0f, 1f, 0f
        )
    )
    addBox(-75.15f, 5.72f, -167.1f, -74.85f, 5.98f, -106.9f, StationRoofDeckDark, StationRoofCharcoal, StationRoofDeckDark)
    var cZ = -165.0f
    while (cZ <= -109.0f) {
        addBox(-82.25f, 0.34f, cZ - 0.16f, -81.95f, 5.25f, cZ + 0.16f, StationSteelDark)
        addBox(-82.15f, 4.92f, cZ - 0.10f, -75.05f, 5.14f, cZ + 0.10f, StationRoofDeckDark)
        cZ += 14.0f
    }

    // Crisp White Floor Bay Numbers "1" .. "4" on the bus bay floor (Z = -158, -144, -130, -116)
    for (bIdx in STATION_BUS_BAY_CENTERS_Z.indices) {
        val bz = STATION_BUS_BAY_CENTERS_Z[bIdx]
        val numSpec = createStationBayNumberSpec("TCR_STATION_BUS_BAY_NUM_V2_${bIdx + 1}", "${bIdx + 1}")
        faces.add(
            QuadFace3D(
                Vec3(STATION_BUS_BAY_X - 1.15f, 0.068f, bz + 3.6f),
                Vec3(STATION_BUS_BAY_X + 1.15f, 0.068f, bz + 3.6f),
                Vec3(STATION_BUS_BAY_X + 1.15f, 0.068f, bz + 6.0f),
                Vec3(STATION_BUS_BAY_X - 1.15f, 0.068f, bz + 6.0f),
                baseColor = Color.White,
                biasDist = -0.9f,
                signboard = numSpec
            )
        )
    }

    // =========================================================================
    // 5. PERIMETER COMPOUND WALL (X = -89.2 .. -64.0, Z = -204.0 .. -62.0)
    //    West side along Track 2 (X = -107.2m) is 100% OPEN for Platform 1 train boarding
    //    just like Shoranur Junction! East Wall along X = -64.0 (facing Station Road) has TWO 12m Gate Portals:
    //    - South ENTRY Portal: Z = -174.0 .. -162.0m (center -168.0m)
    //    - North EXIT Portal : Z = -114.0 .. -102.0m (center -108.0m)
    // =========================================================================
    addWallPolyline(listOf(Pair(-89.2f, -204.0f), Pair(-64.0f, -204.0f)))
    addWallPolyline(listOf(Pair(-89.2f, -62.0f), Pair(-64.0f, -62.0f)))
    addWallPolyline(listOf(Pair(-89.2f, -98.0f), Pair(-89.2f, -62.0f)))
    addWallPolyline(listOf(Pair(-64.0f, -204.0f), Pair(-64.0f, -174.5f)))
    addWallPolyline(listOf(Pair(-64.0f, -161.5f), Pair(-64.0f, -114.5f)))
    addWallPolyline(listOf(Pair(-64.0f, -101.5f), Pair(-64.0f, -62.0f)))

    // =========================================================================
    // 6. TWIN EAST GATE PORTALS AT X = -64.0 (FACING STATION ROAD) — TALL OSWALD BOLD "ENTRY" & "EXIT"
    //    Matched to Sakthan Bus Stand cream pillars, light fascia & charcoal coping.
    // =========================================================================
    val gateX = -64.0f

    // --- SOUTH ENTRY PORTAL (Z = -174.0 .. -162.0m, center -168.0m) ---
    addBox(gateX - 0.45f, 0.06f, -174.85f, gateX + 0.45f, 6.15f, -174.05f, StationWallCream, StationColumnGrey, StationWallCream)
    addBox(gateX - 0.45f, 0.06f, -161.95f, gateX + 0.45f, 6.15f, -161.15f, StationWallCream, StationColumnGrey, StationWallCream)
    addBox(gateX - 0.52f, 5.55f, -175.05f, gateX + 0.52f, 6.25f, -160.95f, StationFasciaLight, StationFasciaShadow, StationWallCream, StationFasciaShadow)
    addBox(gateX - 0.58f, 6.25f, -175.15f, gateX + 0.58f, 6.46f, -160.85f, StationRoofCharcoal, StationRoofDeckDark, StationRoofCharcoal)
    val entryTextSpec = createStation3DTextSpec(
        cacheKey = "THRISSUR_STATION_GATE_3D_ENTRY_V2",
        text = "ENTRY",
        bmpWidth = 512,
        bmpHeight = 160,
        faceColorInt = 0xFFF8FAFC.toInt(),
        extrudeColorInt = 0xFF2B3036.toInt(),
        textSizePx = 124f
    )
    faces.add(
        QuadFace3D(
            Vec3(gateX, 6.46f, -171.2f),
            Vec3(gateX, 6.46f, -164.8f),
            Vec3(gateX, 8.28f, -164.8f),
            Vec3(gateX, 8.28f, -171.2f),
            baseColor = StationSteelDark,
            biasDist = -1.6f,
            signboard = entryTextSpec
        )
    )
    // South ENTRY Boom Barrier Pedestal (outside East gate at X = -63.32) & Rest Post
    addBox(gateX + 0.54f, 0.06f, -173.82f, gateX + 0.84f, 1.05f, -173.54f, StationKerbYellow, StationKerbBlack, StationKerbYellow)
    addBox(gateX + 0.54f, 0.06f, -162.46f, gateX + 0.84f, 0.92f, -162.18f, StationKerbBlack, StationKerbYellow, StationKerbBlack)

    // --- NORTH EXIT PORTAL (Z = -114.0 .. -102.0m, center -108.0m) ---
    addBox(gateX - 0.45f, 0.06f, -114.85f, gateX + 0.45f, 6.15f, -114.05f, StationWallCream, StationColumnGrey, StationWallCream)
    addBox(gateX - 0.45f, 0.06f, -101.95f, gateX + 0.45f, 6.15f, -101.15f, StationWallCream, StationColumnGrey, StationWallCream)
    addBox(gateX - 0.52f, 5.55f, -115.05f, gateX + 0.52f, 6.25f, -100.95f, StationFasciaLight, StationFasciaShadow, StationWallCream, StationFasciaShadow)
    addBox(gateX - 0.58f, 6.25f, -115.15f, gateX + 0.58f, 6.46f, -100.85f, StationRoofCharcoal, StationRoofDeckDark, StationRoofCharcoal)
    val exitTextSpec = createStation3DTextSpec(
        cacheKey = "THRISSUR_STATION_GATE_3D_EXIT_V2",
        text = "EXIT",
        bmpWidth = 512,
        bmpHeight = 160,
        faceColorInt = 0xFFF8FAFC.toInt(),
        extrudeColorInt = 0xFF2B3036.toInt(),
        textSizePx = 124f
    )
    faces.add(
        QuadFace3D(
            Vec3(gateX, 6.46f, -111.2f),
            Vec3(gateX, 6.46f, -104.8f),
            Vec3(gateX, 8.28f, -104.8f),
            Vec3(gateX, 8.28f, -111.2f),
            baseColor = StationSteelDark,
            biasDist = -1.6f,
            signboard = exitTextSpec
        )
    )
    // North EXIT Boom Barrier Pedestal (inside East gate at X = -64.68) & Rest Post
    addBox(gateX - 0.84f, 0.06f, -102.46f, gateX - 0.54f, 1.05f, -102.18f, StationKerbYellow, StationKerbBlack, StationKerbYellow)
    addBox(gateX - 0.84f, 0.06f, -113.82f, gateX - 0.54f, 0.92f, -113.54f, StationKerbBlack, StationKerbYellow, StationKerbBlack)

    // Guard booth cleanly centered outside East perimeter wall between the two gates (Z = -140.1 .. -138.1)
    addBox(gateX + 0.15f, 0.06f, -140.1f, gateX + 2.05f, 2.55f, -138.1f, StationWallCream, StationColumnGrey, StationWallCream)
    addBox(gateX - 0.05f, 2.55f, -140.3f, gateX + 2.25f, 2.82f, -137.9f, StationWallCoping, StationRoofDeckDark)
    addBox(gateX + 2.02f, 1.25f, -139.65f, gateX + 2.08f, 2.05f, -138.55f, StationGlassTint)

    faces
}

private fun appendStationBoomBarrierArm(
    pivotX: Float,
    pivotY: Float,
    pivotZ: Float,
    dirZ: Float,
    armLength: Float,
    openFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    val angleRad = openFrac.coerceIn(0f, 1f) * Math.toRadians(84.0).toFloat()
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val uz = dirZ * cosA
    val uy = sinA
    val vz = -dirZ * sinA
    val vy = cosA

    fun addRotatedSegment(s0: Float, s1: Float, halfT: Float, topCol: Color, sideCol: Color) {
        fun pt(s: Float, dv: Float, dx: Float) = Vec3(
            pivotX + dx,
            pivotY + uy * s + vy * dv,
            pivotZ + uz * s + vz * dv
        )
        val c000 = pt(s0, -halfT, -halfT)
        val c100 = pt(s1, -halfT, -halfT)
        val c110 = pt(s1, halfT, -halfT)
        val c010 = pt(s0, halfT, -halfT)
        val c001 = pt(s0, -halfT, halfT)
        val c101 = pt(s1, -halfT, halfT)
        val c111 = pt(s1, halfT, halfT)
        val c011 = pt(s0, halfT, halfT)

        outFaces.add(QuadFace3D(c011, c111, c110, c010, topCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c000, c100, c101, c001, sideCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c001, c101, c111, c011, topCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c100, c000, c010, c110, sideCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c000, c001, c011, c010, sideCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c101, c100, c110, c111, sideCol, biasDist = -0.35f))
    }

    addRotatedSegment(-0.66f, 0.0f, 0.075f, StationSteelDark, StationKerbBlack)

    val segCount = 14
    val segLen = armLength / segCount.toFloat()
    for (i in 0 until segCount) {
        val s0 = i * segLen
        val s1 = (i + 1) * segLen
        val isRed = (i % 2 == 0)
        val topCol = if (isRed) StationBarrierRed else StationBarrierWhite
        val sideCol = if (isRed) Color(0xFF991B1B) else Color(0xFFCBD5E1)
        addRotatedSegment(s0, s1, 0.055f, topCol, sideCol)
    }
}

internal fun collectStationDynamicBarrierFaces(
    entryOpenFrac: Float,
    exitOpenFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    // South ENTRY boom arm hinged outside East gate at (-63.32, 0.92, -173.68), extending +Z across South ENTRY portal
    appendStationBoomBarrierArm(
        pivotX = -63.32f,
        pivotY = 0.92f,
        pivotZ = -173.68f,
        dirZ = 1f,
        armLength = 11.40f,
        openFrac = entryOpenFrac,
        outFaces = outFaces
    )
    // North EXIT boom arm hinged inside East gate at (-64.68, 0.92, -102.32), extending -Z across North EXIT portal
    appendStationBoomBarrierArm(
        pivotX = -64.68f,
        pivotY = 0.92f,
        pivotZ = -102.32f,
        dirZ = -1f,
        armLength = 11.40f,
        openFrac = exitOpenFrac,
        outFaces = outFaces
    )
}

internal fun collectThrissurModernStationSceneFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - (-92.0f)
    val dz = camEye.z - (-138.0f)
    if (dx * dx + dz * dz > 260f * 260f) return
    outFaces.addAll(cachedThrissurStationFaces)
}

internal fun collectAllThrissurModernStationStaticFaces(
    outFaces: MutableList<QuadFace3D>
) {
    outFaces.addAll(cachedThrissurStationFaces)
}

private fun createStation3DTextSpec(
    cacheKey: String,
    text: String,
    bmpWidth: Int,
    bmpHeight: Int,
    faceColorInt: Int,
    extrudeColorInt: Int,
    textSizePx: Float
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = bmpWidth,
    bmpHeight = bmpHeight,
    isDoubleSided = true,
    renderBitmap = {
        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.TRANSPARENT)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = textSizePx
            typeface = PremiumFonts.stationDisplayTypeface()
            letterSpacing = 0.05f
        }
        val safeW = bmpWidth - 32f
        val measured = p.measureText(text)
        if (measured > safeW && measured > 1f) {
            p.textSize = (textSizePx * (safeW / measured)).coerceAtLeast(24f)
        }
        val fm = p.fontMetrics
        val cx = bmpWidth * 0.5f
        val baseY = (bmpHeight - (fm.ascent + fm.descent)) * 0.5f

        p.color = extrudeColorInt
        for (d in 6 downTo 1) {
            c.drawText(text, cx + d * 1.3f, baseY + d * 1.3f, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4.0f
        p.color = extrudeColorInt
        c.drawText(text, cx, baseY, p)

        p.style = Paint.Style.FILL
        p.color = faceColorInt
        c.drawText(text, cx, baseY, p)
        bmp
    }
)

private fun createStationBayNumberSpec(
    cacheKey: String,
    bayNumberText: String
): SignboardQuadSpec = SignboardQuadSpec(
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
            textSize = 116f
            typeface = PremiumFonts.stationDisplayTypeface()
            letterSpacing = 0.04f
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = 0x88181D24.toInt()
        c.drawText(bayNumberText, 80f, 124f, p)

        p.style = Paint.Style.FILL
        p.color = 0xFFF8FAFC.toInt()
        c.drawText(bayNumberText, 80f, 124f, p)
        bmp
    }
)

private fun createThrissurStationMustardBoardSpec(
    cacheKey: String,
    lineTop: String,
    lineBottom: String
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = 768,
    bmpHeight = 256,
    isDoubleSided = true,
    renderBitmap = {
        val bmp = Bitmap.createBitmap(768, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF5C428.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 10f
        p.color = 0xFF1E2228.toInt()
        c.drawRect(10f, 10f, 758f, 246f, p)

        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = PremiumFonts.displayTypefaceFor(lineTop, bold = true)
        p.color = 0xFF14181F.toInt()
        p.textSize = 78f
        c.drawText(lineTop, 384f, 114f, p)

        p.typeface = PremiumFonts.uiTypefaceFor(lineBottom, bold = true)
        p.textSize = 54f
        c.drawText(lineBottom, 384f, 198f, p)
        bmp
    }
)
