package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.sticker.CustomEmojiRenderer
import dev.kyralabs.pingogram.core.ui.sticker.LocalCustomEmoji
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.translation.BubbleTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.withTranslations
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the conversation composes again while the user scrolls, as messages come in, when someone starts typing and
 * when a bubble's time is tapped to show (`docs/PERFORMANCE.md` §4: no recomposition of what stays on screen), counted
 * with [RecompositionCounter] frame by frame on the test clock. The counter starts before the screen first composes,
 * so it knows every composable's first composition and counts every later one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatRecompositionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()

    private lateinit var listState: LazyListState

    private var state by mutableStateOf(ChatUiState())

    /** False until the counter runs, so it sees the screen's first composition. */
    private var shown by mutableStateOf(false)

    @Test
    fun `scrolling the messages recomposes none of the bubbles`() {
        setScreen(messages(MESSAGES))

        repeat(SCROLL_FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(SCROLL_STEP) }
            frame()
        }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > ENTRIES_ON_SCREEN)
        assertEquals(emptyList<String>(), counter.recomposedOwn.filter { it.isItemFunction() })
        // Only the jump button, springing in once the newest message leaves the screen: nothing in every frame.
        val others = counter.recomposedOwn.filterNot { JUMP_BUTTON in it || it.startsWith(SCREEN_LAMBDA) }
        assertEquals(emptyList<String>(), others)
        assertTrue(counter.recomposedOwn.toString(), counter.recomposedOwn.size < SCROLL_FRAMES / RARE)
    }

    @Test
    fun `swiping a bubble to reply recomposes none of the bubbles`() {
        setScreen(messages(MESSAGES).copy(shortcuts = MessageShortcuts(canReply = true)))
        val bubble = composeRule.onNode(hasContentDescription("Message $MESSAGES", substring = true))

        bubble.performTouchInput { down(centerLeft + Offset(SWIPE_START.toPx(), 0f)) }
        frame()
        repeat(SWIPE_FRAMES) {
            bubble.performTouchInput { moveBy(Offset(SWIPE_STEP.toPx(), 0f)) }
            frame()
        }
        bubble.performTouchInput { up() }
        repeat(SETTLE_FRAMES) { frame() }

        assertEquals(emptyList<String>(), counter.recomposedOwn.filter { it.isItemFunction() })
    }

    @Test
    fun `a new message composes only its own bubble`() {
        val before = messages(MESSAGES)
        setScreen(before)

        state = before.withNewest(bubble(id = MESSAGES + 1L, outgoing = true))
        repeat(SETTLE_FRAMES) { frame() }

        // The screen and the list compose again for the new items; the bubbles on screen skip.
        val functions = counter.recomposedOwnFunctions
        assertTrue(functions.toString(), functions.any { "MessageList" in it })
        assertEquals(emptyList<String>(), functions.filter { it.isItemFunction() })
    }

    @Test
    fun `a new message above a sponsored message composes neither it nor the bubbles on screen`() {
        val ad = ChatItem.Sponsored(SponsoredPreviewData.text, UiText.Plain("Today"))
        val before = messages(MESSAGES).withNewest(ad)
        setScreen(before)

        // Where the sponsored message is pinned below every message, a new one comes above it.
        val content = before.content as ChatContent.Content
        val items = listOf(ad, bubble(id = MESSAGES + 1L, outgoing = true)) + content.items.drop(1)
        state = before.copy(content = content.copy(items = items.toImmutableList()))
        repeat(SETTLE_FRAMES) { frame() }

        val functions = counter.recomposedOwnFunctions
        assertTrue(functions.toString(), functions.any { "MessageList" in it })
        assertEquals(emptyList<String>(), functions.filter { it.isItemFunction() })
    }

    @Test
    fun `showing a bubble's time composes only that bubble`() {
        val before = messages(MESSAGES)
        setScreen(before)

        val newest = before.bubbles().first()
        state = before.replacing(newest.copy(showsTime = !newest.showsTime))
        repeat(SETTLE_FRAMES) { frame() }

        val functions = counter.recomposedOwnFunctions
        assertEquals(functions.toString(), 1, functions.count { it.endsWith(".MessageBubbleRow") })
        assertEquals(functions.toString(), 1, functions.count { it.endsWith(".MessageBubble") })
    }

    @Test
    fun `translating a bubble composes only that bubble`() {
        val before = messages(MESSAGES)
        setScreen(before)

        val newest = before.bubbles().first()
        val translation = BubbleTranslation.Shown(FormattedText("Translated", persistentListOf()), "es")
        state = before.withTranslations(persistentMapOf(newest.id to translation))
        repeat(SETTLE_FRAMES) { frame() }

        val functions = counter.recomposedOwnFunctions
        assertEquals(functions.toString(), 1, functions.count { it.endsWith(".MessageBubbleRow") })
        assertEquals(functions.toString(), 1, functions.count { it.endsWith(".MessageBubble") })
    }

    @Test
    fun `new votes in a poll compose only its bubble, once, while its bars animate in the draw phase`() {
        val before =
            messages(MESSAGES).withNewest(pollBubble(MESSAGES + 2L, votes = 12)).withNewest(
                pollBubble(MESSAGES + 1L, votes = 30),
            )
        setScreen(before)

        state = before.replacing(pollBubble(MESSAGES + 1L, votes = 50))
        repeat(SETTLE_FRAMES) { frame() }

        val functions = counter.recomposedOwnFunctions
        assertEquals(functions.toString(), 1, functions.count { it.endsWith(".MessageBubbleRow") })
        assertEquals(functions.toString(), 1, functions.count { it.endsWith("MessagePollKt.MessagePoll") })
    }

    @Test
    fun `someone starting to type composes the top bar's title and none of the bubbles`() {
        val before = messages(MESSAGES)
        setScreen(before)

        val typing = ChatSubtitle.Action(UiText.Plain("Ada is typing…"))
        state = before.copy(header = before.header?.copy(subtitle = typing))
        repeat(SETTLE_FRAMES) { frame() }

        // The list runs again (it takes the whole state), but every bubble on screen skips.
        val functions = counter.recomposedOwnFunctions
        assertTrue(functions.toString(), functions.any { it.endsWith("ChatTopBarKt.ChatTitle") })
        assertEquals(emptyList<String>(), functions.filter { it.isItemFunction() })
    }

    @Test
    fun `the counter sees a bubble whose reactions change`() {
        val before = messages(MESSAGES)
        setScreen(before)

        val newest = before.bubbles().first()
        state = before.replacing(newest.copy(reactions = ChatPreviewData.reactions(thumbsUp)))
        repeat(SETTLE_FRAMES) { frame() }

        assertNotEquals(emptyList<String>(), counter.recomposedOwnFunctions.filter { it.isItemFunction() })
    }

    @Test
    fun `loading custom emoji composes none of the bubbles that show them`() {
        val renderer = testCustomEmojiRenderer(known = emptyMap())
        setScreen(messages(MESSAGES, customEmoji = true), renderer)

        renderer.emoji = CustomEmojiFakes.all.toImmutableMap()
        repeat(SETTLE_FRAMES) { frame() }

        // The pictures load and draw in the bubbles' draw phase: nothing composes again.
        assertEquals(emptyList<String>(), counter.recomposedOwn.filter { it.isItemFunction() })
        assertEquals(emptyList<String>(), counter.recomposedOwn.filterNot { it.startsWith(SCREEN_LAMBDA) })
    }

    private fun setScreen(
        initial: ChatUiState,
        customEmoji: CustomEmojiRenderer? = null,
    ) {
        state = initial
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                if (shown) {
                    listState = rememberLazyListState()
                    CompositionLocalProvider(LocalCustomEmoji provides customEmoji) {
                        ChatScreen(state = state, onEvent = {}, onBack = {}, listState = listState)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        counter.start()
        shown = true
        repeat(SETTLE_FRAMES) { frame() }
        counter.reset()
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    /** The composables of the list's entries: bubbles and what they show, and the other rows. */
    private fun String.isItemFunction(): Boolean =
        ITEM_FILES.any { "ui.$it." in this || "ui.content.$it." in this } && FLOATING_PILL !in this

    /**
     * [count] messages, newest first, in runs of incoming and outgoing ones, some with reactions or replies; with
     * [customEmoji], every third has a custom emoji in its text and every fifth a custom emoji reaction.
     */
    private fun messages(
        count: Int,
        customEmoji: Boolean = false,
    ): ChatUiState {
        val items =
            (count downTo 1).map { id ->
                val bubble = bubble(id.toLong(), outgoing = id / RUN % 2 == 0)
                if (customEmoji) bubble.withCustomEmoji() else bubble
            }
        return ChatUiState(
            header = ChatPreviewData.privateChat.header,
            content = ChatContent.Content(items.toImmutableList(), reachedStart = true, isLoadingOlder = false, null),
            composer = ChatPreviewData.canWrite,
        )
    }

    private fun bubble(
        id: Long,
        outgoing: Boolean,
    ): ChatItem.Bubble =
        ChatPreviewData.bubble(
            id = id,
            text = if (id % 4 == 0L) "Message $id, a longer one that takes a second line or so" else "Message $id",
            time = "9:41 AM",
            isOutgoing = outgoing,
            sender = UiText.Plain("Ada"),
            reply = if (id % 7 == 0L) quoteOf(id - 1) else null,
            reactions = if (id % 5 == 0L) ChatPreviewData.reactions(thumbsUp, heart) else persistentListOf(),
        )

    /** A public poll's results with [votes] for its second answer, as message [id]. */
    private fun pollBubble(
        id: Long,
        votes: Int,
    ): ChatItem.Bubble {
        val content = ContentPreviewData.poll(voted = true, publicVoters = true)
        val total = content.poll.totalVoterCount - content.poll.options[1].voterCount + votes
        val options =
            content.poll.options.map { option ->
                val count = if (option.index == 1) votes else option.voterCount
                option.copy(voterCount = count, votePercentage = count * PERCENT / total)
            }
        val poll = content.copy(poll = content.poll.copy(options = options.toImmutableList(), totalVoterCount = total))
        return ChatPreviewData.bubble(
            id = id,
            text = "",
            time = "9:41 AM",
            sender = UiText.Plain("Ada"),
            content = poll,
        )
    }

    private fun ChatItem.Bubble.withCustomEmoji(): ChatItem.Bubble =
        copy(
            text = if (id % 3 == 0L) CustomEmojiFakes.text("${text.text} ⭐", "⭐" to CustomEmojiFakes.STAR) else text,
            reactions =
                if (id % 5 == 0L) {
                    ChatPreviewData.reactions(ReactionChip(ReactionLabel.CustomEmoji(CustomEmojiFakes.LEAF), 1, false))
                } else {
                    reactions
                },
        )

    private fun quoteOf(id: Long) = ReplyQuote(UiText.Plain("Ada"), senderId = 2, UiText.Plain("Message $id"), id)

    private fun ChatUiState.bubbles() = (content as ChatContent.Content).items.filterIsInstance<ChatItem.Bubble>()

    private fun ChatUiState.withNewest(item: ChatItem): ChatUiState {
        val content = content as ChatContent.Content
        return copy(content = content.copy(items = (listOf(item) + content.items).toImmutableList()))
    }

    private fun ChatUiState.replacing(bubble: ChatItem.Bubble): ChatUiState {
        val content = content as ChatContent.Content
        val items = content.items.map { if (it.key == bubble.key) bubble else it }
        return copy(content = content.copy(items = items.toImmutableList()))
    }

    private companion object {
        const val MESSAGES = 200
        const val RUN = 3
        const val PERCENT = 100
        const val ENTRIES_ON_SCREEN = 10

        const val SCROLL_FRAMES = 120
        const val SCROLL_STEP = 25f

        const val SETTLE_FRAMES = 60

        /** A swipe to reply past its distance: where it starts on the bubble, its steps, one per frame. */
        val SWIPE_START = 24.dp
        val SWIPE_STEP = 8.dp
        const val SWIPE_FRAMES = 20

        /** At most one recomposition in this many frames counts as rare, nothing like one in every frame. */
        const val RARE = 10

        const val JUMP_BUTTON = "JumpToBottomButtonKt"

        /** The lambdas of the screen's own file: the `Scaffold`'s slots, e.g. the jump button's. */
        const val SCREEN_LAMBDA = "dev.kyralabs.pingogram.feature.chat.ui.ChatScreenKt$$"

        val thumbsUp = ReactionChip(ReactionLabel.Emoji("👍"), count = 2, isChosen = false)
        val heart = ReactionChip(ReactionLabel.Emoji("❤️"), count = 1, isChosen = true)

        /** Over the list, not one of its entries. */
        const val FLOATING_PILL = "FloatingDatePill"

        /** The files whose composables draw the list's entries. */
        val ITEM_FILES =
            listOf(
                "MessageBubbleKt",
                "BubbleFramesKt",
                "BubblePartsKt",
                "BubbleMetaKt",
                "MessageContentBodyKt",
                "MessagePollKt",
                "ChatPillsKt",
                "SponsoredMessageCardKt",
            )
    }
}
