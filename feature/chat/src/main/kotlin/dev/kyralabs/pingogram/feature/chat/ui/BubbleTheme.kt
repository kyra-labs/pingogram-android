package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.text.FormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.MessageBubbleShapes
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors

/**
 * How one side's bubbles look (`docs/DESIGN_SYSTEM.md` §2.2): their [colors], `core:ui`'s [BubbleColors] from
 * `MaterialTheme.pingogramColors` (the one source of bubble colors, for the bubble and the media in it), and the look
 * of entities in their [text], with code on the side's block tone and hidden spoilers in its tonal spoiler color, both
 * drawn as rounded blocks behind the text ([textBlocks][dev.kyralabs.pingogram.core.ui.text.textBlocks]).
 */
@Immutable
internal class BubbleStyle(
    val colors: BubbleColors,
    val text: FormattedTextStyles,
)

/**
 * The [BubbleStyle] of both sides and of a selected bubble (selection mode, either side), and the bubble [shapes] by
 * side and place in a group, built once per theme for the whole list.
 */
@Immutable
internal class BubbleTheme(
    val incoming: BubbleStyle,
    val outgoing: BubbleStyle,
    val selected: BubbleStyle,
    val shapes: MessageBubbleShapes,
) {
    fun styleOf(
        isOutgoing: Boolean,
        isSelected: Boolean = false,
    ): BubbleStyle =
        when {
            isSelected -> selected
            isOutgoing -> outgoing
            else -> incoming
        }
}

@Composable
internal fun rememberBubbleTheme(): BubbleTheme {
    val bubbles = MaterialTheme.pingogramColors
    val incoming = bubbles.incomingBubble
    val outgoing = bubbles.outgoingBubble
    val selected = bubbles.selectedBubble
    val incomingText =
        rememberFormattedTextStyles(codeBackground = incoming.block, spoilerColor = incoming.spoiler)
    val outgoingText =
        rememberFormattedTextStyles(codeBackground = outgoing.block, spoilerColor = outgoing.spoiler)
    val selectedText =
        rememberFormattedTextStyles(codeBackground = selected.block, spoilerColor = selected.spoiler)
    val shapes = MaterialTheme.shapes
    return remember(incoming, outgoing, selected, incomingText, outgoingText, selectedText, shapes) {
        BubbleTheme(
            incoming = BubbleStyle(incoming, incomingText),
            outgoing = BubbleStyle(outgoing, outgoingText),
            selected = BubbleStyle(selected, selectedText),
            shapes = MessageBubbleShapes(shapes),
        )
    }
}

/**
 * What every bubble of the list shares: the [theme], the [texts] cache, what tapping a link does ([onLinkClick]), the
 * message highlighted after a jump to it ([jump]), the crossfade of a tapped reaction badge ([reactions]), and the
 * actions screen readers get on every bubble ([shortcuts]), how photos download ([mediaDownloads]), the message a long
 * press lifts for its floating menu ([lift]) and the look of the swipe to reply ([swipe]).
 */
@Suppress("LongParameterList") // One holder for what every bubble shares, so the bubbles take a single parameter.
@Stable
internal class BubbleResources(
    val theme: BubbleTheme,
    val texts: AnnotatedTextCache,
    val onLinkClick: LinkInteractionListener,
    val jump: MessageJumpState,
    val reactions: ReactionToggleState,
    val shortcuts: MessageShortcuts,
    val mediaDownloads: MediaDownloads,
    val lift: MessageLiftState,
    val swipe: SwipeReplyStyle,
)

/**
 * What every bubble of the list shares ([BubbleResources]), kept while the theme, the jump state, the [shortcuts],
 * the [mediaDownloads] and the [lift] stay.
 */
@Composable
internal fun rememberBubbleResources(
    jump: MessageJumpState,
    shortcuts: MessageShortcuts,
    mediaDownloads: MediaDownloads = MediaDownloads(),
    lift: MessageLiftState = remember { MessageLiftState() },
): BubbleResources {
    val theme = rememberBubbleTheme()
    val texts = remember { AnnotatedTextCache() }
    val onLinkClick = rememberLinkOpener()
    val reactions = rememberReactionToggleState()
    val swipe = rememberSwipeReplyStyle()
    return remember(theme, texts, onLinkClick, jump, reactions, shortcuts, mediaDownloads, lift, swipe) {
        BubbleResources(theme, texts, onLinkClick, jump, reactions, shortcuts, mediaDownloads, lift, swipe)
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
        val annotated = text.toAnnotatedString(styles, revealed, onSpoilerClick, onLinkClick, drawsBlocks = true)
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
