package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.text.FormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString

/**
 * The colors of one side's bubbles (`docs/DESIGN_SYSTEM.md` §2.2).
 *
 * @property container the bubble: `primaryContainer` outgoing, `surfaceContainerHigh` incoming.
 * @property content its text: `onPrimaryContainer`, `onSurface`.
 * @property secondaryContent the time, "edited", "Forwarded from …" and the kind of media: `onPrimaryContainer`,
 *   `onSurfaceVariant`.
 * @property block reply quotes, link previews and reactions inside the bubble, a tone that stands out from
 *   [container]: `surfaceContainerLow` outgoing, `surfaceContainerHighest` incoming.
 * @property blockContent the text in a [block]: `onSurfaceVariant`.
 * @property text the look of entities in the bubble's text, with code and spoilers in tones of this side.
 */
@Immutable
internal class BubbleColors(
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
    val block: Color,
    val blockContent: Color,
    val text: FormattedTextStyles,
)

/** The [BubbleColors] of both sides, built once per theme for the whole list. */
@Immutable
internal class BubbleTheme(
    val incoming: BubbleColors,
    val outgoing: BubbleColors,
) {
    fun colorsOf(isOutgoing: Boolean): BubbleColors = if (isOutgoing) outgoing else incoming
}

@Composable
internal fun rememberBubbleTheme(): BubbleTheme {
    val colors = MaterialTheme.colorScheme
    val incomingText =
        rememberFormattedTextStyles(
            codeBackground = colors.surfaceContainerHighest,
            spoilerColor = colors.onSurfaceVariant,
        )
    val outgoingText =
        rememberFormattedTextStyles(
            codeBackground = colors.surfaceContainerLow,
            spoilerColor = colors.onPrimaryContainer,
        )
    return remember(colors, incomingText, outgoingText) {
        BubbleTheme(
            incoming =
                BubbleColors(
                    container = colors.surfaceContainerHigh,
                    content = colors.onSurface,
                    secondaryContent = colors.onSurfaceVariant,
                    block = colors.surfaceContainerHighest,
                    blockContent = colors.onSurfaceVariant,
                    text = incomingText,
                ),
            outgoing =
                BubbleColors(
                    container = colors.primaryContainer,
                    content = colors.onPrimaryContainer,
                    secondaryContent = colors.onPrimaryContainer,
                    block = colors.surfaceContainerLow,
                    blockContent = colors.onSurfaceVariant,
                    text = outgoingText,
                ),
        )
    }
}

/**
 * The texts of the bubbles as [AnnotatedString]s (`docs/PERFORMANCE.md` §4), built in composition, where the theme's
 * colors are, and kept per message id for as long as the screen shows: a bubble that scrolls away and back, or
 * recomposes, gets the same instance. An entry is built again only when the message's text, the styles (another
 * theme) or its spoilers' state change; the [MAX_ENTRIES] most recently used are kept.
 *
 * Building in the ViewModel instead would need the theme there, or a second, styled copy built here anyway; the
 * mapping already takes the domain's [FormattedText] as it is.
 *
 * Read in composition, on the main thread only; it holds no state that composition observes.
 */
@Stable
internal class AnnotatedTextCache {
    private val entries =
        object : LinkedHashMap<Long, Entry>(MAX_ENTRIES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?) = size > MAX_ENTRIES
        }

    /**
     * The annotated text of [bubble], with its spoilers hidden or revealed, in [styles]. The listeners end up in the
     * kept instance, so they must do the same for every composition of the bubble: reveal its spoilers, open a link.
     */
    fun get(
        bubble: ChatItem.Bubble,
        styles: FormattedTextStyles,
        onSpoilerClick: LinkInteractionListener,
        onLinkClick: LinkInteractionListener,
    ): AnnotatedString {
        val text = bubble.text
        val revealed = bubble.spoilersRevealed
        val cached = entries[bubble.id]
        if (cached != null && cached.matches(text, styles, revealed)) return cached.annotated
        val annotated = text.toAnnotatedString(styles, revealed, onSpoilerClick, onLinkClick)
        entries[bubble.id] = Entry(text, styles, revealed, annotated)
        return annotated
    }

    class Entry(
        val text: FormattedText,
        val styles: FormattedTextStyles,
        val spoilersRevealed: Boolean,
        val annotated: AnnotatedString,
    ) {
        // Identity first: the mapper hands the same text object on while the message does not change.
        fun matches(
            text: FormattedText,
            styles: FormattedTextStyles,
            spoilersRevealed: Boolean,
        ) = (this.text === text || this.text == text) && this.styles == styles &&
            this.spoilersRevealed == spoilersRevealed
    }

    private companion object {
        /** About ten screens of messages. */
        const val MAX_ENTRIES = 256
        const val LOAD_FACTOR = 0.75f
    }
}
