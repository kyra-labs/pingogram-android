package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the message list puts an anchor's entry ([FollowListAnchor], [anchorPlacement]): measured there in the first
 * place, so the list never lays out the newest messages first when it opens at the unread divider, and exactly where
 * [AnchorPosition] says once the entry is measured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageListAnchorTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    /** The keys of every entry any layout of the list showed, in order. */
    private val laidOut = mutableListOf<Any>()

    private var margin = 0

    @Test
    fun `opening far from the newest message lays out the entries around the unread divider only`() {
        setScreen(longChat(unread = UNREAD))

        assertFalse("the newest message was laid out: $laidOut", NEWEST_KEY in laidOut)
        assertTrue(DIVIDER_KEY in laidOut)
    }

    @Test
    fun `the unread divider opens a margin below the top of the list`() {
        setScreen(longChat(unread = UNREAD))

        val layout = listState.layoutInfo
        val divider = layout.entry(DIVIDER_KEY)
        assertEquals(layout.viewport - margin - divider.size, divider.offset)
    }

    @Test
    fun `a jump far back centres the message`() {
        var state by mutableStateOf(longChat(unread = 0))
        setScreen { state }

        state = state.withAnchor(ListAnchor(id = 1, AnchorTarget.Message(FAR_BACK)))
        composeRule.waitForIdle()

        val layout = listState.layoutInfo
        val entry = layout.entry(FAR_BACK)
        assertEquals((layout.viewport - entry.size) / 2, entry.offset)
    }

    @Test
    fun `a jump to a message on screen glides it to the middle`() {
        var state by mutableStateOf(longChat(unread = 0))
        setScreen { state }
        composeRule.runOnIdle { listState.requestScrollToItem(SCROLLED_TO) }
        composeRule.waitForIdle()
        // A message near the top of the screen, which the list can bring to the middle.
        val onScreen =
            listState.layoutInfo.visibleItemsInfo
                .dropLast(1)
                .last()
                .key as Long

        state = state.withAnchor(ListAnchor(id = 1, AnchorTarget.Message(onScreen)))
        composeRule.waitForIdle()

        val layout = listState.layoutInfo
        val entry = layout.entry(onScreen)
        // The spring settles within a pixel.
        assertEquals((layout.viewport - entry.size) / 2f, entry.offset.toFloat(), 1f)
    }

    private fun setScreen(state: ChatUiState) = setScreen { state }

    private fun setScreen(state: () -> ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val listState = rememberLazyListState().also { listState = it }
                margin = with(LocalDensity.current) { MaterialTheme.spacing.extraExtraLarge.roundToPx() }
                LaunchedEffect(listState) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key } }.collect { laidOut.addAll(it) }
                }
                ChatScreen(state = state(), onEvent = {}, onBack = {}, listState = listState)
            }
        }
        composeRule.waitForIdle()
    }

    private fun LazyListLayoutInfo.entry(key: Any): LazyListItemInfo = visibleItemsInfo.single { it.key == key }

    private val LazyListLayoutInfo.viewport: Int
        get() = viewportSize.height - beforeContentPadding - afterContentPadding

    /**
     * [MESSAGES] incoming messages, newest first as the list takes them, with ids from [MESSAGES] down; the unread
     * divider above the [unread] newest.
     */
    private fun longChat(unread: Int): ChatUiState {
        val today = UiText.resource(R.string.chat_date_today)
        val items =
            (1L..MESSAGES).flatMap { index ->
                val bubble = bubble(index)
                val divider = unread > 0 && index == unread.toLong()
                if (divider) listOf(bubble, ChatItem.UnreadDivider(today)) else listOf(bubble)
            } + ChatItem.DateSeparator(epochDay = 1, date = today)
        return ChatUiState(
            header = ChatPreviewData.privateChat.header,
            content = ChatContent.Content(items.toImmutableList(), reachedStart = true, isLoadingOlder = false, null),
            unreadCount = unread,
        )
    }

    private fun ChatUiState.withAnchor(anchor: ListAnchor) =
        copy(content = (content as ChatContent.Content).copy(anchor = anchor))

    /** The incoming message [index] entries from the newest, one or two lines long. */
    private fun bubble(index: Long): ChatItem.Bubble {
        val id = MESSAGES - index + 1
        val text = FormattedText(if (id % 3 == 0L) "Message $id, a longer one that takes two lines" else "Message $id")
        return ChatItem.Bubble(
            id = id,
            isOutgoing = false,
            position = GroupPosition.Single,
            sender = UiText.Plain("Ada"),
            senderId = 2,
            showsSenderName = false,
            avatar = null,
            hasAvatarColumn = false,
            time = "9:41 AM",
            isEdited = false,
            status = null,
            forwardedFrom = null,
            reply = null,
            content = MessageContent.Text(text, linkPreview = null),
            contentLabel = null,
            text = text,
            spoilersRevealed = false,
            linkPreview = null,
            reactions = persistentListOf(),
            date = UiText.resource(R.string.chat_date_today),
        )
    }

    private fun FormattedText(text: String) = FormattedText(text, persistentListOf())

    private companion object {
        const val MESSAGES = 80L
        const val UNREAD = 40

        /** The newest message's key: its id. */
        const val NEWEST_KEY = MESSAGES

        const val DIVIDER_KEY = ChatItem.UNREAD_DIVIDER_KEY

        /** A message far back from the newest. */
        const val FAR_BACK = 10L

        /** An entry in the middle of the list. */
        const val SCROLLED_TO = 30
    }
}
