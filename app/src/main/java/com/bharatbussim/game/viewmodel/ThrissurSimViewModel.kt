package com.bharatbussim.game.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.model.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

data class SimUiState(
    val roads: List<ThrissurOsmRoad> = emptyList(),
    val activeRoadIndex: Int = 1, // Default: Sakthan Stand -> M.O. Road -> Swaraj Round
    val busPos: Vec3 = Vec3(25.0f, 0.06f, -176.0f),
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
    val headlightsOn: Boolean = true,
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
    val minimapZoom: Float = 1.0f,
    val walletInr: Int = 1250,
    val currentRoadStreetName: String = "Sakthan Thampuran Stand • Bay 07",
    val distanceToRouteEndKm: Float = 0.48f,
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
    private var accelSoundCooldown = 0f
    private var wasBrakingInPreviousStep = false

    fun loadRoadsIfNeeded(context: Context) {
        if (_uiState.value.roads.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = ThrissurRoadRepository.loadFromAssets(context)
            val startIdx = loaded.indexOfFirst { it.id == "ROAD_SAKTHAN_TO_SWARAJ" }.coerceAtLeast(0)
            val startRoad = loaded.getOrNull(startIdx)
            // Spawn parked inside Sakthan Thampuran Bus Stand Bay 07 (X=25.0m, Z=-176.0m, facing straight North along X=25.0m toward Right Exit Gate)
            val pos = Vec3(25.0f, 0.06f, -176.0f)

            _uiState.update {
                it.copy(
                    roads = loaded,
                    activeRoadIndex = startIdx,
                    busPos = pos,
                    busHeadingRad = 0f,
                    currentRoadStreetName = "Sakthan Stand • Boarding Bay 07",
                    distanceToRouteEndKm = (startRoad?.realDistanceM ?: 480f) / 1000f,
                    statusBanner = "Hino RM 2 Coach Parked in Sakthan Stand (Bay 07) • Press GAS to Exit"
                )
            }
        }
    }

    fun stepPhysics(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        val s = _uiState.value
        if (s.roads.isEmpty()) return

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
        val maxFwdSpeedMps = 75f / 3.6f
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
                            effectiveThrottle * 5.6f * (1f - (speedMps / maxFwdSpeedMps).coerceIn(0f, 0.9f) * 0.40f)
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

        // Continuous turbo-diesel acceleration sound pulse while holding throttle
        if (accelSoundCooldown > 0f) {
            accelSoundCooldown = (accelSoundCooldown - dt).coerceAtLeast(0f)
        }
        val isActivelyAccelerating = !s.isParkBrakeActive &&
            effectiveThrottle > 0.08f &&
            s.brakeInput < 0.05f &&
            (s.gear == TransmissionGear.Drive || s.gear == TransmissionGear.Reverse)
        if (isActivelyAccelerating && accelSoundCooldown <= 0f) {
            accelSoundCooldown = 0.42f
            triggerAccelerationSound(abs(speedMps) * 3.6f)
        }

        // 3. Ackermann / Bicycle Power-Steering Kinematics (Hino RM 2 6,000 mm wheelbase, 42° terminal lock)
        val wheelbase = 6.0f
        val speedFactor = (abs(speedMps) * 3.6f / 70f).coerceIn(0f, 1f)
        val maxFrontWheelDeg = 42.0 - 23.0 * speedFactor
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

        // 4. Track closest road & terminal bay / exit gate status
        var closestRoadIdx = s.activeRoadIndex
        var closestFrameIdx = 0
        var minDist = Float.MAX_VALUE
        for (rIdx in s.roads.indices) {
            val frames = s.roads[rIdx].frames
            for (fIdx in frames.indices step 2) {
                val d = hypot(frames[fIdx].pos.x - newPos.x, frames[fIdx].pos.z - newPos.z)
                if (d < minDist) {
                    minDist = d
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
        val remKm = (curRoad.realDistanceM / 1000f) * fracRemaining

        val insideSakthanTerminal = newPos.x in -22f..48f && newPos.z in -218f..-138f
        val streetName = when {
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
        var newWallet = s.walletInr
        var newBanner = s.statusBanner
        val distToToll = hypot(newPos.x - 998.2f, newPos.z - (-1805.2f))
        if (distToToll < 24f && !tollPaidInSession && newWallet >= 50) {
            tollPaidInSession = true
            newWallet -= 50
            newBanner = "FASTag Toll Paid: ₹50 at Paliyekkara Toll Plaza (NH 544) • Barrier Open"
        } else if (distToToll > 60f) {
            tollPaidInSession = false
        }

        _uiState.update {
            it.copy(
                busPos = newPos,
                busHeadingRad = newHeading,
                speedKmh = abs(speedMps) * 3.6f,
                signedSpeedMps = speedMps,
                steerInput = nextSteerInput,
                steeringWheelAngleDeg = nextWheelDeg,
                wiperPhaseRad = wiperAccumulator,
                blinkerPhase = newBlink,
                currentRoadStreetName = streetName,
                distanceToRouteEndKm = remKm,
                walletInr = newWallet,
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
        if (pressed && prev.throttleInput <= 0.05f) {
            accelSoundCooldown = 0.42f
            triggerAccelerationSound(prev.speedKmh)
            if (prev.isParkBrakeActive) {
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
        _uiState.update {
            val next = !it.hazardsOn
            it.copy(hazardsOn = next, turnLeftOn = false, turnRightOn = false)
        }
    }

    fun toggleTurnLeft() {
        _uiState.update {
            val next = !it.turnLeftOn
            it.copy(turnLeftOn = next, turnRightOn = false, hazardsOn = false)
        }
    }

    fun toggleTurnRight() {
        _uiState.update {
            val next = !it.turnRightOn
            it.copy(turnRightOn = next, turnLeftOn = false, hazardsOn = false)
        }
    }

    fun cycleCameraMode() {
        val modes = CameraViewMode.entries
        _uiState.update {
            val nextIdx = (it.cameraMode.ordinal + 1) % modes.size
            it.copy(cameraMode = modes[nextIdx], cameraOrbitYawDeg = 0f, cameraOrbitPitchDeg = 0f)
        }
    }

    fun toggleRearCamera() {
        _uiState.update { it.copy(rearViewActive = !it.rearViewActive) }
    }

    fun orbitCamera(deltaYawDeg: Float, deltaPitchDeg: Float) {
        _uiState.update {
            it.copy(
                cameraOrbitYawDeg = (it.cameraOrbitYawDeg + deltaYawDeg).coerceIn(-120f, 120f),
                cameraOrbitPitchDeg = (it.cameraOrbitPitchDeg + deltaPitchDeg).coerceIn(-15f, 35f)
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
        _uiState.update {
            it.copy(
                activeRoadIndex = index,
                busPos = Vec3(spawnFrame.pos.x, 0.06f, spawnFrame.pos.z),
                busHeadingRad = heading,
                speedKmh = 0f,
                signedSpeedMps = 0f,
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
        _uiState.update {
            it.copy(
                activeRoadIndex = roadIdx,
                busPos = spec.pos,
                busHeadingRad = spec.headingRad,
                speedKmh = 0f,
                signedSpeedMps = 0f,
                steerInput = 0f,
                steeringWheelAngleDeg = 0f,
                isSteeringHeld = false,
                gear = TransmissionGear.Drive,
                isParkBrakeActive = false,
                currentRoadStreetName = spec.streetLabel,
                showRouteModal = false,
                statusBanner = spec.banner
            )
        }
    }

    fun resetBusToActiveCorridorStart() {
        teleportToRoad(_uiState.value.activeRoadIndex)
    }

    fun triggerAirHorn() {
        _uiState.update { it.copy(hornActive = true) }
        viewModelScope.launch(Dispatchers.Default) {
            playDualTonePneumaticHorn()
            _uiState.update { it.copy(hornActive = false) }
        }
    }

    private fun triggerAccelerationSound(speedKmh: Float) {
        viewModelScope.launch(Dispatchers.Default) {
            playTurboDieselAccelRev(speedKmh)
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

    private fun playTurboDieselAccelRev(speedKmh: Float) {
        try {
            val sampleRate = 22050
            val durationSec = 0.42
            val numSamples = (sampleRate * durationSec).toInt()
            val pcm = ShortArray(numSamples)
            // Scale base engine RPM with bus speed (62 Hz idle roar up to 135 Hz high-RPM pull)
            val speedRatio = (speedKmh / 65f).coerceIn(0f, 1f)
            val fStart = 64.0 + speedRatio * 48.0
            val fEnd = fStart + 26.0
            var phase = 0.0
            var turboPhase = 0.0
            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val f0 = fStart + (fEnd - fStart) * progress
                phase += 2.0 * PI * f0 / sampleRate
                // 6-cylinder diesel firing harmonics + turbocharger spool whistle (1,650..2,350 Hz)
                val turboFreq = 1650.0 + speedRatio * 450.0 + 320.0 * progress
                turboPhase += 2.0 * PI * turboFreq / sampleRate
                val env = when {
                    progress < 0.12 -> progress / 0.12
                    progress > 0.82 -> (1.0 - progress) / 0.18
                    else -> 1.0
                }
                val cylPulse = 0.72 + 0.28 * sin(phase * 3.0)
                val dieselWave = (
                    sin(phase) * 0.48 +
                    sin(phase * 1.5) * 0.26 +
                    sin(phase * 2.0) * 0.22 +
                    sin(phase * 3.0) * 0.15 +
                    sin(turboPhase) * 0.08
                ) * cylPulse * env
                pcm[i] = (dieselWave.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.46).toInt().toShort()
            }
            playStaticPcmTrack(pcm, sampleRate, 430L)
        } catch (_: Throwable) {
        }
    }

    private fun playPneumaticBrakeApply(speedKmh: Float) {
        try {
            val sampleRate = 22050
            val durationSec = 0.34
            val numSamples = (sampleRate * durationSec).toInt()
            val pcm = ShortArray(numSamples)
            val movingFactor = (speedKmh / 20f).coerceIn(0.15f, 1.0f).toDouble()
            var noiseSeed = 0x5A17C93u
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / numSamples
                // Fast deterministic pseudo-random white noise for air-valve actuation hiss
                noiseSeed = noiseSeed * 1664525u + 1013904223u
                val white = ((noiseSeed shr 16).toInt() / 32768.0) - 1.0
                val hissEnv = kotlin.math.exp(-t * 9.5)
                // Heavy brake drum / pad friction resonance + subtle metallic squeal when moving
                val drumLow = sin(2.0 * PI * (155.0 - 35.0 * progress) * t) * 0.36
                val squeal = sin(2.0 * PI * (2420.0 - 180.0 * progress) * t) * 0.14 * movingFactor
                val fadeOut = if (progress > 0.75) (1.0 - progress) / 0.25 else 1.0
                val sample = (white * 0.52 * hissEnv + (drumLow + squeal) * fadeOut)
                pcm[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.52).toInt().toShort()
            }
            playStaticPcmTrack(pcm, sampleRate, 350L)
        } catch (_: Throwable) {
        }
    }

    private fun playPneumaticBrakeReleaseDump() {
        try {
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
                // Sharp pneumatic burst ("Pssshhh!") when releasing air brakes
                val attack = (t / 0.014).coerceIn(0.0, 1.0)
                val decay = kotlin.math.exp(-t * 7.8)
                val valvePing = sin(2.0 * PI * (940.0 - 380.0 * t) * t) * kotlin.math.exp(-t * 22.0) * 0.28
                val sample = (filtered * 0.85 + valvePing) * attack * decay
                pcm[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.58).toInt().toShort()
            }
            playStaticPcmTrack(pcm, sampleRate, 390L)
        } catch (_: Throwable) {
        }
    }

    private fun playDualTonePneumaticHorn() {
        try {
            val sampleRate = 22050
            val durationSec = 0.35
            val numSamples = (sampleRate * durationSec).toInt()
            val pcm = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val wave = (sin(2.0 * PI * 380.0 * t) * 0.45 +
                    sin(2.0 * PI * 460.0 * t) * 0.45 +
                    sin(2.0 * PI * 760.0 * t) * 0.10)
                pcm[i] = (wave * Short.MAX_VALUE * 0.55).toInt().toShort()
            }
            playStaticPcmTrack(pcm, sampleRate, 360L)
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
