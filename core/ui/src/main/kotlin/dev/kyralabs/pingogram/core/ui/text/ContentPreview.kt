package dev.kyralabs.pingogram.core.ui.text

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.ExpiredMedia
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.ui.R

/**
 * The preview of [content] that is not a service message ([messagePreviewText]): its text or caption with the kind of
 * media it is, or the kind alone.
 */
internal fun contentPreviewText(content: MessageContent): UiText =
    when (content) {
        is MessageContent.Text -> previewOf(content.text)

        is MessageContent.Photo -> withCaption(UiText.resource(R.string.preview_photo), content.caption)

        is MessageContent.Video -> withCaption(UiText.resource(R.string.preview_video), content.caption)

        is MessageContent.Animation -> withCaption(UiText.resource(R.string.preview_gif), content.caption)

        is MessageContent.Sticker -> stickerText(content.sticker.emoji)

        is MessageContent.VoiceNote -> withCaption(UiText.resource(R.string.preview_voice_message), content.caption)

        is MessageContent.VideoNote -> UiText.resource(R.string.preview_video_message)

        is MessageContent.Audio -> withCaption(audioName(content.audio), content.caption)

        is MessageContent.Document -> withCaption(documentName(content.document.fileName), content.caption)

        is MessageContent.Location -> locationText(content.livePeriod)

        is MessageContent.Venue -> labelled(R.string.preview_location, content.venue.title)

        is MessageContent.Contact -> contactText(content.contact)

        is MessageContent.Poll -> pollText(content.poll)

        is MessageContent.Dice -> UiText.Plain(content.emoji)

        is MessageContent.AnimatedEmoji -> UiText.Plain(content.emoji)

        is MessageContent.Game -> labelled(R.string.preview_game, content.title)

        is MessageContent.Invoice -> labelled(R.string.preview_invoice, content.title)

        is MessageContent.Story -> UiText.resource(R.string.preview_story)

        is MessageContent.Expired -> expiredText(content.media)

        // Without the message there is nobody to name; messagePreviewText handles service messages.
        is MessageContent.Service -> UiText.resource(R.string.preview_service_other)

        MessageContent.Unsupported -> UiText.resource(R.string.preview_unsupported)
    }

/** "[kind] · [caption]", or [kind] alone without a caption. */
private fun withCaption(
    kind: UiText,
    caption: FormattedText,
): UiText = if (caption.text.isBlank()) kind else UiText.resource(R.string.preview_with_text, kind, previewOf(caption))

/** "[kind] · [text]", or [kind] alone when [text] is blank. */
private fun labelled(
    @StringRes kind: Int,
    text: String,
): UiText =
    if (text.isBlank()) {
        UiText.resource(kind)
    } else {
        UiText.resource(R.string.preview_with_text, UiText.resource(kind), UiText.Plain(previewLine(text)))
    }

/** "😀 Sticker" with the emoji the sticker stands for. */
private fun stickerText(emoji: String): UiText =
    if (emoji.isBlank()) {
        UiText.resource(R.string.preview_sticker)
    } else {
        UiText.resource(R.string.preview_sticker_emoji, emoji)
    }

/** "Performer – Title", the title or the file name, whichever the sender gave; "Audio" without any. */
private fun audioName(audio: Audio): UiText =
    when {
        audio.title.isNotBlank() && audio.performer.isNotBlank() -> {
            UiText.resource(R.string.preview_audio_performer_title, audio.performer, audio.title)
        }

        audio.title.isNotBlank() -> {
            UiText.Plain(audio.title)
        }

        audio.fileName.isNotBlank() -> {
            UiText.Plain(audio.fileName)
        }

        else -> {
            UiText.resource(R.string.preview_audio)
        }
    }

private fun documentName(fileName: String): UiText =
    if (fileName.isBlank()) UiText.resource(R.string.preview_file) else UiText.Plain(fileName)

private fun locationText(livePeriod: Int): UiText =
    UiText.resource(if (livePeriod != 0) R.string.preview_live_location else R.string.preview_location)

private fun contactText(contact: Contact): UiText {
    val name = listOf(contact.firstName, contact.lastName).filter(String::isNotBlank).joinToString(" ")
    return labelled(R.string.preview_contact, name)
}

private fun pollText(poll: Poll): UiText =
    labelled(if (poll.type is PollType.Quiz) R.string.preview_quiz else R.string.preview_poll, poll.question.text)

private fun expiredText(media: ExpiredMedia): UiText =
    UiText.resource(
        when (media) {
            ExpiredMedia.Photo -> R.string.preview_expired_photo
            ExpiredMedia.Video -> R.string.preview_expired_video
            ExpiredMedia.VideoNote -> R.string.preview_expired_video_message
            ExpiredMedia.VoiceNote -> R.string.preview_expired_voice_message
        },
    )
