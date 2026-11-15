package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.Transcript
import dev.kyralabs.pingogram.core.domain.repository.TranscriptRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * [TranscriptRepository] in a small file of the account's TDLib directory (`transcripts.txt`), which the account
 * leaving the device deletes with the rest of it (`AccountStorage`). The demo keeps its transcripts in memory only.
 *
 * - **Read** once, on the [io] dispatcher, the first time a chat's transcripts are asked for; then kept in memory.
 * - **Written** whole after each change, through a temporary file, so a write cut short leaves the last good one. At
 *   most [MAX_TRANSCRIPTS] are kept, the oldest dropped first.
 * - **Logging out** (or TDLib closing, whose message ids then belong to another session) deletes them all: it is a
 *   [TdlibUpdateCache], made before TDLib starts, so it sees `AuthorizationStateLoggingOut` and `Closed` among the
 *   updates.
 *
 * The transcripts never leave the device and are never logged.
 */
@AccountScoped
class FileTranscriptRepository internal constructor(
    client: TdlibClient,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : TranscriptRepository,
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

    /** Every transcript, in the order they were made; null until read. */
    private val transcripts = MutableStateFlow<PersistentMap<MessageKey, Transcript>?>(null)

    /** Serializes reading, writing and deleting the file. */
    private val mutex = Mutex()

    init {
        // UNDISPATCHED: subscribed before the constructor returns, as for every TdlibUpdateCache.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.updates.collect { update -> if (update.endsSession()) scope.launch { clear() } }
        }
    }

    override fun transcripts(chatId: Long): Flow<ImmutableMap<Long, Transcript>> =
        flow {
            load()
            emitAll(
                transcripts.filterNotNull().map { all ->
                    all.entries
                        .filter { it.key.chatId == chatId }
                        .associate { it.key.messageId to it.value }
                        .toImmutableMap()
                },
            )
        }.distinctUntilChanged()

    override suspend fun save(
        chatId: Long,
        messageId: Long,
        transcript: Transcript,
    ): Outcome<Unit> =
        mutex.withLock {
            val all =
                loadLocked()
                    .removing(
                        MessageKey(chatId, messageId),
                    ).putting(MessageKey(chatId, messageId), transcript)
            val kept = if (all.size > MAX_TRANSCRIPTS) all.entries.drop(all.size - MAX_TRANSCRIPTS) else all.entries
            val next = kept.associate { it.key to it.value }.toPersistentMap()
            transcripts.value = next
            try {
                write(next)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Transcript not saved: ${error.message}"))
            }
        }

    private suspend fun load() {
        if (transcripts.value == null) mutex.withLock { loadLocked() }
    }

    /** The transcripts, read from the file the first time; with [mutex] held. */
    private suspend fun loadLocked(): PersistentMap<MessageKey, Transcript> =
        transcripts.value ?: withContext(io) { read() }.also { transcripts.value = it }

    private suspend fun clear() {
        mutex.withLock {
            transcripts.value = persistentMapOf()
            withContext(io) { file?.delete() }
        }
    }

    private fun read(): PersistentMap<MessageKey, Transcript> {
        val lines =
            try {
                file?.takeIf { it.isFile }?.readLines().orEmpty()
            } catch (_: IOException) {
                emptyList()
            }
        return lines.mapNotNull(::decode).toMap().toPersistentMap()
    }

    private suspend fun write(all: Map<MessageKey, Transcript>) {
        val target = file ?: return
        withContext(io) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(all.entries.joinToString(separator = "") { (key, value) -> encode(key, value) + "\n" })
            if (!temporary.renameTo(target)) throw IOException("Could not replace $FILE_NAME")
        }
    }

    internal companion object {
        const val FILE_NAME = "transcripts.txt"

        /** The most transcripts kept: a few hundred kilobytes at most. */
        const val MAX_TRANSCRIPTS = 2_000
    }
}

/** A message of the account, by its chat. */
private data class MessageKey(
    val chatId: Long,
    val messageId: Long,
)

private fun TdApi.Update.endsSession(): Boolean =
    this is TdApi.UpdateAuthorizationState &&
        (
            authorizationState is TdApi.AuthorizationStateLoggingOut ||
                authorizationState is TdApi.AuthorizationStateClosed
        )

private const val FLAG_AUTOMATIC = 1
private const val FLAG_FIRST = 2
private const val FIELDS = 4
private const val TEXT_FIELD = 3

/** One line: the chat, the message, the flags and the text, tab-separated, the text escaped. */
private fun encode(
    key: MessageKey,
    transcript: Transcript,
): String {
    val flags = (if (transcript.isAutomatic) FLAG_AUTOMATIC else 0) or (if (transcript.isFirst) FLAG_FIRST else 0)
    return "${key.chatId}\t${key.messageId}\t$flags\t${escape(transcript.text)}"
}

/** [encode]'s line back; null for one that is not (a file cut short). */
private fun decode(line: String): Pair<MessageKey, Transcript>? {
    val fields = line.split('\t', limit = FIELDS).takeIf { it.size == FIELDS } ?: return null
    val chatId = fields[0].toLongOrNull()
    val messageId = fields[1].toLongOrNull()
    val flags = fields[2].toIntOrNull()
    return if (chatId != null && messageId != null && flags != null) {
        MessageKey(chatId, messageId) to
            Transcript(
                text = unescape(fields[TEXT_FIELD]),
                isAutomatic = flags and FLAG_AUTOMATIC != 0,
                isFirst = flags and FLAG_FIRST != 0,
            )
    } else {
        null
    }
}

internal fun escape(text: String): String =
    buildString(text.length) {
        for (char in text) {
            when (char) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

internal fun unescape(text: String): String =
    buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\' && index + 1 < text.length) {
                when (text[index + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(text[index + 1])
                }
                index += 2
            } else {
                append(char)
                index++
            }
        }
    }
