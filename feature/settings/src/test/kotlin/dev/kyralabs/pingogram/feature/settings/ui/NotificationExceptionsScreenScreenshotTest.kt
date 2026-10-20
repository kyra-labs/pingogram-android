package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The exceptions page of groups in each of its states, with a change in progress, and with a chat's mute options. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NotificationExceptionsScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val content = SettingsPreviewData.notificationExceptions
    private val items = SettingsPreviewData.notificationExceptionItems

    @Test
    fun light() = capture("light", ThemeMode.Light, content)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, content)

    @Test
    fun changingLight() =
        capture(
            "changingLight",
            ThemeMode.Light,
            content.copy(
                exceptions =
                    ExceptionsState.Content(
                        (listOf(items.first().copy(isChanging = true)) + items.drop(1)).toImmutableList(),
                    ),
            ),
        )

    @Test
    fun muteOptionsLight() = capture("muteOptionsLight", ThemeMode.Light, content.copy(muteOptions = items[1]))

    @Test
    fun muteOptionsDark() = capture("muteOptionsDark", ThemeMode.Dark, content.copy(muteOptions = items[2]))

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, content)

    @Test
    fun emptyLight() =
        capture(
            "emptyLight",
            ThemeMode.Light,
            NotificationExceptionsUiState(NotificationScope.ChannelChats, ExceptionsState.Empty),
        )

    @Test
    fun loadingLight() =
        capture("loadingLight", ThemeMode.Light, NotificationExceptionsUiState(NotificationScope.PrivateChats))

    @Test
    fun errorDark() =
        capture(
            "errorDark",
            ThemeMode.Dark,
            NotificationExceptionsUiState(
                NotificationScope.GroupChats,
                ExceptionsState.Error(DomainError.FloodWait(60)),
            ),
        )

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: NotificationExceptionsUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        NotificationExceptionsScreen(state = state, onEvent = {}, onBack = {})
    }
}
