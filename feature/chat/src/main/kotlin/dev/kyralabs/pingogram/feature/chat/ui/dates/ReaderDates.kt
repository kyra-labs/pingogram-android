package dev.kyralabs.pingogram.feature.chat.ui.dates

import dev.kyralabs.pingogram.core.domain.model.DateTimeFormat
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.replacingEntities
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import java.time.Instant

/**
 * This text with its dates (td_api.tl `textEntityTypeDateTime`) as the reader should see them (`docs/DESIGN_SYSTEM.md`
 * §6 "Dates in messages"): in the reader's time zone, language and clock ([formatter]), relative ones as of [now]
 * ("in 3 days"), or as a full date where there is no [now]; a date without a format keeps the sender's words. The
 * other entities follow ([replacingEntities]), so everything that marks the text afterwards (mentions, search, custom
 * emoji, a headline) works on the text as shown.
 */
internal fun FormattedText.withReaderDates(
    formatter: ChatTimeFormatter,
    now: Instant?,
): FormattedText {
    if (entities.none { it.type is TextEntityType.DateTime }) return this
    return replacingEntities { entity ->
        val date = entity.type as? TextEntityType.DateTime ?: return@replacingEntities null
        when {
            date.format == DateTimeFormat.Relative && now == null -> formatter.fullDateTime(date.unixTime)
            else -> formatter.dateTimeText(date, now ?: Instant.EPOCH)
        }
    }
}

/** Whether this text has a date worded relative to now, which the conversation words again each minute. */
internal val FormattedText.hasRelativeDates: Boolean
    get() = entities.any { (it.type as? TextEntityType.DateTime)?.format == DateTimeFormat.Relative }
