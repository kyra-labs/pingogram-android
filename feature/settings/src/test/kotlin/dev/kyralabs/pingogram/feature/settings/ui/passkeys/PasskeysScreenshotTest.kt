package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
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
 * The Passkeys page, light and dark, in AMOLED black, high contrast, right to left, a 700 dp window and at twice the
 * font size (`docs/DESIGN_SYSTEM.md` §12), while one is added and one removed, asking first, without passkeys, before
 * Android 14 (no "Add passkey"), and failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PasskeysScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    fun removingLight() = capture("removingLight", ThemeMode.Light, PasskeysPreviewStates.removing)

    @Test
    fun addingDark() = capture("addingDark", ThemeMode.Dark, PasskeysPreviewStates.adding)

    @Test
    fun dialogLight() = capture("dialogLight", ThemeMode.Light, PasskeysPreviewStates.dialog)

    @Test
    fun oldAndroidLight() = capture("oldAndroidLight", ThemeMode.Light, canAdd = false)

    @Test
    fun emptyLight() = capture("emptyLight", ThemeMode.Light, PasskeysPreviewStates.empty)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, PasskeysPreviewStates.error)

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
        state: PasskeysUiState = PasskeysPreviewStates.content,
        canAdd: Boolean = true,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { Screen(state, canAdd) }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            Screen(PasskeysPreviewStates.content, canAdd = true)
        }

    @Composable
    private fun Screen(
        state: PasskeysUiState,
        canAdd: Boolean,
    ) = PasskeysScreen(
        state = state,
        canAdd = canAdd,
        onEvent = {},
        onOpen = {},
        onBack = {},
    )
}
