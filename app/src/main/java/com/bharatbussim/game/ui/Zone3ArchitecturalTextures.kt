package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * High-resolution procedural architectural textures for Zone 3 (NH 544 South) landmarks,
 * modeled after real-world Kerala highway engineering & photo references:
 *  - Paliyekkara Toll Plaza (NH 544 Km 278.000, GIPL / NHAI): Royal-blue standing-seam
 *    corrugated steel roof, royal-blue/white/yellow-orange GIPL fascia with NHAI & FASTag
 *    emblems, panoramic glass-and-aluminum toll booth facades, and 2-storey GIPL Control Building.
 *  - Ollur ROB (25kV Electrified Southern Railway): Alpha-cutout diamond galvanized wire-mesh
 *    anti-contact safety screen + 25,000V warning plaque.
 *  - Chalakudy & Kurumali River Bridges: Alpha-cutout open slotted Kerala PWD concrete
 *    balustrades with royal-blue tubular pedestrian handrails.
 *  - Chalakudy South Bus Bay: Authentic Mangalore terracotta clay-tile pitched roof sheets.
 */
internal object Zone3ArchitecturalTextures {

    /**
     * Authentic Paliyekkara Toll Plaza warm Terracotta-Orange corrugated/tiled Kerala roof sheet
     * (matches real-world bright terracotta red-orange ribbed roofs of the main wing, central
     * two-tier pagoda pavilion, and flanking left/right gable pavilions).
     */
    fun createPaliyekkaraTerracottaRoofSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_PALIYEKKARA_TERRACOTTA_ROOF_V3",
        bmpWidth = 512,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val w = 512
        val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Bright sun-baked Kerala terracotta-orange gradient (ridge -> eave)
        p.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFFE67047.toInt(), 0xFFD95B34.toInt(), 0xFFC24622.toInt()),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // Crisp vertical corrugated/standing-seam Mangalore tile ribs
        val ribStep = 14f
        var x = 0f
        while (x < w) {
            // Sunlit terracotta crest highlight
            p.color = 0x55FDBA74
            c.drawRect(x, 0f, x + 3f, h.toFloat(), p)
            // Deep rib trough shadow
            p.color = 0x666C1D0B
            c.drawRect(x + 8f, 0f, x + 11.5f, h.toFloat(), p)
            x += ribStep
        }

        // Subtle horizontal tile overlap course lines
        p.color = 0x3A581608
        p.strokeWidth = 2f
        var y = 32f
        while (y < h) {
            c.drawLine(0f, y, w.toFloat(), y, p)
            y += 32f
        }

        // Dark charcoal structural eave trim along bottom edge
        p.color = 0xFF262D36.toInt()
        c.drawRect(0f, h - 10f, w.toFloat(), h.toFloat(), p)
        bmp
    }

    /**
     * Authentic dark charcoal-navy triangular Kerala gable (Mukhappu) with crisp white
     * ornamental filigree lattice along the lower corners (matches the left & right tall
     * flanking pavilions in the real Paliyekkara Toll Plaza photo).
     */
    fun createPaliyekkaraGableMukhappuSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_PALIYEKKARA_GABLE_MUKHAPPU_V3",
        bmpWidth = 512,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val w = 512f
        val h = 256f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AndroidColor.TRANSPARENT)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Outer dark roof bargeboard triangle
        val outerTri = AndroidPath().apply {
            moveTo(w * 0.5f, 4f)
            lineTo(w - 8f, h - 4f)
            lineTo(8f, h - 4f)
            close()
        }
        p.color = 0xFF1A222D.toInt()
        c.drawPath(outerTri, p)

        // Inner dark louvered timber/metal gable tympanum
        val innerTri = AndroidPath().apply {
            moveTo(w * 0.5f, 24f)
            lineTo(w - 30f, h - 16f)
            lineTo(30f, h - 16f)
            close()
        }
        p.color = 0xFF222B38.toInt()
        c.drawPath(innerTri, p)

        // Crisp Kerala ornamental white lattice / sun-ray louver accents at lower right & base
        c.save()
        c.clipPath(innerTri)
        p.color = 0x33FFFFFF
        p.strokeWidth = 3f
        var ly = 44f
        while (ly < h - 20f) {
            c.drawLine(30f, ly, w - 30f, ly, p)
            ly += 18f
        }
        // Iconic white curved Kerala decorative wave / lattice pattern in lower-right gable field
        p.color = 0xFFE2E8F0.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        val wave = AndroidPath().apply {
            moveTo(w * 0.50f, h - 18f)
            quadTo(w * 0.66f, h - 98f, w * 0.86f, h - 22f)
        }
        c.drawPath(wave, p)
        // Diagonal crisscross lattice inside lower ornament
        p.strokeWidth = 3.5f
        for (i in 0..7) {
            val lx = w * 0.52f + i * 24f
            c.drawLine(lx, h - 16f, lx + 26f, h - 64f, p)
            c.drawLine(lx + 26f, h - 16f, lx, h - 64f, p)
        }
        c.restore()

        // Crisp dark-terracotta outer gable outline
        p.style = Paint.Style.STROKE
        p.strokeWidth = 10f
        p.color = 0xFF111827.toInt()
        c.drawPath(outerTri, p)
        bmp
    }

    /**
     * Continuous panoramic white-mullioned clerestory glass ribbon band sitting between the
     * lower skirt roof and upper pitched roof of Paliyekkara's central two-tier Kerala pavilion.
     */
    fun createPaliyekkaraClerestorySpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_PALIYEKKARA_CLERESTORY_V3",
        bmpWidth = 512,
        bmpHeight = 96,
        isDoubleSided = true
    ) {
        val w = 512f
        val h = 96f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Bright white concrete/aluminum window surround
        p.color = 0xFFF1F5F9.toInt()
        c.drawRect(0f, 0f, w, h, p)

        // Dark charcoal upper shadow eaves & lower sill bar
        p.color = 0xFF1F2937.toInt()
        c.drawRect(0f, 0f, w, 14f, p)
        c.drawRect(0f, h - 12f, w, h, p)

        // Row of 14 crisp reflective clerestory window panes separated by bright white vertical mullions
        val panes = 14
        val padX = 12f
        val panePitch = (w - padX * 2f) / panes
        for (i in 0 until panes) {
            val x0 = padX + i * panePitch + 3f
            val x1 = padX + (i + 1) * panePitch - 3f
            p.shader = LinearGradient(
                x0, 18f, x0, h - 16f,
                intArrayOf(0xFF1E3A5F.toInt(), 0xFF475569.toInt(), 0xFFCBD5E1.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(x0, 18f, x1, h - 16f, p)
            p.shader = null
        }
        bmp
    }

    /**
     * Tall vertical NHAI Paliyekkara overhead lane signboards hanging across the terracotta
     * canopy fascia — Teal-Green (#007C7E) for standard/FASTag vehicle lanes and vibrant
     * Saffron-Orange (#EA681E) for dedicated FASTag highlight lanes (exact match to photo!).
     */
    fun createPaliyekkaraVerticalLaneSignSpec(
        cacheKey: String,
        isOrangeHighlight: Boolean,
        laneLabel: String,
        line1: String,
        line2: String
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 192,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val w = 192f
        val h = 256f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val bgTop = if (isOrangeHighlight) 0xFFF97316.toInt() else 0xFF008587.toInt()
        val bgBot = if (isOrangeHighlight) 0xFFDC580E.toInt() else 0xFF006B6E.toInt()
        p.shader = LinearGradient(0f, 0f, 0f, h, bgTop, bgBot, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, w, h), 10f, 10f, p)
        p.shader = null

        // Crisp white NHAI highway signboard inner border
        p.style = Paint.Style.STROKE
        p.color = 0xFFFFFFFF.toInt()
        p.strokeWidth = 6f
        c.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 6f, 6f, p)
        p.style = Paint.Style.FILL

        // Top lane number badge bar
        p.color = 0x33000000
        c.drawRect(14f, 16f, w - 14f, 54f, p)

        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 24f
        c.drawText(laneLabel, w * 0.5f, 44f, p)

        // Primary lane text (FASTag / CAR / BUS / FREE LANE)
        p.color = 0xFFFEF08A.toInt()
        p.textSize = 27f
        c.drawText(line1, w * 0.5f, 108f, p)

        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 20f
        c.drawText(line2, w * 0.5f, 144f, p)

        // Bold white downward lane arrow
        val arrow = AndroidPath().apply {
            moveTo(w * 0.5f - 10f, 168f)
            lineTo(w * 0.5f + 10f, 168f)
            lineTo(w * 0.5f + 10f, 204f)
            lineTo(w * 0.5f + 26f, 204f)
            lineTo(w * 0.5f, 236f)
            lineTo(w * 0.5f - 26f, 204f)
            lineTo(w * 0.5f - 10f, 204f)
            close()
        }
        p.color = 0xFFFFFFFF.toInt()
        c.drawPath(arrow, p)
        bmp
    }

    /**
     * Clean bilingual Paliyekkara Toll Plaza title plaque mounted on the dark fascia under
     * the central two-tier Kerala terracotta pagoda pavilion.
     */
    fun createPaliyekkaraCenterPlaqueSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_PALIYEKKARA_CENTER_PLAQUE_V3",
        bmpWidth = 768,
        bmpHeight = 112,
        isDoubleSided = true
    ) {
        val w = 768f
        val h = 112f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = 0xFF18222F.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.color = 0xFFD95B34.toInt()
        c.drawRect(0f, 0f, w, 8f, p)
        c.drawRect(0f, h - 8f, w, h, p)

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 36f
        c.drawText("PALIYEKKARA TOLL PLAZA  •  പാലിയേക്കര", w * 0.5f, 54f, p)
        p.color = 0xFFFEF08A.toInt()
        p.textSize = 22f
        c.drawText("NH 544 (KM 278.000) • GIPL • 100% NETC FASTag", w * 0.5f, 88f, p)
        bmp
    }

    /**
     * Realistic modular Paliyekkara toll operator booth facade:
     * Bright white-silver composite lower panel with teal/orange FASTag strip, panoramic tinted
     * security glass with white aluminum mullions, sliding service window & warm monitor glow.
     */
    fun createTollBoothFacadeSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_PALIYEKKARA_BOOTH_FACADE_V3",
        bmpWidth = 512,
        bmpHeight = 320,
        isDoubleSided = true
    ) {
        val w = 512f
        val h = 320f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Bright off-white aluminum booth shell matching the photo
        p.color = 0xFFF1F5F9.toInt()
        c.drawRect(0f, 0f, w, h, p)

        // Upper terracotta-red roof valance strip on booth
        p.color = 0xFFC24622.toInt()
        c.drawRect(0f, 0f, w, 26f, p)
        p.color = 0xFF1F2937.toInt()
        c.drawRect(0f, 26f, w, 32f, p)

        // Lower body composite panel with teal-green & orange FASTag stripe
        p.color = 0xFFE2E8F0.toInt()
        c.drawRect(0f, 186f, w, h, p)
        p.color = 0xFF007C7E.toInt()
        c.drawRect(0f, 212f, w, 246f, p)
        p.color = 0xFFEA580C.toInt()
        c.drawRect(0f, 246f, w, 256f, p)
        p.color = 0xFF334155.toInt()
        c.drawRect(0f, h - 16f, w, h, p)

        // Panoramic dark-tinted security glass band
        p.shader = LinearGradient(
            0f, 42f, w, 182f,
            intArrayOf(0xFF0F2233.toInt(), 0xFF1E3B56.toInt(), 0xFF0D1D2D.toInt()),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(14f, 42f, w - 14f, 182f, p)
        p.shader = null

        // Subtle operator monitor glow inside
        p.color = 0x5538BDF8
        c.drawRoundRect(RectF(196f, 108f, 276f, 166f), 6f, 6f, p)

        // Crisp sky reflection streaks
        p.color = 0x2AFFFFFF
        val glare = AndroidPath().apply {
            moveTo(64f, 42f); lineTo(128f, 42f); lineTo(76f, 182f); lineTo(24f, 182f); close()
        }
        c.drawPath(glare, p)

        // White-silver window mullions & sliding counter shutter
        p.color = 0xFFCBD5E1.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        c.drawRect(14f, 42f, w - 14f, 182f, p)
        c.drawLine(158f, 42f, 158f, 182f, p)
        c.drawLine(354f, 42f, 354f, 182f, p)
        p.strokeWidth = 4f
        c.drawRect(172f, 96f, 340f, 178f, p)
        p.style = Paint.Style.FILL

        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 21f
        c.drawText("PALIYEKKARA • FASTag BOOTH", w * 0.5f, 236f, p)
        bmp
    }

    /**
     * 2-Storey GIPL Toll Control & Admin Building modern Kerala curtain-wall glass & concrete facade.
     */
    fun createGiplAdminFacadeSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_GIPL_ADMIN_FACADE_V2",
        bmpWidth = 512,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val w = 512f
        val h = 256f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Warm off-white exterior Kerala plaster/ACP cladding
        p.color = 0xFFF1F5F9.toInt()
        c.drawRect(0f, 0f, w, h, p)

        // Royal blue roofline parapet fascia & middle floor spandrel band
        p.color = 0xFF0052A5.toInt()
        c.drawRect(0f, 0f, w, 34f, p)
        p.color = 0xFFFACC15.toInt()
        c.drawRect(0f, 34f, w, 40f, p)
        p.color = 0xFFCBD5E1.toInt()
        c.drawRect(0f, 122f, w, 144f, p)
        p.color = 0xFF475569.toInt()
        c.drawRect(0f, h - 14f, w, h, p)

        // 2 storeys of recessed reflective blue-tinted aluminum curtain-wall windows
        val floorBands = arrayOf(Pair(50f, 114f), Pair(154f, 224f))
        for ((y0, y1) in floorBands) {
            p.shader = LinearGradient(
                0f, y0, 0f, y1,
                intArrayOf(0xFF0E3A66.toInt(), 0xFF1D4ED8.toInt(), 0xFF0F2942.toInt()),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(18f, y0, w - 18f, y1, p)
            p.shader = null

            // Vertical aluminum mullions & horizontal transom
            p.color = 0xFFCBD5E1.toInt()
            p.strokeWidth = 4f
            c.drawLine(18f, (y0 + y1) * 0.5f, w - 18f, (y0 + y1) * 0.5f, p)
            var mx = 18f
            while (mx <= w - 16f) {
                c.drawLine(mx, y0, mx, y1, p)
                mx += 59.5f
            }
        }

        // Building title on parapet
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 20f
        c.drawText("GIPL TOLL CONTROL CENTRE • PALIYEKKARA", w * 0.5f, 24f, p)

        bmp
    }

    /**
     * ALPHA-CUTOUT Galvanized Diamond Chain-Link 25kV Anti-Contact Safety Mesh for Ollur ROB!
     * Transparent diamond openings allow the player to clearly watch real 3D Indian Railways
     * WAG-9 locomotives & passenger coaches gliding directly underneath the bridge.
     */
    fun createRobSafetyMeshSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_OLLUR_ROB_SAFETY_MESH_ALPHA_V2",
        bmpWidth = 256,
        bmpHeight = 128,
        isDoubleSided = true
    ) {
        val w = 256
        val h = 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AndroidColor.TRANSPARENT)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Outer galvanized tubular frame rails (top, mid, bottom)
        p.color = 0xFFE2E8F0.toInt()
        c.drawRect(0f, 0f, w.toFloat(), 6f, p)
        c.drawRect(0f, h * 0.5f - 2f, w.toFloat(), h * 0.5f + 2f, p)
        c.drawRect(0f, h - 6f, w.toFloat(), h.toFloat(), p)

        // Vertical steel frame posts at left, center & right
        p.color = 0xFF94A3B8.toInt()
        c.drawRect(0f, 0f, 6f, h.toFloat(), p)
        c.drawRect(w * 0.5f - 3f, 0f, w * 0.5f + 3f, h.toFloat(), p)
        c.drawRect(w - 6f, 0f, w.toFloat(), h.toFloat(), p)

        // Crisp diagonal diamond galvanized wire mesh (leaving clear transparent diamonds!)
        p.color = 0xFFD0D8E2.toInt()
        p.strokeWidth = 2.4f
        val step = 16f
        var x = -h.toFloat()
        while (x < w + h) {
            c.drawLine(x, 6f, x + h, h - 6f, p)
            c.drawLine(x, h - 6f, x + h, 6f, p)
            x += step
        }
        bmp
    }

    /**
     * ALPHA-CUTOUT Open Slotted Kerala PWD / NHAI Bridge Balustrade & Royal-Blue Steel Handrail!
     * Transparent open bays let the animated Chalakudy & Kurumali rivers, golden sandbars,
     * and moored canoes shine cleanly through the bridge railing as you drive across.
     */
    fun createBridgeSlottedRailingSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_KERALA_BRIDGE_RAILING_ALPHA_V2",
        bmpWidth = 512,
        bmpHeight = 160,
        isDoubleSided = true
    ) {
        val w = 512
        val h = 160
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AndroidColor.TRANSPARENT)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top royal-blue painted tubular steel handrail pipe (Kerala PWD signature bridge rail)
        p.shader = LinearGradient(
            0f, 4f, 0f, 20f,
            intArrayOf(0xFF60A5FA.toInt(), 0xFF1D4ED8.toInt(), 0xFF1E3A8A.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 6f, w.toFloat(), 20f, p)
        p.shader = null

        // Mid galvanized steel rail bar
        p.color = 0xFFCBD5E1.toInt()
        c.drawRect(0f, 48f, w.toFloat(), 58f, p)

        // Upper concrete balustrade coping rail (Y = 82..98)
        p.color = 0xFFF1F5F9.toInt()
        c.drawRect(0f, 82f, w.toFloat(), 98f, p)
        p.color = 0xFF94A3B8.toInt()
        c.drawRect(0f, 96f, w.toFloat(), 100f, p)

        // Solid concrete lower kerb wall (Y = 132..160) with subtle Kerala monsoon weathering
        p.shader = LinearGradient(
            0f, 132f, 0f, 160f,
            intArrayOf(0xFFE2E8F0.toInt(), 0xFFB8C0CC.toInt(), 0xFF8A94A3.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 132f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // Heavy concrete balustrade end & mid posts + vertical steel stanchions
        for (px in floatArrayOf(0f, 124f, 252f, 380f, 498f)) {
            // Vertical blue steel stanchion pipe (top to coping)
            p.color = 0xFF1E40AF.toInt()
            c.drawRect(px + 2f, 6f, px + 12f, 84f, p)
            // Concrete balustrade pilaster column (coping to kerb)
            p.color = 0xFFE2E8F0.toInt()
            c.drawRect(px, 82f, px + 16f, 160f, p)
            p.color = 0xFF94A3B8.toInt()
            c.drawRect(px + 13f, 82f, px + 16f, 160f, p)
        }

        // Intermediate concrete balusters creating crisp open rectangular viewing slots (Y = 98..132)
        var bx = 38f
        while (bx < w - 20f) {
            if ((bx.toInt() % 128) !in 0..22) {
                p.color = 0xFFCBD5E1.toInt()
                c.drawRect(bx, 98f, bx + 10f, 134f, p)
            }
            bx += 32f
        }

        bmp
    }

    /**
     * Authentic Kerala Mangalore clay terracotta tiled pitched roof for Chalakudy South Bus Bay.
     */
    fun createTerracottaTileRoofSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE3_MANGALORE_TERRACOTTA_ROOF_V2",
        bmpWidth = 256,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val w = 256
        val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFFC2410C.toInt(), 0xFF9A3412.toInt(), 0xFF7C2D12.toInt()),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // Interlocking corrugated Mangalore tile rows
        val tileW = 24f
        val tileH = 28f
        var row = 0
        var y = 0f
        while (y < h) {
            // Horizontal lap shadow
            p.color = 0x663B0F05
            c.drawRect(0f, y + tileH - 4f, w.toFloat(), y + tileH, p)
            var x = if (row % 2 == 0) 0f else -tileW * 0.5f
            while (x < w) {
                p.color = 0x33FDBA74
                c.drawRect(x + 3f, y + 2f, x + 9f, y + tileH - 4f, p)
                p.color = 0x55431407
                c.drawRect(x + tileW - 4f, y, x + tileW, y + tileH, p)
                x += tileW
            }
            y += tileH
            row++
        }
        bmp
    }
}
