package com.bharatbussim.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.ui.graphics.Color
import com.bharatbussim.game.model.CameraViewMode
import com.bharatbussim.game.model.HeadlightBeamMode
import com.bharatbussim.game.model.ThrissurOsmRoad
import com.bharatbussim.game.model.ThrissurTrafficSystem
import com.bharatbussim.game.model.TrafficMeshLoader
import com.bharatbussim.game.model.TrafficSignalAspect
import com.bharatbussim.game.model.TrafficVehicleCatalog
import com.bharatbussim.game.model.TransmissionGear
import com.bharatbussim.game.model.Vec3
import com.bharatbussim.game.ui.theme.PremiumFonts
import com.bharatbussim.game.viewmodel.SimUiState
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Hardware-accelerated OpenGL ES 3.0 3D World Renderer for Bharat Bus Simulator.
 *
 * Features:
 * - 24-bit hardware Z-buffer (`GL_DEPTH_TEST`, `GL_LEQUAL`) eliminating painter's-algorithm artifacts.
 * - Camera-relative world coordinates (`worldPos - uCamEye`) preventing floating-point jitter across Kerala.
 * - Static GPU VBOs for all Thrissur roads, shoulders, bellmouths, road markings, Sakthan 20-bay terminal,
 *   and Zone 1–5 landmarks.
 * - Hardware GPU Instancing (`GLES30.glDrawElementsInstanced`) for all 24 Zone 1 Google Drive 3D Building,
 *   Tree, and Grass models with baked glTF/FBX transforms, full per-vertex UV coordinates, 27-layer RGBA
 *   `GL_TEXTURE_2D_ARRAY` atlas (`zone1_gdrive_atlas.png`), and alpha-cutout foliage (`discard`).
 * - Real-time 3D Hino RM2 bus rendering with animated wheels, steering column, pneumatic plug door,
 *   automotive clearcoat reflections, Fresnel solar glass, and active lighting.
 */
internal data class PrebuiltStaticWorldData(
    val roadCount: Int,
    val roadFloats: FloatArray,
    val landmarkFloats: FloatArray,
    val landmarkFloatCount: Int,
    val signboardFaces: List<QuadFace3D>
)

internal object ThrissurPrebuiltWorldCache {
    @Volatile
    var cachedData: PrebuiltStaticWorldData? = null

    fun buildIfNeeded(roads: List<ThrissurOsmRoad>): PrebuiltStaticWorldData {
        val existing = cachedData
        if (existing != null && existing.roadCount == roads.size) return existing

        val roadFloats = captureStaticRoadAndGroundTriangles(roads)
        val allFaces = ArrayList<QuadFace3D>(16000)
        collectAllSakthanTerminalStaticFaces(allFaces)
        collectAllStaticLandmarkAndSignFaces(roads, allFaces)

        val signFaces = ArrayList<QuadFace3D>(128)
        for (i in 0 until allFaces.size) {
            val f = allFaces[i]
            if (f.signboard != null) {
                signFaces.add(f)
            }
        }

        // Subdivide large walls, roofs, compound walls, and concourse slabs into <= 2.4m panels so
        // no triangle ever spans dozens of meters across the camera near plane on any mobile GPU/emulator!
        var landmarkFloats = FloatArray((allFaces.size - signFaces.size).coerceAtLeast(1024) * 6 * 8 * 2)
        var cursor = 0

        fun ensureCap(extra: Int) {
            if (cursor + extra > landmarkFloats.size) {
                landmarkFloats = landmarkFloats.copyOf(max(landmarkFloats.size * 2, cursor + extra + 65536))
            }
        }

        fun dist3(a: Vec3, b: Vec3): Float {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        fun emitQuadRaw(
            p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3,
            r: Float, g: Float, b: Float, a: Float, biasOffset: Float
        ) {
            ensureCap(48)
            var c = cursor
            landmarkFloats[c++] = p0.x; landmarkFloats[c++] = p0.y; landmarkFloats[c++] = p0.z; landmarkFloats[c++] = r; landmarkFloats[c++] = g; landmarkFloats[c++] = b; landmarkFloats[c++] = a; landmarkFloats[c++] = biasOffset
            landmarkFloats[c++] = p1.x; landmarkFloats[c++] = p1.y; landmarkFloats[c++] = p1.z; landmarkFloats[c++] = r; landmarkFloats[c++] = g; landmarkFloats[c++] = b; landmarkFloats[c++] = a; landmarkFloats[c++] = biasOffset
            landmarkFloats[c++] = p2.x; landmarkFloats[c++] = p2.y; landmarkFloats[c++] = p2.z; landmarkFloats[c++] = r; landmarkFloats[c++] = g; landmarkFloats[c++] = b; landmarkFloats[c++] = a; landmarkFloats[c++] = biasOffset
            landmarkFloats[c++] = p0.x; landmarkFloats[c++] = p0.y; landmarkFloats[c++] = p0.z; landmarkFloats[c++] = r; landmarkFloats[c++] = g; landmarkFloats[c++] = b; landmarkFloats[c++] = a; landmarkFloats[c++] = biasOffset
            landmarkFloats[c++] = p2.x; landmarkFloats[c++] = p2.y; landmarkFloats[c++] = p2.z; landmarkFloats[c++] = r; landmarkFloats[c++] = g; landmarkFloats[c++] = b; landmarkFloats[c++] = a; landmarkFloats[c++] = biasOffset
            landmarkFloats[c++] = p3.x; landmarkFloats[c++] = p3.y; landmarkFloats[c++] = p3.z; landmarkFloats[c++] = r; landmarkFloats[c++] = g; landmarkFloats[c++] = b; landmarkFloats[c++] = a; landmarkFloats[c++] = biasOffset
            cursor = c
        }

        val maxWallEdgeM = 16.0f
        for (i in 0 until allFaces.size) {
            val f = allFaces[i]
            if (f.signboard != null) continue
            val col = f.color
            val r = col.red; val g = col.green; val b = col.blue; val a = col.alpha
            val biasOffset = (f.biasDist * 0.008f).coerceIn(-0.06f, 0.06f)
            val v0 = f.v0; val v1 = f.v1; val v2 = f.v2; val v3 = f.v3

            val lenU = max(dist3(v0, v1), dist3(v3, v2))
            val lenV = max(dist3(v0, v3), dist3(v1, v2))
            if (lenU <= 18.0f && lenV <= 18.0f) {
                emitQuadRaw(v0, v1, v2, v3, r, g, b, a, biasOffset)
            } else {
                val stepsU = kotlin.math.ceil(lenU / maxWallEdgeM).toInt().coerceIn(1, 6)
                val stepsV = kotlin.math.ceil(lenV / maxWallEdgeM).toInt().coerceIn(1, 6)
                val invU = 1f / stepsU.toFloat()
                val invV = 1f / stepsV.toFloat()
                fun bilerp(u: Float, v: Float): Vec3 {
                    val iu = 1f - u; val iv = 1f - v
                    val w0 = iu * iv; val w1 = u * iv; val w2 = u * v; val w3 = iu * v
                    return Vec3(
                        v0.x * w0 + v1.x * w1 + v2.x * w2 + v3.x * w3,
                        v0.y * w0 + v1.y * w1 + v2.y * w2 + v3.y * w3,
                        v0.z * w0 + v1.z * w1 + v2.z * w2 + v3.z * w3
                    )
                }
                for (iv in 0 until stepsV) {
                    val tv0 = iv * invV; val tv1 = (iv + 1) * invV
                    for (iu in 0 until stepsU) {
                        val tu0 = iu * invU; val tu1 = (iu + 1) * invU
                        emitQuadRaw(
                            bilerp(tu0, tv0),
                            bilerp(tu1, tv0),
                            bilerp(tu1, tv1),
                            bilerp(tu0, tv1),
                            r, g, b, a, biasOffset
                        )
                    }
                }
            }
        }

        val built = PrebuiltStaticWorldData(
            roadCount = roads.size,
            roadFloats = roadFloats,
            landmarkFloats = landmarkFloats,
            landmarkFloatCount = cursor,
            signboardFaces = signFaces
        )
        cachedData = built
        return built
    }
}

internal class ThrissurGlWorldRenderer(
    private val context: Context
) : GLSurfaceView.Renderer {

    init {
        PremiumFonts.init(context)
    }

    @Volatile
    var latestState: SimUiState = SimUiState()

    @Volatile
    var stateProvider: (() -> SimUiState)? = null

    @Volatile
    var hinoMesh: List<HinoRealMeshPoly> = emptyList()

    @Volatile
    var onWorldGpuReady: (() -> Unit)? = null

    @Volatile
    private var notifiedGpuReady = false

    private var viewportW: Int = 1
    private var viewportH: Int = 1

    // Shader programs
    private var skyProg = 0
    private var worldColorProg = 0
    private var instancedGDriveProg = 0
    private var fuelStationProg = 0
    private var signboardProg = 0
    private var waterProg = 0
    private var hinoBusProg = 0
    private var trafficVehicleProg = 0
    private var fuelWorkerProg = 0

    // Cached uniform locations (zero glGetUniformLocation calls inside onDrawFrame!)
    private class CamUniformLocs(
        var eye: Int = -1,
        var right: Int = -1,
        var up: Int = -1,
        var fwd: Int = -1,
        var proj: Int = -1
    )
    private val worldColorCamLocs = CamUniformLocs()
    private val instancedCamLocs = CamUniformLocs()
    private val fuelStationCamLocs = CamUniformLocs()
    private val signboardCamLocs = CamUniformLocs()
    private val waterCamLocs = CamUniformLocs()
    private val hinoBusCamLocs = CamUniformLocs()
    private val trafficCamLocs = CamUniformLocs()
    private val fuelWorkerCamLocs = CamUniformLocs()

    private var uSkyHorizonFracLoc = -1
    private var uInstTexArrayLoc = -1
    private var uFuelBaseTexLoc = -1
    private var uFuelWideTexLoc = -1
    private var uFuelTallTexLoc = -1
    private var uTrafficTexArrayLoc = -1
    private var uSignTexLoc = -1
    private var uSignBiasLoc = -1
    private var uWaterTimeLoc = -1
    private var uWorkerTexArrayLoc = -1
    private var uWorkerWorldPosLoc = -1
    private var uWorkerYawCSLoc = -1
    private var uWorkerBoneMatsLoc = -1
    private var maxSupportedAnisotropy = 1.0f

    // Cached Hino RM2 GPU Bus Shader uniform locations
    private var uBusPosLoc = -1
    private var uBusYawCSLoc = -1
    private var uBusPitchRollLoc = -1
    private var uWheelSpinSteerLoc = -1
    private var uDoorTransformLoc = -1
    private var uDoorOpenActiveLoc = -1
    private var uSteerWheelCSLoc = -1
    private var uLightFlagsLoc = -1
    private var uLightFlags2Loc = -1

    // Spatial chunk descriptor for O(1) distance + camera frustum culling inside a single static VBO
    private class SpatialDrawChunk(
        val firstVertex: Int,
        val vertexCount: Int,
        val centerX: Float,
        val centerZ: Float,
        val radius: Float
    )

    // Sky quad VBO
    private var skyVbo = 0

    // Static Road + Ground Plaza VBO (8 floats/vertex: x, y, z, r, g, b, a, biasOffset)
    private var roadVbo = 0
    private var roadVertexCount = 0
    private var roadChunks: List<SpatialDrawChunk> = emptyList()
    private var builtRoadCount = -1

    // Static Landmark VBO (8 floats/vertex: x, y, z, r, g, b, a, biasOffset)
    private var landmarkVbo = 0
    private var landmarkVertexCount = 0
    private var landmarkChunks: List<SpatialDrawChunk> = emptyList()
    private var builtLandmarksForRoads = -1

    // Static GPU Animated Flowing Water VBOs (10 floats/vertex: x, y, z, nx, ny, nz, u, v, waterType, intensity)
    private var waterSurfaceVbo = 0
    private var waterSurfaceVertexCount = 0
    private var waterSurfaceChunks: List<SpatialDrawChunk> = emptyList()
    private var waterMistVbo = 0
    private var waterMistVertexCount = 0
    private var waterMistChunks: List<SpatialDrawChunk> = emptyList()

    // Static GPU Hino RM2 3D Bus VBOs (13 floats/vertex: uploaded ONCE — zero per-frame CPU shading!)
    private var busStaticOpaqueVbo = 0
    private var busStaticOpaqueVertCount = 0
    private var busStaticGlassVbo = 0
    private var busStaticGlassVertCount = 0
    private var builtBusMeshSize = -1

    // Cached 3D Signboards extracted from Sakthan + Landmarks
    private class GpuSignboardEntry(
        val v0: Vec3,
        val v1: Vec3,
        val v2: Vec3,
        val v3: Vec3,
        val cx: Float,
        val cy: Float,
        val cz: Float,
        val nx: Float,
        val ny: Float,
        val nz: Float,
        val biasDist: Float,
        val spec: SignboardQuadSpec
    )
    private val signboardEntries = ArrayList<GpuSignboardEntry>(128)
    private val signboardTexIds = HashMap<String, Int>(160)
    private var signboardDynamicVbo = 0
    private val signboardQuadFloats = FloatArray(12 * 5) // Up to 4 clipped triangles (6-vertex polygon fan) * (x, y, z, u, v)
    private val signboardQuadBuf: FloatBuffer = ByteBuffer
        .allocateDirect(signboardQuadFloats.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val signClipInX = FloatArray(8)
    private val signClipInY = FloatArray(8)
    private val signClipInZ = FloatArray(8)
    private val signClipInU = FloatArray(8)
    private val signClipInV = FloatArray(8)
    private val signClipInCz = FloatArray(8)
    private val signClipOutX = FloatArray(8)
    private val signClipOutY = FloatArray(8)
    private val signClipOutZ = FloatArray(8)
    private val signClipOutU = FloatArray(8)
    private val signClipOutV = FloatArray(8)

    // Dynamic small VBO for Manikandanal active traffic signal lenses, animated barriers & bus shadow/headlamp cones
    private var dynamicColorVbo = 0
    private val busShadowFloats = FloatArray(16 * 6 * 8)
    private val busShadowDirectBuf: FloatBuffer = ByteBuffer
        .allocateDirect(busShadowFloats.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // Zone 1–3 Google Drive GPU Instanced Meshes with spatial chunk culling
    private class GpuInstancedModel(
        val modelIndex: Int,
        val vboId: Int,
        val iboId: Int,
        val instanceVboId: Int,
        val indexCount: Int,
        val instanceCount: Int,
        val maxDrawDist: Float = 360f,
        val chunks: List<SpatialDrawChunk> = emptyList()
    )
    private val gpuGDriveModels = ArrayList<GpuInstancedModel>(32)
    private var gdriveAtlasTexArrayId = 0
    private var builtGDriveModels = false

    // Thrissur District Highway Fuel Stations & Roadside Price Signboards (Google Drive 3D GLB)
    // 3-tier aspect-matched texture arrays: Base materials (512x512), Wide boards (2048x256), Tall hero boards (1024x2048)
    private val gpuFuelStationModels = ArrayList<GpuInstancedModel>(2)
    private var fuelStationAtlasTexArrayId = 0
    private var fuelStationWideSignArrayId = 0
    private var fuelStationTallSignArrayId = 0
    private var builtFuelStationModels = false

    // Animated Rigged 3D Fuel Station Attendants (Google Drive FBX — Petrol Worker 1 & Diesel Worker 2)
    private class GpuSkinnedWorkerModel(
        val workerIndex: Int,
        val vboId: Int,
        val iboId: Int,
        val indexCount: Int,
        val model: FuelStationWorkerAnimator.LoadedFuelWorkerModel
    )
    private val gpuFuelWorkerModels = arrayOfNulls<GpuSkinnedWorkerModel>(2)
    private var fuelWorkerAtlasTexArrayId = 0
    private var builtFuelWorkerModels = false
    private val workerSkinMatsScratch = FloatArray(FuelStationWorkerAnimator.BONE_COUNT * 16)
    private val workerGripFrameLocal = FloatArray(9)
    private var lastWorkerAnimNanos = 0L
    private val hoseDynamicFloats = FloatArray(6144 * 8)
    private val hoseDynamicBuf: FloatBuffer = ByteBuffer
        .allocateDirect(hoseDynamicFloats.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private var pumpMeterTexId = 0
    private var pumpMeterBitmap: Bitmap? = null
    private var pumpMeterCanvas: Canvas? = null
    private var lastPumpMeterKey = ""
    private var fuelPriceSignTexId = 0

    // Thrissur - Wadakkanchery - Shoranur 2-Track Railway & Animated 7-Coach Trains GPU Instancing
    private var shoranurRailwayAtlasTexArrayId = 0
    private var gpuRailwayStaticTrack: GpuInstancedModel? = null
    private val gpuRailwayDynamicModels = HashMap<Int, GpuInstancedModel>(4)
    private var builtRailwayModels = false
    private val trainInstancesScratch = ArrayList<ShoranurRailwaySystem.RailwayInstance>(24)
    private val trainInstFloatsScratch = FloatArray(48 * 6)
    private val trainInstBuf: FloatBuffer = ByteBuffer
        .allocateDirect(trainInstFloatsScratch.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    // 13 Google Drive Real .glb Traffic Vehicles Hardware GPU Instancing (zero-allocation per frame)
    private var trafficAtlasTexArrayId = 0
    private val gpuTrafficModels = arrayOfNulls<GpuInstancedModel>(TrafficVehicleCatalog.MODEL_COUNT)
    private var builtTrafficModels = false
    private val trafficBucketIndices = Array(TrafficVehicleCatalog.MODEL_COUNT) { IntArray(72) }
    private val trafficBucketCounts = IntArray(TrafficVehicleCatalog.MODEL_COUNT)
    private val trafficInstFloatsScratch = FloatArray(72 * 7)
    private val trafficInstBuf: FloatBuffer = ByteBuffer
        .allocateDirect(trafficInstFloatsScratch.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val trafficShadowFloats = FloatArray(96 * 6 * 8)
    private val trafficShadowBuf: FloatBuffer = ByteBuffer
        .allocateDirect(trafficShadowFloats.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.17f, 0.36f, 0.55f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        skyProg = createProgram(SKY_VS, SKY_FS)
        worldColorProg = createProgram(WORLD_COLOR_VS, WORLD_COLOR_FS)
        instancedGDriveProg = createProgram(GDRIVE_INSTANCED_VS, GDRIVE_INSTANCED_FS)
        fuelStationProg = createProgram(GDRIVE_INSTANCED_VS, FUEL_STATION_FS)
        signboardProg = createProgram(SIGNBOARD_VS, SIGNBOARD_FS)
        waterProg = createProgram(WATER_VS, WATER_FS)
        hinoBusProg = createProgram(HINO_BUS_VS, HINO_BUS_FS)
        trafficVehicleProg = createProgram(TRAFFIC_VEHICLE_VS, TRAFFIC_VEHICLE_FS)
        fuelWorkerProg = createProgram(FUEL_WORKER_VS, FUEL_WORKER_FS)

        maxSupportedAnisotropy = queryMaxAnisotropy()

        // Cache ALL shader uniform locations once (zero driver lookup overhead per frame!)
        cacheCamUniformLocs(worldColorProg, worldColorCamLocs)
        cacheCamUniformLocs(instancedGDriveProg, instancedCamLocs)
        cacheCamUniformLocs(fuelStationProg, fuelStationCamLocs)
        cacheCamUniformLocs(signboardProg, signboardCamLocs)
        cacheCamUniformLocs(waterProg, waterCamLocs)
        cacheCamUniformLocs(hinoBusProg, hinoBusCamLocs)
        cacheCamUniformLocs(trafficVehicleProg, trafficCamLocs)
        cacheCamUniformLocs(fuelWorkerProg, fuelWorkerCamLocs)

        uSkyHorizonFracLoc = GLES30.glGetUniformLocation(skyProg, "uHorizonFrac")
        uInstTexArrayLoc = GLES30.glGetUniformLocation(instancedGDriveProg, "uTexArray")
        uFuelBaseTexLoc = GLES30.glGetUniformLocation(fuelStationProg, "uBaseTexArray")
        uFuelWideTexLoc = GLES30.glGetUniformLocation(fuelStationProg, "uWideSignArray")
        uFuelTallTexLoc = GLES30.glGetUniformLocation(fuelStationProg, "uTallSignArray")
        uTrafficTexArrayLoc = GLES30.glGetUniformLocation(trafficVehicleProg, "uTexArray")
        uSignTexLoc = GLES30.glGetUniformLocation(signboardProg, "uSignTex")
        uSignBiasLoc = GLES30.glGetUniformLocation(signboardProg, "uBiasOffset")
        uWaterTimeLoc = GLES30.glGetUniformLocation(waterProg, "uTimeSec")
        uWorkerTexArrayLoc = GLES30.glGetUniformLocation(fuelWorkerProg, "uTexArray")
        uWorkerWorldPosLoc = GLES30.glGetUniformLocation(fuelWorkerProg, "uWorldPos")
        uWorkerYawCSLoc = GLES30.glGetUniformLocation(fuelWorkerProg, "uWorldYawCS")
        uWorkerBoneMatsLoc = GLES30.glGetUniformLocation(fuelWorkerProg, "uBoneMats")

        uBusPosLoc = GLES30.glGetUniformLocation(hinoBusProg, "uBusPos")
        uBusYawCSLoc = GLES30.glGetUniformLocation(hinoBusProg, "uBusYawCS")
        uBusPitchRollLoc = GLES30.glGetUniformLocation(hinoBusProg, "uBusPitchRoll")
        uWheelSpinSteerLoc = GLES30.glGetUniformLocation(hinoBusProg, "uWheelSpinSteer")
        uDoorTransformLoc = GLES30.glGetUniformLocation(hinoBusProg, "uDoorTransform")
        uDoorOpenActiveLoc = GLES30.glGetUniformLocation(hinoBusProg, "uDoorOpenActive")
        uSteerWheelCSLoc = GLES30.glGetUniformLocation(hinoBusProg, "uSteerWheelCS")
        uLightFlagsLoc = GLES30.glGetUniformLocation(hinoBusProg, "uLightFlags")
        uLightFlags2Loc = GLES30.glGetUniformLocation(hinoBusProg, "uLightFlags2")

        // Fullscreen NDC triangle strip for sky & ground horizon gradient
        val skyVerts = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )
        val skyBuf = ByteBuffer.allocateDirect(skyVerts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(skyVerts); position(0) }
        val vbos = IntArray(3)
        GLES30.glGenBuffers(3, vbos, 0)
        skyVbo = vbos[0]
        dynamicColorVbo = vbos[1]
        signboardDynamicVbo = vbos[2]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, skyVerts.size * 4, skyBuf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        // Reset cached GPU handles when EGL context is recreated
        roadVbo = 0
        roadVertexCount = 0
        roadChunks = emptyList()
        builtRoadCount = -1
        landmarkVbo = 0
        landmarkVertexCount = 0
        landmarkChunks = emptyList()
        builtLandmarksForRoads = -1
        waterSurfaceVbo = 0
        waterSurfaceVertexCount = 0
        waterSurfaceChunks = emptyList()
        waterMistVbo = 0
        waterMistVertexCount = 0
        waterMistChunks = emptyList()
        busStaticOpaqueVbo = 0
        busStaticOpaqueVertCount = 0
        busStaticGlassVbo = 0
        busStaticGlassVertCount = 0
        builtBusMeshSize = -1
        signboardTexIds.clear()
        gpuGDriveModels.clear()
        gdriveAtlasTexArrayId = 0
        builtGDriveModels = false
        gpuFuelStationModels.clear()
        fuelStationAtlasTexArrayId = 0
        fuelStationWideSignArrayId = 0
        fuelStationTallSignArrayId = 0
        builtFuelStationModels = false
        gpuFuelWorkerModels.fill(null)
        fuelWorkerAtlasTexArrayId = 0
        builtFuelWorkerModels = false
        pumpMeterTexId = 0
        lastPumpMeterKey = ""
        fuelPriceSignTexId = 0
        shoranurRailwayAtlasTexArrayId = 0
        gpuRailwayStaticTrack = null
        gpuRailwayDynamicModels.clear()
        builtRailwayModels = false
        trafficAtlasTexArrayId = 0
        gpuTrafficModels.fill(null)
        builtTrafficModels = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportW, viewportH)
    }

    override fun onDrawFrame(gl: GL10?) {
        val state = stateProvider?.invoke() ?: latestState
        val w = viewportW.toFloat()
        val h = viewportH.toFloat()

        // Ensure static GPU VBOs are uploaded once roads & Zone 1 assets are ready
        if (state.roads.isNotEmpty()) {
            ensureRoadAndLandmarkVbos(state.roads)
            ensureAnimatedWaterVbos()
            ensureGDriveInstancedMeshes(state.roads)
            ensureFuelStationMeshes(state.roads)
            ensureFuelWorkerMeshes()
            ensureShoranurRailwayMeshes()
            ensureTrafficVehicleMeshes()
        }

        // Compute CameraRig identical to Thrissur3DRoadViewport (bypass clip clamp during cinematic refuel cutscene!)
        val lookTarget = state.camFocusPos
        val camEye = if (state.refuel.isCutsceneActive) {
            state.camEyePos
        } else {
            ThrissurSimViewModel.preventRoadMeshCameraClip(
                focusPos = lookTarget,
                candidateEye = state.camEyePos,
                busPos = state.busPos,
                busHeadingRad = state.busHeadingRad,
                cameraMode = state.cameraMode
            )
        }

        val rawDirX = lookTarget.x - camEye.x
        val rawDirY = lookTarget.y - camEye.y
        val rawDirZ = lookTarget.z - camEye.z
        val dirLen = sqrt(rawDirX * rawDirX + rawDirY * rawDirY + rawDirZ * rawDirZ).coerceAtLeast(0.001f)
        val fwd = Vec3(rawDirX / dirLen, rawDirY / dirLen, rawDirZ / dirLen)

        val rx = fwd.z
        val rz = -fwd.x
        val rLen = hypot(rx, rz).coerceAtLeast(0.001f)
        val right = Vec3(rx / rLen, 0f, rz / rLen)

        val ux = fwd.y * right.z - fwd.z * right.y
        val uy = fwd.z * right.x - fwd.x * right.z
        val uz = fwd.x * right.y - fwd.y * right.x
        val uLen = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.001f)
        val up = Vec3(ux / uLen, uy / uLen, uz / uLen)

        val pitchRad = asin((-fwd.y).coerceIn(-0.95f, 0.95f))
        val minFov = if (state.refuel.isCutsceneActive) 36f else 50f
        val cinematicHalfFovRad = Math.toRadians((state.cameraFovDeg.coerceIn(minFov, 70f) * 0.5f).toDouble())
        val fovScale = (h * 0.5f) / tan(cinematicHalfFovRad).toFloat()
        val horizonY = (h * 0.5f - tan(pitchRad) * fovScale).coerceIn(0f, h * 0.85f)

        val projScaleX = fovScale / (w * 0.5f)
        val projScaleY = fovScale / (h * 0.5f)
        val nearZ = 0.25f
        val farZ = 620.0f
        val depthA = (farZ + nearZ) / (farZ - nearZ)
        val depthB = (-2.0f * farZ * nearZ) / (farZ - nearZ)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 1. Render Sky & Ground Horizon Gradient (depth test disabled, depth write disabled)
        drawSkyAndGroundHorizon(horizonY, h)

        // Enable hardware Z-buffer for all 3D world geometry
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // 2. Render Static Roads, Shoulders, Bellmouths, Road Markings & Landmark Ground Plazas (Spatial Chunk Culled!)
        if (roadVbo != 0 && roadChunks.isNotEmpty()) {
            drawSpatialColorVbo(
                vbo = roadVbo,
                chunks = roadChunks,
                maxDrawDist = 480f,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 3. Render Static Landmarks (Zones 1–4 — Spatial Chunk + Frustum Culled for 90% GPU Triangle Savings!)
        if (landmarkVbo != 0 && landmarkChunks.isNotEmpty()) {
            drawSpatialColorVbo(
                vbo = landmarkVbo,
                chunks = landmarkChunks,
                maxDrawDist = 420f,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 4. Render Dynamic Manikandanal Traffic Signal Lenses & Sakthan Animated Boom Barriers
        drawDynamicTrafficSignalFaces(
            phase = state.manikandanalSignal,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicSakthanBarrierFaces(
            entryOpenFrac = state.sakthanEntryBarrierOpenFrac,
            exitOpenFrac = state.sakthanExitBarrierOpenFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicVadakkeBarrierFaces(
            entryOpenFrac = state.vadakkeEntryBarrierOpenFrac,
            exitOpenFrac = state.vadakkeExitBarrierOpenFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicStationBarrierFaces(
            entryOpenFrac = state.stationEntryBarrierOpenFrac,
            exitOpenFrac = state.stationExitBarrierOpenFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicShoranurTerminalBarrierFaces(
            entryOpenFrac = state.shoranurTermEntryBarrierOpenFrac,
            exitOpenFrac = state.shoranurTermExitBarrierOpenFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicShoranurStationBarrierFaces(
            entryOpenFrac = state.shoranurStationEntryBarrierOpenFrac,
            exitOpenFrac = state.shoranurStationExitBarrierOpenFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicZone3Faces(
            simTimeSec = state.simTimeSec,
            activeTollLaneIdx = state.paliyekkaraTollActiveLaneIdx,
            tollBarrierOpenFrac = state.paliyekkaraTollBarrierOpenFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicZone4Faces(
            simTimeSec = state.simTimeSec,
            lcPhase = state.muthuvaraLcPhase,
            lcBarrierClosedFrac = state.muthuvaraLcBarrierClosedFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
        drawDynamicZone5Faces(
            simTimeSec = state.simTimeSec,
            activeNattikaLaneIdx = state.nattikaTollActiveLaneIdx,
            nattikaBarrierOpenFrac = state.nattikaTollBarrierOpenFrac,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )

        // 5. Render Zone 1 Google Drive 3D Buildings, Trees & Grass via Hardware Instancing (glDrawElementsInstanced)
        //    with Real UV Textures & Alpha-Cutout Foliage
        if (builtGDriveModels && gdriveAtlasTexArrayId != 0 && gpuGDriveModels.isNotEmpty()) {
            drawInstancedZone1GDriveAssets(
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 5A. Render Highway Fuel Stations & 9.22m Roadside Price Signboards (Real Google Drive 3D GLB Assets)
        if (builtFuelStationModels &&
            fuelStationAtlasTexArrayId != 0 &&
            fuelStationWideSignArrayId != 0 &&
            fuelStationTallSignArrayId != 0 &&
            gpuFuelStationModels.isNotEmpty()
        ) {
            drawFuelStationAssets(
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
            drawFuelWorkersHoseAndPumpMeter(
                state = state,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 5B. Render Thrissur - Wadakkanchery - Shoranur 2-Track Indian Railway & Live Animated 7-Coach Trains
        if (builtRailwayModels && shoranurRailwayAtlasTexArrayId != 0) {
            drawShoranurRailwayTracksAndTrains(
                simTimeSec = state.simTimeSec,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 5C. Render All 13 Real Google Drive 3D AI Traffic Vehicles + Toll/Railway-Gate Queue Blocks!
        if (builtTrafficModels && trafficAtlasTexArrayId != 0) {
            drawInstancedTrafficVehicles(
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 5C. Render Live 60 FPS Animated Flowing Water (Peechi Dam Spillway Cascades, Plunge Pool,
        //     Tailrace River, Peechi Reservoir, Kuthiran Cliff Waterfall, Temple Ponds & Arabian Sea Surf)
        if (waterProg != 0 && (waterSurfaceVertexCount > 0 || waterMistVertexCount > 0)) {
            drawAnimatedFlowingWater(
                simTimeSec = state.simTimeSec,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 6. Render 3D Textured Signboards (Terminal Gantries, NHAI/PWD Direction Boards, Station Signs)
        if (signboardEntries.isNotEmpty()) {
            drawTexturedSignboards3D(
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        if (hinoMesh.isEmpty()) {
            val cached = HinoRealMeshCache.cachedMesh
            if (cached.isNotEmpty()) hinoMesh = cached
        }

        // 7. Render Hino RM2 3D Bus (in Chase and Orbit camera modes)
        if (state.cameraMode == CameraViewMode.Chase || state.cameraMode == CameraViewMode.Orbit) {
            drawHinoRm2BusGl(
                state = state,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        if (!notifiedGpuReady && roadVertexCount > 0 && landmarkVertexCount > 0 && state.roads.isNotEmpty()) {
            notifiedGpuReady = true
            onWorldGpuReady?.invoke()
        }
    }

    private fun drawSkyAndGroundHorizon(horizonY: Float, screenH: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_BLEND)

        GLES30.glUseProgram(skyProg)
        // Convert top-down Compose horizonY (0 = top, screenH = bottom) to bottom-up OpenGL vUvY (0 = bottom, 1 = top)
        val horizonFrac = (1.0f - (horizonY / screenH.coerceAtLeast(1f))).coerceIn(0.05f, 0.98f)
        GLES30.glUniform1f(uSkyHorizonFracLoc, horizonFrac)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawStaticColorVbo(
        vbo: Int,
        vertexCount: Int,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        GLES30.glUseProgram(worldColorProg)
        setCameraUniforms(worldColorCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)

        val stride = 8 * 4 // 8 floats: x, y, z, r, g, b, a, bias
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 7 * 4)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawSpatialColorVbo(
        vbo: Int,
        chunks: List<SpatialDrawChunk>,
        maxDrawDist: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        GLES30.glUseProgram(worldColorProg)
        setCameraUniforms(worldColorCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)

        val stride = 8 * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, 7 * 4)

        val ex = camEye.x
        val ez = camEye.z
        val fx = fwd.x
        val fz = fwd.z
        val rx = right.x
        val rz = right.z

        for (i in 0 until chunks.size) {
            val c = chunks[i]
            val dx = c.centerX - ex
            val dz = c.centerZ - ez
            val limit = maxDrawDist + c.radius
            if (dx * dx + dz * dz > limit * limit) continue
            // Camera forward plane + wide horizontal frustum cone culling
            val camZ = dx * fx + dz * fz
            if (camZ < -(c.radius + 25f)) continue
            val camX = abs(dx * rx + dz * rz)
            if (camZ > 0f && camX > camZ * 1.45f + c.radius + 35f) continue
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, c.firstVertex, c.vertexCount)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private val dynamicSignalFaces = ArrayList<QuadFace3D>(16)
    private val dynamicSignalFloats = FloatArray(16 * 6 * 8)
    private val dynamicSignalBuf: FloatBuffer = ByteBuffer
        .allocateDirect(dynamicSignalFloats.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private fun drawDynamicTrafficSignalFaces(
        phase: TrafficSignalAspect,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (hypot(camEye.x - 15.0f, camEye.z - (-63.0f)) > 200f) return
        dynamicSignalFaces.clear()
        collectManikandanalDynamicSignalFaces(phase, dynamicSignalFaces)
        if (dynamicSignalFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicSignalFaces.size) {
            val q = dynamicSignalFaces[i]
            if (fIdx + 48 > dynamicSignalFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicSignalFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicSignalBuf.clear()
        dynamicSignalBuf.put(dynamicSignalFloats, 0, fIdx)
        dynamicSignalBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicSignalBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private val dynamicBarrierFaces = ArrayList<QuadFace3D>(512)
    private val dynamicBarrierFloats = FloatArray(512 * 6 * 8)
    private val dynamicBarrierBuf: FloatBuffer = ByteBuffer
        .allocateDirect(dynamicBarrierFloats.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private fun drawDynamicSakthanBarrierFaces(
        entryOpenFrac: Float,
        exitOpenFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (hypot(camEye.x - 16f, camEye.z - (-174f)) > 260f) return
        dynamicBarrierFaces.clear()
        collectSakthanDynamicBarrierFaces(entryOpenFrac, exitOpenFrac, dynamicBarrierFaces)
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawDynamicVadakkeBarrierFaces(
        entryOpenFrac: Float,
        exitOpenFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (hypot(camEye.x - (-35.5f), camEye.z - 224f) > 260f) return
        dynamicBarrierFaces.clear()
        collectVadakkeDynamicBarrierFaces(entryOpenFrac, exitOpenFrac, dynamicBarrierFaces)
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawDynamicStationBarrierFaces(
        entryOpenFrac: Float,
        exitOpenFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (hypot(camEye.x - (-92.0f), camEye.z - (-138.0f)) > 260f) return
        dynamicBarrierFaces.clear()
        collectStationDynamicBarrierFaces(entryOpenFrac, exitOpenFrac, dynamicBarrierFaces)
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawDynamicShoranurTerminalBarrierFaces(
        entryOpenFrac: Float,
        exitOpenFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (hypot(camEye.x - 140.0f, camEye.z - 3075.0f) > 280f) return
        dynamicBarrierFaces.clear()
        collectShoranurTerminalDynamicBarrierFaces(entryOpenFrac, exitOpenFrac, dynamicBarrierFaces)
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawDynamicShoranurStationBarrierFaces(
        entryOpenFrac: Float,
        exitOpenFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (hypot(camEye.x - 60.0f, camEye.z - 3238.0f) > 280f) return
        dynamicBarrierFaces.clear()
        collectShoranurStationDynamicBarrierFaces(entryOpenFrac, exitOpenFrac, dynamicBarrierFaces)
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawDynamicZone3Faces(
        simTimeSec: Float,
        activeTollLaneIdx: Int,
        tollBarrierOpenFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (camEye.z > -950f || camEye.x < 500f || camEye.x > 1600f) return
        dynamicBarrierFaces.clear()
        Zone3Nh544SouthRenderer.collectZone3DynamicFaces(
            simTimeSec = simTimeSec,
            activeTollLaneIdx = activeTollLaneIdx,
            tollBarrierOpenFrac = tollBarrierOpenFrac,
            camEye = camEye,
            outFaces = dynamicBarrierFaces,
            laneOpenFracs = ThrissurTrafficSystem.latestTelemetry.paliyekkaraLaneOpenFracs
        )
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawDynamicZone4Faces(
        simTimeSec: Float,
        lcPhase: MuthuvaraLcPhase,
        lcBarrierClosedFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (hypot(camEye.x - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_X, camEye.z - Zone4KoleRailHeritageRenderer.MUTHUVARA_LC_Z) > 360f) return
        dynamicBarrierFaces.clear()
        Zone4KoleRailHeritageRenderer.collectZone4DynamicFaces(
            simTimeSec = simTimeSec,
            lcPhase = lcPhase,
            lcBarrierClosedFrac = lcBarrierClosedFrac,
            camEye = camEye,
            outFaces = dynamicBarrierFaces
        )
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawDynamicZone5Faces(
        simTimeSec: Float,
        activeNattikaLaneIdx: Int,
        nattikaBarrierOpenFrac: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (camEye.x > -900f) return
        dynamicBarrierFaces.clear()
        Zone5Nh66CoastalRenderer.collectZone5DynamicFaces(
            simTimeSec = simTimeSec,
            activeNattikaLaneIdx = activeNattikaLaneIdx,
            nattikaBarrierOpenFrac = nattikaBarrierOpenFrac,
            camEye = camEye,
            outFaces = dynamicBarrierFaces,
            laneOpenFracs = ThrissurTrafficSystem.latestTelemetry.nattikaLaneOpenFracs
        )
        if (dynamicBarrierFaces.isEmpty()) return

        var fIdx = 0
        for (i in 0 until dynamicBarrierFaces.size) {
            val q = dynamicBarrierFaces[i]
            if (fIdx + 48 > dynamicBarrierFloats.size) break
            fIdx = appendQuadFaceToFloatArray(q, dynamicBarrierFloats, fIdx, q.biasDist * 0.008f)
        }
        val vertCount = fIdx / 8
        if (vertCount <= 0) return

        dynamicBarrierBuf.clear()
        dynamicBarrierBuf.put(dynamicBarrierFloats, 0, fIdx)
        dynamicBarrierBuf.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fIdx * 4, dynamicBarrierBuf, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        drawStaticColorVbo(
            vbo = dynamicColorVbo,
            vertexCount = vertCount,
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun drawInstancedZone1GDriveAssets(
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        // Zone 1: X in -280..260, Z in -280..265; Zone 2: X in 900..3960, Z in -220..1450; Zone 3: X in 800..2950, Z in -5600..-900
        val inZone1View = camEye.x in -680f..660f && camEye.z in -680f..660f
        val inZone2View = camEye.x in 600f..4400f && camEye.z in -600f..1850f
        val inZone3View = camEye.x in 450f..3250f && camEye.z in -5900f..-550f
        if (!inZone1View && !inZone2View && !inZone3View) return

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(instancedGDriveProg)
        setCameraUniforms(instancedCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, gdriveAtlasTexArrayId)
        GLES30.glUniform1i(uInstTexArrayLoc, 0)

        val vertStride = 9 * 4 // 9 floats: px, py, pz, nx, ny, nz, u, v, packedMat
        val instStride = 5 * 4 // 5 floats: cx, baseY, cz, yawRad, scale
        GLES30.glDisableVertexAttribArray(5)
        GLES30.glVertexAttrib1f(5, 0.0f)

        val ex = camEye.x
        val ez = camEye.z
        val fx = fwd.x
        val fz = fwd.z
        val rx = right.x
        val rz = right.z

        for (i in 0 until gpuGDriveModels.size) {
            val gm = gpuGDriveModels[i]
            if (gm.instanceCount <= 0 || gm.indexCount <= 0 || gm.chunks.isEmpty()) continue

            var boundMesh = false
            val chunks = gm.chunks
            val maxDist = gm.maxDrawDist
            for (cIdx in 0 until chunks.size) {
                val c = chunks[cIdx]
                val dx = c.centerX - ex
                val dz = c.centerZ - ez
                val lim = maxDist + c.radius
                if (dx * dx + dz * dz > lim * lim) continue
                val camZ = dx * fx + dz * fz
                if (camZ < -(c.radius + 20f)) continue
                val camX = abs(dx * rx + dz * rz)
                if (camZ > 0f && camX > camZ * 1.38f + c.radius + 26f) continue

                if (!boundMesh) {
                    boundMesh = true
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.vboId)
                    GLES30.glEnableVertexAttribArray(0)
                    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, vertStride, 0)
                    GLES30.glVertexAttribDivisor(0, 0)

                    GLES30.glEnableVertexAttribArray(1)
                    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, vertStride, 3 * 4)
                    GLES30.glVertexAttribDivisor(1, 0)

                    GLES30.glEnableVertexAttribArray(2)
                    GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, vertStride, 6 * 4)
                    GLES30.glVertexAttribDivisor(2, 0)

                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.instanceVboId)
                    GLES30.glEnableVertexAttribArray(3)
                    GLES30.glVertexAttribDivisor(3, 1)
                    GLES30.glEnableVertexAttribArray(4)
                    GLES30.glVertexAttribDivisor(4, 1)
                    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, gm.iboId)
                }

                val byteOff = c.firstVertex * instStride
                GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, instStride, byteOff)
                GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, instStride, byteOff + 3 * 4)
                GLES30.glDrawElementsInstanced(
                    GLES30.GL_TRIANGLES,
                    gm.indexCount,
                    GLES30.GL_UNSIGNED_SHORT,
                    0,
                    c.vertexCount
                )
            }
        }

        GLES30.glVertexAttribDivisor(3, 0)
        GLES30.glVertexAttribDivisor(4, 0)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(4)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
    }

    private fun drawFuelStationAssets(
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(fuelStationProg)
        setCameraUniforms(fuelStationCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, fuelStationAtlasTexArrayId)
        GLES30.glUniform1i(uFuelBaseTexLoc, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, fuelStationWideSignArrayId)
        GLES30.glUniform1i(uFuelWideTexLoc, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, fuelStationTallSignArrayId)
        GLES30.glUniform1i(uFuelTallTexLoc, 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        val vertStride = 9 * 4
        val instStride = 5 * 4

        GLES30.glDisableVertexAttribArray(5)
        GLES30.glVertexAttribDivisor(5, 0)
        GLES30.glVertexAttrib1f(5, 0.0f)

        val ex = camEye.x
        val ez = camEye.z
        val fx = fwd.x
        val fz = fwd.z
        val rx = right.x
        val rz = right.z

        for (i in 0 until gpuFuelStationModels.size) {
            val gm = gpuFuelStationModels[i]
            if (gm.instanceCount <= 0 || gm.indexCount <= 0 || gm.chunks.isEmpty()) continue

            var boundMesh = false
            val chunks = gm.chunks
            val maxDist = gm.maxDrawDist
            for (cIdx in 0 until chunks.size) {
                val c = chunks[cIdx]
                val dx = c.centerX - ex
                val dz = c.centerZ - ez
                val lim = maxDist + c.radius
                if (dx * dx + dz * dz > lim * lim) continue
                val camZ = dx * fx + dz * fz
                if (camZ < -(c.radius + 24f)) continue
                val camX = abs(dx * rx + dz * rz)
                if (camZ > 0f && camX > camZ * 1.42f + c.radius + 32f) continue

                if (!boundMesh) {
                    boundMesh = true
                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.vboId)
                    GLES30.glEnableVertexAttribArray(0)
                    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, vertStride, 0)
                    GLES30.glVertexAttribDivisor(0, 0)

                    GLES30.glEnableVertexAttribArray(1)
                    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, vertStride, 3 * 4)
                    GLES30.glVertexAttribDivisor(1, 0)

                    GLES30.glEnableVertexAttribArray(2)
                    GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, vertStride, 6 * 4)
                    GLES30.glVertexAttribDivisor(2, 0)

                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.instanceVboId)
                    GLES30.glEnableVertexAttribArray(3)
                    GLES30.glVertexAttribDivisor(3, 1)
                    GLES30.glEnableVertexAttribArray(4)
                    GLES30.glVertexAttribDivisor(4, 1)
                    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, gm.iboId)
                }

                val byteOff = c.firstVertex * instStride
                GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, instStride, byteOff)
                GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, instStride, byteOff + 3 * 4)
                GLES30.glDrawElementsInstanced(
                    GLES30.GL_TRIANGLES,
                    gm.indexCount,
                    GLES30.GL_UNSIGNED_SHORT,
                    0,
                    c.vertexCount
                )
            }
        }

        GLES30.glVertexAttribDivisor(3, 0)
        GLES30.glVertexAttribDivisor(4, 0)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(4)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
        GLES30.glEnable(GLES30.GL_BLEND)

        // Overlay razor-sharp 1024x2048 upright glowing digital Fuel Price Signboard faces directly onto
        // BOTH front & back faces of every visible Entry and Exit Roadside Price Monolith!
        drawFuelPriceSignboardFaces(
            camEye = camEye,
            right = right,
            up = up,
            fwd = fwd,
            projScaleX = projScaleX,
            projScaleY = projScaleY,
            depthA = depthA,
            depthB = depthB
        )
    }

    private fun ensureFuelPriceSignboardTexture(): Int {
        if (fuelPriceSignTexId != 0) return fuelPriceSignTexId
        val bmp = Bitmap.createBitmap(
            FuelStationPremiumSignageBaker.TALL_TEX_W,
            FuelStationPremiumSignageBaker.TALL_TEX_H,
            Bitmap.Config.ARGB_8888
        )
        FuelStationPremiumSignageBaker.renderRoadsidePriceMonolith(bmp)

        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val tid = ids[0]
        if (tid != 0) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tid)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            applyAnisotropicFiltering(GLES30.GL_TEXTURE_2D)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            fuelPriceSignTexId = tid
        }
        bmp.recycle()
        return fuelPriceSignTexId
    }

    private fun drawFuelPriceSignboardFaces(
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        if (signboardProg == 0) return
        val placements = ThrissurFuelStationSystem.getPlacements()
        if (placements.isEmpty()) return
        val texId = ensureFuelPriceSignboardTexture()
        if (texId == 0) return

        val maxDistSq = 260f * 260f
        var bound = false
        val stride = 5 * 4

        fun drawMonolithSign(cx: Float, baseY: Float, cz: Float, cosY: Float, sinY: Float) {
            val dx = cx - camEye.x
            val dz = cz - camEye.z
            if (dx * dx + dz * dz > maxDistSq) return
            val camZ = dx * fwd.x + dz * fwd.z
            if (camZ < -12f) return
            val camX = abs(dx * right.x + dz * right.z)
            if (camZ > 0f && camX > camZ * 1.5f + 16f) return

            if (!bound) {
                bound = true
                GLES30.glUseProgram(signboardProg)
                setCameraUniforms(signboardCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)
                GLES30.glUniform1i(uSignTexLoc, 0)
                GLES30.glUniform1f(uSignBiasLoc, -0.012f)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            }

            fun toW(lx: Float, ly: Float, lz: Float): Vec3 = Vec3(
                x = cx + lx * cosY - lz * sinY,
                y = baseY + ly,
                z = cz + lx * sinY + lz * cosY
            )

            // Exact GLB Roadside_Sign_Face bounds: X=[-1.56, +1.56], Y=[2.66, 8.98], Z=±0.265 (+5mm flush)
            // Left-to-right UV order verified for left-handed right = (+fwd.z, 0, -fwd.x):
            //   -Z face (viewer looks toward +Z, screen-right = +X): LEFT = -1.56 (u=0), RIGHT = +1.56 (u=1)
            //   +Z face (viewer looks toward -Z, screen-right = -X): LEFT = +1.56 (u=0), RIGHT = -1.56 (u=1)
            val halfW = 1.56f
            val yBot = 2.66f
            val yTop = 8.98f
            val zOff = 0.270f

            val faces = arrayOf(
                arrayOf(
                    toW(-halfW, yBot, -zOff),
                    toW(halfW, yBot, -zOff),
                    toW(halfW, yTop, -zOff),
                    toW(-halfW, yTop, -zOff)
                ),
                arrayOf(
                    toW(halfW, yBot, zOff),
                    toW(-halfW, yBot, zOff),
                    toW(-halfW, yTop, zOff),
                    toW(halfW, yTop, zOff)
                )
            )

            for (f in faces) {
                val v0 = f[0]; val v1 = f[1]; val v2 = f[2]; val v3 = f[3]
                var c = 0
                signboardQuadFloats[c++] = v0.x; signboardQuadFloats[c++] = v0.y; signboardQuadFloats[c++] = v0.z; signboardQuadFloats[c++] = 0f; signboardQuadFloats[c++] = 1f
                signboardQuadFloats[c++] = v1.x; signboardQuadFloats[c++] = v1.y; signboardQuadFloats[c++] = v1.z; signboardQuadFloats[c++] = 1f; signboardQuadFloats[c++] = 1f
                signboardQuadFloats[c++] = v2.x; signboardQuadFloats[c++] = v2.y; signboardQuadFloats[c++] = v2.z; signboardQuadFloats[c++] = 1f; signboardQuadFloats[c++] = 0f
                signboardQuadFloats[c++] = v0.x; signboardQuadFloats[c++] = v0.y; signboardQuadFloats[c++] = v0.z; signboardQuadFloats[c++] = 0f; signboardQuadFloats[c++] = 1f
                signboardQuadFloats[c++] = v2.x; signboardQuadFloats[c++] = v2.y; signboardQuadFloats[c++] = v2.z; signboardQuadFloats[c++] = 1f; signboardQuadFloats[c++] = 0f
                signboardQuadFloats[c++] = v3.x; signboardQuadFloats[c++] = v3.y; signboardQuadFloats[c++] = v3.z; signboardQuadFloats[c++] = 0f; signboardQuadFloats[c++] = 0f

                signboardQuadBuf.clear()
                signboardQuadBuf.put(signboardQuadFloats, 0, c)
                signboardQuadBuf.position(0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, signboardDynamicVbo)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, c * 4, signboardQuadBuf, GLES30.GL_STREAM_DRAW)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * 4)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            }
        }

        for (i in placements.indices) {
            val st = placements[i]
            drawMonolithSign(st.signX, st.signBaseY, st.signZ, st.signCosYaw, st.signSinYaw)
            drawMonolithSign(st.exitSignX, st.exitSignBaseY, st.exitSignZ, st.exitSignCosYaw, st.exitSignSinYaw)
        }

        if (bound) {
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }
    }

    private fun drawFuelWorkersHoseAndPumpMeter(
        state: SimUiState,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        val refuel = state.refuel
        val nowNanos = System.nanoTime()
        val frameDtSec = if (lastWorkerAnimNanos == 0L) {
            1f / 60f
        } else {
            ((nowNanos - lastWorkerAnimNanos) * 1e-9f).coerceIn(0.001f, 0.06f)
        }
        lastWorkerAnimNanos = nowNanos

        var activeNozzleHandX = 0f
        var activeNozzleHandY = 0f
        var activeNozzleHandZ = 0f
        var activeNozzleAimX = 0f
        var activeNozzleAimY = 0f
        var activeNozzleAimZ = 1f
        var activeNozzleUpX = 0f
        var activeNozzleUpY = 1f
        var activeNozzleUpZ = 0f
        var hasActiveNozzleHand = false

        // 1. Render 17-Bone GPU-Skinned 3D Fuel Station Attendants (Petrol Worker 1 & Diesel Worker 2)
        if (builtFuelWorkerModels && fuelWorkerAtlasTexArrayId != 0 && fuelWorkerProg != 0) {
            val workersToDraw: List<FuelWorkerWorldPose> = if (refuel.activeWorkers.isNotEmpty()) {
                refuel.activeWorkers
            } else {
                // Fallback idle attendants at any visible highway fuel station within 140m
                val placements = ThrissurFuelStationSystem.getPlacements()
                var nearest: ThrissurFuelStationSystem.FuelStationPlacement? = null
                var bestD2 = 140f * 140f
                for (i in placements.indices) {
                    val st = placements[i]
                    val dx = st.cx - camEye.x
                    val dz = st.cz - camEye.z
                    val d2 = dx * dx + dz * dz
                    if (d2 < bestD2) {
                        bestD2 = d2
                        nearest = st
                    }
                }
                if (nearest != null) {
                    val cosY = nearest.cosYaw
                    val sinY = nearest.sinYaw
                    // One attendant on the right (+Z) slot-facing side of each pump island:
                    //   - Diesel Worker 2 on ROW 1 • DIESEL (Z=-3.50m) facing Diesel Slot (+Z under canopy)
                    //   - Petrol Worker 1 on ROW 2 • PETROL (Z=+3.50m) facing Petrol Slot (+Z rear lane)
                    val dLx = ThrissurFuelStationSystem.DIESEL_DISPENSER_X - 0.90f
                    val dLz = ThrissurFuelStationSystem.DIESEL_ISLAND_Z + ThrissurFuelStationSystem.DIESEL_SLOT_SIDE_DIR * 0.88f
                    val pLx = ThrissurFuelStationSystem.PETROL_DISPENSER_X + 0.90f
                    val pLz = ThrissurFuelStationSystem.PETROL_ISLAND_Z + ThrissurFuelStationSystem.PETROL_SLOT_SIDE_DIR * 0.88f
                    val dWx = nearest.cx + dLx * cosY - dLz * sinY
                    val dWz = nearest.cz + dLx * sinY + dLz * cosY
                    val pWx = nearest.cx + pLx * cosY - pLz * sinY
                    val pWz = nearest.cz + pLx * sinY + pLz * cosY
                    val faceDieselSlotYaw = kotlin.math.atan2(-sinY, cosY)
                    val facePetrolSlotYaw = kotlin.math.atan2(-sinY, cosY)
                    listOf(
                        FuelWorkerWorldPose(
                            workerIndex = FuelStationWorkerAnimator.WORKER_PETROL,
                            x = pWx, y = nearest.baseY + 0.02f, z = pWz,
                            yawRad = facePetrolSlotYaw,
                            animState = WorkerAnimState.IDLE,
                            animPhase = state.simTimeSec
                        ),
                        FuelWorkerWorldPose(
                            workerIndex = FuelStationWorkerAnimator.WORKER_DIESEL,
                            x = dWx, y = nearest.baseY + 0.02f, z = dWz,
                            yawRad = faceDieselSlotYaw,
                            animState = WorkerAnimState.IDLE,
                            animPhase = state.simTimeSec + 1.4f
                        )
                    )
                } else {
                    emptyList()
                }
            }

            if (workersToDraw.isNotEmpty()) {
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glUseProgram(fuelWorkerProg)
                setCameraUniforms(fuelWorkerCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, fuelWorkerAtlasTexArrayId)
                GLES30.glUniform1i(uWorkerTexArrayLoc, 0)

                val vertStride = 13 * 4
                for (i in workersToDraw.indices) {
                    val wp = workersToDraw[i]
                    val dx = wp.x - camEye.x
                    val dz = wp.z - camEye.z
                    if (dx * dx + dz * dz > 165f * 165f) continue
                    val camZ = dx * fwd.x + dz * fwd.z
                    if (camZ < -4.0f) continue

                    val gm = gpuFuelWorkerModels.getOrNull(wp.workerIndex) ?: continue
                    FuelStationWorkerAnimator.solvePose(
                        model = gm.model,
                        pose = wp,
                        dtSec = frameDtSec,
                        outSkinMats = workerSkinMatsScratch,
                        outGripFrameLocal = workerGripFrameLocal
                    )

                    val cosY = cos(wp.yawRad)
                    val sinY = sin(wp.yawRad)

                    if (wp.holdingNozzle) {
                        val lx = workerGripFrameLocal[0]
                        val ly = workerGripFrameLocal[1]
                        val lz = workerGripFrameLocal[2]
                        activeNozzleHandX = wp.x + lx * cosY + lz * sinY
                        activeNozzleHandY = wp.y + ly
                        activeNozzleHandZ = wp.z - lx * sinY + lz * cosY

                        val lax = workerGripFrameLocal[3]
                        val lay = workerGripFrameLocal[4]
                        val laz = workerGripFrameLocal[5]
                        activeNozzleAimX = lax * cosY + laz * sinY
                        activeNozzleAimY = lay
                        activeNozzleAimZ = -lax * sinY + laz * cosY

                        val lux = workerGripFrameLocal[6]
                        val luy = workerGripFrameLocal[7]
                        val luz = workerGripFrameLocal[8]
                        activeNozzleUpX = lux * cosY + luz * sinY
                        activeNozzleUpY = luy
                        activeNozzleUpZ = -lux * sinY + luz * cosY
                        hasActiveNozzleHand = true
                    }

                    GLES30.glUniform3f(uWorkerWorldPosLoc, wp.x, wp.y, wp.z)
                    GLES30.glUniform2f(uWorkerYawCSLoc, cosY, sinY)
                    GLES30.glUniformMatrix4fv(
                        uWorkerBoneMatsLoc,
                        FuelStationWorkerAnimator.BONE_COUNT,
                        false,
                        workerSkinMatsScratch,
                        0
                    )

                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.vboId)
                    GLES30.glEnableVertexAttribArray(0)
                    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, vertStride, 0)
                    GLES30.glEnableVertexAttribArray(1)
                    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, vertStride, 3 * 4)
                    GLES30.glEnableVertexAttribArray(2)
                    GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, vertStride, 6 * 4)
                    GLES30.glEnableVertexAttribArray(3)
                    GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, vertStride, 9 * 4)
                    GLES30.glEnableVertexAttribArray(4)
                    GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, vertStride, 11 * 4)

                    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, gm.iboId)
                    GLES30.glDrawElements(GLES30.GL_TRIANGLES, gm.indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
                }

                GLES30.glDisableVertexAttribArray(0)
                GLES30.glDisableVertexAttribArray(1)
                GLES30.glDisableVertexAttribArray(2)
                GLES30.glDisableVertexAttribArray(3)
                GLES30.glDisableVertexAttribArray(4)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
                GLES30.glEnable(GLES30.GL_BLEND)
            }
        }

        // 2. Render Glowing Bay Slots + Contact Shadows + 3D Hinged Bus Fuel Door + Side Hose/Holster/Nozzle
        var cursor = 0
        fun putColorVert(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float, bias: Float) {
            if (cursor + 8 > hoseDynamicFloats.size) return
            hoseDynamicFloats[cursor++] = x
            hoseDynamicFloats[cursor++] = y
            hoseDynamicFloats[cursor++] = z
            hoseDynamicFloats[cursor++] = r
            hoseDynamicFloats[cursor++] = g
            hoseDynamicFloats[cursor++] = b
            hoseDynamicFloats[cursor++] = a
            hoseDynamicFloats[cursor++] = bias
        }

        fun emitQuad3D(
            x0: Float, y0: Float, z0: Float,
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            r: Float, g: Float, b: Float,
            a: Float = 1f,
            bias: Float = 0f
        ) {
            putColorVert(x0, y0, z0, r, g, b, a, bias)
            putColorVert(x1, y1, z1, r, g, b, a, bias)
            putColorVert(x2, y2, z2, r, g, b, a, bias)
            putColorVert(x0, y0, z0, r, g, b, a, bias)
            putColorVert(x2, y2, z2, r, g, b, a, bias)
            putColorVert(x3, y3, z3, r, g, b, a, bias)
        }

        fun emitTubeSegment(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            radiusA: Float,
            radiusB: Float,
            r: Float, g: Float, b: Float,
            sides: Int = 8,
            capEnds: Boolean = false
        ) {
            val dx = bx - ax; val dy = by - ay; val dz = bz - az
            val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.0005f)
            val tx = dx / len; val ty = dy / len; val tz = dz / len
            val rx0 = if (abs(ty) < 0.85f) 0f else 1f
            val ry0 = if (abs(ty) < 0.85f) 1f else 0f
            val rz0 = 0f
            var ux = ty * rz0 - tz * ry0
            var uy = tz * rx0 - tx * rz0
            var uz = tx * ry0 - ty * rx0
            val uLen = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.0005f)
            ux /= uLen; uy /= uLen; uz /= uLen
            val vx = ty * uz - tz * uy
            val vy = tz * ux - tx * uz
            val vz = tx * uy - ty * ux

            val invSides = (2.0 * kotlin.math.PI / sides).toFloat()
            for (s in 0 until sides) {
                val a0 = s * invSides
                val a1 = (s + 1) * invSides
                val ca0 = cos(a0); val sa0 = sin(a0)
                val ca1 = cos(a1); val sa1 = sin(a1)
                val dir0x = ux * ca0 + vx * sa0
                val dir0y = uy * ca0 + vy * sa0
                val dir0z = uz * ca0 + vz * sa0
                val dir1x = ux * ca1 + vx * sa1
                val dir1y = uy * ca1 + vy * sa1
                val dir1z = uz * ca1 + vz * sa1
                val avgNy = (dir0y + dir1y) * 0.5f
                val shade = 0.68f + 0.32f * (avgNy * 0.5f + 0.5f)
                val cr = (r * shade).coerceIn(0f, 1f)
                val cg = (g * shade).coerceIn(0f, 1f)
                val cb = (b * shade).coerceIn(0f, 1f)

                val p0x = ax + dir0x * radiusA; val p0y = ay + dir0y * radiusA; val p0z = az + dir0z * radiusA
                val p1x = ax + dir1x * radiusA; val p1y = ay + dir1y * radiusA; val p1z = az + dir1z * radiusA
                val p2x = bx + dir1x * radiusB; val p2y = by + dir1y * radiusB; val p2z = bz + dir1z * radiusB
                val p3x = bx + dir0x * radiusB; val p3y = by + dir0y * radiusB; val p3z = bz + dir0z * radiusB

                putColorVert(p0x, p0y, p0z, cr, cg, cb, 1f, 0f)
                putColorVert(p1x, p1y, p1z, cr, cg, cb, 1f, 0f)
                putColorVert(p2x, p2y, p2z, cr, cg, cb, 1f, 0f)
                putColorVert(p0x, p0y, p0z, cr, cg, cb, 1f, 0f)
                putColorVert(p2x, p2y, p2z, cr, cg, cb, 1f, 0f)
                putColorVert(p3x, p3y, p3z, cr, cg, cb, 1f, 0f)

                if (capEnds) {
                    val capR = (r * 0.78f).coerceIn(0f, 1f)
                    val capG = (g * 0.78f).coerceIn(0f, 1f)
                    val capB = (b * 0.78f).coerceIn(0f, 1f)
                    putColorVert(ax, ay, az, capR, capG, capB, 1f, 0f)
                    putColorVert(p1x, p1y, p1z, capR, capG, capB, 1f, 0f)
                    putColorVert(p0x, p0y, p0z, capR, capG, capB, 1f, 0f)

                    putColorVert(bx, by, bz, capR, capG, capB, 1f, 0f)
                    putColorVert(p3x, p3y, p3z, capR, capG, capB, 1f, 0f)
                    putColorVert(p2x, p2y, p2z, capR, capG, capB, 1f, 0f)
                }
            }
        }

        // 2A. Render TWO Symmetrical 3D Glowing Ground Fuel Bay Parking Slots (Diesel Side 1 + Petrol Side 2)!
        run {
            val placements = ThrissurFuelStationSystem.getPlacements()
            for (sIdx in placements.indices) {
                val st = placements[sIdx]
                val sdx = st.cx - camEye.x
                val sdz = st.cz - camEye.z
                if (sdx * sdx + sdz * sdz > 135f * 135f) continue

                val isThisStation = (refuel.stationId == st.id)
                val pulse = 0.72f + 0.28f * sin(state.simTimeSec * 4.2f)
                val cosY = st.cosYaw
                val sinY = st.sinYaw
                val by = st.baseY + 0.036f

                fun emitLocalQuad(
                    x0: Float, z0: Float, x1: Float, z1: Float,
                    r: Float, g: Float, b: Float, a: Float
                ) {
                    val p0x = st.cx + x0 * cosY - z0 * sinY; val p0z = st.cz + x0 * sinY + z0 * cosY
                    val p1x = st.cx + x1 * cosY - z0 * sinY; val p1z = st.cz + x1 * sinY + z0 * cosY
                    val p2x = st.cx + x1 * cosY - z1 * sinY; val p2z = st.cz + x1 * sinY + z1 * cosY
                    val p3x = st.cx + x0 * cosY - z1 * sinY; val p3z = st.cz + x0 * sinY + z1 * cosY
                    putColorVert(p0x, by, p0z, r, g, b, a, -0.025f)
                    putColorVert(p1x, by, p1z, r, g, b, a, -0.025f)
                    putColorVert(p2x, by, p2z, r, g, b, a, -0.025f)
                    putColorVert(p0x, by, p0z, r, g, b, a, -0.025f)
                    putColorVert(p2x, by, p2z, r, g, b, a, -0.025f)
                    putColorVert(p3x, by, p3z, r, g, b, a, -0.025f)
                }

                fun drawSlotBox(
                    minX: Float, maxX: Float, minZ: Float, maxZ: Float,
                    isFullyParked: Boolean,
                    idleR: Float, idleG: Float, idleB: Float
                ) {
                    val boxR = if (isFullyParked) 0.14f else idleR
                    val boxG = if (isFullyParked) 0.92f else idleG
                    val boxB = if (isFullyParked) 0.48f else idleB
                    val lineA = if (isFullyParked) 0.95f else (0.64f + 0.30f * pulse)
                    val fillA = if (isFullyParked) 0.20f else (0.09f + 0.07f * pulse)
                    val bw = 0.22f

                    // Translucent glowing bay floor pad
                    emitLocalQuad(minX + bw, minZ + bw, maxX - bw, maxZ - bw, boxR, boxG, boxB, fillA)
                    // Four crisp glowing perimeter stripes
                    emitLocalQuad(minX, minZ, maxX, minZ + bw, boxR, boxG, boxB, lineA)
                    emitLocalQuad(minX, maxZ - bw, maxX, maxZ, boxR, boxG, boxB, lineA)
                    emitLocalQuad(minX, minZ + bw, minX + bw, maxZ - bw, boxR, boxG, boxB, lineA)
                    emitLocalQuad(maxX - bw, minZ + bw, maxX, maxZ - bw, boxR, boxG, boxB, lineA)

                    // Bright white-tinted L-corner accents for unmistakable full-bus 4-corner alignment
                    val cl = 1.20f
                    val cw = 0.34f
                    val cr = (boxR * 0.55f + 0.45f).coerceAtMost(1f)
                    val cg = (boxG * 0.55f + 0.45f).coerceAtMost(1f)
                    val cb = (boxB * 0.55f + 0.45f).coerceAtMost(1f)
                    emitLocalQuad(minX, minZ, minX + cl, minZ + cw, cr, cg, cb, lineA)
                    emitLocalQuad(minX, minZ, minX + cw, minZ + cl, cr, cg, cb, lineA)
                    emitLocalQuad(maxX - cl, minZ, maxX, minZ + cw, cr, cg, cb, lineA)
                    emitLocalQuad(maxX - cw, minZ, maxX, minZ + cl, cr, cg, cb, lineA)
                    emitLocalQuad(minX, maxZ - cw, minX + cl, maxZ, cr, cg, cb, lineA)
                    emitLocalQuad(minX, maxZ - cl, minX + cw, maxZ, cr, cg, cb, lineA)
                    emitLocalQuad(maxX - cl, maxZ - cw, maxX, maxZ, cr, cg, cb, lineA)
                    emitLocalQuad(maxX - cw, maxZ - cl, maxX, maxZ, cr, cg, cb, lineA)

                    // Center bus stop-alignment cross inside slot
                    val midZ = (minZ + maxZ) * 0.5f
                    emitLocalQuad(-1.8f, midZ - 0.14f, 1.8f, midZ + 0.14f, cr, cg, cb, lineA * 0.72f)
                    emitLocalQuad(-0.14f, midZ - 0.75f, 0.14f, midZ + 0.75f, cr, cg, cb, lineA * 0.72f)
                }

                // Side 1: Diesel Parking Slot (ROW 1 • DIESEL, Amber -> Green when all 4 bus corners inside!)
                drawSlotBox(
                    minX = ThrissurFuelStationSystem.DIESEL_SLOT_MIN_X,
                    maxX = ThrissurFuelStationSystem.DIESEL_SLOT_MAX_X,
                    minZ = ThrissurFuelStationSystem.DIESEL_SLOT_MIN_Z,
                    maxZ = ThrissurFuelStationSystem.DIESEL_SLOT_MAX_Z,
                    isFullyParked = isThisStation && refuel.parkedSlotSide == FuelType.DIESEL,
                    idleR = 0.98f, idleG = 0.72f, idleB = 0.12f
                )

                // Side 2: Petrol Parking Slot (ROW 2 • PETROL, Cyan-Emerald -> Green when all 4 bus corners inside!)
                drawSlotBox(
                    minX = ThrissurFuelStationSystem.PETROL_SLOT_MIN_X,
                    maxX = ThrissurFuelStationSystem.PETROL_SLOT_MAX_X,
                    minZ = ThrissurFuelStationSystem.PETROL_SLOT_MIN_Z,
                    maxZ = ThrissurFuelStationSystem.PETROL_SLOT_MAX_Z,
                    isFullyParked = isThisStation && refuel.parkedSlotSide == FuelType.PETROL,
                    idleR = 0.16f, idleG = 0.78f, idleB = 0.92f
                )
            }
        }

        // Soft contact shadow discs under active fuel attendants
        for (i in refuel.activeWorkers.indices) {
            val wp = refuel.activeWorkers[i]
            val dx = wp.x - camEye.x
            val dz = wp.z - camEye.z
            if (dx * dx + dz * dz > 95f * 95f) continue
            val sy = wp.y + 0.015f
            val r = 0.44f
            putColorVert(wp.x - r, sy, wp.z - r, 0.04f, 0.05f, 0.06f, 0.36f, -0.02f)
            putColorVert(wp.x + r, sy, wp.z - r, 0.04f, 0.05f, 0.06f, 0.36f, -0.02f)
            putColorVert(wp.x + r, sy, wp.z + r, 0.04f, 0.05f, 0.06f, 0.36f, -0.02f)
            putColorVert(wp.x - r, sy, wp.z - r, 0.04f, 0.05f, 0.06f, 0.36f, -0.02f)
            putColorVert(wp.x + r, sy, wp.z + r, 0.04f, 0.05f, 0.06f, 0.36f, -0.02f)
            putColorVert(wp.x - r, sy, wp.z + r, 0.04f, 0.05f, 0.06f, 0.36f, -0.02f)
        }

        // 2B. Realistic 3D Hinged Bus Fuel Filler Door + Recessed Dark Tank Opening + Filler Neck Collar
        if (refuel.showFuelDoorAssembly) {
            val bPos = state.busPos
            val ch = cos(state.busHeadingRad)
            val sh = sin(state.busHeadingRad)
            val busRightX = ch
            val busRightZ = -sh
            val busFwdX = sh
            val busFwdZ = ch

            val side = refuel.fuelDoorSideSign
            val longOff = refuel.fuelDoorLongOffset
            val wallX = side * ThrissurFuelStationSystem.BUS_SIDE_WALL_X
            val centerY = ThrissurFuelStationSystem.BUS_FUEL_DOOR_CENTER_Y
            val doorW = ThrissurFuelStationSystem.BUS_FUEL_DOOR_WIDTH
            val doorH = ThrissurFuelStationSystem.BUS_FUEL_DOOR_HEIGHT
            val halfW = doorW * 0.5f
            val halfH = doorH * 0.5f
            val openFrac = refuel.fuelDoorOpenFrac.coerceIn(0f, 1f)

            fun busWx(lx: Float, lz: Float) = bPos.x + busRightX * lx + busFwdX * lz
            fun busWz(lx: Float, lz: Float) = bPos.z + busRightZ * lx + busFwdZ * lz

            fun emitWallQuad(
                offsetOut: Float,
                z0: Float, y0: Float,
                z1: Float, y1: Float,
                r: Float, g: Float, b: Float,
                bias: Float
            ) {
                val lx = wallX + side * offsetOut
                val wy0 = bPos.y + y0
                val wy1 = bPos.y + y1
                emitQuad3D(
                    busWx(lx, z0), wy0, busWz(lx, z0),
                    busWx(lx, z1), wy0, busWz(lx, z1),
                    busWx(lx, z1), wy1, busWz(lx, z1),
                    busWx(lx, z0), wy1, busWz(lx, z0),
                    r, g, b, 1f, bias
                )
            }

            fun emitWallDisc(
                offsetOut: Float,
                cz: Float, cy: Float,
                radius: Float,
                r: Float, g: Float, b: Float,
                bias: Float,
                segs: Int = 14
            ) {
                val lx = wallX + side * offsetOut
                val centerWx = busWx(lx, cz)
                val centerWy = bPos.y + cy
                val centerWz = busWz(lx, cz)
                val step = (2.0 * kotlin.math.PI / segs).toFloat()
                for (s in 0 until segs) {
                    val a0 = s * step
                    val a1 = (s + 1) * step
                    val z0 = cz + cos(a0) * radius
                    val y0 = cy + sin(a0) * radius
                    val z1 = cz + cos(a1) * radius
                    val y1 = cy + sin(a1) * radius
                    putColorVert(centerWx, centerWy, centerWz, r, g, b, 1f, bias)
                    putColorVert(busWx(lx, z0), bPos.y + y0, busWz(lx, z0), r, g, b, 1f, bias)
                    putColorVert(busWx(lx, z1), bPos.y + y1, busWz(lx, z1), r, g, b, 1f, bias)
                }
            }

            // Outer flush dark gunmetal perimeter bezel on bus side wall
            emitWallQuad(
                offsetOut = 0.004f,
                z0 = longOff - halfW - 0.014f, y0 = centerY - halfH - 0.014f,
                z1 = longOff + halfW + 0.014f, y1 = centerY + halfH + 0.014f,
                r = 0.14f, g = 0.15f, b = 0.17f,
                bias = -0.004f
            )

            // Recessed dark filler housing cavity + brushed aluminum collar + deep dark fuel tank hole
            emitWallQuad(
                offsetOut = 0.006f,
                z0 = longOff - halfW + 0.006f, y0 = centerY - halfH + 0.006f,
                z1 = longOff + halfW - 0.006f, y1 = centerY + halfH - 0.006f,
                r = 0.045f, g = 0.050f, b = 0.058f,
                bias = -0.008f
            )
            // Brushed aluminum filler neck flange ring (radius 6.8 cm)
            emitWallDisc(
                offsetOut = 0.008f,
                cz = longOff, cy = centerY,
                radius = 0.068f,
                r = 0.62f, g = 0.66f, b = 0.72f,
                bias = -0.012f
            )
            // Inner threaded collar (radius 5.4 cm)
            emitWallDisc(
                offsetOut = 0.009f,
                cz = longOff, cy = centerY,
                radius = 0.054f,
                r = 0.30f, g = 0.33f, b = 0.38f,
                bias = -0.015f
            )
            // Actual dark circular fuel tank hole (radius 4.3 cm) — chrome spout penetrates visibly inside!
            emitWallDisc(
                offsetOut = 0.010f,
                cz = longOff, cy = centerY,
                radius = 0.043f,
                r = 0.015f, g = 0.018f, b = 0.022f,
                bias = -0.019f
            )

            // Tethered black fuel cap resting beneath collar while door is open
            if (openFrac > 0.15f) {
                emitWallDisc(
                    offsetOut = 0.012f,
                    cz = longOff - side * 0.065f, cy = centerY - 0.095f,
                    radius = 0.032f,
                    r = 0.12f, g = 0.13f, b = 0.15f,
                    bias = -0.022f,
                    segs = 10
                )
            }

            // Solid 3D Hinged Bus Fuel Door Leaf rotating smoothly 0 deg -> 112 deg around its vertical hinge!
            // Hinge geometry matches ThrissurSimViewModel.fuelDoorHandleWorld() millimeter-for-millimeter.
            val hingeSign = -side
            val maxRad = Math.toRadians(ThrissurFuelStationSystem.BUS_FUEL_DOOR_MAX_OPEN_DEG.toDouble()).toFloat()
            val theta = openFrac * maxRad
            val sinT = sin(theta)
            val cosT = cos(theta)
            val hingeLocalX = side * (ThrissurFuelStationSystem.BUS_SIDE_WALL_X + 0.006f)
            val hingeLocalZ = longOff + hingeSign * halfW
            val leafDirX = side * sinT
            val leafDirZ = -hingeSign * cosT
            val doorNormX = side * cosT
            val doorNormZ = hingeSign * sinT
            val thick = 0.015f

            fun doorPtX(uDist: Float, nOff: Float) = busWx(
                hingeLocalX + leafDirX * uDist + doorNormX * nOff,
                hingeLocalZ + leafDirZ * uDist + doorNormZ * nOff
            )
            fun doorPtZ(uDist: Float, nOff: Float) = busWz(
                hingeLocalX + leafDirX * uDist + doorNormX * nOff,
                hingeLocalZ + leafDirZ * uDist + doorNormZ * nOff
            )

            val yBot = bPos.y + centerY - halfH
            val yTop = bPos.y + centerY + halfH
            val doorBias = if (openFrac < 0.04f) -0.024f else 0f

            // Exterior painted Hino RM2 crimson face
            emitQuad3D(
                doorPtX(0f, thick), yBot, doorPtZ(0f, thick),
                doorPtX(doorW, thick), yBot, doorPtZ(doorW, thick),
                doorPtX(doorW, thick), yTop, doorPtZ(doorW, thick),
                doorPtX(0f, thick), yTop, doorPtZ(0f, thick),
                0.62f, 0.095f, 0.090f, 1f, doorBias
            )
            // Subtle upper coachwork highlight strip on exterior door face
            emitQuad3D(
                doorPtX(0.015f, thick + 0.002f), yTop - 0.055f, doorPtZ(0.015f, thick + 0.002f),
                doorPtX(doorW - 0.015f, thick + 0.002f), yTop - 0.055f, doorPtZ(doorW - 0.015f, thick + 0.002f),
                doorPtX(doorW - 0.015f, thick + 0.002f), yTop - 0.020f, doorPtZ(doorW - 0.015f, thick + 0.002f),
                doorPtX(0.015f, thick + 0.002f), yTop - 0.020f, doorPtZ(0.015f, thick + 0.002f),
                0.86f, 0.72f, 0.24f, 1f, doorBias - 0.002f
            )
            // Interior structural dark-steel door skin
            emitQuad3D(
                doorPtX(0f, 0f), yBot, doorPtZ(0f, 0f),
                doorPtX(0f, 0f), yTop, doorPtZ(0f, 0f),
                doorPtX(doorW, 0f), yTop, doorPtZ(doorW, 0f),
                doorPtX(doorW, 0f), yBot, doorPtZ(doorW, 0f),
                0.15f, 0.16f, 0.18f, 1f, 0f
            )
            // 4 perimeter thickness edges of solid 3D door panel
            emitQuad3D(
                doorPtX(doorW, 0f), yBot, doorPtZ(doorW, 0f),
                doorPtX(doorW, thick), yBot, doorPtZ(doorW, thick),
                doorPtX(doorW, thick), yTop, doorPtZ(doorW, thick),
                doorPtX(doorW, 0f), yTop, doorPtZ(doorW, 0f),
                0.28f, 0.10f, 0.10f, 1f, 0f
            )
            emitQuad3D(
                doorPtX(0f, 0f), yTop, doorPtZ(0f, 0f),
                doorPtX(doorW, 0f), yTop, doorPtZ(doorW, 0f),
                doorPtX(doorW, thick), yTop, doorPtZ(doorW, thick),
                doorPtX(0f, thick), yTop, doorPtZ(0f, thick),
                0.35f, 0.11f, 0.11f, 1f, 0f
            )
            emitQuad3D(
                doorPtX(0f, 0f), yBot, doorPtZ(0f, 0f),
                doorPtX(0f, thick), yBot, doorPtZ(0f, thick),
                doorPtX(doorW, thick), yBot, doorPtZ(doorW, thick),
                doorPtX(doorW, 0f), yBot, doorPtZ(doorW, 0f),
                0.22f, 0.08f, 0.08f, 1f, 0f
            )
            // Metallic hinge barrel knuckles along hinge vertical edge
            val hWx = busWx(hingeLocalX, hingeLocalZ)
            val hWz = busWz(hingeLocalX, hingeLocalZ)
            emitTubeSegment(hWx, yBot + 0.04f, hWz, hWx, yBot + 0.10f, hWz, 0.011f, 0.011f, 0.56f, 0.60f, 0.66f, sides = 6)
            emitTubeSegment(hWx, yTop - 0.10f, hWz, hWx, yTop - 0.04f, hWz, 0.011f, 0.011f, 0.56f, 0.60f, 0.66f, sides = 6)

            // Raised ergonomic door pull handle at u = 0.84 * doorW (exact hand grab target!)
            val hU0 = doorW * 0.78f
            val hU1 = doorW * 0.90f
            val hOut = thick + 0.016f
            val hyMid = bPos.y + centerY
            emitTubeSegment(
                doorPtX(hU0, hOut), hyMid - 0.036f, doorPtZ(hU0, hOut),
                doorPtX(hU0, hOut), hyMid + 0.036f, doorPtZ(hU0, hOut),
                0.009f, 0.009f, 0.18f, 0.20f, 0.23f, sides = 6, capEnds = true
            )
            emitQuad3D(
                doorPtX(hU0, thick + 0.003f), hyMid - 0.042f, doorPtZ(hU0, thick + 0.003f),
                doorPtX(hU1, thick + 0.003f), hyMid - 0.042f, doorPtZ(hU1, thick + 0.003f),
                doorPtX(hU1, thick + 0.003f), hyMid + 0.042f, doorPtZ(hU1, thick + 0.003f),
                doorPtX(hU0, thick + 0.003f), hyMid + 0.042f, doorPtZ(hU0, thick + 0.003f),
                0.10f, 0.11f, 0.13f, 1f, doorBias - 0.004f
            )
        }

        // 2C. Side Hose Swivel Manifold + Side Holster Boot + Sculpted Multi-Part 3D Nozzle + Tension-Aware Rubber Hose
        if (refuel.showFuelHose) {
            val sx = refuel.hoseStartX
            val sy = refuel.hoseStartY
            val sz = refuel.hoseStartZ
            val hx = refuel.nozzleHolsterX
            val hy = refuel.nozzleHolsterY
            val hz = refuel.nozzleHolsterZ

            // Station world basis for side hardware orientation
            val stCos = refuel.activeStationCosYaw
            val stSin = refuel.activeStationSinYaw
            val sideSign = if (refuel.selectedFuelType == FuelType.DIESEL) -1f else 1f
            // Outward side normal of dispenser in world space (local ±X -> world (±cosY, ±sinY))
            val outX = sideSign * stCos
            val outZ = sideSign * stSin

            // 1. Dispenser Side Hose Swivel Manifold Elbow (mounted cleanly on side chassis, NEVER on LCD meter!)
            emitTubeSegment(
                sx - outX * 0.055f, sy, sz - outZ * 0.055f,
                sx + outX * 0.015f, sy, sz + outZ * 0.015f,
                0.036f, 0.032f, 0.72f, 0.76f, 0.80f, sides = 8, capEnds = true
            )
            emitTubeSegment(
                sx, sy + 0.015f, sz,
                sx, sy - 0.075f, sz,
                0.030f, 0.025f, 0.78f, 0.65f, 0.30f, sides = 8, capEnds = true
            )

            // 2. Dispenser Side Nozzle Holster Pocket Cradle (mounted on side chassis below outlet)
            emitTubeSegment(
                hx - outX * 0.035f, hy - 0.06f, hz - outZ * 0.035f,
                hx - outX * 0.010f, hy + 0.18f, hz - outZ * 0.010f,
                0.055f, 0.048f, 0.14f, 0.15f, 0.17f, sides = 8, capEnds = true
            )

            // 3. Determine Nozzle Grip Frame (either docked in side holster OR rigidly parented to worker's RightHand palm!)
            val gripX: Float
            val gripY: Float
            val gripZ: Float
            var aimX: Float
            var aimY: Float
            var aimZ: Float
            var nUpX: Float
            var nUpY: Float
            var nUpZ: Float

            if (refuel.nozzleDockedInHolder || !hasActiveNozzleHand) {
                gripX = hx
                gripY = hy
                gripZ = hz
                // Docked orientation: spout tucked upward-inward into holster pocket, handle facing outward for hand grab
                aimX = -outX * 0.24f
                aimY = 0.97f
                aimZ = -outZ * 0.24f
                val aLen = sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ).coerceAtLeast(0.001f)
                aimX /= aLen; aimY /= aLen; aimZ /= aLen
                nUpX = outX * 0.96f
                nUpY = 0.24f
                nUpZ = outZ * 0.96f
            } else {
                gripX = activeNozzleHandX
                gripY = activeNozzleHandY
                gripZ = activeNozzleHandZ
                aimX = activeNozzleAimX
                aimY = activeNozzleAimY
                aimZ = activeNozzleAimZ
                val aLen = sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ).coerceAtLeast(0.001f)
                aimX /= aLen; aimY /= aLen; aimZ /= aLen
                nUpX = activeNozzleUpX
                nUpY = activeNozzleUpY
                nUpZ = activeNozzleUpZ
            }
            // Orthonormalize nozzle up vector perpendicular to aim direction
            val upDotAim = nUpX * aimX + nUpY * aimY + nUpZ * aimZ
            nUpX -= aimX * upDotAim
            nUpY -= aimY * upDotAim
            nUpZ -= aimZ * upDotAim
            val nUpLen = sqrt(nUpX * nUpX + nUpY * nUpY + nUpZ * nUpZ).coerceAtLeast(0.001f)
            nUpX /= nUpLen; nUpY /= nUpLen; nUpZ /= nUpLen

            // 4. Multi-Part Sculpted 3D Fuel Nozzle (zero floating, centered inside wrapped palm!)
            val bootR = if (refuel.selectedFuelType == FuelType.DIESEL) 0.94f else 0.10f
            val bootG = if (refuel.selectedFuelType == FuelType.DIESEL) 0.68f else 0.78f
            val bootB = if (refuel.selectedFuelType == FuelType.DIESEL) 0.12f else 0.34f

            // Bottom brass swivel nut where rubber hose terminates continuously
            val swivelX = gripX - nUpX * 0.108f - aimX * 0.022f
            val swivelY = gripY - nUpY * 0.108f - aimY * 0.022f
            val swivelZ = gripZ - nUpZ * 0.108f - aimZ * 0.022f

            // Handle bottom -> handle top (passes squarely through the worker's cupped palm & fingers)
            val hBotX = gripX - nUpX * 0.092f - aimX * 0.018f
            val hBotY = gripY - nUpY * 0.092f - aimY * 0.018f
            val hBotZ = gripZ - nUpZ * 0.092f - aimZ * 0.018f
            val hTopX = gripX + nUpX * 0.072f + aimX * 0.014f
            val hTopY = gripY + nUpY * 0.072f + aimY * 0.014f
            val hTopZ = gripZ + nUpZ * 0.072f + aimZ * 0.014f

            // Brass swivel coupler at base of handle
            emitTubeSegment(
                swivelX - nUpX * 0.025f, swivelY - nUpY * 0.025f, swivelZ - nUpZ * 0.025f,
                hBotX, hBotY, hBotZ,
                0.024f, 0.026f, 0.78f, 0.65f, 0.30f, sides = 8, capEnds = true
            )
            // Textured dark pistol grip handle (inside palm)
            emitTubeSegment(
                hBotX, hBotY, hBotZ,
                hTopX, hTopY, hTopZ,
                0.021f, 0.022f, 0.12f, 0.13f, 0.15f, sides = 8, capEnds = true
            )
            // Trigger lever & front protective trigger guard loop
            val trigBotX = gripX - nUpX * 0.060f + aimX * 0.042f
            val trigBotY = gripY - nUpY * 0.060f + aimY * 0.042f
            val trigBotZ = gripZ - nUpZ * 0.060f + aimZ * 0.042f
            val trigTopX = gripX + nUpX * 0.052f + aimX * 0.056f
            val trigTopY = gripY + nUpY * 0.052f + aimY * 0.056f
            val trigTopZ = gripZ + nUpZ * 0.052f + aimZ * 0.056f
            emitTubeSegment(
                trigBotX, trigBotY, trigBotZ,
                trigTopX, trigTopY, trigTopZ,
                0.008f, 0.008f, 0.76f, 0.80f, 0.85f, sides = 6
            )
            emitTubeSegment(
                hBotX, hBotY, hBotZ,
                gripX - nUpX * 0.088f + aimX * 0.068f,
                gripY - nUpY * 0.088f + aimY * 0.068f,
                gripZ - nUpZ * 0.088f + aimZ * 0.068f,
                0.009f, 0.009f, 0.15f, 0.16f, 0.18f, sides = 6
            )
            emitTubeSegment(
                gripX - nUpX * 0.088f + aimX * 0.068f,
                gripY - nUpY * 0.088f + aimY * 0.068f,
                gripZ - nUpZ * 0.088f + aimZ * 0.068f,
                gripX + nUpX * 0.065f + aimX * 0.088f,
                gripY + nUpY * 0.065f + aimY * 0.088f,
                gripZ + nUpZ * 0.065f + aimZ * 0.088f,
                0.009f, 0.009f, 0.15f, 0.16f, 0.18f, sides = 6
            )

            // Color-coded upper valve body boot (Amber Diesel / Emerald Petrol)
            val bodyRearX = gripX + nUpX * 0.072f - aimX * 0.042f
            val bodyRearY = gripY + nUpY * 0.072f - aimY * 0.042f
            val bodyRearZ = gripZ + nUpZ * 0.072f - aimZ * 0.042f
            val bodyFrontX = gripX + nUpX * 0.072f + aimX * 0.118f
            val bodyFrontY = gripY + nUpY * 0.072f + aimY * 0.118f
            val bodyFrontZ = gripZ + nUpZ * 0.072f + aimZ * 0.118f
            emitTubeSegment(
                bodyRearX, bodyRearY, bodyRearZ,
                bodyFrontX, bodyFrontY, bodyFrontZ,
                0.036f, 0.033f, bootR, bootG, bootB, sides = 8, capEnds = true
            )

            // Rubber splash-guard bellows collar at base of chrome spout
            val collarEndX = bodyFrontX + aimX * 0.034f - nUpX * 0.006f
            val collarEndY = bodyFrontY + aimY * 0.034f - nUpY * 0.006f
            val collarEndZ = bodyFrontZ + aimZ * 0.034f - nUpZ * 0.006f
            emitTubeSegment(
                bodyFrontX, bodyFrontY, bodyFrontZ,
                collarEndX, collarEndY, collarEndZ,
                0.027f, 0.020f, 0.13f, 0.14f, 0.16f, sides = 8
            )

            // Angled polished chrome fuel spout extending to P_tip = grip + aim * 0.340 + up * 0.030
            // (visibly enters 11 cm deep inside the dark circular bus fuel tank opening!)
            val spoutTipX = gripX + aimX * 0.340f + nUpX * 0.030f
            val spoutTipY = gripY + aimY * 0.340f + nUpY * 0.030f
            val spoutTipZ = gripZ + aimZ * 0.340f + nUpZ * 0.030f
            emitTubeSegment(
                collarEndX, collarEndY, collarEndZ,
                spoutTipX, spoutTipY, spoutTipZ,
                0.015f, 0.013f, 0.84f, 0.87f, 0.91f, sides = 8, capEnds = true
            )

            // 5. Continuous Tension-Aware Cubic-Bezier Black Rubber Fuel Hose (Pump Side Swivel -> Nozzle Swivel)
            val p0x = sx; val p0y = sy - 0.060f; val p0z = sz
            val p3x = swivelX; val p3y = swivelY; val p3z = swivelZ
            val span = sqrt((p3x - p0x) * (p3x - p0x) + (p3y - p0y) * (p3y - p0y) + (p3z - p0z) * (p3z - p0z))
            val slack = (3.40f - span).coerceIn(0.18f, 2.25f)

            // P1 exits straight downward from the pump side swivel outlet
            val p1x = p0x + outX * 0.06f
            val p1y = (p0y - (0.50f + slack * 0.38f)).coerceAtLeast(refuel.activeStationBaseY + 0.10f)
            val p1z = p0z + outZ * 0.06f

            // P2 approaches smoothly from below the nozzle handle swivel nut
            val p2x = p3x - nUpX * (0.34f + slack * 0.16f)
            val p2y = (p3y - nUpY * (0.34f + slack * 0.16f) - (0.18f + slack * 0.24f)).coerceAtLeast(refuel.activeStationBaseY + 0.08f)
            val p2z = p3z - nUpZ * (0.34f + slack * 0.16f)

            val isFlowing = (refuel.phase == RefuelPhase.FUELING_IN_PROGRESS)
            val segments = 16
            var prevX = p0x; var prevY = p0y; var prevZ = p0z
            val minFloorY = refuel.activeStationBaseY + 0.065f
            for (seg in 1..segments) {
                val t = seg.toFloat() / segments
                val omt = 1f - t
                val b0 = omt * omt * omt
                val b1 = 3f * omt * omt * t
                val b2 = 3f * omt * t * t
                val b3 = t * t * t
                val cx = b0 * p0x + b1 * p1x + b2 * p2x + b3 * p3x
                val cy = (b0 * p0y + b1 * p1y + b2 * p2y + b3 * p3y).coerceAtLeast(minFloorY)
                val cz = b0 * p0z + b1 * p1z + b2 * p2z + b3 * p3z
                val flowPulse = if (isFlowing) {
                    0.0014f * sin(state.simTimeSec * 16f - t * 14f)
                } else {
                    0f
                }
                val rHose = 0.022f + flowPulse
                emitTubeSegment(prevX, prevY, prevZ, cx, cy, cz, rHose, rHose, 0.10f, 0.11f, 0.13f, sides = 8)
                prevX = cx; prevY = cy; prevZ = cz
            }
        }

        if (cursor > 0) {
            hoseDynamicBuf.clear()
            hoseDynamicBuf.put(hoseDynamicFloats, 0, cursor)
            hoseDynamicBuf.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, cursor * 4, hoseDynamicBuf, GLES30.GL_STREAM_DRAW)
            drawStaticColorVbo(
                vbo = dynamicColorVbo,
                vertexCount = cursor / 8,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
        }

        // 3. Render Live Glowing Backlit 3D Digital LED Fuel Dispenser Pump Meter directly on the active 3D pump!
        if (refuel.stationId.isNotEmpty()) {
            val stDx = refuel.activeStationCx - camEye.x
            val stDz = refuel.activeStationCz - camEye.z
            if (stDx * stDx + stDz * stDz <= 90f * 90f) {
                drawLive3DPumpMeterOnDispenser(
                    refuel = refuel,
                    simTimeSec = state.simTimeSec,
                    camEye = camEye,
                    right = right,
                    up = up,
                    fwd = fwd,
                    projScaleX = projScaleX,
                    projScaleY = projScaleY,
                    depthA = depthA,
                    depthB = depthB
                )
            }
        }
    }

    private fun drawLive3DPumpMeterOnDispenser(
        refuel: RefuelLiveState,
        simTimeSec: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        val litBucket = (refuel.dispensedLiters * 25f).toInt()
        val pulse = if (refuel.phase == RefuelPhase.FUELING_IN_PROGRESS) ((simTimeSec * 4f).toInt() and 1) else 0
        val meterKey = "${refuel.selectedFuelType.name}_${refuel.phase.name}_${litBucket}_$pulse"
        if (pumpMeterTexId == 0 || meterKey != lastPumpMeterKey) {
            updatePumpMeterTexture(refuel, pulse == 1)
            lastPumpMeterKey = meterKey
        }
        if (pumpMeterTexId == 0 || signboardProg == 0) return

        val cosY = refuel.activeStationCosYaw
        val sinY = refuel.activeStationSinYaw
        val stCx = refuel.activeStationCx
        val stCy = refuel.activeStationBaseY
        val stCz = refuel.activeStationCz
        val dispX = refuel.activeDispenserLocalX
        val dispZ = refuel.activeDispenserLocalZ

        fun stToWorld(lx: Float, ly: Float, lz: Float): Vec3 = Vec3(
            x = stCx + lx * cosY - lz * sinY,
            y = stCy + ly,
            z = stCz + lx * sinY + lz * cosY
        )

        // EXACT GLB Pump_Front built-in LCD cavity bounds (Indian_Fuel_Station.glb):
        //   Pump_Front spans X=[dispX-0.40..dispX+0.40], Y=[0.44..1.92], Z=[dispZ-0.244..dispZ+0.244]
        //   Built-in dark LCD window inside Pump_Front spans normX=[0.094..0.906], normY=[0.198..0.490]
        //   => X=[dispX-0.326..dispX+0.326], Y=[1.195..1.627], recessed FLUSH at Z=dispZ ± 0.247m!
        val yBot = 1.195f
        val yTop = 1.627f
        val halfW = 0.326f
        val zFace = 0.247f

        // Left-to-right UV vertex order verified for left-handed right = (+fwd.z, 0, -fwd.x):
        //   -Z face (viewed from -Z looking +Z, screen-right = +X): LEFT = dispX - halfW (u=0), RIGHT = dispX + halfW (u=1)
        //   +Z face (viewed from +Z looking -Z, screen-right = -X): LEFT = dispX + halfW (u=0), RIGHT = dispX - halfW (u=1)
        val faces = arrayOf(
            arrayOf(
                stToWorld(dispX - halfW, yBot, dispZ - zFace),
                stToWorld(dispX + halfW, yBot, dispZ - zFace),
                stToWorld(dispX + halfW, yTop, dispZ - zFace),
                stToWorld(dispX - halfW, yTop, dispZ - zFace)
            ),
            arrayOf(
                stToWorld(dispX + halfW, yBot, dispZ + zFace),
                stToWorld(dispX - halfW, yBot, dispZ + zFace),
                stToWorld(dispX - halfW, yTop, dispZ + zFace),
                stToWorld(dispX + halfW, yTop, dispZ + zFace)
            )
        )

        GLES30.glUseProgram(signboardProg)
        setCameraUniforms(signboardCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)
        GLES30.glUniform1i(uSignTexLoc, 0)
        GLES30.glUniform1f(uSignBiasLoc, -0.008f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pumpMeterTexId)

        val stride = 5 * 4
        for (f in faces) {
            val v0 = f[0]; val v1 = f[1]; val v2 = f[2]; val v3 = f[3]
            var c = 0
            // Triangle 1: v0, v1, v2
            signboardQuadFloats[c++] = v0.x; signboardQuadFloats[c++] = v0.y; signboardQuadFloats[c++] = v0.z; signboardQuadFloats[c++] = 0f; signboardQuadFloats[c++] = 1f
            signboardQuadFloats[c++] = v1.x; signboardQuadFloats[c++] = v1.y; signboardQuadFloats[c++] = v1.z; signboardQuadFloats[c++] = 1f; signboardQuadFloats[c++] = 1f
            signboardQuadFloats[c++] = v2.x; signboardQuadFloats[c++] = v2.y; signboardQuadFloats[c++] = v2.z; signboardQuadFloats[c++] = 1f; signboardQuadFloats[c++] = 0f
            // Triangle 2: v0, v2, v3
            signboardQuadFloats[c++] = v0.x; signboardQuadFloats[c++] = v0.y; signboardQuadFloats[c++] = v0.z; signboardQuadFloats[c++] = 0f; signboardQuadFloats[c++] = 1f
            signboardQuadFloats[c++] = v2.x; signboardQuadFloats[c++] = v2.y; signboardQuadFloats[c++] = v2.z; signboardQuadFloats[c++] = 1f; signboardQuadFloats[c++] = 0f
            signboardQuadFloats[c++] = v3.x; signboardQuadFloats[c++] = v3.y; signboardQuadFloats[c++] = v3.z; signboardQuadFloats[c++] = 0f; signboardQuadFloats[c++] = 0f

            signboardQuadBuf.clear()
            signboardQuadBuf.put(signboardQuadFloats, 0, c)
            signboardQuadBuf.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, signboardDynamicVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, c * 4, signboardQuadBuf, GLES30.GL_STREAM_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * 4)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun updatePumpMeterTexture(refuel: RefuelLiveState, pulseOn: Boolean) {
        val bmp = pumpMeterBitmap ?: Bitmap.createBitmap(
            FuelStationPremiumSignageBaker.PUMP_METER_W,
            FuelStationPremiumSignageBaker.PUMP_METER_H,
            Bitmap.Config.ARGB_8888
        ).also {
            pumpMeterBitmap = it
            pumpMeterCanvas = Canvas(it)
        }

        FuelStationPremiumSignageBaker.renderLivePumpMeter(bmp, refuel, pulseOn)

        if (pumpMeterTexId == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            pumpMeterTexId = ids[0]
            if (pumpMeterTexId != 0) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pumpMeterTexId)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
                applyAnisotropicFiltering(GLES30.GL_TEXTURE_2D)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            }
        } else {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pumpMeterTexId)
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bmp)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }
    }

    private fun drawShoranurRailwayTracksAndTrains(
        simTimeSec: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        val inShoranurView = camEye.x in -520f..560f && camEye.z in -580f..3750f
        val inOllurRobView = camEye.x in 520f..1450f && camEye.z in -1820f..-1020f
        val inMuthuvaraLcView = camEye.x in -1180f..40f && camEye.z in 240f..980f
        if (!inShoranurView && !inOllurRobView && !inMuthuvaraLcView) return

        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(instancedGDriveProg)
        setCameraUniforms(instancedCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, shoranurRailwayAtlasTexArrayId)
        GLES30.glUniform1i(uInstTexArrayLoc, 0)

        val vertStride = 9 * 4
        val instStride = 6 * 4

        fun drawInstancedModel(gm: GpuInstancedModel, instanceCount: Int) {
            if (instanceCount <= 0 || gm.indexCount <= 0) return
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.vboId)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, vertStride, 0)
            GLES30.glVertexAttribDivisor(0, 0)

            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, vertStride, 3 * 4)
            GLES30.glVertexAttribDivisor(1, 0)

            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, vertStride, 6 * 4)
            GLES30.glVertexAttribDivisor(2, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.instanceVboId)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, instStride, 0)
            GLES30.glVertexAttribDivisor(3, 1)

            GLES30.glEnableVertexAttribArray(4)
            GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, instStride, 3 * 4)
            GLES30.glVertexAttribDivisor(4, 1)

            GLES30.glEnableVertexAttribArray(5)
            GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, instStride, 5 * 4)
            GLES30.glVertexAttribDivisor(5, 1)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, gm.iboId)
            GLES30.glDrawElementsInstanced(
                GLES30.GL_TRIANGLES,
                gm.indexCount,
                GLES30.GL_UNSIGNED_SHORT,
                0,
                instanceCount
            )
        }

        // 1. Static broad-gauge 2-track corridor tiles (`track_indian`) — Spatial Chunk Culled!
        gpuRailwayStaticTrack?.let { trackGm ->
            if (trackGm.chunks.isNotEmpty() && trackGm.indexCount > 0) {
                var bound = false
                val ex = camEye.x; val ez = camEye.z
                val fx = fwd.x; val fz = fwd.z
                val rx = right.x; val rz = right.z
                for (cIdx in 0 until trackGm.chunks.size) {
                    val c = trackGm.chunks[cIdx]
                    val dx = c.centerX - ex; val dz = c.centerZ - ez
                    val lim = trackGm.maxDrawDist + c.radius
                    if (dx * dx + dz * dz > lim * lim) continue
                    val camZ = dx * fx + dz * fz
                    if (camZ < -(c.radius + 25f)) continue
                    val camX = abs(dx * rx + dz * rz)
                    if (camZ > 0f && camX > camZ * 1.42f + c.radius + 28f) continue
                    if (!bound) {
                        bound = true
                        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, trackGm.vboId)
                        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, vertStride, 0); GLES30.glVertexAttribDivisor(0, 0)
                        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, vertStride, 3 * 4); GLES30.glVertexAttribDivisor(1, 0)
                        GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, vertStride, 6 * 4); GLES30.glVertexAttribDivisor(2, 0)
                        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, trackGm.instanceVboId)
                        GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribDivisor(3, 1)
                        GLES30.glEnableVertexAttribArray(4); GLES30.glVertexAttribDivisor(4, 1)
                        GLES30.glEnableVertexAttribArray(5); GLES30.glVertexAttribDivisor(5, 1)
                        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, trackGm.iboId)
                    }
                    val byteOff = c.firstVertex * instStride
                    GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, instStride, byteOff)
                    GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, instStride, byteOff + 3 * 4)
                    GLES30.glVertexAttribPointer(5, 1, GLES30.GL_FLOAT, false, instStride, byteOff + 5 * 4)
                    GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, trackGm.indexCount, GLES30.GL_UNSIGNED_SHORT, 0, c.vertexCount)
                }
            }
        }

        // 2. Animated 2-track trains:
        //    Track 1 (Northbound Thrissur -> Shoranur): WAG-9 + 7x Coach 1
        //    Track 2 (Southbound Shoranur -> Thrissur): WAG-9 + 7x Coach 2
        trainInstancesScratch.clear()
        ShoranurRailwaySystem.appendAnimatedTrainInstances(simTimeSec, trainInstancesScratch)

        val dynamicModelIds = intArrayOf(
            ShoranurRailwaySystem.MODEL_WAG9,
            ShoranurRailwaySystem.MODEL_COACH_1,
            ShoranurRailwaySystem.MODEL_COACH_2
        )
        for (mId in dynamicModelIds) {
            val gm = gpuRailwayDynamicModels[mId] ?: continue
            var count = 0
            for (i in 0 until trainInstancesScratch.size) {
                val ins = trainInstancesScratch[i]
                if (ins.modelIndex != mId) continue
                val base = count * 6
                if (base + 6 > trainInstFloatsScratch.size) break
                trainInstFloatsScratch[base] = ins.cx
                trainInstFloatsScratch[base + 1] = ins.baseY
                trainInstFloatsScratch[base + 2] = ins.cz
                trainInstFloatsScratch[base + 3] = ins.yawRad
                trainInstFloatsScratch[base + 4] = ins.scale
                trainInstFloatsScratch[base + 5] = ins.pitchRad
                count++
            }
            if (count > 0) {
                trainInstBuf.clear()
                trainInstBuf.put(trainInstFloatsScratch, 0, count * 6)
                trainInstBuf.position(0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.instanceVboId)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, count * 6 * 4, trainInstBuf, GLES30.GL_STREAM_DRAW)
                drawInstancedModel(gm, count)
            }
        }

        GLES30.glVertexAttribDivisor(3, 0)
        GLES30.glVertexAttribDivisor(4, 0)
        GLES30.glVertexAttribDivisor(5, 0)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(4)
        GLES30.glDisableVertexAttribArray(5)
        GLES30.glVertexAttrib1f(5, 0.0f)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
    }

    private fun drawTexturedSignboards3D(
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        GLES30.glUseProgram(signboardProg)
        setCameraUniforms(signboardCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)
        GLES30.glUniform1i(uSignTexLoc, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1.5f, -2.0f)

        val maxDistSq = 280f * 280f
        val stride = 5 * 4

        for (i in 0 until signboardEntries.size) {
            val entry = signboardEntries[i]
            val dx = entry.cx - camEye.x
            val dy = entry.cy - camEye.y
            val dz = entry.cz - camEye.z
            val distSq = dx * dx + dz * dz
            if (distSq > maxDistSq) continue
            val camZ = dx * fwd.x + dy * fwd.y + dz * fwd.z
            if (camZ < -28f) continue
            val camX = abs(dx * right.x + dz * right.z)
            if (camZ > 0f && camX > camZ * 1.45f + 26f) continue

            // Determine whether camera is viewing the front or back of this signboard
            // Matches ThrissurSignboardRenderer.kt (det = c0 · (e1 × e2) < 0f is back)
            val dot = dx * entry.nx + dy * entry.ny + dz * entry.nz
            val isBack = dot < 0f
            if (isBack && !entry.spec.isDoubleSided) continue

            val texId = getOrUploadSignboardTexture(entry.spec, isBack)
            if (texId == 0) continue

            val v0 = if (isBack) entry.v1 else entry.v0
            val v1 = if (isBack) entry.v0 else entry.v1
            val v2 = if (isBack) entry.v3 else entry.v2
            val v3 = if (isBack) entry.v2 else entry.v3

            // Exact Sutherland-Hodgman near-plane clipping (cz >= 0.28f) with UV interpolation so
            // wide 40m rooftop signs & corrugated roofs NEVER disappear when straddling the camera plane!
            signClipInX[0] = v0.x; signClipInY[0] = v0.y; signClipInZ[0] = v0.z; signClipInU[0] = 0f; signClipInV[0] = 1f
            signClipInX[1] = v1.x; signClipInY[1] = v1.y; signClipInZ[1] = v1.z; signClipInU[1] = 1f; signClipInV[1] = 1f
            signClipInX[2] = v2.x; signClipInY[2] = v2.y; signClipInZ[2] = v2.z; signClipInU[2] = 1f; signClipInV[2] = 0f
            signClipInX[3] = v3.x; signClipInY[3] = v3.y; signClipInZ[3] = v3.z; signClipInU[3] = 0f; signClipInV[3] = 0f

            val clipNearZ = 0.28f
            var anyIn = false
            for (k in 0 until 4) {
                val czk = (signClipInX[k] - camEye.x) * fwd.x +
                    (signClipInY[k] - camEye.y) * fwd.y +
                    (signClipInZ[k] - camEye.z) * fwd.z
                signClipInCz[k] = czk
                if (czk >= clipNearZ) anyIn = true
            }
            if (!anyIn) continue

            var outCount = 0
            for (k in 0 until 4) {
                val prev = (k + 3) and 3
                val prevCz = signClipInCz[prev]
                val currCz = signClipInCz[k]
                val prevIn = prevCz >= clipNearZ
                val currIn = currCz >= clipNearZ
                if (prevIn != currIn) {
                    val t = ((clipNearZ - prevCz) / (currCz - prevCz)).coerceIn(0f, 1f)
                    signClipOutX[outCount] = signClipInX[prev] + (signClipInX[k] - signClipInX[prev]) * t
                    signClipOutY[outCount] = signClipInY[prev] + (signClipInY[k] - signClipInY[prev]) * t
                    signClipOutZ[outCount] = signClipInZ[prev] + (signClipInZ[k] - signClipInZ[prev]) * t
                    signClipOutU[outCount] = signClipInU[prev] + (signClipInU[k] - signClipInU[prev]) * t
                    signClipOutV[outCount] = signClipInV[prev] + (signClipInV[k] - signClipInV[prev]) * t
                    outCount++
                }
                if (currIn) {
                    signClipOutX[outCount] = signClipInX[k]
                    signClipOutY[outCount] = signClipInY[k]
                    signClipOutZ[outCount] = signClipInZ[k]
                    signClipOutU[outCount] = signClipInU[k]
                    signClipOutV[outCount] = signClipInV[k]
                    outCount++
                }
            }
            if (outCount < 3) continue

            val triCount = outCount - 2
            var fCursor = 0
            for (tIdx in 0 until triCount) {
                val i1 = tIdx + 1
                val i2 = tIdx + 2
                signboardQuadFloats[fCursor++] = signClipOutX[0]; signboardQuadFloats[fCursor++] = signClipOutY[0]; signboardQuadFloats[fCursor++] = signClipOutZ[0]
                signboardQuadFloats[fCursor++] = signClipOutU[0]; signboardQuadFloats[fCursor++] = signClipOutV[0]
                signboardQuadFloats[fCursor++] = signClipOutX[i1]; signboardQuadFloats[fCursor++] = signClipOutY[i1]; signboardQuadFloats[fCursor++] = signClipOutZ[i1]
                signboardQuadFloats[fCursor++] = signClipOutU[i1]; signboardQuadFloats[fCursor++] = signClipOutV[i1]
                signboardQuadFloats[fCursor++] = signClipOutX[i2]; signboardQuadFloats[fCursor++] = signClipOutY[i2]; signboardQuadFloats[fCursor++] = signClipOutZ[i2]
                signboardQuadFloats[fCursor++] = signClipOutU[i2]; signboardQuadFloats[fCursor++] = signClipOutV[i2]
            }

            signboardQuadBuf.clear()
            signboardQuadBuf.put(signboardQuadFloats, 0, fCursor)
            signboardQuadBuf.position(0)

            GLES30.glUniform1f(uSignBiasLoc, (entry.biasDist * 0.012f).coerceIn(-0.08f, 0.04f))
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, signboardDynamicVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fCursor * 4, signboardQuadBuf, GLES30.GL_STREAM_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * 4)

            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, triCount * 3)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
    }

    private fun getOrUploadSignboardTexture(spec: SignboardQuadSpec, isBack: Boolean): Int {
        val key = if (isBack && spec.renderBackBitmap != null) "${spec.cacheKey}_BACK" else spec.cacheKey
        signboardTexIds[key]?.let { return it }

        val bmp = SignboardTextureCache.getOrCreate(spec, isBack)
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val tid = texIds[0]
        if (tid == 0) return 0

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tid)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        signboardTexIds[key] = tid
        return tid
    }

    /**
     * Uploads the Hino RM2 3D Bus mesh ONCE into two static GPU VBOs (opaque coachwork + transparent glass),
     * completely eliminating the per-frame CPU loop over 30,000+ polygons and multi-MB glBufferData uploads!
     * Vertex layout (13 floats = 52 bytes):
     *   loc 0: aLocalPos   (vec3)
     *   loc 1: aLocalNorm  (vec3)
     *   loc 2: aBaseColor  (vec4)
     *   loc 3: aPartParams (vec3: partId, doorFlag [0=normal, 1=doorExt, 2=doorInt], gpuBias)
     */
    private fun ensureBusStaticVbos(mesh: List<HinoRealMeshPoly>) {
        if (builtBusMeshSize == mesh.size && busStaticOpaqueVbo != 0) return
        if (mesh.isEmpty()) return

        var opVerts = 0
        var glVerts = 0
        for (i in 0 until mesh.size) {
            val p = mesh[i]
            val verts = if (p.vertCount == 4) 6 else 3
            val isTrans = p.partId == 13 || p.alpha < 0.96f
            val mult = if (p.isDoorLeaf) 2 else 1
            if (isTrans) glVerts += verts * mult else opVerts += verts * mult
        }

        val ids = IntArray(2)
        if (busStaticOpaqueVbo == 0 || busStaticGlassVbo == 0) {
            GLES30.glGenBuffers(2, ids, 0)
            if (busStaticOpaqueVbo == 0) busStaticOpaqueVbo = ids[0]
            if (busStaticGlassVbo == 0) busStaticGlassVbo = ids[1]
        }

        fun uploadBusPass(vboId: Int, totalVerts: Int, wantTransparent: Boolean): Int {
            if (totalVerts <= 0) return 0
            val totalBytes = totalVerts * 13 * 4
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, totalBytes, null, GLES30.GL_STATIC_DRAW)

            val chunkVerts = 2048
            val chunkFloats = FloatArray(chunkVerts * 13)
            val chunkBuf = ByteBuffer.allocateDirect(chunkFloats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            var c = 0
            var byteOffset = 0

            fun flush() {
                if (c <= 0) return
                chunkBuf.clear()
                chunkBuf.put(chunkFloats, 0, c)
                chunkBuf.position(0)
                GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, byteOffset, c * 4, chunkBuf)
                byteOffset += c * 4
                c = 0
            }

            fun putVert(
                x: Float, y: Float, z: Float,
                nx: Float, ny: Float, nz: Float,
                r: Float, g: Float, b: Float, a: Float,
                pid: Float, doorFlag: Float, bias: Float
            ) {
                chunkFloats[c++] = x; chunkFloats[c++] = y; chunkFloats[c++] = z
                chunkFloats[c++] = nx; chunkFloats[c++] = ny; chunkFloats[c++] = nz
                chunkFloats[c++] = r; chunkFloats[c++] = g; chunkFloats[c++] = b; chunkFloats[c++] = a
                chunkFloats[c++] = pid; chunkFloats[c++] = doorFlag; chunkFloats[c++] = bias
            }

            fun emitPoly(p: HinoRealMeshPoly, doorFlag: Float, bias: Float, invertInterior: Boolean) {
                val needed = if (p.vertCount == 4) 6 * 13 else 3 * 13
                if (c + needed > chunkFloats.size) flush()
                val pidF = p.partId.toFloat()
                val nSign = if (invertInterior) -1f else 1f
                val useDarkInt = invertInterior && p.partId != 13
                val r0 = if (useDarkInt) 0.15f else p.r0; val g0 = if (useDarkInt) 0.17f else p.g0; val b0 = if (useDarkInt) 0.21f else p.b0
                val r1 = if (useDarkInt) 0.15f else p.r1; val g1 = if (useDarkInt) 0.17f else p.g1; val b1 = if (useDarkInt) 0.21f else p.b1
                val r2 = if (useDarkInt) 0.15f else p.r2; val g2 = if (useDarkInt) 0.17f else p.g2; val b2 = if (useDarkInt) 0.21f else p.b2
                val r3 = if (useDarkInt) 0.15f else p.r3; val g3 = if (useDarkInt) 0.17f else p.g3; val b3 = if (useDarkInt) 0.21f else p.b3
                val a = p.alpha

                putVert(p.x0, p.y0, p.z0, p.nx0 * nSign, p.ny0 * nSign, p.nz0 * nSign, r0, g0, b0, a, pidF, doorFlag, bias)
                putVert(p.x1, p.y1, p.z1, p.nx1 * nSign, p.ny1 * nSign, p.nz1 * nSign, r1, g1, b1, a, pidF, doorFlag, bias)
                putVert(p.x2, p.y2, p.z2, p.nx2 * nSign, p.ny2 * nSign, p.nz2 * nSign, r2, g2, b2, a, pidF, doorFlag, bias)
                if (p.vertCount == 4) {
                    putVert(p.x0, p.y0, p.z0, p.nx0 * nSign, p.ny0 * nSign, p.nz0 * nSign, r0, g0, b0, a, pidF, doorFlag, bias)
                    putVert(p.x2, p.y2, p.z2, p.nx2 * nSign, p.ny2 * nSign, p.nz2 * nSign, r2, g2, b2, a, pidF, doorFlag, bias)
                    putVert(p.x3, p.y3, p.z3, p.nx3 * nSign, p.ny3 * nSign, p.nz3 * nSign, r3, g3, b3, a, pidF, doorFlag, bias)
                }
            }

            for (i in 0 until mesh.size) {
                val p = mesh[i]
                val pid = p.partId
                val isTrans = pid == 13 || p.alpha < 0.96f
                if (isTrans != wantTransparent) continue
                val gpuBias = when {
                    pid in 1..5 -> -0.015f
                    pid == 13 -> -0.008f
                    p.cullMode == 2 -> 0.018f
                    else -> 0f
                }
                val dFlag = if (p.isDoorLeaf) 1.0f else 0.0f
                emitPoly(p, dFlag, gpuBias, invertInterior = false)
                if (p.isDoorLeaf) {
                    emitPoly(p, 2.0f, gpuBias + 0.005f, invertInterior = true)
                }
            }
            flush()
            return totalVerts
        }

        busStaticOpaqueVertCount = uploadBusPass(busStaticOpaqueVbo, opVerts, wantTransparent = false)
        busStaticGlassVertCount = uploadBusPass(busStaticGlassVbo, glVerts, wantTransparent = true)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        builtBusMeshSize = mesh.size
    }

    /**
     * Renders the Hino RM2 3D Bus at 60 FPS using static GPU VBOs + GPU PBR Shader (zero per-frame CPU mesh loop!).
     */
    private fun drawHinoRm2BusGl(
        state: SimUiState,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        val mesh = hinoMesh
        if (mesh.isEmpty()) return
        ensureBusStaticVbos(mesh)

        val pos = state.busPos
        val yaw = state.busHeadingRad
        val s = sin(yaw)
        val c = cos(yaw)

        val pitchSin = sin(state.bodyPitchRad)
        val pitchCos = cos(state.bodyPitchRad)
        val rollSin = sin(state.bodyRollRad)
        val rollCos = cos(state.bodyRollRad)

        // 1. Lightweight Ground Shadows & Headlamp Light Cones (only 7-9 quads!)
        fun busWorld(localX: Float, localY: Float, localZ: Float): Vec3 = Vec3(
            pos.x + localX * c + localZ * s,
            pos.y + localY,
            pos.z - localX * s + localZ * c
        )
        var shC = 0
        fun emitShadowQuad(v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3, col: Color, bias: Float) {
            if (shC + 48 > busShadowFloats.size) return
            val r = col.red; val g = col.green; val b = col.blue; val a = col.alpha
            busShadowFloats[shC++] = v0.x; busShadowFloats[shC++] = v0.y; busShadowFloats[shC++] = v0.z; busShadowFloats[shC++] = r; busShadowFloats[shC++] = g; busShadowFloats[shC++] = b; busShadowFloats[shC++] = a; busShadowFloats[shC++] = bias
            busShadowFloats[shC++] = v1.x; busShadowFloats[shC++] = v1.y; busShadowFloats[shC++] = v1.z; busShadowFloats[shC++] = r; busShadowFloats[shC++] = g; busShadowFloats[shC++] = b; busShadowFloats[shC++] = a; busShadowFloats[shC++] = bias
            busShadowFloats[shC++] = v2.x; busShadowFloats[shC++] = v2.y; busShadowFloats[shC++] = v2.z; busShadowFloats[shC++] = r; busShadowFloats[shC++] = g; busShadowFloats[shC++] = b; busShadowFloats[shC++] = a; busShadowFloats[shC++] = bias
            busShadowFloats[shC++] = v0.x; busShadowFloats[shC++] = v0.y; busShadowFloats[shC++] = v0.z; busShadowFloats[shC++] = r; busShadowFloats[shC++] = g; busShadowFloats[shC++] = b; busShadowFloats[shC++] = a; busShadowFloats[shC++] = bias
            busShadowFloats[shC++] = v2.x; busShadowFloats[shC++] = v2.y; busShadowFloats[shC++] = v2.z; busShadowFloats[shC++] = r; busShadowFloats[shC++] = g; busShadowFloats[shC++] = b; busShadowFloats[shC++] = a; busShadowFloats[shC++] = bias
            busShadowFloats[shC++] = v3.x; busShadowFloats[shC++] = v3.y; busShadowFloats[shC++] = v3.z; busShadowFloats[shC++] = r; busShadowFloats[shC++] = g; busShadowFloats[shC++] = b; busShadowFloats[shC++] = a; busShadowFloats[shC++] = bias
        }

        emitShadowQuad(busWorld(-0.54f, 0.062f, -7.16f), busWorld(1.98f, 0.062f, -7.16f), busWorld(1.98f, 0.062f, 5.12f), busWorld(-0.54f, 0.062f, 5.12f), Color(0x3405080E), -0.004f)
        emitShadowQuad(busWorld(-1.38f, 0.063f, -6.36f), busWorld(1.38f, 0.063f, -6.36f), busWorld(1.38f, 0.063f, 5.94f), busWorld(-1.38f, 0.063f, 5.94f), Color(0x4E05080E), -0.005f)
        emitShadowQuad(busWorld(-1.18f, 0.064f, -6.18f), busWorld(1.18f, 0.064f, -6.18f), busWorld(1.18f, 0.064f, 5.74f), busWorld(-1.18f, 0.064f, 5.74f), Color(0x9404060A), -0.006f)

        val halfL = 5.80f
        if (state.headlightMode != HeadlightBeamMode.Off) {
            val isHighBeam = state.headlightMode == HeadlightBeamMode.High
            val outerLen = if (isHighBeam) 56f else 31f
            val outerHalfW = if (isHighBeam) 7.4f else 5.2f
            val outerCol = if (isHighBeam) Color(0x3AE0F2FE) else Color(0x26E0F2FE)
            val innerLen = if (isHighBeam) 30f else 15f
            val innerHalfW = if (isHighBeam) 3.8f else 2.6f
            val innerCol = if (isHighBeam) Color(0x4CF8FAFC) else Color(0x32F8FAFC)
            emitShadowQuad(busWorld(-1.10f, 0.075f, halfL + 0.2f), busWorld(1.10f, 0.075f, halfL + 0.2f), busWorld(outerHalfW, 0.075f, halfL + outerLen), busWorld(-outerHalfW, 0.075f, halfL + outerLen), outerCol, -0.008f)
            emitShadowQuad(busWorld(-0.90f, 0.076f, halfL + 0.2f), busWorld(0.90f, 0.076f, halfL + 0.2f), busWorld(innerHalfW, 0.076f, halfL + innerLen), busWorld(-innerHalfW, 0.076f, halfL + innerLen), innerCol, -0.009f)
        }

        if (shC > 0) {
            busShadowDirectBuf.clear()
            busShadowDirectBuf.put(busShadowFloats, 0, shC)
            busShadowDirectBuf.position(0)
            GLES30.glDepthMask(false)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, shC * 4, busShadowDirectBuf, GLES30.GL_STREAM_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            drawStaticColorVbo(dynamicColorVbo, shC / 8, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)
            GLES30.glDepthMask(true)
        }

        // 2. Render Static GPU Bus VBOs via GPU PBR Shader (Opaque Pass + Glass Pass)
        val doorOpenT = state.doorOpenFrac.coerceIn(0f, 1f)
        val popPhase = (doorOpenT / 0.30f).coerceIn(0f, 1f)
        val popEase = popPhase * popPhase * (3f - 2f * popPhase)
        val glidePhase = ((doorOpenT - 0.12f) / 0.88f).coerceIn(0f, 1f)
        val glideEase = glidePhase * glidePhase * (3f - 2f * glidePhase)
        val doorOutX = -0.22f * popEase
        val doorFwdZ = -0.84f * glideEase
        val doorYawRad = glideEase * Math.toRadians(3.5).toFloat()

        val swRotRad = Math.toRadians((-state.steeringWheelAngleDeg).toDouble()).toFloat()
        val brakeActive = state.brakeInput > 0.05f || state.isParkBrakeActive
        val revActive = state.gear == TransmissionGear.Reverse
        val leftBlinkOn = state.blinkerPhase && (state.turnLeftOn || state.hazardsOn)
        val rightBlinkOn = state.blinkerPhase && (state.turnRightOn || state.hazardsOn)
        val sideMarkerRunning = state.headlightsOn && !state.turnLeftOn && !state.turnRightOn && !state.hazardsOn

        GLES30.glUseProgram(hinoBusProg)
        setCameraUniforms(hinoBusCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)
        GLES30.glUniform3f(uBusPosLoc, pos.x, pos.y, pos.z)
        GLES30.glUniform2f(uBusYawCSLoc, c, s)
        GLES30.glUniform4f(uBusPitchRollLoc, pitchCos, pitchSin, rollCos, rollSin)
        GLES30.glUniform4f(uWheelSpinSteerLoc, cos(state.wheelSpinRad), sin(state.wheelSpinRad), cos(state.frontWheelSteerRad), sin(state.frontWheelSteerRad))
        GLES30.glUniform4f(uDoorTransformLoc, doorOutX, doorFwdZ, cos(doorYawRad), sin(doorYawRad))
        GLES30.glUniform1f(uDoorOpenActiveLoc, if (doorOpenT > 0.005f) 1f else 0f)
        GLES30.glUniform2f(uSteerWheelCSLoc, cos(swRotRad), sin(swRotRad))
        GLES30.glUniform4f(
            uLightFlagsLoc,
            if (state.headlightsOn) 1f else 0f,
            if (brakeActive) 1f else 0f,
            if (revActive) 1f else 0f,
            if (leftBlinkOn) 1f else 0f
        )
        GLES30.glUniform2f(
            uLightFlags2Loc,
            if (rightBlinkOn) 1f else 0f,
            if (sideMarkerRunning) 1f else 0f
        )

        val busStride = 13 * 4
        fun drawBusStaticVbo(vboId: Int, vertCount: Int) {
            if (vboId == 0 || vertCount <= 0) return
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, busStride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, busStride, 3 * 4)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, busStride, 6 * 4)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, busStride, 10 * 4)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertCount)
        }

        // Opaque coachwork pass (disable GL_BLEND for zero framebuffer read-modify-write!)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        drawBusStaticVbo(busStaticOpaqueVbo, busStaticOpaqueVertCount)

        // Transparent flush solar glass pass
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        drawBusStaticVbo(busStaticGlassVbo, busStaticGlassVertCount)
        GLES30.glDepthMask(true)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Streams flat triangle vertices directly into a single GPU VBO grouped by spatial grid cells
     * using O(N) time and a tiny ~512KB staging buffer (ZERO duplicate giant FloatArray or ByteBuffer!),
     * eliminating OutOfMemoryError on mobile heaps while giving O(1) frustum culling every frame.
     */
    private fun uploadSpatialChunkedVbo(
        vboId: Int,
        srcFloats: FloatArray,
        floatCount: Int,
        floatsPerVert: Int,
        cellSize: Float
    ): Pair<Int, List<SpatialDrawChunk>> {
        val triFloats = 3 * floatsPerVert
        val triCount = floatCount / triFloats
        if (triCount <= 0) return Pair(0, emptyList())

        val cellToIdx = HashMap<Long, Int>(256)
        var capCells = 256
        var numCells = 0
        var cellHead = IntArray(capCells) { -1 }
        var cellCounts = IntArray(capCells)
        var minX = FloatArray(capCells) { Float.POSITIVE_INFINITY }
        var maxX = FloatArray(capCells) { Float.NEGATIVE_INFINITY }
        var minZ = FloatArray(capCells) { Float.POSITIVE_INFINITY }
        var maxZ = FloatArray(capCells) { Float.NEGATIVE_INFINITY }
        val nextTri = IntArray(triCount)
        val invCell = 1f / cellSize

        // Iterate backward so singly-linked lists preserve exact original forward draw order!
        for (t in triCount - 1 downTo 0) {
            val base = t * triFloats
            val x0 = srcFloats[base]
            val z0 = srcFloats[base + 2]
            val x1 = srcFloats[base + floatsPerVert]
            val z1 = srcFloats[base + floatsPerVert + 2]
            val x2 = srcFloats[base + 2 * floatsPerVert]
            val z2 = srcFloats[base + 2 * floatsPerVert + 2]
            val cx = (x0 + x1 + x2) * (1f / 3f)
            val cz = (z0 + z1 + z2) * (1f / 3f)
            val gx = kotlin.math.floor((cx * invCell).toDouble()).toInt()
            val gz = kotlin.math.floor((cz * invCell).toDouble()).toInt()
            val key = (gx.toLong() shl 32) or (gz.toLong() and 0xFFFFFFFFL)

            var cIdx = cellToIdx[key] ?: -1
            if (cIdx < 0) {
                cIdx = numCells++
                if (cIdx >= capCells) {
                    val newCap = capCells * 2
                    cellHead = cellHead.copyOf(newCap).also { arr ->
                        for (k in capCells until newCap) arr[k] = -1
                    }
                    cellCounts = cellCounts.copyOf(newCap)
                    minX = minX.copyOf(newCap).also { arr -> for (k in capCells until newCap) arr[k] = Float.POSITIVE_INFINITY }
                    maxX = maxX.copyOf(newCap).also { arr -> for (k in capCells until newCap) arr[k] = Float.NEGATIVE_INFINITY }
                    minZ = minZ.copyOf(newCap).also { arr -> for (k in capCells until newCap) arr[k] = Float.POSITIVE_INFINITY }
                    maxZ = maxZ.copyOf(newCap).also { arr -> for (k in capCells until newCap) arr[k] = Float.NEGATIVE_INFINITY }
                    capCells = newCap
                }
                cellToIdx[key] = cIdx
            }

            nextTri[t] = cellHead[cIdx]
            cellHead[cIdx] = t
            cellCounts[cIdx]++

            if (x0 < minX[cIdx]) minX[cIdx] = x0; if (x0 > maxX[cIdx]) maxX[cIdx] = x0
            if (x1 < minX[cIdx]) minX[cIdx] = x1; if (x1 > maxX[cIdx]) maxX[cIdx] = x1
            if (x2 < minX[cIdx]) minX[cIdx] = x2; if (x2 > maxX[cIdx]) maxX[cIdx] = x2
            if (z0 < minZ[cIdx]) minZ[cIdx] = z0; if (z0 > maxZ[cIdx]) maxZ[cIdx] = z0
            if (z1 < minZ[cIdx]) minZ[cIdx] = z1; if (z1 > maxZ[cIdx]) maxZ[cIdx] = z1
            if (z2 < minZ[cIdx]) minZ[cIdx] = z2; if (z2 > maxZ[cIdx]) maxZ[cIdx] = z2
        }

        // Reserve GPU VBO once with zero CPU-side duplicate buffer, then stream via 512KB staging buffer
        val totalBytes = triCount * triFloats * 4
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, totalBytes, null, GLES30.GL_STATIC_DRAW)

        val stageMaxTris = 4096
        val stageFloats = FloatArray(stageMaxTris * triFloats)
        val stageBuf = ByteBuffer.allocateDirect(stageFloats.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        var stageCursor = 0
        var uploadedBytes = 0

        fun flushStage() {
            if (stageCursor <= 0) return
            stageBuf.clear()
            stageBuf.put(stageFloats, 0, stageCursor)
            stageBuf.position(0)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, uploadedBytes, stageCursor * 4, stageBuf)
            uploadedBytes += stageCursor * 4
            stageCursor = 0
        }

        val chunks = ArrayList<SpatialDrawChunk>(numCells)
        var uploadedTris = 0
        for (c in 0 until numCells) {
            val countTris = cellCounts[c]
            if (countTris <= 0) continue
            val firstVertex = uploadedTris * 3
            var t = cellHead[c]
            while (t != -1) {
                if (stageCursor + triFloats > stageFloats.size) flushStage()
                System.arraycopy(srcFloats, t * triFloats, stageFloats, stageCursor, triFloats)
                stageCursor += triFloats
                t = nextTri[t]
            }
            uploadedTris += countTris
            val centerX = (minX[c] + maxX[c]) * 0.5f
            val centerZ = (minZ[c] + maxZ[c]) * 0.5f
            val radius = hypot((maxX[c] - minX[c]) * 0.5f, (maxZ[c] - minZ[c]) * 0.5f)
            chunks.add(
                SpatialDrawChunk(
                    firstVertex = firstVertex,
                    vertexCount = countTris * 3,
                    centerX = centerX,
                    centerZ = centerZ,
                    radius = radius
                )
            )
        }
        flushStage()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        return Pair(uploadedTris * 3, chunks)
    }

    private fun ensureRoadAndLandmarkVbos(roads: List<ThrissurOsmRoad>) {
        if (builtRoadCount == roads.size && roadVbo != 0 && builtLandmarksForRoads == roads.size && landmarkVbo != 0) {
            return
        }
        val prebuilt = ThrissurPrebuiltWorldCache.buildIfNeeded(roads)

        if (builtRoadCount != roads.size || roadVbo == 0) {
            val roadFloats = prebuilt.roadFloats
            if (roadFloats.isNotEmpty()) {
                if (roadVbo == 0) {
                    val ids = IntArray(1)
                    GLES30.glGenBuffers(1, ids, 0)
                    roadVbo = ids[0]
                }
                val (vCount, chunks) = uploadSpatialChunkedVbo(roadVbo, roadFloats, roadFloats.size, 8, 180f)
                roadVertexCount = vCount
                roadChunks = chunks
                builtRoadCount = roads.size
            }
        }

        if (builtLandmarksForRoads != roads.size || landmarkVbo == 0) {
            signboardEntries.clear()
            val signFaces = prebuilt.signboardFaces
            for (i in 0 until signFaces.size) {
                val f = signFaces[i]
                val spec = f.signboard ?: continue
                val ux = f.v1.x - f.v0.x; val uy = f.v1.y - f.v0.y; val uz = f.v1.z - f.v0.z
                val wx = f.v3.x - f.v0.x; val wy = f.v3.y - f.v0.y; val wz = f.v3.z - f.v0.z
                val cxN = uy * wz - uz * wy
                val cyN = uz * wx - ux * wz
                val czN = ux * wy - uy * wx
                val nLen = sqrt(cxN * cxN + cyN * cyN + czN * czN).coerceAtLeast(1e-4f)
                signboardEntries.add(
                    GpuSignboardEntry(
                        v0 = f.v0,
                        v1 = f.v1,
                        v2 = f.v2,
                        v3 = f.v3,
                        cx = (f.v0.x + f.v1.x + f.v2.x + f.v3.x) * 0.25f,
                        cy = (f.v0.y + f.v1.y + f.v2.y + f.v3.y) * 0.25f,
                        cz = (f.v0.z + f.v1.z + f.v2.z + f.v3.z) * 0.25f,
                        nx = cxN / nLen,
                        ny = cyN / nLen,
                        nz = czN / nLen,
                        biasDist = f.biasDist,
                        spec = spec
                    )
                )
            }

            val cursor = prebuilt.landmarkFloatCount
            if (cursor > 0) {
                if (landmarkVbo == 0) {
                    val ids = IntArray(1)
                    GLES30.glGenBuffers(1, ids, 0)
                    landmarkVbo = ids[0]
                }
                val (vCount, chunks) = uploadSpatialChunkedVbo(landmarkVbo, prebuilt.landmarkFloats, cursor, 8, 160f)
                landmarkVertexCount = vCount
                landmarkChunks = chunks
                builtLandmarksForRoads = roads.size
            }
        }

        // Release CPU-side raw geometry arrays now that static VBOs live in GPU VRAM (frees ~100MB+ heap!)
        if (roadVbo != 0 && landmarkVbo != 0 && builtRoadCount == roads.size && builtLandmarksForRoads == roads.size) {
            ThrissurPrebuiltWorldCache.cachedData = null
        }
    }

    private fun ensureAnimatedWaterVbos() {
        if (waterSurfaceVbo != 0 && waterSurfaceVertexCount > 0) return
        val built = ThrissurAnimatedWaterBuilder.buildIfNeeded()
        val fpv = ThrissurAnimatedWaterBuilder.FLOATS_PER_VERTEX

        if (built.surfaceVertexCount > 0) {
            if (waterSurfaceVbo == 0) {
                val ids = IntArray(1)
                GLES30.glGenBuffers(1, ids, 0)
                waterSurfaceVbo = ids[0]
            }
            val (vCount, surfChunks) = uploadSpatialChunkedVbo(
                waterSurfaceVbo, built.surfaceFloats, built.surfaceFloats.size, fpv, 180f
            )
            waterSurfaceVertexCount = vCount
            waterSurfaceChunks = surfChunks
        }

        if (built.mistVertexCount > 0) {
            if (waterMistVbo == 0) {
                val ids = IntArray(1)
                GLES30.glGenBuffers(1, ids, 0)
                waterMistVbo = ids[0]
            }
            val (vCount, mistChunks) = uploadSpatialChunkedVbo(
                waterMistVbo, built.mistFloats, built.mistFloats.size, fpv, 180f
            )
            waterMistVertexCount = vCount
            waterMistChunks = mistChunks
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawAnimatedFlowingWater(
        simTimeSec: Float,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        val ex = camEye.x
        val ez = camEye.z
        val fx = fwd.x
        val fz = fwd.z
        val maxWaterDist = 420f

        // Fast early-out if no water chunk is within 720m of the camera!
        var anyVisible = false
        for (i in 0 until waterSurfaceChunks.size) {
            val c = waterSurfaceChunks[i]
            val dx = c.centerX - ex; val dz = c.centerZ - ez
            val lim = maxWaterDist + c.radius
            if (dx * dx + dz * dz <= lim * lim && (dx * fx + dz * fz) > -(c.radius + 25f)) {
                anyVisible = true
                break
            }
        }
        if (!anyVisible) return

        GLES30.glUseProgram(waterProg)
        setCameraUniforms(waterCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)
        GLES30.glUniform1f(uWaterTimeLoc, simTimeSec % 3600.0f)

        val stride = ThrissurAnimatedWaterBuilder.FLOATS_PER_VERTEX * 4 // 10 floats = 40 bytes

        fun drawWaterChunks(vboId: Int, chunks: List<SpatialDrawChunk>) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, stride, 8 * 4)
            for (i in 0 until chunks.size) {
                val c = chunks[i]
                val dx = c.centerX - ex; val dz = c.centerZ - ez
                val lim = maxWaterDist + c.radius
                if (dx * dx + dz * dz > lim * lim) continue
                if (dx * fx + dz * fz < -(c.radius + 25f)) continue
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, c.firstVertex, c.vertexCount)
            }
            GLES30.glDisableVertexAttribArray(0)
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glDisableVertexAttribArray(2)
            GLES30.glDisableVertexAttribArray(3)
        }

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Pass 1: Flowing Water Surfaces (Spatial Chunk Culled!)
        if (waterSurfaceVbo != 0 && waterSurfaceChunks.isNotEmpty()) {
            GLES30.glDepthMask(true)
            drawWaterChunks(waterSurfaceVbo, waterSurfaceChunks)
        }

        // Pass 2: Rising Billowing Waterfall & Spillway Spray Mist Plumes
        if (waterMistVbo != 0 && waterMistChunks.isNotEmpty()) {
            GLES30.glDepthMask(false)
            drawWaterChunks(waterMistVbo, waterMistChunks)
            GLES30.glDepthMask(true)
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun appendQuadFaceToFloatArray(
        f: QuadFace3D,
        out: FloatArray,
        startCursor: Int,
        biasOffset: Float
    ): Int {
        var c = startCursor
        val col = f.color
        val r = col.red; val g = col.green; val b = col.blue; val a = col.alpha
        val v0 = f.v0; val v1 = f.v1; val v2 = f.v2; val v3 = f.v3
        // Tri 1: v0, v1, v2
        out[c++] = v0.x; out[c++] = v0.y; out[c++] = v0.z; out[c++] = r; out[c++] = g; out[c++] = b; out[c++] = a; out[c++] = biasOffset
        out[c++] = v1.x; out[c++] = v1.y; out[c++] = v1.z; out[c++] = r; out[c++] = g; out[c++] = b; out[c++] = a; out[c++] = biasOffset
        out[c++] = v2.x; out[c++] = v2.y; out[c++] = v2.z; out[c++] = r; out[c++] = g; out[c++] = b; out[c++] = a; out[c++] = biasOffset
        // Tri 2: v0, v2, v3
        out[c++] = v0.x; out[c++] = v0.y; out[c++] = v0.z; out[c++] = r; out[c++] = g; out[c++] = b; out[c++] = a; out[c++] = biasOffset
        out[c++] = v2.x; out[c++] = v2.y; out[c++] = v2.z; out[c++] = r; out[c++] = g; out[c++] = b; out[c++] = a; out[c++] = biasOffset
        out[c++] = v3.x; out[c++] = v3.y; out[c++] = v3.z; out[c++] = r; out[c++] = g; out[c++] = b; out[c++] = a; out[c++] = biasOffset
        return c
    }

    private fun ensureGDriveInstancedMeshes(roads: List<ThrissurOsmRoad>) {
        if (builtGDriveModels) return
        Zone1GDriveEnvironmentRenderer.ensureLoaded(context, roads)
        val models = Zone1GDriveEnvironmentRenderer.getLoadedModels()
        val instances = Zone1GDriveEnvironmentRenderer.getPlacedInstances()
        if (models.isEmpty() || instances.isEmpty()) return

        if (gdriveAtlasTexArrayId == 0) {
            gdriveAtlasTexArrayId = uploadAtlasTextureArray("zone1_gdrive_atlas.png")
        }
        if (gdriveAtlasTexArrayId == 0) return

        gpuGDriveModels.clear()
        val byModel = Array(models.size) { ArrayList<Zone1GDriveEnvironmentRenderer.PlacedInstance>() }
        for (inst in instances) {
            if (inst.modelIndex in models.indices) {
                byModel[inst.modelIndex].add(inst)
            }
        }

        for (mIdx in models.indices) {
            val m = models[mIdx]
            val instList = byModel[mIdx]
            if (instList.isEmpty() || m.gpuVertexFloats.isEmpty() || m.gpuIndicesShorts.isEmpty()) continue

            val bufs = IntArray(3)
            GLES30.glGenBuffers(3, bufs, 0)
            val vboId = bufs[0]
            val iboId = bufs[1]
            val instVboId = bufs[2]

            val vBuf: FloatBuffer = ByteBuffer.allocateDirect(m.gpuVertexFloats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(m.gpuVertexFloats); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.gpuVertexFloats.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

            val iBuf: ShortBuffer = ByteBuffer.allocateDirect(m.gpuIndicesShorts.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply { put(m.gpuIndicesShorts); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.gpuIndicesShorts.size * 2, iBuf, GLES30.GL_STATIC_DRAW)

            // Group instances into 160m spatial cells so distant Zone 1/2/3 trees, grass & buildings are culled in O(1)!
            val cellSize = 160f
            val invCell = 1f / cellSize
            val cellMap = HashMap<Long, ArrayList<Zone1GDriveEnvironmentRenderer.PlacedInstance>>(32)
            for (k in instList.indices) {
                val ins = instList[k]
                val gx = kotlin.math.floor((ins.cx * invCell).toDouble()).toInt()
                val gz = kotlin.math.floor((ins.cz * invCell).toDouble()).toInt()
                val key = (gx.toLong() shl 32) or (gz.toLong() and 0xFFFFFFFFL)
                cellMap.getOrPut(key) { ArrayList(8) }.add(ins)
            }

            val instFloats = FloatArray(instList.size * 5)
            val instChunks = ArrayList<SpatialDrawChunk>(cellMap.size)
            var instCursor = 0
            for (cellInsts in cellMap.values) {
                val firstInst = instCursor
                var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
                var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
                for (k in 0 until cellInsts.size) {
                    val ins = cellInsts[k]
                    val base = instCursor * 5
                    instFloats[base] = ins.cx
                    instFloats[base + 1] = ins.baseY
                    instFloats[base + 2] = ins.cz
                    instFloats[base + 3] = ins.yawRad
                    instFloats[base + 4] = ins.scale
                    instCursor++
                    if (ins.cx < minX) minX = ins.cx; if (ins.cx > maxX) maxX = ins.cx
                    if (ins.cz < minZ) minZ = ins.cz; if (ins.cz > maxZ) maxZ = ins.cz
                }
                val cx = (minX + maxX) * 0.5f
                val cz = (minZ + maxZ) * 0.5f
                val rad = hypot((maxX - minX) * 0.5f, (maxZ - minZ) * 0.5f) + 18f
                instChunks.add(SpatialDrawChunk(firstInst, cellInsts.size, cx, cz, rad))
            }
            val instBuf: FloatBuffer = ByteBuffer.allocateDirect(instFloats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(instFloats); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instFloats.size * 4, instBuf, GLES30.GL_STATIC_DRAW)

            val maxDrawDist = when (m.category) {
                Zone1GDriveEnvironmentRenderer.CAT_GRASS -> 115f
                Zone1GDriveEnvironmentRenderer.CAT_TREE -> 250f
                else -> 360f
            }
            gpuGDriveModels.add(
                GpuInstancedModel(
                    modelIndex = mIdx,
                    vboId = vboId,
                    iboId = iboId,
                    instanceVboId = instVboId,
                    indexCount = m.gpuIndicesShorts.size,
                    instanceCount = instList.size,
                    maxDrawDist = maxDrawDist,
                    chunks = instChunks
                )
            )
        }

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        builtGDriveModels = gpuGDriveModels.isNotEmpty()
    }

    private fun ensureFuelStationMeshes(roads: List<ThrissurOsmRoad>) {
        if (builtFuelStationModels) return
        val models = ThrissurFuelStationSystem.ensureLoaded(context, roads)
        val placements = ThrissurFuelStationSystem.getPlacements()
        if (models.isEmpty() || placements.isEmpty()) return

        if (fuelStationAtlasTexArrayId == 0) {
            fuelStationAtlasTexArrayId = uploadFuelStationBaseArray("thrissur_fuel_station_atlas.png", tileSize = 512)
        }
        if (fuelStationWideSignArrayId == 0) {
            fuelStationWideSignArrayId = uploadBakedSignageTextureArray(
                texW = FuelStationPremiumSignageBaker.WIDE_TEX_W,
                texH = FuelStationPremiumSignageBaker.WIDE_TEX_H,
                layerCount = FuelStationPremiumSignageBaker.WIDE_LAYER_COUNT,
                wrapS = GLES30.GL_REPEAT,
                wrapT = GLES30.GL_CLAMP_TO_EDGE
            ) { layerIdx, bmp ->
                FuelStationPremiumSignageBaker.bakeWideSignageLayer(layerIdx, bmp)
            }
        }
        if (fuelStationTallSignArrayId == 0) {
            fuelStationTallSignArrayId = uploadBakedSignageTextureArray(
                texW = FuelStationPremiumSignageBaker.TALL_TEX_W,
                texH = FuelStationPremiumSignageBaker.TALL_TEX_H,
                layerCount = FuelStationPremiumSignageBaker.TALL_LAYER_COUNT,
                wrapS = GLES30.GL_CLAMP_TO_EDGE,
                wrapT = GLES30.GL_CLAMP_TO_EDGE
            ) { layerIdx, bmp ->
                FuelStationPremiumSignageBaker.bakeTallSignageLayer(layerIdx, bmp)
            }
        }
        if (fuelStationAtlasTexArrayId == 0 || fuelStationWideSignArrayId == 0 || fuelStationTallSignArrayId == 0) return

        gpuFuelStationModels.clear()

        for (mIdx in models.indices) {
            val m = models[mIdx]
            if (m.gpuVertexFloats.isEmpty() || m.gpuIndicesShorts.isEmpty()) continue

            val bufs = IntArray(3)
            GLES30.glGenBuffers(3, bufs, 0)
            val vboId = bufs[0]
            val iboId = bufs[1]
            val instVboId = bufs[2]

            val isStationModel = (mIdx == ThrissurFuelStationSystem.MODEL_STATION)
            val uploadVerts: FloatArray = if (isStationModel) {
                val verts = m.gpuVertexFloats.copyOf()
                // Interchange & angle ENTRY (Layer 21 at X=-11.60, Z=-8.90) and EXIT (Layer 20 at X=+11.60, Z=-8.90)
                // around their vertical center posts so both face incoming/departing traffic clearly while staying
                // strictly behind the Z=-9.50m station curb (zero road overlap!).
                val entryRad = Math.toRadians(28.0).toFloat()
                val cosEntry = cos(entryRad); val sinEntry = sin(entryRad)
                val exitRad = Math.toRadians(-26.0).toFloat()
                val cosExit = cos(exitRad); val sinExit = sin(exitRad)
                var base = 0
                while (base + 8 < verts.size) {
                    val texLayer = ((verts[base + 8] + 0.5f).toInt()) shr 1
                    if (texLayer == 21) {
                        val dx = verts[base] - (-11.60f)
                        val dz = verts[base + 2] - (-8.90f)
                        verts[base] = -11.60f + dx * cosEntry + dz * sinEntry
                        verts[base + 2] = -8.90f - dx * sinEntry + dz * cosEntry
                        val nx = verts[base + 3]
                        val nz = verts[base + 5]
                        verts[base + 3] = nx * cosEntry + nz * sinEntry
                        verts[base + 5] = -nx * sinEntry + nz * cosEntry
                    } else if (texLayer == 20) {
                        val dx = verts[base] - 11.60f
                        val dz = verts[base + 2] - (-8.90f)
                        verts[base] = 11.60f + dx * cosExit + dz * sinExit
                        verts[base + 2] = -8.90f - dx * sinExit + dz * cosExit
                        val nx = verts[base + 3]
                        val nz = verts[base + 5]
                        verts[base + 3] = nx * cosExit + nz * sinExit
                        verts[base + 5] = -nx * sinExit + nz * cosExit
                    }
                    base += 9
                }
                verts
            } else {
                m.gpuVertexFloats
            }

            val vBuf: FloatBuffer = ByteBuffer.allocateDirect(uploadVerts.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(uploadVerts); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, uploadVerts.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

            val iBuf: ShortBuffer = ByteBuffer.allocateDirect(m.gpuIndicesShorts.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply { put(m.gpuIndicesShorts); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.gpuIndicesShorts.size * 2, iBuf, GLES30.GL_STATIC_DRAW)

            val isStation = (mIdx == ThrissurFuelStationSystem.MODEL_STATION)
            val totalInst = if (isStation) placements.size else placements.size * 2
            val instFloats = FloatArray(totalInst * 5)
            val instChunks = ArrayList<SpatialDrawChunk>(totalInst)
            val chunkRad = if (isStation) 32.0f else 8.0f

            var instIdx = 0
            for (k in placements.indices) {
                val st = placements[k]
                if (isStation) {
                    val base = instIdx * 5
                    instFloats[base] = st.cx
                    instFloats[base + 1] = st.baseY
                    instFloats[base + 2] = st.cz
                    instFloats[base + 3] = st.yawRad
                    instFloats[base + 4] = 1.0f
                    instChunks.add(SpatialDrawChunk(instIdx, 1, st.cx, st.cz, chunkRad))
                    instIdx++
                } else {
                    // 1. Entry-side roadside Fuel Price Signboard (facing real highway road!)
                    val base1 = instIdx * 5
                    instFloats[base1] = st.signX
                    instFloats[base1 + 1] = st.signBaseY
                    instFloats[base1 + 2] = st.signZ
                    instFloats[base1 + 3] = st.signYawRad
                    instFloats[base1 + 4] = 1.0f
                    instChunks.add(SpatialDrawChunk(instIdx, 1, st.signX, st.signZ, chunkRad))
                    instIdx++

                    // 2. Exit-side roadside Fuel Price Signboard (facing real highway road!)
                    val base2 = instIdx * 5
                    instFloats[base2] = st.exitSignX
                    instFloats[base2 + 1] = st.exitSignBaseY
                    instFloats[base2 + 2] = st.exitSignZ
                    instFloats[base2 + 3] = st.exitSignYawRad
                    instFloats[base2 + 4] = 1.0f
                    instChunks.add(SpatialDrawChunk(instIdx, 1, st.exitSignX, st.exitSignZ, chunkRad))
                    instIdx++
                }
            }

            val instBuf: FloatBuffer = ByteBuffer.allocateDirect(instFloats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(instFloats); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instFloats.size * 4, instBuf, GLES30.GL_STATIC_DRAW)

            gpuFuelStationModels.add(
                GpuInstancedModel(
                    modelIndex = mIdx,
                    vboId = vboId,
                    iboId = iboId,
                    instanceVboId = instVboId,
                    indexCount = m.gpuIndicesShorts.size,
                    instanceCount = totalInst,
                    maxDrawDist = 480f,
                    chunks = instChunks
                )
            )
        }

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        builtFuelStationModels = gpuFuelStationModels.isNotEmpty()
    }

    private fun ensureFuelWorkerMeshes() {
        if (builtFuelWorkerModels) return
        val models = FuelStationWorkerAnimator.ensureLoaded(context)
        if (models.isEmpty()) return

        if (fuelWorkerAtlasTexArrayId == 0) {
            fuelWorkerAtlasTexArrayId = uploadAtlasTextureArray("thrissur_fuel_workers_atlas.png")
        }
        if (fuelWorkerAtlasTexArrayId == 0) return

        gpuFuelWorkerModels.fill(null)
        for (i in models.indices) {
            val m = models[i]
            if (m.workerIndex !in 0..1 || m.vertexFloats.isEmpty() || m.indicesShorts.isEmpty()) continue

            val bufs = IntArray(2)
            GLES30.glGenBuffers(2, bufs, 0)
            val vboId = bufs[0]
            val iboId = bufs[1]

            val vBuf: FloatBuffer = ByteBuffer.allocateDirect(m.vertexFloats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(m.vertexFloats); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexFloats.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

            val iBuf: ShortBuffer = ByteBuffer.allocateDirect(m.indicesShorts.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply { put(m.indicesShorts); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indicesShorts.size * 2, iBuf, GLES30.GL_STATIC_DRAW)

            gpuFuelWorkerModels[m.workerIndex] = GpuSkinnedWorkerModel(
                workerIndex = m.workerIndex,
                vboId = vboId,
                iboId = iboId,
                indexCount = m.indicesShorts.size,
                model = m
            )
        }

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        builtFuelWorkerModels = gpuFuelWorkerModels[0] != null || gpuFuelWorkerModels[1] != null
    }

    private fun ensureShoranurRailwayMeshes() {
        if (builtRailwayModels) return
        val models = ShoranurRailwaySystem.ensureLoaded(context)
        if (models.isEmpty()) return

        if (shoranurRailwayAtlasTexArrayId == 0) {
            shoranurRailwayAtlasTexArrayId = uploadAtlasTextureArray("shoranur_railway_atlas.png")
        }
        if (shoranurRailwayAtlasTexArrayId == 0) return

        gpuRailwayDynamicModels.clear()
        gpuRailwayStaticTrack = null

        val trackInstances = ShoranurRailwaySystem.getStaticTrackInstances()

        for (mIdx in models.indices) {
            val m = models[mIdx]
            if (m.gpuVertexFloats.isEmpty() || m.gpuIndicesShorts.isEmpty()) continue

            val bufs = IntArray(3)
            GLES30.glGenBuffers(3, bufs, 0)
            val vboId = bufs[0]
            val iboId = bufs[1]
            val instVboId = bufs[2]

            val vBuf: FloatBuffer = ByteBuffer.allocateDirect(m.gpuVertexFloats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(m.gpuVertexFloats); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.gpuVertexFloats.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

            val iBuf: ShortBuffer = ByteBuffer.allocateDirect(m.gpuIndicesShorts.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply { put(m.gpuIndicesShorts); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.gpuIndicesShorts.size * 2, iBuf, GLES30.GL_STATIC_DRAW)

            if (mIdx == ShoranurRailwaySystem.MODEL_TRACK) {
                val cellSize = 180f
                val invCell = 1f / cellSize
                val cellMap = HashMap<Long, ArrayList<ShoranurRailwaySystem.RailwayInstance>>(32)
                for (k in trackInstances.indices) {
                    val ins = trackInstances[k]
                    val gx = kotlin.math.floor((ins.cx * invCell).toDouble()).toInt()
                    val gz = kotlin.math.floor((ins.cz * invCell).toDouble()).toInt()
                    val key = (gx.toLong() shl 32) or (gz.toLong() and 0xFFFFFFFFL)
                    cellMap.getOrPut(key) { ArrayList(12) }.add(ins)
                }
                val instFloats = FloatArray(trackInstances.size * 6)
                val trackChunks = ArrayList<SpatialDrawChunk>(cellMap.size)
                var instCursor = 0
                for (cellInsts in cellMap.values) {
                    val firstInst = instCursor
                    var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
                    var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
                    for (k in 0 until cellInsts.size) {
                        val ins = cellInsts[k]
                        val base = instCursor * 6
                        instFloats[base] = ins.cx
                        instFloats[base + 1] = ins.baseY
                        instFloats[base + 2] = ins.cz
                        instFloats[base + 3] = ins.yawRad
                        instFloats[base + 4] = ins.scale
                        instFloats[base + 5] = ins.pitchRad
                        instCursor++
                        if (ins.cx < minX) minX = ins.cx; if (ins.cx > maxX) maxX = ins.cx
                        if (ins.cz < minZ) minZ = ins.cz; if (ins.cz > maxZ) maxZ = ins.cz
                    }
                    val cx = (minX + maxX) * 0.5f
                    val cz = (minZ + maxZ) * 0.5f
                    val rad = hypot((maxX - minX) * 0.5f, (maxZ - minZ) * 0.5f) + 15f
                    trackChunks.add(SpatialDrawChunk(firstInst, cellInsts.size, cx, cz, rad))
                }
                val instBuf: FloatBuffer = ByteBuffer.allocateDirect(instFloats.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply { put(instFloats); position(0) }
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instVboId)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, instFloats.size * 4, instBuf, GLES30.GL_STATIC_DRAW)
                gpuRailwayStaticTrack = GpuInstancedModel(
                    modelIndex = mIdx,
                    vboId = vboId,
                    iboId = iboId,
                    instanceVboId = instVboId,
                    indexCount = m.gpuIndicesShorts.size,
                    instanceCount = trackInstances.size,
                    maxDrawDist = 340f,
                    chunks = trackChunks
                )
            } else {
                // Dynamic train unit (WAG-9, Coach 1, Coach 2) — instance VBO updated per frame
                gpuRailwayDynamicModels[mIdx] = GpuInstancedModel(
                    modelIndex = mIdx,
                    vboId = vboId,
                    iboId = iboId,
                    instanceVboId = instVboId,
                    indexCount = m.gpuIndicesShorts.size,
                    instanceCount = 0
                )
            }
        }

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        builtRailwayModels = gpuRailwayStaticTrack != null && gpuRailwayDynamicModels.isNotEmpty()
    }

    private fun ensureTrafficVehicleMeshes() {
        if (builtTrafficModels) return
        val models = TrafficMeshLoader.ensureLoaded(context)
        if (models.isEmpty()) return

        if (trafficAtlasTexArrayId == 0) {
            trafficAtlasTexArrayId = uploadAtlasTextureArray("thrissur_traffic_atlas.png")
        }
        if (trafficAtlasTexArrayId == 0) return

        var uploadedCount = 0
        for (i in models.indices) {
            val m = models[i]
            if (m.modelIndex !in gpuTrafficModels.indices) continue
            if (m.gpuVertexFloats.isEmpty() || m.gpuIndicesShorts.isEmpty()) continue

            val bufs = IntArray(3)
            GLES30.glGenBuffers(3, bufs, 0)
            val vboId = bufs[0]
            val iboId = bufs[1]
            val instVboId = bufs[2]

            val vBuf: FloatBuffer = ByteBuffer.allocateDirect(m.gpuVertexFloats.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(m.gpuVertexFloats); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.gpuVertexFloats.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

            val iBuf: ShortBuffer = ByteBuffer.allocateDirect(m.gpuIndicesShorts.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply { put(m.gpuIndicesShorts); position(0) }
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.gpuIndicesShorts.size * 2, iBuf, GLES30.GL_STATIC_DRAW)

            gpuTrafficModels[m.modelIndex] = GpuInstancedModel(
                modelIndex = m.modelIndex,
                vboId = vboId,
                iboId = iboId,
                instanceVboId = instVboId,
                indexCount = m.gpuIndicesShorts.size,
                instanceCount = 0,
                maxDrawDist = 290f
            )
            uploadedCount++
        }

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        builtTrafficModels = uploadedCount > 0
    }

    private fun drawInstancedTrafficVehicles(
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        val (snapshotBuf, snapshotCount) = ThrissurTrafficSystem.getReadSnapshot()
        if (snapshotCount <= 0) return

        trafficBucketCounts.fill(0)
        var shadowFloatCursor = 0
        val ex = camEye.x; val ez = camEye.z
        val fx = fwd.x; val fz = fwd.z
        val rx = right.x; val rz = right.z
        val maxDistSq = 285f * 285f
        val shadowMaxDistSq = 135f * 135f

        for (i in 0 until snapshotCount) {
            val tv = snapshotBuf[i]
            val mIdx = tv.modelIndex
            if (mIdx !in 0 until TrafficVehicleCatalog.MODEL_COUNT) continue

            val dx = tv.cx - ex
            val dz = tv.cz - ez
            val dSq = dx * dx + dz * dz
            if (dSq > maxDistSq) continue

            val camZ = dx * fx + dz * fz
            if (camZ < -(tv.halfZ + 4.5f)) continue
            val camX = abs(dx * rx + dz * rz)
            if (camZ > 0f && camX > camZ * 1.46f + tv.halfZ + 6.0f) continue

            val cnt = trafficBucketCounts[mIdx]
            if (cnt < 72) {
                trafficBucketIndices[mIdx][cnt] = i
                trafficBucketCounts[mIdx] = cnt + 1
            }

            // Ground contact shadow quad under nearby vehicles for crisp grounded realism
            if (dSq < shadowMaxDistSq && shadowFloatCursor + 48 <= trafficShadowFloats.size) {
                val c = cos(tv.headingRad)
                val s = sin(tv.headingRad)
                val sx = tv.halfX * 1.06f
                val sz = tv.halfZ * 1.03f
                val sy = tv.baseY + 0.025f

                val x0 = tv.cx - sx * c - sz * s; val z0 = tv.cz + sx * s - sz * c
                val x1 = tv.cx + sx * c - sz * s; val z1 = tv.cz - sx * s - sz * c
                val x2 = tv.cx + sx * c + sz * s; val z2 = tv.cz - sx * s + sz * c
                val x3 = tv.cx - sx * c + sz * s; val z3 = tv.cz + sx * s + sz * c

                val sr = 0.03f; val sg = 0.04f; val sb = 0.05f; val sa = 0.44f; val bias = -0.008f
                var p = shadowFloatCursor
                trafficShadowFloats[p++] = x0; trafficShadowFloats[p++] = sy; trafficShadowFloats[p++] = z0; trafficShadowFloats[p++] = sr; trafficShadowFloats[p++] = sg; trafficShadowFloats[p++] = sb; trafficShadowFloats[p++] = sa; trafficShadowFloats[p++] = bias
                trafficShadowFloats[p++] = x1; trafficShadowFloats[p++] = sy; trafficShadowFloats[p++] = z1; trafficShadowFloats[p++] = sr; trafficShadowFloats[p++] = sg; trafficShadowFloats[p++] = sb; trafficShadowFloats[p++] = sa; trafficShadowFloats[p++] = bias
                trafficShadowFloats[p++] = x2; trafficShadowFloats[p++] = sy; trafficShadowFloats[p++] = z2; trafficShadowFloats[p++] = sr; trafficShadowFloats[p++] = sg; trafficShadowFloats[p++] = sb; trafficShadowFloats[p++] = sa; trafficShadowFloats[p++] = bias
                trafficShadowFloats[p++] = x0; trafficShadowFloats[p++] = sy; trafficShadowFloats[p++] = z0; trafficShadowFloats[p++] = sr; trafficShadowFloats[p++] = sg; trafficShadowFloats[p++] = sb; trafficShadowFloats[p++] = sa; trafficShadowFloats[p++] = bias
                trafficShadowFloats[p++] = x2; trafficShadowFloats[p++] = sy; trafficShadowFloats[p++] = z2; trafficShadowFloats[p++] = sr; trafficShadowFloats[p++] = sg; trafficShadowFloats[p++] = sb; trafficShadowFloats[p++] = sa; trafficShadowFloats[p++] = bias
                trafficShadowFloats[p++] = x3; trafficShadowFloats[p++] = sy; trafficShadowFloats[p++] = z3; trafficShadowFloats[p++] = sr; trafficShadowFloats[p++] = sg; trafficShadowFloats[p++] = sb; trafficShadowFloats[p++] = sa; trafficShadowFloats[p++] = bias
                shadowFloatCursor = p
            }
        }

        // 1. Draw soft blended contact shadows first (depth write off so wheels/chassis sit cleanly on top)
        if (shadowFloatCursor > 0) {
            trafficShadowBuf.clear()
            trafficShadowBuf.put(trafficShadowFloats, 0, shadowFloatCursor)
            trafficShadowBuf.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicColorVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, shadowFloatCursor * 4, trafficShadowBuf, GLES30.GL_STREAM_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            drawStaticColorVbo(
                vbo = dynamicColorVbo,
                vertexCount = shadowFloatCursor / 8,
                camEye = camEye,
                right = right,
                up = up,
                fwd = fwd,
                projScaleX = projScaleX,
                projScaleY = projScaleY,
                depthA = depthA,
                depthB = depthB
            )
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        // 2. Draw all visible vehicles via hardware instancing (1 draw call per visible vehicle model)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(trafficVehicleProg)
        setCameraUniforms(trafficCamLocs, camEye, right, up, fwd, projScaleX, projScaleY, depthA, depthB)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, trafficAtlasTexArrayId)
        GLES30.glUniform1i(uTrafficTexArrayLoc, 0)

        val vertStride = 9 * 4
        val instStride = 7 * 4

        for (mIdx in 0 until TrafficVehicleCatalog.MODEL_COUNT) {
            val count = trafficBucketCounts[mIdx]
            if (count <= 0) continue
            val gm = gpuTrafficModels[mIdx] ?: continue
            if (gm.indexCount <= 0) continue

            val bucket = trafficBucketIndices[mIdx]
            var p = 0
            for (k in 0 until count) {
                val tv = snapshotBuf[bucket[k]]
                trafficInstFloatsScratch[p++] = tv.cx
                trafficInstFloatsScratch[p++] = tv.baseY
                trafficInstFloatsScratch[p++] = tv.cz
                trafficInstFloatsScratch[p++] = tv.headingRad
                trafficInstFloatsScratch[p++] = tv.pitchRad
                trafficInstFloatsScratch[p++] = tv.brakeGlow
                trafficInstFloatsScratch[p++] = tv.sirenPhase
            }

            trafficInstBuf.clear()
            trafficInstBuf.put(trafficInstFloatsScratch, 0, p)
            trafficInstBuf.position(0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.instanceVboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, p * 4, trafficInstBuf, GLES30.GL_STREAM_DRAW)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.vboId)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, vertStride, 0)
            GLES30.glVertexAttribDivisor(0, 0)

            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, vertStride, 3 * 4)
            GLES30.glVertexAttribDivisor(1, 0)

            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, vertStride, 6 * 4)
            GLES30.glVertexAttribDivisor(2, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gm.instanceVboId)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, instStride, 0)
            GLES30.glVertexAttribDivisor(3, 1)

            GLES30.glEnableVertexAttribArray(4)
            GLES30.glVertexAttribPointer(4, 2, GLES30.GL_FLOAT, false, instStride, 3 * 4)
            GLES30.glVertexAttribDivisor(4, 1)

            GLES30.glEnableVertexAttribArray(5)
            GLES30.glVertexAttribPointer(5, 2, GLES30.GL_FLOAT, false, instStride, 5 * 4)
            GLES30.glVertexAttribDivisor(5, 1)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, gm.iboId)
            GLES30.glDrawElementsInstanced(
                GLES30.GL_TRIANGLES,
                gm.indexCount,
                GLES30.GL_UNSIGNED_SHORT,
                0,
                count
            )
        }

        // Reset attribute divisors cleanly for subsequent draw passes
        GLES30.glVertexAttribDivisor(3, 0)
        GLES30.glVertexAttribDivisor(4, 0)
        GLES30.glVertexAttribDivisor(5, 0)
        GLES30.glDisableVertexAttribArray(5)
        GLES30.glDisableVertexAttribArray(4)
        GLES30.glDisableVertexAttribArray(3)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /**
     * Loads a 256x256-tiled PNG texture atlas from `assets/` into an OpenGL ES 3.0 `GL_TEXTURE_2D_ARRAY`
     * with `GL_REPEAT` wrap mode and hardware mipmaps so tiled UV coordinates (> 1.0) repeat seamlessly
     * without bleeding across adjacent atlas tiles.
     */
    private fun uploadAtlasTextureArray(assetName: String, tileSize: Int = 256): Int {
        return try {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val atlasBmp = context.assets.open(assetName).use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return 0

            val tileW = tileSize
            val tileH = tileSize
            val cols = (atlasBmp.width / tileW).coerceAtLeast(1)
            val rows = (atlasBmp.height / tileH).coerceAtLeast(1)
            val totalLayers = cols * rows

            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            val tid = texIds[0]
            if (tid == 0) {
                atlasBmp.recycle()
                return 0
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tid)
            GLES30.glTexImage3D(
                GLES30.GL_TEXTURE_2D_ARRAY,
                0,
                GLES30.GL_RGBA8,
                tileW,
                tileH,
                totalLayers,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )

            val pixels = IntArray(tileW * tileH)
            val rgbaBytes = ByteBuffer.allocateDirect(tileW * tileH * 4).order(ByteOrder.nativeOrder())
            val rgbaInts = rgbaBytes.asIntBuffer()
            val isLittleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

            for (layer in 0 until totalLayers) {
                val gx = (layer % cols) * tileW
                val gy = (layer / cols) * tileH
                atlasBmp.getPixels(pixels, 0, tileW, gx, gy, tileW, tileH)
                if (isLittleEndian) {
                    for (i in 0 until pixels.size) {
                        val p = pixels[i]
                        // ARGB -> ABGR little-endian int = RGBA bytes in memory
                        pixels[i] = (p and 0xFF00FF00.toInt()) or ((p and 0x00FF0000) ushr 16) or ((p and 0x000000FF) shl 16)
                    }
                    rgbaInts.clear()
                    rgbaInts.put(pixels)
                    rgbaBytes.position(0)
                } else {
                    rgbaBytes.clear()
                    for (p in pixels) {
                        rgbaBytes.put(((p ushr 16) and 0xFF).toByte())
                        rgbaBytes.put(((p ushr 8) and 0xFF).toByte())
                        rgbaBytes.put((p and 0xFF).toByte())
                        rgbaBytes.put(((p ushr 24) and 0xFF).toByte())
                    }
                    rgbaBytes.position(0)
                }
                GLES30.glTexSubImage3D(
                    GLES30.GL_TEXTURE_2D_ARRAY,
                    0,
                    0, 0, layer,
                    tileW, tileH, 1,
                    GLES30.GL_RGBA,
                    GLES30.GL_UNSIGNED_BYTE,
                    rgbaBytes
                )
            }

            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
            applyAnisotropicFiltering(GLES30.GL_TEXTURE_2D_ARRAY)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)

            atlasBmp.recycle()
            tid
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Uploads ONLY the 7 structural non-text tiles from `thrissur_fuel_station_atlas.png`
     * (concrete forecourt, hazard stripes, ceiling, roof, wall tiles, shelves, PBR swatches)
     * so all 17 signage/dispenser layers can live in dedicated ultra-HD aspect-matched arrays.
     */
    private fun uploadFuelStationBaseArray(assetName: String, tileSize: Int = 512): Int {
        return try {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val atlasBmp = context.assets.open(assetName).use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return 0

            val cols = (atlasBmp.width / tileSize).coerceAtLeast(1)
            val srcLayers = FuelStationPremiumSignageBaker.BASE_SOURCE_LAYERS
            val totalLayers = srcLayers.size

            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            val tid = texIds[0]
            if (tid == 0) {
                atlasBmp.recycle()
                return 0
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tid)
            GLES30.glTexImage3D(
                GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_RGBA8,
                tileSize, tileSize, totalLayers, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )

            val pixels = IntArray(tileSize * tileSize)
            val rgbaBytes = ByteBuffer.allocateDirect(tileSize * tileSize * 4).order(ByteOrder.nativeOrder())
            val rgbaInts = rgbaBytes.asIntBuffer()
            val isLittleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

            for (dstLayer in 0 until totalLayers) {
                val srcLayer = srcLayers[dstLayer]
                val gx = (srcLayer % cols) * tileSize
                val gy = (srcLayer / cols) * tileSize
                atlasBmp.getPixels(pixels, 0, tileSize, gx, gy, tileSize, tileSize)
                packArgbPixelsToRgbaBuffer(pixels, rgbaBytes, rgbaInts, isLittleEndian)
                GLES30.glTexSubImage3D(
                    GLES30.GL_TEXTURE_2D_ARRAY, 0,
                    0, 0, dstLayer, tileSize, tileSize, 1,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgbaBytes
                )
            }

            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
            applyAnisotropicFiltering(GLES30.GL_TEXTURE_2D_ARRAY)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)

            atlasBmp.recycle()
            tid
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Bakes and uploads a procedural ultra-HD `GL_TEXTURE_2D_ARRAY` using a single recycled scratch
     * Bitmap, hardware trilinear mipmaps, and maximum supported anisotropic filtering.
     */
    private fun uploadBakedSignageTextureArray(
        texW: Int,
        texH: Int,
        layerCount: Int,
        wrapS: Int,
        wrapT: Int,
        bakeLayer: (layerIndex: Int, outBmp: Bitmap) -> Unit
    ): Int {
        return try {
            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            val tid = texIds[0]
            if (tid == 0) return 0

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tid)
            GLES30.glTexImage3D(
                GLES30.GL_TEXTURE_2D_ARRAY, 0, GLES30.GL_RGBA8,
                texW, texH, layerCount, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )

            val scratchBmp = Bitmap.createBitmap(texW, texH, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(texW * texH)
            val rgbaBytes = ByteBuffer.allocateDirect(texW * texH * 4).order(ByteOrder.nativeOrder())
            val rgbaInts = rgbaBytes.asIntBuffer()
            val isLittleEndian = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

            for (layer in 0 until layerCount) {
                scratchBmp.eraseColor(android.graphics.Color.TRANSPARENT)
                bakeLayer(layer, scratchBmp)
                scratchBmp.getPixels(pixels, 0, texW, 0, 0, texW, texH)
                packArgbPixelsToRgbaBuffer(pixels, rgbaBytes, rgbaInts, isLittleEndian)
                GLES30.glTexSubImage3D(
                    GLES30.GL_TEXTURE_2D_ARRAY, 0,
                    0, 0, layer, texW, texH, 1,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, rgbaBytes
                )
            }
            scratchBmp.recycle()

            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, wrapS)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, wrapT)
            applyAnisotropicFiltering(GLES30.GL_TEXTURE_2D_ARRAY)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
            tid
        } catch (_: Throwable) {
            0
        }
    }

    private fun packArgbPixelsToRgbaBuffer(
        pixels: IntArray,
        rgbaBytes: ByteBuffer,
        rgbaInts: java.nio.IntBuffer,
        isLittleEndian: Boolean
    ) {
        if (isLittleEndian) {
            for (i in 0 until pixels.size) {
                val p = pixels[i]
                pixels[i] = (p and 0xFF00FF00.toInt()) or ((p and 0x00FF0000) ushr 16) or ((p and 0x000000FF) shl 16)
            }
            rgbaInts.clear()
            rgbaInts.put(pixels)
            rgbaBytes.position(0)
        } else {
            rgbaBytes.clear()
            for (p in pixels) {
                rgbaBytes.put(((p ushr 16) and 0xFF).toByte())
                rgbaBytes.put(((p ushr 8) and 0xFF).toByte())
                rgbaBytes.put((p and 0xFF).toByte())
                rgbaBytes.put(((p ushr 24) and 0xFF).toByte())
            }
            rgbaBytes.position(0)
        }
    }

    private fun queryMaxAnisotropy(): Float {
        return try {
            val exts = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
            if (!exts.contains("GL_EXT_texture_filter_anisotropic")) return 1.0f
            val buf = FloatArray(1)
            GLES30.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, buf, 0)
            buf[0].coerceIn(1.0f, 16.0f)
        } catch (_: Throwable) {
            1.0f
        }
    }

    private fun applyAnisotropicFiltering(target: Int) {
        if (maxSupportedAnisotropy > 1.0f) {
            GLES30.glTexParameterf(target, GL_TEXTURE_MAX_ANISOTROPY_EXT, maxSupportedAnisotropy)
        }
    }

    private fun cacheCamUniformLocs(prog: Int, out: CamUniformLocs) {
        out.eye = GLES30.glGetUniformLocation(prog, "uCamEye")
        out.right = GLES30.glGetUniformLocation(prog, "uCamRight")
        out.up = GLES30.glGetUniformLocation(prog, "uCamUp")
        out.fwd = GLES30.glGetUniformLocation(prog, "uCamFwd")
        out.proj = GLES30.glGetUniformLocation(prog, "uProjParams")
    }

    private fun setCameraUniforms(
        locs: CamUniformLocs,
        camEye: Vec3,
        right: Vec3,
        up: Vec3,
        fwd: Vec3,
        projScaleX: Float,
        projScaleY: Float,
        depthA: Float,
        depthB: Float
    ) {
        GLES30.glUniform3f(locs.eye, camEye.x, camEye.y, camEye.z)
        GLES30.glUniform3f(locs.right, right.x, right.y, right.z)
        GLES30.glUniform3f(locs.up, up.x, up.y, up.z)
        GLES30.glUniform3f(locs.fwd, fwd.x, fwd.y, fwd.z)
        GLES30.glUniform4f(locs.proj, projScaleX, projScaleY, depthA, depthB)
    }

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val SKY_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec2 aPos;
            out float vUvY;
            void main() {
                vUvY = aPos.y * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.9999, 1.0);
            }
        """

        private const val SKY_FS = """#version 300 es
            precision mediump float;
            in float vUvY;
            uniform float uHorizonFrac;
            out vec4 fragColor;
            void main() {
                vec3 skyZenith = vec3(0.169, 0.357, 0.549);
                vec3 skyMid = vec3(0.388, 0.580, 0.749);
                vec3 skyHaze = vec3(0.769, 0.843, 0.910);
                vec3 gndFar = vec3(0.420, 0.396, 0.349);
                vec3 gndMid = vec3(0.357, 0.333, 0.290);
                vec3 gndNear = vec3(0.310, 0.290, 0.251);

                if (vUvY >= uHorizonFrac) {
                    float t = clamp((1.0 - vUvY) / max(1.0 - uHorizonFrac, 0.001), 0.0, 1.0);
                    vec3 col = (t < 0.62)
                        ? mix(skyZenith, skyMid, t / 0.62)
                        : mix(skyMid, skyHaze, (t - 0.62) / 0.38);
                    fragColor = vec4(col, 1.0);
                } else {
                    float t = clamp((uHorizonFrac - vUvY) / max(uHorizonFrac, 0.001), 0.0, 1.0);
                    vec3 col;
                    if (t < 0.04) {
                        col = mix(skyHaze, gndFar, t / 0.04);
                    } else if (t < 0.28) {
                        col = mix(gndFar, gndMid, (t - 0.04) / 0.24);
                    } else {
                        col = mix(gndMid, gndNear, (t - 0.28) / 0.72);
                    }
                    fragColor = vec4(col, 1.0);
                }
            }
        """

        private const val WORLD_COLOR_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aWorldPos;
            layout(location = 1) in vec4 aColor;
            layout(location = 2) in float aBiasOffset;

            uniform vec3 uCamEye;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamFwd;
            uniform vec4 uProjParams; // (projScaleX, projScaleY, depthA, depthB)

            out vec4 vColor;

            void main() {
                vec3 rel = aWorldPos - uCamEye;
                float cx = dot(rel, uCamRight);
                float cy = dot(rel, uCamUp);
                float cz = dot(rel, uCamFwd);
                float biasTerm = clamp(aBiasOffset, -0.06, 0.06) * 0.0012;
                float zClip = cz * (uProjParams.z + biasTerm) + uProjParams.w;
                gl_Position = vec4(cx * uProjParams.x, cy * uProjParams.y, zClip, cz);
                vColor = aColor;
            }
        """

        private const val WORLD_COLOR_FS = """#version 300 es
            precision mediump float;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                if (vColor.a < 0.02) discard;
                fragColor = vColor;
            }
        """

        private const val GDRIVE_INSTANCED_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aLocalPos;
            layout(location = 1) in vec3 aLocalNormal;
            layout(location = 2) in vec3 aUvMat;     // (u, v, packedMat = texLayer * 2 + cutoutFlag)
            layout(location = 3) in vec3 aInstPos;   // (cx, baseY, cz)
            layout(location = 4) in vec2 aInstYawScale; // (yawRad, scale)
            layout(location = 5) in float aInstPitch;   // pitchRad along local +Z (0 for buildings/trees)

            uniform vec3 uCamEye;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamFwd;
            uniform vec4 uProjParams;

            out vec2 vUv;
            flat out int vTexLayer;
            flat out float vIsCutout;
            out float vShade;
            out float vSpec;

            void main() {
                float yaw = aInstYawScale.x;
                float sc = aInstYawScale.y;
                float c = cos(yaw);
                float s = sin(yaw);
                float cp = cos(aInstPitch);
                float sp = sin(aInstPitch);

                vec3 scaledPos = aLocalPos * sc;
                vec3 pitchedPos = vec3(
                    scaledPos.x,
                    scaledPos.y * cp + scaledPos.z * sp,
                    -scaledPos.y * sp + scaledPos.z * cp
                );
                vec3 rotPos = vec3(
                    pitchedPos.x * c - pitchedPos.z * s,
                    pitchedPos.y,
                    pitchedPos.x * s + pitchedPos.z * c
                );

                // Camera-relative world position (prevents float precision jitter)
                vec3 rel = (aInstPos - uCamEye) + rotPos;
                float cx = dot(rel, uCamRight);
                float cy = dot(rel, uCamUp);
                float cz = dot(rel, uCamFwd);
                gl_Position = vec4(
                    cx * uProjParams.x,
                    cy * uProjParams.y,
                    cz * uProjParams.z + uProjParams.w,
                    cz
                );

                vec3 pNorm = vec3(
                    aLocalNormal.x,
                    aLocalNormal.y * cp + aLocalNormal.z * sp,
                    -aLocalNormal.y * sp + aLocalNormal.z * cp
                );
                vec3 wNorm = normalize(vec3(
                    pNorm.x * c - pNorm.z * s,
                    pNorm.y,
                    pNorm.x * s + pNorm.z * c
                ));

                int packed = int(aUvMat.z + 0.5);
                vTexLayer = packed >> 1;
                vIsCutout = float(packed & 1);
                vUv = aUvMat.xy;

                vec3 sunDir = normalize(vec3(-0.398, 0.777, 0.478));
                float ndotl = dot(wNorm, sunDir);
                float diff = (vIsCutout > 0.5)
                    ? (0.76 + 0.24 * abs(ndotl))
                    : (0.68 + 0.32 * max(ndotl, -0.35));
                float hemi = 0.88 + 0.12 * clamp(wNorm.y, -1.0, 1.0);
                float ao = clamp(0.84 + 0.16 * clamp(scaledPos.y / 1.4, 0.0, 1.0), 0.84, 1.0);
                vShade = clamp(diff * hemi * ao, 0.60, 1.18);

                vec3 viewDir = normalize(-rel);
                vec3 halfVec = normalize(sunDir + viewDir);
                float ndoth = max(0.0, dot(wNorm, halfVec));
                vSpec = (vIsCutout > 0.5) ? (pow(ndoth, 16.0) * 0.06) : (pow(ndoth, 28.0) * 0.10);
            }
        """

        private const val GDRIVE_INSTANCED_FS = """#version 300 es
            precision mediump float;
            precision mediump sampler2DArray;

            in vec2 vUv;
            flat in int vTexLayer;
            flat in float vIsCutout;
            in float vShade;
            in float vSpec;

            uniform sampler2DArray uTexArray;
            out vec4 fragColor;

            void main() {
                vec4 texColor = texture(uTexArray, vec3(vUv, float(vTexLayer)));
                if (vIsCutout > 0.5) {
                    if (texColor.a < 0.42) discard;
                    texColor.rgb = mix(texColor.rgb, texColor.rgb * vec3(0.92, 1.15, 0.88), 0.58);
                }
                vec3 lit = clamp(texColor.rgb * vShade + vec3(vSpec), 0.0, 1.0);
                fragColor = vec4(lit, 1.0);
            }
        """

        // Dedicated Ultra-HD Fuel Station Shader:
        // Routes the 13 wide signboards (2048x256), 4 tall hero panels/dispensers (1024x2048),
        // and 7 structural material tiles (512x512) with highp UVs, sharp negative LOD bias,
        // and illuminated backlit polycarbonate lightbox shading.
        private const val FUEL_STATION_FS = """#version 300 es
            precision highp float;
            precision mediump sampler2DArray;

            in vec2 vUv;
            flat in int vTexLayer;
            flat in float vIsCutout;
            in float vShade;
            in float vSpec;

            uniform sampler2DArray uBaseTexArray;
            uniform sampler2DArray uWideSignArray;
            uniform sampler2DArray uTallSignArray;
            out vec4 fragColor;

            void main() {
                vec4 texColor;
                bool isSign = false;

                if (vTexLayer == 2) {
                    texColor = texture(uTallSignArray, vec3(vUv, 0.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 3) {
                    texColor = texture(uTallSignArray, vec3(vUv, 1.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 19) {
                    texColor = texture(uTallSignArray, vec3(vUv, 2.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 22) {
                    texColor = texture(uTallSignArray, vec3(vUv, 3.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 6) {
                    texColor = texture(uWideSignArray, vec3(vUv, 0.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 7) {
                    texColor = texture(uWideSignArray, vec3(vUv, 1.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 8) {
                    texColor = texture(uWideSignArray, vec3(vUv, 2.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 11) {
                    texColor = texture(uWideSignArray, vec3(vUv, 3.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 12) {
                    texColor = texture(uWideSignArray, vec3(vUv, 4.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 13) {
                    texColor = texture(uWideSignArray, vec3(vUv, 5.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 14) {
                    texColor = texture(uWideSignArray, vec3(vUv, 6.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 15) {
                    texColor = texture(uWideSignArray, vec3(vUv, 7.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 16) {
                    texColor = texture(uWideSignArray, vec3(vUv, 8.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 17) {
                    texColor = texture(uWideSignArray, vec3(vUv, 9.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 18) {
                    texColor = texture(uWideSignArray, vec3(vUv, 10.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 20) {
                    // Interchanged: +11.60m board now displays EXIT (red, ← EXIT) facing departing traffic
                    texColor = texture(uWideSignArray, vec3(vUv, 12.0), -0.45);
                    isSign = true;
                } else if (vTexLayer == 21) {
                    // Interchanged: -11.60m board now displays ENTRY (green, ENTRY →) facing incoming traffic
                    texColor = texture(uWideSignArray, vec3(vUv, 11.0), -0.45);
                    isSign = true;
                } else {
                    float baseIdx = 6.0;
                    if (vTexLayer == 0) baseIdx = 0.0;
                    else if (vTexLayer == 1) baseIdx = 1.0;
                    else if (vTexLayer == 4) baseIdx = 2.0;
                    else if (vTexLayer == 5) baseIdx = 3.0;
                    else if (vTexLayer == 9) baseIdx = 4.0;
                    else if (vTexLayer == 10) baseIdx = 5.0;
                    texColor = texture(uBaseTexArray, vec3(vUv, baseIdx));
                }

                float finalShade = isSign ? mix(vShade, 1.02, 0.62) : vShade;
                float finalSpec = isSign ? (vSpec * 0.35) : vSpec;
                vec3 lit = clamp(texColor.rgb * finalShade + vec3(finalSpec), 0.0, 1.0);
                fragColor = vec4(lit, 1.0);
            }
        """

        private const val GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF

        // 17-Bone GPU Linear Blend Skinning Shader for Animated Google Drive FBX Fuel Station Attendants
        private const val FUEL_WORKER_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNorm;
            layout(location = 2) in vec3 aUvLayer;  // (u, v, texLayer)
            layout(location = 3) in vec2 aBoneIdx;  // (b0, b1)
            layout(location = 4) in vec2 aBoneW;    // (w0, w1)

            uniform vec3 uCamEye;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamFwd;
            uniform vec4 uProjParams;
            uniform vec3 uWorldPos;
            uniform vec2 uWorldYawCS; // (cos(yaw), sin(yaw)) matching yaw = atan2(dx, dz)
            uniform mat4 uBoneMats[17];

            out vec2 vUv;
            flat out int vTexLayer;
            out float vShade;
            out float vSpec;

            void main() {
                int b0 = clamp(int(aBoneIdx.x + 0.5), 0, 16);
                int b1 = clamp(int(aBoneIdx.y + 0.5), 0, 16);
                float w0 = aBoneW.x;
                float w1 = aBoneW.y;
                float wSum = max(w0 + w1, 0.0001);
                w0 /= wSum;
                w1 /= wSum;

                mat4 skinMat = uBoneMats[b0] * w0 + uBoneMats[b1] * w1;
                vec3 skPos = (skinMat * vec4(aPos, 1.0)).xyz;
                vec3 skNorm = normalize((mat3(skinMat) * aNorm));

                // Local +Z faces world (+sin(yaw), 0, +cos(yaw)), local +X maps to (+cos(yaw), 0, -sin(yaw))
                float c = uWorldYawCS.x;
                float s = uWorldYawCS.y;
                vec3 rotPos = vec3(
                    skPos.x * c + skPos.z * s,
                    skPos.y,
                    -skPos.x * s + skPos.z * c
                );
                vec3 wNorm = normalize(vec3(
                    skNorm.x * c + skNorm.z * s,
                    skNorm.y,
                    -skNorm.x * s + skNorm.z * c
                ));

                vec3 rel = (uWorldPos - uCamEye) + rotPos;
                float cx = dot(rel, uCamRight);
                float cy = dot(rel, uCamUp);
                float cz = dot(rel, uCamFwd);
                gl_Position = vec4(
                    cx * uProjParams.x,
                    cy * uProjParams.y,
                    cz * uProjParams.z + uProjParams.w,
                    cz
                );

                vUv = aUvLayer.xy;
                vTexLayer = int(aUvLayer.z + 0.5);

                vec3 sunDir = normalize(vec3(-0.398, 0.777, 0.478));
                float ndotl = dot(wNorm, sunDir);
                float diff = 0.66 + 0.34 * max(ndotl, -0.25);
                float hemi = 0.88 + 0.12 * clamp(wNorm.y, -1.0, 1.0);
                float ao = clamp(0.84 + 0.16 * clamp(skPos.y / 1.5, 0.0, 1.0), 0.84, 1.0);
                vShade = clamp(diff * hemi * ao, 0.58, 1.18);

                vec3 viewDir = normalize(-rel);
                vec3 halfVec = normalize(sunDir + viewDir);
                float ndoth = max(0.0, dot(wNorm, halfVec));
                vSpec = pow(ndoth, 24.0) * 0.08;
            }
        """

        private const val FUEL_WORKER_FS = """#version 300 es
            precision mediump float;
            precision mediump sampler2DArray;

            in vec2 vUv;
            flat in int vTexLayer;
            in float vShade;
            in float vSpec;

            uniform sampler2DArray uTexArray;
            out vec4 fragColor;

            void main() {
                vec4 texColor = texture(uTexArray, vec3(vUv, float(vTexLayer)));
                if (texColor.a < 0.35) discard;
                vec3 lit = clamp(texColor.rgb * vShade + vec3(vSpec), 0.0, 1.0);
                fragColor = vec4(lit, 1.0);
            }
        """

        private const val SIGNBOARD_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aWorldPos;
            layout(location = 1) in vec2 aUv;

            uniform vec3 uCamEye;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamFwd;
            uniform vec4 uProjParams;
            uniform float uBiasOffset;

            out vec2 vUv;

            void main() {
                vec3 rel = aWorldPos - uCamEye;
                float cx = dot(rel, uCamRight);
                float cy = dot(rel, uCamUp);
                float cz = dot(rel, uCamFwd);
                float biasTerm = clamp(uBiasOffset, -0.06, 0.06) * 0.0012;
                float zClip = cz * (uProjParams.z + biasTerm) + uProjParams.w;
                gl_Position = vec4(cx * uProjParams.x, cy * uProjParams.y, zClip, cz);
                vUv = aUv;
            }
        """

        private const val SIGNBOARD_FS = """#version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uSignTex;
            out vec4 fragColor;
            void main() {
                vec4 c = texture(uSignTex, vUv, -0.35);
                if (c.a < 0.1) discard;
                fragColor = c;
            }
        """

        private const val WATER_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aWorldPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aFlowUv;
            layout(location = 3) in vec2 aWaterParams; // x = waterType (0..4), y = flowIntensity

            uniform vec3 uCamEye;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamFwd;
            uniform vec4 uProjParams;
            uniform float uTimeSec;

            out vec3 vWorldPos;
            out vec3 vNormal;
            out vec2 vFlowUv;
            out vec2 vWaterParams;
            out float vCamDist;

            void main() {
                vec3 pos = aWorldPos;
                float wType = aWaterParams.x;
                float intensity = aWaterParams.y;
                float t = uTimeSec;

                if (wType < 0.5) {
                    // Type 0: Waterfall / Curved Ogee Spillway Cascade - rapid downward surface undulation
                    float lipFade = smoothstep(0.0, 0.14, aFlowUv.y) * smoothstep(1.0, 0.88, aFlowUv.y);
                    float w1 = sin(aFlowUv.y * 22.0 - t * (10.0 + 5.0 * intensity) + aFlowUv.x * 1.15);
                    float w2 = cos(aFlowUv.y * 37.0 - t * 15.5 - aFlowUv.x * 2.1);
                    float disp = (w1 * 0.65 + w2 * 0.35) * (0.05 + 0.11 * intensity) * lipFade;
                    pos += aNormal * disp;
                } else if (wType < 1.5) {
                    // Type 1: Boiling Plunge Pool & Downstream Tailrace River
                    float boil = sin(aFlowUv.x * 1.1 + t * 5.2) * cos(aFlowUv.y * 0.85 - t * 4.4);
                    float drift = sin(aFlowUv.y * 0.55 - t * 3.1 + aFlowUv.x * 0.35);
                    pos.y += (boil * 0.085 * intensity + drift * 0.045);
                } else if (wType < 2.5) {
                    // Type 2: Sunlit Wind-Rippled Reservoir & Sacred Temple Ponds
                    float swellA = sin(aFlowUv.x * 0.095 + aFlowUv.y * 0.065 + t * 1.35);
                    float swellB = cos(aFlowUv.x * 0.15 - aFlowUv.y * 0.12 - t * 1.75);
                    float amp = (intensity > 0.65) ? 0.065 : 0.012;
                    pos.y += (swellA * 0.6 + swellB * 0.4) * amp;
                } else if (wType < 3.5) {
                    // Type 3: Arabian Sea Coastal Swells rolling shoreward (v -> 0)
                    float shoreFade = smoothstep(0.0, 7.0, aFlowUv.y);
                    float swell = sin(aFlowUv.y * 0.34 + t * 2.25 + sin(aFlowUv.x * 0.045 + t * 0.35) * 1.6);
                    float chop = cos(aFlowUv.y * 0.72 + t * 3.1 - aFlowUv.x * 0.08) * 0.25;
                    pos.y += (swell * 0.16 + chop * 0.05) * shoreFade * intensity;
                } else {
                    // Type 4: Rising Translucent Spray Mist Curtain
                    float riseFade = smoothstep(0.0, 0.35, aFlowUv.y);
                    float sway = sin(t * 2.4 + aFlowUv.x * 8.0 + aFlowUv.y * 4.0) * 0.24 * riseFade;
                    pos += aNormal * sway;
                    pos.y += sin(t * 1.9 + aFlowUv.x * 5.5) * 0.12 * riseFade;
                }

                vec3 rel = pos - uCamEye;
                float cx = dot(rel, uCamRight);
                float cy = dot(rel, uCamUp);
                float cz = dot(rel, uCamFwd);
                // Slight forward depth bias on cascades & shallow water to prevent any z-fighting with chutes/beds
                float biasTerm = (wType < 0.5) ? (-0.00018) : ((wType > 3.5) ? (-0.00024) : (-0.00008));
                float zClip = cz * (uProjParams.z + biasTerm) + uProjParams.w;
                gl_Position = vec4(cx * uProjParams.x, cy * uProjParams.y, zClip, cz);

                vWorldPos = pos;
                vNormal = normalize(aNormal);
                vFlowUv = aFlowUv;
                vWaterParams = aWaterParams;
                vCamDist = length(rel);
            }
        """

        private const val WATER_FS = """#version 300 es
            precision mediump float;

            in highp vec3 vWorldPos;
            in vec3 vNormal;
            in highp vec2 vFlowUv;
            in vec2 vWaterParams;
            in float vCamDist;

            uniform highp vec3 uCamEye;
            uniform highp float uTimeSec;

            out vec4 fragColor;

            float hash21(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            float vnoise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                vec2 u = f * f * (3.0 - 2.0 * f);
                float a = hash21(i);
                float b = hash21(i + vec2(1.0, 0.0));
                float c = hash21(i + vec2(0.0, 1.0));
                float d = hash21(i + vec2(1.0, 1.0));
                return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
            }

            // Fast 2-octave fbm for mobile GPU 60 FPS fill-rate
            float fbm3(vec2 p) {
                float v = 0.62 * vnoise(p);
                v += 0.38 * vnoise(p * 2.07 + vec2(13.7, 7.1));
                return v;
            }

            void main() {
                float wType = vWaterParams.x;
                float intensity = vWaterParams.y;
                float t = uTimeSec;

                vec3 sunDir = normalize(vec3(-0.398, 0.777, 0.478));
                vec3 skyZenith = vec3(0.169, 0.357, 0.549);
                vec3 skyHaze = vec3(0.769, 0.843, 0.910);
                vec3 viewDir = normalize(uCamEye - vWorldPos);
                vec3 N = normalize(vNormal);

                // =========================================================================
                // TYPE 4: RISING AERATED WATERFALL & SPILLWAY SPRAY MIST CURTAINS
                // =========================================================================
                if (wType > 3.5) {
                    float u = vFlowUv.x;
                    float v = vFlowUv.y;
                    float edgeU = smoothstep(0.0, 0.16, u) * smoothstep(1.0, 0.84, u);
                    float edgeV = smoothstep(0.0, 0.22, v) * smoothstep(1.0, 0.18, v);
                    float n1 = fbm3(vec2(u * 4.5, v * 2.4 - t * 1.15));
                    float n2 = fbm3(vec2(u * 8.5 + 3.7, v * 4.2 - t * 1.75));
                    float cloud = smoothstep(0.32, 0.82, n1 * 0.62 + n2 * 0.38);
                    float alpha = cloud * edgeU * edgeV * (0.48 * intensity);
                    if (alpha < 0.015) discard;
                    vec3 mistCol = mix(vec3(0.84, 0.93, 0.98), vec3(0.98, 0.995, 1.0), n2);
                    fragColor = vec4(mistCol, alpha);
                    return;
                }

                // =========================================================================
                // TYPE 0: FAST WATERFALL & PEECHI DAM CURVED OGEE SPILLWAY CASCADES
                // =========================================================================
                if (wType < 0.5) {
                    float u = vFlowUv.x;
                    float v = vFlowUv.y;
                    // Gravity acceleration along drop
                    float speed = 3.8 + 2.8 * intensity;
                    float vStretch = v * (6.2 + 3.4 * v);

                    float streakA = fbm3(vec2(u * 0.92, vStretch - t * speed));
                    float streakB = fbm3(vec2(u * 2.25 + streakA * 0.72, vStretch * 1.55 - t * (speed * 1.38)));
                    float fineRibbons = vnoise(vec2(u * 5.2, v * 15.0 - t * (speed * 1.9)));

                    // Crest drop aeration (top lip) + hydraulic jump plunge boiling (bottom 30%)
                    float topAeration = smoothstep(0.18, 0.0, v) * 0.55;
                    float plungeBoil = smoothstep(0.64, 0.98, v) * (0.65 + 0.35 * streakB);
                    float foamSignal = streakA * 0.48 + streakB * 0.38 + fineRibbons * 0.14;
                    float foamMask = clamp(
                        smoothstep(0.46 - 0.16 * intensity, 0.78 - 0.10 * intensity, foamSignal) * (0.55 + 0.50 * intensity)
                        + topAeration * intensity
                        + plungeBoil * (0.55 + 0.45 * intensity),
                        0.0, 1.0
                    );

                    // Perturb normal from rushing filaments for sparkling sunlit water glints
                    vec3 pertN = normalize(N + vec3((streakB - 0.5) * 0.34, (fineRibbons - 0.5) * 0.24, (streakA - 0.5) * 0.34));
                    float ndotl = max(0.28, dot(pertN, sunDir));
                    vec3 halfV = normalize(sunDir + viewDir);
                    float spec = pow(max(0.0, dot(pertN, halfV)), 52.0) * 0.78;
                    float fresnel = 0.06 + 0.94 * pow(1.0 - max(0.0, dot(pertN, viewDir)), 3.2);

                    vec3 waterDeep = vec3(0.04, 0.36, 0.48);
                    vec3 waterBright = vec3(0.14, 0.66, 0.78);
                    vec3 baseWater = mix(waterDeep, waterBright, clamp(streakA * 0.8 + 0.25, 0.0, 1.0));
                    baseWater = mix(baseWater * (0.72 + 0.36 * ndotl), skyHaze, fresnel * 0.42);

                    vec3 foamCol = vec3(0.92, 0.97, 1.0) * (0.86 + 0.18 * ndotl);
                    vec3 col = mix(baseWater, foamCol, foamMask) + vec3(1.0, 0.98, 0.92) * spec;

                    // Outer gates (intensity ~0.42) stay semi-translucent so wet concrete chute shows through;
                    // center roaring torrent gates (intensity ~1.0) are thick aerated cascading whitewater!
                    float alpha = clamp(mix(0.44, 0.88, intensity) + foamMask * 0.22, 0.42, 0.98);
                    fragColor = vec4(clamp(col, 0.0, 1.0), alpha);
                    return;
                }

                // =========================================================================
                // TYPE 1: BOILING PLUNGE POOL & DOWNSTREAM TAILRACE RIVER
                // =========================================================================
                if (wType < 1.5) {
                    float u = vFlowUv.x;
                    float v = vFlowUv.y;

                    // Domain-warped downstream river current moving along +v
                    float flowSpeed = 1.65 + 1.25 * intensity;
                    vec2 p1 = vec2(u * 0.36, v * 0.22 - t * flowSpeed);
                    float warp = fbm3(p1 + vec2(0.0, -t * 0.45));
                    vec2 p2 = vec2(u * 0.72 + warp * 1.15, v * 0.46 - t * (flowSpeed * 1.42));
                    float nA = fbm3(p1 + warp * 0.45);
                    float nB = fbm3(p2);

                    // Analytical wave slope for realistic shimmering river ripples
                    float hX = fbm3(p2 + vec2(0.14, 0.0)) - nB;
                    float hZ = fbm3(p2 + vec2(0.0, 0.14)) - nB;
                    vec3 pertN = normalize(vec3(-hX * 2.4, 1.0, -hZ * 2.4));

                    float fresnel = 0.05 + 0.95 * pow(1.0 - max(0.0, dot(pertN, viewDir)), 3.6);
                    vec3 halfV = normalize(sunDir + viewDir);
                    float spec = pow(max(0.0, dot(pertN, halfV)), 76.0) * 0.95 + pow(max(0.0, dot(pertN, halfV)), 18.0) * 0.16;

                    // Rich Kerala emerald-teal river absorption transitioning to aerated turquoise in plunge pool
                    vec3 riverDeep = vec3(0.04, 0.29, 0.36);
                    vec3 riverMid = vec3(0.08, 0.52, 0.62);
                    vec3 waterBody = mix(riverDeep, riverMid, clamp(nA * 0.75 + intensity * 0.32, 0.0, 1.0));
                    vec3 skyRefl = mix(skyZenith, skyHaze, clamp(0.35 + fresnel * 0.65, 0.0, 1.0));
                    vec3 litWater = mix(waterBody, skyRefl, fresnel * 0.68) + vec3(1.0, 0.97, 0.90) * spec;

                    // Churning whitewater eddies in plunge pool + drifting elongated foam streaks downstream
                    float boilFoam = smoothstep(0.46, 0.76, nA * 0.55 + nB * 0.45) * smoothstep(0.38, 0.95, intensity);
                    float driftFoam = smoothstep(0.64, 0.84, nB * 0.7 + warp * 0.3) * (0.28 + 0.55 * intensity);
                    float foamMask = clamp(boilFoam * 0.95 + driftFoam * 0.55, 0.0, 0.96);

                    vec3 foamCol = vec3(0.93, 0.98, 1.0);
                    vec3 col = mix(litWater, foamCol, foamMask);
                    float alpha = clamp(0.84 + foamMask * 0.14, 0.82, 0.98);
                    fragColor = vec4(clamp(col, 0.0, 1.0), alpha);
                    return;
                }

                // =========================================================================
                // TYPE 2: SUNLIT WIND-RIPPLED PEECHI RESERVOIR & SACRED TEMPLE PONDS
                // =========================================================================
                if (wType < 2.5) {
                    bool isReservoir = (intensity > 0.65);
                    float freq = isReservoir ? 0.17 : 0.48;
                    float spd = isReservoir ? 0.62 : 0.38;
                    vec2 uv = vFlowUv * freq;

                    // Dual crossed wind-driven capillary wave trains
                    vec2 wP1 = uv + vec2(t * spd * 0.85, t * spd * 0.42);
                    vec2 wP2 = uv * 1.65 + vec2(-t * spd * 0.55, t * spd * 0.78);
                    float w1 = fbm3(wP1);
                    float w2 = fbm3(wP2);

                    float eps = 0.12;
                    float gx = (fbm3(wP1 + vec2(eps, 0.0)) - w1) + (fbm3(wP2 + vec2(eps, 0.0)) - w2) * 0.7;
                    float gz = (fbm3(wP1 + vec2(0.0, eps)) - w1) + (fbm3(wP2 + vec2(0.0, eps)) - w2) * 0.7;

                    // Distance-attenuate high-frequency normals so far reservoir shimmer never aliases
                    float distAtten = 1.0 / (1.0 + vCamDist * 0.0028);
                    float bumpScale = (isReservoir ? 2.15 : 1.35) * distAtten;
                    vec3 pertN = normalize(vec3(-gx * bumpScale, 1.0, -gz * bumpScale));

                    float fresnel = 0.04 + 0.96 * pow(1.0 - max(0.0, dot(pertN, viewDir)), 4.0);
                    vec3 halfV = normalize(sunDir + viewDir);
                    float ndoth = max(0.0, dot(pertN, halfV));
                    float sparkle = pow(ndoth, 112.0) * 1.25 + pow(ndoth, 22.0) * 0.22;

                    // Deep Western Ghats mountain reservoir sapphire-emerald vs serene temple kulam jade-teal
                    vec3 deepCol = isReservoir ? vec3(0.024, 0.235, 0.365) : vec3(0.032, 0.285, 0.325);
                    vec3 shallowCol = isReservoir ? vec3(0.055, 0.435, 0.565) : vec3(0.065, 0.475, 0.505);
                    vec3 waterBody = mix(deepCol, shallowCol, clamp((w1 + w2) * 0.52, 0.0, 1.0));
                    vec3 skyRefl = mix(skyZenith, skyHaze, clamp(0.28 + fresnel * 0.72, 0.0, 1.0));

                    vec3 col = mix(waterBody, skyRefl, clamp(fresnel * 0.78 + 0.08, 0.0, 0.86));
                    col += vec3(1.0, 0.97, 0.89) * sparkle * (0.45 + 0.55 * distAtten);
                    fragColor = vec4(clamp(col, 0.0, 1.0), 0.94);
                    return;
                }

                // =========================================================================
                // TYPE 3: ARABIAN SEA COASTAL SWELLS & ROLLING BREAKING SHORELINE SURF
                // =========================================================================
                float coastZ = vFlowUv.x;
                float seaDist = max(0.0, vFlowUv.y); // 0m at beach -> increases Westward into deep sea

                float waveWarp = fbm3(vec2(coastZ * 0.045, seaDist * 0.05 + t * 0.22));
                // Rolling waves marching shoreward (decreasing seaDist as time increases -> seaDist + t * speed)
                float primaryPhase = seaDist * 0.36 + t * 2.25 + sin(coastZ * 0.04 + t * 0.32) * 1.85 + waveWarp * 2.2;
                float secondaryPhase = seaDist * 0.74 + t * 3.15 - cos(coastZ * 0.07) * 1.1;
                float waveCrest = sin(primaryPhase) * 0.68 + sin(secondaryPhase) * 0.32;

                // Fine sea surface chop normal
                vec2 seaP = vec2(coastZ * 0.22 + t * 0.25, seaDist * 0.22 + t * 0.75);
                float chop = fbm3(seaP);
                float cgX = fbm3(seaP + vec2(0.14, 0.0)) - chop;
                float cgZ = fbm3(seaP + vec2(0.0, 0.14)) - chop;
                float swellSlope = cos(primaryPhase) * 0.28 * smoothstep(0.0, 12.0, seaDist);
                float distAtten = 1.0 / (1.0 + vCamDist * 0.0025);
                vec3 pertN = normalize(vec3(swellSlope - cgZ * 1.8 * distAtten, 1.0, -cgX * 1.8 * distAtten));

                float fresnel = 0.04 + 0.96 * pow(1.0 - max(0.0, dot(pertN, viewDir)), 3.8);
                vec3 halfV = normalize(sunDir + viewDir);
                float ndoth = max(0.0, dot(pertN, halfV));
                float spec = pow(ndoth, 96.0) * 1.10 + pow(ndoth, 20.0) * 0.20;

                // Depth gradient from shallow turquoise surf to deep Arabian Sea cerulean-navy
                float deepT = smoothstep(8.0, 88.0, seaDist);
                vec3 seaShallow = vec3(0.05, 0.62, 0.72);
                vec3 seaMid = vec3(0.02, 0.44, 0.66);
                vec3 seaDeep = vec3(0.02, 0.26, 0.46);
                vec3 seaCol = mix(mix(seaShallow, seaMid, smoothstep(4.0, 32.0, seaDist)), seaDeep, deepT);
                vec3 skyRefl = mix(skyZenith, skyHaze, clamp(0.32 + fresnel * 0.68, 0.0, 1.0));
                vec3 litSea = mix(seaCol, skyRefl, fresnel * 0.68) + vec3(1.0, 0.96, 0.88) * spec;

                // Breaking white surf rollers near shore (seaDist = 0..38m) + surging beach swash foam!
                float surfZone = smoothstep(42.0, 4.0, seaDist);
                float foamNoise = fbm3(vec2(coastZ * 0.42, seaDist * 0.35 + t * 1.05));
                float breakerLines = smoothstep(0.32, 0.86, waveCrest + (foamNoise - 0.45) * 0.65) * surfZone;
                float swashZone = smoothstep(7.5, 0.4, seaDist + sin(t * 2.25 + coastZ * 0.05) * 2.4) * (0.65 + 0.35 * foamNoise);
                float foamMask = clamp(max(breakerLines * 0.92, swashZone), 0.0, 0.96);

                vec3 foamCol = vec3(0.94, 0.985, 1.0);
                vec3 finalCol = mix(litSea, foamCol, foamMask);
                // Soft translucent wet edge right where swash laps onto wet sand (seaDist = 0..2.2m)
                float edgeAlpha = smoothstep(0.0, 1.8, seaDist + foamMask * 1.4);
                fragColor = vec4(clamp(finalCol, 0.0, 1.0), clamp(0.86 + foamMask * 0.14, 0.0, 0.98) * edgeAlpha);
            }
        """

        // =========================================================================
        // 100% GPU HINO RM2 3D BUS SHADER (Static VBO + Full PBR Clearcoat/Glass/Lights)
        // Eliminates 100% of CPU per-frame vertex loops & multi-MB glBufferData uploads!
        // =========================================================================
        private const val HINO_BUS_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aLocalPos;
            layout(location = 1) in vec3 aLocalNorm;
            layout(location = 2) in vec4 aBaseColor;
            layout(location = 3) in vec3 aPartParams; // x = partId, y = doorFlag (0/1/2), z = gpuBias

            uniform vec3 uCamEye;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamFwd;
            uniform vec4 uProjParams;

            uniform vec3 uBusPos;
            uniform vec2 uBusYawCS;       // (cosYaw, sinYaw)
            uniform vec4 uBusPitchRoll;   // (pitchCos, pitchSin, rollCos, rollSin)
            uniform vec4 uWheelSpinSteer; // (spinCos, spinSin, steerCos, steerSin)
            uniform vec4 uDoorTransform;  // (doorOutX, doorFwdZ, doorCos, doorSin)
            uniform float uDoorOpenActive;
            uniform vec2 uSteerWheelCS;   // (swCos, swSin)
            uniform vec4 uLightFlags;     // (headlightsOn, brakeActive, revActive, leftBlinkOn)
            uniform vec2 uLightFlags2;    // (rightBlinkOn, sideMarkerRunning)

            out vec4 vColor;

            void main() {
                int pid = int(aPartParams.x + 0.5);
                float doorFlag = aPartParams.y;
                float gpuBias = aPartParams.z;

                // Cull interior door backface when door is closed
                if (doorFlag > 1.5 && uDoorOpenActive < 0.5) {
                    gl_Position = vec4(0.0, 0.0, 2.0, 1.0);
                    vColor = vec4(0.0);
                    return;
                }

                float c = uBusYawCS.x;
                float s = uBusYawCS.y;
                float pitchCos = uBusPitchRoll.x;
                float pitchSin = uBusPitchRoll.y;
                float rollCos = uBusPitchRoll.z;
                float rollSin = uBusPitchRoll.w;

                vec3 lp = aLocalPos;
                vec3 ln = aLocalNorm;
                vec3 tp;

                if (pid >= 6 && pid <= 11) {
                    float isFront = (pid == 6 || pid == 7) ? 1.0 : 0.0;
                    float axleZ = mix(-2.80, 3.00, isFront);
                    float dy = lp.y - 0.52;
                    float dz = lp.z - axleZ;
                    float sy = 0.52 + dy * uWheelSpinSteer.x - dz * uWheelSpinSteer.y;
                    float sz = axleZ + dy * uWheelSpinSteer.y + dz * uWheelSpinSteer.x;
                    if (pid == 6) {
                        float dx = lp.x + 1.02;
                        float dzs = sz - 3.00;
                        tp = vec3(-1.02 + dx * uWheelSpinSteer.z + dzs * uWheelSpinSteer.w, sy, 3.00 - dx * uWheelSpinSteer.w + dzs * uWheelSpinSteer.z);
                    } else if (pid == 7) {
                        float dx = lp.x - 1.02;
                        float dzs = sz - 3.00;
                        tp = vec3(1.02 + dx * uWheelSpinSteer.z + dzs * uWheelSpinSteer.w, sy, 3.00 - dx * uWheelSpinSteer.w + dzs * uWheelSpinSteer.z);
                    } else {
                        tp = vec3(lp.x, sy, sz);
                    }
                } else if (pid == 12) {
                    float swCt = 0.7880108; // cos(38 deg)
                    float swSt = 0.6156615; // sin(38 deg)
                    float rx = lp.x - 0.62;
                    float dyW = lp.y - 1.94;
                    float dzW = lp.z - 5.12;
                    float ryL = dyW * swCt - dzW * swSt;
                    float rzL = -dyW * swSt - dzW * swCt;
                    float rxR = rx * uSteerWheelCS.x - ryL * uSteerWheelCS.y;
                    float ryR = rx * uSteerWheelCS.y + ryL * uSteerWheelCS.x;
                    vec3 sp = vec3(0.62 + rxR, 1.94 + ryR * swCt - rzL * swSt, 5.12 - ryR * swSt - rzL * swCt);
                    float dy0 = sp.y - 0.55;
                    float dz0 = sp.z - 0.10;
                    float ry1 = dy0 * rollCos + sp.x * rollSin;
                    tp = vec3(sp.x * rollCos - dy0 * rollSin, 0.55 + ry1 * pitchCos - dz0 * pitchSin, 0.10 + ry1 * pitchSin + dz0 * pitchCos);
                } else {
                    vec3 dp = lp;
                    if (doorFlag > 0.5 && uDoorOpenActive > 0.5) {
                        float dxD = lp.x - (-1.23);
                        float dzD = lp.z - 4.82;
                        dp.x = -1.23 + uDoorTransform.x + dxD * uDoorTransform.z + dzD * uDoorTransform.w;
                        dp.z = 4.82 + uDoorTransform.y - dxD * uDoorTransform.w + dzD * uDoorTransform.z;
                        float rnx = ln.x * uDoorTransform.z + ln.z * uDoorTransform.w;
                        float rnz = -ln.x * uDoorTransform.w + ln.z * uDoorTransform.z;
                        ln.x = rnx;
                        ln.z = rnz;
                        if (doorFlag > 1.5) {
                            float thick = (pid == 13) ? 0.008 : 0.024;
                            dp.x += uDoorTransform.z * thick;
                            dp.z -= uDoorTransform.w * thick;
                        }
                    }
                    float dy0 = dp.y - 0.55;
                    float dz0 = dp.z - 0.10;
                    float ry1 = dy0 * rollCos + dp.x * rollSin;
                    tp = vec3(dp.x * rollCos - dy0 * rollSin, 0.55 + ry1 * pitchCos - dz0 * pitchSin, 0.10 + ry1 * pitchSin + dz0 * pitchCos);
                }

                vec3 wPos = vec3(
                    uBusPos.x + tp.x * c + tp.z * s,
                    uBusPos.y + tp.y,
                    uBusPos.z - tp.x * s + tp.z * c
                );

                vec3 rel = wPos - uCamEye;
                float cx = dot(rel, uCamRight);
                float cy = dot(rel, uCamUp);
                float cz = dot(rel, uCamFwd);
                float biasTerm = clamp(gpuBias, -0.06, 0.06) * 0.0012;
                float zClip = cz * (uProjParams.z + biasTerm) + uProjParams.w;
                gl_Position = vec4(cx * uProjParams.x, cy * uProjParams.y, zClip, cz);

                // GPU PBR Shading (identical visual appearance to CPU shader at 100x speed!)
                vec3 wNorm = normalize(vec3(ln.x * c + ln.z * s, ln.y, -ln.x * s + ln.z * c));
                vec3 vDir = normalize(uCamEye - wPos);
                vec3 sunDir = vec3(-0.398, 0.777, 0.478);

                float ndotl = dot(wNorm, sunDir);
                float wrappedDiff = clamp((ndotl + 0.48) / 1.48, 0.0, 1.0);
                float ndotv = clamp(abs(dot(wNorm, vDir)), 0.05, 1.0);
                float f1 = 1.0 - ndotv;
                float f2 = f1 * f1;
                float f4 = f2 * f2;
                vec3 halfV = normalize(sunDir + vDir);
                float ndoth = max(0.0, dot(wNorm, halfV));
                float refY = clamp(2.0 * ndotv * wNorm.y - vDir.y, -1.0, 1.0);

                vec3 baseRgb = aBaseColor.rgb;
                float alpha = aBaseColor.a;

                if (pid == 13) {
                    float skyGrad = clamp((refY + 0.12) / 1.12, 0.0, 1.0);
                    vec3 envCol = vec3(0.18, 0.24, 0.30) + vec3(0.56, 0.62, 0.68) * skyGrad;
                    float hFlash = clamp(1.0 - abs(refY) * 8.0, 0.0, 1.0) * 0.22;
                    float reflFrac = clamp(0.14 + 0.58 * (f2 * f1 * 0.5 + f2 * 0.5), 0.10, 0.76);
                    float glint = pow(ndoth, 52.0) * 0.95;
                    vec3 col = mix(baseRgb, envCol, reflFrac) + vec3(hFlash, hFlash * 1.05, hFlash * 1.12) + vec3(glint);
                    vColor = vec4(clamp(col, 0.0, 1.0), clamp(alpha * 0.82 + reflFrac * 0.20 + glint * 0.32, 0.46, 0.92));
                    return;
                }

                if (pid >= 1 && pid <= 5) {
                    float skyGrad = clamp((refY + 0.15) / 1.15, 0.0, 1.0);
                    vec3 envCol = vec3(0.22, 0.26, 0.32) + vec3(0.56, 0.62, 0.66) * skyGrad;
                    float lensGlint = pow(ndoth, 52.0) * 0.88 + pow(ndoth, 14.0) * 0.18;
                    float lensFres = clamp(0.12 + 0.42 * f2, 0.10, 0.58);
                    float dContour = (0.48 + 0.52 * wrappedDiff) * (0.62 + 0.38 * ndotv);
                    vec3 col;
                    if (pid == 1) {
                        if (uLightFlags.x > 0.5) {
                            float core = clamp(0.76 + 0.24 * ndotv, 0.0, 1.0);
                            col = vec3(0.86 * core + 0.14 * wrappedDiff, 0.93 * core + 0.07 * wrappedDiff, 0.99 * core + 0.04 * f2) + vec3(lensGlint * 0.5);
                        } else {
                            col = mix(vec3(0.74, 0.80, 0.88) * dContour, envCol, lensFres) + vec3(lensGlint);
                        }
                    } else if (pid == 2) {
                        if (uLightFlags.y > 0.5) {
                            float core = clamp(0.72 + 0.28 * ndotv, 0.0, 1.0);
                            float hot = ndotv * ndotv;
                            col = vec3(0.90 + 0.10 * core, 0.08 + 0.30 * hot, 0.06 + 0.18 * hot) + vec3(lensGlint * 0.35);
                        } else if (uLightFlags.x > 0.5) {
                            float core = clamp(0.65 + 0.35 * ndotv, 0.0, 1.0);
                            float hot = ndotv * ndotv;
                            col = vec3(0.74 * core + 0.14 * wrappedDiff + lensGlint * 0.45, 0.05 + 0.10 * hot + lensGlint * 0.35, 0.06 + 0.08 * hot + lensGlint * 0.35);
                        } else {
                            col = mix(baseRgb * vec3(0.88, 0.65, 0.70) * dContour, envCol, lensFres * 0.45) + vec3(lensGlint * 0.72);
                        }
                    } else if (pid == 3) {
                        if (uLightFlags.z > 0.5) {
                            float core = clamp(0.78 + 0.22 * ndotv, 0.0, 1.0);
                            col = vec3(0.92 * core + 0.08 * wrappedDiff, 0.95 * core + 0.05 * wrappedDiff, 0.98 * core + 0.04 * f2) + vec3(lensGlint * 0.45);
                        } else {
                            col = mix(vec3(0.68, 0.72, 0.78) * dContour, envCol, lensFres * 0.6) + vec3(lensGlint * 0.8);
                        }
                    } else {
                        bool blinkOn = (pid == 4 && uLightFlags.w > 0.5) || (pid == 5 && uLightFlags2.x > 0.5);
                        bool sideRun = (uLightFlags2.y > 0.5) && (abs(lp.z) < 4.5);
                        if (blinkOn || sideRun) {
                            float boost = blinkOn ? 1.0 : 0.78;
                            float hot = ndotv * ndotv;
                            col = vec3((0.94 + 0.06 * ndotv) * boost, (0.54 + 0.32 * hot) * boost, (0.04 + 0.16 * hot) * boost) + vec3(lensGlint * 0.33);
                        } else {
                            col = mix(baseRgb * vec3(0.86, 0.78, 0.65) * dContour, envCol, lensFres * 0.45) + vec3(lensGlint * 0.70);
                        }
                    }
                    vColor = vec4(clamp(col, 0.0, 1.0), 1.0);
                    return;
                }

                bool isWheel = (pid >= 6 && pid <= 11);
                bool isDarkTire = isWheel && baseRgb.r < 0.25 && baseRgb.b < 0.25;
                bool isAlloyRim = isWheel && !isDarkTire;
                bool isMatteTrim = (pid == 0 && baseRgb.r < 0.22 && baseRgb.g < 0.22 && baseRgb.b < 0.24);

                float skyBounce = clamp((wNorm.y + 1.0) * 0.5, 0.0, 1.0);
                vec3 amb = vec3(0.58 + 0.36 * skyBounce, 0.58 + 0.40 * skyBounce, 0.57 + 0.45 * skyBounce);
                float ao = 1.0;
                if (!isWheel && lp.y < 0.70) ao *= (0.82 + 0.18 * clamp((lp.y - 0.40) / 0.30, 0.0, 1.0));
                if (isDarkTire && lp.y < 0.26) ao *= (0.72 + 0.28 * clamp(lp.y / 0.26, 0.0, 1.0));
                float lightFac = (0.64 + 0.38 * wrappedDiff) * ao;
                vec3 lit = baseRgb * amb * lightFac;

                if (isDarkTire) {
                    vColor = vec4(clamp(lit + vec3(pow(ndoth, 10.0) * 0.08), 0.0, 1.0), alpha);
                } else if (isAlloyRim) {
                    float mRefl = (0.16 + 0.30 * f2) * clamp((refY + 0.6) * 0.5, 0.25, 1.0);
                    float spec = pow(ndoth, 32.0) * 0.52 + pow(ndoth, 8.0) * 0.12;
                    vColor = vec4(clamp(lit + vec3(0.72, 0.80, 0.90) * mRefl + vec3(spec, spec, spec * 0.96), 0.0, 1.0), alpha);
                } else if (isMatteTrim) {
                    float sSpec = pow(ndoth, 14.0) * 0.11 + 0.05 * f2;
                    vColor = vec4(clamp(lit + vec3(0.85, 0.88, 0.95) * sSpec, 0.0, 1.0), alpha);
                } else {
                    float skyEnv = clamp((refY + 0.10) / 1.10, 0.0, 1.0);
                    vec3 envCol = vec3(0.24, 0.26, 0.30) + vec3(0.58, 0.64, 0.70) * skyEnv;
                    float ccRefl = 0.04 + 0.30 * f4 + 0.08 * f2;
                    float spec = pow(ndoth, 42.0) * 0.36 + pow(ndoth, 10.0) * 0.10;
                    vColor = vec4(clamp(mix(lit, envCol, ccRefl) + vec3(spec, spec * 0.99, spec * 0.96), 0.0, 1.0), alpha);
                }
            }
        """

        private const val HINO_BUS_FS = """#version 300 es
            precision mediump float;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                if (vColor.a < 0.02) discard;
                fragColor = vColor;
            }
        """

        // =========================================================================
        // HARDWARE-INSTANCED 3D TRAFFIC VEHICLE SHADER (13 Google Drive .glb Models)
        // Supports automotive clearcoat, glossy tinted glass, bright headlights,
        // dynamic brake lights (glowing bright crimson in toll/railway blocks!),
        // and alternating red/blue strobing emergency beacons on Ambulance & Police!
        // =========================================================================
        private const val TRAFFIC_VEHICLE_VS = """#version 300 es
            precision highp float;
            layout(location = 0) in vec3 aLocalPos;
            layout(location = 1) in vec3 aLocalNormal;
            layout(location = 2) in vec3 aUvMat;         // (u, v, packedMat = texLayer * 8 + matFlag)
            layout(location = 3) in vec3 aInstPos;       // (cx, baseY, cz)
            layout(location = 4) in vec2 aInstYawPitch;  // (headingRad, pitchRad)
            layout(location = 5) in vec2 aInstLight;     // (brakeGlow 0..1, sirenPhase sec)

            uniform vec3 uCamEye;
            uniform vec3 uCamRight;
            uniform vec3 uCamUp;
            uniform vec3 uCamFwd;
            uniform vec4 uProjParams;

            out vec2 vUv;
            flat out int vTexLayer;
            flat out int vMatFlag;
            out vec3 vWorldNorm;
            out vec3 vViewDir;
            out float vLocalY;
            flat out vec2 vLightState;

            void main() {
                float h = aInstYawPitch.x;
                float p = aInstYawPitch.y;
                float ch = cos(h);
                float sh = sin(h);
                float cp = cos(p);
                float sp = sin(p);

                // Pitch around local X (positive pitch lifts +Z front nose uphill)
                vec3 pitched = vec3(
                    aLocalPos.x,
                    aLocalPos.y * cp + aLocalPos.z * sp,
                    -aLocalPos.y * sp + aLocalPos.z * cp
                );
                // Heading rotation matching busHeadingRad = atan2(dirX, dirZ):
                // local +Z -> (+sinH, +cosH), local +X -> (+cosH, -sinH)
                vec3 rotPos = vec3(
                    pitched.x * ch + pitched.z * sh,
                    pitched.y,
                    -pitched.x * sh + pitched.z * ch
                );

                vec3 rel = (aInstPos - uCamEye) + rotPos;
                float cx = dot(rel, uCamRight);
                float cy = dot(rel, uCamUp);
                float cz = dot(rel, uCamFwd);
                gl_Position = vec4(
                    cx * uProjParams.x,
                    cy * uProjParams.y,
                    cz * uProjParams.z + uProjParams.w,
                    cz
                );

                vec3 pNorm = vec3(
                    aLocalNormal.x,
                    aLocalNormal.y * cp + aLocalNormal.z * sp,
                    -aLocalNormal.y * sp + aLocalNormal.z * cp
                );
                vWorldNorm = normalize(vec3(
                    pNorm.x * ch + pNorm.z * sh,
                    pNorm.y,
                    -pNorm.x * sh + pNorm.z * ch
                ));
                vViewDir = normalize(-rel);
                vUv = aUvMat.xy;
                int packed = int(aUvMat.z + 0.5);
                vTexLayer = packed >> 3;
                vMatFlag = packed & 7;
                vLocalY = aLocalPos.y;
                vLightState = aInstLight;
            }
        """

        private const val TRAFFIC_VEHICLE_FS = """#version 300 es
            precision mediump float;
            precision mediump sampler2DArray;

            in vec2 vUv;
            flat in int vTexLayer;
            flat in int vMatFlag;
            in vec3 vWorldNorm;
            in vec3 vViewDir;
            in float vLocalY;
            flat in vec2 vLightState;

            uniform sampler2DArray uTexArray;
            out vec4 fragColor;

            void main() {
                vec3 baseRgb = texture(uTexArray, vec3(vUv, float(vTexLayer))).rgb;
                vec3 n = normalize(vWorldNorm);
                vec3 v = normalize(vViewDir);
                vec3 sunDir = normalize(vec3(-0.398, 0.777, 0.478));

                float ndotl = dot(n, sunDir);
                float wrappedDiff = clamp((ndotl + 0.46) / 1.46, 0.0, 1.0);
                float ndotv = clamp(abs(dot(n, v)), 0.05, 1.0);
                float f1 = 1.0 - ndotv;
                float f2 = f1 * f1;
                float f4 = f2 * f2;
                vec3 halfV = normalize(sunDir + v);
                float ndoth = max(0.0, dot(n, halfV));
                float refY = clamp(2.0 * ndotv * n.y - v.y, -1.0, 1.0);

                // Emissive headlights / DRL / illuminated bus destination boards
                if (vMatFlag == 3) {
                    vec3 emCol = mix(baseRgb, vec3(1.0, 0.98, 0.92), 0.35) * (0.92 + 0.16 * ndotv);
                    fragColor = vec4(clamp(emCol, 0.0, 1.0), 1.0);
                    return;
                }

                // Dynamic rear brake & taillights (bright glowing crimson when braking/queued!)
                if (vMatFlag == 4) {
                    float brk = vLightState.x;
                    vec3 tailIdle = vec3(0.42, 0.03, 0.03) * (0.65 + 0.35 * wrappedDiff) + vec3(pow(ndoth, 36.0) * 0.25);
                    vec3 tailBrake = vec3(1.0, 0.10 + 0.16 * ndotv, 0.06 + 0.08 * ndotv);
                    fragColor = vec4(clamp(mix(tailIdle, tailBrake, brk), 0.0, 1.0), 1.0);
                    return;
                }

                // Amber marker / turn lamps
                if (vMatFlag == 5) {
                    vec3 ambCol = vec3(0.98, 0.56, 0.08) * (0.78 + 0.22 * ndotv);
                    fragColor = vec4(clamp(ambCol, 0.0, 1.0), 1.0);
                    return;
                }

                // Flashing emergency roof beacons (108 Ambulance & Kerala Police SUV)
                if (vMatFlag == 6 || vMatFlag == 7) {
                    float t = vLightState.y;
                    float pulse = step(0.0, sin(t * 16.0 + (vMatFlag == 6 ? 0.0 : 3.14159)));
                    vec3 onCol = (vMatFlag == 6) ? vec3(0.12, 0.55, 1.0) : vec3(1.0, 0.12, 0.10);
                    vec3 offCol = (vMatFlag == 6) ? vec3(0.04, 0.12, 0.28) : vec3(0.28, 0.04, 0.04);
                    vec3 col = mix(offCol, onCol, (t > 0.01) ? pulse : 0.35);
                    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
                    return;
                }

                // Tinted automotive windshields & passenger windows (sleek sky Fresnel reflection)
                if (vMatFlag == 2) {
                    float skyGrad = clamp((refY + 0.12) / 1.12, 0.0, 1.0);
                    vec3 envCol = vec3(0.16, 0.22, 0.29) + vec3(0.56, 0.63, 0.70) * skyGrad;
                    float reflFrac = clamp(0.16 + 0.54 * f2, 0.14, 0.72);
                    float glint = pow(ndoth, 48.0) * 0.85;
                    vec3 col = mix(vec3(0.05, 0.08, 0.11), envCol, reflFrac) + vec3(glint);
                    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
                    return;
                }

                float skyBounce = clamp((n.y + 1.0) * 0.5, 0.0, 1.0);
                vec3 amb = vec3(0.58 + 0.34 * skyBounce, 0.58 + 0.38 * skyBounce, 0.57 + 0.42 * skyBounce);
                float ao = clamp(0.80 + 0.20 * clamp(vLocalY / 0.85, 0.0, 1.0), 0.80, 1.0);
                vec3 lit = baseRgb * amb * (0.64 + 0.38 * wrappedDiff) * ao;

                if (vMatFlag == 1) {
                    // Glossy automotive paint / clearcoat
                    float skyEnv = clamp((refY + 0.10) / 1.10, 0.0, 1.0);
                    vec3 envCol = vec3(0.24, 0.27, 0.32) + vec3(0.54, 0.60, 0.66) * skyEnv;
                    float ccRefl = 0.04 + 0.26 * f4 + 0.06 * f2;
                    float spec = pow(ndoth, 38.0) * 0.34 + pow(ndoth, 10.0) * 0.08;
                    fragColor = vec4(clamp(mix(lit, envCol, ccRefl) + vec3(spec, spec * 0.99, spec * 0.96), 0.0, 1.0), 1.0);
                } else {
                    // Matte trim / tires / cargo sacks
                    float spec = pow(ndoth, 12.0) * 0.08;
                    fragColor = vec4(clamp(lit + vec3(spec), 0.0, 1.0), 1.0);
                }
            }
        """
    }
}
