package com.bharatbussim.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.HeadlightBeamMode
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.model.WiperSpeedMode
import com.bharatbussim.game.ui.theme.HudAmber
import com.bharatbussim.game.ui.theme.HudCyan
import com.bharatbussim.game.ui.theme.HudEmerald
import com.bharatbussim.game.ui.theme.HudRed
import com.bharatbussim.game.ui.theme.HudTextMuted
import com.bharatbussim.game.ui.theme.HudTextPrimary
import com.bharatbussim.game.ui.theme.JetBrainsMonoFamily
import com.bharatbussim.game.ui.theme.OutfitDisplayFamily
import com.bharatbussim.game.viewmodel.SimUiState
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BusDashboardHudOverlay(
    stateProvider: () -> SimUiState,
    viewModel: ThrissurSimViewModel,
    modifier: Modifier = Modifier
) {
    val showRouteModal by remember { derivedStateOf { stateProvider().showRouteModal } }
    val isPaused by remember { derivedStateOf { stateProvider().isPaused } }
    val hudHidden by remember { derivedStateOf { stateProvider().hudHidden } }
    val cameraMode by remember { derivedStateOf { stateProvider().cameraMode } }
    val hornActive by remember { derivedStateOf { stateProvider().hornActive } }
    val hazardsOn by remember { derivedStateOf { stateProvider().hazardsOn } }
    val turnLeftOn by remember { derivedStateOf { stateProvider().turnLeftOn } }
    val turnRightOn by remember { derivedStateOf { stateProvider().turnRightOn } }
    val blinkerPhase by remember { derivedStateOf { stateProvider().blinkerPhase } }
    val wiperMode by remember { derivedStateOf { stateProvider().wiperMode } }
    val headlightMode by remember { derivedStateOf { stateProvider().headlightMode } }
    val doorsOpen by remember { derivedStateOf { stateProvider().doorsOpen } }
    val isEngineCranking by remember { derivedStateOf { stateProvider().isEngineCranking } }
    val engineRunning by remember { derivedStateOf { stateProvider().engineRunning } }
    val isParkBrakeActive by remember { derivedStateOf { stateProvider().isParkBrakeActive } }
    val gear by remember { derivedStateOf { stateProvider().gear } }
    val brakeActive by remember { derivedStateOf { stateProvider().brakeInput > 0.05f } }
    val throttleActive by remember { derivedStateOf { stateProvider().throttleInput > 0.05f } }
    val engineStartPromptVisible by remember { derivedStateOf { stateProvider().engineStartPromptVisible } }

    if (showRouteModal) {
        BackHandler {
            viewModel.setRouteModalVisible(false)
        }
    } else if (isPaused) {
        BackHandler {
            viewModel.setPaused(false)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Adaptive vertical scale so everything fits crisply from 340dp phones to large tablets
        val availW = maxWidth
        val availH = maxHeight
        val scale = (availH / 410.dp).coerceIn(0.80f, 1.15f)
        val isCockpit = cameraMode == CameraViewMode.Cockpit

        val topBtnSize = (42.dp * scale)
        val gridBtnW = (46.dp * scale)
        val gridBtnH = (36.dp * scale)
        val gridGap = (4.5.dp * scale)
        val wheelSize = (196.dp * scale)

        val mapW = (176.dp * scale)
        val mapH = (98.dp * scale)

        val parkBtnW = (54.dp * scale)
        val parkBtnH = (46.dp * scale)
        val shifterW = (54.dp * scale)
        val shifterH = (92.dp * scale)

        val brakeW = (76.dp * scale)
        val brakeH = (86.dp * scale)
        val gasW = (62.dp * scale)
        val gasH = (122.dp * scale)

        if (hudHidden) {
            // Minimal restore button in top-right when HUD is hidden
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .alpha(0.55f)
            ) {
                HudGlassSquareButton(
                    width = topBtnSize,
                    height = topBtnSize,
                    cornerRadius = 11.dp,
                    active = false,
                    contentDesc = "Show Controls",
                    testTag = "toggle_hud_button",
                    onClick = { viewModel.toggleHudHidden() }
                ) { tint -> drawEyeSlashIcon(tint) }
            }
        } else {
            // ==========================================
            // LEFT SIDE: NARROW EDGE STRIP (PAUSE, HORN, CAMERA, HAZARD + INDICATORS)
            // Leaves the Left Exterior Mirror (0.088w .. 0.168w) 100% Clear!
            // ==========================================
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(gridGap),
                horizontalAlignment = Alignment.Start
            ) {
                // Single narrow vertical column on far-left edge: Pause, Horn, Camera, Hazard
                HudGlassSquareButton(
                    width = gridBtnW,
                    height = gridBtnH,
                    cornerRadius = 10.dp,
                    active = isPaused,
                    contentDesc = "Pause Menu",
                    testTag = "pause_button",
                    onClick = { viewModel.togglePause() }
                ) { tint -> drawPauseIcon(tint) }

                HudGlassSquareButton(
                    width = gridBtnW,
                    height = gridBtnH,
                    cornerRadius = 10.dp,
                    active = hornActive,
                    activeTint = ActiveOrangeStart,
                    contentDesc = "Air Horn",
                    testTag = "horn_button",
                    onClick = { viewModel.triggerAirHorn() }
                ) { tint -> drawBugleHornIcon(tint) }

                HudGlassSquareButton(
                    width = gridBtnW,
                    height = gridBtnH,
                    cornerRadius = 10.dp,
                    active = false,
                    contentDesc = "Switch Camera",
                    testTag = "camera_cycle_button",
                    onClick = { viewModel.cycleCameraMode() }
                ) { tint -> drawCameraIcon(tint) }

                val hazardLit = hazardsOn && blinkerPhase
                HudGlassSquareButton(
                    width = gridBtnW,
                    height = gridBtnH,
                    cornerRadius = 10.dp,
                    active = hazardsOn,
                    activeTint = if (hazardLit) ActiveRedGlow else ActiveOrangeStart,
                    contentDesc = "Hazard Lights",
                    testTag = "hazard_toggle_button",
                    onClick = { viewModel.toggleHazards() }
                ) { tint -> drawHazardTriangleIcon(tint) }

                // Left & Right Turn Indicators positioned below the Left Mirror height (just above Steering Wheel)
                Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
                    val leftLit = (turnLeftOn || hazardsOn) && blinkerPhase
                    HudGlassSquareButton(
                        width = gridBtnW,
                        height = gridBtnH,
                        cornerRadius = 10.dp,
                        active = leftLit,
                        activeTint = ActiveOrangeStart,
                        contentDesc = "Left Indicator",
                        testTag = "turn_left_button",
                        onClick = { viewModel.toggleTurnLeft() }
                    ) { tint -> drawIndicatorArrowIcon(tint, isLeft = true) }

                    val rightLit = (turnRightOn || hazardsOn) && blinkerPhase
                    HudGlassSquareButton(
                        width = gridBtnW,
                        height = gridBtnH,
                        cornerRadius = 10.dp,
                        active = rightLit,
                        activeTint = ActiveOrangeStart,
                        contentDesc = "Right Indicator",
                        testTag = "turn_right_button",
                        onClick = { viewModel.toggleTurnRight() }
                    ) { tint -> drawIndicatorArrowIcon(tint, isLeft = false) }
                }
            }

            // Bottom-Left: Large Full-Proportion Realistic Bus Steering Wheel
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 2.dp)
            ) {
                SteeringWheelHost(
                    stateProvider = stateProvider,
                    sizeDp = wheelSize,
                    viewModel = viewModel
                )
            }

            // ==========================================
            // BOTTOM-CENTER: SPEEDOMETER, RPM, GEAR & FUEL PILL (ONLY IN EXTERIOR VIEWS)
            // In Cockpit view, Speed & Fuel are integrated directly inside the 3D Instrument Cluster!
            // ==========================================
            if (!isCockpit) {
                BottomSpeedFuelBarHost(
                    stateProvider = stateProvider,
                    gear = gear,
                    isParkBrakeActive = isParkBrakeActive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp)
                )
            } else {
                // Clickable hotspot over the Cockpit Dashboard's built-in GPS Navigation screen (0.284w..0.402w, 0.708h..0.854h)
                Box(
                    modifier = Modifier
                        .padding(start = availW * 0.280f, top = availH * 0.704f)
                        .size(width = availW * 0.124f, height = availH * 0.152f)
                        .clickable { viewModel.setRouteModalVisible(true) }
                        .testTag("radar_minimap_box")
                )
            }

            // ==========================================
            // RIGHT COLUMN: MAP-SIDE AUXILIARY BUTTONS + MINIMAP (EXTERIOR ONLY) | ((P)) + D/R | PEDALS
            // ==========================================
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                // Top-Right: Remaining Auxiliary Buttons (Wiper, Headlights, Door, Hide HUD) + Minimap (when not in Cockpit)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
                    verticalAlignment = Alignment.Top
                ) {
                    // 2x2 Compact Cluster of Remaining Controls moved to the Map's side
                    Column(
                        verticalArrangement = Arrangement.spacedBy(gridGap),
                        horizontalAlignment = Alignment.End
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
                            val wiperTint = when (wiperMode) {
                                WiperSpeedMode.Low -> Color(0xFFFBBF24)
                                WiperSpeedMode.Mid -> ActiveOrangeStart
                                WiperSpeedMode.High -> Color(0xFFFB7185)
                                WiperSpeedMode.Off -> ActiveOrangeStart
                            }
                            HudGlassSquareButton(
                                width = gridBtnW,
                                height = gridBtnH,
                                cornerRadius = 10.dp,
                                active = wiperMode != WiperSpeedMode.Off,
                                activeTint = wiperTint,
                                badgeText = if (wiperMode != WiperSpeedMode.Off) wiperMode.label else null,
                                contentDesc = "Windshield Wipers (${wiperMode.label})",
                                testTag = "wiper_toggle_button",
                                onClick = { viewModel.toggleWipers() }
                            ) { tint -> drawWiperIcon(tint) }

                            val beamTint = when (headlightMode) {
                                HeadlightBeamMode.Low -> Color(0xFF22C55E)
                                HeadlightBeamMode.High -> Color(0xFF38BDF8)
                                HeadlightBeamMode.Off -> ActiveOrangeStart
                            }
                            HudGlassSquareButton(
                                width = gridBtnW,
                                height = gridBtnH,
                                cornerRadius = 10.dp,
                                active = headlightMode != HeadlightBeamMode.Off,
                                activeTint = beamTint,
                                badgeText = if (headlightMode != HeadlightBeamMode.Off) headlightMode.label else null,
                                contentDesc = "Headlights (${headlightMode.label})",
                                testTag = "beam_toggle_button",
                                onClick = { viewModel.toggleHeadlights() }
                            ) { tint ->
                                if (headlightMode == HeadlightBeamMode.High) {
                                    drawHighBeamIcon(tint)
                                } else {
                                    drawLowBeamIcon(tint)
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
                            HudGlassSquareButton(
                                width = gridBtnW,
                                height = gridBtnH,
                                cornerRadius = 10.dp,
                                active = doorsOpen,
                                activeTint = ActiveOrangeStart,
                                contentDesc = "Passenger Door",
                                testTag = "door_toggle_button",
                                onClick = { viewModel.toggleDoors() }
                            ) { tint -> drawSingleDoorIcon(tint) }

                            HudGlassSquareButton(
                                width = gridBtnW,
                                height = gridBtnH,
                                cornerRadius = 10.dp,
                                active = false,
                                contentDesc = "Hide Controls",
                                testTag = "toggle_hud_button",
                                onClick = { viewModel.toggleHudHidden() }
                            ) { tint -> drawEyeSlashIcon(tint) }
                        }
                    }

                    // Floating GPS Minimap is only shown in Exterior (Chase / Orbit) views;
                    // in Cockpit view, the live map is inside the 3D Cockpit Instrument Cluster / Dashboard!
                    if (!isCockpit) {
                        KeralaGpsMinimap(
                            stateProvider = stateProvider,
                            width = mapW,
                            height = mapH,
                            onZoomIn = { viewModel.adjustMinimapZoom(0.35f) },
                            onZoomOut = { viewModel.adjustMinimapZoom(-0.35f) },
                            onTapMap = { viewModel.setRouteModalVisible(true) }
                        )
                    }
                }

                // Mid-Right: Engine Start/Stop + Parking Brake ((P)) + D/R Gear Shifter
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp * scale)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gridGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val engineTint = when {
                            isEngineCranking -> ActiveOrangeStart
                            engineRunning -> Color(0xFF22C55E)
                            else -> ActiveOrangeStart
                        }
                        val engineBadge = when {
                            isEngineCranking -> "CRANK"
                            engineRunning -> "ON"
                            else -> "START"
                        }
                        HudGlassSquareButton(
                            width = parkBtnW,
                            height = parkBtnH,
                            cornerRadius = 11.dp,
                            active = engineRunning || isEngineCranking,
                            activeTint = engineTint,
                            badgeText = engineBadge,
                            contentDesc = if (engineRunning) "Stop Engine" else "Start Engine",
                            testTag = "engine_start_button",
                            onClick = { viewModel.toggleEngineStart() }
                        ) { tint -> drawEngineStartIcon(tint) }

                        HudGlassSquareButton(
                            width = parkBtnW,
                            height = parkBtnH,
                            cornerRadius = 11.dp,
                            active = isParkBrakeActive,
                            activeTint = ActiveRedGlow,
                            contentDesc = "Parking Brake",
                            testTag = "park_brake_button",
                            onClick = { viewModel.toggleParkBrake() }
                        ) { tint -> drawParkBrakeSymbol(tint) }
                    }

                    RightGearShifterPill(
                        selectedGear = gear,
                        width = shifterW,
                        height = shifterH,
                        onSelectGear = { viewModel.selectGear(it) }
                    )
                }

                // Bottom-Right: Realistic Metallic Brake & Gas Pedals
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scale),
                    verticalAlignment = Alignment.Bottom
                ) {
                    RealisticMetalPedal(
                        isGasPedal = false,
                        active = brakeActive,
                        width = brakeW,
                        height = brakeH,
                        testTag = "brake_pedal_button",
                        onPressChanged = { viewModel.setBrake(it) }
                    )

                    RealisticMetalPedal(
                        isGasPedal = true,
                        active = throttleActive,
                        width = gasW,
                        height = gasH,
                        testTag = "gas_pedal_button",
                        onPressChanged = { viewModel.setThrottle(it) }
                    )
                }
            }

            if (engineStartPromptVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xDF181E29))
                        .border(1.2.dp, ActiveOrangeStart, RoundedCornerShape(999.dp))
                        .clickable { viewModel.toggleEngineStart() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("engine_start_prompt_pill")
                ) {
                    Text(
                        text = "ENGINE OFF • TAP START BUTTON",
                        color = ActiveOrangeStart,
                        fontFamily = OutfitDisplayFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        // ==========================================
        // MODAL: PAUSE & QUICK ACTIONS MENU
        // ==========================================
        if (isPaused && !showRouteModal) {
            PauseMenuModal(
                state = stateProvider(),
                onResume = { viewModel.setPaused(false) },
                onOpenRoutes = {
                    viewModel.setPaused(false)
                    viewModel.setRouteModalVisible(true)
                },
                onToggleCruise = { viewModel.toggleAutoPilot() },
                onToggleShowcase = { viewModel.toggleFrontShowcaseView() },
                onResetStand = {
                    viewModel.resetBusToActiveCorridorStart()
                    viewModel.setPaused(false)
                }
            )
        }

        // ==========================================
        // MODAL: THRISSUR REALISTIC OSM & BLENDER ROUTES INSPECTOR
        // ==========================================
        if (showRouteModal) {
            ThrissurRoutesInspectorModal(
                state = stateProvider(),
                onSelectRoute = { viewModel.teleportToRoad(it) },
                onSelectLandmark = { viewModel.teleportToLandmark(it) },
                onClose = { viewModel.setRouteModalVisible(false) }
            )
        }
    }
}

@Composable
private fun SteeringWheelHost(
    stateProvider: () -> SimUiState,
    sizeDp: androidx.compose.ui.unit.Dp,
    viewModel: ThrissurSimViewModel
) {
    val quantizedWheelDeg by remember {
        derivedStateOf { (stateProvider().steeringWheelAngleDeg * 2f).roundToInt() * 0.5f }
    }
    RealisticSteeringWheel(
        wheelAngleProvider = { quantizedWheelDeg },
        sizeDp = sizeDp,
        onSteerGrab = { viewModel.grabSteeringWheel() },
        onSteerDeltaDeg = { viewModel.steerByDeltaDeg(it) },
        onSteerRelease = { viewModel.releaseSteeringWheel() },
        onHornClick = { viewModel.triggerSteeringHorn() }
    )
}

@Composable
private fun BottomSpeedFuelBarHost(
    stateProvider: () -> SimUiState,
    gear: TransmissionGear,
    isParkBrakeActive: Boolean,
    modifier: Modifier = Modifier
) {
    val speedInt by remember { derivedStateOf { stateProvider().speedKmh.roundToInt().coerceIn(0, 180) } }
    val rpmBucket by remember { derivedStateOf { (stateProvider().engineRpm / 25) * 25 } }
    val displayGearNumber by remember { derivedStateOf { stateProvider().displayGearNumber } }
    val fuelInt by remember { derivedStateOf { stateProvider().fuelLiters.roundToInt() } }

    BottomSpeedFuelBar(
        speedKmh = speedInt.toFloat(),
        rpm = rpmBucket,
        gear = gear,
        isParkBrakeActive = isParkBrakeActive,
        displayGearNumber = displayGearNumber,
        fuelLiters = fuelInt.toFloat(),
        modifier = modifier
    )
}

@Composable
private fun PauseMenuModal(
    state: SimUiState,
    onResume: () -> Unit,
    onOpenRoutes: () -> Unit,
    onToggleCruise: () -> Unit,
    onToggleShowcase: () -> Unit,
    onResetStand: () -> Unit
) {
    val isFrontShowcase = abs(state.cameraOrbitYawDeg) > 95f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB806090F))
            .clickable { onResume() }
            .testTag("pause_menu_modal"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF0252E3C), Color(0xF5151B24))))
                .border(1.2.dp, GlassBorderColor, RoundedCornerShape(18.dp))
                .clickable(enabled = false) {}
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PAUSED",
                color = Color.White,
                fontFamily = OutfitDisplayFamily,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.0.sp
            )
            Text(
                text = state.currentRoadStreetName,
                color = HudTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            PauseMenuButton(
                label = "RESUME DRIVING",
                accent = ActiveOrangeStart,
                filled = true,
                testTag = "pause_resume_button",
                onClick = onResume
            )
            PauseMenuButton(
                label = "THRISSUR ROUTES & LANDMARKS",
                accent = HudCyan,
                filled = false,
                testTag = "open_routes_button",
                onClick = onOpenRoutes
            )
            PauseMenuButton(
                label = if (state.autoSteerAssist) "CRUISE ASSIST: ON" else "CRUISE ASSIST: OFF",
                accent = if (state.autoSteerAssist) HudEmerald else Color.White,
                filled = false,
                testTag = "cruise_assist_button",
                onClick = onToggleCruise
            )
            PauseMenuButton(
                label = if (isFrontShowcase) "CAMERA: 3/4 FRONT VIEW" else "CAMERA: FOLLOW VIEW",
                accent = Color.White,
                filled = false,
                testTag = "front_showcase_button",
                onClick = onToggleShowcase
            )
            PauseMenuButton(
                label = "RESET TO STAND",
                accent = HudRed,
                filled = false,
                testTag = "reset_bus_button",
                onClick = onResetStand
            )
        }
    }
}

@Composable
private fun PauseMenuButton(
    label: String,
    accent: Color,
    filled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (filled) {
                    Brush.verticalGradient(listOf(ActiveOrangeStart, ActiveOrangeEnd))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF242D3B), Color(0xFF1A202B)))
                }
            )
            .border(
                1.dp,
                if (filled) Color(0xFFFDE68A) else accent.copy(alpha = 0.45f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (filled) Color(0xFF11161F) else accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
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
        Triple("SAKTHAN_STAND", "Sakthan Stand", "Bay 1 • Clock Tower"),
        Triple("THRISSUR_RAILWAY_TCR", "TCR Railway", "Station Rd • Bus Bays & Rail"),
        Triple("SWARAJ_ROUND", "Swaraj Round", "15m 1-Way • Temple"),
        Triple("PUTHANPALLY_BASILICA", "Puthanpally", "Basilica • Twin Towers & Cross"),
        Triple("VADAKKE_STAND", "Vadakke Stand", "8 Bays • Entry/Exit"),
        Triple("MANNUTHY_FLYOVER", "Mannuthy NH 544", "4-Way Junction • 25m Mast"),
        Triple("KUTHIRAN_TUNNEL", "Kuthiran Tunnel", "6-Lane Twin • Mountain"),
        Triple("PEECHI_DAM", "Peechi Dam", "Entry/Exit Stand • Spillway"),
        Triple("WADAKKANCHERY_ROB", "Wadakkanchery", "SH 22 ROB • 2 Trains"),
        Triple("SHORANUR_TERMINAL", "Shoranur Stand", "8 Bays • Sawtooth"),
        Triple("SHORANUR_STATION", "Shoranur Jn", "SRR • 2-Track Trains"),
        Triple("OLLUR_ROB", "Ollur ROB", "NH 544 • 25kV Train Below"),
        Triple("PALIYEKKARA_TOLL", "Paliakkara Toll", "12-Lane FASTag • Live Boom"),
        Triple("KURUMALI_BRIDGE", "Kurumali Bridge", "Puthukkad • Flowing River"),
        Triple("CHALAKUDY_BRIDGE", "Chalakudy Bridge", "280m Span • River & Boats"),
        Triple("KOLE_WETLANDS", "Kole Wetlands", "SH 75 • 1.5m Bunds & Paddy"),
        Triple("ENAMAVU_BRIDGE", "Enamavu Bridge", "18-Gate Sluice • Lake & Sea"),
        Triple("MUTHUVARA_LC", "Muthuvara LC", "SH 69 • Live 8-Coach Train"),
        Triple("GURUVAYUR_TEMPLE", "Guruvayur Temple", "East Nada • 33m Flagstaff"),
        Triple("GURUVAYUR_STAND", "Guruvayur Stand", "8 Sawtooth Bays • Terminal"),
        Triple("BHARATHAPPUZHA_BRIDGES", "Nila Bridges", "SH 22 + 1902 Cochin Truss"),
        Triple("CHAVAKKAD_BEACH", "Chavakkad Beach", "Seawall (Kallidum) & Boats"),
        Triple("CHETTUVA_BRIDGE", "Chettuva Bridge", "NH 66 • Chinese Nets & Boats"),
        Triple("NATTIKA_TOLL", "Nattika Toll", "10-Lane NH 66 • Live FASTag"),
        Triple("TRIPRAYAR_CANAL", "Triprayar Canal", "Sree Rama Ghat & Meenootu"),
        Triple("KOTTAPURAM_BRIDGE", "Kottapuram NW-3", "540m High Bridge & Barge"),
        Triple("MANIKANDANAL_SIGNAL", "Manikandanal", "30s/25s/4s Signal")
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
                        text = "THRISSUR DISTRICT — ZONES 1–5 (NH 544 • NH 66 • SH 22 • SH 69 • SH 75)",
                        color = HudAmber,
                        fontFamily = OutfitDisplayFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "UTM 43N (632883.20E, 1163650.18N) • %d Corridors • %.1f km • %,d Verts / %,d Quads",
                            state.roads.size,
                            totalKm,
                            totalVerts,
                            totalQuads
                        ),
                        color = HudEmerald,
                        fontSize = 10.sp,
                        fontFamily = JetBrainsMonoFamily
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

            // Road-Aligned 3D Landmarks Quick-Teleport Grid (2 neat rows)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (rowChunk in landmarkPresets.chunked(6)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for ((lmId, lmTitle, lmSub) in rowChunk) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1A2639))
                                    .border(1.dp, HudAmber.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                    .clickable { onSelectLandmark(lmId) }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
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
                        fontFamily = OutfitDisplayFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = state.vehicleLicense,
                        color = HudAmber,
                        fontSize = 9.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "3D Model Creator: ${state.vehicleCreator} • Source: ${state.vehicleSourceUrl}",
                    color = HudTextPrimary,
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMonoFamily,
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
                                fontFamily = JetBrainsMonoFamily,
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
