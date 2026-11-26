package dev.kyralabs.pingogram.feature.settings.ui.notifications

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsScreen
import dev.kyralabs.pingogram.feature.settings.ui.NotificationSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Daily digest's rows (`REQUIREMENTS.md` FR-X23): its switch, and its time, dimmed while it is off. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DailyDigestGroupTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<DailyDigestEvent>()

    private fun setScreen(dailyDigest: DailyDigestUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                NotificationSettingsScreen(
                    state = NotificationSettingsUiState(),
                    onEvent = {},
                    onOpen = {},
                    onBack = {},
                    dailyDigest = dailyDigest,
                    onDailyDigestEvent = { events += it },
                )
            }
        }
    }

    private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun switchRow() =
        composeRule.onNode(
            hasText(string(R.string.settings_daily_digest_summary)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        )

    @Test
    fun `off at first, its switch turns it on and its time is dimmed`() {
        setScreen(DailyDigestUiState(DailyDigestSettings()))

        switchRow().performScrollTo().assertIsOff()
        composeRule.onNodeWithText(string(R.string.settings_daily_digest_time)).assertIsNotEnabled()
        composeRule.assertAccessible()
        switchRow().performClick()

        assertEquals(listOf<DailyDigestEvent>(DailyDigestEvent.SetEnabled(true)), events)
    }

    @Test
    fun `on, its time opens the picker`() {
        setScreen(DailyDigestUiState(DailyDigestSettings(enabled = true)))

        composeRule.onNodeWithText(string(R.string.settings_daily_digest_time)).performScrollTo().performClick()

        assertEquals(listOf<DailyDigestEvent>(DailyDigestEvent.PickTime), events)
    }

    @Test
    fun `nothing shows until the settings are read`() {
        setScreen(DailyDigestUiState())

        composeRule.onNodeWithText(string(R.string.settings_daily_digest_summary)).assertDoesNotExist()
    }
}
