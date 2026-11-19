package dev.kyralabs.pingogram.feature.settings.ui.raisetolisten

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Raise to listen page (owner decision 2026-11-19): its master switch on, and off with "Pause when lowered"
 * dimmed, at 200% font scale, and the audit's variants (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RaiseToListenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onLight() = capture("onLight", ThemeMode.Light, on)

    @Test
    fun offLight() = capture("offLight", ThemeMode.Light, off)

    @Test
    fun onDark() = capture("onDark", ThemeMode.Dark, on)

    @Test
    fun offDark() = capture("offDark", ThemeMode.Dark, off)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, off)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    private val on = RaiseToListenUiState.Content(RaiseToListenSettings(enabled = true, pauseWhenLowered = true))
    private val off = RaiseToListenUiState.Content(RaiseToListenSettings(enabled = false))

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            RaiseToListenScreen(off, onEvent = {}, onBack = {})
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: RaiseToListenUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        RaiseToListenScreen(state, onEvent = {}, onBack = {})
    }
}
