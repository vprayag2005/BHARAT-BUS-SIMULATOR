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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.RoadJunctionBellmouth
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.viewmodel.SimUiState
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
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
    val nearZ = 0.25f
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

internal class QuadFace3D(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val v3: Vec3,
    val color: Color,
    val strokeColor: Color? = Color(0x28000000),
    val biasDist: Float = 0f,
    var sortKey: Float = 0f,
    val nx: Float = 0f,
    val ny: Float = 0f,
    val nz: Float = 0f
)

internal val FaceDepthComparator = Comparator<QuadFace3D> { a, b ->
    b.sortKey.compareTo(a.sortKey)
}

internal class HinoRealMeshPoly(
    val partId: Int,
    val vertCount: Int,
    val cullMode: Int,
    val baseColor: Color,
    val biasDist: Float,
    val nx: Float,
    val ny: Float,
    val nz: Float,
    val cx: Float,
    val cy: Float,
    val cz: Float,
    val x0: Float,
    val y0: Float,
    val z0: Float,
    val x1: Float,
    val y1: Float,
    val z1: Float,
    val x2: Float,
    val y2: Float,
    val z2: Float,
    val x3: Float,
    val y3: Float,
    val z3: Float
)

private fun loadHinoRealMeshFromAssets(context: android.content.Context): List<HinoRealMeshPoly> {
    return try {
        val bytes = context.assets.open("hino_rm2_real_mesh.bin").use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val m0 = buf.get().toInt().toChar()
        val m1 = buf.get().toInt().toChar()
        val m2 = buf.get().toInt().toChar()
        val m3 = buf.get().toInt().toChar()
        if (m0 != 'H' || m1 != 'R' || m2 != 'M' || (m3 != '3' && m3 != '2')) return emptyList()
        val isV3 = (m3 == '3')
        val count = buf.int
        val out = ArrayList<HinoRealMeshPoly>(count)
        for (i in 0 until count) {
            val partId = buf.get().toInt() and 0xFF
            val vcnt = (buf.get().toInt() and 0xFF).coerceIn(3, 4)
            val cullMode = if (isV3) (buf.get().toInt() and 0xFF) else 0
            val r = buf.get().toInt() and 0xFF
            val g = buf.get().toInt() and 0xFF
            val b = buf.get().toInt() and 0xFF
            val a = buf.get().toInt() and 0xFF
            val bias = buf.float
            val nx = if (isV3) buf.float else 0f
            val ny = if (isV3) buf.float else 1f
            val nz = if (isV3) buf.float else 0f
            val x0 = buf.float
            val y0 = buf.float
            val z0 = buf.float
            val x1 = buf.float
            val y1 = buf.float
            val z1 = buf.float
            val x2 = buf.float
            val y2 = buf.float
            val z2 = buf.float
            val x3: Float
            val y3: Float
            val z3: Float
            val cx: Float
            val cy: Float
            val cz: Float
            if (vcnt == 4) {
                x3 = buf.float
                y3 = buf.float
                z3 = buf.float
                cx = (x0 + x1 + x2 + x3) * 0.25f
                cy = (y0 + y1 + y2 + y3) * 0.25f
                cz = (z0 + z1 + z2 + z3) * 0.25f
            } else {
                x3 = x2
                y3 = y2
                z3 = z2
                cx = (x0 + x1 + x2) * (1f / 3f)
                cy = (y0 + y1 + y2) * (1f / 3f)
                cz = (z0 + z1 + z2) * (1f / 3f)
            }
            val col = Color(r, g, b, a)
            out.add(
                HinoRealMeshPoly(
                    partId = partId,
                    vertCount = vcnt,
                    cullMode = cullMode,
                    baseColor = col,
                    biasDist = bias,
                    nx = nx, ny = ny, nz = nz,
                    cx = cx, cy = cy, cz = cz,
                    x0 = x0, y0 = y0, z0 = z0,
                    x1 = x1, y1 = y1, z1 = z1,
                    x2 = x2, y2 = y2, z2 = z2,
                    x3 = x3, y3 = y3, z3 = z3
                )
            )
        }
        out
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
fun Thrissur3DRoadViewport(
    state: SimUiState,
    onOrbitDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hinoRealMesh = remember(context) { loadHinoRealMeshFromAssets(context) }
    val renderFacesBuffer = remember { ArrayList<QuadFace3D>(18000) }
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
            .pointerInput(state.cameraMode) {
                if (state.cameraMode == CameraViewMode.Orbit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onOrbitDrag(dragAmount.x * 0.22f, dragAmount.y * 0.14f)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height

        // 1. Third-Person Follow Camera with Lerp Tracking, Road-Mesh Anti-Clipping & Cinematic FOV
        val busSin = sin(state.busHeadingRad)
        val busCos = cos(state.busHeadingRad)

        val lookTarget = state.camFocusPos
        val camEye = ThrissurSimViewModel.preventRoadMeshCameraClip(
            focusPos = lookTarget,
            candidateEye = state.camEyePos,
            busPos = state.busPos,
            busHeadingRad = state.busHeadingRad,
            cameraMode = state.cameraMode
        )

        val rawDirX = lookTarget.x - camEye.x
        val rawDirY = lookTarget.y - camEye.y
        val rawDirZ = lookTarget.z - camEye.z
        val dirLen = sqrt(rawDirX * rawDirX + rawDirY * rawDirY + rawDirZ * rawDirZ).coerceAtLeast(0.001f)
        val fwd = Vec3(rawDirX / dirLen, rawDirY / dirLen, rawDirZ / dirLen)

        // Orthonormal right = normalize(worldUp x fwd) where worldUp = (0, 1, 0)
        val rx = fwd.z
        val rz = -fwd.x
        val rLen = hypot(rx, rz).coerceAtLeast(0.001f)
        val right = Vec3(rx / rLen, 0f, rz / rLen)

        // Orthonormal up = fwd x right
        val ux = fwd.y * right.z - fwd.z * right.y
        val uy = fwd.z * right.x - fwd.x * right.z
        val uz = fwd.x * right.y - fwd.y * right.x
        val uLen = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.001f)
        val up = Vec3(ux / uLen, uy / uLen, uz / uLen)

        val pitchRad = asin((-fwd.y).coerceIn(-0.95f, 0.95f))

        // Aspect-aware consistent cinematic driving FOV
        val cinematicHalfFovRad = Math.toRadians((state.cameraFovDeg.coerceIn(50f, 70f) * 0.5f).toDouble())
        val fovScale = (h * 0.5f) / tan(cinematicHalfFovRad).toFloat()

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

        // 3. Collect Contiguous Visible Road Spans (frustum-culled to skip spans behind the camera)
        val maxDrawDistSq = 280f * 280f
        val spans = ArrayList<ContiguousRoadSpan>(64)
        val fwdX = fwd.x
        val fwdZ = fwd.z

        for (road in state.roads) {
            val frames = road.frames
            if (frames.size < 2) continue
            val chunkSize = 16
            var i = 0
            while (i < frames.size - 1) {
                var sIdx = i
                val eIdx = min(i + chunkSize, frames.size - 1)

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
                    val f0 = frames[sIdx]
                    val f1 = frames[eIdx]
                    val camZMid = dx * fwdX + dz * fwdZ
                    val camZ0 = (f0.pos.x - camEye.x) * fwdX + (f0.pos.z - camEye.z) * fwdZ
                    val camZ1 = (f1.pos.x - camEye.x) * fwdX + (f1.pos.z - camEye.z) * fwdZ
                    if (camZMid > -24f || camZ0 > -24f || camZ1 > -24f) {
                        spans.add(ContiguousRoadSpan(road, sIdx, eIdx, distSq))
                    }
                }
                i += chunkSize
            }
        }

        spans.sortWith { a, b -> b.avgDistSq.compareTo(a.avgDistSq) }
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

        // PASS 5: Render Player's Real Imported Hino RM 2 3D Mesh (HINO_RM2.obj + 6 Wheels + Eagle Livery)
        // Source: https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus
        // Creator: asrafullislamsiam | License: CGTrader Royalty Free License (No AI)
        if (state.cameraMode == CameraViewMode.Chase || state.cameraMode == CameraViewMode.Orbit) {
            drawHinoRm2Bus3D(
                rig = rig,
                path = sharedPath,
                state = state,
                hinoMesh = hinoRealMesh,
                facesBuffer = renderFacesBuffer,
                titlePaint = titlePaint,
                subPaint = subPaint
            )
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
    if (span.avgDistSq > 165f * 165f) return
    val road = span.road
    val frames = road.frames
    val yMark = 0.062f
    val sIdx = span.startIdx
    val eIdx = span.endIdx
    if (eIdx <= sIdx) return
    val step = if (span.avgDistSq > 75f * 75f) 2 else 1

    fun drawSegment3D(w0: Vec3, w1: Vec3, widthM: Float, color: Color) {
        val seg = rig.projectSegmentClipped(w0, w1) ?: return
        val dx = (w0.x + w1.x) * 0.5f - rig.eye.x
        val dz = (w0.z + w1.z) * 0.5f - rig.eye.z
        val avgCz = max(0.8f, dx * rig.forward.x + dz * rig.forward.z)
        val strokePx = (widthM * rig.fovScale / avgCz).coerceIn(1.6f, 12f)
        drawLine(
            color = color,
            start = seg.first,
            end = seg.second,
            strokeWidth = strokePx
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
        var i = sIdx
        while (i < eIdx) {
            val nextIdx = min(i + step, eIdx)
            val f0 = frames[i]
            val f1 = frames[nextIdx]
            val cov0 = coveredFn(f0)
            val cov1 = coveredFn(f1)
            if (cov0 && cov1) {
                i = nextIdx
                continue
            }
            if (dashed) {
                val period = 8.5f
                if ((f0.cumulativeDist % period) > 3.5f) {
                    i = nextIdx
                    continue
                }
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
            i = nextIdx
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

private val cachedSakthanTerminalFaces: List<QuadFace3D> by lazy {
    val faces = ArrayList<QuadFace3D>(640)

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
        // Top face (+Y normal): only visible when camera is above maxY
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
        // Underside ceiling face (-Y normal) for elevated canopies/gantries: only visible when camera is below minY
        if (minY >= 3.5f) {
            faces.add(
                QuadFace3D(
                    Vec3(minX, minY, minZ),
                    Vec3(maxX, minY, minZ),
                    Vec3(maxX, minY, maxZ),
                    Vec3(minX, minY, maxZ),
                    MatRoofUnderTruss,
                    stroke,
                    bias + 0.5f,
                    0f,
                    0f, -1f, 0f
                )
            )
        }
        // South face (-Z normal): only visible when camera Z < minZ
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
        // North face (+Z normal): only visible when camera Z > maxZ
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
        // West face (-X normal): only visible when camera X < minX
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
        // East face (+X normal): only visible when camera X > maxX
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

    // 2. High-Clearance Galvanized Terminal Canopy segmented per Bay (10 Bay spans across X = -23.0m..51.5m)
    val canopySpanW = (51.5f - (-23.0f)) / 10f
    for (sIdx in 0 until 10) {
        val cx0 = -23.0f + sIdx * canopySpanW
        val cx1 = cx0 + canopySpanW
        addBox3D(
            cx0, 6.20f, -185.5f,
            cx1, 6.85f, -160.5f,
            topCol = MatRoofGalvanized,
            sideCol = MatSakthanFasciaOrange,
            frontBackCol = MatSakthanFasciaOrange,
            bias = 2.2f
        )
    }

    // 3. South Intercity Concourse Canopy & Boarding Platforms (Bays 11–20, segmented per Bay)
    for (sIdx in 0 until 10) {
        val cx0 = -23.0f + sIdx * canopySpanW
        val cx1 = cx0 + canopySpanW
        addBox3D(
            cx0, 0.06f, -218.5f,
            cx1, 0.34f, -214.5f,
            topCol = MatPlatformTileLight,
            sideCol = MatCurbYellow,
            frontBackCol = MatCurbBlack
        )
        addBox3D(
            cx0, 6.20f, -217.5f,
            cx1, 6.85f, -197.0f,
            topCol = MatRoofGalvanized,
            sideCol = MatSakthanFasciaOrange,
            frontBackCol = MatSakthanFasciaOrange,
            bias = 2.2f
        )
    }

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
    val exitZ = -140.0f
    addBox3D(-22.0f, 0.06f, exitZ - 0.45f, -3.8f, 1.45f, exitZ + 0.35f, MatCreamPlaster, MatTerracottaDark, MatTerracottaPillar)
    addBox3D(37.8f, 0.06f, exitZ - 0.45f, 48.0f, 1.45f, exitZ + 0.35f, MatCreamPlaster, MatTerracottaDark, MatTerracottaPillar)
    addBox3D(47.3f, 0.06f, -218.0f, 48.0f, 1.45f, exitZ, MatCreamPlaster, MatTerracottaPillar, MatTerracottaDark)

    // 7. Full-Span Overhead Gantry Columns
    addBox3D(-3.8f, 0.06f, exitZ - 0.38f, -3.0f, 6.8f, exitZ + 0.38f, MatTerracottaDark, MatTerracottaPillar)
    addBox3D(37.0f, 0.06f, exitZ - 0.38f, 37.8f, 6.8f, exitZ + 0.38f, MatTerracottaDark, MatTerracottaPillar)
    addBox3D(-3.8f, 5.45f, exitZ - 0.30f, 37.8f, 6.65f, exitZ + 0.30f, MatRoofUnderTruss, Color(0xFF1E293B))

    faces
}

private val sakthanSortBuffer = ArrayList<QuadFace3D>(640)

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
    val dxT = camEye.x - 16f
    val dzT = camEye.z - (-178f)
    if (dxT * dxT + dzT * dzT > 260f * 260f) return

    sakthanSortBuffer.clear()
    val staticFaces = cachedSakthanTerminalFaces
    val eyeX = camEye.x
    val eyeY = camEye.y
    val eyeZ = camEye.z
    val fwdX = rig.forward.x
    val fwdY = rig.forward.y
    val fwdZ = rig.forward.z
    for (i in 0 until staticFaces.size) {
        val face = staticFaces[i]
        val cx = (face.v0.x + face.v1.x + face.v2.x + face.v3.x) * 0.25f - eyeX
        val cy = (face.v0.y + face.v1.y + face.v2.y + face.v3.y) * 0.25f - eyeY
        val cz = (face.v0.z + face.v1.z + face.v2.z + face.v3.z) * 0.25f - eyeZ
        if (cx * fwdX + cy * fwdY + cz * fwdZ < -14f) continue
        // Outward-normal backface culling so back walls and hidden roof tops never draw over front walls
        if ((face.nx != 0f || face.ny != 0f || face.nz != 0f) &&
            (cx * face.nx + cy * face.ny + cz * face.nz >= 0f)
        ) {
            continue
        }
        face.sortKey = kotlin.math.sqrt(cx * cx + cy * cy + cz * cz) + face.biasDist
        sakthanSortBuffer.add(face)
    }

    sakthanSortBuffer.sortWith(FaceDepthComparator)

    for (i in 0 until sakthanSortBuffer.size) {
        val face = sakthanSortBuffer[i]
        if (rig.buildClippedQuadPath(path, face.v0, face.v1, face.v2, face.v3)) {
            drawPath(path, face.color)
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
 * Renders the CGTrader Hino RM 2 Bus ("EAGLE" / "BUS LOVER" 12m Rear-Engine Intercity AC Coach) in 3D.
 *
 * Asset Provenance & License Compliance:
 * - Source URL : https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus
 * - Creator    : asrafullislamsiam (CGTrader)
 * - License    : CGTrader Royalty Free License (No AI)
 * - Details    : Pearl White aerodynamic coachwork ("main body", "Eagle paint test.png", "Rm 2 paint .png")
 *                with beveled upper roof crown & low-profile silver Denso rooftop AC pod ("ac"),
 *                diagonal Magenta/Orchid-Purple front ribbon & 4 concentric sweeping rear arcs,
 *                "EAGLE" & "RM-2 Exclusive" side branding, 4 black recessed luggage locker latches,
 *                sculpted matte charcoal-black lower front/rear bumpers with twin circular projector
 *                headlamps per side, chrome Hino "H" oval emblem with dual air-intake slots,
 *                "BUS LOVER" / "WinS" / "RM" front decals, panoramic front glass ("front glass")
 *                with 4 crimson-red scalloped/pleated curtains, curved black dashboard cowl with
 *                center tissue box ("tissue"), two-tone cream & dark-brown luxury passenger seats ("seat"),
 *                and high-mount black twin-arm aerodynamic wing mirrors.
 */
private fun DrawScope.drawHinoRm2Bus3D(
    rig: CameraRig,
    path: Path,
    state: SimUiState,
    hinoMesh: List<HinoRealMeshPoly>,
    facesBuffer: ArrayList<QuadFace3D>,
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

    // Camera position in bus-local coordinates for backface culling & front/rear/side decal visibility
    val eyeDx = rig.eye.x - pos.x
    val eyeDy = rig.eye.y - pos.y
    val eyeDz = rig.eye.z - pos.z
    val camLocalX = eyeDx * c - eyeDz * s
    val camLocalY = eyeDy
    val camLocalZ = eyeDx * s + eyeDz * c

    val halfW = 1.24f
    val halfL = 5.80f

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

    // 2. Soft Ambient Contact Shadow under Chassis ("base" / "wide_suspetion")
    val sh0 = busWorld(-1.36f, 0.064f, -6.38f)
    val sh1 = busWorld(1.36f, 0.064f, -6.38f)
    val sh2 = busWorld(1.36f, 0.064f, 5.92f)
    val sh3 = busWorld(-1.36f, 0.064f, 5.92f)
    if (rig.buildClippedQuadPath(path, sh0, sh1, sh2, sh3)) {
        drawPath(path, Color(0x9E070A0F))
    }

    // 3. Transform & Render the Actual Imported 3D Mesh of the Hino RM 2 Bus
    //    (Compiled from HINO_RM2.obj + Mesh_Hino_Wheel_0..5.asset + Eagle paint test.png + Rm 2 paint .png + Eagle glass.png)
    val frontSteerRad = state.steerInput * 0.45f
    val ws = sin(frontSteerRad)
    val wc = cos(frontSteerRad)

    val brakeActive = state.brakeInput > 0.05f || state.isParkBrakeActive
    val revActive = state.gear == com.bharatbussim.game.model.TransmissionGear.Reverse
    val leftBlinkOn = state.blinkerPhase && (state.turnLeftOn || state.hazardsOn)
    val rightBlinkOn = state.blinkerPhase && (state.turnRightOn || state.hazardsOn)

    val hlActiveCol = Color(0xFFFFFFFF)
    val brakeActiveCol = Color(0xFFFF1E1E)
    val revActiveCol = Color(0xFFFFFFFF)
    val blinkActiveCol = Color(0xFFFBBF24)

    fun transformWheelOrStatic(partId: Int, lx: Float, ly: Float, lz: Float): Vec3 {
        if (partId == 6) {
            // PART_WHEEL_FL hub at (-1.02f, 0.52f, 3.00f)
            val dx = lx - (-1.02f)
            val dz = lz - 3.00f
            val rx = -1.02f + dx * wc + dz * ws
            val rz = 3.00f - dx * ws + dz * wc
            return busWorld(rx, ly, rz)
        } else if (partId == 7) {
            // PART_WHEEL_FR hub at (1.02f, 0.52f, 3.00f)
            val dx = lx - 1.02f
            val dz = lz - 3.00f
            val rx = 1.02f + dx * wc + dz * ws
            val rz = 3.00f - dx * ws + dz * wc
            return busWorld(rx, ly, rz)
        }
        return busWorld(lx, ly, lz)
    }

    facesBuffer.clear()
    val eyeX = rig.eye.x
    val eyeY = rig.eye.y
    val eyeZ = rig.eye.z
    val hideInteriorFromRear = camLocalZ < -0.5f && kotlin.math.abs(camLocalX) < 1.38f

    for (i in 0 until hinoMesh.size) {
        val poly = hinoMesh[i]
        val cm = poly.cullMode
        if (cm == 2 && hideInteriorFromRear) {
            continue
        } else if (cm == 1) {
            val dot = (poly.cx - camLocalX) * poly.nx + (poly.cy - camLocalY) * poly.ny + (poly.cz - camLocalZ) * poly.nz
            if (dot >= 0.08f) continue
        }
        val pid = poly.partId
        val v0 = transformWheelOrStatic(pid, poly.x0, poly.y0, poly.z0)
        val v1 = transformWheelOrStatic(pid, poly.x1, poly.y1, poly.z1)
        val v2 = transformWheelOrStatic(pid, poly.x2, poly.y2, poly.z2)
        val v3 = if (poly.vertCount == 4) {
            transformWheelOrStatic(pid, poly.x3, poly.y3, poly.z3)
        } else {
            v2
        }

        val dynCol = when (pid) {
            1 -> if (state.headlightsOn) hlActiveCol else poly.baseColor
            2 -> if (brakeActive) brakeActiveCol else poly.baseColor
            3 -> if (revActive) revActiveCol else poly.baseColor
            4 -> if (leftBlinkOn) blinkActiveCol else poly.baseColor
            5 -> if (rightBlinkOn) blinkActiveCol else poly.baseColor
            else -> poly.baseColor
        }

        val cx = (v0.x + v1.x + v2.x + v3.x) * 0.25f - eyeX
        val cy = (v0.y + v1.y + v2.y + v3.y) * 0.25f - eyeY
        val cz = (v0.z + v1.z + v2.z + v3.z) * 0.25f - eyeZ
        val sortKey = kotlin.math.sqrt(cx * cx + cy * cy + cz * cz) + poly.biasDist

        facesBuffer.add(
            QuadFace3D(
                v0 = v0,
                v1 = v1,
                v2 = v2,
                v3 = v3,
                color = dynCol,
                strokeColor = null,
                biasDist = poly.biasDist,
                sortKey = sortKey
            )
        )
    }

    facesBuffer.sortWith(FaceDepthComparator)

    for (i in 0 until facesBuffer.size) {
        val face = facesBuffer[i]
        if (rig.buildClippedQuadPath(path, face.v0, face.v1, face.v2, face.v3)) {
            drawPath(path, face.color)
        }
    }

    // 4. Crisp Perspective-Aligned 3D Typography Decals on the Imported Mesh Surfaces
    //    ("BUS LOVER", "WinS", "RM", "EAGLE", "RM-2 Exclusive")
    val eagleMagenta = Color(0xFFB0548B)
    val eagleMagentaDeep = Color(0xFF963B72)
    val charcoalBumper = Color(0xFF1C1F26)
    val nc = drawContext.canvas.nativeCanvas

    fun drawCoachDecal(
        worldLeft: Vec3,
        worldRight: Vec3,
        worldTop: Vec3,
        worldBottom: Vec3,
        text: String,
        color: Color
    ) {
        val pL = rig.projectPoint(worldLeft) ?: return
        val pR = rig.projectPoint(worldRight) ?: return
        val pT = rig.projectPoint(worldTop) ?: return
        val pB = rig.projectPoint(worldBottom) ?: return
        val pixW = hypot(pR.x - pL.x, pR.y - pL.y)
        val pixH = hypot(pB.x - pT.x, pB.y - pT.y)
        if (pixW < 14f || pixH < 5f) return

        val cx = (pL.x + pR.x) * 0.5f
        val cy = (pT.y + pB.y) * 0.5f
        var ang = Math.toDegrees(atan2((pR.y - pL.y).toDouble(), (pR.x - pL.x).toDouble())).toFloat()
        if (ang > 90f) ang -= 180f
        if (ang < -90f) ang += 180f

        val textSizePx = min(pixH * 0.85f, pixW / (text.length.coerceAtLeast(2) * 0.56f)).coerceIn(6.5f, 34f)
        titlePaint.color = color.toArgb()
        titlePaint.textSize = textSizePx
        nc.save()
        nc.translate(cx, cy)
        nc.rotate(ang)
        nc.drawText(text, 0f, textSizePx * 0.34f, titlePaint)
        nc.restore()
    }

    // Front Fascia Decals on HINO_RM2.obj front mask (Z = +5.81m)
    if (camLocalZ > 2.2f) {
        val fZ = 5.81f
        drawCoachDecal(
            busWorld(-0.54f, 1.32f, fZ),
            busWorld(0.54f, 1.32f, fZ),
            busWorld(0f, 1.43f, fZ),
            busWorld(0f, 1.21f, fZ),
            "BUS LOVER",
            eagleMagentaDeep
        )
        drawCoachDecal(
            busWorld(1.06f, 1.24f, 5.66f),
            busWorld(0.68f, 1.24f, 5.76f),
            busWorld(0.87f, 1.32f, 5.71f),
            busWorld(0.87f, 1.16f, 5.71f),
            "WinS",
            charcoalBumper
        )
    }

    // Left & Right Side Body Decals on HINO_RM2.obj side panels (X = +/-1.235m)
    for (side in intArrayOf(-1, 1)) {
        val isVisibleSide = (side == -1 && camLocalX < -0.85f && camLocalZ > -5.2f) ||
                (side == 1 && camLocalX > 0.85f && camLocalZ > -5.2f)
        if (isVisibleSide) {
            val sx = side * 1.242f

            // 1. Sleek diagonal magenta sash from rocker up through front pillar (Matching Blender Model)
            val sash0 = busWorld(sx, 0.72f, 3.60f)
            val sash1 = busWorld(sx, 0.96f, 3.60f)
            val sash2 = busWorld(sx, 2.45f, 2.10f)
            val sash3 = busWorld(sx, 2.21f, 2.10f)
            if (rig.buildClippedQuadPath(path, sash0, sash1, sash2, sash3)) {
                drawPath(path, eagleMagenta)
            }

            // 2. 4 Aerodynamic radiating curved swooshes over rear wheels (Matching Blender Model)
            val arcOffsets = floatArrayOf(0f, 0.28f, 0.56f, 0.84f)
            for (ao in arcOffsets) {
                val a0 = busWorld(sx, 0.70f, -1.80f - ao * 0.75f)
                val a1 = busWorld(sx, 0.82f, -1.80f - ao * 0.75f)
                val a2 = busWorld(sx, 1.82f + ao * 0.18f, -3.85f - ao * 0.85f)
                val a3 = busWorld(sx, 1.70f + ao * 0.18f, -3.85f - ao * 0.85f)
                if (rig.buildClippedQuadPath(path, a0, a1, a2, a3)) {
                    drawPath(path, eagleMagenta)
                }
            }

            // 3. Bold "EAGLE" emblem on center luggage locker door
            drawCoachDecal(
                busWorld(sx, 1.62f, 1.55f),
                busWorld(sx, 1.62f, -0.15f),
                busWorld(sx, 1.90f, 0.70f),
                busWorld(sx, 1.34f, 0.70f),
                "EAGLE",
                eagleMagenta
            )

            // 2. Clean window branding on tinted glass (Matching Blender Model)
            val glassX = side * 1.218f
            drawCoachDecal(
                busWorld(glassX, 2.70f, 2.10f),
                busWorld(glassX, 2.70f, 0.95f),
                busWorld(glassX, 2.82f, 1.50f),
                busWorld(glassX, 2.58f, 1.50f),
                "RM-2 Exclusive",
                Color(0xEEFFFFFF)
            )
            drawCoachDecal(
                busWorld(glassX, 2.70f, 0.65f),
                busWorld(glassX, 2.70f, -0.50f),
                busWorld(glassX, 2.82f, 0.05f),
                busWorld(glassX, 2.58f, 0.05f),
                "EAGLE",
                Color(0xEEFFFFFF)
            )
            drawCoachDecal(
                busWorld(glassX, 2.70f, -0.80f),
                busWorld(glassX, 2.70f, -1.95f),
                busWorld(glassX, 2.82f, -1.40f),
                busWorld(glassX, 2.58f, -1.40f),
                "RM-2 Exclusive",
                Color(0xEEFFFFFF)
            )
            drawCoachDecal(
                busWorld(glassX, 2.70f, -2.25f),
                busWorld(glassX, 2.70f, -3.40f),
                busWorld(glassX, 2.82f, -2.80f),
                busWorld(glassX, 2.58f, -2.80f),
                "EAGLE",
                Color(0xEEFFFFFF)
            )
        }
    }

    // Rear Tailgate Decals & Defroster on HINO_RM2.obj rear (Z = -6.195m)
    if (camLocalZ < -1.8f && kotlin.math.abs(camLocalX) < 1.4f) {
        val rZ = -6.195f

        // Subtle copper defroster heating lines across the rear window
        for (step in 1..4) {
            val dy = 2.25f + step * 0.17f
            val seg = rig.projectSegmentClipped(busWorld(-0.95f, dy, -6.182f), busWorld(0.95f, dy, -6.182f))
            if (seg != null) {
                drawLine(Color(0x33FF9966), seg.first, seg.second, strokeWidth = 1.2f)
            }
        }

        drawCoachDecal(
            busWorld(-0.55f, 2.22f, rZ),
            busWorld(0.55f, 2.22f, rZ),
            busWorld(0f, 2.34f, rZ),
            busWorld(0f, 2.10f, rZ),
            "BUS LOVER",
            Color(0xFFF8FAFC)
        )
        drawCoachDecal(
            busWorld(-0.48f, 1.48f, rZ),
            busWorld(0.48f, 1.48f, rZ),
            busWorld(0f, 1.62f, rZ),
            busWorld(0f, 1.34f, rZ),
            "EAGLE",
            eagleMagentaDeep
        )
    }

    // 5. Animated Dual Windshield Wipers on Raked Front Windshield (Z = +5.65m -> +5.38m)
    val wiperSweep = if (state.wipersOn) sin(state.wiperPhaseRad) * 0.42f else -0.32f
    for (pivotX in floatArrayOf(-0.45f, 0.45f)) {
        val pBase = busWorld(pivotX, 1.54f, 5.68f)
        val dy = cos(wiperSweep) * 0.66f
        val pTip = busWorld(pivotX + sin(wiperSweep) * 0.60f, 1.54f + dy, 5.68f - dy * 0.42f)
        val seg = rig.projectSegmentClipped(pBase, pTip)
        if (seg != null) {
            drawLine(Color(0xFF0F172A), seg.first, seg.second, strokeWidth = 2.6f, cap = StrokeCap.Round)
        }
    }
}

/**
 * Renders the 3D Hino RM 2 Driver Cockpit Interior when [CameraViewMode.Cockpit] is active:
 * - 4 Crimson-Red Scalloped / Pleated Windshield Curtains across the top header (Matching Image 1)
 * - Left & Right Coach A-pillars
 * - Wrap-around Hino RM 2 charcoal dashboard ("dashboard")
 * - 3 illuminated circular instrument gauges ("circle.009" Speedo, "circle.010" RPM, "circle.011" Air Pressure)
 * - Tissue Box ("tissue") on center dash & Animated dual windshield wipers
 */
private fun DrawScope.drawHinoRm2Cockpit3D(state: SimUiState) {
    val w = size.width
    val h = size.height

    // 1. Upper Windshield Tint & 4 Crimson-Red Scalloped Curtains (Matching Image 1)
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to Color(0xCC090D16),
            0.75f to Color(0x440D1526),
            1.0f to Color.Transparent,
            startY = 0f,
            endY = h * 0.16f
        ),
        topLeft = Offset.Zero,
        size = Size(w, h * 0.16f)
    )
    val scallopCount = 4
    val curtainLeft = w * 0.06f
    val curtainRight = w * 0.94f
    val scallopW = (curtainRight - curtainLeft) / scallopCount
    val curtainDrop = h * 0.115f
    for (i in 0 until scallopCount) {
        val x0 = curtainLeft + i * scallopW
        val x1 = x0 + scallopW
        val xMid = (x0 + x1) * 0.5f
        val scPath = Path().apply {
            moveTo(x0, 0f)
            quadraticTo(xMid, curtainDrop * 1.45f, x1, 0f)
            close()
        }
        drawPath(scPath, Color(0xFFDC2626))
        drawPath(scPath, Color(0xFF991B1B), style = Stroke(width = 2.dp.toPx()))

        val innerPath = Path().apply {
            moveTo(x0 + scallopW * 0.16f, 0f)
            quadraticTo(xMid, curtainDrop * 0.95f, x1 - scallopW * 0.16f, 0f)
            close()
        }
        drawPath(innerPath, Color(0xFFEF4444))
    }

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

    // 5. Beige & White Tissue Box on Center Dash ("tissue" / "tissue rtexture.png" from Image 1)
    drawRoundRect(
        color = Color(0xFFDCC7A1),
        topLeft = Offset(w * 0.42f, dashTopY - 14.dp.toPx()),
        size = Size(54.dp.toPx(), 20.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(w * 0.435f, dashTopY - 22.dp.toPx()),
        size = Size(24.dp.toPx(), 10.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
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

    // 7. Left & Right Exterior Side Mirrors with Rear-view Road Reflections (Matching Reference Image 2)
    val mirrorCornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
    val mirrorInnerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())

    // Left Mirror Mounting Arm & Housing
    val leftMirrorW = w * 0.078f
    val leftMirrorH = h * 0.26f
    val leftMirrorX = w * 0.062f
    val leftMirrorY = h * 0.038f

    // Left mounting bracket arm (attached to A-pillar)
    val armPathL = Path().apply {
        moveTo(0f, leftMirrorY + leftMirrorH * 0.15f)
        lineTo(leftMirrorX + leftMirrorW * 0.2f, leftMirrorY - 8.dp.toPx())
        lineTo(leftMirrorX + leftMirrorW * 0.6f, leftMirrorY)
        lineTo(leftMirrorX + leftMirrorW * 0.2f, leftMirrorY + 6.dp.toPx())
        close()
    }
    drawPath(armPathL, Color(0xFF0F172A))
    drawLine(
        color = Color(0xFF1E293B),
        start = Offset(0f, leftMirrorY + leftMirrorH * 0.45f),
        end = Offset(leftMirrorX, leftMirrorY + leftMirrorH * 0.55f),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Left Mirror Outer Casing (Kerala Coach Bright Green with Yellow Trim)
    drawRoundRect(
        color = Color(0xFF15803D),
        topLeft = Offset(leftMirrorX - 3.dp.toPx(), leftMirrorY - 3.dp.toPx()),
        size = Size(leftMirrorW + 6.dp.toPx(), leftMirrorH + 6.dp.toPx()),
        cornerRadius = mirrorCornerRadius
    )
    drawRoundRect(
        color = Color(0xFF84CC16),
        topLeft = Offset(leftMirrorX - 1.dp.toPx(), leftMirrorY - 1.dp.toPx()),
        size = Size(leftMirrorW + 2.dp.toPx(), leftMirrorH + 2.dp.toPx()),
        cornerRadius = mirrorCornerRadius,
        style = Stroke(width = 2.dp.toPx())
    )

    // Left Mirror Bezel & Glass
    val glassBezelL = 4.dp.toPx()
    val glassXL = leftMirrorX + glassBezelL
    val glassYL = leftMirrorY + glassBezelL
    val glassWL = leftMirrorW - glassBezelL * 2f
    val glassHL = leftMirrorH - glassBezelL * 2f

    drawRoundRect(
        color = Color(0xFF090D16),
        topLeft = Offset(leftMirrorX, leftMirrorY),
        size = Size(leftMirrorW, leftMirrorH),
        cornerRadius = mirrorCornerRadius
    )

    val mirrorHorizonYL = glassYL + glassHL * 0.45f
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color(0xFF7DD3FC),
            0.45f to Color(0xFFBAE6FD),
            0.451f to Color(0xFF475569),
            1.0f to Color(0xFF1E293B),
            startY = glassYL,
            endY = glassYL + glassHL
        ),
        topLeft = Offset(glassXL, glassYL),
        size = Size(glassWL, glassHL),
        cornerRadius = mirrorInnerRadius
    )

    // Rear Road Ribbon & Lane in Left Mirror
    val mirrorVanishingXL = glassXL + glassWL * 0.52f
    val mirrorRoadL = Path().apply {
        moveTo(glassXL + glassWL * 0.28f, glassYL + glassHL)
        lineTo(glassXL + glassWL * 0.82f, glassYL + glassHL)
        lineTo(mirrorVanishingXL + glassWL * 0.08f, mirrorHorizonYL)
        lineTo(mirrorVanishingXL - glassWL * 0.08f, mirrorHorizonYL)
        close()
    }
    drawPath(mirrorRoadL, Color(0xFF334155))

    drawLine(
        color = Color(0xFFFACC15),
        start = Offset(glassXL + glassWL * 0.55f, glassYL + glassHL),
        end = Offset(mirrorVanishingXL, mirrorHorizonYL),
        strokeWidth = 1.8.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Bus Left Body Flank visible on inner mirror edge
    val busFlankL = Path().apply {
        moveTo(glassXL + glassWL, glassYL + glassHL * 0.25f)
        lineTo(glassXL + glassWL * 0.86f, glassYL + glassHL * 0.35f)
        lineTo(glassXL + glassWL * 0.78f, glassYL + glassHL)
        lineTo(glassXL + glassWL, glassYL + glassHL)
        close()
    }
    drawPath(busFlankL, Color(0xFFF1F5F9))
    val busFlankDecalL = Path().apply {
        moveTo(glassXL + glassWL, glassYL + glassHL * 0.65f)
        lineTo(glassXL + glassWL * 0.81f, glassYL + glassHL * 0.72f)
        lineTo(glassXL + glassWL * 0.78f, glassYL + glassHL * 0.95f)
        lineTo(glassXL + glassWL, glassYL + glassHL * 0.95f)
        close()
    }
    drawPath(busFlankDecalL, Color(0xFF7C3AED))

    // Glass Specular Glint
    drawLine(
        brush = Brush.linearGradient(
            0.0f to Color.Transparent,
            0.5f to Color(0x55FFFFFF),
            1.0f to Color.Transparent,
            start = Offset(glassXL, glassYL),
            end = Offset(glassXL + glassWL, glassYL + glassHL * 0.6f)
        ),
        start = Offset(glassXL, glassYL),
        end = Offset(glassXL + glassWL, glassYL + glassHL * 0.6f),
        strokeWidth = 2.dp.toPx()
    )

    // --- Right Mirror Mounting Arm & Housing (Hanging from header, matching Reference Image 2) ---
    val rightMirrorW = w * 0.078f
    val rightMirrorH = h * 0.26f
    val rightMirrorX = w * 0.645f
    val rightMirrorY = h * 0.015f

    // Right mounting bracket arm (attached to windshield header)
    val armPathR = Path().apply {
        moveTo(rightMirrorX + rightMirrorW * 0.5f - 6.dp.toPx(), 0f)
        lineTo(rightMirrorX + rightMirrorW * 0.5f + 6.dp.toPx(), 0f)
        lineTo(rightMirrorX + rightMirrorW * 0.6f, rightMirrorY + 8.dp.toPx())
        lineTo(rightMirrorX + rightMirrorW * 0.4f, rightMirrorY + 8.dp.toPx())
        close()
    }
    drawPath(armPathR, Color(0xFF0F172A))
    drawLine(
        color = Color(0xFF1E293B),
        start = Offset(rightMirrorX + rightMirrorW * 0.5f, 0f),
        end = Offset(rightMirrorX + rightMirrorW * 0.5f, rightMirrorY + 10.dp.toPx()),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Right Mirror Outer Casing (Kerala Coach Bright Green)
    drawRoundRect(
        color = Color(0xFF15803D),
        topLeft = Offset(rightMirrorX - 3.dp.toPx(), rightMirrorY - 3.dp.toPx()),
        size = Size(rightMirrorW + 6.dp.toPx(), rightMirrorH + 6.dp.toPx()),
        cornerRadius = mirrorCornerRadius
    )
    drawRoundRect(
        color = Color(0xFF84CC16),
        topLeft = Offset(rightMirrorX - 1.dp.toPx(), rightMirrorY - 1.dp.toPx()),
        size = Size(rightMirrorW + 2.dp.toPx(), rightMirrorH + 2.dp.toPx()),
        cornerRadius = mirrorCornerRadius,
        style = Stroke(width = 2.dp.toPx())
    )

    // Right Mirror Bezel & Glass
    val glassXR = rightMirrorX + glassBezelL
    val glassYR = rightMirrorY + glassBezelL
    val glassWR = rightMirrorW - glassBezelL * 2f
    val glassHR = rightMirrorH - glassBezelL * 2f

    drawRoundRect(
        color = Color(0xFF090D16),
        topLeft = Offset(rightMirrorX, rightMirrorY),
        size = Size(rightMirrorW, rightMirrorH),
        cornerRadius = mirrorCornerRadius
    )

    val mirrorHorizonYR = glassYR + glassHR * 0.45f
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color(0xFF7DD3FC),
            0.45f to Color(0xFFBAE6FD),
            0.451f to Color(0xFF475569),
            1.0f to Color(0xFF1E293B),
            startY = glassYR,
            endY = glassYR + glassHR
        ),
        topLeft = Offset(glassXR, glassYR),
        size = Size(glassWR, glassHR),
        cornerRadius = mirrorInnerRadius
    )

    // Rear Road Ribbon & Lane in Right Mirror
    val mirrorVanishingXR = glassXR + glassWR * 0.48f
    val mirrorRoadR = Path().apply {
        moveTo(glassXR + glassWR * 0.18f, glassYR + glassHR)
        lineTo(glassXR + glassWR * 0.72f, glassYR + glassHR)
        lineTo(mirrorVanishingXR + glassWR * 0.08f, mirrorHorizonYR)
        lineTo(mirrorVanishingXR - glassWR * 0.08f, mirrorHorizonYR)
        close()
    }
    drawPath(mirrorRoadR, Color(0xFF334155))

    drawLine(
        color = Color(0xFFFACC15),
        start = Offset(glassXR + glassWR * 0.45f, glassYR + glassHR),
        end = Offset(mirrorVanishingXR, mirrorHorizonYR),
        strokeWidth = 1.8.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Bus Right Body Flank on inner mirror edge
    val busFlankR = Path().apply {
        moveTo(glassXR, glassYR + glassHR * 0.25f)
        lineTo(glassXR + glassWR * 0.14f, glassYR + glassHR * 0.35f)
        lineTo(glassXR + glassWR * 0.22f, glassYR + glassHR)
        lineTo(glassXR, glassYR + glassHR)
        close()
    }
    drawPath(busFlankR, Color(0xFFF1F5F9))
    val busFlankDecalR = Path().apply {
        moveTo(glassXR, glassYR + glassHR * 0.65f)
        lineTo(glassXR + glassWR * 0.19f, glassYR + glassHR * 0.72f)
        lineTo(glassXR + glassWR * 0.22f, glassYR + glassHR * 0.95f)
        lineTo(glassXR, glassYR + glassHR * 0.95f)
        close()
    }
    drawPath(busFlankDecalR, Color(0xFF7C3AED))

    // Glass Specular Glint
    drawLine(
        brush = Brush.linearGradient(
            0.0f to Color.Transparent,
            0.5f to Color(0x55FFFFFF),
            1.0f to Color.Transparent,
            start = Offset(glassXR, glassYR),
            end = Offset(glassXR + glassWR, glassYR + glassHR * 0.6f)
        ),
        start = Offset(glassXR, glassYR),
        end = Offset(glassXR + glassWR, glassYR + glassHR * 0.6f),
        strokeWidth = 2.dp.toPx()
    )

    // 8. GPS Navigation Screen on Center-Left Dashboard (Matching Reference Image 2)
    val gpsW = w * 0.14f
    val gpsH = h * 0.14f
    val gpsX = w * 0.38f
    val gpsY = dashTopY + 12.dp.toPx()
    drawRoundRect(
        color = Color(0xFF090D16),
        topLeft = Offset(gpsX - 2.dp.toPx(), gpsY - 2.dp.toPx()),
        size = Size(gpsW + 4.dp.toPx(), gpsH + 4.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF020617),
        topLeft = Offset(gpsX, gpsY),
        size = Size(gpsW, gpsH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
    )
    val gpsPath = Path().apply {
        moveTo(gpsX + gpsW * 0.15f, gpsY + gpsH * 0.85f)
        quadraticTo(gpsX + gpsW * 0.5f, gpsY + gpsH * 0.6f, gpsX + gpsW * 0.85f, gpsY + gpsH * 0.2f)
    }
    drawPath(gpsPath, Color(0xFF38BDF8), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    val gpsArrow = Path().apply {
        moveTo(gpsX + gpsW * 0.45f, gpsY + gpsH * 0.65f)
        lineTo(gpsX + gpsW * 0.52f, gpsY + gpsH * 0.52f)
        lineTo(gpsX + gpsW * 0.55f, gpsY + gpsH * 0.68f)
        close()
    }
    drawPath(gpsArrow, Color(0xFFF97316))
}

