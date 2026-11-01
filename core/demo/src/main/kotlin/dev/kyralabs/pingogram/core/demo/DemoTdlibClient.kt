package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The demo's [TdlibClient] (`docs/adr/0034-demo-mode-for-store-review.md`): it answers TDLib's functions from the
 * sample chats ([DemoData]) and emits the updates TDLib would, so the repositories and screens work unchanged. It
 * never creates a TDLib instance and uses no network: nothing here reaches `org.drinkless.tdlib.Client`.
 *
 * - It is signed in from the start ([authState] is ready) and always connected.
 * - [start] reads what the user did before ([DemoJournal]), replays it onto the sample data and announces everything,
 *   as TDLib announces users and chats after it starts: subscribe to [updates] before.
 * - Its [areas] answer the functions, each a part of the app (messages, chats…). A function that changes the demo goes
 *   into the journal, after the files it names are copied into the demo's folder, so they outlive the upload cache.
 *   A function no area answers fails with [NOT_AVAILABLE], which the app shows as needing a Telegram account.
 * - Its files are made on the phone the first time a screen downloads them ([DemoFile]).
 *
 * One constructor parameter per part tests replace (texts, journal, files, threads, time): hence the suppression.
 */
@Suppress("LongParameterList")
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

    private val areas: List<DemoArea> = listOf(DemoMessages(), DemoChats())

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
            val world = DemoWorld(data)
            events.filterIsInstance<DemoEvent.Call>().forEach { event ->
                world.now = event.date
                answer(world, event.function)
            }
            announce(data, world).forEach { mutableUpdates.emit(it) }
            ready.complete(world)
        }
    }

    override suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T> {
        start()
        val world = ready.await()
        val answer =
            when (function) {
                is TdApi.DownloadFile -> file(world, function.fileId, download = true)
                is TdApi.GetFile -> file(world, function.fileId, download = false)
                else -> change(world, function)
            }
        answer.updates.forEach { mutableUpdates.emit(it) }
        // Each area answers what td_api.tl says the function returns.
        @Suppress("UNCHECKED_CAST")
        return answer.result as TdResult<T>
    }

    private suspend fun change(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer {
        withContext(io) { keepFiles(function) }
        // The journal keeps the function as it was sent: an area may keep its objects in the demo's state.
        val record = DemoCodec.decode<TdApi.Function<*>>(DemoCodec.encode(function))
        return lock.withLock {
            world.now = clock()
            val answer = answer(world, function)
            if (answer.changed) withContext(io) { journal.append(DemoEvent.Call(world.now, record)) }
            answer
        }
    }

    private fun answer(
        world: DemoWorld,
        function: TdApi.Function<*>,
    ): DemoAnswer = areas.firstNotNullOfOrNull { it.answer(world, function) } ?: needsAccount()

    /** Copies the files [function] names into the demo's folder and names the copies instead. Blocking: on IO. */
    private fun keepFiles(function: TdApi.Function<*>) {
        val sent = directory.resolve(SENT)
        DemoCodec.walk(function) { value ->
            val input = value as? TdApi.InputFileLocal ?: return@walk
            val source = File(input.path)
            if (!source.isFile || source.canonicalPath.startsWith(sent.canonicalPath)) return@walk
            val copy = sent.resolve(UUID.randomUUID().toString()).resolve(source.name)
            runCatching {
                copy.parentFile?.mkdirs()
                source.copyTo(copy)
                input.path = copy.path
            }
        }
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

    /**
     * A demo file: one the user sent (on the phone already), or one of [DemoFile]'s, written on the IO dispatcher the
     * first time it is downloaded; an update tells the screens. Guard clauses for the two kinds and an unknown id:
     * hence the suppression.
     */
    @Suppress("ReturnCount")
    private suspend fun file(
        world: DemoWorld,
        fileId: Int,
        download: Boolean,
    ): DemoAnswer {
        val sent = lock.withLock { world.knownFile(fileId)?.takeIf { world.sentFileById(fileId) != null } }
        if (sent != null) return ok(sent)
        val file = DemoFile.of(fileId) ?: return notFound()
        val target = directory.resolve(file.fileName)
        val written =
            withContext(io) {
                if (download && !target.isFile) runCatching { fileWriter.write(file, target) }
                target.isFile
            }
        val state = DemoFiles.state(file, target.takeIf { written })
        return DemoAnswer(
            TdResult.Success(state),
            if (download &&
                written
            ) {
                listOf(TdApi.UpdateFile(state))
            } else {
                emptyList()
            },
        )
    }

    companion object {
        /** The error of every function the demo does not answer; `core:data` maps it to `NotAvailableInDemo`. */
        const val NOT_AVAILABLE = "NOT_AVAILABLE_IN_DEMO"

        private const val UPDATE_BUFFER_CAPACITY = 4096
        private const val SENT = "sent"
    }
}
