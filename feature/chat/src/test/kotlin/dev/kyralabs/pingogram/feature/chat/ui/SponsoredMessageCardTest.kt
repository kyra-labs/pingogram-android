package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The sponsored message's card (`REQUIREMENTS.md` §2): its label, its button and info button, how screen readers hear
 * it, and, in the conversation, when it is reported as viewed: once wholly on screen, never as a shown message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SponsoredMessageCardTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private val card = SponsoredPreviewData.text

    private fun setCard(card: SponsoredCard) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { SponsoredMessageCard(card, onEvent = { events += it }) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `an ad is labelled sponsored, a recommended channel recommended`() {
        var shown by mutableStateOf(card)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { SponsoredMessageCard(shown, onEvent = { events += it }) }
        }
        composeRule.onNodeWithText(string(R.string.sponsored_label), useUnmergedTree = true).assertExists()

        shown = SponsoredPreviewData.recommended
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.sponsored_label_recommended), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(string(R.string.sponsored_label), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the advertiser's button opens the ad`() {
        setCard(card)

        composeRule.onNodeWithText(card.buttonText).assertHasClickAction().performClick()

        assertEquals(listOf<ChatEvent>(SponsoredEvent.Open(card.id)), events)
    }

    @Test
    fun `the info button opens About this ad, and says so`() {
        setCard(card)

        composeRule
            .onNodeWithContentDescription(string(R.string.sponsored_about))
            .assertContentDescriptionEquals(string(R.string.sponsored_about))
            .performClick()

        assertEquals(listOf<ChatEvent>(SponsoredEvent.ShowInfo(card.id)), events)
    }

    @Test
    fun `screen readers hear the label, the sponsor and the text as one, with About this ad as an action`() {
        setCard(card)

        val node =
            composeRule
                .onNode(hasText(string(R.string.sponsored_label)) and hasText(card.title) and hasText(card.text.text))
                .fetchSemanticsNode()
        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf(string(R.string.sponsored_about)), actions.map { it.label })

        composeRule.runOnIdle { actions.single().action() }
        assertEquals(listOf<ChatEvent>(SponsoredEvent.ShowInfo(card.id)), events)
        assertTrue(
            node.config
                .getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .isNotEmpty(),
        )
    }

    @Test
    fun `in the conversation, a card wholly on screen is viewed once, and never as a shown message`() {
        lateinit var listState: LazyListState
        val ad = ChatItem.Sponsored(card, UiText.resource(R.string.chat_date_today))
        val bubbles = (40L downTo 1L).map { ChatPreviewData.bubble(it, "Message $it", time = "9:41 AM") }
        val content = ChatContent.Content((listOf(ad) + bubbles).toImmutableList(), true, false, null)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = rememberLazyListState()
                ChatScreen(
                    state = ChatUiState(header = ChatPreviewData.channel.header, content = content),
                    onEvent = { events += it },
                    onBack = {},
                    listState = listState,
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf(SponsoredEvent.Viewed(card.id)), events.filterIsInstance<SponsoredEvent.Viewed>())
        val shown = events.filterIsInstance<ChatEvent.MessagesVisible>().flatMap { it.messageIds }
        assertTrue(card.id !in shown)
    }

    @Test
    fun `a card partly out of view is not viewed until it is wholly on screen`() {
        lateinit var listState: LazyListState
        val ad = ChatItem.Sponsored(card, UiText.resource(R.string.chat_date_today))
        val bubbles = (40L downTo 1L).map { ChatPreviewData.bubble(it, "Message $it", time = "9:41 AM") }
        // Among older messages, so it starts out of view.
        val items = (bubbles.take(AD_INDEX) + ad + bubbles.drop(AD_INDEX)).toImmutableList()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = rememberLazyListState()
                ChatScreen(
                    state = ChatUiState(content = ChatContent.Content(items, true, false, null)),
                    onEvent = { events += it },
                    onBack = {},
                    listState = listState,
                )
            }
        }
        composeRule.waitForIdle()

        // The message above it at the bottom edge: the card is below, behind the composer at most.
        composeRule.runOnIdle { listState.requestScrollToItem(AD_INDEX + 1) }
        composeRule.waitForIdle()
        assertEquals(emptyList<SponsoredEvent.Viewed>(), events.filterIsInstance<SponsoredEvent.Viewed>())

        composeRule.runOnIdle { listState.requestScrollToItem(AD_INDEX) }
        composeRule.waitForIdle()
        assertEquals(listOf(SponsoredEvent.Viewed(card.id)), events.filterIsInstance<SponsoredEvent.Viewed>())
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val AD_INDEX = 20
    }
}
