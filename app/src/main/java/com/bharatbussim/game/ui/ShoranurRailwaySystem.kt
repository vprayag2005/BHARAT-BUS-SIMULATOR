package com.bharatbussim.game.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bharatbussim.game.model.Vec3
import java.io.BufferedInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Thrissur - Wadakkanchery - Shoranur 2-Track Southern Railway Corridor,
 * Real Google Drive 3D Locomotive/Coach/Track Asset Loader, Animated 7-Coach Trains,
 * Southern Railway Elevated Rail Viaducts (Poothole & Mullurkara RUBs), and
 * Wadakkanchery Railway Overbridge (ROB) + Town 3D Architecture.
 *
 * - Real-world map alignment:
 *   - Starts at Thrissur Railway Station (TCR) at its real SW Thrissur location (X=-112.0m, Z=-260..-98m),
 *   - Rises smoothly onto the Poothole Southern Railway Elevated Viaduct (deck Y=6.38m, soffit Y=5.86m)
 *     to cross cleanly OVER flat M.G. Road / Kanjani Route (Y=0.06m, ZERO road bridge on Kanjani Rd!),
 *   - Descends to ground level (Y=0.04m) West of Swaraj Round & Vadakke Stand (Punkunnam corridor),
 *   - Passes directly UNDERNEATH Wadakkanchery ROB (SH 22 bridge deck Y=7.40m at X=92.0m, Z=1680.0m),
 *   - Rises smoothly onto the Mullurkara Southern Railway Elevated Viaduct (deck Y=6.45m, soffit Y=5.92m)
 *     to cross cleanly OVER flat SH 22 (Y=0.06m at Z=2420m) onto the West side of SH 22,
 *   - Continues at ground level straight into Shoranur Junction Station Platforms 1 & 2 (X=8.6m, Z=3140..3440m).
 * - ZERO ground-level road–rail crossings anywhere on the map!
 * - Track 1 (West / Northbound: Thrissur -> Shoranur): WAG-9 Locomotive + 7x Coach 1 (`coach_1`).
 * - Track 2 (East / Southbound: Shoranur -> Thrissur): WAG-9 Locomotive + 7x Coach 2 (`coach_2`).
 */
internal object ShoranurRailwaySystem {

    const val MODEL_TRACK = 0
    const val MODEL_WAG9 = 1
    const val MODEL_COACH_1 = 2
    const val MODEL_COACH_2 = 3

    // Broad-gauge Indian Railways double-line center-to-center spacing (5.20 m)
    const val TRACK_HALF_SEP = 2.60f

    // Wadakkanchery Railway Overbridge (ROB) exact geometric anchor on SH 22
    const val ROB_CENTER_X = 92.0f
    const val ROB_RAMP_SOUTH_Z = 1520.0f
    const val ROB_DECK_SOUTH_Z = 1630.0f
    const val ROB_TRACK_CROSS_Z = 1680.0f
    const val ROB_DECK_NORTH_Z = 1730.0f
    const val ROB_RAMP_NORTH_Z = 1840.0f
    const val ROB_DECK_TOP_Y = 7.40f
    const val ROB_DECK_HALF_W = 6.20f

    // Poothole Southern Railway Elevated Rail Viaduct (over flat M.G. Road / Kanjani Route at X=-112, Z=-16.8)
    const val POOTHOLE_VIADUCT_RAMP_S_Z = -98.0f
    const val POOTHOLE_VIADUCT_FLAT_S_Z = -28.0f
    const val POOTHOLE_VIADUCT_FLAT_N_Z = -6.0f
    const val POOTHOLE_VIADUCT_RAMP_N_Z = 76.0f
    const val POOTHOLE_VIADUCT_TOP_Y = 6.38f

    // Mullurkara Southern Railway Elevated Rail Viaduct (over flat SH 22 at X=96.5, Z=2420)
    const val MULLURKARA_VIADUCT_RAMP_S_Z = 2240.0f
    const val MULLURKARA_VIADUCT_FLAT_S_Z = 2386.0f
    const val MULLURKARA_VIADUCT_FLAT_N_Z = 2454.0f
    const val MULLURKARA_VIADUCT_RAMP_N_Z = 2600.0f
    const val MULLURKARA_VIADUCT_TOP_Y = 6.45f

    data class TrackSample(
        val x: Float,
        val y: Float,
        val z: Float,
        val yawRad: Float,
        val pitchRad: Float
    )

    data class RailwayInstance(
        val modelIndex: Int,
        val cx: Float,
        val baseY: Float,
        val cz: Float,
        val yawRad: Float,
        val scale: Float = 1.0f,
        val pitchRad: Float = 0.0f
    )

    internal data class TrackNode(
        val x: Float,
        val y: Float,
        val z: Float,
        val tx: Float,
        val ty: Float,
        val tz: Float,
        val rx: Float,
        val rz: Float,
        val dist: Float
    )

    @Volatile
    private var cachedModels: List<Zone1GDriveEnvironmentRenderer.LoadedGDriveModel>? = null

    @Volatile
    private var cachedTrackNodes: List<TrackNode>? = null

    @Volatile
    private var cachedTrackTileInstances: List<RailwayInstance>? = null

    /**
     * Exact O(1) road surface elevation along Wadakkanchery ROB (SH 22) and all Zone 3 NH 544 South bridges
     * (Ollur ROB, Kurumali River Bridge, and 280m Chalakudy River Bridge), returning 0.06f on all flat roads.
     */
    fun computeRoadSurfaceY(x: Float, z: Float): Float {
        var y = 0.06f
        if (z in ROB_RAMP_SOUTH_Z..ROB_RAMP_NORTH_Z && abs(x - ROB_CENTER_X) <= 14.5f) {
            val profile = robElevationProfile(z)
            y = max(y, 0.06f + profile * (ROB_DECK_TOP_Y - 0.06f))
        }
        val zone3Y = Zone3Nh544SouthRenderer.computeZone3BridgeSurfaceY(x, z)
        if (zone3Y > y) y = zone3Y
        val zone4Y = Zone4KoleRailHeritageRenderer.computeZone4RoadSurfaceY(x, z)
        if (zone4Y > y) y = zone4Y
        val zone5Y = Zone5Nh66CoastalRenderer.computeZone5RoadSurfaceY(x, z)
        if (zone5Y > y) y = zone5Y
        return y
    }

    /**
     * Normalized 0..1 smooth C1 elevation profile for Wadakkanchery ROB (for both road spline & bus physics).
     */
    fun robElevationProfile(z: Float): Float {
        return when {
            z <= ROB_RAMP_SOUTH_Z -> 0f
            z >= ROB_RAMP_NORTH_Z -> 0f
            z in ROB_DECK_SOUTH_Z..ROB_DECK_NORTH_Z -> 1f
            z < ROB_DECK_SOUTH_Z -> {
                val u = ((z - ROB_RAMP_SOUTH_Z) / (ROB_DECK_SOUTH_Z - ROB_RAMP_SOUTH_Z)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
            else -> {
                val u = ((ROB_RAMP_NORTH_Z - z) / (ROB_RAMP_NORTH_Z - ROB_DECK_NORTH_Z)).coerceIn(0f, 1f)
                0.5f - 0.5f * cos(u * PI.toFloat())
            }
        }
    }

    /**
     * True directional forward slope along ANY elevated bridge or embankment (North-South OR East-West,
     * including Wadakkanchery ROB, Ollur ROB, Kurumali, Chalakudy, SH 75 Kole Wetlands, Enamavu Sluice Bridge,
     * and Bharathappuzha Modern Bridge) for authentic bus pitch!
     */
    fun computeRoadForwardSlope(x: Float, z: Float, headingRad: Float): Float {
        val fwdX = sin(headingRad)
        val fwdZ = cos(headingRad)
        val yBack = computeRoadSurfaceY(x - fwdX, z - fwdZ)
        val yFwd = computeRoadSurfaceY(x + fwdX, z + fwdZ)
        return (yFwd - yBack) * 0.5f
    }

    fun computeRoadSlopeDz(x: Float, z: Float): Float {
        val y0 = computeRoadSurfaceY(x, z - 1.0f)
        val y1 = computeRoadSurfaceY(x, z + 1.0f)
        return (y1 - y0) * 0.5f
    }

    /**
     * Smooth C1 elevation profile for the 2-track railway line itself across the two elevated
     * Southern Railway viaducts (Poothole over flat M.G. Road and Mullurkara over flat SH 22).
     */
    fun computeRailTrackY(z: Float): Float {
        var y = 0.04f
        if (z in POOTHOLE_VIADUCT_RAMP_S_Z..POOTHOLE_VIADUCT_RAMP_N_Z) {
            val p = when {
                z in POOTHOLE_VIADUCT_FLAT_S_Z..POOTHOLE_VIADUCT_FLAT_N_Z -> 1f
                z < POOTHOLE_VIADUCT_FLAT_S_Z -> {
                    val u = ((z - POOTHOLE_VIADUCT_RAMP_S_Z) / (POOTHOLE_VIADUCT_FLAT_S_Z - POOTHOLE_VIADUCT_RAMP_S_Z)).coerceIn(0f, 1f)
                    0.5f - 0.5f * cos(u * PI.toFloat())
                }
                else -> {
                    val u = ((POOTHOLE_VIADUCT_RAMP_N_Z - z) / (POOTHOLE_VIADUCT_RAMP_N_Z - POOTHOLE_VIADUCT_FLAT_N_Z)).coerceIn(0f, 1f)
                    0.5f - 0.5f * cos(u * PI.toFloat())
                }
            }
            y = max(y, 0.04f + p * (POOTHOLE_VIADUCT_TOP_Y - 0.04f))
        }
        if (z in MULLURKARA_VIADUCT_RAMP_S_Z..MULLURKARA_VIADUCT_RAMP_N_Z) {
            val p = when {
                z in MULLURKARA_VIADUCT_FLAT_S_Z..MULLURKARA_VIADUCT_FLAT_N_Z -> 1f
                z < MULLURKARA_VIADUCT_FLAT_S_Z -> {
                    val u = ((z - MULLURKARA_VIADUCT_RAMP_S_Z) / (MULLURKARA_VIADUCT_FLAT_S_Z - MULLURKARA_VIADUCT_RAMP_S_Z)).coerceIn(0f, 1f)
                    0.5f - 0.5f * cos(u * PI.toFloat())
                }
                else -> {
                    val u = ((MULLURKARA_VIADUCT_RAMP_N_Z - z) / (MULLURKARA_VIADUCT_RAMP_N_Z - MULLURKARA_VIADUCT_FLAT_N_Z)).coerceIn(0f, 1f)
                    0.5f - 0.5f * cos(u * PI.toFloat())
                }
            }
            y = max(y, 0.04f + p * (MULLURKARA_VIADUCT_TOP_Y - 0.04f))
        }
        return y
    }

    /**
     * Returns camera ceiling clearance height under overhead Southern Railway viaducts so the
     * chase camera smoothly dips underneath the deck girder without clipping when driving under them.
     */
    fun computeOverheadViaductClearanceY(x: Float, z: Float): Float {
        // Poothole Rail Viaduct over flat M.G. Road / Kanjani Route
        if (x in -121.0f..-103.0f && z in -30.0f..-4.0f) {
            return 5.45f
        }
        // Mullurkara Rail Viaduct over flat SH 22
        if (x in 84.0f..109.0f && z in 2394.0f..2446.0f) {
            return 5.50f
        }
        return 1000f
    }

    fun ensureLoaded(context: Context): List<Zone1GDriveEnvironmentRenderer.LoadedGDriveModel> {
        return cachedModels ?: synchronized(this) {
            cachedModels ?: try {
                context.assets.open("shoranur_railway_meshes.bin").use { raw ->
                    BufferedInputStream(raw, 65536).use { input ->
                        Zone1GDriveEnvironmentRenderer.parseBinaryMeshes(input.readBytes())
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }.also { cachedModels = it }
        }
    }

    fun getLoadedModels(): List<Zone1GDriveEnvironmentRenderer.LoadedGDriveModel> = cachedModels ?: emptyList()

    /**
     * Master 2-track 3D Southern Railway centerline spline running strictly along the real-world
     * Thrissur (SW) -> Poothole Viaduct -> Punkunnam -> Wadakkanchery ROB -> Mullurkara Viaduct -> Shoranur corridor:
     * ZERO ground-level road crossings anywhere on the map!
     */
    internal fun getOrBuildTrackNodes(): List<TrackNode> {
        cachedTrackNodes?.let { return it }

        val ctrlPts = listOf(
            Pair(-112.0f, -260.0f),    // South of Thrissur Station (toward Ollur / Ernakulam)
            Pair(-112.0f, -212.0f),
            Pair(-112.0f, -154.0f),    // Straight through Thrissur Railway Station Platforms 1 & 2 (Z=-222..-98)
            Pair(-112.0f, -98.0f),     // North end of Platform 1 & 2 -> begins smooth Poothole viaduct climb
            Pair(-112.0f, -52.0f),     // Climbing smoothly on Poothole Rail Viaduct
            Pair(-112.0f, -16.8f),     // Crossing OVER flat M.G. Road / Kanjani Route (rail Y=6.38m, soffit 5.86m!)
            Pair(-112.0f, 22.0f),      // Descending smoothly North of M.G. Road (40m West of Swaraj Round!)
            Pair(-112.0f, 84.0f),      // Back on ground level (Y=0.04m)
            Pair(-112.0f, 215.0f),     // Running parallel WEST of Vadakke Stand (47m West of Vadakke Stand!)
            Pair(-110.0f, 340.0f),     // Punkunnam rail corridor
            Pair(-96.0f, 500.0f),
            Pair(-68.0f, 700.0f),
            Pair(-32.0f, 920.0f),
            Pair(8.0f, 1140.0f),
            Pair(40.0f, 1340.0f),
            Pair(56.0f, 1500.0f),
            Pair(68.0f, 1600.0f),
            Pair(80.0f, 1650.0f),
            Pair(92.0f, 1680.0f),      // Directly UNDERNEATH Wadakkanchery ROB (road deck Y=7.40m, rail Y=0.04m!)
            Pair(112.0f, 1720.0f),
            Pair(138.0f, 1785.0f),
            Pair(155.0f, 1875.0f),     // East of SH 22 past Wadakkanchery Town (well clear of buildings at X=101..125)
            Pair(158.0f, 2020.0f),
            Pair(152.0f, 2180.0f),     // Mullurkara South approach
            Pair(130.0f, 2310.0f),     // Climbing smoothly on Mullurkara Rail Viaduct
            Pair(96.5f, 2420.0f),      // Crossing OVER flat SH 22 (rail Y=6.45m, soffit 5.92m!)
            Pair(62.0f, 2530.0f),      // Descending smoothly on West side of SH 22
            Pair(38.0f, 2680.0f),      // Back on ground level (Y=0.04m) West of SH 22
            Pair(22.0f, 2860.0f),
            Pair(12.0f, 3020.0f),
            Pair(8.6f, 3140.0f),       // Shoranur Junction South throat
            Pair(8.6f, 3238.0f),       // Shoranur Junction Station West Platforms (Station starts at X=16m)
            Pair(8.6f, 3330.0f),
            Pair(8.6f, 3440.0f)        // North towards Palakkad / Kozhikode
        )

        // Subdivide with smooth Catmull-Rom interpolation every ~5m on viaducts and ~9m on flats for silky 3D grades
        val dense = ArrayList<Vec3>(700)
        val nCtrl = ctrlPts.size
        for (i in 0 until nCtrl - 1) {
            val p0 = ctrlPts[max(0, i - 1)]
            val p1 = ctrlPts[i]
            val p2 = ctrlPts[i + 1]
            val p3 = ctrlPts[min(nCtrl - 1, i + 2)]
            val segLen = hypot(p2.first - p1.first, p2.second - p1.second)
            val isNearViaduct = (p1.second in -120f..100f || p2.second in -120f..100f) ||
                (p1.second in 2200f..2640f || p2.second in 2200f..2640f)
            val targetStep = if (isNearViaduct) 4.5f else 9.0f
            val steps = max(2, (segLen / targetStep).toInt())
            for (s in 0 until steps) {
                val t = s.toFloat() / steps.toFloat()
                val t2 = t * t
                val t3 = t2 * t
                val x = 0.5f * ((2f * p1.first) + (-p0.first + p2.first) * t +
                    (2f * p0.first - 5f * p1.first + 4f * p2.first - p3.first) * t2 +
                    (-p0.first + 3f * p1.first - 3f * p2.first + p3.first) * t3)
                val z = 0.5f * ((2f * p1.second) + (-p0.second + p2.second) * t +
                    (2f * p0.second - 5f * p1.second + 4f * p2.second - p3.second) * t2 +
                    (-p0.second + 3f * p1.second - 3f * p2.second + p3.second) * t3)
                dense.add(Vec3(x, computeRailTrackY(z), z))
            }
        }
        val lastPt = ctrlPts.last()
        dense.add(Vec3(lastPt.first, computeRailTrackY(lastPt.second), lastPt.second))

        val nodes = ArrayList<TrackNode>(dense.size)
        var cumDist = 0f
        for (i in dense.indices) {
            val cur = dense[i]
            if (i > 0) {
                val prevPt = dense[i - 1]
                val ddx = cur.x - prevPt.x
                val ddy = cur.y - prevPt.y
                val ddz = cur.z - prevPt.z
                cumDist += kotlin.math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz)
            }
            val prev = dense[max(0, i - 1)]
            val next = dense[min(dense.size - 1, i + 1)]
            val dx = next.x - prev.x
            val dy = next.y - prev.y
            val dz = next.z - prev.z
            val hLen = hypot(dx, dz).coerceAtLeast(1e-4f)
            val dLen3 = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
            val tx = dx / dLen3
            val ty = dy / dLen3
            val tz = dz / dLen3
            // Horizontal right vector perpendicular to track heading in XZ
            val rx = dz / hLen
            val rz = -dx / hLen
            nodes.add(TrackNode(cur.x, cur.y, cur.z, tx, ty, tz, rx, rz, cumDist))
        }
        cachedTrackNodes = nodes
        return nodes
    }

    fun getTotalTrackLengthM(): Float {
        val nodes = getOrBuildTrackNodes()
        return if (nodes.isEmpty()) 1f else nodes.last().dist
    }

    /**
     * Fast clearance test so roadside trees/buildings/grass never spawn on top of the 2-track railway corridor
     * or under/inside the elevated rail viaducts.
     */
    fun isNearRailwayTrack(x: Float, z: Float, marginM: Float = 12.5f): Boolean {
        if (z < -275f || z > 3460f || x < -145f || x > 185f) return false
        val nodes = getOrBuildTrackNodes()
        val mSq = marginM * marginM
        for (i in 0 until nodes.size - 1) {
            val a = nodes[i]
            val b = nodes[i + 1]
            val vx = b.x - a.x
            val vz = b.z - a.z
            val lenSq = (vx * vx + vz * vz).coerceAtLeast(1e-4f)
            val t = (((x - a.x) * vx + (z - a.z) * vz) / lenSq).coerceIn(0f, 1f)
            val dx = x - (a.x + vx * t)
            val dz = z - (a.z + vz * t)
            if (dx * dx + dz * dz < mSq) return true
        }
        return false
    }

    /**
     * Draws a dedicated crushed-stone railway ballast corridor underneath ground-level tracks ONLY
     * (automatically skipped wherever the track rises onto an elevated rail viaduct so ZERO ballast
     * ever appears on flat M.G. Road or SH 22!).
     */
    internal fun DrawScope.drawRailwayCorridorBallastGround(
        rig: CameraRig,
        path: Path,
        camEye: Vec3
    ) {
        val nodes = getOrBuildTrackNodes()
        if (nodes.size < 2) return
        val maxDistSq = 540f * 540f
        val halfBallastW = 5.15f
        val yBallast = 0.024f
        val unconditional = rig.recorder != null
        for (i in 0 until nodes.size - 1) {
            val a = nodes[i]
            val b = nodes[i + 1]
            // Never draw ground ballast under elevated viaduct spans or road crossings!
            if (max(a.y, b.y) > 0.22f) continue
            if (!unconditional) {
                val dx = a.x - camEye.x
                val dz = a.z - camEye.z
                if (dx * dx + dz * dz > maxDistSq) continue
            }
            val p0 = Vec3(a.x - a.rx * halfBallastW, yBallast, a.z - a.rz * halfBallastW)
            val p1 = Vec3(a.x + a.rx * halfBallastW, yBallast, a.z + a.rz * halfBallastW)
            val p2 = Vec3(b.x + b.rx * halfBallastW, yBallast, b.z + b.rz * halfBallastW)
            val p3 = Vec3(b.x - b.rx * halfBallastW, yBallast, b.z - b.rz * halfBallastW)
            if (rig.buildClippedQuadPath(path, p0, p1, p2, p3)) {
                drawPath(path, RobBallastBed)
            }
        }
    }

    /**
     * Samples exact 3D world pose (x, y, z, yawRad, pitchRad) on Track 1 (`trackIndex == 0`, West/Northbound)
     * or Track 2 (`trackIndex == 1`, East/Southbound) at distance `distM` along the corridor.
     */
    fun sampleTrackPose(trackIndex: Int, distM: Float, faceNorth: Boolean): TrackSample {
        val nodes = getOrBuildTrackNodes()
        val total = nodes.last().dist
        val clamped = distM.coerceIn(0f, total)
        var lo = 0
        var hi = nodes.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (nodes[mid].dist <= clamped) lo = mid else hi = mid
        }
        val a = nodes[lo]
        val b = nodes[hi]
        val span = (b.dist - a.dist).coerceAtLeast(1e-4f)
        val t = ((clamped - a.dist) / span).coerceIn(0f, 1f)

        val cx = a.x + (b.x - a.x) * t
        val cy = a.y + (b.y - a.y) * t
        val cz = a.z + (b.z - a.z) * t
        val tx = a.tx + (b.tx - a.tx) * t
        val ty = a.ty + (b.ty - a.ty) * t
        val tz = a.tz + (b.tz - a.tz) * t
        val hLen = hypot(tx, tz).coerceAtLeast(1e-4f)
        val utx = tx / hLen
        val utz = tz / hLen
        val urx = utz
        val urz = -utx

        val latOff = if (trackIndex == 0) -TRACK_HALF_SEP else TRACK_HALF_SEP
        val px = cx + urx * latOff
        val pz = cz + urz * latOff

        val dirX = if (faceNorth) utx else -utx
        val dirZ = if (faceNorth) utz else -utz
        val dirY = if (faceNorth) ty else -ty
        // Matches GDRIVE_INSTANCED_VS rotation: local +Z maps to (-sin(yaw)*cos(pitch), sin(pitch), cos(yaw)*cos(pitch))
        val yaw = atan2(-dirX.toDouble(), dirZ.toDouble()).toFloat()
        val pitch = atan2(dirY.toDouble(), hLen.toDouble()).toFloat()
        return TrackSample(px, cy, pz, yaw, pitch)
    }

    /**
     * Generates all static instanced 3D Indian Railway Track tiles (`track_indian`) continuously
     * along both parallel tracks (including smooth pitched tiles across Poothole & Mullurkara rail viaducts).
     */
    fun getStaticTrackInstances(): List<RailwayInstance> {
        cachedTrackTileInstances?.let { return it }
        val total = getTotalTrackLengthM()
        val tilePitch = 11.75f
        val out = ArrayList<RailwayInstance>(780)
        for (trackIdx in 0..1) {
            var d = 6.0f
            while (d < total - 6.0f) {
                val pose = sampleTrackPose(trackIdx, d, faceNorth = true)
                out.add(
                    RailwayInstance(
                        modelIndex = MODEL_TRACK,
                        cx = pose.x,
                        baseY = pose.y - 0.01f,
                        cz = pose.z,
                        yawRad = pose.yawRad,
                        scale = 1.0f,
                        pitchRad = pose.pitchRad
                    )
                )
                d += tilePitch
            }
        }
        // Zone 3: Ollur ROB 2-Track Electrified Southern Railway Mainline underneath NH 544 (X = 772..1148, Z = -1430 ± 2.65m)
        // Model local +Z runs along +X when yawRad = -PI/2
        val ollurYaw = (-PI * 0.5).toFloat()
        for (rz in floatArrayOf(Zone3Nh544SouthRenderer.OLLUR_ROB_CENTER_Z - 2.65f, Zone3Nh544SouthRenderer.OLLUR_ROB_CENTER_Z + 2.65f)) {
            var tx = 774.0f
            while (tx <= 1146.0f) {
                out.add(
                    RailwayInstance(
                        modelIndex = MODEL_TRACK,
                        cx = tx,
                        baseY = 0.045f,
                        cz = rz,
                        yawRad = ollurYaw,
                        scale = 1.0f,
                        pitchRad = 0.0f
                    )
                )
                tx += tilePitch
            }
        }
        // Zone 4: Muthuvara Single-Track Electrified Thrissur–Guruvayur Southern Railway Line (X = -960..-160, Z = 610.0)
        // Skip the flush concrete level-crossing road apron at SH 69 (X in -566.5..-553.5) so sleepers never poke above asphalt!
        var mtx = Zone4KoleRailHeritageRenderer.MUTHUVARA_TRACK_WEST_X + 6f
        while (mtx <= Zone4KoleRailHeritageRenderer.MUTHUVARA_TRACK_EAST_X - 6f) {
            if (abs(mtx - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X) > 7.2f) {
                out.add(
                    RailwayInstance(
                        modelIndex = MODEL_TRACK,
                        cx = mtx,
                        baseY = 0.045f,
                        cz = Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z,
                        yawRad = ollurYaw,
                        scale = 1.0f,
                        pitchRad = 0.0f
                    )
                )
            }
            mtx += tilePitch
        }
        cachedTrackTileInstances = out
        return out
    }

    /**
     * Live synchronized head X position (along Z = 610.0f, travelling Westbound -X toward Guruvayur at 65 km/h)
     * of the Zone 4 Muthuvara Animated Level Crossing Express Train (WAP-7/WAG-9 + 8 Blue ICF Coaches).
     * Set to Float.NaN when no train is currently active on the Muthuvara LC approach.
     */
    @Volatile
    var muthuvaraLcTrainHeadX: Float = Float.NaN

    /**
     * Computes live 3D positions & orientations of ALL active 8-unit trains (1 WAG-9 Locomotive + 7 Coaches each)
     * with tight authentic ~5-6 cm coupling clearance between every unit:
     * - Shoranur Corridor Track 1 (Northbound: Thrissur -> Shoranur): WAG-9 + 7x Coach 1 (`coach_1`)
     * - Shoranur Corridor Track 2 (Southbound: Shoranur -> Thrissur): WAG-9 + 7x Coach 2 (`coach_2`)
     * - Zone 3 Ollur ROB Track 1 (Eastbound): WAG-9 + 7x Coach 1 gliding underneath Ollur ROB
     * - Zone 3 Ollur ROB Track 2 (Westbound): WAG-9 + 7x Coach 2 gliding underneath Ollur ROB
     */
    fun appendAnimatedTrainInstances(simTimeSec: Float, out: MutableList<RailwayInstance>) {
        val total = getTotalTrackLengthM()
        // Measured Z-extents:
        // train_wag9 body/buffers end at +-9.91m (coupler tip +-10.28m)
        // coach_1 body/vestibules span [-10.566, +10.579] -> pitch 21.20m leaves ~5.5cm gap
        // coach_2 full body spans [-10.600, +10.600] -> pitch 21.26m leaves ~6.0cm gap
        val locoToFirstCoach1 = 20.42f
        val coach1Spacing = 21.20f
        val locoToFirstCoach2 = 20.57f
        val coach2Spacing = 21.26f

        val trainTotalLen = max(locoToFirstCoach1 + 6f * coach1Spacing, locoToFirstCoach2 + 6f * coach2Spacing) + 24f
        val travelSpan = (total - trainTotalLen - 40f).coerceAtLeast(400f)

        // Train 1 (Shoranur Track 1, Northbound: Thrissur -> Shoranur)
        val speed1 = 23.5f
        val phase1 = ((simTimeSec * speed1 + 1850f) % travelSpan + travelSpan) % travelSpan
        val headDist1 = 220f + phase1
        val loco1 = sampleTrackPose(trackIndex = 0, distM = headDist1, faceNorth = true)
        out.add(RailwayInstance(MODEL_WAG9, loco1.x, loco1.y, loco1.z, loco1.yawRad, 1.0f, loco1.pitchRad))
        for (c in 0 until 7) {
            val cDist = headDist1 - locoToFirstCoach1 - c * coach1Spacing
            val cp = sampleTrackPose(trackIndex = 0, distM = cDist, faceNorth = true)
            out.add(RailwayInstance(MODEL_COACH_1, cp.x, cp.y, cp.z, cp.yawRad, 1.0f, cp.pitchRad))
        }

        // Train 2 (Shoranur Track 2, Southbound: Shoranur -> Thrissur)
        val speed2 = 21.8f
        val phase2 = ((simTimeSec * speed2 + 1650f) % travelSpan + travelSpan) % travelSpan
        val headDist2 = (total - 220f) - phase2
        val loco2 = sampleTrackPose(trackIndex = 1, distM = headDist2, faceNorth = false)
        out.add(RailwayInstance(MODEL_WAG9, loco2.x, loco2.y, loco2.z, loco2.yawRad, 1.0f, loco2.pitchRad))
        for (c in 0 until 7) {
            val cDist = headDist2 + locoToFirstCoach2 + c * coach2Spacing
            val cp = sampleTrackPose(trackIndex = 1, distM = cDist, faceNorth = false)
            out.add(RailwayInstance(MODEL_COACH_2, cp.x, cp.y, cp.z, cp.yawRad, 1.0f, cp.pitchRad))
        }

        // Zone 3 Ollur ROB Real 3D Southern Railway Trains passing directly underneath NH 544 ROB!
        val ollurZ1 = Zone3Nh544SouthRenderer.OLLUR_ROB_CENTER_Z - 2.65f
        val ollurZ2 = Zone3Nh544SouthRenderer.OLLUR_ROB_CENTER_Z + 2.65f
        val ollurSpan = 580f
        val yawEast = (-PI * 0.5).toFloat()
        val yawWest = (PI * 0.5).toFloat()

        // Ollur Train 1 (Eastbound: WAG-9 + 7x Coach 1)
        val oPhase1 = ((simTimeSec * 22.0f + 220f) % ollurSpan + ollurSpan) % ollurSpan
        val oHead1 = 760f + oPhase1
        if (oHead1 in 750f..1160f) {
            out.add(RailwayInstance(MODEL_WAG9, oHead1, 0.055f, ollurZ1, yawEast, 1.0f, 0f))
        }
        for (c in 0 until 7) {
            val cx = oHead1 - locoToFirstCoach1 - c * coach1Spacing
            if (cx in 750f..1160f) {
                out.add(RailwayInstance(MODEL_COACH_1, cx, 0.055f, ollurZ1, yawEast, 1.0f, 0f))
            }
        }

        // Ollur Train 2 (Westbound: WAG-9 + 7x Coach 2)
        val oPhase2 = ((simTimeSec * 19.5f + 410f) % ollurSpan + ollurSpan) % ollurSpan
        val oHead2 = 1160f - oPhase2
        if (oHead2 in 750f..1160f) {
            out.add(RailwayInstance(MODEL_WAG9, oHead2, 0.055f, ollurZ2, yawWest, 1.0f, 0f))
        }
        for (c in 0 until 7) {
            val cx = oHead2 + locoToFirstCoach2 + c * coach2Spacing
            if (cx in 750f..1160f) {
                out.add(RailwayInstance(MODEL_COACH_2, cx, 0.055f, ollurZ2, yawWest, 1.0f, 0f))
            }
        }

        // Zone 4: Muthuvara Animated Level Crossing (LC-14) Live Synchronized 8-Coach Guruvayur Express!
        // Electric Locomotive + 8 Blue ICF Passenger Coaches speeding Westbound (-X) at 65 km/h (18.06 m/s) across SH 69!
        val mHeadX = muthuvaraLcTrainHeadX
        if (!mHeadX.isNaN()) {
            val mz = Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z
            val minVisX = Zone4KoleRailHeritageRenderer.MUTHUVARA_TRACK_WEST_X - 20f
            val maxVisX = Zone4KoleRailHeritageRenderer.MUTHUVARA_TRACK_EAST_X + 20f
            if (mHeadX in minVisX..maxVisX) {
                out.add(RailwayInstance(MODEL_WAG9, mHeadX, 0.055f, mz, yawWest, 1.0f, 0f))
            }
            for (c in 0 until 8) {
                val cx = mHeadX + locoToFirstCoach1 + c * coach1Spacing
                if (cx in minVisX..maxVisX) {
                    out.add(RailwayInstance(MODEL_COACH_1, cx, 0.055f, mz, yawWest, 1.0f, 0f))
                }
            }
        }
    }
}

// =========================================================================
// SOUTHERN RAILWAY ELEVATED RAIL VIADUCTS (POOTHOLE & MULLURKARA RUBs)
// =========================================================================

private val RobConcreteLight = Color(0xFFD4D0C7)
private val RobConcreteMid = Color(0xFFA9A59B)
private val RobConcreteDark = Color(0xFF72767C)
private val RobSteelGirder = Color(0xFF2F4F6B)
private val RobSteelHighlight = Color(0xFF476C8C)
private val RobSafetyYellow = Color(0xFFEAB308)
private val RobSafetyBlack = Color(0xFF1E2228)
private val RobBallastBed = Color(0xFF56514A)
private val RobLampPole = Color(0xFF475569)
private val RobLampGlow = Color(0xFFFEF08A)
private val WadakTownPlaster = Color(0xFFE6DEC8)
private val WadakTownTerracotta = Color(0xFFA2442E)
private val WadakTownSlate = Color(0xFF39414C)

private val cachedRailwayViaductFaces: List<QuadFace3D> by lazy {
    val faces = ArrayList<QuadFace3D>(1400)
    val nodes = ShoranurRailwaySystem.getOrBuildTrackNodes()

    fun addAxisBox(
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        topCol: Color, sideCol: Color = topCol, frontBackCol: Color = sideCol,
        bottomCol: Color? = null
    ) {
        faces.add(QuadFace3D(Vec3(minX, maxY, minZ), Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), topCol, null, 0f, 0f, 0f, 1f, 0f))
        if (bottomCol != null) {
            faces.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), bottomCol, null, 0.1f, 0f, 0f, -1f, 0f))
        }
        faces.add(QuadFace3D(Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, maxY, minZ), Vec3(minX, maxY, minZ), frontBackCol, null, 0f, 0f, 0f, 0f, -1f))
        faces.add(QuadFace3D(Vec3(maxX, minY, maxZ), Vec3(minX, minY, maxZ), Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), frontBackCol, null, 0f, 0f, 0f, 0f, 1f))
        faces.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(minX, minY, minZ), Vec3(minX, maxY, minZ), Vec3(minX, maxY, maxZ), sideCol, null, 0f, 0f, -1f, 0f, 0f))
        faces.add(QuadFace3D(Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), sideCol, null, 0f, 0f, 1f, 0f, 0f))
    }

    fun addOrientedPier(
        cx: Float, cz: Float, rx: Float, rz: Float, tx: Float, tz: Float,
        deckBotY: Float
    ) {
        if (deckBotY < 0.75f) return
        val halfW = 4.65f
        val halfT = 0.72f
        val capBot = (deckBotY - 0.78f).coerceAtLeast(0.45f)

        fun addOrientedQuadPrism(w0: Float, w1: Float, t0: Float, t1: Float, y0: Float, y1: Float, topCol: Color, sideCol: Color) {
            fun p(w: Float, t: Float, y: Float) = Vec3(cx + rx * w + tx * t, y, cz + rz * w + tz * t)
            val b0 = p(w0, t0, y0); val b1 = p(w1, t0, y0); val b2 = p(w1, t1, y0); val b3 = p(w0, t1, y0)
            val u0 = p(w0, t0, y1); val u1 = p(w1, t0, y1); val u2 = p(w1, t1, y1); val u3 = p(w0, t1, y1)
            faces.add(QuadFace3D(u0, u3, u2, u1, topCol))
            faces.add(QuadFace3D(b0, b1, u1, u0, sideCol))
            faces.add(QuadFace3D(b1, b2, u2, u1, sideCol))
            faces.add(QuadFace3D(b2, b3, u3, u2, sideCol))
            faces.add(QuadFace3D(b3, b0, u0, u3, sideCol))
        }

        // Reinforced concrete hammerhead pier cap beam
        addOrientedQuadPrism(-halfW, halfW, -halfT, halfT, capBot, deckBotY, RobConcreteMid, RobConcreteDark)
        // Twin concrete pier columns strictly supporting left & right tracks
        for (colOff in floatArrayOf(-2.75f, 2.75f)) {
            addOrientedQuadPrism(colOff - 0.65f, colOff + 0.65f, -0.65f, 0.65f, 0.04f, capBot, RobConcreteLight, RobConcreteMid)
            addOrientedQuadPrism(colOff - 0.85f, colOff + 0.85f, -0.85f, 0.85f, 0.04f, 0.65f, RobSafetyYellow, RobSafetyBlack)
        }
    }

    val halfDeckW = 5.05f
    val deckThick = 0.52f
    val girderTopH = 1.25f

    for (i in 0 until nodes.size - 1) {
        val a = nodes[i]
        val b = nodes[i + 1]
        if (max(a.y, b.y) <= 0.14f) continue

        val aBot = (a.y - deckThick).coerceAtLeast(0.03f)
        val bBot = (b.y - deckThick).coerceAtLeast(0.03f)
        val midZ = (a.z + b.z) * 0.5f

        val isOpenOverRoad = (midZ in -30.5f..-3.5f) || (midZ in 2376.0f..2464.0f)
        val sideCol = if (isOpenOverRoad) RobSteelGirder else RobConcreteLight
        val sideHiCol = if (isOpenOverRoad) RobSteelHighlight else RobConcreteMid
        val parapetH = if (isOpenOverRoad) girderTopH else 0.72f

        // Ballast-bed top slab of the rail viaduct
        val topA0 = Vec3(a.x - a.rx * halfDeckW, a.y - 0.03f, a.z - a.rz * halfDeckW)
        val topA1 = Vec3(a.x + a.rx * halfDeckW, a.y - 0.03f, a.z + a.rz * halfDeckW)
        val topB1 = Vec3(b.x + b.rx * halfDeckW, b.y - 0.03f, b.z + b.rz * halfDeckW)
        val topB0 = Vec3(b.x - b.rx * halfDeckW, b.y - 0.03f, b.z - b.rz * halfDeckW)
        faces.add(QuadFace3D(topA0, topA1, topB1, topB0, RobBallastBed, null, 0f, 0f, 0f, 1f, 0f))

        // Underside concrete soffit visible from roads underneath!
        if (min(aBot, bBot) > 0.35f) {
            val botA0 = Vec3(a.x - a.rx * halfDeckW, aBot, a.z - a.rz * halfDeckW)
            val botA1 = Vec3(a.x + a.rx * halfDeckW, aBot, a.z + a.rz * halfDeckW)
            val botB1 = Vec3(b.x + b.rx * halfDeckW, bBot, b.z + b.rz * halfDeckW)
            val botB0 = Vec3(b.x - b.rx * halfDeckW, bBot, b.z - b.rz * halfDeckW)
            faces.add(QuadFace3D(botA1, botA0, botB0, botB1, RobConcreteDark, null, 0f, 0f, 0f, -1f, 0f))
        }

        // Left & Right side plate girders / concrete parapets
        for (side in floatArrayOf(-1f, 1f)) {
            val outOff = side * (halfDeckW + 0.14f)
            val inOff = side * (halfDeckW - 0.22f)
            val obA = Vec3(a.x + a.rx * outOff, aBot, a.z + a.rz * outOff)
            val obB = Vec3(b.x + b.rx * outOff, bBot, b.z + b.rz * outOff)
            val otB = Vec3(b.x + b.rx * outOff, b.y + parapetH, b.z + b.rz * outOff)
            val otA = Vec3(a.x + a.rx * outOff, a.y + parapetH, a.z + a.rz * outOff)
            faces.add(QuadFace3D(obA, obB, otB, otA, sideCol))

            val ibA = Vec3(a.x + a.rx * inOff, a.y - 0.02f, a.z + a.rz * inOff)
            val ibB = Vec3(b.x + b.rx * inOff, b.y - 0.02f, b.z + b.rz * inOff)
            val itB = Vec3(b.x + b.rx * inOff, b.y + parapetH, b.z + b.rz * inOff)
            val itA = Vec3(a.x + a.rx * inOff, a.y + parapetH, a.z + a.rz * inOff)
            faces.add(QuadFace3D(ibB, ibA, itA, itB, sideHiCol))

            // Coping top cap
            val cA0 = Vec3(a.x + a.rx * min(inOff, outOff), a.y + parapetH, a.z + a.rz * min(inOff, outOff))
            val cA1 = Vec3(a.x + a.rx * max(inOff, outOff), a.y + parapetH, a.z + a.rz * max(inOff, outOff))
            val cB1 = Vec3(b.x + b.rx * max(inOff, outOff), b.y + parapetH, b.z + b.rz * max(inOff, outOff))
            val cB0 = Vec3(b.x + b.rx * min(inOff, outOff), b.y + parapetH, b.z + b.rz * min(inOff, outOff))
            faces.add(QuadFace3D(cA0, cA1, cB1, cB0, if (isOpenOverRoad) RobSteelHighlight else RobConcreteMid))
        }
    }

    // Place reinforced concrete viaduct piers strictly OUTSIDE road asphalt + shoulders + footpaths!
    // Poothole Viaduct piers (M.G. Road is at Z = -25.5 .. -8.1 -> piers at -72, -52, -31.5 and -2.5, +18, +38, +56)
    val pierTargetZs = floatArrayOf(
        -72.0f, -52.0f, -31.5f, -2.5f, 18.0f, 38.0f, 56.0f,
        2285.0f, 2318.0f, 2350.0f, 2374.0f, 2466.0f, 2498.0f, 2530.0f, 2562.0f
    )
    for (pz in pierTargetZs) {
        var bestIdx = -1
        var bestDz = Float.MAX_VALUE
        for (i in nodes.indices) {
            val dz = abs(nodes[i].z - pz)
            if (dz < bestDz) {
                bestDz = dz
                bestIdx = i
            }
        }
        if (bestIdx >= 0 && bestDz < 12f) {
            val nd = nodes[bestIdx]
            val hLen = hypot(nd.tx, nd.tz).coerceAtLeast(1e-4f)
            addOrientedPier(nd.x, nd.z, nd.rx, nd.rz, nd.tx / hLen, nd.tz / hLen, nd.y - deckThick)
        }
    }

    // Height clearance warning stripes on the Poothole Steel Girder facing East & West traffic on M.G. Road
    addAxisBox(-117.25f, 5.82f, -26.0f, -117.12f, 6.12f, -7.5f, RobSafetyYellow, RobSafetyBlack, RobSafetyYellow)
    addAxisBox(-106.88f, 5.82f, -26.0f, -106.75f, 6.12f, -7.5f, RobSafetyYellow, RobSafetyBlack, RobSafetyYellow)

    faces
}

internal fun collectRailwayViaductFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    val nearPoothole = hypot(camEye.x - (-112f), camEye.z - (-17f)) < 480f
    val nearMullurkara = hypot(camEye.x - 96.5f, camEye.z - 2420f) < 560f
    if (!nearPoothole && !nearMullurkara) return
    outFaces.addAll(cachedRailwayViaductFaces)
}

internal fun collectAllRailwayViaductStaticFaces(outFaces: MutableList<QuadFace3D>) {
    outFaces.addAll(cachedRailwayViaductFaces)
}

// =========================================================================
// WADAKKANCHERY RAILWAY OVERBRIDGE (ROB) & TOWN 3D ARCHITECTURE
// =========================================================================

internal fun DrawScope.drawWadakkancheryRobAndTownGround(
    rig: CameraRig,
    path: Path,
    camEye: Vec3
) {
    val cx = ShoranurRailwaySystem.ROB_CENTER_X
    val cz = ShoranurRailwaySystem.ROB_TRACK_CROSS_Z
    if (rig.recorder == null && hypot(camEye.x - cx, camEye.z - cz) > 480f) return

    // Wadakkanchery Town paved roadside plaza North of the bridge (Z = 1860..1950)
    if (rig.buildClippedQuadPath(
            path,
            Vec3(cx + 8.0f, 0.052f, 1862.0f),
            Vec3(cx + 34.0f, 0.052f, 1862.0f),
            Vec3(cx + 34.0f, 0.052f, 1945.0f),
            Vec3(cx + 8.0f, 0.052f, 1945.0f)
        )
    ) {
        drawPath(path, Color(0xFF8C877E))
    }
}

private val cachedWadakkancheryRobFaces: List<QuadFace3D> by lazy {
    val faces = ArrayList<QuadFace3D>(1400)

    fun addBox(
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
        topCol: Color, sideCol: Color = topCol, frontBackCol: Color = sideCol,
        bottomCol: Color? = null
    ) {
        faces.add(QuadFace3D(Vec3(minX, maxY, minZ), Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), topCol, null, 0f, 0f, 0f, 1f, 0f))
        if (bottomCol != null) {
            faces.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), bottomCol, null, 0.1f, 0f, 0f, -1f, 0f))
        }
        faces.add(QuadFace3D(Vec3(minX, minY, minZ), Vec3(maxX, minY, minZ), Vec3(maxX, maxY, minZ), Vec3(minX, maxY, minZ), frontBackCol, null, 0f, 0f, 0f, 0f, -1f))
        faces.add(QuadFace3D(Vec3(maxX, minY, maxZ), Vec3(minX, minY, maxZ), Vec3(minX, maxY, maxZ), Vec3(maxX, maxY, maxZ), frontBackCol, null, 0f, 0f, 0f, 0f, 1f))
        faces.add(QuadFace3D(Vec3(minX, minY, maxZ), Vec3(minX, minY, minZ), Vec3(minX, maxY, minZ), Vec3(minX, maxY, maxZ), sideCol, null, 0f, 0f, -1f, 0f, 0f))
        faces.add(QuadFace3D(Vec3(maxX, minY, minZ), Vec3(maxX, minY, maxZ), Vec3(maxX, maxY, maxZ), Vec3(maxX, maxY, minZ), sideCol, null, 0f, 0f, 1f, 0f, 0f))
    }

    val cx = ShoranurRailwaySystem.ROB_CENTER_X
    val hw = ShoranurRailwaySystem.ROB_DECK_HALF_W
    val stepZ = 8.0f

    // 1. Segmented Reinforced-Concrete Approach Embankment Walls, Bridge Deck Soffit, & Crash Parapets
    var z0 = ShoranurRailwaySystem.ROB_RAMP_SOUTH_Z
    while (z0 < ShoranurRailwaySystem.ROB_RAMP_NORTH_Z - 0.1f) {
        val z1 = min(z0 + stepZ, ShoranurRailwaySystem.ROB_RAMP_NORTH_Z)
        val y0 = ShoranurRailwaySystem.computeRoadSurfaceY(cx, z0)
        val y1 = ShoranurRailwaySystem.computeRoadSurfaceY(cx, z1)
        val deckThick = 0.68f
        val parapetH = 1.12f
        val isOpenSpan = z0 >= ShoranurRailwaySystem.ROB_DECK_SOUTH_Z - 4f && z1 <= ShoranurRailwaySystem.ROB_DECK_NORTH_Z + 4f

        for (side in floatArrayOf(-1f, 1f)) {
            val innerX = cx + side * (hw - 0.36f)
            val outerX = cx + side * (hw + 0.18f)
            val xMin = min(innerX, outerX)
            val xMax = max(innerX, outerX)

            val b0 = Vec3(outerX, y0 - deckThick, z0)
            val b1 = Vec3(outerX, y1 - deckThick, z1)
            val t1 = Vec3(outerX, y1 + parapetH, z1)
            val t0 = Vec3(outerX, y0 + parapetH, z0)
            faces.add(QuadFace3D(b0, b1, t1, t0, RobConcreteLight, null, 0f, 0f, side, 0f, 0f))

            val ib0 = Vec3(innerX, y0, z0)
            val ib1 = Vec3(innerX, y1, z1)
            val it1 = Vec3(innerX, y1 + parapetH, z1)
            val it0 = Vec3(innerX, y0 + parapetH, z0)
            faces.add(QuadFace3D(ib1, ib0, it0, it1, RobConcreteLight, null, 0f, 0f, -side, 0f, 0f))

            val topCol = if (((z0 / stepZ).toInt() and 1) == 0) RobConcreteMid else RobSafetyYellow
            faces.add(
                QuadFace3D(
                    Vec3(xMin, y0 + parapetH, z0),
                    Vec3(xMax, y0 + parapetH, z0),
                    Vec3(xMax, y1 + parapetH, z1),
                    Vec3(xMin, y1 + parapetH, z1),
                    topCol, null, 0f, 0f, 0f, 1f, 0f
                )
            )

            if (!isOpenSpan && max(y0, y1) > 0.45f) {
                val rBot0 = Vec3(outerX, 0.04f, z0)
                val rBot1 = Vec3(outerX, 0.04f, z1)
                val rTop1 = Vec3(outerX, max(0.05f, y1 - deckThick), z1)
                val rTop0 = Vec3(outerX, max(0.05f, y0 - deckThick), z0)
                faces.add(QuadFace3D(rBot0, rBot1, rTop1, rTop0, RobConcreteMid, null, 0f, 0f, side, 0f, 0f))
            }
        }

        if (isOpenSpan) {
            faces.add(
                QuadFace3D(
                    Vec3(cx - hw, y0 - deckThick, z0),
                    Vec3(cx + hw, y0 - deckThick, z0),
                    Vec3(cx + hw, y1 - deckThick, z1),
                    Vec3(cx - hw, y1 - deckThick, z1),
                    RobConcreteDark, null, 0f, 0f, 0f, -1f, 0f
                )
            )
        }
        z0 = z1
    }

    // 2. Massive Twin Concrete Bridge Piers on South & North Sides of Railway Tracks (100% clear of both tracks!)
    for (pierZ in floatArrayOf(1636.0f, 1724.0f)) {
        val deckBot = ShoranurRailwaySystem.ROB_DECK_TOP_Y - 0.68f
        addBox(cx - hw + 0.2f, deckBot - 0.95f, pierZ - 1.15f, cx + hw - 0.2f, deckBot, pierZ + 1.15f, RobConcreteMid, RobConcreteDark, RobConcreteMid, RobConcreteDark)
        for (px in floatArrayOf(cx - 3.8f, cx + 3.8f)) {
            addBox(px - 0.85f, 0.04f, pierZ - 0.85f, px + 0.85f, deckBot - 0.95f, pierZ + 0.85f, RobConcreteLight, RobConcreteMid, RobConcreteLight)
            addBox(px - 1.15f, 0.04f, pierZ - 1.15f, px + 1.15f, 0.72f, pierZ + 1.15f, RobSafetyYellow, RobSafetyBlack, RobSafetyYellow)
        }
    }

    // 3. Iconic Kerala PWD Steel Bowstring / Plate-Girder Main Arch Spanning Directly Over the 2 Railway Tracks!
    val archStartZ = 1640.0f
    val archEndZ = 1720.0f
    val archSegs = 12
    val archRise = 5.4f
    val baseY = ShoranurRailwaySystem.ROB_DECK_TOP_Y + 0.25f
    for (side in floatArrayOf(-1f, 1f)) {
        val gx = cx + side * (hw + 0.24f)
        addBox(gx - 0.24f, ShoranurRailwaySystem.ROB_DECK_TOP_Y - 0.75f, archStartZ, gx + 0.24f, ShoranurRailwaySystem.ROB_DECK_TOP_Y + 1.45f, archEndZ, RobSteelGirder, RobSteelHighlight, RobSteelGirder)

        for (i in 0 until archSegs) {
            val u0 = i.toFloat() / archSegs.toFloat()
            val u1 = (i + 1).toFloat() / archSegs.toFloat()
            val az0 = archStartZ + (archEndZ - archStartZ) * u0
            val az1 = archStartZ + (archEndZ - archStartZ) * u1
            val ay0 = baseY + 4f * archRise * u0 * (1f - u0)
            val ay1 = baseY + 4f * archRise * u1 * (1f - u1)

            faces.add(QuadFace3D(Vec3(gx - 0.22f, ay0, az0), Vec3(gx - 0.22f, ay1, az1), Vec3(gx - 0.22f, ay1 + 0.58f, az1), Vec3(gx - 0.22f, ay0 + 0.58f, az0), RobSteelHighlight, null, 0f, 0f, -1f, 0f, 0f))
            faces.add(QuadFace3D(Vec3(gx + 0.22f, ay1, az1), Vec3(gx + 0.22f, ay0, az0), Vec3(gx + 0.22f, ay0 + 0.58f, az0), Vec3(gx + 0.22f, ay1 + 0.58f, az1), RobSteelHighlight, null, 0f, 0f, 1f, 0f, 0f))
            faces.add(QuadFace3D(Vec3(gx - 0.22f, ay0 + 0.58f, az0), Vec3(gx + 0.22f, ay0 + 0.58f, az0), Vec3(gx + 0.22f, ay1 + 0.58f, az1), Vec3(gx - 0.22f, ay1 + 0.58f, az1), RobSteelGirder, null, 0f, 0f, 0f, 1f, 0f))

            if (i in 1 until archSegs) {
                addBox(gx - 0.08f, ShoranurRailwaySystem.ROB_DECK_TOP_Y + 1.1f, az0 - 0.08f, gx + 0.08f, ay0, az0 + 0.08f, RobSteelGirder)
            }
        }
    }
    for (braceZ in floatArrayOf(1660.0f, 1670.0f, 1680.0f, 1690.0f, 1700.0f)) {
        val u = (braceZ - archStartZ) / (archEndZ - archStartZ)
        val ay = baseY + 4f * archRise * u * (1f - u)
        addBox(cx - hw - 0.24f, ay, braceZ - 0.20f, cx + hw + 0.24f, ay + 0.45f, braceZ + 0.20f, RobSteelGirder, RobSteelHighlight, RobSteelGirder, RobSteelGirder)
    }

    // 4. Bridge Streetlamps Along Both Parapets
    for (lz in floatArrayOf(1560f, 1600f, 1645f, 1680f, 1715f, 1760f, 1800f)) {
        val ly = ShoranurRailwaySystem.computeRoadSurfaceY(cx, lz) + 1.12f
        for (side in floatArrayOf(-1f, 1f)) {
            val px = cx + side * (hw + 0.05f)
            addBox(px - 0.07f, ly, lz - 0.07f, px + 0.07f, ly + 4.4f, lz + 0.07f, RobLampPole)
            val armEnd = px - side * 1.4f
            addBox(min(px, armEnd), ly + 4.28f, lz - 0.06f, max(px, armEnd), ly + 4.42f, lz + 0.06f, RobLampPole)
            addBox(armEnd - 0.22f, ly + 4.18f, lz - 0.14f, armEnd + 0.22f, ly + 4.30f, lz + 0.14f, RobLampGlow)
        }
    }

    // 5. Wadakkanchery Town Welcome Bus Shelter & Commercial Shops North of ROB (X = cx+10..cx+32, Z = 1868..1940)
    addBox(cx + 9.2f, 0.06f, 1882.0f, cx + 13.8f, 0.24f, 1898.0f, RobConcreteLight)
    addBox(cx + 13.3f, 0.24f, 1882.4f, cx + 13.8f, 3.10f, 1897.6f, WadakTownPlaster, RobConcreteMid, WadakTownPlaster)
    for (pz in floatArrayOf(1883.0f, 1890.0f, 1897.0f)) {
        addBox(cx + 9.5f, 0.24f, pz - 0.12f, cx + 9.74f, 3.25f, pz + 0.12f, RobSteelGirder)
    }
    addBox(cx + 8.8f, 3.15f, 1881.2f, cx + 14.3f, 3.55f, 1898.8f, WadakTownTerracotta, WadakTownSlate, WadakTownTerracotta, RobConcreteDark)
    addBox(cx + 12.1f, 0.24f, 1885.0f, cx + 13.1f, 0.72f, 1895.0f, Color(0xFF784B2A))

    addBox(cx + 18.0f, 0.06f, 1866.0f, cx + 32.0f, 7.60f, 1896.0f, WadakTownTerracotta, WadakTownPlaster, WadakTownPlaster)
    addBox(cx + 17.5f, 7.60f, 1865.5f, cx + 32.5f, 8.20f, 1896.5f, WadakTownTerracotta, WadakTownSlate, WadakTownTerracotta)
    addBox(cx + 17.8f, 3.85f, 1868.0f, cx + 18.06f, 6.20f, 1894.0f, Color(0xFF1F3448))
    addBox(cx + 18.0f, 0.06f, 1906.0f, cx + 32.5f, 6.80f, 1938.0f, WadakTownSlate, Color(0xFFEBD9C4), Color(0xFFEBD9C4))
    addBox(cx + 17.5f, 6.80f, 1905.5f, cx + 33.0f, 7.35f, 1938.5f, WadakTownTerracotta, WadakTownSlate, WadakTownTerracotta)
    addBox(cx + 17.8f, 3.35f, 1908.0f, cx + 18.06f, 5.55f, 1936.0f, Color(0xFF1F3448))

    val robSignSpec = createShoranurBannerSignSpec(
        cacheKey = "WADAKKANCHERY_ROB_SIGN_V1",
        title = "WADAKKANCHERY RAILWAY OVERBRIDGE",
        subtitle = "SH 22  •  SHORANUR 16 KM  •  THRISSUR 17 KM",
        bgColorInt = 0xFF065F46.toInt()
    )
    addBox(cx - hw - 0.55f, 0.05f, 1513.7f, cx - hw - 0.05f, 6.85f, 1514.3f, RobSteelGirder)
    addBox(cx + hw + 0.05f, 0.05f, 1513.7f, cx + hw + 0.55f, 6.85f, 1514.3f, RobSteelGirder)
    addBox(cx - hw - 0.55f, 6.35f, 1513.75f, cx + hw + 0.55f, 6.85f, 1514.25f, RobSteelHighlight)
    faces.add(
        QuadFace3D(
            Vec3(cx - 5.6f, 5.55f, 1513.70f),
            Vec3(cx + 5.6f, 5.55f, 1513.70f),
            Vec3(cx + 5.6f, 7.25f, 1513.70f),
            Vec3(cx - 5.6f, 7.25f, 1513.70f),
            baseColor = Color(0xFF065F46),
            biasDist = -1.2f,
            signboard = robSignSpec
        )
    )

    faces
}

internal fun collectWadakkancheryRobAndTownFaces(
    camEye: Vec3,
    outFaces: MutableList<QuadFace3D>
) {
    if (hypot(camEye.x - ShoranurRailwaySystem.ROB_CENTER_X, camEye.z - ShoranurRailwaySystem.ROB_TRACK_CROSS_Z) > 520f) return
    outFaces.addAll(cachedWadakkancheryRobFaces)
}

internal fun collectAllWadakkancheryRobStaticFaces(outFaces: MutableList<QuadFace3D>) {
    outFaces.addAll(cachedWadakkancheryRobFaces)
}

internal fun createShoranurBannerSignSpec(
    cacheKey: String,
    title: String,
    subtitle: String,
    bgColorInt: Int
): SignboardQuadSpec = SignboardQuadSpec(
    cacheKey = cacheKey,
    bmpWidth = 1024,
    bmpHeight = 256,
    isDoubleSided = true,
    renderBitmap = {
        val bmp = android.graphics.Bitmap.createBitmap(1024, 256, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(bgColorInt)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = 8f
        p.color = android.graphics.Color.WHITE
        c.drawRect(12f, 12f, 1012f, 244f, p)

        p.style = android.graphics.Paint.Style.FILL
        p.textAlign = android.graphics.Paint.Align.CENTER
        p.typeface = com.bharatbussim.game.ui.theme.PremiumFonts.displayTypefaceFor(title, bold = true)
        p.color = android.graphics.Color.WHITE
        p.textSize = 56f
        val tw = p.measureText(title)
        if (tw > 940f) p.textSize = 56f * (940f / tw)
        c.drawText(title, 512f, 118f, p)

        p.typeface = com.bharatbussim.game.ui.theme.PremiumFonts.uiTypefaceFor(subtitle, bold = true)
        p.color = 0xFFFEF08A.toInt()
        p.textSize = 36f
        val sw = p.measureText(subtitle)
        if (sw > 940f) p.textSize = 36f * (940f / sw)
        c.drawText(subtitle, 512f, 194f, p)
        bmp
    }
)
