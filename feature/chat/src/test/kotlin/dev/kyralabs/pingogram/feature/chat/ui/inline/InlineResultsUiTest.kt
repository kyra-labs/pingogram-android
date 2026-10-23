package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.InlineBotEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.InlinePreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * An inline bot's results above the composer in use: tapping one, a hardware keyboard's arrows, Enter and Escape,
 * screen readers' items and actions, the bot's button, "Try again", and the placeholder in the field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalTestApi::class)
class InlineResultsUiTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val allEvents = mutableListOf<InlineBotEvent>()

    /** What the user did; the next page the list asks for as it shows its end is left out. */
    private val events get() = allEvents.filterNot { it == InlineBotEvent.LoadMore }

    private var inline by mutableStateOf<InlineBotUiState?>(null)

    private fun setComposer(
        state: InlineBotUiState,
        typed: String = "@${state.username} ${state.query}",
    ) {
        inline = state
        val text = ComposerText(FormattedText(typed, persistentListOf()))
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    PreviewImages {
                        ChatBottomBar(
                            composer = ComposerUiState.Available(canSchedule = true),
                            text = text,
                            onSend = {},
                            onFocusChanged = {},
                            inline = InlineBotInput(inline, canSchedule = true) { allEvents += it },
                        )
                    }
                }
            }
        }
    }

    /** The text field, focused, as for typing on a hardware keyboard. */
    private fun field() = composeRule.onNode(hasSetTextAction()).also { it.requestFocus() }

    private fun panel() = composeRule.onNodeWithTag(INLINE_RESULTS_TEST_TAG)

    @Test
    fun `a tapped result is sent`() {
        setComposer(InlinePreviewData.gifResults)

        composeRule.onNodeWithContentDescription("GIF, Cat 1").performClick()

        assertEquals(listOf(InlineBotEvent.Send(1, "gif1")), events)
    }

    @Test
    fun `the arrow keys pick a result, Enter sends it`() {
        setComposer(InlinePreviewData.rowResults)

        field().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }

        assertEquals(listOf(InlineBotEvent.Send(1, "a2")), events)
    }

    @Test
    fun `Enter without a pick is the field's`() {
        setComposer(InlinePreviewData.gifResults)

        field().performKeyInput { pressKey(Key.Enter) }

        assertTrue(events.isEmpty())
    }

    @Test
    fun `Escape hides the results`() {
        setComposer(InlinePreviewData.gifResults)
        panel().assertIsDisplayed()

        field().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        panel().assertDoesNotExist()
    }

    @Test
    fun `screen readers hear each result as one button with its kind and title, and the count as the pane`() {
        setComposer(InlinePreviewData.rowResults)

        composeRule
            .onNodeWithContentDescription("Article, Cat, The cat is a small domesticated carnivorous mammal.")
            .assertHasClickAction()
        panel().assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "@wiki, 5 results"))
    }

    @Test
    fun `screen readers send without sound or schedule through custom actions`() {
        setComposer(InlinePreviewData.gifResults)

        performCustomAction("GIF, Cat 0", string(R.string.composer_send_silently))

        assertEquals(listOf(InlineBotEvent.Send(1, "gif0", SendOptions(silent = true))), events)
        val node = composeRule.onNodeWithContentDescription("GIF, Cat 0").fetchSemanticsNode()
        val labels = node.config[SemanticsActions.CustomActions].map { it.label }
        assertTrue(string(R.string.composer_schedule) in labels)
    }

    @Test
    fun `the bot's button opens it`() {
        setComposer(InlinePreviewData.placeResults)

        composeRule.onNodeWithText("Sign in to save places").performClick()

        assertEquals(listOf(InlineBotEvent.OpenButton), events)
    }

    @Test
    fun `a flood wait waits before Try again, a network error does not`() {
        setComposer(InlinePreviewData.floodWait)
        composeRule.onNodeWithText(string(R.string.inline_results_retry)).assertIsNotEnabled()

        inline = InlinePreviewData.failed
        composeRule.onNodeWithText(string(UiR.string.error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.inline_results_retry)).performClick()

        assertEquals(listOf(InlineBotEvent.Retry), events)
    }

    @Test
    fun `the bot's placeholder follows its username while the query is empty`() {
        setComposer(InlinePreviewData.gifSuggestions)

        composeRule.onNodeWithText("Search GIFs…").assertIsDisplayed()
    }

    @Test
    fun `nothing found says so`() {
        setComposer(InlinePreviewData.empty)

        composeRule.onNodeWithText(string(R.string.inline_results_empty)).assertIsDisplayed()
    }

    @Test
    fun `the location question shares or declines`() {
        val answers = mutableListOf<InlineBotEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                InlineLocationContent(
                    InlinePreviewData.locationSheet,
                    onNotNow = { answers += InlineBotEvent.AnswerLocation(false) },
                    onShare = { answers += InlineBotEvent.AnswerLocation(true) },
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.inline_location_share)).performClick()
        composeRule.onNodeWithText(string(R.string.location_not_now)).performClick()

        assertEquals(listOf(InlineBotEvent.AnswerLocation(true), InlineBotEvent.AnswerLocation(false)), answers)
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    /** Does custom action [label] of the result described [description], as TalkBack would. */
    private fun performCustomAction(
        description: String,
        label: String,
    ) {
        val node = composeRule.onNodeWithContentDescription(description).fetchSemanticsNode()
        val action =
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }
}
