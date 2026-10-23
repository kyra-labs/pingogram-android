package dev.kyralabs.pingogram.feature.profile.ui

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.profile.R
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The items of the profile's shared media tabs (`REQUIREMENTS.md` FR-P2) from the messages found, in [zone] and
 * [locale]: photos, videos and GIFs for the grid; files, songs and voice messages with their dates; the first link of a
 * message's text or caption with its preview's title. Dates are "Mar 17" this year ([nowYear]), "Mar 17, 2024" before,
 * with the platform's patterns ([TimePatterns]); lengths "0:42".
 *
 * Built in the view model; [isFor] says whether it still fits the zone and language.
 */
internal class SharedMediaMapper(
    private val zone: ZoneId,
    private val locale: Locale,
    patterns: TimePatterns,
    private val nowYear: Int,
) {
    private val dayAndMonth = patterns.formatter("MMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    private val fullDate = patterns.formatter("yMMMd") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    /** The items of [messages] for the tab of [kind], in their order; a message the tab cannot show is left out. */
    fun items(
        kind: SharedMediaKind,
        messages: List<Message>,
    ): List<SharedMediaItem> = messages.mapNotNull { item(kind, it) }

    fun isFor(
        zone: ZoneId,
        locale: Locale,
        year: Int,
    ): Boolean = this.zone == zone && this.locale == locale && nowYear == year

    private fun item(
        kind: SharedMediaKind,
        message: Message,
    ): SharedMediaItem? {
        val date = date(message.date)
        if (kind == SharedMediaKind.Links) return link(message, date)
        return when (message.content) {
            is MessageContent.Photo, is MessageContent.Video, is MessageContent.Animation -> mediaItem(message, date)
            is MessageContent.Document, is MessageContent.Audio, is MessageContent.VoiceNote -> fileItem(message, date)
            else -> null
        }
    }

    /** A photo, video or GIF of the grid. */
    private fun mediaItem(
        message: Message,
        date: String,
    ): SharedMediaItem.Media? =
        when (val content = message.content) {
            is MessageContent.Photo -> {
                SharedMediaItem.Media(
                    messageId = message.id,
                    kind = MediaKind.Photo,
                    photo = content.photo,
                    thumbnail = null,
                    minithumbnail = content.photo.minithumbnail,
                    duration = null,
                    date = date,
                )
            }

            is MessageContent.Video -> {
                SharedMediaItem.Media(
                    messageId = message.id,
                    kind = MediaKind.Video,
                    photo = null,
                    thumbnail = content.video.thumbnail,
                    minithumbnail = content.video.minithumbnail,
                    duration = duration(content.video.duration),
                    date = date,
                )
            }

            is MessageContent.Animation -> {
                SharedMediaItem.Media(
                    messageId = message.id,
                    kind = MediaKind.Gif,
                    photo = null,
                    thumbnail = content.animation.thumbnail,
                    minithumbnail = content.animation.minithumbnail,
                    duration = null,
                    date = date,
                )
            }

            else -> {
                null
            }
        }

    /** A file, song or voice message of a list. */
    private fun fileItem(
        message: Message,
        date: String,
    ): SharedMediaItem.File? =
        when (val content = message.content) {
            is MessageContent.Document -> {
                val document = content.document
                SharedMediaItem.File(
                    message.id,
                    FileKind.Document,
                    UiText.Plain(document.fileName),
                    null,
                    document.file.size,
                    date,
                )
            }

            is MessageContent.Audio -> {
                val audio = content.audio
                val title =
                    audio.title
                        .ifBlank { audio.fileName }
                        .takeIf { it.isNotBlank() }
                        ?.let(UiText::Plain) ?: UiText.resource(R.string.profile_media_unknown_audio)
                SharedMediaItem.File(
                    message.id,
                    FileKind.Music,
                    title,
                    audio.performer.ifBlank {
                        duration(audio.duration)
                    },
                    audio.file.size,
                    date,
                )
            }

            is MessageContent.VoiceNote -> {
                val voice = content.voiceNote
                val title = UiText.resource(R.string.profile_media_voice_title, duration(voice.duration))
                SharedMediaItem.File(message.id, FileKind.Voice, title, null, voice.file.size, date)
            }

            else -> {
                null
            }
        }

    /** The first link of the message's text or caption, with its preview's title and description where it has one. */
    private fun link(
        message: Message,
        date: String,
    ): SharedMediaItem.Link? {
        val content = message.content
        val url = content.caption()?.firstUrl() ?: return null
        val preview = (content as? MessageContent.Text)?.linkPreview
        return SharedMediaItem.Link(
            messageId = message.id,
            url = url,
            title =
                preview?.title?.takeIf { it.isNotBlank() } ?: preview?.siteName?.takeIf { it.isNotBlank() } ?: host(
                    url,
                ),
            description = preview?.description?.text?.takeIf { it.isNotBlank() } ?: url,
            date = date,
        )
    }

    private fun date(epochSeconds: Long): String {
        val dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        return if (dateTime.year == nowYear) dayAndMonth.format(dateTime) else fullDate.format(dateTime)
    }

    /** "0:42", "1:02:03", in the digits of [locale]. */
    private fun duration(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        val hours = total / SECONDS_PER_HOUR
        val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
        val rest = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, rest)
        } else {
            String.format(locale, "%d:%02d", minutes, rest)
        }
    }

    private fun TimePatterns.formatter(
        skeleton: String,
        fallback: () -> DateTimeFormatter,
    ): DateTimeFormatter =
        try {
            DateTimeFormatter.ofPattern(bestPattern(locale, skeleton), locale)
        } catch (_: IllegalArgumentException) {
            fallback().withLocale(locale)
        }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60
        const val SECONDS_PER_HOUR = 3_600
    }
}

/** The text of a text message, or the caption of media; null for content without either. */
internal fun MessageContent.caption(): FormattedText? =
    when (this) {
        is MessageContent.Text -> text
        is MessageContent.Photo -> caption
        is MessageContent.Video -> caption
        is MessageContent.Animation -> caption
        is MessageContent.Document -> caption
        is MessageContent.Audio -> caption
        is MessageContent.VoiceNote -> caption
        else -> null
    }

/** The first web link of the text: a URL as written (with `https://` where it has no scheme) or a text link's. */
internal fun FormattedText.firstUrl(): String? = entities.sortedBy { it.offset }.firstNotNullOfOrNull { it.url(text) }

/** The web link of this entity of [text]; null for an entity that is none. */
private fun TextEntity.url(text: String): String? {
    val start = offset.coerceIn(0, text.length)
    val part = text.substring(start, (offset + length).coerceIn(start, text.length))
    return when (val type = type) {
        TextEntityType.Url -> part.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "https://$it" }
        is TextEntityType.TextUrl -> type.url
        else -> null
    }
}

/** The host of [url] without `www.` ("example.org"); the URL itself if it has none. */
private fun host(url: String): String =
    runCatching { URI(url).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() } ?: url
