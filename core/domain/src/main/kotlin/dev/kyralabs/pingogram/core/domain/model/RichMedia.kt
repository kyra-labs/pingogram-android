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
 * The photos, videos and GIFs of this rich message, or the unlocked photos and videos of this paid post, as messages
 * of their own, for the media viewer: each a copy of this message (sender, date, chat) with the media as its content,
 * its caption (a paid post's for each of its media) as the caption, and the id [richMediaId] gives it (a paid post's
 * media by their index in the post); in the message's order. Empty for a message of any other content, and for a paid
 * post that is not paid for.
 */
fun Message.richMediaMessages(): List<Message> =
    when (val content = content) {
        is MessageContent.RichMessage -> {
            content.message.media().map { media ->
                copy(id = richMediaId(id, media.index), content = media.toContent())
            }
        }

        is MessageContent.PaidMedia -> {
            content.mediaContents().map { (index, media) -> copy(id = richMediaId(id, index), content = media) }
        }

        else -> {
            emptyList()
        }
    }

/**
 * The photos and videos of this paid post, once it is paid for, as the contents of messages of their own, each with
 * its index among the post's media: what the viewer pages through and the export copies. Empty while it is not paid
 * for; previews and media of kinds Pingogram cannot show are left out.
 */
fun MessageContent.PaidMedia.mediaContents(): List<Pair<Int, MessageContent>> =
    media.mapIndexedNotNull { index, item ->
        when (item) {
            is PaidMediaItem.Photo -> {
                index to
                    MessageContent.Photo(
                        item.photo,
                        caption,
                        hasSpoiler = false,
                        isSecret = false,
                        showCaptionAboveMedia,
                    )
            }

            is PaidMediaItem.Video -> {
                index to
                    MessageContent.Video(
                        item.video,
                        caption,
                        hasSpoiler = false,
                        isSecret = false,
                        showCaptionAboveMedia,
                    )
            }

            is PaidMediaItem.Preview, PaidMediaItem.Unsupported -> {
                null
            }
        }
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
