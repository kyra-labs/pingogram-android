package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Appearance page: loading, with the defaults, with the contrast following the system (Android 14+) and AMOLED
 * black on (in the AMOLED theme it chose), without dynamic color (before Android 12), at 200% font scale, where the
 * connected buttons give way to radio rows, and with AMOLED black, high contrast, right to left and on a medium window
 * (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AppearanceScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, AppearanceUiState.Loading)

    @Test
    fun light() = capture("light", ThemeMode.Light, defaults)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, defaults)

    @Test
    fun contrastFollowsSystemDark() =
        capture(
            "contrastFollowsSystemDark",
            ThemeMode.Dark,
            AppearanceUiState.Content(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true)),
            systemContrast = Contrast.Medium,
            amoled = true,
        )

    @Test
    fun withoutDynamicColorLight() =
        capture(
            "withoutDynamicColorLight",
            ThemeMode.Light,
            AppearanceUiState.Content(AppearanceSettings(themeMode = ThemeMode.Light, contrast = Contrast.High)),
            dynamicColorAvailable = false,
        )

    @Test
    fun brandColorsNoteLight() =
        capture(
            "brandColorsNoteLight",
            ThemeMode.Light,
            AppearanceUiState.Content(AppearanceSettings(contrast = Contrast.Medium)),
        )

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, defaults)

    @Test
    fun amoled() =
        captureAudit(
            ScreenshotVariant.Amoled,
            AppearanceUiState.Content(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true)),
        )

    @Test
    fun highContrast() =
        captureAudit(
            ScreenshotVariant.HighContrast,
            AppearanceUiState.Content(AppearanceSettings(contrast = Contrast.High)),
        )

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl, defaults)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium, defaults)

    private val defaults = AppearanceUiState.Content(AppearanceSettings())

    private fun captureAudit(
        variant: ScreenshotVariant,
        state: AppearanceUiState,
    ) = composeRule.captureScreen(javaClass, variant.goldenName, variant) {
        AppearanceScreen(state = state, onEvent = {}, onBack = {})
    }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: AppearanceUiState,
        dynamicColorAvailable: Boolean = true,
        systemContrast: Contrast? = null,
        amoled: Boolean = false,
    ) = composeRule.captureScreen(javaClass, name, themeMode, amoled) {
        AppearanceScreen(
            state = state,
            onEvent = {},
            onBack = {},
            dynamicColorAvailable = dynamicColorAvailable,
            systemContrast = systemContrast,
        )
    }
}
