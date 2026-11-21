package com.bharatbussim.game.model

import android.content.Context
import androidx.compose.runtime.Immutable
import com.bharatbussim.game.ui.ShoranurRailwaySystem
import com.bharatbussim.game.ui.Zone3Nh544SouthRenderer
import com.bharatbussim.game.ui.Zone4KoleRailHeritageRenderer
import com.bharatbussim.game.ui.Zone5Nh66CoastalRenderer
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
        Pair("ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR", "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM"),
        Pair("ROAD_SWARAJ_TO_MANNUTHY", "ROAD_MANNUTHY_TO_PEECHI_DAM"),
        Pair("ROAD_NH544_MANNUTHY_TO_PALIYEKKARA", "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY")
    )

    private fun isContinuationPair(idA: String, idB: String): Boolean =
        continuationPairs.any { (a, b) -> (a == idA && b == idB) || (a == idB && b == idA) }

    private fun roadPriority(id: String): Int = when (id) {
        "ROAD_SWARAJ_ROUND" -> 0
        "ROAD_SAKTHAN_TO_SWARAJ" -> 1
        "ROAD_SWARAJ_TO_MANNUTHY" -> 1
        "ROAD_MANNUTHY_TO_PEECHI_DAM" -> 1
        "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" -> 2
        "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY" -> 2
        "ROAD_NH544_MANNUTHY_TO_KUTHIRAN" -> 2
        "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD" -> 2
        "ROAD_NH544_KUTHIRAN_LEFT_TUBE", "ROAD_NH544_KUTHIRAN_RIGHT_TUBE" -> 3
        "ROAD_SWARAJ_TO_VADAKKE" -> 2
        "ROAD_SWARAJ_TO_WEST_FORT" -> 2
        "ROAD_WEST_FORT_TO_KANJANI" -> 2
        "ROAD_KANJANI_TO_GURUVAYUR_CHAVAKKAD" -> 2
        "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" -> 2
        "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" -> 2
        "ROAD_SAKTHAN_STAND_ENTRY" -> 3
        "ROAD_VADAKKE_STAND_EXIT" -> 3
        "ROAD_SAKTHAN_TO_OLLUR" -> 3
        "ROAD_PUTHANPALLI_LINK" -> 3
        "ROAD_SH61_TRIPRAYAR_TO_KANJANI" -> 3
        "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR" -> 3
        "ROAD_STATION_ROAD" -> 4
        "ROAD_THRISSUR_TO_SHORANUR" -> 3
        "ROAD_SHORANUR_TERMINAL_ENTRY", "ROAD_SHORANUR_TERMINAL_EXIT",
        "ROAD_SHORANUR_STATION_ENTRY", "ROAD_SHORANUR_STATION_EXIT",
        "ROAD_PEECHI_DAM_ENTRY", "ROAD_PEECHI_DAM_EXIT" -> 4
        "ROAD_RAILWAY_STATION_ACCESS" -> 5
        "ROAD_RAILWAY_STATION_EXIT" -> 6
        else -> 6
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
                "ROAD_SWARAJ_ROUND" -> 15.0
                "ROAD_SAKTHAN_TO_SWARAJ" -> 12.0
                "ROAD_SWARAJ_TO_VADAKKE", "ROAD_SWARAJ_TO_WEST_FORT" -> 11.0
                else -> 10.0
            }
            val widthM = if (id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR") 22.0f else obj.optDouble("width_m", defaultWidth).toFloat()
            val lanes = if (id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR") 6 else obj.optInt("lanes", 2)
            val divided = if (id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR") true else obj.optBoolean("divided", false)
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

            val engineered = engineerHighwaySpline(rawPts, closedLoop).let { pts ->
                if (id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA") {
                    // Densify elevated Ollur ROB & Paliyekkara 92m S-curve flare, and smoothly align the North
                    // approach dead-straight onto X = 999.5f by Z = -1678.0f (before the flare starts at -1686)
                    // so the 24m highway seamlessly widens into the 12-lane toll plaza with 0.000m offset!
                    val dense = ArrayList<Vec3>(pts.size + 96)
                    for (k in 0 until pts.size) {
                        val a = pts[k]
                        dense.add(a)
                        if (k < pts.size - 1) {
                            val b = pts[k + 1]
                            val midZ = (a.z + b.z) * 0.5f
                            val inTollFlare = midZ <= -1660f
                            val inRobSpan = midZ in -1545f..-1315f
                            val maxStep = if (inTollFlare) 6.0f else if (inRobSpan) 8.0f else 999f
                            val segLen = hypot(b.x - a.x, b.z - a.z)
                            if (segLen > maxStep + 0.4f) {
                                val sub = kotlin.math.ceil(segLen / maxStep).toInt().coerceIn(2, 24)
                                for (s in 1 until sub) {
                                    dense.add(lerp(a, b, s.toFloat() / sub.toFloat()))
                                }
                            }
                        }
                    }
                    dense.map { pt ->
                        val alignedX = when {
                            pt.z <= -1678.0f -> 999.5f
                            pt.z < -1555.0f -> {
                                val u = ((-1555.0f - pt.z) / 123.0f).coerceIn(0f, 1f)
                                val w = u * u * (3f - 2f * u)
                                pt.x + (999.5f - pt.x) * w
                            }
                            else -> pt.x
                        }
                        Vec3(alignedX, ShoranurRailwaySystem.computeRoadSurfaceY(alignedX, pt.z) - 0.01f, pt.z)
                    }
                } else if (id == "ROAD_WEST_FORT_TO_KANJANI") {
                    // Zone 4 SH 75: Densify across Thrissur Kole Wetlands (1.5m raised embankment) &
                    // lock dead-straight onto Z = ENAMAVU_CENTER_Z (113.0f) across Enamavu Sluice Regulator Bridge!
                    val dense = ArrayList<Vec3>(pts.size + 96)
                    for (k in 0 until pts.size) {
                        val a = pts[k]
                        dense.add(a)
                        if (k < pts.size - 1) {
                            val b = pts[k + 1]
                            val midX = (a.x + b.x) * 0.5f
                            val inKole = midX in -1855f..-1090f
                            val maxStep = if (inKole) 6.5f else 999f
                            val segLen = hypot(b.x - a.x, b.z - a.z)
                            if (segLen > maxStep + 0.4f) {
                                val sub = kotlin.math.ceil(segLen / maxStep).toInt().coerceIn(2, 24)
                                for (s in 1 until sub) {
                                    dense.add(lerp(a, b, s.toFloat() / sub.toFloat()))
                                }
                            }
                        }
                    }
                    dense.map { pt ->
                        val ez = Zone4KoleRailHeritageRenderer.ENAMAVU_CENTER_Z
                        val alignedZ = when {
                            pt.x in -1735.0f..-1555.0f -> ez
                            pt.x in -1555.0f..-1520.0f -> {
                                val u = ((-1520.0f - pt.x) / 35.0f).coerceIn(0f, 1f)
                                val w = u * u * (3f - 2f * u)
                                pt.z + (ez - pt.z) * w
                            }
                            pt.x in -1770.0f..-1735.0f -> {
                                val u = ((pt.x - (-1770.0f)) / 35.0f).coerceIn(0f, 1f)
                                val w = u * u * (3f - 2f * u)
                                pt.z + (ez - pt.z) * w
                            }
                            else -> pt.z
                        }
                        Vec3(pt.x, ShoranurRailwaySystem.computeRoadSurfaceY(pt.x, alignedZ) - 0.01f, alignedZ)
                    }
                } else if (id == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR") {
                    // Zone 5 NH 66 Coastal 6-Lane Divided National Highway (22.0m = 3+3 lanes + shoulders):
                    // Smooth engineered expressway alignment replacing raw crooked old-road zig-zags:
                    //  1. Clean South-East sweep from Chavakkad Beach (-4500, 1020) -> Orumanayur
                    //  2. Dead-straight North-South across Chettuva Backwaters Bridge (X = -3664.0, Z = 420..-10)
                    //  3. Smooth coastal sweep through Vadanappally -> exact SH 61 Junction (-2802.2, -1220.5)
                    //  4. Dead-straight 400m approach & exit on X = -2536.0 through Nattika 10-Lane Toll Plaza
                    //     (Z = -1630..-2035, ZERO S-kinks before/after the 92m Cosine S-curve bellmouth flares!)
                    //  5. Gentle sweep into dead-straight Triprayar (X = -2220.0, Z = -2320..-2470)
                    val cxChet = Zone5Nh66CoastalRenderer.CHETTUVA_CENTER_X
                    val cxNat = Zone5Nh66CoastalRenderer.NATTIKA_CENTER_X
                    val nh66EngineeredControlPts = listOf(
                        Vec3(-4500.0f, 0.05f, 1020.0f),
                        Vec3(-4486.0f, 0.05f, 975.0f),
                        Vec3(-4440.0f, 0.05f, 920.0f),
                        Vec3(-4350.0f, 0.05f, 855.0f),
                        Vec3(-4220.0f, 0.05f, 780.0f),
                        Vec3(-4070.0f, 0.05f, 695.0f),
                        Vec3(-3920.0f, 0.05f, 605.0f),
                        Vec3(-3785.0f, 0.05f, 515.0f),
                        Vec3(-3695.0f, 0.05f, 448.0f),
                        // Dead-straight Chettuva Backwaters Bridge (X = -3664.0, Z = 415..-5)
                        Vec3(cxChet, 0.05f, 415.0f),
                        Vec3(cxChet, 0.05f, 380.0f),
                        Vec3(cxChet, 0.05f, 335.0f),
                        Vec3(cxChet, 0.05f, 275.0f),
                        Vec3(cxChet, 0.05f, 215.0f),
                        Vec3(cxChet, 0.05f, 155.0f),
                        Vec3(cxChet, 0.05f, 95.0f),
                        Vec3(cxChet, 0.05f, 50.0f),
                        Vec3(cxChet, 0.05f, 15.0f),
                        Vec3(cxChet, 0.05f, -20.0f),
                        // Sweeping 6-Lane NH 66 through Vadanappally -> Engandiyur -> SH 61 Triprayar/Kanjani Junction
                        Vec3(-3632.0f, 0.05f, -95.0f),
                        Vec3(-3555.0f, 0.05f, -210.0f),
                        Vec3(-3455.0f, 0.05f, -350.0f),
                        Vec3(-3345.0f, 0.05f, -505.0f),
                        Vec3(-3225.0f, 0.05f, -665.0f),
                        Vec3(-3105.0f, 0.05f, -825.0f),
                        Vec3(-2990.0f, 0.05f, -975.0f),
                        Vec3(-2885.0f, 0.05f, -1110.0f),
                        // Exact SH 61 Kanjani-Triprayar Junction intersection!
                        Vec3(-2802.2f, 0.05f, -1220.5f),
                        // Long, gentle 410m curve into dead-straight Nattika Toll Plaza North alignment (X = -2536.0)
                        Vec3(-2730.0f, 0.05f, -1325.0f),
                        Vec3(-2660.0f, 0.05f, -1430.0f),
                        Vec3(-2596.0f, 0.05f, -1530.0f),
                        Vec3(-2552.0f, 0.05f, -1610.0f),
                        // Dead-straight Nattika 10-Lane Toll Plaza + 92m North & South Cosine Flares (X = -2536.0, Z = -1650..-2030)
                        Vec3(cxNat, 0.05f, -1650.0f),
                        Vec3(cxNat, 0.05f, -1690.0f),
                        Vec3(cxNat, 0.05f, -1730.0f),
                        Vec3(cxNat, 0.05f, -1775.0f),
                        Vec3(cxNat, 0.05f, -1822.0f),
                        Vec3(cxNat, 0.05f, -1850.0f),
                        Vec3(cxNat, 0.05f, -1878.0f),
                        Vec3(cxNat, 0.05f, -1925.0f),
                        Vec3(cxNat, 0.05f, -1970.0f),
                        Vec3(cxNat, 0.05f, -2025.0f),
                        // Smooth 295m curve into dead-straight Triprayar NH 66 Terminus & Canoli Bridge alignment (X = -2220.0)
                        Vec3(-2498.0f, 0.05f, -2105.0f),
                        Vec3(-2420.0f, 0.05f, -2185.0f),
                        Vec3(-2325.0f, 0.05f, -2255.0f),
                        Vec3(-2248.0f, 0.05f, -2308.0f),
                        Vec3(-2220.0f, 0.05f, -2340.0f),
                        Vec3(-2220.0f, 0.05f, -2380.0f),
                        Vec3(-2220.0f, 0.05f, -2425.0f),
                        Vec3(-2220.0f, 0.05f, -2470.0f)
                    )
                    val nh66Smoothed = engineerHighwaySpline(nh66EngineeredControlPts, closed = false)
                    val dense = ArrayList<Vec3>(nh66Smoothed.size + 160)
                    for (k in 0 until nh66Smoothed.size) {
                        val a = nh66Smoothed[k]
                        dense.add(a)
                        if (k < nh66Smoothed.size - 1) {
                            val b = nh66Smoothed[k + 1]
                            val midZ = (a.z + b.z) * 0.5f
                            val inChettuva = midZ in 15f..415f
                            val inNattikaFlare = midZ in -1990f..-1710f
                            val maxStep = if (inNattikaFlare) 6.0f else if (inChettuva) 7.5f else 999f
                            val segLen = hypot(b.x - a.x, b.z - a.z)
                            if (segLen > maxStep + 0.4f) {
                                val sub = kotlin.math.ceil(segLen / maxStep).toInt().coerceIn(2, 24)
                                for (s in 1 until sub) {
                                    dense.add(lerp(a, b, s.toFloat() / sub.toFloat()))
                                }
                            }
                        }
                    }
                    dense.map { pt ->
                        val z = pt.z
                        val alignedX = when {
                            z in 20.0f..405.0f -> cxChet
                            z in -2015.0f..-1665.0f -> cxNat
                            z <= -2345.0f -> -2220.0f
                            else -> pt.x
                        }
                        Vec3(alignedX, ShoranurRailwaySystem.computeRoadSurfaceY(alignedX, z) - 0.01f, z)
                    }
                } else {
                    pts
                }
            }.toMutableList()
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
                Vec3(-18.65f, 0.05f, -51.0f),
                Vec3(-18.65f, 0.05f, -44.313f)
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

        // Thrissur - Wadakkanchery - Shoranur Highway (SH 22) with elevated Wadakkanchery Railway Overbridge (ROB)
        // Branches East off ROAD_SWARAJ_TO_VADAKKE south of Vadakke Stand, clears Vadakke Stand by 18m+,
        // crosses directly OVER the 2-track Southern Railway corridor at Wadakkanchery ROB (X=92, Z=1520..1840, Y=7.40m),
        // and continues into Shoranur Town (X=100, Z=2960..3370) serving Shoranur Bus Terminal & Shoranur Junction Station.
        if (specs.none { it.id == "ROAD_THRISSUR_TO_SHORANUR" }) {
            val sh22RawPts = listOf(
                Vec3(-12.0f, 0.05f, 162.0f),
                Vec3(4.0f, 0.05f, 167.5f),
                Vec3(17.5f, 0.05f, 177.5f),
                Vec3(24.8f, 0.05f, 195.0f),
                Vec3(26.0f, 0.05f, 232.0f),
                Vec3(26.0f, 0.05f, 272.0f),
                Vec3(29.0f, 0.05f, 355.0f),
                Vec3(35.0f, 0.05f, 445.0f),
                Vec3(43.0f, 0.05f, 545.0f),
                Vec3(52.0f, 0.05f, 680.0f),
                Vec3(62.0f, 0.05f, 840.0f),
                Vec3(72.0f, 0.05f, 1000.0f),
                Vec3(80.0f, 0.05f, 1180.0f),
                Vec3(87.0f, 0.05f, 1340.0f),
                Vec3(91.0f, 0.05f, 1455.0f),
                Vec3(92.0f, 0.05f, 1500.0f),
                // Dense straight points across Wadakkanchery ROB (Z = 1520 .. 1840, X = 92.0)
                Vec3(92.0f, 0.05f, 1520.0f),
                Vec3(92.0f, 0.05f, 1542.0f),
                Vec3(92.0f, 0.05f, 1564.0f),
                Vec3(92.0f, 0.05f, 1586.0f),
                Vec3(92.0f, 0.05f, 1608.0f),
                Vec3(92.0f, 0.05f, 1630.0f),
                Vec3(92.0f, 0.05f, 1655.0f),
                Vec3(92.0f, 0.05f, 1680.0f),
                Vec3(92.0f, 0.05f, 1705.0f),
                Vec3(92.0f, 0.05f, 1730.0f),
                Vec3(92.0f, 0.05f, 1752.0f),
                Vec3(92.0f, 0.05f, 1774.0f),
                Vec3(92.0f, 0.05f, 1796.0f),
                Vec3(92.0f, 0.05f, 1818.0f),
                Vec3(92.0f, 0.05f, 1840.0f),
                // Wadakkanchery Town, Mullurkara, Cheruthuruthy -> Bharathappuzha Dual Bridges (X = 100.0, Z = 2630..2950) -> Shoranur
                Vec3(92.0f, 0.05f, 1875.0f),
                Vec3(92.0f, 0.05f, 1930.0f),
                Vec3(92.5f, 0.05f, 2000.0f),
                Vec3(94.0f, 0.05f, 2140.0f),
                Vec3(95.5f, 0.05f, 2320.0f),
                Vec3(97.5f, 0.05f, 2480.0f),
                Vec3(99.4f, 0.05f, 2575.0f),
                Vec3(100.0f, 0.05f, 2610.0f),
                // Dense dead-straight vertices across Bharathappuzha Modern Roadway Bridge (Z = 2630..2950, X = 100.0)
                Vec3(100.0f, 0.05f, 2630.0f),
                Vec3(100.0f, 0.05f, 2650.0f),
                Vec3(100.0f, 0.05f, 2670.0f),
                Vec3(100.0f, 0.05f, 2690.0f),
                Vec3(100.0f, 0.05f, 2720.0f),
                Vec3(100.0f, 0.05f, 2755.0f),
                Vec3(100.0f, 0.05f, 2790.0f),
                Vec3(100.0f, 0.05f, 2825.0f),
                Vec3(100.0f, 0.05f, 2860.0f),
                Vec3(100.0f, 0.05f, 2890.0f),
                Vec3(100.0f, 0.05f, 2910.0f),
                Vec3(100.0f, 0.05f, 2930.0f),
                Vec3(100.0f, 0.05f, 2950.0f),
                Vec3(100.0f, 0.05f, 2975.0f),
                Vec3(100.0f, 0.05f, 3010.0f),
                Vec3(100.0f, 0.05f, 3044.0f),
                Vec3(100.0f, 0.05f, 3075.0f),
                Vec3(100.0f, 0.05f, 3106.0f),
                Vec3(100.0f, 0.05f, 3152.0f),
                Vec3(100.0f, 0.05f, 3198.0f),
                Vec3(100.0f, 0.05f, 3235.0f),
                Vec3(100.0f, 0.05f, 3272.0f),
                Vec3(100.0f, 0.05f, 3325.0f),
                Vec3(100.0f, 0.05f, 3375.0f)
            )
            val sh22Engineered = engineerHighwaySpline(sh22RawPts, closed = false).let { pts ->
                // Densify Bharathappuzha bridge ramps to <= 7.5m step & lock straight on X = 100.0f
                val dense = ArrayList<Vec3>(pts.size + 48)
                for (k in 0 until pts.size) {
                    val a = pts[k]
                    dense.add(a)
                    if (k < pts.size - 1) {
                        val b = pts[k + 1]
                        val midZ = (a.z + b.z) * 0.5f
                        if (midZ in 2615f..2965f) {
                            val segLen = hypot(b.x - a.x, b.z - a.z)
                            if (segLen > 7.8f) {
                                val sub = kotlin.math.ceil(segLen / 7.5f).toInt().coerceIn(2, 16)
                                for (s in 1 until sub) {
                                    dense.add(lerp(a, b, s.toFloat() / sub.toFloat()))
                                }
                            }
                        }
                    }
                }
                dense.map { pt ->
                    val ax = if (pt.z in 2615f..2965f) Zone4KoleRailHeritageRenderer.BHARATHAPPUZHA_CENTER_X else pt.x
                    Vec3(ax, ShoranurRailwaySystem.computeRoadSurfaceY(ax, pt.z) - 0.01f, pt.z)
                }
            }.toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_THRISSUR_TO_SHORANUR",
                    name = "SH 22 • Thrissur - Wadakkanchery ROB - Shoranur Highway",
                    roadType = "state_highway",
                    widthM = 11.2f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 33500.0f,
                    steps = listOf(
                        "Shoranur Road (SH 22) - Viyyur",
                        "Athani & Mulagunnathukavu",
                        "Wadakkanchery Railway Overbridge (ROB)",
                        "Wadakkanchery Town & Mullurkara",
                        "Cheruthuruthy - Shoranur Bus Terminal & Junction"
                    ),
                    smoothedPts = sh22Engineered,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_THRISSUR_TO_SHORANUR")
                )
            )
        }

        // Shoranur Bus Terminal South ENTRY & North EXIT Gate Access Roads (East of SH 22)
        if (specs.none { it.id == "ROAD_SHORANUR_TERMINAL_ENTRY" }) {
            val pts = engineerHighwaySpline(
                listOf(
                    Vec3(100.0f, 0.05f, 3044.0f),
                    Vec3(109.0f, 0.05f, 3044.0f),
                    Vec3(118.0f, 0.05f, 3044.0f),
                    Vec3(126.0f, 0.05f, 3044.0f),
                    Vec3(136.0f, 0.05f, 3044.0f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_SHORANUR_TERMINAL_ENTRY",
                    name = "Shoranur Bus Terminal • South Entry Gate",
                    roadType = "city_arterial",
                    widthM = 10.0f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 180.0f,
                    steps = listOf("SH 22 Shoranur", "Shoranur Bus Terminal Entry Gate"),
                    smoothedPts = pts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_SHORANUR_TERMINAL_ENTRY")
                )
            )
        }

        if (specs.none { it.id == "ROAD_SHORANUR_TERMINAL_EXIT" }) {
            val pts = engineerHighwaySpline(
                listOf(
                    Vec3(136.0f, 0.05f, 3106.0f),
                    Vec3(126.0f, 0.05f, 3106.0f),
                    Vec3(118.0f, 0.05f, 3106.0f),
                    Vec3(109.0f, 0.05f, 3106.0f),
                    Vec3(100.0f, 0.05f, 3106.0f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_SHORANUR_TERMINAL_EXIT",
                    name = "Shoranur Bus Terminal • North Exit Gate",
                    roadType = "city_arterial",
                    widthM = 10.0f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 180.0f,
                    steps = listOf("Shoranur Bus Terminal Exit Gate", "SH 22 Shoranur"),
                    smoothedPts = pts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_SHORANUR_TERMINAL_EXIT")
                )
            )
        }

        // Shoranur Junction Railway Station (SRR) South ENTRY & North EXIT Gate Access Roads (West of SH 22)
        if (specs.none { it.id == "ROAD_SHORANUR_STATION_ENTRY" }) {
            val pts = engineerHighwaySpline(
                listOf(
                    Vec3(100.0f, 0.05f, 3198.0f),
                    Vec3(91.0f, 0.05f, 3198.0f),
                    Vec3(82.0f, 0.05f, 3198.0f),
                    Vec3(74.0f, 0.05f, 3198.0f),
                    Vec3(64.0f, 0.05f, 3198.0f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_SHORANUR_STATION_ENTRY",
                    name = "Shoranur Junction Station (SRR) • Entry Gate",
                    roadType = "city_arterial",
                    widthM = 10.0f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 180.0f,
                    steps = listOf("SH 22 Shoranur", "Shoranur Junction Railway Station Entry"),
                    smoothedPts = pts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_SHORANUR_STATION_ENTRY")
                )
            )
        }

        if (specs.none { it.id == "ROAD_SHORANUR_STATION_EXIT" }) {
            val pts = engineerHighwaySpline(
                listOf(
                    Vec3(64.0f, 0.05f, 3272.0f),
                    Vec3(74.0f, 0.05f, 3272.0f),
                    Vec3(82.0f, 0.05f, 3272.0f),
                    Vec3(91.0f, 0.05f, 3272.0f),
                    Vec3(100.0f, 0.05f, 3272.0f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_SHORANUR_STATION_EXIT",
                    name = "Shoranur Junction Station (SRR) • Exit Gate",
                    roadType = "city_arterial",
                    widthM = 10.0f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 180.0f,
                    steps = listOf("Shoranur Junction Railway Station Exit", "SH 22 Shoranur"),
                    smoothedPts = pts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_SHORANUR_STATION_EXIT")
                )
            )
        }

        // ZONE 2: NH 544 East 6-Lane Divided Expressway (Mannuthy 4-Way Interchange -> Kuthiran West Approach)
        // Leaves Mannuthy (1050.30, 198.33) North-East into wide-open corridor (+37.8m..+440m clear of all existing roads)
        if (specs.none { it.id == "ROAD_NH544_MANNUTHY_TO_KUTHIRAN" }) {
            val nh544KuthiranPts = engineerHighwaySpline(
                listOf(
                    Vec3(1050.30f, 0.05f, 198.33f),
                    Vec3(1048.5f, 0.05f, 225.0f),
                    Vec3(1046.0f, 0.05f, 260.0f),
                    Vec3(1043.0f, 0.05f, 305.0f),
                    Vec3(1041.0f, 0.05f, 355.0f),
                    Vec3(1042.0f, 0.05f, 415.0f),
                    Vec3(1052.0f, 0.05f, 485.0f),
                    Vec3(1078.0f, 0.05f, 565.0f),
                    Vec3(1122.0f, 0.05f, 650.0f),
                    Vec3(1185.0f, 0.05f, 740.0f),
                    Vec3(1265.0f, 0.05f, 830.0f),
                    Vec3(1365.0f, 0.05f, 915.0f),
                    Vec3(1480.0f, 0.05f, 990.0f),
                    Vec3(1610.0f, 0.05f, 1055.0f),
                    Vec3(1745.0f, 0.05f, 1105.0f),
                    Vec3(1875.0f, 0.05f, 1134.0f),
                    Vec3(1945.0f, 0.05f, 1140.0f),
                    Vec3(2000.0f, 0.05f, 1140.0f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_NH544_MANNUTHY_TO_KUTHIRAN",
                    name = "NH 544 • Mannuthy 4-Way Flyover to Kuthiran Tunnel (6-Lane)",
                    roadType = "national_highway",
                    widthM = 24.0f,
                    lanes = 6,
                    divided = true,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 14200.0f,
                    steps = listOf(
                        "Mannuthy 4-Way Elevated Flyover • NH 544",
                        "NH 544 6-Lane Expressway • Pattikkad",
                        "Western Ghats Approach • Kuthiran Mala",
                        "Kuthiran Twin-Tube Tunnel West Portal"
                    ),
                    smoothedPts = nh544KuthiranPts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_NH544_MANNUTHY_TO_KUTHIRAN")
                )
            )
        }

        // ZONE 2: Kuthiran Twin-Tube Tunnel — Left Tube (3-Lane 10.5m Eastbound, 955m inside mountain)
        if (specs.none { it.id == "ROAD_NH544_KUTHIRAN_LEFT_TUBE" }) {
            val leftTubePts = engineerHighwaySpline(
                listOf(
                    Vec3(1994.0f, 0.05f, 1146.5f),
                    Vec3(2035.0f, 0.05f, 1147.8f),
                    Vec3(2085.0f, 0.05f, 1151.2f),
                    Vec3(2132.0f, 0.05f, 1153.6f),
                    Vec3(2160.0f, 0.05f, 1154.0f),
                    Vec3(2350.0f, 0.05f, 1154.0f),
                    Vec3(2640.0f, 0.05f, 1154.0f),
                    Vec3(2930.0f, 0.05f, 1154.0f),
                    Vec3(3115.0f, 0.05f, 1154.0f),
                    Vec3(3143.0f, 0.05f, 1153.6f),
                    Vec3(3190.0f, 0.05f, 1151.2f),
                    Vec3(3240.0f, 0.05f, 1147.8f),
                    Vec3(3286.0f, 0.05f, 1146.5f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_NH544_KUTHIRAN_LEFT_TUBE",
                    name = "Kuthiran Tunnel • Left Tube (955m • 3-Lane Eastbound)",
                    roadType = "national_highway",
                    widthM = 10.5f,
                    lanes = 3,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 1300.0f,
                    steps = listOf(
                        "Kuthiran West Portal Approach",
                        "Kuthiran Twin Tunnel • Left Tube (955m)",
                        "Kuthiran East Portal • Palakkad Side"
                    ),
                    smoothedPts = leftTubePts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_NH544_KUTHIRAN_LEFT_TUBE")
                )
            )
        }

        // ZONE 2: Kuthiran Twin-Tube Tunnel — Right Tube (3-Lane 10.5m Westbound, 944m inside mountain)
        if (specs.none { it.id == "ROAD_NH544_KUTHIRAN_RIGHT_TUBE" }) {
            val rightTubePts = engineerHighwaySpline(
                listOf(
                    Vec3(1994.0f, 0.05f, 1133.5f),
                    Vec3(2035.0f, 0.05f, 1132.2f),
                    Vec3(2085.0f, 0.05f, 1128.8f),
                    Vec3(2132.0f, 0.05f, 1126.4f),
                    Vec3(2160.0f, 0.05f, 1126.0f),
                    Vec3(2350.0f, 0.05f, 1126.0f),
                    Vec3(2640.0f, 0.05f, 1126.0f),
                    Vec3(2930.0f, 0.05f, 1126.0f),
                    Vec3(3115.0f, 0.05f, 1126.0f),
                    Vec3(3143.0f, 0.05f, 1126.4f),
                    Vec3(3190.0f, 0.05f, 1128.8f),
                    Vec3(3240.0f, 0.05f, 1132.2f),
                    Vec3(3286.0f, 0.05f, 1133.5f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_NH544_KUTHIRAN_RIGHT_TUBE",
                    name = "Kuthiran Tunnel • Right Tube (944m • 3-Lane Westbound)",
                    roadType = "national_highway",
                    widthM = 10.5f,
                    lanes = 3,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 1300.0f,
                    steps = listOf(
                        "Kuthiran West Portal Exit",
                        "Kuthiran Twin Tunnel • Right Tube (944m)",
                        "Kuthiran East Portal Approach"
                    ),
                    smoothedPts = rightTubePts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_NH544_KUTHIRAN_RIGHT_TUBE")
                )
            )
        }

        // ZONE 2: NH 544 East Continuation past Kuthiran East Portal toward Wadakkenchery / Palakkad
        if (specs.none { it.id == "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD" }) {
            val eastPalakkadPts = engineerHighwaySpline(
                listOf(
                    Vec3(3280.0f, 0.05f, 1140.0f),
                    Vec3(3360.0f, 0.05f, 1140.0f),
                    Vec3(3460.0f, 0.05f, 1140.0f),
                    Vec3(3560.0f, 0.05f, 1140.0f),
                    Vec3(3660.0f, 0.05f, 1140.0f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD",
                    name = "NH 544 • Kuthiran East to Palakkad 6-Lane Expressway",
                    roadType = "national_highway",
                    widthM = 24.0f,
                    lanes = 6,
                    divided = true,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 3800.0f,
                    steps = listOf(
                        "Kuthiran East Portal • NH 544",
                        "NH 544 6-Lane Expressway • Toward Palakkad"
                    ),
                    smoothedPts = eastPalakkadPts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_NH544_KUTHIRAN_EAST_PALAKKAD")
                )
            )
        }

        // ZONE 2: Peechi Dam Bus Stand & Viewpoint Walled Compound — South ENTRY & North EXIT Gate Access Roads
        // Branch smoothly off ROAD_MANNUTHY_TO_PEECHI_DAM near (3386.45, 120.18) into West Compound Wall (X = 3412.0)
        if (specs.none { it.id == "ROAD_PEECHI_DAM_ENTRY" }) {
            val pts = engineerHighwaySpline(
                listOf(
                    Vec3(3386.45f, 0.05f, 120.18f),
                    Vec3(3393.5f, 0.05f, 111.5f),
                    Vec3(3400.5f, 0.05f, 102.5f),
                    Vec3(3406.5f, 0.05f, 98.5f),
                    Vec3(3412.0f, 0.05f, 98.0f),
                    Vec3(3420.0f, 0.05f, 98.0f),
                    Vec3(3428.0f, 0.05f, 98.0f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_PEECHI_DAM_ENTRY",
                    name = "Peechi Dam Bus Stand • South Entry Gate",
                    roadType = "city_arterial",
                    widthM = 10.0f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 140.0f,
                    steps = listOf("SH 51 Peechi Road", "Peechi Dam Entry Gate & Bus Bays"),
                    smoothedPts = pts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_PEECHI_DAM_ENTRY")
                )
            )
        }

        if (specs.none { it.id == "ROAD_PEECHI_DAM_EXIT" }) {
            val pts = engineerHighwaySpline(
                listOf(
                    Vec3(3428.0f, 0.05f, 152.0f),
                    Vec3(3420.0f, 0.05f, 152.0f),
                    Vec3(3412.0f, 0.05f, 152.0f),
                    Vec3(3406.5f, 0.05f, 151.5f),
                    Vec3(3400.5f, 0.05f, 146.5f),
                    Vec3(3393.5f, 0.05f, 134.5f),
                    Vec3(3386.45f, 0.05f, 120.18f)
                ),
                closed = false
            ).toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_PEECHI_DAM_EXIT",
                    name = "Peechi Dam Bus Stand • North Exit Gate",
                    roadType = "city_arterial",
                    widthM = 10.0f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 140.0f,
                    steps = listOf("Peechi Dam Exit Gate", "SH 51 Peechi Road -> Mannuthy"),
                    smoothedPts = pts,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_PEECHI_DAM_EXIT")
                )
            )
        }

        // ZONE 3: NH 544 South 4-Lane Divided Highway Continuation
        // Paliakkara Toll Plaza South (999.5, -1890.0) -> Puthukkad & Kurumali River Bridge (1366.0, -2180..-2320)
        // -> Kodakara Junction (2016.0, -3437.0) -> 280m Chalakudy River Bridge (2596.0, -4900..-5180) -> Chalakudy South Terminus
        if (specs.none { it.id == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY" }) {
            val z3RawPts = listOf(
                // Dead-straight continuation across Paliyekkara South S-curve flare (999.5, -1890..-1935)
                Vec3(999.5f, 0.05f, -1890.0f),
                Vec3(999.5f, 0.05f, -1912.0f),
                Vec3(999.5f, 0.05f, -1935.0f),
                Vec3(1022.0f, 0.05f, -1968.0f),
                Vec3(1075.0f, 0.05f, -2002.0f),
                Vec3(1155.0f, 0.05f, -2030.0f),
                Vec3(1245.0f, 0.05f, -2054.0f),
                Vec3(1320.0f, 0.05f, -2074.0f),
                Vec3(1356.0f, 0.05f, -2088.0f),
                // Straight North-South alignment across Kurumali River Bridge (X = 1366.0, Z = -2100..-2400)
                Vec3(1366.0f, 0.05f, -2100.0f),
                Vec3(1366.0f, 0.05f, -2120.0f),
                Vec3(1366.0f, 0.05f, -2140.0f),
                Vec3(1366.0f, 0.05f, -2160.0f),
                Vec3(1366.0f, 0.05f, -2180.0f),
                Vec3(1366.0f, 0.05f, -2215.0f),
                Vec3(1366.0f, 0.05f, -2250.0f),
                Vec3(1366.0f, 0.05f, -2285.0f),
                Vec3(1366.0f, 0.05f, -2320.0f),
                Vec3(1366.0f, 0.05f, -2340.0f),
                Vec3(1366.0f, 0.05f, -2360.0f),
                Vec3(1366.0f, 0.05f, -2380.0f),
                Vec3(1366.0f, 0.05f, -2400.0f),
                // Puthukkad -> Nellayi -> Kodakara Junction (2016.0, -3437.0)
                Vec3(1395.0f, 0.05f, -2465.0f),
                Vec3(1475.0f, 0.05f, -2585.0f),
                Vec3(1575.0f, 0.05f, -2745.0f),
                Vec3(1685.0f, 0.05f, -2920.0f),
                Vec3(1805.0f, 0.05f, -3110.0f),
                Vec3(1920.0f, 0.05f, -3290.0f),
                Vec3(2016.0f, 0.05f, -3437.0f),
                // Kodakara -> Perambra -> Potta -> Chalakudy North Approach
                Vec3(2125.0f, 0.05f, -3640.0f),
                Vec3(2240.0f, 0.05f, -3870.0f),
                Vec3(2355.0f, 0.05f, -4120.0f),
                Vec3(2460.0f, 0.05f, -4380.0f),
                Vec3(2540.0f, 0.05f, -4610.0f),
                Vec3(2588.0f, 0.05f, -4765.0f),
                // Straight North-South alignment across 280m Chalakudy River Bridge (X = 2596.0, Z = -4815..-5265)
                Vec3(2596.0f, 0.05f, -4815.0f),
                Vec3(2596.0f, 0.05f, -4840.0f),
                Vec3(2596.0f, 0.05f, -4860.0f),
                Vec3(2596.0f, 0.05f, -4880.0f),
                Vec3(2596.0f, 0.05f, -4900.0f),
                Vec3(2596.0f, 0.05f, -4945.0f),
                Vec3(2596.0f, 0.05f, -4990.0f),
                Vec3(2596.0f, 0.05f, -5040.0f),
                Vec3(2596.0f, 0.05f, -5090.0f),
                Vec3(2596.0f, 0.05f, -5135.0f),
                Vec3(2596.0f, 0.05f, -5180.0f),
                Vec3(2596.0f, 0.05f, -5200.0f),
                Vec3(2596.0f, 0.05f, -5220.0f),
                Vec3(2596.0f, 0.05f, -5240.0f),
                Vec3(2596.0f, 0.05f, -5265.0f),
                // Chalakudy South Bus Bay & Highway Terminus
                Vec3(2596.0f, 0.05f, -5300.0f),
                Vec3(2596.0f, 0.05f, -5350.0f),
                Vec3(2596.0f, 0.05f, -5398.0f)
            )
            val z3Smoothed = engineerHighwaySpline(z3RawPts, closed = false)
            // Densify bridge ramps & straight-lock X onto Paliyekkara South (999.5), Kurumali Bridge (1366.0),
            // and Chalakudy Bridge (2596.0) so spline smoothing NEVER pulls the road deck sideways off a bridge
            // and cosine ramp elevations match 3D bridge walkways & central New Jersey dividers to < 5mm!
            val z3Dense = ArrayList<Vec3>(z3Smoothed.size + 160)
            for (k in 0 until z3Smoothed.size) {
                val a = z3Smoothed[k]
                z3Dense.add(a)
                if (k < z3Smoothed.size - 1) {
                    val b = z3Smoothed[k + 1]
                    val midZ = (a.z + b.z) * 0.5f
                    val inTollSouthFlare = midZ >= -1965f
                    val inBridgeZone = midZ in -2460f..-2045f || midZ <= -4750f
                    val maxStep = if (inTollSouthFlare) 6.0f else if (inBridgeZone) 8.0f else 999f
                    val segLen = hypot(b.x - a.x, b.z - a.z)
                    if (segLen > maxStep + 0.4f) {
                        val sub = kotlin.math.ceil(segLen / maxStep).toInt().coerceIn(2, 24)
                        for (s in 1 until sub) {
                            z3Dense.add(lerp(a, b, s.toFloat() / sub.toFloat()))
                        }
                    }
                }
            }
            val z3Engineered = z3Dense.map { pt ->
                val z = pt.z
                val alignedX = when {
                    // Dead-straight Paliyekkara South Flare (X = 999.5f across -1890..-1932, smooth blend to -1982)
                    z >= -1932.0f -> 999.5f
                    z > -1982.0f -> {
                        val u = ((z - (-1982.0f)) / 50.0f).coerceIn(0f, 1f)
                        val w = u * u * (3f - 2f * u)
                        pt.x + (999.5f - pt.x) * w
                    }
                    // Dead-straight Kurumali River Bridge (X = 1366.0f across -2102..-2398, smooth lead-in/out)
                    z in -2398.0f..-2102.0f -> 1366.0f
                    z in -2102.0f..-2046.0f -> {
                        val u = ((-2046.0f - z) / 56.0f).coerceIn(0f, 1f)
                        val w = u * u * (3f - 2f * u)
                        pt.x + (1366.0f - pt.x) * w
                    }
                    z in -2458.0f..-2398.0f -> {
                        val u = ((z - (-2458.0f)) / 60.0f).coerceIn(0f, 1f)
                        val w = u * u * (3f - 2f * u)
                        pt.x + (1366.0f - pt.x) * w
                    }
                    // Dead-straight Chalakudy River Bridge + South Bus Bay (X = 2596.0f for Z <= -4822)
                    z <= -4822.0f -> 2596.0f
                    z < -4750.0f -> {
                        val u = ((-4750.0f - z) / 72.0f).coerceIn(0f, 1f)
                        val w = u * u * (3f - 2f * u)
                        pt.x + (2596.0f - pt.x) * w
                    }
                    else -> pt.x
                }
                Vec3(alignedX, ShoranurRailwaySystem.computeRoadSurfaceY(alignedX, z) - 0.01f, z)
            }.toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY",
                    name = "NH 544 South • Paliakkara - Kurumali Bridge - Kodakara - Chalakudy Bridge",
                    roadType = "national_highway",
                    widthM = 20.0f,
                    lanes = 4,
                    divided = true,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 18500.0f,
                    steps = listOf(
                        "NH 544 South • Paliakkara Toll Plaza Exit",
                        "Puthukkad • Kurumali River Bridge",
                        "NH 544 4-Lane Highway • Nellayi & Kodakara Junction",
                        "NH 544 South • Perambra & Potta",
                        "Chalakudy River Bridge (280m) & Chalakudy South Terminus"
                    ),
                    smoothedPts = z3Engineered,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY")
                )
            )
        }

        // Zone 4: SH 69 Thrissur (West Fort) - Punkunnam - Puzhakkal - Muthuvara Animated Railway Level Crossing -
        //         Amala Nagar - Mundur - Choondal - Guruvayur East Nada Junction!
        //         Connects seamlessly onto West Fort Junction (-266.09, -58.905) and Guruvayur South (-2666.0, 1196.0).
        if (specs.none { it.id == "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR" }) {
            val sh69RawPts = listOf(
                Vec3(-266.09f, 0.05f, -58.905f),  // Exact West Fort 3-Way Junction node
                Vec3(-278.0f, 0.05f, -18.0f),
                Vec3(-296.0f, 0.05f, 45.0f),
                Vec3(-322.0f, 0.05f, 125.0f),     // Punkunnam West Avenue
                Vec3(-365.0f, 0.05f, 220.0f),
                Vec3(-428.0f, 0.05f, 315.0f),     // Puzhakkal Sobha City / Lulu stretch
                Vec3(-498.0f, 0.05f, 395.0f),
                Vec3(-544.0f, 0.05f, 445.0f),
                // Dead-straight North-South corridor across Muthuvara Animated Railway LC-14 (X = -560.0, Z = 610.0)
                Vec3(-560.0f, 0.05f, 480.0f),
                Vec3(-560.0f, 0.05f, 520.0f),
                Vec3(-560.0f, 0.05f, 560.0f),
                Vec3(-560.0f, 0.05f, 588.0f),
                Vec3(-560.0f, 0.05f, 610.0f),     // Exact Muthuvara LC-14 Rail Centerline
                Vec3(-560.0f, 0.05f, 632.0f),
                Vec3(-560.0f, 0.05f, 660.0f),
                Vec3(-560.0f, 0.05f, 700.0f),
                Vec3(-560.0f, 0.05f, 740.0f),
                // Smooth North-West sweep through Amala Nagar -> Peramangalam -> Mundur -> Choondal -> Guruvayur
                Vec3(-610.0f, 0.05f, 795.0f),
                Vec3(-730.0f, 0.05f, 845.0f),     // Amala Nagar
                Vec3(-920.0f, 0.05f, 895.0f),
                Vec3(-1160.0f, 0.05f, 940.0f),    // Peramangalam
                Vec3(-1440.0f, 0.05f, 985.0f),
                Vec3(-1740.0f, 0.05f, 1025.0f),   // Mundur
                Vec3(-2040.0f, 0.05f, 1068.0f),
                Vec3(-2310.0f, 0.05f, 1112.0f),   // Choondal
                Vec3(-2510.0f, 0.05f, 1154.0f),
                Vec3(-2612.0f, 0.05f, 1180.0f),
                Vec3(-2666.0f, 0.05f, 1196.0f)    // Clean T-Junction into Guruvayur East Nada Avenue (56m South of Temple!)
            )
            val sh69Engineered = engineerHighwaySpline(sh69RawPts, closed = false).map { pt ->
                val ax = if (pt.z in 485f..735f && pt.x in -575f..-545f) Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X else pt.x
                Vec3(ax, 0.05f, pt.z)
            }.toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR",
                    name = "SH 69 • Thrissur - Punkunnam - Muthuvara LC - Amala Nagar - Guruvayur",
                    roadType = "state_highway",
                    widthM = 10.6f,
                    lanes = 2,
                    divided = false,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 27800.0f,
                    steps = listOf(
                        "West Fort Junction (SH 69 Start)",
                        "Punkunnam & Puzhakkal Highway",
                        "Muthuvara Animated Railway Level Crossing (LC-14)",
                        "Amala Nagar, Mundur & Choondal",
                        "Guruvayur East Nada & Bus Terminal Junction"
                    ),
                    smoothedPts = sh69Engineered,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR")
                )
            )
        }

        // Zone 5: NH 66 South Coastal Continuation (Triprayar -> Canoli Canal Bridge & Sree Rama Temple Ghat
        //         -> Mathilakam -> Kodungallur -> Monumental Kottapuram NW-3 Bridge -> Moothakunnam / Ernakulam Border)
        //         Welded seamlessly via continuationPairs onto ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR at (-2220.0, -2470.0)!
        if (specs.none { it.id == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" }) {
            val z5RawPts = listOf(
                // Dead-straight continuation South from Triprayar Terminus across Canoli Canal Bridge (X = -2220.0, Z = -2470..-2775)
                Vec3(-2220.0f, 0.05f, -2470.0f),
                Vec3(-2220.0f, 0.05f, -2505.0f),
                Vec3(-2220.0f, 0.05f, -2540.0f),
                Vec3(-2220.0f, 0.05f, -2565.0f),
                Vec3(-2220.0f, 0.05f, -2590.0f),
                Vec3(-2220.0f, 0.05f, -2615.0f),
                Vec3(-2220.0f, 0.05f, -2640.0f),
                Vec3(-2220.0f, 0.05f, -2660.0f),
                Vec3(-2220.0f, 0.05f, -2685.0f),
                Vec3(-2220.0f, 0.05f, -2710.0f),
                Vec3(-2220.0f, 0.05f, -2735.0f),
                Vec3(-2220.0f, 0.05f, -2765.0f),
                Vec3(-2220.0f, 0.05f, -2795.0f),
                // Sweeping NH 66 Coastal Highway through Mathilakam -> S.N. Puram -> Kodungallur Approach
                Vec3(-2198.0f, 0.05f, -2865.0f),
                Vec3(-2140.0f, 0.05f, -2965.0f),
                Vec3(-2045.0f, 0.05f, -3085.0f),
                Vec3(-1935.0f, 0.05f, -3210.0f),
                Vec3(-1815.0f, 0.05f, -3335.0f),
                Vec3(-1700.0f, 0.05f, -3455.0f),
                Vec3(-1605.0f, 0.05f, -3565.0f),
                Vec3(-1545.0f, 0.05f, -3655.0f),
                Vec3(-1522.0f, 0.05f, -3725.0f),
                // Dead-straight North-South alignment across Monumental Kottapuram NW-3 Bridge (X = -1520.0, Z = -3765..-4465)
                Vec3(-1520.0f, 0.05f, -3765.0f),
                Vec3(-1520.0f, 0.05f, -3800.0f),
                Vec3(-1520.0f, 0.05f, -3840.0f),
                Vec3(-1520.0f, 0.05f, -3880.0f),
                Vec3(-1520.0f, 0.05f, -3935.0f),
                Vec3(-1520.0f, 0.05f, -3990.0f),
                Vec3(-1520.0f, 0.05f, -4050.0f),
                Vec3(-1520.0f, 0.05f, -4110.0f),
                Vec3(-1520.0f, 0.05f, -4165.0f),
                Vec3(-1520.0f, 0.05f, -4220.0f),
                Vec3(-1520.0f, 0.05f, -4260.0f),
                Vec3(-1520.0f, 0.05f, -4300.0f),
                Vec3(-1520.0f, 0.05f, -4340.0f),
                Vec3(-1520.0f, 0.05f, -4390.0f),
                Vec3(-1520.0f, 0.05f, -4455.0f)
            )
            val z5Smoothed = engineerHighwaySpline(z5RawPts, closed = false)
            val z5Dense = ArrayList<Vec3>(z5Smoothed.size + 120)
            for (k in 0 until z5Smoothed.size) {
                val a = z5Smoothed[k]
                z5Dense.add(a)
                if (k < z5Smoothed.size - 1) {
                    val b = z5Smoothed[k + 1]
                    val midZ = (a.z + b.z) * 0.5f
                    val inBridge = midZ in -2780f..-2540f || midZ <= -3760f
                    val maxStep = if (inBridge) 7.5f else 999f
                    val segLen = hypot(b.x - a.x, b.z - a.z)
                    if (segLen > maxStep + 0.4f) {
                        val sub = kotlin.math.ceil(segLen / maxStep).toInt().coerceIn(2, 24)
                        for (s in 1 until sub) {
                            z5Dense.add(lerp(a, b, s.toFloat() / sub.toFloat()))
                        }
                    }
                }
            }
            val cxCan = Zone5Nh66CoastalRenderer.CANOLI_CENTER_X
            val cxKot = Zone5Nh66CoastalRenderer.KOTTAPURAM_CENTER_X
            val z5Engineered = z5Dense.map { pt ->
                val z = pt.z
                val alignedX = when {
                    z >= -2780.0f -> cxCan
                    z > -2850.0f -> {
                        val u = ((z - (-2850.0f)) / 70.0f).coerceIn(0f, 1f)
                        val w = u * u * (3f - 2f * u)
                        pt.x + (cxCan - pt.x) * w
                    }
                    z <= -3755.0f -> cxKot
                    z < -3685.0f -> {
                        val u = ((-3685.0f - z) / 70.0f).coerceIn(0f, 1f)
                        val w = u * u * (3f - 2f * u)
                        pt.x + (cxKot - pt.x) * w
                    }
                    else -> pt.x
                }
                Vec3(alignedX, ShoranurRailwaySystem.computeRoadSurfaceY(alignedX, z) - 0.01f, z)
            }.toMutableList()
            specs.add(
                MutableRoadSpec(
                    idx = specs.size,
                    id = "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM",
                    name = "NH 66 Coastal 6-Lane • Triprayar Canoli Bridge - Kodungallur - Kottapuram NW-3",
                    roadType = "national_highway",
                    widthM = 22.0f,
                    lanes = 6,
                    divided = true,
                    closedLoop = false,
                    oneWayClockwise = false,
                    realDist = 22400.0f,
                    steps = listOf(
                        "Triprayar Canoli Canal Bridge & Sree Rama Temple Water Ghat",
                        "NH 66 Coastal Highway • Mathilakam & S.N. Puram",
                        "Kodungallur Bypass Approach",
                        "Kottapuram / Kodungallur NW-3 High-Clearance Bridge (Periyar Lagoon)",
                        "Moothakunnam • Ernakulam District Border"
                    ),
                    smoothedPts = z5Engineered,
                    baseFrames = mutableListOf(),
                    priority = roadPriority("ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM")
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
                val midJoint = if (idA == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" && idB == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY") {
                    Vec3(999.5f, ptsA.last().y, -1890.0f)
                } else if (idA == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" && idB == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM") {
                    Vec3(-2220.0f, ptsA.last().y, -2470.0f)
                } else {
                    Vec3(
                        (ptsA.last().x + ptsB.first().x) * 0.35f + (pPrev.x + pNext.x) * 0.15f,
                        ptsA.last().y,
                        (ptsA.last().z + ptsB.first().z) * 0.35f + (pPrev.z + pNext.z) * 0.15f
                    )
                }
                ptsA[ptsA.size - 1] = midJoint
                ptsB[0] = midJoint
                // Also snap NH544 South & North legs if they share the Mannuthy 4-Way joint
                if (idA == "ROAD_SWARAJ_TO_MANNUTHY") {
                    for (nhId in arrayOf("ROAD_NH544_MANNUTHY_TO_PALIYEKKARA", "ROAD_NH544_MANNUTHY_TO_KUTHIRAN")) {
                        specById[nhId]?.let { nh ->
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
                // Also snap SH 69 start onto exact West Fort 3-Way Junction node!
                if (idA == "ROAD_SWARAJ_TO_WEST_FORT") {
                    specById["ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR"]?.let { sh69 ->
                        if (sh69.smoothedPts.isNotEmpty()) {
                            val d = midJoint - sh69.smoothedPts[0]
                            for (k in 0 until min(5, sh69.smoothedPts.size)) {
                                val w = (1f - k * 0.22f).coerceAtLeast(0f)
                                sh69.smoothedPts[k] = sh69.smoothedPts[k] + d * w
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
                spec.id == "ROAD_VADAKKE_STAND_EXIT" ||
                spec.id == "ROAD_RAILWAY_STATION_EXIT" ||
                spec.id == "ROAD_SHORANUR_TERMINAL_EXIT" ||
                spec.id == "ROAD_SHORANUR_STATION_EXIT" ||
                spec.id == "ROAD_PEECHI_DAM_EXIT" ||
                spec.id == "ROAD_NH544_KUTHIRAN_LEFT_TUBE" ||
                spec.id == "ROAD_NH544_KUTHIRAN_RIGHT_TUBE" ||
                spec.id == "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD" ||
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
                spec.id == "ROAD_SWARAJ_TO_VADAKKE" ||
                spec.id == "ROAD_RAILWAY_STATION_ACCESS" ||
                spec.id == "ROAD_THRISSUR_TO_SHORANUR" ||
                spec.id == "ROAD_SHORANUR_TERMINAL_ENTRY" ||
                spec.id == "ROAD_SHORANUR_STATION_ENTRY" ||
                spec.id == "ROAD_PEECHI_DAM_ENTRY" ||
                spec.id == "ROAD_NH544_MANNUTHY_TO_KUTHIRAN" ||
                spec.id == "ROAD_NH544_KUTHIRAN_LEFT_TUBE" ||
                spec.id == "ROAD_NH544_KUTHIRAN_RIGHT_TUBE" ||
                spec.id == "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD" ||
                spec.id == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY" ||
                spec.id == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" ||
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

            val isPaliyekkaraJoint = idA == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" &&
                idB == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY"
            val blendSteps = 5
            for (k in 0 until blendSteps) {
                val t = 1f - (k.toFloat() / blendSteps.toFloat())
                // Blend end of road A (preserve exact cosine S-curve halfWidthM at Paliyekkara Toll South flare!)
                val idxA = fA.size - 1 - k
                val oldA = fA[idxA]
                val tangA = if (k == 0) sharedTangent else (oldA.tangent * (1f - t) + sharedTangent * t).normalizedXZ()
                val rightA = if (k == 0) sharedRight else Vec3(tangA.z, 0f, -tangA.x)
                val hwA = when {
                    k == 0 -> sharedHalfW
                    isPaliyekkaraJoint -> oldA.halfWidthM
                    else -> oldA.halfWidthM * (1f - t) + sharedHalfW * t
                }
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
                val hwB = when {
                    k == 0 -> sharedHalfW
                    isPaliyekkaraJoint -> oldB.halfWidthM
                    else -> oldB.halfWidthM * (1f - t) + sharedHalfW * t
                }
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
                if (isStart && (branch.id == "ROAD_SAKTHAN_TO_SWARAJ" || branch.id == "ROAD_VADAKKE_STAND_EXIT" || branch.id == "ROAD_RAILWAY_STATION_EXIT" || branch.id == "ROAD_SHORANUR_TERMINAL_EXIT" || branch.id == "ROAD_SHORANUR_STATION_EXIT" || branch.id == "ROAD_PEECHI_DAM_EXIT" || branch.id == "ROAD_NH544_KUTHIRAN_LEFT_TUBE" || branch.id == "ROAD_NH544_KUTHIRAN_RIGHT_TUBE" || branch.id == "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD" || continuationPairs.any { it.second == branch.id })) continue
                if (!isStart && (branch.id == "ROAD_SAKTHAN_STAND_ENTRY" || branch.id == "ROAD_SWARAJ_TO_VADAKKE" || branch.id == "ROAD_RAILWAY_STATION_ACCESS" || branch.id == "ROAD_THRISSUR_TO_SHORANUR" || branch.id == "ROAD_SHORANUR_TERMINAL_ENTRY" || branch.id == "ROAD_SHORANUR_STATION_ENTRY" || branch.id == "ROAD_PEECHI_DAM_ENTRY" || branch.id == "ROAD_NH544_MANNUTHY_TO_KUTHIRAN" || branch.id == "ROAD_NH544_KUTHIRAN_LEFT_TUBE" || branch.id == "ROAD_NH544_KUTHIRAN_RIGHT_TUBE" || branch.id == "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD" || branch.id == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY" || branch.id == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" || continuationPairs.any { it.first == branch.id })) continue

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

                if ((branch.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" || branch.id == "ROAD_NH544_MANNUTHY_TO_KUTHIRAN") && isStart) {
                    val westTrunk = specById["ROAD_SWARAJ_TO_MANNUTHY"]!!
                    val eastTrunk = specById["ROAD_MANNUTHY_TO_PEECHI_DAM"]!!
                    val wFrames = westTrunk.baseFrames
                    val eFrames = eastTrunk.baseFrames
                    val wBeforeIdx = stepAlongFrames(wFrames, wFrames.size - 1, -1, flareDistAlongTrunk, false)
                    val eAfterIdx = stepAlongFrames(eFrames, 0, +1, flareDistAlongTrunk, false)
                    tBeforeFrame = wFrames[wBeforeIdx]
                    tAfterFrame = eFrames[eAfterIdx]
                    val sideSgn = if (branch.id == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA") 1f else -1f
                    beforeSideSign = sideSgn
                    afterSideSign = sideSgn
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
        // Fast 2D spatial hash grid sweep (O(N) instead of O(N^2)) so any outer edge of Swaraj Round or a trunk road that crosses a branch road is 100% covered
        val cellSize = 24.0f
        val invCellSize = 1f / cellSize
        data class IndexedFrame(val x: Float, val z: Float, val halfW: Float, val roadId: String, val priority: Int)
        val grid = HashMap<Long, ArrayList<IndexedFrame>>(4096)
        fun cellKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

        for (other in specs) {
            val oId = other.id
            val oPri = other.priority
            for (of in other.baseFrames) {
                val cx = kotlin.math.floor(of.pos.x * invCellSize).toInt()
                val cz = kotlin.math.floor(of.pos.z * invCellSize).toInt()
                val key = cellKey(cx, cz)
                val bucket = grid.getOrPut(key) { ArrayList(8) }
                bucket.add(IndexedFrame(of.pos.x, of.pos.z, of.halfWidthM, oId, oPri))
            }
        }

        for (spec in specs) {
            val lMask = leftCoveredMask[spec.id]!!
            val rMask = rightCoveredMask[spec.id]!!
            val sId = spec.id
            val sPri = spec.priority
            val isSwaraj = sId == "ROAD_SWARAJ_ROUND"
            for (i in spec.baseFrames.indices) {
                val f = spec.baseFrames[i]
                val off = f.halfWidthM + 0.5f
                val lx = f.pos.x - f.right.x * off
                val lz = f.pos.z - f.right.z * off
                val rx = f.pos.x + f.right.x * off
                val rz = f.pos.z + f.right.z * off

                val cxCenter = kotlin.math.floor(f.pos.x * invCellSize).toInt()
                val czCenter = kotlin.math.floor(f.pos.z * invCellSize).toInt()

                var lHit = lMask[i]
                var rHit = isSwaraj || rMask[i]
                for (dxCell in -1..1) {
                    if (lHit && rHit) break
                    for (dzCell in -1..1) {
                        if (lHit && rHit) break
                        val bucket = grid[cellKey(cxCenter + dxCell, czCenter + dzCell)] ?: continue
                        for (bIdx in 0 until bucket.size) {
                            val of = bucket[bIdx]
                            if (of.priority <= sPri || of.roadId == sId) continue
                            val clearR = of.halfW + 3.8f
                            val clearRSq = clearR * clearR
                            if (!lHit) {
                                val dlx = lx - of.x
                                val dlz = lz - of.z
                                if (dlx * dlx + dlz * dlz < clearRSq) {
                                    lMask[i] = true
                                    lHit = true
                                }
                            }
                            if (!rHit) {
                                val drx = rx - of.x
                                val drz = rz - of.z
                                if (drx * drx + drz * drz < clearRSq) {
                                    rMask[i] = true
                                    rHit = true
                                }
                            }
                            if (lHit && rHit) break
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
                finalFrames.add(
                    f.copy(
                        leftEdgeCovered = lMask[i],
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
        var bestDistSq = Float.MAX_VALUE
        var bestQx = p.x
        var bestQy = p.y
        var bestQz = p.z
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
            val dx = p.x - qx
            val dz = p.z - qz
            val dSq = dx * dx + dz * dz
            if (dSq < bestDistSq) {
                bestDistSq = dSq
                bestQx = qx
                bestQy = a.y
                bestQz = qz
            }
        }
        return Pair(kotlin.math.sqrt(bestDistSq), Vec3(bestQx, bestQy, bestQz))
    }

    private fun closestPointOnFramesXZ(p: Vec3, frames: List<RoadFrame>): Float {
        var bestDistSq = Float.MAX_VALUE
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
            val dx = p.x - qx
            val dz = p.z - qz
            val dSq = dx * dx + dz * dz
            if (dSq < bestDistSq) bestDistSq = dSq
        }
        return kotlin.math.sqrt(bestDistSq)
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
                if ((pts[i].z in -210f..-125f && pts[i].x in -5f..36f) ||
                    (pts[i].z >= 186f && pts[i].x in -58f..-10f &&
                        (roadId == "ROAD_SWARAJ_TO_VADAKKE" || roadId == "ROAD_VADAKKE_STAND_EXIT")) ||
                    (pts[i].x <= -54f &&
                        (roadId == "ROAD_RAILWAY_STATION_ACCESS" || roadId == "ROAD_RAILWAY_STATION_EXIT")) ||
                    roadId == "ROAD_SHORANUR_TERMINAL_ENTRY" || roadId == "ROAD_SHORANUR_TERMINAL_EXIT" ||
                    roadId == "ROAD_SHORANUR_STATION_ENTRY" || roadId == "ROAD_SHORANUR_STATION_EXIT" ||
                    roadId == "ROAD_PEECHI_DAM_ENTRY" || roadId == "ROAD_PEECHI_DAM_EXIT" ||
                    (roadId == "ROAD_THRISSUR_TO_SHORANUR" && (pts[i].z in 1500f..1860f || pts[i].z in 2610f..2970f)) ||
                    (roadId == "ROAD_WEST_FORT_TO_KANJANI" && pts[i].x in -1765f..-1525f) ||
                    (roadId == "ROAD_SH69_THRISSUR_MUTHUVARA_GURUVAYUR" && pts[i].z in 520f..700f) ||
                    ((roadId == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" || roadId == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY") &&
                        (pts[i].z in -1940f..-1670f || pts[i].z in -2405f..-2095f || pts[i].z <= -4815f)) ||
                    (roadId == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" &&
                        (pts[i].z in 15f..415f || pts[i].z in -2015f..-1665f || pts[i].z <= -2335f)) ||
                    (roadId == "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" &&
                        (pts[i].z >= -2780f || pts[i].z <= -3755f))
                ) {
                    rawCurveExtra[i] = 0f
                    continue
                }
                val iBack = (i - 3).coerceAtLeast(0)
                val iFwd = (i + 3).coerceAtMost(n - 1)
                val dot = smoothTangents[iBack].dotXZ(smoothTangents[iFwd]).coerceIn(-1f, 1f)
                val turnAngleRad = acos(dot)
                val maxExtra = if (roadId == "ROAD_SAKTHAN_TO_SWARAJ" && pts[i].z < -118f) 1.6f else 3.4f
                rawCurveExtra[i] = (turnAngleRad * 6.2f).coerceIn(0f, maxExtra)
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

            // Exact gate-width alignment at Sakthan, Vadakke & Thrissur Railway Station ENTRY / EXIT Portals:
            val px = pts[i].x
            val pz = pts[i].z
            if (roadId == "ROAD_SAKTHAN_TO_SWARAJ" && px in 18.0f..35.0f && pz < -170.0f) {
                val wMix = if (pz >= -214.0f) 1f else (1f - ((-214.0f - pz) / 10.0f).coerceIn(0f, 1f))
                frameHalfW = 5.6f * wMix + frameHalfW * (1f - wMix)
            } else if (roadId == "ROAD_SAKTHAN_STAND_ENTRY" && pz < -116.0f) {
                val gateBlend = ((pz - (-128.0f)) / 12.0f).coerceIn(0f, 1f)
                frameHalfW = 4.6f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if (roadId == "ROAD_SWARAJ_TO_VADAKKE" && pz > 184.0f) {
                val gateBlend = ((195.0f - pz) / 11.0f).coerceIn(0f, 1f)
                frameHalfW = 4.9f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if (roadId == "ROAD_VADAKKE_STAND_EXIT" && pz > 188.0f) {
                val gateBlend = ((196.0f - pz) / 8.0f).coerceIn(0f, 1f)
                frameHalfW = 5.0f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if ((roadId == "ROAD_RAILWAY_STATION_ACCESS" || roadId == "ROAD_RAILWAY_STATION_EXIT") && px < -52.0f) {
                val gateBlend = ((px - (-59.0f)) / 7.0f).coerceIn(0f, 1f)
                frameHalfW = 4.9f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if ((roadId == "ROAD_SHORANUR_TERMINAL_ENTRY" || roadId == "ROAD_SHORANUR_TERMINAL_EXIT") && px > 118.0f) {
                val gateBlend = ((125.0f - px) / 7.0f).coerceIn(0f, 1f)
                frameHalfW = 4.9f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if ((roadId == "ROAD_SHORANUR_STATION_ENTRY" || roadId == "ROAD_SHORANUR_STATION_EXIT") && px < 82.0f) {
                val gateBlend = ((px - 75.0f) / 7.0f).coerceIn(0f, 1f)
                frameHalfW = 4.9f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if ((roadId == "ROAD_PEECHI_DAM_ENTRY" || roadId == "ROAD_PEECHI_DAM_EXIT") && px > 3404.0f) {
                val gateBlend = ((3411.0f - px) / 7.0f).coerceIn(0f, 1f)
                frameHalfW = 4.9f * (1f - gateBlend) + frameHalfW * gateBlend
            } else if ((roadId == "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" || roadId == "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY") &&
                pz in Zone3Nh544SouthRenderer.TOLL_FLARE_SOUTH_END_Z..Zone3Nh544SouthRenderer.TOLL_FLARE_NORTH_START_Z
            ) {
                // Smooth 92m Cosine S-curve Paliyekkara Toll Plaza bellmouth flare (12m -> 40.2m -> 10m)
                frameHalfW = Zone3Nh544SouthRenderer.paliyekkaraFlareHalfWidthAtZ(pz)
            } else if (roadId == "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" &&
                pz in Zone5Nh66CoastalRenderer.NATTIKA_FLARE_SOUTH_END_Z..Zone5Nh66CoastalRenderer.NATTIKA_FLARE_NORTH_START_Z
            ) {
                // Smooth 92m Cosine S-curve Nattika 10-Lane Toll Plaza bellmouth flare (9.0m -> 34.8m -> 9.0m)
                frameHalfW = Zone5Nh66CoastalRenderer.nattikaFlareHalfWidthAtZ(pz)
            }

            out.add(RoadFrame(pts[i], t, r, dist, frameHalfW, Float.MAX_VALUE))
        }
        return out
    }
}
