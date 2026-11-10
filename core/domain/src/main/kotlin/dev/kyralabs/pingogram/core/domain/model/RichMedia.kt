package dev.kyralabs.pingogram.core.domain.model

/**
 * The id the media viewer and the bubble give the media [index] ([RichBlock.Media.index]) of rich message
 * [messageId]: negative, so no message has it, and ordered as the message's media, so the viewer keeps them in their
 * order. Each message's media keep apart from another's: Telegram's message ids are multiples of
 * [RICH_MEDIA_SPAN], and no message holds that many media.
 */
fun richMediaId(
    messageId: Long,
    index: Int,
): Long = -(messageId + RICH_MEDIA_SPAN - index)

/** Whether [id] is a rich message's media's ([richMediaId]) rather than a message's. */
fun isRichMediaId(id: Long): Boolean = id < 0

/** The message whose media [richMediaId] gave [id]. */
fun richMediaMessageId(id: Long): Long = (-id - 1) / RICH_MEDIA_SPAN * RICH_MEDIA_SPAN

/**
 * The photos, videos and GIFs of this rich message, or a poll's photo or video, as messages of their own, for the
 * media viewer: each a copy of this message (sender, date, chat) with the media as its content, its caption as the
 * caption, and the id [richMediaId] gives it; in the message's order. Empty for a message of any other content.
 */
fun Message.richMediaMessages(): List<Message> {
    (content as? MessageContent.Poll)?.mediaContent()?.let { return listOf(copy(id = richMediaId(id, 0), content = it)) }
    val rich = (content as? MessageContent.RichMessage)?.message ?: return emptyList()
    return rich.media().map { media -> copy(id = richMediaId(id, media.index), content = media.toContent()) }
}

/**
 * A poll's photo or video as the content of a message of its own, which the media viewer shows by [richMediaId] with
 * index 0, without a caption; null for a poll without one.
 */
fun MessageContent.Poll.mediaContent(): MessageContent? =
    when (val media = media) {
        is PollMedia.Photo -> MessageContent.Photo(media.photo, FormattedText.Empty, false, false, false)
        is PollMedia.Video -> MessageContent.Video(media.video, FormattedText.Empty, false, false, false)
        else -> null
    }

/** This media as the content of a message of its own, its caption's text as the caption. */
private fun RichBlock.Media.toContent(): MessageContent =
    when (this) {
        is RichBlock.Photo -> {
            MessageContent.Photo(photo, caption.text, hasSpoiler, isSecret = false, showCaptionAboveMedia = false)
        }

        is RichBlock.Video -> {
            MessageContent.Video(video, caption.text, hasSpoiler, isSecret = false, showCaptionAboveMedia = false)
        }

        is RichBlock.Animation -> {
            MessageContent.Animation(
                animation,
                caption.text,
                hasSpoiler,
                isSecret = false,
                showCaptionAboveMedia = false,
            )
        }
    }

/** More than a message holds media; Telegram's message ids are its multiples. */
private const val RICH_MEDIA_SPAN = 1L shl 20
