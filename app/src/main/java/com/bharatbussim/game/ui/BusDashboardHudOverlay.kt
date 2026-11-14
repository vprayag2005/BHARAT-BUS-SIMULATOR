package com.bharatbussim.game.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.ui.theme.HudAmber
import com.bharatbussim.game.ui.theme.HudCyan
import com.bharatbussim.game.ui.theme.HudEmerald
import com.bharatbussim.game.ui.theme.HudPanelSurface
import com.bharatbussim.game.ui.theme.HudRed
import com.bharatbussim.game.ui.theme.HudTextMuted
import com.bharatbussim.game.ui.theme.HudTextPrimary
import com.bharatbussim.game.viewmodel.SimUiState
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun BusDashboardHudOverlay(
    state: SimUiState,
    viewModel: ThrissurSimViewModel,
    modifier: Modifier = Modifier
) {
    if (state.showRouteModal) {
        BackHandler {
            viewModel.setRouteModalVisible(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ==========================================
        // TOP ROW: VEHICLE SYSTEMS & RADAR MINIMAP (Clean 3D Center View — No Status Text Clutter, No Rear Button)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Top Left: Route Picker, Cruise Assist, Headlights, Reset & Hazards
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudActionSquareButton(
                    icon = Icons.Default.Map,
                    label = "ROUTES",
                    active = state.showRouteModal,
                    activeColor = HudAmber,
                    testTag = "open_routes_button",
                    onClick = { viewModel.setRouteModalVisible(true) }
                )

                HudActionSquareButton(
                    icon = Icons.Default.Navigation,
                    label = if (state.autoSteerAssist) "AUTO ON" else "CRUISE",
                    active = state.autoSteerAssist,
                    activeColor = HudEmerald,
                    testTag = "cruise_assist_button",
                    onClick = { viewModel.toggleAutoPilot() }
                )

                HudActionSquareButton(
                    icon = Icons.Default.Highlight,
                    label = "BEAM",
                    active = state.headlightsOn,
                    activeColor = HudAmber,
                    testTag = "beam_toggle_button",
                    onClick = { viewModel.toggleHeadlights() }
                )

                HudActionSquareButton(
                    icon = Icons.Default.Refresh,
                    label = "STAND",
                    active = false,
                    activeColor = HudCyan,
                    testTag = "reset_bus_button",
                    onClick = { viewModel.resetBusToActiveCorridorStart() }
                )

                HudActionSquareButton(
                    icon = Icons.Default.Warning,
                    label = "HAZARD",
                    active = state.hazardsOn,
                    activeColor = HudRed,
                    testTag = "hazard_toggle_button",
                    onClick = { viewModel.toggleHazards() }
                )
            }

            // Top Right: Camera Cycle Button + Live OSM Radar Minimap (No Rear View Button)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                HudActionSquareButton(
                    icon = Icons.Default.Cameraswitch,
                    label = "CAM ${state.cameraMode.ordinal + 1}/4",
                    active = true,
                    activeColor = HudAmber,
                    testTag = "camera_cycle_button",
                    onClick = { viewModel.cycleCameraMode() }
                )

                ThrissurRadarMinimap(
                    state = state,
                    onZoomIn = { viewModel.adjustMinimapZoom(0.35f) },
                    onZoomOut = { viewModel.adjustMinimapZoom(-0.35f) },
                    onTapMap = { viewModel.setRouteModalVisible(true) }
                )
            }
        }

        // ==========================================
        // BOTTOM ROW: CLEAN POWER STEERING WHEEL, CONSOLE & UNOBSTRUCTED PEDALS
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Bottom Left: Clean Commercial Coach Power Steering Wheel (No Dots, No Angle Badge)
            VirtualCoachSteeringWheel(
                wheelAngleDeg = state.steeringWheelAngleDeg,
                onSteerDeltaDeg = { viewModel.steerByDeltaDeg(it) },
                onSteerRelease = { viewModel.releaseSteeringWheel() },
                onHornClick = { viewModel.triggerAirHorn() }
            )

            // Bottom Center: Turn Signals + Gear Selector (P, R, N, D) + Park Brake + Digital Speedometer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Left Blinker Button
                    val leftActive = state.turnLeftOn && state.blinkerPhase
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (leftActive) HudAmber.copy(alpha = 0.32f) else HudPanelSurface)
                            .border(
                                1.5.dp,
                                if (state.turnLeftOn) HudAmber else Color(0x26FFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.toggleTurnLeft() }
                            .testTag("turn_left_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "◀",
                            color = if (state.turnLeftOn) HudAmber else HudTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Gear Selector Panel (P, R, N, D)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(HudPanelSurface)
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("gear_selector_panel"),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        TransmissionGear.entries.forEach { g ->
                            val selected = state.gear == g
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) {
                                            Brush.verticalGradient(listOf(HudAmber, Color(0xFFD97706)))
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF1E2633), Color(0xFF121822))
                                            )
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) Color(0xFFFDE68A) else Color(0x22FFFFFF),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectGear(g) }
                                    .testTag("gear_button_${g.code.lowercase(Locale.ROOT)}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = g.code,
                                    color = if (selected) Color(0xFF0F172A) else HudTextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    // Dedicated Pneumatic Park Brake Button (Inline in Center Console)
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (state.isParkBrakeActive) HudRed.copy(alpha = 0.28f) else HudPanelSurface
                            )
                            .border(
                                1.5.dp,
                                if (state.isParkBrakeActive) HudRed else Color(0x45EF4444),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.toggleParkBrake() }
                            .testTag("park_brake_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(1.8.dp, HudRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P",
                                    color = HudRed,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = if (state.isParkBrakeActive) "LOCKED" else "PARK",
                                color = if (state.isParkBrakeActive) HudRed else HudTextMuted,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Right Blinker Button
                    val rightActive = state.turnRightOn && state.blinkerPhase
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (rightActive) HudAmber.copy(alpha = 0.32f) else HudPanelSurface)
                            .border(
                                1.5.dp,
                                if (state.turnRightOn) HudAmber else Color(0x26FFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.toggleTurnRight() }
                            .testTag("turn_right_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            color = if (state.turnRightOn) HudAmber else HudTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Speedometer & Telemetry Cluster
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HudPanelSurface)
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .testTag("speedometer_cluster"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Diesel Fuel (Hino RM 2 200L Tank)
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = "HINO RM 2",
                            color = HudAmber,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "84%",
                            color = HudTextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "168 L / 200L",
                            color = HudEmerald,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Digital Speed Display
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%03d", state.speedKmh.roundToInt()),
                                color = HudTextPrimary,
                                fontSize = 30.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("speed_value_text")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "KM/H",
                                color = HudAmber,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                        ) {
                            val fillFrac = (state.speedKmh / 75f).coerceIn(0.04f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fillFrac)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(HudAmber)
                            )
                        }
                    }

                    // Air Brake Pressure (Hino RM 2 S-Cam Full Air Brakes)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "AIR BRAKE",
                            color = HudTextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "8.8 BAR",
                            color = HudTextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (state.isParkBrakeActive) "PARK LOCKED" else "OPTIMAL",
                            color = if (state.isParkBrakeActive) HudRed else HudEmerald,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Bottom Right: Unobstructed Brake & Gas Pedals
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                PedalControl(
                    label = "BRAKE",
                    subLabel = "RETARDER",
                    active = state.brakeInput > 0.05f,
                    accentColor = HudRed,
                    heightDp = 132,
                    widthDp = 68,
                    testTag = "brake_pedal_button",
                    onPressChanged = { viewModel.setBrake(it) }
                )

                PedalControl(
                    label = "GAS",
                    subLabel = "THROTTLE",
                    active = state.throttleInput > 0.05f,
                    accentColor = HudEmerald,
                    heightDp = 148,
                    widthDp = 68,
                    testTag = "gas_pedal_button",
                    onPressChanged = { viewModel.setThrottle(it) }
                )
            }
        }

        // ==========================================
        // MODAL: THRISSUR REALISTIC OSM & BLENDER ROUTES INSPECTOR
        // ==========================================
        if (state.showRouteModal) {
            ThrissurRoutesInspectorModal(
                state = state,
                onSelectRoute = { viewModel.teleportToRoad(it) },
                onSelectLandmark = { viewModel.teleportToLandmark(it) },
                onClose = { viewModel.setRouteModalVisible(false) }
            )
        }
    }
}

@Composable
private fun HudActionSquareButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) activeColor.copy(alpha = 0.22f) else HudPanelSurface)
            .border(
                1.dp,
                if (active) activeColor else Color(0x26FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) activeColor else HudTextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (active) activeColor else HudTextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ThrissurRadarMinimap(
    state: SimUiState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onTapMap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 196.dp, height = 108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0C131D))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable { onTapMap() }
            .testTag("radar_minimap_box")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val mapScale = 0.16f * state.minimapZoom

            fun toRadar(wx: Float, wz: Float): Offset {
                val dx = (wx - state.busPos.x) * mapScale
                val dz = (wz - state.busPos.z) * mapScale
                return Offset(cx + dx, cy - dz)
            }

            for ((rIdx, road) in state.roads.withIndex()) {
                val pts = road.points
                if (pts.size < 2) continue
                val isSelected = rIdx == state.activeRoadIndex
                val path = Path()
                val p0 = toRadar(pts[0].x, pts[0].z)
                path.moveTo(p0.x, p0.y)
                for (i in 1 until pts.size) {
                    val p = toRadar(pts[i].x, pts[i].z)
                    path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = if (isSelected) HudCyan else Color(0xFF334155),
                    style = Stroke(width = if (isSelected) 4.5f else 2.2f, cap = StrokeCap.Round)
                )
            }

            drawCircle(
                color = HudCyan.copy(alpha = 0.28f),
                radius = 12f,
                center = Offset(cx, cy)
            )
            val headX = sin(state.busHeadingRad) * 9f
            val headY = -cos(state.busHeadingRad) * 9f
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = Offset(cx + headX, cy + headY),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = HudAmber,
                radius = 4.5f,
                center = Offset(cx, cy)
            )
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudPanelSurface)
                    .clickable { onZoomIn() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = HudTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(HudPanelSurface)
                    .clickable { onZoomOut() },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = HudTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Clean Commercial Coach Hydraulic Power Steering Wheel:
 * No dots on the rim and no angle text overlay.
 * Supports smooth rotary turning and horizontal drag with automatic hydraulic return.
 */
@Composable
private fun VirtualCoachSteeringWheel(
    wheelAngleDeg: Float,
    onSteerDeltaDeg: (Float) -> Unit,
    onSteerRelease: () -> Unit,
    onHornClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(152.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.5f
                    var prevPos = down.position

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val currPos = change.position
                        val drag = change.positionChange()
                        if (drag != Offset.Zero) {
                            change.consume()
                            val distFromHub = hypot(currPos.x - cx, currPos.y - cy)

                            val a0 = atan2(prevPos.y - cy, prevPos.x - cx)
                            val a1 = atan2(currPos.y - cy, currPos.x - cx)
                            var dRad = a1 - a0
                            while (dRad > PI.toFloat()) dRad -= (2.0 * PI).toFloat()
                            while (dRad < -PI.toFloat()) dRad += (2.0 * PI).toFloat()
                            val rotaryDeg = Math.toDegrees(dRad.toDouble()).toFloat() * 1.45f

                            val horizDeg = (drag.x / (size.width * 0.36f)) * 150f

                            val deltaDeg = if (distFromHub > size.width * 0.22f && currPos.y > cy + size.height * 0.14f) {
                                rotaryDeg
                            } else if (abs(horizDeg) > abs(rotaryDeg)) {
                                horizDeg
                            } else {
                                rotaryDeg
                            }
                            onSteerDeltaDeg(deltaDeg)
                            prevPos = currPos
                        }
                    }
                    onSteerRelease()
                }
            }
            .testTag("virtual_steering_wheel"),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Rim & 3-Spoke Bars (Clean — No Dots on Rim)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(wheelAngleDeg)
        ) {
            val r = size.minDimension * 0.43f
            val center = Offset(size.width * 0.5f, size.height * 0.5f)

            drawCircle(
                color = Color(0xFF141A24),
                radius = r,
                center = center,
                style = Stroke(width = 18.dp.toPx())
            )
            drawCircle(
                color = Color(0x38FFFFFF),
                radius = r,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            drawLine(
                color = Color(0xFF253042),
                start = Offset(center.x - r, center.y),
                end = Offset(center.x + r, center.y),
                strokeWidth = 13.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF253042),
                start = center,
                end = Offset(center.x, center.y + r),
                strokeWidth = 13.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Center Pneumatic Air-Horn Hub
        Column(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(HudPanelSurface)
                .border(1.5.dp, HudAmber.copy(alpha = 0.65f), CircleShape)
                .clickable { onHornClick() }
                .testTag("horn_center_button"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Air Horn",
                tint = HudAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "HORN",
                color = HudTextPrimary,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PedalControl(
    label: String,
    subLabel: String,
    active: Boolean,
    accentColor: Color,
    heightDp: Int,
    widthDp: Int,
    testTag: String,
    onPressChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .size(width = widthDp.dp, height = heightDp.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (active) accentColor.copy(alpha = 0.30f) else HudPanelSurface
            )
            .border(
                1.5.dp,
                if (active) accentColor else accentColor.copy(alpha = 0.42f),
                RoundedCornerShape(18.dp)
            )
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
            .padding(vertical = 10.dp, horizontal = 8.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f))
                .border(1.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (label == "GAS") "▲" else "■",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(if (active) accentColor.copy(alpha = 0.55f) else Color(0xFF2A3444))
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = subLabel,
                color = HudTextMuted,
                fontSize = 7.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ThrissurRoutesInspectorModal(
    state: SimUiState,
    onSelectRoute: (Int) -> Unit,
    onSelectLandmark: (String) -> Unit,
    onClose: () -> Unit
) {
    val totalVerts = state.roads.sumOf { it.blenderVertCount }
    val totalQuads = state.roads.sumOf { it.blenderQuadCount }
    val totalKm = state.roads.sumOf { it.realDistanceM.toDouble() } / 1000.0

    val landmarkPresets = listOf(
        Triple("SAKTHAN_STAND", "Sakthan Stand", "20 Bays • Entry/Exit"),
        Triple("VADAKKE_STAND", "Vadakke Stand", "8 Bays • Entry/Exit"),
        Triple("SWARAJ_ROUND", "Swaraj Round", "Vadakkumnathan Temple"),
        Triple("PALIYEKKARA_TOLL", "Paliyekkara Toll", "NH 544 FASTag Plaza"),
        Triple("GURUVAYUR_TEMPLE", "Guruvayur Temple", "East Nada & Flagstaff"),
        Triple("GURUVAYUR_STAND", "Guruvayur Stand", "10 Pilgrim Bays"),
        Triple("CHAVAKKAD_BEACH", "Chavakkad Beach", "Azhi & Lighthouse"),
        Triple("TRIPRAYAR_BEACH", "Triprayar Beach", "Stand & Shoreline")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC06090F))
            .clickable { onClose() }
            .padding(16.dp)
            .testTag("thrissur_routes_modal"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF111823))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "THRISSUR REALISTIC OSM ROADS & 6 BLENDER 3D LANDMARKS",
                        color = HudAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%d Aligned Corridors • 6 3D Landmarks • %.1f km Total • %,d Verts / %,d Quads • 0 Trees",
                            state.roads.size,
                            totalKm,
                            totalVerts,
                            totalQuads
                        ),
                        color = HudEmerald,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(HudRed.copy(alpha = 0.2f))
                        .border(1.dp, HudRed, RoundedCornerShape(10.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("close_routes_modal_button")
                ) {
                    Text("CLOSE", color = HudRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 6 Road-Aligned 3D Blender Landmarks Quick-Teleport Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for ((lmId, lmTitle, lmSub) in landmarkPresets) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A2639))
                            .border(1.dp, HudAmber.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                            .clickable { onSelectLandmark(lmId) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("landmark_btn_${lmId.lowercase(Locale.ROOT)}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = lmTitle,
                            color = HudAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = lmSub,
                            color = HudTextPrimary,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Active 3D Bus Asset & CGTrader Royalty Free License (No AI) Compliance Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF172336))
                    .border(1.dp, HudCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("hino_rm2_license_attribution_card"),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE COACH: ${state.vehicleModelName.uppercase(Locale.ROOT)}",
                        color = HudCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = state.vehicleLicense,
                        color = HudAmber,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "3D Model Creator: ${state.vehicleCreator} • Source: ${state.vehicleSourceUrl}",
                    color = HudTextPrimary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.roads) { idx, road ->
                    val isCurrent = idx == state.activeRoadIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) HudAmber.copy(alpha = 0.16f) else Color(0xFF182232))
                            .border(
                                1.dp,
                                if (isCurrent) HudAmber else Color(0x22FFFFFF),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectRoute(idx) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("route_item_${road.id.lowercase(Locale.ROOT)}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = road.name,
                                    tint = if (isCurrent) HudAmber else HudCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = road.name,
                                    color = HudTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "OSM Streets: ${road.stepNames.take(5).joinToString(" → ").ifEmpty { road.id }}",
                                color = HudTextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.2f km • %dL • %.1fm",
                                    road.realDistanceM / 1000f,
                                    road.lanes,
                                    road.widthM
                                ),
                                color = HudAmber,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isCurrent) "ACTIVE CORRIDOR" else "TAP TO DRIVE",
                                color = if (isCurrent) HudEmerald else HudCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
