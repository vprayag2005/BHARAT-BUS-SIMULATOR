package com.bharatbussim.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.bharatbussim.game.ui.theme.PremiumFonts

/**
 * Procedural high-resolution architectural & signage texture generators for Zone 4:
 * - SH 75 Thrissur Kole Wetlands PWD kilometer milestones
 * - Enamavu Sluice Regulator Bridge PWD signage & industrial shutter plates
 * - Muthuvara Animated Railway Level Crossing (LC-14) 450mm red STOP discs & Indian Railways gatekeeper cabin board
 * - Guruvayur Sree Krishna Temple East Nada traditional Kerala brass/terracotta gopuram tiles, ceremonial plaque & KSRTC/Municipal Bus Terminal bay boards
 * - Bharathappuzha (Nila River) PWD bridge signage & Historic 1902 Cochin Bridge riveted steel lattice & heritage plaque
 */
internal object Zone4ArchitecturalTextures {

    /**
     * Authentic Kerala PWD Highway Kilometer Post with green curved top cap:
     * "SH 75 | കാഞ്ഞാണി 4 | തൃശ്ശൂർ 8"
     */
    fun createSh75KilometerPostSpec(
        cacheKey: String,
        dest1Ml: String,
        dest2Ml: String
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 256,
        bmpHeight = 384,
        isDoubleSided = true,
        renderBitmap = {
            val w = 256f
            val h = 384f
            val bmp = Bitmap.createBitmap(256, 384, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // Weathered white concrete stone body
            c.drawColor(0xFFF3F4F6.toInt())

            // Kerala SH Green curved top crown (upper 34%)
            p.color = 0xFF047857.toInt()
            c.drawRect(0f, 0f, w, h * 0.34f, p)
            p.color = 0xFF065F46.toInt()
            c.drawRect(0f, h * 0.31f, w, h * 0.35f, p)

            // Crisp border outline
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = 0xFF1F2937.toInt()
            c.drawRect(4f, 4f, w - 4f, h - 4f, p)

            // "SH 75" bold white header in green crown
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.color = Color.WHITE
            p.typeface = PremiumFonts.displayTypefaceFor("SH 75", bold = true)
            p.textSize = 54f
            c.drawText("SH 75", w * 0.5f, h * 0.23f, p)

            // Two black destination rows on white lower face
            p.color = 0xFF111827.toInt()
            p.typeface = PremiumFonts.uiTypefaceFor(dest1Ml, bold = true)
            p.textSize = 36f
            val tw1 = p.measureText(dest1Ml)
            if (tw1 > w - 24f) p.textSize *= (w - 24f) / tw1
            c.drawText(dest1Ml, w * 0.5f, h * 0.56f, p)

            p.strokeWidth = 3f
            p.color = 0xFF9CA3AF.toInt()
            c.drawLine(24f, h * 0.66f, w - 24f, h * 0.66f, p)

            p.color = 0xFF111827.toInt()
            p.typeface = PremiumFonts.uiTypefaceFor(dest2Ml, bold = true)
            p.textSize = 36f
            val tw2 = p.measureText(dest2Ml)
            if (tw2 > w - 24f) p.textSize *= (w - 24f) / tw2
            c.drawText(dest2Ml, w * 0.5f, h * 0.84f, p)

            bmp
        }
    )

    /**
     * Enamavu Sluice Regulator Bridge industrial riveted steel shutter plate texture.
     */
    fun createSluiceGateShutterSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE4_ENAMAVU_SLUICE_SHUTTER_V1",
        bmpWidth = 256,
        bmpHeight = 256,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, 256f,
                intArrayOf(0xFF475569.toInt(), 0xFF334155.toInt(), 0xFF1E293B.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, 256f, 256f, p)
            p.shader = null

            // Heavy horizontal steel I-beam stiffener ribs + bolts
            for (row in 0..4) {
                val y = 26f + row * 50f
                p.color = 0xFF64748B.toInt()
                c.drawRect(6f, y - 5f, 250f, y + 2f, p)
                p.color = 0xFF0F172A.toInt()
                c.drawRect(6f, y + 2f, 250f, y + 8f, p)
                p.color = 0xFF94A3B8.toInt()
                for (bx in 20..236 step 36) {
                    c.drawCircle(bx.toFloat(), y - 1f, 3.2f, p)
                }
            }
            // Subtle rust-water stain near bottom waterline
            p.color = 0x447C2D12
            c.drawRect(0f, 195f, 256f, 256f, p)
            // Outer steel frame
            p.style = Paint.Style.STROKE
            p.strokeWidth = 10f
            p.color = 0xFF1E293B.toInt()
            c.drawRect(5f, 5f, 251f, 251f, p)
            bmp
        }
    )

    /**
     * Muthuvara LC Gate suspended 450mm circular red STOP metal disc with white reflective border.
     */
    fun createMuthuvaraStopDiscSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE4_MUTHUVARA_LC_STOP_DISC_V1",
        bmpWidth = 256,
        bmpHeight = 256,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.TRANSPARENT)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // Outer white retroreflective rim
            p.color = Color.WHITE
            c.drawCircle(128f, 128f, 120f, p)
            // Deep Indian Railways signal red face
            p.color = 0xFFDC2626.toInt()
            c.drawCircle(128f, 128f, 108f, p)
            // Inner crisp white ring
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.WHITE
            c.drawCircle(128f, 128f, 96f, p)
            // Bold white STOP text
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.typeface = PremiumFonts.displayTypefaceFor("STOP", bold = true)
            p.textSize = 68f
            p.color = Color.WHITE
            c.drawText("STOP", 128f, 152f, p)
            bmp
        }
    )

    /**
     * Indian Railways Southern Railway Yellow-Ochre Gatekeeper Cabin Nameplate:
     * "SOUTHERN RAILWAY • LC-14 MUTHUVARA"
     */
    fun createMuthuvaraCabinBoardSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE4_MUTHUVARA_CABIN_BOARD_V1",
        bmpWidth = 512,
        bmpHeight = 160,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(512, 160, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFFFACC15.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 10f
            p.color = 0xFF111827.toInt()
            c.drawRect(8f, 8f, 504f, 152f, p)

            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.color = 0xFF111827.toInt()
            p.typeface = PremiumFonts.displayTypefaceFor("SR LC-14 MUTHUVARA", bold = true)
            p.textSize = 42f
            c.drawText("SR • LC-14 • MUTHUVARA", 256f, 70f, p)

            p.typeface = PremiumFonts.uiTypefaceFor("മുതുവറ റെയിൽവേ ഗേറ്റ് • GURUVAYUR LINE", bold = true)
            p.color = 0xFF991B1B.toInt()
            p.textSize = 27f
            c.drawText("മുതുവറ റെയിൽവേ ഗേറ്റ് • GURUVAYUR LINE", 256f, 124f, p)
            bmp
        }
    )

    /**
     * Guruvayur Sree Krishna Temple East Nada traditional Kerala copper-and-terracotta temple roof tiles.
     */
    fun createGuruvayurCopperRoofSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE4_GURUVAYUR_COPPER_ROOF_V1",
        bmpWidth = 512,
        bmpHeight = 256,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, 256f,
                intArrayOf(0xFF9A3412.toInt(), 0xFFB45309.toInt(), 0xFF78350F.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, 512f, 256f, p)
            p.shader = null

            // Vertical standing copper sheet ribs + golden specular catchlights
            var x = 0f
            while (x < 512f) {
                p.color = 0x55FDE68A
                c.drawRect(x, 0f, x + 5f, 256f, p)
                p.color = 0x66451A03
                c.drawRect(x + 12f, 0f, x + 16f, 256f, p)
                x += 22f
            }
            // Horizontal overlapping copper plate seams
            for (y in 44..220 step 44) {
                p.color = 0x773B1306
                c.drawRect(0f, y.toFloat(), 512f, y + 4f, p)
            }
            // Ornate golden brass eave border strip along the bottom edge
            p.color = 0xFFEAB308.toInt()
            c.drawRect(0f, 234f, 512f, 256f, p)
            p.color = 0xFF78350F.toInt()
            for (bx in 10..500 step 24) {
                c.drawCircle(bx.toFloat(), 245f, 4f, p)
            }
            bmp
        }
    )

    /**
     * Guruvayur Sree Krishna Temple East Nada ceremonial archway plaque.
     */
    fun createGuruvayurEastNadaPlaqueSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE4_GURUVAYUR_EAST_NADA_PLAQUE_V1",
        bmpWidth = 1024,
        bmpHeight = 192,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(1024, 192, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, 192f,
                intArrayOf(0xFF7F1D1D.toInt(), 0xFF450A0A.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, 1024f, 192f, p)
            p.shader = null

            // Traditional double golden border
            p.style = Paint.Style.STROKE
            p.strokeWidth = 7f
            p.color = 0xFFFACC15.toInt()
            c.drawRect(10f, 10f, 1014f, 182f, p)
            p.strokeWidth = 3f
            c.drawRect(22f, 22f, 1002f, 170f, p)

            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.color = 0xFFFDE047.toInt()
            p.typeface = PremiumFonts.displayTypefaceFor("ഗുരുവായൂർ ശ്രീകൃഷ്ണ ക്ഷേത്രം", bold = true)
            p.textSize = 52f
            c.drawText("ഗുരുവായൂർ ശ്രീകൃഷ്ണ ക്ഷേത്രം • കിഴക്കേ നട", 512f, 92f, p)

            p.color = Color.WHITE
            p.typeface = PremiumFonts.uiTypefaceFor("GURUVAYUR SREE KRISHNA TEMPLE", bold = true)
            p.textSize = 34f
            c.drawText("GURUVAYUR SREE KRISHNA TEMPLE • EAST NADA", 512f, 148f, p)
            bmp
        }
    )

    /**
     * Guruvayur Bus Terminal (KSRTC & Private Pilgrimage Stand) main canopy fascia signboard.
     */
    fun createGuruvayurTerminalFasciaSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE4_GURUVAYUR_TERMINAL_FASCIA_V1",
        bmpWidth = 1024,
        bmpHeight = 192,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(1024, 192, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(
                0f, 0f, 0f, 192f,
                intArrayOf(0xFF1E3A8A.toInt(), 0xFF172554.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, 1024f, 192f, p)
            p.shader = null

            p.style = Paint.Style.STROKE
            p.strokeWidth = 7f
            p.color = 0xFFFACC15.toInt()
            c.drawRect(10f, 10f, 1014f, 182f, p)

            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.color = Color.WHITE
            p.typeface = PremiumFonts.displayTypefaceFor("GURUVAYUR BUS TERMINAL", bold = true)
            p.textSize = 50f
            c.drawText("ഗുരുവായൂർ ബസ് ടെർമിനൽ • GURUVAYUR BUS TERMINAL", 512f, 88f, p)

            p.color = 0xFFFEF08A.toInt()
            p.typeface = PremiumFonts.uiTypefaceFor("KSRTC & PRIVATE PILGRIMAGE BUSES", bold = true)
            p.textSize = 31f
            c.drawText("KSRTC & PRIVATE SAWTOOTH BAYS • THRISSUR • KOZHIKODE • ERNAKULAM", 512f, 148f, p)
            bmp
        }
    )

    /**
     * White-on-blue Kerala bus bay enamel hanging board.
     */
    fun createBusBaySignSpec(
        cacheKey: String,
        bayNumber: String,
        destinations: String
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 384,
        bmpHeight = 128,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(384, 128, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFF1D4ED8.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.WHITE
            c.drawRect(6f, 6f, 378f, 122f, p)

            p.style = Paint.Style.FILL
            p.color = 0xFFFACC15.toInt()
            c.drawRect(10f, 10f, 118f, 118f, p)

            p.textAlign = Paint.Align.CENTER
            p.color = 0xFF111827.toInt()
            p.typeface = PremiumFonts.displayTypefaceFor(bayNumber, bold = true)
            p.textSize = 38f
            c.drawText(bayNumber, 64f, 76f, p)

            p.color = Color.WHITE
            p.typeface = PremiumFonts.uiTypefaceFor(destinations, bold = true)
            p.textSize = 26f
            val tw = p.measureText(destinations)
            if (tw > 240f) p.textSize *= 240f / tw
            c.drawText(destinations, 248f, 74f, p)
            bmp
        }
    )

    /**
     * Historic 1902 Cochin Bridge weathered British-era red-oxide & black riveted steel lattice plate.
     */
    fun createCochinBridge1902PlaqueSpec(): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = "ZONE4_COCHIN_BRIDGE_1902_PLAQUE_V1",
        bmpWidth = 640,
        bmpHeight = 192,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(640, 192, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFF292524.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 8f
            p.color = 0xFFD97706.toInt()
            c.drawRoundRect(RectF(10f, 10f, 630f, 182f), 14f, 14f, p)

            // Corner cast-iron rivets
            p.style = Paint.Style.FILL
            p.color = 0xFFB45309.toInt()
            for (rx in floatArrayOf(28f, 612f)) {
                for (ry in floatArrayOf(28f, 164f)) {
                    c.drawCircle(rx, ry, 7f, p)
                }
            }

            p.textAlign = Paint.Align.CENTER
            p.color = 0xFFFDE68A.toInt()
            p.typeface = PremiumFonts.displayTypefaceFor("COCHIN BRIDGE 1902", bold = true)
            p.textSize = 46f
            c.drawText("COCHIN BRIDGE • EST. 1902", 320f, 84f, p)

            p.color = 0xFFE7E5E4.toInt()
            p.typeface = PremiumFonts.uiTypefaceFor("BHARATHAPPUZHA HERITAGE TRUSS", bold = true)
            p.textSize = 27f
            c.drawText("കൊച്ചിൻ പാലം • CHERUTHURUTHY – SHORANUR", 320f, 142f, p)
            bmp
        }
    )

    /**
     * Blue Kerala PWD River / Bridge Portal Signboard (Enamavu Regulator & Bharathappuzha Nila River).
     */
    fun createZone4PwdBridgeSignSpec(
        cacheKey: String,
        titleText: String,
        subText: String,
        bgColorInt: Int = 0xFF0369A1.toInt()
    ): SignboardQuadSpec = SignboardQuadSpec(
        cacheKey = cacheKey,
        bmpWidth = 1024,
        bmpHeight = 192,
        isDoubleSided = true,
        renderBitmap = {
            val bmp = Bitmap.createBitmap(1024, 192, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(bgColorInt)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 8f
            p.color = Color.WHITE
            c.drawRect(10f, 10f, 1014f, 182f, p)

            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.color = Color.WHITE
            p.typeface = PremiumFonts.displayTypefaceFor(titleText, bold = true)
            p.textSize = 52f
            val tw = p.measureText(titleText)
            if (tw > 950f) p.textSize *= 950f / tw
            c.drawText(titleText, 512f, 90f, p)

            p.color = 0xFFFEF08A.toInt()
            p.typeface = PremiumFonts.uiTypefaceFor(subText, bold = true)
            p.textSize = 33f
            val sw = p.measureText(subText)
            if (sw > 950f) p.textSize *= 950f / sw
            c.drawText(subText, 512f, 150f, p)
            bmp
        }
    )
}
