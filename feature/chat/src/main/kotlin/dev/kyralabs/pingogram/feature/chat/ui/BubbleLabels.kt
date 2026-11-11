package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * What screen readers hear the content as: an album's count, a kind of content; null for texts, and for rich messages'
 * blocks, paid posts and giveaways, which say what they are in their own description.
 */
internal fun ItemInputs.contentLabel(content: MessageContent): UiText? =
    when {
        album.isNotEmpty() -> albumLabel(album)
        content.describesItself -> null
        else -> contentKindText(content)
    }

private val MessageContent.describesItself: Boolean
    get() =
        when (this) {
            is MessageContent.Text,
            is MessageContent.RichMessage,
            is MessageContent.PaidMedia,
            is MessageContent.Giveaway,
            is MessageContent.GiveawayWinners,
            -> true

            else -> false
        }

/** "4 photos", "2 videos", or "3 photos and videos", for screen readers. */
private fun albumLabel(album: List<Message>): UiText {
    val photos = album.count { it.content is MessageContent.Photo }
    val plural =
        when (photos) {
            album.size -> ChatR.plurals.content_album_photos
            0 -> ChatR.plurals.content_album_videos
            else -> ChatR.plurals.content_album_media
        }
    return UiText.plural(plural, album.size, album.size)
}
