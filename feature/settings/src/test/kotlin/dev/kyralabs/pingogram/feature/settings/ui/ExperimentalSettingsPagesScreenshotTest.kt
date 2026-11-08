package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.GroupBubbles
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings pages in the experimental look (`docs/DESIGN_SYSTEM.md` §13.10): the wide title, the rows' vivid badges in
 * turn, the chevrons of rows that open a page and the tinted destructive rows, as every page draws them through the
 * shared parts. Light, dark, AMOLED black, high contrast, right to left and at the largest font scale. The standard
 * look's goldens of each page are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ExperimentalSettingsPagesScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun privacyLight() =
        composeRule.captureScreen(javaClass, "privacyLight", ThemeMode.Light) {
            Experimental { Privacy() }
        }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun privacyDark() =
        composeRule.captureScreen(javaClass, "privacyDark", ThemeMode.Dark) { Experimental { Privacy() } }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun privacyAmoled() =
        composeRule.captureScreen(javaClass, "privacyAmoled", ScreenshotVariant.Amoled) { Experimental { Privacy() } }

    @Test
    fun privacyHighContrast() =
        composeRule.captureScreen(javaClass, "privacyHighContrast", ScreenshotVariant.HighContrast) {
            Experimental { Privacy() }
        }

    @Test
    fun privacyRtl() =
        composeRule.captureScreen(javaClass, "privacyRtl", ScreenshotVariant.Rtl) { Experimental { Privacy() } }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun privacyFontScale200() =
        composeRule.captureScreen(javaClass, "privacyFontScale200", ThemeMode.Light) { Experimental { Privacy() } }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun notificationsDark() =
        composeRule.captureScreen(javaClass, "notificationsDark", ThemeMode.Dark) {
            Experimental {
                NotificationSettingsScreen(
                    state = SettingsPreviewData.notifications,
                    onEvent = {},
                    onOpen = {},
                    onBack = {},
                )
            }
        }

    @Test
    fun notificationsBlockedLight() =
        composeRule.captureScreen(javaClass, "notificationsBlockedLight", ThemeMode.Light) {
            Experimental {
                NotificationSettingsScreen(
                    state = SettingsPreviewData.notifications.copy(notificationsBlocked = true),
                    onEvent = {},
                    onOpen = {},
                    onBack = {},
                )
            }
        }

    @Test
    fun notificationsRtl() =
        composeRule.captureScreen(javaClass, "notificationsRtl", ScreenshotVariant.Rtl) {
            Experimental {
                NotificationSettingsScreen(
                    state = SettingsPreviewData.notifications,
                    onEvent = {},
                    onOpen = {},
                    onBack = {},
                )
            }
        }

    @Test
    fun powerSavingLight() =
        composeRule.captureScreen(javaClass, "powerSavingLight", ThemeMode.Light) {
            Experimental { PowerSavingScreen(SettingsPreviewData.powerSaving, onEvent = {}, onBack = {}) }
        }

    @Test
    fun powerSavingOnDark() =
        composeRule.captureScreen(javaClass, "powerSavingOnDark", ThemeMode.Dark) {
            Experimental { PowerSavingScreen(SettingsPreviewData.powerSavingOn, onEvent = {}, onBack = {}) }
        }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun powerSavingFontScale200() =
        composeRule.captureScreen(javaClass, "powerSavingFontScale200", ThemeMode.Light) {
            Experimental { PowerSavingScreen(SettingsPreviewData.powerSavingOn, onEvent = {}, onBack = {}) }
        }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun dataLight() = composeRule.captureScreen(javaClass, "dataLight", ThemeMode.Light) { Experimental { Data() } }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun dataDark() = composeRule.captureScreen(javaClass, "dataDark", ThemeMode.Dark) { Experimental { Data() } }

    @Test
    fun dataRtl() = composeRule.captureScreen(javaClass, "dataRtl", ScreenshotVariant.Rtl) { Experimental { Data() } }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun sessionsLight() =
        composeRule.captureScreen(javaClass, "sessionsLight", ThemeMode.Light) {
            Experimental { ActiveSessionsScreen(state = SettingsPreviewData.sessions, onEvent = {}, onBack = {}) }
        }

    @Test
    fun sessionsDark() =
        composeRule.captureScreen(javaClass, "sessionsDark", ThemeMode.Dark) {
            Experimental { ActiveSessionsScreen(state = SettingsPreviewData.sessions, onEvent = {}, onBack = {}) }
        }

    @Test
    fun editProfileLight() =
        composeRule.captureScreen(javaClass, "editProfileLight", ThemeMode.Light) {
            Experimental { EditProfileScreen(state = SettingsPreviewData.editProfile, onEvent = {}, onPickPhoto = {}) }
        }

    @Test
    fun editProfileDark() =
        composeRule.captureScreen(javaClass, "editProfileDark", ThemeMode.Dark) {
            Experimental { EditProfileScreen(state = SettingsPreviewData.editProfile, onEvent = {}, onPickPhoto = {}) }
        }

    @Test
    fun aboutDark() =
        composeRule.captureScreen(javaClass, "aboutDark", ThemeMode.Dark) {
            Experimental {
                AboutScreen(
                    state = AboutUiState(versionName = "0.3.0"),
                    onEvent = {},
                    onOpenLicenses = {},
                    onBack = {},
                    onOpenLinkSettings = {},
                )
            }
        }

    @Test
    fun aboutLight() =
        composeRule.captureScreen(javaClass, "aboutLight", ThemeMode.Light) {
            Experimental {
                AboutScreen(
                    state = AboutUiState(versionName = "0.3.0"),
                    onEvent = {},
                    onOpenLicenses = {},
                    onBack = {},
                    onOpenLinkSettings = {},
                )
            }
        }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun appearanceLight() =
        composeRule.captureScreen(javaClass, "appearanceLight", ThemeMode.Light) { Experimental { Appearance() } }

    @Test
    @Config(qualifiers = WHOLE_PAGE)
    fun appearanceDark() =
        composeRule.captureScreen(javaClass, "appearanceDark", ThemeMode.Dark) {
            Experimental { Appearance(AppearanceSettings(themeMode = ThemeMode.Dark)) }
        }

    @Test
    fun appearanceAmoled() =
        composeRule.captureScreen(javaClass, "appearanceAmoled", ScreenshotVariant.Amoled) {
            Experimental { Appearance(AppearanceSettings(themeMode = ThemeMode.Dark, amoled = true)) }
        }

    @Test
    fun appearanceHighContrast() =
        composeRule.captureScreen(javaClass, "appearanceHighContrast", ScreenshotVariant.HighContrast) {
            Experimental { Appearance() }
        }

    @Test
    fun appearanceRtl() =
        composeRule.captureScreen(javaClass, "appearanceRtl", ScreenshotVariant.Rtl) { Experimental { Appearance() } }

    @Test
    @Config(qualifiers = WHOLE_PAGE, fontScale = FONT_SCALE_200)
    fun appearanceFontScale200() =
        composeRule.captureScreen(javaClass, "appearanceFontScale200", ThemeMode.Light) {
            Experimental { Appearance() }
        }
}

@Composable
private fun Experimental(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExperimentalUi provides true, content = content)
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
        dynamicColorAvailable = false,
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
