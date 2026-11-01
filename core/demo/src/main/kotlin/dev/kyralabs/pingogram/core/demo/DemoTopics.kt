package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.TdResult
import org.drinkless.tdlib.TdApi
import kotlin.random.Random

/**
 * Topics and threads in the demo (`docs/adr/0034-demo-mode-for-store-review.md`): the sample forum's topics (made,
 * renamed, closed, pinned, hidden and deleted as TDLib does it), comment threads on the channel's posts in its
 * discussion group, secret chats with the sample people, and Saved Messages' topics.
 *
 * A secret chat is ready at once: the sample person "accepts" it on this phone, and its key is made up. Nothing is
 * encrypted or sent anywhere, as nothing in the demo is.
 *
 * One branch per function, and guard clauses for chats that are not the demo's: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions", "ReturnCount")
internal class DemoTopics : DemoArea {
    private class Topic(
        val info: TdApi.ForumTopicInfo,
    )

    private val forums = mutableMapOf<Long, MutableList<Topic>>()
    private val pinned = mutableMapOf<Long, MutableList<Int>>()
    private val secretChats = linkedMapOf<Int, TdApi.SecretChat>()
    private var savedTopicsLoaded = false

    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.GetForumTopics -> {
                topics(world, function)
            }

            is TdApi.GetForumTopic -> {
                topic(world, function.chatId, function.forumTopicId)?.let { ok(forumTopic(world, it)) } ?: notFound()
            }

            is TdApi.GetForumTopicHistory -> {
                ok(
                    messages(
                        world.history(function.chatId, function.fromMessageId, function.offset, function.limit) {
                            it.isIn(function.forumTopicId)
                        },
                    ),
                )
            }

            is TdApi.CreateForumTopic -> {
                create(world, function)
            }

            is TdApi.EditForumTopic -> {
                val name = function.name.trim()
                if (name.isEmpty()) {
                    badRequest(TOPIC_TITLE_EMPTY)
                } else {
                    change(world, function.chatId, function.forumTopicId) { it.name = name }
                }
            }

            is TdApi.ToggleForumTopicIsClosed -> {
                change(world, function.chatId, function.forumTopicId) { it.isClosed = function.isClosed }
            }

            is TdApi.ToggleGeneralForumTopicIsHidden -> {
                change(world, function.chatId, DemoData.GENERAL_TOPIC) { it.isHidden = function.isHidden }
            }

            is TdApi.ToggleForumTopicIsPinned -> {
                pin(world, function.chatId, function.forumTopicId, function.isPinned)
            }

            is TdApi.SetPinnedForumTopics -> {
                setPinned(world, function.chatId, function.forumTopicIds.toList())
            }

            is TdApi.DeleteForumTopic -> {
                delete(world, function.chatId, function.forumTopicId)
            }

            is TdApi.ToggleSupergroupIsForum -> {
                toggleForum(world, function)
            }

            is TdApi.GetMessageThread -> {
                thread(world, function.chatId, function.messageId)
            }

            is TdApi.GetMessageThreadHistory -> {
                threadHistory(world, function)
            }

            is TdApi.CreateNewSecretChat -> {
                createSecretChat(world, function.userId)
            }

            is TdApi.CloseSecretChat -> {
                val secretChat = secretChats[function.secretChatId]
                if (secretChat == null) {
                    notFound()
                } else {
                    secretChat.state = TdApi.SecretChatStateClosed()
                    okChanged(listOf(TdApi.UpdateSecretChat(secretChat)))
                }
            }

            is TdApi.GetSecretChat -> {
                secretChats[function.secretChatId]?.let(::ok) ?: notFound()
            }

            is TdApi.LoadSavedMessagesTopics -> {
                loadSavedTopics(world)
            }

            is TdApi.GetSavedMessagesTopicHistory -> {
                ok(
                    messages(
                        world.history(DemoData.ME, function.fromMessageId, function.offset, function.limit) {
                            (it.topicId as? TdApi.MessageTopicSavedMessages)?.savedMessagesTopicId ==
                                function.savedMessagesTopicId
                        },
                    ),
                )
            }

            else -> {
                null
            }
        }

    /** The secret chats as TDLib announces them after it starts, before their chats. */
    fun announce(): List<TdApi.Update> = secretChats.values.map(TdApi::UpdateSecretChat)

    /** A forum's topics: the sample forum's, or General alone for a group the user turned into a forum. */
    private fun topicsOf(
        world: DemoWorld,
        chatId: Long,
    ): MutableList<Topic>? {
        forums[chatId]?.let { return it }
        val group = DemoIds.supergroupId(chatId)?.let { world.groups.supergroups[it] }
        val seeded =
            when {
                chatId == DemoData.FORUM_CHAT -> world.data.forumTopics.map(::Topic)
                group?.isForum == true -> listOf(Topic(general(world, chatId)))
                else -> return null
            }
        return seeded.toMutableList().also { forums[chatId] = it }
    }

    private fun topic(
        world: DemoWorld,
        chatId: Long,
        topicId: Int,
    ): Topic? = topicsOf(world, chatId)?.firstOrNull { it.info.forumTopicId == topicId }

    private fun general(
        world: DemoWorld,
        chatId: Long,
    ) = TdApi.ForumTopicInfo().apply {
        this.chatId = chatId
        forumTopicId = DemoData.GENERAL_TOPIC
        name = world.data.text(R.string.demo_topic_general)
        icon = TdApi.ForumTopicIcon(GENERAL_COLOR, 0)
        creationDate = world.now
        creatorId = TdApi.MessageSenderUser(DemoData.ME)
        isGeneral = true
        isOutgoing = true
    }

    private fun topics(
        world: DemoWorld,
        function: TdApi.GetForumTopics,
    ): DemoAnswer {
        val topics = topicsOf(world, function.chatId) ?: return badRequest(FORUM_MISSING)
        val order = pinned[function.chatId].orEmpty()
        val found =
            topics
                .filter { function.query.isBlank() || it.info.name.contains(function.query.trim(), ignoreCase = true) }
                .map { forumTopic(world, it) }
                .sortedWith(
                    compareBy<TdApi.ForumTopic> { if (it.isPinned) order.indexOf(it.info.forumTopicId) else order.size }
                        .thenByDescending { it.order },
                )
        return ok(TdApi.ForumTopics(found.size, found.toTypedArray(), 0, 0, 0))
    }

    private fun forumTopic(
        world: DemoWorld,
        topic: Topic,
    ): TdApi.ForumTopic {
        val info = topic.info
        val last = world.messagesOf(info.chatId).lastOrNull { it.isIn(info.forumTopicId) }
        return TdApi.ForumTopic().apply {
            this.info = info
            lastMessage = last
            order = (last?.date ?: info.creationDate).toLong() shl ORDER_SHIFT
            isPinned = info.forumTopicId in pinned[info.chatId].orEmpty()
            lastReadInboxMessageId = last?.id ?: 0
            lastReadOutboxMessageId = last?.id ?: 0
            notificationSettings = DemoNotifications.chatSettings(muteFor = 0)
        }
    }

    private fun create(
        world: DemoWorld,
        function: TdApi.CreateForumTopic,
    ): DemoAnswer {
        val topics = topicsOf(world, function.chatId) ?: return badRequest(FORUM_MISSING)
        val name = function.name.trim()
        if (name.isEmpty()) return badRequest(TOPIC_TITLE_EMPTY)
        val info =
            TdApi.ForumTopicInfo().apply {
                chatId = function.chatId
                forumTopicId = topics.maxOf { it.info.forumTopicId } + 1
                this.name = name
                icon = function.icon ?: TdApi.ForumTopicIcon(GENERAL_COLOR, 0)
                creationDate = world.now
                creatorId = TdApi.MessageSenderUser(DemoData.ME)
                isOutgoing = true
                isNameImplicit = function.isNameImplicit
            }
        val topic = Topic(info).also { topics += it }
        val created = TdApi.MessageForumTopicCreated(name, function.isNameImplicit, info.icon)
        val sent = world.addOwn(function.chatId, created, topic = TdApi.MessageTopicForum(info.forumTopicId))
        val updates = listOf(TdApi.UpdateForumTopicInfo(info), topicUpdate(world, topic)) + sent?.second.orEmpty()
        return changed(info, updates)
    }

    private fun change(
        world: DemoWorld,
        chatId: Long,
        topicId: Int,
        edit: (TdApi.ForumTopicInfo) -> Unit,
    ): DemoAnswer {
        val topic = topic(world, chatId, topicId) ?: return notFound()
        edit(topic.info)
        return okChanged(listOf(TdApi.UpdateForumTopicInfo(topic.info)))
    }

    private fun pin(
        world: DemoWorld,
        chatId: Long,
        topicId: Int,
        isPinned: Boolean,
    ): DemoAnswer {
        val topic = topic(world, chatId, topicId) ?: return notFound()
        val order = pinned.getOrPut(chatId) { mutableListOf() }
        order.remove(topicId)
        if (isPinned) order.add(0, topicId)
        return okChanged(listOf(topicUpdate(world, topic)))
    }

    private fun setPinned(
        world: DemoWorld,
        chatId: Long,
        topicIds: List<Int>,
    ): DemoAnswer {
        val topics = topicsOf(world, chatId) ?: return badRequest(FORUM_MISSING)
        val known = topicIds.filter { id -> topics.any { it.info.forumTopicId == id } }
        pinned[chatId] = known.toMutableList()
        return okChanged(topics.map { topicUpdate(world, it) })
    }

    private fun delete(
        world: DemoWorld,
        chatId: Long,
        topicId: Int,
    ): DemoAnswer {
        val topics = topicsOf(world, chatId) ?: return badRequest(FORUM_MISSING)
        val topic = topics.firstOrNull { it.info.forumTopicId == topicId } ?: return notFound()
        if (topic.info.isGeneral) return badRequest("TOPIC_ID_INVALID")
        topics.remove(topic)
        pinned[chatId]?.remove(topicId)
        val ids = world.messagesOf(chatId).filter { it.isIn(topicId) }.map { it.id }
        return okChanged(world.delete(chatId, ids))
    }

    private fun topicUpdate(
        world: DemoWorld,
        topic: Topic,
    ): TdApi.Update {
        val current = forumTopic(world, topic)
        return TdApi.UpdateForumTopic(
            topic.info.chatId,
            topic.info.forumTopicId,
            current.isPinned,
            current.lastReadInboxMessageId,
            current.lastReadOutboxMessageId,
            0,
            0,
            0,
            current.notificationSettings,
            null,
        )
    }

    private fun toggleForum(
        world: DemoWorld,
        function: TdApi.ToggleSupergroupIsForum,
    ): DemoAnswer {
        val group = world.groups.supergroups[function.supergroupId] ?: return notFound()
        if (group.isChannel) return badRequest("CHAT_INVALID")
        if (group.status !is TdApi.ChatMemberStatusCreator) return badRequest("CHAT_ADMIN_REQUIRED")
        group.isForum = function.isForum
        group.hasForumTabs = function.isForum && function.hasForumTabs
        return okChanged(listOf(TdApi.UpdateSupergroup(group)))
    }

    /** The thread a message starts: a post's comments live in the discussion group, under the post's copy there. */
    private fun thread(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
    ): DemoAnswer {
        val (threadChat, head) = threadHead(world, chatId, messageId) ?: return badRequest(MESSAGE_INVALID)
        val replyInfo = head.interactionInfo?.replyInfo ?: TdApi.MessageReplyInfo(0, emptyArray(), 0, 0, 0)
        return ok(TdApi.MessageThreadInfo(threadChat, head.id, replyInfo, 0, arrayOf(head), null))
    }

    private fun threadHistory(
        world: DemoWorld,
        function: TdApi.GetMessageThreadHistory,
    ): DemoAnswer {
        val (threadChat, head) =
            threadHead(world, function.chatId, function.messageId) ?: return badRequest(MESSAGE_INVALID)
        val replies =
            world.history(threadChat, function.fromMessageId, function.offset, function.limit) {
                (it.topicId as? TdApi.MessageTopicThread)?.messageThreadId == head.id
            }
        return ok(messages(replies))
    }

    private fun threadHead(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
    ): Pair<Long, TdApi.Message>? {
        if (chatId == DemoData.CHANNEL_CHAT) {
            val copy =
                world.messagesOf(DemoData.DISCUSSION_CHAT).firstOrNull {
                    (it.forwardInfo?.origin as? TdApi.MessageOriginChannel)?.messageId == messageId
                } ?: return null
            return DemoData.DISCUSSION_CHAT to copy
        }
        val message = world.message(chatId, messageId) ?: return null
        val threadId = (message.topicId as? TdApi.MessageTopicThread)?.messageThreadId
        val head = threadId?.let { world.message(chatId, it) } ?: message
        if (head.interactionInfo?.replyInfo == null) return null
        return chatId to head
    }

    private fun createSecretChat(
        world: DemoWorld,
        userId: Long,
    ): DemoAnswer {
        val user = world.users[userId] ?: return notFound()
        if (userId == DemoData.ME || user.type is TdApi.UserTypeBot) return badRequest("USER_ID_INVALID")
        secretChats.values
            .firstOrNull { it.userId == userId && it.state !is TdApi.SecretChatStateClosed }
            ?.let { open -> world.chat(DemoData.SECRET_CHAT_BASE + open.id)?.let { return ok(it) } }
        val id = (secretChats.keys.maxOrNull() ?: 0) + 1
        val keyHash = Random(id * KEY_SEED + userId).nextBytes(KEY_HASH_SIZE)
        val secretChat = TdApi.SecretChat(id, userId, TdApi.SecretChatStateReady(), true, keyHash, SECRET_CHAT_LAYER)
        secretChats[id] = secretChat
        val title = "${user.firstName} ${user.lastName}".trim()
        val chat = demoChat(DemoData.SECRET_CHAT_BASE + id, TdApi.ChatTypeSecret(id, userId), title, canSend = true)
        chat.canBeDeletedForAllUsers = false
        return changed(chat, listOf(TdApi.UpdateSecretChat(secretChat)) + world.addChat(chat))
    }

    /** Saved Messages' topics, once a session: TDLib answers 404 once all are loaded. */
    private fun loadSavedTopics(world: DemoWorld): DemoAnswer {
        if (savedTopicsLoaded) return notFound()
        savedTopicsLoaded = true
        val ids =
            world
                .messagesOf(DemoData.ME)
                .mapNotNull { (it.topicId as? TdApi.MessageTopicSavedMessages)?.savedMessagesTopicId }
                .distinct()
        val updates = ids.mapNotNull(world::savedTopic).map(TdApi::UpdateSavedMessagesTopic)
        return DemoAnswer(TdResult.Success(TdApi.Ok()), updates)
    }

    private fun TdApi.Message.isIn(topicId: Int): Boolean =
        when (val topic = topicId) {
            DemoData.GENERAL_TOPIC -> {
                this.topicId == null ||
                    (this.topicId as? TdApi.MessageTopicForum)?.forumTopicId == topic
            }

            else -> {
                (this.topicId as? TdApi.MessageTopicForum)?.forumTopicId == topic
            }
        }

    private fun messages(messages: List<TdApi.Message>) = TdApi.Messages(messages.size, messages.toTypedArray())

    private companion object {
        const val ORDER_SHIFT = 20
        const val GENERAL_COLOR = 0x6FB9F0
        const val KEY_SEED = 1_000_003L
        const val KEY_HASH_SIZE = 36
        const val SECRET_CHAT_LAYER = 144
        const val TOPIC_TITLE_EMPTY = "TOPIC_TITLE_EMPTY"
        const val FORUM_MISSING = "CHANNEL_FORUM_MISSING"
        const val MESSAGE_INVALID = "MSG_ID_INVALID"
    }
}
