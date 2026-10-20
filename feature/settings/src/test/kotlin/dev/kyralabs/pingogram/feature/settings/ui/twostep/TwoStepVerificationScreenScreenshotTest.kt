package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.EXPANDED
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The 2-step verification page in each of its states, on a phone, on an expanded window and at 200 % font scale. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TwoStepVerificationScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onLight() = capture("onLight", ThemeMode.Light, SettingsPreviewData.twoStepOn)

    @Test
    fun onDark() = capture("onDark", ThemeMode.Dark, SettingsPreviewData.twoStepOn)

    @Test
    fun offLight() = capture("offLight", ThemeMode.Light, SettingsPreviewData.twoStepOff)

    @Test
    fun pendingLight() = capture("pendingLight", ThemeMode.Light, SettingsPreviewData.twoStepPending)

    @Test
    fun pendingDark() = capture("pendingDark", ThemeMode.Dark, SettingsPreviewData.twoStepPending)

    @Test
    fun resetReadyLight() =
        capture(
            "resetReadyLight",
            ThemeMode.Light,
            SettingsPreviewData.twoStepPending.copy(
                pendingEmailPattern = null,
                pendingReset = PendingPasswordReset("Oct 17, 2026, 9:41 AM", canCompleteNow = true),
            ),
        )

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, SettingsPreviewData.twoStepOn)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.twoStepPending)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, TwoStepVerificationUiState.Loading)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, TwoStepVerificationUiState.Error(DomainError.Network))

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: TwoStepVerificationUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        TwoStepVerificationScreen(state = state, onEvent = {}, onOpen = {}, onBack = {})
    }
}
