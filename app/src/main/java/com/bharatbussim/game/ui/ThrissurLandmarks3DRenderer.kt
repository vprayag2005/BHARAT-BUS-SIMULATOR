package com.bharatbussim.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.TrafficSignalAspect
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
    drawThrissurRailwayStationGround(rig, path, camEye)
    drawVadakkeBusStandGround(rig, path, camEye)
    drawPaliyekkaraTollPlazaGround(rig, path, camEye)
    drawGuruvayurTempleAndStandGround(rig, path, camEye)
    drawChavakkadBeachGround(rig, path, camEye)
    drawTriprayarBeachGround(rig, path, camEye)
}

internal fun collectThrissurLandmarkSceneFaces(
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>,
    manikandanalSignal: TrafficSignalAspect,
    outFaces: MutableList<QuadFace3D>
) {
    collectSwarajRoundFaces(camEye, roads, outFaces)
    collectThrissurRailwayStationFaces(camEye, outFaces)
    collectManikandanalSignalAndStreetPropsFaces(camEye, manikandanalSignal, outFaces)
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

/**
 * Builds a smooth N-sided 3D cylindrical or octagonal frustum with true outward radial normals.
 */
private fun addCylinderFrustumFaces(
    faces: MutableList<QuadFace3D>,
    cx: Float,
    cz: Float,
    yBot: Float,
    yTop: Float,
    rBot: Float,
    rTop: Float,
    baseCol: Color,
    capCol: Color = baseCol,
    sides: Int = 8,
    bias: Float = 0f
) {
    val twoPi = (2.0 * Math.PI).toFloat()
    for (i in 0 until sides) {
        val a0 = i * twoPi / sides
        val a1 = (i + 1) * twoPi / sides
        val c0 = cos(a0); val s0 = sin(a0)
        val c1 = cos(a1); val s1 = sin(a1)
        val v0 = Vec3(cx + c0 * rBot, yBot, cz + s0 * rBot)
        val v1 = Vec3(cx + c1 * rBot, yBot, cz + s1 * rBot)
        val v2 = Vec3(cx + c1 * rTop, yTop, cz + s1 * rTop)
        val v3 = Vec3(cx + c0 * rTop, yTop, cz + s0 * rTop)
        val nx = (c0 + c1) * 0.5f
        val nz = (s0 + s1) * 0.5f
        faces.add(QuadFace3D(v0, v1, v2, v3, baseCol, null, bias, 0f, nx, 0f, nz))
    }
    if (rTop > 0.05f) {
        val rCap = rTop * 0.72f
        faces.add(
            QuadFace3D(
                Vec3(cx - rCap, yTop, cz - rCap),
                Vec3(cx + rCap, yTop, cz - rCap),
                Vec3(cx + rCap, yTop, cz + rCap),
                Vec3(cx - rCap, yTop, cz + rCap),
                capCol, null, bias, 0f, 0f, 1f, 0f
            )
        )
    }
}

/**
 * Builds an arched barrel-vault canopy roof (for bus stands and railway station platforms).
 */
private fun addArchedCanopyRoofFaces(
    faces: MutableList<QuadFace3D>,
    minX: Float,
    maxX: Float,
    minZ: Float,
    maxZ: Float,
    eaveY: Float,
    archRise: Float,
    topCol: Color,
    underCol: Color = Color(0xFF334155),
    archAlongX: Boolean = true
) {
    val archSteps = 6
    val segsLong = (((if (archAlongX) (maxZ - minZ) else (maxX - minX)) / 12.0f).toInt()).coerceIn(2, 10)
    for (a in 0 until archSteps) {
        val t0 = a.toFloat() / archSteps
        val t1 = (a + 1).toFloat() / archSteps
        val h0 = eaveY + sin(t0 * Math.PI.toFloat()) * archRise
        val h1 = eaveY + sin(t1 * Math.PI.toFloat()) * archRise
        for (s in 0 until segsLong) {
            val u0 = s.toFloat() / segsLong
            val u1 = (s + 1).toFloat() / segsLong
            if (archAlongX) {
                val x0 = minX + t0 * (maxX - minX)
                val x1 = minX + t1 * (maxX - minX)
                val z0 = minZ + u0 * (maxZ - minZ)
                val z1 = minZ + u1 * (maxZ - minZ)
                faces.add(QuadFace3D(Vec3(x0, h0, z0), Vec3(x1, h1, z0), Vec3(x1, h1, z1), Vec3(x0, h0, z1), topCol, null, 1.8f, 0f, 0f, 1f, 0f))
                faces.add(QuadFace3D(Vec3(x0, h0 - 0.12f, z1), Vec3(x1, h1 - 0.12f, z1), Vec3(x1, h1 - 0.12f, z0), Vec3(x0, h0 - 0.12f, z0), underCol, null, 2.2f, 0f, 0f, -1f, 0f))
            } else {
                val z0 = minZ + t0 * (maxZ - minZ)
                val z1 = minZ + t1 * (maxZ - minZ)
                val x0 = minX + u0 * (maxX - minX)
                val x1 = minX + u1 * (maxX - minX)
                faces.add(QuadFace3D(Vec3(x0, h0, z0), Vec3(x1, h0, z0), Vec3(x1, h1, z1), Vec3(x0, h1, z1), topCol, null, 1.8f, 0f, 0f, 1f, 0f))
                faces.add(QuadFace3D(Vec3(x0, h0 - 0.12f, z1), Vec3(x1, h0 - 0.12f, z1), Vec3(x1, h1 - 0.12f, z0), Vec3(x0, h1 - 0.12f, z0), underCol, null, 2.2f, 0f, 0f, -1f, 0f))
            }
        }
    }
}

/**
 * Builds a rounded 8-sided 3-band organic 3D foliage dome (24 smooth polyhedral faces per crown lobe).
 */
private fun addOrganicFoliageDome(
    faces: MutableList<QuadFace3D>,
    cx: Float,
    cyBase: Float,
    cz: Float,
    radiusXZ: Float,
    heightY: Float,
    leafShadow: Color,
    leafMid: Color,
    leafCrown: Color,
    sides: Int = 8
) {
    val twoPi = (2.0 * Math.PI).toFloat()
    val r0 = radiusXZ * 0.68f
    val r1 = radiusXZ * 1.00f
    val r2 = radiusXZ * 0.72f
    val r3 = radiusXZ * 0.22f
    val y0 = cyBase
    val y1 = cyBase + heightY * 0.34f
    val y2 = cyBase + heightY * 0.74f
    val y3 = cyBase + heightY

    for (i in 0 until sides) {
        val a0 = i * twoPi / sides
        val a1 = (i + 1) * twoPi / sides
        val c0 = cos(a0); val s0 = sin(a0)
        val c1 = cos(a1); val s1 = sin(a1)
        val nx = (c0 + c1) * 0.5f
        val nz = (s0 + s1) * 0.5f

        // Lower under-crown skirt (shaded foliage)
        faces.add(
            QuadFace3D(
                Vec3(cx + c0 * r0, y0, cz + s0 * r0),
                Vec3(cx + c1 * r0, y0, cz + s1 * r0),
                Vec3(cx + c1 * r1, y1, cz + s1 * r1),
                Vec3(cx + c0 * r1, y1, cz + s0 * r1),
                leafShadow, null, 0.8f, 0f, nx, -0.35f, nz
            )
        )
        // Middle full foliage band
        faces.add(
            QuadFace3D(
                Vec3(cx + c0 * r1, y1, cz + s0 * r1),
                Vec3(cx + c1 * r1, y1, cz + s1 * r1),
                Vec3(cx + c1 * r2, y2, cz + s1 * r2),
                Vec3(cx + c0 * r2, y2, cz + s0 * r2),
                leafMid, null, 0.8f, 0f, nx, 0.25f, nz
            )
        )
        // Upper sunlit crown dome
        faces.add(
            QuadFace3D(
                Vec3(cx + c0 * r2, y2, cz + s0 * r2),
                Vec3(cx + c1 * r2, y2, cz + s1 * r2),
                Vec3(cx + c1 * r3, y3, cz + s1 * r3),
                Vec3(cx + c0 * r3, y3, cz + s0 * r3),
                leafCrown, null, 0.8f, 0f, nx * 0.6f, 0.8f, nz * 0.6f
            )
        )
    }
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
            baseColor = bgColor,
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
// 1. ZONE 1.1: SWARAJ ROUND, THEKKINKADU MAIDAN & VADAKKUMNATHAN TEMPLE
//    (True 3D Multi-Polyhedral Trees, Circular Aaltharas, 18m Vatta Sreekovil, Zero Dummy Boxes)
// ============================================================================

private val MatMaidanLateriteSoil = Color(0xFF8B3A22)
private val MatMaidanNadappatha = Color(0xFF9C472C)
private val MatWalkwayAsphaltGrey = Color(0xFF737B84)
private val MatWalkwayBorder = Color(0xFF949CA6)
private val MatTreeTrunkBark = Color(0xFF4A2F1D)
private val MatTreeAerialRoot = Color(0xFF5A3A24)
private val MatTreeCanopyShadow = Color(0xFF284622)
private val MatTreeCanopyMid = Color(0xFF3E6332)
private val MatTreeCanopyCrown = Color(0xFF527D42)
private val MatCastIronBollard = Color(0xFF1E293B)

private fun isInsideMaidanNadaOpening(pt: Vec3, tcx: Float, tcz: Float): Boolean {
    val dx = pt.x - tcx
    val dz = pt.z - tcz
    if (dz < -14.0f && abs(dx - 3.5f) < 4.5f) return true
    if (dz > 14.0f && abs(dx - 1.5f) < 4.5f) return true
    if (dx < -14.0f && abs(dz - (-3.5f)) < 5.5f) return true
    if (dx > 14.0f && abs(dz - (-2.5f)) < 4.5f) return true
    return false
}

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

    val tcx = 1.8f
    val tcz = 4.5f

    val swarajRoad = roads.firstOrNull { it.id == "ROAD_SWARAJ_ROUND" }
    val frames = swarajRoad?.frames

    // 1. Thekkinkadu Maidan (65-Acre Sacred Hillock): Rolling grassy mound (#4F6239 to #586E40)
    if (frames != null && frames.size >= 4) {
        val centerTop = Vec3(cx, 1.05f, cz)
        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            val inOff0 = f0.halfWidthM + 1.45f
            val inOff1 = f1.halfWidthM + 1.45f
            val p0 = Vec3(f0.pos.x + f0.right.x * inOff0, 0.42f, f0.pos.z + f0.right.z * inOff0)
            val p1 = Vec3(f1.pos.x + f1.right.x * inOff1, 0.42f, f1.pos.z + f1.right.z * inOff1)
            val sectorCol = if ((i and 1) == 0) MatMaidanGrass else MatMaidanGrassUpper
            if (rig.buildClippedQuadPath(path, centerTop, p0, p1, centerTop)) {
                drawPath(path, sectorCol)
            }
        }

        // Outer Border: 2.0m wide paved pedestrian sidewalk tiled in alternating grey and terracotta pavers
        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            if (f0.leftEdgeCovered || f1.leftEdgeCovered) continue
            val inOff0 = -f0.halfWidthM - 0.32f
            val outOff0 = -f0.halfWidthM - 2.32f
            val inOff1 = -f1.halfWidthM - 0.32f
            val outOff1 = -f1.halfWidthM - 2.32f
            val v0 = Vec3(f0.pos.x + f0.right.x * outOff0, 0.068f, f0.pos.z + f0.right.z * outOff0)
            val v1 = Vec3(f0.pos.x + f0.right.x * inOff0, 0.068f, f0.pos.z + f0.right.z * inOff0)
            val v2 = Vec3(f1.pos.x + f1.right.x * inOff1, 0.068f, f1.pos.z + f1.right.z * inOff1)
            val v3 = Vec3(f1.pos.x + f1.right.x * outOff1, 0.068f, f1.pos.z + f1.right.z * outOff1)
            val paverCol = if ((i and 1) == 0) MatPromenadeTile else Color(0xFFB43E24)
            if (rig.buildClippedQuadPath(path, v0, v1, v2, v3)) {
                drawPath(path, paverCol)
            }
        }
    }

    // 2. Upper Rolling Grassy Mound Crown (#586E40)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx - 24.5f, 0.78f, tcz - 23.5f),
            Vec3(tcx + 25.5f, 0.78f, tcz - 23.5f),
            Vec3(tcx + 26.5f, 0.78f, tcz + 27.5f),
            Vec3(tcx - 23.5f, 0.78f, tcz + 27.5f)
        )
    ) {
        drawPath(path, MatMaidanGrassUpper)
    }

    // 3. Earthy Reddish Laterite Stone Pedestrian Paths (Nadappatha)
    drawMaidanArcWalkway(rig, path, cx, cz, 25.2f, 27.0f, -148f, 12f, 20, 0.62f, MatMaidanNadappatha)
    drawMaidanArcWalkway(rig, path, cx, cz, 29.2f, 30.8f, -142f, 8f, 20, 0.56f, MatMaidanLateriteSoil)
    drawMaidanArcWalkway(rig, path, cx, cz, 24.5f, 26.3f, 102f, 196f, 16, 0.62f, MatMaidanNadappatha)
    drawMaidanArcWalkway(rig, path, cx, cz, 28.5f, 30.2f, 106f, 192f, 16, 0.56f, MatMaidanLateriteSoil)
    drawMaidanArcWalkway(rig, path, cx + 18.0f, cz - 14.5f, 4.5f, 5.8f, 0f, 360f, 16, 0.58f, MatMaidanNadappatha)
    drawMaidanArcWalkway(rig, path, cx + 18.0f, cz - 14.5f, 7.8f, 9.0f, -60f, 200f, 14, 0.56f, MatMaidanLateriteSoil)

    // 4. Outer Pradakshinapatha Laterite Stone Walkway Ring around Sree Vadakkumnathan Temple Wall
    val wR = 13.5f
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx - wR - 2.6f, 0.96f, tcz - wR - 2.6f),
            Vec3(tcx + wR + 2.6f, 0.96f, tcz - wR - 2.6f),
            Vec3(tcx + wR + 2.6f, 0.96f, tcz + wR + 2.6f),
            Vec3(tcx - wR - 2.6f, 0.96f, tcz + wR + 2.6f)
        )
    ) {
        drawPath(path, MatMaidanNadappatha)
    }

    // 5. Sacred Temple Pond (Vadakkumnathan Kulam)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx - 2.5f, 0.88f, tcz + wR + 4.0f),
            Vec3(tcx + 5.5f, 0.88f, tcz + wR + 4.0f),
            Vec3(tcx + 5.5f, 0.88f, tcz + wR + 11.5f),
            Vec3(tcx - 2.5f, 0.88f, tcz + wR + 11.5f)
        )
    ) {
        drawPath(path, MatTemplePondWater)
    }
}

/**
 * Builds a realistic organic 3D Kerala tree (Teak / Mahogany / Rain Tree) with a flared 8-sided
 * bark trunk, primary branches, and a multi-lobe rounded polyhedral foliage canopy (zero pyramids, zero boxes).
 */
private fun addRealisticMaidanTree3D(
    faces: MutableList<QuadFace3D>,
    tx: Float,
    tz: Float,
    baseY: Float,
    crownRadius: Float,
    treeHeight: Float
) {
    val trunkRBot = (crownRadius * 0.17f).coerceIn(0.32f, 0.68f)
    val trunkRTop = trunkRBot * 0.62f
    val trunkTopY = baseY + treeHeight * 0.44f

    // 1. Flared root base + tapered 8-sided cylindrical bark trunk
    addCylinderFrustumFaces(faces, tx, tz, baseY, baseY + 0.75f, trunkRBot * 1.38f, trunkRBot, MatTreeTrunkBark, sides = 8, bias = 1.4f)
    addCylinderFrustumFaces(faces, tx, tz, baseY + 0.75f, trunkTopY, trunkRBot, trunkRTop, MatTreeTrunkBark, sides = 8, bias = 1.4f)

    // 2. 3 Sub-Canopy Foliage Lobes + 1 High Central Sunlit Crown Dome
    val subY = baseY + treeHeight * 0.34f
    val subH = treeHeight * 0.46f
    val subR = crownRadius * 0.76f
    for (bIdx in 0 until 3) {
        val ang = (bIdx * 2.0 * Math.PI / 3.0 + 0.4).toFloat()
        val lx = tx + cos(ang) * (crownRadius * 0.36f)
        val lz = tz + sin(ang) * (crownRadius * 0.36f)
        addOrganicFoliageDome(
            faces, lx, subY, lz,
            radiusXZ = subR,
            heightY = subH,
            leafShadow = MatTreeCanopyShadow,
            leafMid = MatTreeCanopyMid,
            leafCrown = MatTreeCanopyCrown,
            sides = 8
        )
    }
    addOrganicFoliageDome(
        faces, tx, baseY + treeHeight * 0.46f, tz,
        radiusXZ = crownRadius * 0.88f,
        heightY = treeHeight * 0.54f,
        leafShadow = MatTreeCanopyShadow,
        leafMid = MatTreeCanopyMid,
        leafCrown = MatTreeCanopyCrown,
        sides = 8
    )
}

/**
 * Builds a Giant Ancient Banyan Tree (Aalmaram, Ficus benghalensis) encircled by a 1.0m high
 * 12-sided circular dressed laterite Aalthara platform, with cylindrical hanging aerial prop roots
 * and a 5-lobe spreading canopy dome.
 */
private fun addAncientBanyanTreeWithAalthara3D(
    faces: MutableList<QuadFace3D>,
    bx: Float,
    bz: Float,
    baseY: Float,
    aaltharaRadius: Float = 3.2f,
    crownRadius: Float = 6.2f,
    treeHeight: Float = 13.5f
) {
    val aTopY = baseY + 1.00f
    // 1. 12-sided circular dressed laterite stone Aalthara platform (1.0m high) + granite coping rim
    addCylinderFrustumFaces(faces, bx, bz, baseY, aTopY - 0.12f, aaltharaRadius, aaltharaRadius * 0.96f, MatLateriteStone, MatLateriteStepTop, sides = 12)
    addCylinderFrustumFaces(faces, bx, bz, aTopY - 0.12f, aTopY, aaltharaRadius * 1.04f, aaltharaRadius * 1.04f, MatLateriteStepTop, MatGranitePaving, sides = 12)

    // 2. Massive Fluted Banyan Trunk + 5-Lobe Spreading Canopy
    addRealisticMaidanTree3D(faces, bx, bz, aTopY, crownRadius, treeHeight)

    // 3. Cylindrical Aerial Prop Roots hanging down from the lower boughs to the Aalthara stone platform
    val boughY = aTopY + treeHeight * 0.38f
    for (rIdx in 0 until 6) {
        val ang = Math.toRadians((rIdx * 60 + 18).toDouble()).toFloat()
        val rx = bx + cos(ang) * (aaltharaRadius * 0.74f)
        val rz = bz + sin(ang) * (aaltharaRadius * 0.74f)
        addCylinderFrustumFaces(faces, rx, rz, aTopY, boughY, 0.11f, 0.08f, MatTreeAerialRoot, sides = 6, bias = 1.2f)
    }
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
    if (dx * dx + dz * dz > 215f * 215f) return

    val existing = cachedSwarajRoundFaces
    if (existing != null) {
        outFaces.addAll(existing)
        return
    }

    val tcx = 1.8f
    val tcz = 4.5f
    val wR = 13.5f
    val templeBaseY = 1.10f

    val faces = ArrayList<QuadFace3D>(2200)
    val swarajFrames = roads.firstOrNull { it.id == "ROAD_SWARAJ_ROUND" }?.frames

    // A. INNER SHOULDER: 1.2m High Continuous Weathered Laterite Stone Boundary Wall (Chuttumathil, #8B3A22)
    //    & OUTER BORDER: Cylindrical Cast-Iron Ornamental Bollards & Chain-Link Railings
    if (swarajFrames != null && swarajFrames.size >= 4) {
        for (i in 0 until swarajFrames.size - 1) {
            val f0 = swarajFrames[i]
            val f1 = swarajFrames[i + 1]
            val midCheck = Vec3(
                (f0.pos.x + f1.pos.x) * 0.5f + f0.right.x * (f0.halfWidthM + 1.0f),
                0.2f,
                (f0.pos.z + f1.pos.z) * 0.5f + f0.right.z * (f0.halfWidthM + 1.0f)
            )
            if (!isInsideMaidanNadaOpening(midCheck, tcx, tcz)) {
                val wOut0 = f0.halfWidthM + 0.42f
                val wIn0 = f0.halfWidthM + 1.05f
                val wOut1 = f1.halfWidthM + 0.42f
                val wIn1 = f1.halfWidthM + 1.05f
                val yBot = 0.06f
                val yWallTop = 1.14f
                val yCopingTop = 1.26f

                val pOut0 = Vec3(f0.pos.x + f0.right.x * wOut0, yWallTop, f0.pos.z + f0.right.z * wOut0)
                val pOut1 = Vec3(f1.pos.x + f1.right.x * wOut1, yWallTop, f1.pos.z + f1.right.z * wOut1)
                val pIn1 = Vec3(f1.pos.x + f1.right.x * wIn1, yWallTop, f1.pos.z + f1.right.z * wIn1)
                val pIn0 = Vec3(f0.pos.x + f0.right.x * wIn0, yWallTop, f0.pos.z + f0.right.z * wIn0)

                faces.add(
                    QuadFace3D(
                        Vec3(pOut0.x, yBot, pOut0.z),
                        Vec3(pOut1.x, yBot, pOut1.z),
                        pOut1,
                        pOut0,
                        baseColor = if ((i and 1) == 0) MatLateriteStone else MatLateriteDark,
                        biasDist = 3.5f
                    )
                )
                val cOut0 = Vec3(pOut0.x, yCopingTop, pOut0.z)
                val cOut1 = Vec3(pOut1.x, yCopingTop, pOut1.z)
                val cIn1 = Vec3(pIn1.x, yCopingTop, pIn1.z)
                val cIn0 = Vec3(pIn0.x, yCopingTop, pIn0.z)
                faces.add(
                    QuadFace3D(
                        cOut0, cOut1, cIn1, cIn0,
                        baseColor = MatLateriteStepTop,
                        biasDist = 3.4f
                    )
                )
            }

            if (!f0.leftEdgeCovered && !f1.leftEdgeCovered && (i % 2 == 0)) {
                val bolOff0 = -f0.halfWidthM - 0.42f
                val bolOff1 = -f1.halfWidthM - 0.42f
                val bx0 = f0.pos.x + f0.right.x * bolOff0
                val bz0 = f0.pos.z + f0.right.z * bolOff0
                val bx1 = f1.pos.x + f1.right.x * bolOff1
                val bz1 = f1.pos.z + f1.right.z * bolOff1

                addCylinderFrustumFaces(faces, bx0, bz0, 0.07f, 0.88f, 0.10f, 0.08f, MatCastIronBollard, MatTempleGoldDeep, sides = 6)
                faces.add(
                    QuadFace3D(
                        Vec3(bx0, 0.68f, bz0),
                        Vec3(bx1, 0.68f, bz1),
                        Vec3(bx1, 0.74f, bz1),
                        Vec3(bx0, 0.74f, bz0),
                        baseColor = MatCastIronBollard,
                        biasDist = 1.5f
                    )
                )
            }
        }
    }

    // B. 4 LATERITE NADAPPATHA AVENUES CONNECTING SWARAJ ROUND TO THE 4 GOPURAMS
    fun addNadappathaAvenue(vRoadL: Vec3, vRoadR: Vec3, vGateR: Vec3, vGateL: Vec3) {
        faces.add(
            QuadFace3D(
                vRoadL, vRoadR, vGateR, vGateL,
                baseColor = MatMaidanNadappatha,
                strokeColor = MatLateriteDark,
                biasDist = 2.8f
            )
        )
    }

    addNadappathaAvenue(Vec3(tcx + 0.5f, 0.08f, cz - 36.5f), Vec3(tcx + 5.5f, 0.08f, cz - 36.5f), Vec3(tcx + 5.0f, templeBaseY, tcz - wR), Vec3(tcx + 1.0f, templeBaseY, tcz - wR))
    addNadappathaAvenue(Vec3(tcx + 3.2f, 0.08f, cz + 38.0f), Vec3(tcx - 1.8f, 0.08f, cz + 38.0f), Vec3(tcx - 1.5f, templeBaseY, tcz + wR), Vec3(tcx + 2.5f, templeBaseY, tcz + wR))
    addNadappathaAvenue(Vec3(cx - 37.0f, 0.08f, tcz - 0.2f), Vec3(cx - 37.0f, 0.08f, tcz - 2.6f), Vec3(tcx - wR - 1.5f, templeBaseY, tcz - 2.4f), Vec3(tcx - wR - 1.5f, templeBaseY, tcz - 0.4f))
    addNadappathaAvenue(Vec3(cx - 37.0f, 0.08f, tcz - 4.2f), Vec3(cx - 37.0f, 0.08f, tcz - 6.6f), Vec3(tcx - wR - 1.5f, templeBaseY, tcz - 6.4f), Vec3(tcx - wR - 1.5f, templeBaseY, tcz - 4.4f))
    addNadappathaAvenue(Vec3(cx - 25.5f, 0.48f, cz + 26.5f), Vec3(cx - 23.2f, 0.48f, cz + 27.5f), Vec3(tcx - wR - 1.2f, templeBaseY, tcz + 1.5f), Vec3(tcx - wR - 3.2f, templeBaseY, tcz + 0.5f))
    addNadappathaAvenue(Vec3(cx + 36.5f, 0.08f, tcz - 4.8f), Vec3(cx + 36.5f, 0.08f, tcz - 0.2f), Vec3(tcx + wR, templeBaseY, tcz - 0.5f), Vec3(tcx + wR, templeBaseY, tcz - 4.5f))

    // C. SREE VADAKKUMNATHAN TEMPLE COMPOUND
    addBoxFaces(faces, tcx - wR + 0.4f, 0.55f, tcz - wR + 0.4f, tcx + wR - 0.4f, templeBaseY, tcz + wR - 0.4f, MatGranitePaving, MatLateriteStone, bias = 4.5f)

    fun addMaryadaWallSegment(minX: Float, minZ: Float, maxX: Float, maxZ: Float, isNorthSouth: Boolean) {
        addBoxFaces(faces, minX, templeBaseY, minZ, maxX, templeBaseY + 1.55f, maxZ, MatKeralaWhitewash, MatKeralaWhitewash, MatKeralaCreamWall)
        addBoxFaces(faces, minX, templeBaseY + 1.55f, minZ, maxX, templeBaseY + 2.45f, maxZ, MatLateriteStone, MatLateriteStone, MatLateriteDark)
        val overhang = 0.26f
        addPitchedKeralaRoof(
            faces,
            minX - overhang, maxX + overhang,
            minZ - overhang, maxZ + overhang,
            baseY = templeBaseY + 2.40f,
            ridgeY = templeBaseY + 2.95f,
            insetX = if (isNorthSouth) 0.12f else (maxX - minX) * 0.45f,
            insetZ = if (isNorthSouth) (maxZ - minZ) * 0.45f else 0.12f,
            roofColor = MatTerracottaTile
        )
    }

    addMaryadaWallSegment(tcx - wR, tcz - wR, tcx - 0.8f, tcz - wR + 0.85f, isNorthSouth = true)
    addMaryadaWallSegment(tcx + 6.8f, tcz - wR, tcx + wR, tcz - wR + 0.85f, isNorthSouth = true)
    addMaryadaWallSegment(tcx - wR, tcz + wR - 0.85f, tcx - 3.3f, tcz + wR, isNorthSouth = true)
    addMaryadaWallSegment(tcx + 4.3f, tcz + wR - 0.85f, tcx + wR, tcz + wR, isNorthSouth = true)
    addMaryadaWallSegment(tcx - wR, tcz - wR, tcx - wR + 0.85f, tcz - 7.2f, isNorthSouth = false)
    addMaryadaWallSegment(tcx - wR, tcz + 0.4f, tcx - wR + 0.85f, tcz + wR, isNorthSouth = false)
    addMaryadaWallSegment(tcx + wR - 0.85f, tcz - wR, tcx + wR, tcz - 6.3f, isNorthSouth = false)
    addMaryadaWallSegment(tcx + wR - 0.85f, tcz + 1.3f, tcx + wR, tcz + wR, isNorthSouth = false)

    fun addAuthenticVadakkumnathanGopuram(
        gx: Float,
        gz: Float,
        halfW: Float,
        halfD: Float,
        facesNorthSouth: Boolean
    ) {
        val y0 = templeBaseY
        addBoxFaces(faces, gx - halfW - 0.45f, y0, gz - halfD - 0.45f, gx + halfW + 0.45f, y0 + 0.42f, gz + halfD + 0.45f, MatKeralaWhitewash, MatLateriteStone, MatGraniteDark)
        addBoxFaces(faces, gx - halfW - 0.22f, y0 + 0.42f, gz - halfD - 0.22f, gx + halfW + 0.22f, y0 + 0.88f, gz + halfD + 0.22f, MatKeralaWhitewash, MatKeralaWhitewash, MatKeralaCreamWall)

        if (facesNorthSouth) {
            val doorHalfW = 1.10f
            addBoxFaces(faces, gx - halfW, y0 + 0.88f, gz - halfD, gx - doorHalfW, y0 + 3.65f, gz + halfD, MatTeakWoodCarved, MatKeralaWhitewash, MatKeralaWhitewash)
            addBoxFaces(faces, gx + doorHalfW, y0 + 0.88f, gz - halfD, gx + halfW, y0 + 3.65f, gz + halfD, MatTeakWoodCarved, MatKeralaWhitewash, MatKeralaWhitewash)
            addBoxFaces(faces, gx - doorHalfW, y0 + 0.88f, gz - halfD + 0.20f, gx + doorHalfW, y0 + 3.65f, gz + halfD - 0.20f, MatTeakWoodDeep, MatTeakWoodDeep, MatTeakWoodDeep)
        } else {
            val doorHalfD = 1.10f
            addBoxFaces(faces, gx - halfW, y0 + 0.88f, gz - halfD, gx + halfW, y0 + 3.65f, gz - doorHalfD, MatTeakWoodCarved, MatKeralaWhitewash, MatKeralaWhitewash)
            addBoxFaces(faces, gx - halfW, y0 + 0.88f, gz + doorHalfD, gx + halfW, y0 + 3.65f, gz + halfD, MatTeakWoodCarved, MatKeralaWhitewash, MatKeralaWhitewash)
            addBoxFaces(faces, gx - halfW + 0.20f, y0 + 0.88f, gz - doorHalfD, gx + halfW - 0.20f, y0 + 3.65f, gz + doorHalfD, MatTeakWoodDeep, MatTeakWoodDeep, MatTeakWoodDeep)
        }

        addPitchedKeralaRoof(faces, gx - halfW - 1.10f, gx + halfW + 1.10f, gz - halfD - 1.10f, gz + halfD + 1.10f, y0 + 3.50f, y0 + 5.45f, 1.20f, 1.20f, MatTerracottaTile, MatTeakWoodCarved)
        addBoxFaces(faces, gx - halfW * 0.80f, y0 + 5.30f, gz - halfD * 0.80f, gx + halfW * 0.80f, y0 + 7.05f, gz + halfD * 0.80f, MatTeakWoodCarved, MatTeakWoodCarved, MatTeakWoodCarved)
        addPitchedKeralaRoof(faces, gx - halfW * 1.02f, gx + halfW * 1.02f, gz - halfD * 1.02f, gz + halfD * 1.02f, y0 + 6.90f, y0 + 8.95f, 1.10f, 1.10f, MatTerracottaRoofDark, MatTeakWoodCarved)
        addPitchedKeralaRoof(
            faces,
            gx - halfW * 0.80f, gx + halfW * 0.80f,
            gz - halfD * 0.80f, gz + halfD * 0.80f,
            baseY = y0 + 8.80f, ridgeY = y0 + 11.40f,
            insetX = if (facesNorthSouth) halfW * 0.26f else halfW * 0.70f,
            insetZ = if (facesNorthSouth) halfD * 0.70f else halfD * 0.26f,
            roofColor = MatTerracottaTile,
            eaveColor = MatTeakWoodCarved
        )
        val mukW = if (facesNorthSouth) 1.35f else halfW * 0.74f
        val mukD = if (facesNorthSouth) halfD * 0.74f else 1.35f
        addPitchedKeralaRoof(
            faces,
            gx - mukW, gx + mukW,
            gz - mukD, gz + mukD,
            baseY = y0 + 8.40f, ridgeY = y0 + 11.05f,
            insetX = if (facesNorthSouth) mukW * 0.85f else 0.20f,
            insetZ = if (facesNorthSouth) 0.20f else mukD * 0.85f,
            roofColor = MatTeakWoodCarved,
            eaveColor = MatTempleGoldDeep
        )
        // Cylindrical Golden Brass Finials (Stupika / Thazhikakkudam, #FACC15)
        for (finialOff in floatArrayOf(-1.2f, 0f, 1.2f)) {
            val fx = if (facesNorthSouth) gx + finialOff else gx
            val fz = if (facesNorthSouth) gz else gz + finialOff
            addCylinderFrustumFaces(faces, fx, fz, y0 + 11.30f, y0 + 12.25f, 0.22f, 0.04f, MatTempleGold, sides = 6)
        }
    }

    addAuthenticVadakkumnathanGopuram(tcx + 3.0f, tcz - wR, halfW = 3.9f, halfD = 2.35f, facesNorthSouth = true)
    addAuthenticVadakkumnathanGopuram(tcx + 0.5f, tcz + wR, halfW = 3.9f, halfD = 2.35f, facesNorthSouth = true)
    addAuthenticVadakkumnathanGopuram(tcx - wR, tcz - 3.4f, halfW = 2.35f, halfD = 3.9f, facesNorthSouth = false)
    addAuthenticVadakkumnathanGopuram(tcx + wR, tcz - 2.5f, halfW = 2.35f, halfD = 3.9f, facesNorthSouth = false)

    // Inner Circular Sanctum Sanctorum (Vatta Sreekovil, 18m diameter) with Conical Copper Roof & Golden Stupika
    val sreekovilCx = tcx + 1.0f
    val sreekovilCz = tcz + 1.2f
    val vattaRadius = 9.0f
    addCylinderFrustumFaces(faces, sreekovilCx, sreekovilCz, templeBaseY + 0.05f, templeBaseY + 3.85f, vattaRadius * 0.88f, vattaRadius * 0.88f, MatKeralaWhitewash, sides = 16)
    addCylinderFrustumFaces(faces, sreekovilCx, sreekovilCz, templeBaseY + 3.70f, templeBaseY + 9.65f, vattaRadius, 0.38f, MatCopperRoof, MatTempleGold, sides = 16)
    addCylinderFrustumFaces(faces, sreekovilCx, sreekovilCz, templeBaseY + 9.60f, templeBaseY + 11.45f, 0.45f, 0.05f, MatTempleGold, sides = 8)

    // Koothambalam (Temple Dance Theatre) with Sloped Copper-Ridged Hipped Roof
    addBoxFaces(faces, tcx - 12.2f, templeBaseY + 0.05f, tcz + 1.5f, tcx - 8.2f, templeBaseY + 0.75f, tcz + 9.8f, MatGraniteLight, MatKeralaWhitewash, MatGraniteDark)
    addBoxFaces(faces, tcx - 11.9f, templeBaseY + 0.75f, tcz + 1.8f, tcx - 8.5f, templeBaseY + 3.10f, tcz + 9.5f, MatTeakWoodCarved, MatTeakWoodCarved, MatTeakWoodDeep)
    addPitchedKeralaRoof(faces, tcx - 12.6f, tcx - 7.8f, tcz + 1.1f, tcz + 10.2f, templeBaseY + 2.95f, templeBaseY + 7.10f, 2.1f, 2.6f, MatCopperRoof, MatTeakWoodCarved)

    // Courtyard Artifacts: Cylindrical 21m Golden Flagstaff (Kodimaram) & 9m 7-Tiered Circular Bronze Deepastambhams
    val kodiX = tcx - 8.5f
    val kodiZ = tcz - 3.4f
    addCylinderFrustumFaces(faces, kodiX, kodiZ, templeBaseY, templeBaseY + 1.40f, 0.85f, 0.65f, MatKeralaWhitewash, sides = 8)
    addCylinderFrustumFaces(faces, kodiX, kodiZ, templeBaseY + 1.40f, templeBaseY + 21.0f, 0.28f, 0.14f, MatTempleGold, sides = 8)
    for (ringIdx in 1..8) {
        val ry = templeBaseY + ringIdx * 2.2f
        addCylinderFrustumFaces(faces, kodiX, kodiZ, ry, ry + 0.20f, 0.38f, 0.38f, MatTempleGoldDeep, sides = 8)
    }

    for ((dpX, dpZ) in arrayOf(Pair(tcx + 3.0f, tcz - wR - 3.2f), Pair(tcx - 10.5f, tcz - 3.4f))) {
        addCylinderFrustumFaces(faces, dpX, dpZ, templeBaseY, templeBaseY + 9.0f, 0.28f, 0.14f, MatCopperRoof, sides = 8)
        for (tier in 1..7) {
            val ty = templeBaseY + tier * 1.12f
            val tr = (1.15f - tier * 0.10f).coerceAtLeast(0.36f)
            addCylinderFrustumFaces(faces, dpX, dpZ, ty, ty + 0.18f, tr, tr * 0.85f, MatTempleGold, sides = 8)
        }
    }

    // D. GIANT ANCIENT BANYAN TREES WITH 12-SIDED CIRCULAR AALTHARA PLATFORMS + ORGANIC TEAK & MAHOGANY GROVE
    addAncientBanyanTreeWithAalthara3D(faces, bx = tcx + 8.5f, bz = tcz - 24.5f, baseY = 0.46f, aaltharaRadius = 3.2f, crownRadius = 6.2f, treeHeight = 13.8f)
    addAncientBanyanTreeWithAalthara3D(faces, bx = tcx - 24.5f, bz = tcz - 8.5f, baseY = 0.46f, aaltharaRadius = 3.2f, crownRadius = 6.2f, treeHeight = 13.8f)
    addAncientBanyanTreeWithAalthara3D(faces, bx = tcx - 5.2f, bz = tcz + 23.5f, baseY = 0.48f, aaltharaRadius = 2.9f, crownRadius = 5.6f, treeHeight = 12.8f)
    addAncientBanyanTreeWithAalthara3D(faces, bx = tcx + 22.5f, bz = tcz + 3.5f, baseY = 0.48f, aaltharaRadius = 2.9f, crownRadius = 5.6f, treeHeight = 12.8f)

    val maidanTrees = arrayOf(
        floatArrayOf(tcx - 16.5f, tcz - 12.5f, 4.4f, 10.2f),
        floatArrayOf(tcx - 12.0f, tcz - 17.5f, 4.8f, 10.8f),
        floatArrayOf(tcx - 5.5f, tcz - 17.8f, 4.5f, 10.4f),
        floatArrayOf(tcx + 11.2f, tcz - 17.5f, 4.6f, 10.4f),
        floatArrayOf(tcx + 16.8f, tcz - 9.8f, 4.4f, 9.8f),
        floatArrayOf(tcx + 16.5f, tcz + 11.2f, 5.0f, 11.2f),
        floatArrayOf(tcx + 10.5f, tcz + 17.2f, 4.6f, 10.4f),
        floatArrayOf(tcx + 6.5f, tcz + 22.5f, 4.4f, 9.8f),
        floatArrayOf(tcx - 12.2f, tcz + 16.5f, 5.0f, 11.0f),
        floatArrayOf(tcx - 17.5f, tcz + 10.2f, 4.6f, 10.2f),
        floatArrayOf(tcx - 17.5f, tcz + 3.8f, 4.4f, 9.8f),
        floatArrayOf(tcx - 14.8f, tcz + 21.5f, 4.2f, 9.5f),
        floatArrayOf(cx + 12.0f, cz + 25.0f, 4.5f, 10.2f),
        floatArrayOf(cx + 19.5f, cz + 24.5f, 4.8f, 10.6f),
        floatArrayOf(cx + 24.5f, cz + 18.0f, 4.4f, 9.8f),
        floatArrayOf(cx + 18.0f, cz - 14.5f, 4.6f, 10.2f),
        floatArrayOf(cx - 24.5f, cz - 19.5f, 4.4f, 9.8f),
        floatArrayOf(cx - 28.5f, cz + 14.5f, 4.2f, 9.5f),
        floatArrayOf(cx - 20.5f, cz + 27.5f, 4.4f, 9.8f)
    )
    for (t in maidanTrees) {
        addRealisticMaidanTree3D(faces, t[0], t[1], 0.52f, t[2], t[3])
    }

    // E. SWARAJ ROUND OUTER RING LANDMARKS (Paramekkavu Temple & Heritage Arcades with Pitched Kerala Roofs)
    addBoxFaces(faces, 69.5f, 0.06f, 10.0f, 70.4f, 2.60f, 30.0f, MatTerracottaTile, MatKeralaWhitewash, MatLateriteStone)
    addBoxFaces(faces, 68.8f, 0.06f, 16.5f, 74.5f, 4.40f, 23.5f, MatTeakWoodCarved, MatKeralaWhitewash, MatKeralaCreamWall)
    addPitchedKeralaRoof(faces, 67.8f, 75.5f, 15.5f, 24.5f, 4.25f, 6.80f, 1.4f, 1.4f, MatTerracottaTile)
    addPitchedKeralaRoof(faces, 68.6f, 74.7f, 16.3f, 23.7f, 6.65f, 9.40f, 2.4f, 2.2f, MatTerracottaRoofDark)
    addCylinderFrustumFaces(faces, 66.6f, 20.0f, 0.06f, 12.50f, 0.36f, 0.18f, MatTempleGold, sides = 8)

    addBoxFaces(faces, 46.5f, 0.06f, -48.5f, 56.5f, 8.40f, -37.5f, MatTerracottaRoofDark, MatCoffeeHouseRed, MatCoffeeHouseRed)
    addPitchedKeralaRoof(faces, 45.7f, 57.3f, -49.3f, -36.7f, 8.25f, 10.60f, 4.2f, 4.2f, MatTerracottaTile)
    addBoxFaces(faces, -26.0f, 0.06f, -66.0f, -6.0f, 7.60f, -57.0f, MatArcadeRoofSlate, MatArcadeCream, MatArcadeSandstone)
    addPitchedKeralaRoof(faces, -26.6f, -5.4f, -66.6f, -56.4f, 7.50f, 9.50f, 2.5f, 3.8f, MatTerracottaTile)
    addBoxFaces(faces, -70.5f, 0.06f, 15.0f, -60.0f, 8.20f, 38.0f, MatArcadeRoofSlate, MatArcadeCream, MatArcadeSandstone)
    addPitchedKeralaRoof(faces, -71.2f, -59.3f, 14.3f, 38.7f, 8.10f, 10.10f, 4.2f, 3.5f, MatTerracottaTile)
    addBoxFaces(faces, 18.5f, 0.06f, 67.5f, 41.5f, 7.60f, 76.5f, MatTerracottaRoofDark, MatKeralaCreamWall, MatKeralaWhitewash)
    addPitchedKeralaRoof(faces, 17.8f, 42.2f, 66.8f, 77.2f, 7.50f, 9.40f, 3.0f, 4.0f, MatTerracottaTile)

    if (swarajFrames != null) {
        cachedSwarajRoundFaces = faces
    }
    outFaces.addAll(faces)
}

// ============================================================================
// 1B. ZONE 1.3: THRISSUR CENTRAL RAILWAY STATION (TCR) & FULL-LENGTH 235-METER
//     INDIAN RAILWAYS EXPRESS TRAIN (SCULPTED WAP-7 LOCOMOTIVE + 10 COUPLED EXPRESS COACHES)
// ============================================================================

private val MatTcrStuccoCream = Color(0xFFEAE4D7)
private val MatTcrBrickArch = Color(0xFF991B1B)
private val MatTcrLouverWood = Color(0xFF4A2C18)
private val MatIrDarkBlue = Color(0xFF1E3A8A)
private val MatIrCoachBlue = Color(0xFF1D4ED8)
private val MatLhbCrimson = Color(0xFFB91C1C)
private val MatLhbSilverBand = Color(0xFFCBD5E1)
private val MatBallastBasalt = Color(0xFF334155)
private val MatPscSleeper = Color(0xFF94A3B8)
private val MatUic60SteelRail = Color(0xFFE2E8F0)
private val MatWap7Cream = Color(0xFFF8FAFC)
private val MatWap7Crimson = Color(0xFFDC2626)

private fun DrawScope.drawThrissurRailwayStationGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - (-45f), camEye.z - (-110f)) > 380f) return

    // 1. Station Forecourt along Station Road / Kokkala (X = -35.0m..-22.0m, Z = -165.0m..-45.0m)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-35.0f, 0.058f, -165.0f),
            Vec3(-22.0f, 0.058f, -165.0f),
            Vec3(-22.0f, 0.058f, -45.0f),
            Vec3(-35.0f, 0.058f, -45.0f)
        )
    ) {
        drawPath(path, MatTerminalConcrete)
    }

    // 2. Full-Length 245m Basalt Ballast Bed (450mm deep bed across Z = -225.0m .. +20.0m)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-66.0f, 0.060f, -225.0f),
            Vec3(-46.0f, 0.060f, -225.0f),
            Vec3(-46.0f, 0.060f, 20.0f),
            Vec3(-66.0f, 0.060f, 20.0f)
        )
    ) {
        drawPath(path, MatBallastBasalt)
    }

    // 3. Broad-Gauge UIC-60 Steel Rails (1.676m gauge) on Prestressed Concrete (PSC) Sleepers
    for (trackCenterX in floatArrayOf(-51.5f, -58.5f)) {
        if (hypot(camEye.x - trackCenterX, camEye.z - (-110f)) < 185f) {
            var sz = -220.0f
            while (sz <= 16.0f) {
                rig.projectSegmentClipped(
                    Vec3(trackCenterX - 1.35f, 0.063f, sz),
                    Vec3(trackCenterX + 1.35f, 0.063f, sz)
                )?.let { seg ->
                    drawLine(MatPscSleeper, seg.first, seg.second, strokeWidth = 2.0f)
                }
                sz += 2.2f
            }
        }
        for (railOff in floatArrayOf(-0.84f, 0.84f)) {
            rig.projectSegmentClipped(
                Vec3(trackCenterX + railOff, 0.066f, -225.0f),
                Vec3(trackCenterX + railOff, 0.066f, 20.0f)
            )?.let { seg ->
                drawLine(MatUic60SteelRail, seg.first, seg.second, strokeWidth = 2.6f)
            }
        }
    }
}

/**
 * Builds a sculpted 3D Indian Railways WAP-7 Electric Locomotive (Royapuram/Erode Livery)
 * with tapered aerodynamic cab noses, 5-panel cambered roof crown, Co-Co 6-axle bogies with 3D wheelsets,
 * cowcatcher, wrap-around windshields, and raised double-arm diamond pantograph.
 */
private fun addSculptedWap7Locomotive3D(
    faces: MutableList<QuadFace3D>,
    cx: Float,
    zRear: Float,
    zFront: Float
) {
    val hw = 1.58f
    val noseLen = 1.45f
    val zBody0 = zRear + noseLen
    val zBody1 = zFront - noseLen

    // 1. Twin Co-Co 3-Axle Heavy Bogies & 6 Wheelsets with Primary Springs (Y = 0.08m .. 1.15m)
    for (bogieZ in floatArrayOf(zRear + 4.2f, zFront - 4.2f)) {
        addBoxFaces(faces, cx - 1.36f, 0.36f, bogieZ - 2.6f, cx + 1.36f, 1.12f, bogieZ + 2.6f, MatCastIronBollard, MatCastIronBollard)
        for (axleOff in floatArrayOf(-1.85f, 0f, 1.85f)) {
            val az = bogieZ + axleOff
            for (wx in floatArrayOf(cx - 1.42f, cx + 1.42f)) {
                addBoxFaces(faces, wx - 0.08f, 0.08f, az - 0.52f, wx + 0.08f, 1.04f, az + 0.52f, MatGraniteDark, Color(0xFF111827))
            }
        }
    }
    // Underframe main transformer & air reservoirs between bogies
    addBoxFaces(faces, cx - 1.28f, 0.42f, zRear + 6.8f, cx + 1.28f, 1.15f, zFront - 6.8f, MatGraniteDark, MatCastIronBollard)

    // 2. Front & Rear Slanted Sloped Steel Cowcatchers (Cattle Guards) & Buffer Beams with Yellow Hazard Chevrons
    faces.add(QuadFace3D(Vec3(cx - 1.48f, 0.22f, zFront + 0.35f), Vec3(cx + 1.48f, 0.22f, zFront + 0.35f), Vec3(cx + 1.54f, 0.88f, zFront), Vec3(cx - 1.54f, 0.88f, zFront), MatWap7Crimson))
    faces.add(QuadFace3D(Vec3(cx + 1.48f, 0.22f, zRear - 0.35f), Vec3(cx - 1.48f, 0.22f, zRear - 0.35f), Vec3(cx - 1.54f, 0.88f, zRear), Vec3(cx + 1.54f, 0.88f, zRear), MatWap7Crimson))
    addBoxFaces(faces, cx - 1.56f, 0.88f, zFront - 0.25f, cx + 1.56f, 1.22f, zFront + 0.12f, MatRoadMarkingYellow, MatWap7Crimson)
    addBoxFaces(faces, cx - 1.56f, 0.88f, zRear - 0.12f, cx + 1.56f, 1.22f, zRear + 0.25f, MatRoadMarkingYellow, MatWap7Crimson)

    // 3. Main Sculpted Locomotive Body Shell (Z = zBody0 .. zBody1) with Royapuram/Erode White-Cream & Bold Crimson Waist Band
    addBoxFaces(faces, cx - hw, 1.15f, zBody0, cx + hw, 1.82f, zBody1, MatWap7Cream, MatWap7Cream)
    addBoxFaces(faces, cx - hw - 0.02f, 1.82f, zBody0, cx + hw + 0.02f, 2.52f, zBody1, MatWap7Crimson, MatWap7Crimson)
    addBoxFaces(faces, cx - hw, 2.52f, zBody0, cx + hw, 3.72f, zBody1, MatWap7Cream, MatWap7Cream)

    // Side machine-room cooling grille louvers & porthole windows along both flanks
    var gz = zBody0 + 1.8f
    while (gz <= zBody1 - 1.8f) {
        addBoxFaces(faces, cx - hw - 0.03f, 2.68f, gz - 0.55f, cx + hw + 0.03f, 3.32f, gz + 0.55f, MatGraniteDark, MatArcadeGlass)
        gz += 2.4f
    }

    // 4. Tapered Aerodynamic Front & Rear Cab Noses with Recessed Twin Windshields & Twin Projector Headlamps
    val noseW = hw * 0.84f
    // Front tapered lower nose (Y = 1.15m .. 2.52m, Z = zBody1 .. zFront)
    faces.add(QuadFace3D(Vec3(cx - noseW, 1.15f, zFront), Vec3(cx + noseW, 1.15f, zFront), Vec3(cx + noseW, 2.52f, zFront), Vec3(cx - noseW, 2.52f, zFront), MatWap7Crimson))
    faces.add(QuadFace3D(Vec3(cx + hw, 1.15f, zBody1), Vec3(cx + noseW, 1.15f, zFront), Vec3(cx + noseW, 2.52f, zFront), Vec3(cx + hw, 2.52f, zBody1), MatWap7Cream))
    faces.add(QuadFace3D(Vec3(cx - noseW, 1.15f, zFront), Vec3(cx - hw, 1.15f, zBody1), Vec3(cx - hw, 2.52f, zBody1), Vec3(cx - noseW, 2.52f, zFront), MatWap7Cream))
    // Front raked upper cab & twin windshields (Y = 2.52m .. 3.72m)
    faces.add(QuadFace3D(Vec3(cx - noseW, 2.52f, zFront), Vec3(cx + noseW, 2.52f, zFront), Vec3(cx + hw * 0.88f, 3.72f, zBody1 + 0.35f), Vec3(cx - hw * 0.88f, 3.72f, zBody1 + 0.35f), MatWap7Cream))
    for (winSide in floatArrayOf(-0.62f, 0.62f)) {
        faces.add(
            QuadFace3D(
                Vec3(cx + winSide - 0.46f, 2.68f, zFront - 0.14f),
                Vec3(cx + winSide + 0.46f, 2.68f, zFront - 0.14f),
                Vec3(cx + winSide + 0.44f, 3.46f, zBody1 + 0.48f),
                Vec3(cx + winSide - 0.44f, 3.46f, zBody1 + 0.48f),
                MatArcadeGlass, biasDist = -0.05f
            )
        )
    }
    // Illuminated twin front halogen headlamps + top marker light
    for (hlX in floatArrayOf(-0.82f, 0.82f)) {
        addBoxFaces(faces, cx + hlX - 0.22f, 1.92f, zFront - 0.04f, cx + hlX + 0.22f, 2.18f, zFront + 0.06f, Color(0xFFFEF08A), Color(0xFFFFFBEB))
    }

    // Rear tapered cab nose (Z = zRear .. zBody0)
    faces.add(QuadFace3D(Vec3(cx + noseW, 1.15f, zRear), Vec3(cx - noseW, 1.15f, zRear), Vec3(cx - noseW, 2.52f, zRear), Vec3(cx + noseW, 2.52f, zRear), MatWap7Crimson))
    faces.add(QuadFace3D(Vec3(cx + noseW, 2.52f, zRear), Vec3(cx - noseW, 2.52f, zRear), Vec3(cx - hw * 0.88f, 3.72f, zBody0 - 0.35f), Vec3(cx + hw * 0.88f, 3.72f, zBody0 - 0.35f), MatWap7Cream))

    // 5. 3-Strip Cambered Roof Crown (Y = 3.72m .. 4.18m) & High-Voltage Raised Double-Arm Diamond Pantograph
    addArchedCanopyRoofFaces(faces, cx - hw, cx + hw, zBody0, zBody1, 3.72f, 0.44f, MatGraniteDark, MatGraniteDark, archAlongX = true)
    // Raised front pantograph at zFront - 3.6m touching the 25kV contact wire at Y = 5.95m
    val pantoZ = zFront - 3.8f
    addBoxFaces(faces, cx - 0.75f, 4.12f, pantoZ - 0.85f, cx + 0.75f, 4.32f, pantoZ + 0.85f, MatGraniteLight, MatWap7Crimson)
    faces.add(QuadFace3D(Vec3(cx - 0.55f, 4.32f, pantoZ), Vec3(cx + 0.55f, 4.32f, pantoZ), Vec3(cx + 0.35f, 5.15f, pantoZ - 0.95f), Vec3(cx - 0.35f, 5.15f, pantoZ - 0.95f), MatWap7Crimson))
    faces.add(QuadFace3D(Vec3(cx - 0.35f, 5.15f, pantoZ - 0.95f), Vec3(cx + 0.35f, 5.15f, pantoZ - 0.95f), Vec3(cx + 0.65f, 5.95f, pantoZ), Vec3(cx - 0.65f, 5.95f, pantoZ), MatWap7Crimson))
    addBoxFaces(faces, cx - 0.95f, 5.90f, pantoZ - 0.18f, cx + 0.95f, 5.98f, pantoZ + 0.18f, MatUic60SteelRail, MatUic60SteelRail)
}

/**
 * Builds a sculpted full-scale Indian Railways Express Passenger Coach (with curved tumblehome lower skirt,
 * cambered arched corrugated roof, rooftop AC condenser pods, 8 individual recessed solar-glass windows per side,
 * recessed end boarding doors, twin Fiat/ICF bogies with wheelsets, and flexible black vestibule gangway bellows).
 */
private fun addSculptedExpressCoach3D(
    faces: MutableList<QuadFace3D>,
    cx: Float,
    z0: Float,
    z1: Float,
    isLhbLivery: Boolean
) {
    val hw = 1.56f
    val bodyCol = if (isLhbLivery) MatLhbCrimson else MatIrCoachBlue
    val upperCol = if (isLhbLivery) MatLhbSilverBand else MatIrDarkBlue
    val stripeCol = if (isLhbLivery) MatWap7Cream else MatRoadMarkingYellow

    // 1. Twin 2-Axle Fiat/ICF Bogies with Wheelsets & Underframe Battery/Air-Brake Cylinders
    for (bogieZ in floatArrayOf(z0 + 2.8f, z1 - 2.8f)) {
        addBoxFaces(faces, cx - 1.32f, 0.32f, bogieZ - 1.55f, cx + 1.32f, 1.08f, bogieZ + 1.55f, MatCastIronBollard, MatCastIronBollard)
        for (axOff in floatArrayOf(-1.05f, 1.05f)) {
            for (wx in floatArrayOf(cx - 1.38f, cx + 1.38f)) {
                addBoxFaces(faces, wx - 0.08f, 0.08f, bogieZ + axOff - 0.46f, wx + 0.08f, 0.96f, bogieZ + axOff + 0.46f, MatGraniteDark, Color(0xFF111827))
            }
        }
    }
    addBoxFaces(faces, cx - 1.15f, 0.48f, z0 + 4.8f, cx + 1.15f, 1.08f, z1 - 4.8f, MatCastIronBollard, MatGraniteDark)

    // 2. Sculpted Multi-Tier Coach Body Shell (Lower Skirt -> Waist Band -> Window Belt -> Cantrail)
    addBoxFaces(faces, cx - hw * 0.92f, 0.92f, z0, cx + hw * 0.92f, 1.18f, z1, MatGraniteDark, MatCastIronBollard)
    addBoxFaces(faces, cx - hw, 1.18f, z0, cx + hw, 2.22f, z1, bodyCol, bodyCol)
    addBoxFaces(faces, cx - hw - 0.015f, 2.22f, z0, cx + hw + 0.015f, 2.40f, z1, stripeCol, stripeCol)
    addBoxFaces(faces, cx - hw, 2.40f, z0, cx + hw, 3.48f, z1, upperCol, upperCol)
    addBoxFaces(faces, cx - hw - 0.015f, 3.48f, z0, cx + hw + 0.015f, 3.62f, z1, stripeCol, stripeCol)

    // 3. 8 Individual Recessed Tinted Solar-Glass Windows per Side + End Boarding Doors
    val winStart = z0 + 1.9f
    val winEnd = z1 - 1.9f
    val winStep = (winEnd - winStart) / 7f
    for (wIdx in 0..7) {
        val wz = winStart + wIdx * winStep
        addBoxFaces(
            faces,
            cx - hw - 0.03f, 2.52f, wz - 0.56f,
            cx + hw + 0.03f, 3.26f, wz + 0.56f,
            topCol = MatUic60SteelRail,
            sideCol = MatArcadeGlass,
            frontBackCol = MatArcadeGlass
        )
    }
    // End boarding doors with footsteps at z0 + 0.75m and z1 - 0.75m
    for (dz in floatArrayOf(z0 + 0.72f, z1 - 0.72f)) {
        addBoxFaces(faces, cx - hw - 0.02f, 1.18f, dz - 0.42f, cx + hw + 0.02f, 3.38f, dz + 0.42f, MatGraniteDark, Color(0xFF0F172A))
    }

    // 4. Cambered Arched Corrugated Coach Roof (Y = 3.62m .. 4.08m) + Rooftop AC Condenser Pods
    addArchedCanopyRoofFaces(faces, cx - hw, cx + hw, z0, z1, 3.62f, 0.42f, MatGranitePaving, MatGraniteDark, archAlongX = true)
    for (acZ in floatArrayOf(z0 + 3.5f, z1 - 3.5f)) {
        addBoxFaces(faces, cx - 0.92f, 3.98f, acZ - 1.4f, cx + 0.92f, 4.18f, acZ + 1.4f, MatGraniteLight, MatGranitePaving)
    }

    // 5. Flexible Black Accordion Vestibule Gangway Bellows at Coach End (z0 - 0.55m .. z0)
    addBoxFaces(faces, cx - 0.95f, 1.25f, z0 - 0.58f, cx + 0.95f, 3.55f, z0 + 0.05f, MatHazardBlack, MatHazardBlack)
}

private var cachedTcrStationFaces: List<QuadFace3D>? = null

private fun collectThrissurRailwayStationFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - (-45f)
    val dz = camEye.z - (-110f)
    if (dx * dx + dz * dz > 260f * 260f) return

    val existing = cachedTcrStationFaces
    if (existing != null) {
        outFaces.addAll(existing)
        return
    }

    val built = ArrayList<QuadFace3D>(3200)

    // 1. British-Cochin Colonial Station Building (X = -43.5m..-35.0m, Z = -148.0m..-62.0m)
    //    with Cylindrical Colonial Columns, Arched Brick Colonnade, Pitched Terracotta Roof & Octagonal Clock Tower
    addBoxFaces(built, -43.5f, 0.06f, -148.0f, -35.0f, 6.80f, -62.0f, MatTerracottaRoofDark, MatTcrStuccoCream, MatTcrStuccoCream)
    var archZ = -142.0f
    while (archZ <= -68.0f) {
        addCylinderFrustumFaces(built, -34.85f, archZ - 2.6f, 0.06f, 3.45f, 0.28f, 0.24f, MatTcrBrickArch, sides = 8)
        addBoxFaces(built, -35.12f, 3.15f, archZ - 2.6f, -34.78f, 3.65f, archZ + 2.6f, MatTcrBrickArch, MatTcrBrickArch)
        addBoxFaces(built, -35.12f, 4.15f, archZ - 1.05f, -34.80f, 5.95f, archZ + 1.05f, MatTcrBrickArch, MatTcrLouverWood)
        archZ += 6.2f
    }

    // Central Porte-Cochère Arrival Porch (X = -35.0m..-28.2m, Z = -114.0m..-96.0m) with Cylindrical Columns
    for (pz in floatArrayOf(-112.5f, -105.0f, -97.5f)) {
        addCylinderFrustumFaces(built, -28.8f, pz, 0.06f, 5.15f, 0.36f, 0.30f, MatTcrStuccoCream, sides = 8)
    }
    addBoxFaces(built, -35.0f, 4.85f, -114.0f, -28.2f, 5.60f, -96.0f, MatTerracottaTile, MatTcrStuccoCream, MatTcrBrickArch)
    addPitchedKeralaRoof(built, -35.2f, -27.8f, -114.6f, -95.4f, 5.55f, 7.50f, 1.2f, 3.2f, MatTerracottaTile, MatTcrLouverWood)

    // Main Red-Tile Pitched Roof & Sculpted Octagonal Station Clock Tower
    addPitchedKeralaRoof(built, -44.2f, -34.3f, -149.0f, -61.0f, 6.70f, 9.50f, 3.6f, 4.2f, MatTerracottaTile, MatTcrLouverWood)
    addCylinderFrustumFaces(built, -38.5f, -105.0f, 8.80f, 13.00f, 2.65f, 2.45f, MatTcrStuccoCream, sides = 8)
    addCylinderFrustumFaces(built, -38.5f, -105.0f, 13.00f, 15.60f, 2.85f, 0.20f, MatTerracottaTile, MatTempleGold, sides = 8)

    // Single Clean Indian Railways Trilingual Station Nameboard mounted flush on the Porte-Cochère Fascia
    val tcrMainSign = createBannerSignSpec(
        cacheKey = "TCR_TRILINGUAL_MAIN_SIGN",
        bgColorInt = 0xFF1E3A8A.toInt(),
        borderColorInt = 0xFFFACC15.toInt(),
        titleText = "തൃശ്ശൂർ  •  THRISSUR  •  त्रिशूर",
        subText = "THRISSUR CENTRAL RAILWAY STATION (TCR)",
        titleColorInt = android.graphics.Color.WHITE,
        subColorInt = 0xFFFACC15.toInt(),
        isDoubleSided = false,
        bmpWidth = 864,
        bmpHeight = 144
    )
    built.add(
        QuadFace3D(
            Vec3(-28.05f, 4.88f, -97.2f),
            Vec3(-28.05f, 4.88f, -112.8f),
            Vec3(-28.05f, 6.08f, -112.8f),
            Vec3(-28.05f, 6.08f, -97.2f),
            baseColor = MatIrDarkBlue,
            biasDist = -0.35f,
            signboard = tcrMainSign
        )
    )

    // 2. Full-Length 235m Platform 1 (X = -49.0m..-43.5m) & Platform 2 (X = -65.5m..-61.0m) across Z = -220.0m..+15.0m
    //    with Cylindrical Steel Canopy Pillars & Arched Blue Barrel-Vault Passenger Sheds (#1E3A8A)
    for ((pMinX, pMaxX) in arrayOf(Pair(-49.0f, -43.5f), Pair(-65.5f, -61.0f))) {
        addBoxFaces(built, pMinX, 0.06f, -220.0f, pMaxX, 0.82f, 15.0f, MatGranitePaving, MatGraniteDark, MatGraniteDark)
        val edgeX = if (pMinX > -55f) pMinX else pMaxX - 0.45f
        addBoxFaces(built, edgeX, 0.82f, -220.0f, edgeX + 0.45f, 0.85f, 15.0f, MatRoadMarkingYellow, MatRoadMarkingYellow)

        val midPx = (pMinX + pMaxX) * 0.5f
        var colZ = -205.0f
        while (colZ <= 5.0f) {
            addCylinderFrustumFaces(built, midPx, colZ, 0.82f, 5.15f, 0.18f, 0.16f, MatGraniteLight, sides = 8)
            colZ += 15.0f
        }
        addArchedCanopyRoofFaces(built, pMinX - 0.35f, pMaxX + 0.35f, -210.0f, 8.0f, 5.10f, 0.68f, MatIrDarkBlue, MatGraniteDark, archAlongX = true)
    }

    // 3. 25 kV AC Overhead Catenary Electrification Portals every 35m along the full 235m track bed
    var mastZ = -210.0f
    while (mastZ <= 10.0f) {
        for (mastX in floatArrayOf(-48.6f, -61.4f)) {
            addCylinderFrustumFaces(built, mastX, mastZ, 0.82f, 7.45f, 0.16f, 0.14f, MatGraniteLight, sides = 6)
        }
        addBoxFaces(built, -61.4f, 6.65f, mastZ - 0.12f, -48.6f, 6.90f, mastZ + 0.12f, MatPromenadeTile, MatGraniteLight)
        mastZ += 35.0f
    }
    for (wireX in floatArrayOf(-51.5f, -58.5f)) {
        addBoxFaces(built, wireX - 0.04f, 5.95f, -222.0f, wireX + 0.04f, 6.02f, 16.0f, MatCopperRoof, MatCopperRoof)
        addBoxFaces(built, wireX - 0.03f, 6.62f, -222.0f, wireX + 0.03f, 6.68f, 16.0f, MatCopperRoof, MatCopperRoof)
    }

    // 4. Covered Steel Truss Foot Over Bridge (FOB) with Arched Blue Roof spanning across all tracks
    addBoxFaces(built, -65.2f, 6.40f, -88.5f, -44.0f, 8.40f, -84.8f, MatIrDarkBlue, MatGranitePaving, MatIrDarkBlue)
    addArchedCanopyRoofFaces(built, -65.4f, -43.8f, -88.8f, -84.5f, 8.40f, 0.55f, MatIrDarkBlue, MatGraniteDark, archAlongX = false)
    addBoxFaces(built, -46.2f, 0.82f, -88.5f, -44.0f, 8.40f, -81.5f, MatIrDarkBlue, MatGraniteLight, MatIrDarkBlue)
    addBoxFaces(built, -65.2f, 0.82f, -88.5f, -63.0f, 8.40f, -81.5f, MatIrDarkBlue, MatGraniteLight, MatIrDarkBlue)

    // 5. FULL-LENGTH 215-METER INDIAN RAILWAYS EXPRESS TRAIN ON PLATFORM 1 (Track 1 at X = -51.5m):
    //    Sculpted Southern Railway WAP-7 Electric Locomotive (Z = -22.0m .. -3.0m) +
    //    10 Full-Scale Coupled Express Passenger Coaches (Z = -182.0m .. -22.5m)
    val trainX = -51.5f
    addSculptedWap7Locomotive3D(built, cx = trainX, zRear = -22.0f, zFront = -3.0f)

    for (coachIdx in 0 until 10) {
        val cz1 = -22.6f - coachIdx * 16.0f
        val cz0 = cz1 - 15.4f
        // Alternate LHB AC Crimson-Silver Express rake & ICF Blue Express coaches
        addSculptedExpressCoach3D(
            faces = built,
            cx = trainX,
            z0 = cz0,
            z1 = cz1,
            isLhbLivery = (coachIdx < 5)
        )
    }

    cachedTcrStationFaces = built
    outFaces.addAll(built)
}

// ============================================================================
// 1C. ZONE 1.4: MANIKANDANAL TRAFFIC SIGNAL (ROUND SOUTH)
//     Sculpted Cylindrical Galvanized Steel Mast Arm (6.5m height) & 3-Aspect 300mm LED Heads
//     Cycle: Red (30s) -> Green (25s) -> Amber (4s) — Zero Dummy Street Stalls!
// ============================================================================

private var cachedManikandanalMastFaces: List<QuadFace3D>? = null

private fun getOrBuildManikandanalMastFaces(): List<QuadFace3D> {
    cachedManikandanalMastFaces?.let { return it }
    val built = ArrayList<QuadFace3D>(140)
    val postX = 23.6f
    val postZ = -37.2f

    // Moulded concrete base + tapered cylindrical galvanized steel mast column (0.06m .. 6.65m)
    addCylinderFrustumFaces(built, postX, postZ, 0.06f, 0.65f, 0.34f, 0.28f, MatGraniteDark, sides = 8)
    addCylinderFrustumFaces(built, postX, postZ, 0.65f, 6.68f, 0.18f, 0.14f, MatGraniteLight, sides = 8)

    // Cantilever galvanized steel mast arm at 6.5m height extending over the M.O. Road traffic lanes (X = 16.2f .. 23.6f)
    addBoxFaces(built, 16.2f, 6.40f, postZ - 0.12f, postX, 6.60f, postZ + 0.12f, MatGraniteLight, MatPromenadeTile)

    // Two 3-Aspect 300mm Signal Head Backplates with Black Visors & 50mm Yellow Reflective Borders
    for (headX in floatArrayOf(17.6f, 20.4f)) {
        addBoxFaces(built, headX - 0.34f, 5.42f, postZ - 0.22f, headX + 0.34f, 6.72f, postZ - 0.10f, MatRoadMarkingYellow, MatRoadMarkingYellow)
        addBoxFaces(built, headX - 0.28f, 5.48f, postZ - 0.26f, headX + 0.28f, 6.66f, postZ - 0.14f, MatHazardBlack, MatHazardBlack)
    }
    // Pedestrian Signal Head Housing mounted on the vertical post (Y = 2.45m .. 3.35m)
    addBoxFaces(built, postX - 0.30f, 2.45f, postZ - 0.26f, postX + 0.30f, 3.35f, postZ - 0.16f, MatRoadMarkingYellow, MatHazardBlack)

    cachedManikandanalMastFaces = built
    return built
}

private fun collectManikandanalSignalAndStreetPropsFaces(
    camEye: Vec3,
    manikandanalSignal: TrafficSignalAspect,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - 20f
    val dz = camEye.z - (-38f)
    if (dx * dx + dz * dz > 180f * 180f) return

    outFaces.addAll(getOrBuildManikandanalMastFaces())

    val sigZ = -37.48f
    val isRed = manikandanalSignal == TrafficSignalAspect.Red
    val isAmber = manikandanalSignal == TrafficSignalAspect.Amber
    val isGreen = manikandanalSignal == TrafficSignalAspect.Green

    val redCol = if (isRed) Color(0xFFFF1E1E) else Color(0xFF450A0A)
    val amberCol = if (isAmber) Color(0xFFFACC15) else Color(0xFF422006)
    val greenCol = if (isGreen) Color(0xFF22C55E) else Color(0xFF052E16)

    for (headX in floatArrayOf(17.6f, 20.4f)) {
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 6.26f, sigZ), Vec3(headX + 0.16f, 6.26f, sigZ), Vec3(headX + 0.16f, 6.58f, sigZ), Vec3(headX - 0.16f, 6.58f, sigZ), redCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 5.90f, sigZ), Vec3(headX + 0.16f, 5.90f, sigZ), Vec3(headX + 0.16f, 6.22f, sigZ), Vec3(headX - 0.16f, 6.22f, sigZ), amberCol, biasDist = -0.35f))
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 5.54f, sigZ), Vec3(headX + 0.16f, 5.54f, sigZ), Vec3(headX + 0.16f, 5.86f, sigZ), Vec3(headX - 0.16f, 5.86f, sigZ), greenCol, biasDist = -0.35f))
    }

    val pedX = 23.6f
    val pedStandCol = if (!isRed) Color(0xFFFF1E1E) else Color(0xFF3F1111)
    val pedWalkCol = if (isRed) Color(0xFF22C55E) else Color(0xFF0A2E1A)
    outFaces.add(QuadFace3D(Vec3(pedX - 0.15f, 2.92f, sigZ), Vec3(pedX + 0.15f, 2.92f, sigZ), Vec3(pedX + 0.15f, 3.26f, sigZ), Vec3(pedX - 0.15f, 3.26f, sigZ), pedStandCol, biasDist = -0.35f))
    outFaces.add(QuadFace3D(Vec3(pedX - 0.15f, 2.52f, sigZ), Vec3(pedX + 0.15f, 2.52f, sigZ), Vec3(pedX + 0.15f, 2.86f, sigZ), Vec3(pedX - 0.15f, 2.86f, sigZ), pedWalkCol, biasDist = -0.35f))
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

        // Arched canopy roof over Guruvayur Stand (Zero dummy box buses, zero floating text)
        addArchedCanopyRoofFaces(built, -2774.0f, -2696.0f, 1283.0f, 1306.0f, 6.45f, 0.85f, MatTerracottaTile, MatGraniteDark, archAlongX = false)

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
        addArchedCanopyRoofFaces(built, -4486.0f, -4466.0f, 1052.0f, 1108.0f, 6.05f, 0.75f, MatTerracottaTile, MatGraniteDark, archAlongX = true)

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
        addArchedCanopyRoofFaces(built, -2206.0f, -2186.0f, -2442.0f, -2378.0f, 6.05f, 0.75f, MatTerracottaTile, MatGraniteDark, archAlongX = true)

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
    if (dx * dx + dz * dz > 210f * 210f) return

    var faces = cachedVadakkeStandFaces
    if (faces == null) {
        val built = ArrayList<QuadFace3D>(640)

        // South Perimeter Wall at far outer corners only (leaves 31.5m entrance X = -46.5..-15.0 100% open & obstacle-free)
        addBoxFaces(built, -62.0f, 0.06f, 200.4f, -46.5f, 1.35f, 201.1f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -15.0f, 0.06f, 200.4f, -10.0f, 1.35f, 201.1f, MatKeralaWhitewash, MatLateriteStone)

        // Sculpted Octagonal Gate Pillars at X = -46.2m and X = -15.3m (Zero floating text banners)
        for (px in floatArrayOf(-46.2f, -15.3f)) {
            addCylinderFrustumFaces(built, px, 200.6f, 0.06f, 0.85f, 0.46f, 0.38f, MatKeralaWhitewash, sides = 8)
            addCylinderFrustumFaces(built, px, 200.6f, 0.85f, 3.10f, 0.34f, 0.30f, MatTerracottaTile, sides = 8)
        }

        // Raised Passenger Boarding Platform with Cylindrical Terracotta Pillars, Arched Barrel-Vault Canopy & Glazed Concourse Hall
        addBoxFaces(built, -58.5f, 0.06f, 232.8f, -13.5f, 0.34f, 244.5f, MatPromenadeTile, MatRoadMarkingYellow, MatGraniteDark)
        var colX = -55.0f
        while (colX <= -16.0f) {
            for (colZ in floatArrayOf(216.0f, 225.0f, 233.5f)) {
                addCylinderFrustumFaces(built, colX, colZ, 0.34f, 0.90f, 0.32f, 0.25f, MatKeralaWhitewash, sides = 8)
                addCylinderFrustumFaces(built, colX, colZ, 0.90f, 5.55f, 0.22f, 0.20f, MatTerracottaTile, sides = 8)
            }
            colX += 7.8f
        }
        // Multi-Bay Arched Barrel-Vault Canopy Roof over Vadakke Stand Boarding Bays
        for (bay in 0 until 5) {
            val bx0 = -58.5f + bay * 9.0f
            val bx1 = bx0 + 9.0f
            addArchedCanopyRoofFaces(built, bx0, bx1, 214.0f, 235.5f, 5.55f, 0.82f, if (bay % 2 == 0) MatIrDarkBlue else MatTerracottaTile, MatGraniteDark, archAlongX = true)
        }
        // Multi-Bay Glazed Passenger Concourse Building with Pitched Kerala Terracotta Roof
        addBoxFaces(built, -57.5f, 0.34f, 235.2f, -14.5f, 4.20f, 244.5f, MatKeralaCreamWall, MatKeralaCreamWall)
        addBoxFaces(built, -57.0f, 4.20f, 235.5f, -15.0f, 6.85f, 244.2f, MatGraniteDark, MatArcadeGlass)
        addPitchedKeralaRoof(built, -58.5f, -13.5f, 234.5f, 245.2f, 6.80f, 9.20f, 3.2f, 3.8f, MatTerracottaTile, MatTeakWoodCarved)

        faces = built
        cachedVadakkeStandFaces = built
    }

    outFaces.addAll(faces)
}

// ============================================================================
// 7. ROADSIDE ORGANIC KERALA TREES (ZERO DUMMY BOX HOUSES)
//    Strictly placed outside roadHalfW + 12.2m and clear of all junctions & signboards
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
    if (bx in -82f..62f && bz in -235f..-30f) return false
    if (hypot(bx - 3.1f, bz - 9.5f) < 88f) return false
    if (bx in -74f..2f && bz in 190f..258f) return false
    if (bx in -2792f..-2560f && bz in 1220f..1342f) return false
    if (bx in -4760f..-4446f && bz in 950f..1210f) return false
    if (bx in -2480f..-2166f && bz in -2520f..-2300f) return false
    if (bx in 945f..1055f && bz in -1870f..-1745f) return false

    if (!isPointClearOfAllRoads(bx, bz, roads, extraMarginM = 9.5f)) return false

    for (s in signPlacements) {
        if (hypot(bx - s.midX, bz - s.midZ) < 15.0f) return false
    }
    return true
}

private fun buildRoadsideSceneryCache(
    roads: List<ThrissurOsmRoad>,
    signPlacements: List<CachedSignRenderItem>
): List<CachedSceneryCluster> {
    val clusters = ArrayList<CachedSceneryCluster>(140)
    for (road in roads) {
        if (road.id == "ROAD_SWARAJ_ROUND" || road.id == "ROAD_SAKTHAN_STAND_ENTRY") continue
        val frames = road.frames
        if (frames.size < 10) continue

        val roadHalfW = road.widthM * 0.5f
        var idx = 6
        while (idx < frames.size - 6) {
            val f = frames[idx]
            for (side in intArrayOf(-1, 1)) {
                val latOffset = (roadHalfW + 13.0f) * side
                val bx = f.pos.x + f.right.x * latOffset
                val bz = f.pos.z + f.right.z * latOffset

                if (isClearForRoadsideScenery(bx, bz, roads, signPlacements)) {
                    val itemFaces = ArrayList<QuadFace3D>(96)
                    val isTallBanyan = ((idx + if (side > 0) 1 else 0) % 2 == 0)
                    addRealisticMaidanTree3D(
                        faces = itemFaces,
                        tx = bx,
                        tz = bz,
                        baseY = 0.06f,
                        crownRadius = if (isTallBanyan) 4.2f else 3.4f,
                        treeHeight = if (isTallBanyan) 9.8f else 8.2f
                    )
                    clusters.add(CachedSceneryCluster(bx, bz, itemFaces))
                }
            }
            idx += 16
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
// 8. ALL-APPROACH MAP-MATCHED KERALA PWD & NHAI GREEN DIRECTION BOARDS
//    Every single board matches the exact connected roads in thrissur_osm_roads.json,
//    and covers BOTH travel directions (travelDir = +1 and travelDir = -1) across all junctions!
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
    val rightDest: KeralaSignDestination,
    val travelDir: Int = 1 // +1 = faces start->end traffic; -1 = faces end->start traffic
)

private val ThrissurAuthenticSignSpecs = listOf(
    // ========================================================================
    // JUNCTION A: M.O. ROAD / OLLUR LINK / SAKTHAN ENTRY (All 3 Approaches!)
    // ========================================================================
    // A1. Northbound on M.O. Road leaving Sakthan Stand approaching Ollur Link (Z = -114.5m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.28f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.4"),
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "LEFT", "0.2"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "RIGHT", "1.2"),
        travelDir = 1
    ),
    // A2. Southbound on M.O. Road from Swaraj Round approaching Puthanpalli Link (Z = -86.0m) & Sakthan Entry (Z = -98.0m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.72f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.3"),
        leftDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "LEFT", "0.5"),
        rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.2"),
        travelDir = -1
    ),
    // A3. Southbound on M.O. Road approaching Ollur Link (Z = -114.5m) & Sakthan Stand
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.45f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.2"),
        leftDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "LEFT", "1.2"),
        rightDest = KeralaSignDestination("ശക്തൻ എൻട്രി", "Sakthan Entry", "RIGHT", "0.1"),
        travelDir = -1
    ),
    // A4. Westbound on Ollur Road approaching M.O. Road T-Junction (Z = -114.5m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_OLLUR",
        frameFraction = 0.045f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "RIGHT", "0.4"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.2"),
        rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "UP", "0.3"),
        travelDir = -1
    ),

    // ========================================================================
    // JUNCTION B: M.O. ROAD / HIGH ROAD (PUTHANPALLI LINK) (All Approaches!)
    // ========================================================================
    // B1. Northbound on M.O. Road approaching High Road / Puthanpalli Link (Z = -86.0m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.56f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.3"),
        leftDest = KeralaSignDestination("ശക്തൻ എൻട്രി", "Sakthan Entry", "LEFT", "0.1"),
        rightDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "RIGHT", "0.5"),
        travelDir = 1
    ),
    // B2. Westbound on Puthanpalli Link approaching M.O. Road T-Junction (Z = -86.0m)
    KeralaRoadSignSpec(
        roadId = "ROAD_PUTHANPALLI_LINK",
        frameFraction = 0.12f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "RIGHT", "0.3"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.3"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "LEFT", "1.4"),
        travelDir = -1
    ),

    // ========================================================================
    // JUNCTION C: SWARAJ ROUND — ALL 4 RADIAL APPROACHES & ALL 4 RING EXITS!
    // ========================================================================
    // C1. South Approach (M.O. Road Northbound into Swaraj Round South / Manikandanal)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.82f,
        side = -1,
        style = KeralaSignStyle.ROUNDABOUT_DIAGRAM,
        headerTitle = "സ്വരാജ് റൗണ്ട് • SWARAJ ROUND",
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "1.3"),
        leftDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "LEFT", "1.0"),
        rightDest = KeralaSignDestination("മണ്ണുത്തി", "Mannuthy", "RIGHT", "7.0"),
        travelDir = 1
    ),
    // C2. West Approach (M.G. Road Eastbound from West Fort into Swaraj Round West)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_WEST_FORT",
        frameFraction = 0.14f,
        side = 1,
        style = KeralaSignStyle.ROUNDABOUT_DIAGRAM,
        headerTitle = "സ്വരാജ് റൗണ്ട് • SWARAJ ROUND",
        straightDest = KeralaSignDestination("മണ്ണുത്തി", "Mannuthy", "UP", "7.0"),
        leftDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "LEFT", "1.3"),
        rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "RIGHT", "0.5"),
        travelDir = -1
    ),
    // C3. North Approach (Shoranur Road Southbound from Vadakke Stand into Swaraj Round North)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_VADAKKE",
        frameFraction = 0.14f,
        side = 1,
        style = KeralaSignStyle.ROUNDABOUT_DIAGRAM,
        headerTitle = "സ്വരാജ് റൗണ്ട് • SWARAJ ROUND",
        straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.5"),
        leftDest = KeralaSignDestination("മണ്ണുത്തി", "Mannuthy", "LEFT", "7.0"),
        rightDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "RIGHT", "1.0"),
        travelDir = -1
    ),
    // C4. East Approach (East Fort / Palace Road Westbound into Swaraj Round East)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_MANNUTHY",
        frameFraction = 0.025f,
        side = 1,
        style = KeralaSignStyle.ROUNDABOUT_DIAGRAM,
        headerTitle = "സ്വരാജ് റൗണ്ട് • SWARAJ ROUND",
        straightDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "UP", "1.0"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.5"),
        rightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "RIGHT", "1.3"),
        travelDir = -1
    ),
    // C5. Swaraj Round Clockwise Ring -> Before West Exit (M.G. Road / West Fort / Guruvayur)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.115f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "1.3"),
        leftDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "LEFT", "1.0"),
        rightDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "LEFT", "16"),
        travelDir = 1
    ),
    // C6. Swaraj Round Clockwise Ring -> Before North Exit (Shoranur Road / Vadakke Stand)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.390f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഈസ്റ്റ് ഫോർട്ട്", "East Fort", "UP", "0.8"),
        leftDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "LEFT", "1.3"),
        rightDest = KeralaSignDestination("മണ്ണുത്തി", "Mannuthy", "UP", "7.0"),
        travelDir = 1
    ),
    // C7. Swaraj Round Clockwise Ring -> Before East Exit (East Fort / Mannuthy / Peechi Dam)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.675f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.5"),
        leftDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "LEFT", "7.0"),
        rightDest = KeralaSignDestination("പീച്ചി ഡാം", "Peechi Dam", "LEFT", "24"),
        travelDir = 1
    ),
    // C8. Swaraj Round Clockwise Ring -> Before South Exit (M.O. Road / Sakthan Stand / Ollur)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.845f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "UP", "1.0"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.4"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "LEFT", "1.5"),
        travelDir = 1
    ),

    // ========================================================================
    // JUNCTION D: VADAKKE STAND (SHORANUR ROAD) & WEST FORT (M.G. ROAD)
    // ========================================================================
    // D1. Northbound on Shoranur Road approaching Vadakke Bus Stand
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_VADAKKE",
        frameFraction = 0.78f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "0.2"),
        leftDest = KeralaSignDestination("സ്റ്റാൻഡ് എൻട്രി", "Stand Entry", "LEFT", "0.1"),
        rightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "RIGHT", "1.1"),
        travelDir = 1
    ),
    // D2. Westbound on M.G. Road approaching West Fort / SH 75 Continuation
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_WEST_FORT",
        frameFraction = 0.84f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("കാഞ്ഞാണി SH 75", "Kanjani", "UP", "11.4"),
        leftDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "UP", "16.1"),
        rightDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "Chavakkad Beach", "UP", "16.5"),
        travelDir = 1
    ),
    // D3. Eastbound on SH 75 approaching West Fort & M.G. Road
    KeralaRoadSignSpec(
        roadId = "ROAD_WEST_FORT_TO_KANJANI",
        frameFraction = 0.035f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "1.0"),
        leftDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "LEFT", "2.2"),
        rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "RIGHT", "1.4"),
        travelDir = -1
    ),

    // ========================================================================
    // JUNCTION E: EAST FORT 3-WAY JUNCTION (All 3 Approaches!)
    // ========================================================================
    // E1. Eastbound from Swaraj Round on Mannuthy Road approaching East Fort / Puthanpalli Link (frac 0.081)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_MANNUTHY",
        frameFraction = 0.055f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "UP", "6.4"),
        leftDest = KeralaSignDestination("പീച്ചി ഡാം", "Peechi Dam", "UP", "23"),
        rightDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "RIGHT", "1.2"),
        travelDir = 1
    ),
    // E2. Westbound from Mannuthy approaching East Fort / Puthanpalli Link (frac 0.081)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_MANNUTHY",
        frameFraction = 0.115f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.6"),
        leftDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "LEFT", "1.2"),
        rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "1.7"),
        travelDir = -1
    ),
    // E3. Eastbound on Puthanpalli Link approaching East Fort T-Junction (frac 1.000)
    KeralaRoadSignSpec(
        roadId = "ROAD_PUTHANPALLI_LINK",
        frameFraction = 0.82f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഈസ്റ്റ് ഫോർട്ട്", "East Fort", "UP", "0.2"),
        leftDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "LEFT", "0.6"),
        rightDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "RIGHT", "6.4"),
        travelDir = 1
    ),

    // ========================================================================
    // JUNCTION F: MANNUTHY 3-WAY INTERCHANGE (All 3 Approaches!)
    // ========================================================================
    // F1. Eastbound from Thrissur approaching Mannuthy NH 544 / Peechi Dam Junction
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_MANNUTHY",
        frameFraction = 0.945f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "MANNUTHY INTERCHANGE • NH 544 / SH 51",
        straightDest = KeralaSignDestination("പീച്ചി ഡാം", "PEECHI DAM", "UP", "17 KM"),
        leftDest = KeralaSignDestination("ഒല്ലൂർ NH 544", "OLLUR", "RIGHT", "8.8 KM"),
        rightDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "TOLL PLAZA", "RIGHT", "16.6 KM"),
        travelDir = 1
    ),
    // F2. Westbound from Peechi Dam approaching Mannuthy Interchange
    KeralaRoadSignSpec(
        roadId = "ROAD_MANNUTHY_TO_PEECHI_DAM",
        frameFraction = 0.035f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("തൃശ്ശൂർ നഗരം", "Thrissur City", "UP", "7.0"),
        leftDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "Paliyekkara Toll", "LEFT", "16.6"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ NH 544", "Ollur", "LEFT", "8.8"),
        travelDir = -1
    ),
    // F3. Northbound on NH 544 from Ollur/Paliyekkara approaching Mannuthy Interchange
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.035f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("തൃശ്ശൂർ നഗരം", "Thrissur City", "LEFT", "7.0"),
        leftDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "LEFT", "7.0"),
        rightDest = KeralaSignDestination("പീച്ചി ഡാം", "Peechi Dam", "RIGHT", "17.0"),
        travelDir = -1
    ),

    // ========================================================================
    // JUNCTION G: OLLUR NH 544 JUNCTION & PALIYEKKARA TOLL (All Approaches!)
    // ========================================================================
    // G1. Southbound on NH 544 approaching Ollur Link (frac 0.531)
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.500f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "Paliyekkara Toll", "UP", "7.8"),
        leftDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "RIGHT", "1.2"),
        rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "RIGHT", "2.5"),
        travelDir = 1
    ),
    // G2. Northbound on NH 544 from Paliyekkara approaching Ollur Link (frac 0.531)
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.565f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "UP", "8.8"),
        leftDest = KeralaSignDestination("ഒല്ലൂർ / തൃശ്ശൂർ", "Ollur / Thrissur", "LEFT", "1.2"),
        rightDest = KeralaSignDestination("പീച്ചി ഡാം", "Peechi Dam", "UP", "25"),
        travelDir = -1
    ),
    // G3. Eastbound on Ollur Road approaching NH 544 T-Junction
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_OLLUR",
        frameFraction = 0.955f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "LEFT", "8.8"),
        leftDest = KeralaSignDestination("പീച്ചി ഡാം", "Peechi Dam", "LEFT", "25"),
        rightDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "Paliyekkara Toll", "RIGHT", "7.8"),
        travelDir = 1
    ),
    // G4. Southbound on NH 544 approaching Paliyekkara Toll Plaza
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.905f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "NATIONAL HIGHWAY • NH 544",
        straightDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "PALIYEKKARA TOLL", "UP", "500 M"),
        leftDest = KeralaSignDestination("ഫാസ്ടാഗ് ലെയിൻ", "FASTAG LANES", "UP", "500 M"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ / തൃശ്ശൂർ", "THRISSUR VIA NH", "UP", "16 KM"),
        travelDir = 1
    ),

    // ========================================================================
    // JUNCTION H: KANJANI, GURUVAYUR, CHAVAKKAD & TRIPRAYAR (All Approaches!)
    // ========================================================================
    // H1. Westbound on SH 75 approaching Kanjani Junction
    KeralaRoadSignSpec(
        roadId = "ROAD_WEST_FORT_TO_KANJANI",
        frameFraction = 0.965f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "KANJANI JUNCTION • SH 75 / SH 61",
        straightDest = KeralaSignDestination("ഗുരുവായൂർ", "GURUVAYUR", "UP", "2.0 KM"),
        leftDest = KeralaSignDestination("തൃപ്രയാർ SH 61", "TRIPRAYAR", "LEFT", "1.6 KM"),
        rightDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "CHAVAKKAD BEACH", "UP", "4.7 KM"),
        travelDir = 1
    ),
    // H2. Southbound from Guruvayur approaching SH 61 Triprayar / Kanjani Junction
    KeralaRoadSignSpec(
        roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
        frameFraction = 0.060f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("തൃശ്ശൂർ SH 75", "Thrissur", "UP", "11.4"),
        leftDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "12.4"),
        rightDest = KeralaSignDestination("തൃപ്രയാർ SH 61", "Triprayar", "RIGHT", "1.6"),
        travelDir = -1
    ),
    // H3. Northbound on SH 61 from Triprayar approaching Kanjani Junction
    KeralaRoadSignSpec(
        roadId = "ROAD_SH61_TRIPRAYAR_TO_KANJANI",
        frameFraction = 0.960f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "LEFT", "2.0"),
        leftDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "Chavakkad Beach", "LEFT", "4.7"),
        rightDest = KeralaSignDestination("തൃശ്ശൂർ SH 75", "Thrissur", "RIGHT", "11.4"),
        travelDir = 1
    ),
    // H4. Northbound approaching Guruvayur Temple & Stand
    KeralaRoadSignSpec(
        roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
        frameFraction = 0.390f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "Chavakkad Beach", "UP", "2.8"),
        leftDest = KeralaSignDestination("ഗുരുവായൂർ ക്ഷേത്രം", "Guruvayur Temple", "LEFT", "0.2"),
        rightDest = KeralaSignDestination("ഗുരുവായൂർ സ്റ്റാൻഡ്", "Guruvayur Stand", "RIGHT", "0.3"),
        travelDir = 1
    ),
    // H5. Southbound on NH 66 approaching SH 61 Triprayar / Kanjani Junction
    KeralaRoadSignSpec(
        roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
        frameFraction = 0.670f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "NATIONAL HIGHWAY • NH 66 / SH 61",
        straightDest = KeralaSignDestination("തൃപ്രയാർ ബീച്ച്", "TRIPRAYAR BEACH", "UP", "1.5 KM"),
        leftDest = KeralaSignDestination("കാഞ്ഞാണി SH 61", "KANJANI", "LEFT", "1.6 KM"),
        rightDest = KeralaSignDestination("തൃശ്ശൂർ", "THRISSUR", "LEFT", "13 KM"),
        travelDir = 1
    )
)

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
    if (px in 64.0f..78.5f && pz in 8.0f..32.5f) return false
    if (px in 43.0f..59.5f && pz in -51.5f..-34.5f) return false
    if (px in -29.0f..-3.0f && pz in -69.0f..-54.0f) return false
    if (px in -73.0f..-57.0f && pz in 12.5f..41.0f) return false
    if (px in 15.5f..44.5f && pz in 64.5f..79.5f) return false
    if (hypot(px - 3.1f, pz - 9.5f) < 42.0f) return false
    if (px in -25.0f..50.0f && pz in -220.0f..-141.0f) return false
    if (px in -70.0f..-26.0f && pz in -225.0f..20.0f) return false
    return true
}

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
        for (delta in intArrayOf(0, -1, -2, -3, -4, 1, 2)) {
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
        for (delta in intArrayOf(0, -1, 1, -2, 2, -3, 3, -5, 5, -7)) {
            val idx = (baseIdx + delta).coerceIn(2, frames.size - 3)
            val f = frames[idx]
            val innerDist = f.halfWidthM + 2.55f + extraShift
            val outerDist = innerDist + boardW
            // When travelDir == +1 (start->end), left driving verge is -f.right (v0=outer, v1=inner).
            // When travelDir == -1 (end->start), left driving verge for incoming traffic is +f.right,
            // and v0 (screen-left) is +outerDist * f.right while v1 (screen-right) is +innerDist * f.right!
            val dLeft: Float
            val dRight: Float
            if (spec.travelDir >= 0) {
                dLeft = if (spec.side < 0) -outerDist else innerDist
                dRight = if (spec.side < 0) -innerDist else outerDist
            } else {
                dLeft = if (spec.side > 0) outerDist else -innerDist
                dRight = if (spec.side > 0) innerDist else -outerDist
            }
            val leftVec = Vec3(f.pos.x + f.right.x * dLeft, 0f, f.pos.z + f.right.z * dLeft)
            val rightVec = Vec3(f.pos.x + f.right.x * dRight, 0f, f.pos.z + f.right.z * dRight)
            val midVec = Vec3((leftVec.x + rightVec.x) * 0.5f, 0f, (leftVec.z + rightVec.z) * 0.5f)
            if (isPointClearOfAllRoads(leftVec.x, leftVec.z, roads, 2.25f) &&
                isPointClearOfAllRoads(rightVec.x, rightVec.z, roads, 2.25f) &&
                isPointClearOfAllRoads(midVec.x, midVec.z, roads, 2.25f) &&
                isPointClearOfLandmarkBuildings(leftVec.x, leftVec.z) &&
                isPointClearOfLandmarkBuildings(rightVec.x, rightVec.z) &&
                existing.none { hypot(midVec.x - it.midX, midVec.z - it.midZ) < 10.5f }
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
        val itemFaces = ArrayList<QuadFace3D>(48)

        if (spec.style == KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY) {
            for (colPos in arrayOf(leftPos, rightPos)) {
                addCylinderFrustumFaces(itemFaces, colPos.x, colPos.z, 0.05f, 8.25f, 0.24f, 0.22f, MatGraniteLight, sides = 8)
            }
            val minX = minOf(leftPos.x, rightPos.x) - 0.2f
            val maxX = maxOf(leftPos.x, rightPos.x) + 0.2f
            val minZ = minOf(leftPos.z, rightPos.z) - 0.2f
            val maxZ = maxOf(leftPos.z, rightPos.z) + 0.2f
            addBoxFaces(itemFaces, minX, 6.95f, minZ, maxX, 7.30f, maxZ, MatGraniteDark, MatGranitePaving, MatGraniteDark, stroke = null)

            val gLeft = Vec3(leftPos.x + (rightPos.x - leftPos.x) * 0.10f, 0f, leftPos.z + (rightPos.z - leftPos.z) * 0.10f)
            val gRight = Vec3(leftPos.x + (rightPos.x - leftPos.x) * 0.90f, 0f, leftPos.z + (rightPos.z - leftPos.z) * 0.90f)
            itemFaces.add(
                QuadFace3D(
                    v0 = Vec3(gLeft.x, 6.15f, gLeft.z),
                    v1 = Vec3(gRight.x, 6.15f, gRight.z),
                    v2 = Vec3(gRight.x, 8.25f, gRight.z),
                    v3 = Vec3(gLeft.x, 8.25f, gLeft.z),
                    baseColor = Color(0xFF057A55),
                    biasDist = -0.35f,
                    signboard = createKeralaRoadSignSpec(spec, specIdx)
                )
            )
        } else {
            // Twin Cylindrical Galvanized Steel Signposts with Black/White Reflective Base Stripes
            for (t in floatArrayOf(0.18f, 0.82f)) {
                val px = leftPos.x + (rightPos.x - leftPos.x) * t
                val pz = leftPos.z + (rightPos.z - leftPos.z) * t
                addCylinderFrustumFaces(itemFaces, px, pz, 0.05f, 1.35f, 0.12f, 0.12f, MatHazardBlack, sides = 6)
                addCylinderFrustumFaces(itemFaces, px, pz, 1.35f, 5.45f, 0.11f, 0.10f, MatRoadMarkingWhite, sides = 6)
            }
            val topY = 5.65f
            val bgColor = if (spec.style == KeralaSignStyle.ROUNDABOUT_DIAGRAM) Color(0xFF006D5B) else Color(0xFF046A4E)
            itemFaces.add(
                QuadFace3D(
                    v0 = Vec3(leftPos.x, 2.65f, leftPos.z),
                    v1 = Vec3(rightPos.x, 2.65f, rightPos.z),
                    v2 = Vec3(rightPos.x, topY, rightPos.z),
                    v3 = Vec3(leftPos.x, topY, leftPos.z),
                    baseColor = bgColor,
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


