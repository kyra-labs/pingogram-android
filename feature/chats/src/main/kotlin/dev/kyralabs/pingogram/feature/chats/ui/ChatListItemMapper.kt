package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.isOnlineAt
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.chatActionText
import dev.kyralabs.pingogram.core.ui.text.draftPreviewText
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.shortName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

/**
 * What rows show besides their chats, as of one emission: the users, supergroups and actions of every chat, the
 * current user, and the minute (for times and online dots that expire).
 */
internal class RowSnapshot(
    val users: ImmutableMap<Long, User>,
    val supergroups: ImmutableMap<Long, Supergroup>,
    val actionsByChat: ImmutableMap<Long, ImmutableList<SenderAction>>,
    val myUserId: Long?,
    val now: Instant,
)

/** What the rows of one emission are made from: the [snapshot], the list shown (for pins), and how to format times. */
internal class RowSources(
    val list: ChatListType,
    val snapshot: RowSnapshot,
    val timeFormatter: ChatListTimeFormatter,
)

/**
 * Maps chats to [ChatListItemModel]s, reusing the previous row of a chat when everything it is made from is the same
 * (`docs/PERFORMANCE.md` §4): user statuses change many times a minute, and each change emits every row. The inputs
 * are compared field by field, mostly by identity, as the repositories keep unchanged objects; only a changed chat,
 * one of its users, its group, its actions, the list, the current user or the minute maps a row again.
 *
 * Not thread-safe: one flow collection uses it at a time.
 */
internal class ChatListItemMapper {
    private var previous: Map<Long, Row> = emptyMap()
    private var previousItems: ImmutableList<ChatListItemModel> = persistentListOf()

    fun map(
        chats: List<Chat>,
        sources: RowSources,
    ): ImmutableList<ChatListItemModel> {
        val rows = HashMap<Long, Row>(chats.size * 2)
        var reusedAll = chats.size == previousItems.size
        val items =
            chats.mapIndexed { index, chat ->
                val inputs = sources.inputsOf(chat)
                val reused = previous[chat.id]?.takeIf { it.inputs == inputs }
                val row = reused ?: Row(inputs, inputs.toItem(sources))
                rows[chat.id] = row
                if (reusedAll && (reused == null || previousItems[index] !== row.item)) reusedAll = false
                row.item
            }
        previous = rows
        // Nothing changed: the same list instance, so the state flow does not even compare the rows.
        if (!reusedAll) previousItems = items.toImmutableList()
        return previousItems
    }

    private class Row(
        val inputs: RowInputs,
        val item: ChatListItemModel,
    )
}

/**
 * Everything one row is made from. [referencedUsers] are the users whose names it may show (the last message's sender,
 * members of a service message, people typing), in the order [referencedUserIds] lists them.
 */
private data class RowInputs(
    val chat: Chat,
    val list: ChatListType,
    val user: User?,
    val supergroup: Supergroup?,
    val actions: ImmutableList<SenderAction>?,
    val referencedUsers: List<User?>,
    val myUserId: Long?,
    val now: Instant,
    val timeFormatter: ChatListTimeFormatter,
)

private fun RowSources.inputsOf(chat: Chat): RowInputs {
    val actions = snapshot.actionsByChat[chat.id]
    val users = snapshot.users
    return RowInputs(
        chat = chat,
        list = list,
        user = chat.userId?.let(users::get),
        supergroup = (chat.type as? ChatType.Supergroup)?.let { snapshot.supergroups[it.supergroupId] },
        actions = actions,
        referencedUsers = referencedUserIds(chat, actions).map(users::get),
        myUserId = snapshot.myUserId,
        now = snapshot.now,
        timeFormatter = timeFormatter,
    )
}

/** The users whose names a row may show; [toItem] looks up no others. */
private fun referencedUserIds(
    chat: Chat,
    actions: List<SenderAction>?,
): List<Long> =
    buildList {
        val message = chat.lastMessage
        (message?.sender as? MessageSender.User)?.let { add(it.userId) }
        when (val action = (message?.content as? MessageContent.Service)?.action) {
            is ServiceAction.MembersAdded -> action.userIds.singleOrNull()?.let(::add)
            is ServiceAction.MemberRemoved -> add(action.userId)
            else -> Unit
        }
        actions?.forEach { (it.sender as? MessageSender.User)?.let { sender -> add(sender.userId) } }
    }

private fun RowInputs.toItem(sources: RowSources): ChatListItemModel {
    val isSavedMessages = chat.type is ChatType.Private && chat.userId == myUserId
    val isDeletedAccount = user?.isDeleted == true
    val name = senderNames(sources.snapshot.users)
    val preview = preview(isSavedMessages, name)
    val lastMessage = chat.lastMessage
    val isPinned = chat.positionIn(list)?.isPinned == true
    return ChatListItemModel(
        id = chat.id,
        title = title(isSavedMessages, isDeletedAccount),
        photo = if (isSavedMessages) null else chat.photo,
        placeholderIcon = placeholderIcon(isSavedMessages, isDeletedAccount),
        isOnline = !isSavedMessages && isUserOnline,
        badge = badge,
        isSecret = chat.type is ChatType.Secret,
        isMuted = chat.isMuted,
        isPinned = isPinned,
        unreadCount = chat.unreadCount,
        hasUnreadMention = chat.unreadMentionCount > 0,
        hasUnreadReactions = chat.unreadReactionCount > 0,
        isMarkedAsUnread = chat.isMarkedAsUnread,
        time = timeOf(preview)?.let { timeFormatter.format(it, now) }.orEmpty(),
        outgoingStatus =
            lastMessage
                ?.takeIf { preview !is ChatPreview.Draft && !isSavedMessages && !chat.isChannel }
                ?.outgoingStatus(chat.lastReadOutboxMessageId),
        preview = preview,
        actions =
            rowActions(
                isUnread = chat.unreadCount > 0 || chat.isMarkedAsUnread,
                // A folder can hold archived chats too.
                isArchived = chat.positionIn(ChatListType.Archive) != null,
                isPinned = isPinned,
                isMuted = chat.isMuted,
                canMute = !isSavedMessages,
            ),
    )
}

/** "Saved Messages" and "Deleted Account" from resources, otherwise the chat's title. */
private fun RowInputs.title(
    isSavedMessages: Boolean,
    isDeletedAccount: Boolean,
): UiText =
    when {
        isSavedMessages -> UiText.resource(R.string.chat_saved_messages)
        isDeletedAccount -> UiText.resource(R.string.user_deleted_account)
        else -> UiText.Plain(chat.title)
    }

/** The bookmark of Saved Messages or the person of a deleted account on the avatar placeholder; null for initials. */
private fun placeholderIcon(
    isSavedMessages: Boolean,
    isDeletedAccount: Boolean,
): Int? =
    when {
        isSavedMessages -> R.drawable.ic_bookmark_filled
        isDeletedAccount -> R.drawable.ic_person_filled
        else -> null
    }

/** The private or secret chat's user is online at the minute shown; bots never are. */
private val RowInputs.isUserOnline: Boolean
    get() = user != null && user.type != UserType.Bot && user.status.isOnlineAt(now.epochSecond)

/**
 * The names of senders: [shortName] for users ("Deleted Account" for deleted ones), the chat's title for messages the
 * chat sends itself (anonymous admins, channel posts); null for anyone else.
 */
private fun RowInputs.senderNames(users: Map<Long, User>): (MessageSender) -> UiText? =
    { sender ->
        when (sender) {
            is MessageSender.User -> users[sender.userId]?.shortName()
            is MessageSender.Chat -> if (sender.chatId == chat.id) UiText.Plain(chat.title) else null
        }
    }

/** By precedence: what people are doing, the draft, the last message. Saved Messages shows nobody typing. */
private fun RowInputs.preview(
    isSavedMessages: Boolean,
    name: (MessageSender) -> UiText?,
): ChatPreview {
    val action = actions?.takeIf { !isSavedMessages }?.let { chatActionText(it, chat.isGroup, name) }
    val draft = chat.draft
    val message = chat.lastMessage
    return when {
        action != null -> ChatPreview.Action(action)
        draft != null -> ChatPreview.Draft(draftPreviewText(draft.content))
        message != null -> ChatPreview.Message(senderPrefix(message, name), messagePreviewText(message, myUserId, name))
        else -> ChatPreview.None
    }
}

/**
 * "You" or the sender's name before a message in a group; none in other chats, for channel posts, for messages the
 * group sends as itself (anonymous admins), for unknown users and for service messages, which name their sender.
 */
private fun RowInputs.senderPrefix(
    message: Message,
    name: (MessageSender) -> UiText?,
): UiText? {
    val sender = message.sender
    return when {
        !chat.isGroup || message.isChannelPost || message.content is MessageContent.Service -> null
        message.isOutgoing -> UiText.resource(R.string.preview_sender_you)
        sender is MessageSender.User -> name(sender)
        else -> null
    }
}

/** When the [preview]ed message was sent: a newer draft's time while the draft shows. */
private fun RowInputs.timeOf(preview: ChatPreview): Long? {
    val messageDate = chat.lastMessage?.date
    val draftDate = chat.draft?.date
    return if (preview is ChatPreview.Draft && draftDate != null) maxOf(draftDate, messageDate ?: 0) else messageDate
}

/** Telegram's verification of the private chat's user or of the supergroup or channel; warnings first. */
private val RowInputs.badge: TitleBadge?
    get() {
        val isScam = user?.isScam ?: supergroup?.isScam ?: false
        val isFake = user?.isFake ?: supergroup?.isFake ?: false
        val isVerified = user?.isVerified ?: supergroup?.isVerified ?: false
        return when {
            isScam -> TitleBadge.Scam
            isFake -> TitleBadge.Fake
            isVerified -> TitleBadge.Verified
            else -> null
        }
    }

/** Null for an incoming message. */
private fun Message.outgoingStatus(lastReadOutboxMessageId: Long): OutgoingStatus? =
    when {
        !isOutgoing -> null
        sendingState is SendingState.Pending -> OutgoingStatus.Pending
        sendingState is SendingState.Failed -> OutgoingStatus.Failed
        id <= lastReadOutboxMessageId -> OutgoingStatus.Read
        else -> OutgoingStatus.Sent
    }

/** The user of a private or secret chat; null for groups and channels. */
private val Chat.userId: Long?
    get() =
        when (val type = type) {
            is ChatType.Private -> type.userId
            is ChatType.Secret -> type.userId
            else -> null
        }

/** A basic group or a supergroup that is not a channel: messages there show who sent them. */
private val Chat.isGroup: Boolean
    get() = type is ChatType.BasicGroup || (type as? ChatType.Supergroup)?.isChannel == false

private val Chat.isChannel: Boolean
    get() = (type as? ChatType.Supergroup)?.isChannel == true
