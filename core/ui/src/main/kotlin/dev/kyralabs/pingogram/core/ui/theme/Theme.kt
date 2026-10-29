package dev.kyralabs.pingogram.core.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette

/**
 * Root theme of every Pingogram screen (`docs/DESIGN_SYSTEM.md` §1): Material 3 Expressive with the
 * expressive motion scheme, Google Sans Flex, the expressive shape scale, [PingogramColors],
 * [PingogramSpacing] and [PingogramSizes].
 *
 * The typography follows the system's "Bold text" setting (Android 12+) without doubling it, and sets text a grade
 * heavier with high contrast; see [pingogramTypography].
 *
 * When a setting changes, every color role cross-fades to the new scheme on the motion scheme's default effects spring
 * ([animateColorScheme]); the first scheme shows at once, so the app never starts with a crossfade.
 *
 * Ripples are the Material 3 state layers (hover 8%, pressed 10%, dragged 16%); keyboard focus shows Material 3
 * Expressive's inset focus ring rather than a 10% focus layer (`docs/DESIGN_SYSTEM.md` §2.3).
 * Compose scales every animation, springs included, by the system animator duration scale, and a scale of 0 makes them
 * finish immediately; shape morphs and container transforms also step aside then (`isReducedMotion`).
 *
 * @param dynamicColor Material You colors on Android 12+; the colors of [palette] otherwise or when false.
 * @param palette the color theme used without the wallpaper's colors (`docs/DESIGN_SYSTEM.md` §2.1).
 * @param contrast used where the system has no contrast setting (before Android 14); from Android 14 the system's
 *   contrast level replaces it ([systemContrastOrNull]), for the colors and the typography alike.
 * @param amoled true-black surfaces in dark theme.
 * @param chat how conversations look ([ChatStyle], `MaterialTheme.chatStyle`): message text size, bubble corners and
 *   wallpaper.
 */
@Composable
fun PingogramTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    palette: ThemePalette = ThemePalette.Pingogram,
    contrast: Contrast = Contrast.Standard,
    amoled: Boolean = false,
    chat: ChatAppearance = ChatAppearance(),
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val effectiveContrast = systemContrastOrNull() ?: contrast
    val colorScheme =
        animateColorScheme(
            target = rememberPingogramColorScheme(dark, dynamicColor, palette, effectiveContrast, amoled),
            animationSpec = ExpressiveMotionScheme.defaultEffectsSpec(),
        )
    val pingogramColors = remember(colorScheme, dark) { pingogramColors(colorScheme, dark) }
    val settings =
        remember(dynamicColor, palette, contrast, amoled, dark, chat) {
            ThemeSettings(dynamicColor, palette, contrast, amoled, dark && amoled, chat)
        }
    val fontWeightAdjustment = LocalConfiguration.current.fontWeightAdjustmentOrZero()
    val messageText = pingogramMessageText(chat.textScale, fontWeightAdjustment, effectiveContrast)
    val conversationStyle =
        remember(chat, messageText, colorScheme) { chatStyle(chat, messageText, PingogramShapes, colorScheme) }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = ExpressiveMotionScheme,
        shapes = PingogramShapes,
        typography = pingogramTypography(fontWeightAdjustment, effectiveContrast),
    ) {
        CompositionLocalProvider(
            LocalPingogramColors provides pingogramColors,
            LocalPingogramSpacing provides DefaultSpacing,
            LocalPingogramSizes provides DefaultSizes,
            LocalRippleThemeConfiguration provides RippleDefaults.InsetFocusRingThemeConfiguration,
            LocalThemeSettings provides settings,
            LocalChatStyle provides conversationStyle,
            content = content,
        )
    }
}

/**
 * [content] in the dark theme of the enclosing [PingogramTheme], with its colors, contrast and AMOLED settings,
 * whatever the theme mode: for immersive media, such as the media viewer, which shows photos and videos on black in
 * light theme too, so the media is what stands out, and its chrome and components are legible on the `scrim` behind
 * them.
 */
@Composable
fun PingogramDarkTheme(content: @Composable () -> Unit) {
    val settings = LocalThemeSettings.current
    PingogramTheme(
        themeMode = ThemeMode.Dark,
        dynamicColor = settings.dynamicColor,
        palette = settings.palette,
        contrast = settings.contrast,
        amoled = settings.amoled,
        chat = settings.chat,
        content = content,
    )
}

/**
 * What [PingogramTheme] was given besides its theme mode, for [PingogramDarkTheme], and whether its surfaces are true
 * black ([amoledBlack]: dark with AMOLED black on).
 */
private data class ThemeSettings(
    val dynamicColor: Boolean,
    val palette: ThemePalette,
    val contrast: Contrast,
    val amoled: Boolean,
    val amoledBlack: Boolean,
    val chat: ChatAppearance,
)

private val LocalThemeSettings =
    staticCompositionLocalOf {
        ThemeSettings(
            dynamicColor = true,
            palette = ThemePalette.Pingogram,
            contrast = Contrast.Standard,
            amoled = false,
            amoledBlack = false,
            chat = ChatAppearance(),
        )
    }

/**
 * Whether the enclosing [PingogramTheme] shows true-black surfaces: dark, with AMOLED black on (`docs/DESIGN_SYSTEM.md`
 * §2.1). For the few places whose roles differ then, e.g. settings pages, which turn black and move their rows onto
 * `surfaceContainer` (§2.2).
 */
@Composable
@ReadOnlyComposable
fun isAmoledBlack(): Boolean = LocalThemeSettings.current.amoledBlack

/** Whether this mode resolves to the dark theme right now. */
@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

/** The "Bold text" weight adjustment of Android 12+ (300 while it is on), or 0. */
private fun Configuration.fontWeightAdjustmentOrZero(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        fontWeightAdjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED
    ) {
        fontWeightAdjustment
    } else {
        0
    }

private val ExpressiveMotionScheme = MotionScheme.expressive()
private val DefaultSpacing = PingogramSpacing()
private val DefaultSizes = PingogramSizes()
