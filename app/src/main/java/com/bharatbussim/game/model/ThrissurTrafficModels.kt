package com.bharatbussim.game.model

import android.content.Context
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Canonical catalog and binary GPU mesh loader (`TVM1`) for the 13 Google Drive `.glb`
 * Indian/Kerala traffic vehicle models compiled into `thrissur_traffic_vehicles.bin`
 * and `thrissur_traffic_atlas.png`.
 */
object TrafficVehicleCatalog {
    const val MODEL_AUTO = 0
    const val MODEL_CAR1 = 1
    const val MODEL_CAR2 = 2
    const val MODEL_OMINI = 3
    const val MODEL_KSRTC_RED = 4
    const val MODEL_KSRTC_BLUE = 5
    const val MODEL_PRIVATE_BUS_1 = 6
    const val MODEL_PRIVATE_BUS_2 = 7
    const val MODEL_PRIVATE_BUS_3 = 8
    const val MODEL_TRUCK1 = 9
    const val MODEL_TRUCK2 = 10
    const val MODEL_AMBULANCE = 11
    const val MODEL_POLICE = 12
    const val MODEL_COUNT = 13

    data class VehicleSpec(
        val modelIndex: Int,
        val displayName: String,
        val defaultHalfX: Float,
        val defaultHalfZ: Float,
        val citySpeedMps: Float,
        val highwaySpeedMps: Float,
        val accelMps2: Float,
        val decelMps2: Float,
        val hasEmergencyBeacon: Boolean = false
    )

    val specs: Array<VehicleSpec> = arrayOf(
        VehicleSpec(MODEL_AUTO, "Kerala Auto Rickshaw", 0.78f, 1.37f, 9.5f, 12.2f, 2.5f, 6.2f),
        VehicleSpec(MODEL_CAR1, "Indian White SUV", 1.10f, 2.50f, 12.8f, 19.5f, 3.2f, 7.8f),
        VehicleSpec(MODEL_CAR2, "Gran Coupe", 1.13f, 2.58f, 13.5f, 21.5f, 3.6f, 8.4f),
        VehicleSpec(MODEL_OMINI, "Omni Compact Van", 0.82f, 1.76f, 11.0f, 15.6f, 2.7f, 6.8f),
        VehicleSpec(MODEL_KSRTC_RED, "KSRTC Fast Passenger", 1.48f, 5.84f, 10.6f, 16.5f, 1.8f, 5.4f),
        VehicleSpec(MODEL_KSRTC_BLUE, "KSRTC Super Fast", 1.48f, 5.84f, 11.2f, 17.4f, 1.9f, 5.5f),
        VehicleSpec(MODEL_PRIVATE_BUS_1, "Private Bus (Pink)", 1.54f, 5.92f, 11.4f, 17.0f, 2.0f, 5.6f),
        VehicleSpec(MODEL_PRIVATE_BUS_2, "Private Bus (Blue)", 1.54f, 5.92f, 11.4f, 17.0f, 2.0f, 5.6f),
        VehicleSpec(MODEL_PRIVATE_BUS_3, "Private Bus (Green)", 1.54f, 5.92f, 11.4f, 17.0f, 2.0f, 5.6f),
        VehicleSpec(MODEL_TRUCK1, "Indian Goods Lorry", 1.44f, 4.20f, 9.2f, 13.8f, 1.5f, 4.8f),
        VehicleSpec(MODEL_TRUCK2, "Cargo Bonnet Lorry", 1.40f, 3.84f, 8.8f, 13.2f, 1.45f, 4.7f),
        VehicleSpec(MODEL_AMBULANCE, "108 Ambulance", 1.18f, 2.82f, 13.8f, 20.5f, 3.4f, 8.0f, hasEmergencyBeacon = true),
        VehicleSpec(MODEL_POLICE, "Kerala Police SUV", 1.04f, 2.26f, 13.2f, 19.8f, 3.3f, 7.9f, hasEmergencyBeacon = true)
    )

    // Weighted Kerala vehicle mix tables (city streets vs national highways vs toll booths vs SH 69)
    val cityPool = intArrayOf(
        MODEL_AUTO, MODEL_AUTO, MODEL_AUTO, MODEL_AUTO,
        MODEL_CAR1, MODEL_CAR1, MODEL_CAR2,
        MODEL_OMINI, MODEL_OMINI,
        MODEL_PRIVATE_BUS_1, MODEL_PRIVATE_BUS_2, MODEL_PRIVATE_BUS_3,
        MODEL_KSRTC_RED, MODEL_KSRTC_BLUE,
        MODEL_TRUCK1, MODEL_AMBULANCE, MODEL_POLICE
    )

    val highwayPool = intArrayOf(
        MODEL_CAR1, MODEL_CAR1, MODEL_CAR1,
        MODEL_CAR2, MODEL_CAR2,
        MODEL_OMINI, MODEL_OMINI,
        MODEL_TRUCK1, MODEL_TRUCK1, MODEL_TRUCK2, MODEL_TRUCK2,
        MODEL_KSRTC_RED, MODEL_KSRTC_BLUE,
        MODEL_PRIVATE_BUS_1, MODEL_PRIVATE_BUS_2, MODEL_PRIVATE_BUS_3,
        MODEL_AMBULANCE, MODEL_POLICE
    )

    val tollPool = intArrayOf(
        MODEL_CAR1, MODEL_CAR1, MODEL_CAR1,
        MODEL_CAR2, MODEL_CAR2,
        MODEL_OMINI, MODEL_OMINI,
        MODEL_TRUCK1, MODEL_TRUCK1, MODEL_TRUCK2, MODEL_TRUCK2,
        MODEL_KSRTC_RED, MODEL_KSRTC_BLUE,
        MODEL_PRIVATE_BUS_1, MODEL_PRIVATE_BUS_2, MODEL_PRIVATE_BUS_3,
        MODEL_AMBULANCE, MODEL_POLICE
    )

    val sh69Pool = intArrayOf(
        MODEL_AUTO, MODEL_AUTO, MODEL_AUTO,
        MODEL_CAR1, MODEL_CAR1, MODEL_CAR2,
        MODEL_OMINI, MODEL_OMINI,
        MODEL_PRIVATE_BUS_1, MODEL_PRIVATE_BUS_2, MODEL_PRIVATE_BUS_3,
        MODEL_KSRTC_RED, MODEL_KSRTC_BLUE,
        MODEL_TRUCK1, MODEL_TRUCK2,
        MODEL_AMBULANCE, MODEL_POLICE
    )

    fun specFor(modelIndex: Int): VehicleSpec = specs[modelIndex.coerceIn(0, specs.lastIndex)]
}

class TrafficGpuModel(
    val modelIndex: Int,
    val name: String,
    val halfX: Float,
    val halfZ: Float,
    val heightY: Float,
    val gpuVertexFloats: FloatArray, // 9 floats/vert: px, py, pz, nx, ny, nz, u, v, packedMat (texLayer*8 + matFlag)
    val gpuIndicesShorts: ShortArray
)

/**
 * Pooled mutable pose snapshot for zero-allocation 60 Hz rendering and dynamic bus OBB collision.
 * Convention: `headingRad = atan2(dirX, dirZ)` (identical to player bus `busHeadingRad`).
 */
class TrafficRenderInstance(
    var modelIndex: Int = 0,
    var cx: Float = 0f,
    var baseY: Float = 0.06f,
    var cz: Float = 0f,
    var headingRad: Float = 0f,
    var pitchRad: Float = 0f,
    var brakeGlow: Float = 0f,
    var sirenPhase: Float = 0f,
    var halfX: Float = 1.0f,
    var halfZ: Float = 2.2f,
    var speedMps: Float = 0f,
    var label: String = "Traffic Vehicle",
    var isQueued: Boolean = false
)

/**
 * Telemetry snapshot produced every physics tick for HUD status banners and independent per-lane
 * toll plaza boom barrier animation.
 */
data class TrafficBlockTelemetry(
    val paliyekkaraLaneOpenFracs: FloatArray = FloatArray(12),
    val nattikaLaneOpenFracs: FloatArray = FloatArray(10),
    val paliyekkaraQueuedCount: Int = 0,
    val nattikaQueuedCount: Int = 0,
    val muthuvaraQueuedCount: Int = 0,
    val activeBlockBanner: String? = null
)

object TrafficMeshLoader {
    private const val ASSET_BIN = "thrissur_traffic_vehicles.bin"
    @Volatile
    private var cachedModels: List<TrafficGpuModel>? = null
    private val loadLock = Any()

    fun getLoadedModelsOrEmpty(): List<TrafficGpuModel> = cachedModels ?: emptyList()

    fun ensureLoaded(context: Context): List<TrafficGpuModel> {
        cachedModels?.let { return it }
        synchronized(loadLock) {
            cachedModels?.let { return it }
            val loaded = parseBinary(context)
            cachedModels = loaded
            return loaded
        }
    }

    private fun parseBinary(context: Context): List<TrafficGpuModel> {
        return try {
            context.assets.open(ASSET_BIN).use { raw ->
                val dis = DataInputStream(BufferedInputStream(raw, 65536))
                val header = ByteArray(8)
                dis.readFully(header)
                if (header[0] != 'T'.code.toByte() ||
                    header[1] != 'V'.code.toByte() ||
                    header[2] != 'M'.code.toByte() ||
                    header[3] != '1'.code.toByte()
                ) {
                    return emptyList()
                }
                val hBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                hBuf.position(4)
                val version = hBuf.short.toInt() and 0xFFFF
                if (version != 1) return emptyList()
                val modelCount = hBuf.short.toInt() and 0xFFFF

                val result = ArrayList<TrafficGpuModel>(modelCount)
                val shortBuf = ByteArray(2)
                val metaBytes = ByteArray(20)

                for (mIdx in 0 until modelCount) {
                    dis.readFully(shortBuf)
                    val nameLen = (shortBuf[0].toInt() and 0xFF) or ((shortBuf[1].toInt() and 0xFF) shl 8)
                    val nameBytes = ByteArray(nameLen)
                    dis.readFully(nameBytes)
                    val name = String(nameBytes, Charsets.UTF_8)

                    dis.readFully(metaBytes)
                    val mb = ByteBuffer.wrap(metaBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val halfX = mb.float
                    val halfZ = mb.float
                    val heightY = mb.float
                    val vertCount = mb.int
                    val indexCount = mb.int

                    val vBytes = ByteArray(vertCount * 16)
                    dis.readFully(vBytes)
                    val vb = ByteBuffer.wrap(vBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val gpuVerts = FloatArray(vertCount * 9)
                    var outPtr = 0
                    for (v in 0 until vertCount) {
                        val px = vb.short.toFloat() * 0.001f
                        val py = vb.short.toFloat() * 0.001f
                        val pz = vb.short.toFloat() * 0.001f
                        val nx = vb.get().toFloat() / 127.0f
                        val ny = vb.get().toFloat() / 127.0f
                        val nz = vb.get().toFloat() / 127.0f
                        vb.get() // pad
                        val u = vb.short.toFloat() / 4096.0f
                        val tv = vb.short.toFloat() / 4096.0f
                        val texLayer = vb.get().toInt() and 0xFF
                        val matFlag = vb.get().toInt() and 0x07

                        gpuVerts[outPtr++] = px
                        gpuVerts[outPtr++] = py
                        gpuVerts[outPtr++] = pz
                        gpuVerts[outPtr++] = nx
                        gpuVerts[outPtr++] = ny
                        gpuVerts[outPtr++] = nz
                        gpuVerts[outPtr++] = u
                        gpuVerts[outPtr++] = tv
                        gpuVerts[outPtr++] = (texLayer * 8 + matFlag).toFloat()
                    }

                    val iBytes = ByteArray(indexCount * 2)
                    dis.readFully(iBytes)
                    val ib = ByteBuffer.wrap(iBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val gpuIdx = ShortArray(indexCount)
                    ib.asShortBuffer().get(gpuIdx)

                    result.add(
                        TrafficGpuModel(
                            modelIndex = mIdx,
                            name = name,
                            halfX = halfX,
                            halfZ = halfZ,
                            heightY = heightY,
                            gpuVertexFloats = gpuVerts,
                            gpuIndicesShorts = gpuIdx
                        )
                    )
                }
                result
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
