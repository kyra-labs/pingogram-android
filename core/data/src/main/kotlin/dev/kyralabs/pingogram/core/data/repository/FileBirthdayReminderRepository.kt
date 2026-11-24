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
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.repository.BirthdayReminderRepository
import dev.kyralabs.pingogram.core.tdlib.AccountSlot
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
 * [BirthdayReminderRepository] in a small file of the account's TDLib directory (`birthday_reminders.txt`), which the
 * account leaving the device deletes with the rest of it. The demo keeps its reminders in memory only.
 *
 * - **What is kept**: the user id and the day and month to ring on; no name, no year.
 * - **Read** once, on the [io] dispatcher, the first time the reminders are asked for; then kept in memory.
 * - **Written** whole after each change, through a temporary file, so a write cut short leaves the last good one. At
 *   most [MAX_REMINDERS] are kept.
 * - **Logging out** deletes them all: it is a [TdlibUpdateCache], made before TDLib starts, so it sees
 *   `AuthorizationStateLoggingOut`. User ids outlive TDLib closing, so closing keeps them.
 */
@AccountScoped
class FileBirthdayReminderRepository internal constructor(
    client: TdlibClient,
    private val scope: CoroutineScope,
    private val file: File?,
    private val io: CoroutineDispatcher,
) : BirthdayReminderRepository,
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

    /** Every reminder, by user id; null until read. */
    private val all = MutableStateFlow<ImmutableList<BirthdayReminder>?>(null)

    /** Serializes reading, writing and deleting the file. */
    private val mutex = Mutex()

    init {
        // Subscribed before the constructor returns, as for every TdlibUpdateCache.
        client.updateRouter(scope).onUpdates(setOf(TdApi.UpdateAuthorizationState::class)) { update ->
            val state = (update as TdApi.UpdateAuthorizationState).authorizationState
            if (state is TdApi.AuthorizationStateLoggingOut) scope.launch { clear() }
        }
    }

    override val reminders: Flow<ImmutableList<BirthdayReminder>> =
        flow {
            load()
            emitAll(all.filterNotNull())
        }.distinctUntilChanged()

    override suspend fun set(reminder: BirthdayReminder): Outcome<Unit> =
        change { list -> list.filterNot { it.userId == reminder.userId } + reminder }

    override suspend fun delete(userId: Long): Outcome<Unit> = change { list -> list.filterNot { it.userId == userId } }

    /** Applies [edit] to the reminders, sorts and trims them, and writes them. */
    private suspend fun change(edit: (List<BirthdayReminder>) -> List<BirthdayReminder>): Outcome<Unit> =
        mutex.withLock {
            val next = edit(loadLocked()).kept()
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
    private suspend fun loadLocked(): ImmutableList<BirthdayReminder> =
        all.value ?: withContext(io) { read() }.also { all.value = it }

    private suspend fun clear() {
        mutex.withLock {
            all.value = persistentListOf()
            withContext(io) { file?.delete() }
        }
    }

    private fun read(): ImmutableList<BirthdayReminder> {
        val lines =
            try {
                file?.takeIf { it.isFile }?.readLines().orEmpty()
            } catch (_: IOException) {
                emptyList()
            }
        return lines.mapNotNull(::decodeReminder).kept()
    }

    private suspend fun write(reminders: List<BirthdayReminder>) {
        val target = file ?: return
        withContext(io) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(reminders.joinToString(separator = "") { encodeReminder(it) + "\n" })
            if (!temporary.renameTo(target)) throw IOException("Could not replace $FILE_NAME")
        }
    }

    internal companion object {
        const val FILE_NAME = "birthday_reminders.txt"

        /** The most reminders kept: one per contact, a few dozen kilobytes at most. */
        const val MAX_REMINDERS = 2_000
    }
}

/** By user id, one each, at most [FileBirthdayReminderRepository.MAX_REMINDERS]. */
private fun List<BirthdayReminder>.kept(): ImmutableList<BirthdayReminder> =
    distinctBy { it.userId }.sortedBy { it.userId }.take(FileBirthdayReminderRepository.MAX_REMINDERS).toImmutableList()

private const val REMINDER_FIELDS = 3
private const val USER_FIELD = 0
private const val DAY_FIELD = 1
private const val MONTH_FIELD = 2
private const val MONTHS = 12
private const val DAYS = 31

/** One line: the user, the day and the month, tab-separated. */
private fun encodeReminder(reminder: BirthdayReminder): String =
    "${reminder.userId}\t${reminder.day}\t${reminder.month}"

/** [encodeReminder]'s line back; null for one that is not (a file cut short) or not a date. */
private fun decodeReminder(line: String): BirthdayReminder? =
    line
        .split('\t')
        .mapNotNull { it.toLongOrNull() }
        .takeIf { it.size == REMINDER_FIELDS }
        ?.let { BirthdayReminder(it[USER_FIELD], it[DAY_FIELD].toInt(), it[MONTH_FIELD].toInt()) }
        ?.takeIf { it.day in 1..DAYS && it.month in 1..MONTHS }
