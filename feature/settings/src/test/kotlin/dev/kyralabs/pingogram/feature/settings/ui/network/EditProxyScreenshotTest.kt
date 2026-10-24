package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.ProxyPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Adding and editing a proxy in each of its states, light and dark; an edited proxy in AMOLED black, high contrast,
 * right to left, a 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class EditProxyScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun newLight() = capture("newLight", ThemeMode.Light, ProxyPreviewStates.newProxy)

    @Test
    fun newDark() = capture("newDark", ThemeMode.Dark, ProxyPreviewStates.newProxy)

    @Test
    fun editLight() = capture("editLight", ThemeMode.Light, ProxyPreviewStates.editProxy)

    @Test
    fun editDark() = capture("editDark", ThemeMode.Dark, ProxyPreviewStates.editProxy)

    @Test
    fun httpLight() = capture("httpLight", ThemeMode.Light, ProxyPreviewStates.httpProxy)

    @Test
    fun errorsLight() = capture("errorsLight", ThemeMode.Light, ProxyPreviewStates.mtprotoErrors)

    @Test
    fun errorsDark() = capture("errorsDark", ThemeMode.Dark, ProxyPreviewStates.mtprotoErrors)

    @Test
    fun savingLight() = capture("savingLight", ThemeMode.Light, ProxyPreviewStates.savingProxy)

    @Test
    fun notFoundLight() = capture("notFoundLight", ThemeMode.Light, ProxyPreviewStates.editProxyError)

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
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, ProxyPreviewStates.editProxy)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: EditProxyUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        EditProxyScreen(state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            EditProxyScreen(ProxyPreviewStates.editProxy, onEvent = {}, onBack = {})
        }
}
