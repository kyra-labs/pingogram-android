package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.transition.sharedMediaBounds
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaAlbum
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaUploadProgress
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageAnimatedEmoji
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageAnimation
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageAudio
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageContact
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageDice
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageDocument
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageExpired
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageGame
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageInvoice
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageLocation
import dev.kyralabs.pingogram.feature.chat.ui.content.MessagePhoto
import dev.kyralabs.pingogram.feature.chat.ui.content.MessagePoll
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageSticker
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageStory
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageVenue
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageVideo
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageVideoNote
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageVoiceNote
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.content.PollAction
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge
import dev.kyralabs.pingogram.feature.chat.ui.content.opensInViewer
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * What a bubble shows of its message's content above the text or caption (`REQUIREMENTS.md` FR-C4): the one place
 * that dispatches on the kind of content, to the components of `ui.content`. The bubble frames them as their kind
 * asks ([isBare], [isEdgeToEdge]; [MessageBubbleRow]); edge-to-edge media is clipped by the bubble, so it is drawn
 * with square corners here.
 *
 * - **Albums** ([ChatItem.Bubble.album]) are one [MediaAlbum] in [mediaShape]; tapping an item opens it by its own
 *   message.
 * - **Photos, videos and GIFs**, an album's items too, grow into the media viewer and shrink back into the bubble
 *   ([sharedMediaBounds], by their own message), from and to [mediaShape], the corners they show in the bubble (an
 *   album's items, theirs in it), which the viewer's side morphs from and back to as well.
 * - **Voice messages, songs and documents** show their file's [fileState]; voice messages and songs also how they
 *   play ([playback]), and send play and seek as events for the ViewModel's player.
 * - **Text messages** show nothing here: the bubble shows their text and link preview itself, as it does the caption
 *   of any media. Service messages never come here; they are pills. Content Pingogram cannot show yet shows its
 *   [ChatItem.Bubble.contentLabel] in one line.
 *
 * - **Uploads** ([uploads]): a photo, video, GIF or document being sent from this device shows its upload with a
 *   cancel button ([ChatEvent.CancelUpload]), an album's item each its own.
 *
 * @param fileState the state of the file of a voice message, song or document ([downloadableFileId]); null while
 *   unknown and for other content.
 * @param playback how a voice message or song plays; [Playback.Stopped] unless it is the one in the player.
 * @param colors the colors of the bubble's side, for content that draws on the bubble.
 * @param mediaShape the corners edge-to-edge media shows in the bubble, which clips it: the bubble's own where the
 *   media meets its edge, square where a header or a caption follows. The container transform starts and ends there.
 * @param uploads the uploads of the bubble's messages being sent, by message id; empty for the others.
 * @param mediaDownloads whether photos download by themselves, and the downloads the user asked for: photos the rules
 *   leave for a tap show a download button ([ChatEvent.DownloadFile], [ChatEvent.CancelDownload]).
 */
@Composable
internal fun MessageContentBody(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    playback: Playback,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
    mediaShape: CornerBasedShape = MaterialTheme.shapes.large,
    uploads: ImmutableMap<Long, MessageUpload> = persistentMapOf(),
    mediaDownloads: MediaDownloads = MediaDownloads(),
) {
    val album = bubble.album
    val content = bubble.content
    when {
        album != null -> {
            MediaAlbum(
                items = album.items,
                messageIds = album.messageIds,
                onOpen = { index -> onEvent(ChatEvent.OpenMedia(album.messageIds[index])) },
                modifier = modifier,
                shape = mediaShape,
                uploads = uploads,
                onCancelUpload = { messageId -> onEvent(ChatEvent.CancelUpload(listOf(messageId))) },
                downloads = mediaDownloads,
                onDownload = { fileId -> onEvent(ChatEvent.DownloadFile(fileId)) },
                onCancelDownload = { fileId -> onEvent(ChatEvent.CancelDownload(fileId)) },
            )
        }

        content.isEdgeToEdge || content.isBare -> {
            Box {
                MediaContent(bubble.id, content, onEvent, modifier, mediaShape, mediaDownloads)
                uploads[bubble.id]?.let { upload ->
                    MediaUploadProgress(
                        upload = upload,
                        onCancel = { onEvent(ChatEvent.CancelUpload(listOf(bubble.id))) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        content.downloadableFileId != null -> {
            FileContent(bubble, fileState, playback, colors, onEvent, modifier, uploads[bubble.id])
        }

        else -> {
            InfoContent(bubble, colors, onEvent, modifier)
        }
    }
}

/**
 * Photos, videos and GIFs, edge to edge, sharing their bounds with the media viewer (from [mediaShape]); stickers,
 * animated emoji, dice and video messages, bare.
 */
@Suppress("LongParameterList") // The message, its content, and how it shows and downloads in the bubble.
@Composable
private fun MediaContent(
    messageId: Long,
    content: MessageContent,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier,
    mediaShape: CornerBasedShape,
    mediaDownloads: MediaDownloads,
) {
    val onOpen = { onEvent(ChatEvent.OpenMedia(messageId)) }
    // Only what the viewer shows (no self-destructing media) shares its bounds with it.
    val shared = modifier.sharedMediaBounds(messageId, mediaShape, enabled = content.opensInViewer)
    when (content) {
        is MessageContent.Photo -> {
            MessagePhoto(
                content = content,
                onOpen = onOpen,
                modifier = shared,
                shape = RectangleShape,
                downloads = mediaDownloads,
                onDownload = { fileId -> onEvent(ChatEvent.DownloadFile(fileId)) },
                onCancelDownload = { fileId -> onEvent(ChatEvent.CancelDownload(fileId)) },
            )
        }

        is MessageContent.Video -> {
            MessageVideo(content, onOpen, shared, shape = RectangleShape)
        }

        is MessageContent.Animation -> {
            MessageAnimation(content, onOpen, shared, shape = RectangleShape)
        }

        is MessageContent.VideoNote -> {
            MessageVideoNote(content, onOpen, modifier)
        }

        is MessageContent.Sticker -> {
            MessageSticker(content, modifier)
        }

        is MessageContent.AnimatedEmoji -> {
            MessageAnimatedEmoji(content, modifier)
        }

        is MessageContent.Dice -> {
            MessageDice(content, modifier)
        }

        else -> {
            Unit
        }
    }
}

/**
 * Voice messages, songs and documents, with their file's state; voice messages and songs with their [playback], a
 * document being sent with its [upload].
 */
@Composable
private fun FileContent(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    playback: Playback,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
    upload: MessageUpload? = null,
) {
    val messageId = bubble.id
    val content = bubble.content
    val fileId = content.downloadableFileId ?: return
    val onPlayPause = { onEvent(ChatEvent.PlayPause(messageId, fileId)) }
    val onSeek = { fraction: Float -> onEvent(ChatEvent.Seek(messageId, fraction)) }
    when (content) {
        is MessageContent.VoiceNote -> {
            MessageVoiceNote(content, fileState, playback, onPlayPause, onSeek, modifier, colors)
        }

        is MessageContent.Audio -> {
            MessageAudio(content, fileState, playback, onPlayPause, onSeek, modifier, colors)
        }

        is MessageContent.Document -> {
            MessageDocument(
                content = content,
                fileState = fileState,
                onDownload = { onEvent(ChatEvent.DownloadFile(fileId)) },
                onCancel = { onEvent(ChatEvent.CancelDownload(fileId)) },
                onOpen = { onEvent(ChatEvent.OpenFile(messageId, fileId)) },
                modifier = modifier,
                colors = colors,
                upload = upload,
                onCancelUpload = { onEvent(ChatEvent.CancelUpload(listOf(messageId))) },
            )
        }

        else -> {
            Unit
        }
    }
}

/**
 * Locations, contacts, polls (with the answers checked before voting, [ChatItem.Bubble.pollChoices], and their taps as
 * [PollEvent]s) and the rest, padded in the bubble; nothing for text, which the bubble shows.
 */
@Composable
private fun InfoContent(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier,
) {
    val onOpenLocation = { onEvent(ChatEvent.OpenLocation(bubble.id)) }
    when (val content = bubble.content) {
        is MessageContent.Location -> {
            MessageLocation(content, onOpenLocation, modifier, colors)
        }

        is MessageContent.Venue -> {
            MessageVenue(content, onOpenLocation, modifier, colors)
        }

        is MessageContent.Contact -> {
            MessageContact(content, modifier, colors)
        }

        is MessageContent.Poll -> {
            MessagePoll(
                content = content,
                modifier = modifier,
                colors = colors,
                choices = bubble.pollChoices ?: persistentSetOf(),
                onAction = { action -> onEvent(action.eventFor(bubble.id)) },
            )
        }

        is MessageContent.Game -> {
            MessageGame(content, modifier, colors)
        }

        is MessageContent.Invoice -> {
            MessageInvoice(content, modifier, colors)
        }

        is MessageContent.Story -> {
            MessageStory(modifier, colors)
        }

        is MessageContent.Expired -> {
            MessageExpired(content, modifier, colors)
        }

        MessageContent.Unsupported -> {
            bubble.contentLabel?.let { ContentPlaceholder(it.resolve(), colors, modifier) }
        }

        else -> {
            Unit
        }
    }
}

/** The kind of content Pingogram cannot show yet, in one line ("Unsupported message"). */
@Composable
private fun ContentPlaceholder(
    label: String,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier,
        color = colors.secondaryContent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The event of a tap in the poll of message [messageId]. */
private fun PollAction.eventFor(messageId: Long): ChatEvent =
    when (this) {
        is PollAction.Vote -> PollEvent.Vote(messageId, optionIndex)
        is PollAction.Toggle -> PollEvent.Toggle(messageId, optionIndex)
        PollAction.Submit -> PollEvent.SubmitVotes(messageId)
        PollAction.ShowVoters -> PollEvent.OpenVoters(messageId)
    }
