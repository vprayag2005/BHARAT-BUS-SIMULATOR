package com.bharatbussim.game.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.CircleObstacleInput
import com.bharatbussim.game.model.HeadlightBeamMode
import com.bharatbussim.game.model.ImpactMaterial
import com.bharatbussim.game.model.ObbObstacleInput
import com.bharatbussim.game.model.ThrissurCollisionSystem
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import com.bharatbussim.game.model.ThrissurTrafficSystem
import com.bharatbussim.game.model.TrafficMeshLoader
import com.bharatbussim.game.model.TrafficSignalAspect
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.model.WiperSpeedMode
import com.bharatbussim.game.ui.FuelStationVoiceManager
import com.bharatbussim.game.ui.FuelStationWorkerAnimator
import com.bharatbussim.game.ui.FuelType
import com.bharatbussim.game.ui.FuelWorkerWorldPose
import com.bharatbussim.game.ui.HinoRealMeshCache
import com.bharatbussim.game.ui.RefuelCutsceneCam
import com.bharatbussim.game.ui.RefuelDialogStep
import com.bharatbussim.game.ui.RefuelLiveState
import com.bharatbussim.game.ui.RefuelOrderMode
import com.bharatbussim.game.ui.RefuelPhase
import com.bharatbussim.game.ui.SHORANUR_STATION_BAY_CENTERS_Z
import com.bharatbussim.game.ui.SHORANUR_STATION_BAY_X
import com.bharatbussim.game.ui.SHORANUR_TERM_BAY_1_CENTER_Z
import com.bharatbussim.game.ui.SHORANUR_TERM_BAY_CENTER_X
import com.bharatbussim.game.ui.SHORANUR_TERM_BAY_PITCH_Z
import com.bharatbussim.game.ui.ShoranurRailwaySystem
import com.bharatbussim.game.ui.ThrissurFuelStationSystem
import com.bharatbussim.game.ui.ThrissurPrebuiltWorldCache
import com.bharatbussim.game.ui.WorkerAnimState
import com.bharatbussim.game.ui.MuthuvaraLcPhase
import com.bharatbussim.game.ui.Zone1GDriveEnvironmentRenderer
import com.bharatbussim.game.ui.Zone2Nh544KuthiranRenderer
import com.bharatbussim.game.ui.Zone3Nh544SouthRenderer
import com.bharatbussim.game.ui.Zone4KoleRailHeritageRenderer
import com.bharatbussim.game.ui.Zone5Nh66CoastalRenderer
import com.bharatbussim.game.ui.PuthanpallyBasilicaRenderer
import com.bharatbussim.game.ui.getRoadSignPostColliders
import com.bharatbussim.game.ui.getRoadsideTreeColliders
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
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
    val activeRoadIndex: Int = 1, // Default: Sakthan Terminal -> Back Exit -> Boundary Road -> Swaraj Round
    val parkedBayNumber: Int = 1,
    val busPos: Vec3 = Vec3(-19.75f, 0.06f, -181.5f),
    val busHeadingRad: Float = 0f, // 0f = +Z (North out of bus bay into Sakthan courtyard)
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
    val camFocusPos: Vec3 = Vec3(-19.75f, 2.08f, -178.30f),
    val camEyePos: Vec3 = Vec3(-19.75f, 3.84f, -191.18f),
    val cameraFovDeg: Float = CINEMATIC_FOLLOW_FOV_DEG,
    val minimapZoom: Float = 1.0f,
    val walletInr: Int = 4500,
    val currentRoadStreetName: String = "Sakthan Bus Terminal • Bay 1 (DEPARTURE)",
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
    val refuel: RefuelLiveState = RefuelLiveState(),
    val trafficSignalCycleSec: Float = 0f,
    val manikandanalSignal: TrafficSignalAspect = TrafficSignalAspect.Red,
    val manikandanalSignalCountdownSec: Int = 30,
    val sakthanEntryBarrierOpenFrac: Float = 0f,
    val sakthanExitBarrierOpenFrac: Float = 0f,
    val vadakkeEntryBarrierOpenFrac: Float = 0f,
    val vadakkeExitBarrierOpenFrac: Float = 0f,
    val stationEntryBarrierOpenFrac: Float = 0f,
    val stationExitBarrierOpenFrac: Float = 0f,
    val shoranurTermEntryBarrierOpenFrac: Float = 0f,
    val shoranurTermExitBarrierOpenFrac: Float = 0f,
    val shoranurStationEntryBarrierOpenFrac: Float = 0f,
    val shoranurStationExitBarrierOpenFrac: Float = 0f,
    val paliyekkaraTollBarrierOpenFrac: Float = 0f,
    val paliyekkaraTollActiveLaneIdx: Int = 5,
    val nattikaTollBarrierOpenFrac: Float = 0f,
    val nattikaTollActiveLaneIdx: Int = 4,
    val muthuvaraLcPhase: MuthuvaraLcPhase = MuthuvaraLcPhase.STATE_OPEN,
    val muthuvaraLcBarrierClosedFrac: Float = 0f,
    val simTimeSec: Float = 0f,
    val isWorldReady: Boolean = false,
    val loadingStageLabel: String = "Loading Thrissur District...",
    val showRouteModal: Boolean = false,
    val statusBanner: String = "Hino RM 2 Coach Parked in Sakthan Terminal (Bay 1) • Press ENGINE START to Crank Diesel",
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
    private var cachedRoadScanIdx = -1
    private var cachedFrameScanIdx = 0
    private var entryBarrierLatchOpen = false
    private var exitBarrierLatchOpen = false
    private var vadakkeEntryBarrierLatchOpen = false
    private var vadakkeExitBarrierLatchOpen = false
    private var stationEntryBarrierLatchOpen = false
    private var stationExitBarrierLatchOpen = false
    private var shoranurTermEntryBarrierLatchOpen = false
    private var shoranurTermExitBarrierLatchOpen = false
    private var shoranurStationEntryBarrierLatchOpen = false
    private var shoranurStationExitBarrierLatchOpen = false
    private var paliyekkaraTollBarrierLatchOpen = false
    private var paliyekkaraTollActiveLane = 5
    private var nattikaTollBarrierLatchOpen = false
    private var nattikaTollActiveLane = 4
    private var nattikaTollPaidInSession = false
    private var muthuvaraLcCycleSec = 0f
    private var simTimeAccumulator = 0f
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
    @Volatile private var synthTargetTunnelReverb = 0f
    @Volatile private var synthLcBellGain = 0f
    @Volatile private var synthLcHornGain = 0f
    @Volatile private var synthLcRailClackGain = 0f

    // Solid obstacle collision feedback state
    private var collisionSoundCooldownSec = 0f
    private var collisionBannerHoldSec = 0f
    private var collisionJoltPitch = 0f
    private var collisionJoltRoll = 0f
    private var isStartupLoading = false

    // Highway Fuel Station Realistic Attendant Animation, Voice & Pump Meter State Machine
    private var fuelVoiceManager: FuelStationVoiceManager? = null
    private var refuelPhaseTimerSec = 0f
    private var refuelBayStoppedSec = 0f
    private var refuelDismissedStationId: String = ""
    private var refuelCompletedStationId: String = ""
    private var activeWorkerX = 0f
    private var activeWorkerZ = 0f
    private var activeWorkerYaw = 0f
    private var activeWorkerWalkPhase = 0f
    private var activeWorkerInitialized = false
    private var workerSpokeConfirm = false
    private var workerPaymentSettled = false
    private var fuelDoorSoundPlayed = false
    private var nozzleActionSoundPlayed = false
    private var fuelDoorOpenFrac = 0f
    private var lastCutsceneCam: RefuelCutsceneCam = RefuelCutsceneCam.NONE

    private fun createSakthanBaySpawn(bayNumber: Int = kotlin.random.Random.nextInt(1, 9)): Triple<Vec3, Float, Int> {
        val clampedBay = bayNumber.coerceIn(1, 8)
        val bayCenterX = -19.75f + (clampedBay - 1) * 5.0f
        return Triple(Vec3(bayCenterX, 0.06f, -181.5f), 0f, clampedBay)
    }

    private fun createVadakkeBaySpawn(bayNumber: Int = kotlin.random.Random.nextInt(1, 9)): Triple<Vec3, Float, Int> {
        val clampedBay = bayNumber.coerceIn(1, 8)
        val bayCenterX = -53.0f + (clampedBay - 1) * 4.95f
        val bayHeading = (PI + 0.24).toFloat() // Facing South-South-West out of angled echelon bay toward West EXIT
        return Triple(Vec3(bayCenterX, 0.06f, 229.2f), bayHeading, clampedBay)
    }

    private fun createStationBaySpawn(bayNumber: Int = kotlin.random.Random.nextInt(1, 5)): Triple<Vec3, Float, Int> {
        val clampedBay = bayNumber.coerceIn(1, 4)
        val bayCenterZ = com.bharatbussim.game.ui.STATION_BUS_BAY_CENTERS_Z[clampedBay - 1]
        return Triple(Vec3(com.bharatbussim.game.ui.STATION_BUS_BAY_X, 0.06f, bayCenterZ), 0f, clampedBay)
    }

    fun markWorldGpuReady() {
        if (!_uiState.value.isWorldReady) {
            _uiState.update { it.copy(isWorldReady = true, loadingStageLabel = "Ready") }
        }
    }

    fun loadRoadsIfNeeded(context: Context) {
        if (_uiState.value.roads.isNotEmpty() || isStartupLoading) return
        isStartupLoading = true
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _uiState.update { it.copy(loadingStageLabel = "Loading Thrissur Roads & Coach Mesh...") }
                val (loaded, _) = coroutineScope {
                    val roadsDeferred = async(Dispatchers.IO) { ThrissurRoadRepository.loadFromAssets(appContext) }
                    val meshDeferred = async(Dispatchers.IO) { HinoRealMeshCache.getOrLoad(appContext) }
                    roadsDeferred.await() to meshDeferred.await()
                }

                if (fuelVoiceManager == null) {
                    fuelVoiceManager = FuelStationVoiceManager(appContext, viewModelScope)
                }
                _uiState.update { it.copy(loadingStageLabel = "Building Terminals, Swaraj Round & Landmarks...") }
                // Precompute highway fuel station footprints first so guardrails, trees & scenery leave clear aprons
                ThrissurFuelStationSystem.ensurePlacements(loaded)
                coroutineScope {
                    val zone1Job = async(Dispatchers.Default) {
                        Zone1GDriveEnvironmentRenderer.ensureLoaded(appContext, loaded)
                    }
                    val fuelStationJob = async(Dispatchers.IO) {
                        ThrissurFuelStationSystem.ensureLoaded(appContext, loaded)
                        FuelStationWorkerAnimator.ensureLoaded(appContext)
                    }
                    val railwayJob = async(Dispatchers.IO) {
                        ShoranurRailwaySystem.ensureLoaded(appContext)
                        ShoranurRailwaySystem.getStaticTrackInstances()
                    }
                    val worldVboJob = async(Dispatchers.Default) {
                        ThrissurPrebuiltWorldCache.buildIfNeeded(loaded)
                    }
                    val trafficJob = async(Dispatchers.IO) {
                        TrafficMeshLoader.ensureLoaded(appContext)
                        ThrissurTrafficSystem.initializeForRoads(loaded)
                    }
                    val audioJob = async(Dispatchers.Default) {
                        // Warm up collision audio buffers off the main/audio threads
                        cachedBuildingCrashPcm
                        cachedTreeHitPcm
                        cachedMetalPoleHitPcm
                    }
                    zone1Job.await()
                    fuelStationJob.await()
                    railwayJob.await()
                    worldVboJob.await()
                    trafficJob.await()
                    audioJob.await()
                }

                // Register all solid colliders (Zone 1 buildings, trees, roadside trees, direction signposts, terminals)
                val placed = Zone1GDriveEnvironmentRenderer.getPlacedInstances()
                val buildingObbs = ArrayList<ObbObstacleInput>(128)
                val treeCircles = ArrayList<CircleObstacleInput>(512)
                for (i in 0 until placed.size) {
                    val inst = placed[i]
                    when (inst.model.category) {
                        Zone1GDriveEnvironmentRenderer.CAT_BUILDING -> {
                            buildingObbs.add(
                                ObbObstacleInput(
                                    cx = inst.cx,
                                    cz = inst.cz,
                                    cosYaw = inst.cosYaw,
                                    sinYaw = inst.sinYaw,
                                    halfX = (inst.scaledHalfX * 0.94f).coerceAtLeast(0.8f),
                                    halfZ = (inst.scaledHalfZ * 0.94f).coerceAtLeast(0.8f),
                                    material = ImpactMaterial.CONCRETE_BUILDING,
                                    label = "Building"
                                )
                            )
                        }
                        Zone1GDriveEnvironmentRenderer.CAT_TREE -> {
                            // Mountain-top trees perched above Kuthiran Tunnel / Reservoir Hills (baseY >= 3.0m)
                            // must NEVER create ground-level 2D colliders inside the tunnel!
                            if (inst.baseY < 3.0f) {
                                val trunkRadius = (min(inst.scaledHalfX, inst.scaledHalfZ) * 0.24f)
                                    .coerceIn(0.32f, 0.78f)
                                treeCircles.add(
                                    CircleObstacleInput(
                                        cx = inst.cx,
                                        cz = inst.cz,
                                        radius = trunkRadius,
                                        material = ImpactMaterial.TREE_WOOD,
                                        label = "Tree"
                                    )
                                )
                            }
                        }
                    }
                }
                val roadsideTrees = getRoadsideTreeColliders(loaded)
                for (i in 0 until roadsideTrees.size) {
                    val t = roadsideTrees[i]
                    treeCircles.add(
                        CircleObstacleInput(
                            cx = t.first,
                            cz = t.second,
                            radius = t.third,
                            material = ImpactMaterial.TREE_WOOD,
                            label = "Roadside Tree"
                        )
                    )
                }
                val rawSignPosts = getRoadSignPostColliders(loaded)
                val signPostCircles = ArrayList<CircleObstacleInput>(rawSignPosts.size)
                for (i in 0 until rawSignPosts.size) {
                    val p = rawSignPosts[i]
                    signPostCircles.add(
                        CircleObstacleInput(
                            cx = p.first,
                            cz = p.second,
                            radius = p.third,
                            material = ImpactMaterial.METAL_SIGN_POLE,
                            label = "Direction Signboard"
                        )
                    )
                }
                ThrissurFuelStationSystem.appendFuelStationColliders(
                    buildingObbs = buildingObbs,
                    signPostCircles = signPostCircles
                )
                ThrissurCollisionSystem.rebuild(
                    buildingObbs = buildingObbs,
                    treeCircles = treeCircles,
                    signPostCircles = signPostCircles
                )

                val startIdx = loaded.indexOfFirst { it.id == "ROAD_SAKTHAN_TO_SWARAJ" }.coerceAtLeast(0)
                val startRoad = loaded.getOrNull(startIdx)
                val (pos, heading, bayNum) = createSakthanBaySpawn()
                entryBarrierLatchOpen = false
                exitBarrierLatchOpen = false
                vadakkeEntryBarrierLatchOpen = false
                vadakkeExitBarrierLatchOpen = false
                stationEntryBarrierLatchOpen = false
                stationExitBarrierLatchOpen = false
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
                        parkedBayNumber = bayNum,
                        busPos = pos,
                        busHeadingRad = heading,
                        sakthanEntryBarrierOpenFrac = 0f,
                        sakthanExitBarrierOpenFrac = 0f,
                        vadakkeEntryBarrierOpenFrac = 0f,
                        vadakkeExitBarrierOpenFrac = 0f,
                        stationEntryBarrierOpenFrac = 0f,
                        stationExitBarrierOpenFrac = 0f,
                        loadingStageLabel = "Uploading 3D Scene to GPU...",
                        currentRoadStreetName = "Sakthan Bus Terminal • Bay $bayNum (DEPARTURE)",
                        distanceToRouteEndKm = (startRoad?.realDistanceM ?: 480f) / 1000f,
                        statusBanner = "Hino RM 2 Coach Parked at Bay $bayNum • Press ENGINE START & Exit via Back Gate"
                    )
                    snapCameraToBus(snapped)
                }
            } finally {
                isStartupLoading = false
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

        // Advance Zone 1 Manikandanal Traffic Signal Cycle & Live Railway Train Simulation Clock
        trafficSignalAccumulator = (trafficSignalAccumulator + dt) % TrafficSignalAspect.TOTAL_CYCLE_SEC
        simTimeAccumulator += dt
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
            isSteeringPointerHeld -> 32.0f
            s.autoSteerAssist -> 12.0f
            else -> 16.5f
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

        // Responsive hydraulic power steering rack (agile low-speed maneuvering + smooth highway poise)
        val steerDiff = targetSteerNorm - smoothedWheelSteerInput
        val rackRate = if (isSteeringPointerHeld) {
            18.5f - highSpeedFactor * 4.0f
        } else {
            13.5f - highSpeedFactor * 2.5f
        }
        val maxRackPerSec = if (isSteeringPointerHeld) {
            4.8f - highSpeedFactor * 1.1f
        } else {
            3.4f - highSpeedFactor * 0.75f
        }
        val rackStep = (steerDiff * smoothDampAlpha(rackRate, dt))
            .coerceIn(-maxRackPerSec * dt, maxRackPerSec * dt)
        smoothedWheelSteerInput = (smoothedWheelSteerInput + rackStep).coerceIn(-1f, 1f)
        if (!isSteeringPointerHeld && abs(targetSteerNorm) < 0.002f && abs(smoothedWheelSteerInput) < 0.003f) {
            smoothedWheelSteerInput = 0f
        }

        // Progressive non-linear steering curve: crisp response around center & smooth full lock
        val absSteer = abs(smoothedWheelSteerInput).coerceIn(0f, 1f)
        val curveSpeedMix = (rollingSpeedKmh / 85f).coerceIn(0f, 1f)
        val lowSpeedCurve = absSteer * (0.76f + 0.24f * absSteer)
        val highSpeedCurve = absSteer * (0.48f + 0.52f * absSteer)
        val curvedSteerMag = lerp(lowSpeedCurve, highSpeedCurve, curveSpeedMix).coerceIn(0f, 1f)
        val shapedSteer = if (smoothedWheelSteerInput >= 0f) curvedSteerMag else -curvedSteerMag
        val targetVisualWheelRad = (shapedSteer * 0.46f).coerceIn(-0.46f, 0.46f)
        smoothedFrontWheelRad = lerp(
            start = smoothedFrontWheelRad,
            end = targetVisualWheelRad,
            t = smoothDampAlpha(20.0f, dt)
        )
        if (!isSteeringPointerHeld && abs(targetVisualWheelRad) < 0.002f && abs(smoothedFrontWheelRad) < 0.002f) {
            smoothedFrontWheelRad = 0f
        }

        val targetThrottle = if (s.brakeInput < 0.05f) effectiveThrottle else 0f
        val throttleRate = if (targetThrottle > smoothedThrottle) dt * 11.5f else dt * 14.0f
        smoothedThrottle = (smoothedThrottle + (targetThrottle - smoothedThrottle) * throttleRate.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)

        val targetBrake = s.brakeInput
        if (targetBrake > 0.05f && smoothedBrake < 0.42f) {
            smoothedBrake = 0.42f
        }
        val brakeRate = if (targetBrake > smoothedBrake) dt * 9.5f else dt * 14.0f
        smoothedBrake = (smoothedBrake + (targetBrake - smoothedBrake) * brakeRate.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)

        // 2. Longitudinal Dynamics (responsive Hino J08E turbo-diesel torque with smooth gliding coast)
        var speedMps = s.signedSpeedMps
        val maxFwdSpeedMps = 105f / 3.6f
        val maxRevSpeedMps = 26f / 3.6f

        if (s.isParkBrakeActive || s.gear == TransmissionGear.Park) {
            val decel = 13.0f * dt
            speedMps = when {
                speedMps > decel -> speedMps - decel
                speedMps < -decel -> speedMps + decel
                else -> 0f
            }
        } else {
            val speedRatio = (abs(speedMps) / maxFwdSpeedMps).coerceIn(0f, 1f)
            val cornerSpeedFactor = ((abs(speedMps) * 3.6f - 28f) / 65f).coerceIn(0f, 1f)
            val cornerScrubDecel = curvedSteerMag * cornerSpeedFactor * 0.85f
            val coastDrag = 0.55f + 0.0014f * speedMps * speedMps + cornerScrubDecel
            val rollingDrag = 0.14f + 0.00045f * speedMps * speedMps + cornerScrubDecel * 0.50f
            val activeBrakeDecel = smoothedBrake * (13.2f + speedRatio * 3.2f)
            val totalCoastAndBrake = coastDrag + activeBrakeDecel

            when (s.gear) {
                TransmissionGear.Drive -> {
                    if (speedMps < 0f) {
                        val stopDecel = (12.0f + smoothedBrake * 12.5f) * dt
                        speedMps = (speedMps + stopDecel).coerceAtMost(0f)
                    } else {
                        val cornerTorqueCut = (1f - curvedSteerMag * cornerSpeedFactor * 0.15f).coerceIn(0.80f, 1f)
                        val torqueCurve = (1f - speedRatio * 0.42f) * cornerTorqueCut
                        val accel = if (smoothedThrottle > 0.02f && s.brakeInput < 0.05f) {
                            smoothedThrottle * 5.25f * torqueCurve
                        } else 0f
                        val decel = if (speedMps > 0f) {
                            (if (accel > 0.05f) rollingDrag else totalCoastAndBrake) * dt
                        } else 0f
                        speedMps = (speedMps + accel * dt - decel).coerceIn(0f, maxFwdSpeedMps)
                    }
                }
                TransmissionGear.Reverse -> {
                    if (speedMps > 0f) {
                        val stopDecel = (12.5f + smoothedBrake * 12.5f + smoothedThrottle * 5.5f) * dt
                        speedMps = (speedMps - stopDecel).coerceAtLeast(0f)
                    } else {
                        val revFrac = (abs(speedMps) / maxRevSpeedMps).coerceIn(0f, 1f)
                        val revAccel = if (smoothedThrottle > 0.02f && s.brakeInput < 0.05f) {
                            -smoothedThrottle * 3.60f * (1f - revFrac * 0.42f)
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
        synthTargetTunnelReverb = Zone2Nh544KuthiranRenderer.computeKuthiranTunnelReverbEnvelope(s.busPos.x, s.busPos.z)
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

        val yawInertiaRate = 14.5f - 2.5f * steerSpeedRatio
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
        val frameCount = currentRoadFrames.size
        if (frameCount > 0) {
            val useLocalWindow = cachedRoadScanIdx == s.activeRoadIndex &&
                cachedFrameScanIdx in 0 until frameCount
            if (useLocalWindow) {
                val winStart = (cachedFrameScanIdx - 28).coerceAtLeast(0)
                val winEnd = (cachedFrameScanIdx + 28).coerceAtMost(frameCount - 1)
                for (fIdx in winStart..winEnd) {
                    val fx = currentRoadFrames[fIdx].pos.x - s.busPos.x
                    val fz = currentRoadFrames[fIdx].pos.z - s.busPos.z
                    val dSq = fx * fx + fz * fz
                    if (dSq < curRoadMinDistSq) {
                        curRoadMinDistSq = dSq
                        curRoadBestFrameIdx = fIdx
                    }
                }
            }
            if (!useLocalWindow || curRoadMinDistSq > 16.0f * 16.0f) {
                for (fIdx in currentRoadFrames.indices) {
                    val fx = currentRoadFrames[fIdx].pos.x - s.busPos.x
                    val fz = currentRoadFrames[fIdx].pos.z - s.busPos.z
                    val dSq = fx * fx + fz * fz
                    if (dSq < curRoadMinDistSq) {
                        curRoadMinDistSq = dSq
                        curRoadBestFrameIdx = fIdx
                    }
                }
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
        cachedRoadScanIdx = closestRoadIdx
        cachedFrameScanIdx = closestFrameIdx
        val curRoad = s.roads[closestRoadIdx]
        val curFrames = curRoad.frames

        if (collisionSoundCooldownSec > 0f) {
            collisionSoundCooldownSec = (collisionSoundCooldownSec - dt).coerceAtLeast(0f)
        }
        if (collisionBannerHoldSec > 0f) {
            collisionBannerHoldSec = (collisionBannerHoldSec - dt).coerceAtLeast(0f)
        }
        val joltDecay = (1f - dt * 9.5f).coerceIn(0f, 1f)
        collisionJoltPitch *= joltDecay
        collisionJoltRoll *= joltDecay

        val vx = sin(newHeading) * kinematicSpeedMps
        val vz = cos(newHeading) * kinematicSpeedMps
        val candX = s.busPos.x + vx * dt
        val candZ = s.busPos.z + vz * dt
        var newPos = Vec3(candX, ShoranurRailwaySystem.computeRoadSurfaceY(candX, candZ), candZ)

        // 4. Update street name, remaining distance, Manikandanal signal status & toll status
        val moveDx = newPos.x - lastRoadScanX
        val moveDz = newPos.z - lastRoadScanZ
        var streetName = s.currentRoadStreetName
        var remKm = s.distanceToRouteEndKm
        var newWallet = s.walletInr
        var newBanner = s.statusBanner

        // 3B. Solid 3D World Collision Resolution (buildings, terminal walls, gate pillars, trees, signboard posts)
        val colRes = ThrissurCollisionSystem.resolveBusCollision(newPos, newHeading, vx, vz)
        if (colRes.hit) {
            val rxCol = colRes.resolvedPos.x
            val rzCol = colRes.resolvedPos.z
            newPos = Vec3(rxCol, ShoranurRailwaySystem.computeRoadSurfaceY(rxCol, rzCol), rzCol)
            val nx = colRes.normalX
            val nz = colRes.normalZ
            val vn = vx * nx + vz * nz
            if (vn < 0f) {
                val fwdXCol = sin(newHeading)
                val fwdZCol = cos(newHeading)
                val fwdDotN = fwdXCol * nx + fwdZCol * nz
                val headOnFactor = abs(fwdDotN)
                if (headOnFactor > 0.58f) {
                    // Direct or steep frontal/rear impact: halt with a subtle chassis rebound at speed
                    speedMps = if (colRes.impactSpeedMps > 3.6f) {
                        -speedMps * 0.14f
                    } else {
                        0f
                    }
                    smoothedYawRate *= 0.35f
                } else {
                    // Shallow side scrape: allow smooth tangential wall sliding with realistic friction
                    val tangentKeep = (1f - headOnFactor * 0.72f).coerceIn(0.22f, 0.90f)
                    speedMps *= tangentKeep
                    smoothedYawRate *= 0.65f
                }

                val impactNorm = (colRes.impactSpeedMps / 11.0f).coerceIn(0.08f, 1.0f)
                val pitchSign = if (speedMps >= 0f) 1f else -1f
                collisionJoltPitch = (collisionJoltPitch + pitchSign * 0.022f * impactNorm)
                    .coerceIn(-0.026f, 0.026f)
                val rightDotN = cos(newHeading) * nx - sin(newHeading) * nz
                collisionJoltRoll = (collisionJoltRoll + rightDotN * 0.026f * impactNorm)
                    .coerceIn(-0.030f, 0.030f)

                if (colRes.impactSpeedMps >= 1.15f && collisionSoundCooldownSec <= 0f) {
                    collisionSoundCooldownSec = 0.36f
                    collisionBannerHoldSec = 2.2f
                    triggerCollisionImpactSound(colRes.material, colRes.impactSpeedMps)
                    val label = colRes.obstacleLabel.ifBlank { colRes.material.displayLabel }
                    newBanner = "Impact! Hit $label"
                }
            }
        }

        // 4B. Sakthan Terminal Direction-Aware Boom Barriers (Front ENTRY Z=-139.33, Back EXIT Z=-206.83)
        // Bus forward Z component: cos(newHeading) < 0 means facing South (-Z).
        val fwdZ = cos(newHeading)
        val facingSouthIntoGate = fwdZ < -0.35f && s.gear != TransmissionGear.Reverse

        // Front ENTRY Gate: open only when approaching from outside North side (-139.2..-127.0) facing South;
        // close only after the entire 12m bus moves clear inside (-148.5) or backs clear away (-125.0).
        if (!entryBarrierLatchOpen) {
            if (newPos.x in -1.5f..15.8f && newPos.z in -139.2f..-127.0f && facingSouthIntoGate) {
                entryBarrierLatchOpen = true
            }
        } else {
            if (newPos.z < -148.5f || newPos.z > -125.0f || newPos.x !in -3.0f..17.2f) {
                entryBarrierLatchOpen = false
            }
        }
        val entryTargetFrac = if (entryBarrierLatchOpen) 1f else 0f
        val entryStep = 2.8f * dt
        val nextEntryBarrierFrac = when {
            s.sakthanEntryBarrierOpenFrac < entryTargetFrac ->
                min(entryTargetFrac, s.sakthanEntryBarrierOpenFrac + entryStep)
            s.sakthanEntryBarrierOpenFrac > entryTargetFrac ->
                max(entryTargetFrac, s.sakthanEntryBarrierOpenFrac - entryStep)
            else -> entryTargetFrac
        }

        // Physical blocking when ENTRY barrier is closed (e.g., wrong direction from inside or reversing)
        if (!entryBarrierLatchOpen && nextEntryBarrierFrac < 0.72f && newPos.x in 0.4f..14.0f) {
            if (s.busPos.z <= -145.4f && newPos.z > -145.6f) {
                newPos = newPos.copy(z = -145.6f)
                speedMps = 0f
                newBanner = "Wrong Way! ENTRY Gate Closed — Use Back EXIT Gate"
            } else if (s.busPos.z >= -133.2f && newPos.z < -133.0f) {
                newPos = newPos.copy(z = -133.0f)
                speedMps = 0f
                newBanner = "Wrong Direction at ENTRY Gate — Drive Forward Facing Gate"
            }
        }

        // Back EXIT Gate: open only when approaching from inside courtyard (-206.7..-194.0) facing South (-Z);
        // close only after the entire 12m bus moves clear outside (-215.5) or backs clear away (-192.0).
        if (!exitBarrierLatchOpen) {
            if (newPos.x in 18.0f..35.0f && newPos.z in -206.7f..-194.0f && facingSouthIntoGate) {
                exitBarrierLatchOpen = true
            }
        } else {
            if (newPos.z < -215.5f || newPos.z > -192.0f || newPos.x !in 16.5f..36.5f) {
                exitBarrierLatchOpen = false
            }
        }
        val exitTargetFrac = if (exitBarrierLatchOpen) 1f else 0f
        val exitStep = 2.8f * dt
        val nextExitBarrierFrac = when {
            s.sakthanExitBarrierOpenFrac < exitTargetFrac ->
                min(exitTargetFrac, s.sakthanExitBarrierOpenFrac + exitStep)
            s.sakthanExitBarrierOpenFrac > exitTargetFrac ->
                max(exitTargetFrac, s.sakthanExitBarrierOpenFrac - exitStep)
            else -> exitTargetFrac
        }

        // Physical blocking when EXIT barrier is closed (e.g., wrong direction from outside road trying to enter)
        if (!exitBarrierLatchOpen && nextExitBarrierFrac < 0.72f && newPos.x in 19.6f..33.4f) {
            if (s.busPos.z <= -213.2f && newPos.z > -213.0f) {
                newPos = newPos.copy(z = -213.0f)
                speedMps = 0f
                newBanner = "Wrong Way! EXIT Gate Closed — Enter via Front ENTRY Gate"
            } else if (s.busPos.z >= -200.5f && newPos.z < -200.7f) {
                newPos = newPos.copy(z = -200.7f)
                speedMps = 0f
                newBanner = "Wrong Direction at EXIT Gate — Drive Forward Facing EXIT"
            }
        }

        // 4C. Vadakke Bus Stand Direction-Aware Boom Barriers (East ENTRY Z=199.82, West EXIT Z=201.18)
        val facingNorthIntoVadakkeEntry = fwdZ > 0.35f && s.gear != TransmissionGear.Reverse
        if (!vadakkeEntryBarrierLatchOpen) {
            if (newPos.x in -28.0f..-15.0f && newPos.z in 187.5f..199.8f && facingNorthIntoVadakkeEntry) {
                vadakkeEntryBarrierLatchOpen = true
            }
        } else {
            if (newPos.z > 209.5f || newPos.z < 185.5f || newPos.x !in -29.5f..-13.5f) {
                vadakkeEntryBarrierLatchOpen = false
            }
        }
        val vadakkeEntryTargetFrac = if (vadakkeEntryBarrierLatchOpen) 1f else 0f
        val nextVadakkeEntryFrac = when {
            s.vadakkeEntryBarrierOpenFrac < vadakkeEntryTargetFrac ->
                min(vadakkeEntryTargetFrac, s.vadakkeEntryBarrierOpenFrac + entryStep)
            s.vadakkeEntryBarrierOpenFrac > vadakkeEntryTargetFrac ->
                max(vadakkeEntryTargetFrac, s.vadakkeEntryBarrierOpenFrac - entryStep)
            else -> vadakkeEntryTargetFrac
        }
        if (!vadakkeEntryBarrierLatchOpen && nextVadakkeEntryFrac < 0.72f && newPos.x in -27.5f..-15.5f) {
            if (s.busPos.z >= 206.4f && newPos.z < 206.6f) {
                newPos = newPos.copy(z = 206.6f)
                speedMps = 0f
                newBanner = "Wrong Way! Vadakke ENTRY Gate Closed — Use West EXIT Gate"
            } else if (s.busPos.z <= 193.2f && newPos.z > 193.0f) {
                newPos = newPos.copy(z = 193.0f)
                speedMps = 0f
                newBanner = "Wrong Direction at Vadakke ENTRY Gate — Drive Forward Facing Gate"
            }
        }

        if (!vadakkeExitBarrierLatchOpen) {
            if (newPos.x in -54.0f..-41.0f && newPos.z in 201.2f..214.0f && facingSouthIntoGate) {
                vadakkeExitBarrierLatchOpen = true
            }
        } else {
            if (newPos.z < 191.5f || newPos.z > 216.0f || newPos.x !in -55.5f..-39.5f) {
                vadakkeExitBarrierLatchOpen = false
            }
        }
        val vadakkeExitTargetFrac = if (vadakkeExitBarrierLatchOpen) 1f else 0f
        val nextVadakkeExitFrac = when {
            s.vadakkeExitBarrierOpenFrac < vadakkeExitTargetFrac ->
                min(vadakkeExitTargetFrac, s.vadakkeExitBarrierOpenFrac + exitStep)
            s.vadakkeExitBarrierOpenFrac > vadakkeExitTargetFrac ->
                max(vadakkeExitTargetFrac, s.vadakkeExitBarrierOpenFrac - exitStep)
            else -> vadakkeExitTargetFrac
        }
        if (!vadakkeExitBarrierLatchOpen && nextVadakkeExitFrac < 0.72f && newPos.x in -53.5f..-41.5f) {
            if (s.busPos.z <= 193.8f && newPos.z > 193.6f) {
                newPos = newPos.copy(z = 193.6f)
                speedMps = 0f
                newBanner = "Wrong Way! Vadakke EXIT Gate Closed — Enter via East ENTRY Gate"
            } else if (s.busPos.z >= 207.2f && newPos.z < 207.4f) {
                newPos = newPos.copy(z = 207.4f)
                speedMps = 0f
                newBanner = "Wrong Direction at Vadakke EXIT Gate — Drive Forward Facing EXIT"
            }
        }

        // 4D. Thrissur Railway Station Direction-Aware Boom Barriers (East Wall X=-64: South ENTRY Z=-174..-162 Westbound, North EXIT Z=-114..-102 Eastbound)
        val fwdX = sin(newHeading)
        val facingWestIntoStationEntry = fwdX < -0.35f && s.gear != TransmissionGear.Reverse
        val facingEastOutStationExit = fwdX > 0.35f && s.gear != TransmissionGear.Reverse

        if (!stationEntryBarrierLatchOpen) {
            if (newPos.z in -174.2f..-161.8f && newPos.x in -63.5f..-50.5f && facingWestIntoStationEntry) {
                stationEntryBarrierLatchOpen = true
            }
        } else {
            if (newPos.x < -72.5f || newPos.x > -48.5f || newPos.z !in -175.5f..-160.5f) {
                stationEntryBarrierLatchOpen = false
            }
        }
        val stationEntryTargetFrac = if (stationEntryBarrierLatchOpen) 1f else 0f
        val nextStationEntryFrac = when {
            s.stationEntryBarrierOpenFrac < stationEntryTargetFrac ->
                min(stationEntryTargetFrac, s.stationEntryBarrierOpenFrac + entryStep)
            s.stationEntryBarrierOpenFrac > stationEntryTargetFrac ->
                max(stationEntryTargetFrac, s.stationEntryBarrierOpenFrac - entryStep)
            else -> stationEntryTargetFrac
        }
        if (!stationEntryBarrierLatchOpen && nextStationEntryFrac < 0.72f && newPos.z in -173.8f..-162.2f) {
            if (s.busPos.x <= -69.5f && newPos.x > -69.7f) {
                newPos = newPos.copy(x = -69.7f)
                speedMps = 0f
                newBanner = "Wrong Way! Station ENTRY Gate Closed — Use North EXIT Gate"
            } else if (s.busPos.x >= -57.1f && newPos.x < -56.9f) {
                newPos = newPos.copy(x = -56.9f)
                speedMps = 0f
                newBanner = "Wrong Direction at Station ENTRY Gate — Drive Forward Facing Gate"
            }
        }

        if (!stationExitBarrierLatchOpen) {
            if (newPos.z in -114.2f..-101.8f && newPos.x in -77.5f..-64.8f && facingEastOutStationExit) {
                stationExitBarrierLatchOpen = true
            }
        } else {
            if (newPos.x > -55.5f || newPos.x < -79.5f || newPos.z !in -115.5f..-100.5f) {
                stationExitBarrierLatchOpen = false
            }
        }
        val stationExitTargetFrac = if (stationExitBarrierLatchOpen) 1f else 0f
        val nextStationExitFrac = when {
            s.stationExitBarrierOpenFrac < stationExitTargetFrac ->
                min(stationExitTargetFrac, s.stationExitBarrierOpenFrac + exitStep)
            s.stationExitBarrierOpenFrac > stationExitTargetFrac ->
                max(stationExitTargetFrac, s.stationExitBarrierOpenFrac - exitStep)
            else -> stationExitTargetFrac
        }
        if (!stationExitBarrierLatchOpen && nextStationExitFrac < 0.72f && newPos.z in -113.8f..-102.2f) {
            if (s.busPos.x >= -58.3f && newPos.x < -58.1f) {
                newPos = newPos.copy(x = -58.1f)
                speedMps = 0f
                newBanner = "Wrong Way! Station EXIT Gate Closed — Enter via South ENTRY Gate"
            } else if (s.busPos.x <= -71.0f && newPos.x > -71.2f) {
                newPos = newPos.copy(x = -71.2f)
                speedMps = 0f
                newBanner = "Wrong Direction at Station EXIT Gate — Drive Forward Facing EXIT"
            }
        }

        // 4E. Shoranur Bus Terminal Boom Barriers (West Wall X=126: South ENTRY Z=3038..3050 Eastbound, North EXIT Z=3100..3112 Westbound)
        if (!shoranurTermEntryBarrierLatchOpen) {
            if (newPos.z in 3037.5f..3050.5f && newPos.x in 113.0f..126.5f && facingEastOutStationExit) {
                shoranurTermEntryBarrierLatchOpen = true
            }
        } else {
            if (newPos.x > 136.0f || newPos.x < 110.0f || newPos.z !in 3036.0f..3052.0f) {
                shoranurTermEntryBarrierLatchOpen = false
            }
        }
        val shorTermEntryTarget = if (shoranurTermEntryBarrierLatchOpen) 1f else 0f
        val nextShoranurTermEntryFrac = when {
            s.shoranurTermEntryBarrierOpenFrac < shorTermEntryTarget ->
                min(shorTermEntryTarget, s.shoranurTermEntryBarrierOpenFrac + entryStep)
            s.shoranurTermEntryBarrierOpenFrac > shorTermEntryTarget ->
                max(shorTermEntryTarget, s.shoranurTermEntryBarrierOpenFrac - entryStep)
            else -> shorTermEntryTarget
        }

        if (!shoranurTermExitBarrierLatchOpen) {
            if (newPos.z in 3099.5f..3112.5f && newPos.x in 125.5f..139.0f && facingWestIntoStationEntry) {
                shoranurTermExitBarrierLatchOpen = true
            }
        } else {
            if (newPos.x < 115.5f || newPos.x > 141.5f || newPos.z !in 3098.0f..3114.0f) {
                shoranurTermExitBarrierLatchOpen = false
            }
        }
        val shorTermExitTarget = if (shoranurTermExitBarrierLatchOpen) 1f else 0f
        val nextShoranurTermExitFrac = when {
            s.shoranurTermExitBarrierOpenFrac < shorTermExitTarget ->
                min(shorTermExitTarget, s.shoranurTermExitBarrierOpenFrac + exitStep)
            s.shoranurTermExitBarrierOpenFrac > shorTermExitTarget ->
                max(shorTermExitTarget, s.shoranurTermExitBarrierOpenFrac - exitStep)
            else -> shorTermExitTarget
        }

        // 4F. Shoranur Junction Railway Station Boom Barriers (East Wall X=74: South ENTRY Z=3192..3204 Westbound, North EXIT Z=3266..3278 Eastbound)
        if (!shoranurStationEntryBarrierLatchOpen) {
            if (newPos.z in 3191.5f..3204.5f && newPos.x in 73.5f..87.0f && facingWestIntoStationEntry) {
                shoranurStationEntryBarrierLatchOpen = true
            }
        } else {
            if (newPos.x < 64.0f || newPos.x > 89.5f || newPos.z !in 3190.0f..3206.0f) {
                shoranurStationEntryBarrierLatchOpen = false
            }
        }
        val shorStationEntryTarget = if (shoranurStationEntryBarrierLatchOpen) 1f else 0f
        val nextShoranurStationEntryFrac = when {
            s.shoranurStationEntryBarrierOpenFrac < shorStationEntryTarget ->
                min(shorStationEntryTarget, s.shoranurStationEntryBarrierOpenFrac + entryStep)
            s.shoranurStationEntryBarrierOpenFrac > shorStationEntryTarget ->
                max(shorStationEntryTarget, s.shoranurStationEntryBarrierOpenFrac - entryStep)
            else -> shorStationEntryTarget
        }

        if (!shoranurStationExitBarrierLatchOpen) {
            if (newPos.z in 3265.5f..3278.5f && newPos.x in 61.0f..74.5f && facingEastOutStationExit) {
                shoranurStationExitBarrierLatchOpen = true
            }
        } else {
            if (newPos.x > 84.5f || newPos.x < 58.5f || newPos.z !in 3264.0f..3280.0f) {
                shoranurStationExitBarrierLatchOpen = false
            }
        }
        val shorStationExitTarget = if (shoranurStationExitBarrierLatchOpen) 1f else 0f
        val nextShoranurStationExitFrac = when {
            s.shoranurStationExitBarrierOpenFrac < shorStationExitTarget ->
                min(shorStationExitTarget, s.shoranurStationExitBarrierOpenFrac + exitStep)
            s.shoranurStationExitBarrierOpenFrac > shorStationExitTarget ->
                max(shorStationExitTarget, s.shoranurStationExitBarrierOpenFrac - exitStep)
            else -> shorStationExitTarget
        }

        // 4G. Paliyekkara 12-Lane FASTag Toll Plaza Boom Barrier (60 Hz Live RFID Open, Physical Gate Stop & Auto-Close!)
        val tollCenterX = Zone3Nh544SouthRenderer.TOLL_CENTER_X
        val tollCenterZ = Zone3Nh544SouthRenderer.TOLL_CENTER_Z
        val inTollLanesX = newPos.x in 967.0f..1032.0f
        val detectedTollLane = if (inTollLanesX) Zone3Nh544SouthRenderer.nearestTollLaneIndex(newPos.x) else -1
        if (detectedTollLane in 0 until Zone3Nh544SouthRenderer.TOLL_LANE_COUNT &&
            newPos.z in (tollCenterZ - 32.0f)..(tollCenterZ + 32.0f)
        ) {
            val pivotZ = if (detectedTollLane < 6) tollCenterZ - 3.6f else tollCenterZ + 3.6f
            val isLaneActive = Zone3Nh544SouthRenderer.isTollLaneActiveFastag(detectedTollLane)
            val distFromBoomZ = newPos.z - pivotZ // > 0 means North of boom arm, < 0 means South of boom arm
            val headingSouth = fwdZ < -0.25f
            val headingNorth = fwdZ > 0.25f

            if (isLaneActive) {
                // Switch active lane cleanly only when not mid-crossing another lane
                if (!paliyekkaraTollBarrierLatchOpen || paliyekkaraTollActiveLane == detectedTollLane) {
                    paliyekkaraTollActiveLane = detectedTollLane
                }
                // Trigger FASTag RFID scan & lift barrier when approaching within 15.0m of boom arm (front bumper ~9m away!)
                val inOpenApproachSouth = headingSouth && distFromBoomZ in -4.0f..15.0f
                val inOpenApproachNorth = headingNorth && distFromBoomZ in -15.0f..4.0f
                if (!paliyekkaraTollBarrierLatchOpen && (inOpenApproachSouth || inOpenApproachNorth)) {
                    paliyekkaraTollBarrierLatchOpen = true
                    paliyekkaraTollActiveLane = detectedTollLane
                    if (!tollPaidInSession && newWallet >= 50) {
                        tollPaidInSession = true
                        newWallet -= 50
                        newBanner = "FASTag Scanned • ₹50 Deducted (Bal ₹$newWallet) • Lane ${detectedTollLane + 1} Gate Opening!"
                    } else {
                        newBanner = "FASTag Verified • Paliakkara Lane ${detectedTollLane + 1} Gate Opening!"
                    }
                } else if (paliyekkaraTollBarrierLatchOpen) {
                    // Close gate smoothly as soon as the bus rear clears 7.5m past the boom arm OR reverses away!
                    val clearedPastBoom =
                        (headingSouth && distFromBoomZ < -7.5f) ||
                            (headingNorth && distFromBoomZ > 7.5f) ||
                            abs(distFromBoomZ) > 16.5f ||
                            detectedTollLane != paliyekkaraTollActiveLane
                    if (clearedPastBoom) {
                        paliyekkaraTollBarrierLatchOpen = false
                        newBanner = "Paliakkara Toll Cleared • Lane ${paliyekkaraTollActiveLane + 1} Barrier Closing"
                    }
                }
            } else {
                // Closed outer lane (Lane 1 or Lane 12): keep boom gate locked CLOSED
                if (paliyekkaraTollActiveLane == detectedTollLane) {
                    paliyekkaraTollBarrierLatchOpen = false
                }
            }
        } else {
            paliyekkaraTollBarrierLatchOpen = false
            if (hypot(newPos.x - tollCenterX, newPos.z - tollCenterZ) > 62f) {
                tollPaidInSession = false
            }
        }

        // Smooth 1.05-second pneumatic boom barrier opening / 0.90-second closing animation at Paliakkara Toll Plaza
        val tollOpenStep = dt / 1.05f
        val tollCloseStep = dt / 0.90f
        val nextPaliyekkaraTollBarrierFrac = if (paliyekkaraTollBarrierLatchOpen) {
            (s.paliyekkaraTollBarrierOpenFrac + tollOpenStep).coerceAtMost(1f)
        } else {
            (s.paliyekkaraTollBarrierOpenFrac - tollCloseStep).coerceAtLeast(0f)
        }

        // Physical Boom Barrier Stop: Prevents ghosting through closed lanes OR active gates while still lifting!
        if (detectedTollLane in 0 until Zone3Nh544SouthRenderer.TOLL_LANE_COUNT &&
            newPos.z in (tollCenterZ - 14.0f)..(tollCenterZ + 14.0f)
        ) {
            val pivotZ = if (detectedTollLane < 6) tollCenterZ - 3.6f else tollCenterZ + 3.6f
            val isLaneActive = Zone3Nh544SouthRenderer.isTollLaneActiveFastag(detectedTollLane)
            val gateIsClear = isLaneActive &&
                detectedTollLane == paliyekkaraTollActiveLane &&
                nextPaliyekkaraTollBarrierFrac >= 0.62f
            if (!gateIsClear) {
                // Bus front/rear overhang is ~5.8m; hold cleanly 0.6m ahead of the horizontal boom arm!
                val stopDist = 6.4f
                val prevDz = s.busPos.z - pivotZ
                val currDz = newPos.z - pivotZ
                if (prevDz >= stopDist - 0.35f && currDz < stopDist) {
                    newPos = newPos.copy(z = pivotZ + stopDist)
                    if (speedMps > 0f && fwdZ < 0f) speedMps = 0f
                    if (speedMps < 0f && fwdZ > 0f) speedMps = 0f
                    newBanner = if (isLaneActive) {
                        "FASTag Scanning • Please Wait — Lane ${detectedTollLane + 1} Gate Opening..."
                    } else {
                        "Lane ${detectedTollLane + 1} CLOSED — Use Green FASTag Lane (Lanes 2–11)"
                    }
                } else if (prevDz <= -(stopDist - 0.35f) && currDz > -stopDist) {
                    newPos = newPos.copy(z = pivotZ - stopDist)
                    if (speedMps > 0f && fwdZ > 0f) speedMps = 0f
                    if (speedMps < 0f && fwdZ < 0f) speedMps = 0f
                    newBanner = if (isLaneActive) {
                        "FASTag Scanning • Please Wait — Lane ${detectedTollLane + 1} Gate Opening..."
                    } else {
                        "Lane ${detectedTollLane + 1} CLOSED — Use Green FASTag Lane (Lanes 2–11)"
                    }
                }
            }
        }

        // 4H. Zone 5: Nattika 10-Lane FASTag Toll Plaza (NH 66) — 60 Hz Live RFID Open, Physical Boom Stop & Auto-Close
        val nattikaX = Zone5Nh66CoastalRenderer.NATTIKA_CENTER_X
        val nattikaZ = Zone5Nh66CoastalRenderer.NATTIKA_CENTER_Z
        val inNattikaLanesX = newPos.x in (nattikaX - 27.2f)..(nattikaX + 27.2f)
        val detectedNattikaLane = if (inNattikaLanesX) {
            Zone5Nh66CoastalRenderer.nearestNattikaTollLaneIndex(newPos.x)
        } else -1
        if (detectedNattikaLane in 0 until Zone5Nh66CoastalRenderer.NATTIKA_LANE_COUNT &&
            newPos.z in (nattikaZ - 32.0f)..(nattikaZ + 32.0f)
        ) {
            val pivotZ = if (detectedNattikaLane < 5) nattikaZ - 3.2f else nattikaZ + 3.2f
            val isLaneActive = Zone5Nh66CoastalRenderer.isNattikaLaneActiveFastag(detectedNattikaLane)
            val distFromBoomZ = newPos.z - pivotZ
            val headingSouth = fwdZ < -0.25f
            val headingNorth = fwdZ > 0.25f

            if (isLaneActive) {
                if (!nattikaTollBarrierLatchOpen || nattikaTollActiveLane == detectedNattikaLane) {
                    nattikaTollActiveLane = detectedNattikaLane
                }
                val inOpenApproachSouth = headingSouth && distFromBoomZ in -4.0f..15.0f
                val inOpenApproachNorth = headingNorth && distFromBoomZ in -15.0f..4.0f
                if (!nattikaTollBarrierLatchOpen && (inOpenApproachSouth || inOpenApproachNorth)) {
                    nattikaTollBarrierLatchOpen = true
                    nattikaTollActiveLane = detectedNattikaLane
                    if (!nattikaTollPaidInSession && newWallet >= 50) {
                        nattikaTollPaidInSession = true
                        newWallet -= 50
                        newBanner = "Nattika NH 66 FASTag Scanned • ₹50 Deducted (Bal ₹$newWallet) • Lane ${detectedNattikaLane + 1} Gate Opening!"
                    } else {
                        newBanner = "Nattika NH 66 FASTag Verified • Lane ${detectedNattikaLane + 1} Gate Opening!"
                    }
                } else if (nattikaTollBarrierLatchOpen) {
                    val clearedPastBoom =
                        (headingSouth && distFromBoomZ < -7.5f) ||
                            (headingNorth && distFromBoomZ > 7.5f) ||
                            abs(distFromBoomZ) > 16.5f ||
                            detectedNattikaLane != nattikaTollActiveLane
                    if (clearedPastBoom) {
                        nattikaTollBarrierLatchOpen = false
                        newBanner = "Nattika Toll Cleared • Lane ${nattikaTollActiveLane + 1} Barrier Closing"
                    }
                }
            } else {
                if (nattikaTollActiveLane == detectedNattikaLane) {
                    nattikaTollBarrierLatchOpen = false
                }
            }
        } else {
            nattikaTollBarrierLatchOpen = false
            if (hypot(newPos.x - nattikaX, newPos.z - nattikaZ) > 62f) {
                nattikaTollPaidInSession = false
            }
        }

        val nextNattikaTollBarrierFrac = if (nattikaTollBarrierLatchOpen) {
            (s.nattikaTollBarrierOpenFrac + tollOpenStep).coerceAtMost(1f)
        } else {
            (s.nattikaTollBarrierOpenFrac - tollCloseStep).coerceAtLeast(0f)
        }

        if (detectedNattikaLane in 0 until Zone5Nh66CoastalRenderer.NATTIKA_LANE_COUNT &&
            newPos.z in (nattikaZ - 14.0f)..(nattikaZ + 14.0f)
        ) {
            val pivotZ = if (detectedNattikaLane < 5) nattikaZ - 3.2f else nattikaZ + 3.2f
            val isLaneActive = Zone5Nh66CoastalRenderer.isNattikaLaneActiveFastag(detectedNattikaLane)
            val gateIsClear = isLaneActive &&
                detectedNattikaLane == nattikaTollActiveLane &&
                nextNattikaTollBarrierFrac >= 0.62f
            if (!gateIsClear) {
                val stopDist = 6.4f
                val prevDz = s.busPos.z - pivotZ
                val currDz = newPos.z - pivotZ
                if (prevDz >= stopDist - 0.35f && currDz < stopDist) {
                    newPos = newPos.copy(z = pivotZ + stopDist)
                    if (speedMps > 0f && fwdZ < 0f) speedMps = 0f
                    if (speedMps < 0f && fwdZ > 0f) speedMps = 0f
                    newBanner = if (isLaneActive) {
                        "Nattika FASTag Scanning • Please Wait — Lane ${detectedNattikaLane + 1} Gate Opening..."
                    } else {
                        "Nattika Lane ${detectedNattikaLane + 1} CLOSED — Use Green FASTag Lane (Lanes 2–9)"
                    }
                } else if (prevDz <= -(stopDist - 0.35f) && currDz > -stopDist) {
                    newPos = newPos.copy(z = pivotZ - stopDist)
                    if (speedMps > 0f && fwdZ > 0f) speedMps = 0f
                    if (speedMps < 0f && fwdZ < 0f) speedMps = 0f
                    newBanner = if (isLaneActive) {
                        "Nattika FASTag Scanning • Please Wait — Lane ${detectedNattikaLane + 1} Gate Opening..."
                    } else {
                        "Nattika Lane ${detectedNattikaLane + 1} CLOSED — Use Green FASTag Lane (Lanes 2–9)"
                    }
                }
            }
        }

        // =========================================================================
        // ZONE 4: MUTHUVARA ANIMATED RAILWAY LEVEL CROSSING (LC-14 ON SH 69)
        // 5-State Interactive Sequence: OPEN -> CLOSING (4s) -> LOCKED (3s) ->
        // TRAIN_PASSING (17.5s, 65 km/h 8-Coach ICF Express) -> OPENING (4s)
        // =========================================================================
        val lcCyclePeriod = 48.0f
        muthuvaraLcCycleSec = (muthuvaraLcCycleSec + dt) % lcCyclePeriod
        val lcT = muthuvaraLcCycleSec
        val nextMuthuvaraLcPhase: MuthuvaraLcPhase
        val nextMuthuvaraLcBarrierClosedFrac: Float
        val lcTrainSpeedMps = 18.06f // Exact 65 km/h
        when {
            lcT < 14.0f -> {
                nextMuthuvaraLcPhase = MuthuvaraLcPhase.STATE_OPEN
                nextMuthuvaraLcBarrierClosedFrac = 0f
                ShoranurRailwaySystem.muthuvaraLcTrainHeadX = Float.NaN
            }
            lcT < 18.0f -> {
                nextMuthuvaraLcPhase = MuthuvaraLcPhase.STATE_CLOSING
                nextMuthuvaraLcBarrierClosedFrac = ((lcT - 14.0f) / 4.0f).coerceIn(0f, 1f)
                ShoranurRailwaySystem.muthuvaraLcTrainHeadX = -340.0f - (lcT - 14.0f) * lcTrainSpeedMps
            }
            lcT < 21.0f -> {
                nextMuthuvaraLcPhase = MuthuvaraLcPhase.STATE_LOCKED
                nextMuthuvaraLcBarrierClosedFrac = 1f
                ShoranurRailwaySystem.muthuvaraLcTrainHeadX = -340.0f - (lcT - 14.0f) * lcTrainSpeedMps
            }
            lcT < 38.5f -> {
                nextMuthuvaraLcPhase = MuthuvaraLcPhase.STATE_TRAIN_PASSING
                nextMuthuvaraLcBarrierClosedFrac = 1f
                ShoranurRailwaySystem.muthuvaraLcTrainHeadX = -340.0f - (lcT - 14.0f) * lcTrainSpeedMps
            }
            lcT < 42.5f -> {
                nextMuthuvaraLcPhase = MuthuvaraLcPhase.STATE_OPENING
                nextMuthuvaraLcBarrierClosedFrac = (1f - (lcT - 38.5f) / 4.0f).coerceIn(0f, 1f)
                ShoranurRailwaySystem.muthuvaraLcTrainHeadX = -340.0f - (lcT - 14.0f) * lcTrainSpeedMps
            }
            else -> {
                nextMuthuvaraLcPhase = MuthuvaraLcPhase.STATE_OPEN
                nextMuthuvaraLcBarrierClosedFrac = 0f
                ShoranurRailwaySystem.muthuvaraLcTrainHeadX = Float.NaN
            }
        }

        // Physical Muthuvara LC-14 Boom Gate Enforcement & Live Proximity Audio (Bell, Horn, Rail Click-Clack)
        val distToMuthuvaraLc = hypot(
            newPos.x - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X,
            newPos.z - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z
        )
        if (distToMuthuvaraLc < 170f) {
            val lcAtten = (1f - distToMuthuvaraLc / 170f).coerceIn(0f, 1f)
            val bellActive = nextMuthuvaraLcPhase == MuthuvaraLcPhase.STATE_CLOSING ||
                nextMuthuvaraLcPhase == MuthuvaraLcPhase.STATE_LOCKED ||
                nextMuthuvaraLcPhase == MuthuvaraLcPhase.STATE_TRAIN_PASSING
            synthLcBellGain = if (bellActive) lcAtten * 0.52f else 0f

            // Electric loco blasts dual-tone Indian Railways horn twice during LOCKED & approach
            val hornWindow = (lcT in 18.2f..20.1f) || (lcT in 22.8f..24.4f)
            synthLcHornGain = if (hornWindow) lcAtten * 0.65f else 0f

            // Rhythmic rail joint click-clack while locomotive & 8 coaches thunder across the crossing
            val trainHeadX = ShoranurRailwaySystem.muthuvaraLcTrainHeadX
            val trainNearCrossing = !trainHeadX.isNaN() && trainHeadX in -785f..-440f
            synthLcRailClackGain = if (trainNearCrossing && distToMuthuvaraLc < 140f) {
                (1f - distToMuthuvaraLc / 140f).coerceIn(0f, 1f) * 0.62f
            } else 0f

            // Live HUD warning banners when approaching Muthuvara LC-14
            if (distToMuthuvaraLc < 58f && nextMuthuvaraLcPhase != MuthuvaraLcPhase.STATE_OPEN) {
                newBanner = when (nextMuthuvaraLcPhase) {
                    MuthuvaraLcPhase.STATE_CLOSING ->
                        "Muthuvara LC-14 Closing — Warning Bell Active • Halt Behind White Stop Line!"
                    MuthuvaraLcPhase.STATE_LOCKED ->
                        "Muthuvara LC-14 LOCKED — Express Train Approaching (65 km/h) • Wait Behind Gate"
                    MuthuvaraLcPhase.STATE_TRAIN_PASSING ->
                        "Train Passing (65 km/h) — Southern Railway 8-Coach Guruvayur Express"
                    MuthuvaraLcPhase.STATE_OPENING ->
                        "Train Cleared • Muthuvara LC-14 Boom Barriers Opening — Proceed Safely"
                    MuthuvaraLcPhase.STATE_OPEN -> newBanner
                }
            }
        } else {
            synthLcBellGain = 0f
            synthLcHornGain = 0f
            synthLcRailClackGain = 0f
        }

        // Physical stop behind South gate (Z = 603.8) and North gate (Z = 616.2) when boom arms are lowered (> 20% closed)
        if (newPos.x in (Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X - 8.5f)..(Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X + 8.5f) &&
            nextMuthuvaraLcBarrierClosedFrac > 0.20f
        ) {
            val lcZ = Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z
            val southStopZ = lcZ - Zone4KoleRailHeritageRenderer.MUTHUVARA_GATE_Z_OFFSET - 5.9f // ~597.9f (front bumper right at white stop line!)
            val northStopZ = lcZ + Zone4KoleRailHeritageRenderer.MUTHUVARA_GATE_Z_OFFSET + 5.9f // ~622.1f
            if (s.busPos.z <= southStopZ + 0.40f && newPos.z > southStopZ) {
                newPos = newPos.copy(z = southStopZ)
                if (speedMps > 0f && fwdZ > 0f) speedMps = 0f
                if (speedMps < 0f && fwdZ < 0f) speedMps = 0f
            } else if (s.busPos.z >= northStopZ - 0.40f && newPos.z < northStopZ) {
                newPos = newPos.copy(z = northStopZ)
                if (speedMps > 0f && fwdZ < 0f) speedMps = 0f
                if (speedMps < 0f && fwdZ > 0f) speedMps = 0f
            }
        }

        // 4I. Real-Time District AI Road Traffic + Paliyekkara/Nattika Toll Queues + Muthuvara LC-14 Railway Gate Blocks
        val trafficTelemetry = ThrissurTrafficSystem.step(
            dt = dt,
            simTimeSec = simTimeAccumulator,
            roads = s.roads,
            playerPos = newPos,
            playerHeadingRad = newHeading,
            manikandanalSignal = curSignalAspect,
            muthuvaraLcPhase = nextMuthuvaraLcPhase,
            muthuvaraLcBarrierClosedFrac = nextMuthuvaraLcBarrierClosedFrac,
            playerPaliyekkaraLane = paliyekkaraTollActiveLane,
            playerPaliyekkaraOpenFrac = nextPaliyekkaraTollBarrierFrac,
            playerNattikaLane = nattikaTollActiveLane,
            playerNattikaOpenFrac = nextNattikaTollBarrierFrac
        )
        if (collisionBannerHoldSec <= 0f && trafficTelemetry.activeBlockBanner != null && newBanner == s.statusBanner) {
            newBanner = trafficTelemetry.activeBlockBanner
        }

        val nearManikandanal = hypot(newPos.x - 20.9f, newPos.z - (-63.0f)) < 26f
        if (nearManikandanal || lastRoadScanX.isNaN() || (moveDx * moveDx + moveDz * moveDz) >= 9.0f) {
            lastRoadScanX = newPos.x
            lastRoadScanZ = newPos.z

            val totalSplineLen = curFrames.lastOrNull()?.cumulativeDist?.coerceAtLeast(1f) ?: 1f
            val curSplineDist = curFrames.getOrNull(closestFrameIdx)?.cumulativeDist ?: 0f
            val fracRemaining = (1f - (curSplineDist / totalSplineLen)).coerceIn(0f, 1f)
            remKm = (curRoad.realDistanceM / 1000f) * fracRemaining

            val insideSakthanTerminal = newPos.x in -31f..41f && newPos.z in -208f..-139.5f
            val insideVadakkeTerminal = newPos.x in -64.5f..-6.5f && newPos.z in 200.5f..251.0f
            val insideStationTerminal = newPos.x in -83.0f..-64.0f && newPos.z in -204.0f..-62.0f
            val nearTcrStation = newPos.x in -126f..-42f && newPos.z in -224f..-55f
            streetName = when {
                insideSakthanTerminal -> {
                    when {
                        newPos.z < -194f -> "Sakthan Bus Terminal • Back Exit Gate"
                        newPos.z > -152f -> "Sakthan Bus Terminal • Front Entry Gate"
                        newPos.x in -22.25f..17.75f && newPos.z in -191f..-170f -> {
                            val bayIdx = (((newPos.x - (-22.25f)) / 5.0f).toInt() + 1).coerceIn(1, 8)
                            "Sakthan Bus Terminal • Bay $bayIdx"
                        }
                        else -> "Sakthan Bus Terminal • Boarding Bays 1–8"
                    }
                }
                insideVadakkeTerminal -> {
                    when {
                        newPos.z < 210f && newPos.x < -36f -> "Vadakke Bus Stand • West Exit Gate"
                        newPos.z < 210f && newPos.x >= -36f -> "Vadakke Bus Stand • East Entry Gate"
                        newPos.x in -56.5f..-14.5f && newPos.z in 220f..238f -> {
                            val bayIdx = (((newPos.x - (-55.475f)) / 4.95f).toInt() + 1).coerceIn(1, 8)
                            "Vadakke Bus Stand • Bay $bayIdx"
                        }
                        else -> "Vadakke Bus Stand • Boarding Bays 1–8"
                    }
                }
                insideStationTerminal -> {
                    when {
                        newPos.x > -71.5f && newPos.z in -175f..-161f -> "Thrissur Railway Station • South Entry Gate"
                        newPos.x > -71.5f && newPos.z in -115f..-101f -> "Thrissur Railway Station • North Exit Gate"
                        newPos.x in -82.0f..-73.5f && newPos.z in -166f..-108f -> {
                            val bayIdx = (((newPos.z - (-165.0f)) / 14.0f).toInt() + 1).coerceIn(1, 4)
                            "Thrissur Railway Station • Bay $bayIdx"
                        }
                        else -> "Thrissur Railway Station • Express Bays 1–4"
                    }
                }
                nearTcrStation -> "Thrissur Railway Station (TCR) • Station Road"
                nearManikandanal -> "Manikandanal Signal (${curSignalAspect.label} ${curSignalCountdown}s) • Swaraj Round South"
                hypot(newPos.x - 3.1f, newPos.z - 18.0f) < 82f -> "Swaraj Round (1-Way Clockwise) • Vadakkumnathan Temple"
                hypot(newPos.x - (-35.5f), newPos.z - 224f) < 52f -> "Vadakke Stand • Thrissur North Bus Stand"
                newPos.x in Zone4KoleRailHeritageRenderer.ENAMAVU_WEST_RAMP_END_X..Zone4KoleRailHeritageRenderer.ENAMAVU_EAST_RAMP_START_X &&
                    abs(newPos.z - Zone4KoleRailHeritageRenderer.ENAMAVU_CENTER_Z) < 22f ->
                    "Enamavu Sluice Regulator Bridge • 18 Gates (Lake Side North / Sea Side South)"
                newPos.x in Zone4KoleRailHeritageRenderer.KOLE_WEST_RAMP_END_X..Zone4KoleRailHeritageRenderer.KOLE_EAST_RAMP_START_X &&
                    newPos.z in 75f..215f ->
                    "SH 75 • Thrissur Kole Wetlands Ramsar Site (1.5m Raised Bunds)"
                hypot(newPos.x - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X, newPos.z - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z) < 62f ->
                    "SH 69 • Muthuvara Railway Level Crossing (LC-14 • Thrissur–Guruvayur Line)"
                newPos.z in Zone4KoleRailHeritageRenderer.BHARATHAPPUZHA_RAMP_SOUTH_Z..Zone4KoleRailHeritageRenderer.BHARATHAPPUZHA_RAMP_NORTH_Z &&
                    newPos.x in 65f..150f ->
                    "Bharathappuzha (Nila River) Dual Bridges • SH 22 & 1902 Cochin Bridge"
                hypot(newPos.x - (-2625f), newPos.z - 1260f) < 68f -> "Guruvayur Sree Krishna Temple • East Nada Square"
                newPos.x in -2790f..-2682f && newPos.z in 1252f..1335f -> "Guruvayur Municipal Bus Terminal • 8 Sawtooth Bays"
                hypot(newPos.x - (-4500f), newPos.z - 1080f) < 85f -> "Chavakkad Beach & Azhi Estuary"
                hypot(newPos.x - (-2220f), newPos.z - (-2410f)) < 85f -> "Triprayar - Nattika Beach Terminus"
                newPos.x in 84f..100f && newPos.z in 1515f..1845f -> "Wadakkanchery Railway Overbridge (ROB) • SH 22"
                newPos.x in 78f..128f && newPos.z in 1845f..1965f -> "Wadakkanchery Town • SH 22"
                newPos.x in 126f..196f && newPos.z in 3015f..3135f -> {
                    if (newPos.x in 156f..178f && newPos.z in 3048f..3098f) {
                        val bayIdx = (((newPos.z - 3048.4f) / SHORANUR_TERM_BAY_PITCH_Z).toInt() + 1).coerceIn(1, 8)
                        "Shoranur Bus Terminal • Bay $bayIdx"
                    } else "Shoranur Bus Terminal • Sawtooth Pavilion"
                }
                newPos.x in 16f..74f && newPos.z in 3165f..3310f -> {
                    if (newPos.x in 50f..63f && newPos.z in 3200f..3264f) {
                        val bayIdx = (((newPos.z - 3200.0f) / 16.0f).toInt() + 1).coerceIn(1, 4)
                        "Shoranur Junction Railway Station (SRR) • Bay $bayIdx"
                    } else "Shoranur Junction Railway Station (SRR)"
                }
                newPos.x in 2155f..3120f && abs(newPos.z - Zone2Nh544KuthiranRenderer.KUTHIRAN_LEFT_CENTER_Z) < 10f ->
                    "Kuthiran Twin Tunnel • Left Tube (955m Eastbound • 60 km/h)"
                newPos.x in 2155f..3120f && abs(newPos.z - Zone2Nh544KuthiranRenderer.KUTHIRAN_RIGHT_CENTER_Z) < 10f ->
                    "Kuthiran Twin Tunnel • Right Tube (944m Westbound • 60 km/h)"
                newPos.x in 1005f..1095f && newPos.z in 70f..340f ->
                    "Mannuthy 4-Way Elevated Flyover & Interchange • NH 544"
                hypot(newPos.x - Zone2Nh544KuthiranRenderer.PEECHI_CHECKPOST_X, newPos.z - Zone2Nh544KuthiranRenderer.PEECHI_CHECKPOST_Z) < 30f ->
                    "Peechi-Vazhani Wildlife Sanctuary Check-Post • SH 51"
                newPos.x in 3375f..3455f && newPos.z in 85f..175f ->
                    "Peechi Dam Masonry Spillway & Reservoir Viewpoint"
                newPos.z in Zone3Nh544SouthRenderer.OLLUR_ROB_RAMP_SOUTH_Z..Zone3Nh544SouthRenderer.OLLUR_ROB_RAMP_NORTH_Z &&
                    abs(newPos.x - Zone3Nh544SouthRenderer.ollurRobCenterXAt(newPos.z)) < 18f ->
                    "Ollur Railway Overbridge (ROB) • NH 544 (25kV Electrified Mainline Below)"
                newPos.z in -1868f..-1744f && newPos.x in 954f..1045f -> {
                    val laneIdx = Zone3Nh544SouthRenderer.nearestTollLaneIndex(newPos.x)
                    when {
                        newPos.x < 967.0f || newPos.x > 1032.0f ->
                            "Paliakkara Toll Plaza • Free Outer Bypass Lane (No Toll)"
                        laneIdx in 0 until Zone3Nh544SouthRenderer.TOLL_LANE_COUNT &&
                            Zone3Nh544SouthRenderer.isTollLaneActiveFastag(laneIdx) ->
                            "Paliakkara Toll Plaza • FASTag Lane ${laneIdx + 1} (NH 544)"
                        laneIdx in 0 until Zone3Nh544SouthRenderer.TOLL_LANE_COUNT ->
                            "Paliakkara Toll Plaza • Lane ${laneIdx + 1} (CLOSED — Use Green FASTag Lane)"
                        else -> "Paliakkara 12-Lane Toll Plaza • NH 544"
                    }
                }
                newPos.z in Zone3Nh544SouthRenderer.KURUMALI_RAMP_SOUTH_Z..Zone3Nh544SouthRenderer.KURUMALI_RAMP_NORTH_Z &&
                    abs(newPos.x - Zone3Nh544SouthRenderer.KURUMALI_CENTER_X) < 22f ->
                    "Kurumali River Bridge • Puthukkad (NH 544 South)"
                hypot(newPos.x - Zone3Nh544SouthRenderer.KODAKARA_X, newPos.z - Zone3Nh544SouthRenderer.KODAKARA_Z) < 120f ->
                    "Kodakara Junction • NH 544 South (4-Lane Divided Highway)"
                newPos.z in Zone3Nh544SouthRenderer.CHALAKUDY_RAMP_SOUTH_Z..Zone3Nh544SouthRenderer.CHALAKUDY_RAMP_NORTH_Z &&
                    abs(newPos.x - Zone3Nh544SouthRenderer.CHALAKUDY_CENTER_X) < 26f ->
                    "Chalakudy River Bridge (280m Span) • NH 544 South"
                newPos.z <= -5320f && abs(newPos.x - Zone3Nh544SouthRenderer.CHALAKUDY_TERM_X) < 55f ->
                    "Chalakudy South Bus Bay & NH 544 Terminus"
                newPos.z in Zone5Nh66CoastalRenderer.CHETTUVA_RAMP_SOUTH_Z..Zone5Nh66CoastalRenderer.CHETTUVA_RAMP_NORTH_Z &&
                    abs(newPos.x - Zone5Nh66CoastalRenderer.CHETTUVA_CENTER_X) < 24f ->
                    "Chettuva Backwaters Bridge • NH 66 (Mangroves & Chinese Fishing Nets)"
                newPos.z in (Zone5Nh66CoastalRenderer.NATTIKA_CENTER_Z - 64f)..(Zone5Nh66CoastalRenderer.NATTIKA_CENTER_Z + 64f) &&
                    abs(newPos.x - Zone5Nh66CoastalRenderer.NATTIKA_CENTER_X) < 40f -> {
                    val laneIdx = Zone5Nh66CoastalRenderer.nearestNattikaTollLaneIndex(newPos.x)
                    when {
                        abs(newPos.x - Zone5Nh66CoastalRenderer.NATTIKA_CENTER_X) > 27.2f ->
                            "Nattika Toll Plaza • Free Outer Bypass Lane (NH 66)"
                        laneIdx in 0 until Zone5Nh66CoastalRenderer.NATTIKA_LANE_COUNT &&
                            Zone5Nh66CoastalRenderer.isNattikaLaneActiveFastag(laneIdx) ->
                            "Nattika Toll Plaza • FASTag Lane ${laneIdx + 1} (NH 66)"
                        laneIdx in 0 until Zone5Nh66CoastalRenderer.NATTIKA_LANE_COUNT ->
                            "Nattika Toll Plaza • Lane ${laneIdx + 1} (CLOSED — Use Green FASTag Lane)"
                        else -> "Nattika 10-Lane Toll Plaza • NH 66 Coastal Highway"
                    }
                }
                newPos.z in Zone5Nh66CoastalRenderer.CANOLI_RAMP_SOUTH_Z..Zone5Nh66CoastalRenderer.CANOLI_RAMP_NORTH_Z &&
                    abs(newPos.x - Zone5Nh66CoastalRenderer.CANOLI_CENTER_X) < 24f ->
                    "Triprayar Canoli Canal Bridge & Sree Rama Temple Water Ghat (Meenootu)"
                newPos.z in Zone5Nh66CoastalRenderer.KOTTAPURAM_RAMP_SOUTH_Z..Zone5Nh66CoastalRenderer.KOTTAPURAM_RAMP_NORTH_Z &&
                    abs(newPos.x - Zone5Nh66CoastalRenderer.KOTTAPURAM_CENTER_X) < 26f ->
                    "Kottapuram / Kodungallur NW-3 High-Clearance Bridge • NH 66"
                newPos.z <= -4340f && abs(newPos.x - Zone5Nh66CoastalRenderer.MOOTHAKUNNAM_TERM_X) < 55f ->
                    "Moothakunnam Border & NH 66 South Turnaround Bay"
                newPos.x in 72f..148f && newPos.z in -106f..-60f ->
                    "Our Lady of Dolours Basilica (Puthanpally) • High Road"
                else -> {
                    val stepList = curRoad.stepNames
                    if (stepList.isNotEmpty()) {
                        val idx = ((1f - fracRemaining) * stepList.size).toInt().coerceIn(0, stepList.lastIndex)
                        stepList[idx]
                    } else curRoad.name
                }
            }
        }

        // 6. Camera Rig & Road-Mesh Anti-Clipping
        val newSpeedKmh = abs(speedMps) * 3.6f
        val yawFollowRate = when (s.cameraMode) {
            CameraViewMode.Cockpit -> 0f
            CameraViewMode.Chase -> 10.5f
            CameraViewMode.Orbit -> 12.5f
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
        val baseNextFuel = (s.fuelLiters - fuelUsed).coerceIn(1f, 100f)

        val (nextRefuel, nextFuel, refuelWallet) = stepFuelStationSimulation(
            dt = dt,
            busPos = newPos,
            busHeadingRad = newHeading,
            speedKmh = newSpeedKmh,
            curFuelLiters = baseNextFuel,
            curWalletInr = newWallet,
            curRefuel = s.refuel
        )
        newWallet = refuelWallet

        // Cinematic Refueling Cutscene Camera Override (CAM 1 -> CAM 2 -> CAM 3)
        // Overrides ANY active player camera (Chase, Cockpit, Orbit) smoothly like a real video clip,
        // and restores the player's exact camera automatically once refueling completes!
        var finalCamFocus = lerpedFocus
        var finalCamEye = safeCameraEye
        var finalCamFov = nextCameraFovDeg
        if (nextRefuel.isCutsceneActive && nextRefuel.cutsceneCam != RefuelCutsceneCam.NONE) {
            val (cutFocus, cutEye, cutFov) = computeRefuelCutsceneCamera(
                refuel = nextRefuel,
                busPos = newPos
            )
            val isHardCut = (nextRefuel.cutsceneCam != lastCutsceneCam)
            if (isHardCut) {
                finalCamFocus = cutFocus
                finalCamEye = cutEye
                finalCamFov = cutFov
            } else {
                val trackT = (dt * 5.2f).coerceIn(0f, 1f)
                finalCamFocus = lerpVec3(s.camFocusPos, cutFocus, trackT)
                finalCamEye = lerpVec3(s.camEyePos, cutEye, trackT)
                finalCamFov = lerp(s.cameraFovDeg, cutFov, trackT)
            }
            lastCutsceneCam = nextRefuel.cutsceneCam
        } else {
            lastCutsceneCam = RefuelCutsceneCam.NONE
        }

        val twoPi = (2.0 * PI).toFloat()
        val nextWheelSpin = ((s.wheelSpinRad + (kinematicSpeedMps / 0.52f) * dt) % twoPi + twoPi) % twoPi
        val accelMps2 = (speedMps - s.signedSpeedMps) / dt
        val robForwardSlope = ShoranurRailwaySystem.computeRoadForwardSlope(newPos.x, newPos.z, newHeading)
        val robSlopePitch = (-robForwardSlope).coerceIn(-0.145f, 0.145f)
        val targetPitch = (-accelMps2 * 0.0026f + collisionJoltPitch + robSlopePitch).coerceIn(-0.165f, 0.165f)
        val nextBodyPitch = lerp(s.bodyPitchRad, targetPitch, (dt * 7.5f).coerceIn(0f, 1f))
        val targetRoll = (smoothedWheelSteerInput * (newSpeedKmh / 105f).coerceIn(0f, 1f) * 0.028f + collisionJoltRoll)
            .coerceIn(-0.038f, 0.038f)
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
                camFocusPos = finalCamFocus,
                camEyePos = finalCamEye,
                cameraFovDeg = finalCamFov,
                currentRoadStreetName = streetName,
                distanceToRouteEndKm = remKm,
                walletInr = newWallet,
                fuelLiters = nextFuel,
                refuel = nextRefuel,
                trafficSignalCycleSec = trafficSignalAccumulator,
                manikandanalSignal = curSignalAspect,
                manikandanalSignalCountdownSec = curSignalCountdown,
                sakthanEntryBarrierOpenFrac = nextEntryBarrierFrac,
                sakthanExitBarrierOpenFrac = nextExitBarrierFrac,
                vadakkeEntryBarrierOpenFrac = nextVadakkeEntryFrac,
                vadakkeExitBarrierOpenFrac = nextVadakkeExitFrac,
                stationEntryBarrierOpenFrac = nextStationEntryFrac,
                stationExitBarrierOpenFrac = nextStationExitFrac,
                shoranurTermEntryBarrierOpenFrac = nextShoranurTermEntryFrac,
                shoranurTermExitBarrierOpenFrac = nextShoranurTermExitFrac,
                shoranurStationEntryBarrierOpenFrac = nextShoranurStationEntryFrac,
                shoranurStationExitBarrierOpenFrac = nextShoranurStationExitFrac,
                paliyekkaraTollBarrierOpenFrac = nextPaliyekkaraTollBarrierFrac,
                paliyekkaraTollActiveLaneIdx = paliyekkaraTollActiveLane,
                nattikaTollBarrierOpenFrac = nextNattikaTollBarrierFrac,
                nattikaTollActiveLaneIdx = nattikaTollActiveLane,
                muthuvaraLcPhase = nextMuthuvaraLcPhase,
                muthuvaraLcBarrierClosedFrac = nextMuthuvaraLcBarrierClosedFrac,
                simTimeSec = simTimeAccumulator,
                engineStartPromptVisible = !s.engineRunning && !s.isEngineCranking && enginePromptHoldSec > 0f,
                statusBanner = newBanner
            )
        }
    }

    // =========================================================================
    // HIGHWAY FUEL STATION ATTENDANTS, VOICE DIALOGUE & REAL-TIME REFUEL ENGINE
    // =========================================================================

    private fun stationLocalToWorld(
        st: ThrissurFuelStationSystem.FuelStationPlacement,
        lx: Float,
        lz: Float
    ): Pair<Float, Float> {
        val wx = st.cx + lx * st.cosYaw - lz * st.sinYaw
        val wz = st.cz + lx * st.sinYaw + lz * st.cosYaw
        return wx to wz
    }

    private fun smoothStep01(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun moveWorkerToward(
        targetX: Float,
        targetZ: Float,
        speedMps: Float,
        dt: Float
    ): Boolean {
        val dx = targetX - activeWorkerX
        val dz = targetZ - activeWorkerZ
        val dist = hypot(dx, dz)
        if (dist <= 0.035f) {
            activeWorkerX = targetX
            activeWorkerZ = targetZ
            return true
        }
        // Smooth deceleration as attendant arrives so there is never an abrupt snap
        val decel = (dist / 0.42f).coerceIn(0.35f, 1.0f)
        val step = (speedMps * decel * dt).coerceAtMost(dist)
        activeWorkerX += (dx / dist) * step
        activeWorkerZ += (dz / dist) * step
        faceWorkerToward(targetX, targetZ, dt * 1.45f)
        activeWorkerWalkPhase += dt * (1.42f * decel.coerceAtLeast(0.65f))
        return dist - step <= 0.035f
    }

    private fun faceWorkerToward(targetX: Float, targetZ: Float, dt: Float) {
        val desiredYaw = atan2(targetX - activeWorkerX, targetZ - activeWorkerZ)
        var diff = desiredYaw - activeWorkerYaw
        while (diff > PI) diff -= (2.0 * PI).toFloat()
        while (diff < -PI) diff += (2.0 * PI).toFloat()
        activeWorkerYaw += diff * (dt * 7.5f).coerceIn(0f, 1f)
    }

    private fun stepFuelStationSimulation(
        dt: Float,
        busPos: Vec3,
        busHeadingRad: Float,
        speedKmh: Float,
        curFuelLiters: Float,
        curWalletInr: Int,
        curRefuel: RefuelLiveState
    ): Triple<RefuelLiveState, Float, Int> {
        val stations = ThrissurFuelStationSystem.getPlacements()
        if (stations.isEmpty()) {
            return Triple(curRefuel, curFuelLiters, curWalletInr)
        }

        // Locate nearest fuel station within 140m
        var nearest: ThrissurFuelStationSystem.FuelStationPlacement? = null
        var nearestDistSq = 140f * 140f
        for (i in 0 until stations.size) {
            val st = stations[i]
            val dx = busPos.x - st.cx
            val dz = busPos.z - st.cz
            val dSq = dx * dx + dz * dz
            if (dSq < nearestDistSq) {
                nearestDistSq = dSq
                nearest = st
            }
        }

        if (nearest == null) {
            if (curRefuel.phase != RefuelPhase.NONE || curRefuel.isNearFuelStation) {
                fuelVoiceManager?.stopFuelFlowSound(playShutoffClunk = false)
                refuelBayStoppedSec = 0f
                refuelDismissedStationId = ""
                refuelCompletedStationId = ""
                activeWorkerInitialized = false
                fuelDoorOpenFrac = 0f
                return Triple(RefuelLiveState(), curFuelLiters, curWalletInr)
            }
            return Triple(curRefuel, curFuelLiters, curWalletInr)
        }

        // Bus orientation vectors in world XZ
        val busFwdX = sin(busHeadingRad)
        val busFwdZ = cos(busHeadingRad)
        val busRightX = busFwdZ
        val busRightZ = -busFwdX

        // Bus center in nearest station's local coordinates
        val relX = busPos.x - nearest.cx
        val relZ = busPos.z - nearest.cz
        val busLx = relX * nearest.cosYaw + relZ * nearest.sinYaw
        val busLz = -relX * nearest.sinYaw + relZ * nearest.cosYaw
        val inForecourt = busLx in -23.0f..23.0f && busLz in -11.5f..11.5f

        // STRICT FULL-BUS PARKING SLOT CHECK (ALL 4 CORNERS OF THE 12m HINO RM2 BUS MUST FIT INSIDE!):
        //   Side 1 (ROW 1 • DIESEL): X=[-6.85..6.85], Z=[-2.70..+1.10] (right / under-canopy side of Diesel Island)
        //   Side 2 (ROW 2 • PETROL): X=[-6.85..6.85], Z=[+4.30..+8.10] (right / rear side of Petrol Island)
        val busHalfLen = 5.65f
        val busHalfWid = 1.24f
        var dieselCorners = 0
        var petrolCorners = 0
        for (signL in intArrayOf(-1, 1)) {
            for (signW in intArrayOf(-1, 1)) {
                val cWx = busPos.x + busFwdX * (signL * busHalfLen) + busRightX * (signW * busHalfWid)
                val cWz = busPos.z + busFwdZ * (signL * busHalfLen) + busRightZ * (signW * busHalfWid)
                val cdx = cWx - nearest.cx
                val cdz = cWz - nearest.cz
                val clx = cdx * nearest.cosYaw + cdz * nearest.sinYaw
                val clz = -cdx * nearest.sinYaw + cdz * nearest.cosYaw
                if (clx in ThrissurFuelStationSystem.DIESEL_SLOT_MIN_X..ThrissurFuelStationSystem.DIESEL_SLOT_MAX_X &&
                    clz in ThrissurFuelStationSystem.DIESEL_SLOT_MIN_Z..ThrissurFuelStationSystem.DIESEL_SLOT_MAX_Z
                ) {
                    dieselCorners++
                }
                if (clx in ThrissurFuelStationSystem.PETROL_SLOT_MIN_X..ThrissurFuelStationSystem.PETROL_SLOT_MAX_X &&
                    clz in ThrissurFuelStationSystem.PETROL_SLOT_MIN_Z..ThrissurFuelStationSystem.PETROL_SLOT_MAX_Z
                ) {
                    petrolCorners++
                }
            }
        }

        val parkedSlotSide: FuelType? = when {
            dieselCorners == 4 -> FuelType.DIESEL
            petrolCorners == 4 -> FuelType.PETROL
            else -> null
        }
        val isParkedInBayBox = (parkedSlotSide != null)
        val nearestSlotSide: FuelType? = when {
            dieselCorners > petrolCorners -> FuelType.DIESEL
            petrolCorners > dieselCorners -> FuelType.PETROL
            dieselCorners > 0 -> FuelType.DIESEL
            inForecourt -> if (busLz <= 2.70f) FuelType.DIESEL else FuelType.PETROL
            else -> null
        }
        val cornersInSlotCount = maxOf(dieselCorners, petrolCorners)

        if (!isParkedInBayBox) {
            refuelBayStoppedSec = 0f
            if (!inForecourt) {
                refuelDismissedStationId = ""
                refuelCompletedStationId = ""
            }
        }

        // TWO SYMMETRICAL WORKERS — BOTH ON THE RIGHT (+Z) SLOT-FACING SIDE OF THEIR PUMP ISLANDS:
        //   - Diesel Worker 2 stands on ROW 1 • DIESEL (Z = -3.50m) facing Diesel Slot (+Z under canopy)
        //   - Petrol Worker 1 stands on ROW 2 • PETROL (Z = +3.50m) facing Petrol Slot (+Z rear lane)
        val dieselHomeLocalX = ThrissurFuelStationSystem.DIESEL_DISPENSER_X - 0.96f
        val dieselHomeLocalZ = ThrissurFuelStationSystem.DIESEL_ISLAND_Z + ThrissurFuelStationSystem.DIESEL_SLOT_SIDE_DIR * 0.88f
        val petrolHomeLocalX = ThrissurFuelStationSystem.PETROL_DISPENSER_X + 0.96f
        val petrolHomeLocalZ = ThrissurFuelStationSystem.PETROL_ISLAND_Z + ThrissurFuelStationSystem.PETROL_SLOT_SIDE_DIR * 0.88f

        val (dieselHomeWx, dieselHomeWz) = stationLocalToWorld(nearest, dieselHomeLocalX, dieselHomeLocalZ)
        val (petrolHomeWx, petrolHomeWz) = stationLocalToWorld(nearest, petrolHomeLocalX, petrolHomeLocalZ)
        val workerBaseY = nearest.baseY + 0.02f

        var phase = curRefuel.phase
        var dialogStep = curRefuel.dialogStep
        var selectedFuel = curRefuel.selectedFuelType
        var dispensedL = curRefuel.dispensedLiters
        var dispensedRs = curRefuel.dispensedRupees
        var targetL = curRefuel.targetLiters
        var speakerLabel = curRefuel.activeSpeakerLabel
        var dialogueLine = curRefuel.activeDialogueLine
        var outFuel = curFuelLiters
        var outWallet = curWalletInr
        var paidViaFleet = curRefuel.paidViaFleetCard

        // Automatically bind the active fuel bay & worker to whichever slot the bus is fully parked in!
        if (parkedSlotSide != null && (phase == RefuelPhase.NONE || phase == RefuelPhase.APPROACH_PROMPT)) {
            if (selectedFuel != parkedSlotSide) {
                selectedFuel = parkedSlotSide
                activeWorkerInitialized = false
            }
        }

        // Safety abort if bus drives away mid-refuel (> 10 km/h) or leaves the Parking Slot before cutscene
        if ((speedKmh > 10f && phase != RefuelPhase.NONE) ||
            (!isParkedInBayBox && (phase == RefuelPhase.APPROACH_PROMPT || phase == RefuelPhase.WORKER_APPROACHING || phase == RefuelPhase.DIALOG_OPEN))
        ) {
            fuelVoiceManager?.stopFuelFlowSound(playShutoffClunk = false)
            fuelVoiceManager?.stopSpeech()
            phase = RefuelPhase.NONE
            dialogStep = RefuelDialogStep.ORDER_MODE
            speakerLabel = ""
            dialogueLine = ""
            activeWorkerInitialized = false
            fuelDoorOpenFrac = 0f
        }

        // Active pump island geometry (both Diesel & Petrol use right-side +Z slots)
        val isDiesel = (selectedFuel == FuelType.DIESEL)
        val activeIslandZ = if (isDiesel) {
            ThrissurFuelStationSystem.DIESEL_ISLAND_Z
        } else {
            ThrissurFuelStationSystem.PETROL_ISLAND_Z
        }
        val activeDispenserX = if (isDiesel) {
            ThrissurFuelStationSystem.DIESEL_DISPENSER_X
        } else {
            ThrissurFuelStationSystem.PETROL_DISPENSER_X
        }
        val slotDir = if (isDiesel) {
            ThrissurFuelStationSystem.DIESEL_SLOT_SIDE_DIR
        } else {
            ThrissurFuelStationSystem.PETROL_SLOT_SIDE_DIR
        }
        // Physical hose side of the dispenser (-X side for Diesel dispX=+2.20, +X side for Petrol dispX=-2.20)
        // Completely clear of the front digital LCD meter!
        val hoseSideSign = if (isDiesel) -1f else 1f

        val (dispWx, dispWz) = stationLocalToWorld(nearest, activeDispenserX, activeIslandZ)

        // Exact side hose swivel outlet on the side chassis of the pump (NEVER on the meter!)
        val (hoseStartWx, hoseStartWz) = stationLocalToWorld(
            nearest,
            activeDispenserX + hoseSideSign * ThrissurFuelStationSystem.DISPENSER_HOSE_SIDE_OFFSET_X,
            activeIslandZ + slotDir * ThrissurFuelStationSystem.DISPENSER_HOSE_OUTLET_Z_OFF
        )
        val hoseStartY = nearest.baseY + ThrissurFuelStationSystem.DISPENSER_HOSE_OUTLET_Y

        // Exact side nozzle holster cradle where the nozzle rests when docked & where worker grabs/returns it
        val (holsterWx, holsterWz) = stationLocalToWorld(
            nearest,
            activeDispenserX + hoseSideSign * ThrissurFuelStationSystem.DISPENSER_HOLSTER_SIDE_OFFSET_X,
            activeIslandZ + slotDir * ThrissurFuelStationSystem.DISPENSER_HOLSTER_Z_OFF
        )
        val holsterY = nearest.baseY + ThrissurFuelStationSystem.DISPENSER_HOLSTER_Y

        // Worker stand position right beside the side holster (clear of the 24cm raised island curb!)
        val (workerAtPumpWx, workerAtPumpWz) = stationLocalToWorld(
            nearest,
            activeDispenserX + hoseSideSign * 0.86f,
            activeIslandZ + slotDir * 0.72f
        )

        // Exact side of the 12m Hino RM2 bus facing the active pump island
        val toPumpX = dispWx - busPos.x
        val toPumpZ = dispWz - busPos.z
        val pumpSideSign = if (toPumpX * busRightX + toPumpZ * busRightZ >= 0f) 1f else -1f

        // Outward normal from bus side wall toward pump island, and inward normal into fuel tank hole
        val wallOutX = busRightX * pumpSideSign
        val wallOutZ = busRightZ * pumpSideSign
        val intoTankX = -wallOutX
        val intoTankZ = -wallOutZ

        // Exact world center of the bus's recessed fuel filler opening on the pump-facing exterior wall
        val fillerLongOffset = if (isDiesel) -1.20f else 1.20f
        val fillerWx = busPos.x + wallOutX * ThrissurFuelStationSystem.BUS_SIDE_WALL_X + busFwdX * fillerLongOffset
        val fillerWz = busPos.z + wallOutZ * ThrissurFuelStationSystem.BUS_SIDE_WALL_X + busFwdZ * fillerLongOffset
        val fillerY = busPos.y + ThrissurFuelStationSystem.BUS_FUEL_DOOR_CENTER_Y

        // Ergonomic worker stand position beside bus fuel tank:
        //   - 0.56m outward from bus wall (within natural 2-bone right-arm reach; zero bus/door clipping!)
        //   - Shifted 0.16m toward worker's left so their RIGHT shoulder aligns dead-center with the filler hole!
        //   - Clamped safely clear of the raised concrete pump island curb (|lz - activeIslandZ| >= 0.76m)
        val rawTankStandWx = busPos.x + wallOutX * (ThrissurFuelStationSystem.BUS_SIDE_WALL_X + 0.56f) +
            busFwdX * (fillerLongOffset + pumpSideSign * 0.16f)
        val rawTankStandWz = busPos.z + wallOutZ * (ThrissurFuelStationSystem.BUS_SIDE_WALL_X + 0.56f) +
            busFwdZ * (fillerLongOffset + pumpSideSign * 0.16f)
        val tRelX = rawTankStandWx - nearest.cx
        val tRelZ = rawTankStandWz - nearest.cz
        val tLx = tRelX * nearest.cosYaw + tRelZ * nearest.sinYaw
        var tLz = -tRelX * nearest.sinYaw + tRelZ * nearest.cosYaw
        val minIslandClearZ = activeIslandZ + slotDir * 0.76f
        if (slotDir > 0f && tLz < minIslandClearZ) tLz = minIslandClearZ
        if (slotDir < 0f && tLz > minIslandClearZ) tLz = minIslandClearZ
        val (workerAtTankWx, workerAtTankWz) = stationLocalToWorld(nearest, tLx, tLz)

        // Worker greeting stand point near front cab / side window on the active slot side
        val rawGreetWx = busPos.x + wallOutX * (ThrissurFuelStationSystem.BUS_SIDE_WALL_X + 0.68f) + busFwdX * 1.45f
        val rawGreetWz = busPos.z + wallOutZ * (ThrissurFuelStationSystem.BUS_SIDE_WALL_X + 0.68f) + busFwdZ * 1.45f
        val gRelX = rawGreetWx - nearest.cx
        val gRelZ = rawGreetWz - nearest.cz
        val gLx = gRelX * nearest.cosYaw + gRelZ * nearest.sinYaw
        var gLz = -gRelX * nearest.sinYaw + gRelZ * nearest.cosYaw
        if (slotDir > 0f && gLz < minIslandClearZ) gLz = minIslandClearZ
        if (slotDir < 0f && gLz > minIslandClearZ) gLz = minIslandClearZ
        val (workerGreetWx, workerGreetWz) = stationLocalToWorld(nearest, gLx, gLz)

        // Helper: Exact 3D world position of the hinged bus fuel door's handle at any open fraction [0..1]
        // Door hinge is on the opposite vertical edge from the worker's stance (`hingeSign = -pumpSideSign`)
        // so the door swings cleanly OUTWARD and AWAY from the worker without ever clipping!
        fun fuelDoorHandleWorld(openFrac: Float): Vec3 {
            val hingeSign = -pumpSideSign
            val halfW = ThrissurFuelStationSystem.BUS_FUEL_DOOR_WIDTH * 0.5f
            val maxRad = Math.toRadians(ThrissurFuelStationSystem.BUS_FUEL_DOOR_MAX_OPEN_DEG.toDouble()).toFloat()
            val theta = openFrac.coerceIn(0f, 1f) * maxRad
            val sinT = sin(theta)
            val cosT = cos(theta)
            val hingeLocalX = pumpSideSign * (ThrissurFuelStationSystem.BUS_SIDE_WALL_X + 0.006f)
            val hingeLocalZ = fillerLongOffset + hingeSign * halfW
            // Leaf vector runs from hinge toward latch edge (-hingeSign when closed, swinging outward along pumpSideSign)
            val leafDirX = pumpSideSign * sinT
            val leafDirZ = -hingeSign * cosT
            // Exterior door normal (outward from bus when closed)
            val doorNormX = pumpSideSign * cosT
            val doorNormZ = hingeSign * sinT
            val handleDist = ThrissurFuelStationSystem.BUS_FUEL_DOOR_WIDTH * 0.84f
            val hLx = hingeLocalX + leafDirX * handleDist + doorNormX * 0.022f
            val hLz = hingeLocalZ + leafDirZ * handleDist + doorNormZ * 0.022f
            return Vec3(
                x = busPos.x + busRightX * hLx + busFwdX * hLz,
                y = busPos.y + ThrissurFuelStationSystem.BUS_FUEL_DOOR_CENTER_Y,
                z = busPos.z + busRightZ * hLx + busFwdZ * hLz
            )
        }

        // Exact palm grip positions for pre-insertion alignment and deep nozzle insertion inside the tank opening:
        // Spout tip sits at `P_palm + aimDir * 0.340f + up * 0.030f`, so:
        //   - At inserted palm (`-0.230m` along intoTank), spout tip penetrates 11cm INSIDE the dark tank hole!
        //   - At pre-insert palm (`-0.420m` along intoTank), spout tip aligns coaxially 8cm outside the hole!
        val nozzleAimY = -0.09f
        val nozzleAimNorm = sqrt(intoTankX * intoTankX + nozzleAimY * nozzleAimY + intoTankZ * intoTankZ)
        val tankAimX = intoTankX / nozzleAimNorm
        val tankAimY = nozzleAimY / nozzleAimNorm
        val tankAimZ = intoTankZ / nozzleAimNorm

        val palmInsertedX = fillerWx - tankAimX * 0.230f
        val palmInsertedY = fillerY - tankAimY * 0.230f - 0.030f
        val palmInsertedZ = fillerWz - tankAimZ * 0.230f

        val palmPreInsertX = fillerWx - tankAimX * 0.420f
        val palmPreInsertY = fillerY - tankAimY * 0.420f - 0.025f
        val palmPreInsertZ = fillerWz - tankAimZ * 0.420f

        // Direction from pump stand toward side holster (for natural wrist alignment when picking up / docking nozzle)
        val toHolsterDx = holsterWx - workerAtPumpWx
        val toHolsterDz = holsterWz - workerAtPumpWz
        val toHolsterLen = hypot(toHolsterDx, toHolsterDz).coerceAtLeast(0.01f)
        val holsterAimX = toHolsterDx / toHolsterLen
        val holsterAimZ = toHolsterDz / toHolsterLen

        val homeWx = if (isDiesel) dieselHomeWx else petrolHomeWx
        val homeWz = if (isDiesel) dieselHomeWz else petrolHomeWz

        if (!activeWorkerInitialized) {
            activeWorkerX = homeWx
            activeWorkerZ = homeWz
            activeWorkerYaw = atan2(busPos.x - homeWx, busPos.z - homeWz)
            activeWorkerInitialized = true
        }

        // STRICT FULL-SLOT GATE: Only summon the worker when ALL 4 corners of the bus are completely
        // inside either the Diesel or Petrol Parking Slot AND the bus has come to a full stop (< 1.2 km/h)!
        if (isParkedInBayBox && speedKmh < 1.2f) {
            if (phase == RefuelPhase.NONE) {
                refuelBayStoppedSec += dt
                if (refuelCompletedStationId == nearest.id || refuelDismissedStationId == nearest.id) {
                    phase = RefuelPhase.APPROACH_PROMPT
                } else if (refuelBayStoppedSec >= 0.45f) {
                    activeWorkerX = homeWx
                    activeWorkerZ = homeWz
                    activeWorkerWalkPhase = 0f
                    refuelPhaseTimerSec = 0f
                    dispensedL = 0f
                    dispensedRs = 0f
                    fuelDoorOpenFrac = 0f
                    workerSpokeConfirm = false
                    workerPaymentSettled = false
                    fuelDoorSoundPlayed = false
                    nozzleActionSoundPlayed = false
                    dialogStep = RefuelDialogStep.ORDER_MODE
                    phase = RefuelPhase.WORKER_APPROACHING
                    speakerLabel = selectedFuel.attendantTitle.uppercase(Locale.ROOT)
                    dialogueLine = "Welcome to the ${selectedFuel.displayName} bay, sir! Coming right over."
                }
            }
        } else if (phase == RefuelPhase.APPROACH_PROMPT && !isParkedInBayBox) {
            phase = RefuelPhase.NONE
            speakerLabel = ""
            dialogueLine = ""
        }

        var activeAnim = WorkerAnimState.IDLE
        var holdingNozzle = false
        var nozzleDocked = true
        var nozzleInsertedFrac = 0f
        var ikWeight = 0f
        var ikTargetX = fillerWx
        var ikTargetY = fillerY
        var ikTargetZ = fillerWz
        var ikAimX = tankAimX
        var ikAimY = tankAimY
        var ikAimZ = tankAimZ
        var torsoLeanRad = 0f

        when (phase) {
            RefuelPhase.NONE, RefuelPhase.APPROACH_PROMPT -> {
                activeWorkerX = homeWx
                activeWorkerZ = homeWz
                faceWorkerToward(busPos.x, busPos.z, dt)
                activeAnim = if (inForecourt) WorkerAnimState.WAVING_WELCOME else WorkerAnimState.IDLE
                fuelDoorOpenFrac = 0f
            }

            RefuelPhase.WORKER_APPROACHING -> {
                refuelPhaseTimerSec += dt
                val arrived = moveWorkerToward(workerGreetWx, workerGreetWz, speedMps = 1.65f, dt = dt)
                activeAnim = WorkerAnimState.WALKING
                if (arrived || refuelPhaseTimerSec > 3.8f) {
                    faceWorkerToward(busPos.x, busPos.z, 1f)
                    phase = RefuelPhase.DIALOG_OPEN
                    dialogStep = RefuelDialogStep.ORDER_MODE
                    refuelPhaseTimerSec = 0f
                    speakerLabel = selectedFuel.attendantTitle.uppercase(Locale.ROOT)
                    val greetLine = "Hello sir! Welcome to the ${selectedFuel.displayName} bay. Full tank, by rupees, or by liters?"
                    dialogueLine = greetLine
                    fuelVoiceManager?.speakWorker(
                        text = greetLine,
                        isPetrolWorker = (selectedFuel == FuelType.PETROL),
                        flush = true
                    )
                }
            }

            RefuelPhase.DIALOG_OPEN -> {
                faceWorkerToward(busPos.x, busPos.z, dt)
                activeAnim = WorkerAnimState.TALKING
            }

            RefuelPhase.DRIVER_REPLYING -> {
                refuelPhaseTimerSec += dt
                faceWorkerToward(busPos.x, busPos.z, dt)
                activeAnim = WorkerAnimState.TALKING
                if (refuelPhaseTimerSec >= 2.0f && !workerSpokeConfirm) {
                    workerSpokeConfirm = true
                    val roundedRs = (targetL * selectedFuel.pricePerLiterInr).toInt()
                    val confirmText = if (curRefuel.orderMode == RefuelOrderMode.FULL_TANK) {
                        "Sure sir! Full tank of ${selectedFuel.displayName.lowercase(Locale.ROOT)} coming right up!"
                    } else {
                        "Sure sir! Filling ${targetL.toInt().coerceAtLeast(1)} liters of ${selectedFuel.displayName.lowercase(Locale.ROOT)} for $roundedRs rupees."
                    }
                    speakerLabel = selectedFuel.attendantTitle.uppercase(Locale.ROOT)
                    dialogueLine = confirmText
                    fuelVoiceManager?.speakWorker(
                        text = confirmText,
                        isPetrolWorker = (selectedFuel == FuelType.PETROL),
                        flush = false
                    )
                }
                if (refuelPhaseTimerSec >= 2.85f) {
                    // STEP 1: Approach the bus fuel tank to open the fuel door
                    phase = RefuelPhase.WALKING_TO_TANK_TO_OPEN_DOOR
                    refuelPhaseTimerSec = 0f
                    fuelDoorSoundPlayed = false
                    nozzleActionSoundPlayed = false
                }
            }

            // =========================================================================
            // STEP 1: APPROACH BUS FUEL TANK & TURN TOWARD FUEL DOOR
            // =========================================================================
            RefuelPhase.WALKING_TO_TANK_TO_OPEN_DOOR -> {
                refuelPhaseTimerSec += dt
                val arrived = moveWorkerToward(workerAtTankWx, workerAtTankWz, speedMps = 1.55f, dt = dt)
                activeAnim = if (arrived) WorkerAnimState.OPENING_DOOR else WorkerAnimState.WALKING
                if (arrived) {
                    faceWorkerToward(fillerWx, fillerWz, dt * 1.4f)
                }
                val desiredYaw = atan2(fillerWx - activeWorkerX, fillerWz - activeWorkerZ)
                var yawErr = desiredYaw - activeWorkerYaw
                while (yawErr > PI) yawErr -= (2.0 * PI).toFloat()
                while (yawErr < -PI) yawErr += (2.0 * PI).toFloat()
                if ((arrived && abs(yawErr) < 0.14f) || refuelPhaseTimerSec >= 3.4f) {
                    phase = RefuelPhase.OPENING_FUEL_DOOR
                    refuelPhaseTimerSec = 0f
                    fuelDoorSoundPlayed = false
                    speakerLabel = selectedFuel.attendantTitle.uppercase(Locale.ROOT)
                    dialogueLine = "Opening the bus fuel tank door now, sir."
                }
            }

            // =========================================================================
            // STEP 2: REACH DOOR HANDLE & SMOOTHLY ROTATE HINGED FUEL DOOR OPEN (0° -> 112°)
            // =========================================================================
            RefuelPhase.OPENING_FUEL_DOOR -> {
                refuelPhaseTimerSec += dt
                faceWorkerToward(fillerWx, fillerWz, dt)
                activeAnim = WorkerAnimState.OPENING_DOOR

                val reachT = smoothStep01(refuelPhaseTimerSec / 0.48f)
                val swingT = smoothStep01((refuelPhaseTimerSec - 0.48f) / 0.82f)
                val retractT = smoothStep01((refuelPhaseTimerSec - 1.32f) / 0.36f)

                if (refuelPhaseTimerSec >= 0.46f && !fuelDoorSoundPlayed) {
                    fuelDoorSoundPlayed = true
                    fuelVoiceManager?.playFuelDoorLatchSound(opening = true)
                }

                fuelDoorOpenFrac = swingT
                val handlePos = fuelDoorHandleWorld(fuelDoorOpenFrac)
                ikWeight = reachT * (1f - retractT)
                ikTargetX = handlePos.x
                ikTargetY = handlePos.y
                ikTargetZ = handlePos.z
                ikAimX = intoTankX
                ikAimY = -0.18f
                ikAimZ = intoTankZ
                torsoLeanRad = 0.14f * ikWeight

                if (refuelPhaseTimerSec >= 1.72f) {
                    fuelDoorOpenFrac = 1f
                    phase = RefuelPhase.WALKING_TO_PUMP_FOR_NOZZLE
                    refuelPhaseTimerSec = 0f
                    nozzleActionSoundPlayed = false
                }
            }

            // =========================================================================
            // STEP 3A: WALK TO SIDE OF FUEL PUMP DISPENSER (TOWARD SIDE NOZZLE HOLDER)
            // =========================================================================
            RefuelPhase.WALKING_TO_PUMP_FOR_NOZZLE -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                val arrived = moveWorkerToward(workerAtPumpWx, workerAtPumpWz, speedMps = 1.60f, dt = dt)
                activeAnim = if (arrived) WorkerAnimState.REACHING_PUMP else WorkerAnimState.WALKING
                if (arrived) {
                    faceWorkerToward(holsterWx, holsterWz, dt * 1.4f)
                }
                val desiredYaw = atan2(holsterWx - activeWorkerX, holsterWz - activeWorkerZ)
                var yawErr = desiredYaw - activeWorkerYaw
                while (yawErr > PI) yawErr -= (2.0 * PI).toFloat()
                while (yawErr < -PI) yawErr += (2.0 * PI).toFloat()
                if ((arrived && abs(yawErr) < 0.16f) || refuelPhaseTimerSec >= 3.2f) {
                    phase = RefuelPhase.PICKING_UP_NOZZLE
                    refuelPhaseTimerSec = 0f
                    nozzleActionSoundPlayed = false
                }
            }

            // =========================================================================
            // STEP 3B: REACH SIDE HOLSTER, GRIP NOZZLE HANDLE & LIFT OUT SMOOTHLY
            // =========================================================================
            RefuelPhase.PICKING_UP_NOZZLE -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                faceWorkerToward(holsterWx, holsterWz, dt)
                activeAnim = WorkerAnimState.REACHING_PUMP

                val reachT = smoothStep01(refuelPhaseTimerSec / 0.52f)
                val liftT = smoothStep01((refuelPhaseTimerSec - 0.54f) / 0.62f)
                val settleT = smoothStep01((refuelPhaseTimerSec - 1.05f) / 0.25f)

                if (refuelPhaseTimerSec >= 0.52f && !nozzleActionSoundPlayed) {
                    nozzleActionSoundPlayed = true
                    fuelVoiceManager?.playNozzleClick(inserting = true)
                }

                val grabbed = refuelPhaseTimerSec >= 0.52f
                holdingNozzle = grabbed
                nozzleDocked = !grabbed

                // Smoothly lift nozzle up (+12cm) and out toward worker (+26cm) from side holster
                val carryTargetX = holsterWx - holsterAimX * 0.26f
                val carryTargetY = holsterY + 0.08f
                val carryTargetZ = holsterWz - holsterAimZ * 0.26f

                ikWeight = reachT * (1f - settleT * 0.72f)
                ikTargetX = holsterWx + (carryTargetX - holsterWx) * liftT
                ikTargetY = holsterY + (carryTargetY - holsterY) * liftT
                ikTargetZ = holsterWz + (carryTargetZ - holsterWz) * liftT
                ikAimX = holsterAimX
                ikAimY = -0.15f + 0.10f * liftT
                ikAimZ = holsterAimZ
                torsoLeanRad = 0.11f * (1f - liftT) * reachT

                if (refuelPhaseTimerSec >= 1.32f) {
                    phase = RefuelPhase.WALKING_TO_TANK_WITH_NOZZLE
                    refuelPhaseTimerSec = 0f
                    nozzleActionSoundPlayed = false
                }
            }

            // =========================================================================
            // STEP 4A: WALK BACK TO BUS FUEL TANK CARRYING NOZZLE WITH DYNAMIC HOSE
            // =========================================================================
            RefuelPhase.WALKING_TO_TANK_WITH_NOZZLE -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                holdingNozzle = true
                nozzleDocked = false
                val arrived = moveWorkerToward(workerAtTankWx, workerAtTankWz, speedMps = 1.50f, dt = dt)
                activeAnim = if (arrived) WorkerAnimState.HOLDING_NOZZLE_IDLE else WorkerAnimState.WALKING
                if (arrived) {
                    faceWorkerToward(fillerWx, fillerWz, dt * 1.4f)
                }
                val desiredYaw = atan2(fillerWx - activeWorkerX, fillerWz - activeWorkerZ)
                var yawErr = desiredYaw - activeWorkerYaw
                while (yawErr > PI) yawErr -= (2.0 * PI).toFloat()
                while (yawErr < -PI) yawErr += (2.0 * PI).toFloat()
                if ((arrived && abs(yawErr) < 0.16f) || refuelPhaseTimerSec >= 3.2f) {
                    phase = RefuelPhase.INSERTING_NOZZLE
                    refuelPhaseTimerSec = 0f
                    nozzleActionSoundPlayed = false
                }
            }

            // =========================================================================
            // STEP 4B: ALIGN COAXIALLY WITH FUEL TANK HOLE & INSERT NOZZLE TIP INSIDE
            // =========================================================================
            RefuelPhase.INSERTING_NOZZLE -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                holdingNozzle = true
                nozzleDocked = false
                faceWorkerToward(fillerWx, fillerWz, dt)
                activeAnim = WorkerAnimState.HOLDING_NOZZLE_IDLE

                val alignT = smoothStep01(refuelPhaseTimerSec / 0.52f)
                val insertT = smoothStep01((refuelPhaseTimerSec - 0.52f) / 0.68f)

                ikWeight = alignT
                ikTargetX = palmPreInsertX + (palmInsertedX - palmPreInsertX) * insertT
                ikTargetY = palmPreInsertY + (palmInsertedY - palmPreInsertY) * insertT
                ikTargetZ = palmPreInsertZ + (palmInsertedZ - palmPreInsertZ) * insertT
                ikAimX = tankAimX
                ikAimY = tankAimY
                ikAimZ = tankAimZ
                nozzleInsertedFrac = insertT
                torsoLeanRad = 0.12f * alignT

                if (refuelPhaseTimerSec >= 1.18f && !nozzleActionSoundPlayed) {
                    nozzleActionSoundPlayed = true
                    fuelVoiceManager?.playNozzleClick(inserting = true)
                    fuelVoiceManager?.startFuelFlowSound()
                }
                if (refuelPhaseTimerSec >= 1.28f) {
                    phase = RefuelPhase.FUELING_IN_PROGRESS
                    refuelPhaseTimerSec = 0f
                    speakerLabel = selectedFuel.attendantTitle.uppercase(Locale.ROOT)
                    dialogueLine = "Nozzle locked in! Pumping ${selectedFuel.displayName.lowercase(Locale.ROOT)} now."
                }
            }

            // =========================================================================
            // STEP 5: REFUELLING IN PROGRESS (STABLE POSE, NOZZLE VISIBLY INSIDE TANK)
            // =========================================================================
            RefuelPhase.FUELING_IN_PROGRESS -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                holdingNozzle = true
                nozzleDocked = false
                nozzleInsertedFrac = 1f
                faceWorkerToward(fillerWx, fillerWz, dt)
                activeAnim = WorkerAnimState.FUELING

                // Keep worker's cupped right hand & nozzle firmly locked inside the bus fuel opening!
                val microPulse = sin(simTimeAccumulator * 9.5f) * 0.0025f
                ikWeight = 1f
                ikTargetX = palmInsertedX
                ikTargetY = palmInsertedY + microPulse
                ikTargetZ = palmInsertedZ
                ikAimX = tankAimX
                ikAimY = tankAimY
                ikAimZ = tankAimZ
                torsoLeanRad = 0.12f

                // Smooth realistic flow rate so the integrated pump dispenser meter counts clearly (~5.5-8s)
                val flowRateLps = (targetL / 6.2f).coerceIn(3.8f, 8.5f)
                val stepL = (flowRateLps * dt).coerceAtMost((targetL - dispensedL).coerceAtLeast(0f))
                val capStepL = stepL.coerceAtMost((100f - outFuel).coerceAtLeast(0f))
                dispensedL += capStepL
                outFuel = (outFuel + capStepL).coerceIn(1f, 100f)
                dispensedRs = dispensedL * selectedFuel.pricePerLiterInr

                if (dispensedL >= targetL - 0.03f || outFuel >= 99.95f) {
                    dispensedL = dispensedL.coerceAtMost(targetL)
                    dispensedRs = dispensedL * selectedFuel.pricePerLiterInr
                    fuelVoiceManager?.stopFuelFlowSound(playShutoffClunk = true)
                    if (!workerPaymentSettled) {
                        workerPaymentSettled = true
                        val totalCost = dispensedRs.toInt()
                        if (outWallet >= totalCost) {
                            outWallet -= totalCost
                            paidViaFleet = false
                        } else {
                            paidViaFleet = true
                        }
                    }
                    phase = RefuelPhase.REMOVING_NOZZLE
                    refuelPhaseTimerSec = 0f
                    nozzleActionSoundPlayed = false
                    speakerLabel = selectedFuel.attendantTitle.uppercase(Locale.ROOT)
                    dialogueLine = "Auto-shutoff clicked! Removing nozzle and closing the fuel door."
                }
            }

            // =========================================================================
            // STEP 6A: REMOVE NOZZLE SMOOTHLY STRAIGHT OUT OF FUEL TANK OPENING
            // =========================================================================
            RefuelPhase.REMOVING_NOZZLE -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                holdingNozzle = true
                nozzleDocked = false
                faceWorkerToward(fillerWx, fillerWz, dt)
                activeAnim = WorkerAnimState.HOLDING_NOZZLE_IDLE

                val pullT = smoothStep01(refuelPhaseTimerSec / 0.68f)
                val releaseIkT = smoothStep01((refuelPhaseTimerSec - 0.68f) / 0.36f)

                ikWeight = 1f - releaseIkT
                ikTargetX = palmInsertedX + (palmPreInsertX - palmInsertedX) * pullT
                ikTargetY = palmInsertedY + (palmPreInsertY - palmInsertedY) * pullT
                ikTargetZ = palmInsertedZ + (palmPreInsertZ - palmInsertedZ) * pullT
                ikAimX = tankAimX
                ikAimY = tankAimY
                ikAimZ = tankAimZ
                nozzleInsertedFrac = 1f - pullT
                torsoLeanRad = 0.12f * (1f - releaseIkT)

                if (refuelPhaseTimerSec >= 1.08f) {
                    phase = RefuelPhase.RETURNING_NOZZLE
                    refuelPhaseTimerSec = 0f
                }
            }

            // =========================================================================
            // STEP 6B: WALK BACK TO SIDE OF FUEL PUMP WITH NOZZLE
            // =========================================================================
            RefuelPhase.RETURNING_NOZZLE -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                holdingNozzle = true
                nozzleDocked = false
                val arrived = moveWorkerToward(workerAtPumpWx, workerAtPumpWz, speedMps = 1.55f, dt = dt)
                activeAnim = if (arrived) WorkerAnimState.REACHING_PUMP else WorkerAnimState.WALKING
                if (arrived) {
                    faceWorkerToward(holsterWx, holsterWz, dt * 1.4f)
                }
                val desiredYaw = atan2(holsterWx - activeWorkerX, holsterWz - activeWorkerZ)
                var yawErr = desiredYaw - activeWorkerYaw
                while (yawErr > PI) yawErr -= (2.0 * PI).toFloat()
                while (yawErr < -PI) yawErr += (2.0 * PI).toFloat()
                if ((arrived && abs(yawErr) < 0.16f) || refuelPhaseTimerSec >= 3.2f) {
                    phase = RefuelPhase.PLACING_NOZZLE_IN_HOLDER
                    refuelPhaseTimerSec = 0f
                    nozzleActionSoundPlayed = false
                }
            }

            // =========================================================================
            // STEP 6C: PLACE NOZZLE CLEANLY INTO SIDE HOLSTER & RELEASE
            // =========================================================================
            RefuelPhase.PLACING_NOZZLE_IN_HOLDER -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                faceWorkerToward(holsterWx, holsterWz, dt)
                activeAnim = WorkerAnimState.REACHING_PUMP

                val dockT = smoothStep01(refuelPhaseTimerSec / 0.62f)
                val retractT = smoothStep01((refuelPhaseTimerSec - 0.66f) / 0.42f)

                val dockedNow = refuelPhaseTimerSec >= 0.62f
                if (dockedNow && !nozzleActionSoundPlayed) {
                    nozzleActionSoundPlayed = true
                    fuelVoiceManager?.playNozzleClick(inserting = false)
                }

                holdingNozzle = !dockedNow
                nozzleDocked = dockedNow

                val carryTargetX = holsterWx - holsterAimX * 0.26f
                val carryTargetY = holsterY + 0.08f
                val carryTargetZ = holsterWz - holsterAimZ * 0.26f

                ikWeight = (0.35f + 0.65f * dockT) * (1f - retractT)
                ikTargetX = carryTargetX + (holsterWx - carryTargetX) * dockT
                ikTargetY = carryTargetY + (holsterY - carryTargetY) * dockT
                ikTargetZ = carryTargetZ + (holsterWz - carryTargetZ) * dockT
                ikAimX = holsterAimX
                ikAimY = -0.05f - 0.10f * dockT
                ikAimZ = holsterAimZ
                torsoLeanRad = 0.10f * dockT * (1f - retractT)

                if (refuelPhaseTimerSec >= 1.12f) {
                    phase = RefuelPhase.WALKING_TO_TANK_TO_CLOSE_DOOR
                    refuelPhaseTimerSec = 0f
                }
            }

            // =========================================================================
            // STEP 6D: WALK BACK TO OPEN BUS FUEL DOOR
            // =========================================================================
            RefuelPhase.WALKING_TO_TANK_TO_CLOSE_DOOR -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 1f
                val arrived = moveWorkerToward(workerAtTankWx, workerAtTankWz, speedMps = 1.55f, dt = dt)
                activeAnim = if (arrived) WorkerAnimState.CLOSING_DOOR else WorkerAnimState.WALKING
                if (arrived) {
                    faceWorkerToward(fillerWx, fillerWz, dt * 1.4f)
                }
                val desiredYaw = atan2(fillerWx - activeWorkerX, fillerWz - activeWorkerZ)
                var yawErr = desiredYaw - activeWorkerYaw
                while (yawErr > PI) yawErr -= (2.0 * PI).toFloat()
                while (yawErr < -PI) yawErr += (2.0 * PI).toFloat()
                if ((arrived && abs(yawErr) < 0.16f) || refuelPhaseTimerSec >= 3.2f) {
                    phase = RefuelPhase.CLOSING_FUEL_DOOR
                    refuelPhaseTimerSec = 0f
                    fuelDoorSoundPlayed = false
                }
            }

            // =========================================================================
            // STEP 6E: GRAB OPEN FUEL DOOR HANDLE & SWING CLOSED AROUND HINGE (112° -> 0°)
            // =========================================================================
            RefuelPhase.CLOSING_FUEL_DOOR -> {
                refuelPhaseTimerSec += dt
                faceWorkerToward(fillerWx, fillerWz, dt)
                activeAnim = WorkerAnimState.CLOSING_DOOR

                val reachT = smoothStep01(refuelPhaseTimerSec / 0.44f)
                val closeT = smoothStep01((refuelPhaseTimerSec - 0.44f) / 0.78f)
                val retractT = smoothStep01((refuelPhaseTimerSec - 1.26f) / 0.34f)

                fuelDoorOpenFrac = 1f - closeT
                if (refuelPhaseTimerSec >= 1.20f && !fuelDoorSoundPlayed) {
                    fuelDoorSoundPlayed = true
                    fuelVoiceManager?.playFuelDoorLatchSound(opening = false)
                }

                val handlePos = fuelDoorHandleWorld(fuelDoorOpenFrac)
                ikWeight = reachT * (1f - retractT)
                ikTargetX = handlePos.x
                ikTargetY = handlePos.y
                ikTargetZ = handlePos.z
                ikAimX = intoTankX
                ikAimY = -0.16f
                ikAimZ = intoTankZ
                torsoLeanRad = 0.13f * ikWeight

                if (refuelPhaseTimerSec >= 1.64f) {
                    fuelDoorOpenFrac = 0f
                    phase = RefuelPhase.THANK_YOU_FAREWELL
                    refuelPhaseTimerSec = 0f
                    refuelCompletedStationId = nearest.id
                    val totalRsInt = dispensedRs.toInt()
                    val thankYouSpeech = "All done sir! Filled ${dispensedL.toInt().coerceAtLeast(1)} liters for $totalRsInt rupees. Fuel door locked! Have a safe journey!"
                    speakerLabel = selectedFuel.attendantTitle.uppercase(Locale.ROOT)
                    dialogueLine = thankYouSpeech
                    fuelVoiceManager?.speakWorker(
                        text = thankYouSpeech,
                        isPetrolWorker = (selectedFuel == FuelType.PETROL),
                        flush = true
                    )
                }
            }

            // =========================================================================
            // STEP 6F: WARM FAREWELL WAVE TO BUS DRIVER
            // =========================================================================
            RefuelPhase.THANK_YOU_FAREWELL -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 0f
                faceWorkerToward(busPos.x, busPos.z, dt)
                activeAnim = WorkerAnimState.THANK_YOU_WAVE
                if (refuelPhaseTimerSec >= 2.6f) {
                    phase = RefuelPhase.WALKING_AWAY_TO_HOME
                    refuelPhaseTimerSec = 0f
                }
            }

            // =========================================================================
            // STEP 6G: WORKER WALKS AWAY NATURALLY BACK TO STATION POST
            // =========================================================================
            RefuelPhase.WALKING_AWAY_TO_HOME -> {
                refuelPhaseTimerSec += dt
                fuelDoorOpenFrac = 0f
                val arrived = moveWorkerToward(homeWx, homeWz, speedMps = 1.50f, dt = dt)
                activeAnim = if (arrived) WorkerAnimState.IDLE else WorkerAnimState.WALKING
                if (arrived) {
                    faceWorkerToward(busPos.x, busPos.z, dt)
                }
                if ((arrived && refuelPhaseTimerSec >= 0.45f) || refuelPhaseTimerSec >= 3.0f) {
                    phase = RefuelPhase.NONE
                    dialogStep = RefuelDialogStep.ORDER_MODE
                    speakerLabel = ""
                    dialogueLine = ""
                    activeWorkerInitialized = false
                }
            }
        }

        // Determine active cinematic cutscene camera shot (works from ANY player camera mode!)
        val cutsceneCam = when (phase) {
            RefuelPhase.DRIVER_REPLYING,
            RefuelPhase.WALKING_TO_TANK_TO_OPEN_DOOR,
            RefuelPhase.OPENING_FUEL_DOOR,
            RefuelPhase.WALKING_TO_PUMP_FOR_NOZZLE,
            RefuelPhase.PICKING_UP_NOZZLE,
            RefuelPhase.WALKING_TO_TANK_WITH_NOZZLE,
            RefuelPhase.INSERTING_NOZZLE -> RefuelCutsceneCam.CAM_1_HOSE
            RefuelPhase.FUELING_IN_PROGRESS -> {
                // Hold CAM 1 on the open hinged door + inserted nozzle for 1.85s, then cut smoothly
                // to CAM 2 close-up on the dispenser's built-in live digital meter!
                if (refuelPhaseTimerSec < 1.85f) RefuelCutsceneCam.CAM_1_HOSE else RefuelCutsceneCam.CAM_2_METER
            }
            RefuelPhase.REMOVING_NOZZLE,
            RefuelPhase.RETURNING_NOZZLE,
            RefuelPhase.PLACING_NOZZLE_IN_HOLDER,
            RefuelPhase.WALKING_TO_TANK_TO_CLOSE_DOOR,
            RefuelPhase.CLOSING_FUEL_DOOR,
            RefuelPhase.THANK_YOU_FAREWELL,
            RefuelPhase.WALKING_AWAY_TO_HOME -> RefuelCutsceneCam.CAM_3_FAREWELL
            else -> RefuelCutsceneCam.NONE
        }
        val isCutsceneActive = (cutsceneCam != RefuelCutsceneCam.NONE)

        // Show physical side hose & nozzle continuously whenever near the fuel station
        val showHose = inForecourt || phase != RefuelPhase.NONE
        val showFuelDoor = inForecourt || phase != RefuelPhase.NONE

        // Build live 3D poses for BOTH human attendants — one on Diesel Side 1, one on Petrol Side 2!
        val petrolIsActive = (selectedFuel == FuelType.PETROL)
        val otherWaveState = if (inForecourt && phase == RefuelPhase.NONE) {
            WorkerAnimState.WAVING_WELCOME
        } else {
            WorkerAnimState.IDLE
        }

        val petrolPose = if (petrolIsActive && phase != RefuelPhase.NONE && phase != RefuelPhase.APPROACH_PROMPT) {
            FuelWorkerWorldPose(
                workerIndex = FuelStationWorkerAnimator.WORKER_PETROL,
                x = activeWorkerX,
                y = workerBaseY,
                z = activeWorkerZ,
                yawRad = activeWorkerYaw,
                animState = activeAnim,
                animPhase = if (activeAnim == WorkerAnimState.WALKING) activeWorkerWalkPhase else simTimeAccumulator,
                holdingNozzle = holdingNozzle,
                rightHandIkWeight = ikWeight,
                rightHandTargetX = ikTargetX,
                rightHandTargetY = ikTargetY,
                rightHandTargetZ = ikTargetZ,
                rightHandAimX = ikAimX,
                rightHandAimY = ikAimY,
                rightHandAimZ = ikAimZ,
                torsoLeanPitchRad = torsoLeanRad
            )
        } else {
            FuelWorkerWorldPose(
                workerIndex = FuelStationWorkerAnimator.WORKER_PETROL,
                x = petrolHomeWx,
                y = workerBaseY,
                z = petrolHomeWz,
                yawRad = atan2(busPos.x - petrolHomeWx, busPos.z - petrolHomeWz),
                animState = if (petrolIsActive) activeAnim else otherWaveState,
                animPhase = simTimeAccumulator + 0.7f,
                holdingNozzle = false
            )
        }

        val dieselPose = if (!petrolIsActive && phase != RefuelPhase.NONE && phase != RefuelPhase.APPROACH_PROMPT) {
            FuelWorkerWorldPose(
                workerIndex = FuelStationWorkerAnimator.WORKER_DIESEL,
                x = activeWorkerX,
                y = workerBaseY,
                z = activeWorkerZ,
                yawRad = activeWorkerYaw,
                animState = activeAnim,
                animPhase = if (activeAnim == WorkerAnimState.WALKING) activeWorkerWalkPhase else simTimeAccumulator,
                holdingNozzle = holdingNozzle,
                rightHandIkWeight = ikWeight,
                rightHandTargetX = ikTargetX,
                rightHandTargetY = ikTargetY,
                rightHandTargetZ = ikTargetZ,
                rightHandAimX = ikAimX,
                rightHandAimY = ikAimY,
                rightHandAimZ = ikAimZ,
                torsoLeanPitchRad = torsoLeanRad
            )
        } else {
            FuelWorkerWorldPose(
                workerIndex = FuelStationWorkerAnimator.WORKER_DIESEL,
                x = dieselHomeWx,
                y = workerBaseY,
                z = dieselHomeWz,
                yawRad = atan2(busPos.x - dieselHomeWx, busPos.z - dieselHomeWz),
                animState = if (!petrolIsActive) activeAnim else otherWaveState,
                animPhase = simTimeAccumulator,
                holdingNozzle = false
            )
        }

        val updated = curRefuel.copy(
            isNearFuelStation = inForecourt,
            isParkedInBayBox = isParkedInBayBox,
            parkedSlotSide = parkedSlotSide,
            nearestSlotSide = nearestSlotSide,
            cornersInSlotCount = cornersInSlotCount,
            stationId = nearest.id,
            stationName = nearest.displayName,
            phase = phase,
            dialogStep = dialogStep,
            cutsceneCam = cutsceneCam,
            isCutsceneActive = isCutsceneActive,
            selectedFuelType = selectedFuel,
            targetLiters = targetL,
            dispensedLiters = dispensedL,
            dispensedRupees = dispensedRs,
            pricePerLiterInr = selectedFuel.pricePerLiterInr,
            paidViaFleetCard = paidViaFleet,
            activeSpeakerLabel = speakerLabel,
            activeDialogueLine = dialogueLine,
            activeDispenserLocalX = activeDispenserX,
            activeDispenserLocalZ = activeIslandZ,
            activeStationCx = nearest.cx,
            activeStationBaseY = nearest.baseY,
            activeStationCz = nearest.cz,
            activeStationCosYaw = nearest.cosYaw,
            activeStationSinYaw = nearest.sinYaw,
            hoseStartX = hoseStartWx,
            hoseStartY = hoseStartY,
            hoseStartZ = hoseStartWz,
            nozzleHolsterX = holsterWx,
            nozzleHolsterY = holsterY,
            nozzleHolsterZ = holsterWz,
            nozzleDockedInHolder = nozzleDocked,
            nozzleInsertedFrac = nozzleInsertedFrac,
            nozzleTargetX = fillerWx,
            nozzleTargetY = fillerY,
            nozzleTargetZ = fillerWz,
            showFuelDoorAssembly = showFuelDoor,
            fuelDoorOpenFrac = fuelDoorOpenFrac,
            fuelDoorSideSign = pumpSideSign,
            fuelDoorLongOffset = fillerLongOffset,
            showFuelHose = showHose,
            activeWorkers = listOf(petrolPose, dieselPose)
        )
        return Triple(updated, outFuel, outWallet)
    }

    /**
     * Cinematic Multi-Camera Refueling Cutscene Director (CAM 1 -> CAM 2 -> CAM 3).
     * Dynamically aligns inside the clear pedestrian aisle between the bus side wall and
     * the active pump island so the fuel door, side nozzle holder, hose, and worker stay
     * cleanly framed with zero bus or pump clipping!
     */
    private fun computeRefuelCutsceneCamera(
        refuel: RefuelLiveState,
        busPos: Vec3
    ): Triple<Vec3, Vec3, Float> {
        val cosY = refuel.activeStationCosYaw
        val sinY = refuel.activeStationSinYaw
        val stCx = refuel.activeStationCx
        val stCy = refuel.activeStationBaseY
        val stCz = refuel.activeStationCz
        val dispX = refuel.activeDispenserLocalX
        val dispZ = refuel.activeDispenserLocalZ
        val slotDir = if (refuel.selectedFuelType == FuelType.DIESEL) {
            ThrissurFuelStationSystem.DIESEL_SLOT_SIDE_DIR
        } else {
            ThrissurFuelStationSystem.PETROL_SLOT_SIDE_DIR
        }

        fun stToWorld(lx: Float, ly: Float, lz: Float): Vec3 = Vec3(
            x = stCx + lx * cosY - lz * sinY,
            y = stCy + ly,
            z = stCz + lx * sinY + lz * cosY
        )

        // Compute dynamic safe aisle Z midpoint between pump island face and bus fuel filler wall
        val fdx = refuel.nozzleTargetX - stCx
        val fdz = refuel.nozzleTargetZ - stCz
        val fillerLocalZ = -fdx * sinY + fdz * cosY
        val islandFaceZ = dispZ + slotDir * 0.55f
        val aisleMidZ = ((islandFaceZ + fillerLocalZ) * 0.50f).coerceIn(
            minOf(islandFaceZ, fillerLocalZ),
            maxOf(islandFaceZ, fillerLocalZ)
        )

        // Look down the open pedestrian lane from the hose-side of the active pump dispenser
        val sideDir = if (dispX >= 0f) -1f else 1f

        return when (refuel.cutsceneCam) {
            RefuelCutsceneCam.CAM_1_HOSE -> {
                // CAM 1: Clear aisle tracking shot showing fuel door opening, side nozzle pickup, and insertion into tank
                val focusTank = refuel.phase == RefuelPhase.OPENING_FUEL_DOOR ||
                    refuel.phase == RefuelPhase.INSERTING_NOZZLE ||
                    refuel.phase == RefuelPhase.FUELING_IN_PROGRESS
                val focusPump = refuel.phase == RefuelPhase.PICKING_UP_NOZZLE
                val focusX = when {
                    focusTank -> activeWorkerX * 0.45f + refuel.nozzleTargetX * 0.55f
                    focusPump -> activeWorkerX * 0.45f + refuel.nozzleHolsterX * 0.55f
                    else -> activeWorkerX * 0.65f + refuel.nozzleTargetX * 0.35f
                }
                val focusZ = when {
                    focusTank -> activeWorkerZ * 0.45f + refuel.nozzleTargetZ * 0.55f
                    focusPump -> activeWorkerZ * 0.45f + refuel.nozzleHolsterZ * 0.55f
                    else -> activeWorkerZ * 0.65f + refuel.nozzleTargetZ * 0.35f
                }
                val focusY = if (focusPump) stCy + 1.32f else stCy + 1.15f
                val focus = Vec3(focusX, focusY, focusZ)
                val driftX = sin(simTimeAccumulator * 0.48f) * 0.10f
                val eye = stToWorld(
                    lx = dispX + sideDir * 3.15f + driftX,
                    ly = 1.54f,
                    lz = aisleMidZ
                )
                Triple(focus, eye, 46f)
            }

            RefuelCutsceneCam.CAM_2_METER -> {
                // CAM 2: Tight realistic telephoto shot directly onto the dispenser's built-in LCD panel!
                val meterCenter = stToWorld(dispX, 1.42f, dispZ + slotDir * 0.247f)
                val microSwayX = sin(simTimeAccumulator * 0.85f) * 0.02f
                val microSwayY = cos(simTimeAccumulator * 0.65f) * 0.01f
                val eye = stToWorld(
                    lx = dispX + sideDir * 0.10f + microSwayX,
                    ly = 1.43f + microSwayY,
                    lz = dispZ + slotDir * 1.16f
                )
                Triple(meterCenter, eye, 38f)
            }

            RefuelCutsceneCam.CAM_3_FAREWELL -> {
                // CAM 3: Clear aisle view showing nozzle removal, docking into side holder, closing fuel door & farewell
                val focusTank = refuel.phase == RefuelPhase.REMOVING_NOZZLE ||
                    refuel.phase == RefuelPhase.CLOSING_FUEL_DOOR
                val focusPump = refuel.phase == RefuelPhase.PLACING_NOZZLE_IN_HOLDER
                val focusX = when {
                    focusTank -> activeWorkerX * 0.45f + refuel.nozzleTargetX * 0.55f
                    focusPump -> activeWorkerX * 0.45f + refuel.nozzleHolsterX * 0.55f
                    else -> activeWorkerX * 0.82f + busPos.x * 0.18f
                }
                val focusZ = when {
                    focusTank -> activeWorkerZ * 0.45f + refuel.nozzleTargetZ * 0.55f
                    focusPump -> activeWorkerZ * 0.45f + refuel.nozzleHolsterZ * 0.55f
                    else -> activeWorkerZ * 0.82f + busPos.z * 0.18f
                }
                val focusY = when {
                    focusTank -> stCy + 1.14f
                    focusPump -> stCy + 1.32f
                    else -> stCy + 1.36f
                }
                val focus = Vec3(focusX, focusY, focusZ)
                val eye = stToWorld(
                    lx = dispX + sideDir * 3.05f,
                    ly = 1.56f,
                    lz = aisleMidZ
                )
                Triple(focus, eye, 46f)
            }

            RefuelCutsceneCam.NONE -> Triple(busPos, busPos, CINEMATIC_FOLLOW_FOV_DEG)
        }
    }

    fun beginRefuelConversation() {
        val r = _uiState.value.refuel
        if (r.stationId.isEmpty() || !r.isParkedInBayBox) return
        val bayFuel = r.parkedSlotSide ?: r.selectedFuelType
        refuelDismissedStationId = ""
        refuelPhaseTimerSec = 0f
        activeWorkerWalkPhase = 0f
        workerSpokeConfirm = false
        workerPaymentSettled = false
        _uiState.update {
            it.copy(
                refuel = it.refuel.copy(
                    phase = RefuelPhase.WORKER_APPROACHING,
                    selectedFuelType = bayFuel,
                    pricePerLiterInr = bayFuel.pricePerLiterInr,
                    dialogStep = RefuelDialogStep.ORDER_MODE,
                    dispensedLiters = 0f,
                    dispensedRupees = 0f,
                    activeSpeakerLabel = bayFuel.attendantTitle.uppercase(Locale.ROOT),
                    activeDialogueLine = "Coming right over, sir!"
                )
            )
        }
    }

    fun dismissRefuelDialog() {
        val stId = _uiState.value.refuel.stationId
        refuelDismissedStationId = stId
        fuelVoiceManager?.stopSpeech()
        _uiState.update {
            it.copy(
                refuel = it.refuel.copy(
                    phase = RefuelPhase.APPROACH_PROMPT,
                    dialogStep = RefuelDialogStep.ORDER_MODE,
                    activeSpeakerLabel = "",
                    activeDialogueLine = ""
                )
            )
        }
    }

    fun chooseFuelTypeAndAdvance(fuelType: FuelType) {
        activeWorkerInitialized = false
        val prompt = "Full tank, or how many rupees or liters do you need?"
        fuelVoiceManager?.speakWorker(
            text = "${fuelType.displayName} selected. $prompt",
            isPetrolWorker = (fuelType == FuelType.PETROL),
            flush = true
        )
        _uiState.update {
            it.copy(
                refuel = it.refuel.copy(
                    selectedFuelType = fuelType,
                    pricePerLiterInr = fuelType.pricePerLiterInr,
                    dialogStep = RefuelDialogStep.ORDER_MODE,
                    activeSpeakerLabel = fuelType.attendantTitle.uppercase(Locale.ROOT),
                    activeDialogueLine = prompt
                )
            )
        }
    }

    fun chooseOrderModeAndAdvance(mode: RefuelOrderMode) {
        val curFuel = _uiState.value.refuel.selectedFuelType
        _uiState.update {
            it.copy(
                refuel = it.refuel.copy(
                    orderMode = mode,
                    dialogStep = if (mode == RefuelOrderMode.FULL_TANK) {
                        RefuelDialogStep.ORDER_MODE
                    } else {
                        RefuelDialogStep.AMOUNT_INPUT
                    }
                )
            )
        }
        if (mode == RefuelOrderMode.FULL_TANK) {
            confirmRefuelOrder()
        } else {
            val askLine = if (mode == RefuelOrderMode.BY_PRICE) {
                "How many rupees should I fill, sir?"
            } else {
                "How many liters do you need, sir?"
            }
            fuelVoiceManager?.speakWorker(
                text = askLine,
                isPetrolWorker = (curFuel == FuelType.PETROL),
                flush = true
            )
            _uiState.update {
                it.copy(
                    refuel = it.refuel.copy(
                        activeSpeakerLabel = curFuel.attendantTitle.uppercase(Locale.ROOT),
                        activeDialogueLine = askLine
                    )
                )
            }
        }
    }

    fun stepBackRefuelDialog() {
        _uiState.update {
            val prev = when (it.refuel.dialogStep) {
                RefuelDialogStep.AMOUNT_INPUT -> RefuelDialogStep.ORDER_MODE
                RefuelDialogStep.ORDER_MODE -> RefuelDialogStep.ORDER_MODE
                RefuelDialogStep.FUEL_TYPE -> RefuelDialogStep.ORDER_MODE
            }
            it.copy(refuel = it.refuel.copy(dialogStep = prev))
        }
    }

    fun selectRefuelFuelType(fuelType: FuelType) {
        activeWorkerInitialized = false
        _uiState.update {
            it.copy(
                refuel = it.refuel.copy(
                    selectedFuelType = fuelType,
                    pricePerLiterInr = fuelType.pricePerLiterInr
                )
            )
        }
    }

    fun selectRefuelOrderMode(mode: RefuelOrderMode) {
        _uiState.update {
            it.copy(refuel = it.refuel.copy(orderMode = mode))
        }
    }

    fun setRefuelCustomPrice(priceInr: Int) {
        _uiState.update {
            it.copy(refuel = it.refuel.copy(customPriceInr = priceInr.coerceIn(100, 10000)))
        }
    }

    fun setRefuelCustomLiters(liters: Int) {
        _uiState.update {
            it.copy(refuel = it.refuel.copy(customLiters = liters.coerceIn(5, 100)))
        }
    }

    fun confirmRefuelOrder() {
        val s = _uiState.value
        val r = s.refuel
        val missingLiters = (100f - s.fuelLiters).coerceAtLeast(0f)
        val rate = r.selectedFuelType.pricePerLiterInr
        val targetLiters = when (r.orderMode) {
            RefuelOrderMode.FULL_TANK -> missingLiters
            RefuelOrderMode.BY_PRICE -> (r.customPriceInr / rate).coerceAtMost(missingLiters)
            RefuelOrderMode.BY_LITERS -> r.customLiters.toFloat().coerceAtMost(missingLiters)
        }.coerceAtLeast(0f)

        if (targetLiters < 0.4f) {
            dismissRefuelDialog()
            return
        }

        val fuelWord = r.selectedFuelType.displayName.lowercase(Locale.ROOT)
        val driverReply = when (r.orderMode) {
            RefuelOrderMode.FULL_TANK -> "Please fill a full tank of $fuelWord."
            RefuelOrderMode.BY_PRICE -> "Give me ${r.customPriceInr} rupees worth of $fuelWord please."
            RefuelOrderMode.BY_LITERS -> "Fill ${targetLiters.toInt().coerceAtLeast(1)} liters of $fuelWord please."
        }

        refuelPhaseTimerSec = 0f
        workerSpokeConfirm = false
        workerPaymentSettled = false
        fuelVoiceManager?.speakDriver(driverReply, flush = true)

        _uiState.update {
            it.copy(
                refuel = it.refuel.copy(
                    phase = RefuelPhase.DRIVER_REPLYING,
                    cutsceneCam = RefuelCutsceneCam.CAM_1_HOSE,
                    isCutsceneActive = true,
                    targetLiters = targetLiters,
                    dispensedLiters = 0f,
                    dispensedRupees = 0f,
                    pricePerLiterInr = rate,
                    activeSpeakerLabel = "BUS DRIVER (YOU)",
                    activeDialogueLine = driverReply
                )
            )
        }
    }

    fun stopRefuelingEarly() {
        val s = _uiState.value
        if (s.refuel.phase != RefuelPhase.FUELING_IN_PROGRESS) return
        fuelVoiceManager?.stopFuelFlowSound(playShutoffClunk = true)
        var outWallet = s.walletInr
        var paidViaFleet = false
        if (!workerPaymentSettled) {
            workerPaymentSettled = true
            val cost = s.refuel.dispensedRupees.toInt()
            if (outWallet >= cost) {
                outWallet -= cost
            } else {
                paidViaFleet = true
            }
        }
        refuelPhaseTimerSec = 0f
        nozzleActionSoundPlayed = false
        _uiState.update {
            it.copy(
                walletInr = outWallet,
                refuel = it.refuel.copy(
                    phase = RefuelPhase.REMOVING_NOZZLE,
                    targetLiters = it.refuel.dispensedLiters,
                    paidViaFleetCard = paidViaFleet,
                    activeSpeakerLabel = it.refuel.selectedFuelType.attendantTitle.uppercase(java.util.Locale.ROOT),
                    activeDialogueLine = "Stopping pump now, sir! Removing nozzle and closing fuel door."
                )
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
                "Park Brake Auto-Released • Departing Sakthan Stand (Bay ${s.parkedBayNumber})"
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
                    statusBanner = "Engine Running (650 RPM Idle) • Ready to Drive from Bay ${it.parkedBayNumber}"
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
        val baySpawn = if (isSakthanStart) createSakthanBaySpawn() else null
        val spawnFrame = if (!isSakthanStart) {
            road.frames.firstOrNull { it.cumulativeDist >= 18f } ?: road.frames.firstOrNull()
        } else null
        if (!isSakthanStart && spawnFrame == null) return
        val spawnPos = baySpawn?.first ?: Vec3(
            spawnFrame!!.pos.x,
            ShoranurRailwaySystem.computeRoadSurfaceY(spawnFrame.pos.x, spawnFrame.pos.z),
            spawnFrame.pos.z
        )
        val heading = baySpawn?.second ?: atan2(spawnFrame!!.tangent.x, spawnFrame.tangent.z)
        val bayNum = baySpawn?.third ?: s.parkedBayNumber
        cachedRoadScanIdx = -1
        cachedFrameScanIdx = 0
        entryBarrierLatchOpen = false
        exitBarrierLatchOpen = false
        vadakkeEntryBarrierLatchOpen = false
        vadakkeExitBarrierLatchOpen = false
        stationEntryBarrierLatchOpen = false
        stationExitBarrierLatchOpen = false
        shoranurTermEntryBarrierLatchOpen = false
        shoranurTermExitBarrierLatchOpen = false
        shoranurStationEntryBarrierLatchOpen = false
        shoranurStationExitBarrierLatchOpen = false
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
        ThrissurTrafficSystem.clearVehiclesNear(spawnPos)
        _uiState.update { current ->
            val updated = current.copy(
                activeRoadIndex = index,
                parkedBayNumber = bayNum,
                busPos = spawnPos,
                busHeadingRad = heading,
                sakthanEntryBarrierOpenFrac = 0f,
                sakthanExitBarrierOpenFrac = 0f,
                vadakkeEntryBarrierOpenFrac = 0f,
                vadakkeExitBarrierOpenFrac = 0f,
                stationEntryBarrierOpenFrac = 0f,
                stationExitBarrierOpenFrac = 0f,
                shoranurTermEntryBarrierOpenFrac = 0f,
                shoranurTermExitBarrierOpenFrac = 0f,
                shoranurStationEntryBarrierOpenFrac = 0f,
                shoranurStationExitBarrierOpenFrac = 0f,
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
                    "Sakthan Bus Terminal • Bay $bayNum (DEPARTURE)"
                } else {
                    road.stepNames.firstOrNull() ?: road.name
                },
                distanceToRouteEndKm = road.realDistanceM / 1000f,
                showRouteModal = false,
                statusBanner = if (isSakthanStart) {
                    "Parked at Sakthan Stand (Bay $bayNum) • Exit via Back Gate onto M.O. Road"
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

        val sakthanBay = createSakthanBaySpawn()
        val vadakkeBay = createVadakkeBaySpawn()
        val stationBay = createStationBaySpawn()
        val spec = when (landmarkId) {
            "SAKTHAN_STAND" -> LandmarkSpawn(
                roadId = "ROAD_SAKTHAN_TO_SWARAJ",
                pos = sakthanBay.first,
                headingRad = sakthanBay.second,
                streetLabel = "Sakthan Bus Terminal • Bay ${sakthanBay.third}",
                banner = "Sakthan Bus Terminal • Parked at Bay ${sakthanBay.third} • Front Entry & Back Exit Gate"
            )
            "SAKTHAN_LEFT_ENTRY" -> LandmarkSpawn(
                roadId = "ROAD_SAKTHAN_STAND_ENTRY",
                pos = Vec3(5.5f, 0.06f, -124.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Sakthan Stand • Left Entry Gate",
                banner = "Sakthan Stand Left Entry Slip Road • Drive Straight In"
            )
            "THRISSUR_RAILWAY_TCR" -> LandmarkSpawn(
                roadId = "ROAD_RAILWAY_STATION_EXIT",
                pos = stationBay.first,
                headingRad = stationBay.second,
                streetLabel = "Thrissur Railway Station • Bay ${stationBay.third}",
                banner = "Thrissur Railway Station • Parked at Bay ${stationBay.third} • South Entry & North Exit Gate"
            )
            "MANIKANDANAL_SIGNAL" -> LandmarkSpawn(
                roadId = "ROAD_SAKTHAN_TO_SWARAJ",
                pos = Vec3(21.5f, 0.06f, -72.0f),
                headingRad = -0.14f,
                streetLabel = "Manikandanal Traffic Signal • Swaraj Round South",
                banner = "Manikandanal Signal (30s Red / 25s Green / 4s Amber) & Zebra Crossing"
            )
            "SWARAJ_ROUND" -> LandmarkSpawn(
                roadId = "ROAD_SWARAJ_ROUND",
                pos = Vec3(3.1f, 0.06f, -48.0f),
                headingRad = (-PI * 0.5).toFloat(),
                streetLabel = "Swaraj Round (1-Way Clockwise) • Vadakkumnathan Temple",
                banner = "Swaraj Round (4-Lane 15m 1-Way), Thekkinkadu Maidan & Vadakkumnathan Temple"
            )
            "VADAKKE_STAND" -> LandmarkSpawn(
                roadId = "ROAD_SWARAJ_TO_VADAKKE",
                pos = vadakkeBay.first,
                headingRad = vadakkeBay.second,
                streetLabel = "Vadakke Bus Stand • Bay ${vadakkeBay.third}",
                banner = "Vadakke Bus Stand • Parked at Bay ${vadakkeBay.third} • East Entry & West Exit Gate"
            )
            "KOLE_WETLANDS" -> {
                val sh75Road = s.roads.firstOrNull { it.id == "ROAD_WEST_FORT_TO_KANJANI" }
                val koleFrame = sh75Road?.frames?.minByOrNull { abs(it.pos.x - (-1340.0f)) }
                val spawnPos = if (koleFrame != null) {
                    val lat = -2.2f // Indian left-hand traffic heading West
                    Vec3(
                        koleFrame.pos.x + koleFrame.right.x * lat,
                        ShoranurRailwaySystem.computeRoadSurfaceY(koleFrame.pos.x, koleFrame.pos.z),
                        koleFrame.pos.z + koleFrame.right.z * lat
                    )
                } else {
                    Vec3(-1340.0f, Zone4KoleRailHeritageRenderer.KOLE_EMBANKMENT_Y, 134.0f)
                }
                val heading = if (koleFrame != null) atan2(koleFrame.tangent.x, koleFrame.tangent.z) else (-PI * 0.5).toFloat()
                LandmarkSpawn(
                    roadId = "ROAD_WEST_FORT_TO_KANJANI",
                    pos = spawnPos,
                    headingRad = heading,
                    streetLabel = "SH 75 • Thrissur Kole Wetlands Ramsar Site",
                    banner = "Zone 4 • SH 75 Raised 1.5m Laterite Embankment, Flooded Paddy Fields & Kerala PWD Km Posts"
                )
            }
            "ENAMAVU_BRIDGE" -> LandmarkSpawn(
                roadId = "ROAD_WEST_FORT_TO_KANJANI",
                pos = Vec3(
                    -1548.0f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(-1548.0f, Zone4KoleRailHeritageRenderer.ENAMAVU_CENTER_Z),
                    Zone4KoleRailHeritageRenderer.ENAMAVU_CENTER_Z - 2.1f
                ),
                headingRad = (-PI * 0.5).toFloat(),
                streetLabel = "Enamavu Sluice Regulator Bridge • SH 75",
                banner = "Zone 4 • Enamavu Barrage (18 Steel Sluice Gates & Overhead Winch Towers • Freshwater North / Saline South)"
            )
            "MUTHUVARA_LC" -> {
                muthuvaraLcCycleSec = 13.0f // Starts STATE_CLOSING 1.0s after arrival so player experiences full live train crossing!
                LandmarkSpawn(
                    roadId = "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR",
                    pos = Vec3(
                        Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X - 2.2f,
                        0.06f,
                        Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z - 34.0f
                    ),
                    headingRad = 0f,
                    streetLabel = "SH 69 • Muthuvara Railway Level Crossing (LC-14)",
                    banner = "Zone 4 • Muthuvara LC-14 (SH 69) — Watch 1 Hz Red LEDs, Descending Boom Arms & 65 km/h 8-Coach Train!"
                )
            }
            "GURUVAYUR_TEMPLE" -> LandmarkSpawn(
                roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
                pos = Vec3(-2660.0f, 0.06f, 1260.0f),
                headingRad = (PI * 0.5).toFloat(),
                streetLabel = "Guruvayur Sree Krishna Temple • East Nada Square",
                banner = "Zone 4 • Guruvayur East Nada Gopuram, 33m Golden Dwajasthambham, Twin Deepastambhams & Temple Elephants"
            )
            "GURUVAYUR_STAND" -> LandmarkSpawn(
                roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
                pos = Vec3(-2706.0f, 0.06f, 1261.5f),
                headingRad = -1.15f,
                streetLabel = "Guruvayur Municipal Bus Terminal • 8 Sawtooth Bays",
                banner = "Zone 4 • Upgraded Guruvayur Bus Terminal (8 Sawtooth Docking Bays, Terracotta Canopy & Route Boards)"
            )
            "BHARATHAPPUZHA_BRIDGES" -> LandmarkSpawn(
                roadId = "ROAD_THRISSUR_TO_SHORANUR",
                pos = Vec3(
                    Zone4KoleRailHeritageRenderer.BHARATHAPPUZHA_CENTER_X - 2.4f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(Zone4KoleRailHeritageRenderer.BHARATHAPPUZHA_CENTER_X, 2622.0f),
                    2622.0f
                ),
                headingRad = 0f,
                streetLabel = "Bharathappuzha (Nila River) Dual Bridges • SH 22",
                banner = "Zone 4 • Modern SH 22 River Bridge & Parallel 1902 Historic Cochin Steel Truss Bridge Across Golden Sandbars"
            )
            "CHAVAKKAD_BEACH" -> LandmarkSpawn(
                roadId = "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD",
                pos = Vec3(-4500.0f, 0.06f, 1132.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Chavakkad Beach, Granite Seawall (Kallidum) & Azhi",
                banner = "Zone 5 • Chavakkad Beach Coastal Drive, Granite Seawall (Kallidum), Wooden Vallams & Lighthouse"
            )
            "PUTHANPALLY_BASILICA" -> LandmarkSpawn(
                roadId = "ROAD_PUTHANPALLI_LINK",
                pos = Vec3(98.0f, 0.06f, -74.5f),
                headingRad = 2.68f, // Facing South-East directly toward the glowing white Twin Towers & Royal-Blue Cross!
                streetLabel = "Our Lady of Dolours Basilica (Puthanpally) • High Road",
                banner = "Puthanpally Basilica • 146 ft Twin Indo-Gothic Bell Towers, Glowing Blue Cross & 260 ft Bible Tower"
            )
            "CHETTUVA_BRIDGE" -> LandmarkSpawn(
                roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
                pos = Vec3(
                    Zone5Nh66CoastalRenderer.CHETTUVA_CENTER_X - 4.4f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(Zone5Nh66CoastalRenderer.CHETTUVA_CENTER_X, 378.0f),
                    378.0f
                ),
                headingRad = PI.toFloat(),
                streetLabel = "Chettuva Backwaters Bridge • NH 66 Coastal Highway",
                banner = "Zone 5 • 360m Chettuva Backwaters Bridge • Mangroves (Kandal Kaadukal), Chinese Fishing Nets & Houseboats"
            )
            "NATTIKA_TOLL" -> LandmarkSpawn(
                roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
                pos = Vec3(
                    Zone5Nh66CoastalRenderer.nattikaLaneCenterX(4),
                    0.06f,
                    Zone5Nh66CoastalRenderer.NATTIKA_CENTER_Z + 44.0f
                ),
                headingRad = PI.toFloat(),
                streetLabel = "Nattika Toll Plaza • FASTag Lane 5 Approach (NH 66)",
                banner = "Zone 5 • Nattika 10-Lane Toll Plaza (White Admin Block + Royal Blue Awnings + Live FASTag Boom Gates)"
            )
            "TRIPRAYAR_BEACH" -> LandmarkSpawn(
                roadId = "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
                pos = Vec3(-2220.0f, 0.06f, -2355.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Triprayar - Nattika Beach Terminus",
                banner = "Triprayar - Nattika Beach Promenade & NH 66 Bus Stand"
            )
            "TRIPRAYAR_CANAL" -> LandmarkSpawn(
                roadId = "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM",
                pos = Vec3(
                    Zone5Nh66CoastalRenderer.CANOLI_CENTER_X - 4.4f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(Zone5Nh66CoastalRenderer.CANOLI_CENTER_X, -2572.0f),
                    -2572.0f
                ),
                headingRad = PI.toFloat(),
                streetLabel = "Triprayar Canoli Canal Bridge & Sree Rama Temple Ghat",
                banner = "Zone 5 • Triprayar Canoli Canal Bridge • Sree Rama Temple Water Ghat, Gopuram & Sacred Meenootu Fish"
            )
            "KOTTAPURAM_BRIDGE" -> LandmarkSpawn(
                roadId = "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM",
                pos = Vec3(
                    Zone5Nh66CoastalRenderer.KOTTAPURAM_CENTER_X - 4.4f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(Zone5Nh66CoastalRenderer.KOTTAPURAM_CENTER_X, -3805.0f),
                    -3805.0f
                ),
                headingRad = PI.toFloat(),
                streetLabel = "Kottapuram / Kodungallur NW-3 High-Clearance Bridge • NH 66",
                banner = "Zone 5 • 540m Kottapuram NW-3 Bridge (12.6m Clearance) • Live Cargo Barge & Kerala SWTD Ferry Below"
            )
            "OLLUR_ROB" -> LandmarkSpawn(
                roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
                pos = Vec3(
                    Zone3Nh544SouthRenderer.ollurRobCenterXAt(-1362.0f) - 4.2f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(Zone3Nh544SouthRenderer.ollurRobCenterXAt(-1362.0f), -1362.0f),
                    -1362.0f
                ),
                headingRad = PI.toFloat(),
                streetLabel = "Ollur Railway Overbridge (ROB) • NH 544",
                banner = "Zone 3 • Ollur ROB with 2.55m Safety Mesh & Live 25kV Electrified Southern Railway Train Below"
            )
            "PALIYEKKARA_TOLL" -> LandmarkSpawn(
                roadId = "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
                pos = Vec3(996.8f, 0.06f, -1768.0f),
                headingRad = PI.toFloat(),
                streetLabel = "Paliakkara Toll Plaza • FASTag Lane 6 Approach (NH 544)",
                banner = "Zone 3 • Paliakkara 12-Lane FASTag Toll Plaza • Drive into Green Lane for Auto Boom Gate"
            )
            "KURUMALI_BRIDGE" -> LandmarkSpawn(
                roadId = "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY",
                pos = Vec3(
                    Zone3Nh544SouthRenderer.KURUMALI_CENTER_X - 4.8f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(Zone3Nh544SouthRenderer.KURUMALI_CENTER_X, -2148.0f),
                    -2148.0f
                ),
                headingRad = PI.toFloat(),
                streetLabel = "Kurumali River Bridge • Puthukkad (NH 544)",
                banner = "Zone 3 • Kurumali River Bridge (140m Span) • Flowing River, Canoes & Coconut Palm Riverbanks"
            )
            "CHALAKUDY_BRIDGE" -> LandmarkSpawn(
                roadId = "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY",
                pos = Vec3(
                    Zone3Nh544SouthRenderer.CHALAKUDY_CENTER_X - 4.8f,
                    ShoranurRailwaySystem.computeRoadSurfaceY(Zone3Nh544SouthRenderer.CHALAKUDY_CENTER_X, -4870.0f),
                    -4870.0f
                ),
                headingRad = PI.toFloat(),
                streetLabel = "Chalakudy River Bridge (280m Span) • NH 544 South",
                banner = "Zone 3 • 280m Chalakudy River Bridge • 8 Elliptical Pier Pairs, Flowing River, Sandbars & Vallams"
            )
            "WADAKKANCHERY_ROB" -> LandmarkSpawn(
                roadId = "ROAD_THRISSUR_TO_SHORANUR",
                pos = Vec3(92.0f, ShoranurRailwaySystem.computeRoadSurfaceY(92.0f, 1605.0f), 1605.0f),
                headingRad = 0f,
                streetLabel = "Wadakkanchery Railway Overbridge (ROB) • SH 22",
                banner = "Wadakkanchery ROB • Crossing Over 2-Track Southern Railway Corridor (WAG-9 7-Coach Trains)"
            )
            "SHORANUR_TERMINAL" -> {
                val bayNum = 3
                val bayZ = SHORANUR_TERM_BAY_1_CENTER_Z + (bayNum - 1) * SHORANUR_TERM_BAY_PITCH_Z
                LandmarkSpawn(
                    roadId = "ROAD_SHORANUR_TERMINAL_EXIT",
                    pos = Vec3(SHORANUR_TERM_BAY_CENTER_X, 0.06f, bayZ),
                    headingRad = (-PI * 0.5).toFloat(),
                    streetLabel = "Shoranur Bus Terminal • Bay $bayNum",
                    banner = "Shoranur Bus Terminal • Sawtooth Pavilion & Arched Wave Canopy • West Exit Gate"
                )
            }
            "SHORANUR_STATION" -> {
                val bayIdx = 1
                LandmarkSpawn(
                    roadId = "ROAD_SHORANUR_STATION_EXIT",
                    pos = Vec3(SHORANUR_STATION_BAY_X, 0.06f, SHORANUR_STATION_BAY_CENTERS_Z[bayIdx]),
                    headingRad = 0f,
                    streetLabel = "Shoranur Junction Railway Station (SRR) • Bay ${bayIdx + 1}",
                    banner = "Shoranur Junction (SRR) • Palakkad Heritage Architecture & Live 2-Track Trains"
                )
            }
            "MANNUTHY_FLYOVER" -> LandmarkSpawn(
                roadId = "ROAD_NH544_MANNUTHY_TO_KUTHIRAN",
                pos = Vec3(1053.5f, 0.06f, 142.0f),
                headingRad = -0.10f,
                streetLabel = "Mannuthy 4-Way Interchange • NH 544",
                banner = "Zone 2 • Mannuthy 4-Way Interchange, 25m High-Mast & NH 544 6-Lane Expressway"
            )
            "KUTHIRAN_TUNNEL" -> LandmarkSpawn(
                roadId = "ROAD_NH544_KUTHIRAN_LEFT_TUBE",
                pos = Vec3(2072.0f, 0.06f, 1150.2f),
                headingRad = (PI * 0.5).toFloat(),
                streetLabel = "Kuthiran 6-Lane Twin Tunnel • West Portal (960m)",
                banner = "Zone 2 • Kuthiran Twin-Tube Tunnel (955m/944m) • Western Ghats Mountain & 3000-Lux LEDs"
            )
            "PEECHI_DAM" -> LandmarkSpawn(
                roadId = "ROAD_PEECHI_DAM_ENTRY",
                pos = Vec3(3385.5f, 0.06f, 119.8f),
                headingRad = 1.78f,
                streetLabel = "Peechi Dam Bus Stand & Spillway • Entry Gate",
                banner = "Zone 2 • Peechi Dam Walled Bus Stand (Entry/Exit Gates), Botanical Gardens & 4-Gate Masonry Spillway"
            )
            else -> return
        }

        val roadIdx = s.roads.indexOfFirst { it.id == spec.roadId }.coerceAtLeast(0)
        cachedRoadScanIdx = -1
        cachedFrameScanIdx = 0
        entryBarrierLatchOpen = false
        exitBarrierLatchOpen = false
        vadakkeEntryBarrierLatchOpen = false
        vadakkeExitBarrierLatchOpen = false
        stationEntryBarrierLatchOpen = false
        stationExitBarrierLatchOpen = false
        shoranurTermEntryBarrierLatchOpen = false
        shoranurTermExitBarrierLatchOpen = false
        shoranurStationEntryBarrierLatchOpen = false
        shoranurStationExitBarrierLatchOpen = false
        paliyekkaraTollBarrierLatchOpen = false
        paliyekkaraTollActiveLane = 5
        tollPaidInSession = false
        nattikaTollBarrierLatchOpen = false
        nattikaTollActiveLane = 4
        nattikaTollPaidInSession = false
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
        ThrissurTrafficSystem.clearVehiclesNear(spec.pos)
        _uiState.update { current ->
            val updated = current.copy(
                activeRoadIndex = roadIdx,
                parkedBayNumber = when (landmarkId) {
                    "SAKTHAN_STAND" -> sakthanBay.third
                    "VADAKKE_STAND" -> vadakkeBay.third
                    "THRISSUR_RAILWAY_TCR" -> stationBay.third
                    "SHORANUR_TERMINAL" -> 3
                    "SHORANUR_STATION" -> 2
                    else -> current.parkedBayNumber
                },
                busPos = spec.pos,
                busHeadingRad = spec.headingRad,
                sakthanEntryBarrierOpenFrac = 0f,
                sakthanExitBarrierOpenFrac = 0f,
                vadakkeEntryBarrierOpenFrac = 0f,
                vadakkeExitBarrierOpenFrac = 0f,
                stationEntryBarrierOpenFrac = 0f,
                stationExitBarrierOpenFrac = 0f,
                shoranurTermEntryBarrierOpenFrac = 0f,
                shoranurTermExitBarrierOpenFrac = 0f,
                shoranurStationEntryBarrierOpenFrac = 0f,
                shoranurStationExitBarrierOpenFrac = 0f,
                paliyekkaraTollBarrierOpenFrac = 0f,
                paliyekkaraTollActiveLaneIdx = 5,
                nattikaTollBarrierOpenFrac = 0f,
                nattikaTollActiveLaneIdx = 4,
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
            // Sakthan Bus Terminal raised concourses (Boarding Wing & Commercial Wing) and bay dividers
            if ((x in -25.2f..19.8f && z in -208f..-194.8f) || (x in 32.0f..41f && z in -208f..-143.5f)) {
                surfaceY = max(surfaceY, 0.32f)
            } else if (x in -23f..18.5f && z in -191f..-170f) {
                surfaceY = max(surfaceY, 0.22f)
            }
            // Thrissur Railway Station concourse & West railway platforms (real-world SW location)
            if (x in -124.5f..-81.6f && z in -222f..-86f) {
                surfaceY = max(surfaceY, 0.34f)
            }
            // Vadakke Stand U-shaped concourse platforms & angled bay dividers
            if ((x in -64.8f..-58.8f && z in 200.8f..250.8f) ||
                (x in -11.8f..-6.2f && z in 200.8f..250.8f) ||
                (x in -64.8f..-6.2f && z in 240.3f..250.8f)
            ) {
                surfaceY = max(surfaceY, 0.32f)
            } else if (x in -58f..-13f && z in 220f..238f) {
                surfaceY = max(surfaceY, 0.21f)
            }
            // Swaraj Round Maidan & Vadakkumnathan raised stone terrace (strictly inside R=57.5m, clear of R=58.5..73.5m carriageway)
            if (hypot(x - 3.1f, z - 18.0f) < 57.5f) {
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
            // Wadakkanchery Railway Overbridge (ROB) elevated bridge deck surface
            surfaceY = max(surfaceY, ShoranurRailwaySystem.computeRoadSurfaceY(x, z) + 0.02f)
            return surfaceY
        }

        private fun computeCanopyCeilingHeight(x: Float, z: Float): Float {
            // Sakthan Bus Terminal L-shaped wing roofs, Bay 1..8 shelter canopy & Front ENTRY / Back EXIT lintels
            if ((x in -25.5f..20.0f && z in -208f..-194.5f) ||
                (x in -23.5f..19.0f && z in -195.5f..-170.0f) ||
                (x in 32.0f..41f && z in -208f..-143.5f) ||
                (x in -0.5f..15f && z in -141.5f..-138.5f) ||
                (x in 19f..34f && z in -209f..-206f)
            ) {
                return 5.30f
            }
            // Vadakke Stand U-wing roofs, Butterfly Canopy & South ENTRY/EXIT gate lintels
            if (x in -65.5f..-6.0f && z in 199.5f..251.5f) {
                return 4.30f
            }
            // Thrissur Railway Station vaulted concourse, bus portico canopy & East gate portals (SW location)
            if (x in -89.5f..-74.8f && z in -196f..-86f) {
                return 4.85f
            }
            if (x in -65.5f..-62.5f && (z in -175.5f..-160.5f || z in -115.5f..-100.5f)) {
                return 5.20f
            }
            // Overhead Southern Railway Elevated Rail Viaducts (Poothole over M.G. Road & Mullurkara over SH 22)
            val viaductClearY = ShoranurRailwaySystem.computeOverheadViaductClearanceY(x, z)
            if (viaductClearY < 100f) {
                return viaductClearY
            }
            // Shoranur Bus Terminal Arched Wave Canopy & West gate portals
            if (x in 155.5f..180.0f && z in 3047f..3099f) {
                return 5.35f
            }
            if (x in 124.5f..127.5f && (z in 3036.5f..3051.5f || z in 3098.5f..3113.5f)) {
                return 5.25f
            }
            // Shoranur Junction Station Portico Canopy & East gate portals
            if (x in 44.5f..56.5f && z in 3198f..3278f) {
                return 5.15f
            }
            if (x in 72.5f..75.5f && (z in 3190.5f..3205.5f || z in 3264.5f..3279.5f)) {
                return 5.35f
            }
            // Guruvayur Stand shelter & gantry
            if (x in -2782f..-2688f && z in 1265f..1318f) {
                return 5.05f
            }
            // Triprayar Stand shelter & gantry
            if (x in -2214f..-2178f && z in -2446f..-2374f) {
                return 5.05f
            }
            // Zone 2: Kuthiran Twin-Tube Tunnel Vault, Mannuthy Elevated Flyover Soffit & Peechi Check-Post Arch
            val zone2CeilingY = Zone2Nh544KuthiranRenderer.computeZone2OverheadCeilingY(x, z)
            if (zone2CeilingY < 100f) {
                return zone2CeilingY
            }
            // Zone 3: Paliakkara 12-Lane Toll Plaza Silver ACP Space-Frame Canopy
            val zone3CeilingY = Zone3Nh544SouthRenderer.computeZone3OverheadCeilingY(x, z)
            if (zone3CeilingY < 100f) {
                return zone3CeilingY
            }
            // Zone 5: Nattika 10-Lane Toll Plaza Two-Story Overhead Concrete Canopy
            val zone5CeilingY = Zone5Nh66CoastalRenderer.computeZone5OverheadCeilingY(x, z)
            if (zone5CeilingY < 100f) {
                return zone5CeilingY
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
            var smoothTunnelReverb = 0.0
            var cyclePos6 = 0.0
            var turboPhase = 0.0
            val cylPulseWeights = doubleArrayOf(1.18, 0.91, 1.05, 0.94, 1.11, 0.89)

            // Lightweight 3-tap acoustic comb delay buffer for Kuthiran Tunnel vault reverberation (~260ms ring)
            val reverbBufLen = 6144
            val reverbBuf = DoubleArray(reverbBufLen)
            var reverbIdx = 0
            var reverbDampLp = 0.0
            var tireHumLp = 0.0

            var subBassLp = 0.0
            var mufflerLowLp1 = 0.0
            var mufflerLowLp2 = 0.0
            var throatBandLp = 0.0
            var throatBandHp = 0.0
            var knockBandLp = 0.0
            var knockBandHp = 0.0
            var fanRushLp = 0.0
            var noiseSeed = 0x7F4A7C15u

            // Zone 4 Muthuvara LC-14 procedural audio oscillators (electric bell, Indian Railways dual-tone loco horn, rail clack)
            var smoothLcBell = 0.0
            var smoothLcHorn = 0.0
            var smoothLcClack = 0.0
            var lcTimeSec = 0.0
            var railRumbleLp = 0.0

            while (isActive) {
                val tgtRpm = synthTargetRpm.toDouble()
                val tgtLoad = synthTargetLoad.toDouble()
                val tgtSpeed = synthTargetSpeedNorm.toDouble()
                val tgtGain = synthTargetGain.toDouble()
                val tgtTunnel = synthTargetTunnelReverb.toDouble()
                val tgtLcBell = synthLcBellGain.toDouble()
                val tgtLcHorn = synthLcHornGain.toDouble()
                val tgtLcClack = synthLcRailClackGain.toDouble()

                for (i in 0 until chunkSamples) {
                    smoothRpm += (tgtRpm - smoothRpm) * 0.00042
                    smoothLoad += (tgtLoad - smoothLoad) * 0.00075
                    smoothSpeed += (tgtSpeed - smoothSpeed) * 0.00050
                    smoothGain += (tgtGain - smoothGain) * 0.00105
                    smoothTunnelReverb += (tgtTunnel - smoothTunnelReverb) * 0.00065
                    smoothLcBell += (tgtLcBell - smoothLcBell) * 0.0018
                    smoothLcHorn += (tgtLcHorn - smoothLcHorn) * 0.0014
                    smoothLcClack += (tgtLcClack - smoothLcClack) * 0.0016
                    lcTimeSec += 1.0 / sampleRate

                    val lcAudioActive = smoothLcBell > 0.002 || smoothLcHorn > 0.002 || smoothLcClack > 0.002
                    if (smoothGain < 0.0015 && !lcAudioActive) {
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

                    var busEngineMix = (
                        subBassLp * 0.52 +
                        mufflerLowLp2 * 0.68 +
                        throatGrowl * 0.64 +
                        dieselClatter * 0.46 +
                        coolingFanRoar
                    ) * smoothGain

                    // Kuthiran Tunnel Concrete Vault Acoustic Reverberation + Turbocharger Whine + Tire Hum Echo
                    if (smoothTunnelReverb > 0.005) {
                        val turboFreqHz = 880.0 + smoothRpm * 1.15 + smoothLoad * 420.0
                        turboPhase += (2.0 * PI * turboFreqHz) / sampleRate
                        if (turboPhase > 2.0 * PI) turboPhase -= 2.0 * PI
                        val turboWhine = kotlin.math.sin(turboPhase) * (0.055 * smoothLoad + 0.035 * smoothSpeed) * smoothGain

                        tireHumLp += (white - tireHumLp) * (0.045 + smoothSpeed * 0.055)
                        val tireEcho = tireHumLp * smoothSpeed * 0.24

                        val tap1 = reverbBuf[(reverbIdx - 1180 + reverbBufLen) % reverbBufLen]
                        val tap2 = reverbBuf[(reverbIdx - 2690 + reverbBufLen) % reverbBufLen]
                        val tap3 = reverbBuf[(reverbIdx - 4420 + reverbBufLen) % reverbBufLen]
                        val wetRaw = tap1 * 0.48 + tap2 * 0.34 + tap3 * 0.22
                        reverbDampLp += (wetRaw - reverbDampLp) * 0.18

                        val dryPlusTunnel = busEngineMix + turboWhine + tireEcho
                        reverbBuf[reverbIdx] = (dryPlusTunnel + reverbDampLp * 0.56).coerceIn(-1.5, 1.5)
                        reverbIdx = (reverbIdx + 1) % reverbBufLen

                        busEngineMix = dryPlusTunnel + reverbDampLp * (0.68 * smoothTunnelReverb)
                    } else {
                        reverbBuf[reverbIdx] = 0.0
                        reverbIdx = (reverbIdx + 1) % reverbBufLen
                    }

                    // Zone 4 Muthuvara Level Crossing (LC-14) Live Soundscape:
                    // 1) Clanging metallic electric warning bell (14 Hz hammer + 920/1480/2310 Hz overtones)
                    // 2) Authentic Southern Railway electric locomotive dual-tone chord (311 Hz + 370 Hz)
                    // 3) Rhythmic ICF coach bogie rail-joint "clack-clack... clack-clack" + steel wheel rumble
                    var lcMix = 0.0
                    if (smoothLcBell > 0.002) {
                        val strikeCycle = (lcTimeSec * 13.5) % 1.0
                        val bellPulse = if (((lcTimeSec * 1.4) % 1.0) < 0.72) kotlin.math.exp(-strikeCycle * 6.5) else 0.12
                        val twoPiT = 2.0 * PI * lcTimeSec
                        val bellTone = kotlin.math.sin(twoPiT * 920.0) * 0.55 +
                            kotlin.math.sin(twoPiT * 1480.0) * 0.30 +
                            kotlin.math.sin(twoPiT * 2310.0) * 0.15
                        lcMix += bellTone * bellPulse * smoothLcBell * 0.36
                    }
                    if (smoothLcHorn > 0.002) {
                        val twoPiT = 2.0 * PI * lcTimeSec
                        val hornRaw = kotlin.math.sin(twoPiT * 311.1) * 0.52 +
                            kotlin.math.sin(twoPiT * 370.0) * 0.48 +
                            kotlin.math.sin(twoPiT * 622.2) * 0.22 +
                            kotlin.math.sin(twoPiT * 740.0) * 0.18
                        lcMix += (hornRaw / (1.0 + abs(hornRaw) * 0.4)) * smoothLcHorn * 0.38
                    }
                    if (smoothLcClack > 0.002) {
                        val bogieCycle = (lcTimeSec * 1.68) % 1.0 // ~65 km/h bogie pass cadence
                        val clack1 = kotlin.math.exp(-((bogieCycle - 0.05).coerceAtLeast(0.0)) * 55.0) * if (bogieCycle >= 0.05) 1.0 else 0.0
                        val clack2 = kotlin.math.exp(-((bogieCycle - 0.17).coerceAtLeast(0.0)) * 55.0) * if (bogieCycle >= 0.17) 1.0 else 0.0
                        val clackImpulse = (clack1 + clack2) * white * 0.85
                        railRumbleLp += (white - railRumbleLp) * 0.045
                        lcMix += (clackImpulse + railRumbleLp * 0.55) * smoothLcClack * 0.42
                    }

                    val totalMix = busEngineMix + lcMix
                    val limited = totalMix / (1.0 + abs(totalMix) * 0.35)
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
        fuelVoiceManager?.release()
        fuelVoiceManager = null
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
    private val cachedBuildingCrashPcm: ShortArray by lazy { buildBuildingCrashPcm() }
    private val cachedTreeHitPcm: ShortArray by lazy { buildTreeImpactPcm() }
    private val cachedMetalPoleHitPcm: ShortArray by lazy { buildMetalPoleHitPcm() }

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

    private fun triggerCollisionImpactSound(material: ImpactMaterial, impactSpeedMps: Float) {
        val vol = (0.36f + ((impactSpeedMps - 1.0f) / 10.0f).coerceIn(0f, 1f) * 0.64f).coerceIn(0.36f, 1.0f)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (material) {
                    ImpactMaterial.CONCRETE_BUILDING, ImpactMaterial.STONE_WALL ->
                        playStaticPcmTrack(cachedBuildingCrashPcm, 22050, 540L, volume = vol)
                    ImpactMaterial.TREE_WOOD ->
                        playStaticPcmTrack(cachedTreeHitPcm, 22050, 460L, volume = vol)
                    ImpactMaterial.METAL_SIGN_POLE, ImpactMaterial.TRAFFIC_VEHICLE ->
                        playStaticPcmTrack(cachedMetalPoleHitPcm, 22050, 480L, volume = vol)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildBuildingCrashPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 0.52
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var seed = 0x7F4A7C15u
        var lowLp = 0.0
        var crunchLp = 0.0
        var crunchHp = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0

            // Heavy bus chassis/concrete impact thud (sweeping sub-bass + frame boom)
            val thudFreq = 86.0 * kotlin.math.exp(-t * 9.5) + 38.0
            val thud = sin(2.0 * PI * thudFreq * t) * kotlin.math.exp(-t * 11.5) * 1.15
            val subBoom = sin(2.0 * PI * 48.0 * t) * kotlin.math.exp(-t * 8.0) * 0.68

            // Crumpling sheet-metal + concrete scrape burst
            lowLp += (white - lowLp) * 0.14
            crunchLp += (white - crunchLp) * 0.42
            crunchHp += (crunchLp - crunchHp) * 0.11
            val crunchBand = (crunchLp - crunchHp) * kotlin.math.exp(-t * 14.0) * 1.05
            val debrisTail = lowLp * kotlin.math.exp(-t * 6.8) * 0.58

            // Brief bumper/steel frame metallic shudder
            val frameClang = (sin(2.0 * PI * 215.0 * t) * 0.34 + sin(2.0 * PI * 478.0 * t) * 0.22) *
                kotlin.math.exp(-t * 22.0)

            val attack = (t / 0.004).coerceIn(0.0, 1.0)
            val endFade = ((durationSec - t) / 0.06).coerceIn(0.0, 1.0)
            val raw = (thud + subBoom + crunchBand + debrisTail + frameClang) * attack * endFade
            val sat = raw / (1.0 + abs(raw) * 0.45)
            pcm[i] = (sat.coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.92).toInt().toShort()
        }
        return pcm
    }

    private fun buildTreeImpactPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 0.44
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var seed = 0x3B9ACA07u
        var rustleLp = 0.0
        var rustleHp = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0

            // Resonant woody trunk knock + branch snap
            val trunkThump = (sin(2.0 * PI * (128.0 - 42.0 * t.coerceAtMost(0.2)) * t) * 0.92 +
                sin(2.0 * PI * 246.0 * t) * 0.40) * kotlin.math.exp(-t * 16.5)
            val woodCrack = white * kotlin.math.exp(-((t - 0.018) * (t - 0.018)) / 0.00018) * 0.72

            // Swaying canopy / foliage rustle tail
            rustleLp += (white - rustleLp) * 0.34
            rustleHp += (rustleLp - rustleHp) * 0.14
            val rustleEnv = ((t / 0.035).coerceIn(0.0, 1.0)) * kotlin.math.exp(-t * 5.2)
            val leafRustle = (rustleLp - rustleHp) * rustleEnv * 0.78

            val endFade = ((durationSec - t) / 0.07).coerceIn(0.0, 1.0)
            val raw = (trunkThump + woodCrack + leafRustle) * endFade
            val sat = raw / (1.0 + abs(raw) * 0.38)
            pcm[i] = (sat.coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.86).toInt().toShort()
        }
        return pcm
    }

    private fun buildMetalPoleHitPcm(): ShortArray {
        val sampleRate = 22050
        val durationSec = 0.46
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)
        var seed = 0xB5297A4Du

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            seed = seed * 1664525u + 1013904223u
            val white = ((seed shr 16).toInt() / 32768.0) - 1.0

            // Sharp impact transient
            val strike = white * kotlin.math.exp(-t * 140.0) * 0.75
            val bumperThud = sin(2.0 * PI * 115.0 * t) * kotlin.math.exp(-t * 24.0) * 0.52

            // Inharmonic hollow steel signboard pole ring modes
            val ring = (
                sin(2.0 * PI * 324.0 * t) * 0.56 * kotlin.math.exp(-t * 9.0) +
                sin(2.0 * PI * 692.0 * t) * 0.44 * kotlin.math.exp(-t * 11.5) +
                sin(2.0 * PI * 1148.0 * t) * 0.32 * kotlin.math.exp(-t * 14.5) +
                sin(2.0 * PI * 1885.0 * t) * 0.22 * kotlin.math.exp(-t * 18.0)
            )

            val endFade = ((durationSec - t) / 0.06).coerceIn(0.0, 1.0)
            val raw = (strike + bumperThud + ring) * endFade
            val sat = raw / (1.0 + abs(raw) * 0.36)
            pcm[i] = (sat.coerceIn(-0.98, 0.98) * Short.MAX_VALUE * 0.88).toInt().toShort()
        }
        return pcm
    }

    private fun playStaticPcmTrack(
        pcm: ShortArray,
        sampleRate: Int,
        sleepMs: Long,
        volume: Float = 1.0f
    ) {
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
        track.setVolume(volume.coerceIn(0.08f, 1.0f))
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep(sleepMs)
        track.release()
    }
}
