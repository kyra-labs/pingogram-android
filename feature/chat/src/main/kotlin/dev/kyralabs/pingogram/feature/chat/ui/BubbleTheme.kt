package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.text.FormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import dev.kyralabs.pingogram.core.ui.text.mentionTarget
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.MessageBubbleShapes
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.feature.chat.ui.rich.RichTextCache

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
 * side and place in a group (`MaterialTheme.chatStyle`'s, with the user's bubble corners), built once per theme for the
 * whole list.
 */
@Immutable
internal class BubbleTheme(
    val incoming: BubbleStyle,
    val outgoing: BubbleStyle,
    val selected: BubbleStyle,
    val shapes: MessageBubbleShapes,
    val hues: List<HueStyle> = emptyList(),
    val vividHues: Boolean = true,
    val channelPost: BubbleStyle? = null,
) {
    /**
     * The style of a bubble: selected, outgoing, or incoming, which in the experimental look takes its sender's [hue]
     * (`docs/DESIGN_SYSTEM.md` §13.6), vivid or tinted as [vividHues] says.
     */
    fun styleOf(
        isOutgoing: Boolean,
        isSelected: Boolean = false,
        hue: Int = -1,
        isChannelPost: Boolean = false,
    ): BubbleStyle =
        when {
            isSelected -> selected
            isChannelPost && channelPost != null -> channelPost
            isOutgoing -> outgoing
            hue >= 0 && hues.isNotEmpty() -> hues[hue % hues.size].let { if (vividHues) it.vivid else it.tinted }
            else -> incoming
        }

    /** The ink of a sender's [hue] for names and reply stripes (§13.6); null outside the experimental look. */
    fun inkOf(hue: Int): Color? = if (hue >= 0 && hues.isNotEmpty()) hues[hue % hues.size].ink else null
}

/** One sender hue's bubble styles and ink in the experimental look (§13.6). */
@Immutable
internal class HueStyle(
    val vivid: BubbleStyle,
    val tinted: BubbleStyle,
    val ink: Color,
)

/**
 * The bubbles' theme; in the experimental look with the sender hues' styles, vivid where [vividHues] (the group's size
 * and Chat appearance's "Group bubbles" decide, `GroupBubbles.isVivid`).
 */
@Composable
internal fun rememberBubbleTheme(vividHues: Boolean = true): BubbleTheme {
    val experimental = LocalExperimentalUi.current
    val hueColors = MaterialTheme.pingogramColors.senderHues
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
    // The bubbles' shapes with the corners chosen in Chat appearance (docs/DESIGN_SYSTEM.md §4).
    val shapes = MaterialTheme.chatStyle.bubbleShapes
    val hues =
        remember(experimental, hueColors, incomingText) {
            if (experimental) hueStyles(hueColors, incomingText) else emptyList()
        }
    // The experimental look's channel posts on a warm container (docs/DESIGN_SYSTEM.md §13.7).
    val postColors = MaterialTheme.pingogramColors.channelPostBubble
    val channelPost =
        remember(experimental, postColors, incomingText) {
            if (experimental) bubbleStyleOf(postColors, incomingText) else null
        }
    return remember(
        incoming,
        outgoing,
        selected,
        incomingText,
        outgoingText,
        selectedText,
        shapes,
        hues,
        vividHues,
        channelPost,
    ) {
        BubbleTheme(
            incoming = BubbleStyle(incoming, incomingText),
            outgoing = BubbleStyle(outgoing, outgoingText),
            selected = BubbleStyle(selected, selectedText),
            shapes = shapes,
            hues = hues,
            vividHues = vividHues,
            channelPost = channelPost,
        )
    }
}

/**
 * Each sender hue's vivid and tinted styles: links and mentions in the bubble's accent, readable on its fill, code and
 * spoilers on its own block and spoiler tones.
 */
private fun hueStyles(
    hues: SenderHues,
    text: FormattedTextStyles,
): List<HueStyle> =
    (0 until hues.size).map { index ->
        val hue = hues[index]
        HueStyle(bubbleStyleOf(hue.vivid, text), bubbleStyleOf(hue.tinted, text), hue.ink)
    }

/** A bubble style on [colors]: links and mentions in its accent, code and spoilers on its block and spoiler tones. */
private fun bubbleStyleOf(
    colors: BubbleColors,
    text: FormattedTextStyles,
) = BubbleStyle(
    colors,
    text.copy(
        linkColor = colors.accent,
        mentionColor = colors.accent,
        codeBackground = colors.block,
        spoilerColor = colors.spoiler,
        quoteColor = colors.secondaryContent,
    ),
)

/**
 * What every bubble of the list shares: the [theme], the [texts] cache, what tapping a link or a mention does
 * ([onLinkClick], [onMentionClick]), the
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
    val onMentionClick: LinkInteractionListener,
    val jump: MessageJumpState,
    val reactions: ReactionToggleState,
    val shortcuts: MessageShortcuts,
    val mediaDownloads: MediaDownloads,
    val lift: MessageLiftState,
    val swipe: SwipeReplyStyle,
    val share: ShareButtonStyle,
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
    vividHues: Boolean = true,
    onMention: (MentionTarget) -> Unit = {},
): BubbleResources {
    val theme = rememberBubbleTheme(vividHues)
    val texts = remember { AnnotatedTextCache() }
    val onLinkClick = rememberLinkOpener()
    val onMentionClick = rememberMentionOpener(onMention)
    val reactions = rememberReactionToggleState()
    val swipe = rememberSwipeReplyStyle()
    val share = rememberShareButtonStyle()
    return remember(
        theme,
        texts,
        onLinkClick,
        onMentionClick,
        jump,
        reactions,
        shortcuts,
        mediaDownloads,
        lift,
        swipe,
        share,
    ) {
        BubbleResources(
            theme,
            texts,
            onLinkClick,
            onMentionClick,
            jump,
            reactions,
            shortcuts,
            mediaDownloads,
            lift,
            swipe,
            share,
        )
    }
}

/**
 * What tapping a mention in a bubble does: [onMention] with who it names ([mentionTarget]). One listener for the
 * screen's lifetime, as the cached texts keep it, which calls the latest [onMention].
 */
@Composable
private fun rememberMentionOpener(onMention: (MentionTarget) -> Unit): LinkInteractionListener {
    val currentOnMention by rememberUpdatedState(onMention)
    return remember { LinkInteractionListener { link -> mentionTarget(link)?.let { currentOnMention(it) } } }
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
    /** The texts of rich messages, kept the same way, per message. */
    val rich = RichTextCache()

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
        onMentionClick: LinkInteractionListener? = null,
    ): AnnotatedString {
        val text = bubble.text
        val revealed = bubble.spoilersRevealed
        val cached = entries[bubble.id]
        if (cached != null && cached.matches(text, styles, revealed)) return cached.annotated
        val annotated =
            text.toAnnotatedString(styles, revealed, onSpoilerClick, onLinkClick, drawsBlocks = true, onMentionClick)
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
