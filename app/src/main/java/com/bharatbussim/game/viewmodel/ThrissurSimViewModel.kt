package com.bharatbussim.game.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.model.Vec3
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

private const val MAX_STEERING_WHEEL_DEG = 420f
private const val CINEMATIC_FOLLOW_FOV_DEG = 58.0f
private const val CINEMATIC_COCKPIT_FOV_DEG = 80.0f
private const val MIN_ROAD_CAMERA_CLEARANCE_M = 1.42f

@Immutable
data class SimUiState(
    val roads: List<ThrissurOsmRoad> = emptyList(),
    val activeRoadIndex: Int = 1, // Default: Sakthan Stand -> M.O. Road -> Swaraj Round
    val busPos: Vec3 = Vec3(25.0f, 0.06f, -105.0f),
    val busHeadingRad: Float = 0f, // 0 = +Z (North toward Sakthan Exit Gate)
    val speedKmh: Float = 0f,
    val signedSpeedMps: Float = 0f, // Positive = forward, Negative = reverse
    val gear: TransmissionGear = TransmissionGear.Drive,
    val isParkBrakeActive: Boolean = false,
    val steerInput: Float = 0f, // -1 (full left) to +1 (full right)
    val steeringWheelAngleDeg: Float = 0f, // -420° to +420° hydraulic power steering
    val isSteeringHeld: Boolean = false,
    val throttleInput: Float = 0f,
    val brakeInput: Float = 0f,
    val autoSteerAssist: Boolean = false,
    val headlightsOn: Boolean = false,
    val wipersOn: Boolean = false,
    val wiperPhaseRad: Float = 0f,
    val hazardsOn: Boolean = false,
    val turnLeftOn: Boolean = false,
    val turnRightOn: Boolean = false,
    val blinkerPhase: Boolean = false,
    val hornActive: Boolean = false,
    val cameraMode: CameraViewMode = CameraViewMode.Chase,
    val rearViewActive: Boolean = false,
    val cameraOrbitYawDeg: Float = 0f,
    val cameraOrbitPitchDeg: Float = 0f,
    val smoothedOrbitYawDeg: Float = 0f,
    val smoothedOrbitPitchDeg: Float = 0f,
    val camFollowYawRad: Float = 0f,
    val camFocusPos: Vec3 = Vec3(25.0f, 2.18f, -173.75f),
    val camEyePos: Vec3 = Vec3(25.0f, 5.48f, -187.55f),
    val cameraFovDeg: Float = CINEMATIC_FOLLOW_FOV_DEG,
    val minimapZoom: Float = 1.0f,
    val walletInr: Int = 1250,
    val currentRoadStreetName: String = "Sakthan Thampuran Stand • Bay 07",
    val distanceToRouteEndKm: Float = 0.48f,
    val hudHidden: Boolean = false,
    val isPaused: Boolean = false,
    val doorsOpen: Boolean = false,
    val engineRpm: Int = 650,
    val displayGearNumber: Int = 1,
    val fuelLiters: Float = 67.0f,
    val showRouteModal: Boolean = false,
    val statusBanner: String = "Hino RM 2 Coach Parked in Sakthan Stand (Bay 07) • Press GAS to Depart",
    val vehicleModelName: String = "Hino RM 2 Bus (12m Rear-Engine AC Luxury Coach)",
    val vehicleCreator: String = "asrafullislamsiam (CGTrader)",
    val vehicleSourceUrl: String = "https://www.cgtrader.com/free-3d-models/vehicle/bus/hino-rm-2-bus",
    val vehicleLicense: String = "CGTrader Royalty Free License (No AI)"
)

class ThrissurSimViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SimUiState())
    val uiState: StateFlow<SimUiState> = _uiState.asStateFlow()

    private var blinkAccumulator = 0f
    private var wiperAccumulator = 0f
    private var tollPaidInSession = false
    private var smoothedWheelSteerInput = 0f
    private var smoothedDisplayRpm = 650f
    private var wasBrakingInPreviousStep = false
    private var lastRoadScanX = Float.NaN
    private var lastRoadScanZ = Float.NaN
    private val activeHornBlasts = java.util.concurrent.atomic.AtomicInteger(0)

    // Real-time continuous streaming Hino J08E / Ashok Leyland 6-cylinder heavy bus diesel engine state
    private var engineAudioJob: Job? = null
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
            // Spawn parked inside Sakthan Thampuran Bus Stand Bay 07 (X=25.0m, Z=-176.0m, facing straight North along X=25.0m toward Right Exit Gate)
            val pos = Vec3(25.0f, 0.06f, -176.0f)

            _uiState.update { current ->
                val snapped = current.copy(
                    roads = loaded,
                    activeRoadIndex = startIdx,
                    busPos = pos,
                    busHeadingRad = 0f,
                    currentRoadStreetName = "Sakthan Stand • Boarding Bay 07",
                    distanceToRouteEndKm = (startRoad?.realDistanceM ?: 480f) / 1000f,
                    statusBanner = "Hino RM 2 Coach Parked in Sakthan Stand (Bay 07) • Press GAS to Exit"
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

        blinkAccumulator += dt
        val newBlink = if (s.hazardsOn || s.turnLeftOn || s.turnRightOn) {
            ((blinkAccumulator / 0.4f).toInt() % 2) == 0
        } else false

        wiperAccumulator = if (s.wipersOn) {
            (wiperAccumulator + dt * 3.8f) % (2.0 * PI).toFloat()
        } else {
            0f
        }

        val activeRoad = s.roads.getOrElse(s.activeRoadIndex) { s.roads.first() }

        // 1. Hydraulic Power Steering Self-Centering & Assist
        var nextWheelDeg = s.steeringWheelAngleDeg
        var nextSteerInput = s.steerInput
        var effectiveThrottle = s.throttleInput

        if (s.autoSteerAssist && !s.isParkBrakeActive && s.gear == TransmissionGear.Drive) {
            effectiveThrottle = max(effectiveThrottle, 0.68f)
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
                nextSteerInput = (diff / 0.60f).coerceIn(-1f, 1f)
                nextWheelDeg = nextSteerInput * MAX_STEERING_WHEEL_DEG
            }
        } else if (!s.isSteeringHeld) {
            // Hydraulic power-steering return-to-center: faster unwind when moving, gentle unwind when stationary
            val speedBonus = (s.speedKmh / 25f).coerceIn(0f, 2.2f)
            val returnDegPerSec = (190f + speedBonus * 220f) * dt
            nextWheelDeg = when {
                nextWheelDeg > returnDegPerSec -> nextWheelDeg - returnDegPerSec
                nextWheelDeg < -returnDegPerSec -> nextWheelDeg + returnDegPerSec
                else -> 0f
            }
            nextSteerInput = (nextWheelDeg / MAX_STEERING_WHEEL_DEG).coerceIn(-1f, 1f)
        }

        // Smooth hydraulic steering rack actuator response
        val rackBlend = (dt * 14f).coerceIn(0f, 1f)
        smoothedWheelSteerInput += (nextSteerInput - smoothedWheelSteerInput) * rackBlend

        // 2. Longitudinal Dynamics (Hino RM 2 10,500 kg 6-wheel coach — Forward & Reverse)
        var speedMps = s.signedSpeedMps
        val maxFwdSpeedMps = 180f / 3.6f
        val maxRevSpeedMps = 28f / 3.6f

        if (s.isParkBrakeActive || s.gear == TransmissionGear.Park) {
            val decel = 12.0f * dt
            speedMps = when {
                speedMps > decel -> speedMps - decel
                speedMps < -decel -> speedMps + decel
                else -> 0f
            }
        } else {
            val brakeDecel = s.brakeInput * 12.5f + 0.45f
            when (s.gear) {
                TransmissionGear.Drive -> {
                    if (speedMps < 0f) {
                        // Quickly arrest any backward roll before accelerating forward
                        val stopDecel = (14.0f + s.brakeInput * 12.0f) * dt
                        speedMps = (speedMps + stopDecel).coerceAtMost(0f)
                    } else {
                        val accel = if (effectiveThrottle > 0.04f && s.brakeInput < 0.05f) {
                            effectiveThrottle * 8.2f * (1f - (speedMps / maxFwdSpeedMps).coerceIn(0f, 0.88f) * 0.42f)
                        } else 0f
                        val decel = if (speedMps > 0f) brakeDecel * dt else 0f
                        speedMps = (speedMps + accel * dt - decel).coerceIn(0f, maxFwdSpeedMps)
                    }
                }
                TransmissionGear.Reverse -> {
                    if (speedMps > 0f) {
                        // Quickly arrest any forward motion when shifted into Reverse
                        val stopDecel = (14.0f + s.brakeInput * 12.0f + effectiveThrottle * 8.0f) * dt
                        speedMps = (speedMps - stopDecel).coerceAtLeast(0f)
                    } else {
                        val revAccel = if (effectiveThrottle > 0.04f && s.brakeInput < 0.05f) {
                            -effectiveThrottle * 5.2f * (1f - (abs(speedMps) / maxRevSpeedMps).coerceIn(0f, 0.9f) * 0.35f)
                        } else 0f
                        val revDrag = if (speedMps < 0f && (effectiveThrottle <= 0.04f || s.brakeInput >= 0.05f)) {
                            brakeDecel * dt
                        } else 0f
                        speedMps = (speedMps + revAccel * dt + revDrag).coerceIn(-maxRevSpeedMps, 0f)
                    }
                }
                TransmissionGear.Neutral, TransmissionGear.Park -> {
                    val drag = (1.4f + s.brakeInput * 11.0f) * dt
                    speedMps = when {
                        speedMps > drag -> speedMps - drag
                        speedMps < -drag -> speedMps + drag
                        else -> 0f
                    }
                }
            }
        }

        // Continuous real-time Heavy Bus 6-Cylinder Diesel Engine (520 - 1,580 RPM heavy torque band, zero whistle)
        val curSpeedKmh = abs(speedMps) * 3.6f
        val speedNorm = (curSpeedKmh / 180f).coerceIn(0f, 1f)
        val isDrivingGear = !s.isParkBrakeActive &&
            (s.gear == TransmissionGear.Drive || s.gear == TransmissionGear.Reverse)
        val activeThrottle = if (isDrivingGear && s.brakeInput < 0.05f) effectiveThrottle else 0f

        if (activeThrottle > 0.02f || curSpeedKmh > 0.25f) {
            idleAudioHoldSec = 3.2f
            ensureEngineAudioStreamStarted()
        } else if (idleAudioHoldSec > 0f) {
            idleAudioHoldSec = (idleAudioHoldSec - dt).coerceAtLeast(0f)
        }

        // 6-speed Heavy Bus Gearbox across 0 - 180 km/h (1st: 0-24, 2nd: 24-50, 3rd: 50-80, 4th: 80-112, 5th: 112-145, 6th: 145-180 km/h)
        val gearThresholds = floatArrayOf(0f, 24f, 50f, 80f, 112f, 145f, 185f)
        var gearIdx = 0
        for (g in 0 until 6) {
            if (curSpeedKmh >= gearThresholds[g]) gearIdx = g
        }
        if (gearIdx != lastAudioGearIdx && curSpeedKmh > 2.5f) {
            lastAudioGearIdx = gearIdx
            gearShiftCutSec = 0.16f // Brief clutch/synchro torque cut during heavy bus gear change
        }
        if (gearShiftCutSec > 0f) {
            gearShiftCutSec = (gearShiftCutSec - dt).coerceAtLeast(0f)
        }

        val gStart = gearThresholds[gearIdx]
        val gSpan = (gearThresholds[gearIdx + 1] - gStart).coerceAtLeast(1f)
        val inGearFrac = ((curSpeedKmh - gStart) / gSpan).coerceIn(0f, 1f)

        // Heavy bus diesel RPM for audio synthesizer: deep 520 RPM idle -> 1,580 RPM max
        val baseGearRpm = 520f + gearIdx * 78f + speedNorm * 340f
        val inGearClimbRpm = inGearFrac * 360f
        val throttlePullRpm = if (gearShiftCutSec > 0f) -70f else activeThrottle * (175f + speedNorm * 95f)

        synthTargetRpm = (baseGearRpm + inGearClimbRpm + throttlePullRpm).coerceIn(500f, 1580f)

        // Tachometer display RPM (650 RPM idle -> ~2,550 RPM redline with realistic upshift drops)
        val targetDisplayRpm = when {
            s.isParkBrakeActive || s.gear == TransmissionGear.Park -> 650f
            s.gear == TransmissionGear.Neutral -> 650f + effectiveThrottle * 1450f
            s.gear == TransmissionGear.Reverse -> {
                720f + (curSpeedKmh / 28f).coerceIn(0f, 1f) * 1350f + activeThrottle * 280f
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
            activeThrottle > 0.02f || curSpeedKmh > 0.35f -> {
                // Heavy bus exhaust & drivetrain roar grows progressively louder as speed and throttle increase
                (0.28f + speedNorm * 0.44f + activeThrottle * (0.20f + speedNorm * 0.10f)).coerceIn(0.26f, 0.94f)
            }
            idleAudioHoldSec > 0f -> {
                0.13f * (idleAudioHoldSec / 3.2f).coerceIn(0f, 1f)
            }
            else -> 0f
        }

        // 3. Ackermann / Bicycle Power-Steering Kinematics (Hino RM 2 6,000 mm wheelbase, 42° terminal lock)
        val wheelbase = 6.0f
        val speedFactor = (abs(speedMps) * 3.6f / 160f).coerceIn(0f, 1f)
        val maxFrontWheelDeg = 42.0 - 30.0 * speedFactor
        val frontWheelAngleRad = smoothedWheelSteerInput * Math.toRadians(maxFrontWheelDeg).toFloat()
        val angularVel = if (abs(speedMps) > 0.03f) {
            (speedMps / wheelbase) * tan(frontWheelAngleRad)
        } else 0f

        var newHeading = s.busHeadingRad + angularVel * dt
        while (newHeading > PI.toFloat()) newHeading -= (2.0 * PI).toFloat()
        while (newHeading < -PI.toFloat()) newHeading += (2.0 * PI).toFloat()

        val vx = sin(newHeading) * speedMps
        val vz = cos(newHeading) * speedMps
        val newPos = Vec3(s.busPos.x + vx * dt, 0.06f, s.busPos.z + vz * dt)

        // 4. Track closest road & terminal bay / exit gate status (throttled to every 3.5m of movement)
        val moveDx = newPos.x - lastRoadScanX
        val moveDz = newPos.z - lastRoadScanZ
        var streetName = s.currentRoadStreetName
        var remKm = s.distanceToRouteEndKm
        var newWallet = s.walletInr
        var newBanner = s.statusBanner

        if (lastRoadScanX.isNaN() || (moveDx * moveDx + moveDz * moveDz) >= 12.25f) {
            lastRoadScanX = newPos.x
            lastRoadScanZ = newPos.z

            var closestRoadIdx = s.activeRoadIndex
            var closestFrameIdx = 0
            var minDistSq = Float.MAX_VALUE
            for (rIdx in s.roads.indices) {
                val frames = s.roads[rIdx].frames
                for (fIdx in frames.indices step 3) {
                    val fx = frames[fIdx].pos.x - newPos.x
                    val fz = frames[fIdx].pos.z - newPos.z
                    val dSq = fx * fx + fz * fz
                    if (dSq < minDistSq) {
                        minDistSq = dSq
                        closestRoadIdx = rIdx
                        closestFrameIdx = fIdx
                    }
                }
            }

            val curRoad = s.roads[closestRoadIdx]
            val curFrames = curRoad.frames
            val totalSplineLen = curFrames.lastOrNull()?.cumulativeDist?.coerceAtLeast(1f) ?: 1f
            val curSplineDist = curFrames.getOrNull(closestFrameIdx)?.cumulativeDist ?: 0f
            val fracRemaining = (1f - (curSplineDist / totalSplineLen)).coerceIn(0f, 1f)
            remKm = (curRoad.realDistanceM / 1000f) * fracRemaining

            val insideSakthanTerminal = newPos.x in -22f..48f && newPos.z in -218f..-138f
            streetName = when {
                insideSakthanTerminal -> {
                    if (newPos.z < -154f) "Sakthan Stand • Boarding Bay 07" else "Sakthan Stand • Left Entry / Right Exit"
                }
                hypot(newPos.x - 3.1f, newPos.z - 9.5f) < 62f -> "Swaraj Round • Vadakkumnathan Temple"
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

            // 5. Automatic FASTag at Paliyekkara Toll Plaza & Terminal Gate Notifications
            val distToToll = hypot(newPos.x - 998.2f, newPos.z - (-1805.2f))
            if (distToToll < 24f && !tollPaidInSession && newWallet >= 50) {
                tollPaidInSession = true
                newWallet -= 50
                newBanner = "FASTag Toll Paid: ₹50 at Paliyekkara Toll Plaza (NH 544) • Barrier Open"
            } else if (distToToll > 60f) {
                tollPaidInSession = false
            }
        }

        // 6. Camera Rig & Road-Mesh Anti-Clipping (Static Chase, Cockpit, or 360 Orbit)
        val newSpeedKmh = abs(speedMps) * 3.6f
        val yawFollowRate = when (s.cameraMode) {
            CameraViewMode.Chase, CameraViewMode.Cockpit -> 0f
            CameraViewMode.Orbit -> 10.5f
        }
        val nextFollowYawRad = if (s.cameraMode == CameraViewMode.Chase || s.cameraMode == CameraViewMode.Cockpit) {
            newHeading
        } else {
            lerpAngleRad(
                fromRad = s.camFollowYawRad,
                toRad = newHeading,
                t = (dt * yawFollowRate).coerceIn(0f, 1f)
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
            steerInput = nextSteerInput,
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

        _uiState.update {
            it.copy(
                busPos = newPos,
                busHeadingRad = newHeading,
                speedKmh = newSpeedKmh,
                signedSpeedMps = speedMps,
                engineRpm = smoothedDisplayRpm.toInt(),
                displayGearNumber = displayGearNumber,
                steerInput = nextSteerInput,
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
                statusBanner = newBanner
            )
        }
    }

    /**
     * Incremental rotary + horizontal drag power steering input.
     * Turning the wheel accumulates angle smoothly up to ±420° without jumping or flipping.
     */
    fun steerByDeltaDeg(deltaDeg: Float) {
        _uiState.update { s ->
            val nextDeg = (s.steeringWheelAngleDeg + deltaDeg).coerceIn(
                -MAX_STEERING_WHEEL_DEG,
                MAX_STEERING_WHEEL_DEG
            )
            val norm = (nextDeg / MAX_STEERING_WHEEL_DEG).coerceIn(-1f, 1f)
            s.copy(
                steeringWheelAngleDeg = nextDeg,
                steerInput = norm,
                isSteeringHeld = true,
                autoSteerAssist = false
            )
        }
    }

    fun releaseSteeringWheel() {
        _uiState.update { it.copy(isSteeringHeld = false) }
    }

    fun setSteer(value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        _uiState.update {
            it.copy(
                steerInput = clamped,
                steeringWheelAngleDeg = clamped * MAX_STEERING_WHEEL_DEG,
                isSteeringHeld = abs(clamped) > 0.001f,
                autoSteerAssist = false
            )
        }
    }

    fun setThrottle(pressed: Boolean) {
        val prev = _uiState.value
        if (pressed) {
            idleAudioHoldSec = 3.2f
            ensureEngineAudioStreamStarted()
            if (prev.throttleInput <= 0.05f && prev.isParkBrakeActive) {
                triggerAirBrakeReleaseSound()
            }
        }
        _uiState.update { s ->
            // Pressing GAS automatically releases Park Brake and shifts P -> D so accelerator never gets stuck
            val autoReleasePark = if (pressed && s.isParkBrakeActive) false else s.isParkBrakeActive
            val autoGear = if (pressed && s.gear == TransmissionGear.Park) TransmissionGear.Drive else s.gear
            val banner = if (pressed && s.isParkBrakeActive) {
                "Park Brake Auto-Released • Departing Sakthan Stand"
            } else s.statusBanner
            s.copy(
                throttleInput = if (pressed) 1f else 0f,
                isParkBrakeActive = autoReleasePark,
                gear = autoGear,
                statusBanner = banner
            )
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
        _uiState.update { it.copy(headlightsOn = !it.headlightsOn) }
    }

    fun toggleWipers() {
        _uiState.update { it.copy(wipersOn = !it.wipersOn) }
    }

    fun toggleHazards() {
        blinkAccumulator = 0f
        _uiState.update {
            val next = !it.hazardsOn
            it.copy(hazardsOn = next, turnLeftOn = false, turnRightOn = false, blinkerPhase = next)
        }
    }

    fun toggleTurnLeft() {
        blinkAccumulator = 0f
        _uiState.update {
            val next = !it.turnLeftOn
            it.copy(turnLeftOn = next, turnRightOn = false, hazardsOn = false, blinkerPhase = next)
        }
    }

    fun toggleTurnRight() {
        blinkAccumulator = 0f
        _uiState.update {
            val next = !it.turnRightOn
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
                cameraOrbitPitchDeg = (it.cameraOrbitPitchDeg + deltaPitchDeg).coerceIn(-6f, 38f)
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
        smoothedWheelSteerInput = 0f
        smoothedDisplayRpm = 650f
        _uiState.update { current ->
            val updated = current.copy(
                activeRoadIndex = index,
                busPos = Vec3(spawnFrame.pos.x, 0.06f, spawnFrame.pos.z),
                busHeadingRad = heading,
                speedKmh = 0f,
                signedSpeedMps = 0f,
                engineRpm = 650,
                displayGearNumber = 1,
                steerInput = 0f,
                steeringWheelAngleDeg = 0f,
                isSteeringHeld = false,
                gear = TransmissionGear.Drive,
                isParkBrakeActive = false,
                currentRoadStreetName = if (isSakthanStart) {
                    "Sakthan Stand • Boarding Bay 07"
                } else {
                    road.stepNames.firstOrNull() ?: road.name
                },
                distanceToRouteEndKm = road.realDistanceM / 1000f,
                showRouteModal = false,
                statusBanner = if (isSakthanStart) {
                    "Parked in Sakthan Stand (Bay 07) • Press GAS to Exit via Right Gate"
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
                streetLabel = "Sakthan Stand • Boarding Bay 07",
                banner = "Sakthan Stand (20 Bays) • Left Gate Entry & Right Gate Exit Aligned"
            )
            "SAKTHAN_LEFT_ENTRY" -> LandmarkSpawn(
                roadId = "ROAD_SAKTHAN_STAND_ENTRY",
                pos = Vec3(5.5f, 0.06f, -124.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Sakthan Stand • Left Entry Gate",
                banner = "Sakthan Stand Left Entry Slip Road • Drive Straight In"
            )
            "SWARAJ_ROUND" -> LandmarkSpawn(
                roadId = "ROAD_SWARAJ_ROUND",
                pos = Vec3(17.5f, 0.06f, -40.5f),
                headingRad = -0.14f,
                streetLabel = "Swaraj Round • Vadakkumnathan Temple",
                banner = "Swaraj Round & Vadakkumnathan Temple South Gopuram"
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
        smoothedWheelSteerInput = 0f
        smoothedDisplayRpm = 650f
        _uiState.update { current ->
            val updated = current.copy(
                activeRoadIndex = roadIdx,
                busPos = spec.pos,
                busHeadingRad = spec.headingRad,
                speedKmh = 0f,
                signedSpeedMps = 0f,
                engineRpm = 650,
                displayGearNumber = 1,
                steerInput = 0f,
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
        val speedNorm = (speedKmh / 180f).coerceIn(0f, 1f)

        return when (cameraMode) {
            CameraViewMode.Chase -> {
                // Strictly static rear follow view locked rigidly to bus heading (zero touch orbit, zero sway)
                val orbitYawRad = followYawRad + rearYawOffset
                val targetLocalX = 0f
                val targetLocalY = 1.55f
                val targetLocalZ = if (rearViewActive) -2.10f else 0.80f
                val focusPos = busLocalToWorld(targetLocalX, targetLocalY, targetLocalZ)

                val orbitDist = 9.8f
                val elevDeg = 3.2f
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
                // Strictly static driver eye position looking forward through windshield and mirrors
                val eyePos = busLocalToWorld(0.62f, 2.28f, 4.05f)
                val lookYaw = busHeadingRad + rearYawOffset
                val elevDeg = 4.2f
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
                // 360-degree free exterior orbit mode with touch drag
                val orbitYawRad = followYawRad + rearYawOffset +
                    Math.toRadians(orbitYawDeg.toDouble()).toFloat()
                val focusPos = busLocalToWorld(0f, 1.65f, 0.20f)
                val orbitDist = 12.0f
                val elevDeg = (12.0f + orbitPitchDeg).coerceIn(-4.0f, 65f)
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
        /**
         * Returns the highest road, curb, boarding platform, or landmark terrace mesh height at (x, z)
         * so the Third-Person Follow camera never clips through road or terminal meshes.
         */
        fun computeRoadSurfaceFloorHeight(x: Float, z: Float): Float {
            var surfaceY = 0.08f // Base asphalt crown & laterite shoulder height
            // Sakthan Thampuran Stand boarding bays & platforms
            if (x in -24f..50f && z in -222f..-134f) {
                surfaceY = max(surfaceY, 0.34f)
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

        /**
         * Prevents camera eye and sightline ray from clipping through road meshes, raised platforms,
         * or the 12m Hino RM 2 coach hull during Lerp transitions and tight cornering.
         */
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
                // 1. Sample 5 points along the sightline ray from focusPos to candidateEye
                // to guarantee neither the camera nor its near-clip frustum slices road/platform meshes.
                var maxRoadFloorAlongRay = computeRoadSurfaceFloorHeight(eyeX, eyeZ)
                val raySamples = 4
                for (i in 1..raySamples) {
                    val frac = i.toFloat() / raySamples.toFloat()
                    val sx = focusPos.x + (eyeX - focusPos.x) * frac
                    val sz = focusPos.z + (eyeZ - focusPos.z) * frac
                    val h = computeRoadSurfaceFloorHeight(sx, sz)
                    if (h > maxRoadFloorAlongRay) {
                        maxRoadFloorAlongRay = h
                    }
                }
                val minAllowedY = maxRoadFloorAlongRay + MIN_ROAD_CAMERA_CLEARANCE_M
                if (eyeY < minAllowedY) {
                    eyeY = minAllowedY
                }

                // 2. Prevent camera from clipping inside the 12m Hino RM 2 coach hull during rapid yaw Lerp
                val dx = eyeX - busPos.x
                val dz = eyeZ - busPos.z
                val sinH = sin(busHeadingRad)
                val cosH = cos(busHeadingRad)
                val localX = dx * cosH - dz * sinH
                val localZ = dx * sinH + dz * cosH
                val halfWidthMargin = 1.65f
                val halfLengthMargin = 6.55f
                if (abs(localX) < halfWidthMargin && abs(localZ) < halfLengthMargin && eyeY < busPos.y + 3.85f) {
                    // Push camera radially outside the coach bounding volume while maintaining height
                    val pushZ = if (localZ >= 0f) halfLengthMargin + 1.2f else -(halfLengthMargin + 1.2f)
                    eyeX = busPos.x + localX * cosH + pushZ * sinH
                    eyeZ = busPos.z - localX * sinH + pushZ * cosH
                }
            } else {
                // Cockpit / Hood: ensure camera never drops below road surface + 1.6m
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

    private fun ensureEngineAudioStreamStarted() {
        if (engineAudioJob?.isActive == true) return
        engineAudioJob = viewModelScope.launch(Dispatchers.Default) {
            runContinuousDieselEngineSynthLoop()
        }
    }

    /**
     * Real-time continuous streaming Heavy Bus 6-Cylinder 8-Liter Diesel Engine Synthesizer.
     * Models physical compression-ignition cylinder blowdown impulses (1-5-3-6-2-4 firing order),
     * direct-injection diesel clatter, and deep 4-inch tailpipe muffler resonance in the 500-1,580 RPM
     * heavy commercial bus torque band — with strictly ZERO sine whistle ('പൂ' tone) or car-like whine.
     */
    private suspend fun kotlinx.coroutines.CoroutineScope.runContinuousDieselEngineSynthLoop() {
        val sampleRate = 22050
        val chunkSamples = 441 // 20 ms low-latency streaming buffer
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

            // 2-revolution (720-degree) 4-stroke cycle position in [0.0, 6.0) across all 6 cylinders
            var cyclePos6 = 0.0

            // 6-cylinder firing order (1-5-3-6-2-4) manifold runner length & injector balance factors
            // Gives the deep, throaty, unmistakable heavy bus exhaust cadence instead of a sterile car hum
            val cylPulseWeights = doubleArrayOf(1.18, 0.91, 1.05, 0.94, 1.11, 0.89)

            // Multi-band physical heavy bus exhaust & cast-iron engine block filter states
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
                    // Heavy cast-iron flywheel inertia smoothing
                    smoothRpm += (tgtRpm - smoothRpm) * 0.00042
                    smoothLoad += (tgtLoad - smoothLoad) * 0.00075
                    smoothSpeed += (tgtSpeed - smoothSpeed) * 0.00050
                    smoothGain += (tgtGain - smoothGain) * 0.00105

                    if (smoothGain < 0.0015) {
                        pcmChunk[i] = 0
                        continue
                    }

                    // 6 cylinders fire across every 2 crankshaft revolutions (3 power strokes per revolution)
                    val fFire = (smoothRpm / 60.0) * 3.0
                    cyclePos6 += fFire / sampleRate
                    if (cyclePos6 >= 6.0) cyclePos6 -= 6.0

                    val cylIdx = cyclePos6.toInt().coerceIn(0, 5)
                    val u = cyclePos6 - cylIdx.toDouble() // 0.0 .. 1.0 within current cylinder power/exhaust stroke
                    val cylWeight = cylPulseWeights[cylIdx]

                    // Broadband combustion & exhaust gas turbulence source
                    noiseSeed = noiseSeed * 1664525u + 1013904223u
                    val white = ((noiseSeed shr 16).toInt() / 32768.0) - 1.0

                    // 1. Asymmetric Exhaust Valve Blowdown Impulse (NO pure sine waves -> zero 'പൂ' flute/whistle!)
                    // Sharp rise at u=0..0.09 followed by exponential pressure blowdown + sawtooth wave
                    val attack = (u / 0.085).coerceIn(0.0, 1.0)
                    val blowdownEnv = attack * kotlin.math.exp(-u * 4.4)
                    val asymmetricSaw = (1.0 - 2.0 * u) * (1.0 - 0.35 * u)
                    val rawBlowdown = (asymmetricSaw * 0.85 + blowdownEnv * 1.35) * cylWeight

                    // Non-linear cast-iron exhaust manifold wave-shaper (creates rich odd & even harmonics under load)
                    val manifoldDrive = 1.45 + smoothLoad * 1.35 + smoothSpeed * 0.65
                    val xManifold = rawBlowdown * manifoldDrive
                    val shapedExhaust = xManifold / (1.0 + abs(xManifold) * 0.72)

                    // 2. Deep 4-Inch Bus Tailpipe Sub-Bass Thump (55 Hz - 140 Hz)
                    val gasChuff = white * blowdownEnv * (0.42 + smoothLoad * 0.45)
                    subBassLp += ((shapedExhaust + gasChuff) - subBassLp) * 0.032
                    val mufflerCut = (0.055 + smoothSpeed * 0.065 + smoothLoad * 0.045).coerceIn(0.05, 0.17)
                    mufflerLowLp1 += ((shapedExhaust + gasChuff) - mufflerLowLp1) * mufflerCut
                    mufflerLowLp2 += (mufflerLowLp1 - mufflerLowLp2) * mufflerCut

                    // 3. Heavy Bus Mid-Range Throat Growl (180 Hz - 440 Hz Ashok Leyland / Hino roar under load)
                    val throatExcite = shapedExhaust * (0.60 + smoothLoad * 0.75) +
                        white * blowdownEnv * (0.55 + smoothLoad * 0.65 + smoothSpeed * 0.35)
                    throatBandLp += (throatExcite - throatBandLp) * (0.115 + smoothSpeed * 0.055)
                    throatBandHp += (throatBandLp - throatBandHp) * 0.038
                    val throatGrowl = (throatBandLp - throatBandHp) * (0.85 + smoothLoad * 0.45 + smoothSpeed * 0.35)

                    // 4. Direct-Injection Diesel Combustion Knock / Clatter (520 Hz - 980 Hz mechanical ignition bark)
                    // Sharp ignition spike at start of power stroke gives authentic heavy bus diesel clatter
                    val knockEnv = kotlin.math.exp(-u * 14.5) * cylWeight
                    val knockExcite = (white * 0.78 + (1.0 - 2.0 * u) * 0.42) * knockEnv
                    knockBandLp += (knockExcite - knockBandLp) * 0.24
                    knockBandHp += (knockBandLp - knockBandHp) * 0.095
                    val dieselClatter = (knockBandLp - knockBandHp) * (0.52 + smoothLoad * 0.48 + smoothSpeed * 0.25)

                    // 5. Belt-Driven Viscous Radiator Fan & Heavy Drivetrain Air Rush (deep, non-tonal rush at speed)
                    fanRushLp += (white - fanRushLp) * 0.065
                    val coolingFanRoar = fanRushLp * (0.04 + smoothSpeed * 0.16 + smoothLoad * 0.08)

                    val busEngineMix = (
                        subBassLp * 0.52 +
                        mufflerLowLp2 * 0.68 +
                        throatGrowl * 0.64 +
                        dieselClatter * 0.46 +
                        coolingFanRoar
                    ) * smoothGain

                    // Final soft limiter so heavy bass never clips harshly
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
        engineAudioJob?.cancel()
        super.onCleared()
    }

    private val cachedBrakeApplyPcm: ShortArray by lazy { buildBrakeApplyPcm() }
    private val cachedBrakeReleasePcm: ShortArray by lazy { buildBrakeReleasePcm() }
    private val cachedHornPcm: ShortArray by lazy { buildHornPcm() }
    private val cachedLongBusHornPcm: ShortArray by lazy { buildLongBusHornPcm() }

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

        // Realistic dual-tone automotive/bus diaphragm horn (F4 349.2 Hz + A4 440.0 Hz major third)
        // Rich brass harmonics shaped with diaphragm formant warmth + crisp pneumatic attack
        val fLow = 349.23
        val fHigh = 440.0
        var lpState = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val remaining = durationSec - t

            // Crisp attack (14 ms) with subtle slight pitch settle + smooth 45 ms release tail
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
            // Soft diaphragm saturation for authentic metallic horn timbre
            val saturated = rawDual / (1.0 + abs(rawDual) * 0.38)
            // Gentle one-pole warmth filter to tame harsh upper harmonics while keeping punch
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

        // Deep heavy Kerala coach pneumatic dual air-trumpet horn (G3 196.0 Hz + B3 246.94 Hz + D4 293.66 Hz harmonic body)
        var lpState = 0.0
        var noiseSeed = 0x3C6EF372u

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val remaining = durationSec - t

            // Pneumatic valve buildup (32 ms) and resonant release tail (160 ms)
            val attack = (t / 0.032).coerceIn(0.0, 1.0)
            val release = (remaining / 0.160).coerceIn(0.0, 1.0)
            val env = attack * release

            // Slight compressor pressure rise at start + subtle brass reed vibrato
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
