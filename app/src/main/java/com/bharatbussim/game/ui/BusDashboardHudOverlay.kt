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
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.ui.theme.HudAmber
import com.bharatbussim.game.ui.theme.HudCyan
import com.bharatbussim.game.ui.theme.HudEmerald
import com.bharatbussim.game.ui.theme.HudRed
import com.bharatbussim.game.ui.theme.HudTextMuted
import com.bharatbussim.game.ui.theme.HudTextPrimary
import com.bharatbussim.game.viewmodel.SimUiState
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel
import java.util.Locale
import kotlin.math.abs

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
    } else if (state.isPaused) {
        BackHandler {
            viewModel.setPaused(false)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Adaptive vertical scale so everything fits crisply from 340dp phones to large tablets
        val availH = maxHeight
        val scale = (availH / 410.dp).coerceIn(0.80f, 1.15f)

        val topBtnSize = (46.dp * scale)
        val gridBtnW = (54.dp * scale)
        val gridBtnH = (44.dp * scale)
        val gridGap = (6.dp * scale)
        val wheelSize = (156.dp * scale)

        val mapW = (182.dp * scale)
        val mapH = (100.dp * scale)

        val parkBtnW = (56.dp * scale)
        val parkBtnH = (48.dp * scale)
        val shifterW = (56.dp * scale)
        val shifterH = (96.dp * scale)

        val brakeW = (76.dp * scale)
        val brakeH = (86.dp * scale)
        val gasW = (62.dp * scale)
        val gasH = (122.dp * scale)

        if (state.hudHidden) {
            // Minimal restore button in top-left when HUD is hidden
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
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
            // LEFT COLUMN: PAUSE + EYE | 3-ROW ICON GRID | STEERING WHEEL
            // ==========================================
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                // Top-Left: Pause + Eye-Slash
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudGlassSquareButton(
                        width = topBtnSize,
                        height = topBtnSize,
                        cornerRadius = 11.dp,
                        active = state.isPaused,
                        contentDesc = "Pause Menu",
                        testTag = "pause_button",
                        onClick = { viewModel.togglePause() }
                    ) { tint -> drawPauseIcon(tint) }

                    HudGlassSquareButton(
                        width = topBtnSize,
                        height = topBtnSize,
                        cornerRadius = 11.dp,
                        active = false,
                        contentDesc = "Hide Controls",
                        testTag = "toggle_hud_button",
                        onClick = { viewModel.toggleHudHidden() }
                    ) { tint -> drawEyeSlashIcon(tint) }
                }

                // Mid-Left 8-Button Cluster (3 + 3 + 2)
                Column(
                    verticalArrangement = Arrangement.spacedBy(gridGap),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Row 1: Camera, Wiper, High Beam
                    Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
                        HudGlassSquareButton(
                            width = gridBtnW,
                            height = gridBtnH,
                            cornerRadius = 10.dp,
                            active = false,
                            contentDesc = "Switch Camera",
                            testTag = "camera_cycle_button",
                            onClick = { viewModel.cycleCameraMode() }
                        ) { tint -> drawCameraIcon(tint) }

                        HudGlassSquareButton(
                            width = gridBtnW,
                            height = gridBtnH,
                            cornerRadius = 10.dp,
                            active = state.wipersOn,
                            activeTint = ActiveOrangeStart,
                            contentDesc = "Windshield Wipers",
                            testTag = "wiper_toggle_button",
                            onClick = { viewModel.toggleWipers() }
                        ) { tint -> drawWiperIcon(tint) }

                        HudGlassSquareButton(
                            width = gridBtnW,
                            height = gridBtnH,
                            cornerRadius = 10.dp,
                            active = state.headlightsOn,
                            activeTint = ActiveOrangeStart,
                            contentDesc = "Headlights",
                            testTag = "beam_toggle_button",
                            onClick = { viewModel.toggleHeadlights() }
                        ) { tint -> drawHighBeamIcon(tint) }
                    }

                    // Row 2: Hazard Warning, Air Horn, Single Door
                    Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
                        val hazardLit = state.hazardsOn && state.blinkerPhase
                        HudGlassSquareButton(
                            width = gridBtnW,
                            height = gridBtnH,
                            cornerRadius = 10.dp,
                            active = state.hazardsOn,
                            activeTint = if (hazardLit) ActiveRedGlow else ActiveOrangeStart,
                            contentDesc = "Hazard Lights",
                            testTag = "hazard_toggle_button",
                            onClick = { viewModel.toggleHazards() }
                        ) { tint -> drawHazardTriangleIcon(tint) }

                        HudGlassSquareButton(
                            width = gridBtnW,
                            height = gridBtnH,
                            cornerRadius = 10.dp,
                            active = state.hornActive,
                            activeTint = ActiveOrangeStart,
                            contentDesc = "Air Horn",
                            testTag = "horn_button",
                            onClick = { viewModel.triggerAirHorn() }
                        ) { tint -> drawBugleHornIcon(tint) }

                        HudGlassSquareButton(
                            width = gridBtnW,
                            height = gridBtnH,
                            cornerRadius = 10.dp,
                            active = state.doorsOpen,
                            activeTint = ActiveOrangeStart,
                            contentDesc = "Passenger Door",
                            testTag = "door_toggle_button",
                            onClick = { viewModel.toggleDoors() }
                        ) { tint -> drawSingleDoorIcon(tint) }
                    }

                    // Row 3: Left Indicator, Right Indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
                        val leftLit = (state.turnLeftOn || state.hazardsOn) && state.blinkerPhase
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

                        val rightLit = (state.turnRightOn || state.hazardsOn) && state.blinkerPhase
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

                // Bottom-Left: Realistic 3D-Styled Steering Wheel
                RealisticSteeringWheel(
                    wheelAngleDeg = state.steeringWheelAngleDeg,
                    sizeDp = wheelSize,
                    onSteerDeltaDeg = { viewModel.steerByDeltaDeg(it) },
                    onSteerRelease = { viewModel.releaseSteeringWheel() },
                    onHornClick = { viewModel.triggerSteeringHorn() }
                )
            }

            // ==========================================
            // BOTTOM-CENTER: SPEEDOMETER, RPM, GEAR & FUEL PILL
            // ==========================================
            BottomSpeedFuelBar(
                speedKmh = state.speedKmh,
                rpm = state.engineRpm,
                gear = state.gear,
                isParkBrakeActive = state.isParkBrakeActive,
                displayGearNumber = state.displayGearNumber,
                fuelLiters = state.fuelLiters,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
            )

            // ==========================================
            // RIGHT COLUMN: KERALA MINIMAP | ((P)) + D/R | BRAKE & GAS PEDALS
            // ==========================================
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                // Top-Right: "Kerala" GPS Minimap
                KeralaGpsMinimap(
                    state = state,
                    width = mapW,
                    height = mapH,
                    onZoomIn = { viewModel.adjustMinimapZoom(0.35f) },
                    onZoomOut = { viewModel.adjustMinimapZoom(-0.35f) },
                    onTapMap = { viewModel.setRouteModalVisible(true) }
                )

                // Mid-Right: Parking Brake ((P)) + D/R Gear Shifter
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp * scale)
                ) {
                    HudGlassSquareButton(
                        width = parkBtnW,
                        height = parkBtnH,
                        cornerRadius = 11.dp,
                        active = state.isParkBrakeActive,
                        activeTint = ActiveRedGlow,
                        contentDesc = "Parking Brake",
                        testTag = "park_brake_button",
                        onClick = { viewModel.toggleParkBrake() }
                    ) { tint -> drawParkBrakeSymbol(tint) }

                    RightGearShifterPill(
                        selectedGear = state.gear,
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
                        active = state.brakeInput > 0.05f,
                        width = brakeW,
                        height = brakeH,
                        testTag = "brake_pedal_button",
                        onPressChanged = { viewModel.setBrake(it) }
                    )

                    RealisticMetalPedal(
                        isGasPedal = true,
                        active = state.throttleInput > 0.05f,
                        width = gasW,
                        height = gasH,
                        testTag = "gas_pedal_button",
                        onPressChanged = { viewModel.setThrottle(it) }
                    )
                }
            }
        }

        // ==========================================
        // MODAL: PAUSE & QUICK ACTIONS MENU
        // ==========================================
        if (state.isPaused && !state.showRouteModal) {
            PauseMenuModal(
                state = state,
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
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
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
