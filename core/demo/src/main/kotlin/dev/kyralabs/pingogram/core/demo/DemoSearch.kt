package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi

/**
 * Search in the demo (`docs/adr/0034-demo-mode-for-store-review.md`): chats by title, messages by text, kind and
 * sender (global search, search in a chat, shared media), the recently found chats, frequent contacts, a day's first
 * message and the links, mentions and hashtags in a typed text, all on the sample chats. It keeps the recently found
 * chats itself; their changes go into the journal like every other change.
 *
 * One branch per function: hence the suppressions.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions")
internal class DemoSearch : DemoArea {
    private val recentlyFound = mutableListOf<Long>()

    override fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer? =
        when (function) {
            is TdApi.SearchChats -> {
                ok(chats(chatsMatching(world, function.query).take(function.limit)))
            }

            is TdApi.SearchChatsOnServer -> {
                ok(chats(chatsMatching(world, function.query).take(function.limit)))
            }

            // The demo's chats are all private to it: none is public.
            is TdApi.SearchPublicChats -> {
                ok(chats(emptyList()))
            }

            is TdApi.SearchRecentlyFoundChats -> {
                val found =
                    recentlyFound
                        .mapNotNull(
                            world::chat,
                        ).filter { it.title.contains(function.query.trim(), true) }
                ok(chats(found.take(function.limit)))
            }

            is TdApi.AddRecentlyFoundChat -> {
                recentlyFound.remove(function.chatId)
                recentlyFound.add(0, function.chatId)
                okChanged(emptyList())
            }

            is TdApi.RemoveRecentlyFoundChat -> {
                recentlyFound.remove(function.chatId)
                okChanged(emptyList())
            }

            is TdApi.ClearRecentlyFoundChats -> {
                recentlyFound.clear()
                okChanged(emptyList())
            }

            is TdApi.GetTopChats -> {
                val people =
                    if (function.category is TdApi.TopChatCategoryUsers) {
                        world.chatsIn(TdApi.ChatListMain()).filter {
                            (it.type as? TdApi.ChatTypePrivate)?.userId.let { id -> id != null && id != DemoData.ME }
                        }
                    } else {
                        emptyList()
                    }
                ok(chats(people.take(function.limit)))
            }

            is TdApi.SearchMessages -> {
                searchAll(world, function)
            }

            is TdApi.SearchChatMessages -> {
                searchChat(world, function)
            }

            // The demo has no secret chat with messages to search.
            is TdApi.SearchSecretMessages -> {
                ok(TdApi.FoundMessages(0, emptyArray(), ""))
            }

            is TdApi.GetChatMessageByDate -> {
                world.messagesOf(function.chatId).lastOrNull { it.date <= function.date }?.let(::ok) ?: notFound()
            }

            is TdApi.GetChatMessagePosition -> {
                val newestFirst = world.messagesOf(function.chatId).asReversed().filter { matches(function.filter, it) }
                val index = newestFirst.indexOfFirst { it.id == function.messageId }
                if (index < 0) notFound() else ok(TdApi.Count(index + 1))
            }

            is TdApi.GetTextEntities -> {
                ok(TdApi.TextEntities(entitiesOf(function.text).toTypedArray()))
            }

            else -> {
                null
            }
        }

    private fun searchAll(
        world: DemoWorld,
        function: TdApi.SearchMessages,
    ): DemoAnswer {
        val lists = function.chatList?.let(::listOf) ?: listOf(TdApi.ChatListMain(), TdApi.ChatListArchive())
        val found =
            lists
                .flatMap(world::chatsIn)
                .flatMap { world.messagesOf(it.id) }
                .filter { function.minDate == 0 || it.date >= function.minDate }
                .filter { function.maxDate == 0 || it.date <= function.maxDate }
                .filter { matches(function.filter, it) && containsQuery(it, function.query) }
                .sortedWith(compareByDescending<TdApi.Message> { it.date }.thenByDescending { it.id })
        val start = function.offset.toIntOrNull() ?: 0
        val page = found.drop(start).take(function.limit)
        val next = if (start + page.size < found.size) (start + page.size).toString() else ""
        return ok(TdApi.FoundMessages(found.size, page.toTypedArray(), next))
    }

    private fun searchChat(
        world: DemoWorld,
        function: TdApi.SearchChatMessages,
    ): DemoAnswer {
        val sender = function.senderId
        val found =
            world
                .messagesOf(function.chatId)
                .asReversed()
                .filter { matches(function.filter, it) && containsQuery(it, function.query) }
                .filter { sender == null || sameSender(sender, it.senderId) }
        val from =
            if (function.fromMessageId == 0L) {
                0
            } else {
                found.indexOfFirst { it.id <= function.fromMessageId }.takeIf { it >= 0 } ?: found.size
            }
        val start = (from + function.offset).coerceIn(0, found.size)
        val page = found.drop(start).take(function.limit)
        val next = if (start + page.size < found.size) page.lastOrNull()?.id ?: 0 else 0
        return ok(TdApi.FoundChatMessages(found.size, page.toTypedArray(), next))
    }

    private fun chatsMatching(
        world: DemoWorld,
        query: String,
    ): List<TdApi.Chat> {
        val words = query.trim()
        if (words.isEmpty()) return emptyList()
        return (world.chatsIn(TdApi.ChatListMain()) + world.chatsIn(TdApi.ChatListArchive()))
            .filter { it.title.contains(words, ignoreCase = true) }
    }

    private fun chats(chats: List<TdApi.Chat>) = TdApi.Chats(chats.size, chats.map { it.id }.toLongArray())

    private fun sameSender(
        a: TdApi.MessageSender,
        b: TdApi.MessageSender,
    ) = (a is TdApi.MessageSenderUser && b is TdApi.MessageSenderUser && a.userId == b.userId) ||
        (a is TdApi.MessageSenderChat && b is TdApi.MessageSenderChat && a.chatId == b.chatId)

    private fun containsQuery(
        message: TdApi.Message,
        query: String,
    ): Boolean {
        val words = query.trim()
        return words.isEmpty() || textOf(message.content)?.text?.contains(words, ignoreCase = true) == true
    }

    /** Whether [message] is of the kind [filter] asks for, as TDLib's search filters sort messages. */
    private fun matches(
        filter: TdApi.SearchMessagesFilter?,
        message: TdApi.Message,
    ): Boolean {
        val content = message.content
        return when (filter) {
            null, is TdApi.SearchMessagesFilterEmpty -> {
                true
            }

            is TdApi.SearchMessagesFilterPhotoAndVideo -> {
                content is TdApi.MessagePhoto || content is TdApi.MessageVideo
            }

            is TdApi.SearchMessagesFilterPhoto -> {
                content is TdApi.MessagePhoto
            }

            is TdApi.SearchMessagesFilterVideo -> {
                content is TdApi.MessageVideo
            }

            is TdApi.SearchMessagesFilterDocument -> {
                content is TdApi.MessageDocument
            }

            is TdApi.SearchMessagesFilterAudio -> {
                content is TdApi.MessageAudio
            }

            is TdApi.SearchMessagesFilterAnimation -> {
                content is TdApi.MessageAnimation
            }

            is TdApi.SearchMessagesFilterVoiceNote -> {
                content is TdApi.MessageVoiceNote
            }

            is TdApi.SearchMessagesFilterVideoNote -> {
                content is TdApi.MessageVideoNote
            }

            is TdApi.SearchMessagesFilterVoiceAndVideoNote -> {
                content is TdApi.MessageVoiceNote || content is TdApi.MessageVideoNote
            }

            is TdApi.SearchMessagesFilterUrl -> {
                hasLink(content)
            }

            is TdApi.SearchMessagesFilterPinned -> {
                message.isPinned
            }

            else -> {
                false
            }
        }
    }

    private fun hasLink(content: TdApi.MessageContent): Boolean {
        val text = textOf(content) ?: return false
        return text.entities.any { it.type is TdApi.TextEntityTypeUrl || it.type is TdApi.TextEntityTypeTextUrl } ||
            entitiesOf(text.text).any { it.type is TdApi.TextEntityTypeUrl }
    }

    private fun textOf(content: TdApi.MessageContent): TdApi.FormattedText? =
        when (content) {
            is TdApi.MessageText -> {
                content.text
            }

            else -> {
                runCatching {
                    content.javaClass
                        .getField(
                            "caption",
                        ).get(content) as? TdApi.FormattedText
                }.getOrNull()
            }
        }

    private companion object {
        /** Links, emails, mentions and hashtags in a text, as `getTextEntities` finds them; the longer of two wins. */
        fun entitiesOf(text: String): List<TdApi.TextEntity> {
            val found =
                PATTERNS
                    .flatMap { (pattern, type) ->
                        pattern.findAll(text).map { TdApi.TextEntity(it.range.first, it.value.length, type()) }.toList()
                    }.sortedWith(compareBy<TdApi.TextEntity> { it.offset }.thenByDescending { it.length })
            val kept = mutableListOf<TdApi.TextEntity>()
            found.forEach { entity ->
                val previous = kept.lastOrNull()
                if (previous == null || entity.offset >= previous.offset + previous.length) kept += entity
            }
            return kept
        }

        val PATTERNS: List<Pair<Regex, () -> TdApi.TextEntityType>> =
            listOf(
                Regex("""https?://[^\s]+|\b[\w-]+\.(?:org|com|net|dev|me)(?:/[^\s]*)?""") to
                    { TdApi.TextEntityTypeUrl() },
                Regex("""\b[\w.+-]+@[\w-]+\.[\w.]+\b""") to { TdApi.TextEntityTypeEmailAddress() },
                Regex("""(?<![\w@])@\w{3,32}""") to { TdApi.TextEntityTypeMention() },
                Regex("""(?<!\w)#\w+""") to { TdApi.TextEntityTypeHashtag() },
            )
    }
}
