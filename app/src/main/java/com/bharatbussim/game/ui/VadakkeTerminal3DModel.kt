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
 * High-detail OpenGL ES 3.0 3D Architectural Model of Vadakke Bus Stand (Thrissur North Terminal).
 *
 * Distinct Architectural Silhouette (completely different from Sakthan's flat L-shape):
 * - Symmetrical U-Shaped Horseshoe Terminal wrapping West, North, and East around a wide courtyard,
 *   crowned with stepped pitched terracotta-and-slate roofs & an 11.5m NE Architectural Clock Tower.
 * - 8 Angled Echelon Sawtooth Bus Bays (1..8, tilted 13.75 deg) with stone-and-terracotta divider
 *   islands, yellow-black safety kerbs, and tilted bay floor numbers.
 * - Sweeping V-Wing / Butterfly Gull-Wing Steel-Truss Canopy over Bays 1..8 supported on V-struts
 *   mounted strictly on divider islands (0% driving-lane encroachment).
 * - Stand name "VADAKKE BUS STAND, THRISSUR" ONLY on top above the bus parking canopy roof
 *   (no blue boxes, no Malayalam, no duplicate fascia/wing banners).
 * - Twin South Portals (Z = 200.5m) connecting directly to Shoranur Road:
 *   - East ENTRY Gate (X = -27.5..-15.5m) with ONLY 3D "ENTRY" text & animated boom barrier.
 *   - West EXIT Gate (X = -53.5..-41.5m) with ONLY 3D "EXIT" text & animated boom barrier.
 * - Zero green ground/platform: warm granite-earth surround, stone concrete plinth & warm sandstone.
 */

private val VadakkeGroundEarth = Color(0xFF6A645B)
private val VadakkeBasementStone = Color(0xFF938E85)
private val VadakkeBayIslandStone = Color(0xFFA8A195)
private val VadakkeApronAsphalt = Color(0xFF444952)
private val VadakkeApronApproach = Color(0xFF393E46)
private val VadakkeJointLine = Color(0x5523272D)
private val VadakkeWhiteMark = Color(0xFFF1F5F9)
private val VadakkeWallSandstone = Color(0xFFDDD5C7)
private val VadakkeWallTrim = Color(0xFFA79E8E)
private val VadakkeColumnStone = Color(0xFFC5BEB0)
private val VadakkeFasciaLight = Color(0xFFE7E1D6)
private val VadakkeFasciaShadow = Color(0xFF9E9689)
private val VadakkePlatformTile = Color(0xFFCBC4B8)
private val VadakkeRoofTerracotta = Color(0xFF9A4A33)
private val VadakkeRoofTerracottaDark = Color(0xFF733322)
private val VadakkeRoofSlate = Color(0xFF333942)
private val VadakkeRoofDeckDark = Color(0xFF262B32)
private val VadakkeKerbYellow = Color(0xFFEAB308)
private val VadakkeKerbBlack = Color(0xFF1E2228)
private val VadakkeWoodTeak = Color(0xFF784B2A)
private val VadakkeSteelDark = Color(0xFF282D35)
private val VadakkeGlassTint = Color(0xFF1F3448)
private val VadakkeBarrierRed = Color(0xFFDC2626)
private val VadakkeBarrierWhite = Color(0xFFF8FAFC)

// Shared bay geometry constants (synchronized with ThrissurSimViewModel bay spawn)
internal const val VADAKKE_BAY_TILT_RAD = 0.24f // ~13.75 deg echelon sawtooth tilt
internal const val VADAKKE_BAY_1_CENTER_X = -53.0f
internal const val VADAKKE_BAY_PITCH_X = 4.95f
internal const val VADAKKE_BAY_CENTER_Z = 229.2f

internal fun DrawScope.drawVadakkeModernTerminalYard(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - (-35.5f), camEye.z - 224f) > 360f) return

    val yGroundOuter = 0.050f
    val yBasementPlinth = 0.052f
    val yApron = 0.058f

    // 1. Warm Stone-Earth Surround & Concrete Basement Plinth (Zero green!)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-78.0f, yGroundOuter, 184.0f),
            Vec3(8.0f, yGroundOuter, 184.0f),
            Vec3(8.0f, yGroundOuter, 264.0f),
            Vec3(-78.0f, yGroundOuter, 264.0f)
        )
    ) {
        drawPath(path, VadakkeGroundEarth)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-69.5f, yBasementPlinth, 193.5f),
            Vec3(-1.5f, yBasementPlinth, 193.5f),
            Vec3(-1.5f, yBasementPlinth, 255.0f),
            Vec3(-69.5f, yBasementPlinth, 255.0f)
        )
    ) {
        drawPath(path, VadakkeBasementStone)
    }

    // 2. Smooth Asphalt Courtyard Inside U-Shaped Terminal Walls (X = -64.3 .. -6.7, Z = 200.5 .. 250.5)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-64.3f, yApron, 200.5f),
            Vec3(-6.7f, yApron, 200.5f),
            Vec3(-6.7f, yApron, 250.5f),
            Vec3(-64.3f, yApron, 250.5f)
        )
    ) {
        drawPath(path, VadakkeApronAsphalt)
    }

    // 3. Portal Threshold Apron Pads at West EXIT (-53.5..-41.5) & East ENTRY (-27.5..-15.5)
    //    Seamlessly bridge exterior Shoranur Road / West Exit Loop into interior courtyard at Z = 200.5m
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-53.5f, 0.0588f, 199.4f),
            Vec3(-41.5f, 0.0588f, 199.4f),
            Vec3(-41.5f, 0.0588f, 202.8f),
            Vec3(-53.5f, 0.0588f, 202.8f)
        )
    ) {
        drawPath(path, VadakkeApronApproach)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-27.5f, 0.0588f, 199.4f),
            Vec3(-15.5f, 0.0588f, 199.4f),
            Vec3(-15.5f, 0.0588f, 202.8f),
            Vec3(-27.5f, 0.0588f, 202.8f)
        )
    ) {
        drawPath(path, VadakkeApronApproach)
    }

    // 4. Concrete Courtyard Expansion Joint Grid
    if (hypot(camEye.x - (-35.5f), camEye.z - 224f) < 165f) {
        var gx = -56.0f
        while (gx <= -12.0f) {
            rig.projectSegmentClipped(Vec3(gx, 0.0592f, 200.8f), Vec3(gx, 0.0592f, 240.5f))?.let { seg ->
                drawLine(VadakkeJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gx += 6.0f
        }
        var gz = 204.0f
        while (gz <= 238.0f) {
            rig.projectSegmentClipped(Vec3(-59.5f, 0.0592f, gz), Vec3(-11.5f, 0.0592f, gz))?.let { seg ->
                drawLine(VadakkeJointLine, seg.first, seg.second, strokeWidth = 1.0f)
            }
            gz += 6.0f
        }
    }

    // 5. Crisp White Counter-Clockwise Circulation Guide Curve:
    //    East ENTRY -> Open Courtyard Sweep -> West EXIT
    val loopGuidePts = listOf(
        Vec3(-21.5f, 0.060f, 200.5f),
        Vec3(-21.5f, 0.060f, 205.0f),
        Vec3(-23.0f, 0.060f, 211.5f),
        Vec3(-29.0f, 0.060f, 215.0f),
        Vec3(-39.5f, 0.060f, 215.0f),
        Vec3(-45.8f, 0.060f, 211.5f),
        Vec3(-47.5f, 0.060f, 205.0f),
        Vec3(-47.5f, 0.060f, 200.5f)
    )
    for (i in 0 until loopGuidePts.size - 1) {
        rig.projectSegmentClipped(loopGuidePts[i], loopGuidePts[i + 1])?.let { seg ->
            drawLine(VadakkeWhiteMark, seg.first, seg.second, strokeWidth = 1.8f)
        }
    }

    // 6. Angled Echelon Bay Boundary Lines & Tire Wear Tracks for 8 Sawtooth Bays (1..8)
    val sinA = sin(VADAKKE_BAY_TILT_RAD)
    val cosA = cos(VADAKKE_BAY_TILT_RAD)
    // Outward direction along bay (pointing South-South-West toward courtyard & West EXIT)
    val fwdX = -sinA
    val fwdZ = -cosA
    // Perpendicular right vector across bay width
    val perpX = cosA
    val perpZ = -sinA

    val halfLaneW = 1.82f
    val bayHalfLen = 7.4f
    val tireDark = Color(0x441B1F26)

    for (bIdx in 0 until 8) {
        val bx = VADAKKE_BAY_1_CENTER_X + bIdx * VADAKKE_BAY_PITCH_X
        val bz = VADAKKE_BAY_CENTER_Z

        fun bayPt(lx: Float, lz: Float, y: Float): Vec3 = Vec3(
            bx + perpX * lx + fwdX * lz,
            y,
            bz + perpZ * lx + fwdZ * lz
        )

        for (tx in floatArrayOf(-0.92f, 0.92f)) {
            rig.projectSegmentClipped(
                bayPt(tx, -bayHalfLen + 1.0f, 0.0595f),
                bayPt(tx, bayHalfLen - 1.0f, 0.0595f)
            )?.let { seg ->
                drawLine(tireDark, seg.first, seg.second, strokeWidth = 2.6f)
            }
        }
        rig.projectSegmentClipped(
            bayPt(-halfLaneW, -bayHalfLen, 0.0605f),
            bayPt(-halfLaneW, bayHalfLen, 0.0605f)
        )?.let { seg ->
            drawLine(VadakkeWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(
            bayPt(halfLaneW, -bayHalfLen, 0.0605f),
            bayPt(halfLaneW, bayHalfLen, 0.0605f)
        )?.let { seg ->
            drawLine(VadakkeWhiteMark, seg.first, seg.second, strokeWidth = 1.9f)
        }
        rig.projectSegmentClipped(
            bayPt(-halfLaneW, bayHalfLen, 0.0605f),
            bayPt(halfLaneW, bayHalfLen, 0.0605f)
        )?.let { seg ->
            drawLine(VadakkeWhiteMark, seg.first, seg.second, strokeWidth = 1.6f)
        }
    }
}

private val cachedVadakkeModernTerminalFaces: List<QuadFace3D> by lazy {
    val faces = ArrayList<QuadFace3D>(3400)

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
                Vec3(minX, minY, minZ),
                Vec3(maxX, minY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(minX, maxY, minZ),
                frontBackCol, null, bias, 0f, 0f, 0f, -1f
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(maxX, minY, maxZ),
                Vec3(minX, minY, maxZ),
                Vec3(minX, maxY, maxZ),
                Vec3(maxX, maxY, maxZ),
                frontBackCol, null, bias, 0f, 0f, 0f, 1f
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, maxZ),
                Vec3(minX, minY, minZ),
                Vec3(minX, maxY, minZ),
                Vec3(minX, maxY, maxZ),
                sideCol, null, bias, 0f, -1f, 0f, 0f
            )
        )
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

    fun addOrientedBox(
        cx: Float,
        cz: Float,
        tiltRad: Float,
        halfW: Float,
        minY: Float,
        maxY: Float,
        halfL: Float,
        topCol: Color,
        sideCol: Color = topCol,
        endCol: Color = sideCol,
        bias: Float = 0f
    ) {
        val s = sin(tiltRad)
        val c = cos(tiltRad)
        val fx = -s
        val fz = -c
        val px = c
        val pz = -s
        fun pt(lx: Float, y: Float, lz: Float) = Vec3(
            cx + px * lx + fx * lz,
            y,
            cz + pz * lx + fz * lz
        )
        val b0 = pt(-halfW, minY, -halfL)
        val b1 = pt(halfW, minY, -halfL)
        val b2 = pt(halfW, minY, halfL)
        val b3 = pt(-halfW, minY, halfL)
        val t0 = pt(-halfW, maxY, -halfL)
        val t1 = pt(halfW, maxY, -halfL)
        val t2 = pt(halfW, maxY, halfL)
        val t3 = pt(-halfW, maxY, halfL)

        faces.add(QuadFace3D(t0, t3, t2, t1, topCol, null, bias, 0f, 0f, 1f, 0f))
        faces.add(QuadFace3D(b0, b1, t1, t0, endCol, null, bias, 0f, -fx, 0f, -fz))
        faces.add(QuadFace3D(b2, b3, t3, t2, endCol, null, bias, 0f, fx, 0f, fz))
        faces.add(QuadFace3D(b3, b0, t0, t3, sideCol, null, bias, 0f, -px, 0f, -pz))
        faces.add(QuadFace3D(b1, b2, t2, t1, sideCol, null, bias, 0f, px, 0f, pz))
    }

    fun addPitchedGableRoof(
        minX: Float,
        minZ: Float,
        maxX: Float,
        maxZ: Float,
        eaveY: Float,
        ridgeY: Float,
        ridgeAlongX: Boolean,
        roofCol: Color,
        gableCol: Color
    ) {
        val midX = (minX + maxX) * 0.5f
        val midZ = (minZ + maxZ) * 0.5f
        if (ridgeAlongX) {
            val r0 = Vec3(minX, ridgeY, midZ)
            val r1 = Vec3(maxX, ridgeY, midZ)
            // South slope
            faces.add(QuadFace3D(Vec3(minX, eaveY, minZ), Vec3(maxX, eaveY, minZ), r1, r0, roofCol))
            // North slope
            faces.add(QuadFace3D(Vec3(maxX, eaveY, maxZ), Vec3(minX, eaveY, maxZ), r0, r1, roofCol))
            // West & East gable triangles
            faces.add(QuadFace3D(Vec3(minX, eaveY, maxZ), Vec3(minX, eaveY, minZ), r0, r0, gableCol))
            faces.add(QuadFace3D(Vec3(maxX, eaveY, minZ), Vec3(maxX, eaveY, maxZ), r1, r1, gableCol))
        } else {
            val r0 = Vec3(midX, ridgeY, minZ)
            val r1 = Vec3(midX, ridgeY, maxZ)
            // West slope
            faces.add(QuadFace3D(Vec3(minX, eaveY, maxZ), Vec3(minX, eaveY, minZ), r0, r1, roofCol))
            // East slope
            faces.add(QuadFace3D(Vec3(maxX, eaveY, minZ), Vec3(maxX, eaveY, maxZ), r1, r0, roofCol))
            // South & North gable triangles
            faces.add(QuadFace3D(Vec3(minX, eaveY, minZ), Vec3(maxX, eaveY, minZ), r0, r0, gableCol))
            faces.add(QuadFace3D(Vec3(maxX, eaveY, maxZ), Vec3(minX, eaveY, maxZ), r1, r1, gableCol))
        }
    }

    fun addWallPolyline(
        pts: List<Pair<Float, Float>>,
        wallMinY: Float = 0.06f,
        wallMaxY: Float = 2.20f,
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
            val cx = nx * (halfThick + 0.06f)
            val cz = nz * (halfThick + 0.06f)
            val capBotY = wallMaxY
            val capTopY = wallMaxY + 0.16f

            faces.add(
                QuadFace3D(
                    Vec3(x0 - ox, wallMinY, z0 - oz),
                    Vec3(x1 - ox, wallMinY, z1 - oz),
                    Vec3(x1 - ox, wallMaxY, z1 - oz),
                    Vec3(x0 - ox, wallMaxY, z0 - oz),
                    VadakkeWallSandstone, null, 0f, 0f, -nx, 0f, -nz
                )
            )
            faces.add(
                QuadFace3D(
                    Vec3(x1 + ox, wallMinY, z1 + oz),
                    Vec3(x0 + ox, wallMinY, z0 + oz),
                    Vec3(x0 + ox, wallMaxY, z0 + oz),
                    Vec3(x1 + ox, wallMaxY, z1 + oz),
                    VadakkeWallSandstone, null, 0f, 0f, nx, 0f, nz
                )
            )
            val t0 = Vec3(x0 - cx, capTopY, z0 - cz)
            val t1 = Vec3(x1 - cx, capTopY, z1 - cz)
            val t2 = Vec3(x1 + cx, capTopY, z1 + cz)
            val t3 = Vec3(x0 + cx, capTopY, z0 + cz)
            faces.add(QuadFace3D(t0, t1, t2, t3, VadakkeRoofTerracottaDark, null, 0f, 0f, 0f, 1f, 0f))
            faces.add(
                QuadFace3D(
                    Vec3(x0 - cx, capBotY, z0 - cz),
                    Vec3(x1 - cx, capBotY, z1 - cz),
                    t1, t0, VadakkeWallTrim, null, 0f, 0f, -nx, 0f, -nz
                )
            )
            faces.add(
                QuadFace3D(
                    Vec3(x1 + cx, capBotY, z1 + cz),
                    Vec3(x0 + cx, capBotY, z0 + cz),
                    t3, t2, VadakkeWallTrim, null, 0f, 0f, nx, 0f, nz
                )
            )
        }
    }

    fun addYellowBlackKerbPolyline(
        pts: List<Pair<Float, Float>>,
        kerbWidth: Float = 0.30f,
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
                val col = if ((globalIdx++) % 2 == 0) VadakkeKerbYellow else VadakkeKerbBlack
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
    // 1. NORTH MAIN CONCOURSE & RETAIL ARCADE (X = -64.5 .. -6.5m, Z = 240.5 .. 250.5m)
    // =========================================================================
    addBox(-64.5f, 0.06f, 240.5f, -6.5f, 0.32f, 250.5f, VadakkePlatformTile, VadakkeFasciaShadow, VadakkeFasciaShadow)
    // Rear North wall & corner enclosure
    addBox(-64.5f, 0.32f, 249.9f, -6.5f, 4.45f, 250.5f, VadakkeWallSandstone, VadakkeFasciaShadow, VadakkeWallSandstone)
    // Arcade columns along South edge of North Concourse (Z = 241.1m)
    for (cx in floatArrayOf(-59.2f, -49.5f, -39.8f, -30.1f, -20.4f, -11.5f)) {
        addBox(cx - 0.28f, 0.32f, 240.8f, cx + 0.28f, 4.45f, 241.36f, VadakkeColumnStone)
        addBox(cx - 0.36f, 0.32f, 240.72f, cx + 0.36f, 0.95f, 241.44f, VadakkeRoofTerracottaDark)
    }
    // Concrete fascia + Terracotta pitched gable roof over North Concourse
    addBox(-64.9f, 4.45f, 240.1f, -6.1f, 5.02f, 250.9f, VadakkeFasciaLight, VadakkeWallTrim, VadakkeFasciaLight, VadakkeSteelDark)
    addBox(-64.6f, 5.02f, 240.4f, -6.4f, 5.24f, 250.6f, VadakkeRoofDeckDark)
    addPitchedGableRoof(-64.8f, 240.3f, -6.2f, 250.7f, eaveY = 5.24f, ridgeY = 7.05f, ridgeAlongX = true, roofCol = VadakkeRoofTerracotta, gableCol = VadakkeWallSandstone)

    // 4 Textured English-Only Commercial Stalls along North Concourse Back Wall (Z = 246.8m, facing South -Z)
    addBox(-64.0f, 0.32f, 246.8f, -13.0f, 4.45f, 250.0f, VadakkeWallSandstone)
    val shopConfigs = arrayOf(
        Triple("NORTH STAND TEA & SNACKS", "CHAI • COFFEE • BAKERY", 0xFF9A3412.toInt()),
        Triple("SHORANUR TRAVELS & PARCEL", "TICKETS • COURIER • CARGO", 0xFF0F766E.toInt()),
        Triple("MALABAR NEWS & BOOKS", "PAPERS • MAGAZINES • RECHARGE", 0xFF1E3A8A.toInt()),
        Triple("KERALA MILK & COOL BAR", "FRESH JUICES • ICE CREAM", 0xFF3F6212.toInt())
    )
    for (sIdx in shopConfigs.indices) {
        val sx0 = -58.0f + sIdx * 10.8f
        val sx1 = sx0 + 9.6f
        val (title, sub, bannerCol) = shopConfigs[sIdx]
        val spec = createVadakkeShopfrontSpec("VADAKKE_SHOP_V1_$sIdx", title, sub, bannerCol)
        // Facing South (-Z): v0 = sx0, v1 = sx1 -> u = (+9.6, 0, 0), w = (0, +3.5, 0) -> nz > 0, dot > 0 from South courtyard!
        faces.add(
            QuadFace3D(
                Vec3(sx0, 0.32f, 246.76f),
                Vec3(sx1, 0.32f, 246.76f),
                Vec3(sx1, 3.82f, 246.76f),
                Vec3(sx0, 3.82f, 246.76f),
                baseColor = VadakkeColumnStone,
                biasDist = -0.6f,
                signboard = spec
            )
        )
    }

    // Passenger Waiting Benches under North Concourse
    for (bx in floatArrayOf(-53.0f, -42.5f, -32.0f, -21.5f)) {
        addBox(bx - 1.8f, 0.32f, 243.4f, bx + 1.8f, 0.56f, 244.05f, VadakkeSteelDark)
        addBox(bx - 1.85f, 0.56f, 243.38f, bx + 1.85f, 0.70f, 244.08f, VadakkeWoodTeak)
        addBox(bx - 1.85f, 0.70f, 243.96f, bx + 1.85f, 1.12f, 244.08f, VadakkeWoodTeak)
    }

    // =========================================================================
    // 2. WEST WAITING PAVILION (X = -64.5 .. -59.0m, Z = 201.0 .. 240.5m)
    // =========================================================================
    addBox(-64.5f, 0.06f, 201.0f, -59.0f, 0.32f, 240.5f, VadakkePlatformTile, VadakkeFasciaShadow, VadakkeFasciaShadow)
    addBox(-64.5f, 0.32f, 201.0f, -63.9f, 4.45f, 240.5f, VadakkeWallSandstone)
    for (wz in floatArrayOf(205.5f, 214.0f, 222.5f, 231.0f, 238.5f)) {
        addBox(-59.55f, 0.32f, wz - 0.26f, -59.05f, 4.45f, wz + 0.26f, VadakkeColumnStone)
        addBox(-59.62f, 0.32f, wz - 0.34f, -58.98f, 0.92f, wz + 0.34f, VadakkeRoofTerracottaDark)
    }
    addBox(-64.8f, 4.45f, 200.7f, -58.7f, 5.02f, 240.7f, VadakkeFasciaLight, VadakkeWallTrim, VadakkeFasciaLight, VadakkeSteelDark)
    addPitchedGableRoof(-64.7f, 200.8f, -58.8f, 240.6f, eaveY = 5.02f, ridgeY = 6.35f, ridgeAlongX = false, roofCol = VadakkeRoofTerracotta, gableCol = VadakkeWallSandstone)
    // West ticket kiosk & seating
    addBox(-63.8f, 0.32f, 216.0f, -61.2f, 2.55f, 222.0f, VadakkeWallSandstone)
    addBox(-61.22f, 1.25f, 217.0f, -61.16f, 2.25f, 221.0f, VadakkeGlassTint)

    // =========================================================================
    // 3. EAST TRANSIT WING & ICONIC 11.5m CLOCK TOWER (X = -11.5 .. -6.5m, Z = 201.0 .. 240.5m)
    // =========================================================================
    addBox(-11.5f, 0.06f, 201.0f, -6.5f, 0.32f, 240.5f, VadakkePlatformTile, VadakkeFasciaShadow, VadakkeFasciaShadow)
    addBox(-7.1f, 0.32f, 201.0f, -6.5f, 4.45f, 240.5f, VadakkeWallSandstone)
    for (ez in floatArrayOf(205.5f, 214.0f, 222.5f, 231.0f, 238.5f)) {
        addBox(-11.45f, 0.32f, ez - 0.26f, -10.95f, 4.45f, ez + 0.26f, VadakkeColumnStone)
        addBox(-11.52f, 0.32f, ez - 0.34f, -10.88f, 0.92f, ez + 0.34f, VadakkeRoofTerracottaDark)
    }
    addBox(-11.8f, 4.45f, 200.7f, -6.2f, 5.02f, 240.7f, VadakkeFasciaLight, VadakkeWallTrim, VadakkeFasciaLight, VadakkeSteelDark)
    addPitchedGableRoof(-11.7f, 200.8f, -6.3f, 240.6f, eaveY = 5.02f, ridgeY = 6.35f, ridgeAlongX = false, roofCol = VadakkeRoofTerracotta, gableCol = VadakkeWallSandstone)

    // Iconic NE Corner Clock Tower (X = -11.6 .. -7.0m, Z = 242.5 .. 247.1m, height 11.8m)
    addBox(-11.6f, 0.32f, 242.5f, -7.0f, 9.60f, 247.1f, VadakkeWallSandstone, VadakkeWallTrim, VadakkeWallSandstone)
    addBox(-11.9f, 9.60f, 242.2f, -6.7f, 10.10f, 247.4f, VadakkeRoofSlate)
    // Pyramidal spire on Clock Tower (apex at -9.3, 12.1, 244.8)
    val towerApex = Vec3(-9.3f, 12.15f, 244.8f)
    faces.add(QuadFace3D(Vec3(-11.8f, 10.10f, 242.3f), Vec3(-6.8f, 10.10f, 242.3f), towerApex, towerApex, VadakkeRoofTerracotta))
    faces.add(QuadFace3D(Vec3(-6.8f, 10.10f, 247.3f), Vec3(-11.8f, 10.10f, 247.3f), towerApex, towerApex, VadakkeRoofTerracotta))
    faces.add(QuadFace3D(Vec3(-11.8f, 10.10f, 247.3f), Vec3(-11.8f, 10.10f, 242.3f), towerApex, towerApex, VadakkeRoofTerracottaDark))
    faces.add(QuadFace3D(Vec3(-6.8f, 10.10f, 242.3f), Vec3(-6.8f, 10.10f, 247.3f), towerApex, towerApex, VadakkeRoofTerracottaDark))
    // Clock dials on South (-Z) and West (-X) tower faces
    val clockSpec = createClockDialSignSpec("VADAKKE_TOWER_CLOCK_V1", wallBgColorInt = 0xFFDDD5C7.toInt())
    faces.add(
        QuadFace3D(
            Vec3(-10.55f, 7.20f, 242.45f),
            Vec3(-8.05f, 7.20f, 242.45f),
            Vec3(-8.05f, 9.35f, 242.45f),
            Vec3(-10.55f, 9.35f, 242.45f),
            baseColor = Color.White,
            biasDist = -0.8f,
            signboard = clockSpec
        )
    )
    faces.add(
        QuadFace3D(
            Vec3(-11.65f, 7.20f, 246.05f),
            Vec3(-11.65f, 7.20f, 243.55f),
            Vec3(-11.65f, 9.35f, 243.55f),
            Vec3(-11.65f, 9.35f, 246.05f),
            baseColor = Color.White,
            biasDist = -0.8f,
            signboard = clockSpec
        )
    )

    // =========================================================================
    // 4. 8 ANGLED ECHELON BUS BAYS (1..8) WITH YELLOW-BLACK KERB ISLANDS
    //    AND SWEEPING V-WING / BUTTERFLY CANOPY ROOF + ROOFTOP STAND NAME ONLY
    // =========================================================================
    val sinA = sin(VADAKKE_BAY_TILT_RAD)
    val cosA = cos(VADAKKE_BAY_TILT_RAD)
    val fwdX = -sinA
    val fwdZ = -cosA

    // 9 Angled Divider Islands (half-width 0.30m, half-length 7.6m)
    for (pIdx in 0..8) {
        val ix = (VADAKKE_BAY_1_CENTER_X - VADAKKE_BAY_PITCH_X * 0.5f) + pIdx * VADAKKE_BAY_PITCH_X
        val iz = VADAKKE_BAY_CENTER_Z
        addOrientedBox(
            cx = ix,
            cz = iz,
            tiltRad = VADAKKE_BAY_TILT_RAD,
            halfW = 0.24f,
            minY = 0.06f,
            maxY = 0.19f,
            halfL = 7.45f,
            topCol = VadakkeBayIslandStone,
            sideCol = VadakkeKerbBlack,
            endCol = VadakkeKerbYellow
        )
        // Perimeter yellow-black kerb wrap around each angled divider island
        val pNose = Pair(ix + fwdX * 7.45f, iz + fwdZ * 7.45f)
        val pTail = Pair(ix - fwdX * 7.45f, iz - fwdZ * 7.45f)
        val px = cosA * 0.22f
        val pz = -sinA * 0.22f
        addYellowBlackKerbPolyline(
            listOf(
                Pair(pNose.first - px, pNose.second - pz),
                Pair(pTail.first - px, pTail.second - pz),
                Pair(pTail.first + px, pTail.second + pz),
                Pair(pNose.first + px, pNose.second + pz)
            ),
            kerbWidth = 0.18f,
            kerbHeight = 0.21f,
            blockLen = 0.72f
        )

        // Steel Y-Columns mounted strictly on even divider islands (pIdx = 0, 2, 4, 6, 8)
        if (pIdx % 2 == 0) {
            for (sOff in floatArrayOf(-4.2f, 4.2f)) {
                val colX = ix + fwdX * sOff
                val colZ = iz + fwdZ * sOff
                addBox(colX - 0.14f, 0.19f, colZ - 0.14f, colX + 0.14f, 5.55f, colZ + 0.14f, VadakkeSteelDark)
                addBox(colX - 0.20f, 0.19f, colZ - 0.20f, colX + 0.20f, 0.65f, colZ + 0.20f, VadakkeKerbBlack)
                // Diagonal Y-arm bracing up to butterfly wings
                addBox(colX - 0.10f, 4.95f, colZ - 2.6f, colX + 0.10f, 5.85f, colZ + 2.6f, VadakkeRoofTerracottaDark)
            }
        }
    }

    // Tilted Crisp White Floor Bay Numbers ("1" .. "8") at South Nose of Each Angled Bay
    // (ny < 0 so dot = dy * ny > 0 when camera is above ground!)
    for (bIdx in 0 until 8) {
        val bx = VADAKKE_BAY_1_CENTER_X + bIdx * VADAKKE_BAY_PITCH_X
        val bz = VADAKKE_BAY_CENTER_Z
        val numCx = bx + fwdX * 6.0f
        val numCz = bz + fwdZ * 6.0f
        val perpX = cosA
        val perpZ = -sinA
        fun nPt(lx: Float, lz: Float) = Vec3(
            numCx + perpX * lx + fwdX * lz,
            0.068f,
            numCz + perpZ * lx + fwdZ * lz
        )
        val numSpec = createVadakkeBayNumberSpec("VADAKKE_BAY_NUM_V1_${bIdx + 1}", "${bIdx + 1}")
        faces.add(
            QuadFace3D(
                nPt(-1.05f, 1.35f),
                nPt(1.05f, 1.35f),
                nPt(1.05f, -1.35f),
                nPt(-1.05f, -1.35f),
                baseColor = Color.White,
                biasDist = -0.9f,
                signboard = numSpec
            )
        )
    }

    // Sweeping V-Wing / Butterfly Canopy Over Bays 1..8 (X = -58.2 .. -12.6m, Z = 219.8 .. 238.8m)
    // Central spine at Z = 229.3m (Y = 5.45m), lifting up to Y = 6.55m at South & North wing tips!
    val cMinX = -58.2f
    val cMaxX = -12.6f
    val cMidZ = 229.3f
    val cSouthZ = 219.8f
    val cNorthZ = 238.8f
    // Central spine box beam (Y = 5.38..5.72m)
    addBox(cMinX, 5.38f, cMidZ - 0.35f, cMaxX, 5.72f, cMidZ + 0.35f, VadakkeSteelDark)
    // South upturned wing slab (under soffit + dark slate upper sheet)
    faces.add(
        QuadFace3D(
            Vec3(cMinX, 6.52f, cSouthZ),
            Vec3(cMaxX, 6.52f, cSouthZ),
            Vec3(cMaxX, 5.56f, cMidZ),
            Vec3(cMinX, 5.56f, cMidZ),
            VadakkeFasciaLight, null, 0f, 0f, 0f, -1f, 0f
        )
    )
    faces.add(
        QuadFace3D(
            Vec3(cMinX, 6.66f, cSouthZ),
            Vec3(cMinX, 5.70f, cMidZ),
            Vec3(cMaxX, 5.70f, cMidZ),
            Vec3(cMaxX, 6.66f, cSouthZ),
            VadakkeRoofSlate, null, 0f, 0f, 0f, 1f, 0f
        )
    )
    // North upturned wing slab
    faces.add(
        QuadFace3D(
            Vec3(cMinX, 5.56f, cMidZ),
            Vec3(cMaxX, 5.56f, cMidZ),
            Vec3(cMaxX, 6.45f, cNorthZ),
            Vec3(cMinX, 6.45f, cNorthZ),
            VadakkeFasciaLight, null, 0f, 0f, 0f, -1f, 0f
        )
    )
    faces.add(
        QuadFace3D(
            Vec3(cMinX, 5.70f, cMidZ),
            Vec3(cMinX, 6.58f, cNorthZ),
            Vec3(cMaxX, 6.58f, cNorthZ),
            Vec3(cMaxX, 5.70f, cMidZ),
            VadakkeRoofSlate, null, 0f, 0f, 0f, 1f, 0f
        )
    )
    // Terracotta edge trim beams along South & North butterfly wing tips
    addBox(cMinX, 6.44f, cSouthZ - 0.16f, cMaxX, 6.68f, cSouthZ + 0.16f, VadakkeRoofTerracotta, VadakkeRoofTerracottaDark, VadakkeRoofTerracotta)
    addBox(cMinX, 6.38f, cNorthZ - 0.16f, cMaxX, 6.60f, cNorthZ + 0.16f, VadakkeRoofTerracotta, VadakkeRoofTerracottaDark, VadakkeRoofTerracotta)

    // "VADAKKE BUS STAND, THRISSUR" — ONLY on top above the roof of the bus parking canopy!
    // Transparent-background 3D raised lettering (no blue box, no Malayalam, no other placements)
    val vadakkeRoofTitleSpec = createVadakke3DTextSpec(
        cacheKey = "VADAKKE_ROOF_3D_TITLE_V1",
        text = "VADAKKE BUS STAND, THRISSUR",
        bmpWidth = 1400,
        bmpHeight = 160,
        faceColorInt = 0xFFF8FAFC.toInt(),
        extrudeColorInt = 0xFF334155.toInt(),
        textSizePx = 74f
    )
    faces.add(
        QuadFace3D(
            Vec3(-55.0f, 6.68f, cSouthZ),
            Vec3(-16.0f, 6.68f, cSouthZ),
            Vec3(-16.0f, 8.28f, cSouthZ),
            Vec3(-55.0f, 8.28f, cSouthZ),
            baseColor = Color.White,
            biasDist = -1.6f,
            signboard = vadakkeRoofTitleSpec
        )
    )

    // =========================================================================
    // 5. YELLOW-AND-BLACK INNER PERIMETER KERBSTONES
    // =========================================================================
    // North Concourse front curb
    addYellowBlackKerbPolyline(listOf(Pair(-59.0f, 240.5f), Pair(-11.5f, 240.5f)), kerbWidth = 0.34f, kerbHeight = 0.28f, blockLen = 0.82f)
    // West Pavilion inner curb
    addYellowBlackKerbPolyline(listOf(Pair(-59.0f, 201.0f), Pair(-59.0f, 240.5f)), kerbWidth = 0.34f, kerbHeight = 0.28f, blockLen = 0.82f)
    // East Wing inner curb
    addYellowBlackKerbPolyline(listOf(Pair(-11.5f, 201.0f), Pair(-11.5f, 240.5f)), kerbWidth = 0.34f, kerbHeight = 0.28f, blockLen = 0.82f)

    // =========================================================================
    // 6. PERIMETER COMPOUND WALL (Z = 200.5m SOUTH WALL WITH TWO 12m GATES)
    //    - West EXIT Gate: X = -53.5 .. -41.5m (12.0m clear)
    //    - East ENTRY Gate: X = -27.5 .. -15.5m (12.0m clear)
    // =========================================================================
    addWallPolyline(listOf(Pair(-64.5f, 200.5f), Pair(-54.0f, 200.5f)))
    addWallPolyline(listOf(Pair(-41.0f, 200.5f), Pair(-28.0f, 200.5f)))
    addWallPolyline(listOf(Pair(-15.0f, 200.5f), Pair(-6.5f, 200.5f)))
    addWallPolyline(listOf(Pair(-64.5f, 200.5f), Pair(-64.5f, 250.5f)))
    addWallPolyline(listOf(Pair(-6.5f, 200.5f), Pair(-6.5f, 250.5f)))
    addWallPolyline(listOf(Pair(-64.5f, 250.5f), Pair(-6.5f, 250.5f)))

    // =========================================================================
    // 7. TWIN SOUTH PORTALS AT Z = 200.5m — ONLY "EXIT" & "ENTRY" TEXT ON GATES!
    // =========================================================================
    val gateZ = 200.5f

    // --- WEST EXIT PORTAL (X = -53.5 .. -41.5m, center -47.5m) ---
    addBox(-54.35f, 0.06f, gateZ - 0.45f, -53.55f, 5.75f, gateZ + 0.45f, VadakkeWallSandstone, VadakkeWallTrim, VadakkeWallSandstone)
    addBox(-41.45f, 0.06f, gateZ - 0.45f, -40.65f, 5.75f, gateZ + 0.45f, VadakkeWallSandstone, VadakkeWallTrim, VadakkeWallSandstone)
    addBox(-54.55f, 5.50f, gateZ - 0.52f, -40.45f, 6.18f, gateZ + 0.52f, VadakkeFasciaLight, VadakkeWallTrim, VadakkeFasciaLight, VadakkeSteelDark)
    addPitchedGableRoof(-54.65f, gateZ - 0.60f, -40.35f, gateZ + 0.60f, eaveY = 6.18f, ridgeY = 6.65f, ridgeAlongX = true, roofCol = VadakkeRoofTerracotta, gableCol = VadakkeWallSandstone)
    val exitTextSpec = createVadakke3DTextSpec(
        cacheKey = "VADAKKE_GATE_3D_EXIT_V1",
        text = "EXIT",
        bmpWidth = 512,
        bmpHeight = 160,
        faceColorInt = 0xFF374151.toInt(),
        extrudeColorInt = 0xFF111827.toInt(),
        textSizePx = 112f
    )
    faces.add(
        QuadFace3D(
            Vec3(-51.1f, 6.66f, gateZ),
            Vec3(-43.9f, 6.66f, gateZ),
            Vec3(-43.9f, 7.98f, gateZ),
            Vec3(-51.1f, 7.98f, gateZ),
            baseColor = VadakkeSteelDark,
            biasDist = -1.6f,
            signboard = exitTextSpec
        )
    )
    // West EXIT Boom Barrier Pedestal (Z = 201.18f, inside lintel) + Opposite Rest Post
    addBox(-53.46f, 0.06f, gateZ + 0.54f, -53.18f, 1.05f, gateZ + 0.84f, VadakkeKerbYellow, VadakkeKerbBlack, VadakkeKerbYellow)
    addBox(-41.82f, 0.06f, gateZ + 0.54f, -41.54f, 0.92f, gateZ + 0.84f, VadakkeKerbBlack, VadakkeKerbYellow, VadakkeKerbBlack)

    // --- EAST ENTRY PORTAL (X = -27.5 .. -15.5m, center -21.5m) ---
    addBox(-28.35f, 0.06f, gateZ - 0.45f, -27.55f, 5.75f, gateZ + 0.45f, VadakkeWallSandstone, VadakkeWallTrim, VadakkeWallSandstone)
    addBox(-15.45f, 0.06f, gateZ - 0.45f, -14.65f, 5.75f, gateZ + 0.45f, VadakkeWallSandstone, VadakkeWallTrim, VadakkeWallSandstone)
    addBox(-28.55f, 5.50f, gateZ - 0.52f, -14.45f, 6.18f, gateZ + 0.52f, VadakkeFasciaLight, VadakkeWallTrim, VadakkeFasciaLight, VadakkeSteelDark)
    addPitchedGableRoof(-28.65f, gateZ - 0.60f, -14.35f, gateZ + 0.60f, eaveY = 6.18f, ridgeY = 6.65f, ridgeAlongX = true, roofCol = VadakkeRoofTerracotta, gableCol = VadakkeWallSandstone)
    val entryTextSpec = createVadakke3DTextSpec(
        cacheKey = "VADAKKE_GATE_3D_ENTRY_V1",
        text = "ENTRY",
        bmpWidth = 512,
        bmpHeight = 160,
        faceColorInt = 0xFF374151.toInt(),
        extrudeColorInt = 0xFF111827.toInt(),
        textSizePx = 112f
    )
    faces.add(
        QuadFace3D(
            Vec3(-25.1f, 6.66f, gateZ),
            Vec3(-17.9f, 6.66f, gateZ),
            Vec3(-17.9f, 7.98f, gateZ),
            Vec3(-25.1f, 7.98f, gateZ),
            baseColor = VadakkeSteelDark,
            biasDist = -1.6f,
            signboard = entryTextSpec
        )
    )
    // Security guard booth outside Right ENTRY pier
    addBox(-14.60f, 0.06f, gateZ - 1.65f, -12.60f, 2.55f, gateZ + 0.15f, VadakkeWallSandstone, VadakkeWallTrim, VadakkeWallSandstone)
    addBox(-14.80f, 2.55f, gateZ - 1.85f, -12.40f, 2.84f, gateZ + 0.35f, VadakkeRoofTerracotta)
    addBox(-14.15f, 1.25f, gateZ - 1.67f, -13.05f, 2.05f, gateZ - 1.61f, VadakkeGlassTint)

    // East ENTRY Boom Barrier Pedestal (Z = 199.82f, outside lintel) + Opposite Rest Post
    addBox(-15.82f, 0.06f, gateZ - 0.84f, -15.54f, 1.05f, gateZ - 0.54f, VadakkeKerbYellow, VadakkeKerbBlack, VadakkeKerbYellow)
    addBox(-27.46f, 0.06f, gateZ - 0.84f, -27.18f, 0.92f, gateZ - 0.54f, VadakkeKerbBlack, VadakkeKerbYellow, VadakkeKerbBlack)

    faces
}

private fun appendVadakkeBoomBarrierArm(
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
    addRotatedSegment(-0.66f, 0.0f, 0.075f, VadakkeSteelDark, VadakkeKerbBlack)

    // 14 Alternating Red & White stripes across the 11.4m portal span
    val segCount = 14
    val segLen = armLength / segCount.toFloat()
    for (i in 0 until segCount) {
        val s0 = i * segLen
        val s1 = (i + 1) * segLen
        val isRed = (i % 2 == 0)
        val topCol = if (isRed) VadakkeBarrierRed else VadakkeBarrierWhite
        val sideCol = if (isRed) Color(0xFF991B1B) else Color(0xFFCBD5E1)
        addRotatedSegment(s0, s1, 0.055f, topCol, sideCol)
    }
}

internal fun collectVadakkeDynamicBarrierFaces(
    entryOpenFrac: Float,
    exitOpenFrac: Float,
    outFaces: MutableList<QuadFace3D>
) {
    // East ENTRY boom arm hinged at (-15.68, 0.92, 199.82), extending -X across East ENTRY gate
    appendVadakkeBoomBarrierArm(
        pivotX = -15.68f,
        pivotY = 0.92f,
        pivotZ = 199.82f,
        dirX = -1f,
        armLength = 11.60f,
        openFrac = entryOpenFrac,
        outFaces = outFaces
    )
    // West EXIT boom arm hinged at (-53.32, 0.92, 201.18), extending +X across West EXIT gate
    appendVadakkeBoomBarrierArm(
        pivotX = -53.32f,
        pivotY = 0.92f,
        pivotZ = 201.18f,
        dirX = 1f,
        armLength = 11.60f,
        openFrac = exitOpenFrac,
        outFaces = outFaces
    )
}

internal fun collectVadakkeModernTerminalSceneFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - (-35.5f)
    val dz = camEye.z - 224f
    if (dx * dx + dz * dz > 240f * 240f) return
    outFaces.addAll(cachedVadakkeModernTerminalFaces)
}

internal fun collectAllVadakkeModernTerminalStaticFaces(
    outFaces: MutableList<QuadFace3D>
) {
    outFaces.addAll(cachedVadakkeModernTerminalFaces)
}

private fun createVadakke3DTextSpec(
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

private fun createVadakkeShopfrontSpec(
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
        c.drawColor(0xFFD5CEC2.toInt())

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

private fun createVadakkeBayNumberSpec(
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
