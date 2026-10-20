package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatFolder
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatPosition
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.UnreadChatCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/**
 * The [Chat] for a TDLib chat; [mutedScopes] are muted by default, for a chat without a mute setting of its own.
 * Null for a chat type added by a newer TDLib: Pingogram could not open such a chat, so it is left out.
 */
internal fun TdApi.Chat.toChat(mutedScopes: Set<NotificationScope>): Chat? {
    val type = type.toChatType() ?: return null
    return Chat(
        id = id,
        type = type,
        title = title,
        photo = photo?.toProfilePhoto(),
        lastMessage = lastMessage?.toMessage(),
        positions = positions.toChatPositions(),
        unreadCount = unreadCount,
        unreadMentionCount = unreadMentionCount,
        unreadReactionCount = unreadReactionCount,
        lastReadInboxMessageId = lastReadInboxMessageId,
        lastReadOutboxMessageId = lastReadOutboxMessageId,
        isMarkedAsUnread = isMarkedAsUnread,
        isMuted = resolveMute(notificationSettings.ownMute, type, mutedScopes),
        draft = draftMessage?.toChatDraft(),
        hasProtectedContent = hasProtectedContent,
        messageAutoDeleteTime = messageAutoDeleteTime,
        permissions = permissions.toChatPermissions(),
        messageSender = messageSenderId?.toMessageSender(),
    )
}

/** Null for a chat type added by a newer TDLib. */
internal fun TdApi.ChatType.toChatType(): ChatType? =
    when (this) {
        is TdApi.ChatTypePrivate -> ChatType.Private(userId)
        is TdApi.ChatTypeSecret -> ChatType.Secret(secretChatId, userId)
        is TdApi.ChatTypeBasicGroup -> ChatType.BasicGroup(basicGroupId)
        is TdApi.ChatTypeSupergroup -> ChatType.Supergroup(supergroupId, isChannel)
        else -> null
    }

/** Null for a chat list type added by a newer TDLib. */
internal fun TdApi.ChatList.toChatListType(): ChatListType? =
    when (this) {
        is TdApi.ChatListMain -> ChatListType.Main
        is TdApi.ChatListArchive -> ChatListType.Archive
        is TdApi.ChatListFolder -> ChatListType.Folder(chatFolderId)
        else -> null
    }

internal fun ChatListType.toTdChatList(): TdApi.ChatList =
    when (this) {
        ChatListType.Main -> TdApi.ChatListMain()
        ChatListType.Archive -> TdApi.ChatListArchive()
        is ChatListType.Folder -> TdApi.ChatListFolder(id)
    }

/** Null for a position in a chat list type added by a newer TDLib. */
internal fun TdApi.ChatPosition.toChatPosition(): ChatPosition? =
    list.toChatListType()?.let { ChatPosition(it, order, isPinned) }

/** All positions of a chat, as `chat` and the updates that replace them send them: only lists the chat is in. */
internal fun Array<TdApi.ChatPosition>.toChatPositions(): ImmutableList<ChatPosition> =
    mapNotNull { it.toChatPosition() }.toImmutableList()

/**
 * The draft with the message of the same chat it replies to. TDLib reports replies to stories and to messages of other
 * chats too, which the composer does not make; they count as no reply.
 */
internal fun TdApi.DraftMessage.toChatDraft(): ChatDraft =
    ChatDraft(
        content = content.toDraftContent(),
        date = date.toLong(),
        replyToMessageId = (replyTo as? TdApi.InputMessageReplyToMessage)?.messageId,
    )

private fun TdApi.DraftMessageContent.toDraftContent(): DraftContent =
    when (this) {
        is TdApi.DraftMessageContentText -> DraftContent.Text(text.toFormattedText())

        is TdApi.DraftMessageContentVoiceNote -> DraftContent.VoiceNote

        is TdApi.DraftMessageContentVideoNote -> DraftContent.VideoNote

        // Rich messages, and content added by a newer TDLib.
        else -> DraftContent.Unsupported
    }

internal fun TdApi.ChatFolderInfo.toChatFolder(): ChatFolder =
    ChatFolder(
        id = id,
        title = name.text.toFormattedText(),
        iconName = icon.name,
        // -1: no color.
        colorId = colorId.takeIf { it >= 0 },
    )

internal fun TdApi.UpdateUnreadChatCount.toUnreadChatCount(): UnreadChatCount =
    UnreadChatCount(
        totalCount = totalCount,
        unreadCount = unreadCount,
        unreadUnmutedCount = unreadUnmutedCount,
        markedAsUnreadCount = markedAsUnreadCount,
        markedAsUnreadUnmutedCount = markedAsUnreadUnmutedCount,
    )
