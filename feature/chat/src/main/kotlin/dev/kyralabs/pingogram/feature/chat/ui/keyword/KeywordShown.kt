package dev.kyralabs.pingogram.feature.chat.ui.keyword

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.OverlayCopies
import dev.kyralabs.pingogram.feature.chat.ui.showsMessage
import kotlinx.collections.immutable.toImmutableList

/** The keyword alert match the match bar shows (board 30): message [messageId], which has [word]. */
@Immutable
data class KeywordShown(
    val messageId: Long,
    val word: String,
) {
    override fun toString() = "KeywordShown(messageId=$messageId)"
}

/**
 * The state with the bubble of message [shown] marking where its text has the word ([ChatItem.Bubble.keywordMatches],
 * found as the alert found it: whole words, any letter case, with or without accents): only that bubble is copied, so
 * no other recomposes; the state as it is without a match, or where the text no longer has the word.
 */
internal fun ChatUiState.withKeywordMatch(
    shown: KeywordShown?,
    copies: OverlayCopies = OverlayCopies(),
): ChatUiState {
    val content = content as? ChatContent.Content
    if (shown == null || content == null) return this
    val matcher = KeywordMatcher(listOf(shown.word))
    var changed = false
    val items =
        content.items.map { item ->
            if (item is ChatItem.Bubble && item.showsMessage(shown.messageId)) {
                copies
                    .entry(item.key, item, shown.word) {
                        val ranges = matcher.matches(item.text.text).map { it.start until it.end }
                        if (ranges.isEmpty()) item else item.copy(keywordMatches = ranges.toImmutableList())
                    }.also { if (it !== item) changed = true }
            } else {
                item
            }
        }
    return if (changed) copy(content = content.copy(items = copies.list(items))) else this
}
