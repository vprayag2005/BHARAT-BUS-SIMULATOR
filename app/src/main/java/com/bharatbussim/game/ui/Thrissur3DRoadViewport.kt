package com.bharatbussim.game.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.RoadJunctionBellmouth
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.viewmodel.SimUiState
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

// Authentic Kerala / IRC Highway & Sakthan Stand Material Palette (Strictly Zero Trees)
private val SkyZenith = Color(0xFF2B5B8C)
private val SkyMid = Color(0xFF6394BF)
private val SkyHorizonHaze = Color(0xFFC4D7E8)

private val GroundFarVerge = Color(0xFF5A6349)
private val GroundMidVerge = Color(0xFF4B543B)
private val GroundNearEarth = Color(0xFF424A34)

private val MatAsphaltBase = Color(0xFF23272D)
private val MatAsphaltCrown = Color(0xFF292E35)
private val MatJunctionAsphalt = Color(0xFF23272D)
private val MatTerminalYardConcrete = Color(0xFF2B3037)

private val MatShoulderLaterite = Color(0xFF684229)
private val MatCurbConcrete = Color(0xFFC2C6C0)
private val MatCurbYellow = Color(0xFFEAB308)
private val MatCurbBlack = Color(0xFF1F2328)

private val MatMedianConcrete = Color(0xFFC6C9C3)
private val MatRoadWhite = Color(0xFFF8FAFC)
private val MatRoadYellow = Color(0xFFFACC15)

// Sakthan Thampuran Bus Stand Architectural Palette
private val MatTerracottaPillar = Color(0xFFA83E28)
private val MatTerracottaDark = Color(0xFF7D2B19)
private val MatSakthanFasciaOrange = Color(0xFFC94A29)
private val MatRoofGalvanized = Color(0xFF64748B)
private val MatRoofUnderTruss = Color(0xFF334155)
private val MatPlatformTileLight = Color(0xFFCBD5E1)
private val MatPlatformTileDark = Color(0xFF94A3B8)
private val MatCreamPlaster = Color(0xFFEAE4D7)

internal data class CamSpaceVert(val cx: Float, val cy: Float, val cz: Float)

internal class CameraRig(
    val eye: Vec3,
    val forward: Vec3,
    val right: Vec3,
    val up: Vec3,
    val fovScale: Float,
    val screenCenterX: Float,
    val screenCenterY: Float,
    val screenW: Float,
    val screenH: Float
) {
    val nearZ = 0.45f
    private val rawVerts = Array(4) { CamSpaceVert(0f, 0f, 0f) }
    private val clippedVerts = ArrayList<CamSpaceVert>(8)
    private val polyRaw = ArrayList<CamSpaceVert>(256)
    private val polyClipped = ArrayList<CamSpaceVert>(256)

    fun toCamSpace(world: Vec3): CamSpaceVert {
        val dx = world.x - eye.x
        val dy = world.y - eye.y
        val dz = world.z - eye.z
        val cx = dx * right.x + dy * right.y + dz * right.z
        val cy = dx * up.x + dy * up.y + dz * up.z
        val cz = dx * forward.x + dy * forward.y + dz * forward.z
        return CamSpaceVert(cx, cy, cz)
    }

    fun projectPoint(world: Vec3): Offset? {
        val c = toCamSpace(world)
        if (c.cz < nearZ) return null
        val invZ = fovScale / c.cz
        val sx = screenCenterX + c.cx * invZ
        val sy = screenCenterY - c.cy * invZ
        if (sx < -2000f || sx > screenW + 2000f || sy < -2000f || sy > screenH + 2000f) return null
        return Offset(sx, sy)
    }

    fun projectSegmentClipped(a: Vec3, b: Vec3): Pair<Offset, Offset>? {
        var ca = toCamSpace(a)
        var cb = toCamSpace(b)
        if (ca.cz < nearZ && cb.cz < nearZ) return null
        if (ca.cz < nearZ) {
            val t = (nearZ - ca.cz) / (cb.cz - ca.cz)
            ca = CamSpaceVert(
                ca.cx + (cb.cx - ca.cx) * t,
                ca.cy + (cb.cy - ca.cy) * t,
                nearZ
            )
        } else if (cb.cz < nearZ) {
            val t = (nearZ - cb.cz) / (ca.cz - cb.cz)
            cb = CamSpaceVert(
                cb.cx + (ca.cx - cb.cx) * t,
                cb.cy + (ca.cy - cb.cy) * t,
                nearZ
            )
        }
        val invA = fovScale / ca.cz
        val invB = fovScale / cb.cz
        val oa = Offset(screenCenterX + ca.cx * invA, screenCenterY - ca.cy * invA)
        val ob = Offset(screenCenterX + cb.cx * invB, screenCenterY - cb.cy * invB)
        return Pair(oa, ob)
    }

    fun buildClippedQuadPath(
        path: Path,
        v0: Vec3,
        v1: Vec3,
        v2: Vec3,
        v3: Vec3
    ): Boolean {
        rawVerts[0] = toCamSpace(v0)
        rawVerts[1] = toCamSpace(v1)
        rawVerts[2] = toCamSpace(v2)
        rawVerts[3] = toCamSpace(v3)

        if (rawVerts[0].cz < nearZ && rawVerts[1].cz < nearZ && rawVerts[2].cz < nearZ && rawVerts[3].cz < nearZ) {
            return false
        }

        clippedVerts.clear()
        for (i in 0 until 4) {
            val curr = rawVerts[i]
            val prev = rawVerts[(i + 3) % 4]
            val currIn = curr.cz >= nearZ
            val prevIn = prev.cz >= nearZ

            if (currIn != prevIn) {
                val t = (nearZ - prev.cz) / (curr.cz - prev.cz)
                clippedVerts.add(
                    CamSpaceVert(
                        prev.cx + (curr.cx - prev.cx) * t,
                        prev.cy + (curr.cy - prev.cy) * t,
                        nearZ
                    )
                )
            }
            if (currIn) {
                clippedVerts.add(curr)
            }
        }

        if (clippedVerts.size < 3) return false

        path.reset()
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until clippedVerts.size) {
            val cv = clippedVerts[i]
            val invZ = fovScale / cv.cz
            val sx = screenCenterX + cv.cx * invZ
            val sy = screenCenterY - cv.cy * invZ
            if (sx < minX) minX = sx
            if (sx > maxX) maxX = sx
            if (sy < minY) minY = sy
            if (sy > maxY) maxY = sy
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }
        path.close()

        if (maxX < -200f || minX > screenW + 200f || maxY < -200f || minY > screenH + 200f) {
            return false
        }
        return true
    }

    /**
     * Builds a single seamless, crack-free polygon ribbon from a sequence of RoadFrames
     * between [startIdx..endIdx] for [offsetL, offsetR], clipped against the camera near plane.
     */
    fun buildContinuousRibbonPath(
        path: Path,
        frames: List<RoadFrame>,
        startIdx: Int,
        endIdx: Int,
        offsetL: Float,
        offsetR: Float,
        yHeight: Float
    ): Boolean = buildVariableRibbonPath(
        path = path,
        frames = frames,
        startIdx = startIdx,
        endIdx = endIdx,
        offsetLFn = { offsetL },
        offsetRFn = { offsetR },
        yHeight = yHeight
    )

    fun buildVariableRibbonPath(
        path: Path,
        frames: List<RoadFrame>,
        startIdx: Int,
        endIdx: Int,
        offsetLFn: (RoadFrame) -> Float,
        offsetRFn: (RoadFrame) -> Float,
        yHeight: Float
    ): Boolean {
        if (endIdx <= startIdx) return false
        polyRaw.clear()

        // Forward along left edge
        for (i in startIdx..endIdx) {
            val f = frames[i]
            val offL = offsetLFn(f)
            val w = Vec3(f.pos.x + f.right.x * offL, yHeight, f.pos.z + f.right.z * offL)
            polyRaw.add(toCamSpace(w))
        }
        // Backward along right edge
        for (i in endIdx downTo startIdx) {
            val f = frames[i]
            val offR = offsetRFn(f)
            val w = Vec3(f.pos.x + f.right.x * offR, yHeight, f.pos.z + f.right.z * offR)
            polyRaw.add(toCamSpace(w))
        }

        val n = polyRaw.size
        var anyIn = false
        for (i in 0 until n) {
            if (polyRaw[i].cz >= nearZ) {
                anyIn = true
                break
            }
        }
        if (!anyIn) return false

        polyClipped.clear()
        for (i in 0 until n) {
            val curr = polyRaw[i]
            val prev = polyRaw[(i + n - 1) % n]
            val currIn = curr.cz >= nearZ
            val prevIn = prev.cz >= nearZ
            if (currIn != prevIn) {
                val t = (nearZ - prev.cz) / (curr.cz - prev.cz)
                polyClipped.add(
                    CamSpaceVert(
                        prev.cx + (curr.cx - prev.cx) * t,
                        prev.cy + (curr.cy - prev.cy) * t,
                        nearZ
                    )
                )
            }
            if (currIn) {
                polyClipped.add(curr)
            }
        }

        if (polyClipped.size < 3) return false

        path.reset()
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until polyClipped.size) {
            val cv = polyClipped[i]
            val invZ = fovScale / cv.cz
            val sx = screenCenterX + cv.cx * invZ
            val sy = screenCenterY - cv.cy * invZ
            if (sx < minX) minX = sx
            if (sx > maxX) maxX = sx
            if (sy < minY) minY = sy
            if (sy > maxY) maxY = sy
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }
        path.close()

        if (maxX < -300f || minX > screenW + 300f || maxY < -300f || minY > screenH + 300f) {
            return false
        }
        return true
    }

    fun buildWorldPolygonPath(
        path: Path,
        worldPts: List<Vec3>
    ): Boolean {
        if (worldPts.size < 3) return false
        polyRaw.clear()
        for (i in worldPts.indices) {
            polyRaw.add(toCamSpace(worldPts[i]))
        }

        val n = polyRaw.size
        var anyIn = false
        for (i in 0 until n) {
            if (polyRaw[i].cz >= nearZ) {
                anyIn = true
                break
            }
        }
        if (!anyIn) return false

        polyClipped.clear()
        for (i in 0 until n) {
            val curr = polyRaw[i]
            val prev = polyRaw[(i + n - 1) % n]
            val currIn = curr.cz >= nearZ
            val prevIn = prev.cz >= nearZ
            if (currIn != prevIn) {
                val t = (nearZ - prev.cz) / (curr.cz - prev.cz)
                polyClipped.add(
                    CamSpaceVert(
                        prev.cx + (curr.cx - prev.cx) * t,
                        prev.cy + (curr.cy - prev.cy) * t,
                        nearZ
                    )
                )
            }
            if (currIn) {
                polyClipped.add(curr)
            }
        }

        if (polyClipped.size < 3) return false

        path.reset()
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until polyClipped.size) {
            val cv = polyClipped[i]
            val invZ = fovScale / cv.cz
            val sx = screenCenterX + cv.cx * invZ
            val sy = screenCenterY - cv.cy * invZ
            if (sx < minX) minX = sx
            if (sx > maxX) maxX = sx
            if (sy < minY) minY = sy
            if (sy > maxY) maxY = sy
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }
        path.close()

        if (maxX < -300f || minX > screenW + 300f || maxY < -300f || minY > screenH + 300f) {
            return false
        }
        return true
    }
}

private data class ContiguousRoadSpan(
    val road: ThrissurOsmRoad,
    val startIdx: Int,
    val endIdx: Int,
    val avgDistSq: Float
)

internal data class QuadFace3D(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3,
    val color: Color,
    val strokeColor: Color? = Color(0x28000000),
    val biasDist: Float = 0f
)

@Composable
fun Thrissur3DRoadViewport(
    state: SimUiState,
    onOrbitDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val titlePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val subPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("thrissur_3d_viewport")
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onOrbitDrag(dragAmount.x * 0.22f, dragAmount.y * 0.14f)
                }
            }
    ) {
        val w = size.width
        val h = size.height

        // 1. Compute Camera Pose (No Rear View)
        val baseYaw = state.busHeadingRad +
            Math.toRadians(state.cameraOrbitYawDeg.toDouble()).toFloat()

        val (camEye, pitchDeg) = when (state.cameraMode) {
            CameraViewMode.Chase -> {
                val backDist = 13.8f
                val eyeX = state.busPos.x - sin(baseYaw) * backDist
                val eyeZ = state.busPos.z - cos(baseYaw) * backDist
                Pair(Vec3(eyeX, 4.15f, eyeZ), 11.5f + state.cameraOrbitPitchDeg)
            }
            CameraViewMode.Cockpit -> {
                val fwdDist = 4.65f
                val rightOff = 0.72f
                val sYaw = sin(state.busHeadingRad)
                val cYaw = cos(state.busHeadingRad)
                val eyeX = state.busPos.x + sYaw * fwdDist + cYaw * rightOff
                val eyeZ = state.busPos.z + cYaw * fwdDist - sYaw * rightOff
                Pair(Vec3(eyeX, 2.32f, eyeZ), 6.0f + state.cameraOrbitPitchDeg)
            }
            CameraViewMode.Hood -> {
                val fwdDist = 6.1f
                val eyeX = state.busPos.x + sin(state.busHeadingRad) * fwdDist
                val eyeZ = state.busPos.z + cos(state.busHeadingRad) * fwdDist
                Pair(Vec3(eyeX, 1.85f, eyeZ), 5.0f + state.cameraOrbitPitchDeg)
            }
            CameraViewMode.TopDown -> {
                val backDist = 26f
                val eyeX = state.busPos.x - sin(baseYaw) * backDist
                val eyeZ = state.busPos.z - cos(baseYaw) * backDist
                Pair(Vec3(eyeX, 25.0f, eyeZ), 36f + state.cameraOrbitPitchDeg)
            }
        }

        val pitchRad = Math.toRadians(pitchDeg.coerceIn(1f, 65f).toDouble()).toFloat()
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)
        val sinY = sin(baseYaw)
        val cosY = cos(baseYaw)

        val fwd = Vec3(sinY * cosP, -sinP, cosY * cosP)
        val right = Vec3(cosY, 0f, -sinY)
        val up = Vec3(sinY * sinP, cosP, cosY * sinP)
        val fovScale = (h * 0.5f) / tan(Math.toRadians(28.0)).toFloat()

        // Exact mathematical vanishing horizon Y of the y=0 ground plane
        val horizonY = (h * 0.5f - tan(pitchRad) * fovScale).coerceIn(0f, h * 0.85f)

        val rig = CameraRig(
            eye = camEye,
            forward = fwd,
            right = right,
            up = up,
            fovScale = fovScale,
            screenCenterX = w * 0.5f,
            screenCenterY = h * 0.5f,
            screenW = w,
            screenH = h
        )

        // 2. Tropical Kerala Sky & Atmospheric Ground Plane (Strictly Zero Trees)
        if (horizonY > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to SkyZenith,
                    0.62f to SkyMid,
                    1.0f to SkyHorizonHaze,
                    startY = 0f,
                    endY = horizonY
                ),
                topLeft = Offset.Zero,
                size = Size(w, horizonY + 1f)
            )
        }
        if (horizonY < h) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to SkyHorizonHaze,
                    0.04f to GroundFarVerge,
                    0.28f to GroundMidVerge,
                    1.0f to GroundNearEarth,
                    startY = horizonY,
                    endY = h
                ),
                topLeft = Offset(0f, horizonY),
                size = Size(w, h - horizonY)
            )
        }

        // 3. Collect Contiguous Visible Road Spans (up to 16 frames per span for seamless crack-free ribbon polygons)
        val maxDrawDistSq = 440f * 440f
        val spans = ArrayList<ContiguousRoadSpan>(128)

        for (road in state.roads) {
            val frames = road.frames
            if (frames.size < 2) continue
            val chunkSize = 14
            var i = 0
            while (i < frames.size - 1) {
                var sIdx = i
                var eIdx = min(i + chunkSize, frames.size - 1)

                // Trim Sakthan Stand bay-interior frames south of Z=-165.0m so the departure road starts cleanly at the North Concourse edge (Z=-165.0m) and runs continuously through the Right Exit Gate (Z=-140.0m)
                if (road.id == "ROAD_SAKTHAN_TO_SWARAJ") {
                    while (sIdx < eIdx && frames[sIdx].pos.z < -165.5f) {
                        sIdx++
                    }
                    if (sIdx >= eIdx) {
                        i += chunkSize
                        continue
                    }
                }

                val midF = frames[(sIdx + eIdx) / 2]
                val dx = midF.pos.x - camEye.x
                val dz = midF.pos.z - camEye.z
                val distSq = dx * dx + dz * dz
                if (distSq <= maxDrawDistSq) {
                    spans.add(ContiguousRoadSpan(road, sIdx, eIdx, distSq))
                }
                i += chunkSize
            }
        }

        spans.sortByDescending { it.avgDistSq }
        val sharedPath = Path()

        // PASS 1: 20-Bay Sakthan Terminal Concrete Yard + 5 Landmark Ground Plazas/Beaches + Continuous Laterite Shoulders & Flared Junction Shoulders
        drawSakthan20BayTerminalYard(rig, sharedPath, camEye)
        drawThrissurLandmarkGroundPlazas(rig, sharedPath, camEye, state.roads)
        drawJunctionShoulders(rig, sharedPath, camEye)
        for (span in spans) {
            drawContinuousShouldersAndCurbs(rig, sharedPath, span)
        }

        // PASS 2: Seamless Bezier Junction Plazas & Continuous Crack-Free Asphalt Carriageway Ribbons
        drawJunctionPlazas(rig, sharedPath, camEye)
        for (span in spans) {
            drawContinuousAsphaltRibbon(rig, sharedPath, span)
        }

        // PASS 3: Anti-Aliased Continuous IRC White Edge Lines, Curved Junction Edge Fillets, Yellow Centerlines, Dashed Lane Dividers & Medians
        for (span in spans) {
            drawContinuousRoadMarkings(rig, sharedPath, span)
        }
        drawJunctionEdgeFilletMarkings(rig, camEye)

        // PASS 4: Realistic 3D Landmarks (Sakthan 20-Bay Stand, Swaraj Round Vadakkumnathan Temple, Guruvayur Temple & Stand, Chavakkad Beach, Triprayar Beach)
        drawSakthan20BayTerminalStructuresAndBanners(
            rig = rig,
            path = sharedPath,
            camEye = camEye,
            titlePaint = titlePaint,
            subPaint = subPaint
        )
        drawThrissurLandmark3DStructures(
            rig = rig,
            path = sharedPath,
            camEye = camEye,
            roads = state.roads,
            titlePaint = titlePaint,
            subPaint = subPaint
        )

        // PASS 5: Render Player's CGTrader Hino RM 2 (12m Rear-Engine AC Luxury Coach) in 3D
        // Source: https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus
        // Creator: asrafullislamsiam | License: CGTrader Royalty Free License (No AI)
        if (state.cameraMode == CameraViewMode.Chase || state.cameraMode == CameraViewMode.TopDown) {
            drawHinoRm2Bus3D(rig, sharedPath, state, titlePaint, subPaint)
        } else if (state.cameraMode == CameraViewMode.Cockpit) {
            drawHinoRm2Cockpit3D(state)
        }
    }
}

/**
 * Evaluates the two concentric Quadratic Bezier corner curves of a [RoadJunctionBellmouth]
 * at a given lateral expansion [extraM] (+2.38m for outer laterite shoulder, +0.28m for dark asphalt lip,
 * 0.0m for hot-mix asphalt carriageway edge, -0.38m for the solid white IRC edge line).
 */
private fun computeBellmouthCornerCurves(
    bm: RoadJunctionBellmouth,
    extraM: Float,
    yHeight: Float,
    steps: Int = 6
): Pair<List<Vec3>, List<Vec3>> {
    val tBefore = bm.tBefore
    val tCenter = bm.tCenter
    val tAfter = bm.tAfter
    val bTip = bm.bTip
    val bThroat = bm.bThroat

    val offTBefore = bm.trunkBeforeSideSign * (tBefore.halfWidthM + extraM)
    val offTAfter = bm.trunkAfterSideSign * (tAfter.halfWidthM + extraM)
    val offBBefore = bm.bBeforeSign * (bThroat.halfWidthM + extraM)
    val offBAfter = bm.bAfterSign * (bThroat.halfWidthM + extraM)

    val pTBefore = Vec3(
        tBefore.pos.x + tBefore.right.x * offTBefore,
        yHeight,
        tBefore.pos.z + tBefore.right.z * offTBefore
    )
    val pTAfter = Vec3(
        tAfter.pos.x + tAfter.right.x * offTAfter,
        yHeight,
        tAfter.pos.z + tAfter.right.z * offTAfter
    )
    val pBBefore = Vec3(
        bThroat.pos.x + bThroat.right.x * offBBefore,
        yHeight,
        bThroat.pos.z + bThroat.right.z * offBBefore
    )
    val pBAfter = Vec3(
        bThroat.pos.x + bThroat.right.x * offBAfter,
        yHeight,
        bThroat.pos.z + bThroat.right.z * offBAfter
    )

    val dirT1 = (tCenter.pos - tBefore.pos).normalizedXZ()
    val dirT2 = (tCenter.pos - tAfter.pos).normalizedXZ()
    val dirB = (bTip.pos - bThroat.pos).normalizedXZ()

    val d1 = (pBBefore - pTBefore).lengthXZ()
    val ctrl1 = Vec3(
        ((pTBefore.x + dirT1.x * d1 * 0.46f) + (pBBefore.x + dirB.x * d1 * 0.46f)) * 0.5f,
        yHeight,
        ((pTBefore.z + dirT1.z * d1 * 0.46f) + (pBBefore.z + dirB.z * d1 * 0.46f)) * 0.5f
    )

    val d2 = (pTAfter - pBAfter).lengthXZ()
    val ctrl2 = Vec3(
        ((pBAfter.x + dirB.x * d2 * 0.46f) + (pTAfter.x + dirT2.x * d2 * 0.46f)) * 0.5f,
        yHeight,
        ((pBAfter.z + dirB.z * d2 * 0.46f) + (pTAfter.z + dirT2.z * d2 * 0.46f)) * 0.5f
    )

    val curve1 = ArrayList<Vec3>(steps + 1)
    val curve2 = ArrayList<Vec3>(steps + 1)
    for (s in 0..steps) {
        val t = s.toFloat() / steps.toFloat()
        val u = 1f - t
        curve1.add(
            Vec3(
                u * u * pTBefore.x + 2f * u * t * ctrl1.x + t * t * pBBefore.x,
                yHeight,
                u * u * pTBefore.z + 2f * u * t * ctrl1.z + t * t * pBBefore.z
            )
        )
        curve2.add(
            Vec3(
                u * u * pBAfter.x + 2f * u * t * ctrl2.x + t * t * pTAfter.x,
                yHeight,
                u * u * pBAfter.z + 2f * u * t * ctrl2.z + t * t * pTAfter.z
            )
        )
    }
    return Pair(curve1, curve2)
}

/**
 * Builds the closed 3D polygon for a [RoadJunctionBellmouth] at lateral offset [extraM].
 */
private fun buildBellmouthPolygonPoints(
    bm: RoadJunctionBellmouth,
    extraM: Float,
    yHeight: Float
): List<Vec3> {
    val (curve1, curve2) = computeBellmouthCornerCurves(bm, extraM, yHeight, steps = 5)
    val pts = ArrayList<Vec3>(16)
    pts.add(Vec3(bm.tBefore.pos.x, yHeight, bm.tBefore.pos.z))
    pts.add(Vec3(bm.tCenter.pos.x, yHeight, bm.tCenter.pos.z))
    pts.add(Vec3(bm.tAfter.pos.x, yHeight, bm.tAfter.pos.z))
    for (i in curve2.indices.reversed()) {
        pts.add(curve2[i])
    }
    for (i in curve1.indices.reversed()) {
        pts.add(curve1[i])
    }
    return pts
}

/**
 * PASS 1: Draws continuous laterite shoulders and dark-asphalt edge bevels along every road span.
 * Because Pass 2 renders all road and junction asphalt ON TOP OF Pass 1, shoulders are never turned
 * off at road-to-road joints — guaranteeing zero shoulder gaps at every intersection.
 */
private fun DrawScope.drawContinuousShouldersAndCurbs(
    rig: CameraRig,
    path: Path,
    span: ContiguousRoadSpan
) {
    val road = span.road
    val frames = road.frames
    val bevelW = 0.28f
    val shoulderW = 2.10f
    val yGround = 0.06f

    fun drawUncoveredSideRibbon(
        offsetInnerFn: (RoadFrame) -> Float,
        offsetOuterFn: (RoadFrame) -> Float,
        color: Color,
        isSuppressed: (RoadFrame) -> Boolean
    ) {
        var runStart = span.startIdx
        while (runStart < span.endIdx) {
            while (runStart < span.endIdx && isSuppressed(frames[runStart])) {
                runStart++
            }
            if (runStart >= span.endIdx) break
            var runEnd = runStart + 1
            while (runEnd < span.endIdx && !isSuppressed(frames[runEnd])) {
                runEnd++
            }
            val drawStart = if (runStart > span.startIdx) runStart - 1 else runStart
            if (rig.buildVariableRibbonPath(path, frames, drawStart, runEnd, offsetInnerFn, offsetOuterFn, yGround)) {
                drawPath(path, color)
            }
            runStart = runEnd
        }
    }

    val isSakthanGateRoad = road.id == "ROAD_SAKTHAN_TO_SWARAJ" || road.id == "ROAD_SAKTHAN_STAND_ENTRY"
    val isNh544 = road.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA"
    fun insideTerminalOrTollApron(f: RoadFrame): Boolean =
        (isSakthanGateRoad && f.pos.z < -139.6f) ||
            (isNh544 && f.pos.z in -1856.0f..-1756.0f)

    // 1. Left & Right Earthen Laterite Shoulders (continuous through all road joints; suppressed only inside concrete terminal/toll aprons)
    drawUncoveredSideRibbon(
        offsetInnerFn = { -it.halfWidthM - bevelW - shoulderW },
        offsetOuterFn = { -it.halfWidthM - bevelW },
        color = MatShoulderLaterite
    ) {
        insideTerminalOrTollApron(it)
    }
    drawUncoveredSideRibbon(
        offsetInnerFn = { it.halfWidthM + bevelW },
        offsetOuterFn = { it.halfWidthM + bevelW + shoulderW },
        color = MatShoulderLaterite
    ) {
        insideTerminalOrTollApron(it)
    }

    // 2. Subtle Dark-Asphalt Edge Bevel
    val matAsphaltEdgeLip = Color(0xFF1F2630)
    drawUncoveredSideRibbon(
        offsetInnerFn = { -it.halfWidthM - bevelW },
        offsetOuterFn = { -it.halfWidthM },
        color = matAsphaltEdgeLip
    ) { insideTerminalOrTollApron(it) }
    drawUncoveredSideRibbon(
        offsetInnerFn = { it.halfWidthM },
        offsetOuterFn = { it.halfWidthM + bevelW },
        color = matAsphaltEdgeLip
    ) { insideTerminalOrTollApron(it) }

    // 3. Raised Urban Concrete Footpaths & Kerb Stones on Thrissur Town Corridors (never crossing junction mouths)
    val hasUrbanFootpath = road.id == "ROAD_SWARAJ_ROUND" ||
        road.id == "ROAD_SAKTHAN_TO_SWARAJ" ||
        road.id == "ROAD_SWARAJ_TO_VADAKKE" ||
        (road.id == "ROAD_SWARAJ_TO_MANNUTHY" && frames[span.startIdx].cumulativeDist < 320f) ||
        (road.id == "ROAD_SWARAJ_TO_WEST_FORT" && frames[span.startIdx].cumulativeDist < 260f)

    if (hasUrbanFootpath) {
        val fpInnerOff = bevelW + shoulderW
        val fpWidth = 1.85f
        val matFootpathSlab = Color(0xFF94A3B8)
        drawUncoveredSideRibbon(
            offsetInnerFn = { -it.halfWidthM - fpInnerOff - fpWidth },
            offsetOuterFn = { -it.halfWidthM - fpInnerOff },
            color = matFootpathSlab
        ) { it.leftEdgeCovered || insideTerminalOrTollApron(it) }
        drawUncoveredSideRibbon(
            offsetInnerFn = { it.halfWidthM + fpInnerOff },
            offsetOuterFn = { it.halfWidthM + fpInnerOff + fpWidth },
            color = matFootpathSlab
        ) { it.rightEdgeCovered || insideTerminalOrTollApron(it) }
    }
}

/**
 * PASS 1: Draws flared Quadratic Bezier laterite shoulder and dark-lip bellmouths around every T-junction
 * and Roundabout branch joint so shoulders sweep smoothly around every intersection corner.
 */
private fun DrawScope.drawJunctionShoulders(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    val bevelW = 0.28f
    val shoulderW = 2.10f
    val matAsphaltEdgeLip = Color(0xFF1F2630)
    val yGround = 0.06f

    for (bm in ThrissurRoadRepository.getJunctionBellmouths()) {
        if (hypot(bm.centerPos.x - camEye.x, bm.centerPos.z - camEye.z) > 400f) continue
        val shoulderPts = buildBellmouthPolygonPoints(bm, bevelW + shoulderW, yGround)
        if (rig.buildWorldPolygonPath(path, shoulderPts)) {
            drawPath(path, MatShoulderLaterite)
        }
        val lipPts = buildBellmouthPolygonPoints(bm, bevelW, yGround)
        if (rig.buildWorldPolygonPath(path, lipPts)) {
            drawPath(path, matAsphaltEdgeLip)
        }
    }
}

/**
 * PASS 2: Draws a single continuous, seamless asphalt carriageway ribbon across [span.startIdx..span.endIdx]
 * using per-frame curve-widened [RoadFrame.halfWidthM].
 */
private fun DrawScope.drawContinuousAsphaltRibbon(
    rig: CameraRig,
    path: Path,
    span: ContiguousRoadSpan
) {
    val road = span.road
    val frames = road.frames
    val yRoad = 0.06f

    // Main seamless hot-mix asphalt carriageway (automatically widened on curves & welded at continuation joints!)
    if (rig.buildVariableRibbonPath(path, frames, span.startIdx, span.endIdx, { -it.halfWidthM }, { it.halfWidthM }, yRoad)) {
        drawPath(path, MatAsphaltBase)
    }

    // Subtle continuous center crown band
    if (rig.buildVariableRibbonPath(path, frames, span.startIdx, span.endIdx, { -it.halfWidthM * 0.25f }, { it.halfWidthM * 0.25f }, yRoad)) {
        drawPath(path, MatAsphaltCrown)
    }
}

/**
 * PASS 2: Draws Trunk-Aligned Quadratic Bezier Hot-Mix Asphalt Bellmouths at every T-junction
 * and Roundabout branch joint, matching the exact anchor vertices (tBefore, tAfter, bThroat)
 * with 0.000m gaps.
 */
private fun DrawScope.drawJunctionPlazas(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    val yRoad = 0.06f
    for (bm in ThrissurRoadRepository.getJunctionBellmouths()) {
        if (hypot(bm.centerPos.x - camEye.x, bm.centerPos.z - camEye.z) > 400f) continue
        val asphaltPts = buildBellmouthPolygonPoints(bm, 0f, yRoad)
        if (rig.buildWorldPolygonPath(path, asphaltPts)) {
            drawPath(path, MatAsphaltBase)
        }
    }
}

/**
 * PASS 3: Draws razor-sharp, anti-aliased continuous IRC road markings (Single Crisp White Edge Lines,
 * Yellow Centerlines, Dashed Lane Dividers, and Medians) following per-frame curve widening.
 */
private fun DrawScope.drawContinuousRoadMarkings(
    rig: CameraRig,
    path: Path,
    span: ContiguousRoadSpan
) {
    val road = span.road
    val frames = road.frames
    val yMark = 0.062f
    val sIdx = span.startIdx
    val eIdx = span.endIdx
    if (eIdx <= sIdx) return

    fun drawSegment3D(w0: Vec3, w1: Vec3, widthM: Float, color: Color) {
        val seg = rig.projectSegmentClipped(w0, w1) ?: return
        val c0 = rig.toCamSpace(w0)
        val c1 = rig.toCamSpace(w1)
        val avgCz = max(0.8f, (c0.cz + c1.cz) * 0.5f)
        val strokePx = (widthM * rig.fovScale / avgCz).coerceIn(1.8f, 14f)
        drawLine(
            color = color,
            start = seg.first,
            end = seg.second,
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }

    fun drawPerspectiveLine(
        lateralOffsetFn: (RoadFrame) -> Float,
        widthM: Float,
        color: Color,
        dashed: Boolean = false,
        clipPartialTransition: Boolean = false,
        coveredFn: (RoadFrame) -> Boolean
    ) {
        for (i in sIdx until eIdx) {
            val f0 = frames[i]
            val f1 = frames[i + 1]
            val cov0 = coveredFn(f0)
            val cov1 = coveredFn(f1)
            if (cov0 && cov1) continue
            if (dashed) {
                val period = 8.5f
                if ((f0.cumulativeDist % period) > 3.5f) continue
            }
            val off0 = lateralOffsetFn(f0)
            val off1 = lateralOffsetFn(f1)
            val w0 = Vec3(f0.pos.x + f0.right.x * off0, yMark, f0.pos.z + f0.right.z * off0)
            val w1 = Vec3(f1.pos.x + f1.right.x * off1, yMark, f1.pos.z + f1.right.z * off1)

            if (!cov0 && !cov1) {
                drawSegment3D(w0, w1, widthM, color)
            } else if (clipPartialTransition && !dashed) {
                // Extend centerline right up to the outer edge of the intersecting trunk road
                val mid = Vec3((w0.x + w1.x) * 0.5f, yMark, (w0.z + w1.z) * 0.5f)
                if (!cov0) {
                    drawSegment3D(w0, mid, widthM, color)
                } else {
                    drawSegment3D(mid, w1, widthM, color)
                }
            }
        }
    }

    // 1. Single Crisp IRC Solid White Edge Lines (stopping at exact tBefore / tAfter / bThroat anchor frames where Bezier fillets connect!)
    drawPerspectiveLine(
        lateralOffsetFn = { -it.halfWidthM + 0.38f },
        widthM = 0.16f,
        color = MatRoadWhite,
        dashed = false,
        clipPartialTransition = false
    ) { it.leftEdgeCovered }

    drawPerspectiveLine(
        lateralOffsetFn = { it.halfWidthM - 0.38f },
        widthM = 0.16f,
        color = MatRoadWhite,
        dashed = false,
        clipPartialTransition = false
    ) { it.rightEdgeCovered }

    // 2. Centerline / Divided Highway Median & Dashed Lane Lines (2-way 1+1, 4-way 2+2, 6-way 3+3)
    if (road.divided) {
        val medHw = 0.62f
        var runStart = sIdx
        while (runStart < eIdx) {
            while (runStart < eIdx && frames[runStart].centerCovered) runStart++
            if (runStart >= eIdx) break
            var runEnd = runStart + 1
            while (runEnd < eIdx && !frames[runEnd].centerCovered) runEnd++
            if (rig.buildContinuousRibbonPath(path, frames, runStart, runEnd, -medHw, medHw, yMark)) {
                drawPath(path, MatMedianConcrete)
            }
            runStart = runEnd
        }
        drawPerspectiveLine({ -medHw - 0.20f }, 0.15f, MatRoadYellow, dashed = false, clipPartialTransition = true) { it.centerCovered }
        drawPerspectiveLine({ medHw + 0.20f }, 0.15f, MatRoadYellow, dashed = false, clipPartialTransition = true) { it.centerCovered }

        if (road.lanes >= 6) {
            // 6-Way (3+3) Divided National Highway (NH 544): 2 Dashed White Lane Dividers per Carriageway = 3 Lanes Each Way!
            drawPerspectiveLine({ -(medHw + (it.halfWidthM - medHw) * 0.333f) }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
            drawPerspectiveLine({ -(medHw + (it.halfWidthM - medHw) * 0.667f) }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
            drawPerspectiveLine({ medHw + (it.halfWidthM - medHw) * 0.333f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
            drawPerspectiveLine({ medHw + (it.halfWidthM - medHw) * 0.667f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
        } else {
            // 4-Way (2+2) Divided Highway: 1 Dashed White Lane Divider per Carriageway = 2 Lanes Each Way
            drawPerspectiveLine({ -(it.halfWidthM + medHw) * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
            drawPerspectiveLine({ (it.halfWidthM + medHw) * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
        }
    } else if (road.lanes >= 4) {
        // 4-Way (2+2) Undivided Arterial / Swaraj Round: IRC Double Yellow Centerline + Dashed Lane Dividers
        drawPerspectiveLine({ -0.15f }, 0.13f, MatRoadYellow, dashed = false, clipPartialTransition = true) { it.centerCovered }
        drawPerspectiveLine({ 0.15f }, 0.13f, MatRoadYellow, dashed = false, clipPartialTransition = true) { it.centerCovered }
        drawPerspectiveLine({ -it.halfWidthM * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
        drawPerspectiveLine({ it.halfWidthM * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
    } else {
        // 2-Way (1+1) Road: Crisp Single Yellow Centerline
        drawPerspectiveLine({ 0f }, 0.15f, MatRoadYellow, dashed = false, clipPartialTransition = true) { it.centerCovered }
    }
}

/**
 * PASS 3: Draws the two smooth Quadratic Bezier White Edge Fillet Lines around every T-junction
 * and Roundabout branch joint, connecting the trunk road's white edge line at [tBefore] and [tAfter]
 * directly into the branch road's left and right white edge lines at [bThroat] with 0.000m gaps.
 */
private fun DrawScope.drawJunctionEdgeFilletMarkings(
    rig: CameraRig,
    camEye: Vec3
) {
    val yMark = 0.062f
    val widthM = 0.16f

    fun drawCurve(pts: List<Vec3>) {
        for (i in 0 until pts.size - 1) {
            val w0 = pts[i]
            val w1 = pts[i + 1]
            val seg = rig.projectSegmentClipped(w0, w1) ?: continue
            val c0 = rig.toCamSpace(w0)
            val c1 = rig.toCamSpace(w1)
            val avgCz = max(0.8f, (c0.cz + c1.cz) * 0.5f)
            val strokePx = (widthM * rig.fovScale / avgCz).coerceIn(1.8f, 14f)
            drawLine(
                color = MatRoadWhite,
                start = seg.first,
                end = seg.second,
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
        }
    }

    for (bm in ThrissurRoadRepository.getJunctionBellmouths()) {
        if (hypot(bm.centerPos.x - camEye.x, bm.centerPos.z - camEye.z) > 400f) continue
        val (curve1, curve2) = computeBellmouthCornerCurves(bm, extraM = -0.38f, yHeight = yMark, steps = 6)
        drawCurve(curve1)
        drawCurve(curve2)
    }
}

/**
 * Renders the expansive 20-Bus Capacity Sakthan Thampuran Bus Stand Concrete Terminal Yard
 * (X = -24m to +54m, Z = -222m to -140m) with 20 painted yellow bus parking slots:
 * - North Departure Row (Bays 01 to 10 at Z = -183.5m to -167.0m, with Bay 07 at X=25.0m for Player Bus)
 * - South Intercity Row (Bays 11 to 20 at Z = -214.5m to -198.0m)
 */
private fun DrawScope.drawSakthan20BayTerminalYard(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    if (hypot(camEye.x - 16f, camEye.z - (-178f)) > 320f) return

    val yYard = 0.058f
    val yardMinX = -22.0f
    val yardMaxX = 48.0f
    val yardMinZ = -220.0f
    val yardMaxZ = -140.0f

    // 1. Expansive Terminal Concrete Apron aligned with Perimeter Compound Wall (X = -22.0m..48.0m, Z = -220.0m..-140.0m)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(yardMinX, yYard, yardMinZ),
            Vec3(yardMaxX, yYard, yardMinZ),
            Vec3(yardMaxX, yYard, yardMaxZ),
            Vec3(yardMinX, yYard, yardMaxZ)
        )
    ) {
        drawPath(path, MatTerminalYardConcrete)
    }

    // 2. 20 Bus Parking Slots:
    // Row A (North Concourse: 10 Bays across X = -18.2m to +46.6m, Z = -183.5m to -167.0m)
    // Row B (South Concourse: 10 Bays across X = -18.2m to +46.6m, Z = -214.5m to -198.0m)
    // Note: Bay at X = 25.0f (Row A, index 6) is the Player's Starting Departure Bay!
    val bayRowZs = arrayOf(
        Pair(-183.5f, -167.0f), // Bays 01–10 (North Departure Concourse)
        Pair(-214.5f, -198.0f)  // Bays 11–20 (South Intercity Concourse)
    )
    val bayHalfW = 2.05f

    for ((z0, z1) in bayRowZs) {
        for (bIdx in 0 until 10) {
            val bx = -18.2f + bIdx * 7.2f
            val leftLine = rig.projectSegmentClipped(
                Vec3(bx - bayHalfW, 0.062f, z0),
                Vec3(bx - bayHalfW, 0.062f, z1)
            )
            if (leftLine != null) {
                drawLine(MatRoadYellow, leftLine.first, leftLine.second, strokeWidth = 2.4f)
            }
            val rightLine = rig.projectSegmentClipped(
                Vec3(bx + bayHalfW, 0.062f, z0),
                Vec3(bx + bayHalfW, 0.062f, z1)
            )
            if (rightLine != null) {
                drawLine(MatRoadYellow, rightLine.first, rightLine.second, strokeWidth = 2.4f)
            }
            val frontStop = rig.projectSegmentClipped(
                Vec3(bx - bayHalfW, 0.062f, z1),
                Vec3(bx + bayHalfW, 0.062f, z1)
            )
            if (frontStop != null) {
                drawLine(MatRoadYellow, frontStop.first, frontStop.second, strokeWidth = 3.0f)
            }
        }
    }
}

/**
 * Renders the 20-Bay Sakthan Thampuran Bus Stand 3D buildings, passenger boarding islands,
 * terracotta pillars, high corrugated canopies, 12+ parked Kerala buses across the 20 bays,
 * and 3D perspective banners (Main Terminal Banner, Entry Gate Banner, Exit Gate Banner).
 */
private fun DrawScope.drawSakthan20BayTerminalStructuresAndBanners(
    rig: CameraRig,
    path: Path,
    camEye: Vec3,
    titlePaint: Paint,
    subPaint: Paint
) {
    if (hypot(camEye.x - 16f, camEye.z - (-178f)) > 320f) return

    val faces = ArrayList<QuadFace3D>(320)

    fun addBox3D(
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
        faces.add(
            QuadFace3D(
                Vec3(minX, maxY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(minX, maxY, maxZ),
                topCol,
                stroke,
                bias
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, minZ),
                Vec3(maxX, minY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(minX, maxY, minZ),
                frontBackCol,
                stroke,
                bias
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, maxZ),
                Vec3(maxX, minY, maxZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(minX, maxY, maxZ),
                frontBackCol,
                stroke,
                bias
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(minX, minY, minZ),
                Vec3(minX, maxY, minZ),
                Vec3(minX, maxY, maxZ),
                Vec3(minX, minY, maxZ),
                sideCol,
                stroke,
                bias
            )
        )
        faces.add(
            QuadFace3D(
                Vec3(maxX, minY, minZ),
                Vec3(maxX, maxY, minZ),
                Vec3(maxX, maxY, maxZ),
                Vec3(maxX, minY, maxZ),
                sideCol,
                stroke,
                bias
            )
        )
    }

    // 1. Passenger Boarding Platforms & Terracotta Columns between the 10 North Bays (Bays 01–10)
    for (pIdx in 0..10) {
        val platCenterX = -21.8f + pIdx * 7.2f
        val px0 = platCenterX - 1.05f
        val px1 = platCenterX + 1.05f
        // North Row Boarding Island
        addBox3D(
            px0, 0.06f, -184.5f,
            px1, 0.34f, -161.5f,
            topCol = MatPlatformTileLight,
            sideCol = MatCurbYellow,
            frontBackCol = MatCurbBlack
        )
        // Terracotta Columns supporting North Canopy
        for (pz in floatArrayOf(-182.0f, -172.0f, -162.5f)) {
            addBox3D(
                platCenterX - 0.24f, 0.34f, pz - 0.24f,
                platCenterX + 0.24f, 6.20f, pz + 0.24f,
                topCol = MatTerracottaDark,
                sideCol = MatTerracottaPillar,
                frontBackCol = MatTerracottaDark
            )
        }
    }

    // 2. High-Clearance Galvanized Terminal Canopy spanning all 10 North Departure Bays (Bays 01–10)
    addBox3D(
        -23.0f, 6.20f, -185.5f,
        51.5f, 6.85f, -160.5f,
        topCol = MatRoofGalvanized,
        sideCol = MatSakthanFasciaOrange,
        frontBackCol = MatSakthanFasciaOrange
    )

    // 3. South Intercity Concourse Canopy & Boarding Platforms (Bays 11–20)
    addBox3D(
        -23.0f, 0.06f, -218.5f,
        51.5f, 0.34f, -214.5f,
        topCol = MatPlatformTileLight,
        sideCol = MatCurbYellow,
        frontBackCol = MatCurbBlack
    )
    addBox3D(
        -23.0f, 6.20f, -217.5f,
        51.5f, 6.85f, -197.0f,
        topCol = MatRoofGalvanized,
        sideCol = MatSakthanFasciaOrange,
        frontBackCol = MatSakthanFasciaOrange
    )

    // 4. Main Terminal Administration Concourse & Sakthan Clock Tower (West Perimeter X = -23.5m to -19.5m)
    addBox3D(
        -23.8f, 0.06f, -218.0f,
        -19.8f, 8.8f, -148.0f,
        topCol = MatPlatformTileDark,
        sideCol = MatCreamPlaster,
        frontBackCol = MatCreamPlaster
    )
    addBox3D(
        -23.2f, 8.8f, -164.0f,
        -19.8f, 16.0f, -159.0f,
        topCol = MatSakthanFasciaOrange,
        sideCol = MatCreamPlaster,
        frontBackCol = MatTerracottaPillar
    )

    // 5. Parked Kerala Buses across the 20 Bays (Leaving Bay 07 at X=25.0m open for the Player's Bus!)
    fun addParkedKeralaBus(centerX: Float, centerZ: Float, bodyCol: Color, upperCol: Color, stripeCol: Color) {
        val bw = 1.22f
        val bl = 5.5f
        addBox3D(
            centerX - bw, 0.42f, centerZ - bl,
            centerX + bw, 1.66f, centerZ + bl,
            topCol = bodyCol,
            sideCol = bodyCol,
            frontBackCol = bodyCol
        )
        addBox3D(
            centerX - bw - 0.02f, 1.52f, centerZ - bl - 0.02f,
            centerX + bw + 0.02f, 1.68f, centerZ + bl + 0.02f,
            topCol = stripeCol,
            sideCol = stripeCol,
            frontBackCol = stripeCol,
            stroke = null,
            bias = -0.05f
        )
        addBox3D(
            centerX - bw, 1.68f, centerZ - bl,
            centerX + bw, 3.16f, centerZ + bl,
            topCol = Color(0xFFE2E8F0),
            sideCol = upperCol,
            frontBackCol = upperCol
        )
        addBox3D(
            centerX - bw + 0.14f, 1.80f, centerZ - bl - 0.03f,
            centerX + bw - 0.14f, 2.84f, centerZ + bl + 0.03f,
            topCol = Color(0xFFE2E8F0),
            sideCol = Color(0xFF0F172A),
            frontBackCol = Color(0xFF0F172A),
            bias = -0.06f
        )
    }

    val busLiveries = arrayOf(
        Triple(Color(0xFF1D4ED8), Color(0xFFF1F5F9), Color(0xFFFACC15)), // Blue Private Bus
        Triple(Color(0xFFBE185D), Color(0xFFFCE7F3), Color(0xFFFACC15)), // Pink Limited Stop
        Triple(Color(0xFFB91C1C), Color(0xFFFEF3C7), Color(0xFFF59E0B)), // KSRTC Red & Cream
        Triple(Color(0xFF047857), Color(0xFFF1F5F9), Color(0xFFFACC15)), // Emerald City Fast
        Triple(Color(0xFFC2410C), Color(0xFFFFF7ED), Color(0xFFFACC15))  // Orange Super Fast
    )

    // Populate North Row Bays 01–10 (Skipping bIdx == 6 which is X = 25.0m reserved for Player Bus!)
    for (bIdx in 0 until 10) {
        if (bIdx == 6) continue // Player's Bus Bay (Bay 07 at X = 25.0m)
        if (bIdx % 2 == 1 || bIdx == 4 || bIdx == 8) {
            val bx = -18.2f + bIdx * 7.2f
            val (bodyC, upperC, stripeC) = busLiveries[bIdx % busLiveries.size]
            addParkedKeralaBus(bx, -175.5f, bodyC, upperC, stripeC)
        }
    }

    // Populate South Row Bays 11–20 (Skipping bIdx == 6 so Chase camera line-of-sight is 100% clear!)
    for (bIdx in 0 until 10) {
        if (bIdx in 5..7) continue // Keep camera corridor clear
        if (bIdx % 2 == 0) {
            val bx = -18.2f + bIdx * 7.2f
            val (bodyC, upperC, stripeC) = busLiveries[(bIdx + 2) % busLiveries.size]
            addParkedKeralaBus(bx, -206.5f, bodyC, upperC, stripeC)
        }
    }

    // 6. Road-Aligned Terminal Perimeter Compound Wall (at Z = -140.0m)
    //    Completely clear of Left Side ENTRY (X = -0.5m..14.5m) and Right Side EXIT (X = 16.0m..34.0m)
    val exitZ = -140.0f
    // West Perimeter Wall (X = -22.0m..-3.8m, well outside Entry road edge)
    addBox3D(-22.0f, 0.06f, exitZ - 0.45f, -3.8f, 1.45f, exitZ + 0.35f, MatCreamPlaster, MatTerracottaDark, MatTerracottaPillar)
    // East Perimeter Wall (X = 37.8m..48.0m, well outside Exit road edge) + East Side Compound Wall
    addBox3D(37.8f, 0.06f, exitZ - 0.45f, 48.0f, 1.45f, exitZ + 0.35f, MatCreamPlaster, MatTerracottaDark, MatTerracottaPillar)
    addBox3D(47.3f, 0.06f, -218.0f, 48.0f, 1.45f, exitZ, MatCreamPlaster, MatTerracottaPillar, MatTerracottaDark)

    // 7. Full-Span Overhead Gantry Columns (planted far outside both roads at X = -3.8m and X = 37.8m, zero pillars on road!)
    addBox3D(-3.8f, 0.06f, exitZ - 0.38f, -3.0f, 6.8f, exitZ + 0.38f, MatTerracottaDark, MatTerracottaPillar)
    addBox3D(37.0f, 0.06f, exitZ - 0.38f, 37.8f, 6.8f, exitZ + 0.38f, MatTerracottaDark, MatTerracottaPillar)
    addBox3D(-3.8f, 5.45f, exitZ - 0.30f, 37.8f, 6.65f, exitZ + 0.30f, MatRoofUnderTruss, Color(0xFF1E293B))

    // Sort all 3D terminal faces far-to-near relative to camera
    faces.sortByDescending { face ->
        val cx = (face.v0.x + face.v1.x + face.v2.x + face.v3.x) * 0.25f - camEye.x
        val cy = (face.v0.y + face.v1.y + face.v2.y + face.v3.y) * 0.25f - camEye.y
        val cz = (face.v0.z + face.v1.z + face.v2.z + face.v3.z) * 0.25f - camEye.z
        hypot(hypot(cx, cy), cz) + face.biasDist
    }

    for (face in faces) {
        if (rig.buildClippedQuadPath(path, face.v0, face.v1, face.v2, face.v3)) {
            drawPath(path, face.color)
            if (face.strokeColor != null) {
                drawPath(path, face.strokeColor, style = Stroke(width = 1f))
            }
        }
    }

    // 9. Strict Stand Text Rule: Stand Name, ENTRY, and EXIT only!
    draw3DGantryBanner(
        rig = rig,
        path = path,
        leftX = 8.0f,
        rightX = 42.0f,
        bottomY = 5.35f,
        topY = 6.75f,
        zPos = -160.2f,
        bgColor = Color(0xFF7C2D12),
        borderColor = Color(0xFFFACC15),
        titleText = "SAKTHAN THAMPURAN STAND",
        subText = "",
        titleColor = Color(0xFFFEF08A),
        subColor = Color(0xFFFFFFFF),
        titlePaint = titlePaint,
        subPaint = subPaint
    )

    draw3DGantryBanner(
        rig = rig,
        path = path,
        leftX = 16.5f,
        rightX = 33.5f,
        bottomY = 5.48f,
        topY = 6.62f,
        zPos = -140.35f,
        bgColor = Color(0xFF065F46),
        borderColor = Color(0xFFFACC15),
        titleText = "EXIT",
        subText = "",
        titleColor = Color(0xFFFFFFFF),
        subColor = Color(0xFFFDE047),
        titlePaint = titlePaint,
        subPaint = subPaint
    )

    draw3DGantryBanner(
        rig = rig,
        path = path,
        leftX = -0.2f,
        rightX = 14.2f,
        bottomY = 5.48f,
        topY = 6.62f,
        zPos = -140.35f,
        bgColor = Color(0xFF1E3A8A),
        borderColor = Color(0xFF38BDF8),
        titleText = "ENTRY",
        subText = "",
        titleColor = Color(0xFFFFFFFF),
        subColor = Color(0xFFBAE6FD),
        titlePaint = titlePaint,
        subPaint = subPaint
    )
}

internal fun DrawScope.draw3DGantryBanner(
    rig: CameraRig,
    path: Path,
    leftX: Float,
    rightX: Float,
    bottomY: Float,
    topY: Float,
    zPos: Float,
    bgColor: Color,
    borderColor: Color,
    titleText: String,
    subText: String,
    titleColor: Color,
    subColor: Color,
    titlePaint: Paint,
    subPaint: Paint
) {
    val vBL = Vec3(leftX, bottomY, zPos)
    val vBR = Vec3(rightX, bottomY, zPos)
    val vTR = Vec3(rightX, topY, zPos)
    val vTL = Vec3(leftX, topY, zPos)

    if (!rig.buildClippedQuadPath(path, vBL, vBR, vTR, vTL)) return
    drawPath(path, bgColor)
    drawPath(path, borderColor, style = Stroke(width = 2.5f))

    val midY = (bottomY + topY) * 0.5f
    val pLeft = rig.projectPoint(Vec3(leftX, midY, zPos)) ?: return
    val pRight = rig.projectPoint(Vec3(rightX, midY, zPos)) ?: return
    val pTop = rig.projectPoint(Vec3((leftX + rightX) * 0.5f, topY, zPos)) ?: return
    val pBottom = rig.projectPoint(Vec3((leftX + rightX) * 0.5f, bottomY, zPos)) ?: return

    val pixelW = hypot(pRight.x - pLeft.x, pRight.y - pLeft.y)
    val pixelH = hypot(pBottom.x - pTop.x, pBottom.y - pTop.y)
    if (pixelW < 32f || pixelH < 9f) return

    val centerX = (pLeft.x + pRight.x) * 0.5f
    val centerY = (pTop.y + pBottom.y) * 0.5f
    var angleDeg = Math.toDegrees(atan2((pRight.y - pLeft.y).toDouble(), (pRight.x - pLeft.x).toDouble())).toFloat()
    if (angleDeg > 90f) angleDeg -= 180f
    if (angleDeg < -90f) angleDeg += 180f

    val titleSizePx = min(pixelH * (if (subText.isEmpty()) 0.52f else 0.42f), (pixelW / (titleText.length.coerceAtLeast(6) * 0.62f))).coerceIn(9f, 28f)
    titlePaint.color = titleColor.toArgb()
    titlePaint.textSize = titleSizePx

    val nativeCanvas = drawContext.canvas.nativeCanvas
    nativeCanvas.save()
    nativeCanvas.translate(centerX, centerY)
    nativeCanvas.rotate(angleDeg)
    if (subText.isEmpty()) {
        nativeCanvas.drawText(titleText, 0f, titleSizePx * 0.34f, titlePaint)
    } else {
        val subSizePx = (titleSizePx * 0.72f).coerceIn(7.5f, 20f)
        subPaint.color = subColor.toArgb()
        subPaint.textSize = subSizePx
        nativeCanvas.drawText(titleText, 0f, -pixelH * 0.07f, titlePaint)
        nativeCanvas.drawText(subText, 0f, pixelH * 0.32f, subPaint)
    }
    nativeCanvas.restore()
}

/**
 * Renders the CGTrader Hino RM 2 Bus (12m Rear-Engine Intercity AC Luxury Coach) in 3D.
 *
 * Asset Provenance & License Compliance:
 * - Source URL : https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus
 * - Creator    : asrafullislamsiam (CGTrader)
 * - License    : CGTrader Royalty Free License (No AI)
 * - Details    : Pearl White aerodynamic coachwork ("main body", "Rm 2 paint .png") with
 *                sweeping Royal Blue, Crimson Red & Metallic Gold livery ribbons, 3 underfloor
 *                luggage lockers per side ("box locker"), front-left passenger door ("box door"),
 *                brushed aluminum beltline trim ("aluminium"), reconstructed panoramic front glass
 *                ("front glass" / "FRONT GLASS.png") with dual wipers, Hino chrome grille ("grill")
 *                & emblem ("hino"), projector headlamps ("headlight"), rooftop Denso AC unit ("ac"),
 *                rear 6-cylinder turbo-diesel engine louvre grille, chrome exhaust ("exausht"),
 *                3-tier vertical LED taillights ("back light", ".002" reverse, ".003" indicator),
 *                and 6 commercial wheels with steering front wheels.
 */
private fun DrawScope.drawHinoRm2Bus3D(
    rig: CameraRig,
    path: Path,
    state: SimUiState,
    titlePaint: Paint,
    subPaint: Paint
) {
    val pos = state.busPos
    val yaw = state.busHeadingRad
    val s = sin(yaw)
    val c = cos(yaw)

    fun busWorld(localX: Float, localY: Float, localZ: Float): Vec3 {
        val wx = pos.x + localX * c + localZ * s
        val wz = pos.z - localX * s + localZ * c
        return Vec3(wx, pos.y + localY, wz)
    }

    val halfW = 1.28f
    val halfL = 6.0f
    val floorY = 0.40f
    val skirtY = 0.80f
    val lockerTopY = 1.38f
    val beltY = 1.76f
    val cantY = 2.92f
    val roofY = 3.30f

    // 1. Projector Headlamp Ground Light Cones ("headlight" / "Lights0012_350.jpg")
    if (state.headlightsOn) {
        val b0 = busWorld(-1.10f, 0.075f, halfL + 0.2f)
        val b1 = busWorld(1.10f, 0.075f, halfL + 0.2f)
        val b2 = busWorld(5.4f, 0.075f, halfL + 30f)
        val b3 = busWorld(-5.4f, 0.075f, halfL + 30f)
        if (rig.buildClippedQuadPath(path, b0, b1, b2, b3)) {
            drawPath(path, Color(0x30E0F2FE))
        }
        val inner0 = busWorld(-0.90f, 0.076f, halfL + 0.2f)
        val inner1 = busWorld(0.90f, 0.076f, halfL + 0.2f)
        val inner2 = busWorld(2.9f, 0.076f, halfL + 16f)
        val inner3 = busWorld(-2.9f, 0.076f, halfL + 16f)
        if (rig.buildClippedQuadPath(path, inner0, inner1, inner2, inner3)) {
            drawPath(path, Color(0x3CF8FAFC))
        }
    }

    // 2. Soft Ambient Contact Shadow under Chassis ("base" / "suspetion")
    val sh0 = busWorld(-halfW * 1.12f, 0.064f, -halfL * 1.05f)
    val sh1 = busWorld(halfW * 1.12f, 0.064f, -halfL * 1.05f)
    val sh2 = busWorld(halfW * 1.12f, 0.064f, halfL * 1.05f)
    val sh3 = busWorld(-halfW * 1.12f, 0.064f, halfL * 1.05f)
    if (rig.buildClippedQuadPath(path, sh0, sh1, sh2, sh3)) {
        drawPath(path, Color(0x9E070A0F))
    }

    // Hino RM 2 Authentic PBR Color Palette ("Rm 2 paint .png", "FRONT GLASS.png", "grey.png")
    val hinoPearlWhite = Color(0xFFF8FAFC)
    val hinoSidePearl = Color(0xFFF1F5F9)
    val hinoSkirtNavy = Color(0xFF0F2147)
    val hinoRoyalBlue = Color(0xFF1D4ED8)
    val hinoCrimsonRed = Color(0xFFDC2626)
    val hinoMetallicGold = Color(0xFFEAB308)
    val hinoAluminium = Color(0xFFCBD5E1)
    val hinoChrome = Color(0xFFE2E8F0)
    val hinoWindowGlass = Color(0xFF0B1526)
    val hinoFrontGlass = Color(0xFF122338)
    val hinoSunVisorTint = Color(0xFF061229)
    val hinoBurgundySeats = Color(0xFF6B1D28)
    val hinoEngineGrille = Color(0xFF111827)
    val hinoPlateYellow = Color(0xFFFACC15)
    val tireRubber = Color(0xFF111317)
    val rimSilver = Color(0xFFD8E0EA)

    val faces = ArrayList<QuadFace3D>(140)

    // 3. Six Commercial Wheels (FL, FR, ML, MR, RL, RR) with Steering Front Axle
    val axleZs = floatArrayOf(3.45f, -2.65f, -4.05f)
    val frontSteerRad = state.steerInput * 0.45f
    for ((aIdx, az) in axleZs.withIndex()) {
        val isFront = aIdx == 0
        val ws = if (isFront) sin(frontSteerRad) else 0f
        val wc = if (isFront) cos(frontSteerRad) else 1f
        for (side in intArrayOf(-1, 1)) {
            val wx = side * (halfW + 0.03f)
            val r = 0.50f
            val dz0 = -r * wc
            val dx0 = -r * ws
            val dz1 = r * wc
            val dx1 = r * ws
            faces.add(
                QuadFace3D(
                    busWorld(wx + dx0, 0.06f, az + dz0),
                    busWorld(wx + dx0, 0.98f, az + dz0),
                    busWorld(wx + dx1, 0.98f, az + dz1),
                    busWorld(wx + dx1, 0.06f, az + dz1),
                    tireRubber
                )
            )
            val hubX = side * (halfW + 0.046f)
            val hr = 0.27f
            faces.add(
                QuadFace3D(
                    busWorld(hubX - hr * ws, 0.28f, az - hr * wc),
                    busWorld(hubX - hr * ws, 0.76f, az - hr * wc),
                    busWorld(hubX + hr * ws, 0.76f, az + hr * wc),
                    busWorld(hubX + hr * ws, 0.28f, az + hr * wc),
                    rimSilver,
                    biasDist = -0.05f
                )
            )
        }
    }

    // 4. Side Body Panels ("main body"), Luggage Lockers ("box locker"), Livery Ribbons & Tinted Windows
    val leftBlinkOn = state.blinkerPhase && (state.turnLeftOn || state.hazardsOn)
    val rightBlinkOn = state.blinkerPhase && (state.turnRightOn || state.hazardsOn)

    for (side in intArrayOf(-1, 1)) {
        val sx = side * halfW
        val detailX = side * (halfW + 0.012f)
        val trimX = side * (halfW + 0.022f)

        // Deep Navy lower chassis valence
        faces.add(
            QuadFace3D(
                busWorld(sx, floorY, -halfL),
                busWorld(sx, skirtY, -halfL),
                busWorld(sx, skirtY, halfL),
                busWorld(sx, floorY, halfL),
                hinoSkirtNavy
            )
        )
        // Pearl White main side body ("main body")
        faces.add(
            QuadFace3D(
                busWorld(sx, skirtY, -halfL),
                busWorld(sx, roofY, -halfL),
                busWorld(sx, roofY, halfL),
                busWorld(sx, skirtY, halfL),
                hinoSidePearl
            )
        )
        // 3 Underfloor Luggage Lockers per side ("box locker.001..006") + Chrome Handles ("handle")
        for (lIdx in 0 until 3) {
            val lz0 = -1.65f + lIdx * 1.62f
            val lz1 = lz0 + 1.48f
            faces.add(
                QuadFace3D(
                    busWorld(detailX, skirtY + 0.04f, lz0),
                    busWorld(detailX, lockerTopY, lz0),
                    busWorld(detailX, lockerTopY, lz1),
                    busWorld(detailX, skirtY + 0.04f, lz1),
                    hinoPearlWhite,
                    strokeColor = Color(0x550F172A),
                    biasDist = -0.03f
                )
            )
            val hMid = (lz0 + lz1) * 0.5f
            faces.add(
                QuadFace3D(
                    busWorld(trimX, skirtY + 0.12f, hMid - 0.14f),
                    busWorld(trimX, skirtY + 0.18f, hMid - 0.14f),
                    busWorld(trimX, skirtY + 0.18f, hMid + 0.14f),
                    busWorld(trimX, skirtY + 0.12f, hMid + 0.14f),
                    hinoChrome,
                    strokeColor = null,
                    biasDist = -0.05f
                )
            )
        }

        // Front-Left Passenger Plug Door ("box door")
        if (side == -1) {
            faces.add(
                QuadFace3D(
                    busWorld(detailX, floorY + 0.05f, 3.95f),
                    busWorld(detailX, cantY - 0.12f, 3.95f),
                    busWorld(detailX, cantY - 0.12f, 5.25f),
                    busWorld(detailX, floorY + 0.05f, 5.25f),
                    hinoPearlWhite,
                    strokeColor = Color(0x660F172A),
                    biasDist = -0.04f
                )
            )
        }

        // Sweeping Hino RM 2 Livery Stripes (Royal Blue, Crimson Red, and Metallic Gold ribbons)
        faces.add(
            QuadFace3D(
                busWorld(detailX, lockerTopY + 0.02f, -halfL),
                busWorld(detailX, lockerTopY + 0.22f, -halfL),
                busWorld(detailX, beltY - 0.06f, halfL),
                busWorld(detailX, beltY - 0.24f, halfL),
                hinoRoyalBlue,
                strokeColor = null,
                biasDist = -0.04f
            )
        )
        faces.add(
            QuadFace3D(
                busWorld(detailX, lockerTopY - 0.12f, -halfL),
                busWorld(detailX, lockerTopY + 0.01f, -halfL),
                busWorld(detailX, beltY - 0.25f, halfL),
                busWorld(detailX, beltY - 0.36f, halfL),
                hinoCrimsonRed,
                strokeColor = null,
                biasDist = -0.04f
            )
        )
        faces.add(
            QuadFace3D(
                busWorld(trimX, beltY - 0.06f, -halfL),
                busWorld(trimX, beltY, -halfL),
                busWorld(trimX, beltY, halfL),
                busWorld(trimX, beltY - 0.06f, halfL),
                hinoMetallicGold,
                strokeColor = null,
                biasDist = -0.05f
            )
        )
        // Brushed Aluminum Beltline Molding ("aluminium")
        faces.add(
            QuadFace3D(
                busWorld(trimX, beltY, -halfL),
                busWorld(trimX, beltY + 0.06f, -halfL),
                busWorld(trimX, beltY + 0.06f, halfL),
                busWorld(trimX, beltY, halfL),
                hinoAluminium,
                strokeColor = null,
                biasDist = -0.05f
            )
        )

        // Continuous Frameless Dark-Tinted Side Windows ("side glass") with Interior Burgundy Seats ("seat")
        for (wIdx in 0 until 6) {
            val z0 = -halfL + 0.36f + wIdx * 1.86f
            val z1 = z0 + 1.68f
            faces.add(
                QuadFace3D(
                    busWorld(detailX, beltY + 0.08f, z0),
                    busWorld(detailX, cantY, z0),
                    busWorld(detailX, cantY, z1),
                    busWorld(detailX, beltY + 0.08f, z1),
                    hinoWindowGlass,
                    biasDist = -0.06f
                )
            )
            // Luxury Burgundy Coach Seat Headrest Silhouette visible inside window
            faces.add(
                QuadFace3D(
                    busWorld(detailX, beltY + 0.14f, z0 + 0.35f),
                    busWorld(detailX, beltY + 0.42f, z0 + 0.35f),
                    busWorld(detailX, beltY + 0.42f, z0 + 0.95f),
                    busWorld(detailX, beltY + 0.14f, z0 + 0.95f),
                    hinoBurgundySeats,
                    strokeColor = null,
                    biasDist = -0.07f
                )
            )
        }

        // Amber Side Marker Light ("side light.001" / ".002")
        val sideBlink = if (side == -1) leftBlinkOn else rightBlinkOn
        faces.add(
            QuadFace3D(
                busWorld(trimX, skirtY + 0.18f, 1.85f),
                busWorld(trimX, skirtY + 0.28f, 1.85f),
                busWorld(trimX, skirtY + 0.28f, 2.15f),
                busWorld(trimX, skirtY + 0.18f, 2.15f),
                if (sideBlink) Color(0xFFFBBF24) else Color(0xFFD97706),
                strokeColor = null,
                biasDist = -0.06f
            )
        )

        // High-Mount Aerodynamic "Elephant-Ear" Wing Mirrors projecting ahead of A-pillars
        val mirX0 = side * (halfW + 0.04f)
        val mirX1 = side * (halfW + 0.26f)
        val mL = min(mirX0, mirX1)
        val mR = max(mirX0, mirX1)
        faces.add(
            QuadFace3D(
                busWorld(mL, cantY - 0.55f, halfL + 0.18f),
                busWorld(mR, cantY - 0.55f, halfL + 0.18f),
                busWorld(mR, cantY - 0.08f, halfL + 0.18f),
                busWorld(mL, cantY - 0.08f, halfL + 0.18f),
                hinoPearlWhite,
                biasDist = -0.09f
            )
        )
    }

    // 5. Roof Panel & Sculpted Rooftop Denso AC Unit ("ac")
    faces.add(
        QuadFace3D(
            busWorld(-halfW, roofY, -halfL),
            busWorld(halfW, roofY, -halfL),
            busWorld(halfW, roofY, halfL),
            busWorld(-halfW, roofY, halfL),
            hinoPearlWhite
        )
    )
    val acW = 0.88f
    val acY = roofY + 0.26f
    faces.add(
        QuadFace3D(
            busWorld(-acW, roofY, -2.2f),
            busWorld(acW, roofY, -2.2f),
            busWorld(acW, acY, -2.2f),
            busWorld(-acW, acY, -2.2f),
            hinoRoyalBlue,
            biasDist = -0.09f
        )
    )
    faces.add(
        QuadFace3D(
            busWorld(-acW, acY, -2.2f),
            busWorld(acW, acY, -2.2f),
            busWorld(acW, acY, 2.2f),
            busWorld(-acW, acY, 2.2f),
            hinoPearlWhite,
            biasDist = -0.10f
        )
    )

    // 6. Front Fascia: Reconstructed Panoramic Front Glass ("front glass"), Hino Chrome Grille ("grill"),
    //    Hino V-Emblem ("hino"), Projector Headlamps ("headlight"), & Dual Wipers
    faces.add(
        QuadFace3D(
            busWorld(-halfW, floorY, halfL),
            busWorld(halfW, floorY, halfL),
            busWorld(halfW, beltY, halfL),
            busWorld(-halfW, beltY, halfL),
            hinoPearlWhite
        )
    )
    faces.add(
        QuadFace3D(
            busWorld(-halfW, beltY, halfL),
            busWorld(halfW, beltY, halfL),
            busWorld(halfW, roofY, halfL),
            busWorld(-halfW, roofY, halfL),
            hinoPearlWhite
        )
    )
    // Reconstructed Panoramic Front Glass ("front glass") + Top Blue Sun-Visor Tint Band ("FRONT GLASS.png")
    faces.add(
        QuadFace3D(
            busWorld(-halfW + 0.10f, beltY + 0.06f, halfL + 0.015f),
            busWorld(halfW - 0.10f, beltY + 0.06f, halfL + 0.015f),
            busWorld(halfW - 0.10f, roofY - 0.34f, halfL + 0.015f),
            busWorld(-halfW + 0.10f, roofY - 0.34f, halfL + 0.015f),
            hinoFrontGlass,
            biasDist = -0.06f
        )
    )
    faces.add(
        QuadFace3D(
            busWorld(-halfW + 0.10f, roofY - 0.34f, halfL + 0.018f),
            busWorld(halfW - 0.10f, roofY - 0.34f, halfL + 0.018f),
            busWorld(halfW - 0.10f, roofY - 0.10f, halfL + 0.018f),
            busWorld(-halfW + 0.10f, roofY - 0.10f, halfL + 0.018f),
            hinoSunVisorTint,
            biasDist = -0.07f
        )
    )
    // Hino Trapezoidal Chrome Front Grille ("grill") & Central V-Emblem ("hino")
    faces.add(
        QuadFace3D(
            busWorld(-0.72f, skirtY + 0.08f, halfL + 0.02f),
            busWorld(0.72f, skirtY + 0.08f, halfL + 0.02f),
            busWorld(0.82f, beltY - 0.22f, halfL + 0.02f),
            busWorld(-0.82f, beltY - 0.22f, halfL + 0.02f),
            hinoChrome,
            biasDist = -0.07f
        )
    )
    faces.add(
        QuadFace3D(
            busWorld(-0.22f, beltY - 0.18f, halfL + 0.026f),
            busWorld(0.22f, beltY - 0.18f, halfL + 0.026f),
            busWorld(0.26f, beltY - 0.04f, halfL + 0.026f),
            busWorld(-0.26f, beltY - 0.04f, halfL + 0.026f),
            hinoMetallicGold,
            biasDist = -0.08f
        )
    )
    // Multi-Element Projector Headlamps ("headlight" / "Lights0012_350.jpg")
    for (side in intArrayOf(-1, 1)) {
        val hx0 = side * (halfW - 0.08f)
        val hx1 = side * (halfW - 0.48f)
        val hL = min(hx0, hx1)
        val hR = max(hx0, hx1)
        faces.add(
            QuadFace3D(
                busWorld(hL, skirtY + 0.10f, halfL + 0.022f),
                busWorld(hR, skirtY + 0.10f, halfL + 0.022f),
                busWorld(hR, skirtY + 0.42f, halfL + 0.022f),
                busWorld(hL, skirtY + 0.42f, halfL + 0.022f),
                if (state.headlightsOn) Color(0xFFE0F2FE) else Color(0xFF94A3B8),
                biasDist = -0.08f
            )
        )
    }
    // Front Commercial Registration Plate ("plate")
    faces.add(
        QuadFace3D(
            busWorld(-0.32f, floorY + 0.14f, halfL + 0.025f),
            busWorld(0.32f, floorY + 0.14f, halfL + 0.025f),
            busWorld(0.32f, floorY + 0.28f, halfL + 0.025f),
            busWorld(-0.32f, floorY + 0.28f, halfL + 0.025f),
            hinoPlateYellow,
            biasDist = -0.08f
        )
    )

    // 7. Rear Engine Bay Fascia (Hino RM 2 Rear 6-Cylinder Turbo-Diesel Engine, Chrome Exhaust & 3-Tier Taillights)
    faces.add(
        QuadFace3D(
            busWorld(-halfW, floorY, -halfL),
            busWorld(halfW, floorY, -halfL),
            busWorld(halfW, skirtY, -halfL),
            busWorld(-halfW, skirtY, -halfL),
            hinoSkirtNavy
        )
    )
    faces.add(
        QuadFace3D(
            busWorld(-halfW, skirtY, -halfL),
            busWorld(halfW, skirtY, -halfL),
            busWorld(halfW, roofY, -halfL),
            busWorld(-halfW, roofY, -halfL),
            hinoPearlWhite
        )
    )
    // Rear Sweeping Royal Blue & Crimson Red Hino RM 2 Livery Accent Bands
    faces.add(
        QuadFace3D(
            busWorld(-halfW, beltY - 0.26f, -halfL - 0.012f),
            busWorld(halfW, beltY - 0.26f, -halfL - 0.012f),
            busWorld(halfW, beltY - 0.08f, -halfL - 0.012f),
            busWorld(-halfW, beltY - 0.08f, -halfL - 0.012f),
            hinoRoyalBlue,
            strokeColor = null,
            biasDist = -0.05f
        )
    )
    faces.add(
        QuadFace3D(
            busWorld(-halfW, beltY - 0.36f, -halfL - 0.012f),
            busWorld(halfW, beltY - 0.36f, -halfL - 0.012f),
            busWorld(halfW, beltY - 0.26f, -halfL - 0.012f),
            busWorld(-halfW, beltY - 0.26f, -halfL - 0.012f),
            hinoCrimsonRed,
            strokeColor = null,
            biasDist = -0.05f
        )
    )
    faces.add(
        QuadFace3D(
            busWorld(-halfW, beltY - 0.08f, -halfL - 0.014f),
            busWorld(halfW, beltY - 0.08f, -halfL - 0.014f),
            busWorld(halfW, beltY, -halfL - 0.014f),
            busWorld(-halfW, beltY, -halfL - 0.014f),
            hinoMetallicGold,
            strokeColor = null,
            biasDist = -0.06f
        )
    )
    // Rear-Engine Hatch Recessed Dark Intercooler Louvre Grille (Hino RM 2 Rear Engine)
    faces.add(
        QuadFace3D(
            busWorld(-0.74f, skirtY + 0.08f, -halfL - 0.018f),
            busWorld(0.74f, skirtY + 0.08f, -halfL - 0.018f),
            busWorld(0.74f, beltY - 0.40f, -halfL - 0.018f),
            busWorld(-0.74f, beltY - 0.40f, -halfL - 0.018f),
            hinoEngineGrille,
            biasDist = -0.07f
        )
    )
    // Chrome Exhaust Tailpipe ("exausht") & Rear Bumper Trim
    faces.add(
        QuadFace3D(
            busWorld(-0.98f, floorY + 0.04f, -halfL - 0.03f),
            busWorld(-0.78f, floorY + 0.04f, -halfL - 0.03f),
            busWorld(-0.78f, floorY + 0.20f, -halfL - 0.03f),
            busWorld(-0.98f, floorY + 0.20f, -halfL - 0.03f),
            hinoChrome,
            biasDist = -0.08f
        )
    )
    // Rear Registration Plate ("plate.001")
    faces.add(
        QuadFace3D(
            busWorld(-0.30f, floorY + 0.22f, -halfL - 0.025f),
            busWorld(0.30f, floorY + 0.22f, -halfL - 0.025f),
            busWorld(0.30f, floorY + 0.36f, -halfL - 0.025f),
            busWorld(-0.30f, floorY + 0.36f, -halfL - 0.025f),
            hinoPlateYellow,
            biasDist = -0.08f
        )
    )
    // Rear Upper Tinted Glass ("rear glass")
    faces.add(
        QuadFace3D(
            busWorld(-halfW + 0.16f, beltY + 0.14f, -halfL - 0.016f),
            busWorld(halfW - 0.16f, beltY + 0.14f, -halfL - 0.016f),
            busWorld(halfW - 0.20f, cantY - 0.04f, -halfL - 0.016f),
            busWorld(-halfW + 0.20f, cantY - 0.04f, -halfL - 0.016f),
            hinoWindowGlass,
            biasDist = -0.06f
        )
    )
    // Upper Rear Destination / Model Header ("HINO RM 2 AC COACH")
    faces.add(
        QuadFace3D(
            busWorld(-0.76f, cantY + 0.03f, -halfL - 0.02f),
            busWorld(0.76f, cantY + 0.03f, -halfL - 0.02f),
            busWorld(0.76f, roofY - 0.06f, -halfL - 0.02f),
            busWorld(-0.76f, roofY - 0.06f, -halfL - 0.02f),
            Color(0xFF091326),
            biasDist = -0.07f
        )
    )

    // 8. 3-Tier Vertical LED Taillights ("back light" = Red, "back light.003" = Amber, "back light.002" = White Reverse)
    val brakeActive = state.brakeInput > 0.05f || state.isParkBrakeActive
    val brakeCol = if (brakeActive) Color(0xFFFF1E1E) else Color(0xFFB91C1C)
    val revActive = state.gear == com.bharatbussim.game.model.TransmissionGear.Reverse
    val revCol = if (revActive) Color(0xFFFFFFFF) else Color(0xFFCBD5E1)
    val leftIndCol = if (leftBlinkOn) Color(0xFFFBBF24) else Color(0xFF78350F)
    val rightIndCol = if (rightBlinkOn) Color(0xFFFBBF24) else Color(0xFF78350F)

    for (side in intArrayOf(-1, 1)) {
        val xOuter = side * (halfW - 0.08f)
        val xInner = side * (halfW - 0.38f)
        val xL = min(xOuter, xInner)
        val xR = max(xOuter, xInner)
        // Top tier: Red Brake / Tail Lamp ("back light")
        faces.add(
            QuadFace3D(
                busWorld(xL, skirtY + 0.42f, -halfL - 0.025f),
                busWorld(xR, skirtY + 0.42f, -halfL - 0.025f),
                busWorld(xR, skirtY + 0.76f, -halfL - 0.025f),
                busWorld(xL, skirtY + 0.76f, -halfL - 0.025f),
                brakeCol,
                biasDist = -0.09f
            )
        )
        // Middle tier: Amber Turn Indicator ("back light.003")
        faces.add(
            QuadFace3D(
                busWorld(xL, skirtY + 0.22f, -halfL - 0.025f),
                busWorld(xR, skirtY + 0.22f, -halfL - 0.025f),
                busWorld(xR, skirtY + 0.40f, -halfL - 0.025f),
                busWorld(xL, skirtY + 0.40f, -halfL - 0.025f),
                if (side == -1) leftIndCol else rightIndCol,
                biasDist = -0.09f
            )
        )
        // Bottom tier: White Reverse Lamp ("back light.002")
        faces.add(
            QuadFace3D(
                busWorld(xL, skirtY + 0.06f, -halfL - 0.025f),
                busWorld(xR, skirtY + 0.06f, -halfL - 0.025f),
                busWorld(xR, skirtY + 0.20f, -halfL - 0.025f),
                busWorld(xL, skirtY + 0.20f, -halfL - 0.025f),
                revCol,
                biasDist = -0.09f
            )
        )
    }

    faces.sortByDescending { face ->
        val cx = (face.v0.x + face.v1.x + face.v2.x + face.v3.x) * 0.25f - rig.eye.x
        val cy = (face.v0.y + face.v1.y + face.v2.y + face.v3.y) * 0.25f - rig.eye.y
        val cz = (face.v0.z + face.v1.z + face.v2.z + face.v3.z) * 0.25f - rig.eye.z
        hypot(hypot(cx, cy), cz) + face.biasDist
    }

    for (face in faces) {
        if (rig.buildClippedQuadPath(path, face.v0, face.v1, face.v2, face.v3)) {
            drawPath(path, face.color)
            if (face.strokeColor != null) {
                drawPath(path, face.strokeColor, style = Stroke(width = 1f))
            }
        }
    }

    // 9. Render 3D "HINO RM 2" Chrome Badge on the Rear Tailgate & Dual Animated Front Wipers
    val rearBadgePt = rig.projectPoint(busWorld(0f, beltY - 0.17f, -halfL - 0.03f))
    if (rearBadgePt != null) {
        val cRear = rig.toCamSpace(busWorld(0f, beltY - 0.17f, -halfL - 0.03f))
        if (cRear.cz in 2.5f..42f) {
            val badgeSize = (180f / cRear.cz).coerceIn(8f, 18f)
            titlePaint.color = Color(0xFFFef08a).toArgb()
            titlePaint.textSize = badgeSize
            subPaint.color = Color.White.toArgb()
            subPaint.textSize = (badgeSize * 0.78f).coerceAtLeast(7f)
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText("HINO RM 2", rearBadgePt.x, rearBadgePt.y, titlePaint)
        }
    }

    // Animated Dual Windshield Wipers on Reconstructed Front Glass ("front glass")
    val wiperSweep = if (state.wipersOn) sin(state.wiperPhaseRad) * 0.42f else -0.32f
    for (pivotX in floatArrayOf(-0.55f, 0.35f)) {
        val pBase = busWorld(pivotX, beltY + 0.10f, halfL + 0.025f)
        val pTip = busWorld(pivotX + sin(wiperSweep) * 0.62f, beltY + 0.10f + cos(wiperSweep) * 0.68f, halfL + 0.025f)
        val seg = rig.projectSegmentClipped(pBase, pTip)
        if (seg != null) {
            drawLine(Color(0xFF0F172A), seg.first, seg.second, strokeWidth = 2.6f, cap = StrokeCap.Round)
        }
    }
}

/**
 * Renders the 3D Hino RM 2 Driver Cockpit Interior when [CameraViewMode.Cockpit] is active:
 * - Upper tinted sun-visor header ("FRONT GLASS.png") & Left/Right A-pillars
 * - Wrap-around Hino RM 2 charcoal dashboard ("dashboard")
 * - 3 illuminated circular instrument gauges ("circle.009" Speedo, "circle.010" RPM, "circle.011" Air Pressure)
 * - AC Control panel ("AC CONTROLL.png"), Digital Hour Meter ("hour meter"), and Tissue Box ("tissue")
 * - Animated dual windshield wipers when active
 */
private fun DrawScope.drawHinoRm2Cockpit3D(state: SimUiState) {
    val w = size.width
    val h = size.height

    // 1. Upper Sun-Visor Tint Band ("FRONT GLASS.png")
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to Color(0xCC040D21),
            0.75f to Color(0x66081B38),
            1.0f to Color.Transparent,
            startY = 0f,
            endY = h * 0.18f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h * 0.18f)
    )

    // 2. Left & Right Coach A-Pillars
    val pillarPathL = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.075f, 0f)
        lineTo(w * 0.045f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(pillarPathL, Color(0xFF161E2B))

    val pillarPathR = Path().apply {
        moveTo(w, 0f)
        lineTo(w * 0.925f, 0f)
        lineTo(w * 0.955f, h)
        lineTo(w, h)
        close()
    }
    drawPath(pillarPathR, Color(0xFF161E2B))

    // 3. Animated Windshield Wipers in Cockpit View
    val wiperAngle = if (state.wipersOn) sin(state.wiperPhaseRad) * 0.55f else -0.48f
    val bladeLen = h * 0.42f
    for (px in floatArrayOf(w * 0.34f, w * 0.68f)) {
        val base = Offset(px, h * 0.76f)
        val tip = Offset(px + sin(wiperAngle) * bladeLen, base.y - cos(wiperAngle) * bladeLen)
        drawLine(
            color = Color(0xDD0F172A),
            start = base,
            end = tip,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // 4. Hino RM 2 Charcoal Wrap-Around Dashboard Deck ("dashboard")
    val dashTopY = h * 0.73f
    val dashPath = Path().apply {
        moveTo(0f, h)
        lineTo(0f, dashTopY + h * 0.04f)
        quadraticTo(w * 0.35f, dashTopY - h * 0.03f, w * 0.68f, dashTopY - h * 0.02f)
        lineTo(w, dashTopY + h * 0.03f)
        lineTo(w, h)
        close()
    }
    drawPath(dashPath, Color(0xFF1B222D))
    drawPath(dashPath, Color(0xFF334155), style = Stroke(width = 2.dp.toPx()))

    // 5. Tissue Box on Center Dash ("tissue" / "tissue rtexture.png")
    drawRoundRect(
        color = Color(0xFFE2E8F0),
        topLeft = Offset(w * 0.42f, dashTopY - 14.dp.toPx()),
        size = Size(54.dp.toPx(), 20.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
    )

    // 6. Three Circular Hino RM 2 Instrument Dials ("circle.009" km/h, "circle.010" RPM, "circle.011" Air Pressure)
    val binnacleCenterX = w * 0.68f
    val dialY = dashTopY + 34.dp.toPx()
    val dialRadii = floatArrayOf(24.dp.toPx(), 24.dp.toPx(), 20.dp.toPx())
    val dialOffsets = floatArrayOf(-58.dp.toPx(), 0f, 54.dp.toPx())
    val dialColors = arrayOf(Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFFFBBF24))

    for (i in 0..2) {
        val cx = binnacleCenterX + dialOffsets[i]
        val r = dialRadii[i]
        drawCircle(Color(0xFF090D16), radius = r, center = Offset(cx, dialY))
        drawCircle(dialColors[i].copy(alpha = 0.7f), radius = r, center = Offset(cx, dialY), style = Stroke(width = 2.dp.toPx()))
        val needleFrac = when (i) {
            0 -> (state.speedKmh / 100f).coerceIn(0f, 1f)
            1 -> (0.22f + (state.speedKmh / 90f) * 0.65f).coerceIn(0.2f, 0.95f)
            else -> 0.78f
        }
        val nAngle = Math.toRadians((-135.0 + needleFrac * 240.0)).toFloat()
        drawLine(
            color = Color(0xFFEF4444),
            start = Offset(cx, dialY),
            end = Offset(cx + sin(nAngle) * (r * 0.78f), dialY - cos(nAngle) * (r * 0.78f)),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

