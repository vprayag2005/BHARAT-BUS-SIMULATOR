package com.bharatbussim.game.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.bharatbussim.game.R

val HudBgDark = Color(0xFF0C1018)
val HudPanelSurface = Color(0xE6161D29)
val HudAmber = Color(0xFFF59E0B)
val HudAmberBright = Color(0xFFFBBF24)
val HudCyan = Color(0xFF38BDF8)
val HudEmerald = Color(0xFF34D399)
val HudRed = Color(0xFFEF4444)
val HudTextPrimary = Color(0xFFF8FAFC)
val HudTextMuted = Color(0xFF94A3B8)

val OutfitDisplayFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal)
)

val JakartaUiFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

val SimTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OutfitDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.4.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OutfitDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.4.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    titleSmall = TextStyle(
        fontFamily = JakartaUiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = JakartaUiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = JakartaUiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = JakartaUiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = OutfitDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JakartaUiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.35.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JakartaUiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.4.sp
    )
)

/**
 * Shared Android [Typeface] registry backed by local bundled Google Fonts in `res/font/`,
 * used by 3D OpenGL signboard bitmaps, Sakthan terminal architectural lettering, and cockpit gauges.
 */
object PremiumFonts {
    @Volatile
    private var initialized = false

    @Volatile
    private var outfitRegular: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    @Volatile
    private var outfitBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    @Volatile
    private var jakartaRegular: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    @Volatile
    private var jakartaBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    @Volatile
    private var monoRegular: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

    @Volatile
    private var monoBold: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    @Volatile
    private var oswaldBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private val malayalamBold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val malayalamRegular: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            ResourcesCompat.getFont(appCtx, R.font.outfit)?.let { base ->
                outfitRegular = base
                outfitBold = Typeface.create(base, Typeface.BOLD)
            }
            ResourcesCompat.getFont(appCtx, R.font.plus_jakarta_sans)?.let { base ->
                jakartaRegular = base
                jakartaBold = Typeface.create(base, Typeface.BOLD)
            }
            ResourcesCompat.getFont(appCtx, R.font.jetbrains_mono)?.let { base ->
                monoRegular = base
                monoBold = Typeface.create(base, Typeface.BOLD)
            }
            ResourcesCompat.getFont(appCtx, R.font.oswald)?.let { base ->
                oswaldBold = Typeface.create(base, Typeface.BOLD)
            }
            initialized = true
        }
    }

    fun containsMalayalam(text: String): Boolean {
        for (i in text.indices) {
            val ch = text[i]
            if (ch in '\u0D00'..'\u0D7F') return true
        }
        return false
    }

    fun displayTypefaceFor(text: String, bold: Boolean = true): Typeface =
        if (containsMalayalam(text)) {
            if (bold) malayalamBold else malayalamRegular
        } else {
            if (bold) outfitBold else outfitRegular
        }

    fun stationDisplayTypeface(): Typeface = oswaldBold

    fun uiTypefaceFor(text: String, bold: Boolean = true): Typeface =
        if (containsMalayalam(text)) {
            if (bold) malayalamBold else malayalamRegular
        } else {
            if (bold) jakartaBold else jakartaRegular
        }

    fun monoTypeface(bold: Boolean = true): Typeface =
        if (bold) monoBold else monoRegular
}

private val SimColorScheme = darkColorScheme(
    primary = HudAmber,
    onPrimary = Color(0xFF0F172A),
    secondary = HudCyan,
    onSecondary = Color(0xFF0F172A),
    tertiary = HudEmerald,
    background = HudBgDark,
    surface = HudPanelSurface,
    onBackground = HudTextPrimary,
    onSurface = HudTextPrimary,
    error = HudRed
)

@Composable
fun BharatBusSimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SimColorScheme,
        typography = SimTypography,
        content = content
    )
}

