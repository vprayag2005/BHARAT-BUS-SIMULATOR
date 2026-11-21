package com.bharatbussim.game.model

import com.bharatbussim.game.ui.MuthuvaraLcPhase
import com.bharatbussim.game.ui.ShoranurRailwaySystem
import com.bharatbussim.game.ui.Zone3Nh544SouthRenderer
import com.bharatbussim.game.ui.Zone4KoleRailHeritageRenderer
import com.bharatbussim.game.ui.Zone5Nh66CoastalRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Realistic Indian Traffic Block & Queue Simulation for the three major highway bottlenecks:
 *  1. Paliyekkara 12-Lane FASTag Toll Plaza (NH 544 South) — 10 active lanes (Lanes 2–11)
 *     with Cosine S-curve flaring, independent per-lane FASTag scanning delays, occasional
 *     slow-tag/cash hiccups causing realistic uneven bumper-to-bumper queues, and animated
 *     pneumatic boom barriers.
 *  2. Nattika 10-Lane FASTag Toll Plaza (NH 66 Coastal) — 8 active lanes (Lanes 2–9) with
 *     the same multi-lane FASTag queuing and independent boom barrier automation.
 *  3. Muthuvara Animated Railway Level Crossing (LC-14 on SH 69) — synchronized directly with
 *     the 48s Southern Railway Guruvayur Express cycle (`STATE_CLOSING` / `STATE_LOCKED` /
 *     `STATE_TRAIN_PASSING`), forming authentic Northbound & Southbound bumper-to-bumper
 *     traffic jams behind the white stop lines until the gates reopen.
 */
internal class ThrissurTrafficBottlenecks {

    // -------------------------------------------------------------------------
    // Generic Toll Plaza Multi-Lane Queue Controller (Paliyekkara & Nattika)
    // -------------------------------------------------------------------------
    private class TollVehicle(
        var modelIndex: Int,
        val laneIdx: Int,
        val southbound: Boolean, // true: travels -Z (North->South), false: travels +Z (South->North)
        var sPos: Float,         // Progress along travel direction from spawn (0f .. totalLen)
        var speedMps: Float,
        var maxSpeedMps: Float,
        var halfX: Float,
        var halfZ: Float,
        var paidThisPass: Boolean = false,
        var brakeGlow: Float = 0f,
        var isQueued: Boolean = false,
        var wx: Float = 0f,
        var wz: Float = 0f,
        var headingRad: Float = 0f
    )

    private class TollPlazaState(
        val name: String,
        val centerX: Float,
        val centerZ: Float,
        val laneCount: Int,
        val activeSouthLanes: IntArray,
        val activeNorthLanes: IntArray,
        val southBoomOffsetZ: Float,
        val northBoomOffsetZ: Float,
        val flareStartDist: Float, // Distance from centerZ where main 4-lane highway begins widening
        val apronHalfLen: Float,   // Half-length of straight concrete booth lane apron
        val highwayLaneCenterOffset: Float, // Half-carriageway center on approach NH (±5.0m)
        val laneCenterXFn: (Int) -> Float
    ) {
        val totalSpan = (flareStartDist + 42f) * 2f // ~324m corridor centered on plaza
        val halfSpan = totalSpan * 0.5f
        val laneOpenFracs = FloatArray(laneCount)
        val laneScanTimer = FloatArray(laneCount)
        val laneScanDuration = FloatArray(laneCount) { 2.8f }
        val laneSlowHiccupCounter = IntArray(laneCount)
        val laneVehicles = Array(laneCount) { ArrayList<TollVehicle>(6) }
        var queuedCount = 0

        fun initVehicles(seedOffset: Int) {
            for (l in 0 until laneCount) laneVehicles[l].clear()
            var rng = 1337 + seedOffset * 97
            fun nextRand(): Int {
                rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
                return rng
            }

            val pool = TrafficVehicleCatalog.tollPool
            for (laneIdx in activeSouthLanes) {
                // Seed 3–4 vehicles per active lane: 1 already scanning near booth + 1–2 queued behind + 1 approaching!
                val count = if (laneIdx % 2 == 0) 4 else 3
                val stopS = boomStopS(southbound = true)
                var cursorS = stopS - 1.5f - (nextRand() % 7)
                for (k in 0 until count) {
                    val mIdx = pool[nextRand() % pool.size]
                    val spec = TrafficVehicleCatalog.specFor(mIdx)
                    val sInit = if (k == 0) {
                        (stopS - spec.defaultHalfZ - 1.2f - (nextRand() % 10) * 0.6f).coerceIn(18f, totalSpan - 20f)
                    } else {
                        cursorS = (cursorS - spec.defaultHalfZ * 2f - 4.5f - (nextRand() % 14)).coerceAtLeast(10f)
                        cursorS
                    }
                    laneVehicles[laneIdx].add(
                        TollVehicle(
                            modelIndex = mIdx,
                            laneIdx = laneIdx,
                            southbound = true,
                            sPos = sInit,
                            speedMps = if (k <= 1) 0f else 8.0f,
                            maxSpeedMps = spec.highwaySpeedMps * 0.85f,
                            halfX = spec.defaultHalfX,
                            halfZ = spec.defaultHalfZ
                        )
                    )
                }
                laneScanDuration[laneIdx] = 2.6f + (nextRand() % 24) * 0.1f
            }

            for (laneIdx in activeNorthLanes) {
                val count = if (laneIdx % 2 == 1) 4 else 3
                val stopS = boomStopS(southbound = false)
                var cursorS = stopS - 1.5f - (nextRand() % 7)
                for (k in 0 until count) {
                    val mIdx = pool[nextRand() % pool.size]
                    val spec = TrafficVehicleCatalog.specFor(mIdx)
                    val sInit = if (k == 0) {
                        (stopS - spec.defaultHalfZ - 1.2f - (nextRand() % 10) * 0.6f).coerceIn(18f, totalSpan - 20f)
                    } else {
                        cursorS = (cursorS - spec.defaultHalfZ * 2f - 4.5f - (nextRand() % 14)).coerceAtLeast(10f)
                        cursorS
                    }
                    laneVehicles[laneIdx].add(
                        TollVehicle(
                            modelIndex = mIdx,
                            laneIdx = laneIdx,
                            southbound = false,
                            sPos = sInit,
                            speedMps = if (k <= 1) 0f else 8.0f,
                            maxSpeedMps = spec.highwaySpeedMps * 0.85f,
                            halfX = spec.defaultHalfX,
                            halfZ = spec.defaultHalfZ
                        )
                    )
                }
                laneScanDuration[laneIdx] = 2.6f + (nextRand() % 24) * 0.1f
            }
        }

        private fun boomStopS(southbound: Boolean): Float {
            // Southbound travels from z = centerZ + halfSpan down to centerZ - halfSpan (s = (centerZ + halfSpan) - z)
            // Northbound travels from z = centerZ - halfSpan up to centerZ + halfSpan (s = z - (centerZ - halfSpan))
            return if (southbound) {
                halfSpan - southBoomOffsetZ
            } else {
                halfSpan + northBoomOffsetZ
            }
        }

        private fun sampleWorldX(laneIdx: Int, southbound: Boolean, z: Float): Float {
            val laneX = laneCenterXFn(laneIdx)
            val dzAbs = abs(z - centerZ)
            if (dzAbs <= apronHalfLen) return laneX
            // Main 4-lane NH carriageway approach X (Indian LHT: Southbound on East half +X? Wait:
            // When heading South (-Z), LEFT is East (+X), RIGHT is West (-X)! Wait: check Paliyekkara lane assignments:
            // Lanes 0..5 are WEST half (x < centerX), Lanes 6..11 are EAST half (x > centerX)!
            // Paliyekkara boom pivotZ: laneIdx < 6 -> centerZ - 3.6 (Southbound exit side!), laneIdx >= 6 -> centerZ + 3.6 (Northbound exit side!).
            // So Southbound approaches from West half of NH 544 (centerX - highwayLaneCenterOffset) into Lanes 1..5,
            // and Northbound approaches from East half of NH 544 (centerX + highwayLaneCenterOffset) into Lanes 6..10!
            val subOffset = if (southbound) {
                val frac = ((laneIdx - activeSouthLanes.first()).toFloat() / max(1, activeSouthLanes.size - 1)) - 0.5f
                -highwayLaneCenterOffset + frac * 3.0f
            } else {
                val frac = ((laneIdx - activeNorthLanes.first()).toFloat() / max(1, activeNorthLanes.size - 1)) - 0.5f
                +highwayLaneCenterOffset + frac * 3.0f
            }
            val mainX = centerX + subOffset
            if (dzAbs >= flareStartDist) return mainX
            val u = ((dzAbs - apronHalfLen) / (flareStartDist - apronHalfLen)).coerceIn(0f, 1f)
            val smooth = 0.5f - 0.5f * cos(u * PI.toFloat())
            return laneX + (mainX - laneX) * smooth
        }

        fun step(
            dt: Float,
            playerPos: Vec3,
            playerHeadingRad: Float,
            playerActiveLane: Int,
            playerBarrierOpenFrac: Float
        ) {
            var qTotal = 0
            val openRate = dt / 1.05f
            val closeRate = dt / 0.90f

            val playerNearPlaza = abs(playerPos.z - centerZ) < halfSpan + 15f &&
                abs(playerPos.x - centerX) < (laneCount * 2.9f + 12f)

            for (laneIdx in 0 until laneCount) {
                val vehicles = laneVehicles[laneIdx]
                if (vehicles.isEmpty()) {
                    laneOpenFracs[laneIdx] = if (laneIdx == playerActiveLane) playerBarrierOpenFrac else 0f
                    continue
                }
                val southbound = vehicles[0].southbound
                val boomS = boomStopS(southbound)

                // Sort vehicles by sPos descending (index 0 is front-most along travel direction)
                for (i in 1 until vehicles.size) {
                    val key = vehicles[i]
                    var j = i - 1
                    while (j >= 0 && vehicles[j].sPos < key.sPos) {
                        vehicles[j + 1] = vehicles[j]
                        j--
                    }
                    vehicles[j + 1] = key
                }

                // Determine if player is currently occupying this lane
                var playerSInLane = Float.NaN
                if (playerNearPlaza) {
                    val laneX = laneCenterXFn(laneIdx)
                    val pZ = playerPos.z
                    val expectedX = sampleWorldX(laneIdx, southbound, pZ)
                    if (abs(playerPos.x - expectedX) < 2.65f || (abs(pZ - centerZ) < apronHalfLen + 10f && abs(playerPos.x - laneX) < 2.65f)) {
                        playerSInLane = if (southbound) (centerZ + halfSpan) - pZ else pZ - (centerZ - halfSpan)
                    }
                }

                // Check FASTag boom state for AI vehicles in this lane
                var aiRequestsOpen = false
                var scanningVehicle: TollVehicle? = null
                for (i in 0 until vehicles.size) {
                    val v = vehicles[i]
                    val frontS = v.sPos + v.halfZ
                    val rearS = v.sPos - v.halfZ
                    if (v.paidThisPass) {
                        // Keep barrier open while paid vehicle is crossing through the boom arm
                        if (rearS < boomS + 2.8f && frontS > boomS - 5.5f) {
                            aiRequestsOpen = true
                        }
                    } else if (frontS in (boomS - 6.2f)..(boomS + 1.2f) && scanningVehicle == null) {
                        scanningVehicle = v
                    }
                }

                if (scanningVehicle != null) {
                    // Wait until vehicle slows to a near-stop at the FASTag scanner line
                    val frontS = scanningVehicle.sPos + scanningVehicle.halfZ
                    if (frontS >= boomS - 2.4f && scanningVehicle.speedMps < 1.8f) {
                        laneScanTimer[laneIdx] += dt
                        if (laneScanTimer[laneIdx] >= laneScanDuration[laneIdx]) {
                            scanningVehicle.paidThisPass = true
                            laneScanTimer[laneIdx] = 0f
                            aiRequestsOpen = true
                            // Every 4th vehicle in a lane simulates a realistic 6.8s FASTag/cash scan hiccup!
                            laneSlowHiccupCounter[laneIdx]++
                            laneScanDuration[laneIdx] = if (laneSlowHiccupCounter[laneIdx] % 4 == 0) {
                                6.8f
                            } else {
                                2.4f + ((laneIdx * 7 + laneSlowHiccupCounter[laneIdx] * 13) % 22) * 0.1f
                            }
                        }
                    }
                } else if (!aiRequestsOpen) {
                    laneScanTimer[laneIdx] = 0f
                }

                val targetAiOpen = if (aiRequestsOpen) 1f else 0f
                var aiFrac = laneOpenFracs[laneIdx]
                aiFrac = if (aiFrac < targetAiOpen) min(targetAiOpen, aiFrac + openRate) else max(targetAiOpen, aiFrac - closeRate)
                val mergedOpenFrac = if (laneIdx == playerActiveLane) max(aiFrac, playerBarrierOpenFrac) else aiFrac
                laneOpenFracs[laneIdx] = mergedOpenFrac

                // Step each vehicle with smooth IDM-like following + booth stop line + player awareness
                for (i in 0 until vehicles.size) {
                    val v = vehicles[i]
                    val spec = TrafficVehicleCatalog.specFor(v.modelIndex)
                    val frontS = v.sPos + v.halfZ

                    // Target cruise speed slows smoothly approaching the toll plaza apron
                    val distToPlaza = abs((if (southbound) (centerZ + halfSpan) - v.sPos else (centerZ - halfSpan) + v.sPos) - centerZ)
                    var desiredSpeed = when {
                        distToPlaza < apronHalfLen + 14f -> min(v.maxSpeedMps, 7.2f)
                        distToPlaza < flareStartDist -> min(v.maxSpeedMps, 12.2f)
                        else -> v.maxSpeedMps
                    }

                    var mustStopDist = Float.POSITIVE_INFINITY
                    var leadSpeed = desiredSpeed

                    // 1. Toll boom stop line (if this vehicle hasn't cleared FASTag yet)
                    if (!v.paidThisPass) {
                        val stopLineS = boomS - 1.15f
                        val gapToBoom = stopLineS - frontS
                        if (gapToBoom > -1.5f) {
                            mustStopDist = min(mustStopDist, max(0f, gapToBoom))
                            leadSpeed = 0f
                        }
                    } else if (frontS < boomS + 0.5f && mergedOpenFrac < 0.58f) {
                        // Wait for boom arm to finish rising before accelerating under it
                        val gapToBoom = (boomS - 0.9f) - frontS
                        if (gapToBoom > -0.5f) {
                            mustStopDist = min(mustStopDist, max(0f, gapToBoom))
                            leadSpeed = 0f
                        }
                    }

                    // 2. Lead vehicle in the exact same toll lane
                    if (i > 0) {
                        val lead = vehicles[i - 1]
                        val bumperGap = (lead.sPos - lead.halfZ) - frontS - 2.15f
                        if (bumperGap < mustStopDist) {
                            mustStopDist = max(0f, bumperGap)
                            leadSpeed = lead.speedMps
                        }
                    }

                    // 3. Player bus directly ahead in this lane (prevent AI ramming player!)
                    if (!playerSInLane.isNaN()) {
                        val playerRearS = playerSInLane - 6.0f
                        val gapToPlayer = playerRearS - frontS - 2.3f
                        if (gapToPlayer > -3.0f && gapToPlayer < 32.0f && gapToPlayer < mustStopDist) {
                            mustStopDist = max(0f, gapToPlayer)
                            leadSpeed = 0f
                        }
                    }

                    // Compute safe speed from braking distance v^2 / (2 * a)
                    if (mustStopDist < 42f) {
                        val safeApproach = kotlin.math.sqrt(max(0f, 2f * (spec.decelMps2 * 0.58f) * mustStopDist))
                        desiredSpeed = min(desiredSpeed, max(leadSpeed * 0.95f, safeApproach))
                        if (mustStopDist < 0.45f && leadSpeed < 0.6f) {
                            desiredSpeed = 0f
                        }
                    }

                    val prevSpeed = v.speedMps
                    if (v.speedMps < desiredSpeed) {
                        v.speedMps = min(desiredSpeed, v.speedMps + spec.accelMps2 * dt)
                    } else {
                        val brakeRate = if (mustStopDist < 3.5f) spec.decelMps2 * 1.45f else spec.decelMps2
                        v.speedMps = max(desiredSpeed, v.speedMps - brakeRate * dt)
                    }
                    if (v.speedMps < 0.08f && desiredSpeed <= 0.08f) v.speedMps = 0f

                    v.sPos += v.speedMps * dt
                    val braking = (v.speedMps < prevSpeed - 0.15f * dt) || (v.speedMps < 1.2f && !v.paidThisPass)
                    v.brakeGlow = if (braking) 1f else 0f
                    v.isQueued = v.speedMps < 1.5f && !v.paidThisPass && frontS < boomS + 2f
                    if (v.isQueued) qTotal++

                    // Wrap cleanly when exiting far end of toll corridor (only if tail isn't blocked)
                    if (v.sPos > totalSpan) {
                        val tailVehicle = vehicles.last()
                        val minSpawnS = 6.0f
                        if (tailVehicle === v || (tailVehicle.sPos - tailVehicle.halfZ) > minSpawnS + v.halfZ + 8.0f) {
                            v.sPos = minSpawnS
                            v.paidThisPass = false
                            v.speedMps = min(v.maxSpeedMps, 10.5f)
                        }
                    }

                    // Update exact world X, Z, heading along the S-curve flare
                    val zNow = if (southbound) (centerZ + halfSpan) - v.sPos else (centerZ - halfSpan) + v.sPos
                    val xNow = sampleWorldX(laneIdx, southbound, zNow)
                    val zAhead = if (southbound) zNow - 2.0f else zNow + 2.0f
                    val xAhead = sampleWorldX(laneIdx, southbound, zAhead)
                    v.wx = xNow
                    v.wz = zNow
                    v.headingRad = atan2(xAhead - xNow, zAhead - zNow)
                }
            }
            queuedCount = qTotal
        }

        fun clearVehiclesNear(pos: Vec3, radiusM: Float) {
            if (abs(pos.z - centerZ) > halfSpan + radiusM ||
                abs(pos.x - centerX) > laneCount * 3.2f + radiusM
            ) return
            for (laneIdx in 0 until laneCount) {
                val list = laneVehicles[laneIdx]
                for (i in 0 until list.size) {
                    val v = list[i]
                    if (hypot(v.wx - pos.x, v.wz - pos.z) < radiusM) {
                        val sPlayer = if (v.southbound) (centerZ + halfSpan) - pos.z else pos.z - (centerZ - halfSpan)
                        val candidateBack = sPlayer - 15.5f - v.halfZ
                        val newS = if (candidateBack >= 4.0f) {
                            candidateBack
                        } else {
                            (sPlayer + 18.0f + v.halfZ).coerceAtMost(totalSpan - 6.0f)
                        }
                        v.sPos = newS
                        v.speedMps = 0f
                        v.paidThisPass = newS > boomStopS(v.southbound)
                        val zNow = if (v.southbound) (centerZ + halfSpan) - v.sPos else (centerZ - halfSpan) + v.sPos
                        val xNow = sampleWorldX(laneIdx, v.southbound, zNow)
                        val zAhead = if (v.southbound) zNow - 2.0f else zNow + 2.0f
                        val xAhead = sampleWorldX(laneIdx, v.southbound, zAhead)
                        v.wx = xNow
                        v.wz = zNow
                        v.headingRad = atan2(xAhead - xNow, zAhead - zNow)
                    }
                }
            }
        }

        fun appendToRenderPool(
            outPool: Array<TrafficRenderInstance>,
            startIdx: Int,
            simTimeSec: Float
        ): Int {
            var cursor = startIdx
            for (laneIdx in 0 until laneCount) {
                val list = laneVehicles[laneIdx]
                for (i in 0 until list.size) {
                    if (cursor >= outPool.size) return cursor
                    val v = list[i]
                    val spec = TrafficVehicleCatalog.specFor(v.modelIndex)
                    val inst = outPool[cursor++]
                    inst.modelIndex = v.modelIndex
                    inst.cx = v.wx
                    inst.baseY = ShoranurRailwaySystem.computeRoadSurfaceY(v.wx, v.wz)
                    inst.cz = v.wz
                    inst.headingRad = v.headingRad
                    inst.pitchRad = 0f
                    inst.brakeGlow = v.brakeGlow
                    inst.sirenPhase = if (spec.hasEmergencyBeacon) simTimeSec else 0f
                    inst.halfX = v.halfX
                    inst.halfZ = v.halfZ
                    inst.speedMps = v.speedMps
                    inst.label = spec.displayName
                    inst.isQueued = v.isQueued
                }
            }
            return cursor
        }
    }

    // -------------------------------------------------------------------------
    // Muthuvara Animated Railway Level Crossing (LC-14 on SH 69) Block Engine
    // -------------------------------------------------------------------------
    private class LcVehicle(
        var modelIndex: Int,
        val northbound: Boolean, // true: South->North (+Z on West lane X=-562.65), false: North->South (-Z on East lane X=-557.35)
        var sPos: Float,         // 0 .. LC_CORRIDOR_LEN (250m centered on Z = 610.0)
        var speedMps: Float,
        var maxSpeedMps: Float,
        var halfX: Float,
        var halfZ: Float,
        var releaseDelaySec: Float = 0f,
        var brakeGlow: Float = 0f,
        var isQueued: Boolean = false,
        var wx: Float = 0f,
        var wz: Float = 0f
    )

    private val lcNorthbound = ArrayList<LcVehicle>(10)
    private val lcSouthbound = ArrayList<LcVehicle>(10)
    private var prevLcBlocked = false
    var muthuvaraQueuedCount = 0
        private set

    private val paliyekkara = TollPlazaState(
        name = "Paliyekkara Toll Plaza (NH 544)",
        centerX = Zone3Nh544SouthRenderer.TOLL_CENTER_X,
        centerZ = Zone3Nh544SouthRenderer.TOLL_CENTER_Z,
        laneCount = Zone3Nh544SouthRenderer.TOLL_LANE_COUNT,
        activeSouthLanes = intArrayOf(1, 2, 3, 4, 5),
        activeNorthLanes = intArrayOf(6, 7, 8, 9, 10),
        southBoomOffsetZ = -3.6f,
        northBoomOffsetZ = +3.6f,
        flareStartDist = 120.0f,
        apronHalfLen = 28.0f,
        highwayLaneCenterOffset = 5.2f,
        laneCenterXFn = { Zone3Nh544SouthRenderer.tollLaneCenterX(it) }
    )

    private val nattika = TollPlazaState(
        name = "Nattika Toll Plaza (NH 66)",
        centerX = Zone5Nh66CoastalRenderer.NATTIKA_CENTER_X,
        centerZ = Zone5Nh66CoastalRenderer.NATTIKA_CENTER_Z,
        laneCount = Zone5Nh66CoastalRenderer.NATTIKA_LANE_COUNT,
        activeSouthLanes = intArrayOf(1, 2, 3, 4),
        activeNorthLanes = intArrayOf(5, 6, 7, 8),
        southBoomOffsetZ = -3.2f,
        northBoomOffsetZ = +3.2f,
        flareStartDist = 120.0f,
        apronHalfLen = 28.0f,
        highwayLaneCenterOffset = 5.4f,
        laneCenterXFn = { Zone5Nh66CoastalRenderer.nattikaLaneCenterX(it) }
    )

    val paliyekkaraLaneOpenFracs: FloatArray get() = paliyekkara.laneOpenFracs
    val nattikaLaneOpenFracs: FloatArray get() = nattika.laneOpenFracs
    val paliyekkaraQueuedCount: Int get() = paliyekkara.queuedCount
    val nattikaQueuedCount: Int get() = nattika.queuedCount

    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        paliyekkara.initVehicles(seedOffset = 1)
        nattika.initVehicles(seedOffset = 2)
        initMuthuvaraLcVehicles()
    }

    private fun initMuthuvaraLcVehicles() {
        lcNorthbound.clear()
        lcSouthbound.clear()
        var rng = 4242
        fun nextRand(): Int {
            rng = (rng * 1103515245 + 12345) and 0x7FFFFFFF
            return rng
        }
        val pool = TrafficVehicleCatalog.sh69Pool
        val laneOffset = 2.65f // Strict Indian LHT inside MUTHUVARA_ROAD_HALF_W = 5.30m!
        val cx = Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X

        // 7 Northbound vehicles (approaching Z = 485 -> 735 on West lane X = cx - 2.65)
        var sCur = 112f
        for (i in 0 until 7) {
            val mIdx = pool[nextRand() % pool.size]
            val spec = TrafficVehicleCatalog.specFor(mIdx)
            val sVal = (sCur - i * 16.5f).coerceIn(8f, 240f)
            lcNorthbound.add(
                LcVehicle(
                    modelIndex = mIdx,
                    northbound = true,
                    sPos = sVal,
                    speedMps = 9.5f,
                    maxSpeedMps = spec.citySpeedMps * 1.08f,
                    halfX = spec.defaultHalfX,
                    halfZ = spec.defaultHalfZ,
                    wx = cx - laneOffset,
                    wz = LC_Z_MIN + sVal
                )
            )
            sCur -= (spec.defaultHalfZ * 2f + 12f)
        }
        // 7 Southbound vehicles (approaching Z = 735 -> 485 on East lane X = cx + 2.65)
        sCur = 112f
        for (i in 0 until 7) {
            val mIdx = pool[nextRand() % pool.size]
            val spec = TrafficVehicleCatalog.specFor(mIdx)
            val sVal = (sCur - i * 16.5f).coerceIn(8f, 240f)
            lcSouthbound.add(
                LcVehicle(
                    modelIndex = mIdx,
                    northbound = false,
                    sPos = sVal,
                    speedMps = 9.5f,
                    maxSpeedMps = spec.citySpeedMps * 1.08f,
                    halfX = spec.defaultHalfX,
                    halfZ = spec.defaultHalfZ,
                    wx = cx + laneOffset,
                    wz = LC_Z_MAX - sVal
                )
            )
            sCur -= (spec.defaultHalfZ * 2f + 12f)
        }
    }

    fun step(
        dt: Float,
        playerPos: Vec3,
        playerHeadingRad: Float,
        muthuvaraLcPhase: MuthuvaraLcPhase,
        muthuvaraLcBarrierClosedFrac: Float,
        playerPaliyekkaraLane: Int,
        playerPaliyekkaraOpenFrac: Float,
        playerNattikaLane: Int,
        playerNattikaOpenFrac: Float
    ): String? {
        ensureInitialized()

        paliyekkara.step(dt, playerPos, playerHeadingRad, playerPaliyekkaraLane, playerPaliyekkaraOpenFrac)
        nattika.step(dt, playerPos, playerHeadingRad, playerNattikaLane, playerNattikaOpenFrac)
        stepMuthuvaraLc(dt, playerPos, muthuvaraLcPhase, muthuvaraLcBarrierClosedFrac)

        // Generate contextual congestion status banner when player is near an active traffic block
        val dPal = hypot(playerPos.x - paliyekkara.centerX, playerPos.z - paliyekkara.centerZ)
        if (dPal < 135f && paliyekkara.queuedCount > 0) {
            return "Paliyekkara Toll Plaza • Heavy FASTag Queue (${paliyekkara.queuedCount} vehicles waiting across Lanes 2–11)"
        }
        val dNat = hypot(playerPos.x - nattika.centerX, playerPos.z - nattika.centerZ)
        if (dNat < 135f && nattika.queuedCount > 0) {
            return "Nattika NH 66 Toll Plaza • FASTag Traffic Queue (${nattika.queuedCount} vehicles in Lanes 2–9)"
        }
        val dLc = hypot(playerPos.x - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X, playerPos.z - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z)
        if (dLc < 125f && muthuvaraQueuedCount > 0 && muthuvaraLcPhase != MuthuvaraLcPhase.STATE_OPEN) {
            return "Muthuvara LC-14 Railway Block • $muthuvaraQueuedCount Vehicles Queued Behind Closed Gate"
        }
        return null
    }

    fun clearVehiclesNear(pos: Vec3, radiusM: Float) {
        if (!initialized) return
        paliyekkara.clearVehiclesNear(pos, radiusM)
        nattika.clearVehiclesNear(pos, radiusM)
        if (abs(pos.x - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X) < 28f &&
            pos.z in (LC_Z_MIN - radiusM)..(LC_Z_MAX + radiusM)
        ) {
            for (dirList in arrayOf(lcNorthbound, lcSouthbound)) {
                for (i in 0 until dirList.size) {
                    val v = dirList[i]
                    if (hypot(v.wx - pos.x, v.wz - pos.z) < radiusM) {
                        val sPlayer = if (v.northbound) pos.z - LC_Z_MIN else LC_Z_MAX - pos.z
                        val candidateBack = sPlayer - 15.5f - v.halfZ
                        val newS = if (candidateBack >= 4.0f) {
                            candidateBack
                        } else {
                            (sPlayer + 18.0f + v.halfZ).coerceAtMost(LC_CORRIDOR_LEN - 6.0f)
                        }
                        v.sPos = newS
                        v.speedMps = 0f
                        v.wz = if (v.northbound) LC_Z_MIN + v.sPos else LC_Z_MAX - v.sPos
                    }
                }
            }
        }
    }

    private fun stepMuthuvaraLc(
        dt: Float,
        playerPos: Vec3,
        lcPhase: MuthuvaraLcPhase,
        lcBarrierClosedFrac: Float
    ) {
        val gateBlocked = lcPhase == MuthuvaraLcPhase.STATE_CLOSING ||
            lcPhase == MuthuvaraLcPhase.STATE_LOCKED ||
            lcPhase == MuthuvaraLcPhase.STATE_TRAIN_PASSING ||
            lcBarrierClosedFrac > 0.22f

        val justReopened = prevLcBlocked && !gateBlocked
        prevLcBlocked = gateBlocked

        var qCount = 0
        qCount += stepLcDirection(dt, lcNorthbound, northbound = true, gateBlocked, justReopened, playerPos)
        qCount += stepLcDirection(dt, lcSouthbound, northbound = false, gateBlocked, justReopened, playerPos)
        muthuvaraQueuedCount = qCount
    }

    private fun stepLcDirection(
        dt: Float,
        list: ArrayList<LcVehicle>,
        northbound: Boolean,
        gateBlocked: Boolean,
        justReopened: Boolean,
        playerPos: Vec3
    ): Int {
        // Sort front-most first (descending sPos)
        for (i in 1 until list.size) {
            val key = list[i]
            var j = i - 1
            while (j >= 0 && list[j].sPos < key.sPos) {
                list[j + 1] = list[j]
                j--
            }
            list[j + 1] = key
        }

        // Exact white stop-line progress along the 250m SH 69 straight segment:
        // LC center Z = 610.0; gates at 610 ± 6.2; white stop lines at 610 ± 8.8 (601.2 South, 618.8 North)
        // Northbound: s = z - 485 -> stop line s = 601.2 - 485 = 116.2m
        // Southbound: s = 735 - z -> stop line s = 735 - 618.8 = 116.2m
        val stopLineS = 116.2f
        val laneX = Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X + (if (northbound) -2.65f else +2.65f)

        var playerS = Float.NaN
        if (abs(playerPos.x - laneX) < 2.6f && playerPos.z in (LC_Z_MIN - 8f)..(LC_Z_MAX + 8f)) {
            playerS = if (northbound) playerPos.z - LC_Z_MIN else LC_Z_MAX - playerPos.z
        }

        // Assign staggered reaction delays when gate reopens so queued vehicles launch organically one by one!
        if (justReopened) {
            var qRank = 0
            for (i in 0 until list.size) {
                val v = list[i]
                if (v.sPos + v.halfZ <= stopLineS + 2.5f && v.speedMps < 1.5f) {
                    v.releaseDelaySec = 0.25f + qRank * 0.42f
                    qRank++
                }
            }
        }

        var queued = 0
        for (i in 0 until list.size) {
            val v = list[i]
            val spec = TrafficVehicleCatalog.specFor(v.modelIndex)
            val frontS = v.sPos + v.halfZ

            if (v.releaseDelaySec > 0f) {
                v.releaseDelaySec = max(0f, v.releaseDelaySec - dt)
            }

            var desiredSpeed = if (v.releaseDelaySec > 0f) 0f else v.maxSpeedMps
            var mustStopDist = Float.POSITIVE_INFINITY
            var leadSpeed = desiredSpeed

            // 1. Railway gate stop line (only stop vehicles that have NOT already entered the track apron!)
            if (gateBlocked && frontS <= stopLineS + 1.4f) {
                val gap = max(0f, stopLineS - frontS)
                mustStopDist = min(mustStopDist, gap)
                leadSpeed = 0f
            }

            // 2. Vehicle directly ahead in the same lane
            if (i > 0) {
                val lead = list[i - 1]
                val gap = (lead.sPos - lead.halfZ) - frontS - 1.95f
                if (gap < mustStopDist) {
                    mustStopDist = max(0f, gap)
                    leadSpeed = lead.speedMps
                }
            }

            // 3. Player bus ahead in the same lane
            if (!playerS.isNaN()) {
                val gapToPlayer = (playerS - 6.0f) - frontS - 2.2f
                if (gapToPlayer > -3.0f && gapToPlayer < 30.0f && gapToPlayer < mustStopDist) {
                    mustStopDist = max(0f, gapToPlayer)
                    leadSpeed = 0f
                }
            }

            if (mustStopDist < 40f) {
                val safeV = kotlin.math.sqrt(max(0f, 2f * (spec.decelMps2 * 0.56f) * mustStopDist))
                desiredSpeed = min(desiredSpeed, max(leadSpeed * 0.95f, safeV))
                if (mustStopDist < 0.45f && leadSpeed < 0.6f) desiredSpeed = 0f
            }

            val prevSpeed = v.speedMps
            if (v.speedMps < desiredSpeed) {
                v.speedMps = min(desiredSpeed, v.speedMps + spec.accelMps2 * dt)
            } else {
                val brakeRate = if (mustStopDist < 3.5f) spec.decelMps2 * 1.45f else spec.decelMps2
                v.speedMps = max(desiredSpeed, v.speedMps - brakeRate * dt)
            }
            if (v.speedMps < 0.08f && desiredSpeed <= 0.08f) v.speedMps = 0f

            v.sPos += v.speedMps * dt
            v.brakeGlow = if (v.speedMps < prevSpeed - 0.15f * dt || (v.speedMps < 0.8f && (gateBlocked || mustStopDist < 4f))) 1f else 0f
            v.isQueued = v.speedMps < 1.2f && frontS <= stopLineS + 2f
            if (v.isQueued) queued++

            // Wrap smoothly to feed continuous realistic SH 69 traffic into the crossing
            if (v.sPos > LC_CORRIDOR_LEN) {
                val tail = list.last()
                if (tail === v || (tail.sPos - tail.halfZ) > v.halfZ + 12.0f) {
                    v.sPos = 4.0f
                    v.speedMps = min(v.maxSpeedMps, 9.5f)
                    v.releaseDelaySec = 0f
                }
            }

            v.wx = laneX
            v.wz = if (northbound) LC_Z_MIN + v.sPos else LC_Z_MAX - v.sPos
        }
        return queued
    }

    fun appendToRenderPool(
        outPool: Array<TrafficRenderInstance>,
        startIdx: Int,
        simTimeSec: Float
    ): Int {
        var cursor = paliyekkara.appendToRenderPool(outPool, startIdx, simTimeSec)
        cursor = nattika.appendToRenderPool(outPool, cursor, simTimeSec)

        for (dirList in arrayOf(lcNorthbound, lcSouthbound)) {
            for (i in 0 until dirList.size) {
                if (cursor >= outPool.size) return cursor
                val v = dirList[i]
                val spec = TrafficVehicleCatalog.specFor(v.modelIndex)
                val inst = outPool[cursor++]
                inst.modelIndex = v.modelIndex
                inst.cx = v.wx
                inst.baseY = 0.06f
                inst.cz = v.wz
                inst.headingRad = if (v.northbound) 0f else PI.toFloat()
                inst.pitchRad = 0f
                inst.brakeGlow = v.brakeGlow
                inst.sirenPhase = if (spec.hasEmergencyBeacon) simTimeSec else 0f
                inst.halfX = v.halfX
                inst.halfZ = v.halfZ
                inst.speedMps = v.speedMps
                inst.label = spec.displayName
                inst.isQueued = v.isQueued
            }
        }
        return cursor
    }

    companion object {
        private const val LC_Z_MIN = 485.0f
        private const val LC_Z_MAX = 735.0f
        private const val LC_CORRIDOR_LEN = LC_Z_MAX - LC_Z_MIN // 250m dead-straight SH 69 approach!
    }
}
