package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ScheduledPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The scheduled messages in use (`REQUIREMENTS.md` FR-C5): the conversation's bar opens them, a bubble's tap and long
 * press open its actions, the sheet's actions, the delete confirmation, the pickers, the composer's edit, and that
 * every control has a name and a touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ScheduledMessagesScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ScheduledEvent>()

    private fun show(
        state: ScheduledMessagesUiState,
        text: String = "",
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ScheduledMessagesScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    composerText = ComposerText(FormattedText(text, persistentListOf())),
                )
                state.actions?.let { ScheduledActionsBottomSheet(it, onEvent = { event -> events += event }) }
                state.deleteDialog?.let {
                    ScheduledDeleteAlert(
                        onConfirm = { events += ScheduledEvent.ConfirmDelete },
                        onDismiss = { events += ScheduledEvent.DismissDelete },
                    )
                }
            }
        }
    }

    @Test
    fun `the conversation's bar opens the scheduled messages, and has a name and a touch target`() {
        var opened = 0
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(
                    state = ScheduledPreviewData.conversation,
                    onEvent = {},
                    onBack = {},
                    onOpenScheduledMessages = { opened++ },
                )
            }
        }

        composeRule.onNodeWithText("3 scheduled messages", substring = true).assertIsDisplayed().performClick()

        assertEquals(1, opened)
        composeRule.assertAccessible()
    }

    @Test
    fun `without a way to open the list the conversation shows no bar`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = ScheduledPreviewData.conversation, onEvent = {}, onBack = {})
            }
        }

        composeRule.onNodeWithText("3 scheduled messages", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a tap or a long press on a bubble opens its actions`() {
        show(ScheduledPreviewData.content)

        composeRule.onNodeWithContentDescription("Daily stand-up", substring = true).performClick()
        composeRule.onNodeWithContentDescription("tickets", substring = true).performTouchInput { longClick() }

        assertEquals(listOf(ScheduledEvent.ShowActions(14), ScheduledEvent.ShowActions(11)), events)
    }

    @Test
    fun `screen readers hear when each message goes out`() {
        show(ScheduledPreviewData.content)

        composeRule.onNodeWithContentDescription("Sends 8:50 AM, repeats: Daily", substring = true).assertExists()
        // The name is isolated (bidiIsolated) in the sentence.
        composeRule
            .onNodeWithContentDescription(
                "Sends when \u2068Ada Lovelace\u2069 is online",
                substring = true,
            ).assertExists()
    }

    @Test
    fun `the actions sheet sends the chosen action, and has names and touch targets`() {
        show(ScheduledPreviewData.withActions)
        composeRule.onNodeWithText("Sends Tomorrow, 8:50 AM").assertIsDisplayed()
        composeRule.assertAccessible()

        composeRule.onNodeWithText(string(R.string.scheduled_action_send_now)).performClick()
        composeRule.waitForIdle()

        assertEquals(ScheduledEvent.Act(14, ScheduledAction.SendNow), events.last())
    }

    @Test
    fun `the delete confirmation confirms or cancels`() {
        show(ScheduledPreviewData.deleting)

        composeRule.onNodeWithText(string(R.string.scheduled_delete_cancel)).performClick()
        composeRule.onNodeWithText(string(R.string.scheduled_delete_confirm)).performClick()

        assertEquals(listOf(ScheduledEvent.DismissDelete, ScheduledEvent.ConfirmDelete), events)
    }

    @Test
    fun `the pickers start at the message's time and give the new one`() {
        val now = Instant.parse("2030-03-01T09:00:00Z")
        val sendDate = Instant.parse("2030-04-15T08:50:00Z").epochSecond
        val picked = mutableListOf<Pair<Long, Long>>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                RescheduleDialogHost(
                    dialog = RescheduleDialog(14, sendDate),
                    onReschedule = { id, at -> picked += id to at },
                    onDismiss = {},
                    now = { now },
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.composer_schedule_next)).performClick()
        composeRule.onNodeWithText(string(R.string.composer_schedule_confirm)).performClick()

        // The same time, picked again.
        assertEquals(listOf(14L to sendDate), picked)
    }

    @Test
    fun `the composer saves or cancels the edit`() {
        show(ScheduledPreviewData.editing, text = "Edited")

        composeRule.onNodeWithContentDescription(string(R.string.composer_edit_save)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.composer_edit_cancel)).performClick()

        assertEquals(listOf(ScheduledEvent.SaveEdit, ScheduledEvent.CancelEdit), events)
    }

    @Test
    fun `failed, Try again loads again`() {
        show(ScheduledPreviewData.error)

        composeRule.onNodeWithText(string(R.string.scheduled_retry)).performClick()

        assertEquals(listOf<ScheduledEvent>(ScheduledEvent.Retry), events)
    }

    @Test
    fun `every control of the list has a name and a touch target`() {
        show(ScheduledPreviewData.editing, text = "Edited")

        composeRule.assertAccessible()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
