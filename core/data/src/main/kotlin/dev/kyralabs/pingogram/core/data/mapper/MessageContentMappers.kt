package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Audio
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.ExpiredMedia
import dev.kyralabs.pingogram.core.domain.model.LinkPreview
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.Venue
import dev.kyralabs.pingogram.core.domain.model.VoiceNote
import dev.kyralabs.pingogram.core.domain.model.Waveform
import org.drinkless.tdlib.TdApi

/**
 * The content of a message from [sender], in a channel if [isChannelPost]; both only matter for service messages
 * ([toServiceAction]). Content this version does not show is [MessageContent.Unsupported].
 */
internal fun TdApi.MessageContent.toMessageContent(
    sender: MessageSender?,
    isChannelPost: Boolean,
): MessageContent =
    when (this) {
        is TdApi.MessageText -> {
            MessageContent.Text(text.toFormattedText(), linkPreview?.toLinkPreview())
        }

        is TdApi.MessageLocation -> {
            MessageContent.Location(location.toLocation(), livePeriod = 0)
        }

        is TdApi.MessageLiveLocation -> {
            MessageContent.Location(location.location.toLocation(), location.livePeriod)
        }

        is TdApi.MessageVenue -> {
            MessageContent.Venue(venue.toVenue())
        }

        is TdApi.MessageContact -> {
            MessageContent.Contact(contact.toContact())
        }

        is TdApi.MessagePoll -> {
            MessageContent.Poll(poll.toPoll(), description.toFormattedText())
        }

        is TdApi.MessageDice -> {
            MessageContent.Dice(emoji, value)
        }

        is TdApi.MessageAnimatedEmoji -> {
            MessageContent.AnimatedEmoji(emoji, animatedEmoji.sticker?.toSticker())
        }

        is TdApi.MessageStory -> {
            MessageContent.Story(storyPosterChatId, storyId)
        }

        is TdApi.MessageExpiredPhoto -> {
            MessageContent.Expired(ExpiredMedia.Photo)
        }

        is TdApi.MessageExpiredVideo -> {
            MessageContent.Expired(ExpiredMedia.Video)
        }

        is TdApi.MessageExpiredVideoNote -> {
            MessageContent.Expired(ExpiredMedia.VideoNote)
        }

        is TdApi.MessageExpiredVoiceNote -> {
            MessageContent.Expired(ExpiredMedia.VoiceNote)
        }

        else -> {
            toMediaContent()
                ?: toServiceAction(sender, isChannelPost)?.let(MessageContent::Service)
                ?: MessageContent.Unsupported
        }
    }

/** Photos, videos, stickers, voice notes and files; null for any other content. */
private fun TdApi.MessageContent.toMediaContent(): MessageContent? =
    when (this) {
        is TdApi.MessagePhoto -> {
            val caption = caption.toFormattedText()
            MessageContent.Photo(photo.toPhoto(), caption, hasSpoiler, isSecret, showCaptionAboveMedia)
        }

        is TdApi.MessageVideo -> {
            val caption = caption.toFormattedText()
            MessageContent.Video(video.toVideo(), caption, hasSpoiler, isSecret, showCaptionAboveMedia)
        }

        is TdApi.MessageAnimation -> {
            val caption = caption.toFormattedText()
            MessageContent.Animation(animation.toAnimation(), caption, hasSpoiler, isSecret, showCaptionAboveMedia)
        }

        // A sticker in a format added by a newer TDLib cannot be shown.
        is TdApi.MessageSticker -> {
            sticker.toSticker()?.let(MessageContent::Sticker) ?: MessageContent.Unsupported
        }

        is TdApi.MessageVoiceNote -> {
            MessageContent.VoiceNote(voiceNote.toVoiceNote(), caption.toFormattedText(), isListened)
        }

        is TdApi.MessageVideoNote -> {
            MessageContent.VideoNote(videoNote.toVideoNote(), isViewed, isSecret)
        }

        is TdApi.MessageAudio -> {
            MessageContent.Audio(audio.toAudio(), caption.toFormattedText())
        }

        is TdApi.MessageDocument -> {
            MessageContent.Document(document.toDocument(), caption.toFormattedText())
        }

        else -> {
            null
        }
    }

private fun TdApi.LinkPreview.toLinkPreview() =
    LinkPreview(
        url = url,
        displayUrl = displayUrl,
        siteName = siteName,
        title = title,
        description = description.toFormattedText(),
        photo = type.photo(),
        showLargeMedia = showLargeMedia,
        showAboveText = showAboveText,
    )

/** The picture of a link preview: a page's photo, a video's cover or thumbnail, a user's or chat's photo. */
private fun TdApi.LinkPreviewType.photo(): Photo? =
    when (this) {
        is TdApi.LinkPreviewTypeArticle -> photo?.toPhoto()
        is TdApi.LinkPreviewTypePhoto -> photo.toPhoto()
        is TdApi.LinkPreviewTypeApp -> photo.toPhoto()
        is TdApi.LinkPreviewTypeWebApp -> photo?.toPhoto()
        is TdApi.LinkPreviewTypeVideo -> cover?.toPhoto()
        is TdApi.LinkPreviewTypeEmbeddedVideoPlayer -> thumbnail?.toPhoto()
        is TdApi.LinkPreviewTypeEmbeddedAnimationPlayer -> thumbnail?.toPhoto()
        is TdApi.LinkPreviewTypeEmbeddedAudioPlayer -> thumbnail?.toPhoto()
        is TdApi.LinkPreviewTypeUser -> photo?.toPhoto()
        is TdApi.LinkPreviewTypeChat -> photo?.toPhoto()
        is TdApi.LinkPreviewTypeDirectMessagesChat -> photo?.toPhoto()
        is TdApi.LinkPreviewTypeChannelBoost -> photo?.toPhoto()
        is TdApi.LinkPreviewTypeSupergroupBoost -> photo?.toPhoto()
        is TdApi.LinkPreviewTypeVideoChat -> photo?.toPhoto()
        else -> null
    }

private fun TdApi.VoiceNote.toVoiceNote() = VoiceNote(duration, Waveform(waveform), voice.toFileRef())

private fun TdApi.Audio.toAudio() =
    Audio(
        duration = duration,
        title = title,
        performer = performer,
        fileName = fileName,
        albumCoverMinithumbnail = albumCoverMinithumbnail?.toMinithumbnail(),
        albumCoverThumbnail = albumCoverThumbnail?.toThumbnail(),
        file = audio.toFileRef(),
    )

private fun TdApi.Document.toDocument() =
    Document(
        fileName = fileName,
        mimeType = mimeType,
        minithumbnail = minithumbnail?.toMinithumbnail(),
        thumbnail = thumbnail?.toThumbnail(),
        file = document.toFileRef(),
    )

private fun TdApi.Location.toLocation() = Location(latitude, longitude)

private fun TdApi.Venue.toVenue() = Venue(location.toLocation(), title, address)

private fun TdApi.Contact.toContact() = Contact(phoneNumber, firstName, lastName, userId)
