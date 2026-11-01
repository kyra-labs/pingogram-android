package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * The demo's state: the sample chats ([DemoData]) with what the user did to them ([DemoEvent]). Each change returns
 * the updates TDLib would send for it. Not thread-safe: [DemoTdlibClient] uses it under its lock.
 *
 * One function per change it takes, as TDLib's functions, each starting with guard clauses for what it does not
 * know (a chat or message that is not the demo's), which return nothing to announce: hence the suppressions.
 */
@Suppress("TooManyFunctions", "ReturnCount")
internal class DemoWorld(
    val data: DemoData,
) {
    val users: Map<Long, TdApi.User> = data.users

    private val chats = data.chats.associateBy { it.id }.toMutableMap()
    private val histories = data.messages.mapValues { it.value.toMutableList() }.toMutableMap()
    private val chosenAnswers = mutableMapOf<Long, Set<Int>>()
    private var nextMessageId = histories.values.flatten().maxOf { it.id } shr DemoData.MESSAGE_ID_SHIFT

    fun chat(id: Long): TdApi.Chat? = chats[id]

    fun allChats(): Collection<TdApi.Chat> = chats.values

    fun message(
        chatId: Long,
        messageId: Long,
    ): TdApi.Message? = histories[chatId]?.firstOrNull { it.id == messageId }

    /** A chat's messages newest first from [fromMessageId] (0: the newest), [offset] newer ones before it. */
    fun history(
        chatId: Long,
        fromMessageId: Long,
        offset: Int,
        limit: Int,
    ): List<TdApi.Message> {
        val newestFirst = histories[chatId].orEmpty().asReversed()
        val from =
            if (fromMessageId ==
                0L
            ) {
                0
            } else {
                newestFirst.indexOfFirst { it.id <= fromMessageId }.takeIf { it >= 0 } ?: newestFirst.size
            }
        val start = (from + offset).coerceIn(0, newestFirst.size)
        return newestFirst.subList(start, (start + limit).coerceAtMost(newestFirst.size)).toList()
    }

    fun pinned(chatId: Long): List<TdApi.Message> = histories[chatId].orEmpty().filter { it.isPinned }.asReversed()

    fun chatsIn(list: TdApi.ChatList): List<TdApi.Chat> =
        chats.values
            .mapNotNull { chat -> chat.positions.firstOrNull { it.list.sameAs(list) }?.let { chat to it.order } }
            .sortedByDescending { it.second }
            .map { it.first }

    /** Adds the user's text to [chatId] as Telegram would once sent: the message, then the chat's last message. */
    fun send(event: DemoEvent.Sent): List<TdApi.Update> {
        val chat = chats[event.chatId] ?: return emptyList()
        nextMessageId = maxOf(nextMessageId, event.messageId shr DemoData.MESSAGE_ID_SHIFT)
        val message =
            TdApi.Message().apply {
                id = event.messageId
                chatId = event.chatId
                senderId = TdApi.MessageSenderUser(DemoData.ME)
                date = event.date
                content = TdApi.MessageText(TdApi.FormattedText(event.text, emptyArray()), null, null)
                isOutgoing = true
                replyTo =
                    event.replyToMessageId.takeIf { it != 0L && message(event.chatId, it) != null }?.let {
                        TdApi.MessageReplyToMessage(event.chatId, it, null, 0, "", null, 0, null)
                    }
                unreadReactions = emptyArray()
                senderTag = ""
                summaryLanguageCode = ""
                authorSignature = ""
            }
        histories.getOrPut(event.chatId) { mutableListOf() } += message
        chat.lastMessage = message
        chat.lastReadOutboxMessageId = message.id
        chat.positions =
            chat.positions.map { TdApi.ChatPosition(it.list, orderOf(message.date), it.isPinned, null) }.toTypedArray()
        return listOf(
            TdApi.UpdateNewMessage(message),
            TdApi.UpdateChatLastMessage(chat.id, message, chat.positions),
            TdApi.UpdateChatReadOutbox(chat.id, message.id),
        )
    }

    fun nextMessageId(): Long = (++nextMessageId) shl DemoData.MESSAGE_ID_SHIFT

    fun read(event: DemoEvent.Read): List<TdApi.Update> {
        val chat = chats[event.chatId] ?: return emptyList()
        if (event.messageId <= chat.lastReadInboxMessageId && !chat.isMarkedAsUnread) return emptyList()
        val updates = mutableListOf<TdApi.Update>()
        if (chat.isMarkedAsUnread) {
            chat.isMarkedAsUnread = false
            updates += TdApi.UpdateChatIsMarkedAsUnread(chat.id, false)
        }
        chat.lastReadInboxMessageId = maxOf(chat.lastReadInboxMessageId, event.messageId)
        chat.unreadCount = histories[chat.id].orEmpty().count { !it.isOutgoing && it.id > chat.lastReadInboxMessageId }
        updates += TdApi.UpdateChatReadInbox(chat.id, chat.lastReadInboxMessageId, chat.unreadCount)
        return updates
    }

    fun react(event: DemoEvent.Reaction): List<TdApi.Update> {
        val message = message(event.chatId, event.messageId) ?: return emptyList()
        val info = message.interactionInfo ?: TdApi.MessageInteractionInfo(0, 0, null, null)
        val reactions =
            info.reactions
                ?.reactions
                .orEmpty()
                .filterNotNull()
                .toMutableList()
        val index = reactions.indexOfFirst { (it.type as? TdApi.ReactionTypeEmoji)?.emoji == event.emoji }
        val existing = reactions.getOrNull(index)
        when {
            event.added && existing == null -> {
                reactions += TdApi.MessageReaction(TdApi.ReactionTypeEmoji(event.emoji), 1, true, null, emptyArray())
            }

            event.added && existing != null && !existing.isChosen -> {
                reactions[index] = existing.copy(count = existing.totalCount + 1, chosen = true)
            }

            !event.added && existing != null && existing.isChosen -> {
                if (existing.totalCount <=
                    1
                ) {
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
        return listOf(TdApi.UpdateMessageInteractionInfo(event.chatId, event.messageId, info))
    }

    fun vote(event: DemoEvent.Vote): List<TdApi.Update> {
        val message = message(event.chatId, event.messageId) ?: return emptyList()
        val content = message.content as? TdApi.MessagePoll ?: return emptyList()
        val poll = content.poll
        chosenAnswers[poll.id] = event.optionIds.toSet()
        val options =
            poll.options.map { option ->
                option.text.text to option.voterCount - if (option.isChosen) 1 else 0
            }
        content.poll = DemoPolls.poll(poll.id, poll.question.text, options, chosenAnswers.getValue(poll.id))
        return listOf(TdApi.UpdatePoll(content.poll))
    }

    fun delete(event: DemoEvent.Deleted): List<TdApi.Update> {
        val history = histories[event.chatId] ?: return emptyList()
        val ids = event.messageIds.filter { id -> history.any { it.id == id && it.isOutgoing } }
        if (ids.isEmpty()) return emptyList()
        history.removeAll { it.id in ids }
        val chat = chats.getValue(event.chatId)
        chat.lastMessage = history.lastOrNull()
        return listOf(
            TdApi.UpdateDeleteMessages(event.chatId, ids.toLongArray(), true, false),
            TdApi.UpdateChatLastMessage(chat.id, chat.lastMessage, chat.positions),
        )
    }

    fun pin(event: DemoEvent.Pinned): List<TdApi.Update> = reposition(event.chatId) { it.isPinned = event.pinned }

    fun markUnread(event: DemoEvent.MarkedUnread): List<TdApi.Update> {
        val chat = chats[event.chatId] ?: return emptyList()
        chat.isMarkedAsUnread = event.unread
        return listOf(TdApi.UpdateChatIsMarkedAsUnread(chat.id, event.unread))
    }

    fun mute(event: DemoEvent.Muted): List<TdApi.Update> {
        val chat = chats[event.chatId] ?: return emptyList()
        chat.notificationSettings = DemoNotifications.chatSettings(event.muteFor)
        return listOf(TdApi.UpdateChatNotificationSettings(chat.id, chat.notificationSettings))
    }

    /** Moves a chat between the main list and the archive; its folders keep it. */
    fun archive(event: DemoEvent.Archived): List<TdApi.Update> {
        val chat = chats[event.chatId] ?: return emptyList()
        val from: TdApi.ChatList = if (event.archived) TdApi.ChatListMain() else TdApi.ChatListArchive()
        val to: TdApi.ChatList = if (event.archived) TdApi.ChatListArchive() else TdApi.ChatListMain()
        val old = chat.positions.firstOrNull { it.list.sameAs(from) } ?: return emptyList()
        val moved = TdApi.ChatPosition(to, old.order, false, null)
        chat.positions = (chat.positions.filterNot { it.list.sameAs(from) } + moved).toTypedArray()
        chat.chatLists = chat.positions.map { it.list }.toTypedArray()
        return listOf(
            TdApi.UpdateChatPosition(chat.id, TdApi.ChatPosition(from, 0, false, null)),
            TdApi.UpdateChatPosition(chat.id, moved),
        )
    }

    /** Applies an event read back from the journal, discarding the updates: nobody listens yet. */
    fun replay(event: DemoEvent) {
        when (event) {
            is DemoEvent.Started -> Unit
            is DemoEvent.Sent -> send(event)
            is DemoEvent.Read -> read(event)
            is DemoEvent.Reaction -> react(event)
            is DemoEvent.Vote -> vote(event)
            is DemoEvent.Deleted -> delete(event)
            is DemoEvent.Pinned -> pin(event)
            is DemoEvent.MarkedUnread -> markUnread(event)
            is DemoEvent.Muted -> mute(event)
            is DemoEvent.Archived -> archive(event)
        }
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

    private fun TdApi.MessageReaction.copy(
        count: Int,
        chosen: Boolean,
    ) = TdApi.MessageReaction(type, count, chosen, usedSenderId, recentSenderIds)

    private companion object {
        const val ORDER_SHIFT = 20
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
