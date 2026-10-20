package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the Notifications page sends: each switch (a whole row, read as a switch) sends its scope's defaults with its
 * change, the banner turns notifications on, the rows open Android's channel settings and the exceptions, "Keep
 * connected" shows only where the build offers it. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NotificationSettingsScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<NotificationSettingsEvent>()
    private val opened = mutableListOf<NotificationSettingsPage>()

    private fun setScreen(state: NotificationSettingsUiState = SettingsPreviewData.notifications) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                NotificationSettingsScreen(
                    state = state,
                    onEvent = { events += it },
                    onOpen = { opened += it },
                    onBack = {},
                )
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    /** The switch rows titled [title], one per scope that has it, in the page's order. */
    private fun switchRows(
        @StringRes title: Int,
    ) = composeRule.onAllNodes(
        hasText(string(title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
    )

    @Test
    fun `the switches show the defaults and send their change`() {
        setScreen()

        // Private chats on, groups muted.
        switchRows(R.string.settings_notifications_notify)[0].assertIsOn()
        switchRows(R.string.settings_notifications_notify)[1].performScrollTo().assertIsOff().performClick()
        switchRows(R.string.settings_notifications_preview)[0].performScrollTo().performClick()

        val groups =
            ScopeNotificationSettings(isMuted = true, showPreview = true, pinnedMessages = true, mentions = true)
        assertEquals(
            listOf(
                NotificationSettingsEvent.ChangeScope(NotificationScope.GroupChats, groups.copy(isMuted = false)),
                NotificationSettingsEvent.ChangeScope(
                    NotificationScope.PrivateChats,
                    groups.copy(isMuted = false, showPreview = false),
                ),
            ),
            events,
        )
    }

    @Test
    fun `mentions show for groups only, pinned messages for groups and channels`() {
        setScreen()

        assertEquals(1, switchRows(R.string.settings_notifications_mentions).fetchSemanticsNodes().size)
        assertEquals(2, switchRows(R.string.settings_notifications_pinned).fetchSemanticsNodes().size)
    }

    @Test
    fun `sound and exceptions open the scope's channel and its exceptions`() {
        setScreen()

        composeRule
            .onAllNodesWithText(
                string(R.string.settings_notifications_sound),
            )[2]
            .performScrollTo()
            .performClick()
        composeRule
            .onAllNodesWithText(
                string(R.string.settings_notifications_exceptions),
            )[1]
            .performScrollTo()
            .performClick()

        assertEquals(listOf(NotificationSettingsEvent.OpenChannelSettings(NotificationScope.ChannelChats)), events)
        assertEquals(listOf(NotificationSettingsPage.Exceptions(NotificationScope.GroupChats)), opened)
    }

    @Test
    fun `the banner of blocked notifications turns them on`() {
        setScreen(SettingsPreviewData.notificationsBlocked)

        composeRule.onNodeWithText(string(R.string.settings_notifications_off)).performClick()

        assertEquals(listOf(NotificationSettingsEvent.TurnOnNotifications), events)
    }

    @Test
    fun `keep connected is a switch where the build offers it, and absent otherwise`() {
        setScreen()

        switchRows(R.string.settings_keep_connected)[0].performScrollTo().assertIsOff().performClick()

        assertEquals(listOf(NotificationSettingsEvent.SetKeepConnected(true)), events)
    }

    @Test
    fun `builds without keep connected do not show it`() {
        setScreen(SettingsPreviewData.notifications.copy(isKeepConnectedOffered = false))

        assertEquals(
            0,
            composeRule.onAllNodesWithText(string(R.string.settings_keep_connected)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `reset asks for confirmation through the view model`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.settings_notifications_reset)).performScrollTo().performClick()

        assertEquals(listOf(NotificationSettingsEvent.Reset), events)
    }
}
