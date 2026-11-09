package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import dev.kyralabs.pingogram.core.ui.text.withMatches
import dev.kyralabs.pingogram.core.ui.text.withMentionChips
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge

// How a bubble frames its content and what its text shows, for MessageBubble.kt.

/** How a bubble's content is framed: without a bubble ([isBare]), media edge to edge, or padded. */
internal enum class FrameKind(
    val isBare: Boolean,
) {
    Emoji(true),
    Shout(true),
    Bare(true),
    Media(false),
    Padded(false),
}

/**
 * How [bubble] is framed: 1 to 3 emoji, a shouted word (docs/DESIGN_SYSTEM.md §13.6), bare content, media, or padded
 * content.
 */
internal fun frameKindOf(bubble: ChatItem.Bubble): FrameKind =
    when {
        bubble.isEmojiOnly -> FrameKind.Emoji
        bubble.isShout -> FrameKind.Shout
        bubble.album == null && bubble.content.isBare -> FrameKind.Bare
        bubble.album != null || bubble.content.isEdgeToEdge -> FrameKind.Media
        else -> FrameKind.Padded
    }

/**
 * [formatted] as [bubble] shows it: the user's mentions as chips and a channel post's headline, and the words the
 * search in the chat marks, each over the cached text and kept per input.
 */
@Composable
internal fun shownText(
    bubble: ChatItem.Bubble,
    formatted: AnnotatedString,
): AnnotatedString {
    val match = bubble.searchMatch
    val matchContent = MaterialTheme.colorScheme.onTertiary
    // The chips on the user's mentions (docs/DESIGN_SYSTEM.md §13.6), over the cached text.
    val mentions = bubble.myMentions.takeIf { it.isNotEmpty() }
    val mentionContent = MaterialTheme.colorScheme.onPrimary
    val chipped =
        if (mentions == null) {
            formatted
        } else {
            remember(formatted, mentions, mentionContent) { formatted.withMentionChips(mentions, mentionContent) }
        }
    val matched =
        if (match == null) {
            chipped
        } else {
            remember(chipped, match, matchContent) { chipped.withMatches(match, matchContent) }
        }
    // The headline of a channel post (docs/DESIGN_SYSTEM.md §13.7), last: it takes out the line
    // break after the headline, which would move the ranges the chips and the matches mark.
    val headline = bubble.headlineLength.takeIf { it > 0 }
    val headlineStyle = MaterialTheme.experimentalTypography.displaySmallCondensed
    return if (headline == null) {
        matched
    } else {
        remember(matched, headline, headlineStyle) { matched.withHeadline(headline, headlineStyle) }
    }
}
