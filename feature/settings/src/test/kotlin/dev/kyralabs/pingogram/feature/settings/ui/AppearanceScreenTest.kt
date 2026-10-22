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
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the Appearance page shows and sends: the theme and contrast choices, the switches (whole rows, read as
 * switches), dynamic color only where the device has it, and the contrast level that follows the system on Android
 * 14+. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AppearanceScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AppearanceEvent>()
    private var themeOpened = 0
    private var chatAppearanceOpened = 0
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private fun setScreen(
        settings: AppearanceSettings = AppearanceSettings(),
        dynamicColorAvailable: Boolean = true,
        systemContrast: Contrast? = null,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    AppearanceScreen(
                        state = AppearanceUiState.Content(settings),
                        onEvent = { events += it },
                        onBack = {},
                        onOpenColorTheme = { themeOpened++ },
                        onOpenChatAppearance = { chatAppearanceOpened++ },
                        dynamicColorAvailable = dynamicColorAvailable,
                        systemContrast = systemContrast,
                    )
                }
            }
        }
    }

    private fun fallbackNote(
        @StringRes theme: Int,
    ) = RuntimeEnvironment.getApplication().getString(R.string.settings_contrast_fallback_theme, string(theme))

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun node(
        @StringRes id: Int,
    ) = composeRule.onNodeWithText(string(id), useUnmergedTree = false)

    /** The row of a switch: the node with the switch's role and [title]. */
    private fun switchRow(
        @StringRes title: Int,
    ) = composeRule.onNode(
        hasText(string(title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
    )

    @Test
    fun `shows the saved theme checked`() {
        setScreen(AppearanceSettings(themeMode = ThemeMode.Dark))

        node(R.string.settings_theme_dark).assertIsOn()
        node(R.string.settings_theme_system).assertIsOff()
    }

    @Test
    fun `the choices are a connected button group side by side where their labels fit`() {
        setScreen()

        val system = node(R.string.settings_theme_system).getUnclippedBoundsInRoot()
        val light = node(R.string.settings_theme_light).getUnclippedBoundsInRoot()
        assertEquals(system.top, light.top)
    }

    @Test
    fun `screen readers hear the connected buttons as radio buttons`() {
        setScreen()

        node(R.string.settings_theme_light)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `choosing another theme saves it with a segment tick`() {
        setScreen()

        node(R.string.settings_theme_light).performClick()

        assertEquals(listOf(AppearanceEvent.SetThemeMode(ThemeMode.Light)), events)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), performedHaptics)
    }

    @Test
    fun `choosing the selected theme again changes nothing`() {
        setScreen()

        node(R.string.settings_theme_system).performClick()

        assertTrue(events.isEmpty())
        assertTrue(performedHaptics.isEmpty())
    }

    @Test
    fun `the AMOLED row toggles AMOLED black with a toggle-on haptic`() {
        setScreen()

        switchRow(R.string.settings_amoled).assertIsOff().performClick()

        assertEquals(listOf(AppearanceEvent.SetAmoled(true)), events)
        assertEquals(listOf(HapticFeedbackType.ToggleOn), performedHaptics)
    }

    @Test
    fun `the Theme row names the theme and opens the Theme page`() {
        setScreen(AppearanceSettings(dynamicColor = false, palette = ThemePalette.Teal))

        node(R.string.settings_color_theme_teal).assertExists()
        node(R.string.settings_color_theme).performClick()

        assertEquals(1, themeOpened)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `the Chat appearance row sums up the chat settings and opens their page`() {
        setScreen(AppearanceSettings(chat = ChatAppearance(textScale = 1.2f, wallpaper = ChatWallpaper.Primary)))

        composeRule.onNodeWithText("120% text", substring = true).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_chat_wallpaper_primary), substring = true).assertExists()
        node(R.string.settings_chat_appearance).performClick()

        assertEquals(1, chatAppearanceOpened)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `with dynamic color the Theme row names the wallpaper's colors`() {
        setScreen(AppearanceSettings(dynamicColor = true, palette = ThemePalette.Teal))

        node(R.string.settings_color_theme_wallpaper).assertExists()
        node(R.string.settings_color_theme_teal).assertDoesNotExist()
    }

    @Test
    fun `below Android 12 the Theme row names the generated theme, as there are no wallpaper colors`() {
        setScreen(AppearanceSettings(dynamicColor = true), dynamicColorAvailable = false)

        node(R.string.settings_color_theme_pingogram).assertExists()
        node(R.string.settings_color_theme_wallpaper).assertDoesNotExist()
    }

    @Test
    fun `choosing a contrast level saves it`() {
        setScreen()

        node(R.string.settings_contrast_high).assertIsEnabled().performClick()

        assertEquals(listOf(AppearanceEvent.SetContrast(Contrast.High)), events)
    }

    @Test
    fun `on Android 14 and later the contrast follows the system and cannot be changed`() {
        setScreen(AppearanceSettings(contrast = Contrast.Standard), systemContrast = Contrast.Medium)

        node(R.string.settings_contrast_follows_system).assertExists()
        node(R.string.settings_contrast_medium).assertIsOn().assertIsNotEnabled()
        node(R.string.settings_contrast_high).assertIsNotEnabled().performClick()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `medium contrast with dynamic color names the theme it uses instead`() {
        setScreen(AppearanceSettings(dynamicColor = true, palette = ThemePalette.Teal, contrast = Contrast.Medium))

        composeRule.onNodeWithText(fallbackNote(R.string.settings_color_theme_teal)).assertExists()
    }

    @Test
    fun `standard contrast needs no note`() {
        setScreen(AppearanceSettings(contrast = Contrast.Standard))

        composeRule.onNodeWithText(fallbackNote(R.string.settings_color_theme_pingogram)).assertDoesNotExist()
        node(R.string.settings_contrast_follows_system).assertDoesNotExist()
    }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun `at a font scale where the buttons are too narrow the choices are radio buttons that work alike`() {
        setScreen()

        // One below the other rather than side by side.
        val system = node(R.string.settings_theme_system).getUnclippedBoundsInRoot()
        val light = node(R.string.settings_theme_light).getUnclippedBoundsInRoot()
        assertTrue(light.top >= system.bottom)
        node(R.string.settings_theme_system).assertIsSelected()
        node(R.string.settings_contrast_standard).assertIsSelected()
        node(R.string.settings_theme_dark).performClick()

        assertEquals(listOf(AppearanceEvent.SetThemeMode(ThemeMode.Dark)), events)
    }

    @Test
    fun `the switches are read as switches`() {
        setScreen()

        switchRow(R.string.settings_amoled).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
    }
}
