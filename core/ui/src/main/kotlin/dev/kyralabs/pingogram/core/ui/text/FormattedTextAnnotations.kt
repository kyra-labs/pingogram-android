package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType

/**
 * This text with its entities as an [AnnotatedString] in [styles]. Offsets count UTF-16 code units, as TDLib sends
 * them and as a `String` is indexed; entities reaching past the text are cut at its end. Entities may be nested
 * (bold in a link, underline in strikethrough); TDLib never sends partly overlapping ones, but they would not break
 * this either.
 *
 * - **Styles**: bold, italic, underline and strikethrough (combined where they nest), code, pre and pre-code
 *   (monospace on [FormattedTextStyles.codeBackground]; the language of pre-code is not shown), block quotes (italic).
 * - **Links**: URLs, text URLs, email addresses and phone numbers are [LinkAnnotation.Url]s (`https://` added to a URL
 *   without a scheme, `mailto:`, `tel:`), which `Text` opens with `LocalUriHandler` unless [onLinkClick] handles them.
 * - **Mentions** (`@username`) and mention names (a user without a username) are colored and, with [onMentionClick],
 *   [LinkAnnotation.Clickable]s whose tag names who they mention ([mentionTarget]): the profile opens from them.
 *   Hashtags, cashtags, bot commands and media timestamps are colored; tapping them does nothing yet. Custom emoji
 *   show the regular emoji the text holds for them; dates stay as written.
 * - **Spoilers** are hidden as blocks of [FormattedTextStyles.spoilerColor] until [spoilersRevealed]; tapping a hidden
 *   one calls [onSpoilerClick] (a [LinkAnnotation.Clickable] tagged [SPOILER_TAG]), and links inside it stay inert.
 * - **Blocks**: with [drawsBlocks], code, pre blocks and hidden spoilers get no span background. They are marked with
 *   [TEXT_BLOCK_TAG] annotations instead (the hidden spoilers' text turned transparent), and [textBlocks] draws them
 *   as rounded shapes behind the laid-out text. A span background is a rectangle and cannot be rounded.
 *
 * Building it costs a pass over the entities and an allocation per span: build it once per text and theme and keep
 * it, never on every recomposition (`docs/PERFORMANCE.md` §4). Listeners end up in the result, so pass ones that stay
 * valid as long as it is kept.
 */
fun FormattedText.toAnnotatedString(
    styles: FormattedTextStyles,
    spoilersRevealed: Boolean = false,
    onSpoilerClick: LinkInteractionListener? = null,
    onLinkClick: LinkInteractionListener? = null,
    drawsBlocks: Boolean = false,
    onMentionClick: LinkInteractionListener? = null,
): AnnotatedString {
    val ranges = entities.mapNotNull { it.rangeIn(text) }
    if (ranges.isEmpty()) return AnnotatedString(text)
    val hiddenSpoilers = if (spoilersRevealed) emptyList() else ranges.filter { it.type == TextEntityType.Spoiler }
    val linkStyles =
        TextLinkStyles(style = SpanStyle(color = styles.linkColor, textDecoration = TextDecoration.Underline))
    // Colored by their span style already; a link's default look would underline them.
    val mentionStyles = TextLinkStyles(style = SpanStyle(color = styles.mentionColor))
    return AnnotatedString
        .Builder(text)
        .apply {
            for (range in ranges) {
                val style = range.type.spanStyle(styles, drawsBlocks)
                if (style != null) addStyle(style, range.start, range.end)
                if (drawsBlocks) addBlock(range.type.block(), range.start, range.end)
                val url = range.type.url(text.substring(range.start, range.end))
                val shown = hiddenSpoilers.none { it.overlaps(range) }
                if (url != null &&
                    shown
                ) {
                    addLink(LinkAnnotation.Url(url, linkStyles, onLinkClick), range.start, range.end)
                }
                if (shown) addMention(range, text, mentionStyles, onMentionClick)
            }
            addDecorations(ranges)
            // Last, so that a hidden spoiler covers every other style within it.
            val hidden =
                if (drawsBlocks) {
                    SpanStyle(color = Color.Transparent)
                } else {
                    SpanStyle(color = styles.spoilerColor, background = styles.spoilerColor)
                }
            for (spoiler in hiddenSpoilers) {
                addStyle(hidden, spoiler.start, spoiler.end)
                if (drawsBlocks) addBlock(TextBlock.SPOILER, spoiler.start, spoiler.end)
                if (onSpoilerClick != null) {
                    // Its own styles, or the link would get the default link look over the hidden text.
                    val link = LinkAnnotation.Clickable(SPOILER_TAG, TextLinkStyles(style = hidden), onSpoilerClick)
                    addLink(link, spoiler.start, spoiler.end)
                }
            }
        }.toAnnotatedString()
}

/** The tag of the [LinkAnnotation.Clickable] on a hidden spoiler. */
const val SPOILER_TAG = "spoiler"

/** Who a tapped mention names: a user by their id (a mention name), or a username without `@`. */
sealed interface MentionTarget {
    data class User(
        val userId: Long,
    ) : MentionTarget

    data class Username(
        val username: String,
    ) : MentionTarget {
        override fun toString() = "Username(…)"
    }
}

/** Who the mention [link] (one `toAnnotatedString` made with `onMentionClick`) names; null for any other link. */
fun mentionTarget(link: LinkAnnotation): MentionTarget? {
    val tag = (link as? LinkAnnotation.Clickable)?.tag ?: return null
    val userId = tag.takeIf { it.startsWith(MENTION_NAME_TAG) }?.removePrefix(MENTION_NAME_TAG)?.toLongOrNull()
    val username = tag.takeIf { it.startsWith(MENTION_TAG) }?.removePrefix(MENTION_TAG)?.takeIf { it.isNotEmpty() }
    return userId?.let(MentionTarget::User) ?: username?.let(MentionTarget::Username)
}

private const val MENTION_TAG = "mention:"
private const val MENTION_NAME_TAG = "mention-name:"

/** A mention's [LinkAnnotation.Clickable] over [range] of [text], with [onMentionClick]; nothing for other entities. */
private fun AnnotatedString.Builder.addMention(
    range: EntityRange,
    text: String,
    styles: TextLinkStyles,
    onMentionClick: LinkInteractionListener?,
) {
    if (onMentionClick == null) return
    val tag = range.type.mentionTag(text.substring(range.start, range.end)) ?: return
    addLink(LinkAnnotation.Clickable(tag, styles, onMentionClick), range.start, range.end)
}

/** The tag of a mention's [LinkAnnotation.Clickable] over [part] of the text; null for other entities. */
private fun TextEntityType.mentionTag(part: String): String? =
    when (this) {
        TextEntityType.Mention -> part.removePrefix("@").takeIf { it.isNotEmpty() }?.let { MENTION_TAG + it }
        is TextEntityType.MentionName -> MENTION_NAME_TAG + userId
        else -> null
    }

/** An entity's [start] (inclusive) and [end] (exclusive) within the text. */
private class EntityRange(
    val start: Int,
    val end: Int,
    val type: TextEntityType,
) {
    fun overlaps(other: EntityRange) = start < other.end && other.start < end
}

/** The entity's range in [text], cut to the text; null if nothing is left of it. */
private fun TextEntity.rangeIn(text: String): EntityRange? {
    val start = offset.coerceIn(0, text.length)
    val end = (offset.toLong() + length).coerceIn(start.toLong(), text.length.toLong()).toInt()
    return if (end > start) EntityRange(start, end, type) else null
}

/** Marks [start] to [end] as a block of [kind] ([TextBlock]) for [textBlocks] to draw; nothing without a [kind]. */
private fun AnnotatedString.Builder.addBlock(
    kind: String?,
    start: Int,
    end: Int,
) {
    if (kind != null) addStringAnnotation(TEXT_BLOCK_TAG, kind, start, end)
}

/** The block an entity of this type draws behind its text, if any; hidden spoilers are added on their own. */
private fun TextEntityType.block(): String? =
    when (this) {
        TextEntityType.Code -> TextBlock.CODE
        TextEntityType.Pre, is TextEntityType.PreCode -> TextBlock.PRE
        else -> null
    }

/**
 * The style of an entity of this type; null for those that only link, decorate, hide, or show as written. Code gets
 * no background when the text [drawsBlocks] itself.
 */
private fun TextEntityType.spanStyle(
    styles: FormattedTextStyles,
    drawsBlocks: Boolean,
): SpanStyle? =
    when (this) {
        TextEntityType.Bold -> {
            SpanStyle(fontWeight = styles.boldWeight)
        }

        TextEntityType.Italic -> {
            SpanStyle(fontStyle = FontStyle.Italic)
        }

        TextEntityType.Code, TextEntityType.Pre, is TextEntityType.PreCode -> {
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = if (drawsBlocks) Color.Unspecified else styles.codeBackground,
            )
        }

        TextEntityType.BlockQuote, TextEntityType.ExpandableBlockQuote -> {
            SpanStyle(fontStyle = FontStyle.Italic, color = styles.quoteColor)
        }

        TextEntityType.Mention,
        is TextEntityType.MentionName,
        TextEntityType.Hashtag,
        TextEntityType.Cashtag,
        TextEntityType.BotCommand,
        is TextEntityType.MediaTimestamp,
        -> {
            SpanStyle(color = styles.mentionColor)
        }

        // Links are styled by their LinkAnnotation, decorations combined in addDecorations, spoilers hidden last.
        TextEntityType.Underline,
        TextEntityType.Strikethrough,
        TextEntityType.Spoiler,
        TextEntityType.Url,
        is TextEntityType.TextUrl,
        TextEntityType.EmailAddress,
        TextEntityType.PhoneNumber,
        TextEntityType.BankCardNumber,
        TextEntityType.TonAddress,
        is TextEntityType.CustomEmoji,
        is TextEntityType.DateTime,
        TextEntityType.Unknown,
        -> {
            null
        }
    }

/** What an entity of this type over [part] of the text opens; null if it is no link. */
private fun TextEntityType.url(part: String): String? =
    when (this) {
        TextEntityType.Url -> if (part.contains("://")) part else "https://$part"
        is TextEntityType.TextUrl -> url
        TextEntityType.EmailAddress -> "mailto:$part"
        TextEntityType.PhoneNumber -> "tel:" + part.filter { it.isDigit() || it == '+' }
        else -> null
    }

/**
 * Underline and strikethrough, combined where they nest or overlap: a span style has one decoration, and of two
 * overlapping spans the later one would win. Each stretch between the ends of such entities gets the decorations of
 * the entities covering it.
 */
private fun AnnotatedString.Builder.addDecorations(ranges: List<EntityRange>) {
    val decorated =
        ranges.filter { it.type == TextEntityType.Underline || it.type == TextEntityType.Strikethrough }
    if (decorated.isEmpty()) return
    val bounds = decorated.flatMap { listOf(it.start, it.end) }.distinct().sorted()
    for ((start, end) in bounds.zipWithNext()) {
        val covering = decorated.filter { it.start <= start && end <= it.end }
        val decorations =
            listOfNotNull(
                TextDecoration.Underline.takeIf { covering.any { it.type == TextEntityType.Underline } },
                TextDecoration.LineThrough.takeIf { covering.any { it.type == TextEntityType.Strikethrough } },
            )
        if (decorations.isNotEmpty()) {
            addStyle(
                SpanStyle(textDecoration = TextDecoration.combine(decorations)),
                start,
                end,
            )
        }
    }
}
