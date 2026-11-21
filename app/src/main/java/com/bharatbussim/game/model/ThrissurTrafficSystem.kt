package com.bharatbussim.game.model

import com.bharatbussim.game.ui.MuthuvaraLcPhase
import com.bharatbussim.game.ui.ShoranurRailwaySystem
import com.bharatbussim.game.ui.Zone3Nh544SouthRenderer
import com.bharatbussim.game.ui.Zone4KoleRailHeritageRenderer
import com.bharatbussim.game.ui.Zone5Nh66CoastalRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * District-wide 60 Hz AI Road Traffic & Bottleneck Simulation Engine for Thrissur District.
 *
 * Strictly enforces:
 * - Road Design Rules: vehicles travel exclusively inside drivable road lane bounds (never on
 *   medians, sidewalks, shoulders, or inside bus terminal yards/driveways).
 * - Indian Left-Hand Traffic (LHT) on all two-way & divided corridors, 1-way clockwise flow on
 *   Swaraj Round, and directional Kuthiran Twin-Tube Tunnel traffic.
 * - Authentic traffic blocks & queues at Paliyekkara 12-Lane Toll Plaza (NH 544), Nattika 10-Lane
 *   Toll Plaza (NH 66), Muthuvara Railway Level Crossing (LC-14 on SH 69), and Manikandanal Signal.
 * - Zero-allocation double-buffered render snapshot for lock-free 60 FPS OpenGL ES 3.0 instancing.
 */
object ThrissurTrafficSystem {

    private const val MAX_RENDER_INSTANCES = 280
    private const val PLAYER_BUS_HALF_X = 1.38f
    private const val PLAYER_BUS_HALF_Z = 5.95f

    private class TrafficLaneTrack(
        val road: ThrissurOsmRoad,
        val direction: Int,       // +1 along frames, -1 reverse along frames
        val lateralOffsetM: Float,// Signed offset along frame.right (LHT: -offset for +1, +offset for -1)
        val totalLenM: Float,
        val isClosedLoop: Boolean,
        val isHighway: Boolean,
        val isManikandanalApproach: Boolean,
        val manikandanalStopS: Float,
        val agents: ArrayList<SplineAgent> = ArrayList(8)
    )

    private class SplineAgent(
        var modelIndex: Int,
        var sDist: Float,         // Progress along lane direction (0 .. totalLenM)
        var speedMps: Float,
        var maxSpeedMps: Float,
        var halfX: Float,
        var halfZ: Float,
        var brakeGlow: Float = 0f,
        var isQueued: Boolean = false,
        var wx: Float = 0f,
        var wy: Float = 0.06f,
        var wz: Float = 0f,
        var headingRad: Float = 0f,
        var pitchRad: Float = 0f,
        var hiddenInBottleneck: Boolean = false
    )

    private val lanes = ArrayList<TrafficLaneTrack>(48)
    private val bottlenecks = ThrissurTrafficBottlenecks()
    private var builtForRoadCount = -1

    // Double-buffered zero-allocation render & collision snapshot
    private val bufferA = Array(MAX_RENDER_INSTANCES) { TrafficRenderInstance() }
    private val bufferB = Array(MAX_RENDER_INSTANCES) { TrafficRenderInstance() }
    @Volatile
    private var activeReadBuffer: Array<TrafficRenderInstance> = bufferA
    @Volatile
    private var activeReadCount: Int = 0
    private var writeToB = true

    @Volatile
    var latestTelemetry: TrafficBlockTelemetry = TrafficBlockTelemetry()
        private set

    fun getReadSnapshot(): Pair<Array<TrafficRenderInstance>, Int> = activeReadBuffer to activeReadCount

    fun reset() {
        lanes.clear()
        builtForRoadCount = -1
        activeReadCount = 0
    }

    /**
     * Nudges any ambient or bottleneck traffic vehicle overlapping a player teleport/spawn point
     * safely out of the bus footprint so the player never spawns clipped inside an AI vehicle.
     */
    fun clearVehiclesNear(pos: Vec3, radiusM: Float = 15.5f) {
        bottlenecks.clearVehiclesNear(pos, radiusM)
        for (lIdx in 0 until lanes.size) {
            val lane = lanes[lIdx]
            val agents = lane.agents
            for (i in 0 until agents.size) {
                val a = agents[i]
                if (!a.hiddenInBottleneck && hypot(a.wx - pos.x, a.wz - pos.z) < radiusM) {
                    val shift = 24.0f
                    a.sDist = if (lane.isClosedLoop) {
                        ((a.sDist - shift) % lane.totalLenM + lane.totalLenM) % lane.totalLenM
                    } else {
                        val back = a.sDist - shift
                        if (back >= 10f) back else (a.sDist + shift).coerceAtMost(lane.totalLenM - 10f)
                    }
                    a.speedMps = 0f
                    val frameDist = if (lane.direction == +1) a.sDist else (lane.totalLenM - a.sDist)
                    sampleLanePose(lane.road.frames, frameDist, lane.direction, lane.lateralOffsetM, a)
                }
            }
        }
    }

    /**
     * Initializes ambient AI traffic lanes across all drivable Thrissur District roads
     * (excluding bus terminal entry/exit ramps so terminals stay 100% clear for the player).
     */
    fun initializeForRoads(roads: List<ThrissurOsmRoad>) {
        if (roads.isEmpty()) return
        if (builtForRoadCount == roads.size && lanes.isNotEmpty()) return
        builtForRoadCount = roads.size
        lanes.clear()
        bottlenecks.ensureInitialized()

        var rng = 90210
        fun nextRand(): Int {
            rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
            return rng
        }

        for (road in roads) {
            if (isTerminalOrExcludedRoad(road.id)) continue
            if (road.frames.size < 6) continue
            val totalLen = road.frames.last().cumulativeDist
            if (totalLen < 60f) continue

            val isHighway = road.roadType == "national_highway" || road.roadType == "state_highway"
            val pool = when {
                road.id == "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR" -> TrafficVehicleCatalog.sh69Pool
                isHighway -> TrafficVehicleCatalog.highwayPool
                else -> TrafficVehicleCatalog.cityPool
            }

            // Determine lane lateral offsets safely inside road half-width (keeping >= 1.4m margin from curb/median!)
            val hw = (road.widthM * 0.5f).coerceAtLeast(4.5f)
            val laneConfigs: List<Pair<Int, Float>> = when {
                road.id == "ROAD_SWARAJ_ROUND" -> {
                    // 1-Way Clockwise Swaraj Round: 2 smooth concentric lanes inside 15m carriageway
                    listOf(+1 to -2.6f, +1 to +2.4f)
                }
                road.id == "ROAD_NH544_KUTHIRAN_LEFT_TUBE" -> {
                    // 3-Lane 10.5m Eastbound Tunnel Tube (dir = +1)
                    listOf(+1 to -2.2f, +1 to +1.8f)
                }
                road.id == "ROAD_NH544_KUTHIRAN_RIGHT_TUBE" -> {
                    // 3-Lane 10.5m Westbound Tunnel Tube (dir = -1)
                    listOf(-1 to +2.2f, -1 to -1.8f)
                }
                road.divided && road.widthM >= 18f -> {
                    // 4-lane or 6-lane divided NH with central median (median half-width ~1.4m)
                    val innerOff = (hw * 0.42f).coerceIn(3.4f, 4.6f)
                    val outerOff = (hw * 0.72f).coerceIn(6.2f, 7.6f)
                    listOf(
                        +1 to -outerOff,
                        +1 to -innerOff,
                        -1 to +innerOff,
                        -1 to +outerOff
                    )
                }
                else -> {
                    // Standard 2-lane undivided Kerala arterial/SH (strict Indian LHT: -off for +1, +off for -1)
                    val off = (hw * 0.48f).coerceIn(2.15f, 3.35f)
                    listOf(+1 to -off, -1 to +off)
                }
            }

            // Detect Manikandanal Traffic Signal stop position on ROAD_SAKTHAN_TO_SWARAJ (+1 Northbound into Swaraj Round)
            val isSakthanApproach = road.id == "ROAD_SAKTHAN_TO_SWARAJ"
            var maniStopS = -1f
            if (isSakthanApproach) {
                var bestD = Float.MAX_VALUE
                for (f in road.frames) {
                    val d = hypot(f.pos.x - 20.9f, f.pos.z - (-70.0f))
                    if (d < bestD) {
                        bestD = d
                        maniStopS = f.cumulativeDist
                    }
                }
            }

            // Decide vehicle count per lane proportional to road length (capped so total ambient stays <= 165)
            val baseSpacingM = when {
                road.id == "ROAD_SWARAJ_ROUND" -> 62f
                isSakthanApproach -> 58f
                road.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" -> 210f
                road.id == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY" -> 340f
                road.id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" -> 320f
                road.id == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" -> 340f
                isHighway -> 285f
                else -> 135f
            }

            for ((dir, latOff) in laneConfigs) {
                val track = TrafficLaneTrack(
                    road = road,
                    direction = dir,
                    lateralOffsetM = latOff,
                    totalLenM = totalLen,
                    isClosedLoop = road.closedLoop,
                    isHighway = isHighway,
                    isManikandanalApproach = isSakthanApproach && dir == +1 && maniStopS > 15f,
                    manikandanalStopS = maniStopS
                )

                val maxPerLane = when {
                    road.id == "ROAD_SWARAJ_ROUND" -> 7
                    isSakthanApproach -> 4
                    isHighway && road.divided -> 5
                    else -> 6
                }
                val count = ((totalLen / baseSpacingM).toInt()).coerceIn(2, maxPerLane)
                val usableLen = if (road.closedLoop) totalLen else (totalLen - 36f).coerceAtLeast(30f)
                val stepS = usableLen / count

                for (k in 0 until count) {
                    val mIdx = pool[nextRand() % pool.size]
                    val spec = TrafficVehicleCatalog.specFor(mIdx)
                    val jitter = ((nextRand() % 100) / 100f - 0.5f) * stepS * 0.35f
                    val sInit = if (road.closedLoop) {
                        ((k * stepS + jitter) % totalLen + totalLen) % totalLen
                    } else {
                        (18f + k * stepS + jitter).coerceIn(14f, totalLen - 18f)
                    }
                    val speedVariation = 0.86f + (nextRand() % 28) * 0.01f
                    val maxSpd = (if (isHighway) spec.highwaySpeedMps else spec.citySpeedMps) * speedVariation

                    track.agents.add(
                        SplineAgent(
                            modelIndex = mIdx,
                            sDist = sInit,
                            speedMps = maxSpd * 0.72f,
                            maxSpeedMps = maxSpd,
                            halfX = spec.defaultHalfX,
                            halfZ = spec.defaultHalfZ
                        )
                    )
                }
                lanes.add(track)
            }
        }
    }

    private fun isTerminalOrExcludedRoad(id: String): Boolean {
        return id == "ROAD_SAKTHAN_STAND_ENTRY" ||
            id == "ROAD_SWARAJ_TO_VADAKKE" ||
            id == "ROAD_VADAKKE_STAND_EXIT" ||
            id == "ROAD_RAILWAY_STATION_ACCESS" ||
            id == "ROAD_RAILWAY_STATION_EXIT" ||
            id == "ROAD_SHORANUR_TERMINAL_ENTRY" ||
            id == "ROAD_SHORANUR_TERMINAL_EXIT" ||
            id == "ROAD_SHORANUR_STATION_ENTRY" ||
            id == "ROAD_SHORANUR_STATION_EXIT" ||
            id == "ROAD_PEECHI_DAM_ENTRY" ||
            id == "ROAD_PEECHI_DAM_EXIT"
    }

    private fun isInsideBottleneckZone(x: Float, z: Float): Boolean {
        // Paliyekkara 12-Lane Toll Plaza (owned by ThrissurTrafficBottlenecks)
        if (x in (Zone3Nh544SouthRenderer.TOLL_CENTER_X - 58f)..(Zone3Nh544SouthRenderer.TOLL_CENTER_X + 58f) &&
            z in (Zone3Nh544SouthRenderer.TOLL_CENTER_Z - 165f)..(Zone3Nh544SouthRenderer.TOLL_CENTER_Z + 165f)
        ) return true

        // Nattika 10-Lane Toll Plaza (owned by ThrissurTrafficBottlenecks)
        if (x in (Zone5Nh66CoastalRenderer.NATTIKA_CENTER_X - 58f)..(Zone5Nh66CoastalRenderer.NATTIKA_CENTER_X + 58f) &&
            z in (Zone5Nh66CoastalRenderer.NATTIKA_CENTER_Z - 165f)..(Zone5Nh66CoastalRenderer.NATTIKA_CENTER_Z + 165f)
        ) return true

        // Muthuvara Animated Railway Level Crossing LC-14 corridor (owned by ThrissurTrafficBottlenecks)
        if (x in (Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X - 24f)..(Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X + 24f) &&
            z in 480f..740f
        ) return true

        // Keep Sakthan Terminal exit courtyard (Z < -194 near X=10..42) completely clear
        if (x in -34f..44f && z in -212f..-138f) return true

        return false
    }

    /**
     * Advances all district traffic, toll plaza queues, railway gate blocks, and Manikandanal signal
     * queues by `dt` seconds and publishes an updated zero-allocation render snapshot.
     */
    fun step(
        dt: Float,
        simTimeSec: Float,
        roads: List<ThrissurOsmRoad>,
        playerPos: Vec3,
        playerHeadingRad: Float,
        manikandanalSignal: TrafficSignalAspect,
        muthuvaraLcPhase: MuthuvaraLcPhase,
        muthuvaraLcBarrierClosedFrac: Float,
        playerPaliyekkaraLane: Int,
        playerPaliyekkaraOpenFrac: Float,
        playerNattikaLane: Int,
        playerNattikaOpenFrac: Float
    ): TrafficBlockTelemetry {
        if (roads.isEmpty()) return latestTelemetry
        if (lanes.isEmpty()) initializeForRoads(roads)

        val safeDt = dt.coerceIn(0.001f, 0.05f)

        // 1. Step all dedicated bottleneck queues (Paliyekkara Toll, Nattika Toll, Muthuvara LC-14)
        val bottleneckBanner = bottlenecks.step(
            dt = safeDt,
            playerPos = playerPos,
            playerHeadingRad = playerHeadingRad,
            muthuvaraLcPhase = muthuvaraLcPhase,
            muthuvaraLcBarrierClosedFrac = muthuvaraLcBarrierClosedFrac,
            playerPaliyekkaraLane = playerPaliyekkaraLane,
            playerPaliyekkaraOpenFrac = playerPaliyekkaraOpenFrac,
            playerNattikaLane = playerNattikaLane,
            playerNattikaOpenFrac = playerNattikaOpenFrac
        )

        // 2. Step ambient road-spline AI traffic across Thrissur District
        var manikandanalQueued = 0
        for (lIdx in 0 until lanes.size) {
            val lane = lanes[lIdx]
            val agents = lane.agents
            if (agents.isEmpty()) continue

            // Sort agents by sDist descending (index 0 is front-most along travel direction)
            for (i in 1 until agents.size) {
                val key = agents[i]
                var j = i - 1
                while (j >= 0 && agents[j].sDist < key.sDist) {
                    agents[j + 1] = agents[j]
                    j--
                }
                agents[j + 1] = key
            }

            val signalRed = lane.isManikandanalApproach && manikandanalSignal != TrafficSignalAspect.Green
            val frames = lane.road.frames

            for (i in 0 until agents.size) {
                val a = agents[i]
                val spec = TrafficVehicleCatalog.specFor(a.modelIndex)
                val frontS = a.sDist + a.halfZ

                var desiredSpeed = a.maxSpeedMps
                var mustStopDist = Float.POSITIVE_INFINITY
                var leadSpeed = desiredSpeed

                // A. Manikandanal Traffic Signal red-light queue on M.O. Road -> Swaraj Round South
                if (signalRed && frontS <= lane.manikandanalStopS + 1.5f) {
                    val gap = max(0f, lane.manikandanalStopS - frontS)
                    if (gap < 55f) {
                        mustStopDist = min(mustStopDist, gap)
                        leadSpeed = 0f
                    }
                }

                // B. Lead vehicle in the exact same lane (including wrap-around for closed-loop Swaraj Round)
                if (i > 0) {
                    val lead = agents[i - 1]
                    val gap = (lead.sDist - lead.halfZ) - frontS - 2.3f
                    if (gap < mustStopDist) {
                        mustStopDist = max(0f, gap)
                        leadSpeed = lead.speedMps
                    }
                } else if (lane.isClosedLoop && agents.size > 1) {
                    val lead = agents[agents.size - 1]
                    val gap = (lead.sDist + lane.totalLenM - lead.halfZ) - frontS - 2.3f
                    if (gap < mustStopDist) {
                        mustStopDist = max(0f, gap)
                        leadSpeed = lead.speedMps
                    }
                }

                // C. Player bus collision avoidance (smoothly brake if player Hino RM2 is ahead in our lane)
                if (!a.hiddenInBottleneck) {
                    val dxP = playerPos.x - a.wx
                    val dzP = playerPos.z - a.wz
                    val distSqP = dxP * dxP + dzP * dzP
                    if (distSqP < 36f * 36f) {
                        val fX = sin(a.headingRad)
                        val fZ = cos(a.headingRad)
                        val fwdProj = dxP * fX + dzP * fZ
                        val latProj = abs(dxP * fZ - dzP * fX)
                        if (fwdProj > 0f && latProj < (a.halfX + PLAYER_BUS_HALF_X + 0.55f)) {
                            val gapP = fwdProj - a.halfZ - PLAYER_BUS_HALF_Z - 2.1f
                            if (gapP < mustStopDist) {
                                mustStopDist = max(0f, gapP)
                                leadSpeed = 0f
                            }
                        }
                    }
                }

                if (mustStopDist < 42f) {
                    val safeV = sqrt(max(0f, 2f * (spec.decelMps2 * 0.55f) * mustStopDist))
                    desiredSpeed = min(desiredSpeed, max(leadSpeed * 0.94f, safeV))
                    if (mustStopDist < 0.5f && leadSpeed < 0.6f) desiredSpeed = 0f
                }

                val prevSpd = a.speedMps
                if (a.speedMps < desiredSpeed) {
                    a.speedMps = min(desiredSpeed, a.speedMps + spec.accelMps2 * safeDt)
                } else {
                    val brakeRate = if (mustStopDist < 4.0f) spec.decelMps2 * 1.4f else spec.decelMps2
                    a.speedMps = max(desiredSpeed, a.speedMps - brakeRate * safeDt)
                }
                if (a.speedMps < 0.08f && desiredSpeed <= 0.08f) a.speedMps = 0f

                a.sDist += a.speedMps * safeDt
                if (lane.isClosedLoop) {
                    if (a.sDist >= lane.totalLenM) a.sDist -= lane.totalLenM
                } else {
                    if (a.sDist > lane.totalLenM - 10f) {
                        val tail = agents.last()
                        if (tail === a || (tail.sDist - tail.halfZ) > a.halfZ + 14f) {
                            a.sDist = 10f
                        } else {
                            a.sDist = lane.totalLenM - 10f
                            a.speedMps = 0f
                        }
                    }
                }

                a.brakeGlow = if (a.speedMps < prevSpd - 0.15f * safeDt || (a.speedMps < 0.8f && mustStopDist < 5f)) 1f else 0f
                a.isQueued = a.speedMps < 1.2f && mustStopDist < 5f
                if (lane.isManikandanalApproach && a.isQueued && signalRed) {
                    manikandanalQueued++
                }

                // Convert lane progress sDist -> exact world position, road elevation, heading & bridge slope pitch
                val splineDist = if (lane.direction > 0) a.sDist else (lane.totalLenM - a.sDist)
                sampleLanePose(frames, splineDist, lane.direction, lane.lateralOffsetM, a)
                a.hiddenInBottleneck = isInsideBottleneckZone(a.wx, a.wz)
            }
        }

        // 3. Populate inactive double-buffer snapshot (bottleneck queues first so they are never truncated!)
        val targetBuf = if (writeToB) bufferB else bufferA
        var cursor = bottlenecks.appendToRenderPool(targetBuf, 0, simTimeSec)

        for (lIdx in 0 until lanes.size) {
            val agents = lanes[lIdx].agents
            for (i in 0 until agents.size) {
                val a = agents[i]
                if (a.hiddenInBottleneck) continue
                if (cursor >= MAX_RENDER_INSTANCES) break
                val spec = TrafficVehicleCatalog.specFor(a.modelIndex)
                val inst = targetBuf[cursor++]
                inst.modelIndex = a.modelIndex
                inst.cx = a.wx
                inst.baseY = a.wy
                inst.cz = a.wz
                inst.headingRad = a.headingRad
                inst.pitchRad = a.pitchRad
                inst.brakeGlow = a.brakeGlow
                inst.sirenPhase = if (spec.hasEmergencyBeacon) simTimeSec else 0f
                inst.halfX = a.halfX
                inst.halfZ = a.halfZ
                inst.speedMps = a.speedMps
                inst.label = spec.displayName
                inst.isQueued = a.isQueued
            }
        }

        activeReadBuffer = targetBuf
        activeReadCount = cursor
        writeToB = !writeToB

        val finalBanner = bottleneckBanner ?: if (manikandanalQueued > 0 && hypot(playerPos.x - 20.9f, playerPos.z - (-70f)) < 68f) {
            "Manikandanal Signal RED • $manikandanalQueued Vehicles Waiting for Green"
        } else null

        val telemetry = TrafficBlockTelemetry(
            paliyekkaraLaneOpenFracs = bottlenecks.paliyekkaraLaneOpenFracs.copyOf(),
            nattikaLaneOpenFracs = bottlenecks.nattikaLaneOpenFracs.copyOf(),
            paliyekkaraQueuedCount = bottlenecks.paliyekkaraQueuedCount,
            nattikaQueuedCount = bottlenecks.nattikaQueuedCount,
            muthuvaraQueuedCount = bottlenecks.muthuvaraQueuedCount,
            activeBlockBanner = finalBanner
        )
        latestTelemetry = telemetry
        return telemetry
    }

    /**
     * Binary-searches `frames` by `cumulativeDist`, interpolates position/tangent/right cleanly,
     * clamps lateral offset strictly inside the frame's road carriageway, and samples exact bridge elevation & pitch.
     */
    private fun sampleLanePose(
        frames: List<RoadFrame>,
        targetDist: Float,
        direction: Int,
        desiredLatOffset: Float,
        out: SplineAgent
    ) {
        val lastIdx = frames.lastIndex
        val d = targetDist.coerceIn(0f, frames[lastIdx].cumulativeDist)

        var lo = 0
        var hi = lastIdx
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (frames[mid].cumulativeDist <= d) lo = mid else hi = mid
        }
        val f0 = frames[lo]
        val f1 = frames[hi]
        val span = (f1.cumulativeDist - f0.cumulativeDist).coerceAtLeast(1e-4f)
        val t = ((d - f0.cumulativeDist) / span).coerceIn(0f, 1f)

        val px = f0.pos.x + (f1.pos.x - f0.pos.x) * t
        val pz = f0.pos.z + (f1.pos.z - f0.pos.z) * t
        val rx = f0.right.x + (f1.right.x - f0.right.x) * t
        val rz = f0.right.z + (f1.right.z - f0.right.z) * t
        val rLen = hypot(rx, rz).coerceAtLeast(1e-4f)
        val nrX = rx / rLen
        val nrZ = rz / rLen

        val tx = (f0.tangent.x + (f1.tangent.x - f0.tangent.x) * t) * direction
        val tz = (f0.tangent.z + (f1.tangent.z - f0.tangent.z) * t) * direction

        val halfW = f0.halfWidthM + (f1.halfWidthM - f0.halfWidthM) * t
        val safeMaxOff = (halfW - out.halfX - 0.55f).coerceAtLeast(1.2f)
        val clampedOff = desiredLatOffset.coerceIn(-safeMaxOff, safeMaxOff)

        val wx = px + nrX * clampedOff
        val wz = pz + nrZ * clampedOff
        val heading = atan2(tx, tz)
        val wy = ShoranurRailwaySystem.computeRoadSurfaceY(wx, wz)
        val slope = ShoranurRailwaySystem.computeRoadForwardSlope(wx, wz, heading)

        out.wx = wx
        out.wy = wy
        out.wz = wz
        out.headingRad = heading
        out.pitchRad = atan(slope).coerceIn(-0.15f, 0.15f)
    }
}
