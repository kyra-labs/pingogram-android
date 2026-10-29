package dev.kyralabs.pingogram.feature.settings.ui.websites

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
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
 * The Websites page, light and dark, in AMOLED black, high contrast, right to left, a 700 dp window and at twice the
 * font size (`docs/DESIGN_SYSTEM.md` §12), while one logs out, asking first, without websites, and failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ConnectedWebsitesScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    fun busyLight() = capture("busyLight", ThemeMode.Light, ConnectedWebsitesPreviewStates.busy)

    @Test
    fun dialogLight() = capture("dialogLight", ThemeMode.Light, ConnectedWebsitesPreviewStates.dialog)

    @Test
    fun dialogAllLight() = capture("dialogAllLight", ThemeMode.Light, ConnectedWebsitesPreviewStates.dialogAll)

    @Test
    fun emptyLight() = capture("emptyLight", ThemeMode.Light, ConnectedWebsitesPreviewStates.empty)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, ConnectedWebsitesPreviewStates.error)

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
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ConnectedWebsitesUiState = ConnectedWebsitesPreviewStates.content,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        ConnectedWebsitesScreen(state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            ConnectedWebsitesScreen(ConnectedWebsitesPreviewStates.content, onEvent = {}, onBack = {})
        }
}
