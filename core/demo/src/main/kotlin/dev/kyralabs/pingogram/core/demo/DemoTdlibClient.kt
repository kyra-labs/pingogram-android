package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The demo's [TdlibClient] (`docs/adr/0034-demo-mode-for-store-review.md`): it answers TDLib's functions from the
 * sample chats ([DemoData]) and emits the updates TDLib would, so the repositories and screens work unchanged. It
 * never creates a TDLib instance and uses no network: nothing here reaches `org.drinkless.tdlib.Client`.
 *
 * - It is signed in from the start ([authState] is ready) and always connected.
 * - [start] reads what the user did before ([DemoJournal]), replays it onto the sample data and announces everything,
 *   as TDLib announces users and chats after it starts: subscribe to [updates] before.
 * - What the user does (sending texts, reading, reactions, votes, deleting their own messages, pinning, archiving,
 *   muting) changes the demo and goes into the journal. Everything else fails with [NOT_AVAILABLE], which the app
 *   shows as "Not available in the demo".
 * - Its files are made on the phone the first time a screen downloads them ([DemoFile]).
 *
 * One function per group of TDLib functions it answers, with guard clauses for what it cannot answer, and one
 * constructor parameter per part tests replace (texts, journal, files, threads, time): hence the suppressions.
 */
@Suppress("TooManyFunctions", "ReturnCount", "LongParameterList")
class DemoTdlibClient internal constructor(
    private val text: (Int) -> String,
    private val journal: DemoJournal,
    private val directory: File,
    private val fileWriter: DemoFileWriter,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Int,
) : TdlibClient {
    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Ready)
    override val authState: StateFlow<AuthState> = mutableAuthState.asStateFlow()

    private val mutableConnectionState = MutableStateFlow(ConnectionState.Ready)
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()

    private val mutableUpdates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = UPDATE_BUFFER_CAPACITY)
    override val updates: SharedFlow<TdApi.Update> = mutableUpdates.asSharedFlow()

    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<DemoWorld>()
    private val lock = Mutex()

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val events = withContext(io) { journal.read() }
            val startedAt = events.filterIsInstance<DemoEvent.Started>().firstOrNull()?.date
            if (startedAt == null) withContext(io) { journal.append(DemoEvent.Started(clock())) }
            val data = DemoData(text, startedAt ?: clock())
            val world = DemoWorld(data).apply { events.forEach(::replay) }
            announce(data, world).forEach { mutableUpdates.emit(it) }
            ready.complete(world)
        }
    }

    override suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T> {
        start()
        val world = ready.await()
        val (result, updates) = lock.withLock { answer(world, function) }
        updates.forEach { mutableUpdates.emit(it) }
        // Each branch of answer() returns what td_api.tl says the function returns.
        @Suppress("UNCHECKED_CAST")
        return result as TdResult<T>
    }

    /** What TDLib announces after it starts: the user, the people, the group and channel, the chats and the folder. */
    private fun announce(
        data: DemoData,
        world: DemoWorld,
    ): List<TdApi.Update> =
        buildList {
            add(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
            add(TdApi.UpdateOption("my_id", TdApi.OptionValueInteger(DemoData.ME)))
            add(TdApi.UpdateConnectionState(TdApi.ConnectionStateReady()))
            world.users.values.forEach { add(TdApi.UpdateUser(it)) }
            add(TdApi.UpdateBasicGroup(data.group))
            add(TdApi.UpdateSupergroup(data.channel))
            listOf(
                TdApi.NotificationSettingsScopePrivateChats(),
                TdApi.NotificationSettingsScopeGroupChats(),
                TdApi.NotificationSettingsScopeChannelChats(),
            ).forEach { add(TdApi.UpdateScopeNotificationSettings(it, DemoNotifications.scopeSettings())) }
            add(TdApi.UpdateActiveEmojiReactions(DemoData.REACTIONS.toTypedArray()))
            add(TdApi.UpdateDefaultReactionType(TdApi.ReactionTypeEmoji(DemoData.REACTIONS.first())))
            world.allChats().forEach { add(TdApi.UpdateNewChat(it)) }
            add(TdApi.UpdateChatFolders(arrayOf(data.folder), 0, false))
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per TDLib function the demo answers.
    private suspend fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> =
        when (function) {
            is TdApi.GetOption -> {
                ok(option(function.name))
            }

            is TdApi.SetOption, is TdApi.OpenChat, is TdApi.CloseChat, is TdApi.SetChatDraftMessage,
            is TdApi.ReadAllChatMentions, is TdApi.ReadAllChatReactions, is TdApi.CancelDownloadFile,
            -> {
                ok(TdApi.Ok())
            }

            is TdApi.GetMe -> {
                ok(world.users.getValue(DemoData.ME))
            }

            is TdApi.GetUser -> {
                world.users[function.userId]?.let(::ok) ?: notFound()
            }

            is TdApi.GetUserFullInfo -> {
                ok(world.data.userFullInfo(function.userId))
            }

            is TdApi.GetContacts -> {
                ok(users(world.users.values.filter { it.isContact }))
            }

            is TdApi.SearchContacts -> {
                ok(
                    users(
                        world.users.values
                            .filter { it.isContact && it.matches(function.query) }
                            .take(function.limit),
                    ),
                )
            }

            is TdApi.GetChat -> {
                world.chat(function.chatId)?.let(::ok) ?: notFound()
            }

            is TdApi.CreatePrivateChat -> {
                world.chat(function.userId)?.let(::ok) ?: notAvailable()
            }

            is TdApi.GetChats -> {
                ok(
                    TdApi.Chats(
                        0,
                        world
                            .chatsIn(function.chatList)
                            .take(function.limit)
                            .map { it.id }
                            .toLongArray(),
                    ),
                )
            }

            // Every chat was announced when the demo started: there is nothing more to load.
            is TdApi.LoadChats, is TdApi.LoadSavedMessagesTopics, is TdApi.LoadActiveStories -> {
                notFound()
            }

            is TdApi.GetChatFolder -> {
                if (function.chatFolderId == DemoData.WORK_FOLDER) ok(world.data.folderSettings) else notFound()
            }

            is TdApi.GetBasicGroup -> {
                ok(world.data.group)
            }

            is TdApi.GetSupergroup -> {
                ok(world.data.channel)
            }

            is TdApi.GetBasicGroupFullInfo -> {
                ok(world.data.groupFullInfo)
            }

            is TdApi.GetSupergroupFullInfo -> {
                ok(world.data.channelFullInfo)
            }

            is TdApi.GetScopeNotificationSettings -> {
                ok(DemoNotifications.scopeSettings())
            }

            is TdApi.GetChatSponsoredMessages -> {
                ok(TdApi.SponsoredMessages(emptyArray(), 0))
            }

            is TdApi.GetChatActiveStories -> {
                ok(TdApi.ChatActiveStories(function.chatId, null, 0, false, 0, emptyArray()))
            }

            is TdApi.GetChatHistory -> {
                ok(messages(world.history(function.chatId, function.fromMessageId, function.offset, function.limit)))
            }

            is TdApi.GetMessage -> {
                world.message(function.chatId, function.messageId)?.let(::ok) ?: notFound()
            }

            is TdApi.GetRepliedMessage -> {
                repliedMessage(world, function.chatId, function.messageId)
            }

            is TdApi.SearchChatMessages -> {
                ok(
                    TdApi.FoundChatMessages(
                        0,
                        if (function.filter is TdApi.SearchMessagesFilterPinned) {
                            world
                                .pinned(
                                    function.chatId,
                                ).toTypedArray()
                        } else {
                            emptyArray()
                        },
                        0,
                    ),
                )
            }

            is TdApi.GetMessageProperties -> {
                world.message(function.chatId, function.messageId)?.let { ok(properties(it)) } ?: notFound()
            }

            is TdApi.GetMessageAvailableReactions -> {
                ok(availableReactions())
            }

            is TdApi.SendMessage -> {
                send(world, function)
            }

            is TdApi.ViewMessages -> {
                change(world, DemoEvent.Read(function.chatId, function.messageIds.maxOrNull() ?: 0))
            }

            is TdApi.AddMessageReaction -> {
                reaction(world, function.chatId, function.messageId, function.reactionType, true)
            }

            is TdApi.RemoveMessageReaction -> {
                reaction(world, function.chatId, function.messageId, function.reactionType, false)
            }

            is TdApi.SetPollAnswer -> {
                change(world, DemoEvent.Vote(function.chatId, function.messageId, function.optionIds.toList()))
            }

            is TdApi.DeleteMessages -> {
                change(world, DemoEvent.Deleted(function.chatId, function.messageIds.toList()))
            }

            is TdApi.ToggleChatIsPinned -> {
                change(world, DemoEvent.Pinned(function.chatId, function.isPinned))
            }

            is TdApi.ToggleChatIsMarkedAsUnread -> {
                change(world, DemoEvent.MarkedUnread(function.chatId, function.isMarkedAsUnread))
            }

            is TdApi.SetChatNotificationSettings -> {
                change(world, DemoEvent.Muted(function.chatId, function.notificationSettings.muteFor))
            }

            is TdApi.AddChatToList -> {
                change(world, DemoEvent.Archived(function.chatId, function.chatList is TdApi.ChatListArchive))
            }

            is TdApi.DownloadFile -> {
                file(function.fileId, download = true)
            }

            is TdApi.GetFile -> {
                file(function.fileId, download = false)
            }

            is TdApi.GetInstalledStickerSets, is TdApi.GetTrendingStickerSets, is TdApi.GetArchivedStickerSets -> {
                ok(TdApi.StickerSets(0, emptyArray()))
            }

            is TdApi.GetRecentStickers, is TdApi.GetFavoriteStickers -> {
                ok(TdApi.Stickers(emptyArray()))
            }

            is TdApi.GetSavedAnimations -> {
                ok(TdApi.Animations(emptyArray()))
            }

            else -> {
                notAvailable()
            }
        }

    private fun option(name: String): TdApi.OptionValue =
        when (name) {
            "my_id" -> TdApi.OptionValueInteger(DemoData.ME)
            "version" -> TdApi.OptionValueString("demo")
            else -> TdApi.OptionValueEmpty()
        }

    private fun repliedMessage(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
    ): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> {
        val reply = world.message(chatId, messageId)?.replyTo as? TdApi.MessageReplyToMessage ?: return notFound()
        return world.message(reply.chatId, reply.messageId)?.let(::ok) ?: notFound()
    }

    /** Only texts: the demo has nothing to upload to, and other content would need files it does not have. */
    private suspend fun send(
        world: DemoWorld,
        function: TdApi.SendMessage,
    ): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> {
        val content = function.inputMessageContent as? TdApi.InputMessageText ?: return notAvailable()
        val chat = world.chat(function.chatId) ?: return notFound()
        if (!chat.permissions.canSendBasicMessages && chat.id != DemoData.ME) return notAvailable()
        if (function.options?.schedulingState != null) return notAvailable()
        val replyTo = (function.replyTo as? TdApi.InputMessageReplyToMessage)?.messageId ?: 0
        val event = DemoEvent.Sent(chat.id, world.nextMessageId(), clock(), content.text.text, replyTo)
        withContext(io) { journal.append(event) }
        val updates = world.send(event)
        val message = (updates.first() as TdApi.UpdateNewMessage).message
        return TdResult.Success(message) to updates
    }

    private suspend fun reaction(
        world: DemoWorld,
        chatId: Long,
        messageId: Long,
        type: TdApi.ReactionType,
        added: Boolean,
    ): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> {
        val emoji = (type as? TdApi.ReactionTypeEmoji)?.emoji ?: return notAvailable()
        return change(world, DemoEvent.Reaction(chatId, messageId, emoji, added))
    }

    /** Applies [event] and keeps it in the journal, answering Ok with its updates. */
    private suspend fun change(
        world: DemoWorld,
        event: DemoEvent,
    ): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> {
        val updates =
            when (event) {
                is DemoEvent.Read -> world.read(event)
                is DemoEvent.Reaction -> world.react(event)
                is DemoEvent.Vote -> world.vote(event)
                is DemoEvent.Deleted -> world.delete(event)
                is DemoEvent.Pinned -> world.pin(event)
                is DemoEvent.MarkedUnread -> world.markUnread(event)
                is DemoEvent.Muted -> world.mute(event)
                is DemoEvent.Archived -> world.archive(event)
                is DemoEvent.Started, is DemoEvent.Sent -> emptyList()
            }
        if (updates.isNotEmpty()) withContext(io) { journal.append(event) }
        return TdResult.Success(TdApi.Ok()) to updates
    }

    /** A demo file, written on the IO dispatcher the first time it is downloaded; an update tells the screens. */
    private suspend fun file(
        fileId: Int,
        download: Boolean,
    ): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> {
        val file = DemoFile.of(fileId) ?: return notFound()
        val target = directory.resolve(file.fileName)
        val written =
            withContext(io) {
                if (download && !target.isFile) runCatching { fileWriter.write(file, target) }
                target.isFile
            }
        val state = DemoFiles.state(file, target.takeIf { written })
        return TdResult.Success(state) to if (download && written) listOf(TdApi.UpdateFile(state)) else emptyList()
    }

    private fun properties(message: TdApi.Message) =
        TdApi.MessageProperties().apply {
            canBeCopied = true
            canBeReplied = message.chatId != DemoData.CHANNEL_CHAT
            canBeDeletedOnlyForSelf = message.isOutgoing
            canBeSaved = true
            customEmojiIds = longArrayOf()
        }

    private fun availableReactions(): TdApi.AvailableReactions {
        val reactions =
            DemoData.REACTIONS.map { TdApi.AvailableReaction(TdApi.ReactionTypeEmoji(it), false) }.toTypedArray()
        return TdApi.AvailableReactions(reactions, emptyArray(), emptyArray(), false, false, null)
    }

    private fun messages(messages: List<TdApi.Message>) = TdApi.Messages(messages.size, messages.toTypedArray())

    private fun users(users: Collection<TdApi.User>) = TdApi.Users(users.size, users.map { it.id }.toLongArray())

    private fun TdApi.User.matches(query: String) =
        query.isBlank() || "$firstName $lastName".contains(query.trim(), ignoreCase = true)

    private fun ok(value: TdApi.Object): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> =
        TdResult.Success(value) to emptyList()

    private fun notFound(): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> =
        TdResult.Failure(TdError(NOT_FOUND, "Not Found")) to emptyList()

    private fun notAvailable(): Pair<TdResult<TdApi.Object>, List<TdApi.Update>> =
        TdResult.Failure(TdError(BAD_REQUEST, NOT_AVAILABLE)) to emptyList()

    companion object {
        /** The error of every function the demo does not answer; `core:data` maps it to `NotAvailableInDemo`. */
        const val NOT_AVAILABLE = "NOT_AVAILABLE_IN_DEMO"

        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val UPDATE_BUFFER_CAPACITY = 4096
    }
}
