package dev.kyralabs.pingogram.feature.chat.ui.muted

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ReplyQuote
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedBar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * Which bubbles of a group's conversation collapse for a muted word (`REQUIREMENTS.md` FR-X21): those whose text or
 * caption has one of [words] (as keyword alerts match them, and within the text in scripts written without spaces),
 * except the user's own, those that mention or reply to the user ([myUserId]), and those shown with a tap ([shown]).
 * Service and sponsored messages are no bubbles, so never collapse.
 */
@Immutable
internal data class MutedWordsFilter(
    val words: ImmutableList<String>,
    val myUserId: Long?,
    val shown: ImmutableSet<Long>,
) {
    private val matcher by lazy { KeywordMatcher(words, withinUnspacedScripts = true) }

    /** Whether [bubble] collapses, whatever the user tapped open. */
    fun hasMutedWord(bubble: ChatItem.Bubble): Boolean =
        !bubble.isOutgoing &&
            !bubble.mentionsMe &&
            (myUserId == null || bubble.reply?.senderId != myUserId) &&
            matcher.firstMatch(bubble.text.text) != null

    /**
     * Whether [quote], a reply's quote of another message, hides its text: the quoted message is not the user's own,
     * is not shown with a tap, and its text has a muted word.
     */
    fun hidesQuote(quote: ReplyQuote): Boolean =
        (myUserId == null || quote.senderId != myUserId) &&
            quote.messageId !in shown &&
            quote.text.hasMutedWord()

    /** Whether the pinned messages [bar] hides its message's text, as [hidesQuote] a quote's. */
    fun hidesPinned(bar: PinnedBar): Boolean = !bar.isOutgoing && bar.messageId !in shown && bar.preview.hasMutedWord()

    /** Whether this text, where it is the message's own words, has a muted word. */
    private fun UiText.hasMutedWord(): Boolean = (this as? UiText.Plain)?.text?.let(matcher::firstMatch) != null

    override fun toString() = "MutedWordsFilter(words=${words.size}, shown=${shown.size})"
}
