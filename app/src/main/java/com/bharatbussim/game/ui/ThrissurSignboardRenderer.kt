package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.bharatbussim.game.model.Vec3
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Metadata and lazy high-resolution bitmap generator attached to any 3D signboard or terminal
 * gantry banner [QuadFace3D] so it can be globally depth-sorted alongside all buildings/trees
 * and rendered with exact perspective projective mapping (`Matrix.setPolyToPoly`).
 */
internal class SignboardQuadSpec(
    val cacheKey: String,
    val bmpWidth: Int,
    val bmpHeight: Int,
    val isDoubleSided: Boolean = true,
    val backplateColor: Color = Color(0xFF94A3B8),
    val backplateBorderColor: Color = Color(0xFF475569),
    val renderBackBitmap: (() -> Bitmap)? = null,
    val renderBitmap: () -> Bitmap
)

internal object SignboardTextureCache {
    private val cache = HashMap<String, Bitmap>(160)

    fun getOrCreate(spec: SignboardQuadSpec, isBackFace: Boolean = false): Bitmap {
        if (isBackFace && spec.renderBackBitmap != null) {
            val backKey = "${spec.cacheKey}_BACK"
            cache[backKey]?.let { return it }
            val bmp = spec.renderBackBitmap.invoke()
            cache[backKey] = bmp
            return bmp
        }
        cache[spec.cacheKey]?.let { return it }
        val bmp = spec.renderBitmap()
        cache[spec.cacheKey] = bmp
        return bmp
    }

    fun clear() {
        cache.clear()
    }
}

// Reusable scratch buffers for zero-allocation 60fps projective signboard rendering
private val signSrcPts = FloatArray(8)
private val signDstPts = FloatArray(8)
private val signTriSrc = FloatArray(6)
private val signTriDst = FloatArray(6)
private val signMatrix = Matrix()
private val signBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
    isDither = true
}
private val signClipNativePath = AndroidPath()
private val signTriClipNativePath = AndroidPath()

private data class SignClipVertex(
    val cx: Float,
    val cy: Float,
    val cz: Float,
    val u: Float,
    val v: Float
)

private val signRawVerts = Array(4) { SignClipVertex(0f, 0f, 0f, 0f, 0f) }
private val signClippedVerts = ArrayList<SignClipVertex>(8)

/**
 * Renders a 3D signboard quad using true perspective homography (`Matrix.setPolyToPoly`) when all
 * 4 corners are in front of `nearZ`, or Sutherland-Hodgman near-plane UV-clipped triangle warping
 * when partially behind the camera plane.
 */
internal fun DrawScope.drawProjectiveSignboardQuad(
    rig: CameraRig,
    path: Path,
    vBL: Vec3,
    vBR: Vec3,
    vTR: Vec3,
    vTL: Vec3,
    spec: SignboardQuadSpec
) {
    var c0 = rig.toCamSpace(vBL)
    var c1 = rig.toCamSpace(vBR)
    var c2 = rig.toCamSpace(vTR)
    var c3 = rig.toCamSpace(vTL)

    val nearZ = rig.nearZ
    if (c0.cz < nearZ && c1.cz < nearZ && c2.cz < nearZ && c3.cz < nearZ) {
        return
    }

    val e1x = c1.cx - c0.cx
    val e1y = c1.cy - c0.cy
    val e1z = c1.cz - c0.cz
    val e2x = c3.cx - c0.cx
    val e2y = c3.cy - c0.cy
    val e2z = c3.cz - c0.cz
    val nx = e1y * e2z - e1z * e2y
    val ny = e1z * e2x - e1x * e2z
    val nz = e1x * e2y - e1y * e2x
    val det = c0.cx * nx + c0.cy * ny + c0.cz * nz

    val isBack = det < 0f
    if (isBack) {
        if (!spec.isDoubleSided) {
            if (rig.buildClippedQuadPath(path, vBL, vBR, vTR, vTL)) {
                drawPath(path, spec.backplateColor)
                drawPath(path, spec.backplateBorderColor, style = Stroke(width = 1.6f))
            }
            return
        }
        val tmp0 = c0; c0 = c1; c1 = tmp0
        val tmp3 = c3; c3 = c2; c2 = tmp3
    }

    val bmpW = spec.bmpWidth.toFloat()
    val bmpH = spec.bmpHeight.toFloat()

    if (c0.cz >= nearZ && c1.cz >= nearZ && c2.cz >= nearZ && c3.cz >= nearZ) {
        val inv0 = rig.fovScale / c0.cz
        val sx0 = rig.screenCenterX + c0.cx * inv0
        val sy0 = rig.screenCenterY - c0.cy * inv0

        val inv1 = rig.fovScale / c1.cz
        val sx1 = rig.screenCenterX + c1.cx * inv1
        val sy1 = rig.screenCenterY - c1.cy * inv1

        val inv2 = rig.fovScale / c2.cz
        val sx2 = rig.screenCenterX + c2.cx * inv2
        val sy2 = rig.screenCenterY - c2.cy * inv2

        val inv3 = rig.fovScale / c3.cz
        val sx3 = rig.screenCenterX + c3.cx * inv3
        val sy3 = rig.screenCenterY - c3.cy * inv3

        val minX = min(min(sx0, sx1), min(sx2, sx3))
        val maxX = max(max(sx0, sx1), max(sx2, sx3))
        val minY = min(min(sy0, sy1), min(sy2, sy3))
        val maxY = max(max(sy0, sy1), max(sy2, sy3))
        if (maxX < -250f || minX > rig.screenW + 250f || maxY < -250f || minY > rig.screenH + 250f) {
            return
        }

        val edgeTop = hypot(sx2 - sx3, sy2 - sy3)
        val edgeBot = hypot(sx1 - sx0, sy1 - sy0)
        val edgeLeft = hypot(sx3 - sx0, sy3 - sy0)
        val edgeRight = hypot(sx2 - sx1, sy2 - sy1)
        if (max(edgeTop, edgeBot) < 6f || max(edgeLeft, edgeRight) < 4f) {
            return
        }

        signSrcPts[0] = 0f;   signSrcPts[1] = bmpH
        signSrcPts[2] = bmpW; signSrcPts[3] = bmpH
        signSrcPts[4] = bmpW; signSrcPts[5] = 0f
        signSrcPts[6] = 0f;   signSrcPts[7] = 0f

        signDstPts[0] = sx0; signDstPts[1] = sy0
        signDstPts[2] = sx1; signDstPts[3] = sy1
        signDstPts[4] = sx2; signDstPts[5] = sy2
        signDstPts[6] = sx3; signDstPts[7] = sy3

        if (signMatrix.setPolyToPoly(signSrcPts, 0, signDstPts, 0, 4)) {
            val bmp = SignboardTextureCache.getOrCreate(spec, isBackFace = isBack)
            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.concat(signMatrix)
            nativeCanvas.drawBitmap(bmp, 0f, 0f, signBitmapPaint)
            nativeCanvas.restore()
            return
        }
    }

    signRawVerts[0] = SignClipVertex(c0.cx, c0.cy, c0.cz, 0f, bmpH)
    signRawVerts[1] = SignClipVertex(c1.cx, c1.cy, c1.cz, bmpW, bmpH)
    signRawVerts[2] = SignClipVertex(c2.cx, c2.cy, c2.cz, bmpW, 0f)
    signRawVerts[3] = SignClipVertex(c3.cx, c3.cy, c3.cz, 0f, 0f)

    signClippedVerts.clear()
    for (i in 0 until 4) {
        val curr = signRawVerts[i]
        val prev = signRawVerts[(i + 3) and 3]
        val currIn = curr.cz >= nearZ
        val prevIn = prev.cz >= nearZ
        if (currIn != prevIn) {
            val t = (nearZ - prev.cz) / (curr.cz - prev.cz)
            signClippedVerts.add(
                SignClipVertex(
                    cx = prev.cx + (curr.cx - prev.cx) * t,
                    cy = prev.cy + (curr.cy - prev.cy) * t,
                    cz = nearZ,
                    u = prev.u + (curr.u - prev.u) * t,
                    v = prev.v + (curr.v - prev.v) * t
                )
            )
        }
        if (currIn) {
            signClippedVerts.add(curr)
        }
    }
    if (signClippedVerts.size < 3) return

    signClipNativePath.reset()
    val sxClip = FloatArray(signClippedVerts.size)
    val syClip = FloatArray(signClippedVerts.size)
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (i in 0 until signClippedVerts.size) {
        val v = signClippedVerts[i]
        val invZ = rig.fovScale / v.cz
        val sx = rig.screenCenterX + v.cx * invZ
        val sy = rig.screenCenterY - v.cy * invZ
        sxClip[i] = sx
        syClip[i] = sy
        if (sx < minX) minX = sx; if (sx > maxX) maxX = sx
        if (sy < minY) minY = sy; if (sy > maxY) maxY = sy
        if (i == 0) signClipNativePath.moveTo(sx, sy) else signClipNativePath.lineTo(sx, sy)
    }
    signClipNativePath.close()

    if (maxX < -250f || minX > rig.screenW + 250f || maxY < -250f || minY > rig.screenH + 250f) {
        return
    }

    val bmp = SignboardTextureCache.getOrCreate(spec, isBackFace = isBack)
    val nativeCanvas = drawContext.canvas.nativeCanvas
    nativeCanvas.save()
    nativeCanvas.clipPath(signClipNativePath)
    val v0c = signClippedVerts[0]
    for (k in 1 until signClippedVerts.size - 1) {
        val v1c = signClippedVerts[k]
        val v2c = signClippedVerts[k + 1]
        signTriSrc[0] = v0c.u; signTriSrc[1] = v0c.v
        signTriSrc[2] = v1c.u; signTriSrc[3] = v1c.v
        signTriSrc[4] = v2c.u; signTriSrc[5] = v2c.v

        signTriDst[0] = sxClip[0]; signTriDst[1] = syClip[0]
        signTriDst[2] = sxClip[k]; signTriDst[3] = syClip[k]
        signTriDst[4] = sxClip[k + 1]; signTriDst[5] = syClip[k + 1]

        if (signMatrix.setPolyToPoly(signTriSrc, 0, signTriDst, 0, 3)) {
            signTriClipNativePath.reset()
            signTriClipNativePath.moveTo(sxClip[0], syClip[0])
            signTriClipNativePath.lineTo(sxClip[k], syClip[k])
            signTriClipNativePath.lineTo(sxClip[k + 1], syClip[k + 1])
            signTriClipNativePath.close()

            nativeCanvas.save()
            nativeCanvas.clipPath(signTriClipNativePath)
            nativeCanvas.concat(signMatrix)
            nativeCanvas.drawBitmap(bmp, 0f, 0f, signBitmapPaint)
            nativeCanvas.restore()
        }
    }
    nativeCanvas.restore()
}

// ============================================================================
// BAKED HIGH-RESOLUTION SIGNBOARD, CLOCK DIAL & BANNER TEXTURE FACTORIES
// ============================================================================

private fun createSignTitlePaint(colorInt: Int = android.graphics.Color.WHITE): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

private fun createSignSubPaint(colorInt: Int = android.graphics.Color.WHITE): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

private fun drawFittedText(
    canvas: Canvas,
    text: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    baseTextSize: Float,
    paint: Paint
) {
    if (text.isBlank()) return
    paint.textSize = baseTextSize
    val measured = paint.measureText(text)
    if (measured > maxWidth && measured > 1f) {
        paint.textSize = (baseTextSize * (maxWidth / measured)).coerceAtLeast(10f)
    }
    canvas.drawText(text, x, y, paint)
}

/**
 * Creates a classic analog Clock Tower dial with black Roman numerals (XII, III, VI, IX),
 * brass rim, and clock hands for the 12-meter Sakthan Stand Clock Tower and TCR Station Clock Tower.
 */
internal fun createClockDialSignSpec(
    cacheKey: String,
    wallBgColorInt: Int = 0xFF991B1B.toInt()
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = 256,
    bmpHeight = 256,
    isDoubleSided = true
) {
    val sz = 256
    val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sz * 0.5f
    val cy = sz * 0.5f
    val r = sz * 0.42f

    canvas.drawColor(wallBgColorInt)

    val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFAF8F5.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, facePaint)

    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD97706.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    canvas.drawCircle(cx, cy, r, rimPaint)

    val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111827.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    for (h in 0 until 12) {
        val ang = Math.toRadians((h * 30).toDouble()).toFloat()
        val r0 = if (h % 3 == 0) r * 0.78f else r * 0.85f
        val r1 = r * 0.93f
        canvas.drawLine(
            cx + sin(ang) * r0,
            cy - cos(ang) * r0,
            cx + sin(ang) * r1,
            cy - cos(ang) * r1,
            tickPaint
        )
    }

    val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = 26f
    }
    canvas.drawText("XII", cx, cy - r * 0.52f, numPaint)
    canvas.drawText("VI", cx, cy + r * 0.68f, numPaint)
    canvas.drawText("IX", cx - r * 0.58f, cy + 9f, numPaint)
    canvas.drawText("III", cx + r * 0.58f, cy + 9f, numPaint)

    // Hour & Minute Hands (10:10)
    val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 7f
    }
    canvas.drawLine(cx, cy, cx - r * 0.38f, cy - r * 0.24f, handPaint)
    handPaint.strokeWidth = 5f
    canvas.drawLine(cx, cy, cx + r * 0.48f, cy - r * 0.32f, handPaint)
    facePaint.color = 0xFFB45309.toInt()
    canvas.drawCircle(cx, cy, 7f, facePaint)
    bmp
}

private fun drawVectorHighwayArrow(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    dir: String,
    length: Float,
    strokeW: Float,
    paintFill: Paint
) {
    canvas.save()
    canvas.translate(cx, cy)
    when (dir) {
        "LEFT" -> canvas.rotate(-90f)
        "RIGHT" -> canvas.rotate(90f)
    }
    val halfL = length * 0.5f
    val headLen = length * 0.42f
    val headHalfW = strokeW * 2.15f
    val halfShaft = strokeW * 0.45f

    val arrowPath = AndroidPath().apply {
        moveTo(0f, -halfL)
        lineTo(headHalfW, -halfL + headLen)
        lineTo(halfShaft, -halfL + headLen)
        lineTo(halfShaft, halfL)
        lineTo(-halfShaft, halfL)
        lineTo(-halfShaft, -halfL + headLen)
        lineTo(-headHalfW, -halfL + headLen)
        close()
    }
    canvas.drawPath(arrowPath, paintFill)
    canvas.restore()
}

private fun sectorRouteHeaderForRoad(roadId: String, uniqueIndex: Int, isReverse: Boolean): Pair<String, String> {
    val secNum = (uniqueIndex % 12) + 1
    val secCode = "SEC-%02d".format(secNum)
    return when (roadId) {
        "ROAD_SWARAJ_ROUND" -> Pair(secCode, "സ്വരാജ് റൗണ്ട് • SWARAJ ROUND RING")
        "ROAD_SAKTHAN_TO_SWARAJ" -> Pair(secCode, if (!isReverse) "M.O. ROAD • NORTHBOUND TO ROUND" else "M.O. ROAD • SOUTHBOUND TO SAKTHAN")
        "ROAD_SAKTHAN_STAND_ENTRY" -> Pair(secCode, "SAKTHAN TERMINAL & TCR LINK ROAD")
        "ROAD_STATION_ROAD" -> Pair(secCode, "RAILWAY STATION ROAD (TCR CORRIDOR)")
        "ROAD_SWARAJ_TO_VADAKKE" -> Pair("SH 22 • $secCode", "SHORANUR ROAD • VADAKKE STAND")
        "ROAD_SWARAJ_TO_WEST_FORT" -> Pair("SH 75 • $secCode", "M.G. ROAD • WEST FORT CORRIDOR")
        "ROAD_WEST_FORT_TO_KANJANI" -> Pair("SH 75 • $secCode", "THRISSUR - KANJANI - GURUVAYUR HWY")
        "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD" -> Pair("SH 75 • $secCode", "GURUVAYUR - CHAVAKKAD CORRIDOR")
        "ROAD_SH61_TRIPRAYAR_TO_KANJANI" -> Pair("SH 61 • $secCode", "TRIPRAYAR - KANJANI STATE HIGHWAY")
        "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" -> Pair("NH 66 • $secCode", "COASTAL NATIONAL HIGHWAY 66")
        "ROAD_SWARAJ_TO_MANNUTHY" -> Pair("SH 51 • $secCode", "EAST FORT - MANNUTHY ARTERIAL")
        "ROAD_MANNUTHY_TO_PEECHI_DAM" -> Pair("SH 51 • $secCode", "MANNUTHY - PEECHI DAM ROAD")
        "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" -> Pair("NH 544 • $secCode", "MANNUTHY - PALIYEKKARA 6-LANE NH")
        "ROAD_SAKTHAN_TO_OLLUR" -> Pair("NH LINK • $secCode", "SAKTHAN - OLLUR - NH 544 LINK")
        "ROAD_PUTHANPALLI_LINK" -> Pair(secCode, "HIGH ROAD • PUTHANPALLI CORRIDOR")
        else -> Pair(secCode, "THRISSUR CITY CORRIDOR")
    }
}

private fun deriveReverseDirectionSpec(spec: KeralaRoadSignSpec, uniqueIndex: Int): KeralaRoadSignSpec {
    fun flipDir(d: String): String = when (d) {
        "LEFT" -> "RIGHT"
        "RIGHT" -> "LEFT"
        else -> "UP"
    }
    val (_, hdrTitle) = sectorRouteHeaderForRoad(spec.roadId, uniqueIndex, isReverse = true)
    return when (spec.roadId) {
        "ROAD_SAKTHAN_TO_SWARAJ" -> if (spec.travelDir >= 0) {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.3 KM"),
                leftDest = KeralaSignDestination("ഒല്ലൂർ NH 544", "Ollur NH 544", "LEFT", "1.2 KM"),
                rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.2 KM"),
                travelDir = -1
            )
        } else {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.3 KM"),
                leftDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "LEFT", "0.2 KM"),
                rightDest = KeralaSignDestination("പുത്തൻപള്ളി", "Puthanpalli", "RIGHT", "0.5 KM"),
                travelDir = 1
            )
        }
        "ROAD_STATION_ROAD" -> if (spec.travelDir >= 0) {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "0.1 KM"),
                leftDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "UP", "0.3 KM"),
                rightDest = KeralaSignDestination("എം.ഒ. റോഡ്", "M.O. Road", "LEFT", "0.2 KM"),
                travelDir = -1
            )
        } else {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "0.2 KM"),
                leftDest = KeralaSignDestination("വെസ്റ്റ് ഫോർട്ട്", "West Fort", "LEFT", "1.0 KM"),
                rightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "RIGHT", "1.3 KM"),
                travelDir = 1
            )
        }
        "ROAD_SWARAJ_TO_VADAKKE" -> if (spec.travelDir >= 0) {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "1.1 KM"),
                leftDest = KeralaSignDestination("മണ്ണുത്തി NH 544", "Mannuthy", "LEFT", "7.0 KM"),
                rightDest = KeralaSignDestination("റെയിൽവേ സ്റ്റേഷൻ", "Railway Station", "RIGHT", "1.4 KM"),
                travelDir = -1
            )
        } else {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "UP", "0.3 KM"),
                leftDest = KeralaSignDestination("സ്റ്റാൻഡ് എൻട്രി", "Stand Entry", "LEFT", "0.2 KM"),
                rightDest = KeralaSignDestination("ഷൊർണ്ണൂർ SH 22", "Shoranur", "UP", "28 KM"),
                travelDir = 1
            )
        }
        "ROAD_SWARAJ_TO_WEST_FORT", "ROAD_WEST_FORT_TO_KANJANI" -> if (spec.travelDir >= 0) {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("സ്വരാജ് റൗണ്ട്", "Swaraj Round", "UP", "1.0 KM"),
                leftDest = KeralaSignDestination("വടക്കേ സ്റ്റാൻഡ്", "Vadakke Stand", "LEFT", "2.1 KM"),
                rightDest = KeralaSignDestination("ശക്തൻ സ്റ്റാൻഡ്", "Sakthan Stand", "RIGHT", "1.4 KM"),
                travelDir = -1
            )
        } else {
            spec.copy(
                headerTitle = hdrTitle,
                straightDest = KeralaSignDestination("കാഞ്ഞാണി SH 75", "Kanjani", "UP", "11.4 KM"),
                leftDest = KeralaSignDestination("ഗുരുവായൂർ", "Guruvayur", "UP", "16.1 KM"),
                rightDest = KeralaSignDestination("ചാവക്കാട് ബീച്ച്", "Chavakkad Beach", "LEFT", "16.5 KM"),
                travelDir = 1
            )
        }
        else -> spec.copy(
            headerTitle = hdrTitle,
            straightDest = spec.straightDest.copy(arrowDir = "UP"),
            leftDest = spec.rightDest.copy(arrowDir = flipDir(spec.rightDest.arrowDir)),
            rightDest = spec.leftDest.copy(arrowDir = flipDir(spec.leftDest.arrowDir)),
            travelDir = -spec.travelDir
        )
    }
}

internal fun createKeralaRoadSignSpec(spec: KeralaRoadSignSpec, uniqueIndex: Int): SignboardQuadSpec {
    val key = "NHAI_V2_${uniqueIndex}_${spec.roadId}_${spec.style.name}_${spec.straightDest.engName}_${spec.headerTitle}"
    val revSpec = deriveReverseDirectionSpec(spec, uniqueIndex)
    return when (spec.style) {
        KeralaSignStyle.ROUNDABOUT_DIAGRAM -> SignboardQuadSpec(
            cacheKey = key,
            bmpWidth = 576,
            bmpHeight = 360,
            isDoubleSided = true,
            renderBackBitmap = { bakeRoundaboutDiagramBitmap(576, 360, revSpec, uniqueIndex, isReverse = true) }
        ) {
            bakeRoundaboutDiagramBitmap(576, 360, spec, uniqueIndex, isReverse = false)
        }
        KeralaSignStyle.STACKED_3_PANEL -> SignboardQuadSpec(
            cacheKey = key,
            bmpWidth = 576,
            bmpHeight = 384,
            isDoubleSided = true,
            renderBackBitmap = { bakeStacked3PanelBitmap(576, 384, revSpec, uniqueIndex, isReverse = true) }
        ) {
            bakeStacked3PanelBitmap(576, 384, spec, uniqueIndex, isReverse = false)
        }
        KeralaSignStyle.OVERHEAD_HIGHWAY_GANTRY -> SignboardQuadSpec(
            cacheKey = key,
            bmpWidth = 960,
            bmpHeight = 236,
            isDoubleSided = true,
            renderBackBitmap = { bakeOverheadGantryBitmap(960, 236, revSpec, uniqueIndex, isReverse = true) }
        ) {
            bakeOverheadGantryBitmap(960, 236, spec, uniqueIndex, isReverse = false)
        }
    }
}

internal fun createBannerSignSpec(
    cacheKey: String,
    bgColorInt: Int,
    borderColorInt: Int,
    titleText: String,
    subText: String = "",
    titleColorInt: Int = android.graphics.Color.WHITE,
    subColorInt: Int = android.graphics.Color.WHITE,
    isDoubleSided: Boolean = true,
    bmpWidth: Int = 576,
    bmpHeight: Int = 144
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = bmpWidth,
    bmpHeight = bmpHeight,
    isDoubleSided = isDoubleSided
) {
    val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val w = bmpWidth.toFloat()
    val h = bmpHeight.toFloat()

    canvas.drawColor(bgColorInt)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColorInt
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    val pad = 7f
    canvas.drawRoundRect(RectF(pad, pad, w - pad, h - pad), 12f, 12f, borderPaint)

    val titlePaint = createSignTitlePaint(titleColorInt).apply {
        textAlign = Paint.Align.CENTER
    }
    val subPaint = createSignSubPaint(subColorInt).apply {
        textAlign = Paint.Align.CENTER
    }

    val safeW = w - 44f
    if (subText.isBlank()) {
        val baseSz = h * 0.44f
        drawFittedText(
            canvas = canvas,
            text = titleText,
            x = w * 0.5f,
            y = h * 0.5f + baseSz * 0.34f,
            maxWidth = safeW,
            baseTextSize = baseSz,
            paint = titlePaint
        )
    } else {
        drawFittedText(
            canvas = canvas,
            text = titleText,
            x = w * 0.5f,
            y = h * 0.42f,
            maxWidth = safeW,
            baseTextSize = h * 0.34f,
            paint = titlePaint
        )
        drawFittedText(
            canvas = canvas,
            text = subText,
            x = w * 0.5f,
            y = h * 0.78f,
            maxWidth = safeW,
            baseTextSize = h * 0.24f,
            paint = subPaint
        )
    }
    bmp
}

/**
 * Creates a high-visibility authentic Indian Railways trilingual station board
 * (available in Mustard-Yellow monument style or Southern Railway Royal-Blue & Gold fascia style)
 * with double borders, Indian Railways emblem roundels on left/right, bold Malayalam/English/Hindi
 * station title, and station code / platform subtitle banner.
 */
internal fun createIndianRailwaysStationSignSpec(
    cacheKey: String,
    bgColorInt: Int,
    borderColorInt: Int,
    accentBandColorInt: Int,
    malTitle: String,
    engTitle: String,
    subTitle: String,
    titleColorInt: Int,
    subColorInt: Int,
    isDoubleSided: Boolean = true,
    bmpWidth: Int = 960,
    bmpHeight: Int = 240,
    badgeLabel: String = "IR",
    badgeBgColorInt: Int = 0xFFB91C1C.toInt()
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = bmpWidth,
    bmpHeight = bmpHeight,
    isDoubleSided = isDoubleSided,
    backplateColor = Color(0xFF1E293B),
    backplateBorderColor = Color(borderColorInt)
) {
    val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val w = bmpWidth.toFloat()
    val h = bmpHeight.toFloat()

    canvas.drawColor(bgColorInt)

    // Bottom accent ribbon band for subtitle / Southern Railway / Station Code
    val bandTop = h * 0.72f
    val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentBandColorInt
        style = Paint.Style.FILL
    }
    canvas.drawRect(8f, bandTop, w - 8f, h - 8f, bandPaint)

    // Outer & inner crisp borders
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColorInt
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    canvas.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 14f, 14f, borderPaint)
    borderPaint.strokeWidth = 3.5f
    canvas.drawLine(12f, bandTop, w - 12f, bandTop, borderPaint)

    // Left & Right Emblem Roundels
    val emblemR = h * 0.21f
    val emblemCy = bandTop * 0.52f
    val emblemFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    val emblemStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColorInt
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    val badgeTextPaint = createSignTitlePaint(android.graphics.Color.WHITE).apply {
        textSize = if (badgeLabel.length >= 3) emblemR * 0.52f else emblemR * 0.68f
    }
    for (emblemCx in floatArrayOf(18f + emblemR + 12f, w - 18f - emblemR - 12f)) {
        emblemFill.color = badgeBgColorInt
        canvas.drawCircle(emblemCx, emblemCy, emblemR, emblemFill)
        canvas.drawCircle(emblemCx, emblemCy, emblemR, emblemStroke)
        canvas.drawCircle(emblemCx, emblemCy, emblemR * 0.74f, emblemStroke)
        canvas.drawText(badgeLabel, emblemCx, emblemCy + emblemR * 0.23f, badgeTextPaint)
    }

    val safeTextW = w - (emblemR * 4.6f) - 72f
    val malPaint = createSignTitlePaint(titleColorInt).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    val engPaint = createSignTitlePaint(titleColorInt).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    val subPaint = createSignTitlePaint(subColorInt).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    drawFittedText(
        canvas = canvas,
        text = malTitle,
        x = w * 0.5f,
        y = h * 0.31f,
        maxWidth = safeTextW,
        baseTextSize = h * 0.24f,
        paint = malPaint
    )
    drawFittedText(
        canvas = canvas,
        text = engTitle,
        x = w * 0.5f,
        y = h * 0.62f,
        maxWidth = safeTextW,
        baseTextSize = h * 0.24f,
        paint = engPaint
    )
    drawFittedText(
        canvas = canvas,
        text = subTitle,
        x = w * 0.5f,
        y = h * 0.90f,
        maxWidth = w - 48f,
        baseTextSize = h * 0.165f,
        paint = subPaint
    )
    bmp
}

private fun bakeRoundaboutDiagramBitmap(
    wPx: Int,
    hPx: Int,
    spec: KeralaRoadSignSpec,
    uniqueIndex: Int = 0,
    isReverse: Boolean = false
): Bitmap {
    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val w = wPx.toFloat()
    val h = hPx.toFloat()

    canvas.drawColor(0xFF006D5B.toInt())
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    canvas.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 14f, 14f, borderPaint)

    val (secBadge, defaultHdr) = sectorRouteHeaderForRoad(spec.roadId, uniqueIndex, isReverse)
    val headerText = spec.headerTitle.ifBlank { defaultHdr }
    val hdrY = 58f
    val hdrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF024E40.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRect(12f, 12f, w - 12f, hdrY, hdrBgPaint)

    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    canvas.drawLine(12f, hdrY, w - 12f, hdrY, dividerPaint)

    // Yellow Sector / Route Code Pill Badge on Left of Header
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFACC15.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(18f, 17f, 134f, 52f), 8f, 8f, pillPaint)
    val pillTextPaint = createSignTitlePaint(0xFF0F172A.toInt()).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    drawFittedText(canvas, secBadge, 76f, 41f, 108f, 20f, pillTextPaint)

    val hdrPaint = createSignTitlePaint(0xFFFEF08A.toInt()).apply {
        textAlign = Paint.Align.CENTER
    }
    drawFittedText(canvas, headerText, (138f + w - 16f) * 0.5f, 42f, w - 164f, 23f, hdrPaint)

    val cx = w * 0.5f
    val cy = 226f
    val ringR = 32f
    val roadStroke = 11f
    val diagramStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = roadStroke
        strokeCap = Paint.Cap.BUTT
    }
    val whiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val headSz = 14f

    canvas.drawCircle(cx, cy, ringR, diagramStrokePaint)
    canvas.drawLine(cx, cy + ringR, cx, h - 18f, diagramStrokePaint)

    val topTipY = 76f
    canvas.drawLine(cx, cy - ringR, cx, topTipY + headSz * 0.85f, diagramStrokePaint)
    val tri = AndroidPath()
    tri.moveTo(cx, topTipY)
    tri.lineTo(cx - headSz, topTipY + headSz * 1.25f)
    tri.lineTo(cx + headSz, topTipY + headSz * 1.25f)
    tri.close()
    canvas.drawPath(tri, whiteFillPaint)

    val leftTipX = cx - 88f
    canvas.drawLine(cx - ringR, cy, leftTipX + headSz * 0.85f, cy, diagramStrokePaint)
    tri.reset()
    tri.moveTo(leftTipX, cy)
    tri.lineTo(leftTipX + headSz * 1.25f, cy - headSz)
    tri.lineTo(leftTipX + headSz * 1.25f, cy + headSz)
    tri.close()
    canvas.drawPath(tri, whiteFillPaint)

    val rightTipX = cx + 88f
    canvas.drawLine(cx + ringR, cy, rightTipX - headSz * 0.85f, cy, diagramStrokePaint)
    tri.reset()
    tri.moveTo(rightTipX, cy)
    tri.lineTo(rightTipX - headSz * 1.25f, cy - headSz)
    tri.lineTo(rightTipX - headSz * 1.25f, cy + headSz)
    tri.close()
    canvas.drawPath(tri, whiteFillPaint)

    val engLeftPaint = createSignTitlePaint(android.graphics.Color.WHITE).apply {
        textAlign = Paint.Align.LEFT
    }
    val malLeftPaint = createSignSubPaint(0xFFE2E8F0.toInt()).apply {
        textAlign = Paint.Align.LEFT
    }
    val engRightPaint = createSignTitlePaint(android.graphics.Color.WHITE).apply {
        textAlign = Paint.Align.RIGHT
    }
    val malRightPaint = createSignSubPaint(0xFFE2E8F0.toInt()).apply {
        textAlign = Paint.Align.RIGHT
    }

    val topLabelX = cx + 26f
    val topMaxW = w - topLabelX - 24f
    drawFittedText(canvas, spec.straightDest.malName, topLabelX, 102f, topMaxW, 25f, malLeftPaint)
    drawFittedText(canvas, "${spec.straightDest.engName}  ${spec.straightDest.distKm}", topLabelX, 134f, topMaxW, 27f, engLeftPaint)

    val leftLabelX = leftTipX - 14f
    val leftMaxW = leftLabelX - 22f
    drawFittedText(canvas, spec.leftDest.malName, leftLabelX, cy - 4f, leftMaxW, 25f, malRightPaint)
    drawFittedText(canvas, "${spec.leftDest.engName}  ${spec.leftDest.distKm}", leftLabelX, cy + 30f, leftMaxW, 27f, engRightPaint)

    val rightLabelX = rightTipX + 14f
    val rightMaxW = w - rightLabelX - 22f
    drawFittedText(canvas, spec.rightDest.malName, rightLabelX, cy - 4f, rightMaxW, 25f, malLeftPaint)
    drawFittedText(canvas, "${spec.rightDest.engName}  ${spec.rightDest.distKm}", rightLabelX, cy + 30f, rightMaxW, 27f, engLeftPaint)

    return bmp
}

private fun bakeStacked3PanelBitmap(
    wPx: Int,
    hPx: Int,
    spec: KeralaRoadSignSpec,
    uniqueIndex: Int = 0,
    isReverse: Boolean = false
): Bitmap {
    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val w = wPx.toFloat()
    val h = hPx.toFloat()

    canvas.drawColor(0xFF046A4E.toInt())
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    canvas.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 14f, 14f, borderPaint)

    // Top Sector & Corridor Header Band (12f .. 58f)
    val hdrBot = 58f
    val hdrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF024E3B.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRect(12f, 12f, w - 12f, hdrBot, hdrBgPaint)

    val (secBadge, corridorTitle) = sectorRouteHeaderForRoad(spec.roadId, uniqueIndex, isReverse)
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFACC15.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(18f, 17f, 156f, 52f), 8f, 8f, pillPaint)
    val pillTextPaint = createSignTitlePaint(0xFF0F172A.toInt()).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    drawFittedText(canvas, secBadge, 87f, 41f, 130f, 20f, pillTextPaint)

    val hdrPaint = createSignTitlePaint(0xFFFEF08A.toInt()).apply {
        textAlign = Paint.Align.CENTER
    }
    drawFittedText(canvas, corridorTitle, (162f + w - 16f) * 0.5f, 42f, w - 184f, 22f, hdrPaint)

    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    val innerTop = hdrBot
    val innerBot = h - 12f
    val rowH = (innerBot - innerTop) / 3f
    canvas.drawLine(12f, innerTop, w - 12f, innerTop, dividerPaint)
    canvas.drawLine(12f, innerTop + rowH, w - 12f, innerTop + rowH, dividerPaint)
    canvas.drawLine(12f, innerTop + rowH * 2f, w - 12f, innerTop + rowH * 2f, dividerPaint)

    val malPaint = createSignSubPaint(0xFFFEF08A.toInt()).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    val engPaint = createSignTitlePaint(android.graphics.Color.WHITE).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    val distPaint = createSignTitlePaint(android.graphics.Color.WHITE).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    val rows = arrayOf(spec.straightDest, spec.leftDest, spec.rightDest)
    val nameMaxW = 352f
    for (i in 0..2) {
        val cy = innerTop + (i + 0.5f) * rowH
        val dest = rows[i]

        drawFittedText(canvas, dest.malName, 26f, cy - 8f, nameMaxW, 27f, malPaint)
        drawFittedText(canvas, dest.engName, 26f, cy + 30f, nameMaxW, 31f, engPaint)

        drawVectorHighwayArrow(
            canvas = canvas,
            cx = 426f,
            cy = cy,
            dir = dest.arrowDir,
            length = 40f,
            strokeW = 8.2f,
            paintFill = arrowFillPaint
        )

        val distLabel = if (dest.distKm.contains("KM", ignoreCase = true) || dest.distKm.contains("M", ignoreCase = true) || dest.distKm == "TCR") {
            dest.distKm
        } else {
            "${dest.distKm} KM"
        }
        drawFittedText(canvas, distLabel, w - 22f, cy + 12f, 102f, 28f, distPaint)
    }
    return bmp
}

private fun bakeOverheadGantryBitmap(
    wPx: Int,
    hPx: Int,
    spec: KeralaRoadSignSpec,
    uniqueIndex: Int = 0,
    isReverse: Boolean = false
): Bitmap {
    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val w = wPx.toFloat()
    val h = hPx.toFloat()

    canvas.drawColor(0xFF057A55.toInt())
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    canvas.drawRoundRect(RectF(7f, 7f, w - 7f, h - 7f), 12f, 12f, borderPaint)

    val headerBottomY = 58f
    val hdrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF034E37.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRect(11f, 11f, w - 11f, headerBottomY, hdrBgPaint)

    val (secBadge, defaultHdr) = sectorRouteHeaderForRoad(spec.roadId, uniqueIndex, isReverse)
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFACC15.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(RectF(18f, 16f, 184f, 52f), 8f, 8f, pillPaint)
    val pillTextPaint = createSignTitlePaint(0xFF0F172A.toInt()).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    drawFittedText(canvas, secBadge, 101f, 41f, 154f, 21f, pillTextPaint)

    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    canvas.drawLine(12f, headerBottomY, w - 12f, headerBottomY, dividerPaint)

    val hdrPaint = createSignSubPaint(0xFFFEF08A.toInt()).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    val hdrText = spec.headerTitle.ifBlank { defaultHdr }
    drawFittedText(canvas, hdrText, (192f + w - 20f) * 0.5f, 42f, w - 220f, 24f, hdrPaint)

    val colW = (w - 24f) / 3f
    canvas.drawLine(12f + colW, headerBottomY, 12f + colW, h - 12f, dividerPaint)
    canvas.drawLine(12f + colW * 2f, headerBottomY, 12f + colW * 2f, h - 12f, dividerPaint)

    val engPaint = createSignTitlePaint(android.graphics.Color.WHITE).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    val malPaint = createSignSubPaint(0xFFFEF08A.toInt()).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    val distPaint = createSignTitlePaint(0xFFFEF08A.toInt()).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    val cols = arrayOf(spec.leftDest, spec.straightDest, spec.rightDest)
    for (cIdx in 0..2) {
        val colLeft = 12f + cIdx * colW
        val dest = cols[cIdx]

        drawVectorHighwayArrow(
            canvas = canvas,
            cx = colLeft + 34f,
            cy = 122f,
            dir = dest.arrowDir,
            length = 44f,
            strokeW = 8.5f,
            paintFill = arrowFillPaint
        )

        drawFittedText(canvas, dest.engName, colLeft + 62f, 122f, colW - 82f, 32f, engPaint)
        drawFittedText(canvas, dest.malName, colLeft + 62f, 174f, colW - 156f, 25f, malPaint)
        drawFittedText(canvas, dest.distKm, colLeft + colW - 18f, 174f, 88f, 25f, distPaint)
    }
    return bmp
}
