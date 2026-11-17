package com.bharatbussim.game.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.viewmodel.SimUiState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal val GlassTopColor = Color(0xD92B3442)
internal val GlassBottomColor = Color(0xE01B212B)
internal val GlassBorderColor = Color(0x7A738398)
internal val ActiveOrangeStart = Color(0xFFF59E0B)
internal val ActiveOrangeEnd = Color(0xFFD97706)
internal val ActiveRedGlow = Color(0xFFEF4444)

@Composable
internal fun HudGlassSquareButton(
    width: Dp,
    height: Dp,
    cornerRadius: Dp = 10.dp,
    active: Boolean = false,
    activeTint: Color = ActiveOrangeStart,
    badgeText: String? = null,
    contentDesc: String,
    testTag: String,
    onClick: () -> Unit,
    iconDraw: DrawScope.(Color) -> Unit
) {
    val bgBrush = if (active) {
        Brush.verticalGradient(
            listOf(
                activeTint.copy(alpha = 0.42f),
                Color(0xE01B212B)
            )
        )
    } else {
        Brush.verticalGradient(listOf(GlassTopColor, GlassBottomColor))
    }
    val borderColor = if (active) activeTint.copy(alpha = 0.90f) else GlassBorderColor
    val iconColor = if (active) activeTint else Color.White

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(cornerRadius), clip = false)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgBrush)
            .border(1.1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 6.dp,
                    end = 6.dp,
                    top = if (badgeText != null) 4.dp else 6.dp,
                    bottom = if (badgeText != null) 9.dp else 6.dp
                )
        ) {
            iconDraw(iconColor)
        }
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = iconColor,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.5.dp)
            )
        }
    }
}

// ==========================================
// EXACT VECTOR HUD ICONS (MATCHING REFERENCE)
// ==========================================

internal fun DrawScope.drawPauseIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val barW = w * 0.21f
    val barH = h * 0.62f
    val top = (h - barH) * 0.5f
    val gap = w * 0.18f
    val leftX = (w - (barW * 2f + gap)) * 0.5f
    drawRoundRect(
        color = tint,
        topLeft = Offset(leftX, top),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barW * 0.35f, barW * 0.35f)
    )
    drawRoundRect(
        color = tint,
        topLeft = Offset(leftX + barW + gap, top),
        size = Size(barW, barH),
        cornerRadius = CornerRadius(barW * 0.35f, barW * 0.35f)
    )
}

internal fun DrawScope.drawEyeSlashIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val rx = w * 0.40f
    val ry = h * 0.27f

    val eyePath = Path().apply {
        moveTo(cx - rx, cy)
        quadraticTo(cx, cy - ry * 1.45f, cx + rx, cy)
        quadraticTo(cx, cy + ry * 1.45f, cx - rx, cy)
        close()
    }
    drawPath(eyePath, color = tint, style = Stroke(width = w * 0.085f, join = StrokeJoin.Round))
    drawCircle(color = tint, radius = w * 0.13f, center = Offset(cx, cy))

    // Dark mask behind slash for crisp separation
    drawLine(
        color = Color(0xFF1E2530),
        start = Offset(cx - rx * 0.92f, cy + ry * 1.25f),
        end = Offset(cx + rx * 0.92f, cy - ry * 1.25f),
        strokeWidth = w * 0.16f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = tint,
        start = Offset(cx - rx * 0.92f, cy + ry * 1.25f),
        end = Offset(cx + rx * 0.92f, cy - ry * 1.25f),
        strokeWidth = w * 0.09f,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawCameraIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val bodyLeft = w * 0.10f
    val bodyTop = h * 0.28f
    val bodyW = w * 0.80f
    val bodyH = h * 0.56f

    // Top camera viewfinder prism bump
    val prismPath = Path().apply {
        moveTo(w * 0.30f, bodyTop + 1f)
        lineTo(w * 0.37f, h * 0.14f)
        lineTo(w * 0.63f, h * 0.14f)
        lineTo(w * 0.70f, bodyTop + 1f)
        close()
    }
    drawPath(prismPath, color = tint)

    // Shutter release button on top-left
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.16f, h * 0.21f),
        size = Size(w * 0.10f, h * 0.08f),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Camera main body
    drawRoundRect(
        color = tint,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(w * 0.11f, w * 0.11f)
    )

    // Lens cutout ring + inner aperture + flash dot
    val cutout = Color(0xFF1D232D)
    val lensCenter = Offset(w * 0.50f, bodyTop + bodyH * 0.52f)
    drawCircle(color = cutout, radius = w * 0.195f, center = lensCenter)
    drawCircle(
        color = tint,
        radius = w * 0.13f,
        center = lensCenter,
        style = Stroke(width = w * 0.048f)
    )
    drawCircle(color = tint, radius = w * 0.055f, center = lensCenter)
    drawCircle(color = cutout, radius = w * 0.042f, center = Offset(w * 0.78f, bodyTop + bodyH * 0.22f))
}

internal fun DrawScope.drawWiperIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.84f
    val outerR = w * 0.56f
    val innerR = w * 0.22f

    val fanPath = Path().apply {
        arcTo(
            rect = Rect(cx - outerR, cy - outerR, cx + outerR, cy + outerR),
            startAngleDegrees = -140f,
            sweepAngleDegrees = 100f,
            forceMoveTo = true
        )
        arcTo(
            rect = Rect(cx - innerR, cy - innerR, cx + innerR, cy + innerR),
            startAngleDegrees = -40f,
            sweepAngleDegrees = -100f,
            forceMoveTo = false
        )
        close()
    }
    drawPath(fanPath, color = tint, style = Stroke(width = w * 0.068f, join = StrokeJoin.Round))

    // Wiper blade arm from bottom-center pivot toward top-right
    val pivot = Offset(cx, h * 0.74f)
    val tip = Offset(cx + outerR * 0.46f, cy - outerR * 0.78f)
    drawLine(color = tint, start = pivot, end = tip, strokeWidth = w * 0.075f, cap = StrokeCap.Round)
    drawCircle(color = tint, radius = w * 0.075f, center = pivot)
}

internal fun DrawScope.drawHighBeamIcon(tint: Color) {
    val w = size.width
    val h = size.height
    // D-shaped lamp dome on the right
    val domeLeft = w * 0.46f
    val domeRight = w * 0.88f
    val domeTop = h * 0.22f
    val domeBottom = h * 0.78f
    val domePath = Path().apply {
        moveTo(domeLeft, domeTop)
        lineTo(domeLeft + (domeRight - domeLeft) * 0.30f, domeTop)
        cubicTo(
            domeRight, domeTop,
            domeRight, domeBottom,
            domeLeft + (domeRight - domeLeft) * 0.30f, domeBottom
        )
        lineTo(domeLeft, domeBottom)
        quadraticTo(domeLeft - w * 0.05f, h * 0.5f, domeLeft, domeTop)
        close()
    }
    drawPath(domePath, color = tint, style = Stroke(width = w * 0.075f, join = StrokeJoin.Round))

    // Three horizontal parallel light beam lines on the left
    val beamStartX = w * 0.10f
    val beamEndX = w * 0.37f
    val yLevels = floatArrayOf(0.30f, 0.50f, 0.70f)
    for (yf in yLevels) {
        drawLine(
            color = tint,
            start = Offset(beamStartX, h * yf),
            end = Offset(beamEndX, h * yf),
            strokeWidth = w * 0.072f,
            cap = StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawLowBeamIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val domeLeft = w * 0.46f
    val domeRight = w * 0.88f
    val domeTop = h * 0.20f
    val domeBottom = h * 0.76f
    val domePath = Path().apply {
        moveTo(domeLeft, domeTop)
        lineTo(domeLeft + (domeRight - domeLeft) * 0.30f, domeTop)
        cubicTo(
            domeRight, domeTop,
            domeRight, domeBottom,
            domeLeft + (domeRight - domeLeft) * 0.30f, domeBottom
        )
        lineTo(domeLeft, domeBottom)
        quadraticTo(domeLeft - w * 0.05f, h * 0.48f, domeLeft, domeTop)
        close()
    }
    drawPath(domePath, color = tint, style = Stroke(width = w * 0.075f, join = StrokeJoin.Round))

    // Three downward-angled dipped beam rays on the left
    val beamStartX = w * 0.37f
    val beamEndX = w * 0.10f
    val yLevels = floatArrayOf(0.28f, 0.48f, 0.68f)
    for (yf in yLevels) {
        drawLine(
            color = tint,
            start = Offset(beamStartX, h * yf),
            end = Offset(beamEndX, h * (yf + 0.14f)),
            strokeWidth = w * 0.072f,
            cap = StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawHazardTriangleIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val topY = h * 0.14f
    val botY = h * 0.86f
    val halfBase = w * 0.42f

    val outer = Path().apply {
        moveTo(cx, topY)
        lineTo(cx + halfBase, botY)
        lineTo(cx - halfBase, botY)
        close()
    }
    drawPath(outer, color = tint, style = Stroke(width = w * 0.085f, join = StrokeJoin.Round))

    val inner = Path().apply {
        moveTo(cx, h * 0.36f)
        lineTo(cx + halfBase * 0.56f, h * 0.74f)
        lineTo(cx - halfBase * 0.56f, h * 0.74f)
        close()
    }
    drawPath(inner, color = tint, style = Stroke(width = w * 0.055f, join = StrokeJoin.Round))
}

internal fun DrawScope.drawBugleHornIcon(tint: Color) {
    val w = size.width
    val h = size.height
    // Bell on right, mouthpiece on left, curved loop underneath
    val bellPath = Path().apply {
        moveTo(w * 0.08f, h * 0.42f)
        lineTo(w * 0.50f, h * 0.42f)
        cubicTo(
            w * 0.70f, h * 0.42f,
            w * 0.82f, h * 0.25f,
            w * 0.90f, h * 0.18f
        )
        lineTo(w * 0.90f, h * 0.72f)
        cubicTo(
            w * 0.80f, h * 0.64f,
            w * 0.68f, h * 0.54f,
            w * 0.48f, h * 0.54f
        )
        lineTo(w * 0.08f, h * 0.50f)
        close()
    }
    drawPath(bellPath, color = tint)

    // Mouthpiece flange
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.05f, h * 0.38f),
        size = Size(w * 0.07f, h * 0.16f),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Lower bugle loop coil
    val coilRect = Rect(w * 0.24f, h * 0.47f, w * 0.62f, h * 0.78f)
    drawArc(
        color = tint,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = coilRect.topLeft,
        size = coilRect.size,
        style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
    )
}

internal fun DrawScope.drawSingleDoorIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val frameLeft = w * 0.22f
    val frameTop = h * 0.12f
    val frameW = w * 0.56f
    val frameH = h * 0.76f

    // Door frame outline
    drawRoundRect(
        color = tint,
        topLeft = Offset(frameLeft, frameTop),
        size = Size(frameW, frameH),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        style = Stroke(width = w * 0.075f)
    )
    // Solid left door panel
    val panelW = frameW * 0.44f
    drawRect(
        color = tint,
        topLeft = Offset(frameLeft, frameTop),
        size = Size(panelW, frameH)
    )
    // Open-door arrow pointing across doorway
    val ax0 = frameLeft + panelW + frameW * 0.10f
    val ax1 = frameLeft + frameW * 0.84f
    val ay = h * 0.50f
    drawLine(tint, Offset(ax0, ay), Offset(ax1 - frameW * 0.10f, ay), strokeWidth = w * 0.068f, cap = StrokeCap.Round)
    val headPath = Path().apply {
        moveTo(ax1, ay)
        lineTo(ax1 - frameW * 0.20f, ay - h * 0.10f)
        lineTo(ax1 - frameW * 0.20f, ay + h * 0.10f)
        close()
    }
    drawPath(headPath, color = tint)
}

internal fun DrawScope.drawIndicatorArrowIcon(tint: Color, isLeft: Boolean) {
    val w = size.width
    val h = size.height
    val cy = h * 0.5f
    val arrowPath = if (isLeft) {
        Path().apply {
            moveTo(w * 0.14f, cy)
            lineTo(w * 0.50f, h * 0.20f)
            lineTo(w * 0.50f, h * 0.36f)
            lineTo(w * 0.84f, h * 0.36f)
            lineTo(w * 0.84f, h * 0.64f)
            lineTo(w * 0.50f, h * 0.64f)
            lineTo(w * 0.50f, h * 0.80f)
            close()
        }
    } else {
        Path().apply {
            moveTo(w * 0.86f, cy)
            lineTo(w * 0.50f, h * 0.20f)
            lineTo(w * 0.50f, h * 0.36f)
            lineTo(w * 0.16f, h * 0.36f)
            lineTo(w * 0.16f, h * 0.64f)
            lineTo(w * 0.50f, h * 0.64f)
            lineTo(w * 0.50f, h * 0.80f)
            close()
        }
    }
    drawPath(arrowPath, color = tint)
}

internal fun DrawScope.drawParkBrakeSymbol(tint: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val r = w * 0.25f

    // Center circle
    drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = Stroke(width = w * 0.072f))

    // Left and right curved brake shoe brackets: (  )
    val bracketR = w * 0.37f
    val rect = Rect(cx - bracketR, cy - bracketR, cx + bracketR, cy + bracketR)
    drawArc(
        color = tint,
        startAngle = 132f,
        sweepAngle = 96f,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = w * 0.072f, cap = StrokeCap.Round)
    )
    drawArc(
        color = tint,
        startAngle = -48f,
        sweepAngle = 96f,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = w * 0.072f, cap = StrokeCap.Round)
    )

    // Bold P inside circle
    val pStroke = w * 0.072f
    val pLeft = cx - r * 0.30f
    val pTop = cy - r * 0.48f
    val pBot = cy + r * 0.48f
    drawLine(tint, Offset(pLeft, pTop), Offset(pLeft, pBot), strokeWidth = pStroke, cap = StrokeCap.Round)
    val pLoop = Path().apply {
        moveTo(pLeft, pTop)
        lineTo(cx + r * 0.14f, pTop)
        cubicTo(
            cx + r * 0.45f, pTop,
            cx + r * 0.45f, cy + r * 0.04f,
            cx + r * 0.14f, cy + r * 0.04f
        )
        lineTo(pLeft, cy + r * 0.04f)
    }
    drawPath(pLoop, color = tint, style = Stroke(width = pStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

internal fun DrawScope.drawEngineStartIcon(tint: Color) {
    val d = size.minDimension
    val cx = size.width * 0.5f
    val cy = size.height * 0.52f
    val r = d * 0.34f
    val strokeW = d * 0.088f
    drawArc(
        color = tint,
        startAngle = -52f,
        sweepAngle = 284f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
    drawLine(
        color = tint,
        start = Offset(cx, cy - r * 1.18f),
        end = Offset(cx, cy - r * 0.12f),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawSpeedometerDialIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.56f
    val r = w * 0.42f
    drawArc(
        color = tint,
        startAngle = 155f,
        sweepAngle = 230f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
    )
    // Needle
    drawCircle(color = tint, radius = w * 0.09f, center = Offset(cx, cy))
    drawLine(
        color = tint,
        start = Offset(cx, cy),
        end = Offset(cx + r * 0.62f, cy - r * 0.58f),
        strokeWidth = w * 0.09f,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawFuelPumpIcon(tint: Color) {
    val w = size.width
    val h = size.height
    val bodyLeft = w * 0.18f
    val bodyTop = h * 0.14f
    val bodyW = w * 0.46f
    val bodyH = h * 0.72f

    // Pump body
    drawRoundRect(
        color = tint,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
    )
    // Display window cutout
    drawRect(
        color = Color(0xFF1D232D),
        topLeft = Offset(bodyLeft + bodyW * 0.18f, bodyTop + bodyH * 0.15f),
        size = Size(bodyW * 0.64f, bodyH * 0.28f)
    )
    // Base plinth
    drawRoundRect(
        color = tint,
        topLeft = Offset(bodyLeft - w * 0.06f, bodyTop + bodyH - h * 0.06f),
        size = Size(bodyW + w * 0.12f, h * 0.10f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    // Fuel hose & nozzle on right
    val hose = Path().apply {
        moveTo(bodyLeft + bodyW, bodyTop + bodyH * 0.30f)
        lineTo(w * 0.78f, bodyTop + bodyH * 0.30f)
        lineTo(w * 0.78f, bodyTop + bodyH * 0.76f)
        quadraticTo(w * 0.78f, bodyTop + bodyH * 0.88f, w * 0.86f, bodyTop + bodyH * 0.84f)
        lineTo(w * 0.88f, bodyTop + bodyH * 0.36f)
        lineTo(w * 0.76f, bodyTop + bodyH * 0.14f)
    }
    drawPath(hose, color = tint, style = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ==========================================
// REALISTIC 3D-STYLED STEERING WHEEL
// ==========================================

@Composable
internal fun RealisticSteeringWheel(
    wheelAngleProvider: () -> Float,
    sizeDp: Dp,
    onSteerGrab: () -> Unit,
    onSteerDeltaDeg: (Float) -> Unit,
    onSteerRelease: () -> Unit,
    onHornClick: () -> Unit
) {
    val latestOnGrab by rememberUpdatedState(onSteerGrab)
    val latestOnDelta by rememberUpdatedState(onSteerDeltaDeg)
    val latestOnRelease by rememberUpdatedState(onSteerRelease)
    val latestOnHorn by rememberUpdatedState(onHornClick)

    Box(
        modifier = Modifier
            .size(sizeDp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    latestOnGrab()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val cx = w * 0.5f
                    val cy = size.height * 0.5f
                    var prevPos = down.position
                    var totalTravel = 0f
                    var smoothedStepDeg = 0f

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val currPos = change.position
                            val dx = currPos.x - prevPos.x
                            val dy = currPos.y - prevPos.y
                            val stepDist = hypot(dx, dy)
                            if (stepDist > 0.20f) {
                                change.consume()
                                totalTravel += stepDist

                                val midX = (prevPos.x + currPos.x) * 0.5f - cx
                                val midY = (prevPos.y + currPos.y) * 0.5f - cy
                                val rMid = hypot(midX, midY)

                                // Continuous 360° tangential rim tracking around wheel center (cx, cy):
                                // Clockwise = positive (Right), Counter-Clockwise = negative (Left)
                                // across all 4 quadrants (12, 3, 6, 9 o'clock) with zero sign flips or jumps.
                                val effR = rMid.coerceAtLeast(w * 0.20f)
                                val tangentStepPx = (midX * dy - midY * dx) / effR
                                val circularDeltaDeg = (tangentStepPx / (w * 0.25f)) * (180f / PI.toFloat())

                                // Smooth center-hub regularization so crossing near (cx, cy) never spikes
                                val rimWeight = ((rMid - w * 0.08f) / (w * 0.10f)).coerceIn(0f, 1f)
                                val centerHorizDeg = (dx / (w * 0.28f)) * 180f
                                val rawDelta = (circularDeltaDeg * rimWeight + centerHorizDeg * (1f - rimWeight))
                                    .coerceIn(-40f, 40f)

                                smoothedStepDeg = smoothedStepDeg * 0.25f + rawDelta * 0.75f
                                latestOnDelta(smoothedStepDeg)
                                prevPos = currPos
                            }
                        }

                        // Tap near center hub triggers pneumatic air horn
                        val tapDistFromHub = hypot(prevPos.x - cx, prevPos.y - cy)
                        if (totalTravel < 14f && tapDistFromHub < size.width * 0.24f) {
                            latestOnHorn()
                        }
                    } finally {
                        latestOnRelease()
                    }
                }
            }
            .semantics { contentDescription = "Bus Steering Wheel" }
            .testTag("virtual_steering_wheel"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = wheelAngleProvider() }
        ) {
            val w = size.width
            val h = size.height
            val c = Offset(w * 0.5f, h * 0.5f)
            val rimRadius = w * 0.442f
            val rimStroke = w * 0.086f

            // Drop shadow behind steering wheel
            drawCircle(
                color = Color(0x66000000),
                radius = rimRadius + rimStroke * 0.12f,
                center = Offset(c.x, c.y + 4.dp.toPx()),
                style = Stroke(width = rimStroke * 1.15f)
            )

            // Outer dark leather coach wheel rim with metallic sheen gradient
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF424A56), Color(0xFF15181E), Color(0xFF2D343F), Color(0xFF111419)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                radius = rimRadius,
                center = c,
                style = Stroke(width = rimStroke)
            )

            // Ergonomic 10-and-2 thumb bolster wraps on rim
            for (startAng in floatArrayOf(196f, 294f)) {
                drawArc(
                    color = Color(0xFF222730),
                    startAngle = startAng,
                    sweepAngle = 50f,
                    useCenter = false,
                    topLeft = Offset(c.x - rimRadius, c.y - rimRadius),
                    size = Size(rimRadius * 2f, rimRadius * 2f),
                    style = Stroke(width = rimStroke * 1.12f, cap = StrokeCap.Round)
                )
            }

            // 12-o'clock amber leather center-alignment band for tracking 1.5-round (540°) rotation
            drawArc(
                color = ActiveOrangeStart,
                startAngle = 265f,
                sweepAngle = 10f,
                useCenter = false,
                topLeft = Offset(c.x - rimRadius, c.y - rimRadius),
                size = Size(rimRadius * 2f, rimRadius * 2f),
                style = Stroke(width = rimStroke * 1.04f, cap = StrokeCap.Butt)
            )

            // Sculpted specular top highlight arc, leather stitch seam & inner rim bevel
            drawArc(
                color = Color(0x3CFFFFFF),
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(c.x - rimRadius - rimStroke * 0.20f, c.y - rimRadius - rimStroke * 0.20f),
                size = Size((rimRadius + rimStroke * 0.20f) * 2f, (rimRadius + rimStroke * 0.20f) * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawCircle(
                color = Color(0x3094A3B8),
                radius = rimRadius,
                center = c,
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF0A0C10),
                radius = rimRadius - rimStroke * 0.48f,
                center = c,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Sleek coach horizontal spokes (leaving wide open inside view)
            val leftSpoke = Path().apply {
                moveTo(c.x - rimRadius + rimStroke * 0.22f, c.y - w * 0.045f)
                lineTo(c.x - w * 0.10f, c.y - w * 0.052f)
                lineTo(c.x - w * 0.10f, c.y + w * 0.062f)
                lineTo(c.x - rimRadius + rimStroke * 0.22f, c.y + w * 0.042f)
                close()
            }
            val rightSpoke = Path().apply {
                moveTo(c.x + rimRadius - rimStroke * 0.22f, c.y - w * 0.045f)
                lineTo(c.x + w * 0.10f, c.y - w * 0.052f)
                lineTo(c.x + w * 0.10f, c.y + w * 0.062f)
                lineTo(c.x + rimRadius - rimStroke * 0.22f, c.y + w * 0.042f)
                close()
            }
            val spokeBrush = Brush.verticalGradient(listOf(Color(0xFF2D333D), Color(0xFF171B21)))
            drawPath(leftSpoke, brush = spokeBrush)
            drawPath(rightSpoke, brush = spokeBrush)

            // Brushed silver V-bezel wrapping the bottom spoke
            val silverBezel = Path().apply {
                moveTo(c.x - w * 0.125f, c.y + w * 0.05f)
                lineTo(c.x - w * 0.065f, c.y + rimRadius - rimStroke * 0.28f)
                lineTo(c.x + w * 0.065f, c.y + rimRadius - rimStroke * 0.28f)
                lineTo(c.x + w * 0.125f, c.y + w * 0.05f)
                lineTo(c.x + w * 0.075f, c.y + w * 0.042f)
                lineTo(c.x + w * 0.036f, c.y + rimRadius - rimStroke * 0.44f)
                lineTo(c.x - w * 0.036f, c.y + rimRadius - rimStroke * 0.44f)
                lineTo(c.x - w * 0.075f, c.y + w * 0.042f)
                close()
            }
            drawPath(
                silverBezel,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF94A3B8), Color(0xFFE2E8F0), Color(0xFF64748B)),
                    start = Offset(c.x - w * 0.13f, c.y),
                    end = Offset(c.x + w * 0.13f, c.y + rimRadius)
                )
            )

            // Compact sculpted center horn pad (maximizing open interior wheel space)
            val hubPath = Path().apply {
                moveTo(c.x - w * 0.125f, c.y - w * 0.088f)
                quadraticTo(c.x, c.y - w * 0.115f, c.x + w * 0.125f, c.y - w * 0.088f)
                lineTo(c.x + w * 0.142f, c.y + w * 0.040f)
                lineTo(c.x + w * 0.080f, c.y + w * 0.130f)
                quadraticTo(c.x, c.y + w * 0.150f, c.x - w * 0.080f, c.y + w * 0.130f)
                lineTo(c.x - w * 0.142f, c.y + w * 0.040f)
                close()
            }
            drawPath(
                hubPath,
                brush = Brush.verticalGradient(listOf(Color(0xFF333944), Color(0xFF1A1E25)))
            )
            drawPath(hubPath, color = Color(0x40FFFFFF), style = Stroke(width = 1.dp.toPx()))

            // Subtle horn emblem bar & spoke control pods
            drawLine(
                color = Color(0x55CBD5E1),
                start = Offset(c.x - w * 0.055f, c.y + w * 0.01f),
                end = Offset(c.x + w * 0.055f, c.y + w * 0.01f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawRoundRect(
                color = Color(0xFF111419),
                topLeft = Offset(c.x - w * 0.24f, c.y - w * 0.032f),
                size = Size(w * 0.085f, w * 0.070f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = Color(0xFF111419),
                topLeft = Offset(c.x + w * 0.155f, c.y - w * 0.032f),
                size = Size(w * 0.085f, w * 0.070f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            for (dy in floatArrayOf(-0.014f, 0.014f)) {
                drawCircle(Color(0x99FFFFFF), radius = 1.1.dp.toPx(), center = Offset(c.x - w * 0.198f, c.y + w * dy))
                drawCircle(Color(0x99FFFFFF), radius = 1.1.dp.toPx(), center = Offset(c.x + w * 0.198f, c.y + w * dy))
            }
        }
    }
}

// ==========================================
// REALISTIC BRUSHED METAL BRAKE & GAS PEDALS
// ==========================================

@Composable
internal fun RealisticMetalPedal(
    isGasPedal: Boolean,
    active: Boolean,
    width: Dp,
    height: Dp,
    testTag: String,
    onPressChanged: (Boolean) -> Unit
) {
    val pressScale by animateFloatAsState(
        targetValue = if (active) 0.94f else 1.0f,
        animationSpec = spring(stiffness = 700f),
        label = "pedal_press"
    )

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .scale(pressScale)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onPressChanged(true)
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        event.changes.forEach { it.consume() }
                        if (!anyPressed) break
                    }
                    onPressChanged(false)
                }
            }
            .semantics { contentDescription = if (isGasPedal) "Accelerator Pedal" else "Brake Pedal" }
            .testTag(testTag)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (!isGasPedal) {
                // BRAKE PEDAL: Dark rubber pad base + brushed silver face with 4 horizontal dark grip bars
                drawRoundRect(
                    color = Color(0xCC1E232B),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                drawRoundRect(
                    color = if (active) ActiveOrangeStart else Color(0x885C6776),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )

                val padM = w * 0.08f
                val plateBrush = if (active) {
                    Brush.verticalGradient(listOf(Color(0xFFBCC6D4), Color(0xFF8390A1)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFE5EAF0), Color(0xFFA7B1BE), Color(0xFFCAD2DC)))
                }
                drawRoundRect(
                    brush = plateBrush,
                    topLeft = Offset(padM, padM),
                    size = Size(w - padM * 2f, h - padM * 2f),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // 4 Horizontal Dark Rubber Grip Bars
                val barW = w * 0.62f
                val barH = h * 0.078f
                val barX = (w - barW) * 0.5f
                for (i in 0 until 4) {
                    val y = h * (0.20f + i * 0.175f)
                    drawRoundRect(
                        color = Color(0xFF2B313B),
                        topLeft = Offset(barX, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barH * 0.5f, barH * 0.5f)
                    )
                }
            } else {
                // GAS PEDAL: Organ-style taller brushed metallic pedal with 5 grip bars + 3 bottom dots
                drawRoundRect(
                    color = Color(0xCC1E232B),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                drawRoundRect(
                    color = if (active) Color(0xFF34D399) else Color(0x885C6776),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )

                val insetXTop = w * 0.10f
                val insetXBot = w * 0.14f
                val insetY = h * 0.045f
                val platePath = Path().apply {
                    moveTo(insetXTop + 6.dp.toPx(), insetY)
                    lineTo(w - insetXTop - 6.dp.toPx(), insetY)
                    quadraticTo(w - insetXTop, insetY, w - insetXTop, insetY + 6.dp.toPx())
                    lineTo(w - insetXBot, h - insetY - 6.dp.toPx())
                    quadraticTo(w - insetXBot, h - insetY, w - insetXBot - 6.dp.toPx(), h - insetY)
                    lineTo(insetXBot + 6.dp.toPx(), h - insetY)
                    quadraticTo(insetXBot, h - insetY, insetXBot, h - insetY - 6.dp.toPx())
                    lineTo(insetXTop, insetY + 6.dp.toPx())
                    quadraticTo(insetXTop, insetY, insetXTop + 6.dp.toPx(), insetY)
                    close()
                }
                val plateBrush = if (active) {
                    Brush.verticalGradient(listOf(Color(0xFFB7C4D3), Color(0xFF7D8A9C)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFFE8EDF3), Color(0xFFA7B2C0), Color(0xFFD2DAE3)))
                }
                drawPath(platePath, brush = plateBrush)

                // 5 Horizontal Dark Rubber Grip Bars
                val barW = w * 0.56f
                val barH = h * 0.046f
                val barX = (w - barW) * 0.5f
                for (i in 0 until 5) {
                    val y = h * (0.14f + i * 0.115f)
                    drawRoundRect(
                        color = Color(0xFF2B313B),
                        topLeft = Offset(barX, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barH * 0.5f, barH * 0.5f)
                    )
                }

                // 3 Bottom Pivot / Grip Dots matching real organ pedal
                drawCircle(Color(0xFF2B313B), radius = 1.8.dp.toPx(), center = Offset(w * 0.36f, h * 0.80f))
                drawCircle(Color(0xFF2B313B), radius = 1.8.dp.toPx(), center = Offset(w * 0.62f, h * 0.82f))
                drawCircle(Color(0xFF2B313B), radius = 1.8.dp.toPx(), center = Offset(w * 0.42f, h * 0.88f))
            }
        }
    }
}

// ==========================================
// RIGHT-EDGE PARK BRAKE + D / R GEAR SHIFTER
// ==========================================

@Composable
internal fun RightGearShifterPill(
    selectedGear: TransmissionGear,
    width: Dp,
    height: Dp,
    onSelectGear: (TransmissionGear) -> Unit
) {
    Column(
        modifier = Modifier
            .size(width = width, height = height)
            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(GlassTopColor, GlassBottomColor)))
            .border(1.1.dp, GlassBorderColor, RoundedCornerShape(12.dp))
            .padding(5.dp)
            .testTag("gear_selector_panel"),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isDrive = selectedGear == TransmissionGear.Drive || selectedGear == TransmissionGear.Park
        val isReverse = selectedGear == TransmissionGear.Reverse

        GearCell(
            code = "D",
            selected = isDrive,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("gear_button_d"),
            onClick = { onSelectGear(TransmissionGear.Drive) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        GearCell(
            code = "R",
            selected = isReverse,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("gear_button_r"),
            onClick = { onSelectGear(TransmissionGear.Reverse) }
        )
    }
}

@Composable
private fun GearCell(
    code: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(ActiveOrangeStart, ActiveOrangeEnd))
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = code,
            color = if (selected) Color(0xFF151921) else Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// ==========================================
// TOP-RIGHT "KERALA" GPS MINIMAP
// ==========================================

@Composable
internal fun KeralaGpsMinimap(
    stateProvider: () -> SimUiState,
    width: Dp,
    height: Dp,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onTapMap: () -> Unit
) {
    val roadPath = remember { Path() }
    val activePath = remember { Path() }

    Column(
        modifier = Modifier
            .size(width = width, height = height)
            .shadow(4.dp, RoundedCornerShape(13.dp), clip = false)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xE6141C28))
            .border(1.1.dp, GlassBorderColor, RoundedCornerShape(13.dp))
            .testTag("radar_minimap_box")
    ) {
        // Header bar: Pin icon + "Kerala"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color(0xEB232C3A))
                .clickable(onClick = onTapMap)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(13.dp)) {
                val w = size.width
                val h = size.height
                val pinPath = Path().apply {
                    moveTo(w * 0.5f, h * 0.94f)
                    cubicTo(w * 0.14f, h * 0.56f, w * 0.12f, h * 0.12f, w * 0.5f, h * 0.10f)
                    cubicTo(w * 0.88f, h * 0.12f, w * 0.86f, h * 0.56f, w * 0.5f, h * 0.94f)
                    close()
                }
                drawPath(pinPath, color = Color.White)
                drawCircle(color = Color(0xFF232C3A), radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.39f))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "Kerala",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(13.dp))
        }

        // Map viewport + +/- buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onTapMap)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val state = stateProvider()
                val w = size.width
                val h = size.height
                val cx = w * 0.50f
                val cy = h * 0.68f
                val mapScale = 0.72f * density * state.minimapZoom
                val bx = state.busPos.x
                val bz = state.busPos.z
                val cosH = cos(-state.busHeadingRad)
                val sinH = sin(-state.busHeadingRad)

                roadPath.reset()
                activePath.reset()

                for (rIdx in state.roads.indices) {
                    val road = state.roads[rIdx]
                    val pts = road.points
                    if (pts.size < 2) continue
                    val targetPath = if (rIdx == state.activeRoadIndex) activePath else roadPath
                    val step = if (pts.size > 40) 3 else 1

                    fun projectPt(wx: Float, wz: Float): Offset {
                        val dx = wx - bx
                        val dz = wz - bz
                        val rx = dx * cosH - dz * sinH
                        val rz = dx * sinH + dz * cosH
                        return Offset(cx + rx * mapScale, cy - rz * mapScale)
                    }

                    var prev = projectPt(pts[0].x, pts[0].z)
                    var i = step
                    while (i < pts.size) {
                        val cur = projectPt(pts[i].x, pts[i].z)
                        if ((prev.x in -80f..(w + 80f) && prev.y in -80f..(h + 80f)) ||
                            (cur.x in -80f..(w + 80f) && cur.y in -80f..(h + 80f))
                        ) {
                            targetPath.moveTo(prev.x, prev.y)
                            targetPath.lineTo(cur.x, cur.y)
                        }
                        prev = cur
                        i = if (i != pts.lastIndex && i + step >= pts.size) pts.lastIndex else i + step
                    }
                }

                // Glowing blue OSM road strokes matching the reference minimap
                drawPath(
                    path = roadPath,
                    color = Color(0xFF3B76B8),
                    style = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = activePath,
                    color = Color(0xFF5FA8F5),
                    style = Stroke(width = 6.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Red 3D faceted navigation arrowhead pointing straight forward
                val tipY = cy - 15.dp.toPx()
                val baseY = cy + 9.dp.toPx()
                val notchY = cy + 3.dp.toPx()
                val halfW = 9.5.dp.toPx()

                val leftFacet = Path().apply {
                    moveTo(cx, tipY)
                    lineTo(cx - halfW, baseY)
                    lineTo(cx, notchY)
                    close()
                }
                val rightFacet = Path().apply {
                    moveTo(cx, tipY)
                    lineTo(cx, notchY)
                    lineTo(cx + halfW, baseY)
                    close()
                }
                drawPath(leftFacet, color = Color(0xFFD62828))
                drawPath(rightFacet, color = Color(0xFFFF4D4D))
            }

            // Stacked + and - circular zoom buttons on right edge
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xA62D3746))
                        .border(0.8.dp, Color(0x66FFFFFF), CircleShape)
                        .clickable { onZoomIn() }
                        .testTag("minimap_zoom_in_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xA62D3746))
                        .border(0.8.dp, Color(0x66FFFFFF), CircleShape)
                        .clickable { onZoomOut() }
                        .testTag("minimap_zoom_out_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// BOTTOM-CENTER SPEEDOMETER & FUEL PILL
// ==========================================

@Composable
internal fun BottomSpeedFuelBar(
    speedKmh: Float,
    rpm: Int,
    gear: TransmissionGear,
    isParkBrakeActive: Boolean,
    displayGearNumber: Int,
    fuelLiters: Float,
    modifier: Modifier = Modifier
) {
    val fuelFrac = (fuelLiters / 100f).coerceIn(0.05f, 1f)
    val speedInt = speedKmh.roundToInt().coerceIn(0, 180)
    val gearText = when {
        isParkBrakeActive || gear == TransmissionGear.Park -> "GEAR P"
        gear == TransmissionGear.Reverse -> "GEAR R"
        gear == TransmissionGear.Neutral -> "GEAR N"
        else -> "GEAR ${displayGearNumber.coerceIn(1, 6)}"
    }

    Row(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(GlassTopColor, GlassBottomColor)))
            .border(1.0.dp, GlassBorderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp)
            .testTag("speedometer_cluster"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Speed (km/h) first
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Canvas(modifier = Modifier.size(15.dp)) {
                drawSpeedometerDialIcon(Color.White)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = speedInt.toString(),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("speed_value_text")
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "km/h",
                    color = Color(0xFFE2E8F0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        // Then RPM
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "$rpm",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("rpm_value_text")
            )
            Text(
                text = "RPM",
                color = Color(0xFF94A3B8),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Compact Gear Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0x660F172A))
                .border(0.8.dp, ActiveOrangeStart.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("gear_indication_text"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = gearText,
                color = ActiveOrangeStart,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Compact Fuel Bar + Liters
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Canvas(modifier = Modifier.size(13.dp)) {
                drawFuelPumpIcon(Color.White)
            }
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF4B5563))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fuelFrac)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFD97B18))
                )
            }
            Text(
                text = "${fuelLiters.roundToInt()} L",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
