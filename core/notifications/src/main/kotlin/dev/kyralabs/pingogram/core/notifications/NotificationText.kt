package dev.kyralabs.pingogram.core.notifications

import android.content.res.Resources
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a notification shows of a message's own words: a text message's text, or "Photo · " and a caption, as the
 * sender wrote them, not the chat list's one-line preview. Line breaks stay, bold, italic, underline, strikethrough
 * and code keep their look, and spoilers are hidden ("Spoiler"). At most [NOTIFICATION_TEXT_MAX_LENGTH] characters,
 * then "…". Null for content without words of its own (a sticker, a call, a service message), which keeps its preview.
 */
internal fun notificationText(
    content: MessageContent,
    resources: Resources,
): CharSequence? {
    val words = content.words()?.takeIf { it.text.isNotBlank() } ?: return null
    val text = words.toSpanned(resources)
    return if (content is MessageContent.Text) text else withKind(content, text, resources)
}

/** "Photo · [caption]", the caption's spans kept and its direction its own. */
private fun withKind(
    content: MessageContent,
    caption: CharSequence,
    resources: Resources,
): CharSequence {
    val kind = contentKindText(content).resolve(resources)
    // The template's arguments become TextUtils' "^1" and "^2", which keep the caption's spans.
    val template =
        resources
            .getString(UiR.string.preview_with_text)
            .replace("%1\$s", "^1")
            .replace("%2\$s", "^2")
    val isolated = SpannableStringBuilder().append(FIRST_STRONG_ISOLATE).append(caption).append(POP_DIRECTIONAL_ISOLATE)
    return TextUtils.expandTemplate(template, kind, isolated)
}

/** The longest message text a notification shows; Android shows a few lines of it, a reply's view more. */
internal const val NOTIFICATION_TEXT_MAX_LENGTH = 1_000

/** FIRST STRONG ISOLATE and POP DIRECTIONAL ISOLATE: the caption takes the direction of its first letter. */
private val FIRST_STRONG_ISOLATE = Char(0x2068)
private val POP_DIRECTIONAL_ISOLATE = Char(0x2069)
private const val ELLIPSIS = "…"

/** The text, or the caption, a message carries. */
private fun MessageContent.words(): FormattedText? =
    when (this) {
        is MessageContent.Text -> text
        is MessageContent.Photo -> caption
        is MessageContent.Video -> caption
        is MessageContent.Animation -> caption
        is MessageContent.VoiceNote -> caption
        is MessageContent.Audio -> caption
        is MessageContent.Document -> caption
        is MessageContent.PaidMedia -> caption
        else -> null
    }

private fun FormattedText.toSpanned(resources: Resources): CharSequence {
    val builder = SpannableStringBuilder(text)
    val spoilers = mutableListOf<IntRange>()
    entities.forEach { entity ->
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        if (start == end) return@forEach
        if (entity.type == TextEntityType.Spoiler) spoilers += start until end
        val span: Any? =
            when (entity.type) {
                TextEntityType.Bold -> StyleSpan(Typeface.BOLD)
                TextEntityType.Italic -> StyleSpan(Typeface.ITALIC)
                TextEntityType.Underline -> UnderlineSpan()
                TextEntityType.Strikethrough -> StrikethroughSpan()
                TextEntityType.Code, TextEntityType.Pre, is TextEntityType.PreCode -> TypefaceSpan(MONOSPACE)
                else -> null
            }
        if (span != null) builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    // From the end, so the ranges before a replaced spoiler keep their offsets; the builder moves the spans.
    val spoiler = resources.getString(R.string.notification_spoiler)
    mergeRanges(spoilers).asReversed().forEach { range -> builder.replace(range.first, range.last + 1, spoiler) }
    if (builder.length > NOTIFICATION_TEXT_MAX_LENGTH) {
        var end = NOTIFICATION_TEXT_MAX_LENGTH
        if (Character.isHighSurrogate(builder[end - 1])) end--
        builder.delete(end, builder.length)
        builder.append(ELLIPSIS)
    }
    return builder
}

/** [ranges] sorted, with those that touch or overlap (a spoiler inside a spoiler) merged into one. */
private fun mergeRanges(ranges: List<IntRange>): List<IntRange> =
    ranges.sortedBy { it.first }.fold(mutableListOf()) { merged, range ->
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
        merged
    }

private const val MONOSPACE = "monospace"
