package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Privacy and security page in each of its states, on a phone, on an expanded window and at 200 % font scale. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacySecurityScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.privacySecurity)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.privacySecurity)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, SettingsPreviewData.privacySecurity)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.privacySecurity)

    @Test
    fun passwordResetPendingLight() = capture("passwordResetPendingLight", ThemeMode.Light, withPendingReset)

    @Test
    fun contactSyncOnLight() =
        capture(
            "contactSyncOnLight",
            ThemeMode.Light,
            SettingsPreviewData.privacySecurity,
            ContactSyncStatus.On(lastSyncedAt = null),
        )

    @Test
    fun contactSyncPausedDark() =
        capture(
            "contactSyncPausedDark",
            ThemeMode.Dark,
            SettingsPreviewData.privacySecurity,
            ContactSyncStatus.NeedsPermission,
        )

    /** The whole page, down to the Websites row, the secret chats' link previews (allowed) and hidden numbers. */
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    fun wholePageLight() =
        capture(
            "wholePageLight",
            ThemeMode.Light,
            SettingsPreviewData.privacySecurity,
            secretChatPreviews = true,
            phoneNumbersHidden = true,
        )

    /** The Contacts group with "Suggest frequent contacts" (on) under "Sync contacts". */
    @Test
    @Config(qualifiers = TALL)
    fun frequentContactsLight() =
        composeRule.captureScreen(javaClass, "frequentContactsLight", ThemeMode.Light) {
            Frequent()
        }

    @Test
    @Config(qualifiers = TALL)
    fun frequentContactsDark() =
        composeRule.captureScreen(javaClass, "frequentContactsDark", ThemeMode.Dark) { Frequent() }

    @Test
    @Config(qualifiers = TALL)
    fun frequentContactsAmoled() = captureFrequent(ScreenshotVariant.Amoled)

    @Test
    @Config(qualifiers = TALL)
    fun frequentContactsHighContrast() = captureFrequent(ScreenshotVariant.HighContrast)

    @Test
    @Config(qualifiers = TALL)
    fun frequentContactsRtl() = captureFrequent(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h1600dp-xxhdpi")
    fun frequentContactsMedium() = captureFrequent(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h3200dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun frequentContactsFontScale200() =
        composeRule.captureScreen(javaClass, "frequentContactsFontScale200", ThemeMode.Light) { Frequent() }

    private fun captureFrequent(variant: ScreenshotVariant) =
        composeRule.captureScreen(
            javaClass,
            "frequentContacts" + variant.goldenName.replaceFirstChar { it.uppercase() },
            variant,
        ) { Frequent() }

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, PrivacySecurityUiState.Loading)

    @Test
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, PrivacySecurityUiState.Loading)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, PrivacySecurityUiState.Error(DomainError.Network))

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, PrivacySecurityUiState.Error(DomainError.FloodWait(300)))

    private val withPendingReset =
        SettingsPreviewData.privacySecurity.copy(
            twoStepVerification =
                TwoStepVerificationSummary(
                    status = UiText.resource(R.string.settings_two_step_verification_on_no_recovery_email),
                    pendingResetDate = "Oct 25, 2026, 9:41 AM",
                ),
        )

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: PrivacySecurityUiState,
        contactSync: ContactSyncStatus = ContactSyncStatus.Off,
        secretChatPreviews: Boolean = false,
        phoneNumbersHidden: Boolean = false,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        PrivacySecurityScreen(
            state = state,
            onEvent = {},
            onOpen = {},
            onBack = {},
            contactSync = contactSync,
            secretChatPreviews = secretChatPreviews,
            phoneNumbersHidden = phoneNumbersHidden,
        )
    }
}

private const val TALL = "w411dp-h1600dp-xxhdpi"

/** The page with contacts sync on and frequent contacts suggested. */
@Composable
private fun Frequent() {
    PrivacySecurityScreen(
        state = SettingsPreviewData.privacySecurity,
        onEvent = {},
        onOpen = {},
        onBack = {},
        contactSync = ContactSyncStatus.On(lastSyncedAt = null),
        suggestsFrequent = true,
    )
}
