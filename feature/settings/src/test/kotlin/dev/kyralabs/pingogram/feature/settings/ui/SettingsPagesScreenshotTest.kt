package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.GroupBubbles
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings pages (`docs/DESIGN_SYSTEM.md` §13.10): the wide title, the rows' vivid badges in
 * turn, the chevrons of rows that open a page and the tinted destructive rows, as every page draws them through the
 * shared parts. Light, dark, AMOLED black, high contrast, right to left and at the largest font scale. The standard
 * look's goldens of each page are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsPagesScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun privacyLight() =
        composeRule.captureScreen(javaClass, "privacyLight", ThemeMode.Light) {
            Privacy()
        }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun privacyDark() = composeRule.captureScreen(javaClass, "privacyDark", ThemeMode.Dark) { Privacy() }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun privacyAmoled() = composeRule.captureScreen(javaClass, "privacyAmoled", ScreenshotVariant.Amoled) { Privacy() }

    @Test
    fun privacyHighContrast() =
        composeRule.captureScreen(javaClass, "privacyHighContrast", ScreenshotVariant.HighContrast) {
            Privacy()
        }

    @Test
    fun privacyRtl() = composeRule.captureScreen(javaClass, "privacyRtl", ScreenshotVariant.Rtl) { Privacy() }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun privacyFontScale200() =
        composeRule.captureScreen(javaClass, "privacyFontScale200", ThemeMode.Light) { Privacy() }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun notificationsDark() =
        composeRule.captureScreen(javaClass, "notificationsDark", ThemeMode.Dark) {
            NotificationSettingsScreen(
                state = SettingsPreviewData.notifications,
                onEvent = {},
                onOpen = {},
                onBack = {},
            )
        }

    @Test
    fun notificationsBlockedLight() =
        composeRule.captureScreen(javaClass, "notificationsBlockedLight", ThemeMode.Light) {
            NotificationSettingsScreen(
                state = SettingsPreviewData.notifications.copy(notificationsBlocked = true),
                onEvent = {},
                onOpen = {},
                onBack = {},
            )
        }

    @Test
    fun notificationsRtl() =
        composeRule.captureScreen(javaClass, "notificationsRtl", ScreenshotVariant.Rtl) {
            NotificationSettingsScreen(
                state = SettingsPreviewData.notifications,
                onEvent = {},
                onOpen = {},
                onBack = {},
            )
        }

    @Test
    fun powerSavingLight() =
        composeRule.captureScreen(javaClass, "powerSavingLight", ThemeMode.Light) {
            PowerSavingScreen(SettingsPreviewData.powerSaving, onEvent = {}, onBack = {})
        }

    @Test
    fun powerSavingOnDark() =
        composeRule.captureScreen(javaClass, "powerSavingOnDark", ThemeMode.Dark) {
            PowerSavingScreen(SettingsPreviewData.powerSavingOn, onEvent = {}, onBack = {})
        }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun powerSavingFontScale200() =
        composeRule.captureScreen(javaClass, "powerSavingFontScale200", ThemeMode.Light) {
            PowerSavingScreen(SettingsPreviewData.powerSavingOn, onEvent = {}, onBack = {})
        }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun dataLight() = composeRule.captureScreen(javaClass, "dataLight", ThemeMode.Light) { Data() }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun dataDark() = composeRule.captureScreen(javaClass, "dataDark", ThemeMode.Dark) { Data() }

    @Test
    fun dataRtl() = composeRule.captureScreen(javaClass, "dataRtl", ScreenshotVariant.Rtl) { Data() }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun sessionsLight() =
        composeRule.captureScreen(javaClass, "sessionsLight", ThemeMode.Light) {
            ActiveSessionsScreen(state = SettingsPreviewData.sessions, onEvent = {}, onBack = {})
        }

    @Test
    fun sessionsDark() =
        composeRule.captureScreen(javaClass, "sessionsDark", ThemeMode.Dark) {
            ActiveSessionsScreen(state = SettingsPreviewData.sessions, onEvent = {}, onBack = {})
        }

    @Test
    fun editProfileLight() =
        composeRule.captureScreen(javaClass, "editProfileLight", ThemeMode.Light) {
            EditProfileScreen(state = SettingsPreviewData.editProfile, onEvent = {}, onPickPhoto = {})
        }

    @Test
    fun editProfileDark() =
        composeRule.captureScreen(javaClass, "editProfileDark", ThemeMode.Dark) {
            EditProfileScreen(state = SettingsPreviewData.editProfile, onEvent = {}, onPickPhoto = {})
        }

    @Test
    fun aboutDark() =
        composeRule.captureScreen(javaClass, "aboutDark", ThemeMode.Dark) {
            AboutScreen(
                state = AboutUiState(versionName = "0.3.0"),
                onEvent = {},
                onOpenLicenses = {},
                onBack = {},
                onOpenLinkSettings = {},
            )
        }

    @Test
    fun aboutFromAppStore() =
        composeRule.captureScreen(javaClass, "aboutFromAppStore", ThemeMode.Light) {
            AboutScreen(
                state = AboutUiState(versionName = "0.3.0", showsCheckForUpdates = false),
                onEvent = {},
                onOpenLicenses = {},
                onBack = {},
                onOpenLinkSettings = {},
            )
        }

    @Test
    fun aboutLight() =
        composeRule.captureScreen(javaClass, "aboutLight", ThemeMode.Light) {
            AboutScreen(
                state = AboutUiState(versionName = "0.3.0"),
                onEvent = {},
                onOpenLicenses = {},
                onBack = {},
                onOpenLinkSettings = {},
            )
        }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun appearanceLight() = composeRule.captureScreen(javaClass, "appearanceLight", ThemeMode.Light) { Appearance() }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun appearanceDark() =
        composeRule.captureScreen(javaClass, "appearanceDark", ThemeMode.Dark) {
            Appearance(AppearanceSettings(themeMode = ThemeMode.Dark))
        }

    @Test
    fun appearanceAmoled() =
        composeRule.captureScreen(javaClass, "appearanceAmoled", ScreenshotVariant.Amoled) {
            Appearance(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true))
        }

    @Test
    fun appearanceHighContrast() =
        composeRule.captureScreen(javaClass, "appearanceHighContrast", ScreenshotVariant.HighContrast) {
            Appearance()
        }

    @Test
    fun appearanceRtl() = composeRule.captureScreen(javaClass, "appearanceRtl", ScreenshotVariant.Rtl) { Appearance() }

    @Test
    @Config(qualifiers = WHOLE_PAGE, fontScale = FONT_SCALE_200)
    fun appearanceFontScale200() =
        composeRule.captureScreen(javaClass, "appearanceFontScale200", ThemeMode.Light) {
            Appearance()
        }
}

@Composable
private fun Privacy() {
    PrivacySecurityScreen(
        state = SettingsPreviewData.privacySecurity,
        onEvent = {},
        onOpen = {},
        onBack = {},
    )
}

/** Appearance with a generated theme picked (Teal), Vivid group bubbles and the defaults otherwise. */
@Composable
private fun Appearance(settings: AppearanceSettings = AppearanceSettings()) {
    AppearanceScreen(
        state =
            AppearanceUiState.Content(
                settings.copy(
                    dynamicColor = false,
                    palette = ThemePalette.Teal,
                    chat = ChatAppearance(groupBubbles = GroupBubbles.Vivid),
                ),
            ),
        onEvent = {},
        onBack = {},
    )
}

/** Data and storage with the storage used read. */
@Composable
private fun Data() {
    DataStorageScreen(
        state = SettingsPreviewData.storageChanged,
        onEvent = {},
        onOpenAutoDownload = {},
        onOpenProxies = {},
        onOpenNetworkUsage = {},
        onBack = {},
    )
}

/** Tall enough for a whole page. */
private const val WHOLE_PAGE = "w411dp-h2400dp-xxhdpi"
