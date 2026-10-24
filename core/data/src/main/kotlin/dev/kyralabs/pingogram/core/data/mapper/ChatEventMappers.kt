package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatEvent
import dev.kyralabs.pingogram.core.domain.model.ChatEventAction
import dev.kyralabs.pingogram.core.domain.model.ChatEventFilter
import dev.kyralabs.pingogram.core.domain.model.ChatEventSetting
import dev.kyralabs.pingogram.core.domain.model.InviteLinkChange
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import org.drinkless.tdlib.TdApi

/**
 * The [ChatEvent] of a TDLib `chatEvent` of chat [chatId]; an actor of a kind this version does not know is the chat.
 */
internal fun TdApi.ChatEvent.toChatEvent(chatId: Long): ChatEvent =
    ChatEvent(
        id = id,
        date = date.toLong(),
        actor = memberId?.toMessageSender() ?: MessageSender.Chat(chatId),
        action = action.toChatEventAction(),
    )

private fun TdApi.ChatEventAction.toChatEventAction(): ChatEventAction =
    messageAction() ?: memberAction() ?: infoAction() ?: settingAction() ?: ChatEventAction.Other

private fun TdApi.ChatEventAction.messageAction(): ChatEventAction? =
    when (this) {
        is TdApi.ChatEventMessageEdited -> ChatEventAction.MessageEdited(oldMessage.toMessage(), newMessage.toMessage())
        is TdApi.ChatEventMessageDeleted -> ChatEventAction.MessageDeleted(message.toMessage())
        is TdApi.ChatEventMessagePinned -> ChatEventAction.MessagePinned(message.toMessage())
        is TdApi.ChatEventMessageUnpinned -> ChatEventAction.MessageUnpinned(message.toMessage())
        else -> null
    }

private fun TdApi.ChatEventAction.memberAction(): ChatEventAction? =
    when (this) {
        is TdApi.ChatEventMemberJoined -> {
            ChatEventAction.MemberJoined()
        }

        is TdApi.ChatEventMemberJoinedByInviteLink -> {
            ChatEventAction.MemberJoined(inviteLink = inviteLink?.inviteLink)
        }

        is TdApi.ChatEventMemberJoinedByRequest -> {
            ChatEventAction.MemberJoined(inviteLink = inviteLink?.inviteLink, approverUserId = approverUserId)
        }

        is TdApi.ChatEventMemberLeft -> {
            ChatEventAction.MemberLeft
        }

        is TdApi.ChatEventMemberInvited -> {
            ChatEventAction.MemberInvited(userId)
        }

        is TdApi.ChatEventMemberPromoted -> {
            ChatEventAction.MemberRoleChanged(
                MessageSender.User(userId),
                oldStatus.toMemberRole(),
                newStatus.toMemberRole(),
            )
        }

        is TdApi.ChatEventMemberRestricted -> {
            memberId.toMessageSender()?.let { member ->
                ChatEventAction.MemberRoleChanged(member, oldStatus.toMemberRole(), newStatus.toMemberRole())
            }
        }

        else -> {
            null
        }
    }

private fun TdApi.ChatEventAction.infoAction(): ChatEventAction? =
    when (this) {
        is TdApi.ChatEventTitleChanged -> ChatEventAction.TitleChanged(oldTitle, newTitle)

        is TdApi.ChatEventDescriptionChanged -> ChatEventAction.DescriptionChanged(newDescription)

        is TdApi.ChatEventUsernameChanged -> ChatEventAction.UsernameChanged(newUsername)

        is TdApi.ChatEventPhotoChanged -> ChatEventAction.PhotoChanged(removed = newPhoto == null)

        is TdApi.ChatEventLinkedChatChanged -> ChatEventAction.LinkedChatChanged(newLinkedChatId)

        is TdApi.ChatEventInviteLinkEdited -> linkChanged(newInviteLink, InviteLinkChange.Edited)

        is TdApi.ChatEventInviteLinkRevoked -> linkChanged(inviteLink, InviteLinkChange.Revoked)

        is TdApi.ChatEventInviteLinkDeleted -> linkChanged(inviteLink, InviteLinkChange.Deleted)

        is TdApi.ChatEventVideoChatCreated -> ChatEventAction.VideoChatStarted

        is TdApi.ChatEventVideoChatEnded -> ChatEventAction.VideoChatEnded

        is TdApi.ChatEventForumTopicCreated,
        is TdApi.ChatEventForumTopicEdited,
        is TdApi.ChatEventForumTopicToggleIsClosed,
        is TdApi.ChatEventForumTopicToggleIsHidden,
        is TdApi.ChatEventForumTopicDeleted,
        is TdApi.ChatEventForumTopicPinned,
        -> ChatEventAction.TopicChanged

        else -> null
    }

private fun linkChanged(
    link: TdApi.ChatInviteLink?,
    change: InviteLinkChange,
): ChatEventAction = link?.let { ChatEventAction.InviteLinkChanged(it.inviteLink, change) } ?: ChatEventAction.Other

private fun TdApi.ChatEventAction.settingAction(): ChatEventAction? =
    when (this) {
        is TdApi.ChatEventPermissionsChanged -> {
            ChatEventAction.PermissionsChanged
        }

        is TdApi.ChatEventSlowModeDelayChanged -> {
            ChatEventAction.SlowModeChanged(newSlowModeDelay)
        }

        is TdApi.ChatEventAvailableReactionsChanged -> {
            ChatEventAction.ReactionsChanged
        }

        is TdApi.ChatEventSignMessagesToggled -> {
            ChatEventAction.SettingToggled(ChatEventSetting.SignMessages, signMessages)
        }

        is TdApi.ChatEventShowMessageSenderToggled -> {
            ChatEventAction.SettingToggled(ChatEventSetting.ShowMessageSender, showMessageSender)
        }

        is TdApi.ChatEventHasProtectedContentToggled -> {
            ChatEventAction.SettingToggled(ChatEventSetting.ProtectedContent, hasProtectedContent)
        }

        is TdApi.ChatEventIsForumToggled -> {
            ChatEventAction.SettingToggled(ChatEventSetting.Topics, isForum)
        }

        is TdApi.ChatEventInvitesToggled -> {
            ChatEventAction.SettingToggled(ChatEventSetting.Invites, canInviteUsers)
        }

        is TdApi.ChatEventIsAllHistoryAvailableToggled -> {
            ChatEventAction.SettingToggled(ChatEventSetting.HistoryForNewMembers, isAllHistoryAvailable)
        }

        else -> {
            null
        }
    }

/** TDLib's filters for [filters]; none at all reads every kind of action, as TDLib does with a null filter. */
internal fun Set<ChatEventFilter>.toTdFilters(): TdApi.ChatEventLogFilters? {
    if (isEmpty()) return null
    return TdApi.ChatEventLogFilters().also {
        it.messageEdits = ChatEventFilter.MessageEdits in this
        it.messageDeletions = ChatEventFilter.MessageDeletions in this
        it.messagePins = ChatEventFilter.MessagePins in this
        it.memberJoins = ChatEventFilter.MemberJoins in this
        it.memberLeaves = ChatEventFilter.MemberLeaves in this
        it.memberInvites = ChatEventFilter.MemberInvites in this
        it.memberPromotions = ChatEventFilter.MemberPromotions in this
        it.memberRestrictions = ChatEventFilter.MemberRestrictions in this
        it.infoChanges = ChatEventFilter.InfoChanges in this
        it.settingChanges = ChatEventFilter.SettingChanges in this
        it.inviteLinkChanges = ChatEventFilter.InviteLinkChanges in this
        it.videoChatChanges = ChatEventFilter.VideoChatChanges in this
        it.forumChanges = ChatEventFilter.TopicChanges in this
    }
}
