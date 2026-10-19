package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A privacy setting's page in each of its states, saving a choice, and voice messages without Telegram Premium. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacySettingScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.privacySetting)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.privacySetting)

    @Test
    fun savingLight() =
        capture(
            "savingLight",
            ThemeMode.Light,
            SettingsPreviewData.privacySetting.copy(savingChoice = PrivacyChoice.Nobody),
        )

    @Test
    fun premiumRequiredLight() = capture("premiumRequiredLight", ThemeMode.Light, SettingsPreviewData.voiceMessages)

    @Test
    fun premiumRequiredDark() = capture("premiumRequiredDark", ThemeMode.Dark, SettingsPreviewData.voiceMessages)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, PrivacySettingUiState(PrivacySetting.Calls))

    @Test
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, PrivacySettingUiState(PrivacySetting.Calls))

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, failed)

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, failed)

    private val failed = PrivacySettingUiState(PrivacySetting.Calls, PrivacyRulesState.Failed(DomainError.Network))

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: PrivacySettingUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        PrivacySettingScreen(state = state, onEvent = {}, onBack = {})
    }
}
