package dev.kyralabs.pingogram.feature.settings.ui.account.autodelete

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.AccountPagePreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Auto-delete in each of its states, light and dark; the timers in AMOLED black, high contrast, right to left, a
 * 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AutoDeleteScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentLight() = capture("contentLight", ThemeMode.Light, AccountPagePreviewStates.autoDelete)

    @Test
    fun contentDark() = capture("contentDark", ThemeMode.Dark, AccountPagePreviewStates.autoDelete)

    @Test
    fun otherLight() = capture("otherLight", ThemeMode.Light, AccountPagePreviewStates.autoDeleteOther)

    @Test
    fun otherDark() = capture("otherDark", ThemeMode.Dark, AccountPagePreviewStates.autoDeleteOther)

    @Test
    fun savingLight() = capture("savingLight", ThemeMode.Light, AccountPagePreviewStates.autoDeleteSaving)

    @Test
    fun savingDark() = capture("savingDark", ThemeMode.Dark, AccountPagePreviewStates.autoDeleteSaving)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, AccountPagePreviewStates.autoDeleteError)

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, AccountPagePreviewStates.autoDeleteError)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, AccountPagePreviewStates.autoDelete)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: AutoDeleteUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        AutoDeleteScreen(state = state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            AutoDeleteScreen(state = AccountPagePreviewStates.autoDelete, onEvent = {}, onBack = {})
        }
}
