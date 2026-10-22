package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.BubbleCorners
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Chat appearance page (`REQUIREMENTS.md` FR-S1): the defaults; larger text, less round corners and a gradient
 * wallpaper; the smallest text and a tint in another color theme, dark; the largest text at twice the font size; on an
 * expanded window; and AMOLED black, high contrast and right to left (`docs/DESIGN_SYSTEM.md` §12). The preview follows
 * the theme's chat style, which the tests set as the app would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatAppearanceScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun defaultLight() = capture("defaultLight", ThemeMode.Light, ChatAppearance())

    @Test
    fun customLight() = capture("customLight", ThemeMode.Light, Custom)

    @Test
    fun tintDark() =
        capture(
            "tintDark",
            ThemeMode.Dark,
            ChatAppearance(
                textScale = ChatAppearance.MIN_TEXT_SCALE,
                bubbleCorners = BubbleCorners.ExtraLargeIncreased,
                wallpaper = ChatWallpaper.Tertiary,
            ),
            ThemePalette.Teal,
        )

    @Test
    // Tall enough to show the whole page at twice the font size.
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = 2f)
    fun largestTextAtTwiceTheFontSize() =
        capture(
            "largestTextAtTwiceTheFontSize",
            ThemeMode.Light,
            ChatAppearance(textScale = ChatAppearance.MAX_TEXT_SCALE, wallpaper = ChatWallpaper.Neutral),
        )

    @Test
    @Config(qualifiers = EXPANDED)
    fun customExpanded() = capture("customExpanded", ThemeMode.Light, Custom)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    fun loadingLight() =
        composeRule.captureScreen(javaClass, "loadingLight", ThemeMode.Light) {
            ChatAppearanceScreen(
                state = ChatAppearanceUiState.Loading,
                onTextScale = {},
                onBubbleCorners = {},
                onWallpaper = {},
                onBack = {},
            )
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        appearance: ChatAppearance,
        palette: ThemePalette = ThemePalette.Pingogram,
    ) = composeRule.captureScreen(javaClass, name, themeMode, palette = palette, chat = appearance) {
        ChatAppearanceScreen(
            state = ChatAppearanceUiState.Content(appearance),
            onTextScale = {},
            onBubbleCorners = {},
            onWallpaper = {},
            onBack = {},
        )
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant, chat = Custom) {
            ChatAppearanceScreen(
                state = ChatAppearanceUiState.Content(Custom),
                onTextScale = {},
                onBubbleCorners = {},
                onWallpaper = {},
                onBack = {},
            )
        }

    private companion object {
        val Custom =
            ChatAppearance(
                textScale = 1.15f,
                bubbleCorners = BubbleCorners.Large,
                wallpaper = ChatWallpaper.PrimaryToTertiary,
            )
    }
}
