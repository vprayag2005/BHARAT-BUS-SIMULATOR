package com.bharatbussim.game.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.HeadlightBeamMode
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.RoadJunctionBellmouth
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.ui.theme.PremiumFonts
import com.bharatbussim.game.viewmodel.SimUiState
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Authentic Kerala / IRC Highway & Sakthan Stand Material Palette (Strictly Zero Trees)
private val SkyZenith = Color(0xFF2B5B8C)
private val SkyMid = Color(0xFF6394BF)
private val SkyHorizonHaze = Color(0xFFC4D7E8)

private val GroundFarVerge = Color(0xFF6B6559)
private val GroundMidVerge = Color(0xFF5B554A)
private val GroundNearEarth = Color(0xFF4F4A40)

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

// Sakthan Thampuran Bus Stand Architectural Palette (Zone 1 Spec)
private val MatTerracottaPillar = Color(0xFFA83E28)
private val MatTerracottaDark = Color(0xFF7D2B19)
private val MatSakthanFasciaOrange = Color(0xFFC94A29)
private val MatRoofRoyalBlue = Color(0xFF1E3A8A)
private val MatRoofRoyalBlueDark = Color(0xFF172554)
private val MatRoofSkylight = Color(0xFF93C5FD)
private val MatRoofGalvanized = Color(0xFF64748B)
private val MatRoofUnderTruss = Color(0xFF334155)
private val MatPlatformTileCharcoal = Color(0xFF334155)
private val MatPlatformTileLight = Color(0xFFCBD5E1)
private val MatPlatformTileDark = Color(0xFF94A3B8)
private val MatCreamPlaster = Color(0xFFEAE4D7)
private val MatClockBrickRed = Color(0xFF991B1B)
private val MatCopperFinial = Color(0xFFB45309)

internal data class CamSpaceVert(val cx: Float, val cy: Float, val cz: Float)

/**
 * Captures 3D world-space road, shoulder, bellmouth, marking, and ground plaza primitives directly
 * into a flat GPU vertex array (8 floats/vertex: `x, y, z, r, g, b, a, biasOffset`) during a one-time
 * startup recording pass so the OpenGL ES 3.0 renderer can draw the entire road network from a static VBO.
 */
internal class WorldGeometryRecorder(initialFloatsCapacity: Int = 450_000) {
    var floats = FloatArray(initialFloatsCapacity)
        private set
    var floatCount = 0
        private set

    var passYOffset: Float = 0f
    var passBiasOffset: Float = 0f
    var microStepY: Float = 0f
    private var microCounter: Int = 0

    private var pendingKind = 0 // 0=none, 1=quad, 2=ribbon, 3=poly
    private var q0 = Vec3(0f, 0f, 0f)
    private var q1 = Vec3(0f, 0f, 0f)
    private var q2 = Vec3(0f, 0f, 0f)
    private var q3 = Vec3(0f, 0f, 0f)

    private val ribbonLeft = ArrayList<Vec3>(256)
    private val ribbonRight = ArrayList<Vec3>(256)
    private val polyPts = ArrayList<Vec3>(64)

    private var hasPendingSeg = false
    private var segA = Vec3(0f, 0f, 0f)
    private var segB = Vec3(0f, 0f, 0f)

    fun setPassLayer(yOffset: Float, biasOffset: Float, microStep: Float = 0.00003f) {
        passYOffset = yOffset
        passBiasOffset = biasOffset
        microStepY = microStep
        microCounter = 0
    }

    private fun ensureCapacity(extraFloats: Int) {
        if (floatCount + extraFloats > floats.size) {
            val newCap = max(floats.size * 2, floatCount + extraFloats + 65536)
            floats = floats.copyOf(newCap)
        }
    }

    private fun emitTri(a: Vec3, b: Vec3, c: Vec3, yLift: Float, col: Color, bias: Float) {
        ensureCapacity(24)
        val r = col.red; val g = col.green; val bl = col.blue; val al = col.alpha
        var p = floatCount
        floats[p++] = a.x; floats[p++] = a.y + yLift; floats[p++] = a.z; floats[p++] = r; floats[p++] = g; floats[p++] = bl; floats[p++] = al; floats[p++] = bias
        floats[p++] = b.x; floats[p++] = b.y + yLift; floats[p++] = b.z; floats[p++] = r; floats[p++] = g; floats[p++] = bl; floats[p++] = al; floats[p++] = bias
        floats[p++] = c.x; floats[p++] = c.y + yLift; floats[p++] = c.z; floats[p++] = r; floats[p++] = g; floats[p++] = bl; floats[p++] = al; floats[p++] = bias
        floatCount = p
    }

    fun setPendingQuad(v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3) {
        q0 = v0; q1 = v1; q2 = v2; q3 = v3
        pendingKind = 1
    }

    fun setPendingRibbon(
        frames: List<RoadFrame>,
        startIdx: Int,
        endIdx: Int,
        offsetLFn: (RoadFrame) -> Float,
        offsetRFn: (RoadFrame) -> Float,
        yHeight: Float
    ) {
        ribbonLeft.clear()
        ribbonRight.clear()
        val yDelta = yHeight - 0.05f
        for (i in startIdx..endIdx) {
            val f = frames[i]
            val offL = offsetLFn(f)
            val offR = offsetRFn(f)
            val fy = f.pos.y + yDelta
            ribbonLeft.add(Vec3(f.pos.x + f.right.x * offL, fy, f.pos.z + f.right.z * offL))
            ribbonRight.add(Vec3(f.pos.x + f.right.x * offR, fy, f.pos.z + f.right.z * offR))
        }
        pendingKind = 2
    }

    fun setPendingPolygon(pts: List<Vec3>) {
        polyPts.clear()
        polyPts.addAll(pts)
        pendingKind = 3
    }

    fun setPendingSegment(a: Vec3, b: Vec3) {
        segA = a
        segB = b
        hasPendingSeg = true
    }

    private fun dist3D(a: Vec3, b: Vec3): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun emitSubdividedQuad(
        v0: Vec3,
        v1: Vec3,
        v2: Vec3,
        v3: Vec3,
        yLift: Float,
        col: Color,
        bias: Float,
        maxEdgeM: Float = 3.5f
    ) {
        val lenU = max(dist3D(v0, v1), dist3D(v3, v2))
        val lenV = max(dist3D(v0, v3), dist3D(v1, v2))
        if (lenU <= maxEdgeM && lenV <= maxEdgeM) {
            emitTri(v0, v1, v2, yLift, col, bias)
            emitTri(v0, v2, v3, yLift, col, bias)
            return
        }
        val stepsU = kotlin.math.ceil(lenU / maxEdgeM).toInt().coerceIn(1, 28)
        val stepsV = kotlin.math.ceil(lenV / maxEdgeM).toInt().coerceIn(1, 28)

        fun bilerp(u: Float, v: Float): Vec3 {
            val iu = 1f - u
            val iv = 1f - v
            val w0 = iu * iv
            val w1 = u * iv
            val w2 = u * v
            val w3 = iu * v
            return Vec3(
                v0.x * w0 + v1.x * w1 + v2.x * w2 + v3.x * w3,
                v0.y * w0 + v1.y * w1 + v2.y * w2 + v3.y * w3,
                v0.z * w0 + v1.z * w1 + v2.z * w2 + v3.z * w3
            )
        }

        val invU = 1f / stepsU.toFloat()
        val invV = 1f / stepsV.toFloat()
        for (iv in 0 until stepsV) {
            val tv0 = iv * invV
            val tv1 = (iv + 1) * invV
            for (iu in 0 until stepsU) {
                val tu0 = iu * invU
                val tu1 = (iu + 1) * invU
                val p00 = bilerp(tu0, tv0)
                val p10 = bilerp(tu1, tv0)
                val p11 = bilerp(tu1, tv1)
                val p01 = bilerp(tu0, tv1)
                emitTri(p00, p10, p11, yLift, col, bias)
                emitTri(p00, p11, p01, yLift, col, bias)
            }
        }
    }

    private fun emitSubdividedTri(
        a: Vec3,
        b: Vec3,
        c: Vec3,
        yLift: Float,
        col: Color,
        bias: Float,
        maxEdgeM: Float = 5.5f,
        depth: Int = 0
    ) {
        if (depth >= 3) {
            emitTri(a, b, c, yLift, col, bias)
            return
        }
        val dAB = dist3D(a, b)
        val dBC = dist3D(b, c)
        val dCA = dist3D(c, a)
        val maxD = max(dAB, max(dBC, dCA))
        if (maxD <= maxEdgeM) {
            emitTri(a, b, c, yLift, col, bias)
            return
        }
        if (dAB >= dBC && dAB >= dCA) {
            val mid = Vec3((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f)
            emitSubdividedTri(a, mid, c, yLift, col, bias, maxEdgeM, depth + 1)
            emitSubdividedTri(mid, b, c, yLift, col, bias, maxEdgeM, depth + 1)
        } else if (dBC >= dCA) {
            val mid = Vec3((b.x + c.x) * 0.5f, (b.y + c.y) * 0.5f, (b.z + c.z) * 0.5f)
            emitSubdividedTri(a, b, mid, yLift, col, bias, maxEdgeM, depth + 1)
            emitSubdividedTri(a, mid, c, yLift, col, bias, maxEdgeM, depth + 1)
        } else {
            val mid = Vec3((c.x + a.x) * 0.5f, (c.y + a.y) * 0.5f, (c.z + a.z) * 0.5f)
            emitSubdividedTri(a, b, mid, yLift, col, bias, maxEdgeM, depth + 1)
            emitSubdividedTri(mid, b, c, yLift, col, bias, maxEdgeM, depth + 1)
        }
    }

    fun commitPendingPath(color: Color) {
        val kind = pendingKind
        pendingKind = 0
        if (kind == 0) return

        val extraCrownY = if (color == MatAsphaltCrown) 0.002f else 0f
        val yLift = passYOffset + extraCrownY + (microCounter++ * microStepY).coerceAtMost(0.004f)
        val bias = passBiasOffset - extraCrownY * 0.5f

        when (kind) {
            1 -> {
                emitSubdividedQuad(q0, q1, q2, q3, yLift, color, bias, maxEdgeM = 3.5f)
            }
            2 -> {
                val n = min(ribbonLeft.size, ribbonRight.size)
                for (i in 0 until n - 1) {
                    val l0 = ribbonLeft[i]
                    val r0 = ribbonRight[i]
                    val r1 = ribbonRight[i + 1]
                    val l1 = ribbonLeft[i + 1]
                    emitSubdividedQuad(l0, r0, r1, l1, yLift, color, bias, maxEdgeM = 4.5f)
                }
            }
            3 -> {
                val n = polyPts.size
                if (n >= 3) {
                    val p0 = polyPts[0]
                    for (i in 1 until n - 1) {
                        emitSubdividedTri(p0, polyPts[i], polyPts[i + 1], yLift, color, bias, maxEdgeM = 5.5f)
                    }
                }
            }
        }
    }

    fun commitPendingSegment(color: Color, strokeWidthPx: Float) {
        if (!hasPendingSeg) return
        hasPendingSeg = false
        val widthM = (strokeWidthPx * 0.065f).coerceIn(0.07f, 0.26f)
        emitWorldSegment(segA, segB, widthM, color)
    }

    fun emitWorldSegment(a: Vec3, b: Vec3, widthM: Float, color: Color) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val len = hypot(dx, dz)
        if (len < 1e-4f) return
        val hw = widthM * 0.5f
        val rx = (-dz / len) * hw
        val rz = (dx / len) * hw
        val yLift = passYOffset + (microCounter++ * microStepY).coerceAtMost(0.004f)
        val steps = if (len > 3.2f) kotlin.math.ceil(len / 3.0f).toInt().coerceIn(1, 28) else 1
        val invSteps = 1f / steps.toFloat()
        for (s in 0 until steps) {
            val t0 = s * invSteps
            val t1 = (s + 1) * invSteps
            val ax = a.x + dx * t0; val ay = a.y + dy * t0; val az = a.z + dz * t0
            val bx = a.x + dx * t1; val by = a.y + dy * t1; val bz = a.z + dz * t1
            val v0 = Vec3(ax - rx, ay, az - rz)
            val v1 = Vec3(ax + rx, ay, az + rz)
            val v2 = Vec3(bx + rx, by, bz + rz)
            val v3 = Vec3(bx - rx, by, bz - rz)
            emitTri(v0, v1, v2, yLift, color, passBiasOffset)
            emitTri(v0, v2, v3, yLift, color, passBiasOffset)
        }
    }

    fun toTrimmedFloatArray(): FloatArray = floats.copyOf(floatCount)
}

private class RecordingComposeCanvas(
    private val delegate: androidx.compose.ui.graphics.Canvas,
    private val recorder: WorldGeometryRecorder
) : androidx.compose.ui.graphics.Canvas by delegate {
    override fun drawPath(path: Path, paint: androidx.compose.ui.graphics.Paint) {
        recorder.commitPendingPath(paint.color)
    }

    override fun drawLine(p1: Offset, p2: Offset, paint: androidx.compose.ui.graphics.Paint) {
        recorder.commitPendingSegment(paint.color, paint.strokeWidth)
    }
}

internal class CameraRig(
    val eye: Vec3,
    val forward: Vec3,
    val right: Vec3,
    val up: Vec3,
    val fovScale: Float,
    val screenCenterX: Float,
    val screenCenterY: Float,
    val screenW: Float,
    val screenH: Float,
    val recorder: WorldGeometryRecorder? = null
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
        if (recorder != null) {
            recorder.setPendingSegment(a, b)
            return Pair(Offset.Zero, Offset(1f, 0f))
        }
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
        if (recorder != null) {
            recorder.setPendingQuad(v0, v1, v2, v3)
            return true
        }
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
        if (recorder != null) {
            recorder.setPendingRibbon(frames, startIdx, endIdx, offsetLFn, offsetRFn, yHeight)
            return true
        }
        polyRaw.clear()

        val yDelta = yHeight - 0.05f
        // Forward along left edge
        for (i in startIdx..endIdx) {
            val f = frames[i]
            val offL = offsetLFn(f)
            val w = Vec3(f.pos.x + f.right.x * offL, f.pos.y + yDelta, f.pos.z + f.right.z * offL)
            polyRaw.add(toCamSpace(w))
        }
        // Backward along right edge
        for (i in endIdx downTo startIdx) {
            val f = frames[i]
            val offR = offsetRFn(f)
            val w = Vec3(f.pos.x + f.right.x * offR, f.pos.y + yDelta, f.pos.z + f.right.z * offR)
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
        if (recorder != null) {
            recorder.setPendingPolygon(worldPts)
            return true
        }
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

    private val triRawX = FloatArray(4)
    private val triRawY = FloatArray(4)
    private val triRawZ = FloatArray(4)
    private val triRawCol = IntArray(4)
    private val triClipX = FloatArray(8)
    private val triClipY = FloatArray(8)
    private val triClipZ = FloatArray(8)
    private val triClipCol = IntArray(8)
    private val triScreenX = FloatArray(8)
    private val triScreenY = FloatArray(8)

    /**
     * Projects a 3D quad/triangle into screen-space triangles with smooth per-vertex ARGB
     * color interpolation across near-plane clipping, appending directly into [outVerts] & [outColors].
     */
    fun projectBusFaceToTriangles(
        face: BusQuadFace3D,
        outVerts: FloatArray,
        outColors: IntArray,
        startVertCount: Int
    ): Int {
        val vcnt = face.vertCount
        val camRx0 = face.x0 - eye.x; val camRy0 = face.y0 - eye.y; val camRz0 = face.z0 - eye.z
        val z0 = camRx0 * forward.x + camRy0 * forward.y + camRz0 * forward.z
        val camRx1 = face.x1 - eye.x; val camRy1 = face.y1 - eye.y; val camRz1 = face.z1 - eye.z
        val z1 = camRx1 * forward.x + camRy1 * forward.y + camRz1 * forward.z
        val camRx2 = face.x2 - eye.x; val camRy2 = face.y2 - eye.y; val camRz2 = face.z2 - eye.z
        val z2 = camRx2 * forward.x + camRy2 * forward.y + camRz2 * forward.z
        val z3 = if (vcnt == 4) {
            (face.x3 - eye.x) * forward.x + (face.y3 - eye.y) * forward.y + (face.z3 - eye.z) * forward.z
        } else {
            z2
        }

        if (z0 < nearZ && z1 < nearZ && z2 < nearZ && z3 < nearZ) return startVertCount

        val x0 = camRx0 * right.x + camRy0 * right.y + camRz0 * right.z
        val y0 = camRx0 * up.x + camRy0 * up.y + camRz0 * up.z
        val x1 = camRx1 * right.x + camRy1 * right.y + camRz1 * right.z
        val y1 = camRx1 * up.x + camRy1 * up.y + camRz1 * up.z
        val x2 = camRx2 * right.x + camRy2 * right.y + camRz2 * right.z
        val y2 = camRx2 * up.x + camRy2 * up.y + camRz2 * up.z

        // Fast path: completely in front of near plane (true for 99.9% of bus polygons)
        if (z0 >= nearZ && z1 >= nearZ && z2 >= nearZ && z3 >= nearZ) {
            val inv0 = fovScale / z0
            val sx0 = screenCenterX + x0 * inv0
            val sy0 = screenCenterY - y0 * inv0
            val inv1 = fovScale / z1
            val sx1 = screenCenterX + x1 * inv1
            val sy1 = screenCenterY - y1 * inv1
            val inv2 = fovScale / z2
            val sx2 = screenCenterX + x2 * inv2
            val sy2 = screenCenterY - y2 * inv2

            if (vcnt == 3) {
                if (!sx0.isFinite() || !sy0.isFinite() || !sx1.isFinite() || !sy1.isFinite() || !sx2.isFinite() || !sy2.isFinite()) {
                    return startVertCount
                }
                val minX = min(sx0, min(sx1, sx2))
                val maxX = max(sx0, max(sx1, sx2))
                val minY = min(sy0, min(sy1, sy2))
                val maxY = max(sy0, max(sy1, sy2))
                if (maxX < -250f || minX > screenW + 250f || maxY < -250f || minY > screenH + 250f) {
                    return startVertCount
                }
                if (startVertCount + 3 > outColors.size) return startVertCount
                val vi = startVertCount * 2
                outVerts[vi] = sx0; outVerts[vi + 1] = sy0
                outVerts[vi + 2] = sx1; outVerts[vi + 3] = sy1
                outVerts[vi + 4] = sx2; outVerts[vi + 5] = sy2
                outColors[startVertCount] = face.c0
                outColors[startVertCount + 1] = face.c1
                outColors[startVertCount + 2] = face.c2
                return startVertCount + 3
            } else {
                val camRx3 = face.x3 - eye.x; val camRy3 = face.y3 - eye.y; val camRz3 = face.z3 - eye.z
                val x3 = camRx3 * right.x + camRy3 * right.y + camRz3 * right.z
                val y3 = camRx3 * up.x + camRy3 * up.y + camRz3 * up.z
                val inv3 = fovScale / z3
                val sx3 = screenCenterX + x3 * inv3
                val sy3 = screenCenterY - y3 * inv3

                if (!sx0.isFinite() || !sy0.isFinite() || !sx1.isFinite() || !sy1.isFinite() ||
                    !sx2.isFinite() || !sy2.isFinite() || !sx3.isFinite() || !sy3.isFinite()
                ) {
                    return startVertCount
                }
                val minX = min(min(sx0, sx1), min(sx2, sx3))
                val maxX = max(max(sx0, sx1), max(sx2, sx3))
                val minY = min(min(sy0, sy1), min(sy2, sy3))
                val maxY = max(max(sy0, sy1), max(sy2, sy3))
                if (maxX < -250f || minX > screenW + 250f || maxY < -250f || minY > screenH + 250f) {
                    return startVertCount
                }
                if (startVertCount + 6 > outColors.size) return startVertCount
                val vi = startVertCount * 2
                outVerts[vi] = sx0; outVerts[vi + 1] = sy0
                outVerts[vi + 2] = sx1; outVerts[vi + 3] = sy1
                outVerts[vi + 4] = sx2; outVerts[vi + 5] = sy2
                outVerts[vi + 6] = sx0; outVerts[vi + 7] = sy0
                outVerts[vi + 8] = sx2; outVerts[vi + 9] = sy2
                outVerts[vi + 10] = sx3; outVerts[vi + 11] = sy3
                outColors[startVertCount] = face.c0
                outColors[startVertCount + 1] = face.c1
                outColors[startVertCount + 2] = face.c2
                outColors[startVertCount + 3] = face.c0
                outColors[startVertCount + 4] = face.c2
                outColors[startVertCount + 5] = face.c3
                return startVertCount + 6
            }
        }

        // Clipped path against camera near plane with color interpolation
        triRawX[0] = x0; triRawY[0] = y0; triRawZ[0] = z0; triRawCol[0] = face.c0
        triRawX[1] = x1; triRawY[1] = y1; triRawZ[1] = z1; triRawCol[1] = face.c1
        triRawX[2] = x2; triRawY[2] = y2; triRawZ[2] = z2; triRawCol[2] = face.c2
        if (vcnt == 4) {
            val camRx3 = face.x3 - eye.x; val camRy3 = face.y3 - eye.y; val camRz3 = face.z3 - eye.z
            triRawX[3] = camRx3 * right.x + camRy3 * right.y + camRz3 * right.z
            triRawY[3] = camRx3 * up.x + camRy3 * up.y + camRz3 * up.z
            triRawZ[3] = z3
            triRawCol[3] = face.c3
        }

        var clipN = 0
        for (i in 0 until vcnt) {
            val prevIdx = (i + vcnt - 1) % vcnt
            val cz = triRawZ[i]
            val pz = triRawZ[prevIdx]
            val currIn = cz >= nearZ
            val prevIn = pz >= nearZ
            if (currIn != prevIn) {
                val t = (nearZ - pz) / (cz - pz)
                triClipX[clipN] = triRawX[prevIdx] + (triRawX[i] - triRawX[prevIdx]) * t
                triClipY[clipN] = triRawY[prevIdx] + (triRawY[i] - triRawY[prevIdx]) * t
                triClipZ[clipN] = nearZ
                triClipCol[clipN] = lerpArgbColor(triRawCol[prevIdx], triRawCol[i], t)
                clipN++
            }
            if (currIn) {
                triClipX[clipN] = triRawX[i]
                triClipY[clipN] = triRawY[i]
                triClipZ[clipN] = cz
                triClipCol[clipN] = triRawCol[i]
                clipN++
            }
        }
        if (clipN < 3) return startVertCount

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until clipN) {
            val inv = fovScale / triClipZ[i]
            val sx = screenCenterX + triClipX[i] * inv
            val sy = screenCenterY - triClipY[i] * inv
            if (!sx.isFinite() || !sy.isFinite()) return startVertCount
            triScreenX[i] = sx
            triScreenY[i] = sy
            if (sx < minX) minX = sx; if (sx > maxX) maxX = sx
            if (sy < minY) minY = sy; if (sy > maxY) maxY = sy
        }
        if (maxX < -250f || minX > screenW + 250f || maxY < -250f || minY > screenH + 250f) {
            return startVertCount
        }

        var cur = startVertCount
        for (k in 1 until clipN - 1) {
            if (cur + 3 > outColors.size) break
            val vi = cur * 2
            outVerts[vi] = triScreenX[0]; outVerts[vi + 1] = triScreenY[0]
            outVerts[vi + 2] = triScreenX[k]; outVerts[vi + 3] = triScreenY[k]
            outVerts[vi + 4] = triScreenX[k + 1]; outVerts[vi + 5] = triScreenY[k + 1]
            outColors[cur] = triClipCol[0]
            outColors[cur + 1] = triClipCol[k]
            outColors[cur + 2] = triClipCol[k + 1]
            cur += 3
        }
        return cur
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
    baseColor: Color,
    val strokeColor: Color? = null,
    val biasDist: Float = 0f,
    var sortKey: Float = 0f,
    val nx: Float = 0f,
    val ny: Float = 0f,
    val nz: Float = 0f,
    val signboard: SignboardQuadSpec? = null
) {
    val color: Color = if (signboard != null || baseColor.alpha < 0.95f) {
        baseColor
    } else {
        // Compute geometric surface normal for 3D directional sun & sky hemisphere shading
        val ux = v1.x - v0.x; val uy = v1.y - v0.y; val uz = v1.z - v0.z
        val wx = v3.x - v0.x; val wy = v3.y - v0.y; val wz = v3.z - v0.z
        val cx = uy * wz - uz * wy
        val cy = uz * wx - ux * wz
        val cz = ux * wy - uy * wx
        val len = sqrt(cx * cx + cy * cy + cz * cz)
        val gnx = if (len > 1e-4f) cx / len else nx
        val gny = if (len > 1e-4f) cy / len else (if (ny != 0f) ny else 1f)
        val gnz = if (len > 1e-4f) cz / len else nz

        // Afternoon Kerala sunlight vector (-0.398, 0.777, 0.478) + sky dome bounce
        val sunDot = gnx * (-0.398f) + gny * 0.777f + gnz * 0.478f
        val dirLight = 0.78f + 0.22f * sunDot
        val skyBounce = 0.92f + 0.08f * gny.coerceIn(-1f, 1f)
        val avgY = (v0.y + v1.y + v2.y + v3.y) * 0.25f
        val groundAo = if (avgY < 0.85f) (0.88f + 0.12f * (avgY / 0.85f).coerceIn(0f, 1f)) else 1.0f
        val shade = (dirLight * skyBounce * groundAo).coerceIn(0.68f, 1.14f)

        Color(
            red = (baseColor.red * shade).coerceIn(0f, 1f),
            green = (baseColor.green * shade).coerceIn(0f, 1f),
            blue = (baseColor.blue * shade).coerceIn(0f, 1f),
            alpha = baseColor.alpha
        )
    }
}

internal val FaceDepthComparator = Comparator<QuadFace3D> { a, b ->
    b.sortKey.compareTo(a.sortKey)
}

internal class BusQuadFace3D {
    var vertCount: Int = 4
    var x0: Float = 0f; var y0: Float = 0f; var z0: Float = 0f
    var x1: Float = 0f; var y1: Float = 0f; var z1: Float = 0f
    var x2: Float = 0f; var y2: Float = 0f; var z2: Float = 0f
    var x3: Float = 0f; var y3: Float = 0f; var z3: Float = 0f
    var c0: Int = 0; var c1: Int = 0; var c2: Int = 0; var c3: Int = 0
    var sortKey: Float = 0f
}

internal val BusFaceDepthComparator = Comparator<BusQuadFace3D> { a, b ->
    b.sortKey.compareTo(a.sortKey)
}

private inline fun packArgb(r: Float, g: Float, b: Float, a: Float): Int {
    val ai = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
    val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
}

private fun lerpArgbColor(c0: Int, c1: Int, t: Float): Int {
    val a0 = (c0 ushr 24) and 0xFF; val r0 = (c0 ushr 16) and 0xFF; val g0 = (c0 ushr 8) and 0xFF; val b0 = c0 and 0xFF
    val a1 = (c1 ushr 24) and 0xFF; val r1 = (c1 ushr 16) and 0xFF; val g1 = (c1 ushr 8) and 0xFF; val b1 = c1 and 0xFF
    val a = (a0 + (a1 - a0) * t + 0.5f).toInt().coerceIn(0, 255)
    val r = (r0 + (r1 - r0) * t + 0.5f).toInt().coerceIn(0, 255)
    val g = (g0 + (g1 - g0) * t + 0.5f).toInt().coerceIn(0, 255)
    val b = (b0 + (b1 - b0) * t + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
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
    val x0: Float, val y0: Float, val z0: Float,
    val x1: Float, val y1: Float, val z1: Float,
    val x2: Float, val y2: Float, val z2: Float,
    val x3: Float, val y3: Float, val z3: Float,
    val nx0: Float, val ny0: Float, val nz0: Float,
    val nx1: Float, val ny1: Float, val nz1: Float,
    val nx2: Float, val ny2: Float, val nz2: Float,
    val nx3: Float, val ny3: Float, val nz3: Float,
    val r0: Float, val g0: Float, val b0: Float,
    val r1: Float, val g1: Float, val b1: Float,
    val r2: Float, val g2: Float, val b2: Float,
    val r3: Float, val g3: Float, val b3: Float,
    val alpha: Float,
    val isDoorLeaf: Boolean = false
)

internal object HinoRealMeshCache {
    @Volatile
    var cachedMesh: List<HinoRealMeshPoly> = emptyList()
        private set

    fun getOrLoad(context: android.content.Context): List<HinoRealMeshPoly> {
        val existing = cachedMesh
        if (existing.isNotEmpty()) return existing
        return synchronized(this) {
            val again = cachedMesh
            if (again.isNotEmpty()) {
                again
            } else {
                loadHinoRealMeshFromAssets(context.applicationContext).also { cachedMesh = it }
            }
        }
    }
}

private fun loadHinoRealMeshFromAssets(context: android.content.Context): List<HinoRealMeshPoly> {
    return try {
        val bytes = context.assets.open("hino_rm2_real_mesh.bin").use { it.readBytes() }
        if (bytes.size < 8) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val m0 = buf.get().toInt().toChar()
        val m1 = buf.get().toInt().toChar()
        val m2 = buf.get().toInt().toChar()
        val m3 = buf.get().toInt().toChar()
        if (m0 != 'H' || m1 != 'R' || m2 != 'M' || (m3 != '3' && m3 != '2')) return emptyList()
        val isV3 = (m3 == '3')
        val count = buf.int
        val minHeaderSize = if (isV3) 23 else 20
        if (count <= 0 || count > 100_000 || count.toLong() * (minHeaderSize + 36).toLong() > bytes.size.toLong()) {
            return emptyList()
        }

        val pPart = IntArray(count)
        val pVcnt = IntArray(count)
        val pCull = IntArray(count)
        val pBias = FloatArray(count)
        val pNx = FloatArray(count)
        val pNy = FloatArray(count)
        val pNz = FloatArray(count)
        val pArea = FloatArray(count)
        val pR = FloatArray(count)
        val pG = FloatArray(count)
        val pB = FloatArray(count)
        val pA = FloatArray(count)
        val vx = FloatArray(count * 4)
        val vy = FloatArray(count * 4)
        val vz = FloatArray(count * 4)

        // Normalized baked light vector from compile_hino_rm2_android_mesh.py (-0.35, 0.80, 0.48)
        val blx = -0.35125f
        val bly = 0.80286f
        val blz = 0.48171f

        // Zero-boxing primitive open-addressing hash table mapping quantized vertex -> linked list of corner indices
        var tableCap = 1024
        val targetCap = (count * 4).coerceAtLeast(256)
        while (tableCap < targetCap) tableCap = tableCap shl 1
        val tableMask = tableCap - 1
        val slotKeys = LongArray(tableCap)
        val slotHeads = IntArray(tableCap) { -1 }
        val cornerNext = IntArray(count * 4) { -1 }

        fun hashKey(key: Long): Int {
            var z = key
            z = (z xor (z ushr 33)) * -0xae502812aa7333L
            z = (z xor (z ushr 33)) * -0x3b314601e57a13adL
            z = z xor (z ushr 33)
            return z.toInt() and tableMask
        }

        fun packWeldKey(x: Float, y: Float, z: Float, group: Long): Long {
            val sx = if (x.isFinite()) x.coerceIn(-40f, 40f) else 0f
            val sy = if (y.isFinite()) y.coerceIn(-40f, 40f) else 0f
            val sz = if (z.isFinite()) z.coerceIn(-40f, 40f) else 0f
            val qx = ((sx * 350f).roundToInt().toLong() + 32768L) and 0xFFFFL
            val qy = ((sy * 350f).roundToInt().toLong() + 32768L) and 0xFFFFL
            val qz = ((sz * 350f).roundToInt().toLong() + 32768L) and 0xFFFFL
            return qx or (qy shl 16) or (qz shl 32) or ((group and 0xFFFFL) shl 48)
        }

        var actualCount = 0
        for (i in 0 until count) {
            if (buf.remaining() < minHeaderSize) break
            val partId = buf.get().toInt() and 0xFF
            val vcnt = (buf.get().toInt() and 0xFF).coerceIn(3, 4)
            val cullMode = if (isV3) (buf.get().toInt() and 0xFF) else 0
            val rRaw = (buf.get().toInt() and 0xFF) / 255f
            val gRaw = (buf.get().toInt() and 0xFF) / 255f
            val bRaw = (buf.get().toInt() and 0xFF) / 255f
            val aRaw = (buf.get().toInt() and 0xFF) / 255f
            val bias = buf.float
            val nx = if (isV3) buf.float else 0f
            val ny = if (isV3) buf.float else 1f
            val nz = if (isV3) buf.float else 0f
            if (buf.remaining() < vcnt * 12) break
            val x0 = buf.float; val y0 = buf.float; val z0 = buf.float
            val x1 = buf.float; val y1 = buf.float; val z1 = buf.float
            val x2 = buf.float; val y2 = buf.float; val z2 = buf.float
            val x3: Float; val y3: Float; val z3: Float
            if (vcnt == 4) {
                x3 = buf.float; y3 = buf.float; z3 = buf.float
            } else {
                x3 = x2; y3 = y2; z3 = z2
            }

            if (!bias.isFinite() || !nx.isFinite() || !ny.isFinite() || !nz.isFinite() ||
                !x0.isFinite() || !y0.isFinite() || !z0.isFinite() ||
                !x1.isFinite() || !y1.isFinite() || !z1.isFinite() ||
                !x2.isFinite() || !y2.isFinite() || !z2.isFinite() ||
                !x3.isFinite() || !y3.isFinite() || !z3.isFinite()
            ) {
                continue
            }

            val idx = actualCount++
            // Un-bake coarse flat-polygon static shading on exterior coachwork & 3D light lenses to remove facet steps
            val unbake = if ((partId == 0 || partId in 1..5) && cullMode == 1) {
                val bndotl = (nx * blx + ny * bly + nz * blz).coerceIn(-0.55f, 1f)
                1f / (0.82f + 0.18f * bndotl)
            } else {
                1f
            }

            pPart[idx] = partId
            pVcnt[idx] = vcnt
            pCull[idx] = cullMode
            pBias[idx] = bias
            pNx[idx] = nx; pNy[idx] = ny; pNz[idx] = nz
            pR[idx] = (rRaw * unbake).coerceIn(0f, 1f)
            pG[idx] = (gRaw * unbake).coerceIn(0f, 1f)
            pB[idx] = (bRaw * unbake).coerceIn(0f, 1f)
            pA[idx] = aRaw

            val ux = x1 - x0; val uy = y1 - y0; val uz = z1 - z0
            val wx = x2 - x0; val wy = y2 - y0; val wz = z2 - z0
            val cxp = uy * wz - uz * wy
            val cyp = uz * wx - ux * wz
            val czp = ux * wy - uy * wx
            pArea[idx] = (0.5f * sqrt(cxp * cxp + cyp * cyp + czp * czp)).coerceIn(1e-5f, 1.5f)

            val base = idx * 4
            vx[base] = x0; vy[base] = y0; vz[base] = z0
            vx[base + 1] = x1; vy[base + 1] = y1; vz[base + 1] = z1
            vx[base + 2] = x2; vy[base + 2] = y2; vz[base + 2] = z2
            vx[base + 3] = x3; vy[base + 3] = y3; vz[base + 3] = z3

            val smoothGroup = when {
                partId == 13 -> 1L
                partId in 1..5 -> 2L
                partId in 6..11 -> partId.toLong()
                cullMode == 2 -> 20L
                else -> 0L
            }
            for (k in 0 until vcnt) {
                val cornerId = base + k
                val key = packWeldKey(vx[cornerId], vy[cornerId], vz[cornerId], smoothGroup)
                var slot = hashKey(key)
                while (slotHeads[slot] != -1 && slotKeys[slot] != key) {
                    slot = (slot + 1) and tableMask
                }
                if (slotHeads[slot] == -1) {
                    slotKeys[slot] = key
                    slotHeads[slot] = cornerId
                } else {
                    cornerNext[cornerId] = slotHeads[slot]
                    slotHeads[slot] = cornerId
                }
            }
        }

        val out = ArrayList<HinoRealMeshPoly>(actualCount)
        val snx = FloatArray(4)
        val sny = FloatArray(4)
        val snz = FloatArray(4)
        val sr = FloatArray(4)
        val sg = FloatArray(4)
        val sb = FloatArray(4)

        for (i in 0 until actualCount) {
            val partId = pPart[i]
            val vcnt = pVcnt[i]
            val cullMode = pCull[i]
            val nx = pNx[i]; val ny = pNy[i]; val nz = pNz[i]
            val rSelf = pR[i]; val gSelf = pG[i]; val bSelf = pB[i]
            val base = i * 4
            val smoothGroup = when {
                partId == 13 -> 1L
                partId in 1..5 -> 2L
                partId in 6..11 -> partId.toLong()
                cullMode == 2 -> 20L
                else -> 0L
            }

            for (k in 0 until vcnt) {
                val cornerId = base + k
                val key = packWeldKey(vx[cornerId], vy[cornerId], vz[cornerId], smoothGroup)
                var slot = hashKey(key)
                while (slotHeads[slot] != -1 && slotKeys[slot] != key) {
                    slot = (slot + 1) and tableMask
                }

                var accNx = 0f; var accNy = 0f; var accNz = 0f
                var accR = rSelf; var accG = gSelf; var accB = bSelf
                var colWeight = 1f

                var node = slotHeads[slot]
                while (node != -1) {
                    val pOther = node ushr 2
                    val dotN = nx * pNx[pOther] + ny * pNy[pOther] + nz * pNz[pOther]
                    if (dotN >= 0.42f) {
                        val w = pArea[pOther]
                        accNx += pNx[pOther] * w
                        accNy += pNy[pOther] * w
                        accNz += pNz[pOther] * w
                        if (pOther != i && partId == 0 && pPart[pOther] == 0 && cullMode == 1 && pCull[pOther] == 1) {
                            val dr = abs(rSelf - pR[pOther])
                            val dg = abs(gSelf - pG[pOther])
                            val db = abs(bSelf - pB[pOther])
                            if (dr < 0.22f && dg < 0.22f && db < 0.22f) {
                                accR += pR[pOther]
                                accG += pG[pOther]
                                accB += pB[pOther]
                                colWeight += 1f
                            }
                        }
                    }
                    node = cornerNext[node]
                }

                val nLen = sqrt(accNx * accNx + accNy * accNy + accNz * accNz)
                if (nLen > 1e-5f) {
                    snx[k] = accNx / nLen; sny[k] = accNy / nLen; snz[k] = accNz / nLen
                } else {
                    snx[k] = nx; sny[k] = ny; snz[k] = nz
                }
                val invColW = 1f / colWeight
                sr[k] = (accR * invColW).coerceIn(0f, 1f)
                sg[k] = (accG * invColW).coerceIn(0f, 1f)
                sb[k] = (accB * invColW).coerceIn(0f, 1f)
            }
            if (vcnt == 3) {
                snx[3] = snx[2]; sny[3] = sny[2]; snz[3] = snz[2]
                sr[3] = sr[2]; sg[3] = sg[2]; sb[3] = sb[2]
            }

            val cx: Float; val cy: Float; val cz: Float
            if (vcnt == 4) {
                cx = (vx[base] + vx[base + 1] + vx[base + 2] + vx[base + 3]) * 0.25f
                cy = (vy[base] + vy[base + 1] + vy[base + 2] + vy[base + 3]) * 0.25f
                cz = (vz[base] + vz[base + 1] + vz[base + 2] + vz[base + 3]) * 0.25f
            } else {
                cx = (vx[base] + vx[base + 1] + vx[base + 2]) * (1f / 3f)
                cy = (vy[base] + vy[base + 1] + vy[base + 2]) * (1f / 3f)
                cz = (vz[base] + vz[base + 1] + vz[base + 2]) * (1f / 3f)
            }

            // Remove red interior window curtains behind the back, left-side, and right-side windows for clearer glass
            val isSideOrRearRedCurtain = partId == 0 &&
                cullMode == 2 &&
                pBias[i] in 6.14f..6.19f &&
                rSelf > 0.35f &&
                gSelf < 0.25f &&
                bSelf < 0.29f
            if (isSideOrRearRedCurtain) {
                continue
            }

            val frontDoorEdgeZ = if (cy < 1.68f) 5.240f else 5.240f - (cy - 1.68f) * 0.21f
            val isDoorLeafPoly = (partId == 0 || partId == 13) &&
                cullMode == 1 &&
                cx < -1.165f &&
                cy in 0.475f..2.496f &&
                cz in 4.382f..frontDoorEdgeZ

            out.add(
                HinoRealMeshPoly(
                    partId = partId,
                    vertCount = vcnt,
                    cullMode = cullMode,
                    baseColor = Color(rSelf, gSelf, bSelf, pA[i]),
                    biasDist = pBias[i],
                    nx = nx, ny = ny, nz = nz,
                    cx = cx, cy = cy, cz = cz,
                    x0 = vx[base], y0 = vy[base], z0 = vz[base],
                    x1 = vx[base + 1], y1 = vy[base + 1], z1 = vz[base + 1],
                    x2 = vx[base + 2], y2 = vy[base + 2], z2 = vz[base + 2],
                    x3 = vx[base + 3], y3 = vy[base + 3], z3 = vz[base + 3],
                    nx0 = snx[0], ny0 = sny[0], nz0 = snz[0],
                    nx1 = snx[1], ny1 = sny[1], nz1 = snz[1],
                    nx2 = snx[2], ny2 = sny[2], nz2 = snz[2],
                    nx3 = snx[3], ny3 = sny[3], nz3 = snz[3],
                    r0 = sr[0], g0 = sg[0], b0 = sb[0],
                    r1 = sr[1], g1 = sg[1], b1 = sb[1],
                    r2 = sr[2], g2 = sg[2], b2 = sb[2],
                    r3 = sr[3], g3 = sg[3], b3 = sb[3],
                    alpha = pA[i],
                    isDoorLeaf = isDoorLeafPoly
                )
            )
        }
        out
    } catch (_: Throwable) {
        emptyList()
    }
}

@Composable
fun Thrissur3DRoadViewport(
    stateProvider: () -> SimUiState,
    glStateProvider: () -> SimUiState = stateProvider,
    onOrbitDrag: (Float, Float) -> Unit,
    onWorldReady: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraModeState = remember(stateProvider) {
        androidx.compose.runtime.derivedStateOf { stateProvider().cameraMode }
    }
    val cameraMode = cameraModeState.value
    val glRenderer = remember(context) {
        ThrissurGlWorldRenderer(context.applicationContext)
    }
    glRenderer.onWorldGpuReady = onWorldReady
    glRenderer.stateProvider = glStateProvider
    val cachedBusMesh = HinoRealMeshCache.cachedMesh
    if (cachedBusMesh.isNotEmpty() && glRenderer.hinoMesh.isEmpty()) {
        glRenderer.hinoMesh = cachedBusMesh
    }

    val glSurfaceView = remember(context) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 24, 0)
            preserveEGLContextOnPause = true
            setRenderer(glRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            var lastFixedW = 0
            var lastFixedH = 0
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val vw = right - left
                val vh = bottom - top
                if (vw > 0 && vh > 720) {
                    val targetH = 720
                    val targetW = ((vw.toLong() * targetH) / vh).toInt().coerceAtLeast(1)
                    if (targetW != lastFixedW || targetH != lastFixedH) {
                        lastFixedW = targetW
                        lastFixedH = targetH
                        holder.setFixedSize(targetW, targetH)
                    }
                }
            }
        }
    }

    DisposableEffect(glSurfaceView) {
        glSurfaceView.onResume()
        onDispose {
            glSurfaceView.onPause()
        }
    }

    val titlePaint = remember(context) {
        PremiumFonts.init(context)
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = PremiumFonts.displayTypefaceFor("", bold = true)
            letterSpacing = 0.04f
        }
    }
    val subPaint = remember(context) {
        PremiumFonts.init(context)
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = PremiumFonts.monoTypeface(bold = true)
            letterSpacing = 0.03f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("thrissur_3d_viewport")
            .pointerInput(cameraMode) {
                if (cameraMode == CameraViewMode.Orbit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onOrbitDrag(dragAmount.x * 0.22f, dragAmount.y * 0.14f)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { glSurfaceView },
            update = {
                glRenderer.stateProvider = glStateProvider
                val latestMesh = HinoRealMeshCache.cachedMesh
                if (latestMesh.isNotEmpty() && glRenderer.hinoMesh.isEmpty()) {
                    glRenderer.hinoMesh = latestMesh
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (cameraMode == CameraViewMode.Cockpit) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawHinoRm2Cockpit3D(
                    state = stateProvider(),
                    titlePaint = titlePaint,
                    subPaint = subPaint
                )
            }
        }
    }
}

/**
 * Performs a one-time startup recording pass of all Thrissur roads, laterite shoulders, footpaths,
 * curb stones, junction bellmouths, road markings, zebra crossings, Sakthan 20-bay terminal yard,
 * and all 5 landmark ground plazas into a static GPU vertex array for OpenGL ES 3.0.
 */
internal fun captureStaticRoadAndGroundTriangles(roads: List<ThrissurOsmRoad>): FloatArray {
    if (roads.isEmpty()) return FloatArray(0)
    val recorder = WorldGeometryRecorder(480_000)
    val dummyEye = Vec3(0f, 50f, 0f)
    val rig = CameraRig(
        eye = dummyEye,
        forward = Vec3(0f, 0f, 1f),
        right = Vec3(1f, 0f, 0f),
        up = Vec3(0f, 1f, 0f),
        fovScale = 500f,
        screenCenterX = 500f,
        screenCenterY = 500f,
        screenW = 1000f,
        screenH = 1000f,
        recorder = recorder
    )

    val allSpans = ArrayList<ContiguousRoadSpan>(256)
    for (road in roads) {
        val frames = road.frames
        if (frames.size < 2) continue
        val chunkSize = 16
        var i = 0
        while (i < frames.size - 1) {
            val sIdx = i
            val eIdx = min(i + chunkSize, frames.size - 1)
            allSpans.add(ContiguousRoadSpan(road, sIdx, eIdx, 0f))
            i += chunkSize
        }
    }

    val delegateCanvas = androidx.compose.ui.graphics.Canvas(ImageBitmap(1, 1))
    val recordingCanvas = RecordingComposeCanvas(delegateCanvas, recorder)
    val drawScope = CanvasDrawScope()
    val sharedPath = Path()

    drawScope.draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = recordingCanvas,
        size = Size(1000f, 1000f)
    ) {
        // PASS 1A: Sakthan 20-Bay Terminal Yard
        recorder.setPassLayer(yOffset = 0.000f, biasOffset = 0.016f, microStep = 0.00004f)
        drawSakthan20BayTerminalYard(rig, sharedPath, Vec3(10f, 2f, -178f))

        // PASS 1B: All 5 Landmark Ground Plazas
        recorder.setPassLayer(yOffset = 0.001f, biasOffset = 0.012f, microStep = 0.00003f)
        drawAllLandmarkGroundPlazasUnconditional(rig, sharedPath, roads)

        // PASS 1C: Flared Junction Laterite Shoulders & Lips
        recorder.setPassLayer(yOffset = 0.003f, biasOffset = 0.008f, microStep = 0.00002f)
        drawJunctionShoulders(rig, sharedPath, dummyEye)

        // PASS 1D: Continuous Road Shoulders, Footpaths & Curb Stones
        recorder.setPassLayer(yOffset = 0.005f, biasOffset = 0.005f, microStep = 0.00001f)
        for (span in allSpans) {
            drawContinuousShouldersAndCurbs(rig, sharedPath, span)
        }

        // PASS 2A: Seamless Bezier Junction Plazas
        recorder.setPassLayer(yOffset = 0.012f, biasOffset = 0.000f, microStep = 0.00002f)
        drawJunctionPlazas(rig, sharedPath, dummyEye)

        // PASS 2B: Continuous Crack-Free Asphalt Carriageway Ribbons + Crown
        recorder.setPassLayer(yOffset = 0.014f, biasOffset = -0.002f, microStep = 0.00001f)
        for (span in allSpans) {
            drawContinuousAsphaltRibbon(rig, sharedPath, span)
        }

        // PASS 2C: Paliyekkara 12-Lane Toll Plaza Rigid PQC Concrete Pavement, Fan-Out Lane Guides & FASTag Strips
        //          (Layered directly above the continuous S-curve flared asphalt ribbon & below outer edge lines!)
        recorder.setPassLayer(yOffset = 0.017f, biasOffset = -0.005f, microStep = 0.00001f)
        Zone3Nh544SouthRenderer.run { drawPaliyekkaraTollPavementOverlay(rig, sharedPath, dummyEye) }

        // PASS 3A/3B/3C: Continuous IRC Road Markings, Junction Edge Fillets & 8 Swaraj Zebra Crossings
        recorder.setPassLayer(yOffset = 0.020f, biasOffset = -0.008f, microStep = 0.000005f)
        for (span in allSpans) {
            drawContinuousRoadMarkings(rig, sharedPath, span)
        }
        drawJunctionEdgeFilletMarkings(rig, dummyEye)
        drawSwarajRound8ZebraCrossings(rig, sharedPath, Vec3(3.1f, 2f, 18.0f), roads)
    }

    return recorder.toTrimmedFloatArray()
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
    val isVadakkeGateRoad = road.id == "ROAD_SWARAJ_TO_VADAKKE" || road.id == "ROAD_VADAKKE_STAND_EXIT"
    val isStationGateRoad = road.id == "ROAD_RAILWAY_STATION_ACCESS" || road.id == "ROAD_RAILWAY_STATION_EXIT"
    val isShoranurTermGateRoad = road.id == "ROAD_SHORANUR_TERMINAL_ENTRY" || road.id == "ROAD_SHORANUR_TERMINAL_EXIT"
    val isShoranurStationGateRoad = road.id == "ROAD_SHORANUR_STATION_ENTRY" || road.id == "ROAD_SHORANUR_STATION_EXIT"
    val isPeechiDamGateRoad = road.id == "ROAD_PEECHI_DAM_ENTRY" || road.id == "ROAD_PEECHI_DAM_EXIT"
    val isNh544 = road.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" || road.id == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY"
    val isNh66 = road.id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" || road.id == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM"
    val isKuthiranTunnelTube = road.id == "ROAD_NH544_KUTHIRAN_LEFT_TUBE" || road.id == "ROAD_NH544_KUTHIRAN_RIGHT_TUBE"
    val isSh69Muthuvara = road.id == "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR"
    fun insideSakthanYard(f: RoadFrame): Boolean =
        isSakthanGateRoad && f.pos.x in -30.8f..40.3f && f.pos.z in -207.2f..-139.6f
    fun insideVadakkeApron(f: RoadFrame): Boolean =
        isVadakkeGateRoad && f.pos.z >= 198.2f
    fun insideStationApron(f: RoadFrame): Boolean =
        isStationGateRoad && f.pos.x <= -61.8f
    fun insideShoranurYard(f: RoadFrame): Boolean =
        (isShoranurTermGateRoad && f.pos.x >= 124.2f) ||
            (isShoranurStationGateRoad && f.pos.x <= 75.8f) ||
            (isPeechiDamGateRoad && f.pos.x >= 3410.2f)
    fun insideKuthiranTubeOrGore(f: RoadFrame): Boolean =
        isKuthiranTunnelTube && f.pos.x in 1994.0f..3286.0f
    fun insideMuthuvaraLcPad(f: RoadFrame): Boolean =
        isSh69Muthuvara && f.pos.z in 597.5f..622.5f
    fun onElevatedBridgeSpan(f: RoadFrame): Boolean = f.pos.y > 0.35f
    fun insideTerminalOrTollApron(f: RoadFrame): Boolean =
        insideSakthanYard(f) || insideVadakkeApron(f) || insideStationApron(f) || insideShoranurYard(f) ||
            insideKuthiranTubeOrGore(f) || insideMuthuvaraLcPad(f) || onElevatedBridgeSpan(f) ||
            (isNh544 && f.pos.z <= -5330.0f) ||
            (isNh66 && (f.pos.z in Zone5Nh66CoastalRenderer.NATTIKA_FLARE_SOUTH_END_Z..Zone5Nh66CoastalRenderer.NATTIKA_FLARE_NORTH_START_Z || f.pos.z <= -4375.0f))

    // 1. Left & Right Earthen Laterite Shoulders (continuous through all road joints; suppressed only inside concrete terminal/toll/station aprons)
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
        road.id == "ROAD_STATION_ROAD" ||
        road.id == "ROAD_RAILWAY_STATION_ACCESS" ||
        road.id == "ROAD_RAILWAY_STATION_EXIT" ||
        road.id == "ROAD_SWARAJ_TO_VADAKKE" ||
        road.id == "ROAD_VADAKKE_STAND_EXIT" ||
        (road.id == "ROAD_SWARAJ_TO_MANNUTHY" && frames[span.startIdx].cumulativeDist < 320f) ||
        (road.id == "ROAD_SWARAJ_TO_WEST_FORT" && frames[span.startIdx].cumulativeDist < 260f)

    if (hasUrbanFootpath) {
        val fpInnerOff = bevelW + shoulderW
        val fpWidth = 2.00f // Zone 1 Spec: 2.0m wide paved pedestrian sidewalk
        val matFootpathSlab = if (road.id == "ROAD_SWARAJ_ROUND") Color(0xFFB86B55) else Color(0xFF94A3B8)
        drawUncoveredSideRibbon(
            offsetInnerFn = { -it.halfWidthM - fpInnerOff - fpWidth },
            offsetOuterFn = { -it.halfWidthM - fpInnerOff },
            color = matFootpathSlab
        ) { it.leftEdgeCovered || insideTerminalOrTollApron(it) }
        if (road.id != "ROAD_SWARAJ_ROUND") {
            drawUncoveredSideRibbon(
                offsetInnerFn = { it.halfWidthM + fpInnerOff },
                offsetOuterFn = { it.halfWidthM + fpInnerOff + fpWidth },
                color = matFootpathSlab
            ) { it.rightEdgeCovered || insideTerminalOrTollApron(it) }
        }

        // Zone 1 Spec: Alternating black and reflective highway yellow curb stones (500mm pitch)
        if (span.avgDistSq <= 115f * 115f) {
            for (i in span.startIdx until span.endIdx) {
                val f0 = frames[i]
                val f1 = frames[i + 1]
                if (insideTerminalOrTollApron(f0)) continue
                val curbCol = if (((f0.cumulativeDist * 2.0f).toInt() and 1) == 0) MatCurbYellow else MatCurbBlack
                if (!f0.leftEdgeCovered) {
                    val cL0 = Vec3(f0.pos.x - f0.right.x * (f0.halfWidthM + 0.14f), 0.064f, f0.pos.z - f0.right.z * (f0.halfWidthM + 0.14f))
                    val cL1 = Vec3(f1.pos.x - f1.right.x * (f1.halfWidthM + 0.14f), 0.064f, f1.pos.z - f1.right.z * (f1.halfWidthM + 0.14f))
                    rig.projectSegmentClipped(cL0, cL1)?.let { seg ->
                        drawLine(curbCol, seg.first, seg.second, strokeWidth = 2.4f)
                    }
                }
                if (!f0.rightEdgeCovered) {
                    val cR0 = Vec3(f0.pos.x + f0.right.x * (f0.halfWidthM + 0.14f), 0.064f, f0.pos.z + f0.right.z * (f0.halfWidthM + 0.14f))
                    val cR1 = Vec3(f1.pos.x + f1.right.x * (f1.halfWidthM + 0.14f), 0.064f, f1.pos.z + f1.right.z * (f1.halfWidthM + 0.14f))
                    rig.projectSegmentClipped(cR0, cR1)?.let { seg ->
                        drawLine(curbCol, seg.first, seg.second, strokeWidth = 2.4f)
                    }
                }
            }
        }
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
        if (rig.recorder == null && hypot(bm.centerPos.x - camEye.x, bm.centerPos.z - camEye.z) > 400f) continue
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
    val isSakthanGateRoad = road.id == "ROAD_SAKTHAN_TO_SWARAJ" || road.id == "ROAD_SAKTHAN_STAND_ENTRY"
    val isVadakkeGateRoad = road.id == "ROAD_SWARAJ_TO_VADAKKE" || road.id == "ROAD_VADAKKE_STAND_EXIT"
    val isStationGateRoad = road.id == "ROAD_RAILWAY_STATION_ACCESS" || road.id == "ROAD_RAILWAY_STATION_EXIT"
    val isShoranurTermGateRoad = road.id == "ROAD_SHORANUR_TERMINAL_ENTRY" || road.id == "ROAD_SHORANUR_TERMINAL_EXIT"
    val isShoranurStationGateRoad = road.id == "ROAD_SHORANUR_STATION_ENTRY" || road.id == "ROAD_SHORANUR_STATION_EXIT"
    val isPeechiDamGateRoad = road.id == "ROAD_PEECHI_DAM_ENTRY" || road.id == "ROAD_PEECHI_DAM_EXIT"

    if (!isSakthanGateRoad && !isVadakkeGateRoad && !isStationGateRoad && !isShoranurTermGateRoad && !isShoranurStationGateRoad && !isPeechiDamGateRoad) {
        if (rig.buildVariableRibbonPath(path, frames, span.startIdx, span.endIdx, { -it.halfWidthM }, { it.halfWidthM }, yRoad)) {
            drawPath(path, MatAsphaltBase)
        }
        if (rig.buildVariableRibbonPath(path, frames, span.startIdx, span.endIdx, { -it.halfWidthM * 0.25f }, { it.halfWidthM * 0.25f }, yRoad)) {
            drawPath(path, MatAsphaltCrown)
        }
        return
    }

    fun isInsideTerminalCourtyard(f: RoadFrame): Boolean =
        (isSakthanGateRoad && f.pos.x in -30.8f..40.3f && f.pos.z in -207.2f..-139.8f) ||
            (isVadakkeGateRoad && f.pos.x in -64.5f..-6.5f && f.pos.z >= 200.5f) ||
            (isStationGateRoad && f.pos.x <= -64.0f && f.pos.z in -204.0f..-62.0f) ||
            (isShoranurTermGateRoad && f.pos.x >= 126.0f) ||
            (isShoranurStationGateRoad && f.pos.x <= 74.0f) ||
            (isPeechiDamGateRoad && f.pos.x >= 3412.0f)

    var runStart = span.startIdx
    while (runStart < span.endIdx) {
        while (runStart < span.endIdx && isInsideTerminalCourtyard(frames[runStart])) runStart++
        if (runStart >= span.endIdx) break
        var runEnd = runStart + 1
        while (runEnd < span.endIdx && !isInsideTerminalCourtyard(frames[runEnd])) runEnd++
        val drawStart = if (runStart > span.startIdx) runStart - 1 else runStart
        if (rig.buildVariableRibbonPath(path, frames, drawStart, runEnd, { -it.halfWidthM }, { it.halfWidthM }, yRoad)) {
            drawPath(path, MatAsphaltBase)
        }
        if (rig.buildVariableRibbonPath(path, frames, drawStart, runEnd, { -it.halfWidthM * 0.25f }, { it.halfWidthM * 0.25f }, yRoad)) {
            drawPath(path, MatAsphaltCrown)
        }
        runStart = runEnd
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
        if (rig.recorder == null && hypot(bm.centerPos.x - camEye.x, bm.centerPos.z - camEye.z) > 400f) continue
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
    if (rig.recorder == null && span.avgDistSq > 165f * 165f) return
    val road = span.road
    val frames = road.frames
    val yMark = 0.062f
    val sIdx = span.startIdx
    val eIdx = span.endIdx
    if (eIdx <= sIdx) return
    val step = if (rig.recorder == null && span.avgDistSq > 75f * 75f) 2 else 1

    fun drawSegment3D(w0: Vec3, w1: Vec3, widthM: Float, color: Color) {
        if (rig.recorder != null) {
            rig.recorder.emitWorldSegment(w0, w1, widthM, color)
            return
        }
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
        val isSakthanGateRoad = road.id == "ROAD_SAKTHAN_TO_SWARAJ" || road.id == "ROAD_SAKTHAN_STAND_ENTRY"
        val isVadakkeGateRoad = road.id == "ROAD_SWARAJ_TO_VADAKKE" || road.id == "ROAD_VADAKKE_STAND_EXIT"
        val isStationGateRoad = road.id == "ROAD_RAILWAY_STATION_ACCESS" || road.id == "ROAD_RAILWAY_STATION_EXIT"
        val isShoranurTermGateRoad = road.id == "ROAD_SHORANUR_TERMINAL_ENTRY" || road.id == "ROAD_SHORANUR_TERMINAL_EXIT"
        val isShoranurStationGateRoad = road.id == "ROAD_SHORANUR_STATION_ENTRY" || road.id == "ROAD_SHORANUR_STATION_EXIT"
        val isPeechiDamGateRoad = road.id == "ROAD_PEECHI_DAM_ENTRY" || road.id == "ROAD_PEECHI_DAM_EXIT"
        fun insideYard(f: RoadFrame): Boolean =
            (isSakthanGateRoad && f.pos.x in -30.8f..40.3f && f.pos.z in -207.2f..-139.8f) ||
                (isVadakkeGateRoad && f.pos.x in -64.5f..-6.5f && f.pos.z >= 200.5f) ||
                (isStationGateRoad && f.pos.x <= -64.0f && f.pos.z in -204.0f..-62.0f) ||
                (isShoranurTermGateRoad && f.pos.x >= 126.0f) ||
                (isShoranurStationGateRoad && f.pos.x <= 74.0f) ||
                (isPeechiDamGateRoad && f.pos.x >= 3412.0f)
        while (i < eIdx) {
            val nextIdx = min(i + step, eIdx)
            val f0 = frames[i]
            val f1 = frames[nextIdx]
            val cov0 = coveredFn(f0) || insideYard(f0)
            val cov1 = coveredFn(f1) || insideYard(f1)
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
            val y0 = f0.pos.y + (yMark - 0.05f)
            val y1 = f1.pos.y + (yMark - 0.05f)
            val w0 = Vec3(f0.pos.x + f0.right.x * off0, y0, f0.pos.z + f0.right.z * off0)
            val w1 = Vec3(f1.pos.x + f1.right.x * off1, y1, f1.pos.z + f1.right.z * off1)

            if (!cov0 && !cov1) {
                drawSegment3D(w0, w1, widthM, color)
            } else if (clipPartialTransition && !dashed) {
                // Extend centerline right up to the outer edge of the intersecting trunk road
                val mid = Vec3((w0.x + w1.x) * 0.5f, (w0.y + w1.y) * 0.5f, (w0.z + w1.z) * 0.5f)
                if (!cov0) {
                    drawSegment3D(w0, mid, widthM, color)
                } else {
                    drawSegment3D(mid, w1, widthM, color)
                }
            }
            i = nextIdx
        }
    }

    // 1. Single Crisp IRC Edge Lines (Continuous Reflective Yellow on NH 544 East 6-Lane Expressway, White elsewhere)
    val isNh544EastExp = road.id == "ROAD_NH544_MANNUTHY_TO_KUTHIRAN" || road.id == "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD"
    val isNh66CoastalExp = road.id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" || road.id == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM"
    val outerEdgeColor = if (isNh544EastExp || isNh66CoastalExp) MatRoadYellow else MatRoadWhite
    drawPerspectiveLine(
        lateralOffsetFn = { -it.halfWidthM + 0.38f },
        widthM = 0.16f,
        color = outerEdgeColor,
        dashed = false,
        clipPartialTransition = false
    ) { it.leftEdgeCovered }

    drawPerspectiveLine(
        lateralOffsetFn = { it.halfWidthM - 0.38f },
        widthM = 0.16f,
        color = outerEdgeColor,
        dashed = false,
        clipPartialTransition = false
    ) { it.rightEdgeCovered }

    // 2. Centerline / Divided Highway Median & Dashed Lane Lines (2-way 1+1, 3-lane Tunnel Tube, 4-way 2+2, 6-way 3+3)
    if (road.divided) {
        val medHw = 0.62f
        val isNh544TollCorridor = road.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" ||
            road.id == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY"
        val isNh66TollCorridor = road.id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR"
        fun suppressTollInterior(f: RoadFrame): Boolean =
            f.centerCovered ||
                (isNh544TollCorridor &&
                    f.pos.z in Zone3Nh544SouthRenderer.TOLL_FLARE_SOUTH_END_Z..Zone3Nh544SouthRenderer.TOLL_FLARE_NORTH_START_Z) ||
                (isNh66TollCorridor &&
                    f.pos.z in Zone5Nh66CoastalRenderer.NATTIKA_FLARE_SOUTH_END_Z..Zone5Nh66CoastalRenderer.NATTIKA_FLARE_NORTH_START_Z)
        var runStart = sIdx
        while (runStart < eIdx) {
            while (runStart < eIdx && suppressTollInterior(frames[runStart])) runStart++
            if (runStart >= eIdx) break
            var runEnd = runStart + 1
            while (runEnd < eIdx && !suppressTollInterior(frames[runEnd])) runEnd++
            if (rig.buildContinuousRibbonPath(path, frames, runStart, runEnd, -medHw, medHw, yMark)) {
                drawPath(path, MatMedianConcrete)
            }
            runStart = runEnd
        }
        drawPerspectiveLine({ -medHw - 0.20f }, 0.15f, MatRoadYellow, dashed = false, clipPartialTransition = true) { suppressTollInterior(it) }
        drawPerspectiveLine({ medHw + 0.20f }, 0.15f, MatRoadYellow, dashed = false, clipPartialTransition = true) { suppressTollInterior(it) }

        if (road.lanes >= 6) {
            // 6-Way (3+3) Divided National Highway (NH 544): 2 Dashed White Lane Dividers per Carriageway = 3 Lanes Each Way!
            drawPerspectiveLine({ -(medHw + (it.halfWidthM - medHw) * 0.333f) }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { suppressTollInterior(it) }
            drawPerspectiveLine({ -(medHw + (it.halfWidthM - medHw) * 0.667f) }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { suppressTollInterior(it) }
            drawPerspectiveLine({ medHw + (it.halfWidthM - medHw) * 0.333f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { suppressTollInterior(it) }
            drawPerspectiveLine({ medHw + (it.halfWidthM - medHw) * 0.667f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { suppressTollInterior(it) }
        } else {
            // 4-Way (2+2) Divided Highway: 1 Dashed White Lane Divider per Carriageway = 2 Lanes Each Way
            drawPerspectiveLine({ -(it.halfWidthM + medHw) * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { suppressTollInterior(it) }
            drawPerspectiveLine({ (it.halfWidthM + medHw) * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { suppressTollInterior(it) }
        }
    } else if (road.oneWayClockwise || road.id == "ROAD_SWARAJ_ROUND") {
        // Zone 1 Spec: Swaraj Round has 4 continuous lanes, strictly 1-way clockwise flow (14.0m asphalt carriageway)
        // Divided by 3 dashed white lane lines at -3.5m, 0.0m, and +3.5m
        drawPerspectiveLine({ -it.halfWidthM * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
        drawPerspectiveLine({ 0f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
        drawPerspectiveLine({ it.halfWidthM * 0.5f }, 0.14f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
    } else if (road.lanes == 3) {
        // Zone 2 Spec: Kuthiran Twin-Tube Tunnel (10.5m = 3 x 3.5m one-way lanes): 2 Dashed White Lane Dividers at -1.75m & +1.75m
        drawPerspectiveLine({ -it.halfWidthM * 0.333f }, 0.15f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
        drawPerspectiveLine({ it.halfWidthM * 0.333f }, 0.15f, MatRoadWhite, dashed = true, clipPartialTransition = false) { it.centerCovered }
    } else if (road.lanes >= 4) {
        // 4-Lane (2+2) Undivided 2-Way Arterial (M.O. Road 12.0m): IRC Double Yellow Centerline + Dashed Lane Dividers
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
            if (rig.recorder != null) {
                rig.recorder.emitWorldSegment(w0, w1, widthM, MatRoadWhite)
                continue
            }
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
        if (rig.recorder == null && hypot(bm.centerPos.x - camEye.x, bm.centerPos.z - camEye.z) > 400f) continue
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
/**
 * Zone 1 Spec: 8 White Thermoplastic Pedestrian Zebra Crossings (500mm wide bars, 500mm spacing, 3.0m length)
 * at all 8 radiating junctions around Swaraj Round:
 * 1. Manikandanal (South)
 * 2. Kuruppam Road
 * 3. Bini Tourist Home
 * 4. Round West (M.G. Road)
 * 5. Shoranur Road (North)
 * 6. Round North
 * 7. Palace Road (East)
 * 8. High Road
 */
private fun DrawScope.drawSwarajRound8ZebraCrossings(
    rig: CameraRig,
    path: Path,
    camEye: Vec3,
    roads: List<ThrissurOsmRoad>
) {
    if (hypot(camEye.x - 3.1f, camEye.z - 18.0f) > 250f) return
    val swarajRoad = roads.firstOrNull { it.id == "ROAD_SWARAJ_ROUND" } ?: return
    val frames = swarajRoad.frames
    if (frames.size < 16) return

    // Frame fractions corresponding to the 8 radiating junctions around expanded R=66m Swaraj Round (starts South phi=180 deg)
    val junctionFractions = floatArrayOf(
        0.964f, // 1. Manikandanal (South / M.O. Road)
        0.053f, // 2. Kuruppam / Station Road (South-West)
        0.125f, // 3. Bini Tourist Home (West-South-West)
        0.194f, // 4. Round West (M.G. Road)
        0.500f, // 5. Shoranur Road (North)
        0.625f, // 6. Round North (North-East)
        0.809f, // 7. Palace / Mannuthy Road (East)
        0.895f  // 8. High Road (South-East)
    )

    val yZebra = 0.064f
    val stripeHalfLen = 1.50f // 3.0m total stripe length along road tangent
    val barWidth = 0.50f      // 500mm wide white thermoplastic bar
    val barPitch = 1.00f      // 500mm bar + 500mm gap = 1.00m pitch

    for (frac in junctionFractions) {
        val fIdx = (frac * (frames.size - 1)).toInt().coerceIn(0, frames.size - 1)
        val f = frames[fIdx]
        if (hypot(f.pos.x - camEye.x, f.pos.z - camEye.z) > 135f) continue

        val hw = f.halfWidthM - 0.65f
        var lat = -hw + 0.35f
        while (lat + barWidth <= hw - 0.15f) {
            val l0 = lat
            val l1 = lat + barWidth
            val v0 = Vec3(
                f.pos.x + f.right.x * l0 - f.tangent.x * stripeHalfLen,
                yZebra,
                f.pos.z + f.right.z * l0 - f.tangent.z * stripeHalfLen
            )
            val v1 = Vec3(
                f.pos.x + f.right.x * l1 - f.tangent.x * stripeHalfLen,
                yZebra,
                f.pos.z + f.right.z * l1 - f.tangent.z * stripeHalfLen
            )
            val v2 = Vec3(
                f.pos.x + f.right.x * l1 + f.tangent.x * stripeHalfLen,
                yZebra,
                f.pos.z + f.right.z * l1 + f.tangent.z * stripeHalfLen
            )
            val v3 = Vec3(
                f.pos.x + f.right.x * l0 + f.tangent.x * stripeHalfLen,
                yZebra,
                f.pos.z + f.right.z * l0 + f.tangent.z * stripeHalfLen
            )
            if (rig.buildClippedQuadPath(path, v0, v1, v2, v3)) {
                drawPath(path, MatRoadWhite)
            }
            lat += barPitch
        }
    }

    // Also draw pedestrian stop line across M.O. Road approach at Manikandanal Signal (Z = -64.5m)
    val moRoad = roads.firstOrNull { it.id == "ROAD_SAKTHAN_TO_SWARAJ" }
    val moFrames = moRoad?.frames
    if (moFrames != null && moFrames.size >= 6) {
        val sigFrame = moFrames.minByOrNull { kotlin.math.abs(it.pos.z - (-64.5f)) } ?: moFrames[(moFrames.size - 4).coerceAtLeast(0)]
        if (hypot(sigFrame.pos.x - camEye.x, sigFrame.pos.z - camEye.z) <= 135f) {
            val sl0 = Vec3(
                sigFrame.pos.x - sigFrame.right.x * (sigFrame.halfWidthM - 0.4f),
                yZebra,
                sigFrame.pos.z - sigFrame.right.z * (sigFrame.halfWidthM - 0.4f)
            )
            val sl1 = Vec3(
                sigFrame.pos.x + sigFrame.right.x * (sigFrame.halfWidthM - 0.4f),
                yZebra,
                sigFrame.pos.z + sigFrame.right.z * (sigFrame.halfWidthM - 0.4f)
            )
            rig.projectSegmentClipped(sl0, sl1)?.let { seg ->
                drawLine(MatRoadWhite, seg.first, seg.second, strokeWidth = 4.2f)
            }
        }
    }
}

/**
 * Zone 1.2 Spec: Sakthan Thampuran Bus Stand — Rebuilt 10-Bay Linear Drive-Through Terminal
 * - Single Row of 10 Drive-Through Bus Bays in 1 line (X = -13.5m .. +36.0m, Z = -182.0m .. -164.0m)
 *   with Bay 8 (bIdx = 7) centered at X = 25.0m for the Player Bus spawn facing North (+Z)!
 * - Zero second row of rear slots — instead, the entire rear zone (Z = -208.0m .. -182.0m) is a wide
 *   26-meter Rear Circulation Carriageway connected via the West Entry Feeder Driveway to the
 *   North BUS ENTRY Gate (X = 1.0m .. 13.5m, Z = -140.0m)!
 * - Full 4-Sided Perimeter Compound Wall enclosing the terminal yard (North, East, South, West)
 * - Distinct Sakthan Stand Maroon-Terracotta & Gold Signboard Theme (#7F1D1D / #F59E0B)
 */
private fun DrawScope.drawSakthan20BayTerminalYard(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    drawSakthanModernTerminalYard(rig, path, camEye)
}

internal fun collectSakthanTerminalSceneFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    collectSakthanModernTerminalSceneFaces(camEye, outFaces)
}

internal fun collectAllSakthanTerminalStaticFaces(
    outFaces: MutableList<QuadFace3D>
) {
    collectAllSakthanModernTerminalStaticFaces(outFaces)
}

internal fun prepareSortedSceneFaces(
    rig: CameraRig,
    camEye: Vec3,
    rawFaces: List<QuadFace3D>,
    outSorted: MutableList<QuadFace3D>
) {
    outSorted.clear()
    val eyeX = camEye.x
    val eyeY = camEye.y
    val eyeZ = camEye.z
    val fwdX = rig.forward.x
    val fwdY = rig.forward.y
    val fwdZ = rig.forward.z

    for (i in 0 until rawFaces.size) {
        val face = rawFaces[i]
        val cx = (face.v0.x + face.v1.x + face.v2.x + face.v3.x) * 0.25f - eyeX
        val cy = (face.v0.y + face.v1.y + face.v2.y + face.v3.y) * 0.25f - eyeY
        val cz = (face.v0.z + face.v1.z + face.v2.z + face.v3.z) * 0.25f - eyeZ
        if (cx * fwdX + cy * fwdY + cz * fwdZ < -16f) continue

        if (face.signboard == null &&
            (face.nx != 0f || face.ny != 0f || face.nz != 0f) &&
            (cx * face.nx + cy * face.ny + cz * face.nz >= 0f)
        ) {
            continue
        }
        face.sortKey = sqrt(cx * cx + cy * cy + cz * cz) + face.biasDist
        outSorted.add(face)
    }
    outSorted.sortWith(FaceDepthComparator)
}

internal fun DrawScope.drawSceneFaceOrSignboard(
    rig: CameraRig,
    path: Path,
    face: QuadFace3D
) {
    val sb = face.signboard
    if (sb != null) {
        drawProjectiveSignboardQuad(rig, path, face.v0, face.v1, face.v2, face.v3, sb)
    } else if (rig.buildClippedQuadPath(path, face.v0, face.v1, face.v2, face.v3)) {
        drawPath(path, face.color)
    }
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
    facePool: ArrayList<BusQuadFace3D>,
    facesBuffer: ArrayList<BusQuadFace3D>,
    vertsBuf: FloatArray,
    colorsBuf: IntArray,
    batchVerts: FloatArray,
    batchColors: IntArray,
    vertexPaint: Paint,
    fallbackPaint: Paint,
    fallbackPath: android.graphics.Path
) {
    val pos = state.busPos
    val yaw = state.busHeadingRad
    val s = sin(yaw)
    val c = cos(yaw)

    val pitchSin = sin(state.bodyPitchRad)
    val pitchCos = cos(state.bodyPitchRad)
    val rollSin = sin(state.bodyRollRad)
    val rollCos = cos(state.bodyRollRad)

    fun busWorld(localX: Float, localY: Float, localZ: Float): Vec3 {
        val wx = pos.x + localX * c + localZ * s
        val wz = pos.z - localX * s + localZ * c
        return Vec3(wx, pos.y + localY, wz)
    }

    fun busWorldSuspended(localX: Float, localY: Float, localZ: Float): Vec3 {
        val dy0 = localY - 0.55f
        val dz0 = localZ - 0.10f
        val rx = localX * rollCos - dy0 * rollSin
        val ry1 = dy0 * rollCos + localX * rollSin
        val ry = 0.55f + ry1 * pitchCos - dz0 * pitchSin
        val rz = 0.10f + ry1 * pitchSin + dz0 * pitchCos
        return busWorld(rx, ry, rz)
    }

    // Camera position in bus-local coordinates for backface culling & front/rear/side decal visibility
    val eyeDx = rig.eye.x - pos.x
    val eyeDy = rig.eye.y - pos.y
    val eyeDz = rig.eye.z - pos.z
    val camLocalX = eyeDx * c - eyeDz * s
    val camLocalY = eyeDy
    val camLocalZ = eyeDx * s + eyeDz * c

    val halfL = 5.80f

    // 1. Projector Headlamp Ground Light Cones (Low Beam dipped vs High Beam long-range)
    if (state.headlightMode != HeadlightBeamMode.Off) {
        val isHighBeam = state.headlightMode == HeadlightBeamMode.High
        val outerLen = if (isHighBeam) 56f else 31f
        val outerHalfW = if (isHighBeam) 7.4f else 5.2f
        val outerCol = if (isHighBeam) Color(0x3AE0F2FE) else Color(0x26E0F2FE)
        val innerLen = if (isHighBeam) 30f else 15f
        val innerHalfW = if (isHighBeam) 3.8f else 2.6f
        val innerCol = if (isHighBeam) Color(0x4CF8FAFC) else Color(0x32F8FAFC)

        val b0 = busWorld(-1.10f, 0.075f, halfL + 0.2f)
        val b1 = busWorld(1.10f, 0.075f, halfL + 0.2f)
        val b2 = busWorld(outerHalfW, 0.075f, halfL + outerLen)
        val b3 = busWorld(-outerHalfW, 0.075f, halfL + outerLen)
        if (rig.buildClippedQuadPath(path, b0, b1, b2, b3)) {
            drawPath(path, outerCol)
        }
        val inner0 = busWorld(-0.90f, 0.076f, halfL + 0.2f)
        val inner1 = busWorld(0.90f, 0.076f, halfL + 0.2f)
        val inner2 = busWorld(innerHalfW, 0.076f, halfL + innerLen)
        val inner3 = busWorld(-innerHalfW, 0.076f, halfL + innerLen)
        if (rig.buildClippedQuadPath(path, inner0, inner1, inner2, inner3)) {
            drawPath(path, innerCol)
        }
    }

    // 2. Multi-Layer Soft Sun Shadow + Deep Chassis Umbra + 4 Tire Contact Patches
    val pen0 = busWorld(-1.26f + 0.72f, 0.062f, -6.38f - 0.78f)
    val pen1 = busWorld(1.26f + 0.72f, 0.062f, -6.38f - 0.78f)
    val pen2 = busWorld(1.26f + 0.72f, 0.062f, 5.90f - 0.78f)
    val pen3 = busWorld(-1.26f + 0.72f, 0.062f, 5.90f - 0.78f)
    if (rig.buildClippedQuadPath(path, pen0, pen1, pen2, pen3)) {
        drawPath(path, Color(0x3405080E))
    }

    val mid0 = busWorld(-1.38f, 0.063f, -6.36f)
    val mid1 = busWorld(1.38f, 0.063f, -6.36f)
    val mid2 = busWorld(1.38f, 0.063f, 5.94f)
    val mid3 = busWorld(-1.38f, 0.063f, 5.94f)
    if (rig.buildClippedQuadPath(path, mid0, mid1, mid2, mid3)) {
        drawPath(path, Color(0x4E05080E))
    }

    val umb0 = busWorld(-1.18f, 0.064f, -6.18f)
    val umb1 = busWorld(1.18f, 0.064f, -6.18f)
    val umb2 = busWorld(1.18f, 0.064f, 5.74f)
    val umb3 = busWorld(-1.18f, 0.064f, 5.74f)
    if (rig.buildClippedQuadPath(path, umb0, umb1, umb2, umb3)) {
        drawPath(path, Color(0x9404060A))
    }

    val tireCoords = arrayOf(
        Pair(-1.02f, 3.00f), Pair(1.02f, 3.00f),
        Pair(-0.96f, -2.80f), Pair(0.96f, -2.80f)
    )
    for ((tx, tz) in tireCoords) {
        val tc0 = busWorld(tx - 0.24f, 0.065f, tz - 0.56f)
        val tc1 = busWorld(tx + 0.24f, 0.065f, tz - 0.56f)
        val tc2 = busWorld(tx + 0.24f, 0.065f, tz + 0.56f)
        val tc3 = busWorld(tx - 0.24f, 0.065f, tz + 0.56f)
        if (rig.buildClippedQuadPath(path, tc0, tc1, tc2, tc3)) {
            drawPath(path, Color(0xB8020408))
        }
    }

    // 3. Transform & Smooth Gouraud + Automotive Clearcoat Lighting across all Hino RM 2 polygons
    val frontSteerRad = state.frontWheelSteerRad
    val ws = sin(frontSteerRad)
    val wc = cos(frontSteerRad)
    val spinSin = sin(state.wheelSpinRad)
    val spinCos = cos(state.wheelSpinRad)

    val brakeActive = state.brakeInput > 0.05f || state.isParkBrakeActive
    val revActive = state.gear == com.bharatbussim.game.model.TransmissionGear.Reverse
    val leftBlinkOn = state.blinkerPhase && (state.turnLeftOn || state.hazardsOn)
    val rightBlinkOn = state.blinkerPhase && (state.turnRightOn || state.hazardsOn)

    // Precomputed world-space afternoon sunlight direction
    val sunX = -0.398f
    val sunY = 0.777f
    val sunZ = 0.478f

    val eyeX = rig.eye.x
    val eyeY = rig.eye.y
    val eyeZ = rig.eye.z
    val basePosX = pos.x
    val basePosY = pos.y
    val basePosZ = pos.z

    var poolCursor = 0
    fun obtainFace(): BusQuadFace3D {
        return if (poolCursor < facePool.size) {
            facePool[poolCursor++]
        } else {
            BusQuadFace3D().also {
                facePool.add(it)
                poolCursor++
            }
        }
    }

    fun shadeVertex(
        pid: Int,
        localX: Float,
        localY: Float,
        localZ: Float,
        wx: Float,
        wy: Float,
        wz: Float,
        lnx: Float,
        lny: Float,
        lnz: Float,
        baseR: Float,
        baseG: Float,
        baseB: Float,
        alpha: Float
    ): Int {
        // World-space smooth normal rotated by bus heading (+ pitch/roll implicitly subtle)
        val wnx = lnx * c + lnz * s
        val wny = lny
        val wnz = -lnx * s + lnz * c

        val vxRaw = eyeX - wx
        val vyRaw = eyeY - wy
        val vzRaw = eyeZ - wz
        val vInvLen = 1f / sqrt(vxRaw * vxRaw + vyRaw * vyRaw + vzRaw * vzRaw).coerceAtLeast(0.01f)
        val vx = vxRaw * vInvLen
        val vy = vyRaw * vInvLen
        val vz = vzRaw * vInvLen

        val ndotl = wnx * sunX + wny * sunY + wnz * sunZ
        val wrappedDiffuse = ((ndotl + 0.48f) / 1.48f).coerceIn(0f, 1f)
        val rawNdotV = wnx * vx + wny * vy + wnz * vz
        val ndotv = abs(rawNdotV).coerceIn(0.05f, 1f)
        val fresnel1 = 1f - ndotv
        val fresnel2 = fresnel1 * fresnel1
        val fresnel4 = fresnel2 * fresnel2

        val hxRaw = sunX + vx
        val hyRaw = sunY + vy
        val hzRaw = sunZ + vz
        val hInvLen = 1f / sqrt(hxRaw * hxRaw + hyRaw * hyRaw + hzRaw * hzRaw).coerceAtLeast(0.01f)
        val ndoth = max(0f, wnx * (hxRaw * hInvLen) + wny * (hyRaw * hInvLen) + wnz * (hzRaw * hInvLen))

        // Environment reflection vector Y component for automotive sky/horizon/asphalt wrap
        val refY = (2f * ndotv * wny - vy).coerceIn(-1f, 1f)

        if (pid == 13) {
            // Smooth flush solar glass: Fresnel sky dome gradient + crisp horizon line + sharp sun glint
            val skyGrad = ((refY + 0.12f) / 1.12f).coerceIn(0f, 1f)
            val envR = 0.18f + 0.56f * skyGrad
            val envG = 0.24f + 0.62f * skyGrad
            val envB = 0.30f + 0.68f * skyGrad
            val horizonFlash = (1f - abs(refY) * 8f).coerceIn(0f, 1f) * 0.22f
            val reflFrac = (0.14f + 0.58f * (fresnel2 * fresnel1 * 0.5f + fresnel2 * 0.5f)).coerceIn(0.10f, 0.76f)
            val glint = ndoth.pow(52f) * 0.95f
            val rG = (baseR * (1f - reflFrac) + envR * reflFrac + horizonFlash + glint).coerceIn(0f, 1f)
            val gG = (baseG * (1f - reflFrac) + envG * reflFrac + horizonFlash * 1.05f + glint).coerceIn(0f, 1f)
            val bG = (baseB * (1f - reflFrac) + envB * reflFrac + horizonFlash * 1.12f + glint).coerceIn(0f, 1f)
            val outAlpha = (alpha * 0.82f + reflFrac * 0.20f + glint * 0.32f).coerceIn(0.46f, 0.92f)
            return packArgb(rG, gG, bG, outAlpha)
        }

        // Realistic 3D Automotive Polycarbonate Light Lenses & Internal Reflector Optics (Headlamps, Tail/Brake, Reverse, Blinkers/Side Markers)
        if (pid in 1..5) {
            val skyGrad = ((refY + 0.15f) / 1.15f).coerceIn(0f, 1f)
            val envR = 0.22f + 0.56f * skyGrad
            val envG = 0.26f + 0.62f * skyGrad
            val envB = 0.32f + 0.66f * skyGrad
            val lensGlint = ndoth.pow(52f) * 0.88f + ndoth.pow(14f) * 0.18f
            val lensFresnel = (0.12f + 0.42f * fresnel2).coerceIn(0.10f, 0.58f)
            val depthContour = (0.48f + 0.52f * wrappedDiffuse) * (0.62f + 0.38f * ndotv)

            when (pid) {
                1 -> {
                    // Front Projector Headlamps & Upper LED DRL Strip
                    if (state.headlightsOn) {
                        val core = (0.76f + 0.24f * ndotv).coerceIn(0f, 1f)
                        val rL = (0.86f * core + 0.14f * wrappedDiffuse + lensGlint * 0.5f).coerceIn(0f, 1f)
                        val gL = (0.93f * core + 0.07f * wrappedDiffuse + lensGlint * 0.5f).coerceIn(0f, 1f)
                        val bL = (0.99f * core + 0.04f * fresnel2 + lensGlint * 0.5f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    } else {
                        // Crystal polycarbonate lens over silver-chrome multi-parabola reflector
                        val chromeR = 0.74f * depthContour
                        val chromeG = 0.80f * depthContour
                        val chromeB = 0.88f * depthContour
                        val rL = (chromeR * (1f - lensFresnel) + envR * lensFresnel + lensGlint).coerceIn(0f, 1f)
                        val gL = (chromeG * (1f - lensFresnel) + envG * lensFresnel + lensGlint).coerceIn(0f, 1f)
                        val bL = (chromeB * (1f - lensFresnel) + envB * lensFresnel + lensGlint).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    }
                }
                2 -> {
                    // Rear Vertical LED Tail/Brake Lamp Clusters & Upper Clearance Lamps
                    if (brakeActive) {
                        val core = (0.72f + 0.28f * ndotv).coerceIn(0f, 1f)
                        val hotCore = ndotv * ndotv
                        val rL = (0.90f + 0.10f * core + lensGlint * 0.35f).coerceIn(0f, 1f)
                        val gL = (0.08f + 0.30f * hotCore + lensGlint * 0.35f).coerceIn(0f, 1f)
                        val bL = (0.06f + 0.18f * hotCore + lensGlint * 0.35f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    } else if (state.headlightsOn) {
                        // Illuminated ruby tail running lamps
                        val core = (0.65f + 0.35f * ndotv).coerceIn(0f, 1f)
                        val rL = (0.74f * core + 0.14f * wrappedDiffuse + lensGlint * 0.45f).coerceIn(0f, 1f)
                        val gL = (0.05f + 0.10f * (ndotv * ndotv) + lensGlint * 0.35f).coerceIn(0f, 1f)
                        val bL = (0.06f + 0.08f * (ndotv * ndotv) + lensGlint * 0.35f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    } else {
                        // Unlit deep ruby-red faceted polycarbonate tail lens
                        val rubyR = (baseR * 0.88f) * depthContour
                        val rubyG = (baseG * 0.65f) * depthContour
                        val rubyB = (baseB * 0.70f) * depthContour
                        val rL = (rubyR * (1f - lensFresnel * 0.45f) + envR * (lensFresnel * 0.45f) + lensGlint * 0.75f).coerceIn(0f, 1f)
                        val gL = (rubyG * (1f - lensFresnel * 0.45f) + envG * (lensFresnel * 0.45f) + lensGlint * 0.70f).coerceIn(0f, 1f)
                        val bL = (rubyB * (1f - lensFresnel * 0.45f) + envB * (lensFresnel * 0.45f) + lensGlint * 0.70f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    }
                }
                3 -> {
                    // Rear Reverse Lamp Housing
                    if (revActive) {
                        val core = (0.78f + 0.22f * ndotv).coerceIn(0f, 1f)
                        val rL = (0.92f * core + 0.08f * wrappedDiffuse + lensGlint * 0.45f).coerceIn(0f, 1f)
                        val gL = (0.95f * core + 0.05f * wrappedDiffuse + lensGlint * 0.45f).coerceIn(0f, 1f)
                        val bL = (0.98f * core + 0.04f * fresnel2 + lensGlint * 0.45f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    } else {
                        val revR = 0.68f * depthContour
                        val revG = 0.72f * depthContour
                        val revB = 0.78f * depthContour
                        val rL = (revR * (1f - lensFresnel * 0.6f) + envR * (lensFresnel * 0.6f) + lensGlint * 0.8f).coerceIn(0f, 1f)
                        val gL = (revG * (1f - lensFresnel * 0.6f) + envG * (lensFresnel * 0.6f) + lensGlint * 0.8f).coerceIn(0f, 1f)
                        val bL = (revB * (1f - lensFresnel * 0.6f) + envB * (lensFresnel * 0.6f) + lensGlint * 0.8f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    }
                }
                else -> {
                    // Front, Side & Rear Amber Turn Signal & Marker Lenses (pid == 4 || pid == 5)
                    val isBlinkOn = (pid == 4 && leftBlinkOn) || (pid == 5 && rightBlinkOn)
                    val isSideMarkerRunning = state.headlightsOn && abs(localZ) < 4.5f && !state.turnLeftOn && !state.turnRightOn && !state.hazardsOn
                    if (isBlinkOn || isSideMarkerRunning) {
                        val boost = if (isBlinkOn) 1.0f else 0.78f
                        val hotCore = ndotv * ndotv
                        val rL = ((0.94f + 0.06f * ndotv) * boost + lensGlint * 0.35f).coerceIn(0f, 1f)
                        val gL = ((0.54f + 0.32f * hotCore) * boost + lensGlint * 0.35f).coerceIn(0f, 1f)
                        val bL = ((0.04f + 0.16f * hotCore) * boost + lensGlint * 0.30f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    } else {
                        val ambLensR = (baseR * 0.86f) * depthContour
                        val ambLensG = (baseG * 0.78f) * depthContour
                        val ambLensB = (baseB * 0.65f) * depthContour
                        val rL = (ambLensR * (1f - lensFresnel * 0.45f) + envR * (lensFresnel * 0.45f) + lensGlint * 0.75f).coerceIn(0f, 1f)
                        val gL = (ambLensG * (1f - lensFresnel * 0.45f) + envG * (lensFresnel * 0.45f) + lensGlint * 0.70f).coerceIn(0f, 1f)
                        val bL = (ambLensB * (1f - lensFresnel * 0.45f) + envB * (lensFresnel * 0.45f) + lensGlint * 0.65f).coerceIn(0f, 1f)
                        return packArgb(rL, gL, bL, 1f)
                    }
                }
            }
        }

        val isWheel = pid in 6..11
        val isDarkTire = isWheel && baseR < 0.25f && baseB < 0.25f
        val isAlloyRim = isWheel && !isDarkTire
        val isMatteTrim = pid == 0 && baseR < 0.22f && baseG < 0.22f && baseB < 0.24f

        // Hemisphere sky/ground bounce
        val skyBounce = ((wny + 1f) * 0.5f).coerceIn(0f, 1f)
        val ambR = 0.58f + 0.36f * skyBounce
        val ambG = 0.58f + 0.40f * skyBounce
        val ambB = 0.57f + 0.45f * skyBounce

        // Subtle ambient occlusion near lower rocker skirt & rooftop AC pod gutter
        var ao = 1f
        if (!isWheel && localY < 0.70f) {
            ao *= (0.82f + 0.18f * ((localY - 0.40f) / 0.30f).coerceIn(0f, 1f))
        }
        if (isDarkTire && localY < 0.26f) {
            ao *= (0.72f + 0.28f * (localY / 0.26f).coerceIn(0f, 1f))
        }

        val lightFactor = (0.64f + 0.38f * wrappedDiffuse) * ao

        if (isDarkTire) {
            val sheen = ndoth.pow(10f) * 0.08f
            return packArgb(
                (baseR * ambR * lightFactor + sheen).coerceIn(0f, 1f),
                (baseG * ambG * lightFactor + sheen).coerceIn(0f, 1f),
                (baseB * ambB * lightFactor + sheen).coerceIn(0f, 1f),
                alpha
            )
        }

        if (isAlloyRim) {
            val metalRefl = (0.16f + 0.30f * fresnel2) * ((refY + 0.6f) * 0.5f).coerceIn(0.25f, 1f)
            val spec = ndoth.pow(32f) * 0.52f + ndoth.pow(8f) * 0.12f
            return packArgb(
                (baseR * ambR * lightFactor + metalRefl * 0.72f + spec).coerceIn(0f, 1f),
                (baseG * ambG * lightFactor + metalRefl * 0.80f + spec).coerceIn(0f, 1f),
                (baseB * ambB * lightFactor + metalRefl * 0.90f + spec * 0.96f).coerceIn(0f, 1f),
                alpha
            )
        }

        if (isMatteTrim) {
            val satinSpec = ndoth.pow(14f) * 0.11f + 0.05f * fresnel2
            return packArgb(
                (baseR * ambR * lightFactor + satinSpec * 0.85f).coerceIn(0f, 1f),
                (baseG * ambG * lightFactor + satinSpec * 0.88f).coerceIn(0f, 1f),
                (baseB * ambB * lightFactor + satinSpec * 0.95f).coerceIn(0f, 1f),
                alpha
            )
        }

        // Painted glossy automotive clearcoat coachwork with smooth sky/asphalt horizon reflection
        val skyEnvFactor = ((refY + 0.10f) / 1.10f).coerceIn(0f, 1f)
        val envColR = 0.24f + 0.58f * skyEnvFactor
        val envColG = 0.26f + 0.64f * skyEnvFactor
        val envColB = 0.30f + 0.70f * skyEnvFactor
        val clearcoatRefl = 0.04f + 0.30f * fresnel4 + 0.08f * fresnel2
        val spec = ndoth.pow(42f) * 0.36f + ndoth.pow(10f) * 0.10f

        val litR = baseR * ambR * lightFactor
        val litG = baseG * ambG * lightFactor
        val litB = baseB * ambB * lightFactor
        val rOut = (litR * (1f - clearcoatRefl) + envColR * clearcoatRefl + spec).coerceIn(0f, 1f)
        val gOut = (litG * (1f - clearcoatRefl) + envColG * clearcoatRefl + spec * 0.99f).coerceIn(0f, 1f)
        val bOut = (litB * (1f - clearcoatRefl) + envColB * clearcoatRefl + spec * 0.96f).coerceIn(0f, 1f)
        return packArgb(rOut, gOut, bOut, alpha)
    }

    fun emitWorldQuad(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        c0: Int, c1: Int, c2: Int, c3: Int,
        biasDist: Float,
        vertCount: Int = 4
    ) {
        val f = obtainFace()
        f.vertCount = vertCount
        f.x0 = x0; f.y0 = y0; f.z0 = z0
        f.x1 = x1; f.y1 = y1; f.z1 = z1
        f.x2 = x2; f.y2 = y2; f.z2 = z2
        f.x3 = x3; f.y3 = y3; f.z3 = z3
        f.c0 = c0; f.c1 = c1; f.c2 = c2; f.c3 = c3
        val cx = (x0 + x1 + x2 + x3) * 0.25f - eyeX
        val cy = (y0 + y1 + y2 + y3) * 0.25f - eyeY
        val cz = (z0 + z1 + z2 + z3) * 0.25f - eyeZ
        f.sortKey = sqrt(cx * cx + cy * cy + cz * cz) + biasDist
        facesBuffer.add(f)
    }

    facesBuffer.clear()
    val doorOpenT = state.doorOpenFrac.coerceIn(0f, 1f)
    val isDoorAnimated = doorOpenT > 0.005f
    val hideInteriorFromRear = camLocalZ < -2.2f && abs(camLocalX) < 1.32f && !isDoorAnimated

    // Single-leaf Hino RM 2 pneumatic outward-swinging plug door kinematics (opens toward the right / side body)
    val popPhase = (doorOpenT / 0.30f).coerceIn(0f, 1f)
    val popEase = popPhase * popPhase * (3f - 2f * popPhase)
    val glidePhase = ((doorOpenT - 0.12f) / 0.88f).coerceIn(0f, 1f)
    val glideEase = glidePhase * glidePhase * (3f - 2f * glidePhase)
    val doorOutX = -0.22f * popEase
    val doorFwdZ = -0.84f * glideEase
    val doorYawRad = glideEase * Math.toRadians(3.5).toFloat()
    val doorCos = cos(doorYawRad)
    val doorSin = sin(doorYawRad)
    val doorPivotX = -1.23f
    val doorPivotZ = 4.82f

    val swTiltRad = Math.toRadians(38.0).toFloat()
    val swCt = cos(swTiltRad)
    val swSt = sin(swTiltRad)
    val swRotRad = Math.toRadians((-state.steeringWheelAngleDeg).toDouble()).toFloat()
    val swCos = cos(swRotRad)
    val swSin = sin(swRotRad)

    for (i in 0 until hinoMesh.size) {
        val poly = hinoMesh[i]
        val animateThisDoorPoly = isDoorAnimated && poly.isDoorLeaf
        val pid = poly.partId
        val vcnt = poly.vertCount
        val cm = poly.cullMode

        var showExterior = true
        var showInterior = false

        if (animateThisDoorPoly) {
            val cdx = poly.cx - doorPivotX
            val cdz = poly.cz - doorPivotZ
            val dcx = doorPivotX + doorOutX + cdx * doorCos + cdz * doorSin
            val dcz = doorPivotZ + doorFwdZ - cdx * doorSin + cdz * doorCos
            val dnx = poly.nx * doorCos + poly.nz * doorSin
            val dnz = -poly.nx * doorSin + poly.nz * doorCos
            val dot = (dcx - camLocalX) * dnx + (poly.cy - camLocalY) * poly.ny + (dcz - camLocalZ) * dnz
            showExterior = dot < 0.08f
            showInterior = dot > -0.08f
            if (!showExterior && !showInterior) continue
        } else {
            if (cm == 2 && hideInteriorFromRear) {
                continue
            } else if (cm == 1) {
                val dot = (poly.cx - camLocalX) * poly.nx + (poly.cy - camLocalY) * poly.ny + (poly.cz - camLocalZ) * poly.nz
                if (dot >= 0.08f) continue
            }
        }

        // Transform all 3 or 4 corners without Vec3 allocations
        var wx0 = 0f; var wy0 = 0f; var wz0 = 0f
        var wx1 = 0f; var wy1 = 0f; var wz1 = 0f
        var wx2 = 0f; var wy2 = 0f; var wz2 = 0f
        var wx3 = 0f; var wy3 = 0f; var wz3 = 0f

        for (k in 0 until vcnt) {
            val lx = when (k) { 0 -> poly.x0; 1 -> poly.x1; 2 -> poly.x2; else -> poly.x3 }
            val ly = when (k) { 0 -> poly.y0; 1 -> poly.y1; 2 -> poly.y2; else -> poly.y3 }
            val lz = when (k) { 0 -> poly.z0; 1 -> poly.z1; 2 -> poly.z2; else -> poly.z3 }

            val tx: Float; val ty: Float; val tz: Float
            if (pid in 6..11) {
                val isFront = pid == 6 || pid == 7
                val axleZ = if (isFront) 3.00f else -2.80f
                val dy = ly - 0.52f
                val dz = lz - axleZ
                val sy = 0.52f + dy * spinCos - dz * spinSin
                val sz = axleZ + dy * spinSin + dz * spinCos
                if (pid == 6) {
                    val dx = lx + 1.02f
                    val dzs = sz - 3.00f
                    tx = -1.02f + dx * wc + dzs * ws
                    ty = sy
                    tz = 3.00f - dx * ws + dzs * wc
                } else if (pid == 7) {
                    val dx = lx - 1.02f
                    val dzs = sz - 3.00f
                    tx = 1.02f + dx * wc + dzs * ws
                    ty = sy
                    tz = 3.00f - dx * ws + dzs * wc
                } else {
                    tx = lx; ty = sy; tz = sz
                }
            } else if (pid == 12) {
                // Rotate interior 3D steering wheel around its tilted column axis (0.62, 1.94, 5.12)
                val rx = lx - 0.62f
                val dyW = ly - 1.94f
                val dzW = lz - 5.12f
                val ryLocal = dyW * swCt - dzW * swSt
                val rzLocal = -dyW * swSt - dzW * swCt
                val rxRot = rx * swCos - ryLocal * swSin
                val ryRot = rx * swSin + ryLocal * swCos
                val sx = 0.62f + rxRot
                val sy = 1.94f + ryRot * swCt - rzLocal * swSt
                val sz = 5.12f - ryRot * swSt - rzLocal * swCt
                val dy0 = sy - 0.55f
                val dz0 = sz - 0.10f
                tx = sx * rollCos - dy0 * rollSin
                val ry1 = dy0 * rollCos + sx * rollSin
                ty = 0.55f + ry1 * pitchCos - dz0 * pitchSin
                tz = 0.10f + ry1 * pitchSin + dz0 * pitchCos
            } else {
                val dlx: Float; val dly: Float; val dlz: Float
                if (animateThisDoorPoly) {
                    val dxD = lx - doorPivotX
                    val dzD = lz - doorPivotZ
                    dlx = doorPivotX + doorOutX + dxD * doorCos + dzD * doorSin
                    dly = ly
                    dlz = doorPivotZ + doorFwdZ - dxD * doorSin + dzD * doorCos
                } else {
                    dlx = lx; dly = ly; dlz = lz
                }
                val dy0 = dly - 0.55f
                val dz0 = dlz - 0.10f
                tx = dlx * rollCos - dy0 * rollSin
                val ry1 = dy0 * rollCos + dlx * rollSin
                ty = 0.55f + ry1 * pitchCos - dz0 * pitchSin
                tz = 0.10f + ry1 * pitchSin + dz0 * pitchCos
            }

            val wx = basePosX + tx * c + tz * s
            val wy = basePosY + ty
            val wz = basePosZ - tx * s + tz * c
            when (k) {
                0 -> { wx0 = wx; wy0 = wy; wz0 = wz }
                1 -> { wx1 = wx; wy1 = wy; wz1 = wz }
                2 -> { wx2 = wx; wy2 = wy; wz2 = wz }
                else -> { wx3 = wx; wy3 = wy; wz3 = wz }
            }
        }
        if (vcnt == 3) {
            wx3 = wx2; wy3 = wy2; wz3 = wz2
        }

        if (animateThisDoorPoly) {
            val rnx0 = poly.nx0 * doorCos + poly.nz0 * doorSin
            val rnz0 = -poly.nx0 * doorSin + poly.nz0 * doorCos
            val rnx1 = poly.nx1 * doorCos + poly.nz1 * doorSin
            val rnz1 = -poly.nx1 * doorSin + poly.nz1 * doorCos
            val rnx2 = poly.nx2 * doorCos + poly.nz2 * doorSin
            val rnz2 = -poly.nx2 * doorSin + poly.nz2 * doorCos
            val rnx3 = poly.nx3 * doorCos + poly.nz3 * doorSin
            val rnz3 = -poly.nx3 * doorSin + poly.nz3 * doorCos

            if (showExterior) {
                val c0 = shadeVertex(pid, poly.x0, poly.y0, poly.z0, wx0, wy0, wz0, rnx0, poly.ny0, rnz0, poly.r0, poly.g0, poly.b0, poly.alpha)
                val c1 = shadeVertex(pid, poly.x1, poly.y1, poly.z1, wx1, wy1, wz1, rnx1, poly.ny1, rnz1, poly.r1, poly.g1, poly.b1, poly.alpha)
                val c2 = shadeVertex(pid, poly.x2, poly.y2, poly.z2, wx2, wy2, wz2, rnx2, poly.ny2, rnz2, poly.r2, poly.g2, poly.b2, poly.alpha)
                val c3 = if (vcnt == 4) {
                    shadeVertex(pid, poly.x3, poly.y3, poly.z3, wx3, wy3, wz3, rnx3, poly.ny3, rnz3, poly.r3, poly.g3, poly.b3, poly.alpha)
                } else c2
                emitWorldQuad(
                    wx0, wy0, wz0,
                    wx1, wy1, wz1,
                    wx2, wy2, wz2,
                    wx3, wy3, wz3,
                    c0, c1, c2, c3,
                    poly.biasDist,
                    vcnt
                )
            }
            if (showInterior) {
                // Interior door face: matching tinted glass for pid==13, or charcoal cabin trim liner (offset 2.4cm inward) for pid==0
                val ir0 = if (pid == 13) poly.r0 else 0.15f
                val ig0 = if (pid == 13) poly.g0 else 0.17f
                val ib0 = if (pid == 13) poly.b0 else 0.21f
                val ir1 = if (pid == 13) poly.r1 else 0.15f
                val ig1 = if (pid == 13) poly.g1 else 0.17f
                val ib1 = if (pid == 13) poly.b1 else 0.21f
                val ir2 = if (pid == 13) poly.r2 else 0.15f
                val ig2 = if (pid == 13) poly.g2 else 0.17f
                val ib2 = if (pid == 13) poly.b2 else 0.21f
                val ir3 = if (pid == 13) poly.r3 else 0.15f
                val ig3 = if (pid == 13) poly.g3 else 0.17f
                val ib3 = if (pid == 13) poly.b3 else 0.21f

                val thick = if (pid == 13) 0.008f else 0.024f
                val inLocalX = -doorCos * thick
                val inLocalZ = doorSin * thick
                val offWx = inLocalX * c + inLocalZ * s
                val offWz = -inLocalX * s + inLocalZ * c

                val iwx0 = wx0 - offWx; val iwz0 = wz0 - offWz
                val iwx1 = wx1 - offWx; val iwz1 = wz1 - offWz
                val iwx2 = wx2 - offWx; val iwz2 = wz2 - offWz
                val iwx3 = wx3 - offWx; val iwz3 = wz3 - offWz

                val ic0 = shadeVertex(pid, poly.x0, poly.y0, poly.z0, iwx0, wy0, iwz0, -rnx0, -poly.ny0, -rnz0, ir0, ig0, ib0, poly.alpha)
                val ic1 = shadeVertex(pid, poly.x1, poly.y1, poly.z1, iwx1, wy1, iwz1, -rnx1, -poly.ny1, -rnz1, ir1, ig1, ib1, poly.alpha)
                val ic2 = shadeVertex(pid, poly.x2, poly.y2, poly.z2, iwx2, wy2, iwz2, -rnx2, -poly.ny2, -rnz2, ir2, ig2, ib2, poly.alpha)
                val ic3 = if (vcnt == 4) {
                    shadeVertex(pid, poly.x3, poly.y3, poly.z3, iwx3, wy3, iwz3, -rnx3, -poly.ny3, -rnz3, ir3, ig3, ib3, poly.alpha)
                } else ic2
                emitWorldQuad(
                    iwx0, wy0, iwz0,
                    iwx1, wy1, iwz1,
                    iwx2, wy2, iwz2,
                    iwx3, wy3, iwz3,
                    ic0, ic1, ic2, ic3,
                    poly.biasDist + 0.02f,
                    vcnt
                )
            }
        } else {
            val c0 = shadeVertex(pid, poly.x0, poly.y0, poly.z0, wx0, wy0, wz0, poly.nx0, poly.ny0, poly.nz0, poly.r0, poly.g0, poly.b0, poly.alpha)
            val c1 = shadeVertex(pid, poly.x1, poly.y1, poly.z1, wx1, wy1, wz1, poly.nx1, poly.ny1, poly.nz1, poly.r1, poly.g1, poly.b1, poly.alpha)
            val c2 = shadeVertex(pid, poly.x2, poly.y2, poly.z2, wx2, wy2, wz2, poly.nx2, poly.ny2, poly.nz2, poly.r2, poly.g2, poly.b2, poly.alpha)
            val c3 = if (vcnt == 4) {
                shadeVertex(pid, poly.x3, poly.y3, poly.z3, wx3, wy3, wz3, poly.nx3, poly.ny3, poly.nz3, poly.r3, poly.g3, poly.b3, poly.alpha)
            } else {
                c2
            }

            emitWorldQuad(
                wx0, wy0, wz0,
                wx1, wy1, wz1,
                wx2, wy2, wz2,
                wx3, wy3, wz3,
                c0, c1, c2, c3,
                poly.biasDist,
                vcnt
            )
        }
    }

    // 3b. Depth-sorted Rear Rubber Mudflaps with Retro-Reflector Strips (behind rear duals)
    val swayZ = -3.64f - (state.speedKmh / 180f) * 0.14f
    for (side in intArrayOf(-1, 1)) {
        val mx0 = side * 0.84f
        val mx1 = side * 1.18f
        val m0 = busWorldSuspended(mx0, 0.13f, swayZ)
        val m1 = busWorldSuspended(mx1, 0.13f, swayZ)
        val m2 = busWorldSuspended(mx1, 0.48f, -3.56f)
        val m3 = busWorldSuspended(mx0, 0.48f, -3.56f)
        val mudCol = 0xFF14171D.toInt()
        emitWorldQuad(m0.x, m0.y, m0.z, m1.x, m1.y, m1.z, m2.x, m2.y, m2.z, m3.x, m3.y, m3.z, mudCol, mudCol, mudCol, mudCol, -0.02f)

        val r0 = busWorldSuspended(mx0 + side * 0.04f, 0.16f, swayZ - 0.015f)
        val r1 = busWorldSuspended(mx1 - side * 0.04f, 0.16f, swayZ - 0.015f)
        val r2 = busWorldSuspended(mx1 - side * 0.04f, 0.21f, swayZ - 0.015f)
        val r3 = busWorldSuspended(mx0 + side * 0.04f, 0.21f, swayZ - 0.015f)
        val reflCol = 0xFFE2E8F0.toInt()
        emitWorldQuad(r0.x, r0.y, r0.z, r1.x, r1.y, r1.z, r2.x, r2.y, r2.z, r3.x, r3.y, r3.z, reflCol, reflCol, reflCol, reflCol, -0.04f)
    }

    // 3c. Realistic 3D Recessed Stepwell & Single-Leaf Pneumatic Plug Door Edge Seals + Chrome Arms
    if (isDoorAnimated && camLocalX < 1.35f) {
        fun emitShadedLocalQuad(
            lx0: Float, ly0: Float, lz0: Float,
            lx1: Float, ly1: Float, lz1: Float,
            lx2: Float, ly2: Float, lz2: Float,
            lx3: Float, ly3: Float, lz3: Float,
            lnx: Float, lny: Float, lnz: Float,
            r: Float, g: Float, b: Float,
            alpha: Float = 1f,
            pid: Int = 0,
            biasDist: Float = 0f
        ) {
            val w0 = busWorldSuspended(lx0, ly0, lz0)
            val w1 = busWorldSuspended(lx1, ly1, lz1)
            val w2 = busWorldSuspended(lx2, ly2, lz2)
            val w3 = busWorldSuspended(lx3, ly3, lz3)
            val col0 = shadeVertex(pid, lx0, ly0, lz0, w0.x, w0.y, w0.z, lnx, lny, lnz, r, g, b, alpha)
            val col1 = shadeVertex(pid, lx1, ly1, lz1, w1.x, w1.y, w1.z, lnx, lny, lnz, r, g, b, alpha)
            val col2 = shadeVertex(pid, lx2, ly2, lz2, w2.x, w2.y, w2.z, lnx, lny, lnz, r, g, b, alpha)
            val col3 = shadeVertex(pid, lx3, ly3, lz3, w3.x, w3.y, w3.z, lnx, lny, lnz, r, g, b, alpha)
            emitWorldQuad(
                w0.x, w0.y, w0.z,
                w1.x, w1.y, w1.z,
                w2.x, w2.y, w2.z,
                w3.x, w3.y, w3.z,
                col0, col1, col2, col3,
                biasDist
            )
        }

        // Front & Rear Door Jamb Frame Moldings + Upper Stepwell Header (seals the doorway opening cleanly)
        val zRearJamb = 4.392f
        val zFrontJamb = 5.148f
        // Front vertical matte-black rubber jamb pillar bridging 5.148..5.242 along A-pillar edge
        emitShadedLocalQuad(
            -1.238f, 0.485f, zFrontJamb,
            -1.238f, 0.485f, 5.238f,
            -1.238f, 2.485f, 5.115f,
            -1.238f, 2.485f, zFrontJamb,
            -1f, 0f, 0f,
            0.10f, 0.12f, 0.15f,
            biasDist = -0.02f
        )
        // Rear & Front inner door jamb cheeks (charcoal cabin trim)
        emitShadedLocalQuad(
            -1.238f, 0.49f, zRearJamb,
            -0.78f, 0.49f, zRearJamb,
            -0.78f, 2.48f, zRearJamb,
            -1.238f, 2.48f, zRearJamb,
            0f, 0f, 1f,
            0.15f, 0.17f, 0.21f,
            biasDist = 4.2f
        )
        emitShadedLocalQuad(
            -0.78f, 0.49f, zFrontJamb,
            -1.238f, 0.49f, zFrontJamb,
            -1.238f, 2.48f, zFrontJamb,
            -0.78f, 2.48f, zFrontJamb,
            0f, 0f, -1f,
            0.15f, 0.17f, 0.21f,
            biasDist = 4.2f
        )
        // Upper doorway header & recessed warm courtesy LED light strip
        emitShadedLocalQuad(
            -1.238f, 2.482f, zRearJamb,
            -0.78f, 2.482f, zRearJamb,
            -0.78f, 2.482f, zFrontJamb,
            -1.238f, 2.482f, zFrontJamb,
            0f, -1f, 0f,
            0.13f, 0.15f, 0.19f,
            biasDist = 4.2f
        )
        emitShadedLocalQuad(
            -1.15f, 2.475f, 4.66f,
            -1.07f, 2.475f, 4.66f,
            -1.07f, 2.475f, 4.90f,
            -1.15f, 2.475f, 4.90f,
            0f, -1f, 0f,
            1.0f, 0.94f, 0.72f,
            biasDist = 4.1f
        )

        // Three Recessed 3D Non-Slip Entry Steps with Crisp Safety Nosing Strips
        val stepTreads = arrayOf(
            floatArrayOf(-1.225f, -1.005f, 0.480f, 0.530f, 4.400f, 5.140f),
            floatArrayOf(-1.005f, -0.785f, 0.530f, 0.760f, 4.400f, 5.140f),
            floatArrayOf(-0.785f, -0.540f, 0.760f, 0.995f, 4.400f, 5.140f)
        )
        for (stIdx in stepTreads.indices) {
            val st = stepTreads[stIdx]
            val xo = st[0]; val xi = st[1]; val yPrev = st[2]; val ys = st[3]; val zs = st[4]; val ze = st[5]
            emitShadedLocalQuad(
                xo, yPrev, zs,
                xo, yPrev, ze,
                xo, ys, ze,
                xo, ys, zs,
                -1f, 0.1f, 0f,
                if (stIdx == 0) 0.34f else 0.14f,
                if (stIdx == 0) 0.37f else 0.16f,
                if (stIdx == 0) 0.42f else 0.20f,
                biasDist = 4.0f
            )
            emitShadedLocalQuad(
                xo + 0.035f, ys, zs,
                xi, ys, zs,
                xi, ys, ze,
                xo + 0.035f, ys, ze,
                0f, 1f, 0f,
                0.15f, 0.17f, 0.21f,
                biasDist = 3.9f
            )
            emitShadedLocalQuad(
                xo, ys + 0.004f, zs,
                xo + 0.035f, ys + 0.004f, zs,
                xo + 0.035f, ys + 0.004f, ze,
                xo, ys + 0.004f, ze,
                0f, 1f, 0f,
                0.88f, 0.68f, 0.12f,
                biasDist = 3.85f
            )
        }

        // Brushed Stainless-Steel Entry Handrail along Rear Jamb
        val railB0 = -1.04f; val railBy0 = 0.92f
        val railT0 = -0.76f; val railTy0 = 1.68f
        emitShadedLocalQuad(
            railB0, railBy0, 4.455f,
            railT0, railTy0, 4.455f,
            railT0, railTy0 + 0.032f, 4.480f,
            railB0, railBy0 + 0.032f, 4.480f,
            -0.5f, 0.5f, 0.7f,
            0.78f, 0.82f, 0.88f,
            biasDist = 3.5f
        )
        emitShadedLocalQuad(
            -0.76f, 0.995f, 4.455f,
            -0.73f, 0.995f, 4.455f,
            -0.73f, 2.42f, 4.480f,
            -0.76f, 2.42f, 4.480f,
            -0.7f, 0f, 0.7f,
            0.78f, 0.82f, 0.88f,
            biasDist = 3.5f
        )

        // Single-Leaf Plug Door 3D Rubber Edge Seals & Dual Chrome Parallelogram Swing Arms
        fun doorPlugLocalX(lx: Float, lz: Float): Float {
            val dxD = lx - doorPivotX
            val dzD = lz - doorPivotZ
            return doorPivotX + doorOutX + dxD * doorCos + dzD * doorSin
        }
        fun doorPlugLocalZ(lx: Float, lz: Float): Float {
            val dxD = lx - doorPivotX
            val dzD = lz - doorPivotZ
            return doorPivotZ + doorFwdZ - dxD * doorSin + dzD * doorCos
        }

        val dOuterX = -1.232f
        val dInnerX = -1.208f
        // Rear vertical rubber seal edge (gives the moving single door realistic 2.4 cm 3D thickness)
        val rOx = doorPlugLocalX(dOuterX, 4.384f); val rOz = doorPlugLocalZ(dOuterX, 4.384f)
        val rIx = doorPlugLocalX(dInnerX, 4.384f); val rIz = doorPlugLocalZ(dInnerX, 4.384f)
        emitShadedLocalQuad(
            rOx, 0.495f, rOz,
            rIx, 0.495f, rIz,
            rIx, 2.480f, rIz,
            rOx, 2.480f, rOz,
            -doorSin, 0f, -doorCos,
            0.09f, 0.10f, 0.13f,
            biasDist = 0.01f
        )
        // Front slanted rubber seal edge matching the Hino RM2 A-pillar profile
        val fBotOx = doorPlugLocalX(dOuterX, 5.235f); val fBotOz = doorPlugLocalZ(dOuterX, 5.235f)
        val fBotIx = doorPlugLocalX(dInnerX, 5.235f); val fBotIz = doorPlugLocalZ(dInnerX, 5.235f)
        val fTopOx = doorPlugLocalX(dOuterX, 5.070f); val fTopOz = doorPlugLocalZ(dOuterX, 5.070f)
        val fTopIx = doorPlugLocalX(dInnerX, 5.070f); val fTopIz = doorPlugLocalZ(dInnerX, 5.070f)
        emitShadedLocalQuad(
            fBotIx, 0.495f, fBotIz,
            fBotOx, 0.495f, fBotOz,
            fTopOx, 2.480f, fTopOz,
            fTopIx, 2.480f, fTopIz,
            doorSin, 0f, doorCos,
            0.09f, 0.10f, 0.13f,
            biasDist = 0.01f
        )

        // Upper & Lower Heavy-Duty Chrome Pneumatic Parallelogram Swing Arms connecting rear doorway jamb to the open single door
        for (armY in floatArrayOf(0.62f, 2.38f)) {
            val anchorX = -1.165f
            val anchorZ = 4.440f
            val doorBracketX = doorPlugLocalX(dInnerX + 0.01f, 4.54f)
            val doorBracketZ = doorPlugLocalZ(dInnerX + 0.01f, 4.54f)
            emitShadedLocalQuad(
                anchorX, armY, anchorZ,
                doorBracketX, armY, doorBracketZ,
                doorBracketX, armY + 0.026f, doorBracketZ - 0.022f,
                anchorX, armY + 0.026f, anchorZ - 0.022f,
                0f, 1f, 0f,
                0.76f, 0.80f, 0.86f,
                biasDist = 0.02f
            )
            emitShadedLocalQuad(
                anchorX, armY - 0.020f, anchorZ,
                doorBracketX, armY - 0.020f, doorBracketZ,
                doorBracketX, armY + 0.026f, doorBracketZ,
                anchorX, armY + 0.026f, anchorZ,
                -0.6f, 0f, 0.8f,
                0.68f, 0.72f, 0.78f,
                biasDist = 0.02f
            )
        }
    }

    facesBuffer.sortWith(BusFaceDepthComparator)

    // Emit all sorted triangles into hardware-accelerated Gouraud vertex buffer
    var triVertCount = 0
    for (i in 0 until facesBuffer.size) {
        triVertCount = rig.projectBusFaceToTriangles(facesBuffer[i], vertsBuf, colorsBuf, triVertCount)
    }

    if (triVertCount >= 3) {
        val nc = drawContext.canvas.nativeCanvas
        var renderedWithVertices = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                val maxBatchVerts = 18000
                var offsetVert = 0
                while (offsetVert + 2 < triVertCount) {
                    val countVert = min(maxBatchVerts, triVertCount - offsetVert)
                    val alignedCount = countVert - (countVert % 3)
                    if (alignedCount < 3) break
                    if (offsetVert == 0) {
                        nc.drawVertices(
                            android.graphics.Canvas.VertexMode.TRIANGLES,
                            alignedCount * 2,
                            vertsBuf,
                            0,
                            null,
                            0,
                            colorsBuf,
                            0,
                            null,
                            0,
                            0,
                            vertexPaint
                        )
                    } else {
                        System.arraycopy(vertsBuf, offsetVert * 2, batchVerts, 0, alignedCount * 2)
                        System.arraycopy(colorsBuf, offsetVert, batchColors, 0, alignedCount)
                        nc.drawVertices(
                            android.graphics.Canvas.VertexMode.TRIANGLES,
                            alignedCount * 2,
                            batchVerts,
                            0,
                            null,
                            0,
                            batchColors,
                            0,
                            null,
                            0,
                            0,
                            vertexPaint
                        )
                    }
                    offsetVert += alignedCount
                }
                renderedWithVertices = true
            } catch (_: Throwable) {
                renderedWithVertices = false
            }
        }
        if (!renderedWithVertices) {
            var v = 0
            while (v + 2 < triVertCount) {
                val vi = v * 2
                fallbackPath.reset()
                fallbackPath.moveTo(vertsBuf[vi], vertsBuf[vi + 1])
                fallbackPath.lineTo(vertsBuf[vi + 2], vertsBuf[vi + 3])
                fallbackPath.lineTo(vertsBuf[vi + 4], vertsBuf[vi + 5])
                fallbackPath.close()
                fallbackPaint.color = colorsBuf[v]
                nc.drawPath(fallbackPath, fallbackPaint)
                v += 3
            }
        }
    }


    // 4. Perspective-Aligned 3D Automotive Glass Glare, Recessed Grille Details & 3D Rectangular/Linear Light Bars (Zero Text, Zero 2D Dots)
    if (camLocalZ > 2.0f) {
        // Subtle recessed front radiator intake slats
        for (gy in floatArrayOf(0.84f, 0.91f, 0.98f)) {
            val gSeg = rig.projectSegmentClipped(
                busWorldSuspended(-0.46f, gy, 5.815f),
                busWorldSuspended(0.46f, gy, 5.815f)
            )
            if (gSeg != null) {
                drawLine(Color(0x990F172A), gSeg.first, gSeg.second, strokeWidth = 2.0f)
            }
        }

        // Front Windshield Diagonal Sky Specular Reflection Glare
        val fg0 = busWorldSuspended(-0.92f, 1.62f, 5.58f)
        val fg1 = busWorldSuspended(-0.48f, 1.56f, 5.66f)
        val fg2 = busWorldSuspended(-0.08f, 2.60f, 5.34f)
        val fg3 = busWorldSuspended(-0.56f, 2.58f, 5.32f)
        if (rig.buildClippedQuadPath(path, fg0, fg1, fg2, fg3)) {
            drawPath(path, Color(0x24BAE6FD))
        }

        // Front LED Daytime Running Light eyebrows & 3D Rectangular Headlamp Lens Glare when illuminated
        for (hx in floatArrayOf(-0.88f, 0.88f)) {
            val drlSeg = rig.projectSegmentClipped(
                busWorldSuspended(hx - 0.16f, 0.91f, 5.80f),
                busWorldSuspended(hx + 0.16f, 0.91f, 5.80f)
            )
            if (drlSeg != null) {
                val drlCol = if (state.headlightsOn) Color(0xF2F0F9FF) else Color(0x88CBD5E1)
                drawLine(drlCol, drlSeg.first, drlSeg.second, strokeWidth = if (state.headlightsOn) 2.8f else 1.8f, cap = StrokeCap.Round)
            }
            if (state.headlightMode != HeadlightBeamMode.Off) {
                val lensGlowCol = if (state.headlightMode == HeadlightBeamMode.High) Color(0x7AE0F2FE) else Color(0x40E0F2FE)
                val hl0 = busWorldSuspended(hx - 0.17f, 0.72f, 5.81f)
                val hl1 = busWorldSuspended(hx + 0.17f, 0.72f, 5.81f)
                val hl2 = busWorldSuspended(hx + 0.17f, 0.88f, 5.81f)
                val hl3 = busWorldSuspended(hx - 0.17f, 0.88f, 5.81f)
                if (rig.buildClippedQuadPath(path, hl0, hl1, hl2, hl3)) {
                    drawPath(path, lensGlowCol)
                }
            }
        }
    }

    // Left & Right Side Flush Luggage Panel Seams
    for (side in intArrayOf(-1, 1)) {
        val isVisibleSide = (side == -1 && camLocalX < -0.85f && camLocalZ > -5.5f) ||
            (side == 1 && camLocalX > 0.85f && camLocalZ > -5.5f)
        if (isVisibleSide) {
            val sx = side * 1.242f
            for (lz in floatArrayOf(-1.75f, -0.35f, 1.05f, 2.20f)) {
                val seam = rig.projectSegmentClipped(
                    busWorldSuspended(sx, 0.56f, lz),
                    busWorldSuspended(sx, 1.48f, lz)
                )
                if (seam != null) {
                    drawLine(Color(0x380F172A), seam.first, seam.second, strokeWidth = 1.1f)
                }
            }
        }
    }

    // Rear Tailgate Realism: Curved Panoramic Rear Window Glare, Defroster Lines, Cooling Louvers, Chrome Exhaust & 3D Rectangular Light Bars
    if (camLocalZ < -1.4f && abs(camLocalX) < 2.2f) {
        // Rear Windshield Sky Reflection Streak
        val rGlare0 = busWorldSuspended(-0.82f, 2.22f, -6.198f)
        val rGlare1 = busWorldSuspended(-0.32f, 2.22f, -6.205f)
        val rGlare2 = busWorldSuspended(0.16f, 3.06f, -6.206f)
        val rGlare3 = busWorldSuspended(-0.38f, 3.06f, -6.202f)
        if (rig.buildClippedQuadPath(path, rGlare0, rGlare1, rGlare2, rGlare3)) {
            drawPath(path, Color(0x24BAE6FD))
        }

        // Subtle copper defroster heating lines across the curved rear window
        for (step in 1..4) {
            val dy = 2.25f + step * 0.17f
            val seg = rig.projectSegmentClipped(
                busWorldSuspended(-0.92f, dy, -6.196f),
                busWorldSuspended(0.92f, dy, -6.196f)
            )
            if (seg != null) {
                drawLine(Color(0x32FF9966), seg.first, seg.second, strokeWidth = 1.1f)
            }
        }

        // Rear engine-bay subtle horizontal louvers
        for (ey in floatArrayOf(1.02f, 1.10f, 1.18f)) {
            val eSeg = rig.projectSegmentClipped(
                busWorldSuspended(-0.68f, ey, -6.198f),
                busWorldSuspended(0.68f, ey, -6.198f)
            )
            if (eSeg != null) {
                drawLine(Color(0x550B0E14), eSeg.first, eSeg.second, strokeWidth = 1.4f)
            }
        }

        // Rear Bumper Retro-Reflective Safety Strips
        for (side in intArrayOf(-1, 1)) {
            val r0 = busWorldSuspended(side * 0.74f, 0.56f, -6.205f)
            val r1 = busWorldSuspended(side * 1.06f, 0.56f, -6.205f)
            val r2 = busWorldSuspended(side * 1.06f, 0.62f, -6.205f)
            val r3 = busWorldSuspended(side * 0.74f, 0.62f, -6.205f)
            if (rig.buildClippedQuadPath(path, r0, r1, r2, r3)) {
                drawPath(path, Color(0xFFB91C1C))
            }
        }

        // Chrome Twin Exhaust Tailpipe under left-rear bumper + diesel plume puffs when accelerating
        val ex0 = busWorldSuspended(-0.88f, 0.36f, -6.24f)
        val ex1 = busWorldSuspended(-0.74f, 0.36f, -6.24f)
        val ex2 = busWorldSuspended(-0.74f, 0.45f, -6.16f)
        val ex3 = busWorldSuspended(-0.88f, 0.45f, -6.16f)
        if (rig.buildClippedQuadPath(path, ex0, ex1, ex2, ex3)) {
            drawPath(path, Color(0xFF94A3B8))
            drawPath(path, Color(0xFF334155), style = Stroke(width = 1.2f))
        }
        if (state.throttleInput > 0.25f && !state.isParkBrakeActive) {
            val puffPhase = (state.wheelSpinRad * 0.6f) % 1f
            for (p in 0..2) {
                val frac = (puffPhase + p * 0.33f) % 1f
                val puffPos = busWorld(-0.81f - frac * 0.14f, 0.38f + frac * 0.28f, -6.30f - frac * 1.65f)
                val pScreen = rig.projectPoint(puffPos)
                if (pScreen != null) {
                    val puffAlpha = ((1f - frac) * 0.16f * state.throttleInput).coerceIn(0f, 0.18f)
                    drawCircle(
                        color = Color(0xFF94A3B8).copy(alpha = puffAlpha),
                        radius = (8f + frac * 18f),
                        center = pScreen
                    )
                }
            }
        }

        // Smoked rear tail-lamp housing bezels & 3D Vertical LED Tail/Brake Light Bars (Zero 2D Dots)
        for (tx in floatArrayOf(-1.04f, 1.04f)) {
            val tb0 = busWorldSuspended(tx - 0.11f, 0.96f, -6.208f)
            val tb1 = busWorldSuspended(tx + 0.11f, 0.96f, -6.208f)
            val tb2 = busWorldSuspended(tx + 0.09f, 1.52f, -6.208f)
            val tb3 = busWorldSuspended(tx - 0.09f, 1.52f, -6.208f)
            if (rig.buildClippedQuadPath(path, tb0, tb1, tb2, tb3)) {
                if (brakeActive || state.headlightsOn) {
                    drawPath(path, if (brakeActive) Color(0x48FF2424) else Color(0x28DC2626))
                }
                drawPath(path, Color(0x660F172A), style = Stroke(width = 1.6f))
            }
        }

        // High-Mount Center LED Third Brake Strip
        val hmsl0 = busWorldSuspended(-0.34f, 3.22f, -6.192f)
        val hmsl1 = busWorldSuspended(0.34f, 3.22f, -6.192f)
        val hmsl2 = busWorldSuspended(0.34f, 3.265f, -6.192f)
        val hmsl3 = busWorldSuspended(-0.34f, 3.265f, -6.192f)
        if (rig.buildClippedQuadPath(path, hmsl0, hmsl1, hmsl2, hmsl3)) {
            drawPath(path, if (brakeActive) Color(0xFFFF2424) else Color(0xFF5A1418))
            drawPath(path, Color(0x660F172A), style = Stroke(width = 1.1f))
        }
    }

    // 5. Realistic Dual Tandem Pantograph Coach Windshield Wipers matching Cockpit View orientation
    if (camLocalZ > 1.2f) {
        fun glassZ(x: Float, y: Float): Float {
            val dyG = (y - 1.48f).coerceAtLeast(0f)
            return 5.752f - dyG * 0.252f - (x * x) * 0.105f
        }
        val sweepNorm = if (state.wipersOn || state.wiperPhaseRad > 0.04f) {
            (0.5f - 0.5f * cos(state.wiperPhaseRad)).coerceIn(0f, 1f)
        } else {
            0f
        }
        // Tandem coach pantograph wipers matching inside cockpit view:
        // Left-center mount (-0.64m) & right-center mount (+0.18m), parked pointing toward +X (driver side),
        // sweeping synchronously UP (+Y) and toward -X (passenger side) across the curved front windshield.
        val armElevRad = 0.09f + sweepNorm * 1.33f
        val bladeTiltRad = 0.05f + sweepNorm * 1.45f
        val armCos = cos(armElevRad)
        val armSin = sin(armElevRad)
        val bladeCos = cos(bladeTiltRad)
        val bladeSin = sin(bladeTiltRad)

        for (pivX in floatArrayOf(-0.64f, 0.18f)) {
            val pivY = 1.48f
            val auxPivX = pivX - 0.06f
            val auxPivY = 1.505f
            val armLen = 0.56f

            val headX = pivX + armCos * armLen
            val headY = pivY + armSin * armLen
            val auxHeadX = auxPivX + armCos * (armLen * 0.94f)
            val auxHeadY = auxPivY + armSin * (armLen * 0.94f)

            val pPiv = busWorldSuspended(pivX, pivY, glassZ(pivX, pivY))
            val pAuxPiv = busWorldSuspended(auxPivX, auxPivY, glassZ(auxPivX, auxPivY))
            val pHead = busWorldSuspended(headX, headY, glassZ(headX, headY))
            val pAuxHead = busWorldSuspended(auxHeadX, auxHeadY, glassZ(auxHeadX, auxHeadY))

            // Primary drive arm & secondary parallel pantograph guide rod
            rig.projectSegmentClipped(pPiv, pHead)?.let { seg ->
                drawLine(Color(0xFF1E2530), seg.first, seg.second, strokeWidth = 2.8f, cap = StrokeCap.Round)
            }
            rig.projectSegmentClipped(pAuxPiv, pAuxHead)?.let { seg ->
                drawLine(Color(0xFF334155), seg.first, seg.second, strokeWidth = 1.7f, cap = StrokeCap.Round)
            }
            // Cowl spindle housing cap
            rig.projectPoint(pPiv)?.let { sp ->
                drawCircle(Color(0xFF0F172A), radius = 3.2f, center = sp)
            }

            // Pantograph-stabilized long squeegee blade (parked horizontal along cowl, upright at peak)
            val halfBlade = 0.36f
            val b0x = headX - bladeCos * halfBlade
            val b0y = headY - bladeSin * halfBlade
            val b1x = headX + bladeCos * halfBlade
            val b1y = headY + bladeSin * halfBlade
            val pB0 = busWorldSuspended(b0x, b0y, glassZ(b0x, b0y) + 0.006f)
            val pB1 = busWorldSuspended(b1x, b1y, glassZ(b1x, b1y) + 0.006f)

            rig.projectSegmentClipped(pB0, pB1)?.let { bSeg ->
                drawLine(Color(0xFF090D14), bSeg.first, bSeg.second, strokeWidth = 3.2f, cap = StrokeCap.Round)
                drawLine(Color(0x6694A3B8), bSeg.first, bSeg.second, strokeWidth = 1.1f, cap = StrokeCap.Round)
            }
            // Spring-loaded wiper knuckle joint
            rig.projectPoint(pHead)?.let { kn ->
                drawCircle(Color(0xFF1F2937), radius = 2.5f, center = kn)
            }
        }
    }
}

/**
 * Renders a high-realism 3D Driver Cockpit Interior when [CameraViewMode.Cockpit] is active:
 * - Clean panoramic front windshield + Right-hand driver side window separated by a sculpted 3D Right A-Pillar
 * - Left & Right 3D two-tone exterior mirrors (amber-gold & olive-green outer shell, tubular brackets, live road & bus flank reflection)
 * - Sculpted two-level soft-touch charcoal dashboard deck with silver S-curve lower trim molding & storage compartment
 * - Raised driver binnacle hood with rich Mahogany/Teak burl-woodgrain fascia panel
 * - Left woodgrain zone: 2x4 rocker switch bank, 3D pneumatic parking brake hand-valve lever, dual auxiliary mini-gauges, and recessed 3D GPS navigation display
 * - Deep-recessed center instrument cluster: Analog Speedometer (0..180 km/h + fuel sub-dial), Analog Tachometer (0..30 x100 rpm with green economy & redline bands + temp sub-dial), and live telltale warning icon strip
 * - Right woodgrain zone: Upper & lower illuminated green LED rocker switch banks
 * - Full-size 3D leather driver steering wheel with twin brushed-silver spoke trims, thumb button pods, and left/right column stalks
 */
private fun DrawScope.drawHinoRm2Cockpit3D(
    state: SimUiState,
    titlePaint: Paint,
    subPaint: Paint
) {
    val w = size.width
    val h = size.height
    val nc = drawContext.canvas.nativeCanvas

    val brakeActive = state.brakeInput > 0.05f || state.isParkBrakeActive
    val leftBlinkOn = state.blinkerPhase && (state.turnLeftOn || state.hazardsOn)
    val rightBlinkOn = state.blinkerPhase && (state.turnRightOn || state.hazardsOn)

    // 1. Subtle Top Windshield Solar Sun-Band (Clean Open Panoramic Glass — No Red Curtains)
    drawRect(
        brush = Brush.verticalGradient(
            0.0f to Color(0x66060B14),
            0.65f to Color(0x1E0B1320),
            1.0f to Color.Transparent,
            startY = 0f,
            endY = h * 0.10f
        ),
        topLeft = Offset.Zero,
        size = Size(w * 0.76f, h * 0.10f)
    )

    // 2. Realistic Pantograph Windshield Wipers (Parked Low on Cowl When Off, Sweeping Smoothly When On)
    val dashDeckTopY = h * 0.675f
    val binnacleTopY = h * 0.590f
    val wiperSweepNorm = if (state.wipersOn || state.wiperPhaseRad > 0.04f) {
        (0.5f - 0.5f * cos(state.wiperPhaseRad)).coerceIn(0f, 1f)
    } else {
        0f
    }

    data class CockpitWiperSpec(val pivotXFrac: Float, val armLenFrac: Float, val bladeLenFrac: Float)
    val cockpitWipers = arrayOf(
        CockpitWiperSpec(pivotXFrac = 0.19f, armLenFrac = 0.28f, bladeLenFrac = 0.36f),
        CockpitWiperSpec(pivotXFrac = 0.46f, armLenFrac = 0.30f, bladeLenFrac = 0.38f)
    )

    for (wsSpec in cockpitWipers) {
        val baseX = w * wsSpec.pivotXFrac
        val baseY = dashDeckTopY + 4.dp.toPx()
        val mainBase = Offset(baseX, baseY)
        val auxBase = Offset(baseX - 9.dp.toPx(), baseY + 3.dp.toPx())

        // Parked low at 0.04 rad along the cowl lip so it barely peeks above the dash when off
        val armElevRad = 0.04f + wiperSweepNorm * 1.36f
        val armLen = h * wsSpec.armLenFrac
        val headX = mainBase.x + cos(armElevRad) * armLen
        val headY = mainBase.y - sin(armElevRad) * armLen
        val headPos = Offset(headX, headY)
        val auxHeadPos = Offset(
            auxBase.x + cos(armElevRad) * (armLen * 0.94f),
            auxBase.y - sin(armElevRad) * (armLen * 0.94f)
        )

        val bladeTiltRad = 0.025f + wiperSweepNorm * 1.46f
        val bladeLen = h * wsSpec.bladeLenFrac
        val bxDir = cos(bladeTiltRad)
        val byDir = -sin(bladeTiltRad)
        val nxDir = -byDir
        val nyDir = bxDir

        val bStart = Offset(headX - bxDir * (bladeLen * 0.46f), headY - byDir * (bladeLen * 0.46f))
        val bEnd = Offset(headX + bxDir * (bladeLen * 0.54f), headY + byDir * (bladeLen * 0.54f))
        val bMid = Offset(
            (bStart.x + bEnd.x) * 0.5f + nxDir * 3.5.dp.toPx(),
            (bStart.y + bEnd.y) * 0.5f + nyDir * 3.5.dp.toPx()
        )

        val rubberPath = Path().apply {
            moveTo(bStart.x, bStart.y)
            quadraticTo(bMid.x, bMid.y, bEnd.x, bEnd.y)
        }
        drawPath(rubberPath, Color(0xEB070B12), style = Stroke(width = 4.6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(rubberPath, Color(0xFF283242), style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

        for (cf in floatArrayOf(0.18f, 0.40f, 0.62f, 0.84f)) {
            val u = 1f - cf
            val cx = u * u * bStart.x + 2f * u * cf * bMid.x + cf * cf * bEnd.x
            val cy = u * u * bStart.y + 2f * u * cf * bMid.y + cf * cf * bEnd.y
            drawCircle(Color(0xFF475569), radius = 2.0.dp.toPx(), center = Offset(cx, cy))
        }

        drawLine(Color(0xFF334155), auxBase, auxHeadPos, strokeWidth = 2.0.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xFF141B26), mainBase, headPos, strokeWidth = 4.2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color(0xFF1E293B), radius = 4.2.dp.toPx(), center = headPos)
    }

    // 3. Structural Cabin Pillars & Right-Side Driver Window Frame
    // 3a. Left A-Pillar
    val leftPillarPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.042f, 0f)
        lineTo(w * 0.072f, h * 0.72f)
        lineTo(0f, h * 0.72f)
        close()
    }
    drawPath(
        leftPillarPath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF141821), Color(0xFF232936), Color(0xFF10141B)),
            startX = 0f,
            endX = w * 0.072f
        )
    )
    drawLine(
        color = Color(0xFF333B4A),
        start = Offset(w * 0.040f, 0f),
        end = Offset(w * 0.070f, h * 0.70f),
        strokeWidth = 1.8.dp.toPx()
    )

    // 3b. Right Driver Window Lower Door Panel & Sill (x = 0.72w .. 1.0w, y = 0.835h .. 1.0h)
    val rightDoorTopY = h * 0.835f
    val rightDoorPath = Path().apply {
        moveTo(w * 0.715f, rightDoorTopY + h * 0.02f)
        lineTo(w, rightDoorTopY - h * 0.01f)
        lineTo(w, h)
        lineTo(w * 0.715f, h)
        close()
    }
    drawPath(
        rightDoorPath,
        brush = Brush.verticalGradient(
            listOf(Color(0xFF282E3A), Color(0xFF161A22), Color(0xFF0E1117)),
            startY = rightDoorTopY,
            endY = h
        )
    )
    drawLine(
        color = Color(0xFF3F4756),
        start = Offset(w * 0.72f, rightDoorTopY + h * 0.018f),
        end = Offset(w, rightDoorTopY - h * 0.01f),
        strokeWidth = 2.5.dp.toPx()
    )

    // 3c. Far-Right Side Window B-Pillar Frame
    val farRightFrame = Path().apply {
        moveTo(w * 0.985f, 0f)
        lineTo(w, 0f)
        lineTo(w, h)
        lineTo(w * 0.945f, h)
        close()
    }
    drawPath(farRightFrame, Color(0xFF141820))

    // 3d. Thick Sculpted 3D Right A-Pillar separating Front Windshield & Right Side Window
    val rightAPillarOuter = Path().apply {
        moveTo(w * 0.730f, 0f)
        lineTo(w * 0.818f, 0f)
        lineTo(w * 0.742f, h)
        lineTo(w * 0.678f, h)
        lineTo(w * 0.674f, h * 0.70f)
        close()
    }
    drawPath(
        rightAPillarOuter,
        brush = Brush.horizontalGradient(
            0.0f to Color(0xFF0E1219),
            0.18f to Color(0xFF232A38),
            0.52f to Color(0xFF2D3546),
            0.85f to Color(0xFF1A202C),
            1.0f to Color(0xFF0B0E14),
            startX = w * 0.674f,
            endX = w * 0.818f
        )
    )
    drawLine(
        color = Color(0x4464748B),
        start = Offset(w * 0.752f, 0f),
        end = Offset(w * 0.696f, h * 0.92f),
        strokeWidth = 2.2.dp.toPx()
    )
    drawLine(
        color = Color(0xFF090C11),
        start = Offset(w * 0.818f, 0f),
        end = Offset(w * 0.742f, h),
        strokeWidth = 2.5.dp.toPx()
    )

    // 4. Subtle Binnacle Reflection on Lower Windshield Glass
    val glassReflPath = Path().apply {
        moveTo(w * 0.415f, binnacleTopY + h * 0.01f)
        quadraticTo(w * 0.525f, binnacleTopY - h * 0.045f, w * 0.645f, binnacleTopY + h * 0.025f)
        lineTo(w * 0.415f, binnacleTopY + h * 0.01f)
        close()
    }
    drawPath(glassReflPath, Color(0x3411161F))

    // 5. Left Passenger/Center Dashboard Deck with S-Curve Silver Molding & Lower Panels (x = 0 .. 0.270w)
    val leftDashPath = Path().apply {
        moveTo(0f, h)
        lineTo(0f, dashDeckTopY + h * 0.025f)
        lineTo(w * 0.068f, dashDeckTopY - h * 0.008f)
        quadraticTo(w * 0.165f, dashDeckTopY - h * 0.012f, w * 0.268f, dashDeckTopY + h * 0.010f)
        lineTo(w * 0.268f, h)
        close()
    }
    drawPath(
        leftDashPath,
        brush = Brush.verticalGradient(
            0.0f to Color(0xFF333944),
            0.25f to Color(0xFF282D36),
            0.65f to Color(0xFF1D2129),
            1.0f to Color(0xFF12151B),
            startY = dashDeckTopY - h * 0.015f,
            endY = h
        )
    )
    // Recessed top tray contour on left dash
    val trayPath = Path().apply {
        moveTo(w * 0.135f, dashDeckTopY + h * 0.008f)
        lineTo(w * 0.236f, dashDeckTopY + h * 0.014f)
        lineTo(w * 0.228f, dashDeckTopY + h * 0.056f)
        lineTo(w * 0.128f, dashDeckTopY + h * 0.050f)
        close()
    }
    drawPath(trayPath, Color(0xFF21262E))
    drawPath(trayPath, Color(0xFF3E4552), style = Stroke(width = 1.2.dp.toPx()))
    for (vx in floatArrayOf(0.205f, 0.217f)) {
        drawRoundRect(
            color = Color(0xFF14181F),
            topLeft = Offset(w * vx, dashDeckTopY + h * 0.022f),
            size = Size(w * 0.007f, h * 0.018f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }

    // Sweeping Silver-Grey S-Curve Lower Dash Trim Strip
    val silverCurveOuter = Path().apply {
        moveTo(0f, h * 0.805f)
        lineTo(w * 0.175f, h * 0.812f)
        cubicTo(w * 0.208f, h * 0.815f, w * 0.222f, h * 0.905f, w * 0.246f, h)
        lineTo(w * 0.230f, h)
        cubicTo(w * 0.208f, h * 0.912f, w * 0.196f, h * 0.832f, w * 0.168f, h * 0.830f)
        lineTo(0f, h * 0.822f)
        close()
    }
    drawPath(
        silverCurveOuter,
        brush = Brush.verticalGradient(
            listOf(Color(0xFF9CA3AF), Color(0xFF6B7280), Color(0xFF4B5563)),
            startY = h * 0.80f,
            endY = h
        )
    )
    // Lower-left metallic grab bar frame at bottom-left corner
    val grabBarPath = Path().apply {
        moveTo(w * 0.008f, h)
        lineTo(w * 0.016f, h * 0.922f)
        lineTo(w * 0.095f, h * 0.935f)
    }
    drawPath(grabBarPath, Color(0xFF64748B), style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(grabBarPath, Color(0xFF94A3B8), style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    // 6. Wide Sculpted 3D Driver Instrument Binnacle Hood & Lower Cabinet (x = 0.246w .. 0.684w)
    val binnacleOuterPath = Path().apply {
        moveTo(w * 0.246f, h)
        lineTo(w * 0.264f, h * 0.840f)
        lineTo(w * 0.290f, binnacleTopY + h * 0.028f)
        quadraticTo(w * 0.306f, binnacleTopY, w * 0.352f, binnacleTopY - h * 0.004f)
        lineTo(w * 0.618f, binnacleTopY + h * 0.014f)
        quadraticTo(w * 0.652f, binnacleTopY + h * 0.022f, w * 0.666f, binnacleTopY + h * 0.056f)
        lineTo(w * 0.684f, h * 0.905f)
        lineTo(w * 0.682f, h)
        close()
    }
    drawPath(
        binnacleOuterPath,
        brush = Brush.verticalGradient(
            0.0f to Color(0xFF353B46),
            0.16f to Color(0xFF262B34),
            0.65f to Color(0xFF1B1F27),
            1.0f to Color(0xFF12151B),
            startY = binnacleTopY,
            endY = h
        )
    )
    // Sculpted top brow lip highlight & inner bevel on the binnacle hood
    val binnacleRimHighlight = Path().apply {
        moveTo(w * 0.264f, h * 0.840f)
        lineTo(w * 0.290f, binnacleTopY + h * 0.028f)
        quadraticTo(w * 0.306f, binnacleTopY, w * 0.352f, binnacleTopY - h * 0.004f)
        lineTo(w * 0.618f, binnacleTopY + h * 0.014f)
        quadraticTo(w * 0.652f, binnacleTopY + h * 0.022f, w * 0.666f, binnacleTopY + h * 0.056f)
        lineTo(w * 0.684f, h * 0.905f)
    }
    drawPath(binnacleRimHighlight, Color(0xFF4B5563), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(binnacleRimHighlight, Color(0xFF11141B), style = Stroke(width = 0.9.dp.toPx()))

    // Lower-left storage cabinet seam & recessed black pull-latch handle
    drawRoundRect(
        color = Color(0xFF141820),
        topLeft = Offset(w * 0.270f, h * 0.902f),
        size = Size(w * 0.095f, h * 0.092f),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(width = 1.4.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF0D1016),
        topLeft = Offset(w * 0.306f, h * 0.928f),
        size = Size(w * 0.024f, h * 0.024f),
        cornerRadius = CornerRadius(2.dp.toPx())
    )

    // 7. Wide Mahogany / Burl-Woodgrain Fascia Panel inside the Driver Binnacle (x = 0.268w .. 0.668w)
    val woodTopY = binnacleTopY + h * 0.018f
    val woodBotY = h * 0.878f
    val woodPanelPath = Path().apply {
        moveTo(w * 0.268f, woodBotY - h * 0.024f)
        lineTo(w * 0.296f, woodTopY + h * 0.016f)
        quadraticTo(w * 0.312f, woodTopY, w * 0.350f, woodTopY)
        lineTo(w * 0.614f, woodTopY + h * 0.014f)
        quadraticTo(w * 0.642f, woodTopY + h * 0.020f, w * 0.654f, woodTopY + h * 0.048f)
        lineTo(w * 0.668f, woodBotY + h * 0.004f)
        lineTo(w * 0.268f, woodBotY - h * 0.024f)
        close()
    }
    drawPath(
        woodPanelPath,
        brush = Brush.linearGradient(
            0.0f to Color(0xFF6B371B),
            0.25f to Color(0xFF8A4924),
            0.50f to Color(0xFF5D2E15),
            0.78f to Color(0xFF844522),
            1.0f to Color(0xFF542912),
            start = Offset(w * 0.268f, woodTopY),
            end = Offset(w * 0.668f, woodBotY)
        )
    )
    for (gStep in 1..6) {
        val gy = woodTopY + (woodBotY - woodTopY) * (gStep / 7f)
        drawLine(
            color = if (gStep % 2 == 0) Color(0x283A1808) else Color(0x1FF59E0B),
            start = Offset(w * 0.292f, gy),
            end = Offset(w * 0.654f, gy + h * 0.010f),
            strokeWidth = 1.6.dp.toPx()
        )
    }
    drawPath(woodPanelPath, Color(0xFF14171F), style = Stroke(width = 3.2.dp.toPx(), join = StrokeJoin.Round))
    drawPath(woodPanelPath, Color(0x44D97706), style = Stroke(width = 1.0.dp.toPx(), join = StrokeJoin.Round))

    // 8. Left Woodgrain Zone: 2x4 Rocker Switch Bank, Pneumatic Park-Brake Lever, Aux Gauge & Large Clean GPS Screen
    // 8a. Top-Left 2x4 Rocker Switch Bank (x = 0.292w .. 0.346w, y = 0.630h .. 0.694h)
    val swBankLeftX = w * 0.292f
    val swBankTopY = h * 0.630f
    val swBankW = w * 0.054f
    val swBankH = h * 0.064f
    drawRoundRect(
        color = Color(0xFF1B1F27),
        topLeft = Offset(swBankLeftX, swBankTopY),
        size = Size(swBankW, swBankH),
        cornerRadius = CornerRadius(3.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF0E1117),
        topLeft = Offset(swBankLeftX, swBankTopY),
        size = Size(swBankW, swBankH),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(width = 1.2.dp.toPx())
    )
    val cellW = swBankW / 4f
    val cellH = swBankH / 2f
    for (row in 0..1) {
        for (col in 0..3) {
            val sx = swBankLeftX + col * cellW + 1.5.dp.toPx()
            val sy = swBankTopY + row * cellH + 1.5.dp.toPx()
            val cw = cellW - 3.dp.toPx()
            val ch = cellH - 3.dp.toPx()
            drawRoundRect(
                color = Color(0xFF282E38),
                topLeft = Offset(sx, sy),
                size = Size(cw, ch),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
            val ledCol = when {
                row == 0 && col == 1 && state.engineRunning -> Color(0xFF22C55E)
                row == 0 && col == 3 -> Color(0xFFF59E0B)
                row == 1 && col == 0 && state.headlightMode == HeadlightBeamMode.High -> Color(0xFF38BDF8)
                row == 1 && col == 0 && state.headlightMode == HeadlightBeamMode.Low -> Color(0xFF22C55E)
                else -> Color(0xFF475569)
            }
            drawLine(
                color = ledCol,
                start = Offset(sx + cw * 0.22f, sy + ch * 0.28f),
                end = Offset(sx + cw * 0.78f, sy + ch * 0.28f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }

    // 8b. Pneumatic Hand-Brake / Retarder Valve Lever (x = 0.352w .. 0.372w, y = 0.628h .. 0.696h)
    val valveBaseX = w * 0.352f
    val valveBaseY = h * 0.628f
    val valveBaseW = w * 0.020f
    val valveBaseH = h * 0.068f
    drawRoundRect(
        color = Color(0xFF1A1E26),
        topLeft = Offset(valveBaseX, valveBaseY),
        size = Size(valveBaseW, valveBaseH),
        cornerRadius = CornerRadius(3.dp.toPx())
    )
    drawLine(
        color = Color(0xFF090C10),
        start = Offset(valveBaseX + valveBaseW * 0.5f, valveBaseY + 4.dp.toPx()),
        end = Offset(valveBaseX + valveBaseW * 0.5f, valveBaseY + valveBaseH - 4.dp.toPx()),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )
    val knobY = if (state.isParkBrakeActive) valveBaseY + valveBaseH * 0.62f else valveBaseY + valveBaseH * 0.14f
    drawLine(
        color = Color(0xFF262C36),
        start = Offset(valveBaseX + valveBaseW * 0.5f, valveBaseY + valveBaseH * 0.55f),
        end = Offset(valveBaseX + valveBaseW * 0.5f, knobY - 6.dp.toPx()),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF475569), Color(0xFF1E242E))),
        topLeft = Offset(valveBaseX - 1.dp.toPx(), knobY - 10.dp.toPx()),
        size = Size(valveBaseW + 2.dp.toPx(), 14.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )

    // 8c. Air Pressure Mini-Gauge (cx = 0.390w, cy = 0.662h)
    val miniGaugeX = w * 0.390f
    val miniGaugeY = h * 0.662f
    val miniRadius = (h * 0.026f).coerceIn(8.dp.toPx(), 15.dp.toPx())
    drawCircle(Color(0xFF11151C), radius = miniRadius + 2.dp.toPx(), center = Offset(miniGaugeX, miniGaugeY))
    drawCircle(Color(0xFF64748B), radius = miniRadius + 1.dp.toPx(), center = Offset(miniGaugeX, miniGaugeY), style = Stroke(width = 1.2.dp.toPx()))
    drawCircle(Color(0xFF1B202A), radius = miniRadius, center = Offset(miniGaugeX, miniGaugeY))
    for (tk in 0..4) {
        val ta = Math.toRadians(-120.0 + tk * 60.0).toFloat()
        drawLine(
            color = Color(0xCCFFFFFF),
            start = Offset(miniGaugeX + sin(ta) * (miniRadius * 0.65f), miniGaugeY - cos(ta) * (miniRadius * 0.65f)),
            end = Offset(miniGaugeX + sin(ta) * (miniRadius * 0.88f), miniGaugeY - cos(ta) * (miniRadius * 0.88f)),
            strokeWidth = 1.dp.toPx()
        )
    }
    val airAngle = Math.toRadians(-120.0 + 0.76 * 240.0).toFloat()
    drawLine(
        color = Color(0xFFEF4444),
        start = Offset(miniGaugeX, miniGaugeY),
        end = Offset(miniGaugeX + sin(airAngle) * (miniRadius * 0.78f), miniGaugeY - cos(airAngle) * (miniRadius * 0.78f)),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawCircle(Color(0xFFCBD5E1), radius = 1.8.dp.toPx(), center = Offset(miniGaugeX, miniGaugeY))

    // 8d. Large, Clean Recessed 3D Live GPS Navigation Display Screen (x = 0.284w .. 0.402w, y = 0.708h .. 0.854h)
    val gpsX = w * 0.284f
    val gpsY = h * 0.708f
    val gpsW = w * 0.118f
    val gpsH = h * 0.146f
    val gpsOuterPath = Path().apply {
        moveTo(gpsX + 4.dp.toPx(), gpsY)
        lineTo(gpsX + gpsW, gpsY + 2.dp.toPx())
        lineTo(gpsX + gpsW - 3.dp.toPx(), gpsY + gpsH)
        lineTo(gpsX, gpsY + gpsH - 2.dp.toPx())
        close()
    }
    drawPath(gpsOuterPath, Color(0xFF141820))
    drawPath(gpsOuterPath, Color(0xFF333B48), style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

    val padGps = 3.5.dp.toPx()
    val scrL = gpsX + padGps
    val scrT = gpsY + padGps
    val scrR = gpsX + gpsW - padGps
    val scrB = gpsY + gpsH - padGps
    val scrW = scrR - scrL
    val scrH = scrB - scrT

    val gpsScreenRect = Path().apply {
        moveTo(scrL + 2.dp.toPx(), scrT)
        lineTo(scrR, scrT + 1.5.dp.toPx())
        lineTo(scrR - 2.dp.toPx(), scrB)
        lineTo(scrL, scrB - 1.5.dp.toPx())
        close()
    }
    drawPath(gpsScreenRect, Color(0xFF070C14))

    // Full-Bleed Live Real-Time OSM Route Projection inside Cockpit Dashboard GPS Screen (No cramped header box!)
    clipPath(gpsScreenRect) {
        val mapCx = scrL + scrW * 0.50f
        val mapCy = scrT + scrH * 0.68f
        val mapScale = 0.62f * density * state.minimapZoom
        val bx = state.busPos.x
        val bz = state.busPos.z
        val cosH = cos(-state.busHeadingRad)
        val sinH = sin(-state.busHeadingRad)

        val otherRoadsPath = Path()
        val activeRoadPath = Path()

        for (rIdx in state.roads.indices) {
            val road = state.roads[rIdx]
            val pts = road.points
            if (pts.size < 2) continue
            val targetPath = if (rIdx == state.activeRoadIndex) activeRoadPath else otherRoadsPath
            val step = if (pts.size > 40) 3 else 1

            fun projectMapPt(wx: Float, wz: Float): Offset {
                val dx = wx - bx
                val dz = wz - bz
                val rx = dx * cosH - dz * sinH
                val rz = dx * sinH + dz * cosH
                return Offset(mapCx + rx * mapScale, mapCy - rz * mapScale)
            }

            var prev = projectMapPt(pts[0].x, pts[0].z)
            var i = step
            while (i < pts.size) {
                val cur = projectMapPt(pts[i].x, pts[i].z)
                if ((prev.x in (scrL - 40f)..(scrR + 40f) && prev.y in (scrT - 40f)..(scrB + 40f)) ||
                    (cur.x in (scrL - 40f)..(scrR + 40f) && cur.y in (scrT - 40f)..(scrB + 40f))
                ) {
                    targetPath.moveTo(prev.x, prev.y)
                    targetPath.lineTo(cur.x, cur.y)
                }
                prev = cur
                i = if (i != pts.lastIndex && i + step >= pts.size) pts.lastIndex else i + step
            }
        }

        drawPath(
            path = otherRoadsPath,
            color = Color(0xFF2B5B94),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = activeRoadPath,
            color = Color.White,
            style = Stroke(width = 7.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = activeRoadPath,
            color = Color(0xFF2563EB),
            style = Stroke(width = 4.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Red-Ringed 3D Navigation Arrowhead at Bus Position
        val navR = (scrH * 0.145f).coerceAtLeast(5.5.dp.toPx())
        drawCircle(Color(0xDD090D16), radius = navR, center = Offset(mapCx, mapCy))
        drawCircle(Color(0xFFEF4444), radius = navR, center = Offset(mapCx, mapCy), style = Stroke(width = 1.5.dp.toPx()))
        val navArrow = Path().apply {
            moveTo(mapCx, mapCy - navR * 0.72f)
            lineTo(mapCx + navR * 0.56f, mapCy + navR * 0.52f)
            lineTo(mapCx, mapCy + navR * 0.20f)
            lineTo(mapCx - navR * 0.56f, mapCy + navR * 0.52f)
            close()
        }
        drawPath(navArrow, Color(0xFFEF4444))

        // Subtle translucent GPS corner badge (keeps 95% of map canvas open!)
        subPaint.textSize = (scrH * 0.14f).coerceIn(7f, 11f)
        subPaint.color = android.graphics.Color.argb(200, 148, 163, 184)
        nc.drawText("GPS", scrL + scrW * 0.15f, scrT + scrH * 0.17f, subPaint)
    }

    // 9. Wide Deep-Recessed Center Analog Instrument Cluster (Speedometer + Dedicated Analog Petrol/Fuel Gauge + Tachometer + OEM Readout + Telltales)
    val clusterLeft = w * 0.412f
    val clusterRight = w * 0.628f
    val clusterTop = h * 0.614f
    val clusterBot = h * 0.822f
    val clusterW = clusterRight - clusterLeft
    val clusterH = clusterBot - clusterTop

    val clusterArchPath = Path().apply {
        moveTo(clusterLeft, clusterBot - h * 0.016f)
        lineTo(clusterLeft + clusterW * 0.03f, clusterTop + h * 0.032f)
        quadraticTo(
            (clusterLeft + clusterRight) * 0.5f,
            clusterTop - h * 0.014f,
            clusterRight - clusterW * 0.03f,
            clusterTop + h * 0.036f
        )
        lineTo(clusterRight, clusterBot - h * 0.012f)
        quadraticTo(
            (clusterLeft + clusterRight) * 0.5f,
            clusterBot + h * 0.008f,
            clusterLeft,
            clusterBot - h * 0.016f
        )
        close()
    }
    drawPath(
        clusterArchPath,
        brush = Brush.verticalGradient(
            0.0f to Color(0xFF0D1016),
            0.22f to Color(0xFF1E232B),
            0.85f to Color(0xFF171B22),
            1.0f to Color(0xFF0E1117),
            startY = clusterTop,
            endY = clusterBot
        )
    )
    drawPath(clusterArchPath, Color(0xFF090B0F), style = Stroke(width = 3.5.dp.toPx()))
    drawPath(clusterArchPath, Color(0xFF384150), style = Stroke(width = 1.2.dp.toPx()))

    // Main Dual Dials (Speedometer on Left, Tachometer on Right) + Dedicated Center Petrol/Fuel Gauge
    val dialRadius = min(clusterW * 0.158f, clusterH * 0.375f).coerceIn(24.dp.toPx(), 44.dp.toPx())
    val speedoCenter = Offset(clusterLeft + clusterW * 0.205f, clusterTop + clusterH * 0.45f)
    val tachoCenter = Offset(clusterLeft + clusterW * 0.795f, clusterTop + clusterH * 0.45f)

    fun drawAnalogInstrumentDial(
        center: Offset,
        radius: Float,
        isTacho: Boolean,
        needleFrac: Float
    ) {
        drawCircle(Color(0xFF0B0E14), radius = radius + 3.dp.toPx(), center = center)
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color(0xFFD1D5DB), Color(0xFF4B5563), Color(0xFF9CA3AF)),
                start = Offset(center.x - radius, center.y - radius),
                end = Offset(center.x + radius, center.y + radius)
            ),
            radius = radius + 1.2.dp.toPx(),
            center = center,
            style = Stroke(width = 2.2.dp.toPx())
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF232832), Color(0xFF141820)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        if (isTacho) {
            val bandR = radius * 0.80f
            val bandTopLeft = Offset(center.x - bandR, center.y - bandR)
            val bandSize = Size(bandR * 2f, bandR * 2f)
            val startCanvasDeg = 135f
            drawArc(
                color = Color(0xFF16A34A),
                startAngle = startCanvasDeg + 80f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = bandTopLeft,
                size = bandSize,
                style = Stroke(width = radius * 0.11f, cap = StrokeCap.Butt)
            )
            drawArc(
                color = Color(0xFFDC2626),
                startAngle = startCanvasDeg + 182f,
                sweepAngle = 58f,
                useCenter = false,
                topLeft = bandTopLeft,
                size = bandSize,
                style = Stroke(width = radius * 0.11f, cap = StrokeCap.Butt)
            )
        }

        val majorCount = if (isTacho) 6 else 9
        val minorPerMajor = 2
        val totalTicks = majorCount * minorPerMajor
        val labelSizePx = (radius * 0.22f).coerceIn(6f, 11.5f)
        subPaint.textSize = labelSizePx
        subPaint.color = android.graphics.Color.argb(235, 241, 245, 249)

        for (t in 0..totalTicks) {
            val frac = t.toFloat() / totalTicks.toFloat()
            val angRad = Math.toRadians(-135.0 + frac * 240.0).toFloat()
            val isMajor = (t % minorPerMajor == 0)
            val rIn = if (isMajor) radius * 0.72f else radius * 0.80f
            val rOut = radius * 0.90f
            drawLine(
                color = if (isTacho && frac >= 0.76f) Color(0xFFEF4444) else Color(0xE6F8FAFC),
                start = Offset(center.x + sin(angRad) * rIn, center.y - cos(angRad) * rIn),
                end = Offset(center.x + sin(angRad) * rOut, center.y - cos(angRad) * rOut),
                strokeWidth = if (isMajor) 1.5.dp.toPx() else 0.8.dp.toPx()
            )
            if (isMajor) {
                val majorIdx = t / minorPerMajor
                val labelText = if (isTacho) "${majorIdx * 5}" else "${majorIdx * 20}"
                val rTxt = radius * 0.53f
                val tx = center.x + sin(angRad) * rTxt
                val ty = center.y - cos(angRad) * rTxt + labelSizePx * 0.36f
                nc.drawText(labelText, tx, ty, subPaint)
            }
        }

        // Dial caption inside bottom gap ("km/h" / "x100 RPM")
        subPaint.textSize = (radius * 0.18f).coerceIn(5.5f, 9.5f)
        subPaint.color = android.graphics.Color.argb(190, 148, 163, 184)
        nc.drawText(
            if (isTacho) "x100 RPM" else "km/h",
            center.x,
            center.y + radius * 0.56f,
            subPaint
        )

        // Glowing Red-Orange Main Needle + Brushed Silver Center Cap
        val nAngle = Math.toRadians(-135.0 + needleFrac.coerceIn(0f, 1f) * 240.0).toFloat()
        val nTip = Offset(center.x + sin(nAngle) * (radius * 0.82f), center.y - cos(nAngle) * (radius * 0.82f))
        val nTail = Offset(center.x - sin(nAngle) * (radius * 0.18f), center.y + cos(nAngle) * (radius * 0.18f))
        drawLine(Color(0x66EF4444), nTail, nTip, strokeWidth = 3.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xFFEF4444), nTail, nTip, strokeWidth = 2.0.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color(0xFF334155), radius = radius * 0.15f, center = center)
        drawCircle(Color(0xFFCBD5E1), radius = radius * 0.08f, center = center)
    }

    val speedFrac = (state.speedKmh / 180f).coerceIn(0f, 1f)
    val rpmFrac = (state.engineRpm / 3000f).coerceIn(0f, 1f)
    drawAnalogInstrumentDial(speedoCenter, dialRadius, isTacho = false, needleFrac = speedFrac)
    drawAnalogInstrumentDial(tachoCenter, dialRadius, isTacho = true, needleFrac = rpmFrac)

    // 9a. Dedicated Realistic Analog Petrol / Fuel Gauge Centered in Instrument Cluster (E — 1/2 — F)
    val fuelCenter = Offset(clusterLeft + clusterW * 0.50f, clusterTop + clusterH * 0.34f)
    val fuelRadius = (dialRadius * 0.64f).coerceIn(15.dp.toPx(), 28.dp.toPx())
    val fuelFrac = (state.fuelLiters / 100f).coerceIn(0.02f, 1f)
    val isLowFuel = state.fuelLiters < 20f

    // Brushed metallic housing & deep charcoal recessed dial
    drawCircle(Color(0xFF0A0D12), radius = fuelRadius + 2.6.dp.toPx(), center = fuelCenter)
    drawCircle(
        brush = Brush.linearGradient(
            listOf(Color(0xFFE2E8F0), Color(0xFF475569), Color(0xFF94A3B8)),
            start = Offset(fuelCenter.x - fuelRadius, fuelCenter.y - fuelRadius),
            end = Offset(fuelCenter.x + fuelRadius, fuelCenter.y + fuelRadius)
        ),
        radius = fuelRadius + 1.1.dp.toPx(),
        center = fuelCenter,
        style = Stroke(width = 1.9.dp.toPx())
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF232833), Color(0xFF12161E)),
            center = fuelCenter,
            radius = fuelRadius
        ),
        radius = fuelRadius,
        center = fuelCenter
    )

    // Red Low-Fuel Reserve Zone Arc (E to 1/4 on the left side: canvas 160° .. 215°)
    val fArcR = fuelRadius * 0.80f
    drawArc(
        color = Color(0xFFDC2626),
        startAngle = 160f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = Offset(fuelCenter.x - fArcR, fuelCenter.y - fArcR),
        size = Size(fArcR * 2f, fArcR * 2f),
        style = Stroke(width = fuelRadius * 0.13f, cap = StrokeCap.Butt)
    )
    // Subtle white main fuel scale arc (1/4 to F: 215° .. 380°)
    drawArc(
        color = Color(0x55E2E8F0),
        startAngle = 215f,
        sweepAngle = 165f,
        useCenter = false,
        topLeft = Offset(fuelCenter.x - fArcR, fuelCenter.y - fArcR),
        size = Size(fArcR * 2f, fArcR * 2f),
        style = Stroke(width = fuelRadius * 0.08f, cap = StrokeCap.Butt)
    )

    // Graduation Ticks from -110° (E) to +110° (F)
    for (ft in 0..8) {
        val tFrac = ft / 8f
        val fAng = Math.toRadians(-110.0 + tFrac * 220.0).toFloat()
        val isMajor = ft % 2 == 0
        val rIn = if (isMajor) fuelRadius * 0.67f else fuelRadius * 0.76f
        val rOut = fuelRadius * 0.89f
        drawLine(
            color = if (ft <= 2) Color(0xFFEF4444) else Color(0xF2F8FAFC),
            start = Offset(fuelCenter.x + sin(fAng) * rIn, fuelCenter.y - cos(fAng) * rIn),
            end = Offset(fuelCenter.x + sin(fAng) * rOut, fuelCenter.y - cos(fAng) * rOut),
            strokeWidth = if (isMajor) 1.5.dp.toPx() else 0.9.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Crisp E (Red), 1/2 (Silver), F (White) Fuel Scale Labels
    val fLabelSize = (fuelRadius * 0.27f).coerceIn(6.5f, 11f)
    subPaint.textSize = fLabelSize
    subPaint.color = android.graphics.Color.argb(255, 248, 113, 113)
    nc.drawText("E", fuelCenter.x - fuelRadius * 0.48f, fuelCenter.y + fuelRadius * 0.24f, subPaint)
    subPaint.color = android.graphics.Color.argb(240, 226, 232, 240)
    nc.drawText("1/2", fuelCenter.x, fuelCenter.y - fuelRadius * 0.36f, subPaint)
    subPaint.color = android.graphics.Color.argb(255, 248, 250, 252)
    nc.drawText("F", fuelCenter.x + fuelRadius * 0.48f, fuelCenter.y + fuelRadius * 0.24f, subPaint)

    // Realistic Miniature Fuel Pump Icon & Reserve Warning Dot at Bottom of Fuel Dial
    val fpCol = if (isLowFuel) Color(0xFFF59E0B) else Color(0xFFE2E8F0)
    val fpW = fuelRadius * 0.24f
    val fpH = fuelRadius * 0.30f
    val fpX = fuelCenter.x - fpW * 0.55f
    val fpY = fuelCenter.y + fuelRadius * 0.32f
    drawRoundRect(
        color = fpCol,
        topLeft = Offset(fpX, fpY),
        size = Size(fpW, fpH),
        cornerRadius = CornerRadius(1.5.dp.toPx())
    )
    drawRect(
        color = Color(0xFF141820),
        topLeft = Offset(fpX + fpW * 0.20f, fpY + fpH * 0.16f),
        size = Size(fpW * 0.60f, fpH * 0.30f)
    )
    drawLine(
        color = fpCol,
        start = Offset(fpX + fpW, fpY + fpH * 0.28f),
        end = Offset(fpX + fpW + fuelRadius * 0.11f, fpY + fpH * 0.72f),
        strokeWidth = 1.2.dp.toPx(),
        cap = StrokeCap.Round
    )
    if (isLowFuel) {
        drawCircle(Color(0xFFF59E0B), radius = 2.0.dp.toPx(), center = Offset(fuelCenter.x - fuelRadius * 0.32f, fpY + fpH * 0.5f))
    }

    // Fuel Gauge Glowing Orange-Red Physical Needle & Hub Cap
    val fNeedleAng = Math.toRadians(-110.0 + fuelFrac * 220.0).toFloat()
    val fTip = Offset(fuelCenter.x + sin(fNeedleAng) * (fuelRadius * 0.78f), fuelCenter.y - cos(fNeedleAng) * (fuelRadius * 0.78f))
    val fTail = Offset(fuelCenter.x - sin(fNeedleAng) * (fuelRadius * 0.18f), fuelCenter.y + cos(fNeedleAng) * (fuelRadius * 0.18f))
    drawLine(Color(0x66F97316), fTail, fTip, strokeWidth = 3.2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color(0xFFF97316), fTail, fTip, strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(Color(0xFF334155), radius = fuelRadius * 0.16f, center = fuelCenter)
    drawCircle(Color(0xFFCBD5E1), radius = fuelRadius * 0.08f, center = fuelCenter)

    // 9b. Clean Flush OEM Telemetry Readout (Speed • Gear • Fuel Liters) — No Cramped Box!
    val midCx = clusterLeft + clusterW * 0.50f
    val speedInt = state.speedKmh.roundToInt().coerceIn(0, 180)
    val gearBadge = when {
        state.isParkBrakeActive || state.gear == com.bharatbussim.game.model.TransmissionGear.Park -> "P"
        state.gear == com.bharatbussim.game.model.TransmissionGear.Reverse -> "R"
        state.gear == com.bharatbussim.game.model.TransmissionGear.Neutral -> "N"
        else -> "D${state.displayGearNumber.coerceIn(1, 6)}"
    }
    val readoutY = clusterTop + clusterH * 0.73f
    titlePaint.textSize = (clusterH * 0.135f).coerceIn(10f, 17f)
    titlePaint.color = android.graphics.Color.argb(245, 248, 250, 252)
    nc.drawText("$speedInt", midCx - clusterW * 0.055f, readoutY, titlePaint)

    subPaint.textSize = (clusterH * 0.092f).coerceIn(7f, 12f)
    subPaint.color = android.graphics.Color.argb(255, 245, 158, 11)
    nc.drawText(gearBadge, midCx + clusterW * 0.068f, readoutY - clusterH * 0.006f, subPaint)

    subPaint.textSize = (clusterH * 0.082f).coerceIn(6.5f, 11f)
    subPaint.color = if (isLowFuel) android.graphics.Color.argb(255, 248, 113, 113) else android.graphics.Color.argb(220, 148, 163, 184)
    nc.drawText("${state.fuelLiters.roundToInt()} L", midCx, readoutY + clusterH * 0.105f, subPaint)

    // Lower Telltale Warning Icon Bar inside Instrument Cluster
    val telltaleY = clusterBot - clusterH * 0.08f
    val ttLeft = clusterLeft + clusterW * 0.12f
    val ttRight = clusterRight - clusterW * 0.12f
    drawRoundRect(
        color = Color(0xFF0E1117),
        topLeft = Offset(ttLeft, telltaleY - 4.5.dp.toPx()),
        size = Size(ttRight - ttLeft, 9.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx())
    )
    val ttStep = (ttRight - ttLeft) / 7f
    val lBlinkCol = if (leftBlinkOn) Color(0xFF22C55E) else Color(0xFF334155)
    drawCircle(lBlinkCol, radius = 2.4.dp.toPx(), center = Offset(ttLeft + ttStep * 0.6f, telltaleY))
    val hazCol = if (state.hazardsOn && state.blinkerPhase) Color(0xFFEF4444) else Color(0xFF7F1D1D)
    drawCircle(hazCol, radius = 2.5.dp.toPx(), center = Offset(ttLeft + ttStep * 1.7f, telltaleY))
    val pkCol = if (brakeActive) Color(0xFFEF4444) else Color(0xFF475569)
    drawCircle(pkCol, radius = 2.6.dp.toPx(), center = Offset(ttLeft + ttStep * 2.8f, telltaleY))
    val engWarnCol = when {
        state.isEngineCranking -> Color(0xFFF59E0B)
        !state.engineRunning -> Color(0xFFEF4444)
        else -> Color(0xFF334155)
    }
    drawCircle(engWarnCol, radius = 2.3.dp.toPx(), center = Offset(ttLeft + ttStep * 3.9f, telltaleY))
    val hlCol = when (state.headlightMode) {
        HeadlightBeamMode.High -> Color(0xFF38BDF8)
        HeadlightBeamMode.Low -> Color(0xFF22C55E)
        HeadlightBeamMode.Off -> Color(0xFF334155)
    }
    drawCircle(hlCol, radius = 2.5.dp.toPx(), center = Offset(ttLeft + ttStep * 5.0f, telltaleY))
    val rBlinkCol = if (rightBlinkOn) Color(0xFF22C55E) else Color(0xFF334155)
    drawCircle(rBlinkCol, radius = 2.4.dp.toPx(), center = Offset(ttLeft + ttStep * 6.2f, telltaleY))

    // 10. Right Woodgrain Zone: Upper & Lower Illuminated Rocker Switch Banks (x = 0.634w .. 0.664w)
    fun drawRightSwitchBank(bx: Float, by: Float, bw: Float, bh: Float) {
        drawRoundRect(
            color = Color(0xFF181C24),
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF0B0E13),
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )
        val cw = bw / 2f
        val ch = bh / 3f
        for (r in 0..2) {
            for (c in 0..1) {
                val rx = bx + c * cw + 1.5.dp.toPx()
                val ry = by + r * ch + 1.5.dp.toPx()
                val rw = cw - 3.dp.toPx()
                val rh = ch - 3.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF262C36),
                    topLeft = Offset(rx, ry),
                    size = Size(rw, rh),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                val isLit = (c == 1) || (r == 1 && state.doorsOpen) || (r == 0 && state.wipersOn)
                drawLine(
                    color = if (isLit) Color(0xFF22C55E) else Color(0xFF475569),
                    start = Offset(rx + rw * 0.22f, ry + rh * 0.30f),
                    end = Offset(rx + rw * 0.78f, ry + rh * 0.30f),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
    drawRightSwitchBank(w * 0.633f, h * 0.668f, w * 0.028f, h * 0.072f)
    drawRightSwitchBank(w * 0.636f, h * 0.758f, w * 0.028f, h * 0.074f)

    // 11. Compact Low-Profile 3D Cockpit Steering Wheel & Dual Column Stalks
    // Positioned cleanly below the Instrument Cluster (clusterBot = 0.822h, wheel top rim = 0.832h)
    // so EVERY dial, petrol gauge, and readout inside the cluster is 100% unobstructed and crystal clear!
    val ckWheelX = (clusterLeft + clusterRight) * 0.50f
    val ckWheelY = h * 0.974f
    val ckWheelRx = (h * 0.162f).coerceIn(52.dp.toPx(), 84.dp.toPx())
    val ckWheelRy = ckWheelRx * 0.82f
    val ckRimStroke = ckWheelRx * 0.118f
    val ckAngRad = Math.toRadians(state.steeringWheelAngleDeg.toDouble()).toFloat()
    val ckCos = cos(ckAngRad)
    val ckSin = sin(ckAngRad)

    fun ckWheelPt(lx: Float, ly: Float): Offset {
        val rx = lx * ckCos - ly * ckSin
        val ry = lx * ckSin + ly * ckCos
        return Offset(ckWheelX + rx, ckWheelY + ry * 0.82f)
    }

    // Steering Column Shroud & Left/Right Combination Stalks
    drawRoundRect(
        color = Color(0xFF141820),
        topLeft = Offset(ckWheelX - ckWheelRx * 0.28f, ckWheelY - ckWheelRy * 0.16f),
        size = Size(ckWheelRx * 0.56f, h - ckWheelY + ckWheelRy * 0.22f),
        cornerRadius = CornerRadius(8.dp.toPx())
    )
    // Left Stalk
    drawLine(
        color = Color(0xFF222731),
        start = Offset(ckWheelX - ckWheelRx * 0.22f, ckWheelY - ckWheelRy * 0.18f),
        end = Offset(ckWheelX - ckWheelRx * 0.80f, ckWheelY - ckWheelRy * 0.28f),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawCircle(Color(0xAAFFFFFF), radius = 1.4.dp.toPx(), center = Offset(ckWheelX - ckWheelRx * 0.70f, ckWheelY - ckWheelRy * 0.27f))
    // Right Stalk
    drawLine(
        color = Color(0xFF222731),
        start = Offset(ckWheelX + ckWheelRx * 0.22f, ckWheelY - ckWheelRy * 0.16f),
        end = Offset(ckWheelX + ckWheelRx * 0.82f, ckWheelY - ckWheelRy * 0.24f),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawCircle(Color(0xAAFFFFFF), radius = 1.4.dp.toPx(), center = Offset(ckWheelX + ckWheelRx * 0.72f, ckWheelY - ckWheelRy * 0.23f))

    // Drop Shadow beneath Steering Wheel
    drawOval(
        color = Color(0x50000000),
        topLeft = Offset(ckWheelX - ckWheelRx, ckWheelY - ckWheelRy + 4.dp.toPx()),
        size = Size(ckWheelRx * 2f, ckWheelRy * 2f),
        style = Stroke(width = ckRimStroke * 1.15f)
    )

    // Rotating 3-Spoke Center Hub with Twin Brushed-Silver Vertical Accent Strips
    val leftSpoke = Path().apply {
        val p0 = ckWheelPt(-ckWheelRx * 0.92f, -ckWheelRx * 0.08f)
        val p1 = ckWheelPt(-ckWheelRx * 0.34f, -ckWheelRx * 0.14f)
        val p2 = ckWheelPt(-ckWheelRx * 0.34f, ckWheelRx * 0.18f)
        val p3 = ckWheelPt(-ckWheelRx * 0.92f, ckWheelRx * 0.10f)
        moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
    }
    val rightSpoke = Path().apply {
        val p0 = ckWheelPt(ckWheelRx * 0.92f, -ckWheelRx * 0.08f)
        val p1 = ckWheelPt(ckWheelRx * 0.34f, -ckWheelRx * 0.14f)
        val p2 = ckWheelPt(ckWheelRx * 0.34f, ckWheelRx * 0.18f)
        val p3 = ckWheelPt(ckWheelRx * 0.92f, ckWheelRx * 0.10f)
        moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
    }
    val spokeGrad = Brush.verticalGradient(listOf(Color(0xFF323844), Color(0xFF1A1E26)))
    drawPath(leftSpoke, brush = spokeGrad)
    drawPath(rightSpoke, brush = spokeGrad)

    // Center Horn Pad Trapezoid
    val centerPad = Path().apply {
        val p0 = ckWheelPt(-ckWheelRx * 0.38f, -ckWheelRx * 0.18f)
        val p1 = ckWheelPt(ckWheelRx * 0.38f, -ckWheelRx * 0.18f)
        val p2 = ckWheelPt(ckWheelRx * 0.28f, ckWheelRx * 0.88f)
        val p3 = ckWheelPt(-ckWheelRx * 0.28f, ckWheelRx * 0.88f)
        moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
    }
    drawPath(
        centerPad,
        brush = Brush.verticalGradient(
            listOf(Color(0xFF383F4C), Color(0xFF222730), Color(0xFF171B22)),
            startY = ckWheelY - ckWheelRy * 0.3f,
            endY = ckWheelY + ckWheelRy
        )
    )
    drawPath(centerPad, Color(0x38FFFFFF), style = Stroke(width = 1.1.dp.toPx()))

    // Twin Vertical Brushed-Silver Accent Strips on Center Hub
    for (side in floatArrayOf(-1f, 1f)) {
        val silverStrip = Path().apply {
            val p0 = ckWheelPt(side * ckWheelRx * 0.22f, -ckWheelRx * 0.17f)
            val p1 = ckWheelPt(side * ckWheelRx * 0.32f, -ckWheelRx * 0.16f)
            val p2 = ckWheelPt(side * ckWheelRx * 0.24f, ckWheelRx * 0.86f)
            val p3 = ckWheelPt(side * ckWheelRx * 0.16f, ckWheelRx * 0.86f)
            moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close()
        }
        drawPath(
            silverStrip,
            brush = Brush.linearGradient(
                listOf(Color(0xFFD1D5DB), Color(0xFF9CA3AF), Color(0xFF6B7280)),
                start = Offset(ckWheelX - ckWheelRx * 0.3f, ckWheelY - ckWheelRy * 0.2f),
                end = Offset(ckWheelX + ckWheelRx * 0.3f, ckWheelY + ckWheelRy * 0.8f)
            )
        )
    }

    // Left & Right Spoke Button Pods
    for (side in floatArrayOf(-1f, 1f)) {
        val podCenter = ckWheelPt(side * ckWheelRx * 0.50f, ckWheelRx * 0.02f)
        drawCircle(Color(0xFF171B22), radius = ckWheelRx * 0.11f, center = podCenter)
        drawCircle(Color(0x6694A3B8), radius = ckWheelRx * 0.11f, center = podCenter, style = Stroke(width = 1.dp.toPx()))
        drawCircle(Color(0xAAFFFFFF), radius = 1.1.dp.toPx(), center = Offset(podCenter.x - 2.5.dp.toPx(), podCenter.y))
        drawCircle(Color(0xAAFFFFFF), radius = 1.1.dp.toPx(), center = Offset(podCenter.x + 2.5.dp.toPx(), podCenter.y))
    }

    // 3D Dark Leather Outer Rim drawn cleanly over spoke tips
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF434B59), Color(0xFF181C24), Color(0xFF2E3540), Color(0xFF14171E)),
            start = Offset(ckWheelX - ckWheelRx, ckWheelY - ckWheelRy),
            end = Offset(ckWheelX + ckWheelRx, ckWheelY + ckWheelRy)
        ),
        topLeft = Offset(ckWheelX - ckWheelRx, ckWheelY - ckWheelRy),
        size = Size(ckWheelRx * 2f, ckWheelRy * 2f),
        style = Stroke(width = ckRimStroke)
    )
    drawArc(
        color = Color(0x42FFFFFF),
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(ckWheelX - ckWheelRx - ckRimStroke * 0.18f, ckWheelY - ckWheelRy - ckRimStroke * 0.18f),
        size = Size((ckWheelRx + ckRimStroke * 0.18f) * 2f, (ckWheelRy + ckRimStroke * 0.18f) * 2f),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
    )
    drawOval(
        color = Color(0xFF0D1016),
        topLeft = Offset(ckWheelX - ckWheelRx + ckRimStroke * 0.46f, ckWheelY - ckWheelRy + ckRimStroke * 0.46f),
        size = Size((ckWheelRx - ckRimStroke * 0.46f) * 2f, (ckWheelRy - ckRimStroke * 0.46f) * 2f),
        style = Stroke(width = 1.3.dp.toPx())
    )

    // 12. Left & Right Working 3D Rear-View Side Mirrors with Original Hino RM2 Livery Casing
    val mirrorCornerRadius = CornerRadius(10.dp.toPx())
    val mirrorInnerRadius = CornerRadius(7.dp.toPx())
    val glassBezel = 4.5.dp.toPx()

    // --- 12a. Left Exterior Side Mirror (x = 0.088w .. 0.168w, y = 0.032h .. 0.292h — Clear of Left Side Buttons) ---
    val leftMirrorW = w * 0.080f
    val leftMirrorH = h * 0.260f
    val leftMirrorX = w * 0.088f
    val leftMirrorY = h * 0.032f

    // Original Hino RM2 Upper Coach Mount (Pearl White + Royal Blue accent + Matte Black tubular strut)
    val topBracketMountL = Path().apply {
        moveTo(w * 0.130f, 0f)
        lineTo(w * 0.160f, 0f)
        lineTo(leftMirrorX + leftMirrorW + 14.dp.toPx(), leftMirrorY + leftMirrorH * 0.16f)
        lineTo(leftMirrorX + leftMirrorW, leftMirrorY + leftMirrorH * 0.16f)
        close()
    }
    drawPath(
        topBracketMountL,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFF4F7FC), Color(0xFFD8E0EC), Color(0xFF181C23)),
            startX = w * 0.130f,
            endX = leftMirrorX + leftMirrorW + 14.dp.toPx()
        )
    )

    // 3D Outer Mirror Shell visible on the Right Flank of the Left Mirror (Original Hino RM2 Pearl White + Royal Blue / Gold / Crimson Livery)
    val shellDepthW = w * 0.022f
    val shellBodyPathL = Path().apply {
        moveTo(leftMirrorX + leftMirrorW - 4.dp.toPx(), leftMirrorY + leftMirrorH * 0.15f)
        lineTo(leftMirrorX + leftMirrorW + shellDepthW, leftMirrorY + leftMirrorH * 0.15f)
        lineTo(leftMirrorX + leftMirrorW + shellDepthW * 0.88f, leftMirrorY + leftMirrorH - 4.dp.toPx())
        quadraticTo(
            leftMirrorX + leftMirrorW + shellDepthW * 0.4f,
            leftMirrorY + leftMirrorH + 3.dp.toPx(),
            leftMirrorX + leftMirrorW - 4.dp.toPx(),
            leftMirrorY + leftMirrorH
        )
        close()
    }
    drawPath(
        shellBodyPathL,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFE2E8F0), Color(0xFFF4F7FC), Color(0xFFCBD5E1)),
            startX = leftMirrorX + leftMirrorW - 4.dp.toPx(),
            endX = leftMirrorX + leftMirrorW + shellDepthW
        )
    )
    // Royal Blue & Gold Livery Accent Band across the 3D Mirror Shell
    val shellBlueBandL = Path().apply {
        moveTo(leftMirrorX + leftMirrorW - 3.dp.toPx(), leftMirrorY + leftMirrorH * 0.62f)
        lineTo(leftMirrorX + leftMirrorW + shellDepthW * 0.92f, leftMirrorY + leftMirrorH * 0.64f)
        lineTo(leftMirrorX + leftMirrorW + shellDepthW * 0.90f, leftMirrorY + leftMirrorH * 0.76f)
        lineTo(leftMirrorX + leftMirrorW - 3.dp.toPx(), leftMirrorY + leftMirrorH * 0.74f)
        close()
    }
    drawPath(shellBlueBandL, Color(0xFF1054A0))
    val shellGoldBandL = Path().apply {
        moveTo(leftMirrorX + leftMirrorW - 3.dp.toPx(), leftMirrorY + leftMirrorH * 0.75f)
        lineTo(leftMirrorX + leftMirrorW + shellDepthW * 0.90f, leftMirrorY + leftMirrorH * 0.77f)
        lineTo(leftMirrorX + leftMirrorW + shellDepthW * 0.89f, leftMirrorY + leftMirrorH * 0.83f)
        lineTo(leftMirrorX + leftMirrorW - 3.dp.toPx(), leftMirrorY + leftMirrorH * 0.81f)
        close()
    }
    drawPath(shellGoldBandL, Color(0xFFE4B214))

    val shellTopCapL = Path().apply {
        moveTo(leftMirrorX + leftMirrorW - 4.dp.toPx(), leftMirrorY)
        quadraticTo(
            leftMirrorX + leftMirrorW + shellDepthW * 0.6f,
            leftMirrorY - 2.dp.toPx(),
            leftMirrorX + leftMirrorW + shellDepthW,
            leftMirrorY + leftMirrorH * 0.16f
        )
        lineTo(leftMirrorX + leftMirrorW - 4.dp.toPx(), leftMirrorY + leftMirrorH * 0.16f)
        close()
    }
    drawPath(shellTopCapL, Color(0xFF181C23))

    // Left Mirror Matte-Black Contoured Bezel
    drawRoundRect(
        color = Color(0xFF12161F),
        topLeft = Offset(leftMirrorX, leftMirrorY),
        size = Size(leftMirrorW, leftMirrorH),
        cornerRadius = mirrorCornerRadius
    )
    drawRoundRect(
        color = Color(0xFF2B323F),
        topLeft = Offset(leftMirrorX, leftMirrorY),
        size = Size(leftMirrorW, leftMirrorH),
        cornerRadius = mirrorCornerRadius,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Live 3D Rear-View Camera Feed in Left Mirror
    drawLive3DRearViewMirrorGlass(
        state = state,
        sideSign = -1f,
        glassX = leftMirrorX + glassBezel,
        glassY = leftMirrorY + glassBezel,
        glassW = leftMirrorW - glassBezel * 2f,
        glassH = leftMirrorH - glassBezel * 2f,
        cornerRadius = mirrorInnerRadius,
        titlePaint = titlePaint,
        subPaint = subPaint
    )

    // --- 12b. Right Exterior Side Mirror (x = 0.632w .. 0.710w, y = 0.004h .. 0.264h) ---
    val rightMirrorW = w * 0.078f
    val rightMirrorH = h * 0.260f
    val rightMirrorX = w * 0.632f
    val rightMirrorY = h * 0.004f

    // Original Hino RM2 Pearl White & Royal Blue 3D Outer Shell Edge on Left Side of Right Mirror
    drawRoundRect(
        brush = Brush.verticalGradient(
            0.0f to Color(0xFF181C23),
            0.16f to Color(0xFFF4F7FC),
            0.64f to Color(0xFFE2E8F0),
            0.65f to Color(0xFF1054A0),
            0.77f to Color(0xFF1054A0),
            0.78f to Color(0xFFE4B214),
            0.84f to Color(0xFFBA1A18),
            1.0f to Color(0xFFCBD5E1),
            startY = rightMirrorY,
            endY = rightMirrorY + rightMirrorH
        ),
        topLeft = Offset(rightMirrorX - 6.dp.toPx(), rightMirrorY),
        size = Size(rightMirrorW + 6.dp.toPx(), rightMirrorH),
        cornerRadius = mirrorCornerRadius
    )
    // Contoured Matte-Black Rubber Bezel
    drawRoundRect(
        color = Color(0xFF12161F),
        topLeft = Offset(rightMirrorX, rightMirrorY),
        size = Size(rightMirrorW, rightMirrorH),
        cornerRadius = mirrorCornerRadius
    )
    drawRoundRect(
        color = Color(0xFF2B323F),
        topLeft = Offset(rightMirrorX, rightMirrorY),
        size = Size(rightMirrorW, rightMirrorH),
        cornerRadius = mirrorCornerRadius,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Live 3D Rear-View Camera Feed in Right Mirror
    drawLive3DRearViewMirrorGlass(
        state = state,
        sideSign = 1f,
        glassX = rightMirrorX + glassBezel,
        glassY = rightMirrorY + glassBezel,
        glassW = rightMirrorW - glassBezel * 2f,
        glassH = rightMirrorH - glassBezel * 2f,
        cornerRadius = mirrorInnerRadius,
        titlePaint = titlePaint,
        subPaint = subPaint
    )
}

/**
 * Renders a real, live 3D rear-view camera projection inside the Left (`sideSign = -1f`) or
 * Right (`sideSign = +1f`) exterior cockpit mirror glass, showing:
 *   1. True optical horizontal mirror reflection of the 3D roads, lane markings, shoulders,
 *      Sakthan 20-bay terminal, and Thrissur landmarks behind the bus.
 *   2. The 3D perspective side flank of the player's own Hino RM2 bus (Pearl White upper body,
 *      smoked window belt with vertical mullions, Royal Blue / Burgundy / Gold / Crimson 4-band
 *      livery, 3D tires, and live blinking turn indicator / brake tail-lamp glow).
 */
private fun DrawScope.drawLive3DRearViewMirrorGlass(
    state: SimUiState,
    sideSign: Float,
    glassX: Float,
    glassY: Float,
    glassW: Float,
    glassH: Float,
    cornerRadius: CornerRadius,
    titlePaint: Paint,
    subPaint: Paint
) {
    val clipShape = Path().apply {
        addRoundRect(
            RoundRect(
                left = glassX,
                top = glassY,
                right = glassX + glassW,
                bottom = glassY + glassH,
                cornerRadius = cornerRadius
            )
        )
    }

    val busSin = sin(state.busHeadingRad)
    val busCos = cos(state.busHeadingRad)
    val busFwdX = busSin
    val busFwdZ = busCos
    val busRightX = busCos
    val busRightZ = -busSin

    // Mirror camera eye mounted at front outer corner of bus (localX = ±1.35m, localY = 2.04m, localZ = 5.38m)
    val eyeLocalX = sideSign * 1.35f
    val eyeLocalY = 2.04f
    val eyeLocalZ = 5.38f
    val mirrorEye = Vec3(
        x = state.busPos.x + busRightX * eyeLocalX + busFwdX * eyeLocalZ,
        y = state.busPos.y + eyeLocalY,
        z = state.busPos.z + busRightZ * eyeLocalX + busFwdZ * eyeLocalZ
    )

    // Look backward along the bus flank, angled slightly outward so the inner ~24% of the mirror shows the bus side body
    // and the outer ~76% shows the adjacent lane, road markings, and buildings behind the bus.
    val rawLookLocalX = sideSign * 0.15f
    val rawLookLocalY = -0.055f - state.bodyPitchRad * 0.6f
    val rawLookLocalZ = -0.987f
    val rawFwdX = busRightX * rawLookLocalX + busFwdX * rawLookLocalZ
    val rawFwdY = rawLookLocalY
    val rawFwdZ = busRightZ * rawLookLocalX + busFwdZ * rawLookLocalZ
    val fwdLen = sqrt(rawFwdX * rawFwdX + rawFwdY * rawFwdY + rawFwdZ * rawFwdZ).coerceAtLeast(0.001f)
    val mirrorFwd = Vec3(rawFwdX / fwdLen, rawFwdY / fwdLen, rawFwdZ / fwdLen)

    // Optical Mirror Horizontal Axis:
    // Because a physical mirror reflects left/right relative to a rear-facing camera,
    // the horizontal screen axis (+X to the right of the mirror glass) aligns with +busRight:
    // rx = -mirrorFwd.z, rz = mirrorFwd.x (opposite to standard camera rx = +fwd.z, rz = -fwd.x).
    val rx = -mirrorFwd.z
    val rz = mirrorFwd.x
    val rLen = hypot(rx, rz).coerceAtLeast(0.001f)
    val mirrorRight = Vec3(rx / rLen, 0f, rz / rLen)

    // Orthonormal Up vector = normalize(worldUp - (worldUp dot mirrorFwd) * mirrorFwd)
    val upX = -mirrorFwd.y * mirrorFwd.x
    val upY = 1f - mirrorFwd.y * mirrorFwd.y
    val upZ = -mirrorFwd.y * mirrorFwd.z
    val uLen = sqrt(upX * upX + upY * upY + upZ * upZ).coerceAtLeast(0.001f)
    val mirrorUp = Vec3(upX / uLen, upY / uLen, upZ / uLen)

    val mirrorPitchRad = asin((-mirrorFwd.y).coerceIn(-0.90f, 0.90f))
    val mirrorCenterX = glassX + glassW * 0.5f
    val mirrorCenterY = glassY + glassH * 0.5f
    val mirrorFovScale = glassH * 0.90f
    val mirrorHorizonY = (mirrorCenterY - tan(mirrorPitchRad) * mirrorFovScale)
        .coerceIn(glassY + glassH * 0.15f, glassY + glassH * 0.82f)

    val mirrorRig = CameraRig(
        eye = mirrorEye,
        forward = mirrorFwd,
        right = mirrorRight,
        up = mirrorUp,
        fovScale = mirrorFovScale,
        screenCenterX = mirrorCenterX,
        screenCenterY = mirrorCenterY,
        screenW = size.width,
        screenH = size.height
    )

    clipPath(clipShape) {
        // 1. Live Mirror Sky & Ground Plane
        if (mirrorHorizonY > glassY) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to SkyZenith,
                    0.62f to SkyMid,
                    1.0f to SkyHorizonHaze,
                    startY = glassY,
                    endY = mirrorHorizonY
                ),
                topLeft = Offset(glassX, glassY),
                size = Size(glassW, mirrorHorizonY - glassY + 1f)
            )
        }
        if (mirrorHorizonY < glassY + glassH) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to SkyHorizonHaze,
                    0.06f to GroundFarVerge,
                    0.35f to GroundMidVerge,
                    1.0f to GroundNearEarth,
                    startY = mirrorHorizonY,
                    endY = glassY + glassH
                ),
                topLeft = Offset(glassX, mirrorHorizonY),
                size = Size(glassW, glassY + glassH - mirrorHorizonY)
            )
        }

        // 2. Collect & Sort Visible Rear Road Spans within 105m behind the bus (fast 60fps cockpit mirror)
        val mirrorMaxDistSq = 105f * 105f
        val mirrorSpans = ArrayList<ContiguousRoadSpan>(20)
        for (road in state.roads) {
            val frames = road.frames
            if (frames.size < 2) continue
            val chunkSize = 16
            var i = 0
            while (i < frames.size - 1) {
                var sIdx = i
                val eIdx = min(i + chunkSize, frames.size - 1)
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
                val dx = midF.pos.x - mirrorEye.x
                val dz = midF.pos.z - mirrorEye.z
                val distSq = dx * dx + dz * dz
                if (distSq <= mirrorMaxDistSq) {
                    val f0 = frames[sIdx]
                    val f1 = frames[eIdx]
                    val camZMid = dx * mirrorFwd.x + dz * mirrorFwd.z
                    val camZ0 = (f0.pos.x - mirrorEye.x) * mirrorFwd.x + (f0.pos.z - mirrorEye.z) * mirrorFwd.z
                    val camZ1 = (f1.pos.x - mirrorEye.x) * mirrorFwd.x + (f1.pos.z - mirrorEye.z) * mirrorFwd.z
                    if (camZMid > -16f || camZ0 > -16f || camZ1 > -16f) {
                        mirrorSpans.add(ContiguousRoadSpan(road, sIdx, eIdx, distSq))
                    }
                }
                i += chunkSize
            }
        }
        mirrorSpans.sortWith { a, b -> b.avgDistSq.compareTo(a.avgDistSq) }

        val mPath = Path()

        // 3. Render Lightweight 3D Shoulders, Asphalt Ribbons & Road Markings Behind the Bus (avoids 6,000-face CPU sort)
        for (span in mirrorSpans) {
            drawContinuousShouldersAndCurbs(mirrorRig, mPath, span)
        }
        for (span in mirrorSpans) {
            drawContinuousAsphaltRibbon(mirrorRig, mPath, span)
        }
        for (span in mirrorSpans) {
            drawContinuousRoadMarkings(mirrorRig, mPath, span)
        }

        // 4. Render Player's Own Hino RM2 Bus Side Flank in 3D Perspective (Original Pearl White + 4-Band Livery)
        val flankX = sideSign * 1.24f
        val zFront = 4.95f
        val zRear = -6.20f

        val pitchSin = sin(state.bodyPitchRad)
        val rollSin = sin(state.bodyRollRad)

        fun busFlankWorld(lx: Float, ly: Float, lz: Float): Vec3 {
            val pitchDeltaY = lz * pitchSin
            val rollDeltaY = -lx * rollSin
            val wy = state.busPos.y + ly + pitchDeltaY + rollDeltaY
            val wx = state.busPos.x + busRightX * lx + busFwdX * lz
            val wz = state.busPos.z + busRightZ * lx + busFwdZ * lz
            return Vec3(wx, wy, wz)
        }

        // 4a. Lower Dark Undercarriage & Wheel Wells (Y = 0.38m .. 0.72m)
        if (mirrorRig.buildClippedQuadPath(
                mPath,
                busFlankWorld(flankX, 0.38f, zFront),
                busFlankWorld(flankX, 0.38f, zRear),
                busFlankWorld(flankX, 0.72f, zRear),
                busFlankWorld(flankX, 0.72f, zFront)
            )
        ) {
            drawPath(mPath, Color(0xFF141820))
        }

        // 4b. Crimson Red Lower Livery Band (Y = 0.52m .. 1.32m)
        if (mirrorRig.buildClippedQuadPath(
                mPath,
                busFlankWorld(flankX, 0.52f, zFront),
                busFlankWorld(flankX, 0.52f, zRear),
                busFlankWorld(flankX, 1.35f, zRear),
                busFlankWorld(flankX, 1.18f, zFront)
            )
        ) {
            drawPath(mPath, Color(0xFFBA1A18))
        }

        // 4c. Gold Yellow Livery Band (Y = 1.18m..1.28m front -> 1.35m..1.48m rear)
        if (mirrorRig.buildClippedQuadPath(
                mPath,
                busFlankWorld(flankX, 1.18f, zFront),
                busFlankWorld(flankX, 1.35f, zRear),
                busFlankWorld(flankX, 1.48f, zRear),
                busFlankWorld(flankX, 1.28f, zFront)
            )
        ) {
            drawPath(mPath, Color(0xFFE4B214))
        }

        // 4d. Burgundy Livery Band (Y = 1.28m..1.39m front -> 1.48m..1.65m rear)
        if (mirrorRig.buildClippedQuadPath(
                mPath,
                busFlankWorld(flankX, 1.28f, zFront),
                busFlankWorld(flankX, 1.48f, zRear),
                busFlankWorld(flankX, 1.65f, zRear),
                busFlankWorld(flankX, 1.39f, zFront)
            )
        ) {
            drawPath(mPath, Color(0xFF921A1A))
        }

        // 4e. Royal Blue Livery Band (Y = 1.39m..1.56m front -> 1.65m..1.94m rear)
        if (mirrorRig.buildClippedQuadPath(
                mPath,
                busFlankWorld(flankX, 1.39f, zFront),
                busFlankWorld(flankX, 1.65f, zRear),
                busFlankWorld(flankX, 1.94f, zRear),
                busFlankWorld(flankX, 1.56f, zFront)
            )
        ) {
            drawPath(mPath, Color(0xFF1054A0))
        }

        // 4f. Pearl White Mid-Belt & Upper Roof Cantrail Crown (Y = 1.56m..3.18m)
        if (mirrorRig.buildClippedQuadPath(
                mPath,
                busFlankWorld(flankX, 1.56f, zFront),
                busFlankWorld(flankX, 1.94f, zRear),
                busFlankWorld(flankX * 0.94f, 3.18f, zRear),
                busFlankWorld(flankX * 0.94f, 3.18f, zFront)
            )
        ) {
            drawPath(mPath, Color(0xFFF4F7FC))
        }

        // 4g. Smoked Solar Side Window Belt (Y = 2.01m..2.94m) with Vertical Window Mullions
        if (mirrorRig.buildClippedQuadPath(
                mPath,
                busFlankWorld(flankX * 1.002f, 1.98f, zFront - 0.25f),
                busFlankWorld(flankX * 1.002f, 2.01f, zRear + 0.35f),
                busFlankWorld(flankX * 0.96f, 2.94f, zRear + 0.35f),
                busFlankWorld(flankX * 0.96f, 2.94f, zFront - 0.25f)
            )
        ) {
            drawPath(mPath, Color(0xFF162434))
        }
        // Vertical black window pillars & luggage bay seams along the flank
        for (pz in floatArrayOf(3.2f, 1.6f, 0.0f, -1.6f, -3.2f, -4.7f)) {
            val winSeg = mirrorRig.projectSegmentClipped(
                busFlankWorld(flankX * 1.004f, 2.00f, pz),
                busFlankWorld(flankX * 0.962f, 2.94f, pz)
            )
            if (winSeg != null) {
                drawLine(Color(0xFF0F141C), winSeg.first, winSeg.second, strokeWidth = 1.6f)
            }
            val lugSeg = mirrorRig.projectSegmentClipped(
                busFlankWorld(flankX * 1.003f, 0.56f, pz),
                busFlankWorld(flankX * 1.003f, 1.48f, pz)
            )
            if (lugSeg != null) {
                drawLine(Color(0x550F172A), lugSeg.first, lugSeg.second, strokeWidth = 1.1f)
            }
        }

        // 4h. Protruding Front & Rear Outer Tire Flanks visible in the mirror
        val tireOutX = sideSign * 1.265f
        for (wz in floatArrayOf(3.05f, -2.55f, -3.85f)) {
            if (mirrorRig.buildClippedQuadPath(
                    mPath,
                    busFlankWorld(tireOutX, 0.05f, wz + 0.48f),
                    busFlankWorld(tireOutX, 0.05f, wz - 0.48f),
                    busFlankWorld(tireOutX, 0.96f, wz - 0.48f),
                    busFlankWorld(tireOutX, 0.96f, wz + 0.48f)
                )
            ) {
                drawPath(mPath, Color(0xFF181B20))
            }
            if (mirrorRig.buildClippedQuadPath(
                    mPath,
                    busFlankWorld(tireOutX * 1.002f, 0.24f, wz + 0.28f),
                    busFlankWorld(tireOutX * 1.002f, 0.24f, wz - 0.28f),
                    busFlankWorld(tireOutX * 1.002f, 0.76f, wz - 0.28f),
                    busFlankWorld(tireOutX * 1.002f, 0.76f, wz + 0.28f)
                )
            ) {
                drawPath(mPath, Color(0xFFCBD5E1))
            }
        }

        // 4i. Realistic Perspective-Projected 3D Side Marker Strips & Wrap-Around Rear Corner Tail Lamp in Mirror
        val blinkerOn = state.blinkerPhase && (
            state.hazardsOn ||
                (sideSign < 0f && state.turnLeftOn) ||
                (sideSign > 0f && state.turnRightOn)
            )
        for (bz in floatArrayOf(3.85f, 0.40f, -5.85f)) {
            if (blinkerOn || state.headlightsOn) {
                val stripColor = if (blinkerOn) Color(0xFFFFB703) else Color(0xFFD97706)
                if (mirrorRig.buildClippedQuadPath(
                        mPath,
                        busFlankWorld(sideSign * 1.244f, 0.895f, bz - 0.11f),
                        busFlankWorld(sideSign * 1.244f, 0.895f, bz + 0.11f),
                        busFlankWorld(sideSign * 1.244f, 0.945f, bz + 0.11f),
                        busFlankWorld(sideSign * 1.244f, 0.945f, bz - 0.11f)
                    )
                ) {
                    drawPath(mPath, stripColor)
                }
            }
        }
        if (state.brakeInput > 0.05f || state.isParkBrakeActive || state.headlightsOn) {
            val isBraking = state.brakeInput > 0.05f || state.isParkBrakeActive
            val tailCol = if (isBraking) Color(0xFFFF2424) else Color(0xFFB91C1C)
            if (mirrorRig.buildClippedQuadPath(
                    mPath,
                    busFlankWorld(sideSign * 1.238f, 1.02f, -6.18f),
                    busFlankWorld(sideSign * 1.244f, 1.02f, -6.04f),
                    busFlankWorld(sideSign * 1.244f, 1.38f, -6.04f),
                    busFlankWorld(sideSign * 1.238f, 1.38f, -6.18f)
                )
            ) {
                drawPath(mPath, tailCol)
            }
        }

        // 5. Subtle Convex Automotive Mirror Specular Glass Glare & Blind-Spot Split Line
        val splitY = glassY + glassH * 0.78f
        drawLine(
            color = Color(0x550F172A),
            start = Offset(glassX, splitY),
            end = Offset(glassX + glassW, splitY),
            strokeWidth = 1.2.dp.toPx()
        )
        val glareStreak = Path().apply {
            moveTo(glassX + glassW * 0.12f, glassY)
            lineTo(glassX + glassW * 0.34f, glassY)
            lineTo(glassX + glassW * 0.08f, glassY + glassH)
            lineTo(glassX, glassY + glassH)
            close()
        }
        drawPath(glareStreak, Color(0x18FFFFFF))
    }
}

