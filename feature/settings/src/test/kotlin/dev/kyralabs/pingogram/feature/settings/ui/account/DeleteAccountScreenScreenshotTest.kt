package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.DeleteAccountPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Delete account page in each of its states, light and dark, on a phone tall enough to show the whole page; the
 * page with a password in AMOLED black, high contrast, right to left, a 700 dp window, an expanded one and at twice
 * the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL)
class DeleteAccountScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun withPasswordLight() = capture("withPasswordLight", ThemeMode.Light, DeleteAccountPreviewStates.withPassword)

    @Test
    fun withPasswordDark() = capture("withPasswordDark", ThemeMode.Dark, DeleteAccountPreviewStates.withPassword)

    @Test
    fun withoutPasswordLight() =
        capture("withoutPasswordLight", ThemeMode.Light, DeleteAccountPreviewStates.withoutPassword)

    @Test
    fun withoutPasswordDark() =
        capture("withoutPasswordDark", ThemeMode.Dark, DeleteAccountPreviewStates.withoutPassword)

    @Test
    fun ownPeriodLight() = capture("ownPeriodLight", ThemeMode.Light, DeleteAccountPreviewStates.ownPeriod)

    @Test
    fun confirmingLight() = capture("confirmingLight", ThemeMode.Light, DeleteAccountPreviewStates.confirming)

    @Test
    fun confirmingDark() = capture("confirmingDark", ThemeMode.Dark, DeleteAccountPreviewStates.confirming)

    @Test
    fun deletingLight() = capture("deletingLight", ThemeMode.Light, DeleteAccountPreviewStates.deleting)

    @Test
    fun deletingDark() = capture("deletingDark", ThemeMode.Dark, DeleteAccountPreviewStates.deleting)

    @Test
    fun wrongPasswordLight() = capture("wrongPasswordLight", ThemeMode.Light, DeleteAccountPreviewStates.wrongPassword)

    @Test
    fun wrongPasswordDark() = capture("wrongPasswordDark", ThemeMode.Dark, DeleteAccountPreviewStates.wrongPassword)

    @Test
    fun floodWaitLight() = capture("floodWaitLight", ThemeMode.Light, DeleteAccountPreviewStates.floodWait)

    @Test
    fun floodWaitDark() = capture("floodWaitDark", ThemeMode.Dark, DeleteAccountPreviewStates.floodWait)

    @Test
    @Config(qualifiers = COMPACT)
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, DeleteAccountUiState.Loading)

    @Test
    @Config(qualifiers = COMPACT)
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, DeleteAccountUiState.Loading)

    @Test
    @Config(qualifiers = COMPACT)
    fun errorLight() = capture("errorLight", ThemeMode.Light, DeleteAccountUiState.Error(DomainError.Network))

    @Test
    @Config(qualifiers = COMPACT)
    fun errorDark() = capture("errorDark", ThemeMode.Dark, DeleteAccountUiState.Error(DomainError.Network))

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM_TALL)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = EXPANDED_TALL)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, DeleteAccountPreviewStates.withPassword)

    @Test
    // Tall enough to show the whole page at twice the font size.
    @Config(qualifiers = "w411dp-h3200dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, DeleteAccountPreviewStates.withPassword)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: DeleteAccountUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        DeleteAccountScreen(state = state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            DeleteAccountScreen(state = DeleteAccountPreviewStates.withPassword, onEvent = {}, onBack = {})
        }
}

/** A phone as [COMPACT], tall enough for the whole page. */
private const val TALL = "w411dp-h1600dp-xxhdpi"

/** The medium (700 dp) and expanded windows of the other settings goldens, tall enough for the whole page. */
private const val MEDIUM_TALL = "w700dp-h1400dp-xxhdpi"
private const val EXPANDED_TALL = "w840dp-h1400dp-xxhdpi"
