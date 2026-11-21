package com.bharatbussim.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Highway Fuel Station Interactive UI & Cinematic Refueling Cutscene Overlay:
 *  1. Parking Box Guidance Pill when inside station forecourt (gates refueling until parked inside glowing 3D box)
 *  2. Compact Step-by-Step Floating Refuel Mini-Window (Step 1: Fuel -> Step 2: Full / Price / Liters -> Step 3: Amount)
 *  3. Cinematic Cutscene Letterbox + CAM 1 / CAM 2 / CAM 3 Badge + Spoken English Conversation Subtitle Banner
 */
@Composable
fun FuelStationRefuelOverlay(
    stateProvider: () -> SimUiState,
    viewModel: ThrissurSimViewModel,
    modifier: Modifier = Modifier
) {
    val refuel by remember { derivedStateOf { stateProvider().refuel } }
    val currentFuelLiters by remember { derivedStateOf { stateProvider().fuelLiters } }
    val speedKmh by remember { derivedStateOf { stateProvider().speedKmh } }

    if (refuel.phase == RefuelPhase.DIALOG_OPEN) {
        BackHandler {
            if (refuel.dialogStep == RefuelDialogStep.AMOUNT_INPUT) {
                viewModel.stepBackRefuelDialog()
            } else {
                viewModel.dismissRefuelDialog()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 0. CINEMATIC VIDEO LETTERBOX BARS during CAM 1 / CAM 2 / CAM 3 refueling cutscene
        AnimatedVisibility(
            visible = refuel.isCutsceneActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .align(Alignment.TopCenter)
                        .background(Color(0xD804070C))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xD804070C))
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 1. TWO-SLOT FULL-BUS PARKING ALIGNMENT GUIDE PILL
            val showParkingGuide = refuel.isNearFuelStation &&
                refuel.phase == RefuelPhase.NONE &&
                (!refuel.isParkedInBayBox || speedKmh >= 1.2f)

            AnimatedVisibility(
                visible = showParkingGuide,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp)
            ) {
                val fullyInSlot = refuel.isParkedInBayBox
                val slotLabel = (refuel.parkedSlotSide ?: refuel.nearestSlotSide)?.displayName?.uppercase(Locale.ROOT) ?: "FUEL"
                val badgeColor = if (fullyInSlot) HudEmerald else HudAmber
                val guideText = when {
                    fullyInSlot -> "FULLY IN $slotLabel SLOT • STOP BUS TO REFUEL"
                    refuel.cornersInSlotCount in 1..3 ->
                        "PULL FULL BUS INSIDE $slotLabel SLOT (${refuel.cornersInSlotCount}/4)"
                    else -> "PARK FULL BUS IN DIESEL (CENTER) OR PETROL (REAR) SLOT"
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xE80C1624))
                        .border(1.4.dp, badgeColor, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("fuel_parking_box_guide"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBus,
                        contentDescription = "Fuel Bay Parking Guide",
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = guideText,
                        color = HudTextPrimary,
                        fontFamily = OutfitDisplayFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.45.sp
                    )
                }
            }

            // 2. RE-OPEN APPROACH PILL (Only when already parked inside the green Parking Box after dismiss/complete)
            AnimatedVisibility(
                visible = refuel.phase == RefuelPhase.APPROACH_PROMPT && refuel.isParkedInBayBox,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xEE0F1B2B))
                        .border(1.5.dp, HudEmerald, RoundedCornerShape(22.dp))
                        .clickable { viewModel.beginRefuelConversation() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("refuel_open_dialog_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = "Call Fuel Attendant",
                        tint = HudEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "PARKED IN BAY • TAP TO REFUEL (${currentFuelLiters.roundToInt()}/100 L)",
                        color = HudTextPrimary,
                        fontFamily = OutfitDisplayFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // 3. CINEMATIC CUTSCENE TOP BAR (CAM 1 / CAM 2 / CAM 3 badge + compact live status & STOP)
            AnimatedVisibility(
                visible = refuel.isCutsceneActive,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Live Cinema Camera Badge (CAM 1 / CAM 2 / CAM 3)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xD90A121E))
                            .border(1.2.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("refuel_cutscene_cam_badge"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(HudRed)
                        )
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Cinematic Camera",
                            tint = HudAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = refuel.cutsceneCam.badge,
                            color = Color.White,
                            fontFamily = OutfitDisplayFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp
                        )
                    }

                    // Right: During CAM 2 let the real built-in 3D dispenser LCD take center stage
                    // (no redundant floating rupee/liter card — only a discrete STOP button while pumping!)
                    if (refuel.phase == RefuelPhase.FUELING_IN_PROGRESS) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xD91A0B0E))
                                .border(1.2.dp, HudRed, RoundedCornerShape(14.dp))
                                .clickable { viewModel.stopRefuelingEarly() }
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                                .testTag("refuel_stop_early_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StopCircle,
                                contentDescription = "Stop Fueling",
                                tint = HudRed,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "STOP",
                                color = Color.White,
                                fontFamily = OutfitDisplayFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // 4. COMPACT FLOATING STEP-BY-STEP REFUEL MINI-WINDOW (Non-blocking — 1 Question at a Time!)
            AnimatedVisibility(
                visible = refuel.phase == RefuelPhase.DIALOG_OPEN,
                enter = fadeIn() + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                CompactSteppedRefuelWindow(
                    refuel = refuel,
                    currentFuelLiters = currentFuelLiters,
                    onChooseFuelType = { viewModel.chooseFuelTypeAndAdvance(it) },
                    onChooseMode = { viewModel.chooseOrderModeAndAdvance(it) },
                    onSetPrice = { viewModel.setRefuelCustomPrice(it) },
                    onSetLiters = { viewModel.setRefuelCustomLiters(it) },
                    onConfirm = { viewModel.confirmRefuelOrder() },
                    onBack = { viewModel.stepBackRefuelDialog() },
                    onDismiss = { viewModel.dismissRefuelDialog() }
                )
            }

            // 5. LIVE SPOKEN ENGLISH CONVERSATION SUBTITLE BANNER (Bottom-Center)
            AnimatedVisibility(
                visible = refuel.activeDialogueLine.isNotEmpty() && refuel.phase != RefuelPhase.DIALOG_OPEN,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (refuel.isCutsceneActive) 18.dp else 12.dp)
            ) {
                val isDriver = refuel.activeSpeakerLabel.contains("DRIVER", ignoreCase = true)
                val speakerColor = when {
                    isDriver -> HudCyan
                    refuel.selectedFuelType == FuelType.DIESEL -> HudAmber
                    else -> HudEmerald
                }
                Row(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE80A121E))
                        .border(1.2.dp, speakerColor.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(speakerColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = refuel.activeSpeakerLabel,
                            tint = speakerColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = refuel.activeSpeakerLabel,
                            color = speakerColor,
                            fontFamily = OutfitDisplayFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "\"${refuel.activeDialogueLine}\"",
                            color = HudTextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact, sleek step-by-step floating refueling mini-window:
 *   Step 1: Diesel or Petrol?
 *   Step 2: Full Tank, By Rupees (₹), or By Liters (L)?
 *   Step 3: Pick Rupees or Liters amount -> Confirm & Launch Cinematic Refuel Video!
 */
@Composable
private fun CompactSteppedRefuelWindow(
    refuel: RefuelLiveState,
    currentFuelLiters: Float,
    onChooseFuelType: (FuelType) -> Unit,
    onChooseMode: (RefuelOrderMode) -> Unit,
    onSetPrice: (Int) -> Unit,
    onSetLiters: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = if (refuel.selectedFuelType == FuelType.DIESEL) HudAmber else HudEmerald
    val missingLiters = (100f - currentFuelLiters).coerceAtLeast(0f)
    val rate = refuel.selectedFuelType.pricePerLiterInr
    val stepIndex = when (refuel.dialogStep) {
        RefuelDialogStep.AMOUNT_INPUT -> 2
        else -> 1
    }
    val bayTitle = "${refuel.selectedFuelType.displayName.uppercase(Locale.ROOT)} BAY • ₹${String.format(Locale.US, "%.2f", rate)}/L"

    Column(
        modifier = Modifier
            .width(326.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF2101B2C), Color(0xF209101C))
                )
            )
            .border(1.5.dp, accent.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Compact Stepper Header (Back / Step Pill / Tank / Close)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (stepIndex > 1) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .clickable { onBack() }
                            .testTag("refuel_step_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Step",
                            tint = HudTextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "STEP $stepIndex OF 2 • $bayTitle",
                        color = accent,
                        fontFamily = OutfitDisplayFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "Tank ${currentFuelLiters.roundToInt()} / 100 L",
                        color = HudTextMuted,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1EFFFFFF))
                    .clickable { onDismiss() }
                    .testTag("refuel_cancel_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Refuel Window",
                    tint = HudTextPrimary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // Animated 1-Question-at-a-Time Content
        AnimatedContent(
            targetState = refuel.dialogStep,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 4 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 4 })
            },
            label = "refuel_step_transition"
        ) { step ->
            when (step) {
                // STEP 1: DIESEL OR PETROL?
                RefuelDialogStep.FUEL_TYPE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "\"Sir, Diesel or Petrol today?\"",
                            color = HudTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactFuelTypeButton(
                                title = "DIESEL",
                                price = String.format(Locale.US, "₹%.2f/L", FuelType.DIESEL.pricePerLiterInr),
                                accent = HudAmber,
                                onClick = { onChooseFuelType(FuelType.DIESEL) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("refuel_type_diesel")
                            )
                            CompactFuelTypeButton(
                                title = "PETROL",
                                price = String.format(Locale.US, "₹%.2f/L", FuelType.PETROL.pricePerLiterInr),
                                accent = HudEmerald,
                                onClick = { onChooseFuelType(FuelType.PETROL) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("refuel_type_petrol")
                            )
                        }
                    }
                }

                // STEP 2: FULL TANK, BY PRICE (₹), OR BY LITERS (L)?
                RefuelDialogStep.ORDER_MODE -> {
                    val fullPrice = (missingLiters * rate).roundToInt()
                    val canFillFull = missingLiters >= 0.5f
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "\"Full tank, by rupees, or by liters?\"",
                            color = HudTextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        CompactStepChoiceRow(
                            title = "FULL TANK",
                            subtitle = if (canFillFull) {
                                String.format(Locale.US, "+%.0f L • ₹%d", missingLiters, fullPrice)
                            } else {
                                "Tank Already Full"
                            },
                            accent = accent,
                            highlighted = true,
                            enabled = canFillFull,
                            onClick = { onChooseMode(RefuelOrderMode.FULL_TANK) },
                            modifier = Modifier.testTag("refuel_mode_full")
                        )
                        CompactStepChoiceRow(
                            title = "BY PRICE (₹)",
                            subtitle = "Choose exact ₹ amount →",
                            accent = HudCyan,
                            highlighted = false,
                            enabled = canFillFull,
                            onClick = { onChooseMode(RefuelOrderMode.BY_PRICE) },
                            modifier = Modifier.testTag("refuel_mode_price")
                        )
                        CompactStepChoiceRow(
                            title = "BY LITERS (L)",
                            subtitle = "Choose exact liters →",
                            accent = HudEmerald,
                            highlighted = false,
                            enabled = canFillFull,
                            onClick = { onChooseMode(RefuelOrderMode.BY_LITERS) },
                            modifier = Modifier.testTag("refuel_mode_liters")
                        )
                    }
                }

                // STEP 3: AMOUNT PICKER (RUPEES OR LITERS)
                RefuelDialogStep.AMOUNT_INPUT -> {
                    val isPrice = (refuel.orderMode == RefuelOrderMode.BY_PRICE)
                    val orderLiters = if (isPrice) {
                        (refuel.customPriceInr / rate).coerceAtMost(missingLiters)
                    } else {
                        refuel.customLiters.toFloat().coerceAtMost(missingLiters)
                    }
                    val orderRs = (orderLiters * rate).roundToInt()
                    val canOrder = orderLiters >= 0.5f

                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(
                            text = if (isPrice) {
                                "\"How many rupees should I fill?\""
                            } else {
                                "\"How many liters do you need?\""
                            },
                            color = HudTextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (isPrice) {
                            val maxPrice = ceil(missingLiters * rate).toInt().coerceAtLeast(200)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (p in intArrayOf(200, 500, 1000, 2000)) {
                                    MiniQuickChip(
                                        label = "₹$p",
                                        selected = refuel.customPriceInr == p,
                                        accent = accent,
                                        onClick = { onSetPrice(p) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("refuel_price_$p")
                                    )
                                }
                            }
                            CompactStepperBar(
                                valueText = "₹${refuel.customPriceInr}",
                                subText = String.format(Locale.US, "≈ %.1f L", orderLiters),
                                accent = accent,
                                onMinus = { onSetPrice((refuel.customPriceInr - 100).coerceAtLeast(100)) },
                                onPlus = { onSetPrice((refuel.customPriceInr + 100).coerceAtMost(maxPrice.coerceAtLeast(5000))) }
                            )
                        } else {
                            val maxL = ceil(missingLiters).toInt().coerceAtLeast(5)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (l in intArrayOf(10, 20, 35, 50)) {
                                    MiniQuickChip(
                                        label = "${l}L",
                                        selected = refuel.customLiters == l,
                                        accent = accent,
                                        onClick = { onSetLiters(l) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("refuel_liter_$l")
                                    )
                                }
                            }
                            CompactStepperBar(
                                valueText = "${refuel.customLiters} L",
                                subText = "Total ₹$orderRs",
                                accent = accent,
                                onMinus = { onSetLiters((refuel.customLiters - 5).coerceAtLeast(5)) },
                                onPlus = { onSetLiters((refuel.customLiters + 5).coerceAtMost(maxL.coerceAtLeast(100))) }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (canOrder) accent else Color(0xFF2B384A)
                                )
                                .clickable(enabled = canOrder) { onConfirm() }
                                .testTag("refuel_confirm_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (canOrder) {
                                    "FILL ${String.format(Locale.US, "%.1f", orderLiters)} L  (₹$orderRs)"
                                } else {
                                    "TANK FULL"
                                },
                                color = if (canOrder) Color(0xFF0A121E) else HudTextMuted,
                                fontFamily = OutfitDisplayFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFuelTypeButton(
    title: String,
    price: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.4.dp, accent, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = accent,
            fontFamily = OutfitDisplayFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp
        )
        Text(
            text = price,
            color = Color.White,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactStepChoiceRow(
    title: String,
    subtitle: String,
    accent: Color,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) accent.copy(alpha = 0.20f) else Color(0xFF142133))
            .border(
                width = if (highlighted) 1.4.dp else 1.dp,
                color = if (enabled) accent.copy(alpha = if (highlighted) 0.95f else 0.45f) else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (enabled) (if (highlighted) accent else Color.White) else HudTextMuted,
            fontFamily = OutfitDisplayFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = subtitle,
            color = if (enabled) HudTextPrimary else HudTextMuted,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MiniQuickChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent else Color(0xFF162436))
            .border(1.dp, if (selected) accent else Color(0x28FFFFFF), RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF0A121E) else HudTextPrimary,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactStepperBar(
    valueText: String,
    subText: String,
    accent: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D1624))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A2A40))
                .clickable { onMinus() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease Amount",
                tint = HudTextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = valueText,
                color = Color.White,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subText,
                color = accent,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A2A40))
                .clickable { onPlus() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase Amount",
                tint = HudTextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
