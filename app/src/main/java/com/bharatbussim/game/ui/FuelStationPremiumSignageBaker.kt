package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.bharatbussim.game.ui.theme.PremiumFonts
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Ultra-HD Aspect-Corrected Architectural Signage & Dispenser Texture Baker for all
 * Thrissur Highway Fuel Stations ("BHARAT HIGHWAY FUELS").
 *
 * Eliminates the blurry, stretched low-resolution square-tile signage from the raw GLB atlas
 * by rendering every board directly in its true 3D physical aspect ratio using bundled
 * [PremiumFonts] (Outfit, Plus Jakarta Sans, JetBrains Mono, Oswald) with subpixel
 * anti-aliasing, vector pictograms, architectural metallic framing, and backlit LED styling:
 *
 *  - Tier B (13 Wide Horizontal Signboards): 2048 x 256 POT texture array (layers 0..12)
 *  - Tier C (4 Tall Vertical Hero Panels):   1024 x 2048 POT texture array (layers 0..3)
 *  - Live 3D Pump Dispenser LCD Overlay:     1024 x 720 crisp backlit digital readout
 */
internal object FuelStationPremiumSignageBaker {

    const val WIDE_TEX_W = 2048
    const val WIDE_TEX_H = 256
    const val WIDE_LAYER_COUNT = 13

    const val TALL_TEX_W = 1024
    const val TALL_TEX_H = 2048
    const val TALL_LAYER_COUNT = 4

    const val PUMP_METER_W = 1024
    const val PUMP_METER_H = 720

    // Structural non-text layers retained verbatim from thrissur_fuel_station_atlas.png
    val BASE_SOURCE_LAYERS = intArrayOf(0, 1, 4, 5, 9, 10, 23)

    private const val TEXT_PAINT_FLAGS =
        Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG

    private fun displayPaint(colorInt: Int, letterSpacingEm: Float = 0.04f): Paint =
        Paint(TEXT_PAINT_FLAGS).apply {
            color = colorInt
            typeface = PremiumFonts.displayTypefaceFor("", bold = true)
            letterSpacing = letterSpacingEm
            textAlign = Paint.Align.CENTER
        }

    private fun uiPaint(colorInt: Int, bold: Boolean = true, letterSpacingEm: Float = 0.04f): Paint =
        Paint(TEXT_PAINT_FLAGS).apply {
            color = colorInt
            typeface = PremiumFonts.uiTypefaceFor("", bold = bold)
            letterSpacing = letterSpacingEm
            textAlign = Paint.Align.CENTER
        }

    private fun monoPaint(colorInt: Int, letterSpacingEm: Float = 0.03f): Paint =
        Paint(TEXT_PAINT_FLAGS).apply {
            color = colorInt
            typeface = PremiumFonts.monoTypeface(bold = true)
            letterSpacing = letterSpacingEm
            textAlign = Paint.Align.CENTER
        }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        cx: Float,
        baselineY: Float,
        maxWidth: Float,
        baseSize: Float,
        paint: Paint,
        shadowColor: Int = 0,
        shadowDy: Float = 0f
    ) {
        if (text.isBlank()) return
        paint.textSize = baseSize
        val measured = paint.measureText(text)
        if (measured > maxWidth && measured > 1f) {
            paint.textSize = (baseSize * (maxWidth / measured)).coerceAtLeast(10f)
        }
        if (shadowColor != 0 && shadowDy != 0f) {
            val saved = paint.color
            paint.color = shadowColor
            canvas.drawText(text, cx, baselineY + shadowDy, paint)
            paint.color = saved
        }
        canvas.drawText(text, cx, baselineY, paint)
    }

    private fun baselineForCenterY(centerY: Float, paint: Paint): Float {
        val fm = paint.fontMetrics
        return centerY - (fm.ascent + fm.descent) * 0.5f
    }

    private fun drawFittedCenteredText(
        canvas: Canvas,
        text: String,
        cx: Float,
        centerY: Float,
        maxWidth: Float,
        baseSize: Float,
        paint: Paint,
        shadowColor: Int = 0x55000000,
        shadowDy: Float = 3f
    ) {
        if (text.isBlank()) return
        paint.textSize = baseSize
        val measured = paint.measureText(text)
        if (measured > maxWidth && measured > 1f) {
            paint.textSize = (baseSize * (maxWidth / measured)).coerceAtLeast(10f)
        }
        val baselineY = baselineForCenterY(centerY, paint)
        if (shadowColor != 0 && shadowDy != 0f) {
            val saved = paint.color
            paint.color = shadowColor
            canvas.drawText(text, cx, baselineY + shadowDy, paint)
            paint.color = saved
        }
        canvas.drawText(text, cx, baselineY, paint)
    }

    private fun drawBoardBase(
        canvas: Canvas,
        w: Float,
        h: Float,
        topColor: Int,
        botColor: Int,
        borderColor: Int = 0xFFFFFFFF.toInt(),
        outerFrameColor: Int = 0xFFCBD5E1.toInt(),
        cornerR: Float = 18f,
        strokeW: Float = 8f
    ) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h, topColor, botColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Subtle backlit polycarbonate top highlight sheen
        val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.44f,
                0x24FFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h * 0.44f, glossPaint)

        // Outer metallic trim (gives clean solid edge along 3D signboard thickness)
        val trimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = outerFrameColor
            strokeWidth = strokeW * 0.9f
        }
        canvas.drawRect(strokeW * 0.45f, strokeW * 0.45f, w - strokeW * 0.45f, h - strokeW * 0.45f, trimPaint)

        // Crisp inner architectural illuminated border
        val pad = strokeW * 1.65f
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = strokeW
        }
        canvas.drawRoundRect(RectF(pad, pad, w - pad, h - pad), cornerR, cornerR, innerPaint)
    }

    private inline fun withAspectCanvas(
        bmp: Bitmap,
        worldAspect: Float,
        designH: Float,
        drawBlock: (canvas: Canvas, w: Float, h: Float) -> Unit
    ) {
        val designW = designH * worldAspect
        val canvas = Canvas(bmp)
        canvas.save()
        canvas.scale(bmp.width.toFloat() / designW, bmp.height.toFloat() / designH)
        drawBlock(canvas, designW, designH)
        canvas.restore()
    }

    // =========================================================================
    // TIER B: 13 ULTRA-WIDE HORIZONTAL ARCHITECTURAL SIGNBOARDS (2048 x 256)
    // =========================================================================

    fun bakeWideSignageLayer(wideIdx: Int, outBmp: Bitmap) {
        when (wideIdx) {
            0 -> bakeCanopyFascia(outBmp)              // Layer 6  (W/H ~ 7.60, dU = 2 seamless repeat)
            1 -> bakeBaySign(outBmp, isDiesel = true)  // Layer 7  (ROW 1 • DIESEL, W/H = 5.82)
            2 -> bakeBaySign(outBmp, isDiesel = false) // Layer 8  (ROW 2 • PETROL, W/H = 5.82)
            3 -> bakeConvenienceStoreSign(outBmp)      // Layer 11 (CONVENIENCE STORE • 24x7 MART, W/H = 8.80)
            4 -> bakeOfficeReceptionSign(outBmp)       // Layer 12 (OFFICE & RECEPTION, W/H = 6.15)
            5 -> bakeStaffRoomSign(outBmp)             // Layer 13 (STAFF ROOM • ENTRY, W/H = 5.85)
            6 -> bakeRestroomSign(outBmp, isMen = true)  // Layer 14 (MEN RESTROOM, W/H = 2.22)
            7 -> bakeRestroomSign(outBmp, isMen = false) // Layer 15 (WOMEN RESTROOM, W/H = 2.22)
            8 -> bakeElectricalRoomSign(outBmp)        // Layer 16 (ELECTRICAL ROOM / DANGER 415V, W/H = 2.65)
            9 -> bakeStorageAreaSign(outBmp)           // Layer 17 (STORAGE AREA / AUTHORIZED ONLY, W/H = 2.65)
            10 -> bakeFreeAirWaterSign(outBmp)         // Layer 18 (FREE AIR & WATER / TYRE INFLATION, W/H = 3.87)
            11 -> bakeEntryExitSign(outBmp, isEntry = true)  // Layer 20 (ENTRY →, W/H = 2.73)
            12 -> bakeEntryExitSign(outBmp, isEntry = false) // Layer 21 (← EXIT, W/H = 2.73)
        }
    }

    private fun bakeCanopyFascia(outBmp: Bitmap) {
        // Seamless tiling (dU = 2.0 on 19m front / 13m side canopies): worldAspect = 7.60
        withAspectCanvas(outBmp, worldAspect = 7.60f, designH = 256f) { c, w, h ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // 1. Top Saffron-Orange Illuminated Crown Band (0..26%)
            val orangeBot = h * 0.27f
            p.shader = LinearGradient(0f, 0f, 0f, orangeBot, 0xFFF97316.toInt(), 0xFFD9480F.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, orangeBot, p)
            p.shader = null

            // 2. Crisp Pure White LED Divider Ribbon (27..33%)
            val whiteBot = h * 0.33f
            p.color = 0xFFFFFFFF.toInt()
            c.drawRect(0f, orangeBot, w, whiteBot, p)

            // 3. Deep Royal Navy Hero Fascia Band (33..84%)
            val navyBot = h * 0.84f
            p.shader = LinearGradient(0f, whiteBot, 0f, navyBot, 0xFF0A1D47.toInt(), 0xFF132F6E.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, whiteBot, w, navyBot, p)
            p.shader = null

            // Subtle upper illuminated gloss inside navy fascia
            p.shader = LinearGradient(0f, whiteBot, 0f, whiteBot + (navyBot - whiteBot) * 0.45f, 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, whiteBot, w, whiteBot + (navyBot - whiteBot) * 0.45f, p)
            p.shader = null

            // 4. Bottom Highway Gold Trim Band (84..100%)
            p.shader = LinearGradient(0f, navyBot, 0f, h, 0xFFFBBF24.toInt(), 0xFFD97706.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, navyBot, w, h, p)
            p.shader = null

            // Crisp thin silver highlight above gold band
            p.color = 0x66FFFFFF
            c.drawRect(0f, navyBot, w, navyBot + 3f, p)

            // Subtle architectural Aluminum Composite Panel (ACP) vertical seam joints (12 even panels = seamless wrap!)
            val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x1E000000
                strokeWidth = 2.5f
            }
            val panelCount = 12
            val panelW = w / panelCount
            for (i in 0 until panelCount) {
                val sx = i * panelW
                c.drawLine(sx, 0f, sx, h, seamPaint)
            }

            // Top band crisp tracked service strip
            val subPaint = uiPaint(0xFFFFF7ED.toInt(), bold = true, letterSpacingEm = 0.14f)
            drawFittedCenteredText(
                c,
                "PETROL   •   HIGH SPEED DIESEL   •   XP95   •   24×7 HIGHWAY SERVICE",
                w * 0.5f,
                orangeBot * 0.50f,
                w * 0.88f,
                34f,
                subPaint,
                shadowColor = 0x44000000,
                shadowDy = 2f
            )

            // Twin symmetrical geometric fuel emblems flanking the hero brand name
            val brandCenterY = (whiteBot + navyBot) * 0.50f
            drawFuelDropEmblem(c, w * 0.105f, brandCenterY, radius = 42f)
            drawFuelDropEmblem(c, w * 0.895f, brandCenterY, radius = 42f)

            // Main Hero Architectural Brand Lettering: BHARAT HIGHWAY FUELS
            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.09f)
            drawFittedCenteredText(
                c,
                "BHARAT HIGHWAY FUELS",
                w * 0.5f,
                brandCenterY,
                w * 0.72f,
                96f,
                titlePaint,
                shadowColor = 0x88000000.toInt(),
                shadowDy = 5f
            )
        }
    }

    private fun bakeBaySign(outBmp: Bitmap, isDiesel: Boolean) {
        withAspectCanvas(outBmp, worldAspect = 5.82f, designH = 256f) { c, w, h ->
            val topCol = if (isDiesel) 0xFF1D64C2.toInt() else 0xFF059669.toInt()
            val botCol = if (isDiesel) 0xFF0D3E84.toInt() else 0xFF046C4E.toInt()
            val badgeBg = if (isDiesel) 0xFFF59E0B.toInt() else 0xFFFFFFFF.toInt()
            val badgeFg = if (isDiesel) 0xFF0A1931.toInt() else 0xFF046C4E.toInt()

            drawBoardBase(c, w, h, topCol, botCol, borderColor = 0xFFFFFFFF.toInt(), outerFrameColor = 0xFFE2E8F0.toInt(), cornerR = 20f, strokeW = 9f)

            // Left high-contrast Row Badge Pill
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeBg }
            val pillRect = RectF(58f, 44f, 408f, h - 44f)
            c.drawRoundRect(pillRect, 24f, 24f, pillPaint)

            val pillTextPaint = displayPaint(badgeFg, letterSpacingEm = 0.06f)
            drawFittedCenteredText(
                c,
                if (isDiesel) "ROW 1" else "ROW 2",
                pillRect.centerX(),
                h * 0.50f,
                pillRect.width() - 36f,
                84f,
                pillTextPaint,
                shadowColor = 0,
                shadowDy = 0f
            )

            // Center huge ultra-crisp fuel title precisely between left & right pills
            val rightPillLeft = w - 392f
            val titleCx = (pillRect.right + rightPillLeft) * 0.50f
            val titleMaxW = (rightPillLeft - pillRect.right) - 64f
            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.08f)
            drawFittedCenteredText(
                c,
                if (isDiesel) "DIESEL" else "PETROL",
                titleCx,
                h * 0.50f,
                titleMaxW,
                122f,
                titlePaint,
                shadowColor = 0x66000000,
                shadowDy = 5f
            )

            // Right sleek specification pill (BUS / HEAVY vs UNLEADED / XP95)
            val tagRect = RectF(rightPillLeft, 64f, w - 58f, h - 64f)
            val tagFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
            val tagStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = 0xAAFFFFFF.toInt()
            }
            c.drawRoundRect(tagRect, 20f, 20f, tagFill)
            c.drawRoundRect(tagRect, 20f, 20f, tagStroke)

            val tagPaint = uiPaint(0xFFF8FAFC.toInt(), bold = true, letterSpacingEm = 0.06f)
            drawFittedCenteredText(
                c,
                if (isDiesel) "HEAVY & BUS" else "XP95 • UNLEADED",
                tagRect.centerX(),
                h * 0.50f,
                tagRect.width() - 28f,
                40f,
                tagPaint,
                shadowColor = 0,
                shadowDy = 0f
            )
        }
    }

    private fun bakeConvenienceStoreSign(outBmp: Bitmap) {
        withAspectCanvas(outBmp, worldAspect = 8.80f, designH = 256f) { c, w, h ->
            drawBoardBase(
                c, w, h,
                topColor = 0xFF142E6E.toInt(),
                botColor = 0xFF0A1B44.toInt(),
                borderColor = 0xFFFBBF24.toInt(),
                outerFrameColor = 0xFFCBD5E1.toInt(),
                cornerR = 20f,
                strokeW = 8f
            )

            // Left Storefront Bag Emblem
            drawStoreBagIcon(c, 118f, h * 0.50f, 52f)

            // Primary Hero Title (zero squishing!)
            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.07f).apply {
                textAlign = Paint.Align.LEFT
            }
            val titleX = 212f
            drawFittedCenteredText(
                c,
                "CONVENIENCE STORE",
                titleX,
                h * 0.49f,
                1040f,
                96f,
                titlePaint,
                shadowColor = 0x77000000,
                shadowDy = 4f
            )

            // Glowing Amber 24x7 MART Pill Badge
            val pillRect = RectF(1290f, 48f, 1700f, h - 48f)
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, pillRect.top, 0f, pillRect.bottom, 0xFFFBBF24.toInt(), 0xFFD97706.toInt(), Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(pillRect, 24f, 24f, pillPaint)
            val badgeTextPaint = displayPaint(0xFF0A1931.toInt(), letterSpacingEm = 0.05f)
            drawFittedCenteredText(
                c,
                "24×7 MART",
                pillRect.centerX(),
                h * 0.50f,
                pillRect.width() - 36f,
                68f,
                badgeTextPaint,
                shadowColor = 0,
                shadowDy = 0f
            )

            // Right Amenities Column
            val subPaint = uiPaint(0xFFDBEAFE.toInt(), bold = true, letterSpacingEm = 0.08f)
            drawFittedCenteredText(c, "COFFEE • SNACKS", w - 280f, h * 0.36f, 440f, 38f, subPaint, 0, 0f)
            drawFittedCenteredText(c, "COLD DRINKS • ATM", w - 280f, h * 0.67f, 440f, 36f, subPaint, 0, 0f)
        }
    }

    private fun bakeOfficeReceptionSign(outBmp: Bitmap) {
        withAspectCanvas(outBmp, worldAspect = 6.15f, designH = 256f) { c, w, h ->
            drawBoardBase(
                c, w, h,
                topColor = 0xFFF97316.toInt(),
                botColor = 0xFFC2410C.toInt(),
                borderColor = 0xFFFFFFFF.toInt(),
                outerFrameColor = 0xFFFFEDD5.toInt(),
                cornerR = 20f,
                strokeW = 9f
            )

            // Accent dark-navy left bar
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1F4B.toInt() }
            c.drawRoundRect(RectF(56f, 48f, 82f, h - 48f), 8f, 8f, barPaint)

            val pillLeft = w - 390f
            val titleCx = (82f + pillLeft) * 0.50f
            val titleMaxW = (pillLeft - 82f) - 72f
            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.07f)
            drawFittedCenteredText(
                c,
                "OFFICE  &  RECEPTION",
                titleCx,
                h * 0.50f,
                titleMaxW,
                96f,
                titlePaint,
                shadowColor = 0x66000000,
                shadowDy = 4f
            )

            // Right crisp pill badge
            val pillRect = RectF(pillLeft, 64f, w - 58f, h - 64f)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1F4B.toInt() }
            c.drawRoundRect(pillRect, 18f, 18f, pillBg)
            val subPaint = uiPaint(0xFFFDE68A.toInt(), bold = true, letterSpacingEm = 0.07f)
            drawFittedCenteredText(c, "MANAGER • BILLS", pillRect.centerX(), h * 0.50f, pillRect.width() - 28f, 36f, subPaint, 0, 0f)
        }
    }

    private fun bakeStaffRoomSign(outBmp: Bitmap) {
        withAspectCanvas(outBmp, worldAspect = 5.85f, designH = 256f) { c, w, h ->
            drawBoardBase(
                c, w, h,
                topColor = 0xFF334155.toInt(),
                botColor = 0xFF1E293B.toInt(),
                borderColor = 0xFFE2E8F0.toInt(),
                outerFrameColor = 0xFF94A3B8.toInt(),
                cornerR = 20f,
                strokeW = 9f
            )

            // Left warm amber vertical bar
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() }
            c.drawRoundRect(RectF(58f, 48f, 82f, h - 48f), 8f, 8f, accent)

            val pillLeft = w - 430f
            val titleCx = (82f + pillLeft) * 0.50f
            val titleMaxW = (pillLeft - 82f) - 72f
            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.07f)
            drawFittedCenteredText(
                c,
                "STAFF ROOM",
                titleCx,
                h * 0.50f,
                titleMaxW,
                100f,
                titlePaint,
                shadowColor = 0x66000000,
                shadowDy = 4f
            )

            // Right ENTRY Pill
            val pillRect = RectF(pillLeft, 52f, w - 60f, h - 52f)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() }
            c.drawRoundRect(pillRect, 20f, 20f, pillBg)
            val pillTxt = displayPaint(0xFF0F172A.toInt(), letterSpacingEm = 0.08f)
            drawFittedCenteredText(c, "ENTRY", pillRect.centerX(), h * 0.50f, pillRect.width() - 32f, 68f, pillTxt, 0, 0f)
        }
    }

    private fun bakeRestroomSign(outBmp: Bitmap, isMen: Boolean) {
        withAspectCanvas(outBmp, worldAspect = 2.22f, designH = 360f) { c, w, h ->
            val topCol = if (isMen) 0xFF1A73E8.toInt() else 0xFFBE185D.toInt()
            val botCol = if (isMen) 0xFF0C4A9E.toInt() else 0xFF831843.toInt()
            drawBoardBase(c, w, h, topCol, botCol, borderColor = 0xFFFFFFFF.toInt(), outerFrameColor = 0xFFE2E8F0.toInt(), cornerR = 24f, strokeW = 10f)

            // Left Circular Pictogram Badge
            val iconCx = 152f
            val iconCy = h * 0.50f
            val circleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
            val circleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                color = 0xBBFFFFFF.toInt()
            }
            c.drawCircle(iconCx, iconCy, 102f, circleBg)
            c.drawCircle(iconCx, iconCy, 102f, circleRing)
            drawRestroomIcon(c, iconCx, iconCy, isMale = isMen)

            // Right Clean Stacked Typography
            val textCx = (268f + w - 40f) * 0.50f
            val textMaxW = w - 318f
            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.07f)
            drawFittedCenteredText(c, if (isMen) "MEN" else "WOMEN", textCx, h * 0.37f, textMaxW, 102f, titlePaint, 0x55000000, 4f)

            val subPaint = uiPaint(0xFFE0F2FE.toInt(), bold = true, letterSpacingEm = 0.12f)
            drawFittedCenteredText(c, "RESTROOM", textCx, h * 0.71f, textMaxW, 56f, subPaint, 0x44000000, 3f)
        }
    }

    private fun bakeElectricalRoomSign(outBmp: Bitmap) {
        withAspectCanvas(outBmp, worldAspect = 2.65f, designH = 360f) { c, w, h ->
            // Safety Yellow panel with bold black industrial border
            drawBoardBase(
                c, w, h,
                topColor = 0xFFFACC15.toInt(),
                botColor = 0xFFEAB308.toInt(),
                borderColor = 0xFF0F172A.toInt(),
                outerFrameColor = 0xFF0F172A.toInt(),
                cornerR = 18f,
                strokeW = 11f
            )

            // Top Crimson DANGER 415V Banner
            val bannerRect = RectF(28f, 28f, w - 28f, 122f)
            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDC2626.toInt() }
            c.drawRoundRect(bannerRect, 12f, 12f, redPaint)
            val dangerPaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.10f)
            drawFittedCenteredText(c, "DANGER  •  415 VOLTS", w * 0.50f, bannerRect.centerY(), w - 90f, 54f, dangerPaint, 0x55000000, 2f)

            // Left High-Voltage Warning Triangle Icon
            drawWarningTriangleIcon(c, 146f, 232f, size = 148f)

            // Right Crisp Black Bold Typography
            val textCx = (240f + w - 36f) * 0.50f
            val maxW = w - 286f
            val titlePaint = displayPaint(0xFF0F172A.toInt(), letterSpacingEm = 0.05f)
            drawFittedCenteredText(c, "ELECTRICAL ROOM", textCx, 206f, maxW, 74f, titlePaint, 0, 0f)
            val subPaint = uiPaint(0xFF1E293B.toInt(), bold = true, letterSpacingEm = 0.08f)
            drawFittedCenteredText(c, "HIGH VOLTAGE • KEEP OUT", textCx, 282f, maxW, 42f, subPaint, 0, 0f)
        }
    }

    private fun bakeStorageAreaSign(outBmp: Bitmap) {
        withAspectCanvas(outBmp, worldAspect = 2.65f, designH = 360f) { c, w, h ->
            drawBoardBase(
                c, w, h,
                topColor = 0xFF3B4758.toInt(),
                botColor = 0xFF1F2937.toInt(),
                borderColor = 0xFFE2E8F0.toInt(),
                outerFrameColor = 0xFF94A3B8.toInt(),
                cornerR = 18f,
                strokeW = 10f
            )

            // Amber top & bottom caution accent strips inside frame
            val amber = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF59E0B.toInt() }
            c.drawRect(30f, 30f, w - 30f, 48f, amber)
            c.drawRect(30f, h - 48f, w - 30f, h - 30f, amber)

            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.06f)
            drawFittedCenteredText(c, "STORAGE AREA", w * 0.50f, h * 0.40f, w - 100f, 92f, titlePaint, 0x66000000, 4f)

            // Restricted Pill Subtitle
            val pillRect = RectF(120f, h * 0.59f, w - 120f, h * 0.80f)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() }
            c.drawRoundRect(pillRect, 16f, 16f, pillBg)
            val subPaint = uiPaint(0xFFFBBF24.toInt(), bold = true, letterSpacingEm = 0.08f)
            drawFittedCenteredText(c, "AUTHORIZED PERSONNEL ONLY", w * 0.50f, pillRect.centerY(), pillRect.width() - 40f, 44f, subPaint, 0, 0f)
        }
    }

    private fun bakeFreeAirWaterSign(outBmp: Bitmap) {
        withAspectCanvas(outBmp, worldAspect = 3.87f, designH = 320f) { c, w, h ->
            drawBoardBase(
                c, w, h,
                topColor = 0xFF0284C7.toInt(),
                botColor = 0xFF035388.toInt(),
                borderColor = 0xFFFFFFFF.toInt(),
                outerFrameColor = 0xFFE0F2FE.toInt(),
                cornerR = 22f,
                strokeW = 10f
            )

            // Left Circular Digital Gauge Vector Icon
            drawAirGaugeIcon(c, 154f, h * 0.50f, 88f)

            // Right Clean Stacked Typography
            val textCx = (268f + w - 42f) * 0.50f
            val maxW = w - 320f
            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.06f)
            drawFittedCenteredText(c, "FREE AIR & WATER", textCx, h * 0.36f, maxW, 92f, titlePaint, 0x55000000, 4f)

            // Sub-badge for Digital Tyre Inflation
            val pillRect = RectF(286f, h * 0.58f, w - 62f, h * 0.82f)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
            c.drawRoundRect(pillRect, 18f, 18f, pillBg)
            val subPaint = uiPaint(0xFFBAE6FD.toInt(), bold = true, letterSpacingEm = 0.10f)
            drawFittedCenteredText(c, "DIGITAL TYRE INFLATION", pillRect.centerX(), pillRect.centerY(), pillRect.width() - 36f, 48f, subPaint, 0, 0f)
        }
    }

    private fun bakeEntryExitSign(outBmp: Bitmap, isEntry: Boolean) {
        withAspectCanvas(outBmp, worldAspect = 2.73f, designH = 340f) { c, w, h ->
            val topCol = if (isEntry) 0xFF10B981.toInt() else 0xFFDC2626.toInt()
            val botCol = if (isEntry) 0xFF047857.toInt() else 0xFF991B1B.toInt()
            drawBoardBase(c, w, h, topCol, botCol, borderColor = 0xFFFFFFFF.toInt(), outerFrameColor = 0xFFF8FAFC.toInt(), cornerR = 24f, strokeW = 11f)

            val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.08f)
            if (isEntry) {
                drawFittedCenteredText(c, "ENTRY", w * 0.36f, h * 0.50f, w * 0.54f, 148f, titlePaint, 0x55000000, 5f)
                drawVectorArrow(c, cx = w * 0.78f, cy = h * 0.50f, pointingRight = true, length = 210f, thickness = 46f)
            } else {
                drawVectorArrow(c, cx = w * 0.22f, cy = h * 0.50f, pointingRight = false, length = 210f, thickness = 46f)
                drawFittedCenteredText(c, "EXIT", w * 0.62f, h * 0.50f, w * 0.54f, 152f, titlePaint, 0x55000000, 5f)
            }
        }
    }

    // =========================================================================
    // TIER C: 4 ULTRA-HD VERTICAL HERO BOARDS & DISPENSERS (1024 x 2048)
    // =========================================================================

    fun bakeTallSignageLayer(tallIdx: Int, outBmp: Bitmap) {
        when (tallIdx) {
            0 -> bakeFuelDispenserFaceplate(outBmp, isDiesel = true)  // Layer 2 (0.80m x 1.48m)
            1 -> bakeFuelDispenserFaceplate(outBmp, isDiesel = false) // Layer 3 (0.80m x 1.48m)
            2 -> bakeDigitalAirConsole(outBmp)                        // Layer 19 (0.50m x 1.00m)
            3 -> renderRoadsidePriceMonolith(outBmp)                  // Layer 22 (3.12m x 6.32m)
        }
    }

    /**
     * Photorealistic 1024 x 2048 Indian Highway Fuel Dispenser Faceplate.
     * Preserves exact GLB LCD housing bounds normX=[0.094..0.906], normY=[0.198..0.490] so
     * the live animated 3D pump meter overlays seamlessly on the active dispenser during refueling.
     */
    private fun bakeFuelDispenserFaceplate(outBmp: Bitmap, isDiesel: Boolean) {
        val w = outBmp.width.toFloat()
        val h = outBmp.height.toFloat()
        val c = Canvas(outBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val headerTop = if (isDiesel) 0xFF1D64C2.toInt() else 0xFF059669.toInt()
        val headerBot = if (isDiesel) 0xFF0D47A1.toInt() else 0xFF046C4E.toInt()
        val sideFrameCol = if (isDiesel) 0xFF0F4C97.toInt() else 0xFF047857.toInt()

        // Outer metallic side chassis frame
        c.drawColor(sideFrameCol)

        // Brushed stainless-white recessed front panel body
        val bodyPad = w * 0.038f
        val headerH = h * 0.168f
        p.shader = LinearGradient(0f, headerH, 0f, h, 0xFFF8FAFC.toInt(), 0xFFE2E8F0.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(bodyPad, headerH - 12f, w - bodyPad, h - bodyPad * 0.6f), 20f, 20f, p)
        p.shader = null

        // Top Illuminated Branded Fuel Type Header (0 .. 16.8%)
        p.shader = LinearGradient(0f, 0f, 0f, headerH, headerTop, headerBot, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, headerH, p)
        p.shader = null

        // Crisp white illuminated header border
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(20f, 20f, w - 20f, headerH - 20f), 18f, 18f, p)
        p.style = Paint.Style.FILL

        val hdrTitle = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.09f)
        drawFittedCenteredText(
            c,
            if (isDiesel) "DIESEL" else "PETROL",
            w * 0.50f,
            headerH * 0.44f,
            w - 100f,
            148f,
            hdrTitle,
            0x66000000,
            6f
        )
        val hdrSub = uiPaint(if (isDiesel) 0xFFFDE68A.toInt() else 0xFFA7F3D0.toInt(), bold = true, letterSpacingEm = 0.12f)
        drawFittedCenteredText(
            c,
            if (isDiesel) "HIGH SPEED DIESEL  •  BS-VI" else "UNLEADED PETROL  •  XP95",
            w * 0.50f,
            headerH * 0.78f,
            w - 110f,
            34f,
            hdrSub,
            0x44000000,
            2f
        )

        // Built-in Dark Glass Digital LCD Housing — EXACT GLB bounds normX=[0.094..0.906], normY=[0.198..0.490]
        val lcdRect = RectF(w * 0.094f, h * 0.198f, w * 0.906f, h * 0.490f)
        drawDispenserLcdModule(
            canvas = c,
            bounds = lcdRect,
            saleStr = if (isDiesel) "2500.00" else "1042.10",
            volStr = if (isDiesel) "26.39" else "10.00",
            rateStr = String.format(
                Locale.ROOT,
                "%.2f",
                if (isDiesel) FuelType.DIESEL.pricePerLiterInr else FuelType.PETROL.pricePerLiterInr
            ),
            isFlowing = false,
            pulseOn = false,
            drawOuterBezel = true
        )

        // Tactile Preset Keypad + Emergency Stop Row (normY = 0.518 .. 0.672)
        val kpTop = h * 0.518f
        val kpBot = h * 0.672f
        val kpRect = RectF(w * 0.094f, kpTop, w * 0.655f, kpBot)
        p.color = 0xFF0F172A.toInt()
        c.drawRoundRect(kpRect, 18f, 18f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = 0xFF475569.toInt()
        c.drawRoundRect(kpRect, 18f, 18f, p)
        p.style = Paint.Style.FILL

        val btnLabels = arrayOf("₹100", "₹500", "₹1000", "FULL", "LITRE", "CLEAR")
        val padIn = 18f
        val gap = 14f
        val cellW = (kpRect.width() - padIn * 2f - gap * 2f) / 3f
        val cellH = (kpRect.height() - padIn * 2f - gap) / 2f
        val btnText = uiPaint(0xFFFFFFFF.toInt(), bold = true, letterSpacingEm = 0.03f)
        for (i in 0 until 6) {
            val col = i % 3
            val row = i / 3
            val bx = kpRect.left + padIn + col * (cellW + gap)
            val by = kpRect.top + padIn + row * (cellH + gap)
            val br = RectF(bx, by, bx + cellW, by + cellH)
            val btnTop = if (i == 5) 0xFFDC2626.toInt() else if (isDiesel) 0xFF2563EB.toInt() else 0xFF10B981.toInt()
            val btnBot = if (i == 5) 0xFF991B1B.toInt() else if (isDiesel) 0xFF1E40AF.toInt() else 0xFF047857.toInt()
            p.shader = LinearGradient(0f, br.top, 0f, br.bottom, btnTop, btnBot, Shader.TileMode.CLAMP)
            c.drawRoundRect(br, 12f, 12f, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            p.color = 0x66FFFFFF
            c.drawRoundRect(br, 12f, 12f, p)
            p.style = Paint.Style.FILL
            drawFittedCenteredText(c, btnLabels[i], br.centerX(), br.centerY(), br.width() - 16f, 36f, btnText, 0x55000000, 2f)
        }

        // Right Emergency Stop Housing
        val stopRect = RectF(w * 0.680f, kpTop, w * 0.906f, kpBot)
        p.shader = LinearGradient(0f, stopRect.top, 0f, stopRect.bottom, 0xFFFDE047.toInt(), 0xFFEAB308.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(stopRect, 18f, 18f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = 0xFF713F12.toInt()
        c.drawRoundRect(stopRect, 18f, 18f, p)
        p.style = Paint.Style.FILL

        val stopLbl = displayPaint(0xFF0F172A.toInt(), letterSpacingEm = 0.04f)
        drawFittedCenteredText(c, "EMERGENCY", stopRect.centerX(), stopRect.top + 38f, stopRect.width() - 24f, 30f, stopLbl, 0, 0f)
        drawFittedCenteredText(c, "STOP", stopRect.centerX(), stopRect.top + 74f, stopRect.width() - 24f, 34f, stopLbl, 0, 0f)

        // 3D Crimson Mushroom Push Button
        val btnCx = stopRect.centerX()
        val btnCy = stopRect.bottom - 94f
        p.color = 0xFF7F1D1D.toInt()
        c.drawCircle(btnCx, btnCy + 6f, 64f, p)
        p.shader = LinearGradient(btnCx - 54f, btnCy - 54f, btnCx + 54f, btnCy + 54f, 0xFFEF4444.toInt(), 0xFFB91C1C.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(btnCx, btnCy, 58f, p)
        p.shader = null
        p.color = 0x44FFFFFF
        c.drawCircle(btnCx - 16f, btnCy - 18f, 20f, p)

        // Deep Navy Brand Ribbon (normY = 0.695 .. 0.752)
        val brandRect = RectF(w * 0.094f, h * 0.695f, w * 0.906f, h * 0.752f)
        p.color = 0xFF0B1F4B.toInt()
        c.drawRoundRect(brandRect, 12f, 12f, p)
        val brandPaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.10f)
        drawFittedCenteredText(c, "BHARAT HIGHWAY FUELS", brandRect.centerX(), brandRect.centerY(), brandRect.width() - 48f, 44f, brandPaint, 0, 0f)

        // 3 Crisp High-Contrast Safety Notice Strips (normY = 0.770 .. 0.906)
        val notices = arrayOf("NO SMOKING", "SWITCH OFF ENGINE", "NO MOBILE PHONES")
        val nStartY = h * 0.770f
        val nHeight = 76f
        val nGap = 18f
        val noticePaint = displayPaint(0xFFB91C1C.toInt(), letterSpacingEm = 0.08f)
        for (i in notices.indices) {
            val top = nStartY + i * (nHeight + nGap)
            val nr = RectF(w * 0.115f, top, w * 0.885f, top + nHeight)
            p.color = 0xFFFFF7ED.toInt()
            c.drawRoundRect(nr, 12f, 12f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 4f
            p.color = 0xFFDC2626.toInt()
            c.drawRoundRect(nr, 12f, 12f, p)
            p.style = Paint.Style.FILL

            // Left crisp red prohibition ring icon
            drawNoSignIcon(c, nr.left + 44f, nr.centerY(), 22f)
            drawFittedCenteredText(c, notices[i], nr.centerX() + 18f, nr.centerY(), nr.width() - 120f, 38f, noticePaint, 0, 0f)
        }

        // Bottom Architectural Ventilation Louvers (normY = 0.922 .. 0.976)
        val ventTop = h * 0.922f
        val ventGap = 18f
        for (v in 0 until 6) {
            val vy = ventTop + v * ventGap
            p.color = 0xFF334155.toInt()
            c.drawRoundRect(RectF(w * 0.16f, vy, w * 0.84f, vy + 9f), 4f, 4f, p)
        }
    }

    private fun bakeDigitalAirConsole(outBmp: Bitmap) {
        val w = outBmp.width.toFloat()
        val h = outBmp.height.toFloat()
        val c = Canvas(outBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Deep Navy Console Chassis
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF0E2954.toInt(), 0xFF091834.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Top Vibrant Orange Backlit Header
        val hdrH = h * 0.165f
        p.shader = LinearGradient(0f, 0f, 0f, hdrH, 0xFFF97316.toInt(), 0xFFEA580C.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, hdrH, p)
        p.shader = null
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(0f, hdrH - 8f, w, hdrH, p)

        val hdrTitle = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.08f)
        drawFittedCenteredText(c, "DIGITAL AIR", w * 0.50f, hdrH * 0.42f, w - 80f, 118f, hdrTitle, 0x55000000, 5f)
        val hdrSub = uiPaint(0xFFFFF7ED.toInt(), bold = true, letterSpacingEm = 0.12f)
        drawFittedCenteredText(c, "AUTO TYRE INFLATOR", w * 0.50f, hdrH * 0.77f, w - 90f, 38f, hdrSub, 0, 0f)

        // Recessed Dark Glass PSI Display Screen
        val lcd = RectF(74f, h * 0.21f, w - 74f, h * 0.55f)
        p.color = 0xFF050B10.toInt()
        c.drawRoundRect(lcd, 28f, 28f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = 0xFF38BDF8.toInt()
        c.drawRoundRect(lcd, 28f, 28f, p)
        p.style = Paint.Style.FILL

        val lblPaint = uiPaint(0xFF94A3B8.toInt(), bold = true, letterSpacingEm = 0.10f)
        drawFittedCenteredText(c, "TARGET PRESSURE (PSI)", w * 0.50f, lcd.top + 88f, lcd.width() - 80f, 44f, lblPaint, 0, 0f)

        // Subtle unlit ghost segments + razor-sharp mint-cyan 32.0 PSI
        val psiGhost = monoPaint(0x185EEAD4, letterSpacingEm = 0.04f)
        drawFittedCenteredText(c, "88.8 PSI", w * 0.50f, lcd.centerY() + 56f, lcd.width() - 72f, 178f, psiGhost, 0, 0f)
        val psiPaint = monoPaint(0xFF5EEAD4.toInt(), letterSpacingEm = 0.04f)
        drawFittedCenteredText(c, "32.0 PSI", w * 0.50f, lcd.centerY() + 56f, lcd.width() - 72f, 178f, psiPaint, 0x442DD4BF, 0f)

        // Tactile + and - Buttons
        val btnTop = h * 0.60f
        val btnBot = h * 0.73f
        val plusRect = RectF(94f, btnTop, w * 0.46f, btnBot)
        val minusRect = RectF(w * 0.54f, btnTop, w - 94f, btnBot)
        for ((rect, sym) in arrayOf(plusRect to "+", minusRect to "−")) {
            p.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFF2563EB.toInt(), 0xFF1D4ED8.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, 24f, 24f, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5f
            p.color = 0xFFBAE6FD.toInt()
            c.drawRoundRect(rect, 24f, 24f, p)
            p.style = Paint.Style.FILL
            val symPaint = displayPaint(0xFFFFFFFF.toInt(), 0f)
            drawFittedCenteredText(c, sym, rect.centerX(), rect.centerY(), rect.width() - 40f, 140f, symPaint, 0x55000000, 4f)
        }

        // Vehicle Mode Selector Bar
        val modeRect = RectF(94f, h * 0.775f, w - 94f, h * 0.865f)
        p.shader = LinearGradient(0f, modeRect.top, 0f, modeRect.bottom, 0xFF10B981.toInt(), 0xFF047857.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(modeRect, 20f, 20f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0xFFA7F3D0.toInt()
        c.drawRoundRect(modeRect, 20f, 20f, p)
        p.style = Paint.Style.FILL
        val modePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.08f)
        drawFittedCenteredText(c, "CAR  •  BIKE  •  BUS  •  TRUCK", w * 0.50f, modeRect.centerY(), modeRect.width() - 48f, 44f, modePaint, 0x44000000, 2f)

        // Bottom Service Footer
        val footPaint = uiPaint(0xFFE2E8F0.toInt(), bold = true, letterSpacingEm = 0.10f)
        drawFittedCenteredText(c, "FREE AIR & WATER SERVICE", w * 0.50f, h * 0.935f, w - 100f, 42f, footPaint, 0, 0f)
    }

    /**
     * Flagship 1024 x 2048 Roadside 9.22m Highway Fuel Price Monolith.
     * Rendered with exact 1:2 aspect ratio (matching the 3.12m x 6.32m 3D monolith face),
     * glowing digital LED rate modules, and crisp architectural typography.
     */
    fun renderRoadsidePriceMonolith(outBmp: Bitmap) {
        val w = outBmp.width.toFloat()
        val h = outBmp.height.toFloat()
        val c = Canvas(outBmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Deep Royal-Navy Architectural Totem Body
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF0D234A.toInt(), 0xFF071329.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        // Outer Brushed Metallic Frame
        p.style = Paint.Style.STROKE
        p.strokeWidth = 18f
        p.color = 0xFF475569.toInt()
        c.drawRect(9f, 9f, w - 9f, h - 9f, p)
        p.style = Paint.Style.FILL

        // 1. TOP SAFFRON-ORANGE ILLUMINATED BRAND CROWN (0 .. 23%)
        val crownTop = 26f
        val crownBot = h * 0.232f
        val crownRect = RectF(26f, crownTop, w - 26f, crownBot)
        p.shader = LinearGradient(0f, crownTop, 0f, crownBot, 0xFFF97316.toInt(), 0xFFD9480F.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(crownRect, 20f, 20f, p)
        p.shader = null

        // Subtle gloss sheen on crown
        p.shader = LinearGradient(0f, crownTop, 0f, crownTop + 160f, 0x28FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(26f, crownTop, w - 26f, crownTop + 160f), 20f, 20f, p)
        p.shader = null

        // Crisp white inner crown border + golden bottom divider bar
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = 0xDDFFFFFF.toInt()
        c.drawRoundRect(RectF(42f, crownTop + 16f, w - 42f, crownBot - 18f), 14f, 14f, p)
        p.style = Paint.Style.FILL

        p.color = 0xFFFBBF24.toInt()
        c.drawRect(26f, crownBot - 16f, w - 26f, crownBot + 4f, p)

        val crownTitle = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.08f)
        drawFittedCenteredText(c, "FUEL STATION", w * 0.50f, crownTop + 132f, w - 120f, 118f, crownTitle, 0x66000000, 6f)

        val crownBrand = displayPaint(0xFFFFF7ED.toInt(), letterSpacingEm = 0.07f)
        drawFittedCenteredText(c, "BHARAT HIGHWAY FUELS", w * 0.50f, crownTop + 264f, w - 120f, 68f, crownBrand, 0x44000000, 3f)

        val crownSub = uiPaint(0xFFFEF08A.toInt(), bold = true, letterSpacingEm = 0.14f)
        drawFittedCenteredText(c, "THRISSUR HIGHWAY  •  24×7", w * 0.50f, crownTop + 356f, w - 130f, 44f, crownSub, 0x33000000, 2f)

        // 2. PETROL DIGITAL RATE CARD (25.8% .. 54.0%)
        drawMonolithPriceCard(
            canvas = c,
            bounds = RectF(52f, h * 0.258f, w - 52f, h * 0.540f),
            cardTopCol = 0xFF0B8A4E.toInt(),
            cardBotCol = 0xFF046036.toInt(),
            borderCol = 0xFFA7F3D0.toInt(),
            ledBorderCol = 0xFF10B981.toInt(),
            ledGlowCol = 0xFF34D399.toInt(),
            fuelTitle = "PETROL",
            fuelBadge = "XP 95 • BS-VI",
            priceInr = FuelType.PETROL.pricePerLiterInr
        )

        // 3. DIESEL DIGITAL RATE CARD (56.8% .. 85.0%)
        drawMonolithPriceCard(
            canvas = c,
            bounds = RectF(52f, h * 0.568f, w - 52f, h * 0.850f),
            cardTopCol = 0xFF1D64C2.toInt(),
            cardBotCol = 0xFF0C418A.toInt(),
            borderCol = 0xFFBAE6FD.toInt(),
            ledBorderCol = 0xFFF59E0B.toInt(),
            ledGlowCol = 0xFFFBBF24.toInt(),
            fuelTitle = "DIESEL",
            fuelBadge = "HIGH SPEED",
            priceInr = FuelType.DIESEL.pricePerLiterInr
        )

        // 4. CRISP ILLUMINATED WHITE AMENITIES FOOTER (87.8% .. 98.6%)
        val footRect = RectF(26f, h * 0.878f, w - 26f, h - 26f)
        p.color = 0xFFF8FAFC.toInt()
        c.drawRoundRect(footRect, 16f, 16f, p)

        val openPaint = displayPaint(0xFF0B1F4B.toInt(), letterSpacingEm = 0.09f)
        drawFittedCenteredText(c, "24 HOURS OPEN", w * 0.50f, footRect.top + footRect.height() * 0.36f, footRect.width() - 60f, 74f, openPaint, 0, 0f)

        val amenPaint = uiPaint(0xFFD9530F.toInt(), bold = true, letterSpacingEm = 0.07f)
        drawFittedCenteredText(c, "FREE AIR & WATER  •  MART  •  RESTROOMS", w * 0.50f, footRect.top + footRect.height() * 0.74f, footRect.width() - 64f, 42f, amenPaint, 0, 0f)
    }

    private fun drawMonolithPriceCard(
        canvas: Canvas,
        bounds: RectF,
        cardTopCol: Int,
        cardBotCol: Int,
        borderCol: Int,
        ledBorderCol: Int,
        ledGlowCol: Int,
        fuelTitle: String,
        fuelBadge: String,
        priceInr: Float
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, bounds.top, 0f, bounds.bottom, cardTopCol, cardBotCol, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, 34f, 34f, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        p.color = borderCol
        canvas.drawRoundRect(bounds, 34f, 34f, p)
        p.style = Paint.Style.FILL

        // Header Row: Bold Fuel Name + Crisp Spec Pill
        val headerY = bounds.top + bounds.height() * 0.21f
        val titlePaint = displayPaint(0xFFFFFFFF.toInt(), letterSpacingEm = 0.07f).apply {
            textAlign = Paint.Align.LEFT
        }
        drawFittedCenteredText(canvas, fuelTitle, bounds.left + 44f, headerY, bounds.width() * 0.52f, 108f, titlePaint, 0x55000000, 4f)

        val pillRect = RectF(bounds.right - 344f, headerY - 40f, bounds.right - 40f, headerY + 40f)
        p.color = 0x38000000
        canvas.drawRoundRect(pillRect, 18f, 18f, p)
        val badgePaint = uiPaint(0xFFF8FAFC.toInt(), bold = true, letterSpacingEm = 0.06f)
        drawFittedCenteredText(canvas, fuelBadge, pillRect.centerX(), pillRect.centerY(), pillRect.width() - 28f, 38f, badgePaint, 0, 0f)

        // Recessed Obsidian Digital LED Readout Box
        val ledRect = RectF(bounds.left + 38f, bounds.top + bounds.height() * 0.40f, bounds.right - 38f, bounds.bottom - 38f)
        p.color = 0xFF04090E.toInt()
        canvas.drawRoundRect(ledRect, 24f, 24f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = ledBorderCol
        canvas.drawRoundRect(ledRect, 24f, 24f, p)
        p.style = Paint.Style.FILL

        // Subtle unlit ghost digits behind glowing LED numbers
        val ghostPaint = monoPaint(0x16FFFFFF, letterSpacingEm = 0.04f)
        drawFittedCenteredText(canvas, "₹ 888.88", ledRect.centerX(), ledRect.centerY() + 6f, ledRect.width() - 56f, 164f, ghostPaint, 0, 0f)

        // Crisp Glowing Tabular LED Price Digits
        val priceText = String.format(Locale.ROOT, "₹ %.2f", priceInr)
        val glowPaint = monoPaint((ledGlowCol and 0x00FFFFFF) or 0x44000000, letterSpacingEm = 0.04f)
        drawFittedCenteredText(canvas, priceText, ledRect.centerX(), ledRect.centerY() + 6f, ledRect.width() - 56f, 168f, glowPaint, 0, 0f)
        val ledPaint = monoPaint(ledGlowCol, letterSpacingEm = 0.04f)
        drawFittedCenteredText(canvas, priceText, ledRect.centerX(), ledRect.centerY() + 6f, ledRect.width() - 56f, 164f, ledPaint, 0, 0f)
    }

    // =========================================================================
    // LIVE 3D DISPENSER LCD METER (1024 x 720) — MATCHES STATIC HOUSING EXACTLY
    // =========================================================================

    fun renderLivePumpMeter(outBmp: Bitmap, refuel: RefuelLiveState, pulseOn: Boolean) {
        val c = Canvas(outBmp)
        val bounds = RectF(0f, 0f, outBmp.width.toFloat(), outBmp.height.toFloat())
        val isFlowing = refuel.phase == RefuelPhase.FUELING_IN_PROGRESS
        drawDispenserLcdModule(
            canvas = c,
            bounds = bounds,
            saleStr = String.format(Locale.ROOT, "%07.2f", refuel.dispensedRupees),
            volStr = String.format(Locale.ROOT, "%06.2f", refuel.dispensedLiters),
            rateStr = String.format(Locale.ROOT, "%.2f", refuel.pricePerLiterInr),
            isFlowing = isFlowing,
            pulseOn = pulseOn,
            drawOuterBezel = false
        )
    }

    private fun drawDispenserLcdModule(
        canvas: Canvas,
        bounds: RectF,
        saleStr: String,
        volStr: String,
        rateStr: String,
        isFlowing: Boolean,
        pulseOn: Boolean,
        drawOuterBezel: Boolean
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = bounds.width()
        val h = bounds.height()
        val x0 = bounds.left
        val y0 = bounds.top

        // Deep Obsidian Recessed Housing
        p.shader = LinearGradient(x0, y0, x0, bounds.bottom, 0xFF0E1820.toInt(), 0xFF070D12.toInt(), Shader.TileMode.CLAMP)
        if (drawOuterBezel) {
            canvas.drawRoundRect(bounds, 22f, 22f, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 7f
            p.color = 0xFF475569.toInt()
            canvas.drawRoundRect(bounds, 22f, 22f, p)
            p.style = Paint.Style.FILL
        } else {
            canvas.drawRect(bounds, p)
            p.shader = null
        }

        val padX = w * 0.044f
        val rowH = h / 3f
        val slotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (w * 0.0055f).coerceAtLeast(3f)
            color = 0xFF1F7A5A.toInt()
        }
        val slotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF03080B.toInt()
        }
        val lblPaint = uiPaint(0xFFCBD5E1.toInt(), bold = true, letterSpacingEm = 0.06f).apply {
            textAlign = Paint.Align.LEFT
        }
        val digitPaint = monoPaint(0xFF5EEAD4.toInt(), letterSpacingEm = 0.04f).apply {
            textAlign = Paint.Align.RIGHT
        }
        val ghostPaint = monoPaint(0x165EEAD4, letterSpacingEm = 0.04f).apply {
            textAlign = Paint.Align.RIGHT
        }

        val rows = arrayOf(
            Triple("SALE AMOUNT (₹)", saleStr, "8888.88"),
            Triple("VOLUME (LITRES)", volStr, "888.88"),
            Triple("PRICE / LITRE (₹)", rateStr, "888.88")
        )
        val digitColors = intArrayOf(0xFF5EEAD4.toInt(), 0xFF34D399.toInt(), 0xFF6EE7B7.toInt())

        for (i in 0 until 3) {
            val ry = y0 + i * rowH
            val labelY = ry + rowH * 0.21f
            lblPaint.textSize = h * 0.052f
            canvas.drawText(rows[i].first, x0 + padX + 4f, baselineForCenterY(labelY, lblPaint), lblPaint)

            // Live flowing indicator pill on Row 1
            if (i == 0 && isFlowing) {
                val dotCol = if (pulseOn) 0xFF22C55E.toInt() else 0xFF15803D.toInt()
                p.color = dotCol
                canvas.drawCircle(bounds.right - padX - 18f, labelY, h * 0.022f, p)
                val liveLbl = uiPaint(0xFF86EFAC.toInt(), bold = true, letterSpacingEm = 0.08f).apply {
                    textAlign = Paint.Align.RIGHT
                    textSize = h * 0.040f
                }
                canvas.drawText("FLOWING", bounds.right - padX - 44f, baselineForCenterY(labelY, liveLbl), liveLbl)
            }

            val slot = RectF(x0 + padX, ry + rowH * 0.34f, bounds.right - padX, ry + rowH * 0.90f)
            canvas.drawRoundRect(slot, 14f, 14f, slotFill)
            canvas.drawRoundRect(slot, 14f, 14f, slotStroke)

            val digSize = if (i == 2) rowH * 0.44f else rowH * 0.50f
            val digX = slot.right - w * 0.032f
            val digCy = slot.centerY() + 2f

            ghostPaint.textSize = digSize
            canvas.drawText(rows[i].third, digX, baselineForCenterY(digCy, ghostPaint), ghostPaint)

            digitPaint.color = digitColors[i]
            digitPaint.textSize = digSize
            canvas.drawText(rows[i].second, digX, baselineForCenterY(digCy, digitPaint), digitPaint)
        }
    }

    // =========================================================================
    // CRISP VECTOR ARCHITECTURAL PICTOGRAMS & ARROWS
    // =========================================================================

    private fun drawVectorArrow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        pointingRight: Boolean,
        length: Float,
        thickness: Float
    ) {
        canvas.save()
        canvas.translate(cx, cy)
        if (!pointingRight) canvas.scale(-1f, 1f)

        val halfL = length * 0.5f
        val headL = length * 0.44f
        val halfShaft = thickness * 0.46f
        val headHalfW = thickness * 1.45f

        val path = Path().apply {
            moveTo(-halfL, -halfShaft)
            lineTo(halfL - headL, -halfShaft)
            lineTo(halfL - headL, -headHalfW)
            lineTo(halfL, 0f)
            lineTo(halfL - headL, headHalfW)
            lineTo(halfL - headL, halfShaft)
            lineTo(-halfL, halfShaft)
            close()
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
        canvas.save()
        canvas.translate(0f, 5f)
        canvas.drawPath(path, shadow)
        canvas.restore()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawPath(path, fill)
        canvas.restore()
    }

    private fun drawFuelDropEmblem(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = 0xFFFBBF24.toInt()
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33F97316 }
        canvas.drawCircle(cx, cy, radius, bg)
        canvas.drawCircle(cx, cy, radius, ring)

        val drop = Path().apply {
            moveTo(cx, cy - radius * 0.62f)
            cubicTo(cx + radius * 0.48f, cy - radius * 0.05f, cx + radius * 0.48f, cy + radius * 0.52f, cx, cy + radius * 0.55f)
            cubicTo(cx - radius * 0.48f, cy + radius * 0.52f, cx - radius * 0.48f, cy - radius * 0.05f, cx, cy - radius * 0.62f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx, cy - radius * 0.6f, cx, cy + radius * 0.6f, 0xFFFDE047.toInt(), 0xFFF97316.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(drop, fill)
    }

    private fun drawRestroomIcon(canvas: Canvas, cx: Float, cy: Float, isMale: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        // Head
        canvas.drawCircle(cx, cy - 52f, 17f, p)
        if (isMale) {
            // Shoulders & Torso
            canvas.drawRoundRect(RectF(cx - 26f, cy - 28f, cx + 26f, cy + 20f), 10f, 10f, p)
            // Legs
            canvas.drawRoundRect(RectF(cx - 20f, cy + 14f, cx - 4f, cy + 68f), 6f, 6f, p)
            canvas.drawRoundRect(RectF(cx + 4f, cy + 14f, cx + 20f, cy + 68f), 6f, 6f, p)
        } else {
            // Dress silhouette
            val dress = Path().apply {
                moveTo(cx - 16f, cy - 28f)
                lineTo(cx + 16f, cy - 28f)
                lineTo(cx + 34f, cy + 32f)
                lineTo(cx - 34f, cy + 32f)
                close()
            }
            canvas.drawPath(dress, p)
            // Legs
            canvas.drawRoundRect(RectF(cx - 15f, cy + 30f, cx - 3f, cy + 68f), 5f, 5f, p)
            canvas.drawRoundRect(RectF(cx + 3f, cy + 30f, cx + 15f, cy + 68f), 5f, 5f, p)
        }
    }

    private fun drawWarningTriangleIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val half = size * 0.5f
        val tri = Path().apply {
            moveTo(cx, cy - half * 0.92f)
            lineTo(cx + half, cy + half * 0.80f)
            lineTo(cx - half, cy + half * 0.80f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F172A.toInt() }
        canvas.drawPath(tri, fill)

        val inner = Path().apply {
            moveTo(cx, cy - half * 0.64f)
            lineTo(cx + half * 0.72f, cy + half * 0.62f)
            lineTo(cx - half * 0.72f, cy + half * 0.62f)
            close()
        }
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() }
        canvas.drawPath(inner, yellow)

        // Lightning Bolt
        val bolt = Path().apply {
            moveTo(cx + 6f, cy - 30f)
            lineTo(cx - 16f, cy + 6f)
            lineTo(cx + 2f, cy + 6f)
            lineTo(cx - 10f, cy + 38f)
            lineTo(cx + 18f, cy - 4f)
            lineTo(cx, cy - 4f)
            close()
        }
        canvas.drawPath(bolt, fill)
    }

    private fun drawAirGaugeIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = Color.WHITE
        }
        canvas.drawCircle(cx, cy, radius, bg)
        canvas.drawCircle(cx, cy, radius, ring)

        // Gauge ticks around upper arc
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFBAE6FD.toInt()
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0..6) {
            val deg = 145.0 + i * (250.0 / 6.0)
            val rad = deg * PI / 180.0
            val r0 = radius * 0.66f
            val r1 = radius * 0.84f
            canvas.drawLine(
                cx + (cos(rad) * r0).toFloat(),
                cy + (sin(rad) * r0).toFloat(),
                cx + (cos(rad) * r1).toFloat(),
                cy + (sin(rad) * r1).toFloat(),
                tickPaint
            )
        }
        // Needle pointing to 32 PSI
        val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFACC15.toInt()
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, cx + radius * 0.45f, cy - radius * 0.42f, needle)
        val hub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx, cy, 11f, hub)
    }

    private fun drawStoreBagIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFBBF24.toInt()
        val rect = RectF(cx - size * 0.72f, cy - size * 0.35f, cx + size * 0.72f, cy + size * 0.85f)
        canvas.drawRoundRect(rect, 12f, 12f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        p.color = 0xFFFDE68A.toInt()
        canvas.drawArc(
            RectF(cx - size * 0.36f, cy - size * 0.85f, cx + size * 0.36f, cy - size * 0.05f),
            180f, 180f, false, p
        )
    }

    private fun drawNoSignIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = 0xFFDC2626.toInt()
        }
        canvas.drawCircle(cx, cy, r, stroke)
        val d = r * 0.68f
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, stroke)
    }
}
