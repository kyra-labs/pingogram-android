package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.StoryNotifications
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Notifications page in each of its states, on a phone, on an expanded window and at 200 % font scale, and whole
 * on a tall window as the `foss` flavor shows it (with "Keep connected") and as `gms` does (without).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NotificationSettingsScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.notifications)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.notifications)

    @Test
    @Config(qualifiers = TALL)
    fun fossWholeLight() = capture("fossWholeLight", ThemeMode.Light, SettingsPreviewData.notifications)

    @Test
    @Config(qualifiers = TALL)
    fun fossWholeDark() = capture("fossWholeDark", ThemeMode.Dark, SettingsPreviewData.notificationsBlocked)

    @Test
    @Config(qualifiers = TALL)
    fun gmsWholeLight() =
        capture(
            "gmsWholeLight",
            ThemeMode.Light,
            SettingsPreviewData.notifications.copy(isKeepConnectedOffered = false),
        )

    @Test
    fun blockedLight() = capture("blockedLight", ThemeMode.Light, SettingsPreviewData.notificationsBlocked)

    @Test
    fun blockedDark() = capture("blockedDark", ThemeMode.Dark, SettingsPreviewData.notificationsBlocked)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, SettingsPreviewData.notifications)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.notificationsBlocked)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, NotificationSettingsUiState())

    @Test
    fun errorDark() =
        capture(
            "errorDark",
            ThemeMode.Dark,
            NotificationSettingsUiState(scopes = ScopeSettingsState.Error(DomainError.Network)),
        )

    @Test
    fun resetDialogLight() =
        capture("resetDialogLight", ThemeMode.Light, SettingsPreviewData.notifications.copy(isResetDialogOpen = true))

    @Test
    fun resetDialogDark() =
        capture("resetDialogDark", ThemeMode.Dark, SettingsPreviewData.notifications.copy(isResetDialogOpen = true))

    /** The private chats' group, its new stories on Automatic, in the audit's other looks. */
    @Test
    fun storiesAmoled() = captureVariant(ScreenshotVariant.Amoled)

    @Test
    fun storiesHighContrast() = captureVariant(ScreenshotVariant.HighContrast)

    @Test
    fun storiesRtl() = captureVariant(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun storiesMedium() = captureVariant(ScreenshotVariant.Medium)

    /** New stories turned off: no "Name who posted". */
    @Test
    fun storiesNoneLight() =
        capture(
            "storiesNoneLight",
            ThemeMode.Light,
            SettingsPreviewData.notifications.withPrivateChats { it.copy(stories = StoryNotifications.None) },
        )

    private fun captureVariant(variant: ScreenshotVariant) =
        composeRule.captureScreen(
            javaClass,
            "stories" + variant.goldenName.replaceFirstChar { it.uppercase() },
            variant,
        ) {
            NotificationSettingsScreen(
                state = SettingsPreviewData.notifications,
                onEvent = {},
                onOpen = {},
                onBack = {},
            )
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: NotificationSettingsUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        NotificationSettingsScreen(state = state, onEvent = {}, onOpen = {}, onBack = {})
    }

    private companion object {
        /** Tall enough for the whole page at 100 % font scale. */
        const val TALL = "w411dp-h2000dp-xxhdpi"
    }
}

/** [this] with the private chats' defaults changed by [change]. */
private fun NotificationSettingsUiState.withPrivateChats(
    change: (ScopeNotificationSettings) -> ScopeNotificationSettings,
): NotificationSettingsUiState {
    val scopes = scopes as ScopeSettingsState.Content
    return copy(
        scopes =
            ScopeSettingsState.Content(
                scopes.settings
                    .mapValues { (scope, settings) ->
                        if (scope == NotificationScope.PrivateChats) change(settings) else settings
                    }.toImmutableMap(),
            ),
    )
}
