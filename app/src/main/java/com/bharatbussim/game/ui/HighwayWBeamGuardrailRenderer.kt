package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import com.bharatbussim.game.model.RoadFrame
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.Vec3
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Photo-Matched Galvanized Steel W-Beam Highway Crash Barrier (MBCB) &
 * Extruder End-Terminal (ET-Plus) Renderer for BOTH sides of NH 544 and NH 66.
 *
 * Matches the real-world highway crash barrier design:
 *  - Continuous 3-tier corrugated double-wave galvanized steel W-beam rail
 *    (bright upper ridge, recessed shadow trough, bright lower ridge) along
 *    both left and right highway shoulders at `halfWidthM + 0.88m`.
 *  - Closely spaced galvanized steel I-beam / C-channel support posts with
 *    offset spacer blocks and reflective highway delineators.
 *  - Authentic galvanized Extruder Impact End Terminals at the start and end
 *    of every barrier run (highway entrances, junctions, toll plazas, bridges),
 *    complete with:
 *      • Traffic-facing vertical Yellow & Black diagonal hazard chevron plate
 *        in a galvanized steel frame,
 *      • Boxed steel extruder chute housing feeding onto the W-beam,
 *      • Heavy twin anchor posts with diagonal galvanized lower ground strut.
 *
 * Strictly enforces Zero Road Intrusion by checking BOTH explicit bridge/toll/tunnel
 * exclusion zones AND live geometric clearance against every intersecting road in
 * the Thrissur road network on each side independently.
 */
internal object HighwayWBeamGuardrailRenderer {

    private val TARGET_HIGHWAY_IDS = setOf(
        // NH 544 corridors (Mannuthy <-> Kuthiran <-> Palakkad & Mannuthy <-> Paliyekkara <-> Chalakudy <-> Angamaly)
        "ROAD_NH544_MANNUTHY_TO_KUTHIRAN",
        "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD",
        "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY",
        // NH 66 coastal corridors (Chavakkad <-> Chettuva <-> Nattika <-> Triprayar <-> Kodungallur <-> Kottapuram)
        "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR",
        "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM"
    )

    // Continuation pairs whose welded joint should NOT be treated as a cross-road break
    private val CONTINUATION_PARTNERS = mapOf(
        "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" to "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY",
        "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY" to "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA",
        "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" to "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM",
        "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" to "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR"
    )

    // Authentic Galvanized Steel & Highway Reflector Material Palette
    private val MatGalvBright = Color(0xFFE2E8F0)      // Sunlit zinc-coated top bevel / highlight
    private val MatGalvLobeUpper = Color(0xFFD3DCE6)   // Upper W-beam wave face
    private val MatGalvLobeLower = Color(0xFFC5D0DC)   // Lower W-beam wave face
    private val MatGalvTrough = Color(0xFF6B7785)      // Deep corrugated center shadow groove
    private val MatGalvBack = Color(0xFF94A3B4)        // Rear galvanized rail face
    private val MatGalvPost = Color(0xFFA6B2C0)        // Galvanized C-channel steel post
    private val MatGalvPostShade = Color(0xFF647182)   // Post shaded flank & spacer block
    private val MatGalvChute = Color(0xFFCBD5E1)       // Extruder impact head steel housing
    private val MatGalvChuteDark = Color(0xFF7B8797)   // Extruder chute shadow side
    private val MatReflectorRed = Color(0xFFEF4444)    // Nearside red cat's-eye reflector
    private val MatReflectorAmber = Color(0xFFFACC15)  // Offside amber/yellow reflector
    private val MatReflectorWhite = Color(0xFFF8FAFC)  // Crisp white reflective strip
    private val MatHazardBase = Color(0xFFFACC15)      // Fallback solid color under chevron bitmap

    private const val SHOULDER_OFFSET_M = 0.88f
    private const val RAIL_BOTTOM_Y = 0.46f
    private const val RAIL_MID_LOW_Y = 0.58f
    private const val RAIL_MID_HIGH_Y = 0.69f
    private const val RAIL_TOP_Y = 0.83f
    private const val POST_SPACING_M = 4.6f
    private const val MIN_RUN_LENGTH_M = 18.0f

    private data class GuardrailCluster(
        val cx: Float,
        val cz: Float,
        val radius: Float,
        val faces: List<QuadFace3D>
    )

    @Volatile
    private var cachedClusters: List<GuardrailCluster>? = null

    @Volatile
    private var cachedRoadCount: Int = -1

    private val chevronLeftSpec: SignboardQuadSpec by lazy {
        createHazardChevronSignSpec(
            cacheKey = "HIGHWAY_WBEAM_HAZARD_CHEVRON_LEFT_V1",
            slantDownRight = true
        )
    }

    private val chevronRightSpec: SignboardQuadSpec by lazy {
        createHazardChevronSignSpec(
            cacheKey = "HIGHWAY_WBEAM_HAZARD_CHEVRON_RIGHT_V1",
            slantDownRight = false
        )
    }

    fun collectAllStaticGuardrailFaces(
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        val clusters = getOrBuildClusters(roads)
        for (c in clusters) {
            outFaces.addAll(c.faces)
        }
    }

    fun collectGuardrailFacesNearCamera(
        camEye: Vec3,
        roads: List<ThrissurOsmRoad>,
        outFaces: MutableList<QuadFace3D>
    ) {
        val clusters = getOrBuildClusters(roads)
        val maxCullDist = 360f
        for (c in clusters) {
            val dx = camEye.x - c.cx
            val dz = camEye.z - c.cz
            val lim = maxCullDist + c.radius
            if (dx * dx + dz * dz <= lim * lim) {
                outFaces.addAll(c.faces)
            }
        }
    }

    private fun getOrBuildClusters(roads: List<ThrissurOsmRoad>): List<GuardrailCluster> {
        val existing = cachedClusters
        if (existing != null && cachedRoadCount == roads.size) return existing
        val built = buildAllHighwayGuardrailClusters(roads)
        cachedClusters = built
        cachedRoadCount = roads.size
        return built
    }

    private data class RoadAabb(
        val road: ThrissurOsmRoad,
        val minX: Float,
        val maxX: Float,
        val minZ: Float,
        val maxZ: Float
    )

    private fun buildAllHighwayGuardrailClusters(roads: List<ThrissurOsmRoad>): List<GuardrailCluster> {
        // Precompute lightweight AABBs for every road to make cross-road clearance queries instant
        val roadAabbs = ArrayList<RoadAabb>(roads.size)
        for (r in roads) {
            if (r.frames.isEmpty()) continue
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            val pad = r.widthM * 0.5f + 8f
            for (f in r.frames) {
                if (f.pos.x < minX) minX = f.pos.x
                if (f.pos.x > maxX) maxX = f.pos.x
                if (f.pos.z < minZ) minZ = f.pos.z
                if (f.pos.z > maxZ) maxZ = f.pos.z
            }
            roadAabbs.add(RoadAabb(r, minX - pad, maxX + pad, minZ - pad, maxZ + pad))
        }

        ThrissurFuelStationSystem.ensurePlacements(roads)
        val clusters = ArrayList<GuardrailCluster>(128)
        for (road in roads) {
            if (road.id !in TARGET_HIGHWAY_IDS) continue
            val frames = road.frames
            if (frames.size < 10) continue
            val partnerId = CONTINUATION_PARTNERS[road.id]

            // Build Left (-1f) and Right (+1f) shoulder guardrails independently
            for (side in floatArrayOf(-1f, 1f)) {
                val validMask = BooleanArray(frames.size)
                for (i in 3 until frames.size - 3) {
                    val f = frames[i]
                    if (!isExplicitlyClearForGuardrail(road.id, f)) continue
                    val gx = f.pos.x + f.right.x * side * (f.halfWidthM + SHOULDER_OFFSET_M)
                    val gz = f.pos.z + f.right.z * side * (f.halfWidthM + SHOULDER_OFFSET_M)
                    if (!isClearOfIntersectingRoads(gx, gz, road.id, partnerId, roadAabbs)) continue
                    if (!ThrissurFuelStationSystem.isGuardrailClearOfFuelStations(gx, gz)) continue
                    validMask[i] = true
                }

                // Extract contiguous valid guardrail runs and emit W-beam + Posts + Start/End Extruder Terminals
                var idx = 3
                while (idx < frames.size - 3) {
                    if (!validMask[idx]) {
                        idx++
                        continue
                    }
                    val runStart = idx
                    while (idx < frames.size - 3 && validMask[idx]) {
                        idx++
                    }
                    val runEnd = idx - 1
                    val runDist = frames[runEnd].cumulativeDist - frames[runStart].cumulativeDist
                    if (runEnd - runStart >= 3 && runDist >= MIN_RUN_LENGTH_M) {
                        emitGuardrailRun(frames, runStart, runEnd, side, clusters)
                    }
                }
            }
        }
        return clusters
    }

    private fun isExplicitlyClearForGuardrail(roadId: String, f: RoadFrame): Boolean {
        val x = f.pos.x
        val y = f.pos.y
        val z = f.pos.z

        // Elevated bridge / flyover decks already carry concrete crash parapets
        if (y > 0.25f) return false

        return when (roadId) {
            "ROAD_NH544_MANNUTHY_TO_KUTHIRAN" -> {
                // Keep clear of Mannuthy 4-way interchange core & Kuthiran West rock-cuttings / portals
                if (hypot(x - Zone2Nh544KuthiranRenderer.MANNUTHY_X, z - Zone2Nh544KuthiranRenderer.MANNUTHY_Z) < 62f) return false
                if (x > 2000f) return false
                true
            }
            "ROAD_NH544_KUTHIRAN_EAST_PALAKKAD" -> {
                // Keep clear of Kuthiran East rock-cuttings / portal transition & far eastern turnaround
                if (x < 3285f) return false
                true
            }
            "ROAD_NH544_MANNUTHY_TO_PALIYEKKARA" -> {
                // Mannuthy 4-way interchange core
                if (hypot(x - Zone2Nh544KuthiranRenderer.MANNUTHY_X, z - Zone2Nh544KuthiranRenderer.MANNUTHY_Z) < 62f) return false
                // Ollur Railway Overbridge (ROB) ramps & deck
                if (z in (Zone3Nh544SouthRenderer.OLLUR_ROB_RAMP_SOUTH_Z - 12f)..(Zone3Nh544SouthRenderer.OLLUR_ROB_RAMP_NORTH_Z + 12f)) return false
                // Paliyekkara Toll Plaza flares & 12-lane PQC apron
                if (z in (Zone3Nh544SouthRenderer.TOLL_FLARE_SOUTH_END_Z - 14f)..(Zone3Nh544SouthRenderer.TOLL_FLARE_NORTH_START_Z + 14f)) return false
                true
            }
            "ROAD_NH544_PALIYEKKARA_TO_CHALAKUDY" -> {
                // Paliyekkara Toll Plaza southern taper
                if (z in (Zone3Nh544SouthRenderer.TOLL_FLARE_SOUTH_END_Z - 14f)..(Zone3Nh544SouthRenderer.TOLL_FLARE_NORTH_START_Z + 14f)) return false
                // Kurumali River Bridge ramps & deck
                if (z in (Zone3Nh544SouthRenderer.KURUMALI_RAMP_SOUTH_Z - 14f)..(Zone3Nh544SouthRenderer.KURUMALI_RAMP_NORTH_Z + 14f)) return false
                // Kodakara Junction underpass/gantry intersection
                if (hypot(x - Zone3Nh544SouthRenderer.KODAKARA_X, z - Zone3Nh544SouthRenderer.KODAKARA_Z) < 42f) return false
                // Chalakudy River Bridge + Chalakudy South Bus Bay compound
                if (z in (Zone3Nh544SouthRenderer.CHALAKUDY_TERM_Z - 55f)..(Zone3Nh544SouthRenderer.CHALAKUDY_RAMP_NORTH_Z + 14f)) return false
                true
            }
            "ROAD_NH66_CHAVAKKAD_TO_TRIPRAYAR" -> {
                // Chavakkad Beach junction
                if (z > 930f) return false
                // Chettuva Bridge ramps & deck
                if (z in (Zone5Nh66CoastalRenderer.CHETTUVA_RAMP_SOUTH_Z - 14f)..(Zone5Nh66CoastalRenderer.CHETTUVA_RAMP_NORTH_Z + 14f)) return false
                // SH 61 Thalikulam-Kanjani junction
                if (z in -1260f..-1180f) return false
                // Nattika Toll Plaza 10-lane apron & flares
                if (z in (Zone5Nh66CoastalRenderer.NATTIKA_FLARE_SOUTH_END_Z - 14f)..(Zone5Nh66CoastalRenderer.NATTIKA_FLARE_NORTH_START_Z + 14f)) return false
                // Triprayar Stand entry/exit apron
                if (z in -2490f..-2335f) return false
                true
            }
            "ROAD_NH66_TRIPRAYAR_TO_KOTTAPURAM" -> {
                // Triprayar southern junction
                if (z > -2490f) return false
                // Canoli Canal Bridge ramps & deck
                if (z in (Zone5Nh66CoastalRenderer.CANOLI_RAMP_SOUTH_Z - 14f)..(Zone5Nh66CoastalRenderer.CANOLI_RAMP_NORTH_Z + 14f)) return false
                // Kottapuram NW-3 Bridge ramps & deck
                if (z in (Zone5Nh66CoastalRenderer.KOTTAPURAM_RAMP_SOUTH_Z - 14f)..(Zone5Nh66CoastalRenderer.KOTTAPURAM_RAMP_NORTH_Z + 14f)) return false
                // Moothakunnam turnaround bay
                if (z < -4355f) return false
                true
            }
            else -> false
        }
    }

    private fun isClearOfIntersectingRoads(
        gx: Float,
        gz: Float,
        selfRoadId: String,
        partnerRoadId: String?,
        roadAabbs: List<RoadAabb>
    ): Boolean {
        for (aabb in roadAabbs) {
            val other = aabb.road
            if (other.id == selfRoadId || other.id == partnerRoadId) continue
            if (gx < aabb.minX || gx > aabb.maxX || gz < aabb.minZ || gz > aabb.maxZ) continue
            val otherFrames = other.frames
            val step = if (otherFrames.size > 160) 2 else 1
            var j = 0
            while (j < otherFrames.size) {
                val of = otherFrames[j]
                val clearR = of.halfWidthM + 5.2f
                val dx = gx - of.pos.x
                val dz = gz - of.pos.z
                if (dx * dx + dz * dz < clearR * clearR) return false
                j += step
            }
        }
        return true
    }

    private fun emitGuardrailRun(
        frames: List<RoadFrame>,
        runStart: Int,
        runEnd: Int,
        side: Float,
        outClusters: MutableList<GuardrailCluster>
    ) {
        // Chunk each run into ~160m spatial clusters for fast frustum/distance culling
        var chunkStart = runStart
        while (chunkStart < runEnd) {
            val startDist = frames[chunkStart].cumulativeDist
            var chunkEnd = chunkStart
            while (chunkEnd < runEnd && frames[chunkEnd].cumulativeDist - startDist < 160f) {
                chunkEnd++
            }
            val faces = ArrayList<QuadFace3D>(260)
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY

            fun trackPoint(x: Float, z: Float) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
            }

            // 1. Continuous 3-tier Corrugated Double-Wave W-Beam Steel Rail + Galvanized I-Posts
            var postCarryDist = 0f
            var postCount = 0
            var i = chunkStart
            while (i < chunkEnd) {
                val nextI = min(chunkEnd, i + 2)
                val f0 = frames[i]
                val f1 = frames[nextI]

                val off0 = f0.halfWidthM + SHOULDER_OFFSET_M
                val off1 = f1.halfWidthM + SHOULDER_OFFSET_M
                val x0 = f0.pos.x + f0.right.x * side * off0
                val z0 = f0.pos.z + f0.right.z * side * off0
                val x1 = f1.pos.x + f1.right.x * side * off1
                val z1 = f1.pos.z + f1.right.z * side * off1
                val y0 = f0.pos.y
                val y1 = f1.pos.y
                trackPoint(x0, z0)
                trackPoint(x1, z1)

                val dx = x1 - x0
                val dz = z1 - z0
                val segLen = hypot(dx, dz)
                if (segLen > 0.25f) {
                    val tx = dx / segLen
                    val tz = dz / segLen
                    // Outward normal pointing AWAY from highway centerline into the verge
                    val outX = f0.right.x * side
                    val outZ = f0.right.z * side
                    val inX = -outX
                    val inZ = -outZ

                    addCorrugatedWBeamSegment(
                        out = faces,
                        x0 = x0, y0 = y0, z0 = z0,
                        x1 = x1, y1 = y1, z1 = z1,
                        inX = inX, inZ = inZ,
                        outX = outX, outZ = outZ
                    )

                    // Closely spaced galvanized steel C-channel / I-beam posts along the chord (~4.6m)
                    var d = postCarryDist
                    while (d < segLen) {
                        val t = d / segLen
                        val px = x0 + dx * t
                        val py = y0 + (y1 - y0) * t
                        val pz = z0 + dz * t
                        addGalvanizedSupportPost(
                            out = faces,
                            px = px + outX * 0.14f,
                            baseY = py,
                            pz = pz + outZ * 0.14f,
                            tx = tx, tz = tz,
                            inX = inX, inZ = inZ,
                            outX = outX, outZ = outZ,
                            addReflector = (postCount % 3 == 0),
                            isLeftSide = (side < 0f)
                        )
                        postCount++
                        d += POST_SPACING_M
                    }
                    postCarryDist = d - segLen
                }
                i = nextI
            }

            // 2. Photo-Matched ET-Plus Extruder Impact End Terminal at Run START (facing -tangent)
            if (chunkStart == runStart) {
                val fStart = frames[runStart]
                val fNext = frames[min(runEnd, runStart + 1)]
                val off = fStart.halfWidthM + SHOULDER_OFFSET_M
                val sx = fStart.pos.x + fStart.right.x * side * off
                val sz = fStart.pos.z + fStart.right.z * side * off
                val tx = fNext.pos.x - fStart.pos.x
                val tz = fNext.pos.z - fStart.pos.z
                val tLen = hypot(tx, tz).coerceAtLeast(1e-3f)
                addExtruderEndTerminal(
                    out = faces,
                    hx = sx,
                    baseY = fStart.pos.y,
                    hz = sz,
                    alongRailX = tx / tLen,
                    alongRailZ = tz / tLen,
                    outX = fStart.right.x * side,
                    outZ = fStart.right.z * side,
                    side = side
                )
            }

            // 3. Photo-Matched ET-Plus Extruder Impact End Terminal at Run END (facing +tangent)
            if (chunkEnd == runEnd) {
                val fEnd = frames[runEnd]
                val fPrev = frames[max(runStart, runEnd - 1)]
                val off = fEnd.halfWidthM + SHOULDER_OFFSET_M
                val ex = fEnd.pos.x + fEnd.right.x * side * off
                val ez = fEnd.pos.z + fEnd.right.z * side * off
                val tx = fPrev.pos.x - fEnd.pos.x
                val tz = fPrev.pos.z - fEnd.pos.z
                val tLen = hypot(tx, tz).coerceAtLeast(1e-3f)
                addExtruderEndTerminal(
                    out = faces,
                    hx = ex,
                    baseY = fEnd.pos.y,
                    hz = ez,
                    alongRailX = tx / tLen,
                    alongRailZ = tz / tLen,
                    outX = fEnd.right.x * side,
                    outZ = fEnd.right.z * side,
                    side = side
                )
            }

            if (faces.isNotEmpty() && minX.isFinite()) {
                val cx = (minX + maxX) * 0.5f
                val cz = (minZ + maxZ) * 0.5f
                val radius = hypot(maxX - cx, maxZ - cz) + 8f
                outClusters.add(GuardrailCluster(cx, cz, radius, faces))
            }
            chunkStart = chunkEnd
        }
    }

    /**
     * Builds the unmistakable 3-tier corrugated double-wave galvanized steel W-beam profile:
     *  - Sunlit top bevel flange (`Y = 0.83m`)
     *  - Upper convex silver ridge (`Y = 0.69..0.83m`, proud `+0.06m` toward traffic)
     *  - Recessed center shadow wave trough (`Y = 0.58..0.69m`, recessed `-0.02m` away from traffic)
     *  - Lower convex silver ridge (`Y = 0.46..0.58m`, proud `+0.06m` toward traffic)
     *  - Galvanized steel rear face so the barrier reads solid from service roads & rear angles
     */
    private fun addCorrugatedWBeamSegment(
        out: MutableList<QuadFace3D>,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        inX: Float, inZ: Float,
        outX: Float, outZ: Float
    ) {
        val ridgeIn = 0.065f
        val troughIn = 0.005f
        val backOut = 0.075f

        val p0RidgeX = x0 + inX * ridgeIn; val p0RidgeZ = z0 + inZ * ridgeIn
        val p1RidgeX = x1 + inX * ridgeIn; val p1RidgeZ = z1 + inZ * ridgeIn

        val p0TroughX = x0 + inX * troughIn; val p0TroughZ = z0 + inZ * troughIn
        val p1TroughX = x1 + inX * troughIn; val p1TroughZ = z1 + inZ * troughIn

        val p0BackX = x0 + outX * backOut; val p0BackZ = z0 + outZ * backOut
        val p1BackX = x1 + outX * backOut; val p1BackZ = z1 + outZ * backOut

        // 1. Bright galvanized top bevel rim
        out.add(
            QuadFace3D(
                Vec3(p0RidgeX, y0 + RAIL_TOP_Y, p0RidgeZ),
                Vec3(p1RidgeX, y1 + RAIL_TOP_Y, p1RidgeZ),
                Vec3(p1BackX, y1 + RAIL_TOP_Y, p1BackZ),
                Vec3(p0BackX, y0 + RAIL_TOP_Y, p0BackZ),
                MatGalvBright
            )
        )
        // 2. Upper W-beam silver lobe facing highway traffic
        out.add(
            QuadFace3D(
                Vec3(p0RidgeX, y0 + RAIL_MID_HIGH_Y, p0RidgeZ),
                Vec3(p1RidgeX, y1 + RAIL_MID_HIGH_Y, p1RidgeZ),
                Vec3(p1RidgeX, y1 + RAIL_TOP_Y, p1RidgeZ),
                Vec3(p0RidgeX, y0 + RAIL_TOP_Y, p0RidgeZ),
                MatGalvLobeUpper
            )
        )
        // 3. Recessed central W-beam corrugated shadow trough (instant signature horizontal stripe!)
        out.add(
            QuadFace3D(
                Vec3(p0TroughX, y0 + RAIL_MID_LOW_Y, p0TroughZ),
                Vec3(p1TroughX, y1 + RAIL_MID_LOW_Y, p1TroughZ),
                Vec3(p1TroughX, y1 + RAIL_MID_HIGH_Y, p1TroughZ),
                Vec3(p0TroughX, y0 + RAIL_MID_HIGH_Y, p0TroughZ),
                MatGalvTrough
            )
        )
        // 4. Lower W-beam silver lobe facing highway traffic
        out.add(
            QuadFace3D(
                Vec3(p0RidgeX, y0 + RAIL_BOTTOM_Y, p0RidgeZ),
                Vec3(p1RidgeX, y1 + RAIL_BOTTOM_Y, p1RidgeZ),
                Vec3(p1RidgeX, y1 + RAIL_MID_LOW_Y, p1RidgeZ),
                Vec3(p0RidgeX, y0 + RAIL_MID_LOW_Y, p0RidgeZ),
                MatGalvLobeLower
            )
        )
        // 5. Rear galvanized steel face (visible from parallel service roads & opposite views)
        out.add(
            QuadFace3D(
                Vec3(p1BackX, y1 + RAIL_BOTTOM_Y, p1BackZ),
                Vec3(p0BackX, y0 + RAIL_BOTTOM_Y, p0BackZ),
                Vec3(p0BackX, y0 + RAIL_TOP_Y, p0BackZ),
                Vec3(p1BackX, y1 + RAIL_TOP_Y, p1BackZ),
                MatGalvBack
            )
        )
    }

    /**
     * Galvanized steel C-channel / I-beam support post anchored into the gravel shoulder
     * right behind the W-beam rail, plus optional IRC:119 highway reflector delineator tab.
     */
    private fun addGalvanizedSupportPost(
        out: MutableList<QuadFace3D>,
        px: Float,
        baseY: Float,
        pz: Float,
        tx: Float,
        tz: Float,
        inX: Float,
        inZ: Float,
        outX: Float,
        outZ: Float,
        addReflector: Boolean,
        isLeftSide: Boolean
    ) {
        addOrientedPostBox(
            out = out,
            cx = px,
            cz = pz,
            y0 = baseY + 0.02f,
            y1 = baseY + 0.87f,
            tx = tx, tz = tz,
            rx = outX, rz = outZ,
            halfLenAlongT = 0.065f,
            halfWidthAlongR = 0.065f,
            topCol = MatGalvBright,
            faceTCol = MatGalvPost,
            faceRCol = MatGalvPostShade
        )
        // Offset steel spacer block connecting post to the back of the W-beam rail
        addOrientedPostBox(
            out = out,
            cx = px + inX * 0.07f,
            cz = pz + inZ * 0.07f,
            y0 = baseY + 0.50f,
            y1 = baseY + 0.79f,
            tx = tx, tz = tz,
            rx = outX, rz = outZ,
            halfLenAlongT = 0.055f,
            halfWidthAlongR = 0.055f,
            topCol = MatGalvPost,
            faceTCol = MatGalvPostShade,
            faceRCol = MatGalvPost
        )
        // Highway reflective delineator tab perched on top of rail/post (visible at night & distance)
        if (addReflector) {
            val refCol = if (isLeftSide) MatReflectorRed else MatReflectorAmber
            addOrientedPostBox(
                out = out,
                cx = px + inX * 0.08f,
                cz = pz + inZ * 0.08f,
                y0 = baseY + 0.84f,
                y1 = baseY + 0.94f,
                tx = tx, tz = tz,
                rx = outX, rz = outZ,
                halfLenAlongT = 0.045f,
                halfWidthAlongR = 0.045f,
                topCol = MatReflectorWhite,
                faceTCol = refCol,
                faceRCol = MatReflectorWhite
            )
        }
    }

    /**
     * Exact 1:1 recreation of the ET-Plus / Extruder Impact End Terminal from the reference photo:
     *  1. Vertical galvanized steel impact head plate (`0.44m` wide x `0.76m` tall) facing oncoming
     *     traffic with high-contrast Yellow & Black Diagonal Hazard Chevron stripes inside a metallic frame.
     *  2. Boxed galvanized steel extruder chute (`1.15m` long) directly behind the impact face.
     *  3. Reinforced Anchor Post 1 right under the head + Anchor Post 2 at `2.05m` + Post 3 at `4.10m`.
     *  4. Angled galvanized steel diagonal ground strut bracing the base of Post 1 to mid-lower Post 2!
     */
    private fun addExtruderEndTerminal(
        out: MutableList<QuadFace3D>,
        hx: Float,
        baseY: Float,
        hz: Float,
        alongRailX: Float,
        alongRailZ: Float,
        outX: Float,
        outZ: Float,
        side: Float
    ) {
        val inX = -outX
        val inZ = -outZ
        // Traffic-facing direction points OPPOSITE alongRail (outward from the start/end of the run)
        val faceX = -alongRailX
        val faceZ = -alongRailZ

        // 1. Vertical Impact Head Steel Housing (offset slightly outward onto shoulder, matching photo!)
        val plateCenterX = hx + outX * 0.14f
        val plateCenterZ = hz + outZ * 0.14f
        val halfPlateW = 0.23f
        val plateBotY = baseY + 0.16f
        val plateTopY = baseY + 0.92f
        val plateBackX = plateCenterX + alongRailX * 0.08f
        val plateBackZ = plateCenterZ + alongRailZ * 0.08f
        val plateFrontX = plateCenterX + faceX * 0.04f
        val plateFrontZ = plateCenterZ + faceZ * 0.04f

        // Solid galvanized steel impact plate frame backing box
        addOrientedPostBox(
            out = out,
            cx = plateCenterX + alongRailX * 0.02f,
            cz = plateCenterZ + alongRailZ * 0.02f,
            y0 = plateBotY,
            y1 = plateTopY,
            tx = alongRailX, tz = alongRailZ,
            rx = outX, rz = outZ,
            halfLenAlongT = 0.055f,
            halfWidthAlongR = halfPlateW,
            topCol = MatGalvBright,
            faceTCol = MatGalvChute,
            faceRCol = MatGalvChuteDark
        )

        // Traffic-facing Yellow & Black Diagonal Chevron Hazard Plate (textured SignboardQuadSpec!)
        // Determine left vs right slant so stripes always slant downward toward the highway carriageway!
        val crossY = faceX * inZ - faceZ * inX
        val useLeftSlant = crossY >= 0f
        val chevronSpec = if (useLeftSlant) chevronLeftSpec else chevronRightSpec

        // Construct quad corners so bottom-left -> bottom-right -> top-right -> top-left faces `faceDir`
        val leftVecX = if (crossY >= 0f) outX else inX
        val leftVecZ = if (crossY >= 0f) outZ else inZ
        val rightVecX = -leftVecX
        val rightVecZ = -leftVecZ

        val qx = plateFrontX + faceX * 0.015f
        val qz = plateFrontZ + faceZ * 0.015f
        val hw = halfPlateW - 0.01f
        out.add(
            QuadFace3D(
                Vec3(qx + leftVecX * hw, plateBotY + 0.01f, qz + leftVecZ * hw),
                Vec3(qx + rightVecX * hw, plateBotY + 0.01f, qz + rightVecZ * hw),
                Vec3(qx + rightVecX * hw, plateTopY - 0.01f, qz + rightVecZ * hw),
                Vec3(qx + leftVecX * hw, plateTopY - 0.01f, qz + leftVecZ * hw),
                MatHazardBase,
                signboard = chevronSpec
            )
        )

        // 2. Galvanized Steel Extruder Chute Housing feeding directly onto the W-beam rail (0.08m..1.18m)
        val chuteMidX = hx + alongRailX * 0.63f + outX * 0.02f
        val chuteMidZ = hz + alongRailZ * 0.63f + outZ * 0.02f
        addOrientedPostBox(
            out = out,
            cx = chuteMidX,
            cz = chuteMidZ,
            y0 = baseY + 0.37f,
            y1 = baseY + 0.90f,
            tx = alongRailX, tz = alongRailZ,
            rx = outX, rz = outZ,
            halfLenAlongT = 0.56f,
            halfWidthAlongR = 0.125f,
            topCol = MatGalvBright,
            faceTCol = MatGalvChuteDark,
            faceRCol = MatGalvChute
        )
        // Recessed shadow flange detail on the traffic-facing flank of the extruder chute
        val chuteFlangeInX = chuteMidX + inX * 0.132f
        val chuteFlangeInZ = chuteMidZ + inZ * 0.132f
        val c0x = chuteFlangeInX - alongRailX * 0.50f; val c0z = chuteFlangeInZ - alongRailZ * 0.50f
        val c1x = chuteFlangeInX + alongRailX * 0.50f; val c1z = chuteFlangeInZ + alongRailZ * 0.50f
        out.add(
            QuadFace3D(
                Vec3(c0x, baseY + 0.57f, c0z),
                Vec3(c1x, baseY + 0.57f, c1z),
                Vec3(c1x, baseY + 0.70f, c1z),
                Vec3(c0x, baseY + 0.70f, c0z),
                MatGalvTrough
            )
        )

        // 3. Heavy Anchor Post 1 (at terminal head), Post 2 (at 2.05m), & Post 3 (at 4.10m)
        val p1x = hx + alongRailX * 0.14f + outX * 0.14f
        val p1z = hz + alongRailZ * 0.14f + outZ * 0.14f
        val p2x = hx + alongRailX * 2.05f + outX * 0.14f
        val p2z = hz + alongRailZ * 2.05f + outZ * 0.14f
        val p3x = hx + alongRailX * 4.10f + outX * 0.14f
        val p3z = hz + alongRailZ * 4.10f + outZ * 0.14f

        addOrientedPostBox(
            out = out,
            cx = p1x, cz = p1z,
            y0 = baseY + 0.01f, y1 = baseY + 0.93f,
            tx = alongRailX, tz = alongRailZ,
            rx = outX, rz = outZ,
            halfLenAlongT = 0.095f, halfWidthAlongR = 0.095f,
            topCol = MatGalvBright, faceTCol = MatGalvPost, faceRCol = MatGalvPostShade
        )
        addGalvanizedSupportPost(
            out = out, px = p2x, baseY = baseY, pz = p2z,
            tx = alongRailX, tz = alongRailZ,
            inX = inX, inZ = inZ, outX = outX, outZ = outZ,
            addReflector = false, isLeftSide = (side < 0f)
        )
        addGalvanizedSupportPost(
            out = out, px = p3x, baseY = baseY, pz = p3z,
            tx = alongRailX, tz = alongRailZ,
            inX = inX, inZ = inZ, outX = outX, outZ = outZ,
            addReflector = true, isLeftSide = (side < 0f)
        )

        // 4. Signature Diagonal Galvanized Steel Ground Strut (Post 1 base -> Post 2 lower-mid shaft!)
        val strutY0 = baseY + 0.12f
        val strutY1 = baseY + 0.42f
        val stH = 0.048f
        val stW = 0.048f
        // Traffic-facing side of diagonal strut
        out.add(
            QuadFace3D(
                Vec3(p1x + inX * stW, strutY0 - stH, p1z + inZ * stW),
                Vec3(p2x + inX * stW, strutY1 - stH, p2z + inZ * stW),
                Vec3(p2x + inX * stW, strutY1 + stH, p2z + inZ * stW),
                Vec3(p1x + inX * stW, strutY0 + stH, p1z + inZ * stW),
                MatGalvBright
            )
        )
        // Outer shoulder-facing side of diagonal strut
        out.add(
            QuadFace3D(
                Vec3(p2x + outX * stW, strutY1 - stH, p2z + outZ * stW),
                Vec3(p1x + outX * stW, strutY0 - stH, p1z + outZ * stW),
                Vec3(p1x + outX * stW, strutY0 + stH, p1z + outZ * stW),
                Vec3(p2x + outX * stW, strutY1 + stH, p2z + outZ * stW),
                MatGalvPostShade
            )
        )
        // Top sunlit flange of diagonal strut
        out.add(
            QuadFace3D(
                Vec3(p1x + inX * stW, strutY0 + stH, p1z + inZ * stW),
                Vec3(p2x + inX * stW, strutY1 + stH, p2z + inZ * stW),
                Vec3(p2x + outX * stW, strutY1 + stH, p2z + outZ * stW),
                Vec3(p1x + outX * stW, strutY0 + stH, p1z + outZ * stW),
                MatGalvChute
            )
        )
        // Keep back plate corner reference clean (suppress unused warning if any)
        if (plateBackX.isNaN() || plateBackZ.isNaN()) return
    }

    /**
     * Compact oriented 5-face box (top + 4 vertical faces, no ground-hidden bottom face)
     * aligned strictly with the local road tangent `(tx, tz)` and lateral `(rx, rz)`.
     */
    private fun addOrientedPostBox(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        y0: Float,
        y1: Float,
        tx: Float,
        tz: Float,
        rx: Float,
        rz: Float,
        halfLenAlongT: Float,
        halfWidthAlongR: Float,
        topCol: Color,
        faceTCol: Color,
        faceRCol: Color
    ) {
        val dtX = tx * halfLenAlongT; val dtZ = tz * halfLenAlongT
        val drX = rx * halfWidthAlongR; val drZ = rz * halfWidthAlongR

        val aX = cx - dtX - drX; val aZ = cz - dtZ - drZ
        val bX = cx + dtX - drX; val bZ = cz + dtZ - drZ
        val cX = cx + dtX + drX; val cZ = cz + dtZ + drZ
        val dX = cx - dtX + drX; val dZ = cz - dtZ + drZ

        // Top cap
        out.add(QuadFace3D(Vec3(aX, y1, aZ), Vec3(bX, y1, bZ), Vec3(cX, y1, cZ), Vec3(dX, y1, dZ), topCol))
        // Four vertical walls
        out.add(QuadFace3D(Vec3(aX, y0, aZ), Vec3(bX, y0, bZ), Vec3(bX, y1, bZ), Vec3(aX, y1, aZ), faceRCol))
        out.add(QuadFace3D(Vec3(cX, y0, cZ), Vec3(dX, y0, dZ), Vec3(dX, y1, dZ), Vec3(cX, y1, cZ), faceRCol))
        out.add(QuadFace3D(Vec3(bX, y0, bZ), Vec3(cX, y0, cZ), Vec3(cX, y1, cZ), Vec3(bX, y1, bZ), faceTCol))
        out.add(QuadFace3D(Vec3(dX, y0, dZ), Vec3(aX, y0, aZ), Vec3(aX, y1, aZ), Vec3(dX, y1, dZ), faceTCol))
    }

    /**
     * Procedural high-contrast Yellow & Black Diagonal Hazard Chevron Signboard Bitmap
     * framed inside a brushed galvanized steel bezel, matching the reference photograph.
     */
    private fun createHazardChevronSignSpec(
        cacheKey: String,
        slantDownRight: Boolean
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 128,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val bmp = Bitmap.createBitmap(128, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer brushed galvanized steel frame
        p.color = 0xFFC5CFD9.toInt()
        c.drawRect(0f, 0f, 128f, 256f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0xFF7B8797.toInt()
        c.drawRect(2f, 2f, 126f, 254f, p)

        // Inner hazard panel clip area
        p.style = Paint.Style.FILL
        val innerRect = RectF(12f, 12f, 116f, 244f)
        c.save()
        c.clipRect(innerRect)

        // Rich reflective highway hazard amber-yellow base
        p.color = 0xFFE89F12.toInt()
        c.drawRect(innerRect, p)

        // Bold diagonal matte-black stripes matching the exact rhythm of the reference photo
        p.color = 0xFF141210.toInt()
        val stripePitch = 44f
        val stripeWidth = 22f
        val path = AndroidPath()
        var y = -160f
        while (y < 420f) {
            path.reset()
            if (slantDownRight) {
                path.moveTo(0f, y)
                path.lineTo(128f, y + 128f)
                path.lineTo(128f, y + 128f + stripeWidth)
                path.lineTo(0f, y + stripeWidth)
            } else {
                path.moveTo(128f, y)
                path.lineTo(0f, y + 128f)
                path.lineTo(0f, y + 128f + stripeWidth)
                path.lineTo(128f, y + stripeWidth)
            }
            path.close()
            c.drawPath(path, p)
            y += stripePitch
        }
        c.restore()

        // Inner galvanized steel shadow recess lip & subtle corner rivet bolts
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0xFF475569.toInt()
        c.drawRect(innerRect, p)

        p.style = Paint.Style.FILL
        p.color = 0xFF64748B.toInt()
        for (rx in floatArrayOf(7f, 121f)) {
            for (ry in floatArrayOf(18f, 238f)) {
                c.drawCircle(rx, ry, 2.5f, p)
            }
        }
        bmp
    }
}
