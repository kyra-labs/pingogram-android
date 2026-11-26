package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.updates.updateRouter
import dev.kyralabs.pingogram.core.domain.repository.UnansweredDismissalRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
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
import javax.inject.Inject

/**
 * [UnansweredDismissalRepository] in a small file of the account's TDLib directory (`unanswered_dismissed.txt`), which
 * the account leaving the device deletes with the rest of it. The demo keeps them in memory only.
 *
 * - **What is kept**: a chat's id and the id of its last message when dismissed; nothing of the chat or message.
 * - **Read** once, on the [io] dispatcher, the first time they are asked for; then kept in memory.
 * - **Written** whole after each change, through a temporary file, so a write cut short leaves the last good one. At
 *   most [MAX_DISMISSED], the oldest dismissal dropped first.
 * - **Logging out** deletes them: it is a [TdlibUpdateCache], made before TDLib starts, so it sees
 *   `AuthorizationStateLoggingOut`. Chat ids outlive TDLib closing, so closing keeps them.
 */
@AccountScoped
class FileUnansweredDismissalRepository internal constructor(
    client: TdlibClient,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : UnansweredDismissalRepository,
    TdlibUpdateCache {
    @Inject
    constructor(
        client: TdlibClient,
        slot: AccountSlot,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(
        client = client,
        scope = scope,
        file = if (slot.isDemo) null else TdlibDirectories.of(context.filesDir, slot).root.resolve(FILE_NAME),
        io = io,
    )

    /** Every dismissal, by chat id, oldest first; null until read. */
    private val all = MutableStateFlow<ImmutableMap<Long, Long>?>(null)

    /** Serializes reading, writing and deleting the file. */
    private val mutex = Mutex()

    init {
        // Subscribed before the constructor returns, as for every TdlibUpdateCache.
        client.updateRouter(scope).onUpdates(setOf(TdApi.UpdateAuthorizationState::class)) { update ->
            val state = (update as TdApi.UpdateAuthorizationState).authorizationState
            if (state is TdApi.AuthorizationStateLoggingOut) scope.launch { clear() }
        }
    }

    override val dismissed: Flow<ImmutableMap<Long, Long>> =
        flow {
            load()
            emitAll(all.filterNotNull())
        }.distinctUntilChanged()

    override suspend fun dismiss(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> =
        mutex.withLock {
            // Dismissed again, a chat moves to the end, as the newest.
            val next = (loadLocked() - chatId + (chatId to messageId)).kept()
            all.value = next
            try {
                write(next)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Dismissal not saved: ${error.message}"))
            }
        }

    private suspend fun load() {
        if (all.value == null) mutex.withLock { loadLocked() }
    }

    /** The dismissals, read from the file the first time; with [mutex] held. */
    private suspend fun loadLocked(): ImmutableMap<Long, Long> =
        all.value ?: withContext(io) { read() }.also { all.value = it }

    private suspend fun clear() {
        mutex.withLock {
            all.value = persistentMapOf()
            withContext(io) { file?.delete() }
        }
    }

    private fun read(): ImmutableMap<Long, Long> {
        val lines =
            try {
                file?.takeIf { it.isFile }?.readLines().orEmpty()
            } catch (_: IOException) {
                emptyList()
            }
        return lines.mapNotNull(::decodeDismissal).toMap().kept()
    }

    private suspend fun write(dismissed: Map<Long, Long>) {
        val target = file ?: return
        withContext(io) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(dismissed.entries.joinToString(separator = "") { "${it.key}\t${it.value}\n" })
            if (!temporary.renameTo(target)) throw IOException("Could not replace $FILE_NAME")
        }
    }

    internal companion object {
        const val FILE_NAME = "unanswered_dismissed.txt"

        /** The most dismissals kept: a few dozen kilobytes at most. */
        const val MAX_DISMISSED = 2_000
    }
}

/** The newest [FileUnansweredDismissalRepository.MAX_DISMISSED], in their order. */
private fun Map<Long, Long>.kept(): ImmutableMap<Long, Long> =
    entries
        .toList()
        .takeLast(FileUnansweredDismissalRepository.MAX_DISMISSED)
        .associate { it.key to it.value }
        .toImmutableMap()

/** A line of a chat and a message, tab-separated, back; null for one that is not (a file cut short). */
private fun decodeDismissal(line: String): Pair<Long, Long>? =
    line
        .split('\t')
        .mapNotNull { it.toLongOrNull() }
        .takeIf { it.size == 2 }
        ?.let { (chatId, messageId) -> chatId to messageId }
