package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.ui.theme.PremiumFonts
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * High-detail OpenGL ES 3.0 3D Architectural Model of Sakthan Bus Terminal, Thrissur.
 *
 * Upgraded Architectural & Layout Spec:
 * - Warm stone-earth surround & paved architectural concrete/stone basement plinth,
 *   curved NW apron pocket, and neutral concrete/stone bay divider median islands.
 * - Solid 3D dark-charcoal corrugated roofs on both L-wings PLUS a full cantilevered/pillared
 *   bus-bay shelter canopy roof covering Bays 1..8 (5.45m clear height, columns strictly on
 *   divider islands with 0% lane encroachment).
 * - Front ENTRY ONLY at North perimeter (X = 0.6..13.8 m, Z = -140.0 m), with closed NE compound wall.
 * - Back EXIT Gate at South perimeter (X = 20.0..33.0 m, Z = -207.5 m) connecting cleanly to the
 *   South-East boundary exit road -> Ollur / M.O. Road -> Swaraj Round.
 * - All 3D rooftop signs, shopfronts, gate headers, and bay floor numbers aligned with
 *   dot > 0f front-facing winding so zero text appears mirrored or inverted.
 */

private val SakthanGroundEarth = Color(0xFF6A645B)
private val SakthanBasementStone = Color(0xFF938E85)
private val SakthanBayIslandStone = Color(0xFFA9A59B)
private val SakthanApronAsphalt = Color(0xFF474D56)
private val SakthanApronApproach = Color(0xFF393E46)
private val SakthanJointLine = Color(0x5523272D)
private val SakthanWhiteMark = Color(0xFFF1F5F9)
private val SakthanWallCream = Color(0xFFD6D2CA)
private val SakthanWallCoping = Color(0xFF9CA3AF)
private val SakthanColumnGrey = Color(0xFFC2BEB6)
private val SakthanFasciaLight = Color(0xFFE2E0DB)
private val SakthanFasciaShadow = Color(0xFFAEB2B8)
private val SakthanPlatformTile = Color(0xFFCBC6BC)
private val SakthanPlatformSoffit = Color(0xFF6B7280)
private val SakthanRoofCharcoal = Color(0xFF363B42)
private val SakthanRoofDeckDark = Color(0xFF2B3036)
private val SakthanKerbYellow = Color(0xFFEAB308)
private val SakthanKerbBlack = Color(0xFF1E2228)
private val SakthanWoodTeak = Color(0xFF784B2A)
private val SakthanSteelDark = Color(0xFF282C33)
private val SakthanGlassTint = Color(0xFF1F3448)
private val SakthanBarrierRed = Color(0xFFDC2626)
private val SakthanBarrierWhite = Color(0xFFF8FAFC)

internal fun DrawScope.drawSakthanModernTerminalYard(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - 6f, camEye.z - (-174f)) > 360f) return

    val yGroundOuter = 0.050f
    val yBasementPlinth = 0.052f
    val yFrontPocket = 0.056f
    val yApron = 0.058f

    // 1. Warm Stone-Earth Surround & Architectural Concrete Plinth Underneath Terminal Basement
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-39.5f, yGroundOuter, -226.0f),
            Vec3(52.0f, yGroundOuter, -226.0f),
            Vec3(52.0f, yGroundOuter, -122.0f),
            Vec3(-39.5f, yGroundOuter, -122.0f)
        )
    ) {
        drawPath(path, SakthanGroundEarth)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-36.5f, yBasementPlinth, -213.0f),
            Vec3(45.5f, yBasementPlinth, -213.0f),
            Vec3(45.5f, yBasementPlinth, -134.5f),
            Vec3(-36.5f, yBasementPlinth, -134.5f)
        )
    ) {
        drawPath(path, SakthanBasementStone)
    }

    // 2. Smooth Grey Asphalt / Concrete Courtyard Strictly Inside Perimeter Walls
    //    Main interior slab (Z = -207.3 .. -143.1)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-30.8f, yApron, -207.3f),
            Vec3(40.3f, yApron, -207.3f),
            Vec3(40.3f, yApron, -143.1f),
            Vec3(-30.8f, yApron, -143.1f)
        )
    ) {
        drawPath(path, SakthanApronAsphalt)
    }
    // Front East & ENTRY apron extension (X = -1.8 .. +40.3, Z = -143.1 .. -139.8)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-1.8f, yApron, -143.1f),
            Vec3(40.3f, yApron, -143.1f),
            Vec3(40.3f, yApron, -139.8f),
            Vec3(-1.8f, yApron, -139.8f)
        )
    ) {
        drawPath(path, SakthanApronAsphalt)
    }
    // Front West straight corner apron (X = -30.8 .. -17.5, Z = -143.1 .. -139.8)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-30.8f, yApron, -143.1f),
            Vec3(-17.5f, yApron, -143.1f),
            Vec3(-17.5f, yApron, -139.8f),
            Vec3(-30.8f, yApron, -139.8f)
        )
    ) {
        drawPath(path, SakthanApronAsphalt)
    }
    // Curved NW Pocket Inside Courtyard Polygon + Paved Stone Apron Outside Curved Wall
    val curveInnerPts = listOf(
        Vec3(-17.6f, yApron, -143.2f),
        Vec3(-17.5f, yApron, -140.0f),
        Vec3(-13.8f, yApron, -141.7f),
        Vec3(-9.5f, yApron, -143.1f),
        Vec3(-4.8f, yApron, -141.9f),
        Vec3(-1.6f, yApron, -140.0f),
        Vec3(-1.6f, yApron, -143.2f)
    )
    if (rig.buildWorldPolygonPath(path, curveInnerPts)) {
        drawPath(path, SakthanApronAsphalt)
    }
    val curveOuterStonePts = listOf(
        Vec3(-17.6f, yFrontPocket, -136.0f),
        Vec3(-17.5f, yFrontPocket, -140.0f),
        Vec3(-13.8f, yFrontPocket, -141.7f),
        Vec3(-9.5f, yFrontPocket, -143.1f),
        Vec3(-4.8f, yFrontPocket, -141.9f),
        Vec3(-1.6f, yFrontPocket, -140.0f),
        Vec3(-1.6f, yFrontPocket, -136.0f)
    )
    if (rig.buildWorldPolygonPath(path, curveOuterStonePts)) {
        drawPath(path, SakthanBasementStone)
    }

    // 3. Approach Asphalt Slabs Right at Front ENTRY (Z = -140.0) & Back EXIT (Z = -207.5)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(0.5f, 0.0585f, -142.0f),
            Vec3(13.9f, 0.0585f, -142.0f),
            Vec3(13.9f, 0.0585f, -138.2f),
            Vec3(0.5f, 0.0585f, -138.2f)
        )
    ) {
        drawPath(path, SakthanApronApproach)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(20.0f, 0.0585f, -209.6f),
            Vec3(33.0f, 0.0585f, -209.6f),
            Vec3(33.0f, 0.0585f, -205.4f),
            Vec3(20.0f, 0.0585f, -205.4f)
        )
    ) {
        drawPath(path, SakthanApronApproach)
    }

    // 4. Subtle Concrete Expansion Joint Grid Across Open Apron
    if (hypot(camEye.x - 6f, camEye.z - (-174f)) < 165f) {
        var gx = -24.0f
        while (gx <= 36.0f) {
            rig.projectSegmentClipped(Vec3(gx, 0.059f, -207.0f), Vec3(gx, 0.059f, -143.2f))?.let { seg ->
                drawLine(SakthanJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gx += 6.0f
        }
        var gz = -202.0f
        while (gz <= -144.0f) {
            rig.projectSegmentClipped(Vec3(-30.5f, 0.059f, gz), Vec3(40.0f, 0.059f, gz))?.let { seg ->
                drawLine(SakthanJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gz += 6.0f
        }
    }

    // 5. Crisp White Circulation Guide Curve From Front ENTRY Across Courtyard Toward Back EXIT
    val whiteCurvePts = listOf(
        Vec3(-28.6f, 0.060f, -162.0f),
        Vec3(-28.6f, 0.060f, -148.0f),
        Vec3(-26.5f, 0.060f, -143.8f),
        Vec3(-20.0f, 0.060f, -143.6f),
        Vec3(-14.5f, 0.060f, -145.0f),
        Vec3(-7.0f, 0.060f, -146.5f),
        Vec3(4.0f, 0.060f, -146.5f),
        Vec3(19.5f, 0.060f, -147.2f),
        Vec3(26.5f, 0.060f, -152.5f),
        Vec3(26.5f, 0.060f, -172.0f),
        Vec3(26.5f, 0.060f, -205.5f)
    )
    for (i in 0 until whiteCurvePts.size - 1) {
        rig.projectSegmentClipped(whiteCurvePts[i], whiteCurvePts[i + 1])?.let { seg ->
            drawLine(SakthanWhiteMark, seg.first, seg.second, strokeWidth = 1.8f)
        }
    }

    // 6. Crisp White Painted Bay Boundary Lines for 8 Linear Bus Bays (1..8)
    //    Bay centers: bx = -19.75f + bIdx * 5.0f -> X = -22.25 .. +17.75 m
    val bayZ0 = -190.5f
    val bayZ1 = -170.5f
    val bayLineHalfW = 1.92f
    val tireDark = Color(0x441B1F26)

    for (bIdx in 0 until 8) {
        val bx = -19.75f + bIdx * 5.0f

        for (tx in floatArrayOf(-0.92f, 0.92f)) {
            rig.projectSegmentClipped(
                Vec3(bx + tx, 0.0595f, bayZ0 + 1.5f),
                Vec3(bx + tx, 0.0595f, bayZ1 - 1.2f)
            )?.let { seg ->
                drawLine(tireDark, seg.first, seg.second, strokeWidth = 2.6f)
            }
        }

        rig.projectSegmentClipped(
            Vec3(bx - bayLineHalfW, 0.0605f, bayZ0 + 0.4f),
            Vec3(bx - bayLineHalfW, 0.0605f, bayZ1)
        )?.let { seg ->
            drawLine(SakthanWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(
            Vec3(bx + bayLineHalfW, 0.0605f, bayZ0 + 0.4f),
            Vec3(bx + bayLineHalfW, 0.0605f, bayZ1)
        )?.let { seg ->
            drawLine(SakthanWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(
            Vec3(bx - bayLineHalfW, 0.0605f, bayZ1),
            Vec3(bx + bayLineHalfW, 0.0605f, bayZ1)
        )?.let { seg ->
            drawLine(SakthanWhiteMark, seg.first, seg.second, strokeWidth = 1.6f)
        }
    }
}

private val cachedSakthanModernTerminalFaces: List<QuadFace3D> by lazy {
    val faces = ArrayList<QuadFace3D>(3200)

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
        // Top (+Y)
        faces.add(
            QuadFace3D(
                Vec3(minX, maxY, minZ),
                Vec3(minX, maxY, maxZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(maxX, maxY, minZ),
                topCol, null, bias, 0f, 0f, 1f, 0f
            )
        )
        // Optional Bottom (-Y)
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
        // South (-Z)
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, minZ),
                Vec3(maxX, minY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(minX, maxY, minZ),
                frontBackCol, null, bias, 0f, 0f, 0f, -1f
            )
        )
        // North (+Z)
        faces.add(
            QuadFace3D(
                Vec3(maxX, minY, maxZ),
                Vec3(minX, minY, maxZ),
                Vec3(minX, maxY, maxZ),
                Vec3(maxX, maxY, maxZ),
                frontBackCol, null, bias, 0f, 0f, 0f, 1f
            )
        )
        // West (-X)
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, maxZ),
                Vec3(minX, minY, minZ),
                Vec3(minX, maxY, minZ),
                Vec3(minX, maxY, maxZ),
                sideCol, null, bias, 0f, -1f, 0f, 0f
            )
        )
        // East (+X)
        faces.add(
            QuadFace3D(
                Vec3(maxX, minY, minZ),
                Vec3(maxX, minY, maxZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(maxX, maxY, minZ),
                sideCol, null, bias, 0f, 1f, 0f, 0f
            )
        )
    }

    fun addWallPolyline(
        pts: List<Pair<Float, Float>>,
        wallMinY: Float = 0.06f,
        wallMaxY: Float = 2.25f,
        halfThick: Float = 0.22f
    ) {
        for (i in 0 until pts.size - 1) {
            val (x0, z0) = pts[i]
            val (x1, z1) = pts[i + 1]
            val dx = x1 - x0
            val dz = z1 - z0
            val len = hypot(dx, dz)
            if (len < 0.05f) continue
            val nx = -dz / len
            val nz = dx / len

            val ox = nx * halfThick
            val oz = nz * halfThick
            val cx = nx * (halfThick + 0.05f)
            val cz = nz * (halfThick + 0.05f)
            val capBotY = wallMaxY
            val capTopY = wallMaxY + 0.14f

            val a0 = Vec3(x0 - ox, wallMinY, z0 - oz)
            val b0 = Vec3(x1 - ox, wallMinY, z1 - oz)
            val c0 = Vec3(x1 - ox, wallMaxY, z1 - oz)
            val d0 = Vec3(x0 - ox, wallMaxY, z0 - oz)
            faces.add(QuadFace3D(a0, b0, c0, d0, SakthanWallCream, null, 0f, 0f, -nx, 0f, -nz))

            val a1 = Vec3(x1 + ox, wallMinY, z1 + oz)
            val b1 = Vec3(x0 + ox, wallMinY, z0 + oz)
            val c1 = Vec3(x0 + ox, wallMaxY, z0 + oz)
            val d1 = Vec3(x1 + ox, wallMaxY, z1 + oz)
            faces.add(QuadFace3D(a1, b1, c1, d1, SakthanWallCream, null, 0f, 0f, nx, 0f, nz))

            val t0 = Vec3(x0 - cx, capTopY, z0 - cz)
            val t1 = Vec3(x1 - cx, capTopY, z1 - cz)
            val t2 = Vec3(x1 + cx, capTopY, z1 + cz)
            val t3 = Vec3(x0 + cx, capTopY, z0 + cz)
            faces.add(QuadFace3D(t0, t1, t2, t3, SakthanWallCoping, null, 0f, 0f, 0f, 1f, 0f))

            val sb0 = Vec3(x0 - cx, capBotY, z0 - cz)
            val sb1 = Vec3(x1 - cx, capBotY, z1 - cz)
            faces.add(QuadFace3D(sb0, sb1, t1, t0, SakthanFasciaShadow, null, 0f, 0f, -nx, 0f, -nz))

            val sb2 = Vec3(x1 + cx, capBotY, z1 + cz)
            val sb3 = Vec3(x0 + cx, capBotY, z0 + cz)
            faces.add(QuadFace3D(sb2, sb3, t3, t2, SakthanFasciaShadow, null, 0f, 0f, nx, 0f, nz))
        }
    }

    fun addYellowBlackKerbPolyline(
        pts: List<Pair<Float, Float>>,
        kerbWidth: Float = 0.34f,
        kerbHeight: Float = 0.20f,
        blockLen: Float = 0.72f
    ) {
        val hw = kerbWidth * 0.5f
        val y0 = 0.06f
        val y1 = kerbHeight
        var globalIdx = 0
        for (i in 0 until pts.size - 1) {
            val (x0, z0) = pts[i]
            val (x1, z1) = pts[i + 1]
            val dx = x1 - x0
            val dz = z1 - z0
            val len = hypot(dx, dz)
            if (len < 0.05f) continue
            val tx = dx / len
            val tz = dz / len
            val nx = -tz * hw
            val nz = tx * hw

            val steps = max(1, (len / blockLen).toInt())
            val segLen = len / steps
            for (s in 0 until steps) {
                val col = if ((globalIdx++) % 2 == 0) SakthanKerbYellow else SakthanKerbBlack
                val d0 = s * segLen
                val d1 = (s + 1) * segLen
                val sx0 = x0 + tx * d0; val sz0 = z0 + tz * d0
                val sx1 = x0 + tx * d1; val sz1 = z0 + tz * d1

                faces.add(
                    QuadFace3D(
                        Vec3(sx0 - nx, y1, sz0 - nz),
                        Vec3(sx1 - nx, y1, sz1 - nz),
                        Vec3(sx1 + nx, y1, sz1 + nz),
                        Vec3(sx0 + nx, y1, sz0 + nz),
                        col, null, -0.2f, 0f, 0f, 1f, 0f
                    )
                )
                faces.add(
                    QuadFace3D(
                        Vec3(sx0 - nx, y0, sz0 - nz),
                        Vec3(sx1 - nx, y0, sz1 - nz),
                        Vec3(sx1 - nx, y1, sz1 - nz),
                        Vec3(sx0 - nx, y1, sz0 - nz),
                        col, null, -0.2f, 0f, -tz, 0f, tx
                    )
                )
                faces.add(
                    QuadFace3D(
                        Vec3(sx1 + nx, y0, sz1 + nz),
                        Vec3(sx0 + nx, y0, sz0 + nz),
                        Vec3(sx0 + nx, y1, sz0 + nz),
                        Vec3(sx1 + nx, y1, sz1 + nz),
                        col, null, -0.2f, 0f, tz, 0f, -tx
                    )
                )
            }
        }
    }

    // =========================================================================
    // 1. SOUTH BOARDING WING (X = -25.0m .. +19.5m, Z = -207.2m .. -195.2m)
    //    Ends cleanly at X = +19.5m to open the Back EXIT Portal (X = 20.0..33.0m)
    // =========================================================================
    addBox(
        -25.0f, 0.06f, -207.2f,
        19.5f, 0.32f, -195.2f,
        topCol = SakthanPlatformTile,
        sideCol = SakthanFasciaShadow,
        frontBackCol = SakthanFasciaShadow
    )
    // Back plaster wall + West side end wall + East side return wall (next to Back EXIT)
    addBox(-25.0f, 0.32f, -207.2f, 19.5f, 4.45f, -206.75f, SakthanWallCream, SakthanFasciaShadow, SakthanWallCream)
    addBox(-25.0f, 0.32f, -207.2f, -24.55f, 4.45f, -196.2f, SakthanWallCream, SakthanWallCream, SakthanFasciaShadow)
    addBox(19.05f, 0.32f, -207.2f, 19.5f, 4.45f, -196.2f, SakthanWallCream, SakthanWallCream, SakthanFasciaShadow)
    addBox(-24.55f, 0.32f, -206.74f, 19.05f, 1.05f, -206.62f, SakthanFasciaShadow)

    // Front columns along South Boarding Wing (Z = -196.35m)
    for (cx in floatArrayOf(-24.2f, -15.4f, -6.6f, 2.2f, 11.0f, 19.1f)) {
        addBox(cx - 0.28f, 0.32f, -196.65f, cx + 0.28f, 4.45f, -196.10f, SakthanColumnGrey)
    }

    // Stepped concrete roof fascia & solid roof deck over Boarding Wing
    addBox(
        -25.5f, 4.45f, -207.5f,
        19.9f, 5.18f, -195.0f,
        topCol = SakthanFasciaLight,
        sideCol = SakthanFasciaLight,
        frontBackCol = SakthanFasciaLight,
        bottomCol = SakthanPlatformSoffit
    )
    addBox(-25.7f, 5.15f, -207.7f, 20.1f, 5.42f, -194.8f, SakthanFasciaLight, SakthanFasciaShadow, SakthanFasciaLight)
    // Solid dark-charcoal roof backing slab (ensures 100% opaque solid roof from all angles)
    addBox(-25.3f, 5.42f, -207.3f, 19.7f, 5.82f, -195.2f, SakthanRoofDeckDark, SakthanRoofCharcoal, SakthanRoofCharcoal)

    // Corrugated metal roof sheet on top of South Boarding Wing (ny < 0 for top-facing dot > 0)
    val bwRoofSpec = createCorrugatedMetalRoofSpec("SAKTHAN_BW_CORRUGATED_ROOF_V2", 1024, 256, ribCount = 72)
    faces.add(
        QuadFace3D(
            Vec3(19.6f, 5.84f, -195.3f),
            Vec3(-25.2f, 5.84f, -195.3f),
            Vec3(-25.2f, 6.12f, -207.2f),
            Vec3(19.6f, 6.12f, -207.2f),
            baseColor = SakthanRoofCharcoal,
            biasDist = -0.3f,
            signboard = bwRoofSpec
        )
    )

    // Glass-and-Plaster Enquiry / Ticket Booth at East end of Boarding Wing (X = +13.8..+18.6m, Z = -206.2..-202.0m)
    addBox(13.8f, 0.32f, -206.2f, 18.6f, 3.10f, -202.0f, SakthanWallCream, SakthanColumnGrey, SakthanWallCream)
    addBox(13.6f, 3.10f, -206.3f, 18.8f, 3.35f, -201.8f, SakthanRoofCharcoal)
    addBox(14.5f, 1.35f, -202.0f, 16.9f, 2.55f, -201.94f, SakthanGlassTint)
    addBox(17.2f, 0.32f, -202.0f, 18.2f, 2.50f, -201.94f, SakthanWoodTeak)

    // Ticket / ATM Kiosk at West concourse end (X = -23.4..-21.7m, Z = -203.2..-201.5m)
    addBox(-23.4f, 0.32f, -203.2f, -21.7f, 2.25f, -201.5f, SakthanFasciaLight, SakthanSteelDark, SakthanColumnGrey)
    addBox(-23.0f, 1.15f, -201.5f, -22.1f, 1.85f, -201.44f, Color(0xFF38BDF8))

    // 4 Pairs of Passenger Waiting Benches under Boarding Wing
    for (bx in floatArrayOf(-19.5f, -11.0f, -2.5f, 6.0f)) {
        for (bz in floatArrayOf(-204.2f, -200.4f)) {
            addBox(bx - 1.9f, 0.32f, bz - 0.32f, bx + 1.9f, 0.56f, bz + 0.32f, SakthanSteelDark)
            addBox(bx - 1.95f, 0.56f, bz - 0.34f, bx + 1.95f, 0.70f, bz + 0.34f, SakthanWoodTeak)
            addBox(bx - 1.95f, 0.70f, bz - 0.34f, bx + 1.95f, 1.12f, bz - 0.22f, SakthanWoodTeak)
        }
    }

    // =========================================================================
    // 1B. SHELTERED BUS-BAY CANOPY ROOF OVER BAYS 1..8
    //     X = -23.0m .. +18.6m, Z = -195.2m .. -170.5m, Clear Soffit = 5.45m
    //     Pillars strictly mounted on divider islands (-22.25, -12.25, -2.25, +7.75, +17.75)
    // =========================================================================
    for (px in floatArrayOf(-22.25f, -12.25f, -2.25f, 7.75f, 17.75f)) {
        for (pz in floatArrayOf(-188.0f, -173.2f)) {
            addBox(
                px - 0.14f, 0.18f, pz - 0.14f,
                px + 0.14f, 5.45f, pz + 0.14f,
                topCol = SakthanColumnGrey,
                sideCol = SakthanColumnGrey,
                frontBackCol = SakthanFasciaShadow
            )
            // Steel collar base on divider island
            addBox(px - 0.20f, 0.18f, pz - 0.20f, px + 0.20f, 0.65f, pz + 0.20f, SakthanKerbBlack)
        }
    }
    // Canopy Fascia Frame & Under-Roof Soffit (Y = 5.45..5.92m)
    addBox(
        -23.0f, 5.45f, -195.2f,
        18.6f, 5.88f, -170.5f,
        topCol = SakthanFasciaLight,
        sideCol = SakthanFasciaLight,
        frontBackCol = SakthanFasciaLight,
        bottomCol = SakthanPlatformSoffit
    )
    // Solid Roof Backing Slab Over Bay Canopy (Y = 5.88..6.22m)
    addBox(-22.8f, 5.88f, -195.0f, 18.4f, 6.22f, -170.7f, SakthanRoofDeckDark, SakthanRoofCharcoal, SakthanRoofCharcoal)
    // Corrugated Metal Roof Surface Above Bus Bays (ny < 0)
    val bayCanopyRoofSpec = createCorrugatedMetalRoofSpec("SAKTHAN_BAY_CANOPY_ROOF_V1", 1024, 512, ribCount = 68)
    faces.add(
        QuadFace3D(
            Vec3(18.5f, 6.24f, -170.6f),
            Vec3(-22.9f, 6.24f, -170.6f),
            Vec3(-22.9f, 6.38f, -195.1f),
            Vec3(18.5f, 6.38f, -195.1f),
            baseColor = SakthanRoofCharcoal,
            biasDist = -0.3f,
            signboard = bayCanopyRoofSpec
        )
    )

    // 3D Raised White/Silver Rooftop Lettering: "SAKTHAN BUS TERMINAL, THRISSUR"
    // Mounted along the front fascia edge of the Bay Canopy Roof (Z = -170.40m, facing North +Z)
    val mainEngRoofTextSpec = createRooftop3DTextSpec(
        cacheKey = "SAKTHAN_ROOF_3D_ENG_TEXT_V2",
        text = "SAKTHAN BUS TERMINAL, THRISSUR",
        bmpWidth = 1400,
        bmpHeight = 160,
        faceColorInt = 0xFFF8FAFC.toInt(),
        extrudeColorInt = 0xFF334155.toInt(),
        textSizePx = 74f
    )
    faces.add(
        QuadFace3D(
            Vec3(17.5f, 6.22f, -170.40f),
            Vec3(-21.5f, 6.22f, -170.40f),
            Vec3(-21.5f, 7.82f, -170.40f),
            Vec3(17.5f, 7.82f, -170.40f),
            baseColor = Color.White,
            biasDist = -1.6f,
            signboard = mainEngRoofTextSpec
        )
    )


    // =========================================================================
    // 2. EAST COMMERCIAL & OFFICE WING (X = +33.5m .. +40.5m, Z = -207.2m .. -143.5m)
    //    Starts cleanly at X = +33.5m right beside Back EXIT Portal (X = 20.0..33.0m)
    // =========================================================================
    addBox(
        32.2f, 0.06f, -207.2f,
        40.5f, 0.32f, -143.5f,
        topCol = SakthanPlatformTile,
        sideCol = SakthanFasciaShadow,
        frontBackCol = SakthanFasciaShadow
    )
    addBox(40.05f, 0.32f, -207.2f, 40.5f, 4.45f, -143.5f, SakthanWallCream)
    addBox(34.4f, 0.32f, -207.2f, 40.05f, 4.45f, -143.5f, SakthanWallCream, SakthanColumnGrey, SakthanWallCream)
    addBox(33.2f, 0.32f, -143.9f, 40.5f, 4.45f, -143.5f, SakthanWallCream)
    addBox(33.2f, 0.32f, -207.2f, 40.5f, 4.45f, -206.8f, SakthanWallCream)

    // Front veranda columns along Commercial Wing (X = 33.4m)
    for (cz in floatArrayOf(-205.5f, -195.5f, -185.5f, -175.5f, -165.5f, -155.5f, -144.2f)) {
        addBox(33.14f, 0.32f, cz - 0.26f, 33.68f, 4.45f, cz + 0.26f, SakthanColumnGrey)
    }

    // Matching stepped roof fascia & solid roof deck over East Commercial Wing
    addBox(
        32.5f, 4.45f, -207.5f,
        40.8f, 5.18f, -143.2f,
        topCol = SakthanFasciaLight,
        sideCol = SakthanFasciaLight,
        frontBackCol = SakthanFasciaLight,
        bottomCol = SakthanPlatformSoffit
    )
    addBox(32.3f, 5.15f, -207.7f, 41.0f, 5.42f, -143.0f, SakthanFasciaLight, SakthanFasciaShadow, SakthanFasciaLight)
    addBox(32.6f, 5.42f, -207.3f, 40.6f, 5.82f, -143.4f, SakthanRoofDeckDark, SakthanRoofCharcoal, SakthanRoofCharcoal)

    // Corrugated sloped metal roof over Commercial Wing (ny < 0)
    val cwRoofSpec = createCorrugatedMetalRoofSpec("SAKTHAN_CW_CORRUGATED_ROOF_V2", 256, 1024, verticalRibs = false, ribCount = 72)
    faces.add(
        QuadFace3D(
            Vec3(32.7f, 5.84f, -143.5f),
            Vec3(32.7f, 5.84f, -207.2f),
            Vec3(40.5f, 6.12f, -207.2f),
            Vec3(40.5f, 6.12f, -143.5f),
            baseColor = SakthanRoofCharcoal,
            biasDist = -0.3f,
            signboard = cwRoofSpec
        )
    )


    // 4 Textured Retail Shopfronts along X = 34.34m (Z = -205.5 .. -166.5m) — English Only
    // v0 = zMax (sz1), v1 = zMin (sz0) so e1 x e2 points +X (dot > 0 from West courtyard!)
    val shopConfigs = arrayOf(
        Triple("SREE MURUGAN TEA & BAKERY", "TEA • SNACKS • BAKERY", 0xFFB91C1C.toInt()),
        Triple("THRISSUR PARCEL & TRAVELS", "COURIER • BOOKING • CARGO", 0xFFB45309.toInt()),
        Triple("KERALA BOOK STALL & NEWS", "NEWSPAPERS • STATIONERY", 0xFF1D4ED8.toInt()),
        Triple("COOL BAR & JUICE CORNER", "FRESH JUICES • ICE CREAM", 0xFF0F766E.toInt())
    )
    for (sIdx in shopConfigs.indices) {
        val sz0 = -205.5f + sIdx * 9.8f
        val sz1 = sz0 + 9.0f
        val (title, engSub, boardCol) = shopConfigs[sIdx]
        val shopSpec = createShopfrontTextureSpec(
            cacheKey = "SAKTHAN_SHOP_STALL_V3_$sIdx",
            title = title,
            subtitle = engSub,
            bannerColorInt = boardCol
        )
        faces.add(
            QuadFace3D(
                Vec3(34.34f, 0.32f, sz1),
                Vec3(34.34f, 0.32f, sz0),
                Vec3(34.34f, 3.85f, sz0),
                Vec3(34.34f, 3.85f, sz1),
                baseColor = SakthanColumnGrey,
                biasDist = -0.6f,
                signboard = shopSpec
            )
        )
    }

    // Station Master Office Room at North End of Commercial Wing (Z = -164.5 .. -143.8m)
    addBox(33.4f, 0.32f, -163.5f, 34.4f, 3.90f, -143.9f, SakthanWallCream)
    addBox(33.34f, 0.32f, -158.0f, 33.40f, 2.55f, -156.4f, SakthanWoodTeak)
    addBox(33.34f, 1.25f, -153.5f, 33.40f, 2.55f, -148.5f, SakthanGlassTint)
    addBox(35.5f, 1.35f, -143.52f, 38.6f, 2.55f, -143.46f, SakthanGlassTint)

    // =========================================================================
    // 3. 8 NUMBERED BUS BAYS (1..8) WITH STONE-CONCRETE & YELLOW-BLACK KERB ISLANDS
    //    Bay centers: bx = -19.75 + bIdx * 5.0 -> X = -22.25 .. +17.75m
    //    Z = -190.5m .. -170.5m (4.32m clear bus lane per bay)
    // =========================================================================
    val bayZ0 = -190.5f
    val bayZ1 = -170.5f

    for (pIdx in 0..8) {
        val ix = -22.25f + pIdx * 5.0f
        val hw = 0.34f
        // Architectural stone/concrete central island bed inside U-shaped yellow-black kerb
        addBox(
            ix - hw + 0.10f, 0.06f, bayZ0 + 0.25f,
            ix + hw - 0.10f, 0.18f, bayZ1 - 0.25f,
            topCol = SakthanBayIslandStone,
            sideCol = SakthanKerbBlack,
            frontBackCol = SakthanKerbYellow
        )
        val islandPts = listOf(
            Pair(ix - hw + 0.08f, bayZ1 - 0.25f),
            Pair(ix - hw + 0.08f, bayZ0 + 0.25f),
            Pair(ix + hw - 0.08f, bayZ0 + 0.25f),
            Pair(ix + hw - 0.08f, bayZ1 - 0.25f)
        )
        addYellowBlackKerbPolyline(islandPts, kerbWidth = 0.20f, kerbHeight = 0.21f, blockLen = 0.74f)
    }

    // Crisp White Painted Bay Floor Numbers ("1" .. "8") Facing North Courtyard Looking South
    // ny < 0 so dot = dy * ny > 0 (isBack = false), v0 = +X (left), v1 = -X (right)
    for (bIdx in 0 until 8) {
        val bx = -19.75f + bIdx * 5.0f
        val numSpec = createBayFloorNumberSpec(
            cacheKey = "SAKTHAN_BAY_FLOOR_NUM_V2_${bIdx + 1}",
            bayNumberText = "${bIdx + 1}"
        )
        faces.add(
            QuadFace3D(
                Vec3(bx + 1.05f, 0.068f, -170.7f),
                Vec3(bx - 1.05f, 0.068f, -170.7f),
                Vec3(bx - 1.05f, 0.068f, -173.4f),
                Vec3(bx + 1.05f, 0.068f, -173.4f),
                baseColor = Color.White,
                biasDist = -0.9f,
                signboard = numSpec
            )
        )
    }

    // =========================================================================
    // 4. YELLOW-AND-BLACK INNER PERIMETER KERBSTONES
    // =========================================================================
    // Boarding Wing concourse edge + East Return
    addYellowBlackKerbPolyline(
        listOf(
            Pair(-25.0f, -195.2f),
            Pair(19.5f, -195.2f),
            Pair(19.5f, -206.8f)
        ),
        kerbWidth = 0.34f,
        kerbHeight = 0.28f,
        blockLen = 0.80f
    )
    // Commercial Wing terrace edge
    addYellowBlackKerbPolyline(
        listOf(
            Pair(32.2f, -206.8f),
            Pair(32.2f, -143.5f),
            Pair(40.2f, -143.5f)
        ),
        kerbWidth = 0.34f,
        kerbHeight = 0.28f,
        blockLen = 0.80f
    )
    // West Driveway wall curb & North-West curved perimeter curb leading to Front ENTRY pillar
    addYellowBlackKerbPolyline(
        listOf(
            Pair(-30.5f, -206.8f),
            Pair(-30.5f, -140.5f),
            Pair(-17.5f, -140.5f),
            Pair(-13.8f, -142.2f),
            Pair(-9.5f, -143.6f),
            Pair(-4.8f, -142.4f),
            Pair(-1.6f, -140.5f),
            Pair(0.1f, -140.5f)
        ),
        kerbWidth = 0.32f,
        kerbHeight = 0.21f,
        blockLen = 0.78f
    )
    // Closed North-East perimeter inner kerb (from ENTRY East pillar to East wall)
    addYellowBlackKerbPolyline(
        listOf(
            Pair(14.3f, -140.5f),
            Pair(40.0f, -140.5f),
            Pair(40.0f, -143.5f)
        ),
        kerbWidth = 0.32f,
        kerbHeight = 0.21f,
        blockLen = 0.78f
    )

    // =========================================================================
    // 5. PERIMETER COMPOUND WALL (FRONT ENTRY ONLY, BACK EXIT OPEN AT X = 20..33m)
    // =========================================================================
    // West Wall + South Wall behind Boarding Wing (up to Back EXIT West pillar X = 19.6)
    addWallPolyline(
        listOf(
            Pair(-31.0f, -140.0f),
            Pair(-31.0f, -207.5f),
            Pair(19.6f, -207.5f)
        ),
        wallMinY = 0.06f,
        wallMaxY = 2.25f,
        halfThick = 0.22f
    )
    // South-East & East outer wall (from Back EXIT East pillar X = 33.4 -> SE corner -> NE corner)
    addWallPolyline(
        listOf(
            Pair(33.4f, -207.5f),
            Pair(40.5f, -207.5f),
            Pair(40.5f, -140.0f)
        ),
        wallMinY = 0.06f,
        wallMaxY = 2.25f,
        halfThick = 0.22f
    )
    // North-West Wall with Smooth Curved Corner Fillet (leaving Front ENTRY X = 0.6..13.8m 100% open!)
    addWallPolyline(
        listOf(
            Pair(-31.0f, -140.0f),
            Pair(-17.5f, -140.0f),
            Pair(-13.8f, -141.7f),
            Pair(-9.5f, -143.1f),
            Pair(-4.8f, -141.9f),
            Pair(-1.6f, -140.0f),
            Pair(0.0f, -140.0f)
        ),
        wallMinY = 0.06f,
        wallMaxY = 2.25f,
        halfThick = 0.22f
    )
    // Closed North-East Perimeter Wall (old North exit removed — Front ENTRY ONLY!)
    addWallPolyline(
        listOf(
            Pair(14.3f, -140.0f),
            Pair(40.5f, -140.0f)
        ),
        wallMinY = 0.06f,
        wallMaxY = 2.25f,
        halfThick = 0.22f
    )

    // =========================================================================
    // 6. FRONT ENTRY PORTAL (Z = -140.0m, X = 0.6..13.8m) &
    //    BACK EXIT PORTAL (Z = -207.5m, X = 20.0..33.0m)
    //    Pedestals & rest forks static; boom arms rendered dynamically in OpenGL!
    // =========================================================================
    val frontGateZ = -140.0f
    val backGateZ = -207.5f

    // --- FRONT ENTRY PORTAL (X = 0.6..13.8m, centered at X = 7.0m) ---
    addBox(-0.15f, 0.06f, frontGateZ - 0.45f, 0.65f, 6.15f, frontGateZ + 0.45f, SakthanWallCream, SakthanColumnGrey, SakthanWallCream)
    addBox(13.75f, 0.06f, frontGateZ - 0.45f, 14.55f, 6.15f, frontGateZ + 0.45f, SakthanWallCream, SakthanColumnGrey, SakthanWallCream)
    addBox(
        -0.25f, 5.55f, frontGateZ - 0.48f,
        14.65f, 6.25f, frontGateZ + 0.48f,
        topCol = SakthanFasciaLight,
        sideCol = SakthanFasciaShadow,
        frontBackCol = SakthanWallCream,
        bottomCol = SakthanFasciaShadow
    )

    val entryLettersSpec = createRooftop3DTextSpec(
        cacheKey = "SAKTHAN_GATE_3D_ENTRY_V2",
        text = "ENTRY",
        bmpWidth = 512,
        bmpHeight = 160,
        faceColorInt = 0xFF374151.toInt(),
        extrudeColorInt = 0xFF111827.toInt(),
        textSizePx = 112f
    )
    faces.add(
        QuadFace3D(
            Vec3(10.8f, 6.25f, frontGateZ),
            Vec3(3.6f, 6.25f, frontGateZ),
            Vec3(3.6f, 7.60f, frontGateZ),
            Vec3(10.8f, 7.60f, frontGateZ),
            baseColor = SakthanSteelDark,
            biasDist = -1.6f,
            signboard = entryLettersSpec
        )
    )
    // Guard Booth outside Left ENTRY pillar
    addBox(-2.25f, 0.06f, frontGateZ - 1.65f, -0.18f, 2.55f, frontGateZ + 0.15f, SakthanWallCream, SakthanColumnGrey, SakthanWallCream)
    addBox(-2.45f, 2.55f, frontGateZ - 1.85f, 0.02f, 2.82f, frontGateZ + 0.35f, SakthanWallCoping)
    addBox(-1.75f, 1.25f, frontGateZ - 1.67f, -0.65f, 2.05f, frontGateZ - 1.61f, SakthanGlassTint)

    // Front ENTRY Boom Barrier Hinge Pedestal (Z = -139.33f, clear in front of lintel) + Opposite Rest Post
    addBox(0.68f, 0.06f, frontGateZ + 0.52f, 0.96f, 1.05f, frontGateZ + 0.82f, SakthanKerbYellow, SakthanKerbBlack, SakthanKerbYellow)
    addBox(13.44f, 0.06f, frontGateZ + 0.52f, 13.72f, 0.92f, frontGateZ + 0.82f, SakthanKerbBlack, SakthanKerbYellow, SakthanKerbBlack)

    // --- BACK EXIT PORTAL (Z = -207.5m, X = 20.0..33.0m, centered at X = 26.5m) ---
    addBox(19.25f, 0.06f, backGateZ - 0.45f, 20.05f, 6.15f, backGateZ + 0.45f, SakthanWallCream, SakthanColumnGrey, SakthanWallCream)
    addBox(32.95f, 0.06f, backGateZ - 0.45f, 33.75f, 6.15f, backGateZ + 0.45f, SakthanWallCream, SakthanColumnGrey, SakthanWallCream)
    addBox(
        19.15f, 5.55f, backGateZ - 0.48f,
        33.85f, 6.25f, backGateZ + 0.48f,
        topCol = SakthanFasciaLight,
        sideCol = SakthanFasciaShadow,
        frontBackCol = SakthanWallCream,
        bottomCol = SakthanFasciaShadow
    )

    // 3D Dark Metallic "EXIT" Rooftop Letters above Back EXIT Lintel (Y = 6.25..7.60m)
    val exitLettersSpec = createRooftop3DTextSpec(
        cacheKey = "SAKTHAN_GATE_3D_EXIT_V2",
        text = "EXIT",
        bmpWidth = 512,
        bmpHeight = 160,
        faceColorInt = 0xFF374151.toInt(),
        extrudeColorInt = 0xFF111827.toInt(),
        textSizePx = 112f
    )
    faces.add(
        QuadFace3D(
            Vec3(30.0f, 6.25f, backGateZ),
            Vec3(23.0f, 6.25f, backGateZ),
            Vec3(23.0f, 7.60f, backGateZ),
            Vec3(30.0f, 7.60f, backGateZ),
            baseColor = SakthanSteelDark,
            biasDist = -1.6f,
            signboard = exitLettersSpec
        )
    )
    // Back EXIT Boom Barrier Hinge Pedestal (Z = -206.83f, clear inside lintel) + Opposite Rest Post
    addBox(32.64f, 0.06f, backGateZ + 0.52f, 32.92f, 1.05f, backGateZ + 0.82f, SakthanKerbYellow, SakthanKerbBlack, SakthanKerbYellow)
    addBox(20.08f, 0.06f, backGateZ + 0.52f, 20.36f, 0.92f, backGateZ + 0.82f, SakthanKerbBlack, SakthanKerbYellow, SakthanKerbBlack)

    faces
}

private fun appendRotatedBoomBarrierArm(
    pivotX: Float,
    pivotY: Float,
    pivotZ: Float,
    dirX: Float,
    armLength: Float,
    openFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    val angleRad = openFrac.coerceIn(0f, 1f) * Math.toRadians(84.0).toFloat()
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val ux = dirX * cosA
    val uy = sinA
    val vx = -dirX * sinA
    val vy = cosA

    fun addRotatedSegment(s0: Float, s1: Float, halfT: Float, topCol: Color, sideCol: Color) {
        fun pt(s: Float, dv: Float, dz: Float) = Vec3(
            pivotX + ux * s + vx * dv,
            pivotY + uy * s + vy * dv,
            pivotZ + dz
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

    // Counterweight tail behind hinge
    addRotatedSegment(-0.68f, 0.0f, 0.075f, SakthanSteelDark, SakthanKerbBlack)

    // 14 Alternating Red & White boom stripes spanning full gate opening
    val segCount = 14
    val segLen = armLength / segCount.toFloat()
    for (i in 0 until segCount) {
        val s0 = i * segLen
        val s1 = (i + 1) * segLen
        val isRed = (i % 2 == 0)
        val topCol = if (isRed) SakthanBarrierRed else SakthanBarrierWhite
        val sideCol = if (isRed) Color(0xFF991B1B) else Color(0xFFCBD5E1)
        addRotatedSegment(s0, s1, 0.055f, topCol, sideCol)
    }
}

internal fun collectSakthanDynamicBarrierFaces(
    entryOpenFrac: Float,
    exitOpenFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    // Front ENTRY boom arm hinged at Left ENTRY pedestal (0.82, 0.92, -139.33), extending +X across 12.6m gate
    appendRotatedBoomBarrierArm(
        pivotX = 0.82f,
        pivotY = 0.92f,
        pivotZ = -139.33f,
        dirX = 1f,
        armLength = 12.60f,
        openFrac = entryOpenFrac,
        outFaces = outFaces
    )
    // Back EXIT boom arm hinged at Right Back EXIT pedestal (32.78, 0.92, -206.83), extending -X across 12.4m gate
    appendRotatedBoomBarrierArm(
        pivotX = 32.78f,
        pivotY = 0.92f,
        pivotZ = -206.83f,
        dirX = -1f,
        armLength = 12.40f,
        openFrac = exitOpenFrac,
        outFaces = outFaces
    )
}

internal fun collectSakthanModernTerminalSceneFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dxT = camEye.x - 6f
    val dzT = camEye.z - (-174f)
    if (dxT * dxT + dzT * dzT > 240f * 240f) return
    outFaces.addAll(cachedSakthanModernTerminalFaces)
}

internal fun collectAllSakthanModernTerminalStaticFaces(
    outFaces: MutableList<QuadFace3D>
) {
    outFaces.addAll(cachedSakthanModernTerminalFaces)
}

// =========================================================================
// HIGH-RES ALPHA-CUTOUT TEXTURES FOR ROOFTOP 3D TEXT, ROOFS, SHOPS & BAYS
// =========================================================================

private fun createRooftop3DTextSpec(
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

        val isMalayalam = PremiumFonts.containsMalayalam(text)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = textSizePx
            typeface = PremiumFonts.displayTypefaceFor(text, bold = true)
            letterSpacing = if (isMalayalam) 0.02f else 0.07f
        }
        val safeW = bmpWidth - 28f
        val measured = p.measureText(text)
        if (measured > safeW && measured > 1f) {
            p.textSize = (textSizePx * (safeW / measured)).coerceAtLeast(18f)
        }

        val cx = bmpWidth * 0.5f
        val baseY = bmpHeight * 0.78f

        p.color = extrudeColorInt
        for (d in 5 downTo 1) {
            c.drawText(text, cx + d * 1.4f, baseY + d * 1.4f, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.5f
        p.color = extrudeColorInt
        c.drawText(text, cx, baseY, p)

        p.style = Paint.Style.FILL
        p.color = faceColorInt
        c.drawText(text, cx, baseY, p)
        bmp
    }
)

private fun createCorrugatedMetalRoofSpec(
    cacheKey: String,
    bmpWidth: Int,
    bmpHeight: Int,
    verticalRibs: Boolean = true,
    ribCount: Int = 64
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = bmpWidth,
    bmpHeight = bmpHeight,
    isDoubleSided = true,
    renderBitmap = {
        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF393E46.toInt())
        val p = Paint()
        val span = if (verticalRibs) bmpWidth.toFloat() else bmpHeight.toFloat()
        val step = span / ribCount.coerceAtLeast(1)
        for (i in 0 until ribCount) {
            val pos = i * step
            p.color = 0xFF4B525C.toInt()
            if (verticalRibs) {
                c.drawRect(pos, 0f, pos + step * 0.42f, bmpHeight.toFloat(), p)
                p.color = 0xFF262A30.toInt()
                c.drawRect(pos + step * 0.65f, 0f, pos + step, bmpHeight.toFloat(), p)
            } else {
                c.drawRect(0f, pos, bmpWidth.toFloat(), pos + step * 0.42f, p)
                p.color = 0xFF262A30.toInt()
                c.drawRect(0f, pos + step * 0.65f, bmpWidth.toFloat(), pos + step, p)
            }
        }
        p.color = 0x55111317
        p.strokeWidth = 2f
        if (verticalRibs) {
            c.drawLine(0f, bmpHeight * 0.5f, bmpWidth.toFloat(), bmpHeight * 0.5f, p)
        } else {
            c.drawLine(bmpWidth * 0.5f, 0f, bmpWidth * 0.5f, bmpHeight.toFloat(), p)
        }
        bmp
    }
)

private fun createShopfrontTextureSpec(
    cacheKey: String,
    title: String,
    subtitle: String,
    bannerColorInt: Int
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = 512,
    bmpHeight = 256,
    isDoubleSided = false,
    renderBitmap = {
        val bmp = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFD5D1C8.toInt())

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = bannerColorInt
        c.drawRect(14f, 12f, 498f, 78f, p)
        p.color = 0xFFFACC15.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        c.drawRect(14f, 12f, 498f, 78f, p)

        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = PremiumFonts.displayTypefaceFor(title, bold = true)
        p.letterSpacing = 0.05f
        p.color = android.graphics.Color.WHITE
        p.textSize = 24f
        val titleW = p.measureText(title)
        if (titleW > 456f) p.textSize = 24f * (456f / titleW)
        c.drawText(title, 256f, 42f, p)

        p.typeface = PremiumFonts.uiTypefaceFor(subtitle, bold = true)
        p.letterSpacing = 0.03f
        p.textSize = 17.5f
        val subW = p.measureText(subtitle)
        if (subW > 456f) p.textSize = 17.5f * (456f / subW)
        p.color = 0xFFFEF08A.toInt()
        c.drawText(subtitle, 256f, 67f, p)

        p.color = 0xFF8C929B.toInt()
        c.drawRect(22f, 88f, 490f, 252f, p)
        for (y in 96..248 step 12) {
            p.color = 0xFF6B7280.toInt()
            c.drawRect(22f, y.toFloat(), 490f, (y + 3).toFloat(), p)
        }
        p.color = 0xFF374151.toInt()
        c.drawRect(22f, 240f, 490f, 252f, p)
        bmp
    }
)

private fun createBayFloorNumberSpec(
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
            textSize = 108f
            typeface = PremiumFonts.displayTypefaceFor(bayNumberText, bold = true)
            letterSpacing = 0.04f
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = 0x88181D24.toInt()
        c.drawText(bayNumberText, 80f, 120f, p)

        p.style = Paint.Style.FILL
        p.color = 0xFFF1F5F9.toInt()
        c.drawText(bayNumberText, 80f, 120f, p)
        bmp
    }
)
