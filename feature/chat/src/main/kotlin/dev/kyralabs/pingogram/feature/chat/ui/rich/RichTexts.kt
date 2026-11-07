package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.ui.text.FormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.TextBlocksLayout
import dev.kyralabs.pingogram.core.ui.text.hasTextBlocks
import dev.kyralabs.pingogram.core.ui.text.textBlocks
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import java.util.IdentityHashMap

/**
 * The texts of rich messages as [AnnotatedString]s, kept per message id while the screen shows, as
 * [AnnotatedTextCache][dev.kyralabs.pingogram.feature.chat.ui.AnnotatedTextCache] keeps a text message's: a bubble
 * that scrolls back or recomposes builds none again (`docs/PERFORMANCE.md` §4). A message's texts are built as its
 * blocks first show, and again only when the message, the styles or its spoilers' state change; the
 * [MAX_ENTRIES] most recently used messages are kept. Read in composition, on the main thread only.
 */
@Stable
internal class RichTextCache {
    private val entries =
        object : LinkedHashMap<Long, RichTexts>(MAX_ENTRIES, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, RichTexts>?) = size > MAX_ENTRIES
        }

    /** The texts of message [messageId]'s [message], in [styles]; the listeners as [AnnotatedTextCache.get]'s. */
    @Suppress("LongParameterList") // The message, its look, and what tapping its spoilers, links and mentions does.
    fun get(
        messageId: Long,
        message: RichMessage,
        styles: FormattedTextStyles,
        spoilersRevealed: Boolean,
        onSpoilerClick: LinkInteractionListener,
        onLinkClick: LinkInteractionListener,
        onMentionClick: LinkInteractionListener,
    ): RichTexts {
        val cached = entries[messageId]
        if (cached != null && cached.matches(message, styles, spoilersRevealed)) return cached
        val texts = RichTexts(message, styles, spoilersRevealed, onSpoilerClick, onLinkClick, onMentionClick)
        entries[messageId] = texts
        return texts
    }

    private companion object {
        /** About ten screens of rich messages. */
        const val MAX_ENTRIES = 32
        const val LOAD_FACTOR = 0.75f
    }
}

/** One rich message's texts, each built the first time a block asks for it ([of]). */
@Stable
internal class RichTexts(
    private val message: RichMessage,
    private val styles: FormattedTextStyles,
    private val spoilersRevealed: Boolean,
    private val onSpoilerClick: LinkInteractionListener,
    private val onLinkClick: LinkInteractionListener,
    private val onMentionClick: LinkInteractionListener,
) {
    // By identity: the blocks keep their texts, so each text object is one place in the message.
    private val built = IdentityHashMap<FormattedText, AnnotatedString>()

    fun of(text: FormattedText): AnnotatedString =
        built.getOrPut(text) {
            text.toAnnotatedString(
                styles,
                spoilersRevealed,
                onSpoilerClick,
                onLinkClick,
                drawsBlocks = true,
                onMentionClick,
            )
        }

    fun matches(
        message: RichMessage,
        styles: FormattedTextStyles,
        spoilersRevealed: Boolean,
    ) = (this.message === message || this.message == message) && this.styles == styles &&
        this.spoilersRevealed == spoilersRevealed
}

/**
 * [text] of a rich message in [style], as wide as its block (its own text direction, as user content), with its
 * links, mentions and spoilers working as in a text bubble; its code and hidden spoilers are rounded `small` blocks
 * behind it, drawn from its layout ([textBlocks]).
 */
@Composable
internal fun RichText(
    text: FormattedText,
    scope: RichScope,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = scope.colors.content,
    textAlign: TextAlign? = null,
) {
    val annotated = scope.texts.of(text)
    // As wide as the block, so a one-line text aligns as a wrapped one does.
    val filled = modifier.fillMaxWidth()
    val textStyle = style.userContent.let { if (textAlign != null) it.copy(textAlign = textAlign) else it }
    if (annotated.hasTextBlocks()) {
        val blocks = remember { TextBlocksLayout() }
        Text(
            text = annotated,
            modifier =
                filled.textBlocks(
                    layout = blocks,
                    codeColor = scope.style.text.codeBackground,
                    spoilerColor = scope.style.text.spoilerColor,
                    shape = MaterialTheme.shapes.small,
                    horizontalPadding = MaterialTheme.sizes.textBlockPadding,
                    matchColor = MaterialTheme.colorScheme.tertiary,
                ),
            color = color,
            style = textStyle,
            onTextLayout = blocks.onTextLayout,
        )
    } else {
        Text(text = annotated, modifier = filled, color = color, style = textStyle)
    }
}
