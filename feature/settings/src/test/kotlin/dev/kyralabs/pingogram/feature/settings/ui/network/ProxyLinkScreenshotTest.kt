package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.spacing
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
 * The proxy link sheet's content on the sheet's container, in each of its states, light and dark; the question in
 * AMOLED black, high contrast, right to left, a 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProxyLinkScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun askLight() = capture("askLight", ThemeMode.Light, ProxyPreviewStates.link)

    @Test
    fun askDark() = capture("askDark", ThemeMode.Dark, ProxyPreviewStates.link)

    @Test
    fun checkingLight() = capture("checkingLight", ThemeMode.Light, ProxyPreviewStates.linkChecking)

    @Test
    fun inUseLight() = capture("inUseLight", ThemeMode.Light, ProxyPreviewStates.linkInUse)

    @Test
    fun connectingLight() = capture("connectingLight", ThemeMode.Light, ProxyPreviewStates.linkConnecting)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, ProxyPreviewStates.linkError)

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, ProxyPreviewStates.linkError)

    @Test
    fun connectedLight() = capture("connectedLight", ThemeMode.Light, ProxyPreviewStates.linkConnected)

    @Test
    fun connectedDark() = capture("connectedDark", ThemeMode.Dark, ProxyPreviewStates.linkConnected)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, ProxyPreviewStates.link)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: ProxyLinkUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        SheetSurface { ProxyLinkContent(state, onEvent = {}, onClose = {}) }
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            SheetSurface { ProxyLinkContent(ProxyPreviewStates.link, onEvent = {}, onClose = {}) }
        }
}

/** [content] on a bottom sheet's container, its top corners and padding, as in the sheet's window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BottomSheetDefaults.ExpandedShape,
        color = BottomSheetDefaults.ContainerColor,
    ) {
        Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) { content() }
    }
}
