package com.bharatbussim.game.ui

import android.content.Context
import androidx.compose.runtime.Immutable
import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

enum class FuelType(
    val displayName: String,
    val pricePerLiterInr: Float,
    val workerIndex: Int, // 0 = Worker 1 (Petrol), 1 = Worker 2 (Diesel)
    val attendantTitle: String
) {
    DIESEL(
        displayName = "Diesel",
        pricePerLiterInr = 95.40f,
        workerIndex = 1,
        attendantTitle = "Diesel Bay Attendant"
    ),
    PETROL(
        displayName = "Petrol",
        pricePerLiterInr = 105.80f,
        workerIndex = 0,
        attendantTitle = "Petrol Bay Attendant"
    )
}

enum class RefuelOrderMode {
    FULL_TANK,
    BY_PRICE,
    BY_LITERS
}

enum class RefuelDialogStep {
    FUEL_TYPE,
    ORDER_MODE,
    AMOUNT_INPUT
}

enum class RefuelCutsceneCam(val badge: String) {
    NONE(""),
    CAM_1_HOSE("CAM 1 • FUELING BAY"),
    CAM_2_METER("CAM 2 • LIVE PUMP METER"),
    CAM_3_FAREWELL("CAM 3 • COMPLETING SERVICE")
}

enum class RefuelPhase {
    NONE,
    APPROACH_PROMPT,
    WORKER_APPROACHING,
    DIALOG_OPEN,
    DRIVER_REPLYING,
    // Step 1: Approach bus fuel tank & turn toward fuel door
    WALKING_TO_TANK_TO_OPEN_DOOR,
    // Step 2: Reach fuel door handle & smoothly swing hinged door open
    OPENING_FUEL_DOOR,
    // Step 3: Walk to pump side, grip nozzle in holster & lift it cleanly out
    WALKING_TO_PUMP_FOR_NOZZLE,
    PICKING_UP_NOZZLE,
    // Step 4: Carry nozzle to tank, align coaxially & insert spout inside tank opening
    WALKING_TO_TANK_WITH_NOZZLE,
    INSERTING_NOZZLE,
    // Step 5: Stable refuelling pose with nozzle locked inside tank opening
    FUELING_IN_PROGRESS,
    // Step 6: Remove nozzle, return to holster, close & lock hinged fuel door, farewell & walk away
    REMOVING_NOZZLE,
    RETURNING_NOZZLE,
    PLACING_NOZZLE_IN_HOLDER,
    WALKING_TO_TANK_TO_CLOSE_DOOR,
    CLOSING_FUEL_DOOR,
    THANK_YOU_FAREWELL,
    WALKING_AWAY_TO_HOME
}

enum class WorkerAnimState {
    IDLE,
    WAVING_WELCOME,
    WALKING,
    TALKING,
    OPENING_DOOR,
    REACHING_PUMP,
    HOLDING_NOZZLE_IDLE,
    FUELING,
    CLOSING_DOOR,
    THANK_YOU_WAVE
}

@Immutable
data class FuelWorkerWorldPose(
    val workerIndex: Int, // 0 = Petrol Worker 1, 1 = Diesel Worker 2
    val x: Float,
    val y: Float,
    val z: Float,
    val yawRad: Float,
    val animState: WorkerAnimState,
    val animPhase: Float,
    val holdingNozzle: Boolean = false,
    // Analytical 2-bone Right Arm IK world-space palm grip target & orientation
    val rightHandIkWeight: Float = 0f,
    val rightHandTargetX: Float = 0f,
    val rightHandTargetY: Float = 0f,
    val rightHandTargetZ: Float = 0f,
    val rightHandAimX: Float = 0f,
    val rightHandAimY: Float = 0f,
    val rightHandAimZ: Float = 1f,
    val torsoLeanPitchRad: Float = 0f
)

@Immutable
data class RefuelLiveState(
    val isNearFuelStation: Boolean = false,
    val isParkedInBayBox: Boolean = false,
    val parkedSlotSide: FuelType? = null,
    val nearestSlotSide: FuelType? = null,
    val cornersInSlotCount: Int = 0,
    val stationId: String = "",
    val stationName: String = "",
    val phase: RefuelPhase = RefuelPhase.NONE,
    val dialogStep: RefuelDialogStep = RefuelDialogStep.ORDER_MODE,
    val cutsceneCam: RefuelCutsceneCam = RefuelCutsceneCam.NONE,
    val isCutsceneActive: Boolean = false,
    val selectedFuelType: FuelType = FuelType.DIESEL,
    val orderMode: RefuelOrderMode = RefuelOrderMode.FULL_TANK,
    val customPriceInr: Int = 1000,
    val customLiters: Int = 25,
    val targetLiters: Float = 0f,
    val dispensedLiters: Float = 0f,
    val dispensedRupees: Float = 0f,
    val pricePerLiterInr: Float = FuelType.DIESEL.pricePerLiterInr,
    val paidViaFleetCard: Boolean = false,
    val activeSpeakerLabel: String = "",
    val activeDialogueLine: String = "",
    val activeDispenserLocalX: Float = ThrissurFuelStationSystem.DIESEL_DISPENSER_X,
    val activeDispenserLocalZ: Float = ThrissurFuelStationSystem.DIESEL_ISLAND_Z,
    val activeStationCx: Float = 0f,
    val activeStationBaseY: Float = 0.035f,
    val activeStationCz: Float = 0f,
    val activeStationCosYaw: Float = 1f,
    val activeStationSinYaw: Float = 0f,
    // Side hose outlet (swivel manifold on side of pump — never on the digital meter!)
    val hoseStartX: Float = 0f,
    val hoseStartY: Float = 0f,
    val hoseStartZ: Float = 0f,
    // Side nozzle holster cradle world position (where nozzle docks when idle)
    val nozzleHolsterX: Float = 0f,
    val nozzleHolsterY: Float = 0f,
    val nozzleHolsterZ: Float = 0f,
    val nozzleDockedInHolder: Boolean = true,
    val nozzleInsertedFrac: Float = 0f,
    // Bus fuel filler hole world position
    val nozzleTargetX: Float = 0f,
    val nozzleTargetY: Float = 0f,
    val nozzleTargetZ: Float = 0f,
    // Hinged 3D bus fuel door state (0 = closed & locked, 1 = 112 deg open)
    val showFuelDoorAssembly: Boolean = false,
    val fuelDoorOpenFrac: Float = 0f,
    val fuelDoorSideSign: Float = 1f,
    val fuelDoorLongOffset: Float = -1.20f,
    val showFuelHose: Boolean = false,
    val activeWorkers: List<FuelWorkerWorldPose> = emptyList()
)

/**
 * Loads both compiled Google Drive FBX human attendants (`thrissur_fuel_workers.bin`, FWK1 format)
 * and solves real-time 17-bone Forward Kinematics + smooth per-worker joint blending +
 * exact analytical 2-Bone Right Arm Inverse Kinematics (IK) with anatomically aligned palm grip
 * orientation for realistic fuel door & nozzle interaction on OpenGL ES 3.0.
 */
internal object FuelStationWorkerAnimator {

    const val WORKER_PETROL = 0 // Google Drive Fuel Worker 1
    const val WORKER_DIESEL = 1 // Google Drive Fuel Worker 2
    const val BONE_COUNT = 17

    // Exact palm grip center relative to RightHand wrist joint (bone 10) in T-pose
    // Verified from thrissur_fuel_workers.bin RightHand mesh extents:
    //   wrist=(-0.705, 1.460, +0.035), mesh X=[-0.892..-0.698], Y=[1.400..1.485], Z=[-0.014..+0.133]
    const val PALM_LOCAL_DX = -0.082f // Mid-palm along finger axis (-X)
    const val PALM_LOCAL_DY = -0.026f // Cupped inner palm offset (-Y)
    const val PALM_LOCAL_DZ = 0.012f  // Centered cleanly below thumb (+Z)

    // 17-bone hierarchy matching tools/compile_fuel_workers.py
    private val BONE_PARENTS = intArrayOf(
        -1, // 0: Hips
        0,  // 1: Spine
        1,  // 2: Spine2
        2,  // 3: Neck
        3,  // 4: Head
        2,  // 5: LeftArm
        5,  // 6: LeftForeArm
        6,  // 7: LeftHand
        2,  // 8: RightArm
        8,  // 9: RightForeArm
        9,  // 10: RightHand
        0,  // 11: LeftUpLeg
        11, // 12: LeftLeg
        12, // 13: LeftFoot
        0,  // 14: RightUpLeg
        14, // 15: RightLeg
        15  // 16: RightFoot
    )

    class LoadedFuelWorkerModel(
        val workerIndex: Int,
        val restPosX: FloatArray,
        val restPosY: FloatArray,
        val restPosZ: FloatArray,
        val vertexFloats: FloatArray, // 13 floats/vert: px,py,pz, nx,ny,nz, u,v,layer, b0,b1,w0,w1
        val indicesShorts: ShortArray
    )

    @Volatile
    private var cachedModels: List<LoadedFuelWorkerModel>? = null

    fun ensureLoaded(context: Context): List<LoadedFuelWorkerModel> {
        return cachedModels ?: synchronized(this) {
            cachedModels ?: try {
                context.assets.open("thrissur_fuel_workers.bin").use { raw ->
                    BufferedInputStream(raw, 65536).use { input ->
                        parseFwk1(input.readBytes())
                    }
                }
            } catch (_: Throwable) {
                emptyList()
            }.also { cachedModels = it }
        }
    }

    fun getLoadedModels(): List<LoadedFuelWorkerModel> = cachedModels ?: emptyList()

    private fun parseFwk1(bytes: ByteArray): List<LoadedFuelWorkerModel> {
        if (bytes.size < 16) return emptyList()
        if (bytes[0] != 'F'.code.toByte() || bytes[1] != 'W'.code.toByte() ||
            bytes[2] != 'K'.code.toByte() || bytes[3] != '1'.code.toByte()
        ) {
            return emptyList()
        }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(4)
        val version = bb.int
        val numWorkers = bb.int
        val numBones = bb.int
        if (version != 1 || numBones != BONE_COUNT) return emptyList()

        val result = ArrayList<LoadedFuelWorkerModel>(numWorkers)
        for (w in 0 until numWorkers) {
            val rx = FloatArray(BONE_COUNT)
            val ry = FloatArray(BONE_COUNT)
            val rz = FloatArray(BONE_COUNT)
            for (b in 0 until BONE_COUNT) {
                rx[b] = bb.float
                ry[b] = bb.float
                rz[b] = bb.float
            }
            val numVerts = bb.int
            val numIndices = bb.int
            val vFloats = FloatArray(numVerts * 13)
            for (i in 0 until vFloats.size) {
                vFloats[i] = bb.float
            }
            val iShorts = ShortArray(numIndices)
            for (i in 0 until numIndices) {
                iShorts[i] = bb.short
            }
            result.add(LoadedFuelWorkerModel(w, rx, ry, rz, vFloats, iShorts))
        }
        return result
    }

    // Per-worker smoothed joint state for buttery animation blending (2 workers, GL render thread only)
    private val smoothRotX = Array(2) { FloatArray(BONE_COUNT) }
    private val smoothRotY = Array(2) { FloatArray(BONE_COUNT) }
    private val smoothRotZ = Array(2) { FloatArray(BONE_COUNT) }
    private val smoothHipY = FloatArray(2)
    private val smoothInitialized = BooleanArray(2)

    // Scratch arrays used on the GL render thread (zero allocations per frame!)
    private val targetRotX = FloatArray(BONE_COUNT)
    private val targetRotY = FloatArray(BONE_COUNT)
    private val targetRotZ = FloatArray(BONE_COUNT)
    private val worldMats = FloatArray(BONE_COUNT * 16)
    private val tmpLocal = FloatArray(16)
    private val ikArmMat = FloatArray(16)
    private val ikForeMat = FloatArray(16)
    private val ikHandMat = FloatArray(16)

    /**
     * Solves 17-bone Forward Kinematics + smooth pose blending + analytical 2-Bone Right Arm IK
     * for a fuel worker's current world pose.
     *
     * Outputs:
     * - `outSkinMats` (length >= 272): 17 column-major 4x4 GPU skinning matrices
     * - `outGripFrameLocal` (length >= 9): exact animated model-local right-hand palm grip frame:
     *     [0..2] = palm grip center (x, y, z) where the nozzle pistol grip sits snugly inside the palm
     *     [3..5] = hand finger/nozzle forward aim axis (unit vector)
     *     [6..8] = hand thumb/nozzle handle-up axis (unit vector)
     */
    fun solvePose(
        model: LoadedFuelWorkerModel,
        pose: FuelWorkerWorldPose,
        dtSec: Float,
        outSkinMats: FloatArray,
        outGripFrameLocal: FloatArray
    ) {
        targetRotX.fill(0f)
        targetRotY.fill(0f)
        targetRotZ.fill(0f)

        val deg = (PI / 180.0).toFloat()
        val phase = pose.animPhase
        var targetHipOffsetY = 0f

        // Base natural relaxed standing pose (lowers both T-pose arms down along hips!)
        targetRotZ[5] = -74f * deg
        targetRotX[6] = -14f * deg
        targetRotZ[8] = 74f * deg
        targetRotX[9] = -14f * deg

        when (pose.animState) {
            WorkerAnimState.IDLE -> {
                val breath = sin(phase * 1.9f)
                val look = sin(phase * 0.55f)
                targetRotX[1] = breath * 1.6f * deg
                targetRotX[2] = breath * 2.0f * deg
                targetRotY[3] = look * 10f * deg
                targetRotY[4] = look * 12f * deg
                targetRotZ[5] = (-74f + breath * 2.5f) * deg
                targetRotZ[8] = (74f - breath * 2.5f) * deg
                targetRotX[6] = (-16f - breath * 2f) * deg
                targetRotX[9] = (-16f - breath * 2f) * deg
            }

            WorkerAnimState.WAVING_WELCOME -> {
                val wave = sin(phase * 7.2f)
                val breath = sin(phase * 2.2f)
                targetRotX[1] = -2f * deg
                targetRotY[2] = 6f * deg
                targetRotY[4] = sin(phase * 1.8f) * 6f * deg
                targetRotZ[5] = -72f * deg
                targetRotX[6] = -22f * deg
                targetRotZ[8] = (-34f + wave * 11f) * deg
                targetRotX[8] = -28f * deg
                targetRotZ[9] = (-68f + wave * 24f) * deg
                targetRotX[9] = -18f * deg
                targetRotZ[10] = wave * 14f * deg
                targetHipOffsetY = breath * 0.008f
            }

            WorkerAnimState.WALKING -> {
                val stride = sin(phase * 6.8f)
                val strideCos = cos(phase * 6.8f)
                targetHipOffsetY = abs(stride) * 0.030f - 0.013f

                // Natural pelvis & torso counter-twist
                targetRotY[0] = stride * 5.2f * deg
                targetRotY[2] = -stride * 6.5f * deg
                targetRotX[1] = 3.5f * deg

                // Bipedal leg swing + realistic knee bend
                targetRotX[11] = -stride * 24f * deg
                targetRotX[12] = (if (strideCos > 0f) strideCos * 40f else abs(stride) * 8f) * deg
                targetRotX[13] = -targetRotX[12] * 0.35f

                targetRotX[14] = stride * 24f * deg
                targetRotX[15] = (if (strideCos < 0f) -strideCos * 40f else abs(stride) * 8f) * deg
                targetRotX[16] = -targetRotX[15] * 0.35f

                // Left arm swings opposite Left leg
                targetRotZ[5] = -72f * deg
                targetRotX[5] = stride * 20f * deg
                targetRotX[6] = (-16f - (if (stride < 0f) -stride * 16f else 0f)) * deg

                // Right arm either swings naturally OR carries nozzle forward steadily
                if (pose.holdingNozzle) {
                    targetRotZ[8] = 60f * deg
                    targetRotX[8] = (-36f + stride * 3.5f) * deg
                    targetRotX[9] = -52f * deg
                    targetRotY[9] = 12f * deg
                    targetRotY[10] = -8f * deg
                } else {
                    targetRotZ[8] = 72f * deg
                    targetRotX[8] = -stride * 20f * deg
                    targetRotX[9] = (-16f - (if (stride > 0f) stride * 16f else 0f)) * deg
                }
            }

            WorkerAnimState.TALKING -> {
                val talk = sin(phase * 4.4f)
                val nod = sin(phase * 3.1f)
                targetRotX[1] = 3f * deg
                targetRotX[4] = (4f + nod * 6.5f) * deg
                targetRotY[4] = sin(phase * 1.4f) * 8f * deg
                targetRotZ[5] = -64f * deg
                targetRotX[5] = (-18f + talk * 6f) * deg
                targetRotX[6] = (-48f + talk * 10f) * deg
                targetRotZ[8] = 58f * deg
                targetRotX[8] = (-34f - talk * 10f) * deg
                targetRotX[9] = (-58f + sin(phase * 5.2f) * 14f) * deg
                targetRotY[9] = 16f * deg
            }

            WorkerAnimState.OPENING_DOOR, WorkerAnimState.CLOSING_DOOR -> {
                val breath = sin(phase * 2.4f)
                targetRotX[1] = (4.5f + breath * 1.2f) * deg
                targetRotX[2] = 5.5f * deg
                targetRotY[2] = -6f * deg
                targetRotX[4] = 11f * deg
                targetRotY[4] = 4f * deg
                // Left hand rests relaxed near side for balanced posture
                targetRotZ[5] = -68f * deg
                targetRotX[5] = -10f * deg
                targetRotX[6] = -28f * deg
                // Right arm base fallback (precisely overridden by analytical 2-bone IK!)
                targetRotZ[8] = 60f * deg
                targetRotX[8] = -52f * deg
                targetRotX[9] = -36f * deg
            }

            WorkerAnimState.REACHING_PUMP -> {
                val breath = sin(phase * 2.4f)
                targetRotX[1] = (4f + breath * 1.2f) * deg
                targetRotX[2] = 4.5f * deg
                targetRotX[4] = 6f * deg
                targetRotZ[5] = -70f * deg
                targetRotX[6] = -20f * deg
                targetRotZ[8] = 60f * deg
                targetRotX[8] = -48f * deg
                targetRotX[9] = -34f * deg
            }

            WorkerAnimState.HOLDING_NOZZLE_IDLE -> {
                val breath = sin(phase * 2.2f)
                targetRotX[1] = (4f + breath * 1.2f) * deg
                targetRotX[2] = 4.5f * deg
                targetRotX[4] = 9f * deg
                targetRotZ[5] = -64f * deg
                targetRotX[5] = -18f * deg
                targetRotX[6] = -42f * deg
                targetRotZ[8] = 60f * deg
                targetRotX[8] = -52f * deg
                targetRotX[9] = -36f * deg
            }

            WorkerAnimState.FUELING -> {
                val sway = sin(phase * 1.8f)
                val checkPump = sin(phase * 0.72f)
                targetRotX[1] = (5.5f + sway * 1.0f) * deg
                targetRotX[2] = 5.0f * deg
                targetRotY[2] = (-5f + checkPump * 7f) * deg
                // Periodically glances back toward the digital pump meter, then back at the nozzle!
                targetRotY[4] = checkPump * 22f * deg
                targetRotX[4] = 10f * deg
                // Left hand rests steadily near waist / supporting balance
                targetRotZ[5] = -60f * deg
                targetRotX[5] = -22f * deg
                targetRotX[6] = -48f * deg
                targetRotY[6] = -16f * deg
                // Right arm base fallback (precisely locked to bus filler nozzle via analytical 2-bone IK!)
                targetRotZ[8] = 60f * deg
                targetRotX[8] = -58f * deg
                targetRotX[9] = -32f * deg
            }

            WorkerAnimState.THANK_YOU_WAVE -> {
                val wave = sin(phase * 7.4f)
                val bow = sin(phase * 2.2f) * 0.5f + 0.5f
                targetRotX[1] = (3f + bow * 4.5f) * deg
                targetRotX[4] = (5f + bow * 7.5f) * deg
                targetRotZ[5] = -72f * deg
                targetRotX[6] = -22f * deg
                targetRotZ[8] = (-36f + wave * 13f) * deg
                targetRotX[8] = -26f * deg
                targetRotZ[9] = (-64f + wave * 28f) * deg
                targetRotX[9] = -16f * deg
                targetRotZ[10] = wave * 15f * deg
            }
        }

        // Apply subtle requested torso forward lean (splits evenly across Spine & Spine2 for natural posture)
        if (abs(pose.torsoLeanPitchRad) > 1e-4f) {
            targetRotX[1] += pose.torsoLeanPitchRad * 0.45f
            targetRotX[2] += pose.torsoLeanPitchRad * 0.55f
        }

        // Smooth exponential joint blending per worker (eliminates all sudden snaps between states!)
        val wSlot = pose.workerIndex.coerceIn(0, 1)
        val safeDt = dtSec.coerceIn(0.001f, 0.1f)
        val blendAlpha = if (!smoothInitialized[wSlot]) {
            smoothInitialized[wSlot] = true
            1f
        } else {
            (1f - exp(-11.5f * safeDt)).coerceIn(0.05f, 1f)
        }
        val sRx = smoothRotX[wSlot]
        val sRy = smoothRotY[wSlot]
        val sRz = smoothRotZ[wSlot]
        for (b in 0 until BONE_COUNT) {
            sRx[b] += (targetRotX[b] - sRx[b]) * blendAlpha
            sRy[b] += (targetRotY[b] - sRy[b]) * blendAlpha
            sRz[b] += (targetRotZ[b] - sRz[b]) * blendAlpha
        }
        smoothHipY[wSlot] += (targetHipOffsetY - smoothHipY[wSlot]) * blendAlpha
        val hipOffsetY = smoothHipY[wSlot]

        // Step 1: Forward Kinematics pass top-down (parents always have smaller indices)
        for (b in 0 until BONE_COUNT) {
            val p = BONE_PARENTS[b]
            val tx: Float
            val ty: Float
            val tz: Float
            if (p < 0) {
                tx = model.restPosX[b]
                ty = model.restPosY[b] + hipOffsetY
                tz = model.restPosZ[b]
            } else {
                tx = model.restPosX[b] - model.restPosX[p]
                ty = model.restPosY[b] - model.restPosY[p]
                tz = model.restPosZ[b] - model.restPosZ[p]
            }
            buildTrsMat(tx, ty, tz, sRx[b], sRy[b], sRz[b], tmpLocal)
            if (p < 0) {
                System.arraycopy(tmpLocal, 0, worldMats, b * 16, 16)
            } else {
                mulMat4(worldMats, p * 16, tmpLocal, 0, worldMats, b * 16)
            }
        }

        // Step 2: Analytical 2-Bone Right Arm Inverse Kinematics (Shoulder 8 -> Elbow 9 -> Wrist/Palm 10)
        val ikW = pose.rightHandIkWeight.coerceIn(0f, 1f)
        if (ikW > 0.001f) {
            applyRightArmAnalyticalIk(model, pose, ikW)
        }

        // Step 3: Build final GPU skinning matrices: skinMat[b] = worldMat[b] * Translate(-restPos[b])
        for (b in 0 until BONE_COUNT) {
            val off = b * 16
            val rpx = -model.restPosX[b]
            val rpy = -model.restPosY[b]
            val rpz = -model.restPosZ[b]
            for (k in 0 until 12) {
                outSkinMats[off + k] = worldMats[off + k]
            }
            outSkinMats[off + 12] = worldMats[off + 0] * rpx + worldMats[off + 4] * rpy + worldMats[off + 8] * rpz + worldMats[off + 12]
            outSkinMats[off + 13] = worldMats[off + 1] * rpx + worldMats[off + 5] * rpy + worldMats[off + 9] * rpz + worldMats[off + 13]
            outSkinMats[off + 14] = worldMats[off + 2] * rpx + worldMats[off + 6] * rpy + worldMats[off + 10] * rpz + worldMats[off + 14]
            outSkinMats[off + 15] = 1f
        }

        // Step 4: Extract exact animated model-local RightHand Palm Grip Frame (position + aim + up)
        // Bone 10 columns: col0 = +X (-fingers), col1 = +Y (back of hand), col2 = +Z (thumb / handle-up)
        val rhOff = 10 * 16
        val r00 = worldMats[rhOff + 0]; val r10 = worldMats[rhOff + 1]; val r20 = worldMats[rhOff + 2]
        val r01 = worldMats[rhOff + 4]; val r11 = worldMats[rhOff + 5]; val r21 = worldMats[rhOff + 6]
        val r02 = worldMats[rhOff + 8]; val r12 = worldMats[rhOff + 9]; val r22 = worldMats[rhOff + 10]
        val wx = worldMats[rhOff + 12]; val wy = worldMats[rhOff + 13]; val wz = worldMats[rhOff + 14]

        // Exact cupped palm grip center in model-local space
        outGripFrameLocal[0] = wx + r00 * PALM_LOCAL_DX + r01 * PALM_LOCAL_DY + r02 * PALM_LOCAL_DZ
        outGripFrameLocal[1] = wy + r10 * PALM_LOCAL_DX + r11 * PALM_LOCAL_DY + r12 * PALM_LOCAL_DZ
        outGripFrameLocal[2] = wz + r20 * PALM_LOCAL_DX + r21 * PALM_LOCAL_DY + r22 * PALM_LOCAL_DZ

        // Exact finger / nozzle forward aim direction (-col0, normalized)
        val ax = -r00; val ay = -r10; val az = -r20
        val aLen = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1e-5f)
        outGripFrameLocal[3] = ax / aLen
        outGripFrameLocal[4] = ay / aLen
        outGripFrameLocal[5] = az / aLen

        // Exact thumb / nozzle handle-up direction (+col2, normalized)
        val ux = r02; val uy = r12; val uz = r22
        val uLen = sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-5f)
        outGripFrameLocal[6] = ux / uLen
        outGripFrameLocal[7] = uy / uLen
        outGripFrameLocal[8] = uz / uLen
    }

    /**
     * Exact Analytical 2-Bone IK solver for RightArm (8) -> RightForeArm (9) -> RightHand (10).
     * Places the worker's cupped RIGHT PALM (not the wrist!) precisely on `pose.rightHandTarget`
     * with fingers wrapped forward along `pose.rightHandAim` and thumb pointing up along the
     * nozzle handle, blending smoothly with the FK pose via `ikW`.
     */
    private fun applyRightArmAnalyticalIk(
        model: LoadedFuelWorkerModel,
        pose: FuelWorkerWorldPose,
        ikW: Float
    ) {
        val cosY = cos(pose.yawRad)
        val sinY = sin(pose.yawRad)

        // Transform world palm target & world aim vector into worker model-local space
        val wdx = pose.rightHandTargetX - pose.x
        val wdy = pose.rightHandTargetY - pose.y
        val wdz = pose.rightHandTargetZ - pose.z
        val palmTgtX = wdx * cosY - wdz * sinY
        val palmTgtY = wdy
        val palmTgtZ = wdx * sinY + wdz * cosY

        var aimX = pose.rightHandAimX * cosY - pose.rightHandAimZ * sinY
        var aimY = pose.rightHandAimY
        var aimZ = pose.rightHandAimX * sinY + pose.rightHandAimZ * cosY
        val aimLen = sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ)
        if (aimLen > 1e-4f) {
            aimX /= aimLen; aimY /= aimLen; aimZ /= aimLen
        } else {
            aimX = 0f; aimY = 0f; aimZ = 1f
        }

        // Build anatomically exact right-hand target orientation basis:
        //   tX = -aimDir (so rest fingers -X point along +aimDir around trigger/handle)
        //   tZ = handleUp (orthonormalized world +Y perpendicular to aimDir -> thumb points UP!)
        //   tY = cross(tZ, tX) (back of hand faces outward-right; palm faces inward gripping handle!)
        val hxX = -aimX; val hxY = -aimY; val hxZ = -aimZ
        var hzX = 0f - aimX * aimY
        var hzY = 1f - aimY * aimY
        var hzZ = 0f - aimZ * aimY
        val hzLen = sqrt(hzX * hzX + hzY * hzY + hzZ * hzZ)
        if (hzLen > 1e-4f) {
            hzX /= hzLen; hzY /= hzLen; hzZ /= hzLen
        } else {
            hzX = 0f; hzY = 0f; hzZ = 1f
        }
        val hyX = hzY * hxZ - hzZ * hxY
        val hyY = hzZ * hxX - hzX * hxZ
        val hyZ = hzX * hxY - hzY * hxX

        // Back-solve exact Wrist IK target so the CUPPED PALM CENTER lands on palmTgt!
        val wristTgtX = palmTgtX - (hxX * PALM_LOCAL_DX + hyX * PALM_LOCAL_DY + hzX * PALM_LOCAL_DZ)
        val wristTgtY = palmTgtY - (hxY * PALM_LOCAL_DX + hyY * PALM_LOCAL_DY + hzY * PALM_LOCAL_DZ)
        val wristTgtZ = palmTgtZ - (hxZ * PALM_LOCAL_DX + hyZ * PALM_LOCAL_DY + hzZ * PALM_LOCAL_DZ)

        // Exact animated shoulder world position from FK Spine2 -> RightArm
        val shOff = 8 * 16
        val sx = worldMats[shOff + 12]
        val sy = worldMats[shOff + 13]
        val sz = worldMats[shOff + 14]

        // Exact rest bone vectors and lengths
        val uRestX = model.restPosX[9] - model.restPosX[8]
        val uRestY = model.restPosY[9] - model.restPosY[8]
        val uRestZ = model.restPosZ[9] - model.restPosZ[8]
        val l1 = sqrt(uRestX * uRestX + uRestY * uRestY + uRestZ * uRestZ).coerceAtLeast(0.05f)

        val fRestX = model.restPosX[10] - model.restPosX[9]
        val fRestY = model.restPosY[10] - model.restPosY[9]
        val fRestZ = model.restPosZ[10] - model.restPosZ[9]
        val l2 = sqrt(fRestX * fRestX + fRestY * fRestY + fRestZ * fRestZ).coerceAtLeast(0.05f)

        // Solve 2-bone triangle (S -> E -> W) using Law of Cosines
        val dx = wristTgtX - sx
        val dy = wristTgtY - sy
        val dz = wristTgtZ - sz
        val rawDist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        val maxReach = (l1 + l2) * 0.992f
        val minReach = abs(l1 - l2) + 0.04f
        val d = rawDist.coerceIn(minReach, maxReach)

        val dirX = dx / rawDist
        val dirY = dy / rawDist
        val dirZ = dz / rawDist

        val cosA = ((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f)
        val sinA = sqrt((1f - cosA * cosA).coerceAtLeast(0f))

        // Anatomically natural right-elbow pole vector: downward, outward-right (-X), slightly rear (-Z)
        val poleX = -0.56f
        val poleY = -0.78f
        val poleZ = -0.28f
        val pDot = poleX * dirX + poleY * dirY + poleZ * dirZ
        var px = poleX - dirX * pDot
        var py = poleY - dirY * pDot
        var pz = poleZ - dirZ * pDot
        val pLen = sqrt(px * px + py * py + pz * pz)
        if (pLen > 1e-4f) {
            px /= pLen; py /= pLen; pz /= pLen
        } else {
            px = 0f; py = -1f; pz = 0f
        }

        // Solved elbow E and wrist W positions in worker-local space
        val ex = sx + dirX * (l1 * cosA) + px * (l1 * sinA)
        val ey = sy + dirY * (l1 * cosA) + py * (l1 * sinA)
        val ez = sz + dirZ * (l1 * cosA) + pz * (l1 * sinA)

        val wx = sx + dirX * d
        val wy = sy + dirY * d
        val wz = sz + dirZ * d

        // Natural twist reference vector for upper arm & forearm (keeps shoulder/elbow crease natural)
        val upperDirX = (ex - sx) / l1
        val upperDirY = (ey - sy) / l1
        val upperDirZ = (ez - sz) / l1

        val foreDx = wx - ex; val foreDy = wy - ey; val foreDz = wz - ez
        val foreLen = sqrt(foreDx * foreDx + foreDy * foreDy + foreDz * foreDz).coerceAtLeast(1e-4f)
        val foreDirX = foreDx / foreLen
        val foreDirY = foreDy / foreLen
        val foreDirZ = foreDz / foreLen

        // Bend-plane up hints: elbow bends upward-inward relative to pole, wrist aligns with hand back/up
        val armUpX = -px * 0.55f + 0f
        val armUpY = -py * 0.55f + 0.85f
        val armUpZ = -pz * 0.55f + 0.10f

        val foreUpX = hyX * 0.55f + armUpX * 0.45f
        val foreUpY = hyY * 0.55f + armUpY * 0.45f
        val foreUpZ = hyZ * 0.55f + armUpZ * 0.45f

        buildAlignedBoneMat(
            restDirX = uRestX / l1, restDirY = uRestY / l1, restDirZ = uRestZ / l1,
            tgtDirX = upperDirX, tgtDirY = upperDirY, tgtDirZ = upperDirZ,
            upHintX = armUpX, upHintY = armUpY, upHintZ = armUpZ,
            posX = sx, posY = sy, posZ = sz,
            out = ikArmMat
        )
        buildAlignedBoneMat(
            restDirX = fRestX / l2, restDirY = fRestY / l2, restDirZ = fRestZ / l2,
            tgtDirX = foreDirX, tgtDirY = foreDirY, tgtDirZ = foreDirZ,
            upHintX = foreUpX, upHintY = foreUpY, upHintZ = foreUpZ,
            posX = ex, posY = ey, posZ = ez,
            out = ikForeMat
        )

        // Exact RightHand world matrix directly from right-handed (hx, hy, hz) basis at solved wrist W
        ikHandMat[0] = hxX; ikHandMat[1] = hxY; ikHandMat[2] = hxZ; ikHandMat[3] = 0f
        ikHandMat[4] = hyX; ikHandMat[5] = hyY; ikHandMat[6] = hyZ; ikHandMat[7] = 0f
        ikHandMat[8] = hzX; ikHandMat[9] = hzY; ikHandMat[10] = hzZ; ikHandMat[11] = 0f
        ikHandMat[12] = wx; ikHandMat[13] = wy; ikHandMat[14] = wz; ikHandMat[15] = 1f

        // Smoothly blend FK worldMats with IK matrices (zero popping during enter/exit of IK!)
        blendOrthonormalMat4(worldMats, 8 * 16, ikArmMat, ikW)
        blendOrthonormalMat4(worldMats, 9 * 16, ikForeMat, ikW)
        blendOrthonormalMat4(worldMats, 10 * 16, ikHandMat, ikW)
    }

    /**
     * Builds exact right-handed 4x4 bone world matrix R = M_tgt * M_rest^T at position (posX, posY, posZ),
     * aligning `restDir` precisely to `tgtDir` without gimbal lock.
     */
    private fun buildAlignedBoneMat(
        restDirX: Float, restDirY: Float, restDirZ: Float,
        tgtDirX: Float, tgtDirY: Float, tgtDirZ: Float,
        upHintX: Float, upHintY: Float, upHintZ: Float,
        posX: Float, posY: Float, posZ: Float,
        out: FloatArray
    ) {
        // Rest orthonormal frame (rx, ry, rz) with restUp = (0, 1, 0)
        val rx0 = restDirX; val rx1 = restDirY; val rx2 = restDirZ
        var rz0 = rx1 * 0f - rx2 * 1f
        var rz1 = rx2 * 0f - rx0 * 0f
        var rz2 = rx0 * 1f - rx1 * 0f
        val rzLen = sqrt(rz0 * rz0 + rz1 * rz1 + rz2 * rz2).coerceAtLeast(1e-5f)
        rz0 /= rzLen; rz1 /= rzLen; rz2 /= rzLen
        val ry0 = rz1 * rx2 - rz2 * rx1
        val ry1 = rz2 * rx0 - rz0 * rx2
        val ry2 = rz0 * rx1 - rz1 * rx0

        // Target orthonormal frame (tx, ty, tz)
        val tx0 = tgtDirX; val tx1 = tgtDirY; val tx2 = tgtDirZ
        var tz0 = tx1 * upHintZ - tx2 * upHintY
        var tz1 = tx2 * upHintX - tx0 * upHintZ
        var tz2 = tx0 * upHintY - tx1 * upHintX
        var tzLen = sqrt(tz0 * tz0 + tz1 * tz1 + tz2 * tz2)
        if (tzLen < 1e-4f) {
            tz0 = tx1 * 1f - tx2 * 0f
            tz1 = tx2 * 0f - tx0 * 1f
            tz2 = tx0 * 0f - tx1 * 0f
            tzLen = sqrt(tz0 * tz0 + tz1 * tz1 + tz2 * tz2).coerceAtLeast(1e-5f)
        }
        tz0 /= tzLen; tz1 /= tzLen; tz2 /= tzLen
        val ty0 = tz1 * tx2 - tz2 * tx1
        val ty1 = tz2 * tx0 - tz0 * tx2
        val ty2 = tz0 * tx1 - tz1 * tx0

        // R = M_tgt * M_rest^T (column-major)
        out[0] = tx0 * rx0 + ty0 * ry0 + tz0 * rz0
        out[1] = tx1 * rx0 + ty1 * ry0 + tz1 * rz0
        out[2] = tx2 * rx0 + ty2 * ry0 + tz2 * rz0
        out[3] = 0f

        out[4] = tx0 * rx1 + ty0 * ry1 + tz0 * rz1
        out[5] = tx1 * rx1 + ty1 * ry1 + tz1 * rz1
        out[6] = tx2 * rx1 + ty2 * ry1 + tz2 * rz1
        out[7] = 0f

        out[8] = tx0 * rx2 + ty0 * ry2 + tz0 * rz2
        out[9] = tx1 * rx2 + ty1 * ry2 + tz1 * rz2
        out[10] = tx2 * rx2 + ty2 * ry2 + tz2 * rz2
        out[11] = 0f

        out[12] = posX; out[13] = posY; out[14] = posZ; out[15] = 1f
    }

    /**
     * Smoothly blends an orthonormal 4x4 matrix in `dst[dstOff..]` toward `src[0..15]` by `w`,
     * re-orthonormalizing the 3x3 rotation block via Gram-Schmidt so bone scale stays strictly 1.0.
     */
    private fun blendOrthonormalMat4(dst: FloatArray, dstOff: Int, src: FloatArray, w: Float) {
        if (w >= 0.999f) {
            System.arraycopy(src, 0, dst, dstOff, 16)
            return
        }
        val inv = 1f - w
        // Blend column 0 (X) and normalize
        var x0 = dst[dstOff + 0] * inv + src[0] * w
        var x1 = dst[dstOff + 1] * inv + src[1] * w
        var x2 = dst[dstOff + 2] * inv + src[2] * w
        val xLen = sqrt(x0 * x0 + x1 * x1 + x2 * x2).coerceAtLeast(1e-5f)
        x0 /= xLen; x1 /= xLen; x2 /= xLen

        // Blend column 2 (Z), orthogonalize against X, and normalize
        var z0 = dst[dstOff + 8] * inv + src[8] * w
        var z1 = dst[dstOff + 9] * inv + src[9] * w
        var z2 = dst[dstOff + 10] * inv + src[10] * w
        val dotXZ = x0 * z0 + x1 * z1 + x2 * z2
        z0 -= x0 * dotXZ; z1 -= x1 * dotXZ; z2 -= x2 * dotXZ
        val zLen = sqrt(z0 * z0 + z1 * z1 + z2 * z2).coerceAtLeast(1e-5f)
        z0 /= zLen; z1 /= zLen; z2 /= zLen

        // Reconstruct right-handed Y = Z x X
        val y0 = z1 * x2 - z2 * x1
        val y1 = z2 * x0 - z0 * x2
        val y2 = z0 * x1 - z1 * x0

        dst[dstOff + 0] = x0; dst[dstOff + 1] = x1; dst[dstOff + 2] = x2; dst[dstOff + 3] = 0f
        dst[dstOff + 4] = y0; dst[dstOff + 5] = y1; dst[dstOff + 6] = y2; dst[dstOff + 7] = 0f
        dst[dstOff + 8] = z0; dst[dstOff + 9] = z1; dst[dstOff + 10] = z2; dst[dstOff + 11] = 0f
        dst[dstOff + 12] = dst[dstOff + 12] * inv + src[12] * w
        dst[dstOff + 13] = dst[dstOff + 13] * inv + src[13] * w
        dst[dstOff + 14] = dst[dstOff + 14] * inv + src[14] * w
        dst[dstOff + 15] = 1f
    }

    /**
     * Builds column-major matrix M = Translate(tx,ty,tz) * Ry(ry) * Rx(rx) * Rz(rz).
     * Applying Rz first cleanly lowers T-pose arms down along the body BEFORE Rx swings them forward!
     */
    private fun buildTrsMat(
        tx: Float,
        ty: Float,
        tz: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        out: FloatArray
    ) {
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val cz = cos(rz); val sz = sin(rz)

        // R = Ry * Rx * Rz (column-major)
        val r00 = cy * cz + sy * sx * sz
        val r10 = cx * sz
        val r20 = -sy * cz + cy * sx * sz

        val r01 = -cy * sz + sy * sx * cz
        val r11 = cx * cz
        val r21 = sy * sz + cy * sx * cz

        val r02 = sy * cx
        val r12 = -sx
        val r22 = cy * cx

        out[0] = r00; out[1] = r10; out[2] = r20; out[3] = 0f
        out[4] = r01; out[5] = r11; out[6] = r21; out[7] = 0f
        out[8] = r02; out[9] = r12; out[10] = r22; out[11] = 0f
        out[12] = tx; out[13] = ty; out[14] = tz; out[15] = 1f
    }

    private fun mulMat4(
        a: FloatArray,
        aOff: Int,
        b: FloatArray,
        bOff: Int,
        out: FloatArray,
        oOff: Int
    ) {
        for (col in 0 until 4) {
            val b0 = b[bOff + col * 4 + 0]
            val b1 = b[bOff + col * 4 + 1]
            val b2 = b[bOff + col * 4 + 2]
            val b3 = b[bOff + col * 4 + 3]
            out[oOff + col * 4 + 0] = a[aOff + 0] * b0 + a[aOff + 4] * b1 + a[aOff + 8] * b2 + a[aOff + 12] * b3
            out[oOff + col * 4 + 1] = a[aOff + 1] * b0 + a[aOff + 5] * b1 + a[aOff + 9] * b2 + a[aOff + 13] * b3
            out[oOff + col * 4 + 2] = a[aOff + 2] * b0 + a[aOff + 6] * b1 + a[aOff + 10] * b2 + a[aOff + 14] * b3
            out[oOff + col * 4 + 3] = a[aOff + 3] * b0 + a[aOff + 7] * b1 + a[aOff + 11] * b2 + a[aOff + 15] * b3
        }
    }
}
