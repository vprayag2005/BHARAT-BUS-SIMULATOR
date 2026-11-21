package com.bharatbussim.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
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
private val MatMaidanGrass = Color(0xFF28722B)
private val MatMaidanGrassUpper = Color(0xFF338A35)
private val MatMaidanGrassCrown = Color(0xFF3F9E3C)
private val MatMaidanGrassDeep = Color(0xFF1F5D23)
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
    ShoranurRailwaySystem.run { drawRailwayCorridorBallastGround(rig, path, camEye) }
    drawThrissurRailwayStationGround(rig, path, camEye)
    drawVadakkeBusStandGround(rig, path, camEye)
    drawWadakkancheryRobAndTownGround(rig, path, camEye)
    drawShoranurTerminalYard(rig, path, camEye)
    drawShoranurStationYard(rig, path, camEye)
    Zone2Nh544KuthiranRenderer.run { drawZone2GroundPlazas(rig, path, camEye) }
    Zone3Nh544SouthRenderer.run { drawZone3GroundPlazas(rig, path, camEye, roads) }
    Zone4KoleRailHeritageRenderer.run { drawZone4GroundPlazas(rig, path, camEye, roads) }
    Zone5Nh66CoastalRenderer.run { drawZone5GroundPlazas(rig, path, camEye, roads) }
    PuthanpallyBasilicaRenderer.run { drawPuthanpallyGroundPlaza(rig, path, camEye) }
    drawChavakkadBeachGround(rig, path, camEye)
    drawTriprayarBeachGround(rig, path, camEye)
}

internal fun DrawScope.drawAllLandmarkGroundPlazasUnconditional(
    rig: CameraRig,
    path: Path,
    roads: List<ThrissurOsmRoad>
) {
    drawSwarajRoundGroundPlaza(rig, path, Vec3(3.1f, 2f, 18.0f), roads)
    ShoranurRailwaySystem.run { drawRailwayCorridorBallastGround(rig, path, Vec3(0f, 2f, 1500f)) }
    drawThrissurRailwayStationGround(rig, path, Vec3(-92f, 2f, -138f))
    drawVadakkeBusStandGround(rig, path, Vec3(-42f, 2f, 185f))
    drawWadakkancheryRobAndTownGround(rig, path, Vec3(92f, 8f, 1680f))
    drawShoranurTerminalYard(rig, path, Vec3(160f, 2f, 3075f))
    drawShoranurStationYard(rig, path, Vec3(45f, 2f, 3238f))
    Zone2Nh544KuthiranRenderer.run { drawZone2GroundPlazas(rig, path, Vec3(2600f, 2f, 1140f)) }
    Zone3Nh544SouthRenderer.run { drawZone3GroundPlazas(rig, path, Vec3(1400f, 2f, -3200f), roads) }
    Zone4KoleRailHeritageRenderer.run { drawZone4GroundPlazas(rig, path, Vec3(-1645f, 2f, 610f), roads) }
    Zone5Nh66CoastalRenderer.run { drawAllZone5GroundPlazasUnconditional(rig, path, roads) }
    PuthanpallyBasilicaRenderer.run { drawPuthanpallyGroundPlaza(rig, path, Vec3(PuthanpallyBasilicaRenderer.CENTER_X, 2f, -126f)) }
    drawChavakkadBeachGround(rig, path, Vec3(-4600f, 2f, 1080f))
    drawTriprayarBeachGround(rig, path, Vec3(-2320f, 2f, -2410f))
}

internal fun collectThrissurLandmarkSceneFaces(
    camEye: Vec3,
    camFwd: Vec3,
    context: android.content.Context?,
    roads: List<ThrissurOsmRoad>,
    manikandanalSignal: TrafficSignalAspect,
    outFaces: MutableList<QuadFace3D>
) {
    collectSwarajRoundFaces(camEye, roads, outFaces)
    collectThrissurRailwayStationFaces(camEye, outFaces)
    collectManikandanalSignalAndStreetPropsFaces(camEye, manikandanalSignal, outFaces)
    collectVadakkeBusStandFaces(camEye, outFaces)
    collectWadakkancheryRobAndTownFaces(camEye, outFaces)
    collectRailwayViaductFaces(camEye, outFaces)
    if (hypot(160f - camEye.x, 3075f - camEye.z) < 650f) {
        collectAllShoranurTerminalStaticFaces(outFaces)
    }
    collectShoranurStationSceneFaces(camEye, outFaces)
    Zone2Nh544KuthiranRenderer.collectZone2FacesNearCamera(camEye, roads, outFaces)
    Zone3Nh544SouthRenderer.collectZone3FacesNearCamera(camEye, roads, outFaces)
    Zone4KoleRailHeritageRenderer.collectZone4FacesNearCamera(camEye, roads, outFaces)
    Zone5Nh66CoastalRenderer.collectZone5FacesNearCamera(camEye, roads, outFaces)
    PuthanpallyBasilicaRenderer.collectPuthanpallyFacesNearCamera(camEye, outFaces)
    HighwayWBeamGuardrailRenderer.collectGuardrailFacesNearCamera(camEye, roads, outFaces)
    collectChavakkadFaces(camEye, outFaces)
    collectTriprayarFaces(camEye, outFaces)
    collectRoadsideSceneryFaces(camEye, roads, outFaces)
    collectNhaiSignboardFaces(camEye, roads, outFaces)
}

internal fun collectAllStaticLandmarkAndSignFaces(
    roads: List<ThrissurOsmRoad>,
    outFaces: MutableList<QuadFace3D>
) {
    collectSwarajRoundFaces(Vec3(3.1f, 2f, 18.0f), roads, outFaces)
    collectThrissurRailwayStationFaces(Vec3(-92f, 2f, -138f), outFaces)
    outFaces.addAll(getOrBuildManikandanalMastFaces())
    collectVadakkeBusStandFaces(Vec3(-42f, 2f, 185f), outFaces)
    collectAllWadakkancheryRobStaticFaces(outFaces)
    collectAllRailwayViaductStaticFaces(outFaces)
    collectAllShoranurTerminalStaticFaces(outFaces)
    collectAllShoranurStationStaticFaces(outFaces)
    Zone2Nh544KuthiranRenderer.collectAllZone2StaticFaces(roads, outFaces)
    Zone3Nh544SouthRenderer.collectAllZone3StaticFaces(roads, outFaces)
    Zone4KoleRailHeritageRenderer.collectAllZone4StaticFaces(roads, outFaces)
    Zone5Nh66CoastalRenderer.collectAllZone5StaticFaces(roads, outFaces)
    PuthanpallyBasilicaRenderer.collectAllPuthanpallyStaticFaces(outFaces)
    HighwayWBeamGuardrailRenderer.collectAllStaticGuardrailFaces(roads, outFaces)
    collectChavakkadFaces(Vec3(-4600f, 2f, 1080f), outFaces)
    collectTriprayarFaces(Vec3(-2320f, 2f, -2410f), outFaces)
    val signs = getOrBuildSignRenderCache(roads)
    val clusters = cachedRoadsideClusters ?: buildRoadsideSceneryCache(roads, signs).also { cachedRoadsideClusters = it }
    for (c in clusters) {
        outFaces.addAll(c.faces)
    }
    for (s in signs) {
        outFaces.addAll(s.faces)
    }
}

internal fun collectManikandanalDynamicSignalFaces(
    manikandanalSignal: TrafficSignalAspect,
    outFaces: MutableList<QuadFace3D>
) {
    val sigSouthZ = -63.20f
    val sigNorthZ = -62.80f
    val isRed = manikandanalSignal == TrafficSignalAspect.Red
    val isAmber = manikandanalSignal == TrafficSignalAspect.Amber
    val isGreen = manikandanalSignal == TrafficSignalAspect.Green

    val redCol = if (isRed) Color(0xFFFF1E1E) else Color(0xFF450A0A)
    val amberCol = if (isAmber) Color(0xFFFACC15) else Color(0xFF422006)
    val greenCol = if (isGreen) Color(0xFF22C55E) else Color(0xFF052E16)

    for (headX in floatArrayOf(19.9f, 23.4f)) {
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 6.26f, sigSouthZ), Vec3(headX + 0.16f, 6.26f, sigSouthZ), Vec3(headX + 0.16f, 6.58f, sigSouthZ), Vec3(headX - 0.16f, 6.58f, sigSouthZ), redCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 5.90f, sigSouthZ), Vec3(headX + 0.16f, 5.90f, sigSouthZ), Vec3(headX + 0.16f, 6.22f, sigSouthZ), Vec3(headX - 0.16f, 6.22f, sigSouthZ), amberCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 5.54f, sigSouthZ), Vec3(headX + 0.16f, 5.54f, sigSouthZ), Vec3(headX + 0.16f, 5.86f, sigSouthZ), Vec3(headX - 0.16f, 5.86f, sigSouthZ), greenCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX + 0.16f, 6.26f, sigNorthZ), Vec3(headX - 0.16f, 6.26f, sigNorthZ), Vec3(headX - 0.16f, 6.58f, sigNorthZ), Vec3(headX + 0.16f, 6.58f, sigNorthZ), redCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX + 0.16f, 5.90f, sigNorthZ), Vec3(headX - 0.16f, 5.90f, sigNorthZ), Vec3(headX - 0.16f, 6.22f, sigNorthZ), Vec3(headX + 0.16f, 6.22f, sigNorthZ), amberCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX + 0.16f, 5.54f, sigNorthZ), Vec3(headX - 0.16f, 5.54f, sigNorthZ), Vec3(headX - 0.16f, 5.86f, sigNorthZ), Vec3(headX + 0.16f, 5.86f, sigNorthZ), greenCol, biasDist = -1.50f))
    }

    val pedX = 9.6f
    val pedStandCol = if (!isRed) Color(0xFFFF1E1E) else Color(0xFF3F1111)
    val pedWalkCol = if (isRed) Color(0xFF22C55E) else Color(0xFF0A2E1A)
    outFaces.add(QuadFace3D(Vec3(pedX - 0.15f, 2.92f, sigSouthZ), Vec3(pedX + 0.15f, 2.92f, sigSouthZ), Vec3(pedX + 0.15f, 3.26f, sigSouthZ), Vec3(pedX - 0.15f, 3.26f, sigSouthZ), pedStandCol, biasDist = -1.50f))
    outFaces.add(QuadFace3D(Vec3(pedX - 0.15f, 2.52f, sigSouthZ), Vec3(pedX + 0.15f, 2.52f, sigSouthZ), Vec3(pedX + 0.15f, 2.86f, sigSouthZ), Vec3(pedX - 0.15f, 2.86f, sigSouthZ), pedWalkCol, biasDist = -1.50f))
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
private val MatTreeCanopyShadow = Color(0xFF1A4F1E)
private val MatTreeCanopyMid = Color(0xFF28732C)
private val MatTreeCanopyCrown = Color(0xFF39923A)
private val MatCastIronBollard = Color(0xFF1E293B)
private val MatVilakkumadamLampGlow = Color(0xFFF59E0B)

private fun maidanHillY(wx: Float, wz: Float): Float {
    val dist = hypot(wx - 3.1f, wz - 18.0f)
    if (dist >= 57.5f) return 0.36f
    val t = ((57.5f - dist) / (57.5f - 21.0f)).coerceIn(0f, 1f)
    return 0.38f + t * (1.15f - 0.38f)
}

private fun isInsideMaidanNadaOpening(pt: Vec3, tcx: Float, tcz: Float): Boolean {
    val dx = pt.x - tcx
    val dz = pt.z - tcz
    if (dz < -18.0f && abs(dx) < 4.5f) return true
    if (dz > 18.0f && abs(dx) < 4.5f) return true
    if (dx < -18.0f && abs(dz) < 4.5f) return true
    if (dx > 18.0f && abs(dz) < 4.5f) return true
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
    val cz = 18.0f
    if (hypot(camEye.x - cx, camEye.z - cz) > 420f) return

    val tcx = 3.1f
    val tcz = 18.0f

    val swarajRoad = roads.firstOrNull { it.id == "ROAD_SWARAJ_ROUND" }
    val frames = swarajRoad?.frames

    // 1. Thekkinkadu Maidan: Multi-tiered Lush Emerald Grassy Hillock (R = 57.5m -> 38.0m -> 21.0m)
    if (frames != null && frames.size >= 4) {
        val rMid = 38.0f
        val rPlateau = 21.0f
        val centerPeak = Vec3(cx, 1.15f, cz)

        for (i in 0 until frames.size - 1) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            val inOff0 = f0.halfWidthM + 1.05f
            val inOff1 = f1.halfWidthM + 1.05f
            val outer0 = Vec3(f0.pos.x + f0.right.x * inOff0, 0.38f, f0.pos.z + f0.right.z * inOff0)
            val outer1 = Vec3(f1.pos.x + f1.right.x * inOff1, 0.38f, f1.pos.z + f1.right.z * inOff1)

            val d0x = outer0.x - cx; val d0z = outer0.z - cz
            val len0 = hypot(d0x, d0z).coerceAtLeast(1f)
            val ux0 = d0x / len0; val uz0 = d0z / len0

            val d1x = outer1.x - cx; val d1z = outer1.z - cz
            val len1 = hypot(d1x, d1z).coerceAtLeast(1f)
            val ux1 = d1x / len1; val uz1 = d1z / len1

            val mid0 = Vec3(cx + ux0 * rMid, 0.80f, cz + uz0 * rMid)
            val mid1 = Vec3(cx + ux1 * rMid, 0.80f, cz + uz1 * rMid)
            val top0 = Vec3(cx + ux0 * rPlateau, 1.15f, cz + uz0 * rPlateau)
            val top1 = Vec3(cx + ux1 * rPlateau, 1.15f, cz + uz1 * rPlateau)

            val outerCol = when (i % 3) {
                0 -> MatMaidanGrassDeep
                1 -> MatMaidanGrass
                else -> MatMaidanGrassUpper
            }
            val midCol = if ((i and 1) == 0) MatMaidanGrass else MatMaidanGrassUpper
            val crownCol = if ((i and 1) == 0) MatMaidanGrassUpper else MatMaidanGrassCrown

            if (rig.buildClippedQuadPath(path, outer0, outer1, mid1, mid0)) {
                drawPath(path, outerCol)
            }
            if (rig.buildClippedQuadPath(path, mid0, mid1, top1, top0)) {
                drawPath(path, midCol)
            }
            if (rig.buildClippedQuadPath(path, top0, top1, centerPeak, centerPeak)) {
                drawPath(path, crownCol)
            }
        }

        // Outer Border: 2.0m wide paved pedestrian sidewalk tiled in alternating terracotta and stone pavers
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

    // 2. Curved Pedestrian Park Promenade Ring inside Thekkinkadu Maidan (laterite stone jogging track)
    drawMaidanArcWalkway(rig, path, cx, cz, 45.2f, 47.2f, 8f, 82f, 14, 0.65f, MatMaidanPathEarthy)
    drawMaidanArcWalkway(rig, path, cx, cz, 45.2f, 47.2f, 98f, 172f, 14, 0.65f, MatMaidanPathEarthy)
    drawMaidanArcWalkway(rig, path, cx, cz, 45.2f, 47.2f, 188f, 262f, 14, 0.65f, MatMaidanPathEarthy)
    drawMaidanArcWalkway(rig, path, cx, cz, 45.2f, 47.2f, 278f, 352f, 14, 0.65f, MatMaidanPathEarthy)

    // 3. Outer Granite & Laterite Pradakshinapatha Courtyard Platform around Vadakkumnathan Temple Wall
    val wR = 17.0f
    if (rig.buildClippedQuadPath(
            path,
            Vec3(tcx - wR - 2.8f, 1.16f, tcz - wR - 2.8f),
            Vec3(tcx + wR + 2.8f, 1.16f, tcz - wR - 2.8f),
            Vec3(tcx + wR + 2.8f, 1.16f, tcz + wR + 2.8f),
            Vec3(tcx - wR - 2.8f, 1.16f, tcz + wR + 2.8f)
        )
    ) {
        drawPath(path, MatGranitePaving)
    }

    // 4. Stepped Sacred Temple Pond (Vadakkumnathan Kulam) in SW Maidan Lawn (X = -33..-20, Z = -18..-5)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-33.4f, 0.72f, -18.4f),
            Vec3(-19.6f, 0.72f, -18.4f),
            Vec3(-19.6f, 0.72f, -4.6f),
            Vec3(-33.4f, 0.72f, -4.6f)
        )
    ) {
        drawPath(path, MatLateriteStone)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-32.2f, 0.76f, -17.2f),
            Vec3(-20.8f, 0.76f, -17.2f),
            Vec3(-20.8f, 0.76f, -5.8f),
            Vec3(-32.2f, 0.76f, -5.8f)
        )
    ) {
        drawPath(path, MatLateriteStepTop)
    }
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-31.2f, 0.79f, -16.2f),
            Vec3(-21.8f, 0.79f, -16.2f),
            Vec3(-21.8f, 0.79f, -6.8f),
            Vec3(-31.2f, 0.79f, -6.8f)
        )
    ) {
        drawPath(path, Color(0xFF0B6E78))
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

private fun addKeralaTempleGopuram3D(
    faces: MutableList<QuadFace3D>,
    gx: Float,
    gz: Float,
    baseY: Float,
    eastWestAxis: Boolean
) {
    // Open portal piers flanking the cardinal walkway (4.4m clear passage)
    val pierOffset = 3.2f
    val pierHalfSpan = 1.15f
    val pierHalfDepth = 1.95f
    val pierH = 3.85f
    for (s in floatArrayOf(-1f, 1f)) {
        if (eastWestAxis) {
            val pz = gz + s * pierOffset
            addBoxFaces(faces, gx - pierHalfDepth, baseY, pz - pierHalfSpan, gx + pierHalfDepth, baseY + 0.65f, pz + pierHalfSpan, MatLateriteStepTop, MatLateriteStone)
            addBoxFaces(faces, gx - pierHalfDepth + 0.1f, baseY + 0.65f, pz - pierHalfSpan + 0.08f, gx + pierHalfDepth - 0.1f, baseY + pierH, pz + pierHalfSpan - 0.08f, MatKeralaCreamWall, MatKeralaWhitewash)
            // Carved teak pillar pilasters facing the portal
            addBoxFaces(faces, gx - 1.5f, baseY + 0.65f, pz - s * pierHalfSpan, gx + 1.5f, baseY + pierH, pz - s * (pierHalfSpan + 0.14f), MatTeakWoodCarved, MatTeakWoodDeep)
        } else {
            val px = gx + s * pierOffset
            addBoxFaces(faces, px - pierHalfSpan, baseY, gz - pierHalfDepth, px + pierHalfSpan, baseY + 0.65f, gz + pierHalfDepth, MatLateriteStepTop, MatLateriteStone)
            addBoxFaces(faces, px - pierHalfSpan + 0.08f, baseY + 0.65f, gz - pierHalfDepth + 0.1f, px + pierHalfSpan - 0.08f, baseY + pierH, gz + pierHalfDepth - 0.1f, MatKeralaCreamWall, MatKeralaWhitewash)
            addBoxFaces(faces, px - s * pierHalfSpan, baseY + 0.65f, gz - 1.5f, px - s * (pierHalfSpan + 0.14f), baseY + pierH, gz + 1.5f, MatTeakWoodCarved, MatTeakWoodDeep)
        }
    }

    val spanW = if (eastWestAxis) 2.25f else 4.95f
    val spanD = if (eastWestAxis) 4.95f else 2.25f

    // Portal Lintel Architrave & Carved Teakwood Attic Cornice
    addBoxFaces(faces, gx - spanW, baseY + pierH - 0.35f, gz - spanD, gx + spanW, baseY + pierH + 0.25f, gz + spanD, MatTeakWoodCarved, MatTeakWoodDeep)

    // Tier 1 Terracotta Pitched Kerala Roof
    addPitchedKeralaRoof(
        faces,
        minX = gx - spanW - 0.75f,
        maxX = gx + spanW + 0.75f,
        minZ = gz - spanD - 0.75f,
        maxZ = gz + spanD + 0.75f,
        baseY = baseY + pierH + 0.18f,
        ridgeY = baseY + pierH + 2.35f,
        insetX = if (eastWestAxis) 1.35f else 1.85f,
        insetZ = if (eastWestAxis) 1.85f else 1.35f,
        roofColor = MatTerracottaTile,
        eaveColor = MatTerracottaRoofDark
    )

    // Tier 2 Recessed Clerestory & Teak Lattice Gallery
    val uW = if (eastWestAxis) 1.30f else 3.15f
    val uD = if (eastWestAxis) 3.15f else 1.30f
    addBoxFaces(faces, gx - uW, baseY + pierH + 2.20f, gz - uD, gx + uW, baseY + pierH + 3.85f, gz + uD, MatKeralaWhitewash, MatKeralaCreamWall)
    addBoxFaces(faces, gx - uW - 0.12f, baseY + pierH + 2.65f, gz - uD - 0.12f, gx + uW + 0.12f, baseY + pierH + 3.30f, gz + uD + 0.12f, MatTeakWoodCarved, MatTeakWoodDeep)

    // Tier 2 Crown Terracotta Roof + 3 Golden Kalasham Finials
    addPitchedKeralaRoof(
        faces,
        minX = gx - uW - 0.65f,
        maxX = gx + uW + 0.65f,
        minZ = gz - uD - 0.65f,
        maxZ = gz + uD + 0.65f,
        baseY = baseY + pierH + 3.78f,
        ridgeY = baseY + pierH + 5.75f,
        insetX = if (eastWestAxis) uW + 0.45f else 1.25f,
        insetZ = if (eastWestAxis) 1.25f else uD + 0.45f,
        roofColor = MatTerracottaTile,
        eaveColor = MatTeakWoodCarved
    )
    for (kOff in floatArrayOf(-1.55f, 0f, 1.55f)) {
        val kx = if (eastWestAxis) gx else gx + kOff
        val kz = if (eastWestAxis) gz + kOff else gz
        addCylinderFrustumFaces(faces, kx, kz, baseY + pierH + 5.75f, baseY + pierH + 6.55f, 0.16f, 0.03f, MatTempleGold, MatTempleGoldDeep, sides = 8)
    }
}

private fun addVadakkumnathanTempleComplex3D(
    faces: MutableList<QuadFace3D>,
    tcx: Float,
    tcz: Float,
    baseY: Float
) {
    val wR = 17.0f
    val gateHalf = 3.5f

    // 1. Continuous Stepped Granite Courtyard Apron Border around Chuttumathil (38.8m x 38.8m)
    addBoxFaces(
        faces,
        tcx - wR - 2.4f, baseY - 0.35f, tcz - wR - 2.4f,
        tcx + wR + 2.4f, baseY, tcz + wR + 2.4f,
        MatGranitePaving, MatLateriteStone, MatLateriteStone
    )

    // 2. Outer Chuttumathil Perimeter Wall + Vilakkumadam Brass Lamp Gallery (with 4 x 7m Cardinal Gateways)
    fun addChuttumathilWallBar(minX: Float, minZ: Float, maxX: Float, maxZ: Float) {
        addBoxFaces(faces, minX, baseY, minZ, maxX, baseY + 0.65f, maxZ, MatLateriteStepTop, MatLateriteStone)
        addBoxFaces(faces, minX + 0.05f, baseY + 0.65f, minZ + 0.05f, maxX - 0.05f, baseY + 2.85f, maxZ - 0.05f, MatKeralaCreamWall, MatKeralaWhitewash)
        // Warm Vilakkumadam brass oil-lamp band on exterior faces
        addBoxFaces(faces, minX - 0.04f, baseY + 1.45f, minZ - 0.04f, maxX + 0.04f, baseY + 1.92f, maxZ + 0.04f, MatTeakWoodDeep, MatVilakkumadamLampGlow)
        // Overhanging terracotta coping roof
        addBoxFaces(faces, minX - 0.25f, baseY + 2.85f, minZ - 0.25f, maxX + 0.25f, baseY + 3.25f, maxZ + 0.25f, MatTerracottaTile, MatTerracottaRoofDark)
    }
    // South Chuttumathil segments
    addChuttumathilWallBar(tcx - wR, tcz - wR, tcx - gateHalf, tcz - wR + 1.05f)
    addChuttumathilWallBar(tcx + gateHalf, tcz - wR, tcx + wR, tcz - wR + 1.05f)
    // North Chuttumathil segments
    addChuttumathilWallBar(tcx - wR, tcz + wR - 1.05f, tcx - gateHalf, tcz + wR)
    addChuttumathilWallBar(tcx + gateHalf, tcz + wR - 1.05f, tcx + wR, tcz + wR)
    // West Chuttumathil segments
    addChuttumathilWallBar(tcx - wR, tcz - wR + 1.05f, tcx - wR + 1.05f, tcz - gateHalf)
    addChuttumathilWallBar(tcx - wR, tcz + gateHalf, tcx - wR + 1.05f, tcz + wR - 1.05f)
    // East Chuttumathil segments
    addChuttumathilWallBar(tcx + wR - 1.05f, tcz - wR + 1.05f, tcx + wR, tcz - gateHalf)
    addChuttumathilWallBar(tcx + wR - 1.05f, tcz + gateHalf, tcx + wR, tcz + wR - 1.05f)

    // 3. Four 2-Tier Kerala Padippura Gopurams (West, East, North, South)
    addKeralaTempleGopuram3D(faces, tcx - wR, tcz, baseY, eastWestAxis = true)
    addKeralaTempleGopuram3D(faces, tcx + wR, tcz, baseY, eastWestAxis = true)
    addKeralaTempleGopuram3D(faces, tcx, tcz - wR, baseY, eastWestAxis = false)
    addKeralaTempleGopuram3D(faces, tcx, tcz + wR, baseY, eastWestAxis = false)

    // 4. Inner Nalambalam Cloister Ring (wIn = 11.4m, pitched terracotta cloister roof + courtyard veranda)
    val wIn = 11.4f
    val nGate = 2.4f
    fun addNalambalamWing(minX: Float, minZ: Float, maxX: Float, maxZ: Float) {
        addBoxFaces(faces, minX, baseY, minZ, maxX, baseY + 0.45f, maxZ, MatGranitePaving, MatLateriteStone)
        addBoxFaces(faces, minX + 0.15f, baseY + 0.45f, minZ + 0.15f, maxX - 0.15f, baseY + 2.65f, maxZ - 0.15f, MatKeralaCreamWall, MatKeralaWhitewash)
        addPitchedKeralaRoof(
            faces,
            minX = minX - 0.35f, maxX = maxX + 0.35f,
            minZ = minZ - 0.35f, maxZ = maxZ + 0.35f,
            baseY = baseY + 2.60f, ridgeY = baseY + 3.85f,
            insetX = if ((maxX - minX) < (maxZ - minZ)) (maxX - minX) * 0.45f else 0.7f,
            insetZ = if ((maxZ - minZ) < (maxX - minX)) (maxZ - minZ) * 0.45f else 0.7f,
            roofColor = MatTerracottaTile, eaveColor = MatTeakWoodCarved
        )
    }
    addNalambalamWing(tcx - wIn, tcz - wIn, tcx - nGate, tcz - wIn + 2.1f)
    addNalambalamWing(tcx + nGate, tcz - wIn, tcx + wIn, tcz - wIn + 2.1f)
    addNalambalamWing(tcx - wIn, tcz + wIn - 2.1f, tcx - nGate, tcz + wIn)
    addNalambalamWing(tcx + nGate, tcz + wIn - 2.1f, tcx + wIn, tcz + wIn)
    addNalambalamWing(tcx - wIn, tcz - wIn + 2.1f, tcx - wIn + 2.1f, tcz - nGate)
    addNalambalamWing(tcx - wIn, tcz + nGate, tcx - wIn + 2.1f, tcz + wIn - 2.1f)
    addNalambalamWing(tcx + wIn - 2.1f, tcz - wIn + 2.1f, tcx + wIn, tcz - nGate)
    addNalambalamWing(tcx + wIn - 2.1f, tcz + nGate, tcx + wIn, tcz + wIn - 2.1f)

    // 5. Central Iconic Circular Vatta-Sreekovil (Vadakkumnathan Sanctum, 16-sided stepped plinth & double conical copper/terracotta roof)
    // Stepped Laterite & Granite Adhishthanam Base
    addCylinderFrustumFaces(faces, tcx, tcz, baseY, baseY + 0.60f, 5.45f, 5.20f, MatLateriteStone, MatLateriteStepTop, sides = 16)
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 0.60f, baseY + 1.25f, 4.95f, 4.75f, MatGraniteDark, MatGranitePaving, sides = 16)
    // Whitewashed Cylindrical Sanctum Wall Drum + Golden Cornice Band
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 1.25f, baseY + 4.35f, 4.40f, 4.40f, MatKeralaWhitewash, MatKeralaCreamWall, sides = 16)
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 3.85f, baseY + 4.35f, 4.52f, 4.52f, MatTeakWoodCarved, MatTempleGoldDeep, sides = 16)
    // Carved Mukhamandapam Porch facing West (main Vadakkumnathan shrine) & East
    addBoxFaces(faces, tcx - 5.9f, baseY + 0.55f, tcz - 1.55f, tcx - 4.1f, baseY + 3.85f, tcz + 1.55f, MatTeakWoodCarved, MatKeralaCreamWall)
    addPitchedKeralaRoof(faces, tcx - 6.3f, tcx - 3.9f, tcz - 1.9f, tcz + 1.9f, baseY + 3.80f, baseY + 5.10f, 0.8f, 0.9f, MatCopperRoof, MatTempleGoldDeep)
    // Lower Conical Kerala Terracotta Roof
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 4.20f, baseY + 6.95f, 6.15f, 2.85f, MatTerracottaTile, MatTerracottaRoofDark, sides = 16)
    // Recessed Clerestory Neck
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 6.85f, baseY + 7.65f, 2.55f, 2.55f, MatTeakWoodCarved, MatTempleGoldDeep, sides = 16)
    // Upper Conical Copper Roof
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 7.50f, baseY + 10.35f, 3.65f, 0.42f, MatCopperRoof, MatCopperRoofDark, sides = 16)
    // Golden Thazhikakudam Spire
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 10.35f, baseY + 10.95f, 0.48f, 0.30f, MatTempleGoldDeep, MatTempleGold, sides = 12)
    addCylinderFrustumFaces(faces, tcx, tcz, baseY + 10.95f, baseY + 12.15f, 0.26f, 0.04f, MatTempleGold, MatTempleGold, sides = 12)

    // 6. Flanking Inner Shrines (Sankaranarayana North & Sri Rama South) + Copper Namaskara Mandapam
    for (sOff in floatArrayOf(-6.6f, 6.6f)) {
        val sz = tcz + sOff
        addBoxFaces(faces, tcx - 1.85f, baseY, sz - 1.65f, tcx + 1.85f, baseY + 2.55f, sz + 1.65f, MatKeralaCreamWall, MatKeralaWhitewash)
        addPitchedKeralaRoof(faces, tcx - 2.35f, tcx + 2.35f, sz - 2.15f, sz + 2.15f, baseY + 2.50f, baseY + 4.35f, 1.35f, 1.25f, MatCopperRoof, MatCopperRoofDark)
        addCylinderFrustumFaces(faces, tcx, sz, baseY + 4.35f, baseY + 5.05f, 0.16f, 0.03f, MatTempleGold, sides = 8)
    }
    // Copper-roofed Namaskara Mandapam (4-pillar ceremonial square pavilion west of Sreekovil)
    val nmX = tcx - 7.6f
    for (px in floatArrayOf(nmX - 1.15f, nmX + 1.15f)) {
        for (pz in floatArrayOf(tcz - 1.15f, tcz + 1.15f)) {
            addCylinderFrustumFaces(faces, px, pz, baseY + 0.35f, baseY + 2.45f, 0.16f, 0.13f, MatGraniteDark, sides = 8)
        }
    }
    addBoxFaces(faces, nmX - 1.45f, baseY, tcz - 1.45f, nmX + 1.45f, baseY + 0.38f, tcz + 1.45f, MatGranitePaving, MatLateriteStone)
    addPitchedKeralaRoof(faces, nmX - 1.85f, nmX + 1.85f, tcz - 1.85f, tcz + 1.85f, baseY + 2.40f, baseY + 4.05f, 1.55f, 1.55f, MatCopperRoof, MatTempleGoldDeep)

    // 7. Koothambalam (Traditional Temple Theatre Pavilion in NW Inner Compound)
    val kx = tcx - 7.5f
    val kz = tcz + 6.8f
    addBoxFaces(faces, kx - 2.5f, baseY, kz - 2.2f, kx + 2.5f, baseY + 0.65f, kz + 2.2f, MatLateriteStepTop, MatLateriteStone)
    addBoxFaces(faces, kx - 2.2f, baseY + 0.65f, kz - 1.9f, kx + 2.2f, baseY + 2.75f, kz + 1.9f, MatTeakWoodCarved, MatKeralaCreamWall)
    addPitchedKeralaRoof(faces, kx - 3.0f, kx + 3.0f, kz - 2.6f, kz + 2.6f, baseY + 2.70f, baseY + 5.10f, 1.3f, 1.9f, MatCopperRoof, MatTerracottaRoofDark)

    // 8. Anakottil (Tall 6-Pillar Ceremonial Elephant Portico just outside West Gopuram)
    val akMinX = tcx - wR - 7.2f
    val akMaxX = tcx - wR - 0.6f
    val akMinZ = tcz - 3.4f
    val akMaxZ = tcz + 3.4f
    addBoxFaces(faces, akMinX, baseY - 0.25f, akMinZ, akMaxX, baseY + 0.12f, akMaxZ, MatGranitePaving, MatLateriteStone)
    for (px in floatArrayOf(akMinX + 0.55f, (akMinX + akMaxX) * 0.5f, akMaxX - 0.55f)) {
        for (pz in floatArrayOf(akMinZ + 0.55f, akMaxZ - 0.55f)) {
            addCylinderFrustumFaces(faces, px, pz, baseY + 0.12f, baseY + 4.45f, 0.28f, 0.24f, MatKeralaCreamWall, MatLateriteStone, sides = 8)
        }
    }
    addPitchedKeralaRoof(faces, akMinX - 0.45f, akMaxX + 0.45f, akMinZ - 0.55f, akMaxZ + 0.55f, baseY + 4.40f, baseY + 6.75f, 1.85f, 2.4f, MatTerracottaTile, MatTeakWoodCarved)
    for (fx in floatArrayOf(akMinX + 2.0f, akMaxX - 2.0f)) {
        addCylinderFrustumFaces(faces, fx, tcz, baseY + 6.75f, baseY + 7.45f, 0.15f, 0.03f, MatTempleGold, sides = 8)
    }

    // 9. Golden Dwajasthambam (Flagstaff) & Granite Deepastambham (Lamp Pillar) on Main West & East Axes
    val flagX = tcx - wR - 8.6f
    addBoxFaces(faces, flagX - 0.95f, baseY - 0.20f, tcz - 0.95f, flagX + 0.95f, baseY + 0.75f, tcz + 0.95f, MatGranitePaving, MatLateriteStone)
    addCylinderFrustumFaces(faces, flagX, tcz, baseY + 0.75f, baseY + 10.8f, 0.26f, 0.12f, MatTempleGoldDeep, MatTempleGold, sides = 12)
    addCylinderFrustumFaces(faces, flagX, tcz, baseY + 10.8f, baseY + 11.45f, 0.34f, 0.06f, MatTempleGold, sides = 12)

    val deepaX = tcx + wR + 2.2f
    addBoxFaces(faces, deepaX - 0.85f, baseY - 0.10f, tcz - 0.85f, deepaX + 0.85f, baseY + 0.70f, tcz + 0.85f, MatGraniteDark, MatLateriteStone)
    addCylinderFrustumFaces(faces, deepaX, tcz, baseY + 0.70f, baseY + 5.60f, 0.24f, 0.14f, MatGranitePaving, MatTempleGold, sides = 10)
    for (tierY in floatArrayOf(2.4f, 3.3f, 4.2f, 5.1f)) {
        addCylinderFrustumFaces(faces, deepaX, tcz, baseY + tierY, baseY + tierY + 0.16f, 0.56f, 0.42f, MatTempleGoldDeep, MatVilakkumadamLampGlow, sides = 10)
    }
}

private var cachedSwarajRoundFaces: List<QuadFace3D>? = null

private fun collectSwarajRoundFaces(
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>,
    outFaces: MutableList<QuadFace3D>
) {
    val cx = 3.1f
    val cz = 18.0f
    val dx = camEye.x - cx
    val dz = camEye.z - cz
    if (dx * dx + dz * dz > 260f * 260f) return

    val existing = cachedSwarajRoundFaces
    if (existing != null) {
        outFaces.addAll(existing)
        return
    }

    val tcx = 3.1f
    val tcz = 18.0f
    val wR = 17.0f
    val templeBaseY = 1.16f

    val faces = ArrayList<QuadFace3D>(3600)
    val swarajFrames = roads.firstOrNull { it.id == "ROAD_SWARAJ_ROUND" }?.frames

    // A. INNER SHOULDER: 1.2m High Continuous Weathered Laterite Stone Boundary Wall
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

    // B. 4 CARDINAL LATERITE NADAPPATHA AVENUES CONNECTING R=57.5m PORTALS TO THE 4 GOPURAMS
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

    val rimDist = 57.5f
    // South Avenue (-Z)
    addNadappathaAvenue(
        Vec3(tcx - 2.3f, 0.40f, cz - rimDist),
        Vec3(tcx + 2.3f, 0.40f, cz - rimDist),
        Vec3(tcx + 2.3f, templeBaseY, tcz - wR),
        Vec3(tcx - 2.3f, templeBaseY, tcz - wR)
    )
    // North Avenue (+Z)
    addNadappathaAvenue(
        Vec3(tcx + 2.3f, 0.40f, cz + rimDist),
        Vec3(tcx - 2.3f, 0.40f, cz + rimDist),
        Vec3(tcx - 2.3f, templeBaseY, tcz + wR),
        Vec3(tcx + 2.3f, templeBaseY, tcz + wR)
    )
    // West Avenue (-X, leads through Anakottil to West Gopuram)
    addNadappathaAvenue(
        Vec3(cx - rimDist, 0.40f, tcz + 2.3f),
        Vec3(cx - rimDist, 0.40f, tcz - 2.3f),
        Vec3(tcx - wR - 7.2f, templeBaseY, tcz - 2.3f),
        Vec3(tcx - wR - 7.2f, templeBaseY, tcz + 2.3f)
    )
    // East Avenue (+X)
    addNadappathaAvenue(
        Vec3(cx + rimDist, 0.40f, tcz - 2.3f),
        Vec3(cx + rimDist, 0.40f, tcz + 2.3f),
        Vec3(tcx + wR, templeBaseY, tcz + 2.3f),
        Vec3(tcx + wR, templeBaseY, tcz - 2.3f)
    )

    // C. VADAKKUMNATHAN TEMPLE COMPLEX (34m x 34m Chuttumathil, 4 Gopurams, 2-Tier Circular Vatta-Sreekovil, Koothambalam, Anakottil)
    addVadakkumnathanTempleComplex3D(faces, tcx, tcz, templeBaseY)

    // D. 4 ANCIENT BANYAN TREES (AALMARAM) WITH 12-SIDED DRESSED LATERITE AALTHARA PLATFORMS IN THE 4 QUADRANT LAWNS
    val banyans = arrayOf(
        floatArrayOf(29.5f, -10.5f, 3.4f, 6.8f, 14.5f),
        floatArrayOf(30.0f, 45.5f, 3.2f, 6.4f, 13.8f),
        floatArrayOf(-24.5f, 45.0f, 3.4f, 6.6f, 14.2f),
        floatArrayOf(-22.5f, -23.5f, 3.1f, 6.2f, 13.2f)
    )
    for (b in banyans) {
        val bx = b[0]
        val bz = b[1]
        addAncientBanyanTreeWithAalthara3D(
            faces = faces,
            bx = bx,
            bz = bz,
            baseY = maidanHillY(bx, bz) - 0.08f,
            aaltharaRadius = b[2],
            crownRadius = b[3],
            treeHeight = b[4]
        )
    }

    if (swarajFrames != null) {
        cachedSwarajRoundFaces = faces
    }
    outFaces.addAll(faces)
}

/**
 * Helper to construct a continuous Kerala Laterite & Plastered Perimeter Compound Wall
 * with dressed granite plinth, brick pilasters every ~6.5m, and sloped terracotta/concrete coping.
 */
private fun addPerimeterCompoundWallSegment(
    faces: MutableList<QuadFace3D>,
    minX: Float,
    minZ: Float,
    maxX: Float,
    maxZ: Float,
    wallColor: Color = MatKeralaCreamWall,
    pilasterColor: Color = MatLateriteStone,
    copingColor: Color = MatTerracottaTile
) {
    // Dressed Laterite Plinth Base (Y = 0.06m .. 0.45m)
    addBoxFaces(faces, minX, 0.06f, minZ, maxX, 0.45f, maxZ, MatGranitePaving, MatLateriteDark, MatLateriteStone)
    // Main Plastered Masonry Wall Panel (Y = 0.45m .. 2.05m)
    addBoxFaces(faces, minX + 0.04f, 0.45f, minZ + 0.04f, maxX - 0.04f, 2.05f, maxZ - 0.04f, wallColor, wallColor, wallColor)
    // Sloped Weatherproof Coping Cap (Y = 2.05m .. 2.28m)
    addBoxFaces(faces, minX - 0.06f, 2.05f, minZ - 0.06f, maxX + 0.06f, 2.28f, maxZ + 0.06f, copingColor, pilasterColor, pilasterColor)

    // Periodic Structural Masonry Pilasters along the longer axis
    val spanX = maxX - minX
    val spanZ = maxZ - minZ
    if (spanX >= spanZ) {
        var px = minX + 0.35f
        while (px <= maxX - 0.35f) {
            addBoxFaces(faces, px - 0.28f, 0.06f, minZ - 0.08f, px + 0.28f, 2.42f, maxZ + 0.08f, copingColor, pilasterColor, pilasterColor)
            px += 6.5f
        }
    } else {
        var pz = minZ + 0.35f
        while (pz <= maxZ - 0.35f) {
            addBoxFaces(faces, minX - 0.08f, 0.06f, pz - 0.28f, maxX + 0.08f, 2.42f, pz + 0.28f, copingColor, pilasterColor, pilasterColor)
            pz += 6.5f
        }
    }
}

// ============================================================================
// 1B. ZONE 1.3: THRISSUR RAILWAY STATION
//     Delegated to ThrissurRailwayStation3DModel.kt (X = -98.0..-64.0m, Z = -204.0..-62.0m)
// ============================================================================

private fun DrawScope.drawThrissurRailwayStationGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    drawThrissurModernStationYard(rig, path, camEye)
}

private fun collectThrissurRailwayStationFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    collectThrissurModernStationSceneFaces(camEye, outFaces)
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
    // Placed at X = 9.6m, Z = -63.0m — safely West of M.O. Road centerline (X=20.9m) and 7m South of Swaraj outer sidewalk,
    // 100% clear of the Swaraj Round roadway, bellmouth flare, and M.O. Road driving lanes!
    val postX = 9.6f
    val postZ = -63.0f

    // Moulded concrete base + tapered cylindrical galvanized steel mast column (0.06m .. 6.65m)
    addCylinderFrustumFaces(built, postX, postZ, 0.06f, 0.65f, 0.34f, 0.28f, MatGraniteDark, sides = 8)
    addCylinderFrustumFaces(built, postX, postZ, 0.65f, 6.68f, 0.18f, 0.14f, MatGraniteLight, sides = 8)

    // Overhead cantilever galvanized steel mast arm at 6.5m height extending Eastward over the M.O. Road northbound lane (X = 9.6f .. 24.9f)
    addBoxFaces(built, postX, 6.40f, postZ - 0.12f, 24.9f, 6.60f, postZ + 0.12f, MatGraniteLight, MatPromenadeTile)

    // Two 3-Aspect 300mm Signal Head Backplates with Black Visors & 50mm Yellow Reflective Borders suspended at Y = 5.42m..6.72m
    for (headX in floatArrayOf(19.9f, 23.4f)) {
        addBoxFaces(built, headX - 0.34f, 5.42f, postZ - 0.14f, headX + 0.34f, 6.72f, postZ + 0.14f, MatRoadMarkingYellow, MatHazardBlack)
    }
    // Pedestrian Signal Head Housing mounted on the roadside vertical post at X = 9.6m (Y = 2.45m .. 3.35m)
    addBoxFaces(built, postX - 0.30f, 2.45f, postZ - 0.14f, postX + 0.30f, 3.35f, postZ + 0.14f, MatRoadMarkingYellow, MatHazardBlack)

    cachedManikandanalMastFaces = built
    return built
}

private fun collectManikandanalSignalAndStreetPropsFaces(
    camEye: Vec3,
    manikandanalSignal: TrafficSignalAspect,
    outFaces: MutableList<QuadFace3D>
) {
    val dx = camEye.x - 19f
    val dz = camEye.z - (-63f)
    if (dx * dx + dz * dz > 180f * 180f) return

    outFaces.addAll(getOrBuildManikandanalMastFaces())

    val sigSouthZ = -63.20f
    val sigNorthZ = -62.80f
    val isRed = manikandanalSignal == TrafficSignalAspect.Red
    val isAmber = manikandanalSignal == TrafficSignalAspect.Amber
    val isGreen = manikandanalSignal == TrafficSignalAspect.Green

    val redCol = if (isRed) Color(0xFFFF1E1E) else Color(0xFF450A0A)
    val amberCol = if (isAmber) Color(0xFFFACC15) else Color(0xFF422006)
    val greenCol = if (isGreen) Color(0xFF22C55E) else Color(0xFF052E16)

    for (headX in floatArrayOf(19.9f, 23.4f)) {
        // South-facing LED lenses (for Northbound traffic approaching Swaraj Round)
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 6.26f, sigSouthZ), Vec3(headX + 0.16f, 6.26f, sigSouthZ), Vec3(headX + 0.16f, 6.58f, sigSouthZ), Vec3(headX - 0.16f, 6.58f, sigSouthZ), redCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 5.90f, sigSouthZ), Vec3(headX + 0.16f, 5.90f, sigSouthZ), Vec3(headX + 0.16f, 6.22f, sigSouthZ), Vec3(headX - 0.16f, 6.22f, sigSouthZ), amberCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX - 0.16f, 5.54f, sigSouthZ), Vec3(headX + 0.16f, 5.54f, sigSouthZ), Vec3(headX + 0.16f, 5.86f, sigSouthZ), Vec3(headX - 0.16f, 5.86f, sigSouthZ), greenCol, biasDist = -1.50f))
        // North-facing LED lenses (for Southbound traffic departing Swaraj Round)
        outFaces.add(QuadFace3D(Vec3(headX + 0.16f, 6.26f, sigNorthZ), Vec3(headX - 0.16f, 6.26f, sigNorthZ), Vec3(headX - 0.16f, 6.58f, sigNorthZ), Vec3(headX + 0.16f, 6.58f, sigNorthZ), redCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX + 0.16f, 5.90f, sigNorthZ), Vec3(headX - 0.16f, 5.90f, sigNorthZ), Vec3(headX - 0.16f, 6.22f, sigNorthZ), Vec3(headX + 0.16f, 6.22f, sigNorthZ), amberCol, biasDist = -1.50f))
        outFaces.add(QuadFace3D(Vec3(headX + 0.16f, 5.54f, sigNorthZ), Vec3(headX - 0.16f, 5.54f, sigNorthZ), Vec3(headX - 0.16f, 5.86f, sigNorthZ), Vec3(headX + 0.16f, 5.86f, sigNorthZ), greenCol, biasDist = -1.50f))
    }

    val pedX = 9.6f
    val pedStandCol = if (!isRed) Color(0xFFFF1E1E) else Color(0xFF3F1111)
    val pedWalkCol = if (isRed) Color(0xFF22C55E) else Color(0xFF0A2E1A)
    outFaces.add(QuadFace3D(Vec3(pedX - 0.15f, 2.92f, sigSouthZ), Vec3(pedX + 0.15f, 2.92f, sigSouthZ), Vec3(pedX + 0.15f, 3.26f, sigSouthZ), Vec3(pedX - 0.15f, 3.26f, sigSouthZ), pedStandCol, biasDist = -1.50f))
    outFaces.add(QuadFace3D(Vec3(pedX - 0.15f, 2.52f, sigSouthZ), Vec3(pedX + 0.15f, 2.52f, sigSouthZ), Vec3(pedX + 0.15f, 2.86f, sigSouthZ), Vec3(pedX - 0.15f, 2.86f, sigSouthZ), pedWalkCol, biasDist = -1.50f))
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

    // Animated breaking surf waves & shoreline foam are rendered dynamically via GPU water shader!

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
            Vec3(-2241.5f, 0.059f, -2485.0f),
            Vec3(-2231.5f, 0.059f, -2485.0f),
            Vec3(-2231.5f, 0.059f, -2335.0f),
            Vec3(-2241.5f, 0.059f, -2335.0f)
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

    // Animated breaking surf waves & shoreline foam are rendered dynamically via GPU water shader!

    // Triprayar Stand Concrete Terminal Apron (set back cleanly East of 22m 6-lane NH 66)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(-2208.5f, 0.058f, -2456.0f),
            Vec3(-2176.0f, 0.058f, -2456.0f),
            Vec3(-2176.0f, 0.058f, -2364.0f),
            Vec3(-2208.5f, 0.058f, -2364.0f)
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

        addBoxFaces(built, -2232.3f, 0.06f, -2480.0f, -2231.5f, 0.85f, -2418.0f, MatKeralaWhitewash, MatSeawallGranite)
        addBoxFaces(built, -2232.3f, 0.06f, -2402.0f, -2231.5f, 0.85f, -2340.0f, MatKeralaWhitewash, MatSeawallGranite)

        for (gz in floatArrayOf(-2435.0f, -2385.0f)) {
            addBoxFaces(built, -2256.0f, 0.06f, gz - 4.0f, -2246.0f, 3.2f, gz + 4.0f, MatPromenadeTile, MatKeralaWhitewash)
            addPitchedKeralaRoof(built, -2257.5f, -2244.5f, gz - 5.5f, gz + 5.5f, 3.1f, 5.8f, 5.8f, 5.8f, MatTerracottaTile)
        }
        for ((vz, hullCol) in arrayOf(Pair(-2452.0f, Color(0xFF0284C7)), Pair(-2410.0f, Color(0xFFDC2626)), Pair(-2368.0f, Color(0xFF059669)))) {
            addBoxFaces(built, -2274.0f, 0.08f, vz - 1.1f, -2262.0f, 1.15f, vz + 1.1f, MatTeakWoodCarved, hullCol, MatTeakWoodCarved)
        }

        addBoxFaces(built, -2304.0f, 0.8f, -2414.0f, -2236.0f, 1.3f, -2406.0f, MatPromenadeTile, MatTeakWoodCarved)
        addBoxFaces(built, -2248.0f, 0.06f, -2356.0f, -2242.0f, 16.5f, -2350.0f, MatTempleGold, MatKeralaWhitewash, MatTerracottaTile)

        // Triprayar Stand Perimeter Wall & Entry / Exit Gate Pillars at X = -2208.5m (clear of 22m 6-lane NH 66)
        addBoxFaces(built, -2208.5f, 0.06f, -2456.0f, -2207.7f, 1.35f, -2436.5f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -2208.5f, 0.06f, -2423.5f, -2207.7f, 1.35f, -2396.5f, MatKeralaWhitewash, MatLateriteStone)
        addBoxFaces(built, -2208.5f, 0.06f, -2383.5f, -2207.7f, 1.35f, -2364.0f, MatKeralaWhitewash, MatLateriteStone)
        for (pz in floatArrayOf(-2436.2f, -2423.8f, -2396.2f, -2383.8f)) {
            addBoxFaces(built, -2208.5f, 0.06f, pz - 0.4f, -2207.7f, 5.8f, pz + 0.4f, MatTerracottaRoofDark, MatTerracottaTile)
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
    // A. OUTER PLAZA TOLL ISLANDS, YELLOW BOLLARDS, GLAZED TOLL BOOTHS & CANOPY COLUMNS
    //    NOTE: Islands iIdx in 3..7 (X = 988.3m..1010.7m) are omitted so the 24m main NH 544
    //    carriageway (X = 987.5m..1011.5m) has ZERO ground-level obstacles under the overhead FASTag gantry!
    // =========================================================================
    for (iIdx in 0..10) {
        if (iIdx in 3..7) continue
        val ix = tx + (iIdx - 5) * lanePitch
        val iHalfW = 0.42f

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
                topCol = col,
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
        // White electronic lane-control cabinet
        addBoxFaces(
            faces,
            ix - 0.30f, 0.90f, -1791.6f,
            ix + 0.30f, 1.65f, -1791.0f,
            topCol = MatFastagWhite,
            sideCol = MatFastagWhite,
            frontBackCol = MatPromenadeTile
        )

        // 3. Tall Cream Concrete Canopy Support Columns rising from each Outer Plaza Island
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

        // 4. Glazed Toll Collector Booth Kiosks on Outer Plaza Islands only
        if (iIdx in 1..2 || iIdx in 8..9) {
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
private fun DrawScope.drawVadakkeBusStandGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    drawVadakkeModernTerminalYard(rig, path, camEye)
}

private fun collectVadakkeBusStandFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    collectVadakkeModernTerminalSceneFaces(camEye, outFaces)
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

@Volatile
private var cachedRoadsideClusters: List<CachedSceneryCluster>? = null

private fun isClearForRoadsideScenery(
    bx: Float,
    bz: Float,
    roads: List<ThrissurOsmRoad>,
    signPlacements: List<CachedSignRenderItem>
): Boolean {
    // Zone 1 uses exclusively genuine Google Drive 3D buildings, trees, and grass via Zone1GDriveEnvironmentRenderer
    if (bx in -285f..265f && bz in -285f..270f) return false
    if (bx in -2792f..-2560f && bz in 1220f..1342f) return false
    if (bx in -4760f..-4446f && bz in 950f..1210f) return false
    if (bx in -2480f..-2166f && bz in -2520f..-2300f) return false
    if (bx in 945f..1055f && bz in -1870f..-1745f) return false
    // Thrissur Railway Station (SW), Wadakkanchery ROB & Town, Shoranur Bus Terminal, Shoranur Junction & 2-track Railway Corridor
    if (bx in -132f..-58f && bz in -232f..-52f) return false
    if (bx in 50f..150f && bz in 1500f..1970f) return false
    if (bx in 110f..212f && bz in 3000f..3150f) return false
    if (bx in -25f..88f && bz in 3130f..3340f) return false
    if (ShoranurRailwaySystem.isNearRailwayTrack(bx, bz, 16.5f)) return false
    if (!Zone2Nh544KuthiranRenderer.isPointClearOfZone2Structures(bx, bz)) return false
    if (!Zone3Nh544SouthRenderer.isPointClearOfZone3Structures(bx, bz)) return false
    if (!Zone4KoleRailHeritageRenderer.isPointClearOfZone4Structures(bx, bz)) return false
    if (!Zone5Nh66CoastalRenderer.isPointClearOfZone5Structures(bx, bz)) return false
    if (!PuthanpallyBasilicaRenderer.isPointClearOfPuthanpally(bx, bz)) return false
    if (!ThrissurFuelStationSystem.isPointClearOfFuelStations(bx, bz, extraMargin = 6.5f)) return false

    if (!isPointClearOfAllRoads(bx, bz, roads, extraMarginM = 11.5f)) return false

    val minSignSq = 15.0f * 15.0f
    for (i in 0 until signPlacements.size) {
        val s = signPlacements[i]
        val dx = bx - s.midX
        val dz = bz - s.midZ
        if (dx * dx + dz * dz < minSignSq) return false
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
    // JUNCTION A: M.O. ROAD / RAILWAY STATION LINK / OLLUR LINK / SAKTHAN ENTRY
    // ========================================================================
    // A0. Full-Width Overhead Gantry on Sakthan South-East Boundary Exit Road (approaching Ollur / M.O. Road)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.21f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "തൃശ്ശൂർ നഗരം • THRISSUR CITY — SAKTHAN EXIT CORRIDOR",
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "SWARAJ ROUND", "UP", "250 M"),
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "RAILWAY STATION", "LEFT", "180 M"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ NH 544", "OLLUR", "RIGHT", "1.2 KM"),
        travelDir = 1
    ),
    // A1. Northbound on M.O. Road approaching Railway Station & Sakthan Entry Slip Turnoff (Z = -98.0m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.77f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.3"),
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "LEFT", "0.1"),
        rightDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "RIGHT", "0.5"),
        travelDir = 1
    ),
    // A2. Southbound on M.O. Road from Swaraj Round approaching Puthanpalli Link (Z = -86.0m) & Railway Station Turnoff (Z = -98.0m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.86f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.3"),
        leftDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "LEFT", "0.5"),
        rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.1"),
        travelDir = -1
    ),
    // A3. Southbound on M.O. Road approaching Ollur Boundary Link & Sakthan Stand
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.71f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ശക്തൻ എൻട്രി", "Sakthan Entry", "RIGHT", "0.1"),
        leftDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "UP", "1.2"),
        rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.1"),
        travelDir = -1
    ),
    // A4. Northbound on Ollur Road approaching Sakthan South-East T-Junction
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_OLLUR",
        frameFraction = 0.032f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "RIGHT", "0.4"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.2"),
        rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.2"),
        travelDir = -1
    ),
    // A5. On Sakthan Entry / Station Link Road approaching the Railway Station Turnoff (Z = -121.0m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_STAND_ENTRY",
        frameFraction = 0.26f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.1"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.1"),
        rightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "LEFT", "0.3"),
        travelDir = 1
    ),
    // A6. Northbound on Station Road approaching Thrissur Railway Station (TCR) West Entry
    KeralaRoadSignSpec(
        roadId = "ROAD_STATION_ROAD",
        frameFraction = 0.22f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.2"),
        leftDest = KeralaSignDestination("തൃശ്ശൂർ റെയിൽവേ", "Railway Station (TCR)", "LEFT", "0.1"),
        rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "RIGHT", "0.2"),
        travelDir = 1
    ),
    // A7. Northbound on Station Road approaching Swaraj Round South-West (Kuruppam Road Junction)
    KeralaRoadSignSpec(
        roadId = "ROAD_STATION_ROAD",
        frameFraction = 0.84f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.1"),
        leftDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "LEFT", "1.0"),
        rightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "RIGHT", "1.3"),
        travelDir = 1
    ),
    // A8. Southbound on Station Road from Swaraj Round approaching Thrissur Railway Station & Sakthan Stand
    KeralaRoadSignSpec(
        roadId = "ROAD_STATION_ROAD",
        frameFraction = 0.72f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.2"),
        leftDest = KeralaSignDestination("എം.ഒ. റോഡ്", "M.O. Road", "LEFT", "0.2"),
        rightDest = KeralaSignDestination("തൃശ്ശൂർ റെയിൽവേ", "Railway Station (TCR)", "RIGHT", "0.1"),
        travelDir = -1
    ),

    // ========================================================================
    // JUNCTION B: M.O. ROAD / HIGH ROAD (PUTHANPALLI LINK) (All Approaches!)
    // ========================================================================
    // B1. Northbound on M.O. Road approaching High Road / Puthanpalli Link (Z = -86.0m)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.83f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.3"),
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "LEFT", "0.1"),
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
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "UP", "0.2"),
        rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.3"),
        travelDir = -1
    ),

    // ========================================================================
    // JUNCTION C: SWARAJ ROUND — ALL 4 RADIAL APPROACHES & ALL RING EXITS!
    // ========================================================================
    // C0. Swaraj Round Clockwise Ring -> Before Station Road / Kuruppam Road Exit (South-West)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.025f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "UP", "1.0"),
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "LEFT", "0.1"),
        rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.3"),
        travelDir = 1
    ),
    // C1. South Approach (M.O. Road Northbound into Swaraj Round South / Manikandanal)
    KeralaRoadSignSpec(
        roadId = "ROAD_SAKTHAN_TO_SWARAJ",
        frameFraction = 0.93f,
        side = -1,
        style = KeralaSignStyle.ROUNDABOUT_DIAGRAM,
        headerTitle = "സ്വരാജ് റൗണ്ട് • SWARAJ ROUND",
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "1.3"),
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "LEFT", "0.2"),
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
        rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.3"),
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
        rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.3"),
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
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "LEFT", "0.3"),
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
    // C8. Swaraj Round Clockwise Ring -> Before South Exit (M.O. Road / Sakthan Stand / Railway Station)
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_ROUND",
        frameFraction = 0.845f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "UP", "0.1"),
        leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "LEFT", "0.4"),
        rightDest = KeralaSignDestination("ഒല്ലൂർ", "Ollur", "LEFT", "1.5"),
        travelDir = 1
    ),

    // ========================================================================
    // JUNCTION D: VADAKKE STAND (SHORANUR ROAD) & WEST FORT (M.G. ROAD)
    // ========================================================================
    // D1. Northbound on Shoranur Road approaching Vadakke Bus Stand East ENTRY & West EXIT Merge
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_VADAKKE",
        frameFraction = 0.72f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "0.2"),
        leftDest = KeralaSignDestination("സ്റ്റാൻഡ് എക്സിറ്റ്", "Stand Exit", "LEFT", "0.1"),
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
    // JUNCTION F: MANNUTHY 4-WAY FLYOVER INTERCHANGE (Zone 2 NH 544 / SH 51!)
    // ========================================================================
    // F1. Eastbound from Thrissur approaching Mannuthy 4-Way Interchange
    KeralaRoadSignSpec(
        roadId = "ROAD_SWARAJ_TO_MANNUTHY",
        frameFraction = 0.905f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "MANNUTHY 4-WAY FLYOVER • NH 544 / SH 51",
        straightDest = KeralaSignDestination("പീച്ചി ഡാം", "PEECHI DAM", "UP", "15 KM"),
        leftDest = KeralaSignDestination("കുതിരാൻ / പാലക്കാട്", "KUTHIRAN / PALAKKAD", "LEFT", "68 KM"),
        rightDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "KOCHI / TOLL NH 544", "RIGHT", "16 KM"),
        travelDir = 1
    ),
    // F2. Westbound from Peechi Dam approaching Mannuthy 4-Way Interchange
    KeralaRoadSignSpec(
        roadId = "ROAD_MANNUTHY_TO_PEECHI_DAM",
        frameFraction = 0.055f,
        side = 1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        straightDest = KeralaSignDestination("തൃശ്ശൂർ നഗരം (ഈസ്റ്റ് ഫോർട്ട്)", "Thrissur City", "UP", "6.0"),
        leftDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "Paliyekkara Toll", "LEFT", "16.0"),
        rightDest = KeralaSignDestination("കുതിരാൻ / പാലക്കാട്", "Kuthiran / Palakkad", "RIGHT", "68.0"),
        travelDir = -1
    ),
    // F3. Northbound on NH 544 from Ollur/Paliyekkara approaching Mannuthy 4-Way Interchange
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.095f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "NH 544 • MANNUTHY 4-WAY INTERCHANGE",
        straightDest = KeralaSignDestination("കുതിരാൻ / പാലക്കാട്", "KUTHIRAN / PALAKKAD", "UP", "68 KM"),
        leftDest = KeralaSignDestination("തൃശ്ശൂർ നഗരം (ഈസ്റ്റ് ഫോർട്ട്)", "THRISSUR CITY", "LEFT", "6 KM"),
        rightDest = KeralaSignDestination("പീച്ചി ഡാം", "PEECHI DAM", "RIGHT", "15 KM"),
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
    // G4. Southbound on NH 544 approaching Paliyekkara Toll Plaza (cleanly ahead of North S-curve flare)
    KeralaRoadSignSpec(
        roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        frameFraction = 0.775f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "NATIONAL HIGHWAY • NH 544",
        straightDest = KeralaSignDestination("പാലിയേക്കര ടോൾ", "PALIYEKKARA TOLL", "UP", "200 M"),
        leftDest = KeralaSignDestination("ഫാസ്ടാഗ് ലെയിൻ", "FASTAG LANES", "UP", "200 M"),
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
    ),
    // I1. Northbound on SH 22 leaving Thrissur / Viyyur towards Wadakkanchery & Shoranur
    KeralaRoadSignSpec(
        roadId = "ROAD_THRISSUR_TO_SHORANUR",
        frameFraction = 0.085f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        headerTitle = "STATE HIGHWAY • SH 22 NORTHBOUND",
        straightDest = KeralaSignDestination("വടക്കാഞ്ചേരി ROB", "Wadakkanchery ROB", "UP", "15 KM"),
        leftDest = KeralaSignDestination("ഷൊർണ്ണൂർ ടെർമിനൽ", "Shoranur Stand", "UP", "31 KM"),
        rightDest = KeralaSignDestination("ഷൊർണ്ണൂർ ജംഗ്ഷൻ", "Shoranur Jn (SRR)", "UP", "33 KM"),
        travelDir = 1
    ),
    // I2. Overhead Gantry approaching Athani / Mulagunnathukavu -> Wadakkanchery ROB
    KeralaRoadSignSpec(
        roadId = "ROAD_THRISSUR_TO_SHORANUR",
        frameFraction = 0.360f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "SH 22 • THRISSUR - WADAKKANCHERY - SHORANUR",
        straightDest = KeralaSignDestination("വടക്കാഞ്ചേരി ROB", "WADAKKANCHERY", "UP", "3.5 KM"),
        leftDest = KeralaSignDestination("ഷൊർണ്ണൂർ ജംഗ്ഷൻ", "SHORANUR JN", "UP", "19 KM"),
        rightDest = KeralaSignDestination("ഷൊർണ്ണൂർ സ്റ്റാൻഡ്", "SHORANUR STAND", "UP", "18 KM"),
        travelDir = 1
    ),
    // I3. Northbound approaching Shoranur Bus Terminal (East) & Shoranur Junction Station (West)
    KeralaRoadSignSpec(
        roadId = "ROAD_THRISSUR_TO_SHORANUR",
        frameFraction = 0.855f,
        side = 0,
        style = KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY,
        headerTitle = "SHORANUR TOWN • SH 22 TERMINUS",
        straightDest = KeralaSignDestination("ഷൊർണ്ണൂർ ജംഗ്ഷൻ", "SHORANUR JN (SRR)", "LEFT", "0.4 KM"),
        leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "RAILWAY STATION", "LEFT", "0.4 KM"),
        rightDest = KeralaSignDestination("ഷൊർണ്ണൂർ ബസ് ടെർമിനൽ", "SHORANUR TERMINAL", "RIGHT", "0.1 KM"),
        travelDir = 1
    ),
    // I4. Northbound between Shoranur Bus Terminal & Shoranur Junction Railway Station
    KeralaRoadSignSpec(
        roadId = "ROAD_THRISSUR_TO_SHORANUR",
        frameFraction = 0.915f,
        side = -1,
        style = KeralaSignStyle.STACKED_3_PANEL,
        headerTitle = "SHORANUR JUNCTION APPROACH",
        straightDest = KeralaSignDestination("ഷൊർണ്ണൂർ ജംഗ്ഷൻ (SRR)", "Shoranur Jn Station", "LEFT", "0.1 KM"),
        leftDest = KeralaSignDestination("പ്ലാറ്റ്ഫോം 1 & 2", "Trains to TCR / PGT", "LEFT", "0.1 KM"),
        rightDest = KeralaSignDestination("ഷൊർണ്ണൂർ ബസ് സ്റ്റാൻഡ്", "Shoranur Bus Stand", "RIGHT", "0.2 KM"),
        travelDir = 1
    )
)

private class RoadFrameSpatialGrid(roads: List<ThrissurOsmRoad>) {
    private val cellSize = 32.0f
    private val invCellSize = 1f / cellSize
    private val grid = HashMap<Long, ArrayList<RoadFrame>>(4096)

    init {
        for (road in roads) {
            val frames = road.frames
            for (i in 0 until frames.size) {
                val f = frames[i]
                val cx = kotlin.math.floor(f.pos.x * invCellSize).toInt()
                val cz = kotlin.math.floor(f.pos.z * invCellSize).toInt()
                val key = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
                grid.getOrPut(key) { ArrayList(8) }.add(f)
            }
        }
    }

    fun isClear(px: Float, pz: Float, extraMarginM: Float): Boolean {
        val cx0 = kotlin.math.floor(px * invCellSize).toInt()
        val cz0 = kotlin.math.floor(pz * invCellSize).toInt()
        for (dxC in -1..1) {
            for (dzC in -1..1) {
                val key = ((cx0 + dxC).toLong() shl 32) or ((cz0 + dzC).toLong() and 0xFFFFFFFFL)
                val bucket = grid[key] ?: continue
                for (i in 0 until bucket.size) {
                    val f = bucket[i]
                    val minClear = f.halfWidthM + extraMarginM
                    val dx = px - f.pos.x
                    val dz = pz - f.pos.z
                    if (dx * dx + dz * dz < minClear * minClear) {
                        return false
                    }
                }
            }
        }
        return true
    }
}

@Volatile
private var cachedRoadSpatialRoadsRef: List<ThrissurOsmRoad>? = null
@Volatile
private var cachedRoadSpatialGrid: RoadFrameSpatialGrid? = null

private fun getOrBuildRoadSpatialGrid(roads: List<ThrissurOsmRoad>): RoadFrameSpatialGrid {
    val existing = cachedRoadSpatialGrid
    if (existing != null && cachedRoadSpatialRoadsRef === roads) return existing
    val built = RoadFrameSpatialGrid(roads)
    cachedRoadSpatialRoadsRef = roads
    cachedRoadSpatialGrid = built
    return built
}

private fun isPointClearOfAllRoads(
    px: Float,
    pz: Float,
    roads: List<ThrissurOsmRoad>,
    extraMarginM: Float = 3.25f
): Boolean {
    val spatialGrid = getOrBuildRoadSpatialGrid(roads)
    if (!spatialGrid.isClear(px, pz, extraMarginM)) return false

    val bellmouths = ThrissurRoadRepository.getJunctionBellmouths()
    for (i in 0 until bellmouths.size) {
        val bm = bellmouths[i]
        val bmMargin = bm.tCenter.halfWidthM + extraMarginM + 3.5f
        val bmMarginSq = bmMargin * bmMargin
        val subMargin = bmMargin * 0.85f
        val subMarginSq = subMargin * subMargin
        val dcx = px - bm.centerPos.x; val dcz = pz - bm.centerPos.z
        if (dcx * dcx + dcz * dcz < bmMarginSq) return false
        val dbx = px - bm.tBefore.pos.x; val dbz = pz - bm.tBefore.pos.z
        if (dbx * dbx + dbz * dbz < subMarginSq) return false
        val dax = px - bm.tAfter.pos.x; val daz = pz - bm.tAfter.pos.z
        if (dax * dax + daz * daz < subMarginSq) return false
    }
    return true
}

private fun isPointClearOfLandmarkBuildings(px: Float, pz: Float): Boolean {
    if (px in 68.0f..80.5f && pz in 12.0f..36.5f) return false
    if (px in 41.0f..66.5f && pz in 69.5f..85.0f) return false
    if (px in -79.0f..-63.5f && pz in 13.5f..41.0f) return false
    if (px in -54.5f..-19.5f && pz in 73.5f..88.5f) return false
    if (hypot(px - 3.1f, pz - 18.0f) < 62.0f) return false
    if (px in -32.0f..41.5f && pz in -208.5f..-138.0f) return false
    if (px in -128.0f..-60.5f && pz in -228.0f..-54.0f) return false
    if (px in -66.0f..-5.0f && pz in 199.0f..249.0f) return false
    if (px in 74.0f..136.0f && pz in 1510.0f..1955.0f) return false
    if (px in 118.0f..204.0f && pz in 3008.0f..3142.0f) return false
    if (px in -2.0f..78.0f && pz in 3158.0f..3318.0f) return false
    if (ShoranurRailwaySystem.isNearRailwayTrack(px, pz, 13.5f)) return false
    if (!Zone2Nh544KuthiranRenderer.isPointClearOfZone2Structures(px, pz)) return false
    if (!Zone3Nh544SouthRenderer.isPointClearOfZone3Structures(px, pz)) return false
    if (!Zone4KoleRailHeritageRenderer.isPointClearOfZone4Structures(px, pz)) return false
    if (!Zone5Nh66CoastalRenderer.isPointClearOfZone5Structures(px, pz)) return false
    if (!PuthanpallyBasilicaRenderer.isPointClearOfPuthanpally(px, pz)) return false
    if (!ThrissurFuelStationSystem.isPointClearOfFuelStations(px, pz, extraMargin = 5.0f)) return false
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
        for (delta in intArrayOf(0, -1, -2, -3, -4, 1, 2, -5, 3)) {
            val idx = (baseIdx + delta).coerceIn(2, frames.size - 3)
            val f = frames[idx]
            val colOffset = f.halfWidthM + 4.50f
            val leftCol = Vec3(f.pos.x - f.right.x * colOffset, 0f, f.pos.z - f.right.z * colOffset)
            val rightCol = Vec3(f.pos.x + f.right.x * colOffset, 0f, f.pos.z + f.right.z * colOffset)
            if (isPointClearOfAllRoads(leftCol.x, leftCol.z, roads, 3.6f) &&
                isPointClearOfAllRoads(rightCol.x, rightCol.z, roads, 3.6f) &&
                isPointClearOfLandmarkBuildings(leftCol.x, leftCol.z) &&
                isPointClearOfLandmarkBuildings(rightCol.x, rightCol.z)
            ) {
                return Triple(f, leftCol, rightCol)
            }
        }
        return null
    }

    val boardW = 4.6f
    for (extraShift in floatArrayOf(0f, 1.2f, 2.4f, 3.6f)) {
        for (delta in intArrayOf(0, -1, 1, -2, 2, -3, 3, -5, 5, -7, 7)) {
            val idx = (baseIdx + delta).coerceIn(2, frames.size - 3)
            val f = frames[idx]
            val innerDist = f.halfWidthM + 3.85f + extraShift
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
            if (isPointClearOfAllRoads(leftVec.x, leftVec.z, roads, 3.5f) &&
                isPointClearOfAllRoads(rightVec.x, rightVec.z, roads, 3.5f) &&
                isPointClearOfAllRoads(midVec.x, midVec.z, roads, 3.5f) &&
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

private fun addOrientedBeamFaces(
    out: MutableList<QuadFace3D>,
    p0x: Float,
    p0z: Float,
    p1x: Float,
    p1z: Float,
    yBot: Float,
    yTop: Float,
    halfThick: Float,
    topCol: Color,
    sideCol: Color,
    bias: Float = 0f
) {
    val dx = p1x - p0x
    val dz = p1z - p0z
    val len = hypot(dx, dz)
    if (len < 0.05f) return
    val nx = -dz / len * halfThick
    val nz = dx / len * halfThick
    val a0 = Vec3(p0x + nx, yBot, p0z + nz)
    val a1 = Vec3(p1x + nx, yBot, p1z + nz)
    val b1 = Vec3(p1x - nx, yBot, p1z - nz)
    val b0 = Vec3(p0x - nx, yBot, p0z - nz)
    val a0T = Vec3(p0x + nx, yTop, p0z + nz)
    val a1T = Vec3(p1x + nx, yTop, p1z + nz)
    val b1T = Vec3(p1x - nx, yTop, p1z - nz)
    val b0T = Vec3(p0x - nx, yTop, p0z - nz)
    out.add(QuadFace3D(a0, a1, a1T, a0T, sideCol, null, bias))
    out.add(QuadFace3D(b1, b0, b0T, b1T, sideCol, null, bias))
    out.add(QuadFace3D(a0T, a1T, b1T, b0T, topCol, null, bias))
    out.add(QuadFace3D(b0, b1, a1, a0, sideCol, null, bias))
    out.add(QuadFace3D(b0, a0, a0T, b0T, sideCol, null, bias))
    out.add(QuadFace3D(a1, b1, b1T, a1T, sideCol, null, bias))
}

private class CachedSignRenderItem(
    val midX: Float,
    val midZ: Float,
    val postCoords: List<Triple<Float, Float, Float>>, // (x, z, radius)
    val faces: List<QuadFace3D>
)

@Volatile
private var cachedSignRenderItems: List<CachedSignRenderItem>? = null

private fun getOrBuildSignRenderCache(roads: List<ThrissurOsmRoad>): List<CachedSignRenderItem> {
    cachedSignRenderItems?.let { return it }
    val built = ArrayList<CachedSignRenderItem>(ThrissurAuthenticSignSpecs.size)
    for ((specIdx, spec) in ThrissurAuthenticSignSpecs.withIndex()) {
        val placement = findSafeSignPlacement(spec, roads, built) ?: continue
        val (_, leftPos, rightPos) = placement
        val midX = (leftPos.x + rightPos.x) * 0.5f
        val midZ = (leftPos.z + rightPos.z) * 0.5f
        val itemFaces = ArrayList<QuadFace3D>(64)
        val posts = ArrayList<Triple<Float, Float, Float>>(2)

        val dx = rightPos.x - leftPos.x
        val dz = rightPos.z - leftPos.z
        val signSpec = createKeralaRoadSignSpec(spec, specIdx)

        if (spec.style == KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY) {
            for (colPos in arrayOf(leftPos, rightPos)) {
                // Reinforced concrete foundation pedestal + galvanized steel lattice gantry column
                addCylinderFrustumFaces(itemFaces, colPos.x, colPos.z, 0.05f, 0.65f, 0.38f, 0.34f, MatRoadMarkingYellow, sides = 8)
                addCylinderFrustumFaces(itemFaces, colPos.x, colPos.z, 0.65f, 8.35f, 0.24f, 0.22f, MatGraniteLight, sides = 8)
                posts.add(Triple(colPos.x, colPos.z, 0.42f))
            }
            // Lower & Upper Gantry Truss Beams (strictly below 6.15m and above 8.25m so no solid box ever covers the signboard!)
            addOrientedBeamFaces(
                itemFaces,
                leftPos.x, leftPos.z,
                rightPos.x, rightPos.z,
                5.92f, 6.15f, 0.18f,
                MatGranitePaving, MatGraniteDark
            )
            addOrientedBeamFaces(
                itemFaces,
                leftPos.x, leftPos.z,
                rightPos.x, rightPos.z,
                8.25f, 8.44f, 0.18f,
                MatGranitePaving, MatGraniteDark
            )

            val gLeft = Vec3(leftPos.x + dx * 0.10f, 0f, leftPos.z + dz * 0.10f)
            val gRight = Vec3(leftPos.x + dx * 0.90f, 0f, leftPos.z + dz * 0.90f)
            // Single Unobstructed Double-Sided Overhead Gantry Signboard (renders forward routes on front & reverse routes on back!)
            itemFaces.add(
                QuadFace3D(
                    v0 = Vec3(gLeft.x, 6.15f, gLeft.z),
                    v1 = Vec3(gRight.x, 6.15f, gRight.z),
                    v2 = Vec3(gRight.x, 8.25f, gRight.z),
                    v3 = Vec3(gLeft.x, 8.25f, gLeft.z),
                    baseColor = Color(0xFF057A55),
                    biasDist = -2.20f,
                    signboard = signSpec
                )
            )
        } else {
            val botY = 2.65f
            val topY = 5.65f
            // 1. Twin Cylindrical Galvanized Steel Support Pillars positioned CLEANLY UNDERNEATH the board (Y = 0.05m .. 2.65m)
            //    with Concrete Foundation Plinths & Black/Yellow Hazard Collars — never slicing through the sign face!
            for (t in floatArrayOf(0.20f, 0.80f)) {
                val px = leftPos.x + dx * t
                val pz = leftPos.z + dz * t
                addCylinderFrustumFaces(itemFaces, px, pz, 0.05f, 0.45f, 0.22f, 0.19f, MatRoadMarkingYellow, sides = 8)
                addCylinderFrustumFaces(itemFaces, px, pz, 0.45f, 1.45f, 0.13f, 0.12f, MatHazardBlack, sides = 8)
                addCylinderFrustumFaces(itemFaces, px, pz, 1.45f, botY, 0.12f, 0.11f, MatRoadMarkingWhite, sides = 8)
                posts.add(Triple(px, pz, 0.30f))
            }
            // 2. Lower Support Cross-Beam (Y = 2.48m .. 2.65m) & Upper Weather-Cap Rim (Y = 5.65m .. 5.74m)
            //    Strictly outside the [botY..topY] sign face so no solid box ever occludes the direction board!
            addOrientedBeamFaces(
                itemFaces,
                leftPos.x + dx * 0.04f, leftPos.z + dz * 0.04f,
                leftPos.x + dx * 0.96f, leftPos.z + dz * 0.96f,
                botY - 0.16f, botY, 0.12f,
                MatGraniteLight, MatGraniteDark
            )
            addOrientedBeamFaces(
                itemFaces,
                leftPos.x, leftPos.z,
                rightPos.x, rightPos.z,
                topY, topY + 0.09f, 0.11f,
                MatGraniteLight, MatGraniteDark
            )
            val bgColor = if (spec.style == KeralaSignStyle.ROUNDABOUT_DIAGRAM) Color(0xFF006D5B) else Color(0xFF046A4E)
            // 3. Single Unobstructed Double-Sided Direction Board (renders forward direction data on front & reverse direction data on back!)
            itemFaces.add(
                QuadFace3D(
                    v0 = Vec3(leftPos.x, botY, leftPos.z),
                    v1 = Vec3(rightPos.x, botY, rightPos.z),
                    v2 = Vec3(rightPos.x, topY, rightPos.z),
                    v3 = Vec3(leftPos.x, topY, leftPos.z),
                    baseColor = bgColor,
                    biasDist = -2.20f,
                    signboard = signSpec
                )
            )
        }
        built.add(CachedSignRenderItem(midX, midZ, posts, itemFaces))
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

internal fun getZone1RoadSignClearancePoints(roads: List<ThrissurOsmRoad>): List<Pair<Float, Float>> {
    if (roads.isEmpty()) return emptyList()
    val items = getOrBuildSignRenderCache(roads)
    val out = ArrayList<Pair<Float, Float>>(items.size)
    for (item in items) {
        if (item.midX in Zone1GDriveEnvironmentRenderer.ZONE1_MIN_X..Zone1GDriveEnvironmentRenderer.ZONE1_MAX_X &&
            item.midZ in Zone1GDriveEnvironmentRenderer.ZONE1_MIN_Z..Zone1GDriveEnvironmentRenderer.ZONE1_MAX_Z
        ) {
            out.add(Pair(item.midX, item.midZ))
        }
    }
    return out
}

internal fun getRoadSignPostColliders(roads: List<ThrissurOsmRoad>): List<Triple<Float, Float, Float>> {
    if (roads.isEmpty()) return emptyList()
    val items = getOrBuildSignRenderCache(roads)
    val out = ArrayList<Triple<Float, Float, Float>>(items.size * 2)
    for (item in items) {
        out.addAll(item.postCoords)
    }
    return out
}

internal fun getRoadsideTreeColliders(roads: List<ThrissurOsmRoad>): List<Triple<Float, Float, Float>> {
    if (roads.isEmpty()) return emptyList()
    val signs = getOrBuildSignRenderCache(roads)
    val clusters = cachedRoadsideClusters ?: buildRoadsideSceneryCache(roads, signs).also { cachedRoadsideClusters = it }
    val out = ArrayList<Triple<Float, Float, Float>>(clusters.size)
    for (c in clusters) {
        out.add(Triple(c.bx, c.bz, 0.56f))
    }
    return out
}


