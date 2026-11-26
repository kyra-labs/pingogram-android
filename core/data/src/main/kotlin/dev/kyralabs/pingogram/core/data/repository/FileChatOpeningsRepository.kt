package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.updates.updateRouter
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import dev.kyralabs.pingogram.core.domain.repository.ChatOpeningsRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * [ChatOpeningsRepository] in a small file of the account's TDLib directory (`chat_openings.txt`), which the account
 * leaving the device deletes with the rest of it. The demo keeps them in memory only.
 *
 * - **What is kept**: when the file was begun, then a chat's id and when it was last opened, to the second; nothing of
 *   the chat. A chat counts as opened when its conversation shows ([ChatOpeningRecorder]).
 * - **Read** once, on the [io] dispatcher, the first time they are asked for or a chat opens; a file not there yet is
 *   begun now. Then kept in memory.
 * - **Written** whole after each change, through a temporary file, so a write cut short leaves the last good one. At
 *   most [MAX_CHATS], the longest unopened dropped first.
 * - **Logging out** forgets them and begins anew: it is a [TdlibUpdateCache], made before TDLib starts, so it sees
 *   `AuthorizationStateLoggingOut`.
 */
@AccountScoped
class FileChatOpeningsRepository internal constructor(
    client: TdlibClient,
    private val clock: WallClock,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : ChatOpeningsRepository,
    TdlibUpdateCache {
    @Inject
    constructor(
        client: TdlibClient,
        clock: WallClock,
        slot: AccountSlot,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(
        client = client,
        clock = clock,
        scope = scope,
        file = if (slot.isDemo) null else TdlibDirectories.of(context.filesDir, slot).root.resolve(FILE_NAME),
        io = io,
    )

    /** The openings; null until read. */
    private val all = MutableStateFlow<ChatOpenings?>(null)

    /** Serializes reading, writing and deleting the file. */
    private val mutex = Mutex()

    init {
        // Subscribed before the constructor returns, as for every TdlibUpdateCache.
        client.updateRouter(scope).onUpdates(setOf(TdApi.UpdateAuthorizationState::class)) { update ->
            val state = (update as TdApi.UpdateAuthorizationState).authorizationState
            if (state is TdApi.AuthorizationStateLoggingOut) scope.launch { clear() }
        }
    }

    override val openings: Flow<ChatOpenings> =
        flow {
            mutex.withLock { loadLocked() }
            emitAll(all.filterNotNull())
        }.distinctUntilChanged()

    override suspend fun markOpened(chatId: Long): Outcome<Unit> =
        mutex.withLock {
            val current = loadLocked()
            val now = clock.now().truncatedTo(ChronoUnit.SECONDS)
            val next = current.copy(lastOpened = (current.lastOpened - chatId + (chatId to now)).kept())
            all.value = next
            try {
                write(next)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Opening not saved: ${error.message}"))
            }
        }

    /** The openings, read from the file the first time, or begun now; with [mutex] held. */
    private suspend fun loadLocked(): ChatOpenings =
        all.value ?: withContext(io) { read() ?: begun() }.also { all.value = it }

    /** A new record, begun now and written, so a later start keeps when it began. */
    private suspend fun begun(): ChatOpenings {
        val openings = ChatOpenings(since = clock.now().truncatedTo(ChronoUnit.SECONDS))
        try {
            write(openings)
        } catch (_: IOException) {
            // Kept in memory: the next start begins anew.
        }
        return openings
    }

    private suspend fun clear() {
        mutex.withLock {
            all.value = null
            withContext(io) { file?.delete() }
        }
    }

    private fun read(): ChatOpenings? {
        val lines =
            try {
                file?.takeIf { it.isFile }?.readLines()
            } catch (_: IOException) {
                null
            }
        val since = lines?.firstOrNull()?.let(::decodeSince) ?: return null
        val opened = lines.drop(1).mapNotNull(::decodeOpening).toMap()
        return ChatOpenings(since, opened.kept())
    }

    private suspend fun write(openings: ChatOpenings) {
        val target = file ?: return
        withContext(io) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(
                buildString {
                    append(SINCE_KEY).append('\t').append(openings.since.epochSecond).append('\n')
                    openings.lastOpened.forEach { (chatId, at) ->
                        append(chatId).append('\t').append(at.epochSecond).append('\n')
                    }
                },
            )
            if (!temporary.renameTo(target)) throw IOException("Could not replace $FILE_NAME")
        }
    }

    internal companion object {
        const val FILE_NAME = "chat_openings.txt"

        /** The most chats kept: about a hundred kilobytes at most. */
        const val MAX_CHATS = 5_000
    }
}

/** The [FileChatOpeningsRepository.MAX_CHATS] most recently opened. */
private fun Map<Long, Instant>.kept() =
    entries
        .sortedBy { it.value }
        .takeLast(FileChatOpeningsRepository.MAX_CHATS)
        .associate { it.key to it.value }
        .toImmutableMap()

/** The first line, when the record began; null for one that is not (a file cut short). */
private fun decodeSince(line: String): Instant? =
    line
        .split('\t')
        .takeIf { it.size == 2 && it[0] == SINCE_KEY }
        ?.get(1)
        ?.toLongOrNull()
        ?.let(Instant::ofEpochSecond)

/** A line of a chat and when it was opened, tab-separated, back; null for one that is not (a file cut short). */
private fun decodeOpening(line: String): Pair<Long, Instant>? =
    line
        .split('\t')
        .mapNotNull { it.toLongOrNull() }
        .takeIf { it.size == 2 }
        ?.let { (chatId, at) -> chatId to Instant.ofEpochSecond(at) }

/** The first line's key, before when the record began. */
private const val SINCE_KEY = "since"
