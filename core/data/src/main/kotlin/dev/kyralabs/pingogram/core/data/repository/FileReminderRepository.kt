package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.MessageReminder
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
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
import java.time.Instant
import javax.inject.Inject

/**
 * [ReminderRepository] in a small file of the account's TDLib directory (`reminders.txt`), which the account leaving
 * the device deletes with the rest of it (`AccountStorage`). The demo keeps its reminders in memory only.
 *
 * - **What is kept**: the chat, the message, the time, whether it is done and the user's note; never the message's
 *   text, which stays in TDLib's encrypted database and is read from there where a reminder shows.
 * - **Read** once, on the [io] dispatcher, the first time the reminders are asked for; then kept in memory.
 * - **Written** whole after each change, through a temporary file, so a write cut short leaves the last good one. At
 *   most [MAX_REMINDERS] are kept, the done ones dropped first, then the oldest.
 * - **Logging out** (or TDLib closing, whose message ids then belong to another session) deletes them all: it is a
 *   [TdlibUpdateCache], made before TDLib starts, so it sees `AuthorizationStateLoggingOut` and `Closed`.
 *
 * Notes are never logged.
 */
@AccountScoped
class FileReminderRepository internal constructor(
    client: TdlibClient,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : ReminderRepository,
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

    /** Every reminder, the soonest first; null until read. */
    private val all = MutableStateFlow<PersistentList<MessageReminder>?>(null)

    /** Serializes reading, writing and deleting the file. */
    private val mutex = Mutex()

    init {
        // UNDISPATCHED: subscribed before the constructor returns, as for every TdlibUpdateCache.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.updates.collect { update -> if (update.endsReminderSession()) scope.launch { clear() } }
        }
    }

    override val reminders: Flow<ImmutableList<MessageReminder>> =
        flow {
            load()
            emitAll(all.filterNotNull())
        }.distinctUntilChanged()

    override fun reminders(chatId: Long): Flow<ImmutableMap<Long, MessageReminder>> =
        reminders
            .map { list -> list.filter { it.chatId == chatId }.associateBy { it.messageId }.toImmutableMap() }
            .distinctUntilChanged()

    override suspend fun set(reminder: MessageReminder): Outcome<Unit> =
        change { list -> list.filterNot { it.sameMessage(reminder) } + reminder.copy(note = reminder.note.trimmed()) }

    override suspend fun delete(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> = change { list -> list.filterNot { it.chatId == chatId && it.messageId == messageId } }

    /** Applies [edit] to the reminders, sorts and trims them, and writes them. */
    private suspend fun change(edit: (List<MessageReminder>) -> List<MessageReminder>): Outcome<Unit> =
        mutex.withLock {
            val next = edit(loadLocked()).kept().toPersistentList()
            all.value = next
            try {
                write(next)
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Reminder not saved: ${error.message}"))
            }
        }

    private suspend fun load() {
        if (all.value == null) mutex.withLock { loadLocked() }
    }

    /** The reminders, read from the file the first time; with [mutex] held. */
    private suspend fun loadLocked(): PersistentList<MessageReminder> =
        all.value ?: withContext(io) { read() }.also { all.value = it }

    private suspend fun clear() {
        mutex.withLock {
            all.value = persistentListOf()
            withContext(io) { file?.delete() }
        }
    }

    private fun read(): PersistentList<MessageReminder> {
        val lines =
            try {
                file?.takeIf { it.isFile }?.readLines().orEmpty()
            } catch (_: IOException) {
                emptyList()
            }
        return lines.mapNotNull(::decodeReminder).kept().toPersistentList()
    }

    private suspend fun write(reminders: List<MessageReminder>) {
        val target = file ?: return
        withContext(io) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(reminders.joinToString(separator = "") { encodeReminder(it) + "\n" })
            if (!temporary.renameTo(target)) throw IOException("Could not replace $FILE_NAME")
        }
    }

    internal companion object {
        const val FILE_NAME = "reminders.txt"

        /** The most reminders kept: a few dozen kilobytes at most. */
        const val MAX_REMINDERS = 500
    }
}

private fun MessageReminder.sameMessage(other: MessageReminder) = chatId == other.chatId && messageId == other.messageId

private fun String.trimmed(): String = trim().take(MessageReminder.NOTE_LENGTH_MAX)

/** Sorted by time; past [FileReminderRepository.MAX_REMINDERS] the done ones go first, then the oldest. */
private fun List<MessageReminder>.kept(): ImmutableList<MessageReminder> {
    val sorted = sortedWith(compareBy<MessageReminder>({ it.at }, { it.chatId }, { it.messageId }))
    val excess = sorted.size - FileReminderRepository.MAX_REMINDERS
    if (excess <= 0) return sorted.toImmutableList()
    val dropped = (sorted.filter { it.isDone } + sorted.filterNot { it.isDone }).take(excess).toSet()
    return sorted.filterNot { it in dropped }.toImmutableList()
}

private fun TdApi.Update.endsReminderSession(): Boolean =
    this is TdApi.UpdateAuthorizationState &&
        (
            authorizationState is TdApi.AuthorizationStateLoggingOut ||
                authorizationState is TdApi.AuthorizationStateClosed
        )

private const val FLAG_DONE = 1
private const val REMINDER_FIELDS = 5
private const val CHAT_FIELD = 0
private const val MESSAGE_FIELD = 1
private const val TIME_FIELD = 2
private const val FLAGS_FIELD = 3
private const val NOTE_FIELD = 4

/** One line: the chat, the message, the time in epoch seconds, the flags and the note, tab-separated, escaped. */
private fun encodeReminder(reminder: MessageReminder): String {
    val flags = if (reminder.isDone) FLAG_DONE else 0
    return "${reminder.chatId}\t${reminder.messageId}\t${reminder.at.epochSecond}\t$flags\t${escape(reminder.note)}"
}

/** [encodeReminder]'s line back; null for one that is not (a file cut short). */
private fun decodeReminder(line: String): MessageReminder? {
    val fields = line.split('\t', limit = REMINDER_FIELDS).takeIf { it.size == REMINDER_FIELDS }
    val numbers = fields?.take(NOTE_FIELD)?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == NOTE_FIELD }
    if (fields == null || numbers == null) return null
    return MessageReminder(
        chatId = numbers[CHAT_FIELD],
        messageId = numbers[MESSAGE_FIELD],
        at = Instant.ofEpochSecond(numbers[TIME_FIELD]),
        note = unescape(fields[NOTE_FIELD]),
        isDone = numbers[FLAGS_FIELD].toInt() and FLAG_DONE != 0,
    )
}
