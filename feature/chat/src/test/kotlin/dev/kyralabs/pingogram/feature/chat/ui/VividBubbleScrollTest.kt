package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A group in the experimental look (`docs/DESIGN_SYSTEM.md` §13.6) scrolls through its vivid bubbles, the first of each
 * sender's run with its name above it, and some texts drawn with blocks (inline code, as mention chips are): the row
 * reads where each such bubble ends, which must not make Compose place the bubble's text while the row is measured
 * (it threw "LayoutNode not found in RectList" there, in the conversation's prefetching list state).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class VividBubbleScrollTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    @Test
    fun `vivid bubbles with names above and code scroll by`() {
        val state =
            ChatUiState(
                header = ChatPreviewData.privateChat.header,
                content =
                    ChatContent.Content(
                        (MESSAGES downTo 1).map(::bubble).toImmutableList(),
                        reachedStart = true,
                        isLoadingOlder = false,
                        null,
                    ),
                composer = ChatPreviewData.canWrite,
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = rememberScrollingListState()
                CompositionLocalProvider(LocalExperimentalUi provides true) {
                    ChatScreen(state = state, onEvent = {}, onBack = {}, listState = listState)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        repeat(FRAMES) {
            composeRule.runOnIdle { listState.dispatchRawDelta(STEP) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        assertTrue("${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > MIN_SCROLLED)
    }

    /** Runs of [RUN] messages by one sender, outgoing and incoming in turn; every [CODE_EVERY]th incoming with code. */
    private fun bubble(id: Int): ChatItem.Bubble {
        val sender = (id / RUN).toLong()
        val bubble =
            ChatPreviewData.bubble(
                id = id.toLong(),
                text =
                    if (id % LONG_EVERY ==
                        0
                    ) {
                        "Message $id, a longer one that takes a second line"
                    } else {
                        "Message $id"
                    },
                entities =
                    if (id % CODE_EVERY == 0) listOf(TextEntity(0, CODE_LENGTH, TextEntityType.Code)) else emptyList(),
                time = "9:41 AM",
                isOutgoing = id / RUN % 2 == 0,
                sender = UiText.Plain("Ada"),
                reply =
                    if (id % REPLY_EVERY == 0) {
                        ReplyQuote(UiText.Plain("Ada"), senderId = 2, UiText.Plain("Message"), id - 1L)
                    } else {
                        null
                    },
            )
        return if (bubble.isOutgoing) {
            bubble
        } else {
            bubble.copy(
                showsSenderName = !bubble.position.joinsPrevious,
                hasAvatarColumn = true,
                senderId = sender,
                senderHue = SenderHues.indexOf(-1, sender),
            )
        }
    }

    private companion object {
        const val MESSAGES = 400
        const val RUN = 3
        const val LONG_EVERY = 4
        const val REPLY_EVERY = 7
        const val CODE_EVERY = 11
        const val CODE_LENGTH = 4
        const val FRAMES = 240
        const val STEP = 40f
        const val MIN_SCROLLED = 20
    }
}
