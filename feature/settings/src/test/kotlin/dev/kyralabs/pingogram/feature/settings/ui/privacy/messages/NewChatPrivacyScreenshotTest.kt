package dev.kyralabs.pingogram.feature.settings.ui.privacy.messages

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.PrivacyPreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Who can message me" in each of its states, light and dark; without Telegram Premium in AMOLED black, high contrast,
 * right to left, a 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NewChatPrivacyScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everybodyLight() = capture("everybodyLight", ThemeMode.Light, PrivacyPreviewStates.newChats)

    @Test
    fun everybodyDark() = capture("everybodyDark", ThemeMode.Dark, PrivacyPreviewStates.newChats)

    @Test
    fun restrictedLight() = capture("restrictedLight", ThemeMode.Light, PrivacyPreviewStates.newChatsRestricted)

    @Test
    fun restrictedDark() = capture("restrictedDark", ThemeMode.Dark, PrivacyPreviewStates.newChatsRestricted)

    @Test
    fun withoutPremiumLight() =
        capture("withoutPremiumLight", ThemeMode.Light, PrivacyPreviewStates.newChatsWithoutPremium)

    @Test
    fun withoutPremiumDark() =
        capture("withoutPremiumDark", ThemeMode.Dark, PrivacyPreviewStates.newChatsWithoutPremium)

    @Test
    fun paidLight() = capture("paidLight", ThemeMode.Light, PrivacyPreviewStates.newChatsPaid)

    @Test
    fun savingLight() = capture("savingLight", ThemeMode.Light, PrivacyPreviewStates.newChatsSaving)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, PrivacyPreviewStates.newChatsError)

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, PrivacyPreviewStates.newChatsError)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, PrivacyPreviewStates.newChatsWithoutPremium)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: NewChatPrivacyUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        NewChatPrivacyScreen(state = state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            NewChatPrivacyScreen(state = PrivacyPreviewStates.newChatsWithoutPremium, onEvent = {}, onBack = {})
        }
}
