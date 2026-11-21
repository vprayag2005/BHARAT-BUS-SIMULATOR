package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * High-resolution procedural architectural textures for Zone 5 (NH 66 Coastal Corridor):
 *  - Nattika Toll Plaza (NH 66, Thrissur): Authentic dark-green NHAI trilingual horizontal
 *    canopy fascia ("നാഷണൽ ഹൈവേ അതോറിറ്റി ഓഫ് ഇന്ത്യ | भारतीय राष्ट्रीय राजमार्ग प्राधिकरण |
 *    National Highways Authority of India" + yellow NH 66 shield + FASTag badge), crisp white
 *    two-story overhead concrete control building facade with dark barred windows, and overhead
 *    FASTag electronic lane status & fare matrix boards.
 *  - Chettuva Backwaters Bridge: Alpha-cutout Chinese fishing net (Cheena Vala) nylon diamond
 *    mesh, golden woven bamboo Kettuvallam houseboat roof, and Kerala PWD bridge signboard.
 *  - Triprayar Canoli Canal Bridge & Sree Rama Temple Water Ghat (Meenootu) plaque.
 *  - Kottapuram / Kodungallur NW-3 Bridge & Moothakunnam Ernakulam Border directional gantry.
 */
internal object Zone5ArchitecturalTextures {

    /**
     * Exact recreation of the Nattika Toll Plaza full-width dark-green NHAI fascia signboard
     * seen in the real-world reference photo:
     *  - Deep NHAI highway green (#006B4F) with crisp white top & bottom horizontal accent borders
     *  - Left badges: FASTag emblem + yellow NH 66 highway shield
     *  - Trilingual crisp white typography:
     *    Malayalam | Hindi | English ("National Highways Authority of India")
     */
    fun createNattikaNhaiFasciaSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE5_NATTIKA_NHAI_FASCIA_V1",
        bmpWidth = 1024,
        bmpHeight = 128,
        isDoubleSided = false
    ) {
        val w = 1024f
        val h = 128f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Subtle vertical gradient on deep NHAI green fascia
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF007655.toInt(), 0xFF006447.toInt(), 0xFF00523A.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Crisp white top and bottom structural trim strips (matching photo's framed green band)
        p.color = 0xFFF1F5F9.toInt()
        c.drawRect(0f, 0f, w, 7f, p)
        c.drawRect(0f, h - 7f, w, h, p)

        // Subtle panel seam dividers every ~128px like real aluminum composite fascia sheets
        p.color = 0x26000000
        p.strokeWidth = 2f
        var sx = 128f
        while (sx < w) {
            c.drawLine(sx, 7f, sx, h - 7f, p)
            sx += 128f
        }

        // Far-left FASTag / Toll Lane emblem box (matches photo's left square FASTag sign)
        p.color = 0xFFF8FAFC.toInt()
        c.drawRoundRect(RectF(14f, 18f, 82f, 110f), 6f, 6f, p)
        p.color = 0xFF0F4C81.toInt()
        c.drawRect(18f, 22f, 78f, 52f, p)
        p.color = 0xFFEA580C.toInt()
        c.drawRect(18f, 52f, 78f, 74f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 15f
        c.drawText("FASTag", 48f, 43f, p)
        p.color = 0xFF0F172A.toInt()
        p.textSize = 13f
        c.drawText("NATTIKA", 48f, 98f, p)

        // Yellow NH-66 Indian National Highway Shield (right next to FASTag box, just like photo!)
        p.color = 0xFFFACC15.toInt()
        c.drawRoundRect(RectF(94f, 20f, 138f, 108f), 6f, 6f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = 0xFF111827.toInt()
        c.drawRoundRect(RectF(96f, 22f, 136f, 106f), 5f, 5f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF111827.toInt()
        p.textSize = 16f
        c.drawText("NH", 116f, 54f, p)
        p.textSize = 24f
        c.drawText("66", 116f, 92f, p)

        // 1. Malayalam NHAI text (left-center)
        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 24f
        c.drawText("നാഷണൽ ഹൈവേ അതോറിറ്റി ഓഫ് ഇന്ത്യ", 154f, 75f, p)

        // 2. Hindi NHAI text (center)
        p.textSize = 25f
        c.drawText("भारतीय राष्ट्रीय राजमार्ग प्राधिकरण", 440f, 76f, p)

        // 3. Bold English "National Highways Authority of India" (right section — exact photo match!)
        p.textSize = 28f
        c.drawText("National Highways Authority of India", 674f, 77f, p)

        bmp
    }

    /**
     * Bright white plastered concrete upper administrative control building facade for Nattika
     * Toll Plaza with subtle vertical structural pilaster ribs, dark recessed aluminium window
     * frames with vertical security grille bars, and upper parapet band (matching the photo!).
     */
    fun createNattikaUpperFacadeSpec(
        cacheKey: String,
        windowColumns: Int,
        twoStorey: Boolean
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 512,
        bmpHeight = 256,
        isDoubleSided = false
    ) {
        val w = 512f
        val h = 256f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sunlit bright white Kerala exterior concrete wall with subtle ambient shading near base
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFFF6F8FA.toInt(), 0xFFECEFF3.toInt(), 0xFFDCE2E9.toInt()),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Subtle roof parapet cap shadow band along top edge
        p.color = 0xFFCBD5E1.toInt()
        c.drawRect(0f, 0f, w, 10f, p)
        p.color = 0x3364748B
        c.drawRect(0f, 10f, w, 16f, p)

        // Vertical structural pilaster joint lines (exact match to photo's ribbed white wall)
        val colW = w / windowColumns.coerceAtLeast(1)
        for (i in 0..windowColumns) {
            val px = i * colW
            p.color = 0xFFFFFFFF.toInt()
            c.drawRect(px - 3f, 10f, px + 1f, h, p)
            p.color = 0x2E475569
            c.drawRect(px + 1f, 10f, px + 4f, h, p)
        }

        // Helper to draw authentic dark aluminium window with vertical steel security grille bars
        fun drawBarredWindow(cx: Float, cy: Float, ww: Float, wh: Float) {
            val l = cx - ww * 0.5f
            val t = cy - wh * 0.5f
            val r = cx + ww * 0.5f
            val b = cy + wh * 0.5f
            // Shadow cast by blue sunshade awning right above the window
            p.color = 0x4A0F172A
            c.drawRect(l - 6f, t - 4f, r + 6f, t + 14f, p)
            // Outer white/grey sill and dark aluminium frame
            p.color = 0xFF334155.toInt()
            c.drawRect(l - 3f, t - 3f, r + 3f, b + 3f, p)
            // Dark reflective window glass interior
            p.shader = LinearGradient(
                l, t, r, b,
                intArrayOf(0xFF0F1F33.toInt(), 0xFF1E3A5F.toInt(), 0xFF0B1522.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(l, t, r, b, p)
            p.shader = null
            // Crisp vertical silver-grey security bars & horizontal mid mullion
            p.color = 0xFFCBD5E1.toInt()
            p.strokeWidth = 2.2f
            val bars = 4
            for (k in 1 until bars) {
                val bx = l + ww * (k.toFloat() / bars)
                c.drawLine(bx, t, bx, b, p)
            }
            c.drawLine(l, cy, r, cy, p)
            // Subtle white window sill ledge
            p.color = 0xFFE2E8F0.toInt()
            c.drawRect(l - 5f, b + 2f, r + 5f, b + 8f, p)
        }

        for (i in 0 until windowColumns) {
            val cx = (i + 0.5f) * colW
            if (twoStorey) {
                drawBarredWindow(cx, h * 0.30f, colW * 0.42f, h * 0.28f)
                drawBarredWindow(cx, h * 0.74f, colW * 0.42f, h * 0.28f)
            } else {
                // Rhythmic staggered upper/lower window layout matching the left wing of the photo
                val cy = if (i % 2 == 0) h * 0.66f else h * 0.36f
                drawBarredWindow(cx, cy, colW * 0.38f, h * 0.30f)
            }
        }
        bmp
    }

    /**
     * Dynamic-look FASTag electronic fare & lane status board for Nattika Toll Plaza lanes.
     */
    fun createNattikaLaneMatrixSpec(
        cacheKey: String,
        laneNumber: Int,
        activeFastag: Boolean
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 256,
        bmpHeight = 128,
        isDoubleSided = true
    ) {
        val w = 256f
        val h = 128f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = 0xFF0A0F18.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = if (activeFastag) 0xFF22C55E.toInt() else 0xFFEF4444.toInt()
        c.drawRect(3f, 3f, w - 3f, h - 3f, p)
        p.style = Paint.Style.FILL

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        if (activeFastag) {
            p.color = 0xFF22C55E.toInt()
            p.textSize = 44f
            c.drawText("↓  L$laneNumber  ↓", w * 0.5f, 56f, p)
            p.color = 0xFFFACC15.toInt()
            p.textSize = 22f
            c.drawText("FASTag OK • ₹50", w * 0.5f, 98f, p)
        } else {
            p.color = 0xFFEF4444.toInt()
            p.textSize = 44f
            c.drawText("×  L$laneNumber  ×", w * 0.5f, 56f, p)
            p.color = 0xFFFCA5A5.toInt()
            p.textSize = 21f
            c.drawText("CLOSED • USE GREEN", w * 0.5f, 98f, p)
        }
        bmp
    }

    /**
     * Authentic Kerala PWD / NHAI Blue River & Coastal Bridge Signboard.
     */
    fun createZone5PwdBridgeSignSpec(
        cacheKey: String,
        malayalamTitle: String,
        englishTitle: String,
        subtitle: String
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 512,
        bmpHeight = 160,
        isDoubleSided = true
    ) {
        val w = 512f
        val h = 160f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = 0xFF0A4E94.toInt()
        c.drawRoundRect(RectF(0f, 0f, w, h), 14f, 14f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 10f, 10f, p)
        p.style = Paint.Style.FILL

        p.color = 0xFFFFFFFF.toInt()
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.textSize = 34f
        c.drawText(malayalamTitle, w * 0.5f, 58f, p)
        p.textSize = 30f
        c.drawText(englishTitle, w * 0.5f, 104f, p)
        p.color = 0xFFFDE047.toInt()
        p.textSize = 20f
        c.drawText(subtitle, w * 0.5f, 138f, p)
        bmp
    }

    /**
     * Alpha-cutout dark nylon fishing net diamond mesh for Chettuva Backwaters Chinese Fishing Nets (Cheena Vala).
     */
    fun createChineseNetMeshSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE5_CHEENA_VALA_MESH_V1",
        bmpWidth = 256,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val w = 256
        val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AndroidColor.TRANSPARENT)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Subtle dark translucent nylon web tint
        p.color = 0x45121820
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // Crisp diagonal diamond nylon mesh cords
        p.color = 0xDC1C252E.toInt()
        p.strokeWidth = 3.2f
        val step = 18
        var d = -h
        while (d < w + h) {
            c.drawLine(d.toFloat(), 0f, (d + h).toFloat(), h.toFloat(), p)
            c.drawLine(d.toFloat(), h.toFloat(), (d + h).toFloat(), 0f, p)
            d += step
        }
        // Thick hemmed rope border around the square fishing net
        p.style = Paint.Style.STROKE
        p.strokeWidth = 10f
        p.color = 0xFF3E2723.toInt()
        c.drawRect(5f, 5f, w - 5f, h - 5f, p)
        bmp
    }

    /**
     * Warm golden-tan woven bamboo & coconut-coir arched roof texture for Chettuva luxury Kettuvallam houseboats.
     */
    fun createKettuvallamRoofSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE5_KETTUVALLAM_ROOF_V1",
        bmpWidth = 256,
        bmpHeight = 256,
        isDoubleSided = true
    ) {
        val w = 256f
        val h = 256f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFFD6A85A.toInt(), 0xFFB8843B.toInt(), 0xFF8C5F24.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Woven bamboo strip ribs & dark coir lashings
        p.strokeWidth = 3f
        var x = 0f
        while (x < w) {
            p.color = 0x554A2C10
            c.drawLine(x, 0f, x, h, p)
            p.color = 0x40FDE68A
            c.drawLine(x + 4f, 0f, x + 4f, h, p)
            x += 16f
        }
        var y = 18f
        while (y < h) {
            p.color = 0x663B200B
            p.strokeWidth = 4f
            c.drawLine(0f, y, w, y, p)
            y += 32f
        }
        bmp
    }

    /**
     * Sacred maroon-and-gold signboard for Triprayar Sree Rama Temple Water Ghat (Meenootu).
     */
    fun createTriprayarTemplePlaqueSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE5_TRIPRAYAR_TEMPLE_PLAQUE_V1",
        bmpWidth = 512,
        bmpHeight = 144,
        isDoubleSided = true
    ) {
        val w = 512f
        val h = 144f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = 0xFF7F1D1D.toInt()
        c.drawRoundRect(RectF(0f, 0f, w, h), 12f, 12f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = 0xFFFACC15.toInt()
        c.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 8f, 8f, p)
        p.style = Paint.Style.FILL

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.color = 0xFFFEF08A.toInt()
        p.textSize = 30f
        c.drawText("തൃപ്രയാർ ശ്രീരാമ ക്ഷേത്രം • മീനൂട്ട് കടവ്", w * 0.5f, 58f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 24f
        c.drawText("TRIPRAYAR SREE RAMA TEMPLE GHAT", w * 0.5f, 108f, p)
        bmp
    }

    /**
     * NH 66 Green Overhead Gantry for Moothakunnam / Ernakulam Border south of Kottapuram Bridge.
     */
    fun createMoothakunnamGantrySpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE5_MOOTHAKUNNAM_GANTRY_V1",
        bmpWidth = 512,
        bmpHeight = 144,
        isDoubleSided = true
    ) {
        val w = 512f
        val h = 144f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.color = 0xFF006B4F.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(6f, 6f, w - 6f, h - 6f, p)
        p.style = Paint.Style.FILL

        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = 28f
        c.drawText("NH 66 • മൂത്തകുന്നം / MOOTHAKUNNAM", w * 0.5f, 54f, p)
        p.color = 0xFFFDE047.toInt()
        p.textSize = 24f
        c.drawText("NORTH PARAVUR 8 KM • KOCHI 34 KM ↑", w * 0.5f, 104f, p)
        bmp
    }
}
