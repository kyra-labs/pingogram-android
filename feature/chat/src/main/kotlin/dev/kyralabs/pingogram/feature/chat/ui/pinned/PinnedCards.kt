package dev.kyralabs.pingogram.feature.chat.ui.pinned

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatPeople
import dev.kyralabs.pingogram.feature.chat.ui.ChatRef
import dev.kyralabs.pingogram.feature.chat.ui.ChatTimeFormatter
import dev.kyralabs.pingogram.feature.chat.ui.DayAndTime
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.content.asPicture
import dev.kyralabs.pingogram.feature.chat.ui.id
import dev.kyralabs.pingogram.feature.chat.ui.isChannel
import dev.kyralabs.pingogram.feature.chat.ui.refOf
import dev.kyralabs.pingogram.feature.chat.ui.senderHueOf
import dev.kyralabs.pingogram.feature.chat.ui.senderNameOf
import dev.kyralabs.pingogram.feature.chat.ui.textOrCaption
import kotlinx.collections.immutable.toImmutableList
import java.net.URI
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a pinned message is for the page's filters: one kind each, so the chips' counts add up to all of them. */
internal fun Message.pinnedKind(): PinnedFilter =
    when (val content = content) {
        is MessageContent.Photo, is MessageContent.Video, is MessageContent.Animation, is MessageContent.VideoNote -> {
            PinnedFilter.Media
        }

        is MessageContent.Document, is MessageContent.Audio -> {
            PinnedFilter.Files
        }

        is MessageContent.Text -> {
            val hasUrl =
                content.text.entities.any {
                    it.type is TextEntityType.Url || it.type is TextEntityType.TextUrl
                }
            if (content.linkPreview != null || hasUrl) PinnedFilter.Links else PinnedFilter.Text
        }

        else -> {
            PinnedFilter.Text
        }
    }

/** What the page's search looks through in a pinned message: its text, its file's name and its link's title and host.
* */
internal fun Message.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val content = content
    val haystack =
        listOfNotNull(
            content.textOrCaption.text,
            (content as? MessageContent.Document)?.document?.fileName,
            (content as? MessageContent.Text)?.linkPreview?.let { "${it.title} ${it.siteName} ${it.displayUrl}" },
        )
    return query.trim().split(WHITESPACE).all { word -> haystack.any { it.contains(word, ignoreCase = true) } }
}

/** What the page's cards are made from, as of one emission. */
internal class CardSources(
    val chat: Chat,
    val people: ChatPeople,
    val formatter: ChatTimeFormatter,
    val now: Instant,
)

/** The cards of [pins] (newest first, all of the chat's) that [shown] keeps, under their months, numbered from the
* oldest. */
internal fun pinnedMonths(
    pins: List<Message>,
    shown: (Message) -> Boolean,
    sources: CardSources,
): List<PinnedMonth> {
    val thisYear = YearMonth.from(sources.now.atZone(sources.formatter.zone)).year
    val monthOnly = DateTimeFormatter.ofPattern(MONTH, sources.formatter.locale)
    val monthAndYear = DateTimeFormatter.ofPattern(MONTH_AND_YEAR, sources.formatter.locale)
    return pins
        .withIndex()
        .filter { (_, message) -> shown(message) }
        .groupBy { (_, message) -> YearMonth.from(Instant.ofEpochSecond(message.date).atZone(sources.formatter.zone)) }
        .map { (month, entries) ->
            val label = (if (month.year == thisYear) monthOnly else monthAndYear).format(month)
            PinnedMonth(
                label.replaceFirstChar { it.titlecase(sources.formatter.locale) },
                entries.map { (index, message) -> message.toCard(pins.size - index, sources) }.toImmutableList(),
            )
        }
}

private fun Message.toCard(
    number: Int,
    sources: CardSources,
): PinnedCard {
    val ref = refOf(sender, sources.chat, sources.people)
    val name = senderNameOf(this, sources.chat, sources.people)
    val content = content
    val text = content.textOrCaption
    val photo = (content as? MessageContent.Photo)?.takeUnless { it.hasSpoiler }?.photo
    val clip = content.pinnedClip()
    val file = content.pinnedFile()
    val link =
        (content as? MessageContent.Text)?.linkPreview?.let { preview ->
            PinnedLink(
                hostOf(preview.url, preview.displayUrl),
                preview.title.ifEmpty {
                    preview.siteName
                },
                preview.description.text,
            )
        }
    return PinnedCard(
        messageId = id,
        number = number,
        name = name,
        avatar =
            SenderAvatar(
                id = sender.id,
                name = name,
                photo = (ref as? User)?.profilePhoto ?: (ref as? ChatRef)?.photo,
                placeholderIcon = null,
            ),
        avatarShape = if (sender is MessageSender.Chat) avatarShapeOf(sources.chat) else AvatarShape.Circle,
        senderHue = senderHueOf(ref, sender.id),
        dateTime = dateTimeOf(date, sources),
        text = text,
        // A message with nothing else to show says what it is ("Video", "Poll: …"), as its preview does.
        kind =
            contentKindText(content).takeIf {
                text.text.isEmpty() && photo == null && clip == null && file == null && link == null
            },
        photo = photo,
        clip = clip,
        file = file,
        link = link,
        canCopy = text.text.isNotEmpty() && !sources.chat.hasProtectedContent,
    )
}

/** The still picture and length of a video, GIF or round video; null without a picture or behind a spoiler. */
private fun MessageContent.pinnedClip(): PinnedClip? =
    when (this) {
        is MessageContent.Video -> {
            video.thumbnail?.asPicture()?.takeUnless { hasSpoiler }?.let {
                PinnedClip(it.file.id, video.width, video.height, video.duration)
            }
        }

        is MessageContent.Animation -> {
            animation.thumbnail?.asPicture()?.takeUnless { hasSpoiler }?.let {
                PinnedClip(it.file.id, animation.width, animation.height, animation.duration, isGif = true)
            }
        }

        is MessageContent.VideoNote -> {
            videoNote.thumbnail?.asPicture()?.let {
                PinnedClip(it.file.id, videoNote.length, videoNote.length, videoNote.duration, isRound = true)
            }
        }

        else -> {
            null
        }
    }

/** A file's name, size and kind ("APK", "PDF": its extension) for a file or a song. */
private fun MessageContent.pinnedFile(): PinnedFile? =
    when (this) {
        is MessageContent.Document -> {
            PinnedFile(document.fileName, document.file.size, extensionOf(document.fileName))
        }

        is MessageContent.Audio -> {
            PinnedFile(
                audio.fileName.ifEmpty {
                    audio.title
                },
                audio.file.size,
                extensionOf(audio.fileName),
            )
        }

        else -> {
            null
        }
    }

private fun extensionOf(fileName: String): String =
    fileName
        .substringAfterLast('.', missingDelimiterValue = "")
        .takeIf {
            it.length in 1..MAX_EXTENSION
        }?.uppercase(Locale.ROOT)
        .orEmpty()

/** The host of a link preview's [url] ("play.google.com"), else its shortened [displayUrl]. */
private fun hostOf(
    url: String,
    displayUrl: String,
): String = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: displayUrl

/** The shape of the chat's own avatar, for a message the chat itself posted (a channel, an anonymous admin). */
private fun avatarShapeOf(chat: Chat): AvatarShape = if (chat.isChannel) AvatarShape.Channel else AvatarShape.Group

/** When a pinned message was sent: "Today, 9:41 PM", "Nov 12, 9:41 PM", "3/17/24, 9:41 PM". */
private fun dateTimeOf(
    epochSeconds: Long,
    sources: CardSources,
): UiText {
    val dayAndTime = sources.formatter.dayAndTime(epochSeconds, sources.now)
    val day =
        when (dayAndTime) {
            is DayAndTime.Today -> UiText.resource(R.string.chat_date_today)
            is DayAndTime.Yesterday -> UiText.resource(R.string.chat_date_yesterday)
            is DayAndTime.OnDate -> UiText.Plain(dayAndTime.date)
        }
    return UiText.resource(R.string.scheduled_day_time, day, dayAndTime.time)
}

private const val MONTH = "LLLL"
private const val MONTH_AND_YEAR = "LLLL yyyy"
private const val MAX_EXTENSION = 5
private val WHITESPACE = Regex("\\s+")
