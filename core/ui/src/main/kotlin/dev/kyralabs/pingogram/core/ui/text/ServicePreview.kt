package dev.kyralabs.pingogram.core.ui.text

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.ui.R

/**
 * What a service message reports ([messagePreviewText]), naming its sender: "Alice joined the group", "You pinned a
 * message". Events of channels, which have no visible author, and events nobody in particular caused are worded
 * without one.
 */
internal fun serviceText(
    action: ServiceAction,
    message: Message,
    myUserId: Long?,
    name: (MessageSender) -> UiText?,
): UiText {
    val sender = message.sender
    val byMe = message.isOutgoing || (myUserId != null && sender == MessageSender.User(myUserId))
    val actor = Actor(byMe, name(sender)?.bidiIsolated() ?: someone())
    return when (action) {
        is ServiceAction.MembersAdded -> {
            membersAddedText(action.userIds, sender, myUserId, actor) { memberName(it, name) }
        }

        is ServiceAction.MemberRemoved -> {
            memberRemovedText(action.userId, sender, myUserId, actor) { memberName(it, name) }
        }

        is ServiceAction.Call -> {
            callText(action, isOutgoing = byMe)
        }

        else -> {
            eventText(action) ?: actor.did(action)
        }
    }
}

private fun someone() = UiText.resource(R.string.preview_someone)

/** The name of the member [userId] of a service message, isolated as an argument; "Someone" if not known. */
private fun memberName(
    userId: Long,
    name: (MessageSender) -> UiText?,
): UiText = name(MessageSender.User(userId))?.bidiIsolated() ?: someone()

/** Events worded without whoever caused them; null for the actions that name their sender. */
private fun eventText(action: ServiceAction): UiText? =
    when (action) {
        is ServiceAction.ChannelCreated -> {
            UiText.resource(R.string.preview_channel_created)
        }

        is ServiceAction.UpgradedToSupergroup, is ServiceAction.UpgradedFromBasicGroup -> {
            UiText.resource(R.string.preview_upgraded)
        }

        is ServiceAction.AutoDeleteTimerChanged -> {
            val text = if (action.seconds > 0) R.string.preview_auto_delete_on else R.string.preview_auto_delete_off
            UiText.resource(text)
        }

        is ServiceAction.VideoChatScheduled -> {
            UiText.resource(R.string.preview_video_chat_scheduled)
        }

        ServiceAction.VideoChatStarted -> {
            UiText.resource(R.string.preview_video_chat_started)
        }

        is ServiceAction.VideoChatEnded -> {
            UiText.resource(R.string.preview_video_chat_ended)
        }

        is ServiceAction.Custom -> {
            UiText.Plain(previewLine(action.text))
        }

        ServiceAction.Other -> {
            UiText.resource(R.string.preview_service_other)
        }

        else -> {
            null
        }
    }

/** The sender of a service message: the current user ([byMe]) or someone by [name]. */
private class Actor(
    val byMe: Boolean,
    val name: UiText,
) {
    /** What [action] says about this actor: "Alice changed the photo", "You left the group". */
    fun did(action: ServiceAction): UiText =
        when (action) {
            is ServiceAction.BasicGroupCreated -> {
                createdGroup(action.title)
            }

            is ServiceAction.SupergroupCreated -> {
                createdGroup(action.title)
            }

            is ServiceAction.TitleChanged -> {
                did(R.string.preview_you_changed_title, R.string.preview_changed_title, action.title.bidiIsolated())
            }

            is ServiceAction.PhotoChanged -> {
                did(R.string.preview_you_changed_photo, R.string.preview_changed_photo)
            }

            ServiceAction.PhotoDeleted -> {
                did(R.string.preview_you_removed_photo, R.string.preview_removed_photo)
            }

            ServiceAction.MemberJoined, ServiceAction.MemberJoinedByRequest -> {
                joined()
            }

            ServiceAction.MemberJoinedByLink -> {
                did(R.string.preview_you_joined_by_link, R.string.preview_joined_by_link)
            }

            ServiceAction.MemberLeft -> {
                left()
            }

            is ServiceAction.MessagePinned -> {
                did(R.string.preview_you_pinned, R.string.preview_pinned)
            }

            ServiceAction.ScreenshotTaken -> {
                did(R.string.preview_you_took_screenshot, R.string.preview_took_screenshot)
            }

            ServiceAction.ContactRegistered -> {
                UiText.resource(R.string.preview_contact_registered, name)
            }

            is ServiceAction.VideoChatInvited -> {
                did(R.string.preview_you_invited_to_video_chat, R.string.preview_invited_to_video_chat)
            }

            // Worded by the other functions of this file.
            else -> {
                UiText.resource(R.string.preview_service_other)
            }
        }

    fun joined(): UiText = did(R.string.preview_you_joined, R.string.preview_joined)

    fun left(): UiText = did(R.string.preview_you_left, R.string.preview_left)

    private fun createdGroup(title: String): UiText =
        did(R.string.preview_you_created_group, R.string.preview_created_group, title.bidiIsolated())

    /** [mine] with [args] for the current user's action; [theirs] with the name, then [args], for anyone else's. */
    fun did(
        @StringRes mine: Int,
        @StringRes theirs: Int,
        vararg args: Any,
    ): UiText = if (byMe) UiText.resource(mine, *args) else UiText.resource(theirs, name, *args)
}

/** Joining shows as adding oneself; adding the current user reads "added you". */
private fun membersAddedText(
    userIds: List<Long>,
    sender: MessageSender,
    myUserId: Long?,
    actor: Actor,
    userName: (Long) -> UiText,
): UiText {
    val only = userIds.singleOrNull()
    return when {
        only != null && sender == MessageSender.User(only) -> {
            actor.joined()
        }

        only != null && only == myUserId && !actor.byMe -> {
            UiText.resource(R.string.preview_added_you, actor.name)
        }

        only != null -> {
            actor.did(R.string.preview_you_added_member, R.string.preview_added_member, userName(only))
        }

        actor.byMe -> {
            UiText.plural(R.plurals.preview_you_added_members, userIds.size, userIds.size)
        }

        else -> {
            UiText.plural(R.plurals.preview_added_members, userIds.size, actor.name, userIds.size)
        }
    }
}

/** Removing oneself shows as leaving; removing the current user reads "removed you". */
private fun memberRemovedText(
    userId: Long,
    sender: MessageSender,
    myUserId: Long?,
    actor: Actor,
    userName: (Long) -> UiText,
): UiText =
    when {
        sender == MessageSender.User(userId) -> actor.left()
        userId == myUserId && !actor.byMe -> UiText.resource(R.string.preview_removed_you, actor.name)
        else -> actor.did(R.string.preview_you_removed_member, R.string.preview_removed_member, userName(userId))
    }

/** From the current user's side: the caller is the sender of the call message. */
private fun callText(
    call: ServiceAction.Call,
    isOutgoing: Boolean,
): UiText {
    val (voice, video) =
        when {
            call.discardReason == CallDiscardReason.Missed && isOutgoing -> {
                R.string.preview_call_cancelled to R.string.preview_video_call_cancelled
            }

            call.discardReason == CallDiscardReason.Missed -> {
                R.string.preview_call_missed to R.string.preview_video_call_missed
            }

            call.discardReason == CallDiscardReason.Declined -> {
                R.string.preview_call_declined to R.string.preview_video_call_declined
            }

            isOutgoing -> {
                R.string.preview_call_outgoing to R.string.preview_video_call_outgoing
            }

            else -> {
                R.string.preview_call_incoming to R.string.preview_video_call_incoming
            }
        }
    return UiText.resource(if (call.isVideo) video else voice)
}
