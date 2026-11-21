package com.bharatbussim.game.ui

import com.bharatbussim.game.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds static subdivided GPU vertex buffers for realistic 60 FPS OpenGL ES 3.0 animated flowing water
 * across Thrissur (Zone 1 & Zone 2):
 *
 * Water Types (`aWaterParams.x`):
 * - `0.0f` = Fast-flowing Waterfall / Curved Ogee Dam Spillway Cascade (Peechi Dam 4-Gate Spillway & Kuthiran Cliff Waterfall)
 * - `1.0f` = Turbulent Boiling Plunge Pool & Downstream Tailrace River (Peechi Dam Stilling Basin/Tailrace & Kuthiran Cliff Pool)
 * - `2.0f` = Sunlit Wind-Rippled Reservoir & Sacred Temple Ponds (Peechi Reservoir Lake, Vadakkumnathan Kulam, Guruvayur Rudratheertham)
 * - `3.0f` = Arabian Sea Coastal Swells & Rolling Breaking Shoreline Surf (Chavakkad Beach, Azhi Estuary, Triprayar-Nattika Beach)
 * - `4.0f` = Rising Translucent Waterfall / Spillway Spray Mist Curtains (drawn in separate depth-write-off pass)
 *
 * Vertex layout (10 floats / 40 bytes per vertex):
 *   [x, y, z, nx, ny, nz, flowU, flowV, waterType, intensity]
 */
internal object ThrissurAnimatedWaterBuilder {

    const val FLOATS_PER_VERTEX = 10

    const val TYPE_WATERFALL_CASCADE = 0.0f
    const val TYPE_RIVER_PLUNGE = 1.0f
    const val TYPE_LAKE_RESERVOIR = 2.0f
    const val TYPE_OCEAN_SURF = 3.0f
    const val TYPE_SPRAY_MIST = 4.0f

    data class BuiltWaterBuffers(
        val surfaceFloats: FloatArray,
        val surfaceVertexCount: Int,
        val mistFloats: FloatArray,
        val mistVertexCount: Int
    )

    private class FloatAccumulator(initialCapacity: Int) {
        var data = FloatArray(initialCapacity)
        var size = 0

        fun ensure(extra: Int) {
            if (size + extra > data.size) {
                var newCap = data.size * 2
                while (newCap < size + extra) newCap *= 2
                data = data.copyOf(newCap)
            }
        }

        fun addVertex(
            x: Float, y: Float, z: Float,
            nx: Float, ny: Float, nz: Float,
            u: Float, v: Float,
            waterType: Float,
            intensity: Float
        ) {
            ensure(FLOATS_PER_VERTEX)
            var c = size
            data[c++] = x
            data[c++] = y
            data[c++] = z
            data[c++] = nx
            data[c++] = ny
            data[c++] = nz
            data[c++] = u
            data[c++] = v
            data[c++] = waterType
            data[c++] = intensity
            size = c
        }

        fun addQuad(
            p0: Vec3, n0: Vec3, u0: Float, v0: Float, i0: Float,
            p1: Vec3, n1: Vec3, u1: Float, v1: Float, i1: Float,
            p2: Vec3, n2: Vec3, u2: Float, v2: Float, i2: Float,
            p3: Vec3, n3: Vec3, u3: Float, v3: Float, i3: Float,
            waterType: Float
        ) {
            // Tri 1: 0, 1, 2
            addVertex(p0.x, p0.y, p0.z, n0.x, n0.y, n0.z, u0, v0, waterType, i0)
            addVertex(p1.x, p1.y, p1.z, n1.x, n1.y, n1.z, u1, v1, waterType, i1)
            addVertex(p2.x, p2.y, p2.z, n2.x, n2.y, n2.z, u2, v2, waterType, i2)
            // Tri 2: 0, 2, 3
            addVertex(p0.x, p0.y, p0.z, n0.x, n0.y, n0.z, u0, v0, waterType, i0)
            addVertex(p2.x, p2.y, p2.z, n2.x, n2.y, n2.z, u2, v2, waterType, i2)
            addVertex(p3.x, p3.y, p3.z, n3.x, n3.y, n3.z, u3, v3, waterType, i3)
        }

        fun toTrimmedArray(): FloatArray = data.copyOf(size)
    }

    @Volatile
    private var cachedBuffers: BuiltWaterBuffers? = null

    fun buildIfNeeded(): BuiltWaterBuffers {
        cachedBuffers?.let { return it }

        val surf = FloatAccumulator(65536)
        val mist = FloatAccumulator(8192)

        // 1. Peechi Dam 4-Gate Curved Ogee Spillway Cascades + Rising Spray Mist
        buildPeechiDamSpillwayCascades(surf, mist)

        // 2. Peechi Dam Boiling Plunge Pool & Winding Downstream Tailrace River
        buildPeechiPlungePoolAndTailraceRiver(surf)

        // 3. Peechi Reservoir Expansive Sunlit Mountain Lake
        buildPeechiReservoirLake(surf)

        // 4. Kuthiran Mala West Portal Monsoon Cliff Waterfall + Plunge Pool + Mist
        buildKuthiranCliffWaterfall(surf, mist)

        // 5. Sacred Kerala Temple Ponds (Vadakkumnathan Kulam & Guruvayur Rudratheertham)
        buildTemplePonds(surf)

        // 6. Arabian Sea Coastal Waves & Breaking Shoreline Surf (Chavakkad Beach & Triprayar Beach)
        buildArabianSeaCoasts(surf)

        // 7. Zone 3 Major Kerala Rivers (Kurumali River at Puthukkad & 280m Chalakudy River)
        buildZone3MajorRivers(surf)

        // 8. Zone 4 Kole Wetlands Canals, Enamavu Sluice Regulator Lake/Sea Backwaters & Bharathappuzha (Nila River)
        buildZone4WetlandsEnamavuAndBharathappuzha(surf)

        // 9. Zone 5 NH 66 Coastal Backwaters: Chettuva Estuary, Triprayar Canoli Canal & Kottapuram NW-3 Periyar Lagoon
        buildZone5CoastalBackwatersAndLagoon(surf)

        val built = BuiltWaterBuffers(
            surfaceFloats = surf.toTrimmedArray(),
            surfaceVertexCount = surf.size / FLOATS_PER_VERTEX,
            mistFloats = mist.toTrimmedArray(),
            mistVertexCount = mist.size / FLOATS_PER_VERTEX
        )
        cachedBuffers = built
        return built
    }

    // =========================================================================
    // 1. PEECHI DAM 4-GATE CURVED OGEE SPILLWAY CASCADES + SPRAY MIST
    // =========================================================================
    private fun buildPeechiDamSpillwayCascades(
        surf: FloatAccumulator,
        mist: FloatAccumulator
    ) {
        val damWestX = 3460.0f
        val spillZ0 = 65.0f
        val spillZ1 = 205.0f
        val gateBayCount = 4
        val baySpanZ = (spillZ1 - spillZ0) / gateBayCount // 35m per bay

        val vSegs = 12
        val uSegs = 6

        for (g in 0 until gateBayCount) {
            val gz0 = spillZ0 + g * baySpanZ + 2.55f
            val gz1 = spillZ0 + (g + 1) * baySpanZ - 2.55f
            val widthM = gz1 - gz0
            val isMainTorrent = (g == 1 || g == 2)
            val baseIntensity = if (isMainTorrent) 1.0f else 0.42f

            // Parabolic hydraulic ogee spillway trajectory arching smoothly in front of concrete chute:
            // Top sluice lip (v=0): (X = damWestX + 2.15, Y = 14.35)
            // Flip-bucket plunge toe (v=1): (X = damWestX - 5.95, Y = 0.14)
            fun ogeePos(t: Float): Pair<Float, Float> {
                val xLin = (damWestX + 2.15f) * (1f - t) + (damWestX - 5.95f) * t
                val yLin = 14.35f * (1f - t) + 0.14f * t
                // Parabolic hydraulic arch: projects slightly outward/upward at crest, smoothly scoops into plunge pool
                val archOut = sin(t * PI.toFloat()) * (if (isMainTorrent) 0.72f else 0.34f)
                val flipLift = sin(t * t * PI.toFloat()) * (if (isMainTorrent) 0.25f else 0.10f)
                return Pair(xLin - archOut, yLin + flipLift)
            }

            fun ogeeNormal(t: Float): Vec3 {
                val eps = 0.02f
                val pA = ogeePos((t - eps).coerceAtLeast(0f))
                val pB = ogeePos((t + eps).coerceAtMost(1f))
                val dx = pB.first - pA.first   // negative (moving West)
                val dy = pB.second - pA.second // negative (moving Down)
                // Outward normal facing West (-X) & slightly Up (+Y): (-dy, dx, 0) -> wait:
                // Since dx < 0, dy < 0: (dy, -dx, 0) has negative X and positive Y!
                val nx = dy
                val ny = -dx
                val len = hypot(nx, ny).coerceAtLeast(1e-4f)
                return Vec3(nx / len, ny / len, 0f)
            }

            for (iv in 0 until vSegs) {
                val t0 = iv.toFloat() / vSegs
                val t1 = (iv + 1).toFloat() / vSegs
                val (x0, y0) = ogeePos(t0)
                val (x1, y1) = ogeePos(t1)
                val n0 = ogeeNormal(t0)
                val n1 = ogeeNormal(t1)

                for (iu in 0 until uSegs) {
                    val s0 = iu.toFloat() / uSegs
                    val s1 = (iu + 1).toFloat() / uSegs
                    val zA = gz0 + s0 * widthM
                    val zB = gz0 + s1 * widthM
                    val uA = s0 * widthM
                    val uB = s1 * widthM

                    // Subtle side tapering near concrete spillway piers so water hugs piers naturally
                    val edgeA = if (iu == 0) 0.72f else 1.0f
                    val edgeB = if (iu == uSegs - 1) 0.72f else 1.0f

                    surf.addQuad(
                        Vec3(x0, y0, zA), n0, uA, t0, baseIntensity * edgeA,
                        Vec3(x0, y0, zB), n0, uB, t0, baseIntensity * edgeB,
                        Vec3(x1, y1, zB), n1, uB, t1, baseIntensity * edgeB,
                        Vec3(x1, y1, zA), n1, uA, t1, baseIntensity * edgeA,
                        TYPE_WATERFALL_CASCADE
                    )
                }
            }

            // Billowing translucent rising spray mist curtains at the plunge toe of each gate!
            val mistStrength = if (isMainTorrent) 0.95f else 0.32f
            val mistH = if (isMainTorrent) 4.8f else 2.2f
            // 2 staggered curtains so mist has real volumetric depth when viewed from any angle
            val mistPlanes = arrayOf(
                Pair(damWestX - 5.85f, 1.0f),
                Pair(damWestX - 4.75f, 0.78f)
            )
            for ((mx, layerScale) in mistPlanes) {
                val mZ0 = gz0 - 0.6f
                val mZ1 = gz1 + 0.6f
                val mNorm = Vec3(-1f, 0.18f, 0f)
                mist.addQuad(
                    Vec3(mx, 0.14f, mZ0), mNorm, 0f, 0f, mistStrength * layerScale,
                    Vec3(mx, 0.14f, mZ1), mNorm, 1f, 0f, mistStrength * layerScale,
                    Vec3(mx - 0.65f, 0.14f + mistH, mZ1), mNorm, 1f, 1f, mistStrength * layerScale,
                    Vec3(mx - 0.65f, 0.14f + mistH, mZ0), mNorm, 0f, 1f, mistStrength * layerScale,
                    TYPE_SPRAY_MIST
                )
            }
        }
    }

    // =========================================================================
    // 2. PEECHI DAM BOILING PLUNGE POOL & WINDING DOWNSTREAM TAILRACE RIVER
    //    Strictly outside Peechi Dam Bus Compound Walls (East of X=3454.1, North of Z=179.0)
    // =========================================================================
    private fun buildPeechiPlungePoolAndTailraceRiver(surf: FloatAccumulator) {
        val up = Vec3(0f, 1f, 0f)
        val poolY = 0.135f

        // A. Direct Spillway Plunge Stilling Basin (X = 3454.15 .. 3460.1, Z = 65.0 .. 205.0)
        //    Water rushes Westward out of the chute & turns Northward toward the tailrace channel!
        val poolZSegs = 20
        val poolXSegs = 3
        val xMin = 3454.15f
        val xMax = 3460.10f
        val zMin = 65.0f
        val zMax = 205.0f

        fun poolTurbulence(wx: Float, wz: Float): Float {
            // Highest boiling whitewater directly under active center gates (Z = 100..170, X = 3454.5..3459.5)
            val nearCenterGates = if (wz in 98f..172f) 1.0f else 0.62f
            val impactBoost = ((wx - xMin) / (xMax - xMin)).coerceIn(0f, 1f)
            return (0.48f + 0.52f * impactBoost) * nearCenterGates
        }

        for (iz in 0 until poolZSegs) {
            val z0 = zMin + (zMax - zMin) * (iz.toFloat() / poolZSegs)
            val z1 = zMin + (zMax - zMin) * ((iz + 1).toFloat() / poolZSegs)
            for (ix in 0 until poolXSegs) {
                val x0 = xMin + (xMax - xMin) * (ix.toFloat() / poolXSegs)
                val x1 = xMin + (xMax - xMin) * ((ix + 1).toFloat() / poolXSegs)

                // Flow UV: v increases along primary flow direction (Northward + slight Westward push)
                val u00 = x0 - xMin; val v00 = z0 - zMin
                val u10 = x1 - xMin; val v10 = z0 - zMin
                val u11 = x1 - xMin; val v11 = z1 - zMin
                val u01 = x0 - xMin; val v01 = z1 - zMin

                surf.addQuad(
                    Vec3(x0, poolY, z0), up, u00, v00, poolTurbulence(x0, z0),
                    Vec3(x1, poolY, z0), up, u10, v10, poolTurbulence(x1, z0),
                    Vec3(x1, poolY, z1), up, u11, v11, poolTurbulence(x1, z1),
                    Vec3(x0, poolY, z1), up, u01, v01, poolTurbulence(x0, z1),
                    TYPE_RIVER_PLUNGE
                )
            }
        }

        // B. Winding Downstream Tailrace River Channel flowing North then West-NorthWest away from dam!
        //    Defined as ordered cross-section pairs (leftBank, rightBank) strictly North of Z = 180.0!
        data class RiverStation(val lx: Float, val lz: Float, val rx: Float, val rz: Float, val turb: Float)
        val stations = arrayOf(
            RiverStation(3454.15f, 182.0f, 3460.10f, 182.0f, 0.78f),
            RiverStation(3446.0f,  198.0f, 3460.0f,  208.0f, 0.68f),
            RiverStation(3428.0f,  212.0f, 3459.5f,  234.0f, 0.56f),
            RiverStation(3405.0f,  222.0f, 3456.0f,  262.0f, 0.48f),
            RiverStation(3386.0f,  234.0f, 3432.0f,  288.0f, 0.42f),
            RiverStation(3364.0f,  248.0f, 3402.0f,  302.0f, 0.36f),
            RiverStation(3340.0f,  258.0f, 3374.0f,  304.0f, 0.32f),
            RiverStation(3318.0f,  266.0f, 3348.0f,  298.0f, 0.28f)
        )

        var accumFlowV = 117.0f // Continues seamlessly from plunge pool v
        val crossSegs = 4
        val longSubSegs = 2

        for (s in 0 until stations.size - 1) {
            val stA = stations[s]
            val stB = stations[s + 1]
            val midAx = (stA.lx + stA.rx) * 0.5f
            val midAz = (stA.lz + stA.rz) * 0.5f
            val midBx = (stB.lx + stB.rx) * 0.5f
            val midBz = (stB.lz + stB.rz) * 0.5f
            val segLen = hypot(midBx - midAx, midBz - midAz)

            for (il in 0 until longSubSegs) {
                val lt0 = il.toFloat() / longSubSegs
                val lt1 = (il + 1).toFloat() / longSubSegs
                val v0 = accumFlowV + segLen * lt0
                val v1 = accumFlowV + segLen * lt1

                val l0x = stA.lx + (stB.lx - stA.lx) * lt0
                val l0z = stA.lz + (stB.lz - stA.lz) * lt0
                val r0x = stA.rx + (stB.rx - stA.rx) * lt0
                val r0z = stA.rz + (stB.rz - stA.rz) * lt0
                val turb0 = stA.turb + (stB.turb - stA.turb) * lt0

                val l1x = stA.lx + (stB.lx - stA.lx) * lt1
                val l1z = stA.lz + (stB.lz - stA.lz) * lt1
                val r1x = stA.rx + (stB.rx - stA.rx) * lt1
                val r1z = stA.rz + (stB.rz - stA.rz) * lt1
                val turb1 = stA.turb + (stB.turb - stA.turb) * lt1

                val w0 = hypot(r0x - l0x, r0z - l0z)
                val w1 = hypot(r1x - l1x, r1z - l1z)

                for (ic in 0 until crossSegs) {
                    val ct0 = ic.toFloat() / crossSegs
                    val ct1 = (ic + 1).toFloat() / crossSegs

                    val p00 = Vec3(l0x + (r0x - l0x) * ct0, poolY, l0z + (r0z - l0z) * ct0)
                    val p10 = Vec3(l0x + (r0x - l0x) * ct1, poolY, l0z + (r0z - l0z) * ct1)
                    val p11 = Vec3(l1x + (r1x - l1x) * ct1, poolY, l1z + (r1z - l1z) * ct1)
                    val p01 = Vec3(l1x + (r1x - l1x) * ct0, poolY, l1z + (r1z - l1z) * ct0)

                    surf.addQuad(
                        p00, up, ct0 * w0, v0, turb0,
                        p10, up, ct1 * w0, v0, turb0,
                        p11, up, ct1 * w1, v1, turb1,
                        p01, up, ct0 * w1, v1, turb1,
                        TYPE_RIVER_PLUNGE
                    )
                }
            }
            accumFlowV += segLen
        }
    }

    // =========================================================================
    // 3. PEECHI RESERVOIR EXPANSIVE SUNLIT MOUNTAIN LAKE (Y = 19.22m)
    // =========================================================================
    private fun buildPeechiReservoirLake(surf: FloatAccumulator) {
        val up = Vec3(0f, 1f, 0f)
        val xMin = 3482.5f
        val xMax = 3945.0f
        val zMin = -155.0f
        val zMax = 415.0f
        val lakeY = 19.22f

        val nx = 14
        val nz = 18
        for (iz in 0 until nz) {
            val z0 = zMin + (zMax - zMin) * (iz.toFloat() / nz)
            val z1 = zMin + (zMax - zMin) * ((iz + 1).toFloat() / nz)
            for (ix in 0 until nx) {
                val x0 = xMin + (xMax - xMin) * (ix.toFloat() / nx)
                val x1 = xMin + (xMax - xMin) * ((ix + 1).toFloat() / nx)
                surf.addQuad(
                    Vec3(x0, lakeY, z0), up, x0, z0, 1.0f,
                    Vec3(x1, lakeY, z0), up, x1, z0, 1.0f,
                    Vec3(x1, lakeY, z1), up, x1, z1, 1.0f,
                    Vec3(x0, lakeY, z1), up, x0, z1, 1.0f,
                    TYPE_LAKE_RESERVOIR
                )
            }
        }
    }

    // =========================================================================
    // 4. KUTHIRAN MALA WEST PORTAL MONSOON CLIFF WATERFALL + POOL + MIST
    //    Located at X = 2124..2131.5, Z = 1102.5..1114.2 (strictly +5.8m CLEAR of NH 544 Right Tube!)
    // =========================================================================
    private fun buildKuthiranCliffWaterfall(
        surf: FloatAccumulator,
        mist: FloatAccumulator
    ) {
        val wfX0 = 2124.2f
        val wfX1 = 2131.3f
        val zTop = 1140.0f - 37.5f // 1102.5
        val zBot = 1140.0f - 28.5f // 1111.5
        val yTop = 17.5f
        val yBot = 0.16f

        val vSegs = 10
        val uSegs = 4

        fun cliffPos(t: Float): Pair<Float, Float> {
            val yLin = yTop * (1f - t) + yBot * t
            val zLin = zTop * (1f - t) + zBot * t
            // Natural waterfall arc leaping outward (+Z) slightly over rock ledges
            val leapZ = sin(t * PI.toFloat()) * 0.65f + sin(t * 2f * PI.toFloat()) * 0.12f
            return Pair(yLin, zLin + leapZ)
        }

        fun cliffNormal(t: Float): Vec3 {
            val eps = 0.02f
            val a = cliffPos((t - eps).coerceAtLeast(0f))
            val b = cliffPos((t + eps).coerceAtMost(1f))
            val dy = b.first - a.first   // negative (falling)
            val dz = b.second - a.second // positive (toward +Z road)
            // Outward normal facing +Z and +Y: (0, dz, -dy)
            val ny = dz
            val nz = -dy
            val len = hypot(ny, nz).coerceAtLeast(1e-4f)
            return Vec3(0f, ny / len, nz / len)
        }

        for (iv in 0 until vSegs) {
            val t0 = iv.toFloat() / vSegs
            val t1 = (iv + 1).toFloat() / vSegs
            val (y0, z0) = cliffPos(t0)
            val (y1, z1) = cliffPos(t1)
            val n0 = cliffNormal(t0)
            val n1 = cliffNormal(t1)

            // Narrower at top cliff notch, fanning gently wider as it cascades down
            val xL0 = wfX0 + 0.85f * (1f - t0)
            val xR0 = wfX1 - 0.85f * (1f - t0)
            val xL1 = wfX0 + 0.85f * (1f - t1)
            val xR1 = wfX1 - 0.85f * (1f - t1)
            val w0 = xR0 - xL0
            val w1 = xR1 - xL1

            for (iu in 0 until uSegs) {
                val s0 = iu.toFloat() / uSegs
                val s1 = (iu + 1).toFloat() / uSegs
                surf.addQuad(
                    Vec3(xL0 + s0 * w0, y0, z0), n0, s0 * w0, t0, 0.94f,
                    Vec3(xL0 + s1 * w0, y0, z0), n0, s1 * w0, t0, 0.94f,
                    Vec3(xL1 + s1 * w1, y1, z1), n1, s1 * w1, t1, 0.94f,
                    Vec3(xL1 + s0 * w1, y1, z1), n1, s0 * w1, t1, 0.94f,
                    TYPE_WATERFALL_CASCADE
                )
            }
        }

        // Small Natural Rocky Plunge Pool at base of Kuthiran Cliff Waterfall
        // Strictly within Z = 1109.2 .. 1114.2 (+5.8m clear of NH 544 Right Tube South edge at Z = 1120.0!)
        val up = Vec3(0f, 1f, 0f)
        val pX0 = 2121.6f; val pX1 = 2134.0f
        val pZ0 = 1109.2f; val pZ1 = 1114.2f
        val pY = 0.135f
        for (iz in 0 until 2) {
            val z0 = pZ0 + (pZ1 - pZ0) * (iz / 2f)
            val z1 = pZ0 + (pZ1 - pZ0) * ((iz + 1) / 2f)
            for (ix in 0 until 3) {
                val x0 = pX0 + (pX1 - pX0) * (ix / 3f)
                val x1 = pX0 + (pX1 - pX0) * ((ix + 1) / 3f)
                surf.addQuad(
                    Vec3(x0, pY, z0), up, x0 - pX0, z0 - pZ0, 0.90f,
                    Vec3(x1, pY, z0), up, x1 - pX0, z0 - pZ0, 0.90f,
                    Vec3(x1, pY, z1), up, x1 - pX0, z1 - pZ0, 0.72f,
                    Vec3(x0, pY, z1), up, x0 - pX0, z1 - pZ0, 0.72f,
                    TYPE_RIVER_PLUNGE
                )
            }
        }

        // Rising Cliff Waterfall Spray Mist Curtain
        val mistNorm = Vec3(0f, 0.2f, 1f)
        mist.addQuad(
            Vec3(wfX0 - 0.8f, 0.14f, zBot + 0.35f), mistNorm, 0f, 0f, 0.88f,
            Vec3(wfX1 + 0.8f, 0.14f, zBot + 0.35f), mistNorm, 1f, 0f, 0.88f,
            Vec3(wfX1 + 1.0f, 3.85f, zBot + 0.85f), mistNorm, 1f, 1f, 0.88f,
            Vec3(wfX0 - 1.0f, 3.85f, zBot + 0.85f), mistNorm, 0f, 1f, 0.88f,
            TYPE_SPRAY_MIST
        )
    }

    // =========================================================================
    // 5. SACRED KERALA TEMPLE PONDS (VADAKKUMNATHAN KULAM & GURUVAYUR)
    // =========================================================================
    private fun buildTemplePonds(surf: FloatAccumulator) {
        val up = Vec3(0f, 1f, 0f)

        // A. Vadakkumnathan Sacred Kulam (SW Thekkinkadu Maidan inside Swaraj Round)
        addSubdividedLakePatch(
            surf = surf,
            xMin = -31.15f, xMax = -21.85f,
            zMin = -16.15f, zMax = -6.85f,
            y = 0.825f,
            nx = 4, nz = 4,
            intensity = 0.42f,
            normal = up
        )

        // B. Guruvayur Sacred Rudratheertham Pond (North of Sree Krishna Temple)
        addSubdividedLakePatch(
            surf = surf,
            xMin = -2625.8f, xMax = -2580.2f,
            zMin = 1292.2f, zMax = 1325.8f,
            y = 0.095f,
            nx = 8, nz = 6,
            intensity = 0.46f,
            normal = up
        )
    }

    private fun addSubdividedLakePatch(
        surf: FloatAccumulator,
        xMin: Float, xMax: Float,
        zMin: Float, zMax: Float,
        y: Float,
        nx: Int, nz: Int,
        intensity: Float,
        normal: Vec3
    ) {
        for (iz in 0 until nz) {
            val z0 = zMin + (zMax - zMin) * (iz.toFloat() / nz)
            val z1 = zMin + (zMax - zMin) * ((iz + 1).toFloat() / nz)
            for (ix in 0 until nx) {
                val x0 = xMin + (xMax - xMin) * (ix.toFloat() / nx)
                val x1 = xMin + (xMax - xMin) * ((ix + 1).toFloat() / nx)
                surf.addQuad(
                    Vec3(x0, y, z0), normal, x0, z0, intensity,
                    Vec3(x1, y, z0), normal, x1, z0, intensity,
                    Vec3(x1, y, z1), normal, x1, z1, intensity,
                    Vec3(x0, y, z1), normal, x0, z1, intensity,
                    TYPE_LAKE_RESERVOIR
                )
            }
        }
    }

    // =========================================================================
    // 6. ARABIAN SEA COASTAL SWELLS & BREAKING SURF (CHAVAKKAD & TRIPRAYAR)
    //    `v` = distance seaward from wet shoreline in meters (0 at beach -> increases Westward!)
    // =========================================================================
    private fun buildArabianSeaCoasts(surf: FloatAccumulator) {
        val up = Vec3(0f, 1f, 0f)

        // A. Chavakkad Beach Arabian Sea (Shoreline at X = -4565.5, extending West to X = -4740, Z = 960..1200)
        addOceanCoastPatch(
            surf = surf,
            shoreX = -4565.5f,
            deepSeaX = -4740.0f,
            zMin = 960.0f,
            zMax = 1200.0f,
            y = 0.092f,
            nx = 14,
            nz = 16,
            surfIntensity = 1.0f,
            normal = up
        )
        // Chavakkad Azhi Estuary Mouth (X = -4565.5 .. -4518.5, Z = 1158.2 .. 1191.8)
        addSubdividedLakePatch(
            surf = surf,
            xMin = -4565.5f, xMax = -4518.5f,
            zMin = 1158.2f, zMax = 1191.8f,
            y = 0.095f,
            nx = 6, nz = 4,
            intensity = 0.72f,
            normal = up
        )

        // B. Triprayar-Nattika Beach Arabian Sea (Shoreline at X = -2289.5, extending West to X = -2460, Z = -2515..-2305)
        addOceanCoastPatch(
            surf = surf,
            shoreX = -2289.5f,
            deepSeaX = -2460.0f,
            zMin = -2515.0f,
            zMax = -2305.0f,
            y = 0.092f,
            nx = 14,
            nz = 14,
            surfIntensity = 1.0f,
            normal = up
        )
    }

    private fun addOceanCoastPatch(
        surf: FloatAccumulator,
        shoreX: Float,
        deepSeaX: Float,
        zMin: Float,
        zMax: Float,
        y: Float,
        nx: Int,
        nz: Int,
        surfIntensity: Float,
        normal: Vec3
    ) {
        // Non-uniform spacing perpendicular to shore: denser near breaking surf zone (0..40m from shore)!
        val xStops = FloatArray(nx + 1) { i ->
            val t = i.toFloat() / nx
            val curved = t * t * 0.65f + t * 0.35f
            shoreX + (deepSeaX - shoreX) * curved
        }

        for (iz in 0 until nz) {
            val z0 = zMin + (zMax - zMin) * (iz.toFloat() / nz)
            val z1 = zMin + (zMax - zMin) * ((iz + 1).toFloat() / nz)
            for (ix in 0 until nx) {
                val x0 = xStops[ix]
                val x1 = xStops[ix + 1]
                val v0 = shoreX - x0 // distance seaward in meters (0 at beach, increasing Westward)
                val v1 = shoreX - x1
                surf.addQuad(
                    Vec3(x0, y, z0), normal, z0, v0, surfIntensity,
                    Vec3(x1, y, z0), normal, z0, v1, surfIntensity,
                    Vec3(x1, y, z1), normal, z1, v1, surfIntensity,
                    Vec3(x0, y, z1), normal, z1, v0, surfIntensity,
                    TYPE_OCEAN_SURF
                )
            }
        }
    }

    // =========================================================================
    // 7. ZONE 3 MAJOR KERALA RIVERS (KURUMALI RIVER & 280m CHALAKUDY RIVER)
    //    Flowing steadily Westward (from Western Ghats toward Arabian Sea)
    //    with heightened wake turbulence around the concrete elliptical bridge piers!
    // =========================================================================
    private fun buildZone3MajorRivers(surf: FloatAccumulator) {
        val up = Vec3(0f, 1f, 0f)

        // A. Kurumali River (Puthukkad, underneath Kurumali River Bridge at X = 1366.0, Z = -2315..-2185)
        addFlowingRiverChannel(
            surf = surf,
            xMin = 1155.0f, xMax = 1580.0f,
            zMin = -2315.0f, zMax = -2185.0f,
            waterY = 0.145f,
            bridgeCenterX = Zone3Nh544SouthRenderer.KURUMALI_CENTER_X,
            nx = 18, nz = 8,
            baseTurb = 0.36f,
            pierWakeTurb = 0.64f,
            normal = up
        )

        // B. Chalakudy River (280m Major Water Bridge 1 at X = 2596.0, Z = -5175..-4905)
        addFlowingRiverChannel(
            surf = surf,
            xMin = 2350.0f, xMax = 2845.0f,
            zMin = -5175.0f, zMax = -4905.0f,
            waterY = 0.145f,
            bridgeCenterX = Zone3Nh544SouthRenderer.CHALAKUDY_CENTER_X,
            nx = 22, nz = 14,
            baseTurb = 0.40f,
            pierWakeTurb = 0.72f,
            normal = up
        )
    }

    private fun addFlowingRiverChannel(
        surf: FloatAccumulator,
        xMin: Float, xMax: Float,
        zMin: Float, zMax: Float,
        waterY: Float,
        bridgeCenterX: Float,
        nx: Int, nz: Int,
        baseTurb: Float,
        pierWakeTurb: Float,
        normal: Vec3
    ) {
        val midZ = (zMin + zMax) * 0.5f
        val halfSpanZ = (zMax - zMin) * 0.5f

        fun riverTurb(wx: Float, wz: Float): Float {
            // Calmer water near sandy banks, stronger current in main thalweg & foaming wake around bridge piers!
            val bankFrac = (1f - kotlin.math.abs(wz - midZ) / halfSpanZ).coerceIn(0f, 1f)
            val nearPiers = if (wx in (bridgeCenterX - 18f)..(bridgeCenterX + 12f)) 1f else 0f
            val base = baseTurb * (0.58f + 0.42f * bankFrac)
            return if (nearPiers > 0.5f) kotlin.math.max(base, pierWakeTurb * (0.72f + 0.28f * bankFrac)) else base
        }

        for (iz in 0 until nz) {
            val z0 = zMin + (zMax - zMin) * (iz.toFloat() / nz)
            val z1 = zMin + (zMax - zMin) * ((iz + 1).toFloat() / nz)
            for (ix in 0 until nx) {
                val x0 = xMin + (xMax - xMin) * (ix.toFloat() / nx)
                val x1 = xMin + (xMax - xMin) * ((ix + 1).toFloat() / nx)
                // Primary current flows Westward (-X): flowV increases from East (xMax) to West (xMin)!
                val v0 = xMax - x0
                val v1 = xMax - x1
                val u0 = z0 - zMin
                val u1 = z1 - zMin
                surf.addQuad(
                    Vec3(x0, waterY, z0), normal, u0, v0, riverTurb(x0, z0),
                    Vec3(x1, waterY, z0), normal, u0, v1, riverTurb(x1, z0),
                    Vec3(x1, waterY, z1), normal, u1, v1, riverTurb(x1, z1),
                    Vec3(x0, waterY, z1), normal, u1, v0, riverTurb(x0, z1),
                    TYPE_RIVER_PLUNGE
                )
            }
        }
    }

    // =========================================================================
    // 8. ZONE 4 WATER SYSTEMS:
    //    - Enamavu Sluice Regulator Lake Side (calm freshwater lake) & Sea Side (rippling saline tidal backwaters)
    //    - SH 75 Kole Wetlands Drainage Canals
    //    - Bharathappuzha (Nila River) Twin Meandering Shallow Channels between Golden Sandbars
    // =========================================================================
    private fun buildZone4WetlandsEnamavuAndBharathappuzha(surf: FloatAccumulator) {
        val up = Vec3(0f, 1f, 0f)

        // A. Enamavu Regulator Lake Side (North / +Z: Calm Freshwater Kole Lake below floating African Payal mats)
        run {
            val xMin = -1700.0f
            val xMax = -1590.0f
            val zMin = 119.5f
            val zMax = 335.0f
            val waterY = 0.130f
            val nx = 8
            val nz = 12
            for (iz in 0 until nz) {
                val z0 = zMin + (zMax - zMin) * (iz.toFloat() / nz)
                val z1 = zMin + (zMax - zMin) * ((iz + 1).toFloat() / nz)
                for (ix in 0 until nx) {
                    val x0 = xMin + (xMax - xMin) * (ix.toFloat() / nx)
                    val x1 = xMin + (xMax - xMin) * ((ix + 1).toFloat() / nx)
                    surf.addQuad(
                        Vec3(x0, waterY, z0), up, x0 * 0.08f, z0 * 0.08f, 0.22f,
                        Vec3(x1, waterY, z0), up, x1 * 0.08f, z0 * 0.08f, 0.22f,
                        Vec3(x1, waterY, z1), up, x1 * 0.08f, z1 * 0.08f, 0.22f,
                        Vec3(x0, waterY, z1), up, x0 * 0.08f, z1 * 0.08f, 0.22f,
                        TYPE_LAKE_RESERVOIR
                    )
                }
            }
        }

        // B. Enamavu Regulator Sea Side (South / -Z: Foaming 18-Gate Sluice Discharge -> Rippling Saline Backwaters!)
        run {
            val xMin = -1700.0f
            val xMax = -1590.0f
            val zMin = -195.0f
            val zMax = 119.5f
            val waterY = 0.125f
            val nx = 10
            val nz = 14
            for (iz in 0 until nz) {
                val z0 = zMin + (zMax - zMin) * (iz.toFloat() / nz)
                val z1 = zMin + (zMax - zMin) * ((iz + 1).toFloat() / nz)
                for (ix in 0 until nx) {
                    val x0 = xMin + (xMax - xMin) * (ix.toFloat() / nx)
                    val x1 = xMin + (xMax - xMin) * ((ix + 1).toFloat() / nx)
                    // Primary outflow flows Southward (-Z) through the 18 sluice gates toward the Arabian Sea!
                    val v0 = zMax - z0
                    val v1 = zMax - z1
                    val u0 = x0 - xMin
                    val u1 = x1 - xMin
                    val t0 = if (z0 > 96f) 0.78f else 0.42f
                    val t1 = if (z1 > 96f) 0.78f else 0.42f
                    surf.addQuad(
                        Vec3(x0, waterY, z0), up, u0, v0, t0,
                        Vec3(x1, waterY, z0), up, u1, v0, t0,
                        Vec3(x1, waterY, z1), up, u1, v1, t1,
                        Vec3(x0, waterY, z1), up, u0, v1, t1,
                        TYPE_RIVER_PLUNGE
                    )
                }
            }
        }

        // C. SH 75 Kole Wetlands Flanking Drainage Canals (East of Enamavu Regulator)
        for (canalZ in arrayOf(Pair(152.5f, 163.5f), Pair(86.5f, 98.5f))) {
            val xMin = -1560.0f
            val xMax = -1145.0f
            val nx = 10
            for (ix in 0 until nx) {
                val x0 = xMin + (xMax - xMin) * (ix.toFloat() / nx)
                val x1 = xMin + (xMax - xMin) * ((ix + 1).toFloat() / nx)
                surf.addQuad(
                    Vec3(x0, 0.115f, canalZ.first), up, x0 * 0.08f, canalZ.first * 0.08f, 0.18f,
                    Vec3(x1, 0.115f, canalZ.first), up, x1 * 0.08f, canalZ.first * 0.08f, 0.18f,
                    Vec3(x1, 0.115f, canalZ.second), up, x1 * 0.08f, canalZ.second * 0.08f, 0.18f,
                    Vec3(x0, 0.115f, canalZ.second), up, x0 * 0.08f, canalZ.second * 0.08f, 0.18f,
                    TYPE_LAKE_RESERVOIR
                )
            }
        }

        // D. Bharathappuzha (Nila River) Twin Meandering Shallow Channels between Golden Sandbars
        //    Flowing Westward under both the Modern SH 22 Bridge (X = 100) and Historic 1902 Cochin Bridge (X = 123.5)!
        addFlowingRiverChannel(
            surf = surf,
            xMin = 50.0f, xMax = 328.0f,
            zMin = 2723.0f, zMax = 2763.0f,
            waterY = 0.125f,
            bridgeCenterX = Zone4KoleRailHeritageRenderer.BHARATHAPPUZHA_CENTER_X,
            nx = 16, nz = 6,
            baseTurb = 0.34f,
            pierWakeTurb = 0.62f,
            normal = up
        )
        addFlowingRiverChannel(
            surf = surf,
            xMin = 50.0f, xMax = 328.0f,
            zMin = 2817.0f, zMax = 2855.0f,
            waterY = 0.125f,
            bridgeCenterX = Zone4KoleRailHeritageRenderer.COCHIN_1902_CENTER_X,
            nx = 16, nz = 6,
            baseTurb = 0.32f,
            pierWakeTurb = 0.58f,
            normal = up
        )
    }

    // =========================================================================
    // 9. ZONE 5 COASTAL BACKWATERS & ESTUARIES (NH 66 CORRIDOR):
    //    - Chettuva Saltwater Backwaters Estuary (350m bridge span, mangrove islands & Chinese nets)
    //    - Triprayar Canoli Canal (flowing cleanly under Canoli Bridge past Sree Rama Temple Water Ghat)
    //    - Kottapuram / Kodungallur National Waterway 3 (NW-3) / Periyar Lagoon (cargo barge & ferry waterway)
    // =========================================================================
    private fun buildZone5CoastalBackwatersAndLagoon(surf: FloatAccumulator) {
        val up = Vec3(0f, 1f, 0f)

        // A. Chettuva Saltwater Backwaters Estuary (X = -3960 .. -3360, Z = 100 .. 330)
        //    Estuarine tidal current flows Westward toward the Arabian Sea under Chettuva Bridge (X = -3664)!
        addFlowingRiverChannel(
            surf = surf,
            xMin = -3960.0f, xMax = -3360.0f,
            zMin = 100.0f, zMax = 330.0f,
            waterY = 0.128f,
            bridgeCenterX = Zone5Nh66CoastalRenderer.CHETTUVA_CENTER_X,
            nx = 24, nz = 12,
            baseTurb = 0.34f,
            pierWakeTurb = 0.68f,
            normal = up
        )

        // B. Triprayar Canoli Canal (X = -2480 .. -1960, Z = -2700 .. -2620)
        //    Calm emerald inland navigation canal flowing beneath Triprayar Canoli Bridge past Meenootu Ghat!
        addFlowingRiverChannel(
            surf = surf,
            xMin = -2480.0f, xMax = -1960.0f,
            zMin = -2700.0f, zMax = -2620.0f,
            waterY = 0.126f,
            bridgeCenterX = Zone5Nh66CoastalRenderer.CANOLI_CENTER_X,
            nx = 18, nz = 6,
            baseTurb = 0.24f,
            pierWakeTurb = 0.48f,
            normal = up
        )

        // C. Kottapuram / Kodungallur NW-3 / Periyar Lagoon (X = -1940 .. -1100, Z = -4210 .. -3890)
        //    Broad sunlit navigable backwater lagoon flowing Westward toward the Munambam/Azhikode bar mouth!
        addFlowingRiverChannel(
            surf = surf,
            xMin = -1940.0f, xMax = -1100.0f,
            zMin = -4210.0f, zMax = -3890.0f,
            waterY = 0.130f,
            bridgeCenterX = Zone5Nh66CoastalRenderer.KOTTAPURAM_CENTER_X,
            nx = 26, nz = 14,
            baseTurb = 0.36f,
            pierWakeTurb = 0.72f,
            normal = up
        )
    }
}
