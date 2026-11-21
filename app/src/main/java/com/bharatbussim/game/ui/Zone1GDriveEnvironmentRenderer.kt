package com.bharatbussim.game.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurRoadRepository
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Loads the real 3D Building, Tree, and Grass models compiled from the Google Drive pack
 * (`assets/zone1_gdrive_env_meshes.bin` in Z1M2 indexed format + `assets/zone1_gdrive_atlas.png`
 * RGBA texture atlas) and places them EXCLUSIVELY inside Zone 1 (Thrissur City Core:
 * Swaraj Round, Sakthan Stand, KSRTC Stand, Thrissur Railway Station, Vadakke Stand, and
 * Zone 1 arterial connectors).
 *
 * Strictly enforces:
 * 1. Zone 1 spatial boundary (`X in -280..260`, `Z in -280..265`). Zones 2-5 are untouched.
 * 2. Road & Shoulder Clearance: ZERO objects overlap any road carriageway, shoulder, footpath,
 *    junction bellmouth, bus bay, railway platform/track, or road signboard.
 * 3. Mutual Non-Overlap: Every placed Building, Tree, and Grass clump is checked with a 2D
 *    Oriented Bounding Box (OBB) Separating Axis Theorem (SAT) test against all previously
 *    placed instances so no two objects ever intersect or overlap each other.
 */
internal object Zone1GDriveEnvironmentRenderer {

    const val CAT_BUILDING = 0
    const val CAT_TREE = 1
    const val CAT_GRASS = 2

    // Strict Zone 1 Bounding Box (covers Swaraj Round, Vadakke Stand, Sakthan Stand, TCR Station)
    const val ZONE1_MIN_X = -280f
    const val ZONE1_MAX_X = 260f
    const val ZONE1_MIN_Z = -280f
    const val ZONE1_MAX_Z = 265f

    /**
     * Indexed 3D model template loaded from `zone1_gdrive_env_meshes.bin` (Z1M2).
     *
     * Vertex layout in `gpuVertexFloats` (9 floats = 36 bytes per vertex):
     *   [0..2] px, py, pz (meters, model-local with minY = 0)
     *   [3..5] nx, ny, nz (unit normal)
     *   [6..7] u, v       (OpenGL texture coordinates)
     *   [8]    packedMat  (`texLayer + (flags * 64f)`, where bit 0 of flags = isAlphaCutout)
     */
    class LoadedGDriveModel(
        val id: String,
        val category: Int,
        val halfX: Float,
        val halfZ: Float,
        val heightY: Float,
        val vertCount: Int,
        val triCount: Int,
        val gpuVertexFloats: FloatArray,
        val gpuIndicesShorts: ShortArray
    )

    class PlacedInstance(
        val modelIndex: Int,
        val model: LoadedGDriveModel,
        val cx: Float,
        val baseY: Float,
        val cz: Float,
        val yawRad: Float,
        val cosYaw: Float,
        val sinYaw: Float,
        val scale: Float,
        val scaledHalfX: Float,
        val scaledHalfZ: Float,
        val cullRadiusXZ: Float
    )

    private data class RoadSeg2D(
        val x0: Float,
        val z0: Float,
        val x1: Float,
        val z1: Float,
        val dx: Float,
        val dz: Float,
        val lenSq: Float,
        val clearanceRadius: Float,
        val minX: Float,
        val maxX: Float,
        val minZ: Float,
        val maxZ: Float
    )

    @Volatile
    private var cachedModels: List<LoadedGDriveModel>? = null

    @Volatile
    private var cachedInstances: List<PlacedInstance>? = null

    @Volatile
    private var cachedRoadSegs: List<RoadSeg2D>? = null

    fun ensureLoaded(context: Context, roads: List<ThrissurOsmRoad> = emptyList()): List<LoadedGDriveModel> {
        val models = cachedModels ?: synchronized(this) {
            cachedModels ?: try {
                context.assets.open("zone1_gdrive_env_meshes.bin").use { raw ->
                    BufferedInputStream(raw, 65536).use { input ->
                        val allBytes = input.readBytes()
                        parseBinaryMeshes(allBytes)
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }.also { cachedModels = it }
        }
        if (roads.isNotEmpty() && cachedInstances == null && models.isNotEmpty()) {
            getOrBuildInstances(context, roads)
        }
        return models
    }

    fun getLoadedModels(): List<LoadedGDriveModel> = cachedModels ?: emptyList()

    fun getPlacedInstances(): List<PlacedInstance> = cachedInstances ?: emptyList()

    internal fun parseBinaryMeshes(bytes: ByteArray): List<LoadedGDriveModel> {
        if (bytes.size < 8) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val m0 = buf.get().toInt().toChar()
        val m1 = buf.get().toInt().toChar()
        val m2 = buf.get().toInt().toChar()
        val m3 = buf.get().toInt().toChar()
        if (m0 != 'Z' || m1 != '1' || m2 != 'M' || (m3 != '2' && m3 != '1')) return emptyList()

        val modelCount = buf.int
        val out = ArrayList<LoadedGDriveModel>(modelCount)

        for (m in 0 until modelCount) {
            val idLen = buf.short.toInt() and 0xFFFF
            val cat = buf.get().toInt() and 0xFF
            val halfX = buf.float
            val halfZ = buf.float
            val heightY = buf.float
            val vertCount = buf.int
            val triCount = buf.int
            val idBytes = ByteArray(idLen)
            buf.get(idBytes)
            val id = String(idBytes, Charsets.UTF_8)

            val gpuVerts = FloatArray(vertCount * 9)
            for (v in 0 until vertCount) {
                val px = buf.short * 0.001f
                val py = buf.short * 0.001f
                val pz = buf.short * 0.001f
                val nx = buf.get() / 127f
                val ny = buf.get() / 127f
                val nz = buf.get() / 127f
                val u = buf.short * 0.0005f
                val uvV = buf.short * 0.0005f
                val texLayer = buf.get().toInt() and 0xFF
                val flags = buf.get().toInt() and 0xFF
                // Skip fallback RGB bytes (3 bytes)
                buf.get()
                buf.get()
                buf.get()

                val base = v * 9
                gpuVerts[base] = px
                gpuVerts[base + 1] = py
                gpuVerts[base + 2] = pz
                gpuVerts[base + 3] = nx
                gpuVerts[base + 4] = ny
                gpuVerts[base + 5] = nz
                gpuVerts[base + 6] = u
                gpuVerts[base + 7] = uvV
                gpuVerts[base + 8] = ((texLayer shl 1) or (flags and 1)).toFloat()
            }

            val gpuIndices = ShortArray(triCount * 3)
            for (t in 0 until triCount * 3) {
                gpuIndices[t] = buf.short
            }

            out.add(
                LoadedGDriveModel(
                    id = id,
                    category = cat,
                    halfX = halfX,
                    halfZ = halfZ,
                    heightY = heightY,
                    vertCount = vertCount,
                    triCount = triCount,
                    gpuVertexFloats = gpuVerts,
                    gpuIndicesShorts = gpuIndices
                )
            )
        }
        return out
    }

    /**
     * Checks if a world point `(wx, wz)` is clear of all Zone 1 roads, shoulders, and junction bellmouths.
     */
    fun isPointClearOfZone1Roads(
        wx: Float,
        wz: Float,
        roads: List<ThrissurOsmRoad>,
        extraMargin: Float = 1.6f
    ): Boolean {
        val segs = cachedRoadSegs ?: buildZone1RoadSegments(roads).also { cachedRoadSegs = it }
        for (i in 0 until segs.size) {
            val s = segs[i]
            val req = s.clearanceRadius + extraMargin
            if (wx < s.minX - req || wx > s.maxX + req || wz < s.minZ - req || wz > s.maxZ + req) continue
            val t = (((wx - s.x0) * s.dx + (wz - s.z0) * s.dz) / s.lenSq).coerceIn(0f, 1f)
            val cx = s.x0 + s.dx * t
            val cz = s.z0 + s.dz * t
            val ox = wx - cx
            val oz = wz - cz
            if (ox * ox + oz * oz < req * req) return false
        }
        return true
    }

    private fun buildZone1RoadSegments(roads: List<ThrissurOsmRoad>): List<RoadSeg2D> {
        val segs = ArrayList<RoadSeg2D>(512)
        for (road in roads) {
            val halfCarriageway = road.widthM * 0.5f
            val clearance = halfCarriageway + 4.8f
            val frames = road.frames
            for (i in 0 until frames.size - 1) {
                val p0 = frames[i].pos
                val p1 = frames[i + 1].pos
                if (max(p0.x, p1.x) < ZONE1_MIN_X - 40f || min(p0.x, p1.x) > ZONE1_MAX_X + 40f ||
                    max(p0.z, p1.z) < ZONE1_MIN_Z - 40f || min(p0.z, p1.z) > ZONE1_MAX_Z + 40f
                ) {
                    continue
                }
                val dx = p1.x - p0.x
                val dz = p1.z - p0.z
                val lenSq = (dx * dx + dz * dz).coerceAtLeast(0.01f)
                segs.add(
                    RoadSeg2D(
                        x0 = p0.x,
                        z0 = p0.z,
                        x1 = p1.x,
                        z1 = p1.z,
                        dx = dx,
                        dz = dz,
                        lenSq = lenSq,
                        clearanceRadius = max(frames[i].halfWidthM, halfCarriageway) + 4.8f,
                        minX = min(p0.x, p1.x),
                        maxX = max(p0.x, p1.x),
                        minZ = min(p0.z, p1.z),
                        maxZ = max(p0.z, p1.z)
                    )
                )
            }
        }
        val bellmouths = ThrissurRoadRepository.getJunctionBellmouths()
        for (bm in bellmouths) {
            if (bm.centerPos.x in (ZONE1_MIN_X - 30f)..(ZONE1_MAX_X + 30f) &&
                bm.centerPos.z in (ZONE1_MIN_Z - 30f)..(ZONE1_MAX_Z + 30f)
            ) {
                val p0 = bm.centerPos
                val p1 = bm.bThroat.pos
                val dx = p1.x - p0.x
                val dz = p1.z - p0.z
                val lenSq = (dx * dx + dz * dz).coerceAtLeast(0.01f)
                val clearance = max(bm.tCenter.halfWidthM, bm.bThroat.halfWidthM) + 5.5f
                segs.add(
                    RoadSeg2D(
                        x0 = p0.x,
                        z0 = p0.z,
                        x1 = p1.x,
                        z1 = p1.z,
                        dx = dx,
                        dz = dz,
                        lenSq = lenSq,
                        clearanceRadius = clearance,
                        minX = min(p0.x, p1.x),
                        maxX = max(p0.x, p1.x),
                        minZ = min(p0.z, p1.z),
                        maxZ = max(p0.z, p1.z)
                    )
                )
            }
        }
        return segs
    }

    private fun obbOverlaps2D(
        ax: Float,
        az: Float,
        aCos: Float,
        aSin: Float,
        aHx: Float,
        aHz: Float,
        bx: Float,
        bz: Float,
        bCos: Float,
        bSin: Float,
        bHx: Float,
        bHz: Float,
        padding: Float
    ): Boolean {
        val dx = bx - ax
        val dz = bz - az
        val aux = aCos
        val auz = aSin
        val avx = -aSin
        val avz = aCos
        val bux = bCos
        val buz = bSin
        val bvx = -bSin
        val bvz = bCos

        val haX = aHx + padding
        val haZ = aHz + padding
        val hbX = bHx + padding
        val hbZ = bHz + padding

        // Unrolled 4-axis SAT test (zero array allocations)
        run {
            val dist = abs(dx * aux + dz * auz)
            val rb = hbX * abs(bux * aux + buz * auz) + hbZ * abs(bvx * aux + bvz * auz)
            if (dist > haX + rb) return false
        }
        run {
            val dist = abs(dx * avx + dz * avz)
            val rb = hbX * abs(bux * avx + buz * avz) + hbZ * abs(bvx * avx + bvz * avz)
            if (dist > haZ + rb) return false
        }
        run {
            val dist = abs(dx * bux + dz * buz)
            val ra = haX * abs(aux * bux + auz * buz) + haZ * abs(avx * bux + avz * buz)
            if (dist > ra + hbX) return false
        }
        run {
            val dist = abs(dx * bvx + dz * bvz)
            val ra = haX * abs(aux * bvx + auz * bvz) + haZ * abs(avx * bvx + avz * bvz)
            if (dist > ra + hbZ) return false
        }
        return true
    }

    private fun maidanTerrainY(wx: Float, wz: Float): Float {
        val dist = hypot(wx - 3.1f, wz - 18.0f)
        if (dist >= 57.5f) return 0f
        val t = ((57.5f - dist) / (57.5f - 21.0f)).coerceIn(0f, 1f)
        return 0.38f + t * (1.15f - 0.38f)
    }

    private val banyanCentersX = floatArrayOf(29.5f, 30.0f, -24.5f, -22.5f)
    private val banyanCentersZ = floatArrayOf(-10.5f, 45.5f, 45.0f, -23.5f)

    private fun spatialKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    private fun buildRoadSegGrid(roadSegs: List<RoadSeg2D>, cellSize: Float): HashMap<Long, ArrayList<RoadSeg2D>> {
        val inv = 1f / cellSize
        val grid = HashMap<Long, ArrayList<RoadSeg2D>>(512)
        for (i in 0 until roadSegs.size) {
            val seg = roadSegs[i]
            val pad = seg.clearanceRadius + 2.5f
            val x0 = kotlin.math.floor((seg.minX - pad) * inv).toInt()
            val x1 = kotlin.math.floor((seg.maxX + pad) * inv).toInt()
            val z0 = kotlin.math.floor((seg.minZ - pad) * inv).toInt()
            val z1 = kotlin.math.floor((seg.maxZ + pad) * inv).toInt()
            for (gx in x0..x1) {
                for (gz in z0..z1) {
                    grid.getOrPut(spatialKey(gx, gz)) { ArrayList(8) }.add(seg)
                }
            }
        }
        return grid
    }

    private fun isFootprintClearOfAllRoadsAndZones(
        cx: Float,
        cz: Float,
        cosYaw: Float,
        sinYaw: Float,
        hx: Float,
        hz: Float,
        category: Int,
        roadSegGrid: HashMap<Long, ArrayList<RoadSeg2D>>,
        invSegCell: Float,
        signX: FloatArray,
        signZ: FloatArray
    ): Boolean {
        if (cx - hx < ZONE1_MIN_X || cx + hx > ZONE1_MAX_X ||
            cz - hz < ZONE1_MIN_Z || cz + hz > ZONE1_MAX_Z
        ) {
            return false
        }

        // 1. Buildings are strictly forbidden inside Swaraj Round / Thekkinkadu Maidan (radius < 81.5m from (3.1, 18.0))
        val srDx = cx - 3.1f
        val srDz = cz - 18.0f
        val srDistSq = srDx * srDx + srDz * srDz
        if (category == CAT_BUILDING && srDistSq < 81.5f * 81.5f) {
            return false
        }

        // 2. Inside Thekkinkadu Maidan (radius < 58m from (3.1, 18.0)), trees & grass must stay clear of temple/paths/banyans
        if (srDistSq < 58.0f * 58.0f) {
            val pad = max(hx, hz) + 1.1f
            if (cx in (-16.8f - pad)..(23.0f + pad) && cz in (-1.8f - pad)..(37.8f + pad)) return false
            if (cx in (-25.2f - pad)..(-15.5f + pad) && cz in (13.0f - pad)..(23.0f + pad)) return false
            if (cx in (-34.5f - pad)..(-18.5f + pad) && cz in (-19.5f - pad)..(-3.5f + pad)) return false
            if (abs(cx - 3.1f) < 4.2f + pad || abs(cz - 18.0f) < 4.2f + pad) return false

            val trackRMin = if (category == CAT_TREE) 44.0f - pad else 44.8f
            val trackRMax = if (category == CAT_TREE) 48.2f + pad else 47.5f
            if (srDistSq in (trackRMin * trackRMin)..(trackRMax * trackRMax)) return false

            val banyanClear = if (category == CAT_TREE) 7.4f + pad else 3.9f + pad
            val banyanClearSq = banyanClear * banyanClear
            for (b in 0 until 4) {
                val bdx = cx - banyanCentersX[b]
                val bdz = cz - banyanCentersZ[b]
                if (bdx * bdx + bdz * bdz < banyanClearSq) return false
            }
        }

        // 3. Sakthan Thampuran Bus Stand yard, L-shaped terminal complex & bays (`x in -34..43, z in -210..-138`)
        val padSakthan = max(hx, hz) + 2.5f
        if (cx in (-34f - padSakthan)..(43f + padSakthan) && cz in (-210f - padSakthan)..(-138f + padSakthan)) {
            return false
        }

        // 4. Thrissur Railway Station terminal complex + West Boarding Platforms 1 & 2 (`x in -128..-60, z in -228..-54`)
        val padStation = max(hx, hz) + 2.8f
        if (cx in (-128.0f - padStation)..(-60.0f + padStation) && cz in (-228.0f - padStation)..(-54.0f + padStation)) {
            return false
        }

        // 4B. 2-Track Southern Railway Corridor (Thrissur SH 22 East -> Wadakkanchery ROB -> Shoranur West)
        //     Keep ALL buildings, trees, and grass strictly outside the railway track & crushed-stone ballast bed!
        val trackPad = max(hx, hz) + when (category) {
            CAT_BUILDING -> 12.0f
            CAT_TREE -> 10.5f
            else -> 8.5f
        }
        if (ShoranurRailwaySystem.isNearRailwayTrack(cx, cz, trackPad)) {
            return false
        }

        // 5. Vadakke Bus Stand U-shaped terminal yard, bays & front bellmouth (`x in -70..-3, z in 191..255`)
        val padVadakke = max(hx, hz) + 2.5f
        if (cx in (-70f - padVadakke)..(-3f + padVadakke) && cz in (191f - padVadakke)..(255f + padVadakke)) {
            return false
        }

        // 6. Manikandanal Traffic Signal mast & plaza on M.O. Road (`x in 6..28, z in -70..-55`)
        val padMani = max(hx, hz) + 1.5f
        if (cx in (6f - padMani)..(28f + padMani) && cz in (-70f - padMani)..(-55f + padMani)) {
            return false
        }

        // 6B. Our Lady of Dolours Basilica (Puthanpally) Campus, Courtyard & Bible Tower (`x in 72..152, z in -180..-86`)
        val padPuthanpally = max(hx, hz) + 2.5f
        if (cx in (74f - padPuthanpally)..(150f + padPuthanpally) && cz in (-178f - padPuthanpally)..(-88f + padPuthanpally)) {
            return false
        }

        // 7. Check 9 sample points on the candidate OBB against spatial-indexed road segments, railway tracks & signboards
        val extraRoadPad = when (category) {
            CAT_BUILDING -> 1.8f
            CAT_TREE -> 0.8f
            else -> 0.2f
        }
        val ptTrackMargin = when (category) {
            CAT_BUILDING -> 10.5f
            CAT_TREE -> 9.2f
            else -> 7.8f
        }
        val signClearSq = 7.5f * 7.5f
        val signCount = signX.size

        for (ptIdx in 0 until 9) {
            val lx = when (ptIdx) {
                0, 5, 6 -> 0f
                1, 4, 7 -> -hx
                else -> hx
            }
            val lz = when (ptIdx) {
                0, 7, 8 -> 0f
                1, 2, 5 -> -hz
                else -> hz
            }
            val wx = cx + lx * cosYaw - lz * sinYaw
            val wz = cz + lx * sinYaw + lz * cosYaw

            if (ShoranurRailwaySystem.isNearRailwayTrack(wx, wz, ptTrackMargin)) return false

            for (sIdx in 0 until signCount) {
                val sdx = wx - signX[sIdx]
                val sdz = wz - signZ[sIdx]
                if (sdx * sdx + sdz * sdz < signClearSq) return false
            }

            val gx = kotlin.math.floor(wx * invSegCell).toInt()
            val gz = kotlin.math.floor(wz * invSegCell).toInt()
            val bucket = roadSegGrid[spatialKey(gx, gz)] ?: continue
            for (i in 0 until bucket.size) {
                val seg = bucket[i]
                val req = seg.clearanceRadius + extraRoadPad
                if (wx < seg.minX - req || wx > seg.maxX + req || wz < seg.minZ - req || wz > seg.maxZ + req) continue
                val t = (((wx - seg.x0) * seg.dx + (wz - seg.z0) * seg.dz) / seg.lenSq).coerceIn(0f, 1f)
                val px = seg.x0 + seg.dx * t
                val pz = seg.z0 + seg.dz * t
                val ox = wx - px
                val oz = wz - pz
                if (ox * ox + oz * oz < req * req) return false
            }
        }

        return true
    }

    internal fun buildZone1Layout(
        loaded: List<LoadedGDriveModel>,
        roads: List<ThrissurOsmRoad>,
        signPoints: List<Pair<Float, Float>>
    ): List<PlacedInstance> {
        if (loaded.isEmpty() || roads.isEmpty()) return emptyList()
        val roadSegs = buildZone1RoadSegments(roads)
        cachedRoadSegs = roadSegs

        val bldgModels = loaded.filter { it.category == CAT_BUILDING }
        val treeModels = loaded.filter { it.category == CAT_TREE }
        val grassModels = loaded.filter { it.category == CAT_GRASS }
        if (bldgModels.isEmpty() || treeModels.isEmpty() || grassModels.isEmpty()) return emptyList()

        val modelIndexMap = HashMap<String, Int>(loaded.size * 2)
        for (i in loaded.indices) {
            modelIndexMap[loaded[i].id] = i
        }

        val segCellSize = 24.0f
        val invSegCell = 1f / segCellSize
        val roadSegGrid = buildRoadSegGrid(roadSegs, segCellSize)

        val signX = FloatArray(signPoints.size) { signPoints[it].first }
        val signZ = FloatArray(signPoints.size) { signPoints[it].second }

        val placedCellSize = 24.0f
        val invPlacedCell = 1f / placedCellSize
        val placedGrid = HashMap<Long, ArrayList<PlacedInstance>>(512)
        val placed = ArrayList<PlacedInstance>(900)

        fun tryPlace(
            model: LoadedGDriveModel,
            cx: Float,
            baseY: Float,
            cz: Float,
            yawRad: Float,
            scale: Float,
            minSeparation: Float
        ): Boolean {
            val cosY = cos(yawRad)
            val sinY = sin(yawRad)
            val shx = model.halfX * scale
            val shz = model.halfZ * scale
            if (!isFootprintClearOfAllRoadsAndZones(
                    cx, cz, cosY, sinY, shx, shz, model.category, roadSegGrid, invSegCell, signX, signZ
                )
            ) {
                return false
            }
            val selfCullR = sqrt(shx * shx + shz * shz)
            val gcx = kotlin.math.floor(cx * invPlacedCell).toInt()
            val gcz = kotlin.math.floor(cz * invPlacedCell).toInt()
            for (dxC in -1..1) {
                for (dzC in -1..1) {
                    val bucket = placedGrid[spatialKey(gcx + dxC, gcz + dzC)] ?: continue
                    for (i in 0 until bucket.size) {
                        val other = bucket[i]
                        val dx = cx - other.cx
                        val dz = cz - other.cz
                        val maxR = selfCullR + other.cullRadiusXZ + minSeparation + 1.0f
                        if (dx * dx + dz * dz > maxR * maxR) continue
                        if (obbOverlaps2D(
                                cx, cz, cosY, sinY, shx, shz,
                                other.cx, other.cz, other.cosYaw, other.sinYaw, other.scaledHalfX, other.scaledHalfZ,
                                minSeparation
                            )
                        ) {
                            return false
                        }
                    }
                }
            }
            val modelIdx = modelIndexMap[model.id] ?: 0
            val inst = PlacedInstance(
                modelIndex = modelIdx,
                model = model,
                cx = cx,
                baseY = baseY,
                cz = cz,
                yawRad = yawRad,
                cosYaw = cosY,
                sinYaw = sinY,
                scale = scale,
                scaledHalfX = shx,
                scaledHalfZ = shz,
                cullRadiusXZ = selfCullR
            )
            placed.add(inst)
            val minGx = kotlin.math.floor((cx - selfCullR) * invPlacedCell).toInt()
            val maxGx = kotlin.math.floor((cx + selfCullR) * invPlacedCell).toInt()
            val minGz = kotlin.math.floor((cz - selfCullR) * invPlacedCell).toInt()
            val maxGz = kotlin.math.floor((cz + selfCullR) * invPlacedCell).toInt()
            for (gx in minGx..maxGx) {
                for (gz in minGz..maxGz) {
                    placedGrid.getOrPut(spatialKey(gx, gz)) { ArrayList(8) }.add(inst)
                }
            }
            return true
        }

        // Deterministic PRNG for natural, non-repetitive urban distribution across Zone 1
        var seed = 0x5F3759DFu
        fun nextRand01(): Float {
            seed = seed * 1664525u + 1013904223u
            return ((seed shr 8) and 0xFFFFFFu).toFloat() / 16777216.0f
        }

        var lastBldgIdx = -1
        var secondLastBldgIdx = -1
        fun pickBuildingModel(): LoadedGDriveModel {
            for (attempt in 0 until 8) {
                val idx = (nextRand01() * bldgModels.size).toInt().coerceIn(0, bldgModels.lastIndex)
                if (idx != lastBldgIdx && idx != secondLastBldgIdx) {
                    secondLastBldgIdx = lastBldgIdx
                    lastBldgIdx = idx
                    return bldgModels[idx]
                }
            }
            val fallback = (lastBldgIdx + 1) % bldgModels.size
            secondLastBldgIdx = lastBldgIdx
            lastBldgIdx = fallback
            return bldgModels[fallback]
        }

        var lastTreeIdx = -1
        fun pickTreeModel(): LoadedGDriveModel {
            for (attempt in 0 until 6) {
                val idx = (nextRand01() * treeModels.size).toInt().coerceIn(0, treeModels.lastIndex)
                if (idx != lastTreeIdx) {
                    lastTreeIdx = idx
                    return treeModels[idx]
                }
            }
            val fallback = (lastTreeIdx + 1) % treeModels.size
            lastTreeIdx = fallback
            return treeModels[fallback]
        }

        fun pickGrassModel(): LoadedGDriveModel {
            val idx = (nextRand01() * grassModels.size).toInt().coerceIn(0, grassModels.lastIndex)
            return grassModels[idx]
        }

        // ------------------------------------------------------------------
        // PASS 1: Zone 1 Roadside Buildings along both sides of all Zone 1 roads
        // ------------------------------------------------------------------
        for (road in roads) {
            val frames = road.frames
            if (frames.size < 2) continue
            val halfW = road.widthM * 0.5f

            var accumDist = 12f
            for (i in 0 until frames.size - 1) {
                val f0 = frames[i]
                val f1 = frames[i + 1]
                val p0 = f0.pos
                val p1 = f1.pos
                if (p0.x !in ZONE1_MIN_X..ZONE1_MAX_X || p0.z !in ZONE1_MIN_Z..ZONE1_MAX_Z) continue

                val segDx = p1.x - p0.x
                val segDz = p1.z - p0.z
                val segLen = sqrt(segDx * segDx + segDz * segDz)
                if (segLen < 0.5f) continue

                var d = accumDist
                while (d <= segLen) {
                    val t = (d / segLen).coerceIn(0f, 1f)
                    val rx = p0.x + segDx * t
                    val rz = p0.z + segDz * t
                    val nx = f0.right.x
                    val nz = f0.right.z
                    val roadYaw = kotlin.math.atan2(f0.tangent.x.toDouble(), f0.tangent.z.toDouble()).toFloat()

                    for (side in intArrayOf(-1, 1)) {
                        val bModel = pickBuildingModel()
                        val bScale = 0.88f + nextRand01() * 0.24f
                        val faceYaw = if (side > 0) roadYaw - (PI.toFloat() * 0.5f) else roadYaw + (PI.toFloat() * 0.5f)
                        val depthHalf = max(bModel.halfX, bModel.halfZ) * bScale
                        val latOffset = max(halfW, f0.halfWidthM) + 7.2f + depthHalf + nextRand01() * 2.5f
                        val bx = rx + nx * side * latOffset
                        val bz = rz + nz * side * latOffset
                        tryPlace(bModel, bx, 0f, bz, faceYaw, bScale, minSeparation = 1.6f)
                    }
                    d += 18f + nextRand01() * 8f
                }
                accumDist = d - segLen
            }
        }

        // Additional Second-Row Urban Skyline Buildings in Zone 1 commercial blocks
        for (road in roads) {
            val frames = road.frames
            val halfW = road.widthM * 0.5f
            for (i in frames.indices step 3) {
                val f = frames[i]
                val p = f.pos
                if (p.x !in ZONE1_MIN_X..ZONE1_MAX_X || p.z !in ZONE1_MIN_Z..ZONE1_MAX_Z) continue
                val nx = f.right.x
                val nz = f.right.z
                val roadYaw = kotlin.math.atan2(f.tangent.x.toDouble(), f.tangent.z.toDouble()).toFloat()
                for (side in intArrayOf(-1, 1)) {
                    val bModel = pickBuildingModel()
                    val bScale = 0.92f + nextRand01() * 0.26f
                    val depthHalf = max(bModel.halfX, bModel.halfZ) * bScale
                    val latOffset = max(halfW, f.halfWidthM) + 25.5f + depthHalf + nextRand01() * 4.0f
                    val bx = p.x + nx * side * latOffset
                    val bz = p.z + nz * side * latOffset
                    val faceYaw = if (side > 0) roadYaw - (PI.toFloat() * 0.5f) else roadYaw + (PI.toFloat() * 0.5f)
                    tryPlace(bModel, bx, 0f, bz, faceYaw, bScale, minSeparation = 2.0f)
                }
            }
        }

        // ------------------------------------------------------------------
        // PASS 2: Thekkinkadu Maidan (Swaraj Round Central Park) Trees & Lush 3D Grass
        // ------------------------------------------------------------------
        val palmIdx = treeModels.indexOfFirst { it.id.contains("palm") }.coerceAtLeast(0)
        val bananaIdx = treeModels.indexOfFirst { it.id.contains("banana") }.coerceAtLeast(0)
        val canopyTreeIndices = treeModels.indices.filter { it != bananaIdx }

        val ringRadii = floatArrayOf(24.5f, 31.5f, 38.5f, 42.2f, 50.5f)
        for (rIdx in ringRadii.indices) {
            val rad = ringRadii[rIdx]
            val steps = 20 + rIdx * 6
            for (s in 0 until steps) {
                val ang = (s.toFloat() / steps.toFloat()) * (2f * PI.toFloat()) + (nextRand01() - 0.5f) * 0.16f
                val rr = rad + (nextRand01() - 0.5f) * 2.4f
                val tx = 3.1f + cos(ang) * rr
                val tz = 18.0f + sin(ang) * rr
                val tModel = if ((s + rIdx) % 5 == 0) {
                    treeModels[palmIdx]
                } else {
                    treeModels[canopyTreeIndices[(s + rIdx) % canopyTreeIndices.size]]
                }
                val tScale = 0.88f + nextRand01() * 0.38f
                val tYaw = nextRand01() * 2f * PI.toFloat()
                val tBaseY = (maidanTerrainY(tx, tz) - 0.04f).coerceAtLeast(0f)
                if (tryPlace(tModel, tx, tBaseY, tz, tYaw, tScale, minSeparation = 1.1f)) {
                    for (g in 0 until 3) {
                        val gAng = nextRand01() * 2f * PI.toFloat()
                        val gDist = 2.6f + nextRand01() * 2.4f
                        val gx = tx + cos(gAng) * gDist
                        val gz = tz + sin(gAng) * gDist
                        val gModel = pickGrassModel()
                        val gBaseY = (maidanTerrainY(gx, gz) - 0.03f).coerceAtLeast(0f)
                        tryPlace(gModel, gx, gBaseY, gz, nextRand01() * 6.28f, 1.05f + nextRand01() * 0.45f, minSeparation = 0.22f)
                    }
                }
            }
        }

        // Open-Lawn Lush 3D Grass Meadow Sweeps across Thekkinkadu Maidan (flush on hillock slope)
        for (m in 0 until 320) {
            val ang = nextRand01() * 2f * PI.toFloat()
            val rr = 22.0f + sqrt(nextRand01()) * 31.2f
            val gx = 3.1f + cos(ang) * rr
            val gz = 18.0f + sin(ang) * rr
            val gModel = pickGrassModel()
            val gBaseY = (maidanTerrainY(gx, gz) - 0.03f).coerceAtLeast(0f)
            tryPlace(gModel, gx, gBaseY, gz, nextRand01() * 6.28f, 1.10f + nextRand01() * 0.55f, minSeparation = 0.35f)
        }

        // ------------------------------------------------------------------
        // PASS 3: Roadside Boulevard Trees & 3D Grass along Zone 1 Roads
        // ------------------------------------------------------------------
        for (road in roads) {
            val frames = road.frames
            if (frames.size < 2) continue
            val halfW = road.widthM * 0.5f

            var accumDist = 8f
            for (i in 0 until frames.size - 1) {
                val f0 = frames[i]
                val f1 = frames[i + 1]
                val p0 = f0.pos
                val p1 = f1.pos
                if (p0.x !in ZONE1_MIN_X..ZONE1_MAX_X || p0.z !in ZONE1_MIN_Z..ZONE1_MAX_Z) continue

                val segDx = p1.x - p0.x
                val segDz = p1.z - p0.z
                val segLen = sqrt(segDx * segDx + segDz * segDz)
                if (segLen < 0.5f) continue

                var d = accumDist
                while (d <= segLen) {
                    val t = (d / segLen).coerceIn(0f, 1f)
                    val rx = p0.x + segDx * t
                    val rz = p0.z + segDz * t
                    val nx = f0.right.x
                    val nz = f0.right.z

                    for (side in intArrayOf(-1, 1)) {
                        val tModel = pickTreeModel()
                        val tScale = 0.84f + nextRand01() * 0.26f
                        val treeRad = max(tModel.halfX, tModel.halfZ) * tScale
                        val latOffset = max(halfW, f0.halfWidthM) + 5.8f + treeRad + nextRand01() * 1.6f
                        val tx = rx + nx * side * latOffset
                        val tz = rz + nz * side * latOffset
                        val tYaw = nextRand01() * 2f * PI.toFloat()
                        tryPlace(tModel, tx, 0f, tz, tYaw, tScale, minSeparation = 1.0f)

                        val gModel = pickGrassModel()
                        val gScale = 0.90f + nextRand01() * 0.35f
                        val gLat = max(halfW, f0.halfWidthM) + 5.2f + nextRand01() * 2.2f
                        val gx = rx + nx * side * gLat + f0.tangent.x * (nextRand01() - 0.5f) * 4f
                        val gz = rz + nz * side * gLat + f0.tangent.z * (nextRand01() - 0.5f) * 4f
                        tryPlace(gModel, gx, 0f, gz, nextRand01() * 6.28f, gScale, minSeparation = 0.25f)
                    }
                    d += 15f + nextRand01() * 7f
                }
                accumDist = d - segLen
            }
        }

        // ------------------------------------------------------------------
        // PASS 4: ZONE 2 — Real 3D Textured Instanced Trees, Palms & Grass for
        //         Mannuthy Islands, NH 544, Kuthiran Mala Mountain, SH 51 Peechi Forest
        //         & Peechi Dam Botanical Gardens & Reservoir Hillsides!
        // ------------------------------------------------------------------
        val zone2RoadIds = setOf(
            "ROAD_SWARAJ_TO_MANNUTHY",
            "ROAD_MANNUTHY_TO_PEECHI_DAM",
            "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
            "ROAD_NH544_MANNUTHY_TO_KUTHIRAN",
            "ROAD_NH544_KUTHIRAN_LEFT_TUBE",
            "ROAD_NH544_KUTHIRAN_RIGHT_TUBE",
            "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD",
            "ROAD_PEECHI_DAM_ENTRY",
            "ROAD_PEECHI_DAM_EXIT"
        )
        val z2Segs = ArrayList<RoadSeg2D>(1600)
        for (road in roads) {
            if (road.id !in zone2RoadIds) continue
            val frames = road.frames
            if (frames.size < 2) continue
            val halfCarriage = road.widthM * 0.5f
            for (i in 0 until frames.size - 1) {
                val p0 = frames[i].pos
                val p1 = frames[i + 1].pos
                val dx = p1.x - p0.x
                val dz = p1.z - p0.z
                val lenSq = (dx * dx + dz * dz).coerceAtLeast(0.01f)
                z2Segs.add(
                    RoadSeg2D(
                        x0 = p0.x,
                        z0 = p0.z,
                        x1 = p1.x,
                        z1 = p1.z,
                        dx = dx,
                        dz = dz,
                        lenSq = lenSq,
                        clearanceRadius = max(frames[i].halfWidthM, halfCarriage) + 5.2f,
                        minX = min(p0.x, p1.x),
                        maxX = max(p0.x, p1.x),
                        minZ = min(p0.z, p1.z),
                        maxZ = max(p0.z, p1.z)
                    )
                )
            }
        }
        val z2RoadGrid = buildRoadSegGrid(z2Segs, segCellSize)

        fun isZone2PointClearOfRoads(wx: Float, wz: Float, extraPad: Float): Boolean {
            val gx = kotlin.math.floor(wx * invSegCell).toInt()
            val gz = kotlin.math.floor(wz * invSegCell).toInt()
            val bucket = z2RoadGrid[spatialKey(gx, gz)] ?: return true
            for (i in 0 until bucket.size) {
                val seg = bucket[i]
                val req = seg.clearanceRadius + extraPad
                if (wx < seg.minX - req || wx > seg.maxX + req || wz < seg.minZ - req || wz > seg.maxZ + req) continue
                val t = (((wx - seg.x0) * seg.dx + (wz - seg.z0) * seg.dz) / seg.lenSq).coerceIn(0f, 1f)
                val px = seg.x0 + seg.dx * t
                val pz = seg.z0 + seg.dz * t
                val ox = wx - px
                val oz = wz - pz
                if (ox * ox + oz * oz < req * req) return false
            }
            return true
        }

        fun tryPlaceZone2(
            model: LoadedGDriveModel,
            cx: Float,
            baseY: Float,
            cz: Float,
            yawRad: Float,
            scale: Float,
            minSeparation: Float,
            allowAboveTunnel: Boolean = false
        ): Boolean {
            val cosY = cos(yawRad)
            val sinY = sin(yawRad)
            val shx = model.halfX * scale
            val shz = model.halfZ * scale
            val selfCullR = sqrt(shx * shx + shz * shz)

            if (!allowAboveTunnel) {
                val extraPad = if (model.category == CAT_TREE) max(shx, shz) * 0.45f + 1.2f else 0.5f
                if (!isZone2PointClearOfRoads(cx, cz, extraPad)) return false
                // Keep Peechi Dam bus courtyard, forecourt & spillway wall clear of wild trees
                if (cx in 3380f..3458f && cz in 89.0f..161.0f) return false
                // Keep Peechi Wildlife Check-Post arches clear
                if (hypot(cx - Zone2Nh544KuthiranRenderer.PEECHI_CHECKPOST_X, cz - Zone2Nh544KuthiranRenderer.PEECHI_CHECKPOST_Z) < 22f) return false
                if (!ThrissurFuelStationSystem.isPointClearOfFuelStations(cx, cz, extraMargin = extraPad + 4.0f)) return false
            }

            val gcx = kotlin.math.floor(cx * invPlacedCell).toInt()
            val gcz = kotlin.math.floor(cz * invPlacedCell).toInt()
            for (dxC in -1..1) {
                for (dzC in -1..1) {
                    val bucket = placedGrid[spatialKey(gcx + dxC, gcz + dzC)] ?: continue
                    for (i in 0 until bucket.size) {
                        val other = bucket[i]
                        val dx = cx - other.cx
                        val dz = cz - other.cz
                        val maxR = selfCullR + other.cullRadiusXZ + minSeparation + 0.6f
                        if (dx * dx + dz * dz > maxR * maxR) continue
                        if (obbOverlaps2D(
                                cx, cz, cosY, sinY, shx, shz,
                                other.cx, other.cz, other.cosYaw, other.sinYaw, other.scaledHalfX, other.scaledHalfZ,
                                minSeparation
                            )
                        ) {
                            return false
                        }
                    }
                }
            }
            val modelIdx = modelIndexMap[model.id] ?: 0
            val inst = PlacedInstance(
                modelIndex = modelIdx,
                model = model,
                cx = cx,
                baseY = baseY,
                cz = cz,
                yawRad = yawRad,
                cosYaw = cosY,
                sinYaw = sinY,
                scale = scale,
                scaledHalfX = shx,
                scaledHalfZ = shz,
                cullRadiusXZ = selfCullR
            )
            placed.add(inst)
            val minGx = kotlin.math.floor((cx - selfCullR) * invPlacedCell).toInt()
            val maxGx = kotlin.math.floor((cx + selfCullR) * invPlacedCell).toInt()
            val minGz = kotlin.math.floor((cz - selfCullR) * invPlacedCell).toInt()
            val maxGz = kotlin.math.floor((cz + selfCullR) * invPlacedCell).toInt()
            for (gx in minGx..maxGx) {
                for (gz in minGz..maxGz) {
                    placedGrid.getOrPut(spatialKey(gx, gz)) { ArrayList(8) }.add(inst)
                }
            }
            return true
        }

        // 4A. Mannuthy 4-Way Interchange Landscaped Green Quadrant Islands (Palms, Canopy Trees & Lush 3D Grass)
        val mannuthyIslands = arrayOf(
            Triple(1012.0f, 238.0f, 12.2f),
            Triple(1086.0f, 256.0f, 10.8f),
            Triple(1014.0f, 144.0f, 10.4f),
            Triple(1096.0f, 162.0f, 11.2f)
        )
        for ((idxIsl, isl) in mannuthyIslands.withIndex()) {
            val (ix, iz, rad) = isl
            for (k in 0 until 6) {
                val ang = (k.toFloat() / 6f) * 2f * PI.toFloat() + idxIsl * 0.45f
                val rr = 5.2f + nextRand01() * (rad - 6.0f).coerceAtLeast(1.5f)
                val tx = ix + cos(ang) * rr
                val tz = iz + sin(ang) * rr
                val tModel = if (k % 2 == 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
                tryPlaceZone2(tModel, tx, 0.04f, tz, nextRand01() * 6.28f, 0.85f + nextRand01() * 0.30f, minSeparation = 1.1f)
            }
            for (g in 0 until 18) {
                val ang = nextRand01() * 2f * PI.toFloat()
                val rr = 3.2f + sqrt(nextRand01()) * (rad - 3.6f).coerceAtLeast(1f)
                val gx = ix + cos(ang) * rr
                val gz = iz + sin(ang) * rr
                tryPlaceZone2(pickGrassModel(), gx, 0.05f, gz, nextRand01() * 6.28f, 1.05f + nextRand01() * 0.45f, minSeparation = 0.25f)
            }
        }

        // 4B. Kuthiran Mala Western Ghats Mountain Massif & Approach Rock-Cutting Terraces!
        //     Dense tropical rainforest canopy perched flush on the 3D mountain surface (Y = 12..66m)!
        //     (1) Portal Crown Groves directly above West (X=2168..2240) & East (X=3035..3108) Tunnel Portals
        for (portalSide in intArrayOf(0, 1)) {
            val baseX = if (portalSide == 0) 2168f else 3038f
            for (row in 0 until 4) {
                val txBase = baseX + row * 19f
                for (col in 0 until 9) {
                    val tz = 1068f + col * 18f + (nextRand01() - 0.5f) * 7f
                    val tx = txBase + (nextRand01() - 0.5f) * 7f
                    val surfY = Zone2Nh544KuthiranRenderer.kuthiranMountainSurfaceY(tx, tz)
                    if (surfY >= 14.0f) {
                        val tModel = treeModels[canopyTreeIndices[(row + col) % canopyTreeIndices.size]]
                        tryPlaceZone2(
                            tModel, tx, (surfY - 0.55f).coerceAtLeast(18.8f), tz,
                            nextRand01() * 6.28f, 1.35f + nextRand01() * 0.65f,
                            minSeparation = 1.6f, allowAboveTunnel = true
                        )
                    }
                }
            }
        }
        //     (2) Full 955m Kuthiran Mala Mountain Ridge & Granite Flanks (X = 2230 .. 3045)
        for (k in 0 until 210) {
            val tx = 2225f + nextRand01() * 825f
            val tz = 1032f + nextRand01() * 216f
            val surfY = Zone2Nh544KuthiranRenderer.kuthiranMountainSurfaceY(tx, tz)
            val overTubes = abs(tz - Zone2Nh544KuthiranRenderer.KUTHIRAN_CORRIDOR_CENTER_Z) < 34f
            if (surfY >= 6.0f && (!overTubes || surfY >= 20.5f)) {
                val tModel = if (k % 7 == 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
                tryPlaceZone2(
                    tModel, tx, (surfY - 0.65f).coerceAtLeast(if (overTubes) 19.5f else 0.5f), tz,
                    nextRand01() * 6.28f, 1.45f + nextRand01() * 0.75f,
                    minSeparation = 2.2f, allowAboveTunnel = overTubes
                )
            }
        }
        //     (3) West (X=2010..2156) & East (X=3119..3270) Approach Rock-Cutting Terraces & Forested Crests
        for (approach in intArrayOf(0, 1)) {
            val x0 = if (approach == 0) 2010f else 3119f
            for (k in 0 until 64) {
                val tx = x0 + nextRand01() * 146f
                val side = if (k % 2 == 0) -1f else 1f
                val latDist = 29.5f + nextRand01() * 44.0f
                val tz = Zone2Nh544KuthiranRenderer.KUTHIRAN_CORRIDOR_CENTER_Z + side * latDist
                val surfY = Zone2Nh544KuthiranRenderer.kuthiranMountainSurfaceY(tx, tz)
                if (surfY >= 2.2f) {
                    val tModel = if (k % 6 == 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
                    if (tryPlaceZone2(tModel, tx, (surfY - 0.55f).coerceAtLeast(0.2f), tz, nextRand01() * 6.28f, 1.25f + nextRand01() * 0.60f, minSeparation = 1.8f)) {
                        tryPlaceZone2(pickGrassModel(), tx + (nextRand01() - 0.5f) * 3f, (surfY - 0.35f).coerceAtLeast(0.1f), tz + (nextRand01() - 0.5f) * 3f, nextRand01() * 6.28f, 1.25f + nextRand01() * 0.5f, minSeparation = 0.3f)
                    }
                }
            }
        }

        // 4C. NH 544 Express Highway Forest Verges (Mannuthy -> Kuthiran & Kuthiran East -> Palakkad)
        for (road in roads) {
            if (road.id != "ROAD_NH544_MANNUTHY_TO_KUTHIRAN" && road.id != "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD") continue
            val frames = road.frames
            var fIdx = 12
            while (fIdx < frames.size - 4) {
                val f = frames[fIdx]
                for (side in intArrayOf(-1, 1)) {
                    val lat = 19.5f + nextRand01() * 12.0f
                    val tx = f.pos.x + f.right.x * side * lat
                    val tz = f.pos.z + f.right.z * side * lat
                    val tModel = if ((fIdx + side) % 4 == 0) treeModels[palmIdx] else pickTreeModel()
                    tryPlaceZone2(tModel, tx, 0f, tz, nextRand01() * 6.28f, 1.05f + nextRand01() * 0.45f, minSeparation = 1.6f)
                    val gx = f.pos.x + f.right.x * side * (16.5f + nextRand01() * 4.5f)
                    val gz = f.pos.z + f.right.z * side * (16.5f + nextRand01() * 4.5f)
                    tryPlaceZone2(pickGrassModel(), gx, 0f, gz, nextRand01() * 6.28f, 1.15f + nextRand01() * 0.45f, minSeparation = 0.3f)
                }
                fIdx += 6
            }
        }

        // 4D. SH 51 Peechi-Vazhani Wildlife Sanctuary Dense Tropical Forest Corridor (2-row canopy + 3D grass!)
        val sh51Road = roads.firstOrNull { it.id == "ROAD_MANNUTHY_TO_PEECHI_DAM" }
        if (sh51Road != null && sh51Road.frames.size > 20) {
            val frames = sh51Road.frames
            var fIdx = 14
            while (fIdx < frames.size - 8) {
                val f = frames[fIdx]
                for (side in intArrayOf(-1, 1)) {
                    // Inner roadside Western Ghats tree row
                    val lat1 = 13.5f + nextRand01() * 5.0f
                    val tx1 = f.pos.x + f.right.x * side * lat1
                    val tz1 = f.pos.z + f.right.z * side * lat1
                    val m1 = if (fIdx % 5 == 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[fIdx % canopyTreeIndices.size]]
                    tryPlaceZone2(m1, tx1, 0f, tz1, nextRand01() * 6.28f, 1.08f + nextRand01() * 0.48f, minSeparation = 1.5f)

                    // Deep forest second row
                    val lat2 = 22.0f + nextRand01() * 11.0f
                    val tx2 = f.pos.x + f.right.x * side * lat2
                    val tz2 = f.pos.z + f.right.z * side * lat2
                    val m2 = treeModels[canopyTreeIndices[(fIdx + 2) % canopyTreeIndices.size]]
                    tryPlaceZone2(m2, tx2, 0f, tz2, nextRand01() * 6.28f, 1.25f + nextRand01() * 0.55f, minSeparation = 1.8f)

                    // Lush roadside ferns & 3D grass clumps
                    val gx = f.pos.x + f.right.x * side * (11.2f + nextRand01() * 3.8f)
                    val gz = f.pos.z + f.right.z * side * (11.2f + nextRand01() * 3.8f)
                    tryPlaceZone2(pickGrassModel(), gx, 0f, gz, nextRand01() * 6.28f, 1.15f + nextRand01() * 0.50f, minSeparation = 0.28f)
                }
                fIdx += 6
            }
        }

        // 4E. Peechi Dam Botanical Gardens (inside South & North Compound Lawns) + Forebay Forest Hills!
        for (gardenZMin in floatArrayOf(75.5f, 163.5f)) {
            for (k in 0 until 6) {
                val tx = 3417.0f + k * 6.2f + (nextRand01() - 0.5f) * 1.4f
                val tz = gardenZMin + 2.5f + nextRand01() * 7.5f
                val tModel = if (k % 2 == 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
                tryPlaceZone2(tModel, tx, 0.05f, tz, nextRand01() * 6.28f, 0.90f + nextRand01() * 0.32f, minSeparation = 1.2f)
            }
            for (g in 0 until 22) {
                val gx = 3415.5f + nextRand01() * 35.0f
                val gz = gardenZMin + nextRand01() * 11.0f
                tryPlaceZone2(pickGrassModel(), gx, 0.06f, gz, nextRand01() * 6.28f, 1.05f + nextRand01() * 0.45f, minSeparation = 0.25f)
            }
        }
        // Scenic Western Ghats forest groves framing the South & North hills around Peechi Dam & Reservoir!
        for (k in 0 until 44) {
            val southSide = k % 2 == 0
            val tx = 3370f + nextRand01() * 120f
            val tz = if (southSide) -80f + nextRand01() * 135f else 195f + nextRand01() * 155f
            if (tx in 3448f..3492f) continue // Keep masonry dam wall itself clean
            val tModel = if (k % 4 == 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
            tryPlaceZone2(tModel, tx, 0f, tz, nextRand01() * 6.28f, 1.25f + nextRand01() * 0.65f, minSeparation = 2.0f)
        }
        // Forested hill trees crowning the East Reservoir mountain shore (Y = 20..44m)
        for (k in 0 until 36) {
            val tz = -135f + (k.toFloat() / 36f) * 530f + (nextRand01() - 0.5f) * 12f
            val tx = 3865f + nextRand01() * 52f
            val hillY = 21.5f + ((tx - 3865f) / 52f) * 20.0f
            val tModel = treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
            tryPlaceZone2(tModel, tx, hillY, tz, nextRand01() * 6.28f, 1.65f + nextRand01() * 0.70f, minSeparation = 3.0f, allowAboveTunnel = true)
        }

        // =========================================================================
        // PASS 5: ZONE 3 — NH 544 SOUTH, KURUMALI & CHALAKUDY RIVERBANK COCONUT GROVES
        //   Strictly outside all NH 544 main carriageways, 5.5m service roads, toll apron & rivers!
        // =========================================================================
        val zone3RoadIds = setOf(
            "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
            "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY",
            "ROAD_SAKTHAN_TO_OLLUR"
        )
        val z3Segs = ArrayList<RoadSeg2D>(1400)
        for (road in roads) {
            if (road.id !in zone3RoadIds) continue
            val frames = road.frames
            if (frames.size < 2) continue
            val halfCarriage = road.widthM * 0.5f
            // Reserve extra +11.5m lateral buffer to protect both 5.5m service roads + verges on both sides of NH 544!
            val serviceRoadClearance = if (road.id == "ROAD_SAKTHAN_TO_OLLUR") 6.0f else 11.5f
            for (i in 0 until frames.size - 1) {
                val p0 = frames[i].pos
                val p1 = frames[i + 1].pos
                val dx = p1.x - p0.x
                val dz = p1.z - p0.z
                val lenSq = (dx * dx + dz * dz).coerceAtLeast(0.01f)
                z3Segs.add(
                    RoadSeg2D(
                        x0 = p0.x,
                        z0 = p0.z,
                        x1 = p1.x,
                        z1 = p1.z,
                        dx = dx,
                        dz = dz,
                        lenSq = lenSq,
                        clearanceRadius = max(frames[i].halfWidthM, halfCarriage) + serviceRoadClearance,
                        minX = min(p0.x, p1.x),
                        maxX = max(p0.x, p1.x),
                        minZ = min(p0.z, p1.z),
                        maxZ = max(p0.z, p1.z)
                    )
                )
            }
        }
        val z3RoadGrid = buildRoadSegGrid(z3Segs, segCellSize)

        fun isZone3PointClearOfRoads(wx: Float, wz: Float, extraPad: Float): Boolean {
            if (!Zone3Nh544SouthRenderer.isPointClearOfZone3Structures(wx, wz)) return false
            if (!Zone4KoleRailHeritageRenderer.isPointClearOfZone4Structures(wx, wz)) return false
            if (!Zone5Nh66CoastalRenderer.isPointClearOfZone5Structures(wx, wz)) return false
            val gx = kotlin.math.floor(wx * invSegCell).toInt()
            val gz = kotlin.math.floor(wz * invSegCell).toInt()
            val bucket = z3RoadGrid[spatialKey(gx, gz)] ?: return true
            for (i in 0 until bucket.size) {
                val seg = bucket[i]
                val req = seg.clearanceRadius + extraPad
                if (wx < seg.minX - req || wx > seg.maxX + req || wz < seg.minZ - req || wz > seg.maxZ + req) continue
                val t = (((wx - seg.x0) * seg.dx + (wz - seg.z0) * seg.dz) / seg.lenSq).coerceIn(0f, 1f)
                val px = seg.x0 + seg.dx * t
                val pz = seg.z0 + seg.dz * t
                val ox = wx - px
                val oz = wz - pz
                if (ox * ox + oz * oz < req * req) return false
            }
            return true
        }

        fun tryPlaceZone3(
            model: LoadedGDriveModel,
            cx: Float,
            baseY: Float,
            cz: Float,
            yawRad: Float,
            scale: Float,
            minSeparation: Float
        ): Boolean {
            val cosY = cos(yawRad)
            val sinY = sin(yawRad)
            val shx = model.halfX * scale
            val shz = model.halfZ * scale
            val selfCullR = sqrt(shx * shx + shz * shz)
            val extraPad = when (model.category) {
                CAT_BUILDING -> selfCullR + 2.5f
                CAT_TREE -> max(shx, shz) * 0.50f + 1.4f
                else -> 0.6f
            }
            if (!isZone3PointClearOfRoads(cx, cz, extraPad)) return false
            if (!ThrissurFuelStationSystem.isPointClearOfFuelStations(cx, cz, extraMargin = extraPad + 4.0f)) return false

            val gcx = kotlin.math.floor(cx * invPlacedCell).toInt()
            val gcz = kotlin.math.floor(cz * invPlacedCell).toInt()
            for (dxC in -1..1) {
                for (dzC in -1..1) {
                    val bucket = placedGrid[spatialKey(gcx + dxC, gcz + dzC)] ?: continue
                    for (i in 0 until bucket.size) {
                        val other = bucket[i]
                        val dx = cx - other.cx
                        val dz = cz - other.cz
                        val maxR = selfCullR + other.cullRadiusXZ + minSeparation + 0.6f
                        if (dx * dx + dz * dz > maxR * maxR) continue
                        if (obbOverlaps2D(
                                cx, cz, cosY, sinY, shx, shz,
                                other.cx, other.cz, other.cosYaw, other.sinYaw, other.scaledHalfX, other.scaledHalfZ,
                                minSeparation
                            )
                        ) {
                            return false
                        }
                    }
                }
            }
            val modelIdx = modelIndexMap[model.id] ?: 0
            val inst = PlacedInstance(
                modelIndex = modelIdx,
                model = model,
                cx = cx,
                baseY = baseY,
                cz = cz,
                yawRad = yawRad,
                cosYaw = cosY,
                sinYaw = sinY,
                scale = scale,
                scaledHalfX = shx,
                scaledHalfZ = shz,
                cullRadiusXZ = selfCullR
            )
            placed.add(inst)
            val minGx = kotlin.math.floor((cx - selfCullR) * invPlacedCell).toInt()
            val maxGx = kotlin.math.floor((cx + selfCullR) * invPlacedCell).toInt()
            val minGz = kotlin.math.floor((cz - selfCullR) * invPlacedCell).toInt()
            val maxGz = kotlin.math.floor((cz + selfCullR) * invPlacedCell).toInt()
            for (gx in minGx..maxGx) {
                for (gz in minGz..maxGz) {
                    placedGrid.getOrPut(spatialKey(gx, gz)) { ArrayList(8) }.add(inst)
                }
            }
            return true
        }

        // 5A. Kurumali River Lush North & South Coconut Palm Riverbanks (X = 1165..1570)
        for (bankSide in intArrayOf(-1, 1)) {
            val baseZ = if (bankSide == 1) -2164.0f else -2338.0f
            for (k in 0 until 44) {
                val tx = 1165f + (k.toFloat() / 43f) * 405f + (nextRand01() - 0.5f) * 6f
                if (abs(tx - Zone3Nh544SouthRenderer.KURUMALI_CENTER_X) < 26f) continue
                val tz = baseZ + bankSide * (nextRand01() * 28f)
                val tModel = if (k % 3 != 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
                if (tryPlaceZone3(tModel, tx, 0f, tz, nextRand01() * 6.28f, 1.15f + nextRand01() * 0.55f, minSeparation = 1.8f)) {
                    tryPlaceZone3(pickGrassModel(), tx + (nextRand01() - 0.5f) * 3f, 0f, tz + (nextRand01() - 0.5f) * 3f, nextRand01() * 6.28f, 1.25f + nextRand01() * 0.45f, minSeparation = 0.3f)
                }
            }
        }

        // 5B. Chalakudy River Dense Tropical Coconut Groves along Both 500m Riverbanks (X = 2360..2835)
        for (bankSide in intArrayOf(-1, 1)) {
            val baseZ = if (bankSide == 1) -4880.0f else -5202.0f
            for (k in 0 until 62) {
                val tx = 2360f + (k.toFloat() / 61f) * 475f + (nextRand01() - 0.5f) * 7f
                if (abs(tx - Zone3Nh544SouthRenderer.CHALAKUDY_CENTER_X) < 28f) continue
                val tz = baseZ + bankSide * (nextRand01() * 38f)
                val tModel = if (k % 3 != 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[k % canopyTreeIndices.size]]
                if (tryPlaceZone3(tModel, tx, 0f, tz, nextRand01() * 6.28f, 1.22f + nextRand01() * 0.58f, minSeparation = 2.0f)) {
                    tryPlaceZone3(pickGrassModel(), tx + (nextRand01() - 0.5f) * 3.5f, 0f, tz + (nextRand01() - 0.5f) * 3.5f, nextRand01() * 6.28f, 1.28f + nextRand01() * 0.45f, minSeparation = 0.3f)
                }
            }
        }

        // 5C. NH 544 South Corridor Roadside Coconut Palms, Tropical Trees & Kerala Town Commercial Buildings
        //     (Ollur, Puthukkad, Kodakara & Chalakudy — strictly OUTSIDE the 5.5m service roads!)
        for (road in roads) {
            if (road.id != "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" && road.id != "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY") continue
            val frames = road.frames
            var fIdx = 14
            while (fIdx < frames.size - 8) {
                val f = frames[fIdx]
                if (f.pos.z < -850f && f.pos.y < 0.30f) {
                    val inTownCluster = (f.pos.z in -1310f..-980f) ||   // Ollur
                        (f.pos.z in -2060f..-1945f) ||                   // Puthukkad North (past South toll flare)
                        (f.pos.z in -2560f..-2410f) ||                   // Puthukkad South
                        (f.pos.z in -3620f..-3260f) ||                   // Kodakara Junction Town
                        (f.pos.z in -4800f..-4560f)                      // Chalakudy North Town
                    val roadYaw = kotlin.math.atan2(f.tangent.x, f.tangent.z)
                    for (side in intArrayOf(-1, 1)) {
                        // Roadside coconut palms & shade trees cleanly outside service lanes
                        val treeLat = 24.5f + nextRand01() * 14.0f
                        val tx = f.pos.x + f.right.x * side * treeLat
                        val tz = f.pos.z + f.right.z * side * treeLat
                        val tModel = if ((fIdx + side) % 3 == 0) treeModels[palmIdx] else treeModels[canopyTreeIndices[fIdx % canopyTreeIndices.size]]
                        tryPlaceZone3(tModel, tx, 0f, tz, nextRand01() * 6.28f, 1.05f + nextRand01() * 0.45f, minSeparation = 1.8f)

                        // Kerala town commercial buildings facing the NH 544 service roads at Ollur, Puthukkad, Kodakara & Chalakudy
                        if (inTownCluster && fIdx % 14 == 0 && bldgModels.isNotEmpty()) {
                            val bModel = pickBuildingModel()
                            val bScale = 0.72f + nextRand01() * 0.25f
                            val bLat = 31.0f + max(bModel.halfX, bModel.halfZ) * bScale + nextRand01() * 5.0f
                            val bx = f.pos.x + f.right.x * side * bLat
                            val bz = f.pos.z + f.right.z * side * bLat
                            val faceYaw = roadYaw + (if (side == 1) -PI.toFloat() * 0.5f else PI.toFloat() * 0.5f)
                            tryPlaceZone3(bModel, bx, 0f, bz, faceYaw, bScale, minSeparation = 2.6f)
                        }
                    }
                }
                fIdx += 7
            }
        }

        return placed
    }

    fun getOrBuildInstances(
        context: Context,
        roads: List<ThrissurOsmRoad>,
        signPoints: List<Pair<Float, Float>> = emptyList()
    ): List<PlacedInstance> {
        cachedInstances?.let { return it }
        val models = ensureLoaded(context)
        if (models.isEmpty() || roads.isEmpty()) return emptyList()
        synchronized(this) {
            cachedInstances?.let { return it }
            val built = buildZone1Layout(models, roads, signPoints)
            cachedInstances = built
            return built
        }
    }
}
