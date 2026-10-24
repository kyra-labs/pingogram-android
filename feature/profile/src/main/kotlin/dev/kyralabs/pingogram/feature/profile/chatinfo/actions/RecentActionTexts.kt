package dev.kyralabs.pingogram.feature.profile.chatinfo.actions

import dev.kyralabs.pingogram.core.domain.model.ChatEventAction
import dev.kyralabs.pingogram.core.domain.model.ChatEventSetting
import dev.kyralabs.pingogram.core.domain.model.InviteLinkChange
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What [action] says under its actor's name ("Changed the name to “Rust Berlin”"), with [name] naming the other
 * people and chats it concerns, isolated where the string is built.
 */
internal fun actionText(
    action: ChatEventAction,
    name: (MessageSender) -> UiText,
): UiText =
    messageText(action)
        ?: memberText(action, name)
        ?: infoText(action, name)
        ?: settingText(action)
        ?: UiText.resource(R.string.recent_actions_other)

/** The message an action concerns, in one line: the new text of an edited one. */
internal fun actionMessage(
    action: ChatEventAction,
    name: (MessageSender) -> UiText?,
): UiText? {
    val message =
        when (action) {
            is ChatEventAction.MessageEdited -> action.new
            is ChatEventAction.MessageDeleted -> action.message
            is ChatEventAction.MessagePinned -> action.message
            is ChatEventAction.MessageUnpinned -> action.message
            else -> return null
        }
    return messagePreviewText(message, myUserId = null, name = name)
}

private fun messageText(action: ChatEventAction): UiText? =
    when (action) {
        is ChatEventAction.MessageEdited -> UiText.resource(R.string.recent_actions_edited)
        is ChatEventAction.MessageDeleted -> UiText.resource(R.string.recent_actions_deleted)
        is ChatEventAction.MessagePinned -> UiText.resource(R.string.recent_actions_pinned)
        is ChatEventAction.MessageUnpinned -> UiText.resource(R.string.recent_actions_unpinned)
        else -> null
    }

private fun memberText(
    action: ChatEventAction,
    name: (MessageSender) -> UiText,
): UiText? =
    when (action) {
        is ChatEventAction.MemberJoined -> {
            when {
                action.approverUserId != 0L -> {
                    UiText.resource(
                        R.string.recent_actions_joined_approved,
                        name(MessageSender.User(action.approverUserId)),
                    )
                }

                action.inviteLink != null -> {
                    UiText.resource(R.string.recent_actions_joined_link)
                }

                else -> {
                    UiText.resource(R.string.recent_actions_joined)
                }
            }
        }

        ChatEventAction.MemberLeft -> {
            UiText.resource(R.string.recent_actions_left)
        }

        is ChatEventAction.MemberInvited -> {
            UiText.resource(R.string.recent_actions_invited, name(MessageSender.User(action.userId)))
        }

        is ChatEventAction.MemberRoleChanged -> {
            UiText.resource(roleChange(action.old, action.new), name(action.member))
        }

        else -> {
            null
        }
    }

/**
 * What a change of role says: promoted, rights changed, dismissed, banned, restricted, restrictions lifted, let back.
 */
private fun roleChange(
    old: MemberRole,
    new: MemberRole,
): Int =
    when {
        new is MemberRole.Administrator || new is MemberRole.Owner -> {
            val wasAdmin = old is MemberRole.Administrator
            if (wasAdmin) R.string.recent_actions_rights_changed else R.string.recent_actions_promoted
        }

        old is MemberRole.Administrator -> {
            R.string.recent_actions_dismissed
        }

        new is MemberRole.Banned -> {
            R.string.recent_actions_banned
        }

        new is MemberRole.Restricted -> {
            R.string.recent_actions_restricted
        }

        old is MemberRole.Banned -> {
            R.string.recent_actions_unbanned
        }

        else -> {
            R.string.recent_actions_unrestricted
        }
    }

private fun infoText(
    action: ChatEventAction,
    name: (MessageSender) -> UiText,
): UiText? =
    when (action) {
        is ChatEventAction.TitleChanged -> {
            UiText.resource(R.string.recent_actions_title, UiText.Plain(action.new).bidiIsolated())
        }

        is ChatEventAction.DescriptionChanged -> {
            UiText.resource(R.string.recent_actions_description)
        }

        is ChatEventAction.UsernameChanged -> {
            if (action.new.isEmpty()) {
                UiText.resource(R.string.recent_actions_username_removed)
            } else {
                UiText.resource(R.string.recent_actions_username, UiText.Plain("t.me/${action.new}").bidiIsolated())
            }
        }

        is ChatEventAction.PhotoChanged -> {
            UiText.resource(
                if (action.removed) R.string.recent_actions_photo_removed else R.string.recent_actions_photo,
            )
        }

        is ChatEventAction.LinkedChatChanged -> {
            if (action.chatId == 0L) {
                UiText.resource(R.string.recent_actions_unlinked)
            } else {
                UiText.resource(R.string.recent_actions_linked, name(MessageSender.Chat(action.chatId)))
            }
        }

        is ChatEventAction.InviteLinkChanged -> {
            val res =
                when (action.change) {
                    InviteLinkChange.Edited -> R.string.recent_actions_link_edited
                    InviteLinkChange.Revoked -> R.string.recent_actions_link_revoked
                    InviteLinkChange.Deleted -> R.string.recent_actions_link_deleted
                }
            UiText.resource(res, UiText.Plain(action.link.removePrefix("https://")).bidiIsolated())
        }

        ChatEventAction.VideoChatStarted -> {
            UiText.resource(R.string.recent_actions_voice_chat_started)
        }

        ChatEventAction.VideoChatEnded -> {
            UiText.resource(R.string.recent_actions_voice_chat_ended)
        }

        ChatEventAction.TopicChanged -> {
            UiText.resource(R.string.recent_actions_topic)
        }

        else -> {
            null
        }
    }

private fun settingText(action: ChatEventAction): UiText? =
    when (action) {
        ChatEventAction.PermissionsChanged -> {
            UiText.resource(R.string.recent_actions_permissions)
        }

        ChatEventAction.ReactionsChanged -> {
            UiText.resource(R.string.recent_actions_reactions)
        }

        is ChatEventAction.SlowModeChanged -> {
            if (action.seconds == 0) {
                UiText.resource(R.string.recent_actions_slow_mode_off)
            } else {
                UiText.resource(R.string.recent_actions_slow_mode, durationUiText(action.seconds))
            }
        }

        is ChatEventAction.SettingToggled -> {
            UiText.resource(toggleText(action.setting, action.on))
        }

        else -> {
            null
        }
    }

private fun toggleText(
    setting: ChatEventSetting,
    on: Boolean,
): Int =
    when (setting) {
        ChatEventSetting.SignMessages -> {
            if (on) R.string.recent_actions_signatures_on else R.string.recent_actions_signatures_off
        }

        ChatEventSetting.ShowMessageSender -> {
            if (on) R.string.recent_actions_profiles_on else R.string.recent_actions_profiles_off
        }

        ChatEventSetting.ProtectedContent -> {
            if (on) R.string.recent_actions_protected_on else R.string.recent_actions_protected_off
        }

        ChatEventSetting.Topics -> {
            if (on) R.string.recent_actions_topics_on else R.string.recent_actions_topics_off
        }

        ChatEventSetting.Invites -> {
            if (on) R.string.recent_actions_invites_on else R.string.recent_actions_invites_off
        }

        ChatEventSetting.HistoryForNewMembers -> {
            if (on) R.string.recent_actions_history_on else R.string.recent_actions_history_off
        }
    }

/** A slow mode delay as "30 seconds", "5 minutes" or "1 hour". */
private fun durationUiText(seconds: Int): UiText =
    when {
        seconds < SECONDS_PER_MINUTE -> {
            UiText.plural(UiR.plurals.duration_seconds, seconds, seconds)
        }

        seconds < SECONDS_PER_HOUR -> {
            val minutes = seconds / SECONDS_PER_MINUTE
            UiText.plural(UiR.plurals.duration_minutes, minutes, minutes)
        }

        else -> {
            val hours = seconds / SECONDS_PER_HOUR
            UiText.plural(UiR.plurals.duration_hours, hours, hours)
        }
    }

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

/** The people and chats [this] names, besides its actor: the senders of its messages, the members it concerns. */
internal fun ChatEventAction.senders(): Set<MessageSender> =
    when (this) {
        is ChatEventAction.MessageEdited -> setOf(new.sender)
        is ChatEventAction.MessageDeleted -> setOf(message.sender)
        is ChatEventAction.MessagePinned -> setOf(message.sender)
        is ChatEventAction.MessageUnpinned -> setOf(message.sender)
        is ChatEventAction.MemberJoined -> setOfNotNull(approverUserId.takeIf { it != 0L }?.let(MessageSender::User))
        is ChatEventAction.MemberInvited -> setOf(MessageSender.User(userId))
        is ChatEventAction.MemberRoleChanged -> setOf(member)
        is ChatEventAction.LinkedChatChanged -> setOfNotNull(chatId.takeIf { it != 0L }?.let(MessageSender::Chat))
        else -> emptySet()
    }
