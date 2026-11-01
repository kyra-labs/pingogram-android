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

    /** The groups and channels beyond their chats: full info, members, links, requests, recent actions. */
    val groups = DemoGroupState(data) { now }

    /** The 2-step verification password the user set in the demo, which an ownership transfer asks for. */
    var password: String? = null

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

    init {
        val requests = groups.requests[DemoData.CHANNEL_CHAT].orEmpty()
        chats[DemoData.CHANNEL_CHAT]?.pendingJoinRequests =
            TdApi.ChatJoinRequestsInfo(requests.size, requests.map { it.userId }.toLongArray())
    }

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
        filter: (TdApi.Message) -> Boolean = { true },
    ): List<TdApi.Message> {
        val newestFirst = histories[chatId].orEmpty().filter(filter).asReversed()
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

    /** A new chat, at the top of the main list, with its first messages (a service message, as Telegram adds one). */
    fun addChat(
        chat: TdApi.Chat,
        firstMessages: List<TdApi.MessageContent> = emptyList(),
    ): List<TdApi.Update> {
        chat.positions = arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), orderOf(now), false, null))
        chat.chatLists = arrayOf(TdApi.ChatListMain())
        chats[chat.id] = chat
        histories[chat.id] = mutableListOf()
        val updates = mutableListOf<TdApi.Update>(TdApi.UpdateNewChat(chat))
        firstMessages.forEach { content -> addOwn(chat.id, content)?.let { updates += it.second } }
        return updates
    }

    /** Takes a chat out of every list, as deleting or leaving it does; its messages go with it. */
    fun removeChat(chatId: Long): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        val updates =
            chat.positions.map {
                TdApi.UpdateChatPosition(
                    chatId,
                    TdApi.ChatPosition(it.list, 0, false, null),
                )
            }
        chat.positions = emptyArray()
        chat.chatLists = emptyArray()
        histories[chatId]?.clear()
        chat.lastMessage = null
        return updates + TdApi.UpdateChatLastMessage(chatId, null, emptyArray())
    }

    /** A demo person's private chat, made in no list (as TDLib's `createPrivateChat`), or null for a stranger. */
    fun privateChat(userId: Long): Pair<TdApi.Chat, List<TdApi.Update>>? {
        chats[userId]?.let { return it to emptyList() }
        val user = users[userId] ?: return null
        val chat = demoChat(userId, TdApi.ChatTypePrivate(userId), "${user.firstName} ${user.lastName}".trim(), true)
        chat.canBeDeletedForAllUsers = false
        chats[userId] = chat
        histories[userId] = mutableListOf()
        return chat to listOf(TdApi.UpdateNewChat(chat))
    }

    /** The user joins a group or channel they are not in: it takes its place in the main list, its history kept. */
    fun join(chatId: Long): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        val id = DemoIds.supergroupId(chatId) ?: return emptyList()
        val group = groups.supergroups[id] ?: return emptyList()
        if (group.status !is TdApi.ChatMemberStatusLeft) return emptyList()
        group.status = TdApi.ChatMemberStatusMember(0)
        val members =
            groups.membersOf(chatId).filterNot { it.isMe() } +
                TdApi.ChatMember(
                    TdApi.MessageSenderUser(DemoData.ME),
                    "",
                    0,
                    now,
                    TdApi.ChatMemberStatusMember(0),
                )
        val updates = mutableListOf<TdApi.Update>(TdApi.UpdateSupergroup(group))
        updates += groups.setMembers(chatId, members)
        val order = orderOf(chat.lastMessage?.date ?: now)
        chat.chatLists = arrayOf(TdApi.ChatListMain())
        chat.positions = arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), order, false, null))
        updates += TdApi.UpdateChatAddedToList(chatId, TdApi.ChatListMain())
        updates += TdApi.UpdateChatPosition(chatId, chat.positions.first())
        return updates
    }

    /** The user leaves a group or channel: it leaves their lists, its history kept for if they join again. */
    fun leave(chatId: Long): List<TdApi.Update> {
        val chat = chats[chatId] ?: return emptyList()
        val updates = mutableListOf<TdApi.Update>()
        val supergroup = DemoIds.supergroupId(chatId)?.let { groups.supergroups[it] }
        val basicGroup = DemoIds.basicGroupId(chatId)?.let { groups.basicGroups[it] }
        if (supergroup != null) {
            supergroup.status = TdApi.ChatMemberStatusLeft()
            updates += TdApi.UpdateSupergroup(supergroup)
            updates += groups.setMembers(chatId, groups.membersOf(chatId).filterNot { it.isMe() })
        }
        if (basicGroup != null) {
            basicGroup.status = TdApi.ChatMemberStatusLeft()
            updates += TdApi.UpdateBasicGroup(basicGroup)
        }
        updates += chat.positions.map { TdApi.UpdateChatPosition(chatId, TdApi.ChatPosition(it.list, 0, false, null)) }
        chat.positions = emptyArray()
        chat.chatLists = emptyArray()
        return updates
    }

    private fun TdApi.ChatMember.isMe() = (memberId as? TdApi.MessageSenderUser)?.userId == DemoData.ME

    /** Deletes a chat's history for the user, the chat itself staying in its lists. */
    fun clearHistory(chatId: Long): List<TdApi.Update> {
        val history = histories[chatId] ?: return emptyList()
        val ids = history.map { it.id }
        history.clear()
        val chat = chats.getValue(chatId)
        chat.lastMessage = null
        chat.unreadCount = 0
        return listOf(
            TdApi.UpdateDeleteMessages(chatId, ids.toLongArray(), true, false),
            TdApi.UpdateChatLastMessage(chatId, null, chat.positions),
            TdApi.UpdateChatReadInbox(chatId, chat.lastReadInboxMessageId, 0),
        )
    }

    /** A copy of a chat's history moved to a new chat, as upgrading a group to a supergroup keeps it. */
    fun moveHistory(
        fromChatId: Long,
        toChatId: Long,
    ) {
        val moved = histories[fromChatId].orEmpty().map { message -> message.also { it.chatId = toChatId } }
        histories[toChatId] = moved.toMutableList()
        histories[fromChatId] = mutableListOf()
        chats[toChatId]?.lastMessage = moved.lastOrNull()
    }

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

    fun sentFileSizes(): List<Long> = sentFiles.values.map { it.length() }

    /** Puts each chat [holds] says into folder [folderId]'s list, and takes the others out of it. */
    fun placeInFolder(
        folderId: Int,
        holds: (TdApi.Chat) -> Boolean,
    ): List<TdApi.Update> =
        chats.values.mapNotNull { chat ->
            val list = TdApi.ChatListFolder(folderId)
            val inFolder = chat.positions.any { it.list.sameAs(list) }
            val order = chat.positions.firstOrNull { it.list !is TdApi.ChatListFolder }?.order ?: 0
            when {
                holds(chat) && !inFolder -> {
                    val position = TdApi.ChatPosition(list, order, false, null)
                    chat.positions += position
                    TdApi.UpdateChatPosition(chat.id, position)
                }

                !holds(chat) && inFolder -> {
                    chat.positions = chat.positions.filterNot { it.list.sameAs(list) }.toTypedArray()
                    TdApi.UpdateChatPosition(chat.id, TdApi.ChatPosition(list, 0, false, null))
                }

                else -> {
                    null
                }
            }
        }

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
        topic: TdApi.MessageTopic? = null,
    ): Pair<TdApi.Message, List<TdApi.Update>>? {
        val chat = chats[chatId] ?: return null
        val message =
            TdApi.Message().apply {
                id = nextMessageId()
                this.chatId = chatId
                // In a channel the user posts as the channel, as its owner does.
                val channel = (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel == true
                senderId = if (channel) TdApi.MessageSenderChat(chatId) else TdApi.MessageSenderUser(DemoData.ME)
                isChannelPost = channel
                if (channel) interactionInfo = TdApi.MessageInteractionInfo(1, 0, null, null)
                date = now
                this.content = content
                isOutgoing = true
                canBeSaved = true
                this.replyTo = replyTo
                this.forwardInfo = forwardInfo
                this.schedulingState = schedulingState
                mediaAlbumId = albumId
                topicId = if (chatId == DemoData.ME) savedTopicOf(forwardInfo) else topic
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
        // A chat in no list yet (a private chat opened from a profile) joins the main list with its first message.
        if (chat.positions.isEmpty()) chat.chatLists = arrayOf(TdApi.ChatListMain())
        chat.positions =
            chat.chatLists
                .map { list ->
                    val pinned = chat.positions.firstOrNull { it.list.sameAs(list) }?.isPinned == true
                    TdApi.ChatPosition(list, orderOf(message.date), pinned, null)
                }.toTypedArray()
        val updates =
            mutableListOf<TdApi.Update>(
                TdApi.UpdateNewMessage(message),
                TdApi.UpdateChatLastMessage(chat.id, message, chat.positions),
                TdApi.UpdateChatReadOutbox(chat.id, message.id),
            )
        (topic as? TdApi.MessageTopicThread)?.let { updates += replied(chatId, it.messageThreadId, message) }
        if (chatId == DemoData.CHANNEL_CHAT) updates += forwardToDiscussion(message)
        if (chatId == DemoData.ME) {
            savedTopic((message.topicId as TdApi.MessageTopicSavedMessages).savedMessagesTopicId)
                ?.let { updates += TdApi.UpdateSavedMessagesTopic(it) }
        }
        return message to updates
    }

    /** One of Saved Messages' topics, with its newest message, as TDLib announces it. */
    fun savedTopic(topicId: Long): TdApi.SavedMessagesTopic? {
        val last =
            histories[DemoData.ME].orEmpty().lastOrNull {
                (it.topicId as? TdApi.MessageTopicSavedMessages)?.savedMessagesTopicId == topicId
            } ?: return null
        val type =
            when (topicId) {
                DemoData.ME -> TdApi.SavedMessagesTopicTypeMyNotes()
                DemoData.AUTHOR_HIDDEN_TOPIC -> TdApi.SavedMessagesTopicTypeAuthorHidden()
                else -> TdApi.SavedMessagesTopicTypeSavedFromChat(topicId)
            }
        return TdApi.SavedMessagesTopic(topicId, type, false, orderOf(last.date), last, null)
    }

    /** Saved Messages files a message under where it came from: the user's notes, a person, a chat, a hidden author. */
    private fun savedTopicOf(forwardInfo: TdApi.MessageForwardInfo?): TdApi.MessageTopic =
        TdApi.MessageTopicSavedMessages(
            when (val origin = forwardInfo?.origin) {
                null -> DemoData.ME
                is TdApi.MessageOriginUser -> origin.senderUserId
                is TdApi.MessageOriginChat -> origin.senderChatId
                is TdApi.MessageOriginChannel -> origin.chatId
                else -> DemoData.AUTHOR_HIDDEN_TOPIC
            },
        )

    /** A comment counted on the post's copy in the discussion group and on the post, as their links show it. */
    private fun replied(
        chatId: Long,
        threadId: Long,
        reply: TdApi.Message,
    ): List<TdApi.Update> {
        val head = message(chatId, threadId) ?: return emptyList()
        val origin = head.forwardInfo?.origin as? TdApi.MessageOriginChannel
        val post = origin?.let { message(it.chatId, it.messageId) }
        return listOfNotNull(head, post).map { counted ->
            val old = counted.interactionInfo
            val info = old?.replyInfo
            val repliers = (listOf(reply.senderId) + info?.recentReplierIds.orEmpty()).distinctBy(::senderKey)
            counted.interactionInfo =
                TdApi.MessageInteractionInfo(
                    old?.viewCount ?: 0,
                    old?.forwardCount ?: 0,
                    TdApi.MessageReplyInfo(
                        (info?.replyCount ?: 0) + 1,
                        repliers.take(RECENT_REPLIERS).toTypedArray(),
                        reply.id,
                        reply.id,
                        reply.id,
                    ),
                    old?.reactions,
                )
            TdApi.UpdateMessageInteractionInfo(counted.chatId, counted.id, counted.interactionInfo)
        }
    }

    /** A new post goes into the discussion group too, as Telegram forwards it, so it can take comments. */
    private fun forwardToDiscussion(post: TdApi.Message): List<TdApi.Update> {
        val linked = DemoIds.supergroupId(post.chatId)?.let { groups.supergroupFullInfo[it]?.linkedChatId } ?: 0
        val discussion = chats[linked] ?: return emptyList()
        val empty = TdApi.MessageReplyInfo(0, emptyArray(), 0, 0, 0)
        post.interactionInfo = TdApi.MessageInteractionInfo(post.interactionInfo?.viewCount ?: 1, 0, empty, null)
        val copy =
            TdApi.Message().apply {
                id = nextMessageId()
                chatId = discussion.id
                senderId = TdApi.MessageSenderChat(post.chatId)
                date = post.date
                content = post.content
                canBeSaved = true
                forwardInfo =
                    TdApi.MessageForwardInfo(TdApi.MessageOriginChannel(post.chatId, post.id, ""), post.date, null, "")
                interactionInfo = TdApi.MessageInteractionInfo(0, 0, empty, null)
                unreadReactions = emptyArray()
                senderTag = ""
                summaryLanguageCode = ""
                authorSignature = ""
            }
        histories.getOrPut(discussion.id) { mutableListOf() } += copy
        discussion.lastMessage = copy
        discussion.positions =
            discussion.positions
                .map { TdApi.ChatPosition(it.list, orderOf(copy.date), it.isPinned, null) }
                .toTypedArray()
        return listOf(
            TdApi.UpdateMessageInteractionInfo(post.chatId, post.id, post.interactionInfo),
            TdApi.UpdateNewMessage(copy),
            TdApi.UpdateChatLastMessage(discussion.id, copy, discussion.positions),
        )
    }

    private fun senderKey(sender: TdApi.MessageSender): Long =
        when (sender) {
            is TdApi.MessageSenderUser -> sender.userId
            is TdApi.MessageSenderChat -> sender.chatId
            else -> 0
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
        info.reactions = TdApi.MessageReactions(reactions.toTypedArray(), false, emptyArray(), true)
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
            canGetVoters = poll.canGetVoters
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
        val ownsChat = groups.statusOf(chatId) is TdApi.ChatMemberStatusCreator
        val ids =
            messageIds.filter { id ->
                history.any { it.id == id && (!it.isChannelPost || ownsChat) } || queued.any { it.id == id }
            }
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
        const val RECENT_REPLIERS = 3

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
