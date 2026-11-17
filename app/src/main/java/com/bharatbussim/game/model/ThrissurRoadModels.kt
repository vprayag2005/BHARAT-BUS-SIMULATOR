package com.bharatbussim.game.model

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Section 1: Geographic Foundation & Simulation Coordinates (Thrissur District)
 * - Geographic CRS: WGS84 (EPSG:4326)
 * - Metric Projected CRS: UTM Zone 43N (EPSG:32643)
 * - District World Datum Anchor: Thrissur Swaraj Round (Thekkinkadu Maidan Center)
 *   - Coordinate: 10.5244° N, 76.2144° E
 *   - UTM 43N: X = 632883.20 m, Y = 1163650.18 m, Elevation = 12.00 m
 * - Mini-Scale Factor (CORRIDOR_SCALE = 0.20):
 *   - Inter-town highway corridors: 1:5 scale (20% distance compression)
 *   - Road cross-sections, vehicle physics, toll plazas, bridges, tunnels, and bus stands: Strictly 1:1 metric scale
 */
object ThrissurGeoConstants {
    const val GEOGRAPHIC_CRS = "EPSG:4326"
    const val PROJECTED_CRS = "EPSG:32643"
    const val ORIGIN_NAME = "Thrissur Swaraj Round (Thekkinkadu Maidan Center)"
    const val ORIGIN_LAT_DEG = 10.5244
    const val ORIGIN_LON_DEG = 76.2144
    const val ORIGIN_UTM_X_M = 632883.20
    const val ORIGIN_UTM_Y_M = 1163650.18
    const val ORIGIN_ELEVATION_M = 12.00f
    const val TEMPLE_BASE_ELEVATION_M = 16.50f
    const val CORRIDOR_SCALE = 0.20f
    const val CROSS_SECTION_SCALE = 1.00f
}

/**
 * Zone 1 Manikandanal Traffic Signal (Round South) 3-aspect LED cycle:
 * Cycle: Red (30s) -> Green (25s) -> Amber (4s) (Total 59.0s period)
 */
enum class TrafficSignalAspect(val label: String, val durationSec: Float) {
    Red("RED", 30f),
    Green("GREEN", 25f),
    Amber("AMBER", 4f);

    companion object {
        const val TOTAL_CYCLE_SEC = 59.0f

        fun fromCycleTime(cycleSec: Float): Pair<TrafficSignalAspect, Int> {
            val t = ((cycleSec % TOTAL_CYCLE_SEC) + TOTAL_CYCLE_SEC) % TOTAL_CYCLE_SEC
            return when {
                t < 30f -> Pair(Red, (30f - t).toInt().coerceAtLeast(1))
                t < 55f -> Pair(Green, (55f - t).toInt().coerceAtLeast(1))
                else -> Pair(Amber, (59f - t).toInt().coerceAtLeast(1))
            }
        }
    }
}

@Immutable
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dotXZ(o: Vec3): Float = x * o.x + z * o.z
    fun lengthXZ(): Float = hypot(x, z)
    fun normalizedXZ(): Vec3 {
        val len = lengthXZ()
        return if (len < 1e-4f) Vec3(0f, 0f, 1f) else Vec3(x / len, 0f, z / len)
    }
}

@Immutable
data class RoadFrame(
    val pos: Vec3,
    val tangent: Vec3,
    val right: Vec3,
    val cumulativeDist: Float,
    val halfWidthM: Float = 8.0f,
    val junctionClearanceM: Float,
    val leftEdgeCovered: Boolean = false,
    val rightEdgeCovered: Boolean = false,
    val centerCovered: Boolean = false
)

@Immutable
data class ThrissurOsmRoad(
    val id: String,
    val name: String,
    val roadType: String,
    val widthM: Float,
    val lanes: Int,
    val divided: Boolean,
    val closedLoop: Boolean,
    val oneWayClockwise: Boolean = false,
    val realDistanceM: Float,
    val stepNames: List<String>,
    val points: List<Vec3>,
    val frames: List<RoadFrame>,
    val blenderVertCount: Int,
    val blenderQuadCount: Int
)

/**
 * Exact topological and geometric specification of a T-junction or Roundabout branch joint,
 * ensuring the trunk road and branch road share exact vertex anchor frames (tBefore, tAfter, bThroat)
 * for Pass 1 (Shoulders), Pass 2 (Asphalt Bellmouth), and Pass 3 (Curved White Edge Fillets)
 * with 0.000m gaps.
 */
@Immutable
data class RoadJunctionBellmouth(
    val branchRoadId: String,
    val isBranchStart: Boolean,
    val centerPos: Vec3,
    val tBefore: RoadFrame,
    val tCenter: RoadFrame,
    val tAfter: RoadFrame,
    val trunkBeforeSideSign: Float,
    val trunkCenterSideSign: Float,
    val trunkAfterSideSign: Float,
    val bTip: RoadFrame,
    val bThroat: RoadFrame,
    val bBeforeSign: Float,
    val bAfterSign: Float
)

enum class TransmissionGear(val code: String) {
    Park("P"),
    Reverse("R"),
    Neutral("N"),
    Drive("D")
}

enum class CameraViewMode(val label: String) {
    Chase("CAM 1/3 • CHASE"),
    Cockpit("CAM 2/3 • COCKPIT"),
    Orbit("CAM 3/3 • 360 ORBIT")
}

enum class WiperSpeedMode(val label: String, val speedRadPerSec: Float) {
    Off("OFF", 0f),
    Low("LO", 2.0f),
    Mid("MID", 3.5f),
    High("HI", 5.4f)
}

enum class HeadlightBeamMode(val label: String) {
    Off("OFF"),
    Low("LO"),
    High("HI")
}

object ThrissurRoadRepository {

    // End-to-end highway continuation pairs (Road A end welds directly into Road B start)
    private val continuationPairs = listOf(
        Pair("ROAD_SWARAJ_TO_WEST_FORT", "ROAD_WEST_FORT_TO_KANJANI"),
        Pair("ROAD_WEST_FORT_TO_KANJANI", "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD"),
        Pair("ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD", "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR"),
        Pair("ROAD_SWARAJ_TO_MANNUTHY", "ROAD_MANNUTHY_TO_PEECHI_DAM")
    )

    private fun isContinuationPair(idA: String, idB: String): Boolean =
        continuationPairs.any { (a, b) -> (a == idA && b == idB) || (a == idB && b == idA) }

    private fun roadPriority(id: String): Int = when (id) {
        "ROAD_SWARAJ_ROUND" -> 0
        "ROAD_SAKTHAN_TO_SWARAJ" -> 1
        "ROAD_SWARAJ_TO_MANNUTHY" -> 1
        "ROAD_MANNUTHY_TO_PEECHI_DAM" -> 1
        "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" -> 2
        "ROAD_SWARAJ_TO_VADAKKE" -> 2
        "ROAD_SWARAJ_TO_WEST_FORT" -> 2
        "ROAD_WEST_FORT_TO_KANJANI" -> 2
        "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD" -> 2
        "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" -> 2
        "ROAD_SAKTHAN_STAND_ENTRY" -> 3
        "ROAD_SAKTHAN_TO_OLLUR" -> 3
        "ROAD_PUTHANPALLI_LINK" -> 3
        "ROAD_SH61_TRIPRAYAR_TO_KANJANI" -> 3
        "ROAD_STATION_ROAD" -> 4
        else -> 4
    }

    @Volatile
    private var cachedBellmouths: List<RoadJunctionBellmouth> = emptyList()

    fun getJunctionBellmouths(): List<RoadJunctionBellmouth> = cachedBellmouths

    fun loadFromAssets(context: Context): List<ThrissurOsmRoad> {
        val jsonText = context.assets.open("thrissur_osm_roads.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonText)
        val roadsArray = root.getJSONArray("roads")

        data class MutableRoadSpec(
            val idx: Int,
            val id: String,
            val name: String,
            val roadType: String,
            val widthM: Float,
            val lanes: Int,
            val divided: Boolean,
            val closedLoop: Boolean,
            val oneWayClockwise: Boolean,
            val realDist: Float,
            val steps: List<String>,
            var smoothedPts: MutableList<Vec3>,
            var baseFrames: MutableList<RoadFrame>,
            val priority: Int
        )

        val specs = ArrayList<MutableRoadSpec>(roadsArray.length())

        for (i in 0 until roadsArray.length()) {
            val obj = roadsArray.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val roadType = obj.optString("road_type", "city_arterial")
            val defaultWidth = when (id) {
                "ROAD_SWARAJ_ROUND" -> 14.0
                "ROAD_SAKTHAN_TO_SWARAJ" -> 12.0
                "ROAD_SWARAJ_TO_VADAKKE", "ROAD_SWARAJ_TO_WEST_FORT" -> 11.0
                else -> 10.0
            }
            val widthM = obj.optDouble("width_m", defaultWidth).toFloat()
            val lanes = obj.optInt("lanes", 2)
            val divided = obj.optBoolean("divided", false)
            val closedLoop = obj.optBoolean("closed_loop", false)
            val oneWayClockwise = obj.optBoolean("one_way_clockwise", id == "ROAD_SWARAJ_ROUND")
            val realDist = obj.optDouble("real_distance_m", 1500.0).toFloat()

            val stepsArr = obj.optJSONArray("step_names")
            val steps = ArrayList<String>()
            if (stepsArr != null) {
                for (s in 0 until stepsArr.length()) {
                    steps.add(stepsArr.getString(s))
                }
            }

            val ptsArr = obj.getJSONArray("points_unity")
            val rawPts = ArrayList<Vec3>(ptsArr.length())
            for (p in 0 until ptsArr.length()) {
                val coord = ptsArr.getJSONArray(p)
                rawPts.add(
                    Vec3(
                        coord.getDouble(0).toFloat(),
                        coord.getDouble(1).toFloat(),
                        coord.getDouble(2).toFloat()
                    )
                )
            }

            val engineered = engineerHighwaySpline(rawPts, closedLoop).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = i,
                    id = id,
                    name = name,
                    roadType = roadType,
                    widthM = widthM,
                    lanes = lanes,
                    divided = divided,
                    closedLoop = closedLoop,
                    oneWayClockwise = oneWayClockwise,
                    realDist = realDist,
                    steps = steps,
                    smoothedPts = engineered,
                    baseFrames = mutableListOf(),
                    priority = roadPriority(id)
                )
            )
        }

        // Ensure Thrissur Railway Station (TCR) has a connected drivable arterial road (ROAD_STATION_ROAD)
        // linking M.O. Road / Sakthan Entry Slip Road (X = 7.0m, Z = -121.0m) directly past the
        // Thrissur Central Railway Station Forecourt (X = -18.65m, Z = -115m..-60m) to Swaraj Round South-West (-18.653m, -30.398m)
        if (specs.none { it.id == "ROAD_STATION_ROAD" }) {
            val stationRawPts = listOf(
                Vec3(7.0f, 0.05f, -121.0f),
                Vec3(1.0f, 0.05f, -121.0f),
                Vec3(-5.5f, 0.05f, -121.0f),
                Vec3(-12.0f, 0.05f, -120.2f),
                Vec3(-16.8f, 0.05f, -117.0f),
                Vec3(-18.65f, 0.05f, -111.5f),
                Vec3(-18.65f, 0.05f, -105.0f),
                Vec3(-18.65f, 0.05f, -97.0f),
                Vec3(-18.65f, 0.05f, -88.0f),
                Vec3(-18.65f, 0.05f, -78.0f),
                Vec3(-18.65f, 0.05f, -68.0f),
                Vec3(-18.65f, 0.05f, -58.0f),
                Vec3(-18.65f, 0.05f, -48.0f),
                Vec3(-18.65f, 0.05f, -38.5f),
                Vec3(-18.653f, 0.05f, -30.398f)
            )
            val stationEngineered = engineerHighwaySpline(stationRawPts, closed = false).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_STATION_ROAD",
                    name = "M.O. Road Link - Thrissur Railway Station (TCR) - Swaraj Round West",
                    roadType = "city_arterial",
                    widthM = 10.5f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 640.0f,
                    steps = listOf(
                        "Railway Station Link Road",
                        "Thrissur Central Railway Station (TCR)",
                        "Diwanji Corner",
                        "Kuruppam Road (Swaraj Round South-West)"
                    ),
                    smoothedPts = stationEngineered,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_STATION_ROAD")
                )
            )
        }

        val specById = specs.associateBy { it.id }

        // STEP 1: Smooth & lock all 4 end-to-end continuation joints so their splines have C1 continuity across the joint
        for ((idA, idB) in continuationPairs) {
            val rA = specById[idA] ?: continue
            val rB = specById[idB] ?: continue
            val ptsA = rA.smoothedPts
            val ptsB = rB.smoothedPts
            if (ptsA.size >= 4 && ptsB.size >= 4) {
                val pPrev = ptsA[ptsA.size - 2]
                val pNext = ptsB[1]
                val midJoint = Vec3(
                    (ptsA.last().x + ptsB.first().x) * 0.35f + (pPrev.x + pNext.x) * 0.15f,
                    ptsA.last().y,
                    (ptsA.last().z + ptsB.first().z) * 0.35f + (pPrev.z + pNext.z) * 0.15f
                )
                ptsA[ptsA.size - 1] = midJoint
                ptsB[0] = midJoint
                // Also snap NH544 start if it shares the Mannuthy joint
                if (idA == "ROAD_SWARAJ_TO_MANNUTHY") {
                    specById["ROAD_NH544_MANNUTHY_TO_PALIYEKKARA"]?.let { nh ->
                        if (nh.smoothedPts.isNotEmpty()) {
                            val d = midJoint - nh.smoothedPts[0]
                            for (k in 0 until min(5, nh.smoothedPts.size)) {
                                val w = (1f - k * 0.22f).coerceAtLeast(0f)
                                nh.smoothedPts[k] = nh.smoothedPts[k] + d * w
                            }
                        }
                    }
                }
            }
        }

        // STEP 2: Snap every T-junction / Roundabout branch road endpoint directly onto the post-smoothed trunk road spline (0.000m gap)
        for (spec in specs.sortedBy { it.priority }) {
            if (spec.closedLoop) continue
            val pts = spec.smoothedPts
            if (pts.size < 5) continue

            // Snap START endpoint if it joins a higher-priority trunk road
            val skipStartSnap = spec.id == "ROAD_SAKTHAN_TO_SWARAJ" ||
                continuationPairs.any { it.second == spec.id }
            if (!skipStartSnap) {
                val p0 = pts.first()
                var bestDist = Float.MAX_VALUE
                var bestProj = p0
                for (other in specs) {
                    if (other.id == spec.id || other.priority > spec.priority) continue
                    val (d, q) = closestPointOnPolylineXZ(p0, other.smoothedPts)
                    if (d < bestDist) {
                        bestDist = d
                        bestProj = q
                    }
                }
                if (bestDist < 18.0f) {
                    val delta = Vec3(bestProj.x - p0.x, 0f, bestProj.z - p0.z)
                    val falloff = floatArrayOf(1.0f, 0.76f, 0.50f, 0.26f, 0.10f)
                    for (k in 0 until min(falloff.size, pts.size - 1)) {
                        pts[k] = pts[k] + delta * falloff[k]
                    }
                }
            }

            // Snap END endpoint if it joins a higher-priority trunk road
            val skipEndSnap = spec.id == "ROAD_SAKTHAN_STAND_ENTRY" ||
                continuationPairs.any { it.first == spec.id }
            if (!skipEndSnap) {
                val pLast = pts.last()
                var bestDist = Float.MAX_VALUE
                var bestProj = pLast
                for (other in specs) {
                    if (other.id == spec.id || other.priority > spec.priority) continue
                    val (d, q) = closestPointOnPolylineXZ(pLast, other.smoothedPts)
                    if (d < bestDist) {
                        bestDist = d
                        bestProj = q
                    }
                }
                if (bestDist < 18.0f) {
                    val delta = Vec3(bestProj.x - pLast.x, 0f, bestProj.z - pLast.z)
                    val falloff = floatArrayOf(1.0f, 0.76f, 0.50f, 0.26f, 0.10f)
                    val n = pts.size
                    for (k in 0 until min(falloff.size, n - 1)) {
                        pts[n - 1 - k] = pts[n - 1 - k] + delta * falloff[k]
                    }
                }
            }
        }

        // STEP 3: Compute base frames (with curve extra widening & Sakthan gate exact width fit)
        for (spec in specs) {
            spec.baseFrames = computeBaseFrames(spec.id, spec.smoothedPts, spec.closedLoop, spec.widthM).toMutableList()
        }

        // STEP 4: Weld all 4 continuation joints at the frame level so pos, tangent, right, and halfWidthM are 100% identical across the joint
        for ((idA, idB) in continuationPairs) {
            val rA = specById[idA] ?: continue
            val rB = specById[idB] ?: continue
            val fA = rA.baseFrames
            val fB = rB.baseFrames
            if (fA.size < 6 || fB.size < 6) continue

            val sharedPos = fA.last().pos
            val sharedTangent = (fA.last().tangent + fB.first().tangent).normalizedXZ()
            val sharedRight = Vec3(sharedTangent.z, 0f, -sharedTangent.x)
            val sharedHalfW = (fA.last().halfWidthM + fB.first().halfWidthM) * 0.5f

            val blendSteps = 5
            for (k in 0 until blendSteps) {
                val t = 1f - (k.toFloat() / blendSteps.toFloat())
                // Blend end of road A
                val idxA = fA.size - 1 - k
                val oldA = fA[idxA]
                val tangA = if (k == 0) sharedTangent else (oldA.tangent * (1f - t) + sharedTangent * t).normalizedXZ()
                val rightA = if (k == 0) sharedRight else Vec3(tangA.z, 0f, -tangA.x)
                val hwA = if (k == 0) sharedHalfW else oldA.halfWidthM * (1f - t) + sharedHalfW * t
                fA[idxA] = oldA.copy(
                    pos = if (k == 0) sharedPos else oldA.pos,
                    tangent = tangA,
                    right = rightA,
                    halfWidthM = hwA
                )

                // Blend start of road B
                val idxB = k
                val oldB = fB[idxB]
                val tangB = if (k == 0) sharedTangent else (oldB.tangent * (1f - t) + sharedTangent * t).normalizedXZ()
                val rightB = if (k == 0) sharedRight else Vec3(tangB.z, 0f, -tangB.x)
                val hwB = if (k == 0) sharedHalfW else oldB.halfWidthM * (1f - t) + sharedHalfW * t
                fB[idxB] = oldB.copy(
                    pos = if (k == 0) sharedPos else oldB.pos,
                    tangent = tangB,
                    right = rightB,
                    halfWidthM = hwB
                )
            }
        }

        // STEP 5: Build exact T-Junction & Roundabout Bellmouths and lock exact frame-index coverage masks
        val leftCoveredMask = HashMap<String, BooleanArray>()
        val rightCoveredMask = HashMap<String, BooleanArray>()
        val centerCoveredMask = HashMap<String, BooleanArray>()
        for (spec in specs) {
            val n = spec.baseFrames.size
            leftCoveredMask[spec.id] = BooleanArray(n)
            rightCoveredMask[spec.id] = BooleanArray(n)
            centerCoveredMask[spec.id] = BooleanArray(n)
        }

        val bellmouths = ArrayList<RoadJunctionBellmouth>(16)

        fun markRoadSideRange(roadId: String, idx0: Int, idx1: Int, sideSign: Float, closedLoop: Boolean) {
            val mask = if (sideSign < 0f) leftCoveredMask[roadId] else rightCoveredMask[roadId]
            if (mask == null || mask.isEmpty()) return
            val n = mask.size
            if (!closedLoop || idx0 <= idx1) {
                for (i in max(0, idx0)..min(n - 1, idx1)) {
                    mask[i] = true
                }
            } else {
                for (i in idx0 until n) mask[i] = true
                for (i in 0..idx1) mask[i] = true
            }
        }

        fun stepAlongFrames(frames: List<RoadFrame>, startIdx: Int, dir: Int, targetDistM: Float, closedLoop: Boolean): Int {
            val n = frames.size
            var curr = startIdx
            var traveled = 0f
            var steps = 0
            while (steps < n - 1 && traveled < targetDistM) {
                val next = if (closedLoop) {
                    (curr + dir + n) % n
                } else {
                    (curr + dir).coerceIn(0, n - 1)
                }
                if (next == curr) break
                traveled += (frames[next].pos - frames[curr].pos).lengthXZ()
                curr = next
                steps++
            }
            return curr
        }

        for (branch in specs) {
            if (branch.closedLoop) continue
            val bFrames = branch.baseFrames
            if (bFrames.size < 8) continue

            for (isStart in booleanArrayOf(true, false)) {
                if (isStart && (branch.id == "ROAD_SAKTHAN_TO_SWARAJ" || continuationPairs.any { it.second == branch.id })) continue
                if (!isStart && (branch.id == "ROAD_SAKTHAN_STAND_ENTRY" || continuationPairs.any { it.first == branch.id })) continue

                val bTipIdx = if (isStart) 0 else bFrames.size - 1
                val bTip = bFrames[bTipIdx]

                // Find closest higher-priority trunk road
                var bestTrunk: MutableRoadSpec? = null
                var bestTCenterIdx = 0
                var bestDist = Float.MAX_VALUE
                for (candidate in specs) {
                    if (candidate.id == branch.id || candidate.priority >= branch.priority) continue
                    for (ti in candidate.baseFrames.indices) {
                        val d = (candidate.baseFrames[ti].pos - bTip.pos).lengthXZ()
                        if (d < bestDist) {
                            bestDist = d
                            bestTrunk = candidate
                            bestTCenterIdx = ti
                        }
                    }
                }

                val trunk = bestTrunk
                if (trunk == null || bestDist > 6.0f) continue

                val tFrames = trunk.baseFrames
                val tCenter = tFrames[bestTCenterIdx]

                // Walk inward along branch road until cleanly outside trunk road's carriageway
                val stepDir = if (isStart) 1 else -1
                val minThroatDistFromTrunkCenter = tCenter.halfWidthM + 3.2f
                var bThroatIdx = bTipIdx
                var stepsTaken = 0
                while (stepsTaken < 10) {
                    val candIdx = (bThroatIdx + stepDir).coerceIn(0, bFrames.size - 1)
                    if (candIdx == bThroatIdx) break
                    bThroatIdx = candIdx
                    stepsTaken++
                    val dToTrunk = closestPointOnFramesXZ(bFrames[bThroatIdx].pos, tFrames)
                    if (dToTrunk >= minThroatDistFromTrunkCenter && stepsTaken >= 2) break
                }
                val bThroat = bFrames[bThroatIdx]

                // Taper the branch road's internal frames (strictly inside the trunk road) so they never poke out the non-branching back side of the trunk road
                val iMin = min(bTipIdx, bThroatIdx)
                val iMax = max(bTipIdx, bThroatIdx)
                val centerMaskB = centerCoveredMask[branch.id]!!
                val leftMaskB = leftCoveredMask[branch.id]!!
                val rightMaskB = rightCoveredMask[branch.id]!!
                for (bi in iMin..iMax) {
                    if (bi != bThroatIdx) {
                        leftMaskB[bi] = true
                        rightMaskB[bi] = true
                    }
                    val dToTrunk = closestPointOnFramesXZ(bFrames[bi].pos, tFrames)
                    if (dToTrunk < tCenter.halfWidthM - 0.15f) {
                        centerMaskB[bi] = true
                    }
                    if (dToTrunk < tCenter.halfWidthM * 0.85f) {
                        val safeHw = min(bFrames[bi].halfWidthM, tCenter.halfWidthM * 0.65f)
                        bFrames[bi] = bFrames[bi].copy(halfWidthM = safeHw)
                    }
                }

                // Determine which side of the trunk road the branch road exits toward
                val branchDir = (bThroat.pos - tCenter.pos).normalizedXZ()
                val trunkCenterSideSign = if (trunk.id == "ROAD_SWARAJ_ROUND") {
                    -1f // Outer circle of clockwise Swaraj Round is always -right
                } else {
                    if (branchDir.dotXZ(tCenter.right) >= 0f) 1f else -1f
                }

                val flareDistAlongTrunk = bThroat.halfWidthM + 6.5f

                val tBeforeFrame: RoadFrame
                val tAfterFrame: RoadFrame
                val beforeSideSign: Float
                val afterSideSign: Float

                if (branch.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" && isStart) {
                    val westTrunk = specById["ROAD_SWARAJ_TO_MANNUTHY"]!!
                    val eastTrunk = specById["ROAD_MANNUTHY_TO_PEECHI_DAM"]!!
                    val wFrames = westTrunk.baseFrames
                    val eFrames = eastTrunk.baseFrames
                    val wBeforeIdx = stepAlongFrames(wFrames, wFrames.size - 1, -1, flareDistAlongTrunk, false)
                    val eAfterIdx = stepAlongFrames(eFrames, 0, +1, flareDistAlongTrunk, false)
                    tBeforeFrame = wFrames[wBeforeIdx]
                    tAfterFrame = eFrames[eAfterIdx]
                    beforeSideSign = 1f
                    afterSideSign = 1f
                    markRoadSideRange(westTrunk.id, wBeforeIdx, wFrames.size - 1, beforeSideSign, false)
                    markRoadSideRange(eastTrunk.id, 0, eAfterIdx, afterSideSign, false)
                } else {
                    val tBeforeIdx = stepAlongFrames(tFrames, bestTCenterIdx, -1, flareDistAlongTrunk, trunk.closedLoop)
                    val tAfterIdx = stepAlongFrames(tFrames, bestTCenterIdx, +1, flareDistAlongTrunk, trunk.closedLoop)
                    tBeforeFrame = tFrames[tBeforeIdx]
                    tAfterFrame = tFrames[tAfterIdx]
                    beforeSideSign = trunkCenterSideSign
                    afterSideSign = trunkCenterSideSign

                    markRoadSideRange(trunk.id, tBeforeIdx, tAfterIdx, trunkCenterSideSign, trunk.closedLoop)
                }

                val alongTrunkVec = (tAfterFrame.pos - tBeforeFrame.pos).normalizedXZ()
                val bRightDotTrunk = bThroat.right.dotXZ(alongTrunkVec)
                val bBeforeSign = if (bRightDotTrunk >= 0f) -1f else 1f
                val bAfterSign = -bBeforeSign

                bellmouths.add(
                    RoadJunctionBellmouth(
                        branchRoadId = branch.id,
                        isBranchStart = isStart,
                        centerPos = tCenter.pos,
                        tBefore = tBeforeFrame,
                        tCenter = tCenter,
                        tAfter = tAfterFrame,
                        trunkBeforeSideSign = beforeSideSign,
                        trunkCenterSideSign = trunkCenterSideSign,
                        trunkAfterSideSign = afterSideSign,
                        bTip = bFrames[bTipIdx],
                        bThroat = bThroat,
                        bBeforeSign = bBeforeSign,
                        bAfterSign = bAfterSign
                    )
                )
            }
        }

        cachedBellmouths = bellmouths

        // STEP 6: Assemble final ThrissurOsmRoad objects with exact coverage flags
        // Also run a geometric safety sweep so any outer edge of Swaraj Round or a trunk road that crosses a branch road is 100% covered
        for (spec in specs) {
            val lMask = leftCoveredMask[spec.id]!!
            val rMask = rightCoveredMask[spec.id]!!
            for (i in spec.baseFrames.indices) {
                val f = spec.baseFrames[i]
                val leftEdgePt = f.pos - f.right * (f.halfWidthM + 0.5f)
                val rightEdgePt = f.pos + f.right * (f.halfWidthM + 0.5f)
                for (other in specs) {
                    if (other.id == spec.id || other.priority <= spec.priority) continue
                    for (of in other.baseFrames) {
                        val clearR = of.halfWidthM + 3.8f
                        if ((leftEdgePt - of.pos).lengthXZ() < clearR) {
                            lMask[i] = true
                        }
                        if (spec.id != "ROAD_SWARAJ_ROUND" && (rightEdgePt - of.pos).lengthXZ() < clearR) {
                            rMask[i] = true
                        }
                    }
                }
            }
        }

        val result = ArrayList<ThrissurOsmRoad>(specs.size)
        for (spec in specs) {
            val lMask = leftCoveredMask[spec.id]!!
            val rMask = rightCoveredMask[spec.id]!!
            val cMask = centerCoveredMask[spec.id]!!
            val finalFrames = ArrayList<RoadFrame>(spec.baseFrames.size)
            for (i in spec.baseFrames.indices) {
                val f = spec.baseFrames[i]
                val isStationForecourtFrontage = spec.id == "ROAD_STATION_ROAD" &&
                    f.pos.x < -16.5f &&
                    f.pos.z in -152.0f..-108.0f
                finalFrames.add(
                    f.copy(
                        leftEdgeCovered = lMask[i] || isStationForecourtFrontage,
                        rightEdgeCovered = if (spec.id == "ROAD_SWARAJ_ROUND") false else rMask[i],
                        centerCovered = if (spec.id == "ROAD_SWARAJ_ROUND") false else cMask[i]
                    )
                )
            }

            val nFrames = finalFrames.size
            val stripCount = if (spec.divided) 11 else 10
            val estQuads = (nFrames - 1).coerceAtLeast(1) * stripCount
            val estVerts = estQuads * 2

            result.add(
                ThrissurOsmRoad(
                    id = spec.id,
                    name = spec.name,
                    roadType = spec.roadType,
                    widthM = spec.widthM,
                    lanes = spec.lanes,
                    divided = spec.divided,
                    closedLoop = spec.closedLoop,
                    oneWayClockwise = spec.oneWayClockwise,
                    realDistanceM = spec.realDist,
                    stepNames = spec.steps,
                    points = spec.smoothedPts,
                    frames = finalFrames,
                    blenderVertCount = estVerts,
                    blenderQuadCount = estQuads
                )
            )
        }
        return result
    }

    private fun closestPointOnPolylineXZ(p: Vec3, poly: List<Vec3>): Pair<Float, Vec3> {
        var bestDist = Float.MAX_VALUE
        var bestPt = p
        for (i in 0 until poly.size - 1) {
            val a = poly[i]
            val b = poly[i + 1]
            val vx = b.x - a.x
            val vz = b.z - a.z
            val wx = p.x - a.x
            val wz = p.z - a.z
            val lenSq = vx * vx + vz * vz
            val t = if (lenSq < 1e-5f) 0f else ((wx * vx + wz * vz) / lenSq).coerceIn(0f, 1f)
            val qx = a.x + t * vx
            val qz = a.z + t * vz
            val d = hypot(p.x - qx, p.z - qz)
            if (d < bestDist) {
                bestDist = d
                bestPt = Vec3(qx, a.y, qz)
            }
        }
        return Pair(bestDist, bestPt)
    }

    private fun closestPointOnFramesXZ(p: Vec3, frames: List<RoadFrame>): Float {
        var bestDist = Float.MAX_VALUE
        for (i in 0 until frames.size - 1) {
            val a = frames[i].pos
            val b = frames[i + 1].pos
            val vx = b.x - a.x
            val vz = b.z - a.z
            val wx = p.x - a.x
            val wz = p.z - a.z
            val lenSq = vx * vx + vz * vz
            val t = if (lenSq < 1e-5f) 0f else ((wx * vx + wz * vz) / lenSq).coerceIn(0f, 1f)
            val qx = a.x + t * vx
            val qz = a.z + t * vz
            val d = hypot(p.x - qx, p.z - qz)
            if (d < bestDist) bestDist = d
        }
        return bestDist
    }

    /**
     * Ensures C1-smooth highway curvature with dense frame subdivision near road endpoints and junctions.
     */
    private fun engineerHighwaySpline(pts: List<Vec3>, closed: Boolean): List<Vec3> {
        if (pts.size < 3) return pts

        val filtered = ArrayList<Vec3>(pts.size)
        filtered.add(pts.first())
        for (i in 1 until pts.size - 1) {
            val p = pts[i]
            val prev = filtered.last()
            if ((p - prev).lengthXZ() < 4.0f) continue
            if (!closed && (p - pts.last()).lengthXZ() < 4.0f) continue
            filtered.add(p)
        }
        filtered.add(pts.last())

        var current = filtered.toList()
        repeat(4) {
            if (current.size <= 3) return@repeat
            val next = ArrayList<Vec3>(current.size)
            if (closed) {
                val base = if ((current.first() - current.last()).lengthXZ() < 1.0f) {
                    current.dropLast(1)
                } else current
                val m = base.size
                for (i in 0 until m) {
                    val pPrev = base[(i - 1 + m) % m]
                    val pCurr = base[i]
                    val pNext = base[(i + 1) % m]
                    next.add(
                        Vec3(
                            pCurr.x * 0.55f + (pPrev.x + pNext.x) * 0.225f,
                            pCurr.y,
                            pCurr.z * 0.55f + (pPrev.z + pNext.z) * 0.225f
                        )
                    )
                }
                if (next.isNotEmpty()) next.add(next.first())
            } else {
                next.add(current.first())
                for (i in 1 until current.size - 1) {
                    val pPrev = current[i - 1]
                    val pCurr = current[i]
                    val pNext = current[i + 1]
                    val v1 = (pCurr - pPrev).normalizedXZ()
                    val v2 = (pNext - pCurr).normalizedXZ()
                    val dot = v1.dotXZ(v2).coerceIn(-1f, 1f)
                    val angle = acos(dot)
                    val blend = if (angle > 0.18f) 0.45f else 0.25f
                    next.add(
                        Vec3(
                            pCurr.x * (1f - blend) + (pPrev.x + pNext.x) * (0.5f * blend),
                            pCurr.y,
                            pCurr.z * (1f - blend) + (pPrev.z + pNext.z) * (0.5f * blend)
                        )
                    )
                }
                next.add(current.last())
            }
            current = next
        }

        val chaikin = ArrayList<Vec3>(current.size * 3)
        if (!closed) {
            chaikin.add(current.first())
            for (i in 0 until current.size - 1) {
                val p0 = current[i]
                val p1 = current[i + 1]
                val nearEnd = i < 4 || i >= current.size - 5
                if (nearEnd) {
                    if (i > 0) chaikin.add(lerp(p0, p1, 0.20f))
                    chaikin.add(lerp(p0, p1, 0.50f))
                    if (i < current.size - 2) chaikin.add(lerp(p0, p1, 0.80f))
                } else {
                    if (i > 0) chaikin.add(lerp(p0, p1, 0.25f))
                    if (i < current.size - 2) chaikin.add(lerp(p0, p1, 0.75f))
                }
            }
            chaikin.add(current.last())
        } else {
            val trimmed = if ((current.first() - current.last()).lengthXZ() < 1.0f) {
                current.dropLast(1)
            } else current
            val m = trimmed.size
            for (i in 0 until m) {
                val p0 = trimmed[i]
                val p1 = trimmed[(i + 1) % m]
                chaikin.add(lerp(p0, p1, 0.25f))
                chaikin.add(lerp(p0, p1, 0.75f))
            }
            if (chaikin.isNotEmpty()) chaikin.add(chaikin.first())
        }
        return chaikin
    }

    private fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 =
        Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    private fun computeBaseFrames(
        roadId: String,
        pts: List<Vec3>,
        closed: Boolean,
        baseWidthM: Float
    ): List<RoadFrame> {
        val n = pts.size
        val baseHalfW = baseWidthM * 0.5f
        val rawTangents = ArrayList<Vec3>(n)
        for (i in 0 until n) {
            val tRaw = when {
                closed && i == n - 1 && n > 2 -> pts[1] - pts[i - 1]
                closed && i == 0 && n > 2 -> pts[1] - pts[n - 2]
                i == 0 && n > 1 -> pts[1] - pts[0]
                i == n - 1 && n > 1 -> pts[i] - pts[i - 1]
                n > 2 -> pts[i + 1] - pts[i - 1]
                else -> Vec3(0f, 0f, 1f)
            }
            rawTangents.add(tRaw.normalizedXZ())
        }

        val smoothTangents = ArrayList<Vec3>(n)
        for (i in 0 until n) {
            val tPrev = rawTangents[(i - 1 + n) % n]
            val tCurr = rawTangents[i]
            val tNext = rawTangents[(i + 1) % n]
            val blended = when {
                !closed && (i == 0 || i == n - 1) -> tCurr
                else -> (tPrev * 0.25f + tCurr * 0.50f + tNext * 0.25f).normalizedXZ()
            }
            smoothTangents.add(blended)
        }

        // Compute IRC Horizontal Curve Extra Widening (for 12m Hino RM 2 bus rear-wheel off-tracking)
        val rawCurveExtra = FloatArray(n)
        if (!closed && n >= 6) {
            for (i in 0 until n) {
                if (pts[i].z < -125f && pts[i].x in -5f..40f) {
                    rawCurveExtra[i] = 0f
                    continue
                }
                val iBack = (i - 3).coerceAtLeast(0)
                val iFwd = (i + 3).coerceAtMost(n - 1)
                val dot = smoothTangents[iBack].dotXZ(smoothTangents[iFwd]).coerceIn(-1f, 1f)
                val turnAngleRad = acos(dot)
                rawCurveExtra[i] = (turnAngleRad * 6.2f).coerceIn(0f, 3.4f)
            }
        }

        val smoothCurveExtra = FloatArray(n)
        for (i in 0 until n) {
            var sum = 0f
            var count = 0
            for (k in -3..3) {
                val idx = (i + k).coerceIn(0, n - 1)
                sum += rawCurveExtra[idx]
                count++
            }
            smoothCurveExtra[i] = sum / count.coerceAtLeast(1)
        }

        val out = ArrayList<RoadFrame>(n)
        var dist = 0f
        for (i in 0 until n) {
            if (i > 0) {
                dist += (pts[i] - pts[i - 1]).lengthXZ()
            }
            val t = smoothTangents[i]
            val r = Vec3(t.z, 0f, -t.x)
            var frameHalfW = baseHalfW + smoothCurveExtra[i]

            // Exact gate-width alignment at Sakthan Stand North Gates (Z = -140m):
            val pz = pts[i].z
            if (roadId == "ROAD_SAKTHAN_TO_SWARAJ" && pz < -124.0f) {
                val gateBlend = ((pz - (-136.0f)) / 12.0f).coerceIn(0f, 1f)
                frameHalfW = 5.6f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if (roadId == "ROAD_SAKTHAN_STAND_ENTRY" && pz < -116.0f) {
                val gateBlend = ((pz - (-128.0f)) / 12.0f).coerceIn(0f, 1f)
                frameHalfW = 4.6f * (1f - gateBlend) + frameHalfW * gateBlend
            }

            out.add(RoadFrame(pts[i], t, r, dist, frameHalfW, Float.MAX_VALUE))
        }
        return out
    }
}
