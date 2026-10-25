package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedBar
import dev.kyralabs.pingogram.feature.chat.ui.preview.PinnedPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pinned messages bar in the conversation (`REQUIREMENTS.md` FR-C9): taps cycle through the pins as the view model
 * answers them, counted from the oldest; its buttons send their events, screen readers hear it as one item with the
 * buttons as actions, and dragging the list is reported.
 * The screen is stateless: a small reducer stands in for the view model.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PinnedMessagesBarTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    /** Newest first, as the view model has them; the bar counts them from the oldest. */
    private val pins = listOf(30L to "Newest", 21L to "Middle", 3L to "Oldest")

    private fun barAt(index: Int) =
        PinnedBar(
            messageId = pins[index].first,
            position = pins.size - 1 - index,
            count = pins.size,
            sender = UiText.Plain("Ada"),
            preview = UiText.Plain(pins[index].second),
            canUnpin = false,
        )

    private fun setScreen(initial: PinnedBar = barAt(0)) {
        var bar by mutableStateOf(initial)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(
                    state = PinnedPreviewData.groupWithPins.copy(pinnedBar = bar),
                    onEvent = { event ->
                        // Only the bar's: the list reports what it shows too.
                        if (event is PinnedEvent) events += event
                        // As the view model does: the bar moves on to the next older pin, back to the newest.
                        if (event == PinnedEvent.ShowCurrent) {
                            bar = barAt((pins.size - bar.position) % pins.size)
                        }
                    },
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `taps cycle through the pinned messages, newest last, back to the newest`() {
        setScreen()
        composeRule.onNodeWithText("Pinned message 3\u00A0of\u00A03").assertExists()

        composeRule.onNodeWithText("Pinned message 3\u00A0of\u00A03").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pinned message 2\u00A0of\u00A03").assertExists()
        composeRule.onNodeWithText("Ada: Middle").assertExists()

        composeRule.onNodeWithText("Pinned message 2\u00A0of\u00A03").performClick()
        composeRule.onNodeWithText("Pinned message 1\u00A0of\u00A03").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Pinned message 3\u00A0of\u00A03").assertExists()
        assertEquals(List(3) { PinnedEvent.ShowCurrent }, events)
    }

    @Test
    fun `the buttons open the list and close the bar`() {
        setScreen()

        composeRule.onNodeWithContentDescription("All pinned messages").performClick()
        composeRule.onNodeWithContentDescription("Hide pinned messages").performClick()

        assertEquals(listOf(PinnedEvent.OpenList, PinnedEvent.Close), events)
    }

    @Test
    fun `a single pin has no list button, and the user who may unpin gets that button`() {
        setScreen(barAt(0).copy(count = 1, canUnpin = true))

        composeRule
            .onAllNodes(
                hasContentDescription("All pinned messages"),
                useUnmergedTree = true,
            ).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Unpin message").performClick()
        composeRule.onNodeWithText("Pinned message").assertExists()

        assertEquals(listOf(PinnedEvent.Close), events)
    }

    @Test
    fun `screen readers hear one item whose actions are the buttons, which they skip`() {
        setScreen()
        composeRule.onAllNodes(hasContentDescription("Hide pinned messages")).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Hide pinned messages").assert(isHiddenFromAccessibility())
        val bar = composeRule.onNodeWithText("Pinned message 3\u00A0of\u00A03")

        val actions =
            bar
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
        assertEquals(listOf("All pinned messages", "Hide pinned messages"), actions.map { it.label })
        bar.performSemanticsAction(SemanticsActions.OnClick)
        actions.forEach { it.action() }

        assertEquals(listOf(PinnedEvent.ShowCurrent, PinnedEvent.OpenList, PinnedEvent.Close), events)
    }

    @Test
    fun `dragging the list tells the bar to follow it again`() {
        setScreen()

        composeRule.onNodeWithTag(MESSAGE_LIST_TEST_TAG).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(PinnedEvent.ListDragged, events.firstOrNull())
    }

    private fun isHiddenFromAccessibility() = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)

    private fun hasContentDescription(text: String) =
        SemanticsMatcher("contentDescription = $text") {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
        }
}
