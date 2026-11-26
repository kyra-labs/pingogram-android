package dev.kyralabs.pingogram.feature.chat.ui.muted

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.OverlayCopies
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The conversation's bubbles collapsed for muted words (`REQUIREMENTS.md` FR-X21), kept by the route from one state
 * to the next: a bubble's text is matched once per bubble object and list of words, not on every state the
 * conversation emits (a typing status's as a new message's), and a collapsed bubble is the same copy while it stays
 * collapsed ([OverlayCopies]), so the message list recomposes only what changed. Runs on the main thread, as the
 * route composes.
 */
internal class MutedMessages {
    private val copies = OverlayCopies()
    private var decided = HashMap<Any, Decision>()
    private var next = HashMap<Any, Decision>()

    /**
     * [state] with the bubbles [filter] collapses marked ([ChatItem.Bubble.isMuted]), the quotes and the pinned bar it
     * hides saying "Message with a muted word" instead; as it is without a filter.
     */
    fun apply(
        state: ChatUiState,
        filter: MutedWordsFilter?,
    ): ChatUiState {
        val content = state.content as? ChatContent.Content
        if (filter == null || content == null) return state
        var changed = false
        val items =
            content.items.map { item ->
                if (item !is ChatItem.Bubble) return@map item
                val muted = item.id !in filter.shown && hasMutedWord(item, filter)
                val quote = item.reply
                val hiddenQuote = quote?.takeIf(filter::hidesQuote)?.copy(text = MUTED)
                if (muted || hiddenQuote != null) {
                    changed = true
                    copies.entry(item.key, item, overlay = muted to (hiddenQuote != null)) {
                        item.copy(isMuted = muted, reply = hiddenQuote ?: quote)
                    }
                } else {
                    item
                }
            }
        decided = next.also { next = decided.apply { clear() } }
        val pinned = state.pinnedBar?.takeIf(filter::hidesPinned)?.copy(preview = MUTED)
        return when {
            changed -> {
                state.copy(content = content.copy(items = copies.list(items)), pinnedBar = pinned ?: state.pinnedBar)
            }

            pinned != null -> {
                state.copy(pinnedBar = pinned)
            }

            else -> {
                state
            }
        }
    }

    /** Whether [bubble] has a muted word, as decided before for the same bubble and words. */
    private fun hasMutedWord(
        bubble: ChatItem.Bubble,
        filter: MutedWordsFilter,
    ): Boolean {
        val kept = decided[bubble.key]
        val muted = if (kept != null && kept.isFor(bubble, filter)) kept.muted else filter.hasMutedWord(bubble)
        next[bubble.key] = Decision(bubble, filter, muted)
        return muted
    }

    /** Whether [bubble] has a muted word ([muted]) for [filter]'s words and user. */
    private class Decision(
        private val bubble: ChatItem.Bubble,
        filter: MutedWordsFilter,
        val muted: Boolean,
    ) {
        private val words = filter.words
        private val myUserId = filter.myUserId

        /** Whether it was decided for the same [bubble] object and the same words and user. */
        fun isFor(
            bubble: ChatItem.Bubble,
            filter: MutedWordsFilter,
        ): Boolean = this.bubble === bubble && words == filter.words && myUserId == filter.myUserId
    }
}

/** What a hidden quote or pinned message says instead of its text. */
private val MUTED = UiText.resource(UiR.string.preview_muted_word)
