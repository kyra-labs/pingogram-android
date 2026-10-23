package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.LinkPreview
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SelfDestruct
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.feature.chat.ui.bots.botKeyboard
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.feature.chat.R as ChatR

// What one item of the conversation shows (REQUIREMENTS.md FR-C2–C4), from its ItemInputs alone.

/** A service message as a pill; anything else as a bubble. */
internal fun ItemInputs.toItem(): ChatItem {
    val content = message.content
    return if (content is MessageContent.Service) {
        ChatItem.Service(
            message.id,
            messagePreviewText(message, myUserId, ::shortNameOf),
            date,
            // A private chat's id is its user's (td_api.tl `chatTypePrivate`).
            callUserId = message.chatId.takeIf { content.action is ServiceAction.Call && kind == ChatKind.Private },
            callIsVideo = (content.action as? ServiceAction.Call)?.isVideo == true,
        )
    } else {
        toBubble(content)
    }
}

/**
 * A bubble: in group chats with the sender's name on the first of a group and the avatar beside the last, for
 * incoming messages; the time and the outgoing state on the last of a group, or where its time was tapped to show;
 * whether a failed message can be sent again; the forward's origin, the reply quote, the text or caption, the kind of
 * media, the link preview and the reactions. An album's bubble takes its time, state and reactions from its newest
 * item.
 */
private fun ItemInputs.toBubble(content: MessageContent): ChatItem.Bubble {
    val showsSender = kind == ChatKind.Group && !message.isOutgoing
    val senderName = senderName()
    val newest = album.lastOrNull() ?: message
    val text = content.text
    return ChatItem.Bubble(
        id = message.id,
        isOutgoing = message.isOutgoing,
        position = position,
        sender = senderName,
        senderId = message.sender.id,
        showsSenderName = showsSender && !position.joinsPrevious,
        avatar = if (showsSender && !position.joinsNext) avatar(senderName) else null,
        hasAvatarColumn = showsSender,
        time = timeFormatter.time(newest.date),
        isEdited = message.editDate > 0,
        status = status(newest),
        showsTime = !position.joinsNext || timeShown,
        canResend = (album.ifEmpty { listOf(message) }).any { it.canResend },
        senderUserId = (message.sender as? MessageSender.User)?.userId?.takeIf { showsSender },
        forwardedFrom = message.forwardInfo?.let(::forwardedFrom),
        reply = reply?.let { toQuote(it) },
        content = content,
        contentLabel =
            when {
                album.isNotEmpty() -> albumLabel(album)
                content is MessageContent.Text -> null
                else -> contentKindText(content)
            },
        text = text,
        spoilersRevealed = spoilersRevealed,
        linkPreview = (content as? MessageContent.Text)?.linkPreview?.let(::linkPreviewCard),
        reactions = newest.reactionChips(),
        date = date,
        album =
            album.takeIf { it.isNotEmpty() }?.let { items ->
                MessageAlbum(
                    id = items.first().mediaAlbumId,
                    messageIds = items.map { it.id }.toImmutableList(),
                    items = items.map { it.content }.toImmutableList(),
                )
            },
        isEmojiOnly = showsLargeEmoji(content, text),
        selfDestructSeconds = (newest.selfDestruct as? SelfDestruct.Timer)?.seconds,
        thread = newest.threadLink(kind),
        keyboard = album.ifEmpty { listOf(message) }.firstNotNullOfOrNull { it.botKeyboard() },
    )
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

/** "You" for the current user's messages; otherwise the sender's full name, or the chat's title. */
private fun ItemInputs.senderName(): UiText =
    if (message.isOutgoing) UiText.resource(R.string.preview_sender_you) else fullNameOf(sender)

private fun ItemInputs.avatar(name: UiText): SenderAvatar =
    SenderAvatar(
        id = message.sender.id,
        name = name,
        photo =
            when (sender) {
                is User -> sender.profilePhoto
                is ChatRef -> sender.photo
                else -> null
            },
        placeholderIcon = if ((sender as? User)?.isDeleted == true) R.drawable.ic_person_filled else null,
    )

/**
 * Where [message] is: null for incoming messages. In channels and in Saved Messages, where nobody else reads them, only
 * while it is sent and when sending failed.
 */
private fun ItemInputs.status(message: Message): OutgoingStatus? =
    when {
        !message.isOutgoing -> null
        message.sendingState is SendingState.Pending -> OutgoingStatus.Pending
        message.sendingState is SendingState.Failed -> OutgoingStatus.Failed
        kind == ChatKind.Channel || kind == ChatKind.SavedMessages -> null
        isRead -> OutgoingStatus.Read
        else -> OutgoingStatus.Sent
    }

/** Sending failed, and TDLib can send it again. */
private val Message.canResend: Boolean get() = (sendingState as? SendingState.Failed)?.canRetry == true

/** "Forwarded from X": a user's full name, a chat's title with the author's signature, or a hidden user's name. */
private fun ItemInputs.forwardedFrom(info: ForwardInfo): UiText {
    val name =
        when (val origin = info.origin) {
            is MessageOrigin.User, is MessageOrigin.HiddenUser -> originName(origin, forwardOrigin)
            is MessageOrigin.Chat -> signed(fullNameOf(forwardOrigin), origin.authorSignature)
            is MessageOrigin.Channel -> signed(fullNameOf(forwardOrigin), origin.authorSignature)
        }
    return UiText.resource(ChatR.string.chat_forwarded_from, name.bidiIsolated())
}

/** "Chat (Signature)", or the chat's name alone without a signature. */
private fun signed(
    name: UiText,
    signature: String,
): UiText =
    if (signature.isBlank()) {
        name
    } else {
        UiText.resource(ChatR.string.chat_forwarded_name_signed, name.bidiIsolated(), signature.bidiIsolated())
    }

/** Null when the preview has nothing to show. */
private fun linkPreviewCard(preview: LinkPreview): LinkPreviewCard? =
    LinkPreviewCard(preview.siteName, preview.title, preview.description.text)
        .takeIf { it.siteName.isNotBlank() || it.title.isNotBlank() || it.description.isNotBlank() }

/** The reactions of this message as chips, in TDLib's order (the most added first). */
internal fun Message.reactionChips(): ImmutableList<ReactionChip> =
    interactionInfo
        ?.reactions
        ?.map { ReactionChip(label = it.type.label, count = it.totalCount, isChosen = it.isChosen) }
        ?.toImmutableList() ?: persistentListOf()

/** The text or caption of [this] content; a poll's description; empty for content without one. */
private val MessageContent.text: FormattedText
    get() =
        when (this) {
            is MessageContent.Text -> text
            is MessageContent.Photo -> caption
            is MessageContent.Video -> caption
            is MessageContent.Animation -> caption
            is MessageContent.VoiceNote -> caption
            is MessageContent.Audio -> caption
            is MessageContent.Document -> caption
            is MessageContent.Poll -> description
            else -> FormattedText.Empty
        }
