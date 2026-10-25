package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.downloadableFileId

/**
 * What screen readers can do with a bubble's content (`docs/DESIGN_SYSTEM.md` §9). The bubble is read as one
 * ([MessageBubbleRow]), so the buttons inside it are offered as these custom actions instead.
 */
internal data class ContentAction(
    @param:StringRes val label: Int,
    val event: ChatEvent,
)

/**
 * The actions of [bubble]'s content: open media (an album's first item), play or pause a voice message or song as
 * [isPlaying] says, download, cancel or open a document as far as its [fileState] goes, open a location; none for
 * other content.
 */
internal fun contentActions(
    bubble: ChatItem.Bubble,
    fileState: FileState?,
    isPlaying: Boolean = false,
): List<ContentAction> {
    val album = bubble.album
    val content = bubble.content
    val fileId = content.downloadableFileId
    return when {
        album != null -> {
            listOfNotNull(album.messageIds.firstOrNull()?.let { ContentAction(OPEN, ChatEvent.OpenMedia(it)) })
        }

        content is MessageContent.Photo ||
            content is MessageContent.Video ||
            content is MessageContent.Animation ||
            content is MessageContent.VideoNote -> {
            listOf(ContentAction(OPEN, ChatEvent.OpenMedia(bubble.id)))
        }

        content is MessageContent.Location || content is MessageContent.Venue -> {
            listOf(ContentAction(OPEN, ChatEvent.OpenLocation(bubble.id)))
        }

        fileId == null -> {
            emptyList()
        }

        content is MessageContent.Document -> {
            listOfNotNull(documentAction(bubble.id, fileId, fileState))
        }

        else -> {
            val label = if (isPlaying) R.string.content_pause else R.string.content_play
            listOf(ContentAction(label, ChatEvent.PlayPause(bubble.id, fileId)))
        }
    }
}

/**
 * The message actions screen readers get directly on [bubble], each checked against TDLib when chosen: sending a failed
 * message again, replying to a sent one, copying its text, replying to it in another chat and copying its link, as
 * [shortcuts] allow in this chat. The rest are in the actions sheet.
 */
internal fun messageShortcuts(
    bubble: ChatItem.Bubble,
    shortcuts: MessageShortcuts,
): List<ContentAction> =
    listOfNotNull(
        ContentAction(R.string.message_action_resend, ChatEvent.Resend(bubble.id))
            .takeIf { bubble.status == OutgoingStatus.Failed },
        ContentAction(
            R.string.message_action_reply,
            ChatEvent.Reply(bubble.id),
        ).takeIf { bubble.canBeRepliedTo(shortcuts) },
        ContentAction(R.string.message_action_copy, ChatEvent.Copy(bubble.id))
            .takeIf { shortcuts.canCopy && bubble.text.text.isNotEmpty() },
        ContentAction(R.string.message_action_reply_elsewhere, ReplyEvent.ReplyElsewhere(bubble.id))
            .takeIf { shortcuts.canReplyElsewhere && bubble.isSent },
        ContentAction(shortcuts.linkLabel, ReplyEvent.CopyLink(bubble.id))
            .takeIf { shortcuts.canCopyLink && bubble.isSent },
    )

/** "Copy link to this thread" in a topic's or thread's conversation, else "Copy link". */
private val MessageShortcuts.linkLabel: Int
    get() = if (linksInThread) R.string.message_action_copy_thread_link else R.string.message_action_copy_link

/** Whether this bubble's message reached Telegram: not still being sent, nor failed. */
private val ChatItem.Bubble.isSent: Boolean
    get() = status != OutgoingStatus.Pending && status != OutgoingStatus.Failed

/** Cancelling the [uploads] of a bubble's messages being sent, all at once; null while none uploads. */
internal fun uploadAction(uploads: Map<Long, *>): ContentAction? =
    uploads.keys.toList().takeIf { it.isNotEmpty() }?.let {
        ContentAction(R.string.attach_cancel_upload, ChatEvent.CancelUpload(it))
    }

/** Opening the sender's profile, for an incoming message of a user in a group chat; null otherwise. */
internal fun profileAction(bubble: ChatItem.Bubble): ContentAction? =
    bubble.senderUserId?.let { ContentAction(R.string.chat_open_profile, ChatEvent.OpenProfile(it)) }

/** Showing the replied message, for a bubble whose quote opens one ([ReplyQuote.messageId]); null otherwise. */
internal fun replyAction(bubble: ChatItem.Bubble): ContentAction? =
    bubble.reply?.messageId?.let { ContentAction(R.string.chat_reply_open, ChatEvent.OpenReply(it)) }

/** Open once downloaded, cancel while downloading, else download if TDLib can. */
private fun documentAction(
    messageId: Long,
    fileId: Int,
    fileState: FileState?,
): ContentAction? =
    when (fileState) {
        is FileState.Downloaded -> ContentAction(OPEN, ChatEvent.OpenFile(messageId, fileId))
        is FileState.Downloading -> ContentAction(R.string.content_cancel_download, ChatEvent.CancelDownload(fileId))
        is FileState.NotDownloaded -> fileState.downloadAction(fileId)
        null -> ContentAction(R.string.content_download, ChatEvent.DownloadFile(fileId))
    }

private fun FileState.NotDownloaded.downloadAction(fileId: Int): ContentAction? =
    if (canBeDownloaded) ContentAction(R.string.content_download, ChatEvent.DownloadFile(fileId)) else null

private val OPEN = R.string.content_open

/**
 * Whether the user can reply to this message now: where they can write ([MessageShortcuts.canReply]), once it is
 * sent. Screen readers' "Reply" and the swipe to reply both ask.
 */
internal fun ChatItem.Bubble.canBeRepliedTo(shortcuts: MessageShortcuts): Boolean =
    shortcuts.canReply && status != OutgoingStatus.Pending && status != OutgoingStatus.Failed
