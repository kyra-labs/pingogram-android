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
import dev.kyralabs.pingogram.core.domain.model.ChatNote
import dev.kyralabs.pingogram.core.domain.repository.ChatNoteRepository
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
 * [ChatNoteRepository] in a small file of the account's TDLib directory (`chat_notes.txt`), which the account leaving
 * the device deletes with the rest of it. The demo keeps its notes in memory only.
 *
 * - **Read** once, on the [io] dispatcher, the first time the notes are asked for; then kept in memory.
 * - **Written** whole after each change, through a temporary file, so a write cut short leaves the last good one. At
 *   most [MAX_NOTES] chats are kept.
 * - **Logging out** deletes them all: it is a [TdlibUpdateCache], made before TDLib starts, so it sees
 *   `AuthorizationStateLoggingOut`. Chat ids outlive TDLib closing, so closing keeps them.
 */
@AccountScoped
class FileChatNoteRepository internal constructor(
    client: TdlibClient,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : ChatNoteRepository,
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

    /** Every note, by chat id; null until read. */
    private val all = MutableStateFlow<ImmutableMap<Long, ChatNote>?>(null)

    /** Serializes reading, writing and deleting the file. */
    private val mutex = Mutex()

    init {
        // Subscribed before the constructor returns, as for every TdlibUpdateCache.
        client.updateRouter(scope).onUpdates(setOf(TdApi.UpdateAuthorizationState::class)) { update ->
            val state = (update as TdApi.UpdateAuthorizationState).authorizationState
            if (state is TdApi.AuthorizationStateLoggingOut) scope.launch { clear() }
        }
    }

    override val notes: Flow<ImmutableMap<Long, ChatNote>> =
        flow {
            load()
            emitAll(all.filterNotNull())
        }.distinctUntilChanged()

    override suspend fun set(note: ChatNote): Outcome<Unit> =
        mutex.withLock {
            val kept = note.kept()
            val current = loadLocked()
            val next =
                if (kept.isEmpty) {
                    current - kept.chatId
                } else {
                    // A new chat beyond the limit is not kept; an existing one is always updated.
                    if (kept.chatId !in current && current.size >= MAX_NOTES) return@withLock full()
                    current + (kept.chatId to kept)
                }.toImmutableMap()
            all.value = next
            try {
                write(next.values)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Note not saved: ${error.message}"))
            }
        }

    private fun full(): Outcome<Unit> = Outcome.Failure(DomainError.Unknown(code = 0, message = "Too many notes"))

    private suspend fun load() {
        if (all.value == null) mutex.withLock { loadLocked() }
    }

    /** The notes, read from the file the first time; with [mutex] held. */
    private suspend fun loadLocked(): ImmutableMap<Long, ChatNote> =
        all.value ?: withContext(io) { read() }.also { all.value = it }

    private suspend fun clear() {
        mutex.withLock {
            all.value = persistentMapOf()
            withContext(io) { file?.delete() }
        }
    }

    private fun read(): ImmutableMap<Long, ChatNote> {
        val lines =
            try {
                file?.takeIf { it.isFile }?.readLines().orEmpty()
            } catch (_: IOException) {
                emptyList()
            }
        return lines
            .mapNotNull(::decodeNote)
            .filterNot { it.isEmpty }
            .take(MAX_NOTES)
            .associateBy { it.chatId }
            .toImmutableMap()
    }

    private suspend fun write(notes: Collection<ChatNote>) {
        val target = file ?: return
        withContext(io) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(notes.joinToString(separator = "") { encodeNote(it) + "\n" })
            if (!temporary.renameTo(target)) throw IOException("Could not replace $FILE_NAME")
        }
    }

    internal companion object {
        const val FILE_NAME = "chat_notes.txt"

        /** The most chats with a note or a name: a few megabytes at the very most. */
        const val MAX_NOTES = 2_000
    }
}

private const val NOTE_FIELDS = 3
private const val NOTE_FIELD = 2

/** One line: the chat, the name and the note, tab-separated, the texts escaped. */
private fun encodeNote(note: ChatNote): String = "${note.chatId}\t${escape(note.name)}\t${escape(note.note)}"

/** [encodeNote]'s line back, cut to the limits; null for one that is not (a file cut short). */
private fun decodeNote(line: String): ChatNote? =
    line
        .split('\t', limit = NOTE_FIELDS)
        .takeIf { it.size == NOTE_FIELDS }
        ?.let { fields ->
            fields[0].toLongOrNull()?.let { ChatNote(it, unescape(fields[1]), unescape(fields[NOTE_FIELD])).kept() }
        }
