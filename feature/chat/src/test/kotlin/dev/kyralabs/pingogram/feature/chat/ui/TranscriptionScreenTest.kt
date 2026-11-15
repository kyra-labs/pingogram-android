package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.TranscriptionPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Voice message transcription on screen (`docs/DESIGN_SYSTEM.md` §6, board 28): the button, "Show more", "Hide text",
 * "Try again" and the link to Android's language settings send their events; screen readers hear the transcript in the
 * bubble's description and transcribe through its custom actions; every control has a name and a touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TranscriptionScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val allEvents = mutableListOf<ChatEvent>()

    /** What transcription was sent; the list's own events (visible messages) are left out. */
    private val events get() = allEvents.filterIsInstance<TranscriptionEvent>()

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { ChatScreen(state = state, onEvent = { allEvents += it }, onBack = {}) }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ) = RuntimeEnvironment.getApplication().getString(id, *args)

    @Test
    fun `the button transcribes, and the transcript's text button hides it`() {
        setScreen(TranscriptionPreviewData.transcribing)

        composeRule
            .onAllNodesWithContentDescription(string(R.string.transcription_transcribe), useUnmergedTree = true)[0]
            .performClick()
        composeRule.onNodeWithText(string(R.string.transcription_hide), useUnmergedTree = true).performClick()

        assertEquals(listOf(TranscriptionEvent.Transcribe(2), TranscriptionEvent.Hide(3)), events)
    }

    @Test
    fun `a long transcript shows more, a failure tries again, a missing language opens the settings`() {
        setScreen(TranscriptionPreviewData.states)

        composeRule.onNodeWithText(string(R.string.transcription_show_more), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(string(R.string.transcription_try_again), useUnmergedTree = true).performClick()
        composeRule
            .onNodeWithText(string(R.string.transcription_language_settings), useUnmergedTree = true)
            .performClick()

        assertEquals(
            listOf(
                TranscriptionEvent.ShowMore(5),
                TranscriptionEvent.Transcribe(7),
                TranscriptionEvent.OpenLanguageSettings,
            ),
            events,
        )
    }

    @Test
    fun `screen readers hear the transcript and transcribe through the bubble's actions`() {
        setScreen(TranscriptionPreviewData.transcribing)

        val transcribed =
            composeRule
                .onNode(hasContentDescription(string(R.string.transcription_a11y_text, ""), substring = true))
                .fetchSemanticsNode()
        val description =
            transcribed.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString()
                .orEmpty()
        assertTrue(description, description.contains("Reached the station"))

        val working =
            composeRule
                .onAllNodesWithContentDescription(string(R.string.transcription_transcribing), substring = true)
                .fetchSemanticsNodes()
        assertTrue(working.isNotEmpty())
        val actions =
            composeRule
                .onAllNodes(hasContentDescription("6:02 PM", substring = true))
                .fetchSemanticsNodes()
                .flatMap { node -> node.config.getOrNull(SemanticsActions.CustomActions).orEmpty() }
        val transcribe = actions.first { it.label == string(R.string.transcription_transcribe) }
        transcribe.action.invoke()
        assertEquals(listOf(TranscriptionEvent.Transcribe(2)), events)
    }

    @Test
    fun `every control of the transcripts has a name and a touch target`() {
        setScreen(TranscriptionPreviewData.states)
        composeRule.assertAccessible()
    }

    @Test
    fun `every control of round video messages has a name and a touch target`() {
        setScreen(TranscriptionPreviewData.roundVideos)
        composeRule.assertAccessible()
    }
}
