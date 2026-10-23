package dev.kyralabs.pingogram.feature.media.story

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import dev.kyralabs.pingogram.feature.media.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The stories viewer's screen on Robolectric: the tap zones (mirrored right to left), holding, swiping to the next
 * chat, closing with back, a swipe down and the close button, the reply field, the reactions, and what screen readers
 * get. The screen is stateless; its events are recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryViewerScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<StoryViewerEvent>()
    private var closes = 0
    private var state by mutableStateOf<StoryViewerUiState>(StoryViewerPreviewData.photo)
    private val replyText = TextFieldState()

    private fun setScreen(
        initial: StoryViewerUiState = StoryViewerPreviewData.photo,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        state = initial
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                    StoryViewerScreen(
                        state = state,
                        onEvent = { events += it },
                        onClose = { closes++ },
                        replyText = replyText,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    /** The story, by its description: where it is among the chat's stories, and whose it is. */
    private fun story(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("Story 2 of 4 by", substring = true))

    private val taps get() = events.filter { it is StoryViewerEvent.Next || it is StoryViewerEvent.Previous }

    @Test
    fun `a tap on the start third shows the previous story, elsewhere the next`() {
        setScreen()

        story().performTouchInput { click(centerLeft.copy(x = width * 0.1f)) }
        story().performTouchInput { click(center) }
        story().performTouchInput { click(centerRight.copy(x = width * 0.9f)) }

        assertEquals(listOf(StoryViewerEvent.Previous, StoryViewerEvent.Next, StoryViewerEvent.Next), taps)
    }

    @Test
    fun `right to left, the start third is on the right`() {
        setScreen(layoutDirection = LayoutDirection.Rtl)

        story().performTouchInput { click(centerRight.copy(x = width * 0.9f)) }
        story().performTouchInput { click(centerLeft.copy(x = width * 0.1f)) }

        assertEquals(listOf(StoryViewerEvent.Previous, StoryViewerEvent.Next), taps)
    }

    @Test
    fun `holding pauses until released`() {
        setScreen()

        story().performTouchInput { down(center) }
        composeRule.waitForIdle()
        assertEquals(listOf(StoryViewerEvent.Hold(true)), events.filterIsInstance<StoryViewerEvent.Hold>())
        story().performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(
            listOf(StoryViewerEvent.Hold(true), StoryViewerEvent.Hold(false)),
            events.filterIsInstance<StoryViewerEvent.Hold>(),
        )
    }

    @Test
    fun `swiping sideways settles on the next chat, pausing while it moves`() {
        setScreen()

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        val settled = events.filterIsInstance<StoryViewerEvent.PosterSettled>()
        assertEquals(StoryViewerEvent.PosterSettled(1), settled.last())
        val paging = events.filterIsInstance<StoryViewerEvent.Paging>().map { it.moving }
        assertEquals(listOf(false, true, false), paging)
    }

    @Test
    fun `the view model's next chat scrolls the pager there`() {
        setScreen()

        state = StoryViewerPreviewData.photo.copy(current = 1)
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("Story 2 of 4 by", substring = true)).assertIsDisplayed()
        assertTrue(events.filterIsInstance<StoryViewerEvent.PosterSettled>().any { it.index == 1 })
    }

    @Test
    fun `back closes the viewer`() {
        setScreen()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun `a swipe down closes the viewer, pausing the story while dragged`() {
        setScreen()

        composeRule.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom) }
        composeRule.waitForIdle()

        assertEquals(1, closes)
        assertTrue(StoryViewerEvent.Hold(true) in events)
    }

    @Test
    fun `the close button closes the viewer`() {
        setScreen()

        composeRule.onNode(hasContentDescription(string(R.string.story_close))).performClick()
        composeRule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun `typing a reply pauses the story and sends it`() {
        setScreen()

        composeRule.onNode(hasSetTextAction()).performTextInput("Lovely!")
        composeRule.waitForIdle()
        assertTrue(StoryViewerEvent.ReplyFocused(true) in events)
        composeRule.onNode(hasContentDescription(string(R.string.story_send))).performClick()

        assertTrue(StoryViewerEvent.SendReply in events)
    }

    @Test
    fun `the reaction button opens the reactions, and a reaction is put on the story`() {
        setScreen()
        composeRule.onNode(hasContentDescription(string(R.string.story_react))).performClick()
        assertTrue(StoryViewerEvent.ReactionsOpen(true) in events)

        state = StoryViewerPreviewData.reacting
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Your reaction: 🔥. Change reaction")).assertIsDisplayed()
        composeRule.onNode(hasText("❤")).performClick()

        assertTrue(StoryViewerEvent.React(ReactionType.Emoji("❤")) in events)
    }

    @Test
    fun `a video with sound has a mute button`() {
        setScreen(StoryViewerPreviewData.video)

        composeRule.onNode(hasContentDescription(string(R.string.story_mute))).performClick()

        assertTrue(StoryViewerEvent.ToggleMute in events)
    }

    @Test
    fun `screen readers hear each story, move between stories and pause with actions, and every control is named`() {
        setScreen()

        val node = story().fetchSemanticsNode()
        assertEquals(
            "Story 2 of 4 by ⁨Ada Lovelace⁩",
            node.config[SemanticsProperties.ContentDescription].single(),
        )
        assertTrue(SemanticsProperties.LiveRegion in node.config)
        val actions = node.config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf(string(R.string.story_previous), string(R.string.story_pause)), actions)
        story().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(StoryViewerEvent.Next), taps)
        composeRule.assertAccessible()
    }

    @Test
    fun `a story that failed offers to try again`() {
        setScreen(StoryViewerPreviewData.failed)

        composeRule.onNode(hasText("Try again")).performClick()

        assertTrue(StoryViewerEvent.Retry in events)
    }
}
