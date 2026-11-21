package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bharatbussim.game.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Our Lady of Dolours Syro-Malabar Catholic Basilica — "Puthanpally" (Puthenpally), Thrissur.
 *
 * Photo-matched 1:1 Indo-Gothic architectural recreation directly alongside ROAD_PUTHANPALLI_LINK:
 *  - Bright pure-white Neo-Gothic plaster facade with warm golden architectural floodlight washes
 *    grazing every corner buttress, pointed arch, and spire rib.
 *  - Ground-floor 3-bay pointed-arch entrance portico (narthex) with warm golden-illuminated vaulted
 *    interior, dark carved teak basilica double doors, glowing electric-blue liturgy info displays,
 *    white granite entrance steps, potted palms, and continuous pierced Gothic balustrade parapet.
 *  - Flanking Twin Front Bell Towers (44.5m / 146 ft proportion): square buttressed base with blind
 *    pointed Gothic tracery lancet windows & navy parish notice board, middle 12-spoke dark Gothic
 *    rose-window stage, upper octagonal belfry with warm amber-lit louvered lancet bell openings and
 *    a ring of tall Gothic needle pinnacles, crowned by golden-lit ribbed spires & gilded finials.
 *  - Central Gothic Nave Pediment: warm-gold central rose window, flanked by tall pointed lancets,
 *    steep central Gothic gable housing the golden-lit arched niche of Our Lady of Dolours, flanking
 *    gablets & pinnacle forest, and the signature glowing electric Royal-Blue Cross at the peak.
 *  - Full Cruciform Basilica Nave with flying buttresses, stained-glass lancets, side transepts, and
 *    Thrissur's world-famous 78m (260 ft) Rear Bible Tower rising behind the sanctuary.
 *
 * Placement Verification (Zero Road Overlap):
 *  - Campus footprint: X in 78.0..146.0, Z in -174.0..-93.0 (Front facade at Z = -104.0 facing North +Z).
 *  - ROAD_PUTHANPALLI_LINK runs cleanly North of Z = -90.1 at X = 78 and curves NE to Z = -64.5 at X = 112.
 *  - ROAD_SAKTHAN_TO_SWARAJ stays West of X = 61.5 (> 16.5m clear).
 *  - ROAD_SWARAJ_TO_MANNUTHY stays North of Z = -33.0 (> 60m clear).
 */
internal object PuthanpallyBasilicaRenderer {

    const val CENTER_X = 112.0f
    const val FRONT_FACADE_Z = -104.0f
    const val NAVE_REAR_Z = -146.0f
    const val BIBLE_TOWER_Z = -157.5f

    const val LEFT_TOWER_X = CENTER_X - 15.6f   // 96.4f
    const val RIGHT_TOWER_X = CENTER_X + 15.6f  // 127.6f

    // Authentic Puthanpally Indo-Gothic Material Palette (matched directly to reference photograph)
    private val MatBasilicaWhite = Color(0xFFF4F6F9)
    private val MatBasilicaWhiteShade = Color(0xFFD8DEE8)
    private val MatBasilicaWarmWash = Color(0xFFFDF3DC)      // Warm floodlit limestone white
    private val MatWarmGoldUplight = Color(0xFFF6C46A)       // Warm amber architectural uplight glow
    private val MatWarmAmberDeep = Color(0xFFD98B2B)         // Deep golden spire/belfry floodlight
    private val MatPorticoInteriorGlow = Color(0xFFFFF3CF)   // Luminous warm cream vaulted narthex
    private val MatGothicBlueCross = Color(0xFF2563EB)       // Signature glowing electric royal-blue cross
    private val MatGothicBlueGlow = Color(0xFF60A5FA)        // Luminous blue halo rim
    private val MatGildedGold = Color(0xFFFACC15)            // Gilded rooftop finials & statues
    private val MatTeakDoor = Color(0xFF5C3317)
    private val MatTeakDoorDark = Color(0xFF3A1E0C)
    private val MatGraniteStep = Color(0xFF8B8E94)
    private val MatGraniteStepTop = Color(0xFFB7BCC4)
    private val MatCourtyardPaver = Color(0xFF9E968E)
    private val MatCourtyardBorder = Color(0xFF7B736B)
    private val MatRoofSlate = Color(0xFF475569)
    private val MatRoofTerracotta = Color(0xFF9A3E2A)
    private val MatDarkRecess = Color(0xFF1E2430)
    private val MatPalmFoliage = Color(0xFF2D6A35)
    private val MatPalmPot = Color(0xFF8C4A2F)

    fun isPointClearOfPuthanpally(px: Float, pz: Float): Boolean {
        return !(px in 72.0f..152.0f && pz in -180.0f..-86.0f)
    }

    // =========================================================================
    // GROUND COURTYARD, FORECOURT APRON & ENTRANCE DRIVEWAY (BAKED TO ROAD VBO)
    // =========================================================================

    fun DrawScope.drawPuthanpallyGroundPlaza(
        rig: CameraRig,
        path: Path,
        camEye: Vec3
    ) {
        if (hypot(camEye.x - CENTER_X, camEye.z - (-126f)) > 480f) return

        // Main paved interlocking-granite Basilica Compound Courtyard (X = 78..146, Z = -173..-93)
        if (rig.buildClippedQuadPath(
                path,
                Vec3(78.0f, 0.056f, -173.0f),
                Vec3(146.0f, 0.056f, -173.0f),
                Vec3(146.0f, 0.056f, -93.0f),
                Vec3(78.0f, 0.056f, -93.0f)
            )
        ) {
            drawPath(path, MatCourtyardPaver)
        }

        // Seamless Entry Driveway Apron linking ROAD_PUTHANPALLI_LINK South curb into Basilica front plaza
        if (rig.buildClippedQuadPath(
                path,
                Vec3(95.0f, 0.0555f, -93.0f),
                Vec3(129.0f, 0.0555f, -93.0f),
                Vec3(129.0f, 0.0555f, -72.0f),
                Vec3(95.0f, 0.0555f, -79.5f)
            )
        ) {
            drawPath(path, MatCourtyardPaver)
        }

        // Decorative darker granite border walkway framing the front forecourt
        if (rig.buildClippedQuadPath(
                path,
                Vec3(86.0f, 0.058f, -102.5f),
                Vec3(138.0f, 0.058f, -102.5f),
                Vec3(138.0f, 0.058f, -94.0f),
                Vec3(86.0f, 0.058f, -94.0f)
            )
        ) {
            drawPath(path, MatCourtyardBorder)
        }
    }

    // =========================================================================
    // HIGH-RESOLUTION PROCEDURAL INDO-GOTHIC TEXTURES (MATCHED TO PHOTO)
    // =========================================================================

    private fun createTowerRoseWindowSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_TOWER_ROSE_V1",
        bmpWidth = 256,
        bmpHeight = 256,
        isDoubleSided = false
    ) {
        val w = 256f
        val h = 256f
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Bright white plaster panel with subtle recessed shadow frame
        p.color = 0xFFF1F4F8.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        p.color = 0xFFCBD5E1.toInt()
        c.drawRect(12f, 12f, w - 12f, h - 12f, p)

        // Deep moulded circular rose window recess
        val cx = w * 0.5f
        val cy = h * 0.5f
        val rOuter = 82f
        p.style = Paint.Style.FILL
        p.color = 0xFFD5DCE6.toInt()
        c.drawCircle(cx, cy, rOuter + 12f, p)
        p.color = 0xFF1A202C.toInt()
        c.drawCircle(cx, cy, rOuter, p)

        // 12 white Gothic radiating spokes & trefoil petal rings
        p.style = Paint.Style.STROKE
        p.color = 0xFFE2E8F0.toInt()
        p.strokeWidth = 5f
        c.drawCircle(cx, cy, rOuter - 5f, p)
        c.drawCircle(cx, cy, rOuter * 0.58f, p)
        c.drawCircle(cx, cy, rOuter * 0.24f, p)
        p.strokeWidth = 4.2f
        for (i in 0 until 12) {
            val a = (i * 30.0) * PI / 180.0
            c.drawLine(
                cx + (rOuter * 0.24f * cos(a)).toFloat(),
                cy + (rOuter * 0.24f * sin(a)).toFloat(),
                cx + ((rOuter - 5f) * cos(a)).toFloat(),
                cy + ((rOuter - 5f) * sin(a)).toFloat(),
                p
            )
            // Outer trefoil cusp circle between spokes
            val aMid = ((i * 30.0) + 15.0) * PI / 180.0
            c.drawCircle(
                cx + (rOuter * 0.78f * cos(aMid)).toFloat(),
                cy + (rOuter * 0.78f * sin(aMid)).toFloat(),
                10f,
                p
            )
        }
        p.style = Paint.Style.FILL
        p.color = 0xFFF1F5F9.toInt()
        c.drawCircle(cx, cy, 11f, p)
        bmp
    }

    private fun createCentralGoldRoseSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_CENTRAL_GOLD_ROSE_V1",
        bmpWidth = 256,
        bmpHeight = 256,
        isDoubleSided = false
    ) {
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFF5F3EE.toInt()
        c.drawRect(0f, 0f, 256f, 256f, p)

        val cx = 128f
        val cy = 128f
        val r = 88f
        // Warm golden backlit interior glow (matching center wheel window in photo!)
        p.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(0xFFFFF7CC.toInt(), 0xFFF5C451.toInt(), 0xFF92400E.toInt()),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.color = 0xFF451A03.toInt()
        p.strokeWidth = 7f
        c.drawCircle(cx, cy, r, p)
        c.drawCircle(cx, cy, r * 0.55f, p)
        for (i in 0 until 8) {
            val a = i * 45.0 * PI / 180.0
            c.drawLine(cx, cy, cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat(), p)
        }
        p.color = 0xFFF8FAFC.toInt()
        p.strokeWidth = 10f
        c.drawCircle(cx, cy, r + 9f, p)
        bmp
    }

    private fun createBelfryLouverLancetSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_BELFRY_LANCET_V1",
        bmpWidth = 128,
        bmpHeight = 320,
        isDoubleSided = false
    ) {
        val w = 128f
        val h = 320f
        val bmp = Bitmap.createBitmap(128, 320, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Warm golden floodlit plaster surround
        p.shader = LinearGradient(
            0f, h, 0f, 0f,
            intArrayOf(0xFFF6C26B.toInt(), 0xFFF5E6C8.toInt(), 0xFFECEFF4.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Pointed Gothic arch opening
        val arch = AndroidPath().apply {
            moveTo(22f, h - 16f)
            lineTo(22f, 96f)
            quadTo(22f, 36f, w * 0.5f, 16f)
            quadTo(w - 22f, 36f, w - 22f, 96f)
            lineTo(w - 22f, h - 16f)
            close()
        }
        p.shader = LinearGradient(
            0f, h, 0f, 20f,
            intArrayOf(0xFFE8A43A.toInt(), 0xFF5A3714.toInt(), 0xFF1F160C.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(arch, p)
        p.shader = null

        // Crisp horizontal dark acoustic belfry louvers
        p.color = 0xFF1E1710.toInt()
        p.strokeWidth = 6f
        var ly = 68f
        while (ly < h - 22f) {
            c.drawLine(28f, ly, w - 28f, ly, p)
            ly += 15f
        }

        // Molded white Gothic arch trim
        p.style = Paint.Style.STROKE
        p.color = 0xFFF8FAFC.toInt()
        p.strokeWidth = 8f
        c.drawPath(arch, p)
        bmp
    }

    private fun createBlindTraceryLancetSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_BLIND_TRACERY_V1",
        bmpWidth = 192,
        bmpHeight = 320,
        isDoubleSided = false
    ) {
        val w = 192f
        val h = 320f
        val bmp = Bitmap.createBitmap(192, 320, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFF3F5F8.toInt()
        c.drawRect(0f, 0f, w, h, p)

        val arch = AndroidPath().apply {
            moveTo(22f, h - 14f)
            lineTo(22f, 118f)
            quadTo(22f, 44f, w * 0.5f, 14f)
            quadTo(w - 22f, 44f, w - 22f, 118f)
            lineTo(w - 22f, h - 14f)
            close()
        }
        p.color = 0xFFD9DFE8.toInt()
        c.drawPath(arch, p)

        // Delicate Gothic vertical mullions & rose tracery inside pointed arch
        p.style = Paint.Style.STROKE
        p.color = 0xFF94A3B8.toInt()
        p.strokeWidth = 4f
        c.drawPath(arch, p)
        c.drawLine(w * 0.36f, 115f, w * 0.36f, h - 16f, p)
        c.drawLine(w * 0.64f, 115f, w * 0.64f, h - 16f, p)
        c.drawCircle(w * 0.5f, 84f, 24f, p)
        c.drawCircle(w * 0.5f, 84f, 12f, p)
        var ry = 135f
        while (ry < h - 24f) {
            c.drawLine(26f, ry, w - 26f, ry, p)
            ry += 28f
        }
        bmp
    }

    private fun createStatueNicheSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_STATUE_NICHE_V1",
        bmpWidth = 192,
        bmpHeight = 320,
        isDoubleSided = false
    ) {
        val w = 192f
        val h = 320f
        val bmp = Bitmap.createBitmap(192, 320, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFF4F6F9.toInt()
        c.drawRect(0f, 0f, w, h, p)

        // Deep pointed Gothic canopy niche glowing warm golden-white
        val niche = AndroidPath().apply {
            moveTo(26f, h - 18f)
            lineTo(26f, 116f)
            quadTo(26f, 42f, w * 0.5f, 16f)
            quadTo(w - 26f, 42f, w - 26f, 116f)
            lineTo(w - 26f, h - 18f)
            close()
        }
        p.shader = RadialGradient(
            w * 0.5f, h * 0.62f, 140f,
            intArrayOf(0xFFFFFCE0.toInt(), 0xFFFDE08D.toInt(), 0xFF78511A.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(niche, p)
        p.shader = null

        // Silhouette of Our Lady of Dolours statue (white & royal-blue robe + golden halo)
        p.color = 0xFFFFF3B0.toInt()
        c.drawCircle(w * 0.5f, 152f, 24f, p)
        p.color = 0xFF1D4ED8.toInt()
        val robe = AndroidPath().apply {
            moveTo(w * 0.5f, 162f)
            lineTo(w * 0.34f, h - 28f)
            lineTo(w * 0.66f, h - 28f)
            close()
        }
        c.drawPath(robe, p)
        p.color = 0xFFF8FAFC.toInt()
        val innerRobe = AndroidPath().apply {
            moveTo(w * 0.5f, 168f)
            lineTo(w * 0.42f, h - 28f)
            lineTo(w * 0.58f, h - 28f)
            close()
        }
        c.drawPath(innerRobe, p)
        c.drawCircle(w * 0.5f, 152f, 11f, p)

        // White Gothic tracery surround
        p.style = Paint.Style.STROKE
        p.color = 0xFFF8FAFC.toInt()
        p.strokeWidth = 9f
        c.drawPath(niche, p)
        c.drawCircle(w * 0.5f, 76f, 24f, p)
        bmp
    }

    private fun createBasilicaNameplateSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_NAMEPLATE_V1",
        bmpWidth = 1024,
        bmpHeight = 96,
        isDoubleSided = false
    ) {
        val w = 1024f
        val h = 96f
        val bmp = Bitmap.createBitmap(1024, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF422006.toInt(), 0xFF713F12.toInt(), 0xFF422006.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.color = 0xFFFDE047.toInt()
        p.strokeWidth = 4f
        c.drawRect(4f, 4f, w - 4f, h - 4f, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        p.color = 0xFFFFFBEB.toInt()
        p.textSize = 36f
        c.drawText("OUR LADY OF DOLOURS BASILICA  •  പുത്തൻപള്ളി", w * 0.5f, 62f, p)
        bmp
    }

    private fun createParishNoticeBoardSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_PARISH_NOTICE_V1",
        bmpWidth = 384,
        bmpHeight = 256,
        isDoubleSided = false
    ) {
        val w = 384f
        val h = 256f
        val bmp = Bitmap.createBitmap(384, 256, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Navy/indigo parish notice board matching the lower right tower in the reference photo
        p.color = 0xFF1E2952.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.color = 0xFFE2E8F0.toInt()
        c.drawRect(14f, 48f, w - 14f, h - 14f, p)

        p.color = 0xFFFDE047.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 22f
        c.drawText("BASILICA HOLY MASS & TIMINGS", w * 0.5f, 33f, p)

        // Schedule grid columns
        p.color = 0xFF1E293B.toInt()
        p.textAlign = Paint.Align.LEFT
        p.textSize = 15f
        val rows = arrayOf(
            "DAILY HOLY QURBANA : 5:30 • 6:30 • 7:30 AM",
            "EVENING MASS       : 5:30 PM (MALAYALAM)",
            "SUNDAY MASSES      : 5:30 • 7:00 • 9:00 AM",
            "NOVENA (FRIDAY)    : 10:30 AM • 5:30 PM",
            "BIBLE TOWER VIEW   : 8:30 AM – 7:00 PM"
        )
        var ry = 82f
        for (row in rows) {
            c.drawText(row, 26f, ry, p)
            ry += 32f
        }
        bmp
    }

    private fun createPorticoInfoScreenSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "PUTHANPALLY_PORTICO_SCREEN_V1",
        bmpWidth = 256,
        bmpHeight = 160,
        isDoubleSided = false
    ) {
        val bmp = Bitmap.createBitmap(256, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, 0f, 160f,
            intArrayOf(0xFF38BDF8.toInt(), 0xFF1D4ED8.toInt(), 0xFF1E3A8A.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, 256f, 160f, p)
        p.shader = null
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 22f
        c.drawText("PUTHENPALLY", 128f, 58f, p)
        p.textSize = 16f
        p.color = 0xFFE0F2FE.toInt()
        c.drawText("THRISSUR BASILICA", 128f, 92f, p)
        p.textSize = 14f
        c.drawText("ESTD. 1929 • INDO-GOTHIC", 128f, 124f, p)
        bmp
    }

    // =========================================================================
    // STATIC 3D GEOMETRY CACHE (BAKED INTO SPATIAL-CHUNKED GPU LANDMARK VBO)
    // =========================================================================

    @Volatile
    private var cachedFaces: List<QuadFace3D>? = null

    fun collectAllPuthanpallyStaticFaces(outFaces: MutableList<QuadFace3D>) {
        val faces = cachedFaces ?: buildPuthanpallyBasilica3D().also { cachedFaces = it }
        outFaces.addAll(faces)
    }

    fun collectPuthanpallyFacesNearCamera(camEye: Vec3, outFaces: MutableList<QuadFace3D>) {
        if (hypot(camEye.x - CENTER_X, camEye.z - (-126f)) > 520f) return
        collectAllPuthanpallyStaticFaces(outFaces)
    }

    private fun buildPuthanpallyBasilica3D(): List<QuadFace3D> {
        val out = ArrayList<QuadFace3D>(1400)
        val cx = CENTER_X
        val fz = FRONT_FACADE_Z

        val towerRoseTex = createTowerRoseWindowSpec()
        val centralRoseTex = createCentralGoldRoseSpec()
        val belfryLancetTex = createBelfryLouverLancetSpec()
        val blindTraceryTex = createBlindTraceryLancetSpec()
        val statueNicheTex = createStatueNicheSpec()
        val nameplateTex = createBasilicaNameplateSpec()
        val parishNoticeTex = createParishNoticeBoardSpec()
        val infoScreenTex = createPorticoInfoScreenSpec()

        // ---------------------------------------------------------------------
        // 1. GRAND TERRACED GRANITE ENTRANCE STEPS, POTTED PALMS & LOW BOLLARDS
        // ---------------------------------------------------------------------
        for (step in 0 until 5) {
            val y0 = 0.06f + step * 0.22f
            val y1 = y0 + 0.22f
            val zFront = fz + 4.4f - step * 0.78f
            val zBack = fz + 0.4f
            addBox(out, cx - 21.5f, y0, zBack, cx + 21.5f, y1, zFront, MatGraniteStepTop, MatGraniteStep, MatGraniteStep)
        }
        val baseY = 1.16f // Top plinth terrace level

        // Ornamental potted palms along the base of the portico piers (matching photo)
        for (px in floatArrayOf(cx - 12.2f, cx - 8.6f, cx - 4.4f, cx + 4.4f, cx + 8.6f, cx + 12.2f)) {
            addBox(out, px - 0.42f, baseY, fz + 0.1f, px + 0.42f, baseY + 0.72f, fz + 0.92f, MatPalmPot, MatPalmPot, MatPalmPot)
            addBox(out, px - 0.78f, baseY + 0.72f, fz - 0.15f, px + 0.78f, baseY + 1.85f, fz + 1.15f, MatPalmFoliage, MatPalmFoliage, MatPalmFoliage)
        }

        // Low white compound wall flanking the courtyard sides (East & West) & rear perimeter
        addBox(out, 78.2f, 0.06f, -172.5f, 79.0f, 2.0f, -93.5f, MatBasilicaWhite, MatBasilicaWhiteShade, MatBasilicaWhite)
        addBox(out, 145.0f, 0.06f, -172.5f, 145.8f, 2.0f, -93.5f, MatBasilicaWhite, MatBasilicaWhiteShade, MatBasilicaWhite)
        addBox(out, 78.2f, 0.06f, -172.8f, 145.8f, 2.0f, -172.0f, MatBasilicaWhite, MatBasilicaWhiteShade, MatBasilicaWhite)
        // Grand Open Courtyard Entry Gate Pillars facing ROAD_PUTHANPALLI_LINK (wide 24m clear opening!)
        for (gx in floatArrayOf(cx - 13.5f, cx + 13.5f)) {
            addBox(out, gx - 0.85f, 0.06f, -94.2f, gx + 0.85f, 4.4f, -92.6f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhite)
            addPyramidSpire(out, gx, 4.4f, -93.4f, 0.78f, 2.2f, MatBasilicaWhite, MatWarmGoldUplight)
            addCross3D(out, gx, 6.6f, -93.4f, 0.95f, 0.12f, MatGildedGold)
        }

        // ---------------------------------------------------------------------
        // 2. GROUND-FLOOR 3-BAY POINTED-ARCH ENTRANCE PORTICO (NARTHEX)
        //    Warm golden vaulted interior, carved teak doors, glowing blue info screens
        // ---------------------------------------------------------------------
        val porticoBackZ = fz - 5.2f
        val porticoTopY = baseY + 9.2f // 10.36m

        // Warm golden-illuminated back wall & vaulted portico soffit ceiling
        addBox(out, cx - 12.2f, baseY, porticoBackZ - 0.6f, cx + 12.2f, porticoTopY, porticoBackZ, MatPorticoInteriorGlow, MatPorticoInteriorGlow, MatPorticoInteriorGlow)
        addBox(out, cx - 12.2f, porticoTopY - 0.45f, porticoBackZ, cx + 12.2f, porticoTopY + 0.15f, fz + 0.1f, MatBasilicaWhite, MatPorticoInteriorGlow, MatPorticoInteriorGlow)

        // Grand Carved Dark Teak Double Basilica Entrance Doors (central & side portals)
        addBox(out, cx - 2.35f, baseY, porticoBackZ, cx + 2.35f, baseY + 4.8f, porticoBackZ + 0.35f, MatTeakDoor, MatTeakDoor, MatTeakDoorDark)
        // Golden sunburst medallion emblem right above the central teak doors (seen in photo!)
        addBox(out, cx - 1.05f, baseY + 5.1f, porticoBackZ, cx + 1.05f, baseY + 6.7f, porticoBackZ + 0.30f, MatWarmGoldUplight, MatGildedGold, MatWarmAmberDeep)
        for (sideDoorX in floatArrayOf(cx - 8.6f, cx + 8.6f)) {
            addBox(out, sideDoorX - 1.35f, baseY, porticoBackZ, sideDoorX + 1.35f, baseY + 4.1f, porticoBackZ + 0.30f, MatTeakDoor, MatTeakDoorDark, MatTeakDoor)
        }
        // Twin Glowing Blue Digital Liturgy Information Screens flanking the central doors!
        for (scrX in floatArrayOf(cx - 4.4f, cx + 4.4f)) {
            out.add(
                QuadFace3D(
                    Vec3(scrX - 1.25f, baseY + 2.4f, porticoBackZ + 0.25f),
                    Vec3(scrX + 1.25f, baseY + 2.4f, porticoBackZ + 0.25f),
                    Vec3(scrX + 1.25f, baseY + 4.1f, porticoBackZ + 0.25f),
                    Vec3(scrX - 1.25f, baseY + 4.1f, porticoBackZ + 0.25f),
                    MatGothicBlueGlow,
                    signboard = infoScreenTex
                )
            )
        }

        // 4 Deep Buttressed White Portico Piers framing the 3 Pointed Gothic Arches
        val pierXs = floatArrayOf(cx - 11.4f, cx - 4.5f, cx + 4.5f, cx + 11.4f)
        for (px in pierXs) {
            addBox(out, px - 0.92f, baseY, fz - 0.7f, px + 0.92f, porticoTopY, fz + 0.45f, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
            // Stepped Gothic front buttress fin with warm uplight grazing
            addBox(out, px - 0.42f, baseY, fz + 0.45f, px + 0.42f, porticoTopY - 0.8f, fz + 1.05f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)
        }

        // 3 Crisp White Pointed Gothic Arch Spandrels across the Portico Front (Left, Wide Center, Right)
        addPointedArchHeader(out, cx - 10.5f, cx - 5.4f, baseY + 4.6f, porticoTopY, fz, depth = 0.85f)
        addPointedArchHeader(out, cx - 3.6f, cx + 3.6f, baseY + 5.0f, porticoTopY, fz, depth = 0.85f)
        addPointedArchHeader(out, cx + 5.4f, cx + 10.5f, baseY + 4.6f, porticoTopY, fz, depth = 0.85f)

        // Full-Width Pierced Gothic Stone Balustrade Parapet above the 3 Portico Arches (Y = 10.36..12.1m)
        addBox(out, cx - 12.2f, porticoTopY, fz - 0.35f, cx + 12.2f, porticoTopY + 0.38f, fz + 0.78f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)
        addBox(out, cx - 12.2f, porticoTopY + 1.48f, fz - 0.25f, cx + 12.2f, porticoTopY + 1.82f, fz + 0.70f, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
        var balX = cx - 11.6f
        while (balX <= cx + 11.6f) {
            addBox(out, balX - 0.14f, porticoTopY + 0.38f, fz + 0.18f, balX + 0.14f, porticoTopY + 1.48f, fz + 0.56f, MatBasilicaWhite, MatBasilicaWhiteShade, MatBasilicaWhite)
            balX += 0.78f
        }

        // Glowing Golden Backlit Basilica Nameplate centered on the Balustrade Parapet
        out.add(
            QuadFace3D(
                Vec3(cx - 8.2f, porticoTopY + 0.28f, fz + 0.84f),
                Vec3(cx + 8.2f, porticoTopY + 0.28f, fz + 0.84f),
                Vec3(cx + 8.2f, porticoTopY + 1.72f, fz + 0.84f),
                Vec3(cx - 8.2f, porticoTopY + 1.72f, fz + 0.84f),
                MatWarmGoldUplight,
                signboard = nameplateTex
            )
        )

        // ---------------------------------------------------------------------
        // 3. CENTRAL NAVE UPPER FACADE, GOLDEN ROSE WINDOW, STATUE NICHE &
        //    SIGNATURE GLOWING ROYAL-BLUE NEON CROSS AT THE PEAK!
        // ---------------------------------------------------------------------
        val upperZ = fz - 1.2f
        val secondTopY = porticoTopY + 9.2f // 19.56m

        // Main central white facade wall behind balustrade
        addBox(out, cx - 12.2f, porticoTopY + 0.2f, upperZ - 3.6f, cx + 12.2f, secondTopY, upperZ, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)

        // Central Warm-Gold Backlit Gothic Rose Window (exact center between twin towers!)
        out.add(
            QuadFace3D(
                Vec3(cx - 2.35f, porticoTopY + 3.0f, upperZ + 0.06f),
                Vec3(cx + 2.35f, porticoTopY + 3.0f, upperZ + 0.06f),
                Vec3(cx + 2.35f, porticoTopY + 7.7f, upperZ + 0.06f),
                Vec3(cx - 2.35f, porticoTopY + 7.7f, upperZ + 0.06f),
                MatWarmGoldUplight,
                signboard = centralRoseTex
            )
        )

        // Flanking Gothic vertical pilasters with warm golden uplighting & blind lancet arches
        for (px in floatArrayOf(cx - 10.6f, cx - 6.8f, cx - 3.3f, cx + 3.3f, cx + 6.8f, cx + 10.6f)) {
            addBox(out, px - 0.36f, porticoTopY + 1.5f, upperZ, px + 0.36f, secondTopY + 0.6f, upperZ + 0.65f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)
            // Warm golden uplight accent near pilaster base
            addBox(out, px - 0.30f, porticoTopY + 1.6f, upperZ + 0.65f, px + 0.30f, porticoTopY + 4.4f, upperZ + 0.74f, MatWarmGoldUplight, MatWarmGoldUplight, MatWarmGoldUplight)
        }
        for (lx in floatArrayOf(cx - 8.7f, cx - 5.05f, cx + 5.05f, cx + 8.7f)) {
            out.add(
                QuadFace3D(
                    Vec3(lx - 1.15f, porticoTopY + 2.6f, upperZ + 0.05f),
                    Vec3(lx + 1.15f, porticoTopY + 2.6f, upperZ + 0.05f),
                    Vec3(lx + 1.15f, porticoTopY + 7.4f, upperZ + 0.05f),
                    Vec3(lx - 1.15f, porticoTopY + 7.4f, upperZ + 0.05f),
                    MatBasilicaWhite,
                    signboard = blindTraceryTex
                )
            )
        }

        // Dramatic Steep Central Gothic Gable Pediment (Y = 19.56..29.6m) housing Our Lady of Dolours Niche!
        addGothicTriangularGable(out, cx, secondTopY, upperZ, halfW = 5.4f, height = 10.2f, depth = 2.6f)
        // Flanking Secondary Pointed Gablets (Left & Right, matching photo silhouette!)
        addGothicTriangularGable(out, cx - 7.8f, secondTopY, upperZ - 0.1f, halfW = 2.6f, height = 5.2f, depth = 1.6f)
        addGothicTriangularGable(out, cx + 7.8f, secondTopY, upperZ - 0.1f, halfW = 2.6f, height = 5.2f, depth = 1.6f)

        // Warm Golden-Illuminated Statue Niche of Our Lady of Dolours centered inside the Main Gable
        out.add(
            QuadFace3D(
                Vec3(cx - 1.75f, secondTopY + 0.6f, upperZ + 0.08f),
                Vec3(cx + 1.75f, secondTopY + 0.6f, upperZ + 0.08f),
                Vec3(cx + 1.75f, secondTopY + 6.4f, upperZ + 0.08f),
                Vec3(cx - 1.75f, secondTopY + 6.4f, upperZ + 0.08f),
                MatWarmGoldUplight,
                signboard = statueNicheTex
            )
        )

        // Forest of Slender White Gothic Needle Pinnacles rising across the central gable roofline
        val pinnacleSpecs = arrayOf(
            Triple(cx - 10.6f, secondTopY, 5.2f),
            Triple(cx - 7.8f, secondTopY + 4.9f, 3.2f),
            Triple(cx - 5.2f, secondTopY + 0.4f, 6.2f),
            Triple(cx - 3.3f, secondTopY + 3.8f, 5.6f),
            Triple(cx + 3.3f, secondTopY + 3.8f, 5.6f),
            Triple(cx + 5.2f, secondTopY + 0.4f, 6.2f),
            Triple(cx + 7.8f, secondTopY + 4.9f, 3.2f),
            Triple(cx + 10.6f, secondTopY, 5.2f)
        )
        for ((px, py, ph) in pinnacleSpecs) {
            addBox(out, px - 0.34f, py, upperZ - 0.35f, px + 0.34f, py + ph * 0.55f, upperZ + 0.38f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)
            addPyramidSpire(out, px, py + ph * 0.55f, upperZ + 0.02f, 0.38f, ph * 0.45f, MatBasilicaWhite, MatBasilicaWhiteShade)
        }

        // SIGNATURE GLOWING ELECTRIC ROYAL-BLUE CROSS AT THE PEAK OF THE CENTRAL GABLE (Exact match to photo!)
        val crossBaseY = secondTopY + 10.0f // 29.56m
        // Outer luminous blue glow halo + bright core electric blue neon cross
        addCross3D(out, cx, crossBaseY, upperZ + 0.05f, height = 3.85f, thick = 0.44f, col = MatGothicBlueGlow)
        addCross3D(out, cx, crossBaseY + 0.08f, upperZ + 0.14f, height = 3.70f, thick = 0.28f, col = MatGothicBlueCross)

        // ---------------------------------------------------------------------
        // 4. FLANKING TWIN FRONT BELL TOWERS (LEFT & RIGHT — 44.5m / 146 FT!)
        //    Exact 4-Stage Silhouette from Reference Photo:
        //    Stage 1: Buttressed Base + Blind Gothic Tracery + Warm Golden Uplights
        //    Stage 2: 12-Spoke Dark Gothic Rose Window Stage + Corner Pinnacles
        //    Stage 3: Octagonal Belfry Lantern with Warm Amber Louvered Lancets
        //    Stage 4: Warm Gold-Washed Ribbed Octagonal Spire + Spirelet Crown + Golden Finial
        // ---------------------------------------------------------------------
        buildFrontBellTower(
            out = out,
            tx = LEFT_TOWER_X,
            tz = fz - 1.4f,
            baseY = baseY,
            isRightTower = false,
            towerRoseTex = towerRoseTex,
            belfryLancetTex = belfryLancetTex,
            blindTraceryTex = blindTraceryTex,
            parishNoticeTex = parishNoticeTex
        )
        buildFrontBellTower(
            out = out,
            tx = RIGHT_TOWER_X,
            tz = fz - 1.4f,
            baseY = baseY,
            isRightTower = true,
            towerRoseTex = towerRoseTex,
            belfryLancetTex = belfryLancetTex,
            blindTraceryTex = blindTraceryTex,
            parishNoticeTex = parishNoticeTex
        )

        // ---------------------------------------------------------------------
        // 5. GRAND CRUCIFORM BASILICA NAVE, SIDE AISLES, TRANSEPTS & FLYING BUTTRESSES
        // ---------------------------------------------------------------------
        val naveFrontZ = fz - 4.8f
        val naveRearZ = NAVE_REAR_Z
        val naveHalfW = 12.2f
        val naveWallTopY = 17.8f
        val naveRidgeY = 23.6f

        // Main central white nave hall
        addBox(out, cx - naveHalfW, 0.06f, naveRearZ, cx + naveHalfW, naveWallTopY, naveFrontZ, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
        // Pitched Basilica Nave Roof (North-South ridge running back toward the Bible Tower)
        addLongitudinalPitchedRoof(out, cx - naveHalfW - 0.5f, cx + naveHalfW + 0.5f, naveRearZ, naveFrontZ, naveWallTopY, naveRidgeY, MatRoofSlate, MatRoofTerracotta)

        // Lower Side Aisles (West & East) + 6 Rhythmic Gothic Exterior Buttresses & Lancet Windows per side!
        val aisleOuterHW = 16.4f
        addBox(out, cx - aisleOuterHW, 0.06f, naveRearZ + 3f, cx - naveHalfW, 10.6f, naveFrontZ - 2f, MatBasilicaWhite, MatBasilicaWhiteShade, MatBasilicaWhite)
        addBox(out, cx + naveHalfW, 0.06f, naveRearZ + 3f, cx + aisleOuterHW, 10.6f, naveFrontZ - 2f, MatBasilicaWhite, MatBasilicaWhiteShade, MatBasilicaWhite)

        for (bay in 0 until 6) {
            val bz = naveFrontZ - 5.0f - bay * 6.2f
            for (side in floatArrayOf(-1f, 1f)) {
                val bx = cx + side * (aisleOuterHW + 0.6f)
                // Stepped flying buttress pier with pinnacle
                addBox(out, bx - 0.65f, 0.06f, bz - 0.55f, bx + 0.65f, 12.8f, bz + 0.55f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)
                addPyramidSpire(out, bx, 12.8f, bz, 0.62f, 2.8f, MatBasilicaWhite, MatBasilicaWhiteShade)
                // Clerestory Gothic lancet window panel between buttresses
                val wx = cx + side * (naveHalfW + 0.06f)
                val wz0 = bz - 1.5f
                val wz1 = bz + 1.5f
                out.add(
                    QuadFace3D(
                        Vec3(wx, 11.4f, if (side > 0f) wz0 else wz1),
                        Vec3(wx, 11.4f, if (side > 0f) wz1 else wz0),
                        Vec3(wx, 16.4f, if (side > 0f) wz1 else wz0),
                        Vec3(wx, 16.4f, if (side > 0f) wz0 else wz1),
                        MatBasilicaWhite,
                        signboard = blindTraceryTex
                    )
                )
            }
        }

        // Cruciform Side Transept Wings (Z = -134..-145, projecting West & East to ±21.5m!)
        addBox(out, cx - 21.5f, 0.06f, -145.0f, cx + 21.5f, 16.6f, -133.5f, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
        for (side in floatArrayOf(-1f, 1f)) {
            val tx = cx + side * 21.56f
            out.add(
                QuadFace3D(
                    Vec3(tx, 9.6f, if (side > 0f) -141.6f else -136.9f),
                    Vec3(tx, 9.6f, if (side > 0f) -136.9f else -141.6f),
                    Vec3(tx, 14.3f, if (side > 0f) -136.9f else -141.6f),
                    Vec3(tx, 14.3f, if (side > 0f) -141.6f else -136.9f),
                    MatBasilicaWhite,
                    signboard = towerRoseTex
                )
            )
        }

        // ---------------------------------------------------------------------
        // 6. ICONIC 78m (260 FT) REAR BIBLE TOWER — TALLEST CHURCH TOWER IN ASIA!
        //    Soaring 4-Tier Pure-White Indo-Gothic Skyscraper Tower Behind Sanctuary
        // ---------------------------------------------------------------------
        buildRearBibleTower(out, cx, BIBLE_TOWER_Z, towerRoseTex, belfryLancetTex)

        return out
    }

    private fun buildFrontBellTower(
        out: MutableList<QuadFace3D>,
        tx: Float,
        tz: Float,
        baseY: Float,
        isRightTower: Boolean,
        towerRoseTex: SignboardQuadSpec,
        belfryLancetTex: SignboardQuadSpec,
        blindTraceryTex: SignboardQuadSpec,
        parishNoticeTex: SignboardQuadSpec
    ) {
        val hw = 3.95f
        val stage1Top = baseY + 10.6f   // 11.76m
        val stage2Top = stage1Top + 9.4f // 21.16m
        val belfryTop = stage2Top + 10.6f // 31.76m
        val spireTop = belfryTop + 12.6f  // 44.36m (~146 ft iconic height!)

        // STAGE 1: Massive Square White Tower Base + Stepped Diagonal Corner Buttresses with Golden Uplights!
        addBox(out, tx - hw, 0.06f, tz - hw, tx + hw, stage1Top, tz + hw, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                val bx = tx + sx * (hw + 0.55f)
                val bz = tz + sz * (hw + 0.55f)
                // Lower wide buttress plinth bathed in warm golden floodlight (exact match to photo!)
                addBox(out, bx - 0.72f, 0.06f, bz - 0.72f, bx + 0.72f, stage1Top - 1.6f, bz + 0.72f, MatBasilicaWarmWash, MatWarmGoldUplight, MatBasilicaWarmWash)
                // Tapered Gothic cap on corner buttress
                addPyramidSpire(out, bx, stage1Top - 1.6f, bz, 0.72f, 2.2f, MatBasilicaWhite, MatBasilicaWarmWash)
                // Upper slender corner pilaster continuing up Stage 2
                addBox(out, bx - 0.44f, stage1Top - 0.4f, bz - 0.44f, bx + 0.44f, stage2Top + 0.8f, bz + 0.44f, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
                // Tall Gothic corner pinnacle rising at the top of Stage 2
                addPyramidSpire(out, bx, stage2Top + 0.8f, bz, 0.48f, 3.6f, MatBasilicaWhite, MatBasilicaWhiteShade)
            }
        }

        // Front pointed blind Gothic tracery window on Tower Base
        out.add(
            QuadFace3D(
                Vec3(tx - 2.3f, baseY + 1.2f, tz + hw + 0.06f),
                Vec3(tx + 2.3f, baseY + 1.2f, tz + hw + 0.06f),
                Vec3(tx + 2.3f, stage1Top - 1.0f, tz + hw + 0.06f),
                Vec3(tx - 2.3f, stage1Top - 1.0f, tz + hw + 0.06f),
                MatBasilicaWhite,
                signboard = blindTraceryTex
            )
        )
        // Framed Navy & White Parish Mass Schedule Notice Board on the lower Right Tower (as in photo!)
        if (isRightTower) {
            out.add(
                QuadFace3D(
                    Vec3(tx - 2.75f, baseY + 0.25f, tz + hw + 0.14f),
                    Vec3(tx + 2.75f, baseY + 0.25f, tz + hw + 0.14f),
                    Vec3(tx + 2.75f, baseY + 3.95f, tz + hw + 0.14f),
                    Vec3(tx - 2.75f, baseY + 3.95f, tz + hw + 0.14f),
                    Color(0xFF1E2952),
                    signboard = parishNoticeTex
                )
            )
        }

        // Horizontal projecting cornice molding between Stage 1 and Stage 2
        addBox(out, tx - hw - 0.45f, stage1Top - 0.22f, tz - hw - 0.45f, tx + hw + 0.45f, stage1Top + 0.34f, tz + hw + 0.45f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)

        // STAGE 2: Middle Square Rose-Window Stage (12-Spoke Dark Gothic Wheel Windows on Front & Outer Side!)
        addBox(out, tx - hw + 0.12f, stage1Top, tz - hw + 0.12f, tx + hw - 0.12f, stage2Top, tz + hw - 0.12f, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
        // Front 12-spoke Rose Window
        out.add(
            QuadFace3D(
                Vec3(tx - 2.65f, stage1Top + 1.8f, tz + hw - 0.05f),
                Vec3(tx + 2.65f, stage1Top + 1.8f, tz + hw - 0.05f),
                Vec3(tx + 2.65f, stage1Top + 7.1f, tz + hw - 0.05f),
                Vec3(tx - 2.65f, stage1Top + 7.1f, tz + hw - 0.05f),
                MatBasilicaWhite,
                signboard = towerRoseTex
            )
        )
        // Outer side 12-spoke Rose Window
        val sideSign = if (isRightTower) 1f else -1f
        val sideX = tx + sideSign * (hw - 0.05f)
        out.add(
            QuadFace3D(
                Vec3(sideX, stage1Top + 1.8f, if (isRightTower) tz - 2.65f else tz + 2.65f),
                Vec3(sideX, stage1Top + 1.8f, if (isRightTower) tz + 2.65f else tz - 2.65f),
                Vec3(sideX, stage1Top + 7.1f, if (isRightTower) tz + 2.65f else tz - 2.65f),
                Vec3(sideX, stage1Top + 7.1f, if (isRightTower) tz - 2.65f else tz + 2.65f),
                MatBasilicaWhite,
                signboard = towerRoseTex
            )
        )
        // Pointed Gothic pediment gable over Stage 2 cornice
        addGothicTriangularGable(out, tx, stage2Top - 0.2f, tz + hw - 0.12f, halfW = hw, height = 2.6f, depth = 0.55f)
        addBox(out, tx - hw - 0.35f, stage2Top - 0.25f, tz - hw - 0.35f, tx + hw + 0.35f, stage2Top + 0.28f, tz + hw + 0.35f, MatBasilicaWhite, MatWarmGoldUplight, MatBasilicaWhiteShade)

        // STAGE 3: Upper Octagonal Gothic Belfry Lantern (Warm Amber-Illuminated Louvered Lancets!)
        val belfryR = 3.15f
        addOctagonalPrismWithLancets(out, tx, tz, stage2Top, belfryTop, belfryR, belfryLancetTex)

        // Ring of 8 Slender Gothic Needle Pinnacles crowning the Belfry Lantern corners
        for (i in 0 until 8) {
            val a = (i * 45.0 + 22.5) * PI / 180.0
            val px = tx + (belfryR + 0.28f) * cos(a).toFloat()
            val pz = tz + (belfryR + 0.28f) * sin(a).toFloat()
            addBox(out, px - 0.24f, stage2Top + 0.4f, pz - 0.24f, px + 0.24f, belfryTop + 1.2f, pz + 0.24f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)
            addPyramidSpire(out, px, belfryTop + 1.2f, pz, 0.28f, 2.8f, MatBasilicaWhite, MatWarmGoldUplight)
        }

        // STAGE 4: Warm Gold-Lit Tapered Octagonal Gothic Spire + Spirelet Crown + Golden Finial & Cross!
        val midSpireY = belfryTop + 5.6f
        addOctagonalTaperedSpire(out, tx, tz, belfryTop, midSpireY, belfryR * 0.92f, belfryR * 0.46f, MatWarmGoldUplight, MatWarmAmberDeep)
        // Mid-spire crown ring of miniature golden-lit Gothic spirelets (clearly visible in photo!)
        for (i in 0 until 8) {
            val a = (i * 45.0) * PI / 180.0
            val sx = tx + (belfryR * 0.54f) * cos(a).toFloat()
            val sz = tz + (belfryR * 0.54f) * sin(a).toFloat()
            addPyramidSpire(out, sx, midSpireY - 0.3f, sz, 0.22f, 2.0f, MatWarmGoldUplight, MatWarmAmberDeep)
        }
        // Upper slender gilded Gothic spire tip to 44.36m apex
        addOctagonalTaperedSpire(out, tx, tz, midSpireY, spireTop - 2.6f, belfryR * 0.44f, 0.38f, MatWarmGoldUplight, MatWarmAmberDeep)
        // Golden Statue / Finial Pedestal & Illuminated Summit Cross
        addBox(out, tx - 0.42f, spireTop - 2.6f, tz - 0.42f, tx + 0.42f, spireTop - 1.2f, tz + 0.42f, MatGildedGold, MatWarmGoldUplight, MatGildedGold)
        addCross3D(out, tx, spireTop - 1.2f, tz, height = 2.6f, thick = 0.22f, col = MatGildedGold)
    }

    private fun buildRearBibleTower(
        out: MutableList<QuadFace3D>,
        cx: Float,
        tz: Float,
        towerRoseTex: SignboardQuadSpec,
        belfryLancetTex: SignboardQuadSpec
    ) {
        // Tier 1 Base Shaft (Y = 0..24m, 13.6m × 13.6m)
        val hw1 = 6.8f
        addBox(out, cx - hw1, 0.06f, tz - hw1, cx + hw1, 24.0f, tz + hw1, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
        // Corner stepped buttresses
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                val bx = cx + sx * (hw1 + 0.5f)
                val bz = tz + sz * (hw1 + 0.5f)
                addBox(out, bx - 0.85f, 0.06f, bz - 0.85f, bx + 0.85f, 24.6f, bz + 0.85f, MatBasilicaWhite, MatBasilicaWarmWash, MatBasilicaWhiteShade)
                addPyramidSpire(out, bx, 24.6f, bz, 0.85f, 3.8f, MatBasilicaWhite, MatBasilicaWhiteShade)
            }
        }

        // Tier 2 Gallery & Rose-Window Stage (Y = 24..42m, 10.6m × 10.6m)
        val hw2 = 5.3f
        addBox(out, cx - hw2, 24.0f, tz - hw2, cx + hw2, 42.0f, tz + hw2, MatBasilicaWhite, MatBasilicaWhite, MatBasilicaWhiteShade)
        // Rose windows facing North & South on the Bible Tower
        for (zDir in floatArrayOf(-1f, 1f)) {
            val fz = tz + zDir * (hw2 + 0.06f)
            out.add(
                QuadFace3D(
                    Vec3(cx + (if (zDir > 0f) -3.2f else 3.2f), 30.0f, fz),
                    Vec3(cx + (if (zDir > 0f) 3.2f else -3.2f), 30.0f, fz),
                    Vec3(cx + (if (zDir > 0f) 3.2f else -3.2f), 36.4f, fz),
                    Vec3(cx + (if (zDir > 0f) -3.2f else 3.2f), 36.4f, fz),
                    MatBasilicaWhite,
                    signboard = towerRoseTex
                )
            )
        }
        // Observation Deck Parapet Cornice at Y = 42m + 4 Corner Spirelets
        addBox(out, cx - hw2 - 0.65f, 41.6f, tz - hw2 - 0.65f, cx + hw2 + 0.65f, 42.8f, tz + hw2 + 0.65f, MatBasilicaWhite, MatWarmGoldUplight, MatBasilicaWhiteShade)
        for (sx in floatArrayOf(-1f, 1f)) {
            for (sz in floatArrayOf(-1f, 1f)) {
                addPyramidSpire(out, cx + sx * (hw2 + 0.2f), 42.8f, tz + sz * (hw2 + 0.2f), 0.72f, 4.6f, MatBasilicaWhite, MatWarmGoldUplight)
            }
        }

        // Tier 3 Soaring Octagonal Belfry & Observation Lantern (Y = 42..58m)
        addOctagonalPrismWithLancets(out, cx, tz, 42.0f, 58.0f, 4.45f, belfryLancetTex)

        // Tier 4 Monumental Ribbed Gothic Spire rising to 76.5m + Glowing 3.8m Cross (Total ~80m — Thrissur Skyline Icon!)
        addOctagonalTaperedSpire(out, cx, tz, 58.0f, 67.5f, 4.35f, 2.25f, MatBasilicaWarmWash, MatWarmGoldUplight)
        addOctagonalTaperedSpire(out, cx, tz, 67.5f, 75.8f, 2.20f, 0.45f, MatWarmGoldUplight, MatWarmAmberDeep)
        addCross3D(out, cx, 75.8f, tz, height = 4.2f, thick = 0.34f, col = MatGildedGold)
    }

    // =========================================================================
    // ARCHITECTURAL GEOMETRY PRIMITIVES (GOTHIC ARCHES, OCTAGONAL TOWERS, SPIRES)
    // =========================================================================

    private fun addPointedArchHeader(
        out: MutableList<QuadFace3D>,
        x0: Float,
        x1: Float,
        springY: Float,
        topY: Float,
        zFront: Float,
        depth: Float
    ) {
        val midX = (x0 + x1) * 0.5f
        val halfSpan = (x1 - x0) * 0.5f
        val apexY = topY - 0.75f
        val archH = apexY - springY
        val zBack = zFront - depth

        // Build clean crisp Gothic pointed-arch spandrels in 4 smooth stepped segments per side
        val segs = 4
        for (side in floatArrayOf(-1f, 1f)) {
            for (i in 0 until segs) {
                val t0 = i.toFloat() / segs
                val t1 = (i + 1).toFloat() / segs
                val ax0 = midX + side * halfSpan * (1f - t0)
                val ax1 = midX + side * halfSpan * (1f - t1)
                // Gothic pointed arch curve (steeper rise toward center peak)
                val ay0 = springY + archH * sin(t0 * PI.toFloat() * 0.5f)
                val ay1 = springY + archH * sin(t1 * PI.toFloat() * 0.5f)
                val lx = minOf(ax0, ax1)
                val rx = maxOf(ax0, ax1)
                val ly = if (ax0 < ax1) ay0 else ay1
                val ry = if (ax0 < ax1) ay1 else ay0

                // Front spandrel quad
                out.add(
                    QuadFace3D(
                        Vec3(lx, ly, zFront),
                        Vec3(rx, ry, zFront),
                        Vec3(rx, topY, zFront),
                        Vec3(lx, topY, zFront),
                        MatBasilicaWhite
                    )
                )
                // Warm golden-lit recessed intrados soffit underside of the pointed arch!
                out.add(
                    QuadFace3D(
                        Vec3(lx, ly, zBack),
                        Vec3(rx, ry, zBack),
                        Vec3(rx, ry, zFront),
                        Vec3(lx, ly, zFront),
                        MatPorticoInteriorGlow
                    )
                )
            }
        }
    }

    private fun addGothicTriangularGable(
        out: MutableList<QuadFace3D>,
        cx: Float,
        baseY: Float,
        zFront: Float,
        halfW: Float,
        height: Float,
        depth: Float
    ) {
        val apexY = baseY + height
        val zBack = zFront - depth
        // Front triangular gable (represented as degenerate quad with crisp apex)
        out.add(
            QuadFace3D(
                Vec3(cx - halfW, baseY, zFront),
                Vec3(cx + halfW, baseY, zFront),
                Vec3(cx, apexY, zFront),
                Vec3(cx, apexY, zFront),
                MatBasilicaWhite
            )
        )
        // Left & Right sloped stone coping edges of the Gothic gable
        out.add(
            QuadFace3D(
                Vec3(cx - halfW, baseY, zFront + 0.14f),
                Vec3(cx, apexY, zFront + 0.14f),
                Vec3(cx, apexY, zBack),
                Vec3(cx - halfW, baseY, zBack),
                MatBasilicaWarmWash
            )
        )
        out.add(
            QuadFace3D(
                Vec3(cx, apexY, zFront + 0.14f),
                Vec3(cx + halfW, baseY, zFront + 0.14f),
                Vec3(cx + halfW, baseY, zBack),
                Vec3(cx, apexY, zBack),
                MatBasilicaWhiteShade
            )
        )
    }

    private fun addOctagonalPrismWithLancets(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        y0: Float,
        y1: Float,
        radius: Float,
        lancetTex: SignboardQuadSpec
    ) {
        for (i in 0 until 8) {
            val a0 = (i * 45.0 - 22.5) * PI / 180.0
            val a1 = ((i + 1) * 45.0 - 22.5) * PI / 180.0
            val x0 = cx + radius * cos(a0).toFloat()
            val z0 = cz + radius * sin(a0).toFloat()
            val x1 = cx + radius * cos(a1).toFloat()
            val z1 = cz + radius * sin(a1).toFloat()
            out.add(
                QuadFace3D(
                    Vec3(x0, y0, z0),
                    Vec3(x1, y0, z1),
                    Vec3(x1, y1, z1),
                    Vec3(x0, y1, z0),
                    MatBasilicaWarmWash,
                    signboard = lancetTex
                )
            )
        }
    }

    private fun addOctagonalTaperedSpire(
        out: MutableList<QuadFace3D>,
        cx: Float,
        cz: Float,
        y0: Float,
        y1: Float,
        rBottom: Float,
        rTop: Float,
        colBright: Color,
        colShade: Color
    ) {
        for (i in 0 until 8) {
            val a0 = (i * 45.0 - 22.5) * PI / 180.0
            val a1 = ((i + 1) * 45.0 - 22.5) * PI / 180.0
            val bx0 = cx + rBottom * cos(a0).toFloat()
            val bz0 = cz + rBottom * sin(a0).toFloat()
            val bx1 = cx + rBottom * cos(a1).toFloat()
            val bz1 = cz + rBottom * sin(a1).toFloat()
            val tx0 = cx + rTop * cos(a0).toFloat()
            val tz0 = cz + rTop * sin(a0).toFloat()
            val tx1 = cx + rTop * cos(a1).toFloat()
            val tz1 = cz + rTop * sin(a1).toFloat()
            out.add(
                QuadFace3D(
                    Vec3(bx0, y0, bz0),
                    Vec3(bx1, y0, bz1),
                    Vec3(tx1, y1, tz1),
                    Vec3(tx0, y1, tz0),
                    if (i % 2 == 0) colBright else colShade
                )
            )
        }
    }

    private fun addPyramidSpire(
        out: MutableList<QuadFace3D>,
        cx: Float,
        y0: Float,
        cz: Float,
        halfW: Float,
        height: Float,
        colBright: Color,
        colShade: Color
    ) {
        val apex = Vec3(cx, y0 + height, cz)
        val p0 = Vec3(cx - halfW, y0, cz + halfW)
        val p1 = Vec3(cx + halfW, y0, cz + halfW)
        val p2 = Vec3(cx + halfW, y0, cz - halfW)
        val p3 = Vec3(cx - halfW, y0, cz - halfW)
        out.add(QuadFace3D(p0, p1, apex, apex, colBright))
        out.add(QuadFace3D(p1, p2, apex, apex, colShade))
        out.add(QuadFace3D(p2, p3, apex, apex, colShade))
        out.add(QuadFace3D(p3, p0, apex, apex, colBright))
    }

    private fun addCross3D(
        out: MutableList<QuadFace3D>,
        cx: Float,
        baseY: Float,
        cz: Float,
        height: Float,
        thick: Float,
        col: Color
    ) {
        val ht = thick * 0.5f
        val armY = baseY + height * 0.66f
        val armHalf = height * 0.34f
        // Vertical bar
        addBox(out, cx - ht, baseY, cz - ht, cx + ht, baseY + height, cz + ht, col, col, col)
        // Horizontal crossbar
        addBox(out, cx - armHalf, armY - ht, cz - ht, cx + armHalf, armY + ht, cz + ht, col, col, col)
    }

    private fun addLongitudinalPitchedRoof(
        out: MutableList<QuadFace3D>,
        x0: Float,
        x1: Float,
        z0: Float,
        z1: Float,
        eaveY: Float,
        ridgeY: Float,
        colWest: Color,
        colEast: Color
    ) {
        val midX = (x0 + x1) * 0.5f
        out.add(QuadFace3D(Vec3(x0, eaveY, z0), Vec3(x0, eaveY, z1), Vec3(midX, ridgeY, z1), Vec3(midX, ridgeY, z0), colWest))
        out.add(QuadFace3D(Vec3(midX, ridgeY, z0), Vec3(midX, ridgeY, z1), Vec3(x1, eaveY, z1), Vec3(x1, eaveY, z0), colEast))
    }

    private fun addBox(
        out: MutableList<QuadFace3D>,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        topCol: Color,
        frontCol: Color,
        sideCol: Color
    ) {
        // Top
        out.add(QuadFace3D(Vec3(x0, y1, z1), Vec3(x1, y1, z1), Vec3(x1, y1, z0), Vec3(x0, y1, z0), topCol))
        // Front (+Z)
        out.add(QuadFace3D(Vec3(x0, y0, z1), Vec3(x1, y0, z1), Vec3(x1, y1, z1), Vec3(x0, y1, z1), frontCol))
        // Back (-Z)
        out.add(QuadFace3D(Vec3(x1, y0, z0), Vec3(x0, y0, z0), Vec3(x0, y1, z0), Vec3(x1, y1, z0), sideCol))
        // Left (-X)
        out.add(QuadFace3D(Vec3(x0, y0, z0), Vec3(x0, y0, z1), Vec3(x0, y1, z1), Vec3(x0, y1, z0), sideCol))
        // Right (+X)
        out.add(QuadFace3D(Vec3(x1, y0, z1), Vec3(x1, y0, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), sideCol))
    }
}
