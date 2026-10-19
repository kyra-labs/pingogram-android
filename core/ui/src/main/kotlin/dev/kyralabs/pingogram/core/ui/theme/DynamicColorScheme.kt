package dev.kyralabs.pingogram.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The platform's Material You scheme ([scheme]); [followsContrast] when it carries the system's contrast level, which
 * only Android 14's color roles do.
 */
internal class DynamicColors(
    val scheme: ColorScheme,
    val followsContrast: Boolean,
)

/**
 * Material You from the wallpaper (`docs/DESIGN_SYSTEM.md` §2.1). On Android 14+ Compose reads the platform's color
 * roles (`system_primary_light`…), which follow the system contrast. Some systems (Samsung's One UI among them) fill
 * only the tonal palettes of Android 12 (`system_accent1_*`…) from the wallpaper and leave those roles at AOSP's stock
 * blue, so every app that reads them shows blue. When the roles do not match the palettes, the scheme comes from the
 * palettes, as on Android 12 and 13.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun systemDynamicColors(
    context: Context,
    dark: Boolean,
): DynamicColors {
    val fromPalettes = paletteColorScheme(SystemPalettes.read(context), dark)
    val fromRoles =
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> null
            dark -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    return chooseDynamicColors(fromRoles, fromPalettes)
}

/**
 * The scheme from the platform's color roles ([fromRoles], Android 14+, null before) when its primary has the hue of
 * the tonal palettes' ([fromPalettes]), which then carries the system contrast; otherwise the palettes' scheme.
 */
internal fun chooseDynamicColors(
    fromRoles: ColorScheme?,
    fromPalettes: ColorScheme,
): DynamicColors =
    if (fromRoles != null && sameHue(fromRoles.primary, fromPalettes.primary)) {
        DynamicColors(fromRoles, followsContrast = true)
    } else {
        DynamicColors(fromPalettes, followsContrast = false)
    }

/**
 * Whether two colors share a hue, within [HUE_TOLERANCE_DEGREES] (in Oklab), or are both near gray, e.g. a monochrome
 * system theme. The system contrast level changes a role's tone, not its hue.
 */
internal fun sameHue(
    a: Color,
    b: Color,
): Boolean {
    val (hueA, chromaA) = a.hueAndChroma()
    val (hueB, chromaB) = b.hueAndChroma()
    val grayA = chromaA < GRAY_CHROMA
    val grayB = chromaB < GRAY_CHROMA
    val difference = abs(hueA - hueB) % FULL_TURN
    return when {
        grayA || grayB -> grayA && grayB
        else -> minOf(difference, FULL_TURN - difference) <= HUE_TOLERANCE_DEGREES
    }
}

private fun Color.hueAndChroma(): Pair<Float, Float> {
    val lab = convert(ColorSpaces.Oklab)
    val a = lab.component2()
    val b = lab.component3()
    return Math.toDegrees(atan2(b, a).toDouble()).toFloat() to hypot(a, b)
}

/**
 * The five tonal palettes of Material You (primary, secondary, tertiary, neutral, neutral variant) by tone, as the
 * platform publishes them since Android 12: 13 tones each. Other tones, which the surface roles need, take the
 * nearest published tone's color at that lightness, as Compose does; never black's or white's, which carry no hue.
 */
internal class SystemPalettes(
    private val accent1: Map<Int, Color>,
    private val accent2: Map<Int, Color>,
    private val accent3: Map<Int, Color>,
    private val neutral2: Map<Int, Color>,
) {
    fun primary(tone: Int) = accent1.tone(tone)

    fun secondary(tone: Int) = accent2.tone(tone)

    fun tertiary(tone: Int) = accent3.tone(tone)

    fun neutralVariant(tone: Int) = neutral2.tone(tone)

    private fun Map<Int, Color>.tone(tone: Int): Color =
        get(tone) ?: getValue(keys.filter { it in 1..99 }.minBy { abs(it - tone) }).withLightness(tone.toFloat())

    companion object {
        /** The published tones, lightest first, in the order of each palette's resources (`_0` … `_1000`). */
        val TONES = listOf(100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)

        @RequiresApi(Build.VERSION_CODES.S)
        fun read(context: Context): SystemPalettes {
            fun palette(ids: List<Int>) = TONES.zip(ids) { tone, id -> tone to Color(context.getColor(id)) }.toMap()
            return with(PaletteResources) {
                SystemPalettes(palette(ACCENT1), palette(ACCENT2), palette(ACCENT3), palette(NEUTRAL2))
            }
        }
    }
}

/** This color at CIE lightness [lightness] (the tone of Material's HCT), keeping its a and b; Compose's way. */
internal fun Color.withLightness(lightness: Float): Color {
    val lab = convert(ColorSpaces.CieLab)
    return Color(
        lightness,
        lab.component2(),
        lab.component3(),
        colorSpace = ColorSpaces.CieLab,
    ).convert(ColorSpaces.Srgb)
}

/**
 * The roles from the tonal palettes, as Compose maps them on Android 12 and 13 (`dynamicLightColorScheme31` and
 * `dynamicDarkColorScheme31`, internal to material3): surfaces from the neutral variant palette.
 */
@Suppress("LongMethod") // One line per color role.
internal fun paletteColorScheme(
    palettes: SystemPalettes,
    dark: Boolean,
): ColorScheme =
    with(palettes) {
        if (dark) {
            darkColorScheme(
                primary = primary(80),
                onPrimary = primary(20),
                primaryContainer = primary(30),
                onPrimaryContainer = primary(90),
                inversePrimary = primary(40),
                secondary = secondary(80),
                onSecondary = secondary(20),
                secondaryContainer = secondary(30),
                onSecondaryContainer = secondary(90),
                tertiary = tertiary(80),
                onTertiary = tertiary(20),
                tertiaryContainer = tertiary(30),
                onTertiaryContainer = tertiary(90),
                background = neutralVariant(6),
                onBackground = neutralVariant(90),
                surface = neutralVariant(6),
                onSurface = neutralVariant(90),
                surfaceVariant = neutralVariant(30),
                onSurfaceVariant = neutralVariant(80),
                surfaceTint = primary(80),
                inverseSurface = neutralVariant(90),
                inverseOnSurface = neutralVariant(20),
                outline = neutralVariant(60),
                outlineVariant = neutralVariant(30),
                scrim = neutralVariant(0),
                surfaceBright = neutralVariant(24),
                surfaceContainer = neutralVariant(12),
                surfaceContainerHigh = neutralVariant(17),
                surfaceContainerHighest = neutralVariant(22),
                surfaceContainerLow = neutralVariant(10),
                surfaceContainerLowest = neutralVariant(4),
                surfaceDim = neutralVariant(6),
                primaryFixed = primary(90),
                primaryFixedDim = primary(80),
                onPrimaryFixed = primary(10),
                onPrimaryFixedVariant = primary(30),
                secondaryFixed = secondary(90),
                secondaryFixedDim = secondary(80),
                onSecondaryFixed = secondary(10),
                onSecondaryFixedVariant = secondary(30),
                tertiaryFixed = tertiary(90),
                tertiaryFixedDim = tertiary(80),
                onTertiaryFixed = tertiary(10),
                onTertiaryFixedVariant = tertiary(30),
            )
        } else {
            lightColorScheme(
                primary = primary(40),
                onPrimary = primary(100),
                primaryContainer = primary(90),
                onPrimaryContainer = primary(10),
                inversePrimary = primary(80),
                secondary = secondary(40),
                onSecondary = secondary(100),
                secondaryContainer = secondary(90),
                onSecondaryContainer = secondary(10),
                tertiary = tertiary(40),
                onTertiary = tertiary(100),
                tertiaryContainer = tertiary(90),
                onTertiaryContainer = tertiary(10),
                background = neutralVariant(98),
                onBackground = neutralVariant(10),
                surface = neutralVariant(98),
                onSurface = neutralVariant(10),
                surfaceVariant = neutralVariant(90),
                onSurfaceVariant = neutralVariant(30),
                surfaceTint = primary(40),
                inverseSurface = neutralVariant(20),
                inverseOnSurface = neutralVariant(95),
                outline = neutralVariant(50),
                outlineVariant = neutralVariant(80),
                scrim = neutralVariant(0),
                surfaceBright = neutralVariant(98),
                surfaceContainer = neutralVariant(94),
                surfaceContainerHigh = neutralVariant(92),
                surfaceContainerHighest = neutralVariant(90),
                surfaceContainerLow = neutralVariant(96),
                surfaceContainerLowest = neutralVariant(100),
                surfaceDim = neutralVariant(87),
                primaryFixed = primary(90),
                primaryFixedDim = primary(80),
                onPrimaryFixed = primary(10),
                onPrimaryFixedVariant = primary(30),
                secondaryFixed = secondary(90),
                secondaryFixedDim = secondary(80),
                onSecondaryFixed = secondary(10),
                onSecondaryFixedVariant = secondary(30),
                tertiaryFixed = tertiary(90),
                tertiaryFixedDim = tertiary(80),
                onTertiaryFixed = tertiary(10),
                onTertiaryFixedVariant = tertiary(30),
            )
        }
    }

/** Hue difference within which a role and its palette count as one hue: contrast moves tones, not hues. */
private const val HUE_TOLERANCE_DEGREES = 30f

/** Oklab chroma below which a color counts as gray (its hue means nothing). */
private const val GRAY_CHROMA = 0.02f

private const val FULL_TURN = 360f

/** The tonal palettes' color resources, `_0` (tone 100) to `_1000` (tone 0); they exist from Android 12. */
@RequiresApi(Build.VERSION_CODES.S)
private object PaletteResources {
    val ACCENT1 =
        listOf(
            android.R.color.system_accent1_0,
            android.R.color.system_accent1_10,
            android.R.color.system_accent1_50,
            android.R.color.system_accent1_100,
            android.R.color.system_accent1_200,
            android.R.color.system_accent1_300,
            android.R.color.system_accent1_400,
            android.R.color.system_accent1_500,
            android.R.color.system_accent1_600,
            android.R.color.system_accent1_700,
            android.R.color.system_accent1_800,
            android.R.color.system_accent1_900,
            android.R.color.system_accent1_1000,
        )

    val ACCENT2 =
        listOf(
            android.R.color.system_accent2_0,
            android.R.color.system_accent2_10,
            android.R.color.system_accent2_50,
            android.R.color.system_accent2_100,
            android.R.color.system_accent2_200,
            android.R.color.system_accent2_300,
            android.R.color.system_accent2_400,
            android.R.color.system_accent2_500,
            android.R.color.system_accent2_600,
            android.R.color.system_accent2_700,
            android.R.color.system_accent2_800,
            android.R.color.system_accent2_900,
            android.R.color.system_accent2_1000,
        )

    val ACCENT3 =
        listOf(
            android.R.color.system_accent3_0,
            android.R.color.system_accent3_10,
            android.R.color.system_accent3_50,
            android.R.color.system_accent3_100,
            android.R.color.system_accent3_200,
            android.R.color.system_accent3_300,
            android.R.color.system_accent3_400,
            android.R.color.system_accent3_500,
            android.R.color.system_accent3_600,
            android.R.color.system_accent3_700,
            android.R.color.system_accent3_800,
            android.R.color.system_accent3_900,
            android.R.color.system_accent3_1000,
        )

    val NEUTRAL2 =
        listOf(
            android.R.color.system_neutral2_0,
            android.R.color.system_neutral2_10,
            android.R.color.system_neutral2_50,
            android.R.color.system_neutral2_100,
            android.R.color.system_neutral2_200,
            android.R.color.system_neutral2_300,
            android.R.color.system_neutral2_400,
            android.R.color.system_neutral2_500,
            android.R.color.system_neutral2_600,
            android.R.color.system_neutral2_700,
            android.R.color.system_neutral2_800,
            android.R.color.system_neutral2_900,
            android.R.color.system_neutral2_1000,
        )
}
