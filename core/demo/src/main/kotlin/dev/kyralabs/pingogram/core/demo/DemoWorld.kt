package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * The demo's state: the sample chats ([DemoData]) with what the user did to them. Each change returns the updates
 * TDLib would send for it. Not thread-safe: [DemoTdlibClient] uses it under its lock.
 *
 * One function per change it takes, as TDLib's functions, each starting with guard clauses for what it does not
 * know (a chat or message that is not the demo's), which return nothing to announce: hence the suppressions.
 */
@Suppress("TooManyFunctions", "ReturnCount")
internal class DemoWorld(
    val data: DemoData,
) {
    val users: Map<Long, TdApi.User> = data.users

    /** The time of the change being answered (Unix seconds): now, or a journal entry's while it is replayed. */
    var now: Int = 0

    private val chats = data.chats.associateBy { it.id }.toMutableMap()
    private val histories = data.messages.mapValues { it.value.toMutableList() }.toMutableMap()
    private val scheduled = mutableMapOf<Long, MutableList<TdApi.Message>>()
    private val chosenAnswers = mutableMapOf<Long, Set<Int>>()
    private val closedPolls = mutableSetOf<Long>()
    private val sentFiles = mutableMapOf<Int, File>()
    private var nextMessageId = histories.values.flatten().maxOf { it.id } shr DemoData.MESSAGE_ID_SHIFT
    private var nextFileId = FIRST_SENT_FILE_ID
    private var nextAlbumId = 1L
    private var nextPollId = DemoData.POLL_ID + 1

    fun chat(id: Long): TdApi.Chat? = chats[id]

    fun allChats(): Collection<TdApi.Chat> = chats.values

    fun message(
        chatId: Long,
        messageId: Long,
    ): TdApi.Message? =
        histories[chatId]?.firstOrNull { it.id == messageId } ?: scheduled[chatId]?.firstOrNull { it.id == messageId }

    /** A chat's messages newest first from [fromMessageId] (0: the newest), [offset] newer ones before it. */
    fun history(
        chatId: Long,
        fromMessageId: Long,
        offset: Int,
        limit: Int,
    ): List<TdApi.Message> {
        val newestFirst = histories[chatId].orEmpty().asReversed()
        val from =
            if (fromMessageId == 0L) {
                0
            } else {
                newestFirst.indexOfFirst { it.id <= fromMessageId }.takeIf { it >= 0 } ?: newestFirst.size
            }
        val start = (from + offset).coerceIn(0, newestFirst.size)
        return newestFirst.subList(start, (start + limit).coerceAtMost(newestFirst.size)).toList()
    }

    fun messagesOf(chatId: Long): List<TdApi.Message> = histories[chatId].orEmpty()

    fun pinned(chatId: Long): List<TdApi.Message> = histories[chatId].orEmpty().filter { it.isPinned }.asReversed()

    fun scheduledIn(chatId: Long): List<TdApi.Message> = scheduled[chatId].orEmpty().toList()

    fun chatsIn(list: TdApi.ChatList): List<TdApi.Chat> =
        chats.values
            .mapNotNull { chat -> chat.positions.firstOrNull { it.list.sameAs(list) }?.let { chat to it.order } }
            .sortedByDescending { it.second }
            .map { it.first }

    fun nextMessageId(): Long = (++nextMessageId) shl DemoData.MESSAGE_ID_SHIFT

    fun nextAlbumId(): Long = nextAlbumId++

    fun nextPollId(): Long = nextPollId++

    /** A file the user sent, as TDLib reports it once uploaded: on the device (the demo's copy) and "remote". */
    fun sentFile(path: String): TdApi.File {
        val id = nextFileId++
        val file = File(path)
        sentFiles[id] = file
        return localFile(id, file)
    }

    /** A file the demo knows by id: one the user sent, or one of [DemoFile]'s (remote until downloaded). */
    fun knownFile(id: Int): TdApi.File? =
        sentFiles[id]?.let { localFile(id, it) } ?: DemoFile.of(id)?.let(DemoFiles::remote)

    fun sentFileById(id: Int): File? = sentFiles[id]

    /**
     * Adds a message of the user's to [chatId] as Telegram would once sent: the message, then the chat's last
     * message; or, with a [schedulingState], to the chat's scheduled messages.
     */
    fun addOwn(
        chatId: Long,
        content: TdApi.MessageContent,
        replyTo: TdApi.MessageReplyTo? = null,
        forwardInfo: TdApi.MessageForwardInfo? = null,
        schedulingState: TdApi.MessageSchedulingState? = null,
        albumId: Long = 0,
    ): Pair<TdApi.Message, List<TdApi.Update>>? {
        val chat = chats[chatId] ?: return null
        val message =
            TdApi.Message().apply {
                id = nextMessageId()
                this.chatId = chatId
                senderId = TdApi.MessageSenderUser(DemoData.ME)
                date = now
                this.content = content
                isOutgoing = true
                canBeSaved = true
                this.replyTo = replyTo
                this.forwardInfo = forwardInfo
                this.schedulingState = schedulingState
                mediaAlbumId = albumId
                unreadReactions = emptyArray()
                senderTag = ""
                summaryLanguageCode = ""
                authorSignature = ""
            }
        if (schedulingState != null) {
            scheduled.getOrPut(chatId) { mutableListOf() } += message
            val updates = mutableListOf<TdApi.Update>(TdApi.UpdateNewMessage(message))
            if (!chat.hasScheduledMessages) {
                chat.hasScheduledMessages = true
                updates += TdApi.UpdateChatHasScheduledMessages(chatId, true)
            }
            return message to updates
        }
        histories.getOrPut(chatId) { mutableListOf() } += message
        chat.lastMessage = message
        chat.lastReadOutboxMessageId = message.id
        chat.positions =
            chat.positions.map { TdApi.ChatPosition(it.list, orderOf(message.date), it.isPinned, null) }.toTypedArray()
        return message to
            listOf(
                TdApi.UpdateNewMessage(message),
                TdApi.UpdateChatLastMessage(chat.id, message, chat.positions),
                TdApi.UpdateChatReadOutbox(chat.id, message.id),
            )
    }

    /** A scheduled message sent now: it leaves the scheduled ones and arrives in the chat as a new message. */
    fun sendScheduledNow(
        chatId: Long,
        messageId: Long,
    ): List<TdApi.Update> {
        val list = scheduled[chatId] ?: return emptyList()
        val message = list.firstOrNull { it.id == messageId } ?: return emptyList()
        list.remove(message)
        val updates =
            mutableListOf<TdApi.Update>(TdApi.UpdateDeleteMessages(chatId, longArrayOf(messageId), true, false))
        updates += addOwn(chatId, message.content, message.replyTo)?.second.orEmpty()
        updates += scheduledGone(chatId)
        return updates
    }

    fun reschedule(
        chatId: Long,
        messageId: Long,
        state: TdApi.MessageSchedulingState,
    ): Boolean {
        val message = scheduled[chatId]?.firstOrNull { it.id == messageId } ?: return false
        message.schedulingState = state
        return true
    }

    /** Replaces a message's content as an edit does: the new content, then its edit date. */
    fun edit(
        chatId: Long,
        messageId: Long,
        content: TdApi.MessageContent,
    ): List<TdApi.Update> {
        val message = message(chatId, messageId) ?: return emptyList()
        message.content = content
        message.editDate = now
        if (chats[chatId]?.lastMessage?.id == messageId) chats.getValue(chatId).lastMessage = message
        return listOf(
            TdApi.UpdateMessageContent(chatId, messageId, content),
            TdApi.UpdateMessageEdited(chatId, messageId, now, message.replyMarkup),
        )
    }

    fun pinMessage(
        chatId: Long,
        messageId: Long,
        pinned: Boolean,
    ): List<TdApi.Update> {
        val message = histories[chatId]?.firstOrNull { it.id == messageId } ?: return emptyList()
        if (message.isPinned == pinned) return emptyList()
        message.isPinned = pinned
        return listOf(TdApi.UpdateMessageIsPinned(chatId, messageId, pinned))
    }

    fun unpinAll(chatId: Long): List<TdApi.Update> =
        histories[chatId].orEmpty().filter { it.isPinned }.flatMap { pinMessage(chatId, it.id, false) }

    fun read(
        chatId: Long,
        messageId: Long,
    ): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        if (messageId <= chat.lastReadInboxMessageId && !chat.isMarkedAsUnread) return emptyList()
        val updates = mutableListOf<TdApi.Update>()
        if (chat.isMarkedAsUnread) {
            chat.isMarkedAsUnread = false
            updates += TdApi.UpdateChatIsMarkedAsUnread(chat.id, false)
        }
        chat.lastReadInboxMessageId = maxOf(chat.lastReadInboxMessageId, messageId)
        chat.unreadCount = histories[chat.id].orEmpty().count { !it.isOutgoing && it.id > chat.lastReadInboxMessageId }
        updates += TdApi.UpdateChatReadInbox(chat.id, chat.lastReadInboxMessageId, chat.unreadCount)
        return updates
    }

    fun react(
        chatId: Long,
        messageId: Long,
        emoji: String,
        added: Boolean,
    ): List<TdApi.Update> {
        val message = message(chatId, messageId) ?: return emptyList()
        val info = message.interactionInfo ?: TdApi.MessageInteractionInfo(0, 0, null, null)
        val reactions =
            info.reactions
                ?.reactions
                .orEmpty()
                .filterNotNull()
                .toMutableList()
        val index = reactions.indexOfFirst { (it.type as? TdApi.ReactionTypeEmoji)?.emoji == emoji }
        val existing = reactions.getOrNull(index)
        when {
            added && existing == null -> {
                reactions += TdApi.MessageReaction(TdApi.ReactionTypeEmoji(emoji), 1, true, null, emptyArray())
            }

            added && existing != null && !existing.isChosen -> {
                reactions[index] = existing.copy(count = existing.totalCount + 1, chosen = true)
            }

            !added && existing != null && existing.isChosen -> {
                if (existing.totalCount <= 1) {
                    reactions.removeAt(index)
                } else {
                    reactions[index] = existing.copy(existing.totalCount - 1, false)
                }
            }

            else -> {
                return emptyList()
            }
        }
        info.reactions = TdApi.MessageReactions(reactions.toTypedArray(), true, emptyArray(), false)
        message.interactionInfo = info
        return listOf(TdApi.UpdateMessageInteractionInfo(chatId, messageId, info))
    }

    fun vote(
        chatId: Long,
        messageId: Long,
        optionIds: List<Int>,
    ): List<TdApi.Update> {
        val content = message(chatId, messageId)?.content as? TdApi.MessagePoll ?: return emptyList()
        if (content.poll.id in closedPolls) return emptyList()
        chosenAnswers[content.poll.id] = optionIds.toSet()
        content.poll = repoll(content.poll)
        return listOf(TdApi.UpdatePoll(content.poll))
    }

    fun stopPoll(
        chatId: Long,
        messageId: Long,
    ): List<TdApi.Update> {
        val content = message(chatId, messageId)?.content as? TdApi.MessagePoll ?: return emptyList()
        if (!closedPolls.add(content.poll.id)) return emptyList()
        content.poll = repoll(content.poll)
        return listOf(TdApi.UpdatePoll(content.poll))
    }

    private fun repoll(poll: TdApi.Poll): TdApi.Poll {
        val chosen = chosenAnswers[poll.id].orEmpty()
        val options = poll.options.map { it.text.text to it.voterCount - if (it.isChosen) 1 else 0 }
        return DemoPolls.poll(poll.id, poll.question.text, options, chosen).apply {
            isAnonymous = poll.isAnonymous
            allowsMultipleAnswers = poll.allowsMultipleAnswers
            type = poll.type
            isClosed = poll.id in closedPolls
            canSeeResults = canSeeResults || isClosed
        }
    }

    /** Deletes messages (for the user only, where they are others'), except a channel's posts, which it cannot. */
    fun delete(
        chatId: Long,
        messageIds: List<Long>,
    ): List<TdApi.Update> {
        val history = histories[chatId].orEmpty()
        val queued = scheduled[chatId].orEmpty()
        val ids =
            messageIds.filter { id -> history.any { it.id == id && !it.isChannelPost } || queued.any { it.id == id } }
        if (ids.isEmpty()) return emptyList()
        histories[chatId]?.removeAll { it.id in ids }
        scheduled[chatId]?.removeAll { it.id in ids }
        val chat = chats.getValue(chatId)
        chat.lastMessage = histories[chatId]?.lastOrNull()
        return listOf(
            TdApi.UpdateDeleteMessages(chatId, ids.toLongArray(), true, false),
            TdApi.UpdateChatLastMessage(chat.id, chat.lastMessage, chat.positions),
        ) + scheduledGone(chatId)
    }

    private fun scheduledGone(chatId: Long): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        if (!chat.hasScheduledMessages || scheduled[chatId].orEmpty().isNotEmpty()) return emptyList()
        chat.hasScheduledMessages = false
        return listOf(TdApi.UpdateChatHasScheduledMessages(chatId, false))
    }

    fun pinChat(
        chatId: Long,
        pinned: Boolean,
    ): List<TdApi.Update> = reposition(chatId) { it.isPinned = pinned }

    fun markUnread(
        chatId: Long,
        unread: Boolean,
    ): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        chat.isMarkedAsUnread = unread
        return listOf(TdApi.UpdateChatIsMarkedAsUnread(chat.id, unread))
    }

    fun mute(
        chatId: Long,
        muteFor: Int,
    ): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        chat.notificationSettings = DemoNotifications.chatSettings(muteFor)
        return listOf(TdApi.UpdateChatNotificationSettings(chat.id, chat.notificationSettings))
    }

    /** Moves a chat between the main list and the archive; its folders keep it. */
    fun archive(
        chatId: Long,
        archived: Boolean,
    ): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        val from: TdApi.ChatList = if (archived) TdApi.ChatListMain() else TdApi.ChatListArchive()
        val to: TdApi.ChatList = if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()
        val old = chat.positions.firstOrNull { it.list.sameAs(from) } ?: return emptyList()
        val moved = TdApi.ChatPosition(to, old.order, false, null)
        chat.positions = (chat.positions.filterNot { it.list.sameAs(from) } + moved).toTypedArray()
        chat.chatLists = chat.positions.map { it.list }.toTypedArray()
        return listOf(
            TdApi.UpdateChatPosition(chat.id, TdApi.ChatPosition(from, 0, false, null)),
            TdApi.UpdateChatPosition(chat.id, moved),
        )
    }

    private fun reposition(
        chatId: Long,
        change: (TdApi.ChatPosition) -> Unit,
    ): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        val position = chat.positions.firstOrNull { it.list !is TdApi.ChatListFolder } ?: return emptyList()
        change(position)
        return listOf(TdApi.UpdateChatPosition(chat.id, position))
    }

    private fun orderOf(date: Int) = date.toLong() shl ORDER_SHIFT

    private fun localFile(
        id: Int,
        file: File,
    ): TdApi.File {
        val size = file.length()
        return TdApi.File(
            id,
            size,
            size,
            // Path, can be downloaded, can be deleted, downloading, downloaded, offset, prefix and size.
            TdApi.LocalFile(file.path, true, true, false, true, 0, size, size),
            TdApi.RemoteFile("demo-$id", "demo-$id", false, true, size),
        )
    }

    private fun TdApi.MessageReaction.copy(
        count: Int,
        chosen: Boolean,
    ) = TdApi.MessageReaction(type, count, chosen, usedSenderId, recentSenderIds)

    private companion object {
        const val ORDER_SHIFT = 20

        /** Sent files' ids start above [DemoFile]'s, as TDLib numbers a client's files. */
        const val FIRST_SENT_FILE_ID = 1_000
    }
}

/** Whether two of TDLib's chat lists are the same list: the main list, the archive, or one folder. */
internal fun TdApi.ChatList.sameAs(other: TdApi.ChatList): Boolean =
    when (this) {
        is TdApi.ChatListMain -> other is TdApi.ChatListMain
        is TdApi.ChatListArchive -> other is TdApi.ChatListArchive
        is TdApi.ChatListFolder -> other is TdApi.ChatListFolder && other.chatFolderId == chatFolderId
        else -> false
    }
