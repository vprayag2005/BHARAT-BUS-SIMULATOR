package com.bharatbussim.game.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.HeadlightBeamMode
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import com.bharatbussim.game.model.TrafficSignalAspect
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.model.WiperSpeedMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val MAX_STEERING_WHEEL_DEG = 360f // Full 360° left / 360° right (720° lock-to-lock) smooth bus steering
private const val CINEMATIC_FOLLOW_FOV_DEG = 58.0f
private const val CINEMATIC_COCKPIT_FOV_DEG = 80.0f
private const val MIN_ROAD_CAMERA_CLEARANCE_M = 1.42f

@Immutable
data class SimUiState(
    val roads: List<ThrissurOsmRoad> = emptyList(),
    val activeRoadIndex: Int = 1, // Default: Sakthan Stand (Bay 1) -> M.O. Road -> Swaraj Round
    val busPos: Vec3 = Vec3(25.0f, 0.06f, -176.0f),
    val busHeadingRad: Float = 0f, // 0 = +Z (North toward Sakthan Exit Gate onto M.O. Road)
    val speedKmh: Float = 0f,
    val signedSpeedMps: Float = 0f, // Positive = forward, Negative = reverse
    val gear: TransmissionGear = TransmissionGear.Drive,
    val isParkBrakeActive: Boolean = false,
    val steerInput: Float = 0f, // -1 (full left) to +1 (full right), smoothly interpolated
    val frontWheelSteerRad: Float = 0f, // Smoothly eased front axle steering angle in radians
    val steeringWheelAngleDeg: Float = 0f, // -300° to +300° hydraulic power steering
    val isSteeringHeld: Boolean = false,
    val throttleInput: Float = 0f,
    val brakeInput: Float = 0f,
    val autoSteerAssist: Boolean = false,
    val headlightsOn: Boolean = false,
    val headlightMode: HeadlightBeamMode = HeadlightBeamMode.Off,
    val wipersOn: Boolean = false,
    val wiperMode: WiperSpeedMode = WiperSpeedMode.Off,
    val wiperPhaseRad: Float = 0f,
    val hazardsOn: Boolean = false,
    val turnLeftOn: Boolean = false,
    val turnRightOn: Boolean = false,
    val blinkerPhase: Boolean = false,
    val hornActive: Boolean = false,
    val engineRunning: Boolean = false,
    val isEngineCranking: Boolean = false,
    val engineStartPromptVisible: Boolean = false,
    val cameraMode: CameraViewMode = CameraViewMode.Chase,
    val rearViewActive: Boolean = false,
    val cameraOrbitYawDeg: Float = 0f,
    val cameraOrbitPitchDeg: Float = 0f,
    val smoothedOrbitYawDeg: Float = 0f,
    val smoothedOrbitPitchDeg: Float = 0f,
    val camFollowYawRad: Float = 0f,
    val camFocusPos: Vec3 = Vec3(25.0f, 2.08f, -172.80f),
    val camEyePos: Vec3 = Vec3(25.0f, 3.84f, -185.68f),
    val cameraFovDeg: Float = CINEMATIC_FOLLOW_FOV_DEG,
    val minimapZoom: Float = 1.0f,
    val walletInr: Int = 1250,
    val currentRoadStreetName: String = "Sakthan Thampuran Stand • Bay 1 (PALAKKAD / COIMBATORE)",
    val distanceToRouteEndKm: Float = 0.48f,
    val hudHidden: Boolean = false,
    val isPaused: Boolean = false,
    val doorsOpen: Boolean = false,
    val doorOpenFrac: Float = 0f,
    val wheelSpinRad: Float = 0f,
    val bodyPitchRad: Float = 0f,
    val bodyRollRad: Float = 0f,
    val engineRpm: Int = 0,
    val displayGearNumber: Int = 1,
    val fuelLiters: Float = 67.0f,
    val trafficSignalCycleSec: Float = 0f,
    val manikandanalSignal: TrafficSignalAspect = TrafficSignalAspect.Red,
    val manikandanalSignalCountdownSec: Int = 30,
    val showRouteModal: Boolean = false,
    val statusBanner: String = "Hino RM 2 Coach Parked in Sakthan Stand (Bay 1) • Press ENGINE START to Crank Diesel",
    val vehicleModelName: String = "Hino RM 2 Bus (12m Rear-Engine AC Luxury Coach)",
    val vehicleCreator: String = "asrafullislamsiam (CGTrader)",
    val vehicleSourceUrl: String = "https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus",
    val vehicleLicense: String = "CGTrader Royalty Free License (No AI)"
)

class ThrissurSimViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SimUiState())
    val uiState: StateFlow<SimUiState> = _uiState.asStateFlow()

    private var blinkAccumulator = 0f
    private var prevBlinkPhase = false
    private var wiperAccumulator = 0f
    private var prevWiperHalfCycle = 0
    private var trafficSignalAccumulator = 0f
    private var tollPaidInSession = false
    private var smoothedWheelSteerInput = 0f
    private var smoothedFrontWheelRad = 0f
    private var smoothedYawRate = 0f
    private var steerReleaseHoldSec = 0f
    @Volatile private var targetWheelAngleDeg = 0f
    @Volatile private var displayedWheelAngleDeg = 0f
    @Volatile private var isSteeringPointerHeld = false
    private var smoothedThrottle = 0f
    private var smoothedBrake = 0f
    private var smoothedDisplayRpm = 0f
    private var wasBrakingInPreviousStep = false
    private var lastRoadScanX = Float.NaN
    private var lastRoadScanZ = Float.NaN
    private val activeHornBlasts = java.util.concurrent.atomic.AtomicInteger(0)

    // Real-time continuous streaming Hino J08E / Ashok Leyland 6-cylinder heavy bus diesel engine state
    private var engineAudioJob: Job? = null
    private var engineStartJob: Job? = null
    private var wiperAudioJob: Job? = null
    private var relayAudioJob: Job? = null
    private var enginePromptHoldSec = 0f
    private var idleAudioHoldSec = 0f
    private var lastAudioGearIdx = 0
    private var gearShiftCutSec = 0f
    @Volatile private var synthTargetRpm = 520f
    @Volatile private var synthTargetLoad = 0f
    @Volatile private var synthTargetSpeedNorm = 0f
    @Volatile private var synthTargetGain = 0f

    fun loadRoadsIfNeeded(context: Context) {
        if (_uiState.value.roads.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = ThrissurRoadRepository.loadFromAssets(context)
            val startIdx = loaded.indexOfFirst { it.id == "ROAD_SAKTHAN_TO_SWARAJ" }.coerceAtLeast(0)
            val startRoad = loaded.getOrNull(startIdx)
            // Zone 1 Spec: Player bus spawn positioned in Bay 1 facing the straight north exit onto M.O. Road
            val pos = Vec3(25.0f, 0.06f, -176.0f)
            targetWheelAngleDeg = 0f
            displayedWheelAngleDeg = 0f
            smoothedWheelSteerInput = 0f
            smoothedFrontWheelRad = 0f
            smoothedYawRate = 0f
            isSteeringPointerHeld = false
            steerReleaseHoldSec = 0f

            _uiState.update { current ->
                val snapped = current.copy(
                    roads = loaded,
                    activeRoadIndex = startIdx,
                    busPos = pos,
                    busHeadingRad = 0f,
                    currentRoadStreetName = "Sakthan Stand • Bay 1 (PALAKKAD / COIMBATORE)",
                    distanceToRouteEndKm = (startRoad?.realDistanceM ?: 480f) / 1000f,
                    statusBanner = "Hino RM 2 Coach Parked in Sakthan Stand (Bay 1) • Press GAS to Exit onto M.O. Road"
                )
                snapCameraToBus(snapped)
            }
        }
    }

    fun stepPhysics(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        val s = _uiState.value
        if (s.roads.isEmpty() || s.isPaused) {
            if (s.isPaused) synthTargetGain = 0f
            return
        }

        // Advance Zone 1 Manikandanal Traffic Signal Cycle: Red (30s) -> Green (25s) -> Amber (4s)
        trafficSignalAccumulator = (trafficSignalAccumulator + dt) % TrafficSignalAspect.TOTAL_CYCLE_SEC
        val (curSignalAspect, curSignalCountdown) = TrafficSignalAspect.fromCycleTime(trafficSignalAccumulator)

        blinkAccumulator += dt
        val anyBlinkerActive = s.hazardsOn || s.turnLeftOn || s.turnRightOn
        val newBlink = if (anyBlinkerActive) {
            ((blinkAccumulator / 0.42f).toInt() % 2) == 0
        } else false
        if (anyBlinkerActive && newBlink != prevBlinkPhase) {
            prevBlinkPhase = newBlink
            triggerFlasherRelaySound(isHazard = s.hazardsOn, isTick = newBlink)
        } else if (!anyBlinkerActive) {
            prevBlinkPhase = false
        }

        val wiperPeriodRad = (2.0 * PI).toFloat()
        wiperAccumulator = if (s.wiperMode != WiperSpeedMode.Off) {
            (wiperAccumulator + dt * s.wiperMode.speedRadPerSec) % wiperPeriodRad
        } else if (wiperAccumulator > 0.04f) {
            val nextWiper = wiperAccumulator + dt * 4.0f
            if (nextWiper >= wiperPeriodRad) 0f else nextWiper
        } else {
            0f
        }
        if (s.wiperMode != WiperSpeedMode.Off) {
            val curHalfCycle = if (wiperAccumulator < PI.toFloat()) 0 else 1
            if (curHalfCycle != prevWiperHalfCycle) {
                prevWiperHalfCycle = curHalfCycle
                triggerWiperStrokeSound(s.wiperMode)
            }
        } else {
            prevWiperHalfCycle = 0
        }

        if (enginePromptHoldSec > 0f) {
            enginePromptHoldSec = (enginePromptHoldSec - dt).coerceAtLeast(0f)
        }

        val activeRoad = s.roads.getOrElse(s.activeRoadIndex) { s.roads.first() }

        // 1. Smooth Hydraulic Power Steering & Universal Self-Centering
        var effectiveThrottle = if (s.engineRunning) s.throttleInput else 0f

        if (s.autoSteerAssist && s.engineRunning && !s.isParkBrakeActive && s.gear == TransmissionGear.Drive) {
            effectiveThrottle = max(effectiveThrottle, 0.62f)
            val frames = activeRoad.frames
            if (frames.size >= 2) {
                var nearestIdx = 0
                var bestDist = Float.MAX_VALUE
                for (i in frames.indices) {
                    val d = (frames[i].pos - s.busPos).lengthXZ()
                    if (d < bestDist) {
                        bestDist = d
                        nearestIdx = i
                    }
                }
                val lookAheadIdx = if (activeRoad.closedLoop) {
                    (nearestIdx + 5) % frames.size
                } else {
                    min(nearestIdx + 5, frames.lastIndex)
                }
                val target = frames[lookAheadIdx].pos
                val dx = target.x - s.busPos.x
                val dz = target.z - s.busPos.z
                val desiredYaw = atan2(dx, dz)
                var diff = desiredYaw - s.busHeadingRad
                while (diff > PI.toFloat()) diff -= (2.0 * PI).toFloat()
                while (diff < -PI.toFloat()) diff += (2.0 * PI).toFloat()
                val desiredNorm = (diff / 0.55f).coerceIn(-1f, 1f)
                targetWheelAngleDeg = desiredNorm * MAX_STEERING_WHEEL_DEG
            }
        } else if (!isSteeringPointerHeld) {
            if (steerReleaseHoldSec > 0f) {
                steerReleaseHoldSec = (steerReleaseHoldSec - dt).coerceAtLeast(0f)
            } else if (abs(targetWheelAngleDeg) > 0.01f) {
                val rollingKmh = abs(s.signedSpeedMps) * 3.6f
                val speedFactor = (rollingKmh / 50f).coerceIn(0f, 1f)
                val maxReturnDegPerSec = 150f + speedFactor * 190f
                val springRate = 2.6f + speedFactor * 2.8f
                val springDesired = lerp(targetWheelAngleDeg, 0f, smoothDampAlpha(springRate, dt))
                val maxStep = maxReturnDegPerSec * dt
                val step = (springDesired - targetWheelAngleDeg).coerceIn(-maxStep, maxStep)
                val nextTarget = targetWheelAngleDeg + step
                targetWheelAngleDeg = if (abs(nextTarget) < 0.6f) 0f else nextTarget
            }
        }

        val rollingSpeedKmh = abs(s.signedSpeedMps) * 3.6f
        val highSpeedFactor = ((rollingSpeedKmh - 18f) / 72f).coerceIn(0f, 1f)

        val wheelFollowRate = when {
            isSteeringPointerHeld -> 26.0f
            s.autoSteerAssist -> 10.0f
            else -> 14.0f
        }
        displayedWheelAngleDeg = lerp(
            start = displayedWheelAngleDeg,
            end = targetWheelAngleDeg,
            t = smoothDampAlpha(wheelFollowRate, dt)
        )
        if (!isSteeringPointerHeld && abs(targetWheelAngleDeg) < 0.1f && abs(displayedWheelAngleDeg) < 0.35f) {
            displayedWheelAngleDeg = 0f
        }
        val nextWheelDeg = displayedWheelAngleDeg.coerceIn(-MAX_STEERING_WHEEL_DEG, MAX_STEERING_WHEEL_DEG)
        val targetSteerNorm = (nextWheelDeg / MAX_STEERING_WHEEL_DEG).coerceIn(-1f, 1f)

        // Continuous speed-adaptive hydraulic power steering rack (zero sign-flip step jumps)
        val steerDiff = targetSteerNorm - smoothedWheelSteerInput
        val rackRate = if (isSteeringPointerHeld) {
            13.5f - highSpeedFactor * 4.5f
        } else {
            10.5f - highSpeedFactor * 2.5f
        }
        val maxRackPerSec = if (isSteeringPointerHeld) {
            3.4f - highSpeedFactor * 1.25f
        } else {
            2.6f - highSpeedFactor * 0.8f
        }
        val rackStep = (steerDiff * smoothDampAlpha(rackRate, dt))
            .coerceIn(-maxRackPerSec * dt, maxRackPerSec * dt)
        smoothedWheelSteerInput = (smoothedWheelSteerInput + rackStep).coerceIn(-1f, 1f)
        if (!isSteeringPointerHeld && abs(targetSteerNorm) < 0.002f && abs(smoothedWheelSteerInput) < 0.003f) {
            smoothedWheelSteerInput = 0f
        }

        // Progressive non-linear steering curve: gentle precision around center, 100% authority at full lock
        val absSteer = abs(smoothedWheelSteerInput).coerceIn(0f, 1f)
        val curveSpeedMix = (rollingSpeedKmh / 80f).coerceIn(0f, 1f)
        val lowSpeedCurve = absSteer * (0.62f + 0.38f * absSteer)
        val highSpeedCurve = absSteer * (0.36f + 0.64f * absSteer)
        val curvedSteerMag = lerp(lowSpeedCurve, highSpeedCurve, curveSpeedMix).coerceIn(0f, 1f)
        val shapedSteer = if (smoothedWheelSteerInput >= 0f) curvedSteerMag else -curvedSteerMag
        val targetVisualWheelRad = (shapedSteer * 0.46f).coerceIn(-0.46f, 0.46f)
        smoothedFrontWheelRad = lerp(
            start = smoothedFrontWheelRad,
            end = targetVisualWheelRad,
            t = smoothDampAlpha(16.0f, dt)
        )
        if (!isSteeringPointerHeld && abs(targetVisualWheelRad) < 0.002f && abs(smoothedFrontWheelRad) < 0.002f) {
            smoothedFrontWheelRad = 0f
        }

        val targetThrottle = if (s.brakeInput < 0.05f) effectiveThrottle else 0f
        val throttleRate = if (targetThrottle > smoothedThrottle) dt * 4.8f else dt * 9.5f
        smoothedThrottle = (smoothedThrottle + (targetThrottle - smoothedThrottle) * throttleRate.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)

        val targetBrake = s.brakeInput
        if (targetBrake > 0.05f && smoothedBrake < 0.35f) {
            smoothedBrake = 0.35f
        }
        val brakeRate = if (targetBrake > smoothedBrake) dt * 5.2f else dt * 11.0f
        smoothedBrake = (smoothedBrake + (targetBrake - smoothedBrake) * brakeRate.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)

        // 2. Longitudinal Dynamics
        var speedMps = s.signedSpeedMps
        val maxFwdSpeedMps = 105f / 3.6f
        val maxRevSpeedMps = 24f / 3.6f

        if (s.isParkBrakeActive || s.gear == TransmissionGear.Park) {
            val decel = 12.0f * dt
            speedMps = when {
                speedMps > decel -> speedMps - decel
                speedMps < -decel -> speedMps + decel
                else -> 0f
            }
        } else {
            val speedRatio = (abs(speedMps) / maxFwdSpeedMps).coerceIn(0f, 1f)
            val cornerSpeedFactor = ((abs(speedMps) * 3.6f - 20f) / 65f).coerceIn(0f, 1f)
            val cornerScrubDecel = curvedSteerMag * cornerSpeedFactor * 2.4f
            val coastDrag = 0.92f + 0.0022f * speedMps * speedMps + cornerScrubDecel
            val rollingDrag = 0.22f + 0.00065f * speedMps * speedMps + cornerScrubDecel * 0.75f
            val activeBrakeDecel = smoothedBrake * (11.8f + speedRatio * 3.0f)
            val totalCoastAndBrake = coastDrag + activeBrakeDecel

            when (s.gear) {
                TransmissionGear.Drive -> {
                    if (speedMps < 0f) {
                        val stopDecel = (11.0f + smoothedBrake * 11.5f) * dt
                        speedMps = (speedMps + stopDecel).coerceAtMost(0f)
                    } else {
                        val cornerTorqueCut = (1f - curvedSteerMag * cornerSpeedFactor * 0.35f).coerceIn(0.60f, 1f)
                        val torqueCurve = (1f - speedRatio * 0.60f) * cornerTorqueCut
                        val accel = if (smoothedThrottle > 0.02f && s.brakeInput < 0.05f) {
                            smoothedThrottle * 3.60f * torqueCurve
                        } else 0f
                        val decel = if (speedMps > 0f) {
                            (if (accel > 0.05f) rollingDrag else totalCoastAndBrake) * dt
                        } else 0f
                        speedMps = (speedMps + accel * dt - decel).coerceIn(0f, maxFwdSpeedMps)
                    }
                }
                TransmissionGear.Reverse -> {
                    if (speedMps > 0f) {
                        val stopDecel = (11.5f + smoothedBrake * 11.5f + smoothedThrottle * 5.0f) * dt
                        speedMps = (speedMps - stopDecel).coerceAtLeast(0f)
                    } else {
                        val revFrac = (abs(speedMps) / maxRevSpeedMps).coerceIn(0f, 1f)
                        val revAccel = if (smoothedThrottle > 0.02f && s.brakeInput < 0.05f) {
                            -smoothedThrottle * 2.85f * (1f - revFrac * 0.50f)
                        } else 0f
                        val revDrag = if (speedMps < 0f && (smoothedThrottle <= 0.02f || s.brakeInput >= 0.05f)) {
                            totalCoastAndBrake * dt
                        } else 0f
                        speedMps = (speedMps + revAccel * dt + revDrag).coerceIn(-maxRevSpeedMps, 0f)
                    }
                }
                TransmissionGear.Neutral, TransmissionGear.Park -> {
                    val drag = (0.65f + activeBrakeDecel) * dt
                    speedMps = when {
                        speedMps > drag -> speedMps - drag
                        speedMps < -drag -> speedMps + drag
                        else -> 0f
                    }
                }
            }
        }

        val curSpeedKmh = abs(speedMps) * 3.6f
        val speedNorm = (curSpeedKmh / 105f).coerceIn(0f, 1f)
        val isDrivingGear = s.engineRunning && !s.isParkBrakeActive &&
            (s.gear == TransmissionGear.Drive || s.gear == TransmissionGear.Reverse)
        val activeThrottle = if (isDrivingGear && s.brakeInput < 0.05f) smoothedThrottle else 0f

        if (s.engineRunning) {
            idleAudioHoldSec = 3.2f
            ensureEngineAudioStreamStarted()
        } else {
            idleAudioHoldSec = 0f
        }

        val gearThresholds = floatArrayOf(0f, 16f, 32f, 50f, 68f, 86f, 110f)
        var gearIdx = 0
        for (g in 0 until 6) {
            if (curSpeedKmh >= gearThresholds[g]) gearIdx = g
        }
        if (gearIdx != lastAudioGearIdx && curSpeedKmh > 2.5f) {
            lastAudioGearIdx = gearIdx
            gearShiftCutSec = 0.16f
        }
        if (gearShiftCutSec > 0f) {
            gearShiftCutSec = (gearShiftCutSec - dt).coerceAtLeast(0f)
        }

        val gStart = gearThresholds[gearIdx]
        val gSpan = (gearThresholds[gearIdx + 1] - gStart).coerceAtLeast(1f)
        val inGearFrac = ((curSpeedKmh - gStart) / gSpan).coerceIn(0f, 1f)

        val baseGearRpm = 520f + gearIdx * 78f + speedNorm * 340f
        val inGearClimbRpm = inGearFrac * 360f
        val throttlePullRpm = if (gearShiftCutSec > 0f) -70f else activeThrottle * (175f + speedNorm * 95f)

        synthTargetRpm = (baseGearRpm + inGearClimbRpm + throttlePullRpm).coerceIn(500f, 1580f)

        val targetDisplayRpm = when {
            s.isEngineCranking -> 260f
            !s.engineRunning -> 0f
            s.isParkBrakeActive || s.gear == TransmissionGear.Park -> 650f
            s.gear == TransmissionGear.Neutral -> 650f + effectiveThrottle * 1450f
            s.gear == TransmissionGear.Reverse -> {
                720f + (curSpeedKmh / 24f).coerceIn(0f, 1f) * 1350f + activeThrottle * 280f
            }
            else -> {
                val shiftDip = if (gearShiftCutSec > 0f) -260f else 0f
                (650f + gearIdx * 90f + inGearFrac * 1250f + activeThrottle * 280f + shiftDip)
                    .coerceIn(650f, 2600f)
            }
        }
        smoothedDisplayRpm += (targetDisplayRpm - smoothedDisplayRpm) * (dt * 9.5f).coerceIn(0f, 1f)
        val displayGearNumber = if (!s.isParkBrakeActive && s.gear == TransmissionGear.Drive) {
            gearIdx + 1
        } else 0

        synthTargetLoad = if (gearShiftCutSec > 0f) {
            0.12f
        } else {
            (activeThrottle * 0.78f + speedNorm * 0.22f).coerceIn(0f, 1f)
        }
        synthTargetSpeedNorm = speedNorm
        synthTargetGain = when {
            !s.engineRunning -> 0f
            activeThrottle > 0.02f || curSpeedKmh > 0.35f -> {
                (0.28f + speedNorm * 0.44f + activeThrottle * (0.20f + speedNorm * 0.10f)).coerceIn(0.26f, 0.94f)
            }
            else -> 0.12f
        }

        // 3. Speed-Calibrated Ackermann Kinematics & Chassis Yaw Response
        val effectiveWheelbase = 5.15f
        val steerSpeedRatio = (curSpeedKmh / 105f).coerceIn(0f, 1f)
        val speedTaper = 1f - steerSpeedRatio
        val maxFrontWheelDeg = 22.0f + 20.0f * speedTaper
        val frontWheelAngleRad = shapedSteer * Math.toRadians(maxFrontWheelDeg.toDouble()).toFloat()

        val absSpeedMps = abs(speedMps)
        val scaledSpeedMag = if (absSpeedMps <= 8.5f) {
            absSpeedMps
        } else {
            8.5f + (absSpeedMps - 8.5f) * 0.62f
        }
        val kinematicSpeedMps = if (speedMps >= 0f) scaledSpeedMag else -scaledSpeedMag

        val rawAngularVel = if (abs(kinematicSpeedMps) > 0.03f) {
            (kinematicSpeedMps / effectiveWheelbase) * tan(frontWheelAngleRad)
        } else 0f
        val maxSafeYawRate = (1.18f - 0.28f * steerSpeedRatio).coerceAtLeast(0.82f)
        val softKneeYaw = rawAngularVel / (1f + abs(rawAngularVel) / (maxSafeYawRate * 2.2f))
        val targetAngularVel = softKneeYaw.coerceIn(-maxSafeYawRate, maxSafeYawRate)

        val yawInertiaRate = 11.0f - 3.2f * steerSpeedRatio
        smoothedYawRate += (targetAngularVel - smoothedYawRate) * smoothDampAlpha(yawInertiaRate, dt)
        if (abs(kinematicSpeedMps) <= 0.03f) {
            smoothedYawRate *= (1f - smoothDampAlpha(16f, dt))
        }

        var newHeading = s.busHeadingRad + smoothedYawRate * dt
        while (newHeading > PI.toFloat()) newHeading -= (2.0 * PI).toFloat()
        while (newHeading < -PI.toFloat()) newHeading += (2.0 * PI).toFloat()

        var curRoadBestFrameIdx = 0
        var curRoadMinDistSq = Float.MAX_VALUE
        val currentRoadFrames = activeRoad.frames
        for (fIdx in currentRoadFrames.indices) {
            val fx = currentRoadFrames[fIdx].pos.x - s.busPos.x
            val fz = currentRoadFrames[fIdx].pos.z - s.busPos.z
            val dSq = fx * fx + fz * fz
            if (dSq < curRoadMinDistSq) {
                curRoadMinDistSq = dSq
                curRoadBestFrameIdx = fIdx
            }
        }

        var otherRoadIdx = s.activeRoadIndex
        var otherRoadFrameIdx = curRoadBestFrameIdx
        var otherMinDistSq = Float.MAX_VALUE
        if (curRoadMinDistSq > 16.0f * 16.0f) {
            for (rIdx in s.roads.indices) {
                if (rIdx == s.activeRoadIndex) continue
                val frames = s.roads[rIdx].frames
                for (fIdx in frames.indices step 2) {
                    val fx = frames[fIdx].pos.x - s.busPos.x
                    val fz = frames[fIdx].pos.z - s.busPos.z
                    val dSq = fx * fx + fz * fz
                    if (dSq < otherMinDistSq) {
                        otherMinDistSq = dSq
                        otherRoadIdx = rIdx
                        otherRoadFrameIdx = fIdx
                    }
                }
            }
        }

        val shouldSwitchRoad = curRoadMinDistSq > 16.0f * 16.0f && otherMinDistSq < curRoadMinDistSq * 0.55f
        val closestRoadIdx = if (shouldSwitchRoad) otherRoadIdx else s.activeRoadIndex
        val closestFrameIdx = if (shouldSwitchRoad) otherRoadFrameIdx else curRoadBestFrameIdx
        val curRoad = s.roads[closestRoadIdx]
        val curFrames = curRoad.frames

        val vx = sin(newHeading) * kinematicSpeedMps
        val vz = cos(newHeading) * kinematicSpeedMps
        val newPos = Vec3(s.busPos.x + vx * dt, 0.06f, s.busPos.z + vz * dt)

        // 4. Update street name, remaining distance, Manikandanal signal status & toll status
        val moveDx = newPos.x - lastRoadScanX
        val moveDz = newPos.z - lastRoadScanZ
        var streetName = s.currentRoadStreetName
        var remKm = s.distanceToRouteEndKm
        var newWallet = s.walletInr
        var newBanner = s.statusBanner

        val nearManikandanal = hypot(newPos.x - 16.5f, newPos.z - (-36.0f)) < 26f
        if (nearManikandanal || lastRoadScanX.isNaN() || (moveDx * moveDx + moveDz * moveDz) >= 9.0f) {
            lastRoadScanX = newPos.x
            lastRoadScanZ = newPos.z

            val totalSplineLen = curFrames.lastOrNull()?.cumulativeDist?.coerceAtLeast(1f) ?: 1f
            val curSplineDist = curFrames.getOrNull(closestFrameIdx)?.cumulativeDist ?: 0f
            val fracRemaining = (1f - (curSplineDist / totalSplineLen)).coerceIn(0f, 1f)
            remKm = (curRoad.realDistanceM / 1000f) * fracRemaining

            val insideSakthanTerminal = newPos.x in -22f..48f && newPos.z in -218f..-138f
            val nearTcrStation = newPos.x in -85f..6f && newPos.z in -155f..-55f
            streetName = when {
                insideSakthanTerminal -> {
                    if (newPos.z < -154f) "Sakthan Stand • Bay 1 (PALAKKAD / COIMBATORE)" else "Sakthan Stand • Clock Tower & North Exit Gate"
                }
                nearTcrStation -> "Thrissur Central Railway Station (TCR) • Station Road"
                nearManikandanal -> "Manikandanal Signal (${curSignalAspect.label} ${curSignalCountdown}s) • Swaraj Round South"
                hypot(newPos.x - 3.1f, newPos.z - 9.5f) < 62f -> "Swaraj Round (1-Way Clockwise) • Vadakkumnathan Temple"
                hypot(newPos.x - (-34f), newPos.z - 220f) < 48f -> "Vadakke Stand • Thrissur North Bus Stand"
                hypot(newPos.x - (-2635f), newPos.z - 1260f) < 55f -> "Guruvayur Sree Krishna Temple • East Nada"
                hypot(newPos.x - (-2735f), newPos.z - 1280f) < 55f -> "Guruvayur Municipal Bus Stand"
                hypot(newPos.x - (-4500f), newPos.z - 1080f) < 85f -> "Chavakkad Beach & Azhi Estuary"
                hypot(newPos.x - (-2220f), newPos.z - (-2410f)) < 85f -> "Triprayar - Nattika Beach Terminus"
                else -> {
                    val stepList = curRoad.stepNames
                    if (stepList.isNotEmpty()) {
                        val idx = ((1f - fracRemaining) * stepList.size).toInt().coerceIn(0, stepList.lastIndex)
                        stepList[idx]
                    } else curRoad.name
                }
            }

            // 5. Automatic FASTag at Paliyekkara Toll Plaza
            val distToToll = hypot(newPos.x - 998.2f, newPos.z - (-1805.2f))
            if (distToToll < 24f && !tollPaidInSession && newWallet >= 50) {
                tollPaidInSession = true
                newWallet -= 50
                newBanner = "FASTag Toll Paid: ₹50 at Paliyekkara Toll Plaza (NH 544) • Barrier Open"
            } else if (distToToll > 60f) {
                tollPaidInSession = false
            }
        }

        // 6. Camera Rig & Road-Mesh Anti-Clipping
        val newSpeedKmh = abs(speedMps) * 3.6f
        val yawFollowRate = when (s.cameraMode) {
            CameraViewMode.Cockpit -> 0f
            CameraViewMode.Chase -> 7.8f
            CameraViewMode.Orbit -> 10.5f
        }
        val nextFollowYawRad = if (s.cameraMode == CameraViewMode.Cockpit) {
            newHeading
        } else {
            lerpAngleRad(
                fromRad = s.camFollowYawRad,
                toRad = newHeading,
                t = smoothDampAlpha(yawFollowRate, dt)
            )
        }
        val nextOrbitYawDeg = if (s.cameraMode == CameraViewMode.Orbit) {
            lerpAngleDeg(
                fromDeg = s.smoothedOrbitYawDeg,
                toDeg = s.cameraOrbitYawDeg,
                t = (dt * 15.0f).coerceIn(0f, 1f)
            )
        } else 0f
        val nextOrbitPitchDeg = if (s.cameraMode == CameraViewMode.Orbit) {
            lerp(
                start = s.smoothedOrbitPitchDeg,
                end = s.cameraOrbitPitchDeg,
                t = (dt * 15.0f).coerceIn(0f, 1f)
            )
        } else 0f

        val (desiredFocus, desiredEye, desiredFov) = computeDesiredCameraRig(
            busPos = newPos,
            busHeadingRad = newHeading,
            followYawRad = nextFollowYawRad,
            steerInput = smoothedWheelSteerInput,
            speedKmh = newSpeedKmh,
            cameraMode = s.cameraMode,
            rearViewActive = s.rearViewActive,
            orbitYawDeg = nextOrbitYawDeg,
            orbitPitchDeg = nextOrbitPitchDeg
        )

        val focusLerpRate = when (s.cameraMode) {
            CameraViewMode.Chase, CameraViewMode.Cockpit -> 0f
            CameraViewMode.Orbit -> 12.5f
        }
        val eyeLerpRate = when (s.cameraMode) {
            CameraViewMode.Chase, CameraViewMode.Cockpit -> 0f
            CameraViewMode.Orbit -> 10.5f
        }

        val lerpedFocus = if (focusLerpRate <= 0.05f) desiredFocus else lerpVec3(
            start = s.camFocusPos,
            end = desiredFocus,
            t = (dt * focusLerpRate).coerceIn(0f, 1f)
        )
        val rawLerpedEye = if (eyeLerpRate <= 0.05f) desiredEye else lerpVec3(
            start = s.camEyePos,
            end = desiredEye,
            t = (dt * eyeLerpRate).coerceIn(0f, 1f)
        )
        val safeCameraEye = preventRoadMeshCameraClip(
            focusPos = lerpedFocus,
            candidateEye = rawLerpedEye,
            busPos = newPos,
            busHeadingRad = newHeading,
            cameraMode = s.cameraMode
        )
        val nextCameraFovDeg = lerp(
            start = s.cameraFovDeg,
            end = desiredFov,
            t = (dt * 9.0f).coerceIn(0f, 1f)
        )

        val fuelUsed = abs(speedMps) * dt * 0.00038f
        val nextFuel = (s.fuelLiters - fuelUsed).coerceIn(1f, 100f)

        val twoPi = (2.0 * PI).toFloat()
        val nextWheelSpin = ((s.wheelSpinRad + (kinematicSpeedMps / 0.52f) * dt) % twoPi + twoPi) % twoPi
        val accelMps2 = (speedMps - s.signedSpeedMps) / dt
        val targetPitch = (-accelMps2 * 0.0026f).coerceIn(-0.022f, 0.022f)
        val nextBodyPitch = lerp(s.bodyPitchRad, targetPitch, (dt * 7.5f).coerceIn(0f, 1f))
        val targetRoll = (smoothedWheelSteerInput * (newSpeedKmh / 105f).coerceIn(0f, 1f) * 0.028f)
            .coerceIn(-0.028f, 0.028f)
        val nextBodyRoll = lerp(s.bodyRollRad, targetRoll, (dt * 6.5f).coerceIn(0f, 1f))
        val targetDoorFrac = if (s.doorsOpen) 1f else 0f
        val nextDoorFrac = lerp(s.doorOpenFrac, targetDoorFrac, (dt * 5.2f).coerceIn(0f, 1f))

        _uiState.update {
            it.copy(
                activeRoadIndex = closestRoadIdx,
                busPos = newPos,
                busHeadingRad = newHeading,
                speedKmh = newSpeedKmh,
                signedSpeedMps = speedMps,
                wheelSpinRad = nextWheelSpin,
                bodyPitchRad = nextBodyPitch,
                bodyRollRad = nextBodyRoll,
                doorOpenFrac = nextDoorFrac,
                engineRpm = smoothedDisplayRpm.toInt(),
                displayGearNumber = displayGearNumber,
                steerInput = smoothedWheelSteerInput,
                frontWheelSteerRad = smoothedFrontWheelRad,
                steeringWheelAngleDeg = nextWheelDeg,
                wiperPhaseRad = wiperAccumulator,
                blinkerPhase = newBlink,
                smoothedOrbitYawDeg = nextOrbitYawDeg,
                smoothedOrbitPitchDeg = nextOrbitPitchDeg,
                camFollowYawRad = nextFollowYawRad,
                camFocusPos = lerpedFocus,
                camEyePos = safeCameraEye,
                cameraFovDeg = nextCameraFovDeg,
                currentRoadStreetName = streetName,
                distanceToRouteEndKm = remKm,
                walletInr = newWallet,
                fuelLiters = nextFuel,
                trafficSignalCycleSec = trafficSignalAccumulator,
                manikandanalSignal = curSignalAspect,
                manikandanalSignalCountdownSec = curSignalCountdown,
                engineStartPromptVisible = !s.engineRunning && !s.isEngineCranking && enginePromptHoldSec > 0f,
                statusBanner = newBanner
            )
        }
    }

    fun grabSteeringWheel() {
        isSteeringPointerHeld = true
        steerReleaseHoldSec = 0.20f
        _uiState.update { it.copy(isSteeringHeld = true, autoSteerAssist = false) }
    }

    fun steerByDeltaDeg(deltaDeg: Float) {
        val safeDelta = deltaDeg.coerceIn(-45f, 45f)
        isSteeringPointerHeld = true
        steerReleaseHoldSec = 0.16f
        targetWheelAngleDeg = (targetWheelAngleDeg + safeDelta).coerceIn(
            -MAX_STEERING_WHEEL_DEG,
            MAX_STEERING_WHEEL_DEG
        )
        val cur = _uiState.value
        if (!cur.isSteeringHeld || cur.autoSteerAssist) {
            _uiState.update { it.copy(isSteeringHeld = true, autoSteerAssist = false) }
        }
    }

    fun releaseSteeringWheel() {
        isSteeringPointerHeld = false
        steerReleaseHoldSec = 0.20f
        _uiState.update { it.copy(isSteeringHeld = false) }
    }

    fun setSteer(value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        val deg = clamped * MAX_STEERING_WHEEL_DEG
        isSteeringPointerHeld = abs(clamped) > 0.001f
        steerReleaseHoldSec = 0.09f
        targetWheelAngleDeg = deg
        _uiState.update {
            it.copy(
                isSteeringHeld = isSteeringPointerHeld,
                autoSteerAssist = false
            )
        }
    }

    fun setThrottle(pressed: Boolean) {
        val prev = _uiState.value
        if (pressed && !prev.engineRunning) {
            enginePromptHoldSec = 2.6f
            _uiState.update {
                it.copy(
                    throttleInput = 0f,
                    engineStartPromptVisible = !it.isEngineCranking,
                    statusBanner = if (it.isEngineCranking) {
                        "Cranking Hino J08E Diesel Engine..."
                    } else {
                        "Engine OFF • Press ENGINE START Button"
                    }
                )
            }
            return
        }
        if (pressed) {
            idleAudioHoldSec = 3.2f
            ensureEngineAudioStreamStarted()
            if (prev.throttleInput <= 0.05f && prev.isParkBrakeActive) {
                triggerAirBrakeReleaseSound()
            }
        }
        _uiState.update { s ->
            val autoReleasePark = if (pressed && s.isParkBrakeActive) false else s.isParkBrakeActive
            val autoGear = if (pressed && s.gear == TransmissionGear.Park) TransmissionGear.Drive else s.gear
            val banner = if (pressed && s.isParkBrakeActive) {
                "Park Brake Auto-Released • Departing Sakthan Stand (Bay 1)"
            } else s.statusBanner
            s.copy(
                throttleInput = if (pressed) 1f else 0f,
                isParkBrakeActive = autoReleasePark,
                gear = autoGear,
                engineStartPromptVisible = false,
                statusBanner = banner
            )
        }
    }

    fun toggleEngineStart() {
        val current = _uiState.value
        if (current.isEngineCranking) return

        if (current.engineRunning) {
            engineStartJob?.cancel()
            smoothedThrottle = 0f
            synthTargetGain = 0f
            _uiState.update {
                it.copy(
                    engineRunning = false,
                    isEngineCranking = false,
                    throttleInput = 0f,
                    autoSteerAssist = false,
                    engineStartPromptVisible = false,
                    statusBanner = "Hino J08E Diesel Engine Stopped"
                )
            }
            viewModelScope.launch(Dispatchers.Default) {
                playEngineStopSound()
            }
            return
        }

        engineStartJob?.cancel()
        enginePromptHoldSec = 0f
        _uiState.update {
            it.copy(
                isEngineCranking = true,
                engineStartPromptVisible = false,
                statusBanner = "Cranking Hino J08E 6-Cyl Diesel..."
            )
        }
        engineStartJob = viewModelScope.launch {
            launch(Dispatchers.Default) {
                playEngineStartSound()
            }
            delay(780L)
            synthTargetRpm = 620f
            synthTargetLoad = 0.14f
            synthTargetGain = 0.16f
            ensureEngineAudioStreamStarted()
            _uiState.update {
                it.copy(
                    isEngineCranking = false,
                    engineRunning = true,
                    engineStartPromptVisible = false,
                    statusBanner = "Engine Running (650 RPM Idle) • Ready to Drive from Bay 1"
                )
            }
        }
    }

    fun setBrake(pressed: Boolean) {
        val prev = _uiState.value
        if (pressed && !wasBrakingInPreviousStep) {
            wasBrakingInPreviousStep = true
            triggerBrakeApplySound(prev.speedKmh)
        } else if (!pressed && wasBrakingInPreviousStep) {
            wasBrakingInPreviousStep = false
            triggerAirBrakeReleaseSound()
        }
        _uiState.update { it.copy(brakeInput = if (pressed) 1f else 0f) }
    }

    fun selectGear(target: TransmissionGear) {
        if (target == TransmissionGear.Park || target == TransmissionGear.Neutral) {
            smoothedThrottle = 0f
        }
        _uiState.update { s ->
            val newParkBrake = target == TransmissionGear.Park
            val newAutoAssist = if (target != TransmissionGear.Drive) false else s.autoSteerAssist
            s.copy(
                gear = target,
                isParkBrakeActive = newParkBrake,
                autoSteerAssist = newAutoAssist,
                displayGearNumber = if (!newParkBrake && target == TransmissionGear.Drive) {
                    s.displayGearNumber.coerceIn(1, 6)
                } else 0,
                statusBanner = "Gear shifted to ${target.name} (${target.code})"
            )
        }
    }

    fun toggleParkBrake() {
        val prev = _uiState.value
        if (prev.isParkBrakeActive) {
            triggerAirBrakeReleaseSound()
        } else {
            smoothedThrottle = 0f
            triggerBrakeApplySound(prev.speedKmh)
        }
        _uiState.update { s ->
            val next = !s.isParkBrakeActive
            val nextGear = if (!next && s.gear == TransmissionGear.Park) TransmissionGear.Drive else s.gear
            s.copy(
                isParkBrakeActive = next,
                gear = nextGear,
                displayGearNumber = if (!next && nextGear == TransmissionGear.Drive) 1 else 0,
                statusBanner = if (next) "Parking Brake Engaged (LOCKED)" else "Parking Brake Released • Ready to Drive"
            )
        }
    }

    fun toggleAutoPilot() {
        val current = _uiState.value
        if (!current.autoSteerAssist && !current.engineRunning && !current.isEngineCranking) {
            toggleEngineStart()
        }
        _uiState.update { s ->
            val next = !s.autoSteerAssist
            s.copy(
                autoSteerAssist = next,
                gear = if (next) TransmissionGear.Drive else s.gear,
                isParkBrakeActive = if (next) false else s.isParkBrakeActive,
                statusBanner = if (next) {
                    "OSM Route Cruise Active on ${s.roads.getOrNull(s.activeRoadIndex)?.name.orEmpty()}"
                } else {
                    "Manual Hydraulic Power Steering Active"
                }
            )
        }
    }

    fun toggleHeadlights() {
        _uiState.update { s ->
            val nextMode = when (s.headlightMode) {
                HeadlightBeamMode.Off -> HeadlightBeamMode.Low
                HeadlightBeamMode.Low -> HeadlightBeamMode.High
                HeadlightBeamMode.High -> HeadlightBeamMode.Off
            }
            val banner = when (nextMode) {
                HeadlightBeamMode.Off -> "Headlights: OFF"
                HeadlightBeamMode.Low -> "Headlights: Low Beam (Dipped)"
                HeadlightBeamMode.High -> "Headlights: High Beam (Full Range)"
            }
            s.copy(
                headlightMode = nextMode,
                headlightsOn = nextMode != HeadlightBeamMode.Off,
                statusBanner = banner
            )
        }
    }

    fun toggleWipers() {
        val nextMode = when (_uiState.value.wiperMode) {
            WiperSpeedMode.Off -> WiperSpeedMode.Low
            WiperSpeedMode.Low -> WiperSpeedMode.Mid
            WiperSpeedMode.Mid -> WiperSpeedMode.High
            WiperSpeedMode.High -> WiperSpeedMode.Off
        }
        if (nextMode != WiperSpeedMode.Off) {
            val curHalf = if (wiperAccumulator < PI.toFloat()) 0 else 1
            prevWiperHalfCycle = curHalf
            triggerWiperStrokeSound(nextMode)
        } else {
            wiperAudioJob?.cancel()
            prevWiperHalfCycle = 0
        }
        _uiState.update { s ->
            val banner = when (nextMode) {
                WiperSpeedMode.Off -> "Windshield Wipers: OFF"
                WiperSpeedMode.Low -> "Windshield Wipers: Low Speed"
                WiperSpeedMode.Mid -> "Windshield Wipers: Mid Speed"
                WiperSpeedMode.High -> "Windshield Wipers: High Speed"
            }
            s.copy(
                wiperMode = nextMode,
                wipersOn = nextMode != WiperSpeedMode.Off,
                statusBanner = banner
            )
        }
    }

    fun toggleHazards() {
        blinkAccumulator = 0f
        val next = !_uiState.value.hazardsOn
        prevBlinkPhase = next
        if (next) {
            triggerFlasherRelaySound(isHazard = true, isTick = true)
        } else {
            relayAudioJob?.cancel()
        }
        _uiState.update {
            it.copy(hazardsOn = next, turnLeftOn = false, turnRightOn = false, blinkerPhase = next)
        }
    }

    fun toggleTurnLeft() {
        blinkAccumulator = 0f
        val next = !_uiState.value.turnLeftOn
        prevBlinkPhase = next
        if (next) {
            triggerFlasherRelaySound(isHazard = false, isTick = true)
        } else {
            relayAudioJob?.cancel()
        }
        _uiState.update {
            it.copy(turnLeftOn = next, turnRightOn = false, hazardsOn = false, blinkerPhase = next)
        }
    }

    fun toggleTurnRight() {
        blinkAccumulator = 0f
        val next = !_uiState.value.turnRightOn
        prevBlinkPhase = next
        if (next) {
            triggerFlasherRelaySound(isHazard = false, isTick = true)
        } else {
            relayAudioJob?.cancel()
        }
        _uiState.update {
            it.copy(turnRightOn = next, turnLeftOn = false, hazardsOn = false, blinkerPhase = next)
        }
    }

    fun toggleDoors() {
        triggerAirBrakeReleaseSound()
        _uiState.update {
            val next = !it.doorsOpen
            it.copy(
                doorsOpen = next,
                statusBanner = if (next) "Passenger Door Open" else "Passenger Door Closed"
            )
        }
    }

    fun toggleHudHidden() {
        _uiState.update { it.copy(hudHidden = !it.hudHidden) }
    }

    fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun setPaused(paused: Boolean) {
        _uiState.update { it.copy(isPaused = paused) }
    }

    fun cycleCameraMode() {
        val modes = CameraViewMode.entries
        _uiState.update { s ->
            val nextIdx = (s.cameraMode.ordinal + 1) % modes.size
            val nextMode = modes[nextIdx]
            val updated = s.copy(
                cameraMode = nextMode,
                rearViewActive = false,
                cameraOrbitYawDeg = 0f,
                cameraOrbitPitchDeg = 0f,
                smoothedOrbitYawDeg = 0f,
                smoothedOrbitPitchDeg = 0f,
                statusBanner = "Camera Mode: ${nextMode.label}"
            )
            snapCameraToBus(updated)
        }
    }

    fun toggleRearCamera() {
        _uiState.update { it.copy(rearViewActive = !it.rearViewActive) }
    }

    fun toggleFrontShowcaseView() {
        _uiState.update { s ->
            val isFront = abs(s.cameraOrbitYawDeg) > 95f && s.cameraMode == CameraViewMode.Orbit
            if (isFront) {
                s.copy(
                    cameraMode = CameraViewMode.Chase,
                    rearViewActive = false,
                    cameraOrbitYawDeg = 0f,
                    cameraOrbitPitchDeg = 0f,
                    statusBanner = "Hino RM 2 EAGLE (BUS LOVER) • Static Chase View"
                )
            } else {
                s.copy(
                    cameraMode = CameraViewMode.Orbit,
                    rearViewActive = false,
                    cameraOrbitYawDeg = 146f,
                    cameraOrbitPitchDeg = 4f,
                    statusBanner = "Hino RM 2 EAGLE (BUS LOVER) • 3/4 Front Showcase View"
                )
            }
        }
    }

    fun orbitCamera(deltaYawDeg: Float, deltaPitchDeg: Float) {
        if (_uiState.value.cameraMode != CameraViewMode.Orbit) return
        _uiState.update {
            var nextYaw = it.cameraOrbitYawDeg + deltaYawDeg
            while (nextYaw > 180f) nextYaw -= 360f
            while (nextYaw < -180f) nextYaw += 360f
            it.copy(
                cameraOrbitYawDeg = nextYaw,
                cameraOrbitPitchDeg = (it.cameraOrbitPitchDeg + deltaPitchDeg).coerceIn(-5f, 22f)
            )
        }
    }

    fun adjustMinimapZoom(delta: Float) {
        _uiState.update { it.copy(minimapZoom = (it.minimapZoom + delta).coerceIn(0.4f, 3.0f)) }
    }

    fun setRouteModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showRouteModal = visible) }
    }

    fun teleportToRoad(index: Int) {
        val s = _uiState.value
        val road = s.roads.getOrNull(index) ?: return
        val isSakthanStart = road.id == "ROAD_SAKTHAN_TO_SWARAJ"
        val spawnFrame = if (isSakthanStart) {
            road.frames.firstOrNull()
        } else {
            road.frames.firstOrNull { it.cumulativeDist >= 18f } ?: road.frames.firstOrNull()
        } ?: return
        val heading = if (isSakthanStart) 0f else atan2(spawnFrame.tangent.x, spawnFrame.tangent.z)
        targetWheelAngleDeg = 0f
        displayedWheelAngleDeg = 0f
        smoothedYawRate = 0f
        isSteeringPointerHeld = false
        steerReleaseHoldSec = 0f
        smoothedWheelSteerInput = 0f
        smoothedFrontWheelRad = 0f
        smoothedThrottle = 0f
        smoothedBrake = 0f
        smoothedDisplayRpm = if (s.engineRunning) 650f else 0f
        _uiState.update { current ->
            val updated = current.copy(
                activeRoadIndex = index,
                busPos = Vec3(spawnFrame.pos.x, 0.06f, spawnFrame.pos.z),
                busHeadingRad = heading,
                speedKmh = 0f,
                signedSpeedMps = 0f,
                engineRpm = if (current.engineRunning) 650 else 0,
                displayGearNumber = 1,
                steerInput = 0f,
                frontWheelSteerRad = 0f,
                steeringWheelAngleDeg = 0f,
                isSteeringHeld = false,
                gear = TransmissionGear.Drive,
                isParkBrakeActive = false,
                currentRoadStreetName = if (isSakthanStart) {
                    "Sakthan Stand • Bay 1 (PALAKKAD / COIMBATORE)"
                } else {
                    road.stepNames.firstOrNull() ?: road.name
                },
                distanceToRouteEndKm = road.realDistanceM / 1000f,
                showRouteModal = false,
                statusBanner = if (isSakthanStart) {
                    "Parked in Sakthan Stand (Bay 1) • Press GAS to Exit onto M.O. Road"
                } else {
                    "Spawned on Blender OSM Route: ${road.name}"
                }
            )
            snapCameraToBus(updated)
        }
    }

    fun teleportToLandmark(landmarkId: String) {
        val s = _uiState.value
        if (s.roads.isEmpty()) return

        data class LandmarkSpawn(
            val roadId: String,
            val pos: Vec3,
            val headingRad: Float,
            val streetLabel: String,
            val banner: String
        )

        val spec = when (landmarkId) {
            "SAKTHAN_STAND" -> LandmarkSpawn(
                roadId = "ROAD_SAKTHAN_TO_SWARAJ",
                pos = Vec3(25.0f, 0.06f, -176.0f),
                headingRad = 0f,
                streetLabel = "Sakthan Stand • Bay 1 (PALAKKAD / COIMBATORE)",
                banner = "Sakthan Stand (Bay 1 Spawn) • Blue Roof Sheds, Clock Tower & High-Mast Floodlight"
            )
            "SAKTHAN_LEFT_ENTRY" -> LandmarkSpawn(
                roadId = "ROAD_SAKTHAN_STAND_ENTRY",
                pos = Vec3(5.5f, 0.06f, -124.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Sakthan Stand • Left Entry Gate",
                banner = "Sakthan Stand Left Entry Slip Road • Drive Straight In"
            )
            "THRISSUR_RAILWAY_TCR" -> LandmarkSpawn(
                roadId = "ROAD_STATION_ROAD",
                pos = Vec3(-18.65f, 0.06f, -114.0f),
                headingRad = 0f,
                streetLabel = "Thrissur Central Railway Station (TCR) • Station Road",
                banner = "Thrissur Central Railway Station (TCR) • 3D Station Boards, Clock Tower & Station Road"
            )
            "MANIKANDANAL_SIGNAL" -> LandmarkSpawn(
                roadId = "ROAD_SAKTHAN_TO_SWARAJ",
                pos = Vec3(19.0f, 0.06f, -49.0f),
                headingRad = -0.15f,
                streetLabel = "Manikandanal Traffic Signal • Swaraj Round South",
                banner = "Manikandanal Signal (30s Red / 25s Green / 4s Amber) & Zebra Crossing"
            )
            "SWARAJ_ROUND" -> LandmarkSpawn(
                roadId = "ROAD_SWARAJ_ROUND",
                pos = Vec3(17.5f, 0.06f, -40.5f),
                headingRad = -0.14f,
                streetLabel = "Swaraj Round (1-Way Clockwise) • Vadakkumnathan Temple",
                banner = "Swaraj Round (4-Lane 14m 1-Way), Thekkinkadu Maidan & Vadakkumnathan Temple"
            )
            "VADAKKE_STAND" -> LandmarkSpawn(
                roadId = "ROAD_SWARAJ_TO_VADAKKE",
                pos = Vec3(-24.0f, 0.06f, 184.0f),
                headingRad = 0f,
                streetLabel = "Vadakke Stand • Thrissur North Bus Stand",
                banner = "Vadakke Stand • Bays, Platforms, Shelter & Entry/Exit Gates"
            )
            "GURUVAYUR_TEMPLE" -> LandmarkSpawn(
                roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
                pos = Vec3(-2662.0f, 0.06f, 1260.0f),
                headingRad = (PI * 0.5).toFloat(),
                streetLabel = "Guruvayur Temple • East Nada",
                banner = "Guruvayur Sree Krishna Temple East Nada & Golden Flagstaff"
            )
            "GURUVAYUR_STAND" -> LandmarkSpawn(
                roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
                pos = Vec3(-2718.0f, 0.06f, 1260.0f),
                headingRad = (-PI * 0.5).toFloat(),
                streetLabel = "Guruvayur Municipal Bus Stand",
                banner = "Guruvayur Bus Stand • Road-Aligned Left Entry & Right Exit"
            )
            "CHAVAKKAD_BEACH" -> LandmarkSpawn(
                roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
                pos = Vec3(-4500.0f, 0.06f, 1132.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Chavakkad Beach & Azhi Estuary",
                banner = "Chavakkad Beach Coastal Drive, Lighthouse & Beach Bus Stand"
            )
            "TRIPRAYAR_BEACH" -> LandmarkSpawn(
                roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
                pos = Vec3(-2220.0f, 0.06f, -2355.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Triprayar - Nattika Beach Terminus",
                banner = "Triprayar - Nattika Beach Promenade & NH 66 Bus Stand"
            )
            "PALIYEKKARA_TOLL" -> LandmarkSpawn(
                roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
                pos = Vec3(996.7f, 0.06f, -1772.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Paliyekkara Toll Plaza • NH 544",
                banner = "Paliyekkara Toll Plaza (10-Lane NETC FASTag Kerala Roof Canopy)"
            )
            else -> return
        }

        val roadIdx = s.roads.indexOfFirst { it.id == spec.roadId }.coerceAtLeast(0)
        targetWheelAngleDeg = 0f
        displayedWheelAngleDeg = 0f
        smoothedYawRate = 0f
        isSteeringPointerHeld = false
        steerReleaseHoldSec = 0f
        smoothedWheelSteerInput = 0f
        smoothedFrontWheelRad = 0f
        smoothedThrottle = 0f
        smoothedBrake = 0f
        smoothedDisplayRpm = if (s.engineRunning) 650f else 0f
        _uiState.update { current ->
            val updated = current.copy(
                activeRoadIndex = roadIdx,
                busPos = spec.pos,
                busHeadingRad = spec.headingRad,
                speedKmh = 0f,
                signedSpeedMps = 0f,
                engineRpm = if (current.engineRunning) 650 else 0,
                displayGearNumber = 1,
                steerInput = 0f,
                frontWheelSteerRad = 0f,
                steeringWheelAngleDeg = 0f,
                isSteeringHeld = false,
                gear = TransmissionGear.Drive,
                isParkBrakeActive = false,
                currentRoadStreetName = spec.streetLabel,
                showRouteModal = false,
                statusBanner = spec.banner
            )
            snapCameraToBus(updated)
        }
    }

    fun resetBusToActiveCorridorStart() {
        teleportToRoad(_uiState.value.activeRoadIndex)
    }

    private fun snapCameraToBus(state: SimUiState): SimUiState {
        val (focus, eye, fov) = computeDesiredCameraRig(
            busPos = state.busPos,
            busHeadingRad = state.busHeadingRad,
            followYawRad = state.busHeadingRad,
            steerInput = state.steerInput,
            speedKmh = state.speedKmh,
            cameraMode = state.cameraMode,
            rearViewActive = state.rearViewActive,
            orbitYawDeg = state.cameraOrbitYawDeg,
            orbitPitchDeg = state.cameraOrbitPitchDeg
        )
        return state.copy(
            camFollowYawRad = state.busHeadingRad,
            smoothedOrbitYawDeg = state.cameraOrbitYawDeg,
            smoothedOrbitPitchDeg = state.cameraOrbitPitchDeg,
            camFocusPos = focus,
            camEyePos = eye,
            cameraFovDeg = fov
        )
    }

    private fun computeDesiredCameraRig(
        busPos: Vec3,
        busHeadingRad: Float,
        followYawRad: Float,
        steerInput: Float,
        speedKmh: Float,
        cameraMode: CameraViewMode,
        rearViewActive: Boolean,
        orbitYawDeg: Float,
        orbitPitchDeg: Float
    ): Triple<Vec3, Vec3, Float> {
        val busSin = sin(busHeadingRad)
        val busCos = cos(busHeadingRad)
        fun busLocalToWorld(lx: Float, ly: Float, lz: Float): Vec3 = Vec3(
            x = busPos.x + lx * busCos + lz * busSin,
            y = busPos.y + ly,
            z = busPos.z - lx * busSin + lz * busCos
        )

        val rearYawOffset = if (rearViewActive) PI.toFloat() else 0f

        return when (cameraMode) {
            CameraViewMode.Chase -> {
                val orbitYawRad = followYawRad + rearYawOffset
                val targetLocalX = 0f
                val targetLocalY = 2.02f
                val targetLocalZ = if (rearViewActive) -2.40f else 3.20f
                val focusPos = busLocalToWorld(targetLocalX, targetLocalY, targetLocalZ)

                val orbitDist = 13.0f
                val elevDeg = 7.8f
                val pitchRad = Math.toRadians(elevDeg.toDouble()).toFloat()
                val cosP = cos(pitchRad)
                val sinP = sin(pitchRad)
                val sinY = sin(orbitYawRad)
                val cosY = cos(orbitYawRad)

                val horizDist = orbitDist * cosP
                val candidateEye = Vec3(
                    x = focusPos.x - sinY * horizDist,
                    y = focusPos.y + orbitDist * sinP,
                    z = focusPos.z - cosY * horizDist
                )
                val safeEye = preventRoadMeshCameraClip(
                    focusPos = focusPos,
                    candidateEye = candidateEye,
                    busPos = busPos,
                    busHeadingRad = busHeadingRad,
                    cameraMode = cameraMode
                )
                Triple(focusPos, safeEye, CINEMATIC_FOLLOW_FOV_DEG)
            }
            CameraViewMode.Cockpit -> {
                val eyePos = busLocalToWorld(0.62f, 2.28f, 4.05f)
                val lookYaw = busHeadingRad + rearYawOffset
                val elevDeg = 3.6f
                val pitchRad = Math.toRadians(elevDeg.toDouble()).toFloat()
                val cosP = cos(pitchRad)
                val sinP = sin(pitchRad)
                val focusPos = Vec3(
                    x = eyePos.x + sin(lookYaw) * cosP * 18.0f,
                    y = eyePos.y - sinP * 18.0f,
                    z = eyePos.z + cos(lookYaw) * cosP * 18.0f
                )
                Triple(focusPos, eyePos, CINEMATIC_COCKPIT_FOV_DEG)
            }
            CameraViewMode.Orbit -> {
                val orbitYawRad = followYawRad + rearYawOffset +
                    Math.toRadians(orbitYawDeg.toDouble()).toFloat()
                val focusPos = busLocalToWorld(0f, 1.72f, 0.60f)
                val orbitDist = 11.8f
                val elevDeg = (9.5f + orbitPitchDeg).coerceIn(2.0f, 31.5f)
                val pitchRad = Math.toRadians(elevDeg.toDouble()).toFloat()
                val cosP = cos(pitchRad)
                val sinP = sin(pitchRad)
                val sinY = sin(orbitYawRad)
                val cosY = cos(orbitYawRad)

                val horizDist = orbitDist * cosP
                val candidateEye = Vec3(
                    x = focusPos.x - sinY * horizDist,
                    y = focusPos.y + orbitDist * sinP,
                    z = focusPos.z - cosY * horizDist
                )
                val safeEye = preventRoadMeshCameraClip(
                    focusPos = focusPos,
                    candidateEye = candidateEye,
                    busPos = busPos,
                    busHeadingRad = busHeadingRad,
                    cameraMode = cameraMode
                )
                Triple(focusPos, safeEye, CINEMATIC_FOLLOW_FOV_DEG)
            }
        }
    }

    private fun smoothDampAlpha(ratePerSec: Float, dt: Float): Float {
        if (ratePerSec <= 0f || dt <= 0f) return 0f
        return (1f - exp(-ratePerSec * dt)).coerceIn(0f, 1f)
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t.coerceIn(0f, 1f)
    }

    private fun lerpVec3(start: Vec3, end: Vec3, t: Float): Vec3 {
        val alpha = t.coerceIn(0f, 1f)
        return Vec3(
            x = start.x + (end.x - start.x) * alpha,
            y = start.y + (end.y - start.y) * alpha,
            z = start.z + (end.z - start.z) * alpha
        )
    }

    private fun lerpAngleRad(fromRad: Float, toRad: Float, t: Float): Float {
        val twoPi = (2.0 * PI).toFloat()
        var diff = (toRad - fromRad) % twoPi
        if (diff > PI.toFloat()) diff -= twoPi
        if (diff < -PI.toFloat()) diff += twoPi
        var out = fromRad + diff * t.coerceIn(0f, 1f)
        while (out > PI.toFloat()) out -= twoPi
        while (out < -PI.toFloat()) out += twoPi
        return out
    }

    private fun lerpAngleDeg(fromDeg: Float, toDeg: Float, t: Float): Float {
        var diff = (toDeg - fromDeg) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        var out = fromDeg + diff * t.coerceIn(0f, 1f)
        while (out > 180f) out -= 360f
        while (out < -180f) out += 360f
        return out
    }

    companion object {
        fun computeRoadSurfaceFloorHeight(x: Float, z: Float): Float {
            var surfaceY = 0.08f
            // Sakthan Thampuran Stand boarding bays & 250mm raised curbs
            if (x in -24f..50f && z in -222f..-134f) {
                surfaceY = max(surfaceY, 0.31f)
            }
            // Thrissur Central Railway Station (TCR) platforms
            if (x in -76f..-45f && z in -165f..-45f) {
                surfaceY = max(surfaceY, 0.36f)
            }
            // Vadakke Stand boarding platforms
            if (x in -68f..0f && z in 176f..258f) {
                surfaceY = max(surfaceY, 0.34f)
            }
            // Swaraj Round Maidan & Vadakkumnathan raised stone terrace
            if (hypot(x - 3.1f, z - 9.5f) < 68f) {
                surfaceY = max(surfaceY, 0.46f)
            }
            // Guruvayur Temple Nada & Municipal Bus Stand platforms
            if (x in -2765f..-2610f && z in 1225f..1310f) {
                surfaceY = max(surfaceY, 0.34f)
            }
            // Paliyekkara Toll Plaza concrete toll islands
            if (x in 970f..1025f && z in -1845f..-1765f) {
                surfaceY = max(surfaceY, 0.30f)
            }
            return surfaceY
        }

        private fun computeCanopyCeilingHeight(x: Float, z: Float): Float {
            // Sakthan Thampuran Stand canopies & entry/exit gantry
            if (x in -25f..53f && z in -221f..-137f) {
                return 5.20f
            }
            // Vadakke Stand shelter & gantry
            if (x in -68f..0f && z in 196f..252f) {
                return 5.05f
            }
            // Guruvayur Stand shelter & gantry
            if (x in -2782f..-2688f && z in 1265f..1318f) {
                return 5.05f
            }
            // Triprayar Stand shelter & gantry
            if (x in -2214f..-2178f && z in -2446f..-2374f) {
                return 5.05f
            }
            // Paliyekkara Toll Plaza canopy
            if (x in 966f..1033f && z in -1816f..-1796f) {
                return 5.05f
            }
            return 1000f
        }

        fun preventRoadMeshCameraClip(
            focusPos: Vec3,
            candidateEye: Vec3,
            busPos: Vec3,
            busHeadingRad: Float,
            cameraMode: CameraViewMode
        ): Vec3 {
            var eyeX = candidateEye.x
            var eyeY = candidateEye.y
            var eyeZ = candidateEye.z

            if (cameraMode == CameraViewMode.Chase || cameraMode == CameraViewMode.Orbit) {
                var maxRoadFloorAlongRay = computeRoadSurfaceFloorHeight(eyeX, eyeZ)
                var minCanopyCeilingAlongRay = min(
                    computeCanopyCeilingHeight(eyeX, eyeZ),
                    computeCanopyCeilingHeight(busPos.x, busPos.z)
                )
                val raySamples = 4
                for (i in 1..raySamples) {
                    val frac = i.toFloat() / raySamples.toFloat()
                    val sx = focusPos.x + (eyeX - focusPos.x) * frac
                    val sz = focusPos.z + (eyeZ - focusPos.z) * frac
                    val h = computeRoadSurfaceFloorHeight(sx, sz)
                    if (h > maxRoadFloorAlongRay) {
                        maxRoadFloorAlongRay = h
                    }
                    val c = computeCanopyCeilingHeight(sx, sz)
                    if (c < minCanopyCeilingAlongRay) {
                        minCanopyCeilingAlongRay = c
                    }
                }
                val minAllowedY = maxRoadFloorAlongRay + MIN_ROAD_CAMERA_CLEARANCE_M
                val maxAllowedY = (minCanopyCeilingAlongRay - 0.65f).coerceAtLeast(minAllowedY + 0.5f)
                eyeY = eyeY.coerceIn(minAllowedY, maxAllowedY)

                val dx = eyeX - busPos.x
                val dz = eyeZ - busPos.z
                val sinH = sin(busHeadingRad)
                val cosH = cos(busHeadingRad)
                val localX = dx * cosH - dz * sinH
                val localZ = dx * sinH + dz * cosH
                val halfWidthMargin = 1.65f
                val halfLengthMargin = 6.55f
                if (abs(localX) < halfWidthMargin && abs(localZ) < halfLengthMargin && eyeY < busPos.y + 3.85f) {
                    val pushZ = if (localZ >= 0f) halfLengthMargin + 1.2f else -(halfLengthMargin + 1.2f)
                    eyeX = busPos.x + localX * cosH + pushZ * sinH
                    eyeZ = busPos.z - localX * sinH + pushZ * cosH
                }
            } else {
                val floorY = computeRoadSurfaceFloorHeight(eyeX, eyeZ) + 1.60f
                if (eyeY < floorY) {
                    eyeY = floorY
                }
            }

            return Vec3(eyeX, eyeY, eyeZ)
        }
    }

    fun triggerSteeringHorn() {
        viewModelScope.launch(Dispatchers.Default) {
            playSteeringHorn()
        }
    }

    fun triggerAirHorn() {
        activeHornBlasts.incrementAndGet()
        _uiState.update { it.copy(hornActive = true) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                playLongBusAirHorn()
            } finally {
                if (activeHornBlasts.decrementAndGet() <= 0) {
                    activeHornBlasts.set(0)
                    _uiState.update { it.copy(hornActive = false) }
                }
            }
        }
    }

    private fun triggerBrakeApplySound(speedKmh: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            playPneumaticBrakeApply(speedKmh)
        }
    }

    private fun triggerAirBrakeReleaseSound() {
        viewModelScope.launch(Dispatchers.Default) {
            playPneumaticBrakeReleaseDump()
        }
    }

    private fun triggerFlasherRelaySound(isHazard: Boolean, isTick: Boolean) {
        relayAudioJob?.cancel()
        relayAudioJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val pcm = when {
                    isHazard && isTick -> cachedHazardTickPcm
                    isHazard && !isTick -> cachedHazardTockPcm
                    !isHazard && isTick -> cachedIndicatorTickPcm
                    else -> cachedIndicatorTockPcm
                }
                playStaticPcmTrack(pcm, 22050, if (isHazard) 70L else 55L)
            } catch (_: Throwable) {
            }
        }
    }

    private fun triggerWiperStrokeSound(mode: WiperSpeedMode) {
        if (mode == WiperSpeedMode.Off) return
        wiperAudioJob?.cancel()
        wiperAudioJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val (pcm, durMs) = when (mode) {
                    WiperSpeedMode.Low -> cachedWiperLowStrokePcm to 440L
                    WiperSpeedMode.Mid -> cachedWiperMidStrokePcm to 310L
                    WiperSpeedMode.High -> cachedWiperHighStrokePcm to 220L
                    WiperSpeedMode.Off -> return@launch
                }
                playStaticPcmTrack(pcm, 22050, durMs)
            } catch (_: Throwable) {
            }
        }
    }

    private fun ensureEngineAudioStreamStarted() {
        if (engineAudioJob?.isActive == true) return
        engineAudioJob = viewModelScope.launch(Dispatchers.Default) {
            runContinuousDieselEngineSynthLoop()
        }
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.runContinuousDieselEngineSynthLoop() {
        val sampleRate = 22050
        val chunkSamples = 441
        val pcmChunk = ShortArray(chunkSamples)

        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(chunkSamples * 4)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBufBytes, chunkSamples * 6))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Throwable) {
            return
        }

        try {
            track.play()

            var smoothRpm = 520.0
            var smoothLoad = 0.0
            var smoothSpeed = 0.0
            var smoothGain = 0.0
            var cyclePos6 = 0.0
            val cylPulseWeights = doubleArrayOf(1.18, 0.91, 1.05, 0.94, 1.11, 0.89)

            var subBassLp = 0.0
            var mufflerLowLp1 = 0.0
            var mufflerLowLp2 = 0.0
            var throatBandLp = 0.0
            var throatBandHp = 0.0
            var knockBandLp = 0.0
            var knockBandHp = 0.0
            var fanRushLp = 0.0
            var noiseSeed = 0x7F4A7C15u

            while (isActive) {
                val tgtRpm = synthTargetRpm.toDouble()
                val tgtLoad = synthTargetLoad.toDouble()
                val tgtSpeed = synthTargetSpeedNorm.toDouble()
                val tgtGain = synthTargetGain.toDouble()

                for (i in 0 until chunkSamples) {
                    smoothRpm += (tgtRpm - smoothRpm) * 0.00042
                    smoothLoad += (tgtLoad - smoothLoad) * 0.00075
                    smoothSpeed += (tgtSpeed - smoothSpeed) * 0.00050
                    smoothGain += (tgtGain - smoothGain) * 0.00105

                    if (smoothGain < 0.0015) {
                        pcmChunk[i] = 0
                        continue
                    }

                    val fFire = (smoothRpm / 60.0) * 3.0
                    cyclePos6 += fFire / sampleRate
                    if (cyclePos6 >= 6.0) cyclePos6 -= 6.0

                    val cylIdx = cyclePos6.toInt().coerceIn(0, 5)
                    val u = cyclePos6 - cylIdx.toDouble()
                    val cylWeight = cylPulseWeights[cylIdx]

                    noiseSeed = noiseSeed * 1664525u + 1013904223u
                    val white = ((noiseSeed shr 16).toInt() / 32768.0) - 1.0

                    val attack = (u / 0.085).coerceIn(0.0, 1.0)
                    val blowdownEnv = attack * kotlin.math.exp(-u * 4.4)
                    val asymmetricSaw = (1.0 - 2.0 * u) * (1.0 - 0.35 * u)
                    val rawBlowdown = (asymmetricSaw * 0.85 + blowdownEnv * 1.35) * cylWeight

                    val manifoldDrive = 1.45 + smoothLoad * 1.35 + smoothSpeed * 0.65
                    val xManifold = rawBlowdown * manifoldDrive
                    val shapedExhaust = xManifold / (1.0 + abs(xManifold) * 0.72)

                    val gasChuff = white * blowdownEnv * (0.42 + smoothLoad * 0.45)
                    subBassLp += ((shapedExhaust + gasChuff) - subBassLp) * 0.032
                    val mufflerCut = (0.055 + smoothSpeed * 0.065 + smoothLoad * 0.045).coerceIn(0.05, 0.17)
                    mufflerLowLp1 += ((shapedExhaust + gasChuff) - mufflerLowLp1) * mufflerCut
                    mufflerLowLp2 += (mufflerLowLp1 - mufflerLowLp2) * mufflerCut

                    val throatExcite = shapedExhaust * (0.60 + smoothLoad * 0.75) +
                        white * blowdownEnv * (0.55 + smoothLoad * 0.65 + smoothSpeed * 0.35)
                    throatBandLp += (throatExcite - throatBandLp) * (0.115 + smoothSpeed * 0.055)
                    throatBandHp += (throatBandLp - throatBandHp) * 0.038
                    val throatGrowl = (throatBandLp - throatBandHp) * (0.85 + smoothLoad * 0.45 + smoothSpeed * 0.35)

                    val knockEnv = kotlin.math.exp(-u * 14.5) * cylWeight
                    val knockExcite = (white * 0.78 + (1.0 - 2.0 * u) * 0.42) * knockEnv
                    knockBandLp += (knockExcite - knockBandLp) * 0.24
                    knockBandHp += (knockBandLp - knockBandHp) * 0.095
                    val dieselClatter = (knockBandLp - knockBandHp) * (0.52 + smoothLoad * 0.48 + smoothSpeed * 0.25)

                    fanRushLp += (white - fanRushLp) * 0.065
                    val coolingFanRoar = fanRushLp * (0.04 + smoothSpeed * 0.16 + smoothLoad * 0.08)

                    val busEngineMix = (
                        subBassLp * 0.52 +
                        mufflerLowLp2 * 0.68 +
                        throatGrowl * 0.64 +
                        dieselClatter * 0.46 +
                        coolingFanRoar
                    ) * smoothGain

                    val limited = busEngineMix / (1.0 + abs(busEngineMix) * 0.35)
                    pcmChunk[i] = (limited.coerceIn(-0.98, 0.98) * Short.MAX_VALUE).toInt().toShort()
                }

                val written = track.write(pcmChunk, 0, chunkSamples)
                if (written < 0) break
            }
        } catch (_: Throwable) {
        } finally {
            try {
                track.stop()
            } catch (_: Throwable) {
            }
            try {
                track.release()
            } catch (_: Throwable) {
            }
        }
    }

    override fun onCleared() {
        engineStartJob?.cancel()
        engineAudioJob?.cancel()
        wiperAudioJob?.cancel()
        relayAudioJob?.cancel()
        super.onCleared()
    }

    private val cachedBrakeApplyPcm: ShortArray by lazy { buildBrakeApplyPcm() }
    private val cachedBrakeReleasePcm: ShortArray by lazy { buildBrakeReleasePcm() }
    private val cachedHornPcm: ShortArray by lazy { buildHornPcm() }
    private val cachedLongBusHornPcm: ShortArray by lazy { buildLongBusHornPcm() }
    private val cachedEngineStartPcm: ShortArray by lazy { buildEngineStartPcm() }
    private val cachedEngineStopPcm: ShortArray by lazy { buildEngineStopPcm() }
    private val cachedIndicatorTickPcm: ShortArray by lazy { buildRelayClickPcm(isHazard = false, isTick = true) }
    private val cachedIndicatorTockPcm: ShortArray by lazy { buildRelayClickPcm(isHazard = false, isTick = false) }
    private val cachedHazardTickPcm: ShortArray by lazy { buildRelayClickPcm(isHazard = true, isTick = true) }
    private val cachedHazardTockPcm: ShortArray by lazy { buildRelayClickPcm(isHazard = true, isTick = false) }
    private val cachedWiperLowStrokePcm: ShortArray by lazy { buildWiperStrokePcm(durationSec = 0.42, motorBaseHz = 92.0, sweepGain = 0.44, motorGain = 0.34) }
    private val cachedWiperMidStrokePcm: ShortArray by lazy { buildWiperStrokePcm(durationSec = 0.29, motorBaseHz = 124.0, sweepGain = 0.54, motorGain = 0.44) }
    private val cachedWiperHighStrokePcm: ShortArray by lazy { buildWiperStrokePcm(durationSec = 0.20, motorBaseHz = 162.0, sweepGain = 0.66, motorGain = 0.56) }

    private fun buildBrakeApplyPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 0.34
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var noiseSeed = 0x5A17C93u
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            noiseSeed = noiseSeed * 1664525u + 1013904223u
            val white = ((noiseSeed shr 16).toInt() / 32768.0) - 1.0
            val hissEnv = kotlin.math.exp(-t * 9.5)
            val drumLow = sin(2.0 * PI * (155.0 - 35.0 * progress) * t) * 0.36
            val squeal = sin(2.0 * PI * (2420.0 - 180.0 * progress) * t) * 0.08
            val fadeOut = if (progress > 0.75) (1.0 - progress) / 0.25 else 1.0
            val sample = (white * 0.52 * hissEnv + (drumLow + squeal) * fadeOut)
            pcm[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.52).toInt().toShort()
        }
        return pcm
    }

    private fun buildBrakeReleasePcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 0.38
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var noiseSeed = 0x9E3779B9u
        var filtered = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            noiseSeed = noiseSeed * 1664525u + 1013904223u
            val white = ((noiseSeed shr 16).toInt() / 32768.0) - 1.0
            filtered = 0.68 * filtered + 0.32 * white
            val attack = (t / 0.014).coerceIn(0.0, 1.0)
            val decay = kotlin.math.exp(-t * 7.8)
            val valvePing = sin(2.0 * PI * (940.0 - 380.0 * t) * t) * kotlin.math.exp(-t * 22.0) * 0.28
            val sample = (filtered * 0.85 + valvePing) * attack * decay
            pcm[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.58).toInt().toShort()
        }
        return pcm
    }

    private fun buildHornPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 0.44
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        val fLow = 349.23
        val fHigh = 440.0
        var lpState = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val remaining = durationSec - t
            val attack = (t / 0.014).coerceIn(0.0, 1.0)
            val release = (remaining / 0.045).coerceIn(0.0, 1.0)
            val env = attack * release
            val settle = 1.0 + 0.012 * kotlin.math.exp(-t * 45.0)

            fun hornVoice(freq: Double): Double {
                val w = 2.0 * PI * freq * settle * t
                return sin(w) * 0.56 +
                    sin(2.0 * w) * 0.34 +
                    sin(3.0 * w) * 0.22 +
                    sin(4.0 * w) * 0.14 +
                    sin(5.0 * w) * 0.08 +
                    sin(6.0 * w) * 0.04
            }

            val rawDual = hornVoice(fLow) * 0.54 + hornVoice(fHigh) * 0.46
            val saturated = rawDual / (1.0 + abs(rawDual) * 0.38)
            lpState += (saturated - lpState) * 0.46
            pcm[i] = ((lpState * env).coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.72).toInt().toShort()
        }
        return pcm
    }

    private fun buildLongBusHornPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 1.35
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var lpState = 0.0
        var noiseSeed = 0x3C6EF372u

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val remaining = durationSec - t
            val attack = (t / 0.032).coerceIn(0.0, 1.0)
            val release = (remaining / 0.160).coerceIn(0.0, 1.0)
            val env = attack * release
            val pitchSweep = 1.0 - 0.035 * kotlin.math.exp(-t * 22.0)
            val reedFlutter = 1.0 + 0.004 * sin(2.0 * PI * 14.0 * t)
            val pitchMod = pitchSweep * reedFlutter

            fun airTrumpet(freq: Double): Double {
                val w = 2.0 * PI * freq * pitchMod * t
                return sin(w) * 0.52 +
                    sin(2.0 * w) * 0.38 +
                    sin(3.0 * w) * 0.26 +
                    sin(4.0 * w) * 0.17 +
                    sin(5.0 * w) * 0.10 +
                    sin(6.0 * w) * 0.06
            }

            noiseSeed = noiseSeed * 1664525u + 1013904223u
            val airNoise = (((noiseSeed shr 16).toInt() / 32768.0) - 1.0) * 0.09 * kotlin.math.exp(-t * 8.0)

            val rawTrumpet = airTrumpet(196.0) * 0.50 +
                airTrumpet(246.94) * 0.36 +
                airTrumpet(293.66) * 0.14 +
                airNoise

            val saturated = rawTrumpet / (1.0 + abs(rawTrumpet) * 0.42)
            lpState += (saturated - lpState) * 0.38
            pcm[i] = ((lpState * env).coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.78).toInt().toShort()
        }
        return pcm
    }

    private fun playPneumaticBrakeApply(@Suppress("UNUSED_PARAMETER") speedKmh: Float) {
        try {
            playStaticPcmTrack(cachedBrakeApplyPcm, 22050, 350L)
        } catch (_: Throwable) {
        }
    }

    private fun playPneumaticBrakeReleaseDump() {
        try {
            playStaticPcmTrack(cachedBrakeReleasePcm, 22050, 390L)
        } catch (_: Throwable) {
        }
    }

    private fun playSteeringHorn() {
        try {
            playStaticPcmTrack(cachedHornPcm, 22050, 450L)
        } catch (_: Throwable) {
        }
    }

    private fun playLongBusAirHorn() {
        try {
            playStaticPcmTrack(cachedLongBusHornPcm, 22050, 1360L)
        } catch (_: Throwable) {
        }
    }

    private fun playEngineStartSound() {
        try {
            playStaticPcmTrack(cachedEngineStartPcm, 22050, 1260L)
        } catch (_: Throwable) {
        }
    }

    private fun playEngineStopSound() {
        try {
            playStaticPcmTrack(cachedEngineStopPcm, 22050, 520L)
        } catch (_: Throwable) {
        }
    }

    private fun buildEngineStartPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 1.25
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)

        var seed = 0x517CC1B7u
        var cylCycle = 0.0
        var starterPhase = 0.0
        var lowLp = 0.0
        var throatLp = 0.0
        var throatHp = 0.0
        var gearLp = 0.0
        var gearHp = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0

            var mix = 0.0
            if (t < 0.085) {
                val clickDecay = kotlin.math.exp(-t * 95.0)
                val heavyClack = (sin(2.0 * PI * 285.0 * t) * 0.55 +
                    sin(2.0 * PI * 890.0 * t) * 0.35 +
                    white * 0.45) * clickDecay
                mix += heavyClack * 0.68
            }

            if (t in 0.04..0.82) {
                val crankFadeOut = if (t > 0.72) ((0.82 - t) / 0.10).coerceIn(0.0, 1.0) else 1.0
                val crankFadeIn = ((t - 0.04) / 0.025).coerceIn(0.0, 1.0)
                val crankEnv = crankFadeIn * crankFadeOut

                val strokePhase = (t - 0.05) * 4.45
                val frac = strokePhase - kotlin.math.floor(strokePhase)
                val compressionLoad = kotlin.math.exp(-((frac - 0.35) * (frac - 0.35)) / 0.055)

                val starterHz = 365.0 - compressionLoad * 72.0 + t * 55.0
                starterPhase += starterHz / sampleRate
                val gearSaw = 2.0 * (starterPhase - kotlin.math.floor(starterPhase)) - 1.0
                val starterWhineRaw = gearSaw * 0.65 + sin(2.0 * PI * starterPhase * 2.0) * 0.25
                gearLp += (starterWhineRaw - gearLp) * 0.22
                gearHp += (gearLp - gearHp) * 0.09
                val planetaryGear = (gearLp - gearHp) * (0.28 + 0.18 * (1.0 - compressionLoad))

                val chugPulse = kotlin.math.exp(-frac * 6.5) * 0.90 +
                    (white * kotlin.math.exp(-frac * 8.0) * 0.42)
                mix += (planetaryGear + chugPulse * 0.55) * crankEnv * 0.72
            }

            if (t >= 0.72) {
                val postIgn = t - 0.72
                val flareEnv = (postIgn / 0.06).coerceIn(0.0, 1.0)
                val settleRpm = 620.0 + 430.0 * kotlin.math.exp(-postIgn * 4.2)
                val fireHz = (settleRpm / 60.0) * 3.0
                cylCycle += fireHz / sampleRate
                if (cylCycle >= 6.0) cylCycle -= 6.0
                val u = cylCycle - cylCycle.toInt().toDouble()

                val blowdown = ((1.0 - 2.0 * u) * 0.8 + (u / 0.09).coerceIn(0.0, 1.0) * kotlin.math.exp(-u * 4.4) * 1.35)
                val shaped = blowdown * 1.9 / (1.0 + abs(blowdown * 1.9) * 0.65)
                val ignitionBark = white * kotlin.math.exp(-u * 13.0) * (0.55 + 0.45 * kotlin.math.exp(-postIgn * 5.0))

                lowLp += ((shaped + ignitionBark * 0.35) - lowLp) * 0.095
                throatLp += ((shaped * 0.75 + ignitionBark) - throatLp) * 0.19
                throatHp += (throatLp - throatHp) * 0.055
                val flareMix = (lowLp * 0.78 + (throatLp - throatHp) * 0.72) * flareEnv

                val handoff = if (t > 1.05) ((1.25 - t) / 0.20).coerceIn(0.0, 1.0) else 1.0
                mix += flareMix * handoff * 0.85
            }

            val limited = mix / (1.0 + abs(mix) * 0.35)
            pcm[i] = (limited.coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.82).toInt().toShort()
        }
        return pcm
    }

    private fun buildEngineStopPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 0.50
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var seed = 0x91E10DA5u
        var lp = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0

            val solClick = sin(2.0 * PI * 420.0 * t) * kotlin.math.exp(-t * 95.0) * 0.45
            val shudder1 = sin(2.0 * PI * 68.0 * t) * kotlin.math.exp(-((t - 0.08) * (t - 0.08)) / 0.0022)
            val shudder2 = sin(2.0 * PI * 54.0 * t) * kotlin.math.exp(-((t - 0.23) * (t - 0.23)) / 0.0032) * 0.75
            lp += (white - lp) * (0.09 - t * 0.12).coerceIn(0.02, 0.09)
            val spinDown = lp * kotlin.math.exp(-t * 4.5) * 0.42
            val tailFade = ((durationSec - t) / 0.07).coerceIn(0.0, 1.0)
            val sample = (solClick + (shudder1 + shudder2) * 0.62 + spinDown) * tailFade
            pcm[i] = (sample.coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.74).toInt().toShort()
        }
        return pcm
    }

    private fun buildRelayClickPcm(isHazard: Boolean, isTick: Boolean): ShortArray {
        val sampleRate = 22050
        val durationSec = if (isHazard) 0.058 else 0.044
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var seed = if (isTick) 0x243F6A88u else 0x85A308D3u

        val primaryHz = when {
            isHazard && isTick -> 760.0
            isHazard && !isTick -> 540.0
            !isHazard && isTick -> 1240.0
            else -> 890.0
        }
        val secondaryHz = primaryHz * 2.08
        val thudHz = if (isHazard) 165.0 else 225.0
        val gain = if (isHazard) 0.74 else 0.60

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0

            val snap = white * kotlin.math.exp(-t * 780.0) * 0.65
            val armature = (sin(2.0 * PI * primaryHz * t) * 0.62 +
                sin(2.0 * PI * secondaryHz * t) * 0.30) * kotlin.math.exp(-t * 155.0)
            val thud = sin(2.0 * PI * thudHz * t) * kotlin.math.exp(-t * 105.0) * (if (isHazard) 0.58 else 0.34)

            val tailFade = ((durationSec - t) / 0.008).coerceIn(0.0, 1.0)
            val sample = (snap + armature + thud) * tailFade
            pcm[i] = (sample.coerceIn(-0.98, 0.98) * Short.MAX_VALUE * gain).toInt().toShort()
        }
        return pcm
    }

    private fun buildWiperStrokePcm(
        durationSec: Double,
        motorBaseHz: Double,
        sweepGain: Double,
        motorGain: Double
    ): ShortArray {
        val sampleRate = 22050
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var seed = 0x6A09E667u
        var motorPhase = 0.0
        var glassLp = 0.0
        var glassHp = 0.0
        var motorLp = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val prog = (t / durationSec).coerceIn(0.0, 1.0)

            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0
            val velEnv = Math.pow(sin(PI * prog).coerceIn(0.0, 1.0), 1.25)
            val pivotThump = sin(2.0 * PI * 112.0 * t) * kotlin.math.exp(-t * 72.0) * 0.42

            val motorFreq = motorBaseHz * (0.88 + 0.24 * velEnv)
            motorPhase += motorFreq / sampleRate
            val sawTooth = 2.0 * (motorPhase - kotlin.math.floor(motorPhase)) - 1.0
            val rawMotor = sawTooth * 0.55 + sin(2.0 * PI * motorPhase * 2.0) * 0.35
            motorLp += (rawMotor - motorLp) * 0.085
            val motorBody = motorLp * (0.35 + 0.65 * velEnv) * motorGain

            glassLp += (white - glassLp) * (0.16 + 0.08 * velEnv)
            glassHp += (glassLp - glassHp) * 0.055
            val squeegeeSwish = (glassLp - glassHp) * velEnv * sweepGain * 1.45

            val endFade = ((durationSec - t) / 0.028).coerceIn(0.0, 1.0)
            val startFade = (t / 0.014).coerceIn(0.0, 1.0)
            val sample = (pivotThump + motorBody + squeegeeSwish) * startFade * endFade
            pcm[i] = (sample.coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.70).toInt().toShort()
        }
        return pcm
    }

    private fun playStaticPcmTrack(pcm: ShortArray, sampleRate: Int, sleepMs: Long) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(sleepMs)
        track.release()
    }
}
