package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.Reminder
import dev.kyralabs.pingogram.core.domain.repository.ReminderRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
 * [ReminderRepository] in a small file of the account's TDLib directory (`reminders.txt`), which the account leaving
 * the device deletes with the rest of it (`AccountStorage`); the demo keeps its reminders in memory only. As
 * [FileTranscriptRepository] does: read once on the [io] dispatcher, written whole through a temporary file after each
 * change, at most [MAX_REMINDERS] kept (the soonest dropped first), and everything deleted when the account logs out
 * (a [TdlibUpdateCache] for that). Nothing of it leaves the device or is logged. When the reminders ring is
`core:notifications`' `ReminderAlarms`, which follows them.
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

    /** The reminders, by when they are due; null until read. */
    private val store = MutableStateFlow<List<Reminder>?>(null)
    private val mutex = Mutex()

    init {
        // UNDISPATCHED: subscribed before the constructor returns, as for every TdlibUpdateCache.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.updates.collect { update -> if (update.endsSession()) scope.launch { clear() } }
        }
    }

    override val reminders: Flow<ImmutableList<Reminder>> =
        loaded { all -> all.sortedBy { it.at }.toImmutableList() }

    override fun reminder(
        chatId: Long,
        messageId: Long,
    ): Flow<Reminder?> = loaded { all -> all.firstOrNull { it.chatId == chatId && it.messageId == messageId } }

    override suspend fun save(reminder: Reminder): Outcome<Unit> =
        change { all ->
            val others = all.filterNot { it.chatId == reminder.chatId && it.messageId == reminder.messageId }
            (others + reminder).sortedBy { it.at }.takeLast(MAX_REMINDERS)
        }

    override suspend fun remove(
        chatId: Long,
        messageId: Long,
    ): Outcome<Unit> = change { all -> all.filterNot { it.chatId == chatId && it.messageId == messageId } }

    override suspend fun setDone(
        chatId: Long,
        messageId: Long,
        done: Boolean,
    ): Outcome<Unit> =
        change { all ->
            all.map { if (it.chatId == chatId && it.messageId == messageId) it.copy(done = done) else it }
        }

    private fun <T> loaded(map: (List<Reminder>) -> T): Flow<T> =
        flow {
            if (store.value == null) {
                mutex.withLock { store.value ?: withContext(io) { readReminders(file) }.also { store.value = it } }
            }
            emitAll(store.filterNotNull().map(map))
        }.distinctUntilChanged()

    private suspend fun change(transform: (List<Reminder>) -> List<Reminder>): Outcome<Unit> =
        mutex.withLock {
            val next = transform(store.value ?: withContext(io) { readReminders(file) }.also { store.value = it })
            store.value = next
            try {
                file?.let { target -> withContext(io) { writeReminders(target, next) } }
                Outcome.Success(Unit)
            } catch (error: IOException) {
                Outcome.Failure(DomainError.Unknown(code = 0, message = "Reminders not saved: ${error.message}"))
            }
        }

    private suspend fun clear() {
        mutex.withLock {
            store.value = emptyList()
            withContext(io) { file?.delete() }
        }
    }

    internal companion object {
        const val FILE_NAME = "reminders.txt"

        /** The most reminders kept: a few hundred kilobytes at most. */
        const val MAX_REMINDERS = 2_000
    }
}

private fun readReminders(file: File?): List<Reminder> {
    val lines =
        try {
            file?.takeIf { it.isFile }?.readLines().orEmpty()
        } catch (_: IOException) {
            emptyList()
        }
    return lines.mapNotNull(::decodeReminder)
}

/** Writes [reminders] whole through a temporary file, so a write cut short leaves the last good one. */
private fun writeReminders(
    target: File,
    reminders: List<Reminder>,
) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.tmp")
    temporary.writeText(reminders.joinToString(separator = "") { encode(it) + "\n" })
    if (!temporary.renameTo(target)) throw IOException("Could not replace ${target.name}")
}

private fun TdApi.Update.endsSession(): Boolean =
    this is TdApi.UpdateAuthorizationState &&
        (
            authorizationState is TdApi.AuthorizationStateLoggingOut ||
                authorizationState is TdApi.AuthorizationStateClosed
        )

private const val REMINDER = "R"
private const val FIELDS = 8

/** A reminder's line: "R", the chat, the message, when, done (1 or 0), the sender, the text and the note. */
private fun encode(reminder: Reminder) =
    listOf(
        REMINDER,
        reminder.chatId,
        reminder.messageId,
        reminder.at,
        if (reminder.done) 1 else 0,
        escape(reminder.preview.sender),
        escape(reminder.preview.text),
        escape(reminder.note),
    ).joinToString("\t")

private fun decodeReminder(line: String): Reminder? {
    val fields = line.split('\t', limit = FIELDS).takeIf { it.size == FIELDS && it[0] == REMINDER } ?: return null
    val chatId = fields[1].toLongOrNull()
    val messageId = fields[2].toLongOrNull()
    val at = fields[AT].toLongOrNull()
    return if (chatId != null && messageId != null && at != null) {
        Reminder(
            chatId = chatId,
            messageId = messageId,
            at = at,
            note = unescape(fields[NOTE]),
            preview = BookmarkPreview(sender = unescape(fields[SENDER]), text = unescape(fields[TEXT])),
            done = fields[DONE] == "1",
        )
    } else {
        null
    }
}

private const val AT = 3
private const val DONE = 4
private const val SENDER = 5
private const val TEXT = 6
private const val NOTE = 7
