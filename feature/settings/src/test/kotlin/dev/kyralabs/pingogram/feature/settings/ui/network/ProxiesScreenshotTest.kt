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
 * The Proxy page in each of its states, light and dark; the proxies in AMOLED black, high contrast, right to left, a
 * 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProxiesScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun proxiesLight() = capture("proxiesLight", ThemeMode.Light, ProxyPreviewStates.proxies)

    @Test
    fun proxiesDark() = capture("proxiesDark", ThemeMode.Dark, ProxyPreviewStates.proxies)

    @Test
    fun offLight() = capture("offLight", ThemeMode.Light, ProxyPreviewStates.proxiesOff)

    @Test
    fun emptyLight() = capture("emptyLight", ThemeMode.Light, ProxyPreviewStates.proxiesEmpty)

    @Test
    fun emptyDark() = capture("emptyDark", ThemeMode.Dark, ProxyPreviewStates.proxiesEmpty)

    @Test
    fun deletingLight() = capture("deletingLight", ThemeMode.Light, ProxyPreviewStates.proxiesDeleting)

    @Test
    fun deletingDark() = capture("deletingDark", ThemeMode.Dark, ProxyPreviewStates.proxiesDeleting)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, ProxyPreviewStates.proxiesError)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, ProxyPreviewStates.proxies)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ProxiesUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ProxiesScreen(state, onEvent = {}, onAddProxy = {}, onEditProxy = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            ProxiesScreen(ProxyPreviewStates.proxies, onEvent = {}, onAddProxy = {}, onEditProxy = {}, onBack = {})
        }
}
