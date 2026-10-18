package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaAlbum
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
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId
import dev.kyralabs.pingogram.feature.chat.ui.content.isBare
import dev.kyralabs.pingogram.feature.chat.ui.content.isEdgeToEdge

/**
 * What a bubble shows of its message's content above the text or caption (`REQUIREMENTS.md` FR-C4): the one place
 * that dispatches on the kind of content, to the components of `ui.content`. The bubble frames them as their kind
 * asks ([isBare], [isEdgeToEdge]; [MessageBubbleRow]); edge-to-edge media is clipped by the bubble, so it is drawn
 * with square corners here.
 *
 * - **Albums** ([ChatItem.Bubble.album]) are one [MediaAlbum]; tapping an item opens it by its own message.
 * - **Voice messages, songs and documents** show their file's [fileState]. Playback comes with the player: until
 *   then they show [Playback.Stopped], and play and seek go out as events for it.
 * - **Text messages** show nothing here: the bubble shows their text and link preview itself, as it does the caption
 *   of any media. Service messages never come here; they are pills. Content Pingogram cannot show yet shows its
 *   [ChatItem.Bubble.contentLabel] in one line.
 *
 * @param fileState the state of the file of a voice message, song or document ([downloadableFileId]); null while
 *   unknown and for other content.
 * @param colors the colors of the bubble's side, for content that draws on the bubble.
 */
@Composable
internal fun MessageContentBody(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = bubble.album
    val content = bubble.content
    when {
        album != null -> {
            MediaAlbum(
                items = album.items,
                onOpen = { index -> onEvent(ChatEvent.OpenMedia(album.messageIds[index])) },
                modifier = modifier,
                shape = RectangleShape,
            )
        }

        content.isEdgeToEdge || content.isBare -> {
            MediaContent(bubble.id, content, onEvent, modifier)
        }

        content.downloadableFileId != null -> {
            FileContent(bubble, fileState, colors, onEvent, modifier)
        }

        else -> {
            InfoContent(bubble, colors, onEvent, modifier)
        }
    }
}

/** Photos, videos and GIFs, edge to edge; stickers, animated emoji, dice and video messages, bare. */
@Composable
private fun MediaContent(
    messageId: Long,
    content: MessageContent,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier,
) {
    val onOpen = { onEvent(ChatEvent.OpenMedia(messageId)) }
    when (content) {
        is MessageContent.Photo -> MessagePhoto(content, onOpen, modifier, shape = RectangleShape)
        is MessageContent.Video -> MessageVideo(content, onOpen, modifier, shape = RectangleShape)
        is MessageContent.Animation -> MessageAnimation(content, onOpen, modifier, shape = RectangleShape)
        is MessageContent.VideoNote -> MessageVideoNote(content, onOpen, modifier)
        is MessageContent.Sticker -> MessageSticker(content, modifier)
        is MessageContent.AnimatedEmoji -> MessageAnimatedEmoji(content, modifier)
        is MessageContent.Dice -> MessageDice(content, modifier)
        else -> Unit
    }
}

/** Voice messages, songs and documents, with their file's state. */
@Composable
private fun FileContent(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier,
) {
    val messageId = bubble.id
    val content = bubble.content
    val fileId = content.downloadableFileId ?: return
    val onPlayPause = { onEvent(ChatEvent.PlayPause(messageId, fileId)) }
    val onSeek = { fraction: Float -> onEvent(ChatEvent.Seek(messageId, fraction)) }
    when (content) {
        is MessageContent.VoiceNote -> {
            MessageVoiceNote(content, fileState, Playback.Stopped, onPlayPause, onSeek, modifier, colors)
        }

        is MessageContent.Audio -> {
            MessageAudio(content, fileState, Playback.Stopped, onPlayPause, onSeek, modifier, colors)
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
            )
        }

        else -> {
            Unit
        }
    }
}

/** Locations, contacts, polls and the rest, padded in the bubble; nothing for text, which the bubble shows. */
@Composable
private fun InfoContent(
    bubble: ChatItem.Bubble,
    colors: BubbleColors,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier,
) {
    val onOpenLocation = { onEvent(ChatEvent.OpenLocation(bubble.id)) }
    when (val content = bubble.content) {
        is MessageContent.Location -> MessageLocation(content, onOpenLocation, modifier, colors)
        is MessageContent.Venue -> MessageVenue(content, onOpenLocation, modifier, colors)
        is MessageContent.Contact -> MessageContact(content, modifier, colors)
        is MessageContent.Poll -> MessagePoll(content, modifier, colors)
        is MessageContent.Game -> MessageGame(content, modifier, colors)
        is MessageContent.Invoice -> MessageInvoice(content, modifier, colors)
        is MessageContent.Story -> MessageStory(modifier, colors)
        is MessageContent.Expired -> MessageExpired(content, modifier, colors)
        MessageContent.Unsupported -> bubble.contentLabel?.let { ContentPlaceholder(it.resolve(), colors, modifier) }
        else -> Unit
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
