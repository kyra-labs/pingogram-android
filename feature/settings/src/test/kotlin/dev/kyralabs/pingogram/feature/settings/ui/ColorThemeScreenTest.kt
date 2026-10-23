package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.themeColorScheme
import dev.kyralabs.pingogram.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the Theme page offers and sends: every theme as a named radio button, the one in use selected, a pick with a
 * tick, and the wallpaper's colors only where the device has them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ColorThemeScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val picks = mutableListOf<ColorThemeOption>()
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private val wallpaper =
        WallpaperSchemes(
            light = themeColorScheme(ThemePalette.Blue, dark = false, Contrast.Standard),
            dark = themeColorScheme(ThemePalette.Blue, dark = true, Contrast.Standard),
        )

    private fun setScreen(
        settings: AppearanceSettings,
        wallpaperSchemes: WallpaperSchemes? = wallpaper,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    ColorThemeScreen(
                        state = ColorThemeUiState.Content(settings),
                        onSelect = { picks += it },
                        onBack = {},
                        wallpaperSchemes = wallpaperSchemes,
                    )
                }
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun chip(
        @StringRes name: Int,
    ) = composeRule.onNode(
        hasContentDescription(
            string(name),
        ) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
    )

    @Test
    fun `every theme is a named radio button, the one in use selected`() {
        setScreen(AppearanceSettings(dynamicColor = false, palette = ThemePalette.Teal))

        chip(R.string.settings_color_theme_teal).assertIsSelected()
        chip(R.string.settings_color_theme_wallpaper).assertIsNotSelected()
        chip(R.string.settings_color_theme_pingogram).assertIsNotSelected()
        for (name in THEME_NAMES) chip(name).assertExists()
    }

    @Test
    fun `picking a theme sends it with a tick`() {
        setScreen(AppearanceSettings(dynamicColor = true))

        chip(R.string.settings_color_theme_monochrome).performScrollTo().performClick()

        assertEquals(listOf(ColorThemeOption.Palette(ThemePalette.Monochrome)), picks)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), performedHaptics)
    }

    @Test
    fun `with dynamic color on, the wallpaper's colors are selected and can be picked again`() {
        setScreen(AppearanceSettings(dynamicColor = true, palette = ThemePalette.Red))

        chip(R.string.settings_color_theme_wallpaper).assertIsSelected().performClick()

        chip(R.string.settings_color_theme_red).assertIsNotSelected()
        assertEquals(listOf(ColorThemeOption.Wallpaper), picks)
    }

    @Test
    fun `before Android 12 there are no wallpaper colors and the generated theme is selected`() {
        setScreen(AppearanceSettings(dynamicColor = true), wallpaperSchemes = null)

        composeRule
            .onAllNodesWithContentDescription(string(R.string.settings_color_theme_wallpaper))
            .assertCountEquals(0)
        chip(R.string.settings_color_theme_pingogram).assertIsSelected()
    }

    private companion object {
        val THEME_NAMES =
            listOf(
                R.string.settings_color_theme_pingogram,
                R.string.settings_color_theme_blue,
                R.string.settings_color_theme_teal,
                R.string.settings_color_theme_green,
                R.string.settings_color_theme_lime,
                R.string.settings_color_theme_yellow,
                R.string.settings_color_theme_orange,
                R.string.settings_color_theme_red,
                R.string.settings_color_theme_pink,
                R.string.settings_color_theme_purple,
                R.string.settings_color_theme_indigo,
                R.string.settings_color_theme_brown,
                R.string.settings_color_theme_monochrome,
            )
    }
}
