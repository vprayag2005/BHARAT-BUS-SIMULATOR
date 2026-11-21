package com.bharatbussim.game.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class ImpactMaterial(val displayLabel: String) {
    CONCRETE_BUILDING("Building"),
    TREE_WOOD("Tree"),
    METAL_SIGN_POLE("Signboard Post"),
    STONE_WALL("Compound Wall"),
    TRAFFIC_VEHICLE("Traffic Vehicle")
}

data class CollisionResult(
    val resolvedPos: Vec3,
    val hit: Boolean,
    val material: ImpactMaterial = ImpactMaterial.CONCRETE_BUILDING,
    val normalX: Float = 0f,
    val normalZ: Float = 0f,
    val impactSpeedMps: Float = 0f,
    val obstacleLabel: String = ""
)

data class ObbObstacleInput(
    val cx: Float,
    val cz: Float,
    val cosYaw: Float,
    val sinYaw: Float,
    val halfX: Float,
    val halfZ: Float,
    val material: ImpactMaterial,
    val label: String
)

data class CircleObstacleInput(
    val cx: Float,
    val cz: Float,
    val radius: Float,
    val material: ImpactMaterial,
    val label: String
)

/**
 * High-performance spatial-hashed 2D/2.5D solid collision engine for Thrissur District.
 *
 * Represents the 12m Hino RM 2 bus as 3 overlapping spine circles (rear, center, front)
 * for smooth sliding along walls, zero corner snagging, and exact minimum-translation-vector
 * push-out against buildings, terminal walls, gate pillars, trees, signboard poles, and
 * Swaraj Round's inner laterite wall — while preserving every drivable road, bay, and gate portal.
 */
object ThrissurCollisionSystem {

    private sealed class Collider(
        val material: ImpactMaterial,
        val label: String
    ) {
        class Aabb(
            val minX: Float,
            val minZ: Float,
            val maxX: Float,
            val maxZ: Float,
            material: ImpactMaterial,
            label: String
        ) : Collider(material, label)

        class Obb(
            val cx: Float,
            val cz: Float,
            val cosYaw: Float,
            val sinYaw: Float,
            val halfX: Float,
            val halfZ: Float,
            val boundR: Float,
            material: ImpactMaterial,
            label: String
        ) : Collider(material, label)

        class Circle(
            val cx: Float,
            val cz: Float,
            val radius: Float,
            material: ImpactMaterial,
            label: String
        ) : Collider(material, label)

        class Segment(
            val x0: Float,
            val z0: Float,
            val x1: Float,
            val z1: Float,
            val dx: Float,
            val dz: Float,
            val lenSq: Float,
            val halfThick: Float,
            material: ImpactMaterial,
            label: String
        ) : Collider(material, label)
    }

    private const val CELL_SIZE = 24.0f
    private const val INV_CELL_SIZE = 1f / CELL_SIZE

    // 12m Hino RM 2 coach spine circle configuration (width ~2.48m, effective collision length ~10.88m)
    private const val BUS_CIRCLE_RADIUS = 1.24f
    private val BUS_SPINE_OFFSETS_Z = floatArrayOf(-4.20f, 0.0f, 4.20f)

    @Volatile
    private var spatialGrid: HashMap<Long, ArrayList<Collider>> = HashMap()

    @Volatile
    private var isBuilt = false

    private fun cellKey(cx: Int, cz: Int): Long =
        (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    fun rebuild(
        buildingObbs: List<ObbObstacleInput>,
        treeCircles: List<CircleObstacleInput>,
        signPostCircles: List<CircleObstacleInput>
    ) {
        val grid = HashMap<Long, ArrayList<Collider>>(1024)

        fun register(c: Collider, minX: Float, minZ: Float, maxX: Float, maxZ: Float) {
            val gx0 = floor(minX * INV_CELL_SIZE).toInt()
            val gx1 = floor(maxX * INV_CELL_SIZE).toInt()
            val gz0 = floor(minZ * INV_CELL_SIZE).toInt()
            val gz1 = floor(maxZ * INV_CELL_SIZE).toInt()
            for (gx in gx0..gx1) {
                for (gz in gz0..gz1) {
                    grid.getOrPut(cellKey(gx, gz)) { ArrayList(8) }.add(c)
                }
            }
        }

        fun addAabb(minX: Float, minZ: Float, maxX: Float, maxZ: Float, mat: ImpactMaterial, label: String) {
            val c = Collider.Aabb(minX, minZ, maxX, maxZ, mat, label)
            register(c, minX, minZ, maxX, maxZ)
        }

        fun addCircle(cx: Float, cz: Float, r: Float, mat: ImpactMaterial, label: String) {
            val c = Collider.Circle(cx, cz, r, mat, label)
            register(c, cx - r, cz - r, cx + r, cz + r)
        }

        fun addWallPolyline(pts: List<Pair<Float, Float>>, halfThick: Float, mat: ImpactMaterial, label: String) {
            for (i in 0 until pts.size - 1) {
                val (x0, z0) = pts[i]
                val (x1, z1) = pts[i + 1]
                val dx = x1 - x0
                val dz = z1 - z0
                val lenSq = dx * dx + dz * dz
                if (lenSq < 0.002f) continue
                val seg = Collider.Segment(x0, z0, x1, z1, dx, dz, lenSq, halfThick, mat, label)
                register(
                    seg,
                    min(x0, x1) - halfThick,
                    min(z0, z1) - halfThick,
                    max(x0, x1) + halfThick,
                    max(z0, z1) + halfThick
                )
            }
        }

        // =====================================================================
        // 1. SAKTHAN BUS TERMINAL (Wings, Perimeter Walls, Gate Pillars, Canopy Columns)
        //    Front ENTRY (X=0.65..13.75, Z=-140) & Back EXIT (X=20.05..32.95, Z=-207.5) OPEN!
        // =====================================================================
        // South Boarding Wing & East Commercial Wing
        addAabb(-25.0f, -207.2f, 19.5f, -195.2f, ImpactMaterial.CONCRETE_BUILDING, "Sakthan Boarding Wing")
        addAabb(32.2f, -207.2f, 40.5f, -143.5f, ImpactMaterial.CONCRETE_BUILDING, "Sakthan Commercial Wing")
        // Perimeter Compound Walls
        addWallPolyline(
            listOf(Pair(-31.0f, -140.0f), Pair(-31.0f, -207.5f), Pair(19.6f, -207.5f)),
            0.26f, ImpactMaterial.STONE_WALL, "Sakthan Compound Wall"
        )
        addWallPolyline(
            listOf(Pair(33.4f, -207.5f), Pair(40.5f, -207.5f), Pair(40.5f, -140.0f)),
            0.26f, ImpactMaterial.STONE_WALL, "Sakthan Compound Wall"
        )
        addWallPolyline(
            listOf(
                Pair(-31.0f, -140.0f),
                Pair(-17.5f, -140.0f),
                Pair(-13.8f, -141.7f),
                Pair(-9.5f, -143.1f),
                Pair(-4.8f, -141.9f),
                Pair(-1.6f, -140.0f),
                Pair(0.0f, -140.0f)
            ),
            0.26f, ImpactMaterial.STONE_WALL, "Sakthan North Wall"
        )
        addWallPolyline(
            listOf(Pair(14.3f, -140.0f), Pair(40.5f, -140.0f)),
            0.26f, ImpactMaterial.STONE_WALL, "Sakthan North Wall"
        )
        // Front ENTRY & Back EXIT Gate Pillars + Guard Booth
        addAabb(-0.15f, -140.45f, 0.65f, -139.55f, ImpactMaterial.CONCRETE_BUILDING, "Sakthan Entry Pillar")
        addAabb(13.75f, -140.45f, 14.55f, -139.55f, ImpactMaterial.CONCRETE_BUILDING, "Sakthan Entry Pillar")
        addAabb(-2.25f, -141.65f, -0.18f, -139.85f, ImpactMaterial.CONCRETE_BUILDING, "Sakthan Guard Booth")
        addAabb(19.25f, -207.95f, 20.05f, -207.05f, ImpactMaterial.CONCRETE_BUILDING, "Sakthan Exit Pillar")
        addAabb(32.95f, -207.95f, 33.75f, -207.05f, ImpactMaterial.CONCRETE_BUILDING, "Sakthan Exit Pillar")
        // Bay Canopy Steel Support Columns (mounted on divider islands; leaves 1.1m clearance on each bay side)
        for (px in floatArrayOf(-22.25f, -12.25f, -2.25f, 7.75f, 17.75f)) {
            for (pz in floatArrayOf(-188.0f, -173.2f)) {
                addCircle(px, pz, 0.16f, ImpactMaterial.METAL_SIGN_POLE, "Canopy Pillar")
            }
        }

        // =====================================================================
        // 2. VADAKKE BUS STAND (U-Wings, Perimeter Walls, Gate Pillars, Canopy Columns)
        //    West EXIT (X=-53.55..-41.45, Z=200.5) & East ENTRY (X=-27.55..-15.45, Z=200.5) OPEN!
        // =====================================================================
        addAabb(-64.5f, 240.5f, -6.5f, 250.5f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke North Concourse")
        addAabb(-64.5f, 201.0f, -59.0f, 240.5f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke West Pavilion")
        addAabb(-11.5f, 201.0f, -6.5f, 240.5f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke East Wing")
        addWallPolyline(listOf(Pair(-64.5f, 200.5f), Pair(-54.0f, 200.5f)), 0.26f, ImpactMaterial.STONE_WALL, "Vadakke Wall")
        addWallPolyline(listOf(Pair(-41.0f, 200.5f), Pair(-28.0f, 200.5f)), 0.26f, ImpactMaterial.STONE_WALL, "Vadakke Wall")
        addWallPolyline(listOf(Pair(-15.0f, 200.5f), Pair(-6.5f, 200.5f)), 0.26f, ImpactMaterial.STONE_WALL, "Vadakke Wall")
        addWallPolyline(listOf(Pair(-64.5f, 200.5f), Pair(-64.5f, 250.5f)), 0.26f, ImpactMaterial.STONE_WALL, "Vadakke Wall")
        addWallPolyline(listOf(Pair(-6.5f, 200.5f), Pair(-6.5f, 250.5f)), 0.26f, ImpactMaterial.STONE_WALL, "Vadakke Wall")
        addWallPolyline(listOf(Pair(-64.5f, 250.5f), Pair(-6.5f, 250.5f)), 0.26f, ImpactMaterial.STONE_WALL, "Vadakke Wall")
        // Twin Portal Gate Pillars & Guard Booth
        addAabb(-54.35f, 200.05f, -53.55f, 200.95f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke Exit Pillar")
        addAabb(-41.45f, 200.05f, -40.65f, 200.95f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke Exit Pillar")
        addAabb(-28.35f, 200.05f, -27.55f, 200.95f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke Entry Pillar")
        addAabb(-15.45f, 200.05f, -14.65f, 200.95f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke Entry Pillar")
        addAabb(-14.60f, 198.85f, -12.60f, 200.65f, ImpactMaterial.CONCRETE_BUILDING, "Vadakke Guard Booth")

        // =====================================================================
        // 3. THRISSUR RAILWAY STATION BUS TERMINAL & WEST BOARDING PLATFORMS (Real-world SW Thrissur)
        //    South ENTRY (Z=-174.05..-161.95, X=-64) & North EXIT (Z=-114.05..-101.95, X=-64) OPEN!
        //    West side along Track 2 (X=-107.2) is open Platform 1 boarding like Shoranur Junction!
        // =====================================================================
        addAabb(-107.2f, -222.0f, -89.2f, -98.0f, ImpactMaterial.CONCRETE_BUILDING, "Thrissur Station Platform 1")
        addAabb(-124.2f, -218.0f, -116.8f, -100.0f, ImpactMaterial.CONCRETE_BUILDING, "Thrissur Station Platform 2")
        addAabb(-89.5f, -196.0f, -81.9f, -86.0f, ImpactMaterial.CONCRETE_BUILDING, "Railway Station Concourse")
        addWallPolyline(listOf(Pair(-89.2f, -204.0f), Pair(-64.0f, -204.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Station Wall")
        addWallPolyline(listOf(Pair(-89.2f, -62.0f), Pair(-64.0f, -62.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Station Wall")
        addWallPolyline(listOf(Pair(-89.2f, -98.0f), Pair(-89.2f, -62.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Station Wall")
        addWallPolyline(listOf(Pair(-64.0f, -204.0f), Pair(-64.0f, -174.5f)), 0.25f, ImpactMaterial.STONE_WALL, "Station Wall")
        addWallPolyline(listOf(Pair(-64.0f, -161.5f), Pair(-64.0f, -114.5f)), 0.25f, ImpactMaterial.STONE_WALL, "Station Wall")
        addWallPolyline(listOf(Pair(-64.0f, -101.5f), Pair(-64.0f, -62.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Station Wall")
        addAabb(-64.45f, -174.85f, -63.55f, -174.05f, ImpactMaterial.CONCRETE_BUILDING, "Station Entry Pillar")
        addAabb(-64.45f, -161.95f, -63.55f, -161.15f, ImpactMaterial.CONCRETE_BUILDING, "Station Entry Pillar")
        addAabb(-64.45f, -114.85f, -63.55f, -114.05f, ImpactMaterial.CONCRETE_BUILDING, "Station Exit Pillar")
        addAabb(-64.45f, -101.95f, -63.55f, -101.15f, ImpactMaterial.CONCRETE_BUILDING, "Station Exit Pillar")
        addAabb(-63.85f, -140.1f, -61.95f, -138.1f, ImpactMaterial.CONCRETE_BUILDING, "Station Guard Booth")

        // Poothole Southern Railway Viaduct concrete piers (strictly outside flat M.G. Road at Z=-25.5..-8.1!)
        for (pz in floatArrayOf(-31.5f, -2.5f)) {
            addAabb(-115.6f, pz - 0.85f, -113.9f, pz + 0.85f, ImpactMaterial.CONCRETE_BUILDING, "Poothole Rail Viaduct Pier")
            addAabb(-110.1f, pz - 0.85f, -108.4f, pz + 0.85f, ImpactMaterial.CONCRETE_BUILDING, "Poothole Rail Viaduct Pier")
        }

        // =====================================================================
        // 4. SWARAJ ROUND INNER LATERITE WALL & THEKKINKADU MAIDAN
        //    Carriageway runs at R = 58.5m..73.5m; inner boundary wall sits at R = 57.6m
        // =====================================================================
        addCircle(3.1f, 18.0f, 57.6f, ImpactMaterial.STONE_WALL, "Thekkinkadu Maidan Wall")

        // Manikandanal Traffic Signal Mast on M.O. Road South approach
        addCircle(9.6f, -63.0f, 0.45f, ImpactMaterial.METAL_SIGN_POLE, "Manikandanal Signal Post")

        // =====================================================================
        // 5. ZONE 3: PALIYEKKARA 12-LANE FASTag TOLL PLAZA, OLLUR ROB, KURUMALI & CHALAKUDY BRIDGES + GURUVAYUR
        //    Every active toll lane (4.04m clear), outer bypass lane, and bridge carriageway stays 100% unobstructed!
        // =====================================================================
        val tollX = 999.5f
        val tollZ = -1806.0f
        // 13 Concrete Barrier Islands (iIdx = 0..12, physical half-width 0.54m leaving 4.32m clear in every 5.40m lane pitch!)
        for (iIdx in 0..12) {
            val ix = tollX + (iIdx - 6) * 5.40f
            addAabb(ix - 0.54f, tollZ - 10.6f, ix + 0.54f, tollZ + 10.6f, ImpactMaterial.CONCRETE_BUILDING, "Paliakkara Toll Island")
        }
        // Twin 25m High-Mast Floodlight Towers strictly outside the 2 Free Outer Bypass Lanes (X = 953.0 & 1046.0)
        addCircle(953.0f, tollZ + 14.0f, 0.55f, ImpactMaterial.METAL_SIGN_POLE, "Toll High-Mast Light")
        addCircle(1046.0f, tollZ + 14.0f, 0.55f, ImpactMaterial.METAL_SIGN_POLE, "Toll High-Mast Light")
        // 2-Storey Paliakkara Toll Admin & Control Building (East of East Bypass Lane)
        addAabb(1047.5f, tollZ - 18.0f, 1061.5f, tollZ + 14.0f, ImpactMaterial.CONCRETE_BUILDING, "Paliakkara Toll Admin Building")

        // Ollur Railway Overbridge (ROB) Side Parapets (strictly outside 20m road deck along ROAD_NH544_MANNUTHY_TO_PALIYEKKARA)
        val ollurLeftParapet = listOf(
            Pair(955.65f - 10.45f, -1330.0f),
            Pair(958.85f - 10.45f, -1430.0f),
            Pair(962.05f - 10.45f, -1530.0f)
        )
        val ollurRightParapet = listOf(
            Pair(955.65f + 10.45f, -1330.0f),
            Pair(958.85f + 10.45f, -1430.0f),
            Pair(962.05f + 10.45f, -1530.0f)
        )
        addWallPolyline(ollurLeftParapet, 0.35f, ImpactMaterial.STONE_WALL, "Ollur ROB Parapet")
        addWallPolyline(ollurRightParapet, 0.35f, ImpactMaterial.STONE_WALL, "Ollur ROB Parapet")

        // Kurumali River Bridge (Puthukkad, X = 1366.0, Z = -2120..-2380) Side Parapets & Central Median Barrier
        addWallPolyline(listOf(Pair(1366.0f - 10.40f, -2120.0f), Pair(1366.0f - 10.40f, -2380.0f)), 0.35f, ImpactMaterial.STONE_WALL, "Kurumali Bridge Parapet")
        addWallPolyline(listOf(Pair(1366.0f + 10.40f, -2120.0f), Pair(1366.0f + 10.40f, -2380.0f)), 0.35f, ImpactMaterial.STONE_WALL, "Kurumali Bridge Parapet")
        addWallPolyline(listOf(Pair(1366.0f, -2125.0f), Pair(1366.0f, -2375.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Kurumali Bridge Median")

        // 280m Chalakudy River Bridge (X = 2596.0, Z = -4840..-5240) Side Parapets & Central Median Barrier
        addWallPolyline(listOf(Pair(2596.0f - 10.40f, -4840.0f), Pair(2596.0f - 10.40f, -5240.0f)), 0.35f, ImpactMaterial.STONE_WALL, "Chalakudy Bridge Parapet")
        addWallPolyline(listOf(Pair(2596.0f + 10.40f, -4840.0f), Pair(2596.0f + 10.40f, -5240.0f)), 0.35f, ImpactMaterial.STONE_WALL, "Chalakudy Bridge Parapet")
        addWallPolyline(listOf(Pair(2596.0f, -4845.0f), Pair(2596.0f, -5235.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Chalakudy Bridge Median")

        // Chalakudy South Terminus Passenger Shelter Building (strictly East of turnaround apron)
        addAabb(2626.0f, -5398.0f, 2634.5f, -5346.0f, ImpactMaterial.CONCRETE_BUILDING, "Chalakudy Bus Bay Shelter")

        // =====================================================================
        // 5B. ZONE 4: ENAMAVU REGULATOR BRIDGE, MUTHUVARA LC-14, GURUVAYUR TEMPLE & BHARATHAPPUZHA BRIDGES
        //     Every road lane (SH 75, SH 69, Guruvayur Z = 1260 & Bus Stand Driveways, SH 22) stays 100% unobstructed!
        // =====================================================================
        // Enamavu Sluice Regulator Bridge Side Parapets (strictly outside Z = 113.0 ± 5.25m road deck!)
        addWallPolyline(listOf(Pair(-1555.0f, 113.0f - 5.55f), Pair(-1735.0f, 113.0f - 5.55f)), 0.30f, ImpactMaterial.STONE_WALL, "Enamavu Bridge Parapet")
        addWallPolyline(listOf(Pair(-1555.0f, 113.0f + 5.55f), Pair(-1735.0f, 113.0f + 5.55f)), 0.30f, ImpactMaterial.STONE_WALL, "Enamavu Bridge Parapet")

        // Muthuvara Animated LC-14 Gatekeeper Cabin & Pivot Stanchions (strictly outside SH 69 X = -560 ± 5.3m!)
        addAabb(-560.0f + 10.8f, 610.0f + 7.4f, -560.0f + 17.6f, 610.0f + 12.8f, ImpactMaterial.CONCRETE_BUILDING, "Muthuvara LC Gatekeeper Cabin")
        for (gz in floatArrayOf(610.0f - 6.2f, 610.0f + 6.2f)) {
            addCircle(-560.0f - 5.95f, gz, 0.42f, ImpactMaterial.METAL_SIGN_POLE, "Muthuvara LC Gate Stanchion")
            addCircle(-560.0f + 5.95f, gz, 0.42f, ImpactMaterial.METAL_SIGN_POLE, "Muthuvara LC Gate Stanchion")
        }

        // Guruvayur Sree Krishna Temple Compound (FIXED: strictly EAST of road at X = -2620..-2554, Z = 1234..1286!)
        addAabb(-2620.0f, 1234.5f, -2554.0f, 1285.5f, ImpactMaterial.STONE_WALL, "Guruvayur Temple Wall")
        // Guruvayur Bus Terminal Concourse Building (North behind sawtooth bays: Z = 1314.5..1326.5)
        addAabb(-2778.0f, 1314.5f, -2692.0f, 1326.5f, ImpactMaterial.CONCRETE_BUILDING, "Guruvayur Bus Terminal")

        // Bharathappuzha Modern Roadway Bridge Side Parapets on SH 22 (X = 100.0 ± 5.95m, Z = 2630..2950)
        addWallPolyline(listOf(Pair(100.0f - 5.95f, 2630.0f), Pair(100.0f - 5.95f, 2950.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Bharathappuzha Bridge Parapet")
        addWallPolyline(listOf(Pair(100.0f + 5.95f, 2630.0f), Pair(100.0f + 5.95f, 2950.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Bharathappuzha Bridge Parapet")

        // =====================================================================
        // 5C. ZONE 5: NH 66 COASTAL CORRIDOR, NATTIKA 10-LANE TOLL PLAZA, CHETTUVA BRIDGE,
        //     TRIPRAYAR CANOLI CANAL BRIDGE & KOTTAPURAM NW-3 HIGH-CLEARANCE BRIDGE
        //     Every active toll lane (4.36m physical clear), outer bypass lane, and bridge lane stays 100% clear!
        // =====================================================================
        val natX = -2536.0f
        val natZ = -1850.0f
        // 11 Nattika Toll Concrete Islands (iIdx = 0..10, half-width 0.52m leaving 4.36m clear per 5.40m lane!)
        for (iIdx in 0..10) {
            val ix = natX + (iIdx - 5) * 5.40f
            addAabb(ix - 0.52f, natZ - 10.2f, ix + 0.52f, natZ + 10.2f, ImpactMaterial.CONCRETE_BUILDING, "Nattika Toll Island")
        }
        // Twin Nattika High-Mast Floodlight Towers strictly outside West & East Outer Bypass Lanes
        addCircle(natX - 39.2f, natZ + 15.0f, 0.55f, ImpactMaterial.METAL_SIGN_POLE, "Nattika Toll High-Mast")
        addCircle(natX + 39.2f, natZ + 15.0f, 0.55f, ImpactMaterial.METAL_SIGN_POLE, "Nattika Toll High-Mast")

        // Chettuva Backwaters Bridge Side Parapets & Central Median Divider (X = -3664.0 ± 11.35m, Z = 35..395)
        addWallPolyline(listOf(Pair(-3664.0f - 11.35f, 35.0f), Pair(-3664.0f - 11.35f, 395.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Chettuva Bridge Parapet")
        addWallPolyline(listOf(Pair(-3664.0f + 11.35f, 35.0f), Pair(-3664.0f + 11.35f, 395.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Chettuva Bridge Parapet")
        addWallPolyline(listOf(Pair(-3664.0f, 40.0f), Pair(-3664.0f, 390.0f)), 0.28f, ImpactMaterial.STONE_WALL, "Chettuva Bridge Median")

        // Triprayar Canoli Canal Bridge Side Parapets & Sree Rama Temple East Compound (X = -2220.0 ± 11.35m)
        addWallPolyline(listOf(Pair(-2220.0f - 11.35f, -2555.0f), Pair(-2220.0f - 11.35f, -2765.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Triprayar Canal Bridge Parapet")
        addWallPolyline(listOf(Pair(-2220.0f + 11.35f, -2555.0f), Pair(-2220.0f + 11.35f, -2765.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Triprayar Canal Bridge Parapet")
        addAabb(-2188.0f, -2610.0f, -2138.0f, -2578.0f, ImpactMaterial.STONE_WALL, "Triprayar Sree Rama Temple")

        // Kottapuram / Kodungallur NW-3 Bridge Side Parapets, Central Median & Moothakunnam Pavilion (X = -1520.0 ± 11.35m)
        addWallPolyline(listOf(Pair(-1520.0f - 11.35f, -3780.0f), Pair(-1520.0f - 11.35f, -4320.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Kottapuram Bridge Parapet")
        addWallPolyline(listOf(Pair(-1520.0f + 11.35f, -3780.0f), Pair(-1520.0f + 11.35f, -4320.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Kottapuram Bridge Parapet")
        addWallPolyline(listOf(Pair(-1520.0f, -3785.0f), Pair(-1520.0f, -4315.0f)), 0.30f, ImpactMaterial.STONE_WALL, "Kottapuram Bridge Median")
        addAabb(-1491.5f, -4451.0f, -1483.5f, -4399.0f, ImpactMaterial.CONCRETE_BUILDING, "Moothakunnam Bus Pavilion")

        // 5D. OUR LADY OF DOLOURS BASILICA ("PUTHANPALLY"), THRISSUR (X = 78..146, Z = -173..-93)
        //     Front forecourt (Z = -75..-98) remains completely open for bus approach & scenic viewing!
        addAabb(92.0f, -110.0f, 100.8f, -101.0f, ImpactMaterial.CONCRETE_BUILDING, "Puthanpally West Bell Tower")
        addAabb(123.2f, -110.0f, 132.0f, -101.0f, ImpactMaterial.CONCRETE_BUILDING, "Puthanpally East Bell Tower")
        addAabb(95.5f, -146.5f, 128.5f, -103.5f, ImpactMaterial.CONCRETE_BUILDING, "Puthanpally Basilica Nave")
        addAabb(90.5f, -145.5f, 133.5f, -133.5f, ImpactMaterial.CONCRETE_BUILDING, "Puthanpally Basilica Transepts")
        addAabb(104.5f, -165.0f, 119.5f, -150.0f, ImpactMaterial.CONCRETE_BUILDING, "Puthanpally Bible Tower")
        addWallPolyline(listOf(Pair(78.6f, -172.5f), Pair(78.6f, -93.5f)), 0.35f, ImpactMaterial.STONE_WALL, "Puthanpally Compound Wall")
        addWallPolyline(listOf(Pair(145.4f, -172.5f), Pair(145.4f, -93.5f)), 0.35f, ImpactMaterial.STONE_WALL, "Puthanpally Compound Wall")
        addWallPolyline(listOf(Pair(78.6f, -172.4f), Pair(145.4f, -172.4f)), 0.35f, ImpactMaterial.STONE_WALL, "Puthanpally Compound Wall")

        // =====================================================================
        // 6. WADAKKANCHERY ROB & TOWN, SHORANUR BUS TERMINAL & SHORANUR JUNCTION STATION
        // =====================================================================
        // Wadakkanchery ROB West & East Crash Parapets (X = 85.85 & 98.15, Z = 1520..1840) + Gantry Columns
        addWallPolyline(listOf(Pair(85.80f, 1520.0f), Pair(85.80f, 1840.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Wadakkanchery ROB Parapet")
        addWallPolyline(listOf(Pair(98.20f, 1520.0f), Pair(98.20f, 1840.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Wadakkanchery ROB Parapet")
        addCircle(85.50f, 1514.0f, 0.35f, ImpactMaterial.METAL_SIGN_POLE, "Wadakkanchery Gantry")
        addCircle(98.50f, 1514.0f, 0.35f, ImpactMaterial.METAL_SIGN_POLE, "Wadakkanchery Gantry")
        // Wadakkanchery Town Bus Shelter & Commercial Arcade (East of SH 22, X = 101..124, Z = 1866..1938)
        addAabb(101.2f, 1882.0f, 105.9f, 1898.0f, ImpactMaterial.CONCRETE_BUILDING, "Wadakkanchery Bus Shelter")
        addAabb(110.0f, 1866.0f, 124.5f, 1896.0f, ImpactMaterial.CONCRETE_BUILDING, "Wadakkanchery Arcade")
        addAabb(110.0f, 1906.0f, 124.5f, 1938.0f, ImpactMaterial.CONCRETE_BUILDING, "Wadakkanchery Arcade")

        // Shoranur Bus Terminal (X = 126..196, Z = 3015..3135)
        // West Wall Portals OPEN: South ENTRY (Z = 3038..3050), North EXIT (Z = 3100..3112)
        addAabb(179.0f, 3017.0f, 195.8f, 3133.0f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Terminal Pavilion")
        addAabb(153.5f, 3015.5f, 179.0f, 3024.5f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur South Wing")
        addAabb(153.5f, 3125.5f, 179.0f, 3134.5f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur North Wing")
        addWallPolyline(listOf(Pair(126.0f, 3015.0f), Pair(196.0f, 3015.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Terminal Wall")
        addWallPolyline(listOf(Pair(126.0f, 3135.0f), Pair(196.0f, 3135.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Terminal Wall")
        addWallPolyline(listOf(Pair(196.0f, 3015.0f), Pair(196.0f, 3135.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Terminal Wall")
        addWallPolyline(listOf(Pair(126.0f, 3015.0f), Pair(126.0f, 3037.5f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Terminal Wall")
        addWallPolyline(listOf(Pair(126.0f, 3050.5f), Pair(126.0f, 3099.5f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Terminal Wall")
        addWallPolyline(listOf(Pair(126.0f, 3112.5f), Pair(126.0f, 3135.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Terminal Wall")
        // Shoranur Terminal Gate Pillars & Canopy Columns
        addAabb(125.45f, 3037.1f, 126.55f, 3038.0f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Entry Pillar")
        addAabb(125.45f, 3050.0f, 126.55f, 3050.9f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Entry Pillar")
        addAabb(125.45f, 3099.1f, 126.55f, 3100.0f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Exit Pillar")
        addAabb(125.45f, 3112.0f, 126.55f, 3112.9f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Exit Pillar")
        for (pIdx in 0..8 step 2) {
            val iz = 3048.4f + pIdx * 6.20f
            addCircle(158.8f, iz, 0.22f, ImpactMaterial.METAL_SIGN_POLE, "Shoranur Canopy Column")
            addCircle(174.2f, iz, 0.22f, ImpactMaterial.METAL_SIGN_POLE, "Shoranur Canopy Column")
        }

        // Shoranur Junction Railway Station (SRR) (X = 16..74, Z = 3165..3310)
        // East Wall Portals OPEN: South ENTRY (Z = 3192..3204), North EXIT (Z = 3266..3278)
        addAabb(14.2f, 3168.0f, 44.6f, 3306.0f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Junction Station")
        addWallPolyline(listOf(Pair(16.0f, 3165.0f), Pair(74.0f, 3165.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Station Wall")
        addWallPolyline(listOf(Pair(16.0f, 3310.0f), Pair(74.0f, 3310.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Station Wall")
        addWallPolyline(listOf(Pair(74.0f, 3165.0f), Pair(74.0f, 3191.4f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Station Wall")
        addWallPolyline(listOf(Pair(74.0f, 3204.6f), Pair(74.0f, 3265.4f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Station Wall")
        addWallPolyline(listOf(Pair(74.0f, 3278.6f), Pair(74.0f, 3310.0f)), 0.25f, ImpactMaterial.STONE_WALL, "Shoranur Station Wall")
        // Shoranur Station Gate Pillars, Guard Booth & Portico Columns
        addAabb(73.45f, 3191.05f, 74.55f, 3191.95f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Station Entry Pillar")
        addAabb(73.45f, 3204.05f, 74.55f, 3204.95f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Station Entry Pillar")
        addAabb(73.45f, 3265.05f, 74.55f, 3265.95f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Station Exit Pillar")
        addAabb(73.45f, 3278.05f, 74.55f, 3278.95f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Station Exit Pillar")
        addAabb(74.2f, 3232.0f, 77.4f, 3238.0f, ImpactMaterial.CONCRETE_BUILDING, "Shoranur Station Guard Booth")
        var pfColZ = 3202.0f
        while (pfColZ <= 3274.0f) {
            addCircle(49.44f, pfColZ, 0.24f, ImpactMaterial.METAL_SIGN_POLE, "Station Portico Column")
            pfColZ += 12.0f
        }

        // =====================================================================
        // 6B. ZONE 2: MANNUTHY 4-WAY INTERCHANGE, NH 544 MEDIAN, KUTHIRAN TWIN TUNNEL & PEECHI DAM
        //     Every collider sits strictly OUTSIDE drivable carriageways!
        // =====================================================================
        // Mannuthy 25m High-Mast Floodlight Pylon plinth (NW Island) + North Gantry columns
        addAabb(1009.4f, 235.4f, 1014.6f, 240.6f, ImpactMaterial.CONCRETE_BUILDING, "Mannuthy High-Mast Pylon")
        addCircle(1041.2f - 14.8f, 365.0f, 0.55f, ImpactMaterial.METAL_SIGN_POLE, "NH 544 Gantry Column")
        addCircle(1041.2f + 14.8f, 365.0f, 0.55f, ImpactMaterial.METAL_SIGN_POLE, "NH 544 Gantry Column")

        // NH 544 6-Lane Divided New Jersey Concrete Median Barrier (Mannuthy -> Kuthiran, stopping 48m from Mannuthy & 32m from fork)
        addWallPolyline(
            listOf(
                Pair(1046.5f, 248.0f),
                Pair(1043.0f, 305.0f),
                Pair(1041.0f, 355.0f),
                Pair(1042.0f, 415.0f),
                Pair(1052.0f, 485.0f),
                Pair(1078.0f, 565.0f),
                Pair(1122.0f, 650.0f),
                Pair(1185.0f, 740.0f),
                Pair(1265.0f, 830.0f),
                Pair(1365.0f, 915.0f),
                Pair(1480.0f, 990.0f),
                Pair(1610.0f, 1055.0f),
                Pair(1745.0f, 1105.0f),
                Pair(1875.0f, 1134.0f),
                Pair(1968.0f, 1140.0f)
            ),
            0.44f, ImpactMaterial.STONE_WALL, "NH 544 Median Barrier"
        )
        // NH 544 East of Kuthiran New Jersey Concrete Median Barrier
        addWallPolyline(
            listOf(Pair(3308.0f, 1140.0f), Pair(3632.0f, 1140.0f)),
            0.44f, ImpactMaterial.STONE_WALL, "NH 544 Median Barrier"
        )

        // Kuthiran Twin-Tube Tunnel Sidewalk Curbs, Vault Walls, Dividing Rock Pillar & Portal Buttresses
        // Leaves full 10.5m 3-lane asphalt open inside BOTH Left Tube (Z=1148.75..1159.25) & Right Tube (Z=1120.75..1131.25)!
        addAabb(2156.0f, 1159.25f, 3119.0f, 1172.0f, ImpactMaterial.CONCRETE_BUILDING, "Kuthiran Tunnel Wall")
        addAabb(2154.8f, 1131.25f, 3120.2f, 1148.75f, ImpactMaterial.CONCRETE_BUILDING, "Kuthiran Central Rock Pillar")
        addAabb(2156.0f, 1108.0f, 3119.0f, 1120.75f, ImpactMaterial.CONCRETE_BUILDING, "Kuthiran Tunnel Wall")
        // West & East Coral-Pink / Sky-Blue Tunnel Control Houses & Annex Sheds (>5.1m clear of both tubes!)
        addAabb(2143.8f, 1134.8f, 2153.2f, 1143.6f, ImpactMaterial.CONCRETE_BUILDING, "Kuthiran Tunnel Control House")
        addAabb(3121.8f, 1134.8f, 3131.2f, 1143.6f, ImpactMaterial.CONCRETE_BUILDING, "Kuthiran Tunnel Control House")
        // Relocated VMS Gantry Columns (strictly outside outer lanes at X = 2014 / 3266)
        for (gx in floatArrayOf(2014.0f, 3266.0f)) {
            for (gz in floatArrayOf(1117.0f, 1140.0f, 1163.0f)) {
                addCircle(gx, gz, 0.50f, ImpactMaterial.METAL_SIGN_POLE, "Kuthiran VMS Gantry")
            }
        }

        // Peechi-Vazhani Wildlife Sanctuary Check-Post Pillars & Forest Guard Booth (strictly clear of 14.5m SH 51!)
        addCircle(3123.09f - 0.625f * 10.6f, 51.27f - (-0.781f) * 10.6f, 0.80f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Check-Post Pillar")
        addCircle(3123.09f + 0.625f * 10.6f, 51.27f + (-0.781f) * 10.6f, 0.80f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Check-Post Pillar")
        val pBoothX = 3123.09f + 0.625f * 13.4f
        val pBoothZ = 51.27f + (-0.781f) * 13.4f
        addAabb(pBoothX - 2.1f, pBoothZ - 2.1f, pBoothX + 2.1f, pBoothZ + 2.1f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Forest Guard Booth")

        // Peechi Dam Walled Bus Terminal Compound (X = 3412..3454, Z = 72..178)
        // West Wall Portals OPEN: South ENTRY (Z = 92.0..104.0), North EXIT (Z = 146.0..158.0)!
        addWallPolyline(listOf(Pair(3412.0f, 72.0f), Pair(3454.0f, 72.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Peechi Compound Wall")
        addWallPolyline(listOf(Pair(3412.0f, 178.0f), Pair(3454.0f, 178.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Peechi Compound Wall")
        addWallPolyline(listOf(Pair(3454.0f, 72.0f), Pair(3454.0f, 178.0f)), 0.36f, ImpactMaterial.STONE_WALL, "Peechi Dam Viewpoint Balustrade")
        addWallPolyline(listOf(Pair(3412.0f, 72.0f), Pair(3412.0f, 91.2f)), 0.34f, ImpactMaterial.STONE_WALL, "Peechi Compound Wall")
        addWallPolyline(listOf(Pair(3412.0f, 104.8f), Pair(3412.0f, 145.2f)), 0.34f, ImpactMaterial.STONE_WALL, "Peechi Compound Wall")
        addWallPolyline(listOf(Pair(3412.0f, 158.8f), Pair(3412.0f, 178.0f)), 0.34f, ImpactMaterial.STONE_WALL, "Peechi Compound Wall")
        // Entry & Exit Gate Pillars (12.0m clear openings!) + Security Booth + Center Monument + DTPC Tourist Pavilion
        addAabb(3411.05f, 90.4f, 3412.95f, 92.0f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Entry Gate Pillar")
        addAabb(3411.05f, 104.0f, 3412.95f, 105.6f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Entry Gate Pillar")
        addAabb(3411.05f, 144.4f, 3412.95f, 146.0f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Exit Gate Pillar")
        addAabb(3411.05f, 158.0f, 3412.95f, 159.6f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Exit Gate Pillar")
        addAabb(3408.6f, 119.5f, 3411.6f, 130.5f, ImpactMaterial.STONE_WALL, "Peechi Dam Monument")
        addAabb(3412.6f, 105.6f, 3416.2f, 110.2f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Security Booth")
        addAabb(3443.8f, 105.5f, 3452.6f, 144.5f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Tourist Pavilion")
        // Monumental Peechi Dam Masonry Gravity Wall
        addAabb(3455.0f, -120.0f, 3486.0f, 365.0f, ImpactMaterial.CONCRETE_BUILDING, "Peechi Dam Masonry Wall")

        // =====================================================================
        // 6. ZONE 1 GOOGLE DRIVE 3D BUILDINGS (OBBs)
        // =====================================================================
        for (i in 0 until buildingObbs.size) {
            val b = buildingObbs[i]
            val hx = (b.halfX * 0.94f).coerceAtLeast(0.6f)
            val hz = (b.halfZ * 0.94f).coerceAtLeast(0.6f)
            val boundR = sqrt(hx * hx + hz * hz)
            val obb = Collider.Obb(
                cx = b.cx,
                cz = b.cz,
                cosYaw = b.cosYaw,
                sinYaw = b.sinYaw,
                halfX = hx,
                halfZ = hz,
                boundR = boundR,
                material = b.material,
                label = b.label
            )
            register(obb, b.cx - boundR, b.cz - boundR, b.cx + boundR, b.cz + boundR)
        }

        // =====================================================================
        // 7. TREES (Zone 1 GDrive Trunks + Roadside Trees across Thrissur)
        // =====================================================================
        for (i in 0 until treeCircles.size) {
            val t = treeCircles[i]
            addCircle(t.cx, t.cz, t.radius, t.material, t.label)
        }

        // =====================================================================
        // 8. DIRECTION SIGNBOARD SUPPORT POSTS & OVERHEAD GANTRY COLUMNS
        // =====================================================================
        for (i in 0 until signPostCircles.size) {
            val s = signPostCircles[i]
            addCircle(s.cx, s.cz, s.radius, s.material, s.label)
        }

        spatialGrid = grid
        isBuilt = true
    }

    /**
     * Resolves bus collisions against all nearby solid obstacles using 3 spine circles
     * and up to 3 solver passes for clean corner sliding with zero tunneling.
     */
    fun resolveBusCollision(
        candidatePos: Vec3,
        busHeadingRad: Float,
        velX: Float,
        velZ: Float
    ): CollisionResult {
        if (!isBuilt) {
            return CollisionResult(candidatePos, hit = false)
        }
        val grid = spatialGrid
        val sinH = sin(busHeadingRad)
        val cosH = cos(busHeadingRad)

        var posX = candidatePos.x
        var posZ = candidatePos.z

        var anyHit = false
        var bestMaterial = ImpactMaterial.CONCRETE_BUILDING
        var bestLabel = ""
        var strongestPushSq = 0f
        var hitNormX = 0f
        var hitNormZ = 0f

        val checked = HashSet<Collider>(32)

        for (iter in 0 until 3) {
            var iterPushed = false
            for (sIdx in 0 until BUS_SPINE_OFFSETS_Z.size) {
                val offZ = BUS_SPINE_OFFSETS_Z[sIdx]
                val cx = posX + sinH * offZ
                val cz = posZ + cosH * offZ

                val gx0 = floor((cx - BUS_CIRCLE_RADIUS - 1.0f) * INV_CELL_SIZE).toInt()
                val gx1 = floor((cx + BUS_CIRCLE_RADIUS + 1.0f) * INV_CELL_SIZE).toInt()
                val gz0 = floor((cz - BUS_CIRCLE_RADIUS - 1.0f) * INV_CELL_SIZE).toInt()
                val gz1 = floor((cz + BUS_CIRCLE_RADIUS + 1.0f) * INV_CELL_SIZE).toInt()

                checked.clear()
                for (gx in gx0..gx1) {
                    for (gz in gz0..gz1) {
                        val bucket = grid[cellKey(gx, gz)] ?: continue
                        for (bIdx in 0 until bucket.size) {
                            val col = bucket[bIdx]
                            if (!checked.add(col)) continue

                            var pushX = 0f
                            var pushZ = 0f

                            when (col) {
                                is Collider.Aabb -> {
                                    val closestX = cx.coerceIn(col.minX, col.maxX)
                                    val closestZ = cz.coerceIn(col.minZ, col.maxZ)
                                    val dx = cx - closestX
                                    val dz = cz - closestZ
                                    val distSq = dx * dx + dz * dz
                                    if (distSq < BUS_CIRCLE_RADIUS * BUS_CIRCLE_RADIUS) {
                                        if (distSq > 1e-6f) {
                                            val dist = sqrt(distSq)
                                            val pen = BUS_CIRCLE_RADIUS - dist
                                            pushX = (dx / dist) * pen
                                            pushZ = (dz / dist) * pen
                                        } else {
                                            // Circle center strictly inside AABB -> push out along shallowest edge
                                            val dLeft = cx - col.minX
                                            val dRight = col.maxX - cx
                                            val dBot = cz - col.minZ
                                            val dTop = col.maxZ - cz
                                            val m = min(min(dLeft, dRight), min(dBot, dTop))
                                            when (m) {
                                                dLeft -> pushX = -(dLeft + BUS_CIRCLE_RADIUS)
                                                dRight -> pushX = dRight + BUS_CIRCLE_RADIUS
                                                dBot -> pushZ = -(dBot + BUS_CIRCLE_RADIUS)
                                                else -> pushZ = dTop + BUS_CIRCLE_RADIUS
                                            }
                                        }
                                    }
                                }

                                is Collider.Obb -> {
                                    val relX = cx - col.cx
                                    val relZ = cz - col.cz
                                    val maxR = col.boundR + BUS_CIRCLE_RADIUS
                                    if (relX * relX + relZ * relZ < maxR * maxR) {
                                        // Transform circle center into OBB local frame
                                        // World: wx = cx + lx*cos - lz*sin, wz = cz + lx*sin + lz*cos
                                        // Inverse: lx = relX*cos + relZ*sin, lz = -relX*sin + relZ*cos
                                        val lx = relX * col.cosYaw + relZ * col.sinYaw
                                        val lz = -relX * col.sinYaw + relZ * col.cosYaw
                                        val clampedX = lx.coerceIn(-col.halfX, col.halfX)
                                        val clampedZ = lz.coerceIn(-col.halfZ, col.halfZ)
                                        val dlx = lx - clampedX
                                        val dlz = lz - clampedZ
                                        val distSq = dlx * dlx + dlz * dlz
                                        if (distSq < BUS_CIRCLE_RADIUS * BUS_CIRCLE_RADIUS) {
                                            var localPushX = 0f
                                            var localPushZ = 0f
                                            if (distSq > 1e-6f) {
                                                val dist = sqrt(distSq)
                                                val pen = BUS_CIRCLE_RADIUS - dist
                                                localPushX = (dlx / dist) * pen
                                                localPushZ = (dlz / dist) * pen
                                            } else {
                                                val penX = col.halfX - abs(lx)
                                                val penZ = col.halfZ - abs(lz)
                                                if (penX < penZ) {
                                                    localPushX = if (lx >= 0f) penX + BUS_CIRCLE_RADIUS else -(penX + BUS_CIRCLE_RADIUS)
                                                } else {
                                                    localPushZ = if (lz >= 0f) penZ + BUS_CIRCLE_RADIUS else -(penZ + BUS_CIRCLE_RADIUS)
                                                }
                                            }
                                            pushX = localPushX * col.cosYaw - localPushZ * col.sinYaw
                                            pushZ = localPushX * col.sinYaw + localPushZ * col.cosYaw
                                        }
                                    }
                                }

                                is Collider.Circle -> {
                                    val dx = cx - col.cx
                                    val dz = cz - col.cz
                                    val minDist = BUS_CIRCLE_RADIUS + col.radius
                                    val distSq = dx * dx + dz * dz
                                    if (distSq < minDist * minDist) {
                                        if (distSq > 1e-6f) {
                                            val dist = sqrt(distSq)
                                            val pen = minDist - dist
                                            pushX = (dx / dist) * pen
                                            pushZ = (dz / dist) * pen
                                        } else {
                                            pushX = minDist
                                        }
                                    }
                                }

                                is Collider.Segment -> {
                                    val t = (((cx - col.x0) * col.dx + (cz - col.z0) * col.dz) / col.lenSq).coerceIn(0f, 1f)
                                    val px = col.x0 + col.dx * t
                                    val pz = col.z0 + col.dz * t
                                    val dx = cx - px
                                    val dz = cz - pz
                                    val minDist = BUS_CIRCLE_RADIUS + col.halfThick
                                    val distSq = dx * dx + dz * dz
                                    if (distSq < minDist * minDist) {
                                        if (distSq > 1e-6f) {
                                            val dist = sqrt(distSq)
                                            val pen = minDist - dist
                                            pushX = (dx / dist) * pen
                                            pushZ = (dz / dist) * pen
                                        } else {
                                            val len = sqrt(col.lenSq).coerceAtLeast(1e-4f)
                                            pushX = (-col.dz / len) * minDist
                                            pushZ = (col.dx / len) * minDist
                                        }
                                    }
                                }
                            }

                            val pSq = pushX * pushX + pushZ * pushZ
                            if (pSq > 1e-7f) {
                                posX += pushX
                                posZ += pushZ
                                iterPushed = true
                                anyHit = true
                                if (pSq > strongestPushSq) {
                                    strongestPushSq = pSq
                                    val pLen = sqrt(pSq)
                                    hitNormX = pushX / pLen
                                    hitNormZ = pushZ / pLen
                                    bestMaterial = col.material
                                    bestLabel = col.label
                                }
                            }
                        }
                    }
                }
            }
            // Dynamic AI Traffic Vehicle OBB Collision (keeps queued vehicles at toll plazas & railway gates 100% solid!)
            val (trafficBuf, trafficCount) = ThrissurTrafficSystem.getReadSnapshot()
            if (trafficCount > 0) {
                for (tIdx in 0 until trafficCount) {
                    val tv = trafficBuf[tIdx]
                    val dxCenter = posX - tv.cx
                    val dzCenter = posZ - tv.cz
                    if (dxCenter * dxCenter + dzCenter * dzCenter > 17.5f * 17.5f) continue

                    val cosV = cos(tv.headingRad)
                    val sinV = sin(tv.headingRad)
                    val vHalfX = (tv.halfX * 0.94f).coerceAtLeast(0.62f)
                    val vHalfZ = (tv.halfZ * 0.95f).coerceAtLeast(1.15f)
                    val vBoundR = sqrt(vHalfX * vHalfX + vHalfZ * vHalfZ) + BUS_CIRCLE_RADIUS

                    for (sIdx in 0 until BUS_SPINE_OFFSETS_Z.size) {
                        val offZ = BUS_SPINE_OFFSETS_Z[sIdx]
                        val cx = posX + sinH * offZ
                        val cz = posZ + cosH * offZ
                        val relX = cx - tv.cx
                        val relZ = cz - tv.cz
                        if (relX * relX + relZ * relZ >= vBoundR * vBoundR) continue

                        // Local frame matching headingRad = atan2(dirX, dirZ):
                        // local +Z -> (+sinV, +cosV), local +X -> (+cosV, -sinV)
                        val lx = relX * cosV - relZ * sinV
                        val lz = relX * sinV + relZ * cosV
                        val clampedX = lx.coerceIn(-vHalfX, vHalfX)
                        val clampedZ = lz.coerceIn(-vHalfZ, vHalfZ)
                        val dlx = lx - clampedX
                        val dlz = lz - clampedZ
                        val distSq = dlx * dlx + dlz * dlz
                        if (distSq < BUS_CIRCLE_RADIUS * BUS_CIRCLE_RADIUS) {
                            var localPushX = 0f
                            var localPushZ = 0f
                            if (distSq > 1e-6f) {
                                val dist = sqrt(distSq)
                                val pen = BUS_CIRCLE_RADIUS - dist
                                localPushX = (dlx / dist) * pen
                                localPushZ = (dlz / dist) * pen
                            } else {
                                val penX = vHalfX - abs(lx)
                                val penZ = vHalfZ - abs(lz)
                                if (penX < penZ) {
                                    localPushX = if (lx >= 0f) penX + BUS_CIRCLE_RADIUS else -(penX + BUS_CIRCLE_RADIUS)
                                } else {
                                    localPushZ = if (lz >= 0f) penZ + BUS_CIRCLE_RADIUS else -(penZ + BUS_CIRCLE_RADIUS)
                                }
                            }
                            val pushX = localPushX * cosV + localPushZ * sinV
                            val pushZ = -localPushX * sinV + localPushZ * cosV
                            val pSq = pushX * pushX + pushZ * pushZ
                            if (pSq > 1e-7f) {
                                posX += pushX
                                posZ += pushZ
                                iterPushed = true
                                anyHit = true
                                if (pSq > strongestPushSq) {
                                    strongestPushSq = pSq
                                    val pLen = sqrt(pSq)
                                    hitNormX = pushX / pLen
                                    hitNormZ = pushZ / pLen
                                    bestMaterial = ImpactMaterial.TRAFFIC_VEHICLE
                                    bestLabel = tv.label
                                }
                            }
                        }
                    }
                }
            }
            if (!iterPushed) break
        }

        if (!anyHit) {
            return CollisionResult(candidatePos, hit = false)
        }

        // Compute inward impact speed along the collision normal (positive when moving into the obstacle)
        val inwardSpeed = max(0f, -(velX * hitNormX + velZ * hitNormZ))
        return CollisionResult(
            resolvedPos = candidatePos.copy(x = posX, z = posZ),
            hit = true,
            material = bestMaterial,
            normalX = hitNormX,
            normalZ = hitNormZ,
            impactSpeedMps = inwardSpeed,
            obstacleLabel = bestLabel
        )
    }
}
