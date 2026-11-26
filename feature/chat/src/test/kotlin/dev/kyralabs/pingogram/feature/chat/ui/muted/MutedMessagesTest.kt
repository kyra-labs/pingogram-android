package dev.kyralabs.pingogram.feature.chat.ui.muted

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.ReplyQuote
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedBar
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.core.ui.R as UiR

/** A group's bubbles collapsed for muted words (FR-X21), and only the bubbles that may be. */
class MutedMessagesTest {
    private val state = ChatPreviewData.groupChat

    private fun filter(
        vararg words: String,
        shown: Set<Long> = emptySet(),
    ) = MutedWordsFilter(persistentListOf(*words), ME, shown.toPersistentSet())

    private val ChatUiState.bubbles get() = (content as ChatContent.Content).items.filterIsInstance<ChatItem.Bubble>()

    private fun ChatUiState.bubble(id: Long) = bubbles.single { it.id == id }

    /** [state] with bubble [id] changed by [change]. */
    private fun with(
        id: Long,
        change: (ChatItem.Bubble) -> ChatItem.Bubble,
    ): ChatUiState {
        val content = state.content as ChatContent.Content
        val items = content.items.map { if (it is ChatItem.Bubble && it.id == id) change(it) else it }
        return state.copy(content = content.copy(items = items.toImmutableList()))
    }

    @Test
    fun `collapses an incoming bubble with a muted word, in any case, and copies that bubble only`() {
        val muted = MutedMessages().apply(state, filter("MOTION"))

        assertTrue(muted.bubble(MOTION).isMuted)
        val before = (state.content as ChatContent.Content).items
        val after = (muted.content as ChatContent.Content).items
        before.zip(after).forEach { (old, new) -> if (new !is ChatItem.Bubble || !new.isMuted) assertSame(old, new) }
    }

    @Test
    fun `no filter and no muted word leave the state as it is`() {
        assertSame(state, MutedMessages().apply(state, null))
        assertSame(state, MutedMessages().apply(state, filter("zeppelin")))
        assertSame(state, MutedMessages().apply(state, filter("mot")))
    }

    @Test
    fun `a message tapped open shows`() {
        assertFalse(MutedMessages().apply(state, filter("motion", shown = setOf(MOTION))).bubble(MOTION).isMuted)
    }

    @Test
    fun `the user's own messages, mentions of the user and replies to the user never collapse`() {
        val own = with(MOTION) { it.copy(isOutgoing = true) }
        val mention = with(MOTION) { it.copy(mentionsMe = true) }
        val reply = with(MOTION) { it.copy(reply = ReplyQuote(UiText.Plain("You"), ME, UiText.Plain("Hi"))) }

        listOf(own, mention, reply).forEach { changed ->
            assertFalse(MutedMessages().apply(changed, filter("motion")).bubble(MOTION).isMuted)
        }
    }

    @Test
    fun `a quote of a message with a muted word says so, unless it is the user's own or tapped open`() {
        val id = state.bubbles.first { it.id != MOTION }.id
        val quote = ReplyQuote(UiText.Plain("Bob"), BOB, UiText.Plain("Big motion ahead"), messageId = 77)
        val quoting = with(id) { it.copy(reply = quote) }

        val hidden = MutedMessages().apply(quoting, filter("motion")).bubble(id).reply
        val own = MutedMessages().apply(with(id) { it.copy(reply = quote.copy(senderId = ME)) }, filter("motion"))
        val open = MutedMessages().apply(quoting, filter("motion", shown = setOf(77L)))

        assertEquals(UiText.resource(UiR.string.preview_muted_word), hidden?.text)
        assertEquals(quote.copy(senderId = ME), own.bubble(id).reply)
        assertEquals(quote, open.bubble(id).reply)
    }

    @Test
    fun `the pinned bar says so too, unless the pinned message is the user's own`() {
        val bar =
            PinnedBar(5, position = 0, count = 1, sender = null, preview = UiText.Plain("motion"), canUnpin = false)

        val hidden = MutedMessages().apply(state.copy(pinnedBar = bar), filter("motion")).pinnedBar
        val ownBar = bar.copy(isOutgoing = true)
        val own = MutedMessages().apply(state.copy(pinnedBar = ownBar), filter("motion")).pinnedBar

        assertEquals(UiText.resource(UiR.string.preview_muted_word), hidden?.preview)
        assertEquals(ownBar, own)
    }

    @Test
    fun `the same state again is the same list, without matching again`() {
        val muted = MutedMessages()

        val first = muted.apply(state, filter("motion"))
        val second = muted.apply(state, filter("motion"))

        assertSame((first.content as ChatContent.Content).items, (second.content as ChatContent.Content).items)
    }

    private companion object {
        const val ME = 1L
        const val BOB = 2L

        /** The preview group's message about motion. */
        const val MOTION = 24L
    }
}
