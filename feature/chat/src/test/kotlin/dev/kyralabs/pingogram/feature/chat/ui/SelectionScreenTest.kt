package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SelectionPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Selection mode on the conversation screen (`REQUIREMENTS.md` FR-C6 "select"): taps and long presses toggle messages
 * instead of opening them or their actions, screen readers hear and toggle the selection, the selection bar counts and
 * closes, and the toolbar sends its actions. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SelectionScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap and a long press toggle a message`() {
        setScreen(SelectionPreviewData.selecting)

        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { click() }
        composeRule.onNodeWithContentDescription(OWN_FIRST, substring = true).performTouchInput { longClick() }

        assertEquals(
            listOf(SelectionEvent.Toggle(11), SelectionEvent.Toggle(9)),
            events.filterIsInstance<SelectionEvent.Toggle>(),
        )
        assertEquals(emptyList<ChatEvent>(), events.filterIsInstance<ChatEvent.ShowMessageActions>())
    }

    @Test
    @Config(qualifiers = COMPACT_TALL)
    fun `a tap on a photo selects it instead of opening it`() {
        setScreen(ChatPreviewData.mediaPhotos.copy(selection = SelectionPreviewData.selecting.selection))

        composeRule.onNodeWithContentDescription("Ada, 9:40 AM: Photo").performTouchInput {
            click(percentOffset(PHOTO_X, PHOTO_Y))
        }

        assertEquals(listOf(SelectionEvent.Toggle(48)), events.filterIsInstance<SelectionEvent.Toggle>())
        assertEquals(emptyList<ChatEvent>(), events.filterIsInstance<ChatEvent.OpenMedia>())
    }

    @Test
    fun `screen readers hear which messages are selected, and their click toggles them`() {
        setScreen(SelectionPreviewData.selecting)

        val selected = composeRule.onNodeWithContentDescription(ADA_LAST)
        selected.assertIsSelected()
        assertEquals(
            string(R.string.selection_deselect),
            selected.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        val other = composeRule.onNodeWithContentDescription(OWN_SENT, substring = true).fetchSemanticsNode()
        assertEquals(false, other.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(string(R.string.selection_select), other.config[SemanticsActions.OnClick].label)

        selected.performClick()

        assertEquals(listOf(SelectionEvent.Toggle(11)), events.filterIsInstance<SelectionEvent.Toggle>())
    }

    @Test
    fun `outside selection mode screen readers start it from a bubble`() {
        setScreen(ChatPreviewData.privateChat)
        val node = composeRule.onNodeWithContentDescription(ADA_LAST).fetchSemanticsNode()

        val select = node.config[SemanticsActions.CustomActions].single { it.label == string(R.string.selection_start) }
        composeRule.runOnIdle { select.action() }

        assertEquals(listOf(SelectionEvent.Select(11)), events.filterIsInstance<SelectionEvent.Select>())
    }

    @Test
    fun `the bar counts the selected messages and its close button ends selection mode`() {
        setScreen(SelectionPreviewData.selecting)

        composeRule.onNodeWithText(plural(R.plurals.selection_count, 3)).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.selection_clear)).performClick()

        assertEquals(listOf(SelectionEvent.Clear), events.filterIsInstance<SelectionEvent.Clear>())
    }

    @Test
    fun `the toolbar sends its actions, and the composer gives way to it`() {
        setScreen(SelectionPreviewData.selecting)

        listOf(R.string.selection_action_copy, R.string.message_action_forward, R.string.message_action_report)
            .forEach { composeRule.onNodeWithContentDescription(string(it)).performClick() }
        composeRule.onNodeWithContentDescription(string(R.string.message_action_delete)).performClick()

        assertEquals(
            listOf(MessageAction.Copy, MessageAction.Forward, MessageAction.Report, MessageAction.Delete)
                .map(SelectionEvent::Act),
            events.filterIsInstance<SelectionEvent.Act>(),
        )
        assertFalse(
            composeRule
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasSetTextAction(),
                ).fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    @Test
    fun `the report sheet sends the reason chosen`() {
        setScreen(SelectionPreviewData.reporting)

        composeRule.onNodeWithText("Scam or spam").performClick()

        assertEquals(
            listOf(SelectionEvent.ChooseReportOption(7)),
            events.filterIsInstance<SelectionEvent.ChooseReportOption>(),
        )
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private fun plural(
        id: Int,
        count: Int,
    ): String = RuntimeEnvironment.getApplication().resources.getQuantityString(id, count, count)

    private companion object {
        const val ADA_LAST = "Ada, 9:46 AM: See you there!"
        const val OWN_FIRST = "Sending the slides now"
        const val OWN_SENT = "You, 9:40 AM: Delivered, not read yet"

        // Where a photo of the album in ChatPreviewData.mediaPhotos is, within its row.
        const val PHOTO_X = 0.3f
        const val PHOTO_Y = 0.5f
    }
}
