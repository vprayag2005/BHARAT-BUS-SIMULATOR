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
import kotlin.math.max
import kotlin.math.sin

/**
 * High-detail OpenGL ES 3.0 3D Architectural Model of Shoranur Bus Terminal (SH 22 Terminus).
 *
 * Completely Distinct Architectural Silhouette (unique from Sakthan's L-shape & Vadakke's U-horseshoe):
 * - Modern Sawtooth Twin-Gable East Concourse Pavilion crowned with a central Sky-Lit Control Tower,
 *   paired with an undulating 4-Bay Arched Wave-Truss Steel Shelter over 8 East-West Bus Bays (1..8).
 * - Distinctive Coastal Ivory Masonry + Deep Malabar Slate-Teal Roof & Arch Fascia palette.
 * - Raised 3D Rooftop Lettering ("SHORANUR BUS TERMINAL") positioned strictly on top above the
 *   bus parking wave canopy roof facing SH 22 (West).
 * - Twin West Gate Portals (X = 126.0m) connecting cleanly to SH 22:
 *   - South ENTRY Gate (Z = 3038.0 .. 3050.0m) with 3D "ENTRY" text & animated boom barrier.
 *   - North EXIT Gate  (Z = 3100.0 .. 3112.0m) with 3D "EXIT" text & animated boom barrier.
 * - Placed strictly at X = 126.0 .. 196.0m, Z = 3015.0 .. 3135.0m (0% overlap with SH 22 or railway).
 */

private val ShorGroundEarth = Color(0xFF6B655C)
private val ShorBasementStone = Color(0xFF9B9488)
private val ShorBayIslandStone = Color(0xFFAFA99E)
private val ShorApronAsphalt = Color(0xFF434952)
private val ShorApronApproach = Color(0xFF373C44)
private val ShorJointLine = Color(0x5523272D)
private val ShorWhiteMark = Color(0xFFF1F5F9)

// Distinct Shoranur Terminal Palette (Coastal Ivory + Deep Malabar Slate-Teal + Brushed Steel)
private val ShorWallIvory = Color(0xFFEAE4D8)
private val ShorWallTrim = Color(0xFFB5AEA0)
private val ShorColumnLight = Color(0xFFD2CCC0)
private val ShorTealRoof = Color(0xFF1F5364)
private val ShorTealDark = Color(0xFF153B48)
private val ShorTealHighlight = Color(0xFF2D6F84)
private val ShorSteelArch = Color(0xFF394452)
private val ShorSoffitGrey = Color(0xFF64707D)
private val ShorPlatformTile = Color(0xFFD4CEC3)
private val ShorKerbYellow = Color(0xFFEAB308)
private val ShorKerbBlack = Color(0xFF1E2228)
private val ShorWoodTeak = Color(0xFF7B4D2B)
private val ShorGlassTint = Color(0xFF1A374D)
private val ShorBarrierRed = Color(0xFFDC2626)
private val ShorBarrierWhite = Color(0xFFF8FAFC)

internal const val SHORANUR_TERM_BAY_CENTER_X = 166.5f
internal const val SHORANUR_TERM_BAY_1_CENTER_Z = 3051.5f
internal const val SHORANUR_TERM_BAY_PITCH_Z = 6.20f

internal fun DrawScope.drawShoranurTerminalYard(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - 160f, camEye.z - 3075f) > 420f) return

    val yGroundOuter = 0.050f
    val yBasementPlinth = 0.052f
    val yApron = 0.058f

    // 1. Warm Stone-Earth Surround & Architectural Concrete Plinth
    if (rig.buildClippedQuadPath(
            path,
            Vec3(119.5f, yGroundOuter, 3007.0f),
            Vec3(204.0f, yGroundOuter, 3007.0f),
            Vec3(204.0f, yGroundOuter, 3143.0f),
            Vec3(119.5f, yGroundOuter, 3143.0f)
        )
    ) {
        drawPath(path, ShorGroundEarth)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(122.5f, yBasementPlinth, 3011.5f),
            Vec3(199.5f, yBasementPlinth, 3011.5f),
            Vec3(199.5f, yBasementPlinth, 3138.5f),
            Vec3(122.5f, yBasementPlinth, 3138.5f)
        )
    ) {
        drawPath(path, ShorBasementStone)
    }

    // 2. Smooth Interior Asphalt Courtyard (X = 126.0 .. 195.8, Z = 3015.0 .. 3135.0)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(126.0f, yApron, 3015.0f),
            Vec3(195.8f, yApron, 3015.0f),
            Vec3(195.8f, yApron, 3135.0f),
            Vec3(126.0f, yApron, 3135.0f)
        )
    ) {
        drawPath(path, ShorApronAsphalt)
    }

    // 3. West Portal Threshold Aprons at South ENTRY (Z=3038..3050) & North EXIT (Z=3100..3112)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(124.2f, 0.0588f, 3038.0f),
            Vec3(128.5f, 0.0588f, 3038.0f),
            Vec3(128.5f, 0.0588f, 3050.0f),
            Vec3(124.2f, 0.0588f, 3050.0f)
        )
    ) {
        drawPath(path, ShorApronApproach)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(124.2f, 0.0588f, 3100.0f),
            Vec3(128.5f, 0.0588f, 3100.0f),
            Vec3(128.5f, 0.0588f, 3112.0f),
            Vec3(124.2f, 0.0588f, 3112.0f)
        )
    ) {
        drawPath(path, ShorApronApproach)
    }

    // 4. Courtyard Expansion Joint Grid
    if (hypot(camEye.x - 160f, camEye.z - 3075f) < 185f) {
        var gx = 132.0f
        while (gx <= 178.0f) {
            rig.projectSegmentClipped(Vec3(gx, 0.0592f, 3017.0f), Vec3(gx, 0.0592f, 3133.0f))?.let { seg ->
                drawLine(ShorJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gx += 6.5f
        }
        var gz = 3022.0f
        while (gz <= 3128.0f) {
            rig.projectSegmentClipped(Vec3(126.8f, 0.0592f, gz), Vec3(179.0f, 0.0592f, gz))?.let { seg ->
                drawLine(ShorJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gz += 7.0f
        }
    }

    // 5. Crisp White Bus Circulation Guide Curve: West South ENTRY -> Courtyard Sweep -> West North EXIT
    val guidePts = listOf(
        Vec3(126.0f, 0.060f, 3044.0f),
        Vec3(136.0f, 0.060f, 3044.0f),
        Vec3(144.0f, 0.060f, 3048.5f),
        Vec3(147.0f, 0.060f, 3058.0f),
        Vec3(147.0f, 0.060f, 3092.0f),
        Vec3(144.0f, 0.060f, 3101.5f),
        Vec3(136.0f, 0.060f, 3106.0f),
        Vec3(126.0f, 0.060f, 3106.0f)
    )
    for (i in 0 until guidePts.size - 1) {
        rig.projectSegmentClipped(guidePts[i], guidePts[i + 1])?.let { seg ->
            drawLine(ShorWhiteMark, seg.first, seg.second, strokeWidth = 1.8f)
        }
    }

    // 6. Painted Bay Boundary Lines & Tire Wear Tracks for 8 Parallel Bus Bays (X = 156.5 .. 176.5)
    val bayX0 = 156.5f
    val bayX1 = 176.5f
    val bayHalfW = 2.05f
    val tireDark = Color(0x441B1F26)
    for (bIdx in 0 until 8) {
        val bz = SHORANUR_TERM_BAY_1_CENTER_Z + bIdx * SHORANUR_TERM_BAY_PITCH_Z
        for (tz in floatArrayOf(-0.92f, 0.92f)) {
            rig.projectSegmentClipped(Vec3(bayX0 + 1.4f, 0.0595f, bz + tz), Vec3(bayX1 - 1.2f, 0.0595f, bz + tz))?.let { seg ->
                drawLine(tireDark, seg.first, seg.second, strokeWidth = 2.6f)
            }
        }
        rig.projectSegmentClipped(Vec3(bayX0, 0.0605f, bz - bayHalfW), Vec3(bayX1, 0.0605f, bz - bayHalfW))?.let { seg ->
            drawLine(ShorWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(Vec3(bayX0, 0.0605f, bz + bayHalfW), Vec3(bayX1, 0.0605f, bz + bayHalfW))?.let { seg ->
            drawLine(ShorWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(Vec3(bayX0, 0.0605f, bz - bayHalfW), Vec3(bayX0, 0.0605f, bz + bayHalfW))?.let { seg ->
            drawLine(ShorWhiteMark, seg.first, seg.second, strokeWidth = 1.6f)
        }
    }
}

private val cachedShoranurTerminalFaces: List<QuadFace3D> by lazy {
    val faces = ArrayList<QuadFace3D>(2600)

    fun addBox(
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        topCol: Color, sideCol: Color = topCol, frontBackCol: Color = sideCol,
        bottomCol: Color? = null, bias: Float = 0f
    ) {
        faces.add(QuadFace3D(Vec3(minX, maxY, minZ), Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), topCol, null, bias, 0f, 0f, 1f, 0f))
        if (bottomCol != null) {
            faces.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), bottomCol, null, bias + 0.2f, 0f, 0f, -1f, 0f))
        }
        faces.add(QuadFace3D(Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, maxY, minZ), Vec3(minX, maxY, minZ), frontBackCol, null, bias, 0f, 0f, 0f, -1f))
        faces.add(QuadFace3D(Vec3(maxX, minY, maxZ), Vec3(minX, minY, maxZ), Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), frontBackCol, null, bias, 0f, 0f, 0f, 1f))
        faces.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(minX, minY, minZ), Vec3(minX, maxY, minZ), Vec3(minX, maxY, maxZ), sideCol, null, bias, 0f, -1f, 0f, 0f))
        faces.add(QuadFace3D(Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), sideCol, null, bias, 0f, 1f, 0f, 0f))
    }

    fun addWallPolyline(pts: List<Pair<Float, Float>>, wallMinY: Float = 0.06f, wallMaxY: Float = 2.35f, halfThick: Float = 0.22f) {
        for (i in 0 until pts.size - 1) {
            val (x0, z0) = pts[i]
            val (x1, z1) = pts[i + 1]
            val dx = x1 - x0; val dz = z1 - z0
            val len = hypot(dx, dz)
            if (len < 0.05f) continue
            val nx = -dz / len; val nz = dx / len
            val ox = nx * halfThick; val oz = nz * halfThick
            val cx = nx * (halfThick + 0.05f); val cz = nz * (halfThick + 0.05f)
            val capTopY = wallMaxY + 0.15f
            faces.add(QuadFace3D(Vec3(x0 - ox, wallMinY, z0 - oz), Vec3(x1 - ox, wallMinY, z1 - oz), Vec3(x1 - ox, wallMaxY, z1 - oz), Vec3(x0 - ox, wallMaxY, z0 - oz), ShorWallIvory, null, 0f, 0f, -nx, 0f, -nz))
            faces.add(QuadFace3D(Vec3(x1 + ox, wallMinY, z1 + oz), Vec3(x0 + ox, wallMinY, z0 + oz), Vec3(x0 + ox, wallMaxY, z0 + oz), Vec3(x1 + ox, wallMaxY, z1 + oz), ShorWallIvory, null, 0f, 0f, nx, 0f, nz))
            faces.add(QuadFace3D(Vec3(x0 - cx, capTopY, z0 - cz), Vec3(x1 - cx, capTopY, z1 - cz), Vec3(x1 + cx, capTopY, z1 + cz), Vec3(x0 + cx, capTopY, z0 + cz), ShorTealRoof, null, 0f, 0f, 0f, 1f, 0f))
            faces.add(QuadFace3D(Vec3(x0 - cx, wallMaxY, z0 - cz), Vec3(x1 - cx, wallMaxY, z1 - cz), Vec3(x1 - cx, capTopY, z1 - cz), Vec3(x0 - cx, capTopY, z0 - cz), ShorTealDark, null, 0f, 0f, -nx, 0f, -nz))
            faces.add(QuadFace3D(Vec3(x1 + cx, wallMaxY, z1 + cz), Vec3(x0 + cx, wallMaxY, z0 + cz), Vec3(x0 + cx, capTopY, z0 + cz), Vec3(x1 + cx, capTopY, z1 + cz), ShorTealDark, null, 0f, 0f, nx, 0f, nz))
        }
    }

    fun addYellowBlackKerbPolyline(pts: List<Pair<Float, Float>>, kerbWidth: Float = 0.34f, kerbHeight: Float = 0.21f, blockLen: Float = 0.78f) {
        val hw = kerbWidth * 0.5f
        val y0 = 0.06f
        var gIdx = 0
        for (i in 0 until pts.size - 1) {
            val (x0, z0) = pts[i]
            val (x1, z1) = pts[i + 1]
            val dx = x1 - x0; val dz = z1 - z0
            val len = hypot(dx, dz)
            if (len < 0.05f) continue
            val tx = dx / len; val tz = dz / len
            val nx = -tz * hw; val nz = tx * hw
            val steps = max(1, (len / blockLen).toInt())
            val segLen = len / steps
            for (s in 0 until steps) {
                val col = if ((gIdx++) % 2 == 0) ShorKerbYellow else ShorKerbBlack
                val d0 = s * segLen; val d1 = (s + 1) * segLen
                val sx0 = x0 + tx * d0; val sz0 = z0 + tz * d0
                val sx1 = x0 + tx * d1; val sz1 = z0 + tz * d1
                faces.add(QuadFace3D(Vec3(sx0 - nx, kerbHeight, sz0 - nz), Vec3(sx1 - nx, kerbHeight, sz1 - nz), Vec3(sx1 + nx, kerbHeight, sz1 + nz), Vec3(sx0 + nx, kerbHeight, sz0 + nz), col, null, -0.2f, 0f, 0f, 1f, 0f))
                faces.add(QuadFace3D(Vec3(sx0 - nx, y0, sz0 - nz), Vec3(sx1 - nx, y0, sz1 - nz), Vec3(sx1 - nx, kerbHeight, sz1 - nz), Vec3(sx0 - nx, kerbHeight, sz0 - nz), col, null, -0.2f, 0f, -tz, 0f, tx))
                faces.add(QuadFace3D(Vec3(sx1 + nx, y0, sz1 + nz), Vec3(sx0 + nx, y0, sz0 + nz), Vec3(sx0 + nx, kerbHeight, sz0 + nz), Vec3(sx1 + nx, kerbHeight, sz1 + nz), col, null, -0.2f, 0f, tz, 0f, -tx))
            }
        }
    }

    // =========================================================================
    // 1. EAST SAWTOOTH TWIN-GABLE CONCOURSE PAVILION (X = 179.0 .. 195.8, Z = 3017.0 .. 3133.0)
    // =========================================================================
    addBox(179.0f, 0.06f, 3017.0f, 195.8f, 0.34f, 3133.0f, ShorPlatformTile, ShorWallTrim, ShorWallTrim)
    // Rear East Wall + North/South End Walls
    addBox(194.8f, 0.34f, 3017.0f, 195.8f, 4.95f, 3133.0f, ShorWallIvory, ShorWallTrim, ShorWallIvory)
    addBox(179.2f, 0.34f, 3017.0f, 195.8f, 4.95f, 3017.8f, ShorWallIvory)
    addBox(179.2f, 0.34f, 3132.2f, 195.8f, 4.95f, 3133.0f, ShorWallIvory)

    // Deep Malabar Teal Wainscot Dado Strip along East Wall
    addBox(194.4f, 0.34f, 3018.0f, 194.82f, 1.35f, 3132.0f, ShorTealDark)

    // Veranda Columns along West Concourse Edge (X = 179.8m)
    var colZ = 3020.0f
    while (colZ <= 3130.5f) {
        addBox(179.5f, 0.34f, colZ - 0.30f, 180.1f, 4.95f, colZ + 0.30f, ShorColumnLight, ShorWallTrim, ShorColumnLight)
        addBox(179.42f, 0.34f, colZ - 0.36f, 180.18f, 1.15f, colZ + 0.36f, ShorTealDark)
        colZ += 11.0f
    }

    // Twin-Gable Sawtooth Roof Sections over East Pavilion (distinct jagged-skylight profile!)
    addBox(178.4f, 4.95f, 3016.2f, 196.4f, 5.55f, 3133.8f, ShorWallIvory, ShorWallTrim, ShorWallIvory, ShorSoffitGrey)
    val sawCount = 6
    val sawSpanZ = (3133.2f - 3016.8f) / sawCount
    for (s in 0 until sawCount) {
        val sz0 = 3016.8f + s * sawSpanZ
        val sz1 = sz0 + sawSpanZ - 0.5f
        val zPeak = sz0 + sawSpanZ * 0.25f
        // Raised skylight clerestory vertical glass band facing North (+Z)
        addBox(179.0f, 5.55f, sz0, 195.8f, 7.10f, sz0 + 0.65f, ShorTealDark, ShorGlassTint, ShorGlassTint)
        // Stepped sloped Malabar Slate-Teal roof tiers cascading South
        for (tier in 0 until 4) {
            val t0 = tier / 4f
            val t1 = (tier + 1) / 4f
            val tz0 = zPeak + (sz1 - zPeak) * t0
            val tz1 = zPeak + (sz1 - zPeak) * t1
            val ty = 7.10f - tier * 0.38f
            addBox(178.7f, 5.55f, tz0, 196.1f, ty, tz1, if (tier % 2 == 0) ShorTealRoof else ShorTealHighlight, ShorTealDark, ShorTealRoof)
        }
    }

    // Central Architectural Sky-Lit Control Tower Pavilion (Z = 3067..3083, rising to Y = 11.4m)
    addBox(182.0f, 5.55f, 3067.5f, 193.5f, 9.80f, 3082.5f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(181.6f, 6.40f, 3069.0f, 182.05f, 8.95f, 3081.0f, ShorGlassTint)
    addBox(181.2f, 9.80f, 3066.5f, 194.2f, 10.65f, 3083.5f, ShorTealRoof, ShorTealDark, ShorTealRoof)
    addBox(183.4f, 10.65f, 3069.5f, 192.0f, 11.45f, 3080.5f, ShorTealHighlight, ShorTealDark, ShorTealRoof)

    // Enquiry Office & Passenger Waiting Benches inside East Pavilion
    addBox(187.5f, 0.34f, 3070.0f, 194.4f, 3.60f, 3080.0f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(187.42f, 1.35f, 3071.5f, 187.52f, 2.75f, 3078.5f, ShorGlassTint)
    for (bz in floatArrayOf(3032f, 3046f, 3060f, 3090f, 3104f, 3118f)) {
        addBox(186.5f, 0.34f, bz - 2.4f, 187.3f, 0.72f, bz + 2.4f, ShorWoodTeak)
        addBox(187.1f, 0.72f, bz - 2.4f, 187.35f, 1.15f, bz + 2.4f, ShorSteelArch)
    }

    // North & South Side Service / Commercial Wings (X = 148..179, flanking Bays 1..8)
    addBox(152.0f, 0.06f, 3015.5f, 179.0f, 0.32f, 3028.0f, ShorPlatformTile)
    addBox(153.5f, 0.32f, 3015.8f, 179.0f, 4.30f, 3024.0f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(151.6f, 4.30f, 3015.2f, 179.4f, 5.05f, 3028.4f, ShorTealRoof, ShorTealDark, ShorTealRoof, ShorSoffitGrey)
    addBox(156.0f, 1.30f, 3024.0f, 174.0f, 2.95f, 3024.08f, ShorGlassTint)

    addBox(152.0f, 0.06f, 3122.0f, 179.0f, 0.32f, 3134.5f, ShorPlatformTile)
    addBox(153.5f, 0.32f, 3126.0f, 179.0f, 4.30f, 3134.2f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(151.6f, 4.30f, 3121.6f, 179.4f, 5.05f, 3134.8f, ShorTealRoof, ShorTealDark, ShorTealRoof, ShorSoffitGrey)
    addBox(156.0f, 1.30f, 3125.92f, 174.0f, 2.95f, 3126.0f, ShorGlassTint)

    // =========================================================================
    // 2. 8 BUS BAY ISLANDS + ARCHED WAVE-TRUSS CANOPY OVER BAYS 1..8
    //    X = 156.0 .. 179.0m, Z = 3048.4 .. 3098.0m (Clear Soffit = 5.55m!)
    // =========================================================================
    for (pIdx in 0..8) {
        val iz = (SHORANUR_TERM_BAY_1_CENTER_Z - SHORANUR_TERM_BAY_PITCH_Z * 0.5f) + pIdx * SHORANUR_TERM_BAY_PITCH_Z
        val hw = 0.34f
        addBox(156.8f, 0.06f, iz - hw + 0.08f, 176.2f, 0.20f, iz + hw - 0.08f, ShorBayIslandStone, ShorKerbBlack, ShorKerbYellow)
        addYellowBlackKerbPolyline(
            listOf(
                Pair(156.8f, iz - hw + 0.06f),
                Pair(176.2f, iz - hw + 0.06f),
                Pair(176.2f, iz + hw - 0.06f),
                Pair(156.8f, iz + hw - 0.06f)
            ),
            kerbWidth = 0.18f, kerbHeight = 0.22f, blockLen = 0.76f
        )
        // Twin Steel V-Columns on alternate islands supporting the Arched Wave Canopy
        if (pIdx % 2 == 0) {
            for (px in floatArrayOf(158.8f, 174.2f)) {
                addBox(px - 0.16f, 0.20f, iz - 0.16f, px + 0.16f, 5.55f, iz + 0.16f, ShorSteelArch, ShorColumnLight, ShorSteelArch)
                addBox(px - 0.24f, 0.20f, iz - 0.24f, px + 0.24f, 0.75f, iz + 0.24f, ShorTealDark)
            }
        }
    }

    // Arched Wave-Truss Canopy Roof over all 8 Bus Bays (4 distinct barrel-vaulted teal waves!)
    val canZ0 = 3047.5f
    val canZ1 = 3099.5f
    addBox(156.0f, 5.55f, canZ0, 179.0f, 5.95f, canZ1, ShorWallIvory, ShorTealDark, ShorWallIvory, ShorSoffitGrey)
    val waveCount = 4
    val waveLen = (canZ1 - canZ0) / waveCount
    for (w in 0 until waveCount) {
        val wz0 = canZ0 + w * waveLen
        val segs = 6
        for (s in 0 until segs) {
            val u0 = s.toFloat() / segs
            val u1 = (s + 1).toFloat() / segs
            val midU = (u0 + u1) * 0.5f
            val rise = sin(midU * Math.PI).toFloat() * 0.95f
            val col = if (s % 2 == 0) ShorTealRoof else ShorTealHighlight
            addBox(
                156.2f, 5.95f, wz0 + u0 * waveLen,
                178.8f, 6.22f + rise, wz0 + u1 * waveLen,
                col, ShorTealDark, col
            )
        }
    }

    // Raised 3D Rooftop Lettering: "SHORANUR BUS TERMINAL" ONLY on top above the bus parking canopy!
    // Facing West (-X, toward SH 22): v0 = zMax (North, left), v1 = zMin (South, right) -> nx = +1 -> dot > 0 from West!
    val roofTextSpec = createShoranurRoof3DTextSpec(
        cacheKey = "SHORANUR_TERM_ROOF_3D_TEXT_V1",
        text = "SHORANUR BUS TERMINAL",
        bmpWidth = 1400,
        bmpHeight = 180,
        faceColorInt = 0xFFF8FAFC.toInt(),
        extrudeColorInt = 0xFF153B48.toInt(),
        textSizePx = 88f
    )
    faces.add(
        QuadFace3D(
            Vec3(156.10f, 6.35f, 3094.0f),
            Vec3(156.10f, 6.35f, 3053.0f),
            Vec3(156.10f, 8.55f, 3053.0f),
            Vec3(156.10f, 8.55f, 3094.0f),
            baseColor = Color.White,
            biasDist = -1.6f,
            signboard = roofTextSpec
        )
    )

    // Painted Bay Floor Numbers ("1" .. "8") inside Bay Lanes
    for (bIdx in 0 until 8) {
        val bz = SHORANUR_TERM_BAY_1_CENTER_Z + bIdx * SHORANUR_TERM_BAY_PITCH_Z
        val numSpec = createShoranurBayNumberSpec("SHORANUR_BAY_NUM_${bIdx + 1}", "${bIdx + 1}")
        faces.add(
            QuadFace3D(
                Vec3(158.0f, 0.068f, bz + 1.15f),
                Vec3(158.0f, 0.068f, bz - 1.15f),
                Vec3(160.8f, 0.068f, bz - 1.15f),
                Vec3(160.8f, 0.068f, bz + 1.15f),
                baseColor = Color.White,
                biasDist = -0.9f,
                signboard = numSpec
            )
        )
    }

    // =========================================================================
    // 3. PERIMETER COMPOUND WALLS & TWIN WEST PORTALS (X = 126.0m)
    //    South ENTRY: Z = 3038.0 .. 3050.0m | North EXIT: Z = 3100.0 .. 3112.0m
    // =========================================================================
    addWallPolyline(listOf(Pair(126.0f, 3015.0f), Pair(195.8f, 3015.0f), Pair(195.8f, 3135.0f), Pair(126.0f, 3135.0f)))
    addWallPolyline(listOf(Pair(126.0f, 3015.0f), Pair(126.0f, 3037.5f)))
    addWallPolyline(listOf(Pair(126.0f, 3050.5f), Pair(126.0f, 3099.5f)))
    addWallPolyline(listOf(Pair(126.0f, 3112.5f), Pair(126.0f, 3135.0f)))

    // Inner perimeter yellow-black safety curbs
    addYellowBlackKerbPolyline(listOf(Pair(126.6f, 3015.8f), Pair(126.6f, 3037.5f)))
    addYellowBlackKerbPolyline(listOf(Pair(126.6f, 3050.5f), Pair(126.6f, 3099.5f)))
    addYellowBlackKerbPolyline(listOf(Pair(126.6f, 3112.5f), Pair(126.6f, 3134.2f)))
    addYellowBlackKerbPolyline(listOf(Pair(178.6f, 3028.0f), Pair(178.6f, 3122.0f)))

    // --- SOUTH ENTRY PORTAL (X = 126.0m, Z = 3038.0 .. 3050.0m) ---
    val gateX = 126.0f
    addBox(gateX - 0.50f, 0.06f, 3037.1f, gateX + 0.50f, 6.20f, 3038.0f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(gateX - 0.50f, 0.06f, 3050.0f, gateX + 0.50f, 6.20f, 3050.9f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(gateX - 0.55f, 5.55f, 3036.8f, gateX + 0.55f, 6.35f, 3051.2f, ShorTealRoof, ShorTealDark, ShorTealRoof, ShorSoffitGrey)
    val entrySpec = createShoranurRoof3DTextSpec("SHORANUR_GATE_ENTRY_V1", "ENTRY", 512, 160, 0xFFF8FAFC.toInt(), 0xFF153B48.toInt(), 112f)
    faces.add(QuadFace3D(Vec3(gateX, 6.38f, 3047.5f), Vec3(gateX, 6.38f, 3040.5f), Vec3(gateX, 7.80f, 3040.5f), Vec3(gateX, 7.80f, 3047.5f), Color.White, biasDist = -1.5f, signboard = entrySpec))
    // Guard Booth between gates
    addBox(gateX + 0.45f, 0.06f, 3051.5f, gateX + 2.45f, 2.75f, 3054.2f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(gateX + 0.30f, 2.75f, 3051.3f, gateX + 2.60f, 3.05f, 3054.4f, ShorTealRoof)
    // Entry Boom Pedestal & Rest Post
    addBox(gateX + 0.58f, 0.06f, 3038.1f, gateX + 0.90f, 1.05f, 3038.4f, ShorKerbYellow, ShorKerbBlack, ShorKerbYellow)
    addBox(gateX + 0.58f, 0.06f, 3049.6f, gateX + 0.90f, 0.92f, 3049.9f, ShorKerbBlack, ShorKerbYellow, ShorKerbBlack)

    // --- NORTH EXIT PORTAL (X = 126.0m, Z = 3100.0 .. 3112.0m) ---
    addBox(gateX - 0.50f, 0.06f, 3099.1f, gateX + 0.50f, 6.20f, 3100.0f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(gateX - 0.50f, 0.06f, 3112.0f, gateX + 0.50f, 6.20f, 3112.9f, ShorWallIvory, ShorColumnLight, ShorWallIvory)
    addBox(gateX - 0.55f, 5.55f, 3098.8f, gateX + 0.55f, 6.35f, 3113.2f, ShorTealRoof, ShorTealDark, ShorTealRoof, ShorSoffitGrey)
    val exitSpec = createShoranurRoof3DTextSpec("SHORANUR_GATE_EXIT_V1", "EXIT", 512, 160, 0xFFF8FAFC.toInt(), 0xFF153B48.toInt(), 112f)
    faces.add(QuadFace3D(Vec3(gateX, 6.38f, 3109.5f), Vec3(gateX, 6.38f, 3102.5f), Vec3(gateX, 7.80f, 3102.5f), Vec3(gateX, 7.80f, 3109.5f), Color.White, biasDist = -1.5f, signboard = exitSpec))
    // Exit Boom Pedestal & Rest Post
    addBox(gateX + 0.58f, 0.06f, 3100.1f, gateX + 0.90f, 1.05f, 3100.4f, ShorKerbYellow, ShorKerbBlack, ShorKerbYellow)
    addBox(gateX + 0.58f, 0.06f, 3111.6f, gateX + 0.90f, 0.92f, 3111.9f, ShorKerbBlack, ShorKerbYellow, ShorKerbBlack)

    faces
}

private fun appendShoranurBoomArmAlongZ(
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

    fun addSeg(s0: Float, s1: Float, ht: Float, topCol: Color, sideCol: Color) {
        fun pt(s: Float, dv: Float, dx: Float) = Vec3(pivotX + dx, pivotY + uy * s + vy * dv, pivotZ + uz * s + vz * dv)
        val c000 = pt(s0, -ht, -ht); val c100 = pt(s1, -ht, -ht); val c110 = pt(s1, ht, -ht); val c010 = pt(s0, ht, -ht)
        val c001 = pt(s0, -ht, ht); val c101 = pt(s1, -ht, ht); val c111 = pt(s1, ht, ht); val c011 = pt(s0, ht, ht)
        outFaces.add(QuadFace3D(c011, c111, c110, c010, topCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c000, c100, c101, c001, sideCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c001, c101, c111, c011, topCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(c100, c000, c010, c110, sideCol, biasDist = -0.35f))
    }

    addSeg(-0.65f, 0f, 0.075f, ShorSteelArch, ShorKerbBlack)
    val n = 14
    val segLen = armLength / n
    for (i in 0 until n) {
        val isRed = (i % 2 == 0)
        addSeg(i * segLen, (i + 1) * segLen, 0.055f, if (isRed) ShorBarrierRed else ShorBarrierWhite, if (isRed) Color(0xFF991B1B) else Color(0xFFCBD5E1))
    }
}

internal fun collectShoranurTerminalDynamicBarrierFaces(
    entryOpenFrac: Float,
    exitOpenFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    appendShoranurBoomArmAlongZ(126.74f, 0.92f, 3038.25f, 1f, 11.45f, entryOpenFrac, outFaces)
    appendShoranurBoomArmAlongZ(126.74f, 0.92f, 3100.25f, 1f, 11.45f, exitOpenFrac, outFaces)
}

internal fun collectAllShoranurTerminalStaticFaces(outFaces: MutableList<QuadFace3D>) {
    outFaces.addAll(cachedShoranurTerminalFaces)
}

internal fun createShoranurRoof3DTextSpec(
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
            typeface = PremiumFonts.displayTypefaceFor(text, bold = true)
            letterSpacing = 0.07f
        }
        val safeW = bmpWidth - 28f
        val measured = p.measureText(text)
        if (measured > safeW && measured > 1f) {
            p.textSize = (textSizePx * (safeW / measured)).coerceAtLeast(22f)
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

private fun createShoranurBayNumberSpec(cacheKey: String, numText: String): SignboardQuadSpec = SignboardQuadSpec(
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
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = 0x88153B48.toInt()
        c.drawText(numText, 80f, 120f, p)
        p.style = Paint.Style.FILL; p.color = 0xFFF1F5F9.toInt()
        c.drawText(numText, 80f, 120f, p)
        bmp
    }
)
