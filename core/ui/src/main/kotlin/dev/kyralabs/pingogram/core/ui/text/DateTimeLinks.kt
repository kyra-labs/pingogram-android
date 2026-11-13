package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import dev.kyralabs.pingogram.core.domain.model.TextEntityType

// Dates in messages (td_api.tl textEntityTypeDateTime) as links of the text, which [toAnnotatedString] adds.

/**
 * The moment a tapped date in a message names ([TextEntityType.DateTime]), in Unix time; null for other links. Dates
 * are [LinkAnnotation.Clickable]s handed the mention listener, which tells them apart with this.
 */
fun dateTimeOf(link: LinkAnnotation): Long? =
    ((link as? LinkAnnotation.Clickable)?.tag)
        ?.takeIf { it.startsWith(DATE_TIME_TAG) }
        ?.removePrefix(DATE_TIME_TAG)
        ?.toLongOrNull()

private const val DATE_TIME_TAG = "date-time:"

/** A date as a link in the mention style, which [listener] gets with its moment ([dateTimeOf]). */
internal fun AnnotatedString.Builder.addDateTime(
    range: EntityRange,
    styles: TextLinkStyles,
    listener: LinkInteractionListener?,
) {
    val type = range.type as? TextEntityType.DateTime ?: return
    if (listener == null) return
    addLink(LinkAnnotation.Clickable(DATE_TIME_TAG + type.unixTime, styles, listener), range.start, range.end)
}
