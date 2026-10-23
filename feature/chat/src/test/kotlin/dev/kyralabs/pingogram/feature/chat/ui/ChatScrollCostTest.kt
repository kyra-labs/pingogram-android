package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.sticker.LocalCustomEmoji
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.management.ManagementFactory

/**
 * What the conversation's scroll frames cost on the JVM (`docs/PERFORMANCE.md` §9): the main thread's CPU time per
 * frame while 2,000 text messages (some with replies and reactions) scroll by [STEP] px a frame, after the JIT warmed
 * up, printed as percentiles, with the conversation's own list state ([rememberScrollingListState]) and with Compose's
 * default one. A measurement, not a check: it runs only with `-Ppingogram.scrollCost`, as `feature:chats`'
 * `ChatListScrollCostTest` describes. No media: Robolectric loads no pictures and draws nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatScrollCostTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    @Before
    fun onlyWhenAsked() = assumeTrue(System.getProperty(SCROLL_COST_PROPERTY).toBoolean())

    @Test
    fun `scroll frames with the conversation's own state`() = measure(scrollingState = true)

    @Test
    fun `scroll frames with Compose's default state`() = measure(scrollingState = false)

    /** A third of the messages hold two custom emoji, drawn as still pictures. */
    @Test
    fun `scroll frames with custom emoji and the conversation's own state`() =
        measure(scrollingState = true, customEmoji = true)

    /** The same messages with their fallback emoji only, as before custom emoji were drawn: the comparison. */
    @Test
    fun `scroll frames with custom emoji as fallback emoji only`() =
        measure(scrollingState = true, customEmoji = true, pictures = false)

    private fun measure(
        scrollingState: Boolean,
        customEmoji: Boolean = false,
        pictures: Boolean = customEmoji,
    ) {
        val state =
            ChatUiState(
                header = ChatPreviewData.privateChat.header,
                content =
                    ChatContent.Content(
                        (MESSAGES downTo 1).map { bubble(it, customEmoji) }.toImmutableList(),
                        reachedStart = true,
                        isLoadingOlder = false,
                        null,
                    ),
                composer = ChatPreviewData.canWrite,
            )
        // Their still pictures from a test renderer, which draws them at once.
        val renderer = if (pictures) testCustomEmojiRenderer(CUSTOM_EMOJI) else null
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = if (scrollingState) rememberScrollingListState() else rememberLazyListState()
                CompositionLocalProvider(LocalCustomEmoji provides renderer) {
                    ChatScreen(state = state, onEvent = {}, onBack = {}, listState = listState)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        repeat(WARMUP_RUNS) { scroll() }
        val frames = (1..MEASURED_RUNS).flatMap { scroll() }.sorted()
        println(
            "Scroll cost (${if (scrollingState) "scrolling" else "default"} state${emojiLabel(
                customEmoji,
                pictures,
            )}): " +
                "CPU per frame P50 " +
                "${frames.percentile(50)} µs, P90 ${frames.percentile(90)} µs, P99 ${frames.percentile(99)} µs",
        )
    }

    private fun emojiLabel(
        customEmoji: Boolean,
        pictures: Boolean,
    ) = when {
        !customEmoji -> ""
        pictures -> ", custom emoji"
        else -> ", custom emoji as fallback"
    }

    private fun bubble(
        id: Int,
        customEmoji: Boolean,
    ): ChatItem.Bubble {
        val text =
            if (id % LONG_EVERY == 0) "Message $id, a longer one that takes a second line or so" else "Message $id"
        return if (customEmoji && id % CUSTOM_EMOJI_EVERY == 0) {
            bubble(id, "$text $EMOJI and $EMOJI", entities = customEmojiEntities(text.length + 1))
        } else {
            bubble(id, text)
        }
    }

    /** Two custom emoji: [EMOJI] at [offset] and again after " and ". */
    private fun customEmojiEntities(offset: Int) =
        listOf(
            TextEntity(offset, EMOJI.length, TextEntityType.CustomEmoji(CustomEmojiFakes.STAR)),
            TextEntity(
                offset + EMOJI.length + AND.length,
                EMOJI.length,
                TextEntityType.CustomEmoji(CustomEmojiFakes.LEAF),
            ),
        )

    private fun bubble(
        id: Int,
        text: String,
        entities: List<TextEntity> = emptyList(),
    ): ChatItem.Bubble =
        ChatPreviewData.bubble(
            id = id.toLong(),
            text = text,
            entities = entities,
            time = "9:41 AM",
            isOutgoing = id / RUN % 2 == 0,
            sender = UiText.Plain("Ada"),
            reply =
                if (id % REPLY_EVERY == 0) {
                    ReplyQuote(UiText.Plain("Ada"), senderId = 2, UiText.Plain("Message"), id - 1L)
                } else {
                    null
                },
            reactions =
                if (id % REACTIONS_EVERY == 0) {
                    ChatPreviewData.reactions(ReactionChip(ReactionLabel.Emoji("👍"), count = 2, isChosen = false))
                } else {
                    persistentListOf()
                },
        )

    /** [FRAMES] frames scrolling by [STEP] px each; the main thread's CPU time of each, scroll and frame, in µs. */
    private fun scroll(): List<Long> {
        val threads = ManagementFactory.getThreadMXBean()
        return List(FRAMES) {
            val start = threads.currentThreadCpuTime
            // Scrolling measures the list at once, composing what comes into view, before the frame.
            composeRule.runOnIdle { listState.dispatchRawDelta(STEP) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            (threads.currentThreadCpuTime - start) / NANOS_PER_MICRO
        }
    }

    private fun List<Long>.percentile(percent: Int) = this[(size - 1) * percent / PERCENT]

    private companion object {
        const val MESSAGES = 2_000
        const val RUN = 3
        const val LONG_EVERY = 4
        const val REPLY_EVERY = 7
        const val REACTIONS_EVERY = 5
        const val FRAMES = 120
        const val STEP = 40f
        const val WARMUP_RUNS = 3
        const val MEASURED_RUNS = 6
        const val NANOS_PER_MICRO = 1_000
        const val PERCENT = 100
        const val SCROLL_COST_PROPERTY = "pingogram.scrollCost"
        const val CUSTOM_EMOJI_EVERY = 3
        val CUSTOM_EMOJI = CustomEmojiFakes.all.filterKeys { it != CustomEmojiFakes.HEART }
        const val EMOJI = "🎉"
        const val AND = " and "
    }
}
