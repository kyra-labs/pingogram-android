package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.text.withMatches
import dev.kyralabs.pingogram.core.ui.text.withMentionChips

/**
 * [formatted] as [bubble] shows it: in the experimental look with chips on the user's mentions
 * ([ChatItem.Bubble.myMentions], `docs/DESIGN_SYSTEM.md` §13.6), then with the words the search in the chat marks
 * ([ChatItem.Bubble.searchMatch]); each added over the cached text only where the bubble has any.
 */
@Composable
internal fun shownText(
    bubble: ChatItem.Bubble,
    formatted: AnnotatedString,
): AnnotatedString {
    val mentions = bubble.myMentions.takeIf { LocalExperimentalUi.current && it.isNotEmpty() }
    val mentionContent = MaterialTheme.colorScheme.onPrimary
    val chipped =
        if (mentions == null) {
            formatted
        } else {
            remember(formatted, mentions, mentionContent) { formatted.withMentionChips(mentions, mentionContent) }
        }
    val match = bubble.searchMatch ?: return chipped
    val matchContent = MaterialTheme.colorScheme.onTertiary
    return remember(chipped, match, matchContent) { chipped.withMatches(match, matchContent) }
}
