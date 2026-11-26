package dev.kyralabs.pingogram.feature.settings.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsScreen
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsUiState
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * The Daily digest's rows on the Notifications page (`REQUIREMENTS.md` FR-X23), the kinds of chats still loading so
 * they show near the top: on, off with its time dimmed, and its time picker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DailyDigestScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onLight() = capture("onLight", ThemeMode.Light, DailyDigestUiState(DailyDigestSettings(enabled = true)))

    @Test
    fun offDark() = capture("offDark", ThemeMode.Dark, DailyDigestUiState(DailyDigestSettings()))

    @Test
    fun timePickerLight() =
        capture(
            "timePickerLight",
            ThemeMode.Light,
            DailyDigestUiState(DailyDigestSettings(enabled = true, time = LocalTime.of(7, 45)), isPickingTime = true),
        )

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        dailyDigest: DailyDigestUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        NotificationSettingsScreen(
            state = NotificationSettingsUiState(),
            onEvent = {},
            onOpen = {},
            onBack = {},
            dailyDigest = dailyDigest,
        )
    }
}
