package dev.kyralabs.pingogram.feature.media.ui

import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// How the viewer's items are made from the loaded messages: their media, sender and date.

/**
 * The item of this message, with its [sender] and [date] already put into words; null for a message that holds no
 * photo, video or GIF the viewer shows (a photo without sizes, self-destructing media). A spoiler is hidden unless
 * [revealed].
 */
internal fun Message.toMediaItem(
    sender: UiText,
    date: String,
    revealed: Boolean,
): MediaItem? {
    val media = content.toViewerMedia() ?: return null
    return MediaItem(id, media, sender, date, content.caption, isHidden = content.hasSpoiler && !revealed)
}

/** What the viewer shows of a photo, video or GIF; null for other content and for self-destructing media. */
internal fun MessageContent.toViewerMedia(): ViewerMedia? =
    when (this) {
        is MessageContent.Photo -> {
            largestPhotoSize(photo.sizes)?.takeUnless { isSecret }?.let { full -> ViewerMedia.Image(photo, full) }
        }

        is MessageContent.Video -> {
            ViewerMedia.Clip(video).takeUnless { isSecret }
        }

        is MessageContent.Animation -> {
            ViewerMedia.Gif(animation).takeUnless { isSecret }
        }

        else -> {
            null
        }
    }

/**
 * The files the viewer follows for this media: its full file, and a photo's other sizes, the largest of which on the
 * device shows while the full one loads.
 */
internal val ViewerMedia.followedFileIds: List<Int>
    get() =
        when (this) {
            is ViewerMedia.Image -> photo.sizes.map { it.file.id }
            is ViewerMedia.Clip, is ViewerMedia.Gif -> listOf(fileId)
        }

private val MessageContent.caption: FormattedText
    get() =
        when (this) {
            is MessageContent.Photo -> caption
            is MessageContent.Video -> caption
            is MessageContent.Animation -> caption
            else -> FormattedText("", persistentListOf())
        }

private val MessageContent.hasSpoiler: Boolean
    get() =
        when (this) {
            is MessageContent.Photo -> hasSpoiler
            is MessageContent.Video -> hasSpoiler
            is MessageContent.Animation -> hasSpoiler
            else -> false
        }

/** The largest of [sizes], the one the viewer shows; null without sizes. */
internal fun largestPhotoSize(sizes: List<PhotoSize>): PhotoSize? = sizes.maxByOrNull { it.width.toLong() * it.height }

/**
 * Who sent a message, for the top bar: the full name of a user in [users], the title of a chat in [chatTitles] (a
 * channel, a group's anonymous admins), or "Someone" while TDLib has not told.
 */
internal fun senderName(
    sender: MessageSender,
    users: Map<Long, User>,
    chatTitles: Map<Long, String>,
): UiText =
    when (sender) {
        is MessageSender.User -> users[sender.userId]?.fullName()
        is MessageSender.Chat -> chatTitles[sender.chatId]?.let(UiText::Plain)
    } ?: UiText.resource(R.string.preview_someone)

/**
 * Maps the loaded messages of a [MediaWindow] to the viewer's items, with their senders' names and their dates in the
 * zone, the language and the 12- or 24-hour setting of the moment, read from [clock], [locale] and [timePatterns] at
 * each mapping: the formatter is made anew only when one of them changed.
 */
internal class MediaItemMapper(
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
) {
    private var formatter: MediaDateFormatter? = null

    /** The items of [window]: spoilers hidden unless [revealed], senders named after [users] or [chatTitles]. */
    fun map(
        window: MediaWindow,
        revealed: Set<Long>,
        users: Map<Long, User>,
        chatTitles: Map<Long, String>,
    ): ImmutableList<MediaItem> {
        val formatter = formatter()
        val now = clock.now()
        return window.messages
            .mapNotNull { message ->
                message.toMediaItem(
                    sender = senderName(message.sender, users, chatTitles),
                    date = formatter.format(message.date, now),
                    revealed = message.id in revealed,
                )
            }.toImmutableList()
    }

    private fun formatter(): MediaDateFormatter {
        val zone = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return formatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: MediaDateFormatter(zone, locale, is24HourClock, timePatterns).also { formatter = it }
    }
}

/**
 * The dates of the viewer's items in [zone] and [locale], with the platform's patterns ([TimePatterns]): the day and
 * the time, "Oct 18, 9:41 AM" (or "18 Oct, 09:41" on the 24-hour clock), and the year too before this year. A pattern
 * java.time cannot read falls back to java.time's own localized style, as in the conversation.
 */
internal class MediaDateFormatter(
    val zone: ZoneId,
    val locale: Locale,
    val is24HourClock: Boolean,
    patterns: TimePatterns,
) {
    private val thisYear = patterns.formatter(if (is24HourClock) "MMMdHm" else "MMMdhm")
    private val otherYear = patterns.formatter(if (is24HourClock) "yMMMdHm" else "yMMMdhm")

    /** The date of [epochSeconds] (Unix time), with the year unless it is [now]'s. */
    fun format(
        epochSeconds: Long,
        now: Instant,
    ): String {
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val formatter = if (dateTime.year == now.atZone(zone).year) thisYear else otherYear
        return formatter.format(dateTime)
    }

    /** Whether this formatter is the one for [zone], [locale] and the clock setting. */
    fun isFor(
        zone: ZoneId,
        locale: Locale,
        is24HourClock: Boolean,
    ): Boolean = this.zone == zone && this.locale == locale && this.is24HourClock == is24HourClock

    private fun TimePatterns.formatter(skeleton: String): DateTimeFormatter =
        try {
            DateTimeFormatter.ofPattern(bestPattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        }
}
