package dev.kyralabs.pingogram.feature.chat.ui.muted

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
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

    override fun toString() = "MutedWordsFilter(words=${words.size}, shown=${shown.size})"
}
