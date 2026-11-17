package com.bharatbussim.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Authentic Kerala Temple, Thekkinkadu Maidan, Bus Terminal & Arabian Sea Coastal Material Palette
private val MatLateriteStone = Color(0xFF8B3A22)
private val MatLateriteDark = Color(0xFF682916)
private val MatLateriteStepTop = Color(0xFF9C472C)
private val MatKeralaWhitewash = Color(0xFFF4EFE6)
private val MatKeralaCreamWall = Color(0xFFE8DFD1)
private val MatTeakWoodCarved = Color(0xFF4A2C18)
private val MatTeakWoodDeep = Color(0xFF2B180C)
private val MatTerracottaTile = Color(0xFFB43E24)
private val MatTerracottaRoofDark = Color(0xFF8C2D19)
private val MatCopperRoof = Color(0xFF5C3A28)
private val MatCopperRoofDark = Color(0xFF462B1D)
private val MatTempleGold = Color(0xFFFACC15)
private val MatTempleGoldDeep = Color(0xFFD97706)
private val MatGranitePaving = Color(0xFF64748B)
private val MatGraniteLight = Color(0xFF94A3B8)
private val MatGraniteDark = Color(0xFF475569)
private val MatMaidanGrass = Color(0xFF4F6239)
private val MatMaidanGrassUpper = Color(0xFF586E40)
private val MatMaidanPathEarthy = Color(0xFF7D6B57)
private val MatTerminalConcrete = Color(0xFF2B3037)
private val MatRoadMarkingYellow = Color(0xFFFACC15)
private val MatRoadMarkingWhite = Color(0xFFF8FAFC)
private val MatNhaiBoardGreen = Color(0xFF065F46)

// Swaraj Round Outer Ring Commercial & Heritage Facade Palette (Round North, East, South, West)
private val MatCoffeeHouseRed = Color(0xFFA93822)
private val MatArcadeCream = Color(0xFFEFECE4)
private val MatArcadeSandstone = Color(0xFFDCCEB8)
private val MatArcadeGlass = Color(0xFF1E293B)
private val MatArcadeRoofSlate = Color(0xFF475569)

// Arabian Sea & Beach Palette (Chavakkad Beach & Triprayar Beach)
private val MatArabianSeaDeep = Color(0xFF075985)
private val MatArabianSeaMid = Color(0xFF0284C7)
private val MatArabianSeaShallow = Color(0xFF06B6D4)
private val MatSeaFoamWhite = Color(0xFFF0F9FF)
private val MatGoldenBeachSand = Color(0xFFE5C88E)
private val MatWetBeachSand = Color(0xFFC8A96E)
private val MatSeawallGranite = Color(0xFF475569)
private val MatPromenadeTile = Color(0xFFCBD5E1)
private val MatLighthouseRed = Color(0xFFDC2626)
private val MatLighthouseWhite = Color(0xFFF8FAFC)
private val MatTemplePondWater = Color(0xFF0E7490)

/**
 * Renders ground-level plazas, Thekkinkadu Maidan lawn & stone approach walkways aligned with
 * ROAD_SWARAJ_ROUND, bus terminal aprons, beach sand shores, seawalls, and Arabian Sea water surfaces.
 */
internal fun DrawScope.drawThrissurLandmarkGroundPlazas(
    rig: CameraRig,
    path: Path,
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>
) {
    drawSwarajRoundGroundPlaza(rig, path, camEye, roads)
    drawVadakkeBusStandGround(rig, path, camEye)
    drawPaliyekkaraTollPlazaGround(rig, path, camEye)
    drawGuruvayurTempleAndStandGround(rig, path, camEye)
    drawChavakkadBeachGround(rig, path, camEye)
    drawTriprayarBeachGround(rig, path, camEye)
}

internal fun collectThrissurLandmarkSceneFaces(
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>,
    outFaces: MutableList<QuadFace3D>
) {
    collectSwarajRoundFaces(camEye, roads, outFaces)
    collectVadakkeBusStandFaces(camEye, outFaces)
    collectPaliyekkaraTollFaces(camEye, outFaces)
    collectGuruvayurFaces(camEye, outFaces)
    collectChavakkadFaces(camEye, outFaces)
    collectTriprayarFaces(camEye, outFaces)
    collectRoadsideSceneryFaces(camEye, roads, outFaces)
    collectNhaiSignboardFaces(camEye, roads, outFaces)
}

private fun addSingleBoxFaces(
    faces: MutableList<QuadFace3D>,
    minX: Float,
    minY: Float,
    minZ: Float,
    maxX: Float,
    maxY: Float,
    maxZ: Float,
    topCol: Color,
    sideCol: Color,
    frontBackCol: Color,
    stroke: Color?,
    bias: Float,
    emitWest: Boolean,
    emitEast: Boolean,
    emitSouth: Boolean,
    emitNorth: Boolean
) {
    // Top face (+Y normal): only visible when camera Y > maxY
    faces.add(
        QuadFace3D(
            Vec3(minX, maxY, minZ),
            Vec3(maxX, maxY, minZ),
            Vec3(maxX, maxY, maxZ),
            Vec3(minX, maxY, maxZ),
            topCol,
            stroke,
            bias,
            0f,
            0f, 1f, 0f
        )
    )
    // Underside ceiling face (-Y normal) for elevated canopies: only visible when camera Y < minY
    if (minY >= 3.5f) {
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, minZ),
                Vec3(maxX, minY, minZ),
                Vec3(maxX, minY, maxZ),
                Vec3(minX, minY, maxZ),
                MatGraniteDark,
                stroke,
                bias + 0.4f,
                0f,
                0f, -1f, 0f
            )
        )
    }
    // South face (-Z normal): only visible when camera Z < minZ
    if (emitSouth) {
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, minZ),
                Vec3(maxX, minY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(minX, maxY, minZ),
                frontBackCol,
                stroke,
                bias,
                0f,
                0f, 0f, -1f
            )
        )
    }
    // North face (+Z normal): only visible when camera Z > maxZ
    if (emitNorth) {
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, maxZ),
                Vec3(maxX, minY, maxZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(minX, maxY, maxZ),
                frontBackCol,
                stroke,
                bias,
                0f,
                0f, 0f, 1f
            )
        )
    }
    // West face (-X normal): only visible when camera X < minX
    if (emitWest) {
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, minZ),
                Vec3(minX, maxY, minZ),
                Vec3(minX, maxY, maxZ),
                Vec3(minX, minY, maxZ),
                sideCol,
                stroke,
                bias,
                0f,
                -1f, 0f, 0f
            )
        )
    }
    // East face (+X normal): only visible when camera X > maxX
    if (emitEast) {
        faces.add(
            QuadFace3D(
                Vec3(maxX, minY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(maxX, minY, maxZ),
                sideCol,
                stroke,
                bias,
                0f,
                1f, 0f, 0f
            )
        )
    }
}

private fun addBoxFaces(
    faces: MutableList<QuadFace3D>,
    minX: Float,
    minY: Float,
    minZ: Float,
    maxX: Float,
    maxY: Float,
    maxZ: Float,
    topCol: Color,
    sideCol: Color,
    frontBackCol: Color = sideCol,
    stroke: Color? = Color(0x22000000),
    bias: Float = 0f
) {
    val spanX = maxX - minX
    val spanZ = maxZ - minZ
    // Segment long walls, arcades, platforms, and elevated canopies along their long axis for crisp Painter depth sorting
    if (spanX > 13f || spanZ > 13f) {
        val effBias = if (minY >= 3.8f) bias + 1.8f else bias
        if (spanX >= spanZ) {
            val segs = ((spanX / 8.0f).toInt()).coerceIn(2, 12)
            val stepX = spanX / segs
            for (s in 0 until segs) {
                val sx0 = minX + s * stepX
                val sx1 = sx0 + stepX
                addSingleBoxFaces(
                    faces, sx0, minY, minZ, sx1, maxY, maxZ,
                    topCol, sideCol, frontBackCol, stroke, effBias,
                    emitWest = (s == 0), emitEast = (s == segs - 1),
                    emitSouth = true, emitNorth = true
                )
            }
        } else {
            val segs = ((spanZ / 8.0f).toInt()).coerceIn(2, 12)
            val stepZ = spanZ / segs
            for (s in 0 until segs) {
                val sz0 = minZ + s * stepZ
                val sz1 = sz0 + stepZ
                addSingleBoxFaces(
                    faces, minX, minY, sz0, maxX, maxY, sz1,
                    topCol, sideCol, frontBackCol, stroke, effBias,
                    emitWest = true, emitEast = true,
                    emitSouth = (s == 0), emitNorth = (s == segs - 1)
                )
            }
        }
    } else {
        addSingleBoxFaces(
            faces, minX, minY, minZ, maxX, maxY, maxZ,
            topCol, sideCol, frontBackCol, stroke, bias,
            emitWest = true, emitEast = true, emitSouth = true, emitNorth = true
        )
    }
}

/**
 * Builds a pitched 4-hipped traditional Kerala roof pyramid frustum (for Gopurams, Sreekovils, Pavilions).
 */
private fun addPitchedKeralaRoof(
    faces: MutableList<QuadFace3D>,
    minX: Float,
    maxX: Float,
    minZ: Float,
    maxZ: Float,
    baseY: Float,
    ridgeY: Float,
    insetX: Float,
    insetZ: Float,
    roofColor: Color,
    eaveColor: Color = MatTeakWoodCarved
) {
    val rMinX = minX + insetX
    val rMaxX = maxX - insetX
    val rMinZ = minZ + insetZ
    val rMaxZ = maxZ - insetZ

    // South slope (-Z)
    faces.add(
        QuadFace3D(
            Vec3(minX, baseY, minZ),
            Vec3(maxX, baseY, minZ),
            Vec3(rMaxX, ridgeY, rMinZ),
            Vec3(rMinX, ridgeY, rMinZ),
            roofColor,
            eaveColor
        )
    )
    // North slope (+Z)
    faces.add(
        QuadFace3D(
            Vec3(maxX, baseY, maxZ),
            Vec3(minX, baseY, maxZ),
            Vec3(rMinX, ridgeY, rMaxZ),
            Vec3(rMaxX, ridgeY, rMaxZ),
            roofColor,
            eaveColor
        )
    )
    // West slope (-X)
    faces.add(
        QuadFace3D(
            Vec3(minX, baseY, maxZ),
            Vec3(minX, baseY, minZ),
            Vec3(rMinX, ridgeY, rMinZ),
            Vec3(rMinX, ridgeY, rMaxZ),
            roofColor,
            eaveColor
        )
    )
    // East slope (+X)
    faces.add(
        QuadFace3D(
            Vec3(maxX, baseY, minZ),
            Vec3(maxX, baseY, maxZ),
            Vec3(rMaxX, ridgeY, rMaxZ),
            Vec3(rMaxX, ridgeY, rMinZ),
            roofColor,
            eaveColor
        )
    )
    // Ridge cap (+Y normal)
    faces.add(
        QuadFace3D(
            Vec3(rMinX, ridgeY, rMinZ),
            Vec3(rMaxX, ridgeY, rMinZ),
            Vec3(rMaxX, ridgeY, rMaxZ),
            Vec3(rMinX, ridgeY, rMaxZ),
            MatTempleGoldDeep,
            null,
            0f,
            0f,
            0f, 1f, 0f
        )
    )
}

private fun addStandBannerQuadFace(
    faces: MutableList<QuadFace3D>,
    cacheKey: String,
    leftVec: Vec3,
    rightVec: Vec3,
    bottomY: Float,
    topY: Float,
    bgColor: Color,
    borderColor: Color,
    titleText: String
) {
    faces.add(
        QuadFace3D(
            v0 = Vec3(leftVec.x, bottomY, leftVec.z),
            v1 = Vec3(rightVec.x, bottomY, rightVec.z),
            v2 = Vec3(rightVec.x, topY, rightVec.z),
            v3 = Vec3(leftVec.x, topY, leftVec.z),
            color = bgColor,
            strokeColor = null,
            biasDist = -0.35f,
            signboard = createBannerSignSpec(
                cacheKey = cacheKey,
                bgColorInt = bgColor.toArgb(),
                borderColorInt = borderColor.toArgb(),
                titleText = titleText,
                titleColorInt = if (titleText == "ENTRY" || titleText == "EXIT") {
                    android.graphics.Color.WHITE
                } else {
                    0xFFFEF08A.toInt()
                },
                isDoubleSided = true,
                bmpWidth = 576,
                bmpHeight = 112
            )
        )
    )
}

// ============================================================================
// 1. SATELLITE-ACCURATE THRISSUR SWARAJ ROUND, THEKKINKADU MAIDAN TREE GROVE,
//    NEHRU PARK & SREE VADAKKUMNATHAN TEMPLE (Ref: image_9a4e2fdd.png & r1_3317f25d.jpeg)
// ============================================================================

private val MatMaidanLateriteSoil = Color(0xFF6B4C3B)
private val MatMaidanSoilLight = Color(0xFF7D5A47)
private val MatWalkwayAsphaltGrey = Color(0xFF737B84)
private val MatWalkwayBorder = Color(0xFF949CA6)
private val MatTreeTrunkBark = Color(0xFF3D2616)
private val MatTreeCanopyShadow = Color(0xFF1B3B22)
private val MatTreeCanopyMid = Color(0xFF254D2C)
private val MatTreeCanopyCrown = Color(0xFF316137)
private val MatTreeCanopyHighlight = Color(0xFF3D7344)

/**
 * Checks if an inner-circle point on ROAD_SWARAJ_ROUND falls inside one of the 4 cardinal
 * Gopuram processional approach walkways (Nadas) or Nehru Park gate so the laterite steps open cleanly.
 */
private fun isInsideMaidanNadaOpening(pt: Vec3, tcx: Float, tcz: Float): Boolean {
    val dx = pt.x - tcx
    val dz = pt.z - tcz
    // South Nada (aligned with M.O. Road & South Gopuram)
    if (dz < -14.0f && abs(dx - 3.5f) < 4.5f) return true
    // North Nada (aligned with Shornur Road & North Gopuram)
    if (dz > 14.0f && abs(dx - 1.5f) < 4.5f) return true
    // West Nada Double-Boulevard (aligned with M.G. Road / West Fort & West Gopuram)
    if (dx < -14.0f && abs(dz - (-3.5f)) < 5.5f) return true
    // East Nada (aligned with Thrissur-Palghat Road & East Gopuram)
    if (dx > 14.0f && abs(dz - (-2.5f)) < 4.5f) return true
    return false
}

/**
 * Draws a curved ribbon arc between [angleStartDeg..angleEndDeg] around (cx, cz) at [radiusInner..radiusOuter].
 */
private fun DrawScope.drawMaidanArcWalkway(
    rig: CameraRig,
    path: Path,
    cx: Float,
    cz: Float,
    radiusInner: Float,
    radiusOuter: Float,
    angleStartDeg: Float,
    angleEndDeg: Float,
    steps: Int,
    yHeight: Float,
    color: Color
) {
    for (i in 0 until steps) {
        val t0 = i.toFloat() / steps
        val t1 = (i + 1).toFloat() / steps
        val a0 = Math.toRadians((angleStartDeg + (angleEndDeg - angleStartDeg) * t0).toDouble()).toFloat()
        val a1 = Math.toRadians((angleStartDeg + (angleEndDeg - angleStartDeg) * t1).toDouble()).toFloat()
        val v0 = Vec3(cx + cos(a0) * radiusInner, yHeight, cz + sin(a0) * radiusInner)
        val v1 = Vec3(cx + cos(a0) * radiusOuter, yHeight, cz + sin(a0) * radiusOuter)
        val v2 = Vec3(cx + cos(a1) * radiusOuter, yHeight, cz + sin(a1) * radiusOuter)
        val v3 = Vec3(cx + cos(a1) * radiusInner, yHeight, cz + sin(a1) * radiusInner)
        if (rig.buildClippedQuadPath(path, v0, v1, v2, v3)) {
            drawPath(path, color)
        }
    }
}

private fun DrawScope.drawSwarajRoundGroundPlaza(
    rig: CameraRig,
    path: Path,
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>
) {
    val cx = 3.1f
    val cz = 9.5f
    if (hypot(camEye.x - cx, camEye.z - cz) > 380f) return

    // Temple center is slightly South-Center of Thekkinkadu Maidan (matching image_9a4e2fdd.png)
    val tcx = 1.8f
    val tcz = 4.5f

    val swarajRoad = roads.firstOrNull { it.id == "ROAD_SWARAJ_ROUND" }
    val frames = swarajRoad?.frames

    // 1. Base Thekkinkadu Maidan Reddish-Earth & Turf Ground (Ref: image_9a4e2fdd.png)
    if (frames != null && frames.size >= 4) {
        val centerTop = Vec3(cx, 0.44f, cz)
        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            val inOff0 = f0.halfWidthM + 1.85f
            val inOff1 = f1.halfWidthM + 1.85f
            val p0 = Vec3(f0.pos.x + f0.right.x * inOff0, 0.42f, f0.pos.z + f0.right.z * inOff0)
            val p1 = Vec3(f1.pos.x + f1.right.x * inOff1, 0.42f, f1.pos.z + f1.right.z * inOff1)
            // Alternate open reddish laterite Pooram ground on West/South with lush green turf in Center/NE
            val midX = (p0.x + p1.x) * 0.5f - cx
            val midZ = (p0.z + p1.z) * 0.5f - cz
            val sectorCol = if (midX < -8.0f || midZ < -10.0f) MatMaidanLateriteSoil else MatMaidanGrass
            if (rig.buildClippedQuadPath(path, centerTop, p0, p1, centerTop)) {
                drawPath(path, sectorCol)
            }
        }

        // Outer Ring Pedestrian Sidewalk along Round North, East, South, West (stops at radial road junctions)
        var runStart = 0
        while (runStart < frames.lastIndex) {
            while (runStart < frames.lastIndex && frames[runStart].leftEdgeCovered) runStart++
            if (runStart >= frames.lastIndex) break
            var runEnd = runStart + 1
            while (runEnd < frames.lastIndex && !frames[runEnd].leftEdgeCovered) runEnd++
            if (rig.buildVariableRibbonPath(
                    path = path,
                    frames = frames,
                    startIdx = runStart,
                    endIdx = runEnd,
                    offsetLFn = { -it.halfWidthM - 3.80f },
                    offsetRFn = { -it.halfWidthM - 0.32f },
                    yHeight = 0.065f
                )
            ) {
                drawPath(path, MatPromenadeTile)
            }
            runStart = runEnd
        }
    }

    // 2. Inner Forested Canopy Turf Zone around Sree Vadakkumnathan Temple & Nehru Park (Dark Green Under-Canopy Ground)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx - 21.5f, 0.45f, tcz - 20.5f),
            Vec3(tcx + 22.5f, 0.45f, tcz - 20.5f),
            Vec3(tcx + 24.5f, 0.45f, tcz + 26.5f),
            Vec3(tcx - 19.5f, 0.45f, tcz + 26.5f)
        )
    ) {
        drawPath(path, Color(0xFF2B472C))
    }

    // 3. Concentric Curved Grey Pedestrian Walkways across Thekkinkadu Maidan (Exact match to image_9a4e2fdd.png!)
    //    - Twin South & South-East Curved Walkways (sweeping from SW at -145° around South & East to +10°)
    drawMaidanArcWalkway(rig, path, cx, cz, 25.2f, 27.0f, -148f, 12f, 20, 0.46f, MatWalkwayAsphaltGrey)
    drawMaidanArcWalkway(rig, path, cx, cz, 29.2f, 30.8f, -142f, 8f, 20, 0.46f, MatWalkwayAsphaltGrey)
    //    - Twin North-West Curved Walkways (sweeping from West +105° to North +195° near Naikkanal Junction)
    drawMaidanArcWalkway(rig, path, cx, cz, 24.5f, 26.3f, 102f, 196f, 16, 0.46f, MatWalkwayAsphaltGrey)
    drawMaidanArcWalkway(rig, path, cx, cz, 28.5f, 30.2f, 106f, 192f, 16, 0.46f, MatWalkwayAsphaltGrey)
    //    - Nehru Park Looping Garden Walkways in North-East Quadrant (cx + 6..26, cz + 10..32)
    drawMaidanArcWalkway(rig, path, cx + 14.5f, cz + 19.5f, 5.5f, 6.8f, 0f, 360f, 16, 0.465f, MatWalkwayBorder)
    drawMaidanArcWalkway(rig, path, cx + 15.5f, cz + 20.5f, 9.5f, 10.8f, -45f, 215f, 14, 0.465f, MatWalkwayBorder)

    // 4. Outer Pradakshinapatha Stone Walkway Ring around Sree Vadakkumnathan Temple Wall (with NE loop from image_9a4e2fdd.png)
    val wR = 11.8f
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx - wR - 2.6f, 0.47f, tcz - wR - 2.6f),
            Vec3(tcx + wR + 2.6f, 0.47f, tcz - wR - 2.6f),
            Vec3(tcx + wR + 2.6f, 0.47f, tcz + wR + 2.6f),
            Vec3(tcx - wR - 2.6f, 0.47f, tcz + wR + 2.6f)
        )
    ) {
        drawPath(path, MatWalkwayAsphaltGrey)
    }
    // North-East Pradakshinapatha Corner Loop (distinctive diagonal loop at NE corner of Temple in image_9a4e2fdd.png)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx + wR, 0.47f, tcz + wR + 1.0f),
            Vec3(tcx + wR + 8.5f, 0.47f, tcz + wR + 7.5f),
            Vec3(tcx + wR + 10.5f, 0.47f, tcz + wR + 5.0f),
            Vec3(tcx + wR + 2.0f, 0.47f, tcz + wR - 1.5f)
        )
    ) {
        drawPath(path, MatWalkwayAsphaltGrey)
    }

    // 5. Sacred Temple Pond (Vadakkumnathan Kulam) just North of the Temple inside the Tree Grove (image_9a4e2fdd.png)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx - 2.5f, 0.46f, tcz + wR + 5.0f),
            Vec3(tcx + 5.5f, 0.46f, tcz + wR + 5.0f),
            Vec3(tcx + 5.5f, 0.46f, tcz + wR + 12.5f),
            Vec3(tcx - 2.5f, 0.46f, tcz + wR + 12.5f)
        )
    ) {
        drawPath(path, MatTemplePondWater)
    }
}

/**
 * Adds a realistic 3D tropical Kerala tree (Banyan / Teak / Peepal) with a 3D bark trunk and
 * multi-tiered 3D domed/pitched foliage canopy so Sree Vadakkumnathan Temple sits inside a lush
 * sacred forest grove (Thekkinkadu) visible from both street level and top-down satellite view.
 */
private fun addRealisticMaidanTree3D(
    faces: MutableList<QuadFace3D>,
    tx: Float,
    tz: Float,
    baseY: Float,
    crownRadius: Float,
    treeHeight: Float
) {
    val trunkHalfW = (crownRadius * 0.16f).coerceIn(0.28f, 0.65f)
    val trunkTopY = baseY + treeHeight * 0.42f
    // 1. 3D Tree Trunk
    addBoxFaces(
        faces,
        tx - trunkHalfW, baseY, tz - trunkHalfW,
        tx + trunkHalfW, trunkTopY, tz + trunkHalfW,
        topCol = MatTreeTrunkBark,
        sideCol = MatTreeTrunkBark,
        frontBackCol = MatTeakWoodDeep,
        stroke = null,
        bias = 1.5f
    )

    // 2. Lower Spreading Bough Canopy Tier (Wide dark-green under-canopy)
    val r1 = crownRadius
    val y1Bot = baseY + treeHeight * 0.34f
    val y1Top = baseY + treeHeight * 0.66f
    addPitchedKeralaRoof(
        faces,
        tx - r1, tx + r1,
        tz - r1, tz + r1,
        baseY = y1Bot,
        ridgeY = y1Top,
        insetX = r1 * 0.28f,
        insetZ = r1 * 0.28f,
        roofColor = MatTreeCanopyShadow,
        eaveColor = MatTreeCanopyShadow
    )

    // 3. Middle Lush Foliage Canopy Tier
    val r2 = crownRadius * 0.82f
    val y2Bot = baseY + treeHeight * 0.56f
    val y2Top = baseY + treeHeight * 0.86f
    addPitchedKeralaRoof(
        faces,
        tx - r2, tx + r2,
        tz - r2, tz + r2,
        baseY = y2Bot,
        ridgeY = y2Top,
        insetX = r2 * 0.35f,
        insetZ = r2 * 0.35f,
        roofColor = MatTreeCanopyMid,
        eaveColor = MatTreeCanopyShadow
    )

    // 4. Upper Sunlit Crown Cap
    val r3 = crownRadius * 0.58f
    val y3Bot = baseY + treeHeight * 0.78f
    val y3Top = baseY + treeHeight
    addPitchedKeralaRoof(
        faces,
        tx - r3, tx + r3,
        tz - r3, tz + r3,
        baseY = y3Bot,
        ridgeY = y3Top,
        insetX = r3 * 0.48f,
        insetZ = r3 * 0.48f,
        roofColor = MatTreeCanopyCrown,
        eaveColor = MatTreeCanopyHighlight
    )
}

private var cachedSwarajRoundFaces: List<QuadFace3D>? = null

private fun collectSwarajRoundFaces(
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>,
    outFaces: MutableList<QuadFace3D>
) {
    val cx = 3.1f
    val cz = 9.5f
    val dx = camEye.x - cx
    val dz = camEye.z - cz
    if (dx * dx + dz * dz > 205f * 205f) return

    val existing = cachedSwarajRoundFaces
    if (existing != null) {
        outFaces.addAll(existing)
        return
    }

    // Temple center inside Thekkinkadu Maidan (matching satellite image_9a4e2fdd.png)
    val tcx = 1.8f
    val tcz = 4.5f
    val wR = 11.8f // Realistic compact Maryada wall radius so the surrounding Maidan & tree grove are expansive!
    val templeBaseY = 0.62f

    val faces = ArrayList<QuadFace3D>(920)
    val swarajFrames = roads.firstOrNull { it.id == "ROAD_SWARAJ_ROUND" }?.frames

    // =========================================================================
    // A. THEKKINKADU MAIDAN 3-STEPPED LATERITE STONE TERRACES (Ref: r1_3317f25d.jpeg)
    //    Built directly along the inner edge of ROAD_SWARAJ_ROUND (+right),
    //    with 4 clean openings for the 4 Gopuram processional avenues!
    // =========================================================================
    if (swarajFrames != null && swarajFrames.size >= 4) {
        val stepConfigs = arrayOf(
            floatArrayOf(0.32f, 0.90f, 0.06f, 0.19f),
            floatArrayOf(0.90f, 1.48f, 0.19f, 0.32f),
            floatArrayOf(1.48f, 2.06f, 0.32f, 0.45f)
        )
        for (i in 0 until swarajFrames.size - 1) {
            val f0 = swarajFrames[i]
            val f1 = swarajFrames[i + 1]
            val midCheck = Vec3(
                (f0.pos.x + f1.pos.x) * 0.5f + f0.right.x * (f0.halfWidthM + 1.2f),
                0.2f,
                (f0.pos.z + f1.pos.z) * 0.5f + f0.right.z * (f0.halfWidthM + 1.2f)
            )
            if (isInsideMaidanNadaOpening(midCheck, tcx, tcz)) continue

            for (sIdx in stepConfigs.indices) {
                val cfg = stepConfigs[sIdx]
                val dOut0 = f0.halfWidthM + cfg[0]
                val dIn0 = f0.halfWidthM + cfg[1]
                val dOut1 = f1.halfWidthM + cfg[0]
                val dIn1 = f1.halfWidthM + cfg[1]
                val yBot = cfg[2]
                val yTop = cfg[3]

                val pOut0 = Vec3(f0.pos.x + f0.right.x * dOut0, yTop, f0.pos.z + f0.right.z * dOut0)
                val pOut1 = Vec3(f1.pos.x + f1.right.x * dOut1, yTop, f1.pos.z + f1.right.z * dOut1)
                val pIn1 = Vec3(f1.pos.x + f1.right.x * dIn1, yTop, f1.pos.z + f1.right.z * dIn1)
                val pIn0 = Vec3(f0.pos.x + f0.right.x * dIn0, yTop, f0.pos.z + f0.right.z * dIn0)

                faces.add(
                    QuadFace3D(
                        pOut0, pOut1, pIn1, pIn0,
                        color = if (sIdx == 2) MatGranitePaving else MatLateriteStepTop,
                        strokeColor = Color(0x28000000),
                        biasDist = 4.0f
                    )
                )
                faces.add(
                    QuadFace3D(
                        Vec3(pOut0.x, yBot, pOut0.z),
                        Vec3(pOut1.x, yBot, pOut1.z),
                        pOut1,
                        pOut0,
                        color = if (sIdx % 2 == 0) MatLateriteStone else MatLateriteDark,
                        strokeColor = Color(0x33000000),
                        biasDist = 3.8f
                    )
                )
            }
        }
    }

    // =========================================================================
    // B. 4 PAVED PROCESSIONAL AVENUES (NADAS) CONNECTING SWARAJ ROUND TO THE 4 GOPURAMS
    //    (Including the Divided Double-Lane West Avenue visible in image_9a4e2fdd.png!)
    // =========================================================================
    fun addProcessionalAvenue(vRoadL: Vec3, vRoadR: Vec3, vGateR: Vec3, vGateL: Vec3) {
        faces.add(
            QuadFace3D(
                vRoadL, vRoadR, vGateR, vGateL,
                color = MatWalkwayAsphaltGrey,
                strokeColor = MatWalkwayBorder,
                biasDist = 2.8f
            )
        )
    }

    // 1. South Processional Avenue (M.O. Road / Ragam Theatre side -> South Gopuram)
    addProcessionalAvenue(
        vRoadL = Vec3(tcx + 0.5f, 0.08f, cz - 34.8f),
        vRoadR = Vec3(tcx + 5.5f, 0.08f, cz - 34.8f),
        vGateR = Vec3(tcx + 5.0f, templeBaseY, tcz - wR),
        vGateL = Vec3(tcx + 1.0f, templeBaseY, tcz - wR)
    )
    // 2. North Processional Avenue (Swaraj Round N / Shornur Road -> North Gopuram)
    addProcessionalAvenue(
        vRoadL = Vec3(tcx + 3.2f, 0.08f, cz + 35.8f),
        vRoadR = Vec3(tcx - 1.8f, 0.08f, cz + 35.8f),
        vGateR = Vec3(tcx - 1.5f, templeBaseY, tcz + wR),
        vGateL = Vec3(tcx + 2.5f, templeBaseY, tcz + wR)
    )
    // 3. West Divided Double-Boulevard Avenue (Swaraj Round S/W Rd -> West Gopuram, Ref: image_9a4e2fdd.png)
    addProcessionalAvenue(
        vRoadL = Vec3(cx - 35.2f, 0.08f, tcz - 0.2f),
        vRoadR = Vec3(cx - 35.2f, 0.08f, tcz - 2.6f),
        vGateR = Vec3(tcx - wR - 2.0f, templeBaseY, tcz - 2.4f),
        vGateL = Vec3(tcx - wR - 2.0f, templeBaseY, tcz - 0.4f)
    )
    addProcessionalAvenue(
        vRoadL = Vec3(cx - 35.2f, 0.08f, tcz - 4.2f),
        vRoadR = Vec3(cx - 35.2f, 0.08f, tcz - 6.6f),
        vGateR = Vec3(tcx - wR - 2.0f, templeBaseY, tcz - 6.4f),
        vGateL = Vec3(tcx - wR - 2.0f, templeBaseY, tcz - 4.4f)
    )
    // Diagonal NW walkway from Naikkanal Junction to West Gopuram (clearly visible in image_9a4e2fdd.png!)
    addProcessionalAvenue(
        vRoadL = Vec3(cx - 25.5f, 0.46f, cz + 26.5f),
        vRoadR = Vec3(cx - 23.2f, 0.46f, cz + 27.5f),
        vGateR = Vec3(tcx - wR - 1.5f, templeBaseY, tcz + 1.5f),
        vGateL = Vec3(tcx - wR - 3.5f, templeBaseY, tcz + 0.5f)
    )
    // 4. East Processional Avenue (Swaraj Round East -> East Gopuram)
    addProcessionalAvenue(
        vRoadL = Vec3(cx + 34.5f, 0.08f, tcz - 4.8f),
        vRoadR = Vec3(cx + 34.5f, 0.08f, tcz - 0.2f),
        vGateR = Vec3(tcx + wR, templeBaseY, tcz - 0.5f),
        vGateL = Vec3(tcx + wR, templeBaseY, tcz - 4.5f)
    )

    // =========================================================================
    // C. SREE VADAKKUMNATHAN TEMPLE COMPOUND (NESTLED INSIDE THE TREE GROVE)
    //    (Maryada Wall + 4 Kerala Gopurams + Rectangular Nalambalam + Sreekovils + Koothambalam)
    // =========================================================================
    // Inner Temple Courtyard Stone Paving
    addBoxFaces(
        faces,
        tcx - wR + 0.4f, 0.45f, tcz - wR + 0.4f,
        tcx + wR - 0.4f, templeBaseY, tcz + wR - 0.4f,
        topCol = MatGranitePaving,
        sideCol = MatGraniteDark,
        bias = 4.5f
    )

    fun addMaryadaWallSegment(minX: Float, minZ: Float, maxX: Float, maxZ: Float, isNorthSouth: Boolean) {
        addBoxFaces(
            faces,
            minX, templeBaseY, minZ,
            maxX, templeBaseY + 1.45f, maxZ,
            topCol = MatKeralaWhitewash,
            sideCol = MatKeralaWhitewash,
            frontBackCol = MatKeralaCreamWall
        )
        addBoxFaces(
            faces,
            minX, templeBaseY + 1.45f, minZ,
            maxX, templeBaseY + 2.30f, maxZ,
            topCol = MatLateriteStone,
            sideCol = MatLateriteStone,
            frontBackCol = MatLateriteDark
        )
        val overhang = 0.24f
        addPitchedKeralaRoof(
            faces,
            minX - overhang, maxX + overhang,
            minZ - overhang, maxZ + overhang,
            baseY = templeBaseY + 2.25f,
            ridgeY = templeBaseY + 2.75f,
            insetX = if (isNorthSouth) 0.12f else (maxX - minX) * 0.45f,
            insetZ = if (isNorthSouth) (maxZ - minZ) * 0.45f else 0.12f,
            roofColor = MatTerracottaTile
        )
    }

    // South Maryada Wall (split around South Gopuram at tcx + 3.0m)
    addMaryadaWallSegment(tcx - wR, tcz - wR, tcx - 0.6f, tcz - wR + 0.8f, isNorthSouth = true)
    addMaryadaWallSegment(tcx + 6.6f, tcz - wR, tcx + wR, tcz - wR + 0.8f, isNorthSouth = true)
    // North Maryada Wall (split around North Gopuram at tcx + 0.5m)
    addMaryadaWallSegment(tcx - wR, tcz + wR - 0.8f, tcx - 3.1f, tcz + wR, isNorthSouth = true)
    addMaryadaWallSegment(tcx + 4.1f, tcz + wR - 0.8f, tcx + wR, tcz + wR, isNorthSouth = true)
    // West Maryada Wall (split around West Gopuram at tcz - 3.4m)
    addMaryadaWallSegment(tcx - wR, tcz - wR, tcx - wR + 0.8f, tcz - 7.0f, isNorthSouth = false)
    addMaryadaWallSegment(tcx - wR, tcz + 0.2f, tcx - wR + 0.8f, tcz + wR, isNorthSouth = false)
    // East Maryada Wall (split around East Gopuram at tcz - 2.5m)
    addMaryadaWallSegment(tcx + wR - 0.8f, tcz - wR, tcx + wR, tcz - 6.1f, isNorthSouth = false)
    addMaryadaWallSegment(tcx + wR - 0.8f, tcz + 1.1f, tcx + wR, tcz + wR, isNorthSouth = false)

    // 4 Authentic 3-Tiered Kerala Gopurams (Proportioned to sit majestically inside the tree canopy)
    fun addAuthenticVadakkumnathanGopuram(
        gx: Float,
        gz: Float,
        halfW: Float,
        halfD: Float,
        facesNorthSouth: Boolean
    ) {
        val y0 = templeBaseY
        addBoxFaces(
            faces,
            gx - halfW - 0.25f, y0, gz - halfD - 0.25f,
            gx + halfW + 0.25f, y0 + 0.50f, gz + halfD + 0.25f,
            topCol = MatGraniteLight,
            sideCol = MatGraniteDark,
            frontBackCol = MatGranitePaving
        )

        if (facesNorthSouth) {
            val doorHalfW = 1.05f
            addBoxFaces(faces, gx - halfW, y0 + 0.50f, gz - halfD, gx - doorHalfW, y0 + 3.40f, gz + halfD, MatTeakWoodCarved, MatKeralaCreamWall, MatKeralaWhitewash)
            addBoxFaces(faces, gx + doorHalfW, y0 + 0.50f, gz - halfD, gx + halfW, y0 + 3.40f, gz + halfD, MatTeakWoodCarved, MatKeralaCreamWall, MatKeralaWhitewash)
            addBoxFaces(faces, gx - doorHalfW, y0 + 0.50f, gz - halfD + 0.20f, gx + doorHalfW, y0 + 3.40f, gz + halfD - 0.20f, MatTeakWoodDeep, MatTeakWoodDeep, MatTeakWoodDeep)
        } else {
            val doorHalfD = 1.05f
            addBoxFaces(faces, gx - halfW, y0 + 0.50f, gz - halfD, gx + halfW, y0 + 3.40f, gz - doorHalfD, MatTeakWoodCarved, MatKeralaWhitewash, MatKeralaCreamWall)
            addBoxFaces(faces, gx - halfW, y0 + 0.50f, gz + doorHalfD, gx + halfW, y0 + 3.40f, gz + halfD, MatTeakWoodCarved, MatKeralaWhitewash, MatKeralaCreamWall)
            addBoxFaces(faces, gx - halfW + 0.20f, y0 + 0.50f, gz - doorHalfD, gx + halfW - 0.20f, y0 + 3.40f, gz + doorHalfD, MatTeakWoodDeep, MatTeakWoodDeep, MatTeakWoodDeep)
        }

        // Tier 1 Sloped Roof
        addPitchedKeralaRoof(
            faces,
            gx - halfW - 1.05f, gx + halfW + 1.05f,
            gz - halfD - 1.05f, gz + halfD + 1.05f,
            baseY = y0 + 3.25f, ridgeY = y0 + 5.10f,
            insetX = 1.20f, insetZ = 1.20f,
            roofColor = MatTerracottaRoofDark,
            eaveColor = MatTeakWoodCarved
        )
        // Tier 2 Carved Teakwood Hall + Sloped Roof
        addBoxFaces(
            faces,
            gx - halfW * 0.78f, y0 + 4.95f, gz - halfD * 0.78f,
            gx + halfW * 0.78f, y0 + 6.60f, gz + halfD * 0.78f,
            topCol = MatTeakWoodCarved,
            sideCol = MatTeakWoodCarved,
            frontBackCol = MatTeakWoodCarved
        )
        addPitchedKeralaRoof(
            faces,
            gx - halfW * 0.98f, gx + halfW * 0.98f,
            gz - halfD * 0.98f, gz + halfD * 0.98f,
            baseY = y0 + 6.45f, ridgeY = y0 + 8.35f,
            insetX = 1.10f, insetZ = 1.10f,
            roofColor = MatCopperRoof,
            eaveColor = MatTeakWoodCarved
        )
        // Tier 3 Uppermost Steep Roof + Central Mukhappu Gable
        addPitchedKeralaRoof(
            faces,
            gx - halfW * 0.76f, gx + halfW * 0.76f,
            gz - halfD * 0.76f, gz + halfD * 0.76f,
            baseY = y0 + 8.20f, ridgeY = y0 + 10.65f,
            insetX = if (facesNorthSouth) halfW * 0.28f else halfW * 0.68f,
            insetZ = if (facesNorthSouth) halfD * 0.68f else halfD * 0.28f,
            roofColor = MatTerracottaRoofDark,
            eaveColor = MatKeralaWhitewash
        )
        val mukW = if (facesNorthSouth) 1.25f else halfW * 0.70f
        val mukD = if (facesNorthSouth) halfD * 0.70f else 1.25f
        addPitchedKeralaRoof(
            faces,
            gx - mukW, gx + mukW,
            gz - mukD, gz + mukD,
            baseY = y0 + 7.80f, ridgeY = y0 + 10.25f,
            insetX = if (facesNorthSouth) mukW * 0.85f else 0.20f,
            insetZ = if (facesNorthSouth) 0.20f else mukD * 0.85f,
            roofColor = MatCopperRoofDark,
            eaveColor = MatKeralaWhitewash
        )
    }

    addAuthenticVadakkumnathanGopuram(tcx + 3.0f, tcz - wR, halfW = 3.8f, halfD = 2.3f, facesNorthSouth = true)   // South Gopuram
    addAuthenticVadakkumnathanGopuram(tcx + 0.5f, tcz + wR, halfW = 3.8f, halfD = 2.3f, facesNorthSouth = true)   // North Gopuram
    addAuthenticVadakkumnathanGopuram(tcx - wR, tcz - 3.4f, halfW = 2.3f, halfD = 3.8f, facesNorthSouth = false)  // West Gopuram
    addAuthenticVadakkumnathanGopuram(tcx + wR, tcz - 2.5f, halfW = 2.3f, halfD = 3.8f, facesNorthSouth = false)  // East Gopuram

    // Inner Rectangular Nalambalam Cloister & Sreekovils (Ref: image_9a4e2fdd.png center-south rectangle)
    val nalW = 6.2f
    val nalD = 7.6f
    addBoxFaces(
        faces,
        tcx - nalW, templeBaseY + 0.05f, tcz - nalD,
        tcx + nalW, templeBaseY + 2.60f, tcz + nalD,
        topCol = MatTeakWoodCarved,
        sideCol = MatKeralaWhitewash,
        frontBackCol = MatKeralaCreamWall
    )
    addPitchedKeralaRoof(
        faces,
        tcx - nalW - 0.5f, tcx + nalW + 0.5f,
        tcz - nalD - 0.5f, tcz + nalD + 0.5f,
        baseY = templeBaseY + 2.50f,
        ridgeY = templeBaseY + 4.10f,
        insetX = 1.8f, insetZ = 1.8f,
        roofColor = MatTerracottaTile
    )
    // Conical Copper-Roofed Vadakkumnathan & Sri Rama Sreekovils inside Nalambalam
    addPitchedKeralaRoof(
        faces,
        tcx - 3.6f, tcx + 3.6f,
        tcz + 0.2f, tcz + 6.2f,
        baseY = templeBaseY + 3.85f,
        ridgeY = templeBaseY + 7.80f,
        insetX = 3.3f, insetZ = 2.8f,
        roofColor = MatCopperRoof
    )
    addPitchedKeralaRoof(
        faces,
        tcx - 3.2f, tcx + 3.2f,
        tcz - 6.2f, tcz - 0.6f,
        baseY = templeBaseY + 3.85f,
        ridgeY = templeBaseY + 7.10f,
        insetX = 2.9f, insetZ = 2.6f,
        roofColor = MatCopperRoofDark
    )
    // Koothambalam (Red-tiled rectangular temple theatre in NW temple courtyard, visible in image_9a4e2fdd.png)
    addBoxFaces(
        faces,
        tcx - 10.2f, templeBaseY + 0.05f, tcz + 1.8f,
        tcx - 7.0f, templeBaseY + 2.80f, tcz + 9.2f,
        topCol = MatTeakWoodCarved,
        sideCol = MatKeralaWhitewash,
        frontBackCol = MatTeakWoodCarved
    )
    addPitchedKeralaRoof(
        faces,
        tcx - 10.7f, tcx - 6.5f,
        tcz + 1.3f, tcz + 9.7f,
        baseY = templeBaseY + 2.70f,
        ridgeY = templeBaseY + 6.40f,
        insetX = 1.8f, insetZ = 2.4f,
        roofColor = MatTerracottaTile
    )

    // =========================================================================
    // D. LUSH 3D SACRED TREE CANOPY ("THEKKINKADU" FOREST GROVE) SURROUNDING
    //    THE TEMPLE, NEHRU PARK & MAIDAN PERIMETER (Ref: image_9a4e2fdd.png & r1_3317f25d.jpeg)
    //    (Leaves the 4 Nada avenues open so the 4 Gopurams are framed by the trees!)
    // =========================================================================
    val maidanTrees = arrayOf(
        // Inner Sacred Grove Ring directly surrounding Sree Vadakkumnathan Temple Compound Wall
        // South-West Grove (between West Nada & South Nada)
        floatArrayOf(tcx - 16.5f, tcz - 10.5f, 4.4f, 9.8f),
        floatArrayOf(tcx - 12.0f, tcz - 16.5f, 4.8f, 10.6f),
        floatArrayOf(tcx - 5.5f, tcz - 16.8f, 4.5f, 10.2f),
        floatArrayOf(tcx - 18.8f, tcz - 15.8f, 4.2f, 9.4f),
        // South-East Grove (between South Nada & East Nada)
        floatArrayOf(tcx + 10.2f, tcz - 16.5f, 4.6f, 10.4f),
        floatArrayOf(tcx + 15.8f, tcz - 14.5f, 4.8f, 10.8f),
        floatArrayOf(tcx + 16.8f, tcz - 8.8f, 4.4f, 9.8f),
        floatArrayOf(tcx + 12.5f, tcz - 19.8f, 4.0f, 9.0f),
        // North-East Grove (between East Nada, Temple Pond & Nehru Park)
        floatArrayOf(tcx + 16.5f, tcz + 4.2f, 4.8f, 10.8f),
        floatArrayOf(tcx + 15.5f, tcz + 11.2f, 5.0f, 11.2f),
        floatArrayOf(tcx + 10.5f, tcz + 16.2f, 4.6f, 10.4f),
        floatArrayOf(tcx + 11.8f, tcz + 22.0f, 4.8f, 10.6f),
        floatArrayOf(tcx + 6.5f, tcz + 21.5f, 4.4f, 9.8f),
        // North-West Grove (between North Nada & West Nada)
        floatArrayOf(tcx - 5.5f, tcz + 16.5f, 4.8f, 10.8f),
        floatArrayOf(tcx - 12.2f, tcz + 15.5f, 5.0f, 11.0f),
        floatArrayOf(tcx - 16.8f, tcz + 10.2f, 4.6f, 10.2f),
        floatArrayOf(tcx - 16.5f, tcz + 3.8f, 4.4f, 9.8f),
        floatArrayOf(tcx - 8.5f, tcz + 22.5f, 4.5f, 10.0f),
        floatArrayOf(tcx - 14.8f, tcz + 21.0f, 4.2f, 9.5f),

        // Nehru Park Lush Tree Canopy in North-East Quadrant of Thekkinkadu Maidan (Ref: image_9a4e2fdd.png)
        floatArrayOf(cx + 12.0f, cz + 25.0f, 4.5f, 10.2f),
        floatArrayOf(cx + 19.5f, cz + 24.5f, 4.8f, 10.6f),
        floatArrayOf(cx + 24.5f, cz + 18.0f, 4.4f, 9.8f),
        floatArrayOf(cx + 18.5f, cz + 14.0f, 4.6f, 10.4f),
        floatArrayOf(cx + 6.0f, cz + 28.5f, 4.2f, 9.6f),

        // Outer Maidan Perimeter Tree Clusters (SW corner, NW Naikkanal border, SE border — Ref: image_9a4e2fdd.png)
        floatArrayOf(cx - 24.5f, cz - 19.5f, 4.4f, 9.8f),
        floatArrayOf(cx - 18.5f, cz - 25.0f, 4.2f, 9.4f),
        floatArrayOf(cx - 28.5f, cz + 14.5f, 4.2f, 9.5f),
        floatArrayOf(cx - 20.5f, cz + 27.5f, 4.4f, 9.8f),
        floatArrayOf(cx + 22.5f, cz - 18.5f, 4.2f, 9.4f),
        floatArrayOf(cx + 27.0f, cz - 11.0f, 4.0f, 9.0f)
    )

    for (t in maidanTrees) {
        addRealisticMaidanTree3D(
            faces = faces,
            tx = t[0],
            tz = t[1],
            baseY = 0.45f,
            crownRadius = t[2],
            treeHeight = t[3]
        )
    }

    // =========================================================================
    // E. EAST-SIDE WATER AUTHORITY BUILDING & CIRCULAR TANKS + NEHRU PARK PAVILION
    //    (Clearly visible on the East & NE side inside Swaraj Round in image_9a4e2fdd.png!)
    // =========================================================================
    // White Rectangular Water Authority Building (East of Temple grove at cx + 19.5m..23.0m, cz - 1.0m..+8.5m)
    addBoxFaces(
        faces,
        cx + 19.2f, 0.46f, cz - 1.5f,
        cx + 22.8f, 3.20f, cz + 8.0f,
        topCol = MatPromenadeTile,
        sideCol = MatKeralaWhitewash,
        frontBackCol = MatKeralaCreamWall
    )
    // 3 White Circular/Octagonal Water Tanks next to the building (visible in image_9a4e2fdd.png)
    for ((wtX, wtZ) in arrayOf(
        Pair(cx + 25.0f, cz + 6.5f),
        Pair(cx + 25.0f, cz + 2.5f),
        Pair(cx + 28.2f, cz + 4.5f)
    )) {
        addBoxFaces(
            faces,
            wtX - 1.3f, 0.46f, wtZ - 1.3f,
            wtX + 1.3f, 2.40f, wtZ + 1.3f,
            topCol = MatFastagWhite,
            sideCol = MatPromenadeTile,
            frontBackCol = MatKeralaWhitewash
        )
    }

    // =========================================================================
    // F. SWARAJ ROUND OUTER RING LANDMARKS & HERITAGE ARCADES (Ref: image_9a4e2fdd.png)
    //    All positioned outside the outer sidewalk with wide clearance for all 4 radial roads!
    // =========================================================================
    // 1. PARAMEKKAVU BAGAVATHI TEMPLE (Round East — clear outer setback outside sidewalk & signboards)
    addBoxFaces(
        faces,
        69.5f, 0.06f, 10.0f,
        70.4f, 2.60f, 30.0f,
        topCol = MatTerracottaTile,
        sideCol = MatKeralaWhitewash,
        frontBackCol = MatLateriteStone
    )
    addBoxFaces(
        faces,
        68.8f, 0.06f, 16.5f,
        74.5f, 4.40f, 23.5f,
        topCol = MatTeakWoodCarved,
        sideCol = MatKeralaWhitewash,
        frontBackCol = MatKeralaCreamWall
    )
    addPitchedKeralaRoof(
        faces,
        67.8f, 75.5f,
        15.5f, 24.5f,
        baseY = 4.25f, ridgeY = 6.80f,
        insetX = 1.4f, insetZ = 1.4f,
        roofColor = MatTerracottaTile
    )
    addPitchedKeralaRoof(
        faces,
        68.6f, 74.7f,
        16.3f, 23.7f,
        baseY = 6.65f, ridgeY = 9.40f,
        insetX = 2.4f, insetZ = 2.2f,
        roofColor = MatTerracottaRoofDark
    )
    addBoxFaces(
        faces,
        66.2f, 0.06f, 19.5f,
        67.1f, 12.50f, 20.5f,
        topCol = MatTempleGold,
        sideCol = MatTempleGold,
        frontBackCol = MatTempleGoldDeep
    )

    // 2. INDIAN COFFEE HOUSE, RAGAM THEATRE & ROUND SOUTH HERITAGE ARCADES (set back outside MO Rd / Swaraj junction signs)
    addBoxFaces(
        faces,
        46.5f, 0.06f, -48.5f,
        56.5f, 8.60f, -37.5f,
        topCol = MatTerracottaRoofDark,
        sideCol = MatCoffeeHouseRed,
        frontBackCol = MatCoffeeHouseRed
    )
    addPitchedKeralaRoof(
        faces,
        45.7f, 57.3f,
        -49.3f, -36.7f,
        baseY = 8.45f, ridgeY = 10.60f,
        insetX = 4.2f, insetZ = 4.2f,
        roofColor = MatTerracottaTile
    )
    addBoxFaces(
        faces,
        -26.0f, 0.06f, -66.0f,
        -6.0f, 7.80f, -57.0f,
        topCol = MatArcadeRoofSlate,
        sideCol = MatArcadeCream,
        frontBackCol = MatArcadeSandstone
    )
    addPitchedKeralaRoof(
        faces,
        -26.6f, -5.4f,
        -66.6f, -56.4f,
        baseY = 7.70f, ridgeY = 9.50f,
        insetX = 2.5f, insetZ = 3.8f,
        roofColor = MatTerracottaTile
    )

    // 3. CITY CENTRE, DEVAKI DAIRY & ROUND WEST COMMERCIAL ARCADES
    addBoxFaces(
        faces,
        -70.5f, 0.06f, 15.0f,
        -60.0f, 8.40f, 38.0f,
        topCol = MatArcadeRoofSlate,
        sideCol = MatArcadeCream,
        frontBackCol = MatArcadeSandstone
    )
    addBoxFaces(
        faces,
        -60.2f, 0.50f, 16.5f,
        -59.8f, 3.60f, 36.5f,
        topCol = MatArcadeGlass,
        sideCol = MatArcadeGlass,
        frontBackCol = MatArcadeGlass
    )

    // 4. ROUND NORTH & SHORNUR ROAD JUNCTION HERITAGE BLOCKS
    addBoxFaces(
        faces,
        18.5f, 0.06f, 67.5f,
        41.5f, 7.60f, 76.5f,
        topCol = MatTerracottaRoofDark,
        sideCol = MatKeralaCreamWall,
        frontBackCol = MatKeralaWhitewash
    )
    addPitchedKeralaRoof(
        faces,
        17.8f, 42.2f,
        66.8f, 77.2f,
        baseY = 7.50f, ridgeY = 9.40f,
        insetX = 3.0f, insetZ = 4.0f,
        roofColor = MatTerracottaTile
    )

    if (swarajFrames != null) {
        cachedSwarajRoundFaces = faces
    }
    outFaces.addAll(faces)
}

// ============================================================================
// 2. GURUVAYUR SREE KRISHNA TEMPLE (EAST NADA) & GURUVAYUR BUS STAND
//    Aligned with ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD along Z = 1260.0m
// ============================================================================

private fun DrawScope.drawGuruvayurTempleAndStandGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - (-2700f), camEye.z - 1275f) > 420f) return

    // A. Guruvayur East Nada Pedestrian Stone Plaza (X = -2645m..-2565m, Z = 1232m..1288m)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2645.0f, 0.058f, 1232.0f),
            Vec3(-2565.0f, 0.058f, 1232.0f),
            Vec3(-2565.0f, 0.058f, 1288.0f),
            Vec3(-2645.0f, 0.058f, 1288.0f)
        )
    ) {
        drawPath(path, MatGranitePaving)
    }

    // Sacred Rudratheertham Temple Pond (North of Temple at X = -2628m..-2578m, Z = 1290m..1328m)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2630.0f, 0.057f, 1288.0f),
            Vec3(-2576.0f, 0.057f, 1288.0f),
            Vec3(-2576.0f, 0.057f, 1330.0f),
            Vec3(-2630.0f, 0.057f, 1330.0f)
        )
    ) {
        drawPath(path, MatLateriteStone)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2626.0f, 0.059f, 1292.0f),
            Vec3(-2580.0f, 0.059f, 1292.0f),
            Vec3(-2580.0f, 0.059f, 1326.0f),
            Vec3(-2626.0f, 0.059f, 1326.0f)
        )
    ) {
        drawPath(path, MatTemplePondWater)
    }

    // B. Guruvayur Municipal Bus Stand Concrete Terminal Apron
    val yardMinX = -2780.0f
    val yardMaxX = -2690.0f
    val yardMinZ = 1267.8f
    val yardMaxZ = 1326.0f
    if (rig.buildClippedQuadPath(
            path,
            Vec3(yardMinX, 0.058f, yardMinZ),
            Vec3(yardMaxX, 0.058f, yardMinZ),
            Vec3(yardMaxX, 0.058f, yardMaxZ),
            Vec3(yardMinX, 0.058f, yardMaxZ)
        )
    ) {
        drawPath(path, MatTerminalConcrete)
    }

    // 10 Yellow Bus Parking Bays inside Guruvayur Stand (Z = 1285m..1302m)
    for (bIdx in 0 until 10) {
        val bx = -2768.0f + bIdx * 7.6f
        val lSeg = rig.projectSegmentClipped(Vec3(bx - 2.0f, 0.062f, 1285.0f), Vec3(bx - 2.0f, 0.062f, 1302.0f))
        if (lSeg != null) drawLine(MatRoadMarkingYellow, lSeg.first, lSeg.second, strokeWidth = 2.4f)
        val rSeg = rig.projectSegmentClipped(Vec3(bx + 2.0f, 0.062f, 1285.0f), Vec3(bx + 2.0f, 0.062f, 1302.0f))
        if (rSeg != null) drawLine(MatRoadMarkingYellow, rSeg.first, rSeg.second, strokeWidth = 2.4f)
    }

    // Aligned Left Entry Driveway (X = -2713m..-2703m) & Right Exit Driveway (X = -2765m..-2755m)
    for (driveX in floatArrayOf(-2713.0f, -2703.0f, -2765.0f, -2755.0f)) {
        val seg = rig.projectSegmentClipped(Vec3(driveX, 0.063f, 1267.8f), Vec3(driveX, 0.063f, 1283.0f))
        if (seg != null) drawLine(MatRoadMarkingWhite, seg.first, seg.second, strokeWidth = 2.5f)
    }
}

private var cachedGuruvayurFaces: List<QuadFace3D>? = null

private fun collectGuruvayurFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - (-2700f)
    val dz = camEye.z - 1275f
    if (dx * dx + dz * dz > 210f * 210f) return

    var faces = cachedGuruvayurFaces
    if (faces == null) {
        val built = ArrayList<QuadFace3D>(320)

        // A. GURUVAYUR SREE KRISHNA TEMPLE (EAST NADA) — No text or labels on the temple model
        addBoxFaces(built, -2628.0f, 0.08f, 1236.0f, -2572.0f, 3.6f, 1237.2f, MatTerracottaTile, MatKeralaWhitewash)
        addBoxFaces(built, -2628.0f, 0.08f, 1282.8f, -2572.0f, 3.6f, 1284.0f, MatTerracottaTile, MatKeralaWhitewash)
        addBoxFaces(built, -2573.2f, 0.08f, 1236.0f, -2572.0f, 3.6f, 1284.0f, MatTerracottaTile, MatKeralaWhitewash)
        addBoxFaces(built, -2628.0f, 0.08f, 1236.0f, -2626.5f, 3.6f, 1251.0f, MatTerracottaTile, MatKeralaWhitewash)
        addBoxFaces(built, -2628.0f, 0.08f, 1269.0f, -2626.5f, 3.6f, 1284.0f, MatTerracottaTile, MatKeralaWhitewash)
        addBoxFaces(built, -2628.2f, 1.8f, 1236.0f, -2627.8f, 2.2f, 1251.0f, MatTempleGold, MatTempleGold, stroke = null)
        addBoxFaces(built, -2628.2f, 1.8f, 1269.0f, -2627.8f, 2.2f, 1284.0f, MatTempleGold, MatTempleGold, stroke = null)

        // Iconic 3-Storey Guruvayur East Nada Gopuram (X = -2626m, Z = 1260m)
        addBoxFaces(built, -2629.5f, 0.08f, 1251.0f, -2621.5f, 5.2f, 1269.0f, MatTeakWoodCarved, MatKeralaWhitewash)
        addPitchedKeralaRoof(built, -2631.0f, -2620.0f, 1249.5f, 1270.5f, 5.0f, 7.8f, 1.4f, 1.6f, MatTerracottaTile)
        addBoxFaces(built, -2628.5f, 7.6f, 1253.0f, -2622.5f, 10.2f, 1267.0f, MatTeakWoodCarved, MatTeakWoodCarved)
        addPitchedKeralaRoof(built, -2630.0f, -2621.0f, 1251.5f, 1268.5f, 10.0f, 13.6f, 3.8f, 3.0f, MatTerracottaRoofDark)
        for (fz in floatArrayOf(1257.0f, 1260.0f, 1263.0f)) {
            addBoxFaces(built, -2626.0f, 13.5f, fz - 0.45f, -2625.0f, 15.2f, fz + 0.45f, MatTempleGold, MatTempleGold)
        }

        // 33.5m Gold-Plated Dwajasthambham (Flagstaff) & Twin Multi-Tiered Golden Deepastambhams
        addBoxFaces(built, -2635.6f, 0.08f, 1259.4f, -2634.4f, 22.5f, 1260.6f, MatTempleGold, MatTempleGold, MatTempleGoldDeep)
        for (dzLamp in floatArrayOf(1254.5f, 1265.5f)) {
            addBoxFaces(built, -2638.5f, 0.08f, dzLamp - 0.9f, -2636.7f, 8.8f, dzLamp + 0.9f, MatTempleGold, MatTempleGoldDeep, MatTempleGold)
        }

        // Inner Sreekovil with Pure Gold-Plated Roof & Thazhikakudam
        addBoxFaces(built, -2608.0f, 0.08f, 1251.0f, -2590.0f, 5.2f, 1269.0f, MatTempleGoldDeep, MatKeralaWhitewash)
        addPitchedKeralaRoof(built, -2609.5f, -2588.5f, 1249.5f, 1270.5f, 5.0f, 10.4f, 9.8f, 9.8f, MatTempleGold)
        addBoxFaces(built, -2599.8f, 10.3f, 1259.2f, -2598.2f, 12.8f, 1260.8f, MatTempleGold, MatTempleGold)

        // B. GURUVAYUR MUNICIPAL BUS STAND (X = -2780m..-2690m, Z = 1267.8m..1326m)
        addBoxFaces(built, -2780.0f, 0.06f, 1267.8f, -2765.5f, 1.4f, 1268.6f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -2754.5f, 0.06f, 1267.8f, -2713.5f, 1.4f, 1268.6f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -2702.5f, 0.06f, 1267.8f, -2690.0f, 1.4f, 1268.6f, MatKeralaWhitewash, MatLateriteStone)

        addBoxFaces(built, -2714.0f, 0.06f, 1267.6f, -2713.2f, 5.8f, 1268.4f, MatTerracottaRoofDark, MatTerracottaTile)
        addBoxFaces(built, -2702.8f, 0.06f, 1267.6f, -2702.0f, 5.8f, 1268.4f, MatTerracottaRoofDark, MatTerracottaTile)
        addBoxFaces(built, -2766.0f, 0.06f, 1267.6f, -2765.2f, 5.8f, 1268.4f, MatTerracottaRoofDark, MatTerracottaTile)
        addBoxFaces(built, -2754.8f, 0.06f, 1267.6f, -2754.0f, 5.8f, 1268.4f, MatTerracottaRoofDark, MatTerracottaTile)

        // Raised passenger boarding platform under shelter
        addBoxFaces(built, -2774.0f, 0.06f, 1302.5f, -2696.0f, 0.36f, 1312.0f, MatPromenadeTile, MatGraniteDark)
        addBoxFaces(built, -2774.0f, 5.8f, 1283.0f, -2696.0f, 6.5f, 1306.0f, MatGranitePaving, MatTerracottaTile)
        addBoxFaces(built, -2776.0f, 0.06f, 1312.0f, -2694.0f, 8.2f, 1324.0f, MatTerracottaRoofDark, MatKeralaCreamWall)

        val colors = arrayOf(Color(0xFFB91C1C), Color(0xFF1D4ED8), Color(0xFF047857), Color(0xFFC2410C))
        for (bIdx in intArrayOf(1, 3, 6, 8)) {
            val bx = -2768.0f + bIdx * 7.6f
            val col = colors[bIdx % colors.size]
            addBoxFaces(built, bx - 1.2f, 0.4f, 1288.0f, bx + 1.2f, 3.1f, 1299.0f, Color(0xFFE2E8F0), col, col)
        }

        // Strict Stand Text Rule on Guruvayur Stand: Stand name, ENTRY, and EXIT only!
        addStandBannerQuadFace(
            built, "BANNER_GURUVAYUR_MAIN",
            Vec3(-2752.0f, 0f, 1282.85f), Vec3(-2718.0f, 0f, 1282.85f),
            5.65f, 6.95f, Color(0xFF7C2D12), Color(0xFFFACC15), "GURUVAYUR STAND"
        )
        addStandBannerQuadFace(
            built, "BANNER_GURUVAYUR_ENTRY",
            Vec3(-2713.5f, 0f, 1267.55f), Vec3(-2702.5f, 0f, 1267.55f),
            4.65f, 5.85f, Color(0xFF1E3A8A), Color(0xFF38BDF8), "ENTRY"
        )
        addStandBannerQuadFace(
            built, "BANNER_GURUVAYUR_EXIT",
            Vec3(-2765.5f, 0f, 1267.55f), Vec3(-2754.5f, 0f, 1267.55f),
            4.65f, 5.85f, Color(0xFF065F46), Color(0xFFFACC15), "EXIT"
        )

        faces = built
        cachedGuruvayurFaces = built
    }

    outFaces.addAll(faces)
}

// ============================================================================
// 3. CHAVAKKAD BEACH, AZHI ESTUARY, LIGHTHOUSE & BEACH BUS STAND
//    Aligned with X = -4500.0m Coastal Drive (Z = 1020.0m..1140.0m)
// ============================================================================

private fun DrawScope.drawChavakkadBeachGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - (-4500f), camEye.z - 1080f) > 440f) return

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-4518.0f, 0.059f, 995.0f),
            Vec3(-4508.5f, 0.059f, 995.0f),
            Vec3(-4508.5f, 0.059f, 1165.0f),
            Vec3(-4518.0f, 0.059f, 1165.0f)
        )
    ) {
        drawPath(path, MatPromenadeTile)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-4555.0f, 0.055f, 975.0f),
            Vec3(-4518.0f, 0.055f, 975.0f),
            Vec3(-4518.0f, 0.055f, 1185.0f),
            Vec3(-4555.0f, 0.055f, 1185.0f)
        )
    ) {
        drawPath(path, MatGoldenBeachSand)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-4568.0f, 0.054f, 975.0f),
            Vec3(-4555.0f, 0.054f, 975.0f),
            Vec3(-4555.0f, 0.054f, 1185.0f),
            Vec3(-4568.0f, 0.054f, 1185.0f)
        )
    ) {
        drawPath(path, MatWetBeachSand)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-4605.0f, 0.053f, 960.0f),
            Vec3(-4568.0f, 0.053f, 960.0f),
            Vec3(-4568.0f, 0.053f, 1200.0f),
            Vec3(-4605.0f, 0.053f, 1200.0f)
        )
    ) {
        drawPath(path, MatArabianSeaShallow)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-4740.0f, 0.052f, 960.0f),
            Vec3(-4605.0f, 0.052f, 960.0f),
            Vec3(-4605.0f, 0.052f, 1200.0f),
            Vec3(-4740.0f, 0.052f, 1200.0f)
        )
    ) {
        drawPath(path, MatArabianSeaDeep)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-4568.0f, 0.056f, 1158.0f),
            Vec3(-4518.0f, 0.056f, 1158.0f),
            Vec3(-4518.0f, 0.056f, 1192.0f),
            Vec3(-4568.0f, 0.056f, 1192.0f)
        )
    ) {
        drawPath(path, MatArabianSeaMid)
    }

    for (foamX in floatArrayOf(-4567.5f, -4576.0f, -4588.0f)) {
        val seg = rig.projectSegmentClipped(Vec3(foamX, 0.058f, 975.0f), Vec3(foamX, 0.058f, 1185.0f))
        if (seg != null) drawLine(MatSeaFoamWhite, seg.first, seg.second, strokeWidth = 3.0f)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-4491.5f, 0.058f, 1038.0f),
            Vec3(-4456.0f, 0.058f, 1038.0f),
            Vec3(-4456.0f, 0.058f, 1122.0f),
            Vec3(-4491.5f, 0.058f, 1122.0f)
        )
    ) {
        drawPath(path, MatTerminalConcrete)
    }
}

private var cachedChavakkadFaces: List<QuadFace3D>? = null

private fun collectChavakkadFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - (-4500f)
    val dz = camEye.z - 1080f
    if (dx * dx + dz * dz > 210f * 210f) return

    var faces = cachedChavakkadFaces
    if (faces == null) {
        val built = ArrayList<QuadFace3D>(220)

        addBoxFaces(built, -4509.2f, 0.06f, 1000.0f, -4508.4f, 0.85f, 1072.0f, MatKeralaWhitewash, MatSeawallGranite)
        addBoxFaces(built, -4509.2f, 0.06f, 1088.0f, -4508.4f, 0.85f, 1160.0f, MatKeralaWhitewash, MatSeawallGranite)

        val lx = -4524.0f
        val lz = 1125.0f
        addBoxFaces(built, lx - 3.2f, 0.06f, lz - 3.2f, lx + 3.2f, 4.5f, lz + 3.2f, MatLighthouseWhite, MatLighthouseRed)
        addBoxFaces(built, lx - 2.7f, 4.5f, lz - 2.7f, lx + 2.7f, 9.0f, lz + 2.7f, MatLighthouseRed, MatLighthouseWhite)
        addBoxFaces(built, lx - 2.3f, 9.0f, lz - 2.3f, lx + 2.3f, 13.5f, lz + 2.3f, MatLighthouseWhite, MatLighthouseRed)
        addBoxFaces(built, lx - 2.0f, 13.5f, lz - 2.0f, lx + 2.0f, 18.0f, lz + 2.0f, MatLighthouseRed, MatLighthouseWhite)
        addBoxFaces(built, lx - 2.6f, 18.0f, lz - 2.6f, lx + 2.6f, 20.5f, lz + 2.6f, MatTempleGold, Color(0xFF0F172A))
        addPitchedKeralaRoof(built, lx - 3.0f, lx + 3.0f, lz - 3.0f, lz + 3.0f, 20.5f, 22.8f, 2.7f, 2.7f, MatLighthouseRed)

        addBoxFaces(built, -4582.0f, 0.8f, 1076.0f, -4514.0f, 1.3f, 1084.0f, MatPromenadeTile, MatTeakWoodCarved)
        addBoxFaces(built, -4584.0f, 1.3f, 1074.0f, -4572.0f, 4.2f, 1086.0f, MatTeakWoodCarved, MatKeralaWhitewash)
        addPitchedKeralaRoof(built, -4585.5f, -4570.5f, 1072.5f, 1087.5f, 4.0f, 6.6f, 6.5f, 6.5f, MatTerracottaTile)

        addBoxFaces(built, -4486.0f, 5.4f, 1052.0f, -4466.0f, 6.1f, 1108.0f, MatGranitePaving, MatTerracottaTile)
        addBoxFaces(built, -4466.0f, 0.06f, 1050.0f, -4458.0f, 7.6f, 1110.0f, MatTerracottaRoofDark, MatKeralaCreamWall)
        addBoxFaces(built, -4483.0f, 0.4f, 1062.0f, -4471.0f, 3.1f, 1064.5f, Color(0xFFE2E8F0), Color(0xFF1D4ED8))
        addBoxFaces(built, -4483.0f, 0.4f, 1094.0f, -4471.0f, 3.1f, 1096.5f, Color(0xFFE2E8F0), Color(0xFFB91C1C))

        faces = built
        cachedChavakkadFaces = built
    }

    outFaces.addAll(faces)
}

// ============================================================================
// 4. TRIPRAYAR - NATTIKA BEACH, FISHING VALLAMS & TRIPRAYAR STAND
//    Aligned with X = -2220.0m NH 66 Coastal Terminus (Z = -2470.0m..-2340.0m)
// ============================================================================

private fun DrawScope.drawTriprayarBeachGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - (-2220f), camEye.z - (-2410f)) > 440f) return

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2239.0f, 0.059f, -2485.0f),
            Vec3(-2229.0f, 0.059f, -2485.0f),
            Vec3(-2229.0f, 0.059f, -2335.0f),
            Vec3(-2239.0f, 0.059f, -2335.0f)
        )
    ) {
        drawPath(path, MatPromenadeTile)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2278.0f, 0.055f, -2500.0f),
            Vec3(-2239.0f, 0.055f, -2500.0f),
            Vec3(-2239.0f, 0.055f, -2320.0f),
            Vec3(-2278.0f, 0.055f, -2320.0f)
        )
    ) {
        drawPath(path, MatGoldenBeachSand)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2292.0f, 0.054f, -2500.0f),
            Vec3(-2278.0f, 0.054f, -2500.0f),
            Vec3(-2278.0f, 0.054f, -2320.0f),
            Vec3(-2292.0f, 0.054f, -2320.0f)
        )
    ) {
        drawPath(path, MatWetBeachSand)
    }

    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2330.0f, 0.053f, -2515.0f),
            Vec3(-2292.0f, 0.053f, -2515.0f),
            Vec3(-2292.0f, 0.053f, -2305.0f),
            Vec3(-2330.0f, 0.053f, -2305.0f)
        )
    ) {
        drawPath(path, MatArabianSeaShallow)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2460.0f, 0.052f, -2515.0f),
            Vec3(-2330.0f, 0.052f, -2515.0f),
            Vec3(-2330.0f, 0.052f, -2305.0f),
            Vec3(-2460.0f, 0.052f, -2305.0f)
        )
    ) {
        drawPath(path, MatArabianSeaDeep)
    }

    for (foamX in floatArrayOf(-2291.5f, -2300.0f, -2312.0f)) {
        val seg = rig.projectSegmentClipped(Vec3(foamX, 0.058f, -2500.0f), Vec3(foamX, 0.058f, -2320.0f))
        if (seg != null) drawLine(MatSeaFoamWhite, seg.first, seg.second, strokeWidth = 3.0f)
    }

    // Triprayar Stand Concrete Terminal Apron
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2211.0f, 0.058f, -2456.0f),
            Vec3(-2176.0f, 0.058f, -2456.0f),
            Vec3(-2176.0f, 0.058f, -2364.0f),
            Vec3(-2211.0f, 0.058f, -2364.0f)
        )
    ) {
        drawPath(path, MatTerminalConcrete)
    }

    // 8 Painted Yellow Bus Bays inside Triprayar Stand
    for (bIdx in 0 until 8) {
        val bz = -2437.0f + bIdx * 7.8f
        val lSeg = rig.projectSegmentClipped(Vec3(-2204.0f, 0.062f, bz - 1.9f), Vec3(-2188.0f, 0.062f, bz - 1.9f))
        if (lSeg != null) drawLine(MatRoadMarkingYellow, lSeg.first, lSeg.second, strokeWidth = 2.4f)
        val rSeg = rig.projectSegmentClipped(Vec3(-2204.0f, 0.062f, bz + 1.9f), Vec3(-2188.0f, 0.062f, bz + 1.9f))
        if (rSeg != null) drawLine(MatRoadMarkingYellow, rSeg.first, rSeg.second, strokeWidth = 2.4f)
    }

    // Entry (Z = -2396..-2384) & Exit (Z = -2436..-2424) White Driveway Guide Lines
    for (driveZ in floatArrayOf(-2436.0f, -2424.0f, -2396.0f, -2384.0f)) {
        val seg = rig.projectSegmentClipped(Vec3(-2211.0f, 0.063f, driveZ), Vec3(-2204.0f, 0.063f, driveZ))
        if (seg != null) drawLine(MatRoadMarkingWhite, seg.first, seg.second, strokeWidth = 2.5f)
    }
}

private var cachedTriprayarFaces: List<QuadFace3D>? = null

private fun collectTriprayarFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - (-2220f)
    val dz = camEye.z - (-2410f)
    if (dx * dx + dz * dz > 210f * 210f) return

    var faces = cachedTriprayarFaces
    if (faces == null) {
        val built = ArrayList<QuadFace3D>(280)

        addBoxFaces(built, -2229.8f, 0.06f, -2480.0f, -2229.0f, 0.85f, -2418.0f, MatKeralaWhitewash, MatSeawallGranite)
        addBoxFaces(built, -2229.8f, 0.06f, -2402.0f, -2229.0f, 0.85f, -2340.0f, MatKeralaWhitewash, MatSeawallGranite)

        for (gz in floatArrayOf(-2435.0f, -2385.0f)) {
            addBoxFaces(built, -2256.0f, 0.06f, gz - 4.0f, -2246.0f, 3.2f, gz + 4.0f, MatPromenadeTile, MatKeralaWhitewash)
            addPitchedKeralaRoof(built, -2257.5f, -2244.5f, gz - 5.5f, gz + 5.5f, 3.1f, 5.8f, 5.8f, 5.8f, MatTerracottaTile)
        }
        for ((vz, hullCol) in arrayOf(Pair(-2452.0f, Color(0xFF0284C7)), Pair(-2410.0f, Color(0xFFDC2626)), Pair(-2368.0f, Color(0xFF059669)))) {
            addBoxFaces(built, -2274.0f, 0.08f, vz - 1.1f, -2262.0f, 1.15f, vz + 1.1f, MatTeakWoodCarved, hullCol, MatTeakWoodCarved)
        }

        addBoxFaces(built, -2304.0f, 0.8f, -2414.0f, -2236.0f, 1.3f, -2406.0f, MatPromenadeTile, MatTeakWoodCarved)
        addBoxFaces(built, -2248.0f, 0.06f, -2356.0f, -2242.0f, 16.5f, -2350.0f, MatTempleGold, MatKeralaWhitewash, MatTerracottaTile)

        // Triprayar Stand Perimeter Wall & Entry / Exit Gate Pillars at X = -2210.8m
        addBoxFaces(built, -2211.0f, 0.06f, -2456.0f, -2210.2f, 1.35f, -2436.5f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -2211.0f, 0.06f, -2423.5f, -2210.2f, 1.35f, -2396.5f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -2211.0f, 0.06f, -2383.5f, -2210.2f, 1.35f, -2364.0f, MatKeralaWhitewash, MatLateriteStone)
        for (pz in floatArrayOf(-2436.2f, -2423.8f, -2396.2f, -2383.8f)) {
            addBoxFaces(built, -2211.0f, 0.06f, pz - 0.4f, -2210.2f, 5.8f, pz + 0.4f, MatTerracottaRoofDark, MatTerracottaTile)
        }

        // Raised Passenger Boarding Platform, Canopy Shelter & Terminal Building
        addBoxFaces(built, -2189.5f, 0.06f, -2442.0f, -2186.0f, 0.36f, -2378.0f, MatPromenadeTile, MatGraniteDark)
        addBoxFaces(built, -2206.0f, 5.4f, -2442.0f, -2186.0f, 6.1f, -2378.0f, MatGranitePaving, MatTerracottaTile)
        addBoxFaces(built, -2186.0f, 0.06f, -2444.0f, -2178.0f, 7.6f, -2376.0f, MatTerracottaRoofDark, MatKeralaCreamWall)
        addBoxFaces(built, -2203.0f, 0.4f, -2428.0f, -2191.0f, 3.1f, -2425.5f, Color(0xFFE2E8F0), Color(0xFF047857))
        addBoxFaces(built, -2203.0f, 0.4f, -2396.0f, -2191.0f, 3.1f, -2393.5f, Color(0xFFE2E8F0), Color(0xFFC2410C))

        // Strict Stand Text Rule on Triprayar Stand: Stand name, ENTRY, and EXIT only!
        addStandBannerQuadFace(
            built, "BANNER_TRIPRAYAR_MAIN",
            Vec3(-2206.15f, 0f, -2394.0f), Vec3(-2206.15f, 0f, -2426.0f),
            5.45f, 6.75f, Color(0xFF7C2D12), Color(0xFFFACC15), "TRIPRAYAR STAND"
        )
        addStandBannerQuadFace(
            built, "BANNER_TRIPRAYAR_ENTRY",
            Vec3(-2211.05f, 0f, -2384.0f), Vec3(-2211.05f, 0f, -2396.0f),
            4.65f, 5.85f, Color(0xFF1E3A8A), Color(0xFF38BDF8), "ENTRY"
        )
        addStandBannerQuadFace(
            built, "BANNER_TRIPRAYAR_EXIT",
            Vec3(-2211.05f, 0f, -2424.0f), Vec3(-2211.05f, 0f, -2436.0f),
            4.65f, 5.85f, Color(0xFF065F46), Color(0xFFFACC15), "EXIT"
        )

        faces = built
        cachedTriprayarFaces = built
    }

    outFaces.addAll(faces)
}

// ============================================================================
// 5. ULTRA-REALISTIC PALIYEKKARA TOLL PLAZA ON NH 544 (Ref: toll1, toll2, toll3)
//    Aligned directly with ROAD_NH544_MANNUTHY_TO_PALIYEKKARA at X = 999.5m, Z = -1806.0m
// ============================================================================

private val MatTollConcreteApron = Color(0xFF3E4652)
private val MatTollLaneAsphalt = Color(0xFF2B313A)
private val MatTollRoofRed = Color(0xFFC84630)
private val MatTollRoofRedDark = Color(0xFF9E3220)
private val MatTollTrussDark = Color(0xFF1A202C)
private val MatFastagWhite = Color(0xFFF8FAFC)
private val MatFastagOrange = Color(0xFFF97316)
private val MatFastagGreen = Color(0xFF16A34A)
private val MatHazardYellow = Color(0xFFFACC15)
private val MatHazardBlack = Color(0xFF18181B)

private fun DrawScope.drawPaliyekkaraTollPlazaGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    val tx = 999.5f
    val tz = -1806.0f
    if (hypot(camEye.x - tx, camEye.z - tz) > 440f) return

    // 1. North Approach Flared Concrete Taper (Z = -1754m .. -1782m, flaring from 24m highway to 58m 10-lane plaza)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tx - 12.2f, 0.058f, -1754.0f),
            Vec3(tx + 12.2f, 0.058f, -1754.0f),
            Vec3(tx + 29.0f, 0.058f, -1782.0f),
            Vec3(tx - 29.0f, 0.058f, -1782.0f)
        )
    ) {
        drawPath(path, MatTollConcreteApron)
    }

    // 2. Main 10-Lane Concrete Toll Plaza Apron (X = tx - 29.0m .. tx + 29.0m, Z = -1782.0m .. -1830.0m)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tx - 29.0f, 0.058f, -1782.0f),
            Vec3(tx + 29.0f, 0.058f, -1782.0f),
            Vec3(tx + 29.0f, 0.058f, -1830.0f),
            Vec3(tx - 29.0f, 0.058f, -1830.0f)
        )
    ) {
        drawPath(path, MatTollConcreteApron)
    }

    // 3. South Exit Flared Concrete Taper (Z = -1830m .. -1858m, converging from 58m plaza back to 24m highway)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tx - 29.0f, 0.058f, -1830.0f),
            Vec3(tx + 29.0f, 0.058f, -1830.0f),
            Vec3(tx + 12.2f, 0.058f, -1858.0f),
            Vec3(tx - 12.2f, 0.058f, -1858.0f)
        )
    ) {
        drawPath(path, MatTollConcreteApron)
    }

    // 4. 10 Individual Toll Lane Wear Strips, White Lane Guide Lines & Yellow-Black Speed Bumps (Ref: toll3_673df7db.jpeg)
    val lanePitch = 5.6f
    for (laneIdx in 0 until 10) {
        val laneCenterX = tx + (laneIdx - 4.5f) * lanePitch
        // Subtle darker tire-wear strip along each toll lane
        if (rig.buildClippedQuadPath(
                path,
                Vec3(laneCenterX - 1.9f, 0.061f, -1782.0f),
                Vec3(laneCenterX + 1.9f, 0.061f, -1782.0f),
                Vec3(laneCenterX + 1.9f, 0.061f, -1830.0f),
                Vec3(laneCenterX - 1.9f, 0.061f, -1830.0f)
            )
        ) {
            drawPath(path, MatTollLaneAsphalt)
        }

        // White lane guide lines on approach and exit aprons
        val lLine = rig.projectSegmentClipped(
            Vec3(laneCenterX - 2.35f, 0.063f, -1780.0f),
            Vec3(laneCenterX - 2.35f, 0.063f, -1832.0f)
        )
        if (lLine != null) drawLine(MatRoadMarkingWhite, lLine.first, lLine.second, strokeWidth = 2.0f)

        // Yellow-and-Black Speed Bump / Rumble Strip before the booth (Ref: toll3_673df7db.jpeg)
        for (bumpZ in floatArrayOf(-1795.5f, -1816.5f)) {
            if (rig.buildClippedQuadPath(
                    path,
                    Vec3(laneCenterX - 2.1f, 0.064f, bumpZ - 0.35f),
                    Vec3(laneCenterX + 2.1f, 0.064f, bumpZ - 0.35f),
                    Vec3(laneCenterX + 2.1f, 0.064f, bumpZ + 0.35f),
                    Vec3(laneCenterX - 2.1f, 0.064f, bumpZ + 0.35f)
                )
            ) {
                drawPath(path, MatHazardYellow)
            }
        }
    }
}

private var cachedPaliyekkaraTollFaces: List<QuadFace3D>? = null

private fun collectPaliyekkaraTollFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val tx = 999.5f
    val tz = -1806.0f
    val dx = camEye.x - tx
    val dz = camEye.z - tz
    if (dx * dx + dz * dz > 220f * 220f) return

    val existing = cachedPaliyekkaraTollFaces
    if (existing != null) {
        outFaces.addAll(existing)
        return
    }

    val faces = ArrayList<QuadFace3D>(620)
    val lanePitch = 5.6f
    val canopyHalfW = 30.8f // Full 61.6m width across all 10 lanes + wing pavilions

    // =========================================================================
    // A. 11 YELLOW-AND-BLACK STRIPED CONCRETE TOLL ISLANDS, YELLOW BOLLARDS,
    //    GLAZED TOLL BOOTHS & AUTOMATIC BOOM BARRIERS (Ref: toll3_673df7db.jpeg)
    // =========================================================================
    for (iIdx in 0..10) {
        val ix = tx + (iIdx - 5) * lanePitch
        val iHalfW = if (iIdx == 5) 0.58f else 0.42f // Slightly wider central median island

        // 1. Alternating Yellow & Black Striped Concrete Island Segments (Z = -1823m .. -1789m)
        val segCount = 12
        val zStart = -1823.0f
        val zEnd = -1789.0f
        val segLen = (zEnd - zStart) / segCount
        for (s in 0 until segCount) {
            val sz0 = zStart + s * segLen
            val sz1 = sz0 + segLen
            val col = if (s % 2 == 0) MatHazardYellow else MatHazardBlack
            addBoxFaces(
                faces,
                ix - iHalfW, 0.06f, sz0,
                ix + iHalfW, 0.38f, sz1,
                topCol = if (iIdx == 5) MatGranitePaving else col,
                sideCol = col,
                frontBackCol = col,
                stroke = null
            )
        }

        // 2. Bright Yellow Tubular Steel Guard Bollards & White Lane Control Cabinets on Approach Ends
        for (bz in floatArrayOf(-1790.2f, -1792.8f, -1819.2f, -1821.8f)) {
            addBoxFaces(
                faces,
                ix - 0.14f, 0.38f, bz - 0.14f,
                ix + 0.14f, 1.58f, bz + 0.14f,
                topCol = MatHazardYellow,
                sideCol = MatHazardYellow,
                frontBackCol = MatTempleGoldDeep,
                stroke = null
            )
        }
        // White electronic lane-control cabinet (Ref: toll3_673df7db.jpeg center median & left island)
        addBoxFaces(
            faces,
            ix - 0.30f, 0.90f, -1791.6f,
            ix + 0.30f, 1.65f, -1791.0f,
            topCol = MatFastagWhite,
            sideCol = MatFastagWhite,
            frontBackCol = MatPromenadeTile
        )

        // 3. Tall Cream Concrete Canopy Support Columns rising from each Island (Ref: toll3_673df7db.jpeg)
        addBoxFaces(
            faces,
            ix - 0.34f, 0.38f, tz - 0.34f,
            ix + 0.34f, 5.55f, tz + 0.34f,
            topCol = MatKeralaCreamWall,
            sideCol = MatKeralaCreamWall,
            frontBackCol = MatKeralaWhitewash
        )
        // Flared column capital under the space-frame truss
        addBoxFaces(
            faces,
            ix - 0.52f, 5.20f, tz - 0.52f,
            ix + 0.52f, 5.55f, tz + 0.52f,
            topCol = MatKeralaCreamWall,
            sideCol = MatKeralaWhitewash,
            frontBackCol = MatKeralaCreamWall
        )

        // 4. Glazed Toll Collector Booth Kiosks on Interior Islands (Ref: toll2 & toll3)
        if (iIdx in 1..9) {
            // Lower red/terracotta booth body
            addBoxFaces(
                faces,
                ix - 0.40f, 0.38f, tz - 2.60f,
                ix + 0.40f, 1.45f, tz + 2.60f,
                topCol = MatTollRoofRed,
                sideCol = MatTollRoofRed,
                frontBackCol = MatTollRoofRedDark
            )
            // Upper dark-tinted glass booth cabin
            addBoxFaces(
                faces,
                ix - 0.38f, 1.45f, tz - 2.45f,
                ix + 0.38f, 2.85f, tz + 2.45f,
                topCol = MatFastagWhite,
                sideCol = MatArcadeGlass,
                frontBackCol = MatArcadeGlass
            )
        }
    }

    // 5. Concrete Approach Median Kerb Stones along Centerline (Ref: toll1_d95fdb21.jpeg foreground right)
    for (kIdx in 0 until 10) {
        val kzNorth = -1782.0f + kIdx * 2.6f
        val kzSouth = -1830.0f - (kIdx + 1) * 2.6f
        val kCol = if (kIdx % 2 == 0) MatPromenadeTile else MatGranitePaving
        addBoxFaces(faces, tx - 0.45f, 0.06f, kzNorth, tx + 0.45f, 0.42f, kzNorth + 1.9f, kCol, kCol)
        addBoxFaces(faces, tx - 0.45f, 0.06f, kzSouth, tx + 0.45f, 0.42f, kzSouth + 1.9f, kCol, kCol)
    }

    // =========================================================================
    // B. DARK SPACE-FRAME CEILING, WHITE "NETC FASTag" FASCIA BOARDS & LANE SIGNS
    //    (Ref: toll2_5d53a19c.jpeg & toll3_673df7db.jpeg)
    // =========================================================================
    // 1. Dark Structural Steel Space-Frame Under-Canopy Box (Y = 5.45m .. 6.25m)
    addBoxFaces(
        faces,
        tx - canopyHalfW, 5.45f, tz - 5.40f,
        tx + canopyHalfW, 6.25f, tz + 5.40f,
        topCol = MatTollTrussDark,
        sideCol = MatTollTrussDark,
        frontBackCol = MatTollTrussDark
    )

    // 2. White "NETC FASTag" Fascia Boards with Orange & Green FASTag Graphic Bars across all 10 Lanes (Ref: toll2_5d53a19c.jpeg)
    for (laneIdx in 0 until 10) {
        val lx = tx + (laneIdx - 4.5f) * lanePitch
        val fMinX = lx - 2.55f
        val fMaxX = lx + 2.55f

        for (fasciaZ in floatArrayOf(tz + 5.44f, tz - 5.44f)) {
            val zThickness = if (fasciaZ > tz) 0.12f else -0.12f
            val z0 = minOf(fasciaZ, fasciaZ + zThickness)
            val z1 = maxOf(fasciaZ, fasciaZ + zThickness)

            // Crisp White NETC FASTag Fascia Background Board
            addBoxFaces(
                faces,
                fMinX, 5.52f, z0,
                fMaxX, 6.22f, z1,
                topCol = MatFastagWhite,
                sideCol = MatFastagWhite,
                frontBackCol = MatFastagWhite,
                stroke = MatTollTrussDark,
                bias = -0.15f
            )
            // Left Orange "FAST" Bar of FASTag Logo
            addBoxFaces(
                faces,
                lx - 1.15f, 5.74f, z0 - 0.02f,
                lx + 0.25f, 6.04f, z1 + 0.02f,
                topCol = MatFastagOrange,
                sideCol = MatFastagOrange,
                frontBackCol = MatFastagOrange,
                stroke = null,
                bias = -0.25f
            )
            // Right Green "tag" Bar of FASTag Logo
            addBoxFaces(
                faces,
                lx + 0.30f, 5.74f, z0 - 0.02f,
                lx + 1.85f, 6.04f, z1 + 0.02f,
                topCol = MatFastagGreen,
                sideCol = MatFastagGreen,
                frontBackCol = MatFastagGreen,
                stroke = null,
                bias = -0.25f
            )
        }

        // 3. Overhead Rectangular Green / Orange Lane Signboards mounted above the eaves (Ref: toll1, toll2, toll3)
        val signColor = if (laneIdx == 3 || laneIdx == 6) MatFastagOrange else MatFastagGreen
        for (signZ in floatArrayOf(tz + 5.55f, tz - 5.55f)) {
            addBoxFaces(
                faces,
                lx - 0.85f, 6.30f, signZ - 0.08f,
                lx + 0.85f, 7.25f, signZ + 0.08f,
                topCol = MatFastagWhite,
                sideCol = signColor,
                frontBackCol = signColor,
                stroke = MatFastagWhite,
                bias = -0.20f
            )
        }
    }

    // =========================================================================
    // C. FULL-WIDTH KERALA TERRACOTTA SLOPED ROOF CANOPY, CENTRAL 2-TIERED
    //    GOPURAM TOWER & LEFT/RIGHT STEEP GABLE WING PAVILIONS (Ref: toll1, toll2, toll3)
    // =========================================================================
    // 1. Main Full-Width Pitched Terracotta-Red Kerala Roof Deck (X = tx - 30.8m .. tx + 30.8m, Y = 6.25m .. 7.75m)
    addPitchedKeralaRoof(
        faces,
        tx - canopyHalfW - 0.6f, tx + canopyHalfW + 0.6f,
        tz - 5.80f, tz + 5.80f,
        baseY = 6.22f,
        ridgeY = 7.75f,
        insetX = 1.8f,
        insetZ = 5.1f,
        roofColor = MatTollRoofRed,
        eaveColor = MatTollRoofRedDark
    )

    // Small Triangular Kerala Gable Dormers (Mukhappu) along the main roof over each lane (Ref: toll1 & toll3)
    for (laneIdx in 0 until 10) {
        val lx = tx + (laneIdx - 4.5f) * lanePitch
        addPitchedKeralaRoof(
            faces,
            lx - 1.10f, lx + 1.10f,
            tz - 5.75f, tz + 5.75f,
            baseY = 6.25f,
            ridgeY = 7.15f,
            insetX = 0.95f,
            insetZ = 0.25f,
            roofColor = MatTollRoofRedDark,
            eaveColor = MatTollTrussDark
        )
    }

    // 2. Central 2-Tiered Kerala Gopuram Roof Tower above the Center Median (Ref: toll1, toll2, toll3)
    //    - Lower Sloped Terracotta Roof Tier (X = tx - 9.8m .. tx + 9.8m, Y = 7.65m .. 9.95m)
    addPitchedKeralaRoof(
        faces,
        tx - 9.8f, tx + 9.8f,
        tz - 5.20f, tz + 5.20f,
        baseY = 7.65f,
        ridgeY = 9.95f,
        insetX = 1.6f,
        insetZ = 1.8f,
        roofColor = MatTollRoofRed,
        eaveColor = MatTollTrussDark
    )
    //    - White Clerestory Louver Band between the two central roof tiers (Ref: toll1_d95fdb21.jpeg)
    addBoxFaces(
        faces,
        tx - 8.2f, 9.80f, tz - 3.40f,
        tx + 8.2f, 11.15f, tz + 3.40f,
        topCol = MatTollTrussDark,
        sideCol = MatFastagWhite,
        frontBackCol = MatFastagWhite,
        stroke = MatTollTrussDark
    )
    //    - Upper Steep Pitched Terracotta Roof Tier (X = tx - 9.2m .. tx + 9.2m, Y = 11.05m .. 14.30m)
    addPitchedKeralaRoof(
        faces,
        tx - 9.2f, tx + 9.2f,
        tz - 4.80f, tz + 4.80f,
        baseY = 11.05f,
        ridgeY = 14.30f,
        insetX = 1.4f,
        insetZ = 4.2f,
        roofColor = MatTollRoofRed,
        eaveColor = MatTollRoofRedDark
    )
    //    - Central Triangular Mukhappu Gable on the Upper Roof (Ref: toll1, toll2, toll3)
    addPitchedKeralaRoof(
        faces,
        tx - 2.35f, tx + 2.35f,
        tz - 4.65f, tz + 4.65f,
        baseY = 12.20f,
        ridgeY = 14.75f,
        insetX = 2.05f,
        insetZ = 0.30f,
        roofColor = MatTollRoofRedDark,
        eaveColor = MatTollTrussDark
    )

    // 3. Left & Right Steep Pitched Triangular Gable Wing Pavilions (Ref: toll1_d95fdb21.jpeg & toll3_673df7db.jpeg)
    for (wingCenterX in floatArrayOf(tx - canopyHalfW - 1.5f, tx + canopyHalfW + 1.5f)) {
        // White outer support columns for the wing pavilion
        for (wx in floatArrayOf(wingCenterX - 4.5f, wingCenterX + 4.5f)) {
            for (wz in floatArrayOf(tz - 4.5f, tz + 4.5f)) {
                addBoxFaces(
                    faces,
                    wx - 0.28f, 0.06f, wz - 0.28f,
                    wx + 0.28f, 6.25f, wz + 0.28f,
                    topCol = MatFastagWhite,
                    sideCol = MatFastagWhite,
                    frontBackCol = MatPromenadeTile
                )
            }
        }
        // Dark triangular truss gable core + steep terracotta-red pitched roof
        addBoxFaces(
            faces,
            wingCenterX - 3.6f, 6.22f, tz - 5.2f,
            wingCenterX + 3.6f, 8.60f, tz + 5.2f,
            topCol = MatTollTrussDark,
            sideCol = MatTollTrussDark,
            frontBackCol = MatTollTrussDark
        )
        addPitchedKeralaRoof(
            faces,
            wingCenterX - 6.2f, wingCenterX + 6.2f,
            tz - 6.0f, tz + 6.0f,
            baseY = 6.22f,
            ridgeY = 11.40f,
            insetX = 5.4f,
            insetZ = 0.6f,
            roofColor = MatTollRoofRed,
            eaveColor = MatTollTrussDark
        )
    }

    // =========================================================================
    // D. HIGH-MAST HIGHWAY FLOODLIGHT TOWERS & 2-STOREY SIDE ADMIN BUILDING
    //    (Ref: toll1_d95fdb21.jpeg & toll3_673df7db.jpeg right side)
    // =========================================================================
    for (mastX in floatArrayOf(tx - 11.2f, tx + 16.8f)) {
        val mz = tz + 14.0f
        addBoxFaces(
            faces,
            mastX - 0.22f, 0.06f, mz - 0.22f,
            mastX + 0.22f, 23.5f, mz + 0.22f,
            topCol = MatGraniteLight,
            sideCol = MatGranitePaving,
            frontBackCol = MatGraniteLight
        )
        // Floodlight luminaire ring at top of mast
        addBoxFaces(
            faces,
            mastX - 1.35f, 23.2f, mz - 1.35f,
            mastX + 1.35f, 23.9f, mz + 1.35f,
            topCol = MatFastagWhite,
            sideCol = MatTempleGold,
            frontBackCol = MatFastagWhite
        )
    }

    // 2-Storey Paliyekkara Toll Plaza Administrative Building on West/Right side (Ref: toll3_673df7db.jpeg)
    addBoxFaces(
        faces,
        tx + 34.5f, 0.06f, tz - 22.0f,
        tx + 46.5f, 7.60f, tz + 12.0f,
        topCol = MatGranitePaving,
        sideCol = MatPromenadeTile,
        frontBackCol = MatKeralaCreamWall
    )
    // Ribbon glass windows on admin building
    addBoxFaces(
        faces,
        tx + 34.3f, 4.20f, tz - 20.0f,
        tx + 34.6f, 6.40f, tz + 10.0f,
        topCol = MatArcadeGlass,
        sideCol = MatArcadeGlass,
        frontBackCol = MatArcadeGlass
    )
    addPitchedKeralaRoof(
        faces,
        tx + 33.5f, tx + 47.5f,
        tz - 23.0f, tz + 13.0f,
        baseY = 7.50f,
        ridgeY = 10.20f,
        insetX = 5.5f,
        insetZ = 4.0f,
        roofColor = MatTollRoofRed
    )

    cachedPaliyekkaraTollFaces = faces
    outFaces.addAll(faces)
}

// ============================================================================
// 6. VADAKKE STAND (THRISSUR NORTH BUS STAND)
//    Aligned with North end of ROAD_SWARAJ_TO_VADAKKE at X = -62m..-10m, Z = 200.2m..246m
// ============================================================================

private fun DrawScope.drawVadakkeBusStandGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - (-34f), camEye.z - 220f) > 360f) return

    // Concrete terminal yard apron
    val yardMinX = -62.0f
    val yardMaxX = -10.0f
    val yardMinZ = 200.16f
    val yardMaxZ = 246.0f
    if (rig.buildClippedQuadPath(
            path,
            Vec3(yardMinX, 0.058f, yardMinZ),
            Vec3(yardMaxX, 0.058f, yardMinZ),
            Vec3(yardMaxX, 0.058f, yardMaxZ),
            Vec3(yardMinX, 0.058f, yardMaxZ)
        )
    ) {
        drawPath(path, MatTerminalConcrete)
    }

    // 8 Painted Yellow Bus Bays inside Vadakke Stand (Z = 216m..233m)
    for (bIdx in 0 until 8) {
        val bx = -55.0f + bIdx * 5.6f
        val lSeg = rig.projectSegmentClipped(Vec3(bx - 1.85f, 0.062f, 216.0f), Vec3(bx - 1.85f, 0.062f, 233.0f))
        if (lSeg != null) drawLine(MatRoadMarkingYellow, lSeg.first, lSeg.second, strokeWidth = 2.4f)
        val rSeg = rig.projectSegmentClipped(Vec3(bx + 1.85f, 0.062f, 216.0f), Vec3(bx + 1.85f, 0.062f, 233.0f))
        if (rSeg != null) drawLine(MatRoadMarkingYellow, rSeg.first, rSeg.second, strokeWidth = 2.4f)
    }

    // Entry Driveway (X = -28m..-16m) & Exit Driveway (X = -44m..-32m) White Guide Lines
    for (driveX in floatArrayOf(-44.0f, -32.0f, -28.0f, -16.0f)) {
        val seg = rig.projectSegmentClipped(Vec3(driveX, 0.063f, 200.16f), Vec3(driveX, 0.063f, 214.0f))
        if (seg != null) drawLine(MatRoadMarkingWhite, seg.first, seg.second, strokeWidth = 2.5f)
    }
}

private var cachedVadakkeStandFaces: List<QuadFace3D>? = null

private fun collectVadakkeBusStandFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - (-34f)
    val dz = camEye.z - 220f
    if (dx * dx + dz * dz > 205f * 205f) return

    var faces = cachedVadakkeStandFaces
    if (faces == null) {
        val built = ArrayList<QuadFace3D>(200)

        // South Perimeter Wall at far outer corners only (X = -62..-46.5 and X = -15.0..-10.0)
        // Leaves the entire 31.5m entrance X = -46.5..-15.0 100% open & obstacle-free for ROAD_SWARAJ_TO_VADAKKE!
        addBoxFaces(built, -62.0f, 0.06f, 200.4f, -46.5f, 1.35f, 201.1f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -15.0f, 0.06f, 200.4f, -10.0f, 1.35f, 201.1f, MatKeralaWhitewash, MatLateriteStone)

        // Outer Gate Arch Columns at X = -46.2m and X = -15.3m (strictly outside road bounds)
        for (px in floatArrayOf(-46.2f, -15.3f)) {
            addBoxFaces(built, px - 0.4f, 0.06f, 200.2f, px + 0.4f, 6.1f, 201.0f, MatTerracottaRoofDark, MatTerracottaTile)
        }

        // Raised Passenger Boarding Platform, Canopy Shelter & Waiting Terminal Hall
        addBoxFaces(built, -58.0f, 0.06f, 233.2f, -14.0f, 0.36f, 244.0f, MatPromenadeTile, MatGraniteDark)
        for (colX in floatArrayOf(-56.0f, -42.0f, -28.0f, -16.0f)) {
            addBoxFaces(built, colX - 0.35f, 0.36f, 233.5f, colX + 0.35f, 5.6f, 234.2f, MatKeralaCreamWall, MatGranitePaving)
        }
        addBoxFaces(built, -58.5f, 5.6f, 214.0f, -13.5f, 6.3f, 235.5f, MatGranitePaving, MatTerracottaTile)
        addPitchedKeralaRoof(built, -59.2f, -12.8f, 213.5f, 236.0f, 6.25f, 8.20f, 3.5f, 4.5f, MatTerracottaTile)
        addBoxFaces(built, -57.0f, 0.36f, 235.5f, -15.0f, 7.2f, 244.5f, MatTerracottaRoofDark, MatKeralaCreamWall)

        // Parked Town Buses in Vadakke Stand Bays (North of Z = 218.5m, well clear of entrance)
        val busColors = arrayOf(Color(0xFF1D4ED8), Color(0xFFB91C1C), Color(0xFF047857), Color(0xFFC2410C))
        for ((idx, bIdx) in intArrayOf(1, 3, 5, 7).withIndex()) {
            val bx = -55.0f + bIdx * 5.6f
            val col = busColors[idx % busColors.size]
            addBoxFaces(built, bx - 1.15f, 0.4f, 218.5f, bx + 1.15f, 3.1f, 229.5f, Color(0xFFE2E8F0), col, col)
        }

        // Strict Stand Text Rule on Vadakke Stand: Stand name, ENTRY, and EXIT only (overhead at Y = 5.2..6.8m)!
        addStandBannerQuadFace(
            built, "BANNER_VADAKKE_MAIN",
            Vec3(-50.0f, 0f, 213.85f), Vec3(-22.0f, 0f, 213.85f),
            5.55f, 6.85f, Color(0xFF7C2D12), Color(0xFFFACC15), "VADAKKE STAND"
        )
        addStandBannerQuadFace(
            built, "BANNER_VADAKKE_ENTRY",
            Vec3(-28.5f, 0f, 200.4f), Vec3(-15.5f, 0f, 200.4f),
            5.15f, 6.25f, Color(0xFF1E3A8A), Color(0xFF38BDF8), "ENTRY"
        )
        addStandBannerQuadFace(
            built, "BANNER_VADAKKE_EXIT",
            Vec3(-46.0f, 0f, 200.4f), Vec3(-33.0f, 0f, 200.4f),
            5.15f, 6.25f, Color(0xFF065F46), Color(0xFFFACC15), "EXIT"
        )

        faces = built
        cachedVadakkeStandFaces = built
    }

    outFaces.addAll(faces)
}

// ============================================================================
// 7. ROADSIDE KERALA SCENERY (TILED-ROOF HOUSES, SHOPS, LATERITE WALLS & TREES)
//    Strictly placed outside roadHalfW + 10.5m and clear of all junctions
// ============================================================================

private class CachedSceneryCluster(
    val bx: Float,
    val bz: Float,
    val faces: List<QuadFace3D>
)

private var cachedRoadsideClusters: List<CachedSceneryCluster>? = null

private fun isClearForRoadsideScenery(
    bx: Float,
    bz: Float,
    roads: List<ThrissurOsmRoad>,
    signPlacements: List<CachedSignRenderItem>
): Boolean {
    // Keep strictly clear of Sakthan Stand, Swaraj Round, Vadakke Stand, Guruvayur, Chavakkad, Triprayar & Toll
    if (bx in -34f..58f && bz in -232f..-118f) return false
    if (hypot(bx - 3.1f, bz - 9.5f) < 86f) return false
    if (bx in -74f..2f && bz in 190f..258f) return false
    if (bx in -2792f..-2560f && bz in 1220f..1342f) return false
    if (bx in -4760f..-4446f && bz in 950f..1210f) return false
    if (bx in -2480f..-2166f && bz in -2520f..-2300f) return false
    if (bx in 945f..1055f && bz in -1870f..-1745f) return false

    if (!isPointClearOfAllRoads(bx, bz, roads, extraMarginM = 9.0f)) return false

    // Stay clear of all NHAI / PWD green direction boards so houses never block or clip signposts
    for (s in signPlacements) {
        if (hypot(bx - s.midX, bz - s.midZ) < 13.5f) return false
    }
    return true
}

private fun buildRoadsideSceneryCache(
    roads: List<ThrissurOsmRoad>,
    signPlacements: List<CachedSignRenderItem>
): List<CachedSceneryCluster> {
    val clusters = ArrayList<CachedSceneryCluster>(160)
    for (road in roads) {
        if (road.id == "ROAD_SWARAJ_ROUND" || road.id == "ROAD_SAKTHAN_STAND_ENTRY") continue
        val frames = road.frames
        if (frames.size < 8) continue

        val roadHalfW = road.widthM * 0.5f
        var idx = 5
        while (idx < frames.size - 5) {
            val f = frames[idx]
            for (side in intArrayOf(-1, 1)) {
                val latOffset = (roadHalfW + 12.2f) * side
                val bx = f.pos.x + f.right.x * latOffset
                val bz = f.pos.z + f.right.z * latOffset

                if (isClearForRoadsideScenery(bx, bz, roads, signPlacements)) {
                    val itemFaces = ArrayList<QuadFace3D>(18)
                    val pattern = (idx + if (side > 0) 1 else 2) % 3
                    if (pattern == 0) {
                        addBoxFaces(
                            itemFaces,
                            bx - 3.4f, 0.06f, bz - 3.0f,
                            bx + 3.4f, 3.4f, bz + 3.0f,
                            topCol = MatTeakWoodCarved,
                            sideCol = MatKeralaWhitewash,
                            frontBackCol = MatKeralaCreamWall
                        )
                        addPitchedKeralaRoof(
                            itemFaces,
                            bx - 4.0f, bx + 4.0f,
                            bz - 3.6f, bz + 3.6f,
                            baseY = 3.3f,
                            ridgeY = 5.4f,
                            insetX = 1.6f,
                            insetZ = 1.6f,
                            roofColor = MatTerracottaTile
                        )
                    } else if (pattern == 1) {
                        addBoxFaces(
                            itemFaces,
                            bx - 3.8f, 0.06f, bz - 2.6f,
                            bx + 3.8f, 5.2f, bz + 2.6f,
                            topCol = MatArcadeRoofSlate,
                            sideCol = MatArcadeCream,
                            frontBackCol = MatArcadeSandstone
                        )
                    } else {
                        addRealisticMaidanTree3D(
                            faces = itemFaces,
                            tx = bx,
                            tz = bz,
                            baseY = 0.06f,
                            crownRadius = 3.2f,
                            treeHeight = 7.8f
                        )
                    }
                    clusters.add(CachedSceneryCluster(bx, bz, itemFaces))
                }
            }
            idx += 12
        }
    }
    return clusters
}

private fun collectRoadsideSceneryFaces(
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>,
    outFaces: MutableList<QuadFace3D>
) {
    if (roads.isEmpty()) return
    val signPlacements = getOrBuildSignRenderCache(roads)
    var clusters = cachedRoadsideClusters
    if (clusters == null) {
        clusters = buildRoadsideSceneryCache(roads, signPlacements)
        cachedRoadsideClusters = clusters
    }

    val maxDistSq = 155f * 155f
    val eyeX = camEye.x
    val eyeZ = camEye.z
    for (i in 0 until clusters.size) {
        val c = clusters[i]
        val dx = c.bx - eyeX
        val dz = c.bz - eyeZ
        if (dx * dx + dz * dz <= maxDistSq) {
            outFaces.addAll(c.faces)
        }
    }
}

// ============================================================================
// 8. REFERENCE-ACCURATE KERALA PWD & NHAI GREEN SIGNBOARDS (100% OBSTACLE-FREE)
// ============================================================================

internal enum class KeralaSignStyle {
    ROUNDABOUT_DIAGRAM,
    STACKED_3_PANEL,
    OVERHEAD_HIGHWAY_GANTRY
}

internal data class KeralaSignDestination(
    val malName: String,
    val engName: String,
    val arrowDir: String,
    val distKm: String
)

internal data class KeralaRoadSignSpec(
    val roadId: String,
    val frameFraction: Float,
    val side: Int,
    val style: KeralaSignStyle,
    val headerTitle: String = "",
    val straightDest: KeralaSignDestination,
    val leftDest: KeralaSignDestination,
    val rightDest: KeralaSignDestination
)

private val ThrissurAuthenticSignSpecs = listOf(
    // 1. M.O. Road mid-corridor -> Before Sakthan South / Ollur Junction — Overhead Exit Gantry (clear of Exit Gate)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.45f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "M.O. ROAD • THRISSUR CITY CORRIDOR",
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "SWARAJ ROUND", "UP", "0.6 KM"),
        leftDest = KeralaSignDestination("കൊക്കാലെ", "KOKKALA", "UP", "1.2 KM"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ", "OLLUR", "UP", "5.5 KM")
    ),
    // 2. MO Road -> Before High Road / Puthanpalli Junction (0.647) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.59f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.5"),
        leftDest = KeralaSignDestination("ശക്തൻ എൻട്രി", "Sakthan Entry", "LEFT", "0.1"),
        rightDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "RIGHT", "0.6")
    ),
    // 3. MO Road -> Before Swaraj Round South Entry (1.000) — True Roundabout Approach Board
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.82f,
        side = -1,
        style = KeralaSignStyle.ROUNDABOUT_DIAGRAM,
        headerTitle = "സ്വരാജ് റൗണ്ട് • SWARAJ ROUND",
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "0.9"),
        leftDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "LEFT", "1.4"),
        rightDest = KeralaSignDestination("മണ്ണുത്തി", "Mannuthy", "RIGHT", "6.5")
    ),
    // 4. Sakthan Left Entry Slip -> Roadside verge sign approaching Sakthan Entry Gate (avoids double-gantry clutter)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_STAND_ENTRY",
        frameFraction = 0.25f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ബസ് ബേ 1-20", "Bus Bays 1-20", "UP", "0.1"),
        leftDest = KeralaSignDestination("അറൈവൽ ഗേറ്റ്", "Arrival Gate", "UP", "0.1"),
        rightDest = KeralaSignDestination("എക്സിറ്റ് ഗേറ്റ്", "Exit Gate", "RIGHT", "0.2")
    ),
    // 5. Swaraj Round -> Before M.G. Road / West Fort Exit (0.179) — Left outer driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.105f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "0.9"),
        leftDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "LEFT", "1.4"),
        rightDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "LEFT", "28")
    ),
    // 6. Swaraj Round -> Before Shoranur Road / Vadakke Stand Exit (0.468) — Left outer driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.385f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഈസ്റ്റ് ഫോർട്ട്", "East Fort", "UP", "0.5"),
        leftDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "LEFT", "0.4"),
        rightDest = KeralaSignDestination("മണ്ണുത്തി", "Mannuthy", "UP", "6.5")
    ),
    // 7. Swaraj Round -> Before East Fort / Mannuthy Exit (0.757) — Left outer driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.675f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "1.1"),
        leftDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "LEFT", "6.5"),
        rightDest = KeralaSignDestination("പാലക്കാട്", "Palakkad", "LEFT", "58")
    ),
    // 8. Swaraj Round -> Before M.O. Road / Sakthan Exit (0.924) — Left outer driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.845f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "UP", "1.4"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.3"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "LEFT", "5.5")
    ),
    // 9. Shoranur Road -> Before Vadakke Bus Stand Turning — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_VADAKKE",
        frameFraction = 0.76f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "0.2"),
        leftDest = KeralaSignDestination("പൂങ്കുന്നം", "Punkunnam", "LEFT", "2.4"),
        rightDest = KeralaSignDestination("വിയ്യൂർ", "Viyyur", "RIGHT", "3.8")
    ),
    // 10. Puthanpalli Link -> Before East Fort T-Junction (1.000) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_PUTHANPALLI_LINK",
        frameFraction = 0.78f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഈസ്റ്റ് ഫോർട്ട്", "East Fort", "RIGHT", "0.2"),
        leftDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "LEFT", "0.5"),
        rightDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "RIGHT", "6.0")
    ),
    // 11. Mannuthy Road -> Before East Fort / Puthanpalli Junction (0.091) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_MANNUTHY",
        frameFraction = 0.055f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "UP", "6.0"),
        leftDest = KeralaSignDestination("ഈസ്റ്റ് ഫോർട്ട്", "East Fort", "LEFT", "0.3"),
        rightDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "RIGHT", "0.5")
    ),
    // 12. Mannuthy Road -> Before Mannuthy 3-Way NH 544 / Peechi Junction (1.000) — Overhead Gantry
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_MANNUTHY",
        frameFraction = 0.945f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "NATIONAL HIGHWAY • NH 544 JUNCTION",
        straightDest = KeralaSignDestination("പീച്ചി ഡാം", "PEECHI DAM", "UP", "14 KM"),
        leftDest = KeralaSignDestination("പാലക്കാട്", "PALAKKAD", "UP", "54 KM"),
        rightDest = KeralaSignDestination("ടോൾ പ്ലാസ", "TOLL PLAZA", "RIGHT", "6 KM")
    ),
    // 13. NH 544 -> Before Ollur / Thrissur City Link Junction (0.543) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.515f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ടോൾ പ്ലാസ", "Toll Plaza", "UP", "4.5"),
        leftDest = KeralaSignDestination("ആമ്പല്ലൂർ", "Amballur", "UP", "6.0"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ / തൃശ്ശൂർ", "Ollur", "RIGHT", "1.5")
    ),
    // 14. NH 544 -> Before Paliyekkara Toll Plaza — Overhead Highway Gantry
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.905f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "NATIONAL HIGHWAY • NH 544",
        straightDest = KeralaSignDestination("ടോൾ പ്ലാസ", "TOLL PLAZA", "UP", "500 M"),
        leftDest = KeralaSignDestination("ചാലക്കുടി", "CHALAKUDY", "UP", "22 KM"),
        rightDest = KeralaSignDestination("എറണാകുളം", "ERNAKULAM", "UP", "58 KM")
    ),
    // 15. Ollur Road -> Leaving Sakthan Junction before Kokkala Turning — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_OLLUR",
        frameFraction = 0.035f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur Town", "UP", "4.5"),
        leftDest = KeralaSignDestination("തൃശ്ശൂർ", "Thrissur", "LEFT", "1.2"),
        rightDest = KeralaSignDestination("കൂർക്കഞ്ചേരി", "Koorkenchery", "RIGHT", "2.0")
    ),
    // 16. Ollur Road -> Before NH 544 T-Junction (1.000) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_OLLUR",
        frameFraction = 0.955f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "LEFT", "8.0"),
        leftDest = KeralaSignDestination("ആമ്പല്ലൂർ", "Amballur", "RIGHT", "5.0"),
        rightDest = KeralaSignDestination("എറണാകുളം", "Ernakulam", "RIGHT", "60")
    ),
    // 17. Peechi Dam Road -> Before Pattikkad / Peechi Dam Turning — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_MANNUTHY_TO_PEECHI_DAM",
        frameFraction = 0.935f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("പീച്ചി ഡാം", "Peechi Dam", "UP", "1.2"),
        leftDest = KeralaSignDestination("പാലക്കാട്", "Palakkad", "LEFT", "45"),
        rightDest = KeralaSignDestination("പട്ടിക്കാട്", "Pattikkad", "RIGHT", "2.5")
    ),
    // 18. M.G. Road -> Before West Fort / SH 75 Continuation — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_WEST_FORT",
        frameFraction = 0.84f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("കാഞ്ഞാണി SH 75", "Kanjani", "UP", "12"),
        leftDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "UP", "26"),
        rightDest = KeralaSignDestination("അയ്യന്തോൾ", "Ayyanthole", "RIGHT", "2.5")
    ),
    // 19. SH 75 West Fort -> Before Kanjani Junction (1.000) — Overhead Highway Gantry
    KeralaRoadSignSpec(
        roadId = "ROAD_WEST_FORT_TO_KANJANI",
        frameFraction = 0.965f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "STATE HIGHWAY • SH 75 / SH 61",
        straightDest = KeralaSignDestination("ഗുരുവായൂർ", "GURUVAYUR", "UP", "19 KM"),
        leftDest = KeralaSignDestination("തൃപ്രയാർ", "TRIPRAYAR", "LEFT", "15 KM"),
        rightDest = KeralaSignDestination("ചാവക്കാട്", "CHAVAKKAD", "UP", "22 KM")
    ),
    // 20. Kanjani -> Before SH 61 Triprayar T-Junction (0.028) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
        frameFraction = 0.016f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "UP", "18"),
        leftDest = KeralaSignDestination("ചാവക്കാട്", "Chavakkad", "UP", "22"),
        rightDest = KeralaSignDestination("തൃപ്രയാർ SH 61", "Triprayar", "LEFT", "14")
    ),
    // 21. Kanjani -> Before Guruvayur Temple & Bus Stand Turning (0.406) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
        frameFraction = 0.392f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "Chavakkad Beach", "UP", "4.5"),
        leftDest = KeralaSignDestination("ഗുരുവായൂർ ക്ഷേത്രം", "Guruvayur Temple", "LEFT", "0.3"),
        rightDest = KeralaSignDestination("ഗുരുവായൂർ സ്റ്റാൻഡ്", "Guruvayur Stand", "RIGHT", "0.4")
    ),
    // 22. Guruvayur -> Before Chavakkad Coastal Turning (0.900) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
        frameFraction = 0.885f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "Chavakkad Beach", "UP", "0.8"),
        leftDest = KeralaSignDestination("തൃപ്രയാർ NH 66", "Triprayar", "UP", "18"),
        rightDest = KeralaSignDestination("പൊന്നാനി", "Ponnani", "RIGHT", "24")
    ),
    // 23. Chavakkad -> Before NH 66 Coastal South Curve (1.000) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
        frameFraction = 0.978f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("തൃപ്രയാർ NH 66", "Triprayar NH 66", "UP", "18"),
        leftDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "Chavakkad Beach", "UP", "0.3"),
        rightDest = KeralaSignDestination("പൊന്നാനി", "Ponnani", "RIGHT", "24")
    ),
    // 24. NH 66 -> Before SH 61 Triprayar / Kanjani Junction (0.689) — Overhead Highway Gantry
    KeralaRoadSignSpec(
        roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
        frameFraction = 0.670f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "NATIONAL HIGHWAY • NH 66 / SH 61",
        straightDest = KeralaSignDestination("തൃപ്രയാർ ബീച്ച്", "TRIPRAYAR BEACH", "UP", "2 KM"),
        leftDest = KeralaSignDestination("കാഞ്ഞാണി SH 61", "KANJANI", "LEFT", "14 KM"),
        rightDest = KeralaSignDestination("കൊടുങ്ങല്ലൂർ", "KODUNGALLUR", "UP", "25 KM")
    ),
    // 25. NH 66 -> Before Triprayar - Nattika Beach Terminus Turning — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
        frameFraction = 0.978f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("തൃപ്രയാർ ബീച്ച്", "Triprayar Beach", "UP", "0.2"),
        leftDest = KeralaSignDestination("തൃപ്രയാർ സ്റ്റാൻഡ്", "Triprayar Stand", "LEFT", "0.3"),
        rightDest = KeralaSignDestination("നാട്ടിക ബീച്ച്", "Nattika Beach", "RIGHT", "0.5")
    ),
    // 26. SH 61 -> Before Kanjani Main T-Junction (1.000) — Left driving verge
    KeralaRoadSignSpec(
        roadId = "ROAD_SH61_TRIPRAYAR_TO_KANJANI",
        frameFraction = 0.968f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "LEFT", "18"),
        leftDest = KeralaSignDestination("ചാവക്കാട്", "Chavakkad", "LEFT", "22"),
        rightDest = KeralaSignDestination("തൃശ്ശൂർ", "Thrissur", "RIGHT", "12")
    )
)

/**
 * Mathematically verifies that a world-space point (px, pz) is strictly outside the asphalt +
 * curb + shoulder envelope of EVERY road in the network.
 */
private fun isPointClearOfAllRoads(
    px: Float,
    pz: Float,
    roads: List<ThrissurOsmRoad>,
    extraMarginM: Float = 2.25f
): Boolean {
    for (road in roads) {
        for (f in road.frames) {
            val minClearDist = f.halfWidthM + extraMarginM
            if (hypot(px - f.pos.x, pz - f.pos.z) < minClearDist) {
                return false
            }
        }
    }
    return true
}

private fun isPointClearOfLandmarkBuildings(px: Float, pz: Float): Boolean {
    // Keep clear of Swaraj Round outer heritage buildings + 4.0m safety setback so signs never intersect walls
    if (px in 64.0f..78.5f && pz in 8.0f..32.5f) return false      // Paramekkavu Temple
    if (px in 43.0f..59.5f && pz in -51.5f..-34.5f) return false  // Indian Coffee House
    if (px in -29.0f..-3.0f && pz in -69.0f..-54.0f) return false // Round South Heritage Arcade
    if (px in -73.0f..-57.0f && pz in 12.5f..41.0f) return false  // City Centre Arcade
    if (px in 15.5f..44.5f && pz in 64.5f..79.5f) return false    // Round North Block
    if (hypot(px - 3.1f, pz - 9.5f) < 42.0f) return false         // Inner Thekkinkadu Maidan
    if (px in -25.0f..50.0f && pz in -220.0f..-141.0f) return false // Sakthan Stand yard
    return true
}

/**
 * Finds a road frame at or strictly BEFORE `targetFraction` along `road` where the left-verge
 * signboard (or overhead highway gantry) sits cleanly outside the asphalt + shoulder envelope,
 * clear of all buildings and any previously placed signboards.
 */
private fun findSafeSignPlacement(
    spec: KeralaRoadSignSpec,
    roads: List<ThrissurOsmRoad>,
    existing: List<CachedSignRenderItem>
): Triple<RoadFrame, Vec3, Vec3>? {
    val road = roads.firstOrNull { it.id == spec.roadId } ?: return null
    val frames = road.frames
    if (frames.size < 6) return null

    val baseIdx = (spec.frameFraction * (frames.size - 1)).toInt().coerceIn(2, frames.size - 3)

    if (spec.style == KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY || spec.side == 0) {
        for (delta in intArrayOf(0, -1, -2, -3, -4, -6, 1, 2)) {
            val idx = (baseIdx + delta).coerceIn(2, frames.size - 3)
            val f = frames[idx]
            val colOffset = f.halfWidthM + 2.85f
            val leftCol = Vec3(f.pos.x - f.right.x * colOffset, 0f, f.pos.z - f.right.z * colOffset)
            val rightCol = Vec3(f.pos.x + f.right.x * colOffset, 0f, f.pos.z + f.right.z * colOffset)
            if (isPointClearOfAllRoads(leftCol.x, leftCol.z, roads, 2.25f) &&
                isPointClearOfAllRoads(rightCol.x, rightCol.z, roads, 2.25f) &&
                isPointClearOfLandmarkBuildings(leftCol.x, leftCol.z) &&
                isPointClearOfLandmarkBuildings(rightCol.x, rightCol.z)
            ) {
                return Triple(f, leftCol, rightCol)
            }
        }
        return null
    }

    val boardW = 4.6f
    for (extraShift in floatArrayOf(0f, 1.0f, 2.0f)) {
        for (delta in intArrayOf(0, -1, -2, -3, -4, -6, -8, -10, 1)) {
            val idx = (baseIdx + delta).coerceIn(2, frames.size - 3)
            val f = frames[idx]
            val innerDist = f.halfWidthM + 2.55f + extraShift
            val outerDist = innerDist + boardW
            val d1 = if (spec.side < 0) -outerDist else innerDist
            val d2 = if (spec.side < 0) -innerDist else outerDist
            val leftVec = Vec3(f.pos.x + f.right.x * d1, 0f, f.pos.z + f.right.z * d1)
            val rightVec = Vec3(f.pos.x + f.right.x * d2, 0f, f.pos.z + f.right.z * d2)
            val midVec = Vec3((leftVec.x + rightVec.x) * 0.5f, 0f, (leftVec.z + rightVec.z) * 0.5f)
            if (isPointClearOfAllRoads(leftVec.x, leftVec.z, roads, 2.25f) &&
                isPointClearOfAllRoads(rightVec.x, rightVec.z, roads, 2.25f) &&
                isPointClearOfAllRoads(midVec.x, midVec.z, roads, 2.25f) &&
                isPointClearOfLandmarkBuildings(leftVec.x, leftVec.z) &&
                isPointClearOfLandmarkBuildings(rightVec.x, rightVec.z) &&
                existing.none { hypot(midVec.x - it.midX, midVec.z - it.midZ) < 11.5f }
            ) {
                return Triple(f, leftVec, rightVec)
            }
        }
    }
    return null
}

private class CachedSignRenderItem(
    val midX: Float,
    val midZ: Float,
    val faces: List<QuadFace3D>
)

private var cachedSignRenderItems: List<CachedSignRenderItem>? = null

private fun getOrBuildSignRenderCache(roads: List<ThrissurOsmRoad>): List<CachedSignRenderItem> {
    cachedSignRenderItems?.let { return it }
    val built = ArrayList<CachedSignRenderItem>(ThrissurAuthenticSignSpecs.size)
    for ((specIdx, spec) in ThrissurAuthenticSignSpecs.withIndex()) {
        val placement = findSafeSignPlacement(spec, roads, built) ?: continue
        val (_, leftPos, rightPos) = placement
        val midX = (leftPos.x + rightPos.x) * 0.5f
        val midZ = (leftPos.z + rightPos.z) * 0.5f
        val itemFaces = ArrayList<QuadFace3D>(32)

        if (spec.style == KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY) {
            for (colPos in arrayOf(leftPos, rightPos)) {
                addBoxFaces(
                    itemFaces,
                    colPos.x - 0.24f, 0.05f, colPos.z - 0.24f,
                    colPos.x + 0.24f, 8.25f, colPos.z + 0.24f,
                    topCol = MatGraniteLight,
                    sideCol = MatGraniteDark,
                    frontBackCol = MatGranitePaving,
                    stroke = null
                )
            }
            val minX = minOf(leftPos.x, rightPos.x) - 0.2f
            val maxX = maxOf(leftPos.x, rightPos.x) + 0.2f
            val minZ = minOf(leftPos.z, rightPos.z) - 0.2f
            val maxZ = maxOf(leftPos.z, rightPos.z) + 0.2f
            addBoxFaces(
                itemFaces,
                minX, 6.95f, minZ,
                maxX, 7.30f, maxZ,
                topCol = MatGraniteDark,
                sideCol = MatGranitePaving,
                frontBackCol = MatGraniteDark,
                stroke = null
            )

            val gLeft = Vec3(
                leftPos.x + (rightPos.x - leftPos.x) * 0.10f,
                0f,
                leftPos.z + (rightPos.z - leftPos.z) * 0.10f
            )
            val gRight = Vec3(
                leftPos.x + (rightPos.x - leftPos.x) * 0.90f,
                0f,
                leftPos.z + (rightPos.z - leftPos.z) * 0.90f
            )
            itemFaces.add(
                QuadFace3D(
                    v0 = Vec3(gLeft.x, 6.15f, gLeft.z),
                    v1 = Vec3(gRight.x, 6.15f, gRight.z),
                    v2 = Vec3(gRight.x, 8.25f, gRight.z),
                    v3 = Vec3(gLeft.x, 8.25f, gLeft.z),
                    color = Color(0xFF057A55),
                    strokeColor = null,
                    biasDist = -0.35f,
                    signboard = createKeralaRoadSignSpec(spec, specIdx)
                )
            )
        } else {
            for (t in floatArrayOf(0.18f, 0.82f)) {
                val px = leftPos.x + (rightPos.x - leftPos.x) * t
                val pz = leftPos.z + (rightPos.z - leftPos.z) * t
                addBoxFaces(
                    itemFaces,
                    px - 0.13f, 0.05f, pz - 0.13f,
                    px + 0.13f, 1.35f, pz + 0.13f,
                    topCol = MatHazardBlack,
                    sideCol = MatHazardBlack,
                    frontBackCol = MatHazardBlack,
                    stroke = null
                )
                addBoxFaces(
                    itemFaces,
                    px - 0.13f, 1.35f, pz - 0.13f,
                    px + 0.13f, 2.75f, pz + 0.13f,
                    topCol = MatRoadMarkingWhite,
                    sideCol = MatRoadMarkingWhite,
                    frontBackCol = MatPromenadeTile,
                    stroke = null
                )
            }
            val topY = 5.65f
            val bgColor = if (spec.style == KeralaSignStyle.ROUNDABOUT_DIAGRAM) Color(0xFF006D5B) else Color(0xFF046A4E)
            itemFaces.add(
                QuadFace3D(
                    v0 = Vec3(leftPos.x, 2.65f, leftPos.z),
                    v1 = Vec3(rightPos.x, 2.65f, rightPos.z),
                    v2 = Vec3(rightPos.x, topY, rightPos.z),
                    v3 = Vec3(leftPos.x, topY, leftPos.z),
                    color = bgColor,
                    strokeColor = null,
                    biasDist = -0.30f,
                    signboard = createKeralaRoadSignSpec(spec, specIdx)
                )
            )
        }
        built.add(CachedSignRenderItem(midX, midZ, itemFaces))
    }
    cachedSignRenderItems = built
    return built
}

private fun collectNhaiSignboardFaces(
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>,
    outFaces: MutableList<QuadFace3D>
) {
    if (roads.isEmpty()) return
    val items = getOrBuildSignRenderCache(roads)
    val maxDistSq = 155f * 155f
    val eyeX = camEye.x
    val eyeZ = camEye.z
    for (i in 0 until items.size) {
        val item = items[i]
        val dx = eyeX - item.midX
        val dz = eyeZ - item.midZ
        if (dx * dx + dz * dz <= maxDistSq) {
            outFaces.addAll(item.faces)
        }
    }
}


